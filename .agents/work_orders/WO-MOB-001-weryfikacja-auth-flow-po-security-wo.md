# WO-MOB-001: Weryfikacja auth flow po security WO (WO-201/202/204)

**Data:** 2026-05-19
**Worker:** [do uzupełnienia przez Mastera]
**Stage:** [placeholder]
**Priorytet:** [placeholder — Krytyczny / Wysoki / Normalny / Niski]

## Cel

Potwierdzenie end-to-end, że trzy security WO wykonane 2026-05-18 (WO-201: szyfrowanie JWT w EncryptedSharedPreferences, WO-202: Room migrations, WO-204: guard PII w release build + walidacja QR input) nie zepsuły istniejącego auth flow aplikacji `simple-event-checkin` przed dystrybucją debug APK do zespołu. Weryfikacja obejmuje pełen cykl: logowanie, błędne hasło, wygasanie tokenu, wylogowanie, reset hasła (jeśli funkcja istnieje) oraz pierwsze uruchomienie po migracji Room DB na urządzeniu z poprzednią wersją apki.

## Zakres

Pliki objęte analizą / weryfikacją (read-only — to WO QA, nie implementacja):
- `simple-event-checkin/app/src/main/java/...` — auth flow, login screen, token management
- `simple-event-checkin/app/src/main/java/.../data/local/` — Room DB, migrations
- `simple-event-checkin/app/src/main/java/.../security/` lub analogiczny moduł — EncryptedSharedPreferences
- `simple-event-checkin/app/src/main/java/.../ui/login/` — ekran logowania
- Logcat output z fizycznego urządzenia / emulatora podczas scenariuszy testowych

## Czego NIE ruszać

- Kodu produkcyjnego poza poprawkami regresji wykrytymi w toku QA (wszelkie fixy = nowy WO)
- `simple-event-checkin/app/build.gradle` — nie zmieniaj wersji/zależności
- Backend (`backend/api/mobile.py`, `pg_storage.py`) — weryfikacja odbywa sie po stronie klienta mobilnego
- Danych testowych w DB produkcyjnej

## Pliki startowe

- `simple-event-checkin/app/src/main/java/.../` — moduł auth/login (zidentyfikuj lokalizację po strukturze projektu)
- Kod zmieniany w WO-201, WO-202, WO-204 — punktem wejścia do QA są diff'y tych WO
- `simple-event-checkin/.agents/work_orders/` — WO-201, WO-202, WO-204 (jeśli istnieją jako pliki — do przeczytania przed testami)

## Ryzyko

- Brak migracji Room: pierwsze uruchomienie po update kasuje lokalną bazę → utrata kolejki offline check-in → do uzupełnienia po inspekcji kodu WO-202
- EncryptedSharedPreferences: klucz KeyStore może nie być dostępny na starszych API (poniżej 23) → sprawdzić min SDK
- PII guard (WO-204): zbyt agresywna filtracja logów może ukryć informacje diagnostyczne przydatne do debugowania regresji
- Token 72h: bez możliwości symulacji upływu czasu w debug APK potrzebne jest ręczne ustawienie systemowego zegara lub wstrzyknięcie wygasłego tokenu

## Definition of Done

- [ ] Scenariusz 1 (logowanie poprawne) przejść pozytywnie — token zapisany w EncryptedSharedPreferences, użytkownik trafia na ekran główny
- [ ] Scenariusz 2 (błędne hasło) — apka wyświetla stosowny komunikat, nie crashuje, nie zapisuje złego tokenu
- [ ] Scenariusz 3 (wygasły token) — apka prosi o ponowne logowanie (nie crashuje silent)
- [ ] Scenariusz 4 (wylogowanie) — token usuwany z EncryptedSharedPreferences, powrót do login screen
- [ ] Scenariusz 5 (reset hasła) — [do uzupełnienia: sprawdzić czy ekran istnieje; jeśli nie — oznaczyć jako N/A]
- [ ] Scenariusz 6 (pierwsze uruchomienie po migracji Room) — apka startuje bez crashu na urządzeniu z poprzednią wersją; dane przetrwają LUB fallbackToDestructiveMigration udokumentowany jako świadoma decyzja
- [ ] Logcat w build release nie wycieka PII (imiona, emaile, ticket ID) — potwierdzenie konfiguracji guard z WO-204
- [ ] Raport z wyników (checklist) dołączony jako Format zwrotki

## Test akceptacyjny

### Scenariusz 1 — Logowanie poprawne
1. Zainstaluj debug APK na czystym urządzeniu (lub po `adb shell pm clear <package>`).
2. Wprowadz prawidlowe dane konta testowego.
3. Oczekiwany wynik: token JWT zapisany (weryfikacja przez inspekcję EncryptedSharedPreferences lub log debug), ekran główny widoczny.

### Scenariusz 2 — Błędne hasło
1. Na ekranie logowania wprowadz bledne haslo.
2. Oczekiwany wynik: komunikat błędu widoczny (np. "Nieprawidłowe dane"), apka nie crashuje, pole hasła czyszczone LUB fokus powraca do pola.

### Scenariusz 3 — Wygasły token (symulacja)
1. Zaloguj sie poprawnie.
2. Wymuś wygasniecie tokenu: zmien zegar systemowy o +73h LUB wstaw do EncryptedSharedPreferences token z exp w przeszlości (narzedzie testowe).
3. Oczekiwany wynik: apka wykrywa 401 z backendu i przekierowuje na login screen bez crash.

### Scenariusz 4 — Wylogowanie
1. Bedzac zalogowanym, wybierz opcje wylogowania.
2. Oczekiwany wynik: powrot do login screen, token usuniety z EncryptedSharedPreferences (weryfikacja: ponowne otwarcie apki nie wchodzi automatycznie).

### Scenariusz 5 — Reset hasla
1. Na ekranie logowania sprawdz czy istnieje opcja resetu hasla.
2. Jezeli tak: przetestuj flow (email z linkiem / kod OTP). Oczekiwany wynik: pomyslne zakonczenie bez crash.
3. Jezeli nie istnieje: oznaczyc jako N/A w raporcie.

### Scenariusz 6 — Pierwsze uruchomienie po migracji Room
1. Zainstaluj poprzednia wersje APK i zaloguj sie (by utworzyc Room DB).
2. Zaktualizuj do nowej wersji APK (z WO-202).
3. Oczekiwany wynik: apka startuje bez crash; dane przetrwały (auto-migrate) LUB baza zresetowana (fallbackToDestructiveMigration) — oba warianty sa akceptowalne jezeli udokumentowane.

## Oczekiwany efekt wizualny

- Brak nowych ekranow bledu / crash dialogs w porownaniu z wersja sprzed security WO
- Login screen wyglada i zachowuje sie identycznie jak przed WO-201/202/204
- Logcat (poziom WARN+) czysty z PII w build release

## Kontrakt API (jesli zmiana full-stack)

Nie dotyczy — to WO QA read-only. Zmiany API nie sa czescia zakresu.

## Format zwrotki

- Checklist 6 scenariuszy z wynikiem PASS / FAIL / N/A + krotka notatka dla kazdego
- Logcat snippet (poziom WARN+) z momentu logowania w release build — potwierdzenie braku PII
- Ewentualna lista regresji (kazda = osobny nowy WO, nie naprawiamy tutaj)
- Decyzja: "APK gotowy do dystrybucji" lub "BLOKADA — regresja w scenariuszu X"
