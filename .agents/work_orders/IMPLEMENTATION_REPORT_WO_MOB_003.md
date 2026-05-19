# IMPLEMENTATION_REPORT — WO-MOB-003

**Data:** 2026-05-19
**Worker:** worker-implementer (mobile Kotlin Android)
**Status:** ✅ Code complete + build PASS + gates PASS, awaiting commit + APK QA on device

## Zmienione pliki (10 modified + 1 schema generated)

**core-network (1 plik):**
- `simple-event-checkin/core/core-network/.../dto/ResponseDtos.kt:93-95` — `ParticipantDto` +3 pola Moshi (`@Json(name="rsvp_sent") val rsvpSent: Boolean = false`, `@Json(name="rsvp_response") val rsvpResponse: String? = null`, `@Json(name="rsvp_responded_at") val rsvpRespondedAt: String? = null`)

**core-model (1 plik):**
- `simple-event-checkin/core/core-model/.../Participant.kt:29-31` — domain model +3 pola (Boolean/String?/String?)

**core-database (4 pliki + 1 auto-generated schema):**
- `simple-event-checkin/core/core-database/.../entities/ParticipantEntity.kt:44-46` — Room +3 kolumny z `@ColumnInfo(name=..., defaultValue="0")`
- `simple-event-checkin/core/core-database/.../MdDatabase.kt:16` — `version = 8 → 9` + komentarz
- `simple-event-checkin/core/core-database/.../Migrations.kt:38-43` — nowy `MIGRATION_8_9` z 3× `ALTER TABLE participants ADD COLUMN`
- `simple-event-checkin/core/core-database/.../DatabaseModule.kt:23` — `.addMigrations(MIGRATION_7_8, MIGRATION_8_9)`
- `simple-event-checkin/core/core-database/schemas/.../9.json` — AUTO-GENERATED przez `kapt`/`ksp` Room schema export podczas `:core-database:assembleDebug`

**core-sync (1 plik) — DTO → Entity mapper:**
- `simple-event-checkin/core/core-sync/.../SyncWorker.kt:169-171` (`pullParticipants()`) — dodane 3 pola w `map { dto -> ParticipantEntity(...) }`

**feature-participants (3 pliki):**
- `simple-event-checkin/features/feature-participants/.../viewmodel/ParticipantDetailsViewModel.kt:183-185` (main `toParticipant()` extension) + `:224-226` (legacy `loadParticipant()`) — mapper Entity→Domain
- `simple-event-checkin/features/feature-participants/.../viewmodel/ParticipantsViewModel.kt:83-85` — mapper Entity→Domain dla listy (proaktywne rozszerzenie scope)
- `simple-event-checkin/features/feature-participants/.../screen/ParticipantDetailsScreen.kt:325-371` — **Composable `StatusIconsRow` przepisany** z warunkowym renderem `if (participant.rsvpSent) { ... }` + usunięty dead code `translateRsvp()` (linie 736-744)

**Git diff summary:** 10 plików, +58 / -29 LOC.

## Build / Compile

```
./gradlew :core-database:assembleDebug :feature-participants:assembleDebug

BUILD SUCCESSFUL in 7m 10s
146 actionable tasks: 37 executed, 109 up-to-date
```

Tylko pre-existing warnings (Icons.Filled.Undo deprecation, KT-73255 annotation default target — out of scope).

## Schema export

`core-database/schemas/pl.medidesk.mobile.core.database.MdDatabase/9.json` wygenerowany. Diff vs `8.json`:

```diff
- "version": 8,
- "identityHash": "7a949801ed72510925013fe03a71a349",
+ "version": 9,
+ "identityHash": "649b9db77dae6ef34695c026c802175d",
```

W `participants.createSql` dopisane przed `PRIMARY KEY(id)`:
```sql
`rsvp_sent` INTEGER NOT NULL DEFAULT 0, `rsvp_response` TEXT, `rsvp_responded_at` TEXT,
```

W `participants.fields[]` dodane 3 wpisy. Wszystkie pozostałe tabele (offline_checkins, sync_metadata, walkin_participants, ticket_classes) bit-identyczne z v8.

Identity hash v9 zgodny z `MdDatabase_Impl.kt:62` (debug build) — Room runtime przejdzie identity check.

## Migration code

```kotlin
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE participants ADD COLUMN rsvp_sent INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE participants ADD COLUMN rsvp_response TEXT")
        db.execSQL("ALTER TABLE participants ADD COLUMN rsvp_responded_at TEXT")
    }
}
```

Bezpieczne defaulty: istniejące rzędy dostają `false`/`null`/`null` → ikona RSVP **ukryta** dopóki następny SyncWorker nie odświeży z backendu (post WO-MOB-002 deploy → backend zwraca prawdziwe wartości).

## Composable diff — kluczowa zmiana

**PRZED (StatusIconsRow.kt:339-349):**
```kotlin
val attendanceRaw = participant.attendanceStatus?.lowercase() ?: ""
val (rsvpIcon, rsvpColor) = when {
    attendanceRaw in listOf("attending", "confirmed", "rsvp_confirmed") ->
        Icons.Default.EventAvailable to StatusGreen
    attendanceRaw in listOf("declined", "rsvp_declined", "cancelled") ->
        Icons.Default.EventBusy to StatusRed
    attendanceRaw.isNotBlank() && attendanceRaw != "n/a" ->
        Icons.Default.HelpOutline to StatusGray
    else -> Icons.Outlined.Event to cs.outlineVariant
}
StatusIcon(rsvpIcon, rsvpColor, "RSVP")  // ZAWSZE renderowane
```

**PO:**
```kotlin
// WO-MOB-003: conditional render — RSVP icon hidden entirely when not sent.
if (participant.rsvpSent) {
    val (rsvpIcon, rsvpColor) = when (participant.rsvpResponse?.lowercase()) {
        "confirmed" -> Icons.Default.EventAvailable to StatusGreen
        "declined" -> Icons.Default.EventBusy to StatusRed
        else -> Icons.Default.HourglassEmpty to StatusGray  // czeka na odpowiedź
    }
    StatusIcon(rsvpIcon, rsvpColor, "RSVP")
}
// Płatność i Check-In renderowane bez zmian poniżej
```

**Efekt dla Sylwia Baran case:** `rsvp_sent=false` (mail nie poszedł) → ikona RSVP **w ogóle się nie pokazuje**, rząd ma 2 ikony zamiast 3.

## Gates

| Gate | Status | Klucz finding |
|---|---|---|
| 🔒 Security | PASS | 0 Crit/High/Med. RSVP fields jako business status plaintext (parity z istniejącymi 25 PII kolumnami w `participants`). Constraint §16 N/A. Brak nowych logów PII. ProGuard wildcard chroni `@Json` fields. 1 niskie: schema 9.json untracked do dodania w commit. |
| 🔗 Contract Sync | PASS | 4-warstwowa consistency. Backend SELECT alias ↔ DTO `@Json` ↔ Room `@ColumnInfo` ↔ Domain — wszystkie identyczne snake_case. 4 mappery propagują 3 pola. Walk-ini spójne. `api-types.ts` × 3 N/A (native Kotlin). |
| 🗄️ Migration Guard | PASS | Room v8→v9 `ALTER TABLE ADD COLUMN` × 3 idempotentne w transakcji. `defaultValue="0"` safe degradation. Schema 9.json identity hash matches. 2 niskie informational: (a) stale release KSP cache; (b) brak MigrationTest infra → P2. |
| 🧪 QA | DEFERRED | Wymaga APK install + device. Build PASS już potwierdził compile + schema gen. 4-stan QA na event `24311000000909074` — user-driven post-commit. |

## Postmortem (4 pytania)

### Co działa

- Wszystkie 4 warstwy aplikacji mobile konsumują 3 nowe pola RSVP end-to-end.
- Build (`assembleDebug`) PASS w 7m 10s.
- Room schema v9 auto-generated, identity hash zgodny.
- Composable warunkowy render: ikona ukryta gdy `rsvpSent=false` (slot wycięty z rzędu — decyzja UX z `/master ustal odpowiedzi` 2026-05-19).
- Backwards compat: Moshi defaults `false`/`null` → stara wersja klienta z nowym backendem działa graceful, nowa wersja klienta ze starym backendem też (ikona ukryta wszędzie, safe-by-default).
- Dead code `translateRsvp()` usunięty (verified 0 callerów).

### Co nie działa / known issues

- **Brak `MigrationTest` infrastructure** w `:core-database:`. Best-practice Room (`MigrationTestHelper`) niezaadresowane. Mityguje to runtime identity hash check przy `Database.openHelper` (crash natychmiast jeśli mismatch) ale boundary cases (np. partial migration crash w obrębie transakcji) nie są wyłapywane testami. Follow-up WO planowane.
- **Stale release KSP cache** — `core-database/build/generated/ksp/release/.../MdDatabase_Impl.kt:62` pokazuje wciąż `version=8, identityHash="7a949...`". Zregeneruje się przy następnym `assembleRelease` lub `clean build`. Nie blocker — wpływa tylko na ewentualny release APK build, debug już OK.
- **Mobile QA on-device niewykonane** — wymaga APK install (debug build) lub EAS update. User musi wykonać post-commit weryfikację 4 stanów (A/B/C/D na event `24311000000909074`).

### Co odłożone

- **APK QA test acceptance** (`gradle build → install → 4 stany na event`) — user-driven post-commit
- **WO-MOB-004 follow-up: refactor mapper triple-duplication** — `Participant` mapper jest w 4 miejscach (SyncWorker DTO→Entity, ParticipantDetailsViewModel × 2 main+legacy, ParticipantsViewModel) bez dedykowanego `ParticipantMapper.kt`. Każde dodanie pola = 4 edycje. Refactor pozwoliłby single point of edit + jednostkowe testy mappera.
- **Migration test infrastructure** — dodać `MigrationTestHelper` w `androidTest/`, asercje że v8 DB + przykładowy rząd → v9 daje `rsvp_sent=0`, `rsvp_response=null`, `rsvp_responded_at=null` + correct schema.
- **EAS production build** — po zaakceptowaniu w debug, `eas build --platform android --profile production` + APK distribution.
- **Drugi mobile codebase `MD_mobile_android/`** (poza monorepo) — wymaga user-side klaryfikacji czy dead branch czy aktywny equivalent.

### Lessons learned

1. **Pre-flight check WORKS.** Master Agent zrobił `git status` w submodule **PRZED** snapshot/implementer dispatch (lekcja z WO-MOB-002 mixed commit incident). Submodule był clean (tylko 2 stare untracked notes), więc safe. Implementer poprawnie zatrzymał się przed gitem — `git add`/`commit` zostały w gestii Mastera.

2. **Mapper triple-duplication w mobile codebase.** Mobile native Kotlin nie ma dedykowanego `ParticipantMapper.kt` — mappery są inline w 4 lokalizacjach. Wzorzec architektoniczny submodule = mappery inline w `core-sync` workers + `feature-*/viewmodel`. Każde nowe pole w `Participant` wymaga 4 edycji (bug-prone). Proponowany follow-up WO-MOB-004: refactor do `ParticipantMapper.kt` z funkcjami `ParticipantDto.toEntity()` + `ParticipantEntity.toDomain()`. Gotcha dopisać do `known_gotchas.md`.

3. **Room migration `defaultValue` jako safe-by-default.** `rsvp_sent INTEGER NOT NULL DEFAULT 0` zapewnia że istniejące rzędy w lokalnym cache nie pokazują false-positive informacji (ikona RSVP zielona). Wartość `false` → ikona ukryta → graceful degradation dopóki SyncWorker nie odświeży z backendu. Wzorzec do replikacji w przyszłych migracjach.

4. **`identityHash` Room jest deterministyczny.** Schema export (`exportSchema = true`) + identity check w runtime = automatyczne wykrywanie schema drift bez testów jednostkowych. Wystarczy commit `schemas/.../*.json` do gita razem z migracją.

5. **Backwards compat przez Moshi defaults.** Pre-WO-MOB-002 backend brak pól RSVP → Moshi pomija → DTO defaults `false`/`null` → graceful degradation. Wzorzec `val rsvpSent: Boolean = false` dla nowych Boolean fields i `val xxx: String? = null` dla optional fields → never crash on missing JSON keys.

## Cross-references

- WO: [WO-MOB-003](WO-MOB-003-mobile-rsvp-status-conditional-render.md)
- Review: [REVIEW-WO-MOB-003](review_notes/REVIEW-WO-MOB-003.md)
- Parent (backend): [WO-MOB-002](WO-MOB-002-fix-rsvp-status-participant-details.md) + [IMPLEMENTATION_REPORT_WO_MOB_002](IMPLEMENTATION_REPORT_WO_MOB_002.md)
- ADR (decision_log.md, 2026-05-19): "Mobile RSVP icon: hidden slot when rsvp_sent=false (vs gray nie dotyczy)" — zapisany w poprzedniej sesji `/master ustal odpowiedzi`
- Snapshot tag: `snapshot/pre-wo-mob-003-mobile-rsvp-render-2026-05-19` (monorepo `27c77f61` + simple-event-checkin submodule `cfbdbb66` na HEAD `5d578bd`)
- Backend dependency: commit `e99c3d0` (mixed-content) + `18c1bfc` (docs) — LIVE deployed na Render od 2026-05-19
- Build artifact: `simple-event-checkin/feature-participants/build/outputs/apk/debug/` (po commit można odpalić `./gradlew :app:assembleDebug` dla pełnego APK)
- Mobile submodule branch: `master` (po commit będzie HEAD bumpnięty w main repo)
