#!/usr/bin/env python3
"""Check Java permission literals against aiSector's permission catalog and descriptor."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
JAVA_ROOT = ROOT / "aiSector" / "src" / "main" / "java"
PLUGIN_YML = ROOT / "aiSector" / "src" / "main" / "resources" / "plugin.yml"
CATALOG_YML = ROOT / "aiSector" / "src" / "main" / "resources" / "permissions.yml"
NODE = re.compile(r"^[A-Za-z0-9_.-]+$")
CHECKED_PERMISSION = re.compile(r'\.hasPermission\(\s*"([^"]+)"\s*\)')
REGISTERED_EXECUTOR = re.compile(
    r'getCommand\(\s*"([A-Za-z0-9_-]+)"\s*\)\s*\.setExecutor\(\s*new\s+(?:[A-Za-z0-9_]+\.)*([A-Za-z0-9_]+)'
)


def descriptor_inventory(text: str) -> dict:
    """Read the flat command/permission blocks used in this repository (not general YAML)."""
    result = {"commands": {}, "permissions": {}}
    section = None
    current = None
    alias_list = False
    for line in text.splitlines():
        if re.match(r"^\S", line) and not line.startswith("#"):
            section = line.rstrip(":") if line in ("commands:", "permissions:") else None
            current = None
            alias_list = False
        entry = re.match(r"^  ([A-Za-z0-9_.-]+):\s*(?:#.*)?$", line)
        if section and entry:
            current = entry.group(1)
            result[section][current] = {}
            alias_list = False
        field = re.match(r"^    (permission|default|aliases):\s*(.*?)\s*(?:#.*)?$", line)
        if section and current and field:
            result[section][current][field.group(1)] = field.group(2)
            alias_list = field.group(1) == "aliases" and not field.group(2)
            if field.group(1) == "aliases" and field.group(2):
                result[section][current]["aliases"] = [value.strip(" '\"") for value in
                        field.group(2).strip("[]").split(",") if value.strip()]
            elif alias_list:
                result[section][current]["aliases"] = []
        alias = re.match(r"^      -\s*([A-Za-z0-9_-]+)\s*$", line)
        if alias and alias_list and section and current:
            result[section][current]["aliases"].append(alias.group(1))
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--strict-descriptor", action="store_true",
                         help="also fail when a command descriptor permission differs from its executor")
    parser.add_argument("--report", type=Path, help="write an advisory JSON inventory without changing permissions")
    args = parser.parse_args()

    java_files = list(JAVA_ROOT.rglob("*.java"))
    java_text = {path: path.read_text(encoding="utf-8") for path in java_files}
    checked: dict[str, set[Path]] = {}
    executor_by_command: dict[str, str] = {}
    for path, text in java_text.items():
        for permission in CHECKED_PERMISSION.findall(text):
            checked.setdefault(permission, set()).add(path)
        for command, executor in REGISTERED_EXECUTOR.findall(text):
            executor_by_command[command.lower()] = executor

    catalog_text = CATALOG_YML.read_text(encoding="utf-8")
    catalog = set(re.findall(r"^\s*-\s*([^\s#]+)", catalog_text, re.MULTILINE))
    plugin_text = PLUGIN_YML.read_text(encoding="utf-8")

    command_descriptors: dict[str, str] = {}
    in_commands = False
    current_command: str | None = None
    command_indent = 2
    for line in plugin_text.splitlines():
        if line == "commands:":
            in_commands = True
            continue
        if in_commands and line == "permissions:":
            break
        if not in_commands:
            continue
        command_match = re.match(r"^(\s+)([A-Za-z0-9_-]+):\s*(?:#.*)?$", line)
        if command_match and len(command_match.group(1)) == command_indent:
            current_command = command_match.group(2).lower()
            continue
        permission_match = re.match(r"^\s+permission:\s*([A-Za-z0-9_.-]+)\s*$", line)
        if permission_match and current_command:
            command_descriptors[current_command] = permission_match.group(1)

    inventory = descriptor_inventory(plugin_text)
    declared = catalog | set(inventory["permissions"])
    missing = sorted(set(checked) - declared)

    mismatches: list[tuple[str, str, str]] = []
    for command, descriptor_node in command_descriptors.items():
        executor = executor_by_command.get(command)
        if not executor:
            continue
        for path, text in java_text.items():
            if path.stem != executor:
                continue
            executor_nodes = set(CHECKED_PERMISSION.findall(text))
            if executor_nodes and descriptor_node not in executor_nodes:
                mismatches.extend((command, descriptor_node, executor_node)
                                  for executor_node in sorted(executor_nodes))

    if missing:
        print("ERROR: Java permission checks absent from catalog/descriptor:")
        for node in missing:
            locations = ", ".join(str(path.relative_to(ROOT)) for path in sorted(checked[node]))
            print(f"  {node}: {locations}")
    else:
        print(f"OK: {len(checked)} literal Java permission nodes are declared.")

    if mismatches:
        print("Descriptor/executor mismatches (policy decision required):")
        for command, descriptor_node, executor_node in sorted(set(mismatches)):
            print(f"  /{command}: plugin.yml={descriptor_node}, executor={executor_node}")

    no_literal_check = []
    for command, executor in sorted(executor_by_command.items()):
        text = "\n".join(text for path, text in java_text.items() if path.stem == executor)
        if not CHECKED_PERMISSION.search(text) and not command_descriptors.get(command):
            no_literal_check.append(command)
    unregistered = sorted(set(inventory["commands"]) - set(executor_by_command))
    advisory_unused = sorted(catalog - set(checked) - set(command_descriptors.values()))
    alias_owners: dict[str, list[str]] = {}
    for command, descriptor in inventory["commands"].items():
        for alias in [command, *descriptor.get("aliases", [])]:
            alias_owners.setdefault(alias.lower(), []).append(command)
    alias_collisions = {alias: owners for alias, owners in alias_owners.items() if len(set(owners)) > 1}
    print("Advisory commands with no descriptor/literal executor permission (may be intentionally public): "
          + ", ".join(no_literal_check))
    print("Descriptors without detected executor: " + (", ".join(unregistered) or "none"))
    print(f"Advisory catalog-only nodes: {len(advisory_unused)} (dynamic/external use not inferred).")
    print("Alias collisions: " + (str(alias_collisions) if alias_collisions else "none"))
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps({
            "descriptor": inventory, "executors": executor_by_command,
            "missing_nodes": missing, "mismatches": sorted(set(mismatches)),
            "commands_without_literal_check": no_literal_check,
            "descriptors_without_executor": unregistered, "catalog_only_nodes": advisory_unused,
            "alias_collisions": alias_collisions,
            "limitations": "Static advisory inventory; aliases/defaults are descriptor text; dynamic and Velocity policy require review."
        }, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    if missing or (args.strict_descriptor and mismatches):
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
