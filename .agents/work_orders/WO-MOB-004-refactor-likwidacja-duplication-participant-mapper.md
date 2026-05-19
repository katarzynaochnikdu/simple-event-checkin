# WO-MOB-004: Refactor — likwidacja 4-way duplication mappera Participant

**Data:** 2026-05-19
**Scope:** mobile (simple-event-checkin)
**Worker:** [do uzupełnienia przez Mastera]
**Stage:** [placeholder]
**Priorytet:** [placeholder — Krytyczny / Wysoki / Normalny / Niski]

## Cel
Wprowadzić dedykowane mapper functions (`ParticipantDto.toEntity()` + `ParticipantEntity.toDomain()`) i zastąpić nimi 4 rozproszone inline mapowania w `simple-event-checkin/` (natywny Android Kotlin), aby uzyskać single point of edit przy dodawaniu nowych pól `Participant`. Gotcha z `known_gotchas.md` (2026-05-19): każde nowe pole wymaga obecnie 4 osobnych edycji w 4 plikach → cicha regresja jeśli zapomnimy którąś lokalizację (np. UI dostaje default zamiast prawdziwej wartości pola).

## Zakres
Lista plików/modułów, które Worker MA PRAWO modyfikować:

**NEW (zależne od decyzji A/B/C w DoR):**
- `core-network/src/main/java/.../dto/ParticipantDtoMapper.kt` (lub w wybranej lokalizacji A/B/C) — `fun ParticipantDto.toEntity(eventId: String): ParticipantEntity`
- `core-database/src/main/java/.../entities/ParticipantEntityMapper.kt` (lub w wybranej lokalizacji) — `fun ParticipantEntity.toDomain(): Participant`
- (Opcjonalnie) `core-database/src/test/java/.../ParticipantEntityMapperTest.kt`
- (Opcjonalnie) `core-network/src/test/java/.../ParticipantDtoMapperTest.kt`

**MODIFY:**
- `core-sync/src/main/java/pl/medidesk/mobile/core/sync/SyncWorker.kt:124-170` — zastąp inline `ParticipantEntity(...)` wywołaniem `dto.toEntity(eventId)`
- `features/feature-participants/.../viewmodel/ParticipantDetailsViewModel.kt:158-186` — zastąp ciało `toParticipant()` wywołaniem `this.toDomain()` (lub usuń extension i wywołuj `entity.toDomain()` w consumer'ach)
- `features/feature-participants/.../viewmodel/ParticipantDetailsViewModel.kt:195-228` — legacy `loadParticipant()`: zastąp inline `Participant(...)` wywołaniem `entity.toDomain()`
- `features/feature-participants/.../viewmodel/ParticipantsViewModel.kt:56-86` — zastąp inline `Participant(...)` w flow collector wywołaniem `entity.toDomain()`
- (Opcjonalnie) `core-network/build.gradle.kts` lub `core-database/build.gradle.kts` — jeśli wybrana lokalizacja wymaga dodania dependency (opcja B)

## Czego NIE ruszać 🛑
- Schema Room — `ParticipantEntity` data class declaration nietykalne (refactor zachowuje pola 1:1)
- Migracje Room (`Migration_8_9` z WO-MOB-003) — refactor nie zmienia DB schema
- Mobile API contract (`backend/api/mobile.py`) — refactor mobile-only, zero zmian backendu
- `Participant` domain model declaration — refactor nie zmienia kontraktu domain, tylko sposób budowania instancji
- `ParticipantDto` declaration — refactor nie zmienia kontraktu DTO
- Logika sync (offline queue, conflict resolution w `SyncWorker.kt`) — tylko wymiana inline mappingu na wywołanie mappera, reszta `pullParticipants()` nietknięta
- Desktop kod (`backend/`, `frontend/`, `Purchase Cart/`, `shared/`) — refactor mobile-only

## Pliki startowe
Od czego Worker powinien zacząć czytanie/analizę:
- `simple-event-checkin/core-sync/src/main/java/pl/medidesk/mobile/core/sync/SyncWorker.kt:124-170` — DTO→Entity inline mapping #1
- `simple-event-checkin/features/feature-participants/.../viewmodel/ParticipantDetailsViewModel.kt:158-186` + `:195-228` — Entity→Domain inline mapping #2 + #3
- `simple-event-checkin/features/feature-participants/.../viewmodel/ParticipantsViewModel.kt:56-86` — Entity→Domain inline mapping #4
- `simple-event-checkin/core-network/build.gradle.kts` — sprawdzić dependencies dla decyzji A/B
- `simple-event-checkin/core-database/build.gradle.kts` — sprawdzić dependencies dla decyzji A/B
- `simple-event-checkin/core-model/.../Participant.kt` — domain model (28 pól po WO-MOB-003)
- `simple-event-checkin/core-database/.../ParticipantEntity.kt` — Room entity
- `simple-event-checkin/core-network/.../ParticipantDto.kt` — Retrofit DTO

## Decyzja lokalizacji do podjęcia w DoR
Mapper funkcje muszą żyć w module który **importuje OBA typy** (źródłowy + docelowy). Opcje:
- **(A)** Mappery w nowym module `core-mappers` z dependencies na `core-model` + `core-network` + `core-database` — czyste separation, ale +1 moduł Gradle (overhead). RECOMMENDED dla większych projektów.
- **(B)** `DTO.toEntity()` w `core-network` (trzeba sprawdzić, czy ma deps na `core-database`), `Entity.toDomain()` w `core-database` (ma deps na `core-model`) — natural ownership per source type. RECOMMENDED dla obecnego stanu submodule.
- **(C)** Wszystkie w `core-sync` (najmocniej-zależny moduł, używa wszystkich 3 warstw). Pragmatyczne ale brudne (mapper Entity→Domain w `core-sync` to anti-pattern — sync nie powinno wiedzieć o domain models).

Implementer powinien wybrać między A i B na podstawie obecnych Gradle dependencies. Decyzja udokumentowana w commit message lub komentarzu pliku.

## Pre-condition
- WO-MOB-003 zmergowany + deployed (mobile submodule HEAD = `f942987`)
- Pole `rsvpSent` / `rsvpResponse` / `rsvpRespondedAt` już w `Participant` + `ParticipantEntity` + `ParticipantDto`
- Refactor nie zmienia danych, tylko strukturę kodu

## Ryzyko
- **Subtelne różnice między 4 inline mappingami** — jeśli któryś transformuje pole inaczej (np. `attendance_status` lowercase normalization, lub `checked_in_at` parsowanie), unified mapper może wprowadzić cichą regresję. Mitigation: pre-implementation diff 4 mappingów + decyzja jaki "canonical" reprezentuje wszystkie 4.
- **Order pól w konstruktorze `ParticipantEntity(...)`** — Kotlin data class wymaga prawidłowej kolejności positional args lub named args. Refactor MUSI zachować named args lub dokładny order.
- **Module dependencies** — jeśli wybieramy opcję B (`DTO.toEntity()` w `core-network`), `core-network` musi mieć dependency na `core-database` (gdzie `ParticipantEntity`). Sprawdzić Gradle, jeśli circular → fallback na A (nowy moduł) lub C (`core-sync`).
- **Ciche regresje fieldów** — brak jednostkowych testów mappera = ryzyko że któreś z 28 pól zostanie zgubione. Mitigation: opcjonalne testy z asercjami wszystkich pól (recommended).

## Definition of Done ✅
- [ ] Wybrana lokalizacja mapperów (A/B/C) z uzasadnieniem w komentarzu lub commit message
- [ ] `fun ParticipantDto.toEntity(eventId: String): ParticipantEntity` zdefiniowane raz
- [ ] `fun ParticipantEntity.toDomain(): Participant` zdefiniowane raz
- [ ] Wszystkie 4 inline mapowania zastąpione wywołaniami mapper functions
- [ ] `./gradlew :app:assembleDebug` PASS (full APK build, nie tylko moduły)
- [ ] Grep: `grep -c "ParticipantEntity(" simple-event-checkin/**/*.kt` → 1 (tylko data class declaration)
- [ ] Grep: `grep -c "Participant(" simple-event-checkin/**/*.kt` → 1-2 (1× data class declaration + ewentualnie 1 użycie w testach)
- [ ] (Opcjonalnie) Jednostkowe testy mapperów z asercjami wszystkich pól (28 pól po WO-MOB-003)
- [ ] APK install + manual sanity check: lista uczestników + szczegóły 1 uczestnika + ikona RSVP nadal działa (zero regresji)
- [ ] Review note w `review_notes/REVIEW-WO-MOB-004.md`

## Test akceptacyjny 🧪
1. **Build PASS** (per-moduł i pełny APK):
   ```
   cd simple-event-checkin
   ./gradlew :core-sync:assembleDebug :feature-participants:assembleDebug :core-database:assembleDebug :core-network:assembleDebug
   ./gradlew :app:assembleDebug
   ```
   Oczekiwany wynik: BUILD SUCCESSFUL, zero compilation errors.
2. **Grep verification — single mapper declaration:**
   ```
   grep -rn "ParticipantEntity(" simple-event-checkin/ --include="*.kt" | wc -l
   ```
   Oczekiwany wynik: **1** (tylko deklaracja klasy w `ParticipantEntity.kt`). Przed refactor: **2** (deklaracja + mapper w SyncWorker).
3. **Grep verification — single Domain mapper:**
   ```
   grep -rn "fun.*toParticipant\|fun.*toDomain" simple-event-checkin/ --include="*.kt"
   ```
   Oczekiwany wynik: 1 deklaracja extension fn `ParticipantEntity.toDomain()` (lub `toParticipant()` jeśli zachowano nazwę).
4. **Zero regresji funkcjonalnej — APK manual smoke test:**
   - Zainstaluj `app-debug.apk` po refactor obok APK z WO-MOB-003
   - Wybierz event z listy → otwórz listę uczestników → otwórz szczegóły dowolnego uczestnika
   - Ikona RSVP renderuje się warunkowo (gdy `rsvpSent=true`) — identycznie jak przed refactor
   - Check-in / undo check-in działa
   - Wszystkie pola w szczegółach uczestnika widoczne (email, telefon, ticket type, status, opłata, RSVP, etc.)

## Oczekiwany efekt wizualny 🖼️
**ZERO zmian w UI.** Refactor czysto strukturalny — backend kodu, nie behavior. Test akceptacyjny opiera się na braku zmian wizualnych + zielonym build'zie + grep'ach.

## Kontrakt API (jeśli zmiana full-stack) 🔗
N/A — refactor mobile-only. Zero zmian backend / API / DTO / Entity schema. Wszystkie 28 pól `Participant` zachowane bez zmian.

## Format zwrotki
- Lista zmienionych plików z jednolinijkowym opisem zmian
- Git diff summary (oczekiwane: 2-4 nowe pliki mapperów + 4 modified pliki + opcjonalnie 1-2 test files)
- Wynik `./gradlew :app:assembleDebug` (BUILD SUCCESSFUL)
- Wynik grep'ów weryfikacyjnych (DoD pkt 5 + 6)
- Screenshot listy uczestników + szczegółów + ikony RSVP (dowód braku regresji wizualnej)
- Decyzja A/B/C z uzasadnieniem (commit message lub komentarz w plikach mappera)
- Propozycja wpisu do `decision_log.md` (decyzja architektoniczna o lokalizacji mapperów w mobile)

## Sizing
🟡 **średni** — 2-4 nowe pliki (mappery + opcjonalnie testy) + 4 modified pliki. Zero zmian schema/API/migrations. Pure structural refactor.
