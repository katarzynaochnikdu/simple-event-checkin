# WO-MOB-013: Fix rozbieżności liczby uczestników "łącznie" mobile vs web

**Data:** 2026-05-20
**Worker:** Implementer (autonomous, "Działaj dalej")
**Stage:** Mobile data sync — totals filter alignment
**Priorytet:** Wysoki (P1)
**Powiązany bug:** [BUG-MOB-002](../bugs/BUG-MOB-002-checkin-counts-mismatch-historical-events.md) Wątek B

## Cel

Wyrównać filtr "łącznie uczestników" w mobile do filtra panelu web "Pewni uczestnicy". Mobile musi pokazywać tych samych uczestników, których operator widzi w panelu web — żeby liczby się zgadzały.

## Kontekst (z prod SELECT-ów)

| Widok | Filtr | AMOZ Kraków | Dental Poznań |
|---|---|---|---|
| **Mobile (przed fixem)** | `o.status NOT IN ('cancelled', 'refunded')` | 107 | 49 |
| **Web "Pewni uczestnicy"** | `p.attendance_status IN ('confirmed', 'pending')` | 100 | 46 |
| **Web "Łącznie w systemie"** | (brak filtra) | 123 | 53 |

Wzajemne wykluczenie filtrów:
- AMOZ: 12 cancelled + 4 refunded orders = 16 wyciętych przez mobile (ale niektórzy z tych mają aktywny RSVP)
- Web wycina 23 z `attendance_status='cancelled'` (ale niektórzy z tych mają paid order)
- Crossover (`paid + cancelled attendance`) — 7 dla AMOZ, 2 dla Dental — to "kupili bilet ale odwołali RSVP"

## Decyzja (autonomous "Działaj dalej")

**Opcja b:** Mobile używa filtra `p.attendance_status IN ('confirmed', 'pending')` — identycznego z web "Pewni".

### Rationale

1. **Spójność operator UX:** mobile i web pokazują te same osoby, ten sam licznik
2. **Semantyka "kogo wpuszczamy":** `attendance_status='cancelled'` to **explicit signal** "nie przychodzę" — operator nie ma co ich liczyć; `attendance='confirmed'` lub `pending` (jeszcze nie odpowiedział) = potencjalny gość przy bramce
3. **Refunded order może mieć aktywny RSVP** (zmiana płatności, voucher) — dotąd mobile go ukrywał, teraz pokaże (zgodnie z web)
4. **Sanity check potwierdza:** AMOZ 100 = 100 idealnie. Dental 45 vs web 46 (offset of 1 — pojedynczy edge case `attendance_status='registered'`, akceptowalne; lepiej niż obecne 49)

### Alternatywy odrzucone (autonomicznie)

- **a) Zachować mobile 107 (paid filter)** — odrzucone, bo utrwala rozjazd vs web
- **c) Intersection (paid AND attendance active)** — odrzucone, zbyt rygorystyczne (operator może nie widzieć osoby która kupiła bilet ale ma stary RSVP cancelled)
- **d) Dwa liczniki w mobile** — UI scope creep, mobile UX prosty na bramce wystarczy

## Zakres

`backend/pg_storage.py` — 5 lokalizacji w 3 funkcjach mobile:

| Linia | Funkcja | Zmiana |
|---|---|---|
| 24031 | `get_participants_for_mobile` | filtr `o.status NOT IN (...)` → `p.attendance_status IN ('confirmed','pending')` |
| 24326 | `get_checkin_stats` | jak wyżej |
| 24390 | `get_checkin_stats` (sub-query not_checked_in) | jak wyżej |
| 24826 | `get_mobile_dashboard` aggregate | jak wyżej |
| 24868 | `get_mobile_dashboard` by_ticket_class | jak wyżej |

## Czego NIE ruszać 🛑

- ❌ `recompute_discount_code_uses` (lines 6241/6264) — używa identycznego filtra `o.status NOT IN ('cancelled','refunded')` ale dla DISCOUNT CODE accounting, nie check-in. Pozostawić bez zmian. (Initial replace_all złapał te linie omyłkowo, zostały zrewertowane.)
- ❌ Write path check-in (`checkin_participant_by_ticket_id`, `batch_checkin_sync`)
- ❌ Pokrewne `mobile.py:1176/1846/1921/1927` (osobny WO-MOB-014)
- ❌ Web admin code (poza scope)
- ❌ Hurtowe zamiany Unicode (constraint §9)

## Definition of Done ✅

- [x] 5 lokalizacji w pg_storage.py zmienione
- [x] `py -m py_compile backend/pg_storage.py` PASS
- [x] Sanity SELECT na prod: AMOZ 100 ✅, Dental 45 (vs web 46 — offset 1 acceptable)
- [x] discount code locations (6241/6264) zachowane
- [ ] Commit + push + Render deploy
- [ ] BUG-MOB-002 Wątek B → Resolved
- [ ] IMPLEMENTATION_REPORT + state update

## Test akceptacyjny

Po deploy Render (~2-3 min):
1. Mobile AMOZ Connect Kraków → ŁĄCZNIE powinno spadć z 107 na **100** (zgodne z web "Pewni uczestnicy")
2. Mobile Dental → ŁĄCZNIE z 49 na **45-46** (zgodne z web "Pewni")
3. Mobile świeży event → bez regresji (nowi uczestnicy z aktywnym RSVP są w `('confirmed','pending')`)

## Snapshot

**SKIPPED** — kontynuacja decyzji user'a z WO-MOB-012 (fix defensywny, jeden git revert wystarczy do rollback'u).

## Notatki

Bonus dla edge case Dental 45 vs 46 — to różnica jednego uczestnika z `attendance_status='registered'` lub NULL. Web prawdopodobnie używa szerszego filtra lub policzy NULLs jako "active". Dla 1 uczestnika nie warto rozszerzać predykatu — sygnalizujemy w gotcha note.
