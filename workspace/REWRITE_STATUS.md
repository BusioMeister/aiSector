# aiSector rewrite — status wznowienia

## 2026-11-19 — border, przejścia, opcjonalny Spawn + dwa sektory gry

- Border gracza: jedna ściana, narożnik lub brak, zależnie od odległości od krawędzi. Nie podmieniamy właściwego bordera świata; 2 bloki bufora pozostają dla zgłoszenia przejścia.
- Join wykonuje odczyty Mongo i Redis równolegle z jednym `MGET`; zredukowano sztuczne opóźnienia teleportu i powielone kodowanie zbroi/offhand. Przejście ukośne zachowuje współrzędną w osi nieprzecinanej granicy.
- `aiSector/SECTOR_LAYOUT.md` opisuje włączenie opcjonalnego profilu `Spawn`, `GameWest`, `GameEast`, dwie kopie mapy 1000×1000, blokady po stronie Paper/Velocity i losowy teleport przyciskiem na suchej gąbce.
- `clean verify`: 127 testów (shared 9, Paper 96, Velocity 22). Izolowany klient 26.3 na Paper 26.3-256 + Velocity 4.3.1 przetestował: blokadę teleportu poza Spawn i `/server`, zwykły przycisk, wyjście na bezpieczną powierzchnię, pojedynczą ścianę i przejście przez `X=0` z tym samym inventory/XP. Dowody: `C:\Users\Busio\AppData\Local\Temp\opencode\aisector-layout-smoke-pe1_3i_q`. Redis 6379 bez mutacji. Brak porównania opóźnienia z produkcją.
- Lokalna instancja zachowuje światy i OP i uruchamia teraz fork 4.3.1 dla zwykłego klienta 26.3. Testy potwierdziły brak ekranów dla przejścia przez granicę, `/spawn`, `/tppos` i wyjścia przyciskiem na suchej gąbce. To nie jest wdrożenie produkcyjne.

Ostatnia aktualizacja: **2026-11-02**

## Publikacja GitHub — po poleceniu użytkownika

- Repozytoria: `BusioMeister/aiSector`, `BusioMeister/VelocityPlugin`, branche `master`.
- Git CLI odnaleziono w `C:/Program Files/Git/cmd/git.exe`; wcześniejszy brak Git dotyczył PATH, nie instalacji.
- Commity lokalne: Paper `cff1c5d`, Velocity `daa7cb8`. Zawierają kod/testy rewrite, opis wydania, wrapper i zgodny snapshot parent/shared w `build-support/`.
- Oba repozytoria przeszły osobny `build.ps1` z izolowanych katalogów bez nadrzędnego workspace. Logi: `C:\Users\Busio\AppData\Local\Temp\opencode\aisector-github-build-20261102-213545`.
- Nie dodano `Purpur/`, światów, baz, runtime fixture, sekretów ani target JAR-ów. Index export hashes i `diff --check` zweryfikowane.
- **Publikacja zakończona i zweryfikowana 2026-11-03:** po autoryzacji Git Credential Manager wykonano zwykły push obu branchy `master`, bez force-push. `ls-remote` potwierdza Paper `cff1c5d85960012e9fd771d740eb70c4f0c8b3a7` i Velocity `daa7cb8eb1226e4e3ad63fb6b148f57b10e01e5c`. Oba lokalne repozytoria mają czyste drzewa i są zgodne z `origin/master`.
- Ta sekcja zastępuje wcześniejsze stwierdzenia „niecommitowane/niepushowane” w historycznym checkpointcie poniżej.

## Bieżący checkpoint — wykonanie REWRITE_PLAN.md

Ta sekcja oraz `REWRITE_RELEASE.md` mają pierwszeństwo przed historycznymi listami niżej.

- Wykonano zakres implementacji, audyt zgodności i lokalny release smoke. Dawny checkpoint **49/60 jest nieaktualny**. Backlog opisuje wykonane weryfikacje i granice kontraktu; nie jest procentową gwarancją niezawodności ani zgodą na produkcję.
- Finalne `clean verify`: **122 testy** (shared 9, Paper 92, Velocity 21), zero failures/errors/skips. Dodatkowo **8 testów Python** oraz pełny runtime z dwoma oficjalnymi klientami 26.3.
- Poprawiono m.in. cancel/reentrancy/backpressure kolejek, Player-session profile loads, kolejność cache membership, stale quit snapshot, Mongo home identity/revision i stale menu, generation-bound guild CAS i write-failure fence, walidator invalidacji, restore claim rollback, generator lifecycle, Redis PSUBSCRIBE shutdown, async join/warmup oraz nowe/legacy backup itemy.
- Finalny zaliczony runtime: `C:\Users\Busio\AppData\Local\Temp\opencode\aisector-final-smoke-20261102-194913`; backupy światów zweryfikowano SHA-256 (195/141/183 pliki). Logi, screenshoty, `player-results.json`, manifesty i staging-before JAR-y zachowane w tym katalogu.
- Stagingowe JAR-y po ostatnim buildzie są odświeżone i zgodne SHA-256; serwery oraz Redis `6380` zatrzymane kontrolowanie, porty `25565–25568`/`6380` wolne. Redis `6379` nietknięty i odpowiada `PONG`.
- Transfer czeka na zapis profilu przed publish/ACK; source mutation guard sprawdzony przy opóźnieniu kolejki 2 s, fly/god odczytane na celu. Późny quit zapisanego źródła nie nadpisuje profilu celu. Odrzucony pre-publish zapis zwalnia guard. Granice crash/reconnect/external writers nie są globalną transakcją — szczegóły w release.
- Użytkownik zatwierdził publiczny `/spawn`; test nie-operatora z explicit false dla `aisector.setspawn` przeszedł. Inne granty i legacy integracje niezmienione, `/msg` i `/reply` nieaktywne. Historyczny mismatch `/sectorinfo` pozostaje jawny.
- Audyt odczytał dwa lokalne repozytoria Git i zachowane commity, wszystkie 41 executorów Paper oraz mapowania przeniesionych klas (`REWRITE_BASELINE.md`). Standardowe legacy itemy: 1657 + 6 metadata cases. Nie ma blokera braku klienta/Git/cold vanish; nie obiecuje się obsługi dowolnego custom serialized payloadu.
- Raport, finalne SHA-256 i rollback/migration caveats: **`REWRITE_RELEASE.md`**. Zmiany lokalne, niecommitowane i niepushowane; produkcji nie uruchamiano ani nie wdrażano.

## Cel

Stopniowy rewrite aiSector: usuwanie blokującego I/O z głównych wątków Paper/Velocity, poprawa niezawodności transferów między sektorami i zachowanie współpracy modułów `shared`, `aiSector` (Paper) oraz `VelocityPlugin`.

Nie ma matematycznego procentu ukończenia wszystkich możliwych zachowań. Aktualne dowody i ograniczenia opisuje `REWRITE_RELEASE.md`; dawne procenty, liczby testów i blokery w historii niżej nie są aktualnym checkpointem.

## Historia zmian 2026-11-01 — poniżej zachowano wcześniejsze etapy, nie aktualny stan release

- 2026-11-01: `/spawn` używa `teleportAsync` przy spawnie lokalnym, potwierdza wynik na głównym wątku i odrzuca niekończące się współrzędne z uszkodzonego wpisu Redis.
- 2026-11-01: Velocity szereguje odczyt/aktualizację preferowanego sektora przy join i zapis ostatniego sektora przy disconnect per UUID; szybki reconnect nie może nadpisać nowszego stanu starszym zapisem. Dodano testy kolejności, izolacji UUID i kontynuacji po błędzie.
- 2026-11-01: `/tpaccept` atomowo przejmuje oczekujące TPA; podwójna akceptacja nie publikuje dwóch teleportów, a sukces jest komunikowany dopiero po publikacji Redis. Przy błędzie publikacji aktywna, niewygasła prośba wraca do stanu oczekującego; expiry usuwa warunkowo także claim tej samej generacji.
- 2026-11-01: nowe TPA nie zastępuje prośby, której akceptacja jest już w toku; publikacja po stronie Redis nie zostanie odłączona od stanu przez równoległe żądanie.
- 2026-11-01: payload lokalnego i międzysektorowego TPA jest parsowany przez wspólny walidator (typy, nazwa świata, finite coordinates, world bounds i pitch). Pakiety trafiają na kanał konkretnego backendu requester-a; brak subskrybenta przywraca prośbę zamiast potwierdzać fałszywy sukces.
- 2026-11-01: transfer snapshot Paper ma limit 4 MiB, waliduje finite/range dla lokalizacji, health/food/XP/slotów/saturation i limituje liczbę efektów; malformed potion entries są pomijane zamiast odrzucać cały transfer. Dodano czyste testy walidatora.
- 2026-11-01: Velocity odrzuca brakujące/nadmierne payloady plugin-message global chat i obniża per-wiadomość logowanie z INFO do DEBUG; callback preferowanego sektora join bezpiecznie ignoruje scheduler zamknięty podczas shutdownu.
- 2026-11-01: failed preferred-sector connection now informs the still-online player via Velocity scheduler; added boundary tests for global chat message sizes.
- 2026-11-01: Paper `PlayerDataListener` wraps async join/profile/guild/Redis completions in a shutdown-safe main-thread scheduler helper; async callbacks no longer throw when Bukkit stops accepting tasks.
- 2026-11-01: Paper ban writes now atomically update ban hashes/index and publish notifications through Lua; each ban has a unique `ban_id`. `/unban` removes hash/index atomically, while `/banlist` uses `HGETALL`, bounded `SSCAN`, compare-and-delete for expired records and conditional orphan-index cleanup to avoid deleting a concurrent re-ban.
- 2026-11-01: `CommandResponseListener` limits Redis messages to 1 MiB, validates request ID/UUID before snapshotting and tolerates scheduler shutdown; the transfer snapshot and success ACK are stored/published atomically through Lua.
- 2026-11-01: Velocity sector-GUI responses validate requester UUID, cap output to the Paper inventory limit (54 sectors), and safely default malformed/missing TPS or RAM stats.
- 2026-11-01: pending cross-server transfer save request now fails immediately and cleans its token if Redis reports zero Paper subscribers; ACK request IDs are type/length checked before pending-transfer lookup.
- 2026-11-01: Velocity login ban check now safely ignores unresolved/invalid remote IP addresses instead of querying `ban:ip:unknown`; expired ban cleanup compares the observed ban generation atomically before deleting.
- 2026-11-01: preferred-sector join callback is guarded by a per-session random generation token invalidated on disconnect, preventing stale Mongo reads from routing a rapid reconnect; registry entries are removed on disconnect. Global spawn reads now share strict field/range validation for `/spawn` and respawn, and `/setspawn` ignores late callbacks after shutdown/disconnect.
- 2026-11-01: login ban lookup plus expiry/index cleanup now runs as one Redis Lua operation, eliminating the expiry-read/reban race that could admit a player with a newly renewed ban.
- 2026-11-01: Paper respawn cache refreshes use monotonically increasing request generations, so overlapping Redis reads cannot let a slow older response overwrite a newer spawn value.
- 2026-11-01: Velocity global-chat plugin messages are explicitly marked handled after source validation, preventing default forwarding to the player in addition to the intentional backend relay.
- 2026-11-01: offline death-backup restore no longer overwrites an existing pending inventory; Mongo CAS is committed before the pending value becomes visible, Redis uses `SET NX EX`, and a confirmed pre-existing pending backup triggers a conditional restore-marker rollback. Backup JSON is capped at 1 MiB.
- 2026-11-01: pending offline backups are now read during Paper join, fully decoded before inventory mutation, and removed only by compare-and-delete after successful application; malformed data is retained and the login restore fails closed. Inventory schema/slot/size validation has unit tests.
- 2026-11-01: `NameResolver` now rejects non-Minecraft usernames before Redis/Mongo lookups and bounds Mojang response parsing; username boundary tests added.
- 2026-11-01: schematic test command is now operator-permission gated; names are bounded and file/symlink resolution is confined to the configured schematic directory.
- 2026-11-01: setting a home now uses Mongo replace/upsert keyed by `(player_uuid, home_slot)` instead of insert-only, preventing duplicate slot documents on retries.
- 2026-11-01: SetHome GUI now obeys its displayed right-click instructions for both setting and deleting a slot; other click types do not start persistence work.
- 2026-11-01: Velocity consumes a transfer save ACK only after validating boolean `success`, string UUID, and equality with the pending transfer player; malformed/mismatched ACKs leave the valid request pending. Regression tests added.
- 2026-11-01: cross-server save requests register a 30-second per-player request token before Pub/Sub; Paper checks it atomically before writing `player:data` or publishing ACK, preventing delayed stale snapshots from overwriting a newer transfer.
- 2026-11-01: pending transfer request state is now canceled on Velocity disconnect; token and target cleanup are compare-and-delete guarded so a replacement request is preserved.
- 2026-11-01: deleting a home now uses Mongo `deleteMany` for the player/slot pair, cleaning up historical duplicate slot documents rather than leaving one behind.
- 2026-11-01: offline backup restore now rolls back its Mongo marker only for a confirmed `SET NX` conflict; ambiguous Redis errors retain the claim and report manual verification rather than risking duplicate restoration. Transfer save Lua restores the prior snapshot (including its TTL) if it finds no ACK subscriber.
- 2026-11-01: Velocity now serializes transfer save-token publish/clear operations per player and atomically pairs pending-map replacement with enqueue order; late publish completions for replaced requests cannot be reported as active transfer failures.
- 2026-11-01: negative/stale transfer ACK paths now also compare-and-clear their request token, avoiding leftover per-player token state while preserving a newer request.
- 2026-11-01: protocol audit found `/alert` publishing raw text to an unconsumed `aisector:alert` channel despite an existing registered `AlertPacket` handler. It now publishes the shared packet envelope on `aisector:packet`, and Paper renders the legacy-formatted announcement.
- 2026-11-01: permissions catalog audit found `/heal <player>`, operator-gated `/pastetest`, command-level `gildie.admin`, and command aliases `ai.sector.info`/`aisector.setspawn` missing from `permissions.yml`; nodes are now listed without changing the established grant policy.
- 2026-11-01: callback audit fixed `/weather` to use the shutdown-safe Bukkit scheduler and report missing Redis subscribers; `/wyjebane` now toggles `pm_disabled:<uuid>` in one Redis Lua operation, avoiding concurrent exists/set/delete races.
- 2026-11-01: `/pastetest` and `/setspawnsector` completions now use the shared shutdown-safe Bukkit handoff and re-check player online state before messaging.
- 2026-11-01: vanish/admin Redis listener now enforces payload byte/character bounds, strict UUID/name/action/message types, and shutdown-safe main-thread dispatch; vanish updates are matched by UUID rather than name.
- 2026-11-01: extracted vanish update parsing to a pure payload validator and added regression cases for UUID type, name bounds, and action allowlist.
- 2026-11-01: transfer save request `{requestId,uuid}` is now represented by a shared validated contract used by Paper before lookup/snapshot; malformed JSON primitive types no longer coerce to strings. Shared boundary tests added.
- 2026-11-01: Paper's global-player-list listener now caps Redis input at 1 MiB/65,536 entries and validates every Minecraft name before replacing its snapshot; invalid and oversized updates preserve the last good snapshot.
- 2026-11-01: external `sector-border-init:<sector>` callback now validates bounded channel/UUID input and uses the shutdown-safe Bukkit scheduler handoff before scheduling the delayed border update.
- 2026-11-01: `/banlist` Redis completion now uses the shared shutdown-safe scheduler and ignores offline senders/null results.
- 2026-11-01: przygotowano `REWRITE_BACKLOG.md` z 30 konkretnymi pozycjami audytu; pozycja 1 (`/banlist`) wykonana i zweryfikowana.
- 2026-11-01: backlog rozszerzono o drugie 30 zadań, pozycje 31–60, dotyczące persistence, GUI/inventory, gameplay, permissions/config i release compatibility.
- 2026-11-01: `CommandResponseListener` przeniesiono na wspólny shutdown-safe scheduler; dodano parsery/allowlisty dla lokalnego TP, admin-location, pogody i czasu oraz testy walidacji. Odpowiedź TP nie jest publikowana bez mapowania na sektor.
- 2026-11-01: pozycja 2 backlogu oznaczona jako wykonana po regresjach parsera Redis; pełny build i staging hash audit zaliczone.
- 2026-11-01: pozycja 3: `GuiDataListener` odrzuca oversized/nieprawidłowe UUID i parsuje ograniczony payload poza wątkiem głównym; Bukkit player lookup oraz inventory GUI są wykonywane wyłącznie na głównym wątku przez shutdown-safe scheduler.
- 2026-11-01: pozycje 4–5: guild-deletion completion używa wspólnego scheduler handoff; SetHome GUI nie wykonuje już late completion na nowej sesji tego samego UUID i zwalnia pending slot także przy shutdown.
- 2026-11-01: pozycja 6: Paper join/delayed callbacks odrzucają nieaktualną instancję Player; boundary transfer pending state używa unikalnego tokenu przy timeout cleanup. `PlayerQuitListener` jest pusty i niezarejestrowany — pozostawiony do parity review.
- 2026-11-01: pozycje 11–14 Velocity: whitelist async odpowiedzi schedulowane z kontrolą sesji i lifecycle; `/r` ponownie sprawdza obie sesje oraz limituje wiadomość; maintenance i ban/kick payloady mają ścisłe typy, zakresy i limity, a callbacki nie wykonują skutków ubocznych po shutdown.
- 2026-11-01: pozycje 15–17: transfer po save ACK zabezpieczono przed timeout/ACK/reconnect race przez pending-object identity i re-check aktywnego Player przed/po connect; disconnect nadal czyści transfer i token.
- 2026-11-01: pozycje 18–21, 23, 25–28: poprawiono proxy shutdown/login timeout/presence collision; dodano `REDIS_PROTOCOL.md`; ograniczono PacketEnvelope payload i udokumentowano plugin-message, maintenance, ban/kick, spawn/weather/time kontrakty. Kick emituje broadcast i disconnect w jednej Redis Lua operacji.
- 2026-11-01: pozycje 22 i 24 domknięte dla potwierdzonych kanałów; nieznane kanały legacy zachowano bez zmiany. Dodatkowo 36, 41–42: walidacja home payloadów i cross-backend world name, ochrona reconnect w GUI oraz drag/click cancellation.
- 2026-11-01: pozycje 33–34: profile executor ma jawny bounded shutdown wait; async Mongo write APIs snapshotują mutable BSON values przed enqueue, z testem rekurencyjnej kopii. Pozostałe pozycje 31–32 oraz 35 wymagają dalszego race/DB audit.
- 2026-11-01: pozycja 58: `SectorManager` odrzuca niecałkowite/niebezpieczne bounds, duplikaty case-insensitive i overlap; rejestracja jest all-or-nothing, dodano testy validatora.
- 2026-11-01: pozycje 43–44 i 47: Backup GUI blokuje drag/click item transfer; restore pipeline utrzymuje CAS claim/NX pending backup/manual-review dla niejednoznacznego Redis outcome. Generator PDC enum parsing jest allowlisted i testowany.
- 2026-11-01: `plugin.yml`/executor audit znalazł permission mismatch dla `sectorinfo` (`ai.sector.info` w descriptorze vs `aisector.command.sectorinfo` w executorze); pozostawiono bez zmiany do decyzji access-policy. `/reply` nadal nie jest rejestrowane.
- 2026-11-01: Hotbar delayed sync weryfikuje sesję/lokację/slot na każdym kroku i unieważnia starsze wywołanie per UUID; inventory object deserialization dostała byte/resource limits i test szybkiego odrzucenia oversized inputu.
- 2026-11-01: backlog 52 zakończony; pozycja 45 pozostaje częściowa: dodano zasobowe filtry serializacji, ale ścisła klasa allowlist wymaga testu na docelowym Bukkit/Paper, by nie złamać starych backupów.
- 2026-11-01: generator lifecycle: dismantle czeka na trwały Mongo delete przed zmianą bloku/nagrodą i jest zabezpieczone claimem per location; regen deduplikowany, nie ładuje chunków i nie nadpisuje zmienionego bloku. Piston, burn i eksplozje chronią generatory; pozycja 48 wymaga jeszcze runtime failure/restart smoke.
- 2026-11-01: pozycja 51: generator PDC ma wersję 1; brak wersji jest obsługiwany jako legacy v0, a nieznana wersja i niezgodny materiał są odrzucane. Dodano testy parsera.
- 2026-11-01: pozycja 55 częściowa: `tools/check_permissions.py` raportuje 40 literalnych node'ów i wykrywa jedyny mismatch `/sectorinfo`; grantów nie zmieniono. Pozycja 56: `/tppos` ma ścisły finite/world-height parser, block-floor routing i `teleportAsync` z kontrolą sesji/border.
- 2026-11-01: pozycja 57: przegląd sender/world edge cases komend lokalnych; `/setspawnsector` odrzuca late callback po rozłączeniu/reconnect, a `/tppos` nie przyjmuje wartości non-finite/out-of-world.
- 2026-11-01: vanish join czeka na async profil, weryfikuje Player session i odtwarza tagi z lokalnego cache zdalnych updates dla dołączającego staff; testy UUID/state/name validation dodane. Dynamic permission changes i snapshot dla świeżego backendu pozostają ograniczeniami Pub/Sub.
- 2026-11-01: Guild PvP sprawdza właściciela projectile i fail-closed przy cache miss profilu; test geometrii square claim boundary. Nie dodano access bypass bez potwierdzonej polityki.
- 2026-11-01: global/guild/ally chat sanitizuje untrusted plain text z legacy formatting introducers oraz control chars przed wstawieniem do legacy broadcastów; sanitizer ma testy. Wersje statycznie sprawdzone: Java 25 lokalnie/release 25, Paper API 26.3 z `api-version` 26.1, Velocity API 4.3.1 provided; wersje deploymentu nadal niepotwierdzone.
- 2026-11-01: staging logs record Paper 26.3 / Java 25 and Velocity 4.3.1. Historical staging logs dated 2026-10-28 contain missing-class failures from an older plugin artifact; current built Paper/Velocity JARs include the previously missing Gson/Mongo/BSON/Commons Pool classes and match staged hashes. Those old logs are not counted as current runtime evidence.
- 2026-11-01: Guild PvP additionally fails closed when a cached player profile references a nonblank guild tag missing from the ready guild cache; ordinary untagged PvP behavior is unchanged. Added a pure policy regression test.
- 2026-11-01: Vanish quit no longer calls the explicit unvanish transition: it clears only local hide/tag state and preserves the persisted/network vanished state across disconnect or backend transfer. Added a cache lifecycle regression case.
- 2026-11-01: CobbleX loot config rejects non-finite/out-of-range chances and non-integral/overflowing reward counts, builds a candidate table before publishing one immutable snapshot, and preserves the last valid table on read failure. Added parser boundary tests.
- 2026-11-01: Velocity maintenance countdown is explicitly closed during proxy shutdown; it cancels the scheduled task and invalidates outstanding state/countdown callbacks before Redis closes.
- 2026-11-01: `UserManager` profile loads and guild-membership writes now share an async per-UUID queue, preventing a stale Mongo read from winning over an ordered membership write; cache construction still runs on the Bukkit main thread and will not replace an existing cache entry. Added queue ordering, key isolation and failure-recovery tests.
- 2026-11-01: `SetHomeGuiListener` uses a token-checked Redis lease per UUID+home slot around Mongo replace/delete, with compare-and-release cleanup; this is best-effort coordination with a 120 s TTL, not a fencing/CAS guarantee for external writers or operations exceeding the lease.
- 2026-11-01: global chat rejects oversized plugin-message frames and decoded text on Paper, with 2 boundary tests; `/banlist` now safely handles late Redis completion after disconnect/plugin shutdown.
- 2026-11-01: added shared shutdown-safe Bukkit scheduler handoff and applied it to async Paper command callbacks; missing Redis subscribers now report failure instead of false success. `/send`, `/alert`, `/tpa`, `/tpaccept`, `/tp`, `/summon`, `/sektor`, maintenance/time and sector-GUI requests validate basic input and guard offline players.
- 2026-11-01: `/home` and `/sethome` callbacks now tolerate disconnect/shutdown, and malformed/duplicate home records are skipped rather than aborting GUI creation. `/kick` and `/unban` callbacks have equivalent shutdown/offline guards; kick reason/name lengths are bounded.
- Velocity: asynchroniczne sprawdzanie banów i whitelisty podczas logowania; asynchroniczne operacje Redis w `NetworkListener`, `MaintenanceManager`, `/vwhitelist`, `/r` i publikowaniu globalnej listy graczy.
- Paper: async Redis/Mongo w komendach `/vserver`, `/sectorinfo`, `/s`, `/wyjebane`; odczyt globalnego spawnu przez cache odświeżany asynchronicznie; asynchroniczny zapis backupu śmierci.
- Paper: async operacje w `BackupGuiListener`, `SetHomeGuiListener`, `/home`, `/sethome`, `/backup`, `VanishManager`, `CommandResponseListener` oraz publisherach graczy i statystyk.
- `PlayerDataListener` pobiera transfer state z Redis asynchronicznie, a modyfikacje Bukkit wykonuje na głównym wątku.
- `SectorManager` zapisuje klucze transferu i publikuje transfer przez Redis async.
- `GuildManager`: odświeżenie cache jest stale-while-revalidate; Mongo writes/deletes trafiają do pojedynczej kolejki zachowującej kolejność, a kolejka jest opróżniana podczas wyłączania pluginu.
- `UserManager.loadOrGetUser()` nie robi już synchronicznego odczytu Mongo przy cache miss; uruchamia ładowanie asynchroniczne.
- Wspólny `MongoDatabaseManager` ma async metody find-many, insert, update i delete.
- GeneratorManager: wczytywanie i mutacje Mongo nie blokują głównego wątku; zapisy mutacji są kolejkowane. Snapshot przy shutdown jest wykonywany przed zamknięciem Mongo.
- 2026-10-29: przejrzano wszystkich callerów `UserManager.loadOrGetUser()`; po cache miss mogą zwrócić `null`, a aktywne callery obsługują ten przypadek.
- 2026-10-29: `GuildManager.reloadGuildAsync()` współdzieli oczekujące odświeżenie per tag; `GuildCommand` wstrzymuje komendę, odświeża brakującą gildię i ponawia ją po zakończeniu odczytu.
- 2026-10-29: zapis gildii nie używa już upsertu, więc stary cache nie odtworzy gildii skasowanej na innym backendzie; kolejka ponawia błędy przejściowe trzykrotnie.
- 2026-10-29: dokumenty gildii zawierają `_version`; zapisy Mongo są warunkowe, a konflikt cache między backendami odrzuca stary zapis i odświeża cache zamiast nadpisywać nowsze dane.
- 2026-10-29: dodano testy regresyjne dla filtrów wersji Mongo — pierwszy zapis obsługuje dokument legacy bez `_version`, kolejne wymagają dokładnej wersji i inkrementują ją atomowo.
- 2026-10-29: po konflikcie wersji online role członków są rekoncyliowane z odświeżoną gildią, żeby nie zostawiać lokalnego cache User z odrzuconymi zmianami.
- 2026-10-29: `saveGuild()` zwraca future; błędy końcowego zapisu/konflikty wersji logują się i informują online ownera o konieczności ponowienia zmian.
- 2026-10-29: usuwanie gildii jest asynchroniczne, używa tej samej blokady Redis co create i CAS po `_version`; po usunięciu czyści też guildTag/guildRole offline członków.
- 2026-10-29: `/ga usun` i `/gildia rozwiaz` czekają na potwierdzenie Mongo przed czyszczeniem online cache i wysłaniem komunikatu sukcesu.
- 2026-10-29: po nieudanym zapisie gildii cache online jest rekoncyliowany z aktualnym dokumentem Mongo, a online owner dostaje komunikat o ponowieniu.
- 2026-10-29: blokada create/delete jest współdzielona przez wszystkie backendy; profile i cache online aktualizują się po potwierdzeniu delete, a po konflikcie są rekoncyliowane z Mongo.
- 2026-10-29: usunięcie gildii publikuje wersjonowaną invalidację Redis; pozostałe backendy odświeżają cache i czyszczą lokalnych członków, jeśli dokument tej generacji już nie istnieje.
- 2026-10-29: profile członków online i komunikaty sukcesu są aktualizowane dopiero po potwierdzeniu wersjonowanego zapisu Mongo przy usuwaniu gildii.
- 2026-10-29: odtworzono lokalny Redis stagingowy na porcie `6380`; `PING` zwraca `PONG`. Serwery Paper/Velocity pozostają wyłączone.
- 2026-10-29: początkowy odczyt kolekcji gildii przeniesiono poza wątek startowy. Komendy/admin, ochrona regionu i PvP respektują stan `GuildManager.isReady()`; po załadowaniu cache tagi są odświeżane.
- 2026-10-29: tworzenie gildii korzysta z krótkotrwałej blokady Redis per znormalizowany tag, sprawdza Mongo pod blokadą i zapisuje pełny początkowy dokument przed potwierdzeniem komendy.
- 2026-10-29: zapis profili UserManager jest serializowany per UUID; jeśli właściciel rozłączy się podczas tworzenia gildii, przypisanie gildii trafia również do trwałego profilu.
- 2026-10-29: zapis profilu z guildTag używa tej samej blokady Redis co create/delete i czyści tag, jeśli gildia zniknęła; shutdown Paper opróżnia profile/guild/generator oraz Redis queues przed zamknięciem Mongo.
- 2026-10-29: `HomeGuiListener` pobiera dokument domu z Mongo async przed uruchomieniem teleport warmup.
- 2026-10-29: `UserManager.close()` i `GuildManager.saveAll()` czekają do 35 s na kolejki zapisów; po timeout logują problem i przerywają oczekiwanie przed zamknięciem Mongo.
- 2026-10-29: `GeneratorManager` nie wykonuje destrukcyjnego snapshotu, jeśli asynchroniczny initial-load nie zakończył się; rejestracje/usunięcia z czasu ładowania nie są nadpisywane przez stare dane.
- 2026-10-29: podczas shutdownu `GeneratorManager` najpierw opróżnia kolejkowane mutacje Mongo; jeśli initial-load jest niekompletny, pomija snapshot kasujący istniejące rekordy.
- 2026-10-29: wspólny `MongoDatabaseManager` używa teraz własnego executora I/O i opróżnia go przed zamknięciem klienta; operacje Velocity join/disconnect/transfer korzystają z jego async API.
- 2026-10-29: `GeneratorManager.saveAll()` pomija czyszczenie kolekcji, jeżeli początkowy odczyt generatorów nie zakończył się przed shutdownem.
- 2026-10-29: merge początkowego odczytu generatorów jest wydzielony i testuje, że initial load nie nadpisuje rejestracji ani nie przywraca usunięcia.
- W stagingu zachowano kopie poprzednich JAR-ów jako pliki `.previous`; bieżące JAR-y stagingowe mają SHA-256 zgodne z artefaktami buildu.

## Weryfikacja

- Ostatni build: ` .\mvnw.cmd -B -ntp clean verify ` — BUILD SUCCESS (2026-11-01; 107 testów: 9 shared, 80 Paper, 18 Velocity).
- Testy: 9 `shared`, 80 Paper, 18 Velocity; 0 failures/errors/skips; dodatkowo 3 testy Python.
- Redis: trwała instancja WSL na `127.0.0.1:6379` odpowiedziała `PONG` po shutdownie; stagingowy Redis `6380` został zatrzymany kontrolowanie przez `SHUTDOWN NOSAVE`. `FLUSH` nie wykonywano. Uwaga: pierwsze uruchomienie Velocity nie miało zmiennych środowiskowych i użyło domyślnego Redis `6379` oraz Mongo `users`; możliwy zapis listy graczy/preferowanego sektora. `DBSIZE` Redis `6379` pozostał równy 1. Niczego tam nie usuwano ani nie przywracano.
- Staging Paper Sector1/Sector2/Sector3 i Velocity zostały zatrzymane kontrolowanie; porty `25565–25568` są wolne. Nie przywracano backupów ani nie modyfikowano ich światów poza wcześniejszym smoke schematu.
- Staging JAR-y odświeżone po finalnym verify; SHA-256 Paper `1830ddc4ad4fa7c24c22c63e5d4447b69bb394809ca0439ed0618a9994b2906f` na wszystkich 3 backendach, Velocity `b310192291f78f15c6d0410b8ccd3c00953be0df574a3464d453fb312d84e8a8`; zgodność zapisana w finalnym smoke manifest.
- 2026-11-01 schematic smoke: na Sector1 zainstalowano oficjalny WorldEdit `7.4.6-beta-02` zgodny z Paper `26.3`; plugin wybrał adapter `v26_3`. W `plugins/aiSECTOR/schematics/aisector-smoke.schem` utworzono schemat 5×4×5 (platforma 5×5 i kolorowy znacznik; powietrze jest ignorowane). SHA-256 WorldEdit `166365C44BAD4CD916200B288CF391D3770E3197D1ECA84FF74F3914D933E289`; schematu `C64C81EAB7873BF17566F24141AE0A02FC2FC9A6324AA277D4EE6B05D26B3532`. Gracz wykonał `/pastetest aisector-smoke` i potwierdził, że wklejanie działa.
- Przed wklejeniem i po kontrolowanym shutdownie Sector1 zweryfikowano SHA-256 backup świata (86 plików): `C:\Users\Busio\AppData\Local\Temp\opencode\aisector-world-backup-20261101-before-schematic-paste\Sector1-world`. WorldEdit i schemat są obecnie zainstalowane tylko w stagingowym Sector1; inne staging backendy pozostają bez WorldEdit.
- Runtime smoke: Sector1 uruchomił się bez graczy; Paper zakończył startup, `GuildManager` załadował cache (0 guilds w staging DB), `GeneratorManager` załadował 0 generatorów, a komenda `stop` zapisała chunky. Serwer został ponownie zatrzymany.
- Runtime smoke multi-backend: dwa Paper backendy wystartowały z aktualnymi JAR-ami. Równoległe utworzenie tagu `RWX31` dało dokładnie jedną odpowiedź `CREATED`, drugą `DUPLICATE`; usunięcie zwróciło `true`. Oba backendy wykonały `stop`, zapisały chunky i porty są wolne.
- Runtime CAS smoke: Sector1 utworzył `VRC29`, Sector2 wczytał kopię v0, Sector1 zapisał v1; zapis starej kopii Sector2 został odrzucony, a cache odświeżył się do `fresh-version`. Usunięcie gildii zakończyło się `true`; oba backendy zatrzymano normalnie.
- Runtime invalidation smoke: Sector2 wczytał gildię `GDV30` do cache; po usunięciu przez Sector1 odbiornik Redis wyczyścił cache Sector2. Oba backendy zakończyły normalny shutdown.
- Runtime profile smoke: Sector2 zapisał profil offline z tagiem `PVH29`; po usunięciu gildii na Sector1, Redis invalidation wyczyścił cache Sector2, a ponowny zapis profilu usunął nieaktualny guildTag i guildRole.
- Runtime generator smoke: zarejestrowano generator testowy, przy restarcie async loader odtworzył go (`Loaded 1`), następnie usunięto go i shutdown zapisał snapshot pustej kolekcji (`Saved 0`).
- 2026-11-01 runtime connectivity smoke: Paper Sector1/Sector2/Sector3 uruchomiły się na portach `25566–25568`, aiSECTOR połączył się z Mongo `aisector_staging`, każdy backend załadował 3 sektory, 0 gildii i 0 generatorów; Redis staging `6380` potwierdził subskrypcje backendów.
- 2026-11-01 proxy/player smoke: Velocity odpowiada na `127.0.0.1:25565`; po wykryciu błędnego domyślnego endpointu proxy zrestartowano z `AISECTOR_REDIS_PORT=6380` i `AISECTOR_MONGODB_DATABASE=aisector_staging`. `PUBSUB NUMSUB sector-transfer` na staging Redis wynosi 1. Gracz połączył się przez Velocity, przechodził między Sector1/2/3, a `/spawn` przeniósł go między backendami; użytkownik potwierdził „działa”. Wcześniejsze próby przed poprawką zgłaszały brak subskrybenta — nie traktować ich jako wynik końcowy.
- Backupy światów przed smoke są w `C:\Users\Busio\AppData\Local\Temp\opencode\aisector-world-backup-20261101-before-proxy-smoke` (zweryfikowane SHA-256: Sector1 86, Sector2 86, Sector3 82 pliki) oraz wcześniejszy backup Sector2/Sector3 w `C:\Users\Busio\AppData\Local\Temp\opencode\aisector-world-backup-20261101-212500`. Nie przywracano backupów; na stagingowych światach wykonano normalne testy wejścia/teleportu, a gracz użył `/setspawnsector`. Dalsze testy TPA/respawn/reconnect/shutdown pozostają do wykonania.
- 2026-10-29: profil z guildTag jest zapisywany tylko wtedy, gdy UUID nadal figuruje jako członek gildii w Mongo; chroni to przed ponownym użyciem tego samego tagu przez nową generację.
- 2026-10-29: opcjonalne MySQL pool/tabele inicjalizują się poza `onEnable`; shutdown zamyka pool również, gdy inicjalizacja kończy się już po rozpoczęciu wyłączania.
- 2026-10-29: `/ga fixuser` zapisuje reset gildii do Mongo asynchronicznie i dopiero po potwierdzeniu pokazuje sukces.
- 2026-10-29: `/ga list` pobiera listę sieciową z Mongo async; `/ga usun` odświeża cache async przy cache miss zamiast zgłaszać fałszywie brak gildii.
- 2026-10-29: odczyt i deserializacja pliku schematu w `/pastetest` i tworzeniu gildii odbywają się poza głównym wątkiem; WorldEdit paste pozostaje na wątku serwera.
- 2026-10-29: `GuildCommand` czeka na zapis guild/member przed utrwaleniem zmian roli lub członkostwa; błędy profilu są raportowane wykonującemu komendę.
- 2026-10-29: powitania, PvP, zastępca, sojusze, zaproszenia i ustawienie domu dopiero po powodzeniu trwałego zapisu wysyłają success/broadcast lub aktualizują GUI/tagi.
- 2026-10-29: wiadomości sukcesu i efekty poboczne dla powitań, PvP, zastępcy, sojuszy, zaproszeń i domu gildii są wykonywane dopiero po udanym zapisie Mongo.
- 2026-10-29: przy cache miss `/gildia dolacz`, `akceptuj`, `sojusz` i `info` asynchronicznie pobierają docelową gildię przed odpowiedzią o jej braku.
- 2026-10-29: `GeneratorManagerMergeTest` symuluje wczytanie Mongo podczas lokalnego register/unregister i chroni merge przed przywróceniem starej wartości.
- 2026-10-29: `UserManager.loadUserAsync()` pobiera dokument profilu i domy równolegle przez async API `MongoDatabaseManager`; budowa cache Bukkit nadal odbywa się na głównym wątku.
- 2026-10-29: `NameResolver` po cache miss w Redis/Mongo odpytuje profil Mojang przez `HttpClient.sendAsync`; usunięto `Bukkit.getOfflinePlayer(...)` z awaryjnej ścieżki, która mogła blokować główny wątek.
- 2026-10-29: Velocity usuwa porzucony `player:data` przy nowym `LoginEvent` (przed dołączeniem do backendu), zamiast asynchronicznie kasować go po `ServerPostConnectEvent`, co mogło ścigać się z Paperowym odczytem transferu.
- 2026-10-29: przywracanie preferowanego sektora po join sprawdza, czy gracz nadal jest na serwerze początkowym; opóźniony odczyt Mongo nie cofnie ręcznego transferu gracza.
- 2026-10-29: `PlayerDataListener` czyści respawn marker, transfer snapshot i teleport targets przez Redis compare-and-delete; nie usuwa nowszego stanu, który pojawił się po odczycie snapshotu.
- 2026-10-29: `SectorManager.transferPlayerAsync()` zapisuje wszystkie klucze transferu i publikuje żądanie proxy jednym atomowym skryptem Redis; brak subskrybenta cofa zapis w tym samym skrypcie.
- 2026-10-29: timeout/błąd transferu administracyjnego na Velocity usuwa teleport target tylko wtedy, gdy jego wartość nadal odpowiada żądaniu, które właśnie anulowano.
- 2026-10-29: target i unikalny token żądania administracyjnego są zapisywane atomowo; cleanup wymaga dopasowania tokenu i wartości, więc stary timeout nie usuwa nowszego celu nawet przy identycznej wartości.
- 2026-10-29: Velocity utrzymuje jedno oczekujące żądanie transferu na UUID; nowsze zastępuje starsze, a ACK przed połączeniem ponownie weryfikuje token i aktualną wartość celu w Redis.
- 2026-10-29: `MaintenanceManager.disableMaintenance()` anuluje aktywne countdown; odczyt startowy, enable i disable są wersjonowane i sekwencjonowane, a lokalny stan aktualizuje się dopiero po udanym Redis.
- 2026-10-29: GUI `/backup` ustawia ownera głowy po UUID z dokumentu zamiast `Bukkit.getOfflinePlayer(nick)`, unikając potencjalnego blokującego lookupu profilu po nicku.
- 2026-10-29: przegląd pozostałych Paperowych wywołań Mongo/Redis nie wykrył synchronicznych zapytań na komendach/eventach; pozostałe wywołania synchroniczne działają wewnątrz dedykowanych kolejek I/O lub shutdownu.
- 2026-10-29: Mongo/Redis I/O, zapisy profili/gildii/generatorów oraz schematics korzystają z ograniczonych kolejek i jawnego odrzucania przy przeciążeniu, zamiast nieograniczonego wzrostu kolejki.
- 2026-10-29: Velocity `OnlinePlayersListener` przechowuje snapshoty sektorów z 15-sekundowym TTL, ignoruje niepoprawne payloady i usuwa wygasłe wpisy, żeby awaria backendu nie zostawiała graczy stale online.
- 2026-10-29: `/banlist` pomija i czyści wygasłe/stare indeksy Redis, nie przerywa całego wyniku na uszkodzonym expires_at oraz pokazuje bezpieczne wartości domyślne dla brakujących pól.
- 2026-10-29: `/banlist` wymaga teraz `aisector.ban.list`, sprawdzanego w plugin.yml i executorze komendy.
- 2026-10-29: GUI ustawiania domów przyjmuje tylko kliknięcia slotów menu i serializuje zapis/usunięcie per UUID+slot, zapobiegając powielonym domom przy szybkich kliknięciach.
- 2026-10-29: fallback Mojang w `NameResolver` cache'uje wyniki (pozytywne 30 min, negatywne 30 s), scala równoległe żądania tego samego nicku i ogranicza liczbę jednoczesnych HTTP lookupów do 8.
- 2026-10-29: `/banip` i `/unban` walidują IPv4/IPv6 literalnie bez DNS i używają kanonicznej postaci adresu; wspólny `NetworkAddressUtil` ma testy adresów poprawnych, granicznych i błędnych.
- 2026-10-29: GUI wyboru domu ignoruje kliknięcia poza slotami menu i scala równoległe odczyty domu per gracz, aby wiele kliknięć nie uruchamiało kilku teleport warmupów.
- 2026-10-29: oczekujące TPA na Velocity wygasają po 60 s; rekord zawiera unikalne ID, a timeout usuwa je warunkowo, nie kasując nowszej prośby. `/tpaccept` dodatkowo weryfikuje termin.
- 2026-10-29: testy Velocity weryfikują TTL snapshotów online (zegar kontrolowany w teście), ignorowanie malformed JSON oraz deadline TPA.
- 2026-10-29: Velocity usuwa TPA także przy rozłączeniu nadawcy lub odbiorcy; compare-remove chroni nowszą prośbę, która mogła zastąpić starą podczas sprzątania.
- 2026-10-29: `SectorPlugin.onDisable()` kończy wszystkie oczekujące futures paste schematów wyjątkowo, a callbacki nie wykonują WorldEdit paste po wyłączeniu pluginu.
- 2026-10-29: Velocity `NetworkListener` izoluje błędy payloadów per wiadomość Redis; pusta/nieprawidłowa wiadomość ani błędny UUID transferu nie przerywa już subskrypcji Pub/Sub.
- 2026-10-29: subskrypcje Redis maintenance/ban w `VelocityPlugin` ignorują i logują malformed JSON/fields per wiadomość zamiast restartować cały subscriber thread.
- 2026-10-29: Paper Redis listeners dla globalnej listy graczy, vanish, GUI sector data i command response walidują payloady i odcinają błędy per wiadomość; `GlobalPlayerListListenerTest` weryfikuje malformed snapshot.
- 2026-10-29: `GlobalPlayerManager` atomowo podmienia niemutowalne snapshoty listy graczy; test potwierdza, że dotychczasowi czytelnicy zachowują pełny stary snapshot podczas aktualizacji.
- 2026-10-29: initial generator merge oraz lokalne register/unregister używają wspólnej blokady; deterministyczny test zatrzymuje merge między sprawdzeniem i insertem i potwierdza, że unregister nie zostaje cofnięty.
- 2026-10-29: `SectorPlugin.onDisable()` czyści rejestr warmupów i bossbarów, również blokady trwających teleportów.
- 2026-10-29: GUI odpowiedzi sektorów waliduje UUID/payload, pomija taski dla gracza po rozłączeniu i ogranicza widok do 54 slotów obsługiwanych przez Bukkit.
- 2026-10-29: `BaseTeleportWarmupTask` zastępuje wcześniejszy warmup danego gracza, zwalnia registry przy cancel i zgłasza sukces lokalnego teleportu tylko, gdy Bukkit go zaakceptował.
- 2026-10-29: lokalny TPA używa tego samego rejestru warmupów, anuluje się po ruchu, a wynik `teleportAsync` i aktualizacja world border są obsługiwane na wątku serwera.
- 2026-10-29: rejestr teleportów utrzymuje lock gracza także podczas asynchronicznego teleportu lub transferu; kolejny warmup jest odrzucany do zakończenia operacji.
- 2026-10-29: merge generatorów korzysta ze wspólnej blokady stanu przy register/unregister, a deterministyczny test odtwarza wyścig pomiędzy sprawdzeniem modified marker i `putIfAbsent`.
- 2026-10-29: lokalne TPA i cross-sector teleport warmupy współdzielą rejestr; lock jest utrzymany do zakończenia `teleportAsync`/Redis transferu, a shutdown Paper czyści taski i bossbary.

## Następny krok

Nie powtarzać wykonanych audytów i smoke bez powodu. Następny krok to zamknięcie konkretnej macierzy klientowej i decyzji z `REWRITE_RELEASE.md` (policy/parity, legacy corpus oraz guild cross-document/TTL strategy). Brak zaliczenia nie jest akceptacją ograniczenia. Przy kolejnych zmianach kodu ponowić testy/hash/smoke; zachować izolację staging `6380`/`aisector_staging`, backupy i niezmienione integracje legacy.

## Kolejka samodzielnej sesji rewrite — 20 punktów

Wykonywać po kolei: audyt → ograniczona poprawka → test/build. Nie uruchamiać stagingu bez potrzeby runtime; Redis jest obecnie niedostępny.

1. [x] Zabezpieczyć pojedyncze przejęcie TPA i sukces dopiero po publikacji.
2. [x] Nie pozwolić nowej prośbie zastąpić claimu TPA w toku; zachować expiry per request ID.
3. [x] Testować przejścia claim/expiry TPA oraz przebudować i zapisać staging po buildzie.
4. [x] Walidować komplet pól i zakres liczb w payloadach teleportu TPA na Velocity.
5. [x] Dodać testy regresyjne dla walidacji payloadu TPA bez serwera runtime.
6. [x] Audyt `/msg` i `/reply`: klasy nie są rejestrowane przez Velocity; nie aktywować niedokończonych handlerów bez specyfikacji.
7. [x] Audyt mapy ostatnich rozmów: brak aktywnego lifecycle, bo handler jest niepodłączony; odłożone do decyzji o aktywacji komend DM.
8. [x] Przejrzeć walidację wejścia i błędy Redis w `/vwhitelist` — operacje async raportują błędy; brak blokującego I/O.
9. [x] Zweryfikować reconnect/backoff (exponential do 30 s) i zamykanie subskrypcji Velocity Redis.
10. [x] Zweryfikować bounded queue/backpressure Redis (kolejka 4096, jawne rejection futures) w obu modułach.
11. [x] Ograniczyć rozmiar global-chat plugin messages oraz spam INFO.
12. [x] Zweryfikować atomowe global player snapshots i TTL cache sektorów (wcześniejsze prace).
13. [x] Utwardzić PlayerDataListener deserializację i limity transfer data.
14. [x] Dodać testy walidacji numeric/location bez runtime Bukkit.
15. [x] Audytować Home/TPA teleport input dla world/null/non-finite/range; TPA ma walidator i teleporty warmup sprawdzają world.
16. [x] Osłonić callback preferred-sector i Paper join-state callbacks przed zamkniętym schedulerem.
17. [x] Potwierdzić shutdown cleanup Paper warmupów i automatyczne anulowanie zadań Bukkit.
18. [x] Zweryfikować per-UUID `UserManager` write tails oraz compare-remove cleanup.
19. [x] Zweryfikować obsługę timeout/error przy końcowym zapisie guild/generator/profile; każdy subsystem loguje timeout i unika zamykania Mongo przed opróżnieniem kolejek.
20. [x] Pełne verify i staging JAR-y wykonane; runtime smoke TPA pozostaje zablokowany do czasu przywrócenia Redis.

## Następna runda audytu — 15 punktów

21. [x] Audytować ban/unban TTL i Redis index consistency przy concurrent admin commands; write/remove są atomowe.
22. [x] Zgrupować zapis bana i notyfikacje w Lua po weryfikacji typów Redis.
23. [x] Ograniczyć `/banlist` do bounded SSCAN, jednego HGETALL na wpis i compare-cleanup.
24. [x] Przejrzeć `CommandResponseListener` UUID/field validation i task scheduling po shutdown; snapshot i success ACK są atomowe.
25. [x] Audytować GUI payloady/limity: Velocity validates UUID and caps at 54; Paper GUI already validates payload and inventory bounds.
26. [x] Sprawdzić teleport warmup/lock cleanup przy quit/disconnect; Paper warmup registry cancels and releases locks on quit/cancel and is cleared on shutdown.
27. [x] Audytować timeout/cleanup transfer tokens; target/token cleanup is conditional, timeout is bounded, and absent save subscribers fail immediately.
28. [x] Przejrzeć login Redis ban listener na invalid client IP/unknown address; unresolved address no longer matches an `unknown` IP ban, and expiry cleanup is generation-conditional.
29. [x] Sprawdzić opóźnione join route przy szybkim reconnect; session tokens invalidate stale callback, with regression tests.
30. [x] Audytować `OnlinePlayersListener`; snapshots are immutable, concurrent-safe, reject malformed payloads and expire by bounded TTL with compare-remove cleanup.
31. [x] Zweryfikować global spawn bounds/fallback: shared strict parser protects `/spawn` and respawn; malformed/out-of-range fixtures have tests.
32. [x] Przejrzeć pending schematic/WorldEdit lifecycle; shutdown completes pending futures exceptionally, async read callbacks check completion before main-thread paste, and executor is closed.
33. [x] Audytować guild cache refresh pod burst; `pendingReloads` scala równoległe odświeżenia per normalized tag, a wersjonowanie/cache TTL chroni przed starym wynikiem.
34. [x] Sprawdzić server names w Redis channels; wspólny builder odrzuca puste nazwy i `:` (separator kanału), konfiguracja per-backend jest źródłem nazw.
35. [x] Dodać regresyjne testy dla reconnect/global-spawn bounds, uruchomić verify i odświeżyć staging.

## Dodatkowa runda protokołów — 10 audytów

36. [x] Sprawdzić default forward global-chat plugin messages; event consumed before source check so player-originated spoof packets are dropped, while backend messages are explicitly relayed.
37. [x] Ograniczyć plugin-message payloady global chat po stronie Paper i długość dekodowanej wiadomości; testy granic dodane.
38. [x] Przejrzeć Redis lifecycle obu modułów; shutdown teraz przerywa też reconnect/backoff Pub/Sub threads, nie tylko async command executor.
39. [x] Zweryfikować maintenance countdown przy toggle/shutdown; close cancels task and invalidates pending callbacks before Redis teardown.
40. [x] Audytować `/banlist` błędy Redis/type mismatch; błąd zwraca fail-safe komunikat i callback jest shutdown/offline-safe.
41. [x] Sprawdzić atomowe claim/replace/expiry i compare-remove przy TPA accept/disconnect; potwierdzone w `NetworkListener`/disconnect listener.
42. [x] Dodać limity 1 MiB do Redis control messages Velocity przed JSON parsing.
43. [x] Ograniczyć wejściowy i wyjściowy tekst globalnego czatu oraz plugin-message packet length.
44. [x] Potwierdzić cleanup tokenów przy błędzie/timeout transferu; callbacki nie wykonują synchronicznego I/O.
45. [x] Full verify (41 testów; patrz aktualna sekcja Weryfikacja), staging JAR-y odświeżone i SHA-256 porównane; Redis `6380` nadal zwraca Connection refused, więc runtime blocker zapisany.
46. [x] Global chat async Mongo callbacks and AsyncChat scheduling use shutdown-safe Bukkit scheduler handoff.
47. [x] Bound Velocity online-sector snapshot payloads, sector count, and player count/name lengths; malformed snapshots do not replace last valid data.
48. [x] Dispatch maintenance Redis callback kicks/broadcasts via Velocity scheduler; re-check state version before kick to suppress stale toggle callbacks.
49. [x] Maintenance-off broadcast now waits for successful Redis deletion and skips if a newer toggle re-enabled maintenance.
50. [x] Cap Redis global-spawn JSON at 4 KiB before parsing on Paper; test covers oversized value.
51. [x] Strictly parse bounded sector-GUI Redis responses (payload/entry/count/name/numeric limits); malformed fields are rejected before Bukkit inventory creation and covered by unit tests.

## Długa kolejka dalszego przeglądu — klasy

Cel: kontynuować audyt istniejących klas porcjami, nie traktować wykonanych punktów jako kompletnego rewrite'u.

- [x] Paper command callbacks: `AlertCommand`, `SectorInfoCommand`, `ServerCommand`, `TimeCommand`, `SendCommand` — shutdown-safe scheduler handoff, offline sender guard i obsługa braku subskrybentów; limity wejścia dla `/alert` i `/send`.
- [x] Paper utility commands: `SektorCommand`, `TpCommand`, `TpacceptCommand`, `TpaCommand`, `SummonCommand` — limity nazw, shutdown/offline-safe handoff, poprawna obsługa braku subskrybenta.
- [x] Paper player commands: `HomeCommand`, `SetHomeCommand`, `UnbanCommand`, `KickCommand`, `SpawnCommand`, `BanCommand`, `BanIpCommand` audited for shutdown/offline callbacks, bounded input, malformed homes and transfer failure behavior.
- [ ] Paper admin/data: `BackupCommand`, `BackupGuiListener`, `HomeGuiListener`, and `GuildAdminCommand` now use shutdown-safe callback handoff; home/backup/guild list rendering skips malformed records. Continue `GuildCommand`, `GeneratorManager` and review backup restore compensation/side-effect ordering.
- [ ] Paper listeners/managers: `VanishUpdateListener`, `HomeGuiListener`, `GuiDataListener`, `CommandResponseListener`, `PlayerRespawnListener`, `SectorManager` — scheduler safety, payloads, stale callbacks.
- [ ] Velocity listeners: `MaintenanceLoginListener`, `BanLoginListener`, `PlayerJoinVelocityListener`, `PlayerDisconnectListener` — reconnect/shutdown state and scheduler behavior.
- [ ] Velocity routing: `NetworkListener`, `MaintenanceManager`, `OnlinePlayersListener`, `GlobalPlayerListPublisher` — message bounds, TTLs, concurrent state and cleanup.
- [ ] Redis/Mongo ownership: `RedisManager` obu modułów, `MongoDatabaseManager`, `GuildManager`, `UserManager`, `GeneratorManager` — queue backpressure, close races, ordering and durable failure paths.
- [ ] Cross-module protocol consistency: channels, schemas, JSON limits, sender authentication and compatibility checks.
- [ ] Na koniec: pełny verify, staging hash check, a po przywróceniu Redis — runtime smoke bez zmian danych świata.

## Seria 40 poprawek — kolejny przebieg

1. [x] Wspólny `BukkitTaskScheduler` odrzuca callbacki bezpiecznie po disable/shutdown.
2. [x] `AlertCommand`: limit treści, offline-safe odpowiedź i brak fałszywego sukcesu bez odbiorców.
3. [x] `SectorInfoCommand`: offline/shutdown guard i błąd, jeśli proxy nie nasłuchuje.
4. [x] `ServerCommand`: wykrywanie braku subskrybenta proxy przy maintenance toggle.
5. [x] `TimeCommand`: offline guard i wykrywanie pustego fan-outu.
6. [x] `SendCommand`: limity nick/sektor i fail-safe fan-out.
7. [x] `SektorCommand`: limit nicku i komunikat dla niedostępnego proxy.
8. [x] `TpCommand`: limit celu i shutdown/offline-safe odpowiedź Redis.
9. [x] `TpaCommand`: limit nicku i wykrywanie braku Velocity listenera.
10. [x] `TpacceptCommand`: sprawdzenie świata lokalizacji oraz bezpieczna obsługa publikacji.
11. [x] `SummonCommand`: limit celu, callback po UUID i status publikacji.
12. [x] `HomeCommand`: shutdown/offline-safe odczyt i pomijanie uszkodzonych/zdublowanych slotów.
13. [x] `SetHomeCommand`: shutdown/offline-safe odczyt oraz ignorowanie błędnych rekordów.
14. [x] `UnbanCommand`: bezpieczne callbacki resolvera i Redis.
15. [x] `KickCommand`: limity wejścia i bezpieczne callbacki.
16. [x] `BanCommand`: limity wejścia i bezpieczne callbacki.
17. [x] `BanIpCommand`: limity wejścia i bezpieczne callbacki.
18. [x] `SpawnCommand`: bezpieczne odczyty/teleport callbacki; transfer błędy obsługuje `SectorManager`.
19. [x] `BackupCommand`: limit nicku i pomijanie wadliwych rekordów GUI.
20. [x] `BackupGuiListener`: callbacki przechodzą przez shutdown-safe scheduler.
21. [x] `HomeGuiListener`: callback korzysta z bezpiecznego schedulera i czyści pending po shutdown.
22. [x] `GuildAdminCommand`: scheduler-safe callbacki i odporność listy na wadliwe dokumenty.
23. [x] `GuildCommand`: limit tagu/nazwy/alertu, bezpieczne callbacki async, pending cleanup przy scheduler shutdown i world-null guard.
24. [x] `BackupGuiListener`: pending backup zapisywany atomowo `SET NX EX`, istniejący backup nie jest nadpisywany, Mongo CAS precedes visibility, a restore marker jest kompensowany przy potwierdzonym konflikcie; payload walidowany przed markerem.
25. [x] `GuildManager`: `saveGuild`/`deleteGuildAsync` odrzucają null/blank/control/oversized/colon tags przed cache/lock mutation; walidator ma regresyjny test.
26. [x] `GuildManager`: refresh współdzielony per-tag, wynik Mongo aplikowany tylko jeśli cache version nie zmieniła się od startu odczytu, a niezapisane cache mutations wygrywają z opóźnionym odczytem; create/delete aktualizują version/generation.
27. [x] `GeneratorManager`: shutdown nie wykonuje już delete-all/insert-many nieatomowego snapshotu; mutation queue jest drenowana przed zamknięciem, a load callback korzysta z shutdown-safe scheduler.
28. [x] `UserManager`: profile load callback sprawdza online session przed wstawieniem do cache, zawsze usuwa pending load, a shutdown/reconnect nie pozwalają staremu loadowi wskrzesić profilu; persisted homes walidowane przez `StoredHomeData`.
29. [x] `UserManager`: guild membership cache nie mutuje przed zapisem Mongo; po sukcesie aktualizuje tylko tę samą, nadal-online sesję na main thread, więc błąd zapisu nie zostawia cache pozornie zatwierdzonego.
30. [x] `SectorManager`: transfer waliduje nazwę sektora, liczbę kluczy metadanych, ich ścisłą allowlistę per-player, wartości i łączny rozmiar payloadu przed Redis; brak proxy usuwa atomowo zapisane klucze.
31. [x] Paper `RedisManager`: command submission odrzuca po close, executor drainuje przy shutdown, Pub/Sub reconnect ma backoff i jest przerwany przy close.
32. [x] Velocity `RedisManager`: command submission odrzuca po close, executor drainuje przy shutdown, Pub/Sub reconnect ma backoff i jest przerwany przy close.
33. [x] Protocol hardening: Velocity inbound message cap, Paper command-response cap, guild invalidation payload/tag/generation validation, and shared per-server channel identifier bounds.
34. [x] `NetworkListener`: transfer destination z Redis walidowany jest jako ograniczony identifier i musi istnieć w proxy przed connection request; pending save ACK jest requestId-bound, timeout cleanup compare-by-request.
35. [x] `PlayerJoinVelocityListener`/`PlayerSectorPersistence`: generation guard unieważnia opóźniony route po reconnect/disconnect; preferred/persisted sector jest ograniczonym identifierem, regresyjny test dodany.
36. [x] `PlayerDisconnectListener`: czyści requesty jako target i requester, zapis ostatniego sektora idzie przez UUID-keyed sequencer po wcześniejszym preferred-sector load/update.
37. [x] `MaintenanceManager`: stan serializowany kolejką Redis; state/countdown generations anulują stare async wyniki i callbacki, close invaliduje generacje.
38. [x] Login policy audyt: ban check jest fail-closed (Redis error deny); maintenance jest fail-open z ostrzeżeniem, aby awaria Redis nie odcinała całej sieci — polityka rozbieżna i jawna.
39. [x] Shared protocol: kanały per-server akceptują wyłącznie bounded `[A-Za-z0-9_-]` identifiers; testy granic, control chars i delimiterów rozszerzone.
40. [x] Ostatni verify (75 testów) i staging hash audit wykonane 2026-11-01 20:49; [ ] runtime smoke wieloserwerowy czeka na działający Redis.

## Plan pracy na dziś — domknięcie rewrite'u

Cel: wykorzystać dzień na zamknięcie wszystkich prac, które nie wymagają aktywnego Redis, a po jego przywróceniu wykonać runtime smoke i release gate. Nie włączać `/msg` ani `/reply` bez specyfikacji; nie modyfikować światów ani robić destrukcyjnych migracji.

1. [x] **Statyczne I/O i lifecycle:** pełne wyszukiwanie sync Redis/Mongo/HTTP/SQL, plików i `Future.get/join`; sync Mongo/Redis callsites są w persistence workers, schematic read w executorze, MySQL init w daemon initializer; brak produkcyjnego blocking future get/join. Mojang response body ograniczone do 16 KiB, request timeout 4 s.
2. [ ] **Trwałość/race sweep:** TPA, transfer save/ACK, backup restore, guild/profile, homes, preferred-sector i maintenance; sprawdzić każde częściowe powodzenie, retry oraz CAS cleanup. Dodać testy czystych state machine, gdzie da się odseparować Bukkit/Velocity.
3. [ ] **Protokół:** spisać źródła, konsumentów i schematy wszystkich Redis Pub/Sub i plugin-message kanałów; walidować typy/limity/wersje po obu stronach, eliminować rozbieżne literal channel names.
4. [ ] **Callbacki/asynchroniczność:** przegląd listenerów i komend Paper/Velocity pod kątem powrotu wątku, offline/reconnect generation, podwójnych callbacków, rejected scheduler i wyjątków.
5. [ ] **Feature parity/config:** inwentaryzacja komend/listenerów/resource/config vs dostępny poprzedni kod; rozdzielić brakujące funkcje od świadomie wyłączonych `/msg` i `/reply`; zweryfikować permissions/defaults.
6. [x] **Test gate (aktualny checkpoint):** `clean verify` BUILD SUCCESS, 63 testy (shared 9, Paper 37, Velocity 17). Staging skopiowany po buildzie, pełne SHA-256 porównane (porty stagingowe wolne).
7. [ ] **Runtime gate po Redis:** sprawdzić Redis PING i Mongo reachability, uruchomić staging bez graczy, potwierdzić plugin startup/initial loads; następnie transfer, respawn, TPA, pending backup, guild invalidation, join/reconnect oraz normalny shutdown. Zatrzymać test przy błędach, nie dotykać światów bez backupu.
8. [ ] **Release checklist:** config/migracje, brak sekretów w logach, artefakty, staging hashes, znane ograniczenia (w tym polityka maintenance fail-open i wyłączone wiadomości), procedura rollback i finalny checkpoint.

### Postęp planu

- [x] Paper sync Mongo callsites są w `UserManager` profile executor, `GuildManager` persistence executor lub `GeneratorManager` writer; Velocity Mongo operations są async.
- [x] `NameResolver` waliduje usernames przed Redis/Mongo, używa request timeout, concurrency semaphore i ograniczonego body subscriber; test granic username dodany.
- [x] `SchematicPaster` filesystem audit: schem names bounded; canonical path and symlink must remain under plugin schematic directory; `/pastetest` restricted to operators.
- [x] Home set retry is idempotent via per-player/slot replace-upsert; same slot is also locally serialized by pending operation guard.
- [x] Transfer save ACK ties `requestId` to the actual pending UUID and rejects non-boolean success before consuming pending state; tests added.
- [x] Transfer snapshot pipeline uses the current Redis request token as a CAS gate; stale Paper save handlers neither overwrite the active snapshot nor ACK a superseded request.
- [x] Transfer pending state is explicitly canceled on disconnect; Redis token/target cleanup remains token/value conditional.
- [x] Per-player transfer save request and token cleanup operations are sequenced; pending replacement and queue registration are atomic with respect to concurrent handler calls.
- [x] Home delete removes all legacy duplicate documents matching the player's slot.
- [x] Backup restore compensation distinguishes confirmed pending-key conflicts from ambiguous Redis outcomes; stale state is not silently made retryable.
- [x] Transfer token publishes and compare-delete cleanup are sequenced per UUID; pending replacement plus queue submission share a critical section, eliminating out-of-order older token writes.
- [x] Transfer negative/stale ACK cleanup now clears its token conditionally; newer request tokens cannot be removed by stale ACK cleanup.
- [x] Protocol audit caught and fixed `/alert`'s dead Redis channel by routing through the existing shared AlertPacket schema/consumer.
- [x] Command registration/permission catalog consistency sweep: all Java-checked nodes are represented in `permissions.yml` or declared in `plugin.yml`; added the two missing catalog entries.
- [x] Callback/race audit: `/weather` handles shutdown/offline/zero-subscriber cases; private-message disable toggle is atomic under concurrent commands.
- [x] Callback sweep also moved schematic test and global spawn-save completions onto shutdown-safe scheduling.
- [x] Vanish/admin channel payloads now have explicit size/type/allowlist validation and reject stale local-player updates by UUID.
- [x] Wstępna mapa krytycznych protokołów: `aisector:save_player_data` Velocity→Paper `{requestId,uuid}`; `aisector:save_player_data:ack` Paper→Velocity `{requestId,uuid,success}`; `sector-transfer` Paper→Velocity `{uuid,server}`; `aisector:packet` uses shared `{id,payload}` envelopes and per-server packet channels; `global:chat` uses Velocity plugin messaging.
- [ ] Protokół do wyjaśnienia przed usuwaniem: Velocity nadal subskrybuje `aisector:invsee_request` i publikuje `aisector:invsee_get_data`, ale w obecnym kodzie Paper `/invsee` działa lokalnie i nie znaleziono ani producenta tego requestu, ani konsumenta odpowiedzi. Traktować jako niepodłączoną/legacy ścieżkę, nie zmieniać jej w feature bez decyzji parity.
- [ ] Dwie kolejne subskrypcje nie mają producenta w tym repozytorium (`aisector:admin_chat`, dynamiczne `sector-border-init:<sector>`); prawdopodobnie kanały zewnętrznej administracji/bootstrapa — zachować, dopóki konfiguracja/integracje sieci nie zostaną potwierdzone.
- [ ] Aktualny fokus: finish protocol channel/schema inventory, documenting source/consumer and payloads; then callback and config/feature parity audits.

Definition of done: brak niezabezpieczonego blokującego I/O na tick/event threads, wszystkie callbacki mają określony lifecycle/error path, protokoły mają limity i walidację, krytyczne mutacje są odporne na retry/race, testy/build i wieloserwerowy runtime smoke przechodzą. Jeśli Redis nie wróci dziś, wyraźnie odnotować jedynie runtime gate jako zablokowany, zamiast fałszywie oznaczać rewrite jako kompletny.

## Środowisko stagingowe

- Ścieżka: `C:\Users\Busio\AppData\Local\Temp\opencode\aisector-staging`
- Redis: WSL Ubuntu, `172.28.201.243:6380` (może się zmieniać po restarcie WSL); na potrzeby lokalnego stagingu protected mode wyłączono, aby Paper na Windows mógł łączyć się przez WSL IP.
- 2026-11-01 20:04 and 20:06: Redis reachability rechecked with `wsl.exe redis-cli -p 6380 ping`; connection refused on WSL localhost. Runtime smoke remains blocked.
- Mongo: `127.0.0.1:27017`, baza `aisector_staging`
- Proxy: `127.0.0.1:25565`; backendy: `25566–25568`
- Skrypt startowy: `C:\Users\Busio\AppData\Local\Temp\opencode\start-aisector-staging.py`
- Próba kopiowania backupu światów wcześniej natrafiła na zablokowany `world\session.lock`; nie zakładać, że pełny backup map istnieje ani nie nadpisywać światów.

## Stan serwerów po wyłączeniu — 2026-10-28

- Wszystkie procesy gry są zatrzymane; porty `25565–25568` są zwolnione.
- Paper Sector1 (PID `22384`), Sector2 (`10044`) i Sector3 (`17296`) dostały `save-all flush`, następnie `stop`; logi potwierdzają zapis graczy i chunków oraz rozpoczęcie normalnego shutdownu około `23:23`.
- Velocity (PID `25760`) został zakończony po zatrzymaniu Paper. Proces był uruchomiony bez wejścia konsoli i nie przyjmował zwykłego sygnału zamknięcia; przed zatrzymaniem nie było graczy online.
- Ostatnie połączenie `BusioMeister_` zakończyło się o `23:19:48`; w chwili shutdownu nie było graczy online.
- Zaktualizowane JAR-y stagingowe pozostają gotowe do następnego uruchomienia.
- Redis WSL nie był wyłączany; odpowiadał `PONG` na `6380` przy weryfikacji.
- Aktualny checkpoint 2026-11-01 po wykonaniu planu: patrz pierwsza sekcja tego pliku i `REWRITE_RELEASE.md`; Paper/Velocity oraz staging Redis `6380` zatrzymane kontrolowanie, JAR-y z finalnego verify skopiowane i zgodne z manifestem SHA-256.
