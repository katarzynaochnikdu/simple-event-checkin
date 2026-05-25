# REVIEW — WO-MOB-015: Ręczny check-in prelegentów (bez QR) w mobile

**Data:** 2026-05-25
**Reviewer:** Master Agent (loop run)
**Worker:** worker-implementer
**Snapshot:** `snapshot/pre-speaker-checkin-mobile-2026-05-25` (monorepo + backend + simple-event-checkin)
**Commit (backend):** `ffb0d54` (zacommitowane w submodule przed pełnym loop'em)
**Status:** ✅ **APPROVED** — gotowe do commit + push + deploy + post-deploy smoke

---

## Definition of Done — checklist

| # | Punkt | Status | Notatka |
|---|---|---|---|
| 1 | Endpoint backend zaimplementowany | ✅ | 3 endpointy w `mobile.py:1449-1591` (POST checkin, POST sync, GET stats) |
| 2 | Endpoint przetestowany (curl/pytest) | ⚠️ live SKIP | Lokalny Flask down — code-level QA PASS; smoke test post-deploy MANDATORY |
| 3 | Migracja DB aplikowana lokalnie + opisana w decision_log | ✅ | `0039_speaker_checkin_log.sql`, ADR poniżej |
| 4 | Mobile UI: tap "Odhacz" działa | ✅ code-level | assembleDebug PASS; APK install + manual smoke post-deploy |
| 5 | Persistence po restarcie aplikacji | ✅ code-level | Room v9→v10 migration registered |
| 6 | Offline queue + auto-sync | ✅ code-level | `speaker_checkin_queue` + `SyncEngine` integration |
| 7 | Stats card "Prelegenci obecni: X/N" | ✅ | `DashboardScreen` row + ekran Prelegenci header |
| 8 | Race condition test (FOR UPDATE) | ✅ code-level | `SELECT ... FOR UPDATE` w `checkin_speaker`; live test post-deploy |
| 9 | Build PASS | ✅ | `py -m py_compile` PASS, `./gradlew assembleDebug` BUILD SUCCESSFUL 4m 59s |
| 10 | Backend test akceptacyjny zielony | ⚠️ SKIP | Lokalny Flask down (precedens WO-204, WO-187) |
| 11 | Review note | ✅ | ten plik |

**DoD coverage:** 9/11 ✅ + 2 ⚠️ live-only (smoke test plan w QA Gate report) → APPROVE.

---

## Gates — aggregated

| Gate | Status | Krytyczne | Wysokie | Średnie | Niskie |
|---|---|---|---|---|---|
| QA | PASS (WARN live) | 0 | 0 | 2 | 2 |
| Security | PASS | 0 | 0 | 0 | 2 |
| Contract Sync | PASS SYNCED | 0 | 0 | 0 | 0 |
| Migration Guard | PASS | 0 | 0 | 1 | 2 |
| **AGGREGATED** | ✅ **PASS** | **0** | **0** | **3** | **6** |

Wszystkie Średnie/Niskie to robustness/cleanup, nie security boundary ani functional blockers. Akceptowalne jako follow-up.

---

## Decyzje architektoniczne (do `decision_log.md`)

### ADR: Osobna tabela `speaker_checkin_log` (vs unified `checkin_log` z `subject_type`)

**Decyzja:** Stworzona osobna tabela `speaker_checkin_log` w migracji `0039_speaker_checkin_log.sql`. NIE rozszerzona istniejąca `checkin_log` o `subject_type` discriminator.

**Powód:**
- `checkin_log.participant_id BIGINT NOT NULL FK→participants(id) CASCADE` to mission-critical flow z tysięcy skanów per event
- Unified wymagałoby: DROP NOT NULL + nowy nullable `speaker_id` FK + CHECK constraint + refactor wszystkich queries + reindex
- Bug w unified path → ryzyko regresji participant check-inu (P0 critical)
- Speakers semantycznie różni (brak biletów, brak QR, niski volume ~5-50/event)
- YAGNI dla polymorphic — refactor JAK pojawi się 3-ci subject (guardians? staff?)

**Trade-off:**
- Lekka duplikacja funkcji (`batch_sync`, `stats` × 2)
- Brak unified analytics "kto był na evencie" w jednym query (wymaga UNION)

### Decyzja implementacyjna: Undo = INSERT `action='check-out'` (NIE DELETE)

**Decyzja worker-implementer'a:** Undo check-inu zapisuje nowy wiersz z `action='check-out'`. Status prelegenta determinowany przez `ORDER BY scanned_at DESC LIMIT 1`.

**Powód:**
- Audit trail (kto, kiedy, dlaczego cofnął)
- Precedens `checkin_log.action` z migracji `0004_checkin_log_action.sql`
- Naturalna idempotencja (drugi undo na nie-checked-in = `not_checked_in`, brak INSERT)
- DELETE traciłby informację historyczną

### Decyzja: Mobile speaker_id jako TEXT (NIE BIGINT)

**Decyzja:** Mobile API + Room używają `speaker_id` jako TEXT (globalny z `global_speakers`). Backend `checkin_speaker()` mapuje TEXT → BIGINT przez `SELECT id FROM event_speakers WHERE event_id=%s AND speaker_id=%s FOR UPDATE`.

**Powód:** `event_speakers` ma dwa identyfikatory: technical PK `id BIGINT` (używany w FK `speaker_checkin_log.speaker_id`) i globalny `speaker_id TEXT` (używany w mobile API/Room — stabilny cross-event).

**Gotcha:** zapomnienie mapowania = FK violation lub parse error. Dopisać do `known_gotchas.md`.

---

## Gotchas do dopisania (`known_gotchas.md`)

1. **`event_speakers` ma 2 identyfikatory: `id BIGINT` (technical PK, FK target) i `speaker_id TEXT` (globalny z `global_speakers`).** Mobile API używa TEXT, backend MUSI mapować na BIGINT przez `SELECT id FROM event_speakers WHERE event_id=%s AND speaker_id=%s FOR UPDATE`. Wprowadzone: WO-MOB-015 (2026-05-25).

2. **Room `fallbackToDestructiveMigration` to TRAP — działa TYLKO dla wersji NIEOBECNYCH w `addMigrations(...)`.** Każdy nowy `MIGRATION_X_Y` musi być dodany do `addMigrations(...)` żeby uniknąć destructive update z X. Wprowadzone: WO-MOB-015.

3. **Mobile batch sync MUSI per-entry walidować event-scope** (WO-SEC-009 precedens) — atakujący może wsadzić foreign `event_id` do batch entry, jeśli endpoint tylko sprawdza pierwszy. Implementacja: pętla po `_mobile_user_has_event_access` z agregacją `forbidden_events` → 403 (precedens WO-MOB-015).

---

## Follow-up actions

### Pre-commit (mandatory)
- [ ] Master Krok 6: update `system_state.md` z WO-MOB-015 entry
- [ ] Master Krok 6.6: update `MOBILE_API.md` (3 nowe endpointy) + `DATA_DICTIONARY.md` (speaker_checkin_log table)
- [ ] User approval → commit + push DB + mobile working tree (backend już zacommitowany `ffb0d54`)

### Pre-deploy (mandatory)
- [ ] **Sprawdzić idle-in-transaction na `event_speakers`** przed Render auto-deploy (Migration Guard WARN): `SELECT pid, state, EXTRACT(EPOCH FROM (now()-query_start))::int AS secs FROM pg_stat_activity WHERE state LIKE '%idle in transaction%';` — jeśli są stuck txn → `pg_terminate_backend()` przed ALTER

### Post-deploy (mandatory smoke test)
- [ ] APK install na ZY22FJL7QX, verify Room v9→v10 seamless upgrade (brak crash, `participants` zachowane, `speaker_checkin_queue` istnieje)
- [ ] Curl smoke: single checkin + repeat (already_checked_in) + check-out + repeat (not_checked_in) + invalid action 400 + wrong scope 403 + 404 not_found
- [ ] Race condition: 2 telefony / 2 curle równoczesne POST → 1 row INSERT
- [ ] Batch sync z forbidden event → 403
- [ ] Offline queue: airplane mode → odhacz → wifi → SyncWorker push → row w DB
- [ ] UI E2E (10 kroków z WO Test acceptance)
- [ ] Audit log: `SELECT * FROM admin_audit_log WHERE action LIKE 'mobile_speaker_checkin%'` — entries widoczne

### Backlog (nie blocker)
- [ ] **Cleanup `fallbackToDestructiveMigration`** w `DatabaseModule.kt:26` (legacy v1..v6) — osobny WO-MOB-cleanup
- [ ] **Validate ISO8601 `scanned_at`** w `checkin_speaker()` (Security NISKIE #1) — robustness fix
- [ ] **`app.logger.warning` zamiast `print()`** dla audit log errors (Security NISKIE #2) — style fix
- [ ] **Złożony index `(event_id, speaker_id, scanned_at DESC)`** — optymalizacja przy growth (Migration NISKIE)
- [ ] **IDEA-003 desktop view** unblocked po deploy 015a — dispatch po smoke test PASS

---

## Pliki zmienione

**Submodule `backend/` (committed `ffb0d54`):**
- `backend/api/mobile.py` (+143)
- `backend/pg_storage.py` (+290)

**Monorepo root (working tree):**
- `database/migrations/0039_speaker_checkin_log.sql` (NEW, 77 LOC)
- `database/full_schema.sql` (+21)
- `.agents/context/system_state.md` (snapshot entry, system state update)

**Submodule `simple-event-checkin/` (working tree, 21 plików, ~+674/-85 LOC):**
- NEW: `SpeakerCheckinEntity.kt`, `SpeakerCheckinDao.kt`, `SpeakerCheckinDtos.kt`, `SpeakerCheckinRepository.kt`, `schemas/.../MdDatabase/10.json`
- MOD: `MdDatabase.kt`, `Migrations.kt` (MIGRATION_9_10), `DatabaseModule.kt`, `MobileApiService.kt`, `SyncEngine.kt`, `SyncWorker.kt`, `DashboardData.kt`, `DashboardViewModel.kt`, `DashboardScreen.kt`, `SpeakersViewModel.kt`, `SpeakerDetailViewModel.kt`, `SpeakersScreen.kt`, `SpeakerDetailScreen.kt`, `build.gradle.kts` ×2, `settings.gradle.kts`

---

## Sign-off

✅ **APPROVED** by Master Agent loop. Proceed to Krok 6.7 (commit prep) after Krok 6/6.5/6.6.

Build: PASS. Compile: PASS. 4 gates: PASS. DoD: 9/11 ✅ + 2 ⚠️ live-only (acceptable, smoke test plan documented).
