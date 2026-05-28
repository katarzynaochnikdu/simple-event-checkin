# WO-MOB-018: Sandbox events inline z pomarańczowym pillem „SANDBOX" (dev-only), usunięcie zakładki Sandbox

**Status:** ✅ DONE (2026-05-28) — zaimplementowane, build `assembleDebug` PASS, snapshot pominięty na życzenie usera, czeka na commit/push (user go-ahead).
**Data:** 2026-05-28
**Worker:** Implementer (mobile / Kotlin)
**Stage:** Mobile — Events list UX (follow-up WO-MOB-017)
**Priorytet:** Wysoki
**Scope:** mobile (`simple-event-checkin/`)

## Cel
Zmiana podejścia do wydarzeń sandboxowych na liście wydarzeń (`EventsScreen`):
1. **Usunąć osobną zakładkę „Sandbox".**
2. Wydarzenia sandboxowe **wpadają do normalnych grup** (Trwające / Nadchodzące / Przeszłe) klasyfikowane po datach jak każde inne.
3. Oznaczyć je **pomarańczowym pillem „SANDBOX"** na karcie wydarzenia.
4. **Widoczne wyłącznie dla deweloperów** (`BuildConfig.DEBUG`). Poza buildem debug — sandbox events **całkowicie ukryte** (nie liczą się do żadnej zakładki/grupy/licznika).

## Kontekst
WO-MOB-017 (DONE, commit `9ef6476`) dał zakładki **Trwające | Nadchodzące | Przeszłe | Sandbox**, gdzie Sandbox to osobna zakładka gated `BuildConfig.DEBUG`. User: „zrób inaczej" — sandbox ma być inline w normalnych grupach z pillem, nadal tylko dla devów.

## Zakres (pliki, które Worker MA PRAWO modyfikować)
- `features/feature-events/src/main/java/pl/medidesk/mobile/feature/events/presentation/viewmodel/EventsViewModel.kt` — usuń `EventTab.SANDBOX`; dev-gate filtruje sandbox z całego źródła gdy nie-dev; per-tab filtry przestają wykluczać sandbox; `isSandbox` wyniesione do top-level.
- `features/feature-events/src/main/java/pl/medidesk/mobile/feature/events/presentation/screen/EventsScreen.kt` — usuń label „Sandbox"; top-level `fun isSandbox(EventItem)`; pomarańczowy pill „SANDBOX" na `EventCompactCard`.

## Czego NIE ruszać 🛑
- `feature-events/build.gradle.kts` — `buildConfig = true` JUŻ jest (WO-MOB-017). Bez zmian.
- `core-model/.../EventItem.kt` — bez zmian schematu.
- `parseToDateTime` / `formatDateLabel` / klasyfikacja dat `isOngoing/isUpcoming/isPast` z WO-MOB-017 — reuse as-is.
- Backend / API / DTO. Inne moduły. `EventFullWidthCard` (nieużywany w liście — nie dotykać).

## Pliki startowe
- `EventsViewModel.kt` — `enum EventTab`, `combine{}`, `isSandbox`, per-tab `when`.
- `EventsScreen.kt` — `TabRow` label `when(tab)`, `EventCompactCard` (linie ~127-218, blok `Text(event.eventName...)` ~181-188).
- Wzorzec koloru/pilla: `core-ui/.../ParticipantTagChip.kt` (Tailwind→Compose Color), `ParticipantDetailsScreen` (użycie chipów).

## Decyzje projektowe (domyślne — do ewentualnej korekty)
1. **Dev-gating = `BuildConfig.DEBUG`** (jak WO-MOB-017; „nikt poza deweloperami"). Non-dev → `raw.filter { !isSandbox(it) }` na samym wejściu, więc sandbox nie istnieje w żadnej grupie ani liczniku.
2. **Pill = pomarańczowy** `Color(0xFFF97316)` (orange-500), tekst biały „SANDBOX", ~9sp bold, rounded ~6dp, padding h6/v2. Umiejscowienie: **w wierszu nazwy** (trailing, po nazwie eventu; nazwa dostaje `weight(1f, fill=false)` + ellipsis żeby pill był zawsze widoczny).
3. **Detekcja sandbox bez zmian** (`isSandbox`): nazwa zawiera „sandbox"/„test" LUB `status == "draft"`. *Caveat:* event z „test" w nazwie też dostanie pill i będzie ukryty dla non-dev — zachowanie pre-existing, poza zakresem zmiany.
4. **Enum `EventTab`** → `{ ONGOING, UPCOMING, PAST }` (usunięty `SANDBOX`). `visibleTabs` default `listOf(UPCOMING, PAST)`.

## Ryzyko
- **`when(effectiveTab)` / `when(tab)` muszą zostać wyczerpujące** po usunięciu `SANDBOX` (enum ma teraz 3 wartości). Mitygacja: compile check.
- **`isSandbox` wynoszone do top-level** — VM importuje je z pakietu `...presentation.screen` (precedens: `parseToDateTime`). Usunąć prywatną kopię w VM, by nie było 2 definicji.
- **Pill w wierszu nazwy** może skracać długie nazwy — akceptowalne (ellipsis), pill ma priorytet widoczności.
- **Licznik `totalActiveEvents`** dla non-dev nie obejmuje sandbox (zgodnie z „ukryte") — pożądane.

## Definition of Done ✅
- [ ] `EventTab` = `{ ONGOING, UPCOMING, PAST }` (bez SANDBOX); brak zakładki „Sandbox" w UI.
- [ ] Non-dev (`!BuildConfig.DEBUG`): sandbox events **nieobecne** wszędzie (grupy, liczniki, anyOngoing).
- [ ] Dev (`BuildConfig.DEBUG`): sandbox events widoczne **inline** w grupie zgodnej z ich datą (Trwające/Nadchodzące/Przeszłe).
- [ ] Każda karta sandbox eventu ma **pomarańczowy pill „SANDBOX"**; nie-sandbox events bez pilla.
- [ ] Per-tab filtry NIE wykluczają już sandbox (`!isSandbox` usunięte); dev-gate robione raz na wejściu.
- [ ] `isSandbox` jako pojedyncza top-level funkcja (VM + karta używają tej samej).
- [ ] Build `assembleDebug` PASS.
- [ ] Review note `review_notes/REVIEW-WO-MOB-018.md`.

## Test akceptacyjny 🧪 (manualne QA na APK)
1. **Debug build:** zaloguj się; upewnij się że istnieje event sandbox/test/draft z datą np. dziś.
   - Oczekiwane: event widoczny w „Trwające" (lub właściwej grupie wg daty), z **pomarańczowym pillem „SANDBOX"** przy nazwie. Brak osobnej zakładki „Sandbox".
2. Event normalny (nie-sandbox) → bez pilla, w swojej grupie wg daty.
3. **(Koncepcyjnie / release build)** non-dev: sandbox events nie pojawiają się w żadnej zakładce; liczniki ich nie liczą.
4. Search działa w obrębie aktywnej zakładki; zakładki = Trwające?/Nadchodzące/Przeszłe (bez Sandbox).
5. Brak crashy (`adb logcat`).

## Oczekiwany efekt wizualny 🖼️
- Brak zakładki „Sandbox". Zakładki: **Trwające | Nadchodzące | Przeszłe** (Trwające warunkowo).
- W debug: karta sandbox eventu identyczna jak normalna + mały **pomarańczowy pill „SANDBOX"** w wierszu nazwy.
- W release: sandbox events w ogóle niewidoczne.

## Kontrakt API
**N/A** — czysto kliencka prezentacja. Zero zmian backend/API/model/DTO.

## Format zwrotki
- 2 zmienione pliki + 1-linijkowy opis każdego.
- `git diff --stat`.
- Build `assembleDebug` PASS/FAIL + screenshot (jeśli możliwe).
- Propozycja gotcha (detekcja „test" w nazwie ukrywa event dla non-dev).

## Sizing
🟢 **Mały** — 2 pliki, 1 warstwa (presentation), gradle bez zmian.

## Snapshot (Step 2.5)
**WYMAGANY** — kod aplikacji mobilnej. Tag: `snapshot/pre-mobile-sandbox-inline-pill-2026-05-28`.
