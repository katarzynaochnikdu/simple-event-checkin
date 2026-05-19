# WO-MOB-005: Backend SELECT extension — uzupełnij brakujące 5 pól w `get_participants_for_mobile()` endpoint

**Data:** 2026-05-19
**Scope:** mobile (simple-event-checkin)
**Worker:** [do uzupełnienia przez Mastera]
**Stage:** [placeholder]
**Priorytet:** [placeholder — Krytyczny / Wysoki / Normalny / Niski]

## Cel

Uzupełnić w backendowym SELECT (`get_participants_for_mobile()` + `get_walkin_participants_for_mobile()`) 5 brakujących pól (`phone`, `is_walkin`, `tags`, `buyer_name`, `buyer_email`), które są obecne w mobile DTO/Entity/Domain od WO-MOB-002/003/004, ale dotąd nie były zwracane przez backend — przez co Moshi maskuje brak defaultami `null`/`false`. Ten WO realizuje follow-up oznaczony w WO-MOB-004 (Contract Sync gate) i odblokowuje wyświetlanie `phone` na details screen (pre-existing latent bug fix staje się visible w prod).

## Tło

Mobile DTO (`ParticipantDto`) + Entity (`ParticipantEntity`) + Domain (`Participant`) zawiera 5 pól, których backend **NIE zwraca** w SELECT (pre-existing drift, wykryty przez Contract Sync gate WO-MOB-004): `phone`, `is_walkin`, `tags`, `buyer_name`, `buyer_email`. Moshi defaults `null`/`false` maskują brak — mobile widzi te pola jako `null`/`false` zawsze.

Po merge'u tego WO:
- `phone` pojawia się na details screen (pre-existing latent bug fix z WO-MOB-004 stanie się visible w prod)
- `buyer_name`/`buyer_email` pojawiają się w mobile UI (jeśli kiedyś implementator UI tego użyje)
- `tags` lista (1-elementowa) pojawia się — gotowość pod multi-tag w przyszłości
- `is_walkin` = `false` zawsze dla regular participants (walk-ini są w osobnym endpoincie)

**Decyzja UX z `/master WO-MOB-005`** (2026-05-19):
- `tags` cardinality mismatch (mobile `List<String>?` vs backend SINGLE `participants.participant_tag`) → **Opcja A**: backend wraps w 1-elementową listę przez `ARRAY[p.participant_tag]`, mobile bez zmian.

## Zakres

Pliki, które Worker MA PRAWO modyfikować:
- `backend/pg_storage.py` (linie ~23900-23943 — `get_participants_for_mobile` SELECT block; ~24601-24641 — `get_walkin_participants_for_mobile`)
- `backend/api/MOBILE_API.md` (sekcja przykładu response — dodać 5 pól)
- `API_CONTRACT.md` (sekcja "Mobile Participants — RSVP Fields" rozszerzyć o 5 pól, lub stworzyć nową "Mobile Participants — Buyer & Tags Fields")

Konkretne zmiany SQL (z research raportu) w `get_participants_for_mobile()` SELECT — między linią 23913 (`p.event_order_id,`) a 23914 (`tc.ticket_name,`) dodać:

```sql
p.phone,
CASE
    WHEN p.participant_tag IS NOT NULL AND TRIM(p.participant_tag) <> ''
    THEN ARRAY[p.participant_tag]
    ELSE NULL
END AS tags,
FALSE AS is_walkin,
NULLIF(
    TRIM(CONCAT_WS(' ',
        NULLIF(TRIM(o.purchaser_first_name), ''),
        NULLIF(TRIM(o.purchaser_last_name), '')
    )),
    ''
) AS buyer_name,
o.purchaser_email AS buyer_email,
```

**ZERO new JOIN-ów** — wszystkie 5 pól dostępne z już-JOINed tabel (`participants p` + `orders o`).

Dla `get_walkin_participants_for_mobile()`: zwrócić te same 5 pól z różnymi wartościami — `is_walkin=TRUE`, `phone`/`buyer_*` z walk-in source jeśli kolumna istnieje w `walkin_participants`.

## Czego NIE ruszać 🛑

- Mobile codebase (`simple-event-checkin/app/src/**`) — DTO/Entity/Domain już mają pola, mappery WO-MOB-004 już to obsługują.
- Schema DB / migrations — kolumny istnieją (`participants.phone`, `participants.participant_tag`, `orders.purchaser_*`).
- Inne SELECT-y w `pg_storage.py` poza dwoma mobile endpointami.
- Auth / JWT / endpointy w `backend/api/mobile.py` poza ewentualnym docstring update'em.

## Pliki startowe

- `backend/pg_storage.py:23900-23943` — `get_participants_for_mobile()` SELECT
- `backend/pg_storage.py:24601-24641` — `get_walkin_participants_for_mobile()` (sprawdź źródło `phone`/`buyer_*` dla walk-ini)
- `backend/api/MOBILE_API.md` — sekcja response example
- `API_CONTRACT.md` — sekcja Mobile Participants
- `simple-event-checkin/app/src/main/java/.../data/remote/dto/ParticipantDto.kt` (read-only — referencja kontraktu)
- WO-MOB-004 raport implementacji (`simple-event-checkin/.agents/work_orders/IMPLEMENTATION_REPORT_WO_MOB_004.md`) — kontekst follow-up

## Ryzyko

- **`phone` legacy NULL** — pre-2024 uczestnicy mogą mieć `phone=NULL`. Mobile DTO nullable — OK.
- **`buyer_name` empty** — legacy orders mogą mieć BOTH first/last NULL → `NULLIF(..., '')` zwraca NULL zamiast empty string.
- **`buyer_email` NULL** — pre-purchaser-tracking orders. Mobile nullable — OK.
- **`tags` array empty vs NULL** — gdy `participant_tag IS NULL` lub empty → return NULL (nie pustą listę). Moshi parsuje NULL → `null`, mobile DTO `List<String>? = null` przyjmuje.
- **`is_walkin` zawsze FALSE w regular endpoint** — walk-ini mają hardcoded `TRUE` w `get_walkin_participants_for_mobile()` (już ustawione w WO-MOB-002 walk-in fix). Mobile client łączy oba endpointy.
- **Performance** — zero new JOINs, scalar columns z już-JOINed tabel. Brak ryzyka dla event 5000+ uczestników.
- **PII parity** — 5 pól = istniejące dane biznesowe, parity z desktop endpointem. Security Gate musi to potwierdzić.

## Definition of Done ✅

- [ ] `get_participants_for_mobile()` zwraca 5 nowych pól (`phone`, `is_walkin=FALSE`, `tags`, `buyer_name`, `buyer_email`)
- [ ] `get_walkin_participants_for_mobile()` zwraca 5 pól (z różnymi wartościami: `is_walkin=TRUE`, `phone`/`buyer_*` z walk-in source jeśli dostępne)
- [ ] `py -m py_compile backend/pg_storage.py backend/api/mobile.py` PASS
- [ ] `backend/api/MOBILE_API.md` — sekcja response example uzupełniona o 5 pól
- [ ] `API_CONTRACT.md` — sekcja Mobile Participants rozszerzona o 5 pól (lub nowa "Buyer & Tags Fields")
- [ ] Security Gate: PASS (5 pól = istniejące dane biznesowe, PII parity z desktop endpoint)
- [ ] Contract Sync Gate: PASS (3-way consistency backend ↔ MOBILE_API.md ↔ API_CONTRACT.md; mobile DTO już ma pola od WO-MOB-002/003)
- [ ] Migration Guard: N/A (brak SQL migrations)
- [ ] Review note w `review_notes/REVIEW-WO-MOB-005-*.md`

## Test akceptacyjny 🧪

Event testowy: `24311000000909074` ([panel.medidesk.edu.pl](https://panel.medidesk.edu.pl/admin/events/24311000000909074/))

1. Po deploy backendu (lub lokalnie) curl `/api/mobile/events/24311000000909074/participants` z JWT admin token.
2. Response sprawdza obecność 5 pól per uczestnik (`phone`, `is_walkin`, `tags`, `buyer_name`, `buyer_email`).
3. Spot check w DB: dla uczestnika z `participants.phone IS NOT NULL` mobile zwraca tę wartość.
4. Co najmniej 1 uczestnik z `participant_tag='prelegent'` (lub innym) → `tags` JSON `["prelegent"]`.
5. Co najmniej 1 uczestnik bez tagu → `tags: null`.
6. Wszyscy z regular endpoint mają `is_walkin: false`.
7. Walk-in endpoint (`/walkins`) zwraca `is_walkin: true` dla każdego rekordu.
8. Mobile app (debug APK) wyświetla `phone` na details screen dla uczestnika z `phone IS NOT NULL`.

## Oczekiwany efekt wizualny 🖼️

- Mobile **details screen**: pole `phone` (jeśli było ukryte placeholderem) pokazuje rzeczywistą wartość dla uczestników z numerem.
- Mobile **details screen**: pola `buyer_name`/`buyer_email` (jeśli istnieje UI binding) wyświetlają imię/email nabywcy biletu.
- Mobile **listing / details**: tag uczestnika pojawia się jako element listy (1-elementowa lista) zamiast być `null`.
- **Brak regresji wizualnych** — pozostałe pola (`rsvp_*`, `ticket_*`, `email`, `checked_in*`) bez zmian.

## Kontrakt API (zmiana full-stack) 🔗

`GET /api/mobile/events/:id/participants` — response dodaje per uczestnik:

```json
{
  "phone": "string | null",
  "is_walkin": false,
  "tags": ["string"] | null,
  "buyer_name": "string | null",
  "buyer_email": "string | null"
}
```

`GET /api/mobile/events/:id/walkins` — analogiczne 5 pól, z `is_walkin: true` zawsze. `phone`/`buyer_*` z walk-in źródła (jeśli kolumna istnieje), inaczej `null`.

## Format zwrotki

- Lista zmienionych plików z jednolinijkowym opisem zmian (zwłaszcza `pg_storage.py` SELECT diff)
- Git diff summary
- Wynik `py -m py_compile backend/pg_storage.py backend/api/mobile.py`
- Curl response sample (anonimizowany) z 5 nowymi polami
- Spot check DB: 1 row z `phone IS NOT NULL`, 1 row z `participant_tag`, 1 row bez taga
- Screenshot details screen mobile (jeśli debug APK dostępny)
- Propozycja wpisu do `decision_log.md` (Opcja A dla `tags` cardinality — backend wraps single tag w 1-elementową listę)

---

## Powiązania

- **Follow-up z:** WO-MOB-004 (Contract Sync gate flagged backend dependency)
- **Buduje na:** WO-MOB-002 (walk-in `is_walkin=TRUE` hardcoded), WO-MOB-003 (DTO/Domain extension)
- **Sizing:** 🟢 mały — 1 plik kodu (`pg_storage.py`) + 2 doc files. Zero new JOIN-ów, zero migrations, zero mobile changes.
