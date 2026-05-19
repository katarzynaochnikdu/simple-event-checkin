# WO-MOB-002: Backend — dodaj rsvp_sent / rsvp_response / rsvp_responded_at do mobile participants endpoint

**Data:** 2026-05-19
**Worker:** worker-implementer (backend) — dispatch przez `/master`
**Stage:** Backend / Mobile API
**Priorytet:** Wysoki (bug widoczny w UI mobile produkcyjnym)
**Scope:** **backend** (część bug'a "RSVP status na ekranie szczegółów uczestnika mobile" — split per decyzja użytkownika 2026-05-19 z `/master ustal odpowiedzi`)
**Parent context:** ten WO to **część 1/2**. Część 2/2 = [WO-MOB-003](WO-MOB-003-mobile-rsvp-status-conditional-render.md) (mobile DTO + Room migration + Composable). Mobile WO **zależy** od merge'u tego WO.

## Cel
Endpoint `GET /api/mobile/events/:event_id/participants` ma zwracać 3 nowe pola per uczestnik, które pozwolą aplikacji mobile **wiarygodnie** rozróżnić: (a) mail RSVP nie został wysłany, (b) mail wysłany, brak odpowiedzi, (c) mail wysłany, odpowiedź `confirmed`/`declined`. Obecne `attendance_status` jest **derived** i kompoundowe (order.status + RSVP) — nie nadaje się jako proxy RSVP.

## Kontekst i diagnoza (z research 2026-05-19)
- Endpoint `backend/api/mobile.py:788-806` → `pg_storage.get_participants_for_mobile()` (`pg_storage.py:23797-23872`).
- Obecny SELECT zwraca tylko `p.status` + `p.attendance_status` — żadnych pól RSVP.
- Sylwia Baran case: `order.status='paid'` + brak `rsvp_id` → `recalculate_attendance_status()` ustawia `attendance_status='confirmed'` (`pg_storage.py:3618-3628`) → mobile interpretuje to jako "RSVP confirmed" → zielony tick **mimo że mail RSVP nigdy nie poszedł**.
- Wzorzec referencyjny dla logiki `rsvp_sent` istnieje już w desktop admin endpoint: `get_participants_for_admin_list()` (`pg_storage.py:10411-10519`, JOIN-y `latest_rsvp` + `latest_rsvp_mail` z `mail_log` filtrem `template_key LIKE 'rsvp_%'` AND `status IN ('sent','delivered')`).

## Oczekiwane zachowanie ✅
Response per uczestnik z endpointu mobile zawiera 3 nowe pola:

| Pole | Typ | Semantyka |
|---|---|---|
| `rsvp_sent` | `bool` | `TRUE` ⇔ istnieje row w `mail_log` z `to_email=participant.email` AND `event_id=...` AND `template_key LIKE 'rsvp_%'` (OR `template_key ~ '^sched_campaign_\d+_rsvp'`) AND `status IN ('sent','delivered')`. `FALSE` w przeciwnym razie (w tym: token w `rsvp_responses` istnieje ale brak wpisu w `mail_log`). |
| `rsvp_response` | `'confirmed' \| 'declined' \| null` | Wartość z `rsvp_responses.response` dla najnowszego token'a tego uczestnika. `null` gdy brak row w `rsvp_responses` lub gdy `response IS NULL` (czeka na klik). |
| `rsvp_responded_at` | ISO 8601 timestamp \| `null` | `rsvp_responses.responded_at` dla najnowszego token'a. `null` gdy uczestnik nie kliknął. |

## Zakres
- `backend/pg_storage.py:23797-23872` (`get_participants_for_mobile`) — dodać:
  - LATERAL JOIN `latest_rsvp` (kopia logiki z linii 10428-10440)
  - LATERAL JOIN `latest_rsvp_mail` (kopia logiki z linii 10441-10451)
  - 3 nowe pola w SELECT
- `backend/api/mobile.py:788-806` — bez zmian w kodzie (dict pass-through), ale **upewnij się** że endpoint nie filtruje pól po drodze
- Analogicznie: `get_walkin_participants_for_mobile()` (jeśli istnieje) — dla walk-inów zwróć `rsvp_sent: false`, `rsvp_response: null`, `rsvp_responded_at: null` (stała — walk-ini nigdy nie dostają RSVP)
- `backend/api/MOBILE_API.md` — sekcja `GET /api/mobile/events/:id/participants` → zaktualizować przykład response
- `API_CONTRACT.md` — sekcja Mobile (jeśli istnieje) lub dopisać

## Czego NIE ruszać 🛑
- 🛑 `get_participants_for_admin_list()` (desktop) — wzorzec referencyjny, NIE modyfikuj
- 🛑 `recalculate_attendance_status()` / logika `attendance_status` — NIE zmieniaj. `attendance_status` zostaje compound; nowe pola RSVP istnieją obok niego, niezależnie
- 🛑 Tabela `rsvp_responses` — schema bez zmian, tylko czytamy
- 🛑 Tabela `mail_log` — schema bez zmian, tylko czytamy
- 🛑 JSONB `events.data.*` — NIE modyfikuj (constraints §11 incydent kwiecień 2026)
- 🛑 Innych endpointów `/api/mobile/*` (login, /me, /events, /checkin, /sync)

## Pliki startowe 📂
- `backend/pg_storage.py:23797-23872` (`get_participants_for_mobile` — punkt startu)
- `backend/pg_storage.py:10411-10519` (`get_participants_for_admin_list` — wzorzec referencyjny LATERAL JOIN-ów rsvp)
- `backend/api/mobile.py:788-806` (endpoint, sprawdzić czy nie filtruje fields)
- `backend/api/MOBILE_API.md:113-152` (kontrakt do aktualizacji)
- `DATA_DICTIONARY.md` — sekcje `rsvp_responses`, `mail_log` (referencja schema)

## Ryzyko
- **Drift kontraktu mobile** — mobile DTO (WO-MOB-003) musi tolerować brak pól (legacy klienci). Mitigation: mobile DTO `val rsvpSent: Boolean = false` (Moshi pominie brakujące, default `false` → ikona ukryta = safe fallback).
- **Performance** — LATERAL JOIN do `mail_log` przy każdym uczestniku może spowolnić query (event z 5000 uczestników × LATERAL). Mitigation: sprawdź czy `mail_log` ma INDEX na `(to_email, event_id, template_key, status)`. Jeśli nie — rozważ index hint w WO follow-up (poza scope tego WO).
- **`mail_log` może zawierać wiele wpisów** rsvp_initial + rsvp_reminder_1 + rsvp_reminder_2 — semantyka `rsvp_sent` to OR (dowolny z nich = sent/delivered = true). Patrn admin endpoint linie 10441-10451 dla referencyjnej logiki.

## Definition of Done ✅
- [ ] `get_participants_for_mobile()` zwraca dodatkowo `rsvp_sent`, `rsvp_response`, `rsvp_responded_at` per uczestnik
- [ ] `get_walkin_participants_for_mobile()` (jeśli istnieje) zwraca te same 3 pola jako `false` / `null` / `null`
- [ ] `backend/api/mobile.py:788-806` przepuszcza nowe pola bez filtra
- [ ] `py -m py_compile backend/pg_storage.py backend/api/mobile.py` — PASS
- [ ] `backend/api/MOBILE_API.md` — przykład response zaktualizowany
- [ ] `API_CONTRACT.md` — sekcja mobile zaktualizowana
- [ ] Test akceptacyjny przechodzi (curl/Postman → response zawiera nowe pola dla event testowego 24311000000909074)
- [ ] Contract Sync Gate: PASS (nowe pola udokumentowane w 3 miejscach: backend response, MOBILE_API.md, API_CONTRACT.md)
- [ ] Security Gate: PASS (nowe pola NIE eksponują PII spoza zakresu — `rsvp_response` to enum, `rsvp_responded_at` to timestamp, `rsvp_sent` to bool; wszystko już dostępne dla operatora w panelu admin)

## Test akceptacyjny 🧪

**Event testowy:** `24311000000909074` ([panel.medidesk.edu.pl](https://panel.medidesk.edu.pl/admin/events/24311000000909074/))

1. Backend uruchomiony lokalnie LUB deploy do staging/prod.
2. Auth: zaloguj się do mobile API (`POST /api/mobile/login`) z konta administratora mającego dostęp do eventu 24311000000909074, pobierz JWT.
3. `curl -H "Authorization: Bearer <JWT>" https://md-order-portal-backend.onrender.com/api/mobile/events/24311000000909074/participants` (lub localhost gdy lokalnie)
4. Zweryfikuj że response per uczestnik zawiera 3 nowe pola.
5. Znajdź w response:
   - **Uczestnika A** który dostał RSVP i kliknął "potwierdzam": `rsvp_sent: true`, `rsvp_response: "confirmed"`, `rsvp_responded_at: "<timestamp>"`
   - **Uczestnika B** który dostał RSVP i kliknął "odmawiam": `rsvp_sent: true`, `rsvp_response: "declined"`, `rsvp_responded_at: "<timestamp>"`
   - **Uczestnika C** który dostał RSVP ale nie kliknął: `rsvp_sent: true`, `rsvp_response: null`, `rsvp_responded_at: null`
   - **Uczestnika D** który NIE dostał RSVP (np. Sylwia Baran lub analogiczna): `rsvp_sent: false`, `rsvp_response: null`, `rsvp_responded_at: null`
6. Krzyżowa weryfikacja w DB: dla uczestnika D query `SELECT 1 FROM mail_log WHERE to_email='<email>' AND event_id='24311000000909074' AND template_key LIKE 'rsvp_%' AND status IN ('sent','delivered')` → 0 rowów.

## Kontrakt API (zmiana additive) 🔗

`GET /api/mobile/events/:event_id/participants` response — dodaj per element listy `participants[]`:

```json
{
  "id": 12345,
  "ticket_id": "...",
  "first_name": "Sylwia",
  "last_name": "Baran",
  "email": "sylwia.baran@luxmedica.pl",
  // ... istniejące pola ...
  "attendance_status": "confirmed",  // bez zmian, compound
  "rsvp_sent": false,                // NOWE
  "rsvp_response": null,             // NOWE: 'confirmed' | 'declined' | null
  "rsvp_responded_at": null          // NOWE: ISO timestamp | null
}
```

**Backwards compatibility:** zmiana additive — istniejący klienci mobile (pre WO-MOB-003) ignorują nowe pola, działają jak dotychczas. Po WO-MOB-003 mobile zacznie konsumować.

## Format zwrotki
- Lista zmienionych plików z jednolinijkowym opisem
- Git diff summary
- Wynik `py -m py_compile backend/pg_storage.py backend/api/mobile.py`
- Curl response dla event 24311000000909074 (4 uczestnicy: A/B/C/D) — sanitized (zamaskuj PII w raporcie, zostaw tylko `rsvp_*` pola + initials)
- Wpis do `API_CONTRACT.md` (diff sekcji)
- Wpis do `backend/api/MOBILE_API.md` (diff sekcji)
- (Opcjonalnie) sprawdzenie czy `mail_log` ma INDEX na (to_email, event_id, template_key, status) — output `EXPLAIN ANALYZE` dla query

## Załączniki
- Research raport źródłowy (transcript sesji 2026-05-19 `/master ustal odpowiedzi`)
- Screenshot Sylwia Baran (mobile detail screen, zielony RSVP tick mimo braku wysłanego maila)
