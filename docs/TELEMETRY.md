# Telemetria PostHog — simple-event-checkin

Dokumentacja opisuje konfigurację PostHog, listę śledzonych zdarzeń, mechanizm zgody RODO i procedurę dodawania nowych eventów.

---

## 1. Przegląd

Aplikacja operatora check-in (`simple-event-checkin`) wysyła anonimowe dane analityczne do PostHog EU Cloud w celu monitorowania przepływu pracy operatora (logowanie, skanowanie QR, synchronizacja, otwieranie wydarzeń, dodawanie zamówień).

| Parametr | Wartość |
|---|---|
| Provider | PostHog EU Cloud |
| Host | `https://eu.i.posthog.com` |
| Project ID | 178536 |
| SDK | `com.posthog:posthog-android:3.11.0` |
| Domyślna polityka | **opt-out** — żadne eventy nie lecą dopóki użytkownik nie zaakceptuje |

Dane, które NIE są zbierane: imiona/nazwiska uczestników, e-maile, NIPy, numery telefonów. Wszystkie pola formularzy i obrazy są maskowane w Session Replay. Identyfikacja użytkownika opiera się wyłącznie na `user_id` (liczba całkowita z backendu) i `role`.

---

## 2. Architektura

```
MdApplication.onCreate()
    └─ setupPostHog()
           ├─ odczytuje consent z AuthDataStore (synchronicznie, max 300 ms)
           ├─ PostHogAndroid.setup(context, PostHogAndroidConfig)
           │       optOut = hasConsent != true   ← opt-out by default
           │       sessionReplay = hasConsent == true
           └─ Analytics.register(superProperties)

AppNavHost (Compose)
    └─ AuthViewModel
           ├─ obserwuje analyticsConsentFlow → Analytics.optIn() / Analytics.optOut()
           └─ showsAnalyticsConsentDialog gdy consent == null

Feature modules (feature-auth, feature-scanner, feature-dashboard, feature-more, feature-add-order, core-sync)
    └─ wywołują Analytics.capture() / Analytics.identify() / Analytics.reset()
           ↓
    core-analytics / Analytics.kt   ← jedyna fasada; nikt nie importuje PostHog bezpośrednio*
           ↓
    com.posthog.PostHog (SDK)
           ↓
    PostHog EU Cloud (eu.i.posthog.com)
```

\* Wyjątek: `MdApplication.kt` importuje `PostHogAndroid` i `PostHogAndroidConfig` wyłącznie do jednorazowego `setup()`. `core-analytics/build.gradle.kts` eksponuje dependency przez `api()` właśnie z tego powodu.

### Lokalizacja kodu

| Rola | Plik |
|---|---|
| Inicjalizacja SDK | [`app/src/main/java/pl/medidesk/mobile/MdApplication.kt`](../app/src/main/java/pl/medidesk/mobile/MdApplication.kt) |
| Fasada Analytics | [`core/core-analytics/src/main/java/pl/medidesk/mobile/core/analytics/Analytics.kt`](../core/core-analytics/src/main/java/pl/medidesk/mobile/core/analytics/Analytics.kt) |
| Stałe eventów i właściwości | [`core/core-analytics/src/main/java/pl/medidesk/mobile/core/analytics/AnalyticsEvent.kt`](../core/core-analytics/src/main/java/pl/medidesk/mobile/core/analytics/AnalyticsEvent.kt) |
| Consent flow + screen tracking | [`app/src/main/java/pl/medidesk/mobile/navigation/AppNavHost.kt`](../app/src/main/java/pl/medidesk/mobile/navigation/AppNavHost.kt) |
| UI dialogu zgody | [`app/src/main/java/pl/medidesk/mobile/ui/AnalyticsConsentDialog.kt`](../app/src/main/java/pl/medidesk/mobile/ui/AnalyticsConsentDialog.kt) |
| Persystencja consent | [`core/core-datastore/src/main/java/pl/medidesk/mobile/core/datastore/AuthDataStore.kt`](../core/core-datastore/src/main/java/pl/medidesk/mobile/core/datastore/AuthDataStore.kt) |
| Dependency PostHog (wersja) | [`core/core-analytics/build.gradle.kts`](../core/core-analytics/build.gradle.kts) |

---

## 3. Konfiguracja

### 3.1. Klucz API

Klucz PostHog trafia do APK przez łańcuch:

```
local.properties (dev) / env var (CI)
        ↓
app/build.gradle.kts — buildConfigField("String", "POSTHOG_API_KEY", ...)
        ↓
BuildConfig.POSTHOG_API_KEY (wygenerowany kod)
        ↓
MdApplication.setupPostHog() → PostHogAndroidConfig(apiKey = BuildConfig.POSTHOG_API_KEY)
```

Jeśli `POSTHOG_API_KEY` jest pusty (brak wpisu w `local.properties` i brak env var), `setupPostHog()` zwraca natychmiast bez inicjalizacji SDK — aplikacja działa normalnie, tylko bez telemetrii.

### 3.2. Zmienne konfiguracyjne

| Zmienna | Gdzie ustawić (dev) | Gdzie ustawić (CI/release) | Wartość przykładowa |
|---|---|---|---|
| `POSTHOG_API_KEY` | `local.properties` | env var `POSTHOG_API_KEY` | `phc_xxxxxxxxxxxx` |
| `POSTHOG_HOST` | `local.properties` (opcjonalnie) | env var `POSTHOG_HOST` | `https://eu.i.posthog.com` |

Plik szablonu: [`local.properties.example`](../local.properties.example).

`local.properties` jest w `.gitignore` — nigdy nie commitować kluczy.

### 3.3. Tryb debug

W buildach debug (`BuildConfig.DEBUG == true`) SDK ma włączone `debug = true` ([MdApplication.kt, linia 60](../app/src/main/java/pl/medidesk/mobile/MdApplication.kt#L60)), co powoduje logowanie do Logcat pod tagiem `PostHog`.

### 3.4. Session Replay

Session Replay jest aktywne **wyłącznie gdy użytkownik zaakceptował consent**. Zastosowane maskowania (źródło: [MdApplication.kt, linie 73–79](../app/src/main/java/pl/medidesk/mobile/MdApplication.kt#L73)):

| Opcja | Wartość | Uzasadnienie |
|---|---|---|
| `maskAllTextInputs` | `true` | Pola formularzy: hasła, NIP, dane osobowe uczestników |
| `maskAllImages` | `true` | Zdjęcia uczestników, loga z nazwami |

Sample rate Session Replay jest ustawiany w dashboardzie PostHog, nie w SDK.

---

## 4. Consent / RODO

### 4.1. Przepływ przy pierwszym uruchomieniu

```
Pierwsze uruchomienie
        │
        ▼
MdApplication.onCreate()
  authDataStore.analyticsConsentFlow.firstOrNull() → null (nigdy nie ustawiony)
  PostHogAndroid.setup(..., optOut = true)   ← żadne eventy nie lecą
        │
        ▼
AppNavHost renderuje AnalyticsConsentDialog
  (blokuje nawigację — dialog jest nieodrzywalny: dismissOnBackPress=false, dismissOnClickOutside=false)
        │
        ├─── "Akceptuję" ──► AuthViewModel.saveAnalyticsConsent(true)
        │                         authDataStore.saveAnalyticsConsent(true)   ← DataStore "auth_prefs", klucz "analytics_consent"
        │                         Analytics.optIn()
        │                         Analytics.capture(ANALYTICS_CONSENT_CHANGED, action="opted_in")
        │
        └─── "Odmawiam" ───► AuthViewModel.saveAnalyticsConsent(false)
                                  authDataStore.saveAnalyticsConsent(false)
                                  Analytics.optOut()
                                  Analytics.capture(ANALYTICS_CONSENT_CHANGED, action="opted_out")
                                  (ten jeden event jest wysłany, reszta zablokowana)
```

### 4.2. Persystencja

Consent jest przechowywany w `DataStore<Preferences>` pod nazwą pliku `auth_prefs`, klucz `analytics_consent` (typ `String`: `"true"` / `"false"` / brak klucza = `null`).

Mapowanie w [`AuthDataStore.analyticsConsentFlow`](../core/core-datastore/src/main/java/pl/medidesk/mobile/core/datastore/AuthDataStore.kt#L45):

```kotlin
null  = nigdy nie ustawiono → pokaż dialog
true  = user zaakceptował → Analytics.optIn()
false = user odrzucił → Analytics.optOut()
```

### 4.3. Zmiana zgody po pierwszym uruchomieniu

Ścieżka: **Ustawienia** → przełącznik "Analityka" → [`SettingsViewModel.setAnalyticsConsent()`](../features/feature-more/src/main/java/pl/medidesk/mobile/feature/more/presentation/viewmodel/SettingsViewModel.kt#L93).

Po zmianie:
1. `authDataStore.saveAnalyticsConsent(consent)` — trwały zapis.
2. `Analytics.optIn()` lub `Analytics.optOut()` — natychmiastowy efekt w sesji.
3. `Analytics.capture(ANALYTICS_CONSENT_CHANGED, action="opted_in"|"opted_out")` — event audytowy.

### 4.4. Wylogowanie

`SettingsViewModel.logout()` wywołuje `Analytics.reset()`, co czyści tożsamość użytkownika w PostHog (nowy anonimowy distinct_id po kolejnym logowaniu).

---

## 5. Katalog eventów

Stałe nazw eventów i kluczy właściwości: [`AnalyticsEvent.kt`](../core/core-analytics/src/main/java/pl/medidesk/mobile/core/analytics/AnalyticsEvent.kt).

### 5.1. Eventy niestandardowe

| Nazwa eventu (stała) | Gdzie wysyłany | Właściwości | Kiedy |
|---|---|---|---|
| `user_logged_in` (`USER_LOGGED_IN`) | `LoginViewModel.login()` | `role: String` | Po udanym logowaniu |
| `user_logged_out` (`USER_LOGGED_OUT`) | `SettingsViewModel.logout()` | — | Po kliknięciu "Wyloguj" |
| `analytics_consent_changed` (`ANALYTICS_CONSENT_CHANGED`) | `AuthViewModel.saveAnalyticsConsent()` | `action: "opted_in"\|"opted_out"`, `app_version: String` | Przy pierwszym wyborze w dialogu |
| `analytics_consent_changed` (`ANALYTICS_CONSENT_CHANGED`) | `SettingsViewModel.setAnalyticsConsent()` | `action: "opted_in"\|"opted_out"` | Przy zmianie w Ustawieniach |
| `qr_scan_completed` (`QR_SCAN_COMPLETED`) | `ScannerViewModel.confirmScan()` | `result: "success"\|"success_offline"\|"duplicate"\|"error"\|"not_found"`, `event_id: String`, `is_offline: Boolean` | Po zatwierdzeniu skanu w dialogu potwierdzenia |
| `qr_scan_completed` (`QR_SCAN_COMPLETED`) | `ScannerViewModel.cancelScan()` | `result: "denied"`, `event_id: String`, `is_offline: false` | Po kliknięciu "Anuluj" w dialogu potwierdzenia |
| `checkin_undone` (`CHECKIN_UNDONE`) | `ScannerViewModel.undoLastScan()` | `event_id: String` | Po udanym cofnięciu check-in |
| `sync_completed` (`SYNC_COMPLETED`) | `SyncWorker.doWork()` | `pushed_count: Int`, `pulled_count: Int`, `duration_ms: Long`, `force_full: Boolean` | Po udanej synchronizacji (push + pull + walkins) |
| `sync_failed` (`SYNC_FAILED`) | `SyncWorker.doWork()` | `error_type: "worker_error"`, `pending_count: Int` | Gdy jakakolwiek faza sync rzuci wyjątek |
| `event_opened` (`EVENT_OPENED`) | `DashboardViewModel.loadDashboard()` | `event_id: String` | Przy pierwszym załadowaniu dashboardu wydarzenia |
| `add_order_started` (`ADD_ORDER_STARTED`) | `AddOrderViewModel.loadCartConfig()` | `event_id: String` | Przy otwarciu flow "Dodaj zamówienie" |
| `add_order_completed` (`ADD_ORDER_COMPLETED`) | `AddOrderViewModel.handleResponse()` | `payment_method: "proforma"\|"stripe"\|"free"` | Po udanym złożeniu zamówienia przez operatora |

### 5.2. Eventy autocapture (SDK)

| Nazwa eventu | Mechanizm | Kiedy |
|---|---|---|
| `$screen` | `LaunchedEffect` w `AppNavHost` — emitowany ręcznie przy każdej zmianie destinacji | Przy każdej nawigacji (patrz sekcja 7) |
| `Application Opened`, `Application Backgrounded`, `Application Installed`, `Application Updated` | `captureApplicationLifecycleEvents = true` w konfiguracji SDK | Cykl życia aplikacji |
| Deep link events | `captureDeepLinks = true` | Po odebraniu deep linka `medidesk://reset-password?token=...` |

Natywny autocapture kliknięć View NIE jest włączony (domyślnie wyłączony w PostHog Android SDK v3, nie był explicite włączony w konfiguracji).

---

## 6. Super Properties

Super properties są rejestrowane jednorazowo po `PostHogAndroid.setup()` w [`MdApplication.setupPostHog()`](../app/src/main/java/pl/medidesk/mobile/MdApplication.kt#L87). Są automatycznie dołączane do **każdego** kolejnego eventu.

| Klucz | Źródło | Przykład |
|---|---|---|
| `app_version` | `BuildConfig.VERSION_NAME` | `"1.0.0"` |
| `version_code` | `BuildConfig.VERSION_CODE` | `1` |
| `device_manufacturer` | `Build.MANUFACTURER` | `"samsung"` |
| `device_model` | `Build.MODEL` | `"SM-A546B"` |
| `android_sdk` | `Build.VERSION.SDK_INT` | `34` |
| `build_type` | `BuildConfig.DEBUG` | `"release"` lub `"debug"` |

---

## 7. Screen Tracking

PostHog Android SDK v3 nie wykrywa automatycznie zmian ekranu w Jetpack Compose Navigation (SDK widzi tylko Activity). Screen tracking jest implementowany ręcznie w [`AppNavHost.kt`](../app/src/main/java/pl/medidesk/mobile/navigation/AppNavHost.kt#L134).

**Mechanizm:**

```kotlin
val navBackStackEntry by navController.currentBackStackEntryAsState()
LaunchedEffect(navBackStackEntry?.destination?.route) {
    val route = navBackStackEntry?.destination?.route ?: return@LaunchedEffect
    val screenName = route.substringBefore("/").substringBefore("?")
    Analytics.capture(
        "\$screen",
        mapOf("screen_name" to screenName, "\$screen_name" to screenName)
    )
}
```

Parametry route'a (np. `eventId`) są obcinane przed wysłaniem, żeby uniknąć mnożenia wariantów ekranu w PostHog Persons.

**Route'y śledzone** (po obcięciu parametrów):

| Route (po obcięciu) | Odpowiada ekranowi |
|---|---|
| `login` | Ekran logowania |
| `reset_password` | Ekran resetu hasła (deep link) |
| `events` | Lista wydarzeń |
| `settings` | Ustawienia |
| `global_scanner` | Globalny skaner QR (bez kontekstu wydarzenia) |
| `main` | Kontener wydarzenia z bottom navigation |
| `dashboard` | Dashboard (statystyki) wydarzenia |
| `scanner` | Skaner QR w kontekście wydarzenia |
| `participants` | Lista uczestników |
| `stats` | Szczegółowe statystyki |
| `my_mentees` | Lista podopiecznych (rola mentora) |
| `participantDetails` | Szczegóły uczestnika |

---

## 8. Jak dodać nowy event

### Krok 1 — Dodaj stałą w `AnalyticsEvent.kt`

```kotlin
// core/core-analytics/src/main/java/pl/medidesk/mobile/core/analytics/AnalyticsEvent.kt

object AnalyticsEvent {
    // ... istniejące stałe ...

    // --- Participants (WO-XXX) ---
    const val PARTICIPANT_DETAILS_VIEWED = "participant_details_viewed"

    object Props {
        // ... istniejące klucze ...
        const val PARTICIPANT_ID = "participant_id"
    }
}
```

Zasady nazewnictwa:
- Nazwy eventów: `snake_case`, czasownik w czasie przeszłym (`_viewed`, `_completed`, `_failed`, `_started`).
- Klucze właściwości: `snake_case`.
- Grupuj eventy komentarzem z referencją do WO.

### Krok 2 — Wywołaj w ViewModelu

```kotlin
// W odpowiednim ViewModel, nigdy bezpośrednio w Composable

import pl.medidesk.mobile.core.analytics.Analytics
import pl.medidesk.mobile.core.analytics.AnalyticsEvent

fun viewParticipantDetails(participantId: Long) {
    Analytics.capture(
        AnalyticsEvent.PARTICIPANT_DETAILS_VIEWED,
        mapOf(AnalyticsEvent.Props.PARTICIPANT_ID to participantId)
    )
    // ... reszta logiki ...
}
```

### Zasady

- Wywołuj `Analytics.capture()` **tylko w ViewModelach** — nie w Composable, nie w Repository, nie w Worker (z wyjątkiem `SyncWorker`, który nie ma dostępu do ViewModel).
- Nie wysyłaj PII: imion, nazwisk, e-maili, NIPów, numerów telefonów, adresów IP.
- Wartości właściwości muszą być typów `String`, `Int`, `Long`, `Boolean` lub `Double` — PostHog nie obsługuje zagnieżdżonych obiektów jako wartości właściwości.
- Używaj stałych z `AnalyticsEvent.Props` zamiast string literałów.

---

## 9. Troubleshooting

### APK nie wysyła żadnych eventów

1. Sprawdź czy `POSTHOG_API_KEY` jest ustawiony w `local.properties`:
   ```
   POSTHOG_API_KEY=phc_...
   ```
2. Przebuduj projekt po dodaniu klucza — `BuildConfig` jest generowany podczas kompilacji, zmiana w `local.properties` wymaga pełnego rebuild'u (`Build → Clean Project`, potem `Build → Rebuild Project`).
3. Sprawdź Logcat pod tagiem `PostHog` (dostępne w debug build).

### Eventy nie pojawiają się w PostHog dla konkretnego użytkownika

1. Sprawdź consent: **Ustawienia → Analityka** — przełącznik musi być włączony.
2. Sprawdź czy `Analytics.optOut()` nie jest wywoływane po `optIn()` — `AuthViewModel` obserwuje `analyticsConsentFlow` i wywołuje `optIn`/`optOut` reaktywnie.
3. Eventy wysyłane po `Analytics.optOut()` są odrzucane przez SDK bez logowania.

### Identyfikacja użytkownika nie działa (eventy pod anonimowym distinct_id)

`Analytics.identify()` jest wywoływane w [`LoginViewModel.login()`](../features/feature-auth/src/main/java/pl/medidesk/mobile/feature/auth/presentation/viewmodel/LoginViewModel.kt#L49) bezpośrednio po udanym logowaniu. Jeśli eventy trafiają pod anonimowe ID:
- Sprawdź czy `login()` zwraca `isSuccess = true` i czy `user` nie jest `null`.
- PostHog łączy anonimowe eventy sprzed `identify()` z użytkownikiem — jest to normalne zachowanie (alias merge).

### Włączenie szczegółowego logowania SDK

W `MdApplication.setupPostHog()` parametr `debug` jest automatycznie ustawiany na `BuildConfig.DEBUG`. Dla release build'u z logowaniem można tymczasowo ustawić `debug = true` explicite (tylko lokalnie, nie commitować).

### Session Replay nie nagrywa

Session Replay jest aktywne tylko gdy `hasConsent == true` w momencie startu aplikacji (cold start). Zmiana consent po starcie nie włącza Session Replay do momentu ponownego uruchomienia aplikacji — jest to ograniczenie konfiguracji SDK wykonywanej jednorazowo w `Application.onCreate()`.
