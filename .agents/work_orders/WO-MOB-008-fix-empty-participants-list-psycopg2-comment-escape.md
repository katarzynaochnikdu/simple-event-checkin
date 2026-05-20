# WO-MOB-008: Fix `get_participants_for_mobile` — escape `%` w SQL komentarzu (psycopg2 IndexError)

**Data:** 2026-05-19
**Scope:** mobile (simple-event-checkin) + backend (pg_storage.py)
**Worker:** worker-debugger (planned)
**Stage:** bugfix
**Priorytet:** **P1 — core feature broken**, mobile lista uczestników niedostępna dla wszystkich eventów na produkcji
**Powiązany bug:** [BUG-MOB-001](../bugs/BUG-MOB-001-uczestnicy-lista-pusta-mimo-stats-93.md)
**Regression introduced by:** WO-MOB-002 (komentarz LATERAL JOIN dla rsvp_responses + mail_log)

---

## Cel

Naprawić ukryty bug w `backend/pg_storage.py` funkcji `get_participants_for_mobile`, który powoduje że **mobile lista uczestników jest pusta na wszystkich eventach** (dashboard działa — używa innej funkcji). Bug jest błędem escapingu pojedynczego znaku `%` w komentarzu SQL — psycopg2 traktuje to jako format spec i rzuca `IndexError: list index out of range`. Funkcja ma `except Exception: return []` więc mobile dostaje "pusta listę 200 OK" bez sygnału błędu.

## Zakres

Worker MA PRAWO modyfikować:
- `backend/pg_storage.py:24005` — **JEDNA linia komentarza SQL**: `'rsvp_%'` → `'rsvp_%%'` (escape psycopg2)

## Czego NIE ruszać 🛑

- Reszta `get_participants_for_mobile` (linia 23884-24037) — funkcja jest poprawna, jedynie komentarz wymaga escape'u.
- Mobile Android codebase (Kotlin) — bug jest 100% po stronie backendu, mobile reaguje poprawnie na pustą listę.
- Inne funkcje w `pg_storage.py` — skan pokazał że to JEDYNA z 512 cur.execute z bad `%` (zero false positive risk).
- Migracje DB — N/A (czysto warstwa odczytu, bez zmiany schematu).
- API contract — N/A (brak zmiany shape response, jedynie fix runtime crash).

## Pliki startowe

- `backend/pg_storage.py:24005` — linia do edycji (komentarz w środku f-string SQL block funkcji `get_participants_for_mobile`)
- `backend/pg_storage.py:24014` — referencyjny wzorzec: `LIKE 'rsvp_%%'` (już poprawnie escaped — kanon)
- Render logs (stopka-api `srv-d61j4bogjchc73fkfiug`) — potwierdzenie błędu

## Diagnostyka — co już zostało zweryfikowane

### 1. Production logs (Render `MD_Order_portal_backend`)

```
2026-05-19 11:35:49 [DB] get_participants_for_mobile error: list index out of range
2026-05-19 11:35:50 [DB] get_participants_for_mobile error: list index out of range
... (dziesiątki wystąpień, od 11:35 do 18:07 ostatnio)
2026-05-19 18:07:38 [DB] get_participants_for_mobile error: list index out of range
```

### 2. Analiza f-string SQL — count `%`:

```
%s placeholders: 1 (linia 24025: WHERE o.event_id = %s)
Total % chars: 4
  L24005: -- ... template_key LIKE 'rsvp_%'         ← BAD (single %)
  L24014:    ml.template_key LIKE 'rsvp_%%'        ← OK (escaped)
  L24025: WHERE o.event_id = %s                    ← OK (placeholder)
```

Mechanizm crash: psycopg2 NIE pomija SQL komentarzy przy parsowaniu format spec. `%` w komentarzu na L24005 → próba pobrania kolejnego parametru → `IndexError`.

### 3. Pełen scan `pg_storage.py`:

```
Total cur.execute calls with triple-quoted strings: 512
Functions with bad %: 1 (tylko get_participants_for_mobile)
```

Jedyna funkcja z tym bugiem — zero ryzyka rozprzestrzenienia, zero false positive.

### 4. Dlaczego dashboard pokazuje 93 a lista 0?

| Endpoint | Funkcja backend | Status |
|---|---|---|
| `GET /api/mobile/events/:id/dashboard` | `get_mobile_dashboard()` | ✅ działa, zwraca counts |
| `GET /api/mobile/events/:id/checkin-stats` | `get_checkin_stats()` | ✅ działa |
| `GET /api/mobile/events/:id/participants` | `get_participants_for_mobile()` | ❌ crash → `[]` |

Dashboard i stats używają **innych funkcji SQL** które nie mają tego buga (sprawdzone scanem).

## Konkretna zmiana

W `backend/pg_storage.py:24005`:

**PRZED:**
```python
            -- admin endpointu (pg_storage.py linie 10523-10530): template_key LIKE 'rsvp_%'
```

**PO:**
```python
            -- admin endpointu (pg_storage.py linie 10523-10530): template_key LIKE 'rsvp_%%'
```

**Uzasadnienie escape:** `%%` w psycopg2 = literalny `%`. Po psycopg2 substytucji komentarz będzie czytelny jako `LIKE 'rsvp_%'` (po unescape). Zachowuje czytelność dla człowieka + nie crashuje runtime. Identyczny pattern jak L24014 dla aktualnego LIKE.

**Alternatywne fix'y rozważone i odrzucone:**
- "Usuń pojedynczy `%` z komentarza" — utrata informacji o oryginalnym wzorcu SQL.
- "Zamień komentarz na opisowy bez `%`" — over-engineering, traci dokładność.
- "Owrap komentarz w Python `#` zamiast SQL `--`" — niemożliwe, komentarz jest w f-string SQL blocku.

## Ryzyko

- 🟢 **Bardzo niskie.** Czysto kosmetyczna zmiana 1 znaku w komentarzu SQL — zero wpływu na semantykę zapytania.
- 🟢 **Atomicity.** Jedna linia, jedna funkcja, jedna sesja → atomic commit + revert trivial.
- 🟢 **Backward compat.** Brak zmiany API shape, brak zmiany filtrów, brak zmiany schematu.
- 🟢 **Deploy bezpieczny.** Auto-deploy Render z `master` → 2-3 min od push do propagacji. Mobile dostanie poprawną listę uczestników natychmiast po deploy bez restart aplikacji (kolejny `triggerImmediateSync` na `LifecycleResumeEffect` zaciągnie dane).

## Definition of Done ✅

- [ ] Linia `backend/pg_storage.py:24005` zaktualizowana z `'rsvp_%'` → `'rsvp_%%'`.
- [ ] `py -m py_compile backend/pg_storage.py` PASS.
- [ ] Lokalny scan `pg_storage.py` confirmuje ZERO funkcji z bad `%` (regression test): `Functions with bad %: 0`.
- [ ] Commit message wskazuje `WO-MOB-008` + `BUG-MOB-001` (cross-reference).
- [ ] Backend deploy na Render (auto-deploy z `master`).
- [ ] Render logs po deploy: brak nowych wpisów `[DB] get_participants_for_mobile error`.
- [ ] QA mobile: aplikacja po LifecycleResume / pull-to-refresh / restart pokazuje 93 uczestników na AMOZ Connect Gdańsk.
- [ ] Review note w `simple-event-checkin/.agents/work_orders/review_notes/REVIEW-WO-MOB-008.md`.
- [ ] `IMPLEMENTATION_REPORT_WO_MOB_008.md`.
- [ ] Aktualizacja `BUG-MOB-001` status → `✅ Resolved` + cross-link.
- [ ] Aktualizacja `current_stage.md` + `system_state.md` (Krok 6 Master).

## Test akceptacyjny 🧪

### Pre-fix (baseline)

1. Otwórz aplikację Medidesk Event Check-in (Android).
2. Zaloguj się.
3. Wybierz "AMOZ Connect Gdańsk".
4. Dashboard: 93 uczestników (oczekujących).
5. Kliknij "Uczestnicy" w bottom nav.
6. **Obecny rezultat:** licznik "0", lista pusta.

### Post-fix (oczekiwany)

1. Deploy backend (auto z `master` na Render, ~2-3 min).
2. Render logs: brak nowych `[DB] get_participants_for_mobile error`.
3. (Manual curl test — opcjonalny przed apką):
   ```bash
   curl -H "Authorization: Bearer <mobile-jwt>" \
     "https://md-order-portal-backend.onrender.com/api/mobile/events/<event_id>/participants"
   ```
   → JSON `{event_id, count: 93, participants: [...93 items...]}`.
4. W aplikacji: pull-to-refresh na ekranie "Uczestnicy" lub LifecycleResume.
5. **Oczekiwany rezultat:** licznik "93", lista pokazuje wszystkich uczestników z filtrowaniem.

## Oczekiwany efekt wizualny 🖼️

- Ekran "Uczestnicy" pokazuje 93 wierszy z imionami, firmą, statusem (poprzednio: pusty ekran).
- Licznik w nagłówku: 93 (poprzednio: 0).
- FAB "Dodaj uczestnika" pozostaje (bez regresu).
- Filtry (status, klasa biletu) działają na pełnej liście.
- Search bar filtruje listę 93 (poprzednio: nie miał czego filtrować).

## Kontrakt API (jeśli zmiana full-stack) 🔗

- **Endpoint:** `GET /api/mobile/events/<event_id>/participants` (bez zmian).
- **Response shape:** bez zmian — `{event_id, count, participants: List<ParticipantDto>, incremental}`.
- **Zmiana:** behavior — **przestanie zwracać 200 OK z `[]` mimo że dane istnieją**. Po fix'ie zwraca prawdziwe 93 participants.
- **Backwards compat:** zachowana w 100% — żaden klient (Kotlin app, ew. iOS / web admin) nie musi się aktualizować.
- **Mobile DTO:** bez zmian (`ParticipantsResponse` + `ParticipantDto` w `core-network/dto/ResponseDtos.kt` od WO-MOB-002/003/004/005 — wszystkie pola od dawna gotowe).

## Sizing

🟢 **Bardzo mały** — 1 plik, 1 linia, 1 znak (`%` → `%%`). Klasyczny one-character bugfix z dramatic blast radius.

## Format zwrotki

- Diff linii 24005 (przed/po).
- Wynik `py -m py_compile backend/pg_storage.py` (PASS).
- Wynik scanu regression (`Functions with bad %: 0`).
- Confirmation z Render logs: po deploy brak nowych wpisów `[DB] get_participants_for_mobile error` przez ≥5 min.
- Screenshot mobile aplikacji: lista uczestników z licznikiem 93.

## Kontekst / źródło

- Bug: [BUG-MOB-001](../bugs/BUG-MOB-001-uczestnicy-lista-pusta-mimo-stats-93.md)
- Regression source: WO-MOB-002 (commit `b7e2cc5`) — wprowadził LATERAL JOIN z komentarzem zawierającym `'rsvp_%'`.
- Production logs evidence: Render `srv-d61j4bogjchc73fkfiug` (`MD_Order_portal_backend`), wpisy 2026-05-19 11:35 — 18:07.
- Affected scope: **WSZYSTKIE eventy** (nie tylko AMOZ Connect Gdańsk) — bug jest deterministyczny, każde wywołanie funkcji crashuje.
- Pattern weryfikacji: scan 512 cur.execute → tylko 1 instance bad `%` → fix punktowy bez regression.
- Reference correct escape: L24014 w tej samej funkcji (`LIKE 'rsvp_%%'`).
