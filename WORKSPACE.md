# Pełny kod workspace

Plugin Paper i jego testy są w `src/`. Plugin proxy i jego testy:
[BusioMeister/VelocityPlugin](https://github.com/BusioMeister/VelocityPlugin).
Wspólne źródła, wszystkie narzędzia audytu/runtime, nadrzędny Maven reactor i pełne
checkpointy rewrite znajdują się w **`workspace/`**. `build-support/` nadal pozwala
budować każdy plugin osobno.

## Odtworzenie pełnego układu

Wymagane: Git, Python 3.11+, JDK 25; Maven pobierany przez dołączony wrapper.
W pustym katalogu nadrzędnym:

```shell
git clone https://github.com/BusioMeister/aiSector.git aiSector
git clone https://github.com/BusioMeister/VelocityPlugin.git VelocityPlugin
python aiSector/workspace/tools/restore_workspace.py .
```

Następnie `sh ./mvnw clean verify` (Windows: `.\mvnw.cmd clean verify`).
Restore sprawdza manifest SHA-256 z normalizacją CRLF/LF i **nie nadpisuje**
istniejących różniących się plików ani katalogów repozytoriów. Kopiuje parent,
shared, tools i dokumentację do katalogu nadrzędnego dwóch klonów.

## Zakres publikacji

- Pełne źródła obu pluginów i shared, unit tests, wrapper i POM-y.
- Narzędzia sprawdzania permissions/parity oraz źródła klientowego smoke harness.
- Dokumentacja protokołów, plan, backlog, status i raport release.
- Nie dołączono światów, baz/Redis dumpów, prywatnych sekretów, caches, środowiska
  `.minecraft`, runtime logów, kompilatów ani lokalnego checkoutu upstream Purpur.

Narzędzia runtime dokumentują poprzedni lokalny staging Windows. Publikacja ich
źródeł **nie uruchamia** serwerów i nie konfiguruje środowiska automatycznie.
Przed jakimkolwiek smoke sprawdź ścieżki, endpointy, wersje, wymagane serwery
oraz zweryfikowane backupy; nie kieruj testów na produkcję.
Checkpointy i lokalne ścieżki z listopada 2026 są historycznymi dowodami,
nie zawartością załączoną do repozytorium ani deklaracją nowych testów runtime.

## Weryfikacja pełnego eksportu — 2026-11-11

Odtworzono wszystkie 50 plików nadrzędnego workspace z manifestu do izolowanego
katalogu z kopiami źródeł obu pluginów. Pełne `clean verify` zakończyło się
`BUILD SUCCESS` (122 testy Maven). Audytory/klient i nowy restore mają łącznie
12 zaliczonych testów Python. W tej publikacji nie powtarzano runtime smoke
ani nie uruchamiano środowiska produkcyjnego.
