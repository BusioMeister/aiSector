# Rewrite 2.0 — 2026-11-02

## Zmiany

- Wspólny moduł protokołów i Mongo, bounded async queues, session-aware callbacki i poprawiony shutdown.
- Profile zapisują się przed transfer publish/ACK. Tymczasowa blokada źródłowych działań gracza chroni przechwycony snapshot; późny quit zapisanego źródła nie zapisuje ponownie starego profilu.
- Mongo home identity/revision i stale GUI fence; guild generation/version CAS, membership revision cleanup i aktualna autoryzacja ról.
- Tokenowy rollback backup claimów, pending conflict bez nadpisywania payloadu, backup itemy v2 i ograniczony allowlistą czytnik legacy.
- Odtwarzanie cold vanish cache, rekoncyliacja permission/tag/hide; generator lifecycle, restore i async teleport cleanup.
- Publiczny `/spawn`; `/banlist` i `/pastetest` administracyjne. Pozostałe granty i historyczny mismatch `/sectorinfo` zachowane. Edytowalny lokalny invsee, `aisector:admin_chat` i `sector-border-init:*` zachowane; `/msg` i `/reply` nieaktywne.

## Weryfikacja

Zgodny workspace: 122 testy Maven (shared 9, Paper 92, Velocity 21), zero failures/errors/skips; 8 testów Python audytorów/klienta. Pełny lokalny smoke z dwoma oficjalnymi klientami 26.3 sprawdził inventory/meta/PDC/XP, opóźniony profile flush i source guard, transfery, home/TPA/spawn/reconnect, vanish, generator, PvP/projectile, invsee, Unicode chat i backup online/offline/conflict. Legacy corpus: 1657 standardowych itemów + 6 metadata cases.

## Wdrożenie i ograniczenia

Wdrażaj z kompatybilnym VelocityPlugin jako jeden zestaw, po zweryfikowanym backupie **Mongo i światów** oraz sprawdzeniu topologii i forwardingu. Nie mieszaj ze starymi writerami bez revision. Nie kopiuj lokalnej konfiguracji offline na produkcję.

Mongo, Redis i świat/ekwipunek nie są jedną transakcją. Nie ma gwarancji globalnego exactly-once przy awarii procesu lub writerach zewnętrznych omijających kontrakt. Niejednoznaczne restore outcomes wymagają manual review, nie automatycznego retry. Stare pluginy mogą nie czytać nowych backupów `v2:` — rollback wymaga kompatybilnego czytnika lub osobno zweryfikowanej migracji. Nie kasuj nowszych danych przy rollbacku. Custom legacy payloady poza allowlistą są odrzucane z zachowaniem danych.

Build i lokalny smoke nie oznaczają wykonanego wdrożenia produkcyjnego. Repozytorium nie zawiera kont testowych, światów, stagingowej bazy, logów ani prywatnych credentials.
