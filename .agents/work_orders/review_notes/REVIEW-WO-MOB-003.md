# REVIEW-WO-MOB-003 — Mobile Kotlin DTO + Room v8→v9 + Composable conditional render

**Data:** 2026-05-19
**Status:** ✅ Code complete, build PASS, gates PASS, awaiting commit + APK QA on device
**Worker:** worker-implementer (mobile Kotlin)
**Stage:** Mobile / Participant detail screen

## DoD checklist (z WO-MOB-003)

- [✅] `ParticipantDto` rozszerzony o 3 pola RSVP z `@Json(name=...)` + default values
- [✅] `Participant` (domain) rozszerzony o 3 pola RSVP
- [✅] `ParticipantEntity` rozszerzony o 3 kolumny Room z `defaultValue="0"`
- [✅] `MdDatabase.kt` version=9, `MIGRATION_8_9` zarejestrowane w `DatabaseModule.kt`
- [✅] `Migrations.kt` definiuje `MIGRATION_8_9` z 3 ALTER TABLE
- [✅] Mapper `Dto→Entity` przenosi 3 pola (znaleziony **inline w `SyncWorker.pullParticipants()`**, nie w dedykowanym mapperze)
- [✅] Mapper `Entity→Domain` w ViewModel przenosi 3 pola (3 lokalizacje: main toParticipant + legacy loadParticipant + ParticipantsViewModel)
- [✅] `StatusIconsRow` Composable: `if (participant.rsvpSent)` warunkowy render — bez slotu gdy `false` (decyzja UX z `/master ustal odpowiedzi`: ikona ukryta)
- [✅] `when (rsvpResponse)`: confirmed → green, declined → red, null → gray "czeka" (HourglassEmpty)
- [✅] Usunięty dead code `translateRsvp()` (verified grep: 0 callerów w prod kodzie Kotlin)
- [✅] `./gradlew :feature-participants:assembleDebug :core-database:assembleDebug` — BUILD SUCCESSFUL (7m 10s)
- [⏳] `./gradlew :core-database:test` — N/A (brak migration test infrastructure; gap udokumentowany w postmortem)
- [✅] Schema `9.json` wygenerowany (`identityHash` v9 `649b9db77dae6ef34695c026c802175d`, v8 → `7a949801ed72510925013fe03a71a349`)
- [⏳] **Test akceptacyjny on-device** — DEFERRED do post-commit APK install (4 stany A/B/C/D na event 24311000000909074)
- [⏳] 4 screenshoty — DEFERRED post-APK QA
- [✅] Decyzja UX dopisana do `decision_log.md` (ikona ukryta vs slot szary — wybrana ukryta, ADR 2026-05-19 z poprzedniej sesji `/master ustal odpowiedzi`)

## Gates summary

| Gate | Status | Notes |
|---|---|---|
| 🔒 Security | **PASS** | 0 Crit/High/Med, 1 niskie informational (schema 9.json untracked → dodać w commit). Klasyfikacja danych RSVP jako "business status plaintext" zgodna z istniejącymi 25 plaintext kolumnami w `participants` Room table. Constraint §16 (encrypted storage) dotyczy WYŁĄCZNIE auth tokens, NIE business status. Brak nowych logów PII. ProGuard rules `core.network.**` i `core.model.**` wildcard chronią nowe pola. |
| 🔗 Contract Sync | **PASS** | 4-warstwowa konsystencja: backend SELECT alias (`rsvp_sent`/`rsvp_response`/`rsvp_responded_at`) ↔ Moshi `@Json(name=...)` ↔ Room `@ColumnInfo(name=...)` ↔ Domain model — wszystkie snake_case identyczne. Typy `Boolean`/`String?`/`String?` spójne end-to-end. Wszystkie 4 mappery (DTO→Entity + 3× Entity→Domain) propagują 3 pola. `api-types.ts` × 3 N/A (mobile native Kotlin). Walk-ini spójne (backend zwraca `false`/`null`/`null`). |
| 🗄️ Migration Guard | **PASS** | Room v8→v9 z 3× `ALTER TABLE ADD COLUMN` (idempotentne w transakcji). `rsvp_sent INTEGER NOT NULL DEFAULT 0` → safe degradation UX (ikona ukryta dopóki sync nie zwróci prawdziwych wartości). Schema 9.json wygenerowany, `identityHash` matches `MdDatabase_Impl.kt` debug. 2 niskie informational: (a) stale release KSP cache v8 → zregeneruje się przy następnym `assembleRelease`; (b) brak `MigrationTest` infrastructure → P2 backlog. |
| 🧪 QA | **DEFERRED** | Mobile QA wymaga APK install + emulator/device. Build PASS już zweryfikował Kotlin compile + Room schema generation. Test 4 stanów RSVP (A/B/C/D na event `24311000000909074`) — user-driven post-commit. |

## Findings

### [INFO] Mapper triple-duplication w mobile (nowa gotcha)

Implementer wykrył że `Participant` mapper jest **zduplikowany w 4 miejscach**, brak dedykowanego `ParticipantMapper.kt`:
1. `core-sync/SyncWorker.kt:124-170` — DTO→Entity (inline w `pullParticipants()`)
2. `feature-participants/ParticipantDetailsViewModel.kt:158-186` — Entity→Domain (extension `toParticipant()`, main flow)
3. `feature-participants/ParticipantDetailsViewModel.kt:195-228` — Entity→Domain (legacy `loadParticipant()`)
4. `feature-participants/ParticipantsViewModel.kt:56-86` — Entity→Domain (list flow collector)

**Wpływ:** Każde dodanie pola w `Participant` wymaga 4 osobnych edycji. Implementer prawidłowo zaktualizował wszystkie 4, ale wzorzec jest bug-prone.

**Rekomendacja follow-up:** WO-MOB-004 cel "Refactor: dedicated `ParticipantMapper.kt` z funkcjami `ParticipantDto.toEntity()` + `ParticipantEntity.toDomain()`" — pozwoliłby na single point of edit i potencjalnie testy jednostkowe mappera. Dopisać do `known_gotchas.md` jako gotcha "Participant 4-way mapper duplication".

### [NISKIE — procedural] Schema 9.json untracked w git

`core-database/schemas/.../9.json` jest auto-generated przez Room schema export ale untracked. Musi być dodany do commit razem z migracją (istniejący wzorzec — schema 1.json..8.json są wersjonowane).

**Fix:** uwzględniony w plan commit'u (`git add core/core-database/schemas/...`).

### [INFO out-of-scope] Drugi mobile codebase poza monorepo

`C:\Users\kochn\StudioProjects\MD_mobile_android\` (poza monorepo, NIE submodule) — równoległy mobile Kotlin codebase który NIE ma 3 pól RSVP. Spodziewane: legacy/eksperymentalny (canonical mobile = `simple-event-checkin/` submodule). Out-of-scope WO-MOB-003. User-side klaryfikacja w przyszłości czy aktywny/dead.

### [INFO] ProactiveScope: `ParticipantsViewModel` mapper

Implementer **proaktywnie** zaktualizował `ParticipantsViewModel` (mapper dla listy uczestników) o 3 pola RSVP, mimo że WO mówił tylko o `ParticipantDetailsViewModel`. Powód: domain model `Participant` ma teraz te pola → jeśli pominięte, każdy render listy uczestników utracił by tę informację (default `false`/`null`). UI listy jeszcze nie wykorzystuje `rsvpSent` — to safe addition pod follow-up WO-MOB (np. ikona RSVP w wierszu listy uczestników).

**Klasyfikacja:** akceptowalne rozszerzenie scope — minimalna zmiana, nie modyfikuje UI, future-proof.

## Recommendation

✅ **APPROVED dla commit + APK QA.**

WO osiągnęło cel funkcjonalny end-to-end:
- 3 pola RSVP propagowane przez 4 warstwy (DTO → Entity → Domain → Composable)
- Room migration v8→v9 safe (default `false` → ikona ukryta w degradation)
- Composable warunkowy render — gdy `rsvpSent=false` ikona NIE renderowana (slot wycięty z rzędu, jak Sylwia Baran case)
- Build PASS, schema 9.json wygenerowany
- Dead code `translateRsvp()` usunięty

**Sekwencja domknięcia:**
1. Commit `.agents/work_orders/` artefakty mobile (REVIEW, IR — utworzony w Step 6)
2. Commit mobile submodule (10 plików Kotlin + schema 9.json)
3. Bump submodule reference w main repo + commit meta-context
4. Push wszystkich 3 commit'ów
5. APK install + 4-stan QA (user-driven)
6. (Opcjonalnie) WO-MOB-004 follow-up — refactor mapper duplication
