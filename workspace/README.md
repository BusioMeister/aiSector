# aiSector network plugins

Lokalny układ `Spawn` / `GameWest` / `GameEast`: [konfiguracja mapy i sektory](aiSector/SECTOR_LAYOUT.md).
Opcjonalny [silnik Velocity 4.3.1 z InstantTransfer](VelocityPlugin/SEAMLESS_FORK.md) wymaga osobnego buildu.

The repository contains two separately deployable plugins and a small shared protocol module:

| Maven module | Runtime | Artifact |
| --- | --- | --- |
| `shared` | Shared channel/protocol definitions | Dependency only |
| `aiSector` | Paper 26.3 server (also usable on compatible forks) | `aiSector/target/aiSector-2.0.0-SNAPSHOT.jar` |
| `VelocityPlugin` | Velocity 4.3.1 proxy | `VelocityPlugin/target/aiSector-Velocity-2.0.0-SNAPSHOT.jar` |

The root `pom.xml` builds all modules in dependency order. Requires JDK 25 and Maven 3.9+.

```shell
./mvnw test
./mvnw package
```

On Windows, use `mvnw.cmd` instead of `./mvnw`.

Paper's sector-specific settings are in the server plugin's `plugins/aiSECTOR/config.yml`.
Set a unique `this-sector-name` on each backend. Configure Redis and MongoDB there. MySQL
is disabled unless explicitly enabled; current gameplay features do not use it.

Velocity Redis and MongoDB connection settings can be provided using JVM properties or
environment variables:

| Setting | JVM property | Environment variable | Default |
| --- | --- | --- | --- |
| Redis host | `aisector.redis.host` | `AISECTOR_REDIS_HOST` | `127.0.0.1` |
| Redis port | `aisector.redis.port` | `AISECTOR_REDIS_PORT` | `6379` |
| Redis password | `aisector.redis.password` | `AISECTOR_REDIS_PASSWORD` | empty |
| MongoDB URI | `aisector.mongodb.uri` | `AISECTOR_MONGODB_URI` | `mongodb://127.0.0.1:27017` |
| MongoDB database | `aisector.mongodb.database` | `AISECTOR_MONGODB_DATABASE` | `users` |

The old `Purpur/` directory under `aiSector` is an upstream Purpur source checkout and is
not included in the Maven reactor. It is not needed to build either plugin.

## Compatibility note

This is the first migration pass to the current platform APIs. Paper-facing code must avoid
version-specific NMS/CraftBukkit internals. Shared network messages are being consolidated
in the `shared` module while the legacy message handlers are migrated incrementally.
