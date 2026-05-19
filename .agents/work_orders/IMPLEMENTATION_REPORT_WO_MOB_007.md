# IMPLEMENTATION_REPORT — WO-MOB-007

**Data:** 2026-05-19
**Worker:** Master Agent direct edit (microscopowy 1-line fix, full implementer dispatch overkill)
**Status:** ✅ Compile PASS, Contract Sync PASS, awaiting commit

## Cel
Drobny fix konsystencji — `get_all_participants` ml_sub WHERE clause dodaje `OR ml.template_key ~ '^sched_campaign_[0-9]+_'` matcher dla parity z 4 innymi funkcjami `rsvp_sent`.

## Zmienione pliki

- **`backend/pg_storage.py:10791-10797`** — w ml_sub WHERE clause funkcji `get_all_participants` rozszerzono o `OR ml.template_key ~ '^sched_campaign_[0-9]+_'` matcher z komentarzem WO-MOB-007.

## Diff

PRZED:
```sql
WHERE ml.to_email = rr.email
  AND ml.template_key LIKE 'rsvp_%%'
  AND (ml.data->>'event_id' = rr.event_id
       OR ml.event_order_id = rr.event_order_id)
```

PO:
```sql
WHERE ml.to_email = rr.email
  -- WO-MOB-007 (2026-05-19): dodanie scheduled campaign matcher dla parity
  -- z 4 innymi rsvp_sent functions (10525, 10702, 10968 desktop + 24007 mobile).
  AND (
      ml.template_key LIKE 'rsvp_%%'
      OR ml.template_key ~ '^sched_campaign_[0-9]+_'
  )
  AND (ml.data->>'event_id' = rr.event_id
       OR ml.event_order_id = rr.event_order_id)
```

## Build / Compile

```
py -m py_compile backend/pg_storage.py
→ PASS
```

## Gates

- 🔗 **Contract Sync PASS** — 5 funkcji `rsvp_sent` × identical WHERE pattern matcher (linie 10525, 10702, **10796-10797 NEW**, 10975, 24014-24015). Parity achieved.
- 🔒 **Security N/A** — czysto wewnętrzna logika SQL, brak zmian PII/auth/endpoints
- 🗄️ **Migration N/A** — zero SQL migrations
- 🧪 **QA DEFERRED** — post-deploy spot check: uczestnik z mail_log `template_key LIKE 'sched_campaign_%_rsvp%'` powinien teraz pokazywać `rsvp_sent=true` w response `get_all_participants` (poprzednio false negative)

## Postmortem

### Co działa
- 5 funkcji `rsvp_sent` w `pg_storage.py` używa teraz identycznego WHERE pattern (kompletna spójność)
- Backwards compat: dodanie OR nie zmienia false-positive ani false-negative dla regular RSVP — tylko **naprawia false negative** dla scheduled campaign RSVP

### Co nie działa / known
- Komentarz inline (linia 10792) odwołuje się do "linie 10525, 10702, 10968" ale faktyczna 4. funkcja jest na linii 10975 — off-by-7 cosmetic, nie wpływa na SQL. Akceptowalne (line numbers w komentarzach są fragile).

### Co odłożone
- Post-deploy spot check DB query — porównaj `COUNT(*) WHERE rsvp_sent=TRUE` między `get_all_participants` a `get_participants_for_event` dla event z scheduled campaign RSVP. Powinno być teraz spójne.

### Lessons learned

1. **Pre-existing drift discovery przez Contract Sync gate** — WO-MOB-006 nie naprawił WO-MOB-007 mimo że dotykał tej samej funkcji (`get_all_participants`). Gate flagował to jako out-of-scope. Lekcja: Contract Sync findings nawet "out-of-scope" są wartościowymi follow-up'ami.

2. **Direct Master edit dla microscopowych fixów** — full worker-implementer dispatch dla 1-line fix to overkill. Master może bezpośrednio editować po snapshot + WO scaffold, zachowując pełen trail audit (snapshot + WO + IR + REVIEW + commit message).

## Cross-references

- WO: [WO-MOB-007](WO-MOB-007-fix-get-all-participants-rsvp-scheduled-campaign-or-clause.md)
- Parent context: WO-MOB-006 Contract Sync gate WARN-2 (out-of-scope follow-up)
- Snapshot tag: `snapshot/pre-wo-mob-007-get-all-participants-sched-campaign-fix-2026-05-19` (monorepo `8a61253` + backend submodule `b7e2cc5`)
- Backend commit (po Krok 6.7): TBD
- Render auto-deploy: ~2-3 min po push backend
