# Work Orders INDEX — simple-event-checkin

> Rejestr Work Orderów **mobile** (Android natywny).
> Desktop WO → [.agents/work_orders/](../../../.agents/work_orders/).
>
> Dodawane przez `/mwo <opis>` (subagent `work-order-logger`, scope=mobile).
> Format pliku: `WO-MOB-NNN-<slug>.md`. Numeracja chronologiczna.

| ID | Tytuł | Worker | Status | Utworzony |
|---|---|---|---|---|
| WO-MOB-001 | Weryfikacja auth flow po security WO (WO-201/202/204) | [TBD] | Otwarty | 2026-05-19 |
| WO-MOB-002 | Backend — dodaj rsvp_sent / rsvp_response / rsvp_responded_at do mobile participants endpoint | worker-implementer | Otwarty (DoR ✅) | 2026-05-19 |
| WO-MOB-003 | Mobile — Room v8→v9 + DTO/Domain + Composable warunkowy render RSVP (depends WO-MOB-002) | worker-implementer | Otwarty (DoR ✅) | 2026-05-19 |
