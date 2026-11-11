"""Read-only audit of the two nested Git HEAD trees. Never checkout, reset, fetch, or print config secrets."""
import argparse
import json
import os
import re
import sys
from pathlib import Path
from check_permissions import descriptor_inventory, REGISTERED_EXECUTOR, CHECKED_PERMISSION

ROOT = Path(__file__).resolve().parents[1]


def config_keys(text):
    stack, keys = [], []
    for line in text.splitlines():
        match = re.match(r"^(\s*)([A-Za-z0-9_.-]+):(?:\s*(.*))?$", line)
        if not match:
            continue
        indent, key, value = len(match[1]), match[2], match[3] or ""
        while stack and stack[-1][0] >= indent:
            stack.pop()
        path = ".".join([entry[1] for entry in stack] + [key])
        keys.append(path)
        if not value.strip() or value.lstrip().startswith("#"):
            stack.append((indent, key))
    return sorted(keys)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dulwich-path", type=Path, default=Path(os.environ["LOCALAPPDATA"]) / "Temp/opencode/git-readonly-python")
    parser.add_argument("--output", type=Path, default=ROOT / "tools/baseline_inventory.json")
    args = parser.parse_args()
    sys.path.insert(0, str(args.dulwich_path))
    from dulwich.repo import Repo
    from dulwich.object_store import iter_tree_contents

    result = {}
    for module in ["aiSector", "VelocityPlugin"]:
        repository = Repo(str(ROOT / module))
        commit = repository[repository.head()]
        baseline = {entry.path.decode(): repository[entry.sha].data.decode("utf-8", errors="replace")
                    for entry in iter_tree_contents(repository.object_store, commit.tree)
                    if entry.path.startswith(b"src/main/") and entry.path.endswith((b".java", b".yml", b".json"))}
        current = {str(path.relative_to(ROOT / module)).replace("\\", "/"): path.read_text(encoding="utf-8")
                   for path in (ROOT / module / "src/main").rglob("*") if path.suffix in (".java", ".yml", ".json")}
        old_java = {p: text for p, text in baseline.items() if p.endswith(".java")}
        new_java = {p: text for p, text in current.items() if p.endswith(".java")}
        old_source, new_source = "\n".join(old_java.values()), "\n".join(new_java.values())
        old_exec = dict(REGISTERED_EXECUTOR.findall(old_source))
        new_exec = dict(REGISTERED_EXECUTOR.findall(new_source))
        config = "src/main/resources/config.yml"
        descriptor = "src/main/resources/plugin.yml"
        old_descriptor = descriptor_inventory(baseline.get(descriptor, ""))
        new_descriptor = descriptor_inventory(current.get(descriptor, ""))
        changed = {name: {"before": old_descriptor["commands"].get(name), "after": new_descriptor["commands"].get(name)}
                   for name in set(old_descriptor["commands"]) | set(new_descriptor["commands"])
                   if old_descriptor["commands"].get(name) != new_descriptor["commands"].get(name)}
        result[module] = {
            "baseline_commit": commit.id.decode(),
            "baseline_java_count": len(old_java), "current_java_count": len(new_java),
            "removed_java_paths": sorted(set(old_java) - set(new_java)),
            "added_java_paths": sorted(set(new_java) - set(old_java)),
            "removed_resources": sorted(p for p in set(baseline) - set(current) if "/resources/" in p),
            "command_executors_removed": {key: value for key, value in old_exec.items() if key not in new_exec},
            "command_executors_added": {key: value for key, value in new_exec.items() if key not in old_exec},
            "command_executors_changed": {key: [old_exec[key], new_exec[key]] for key in old_exec.keys() & new_exec.keys()
                                          if old_exec[key] != new_exec[key]},
            "command_descriptor_changes": changed,
            "literal_permissions_added": sorted(set(CHECKED_PERMISSION.findall(new_source)) - set(CHECKED_PERMISSION.findall(old_source))),
            "literal_permissions_removed": sorted(set(CHECKED_PERMISSION.findall(old_source)) - set(CHECKED_PERMISSION.findall(new_source))),
            "baseline_config_keys": config_keys(baseline.get(config, "")),
            "current_config_keys": config_keys(current.get(config, "")),
            "velocity_registration_baseline": sorted(set(re.findall(r'(?:metaBuilder|register)\(\s*"([A-Za-z0-9_-]+)"', old_source))),
            "velocity_registration_current": sorted(set(re.findall(r'(?:metaBuilder|register)\(\s*"([A-Za-z0-9_-]+)"', new_source))),
            "config_parser_references_baseline": sorted(set(re.findall(r'([A-Za-z0-9_.]*(?:Config|Yaml)[A-Za-z0-9_.]*)', old_source))),
        }
        shared_sources = ROOT / "shared/src/main/java"
        mapped = {}
        for removed in result[module]["removed_java_paths"]:
            matches = list(shared_sources.rglob(Path(removed).name))
            if matches:
                mapped[removed] = str(matches[0].relative_to(ROOT)).replace("\\", "/")
            elif removed.endswith("/PlayerDisconectListener.java") and (ROOT / module / "src/main/java/ai/velocitysector/PlayerDisconnectListener.java").exists():
                mapped[removed] = "VelocityPlugin/src/main/java/ai/velocitysector/PlayerDisconnectListener.java"
        result[module]["moved_or_renamed_classes"] = mapped
        result[module]["unmapped_removed_java_paths"] = sorted(set(result[module]["removed_java_paths"]) - set(mapped))
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
