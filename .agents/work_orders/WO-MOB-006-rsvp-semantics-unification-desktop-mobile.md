# WO-MOB-006: RSVP semantics unification desktop↔mobile (strict dla wszystkich)

**Data:** 2026-05-19
**Scope:** mobile (simple-event-checkin) — ale **fizyczna zmiana w backend desktop** (`backend/pg_storage.py`)
**Worker:** [do uzupełnienia przez Mastera]
**Stage:** [placeholder]
**Priorytet:** [placeholder — Krytyczny / Wysoki / Normalny / Niski]

> **⚠️ Cross-scope note:** WO zarejestrowane w mobile (driver: ADR z WO-MOB-002/006 unifikacja), ale modyfikuje pliki backend desktop. Może wymagać re-route do desktop scope przy dispatch'u przez Mastera. Patrz sekcja "Zakres".

## Cel

Ujednolicić semantykę pola `rsvp_sent` między desktop CRM a mobile app — przejście z desktop **lenient** (`else TRUE` = brak wpisu w mail_log domniemywa wysłanie) na **strict** (TRUE iff istnieje realny wpis `mail_log.status IN ('sent', 'delivered')`).

Drift powstał gdy mobile (WO-MOB-002) przyjął strict semantykę, podczas gdy desktop pozostał lenient — ten sam uczestnik widział się różnie w dwóch UI. Decyzja UX (`/master WO-MOB-006` 2026-05-19, Opcja A z DoR): **strict dla wszystkich**. Akceptujemy potencjalne false negatives dla legacy uczestników (stare eventy sprzed `mail_log` era) — uczciwa informacja "brak dowodu wysłania" > fałszywie pozytywny tick.

## Zakres

Pliki które Worker MA PRAWO modyfikować:

- `backend/pg_storage.py` (linie 10517-10521 — funkcja `get_participants_for_admin_list()`)
- `backend/api/MOBILE_API.md` (usuń note o STRICT drift desktop↔mobile)
- `API_CONTRACT.md` (usuń "known intentional drift" note z sekcji "Mobile Participants — RSVP Fields")
- (Opcjonalnie, jeśli grep wykryje) inne occurrences `ELSE TRUE` powiązane z `rsvp_sent` w `pg_storage.py`

Pliki do aktualizacji **post-implementation** (już akceptacja zamknięta):

- `.agents/context/decision_log.md` — close ADR "Mobile rsvp_sent semantyka STRICT-niejsza niż desktop" jako RESOLVED + nowa ADR "RSVP semantics unification (WO-MOB-006) — strict dla wszystkich"
- (Opcjonalnie) `.agents/context/known_gotchas.md` — informational note: "Legacy uczestnicy bez mail_log pokażą się jako rsvp_sent=FALSE post-WO-MOB-006"

## Czego NIE ruszać 🛑

- Mobile kod (`simple-event-checkin/app/...`) — strict semantyka już zaimplementowana w WO-MOB-002. Zero zmian.
- Frontend desktop (`frontend/src/...`) — typ pola `rsvp_sent: bool` bez zmian, tylko semantyka w backend. Frontend NIE wymaga modyfikacji.
- Migracje SQL (`database/migrations/`) — N/A, to **NIE jest** zmiana schemy, tylko query logic.
- Inne funkcje w `pg_storage.py` poza `get_participants_for_admin_list()` — chyba że grep ujawni identyczny lenient pattern przy `rsvp_sent`.

## Pliki startowe

- `backend/pg_storage.py` (linia 10517 — current lenient implementation)
- `.agents/context/decision_log.md` (ADR 2026-05-19 "Mobile rsvp_sent semantyka STRICT-niejsza niż desktop")
- `backend/api/MOBILE_API.md` (sekcja drift note)
- `API_CONTRACT.md` (sekcja "Mobile Participants — RSVP Fields")

## Ryzyko

- **UX impact dla operator desktop** — niektórzy uczestnicy nagle pokażą się jako "RSVP nie wysłany". To **prawda biznesowa**, ale może wywołać zgłoszenia "co się stało?". Mitygacja: komunikat w changelogu / ADR.
- **Legacy data false negatives** — uczestnicy z eventów sprzed `mail_log` era pokażą się jako rsvp_sent=FALSE. **Akceptowane** per Opcja A z DoR. Nie blokuje WO.
- **Walk-ini desktop** — `get_walkin_participants_for_event()` (jeśli istnieje analogiczna funkcja) — może mieć identyczny lenient pattern. Worker musi sprawdzić grep i zdecydować scope.
- **Zero ryzyko regresji dla normalnego przypadku** — uczestnik z `mail_log.status='sent'` nadal dostaje rsvp_sent=TRUE. Drift jest tylko dla brakujących wpisów.

## Definition of Done ✅

- [ ] `backend/pg_storage.py:10517-10521` zmienione z lenient na strict (`ml.mail_id IS NOT NULL AND ml.status IN ('sent', 'delivered')`)
- [ ] `py -m py_compile backend/pg_storage.py` PASS
- [ ] `backend/api/MOBILE_API.md` — note o STRICT drift usunięta lub przekształcona w "STRICT unified WO-MOB-006"
- [ ] `API_CONTRACT.md` — analogicznie usunięta sekcja drift
- [ ] Security Gate: PASS (zero zmian w PII exposure / auth)
- [ ] Contract Sync Gate: PASS (drift desktop↔mobile RESOLVED)
- [ ] Migration Guard: N/A (zero SQL migrations)
- [ ] ADR closure: stary ADR oznaczony RESOLVED + nowa ADR opisująca unifikację
- [ ] Grep `pg_storage.py` dla innych lenient `ELSE TRUE` powiązanych z `rsvp_sent` — jeśli są, dodać do scope WO lub utworzyć follow-up WO
- [ ] Review note w `simple-event-checkin/.agents/work_orders/review_notes/REVIEW-WO-MOB-006-*.md`

## Test akceptacyjny 🧪

Event testowy: `24311000000909074` (Sylwia Baran case)

**Pre-deploy (baseline):**
1. Otwórz desktop CRM: `/admin/events/24311000000909074/participants`
2. Znajdź Sylwia Baran — **oczekiwane:** zielony tick "RSVP wysłany" (lenient bug-feature ⚠️)
3. Otwórz mobile app, ten sam event — **oczekiwane:** ikona RSVP ukryta (strict ✅, prawdziwy stan)

**Post-deploy (verification):**
4. Desktop CRM `/admin/events/24311000000909074/participants` → Sylwia Baran ma "RSVP nie wysłany" ✅ (strict, unified)
5. Mobile app: bez zmian — nadal strict (już od WO-MOB-002)
6. **Sanity check (zero regresji):** wybierz uczestnika który **faktycznie** dostał RSVP (sprawdź w DB: `SELECT * FROM mail_log WHERE to_email='<email>' AND template_key LIKE 'rsvp_%' AND status IN ('sent','delivered')`) → desktop UI nadal pokazuje tick.

**Spot check DB (impact estimation):**
```sql
SELECT COUNT(*) FROM participants p
JOIN orders o ON p.event_order_id = o.event_order_id
WHERE o.status='paid'
AND NOT EXISTS (
  SELECT 1 FROM mail_log ml
  WHERE ml.to_email=p.email
    AND ml.template_key LIKE 'rsvp_%'
    AND ml.status IN ('sent','delivered')
);
```
To liczba uczestników którzy "stracą" zielony tick w desktop UI. Może być znaczna dla starych eventów. To **prawda**.

## Oczekiwany efekt wizualny 🖼️

- **Desktop CRM panel uczestników** (`/admin/events/:id/participants`): dla uczestników bez wpisu w `mail_log` ze statusem 'sent'/'delivered' — zniknie zielony tick "RSVP wysłany" (zamiast tego brak tick / czerwony / "nie wysłano" w zależności od UI komponentu)
- **Mobile app** (`ParticipantListScreen`): **zero zmian** — już strict od WO-MOB-002
- **Spójność** — ten sam uczestnik widzi się identycznie w obu UI

## Kontrakt API (zmiana semantyki, nie typu) 🔗

Endpoint: `GET /admin/api/events/:id/participants`

- **Typ pola:** `rsvp_sent: bool` — **bez zmian**
- **Semantyka:** PRZED = lenient (brak `mail_log` → TRUE), PO = strict (brak `mail_log` → FALSE)
- **Frontend impact:** zero — typ ten sam, UI komponent renderuje tick na podstawie boolean'a
- **Drift resolution:** mobile endpoint `GET /api/mobile/events/:id/participants` (od WO-MOB-002) używał już strict. Desktop teraz dorównuje. Single source of truth: realny wpis w `mail_log`.

PRZED (`backend/pg_storage.py:10517-10521`):
```sql
CASE
    WHEN ml.status IN ('sent', 'delivered') THEN TRUE
    WHEN ml.status IS NOT NULL THEN FALSE
    ELSE TRUE                          -- brak wpisu = TRUE (bug-feature!)
END AS rsvp_sent,
```

PO:
```sql
-- WO-MOB-006 (2026-05-19): strict semantyka — match mobile WO-MOB-002.
-- Brak wpisu w mail_log = FALSE (nie domniemujemy że wysłano).
-- Patrz decision_log.md ADR "RSVP semantics unification 2026-05-19".
(ml.mail_id IS NOT NULL AND ml.status IN ('sent', 'delivered')) AS rsvp_sent,
```

## Format zwrotki

- Lista zmienionych plików z jednolinijkowym opisem (backend SQL + docs)
- Git diff summary (oczekiwane: ~5 linii w pg_storage.py + cleanup w 2 doc files)
- Output `py -m py_compile backend/pg_storage.py`
- Output spot-check query (liczba uczestników którzy stracą tick)
- Propozycja wpisu zamknięcia + nowa ADR do `decision_log.md`
- Screenshot pre/post-deploy desktop CRM dla event `24311000000909074` (deferred jeśli post-deploy)

## Sizing

🟢 **mały** — 1 plik kodu (~5 linii SQL change) + 2 doc files. Zero JOIN-ów, zero migrations, zero mobile changes, zero frontend changes.

## Cross-scope dispatch note

⚠️ WO zarejestrowane w **mobile** (driver: kontynuacja WO-MOB-002 + ADR z mobile sprintu), ale fizyczna zmiana w **backend desktop** (`backend/pg_storage.py`). Master Agent przy dispatch'u może:
- **(a)** uznać scope=desktop dla worker'a i tylko cross-reference w mobile INDEX
- **(b)** trzymać scope=mobile bo to kontynuacja unifikacji
Decyzja per Master Agent — bez znaczenia dla executora, byle worker miał dostęp do backend/.
