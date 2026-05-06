# MD_mobile_android

Native Android app for Medidesk event organizers.

## Tech Stack

- Kotlin 2.0.21 + Jetpack Compose (Material 3)
- Hilt (DI), Retrofit + Moshi (networking), Room (offline DB)
- WorkManager (background sync), CameraX + MLKit (QR scanner)
- Navigation Compose, DataStore (JWT storage)

## Architecture

Multi-module Clean Architecture:
- `core/` — shared infrastructure (network, DB, theme, sync)
- `features/` — independent feature modules (auth, events, scanner, etc.)
- `app/` — wiring, navigation, DI entry point

## Backend

All API endpoints are on the existing Flask backend:
`https://md-order-portal-backend.onrender.com/api/mobile/`

No new backend services required.

## Setup

1. Open project in Android Studio Ladybug or newer
2. Copy `local.properties.example` to `local.properties` and fill in `sdk.dir`
3. Sync Gradle
4. Run on device or emulator (API 26+)

## Build

```bash
./gradlew assembleDebug
```

For release APK:
```bash
./gradlew assembleRelease
```

## Modules

| Module | Purpose |
|--------|---------|
| `core-model` | Shared domain data classes |
| `core-network` | Retrofit + JWT interceptor |
| `core-database` | Room offline cache |
| `core-datastore` | JWT token storage |
| `core-ui` | Material3 theme + shared composables |
| `core-sync` | WorkManager sync engine |
| `feature-auth` | Login screen |
| `feature-events` | Event list |
| `feature-scanner` | QR scanner + check-in |
| `feature-participants` | Participant list (offline-first) |
| `feature-dashboard` | KPI + statistics |
| `feature-walkin` | Walk-in form + offline queue |
| `feature-inhub` | Kiosk/InHub mode |
| `feature-more` | Profile + settings |
