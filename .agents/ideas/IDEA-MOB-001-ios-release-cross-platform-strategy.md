# IDEA-MOB-001: Wydanie simple-event-checkin na iOS — analiza 4 ścieżek (Swift / KMP / Compose MP / Expo)

**Zgłoszony:** 2026-05-19
**Scope:** mobile
**Moduł:** Inbox
**Status:** Inbox

## Pomysł
Projekt: wydanie aplikacji `simple-event-checkin` (obecnie Android natywny Kotlin, dojrzała, modułowa, ukończona) w wersji **iOS**, możliwej do samodzielnej instalacji przez administratora.

# Stan wyjściowy

`simple-event-checkin/` — natywny Android, ~292 plików .kt, architektura w stylu Now-in-Android:
- build-logic + convention plugins
- 8 core modules: analytics, database (Room), datastore (DataStore), mappers, model, network, sync, ui (Compose)
- 10 feature modules: add-order, auth, dashboard, events, inhub, more, participants, scanner, speakers, sponsors, walkin
- Stack: Jetpack Compose, Hilt, Room, DataStore, Retrofit/OkHttp, CameraX + ML Kit, Coroutines + Flow

Aplikacja świeżo zakończona, działa produkcyjnie.

# Problem

iOS nie pozwala na sideloading APK. Samodzielna instalacja administratora wymaga:
- Apple Developer Program ($99/rok)
- TestFlight internal (do 100 osób, build wygasa po 90 dniach) **lub** unlisted App Store (każdy update przez review Apple)

Sam Kotlin natywny nie buduje się na iOS — wymaga osobnego kodu.

# Cztery ścieżki realizacji

## Ścieżka A: Pełen rewrite SwiftUI (drugi natywny codebase)
- Czas: 4–8 tygodni dev (senior iOS)
- Koszt zewnętrzny: 30–60k PLN
- Reuse kodu Kotlin <-> Swift: ~5–15% (przez konwencję, asset pipeline)
- Każdy nowy feature: 2x (dwa razy ten sam kod, dwa zespoły / jeden bardzo drogi senior)
- Ikona/kolory: wymagają własnego skryptu generatora (design tokens + Style Dictionary)

## Ścieżka B: Kotlin Multiplatform (KMP)
- Czas: 6–10 tygodni — refaktor obecnego Kotlin (Hilt -> Koin, Room -> SQLDelight, Retrofit -> Ktor) + iOS UI w SwiftUI
- Reuse: ~40–60% (commonMain dla modeli, network, DB, sync, ViewModels)
- Ryzyko: regresja ukończonej apki Android
- Ikona: nadal osobno per platforma

## Ścieżka C: Compose Multiplatform
- Czas: 4–7 tygodni
- Reuse: ~80–95% (cały UI + logika)
- Ryzyko: ekosystem młody (CMP iOS Stable od 2026), UI "prawie natywny", ryzyko Apple review
- Ikona: jeden vector resource w commonMain

## Ścieżka D: Rewrite na React Native / Expo (rozwinięcie istniejącego `checkin-app/`)
- Czas: 3–6 tygodni (feature parity z `simple-event-checkin`)
- Reuse: 90–98%, iOS gratis
- Ikona "raz = wszędzie": JEDEN plik `icon.png` 1024x1024 w `app.json`, Expo robi resztę
- Wymaga porzucenia natywnego Kotlin codebase
- W monorepo już istnieje `checkin-app/` (Expo MVP1) — można rozbudować zamiast tworzyć od zera

# Tabela decyzyjna

| Ścieżka | Effort z obecnego stanu | Reuse | Spójność ikony/tematu | Koszt nowego feature'a |
|---|---|---|---|---|
| Swift nat. (A) | 4–8 tyg | 5–15% | Własny skrypt | 2x |
| KMP (B) | 6–10 tyg | 40–60% | Częściowy | 1.3–1.5x |
| Compose MP (C) | 4–7 tyg | 80–95% | Compose resource | 1.05x |
| Expo rewrite (D) | 3–6 tyg | 90–98% | Auto | 1x |

# Współdzielenie wymagań Apple (niezależnie od ścieżki)

- Apple Developer Program: $99/rok obowiązkowo
- TestFlight internal: do 100 adminów, **rebuild co 90 dni** (build expiry)
- Unlisted App Store: trwała instalacja, każdy update przez Apple review (~1–2 dni)
- Ad Hoc / Enterprise: nierealne (UDID pain / wymóg 100+ pracowników)

# Otwarte pytania do biznesu

1. Jak krytyczny jest iOS dla adminów? (must-have vs nice-to-have)
2. Czy gotowa apka Kotlin musi pozostać w produkcji równolegle, czy można ją zastąpić cross-platform?
3. Jaka skala adminów na iOS? (TestFlight <=100 vs unlisted App Store >100)
4. Budżet vs preferowana ścieżka — rewrite zewnętrznym devem (drogie) vs migracja na cross-platform (utrata części natywnej dojrzałości)

# Rekomendacja (analityczna, do dyskusji)

Jeśli iOS jest must-have -> **Ścieżka D (Expo)** jest najpragmatyczniejsza: bazuje na istniejącym `checkin-app/`, daje maksymalną spójność cross-platform, najtańsze dalsze utrzymanie.

Jeśli zachowanie natywnego Android jest krytyczne biznesowo -> **Ścieżka A + asset pipeline** — drogie utrzymanie, ale dwa pełnoprawnie natywne codebase'y.

# Źródło

Analiza w sesji 2026-05-19 (Claude Code). Pytanie użytkownika: "jak trudno przerobić te aplikacje na taką, którą można zainstalować jako administrator samodzielnie na iOS?" + dopytka o maksymalną spójność asset/kod między platformami.

## Do rozszerzenia (gdy będzie czas)
- [ ] Cel
- [ ] Opis szczegółowy
- [ ] Efekt UX / behawior
- [ ] Zależności / wymagania
- [ ] Estymacja effortu
