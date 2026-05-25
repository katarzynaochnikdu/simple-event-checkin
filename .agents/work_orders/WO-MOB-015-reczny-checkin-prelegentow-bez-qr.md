# WO-MOB-015: Ręczny check-in prelegentów (bez QR) w mobile

**Data:** 2026-05-25
**Scope:** mobile (simple-event-checkin)
**Worker:** [do uzupełnienia przez Mastera]
**Stage:** [placeholder — np. "Mobile: Speaker Attendance MVP"]
**Priorytet:** [placeholder — Normalny / Wysoki — do potwierdzenia]

---

## Status

**Otwarty — decyzje podjęte 2026-05-25, gotowy do dispatchu po akceptacji rozbicia na sub-WO.**
NIE dispatchowany, NIE rozpoczęty. Sekcja "Otwarte pytania" → zastąpiona przez "Decyzje przyjęte" (poniżej).

---

## Cel

Umożliwić mobile-użytkownikowi (admin / koordynator wydarzenia) **manualne** oznaczenie prelegenta jako "przybył" w aplikacji checkin — analogicznie do uczestników, ale **bez QR codes**. Prelegenci nie mają biletów (`backstage_ticket_id`), więc identyfikacja odbywa się po nazwisku z listy (search + tap), nie przez skan.

Cytat user'a:
> "Przydałby się na listy Prelegenci — żeby manualnie już nawet bez kodów po prostu odhaczając w aplikacji po nazwisku kto przybył; czyli funkcjonalność taka jak uczestników ale bez kodów — znajduje po nazwisku i odhaczam że jest."

---

## Zakres (po decyzjach 2026-05-25)

**Backend (`backend/`):**
- `backend/api/mobile.py` — 3 nowe endpointy:
  - `POST /api/mobile/events/<event_id>/speakers/<speaker_id>/checkin` (pojedynczy check-in lub undo)
  - `POST /api/mobile/speakers/checkin/sync` (batch offline sync)
  - `GET /api/mobile/events/<event_id>/speakers/checkin-stats`
- `backend/pg_storage.py` — funkcje:
  - `checkin_speaker(event_id, speaker_id, scanned_by, device_id, scanned_at, action='check-in')` — z `SELECT ... FOR UPDATE` (race safety) + idempotencja
  - `batch_checkin_speakers_sync(entries)` — dedup po (event_id, speaker_id, action, scanned_at)
  - `get_speaker_checkin_stats(event_id)` — `{ total, attended, attended_speaker_ids[] }`
  - **Design jako shared (mobile + desktop-ready)** — funkcje wywoływalne też z `backend/api/admin.py` w przyszłym desktop WO

**DB (`database/migrations/`):**
- Nowa migracja `0033_speaker_checkin_log.sql` (numer kolejny po 0032 — _backfill_zoho_conflict_):
  - `CREATE TABLE speaker_checkin_log` analogiczny do `checkin_log`: `id BIGINT PK`, `speaker_id BIGINT NOT NULL FK→event_speakers(id) ON DELETE CASCADE`, `event_id text NOT NULL`, `scanned_by text NOT NULL`, `scanned_at timestamptz NOT NULL`, `device_id text`, `sync_status text DEFAULT 'synced'`, `action text NOT NULL DEFAULT 'check-in'` (precedens 0004), `created_at timestamptz DEFAULT now()`
  - Indexes: `(speaker_id)`, `(event_id)`, `(scanned_at)`, `(event_id, action)`
  - Idempotent (`CREATE TABLE IF NOT EXISTS`, `IF NOT EXISTS` na indeksach), `INSERT INTO schema_migrations`
  - ROLLBACK block w komentarzu
- Update `database/full_schema.sql` (sync)

**Mobile (`simple-event-checkin/`) — z PEŁNYM offline queue:**
- `features/feature-speakers/presentation/screen/SpeakersScreen.kt`:
  - Checkbox po prawej stronie każdego itemu (Material3 `Checkbox` lub custom)
  - Header: licznik "Obecni: K/N" + filter chip "Tylko nieobecni"
  - Tap checkbox: optimistic UI → call repo → success badge zielony / fail rollback + snackbar
  - Tap ponowny (już odhaczony): confirm dialog "Cofnąć check-in dla {imię nazwisko}?" → undo
- `features/feature-speakers/presentation/screen/SpeakerDetailScreen.kt`:
  - Przycisk full-width "Oznacz jako obecny" → po check-in zmienia się w "Obecny od HH:MM" + secondary "Cofnij"
- `features/feature-speakers/presentation/viewmodel/SpeakersViewModel.kt`:
  - State: `checkedInSpeakerIds: Set<String>`, `pendingSpeakerIds: Set<String>` (in-flight optimistic)
  - Akcje: `markAttended(speakerId)`, `undoAttended(speakerId)`, `refreshStats()`
- `features/feature-speakers/data/repository/SpeakerCheckinRepository.kt` (NEW)
- `core/core-network/.../MobileApiService.kt` — 3 nowe @POST/@GET endpoints
- `core/core-network/.../dto/SpeakerCheckinDtos.kt` (NEW) — `SpeakerCheckinRequestDto`, `SpeakerCheckinResponseDto`, `SpeakerCheckinStatsDto`, `SpeakerCheckinSyncBatchDto`
- `core/core-database/.../entity/SpeakerCheckinEntity.kt` (NEW) — Room entity, fields: `id`, `speakerId`, `eventId`, `scannedAt`, `deviceId`, `action`, `syncStatus` ('pending'|'synced'|'failed')
- `core/core-database/.../dao/SpeakerCheckinDao.kt` (NEW) — insert / queryPending / updateStatus / deleteSyncedOlderThan
- `core/core-database/AppDatabase.kt` — bump version + migracja (dodać `SpeakerCheckinEntity`)
- Sync worker (analogia do participant sync): `core/core-data/.../sync/SpeakerCheckinSyncWorker.kt` lub rozszerzyć istniejący scheduler
- `feature-dashboard/.../DashboardScreen.kt` — dodać 1 wiersz "Prelegenci: K/N" obok analogicznych statsów uczestników

---

## Czego NIE ruszać 🛑

- Istniejącej logiki QR check-in dla **participantów** (`checkin_log` + `checkin_participant_by_ticket_id()` z `FOR UPDATE` + flow `ScanResultOverlay`)
- App-level routing (`app/.../navigation/Screen.kt` — speakers routes już istnieją)
- Auth flow (JWT mobile)
- Desktop admin panel (out-of-scope; ewentualny follow-up jako osobne WO)

---

## Pliki startowe

Backend:
- `backend/api/mobile.py:1388` — `GET /events/<event_id>/speakers` (kontekst speakers endpoint)
- `backend/api/mobile.py:1418` — `GET /events/<event_id>/speakers/<speaker_id>` (detail)
- `backend/api/mobile.py` — sekcja participants checkin (`POST /api/mobile/checkin`, `POST /api/mobile/checkin/sync`, `GET /events/<id>/checkin-stats`) jako wzorzec
- `backend/pg_storage.py` — `get_speakers_for_event()`, `checkin_participant_by_ticket_id()`, `batch_checkin_sync()`, `get_checkin_stats()` (wzorzec)
- `database/migrations/0002_checkin_log.sql` — wzorzec schematu

Mobile:
- `simple-event-checkin/features/feature-speakers/presentation/screen/SpeakersScreen.kt`
- `simple-event-checkin/features/feature-speakers/presentation/screen/SpeakerDetailScreen.kt`
- `simple-event-checkin/features/feature-speakers/presentation/viewmodel/SpeakersViewModel.kt`
- `simple-event-checkin/features/feature-speakers/presentation/viewmodel/SpeakerDetailViewModel.kt`
- `simple-event-checkin/features/feature-speakers/di/SpeakersModule.kt`
- `simple-event-checkin/core/core-network/.../MobileApiService.kt`
- `simple-event-checkin/core/core-network/.../dto/ResponseDtos.kt` (SpeakerDto)
- `simple-event-checkin/core/core-model/.../Speaker.kt`
- Participants screen + repository jako wzorzec UX-u

---

## Ryzyko

- **Drift schematu DB:** jeśli dodajemy `subject_type` do istniejącej `checkin_log` — wymaga update wszystkich miejsc czytających participants checkins (filtry `WHERE subject_type='participant'`); ryzyko regresji w istniejących statach uczestników.
- **Race condition:** dwa urządzenia odhaczające tego samego prelegenta równocześnie — wymagane `SELECT … FOR UPDATE` lub `ON CONFLICT DO NOTHING` jak w participants.
- **Offline divergence:** jeśli implementujemy offline queue — konieczna idempotencja po stronie backendu (deduplikacja w batch sync).
- **UX zamieszanie:** prelegenci NIE mają biletów, więc brak QR; ekran nie powinien sugerować skanowania. Brak rozróżnienia może wprowadzać w błąd koordynatorów.
- **Odpowiedzialność za stats:** czy "Prelegenci obecni: X/N" liczone niezależnie od participantów? Tak — osobny licznik (a może wspólny "obecni X osób"?). Decyzja UX.

---

## Definition of Done ✅

- [ ] Endpoint backend zaimplementowany + przetestowany (curl / pytest)
- [ ] Migracja DB aplikowana lokalnie i opisana w `decision_log.md` (decyzja: tabela osobna vs unified)
- [ ] Mobile UI: tap "Odhacz" działa w SpeakersScreen + SpeakerDetailScreen
- [ ] Persistence: po restarcie aplikacji + pull-to-refresh status zachowany
- [ ] (jeśli w zakresie) Offline queue: action queued lokalnie → reconnect → auto-sync
- [ ] Stats card pokazuje "Prelegenci obecni: X/N"
- [ ] Race condition test — dwa równoczesne check-in na tego samego prelegenta = jeden zapis, drugi `already_checked_in`
- [ ] Build mobile przechodzi (`./gradlew assembleDebug`)
- [ ] Backend test akceptacyjny zielony
- [ ] Review note w `simple-event-checkin/.agents/work_orders/review_notes/`

---

## Test akceptacyjny 🧪

1. Backend ready, mobile build zainstalowany na urządzeniu testowym.
2. Login do mobile jako admin → wybierz event z prelegentami → tap zakładka "Prelegenci".
3. Lista pokazuje N prelegentów z search bar (już istnieje) + nowy element UI po prawej (checkbox / przycisk "Odhacz").
4. Wpisz fragment nazwiska w search → lista filtruje się (istniejąca funkcja).
5. Tap "Odhacz" przy wybranym prelegencie → status zmienia się na "Przybył" (zielony badge / checkmark).
   - Network: `POST /api/mobile/events/<event_id>/speakers/<speaker_id>/checkin` → 200 `{ "speaker_id": ..., "attended_at": "...", "status": "ok" }`
6. Tap drugi raz przy tym samym prelegencie → response `status: "already_checked_in"` (idempotencja); UI bez zmian lub komunikat.
7. Restart aplikacji → wróć do listy prelegentów → checked-in speakers zachowują badge.
8. Pull-to-refresh → status nadal zgodny z backendem.
9. (jeśli offline w zakresie) Wyłącz Wi-Fi → odhacz prelegenta → status optimistic "queued"; włącz Wi-Fi → auto-sync; weryfikacja w backend.
10. Otwórz ekran eventu → stats: "Prelegenci obecni: K/N" (gdzie K = liczba odhaczonych).

---

## Oczekiwany efekt wizualny 🖼️

- W `SpeakersScreen.kt` każdy item listy ma po prawej stronie:
  - **Przed check-in:** przycisk / ikona "Odhacz" (np. outline checkbox) — analogicznie do listy uczestników (bez kontekstu QR scan)
  - **Po check-in:** zielony badge "✓ Obecny" lub filled checkbox + ewentualnie timestamp "12:34"
- W `SpeakerDetailScreen.kt`:
  - Przycisk full-width "Oznacz jako obecny" (analogiczny do participant detail)
  - Po check-in: przycisk zmienia się na status "Obecny od 12:34" (disabled lub z opcją "Cofnij" — patrz "Otwarte pytania")
- Stats card na ekranie eventu:
  - Sekcja "Prelegenci: K/N obecnych" obok analogicznej dla participantów

---

## Kontrakt API 🔗

Propozycja (do potwierdzenia w WO):

**Pojedynczy check-in:**
```
POST /api/mobile/events/<event_id>/speakers/<speaker_id>/checkin
Headers: Authorization: Bearer <jwt>
Body: { "device_id": "<uuid>", "scanned_at": "<iso8601>" }   (opcjonalne dla offline)
Response 200: { "speaker_id": <int>, "event_id": <int>, "attended_at": "<iso8601>", "status": "ok" | "already_checked_in", "scanned_by": <user_id> }
Response 404: speaker not in event
Response 401: invalid token
```

**Batch sync offline (jeśli w zakresie):**
```
POST /api/mobile/speakers/checkin/sync
Body: { "entries": [{ "event_id": ..., "speaker_id": ..., "scanned_at": ..., "device_id": ... }, ...] }
Response 200: { "synced": N, "duplicates": M, "errors": [...] }
```

**Stats:**
```
GET /api/mobile/events/<event_id>/speakers/checkin-stats
Response: { "event_id": ..., "total": N, "attended": K, "attended_speaker_ids": [...] }
```

> Alternatywnie: rozszerzyć istniejące endpointy participants o `subject_type` parameter — patrz "Otwarte pytania".

---

## Sizing 🔴 — 1 WO (user decision 2026-05-25: accept risk, no split)

- **Backend:** 3 pliki (`api/mobile.py`, `pg_storage.py`, `migrations/0033_*.sql`) + `full_schema.sql` sync
- **Mobile:** ~10 plików (Screen, DetailScreen, ViewModel, Repository, ApiService, 4 DTO, Room Entity, DAO, AppDatabase migration, SyncWorker, DashboardScreen edit)
- **DB:** 1 migracja Postgres + 1 migracja Room (mobile lokalna)
- **Rozmiar:** **🔴 duży** — ~13 plików, 3 warstwy, NOWA end-to-end funkcjonalność z pełnym offline queue (decyzja user'a 2026-05-25).

**Rozbicie (rekomendacja przyjęta):**

| Sub-WO | Zakres | Pliki | Definition of Done |
|---|---|---|---|
| **WO-MOB-015a** | Backend + DB Postgres (shared design dla mobile + desktop) | `backend/api/mobile.py`, `backend/pg_storage.py`, `database/migrations/0033_*.sql`, `database/full_schema.sql` | Curl PASS na 3 endpointach (POST checkin, POST batch sync, GET stats), race condition test (2× równoczesny POST → 1 row + `already_checked_in`), migration applied lokalnie, py_compile PASS |
| **WO-MOB-015b** | Mobile UI ONLINE-only (Screen + ViewModel + Repo + Network + DTO) — zależy od 015a | `SpeakersScreen.kt`, `SpeakerDetailScreen.kt`, `SpeakersViewModel.kt`, `SpeakerCheckinRepository.kt`, `MobileApiService.kt`, 4× DTO, `DashboardScreen.kt` (stats row) | `assembleDebug` PASS, manualny smoke test: tap odhacz → zielony badge → refresh zachowuje stan, undo dialog działa, stats widoczne w 2 miejscach |
| **WO-MOB-015c** | Mobile Offline queue (Room entity + DAO + AppDatabase migration + SyncWorker) — zależy od 015b | `SpeakerCheckinEntity.kt`, `SpeakerCheckinDao.kt`, `AppDatabase.kt`, `SpeakerCheckinSyncWorker.kt` | Airplane mode → tap odhacz → `pending` w lokalnym DB + optimistic UI → włącz wifi → SyncWorker wysyła batch → status `synced`, retry dla failed |

**Procedura (finalna):** **1 WO, dispatch do `worker-implementer` z pełnym zakresem.** User explicit accept risk (2026-05-25):
> "Nie rozbijaj → zostaw jako 1 WO i odpal /master WO-MOB-015 (worker-implementer dostanie cały zakres jednym dispatch'em — większe ryzyko regresji, mniej overhead'u meta)."

**Implementer ma wykonać w kolejności faz (logiczna sekwencja, ale 1 commit/PR):**

**Faza A — Backend + DB Postgres (fundament):**
- Migracja `0033_speaker_checkin_log.sql` (CREATE TABLE + indexes + schema_migrations INSERT, idempotent, rollback comment)
- `database/full_schema.sql` sync
- `backend/pg_storage.py` — 3 funkcje (`checkin_speaker`, `batch_checkin_speakers_sync`, `get_speaker_checkin_stats`) z `SELECT ... FOR UPDATE` (race safety), shared-design dla desktop reuse
- `backend/api/mobile.py` — 3 endpointy (POST checkin, POST batch sync, GET stats) z auth + event-scope check (precedens `_mobile_user_has_event_access` z WO-SEC-002)
- Inline test: `py -m py_compile` + curl smoke (jeśli lokalny Flask up)

**Faza B — Mobile online-only UI (zależne od A):**
- `core/core-network/.../dto/SpeakerCheckinDtos.kt` (4 DTO)
- `core/core-network/.../MobileApiService.kt` (3 endpointy Retrofit)
- `features/feature-speakers/data/repository/SpeakerCheckinRepository.kt` (NEW)
- `features/feature-speakers/presentation/viewmodel/SpeakersViewModel.kt` (state + akcje markAttended/undoAttended/refreshStats)
- `features/feature-speakers/presentation/screen/SpeakersScreen.kt` (checkbox + badge + header licznik + filter chip)
- `features/feature-speakers/presentation/screen/SpeakerDetailScreen.kt` (pełnoekranowy przycisk + undo dialog)
- `features/feature-dashboard/.../DashboardScreen.kt` (1 wiersz "Prelegenci: K/N")

**Faza C — Mobile offline queue (zależne od B):**
- `core/core-database/.../entity/SpeakerCheckinEntity.kt` (NEW Room entity, fields per WO Zakres)
- `core/core-database/.../dao/SpeakerCheckinDao.kt` (insert / queryPending / updateStatus / deleteSyncedOlderThan)
- `core/core-database/AppDatabase.kt` — bump version + dodać entity do `@Database(entities=[...])` + Migration object
- `core/core-data/.../sync/SpeakerCheckinSyncWorker.kt` — analogia do istniejącego participant sync (WorkManager periodic + on-reconnect trigger)
- `SpeakerCheckinRepository.kt` (refactor z B) — fallback do lokalnego DB gdy offline, sync_status update na success

**Smoke testy po implementacji (każda faza):**
- A: `py -m py_compile` PASS, curl 3 endpointy (lokalny lub stub), race condition test (2× równoczesny POST)
- B: `./gradlew assembleDebug` PASS, APK install na ZY22FJL7QX (precedens z WO-MOB-007), manualny smoke: tap odhacz, undo, dashboard stats
- C: airplane mode toggle, weryfikacja lokalnego DB queue → reconnect → SyncWorker pushes batch

**Gates po implementacji (Master Krok 4 + 4.5):**
- QA (manualny + curl + grep DoD)
- Security (WO-SEC-002 precedens event-scope + nowy endpoint surface)
- Contract Sync (3× api-types.ts — speaker_checkin shapes nowe)
- Migration Guard (`0033_*.sql` — JSONB N/A, reversibility, idempotency)

---

## Decyzje przyjęte (2026-05-25)

1. **Schemat DB:** ✅ **Osobna tabela `speaker_checkin_log`** (NIE unified z `subject_type`).
   - Powód: risk isolation — `checkin_log.participant_id BIGINT NOT NULL FK CASCADE` to mission-critical flow; unified wymagałoby DROP NOT NULL + nullable speaker_id FK + CHECK constraint + refactor wszystkich queries. Osobna tabela = CREATE TABLE only, niezależny code path.
   - Migracja: `0033_speaker_checkin_log.sql` (numer kolejny po 0032).

2. **Offline queue:** ✅ **PEŁNY offline jak participants** (z Room entity + sync worker).
   - Powód: spójność UX z uczestnikami; eventy mogą mieć słabe WiFi; infrastruktura już istnieje w `core-database`.
   - Trade-off zaakceptowany: ~+4 pliki mobile, scope 🔴 duży — uzasadnia rozbicie na sub-WO (015c).

3. **Undo check-in:** ✅ **Tak — ponowny tap na checkboxie + confirm dialog.**
   - UX: tap → check-in (zielony badge), tap ponownie → "Cofnąć check-in dla {imię}?" → undo (insert row z `action='check-out'` LUB DELETE — final decision podczas implementacji; precedens `action` column z 0004).
   - WO-MOB-010 (usunięcie "Cofnij wejście" dla participantów) **nie dotyczy** — tamten był QR scan overlay'em, tu jest lista z manualnym tapem.

4. **Stats prelegentów:** ✅ **Dashboard eventu + ekran Prelegenci.**
   - Dashboard: 1 nowy wiersz "Prelegenci: K/N" obok "Uczestnicy: X/Y".
   - Ekran Prelegenci: header z licznikiem + filter chip "Tylko nieobecni" (przyspiesza odhaczanie pod koniec dnia).

5. **Desktop admin panel:** ✅ **Out-of-scope dla WO-MOB-015**, ale:
   - Backend funkcje w `pg_storage.py` zaprojektowane jako **shared (mobile + desktop-ready)** — `get_speaker_checkin_stats()`, `checkin_speaker()` reuse'owalne z `backend/api/admin.py`.
   - Endpoint response shape: `speaker_id`, `attended_at`, `marked_by` — kompatybilny z desktop UI.
   - Desktop view → osobna IDEA (do utworzenia przez `/idea` po akceptacji niniejszego WO).

## Pytania techniczne do weryfikacji w 1. kroku worker'a

6. **Identyfikacja prelegenta:** czy `speaker_id` (PK z DB) jest stabilny i zawsze zwracany z `get_speakers_for_event()`? — `worker-research` zweryfikuje przed implementacją WO-MOB-015a.
7. **Auth/uprawnienia:** decyzja: **te same uprawnienia co participants checkin** (każdy mobile-zalogowany admin/koordynator). Brak nowych roli/permission group.
8. **Idempotencja pod offline batch sync:** dedup po `(event_id, speaker_id, action, scanned_at)` — implementacja w `batch_checkin_speakers_sync()`.

---

## Definition of Ready — 7-punktowa checklista (Master Krok 2.1)

- [x] **Cel jasny** — manualne oznaczanie prelegentów jako obecnych, bez QR, po nazwisku.
- [x] **Zakres z konkretnymi plikami** — wymienione w sekcji "Zakres" (wstępna lista; finalna po decyzji o schemacie DB).
- [x] **Czego nie ruszać** — wymienione (participants QR flow, routing, auth).
- [x] **Test akceptacyjny** — 10-krokowy scenariusz.
- [x] **Oczekiwany efekt wizualny** — opisany (badge/checkbox + stats card).
- [x] **Kontrakt API** — propozycja 3 endpointów z response shapes (do potwierdzenia).
- [x] **Pliki startowe** — wymienione (backend + mobile).

**DoR: 7/7 wypełnione** (z zastrzeżeniem otwartych pytań — zakres i kontrakt API mogą się zawęzić po decyzjach).

---

## Format zwrotki (dla worker'a)

- Lista zmienionych plików z jednolinijkowym opisem
- Git diff summary
- Screenshot mobile listy Prelegenci po check-in (zielony badge)
- Curl response dla POST checkin (status: ok + already_checked_in)
- Wynik `./gradlew assembleDebug` + backend test akceptacyjny
- Propozycja wpisu do `decision_log.md` dotycząca schematu DB (osobna tabela vs unified)
