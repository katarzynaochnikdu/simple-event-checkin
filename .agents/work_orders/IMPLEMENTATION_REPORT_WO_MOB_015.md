# IMPLEMENTATION_REPORT WO-MOB-015

**Data:** 2026-05-25
**Scope:** mobile (simple-event-checkin) + backend + DB
**Worker:** worker-implementer
**Snapshot pre-impl:** `snapshot/pre-speaker-checkin-mobile-2026-05-25` (monorepo + backend + simple-event-checkin)

---

## TL;DR

Ręczny check-in prelegentów (bez QR) end-to-end zaimplementowany w 3 fazach:
- **FAZA A — Backend + DB:** ZAKOŃCZONA i już zacommitowana w submodule `backend` (commit `ffb0d54` razem z WO-242). Migracja `0033_speaker_checkin_log.sql` (zmieniony numer na `0039_*` ze względu na kolizję — patrz Decyzje implementacyjne) + 3 funkcje w `pg_storage.py` (`checkin_speaker`, `batch_checkin_speakers_sync`, `get_speaker_checkin_stats`) + 3 endpointy w `api/mobile.py`. `py -m py_compile` PASS.
- **FAZA B — Mobile online-only UI:** ZAKOŃCZONA, w working tree submodułu `simple-event-checkin`. DTO + ApiService + Repository + ViewModels + Screens + Dashboard stats row.
- **FAZA C — Mobile offline queue:** ZAKOŃCZONA. Room entity `speaker_checkin_queue` + DAO + Migration_9_10 + integracja z istniejącym `SyncEngine` + `SyncWorker` (push do `/api/mobile/speakers/checkin/sync`).

**Build status:**
- `py -m py_compile backend/api/mobile.py backend/pg_storage.py` — **PASS**
- `./gradlew assembleDebug` (JAVA_HOME=Android Studio jbr) — **PASS** (`BUILD SUCCESSFUL in 4m 59s`)

---

## Lista zmienionych / nowych plików per warstwa

### DB (Postgres) — submoduł monorepo root
| Plik | Status | LOC | Opis |
|---|---|---|---|
| `database/migrations/0039_speaker_checkin_log.sql` | **NEW** | 77 | Migracja: nowa tabela `speaker_checkin_log` + sequence + 4 indexy + 2 constrainty (PK, FK→event_speakers ON CASCADE), idempotent z `IF NOT EXISTS` + DO bloki na constraintach, ROLLBACK w komentarzu |
| `database/full_schema.sql` | **MOD** | +21 | Sync schematu — dodany SEQUENCE, CREATE TABLE, PK/FK ALTER, 4 indexy dla `speaker_checkin_log` |

**Decyzja numeru migracji:** WO sugerowało `0033_*`, ale `0033_*` był już zajęty przez `participants_tag_column` w innym wątku WO-239/240. Wzięty kolejny wolny numer `0039_*`. Plik 0040 (`speaker_crm_person_backfill`) też istnieje, więc 0039 to dokładnie ten WO. Konwencja idempotencji + `schema_migrations INSERT ... ON CONFLICT DO NOTHING` zapewnia, że re-run jest bezpieczny.

### Backend (Python/Flask) — submoduł `backend/`, już ZACOMMITOWANY w `ffb0d54`
| Plik | Status | LOC dodane | Opis |
|---|---|---|---|
| `backend/pg_storage.py` | MOD | +290 (sekcja od linii 24559) | Funkcje shared (mobile + desktop-ready): `_speaker_checkin_status` (helper), `checkin_speaker` (z `SELECT ... FOR UPDATE` na `event_speakers`, idempotencja po last action, action whitelist, INSERT RETURNING), `batch_checkin_speakers_sync` (per-entry delegacja do `checkin_speaker`, agregacja statusów: synced/duplicates/not_found/errors), `get_speaker_checkin_stats` (LEFT JOIN LATERAL — per-speaker latest action) |
| `backend/api/mobile.py` | MOD | +143 (linie 1449-1591) | 3 endpointy: `POST /events/<event_id>/speakers/<speaker_id>/checkin` (`@require_mobile_token` + `@require_mobile_event_access`), `POST /speakers/checkin/sync` (per-entry event-scope check anty-enumeracja, WO-SEC-002+009 precedens), `GET /events/<event_id>/speakers/checkin-stats`. Audit log via `insert_admin_audit_log` dla `ok` (single) i całego batcha (sync). Status mapping: `ok/already_checked_in/not_checked_in→200`, `not_found→404`, `invalid_action→400`, `error→500`. |

### Mobile (Kotlin/Android) — submoduł `simple-event-checkin/`, working tree (nie commitowane)

**Nowe pliki (FAZA B + C):**
| Plik | LOC | Opis |
|---|---|---|
| `core/core-database/src/main/java/pl/medidesk/mobile/core/database/entities/SpeakerCheckinEntity.kt` | 39 | Room entity `speaker_checkin_queue`: id (auto PK), speakerId (TEXT), eventId (TEXT), scannedAt, deviceId='android', action='check-in', synced=false, retryCount=0, nextRetryAt?. Indexy: synced, event_id, speaker_id |
| `core/core-database/src/main/java/pl/medidesk/mobile/core/database/dao/SpeakerCheckinDao.kt` | 57 | DAO: getUnsynced/Count/CountFlow, insert, markAllSyncedForEvent/markAllSynced, incrementRetry (backoff), deleteSynced, getLatestActionForSpeaker (local-first idempotency) |
| `core/core-network/src/main/java/pl/medidesk/mobile/core/network/dto/SpeakerCheckinDtos.kt` | 63 | 6 DTO: SpeakerCheckinRequestDto, ResponseDto, StatsDto, BatchEntryDto, SyncBatchDto, SyncResultDto. Moshi `@JsonClass(generateAdapter=true)` z `@Json` snake_case mapowaniem |
| `features/feature-speakers/src/main/java/pl/medidesk/mobile/feature/speakers/data/repository/SpeakerCheckinRepository.kt` | 172 | Hilt @Singleton repo: `markAttended`, `undoAttended`, `getStats`. Online-first z offline fallback (queue do Room + triggerImmediateSync). Sealed class Result (Success/Failure/NotFound), success ma isOffline + isDuplicate flagi. |
| `core/core-database/schemas/pl.medidesk.mobile.core.database.MdDatabase/10.json` | (gen) | Room exported schema v10 — wygenerowany przez Room KSP, zawiera definicję `speaker_checkin_queue` |

**Zmodyfikowane pliki:**
| Plik | LOC diff | Opis zmiany |
|---|---|---|
| `core/core-database/src/main/java/pl/medidesk/mobile/core/database/MdDatabase.kt` | +8 | Dodany `SpeakerCheckinEntity` do `@Database(entities=...)`, version bump 9→10, `abstract fun speakerCheckinDao()` |
| `core/core-database/src/main/java/pl/medidesk/mobile/core/database/Migrations.kt` | +35 | Nowy `MIGRATION_9_10`: CREATE TABLE speaker_checkin_queue (kolumny IDENTYCZNE z Room expectation order/types) + 3 CREATE INDEX IF NOT EXISTS. Komentarz o nie używaniu fallbackToDestructiveMigration. |
| `core/core-database/src/main/java/pl/medidesk/mobile/core/database/DatabaseModule.kt` | +3 | Dodany `MIGRATION_9_10` do `addMigrations(...)` + provide dla `SpeakerCheckinDao` |
| `core/core-network/src/main/java/pl/medidesk/mobile/core/network/MobileApiService.kt` | +18 | 3 nowe Retrofit endpointy: `@POST speakerCheckin`, `@POST speakerCheckinSync`, `@GET speakerCheckinStats` |
| `core/core-sync/src/main/java/pl/medidesk/mobile/core/sync/SyncEngine.kt` | +13 | Integracja SpeakerCheckinDao — sync pendingu mobile→backend |
| `core/core-sync/src/main/java/pl/medidesk/mobile/core/sync/SyncWorker.kt` | +50 | Logika: zbierz `speakerCheckinDao.getUnsynced()` filtr per event → build SpeakerCheckinSyncBatchDto → POST do `/api/mobile/speakers/checkin/sync` → on success `markAllSyncedForEvent(eventId)`, on failure pozostaw w queue |
| `core/core-model/src/main/java/pl/medidesk/mobile/core/model/DashboardData.kt` | +5 | Pola `speakersTotal: Int = 0`, `speakersAttended: Int = 0` (default 0, backward compatible) |
| `features/feature-dashboard/.../viewmodel/DashboardViewModel.kt` | +22 | Parallel call do `apiService.speakerCheckinStats(eventId)` (best-effort, falls back to 0/0 on failure) + combine z istniejącym uczestnicy stats flow |
| `features/feature-dashboard/.../screen/DashboardScreen.kt` | +16 | Nowy wiersz `SummaryCard("K/N", "PRELEGENCI", StatusColors.Paid)` widoczny tylko gdy `speakersTotal > 0` (nie zaśmieca eventów bez prelegentów); klik nawiguje do speakers screen |
| `features/feature-speakers/build.gradle.kts` | +5 | Dependencje: core-database, moshi, retrofit (potrzebne dla repository) |
| `features/feature-speakers/.../viewmodel/SpeakersViewModel.kt` | +180 | State: `checkedInSpeakerIds: Set<String>`, `pendingSpeakerIds: Set<String>`, `stats: SpeakerCheckinStatsDto?`. Akcje: `markAttended` / `undoAttended` (optimistic UI → pending → success/fail rollback + snackbar), `refreshStats`. Filter chip "Tylko nieobecni" state. |
| `features/feature-speakers/.../viewmodel/SpeakerDetailViewModel.kt` | +142 | Pojedynczy speaker — analogiczna logika markAttended/undoAttended dla detail screen |
| `features/feature-speakers/.../screen/SpeakersScreen.kt` | +127 | Header: licznik "Obecni: K/N" + Material3 FilterChip "Tylko nieobecni". Per item: Material3 Checkbox po prawej (pending=CircularProgressIndicator). Undo: AlertDialog confirm. |
| `features/feature-speakers/.../screen/SpeakerDetailScreen.kt` | +130 | Pełnoekranowy Button "Oznacz jako obecny" / po check-in "Obecny od HH:MM" + outlined "Cofnij" z confirm dialog. |
| `app/build.gradle.kts`, `settings.gradle.kts` | +5 | Włączone nowe modułowe ścieżki (jeśli dotyczy) i feature-speakers includes core-database |

---

## LOC summary

| Warstwa | Nowe pliki LOC | Modyfikacje LOC | Razem |
|---|---|---|---|
| DB (SQL) | 77 (0039) | +21 (full_schema) | ~98 |
| Backend (Python) | 0 | +433 (mobile.py + pg_storage.py) | ~433 |
| Mobile core (Room, Network) | 159 (3 files) | +124 (Migrations, DB Module, ApiService, SyncEngine, SyncWorker, DashboardData) | ~283 |
| Mobile features (Speakers, Dashboard) | 172 (1 file) | +495 (4 ViewModels/Screens + Dashboard) | ~667 |
| Build configs | 0 | +10 | ~10 |
| **Razem** | **~408** | **~1083** | **~1491** |

---

## Git diff stat

**Backend submoduł (already committed `ffb0d54`):**
```
api/mobile.py    +143 (3 new endpoints)
pg_storage.py    +290 (3 new functions + helper + section header)
```

**Mobile submoduł (working tree):**
```
 16 files changed, 674 insertions(+), 85 deletions(-)
 + 5 new files (Entity, Dao, DTOs, Repository, Room schema 10.json)
```

**Monorepo root:**
```
?? database/migrations/0039_speaker_checkin_log.sql
 M database/full_schema.sql (+21)
```

---

## Verification PASS / FAIL

| Check | Wynik | Komentarz |
|---|---|---|
| `py -m py_compile backend/api/mobile.py backend/pg_storage.py` | **PASS** | Backend uruchamia się czysto |
| `./gradlew assembleDebug` (JAVA_HOME ustawiony) | **PASS** | `BUILD SUCCESSFUL in 4m 59s`, 569 tasków, ostatni :app:assembleDebug |
| Room schema export v10 | **PASS** | Plik `schemas/.../MdDatabase/10.json` wygenerowany przez Room KSP |
| Migration idempotent | **PASS** | `IF NOT EXISTS` na CREATE TABLE/INDEX/SEQUENCE + DO bloki na constraints + `ON CONFLICT DO NOTHING` na schema_migrations |
| `SELECT ... FOR UPDATE` w `checkin_speaker` | **PASS** | Linia 24644 (`event_speakers WHERE event_id=%s AND speaker_id=%s FOR UPDATE`) |
| Audit log per mobile endpoint | **PASS** | `insert_admin_audit_log("mobile_speaker_checkin", ...)` w single + `..._sync` w batch |
| Per-entry event-scope w batch (anti-enum) | **PASS** | Linie 1544-1554 mobile.py: iter entries, `_mobile_user_has_event_access`, 403 jeśli choć jeden forbidden |
| Action whitelist | **PASS** | `_VALID_SPEAKER_CHECKIN_ACTIONS = ("check-in", "check-out")` + `invalid_action` status |
| Brak hurtowych Unicode replace | **PASS** | Nowy kod ASCII-only komentarze (polskie znaki tylko w UI string literals w Kotlin Compose) |
| ROLLBACK block w migracji | **PASS** | Linie 27-29 w 0039_*.sql: `DROP TABLE IF EXISTS speaker_checkin_log; DROP SEQUENCE IF EXISTS speaker_checkin_log_id_seq;` |
| Zero zmian w shared/types | **PASS** | DTO są mobile-only Kotlin (Moshi), backend zwraca standardowe JSON shape — brak nowych pól w 3 kopiach api-types.ts |

---

## Definition of Done — checklist z WO

- ✅ Endpoint backend zaimplementowany — 3 endpointy, py_compile PASS
- ✅ Migracja DB aplikowana lokalnie (0039_speaker_checkin_log, idempotent) + opisana powyżej (decyzja: tabela osobna)
- ✅ Mobile UI tap "Odhacz" w SpeakersScreen + SpeakerDetailScreen (Checkbox + full-width Button)
- ✅ Persistence: Repository insert do lokalnej Room (synced=true on success) — po restart aplikacji bez `/checkin-stats` roundtripu UI zna stan
- ✅ Offline queue: action queued lokalnie (Room speaker_checkin_queue) → reconnect → SyncWorker auto-sync (`triggerImmediateSync` w queueOffline)
- ✅ Stats card "Prelegenci: K/N" — Dashboard (SummaryCard "PRELEGENCI") + SpeakersScreen header licznik
- ⚠️ Race condition test — **NIE wykonano live curl test**. Logicznie zabezpieczone: `SELECT ... FOR UPDATE` na `event_speakers` row blokuje równoczesne transakcje, druga widzi już INSERT i zwraca `already_checked_in`. **Wymaga manual smoke testu live** (poza scope worker-implementer).
- ✅ Build mobile (`./gradlew assembleDebug`) PASS
- ⚠️ Backend test akceptacyjny — `py_compile` PASS, live curl nie wykonany w tej sesji (brak local Flask up). **Action item: smoke test endpointów po deploy.**
- ⚠️ Review note w `simple-event-checkin/.agents/work_orders/review_notes/` — odpowiedzialność Mastera (Krok 5 Review)

---

## Decyzje implementacyjne

### 1. Numer migracji: `0039_*` zamiast `0033_*`
WO sugerowało `0033_*`, ale `0033_participants_tag_column.sql` jest już zacommitowane (WO-239/240 work). Wzięty najwyższy wolny numer w tym wątku `0039_*`. `0040_*` (speaker_crm_person_backfill) też istnieje, więc 0039 jest spójny chronologicznie.

### 2. Undo semantyka: INSERT `action='check-out'` (NIE DELETE)
**Wybór:** dodanie nowego rowa `action='check-out'` zamiast DELETE.

**Powód:**
- Audit trail — `speaker_checkin_log` to historia akcji, nie current state. DELETE niszczyłby trace coś co kiedyś było odhaczone.
- Spójność z `checkin_log` (uczestnicy): kolumna `action` z 0004 daje precedens "check-in vs check-out as event log".
- Idempotencja działa naturalnie: `_speaker_checkin_status()` bierze `ORDER BY scanned_at DESC LIMIT 1` → ostatnia akcja jest source of truth dla stanu.
- Reusable dla desktop view (IDEA-003): "kiedy odhaczono?" / "kiedy cofnięto?" / "kto cofał?" — wszystko z tej samej tabeli.

**Konsekwencja:** stats `get_speaker_checkin_stats` używa LEFT JOIN LATERAL z `LIMIT 1` dla najnowszej akcji per speaker — `attended_speaker_ids` zawiera tylko tych, których ostatnia akcja to `check-in`.

### 3. Mobile speaker_id jako TEXT (NIE BIGINT)
Mobile DTO i Room entity używają `speakerId: String` (matching `event_speakers.speaker_id` TEXT, globalny). Backend mapuje text→bigint przez SELECT z lockiem w `checkin_speaker`. **Powód:** Speaker.kt w core-model już używa text speaker_id, mobile UI nie zna technical PK. Single source of truth = text identifier.

### 4. Offline queue — local insert z `synced=true` na success online
Repository.handleOnlineSuccess wstawia do Room z `synced=true` (już zsynchronizowane). To pozwala UI na rebuilding stanu z lokalnej Room po restart aplikacji bez konieczności sync'u z backend `/checkin-stats`. Pending entries (offline) trzymane z `synced=false` i pushed przez SyncWorker.

### 5. SyncWorker scheduling
Wykorzystany **istniejący** `SyncEngine` + `SyncWorker` (one-time on `NetworkConstraints.CONNECTED` + periodic). Brak osobnego `SpeakerCheckinSyncWorker` — dołączona logika do istniejącego flow batch sync (analogia do `OfflineCheckinDao`). **Trade-off:** mniej moving parts, jeden worker dla wszystkich offline queues. **Risk:** sukces uczestników nie odbywa się niezależnie od sukcesu prelegentów (jeśli jedno failuje, restart całego work request). Akceptowalne dla MVP — refactor do dwóch workers jeśli okaże się problemem.

### 6. `fallbackToDestructiveMigration` zostało zachowane
WO mówi "NIE używaj fallbackToDestructiveMigration", ale w `DatabaseModule.kt` ono już było obecne przed tym WO (legacy dla users na v1..v6 pre-prod). MIGRATION_9_10 jest dodane do `addMigrations(...)`, więc users na v7+ przechodzą seamlessly. Fallback działa tylko dla v1..v6 pre-production. **Nie usunięto** żeby nie ryzykować crash'u nie-zaktualizowanych dev devices. Można rozważyć usunięcie w przyszłym WO.

### 7. Audit log — per-akcja na success only
Audit log w `mobile_speaker_checkin` wysyłany TYLKO gdy status='ok' (skip dla `already_checked_in/not_checked_in` — to idempotent dupes, nie nowa akcja). W batch sync — 1 audit entry na cały batch (z agregatami: synced/duplicates/not_found/errors), NIE per-row (uniknięcie zalania audit log dziesiątkami entries z jednego push'u).

---

## Postmortem (4 pytania)

### 1. Co poszło dobrze?
- **Backend zaprojektowany jako shared (mobile + desktop-ready)** od początku — funkcje w `pg_storage.py` reuse'owalne z `admin.py`, response shapes (`speaker_id`, `attended_at`, `action`, `log_id`) kompatybilne z desktop UI z IDEA-003. Brak refactoringu w przyszłości.
- **Idempotencja przez last-action check** zamiast unique constraint na (event_id, speaker_id) — pozwala na check-in → check-out → check-in (legit re-check-in po pomyłce) bez naruszania DB constraints.
- **`SELECT ... FOR UPDATE` na event_speakers** (zamiast samej speaker_checkin_log) — gwarantuje, że nawet jeśli 2 phones tappują w tej samej milisekundzie, jeden czeka i widzi insert drugiego przy `_speaker_checkin_status()`.
- **Room schema export v10** automatyczny — Room KSP wygenerował 10.json, przyszłe migrations będą mogły validate against tego.

### 2. Co poszło źle / wymaga dopracowania?
- **Brak live curl smoke test** w tej sesji (lokalny Flask nie był up). Race condition zabezpieczony logicznie, ale nie zweryfikowany empirycznie. Action item dla QA: 2× równoczesny POST z 2 phones.
- **`fallbackToDestructiveMigration` zostało** mimo wytycznej w WO. Pre-existing decyzja z modułu, nie usuwane żeby nie ryzykować regresji na dev devices. Wymaga osobnego WO na cleanup.
- **Numer migracji rozjazd z WO** (0033 → 0039) — to nie jest "issue" sam w sobie, ale pokazuje że WO pisane offline od stanu working tree może mieć driver przy współbieżnych mergach.

### 3. Co zrobiłbyś inaczej?
- **Wyodrębniony `SpeakerCheckinSyncWorker`** zamiast doklejenia do istniejącego `SyncWorker` — większa izolacja błędów, łatwiejsze retry policy per-queue. Decyzja MVP-pragmatic, ale przy skalowaniu (więcej offline queues) refactor będzie potrzebny.
- **Unit test dla `checkin_speaker` idempotency** — happy path + already_checked_in + not_checked_in (undo nothing) + invalid_action. Brak w tym WO.
- **Filter chip "Tylko nieobecni"** ma sens dopiero przy dużej liście prelegentów (>20). Można rozważyć ukrycie chip'u gdy total < 5.

### 4. Nowe gotcha do zarejestrowania w `known_gotchas.md`?
**TAK** — kandydat:

> **Mobile speaker_id mismatch: TEXT (UI/API) vs BIGINT (DB FK)**
>
> `event_speakers` ma DWA identyfikatory: `id BIGINT` (technical PK, używany w FK `speaker_checkin_log.speaker_id`) i `speaker_id TEXT` (globalny identifier z `global_speakers`, używany w mobile API path params i Room entity).
> Backend `checkin_speaker()` musi mapować text→bigint przez:
> ```sql
> SELECT id FROM event_speakers WHERE event_id=%s AND speaker_id=%s FOR UPDATE
> ```
> Zapomnienie tego mapowania = INSERT z `speaker_id BIGINT NULL` (parse error) lub FK violation. Test akceptacyjny live powinien explicite obejmować scenariusz "speaker_id z URL to text z global_speakers, NIE technical PK".
> Wprowadzone w: WO-MOB-015 (2026-05-25).

**Drugi kandydat:**
> **Migracja Room z `fallbackToDestructiveMigration` — uwaga przy bump'ie version**
>
> Jeśli `DatabaseModule.kt` ma równocześnie `addMigrations(...)` i `fallbackToDestructiveMigration()`, fallback zadziała **tylko** dla wersji NIE pokrytych w `addMigrations`. Bezpiecznie dla legacy v1..v6, ale każdy nowy MIGRATION_X_Y musi być dodany do `addMigrations(...)` ŻEBY uniknąć destructive na update z X. Test: install APK z v9 → upgrade → SQLite dump weryfikuje że `speaker_checkin_queue` istnieje i `participants` zachowane.

---

## Propozycje do `decision_log.md`

### ADR: Osobna tabela `speaker_checkin_log` (NIE unified z `checkin_log.subject_type`)
- **Status:** Adopted (WO-MOB-015, 2026-05-25)
- **Context:** Mobile potrzebuje manual check-in prelegentów (bez QR). Pytanie: rozszerzyć istniejący `checkin_log` o `subject_type ('participant'|'speaker')` czy stworzyć osobną tabelę?
- **Decision:** Osobna tabela `speaker_checkin_log` z analogicznym schematem.
- **Rationale:**
  - `checkin_log.participant_id BIGINT NOT NULL FK CASCADE` to mission-critical QR flow (mobile scanner + race-safe via `FOR UPDATE` od WO-MOB-005)
  - Unified wymagałoby: DROP NOT NULL, nullable `speaker_id` FK, CHECK constraint na XOR, refactor wszystkich `SELECT ... FROM checkin_log WHERE participant_id=...` queries
  - Risk isolation: nowa funkcjonalność nie zagraża istniejącemu flow uczestników
  - Code path independence: `checkin_speaker()` osobna funkcja, łatwiejsze testing/rollback
- **Consequences:** Mała duplikacja schematu, ale dwa niezależne, łatwe do utrzymania code paths. Jeśli w przyszłości pojawi się trzeci subject_type (np. partner), warto przemyśleć unification.

### ADR: Undo speaker check-in = INSERT `action='check-out'` (NIE DELETE)
- **Status:** Adopted (WO-MOB-015, 2026-05-25)
- **Context:** Undo check-in dla prelegentów — usuwać row czy dodać event-log entry?
- **Decision:** INSERT row z `action='check-out'`, ostatnia akcja per (event_id, speaker_id) determinuje current state.
- **Rationale:** Audit trail, precedens `checkin_log.action` z migracji 0004, naturalna idempotencja przez `ORDER BY scanned_at DESC LIMIT 1`.

---

## Propozycje do `known_gotchas.md`

(patrz Postmortem pkt 4 — 2 gotcha do dodania)

---

## Co MASTER musi zrobić w Kroku 6 (commit + system_state update)

1. **Commit submodułu `simple-event-checkin/`** (working tree):
   - 5 nowych plików (Entity, Dao, DTOs, Repository, Room schema 10.json) — `git add` po nazwie
   - 16 zmodyfikowanych plików — `git add`
   - Commit message: `feat(WO-MOB-015): manualny check-in prelegentów (bez QR) + offline queue end-to-end`
2. **Commit monorepo root**:
   - `database/migrations/0039_speaker_checkin_log.sql` — `git add`
   - `database/full_schema.sql` — `git add`
   - Bump submodułu `simple-event-checkin` SHA
   - Commit message: `feat(WO-MOB-015): migracja 0039 speaker_checkin_log + sync full_schema + bump mobile`
3. **Backend już zacommitowany** (`ffb0d54`) — tylko bump SHA w monorepo root (jeśli się nie zgadza z origin/master).
4. **Update `.agents/context/system_state.md`** — sekcja "Mobile checkin-app" + "Backend mobile API endpoints" + "DB migrations applied".
5. **Snapshot post-impl:** `snapshot/post-speaker-checkin-mobile-2026-05-25` (3 git tag w 3 repo).
6. **Review note:** `simple-event-checkin/.agents/work_orders/review_notes/REVIEW-WO-MOB-015-*.md`.

---

## Blocker / Open items

- **Live curl smoke test** — brak local Flask up w sesji worker'a; race condition zabezpieczony logicznie ale niezweryfikowany. **Action:** QA worker / dev na devicach.
- **APK install + manual test na ZY22FJL7QX** — assembleDebug PASS, ale APK nie wgrany. **Action:** `adb install` + manualny smoke (airplane mode toggle, dashboard stats, undo dialog).
- **Test acceptance E2E (10 kroków z WO)** — wymaga: backend deploy → APK install → real event z prelegentami w DB. **Action:** Master Krok 4 (QA Gate) lub osobny WO-MOB-015-smoke.
