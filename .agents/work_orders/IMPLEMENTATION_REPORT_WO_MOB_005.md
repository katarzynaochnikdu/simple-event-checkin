# IMPLEMENTATION_REPORT — WO-MOB-005

**Data:** 2026-05-19
**Worker:** worker-implementer (backend)
**Status:** ✅ Code complete, compile PASS, both gates PASS, awaiting commit + post-deploy QA curl

## Cel
Uzupełnij 5 brakujących pól w response mobile participants endpoint — naprawienie pre-existing drift backend↔DTO (flagowany przez Contract Sync gate WO-MOB-004).

## Zmienione pliki

- **`backend/pg_storage.py:23914-23937`** — `get_participants_for_mobile()` SELECT block: dodano 5 pól (`p.phone`, `CASE WHEN ... ARRAY[participant_tag] ELSE NULL END AS tags`, `FALSE AS is_walkin`, `NULLIF(TRIM(CONCAT_WS...))` AS buyer_name, `o.purchaser_email AS buyer_email`). **ZERO new JOINs** — wszystko z już-JOINed `participants p` + `orders o`.
- **`backend/pg_storage.py:24638-24665`** — `get_walkin_participants_for_mobile()`: dodano explicit `d["tags"] = None`, `d["buyer_name"] = None`, `d["buyer_email"] = None` w pętli rows. `phone` (z `w.phone`) i `is_walkin=True` już ustawione w pre-existing kodzie.
- **`backend/api/mobile.py:788-806`** — bez zmian (pass-through `jsonify`, brak whitelist'u pól).
- **`backend/api/MOBILE_API.md`** — +21 linii: zaktualizowany przykład response + nowa sekcja "Pola Buyer & Tags (WO-MOB-005)" z tabelą 5 pól + decyzja Opcja A dla `tags` + walk-in parity note.
- **`API_CONTRACT.md`** (root) — +25 linii: nowa sekcja "Mobile Participants — Buyer & Tags Fields (WO-MOB-005)" z tabelą kontraktu, decyzją cardinality, backwards compat note, walk-in section.

## Diff stats

Backend submodule: 2 files changed, +54 insertions, +0 deletions (`pg_storage.py` +33, `MOBILE_API.md` +21).
Main repo: 1 file changed, +25 insertions, +0 deletions (`API_CONTRACT.md`).

## Decyzja UX (z `/master WO-MOB-005`)

**`tags` cardinality mismatch:** mobile DTO `List<String>?` vs backend SINGLE `participants.participant_tag`.
**Wybrana Opcja A:** Backend wraps single tag w 1-elementową PostgreSQL array (`ARRAY[p.participant_tag]`), mobile DTO bez zmian. Future-proof pod multi-tag w Stage 5+ plan_participant_tagging.

## Build / Compile

```
py -m py_compile backend/pg_storage.py backend/api/mobile.py
→ COMPILE_OK (oba pliki PASS)
```

## Kluczowe fragmenty diff

**`pg_storage.py` — SELECT additions (między `event_order_id` a `ticket_name`):**
```sql
                p.event_order_id,
                -- WO-MOB-005: 5 dodatkowych pól dla mobile parity z desktop endpoint.
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
                tc.ticket_name,
```

**`pg_storage.py` — walk-in additions w pętli rows:**
```python
            d["rsvp_response"] = None
            d["rsvp_responded_at"] = None
            # WO-MOB-005: contract parity z regular endpoint.
            d["tags"] = None
            d["buyer_name"] = None
            d["buyer_email"] = None
            result.append(d)
```

## Gates

| Gate | Status | Klucz finding |
|---|---|---|
| 🔒 Security | **PASS** | 0 Crit/High/Med, 1 niskie informational (walk-in explicit None — self-documenting). **PII desktop parity 100% potwierdzona** — wszystkie 5 pól już w `get_participants_for_event()`. SQL injection N/A. Performance N/A (zero new JOINs). Error path clean. |
| 🔗 Contract Sync | **PASS** | 5 pól × 7 warstw spójne. **Drift z WO-MOB-004 RESOLVED.** `ARRAY[participant_tag]` → JSON `["prelegent"]` verified (psycopg2 + jsonify). |
| 🗄️ Migration Guard | **N/A** | Zero SQL migrations. |
| 🧪 QA | **DEFERRED** | Post-deploy curl na event `24311000000909074`. |

## Postmortem (4 pytania)

### Co działa

- Wszystkie 5 pól zwracane przez backend (regular endpoint + walk-in endpoint z odpowiednią semantyką).
- Mobile DTO/Entity/Domain/mappery (od WO-MOB-002/003/004) konsumują pola bez zmian.
- Backwards compat: additive change, pre-WO-MOB-003 klienci ignorują, post-WO-MOB-003 klienci dostają real values zamiast Moshi defaults.
- ZERO new JOINs — performance neutral nawet dla event 10k+ uczestników.
- Desktop parity zachowana (5 pól od dawna w admin endpoint).
- Bonus: `phone` latent bug fix z WO-MOB-004 (unified `toDomain()` zawiera phone canonical) staje się **active in production** — details screen pokaże telefon uczestnika.

### Co nie działa / known issues

- **Brak QA curl** — wymaga deploy lub running local backend. Backend code compile PASS, ale rzeczywiste behavior post-deploy weryfikowane przez user.
- **`tags` cardinality 1-element compromise** — gdy backend będzie wspierał multi-tag (Stage 5+ plan_participant_tagging) i DB schema się zmieni do many-to-many, `ARRAY[p.participant_tag]` zostanie zastąpione przez `array_agg(...)` z join'a. Pre-bezpieczne future migration path bez zmiany kontraktu mobile.
- **psycopg2 array adapter dependency** — jeśli ktoś w przyszłości doda `register_adapter(list, ...)` overriding default array reader → `tags` mógłby się szeregować jako `"{prelegent}"` zamiast `["prelegent"]`. Obecnie zero overrides w repo (grepable), ale runbook hygiene to know.

### Co odłożone

- **Post-deploy curl QA** na event `24311000000909074` — weryfikacja 5 stanów (uczestnik z phone, z participant_tag, walk-in z is_walkin=true, buyer fields, edge case bez orders).
- **WO-MOB-006 (opcjonalnie)** — Mobile UI dla buyer info: jeśli operator wejściowy ma widzieć "kupujący to inna osoba niż uczestnik" w details screen, dodać UI binding (obecnie pola są w DTO/Entity/Domain ale UI ich nie używa).
- **Spot check `EXPLAIN ANALYZE`** dla event 5000+ uczestników (defensive performance check pre-prod).
- **WO follow-up dla constraints_do_not_break.md** — dodać regułę o desktop-mobile parity dla list PII fields (formalizuje implicit pattern).
- **WO follow-up dla `decision_log.md`** — ADR formalny dla Opcja A `tags` cardinality (kontekst + alternatywy + konsekwencje).

### Lessons learned

1. **Pre-existing drift maskowany przez Moshi defaults** — najlepsza pułapka: client-side defaults `null`/`false`/`emptyList()` ukrywały brak field'u w response przez **3 kolejne WO** (WO-MOB-002 → WO-MOB-003 → WO-MOB-004). Tylko Contract Sync gate WO-MOB-004 explicit per-field consistency check ujawnił drift. **Rule:** Contract Sync gate powinien być MANDATORY dla każdego WO zmieniającego mobile DTO/API kontrakt, nie tylko gdy implementer flag'uje.

2. **Walk-in endpoint NIE jest SELECT-symetryczny z regular** — pola "biznesowo niemożliwe" dla walk-in (`tags`, `buyer_*`) NIE są w jego SQL (różna tabela `mobile_walkin_participants`). Implementer wybrał explicit `d["field"] = None` w Python — **self-documenting contract**. Lepsze niż polegania na Moshi defaults (które są runtime mechanism, nie kontrakt). Lekcja: jeśli endpointy mobile zwracają **różne źródła z tej samej** tablicy DTO, EXPLICITLY ustaw pola niemożliwe na None na backend (nie polegaj na client-side defaults).

3. **PostgreSQL ARRAY → JSON serialization jest "free"** dzięki psycopg2 default array adapter + Flask jsonify. Wrapping single column w `ARRAY[col]` daje pragmatic future-proof contract bez schema migration. Pattern do replikacji gdy mobile expects list ale backend ma single value (np. legacy single-tag systems przed multi-tag refactor).

4. **Master Agent pre-flight `git status` PASS ponownie** — trzeci WO z rzędu (WO-MOB-003, WO-MOB-004, WO-MOB-005) bez atomicity violation. Lekcja z WO-MOB-002 mixed commit incident działa jako stable pattern w workflow.

## Cross-references

- WO: [WO-MOB-005](WO-MOB-005-backend-select-extension-5-pol-mobile-participants.md)
- Review: [REVIEW-WO-MOB-005](review_notes/REVIEW-WO-MOB-005.md)
- Parent context: WO-MOB-004 Contract Sync gate WARN — drift backend↔DTO dla 5 pól, follow-up zalecany → **WO-MOB-005 = closure**
- Snapshot tag: `snapshot/pre-wo-mob-005-backend-select-5-fields-2026-05-19` (monorepo `055f68b` + backend submodule `81f1a1d`)
- Backend dependency: brak (czysto backend SELECT extension)
- Mobile dependency: zero (wszystkie warstwy mobile od WO-MOB-002/003/004 już mają fields)
- Decyzja UX: Opcja A `tags` jako `ARRAY[participant_tag]` (1-element list) — udokumentowana w WO-MOB-005 + MOBILE_API.md + API_CONTRACT.md
- Backend commit (po Krok 6.7): TBD
- Render auto-deploy: ~2-3 min po push backend submodule
- Bonus activated: `phone` latent bug fix z WO-MOB-004 staje się visible w prod (mapper canonical = list view zawiera phone, backend teraz wysyła wartość)
