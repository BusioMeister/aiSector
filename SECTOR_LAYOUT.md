# Zamknięty spawn i mapa 1000×1000

Opcjonalny profil `src/main/resources/sectors-locked-spawn.yml`:

| Backend | X (bloki) | Z (bloki) |
|---|---|---|
| `Spawn` | 2000–2099 | 2000–2099 |
| `GameWest` | −500 do −1 | −500–499 |
| `GameEast` | 0–499 | −500–499 |

Przejście między połówkami jest na `X=0`. To jedna mapa z dwiema strefami
odpowiedzialności, nie dwie niezależnie generowane mapy. Nie ma współdzielenia
zapisu plików świata ani replikacji bloków przez plugin.

1. Zatrzymaj backendy, sprawdź kopie zapasowe światów i danych. Skopiuj **ten
   sam zapis mapy gry** do dwóch osobnych katalogów `GameWest` i `GameEast`
   (nie linkuj obu do tego samego katalogu). Zachowaj pozycje, seed i ustawienia
   generatora. Przed startem wygeneruj obszar gry i okolice granicy na obu kopiach.
2. `Spawn` otrzymuje osobny zapis świata; ustaw `/setworldspawn` wewnątrz
   jego zakresu, np. `2050 <bezpieczne Y> 2050`. Zgodność `spawn-world`
   i `game-world` oznacza zgodność nazw Bukkit na odpowiednich backendach,
   nie ten sam zapis dyskowy. Jeżeli `/spawn` i globalny respawn mają wracać
   na ten backend, ustaw też istniejącą komendą `/setspawnsector` pozycję globalną.
3. Skopiuj profil na wszystkie trzy backendy jako `plugins/aiSECTOR/sectors.yml`.
   Ustaw `this-sector-name` odpowiednio na `Spawn`, `GameWest`, `GameEast`.
4. W `velocity.toml` zarejestruj te same nazwy i ustaw `try = ["Spawn"]`.
   Uruchom **zaktualizowany** plugin Velocity z `-Daisector.locked-spawn=Spawn`
   lub `AISECTOR_LOCKED_SPAWN=Spawn`; obie konfiguracje muszą być włączone razem.
   Nie otwieraj bezpośredniego dostępu do backendów. Istniejący zapis sektora
   powracającego gracza zostaje; nowy gracz zaczyna na `Spawn`.
5. Na `Spawn` połóż suchą gąbkę (`SPONGE`) i umieść na niej dowolny przycisk.
   Przycisk musi być fizycznie zamocowany do gąbki; mokra gąbka, dźwignia
   lub przycisk zamocowany do innego bloku nie działają.

Kliknięcie losuje jedną z dwóch równych połówek. Backend docelowy sprawdza do
12 chunków (również pobliskie kolumny w obrębie wybranego chunka), poza pasem przy granicy,
i ląduje na pełnym bloku z dwoma
wolnymi blokami nad nim; woda, lawa i niebezpieczna powierzchnia są odrzucane.
Jeżeli nie znajdzie miejsca, próbuje bezpiecznego powrotu na Spawn.
Blokada spawna dotyczy ruchu, portali, teleportacji poza obszar i zmiany backendu
przez standardowe drogi proxy; publiczna komenda `/spawn` nie dostaje nowego grantu.
Wyjątki to `/tp <gracz>` i `/s <gracz>` operatora. Po sprawdzeniu OP na
Paper obsługującym komendę i zapisie danych przenoszonej postaci proxy wystawia
jednorazową zgodę na konkretny backend docelowy. Osoba przywoływana nie musi mieć OP.
Gracz może też wyjść do swojego `/home`, do domu gildii (`/g dom`, `/g baza`)
albo przez zaakceptowane `/tpa`. Obowiązują dotychczasowe uprawnienia,
sprawdzanie celu i odliczanie. Bezpośrednie `/server` pozostaje zablokowane.
Samo wgranie JAR-a nie nadpisuje istniejącego `sectors.yml` ani mapy.

Na przejściu pomiędzy sektorami gry Paper próbuje wystartować od razu przy
krawędzi docelowego sektora. Ta sama ścieżka ustawia docelową pozycję dla
`/spawn` i pozostałych transferów z konkretnym celem. Jeżeli Paper już
przyjął tę pozycję przy wejściu, przywrócenie profilu jej nie nadpisuje, nawet
gdy gracz zdążył się ruszyć. Jeśli odczyt pozycji się spóźni, zwykły transfer
nadal działa. Skrócona jest faza wczytywania, ale stockowy Velocity nadal zmienia
stan protokołu klienta przy przełączeniu — nie jest to tryb bez ekranu.

## Chunks na granicy i dalsze prace nad Velocity

Na każdym backendzie gry Paper utrzymuje po jednym rzędzie chunków po obu
stronach wspólnej granicy (128 na backend w tym układzie). Ładuje je
asynchronicznie po starcie i zwalnia własne bilety przy wyłączeniu. Spawn nie
ładuje granicy pomiędzy sektorami gry. Log `Seam chunk tickets ready` pokazuje
faktyczną liczbę przypiętych chunków. Nie jest to ładowanie całej mapy.

W Velocity 4.3.1 `LoginSessionHandler.handle(ServerLoginSuccessPacket)` uruchamia
`ClientPlaySessionHandler.doSwitch()` dla już zalogowanego gracza. Samo
podgrzanie chunków nie pomija tej rekonfiguracji. Eksperymentalna ścieżka
proxy musi najpierw osobno przygotować docelowy backend i porównać stan
konfiguracji klienta (wersję, registry, tagi, znane pakiety i paczki zasobów).
Zmianę bez rekonfiguracji można dopuścić tylko na zgodnych parach backendów;
przy różnicy stanu lub błędzie potrzebna jest standardowa ścieżka Velocity.
Lokalna instancja używa opcjonalnego [forka Velocity](https://github.com/BusioMeister/VelocityPlugin/blob/master/SEAMLESS_FORK.md)
z ręcznie zatwierdzonymi trasami dla tego układu. Nie jest to automatyczne
porównanie rejestrów. Akcja `trace-seam` agenta lokalnego smoke
zapisuje faktyczne zmiany ekranów w oficjalnym kliencie do porównania z prototypem.

Pomiar lokalny z przypiętymi chunkami, po jednym przejściu `GameEast → GameWest`:
`PLAY 0 ms → ServerReconfigScreen 201 ms → LevelLoadingScreen 709 ms → PLAY 1109 ms`.
To pomiar pojedynczej próby, nie średnia ani porównanie A/B. Pierwszy ekran
wynika z rekonfiguracji używanej przez Velocity; drugi z ponownego
załadowania poziomu przez klienta. Pominięcie samej rekonfiguracji nie jest
wystarczającym kryterium dla trybu bez ekranu. Test prototypu musi potwierdzić
brak **obu** ekranów oraz poprawną wymianę chunków, bytów, inventory, pozycji i
bordera; nie wolno uznać trzymania starego obrazu bez synchronizacji stanu za sukces.

## Lot podczas transferu

Wraz z trybem gry przenosimy informację, czy gracz rzeczywiście leciał.
Docelowy Paper zezwala na lot na podstawie trybu gry albo uprawnienia
zapisanego przez `/fly`, a nie samej flagi aktywnego lotu. Wyłączenie
`/fly` nie odbiera graczom w Creative możliwości latania. Przycisk w
typowym przypadku przygotowuje bezpieczne lądowanie jeszcze przed
pierwszym pakietem pozycji; gdy nie da się tego zrobić w limicie czasu,
pozostaje dotychczasowe wyszukiwanie po wejściu.

## Gotowa mapa od zera

Archiwum mapy, jeśli zostało wygenerowane lokalnym narzędziem
`tools/runtime/package_layout_worlds.py`, zawiera osobne katalogi
`Spawn/world`, `GameWest/world`, `GameEast/world` oraz `sectors.yml`.
`GameWest` i `GameEast` są kopiami tego samego zapisu wygenerowanego przed
podziałem. W `Spawn/world` przygotowano platformę i przycisk na gąbce przy
`2050 127 2046`. Rozpakuj każdy świat tylko do odpowiedniego, zatrzymanego
backendu, po sprawdzeniu backupu. Nie nadpisuj istniejących `world/` w ciemno.
