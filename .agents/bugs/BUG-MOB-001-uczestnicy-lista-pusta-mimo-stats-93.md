# BUG-MOB-001: Lista uczestników pusta mimo że dashboard pokazuje 93 oczekujących

**Zgłoszony:** 2026-05-19
**Scope:** mobile
**Severity:** P1
**Status:** ✅ Resolved (WO-MOB-008)
**Powiązany WO:** [WO-MOB-008](../work_orders/WO-MOB-008-fix-empty-participants-list-psycopg2-comment-escape.md)
**Rozwiązany:** 2026-05-19

## Gdzie

- **Ekran:** zakładka "Uczestnicy" w bottom nav (przejście z dashboardu wydarzenia → tab "Uczestnicy")
- **Aktywne wydarzenie podczas repro:** "AMOZ Connect Gdańsk", 28 maja 2026, Hotel Novotel Marina, Gdańsk
- **Podejrzane warstwy** (do diagnozy, nie do fixu na tym etapie):
  - Network/API: `GET /api/mobile/events/:id/participants` (vs poprawnie działający `GET /api/mobile/events/:id/checkin-stats`)
  - SQLite cache uczestników (offline-first warstwa)
  - ViewModel/parsing layer (Kotlin data class dla response z `/participants`)
  - Filtr/wyszukiwarka (pole "Imię, firma, bilet, płatnik, tag...") — czy domyślnie nie obcina wyniku

## Objaw

**Niespójność między dashboardem a listą uczestników:**

**Screenshot 1 — Dashboard wydarzenia "AMOZ Connect Gdańsk":**
- Postęp check-in: 0%
- Odznaczeni: 0
- Oczekujący: **93**
- Łącznie: **93**
- Statystyki na dashboardzie poprawnie pokazują 93 uczestników

**Screenshot 2 — Ekran "Uczestnicy" (po wejściu z dashboardu):**
- Nagłówek: "Uczestnicy" z licznikiem **0**
- Pole wyszukiwania widoczne (placeholder: "Imię, firma, bilet, płatnik, tag...")
- **Lista uczestników CAŁKOWICIE PUSTA** — brak jakichkolwiek wpisów / kart / wierszy
- FAB "Dodaj uczestnika" widoczny (renderuje się normalnie)
- Bottom nav: AMOZ Connect G... / Uczestnicy (aktywny, podświetlony) / Skaner

**Konsekwencja biznesowa:** Bez listy uczestników aplikacja **nie pozwala wykonać check-in** — ani offline-owo (brak rekordów do oznaczenia), ani z poziomu skanera nie sposób zweryfikować że scan trafia w istniejącego uczestnika z lokalnego cache.

## Kroki repro

1. Otwórz aplikację Medidesk Event Check-in (Android APK, debug/preview build)
2. Zaloguj się danymi admin
3. Wybierz wydarzenie "AMOZ Connect Gdańsk" (28.05.2026)
4. Na dashboardzie zobacz statystyki: Oczekujący 93, Łącznie 93
5. Kliknij zakładkę "Uczestnicy" w bottom nav
6. **Obserwowany rezultat:** licznik "0" w nagłówku, lista pusta
7. **Oczekiwany rezultat:** licznik "93", lista 93 uczestników z możliwością filtrowania i check-in

## Środowisko

- **Build:** Android APK (prawdopodobnie debug lub preview)
- **Codebase:** `simple-event-checkin/` — Android natywny Kotlin (zgodnie z `CLAUDE.md` — mobile scope to natywny Android, nie Expo z legacy `checkin-app/`)
- **Backend target:** prawdopodobnie production (event "AMOZ Connect Gdańsk" widoczny z prawdziwymi danymi i statystykami)
- **Urządzenie z screenshota:** Android, 20:07 czasu lokalnego, LTE + słaby zasięg WiFi, naładowana bateria, przyciski nawigacyjne (◁ ○ ▢)
- **Network status:** mieszany (LTE + WiFi widoczne) — możliwa rola w problemie z fetch/sync

## Notatki

### Hipotezy do sprawdzenia (do diagnozy w /master fix, NIE rozwiązywać tu)

1. **API response mismatch** — endpoint `GET /api/mobile/events/:id/participants` może zwracać pustą listę / nieoczekiwany shape (envelope vs raw array), podczas gdy `checkin-stats` zwraca poprawne 93. Sprawdzić Logcat + odpowiedź serwera.
2. **SQLite cache pusty / nie zapełniony** — jeśli aplikacja używa offline-first cache, możliwe że pierwsze wejście nie wyzwoliło fetch+persist do Room/SQLite, a ekran "Uczestnicy" czyta tylko z lokalnej bazy.
3. **Kotlin data class drift** — response z backendu (snake_case) vs data class (camelCase bez `@SerializedName` / `@Json`), parsowanie ciche pada na pojedynczych polach → cała lista odrzucona.
4. **Filtr/query bias** — domyślny stan pola wyszukiwania może mieć wstrzyknięty niewidoczny filtr (np. tag, status) który zeruje wynik.
5. **Race condition** — stats endpoint odpalany asynchronicznie szybciej, list endpoint w trakcie loadingu — ale brak widocznego loadera/spinnera na screenie sugeruje że "loading=false" już został ustawiony i lista jest realnie pusta.
6. **Authorization scope** — czy mobile JWT (z `FLASK_SECRET_KEY`, 72h) ma dostęp do `/participants` dla tego konkretnego event_id? Stats endpoint może być bardziej liberalny niż listing.

### Powiązany kod (orientacyjnie, do potwierdzenia podczas fixu)

- `simple-event-checkin/` Android Kotlin codebase (ścieżki konkretne do ustalenia: `ParticipantsScreen.kt`, `ParticipantsViewModel.kt`, `EventRepository.kt`/podobne)
- Backend desktop monorepo: `backend/api/mobile.py` — endpoint `GET /events/:id/participants`
- Backend desktop monorepo: `backend/pg_storage.py` — funkcja `get_participants_for_mobile(event_id)`
- Shared types: `shared/api-types.ts` (jeśli mobile używa wspólnego kontraktu, choć w natywnym Kotlinie raczej własne data class)

### Pytania do user'a (jeśli /master fix odpali debugger)

- Czy lista jest pusta tylko dla "AMOZ Connect Gdańsk", czy też dla innych wydarzeń?
- Czy WiFi vs same LTE zmienia wynik (retry przy lepszym zasięgu)?
- Czy po pull-to-refresh / restart aplikacji lista się ładuje?
- Czy w starszych buildach to działało (regression vs zawsze-bug)?

## Resolution

**Naprawiono przez:** [WO-MOB-008](../work_orders/WO-MOB-008-fix-empty-participants-list-psycopg2-comment-escape.md) (2026-05-19).

**Root cause:** `backend/pg_storage.py:24005` zawierał pojedynczy `%` w komentarzu SQL (`-- ... template_key LIKE 'rsvp_%'`). psycopg2 NIE pomija komentarzy przy parsowaniu format specifierów, więc `%` był interpretowany jako placeholder bez parametru → `IndexError: list index out of range`. Funkcja `get_participants_for_mobile` ma `except Exception: return []`, więc mobile dostawał 200 OK z pustą listą mimo crashu w logach.

**Affected scope:** WSZYSTKIE eventy (nie tylko "AMOZ Connect Gdańsk") — bug deterministyczny, każde wywołanie funkcji crashowało.

**Regression source:** WO-MOB-002 commit `b7e2cc5` wprowadził LATERAL JOIN dla `mail_log` z komentarzem skopiowanym z desktop endpointu bez audytu psycopg2 escape.

**Fix:** 1 znak — `LIKE 'rsvp_%'` → `LIKE 'rsvp_%%'` w komentarzu SQL (psycopg2 `%%` = literalny `%`). Pattern dopasowany do kanonicznego escape'u w linii 24014 tej samej funkcji.

**Production evidence:** Render `srv-d61j4bogjchc73fkfiug` (`MD_Order_portal_backend`), wpisy `[DB] get_participants_for_mobile error: list index out of range` w okresie 2026-05-19 11:35 — 18:07 (dziesiątki wystąpień).

**Commit:** TBD (Krok 6.7 Master Agenta — backend submodule master + monorepo pointer bump).

**QA post-deploy (Krok 6.7+):**
- Render logs: brak nowych `[DB] get_participants_for_mobile error` przez ≥5 min po deploy.
- Mobile aplikacja: pull-to-refresh na "Uczestnicy" → licznik 93 na "AMOZ Connect Gdańsk".

**Lessons learned + ADR:** patrz [REVIEW-WO-MOB-008](../work_orders/review_notes/REVIEW-WO-MOB-008.md) + proponowany follow-up WO-MOB-009 (error visibility refactor `get_*_for_mobile`).
