# IMPLEMENTATION REPORT — WO-MOB-017

**Zakładka „Trwające" na liście wydarzeń (dynamiczna, dev-gated Sandbox) + fix data-granularny**

**Data:** 2026-05-28
**Worker:** worker-implementer (dispatch przez Master)
**Status:** ✅ DONE — build PASS, working tree (NIE commitowane, NIE pushowane)
**Snapshot:** `snapshot/pre-mobile-events-ongoing-tab-2026-05-28` @ `28168cc`

---

## Co zrobiono

Na ekranie listy wydarzeń (`EventsScreen`) dodano dynamiczną zakładkę **„Trwające"** (pierwsza od lewej, widoczna tylko gdy ≥1 wydarzenie aktualnie trwa) i naprawiono błąd, przez który w **dniu wydarzenia** event natychmiast klasyfikował się jako „Przeszłe".

Docelowy układ zakładek: **Trwające | Nadchodzące | Przeszłe | Sandbox**, gdzie:
- **Trwające** — w `visibleTabs` tylko gdy `anyOngoing`; auto-wybrana gdy obecna.
- **Sandbox** — widoczna tylko w buildach debug (`BuildConfig.DEBUG`).

## Root cause (błąd „dzień wydarzenia wpada w przeszłość")

`isPast(event, now)` robił `parseToDateTime(event.endDate).isBefore(now)`. Parser `parseToDateTime` dla daty bez części godzinowej zwraca `LocalDateTime` o `00:00`. Zatem już o 00:01 w dniu wydarzenia `endDate(00:00).isBefore(now)` = `true` → event „przeszły" przez cały swój dzień.

## Fix

Klasyfikacja **data-granularna** (porównanie `LocalDate`, ignoruje godziny):
- `isOngoing(e, today) = !today.isBefore(startDay(e)) && !today.isAfter(endDay(e))`
- `isUpcoming(e, today) = today.isBefore(startDay(e))`
- `isPast(e, today) = today.isAfter(endDay(e))`

gdzie `startDay/endDay = parseToDateTime(...).toLocalDate()` (z `endDate.ifBlank { startDate }`). Funkcje czyste, deterministyczne, przyjmują `today: LocalDate` — gotowe pod test jednostkowy.

## Zmienione pliki (3, +47/-30 LOC)

| Plik | Zmiana |
|---|---|
| `features/feature-events/.../viewmodel/EventsViewModel.kt` | `EventTab` += `ONGOING` (pierwsze); `_selectedTab` nullable (default sterowany danymi); `EventsUiState.visibleTabs`; rewrite `combine{}` (`anyOngoing`/`visibleTabs`/`defaultTab`/`effectiveTab`, `BuildConfig.DEBUG`-gate Sandbox); zamiana time-based `isPast` na data-granularne czyste funkcje. Month-grouping verbatim. |
| `features/feature-events/.../screen/EventsScreen.kt` | `TabRow` iteruje `uiState.visibleTabs`; `selectedTabIndex = visibleTabs.indexOf(selectedTab).coerceAtLeast(0)`; label `ONGOING → "Trwające"`. |
| `features/feature-events/build.gradle.kts` | `buildFeatures { buildConfig = true }` (1:1 z `feature-dashboard`) — dla `BuildConfig.DEBUG`. |

**Bez zmian:** backend, API, DTO, `EventItem`, `parseToDateTime`/`formatDateLabel`.

## Build / weryfikacja

- `./gradlew assembleDebug` → **BUILD SUCCESSFUL in 9m43s**, exit 0 (570 tasks, 28 executed).
- `:features:feature-events:compileDebugKotlin` ✅ (w tym nowa referencja `BuildConfig.DEBUG`).
- `:features:feature-dashboard:compileDebugKotlin` ✅ (konsument `EventTab`/`EventsUiState` — brak regresji od zmian enum/state).
- 1 warning pre-existing poza zakresem: `EventsScreen.kt:261 Condition is always 'true'` (`if (event.status != null)` w `EventFullWidthCard`).

## Bramki

- **QA:** code-level PASS (8 scenariuszy z WO) + build PASS. On-device QA deferred do usera (brak emulatora; worker-qa web-only).
- **Security:** PASS inline (klient-side; brak endpointów/API/auth/PII; Sandbox gating ogranicza). Obserwacja: `local.properties` (gitignored) ma plaintext sekrety — nie commitować.
- **Contract Sync:** SKIPPED (zero zmian typów/API). **Migration:** N/A (zero SQL).

---

## Postmortem (4 pytania)

**1. Czy było coś nieoczywistego/zaskakującego?**
TAK — dwie rzeczy:
- `BuildConfig.DEBUG` nie jest dostępne w module feature, dopóki moduł nie ma `buildFeatures { buildConfig = true }`. Plugin konwencji `md.android.feature` nie generuje `BuildConfig` domyślnie (`feature-dashboard` miał opt-in, `feature-events` nie).
- `parseToDateTime` zwraca `now()` dla pustych/nieparsowalnych dat → przy klasyfikacji data-granularnej taki event (`startDay==endDay==dziś`) ląduje w „Trwające".

**2. Czy wprowadzono dług techniczny?**
Minimalny. Mobile nadal nie ma harness testowego (`src/test` nie istnieje) — funkcje klasyfikacji są celowo czyste/testowalne, ale faktyczny test JUnit odłożony do osobnego WO (pierwsze uruchomienie test infra mobile). Pozostawiono pre-existing dead import `DateTimeFormatter` i warning `status != null` (poza zakresem).

**3. Czy zmieniono coś security-critical?**
NIE. Czysto kliencka prezentacja. Jedyny element auth-adjacent to dev-gating Sandbox przez `BuildConfig.DEBUG` (ogranicza widoczność w release/preview, nie eksponuje).

**4. Czy coś wymaga dalszej pracy / follow-up?**
- **On-device QA** (8 scenariuszy z WO) — user na realnym urządzeniu/emulatorze.
- **Commit + push** — czeka na user go-ahead (guardrail live check-in).
- **Test infra mobile (JUnit)** — osobny WO; test `isOngoing/isUpcoming/isPast`.
- **Promocja** gotcha (×2) do `known_gotchas.md` i ADR do `decision_log.md` (propozycje w REVIEW-WO-MOB-017.md).
