# MD Mobile Android — Roadmap Rozbudowy

> Utworzono: 2026-03-11 | Status: W trakcie realizacji

## 🎯 Faza 1 — Funkcjonalności Organizatora (AKTUALNIE)

### 1.1 📱 InHub z kamerą QR
- [ ] Integracja CameraX + MLKit w trybie InHub Active
- [ ] Auto check-in po skanowaniu (bazuje na konfiguracji)
- [ ] Ekran wyszukiwania uczestnika w InHub

### 1.2 🏢 Moduł Firm / Sponsorów / Prelegentów
- [ ] Ekran listy firm przypisanych do wydarzenia
- [ ] Ekran listy sponsorów
- [ ] Ekran listy prelegentów
- [ ] Szczegóły firmy/sponsora/prelegenta
- [ ] Endpointy backendowe (jeśli nie istnieją)

### 1.3 🔍 Szybkie wyszukiwanie uczestników
- [ ] Search bar na ekranie uczestników
- [ ] Filtrowanie po nazwisku, emailu, firmie
- [ ] Wyszukiwanie globalne (z poziomu Home)

### 1.4 📊 Rozbudowane statystyki
- [ ] Wykres timeline check-inów (już mamy dane z API)
- [ ] Breakdown wg klasy biletu (już mamy dane)
- [ ] Top scanners ranking
- [ ] Heatmap godzinowy

### 1.5 ⚙️ Ekran Ustawień
- [ ] Motyw (dark/light mode)
- [ ] Częstotliwość auto-synchronizacji
- [ ] Informacje o aplikacji (wersja, build)

---

## 🔮 Faza 2 — Rozszerzenia UX

### 2.1 🎨 UI/UX Polish
- [ ] Splash screen z animacją logo
- [ ] Onboarding flow (pierwsze uruchomienie)
- [ ] Micro-animacje i transitions między ekranami
- [ ] Pull-to-refresh na listach
- [ ] Skeleton loading zamiast spinnerów

### 2.2 📶 Offline-first improvements
- [ ] Widoczność stanu synchronizacji na każdym ekranie
- [ ] Zarządzanie konfliktami (check-in offline vs online)
- [ ] Retry policy z exponential backoff
- [ ] Wizualna kolejka oczekujących operacji

### 2.3 🎪 Moduł Atrakcji
- [ ] Lista atrakcji wydarzenia
- [ ] Szczegóły atrakcji
- [ ] System ankiet (polls) — tworzenie i przeglądanie wyników
- [ ] Zarządzanie sesłami w atrakcjach

---

## 🚀 Faza 3 — Zaawansowane funkcje

### 3.1 🔔 Push Notifications
- [ ] Firebase Cloud Messaging (FCM) setup
- [ ] Alerty o progach frekwencji (50%, 75%, 90%)
- [ ] Powiadomienia o nowych walk-in registracjach
- [ ] Kanały powiadomień (check-in, admin, alerts)

### 3.2 📤 Eksport danych
- [ ] Generowanie PDF raportu z dashboardu
- [ ] Eksport listy uczestników do CSV
- [ ] Share raportów przez systemowy share sheet
- [ ] Zapis do pliku lokalnego

### 3.3 🔐 Bezpieczeństwo
- [ ] Biometric login (fingerprint/face)
- [ ] Auto-logout po bezczynności
- [ ] Screen pinning w trybie InHub
- [ ] Certificate pinning dla API

---

## 📝 Notatki techniczne

- **Stack**: Kotlin 2.0.21, Jetpack Compose (M3), Hilt, Retrofit, Room, WorkManager
- **Architektura**: Multi-module Clean Architecture
- **Backend**: Flask API (`/api/mobile/`) na Render.com
- **Min SDK**: API 26 (Android 8.0)
