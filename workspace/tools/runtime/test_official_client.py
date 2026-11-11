import os
import tempfile
import unittest
from pathlib import Path
import official_client


class OfficialClientTest(unittest.TestCase):
    def test_os_rules_do_not_load_foreign_or_arm_natives(self):
        self.assertTrue(official_client.allowed({}))
        self.assertTrue(official_client.allowed({"rules": [{"action": "allow", "os": {"name": "windows"}}]}))
        self.assertFalse(official_client.allowed({"rules": [{"action": "allow", "os": {"name": "linux"}}]}))
        self.assertFalse(official_client.allowed({"rules": [{"action": "allow", "os": {"name": "windows", "arch": "aarch64"}}]}))

    def test_offline_fixture_uses_zero_token_isolated_directory_and_local_proxy(self):
        temporary = Path(os.environ["LOCALAPPDATA"]) / "Temp" / "opencode"
        with tempfile.TemporaryDirectory(dir=temporary) as root:
            prepared = ({"mainClass": "fixture.Main", "assetIndex": {"id": "34"}}, "fixture-classpath", Path(root) / "assets")
            directory = Path(root) / "fixture"
            args = official_client.arguments(prepared, directory, "RwS123abcA")
            self.assertEqual("0", args[args.index("--accessToken") + 1])
            self.assertEqual("127.0.0.1:25565", args[args.index("--quickPlayMultiplayer") + 1])
            self.assertTrue((directory / "options.txt").exists())
            with self.assertRaises(FileExistsError):
                official_client.arguments(prepared, directory, "RwS123abcA")

    def test_refuses_real_account_names_and_nonstaging_ports(self):
        with self.assertRaises(ValueError):
            official_client.arguments(None, Path("unused"), "RealAccount")
        with self.assertRaises(ValueError):
            official_client.arguments(None, Path("unused"), "RwS123abcA", port=25566)


if __name__ == "__main__":
    unittest.main()
