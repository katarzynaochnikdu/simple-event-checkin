# REVIEW — WO-MOB-018: Sandbox inline + pomarańczowy pill (dev-only)

**Data review:** 2026-05-28
**Reviewer:** Master Agent
**Worker:** worker-implementer
**Snapshot:** POMINIĘTY na życzenie usera („rób bez snapshota"). Rollback dostępny: commit `9ef6476` (WO-MOB-017) + tag `snapshot/pre-mobile-events-ongoing-tab-2026-05-28`.

---

## Definition of Done — weryfikacja

| # | Kryterium | Status | Dowód |
|---|---|---|---|
| 1 | `EventTab` = `{ ONGOING, UPCOMING, PAST }` (bez SANDBOX); brak zakładki „Sandbox" w UI | ✅ | enum + usunięty label `EventTab.SANDBOX -> "Sandbox"` |
| 2 | Non-dev: sandbox events nieobecne wszędzie (grupy/liczniki/anyOngoing) | ✅ | `sourceEvents = if (isDev) raw else raw.filter { !isSandbox(it) }` (dev-gate na wejściu) |
| 3 | Dev: sandbox events inline w grupie wg daty | ✅ | per-tab filtry bez `!isSandbox`; sandbox klasyfikowane jak każde |
| 4 | Karta sandbox eventu ma pomarańczowy pill „SANDBOX"; non-sandbox bez pilla | ✅ | `if (isSandbox(event)) SandboxPill()` w `EventCompactCard`; `Color(0xFFF97316)` |
| 5 | Per-tab filtry nie wykluczają sandbox; dev-gate raz na wejściu | ✅ | `when(effectiveTab)` bez `isSandbox`; gate w `sourceEvents` |
| 6 | `isSandbox` jako pojedyncza top-level funkcja (VM + karta) | ✅ | top-level w `EventsScreen.kt`; VM importuje; usunięta prywatna kopia |
| 7 | Build `assembleDebug` PASS | ✅ | `BUILD SUCCESSFUL in 3m14s`, exit 0 |
| 8 | Review note | ✅ | ten plik |

**DoD: 8/8 spełnione.** ✅

---

## Weryfikacja logiki (trace)
- **Non-dev (release/preview):** `sourceEvents` nie zawiera sandbox → `filtered`, `anyOngoing`, `visibleTabs`, `totalActiveEvents` ich nie obejmują. Sandbox events **całkowicie niewidoczne**. ✅ (wymaganie „nikt poza deweloperami").
- **Dev (debug):** `sourceEvents = raw` → sandbox event klasyfikowany po dacie (`isOngoing/isUpcoming/isPast`) i pokazany w odpowiedniej grupie; karta renderuje pomarańczowy pill. ✅
- **Sandbox ongoing dziś (dev):** `anyOngoing = true` → zakładka „Trwające" obecna, event w niej z pillem. Dla non-dev ten sam event odfiltrowany → jeśli był jedynym ongoing, brak „Trwające". ✅
- **`when` exhaustywność:** enum 3-wartościowy, oba `when` (label w `EventsScreen`, filtr w VM) pokrywają ONGOING/UPCOMING/PAST bez `else`. ✅
- **`Row` + `weight(1f, fill=false)`:** nazwa ellipsizowana, pill zawsze widoczny (priorytet). ✅

## Bramki (Step 4.5)
| Gate | Wynik | Uzasadnienie |
|---|---|---|
| **QA** | ✅ PASS (code-level + build) | 5 scenariuszy z WO prześledzonych; `assembleDebug` PASS. On-device deferred do usera. |
| **Security** | ✅ PASS (inline) | Czysto kliencka prezentacja + filtrowanie listy. Dev-gate `BuildConfig.DEBUG` to mechanizm *ukrywania UI*, NIE granica autoryzacji (dane z API przychodzą niezależnie od buildu — bez zmian względem stanu przed WO). Zero nowych endpointów/API/auth/PII. |
| **Contract Sync** | ⏭️ SKIPPED | Zero zmian typów/API/DTO. |
| **Migration** | ⏭️ N/A | Zero SQL. |

## Decyzja (do `decision_log.md`)
**ADR-WO-MOB-018** — supersedes część WO-MOB-017: zamiast osobnej zakładki „Sandbox" → sandbox events inline w grupach z pomarańczowym pillem, dev-gated raz na wejściu. Powód: user explicit. Dev-gating nadal `BuildConfig.DEBUG`.

## Gotcha (do `known_gotchas.md`)
- `Modifier.weight(1f, fill=false)` na nazwie eventu wymaga `RowScope` — nazwa MUSI być w `Row` (z pillem). Poza `Row` → compile error.
- `isSandbox` to JEDNO top-level źródło prawdy (VM + karta) — nie duplikować prywatnej kopii w VM.
- Detekcja `name.contains("test")` ukrywa też realne eventy z „test" w nazwie dla non-dev (pre-existing; zawęzić w osobnym WO jeśli problem).

## Follow-up
- On-device QA (dev build: sandbox z pillem inline; release: niewidoczny).
- (Opcjonalnie) zawęzić heurystykę `isSandbox` (np. tylko `status=="draft"` lub dedykowany flag z API) — osobny WO.
- Test infra mobile (JUnit) — wspólny follow-up z WO-MOB-017.
