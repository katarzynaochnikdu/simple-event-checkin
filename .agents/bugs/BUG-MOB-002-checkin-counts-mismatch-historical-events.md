# BUG-MOB-002: Rozbieżność liczb check-in między aplikacją mobilną a panelem web na historycznych wydarzeniach

**Zgłoszony:** 2026-05-20
**Scope:** mobile
**Severity:** P1
**Status:** ✅ Resolved (2026-05-20, Wątek A) — Wątek B w WO-MOB-013
**Powiązany WO:** [WO-MOB-012](../work_orders/WO-MOB-012-fix-checkin-counts-mismatch-historical-events.md)

## Gdzie
Aplikacja mobilna — ekran szczegółów wydarzenia (po wybraniu z listy wydarzeń), sekcja **POSTĘP CHECK-IN** + 3 liczniki **ODZNACZENI / OCZEKUJĄCY / ŁĄCZNIE**.

Podejrzane źródła danych (do zbadania, nie potwierdzone):
- `backend/api/mobile.py` → `GET /api/mobile/events/:id/checkin-stats`
- `backend/api/mobile.py` → `GET /api/mobile/events/:id/participants`
- Mobile (Kotlin) — ekran szczegółów wydarzenia + ViewModel agregujący licznik (potencjalny cache w Room/SQLite)
- Panel web — zakładka "Uczestnicy on-site" (porównawcze źródło prawdy)

## Objaw

**Główny problem (doprecyzowany przez user'a 2026-05-20):** na **desktopie (panel web) uczestnicy są oznaczeni jako "checked in" — ale na mobile NIE widać tego statusu.** Mobile nie pobiera/nie odświeża aktualnego stanu odznaczeń z backendu dla niektórych (historycznych) wydarzeń. Część eventów się zgadza, część nie — co sugeruje że scope buga to **sync stanu check-in z backendu do mobile**, prawdopodobnie cache lub źródło danych pomija odznaczenia zrobione spoza tej instancji aplikacji.

**Drugi problem (równoległy, realny):** liczba uczestników **ogółem** różni się między mobile a web — to nie jest tylko inny filtr "odznaczeni", to realnie inny zestaw rekordów pobierany przez mobile (możliwy cache z poprzedniej synchronizacji, sprzed zmian w liście uczestników).

Przykłady:
- **Dental Practice Academy Poznań (28.04.2026):** ⚠️ główny przypadek
  - Mobile: **0** odznaczonych / 49 oczekujących / 49 łącznie (POSTĘP 0%)
  - Web: **46** odznaczonych / 46 łącznie ("CHECK-IN 46 zweryfikowany")
  - Interpretacja: 46 osób BYŁO odznaczonych (najpewniej z panelu web lub z innej instancji mobile), ale ta instancja mobile widzi 0 → **mobile nie ściąga aktualnego stanu check-in z backendu.** Dodatkowo łącznie 49 vs 46 — mobile ma 3 uczestników więcej niż web (realna rozbieżność zestawu, nie filtr).
- **AMOZ Connect Kraków (15.04.2026):** ✅ check-in się zgadza, ale łącznie nie
  - Mobile: 66 odznaczonych / 41 oczekujących / **107** łącznie (POSTĘP 61%)
  - Web: 66 odznaczonych / **100** łącznie ("Pewni uczestnicy 100 / CHECK-IN 66 zweryfikowany")
  - Interpretacja: odznaczenia widoczne w obu (66=66 OK), ale mobile ma 7 uczestników więcej niż web → potwierdza drugi (niezależny) problem rozbieżności zestawu uczestników.

**Wzorzec:** dwa potencjalnie niezależne błędy:
1. **Mobile nie widzi check-inów zrobionych spoza tej instancji** (Dental: 0 vs 46) — sync stanu odznaczeń z backendu nie działa lub działa tylko dla lokalnie wykonanych check-inów.
2. **Mobile ma inny (większy) zestaw uczestników niż web** (Dental 49 vs 46, Kraków 107 vs 100) — możliwy stale cache w SQLite z poprzedniej synchronizacji, gdy lista uczestników wyglądała inaczej, albo różny filtr statusu (ale konsekwentnie mobile > web).

## Kroki repro
1. Otwórz aplikację mobilną (simple-event-checkin).
2. Wejdź w historyczne wydarzenie, np. **Dental Practice Academy Poznań** z 28.04.2026 lub **AMOZ Connect Kraków** z 15.04.2026.
3. Odczytaj liczniki **ODZNACZENI / OCZEKUJĄCY / ŁĄCZNIE**.
4. Otwórz panel web → ten sam event → zakładka **Uczestnicy on-site**.
5. Porównaj liczby — na niektórych wydarzeniach rozjazd, na innych zgodność.

## Środowisko
Produkcja (Render backend + APK release lub debug — do potwierdzenia którą wersją APK testowano).

## Notatki

### Hipotezy do zbadania (NIE naprawiać tutaj)

Po doprecyzowaniu od user'a (2026-05-20) hipotezy uporządkowane wg dwóch wątków:

**Wątek A — mobile nie widzi check-inów zrobionych spoza tej instancji (główny problem, Dental 0 vs 46):**

1. **Mobile pobiera tylko własne rekordy z `checkin_log`** (filtrowane po `scanned_by`/`device_id` aktualnego operatora lub urządzenia) zamiast źródła prawdy "ten uczestnik jest odznaczony niezależnie kto to zrobił". Jeśli web odznacza inną drogą (np. flaga `checked_in` na `event_participants`, albo inny `scanned_by`), mobile tego nie pokaże. Sprawdzić co dokładnie zwraca `get_participants_for_mobile()` i `get_checkin_stats()` w `pg_storage.py` — czy patrzy w `checkin_log` globalnie czy zawężająco.

2. **Endpoint `GET /api/mobile/events/:id/participants` nie zwraca aktualnego pola `checked_in`** (lub mobile go ignoruje i polega tylko na lokalnym SQLite). Mobile po pierwszej synchronizacji nie pull'uje aktualnego stanu odznaczeń przy ponownym otwarciu eventu.

3. **Stale cache mobile (SQLite/Room) bez TTL / bez re-sync na otwarcie eventu:** pierwsze pobranie listy uczestników zapisuje stan `checked_in=false` dla wszystkich, kolejne otwarcia ekranu nie wymuszają re-fetch'u — tłumaczy 0 odznaczonych w Dental (event cachowany przed odznaczeniami z panelu web).

**Wątek B — realnie inny zestaw uczestników (Dental 49 vs 46, Kraków 107 vs 100):**

4. **Stale cache listy uczestników w SQLite:** mobile pobrał listę uczestników tygodnie temu, od tego czasu na panelu web ktoś usunął/anulował część rejestracji — mobile widzi "stary" zestaw. Konsekwentnie mobile > web (nigdy mniej) pasuje do tej hipotezy.

5. **Różne filtry statusu między mobile a web:** mobile wlicza uczestników w statusie `cancelled`/`pending`/`unpaid`/`waitlist`, a web "Uczestnicy on-site" — nie. Sprawdzić jaki filtr stosuje `get_participants_for_mobile()` w `pg_storage.py` vs zapytanie web "Uczestnicy on-site".

**Hipotezy mniej prawdopodobne (zachowane do wykluczenia):**

6. **Offline queue nie zsynchronizowana w drugą stronę:** mało prawdopodobne tutaj — Dental ma 0 w mobile, więc to nie wyjaśnia tego głównego objawu.

7. **Race / brak refresha statystyk po zamknięciu eventu:** endpoint `checkin-stats` mógł kiedyś działać inaczej (poprawiony w WO-MOB-008 fix psycopg2 escape) i stare odpowiedzi zostały scache'owane lokalnie.

### Źródła prawdy do porównania
- `SELECT COUNT(*) FROM event_participants WHERE event_id=... AND status IN (...)` — co liczy web?
- `SELECT COUNT(*) FROM event_participants WHERE event_id=...` filtrowane jak w `get_participants_for_mobile()` — co liczy mobile?
- `SELECT COUNT(*) FROM checkin_log WHERE event_id=...` vs `SELECT COUNT(*) FROM event_participants WHERE event_id=... AND checked_in=true` — czy oba źródła zgodne?

### Screenshoty
User dołączył 5 screenshotów (3 z mobile + 2 z panelu web) w prompcie zgłoszenia. Do zachowania przy analizie — załączyć do raportu fix WO.

### Powiązania
- Powiązanie z [WO-MOB-008](../work_orders/WO-MOB-008-fix-empty-participants-list-psycopg2-comment-escape.md) (psycopg2 comment escape) — możliwe że ten fix zmienił/odsłonił zachowanie endpointu stats.
- Sprawdzić [BUG-MOB-001](BUG-MOB-001-uczestnicy-lista-pusta-mimo-stats-93.md) — pokrewny temat (mismatch stats vs participants list).
