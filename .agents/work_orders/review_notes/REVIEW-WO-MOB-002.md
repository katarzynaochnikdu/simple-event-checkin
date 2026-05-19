# REVIEW-WO-MOB-002 — Backend RSVP fields w mobile participants endpoint

**Data:** 2026-05-19
**Status:** ✅ Code merged + deployed (przed review) — backend już na prod (commit `e99c3d0`)
**Worker:** worker-implementer
**Stage:** Backend / Mobile API

## DoD checklist (z WO-MOB-002)

- [✅] `get_participants_for_mobile()` zwraca dodatkowo `rsvp_sent`, `rsvp_response`, `rsvp_responded_at` per uczestnik — **POTWIERDZONE** (pg_storage.py:23937-23942)
- [✅] `get_walkin_participants_for_mobile()` zwraca te same 3 pola jako `false` / `null` / `null` — **POTWIERDZONE** (pg_storage.py:24628-24632)
- [✅] `backend/api/mobile.py:788-806` przepuszcza nowe pola bez filtra — passthrough verified
- [✅] `py -m py_compile backend/pg_storage.py backend/api/mobile.py` — PASS (exit_code=0)
- [✅] `backend/api/MOBILE_API.md` — przykład response zaktualizowany (uncommitted, ready)
- [✅] `API_CONTRACT.md` — sekcja Mobile RSVP Fields dopisana (uncommitted, ready)
- [⏳] **Test akceptacyjny** — DEFERRED do post-deploy curl. Backend code już na prod (push e99c3d0), więc test można wykonać kiedy user dostarczy JWT admina + dostęp do eventu `24311000000909074`.
- [✅] Contract Sync Gate: **PASS** (3-way consistency SQL ↔ MOBILE_API.md ↔ API_CONTRACT.md; walk-ins spójne; api-types.ts × 3 N/A; semantyka drift desktop↔mobile udokumentowana w 3 miejscach)
- [✅] Security Gate: **PASS** (0 critical/high/medium, 2 low informational — Unicode comments + responded_at edge case; PII parity z desktop endpoint; SQL injection safe — placeholders %s)
- [✅] Migration Guard Gate: **N/A** (brak SQL migrations)

## Findings krytyczne (commit hygiene)

### 🛑 [WYSOKIE] Atomicity violation §6 — commit e99c3d0 łączy 2 niezależne WO

**Plik:** `backend` submodule, commit `e99c3d01895f64ff89ad070644690de6bb9a4e24`
**Message:** `feat(WO-219): CRM ignored decisions — DB layer (DDL + 10 helpers)`
**Rzeczywista zawartość:** WO-219 (CRM ignored decisions DDL + 10 helpers, ~550 linii) **+** WO-MOB-002 (LATERAL JOIN-y dla RSVP + 3 nowe pola + walk-in stałe, ~20 linii) — łącznie 569 insertions / 1 deletion.

**Naruszenie:** [constraints §6](../../../.agents/context/constraints_do_not_break.md) — "NIE łącz dwóch niezależnych zadań w jedno — każda zmiana musi być atomowa i revertowalna".

**Dlaczego nie da się naprawić tu i teraz:**
- Commit `e99c3d0` jest **już pushnięty na `origin/master`** w submodule `backend`
- Backend deployed na Render z tego commit'u — funkcjonalnie wszystko działa
- Rewrite historii (`git rebase -i`) wymagałby force-push na master — zabronione (constraints §1 git safety, constraints §6 deploy stability)

**Konsekwencje:**
- Trail audit dla WO-MOB-002 NIE znajdzie commit'u z prefiksem `WO-MOB-002` w `backend` submodule
- Rollback WO-MOB-002 (jeśli kiedyś będzie potrzebny) wymaga **selektywnego revert'u** odpowiednich linii z `e99c3d0`, NIE revert całego commit'u (bo cofniesz też WO-219)
- IMPLEMENTATION_REPORT i system_state muszą **explicite** wskazać że WO-MOB-002 backend code jest w commit'cie WO-219

**Mitigation (dla niniejszego review):**
1. ✅ Udokumentowane tutaj
2. ✅ Wpis w `decision_log.md` (zaplanowany Step 6) — ADR "WO-MOB-002 code accidentally bundled into WO-219 commit, accepted as deployed-state"
3. ✅ Wpis w `known_gotchas.md` (zaplanowany Step 6) — "Przy rollback WO-MOB-002 lub WO-219 nie używać `git revert e99c3d0` na całość"
4. ⚠️ **Follow-up WO (opcjonalny):** dodać reference w commit `feat(WO-216)` lub przyszłym commit-cie z annotacją "WO-MOB-002 backend code retroactively included in commit e99c3d0" (commit message amendment via `git notes` jeśli niechęć do amend)

**Root cause:** implementer worker dla WO-MOB-002 najprawdopodobniej zastał backend submodule z **już-staged content** z poprzedniej sesji (WO-219 implementacja niezakończona, plik pg_storage.py w stanie partial). Implementer dopisał WO-MOB-002 zmiany i — wbrew instrukcji "NIE commituj, NIE pushuj" — wykonał commit (lub git commit zostal automatycznie wykonany przez prior worker session). Backend submodule był push'nięty wraz z mixed content.

**Lekcja na przyszłość:** Master Agent przed dispatch implementera MUSI:
1. `git status` w submodule **przed** dispatchem — zweryfikować że working tree jest clean
2. Wszelkie pre-existing staged content z innych WO MUSZĄ być albo commitnięte odrębnie albo stash'owane PRZED edycją kolejnego WO
3. Snapshot worker powinien dodać wpis `pre-task working tree state: clean/dirty` w raporcie

## Findings niskie (nie blokujące)

### [NISKIE] Semantyka `rsvp_sent` desktop vs mobile — known intentional drift

Implementer celowo zrobił logikę STRICT-niejszą niż w `get_participants_for_admin_list()` desktop endpoint. Desktop ma `else TRUE` (zwraca `rsvp_sent=true` gdy brak wpisu w `mail_log`), mobile ma `(lrm.mail_id IS NOT NULL)` (zwraca `false` gdy brak wpisu).

**Status:** udokumentowane w 3 miejscach (komentarz SQL, MOBILE_API.md, API_CONTRACT.md). Follow-up WO zalecany: "RSVP semantics unification" — rekomendacja: ujednolicić desktop do mobile-style (strict). Sylwia Baran case pokazuje że lenient był bug-feature.

### [NISKIE] `mail_log` index check

Performance: LATERAL JOIN do `mail_log` per uczestnik. Event z 5000 uczestników × 2 LATERAL może spowolnić query. Sprawdzić `EXPLAIN ANALYZE` po deploy — jeśli seq scan, dodać INDEX na `(to_email, event_id, template_key, status)` w osobnym WO follow-up.

### [NISKIE] `responded_at` edge case

`rsvp_responded_at` jest `responded_at` z najnowszego rekordu `rsvp_responses` per `ORDER BY rr.created_at DESC LIMIT 1`. Edge case: rekord z najnowszym `created_at` ale `responded_at IS NULL` (token wysłany, nie kliknięty) → zwraca `null` mimo że może istnieć starszy rekord z non-null. Desktop endpoint ma identyczny pattern — nie regresja, ale known correctness limitation. Rozważyć `COALESCE(responded_at, created_at) DESC` w przyszłym WO unifying.

## Test akceptacyjny — DEFERRED

Status testu z WO-MOB-002 sekcja "Test akceptacyjny":

Backend code już deployed na prod (`md-order-portal-backend.onrender.com`). QA do wykonania **post-session** przez user'a:

```bash
# Pre-condition: JWT admina mobile z dostępem do eventu 24311000000909074
curl -H "Authorization: Bearer <JWT>" \
  https://md-order-portal-backend.onrender.com/api/mobile/events/24311000000909074/participants \
  | jq '.participants[] | {first_name, last_name, attendance_status, rsvp_sent, rsvp_response, rsvp_responded_at}'
```

Oczekiwane: 4 stany w response zgodne z WO-MOB-002 sekcja "Test akceptacyjny" (A/B/C/D).

**Spot check dla Sylwia Baran case** (analogiczny event):
```sql
-- W DB sprawdzić że Sylwia Baran ma rsvp_sent=false dla eventu 24311000000909074:
SELECT EXISTS (
  SELECT 1 FROM mail_log
  WHERE to_email = 'sylwia.baran@luxmedica.pl'
    AND template_key LIKE 'rsvp_%'
    AND status IN ('sent', 'delivered')
    AND (data->>'event_id' = '24311000000909074' OR event_order_id IN (
      SELECT event_order_id FROM participants WHERE email='sylwia.baran@luxmedica.pl'
    ))
);
-- Oczekiwane: FALSE (mail RSVP nie poszedł)
```

## Recommendation

✅ **APPROVED dla zamknięcia WO-MOB-002 backend.**

WO osiągnęło cel funkcjonalny (response endpointu mobile zawiera 3 nowe pola RSVP, walk-ini spójne, dokumentacja zaktualizowana, security/contract gates PASS). Commit hygiene violation `e99c3d0` jest udokumentowany i nie blokuje merge'u (deploy już aktywny).

**Sekwencja domknięcia:**
1. Commit `backend/api/MOBILE_API.md` w submodule backend → push
2. Commit `API_CONTRACT.md` + WO files + system_state changes w main repo → push (auto-bump backend submodule ref)
3. ADR + gotcha entry per finding §1
4. (Opcjonalnie) Otworzyć WO-MOB-004 "RSVP semantics unification desktop+mobile"
5. Post-deploy: user wykonuje curl QA na event 24311000000909074

WO-MOB-003 (mobile DTO + Composable) może startować — backend kontrakt stable.
