# REVIEW — WO-MOB-017: Zakładka „Trwające" + fix data-granularny

**Data review:** 2026-05-28
**Reviewer:** Master Agent
**Worker:** worker-implementer
**Snapshot:** `snapshot/pre-mobile-events-ongoing-tab-2026-05-28` @ `28168cc`

---

## Definition of Done — weryfikacja

| # | Kryterium | Status | Dowód |
|---|---|---|---|
| 1 | `EventTab` rozszerzony o `ONGOING` (kolejność `ONGOING, UPCOMING, PAST, SANDBOX`) | ✅ | `EventsViewModel.kt:16` |
| 2 | Klasyfikacja data-granularna `isOngoing/isUpcoming/isPast(e, today: LocalDate)`, czyste funkcje | ✅ | `EventsViewModel.kt` helpers `startDay/endDay/isOngoing/isUpcoming/isPast` |
| 3 | `EventsUiState.visibleTabs` — ONGOING tylko gdy `anyOngoing`; SANDBOX tylko gdy `BuildConfig.DEBUG`; UPCOMING+PAST zawsze | ✅ | `combine{}` `buildList{}` |
| 4 | Domyślna zakładka = ONGOING gdy `anyOngoing`, inaczej UPCOMING; wybór usera respektowany | ✅ | `_selectedTab` nullable + `effectiveTab = tab?.takeIf { it in visibleTabs } ?: defaultTab` |
| 5 | `EventsScreen` renderuje `visibleTabs`; label ONGOING→„Trwające"; `selectedTabIndex = indexOf(...).coerceAtLeast(0)` | ✅ | `EventsScreen.kt:71-79` |
| 6 | `feature-events/build.gradle.kts` ma `buildFeatures { buildConfig = true }` | ✅ | gradle diff |
| 7 | Build `assembleDebug` PASS | ✅ | `BUILD SUCCESSFUL in 9m43s`, exit 0 |
| 8 | Reguły bezpieczeństwa zachowane (brak nowych endpointów/PII/auth) | ✅ | Security gate inline PASS |
| 9 | Review note w `review_notes/` | ✅ | ten plik |

**DoD: 9/9 spełnione.** ✅

---

## Weryfikacja logiki (trace, nie tylko build)

- **Fix błędu „dzień wydarzenia wpada w przeszłość":** event z `startDay == endDay == dziś` → `isOngoing = !today.isBefore(today) && !today.isAfter(today) = true` → **ONGOING** (nie PAST). Błąd naprawiony. Poprzednio `isPast = parseToDateTime(endDate).isBefore(now)` zwracał `true` o 00:01 (parser → 00:00).
- **Dynamiczna zakładka:** `anyOngoing` z `_rawEvents` (nie z listy po search) → „Trwające" w `visibleTabs` tylko gdy coś trwa. **Zgodne z explicit wymaganiem usera** („nie ma zakładki trwające jeśli nic aktualnie nie trwa").
- **Bezpieczeństwo indeksu:** `effectiveTab` zawsze ∈ `visibleTabs` (fallback do `defaultTab`), więc `indexOf` ≥ 0; `coerceAtLeast(0)` jako dodatkowy bezpiecznik. Brak ryzyka rozjazdu ordinal↔pozycja.
- **`EventsUiState(...)` pozycyjne argumenty** zgodne z kolejnością pól data class (isLoading, groupedEvents, totalActiveEvents, error, searchQuery, selectedTab, visibleTabs). ✅
- **Stan początkowy** (`stateIn` initial `EventsUiState(isLoading = true)`): `visibleTabs` default `[UPCOMING, PAST]`, brak crasha podczas loadu.

---

## Bramki (Step 4.5)

| Gate | Wynik | Uzasadnienie |
|---|---|---|
| **QA** | ✅ PASS (code-level + build) | 8 scenariuszy z WO prześledzonych w kodzie; `assembleDebug` PASS. Live on-device QA **deferred do usera** (brak emulatora w env; worker-qa = web-only). |
| **Security** | ✅ PASS (inline) | Zmiana czysto kliencka: widoczność zakładek + klasyfikacja dat + gradle toggle. Zero nowych endpointów/API/DTO/auth/PII. Sandbox dev-gating *ogranicza* (nie eksponuje). Obserwacja (poza zakresem): `local.properties` (gitignored) trzyma plaintext sekrety — NIE commitować. |
| **Contract Sync** | ⏭️ SKIPPED | Zero zmian w api-types.ts / response shapes / DTO. |
| **Migration** | ⏭️ N/A | Zero SQL. |

---

## Decyzje (propozycja do `decision_log.md`)

**ADR — Mobile events list: klasyfikacja data-granularna + Sandbox dev-gated.**
- Wydarzenie jest „Trwające" dla całego zakresu kalendarzowego `[startDay..endDay]` włącznie (ignoruje godziny). Powód: time-precyzyjna klasyfikacja powodowała, że event „wpadał w przeszłość" w dniu wydarzenia po godzinie końcowej — dokładnie problem zgłoszony przez usera.
- „Trwające" widoczna i auto-wybrana tylko gdy istnieje aktywne wydarzenie; znika gdy nic nie trwa.
- „Sandbox" gated `BuildConfig.DEBUG` (brak roli „developer" w backendzie; ustalony wzorzec dev-only UI).

## Gotchy (propozycja do `known_gotchas.md`)

1. **`BuildConfig.DEBUG` wymaga `buildConfig = true` per moduł** — plugin `md.android.feature` nie generuje `BuildConfig` domyślnie; bez opt-in w `build.gradle.kts` modułu → unresolved reference.
2. **`parseToDateTime` → `now()` dla pustych/błędnych dat** — przy klasyfikacji data-granularnej taki event ma `startDay==endDay==dziś` → ląduje w „Trwające". Akceptowalne; rewizja gdy pojawią się złe daty na prod.

## Follow-up (sugerowane WO)
- **Test infra mobile (JUnit)** — pierwsze uruchomienie harness dla modułu feature-events + test `isOngoing/isUpcoming/isPast` (funkcje już czyste/gotowe). Osobna inicjatywa.
