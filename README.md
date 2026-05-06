# Simple Event Check-In — Event Lab

Uproszczona aplikacja mobilna do obsługi check-inu uczestników na wydarzeniach.
Przeznaczona dla obsługi na miejscu — szybka, offline-capable, skupiona na jednym zadaniu.

---

## Zakres funkcjonalny (MVP)

### 1. Logowanie
- Logowanie kontem administratora (te same konta co panel webowy)
- JWT token, sesja persystowana lokalnie
- Wylogowanie

### 2. Lista wydarzeń
- Wyświetlenie dostępnych wydarzeń
- Wybór wydarzenia do obsługi check-inu

### 3. Lista uczestników
- Lista uczestników wybranego wydarzenia
- Informacje: imię, nazwisko, typ biletu, status check-inu
- Wyszukiwanie uczestnika po nazwisku / emailu

### 4. Ręczny check-in uczestnika
- Wyszukanie uczestnika na liście
- Potwierdzenie check-inu jednym tapnięciem
- Informacja zwrotna: sukces / już zaczekowany / błąd

### 5. Skaner kodów QR
- Skanowanie kodu QR z biletu uczestnika
- Automatyczne wyszukanie uczestnika po `backstage_ticket_id`
- Ekran potwierdzenia z danymi uczestnika
- Możliwość anulowania / potwierdzenia check-inu

### 6. Statystyki check-inu
- Liczba zacheck-inowanych vs wszystkich uczestników
- Pasek postępu
- Odświeżanie w czasie rzeczywistym

---

## Stack technologiczny (planowany)

- **Framework:** Expo (React Native, TypeScript)
- **Routing:** Expo Router (file-based)
- **Kamera/QR:** `expo-camera` + `expo-barcode-scanner`
- **Auth:** JWT via Secure Store (`expo-secure-store`)
- **API:** Backend Medidesk (`/api/mobile/*`)
- **Offline:** `expo-sqlite` (cache uczestników, kolejka check-inów)
- **Build:** EAS Build (Android APK + iOS)

---

## API (z istniejącego backendu)

Wszystkie endpointy już istnieją w `backend/api/mobile.py`:

| Endpoint | Opis |
|---|---|
| `POST /api/mobile/login` | Logowanie, zwraca JWT |
| `GET /api/mobile/me` | Dane zalogowanego użytkownika |
| `GET /api/mobile/events` | Lista wydarzeń |
| `GET /api/mobile/events/:id/participants` | Lista uczestników wydarzenia |
| `POST /api/mobile/checkin` | Check-in uczestnika (po ticket_id) |
| `POST /api/mobile/checkin/sync` | Sync offline check-inów |
| `GET /api/mobile/events/:id/checkin-stats` | Statystyki check-inu |

---

## Powiązanie z monorepo

Ten projekt jest submodułem monorepo `Event_orders_portal_monorepo`.
Współdzieli backend z aplikacją `checkin-app/` (pełna wersja) — używa tych samych endpointów API.

Różnica względem `checkin-app/`:
- Uproszczony UI, bez zaawansowanych funkcji
- Przeznaczony jako wersja demonstracyjna / Event Lab
- Szybszy onboarding dla nowych operatorów

---

## Status

`SCAFFOLD` — projekt w fazie planowania. Kod nie jest jeszcze napisany.
