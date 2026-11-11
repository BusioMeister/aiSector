"""Restore published workspace inputs around sibling aiSector and VelocityPlugin clones.

Example: python aiSector/workspace/tools/restore_workspace.py /path/to/parent-of-clones
Existing differing files are never overwritten; all validation happens before copying.
"""
import argparse
import hashlib
import json
import shutil
from pathlib import Path, PurePosixPath


def digest(path):
    return hashlib.sha256(path.read_bytes().replace(b"\r\n", b"\n")).hexdigest()


def restore(snapshot, destination):
    snapshot, destination = snapshot.resolve(), destination.resolve()
    for module in ("aiSector", "VelocityPlugin"):
        if not (destination / module / "src").is_dir():
            raise ValueError(f"Clone {module} into {destination} first")
    manifest = json.loads((snapshot / "source-manifest.json").read_text(encoding="utf-8"))
    if manifest.get("hash_format") != "sha256-after-crlf-to-lf":
        raise ValueError("Unsupported manifest format")
    copies = []
    for relative, expected in manifest["files"].items():
        path = PurePosixPath(relative)
        if path.is_absolute() or ".." in path.parts or "\\" in relative or ":" in relative:
            raise ValueError("Unsafe manifest path")
        if path.parts[0] not in {"shared", "tools", ".mvn"} and len(path.parts) != 1:
            raise ValueError("Unexpected source directory")
        if path.parts[0] in {"aiSector", "VelocityPlugin", ".git"}:
            raise ValueError("Manifest must not replace plugin clones")
        source, target = snapshot / relative, destination / relative
        source.resolve().relative_to(snapshot)
        target.resolve().relative_to(destination)
        if source.is_symlink() or not source.is_file() or digest(source) != expected:
            raise ValueError(f"Source integrity check failed: {relative}")
        if target.exists() and (not target.is_file() or digest(target) != expected):
            raise ValueError(f"Existing file differs; refusing overwrite: {target}")
        if not target.exists():
            copies.append((source, target))
    for source, target in copies:
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
    return len(copies)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("destination", type=Path)
    parser.add_argument("--snapshot", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    print(f"Restored {restore(args.snapshot, args.destination)} files; existing matching files retained")


if __name__ == "__main__":
    main()
