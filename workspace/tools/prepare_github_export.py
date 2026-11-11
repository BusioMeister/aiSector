"""Copy the shared source/build inputs into both existing, separately published repositories.

Only creates publication inputs, never copies target, runtime data, credentials or Git metadata.
Run from the canonical multi-module workspace before publishing a synchronized pair of updates.
"""
import hashlib
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def copy_new(source, target):
    data = source.read_bytes()
    if target.exists() and target.read_bytes() != data:
        raise RuntimeError(f"Refusing to replace a differing export: {target}")
    target.parent.mkdir(parents=True, exist_ok=True)
    if not target.exists():
        shutil.copy2(source, target)


def main():
    for module in ("aiSector", "VelocityPlugin"):
        repository = ROOT / module
        files = [(ROOT / "pom.xml", repository / "build-support/pom.xml"),
                 (ROOT / "shared/pom.xml", repository / "build-support/shared/pom.xml")]
        files.extend((path, repository / "build-support/shared/src" / path.relative_to(ROOT / "shared/src"))
                     for path in sorted((ROOT / "shared/src").rglob("*")) if path.is_file())
        files.extend((ROOT / name, repository / name) for name in ("mvnw", "mvnw.cmd"))
        files.extend((path, repository / ".mvn" / path.relative_to(ROOT / ".mvn"))
                     for path in sorted((ROOT / ".mvn").rglob("*")) if path.is_file())
        manifest = {}
        for source, target in files:
            copy_new(source, target)
            manifest[target.relative_to(repository).as_posix()] = hashlib.sha256(
                source.read_bytes().replace(b"\r\n", b"\n")).hexdigest()
        output = repository / "build-support/source-manifest.json"
        payload = (json.dumps(manifest, indent=2) + "\n").encode()
        if output.exists() and output.read_bytes() != payload:
            raise RuntimeError(f"Existing export manifest differs: {output}")
        output.write_bytes(payload)
        print(f"{module}: exported {len(files)} build/shared files")


if __name__ == "__main__":
    main()
