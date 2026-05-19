# IMPLEMENTATION_REPORT — WO-MOB-006

**Data:** 2026-05-19
**Worker:** worker-implementer (backend) + bonus comment refresh by Master
**Status:** ✅ Code complete, compile PASS, both gates PASS, awaiting commit + post-deploy QA

## Cel
RSVP semantics unification desktop↔mobile — zmień desktop `get_participants_for_admin_list()` na strict semantykę dopasowaną do mobile (WO-MOB-002). **Rozszerzony scope** po pre-implementation grep: 4 funkcje zamiast 1.

## Zmienione pliki (3 + 1 bonus comment fix)

- **`backend/pg_storage.py`** (+37 LOC, 4 funkcje):
  - `get_participants_for_event()` (~10520) — z lenient `CASE...ELSE TRUE` na strict `(ml.mail_id IS NOT NULL AND ml.status IN ('sent','delivered'))`
  - `get_my_mentees_participants()` (~10685) — analogiczna zmiana + dodano `ml.mail_id` do inner LATERAL SELECT
  - `get_all_participants()` (~10775) — z **hardcoded `TRUE as rsvp_sent`** na strict (najgorsza wariantą lenient!)
  - `get_participant_by_id()` (~10951) — analogiczna zmiana + `ml.mail_id` w inner SELECT
  - **Bonus comment fix** (`get_participants_for_mobile`, linia 23958-23959): stary komentarz "STRIKT-niejsza niż desktop" odświeżony na "spójna z desktop endpointami po WO-MOB-006"
- **`backend/api/MOBILE_API.md`** (-1/+1 LOC) — note STRICT unified
- **`API_CONTRACT.md`** (-1/+1 LOC) — drift RESOLVED note

## Decyzja UX (z `/master WO-MOB-006`)

**Strict dla wszystkich** (Opcja A z DoR) — akceptujemy legacy uczestników pokazujących się jako "RSVP nie wysłany" (false negative). To **prawda biznesowa** dla starych eventów — operator widzi "nie ma dowodu wysłania".

## Build / Compile

```
py -m py_compile backend/pg_storage.py
→ PASS
```

## Kluczowe fragmenty diff

**Pre-refactor wzorzec wspólny dla 3 z 4 funkcji** (`get_participants_for_event`, `get_my_mentees_participants`, `get_participant_by_id`):
```sql
CASE
    WHEN ml.status IN ('sent', 'delivered') THEN TRUE
    WHEN ml.status IS NOT NULL THEN FALSE
    ELSE TRUE                           -- bug-feature: brak wpisu = TRUE
END AS rsvp_sent,
```

**Pre-refactor `get_all_participants()` (najgorsze):**
```sql
SELECT rr.response as rsvp_response, rr.responded_at as rsvp_responded_at,
       TRUE as rsvp_sent,                -- hardcoded! "RSVP response exists => mail sent"
```

**Post-refactor (wszystkie 4 funkcje identycznie):**
```sql
-- WO-MOB-006 (2026-05-19): strict semantyka — match mobile WO-MOB-002.
-- Brak wpisu w mail_log = FALSE (nie domniemujemy ze wyslano).
-- Patrz decision_log.md ADR "RSVP semantics unification 2026-05-19".
(ml.mail_id IS NOT NULL AND ml.status IN ('sent', 'delivered')) AS rsvp_sent,
```

**Inner LATERAL extension** (3 funkcje wymagały dodania `ml.mail_id` do inner SELECT):
```sql
LEFT JOIN LATERAL (
    SELECT ml.mail_id, ml.status, ml.created_at, ...  -- dodano mail_id
    FROM mail_log ml
    WHERE ...
) ml_sub ON TRUE
```

## Grep verification (zero pozostałych lenient occurrences)

```bash
grep "ELSE TRUE" pg_storage.py        → No matches found
grep "TRUE as rsvp_sent" pg_storage.py → No matches found
```

Pozostałe `rsvp_sent` w `pg_storage.py` to:
- `COALESCE(lrm.rsvp_sent, FALSE)` w consumer'ach LATERAL — konsumują już strict CTE
- `COUNT(*) as rsvp_sent` w stats queries — agregat, nie boolean
- Mobile endpoint (`get_participants_for_mobile`) — już strict od WO-MOB-002
- Python-side `dict[...] = ...` w innych funkcjach (poza scope)

## Gates

| Gate | Status | Klucz finding |
|---|---|---|
| 🔒 Security | PASS | 0 findings, pure SQL semantic refactor (bool → bool, ten sam typ). `mail_id` w inner LATERAL NIE propagowany do outer response. Zero new PII/logging/JSONB/injection. UX impact = business decision, accepted. |
| 🔗 Contract Sync | PASS | 5 funkcji × identical strict pattern. Frontend 27 użyć `rsvp_sent` audytowane — żaden kod nie polega na lenient. `api-types.ts × 3` N/A. **Drift mobile↔desktop RESOLVED.** 2 minor WARN: stary komentarz w mobile endpoint (✅ FIXED post-gate), pre-existing `get_all_participants` brak `OR sched_campaign` matcher (out-of-scope WO-MOB-007 candidate). |
| 🗄️ Migration Guard | N/A | Zero SQL migrations. |
| 🧪 QA | DEFERRED | Compile PASS. UI/curl test post-deploy. |

## Postmortem (4 pytania)

### Co działa

- Wszystkie 4 desktop funkcje używają teraz **identycznej strict logiki** SQL — spójność wewnątrz desktop **i** między desktop↔mobile.
- Mobile endpoint (`get_participants_for_mobile`) bez zmian funkcjonalnych (już strict od WO-MOB-002), ale stary komentarz odświeżony dla audit trail clarity.
- Compile PASS. Build implicit (Python bez kompilacji).
- Frontend bez zmian (typ `rsvp_sent: bool` unchanged) — zero downstream impact.
- Backwards compat: typ pola identyczny, **semantyka strict** dla legacy data.

### Co nie działa / known issues

- **Legacy false negatives** dla starych eventów (pre-mail_log era) — akceptowane per Opcja A. UX impact zostanie zmierzony post-deploy przez spot check DB query.
- **Pre-existing inconsistency:** `get_all_participants` (`pg_storage.py:10791`) ml_sub WHERE brakuje `OR ml.template_key ~ '^sched_campaign_[0-9]+_'` matcher. Pre-existing, NIE wprowadzone przez WO-MOB-006. Może być follow-up WO-MOB-007.
- **Brak `decision_log.md` + `known_gotchas.md` update przez implementera** — to scope Mastera (post-implementation update). Done w state update phase.

### Co odłożone

- **Spot check DB query post-deploy** — pomiar ile uczestników "straciło" zielony tick w desktop CRM (estimated impact).
- **UI/curl QA test** na event `24311000000909074` — pre/post-deploy comparison Sylwii Baran case.
- **WO-MOB-007 (opcjonalny)** — drobny follow-up dla `get_all_participants` ml_sub WHERE rozszerzony o scheduled campaigns matcher (pre-existing inconsistency).
- **Frontend audit** — jeśli któryś desktop user zgłosi "wszyscy nagle bez RSVP" → może być potrzebny UI tooltip wyjaśniający strict semantykę.

### Lessons learned

1. **"Drift desktop↔mobile" mógł być **drift wewnątrz desktop** w 4 funkcjach.** Pre-implementation grep wykrył nie 1 lenient ale 4 różne warianty. Implementer rozszerzył scope organicznie — to **wzorzec do replikacji** dla future cross-scope unification: zawsze grep wszystkie warianty pola w docelowym module przed implementacją.

2. **`get_all_participants` hardcoded `TRUE as rsvp_sent`** — najgorsza pre-existing semantyka, działała jak zaślepka która **zawsze** zwracała "RSVP wysłany" niezależnie od stanu `mail_log`. Lekcja: hardcoded boolean values w SELECT są red flag — zawsze sprawdzić czy jest realna logika lub eksplicit komentarz "intentional".

3. **Inner LATERAL extension wymagała zmiany** — `ml.mail_id` musiało być dodane do inner SELECT, aby outer mogło użyć `IS NOT NULL`. Dla 3 funkcji to było bezpośrednie. Bez tego pattern `(ml.mail_id IS NOT NULL AND ...)` nie skompiluje się — `ml.mail_id` byłby niedostępny. Lekcja przy LATERAL refactor: sprawdź czy referenced columns są w inner projection.

4. **Pre-flight `git status` PASS po raz 4** — czwarty WO w sesji (MOB-003 → MOB-004 → MOB-005 → MOB-006) bez atomicity violation. Lekcja z WO-MOB-002 mixed commit jest **stable pattern** w workflow.

5. **Comment hygiene matters.** Stary komentarz w mobile endpoint claimował "STRIKT-niejsza niż desktop" — po unification stało się to nieprawdą. Contract Sync gate to wykrył jako WARN-1. Pattern: każda zmiana semantyki SQL **musi** trigger review komentarzy w pokrewnych funkcjach. Master Agent zaadresował to post-gate (bonus comment fix).

## Cross-references

- WO: [WO-MOB-006](WO-MOB-006-rsvp-semantics-unification-desktop-mobile.md)
- Review: [REVIEW-WO-MOB-006](review_notes/REVIEW-WO-MOB-006.md)
- Parent ADR (ma zostać oznaczone RESOLVED): `decision_log.md` "Mobile `rsvp_sent` semantyka STRICT-niejsza niż desktop — accepted intentional drift" (2026-05-19)
- Nowy ADR do dopisania: `decision_log.md` "RSVP semantics unification (WO-MOB-006) — strict dla wszystkich"
- Snapshot tag: `snapshot/pre-wo-mob-006-rsvp-semantics-unification-2026-05-19` (monorepo `6f51a5a` + backend submodule `ebc2d6d`)
- Backend dependency: brak (czysto desktop SELECT semantic change)
- Mobile dependency: zero (mobile już strict od WO-MOB-002)
- Bonus discovery: scope rozszerzony z 1 do 4 funkcji (pre-implementation grep wykrył ukryte lenient drift'y wewnątrz desktop)
- Bonus comment refresh: stary komentarz w `get_participants_for_mobile()` linia 23958-23959 zaktualizowany (Contract Sync gate WARN-1 zaadresowany przez Master)
- Backend commit (po Krok 6.7): TBD
- Render auto-deploy: ~2-3 min po push backend
- Post-deploy: spot check DB query dla impact estimation + UI verify w desktop CRM dla Sylwii Baran case
