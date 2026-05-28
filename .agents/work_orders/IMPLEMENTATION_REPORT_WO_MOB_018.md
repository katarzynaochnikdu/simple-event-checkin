# IMPLEMENTATION REPORT — WO-MOB-018

**Sandbox events inline z pomarańczowym pillem „SANDBOX" (dev-only), usunięcie zakładki Sandbox**

**Data:** 2026-05-28
**Worker:** worker-implementer (dispatch przez Master)
**Status:** ✅ DONE — build PASS, working tree (NIE commitowane, NIE pushowane)
**Snapshot:** POMINIĘTY na życzenie usera. Rollback: `9ef6476` (WO-MOB-017).

---

## Co zrobiono
Zmiana podejścia do sandbox events na liście wydarzeń (follow-up WO-MOB-017):
1. Usunięto osobną zakładkę „Sandbox" (`EventTab` → `{ ONGOING, UPCOMING, PAST }`).
2. Sandbox events wpadają **inline** do normalnych grup wg daty.
3. Karta sandbox eventu = **pomarańczowy pill „SANDBOX"** (`#F97316`, biały tekst, w wierszu nazwy).
4. **Dev-only:** `sourceEvents = if (BuildConfig.DEBUG) raw else raw.filter { !isSandbox(it) }` — non-dev nie widzi sandbox NIGDZIE.

## Zmienione pliki (2, +64/-27 LOC)
| Plik | Zmiana |
|---|---|
| `features/feature-events/.../viewmodel/EventsViewModel.kt` | `EventTab` -SANDBOX; dev-gate `sourceEvents` raz na wejściu; per-tab `when` (3-wart., wyczerpujący) bez `isSandbox`; `anyOngoing`/search/`visibleTabs` na `sourceEvents`; usunięta prywatna `isSandbox`, import top-level z `...presentation.screen`. |
| `features/feature-events/.../screen/EventsScreen.kt` | usunięty label „Sandbox"; top-level `fun isSandbox(EventItem)`; `@Composable SandboxPill()` (orange-500); nazwa w `EventCompactCard` opakowana w `Row` z `weight(1f, fill=false)` + warunkowy pill. |

**Bez zmian:** backend/API/model/DTO, `build.gradle.kts` (`buildConfig=true` z WO-MOB-017), klasyfikacja dat z WO-MOB-017, `EventFullWidthCard`.

## Build / weryfikacja
- `./gradlew assembleDebug` → **BUILD SUCCESSFUL in 3m14s**, exit 0 (570 tasks, 26 executed).
- `:features:feature-events:compileDebugKotlin` ✅ (nowy kod zero warningów).
- 1 warning pre-existing poza zakresem: `EventsScreen.kt` `EventFullWidthCard` `if (event.status != null)` always true.

## Bramki
- **QA:** code-level PASS (5 scenariuszy) + build PASS. On-device deferred do usera.
- **Security:** PASS inline (klient-side; dev-gate = ukrywanie UI, nie autoryzacja; dane z API bez zmian). Zero endpointów/API/auth/PII.
- **Contract Sync:** SKIPPED. **Migration:** N/A.

---

## Postmortem (4 pytania)

**1. Coś nieoczywistego?**
TAK — `Modifier.weight(1f, fill=false)` na nazwie wymaga `RowScope` (nazwa musi żyć w `Row` z pillem); poza `Row` → compile error. Oraz: top-level `isSandbox` w pliku screen importowany przez VM (precedens `parseToDateTime`) = jedno źródło prawdy, bez duplikacji.

**2. Dług techniczny?**
Minimalny. Heurystyka `isSandbox` (`name.contains("test")`) jest szeroka — realny event z „test" w nazwie zostanie ukryty dla non-dev. Pre-existing; do ewentualnego zawężenia osobnym WO. Mobile nadal bez harness testowego (wspólny follow-up z WO-MOB-017). Snapshot pominięty (świadoma decyzja usera).

**3. Security-critical?**
NIE. Dev-gate `BuildConfig.DEBUG` ukrywa sandbox events w release UI, ale to nie jest granica bezpieczeństwa (API zwraca te dane niezależnie od buildu — stan bez zmian względem przed-WO). Brak nowych endpointów/auth/PII/secrets.

**4. Follow-up?**
- On-device QA (dev: pill inline; release: niewidoczny).
- (Opcjonalnie) zawęzić `isSandbox` (status=="draft" lub flag z API) — osobny WO.
- Commit + push — czeka na user go-ahead.
- Promocja gotcha/ADR do `known_gotchas.md` / `decision_log.md`.
