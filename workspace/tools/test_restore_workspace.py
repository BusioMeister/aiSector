import hashlib
import json
import os
from pathlib import Path
import tempfile
import unittest
from restore_workspace import restore


class RestoreWorkspaceTest(unittest.TestCase):
    def setUp(self):
        root = Path(os.environ.get("LOCALAPPDATA", tempfile.gettempdir()))
        temp_root = root / "Temp/opencode" if os.name == "nt" else root
        self.directory = tempfile.TemporaryDirectory(dir=temp_root)
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.snapshot = self.root / "snapshot"
        self.snapshot.mkdir()
        self.target = self.root / "target"
        for module in ("aiSector", "VelocityPlugin"):
            (self.target / module / "src").mkdir(parents=True)

    def manifest(self, files):
        values = {}
        for relative, data in files.items():
            source = self.snapshot / relative
            source.parent.mkdir(parents=True, exist_ok=True)
            source.write_bytes(data)
            values[relative] = hashlib.sha256(data.replace(b"\r\n", b"\n")).hexdigest()
        (self.snapshot / "source-manifest.json").write_text(json.dumps({
            "hash_format": "sha256-after-crlf-to-lf", "files": values}), encoding="utf-8")

    def test_copies_and_then_skips_matching_files_with_different_line_endings(self):
        self.manifest({"pom.xml": b"<project/>\n"})
        self.assertEqual(1, restore(self.snapshot, self.target))
        (self.target / "pom.xml").write_bytes(b"<project/>\r\n")
        self.assertEqual(0, restore(self.snapshot, self.target))

    def test_conflict_does_not_overwrite_or_partially_copy(self):
        self.manifest({"pom.xml": b"new", "README.md": b"new"})
        (self.target / "README.md").write_text("user work", encoding="utf-8")
        with self.assertRaises(ValueError):
            restore(self.snapshot, self.target)
        self.assertFalse((self.target / "pom.xml").exists())
        self.assertEqual("user work", (self.target / "README.md").read_text())

    def test_corrupted_source_is_rejected(self):
        self.manifest({"pom.xml": b"expected"})
        (self.snapshot / "pom.xml").write_bytes(b"modified")
        with self.assertRaises(ValueError):
            restore(self.snapshot, self.target)

    def test_parent_traversal_is_rejected(self):
        (self.snapshot / "source-manifest.json").write_text(json.dumps({
            "hash_format": "sha256-after-crlf-to-lf", "files": {"../outside": "unused"}}))
        with self.assertRaises(ValueError):
            restore(self.snapshot, self.target)


if __name__ == "__main__":
    unittest.main()
