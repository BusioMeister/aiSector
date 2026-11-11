import unittest
from check_permissions import descriptor_inventory, REGISTERED_EXECUTOR


class PermissionInventoryTest(unittest.TestCase):
    def test_commands_are_not_mistaken_for_permission_declarations(self):
        result = descriptor_inventory("commands:\n  heal:\n    permission: admin.heal\n    aliases: [h]\npermissions:\n  admin.heal:\n    default: op\n")
        self.assertNotIn("heal", result["permissions"])
        self.assertEqual("op", result["permissions"]["admin.heal"]["default"])
        self.assertEqual(["h"], result["commands"]["heal"]["aliases"])

    def test_multiline_aliases(self):
        result = descriptor_inventory("commands:\n  lvl:\n    aliases:\n      - pkt\n    description: level\n")
        self.assertEqual(["pkt"], result["commands"]["lvl"]["aliases"])

    def test_qualified_executor_registration(self):
        text = 'getCommand("gildia").setExecutor(new ai.aisector.guilds.GuildCommand(this));'
        self.assertEqual([("gildia", "GuildCommand")], REGISTERED_EXECUTOR.findall(text))


if __name__ == "__main__":
    unittest.main()
