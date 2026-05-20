# Bugs INDEX — simple-event-checkin

> Rejestr bugów **mobile** (Android natywny).
> Desktop bugi → [.agents/bugs/INDEX.md](../../../.agents/bugs/INDEX.md).
>
> Dodawane przez `/mbug <opis>` (subagent `bug-logger`, scope=mobile).
> Format pliku: `BUG-MOB-NNN-<slug>.md`. Numeracja chronologiczna.

| ID | Tytuł | Severity | Status | Zgłoszony | Powiązany WO |
|---|---|---|---|---|---|
| BUG-MOB-001 | Lista uczestników pusta mimo że dashboard pokazuje 93 oczekujących | P1 | ✅ Resolved (2026-05-19) | 2026-05-19 | [WO-MOB-008](../work_orders/WO-MOB-008-fix-empty-participants-list-psycopg2-comment-escape.md) |
| BUG-MOB-002 | Rozbieżność liczb check-in między aplikacją a panelem web na historycznych wydarzeniach | P1 | ✅ Resolved (2026-05-20, A+B) | 2026-05-20 | [WO-MOB-012](../work_orders/WO-MOB-012-fix-checkin-counts-mismatch-historical-events.md) + [WO-MOB-013](../work_orders/WO-MOB-013-fix-totals-mismatch-attendance-status-filter.md) |
