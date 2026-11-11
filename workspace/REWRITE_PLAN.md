# aiSector rewrite — plan domknięcia

Stan planu: **2026-11-02 — implementacja i lokalna weryfikacja wykonane**. Aktualny raport: `REWRITE_RELEASE.md`. Nie wykonano wdrożenia produkcyjnego.

## Wynik końcowy sesji 2026-11-02

- Wykonano wszystkie pięć faz: kod/regresje, protokoły, parity/policy, runtime z klientami oraz artefakty/cleanup/checkpointy.
- `clean verify`: 122 testy (9/92/21), zero failures/errors/skips; Python: 8 zaliczonych testów.
- Pełny `staging_smoke.py --players`: **`aisector-final-smoke-20261102-194913`**, dwa oficjalne klienty 26.3, wszystkie asercje w `player-results.json` zaliczone, również opóźniony profile flush i source mutation guard.
- Świeże backupy światów i SHA-256 zweryfikowane. Wszystkie własne procesy wyłączone; porty 25565–25568/6380 zamknięte; 6379 PONG bez mutacji.
- Klient/Git/standardowy legacy corpus/cold vanish nie są już blockerami. Wdrożenie i backup produkcyjnego Mongo to osobna operacja operatora. Granice wielosystemowej crash consistency, arbitrary external writers i nieprzetestowanych fault schedules opisano bez obiecywania exactly-once.
- Poniższe plany godzinowe i raport 49/60 są **archiwum**, nie instrukcją ponownego otwierania rozwiązanych spraw ani aktualną liczbą pozostałych zadań. Zmiany niecommitowane i niepushowane.

## Wykonany plan sesji domknięcia — 2026-11-02

Użytkownik polecił kontynuować do końca w tej sesji. Nie pobierać loginu/hasła Microsoft ani tokenów launchera. Aktualne uprawnienia i edytowalny lokalny invsee zachować jako baseline, nie zmieniać grant policy.

Decyzja użytkownika w tej sesji: **publiczny `/spawn` jak w starym commicie**; `/banlist` i `/pastetest` pozostają administracyjne, pozostałe granty bez zmian. Zgoda dotyczy tylko tej jawnej korekty; nie jest upoważnieniem do innych zmian polityki dostępu.

1. Przygotować oficjalnego klienta Minecraft 26.3 z odizolowanymi katalogami gry i losowymi nazwami offline; weryfikować pliki względem oficjalnych hashy. Żadnego korzystania z prywatnego konta.
2. Rozszerzyć istniejący kontrolowany harness (backup/hash/endpoint guard) o 2 prawdziwe klienty: login/hold, profil i ekwipunek, transfer 1→2→3→1, home/TPA, respawn i reconnect. Zbierać dowody z backendu, nie utożsamiać samego otwarcia okna z zaliczonym testem.
3. Naprawiać wykryte regresje i powtarzać konkretny test; dalej wykonać backup/generator/vanish/PvP/GUI matrix tam, gdzie można bezpiecznie zautomatyzować. Testowe uprawnienia mogą dotyczyć wyłącznie losowych fixture players w lokalnym stagingu, bez zmian trwałych grantów i bez opowania realnych kont.
4. Domknąć pozostające ryzyka TTL/legacy serialization i udokumentować dokładnie rzeczywiste gwarancje; nie nazywać wielosystemowej operacji transakcją ani nie zatwierdzać migracji produkcyjnej.
5. Pełny verify, powtórzenie regresji i runtime, aktualizacja wszystkich checkpointów oraz release/rollback. Wyłączyć własne klienty, backendy/proxy i staging Redis 6380; trwały Redis 6379 i produkcja pozostają nietknięte.

Wynik powyżej i `REWRITE_RELEASE.md` zastępują poniższy historyczny checkpoint. Zachowana instrukcja ciągłego wykonania nie upoważnia do wdrożenia produkcyjnego ani zmiany zatwierdzonej polityki.

## Archiwum: wynik wykonania planu — 2026-11-01

Wykonano ciągły przebieg faz kod/test/audyt oraz automatycznej części staging/release. **Nie ogłaszać „ukończonego rewrite’u”: 49/60 punktów jest zamkniętych, a 11 pozostaje częściowych/otwartych.** Dowody, zmiany, nierozstrzygnięte warunki, manualna macierz i bezpieczny rollback są w `REWRITE_RELEASE.md`. Wyjątki nie zostały zaakceptowane przez użytkownika.

| Faza | Wynik |
|---|---|
| 1. Trwałość/race | Poprawki i regresje profilu/home/guild/backup/generator wykonane; znana granica lease TTL i cross-document/world crash consistency pozostaje otwarta, nie jest fencingiem/transakcją. |
| 2. Callbacki/protokoły | Poprawiono dodatkowe async/reconnect paths, join cleanup po potwierdzonym teleportAsync i Redis PSUBSCRIBE close; inventory protokołów zaktualizowane, legacy nietknięte. |
| 3. Config/permissions/parity | Walidator endpointów, audyt aliasów/defaultów/rejestracji i 3 testy Python wykonane. Brak poprzedniego pełnego source i decyzji policy opisany jako blocker, nie arbitralnie zmieniony. |
| 4. Runtime | 3× Paper + Velocity, Mongo homes, guild CAS/invalidation/recreate, profile cache, inventory v2/legacy i Redis lifecycle przeszły. Pełny player smoke wymaga zgodnego klienta 26.3; nie został zaliczony bez gracza. |
| 5. Release | 107 testów Maven + 3 Python, staging SHA-256 i świeże backupy zweryfikowane, procesy wyłączone; release dokumentacja gotowa, produkcja niezatwierdzona. |

Nie uruchamiać ponownie ogólnego „rewrite od początku”. Pracować nad konkretnymi otwartymi warunkami z raportu, po dostarczeniu klienta/corpus/specyfikacji lub podjęciu uzgodnionej decyzji projektowej.

## Ile zostało

- Backlog audytu ma 60 pozycji: **49 zamkniętych, 11 niezamkniętych/częściowych**. Zamknięte pozycje to 81,7% liczby punktów, nie zmierzonego całego rewrite’u.
- Wcześniejsza prognoza 65–70% / 12–20 h dotyczyła stanu przed tym przebiegiem i jest historyczna, nie jest aktualnym pomiarem.
- Dalszy wysiłek zależy od klienta 26.3, danych legacy, baseline parity i decyzji architektury/policy. Nie podawać pozornie precyzyjnego czasu na niepotwierdzone wymagania; wykonywalne warunki są wymienione w `REWRITE_RELEASE.md`.

## Kolejność wykonania

### 1. Domknąć ryzyka trwałości i race — 4–7 h

1. Dokończyć audyt profilu/home: `UserManager`, `SetHomeGuiListener`, `HomeGuiListener`, wszystkie Mongo writer paths. Zastąpić lub ograniczyć lease 120 s, jeśli analiza wykaże writer omijający go albo możliwość zapisu po wygaśnięciu; preferować wspólny, testowalny writer/protokół, nie sam lock bez fencing.
2. Przejrzeć `GuildManager` pozycje 37–40: wersje write-behind po błędzie, TTL/wygaśnięcie locka, retry/backpressure/rejection oraz kolejność invalidation względem create/delete.
3. Zakończyć audyt backup restore compensation i generator break/reward: sprawdzić błędy częściowe/restart; dodać deterministyczne testy poza Bukkit tam, gdzie możliwe.
4. Uzupełnić testy dla profilu/cache i release/cleanup lease. Nie uznawać samego testu generycznej kolejki za test pełnego Bukkit lifecycle.

### 2. Callbacki i granice protokołów — 2–4 h

1. Przejść pozostałe grupy listenerów Paper/Velocity z `REWRITE_STATUS.md`: scheduler/lifecycle, stale session, double completion, walidacja i limity.
2. Dokończyć mapę kanałów i plugin messages w `REDIS_PROTOCOL.md`; sprawdzić producenta/konsumenta, format, limit, obsługę błędu i kompatybilność.
3. Nie usuwać ani nie zmieniać `aisector:admin_chat`, `sector-border-init:*` ani `invsee`; nie uruchamiać `/msg` ani `/reply` bez specyfikacji. Udokumentować je jako zewnętrzne/legacy, jeśli źródła pozostaną nieznane.

### 3. Feature parity, permissions i config — 2–4 h

1. Uruchomić/rozszerzyć `tools/check_permissions.py`; sprawdzić aliasy, defaulty, komendy bez checków i osierocone nody.
2. Zweryfikować mismatch `/sectorinfo` i inne policy-sensitive przypadki, ale **nie zmieniać grantów, dostępu ani zachowania** bez potwierdzenia polityki.
3. Zakończyć inventory configów i release compatibility. Porównać z poprzednim kodem tylko tam, gdzie poprzednia wersja jest dostępna; brak danych opisać jako nierozstrzygnięty parity item.

### 4. Weryfikacja runtime staging — 3–5 h (zależna od środowiska)

1. Najpierw sprawdzić staging config, reachability Redis staging `6380` i Mongo `aisector_staging`, porty oraz istniejące backupy. **Nigdy nie łączyć testowych operacji z Redis `6379`; nie wykonywać flush/cleanup trwałego Redis.**
2. Przed testami światów potwierdzić backup i jego hash; jeśli nie można go zweryfikować, pominąć test dotykający świata.
3. Zbudować JAR-y dopiero po przejściu testów; skopiować wyłącznie do staging, porównać SHA-256.
4. Uruchomić backendy/proxy kontrolowanie i wykonać smoke: startup, transfer, `/spawn`, TPA/local teleport, respawn, reconnect, guild invalidation, home set/delete między backendami oraz normalny shutdown. Dla generatora/backup restore testować tylko po backupie i z kontrolą wpływu na dane stagingowe.
5. Po smoke zatrzymać serwery i nietrwały Redis `6380` kontrolowanie; potwierdzić porty wolne i Redis `6379` nietknięty.

### 5. Release gate i checkpoint — 1–2 h

1. Uruchomić `clean verify`; zapisać liczbę testów i rezultat.
2. Przejrzeć diff/artefakty, staging hashes, config/migracje, sekrety w logach, znane ograniczenia i rollback.
3. Zaktualizować `REWRITE_BACKLOG.md`, `REWRITE_STATUS.md` i ten plan. Każdy punkt bez dowodu ma pozostać otwarty lub jawnie zablokowany.

## Kryteria końca

Rewrite w ramach tego planu jest domknięty, gdy:

- wszystkie pozycje 1–60 są oznaczone jako wykonane z testem/dowodem albo jako jawny, zaakceptowany blocker/specyfikacja zewnętrzna;
- krytyczne writer/race paths mają testy regresyjne lub wskazany bezpieczny runtime test;
- kanały i niezmieniane legacy integracje są udokumentowane, a permissions nie zostały zmienione bez decyzji;
- pełne `clean verify` przechodzi;
- staging smoke/release gate przechodzi albo jest dokładnie opisany jako zablokowany przez niedostępny Redis/Mongo/backup/specyfikację;
- serwery testowe są wyłączone po sesji, staging artifacts mają zgodne hashe, a Redis `6379` i światy nie zostały naruszone.

## Instrukcja wznowienia

Następna sesja ma zacząć od tego pliku i aktualnego `REWRITE_STATUS.md`, realizować fazy po kolei aż do kryteriów końca. Nie zatrzymywać się po pojedynczej poprawce; uruchamiać testy i aktualizować checkpointy między fazami. Zadawać pytanie tylko wtedy, gdy dalszy krok wymaga decyzji o access policy, nieznanym kontrakcie zewnętrznym lub ryzyku dla trwałych danych/światów. Nie traktować „plan do końca” jako zgody na `/msg`, `/reply`, zmianę permissions, czyszczenie Redis `6379` ani modyfikację światów bez zweryfikowanego backupu.

## Tryb wykonania: bez zatrzymywania po partiach

To obowiązująca instrukcja realizacji całego planu po poleceniu użytkownika „wykonaj plan do końca”:

- Wykonywać kolejne fazy w jednej ciągłej sesji pracy. Nie kończyć odpowiedzi po audycie, poprawce, teście, pojedynczej fazie ani aktualizacji checkpointu. Nie pytać „czy kontynuować?”, „co dalej?” ani prosić o zatwierdzanie zwykłych zmian kodu i testów mieszczących się w zakresie.
- Krótkie komunikaty o postępie są informacyjne, nie są punktami zatrzymania. Po komunikacie od razu pracować dalej.
- Błąd testu/builda oznacza diagnozę, poprawkę i ponowną weryfikację, nie zakończenie pracy. Nie odkładać bez przyczyny regresji wykrytych we własnych zmianach.
- Zablokowanie jednego zadania nie zatrzymuje pozostałych. Zapisać konkretny blocker/dowód i przejść do następnego niezależnego zadania. Pytania o politykę lub zewnętrzne kontrakty zbierać do końcowego raportu, o ile można bezpiecznie pracować dalej.
- Dla permissions, edytowalności invsee i nieznanych integracji zachować obecne zachowanie oraz opisać rozbieżność. Brak specyfikacji nie uprawnia do samodzielnego wyboru nowej polityki; sam wpis na liście blockerów nie oznacza zaakceptowanego wyjątku.
- Polecenie wykonania całego planu obejmuje potrzebne uruchomienie i kontrolowane wyłączenie lokalnego stagingu oraz nietrwałego Redis `6380`, kopiowanie stagingowych JAR-ów po verify, a także izolowane testy w Mongo `aisector_staging`. Przed startem zweryfikować wszystkie endpointy, brak konfliktu z innymi procesami i backupy. Nie zatrzymywać usług niezwiązanych z testem, nie zmieniać środowiska produkcyjnego.
- Brak gracza/klienta dla manualnego smoke nie jest powodem kończenia całej sesji. Wykonać dostępne testy automatyczne i serwerowe, a konkretną manualną macierz pozostawić jako niezaliczony gate. Nie deklarować, że test wykonano, jeśli nie było realnego potwierdzenia.
- Nie wykonywać niebezpiecznego kroku tylko po to, by nie zadawać pytania. Jeśli dalsza praca w całości zależy od zgody dotyczącej trwałych danych, świata bez backupu lub zmiany polityki, wolno zatrzymać się z jednym zbiorczym pytaniem.
- Aktualizować checkpointy na bieżąco, aby przerwanie przez limit sesji/środowiska nie utraciło stanu. Nie można zagwarantować nieograniczonego czasu jednej sesji; przy wymuszonym przerwaniu zapisać dokładne następne zadanie i wyniki, bez twierdzenia, że rewrite jest ukończony.

### Kiedy wolno zakończyć odpowiedź

1. Wszystkie fazy i końcowe verify zostały wykonane, runtime/release mają dowody, a kryteria końca są spełnione — raport **„ukończone”**.
2. Wszystkie możliwe niezablokowane prace są wykonane, lecz pozostały rzeczywiste zależności od użytkownika/środowiska — raport **„część autonomiczna zakończona, rewrite jeszcze niezamknięty”**, z jedną zbiorczą listą blockerów i wymaganych decyzji/testów.
3. Wymuszony limit sesji/narzędzi lub blokada bezpieczeństwa uniemożliwia jakąkolwiek dalszą niezależną pracę — zapisać checkpoint i jawnie wskazać przerwanie.

Nie uznawać ręcznie zaakceptowanych ograniczeń za załatwione bez rzeczywistej akceptacji użytkownika. Nie tworzyć kolejnej ogólnej listy audytów zamiast wykonywać ten plan; nowe konkretne błędy w objętych nim ścieżkach naprawiać i weryfikować w ramach bieżącej pracy.
