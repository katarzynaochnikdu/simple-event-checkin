# WO-MOB-012: Fix rozbieżności liczb check-in między mobile a panelem web (historyczne wydarzenia)

**Data:** 2026-05-20
**Worker:** Debugger (Faza 1 — diagnoza) → Implementer (Faza 2 — fix, po doprecyzowaniu zakresu)
**Stage:** Mobile data sync — correctness gate
**Priorytet:** Wysoki (P1)
**Powiązany bug:** [BUG-MOB-002](../bugs/BUG-MOB-002-checkin-counts-mismatch-historical-events.md)

## Cel

Wyeliminować rozbieżności między aplikacją mobilną a panelem web w (a) liczbie check-inów widocznych w mobile oraz (b) liczbie uczestników ogółem dla wydarzeń. Mobile musi pokazywać aktualny stan z backendu — niezależnie od tego, kto wykonał odznaczenie (web, inna instancja mobile, sync z offline queue) — oraz aktualny zestaw uczestników.

Zgodnie z BUG-MOB-002: dwa potencjalnie niezależne wątki:
- **Wątek A** — mobile nie widzi check-inów spoza tej instancji (Dental: 0 vs 46).
- **Wątek B** — mobile widzi inny (większy) zestaw uczestników niż web (Dental 49 vs 46, Kraków 107 vs 100).

**Wymaganie procesowe:** najpierw **diagnoza** (worker-debugger, read-only) → potwierdzić czy to 1 czy 2 bugi → potem **fix** w zakresie potwierdzonym diagnozą. Jeśli diagnoza pokaże >2 warstwy lub >6 plików — rozbić na WO-MOB-012a + WO-MOB-012b.

## Zakres

### Faza 1 — Diagnoza (read-only)
- `backend/api/mobile.py` — endpointy `/api/mobile/events/:id/participants` + `/api/mobile/events/:id/checkin-stats`
- `backend/pg_storage.py` — `get_participants_for_mobile()` (linia ~23884), `get_checkin_stats()` (linia ~24290), query filtra statusu i `checked_in`
- Mobile Kotlin (`simple-event-checkin/`) — Repository / ViewModel ekranu szczegółów eventu, Room/SQLite layer cache uczestników + check-inów, logika sync (refresh on enter event detail screen)
- Diagnostyczne SQL na produkcji (read-only `SELECT`):
  - `SELECT COUNT(*), status FROM event_participants WHERE event_id = '<dental>' GROUP BY status` — co realnie jest w DB
  - `SELECT COUNT(*) FROM checkin_log WHERE event_id = '<dental>'` — co liczy check-in dla tego eventu
  - Co zwraca `GET /api/mobile/events/<dental>/participants` i `/checkin-stats` na produkcji vs panel web

### Faza 2 — Fix (po doprecyzowaniu)
Na podstawie ustaleń Fazy 1 — minimalny celowany fix w warstwach wskazanych przez diagnozę. Możliwe zakresy:
- (jeśli backend filtr) — `backend/pg_storage.py` query w `get_participants_for_mobile` / `get_checkin_stats`
- (jeśli backend response shape) — `backend/api/mobile.py` mapowanie pól (np. brak `checked_in` w response)
- (jeśli mobile cache) — Kotlin Repository/Room: TTL, force-refresh on enter event detail, sync `checked_in` z backendu
- (jeśli mobile filtruje source) — Kotlin: zmiana źródła licznika z lokalnego `checkin_log`-equivalent na pole `checked_in` z backendu

## Czego NIE ruszać 🛑

- ❌ `pg_storage.py` poza dokładnie wskazanymi funkcjami (`get_participants_for_mobile`, `get_checkin_stats`, ewentualnie funkcja pomocnicza pobierająca uczestników dla eventu) — incydent z masową zamianą Unicode (constraint §9), plik 868KB.
- ❌ **NIE** rób hurtowych zamian Unicode / regex w `pg_storage.py` (constraint §9).
- ❌ **NIE** modyfikuj kontraktu API mobile bez aktualizacji `backend/api/MOBILE_API.md` i — jeśli pojawi się nowe pole — bez sync z mobile DTO.
- ❌ **NIE** tykaj endpointów check-in (`POST /api/mobile/checkin`, `/checkin/sync`) ani `checkin_participant_by_ticket_id()` — race condition safety z `SELECT ... FOR UPDATE` została wprowadzona celowo.
- ❌ **NIE** zmieniaj struktury tabeli `checkin_log` — to wymagałoby migracji SQL (osobny WO).
- ❌ **NIE** dotykaj env vars na Render (constraint §14).
- ❌ **NIE** wprowadzaj testowych endpointów / debug logów z PII na produkcji.

## Pliki startowe

### Faza 1 (diagnoza)
1. `simple-event-checkin/.agents/bugs/BUG-MOB-002-checkin-counts-mismatch-historical-events.md` — pełny opis objawu + hipotezy.
2. `backend/api/mobile.py` — endpointy mobile.
3. `backend/pg_storage.py:23884` (`get_participants_for_mobile`) + `:24290` (`get_checkin_stats`).
4. `backend/api/MOBILE_API.md` — kontrakt API mobile (czy `checked_in` jest w response?).
5. Mobile Kotlin: katalog `simple-event-checkin/feature-event-detail/` (lub równoważny — feature module ekranu szczegółów eventu).
6. `database/full_schema.sql` — schema `event_participants` (jak wygląda pole `checked_in` / `status`) i `checkin_log`.

### Faza 2 (fix) — wyłania się z Fazy 1

## Ryzyko

- **Regresja licznika check-in dla bieżących eventów:** zmiana logiki agregacji może zepsuć obecnie działające eventy (AMOZ Kraków: check-in OK). Mitygacja: testy ręczne na **co najmniej 3 eventach** (Dental historyczny / AMOZ historyczny / nowy testowy event z świeżym check-in).
- **Race / locking na `checkin_log`:** jeśli dotykamy query agregującego z `FOR UPDATE` lub w transakcji — ryzyko deadlocku. Mitygacja: agregacja SELECT bez locków, lock tylko w write path (już jest w `checkin_participant_by_ticket_id`).
- **Wpływ na inne ekrany mobile** (np. ekran listy uczestników, statystyki) korzystające z tego samego cache/endpointa — sprawdzić wszystkich konsumentów `get_participants_for_mobile`.
- **Mobile APK release vs debug:** fix backend wymaga deploy Render (auto z `master`), mobile może wymagać nowego APK (jeśli Faza 2 wpada w Kotlin) lub być pure backend fix.
- **JSONB merge** (constraint §11): jeśli okaże się że trzeba modyfikować `event_participants.data` — bezwzględnie read-modify-write.

## Definition of Done ✅

### Faza 1 — Diagnoza (output: raport diagnozy)
- [ ] Raport diagnozy zapisany jako notatka w WO (sekcja "Notatki diagnostyczne") — co konkretnie zwraca backend dla Dental + Kraków, co pokazuje mobile, gdzie jest rozjazd.
- [ ] Potwierdzenie czy to 1 bug czy 2 niezależne (Wątek A vs Wątek B).
- [ ] Konkretna lista plików do zmiany w Fazie 2 (≤6 plików; jeśli więcej — rozbicie na 2 WO).
- [ ] User akceptuje przejście do Fazy 2 (STOP point).

### Faza 2 — Fix
- [ ] Backend compile OK: `py -m py_compile backend/<plik>.py` (jeśli backend dotknięty).
- [ ] Frontend / mobile typecheck OK: `npx tsc --noEmit` (frontend) / Gradle build (mobile) — jeśli dotknięte.
- [ ] Test akceptacyjny PASS (sekcja niżej).
- [ ] `backend/api/MOBILE_API.md` zaktualizowany, jeśli zmieniono response shape.
- [ ] Constraints `constraints_do_not_break.md` zachowane (zwłaszcza §9 Unicode, §11 JSONB, §3 nie łamać API kontraktu bez doc, §14 nie tykać Render env).
- [ ] Review note w `simple-event-checkin/.agents/work_orders/review_notes/REVIEW-WO-MOB-012.md`.
- [ ] BUG-MOB-002 oznaczony jako RESOLVED w treści pliku + INDEX.md.
- [ ] IMPLEMENTATION_REPORT_WO_MOB_012.md z postmortem (4 pytania).

## Test akceptacyjny 🧪

Przed fixem (baseline — Faza 1):
1. Mobile: otwórz event **Dental Practice Academy Poznań (28.04.2026)** → odczytaj liczniki ODZNACZENI / OCZEKUJĄCY / ŁĄCZNIE.
2. Web: ten sam event → "Uczestnicy on-site" → odczytaj Pewni uczestnicy + CHECK-IN zweryfikowany.
3. Zapisz baseline (oczekiwany rozjazd: mobile 0/49/49 vs web 46/46).

Po fixie (Faza 2):
1. Mobile: ponowne otwarcie eventu Dental → liczniki powinny być **identyczne z panelem web** (46 odznaczonych, suma odpowiadająca rzeczywistemu zestawowi DB).
2. Mobile: AMOZ Kraków → check-in dalej 66=66 (brak regresji), łącznie zgodne z web (100).
3. **Nowy testowy event** (świeżo utworzony): odznacz 2 uczestników z aplikacji + 2 z panelu web → mobile musi pokazać **4 odznaczonych** (nie tylko swoje 2).
4. Mobile: scenariusz offline → odznacz 1 uczestnika w trybie offline → wróć online → sync → mobile + web pokazują tego uczestnika jako odznaczonego.
5. **Network tab / Logcat:** zweryfikować że odpowiedź `GET /api/mobile/events/<id>/participants` zawiera pole `checked_in` (jeśli to droga fixu) **lub** że mobile re-fetch'uje stan przy otwarciu ekranu (nie polega tylko na lokalnym SQLite cache).

> ⚠️ Test akceptacyjny WYMAGA dostępu do produkcyjnego konta operatora + co najmniej 1 historyczny event z rozbieżnością + 1 testowy event. Worker QA wykonuje na zainstalowanym APK (preview build).

## Oczekiwany efekt wizualny 🖼️

- Mobile ekran szczegółów eventu — POSTĘP CHECK-IN i 3 liczniki (ODZNACZENI / OCZEKUJĄCY / ŁĄCZNIE) odpowiadają wartościom z panelu web.
- Dental Practice Academy Poznań: zamiast 0% → **~100%** (46/46 odznaczonych — przy założeniu że łącznie = 46 po dopasowaniu zestawu) lub przynajmniej `<liczba odznaczonych z web>` / `<łącznie z web>`.
- AMOZ Kraków: zamiast 107 łącznie → **100** (zgodnie z web); odznaczeni dalej 66.

## Kontrakt API 🔗

Możliwe zmiany (do potwierdzenia w Fazie 1):
- `GET /api/mobile/events/:id/participants` — może wymagać dodania pola `checked_in: boolean` per uczestnik (jeśli go nie ma) **lub** zmiany filtra `status IN (...)` żeby zgadzał się z panelem web.
- `GET /api/mobile/events/:id/checkin-stats` — może wymagać zmiany source z lokalnego liczenia na agregację po `event_participants.checked_in` lub `checkin_log` (zależnie od źródła prawdy).

Jeśli pole `checked_in` zostanie dodane / zmienione — **MUST** aktualizacja `backend/api/MOBILE_API.md` + jeśli istnieje DTO w mobile Kotlin — ręczna sync (constraint: nie ma `shared/` dla mobile).

## Format zwrotki

### Faza 1
- Raport diagnozy (markdown w samym WO lub osobny plik `WO-MOB-012-diagnosis-notes.md`)
- Lista plików do zmiany w Fazie 2
- Decyzja: 1 WO czy split na 2 (WO-MOB-012a + WO-MOB-012b)

### Faza 2
- Lista zmienionych plików z 1-linijkowym opisem
- Git diff summary
- Screenshot mobile (3 liczniki) + screenshot panelu web (porównanie zgodności)
- Wyniki Network log: odpowiedzi endpointów dla testowego eventu
- Wynik build/compile (py_compile / Gradle)
- IMPLEMENTATION_REPORT_WO_MOB_012.md z postmortem
- Propozycja wpisu do `decision_log.md` (jeśli zmiana kontraktu API lub semantyki licznika check-in)
- Update INDEX.md bugów (BUG-MOB-002 → RESOLVED, link do tego WO)

---

## Notatki diagnostyczne

**Worker:** Debugger (read-only) — 2026-05-20
**Status:** Faza 1 ZAKOŃCZONA — czeka na akceptację Fazy 2.

### TL;DR — potwierdzono **2 niezależne bugi**

**Wątek A (P1, główny) — niespójność źródła prawdy "checked_in" backend↔backend:**
- Web admin liczy check-in jako `(data->>'checked_in') = 'true' OR (data->>'checked_in') = 'True' OR status = 'checked_in'` (`pg_storage.py:9964-9966` + `admin.py:13909, 17061, 17758, 29905`, itd.).
- Mobile endpointy liczą TYLKO `status = 'checked_in'` (`get_mobile_dashboard:24796`, `get_checkin_stats:24301`) lub `status='checked_in' OR attendance_status IN ('checked_in','present')` (`get_participants_for_mobile:23954-23956`), **NIGDY** nie patrząc na `data->>'checked_in'`.
- → Dental: 46 uczestników ma najprawdopodobniej `data->>'checked_in' = true` bez `status='checked_in'` (legacy ścieżka — pre-WO-MOB-008 CSV, Zoho Backstage import, lub stara migracja). Web ich widzi, mobile NIE.
- **Mobile NIE ma własnego "Wątku A" — nie filtruje po `scanned_by`/device.** Mobile po prostu dostaje `checked_in_at = NULL` z backendu → wpisuje NULL do Room → `isCheckedIn = checkedInAt != null` → false. Bug jest **w całości po stronie backendu**.

**Wątek B (P2, drugi rząd) — różny filtr "kto się liczy jako uczestnik":**
- Web "Uczestnicy on-site" → `isOnsiteCertain(p) = attendance_status IN ('confirmed', 'pending')` (`OnsiteParticipantsSubTab.tsx:80-83`).
- Mobile `get_participants_for_mobile` → `o.status NOT IN ('cancelled', 'refunded')` (`pg_storage.py:24026`).
- Różnica: mobile wlicza uczestników z paid orders ale `attendance_status='declined'` (RSVP odmówił), web on-site ich pomija. Stąd Dental 49 vs 46 (3 declined/refused) i Kraków 107 vs 100 (7 declined). Konsekwentnie mobile > web — zgadza się z hipotezą.

### Co konkretnie zwracają endpointy (backend → mobile)

**`GET /api/mobile/events/<id>/participants`** (`mobile.py:788-806` → `pg_storage.py:23884-24036`):
- Filtr: `o.event_id = %s AND o.status NOT IN ('cancelled', 'refunded')`. **Pole `checked_in: bool` NIE jest w response** — mobile wnioskuje status z `checked_in_at != NULL` (`Participant.kt:34`).
- `checked_in_at` = `COALESCE(cl.first_scanned_at, p.updated_at)` **WTEDY GDY** `p.status='checked_in' OR p.attendance_status IN ('checked_in','present')`, w przeciwnym razie `NULL`.

**`GET /api/mobile/events/<id>/checkin-stats`** (`mobile.py:935-943` → `pg_storage.py:24290-24323`):
- `checked_in: COUNT(*) FILTER (WHERE p.status = 'checked_in')` — **TYLKO** po `status`.
- `not_checked_in: COUNT(*) FILTER (WHERE p.status != 'checked_in' AND ticket_number is not null)`.

**`GET /api/mobile/events/<id>/dashboard`** (`mobile.py:1117-1127` → `pg_storage.py:24781+`):
- Dashboardowy `total_registered`, `total_with_qr`, `checked_in` — wszystkie po `status='checked_in'`. Również timeline + topScanners po `p.status='checked_in'`.

### Co konkretnie liczy mobile (źródło)

`DashboardViewModel.kt:118-119`:
```kotlin
val total   = body?.totalRegistered ?: localTotal
val checked = body?.checkedIn       ?: localCheckedIn
```
- **Priorytet 1:** wartość z `GET /events/<id>/dashboard` (gdy nie null).
- **Priorytet 2 (fallback):** lokalny SQLite count: `participantDao.countCheckedInFlow(eventId)` → `WHERE event_id = :eventId AND checked_in_at IS NOT NULL` (`ParticipantDao.kt:45-46`).

`SyncWorker.pullParticipants` (`SyncWorker.kt:124-157`):
- `forceFullPull=true` w `triggerImmediateSync` → `since=null` → `replaceAll(eventId, entities)`. Cache jest świeży po wejściu w event.
- DTO→Entity mapping zachowuje `checked_in_at` 1:1 (`ParticipantMappers.kt:49`).

→ **Mobile JEST poprawnie zsynchronizowany z tym, co serwer zwraca.** Problem leży w tym, **co serwer zwraca dla mobile** — undercount względem web.

### Gdzie jest rozjazd

| Warstwa | Source of truth dla "checked_in" |
|---|---|
| Web admin (`get_checkin_counts_by_orders`, `isOnsiteCertain` + raw `data.checked_in`) | `data->>'checked_in' = true/True` **OR** `status='checked_in'` |
| Backend mobile `get_participants_for_mobile` | `status='checked_in'` **OR** `attendance_status IN ('checked_in','present')` |
| Backend mobile `get_checkin_stats`, `get_mobile_dashboard` | `status='checked_in'` (tylko) |
| Mobile UI (Kotlin) | `checked_in_at != NULL` (zwraca backend powyżej) |

**Drift:** mobile nie patrzy na `data->>'checked_in'`. Jeśli historyczne odznaczenia trafiły tam (np. legacy import / starsza ścieżka admina pre-2026), mobile ich nie widzi.

Hipoteza dlaczego AMOZ Kraków (15.04.2026) działa, a Dental (28.04.2026) nie: AMOZ check-in robiony był z mobile (WO-MOB-008 fix już działał) → ustawiał `status='checked_in'`. Dental check-in robiony był z web admin / CSV import / starszej ścieżki — ustawiała tylko `data.checked_in`. **Bez prod DB SELECT nie potwierdzimy w 100%**, ale wzorzec spójny.

### Potwierdzenie / odrzucenie hipotez z BUG-MOB-002

| # | Hipoteza | Werdykt |
|---|---|---|
| A1 | Mobile pobiera tylko własne rekordy z `checkin_log` (`scanned_by` filter) | ❌ **Odrzucona** — mobile dostaje `checked_in_at` od backendu, który NIE filtruje po `scanned_by` (LEFT JOIN LATERAL po `participant_id` only). |
| A2 | Mobile ignoruje `checked_in` z response | ❌ **Odrzucona** — backend NIE zwraca `checked_in` (tylko `checked_in_at`); mobile czyta `checked_in_at` poprawnie. |
| A3 | Stale cache SQLite bez TTL | ❌ **Odrzucona** — `triggerImmediateSync` w `loadDashboard` z `forceFullPull=true` + `replaceAll`. Cache jest świeży. |
| **A4 (nowa)** | **Backend `get_participants_for_mobile` / `_stats` / `_dashboard` ignorują `data->>'checked_in'` — desynchronizacja źródła prawdy między endpointami web a mobile** | ✅ **POTWIERDZONA** |
| B4 | Stale cache listy uczestników w SQLite | ❌ Odrzucona (z tych samych powodów co A3). |
| **B5** | **Różne filtry statusu mobile vs web on-site (`o.status` vs `attendance_status`)** | ✅ **POTWIERDZONA** |
| C6 | Offline queue nie zsynchronizowana | ❌ Niezwiązany. |
| C7 | Race po WO-MOB-008 / scache'owane odpowiedzi | ❌ Niezwiązany (sync force-full). |

### Lista plików do zmiany w Fazie 2 (≤6)

**Decyzja:** **JEDEN WO** wystarczy. 3 pliki backendu, 0 plików mobile (mobile DTO ani Room nie wymagają zmian — backend self-fix wystarcza). APK build **nie jest potrzebny** — pure backend fix → auto-deploy Render z `master`.

| # | Plik | Zmiana | Linie |
|---|---|---|---|
| 1 | `backend/pg_storage.py` | `get_participants_for_mobile`: rozszerzyć `CASE WHEN` o `data->>'checked_in' = 'true' OR data->>'checked_in' = 'True'` — spójność z web admin. | 23953-23958 |
| 2 | `backend/pg_storage.py` | `get_checkin_stats`: zmienić filtr `checked_in` na `(p.status='checked_in' OR (p.data->>'checked_in') IN ('true','True'))`. | 24299-24309 |
| 3 | `backend/pg_storage.py` | `get_mobile_dashboard`: ten sam filtr w 3 query (aggregate counts:24796, by_ticket_class:24831, timeline:24857, top_scanners:24878). Najmniej inwazyjnie: użyć helpera `CHECKED_IN_PREDICATE` jako stałej lub inline. | 24792-24884 |
| 4 | `backend/api/MOBILE_API.md` | Doc update — udokumentować że `checked_in_at` reflectuje OR dwóch źródeł (`status` + `data->>'checked_in'`). | sekcja `/participants` + `/checkin-stats` |
| 5 (opcjonalnie) | `backend/pg_storage.py` | **Wątek B fix** — zmienić filtr `get_participants_for_mobile` z `o.status NOT IN ('cancelled','refunded')` na `p.attendance_status IN ('confirmed','pending')` żeby zgadzało się z web on-site. ⚠️ **RYZYKO REGRESJI** — może odfiltrować uczestników których walk-in scenariusz wymaga (RSVP nie wysłany). **Rekomendacja: NIE robić w tym WO — split jako WO-MOB-012b lub osobny WO po decyzji produktowej.** | 24026 |

**Decyzja split:**
- **WO-MOB-012 (ten WO) — Faza 2:** TYLKO Wątek A (pliki #1-4). To naprawia Dental 0→46 i zapewnia spójność check-in counts.
- **WO-MOB-012b (nowy WO, po decyzji produktowej):** Wątek B — wymaga rozmowy z biznesem czy "RSVP declined" ma być widoczny w mobile (do edge case check-in) czy ukryty (jak web on-site). To **nie jest bug** — to celowa różnica semantyki, którą trzeba świadomie ujednolicić.

### Rekomendowane podejście fix

**Backend (Wątek A):** dodać przewidywalny predykat `is_checked_in` we wszystkich 3 funkcjach mobile:
```sql
(p.status = 'checked_in'
 OR (p.data->>'checked_in') IN ('true', 'True')
 OR p.attendance_status IN ('checked_in', 'present'))
```
Spójne z istniejącym `get_checkin_counts_by_orders` (admin). Zero migracji DB, zero zmiany kontraktu API (`checked_in_at` semantycznie pozostaje "kiedy zaczęto", po prostu NULL znika tam gdzie wcześniej był nieprawidłowo).

**Mobile:** ZERO zmian. APK release nie wymagany.

**Test akceptacyjny:**
1. Backend `py -m py_compile pg_storage.py` PASS.
2. Po deploy Render: `curl GET /api/mobile/events/<dental_id>/checkin-stats` z tokenem operatora → `checked_in: 46` (było 0).
3. Mobile **bez** nowego APK: zamknij i otwórz Dental → POSTĘP ~100%, ODZNACZENI 46.
4. AMOZ Kraków: brak regresji — check-in 66/100 dalej widoczny.
5. Świeży event: scan z mobile → status update widoczny też z web (brak regresji w write path).

### ✅ Prod SELECT-y wykonane (2026-05-20, master agent przez `backend/.env` DATABASE_URL)

**Dental Practice Academy Poznań** (`event_id=24311000000883034`):
| Metryka | Wartość |
|---|---|
| total participants | **53** |
| by status | `emailed=47, pending=4, registered=1, cancelled=1` (ZERO w `status='checked_in'`) |
| by attendance_status | `confirmed=46, cancelled=6, registered=1` |
| by data.checked_in | `'true'=46, NULL=7` |
| web-style OR predykat | **46** ✅ |
| mobile-style (status only) | **0** ❌ ← źródło buga |
| checkin_log rows | **17** (z 46 odznaczonych — log niekompletny!) |

**AMOZ Connect Kraków** (`event_id=24311000000909074`):
| Metryka | Wartość |
|---|---|
| total participants | **123** |
| by status | `checked_in=66, emailed=34, registered=14, pending=9` |
| by attendance_status | `confirmed=78, cancelled=23, pending=22` |
| by data.checked_in | `'true'=66, NULL=57` |
| web OR / mobile (status only) | **66 / 66** ✅ (oba pola ustawione równolegle) |
| checkin_log | 50 |

### Konkluzje z prod SELECT-ów

1. **Wątek A 100% potwierdzony:** Dental ma 46 uczestników z `data.checked_in='true'` ale BEZ `status='checked_in'`. Predykat OR daje 46, mobile-only-status daje 0. Fix backendowy trafia w sedno.
2. **Wątek B potwierdzony:** Dental mobile 49 vs web 46 (różny filtr `status` vs `attendance_status`); AMOZ mobile 107 vs web 100 (web odsiewa `attendance_status='cancelled'`=23). → **osobny WO-MOB-013** po decyzji biznesowej.
3. **Bonus odkrycie — checkin_log niekompletny** (Dental: 17/46, AMOZ: 50/66). Web admin check-in NIE pisze do `checkin_log`, tylko ustawia `data.checked_in`. **Poza zakresem fixu** (read aggregat działa OR), ale do `known_gotchas.md`.
4. **Pokrewny wzorzec poza zakresem:** `mobile.py:1176/1846/1921/1927` (MyMentees/event_orders) używają `p.status='checked_in'` → ten sam undercount. **Propozycja WO-MOB-014.**

### Historyczne open questions (zostawione dla kontekstu)

1. ~~**Brak dostępu do produkcyjnego psql**~~ — ✅ ROZWIĄZANE, SELECT-y wykonane przez `backend/.env`. **Hipoteza Wątek A potwierdzona empirycznie.** Pierwotnie debugger nie próbował połączenia DB; user wskazał `DATABASE_URL` w env.
   ```sql
   SELECT
     COUNT(*) FILTER (WHERE status='checked_in') AS by_status,
     COUNT(*) FILTER (WHERE (data->>'checked_in') IN ('true','True')) AS by_data,
     COUNT(*) FILTER (WHERE status='checked_in' AND (data->>'checked_in') NOT IN ('true','True')) AS status_only,
     COUNT(*) FILTER (WHERE status<>'checked_in' AND (data->>'checked_in') IN ('true','True')) AS data_only
   FROM participants p JOIN orders o ON p.event_order_id=o.event_order_id
   WHERE o.event_id='<dental_event_id>' AND o.status NOT IN ('cancelled','refunded');
   ```
   Spodziewane: `data_only ≈ 46`, `by_status = 0`.
2. **Brak dostępu do tokenu operatora** — nie wywołałem `GET /api/mobile/events/<dental_id>/participants` empirycznie. Diagnoza w 100% z czytania kodu.
3. **Jak powstały Dental odznaczenia** — czy CSV import (który ustawia oba), czy starsza ścieżka tylko `data.checked_in`. Wpływa na to czy istnieje też niezaadresowany "legacy writer" bug.

