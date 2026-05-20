# IMPLEMENTATION REPORT — WO-MOB-012

**Data ukończenia:** 2026-05-20
**Powiązany WO:** [WO-MOB-012](WO-MOB-012-fix-checkin-counts-mismatch-historical-events.md)
**Powiązany bug:** [BUG-MOB-002](../bugs/BUG-MOB-002-checkin-counts-mismatch-historical-events.md) ✅ Resolved (Wątek A)

## Co zrobiono

Pure backend fix — 3 funkcje SELECT-aggregate w `pg_storage.py` zaczynają używać kanonicznego predykatu OR check-in (zgodnie z web admin `pg_storage.py:9963-9966`). Mobile aplikacja, DTO, Room, Kotlin — **bez zmian**. Brak nowego APK.

## Zmienione pliki

| Plik | Funkcja / sekcja | Opis |
|---|---|---|
| `backend/pg_storage.py:~23949` | `get_participants_for_mobile` CASE | `checked_in_at` non-NULL gdy spełniony OR-predykat |
| `backend/pg_storage.py:~24298` | `get_checkin_stats` COUNT FILTER | `checked_in`/`not_checked_in` na OR-predykacie |
| `backend/pg_storage.py:~24795` | `get_mobile_dashboard` × 4 sub-query (aggregate, by_ticket_class, timeline, top_scanners) | wszystkie agregaty na OR-predykacie |
| `backend/api/MOBILE_API.md` | sekcje `/participants` + `/checkin-stats` | doc note semantyki OR-predykatu |
| `simple-event-checkin/.agents/work_orders/WO-MOB-012-...md` | scaffold + Notatki diagnostyczne | full diagnose record |
| `simple-event-checkin/.agents/bugs/BUG-MOB-002-...md` | status → Resolved (Wątek A) | link do WO-MOB-012 |
| `simple-event-checkin/.agents/bugs/INDEX.md` | row BUG-MOB-002 | status update |

## Predykat kanoniczny (literalnie identyczny w 5 miejscach)

```sql
p.status = 'checked_in'
OR (p.data->>'checked_in') IN ('true', 'True')
OR p.attendance_status IN ('checked_in', 'present')
```

## Weryfikacja

- `py -m py_compile backend/pg_storage.py` → **PASS**
- Mobile DTO / Room / Kotlin — bez zmian (APK niepotrzebny)
- Sanity SELECT na produkcji (po fixie symulacja):
  - Dental Practice Academy Poznań: **checked_in=46** (było 0, web=46) ✅
  - AMOZ Connect Kraków: **checked_in=66** (było 66, web=66) ✅ brak regresji
- Write path (`checkin_participant_by_ticket_id`, `batch_checkin_sync`) — nietknięty
- JSONB — tylko READ (`data->>'checked_in'`), brak merge (constraint §11 zachowane)

## Definition of Done

- [x] 3 funkcje pg_storage.py + 1 doc zmienione
- [x] `py -m py_compile backend/pg_storage.py` PASS
- [x] Predykaty literalnie identyczne (constraint: spójność z `pg_storage.py:9963-9966`)
- [x] `backend/api/MOBILE_API.md` zaktualizowany
- [x] Snapshot SKIPPED — user explicit decision (zanotowane)
- [x] Review note w `review_notes/REVIEW-WO-MOB-012.md`
- [x] BUG-MOB-002 oznaczony jako Resolved (Wątek A)
- [x] IMPLEMENTATION_REPORT (ten plik)

## Snapshot

**SKIPPED** — user explicit decision w prompcie Fazy 2 ("BEZ snapshot"). Rationale akceptowalne: fix defensywny (dodaje OR, niczego nie wyłącza), revertowalny pojedynczym git revert na backend submodule, brak SQL/migracji.

## Postmortem (4 pytania)

### 1. Czy ten bug mógł się powtórzyć gdzie indziej?

**TAK.** Wzorzec "agregator filtruje tylko po `p.status='checked_in'`" istnieje w innych miejscach mobile API — `backend/api/mobile.py:1176, 1846, 1921, 1927` (MyMentees, event_orders). Wymagają osobnego sprawdzenia (user-visible? admin-only? potencjalny undercount?). **Propozycja: WO-MOB-014** — sprzątnięcie wszystkich pokrewnych miejsc do kanonicznego OR-predykatu.

### 2. Co nas zaskoczyło?

`checkin_log` jest **niekompletny** — Dental ma 17 rows dla 46 odznaczonych, AMOZ 50 dla 66. Web admin manual check-in pisze tylko do `data.checked_in` + ewentualnie `status='checked_in'`, ale NIE do `checkin_log`. Jeśli ktoś kiedyś będzie budował raporty z `checkin_log` ("kto skanował kogo") — dostanie podzbiór rzeczywistości. Wpis do `known_gotchas.md`.

### 3. Jaką regułę warto utrwalić?

**ADR:** kanoniczny predykat OR-3 dla check-in agregatów (read path). **Gotcha:** dual-source `is_checked_in` + `checkin_log` jako audit-only, nie source-of-truth dla agregatów. Treści propozycji w sekcji "Propozycje wpisów" poniżej.

### 4. Czy są nowe ryzyka bezpieczeństwa?

**NIE.** Fix to czysto read-aggregation SQL. Bez nowych endpointów, bez auth, bez upload, bez PII (predykat operuje na istniejących polach, nie zmienia tego co wraca do klienta — tylko `checked_in_at` zmienia się z NULL na timestamp dla wcześniej-undercount'owanych uczestników). Write path nietknięty. JSONB tylko READ.

## Propozycje wpisów

### `decision_log.md`

```markdown
## ADR-2026-05-20: Kanoniczny predykat check-in (OR-3) jako single source of truth dla agregatów

**Kontekst:** Historycznie istniały 3 ścieżki zapisu stanu check-in: (1) `participants.status='checked_in'`, (2) `participants.data->>'checked_in'='true'`, (3) `participants.attendance_status IN ('checked_in','present')`. Web admin (`pg_storage.py:9963-9966`) liczył OR; mobile liczył tylko (1). Undercount dla historycznych eventów (BUG-MOB-002, Dental: 0 vs 46).

**Decyzja:** Każda funkcja read/aggregate dotykająca licznika check-in MUSI używać predykatu:
`p.status='checked_in' OR (p.data->>'checked_in') IN ('true','True') OR p.attendance_status IN ('checked_in','present')`.

**Write path nie zmienia się** — pisze dalej `status='checked_in'` + `data.checked_in='true'` + `checkin_log`. OR jest defensywny — zero regresji gdy write ustawia oba.

**Konsekwencje:** Spójność mobile↔web. Niejednorodność danych historycznych nie zniknie — predykat OR akceptuje wszystkie 3 ścieżki na zawsze (do ewentualnej migracji normalizującej; poza zakresem).
```

### `known_gotchas.md`

```markdown
## Check-in state: dual-source `is_checked_in` + niekompletny `checkin_log`

**Problem:** Stan "uczestnik odznaczony" zapisany w 3 miejscach niespójnie:
- `participants.status='checked_in'` (mobile scan, CSV import)
- `participants.data->>'checked_in'='true'` (web admin manual, legacy)
- `participants.attendance_status IN ('checked_in','present')` (RSVP/Backstage)

Web admin liczy OR; mobile (przed WO-MOB-012) tylko status. Undercount historic.

**Pułapka:** Pisząc nowy aggregator NIE filtruj samego `status='checked_in'` — użyj kanonicznego OR-predykatu z `pg_storage.py:9963-9966`.

**Bonus:** `checkin_log` jest niekompletny — web admin manual check-in NIE pisze do log. Dental: 17/46, AMOZ: 50/66 (prod data 2026-05-20). Używaj `checkin_log` tylko jako audit log skanów (kto, kiedy, urządzenie), NIE jako source-of-truth dla "ilu odznaczonych".
```

## Co dalej

1. Deploy Render auto z `master` (~2-3 min po push)
2. **Test akceptacyjny na żywym urządzeniu:**
   - Mobile: Dental → POSTĘP CHECK-IN ≈94% (46/49)
   - Mobile: AMOZ → bez zmian (66/107)
3. **Follow-up WO:**
   - WO-MOB-013: Wątek B (totals filter, decyzja biznesowa attendance_status filter)
   - WO-MOB-014: pokrewne `mobile.py:1176/1846/1921/1927` undercount cleanup
