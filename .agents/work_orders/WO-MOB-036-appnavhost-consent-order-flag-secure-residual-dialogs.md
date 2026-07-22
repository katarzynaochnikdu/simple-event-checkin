# WO-MOB-036: AppNavHost consent-order (F2B-007c) + FLAG_SECURE na residualnych dialogach PII (N-3 residual)

**Data utworzenia:** 2026-06-12
**Worker:** Master (direct impl) — bundle 2× NISK
**Stage:** Mobile Sprint 2 Remediation — domknięcie residualów (follow-up WO-MOB-033/034)
**Priorytet:** 🟢 P2 (2× NISK — audit-trail completeness + screenshot hardening)
**Status:** 🔧 IN PROGRESS 2026-06-12
**Findings:** **F2B-007 residual (c)** ([F2B](../../../.agents/context/_audits/2026-06-mobile-security-sprint-2/F2B_NETWORK_AUTH_TELEMETRY_REVIEW.md)) + **N-3 residual** (3 ekrany pominięte w WO-MOB-034)

---

## Cel

Domknąć dwa NISK residualy zgłoszone explicite jako out-of-scope przez WO-MOB-033/034:
1. **F2B-007 (c)** — pierwszy-launch dialog zgody (`AppNavHost.saveAnalyticsConsent`) gubi event audytowy `analytics_consent_changed`, bo `capture()` leci ZANIM observer ustawi `optIn()/optOut()` (SDK rodzi się opted-out → event odrzucony). To dokładnie ta sama klasa błędu, którą WO-MOB-033 naprawił w `SettingsViewModel`, ale w AppNavHost została pominięta.
2. **N-3 residual** — 3 ekrany z `AlertDialog`-ami pokazującymi dane osoby NIE dostały `SecureDialogEffect()` w WO-MOB-034 (objęte były tylko AddOrderSheet/WalkinFormSheet/ChangePasswordDialog/Scanner). Activity-level FLAG_SECURE (WO-MOB-030) NIE dziedziczy się na osobne okna Dialog → w release te overlaye dają się zrzucić.

## Problem (file:line)

1. `app/.../navigation/AppNavHost.kt:122-133` — `saveAnalyticsConsent`: `authDataStore.save` → `Analytics.capture(...)`; brak inline `optIn()` przed `capture()` (accept) / `capture()` przed `optOut()` (decline). Observer (`AppNavHost.kt:110-118`) robi toggle asynchronicznie, więc nie gwarantuje kolejności wobec `capture`.
2. `features/feature-participants/.../ParticipantDetailsScreen.kt:107,131` — 2 AlertDialogi (check-in / undo) pokazują `participant.displayName`.
3. `features/feature-speakers/.../SpeakerDetailScreen.kt:235` — AlertDialog (undo check-in) pokazuje `speaker.displayName`.
4. `features/feature-dashboard/.../MyMenteesScreen.kt:827,864` — 2 AlertDialogi (wycofanie opieki — `companyName`; potwierdzenie przybycia — `name`+`companyName`+`ticketName`).

## Zakres

1. **F2B-007c** — `AppNavHost.saveAnalyticsConsent`: dołożyć inline ordered toggle mirror `SettingsViewModel` (WO-MOB-033): `consent=true` → `optIn()` PRZED `capture(opted_in)`; `consent=false` → `capture(opted_out)` PRZED `optOut()`. Observer zostaje bez zmian (redundantny no-op toggle przy app-restart — zachowane defensywnie).
2. **N-3** — dodać `SecureDialogEffect()` jako pierwszą instrukcję w content-lambdzie 5 AlertDialogów (3 pliki) + import `pl.medidesk.mobile.core.ui.components.SecureDialogEffect` (wszystkie 3 moduły już zależą od core-ui — zweryfikowane). Helper jest gated `!BuildConfig.DEBUG` → no-op w debug, chroni w release.

## Czego NIE ruszać 🛑

- `SecureDialogEffect.kt` (core-ui, WO-MOB-034) i `MainActivity` FLAG_SECURE (WO-MOB-030) — bez zmian, tylko stosujemy.
- Observer consent w `AppNavHost.kt:110-118` i konfiguracja PostHog `MdApplication` (WO-MOB-031) — bez zmian.
- `SettingsViewModel` (WO-MOB-033) — wzorzec źródłowy, nie dotykać.
- Zero zmian gradle/zależności (core-ui dep już istnieje w 3 modułach).

## Test akceptacyjny 🧪

1. `./gradlew :app:assembleDebug` PASS (kompilacja: import + 5× SecureDialogEffect + reorder AppNavHost).
2. Code-review: `SecureDialogEffect()` obecny w każdym z 5 AlertDialogów; AppNavHost mirror Settings (optIn→capture / capture→optOut).
3. **On-device (post-deploy, user — worker bez urządzenia):** release build → zrzut ekranu na otwartym dialogu check-in uczestnika = zablokowany; first-launch accept → event `analytics_consent_changed(opted_in)` widoczny w PostHog.

## Sizing / Estymata

🟢 trywialny — ~1h (reorder 3-4 linie + 5× 1 linia + 3 importy + assembleDebug).

## Gates

Security (diff — telemetria/consent + screenshot hardening; mechaniczne rozszerzenie 2 już-zgejtowanych wzorców WO-MOB-033/034) · QA = build PASS + on-device smoke post-deploy (user). Snapshot wymagany (kod mobile).
