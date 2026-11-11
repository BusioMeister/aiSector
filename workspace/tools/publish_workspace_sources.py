"""Export all workspace-owned source/build/docs inputs, excluding runtime data and outputs."""
import hashlib
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def normalized_digest(data):
    return hashlib.sha256(data.replace(b"\r\n", b"\n")).hexdigest()


def main():
    destination = ROOT / "aiSector/workspace"
    names = ["pom.xml", "mvnw", "mvnw.cmd", ".gitignore", "README.md", "REDIS_PROTOCOL.md",
             "REWRITE_BACKLOG.md", "REWRITE_BASELINE.md", "REWRITE_PLAN.md", "REWRITE_RELEASE.md", "REWRITE_STATUS.md"]
    files = [ROOT / name for name in names]
    files.extend(p for p in (ROOT / ".mvn").rglob("*.properties") if p.is_file())
    files.append(ROOT / "shared/pom.xml")
    files.extend(p for p in (ROOT / "shared/src").rglob("*") if p.is_file() and p.suffix in {".java", ".yml", ".json"})
    files.extend(p for p in (ROOT / "tools").rglob("*") if p.is_file() and p.suffix in {".py", ".java", ".json"}
                 and "__pycache__" not in p.parts)
    manifest = {}
    for source in sorted(files):
        if source.is_symlink():
            raise RuntimeError(f"Refusing symbolic link: {source}")
        relative = source.relative_to(ROOT)
        target = destination / relative
        digest = normalized_digest(source.read_bytes())
        if target.exists() and normalized_digest(target.read_bytes()) != digest:
            raise RuntimeError(f"Export differs; review before updating: {target}")
        target.parent.mkdir(parents=True, exist_ok=True)
        if not target.exists():
            shutil.copy2(source, target)
        manifest[relative.as_posix()] = digest
    payload = {"hash_format": "sha256-after-crlf-to-lf", "files": manifest}
    output = destination / "source-manifest.json"
    if output.exists() and json.loads(output.read_text(encoding="utf-8")) != payload:
        raise RuntimeError("Export manifest differs; review before replacing")
    output.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    print(f"Published {len(manifest)} source/build/documentation files to {destination}")


if __name__ == "__main__":
    main()
