# Uruchomienie sieci: Spawn + GameWest + GameEast

Pakiet startowy nie zawiera mapy działającego serwera, danych graczy ani sekretów.
Potrzebujesz własnego zapisu mapy, JDK 25, Gita, Pythona 3.11+, Maven 3.9+ dla
silnika, trzech instancji Paper 26.3, Redis i MongoDB. Na Windows skrypty silnika
wymagają PowerShella.

## 1. Zbuduj pluginy i silnik

W pustym katalogu roboczym, obok siebie:

```powershell
git clone https://github.com/BusioMeister/aiSector.git aiSector
git clone https://github.com/BusioMeister/VelocityPlugin.git VelocityPlugin
python aiSector/workspace/tools/restore_workspace.py .
.\mvnw.cmd -B -ntp -DskipTests package
.\VelocityPlugin\velocity-fork\build-instant.ps1 -OutputDirectory "$PWD\fork-build"
```

Silnik opiera się na przypiętych rewizjach Velocity 4.3.1 i InstantTransfer.
[Patch Velocity, patch InstantTransfer i skrypt buildu](https://github.com/BusioMeister/VelocityPlugin/tree/master/velocity-fork)
są wersjonowane publicznie. Nie używaj dodatkowych historycznych patchy.

| Plik | Gdzie go umieścić |
|---|---|
| `aiSector/target/aiSector-2.0.0-SNAPSHOT.jar` | `plugins/` na wszystkich trzech Paperach |
| `VelocityPlugin/target/aiSector-Velocity-2.0.0-SNAPSHOT.jar` | `proxy/plugins/` |
| `fork-build/Velocity/proxy/build/libs/velocity-proxy-4.3.1-all.jar` | `proxy/velocity.jar` — to silnik proxy |
| `fork-build/InstantTransfer/proxy-plugin/target/InstantTransfer-0.1.0.jar` | `proxy/plugins/` |
| `fork-build/InstantTransfer/backend-agent/spigot/target/original-instanttransfer-agent-0.1.0.jar` | `plugins/` na wszystkich trzech Paperach |
| [PacketEvents 2.14.0](https://github.com/retrooper/packetevents/releases) | wariant Velocity na proxy, wariant Spigot na każdym Paperze |

## 2. Skonfiguruj lokalne usługi i światy

1. Uruchom lokalny Redis na `6380` i MongoDB na `27017`. Nie wystawiaj tych
   portów do Internetu. Skopiuj [`backend.example.yml`](backend.example.yml)
   jako `plugins/aiSECTOR/config.yml`. Ustaw nazwę każdego sektora i wspólną
   bazę MongoDB. Jeżeli Redis wymaga hasła, ustaw je lokalnie również na proxy.
2. Na każdym Paperze skopiuj
   [`sectors-locked-spawn.yml`](../src/main/resources/sectors-locked-spawn.yml)
   jako `plugins/aiSECTOR/sectors.yml`. W `server.properties` ustaw
   `server-ip=127.0.0.1`, `online-mode=false`, osobny port `25566`–`25568`
   według nazw z pliku Velocity, `view-distance=16` i `simulation-distance=3`.
3. Na wszystkich trzech backendach ustaw w `config/paper-global.yml`
   `proxies.velocity.enabled: true`, `proxies.velocity.online-mode: true`
   oraz `proxies.velocity.secret` na tę samą wartość co plik
   `proxy/forwarding.secret`. Wygeneruj własny sekret, np. `openssl rand -hex 32`.
   **Publicznego Velocity nie przełączaj w tryb offline.**
4. Wgraj dwie osobne kopie tej samej mapy gry, a na Spawn oddzielny świat.
   Szczegóły współrzędnych, przycisku i bezpiecznego lądowania są w
   [SECTOR_LAYOUT.md](../SECTOR_LAYOUT.md). Ustaw `/setworldspawn` w granicach
   Spawnu, przygotuj suchą gąbkę z przyciskiem i ustaw `/setspawnsector`.
5. Skopiuj [`velocity.example.toml`](velocity.example.toml) do katalogu proxy
   jako `velocity.toml`. Przed wystawieniem otwórz wyłącznie `25565/TCP`.
   Opcjonalne logo listy serwerów to `proxy/server-icon.png` (64×64).
   Jeżeli używasz paczki zasobów, ustaw taki sam identyfikator i SHA-1
   jej bezpośredniego ZIP-a na wszystkich backendach.

## 3. Uruchom we właściwej kolejności

Po Redis i MongoDB uruchom Paper w każdym z trzech katalogów serwerów:

```powershell
java -Xmx2G -jar paper.jar --nogui
```

W katalogu proxy, po zakończeniu startu backendów:

```powershell
java -Xmx1G "-Daisector.redis.port=6380" "-Daisector.mongodb.database=aisector" "-Daisector.locked-spawn=Spawn" "-Dvelocity.seamlessSwitch=true" "-Dvelocity.seamlessSpawn=true" -jar velocity.jar
```

Najpierw sprawdź własnym klientem 26.3, czy można wejść na Spawn, użyć
przycisku i przejść pieszo przez `X=0` w obie strony. Tryb bez ekranów
wymaga zgodnych rejestrów i konfiguracji Paper oraz paczki zasobów na
backendach — nie zakładaj zgodności po dołożeniu dowolnych datapacków.

Teksty edytujesz w `plugins/aiSECTOR/messages.properties` i
`plugins/velocitysector/messages.properties`; `/core reload` wysyła polecenie
ponownego wczytania bez restartu. Instrukcja nie wymaga żadnych botów,
narzędzi typu smoke ani plików ze środowiska produkcyjnego.
