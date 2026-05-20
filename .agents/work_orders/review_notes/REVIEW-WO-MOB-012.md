# REVIEW — WO-MOB-012

**Data:** 2026-05-20
**Reviewer:** Master Agent
**Status DoD:** ✅ PASS

## Definition of Done — checklist

| Kryterium | Status |
|---|---|
| 3 funkcje pg_storage.py + 1 doc zmienione | ✅ |
| `py -m py_compile backend/pg_storage.py` PASS | ✅ |
| Predykaty literalnie identyczne (5 wystąpień) | ✅ |
| `backend/api/MOBILE_API.md` zaktualizowany | ✅ |
| Constraints zachowane (§3 plik 868KB ostrożnie, §6 jedno WO, §9 brak Unicode replace, §11 JSONB READ-only, §14 brak Render env) | ✅ |
| Review note (ten plik) | ✅ |
| BUG-MOB-002 → Resolved (Wątek A) | ✅ |
| IMPLEMENTATION_REPORT z postmortem | ✅ |
| Snapshot | ⏭️ SKIPPED — user explicit decision |

## Weryfikacja korektności

**Sanity SELECT** na produkcji (po symulacji nowego predykatu OR):
- Dental Practice Academy Poznań: 46 checked-in (web=46) ✅
- AMOZ Connect Kraków: 66 checked-in (brak regresji vs 66 baseline) ✅

**Compile:** `py -m py_compile backend/pg_storage.py` PASS.

## Decyzje architektoniczne (ADR — propozycja do `decision_log.md`)

ADR-2026-05-20: Kanoniczny predykat check-in (OR-3) jako single source of truth dla agregatów. Treść w IMPLEMENTATION_REPORT.

## Gotchas (propozycja do `known_gotchas.md`)

1. Check-in state: dual-source `is_checked_in` + niekompletny `checkin_log`.

## Follow-up WO

- **WO-MOB-013** — Wątek B (różny filtr "łącznie uczestników" mobile vs web). Wymaga decyzji biznesowej.
- **WO-MOB-014** — sprzątnięcie pokrewnych miejsc `backend/api/mobile.py:1176, 1846, 1921, 1927` do kanonicznego OR-predykatu (MyMentees / event_orders).

## Risk assessment

- **Regresja:** brak — predykat OR jest defensywny (rozszerza, nie zawęża). AMOZ baseline 66 zachowany.
- **Security:** brak — pure read aggregation, write path nietknięty.
- **Performance:** marginalny narzut na evaluation 3-warunkowego OR per wiersz; bez nowych indeksów potrzebnych (filtr operuje na małym podzbiorze już ograniczonym do `event_id`).

## Commit / deploy

Backend submodule commit + push → Render auto-deploy z `master` (~2-3 min).
Mobile submodule artefakty commit + push (dokumentacja WO + bug + raporty + index).
Main repo bump submodule refs.

## Conclusion

✅ **APPROVED for commit.**
