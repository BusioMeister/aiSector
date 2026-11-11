import unittest
from audit_rewrite_baseline import config_keys


class BaselineAuditTest(unittest.TestCase):
    def test_config_inventory_exposes_keys_not_secret_values(self):
        keys = config_keys('redis:\n  host: "localhost"\n  password: "super-secret"\nmongodb:\n  uri: "credential-bearing-uri"\n')
        self.assertEqual(["mongodb", "mongodb.uri", "redis", "redis.host", "redis.password"], keys)
        self.assertNotIn("super-secret", str(keys))
        self.assertNotIn("credential-bearing-uri", str(keys))

    def test_nested_server_keys_are_qualified(self):
        self.assertEqual(["servers", "servers.Sector1", "servers.Sector1.address"],
                         config_keys("servers:\n  Sector1:\n    address: localhost\n"))


if __name__ == "__main__":
    unittest.main()
