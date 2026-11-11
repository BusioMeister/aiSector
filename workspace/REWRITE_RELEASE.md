# aiSector — release gate i nierozstrzygnięte warunki

Checkpoint końcowy: **2026-11-02, 19:54**. Implementacja i lokalna macierz release zostały wykonane. Jest to zweryfikowany zestaw JAR-ów do dalszego wydania, **nie wykonane ani zatwierdzone wdrożenie produkcyjne**. Poniższy aktualny wynik zastępuje checkpoint 2026-11-01 zachowany dalej jako historia.

## Finalny wynik 2026-11-02

- `clean verify`: **122 testy** (shared 9, Paper 92, Velocity 21), failures/errors/skips = 0. Log: `C:\Users\Busio\AppData\Local\Temp\opencode\aisector-rewrite-20261102-verify.log`.
- Python: **8 testów** (permissions 3, baseline 2, official client 3). Audyt: wszystkie 41 executorów Paper zachowane; brak niezmapowanych usunięć klas. Referencyjne commity i jawne różnice: `REWRITE_BASELINE.md`, `tools/baseline_inventory.json`.
- **Finalny pełny smoke:** `C:\Users\Busio\AppData\Local\Temp\opencode\aisector-final-smoke-20261102-194913` — `STAGING_SMOKE_OK`, `player-results.json`, logi, screenshoty, manifesty i poprzednie staging JAR-y. Tylko ten przebieg jest finalnym dowodem po ostatnich zmianach kodu.
- 2 oficjalne klienty Minecraft 26.3, losowe offline identities i izolowane katalogi; bez prywatnego konta, hasła lub tokenów Microsoft. Runtime: 3× Paper 26.3, Velocity 4.3.1, Java 25, Mongo `aisector_staging`, Redis `6380`.
- Pełny transport inventory/meta/PDC/armor/offhand/slot/XP, Sector1→2→3→1, reconnect; profile fly/god przełączane przed każdym transferem i weryfikowane na celu. Wymuszone opóźnienie kolejki 2 s nie publikuje transferu przed flush. Próby drop/GUI/komendy w tym oknie są anulowane; odrzucenie zapisu przed publikacją zwalnia blokadę.
- GUI home local/cross-backend, delete/recreate i stare menu; TPA local/cross; publiczny `/spawn` u nie-operatora z jawnym false dla `aisector.setspawn`; respawn; backup online/offline oraz pending conflict z rollbackiem wyłącznie własnego claimu.
- Vanish transfer/reconnect, grant/revoke fixture permission bez reconnect i cold backend po restarcie; generator restart/rejection/cancelled event/regen/dismantle. Nagroda liczona przez rzeczywisty nieanulowany `ItemSpawnEvent`, oddzielnie od liczby nadal załadowanych entity (spadanie/unload chunka nie oznacza braku nagrody).
- PvP direct/projectile i brak gildii, rzeczywista komenda odrzucona przy stale OWNER, edytowalne lokalne invsee z zapisem przy close, Unicode chat i screenshoty GUI. Nie jest to certyfikacja dowolnego zewnętrznego pluginu ochronnego.
- Realne Mongo: 24 równoległych twórców dało jeden nowy profil bez utraconych pól; CAS home, membership i guild generation/version; opóźniony cleanup nie usuwa nowszego same-tag assignment ani upgrade legacy revision.
- Legacy allowlist: **1657 standardowych itemów + 6 przypadków metadata** (skull, leather color, potion, firework, book, PDC); obca klasa odrzucana przed `readObject`. Limity compressed i inflated bytes. Custom/historyczne dane spoza kontraktu są odrzucane, nie przepisywane ani kasowane.
- Backupy światów sprawdzone przed testem: Sector1 **195**, Sector2 **141**, Sector3 **183** pliki, manifest SHA-256. Po zakończeniu porty **25565–25568 i 6380 odrzucają połączenia**, testowe klienty zamknięte, fixture JAR-y usunięte. Redis `6379`: **PONG**, bez mutacji/flush. Brak produkcyjnych operacji Mongo. Końcowy przegląd backendowych logów nie znalazł błędów poza odfiltrowanymi ostrzeżeniami testowego attach; kontrolowane konflikty są oczekiwanym wynikiem regresji.

### Finalne artefakty SHA-256

| Artefakt | SHA-256 |
|---|---|
| `aiSector/target/aiSector-2.0.0-SNAPSHOT.jar` | `76e6e24eed1582ad3925c94ed5302a868711ef81cb0431531508b16bad776205` |
| `VelocityPlugin/target/aiSector-Velocity-2.0.0-SNAPSHOT.jar` | `2f1d6dcd00ed12cb454d58c1cf930b1786f9a1add604eb96728cbf79deb2dff3` |

Wszystkie trzy staging kopie Paper i kopia Velocity mają te same hashe co build. Zmiany są lokalne, niecommitowane i niepushowane.

### Kontrakt transferu i trwałości

`prepareTransferProfileAsync` przechwytuje profil na głównym wątku i czeka na lokalną kolejkę UUID oraz potwierdzenie Mongo **przed** Redis transfer publish / save ACK. `TransferSnapshotGuard` blokuje normalne działania gracza, inventory, damage, pickup, komendy i ruch źródła podczas handoffu; trwały source snapshot nie jest zapisywany ponownie przez późny quit. Invsee/online restore nie nadpisują zamrożonego celu. Po niejednoznacznym błędzie publikacji nie odblokowuje się źródła do dalszej gry; jeśli nie nastąpi przełączenie, po 60 s następuje kontrolowane rozłączenie. Pre-publish profile failure zwalnia guard.

To nie jest globalny fencing wszystkich writerów ani transakcja między Mongo, Redis, Paper i zapisem świata. Dowolny plugin bezpośrednio modyfikujący inventory/profil poza obsługiwanymi eventami, mieszany deployment starych writerów, awaria procesu w połowie apply oraz dowolna kolejność disconnect/reconnect podczas awarii nie mają udowodnionego exactly-once. Guild CAS i revision-specific cleanup chronią obserwowane operacje także po utracie lease, ale nie zapewniają atomowego multi-document delete. Generator Mongo delete i world reward nie są jedną transakcją. Niejednoznaczne restore outcomes pozostają fail-closed/manual-review; nie wolno ich automatycznie ponawiać.

### Polityka i wydanie

Publiczny `/spawn` jest jawnie zatwierdzony przez użytkownika. `/banlist` i `/pastetest` administracyjne, pozostałe granty bez zmian. Historyczny mismatch `/sectorinfo` pozostaje ujawniony i zachowany. `/msg` i `/reply` nieaktywne, `aisector:admin_chat`, `sector-border-init:*` i lokalny edytowalny invsee zachowane.

Usunięto blokery klienta, lokalnej historii Git, standardowego legacy corpus i cold vanish. Produkcja nadal wymaga operatora: zweryfikowanego backupu **Mongo i światów**, poprawnej topologii/forwardingu/endpointów oraz docelowego smoke. Nie kopiować stagingowych ustawień offline na produkcję. Procedura rollback niżej nadal obowiązuje, szczególnie zgodność czytnika `v2:`. Granice powyżej są jawnymi ograniczeniami kontraktu, nie twierdzeniem o uzyskaniu zgody na ryzyko produkcyjne.

## Archiwum — checkpoint 2026-11-01 (nieaktualne blokery i hashe)

## Dowody z tej sesji

- Maven `clean verify`: 107 testów (9 shared, 80 Paper, 18 Velocity), zero failures/errors/skips.
- `python tools/test_check_permissions.py`: 3 testy; inwentarz `tools/permissions_inventory.json` jest wygenerowany, granty niezmienione.
- `python tools/runtime/staging_smoke.py`: rzeczywisty start 3× Paper 26.3 / Java 25 i Velocity 4.3.1, Mongo `aisector_staging`, nietrwały Redis `6380`, zweryfikowane świeże backupy światów i SHA-256 artefaktów.
- Runtime: równoległe tworzenie home przez dwa repozytoria Mongo, jedna tożsamość slotu, delete/recreate z nową rewizją; czyste testy symulują także opóźniony delete kontra odtworzenie slotu i duplikaty legacy.
- Runtime: create/load/save gildii, odrzucenie stale version na drugim backendzie, delete i invalidacja zdalnego cache, recreate tego samego tagu, odrzucenie zapisu zapamiętanego obiektu starej generacji.
- Runtime: cache profilu otrzymuje rzeczywiście zatwierdzone wartości (null/null po odrzuceniu nieistniejącej gildii), a nie żądane OWNER/tag; test używa odizolowanego losowego UUID, którego rekord jest potem usuwany.
- Runtime: Paper item roundtrip z nazwą i lore w nowym formacie v2 i legacy `BukkitObjectOutputStream`; to nie jest dowód zgodności wszystkich historycznych/custom itemów.
- Runtime: osobny test zamykania SUBSCRIBE i PSUBSCRIBE w obu modułach; brak pozostawionych subscriber threads.
- Nowy async join/warmup handoff ma testy potwierdzenia teleportu, zachowania stanu po false/error, odrzucenia starej sesji i scheduler shutdown. Nie zastępują one testu z prawdziwym klientem.

Pełne logi, backup-manifest i artifact-hashes znajdują się w katalogach `C:\Users\Busio\AppData\Local\Temp\opencode\aisector-final-smoke-*`. Nie uznawać wcześniejszej nieudanej próby invalidacji za zaliczoną; błąd walidatora tagów naprawiono i test powtórzono.

Ostateczny zaliczony smoke: `C:\Users\Busio\AppData\Local\Temp\opencode\aisector-final-smoke-20261101-234640`. Manifest zawiera po 86 zweryfikowanych plików świata na backend. SHA-256 Paper (wszystkie trzy kopie) `1830ddc4ad4fa7c24c22c63e5d4447b69bb394809ca0439ed0618a9994b2906f`; Velocity `b310192291f78f15c6d0410b8ccd3c00953be0df574a3464d453fb312d84e8a8`. Porty `25565–25568` i `6380` po smoke odrzucają połączenia; Redis `6379` odpowiada `PONG`, nie wykonywano tam mutacji/flush. Ostrzeżenia JVM o dynamicznym attach są przechwytywane przez Paper jako STDERR/ERROR; są związane wyłącznie z testowym agentem, nie błędem pluginu.

## Aktualny zakres zmian

- Profil: kolejka per UUID ma barierę odporną na cancel/reentrancy i limit pending; odczyt jest związany z instancją Player; cache handoffs są domykane przed shutdownem executora, quit snapshot nie jest właścicielem guildTag/guildRole.
- Homes: nowy wpis używa `_id=home:<uuid>:<slot>` i losowej `_revision`; pusty slot tworzony jest przez insert, nie bezwarunkowy upsert. Delete usuwa wyłącznie obserwowane owner/slot/`_id`+revision, również historyczne duplikaty. `HomeSlotMenu` pamięta odczytane identity, więc stare menu nie skasuje nowszego domu; ma regresję. Nie ma już Redis home lease.
- Gildie: generation jest przypisana do obiektu snapshotu; CAS łączy tag, wersję i generację. Nowa gildia ma deterministyczne `_id=guild:<normalized-tag>`. Kolejka unieważnia już oczekujące snapshoty po konflikcie/końcowym błędzie/rejection. Walidator invalidacji akceptuje ten sam kontrakt tagów co writer.
- Backup: rollback wymaga unikalnego `restore_claim`, a nie tylko adminName; odrzucenie main-thread handoff po claimie uruchamia pending-backup fallback. Błąd apply po claimie wymaga ręcznej weryfikacji zamiast automatycznego odblokowania.
- Nowe backup itemy używają `v2:` i Bukkit byte serialization; reader zachowuje obsługę legacy. Historyczne sloty 36–40 normalizują się na 100–104; jawne canonical armor/offhand mają pierwszeństwo. Nowy writer korzysta ze storage slots 0–35.
- Generator: anulowane eventy nie uruchamiają place/break persistence; callback usunięcia sprawdza generację lokalizacji, a malformed Mongo record nie przerywa całego initial load.
- Join: destination teleport jest async, a Redis cleanup następuje po jego potwierdzeniu; gracz pozostaje pod join hold do zakończenia. Lokalne home/guild warmupy nie używają już sync teleportu.
- Konfiguracja: typy/porty/endpointy Paper sprawdzane są przed inicjalizacją, bez wypisywania sekretów w błędach. Permission audit wykrywa także aliasy, brak rejestracji i advisory catalog-only nodes.

## Co nadal uniemożliwia zamknięcie release

| Warunek | Powód / następne działanie |
|---|---|
| Pełny klientowy test po aktualnych zmianach | TPA, lokalny/cross-sector home, transfer snapshot+ekwipunek, respawn, szybki reconnect, odmowa teleportu i błędy pending backup wymagają zgodnego klienta Minecraft 26.3. Dostępny automatyczny `minecraft-protocol` deklaruje wsparcie najwyżej 26.1. Nie zastępujemy gracza fałszywym potwierdzeniem. |
| Permissions i parity | `/sectorinfo`: descriptor `ai.sector.info`, executor `aisector.command.sectorinfo`; `/spawn` wymaga obecnie `aisector.setspawn`. Potrzebna decyzja o zamierzonej polityce, jeśli ma być zmieniona. Brak pełnej poprzedniej wersji do porównania funkcji; workspace nie jest repozytorium Git, a git CLI nie jest dostępny. |
| Invsee | Obecnie lokalny edytowalny widok z zapisem przy close; nie przerabiać na read-only ani nie aktywować legacy cross-server bez decyzji. |
| Guild lease / cross-document atomowość | Mongo CAS chroni pojedynczą gildię, lecz Redis TTL 120 s nie daje transakcyjnej ochrony sekwencji guild delete + czyszczenie profili po utracie lease. Testy normalnego przebiegu nie dowodzą odporności na dowolnie długą pauzę procesu. Zmiana wymaga uzgodnionego protokołu/migracji (np. generacji w profilach lub transakcji Mongo z odpowiednim deploymentem), nie samego wydłużenia TTL. |
| Legacy deserializacja | Resource limits są aktywne, nowe dane nie używają Java object streams; ścisła class allowlist dla starych itemów wymaga reprezentatywnego historycznego/custom corpus. Bez niego nie można jednocześnie potwierdzić safety i parity. |
| Generator/backup crash boundary | World/inventory mutation i Mongo nie stanowią jednej transakcji. Failure/restart smoke z rzeczywistym graczem/itemami i odpowiednim backupem pozostaje wymagany; sam happy-path nie daje exactly-once nagrody w razie awarii procesu. |
| Vanish/PvP/formatowanie | Zmiana permission bez reconnect, cold backend bez historii Pub/Sub, event matrix innych pluginów ochronnych oraz wizualna zgodność GUI/czatu wymagają specyfikacji/testu klientowego. Nie dodano nowego trwałego protokołu vanish ani access bypass. |
| Produkcyjne endpointy i migracja | Potwierdzono tylko lokalny staging. Nie uruchamiano ani nie rekonfigurowano produkcji. Nie zakładać, że lokalny offline/forwarding NONE jest poprawnym ustawieniem wdrożenia. |

Żaden z tych wyjątków nie jest automatycznie zaakceptowany przez użytkownika. Pozostałe pozycje backlogu pozostają częściowe/otwarte zgodnie z tymi warunkami.

## Procedura wydania / rollback

1. Przed wydaniem zamknąć powyższe gates lub uzyskać świadomą decyzję dotyczącą konkretnych ograniczeń. Zrobić zweryfikowany backup światów **oraz Mongo** z docelowego środowiska. Plan nie upoważnia do przywracania bazy ani światów bez oddzielnej zgody.
2. Zatrzymać backendy i proxy normalnie; sprawdzić brak aktywnych sesji i opróżnienie kolejek. Zapisać poprzednie JAR-y, configi i hashe.
3. Wdrożyć Paper/Velocity jako zgodny zestaw, bez okresu mieszania nowego HomeSlotRepository ze starymi writerami bez revision. Zewnętrzne narzędzia Mongo muszą respektować identity/revision; nie obejmuje ich wewnętrzny kontrakt.
4. Nie usuwać ani nie przepisywać istniejących legacy home/guild rekordów hurtowo. Nowe identity/revision/generation dodawane są przez zwykłe nowe operacje.
5. **Rollback nie jest bezwarunkowym podmienieniem JAR-a:** stare czytniki backupów mogą nie obsługiwać `v2:`. Po nowych zapisach zachować czytnik kompatybilny z v1/v2 lub przygotować osobną, zweryfikowaną migrację. Nie kasować nowych backupów ani nie odtwarzać starszej bazy w ciemno.
6. Powtórzyć build/hash/startup/player smoke na docelowej konfiguracji. W razie niepowodzenia zatrzymać wdrożenie; zachować dane i logi. Automatyczny harness wyłącza tylko swoje procesy i Redis `6380`, nigdy trwały Redis `6379`.

## Ręczna macierz testowa

- Dwóch graczy: TPA local/cross-server, podwójna akceptacja, expiry, disconnect w trakcie warmupu i transferu.
- Przejście Sector1→2→3→1 z pełnym ekwipunkiem/armor/offhand/XP/effects i szybki reconnect w trakcie odczytu profilu. Porównać przed/po, sprawdzić brak utraty/duplikacji i brak pozostawionego join hold.
- Home set/delete/recreate na różnych backendach; nieaktualne menu; guild home w świecie niezaładowanym na źródle.
- Śmierć/respawn i backup online/offline: itemy z meta/PDC, istniejący pending backup, błąd Redis po claimie i restart. Niejednoznaczne wyniki tylko do manual review, nie automatycznego retry.
- Generator placement/break/regen, anulowane eventy ochrony i awaria Mongo/restart; porównać świat, cache, DB i liczbę itemów.
- Vanish + reconnect/transfer/cold backend, zmiana staff permission, PvP/projectiles/region granice, GUI drag/hotbar/offhand, chat/lore formatowanie.
