# Redis protocol inventory

This inventory reflects the current Paper/Velocity producers and subscribers. Pub/Sub is ephemeral: a publish count of zero means no live consumer received the message; durable transfer state is separately stored with a TTL. No external consumers are inferred for channels explicitly marked external/unknown.

| Channel / pattern | Producer → consumer | Payload / response |
|---|---|---|
| `aisector:packet`, `aisector:packet:<backend>` | Paper/Velocity packet publishers → Paper `CommandResponseListener`, Velocity `NetworkListener` | `PacketEnvelope {version,id,payload}`; version 1; targeted form routes to one backend. Unknown IDs are ignored by the receiver. |
| `aisector:save_player_data` | Velocity transfer handler → all Paper backends | `{requestId,uuid}`; only the hosting backend seals player actions, flushes the profile to Mongo, then stores snapshot and publishes ACK. |
| `aisector:save_player_data:ack` | Paper → Velocity transfer handler | `{requestId,uuid,success}`; request ID + UUID must match the pending transfer. |
| `sector-transfer` | Paper `SectorManager` → Velocity `NetworkListener` | `<uuid>:<backend>` after source action seal, acknowledged profile flush and transfer key writes; no response payload. |
| `aisector:global_playerlist_update` | Velocity publisher → Paper `GlobalPlayerListListener` | JSON player-name collection; ephemeral snapshot. |
| `sector-online:<backend>` | Paper publisher → Velocity `OnlinePlayersListener` (`sector-online:*`) | JSON map of player IDs/names; snapshots expire after 15 s; max 256 sectors, 1000 entries/sector and 64 KiB/message. |
| `aisector:global_weather_change`, `aisector:global_time_change` | Paper commands → every Paper backend | `{weatherType,admin}` (`clear/rain/thunder`) and `{timeType,admin}` (`day/night`); no ACK. |
| `aisector:admin_tp_request` → `aisector:get_location_for_admin_tp` → `aisector:admin_location_response` | Paper `/tp` → Velocity → hosting Paper → Velocity | Request `{adminUUID,targetName}`; response `{adminUUID,targetServerName,location:{world,x,y,z,yaw,pitch}}`. |
| `aisector:tpa_request`, `aisector:tpa_accept`, `aisector:summon_request` | Paper commands → Velocity | JSON request records; acceptance may lead to save/ACK transfer protocol above. |
| `aisector:sektor_request`, `aisector:send_request` | Paper commands → Velocity | JSON requester/target/server data; no durable response. |
| `aisector:gui_data_request` → `aisector:gui_data_response:<uuid>` | Paper sector-info command → Velocity → requesting Paper backend | Request `{uuid}`; response is bounded JSON sector-status array. |
| `aisector:sector_stats` | Paper `SectorStatsPublisher` → Velocity `NetworkListener` | JSON stats keyed by sector name; cache is currently in-memory. |
| `aisector:vanish_update` | Paper `VanishManager` → Paper `VanishUpdateListener` | JSON vanish state, atomically published with the `aisector:vanish:state` hash mutation. A bounded hash snapshot rebuilds a cold backend; live-cache revisions reject stale snapshots. `aisector:admin_chat` remains external/unknown and untouched. |
| `aisector:guild_deleted` | Paper `GuildManager` → all Paper backends | `{tag,generation}`; causes cache/profile membership invalidation. Tag uses the persistent-writer contract (trimmed/normalized, 1–32 chars, no control/colon), not the narrower player-command creation regex; generation is a bounded string (legacy absent allowed). |
| `aisector:maintenance_toggle` | Paper `/server` → Velocity maintenance manager | `{action,by,countdown}`; action `on` starts countdown (bounded 0–300, minimum effective 5 s), `off` clears state. |
| `aisector:maintenance_prepare` | Velocity maintenance manager → Paper backends | `{}` at the five-second warning; no ACK. |
| `aisector:ban_broadcast`, `aisector:ban_kick` | Paper ban/kick commands → Velocity | Broadcast `{type,name,by,reason,perm,minutes}` (kick omits ban fields); kick `{uuid,message}`. Velocity validates fields before broadcast/disconnect. |
| `aisector:gui_data_response:*`, `sector-border-init:*` | Velocity/external initializer → Paper listeners | GUI response is JSON; border-init payload contract is legacy/external and remains unchanged pending integration confirmation. |
| `global:chat` plugin-message channel | Paper player-originated message → Velocity → all backend servers → Paper | UTF string prefixes `[GLOBAL]`, `[GCHAT]`, `[ALLYCHAT]`; Velocity consumes player-originated forged frames and forwards only server-originated frames; Paper enforces packet length and dispatches recognized prefixes. |

The `invsee` request/data channels and any undocumented producer of legacy channels remain intentionally outside this contract until their external integrations are confirmed.

## Delivery and storage boundaries

- `global:chat` is one Java/DataOutput modified-UTF frame; the full plugin message is limited to 32,767 bytes and Paper's decoded text to the validator limit. Pipe-delimited prefixes preserve content after the fixed leading fields; legacy unprefixed fallback remains enabled. Velocity treats it as a trusted-backend channel, never as a player command protocol.
- Pub/Sub has no replay, authentication envelope or durable delivery guarantee. Redis itself must be isolated/authenticated; this rewrite does not change the network access policy. A zero subscriber count is checked where a command requires an active consumer, but a positive count is not a durable operation ACK.
- Vanish events remain ephemeral; cold-backend reconstruction and dynamic permission recalculation are not guaranteed by this channel alone.
- Guild creation/mutation/profile coordination uses token-checked Redis leases with TTL 120 s; Mongo guild mutations additionally check snapshot generation/version. Cross-document cleanup after lease expiry is a known release limitation (see `REWRITE_RELEASE.md`).
- Homes no longer use Redis lease state. `HomeSlotRepository` uses deterministic Mongo slot `_id` and random `_revision` for create/delete; all participating writers must use that protocol. Existing records are not destructively migrated.
- Backup claim `restore_claim` lives in Mongo; pending inventory is Redis `SET NX EX 86400`. Only a definite NX conflict permits token-specific claim rollback. Ambiguous outcomes are manual-review cases.
