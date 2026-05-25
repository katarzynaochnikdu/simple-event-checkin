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
| WO-MOB-004 | Refactor — likwidacja 4-way duplication mappera Participant (DTO.toEntity + Entity.toDomain) | [TBD] | ⏳ Otwarty | 2026-05-19 |
| WO-MOB-005 | Backend SELECT extension — uzupełnij brakujące 5 pól w get_participants_for_mobile() endpoint | [TBD] | ⏳ Otwarty | 2026-05-19 |
| WO-MOB-006 | RSVP semantics unification desktop↔mobile (strict dla wszystkich, modyfikuje backend desktop) | [TBD] | ⏳ Otwarty | 2026-05-19 |
| WO-MOB-007 | Fix drift `get_all_participants` rsvp_sent — dodaj scheduled campaign OR clause (linia 10791) | [TBD] | ⏳ Otwarty | 2026-05-19 |
| WO-MOB-009 | Fix — feedback check-in (zielony/czerwony) zawsze jako top-level overlay nad tab barem | [TBD] | ⏳ Otwarty | 2026-05-19 |
| WO-MOB-010 | Fix UNDONE color (ScanError) + remove "Cofnij wejście" button from ScanResultOverlay SUCCESS | [TBD] | ⏳ Otwarty | 2026-05-20 |
| WO-MOB-011 | Bottom NavBar — wrap labela "Wydarzenie" do 2 linii (opcja A, accept icon shift) | Master | ✅ DONE | 2026-05-20 |
| WO-MOB-014 | Sync button — odswiezanie listy uczestnikow w cache wydarzenia (manual refresh) | Master + worker-implementer | ✅ DONE | 2026-05-25 |
| WO-MOB-015 | Reczny check-in prelegentow (bez QR) w mobile | [TBD] | ⏳ Otwarty | 2026-05-25 |
