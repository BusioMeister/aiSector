# aiSector rewrite — porównanie z lokalną historią

Audyt read-only: 2026-11-02. Nie wykonywano checkout/reset/fetch/commit ani modyfikacji `.git` przez narzędzia Git. Git CLI nie jest dostępny, ale dwa podprojekty mają własne repozytoria; wcześniejsze stwierdzenie o braku historii było błędne.

| Moduł | Commit referencyjny | Java przed → obecnie |
|---|---|---|
| Paper | `e0ae4972b415ff0d9b0a56c63fef88740fff2494` | 123 → 148 |
| Velocity | `6f1ac27fe254fb94882413ea90001e90e3a36f67` | 26 → 24 |

Pełny maszynowy wynik: `tools/baseline_inventory.json`, generator: `tools/audit_rewrite_baseline.py`. Czyta lokalne obiekty commitów przez izolowany Dulwich; raport konfiguracji zawiera wyłącznie nazwy kluczy, nigdy URI/password values. Dwa testy Python sprawdzają redakcję wartości i kwalifikację kluczy.

## Funkcje, rejestracje i zasoby

- Wszystkie **41 rejestracji executorów Paper** pozostały; brak usuniętych komend i brak podmiany klasy executora.
- Velocity zachowuje rejestrację `vwhitelist`; `/msg` i `/reply` nie były rejestrowane w referencyjnym kodzie i nadal nie są aktywowane. Ich źródła zachowano.
- 7 usuniętych ścieżek klas Paper to przeniesienia wspólnych packet contracts do `shared`. W Velocity 7 to takie same przeniesienia, a `PlayerDisconectListener` zastąpił poprawnie nazwany `PlayerDisconnectListener`. **Brak niezmapowanych usunięć klas.**
- Nie usunięto zasobów Paper. Dawny `VelocityPlugin/src/main/resources/config.yml` zawierał listę serwerów, ale referencyjny kod Velocity nie miał parsera/odczytu tego pliku. Rzeczywiste adresy backendów pozostają w `velocity.toml`; endpointy Redis/Mongo są jawnie opisane w README jako JVM properties / environment variables.
- Lokalne edytowalne invsee z zapisem przy zamknięciu widoku jest zachowaniem starego kodu. Potwierdzono je rzeczywistym kliknięciem oficjalnego klienta, bez dodawania read-only ani cross-server feature.
- `aisector:admin_chat`, `sector-border-init:*`, legacy invsee channels oraz inne niepotwierdzone integracje nie zostały usunięte ani aktywowane jako nowe funkcje.

## Uprawnienia i decyzja użytkownika

- Większość dopisanych descriptor permissions dubluje kontrolę już istniejącą w executorze (`tp`, `s`, `heal`, `fly`, `speed`, `god`, `vanish`, `tpa`). Nie usuwa się przez to dawnych Java checks ani nie dodaje grantów zwykłym graczom.
- `pastetest` i `banlist` zostały utwardzone do uprawnień administracyjnych w dotychczasowym rewrite. Użytkownik 2026-11-02 jawnie wybrał ich pozostawienie jako administracyjne przy przywróceniu publicznego `/spawn`.
- **`/spawn` był publiczny w referencyjnym kodzie. Usunięto dodany descriptor `aisector.setspawn` zgodnie z wyborem użytkownika.** Test używa nie-operatora z jawnym false dla tej permission i potwierdza transfer `/spawn` na inny backend.
- Rozbieżność `/sectorinfo` (`ai.sector.info` w descriptorze, `aisector.command.sectorinfo` w executorze) występuje już w commicie referencyjnym. Zachowano ją zgodnie z instrukcją pozostawienia pozostałych grantów bez zmian. Audyt nadal jawnie ją raportuje; nie oznacza to nowej regresji ani ukrytej zmiany polityki.
- Aliasów i katalogowych nodów nie usuwano. Advisory catalog-only nodes mogą być używane zewnętrznie; nie uznano ich automatycznie za martwe.

## Granica porównania

To dowód porównania dostępnych commitów, rejestracji, klas, zasobów i bieżących regresji runtime — nie matematyczny dowód identyczności wszystkich możliwych zachowań ani obietnica wspierania dowolnych zewnętrznych pluginów/serializowanych klas. Zmiany bezpieczeństwa i kontrakt kompatybilności są jawne w `REWRITE_RELEASE.md`; wdrożenie produkcyjne pozostaje oddzielną czynnością operatora.
