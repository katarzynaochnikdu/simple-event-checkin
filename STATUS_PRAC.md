# Status prac — simple-event-checkin (Android)

> Ostatnia aktualizacja: 2026-05-28

---

## Aplikacja: co to jest?

Natywna aplikacja Android (Kotlin + Jetpack Compose) do obsługi check-inu na wydarzeniach Medidesk.
Organizator skanuje bilety QR → uczestnicy dostają odznaczenie → statystyki w czasie rzeczywistym.

**Stack:** Kotlin, Jetpack Compose, Material 3, Hilt, Room (SQLite offline), Moshi, Retrofit, Coroutines/Flow.

---

## Ekrany i stan

### ✅ Logowanie (`LoginScreen`)
- JWT auth, te same konta co panel webowy
- **DEV:** dane logowania prefillowane (konto `testapki@medidesk.pl`; hasło + instrukcja usunięcia przed release w `DEV_NOTES.md`)

### ✅ Lista wydarzeń (`EventsScreen`)
- Zakładki: **Trwające** / **Nadchodzące** / **Przeszłe** (WO-MOB-017)
  - **Trwające** pojawia się jako pierwsza i jest auto-wybrana **tylko gdy ≥1 wydarzenie aktualnie trwa**; gdy nic nie trwa — znika, domyślnie „Nadchodzące".
  - Klasyfikacja **data-granularna** (`isOngoing/isUpcoming/isPast(today: LocalDate)`, ignoruje godziny): event jest „Trwające" dla całego zakresu `[startDay..endDay]` włącznie. Naprawia błąd „event w dniu wydarzenia wpadał w przeszłość".
- **Sandbox events** (nazwa zawiera sandbox/test lub `status=draft`): wyświetlane **inline** w grupach wg daty z **pomarańczowym pillem „SANDBOX"** (`#F97316`), **widoczne tylko w buildach debug** (`BuildConfig.DEBUG`); w release/preview całkowicie ukryte (WO-MOB-018). Brak osobnej zakładki „Sandbox".
- Wyszukiwarka po nazwie i miejscu (zakładki nie migoczą podczas wpisywania)
- Kompaktowe karty (~80dp): lewy pasek koloru wydarzenia + logo 56dp + nazwa/data/miejsce
- Kolory i logo pobierane z API (`primary_color`, `logo_url`)

### ✅ Dashboard (`DashboardScreen`)
- Header z kolorem wydarzenia (bez bannera graficznego)
- Strzałka ← powrót do listy wydarzeń
- Kafelki: procent check-in, ODZNACZENI / OCZEKUJĄCY / ŁĄCZNIE (klikalne — filtrują listę)
- Menu: **Moi podopieczni** (placeholder, "Wkrótce") + **Uczestnicy** + **Statystyki**
- Przycisk synchronizacji z licznikiem oczekujących
- Widok uczestnika (role ≠ organizer): Mój Bilet z QR-kodem + przycisk trybu organizatora

### ✅ Skaner QR (`ScannerScreen`)
- Kamera + dekoder QR (CameraX + ML Kit)
- Offline-first: check-in trafia do SQLite, auto-sync po powrocie sieci
- Wynik: success / already checked / not found / error

### ✅ Lista uczestników (`ParticipantsScreen`)
- Filtrowanie: wszyscy / odznaczeni / oczekujący / po typie biletu
- Wyszukiwarka
- Kliknięcie → szczegóły uczestnika

### ✅ Szczegóły uczestnika (`ParticipantDetailsScreen`)
- Brak TopAppBar / sticky bannera — strzałka ← na poziomie awatara
- Awatar z inicjałami, nazwa, firma, typ biletu, tagi
- Status płatności: nieopłacony = dokument czerwony (proforma), płatność oczekuje = amber, opłacony = zielony
- **Check-in / Undo check-in** z potwierdzeniem (AlertDialog)
- Snackbar z wynikiem operacji

### ✅ Statystyki (`StatsScreen`)
- Sekcja "SKANERY" (dawniej "WYDAJNOŚĆ SKANERÓW")
- Timeline skanowań godzinowych
- Top skanerzy

---

## Backend API (mobile endpoint)

Moduł: `backend/api/mobile.py` — prefix `/api/mobile`

| Endpoint | Status | Uwagi |
|---|---|---|
| `POST /login` | ✅ | JWT, 72h |
| `GET /me` | ✅ | |
| `GET /events` | ✅ | Zwraca `logo_url`, `primary_color`, `secondary_color`, `accent_color` |
| `GET /events/:id/participants` | ✅ | Offline cache w Room |
| `POST /checkin` | ✅ | Race-condition safe (`SELECT FOR UPDATE`) |
| `POST /checkin/sync` | ✅ | Batch sync offline queue |
| `GET /events/:id/checkin-stats` | ✅ | Dashboard data |
| `GET /events/:id/dashboard` | ✅ (naprawiony) | Fix: usunięto filtr `action='checkin'` (kolumna nie istnieje) |

### Poprawki backendu w tej sesji
- Usunięto duplikat `get_mobile_dashboard` (Python – drugie def nadpisuje pierwsze)
- Usunięto `AND cl.action = 'checkin'` – kolumna `action` nie istnieje w `checkin_log`
- Dodano `AND cl.sync_status != 'duplicate'` zamiast
- Dodano logowanie `[DASHBOARD v3]` na każdym kroku
- Endpoint `/events` zwraca teraz branding wydarzenia (kolory + logo)

---

## Nawigacja

```
Login → EventsScreen → MainScreen(eventId)
                         ├── [Tab] Lista → (onBackToEvents → EventsScreen)
                         ├── [Tab] Uczestnicy → ParticipantsScreen → ParticipantDetailsScreen
                         └── [Tab] Skaner → ScannerScreen
                         
                         (wewnętrzny NavHost startuje od DashboardScreen)
```

Bottom nav w evencie:
- **Lista** — wraca do EventsScreen (nie jest innerNav — to akcja `onBackToEvents`)
- **Uczestnicy** — innerNav do ParticipantsScreen
- **Skaner** — innerNav do ScannerScreen

---

## Offline-first architektura

- Room (SQLite): tabela `participants` per event, tabela `checkin_log` (pending sync)
- SyncEngine: obserwuje status sieci, auto-sync po powrocie, triggerowany ręcznie z Dashboard
- Konflikty: serwer wygrywa (backend `SELECT FOR UPDATE` zapobiega double-checkin)

---

## Co zostało do zrobienia (backlog)

| Priorytet | Zadanie | Uwagi |
|---|---|---|
| P1 | **Ekran "Moi podopieczni"** | Placeholder w Dashboard, user zapowiedział "zaraz dorabiamy" |
| P1 | **Build APK produkcyjny** | `npx eas build --platform android --profile preview` |
| P1 | **Deploy backendu na Render** | Push zmian mobile.py + pg_storage.py |
| P2 | Wyświetlenie kodu QR uczestnika | Ekran "Mój Bilet" — currently placeholder Icon |
| P2 | Filtrowanie po typie biletu | ParticipantsScreen → ticketClassId flow |
| P2 | Paginacja listy uczestników | Przy dużych eventach |
| P3 | Ciemny motyw | Material 3 Dark theme dopracowanie |
| P3 | Push notifications | Powiadomienie o ważnym wydarzeniu |
| P3 | Wiele eventów jednocześnie | Multi-event organizer view |

---

## Pliki kluczowe

```
simple-event-checkin/
├── app/src/main/java/pl/medidesk/mobile/
│   ├── navigation/
│   │   ├── AppNavHost.kt          # Główna nawigacja + MainScreen z bottom nav
│   │   └── Screen.kt              # Definicje tras
│   └── MainActivity.kt
├── features/
│   ├── feature-auth/              # LoginScreen + LoginViewModel
│   ├── feature-dashboard/         # DashboardScreen + StatsScreen + DashboardViewModel
│   ├── feature-events/            # EventsScreen + EventsViewModel + EventsRepository
│   ├── feature-participants/      # ParticipantsScreen + ParticipantDetailsScreen + ViewModels
│   └── feature-scanner/           # ScannerScreen + ScannerViewModel
├── core/
│   ├── core-model/                # DashboardData, EventItem, Participant, itp.
│   ├── core-network/              # MobileApiService, ResponseDtos
│   ├── core-database/             # Room DB, DAOs, ParticipantEntity
│   ├── core-datastore/            # AuthDataStore (JWT, user info)
│   ├── core-sync/                 # SyncEngine (offline queue)
│   └── core-ui/                   # StatusColors, MdAsyncImage, LoadingScreen
└── DEV_NOTES.md                   # Jak usunąć dev shortcuts przed release
```

---

## Zmienne środowiskowe / konfiguracja

- `EXPO_PUBLIC_API_URL` → nie dotyczy (to Expo, nie ten projekt)
- API URL: hardcodowany w `core-network/` (szukaj `BASE_URL` lub `ApiModule`)
- Zmień URL na produkcyjny przed buildiem release

---

## Build i deploy

```bash
# Debug build (dla testów lokalnych via ADB)
./gradlew assembleDebug

# Install na podłączonym urządzeniu
adb install app/build/outputs/apk/debug/app-debug.apk

# EAS Build (jeśli skonfigurowany)
npx eas build --platform android --profile preview
```

---

## Znane ograniczenia / pułapki

1. **Moshi + non-null fields** — jeśli backend zwróci `null` dla pola zadeklarowanego bez `?`, app crasha. Wszystkie nowe pola DTO muszą być `String? = null`.
2. **`checkin_log` brak kolumny `action`** — naprawione w tej sesji. Nie dodawać filtra `action` bez migracji DB.
3. **Python — dwa def o tej samej nazwie** — drugie nadpisuje pierwsze CICHO. Zawsze grep przed dodaniem nowej funkcji.
4. **`statusBarsPadding()` jako jedyna Box** — tworzy dużą pustą przestrzeń. Zawsze łączyć z treścią w jednym Column.
5. **Room `fallbackToDestructiveMigration()`** — bezpieczne dla dev, na produkcji upewnij się że migracje są poprawne.
