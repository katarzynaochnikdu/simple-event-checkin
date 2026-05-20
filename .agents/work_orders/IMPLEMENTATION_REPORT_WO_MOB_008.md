# IMPLEMENTATION_REPORT — WO-MOB-008

**Data:** 2026-05-19
**Worker:** worker-debugger (1-line punktowy fix po pelnej diagnostyce Master Agenta)
**Status:** Compile PASS, Regression scan PASS, awaiting commit

## Cel

Naprawic ukryty bug `IndexError: list index out of range` w funkcji `get_participants_for_mobile` w `backend/pg_storage.py`. Przyczyna: psycopg2 NIE pomija komentarzy SQL przy parsowaniu format specifierow, wiec pojedynczy `%` w komentarzu (linia 24005) jest interpretowany jako placeholder bez parametru do bindowania. Funkcja ma `except Exception: return []`, wiec mobile dostaje 200 OK z pusta lista mimo ze dane istnieja. Konsekwencja: BUG-MOB-001 (lista uczestnikow pusta na wszystkich eventach w mobile aplikacji).

## Zmienione pliki

| Plik | Linia | Zmiana |
|---|---|---|
| `backend/pg_storage.py` | 24005 | `LIKE 'rsvp_%'` -> `LIKE 'rsvp_%%'` (komentarz SQL) |

**Diff stats:** 1 plik, 1 linia (+1 -1), 1 znak `%` dodany.

## Build / Compile

```
py -m py_compile backend/pg_storage.py
-> COMPILE OK
```

## Regression scan

Skan wszystkich `cur.execute("""...""", ...)` w `pg_storage.py` na obecnosc nie-escapowanych `%`:

```
Functions with bad %: 0
```

Pre-fix wynik: 1 (wlasnie linia 24005). Post-fix: 0. Caly modul jest czysty.

## Kluczowe fragmenty diff

```diff
--- a/pg_storage.py
+++ b/pg_storage.py
@@ -24002,7 +24002,7 @@ def get_participants_for_mobile(event_id: str, since: str = None) -> List[Dict[s
                 LIMIT 1
             ) lr ON true
             -- WO-MOB-002: czy faktycznie poszedl mail RSVP? Filtr identyczny z desktop
-            -- admin endpointu (pg_storage.py linie 10523-10530): template_key LIKE 'rsvp_%'
+            -- admin endpointu (pg_storage.py linie 10523-10530): template_key LIKE 'rsvp_%%'
             -- LUB scheduled campaign (sched_campaign_<id>_*), match po event_id w data
             -- LUB event_order_id, status IN ('sent','delivered'). Patrzymy tylko czy
             -- istnieje JAKIKOLWIEK taki wpis (mail_id IS NOT NULL = TRUE).
```

Pattern dopasowany do kanonicznego escape'u w linii 24014 tej samej funkcji (`LIKE 'rsvp_%%'` w aktywnym SQL).

## Gates

- **Security N/A** — zmiana wylacznie w komentarzu SQL. Brak nowych endpointow, brak zmian PII, brak zmian auth/JWT, brak nowych logow. `except Exception: return []` swiadomie pozostaje (zgodnie z WO scope — fix root cause, nie redesign error handlingu funkcji).
- **Contract Sync N/A** — zero zmian w response shape `GET /api/mobile/events/:id/participants`. Mobile DTO `ParticipantsResponse` + `ParticipantDto` bez zmian. Klient Kotlin (Android) bez zmian. `shared/api-types.ts` bez zmian (mobile uzywa wlasnych Kotlin data classes).
- **Migration N/A** — zero zmian w DB schema, zero migracji SQL, brak nowych tabel/kolumn/indeksow.
- **QA DEFERRED do post-deploy** — wymaga Render auto-deploy z `master` (~2-3 min) oraz fizycznej weryfikacji w aplikacji Android (pull-to-refresh na "Uczestnicy" -> oczekiwany licznik 93 na evencie "AMOZ Connect Gdansk"). Render logs `srv-d61j4bogjchc73fkfiug` powinny przestac generowac `[DB] get_participants_for_mobile error: list index out of range`.

## Postmortem

### Co dziala

- Compile PASS, regression scan czysty, diff atomic (1 znak), zero ryzyka rozprzestrzenienia.
- Pattern escape jest spojny z istniejacym kanonicznym escape'em w linii 24014 tej samej funkcji — sredni programista ogarnie to bez konsultacji.
- Fix nie zmienia semantyki zapytania (komentarz nadal czytelny po psycopg2 unescape jako `LIKE 'rsvp_%'`).
- Mobile Kotlin codebase **bez modyfikacji** — bug byl 100% backendowy, mobile reaguje poprawnie na pusta liste (renderuje "0" + brak wierszy) i bedzie poprawnie reagowac na pelna liste po deploy.
- Backwards compat 100% — zero klientow musi sie zaktualizowac.

### Co nie dziala / known

- **Bezposredni QA niemozliwy lokalnie** — funkcja `get_participants_for_mobile` ma `except Exception: return []`, wiec do pelnego potwierdzenia trzeba albo (a) Render deploy + sprawdzenie logow + ekran mobile, albo (b) lokalny postgres z prod-like danymi + odpalenie endpointu. Worker robi mocniejszy compile + regression scan, ale empiryczna weryfikacja jest deferred do Krok 6.7 (post-deploy).
- **Komentarz w linii 24005 dalej zawiera `%%` ktore czlowiek czytajacy diff moze pomylic z literalem** — drobny readability tradeoff, ale wskazuje na psycopg2 escape pattern i jest spojny z linia 24014. Akceptowalne.

### Co odlozone

- **Defensive: rozwazyc usuniecie `except Exception: return []`** w funkcji `get_participants_for_mobile`. To wzorzec ktory uksztaltowal silent failure na produkcji przez X godzin — log wpis byl, ale mobile UI nie odroznialo "pusta lista" od "blad serwera". Refactor jest **out-of-scope WO-MOB-008** (zakres: 1 znak), ale zaslugiwany na osobny WO **WO-MOB-009 (proponowany): error visibility dla mobile endpoints — return 500 zamiast 200+[] na exception**.
- **Audyt innych funkcji `get_*_for_mobile` w `pg_storage.py`** pod katem podobnego `except: return []` pattern — fragile silent-failure spread byc moze powtarza sie.
- **Render logs monitoring po deploy** — confirm zero nowych `[DB] get_participants_for_mobile error` przez >=5 min po push.

### Lessons learned

1. **psycopg2 NIE pomija komentarzy SQL** przy parsowaniu format specifierow — wynika to z mechanizmu C-na-niskim-poziomie ktory skanuje caly string przed substytucja. `LIKE 'rsvp_%'` w komentarzu `--` zachowuje sie tak samo jak `LIKE 'rsvp_%'` w SQL — psycopg2 chce parametr i nie dostaje go. **Zasada: nie pisz `%` w komentarzach do `cur.execute()` — uzyj `%%` lub przeformuluj.**

2. **Silent failure `except Exception: return []` to debugging hell** — bez Render logs nie byloby mozna zlapac tego buga. Mobile dostawal 200 OK + `{count: 0, participants: []}`, dashboard pokazywal poprawne 93 (inna funkcja). Bez sygnalu bledu w response trzeba bylo czekac na to az ktos rzuci okiem na logi srv-d61j4bogjchc73fkfiug. **Zasada: error visibility > graceful degradation dla data-critical endpoints.**

3. **Regression tests jako dedykowane Python skanery sa skuteczne** dla SQL escape patterns. Skaner `cur.execute -> SQL string -> count bad %` (rozroznia `%s`, `%(name)s`, `%%`, vs golemy `%`) wykryl problem w jednej linii sposrod 512 wywolan. **Wzor: budowac one-shot diagnostic skanery dla problemow ktore latwiej znalezc przez regex niz przez manualne czytanie.**

4. **Microscopowy fix x dramatic blast radius** — 1 znak naprawia funkcjonalnosc dla **wszystkich** eventow dla **wszystkich** uzytkownikow mobile. Cost/benefit jest absurdalnie korzystne. Atomic commit + revert trivial w razie regress.

## Cross-references

- **Bug:** [BUG-MOB-001](../bugs/BUG-MOB-001-uczestnicy-lista-pusta-mimo-stats-93.md) — status updated do `Resolved (WO-MOB-008)` w sekcji `## Resolution`.
- **WO:** [WO-MOB-008](WO-MOB-008-fix-empty-participants-list-psycopg2-comment-escape.md).
- **Regression source:** WO-MOB-002 commit `b7e2cc5` (backend submodule) — wprowadzil LATERAL JOIN dla `mail_log` z komentarzem opisujacym oryginalny pattern desktop endpointu (`'rsvp_%'`). Pattern w komentarzu byl literalna kopia desktop endpointu, ktory NIE byl interpolowany przez psycopg2 (inna funkcja, inny dispatch). Lesson: kopiowanie komentarzy miedzy funkcjami wymaga audytu escapingu.
- **Production evidence:** Render `srv-d61j4bogjchc73fkfiug` (`MD_Order_portal_backend`), wpisy `[DB] get_participants_for_mobile error: list index out of range` w okresie 2026-05-19 11:35 — 18:07 (dziesiatki wystapien, deterministyczne).
- **Snapshot tag:** wymagany do utworzenia przez Master Agenta przed commit'em w Krok 6.7 (proponowana nazwa: `snapshot/pre-wo-mob-008-psycopg2-comment-escape-2026-05-19`).
- **Backend commit:** TBD (Krok 6.7 — Master Agent dispatch).
- **Render auto-deploy:** ~2-3 min po push backend submodule do `master`.

## Pliki gotowe do commit

Backend submodule (`backend/`):
- `pg_storage.py` — 1 linia zmieniona (24005)

Monorepo root:
- Submodule pointer bump po commit backend
- `simple-event-checkin/.agents/work_orders/IMPLEMENTATION_REPORT_WO_MOB_008.md` (nowy)
- `simple-event-checkin/.agents/work_orders/review_notes/REVIEW-WO-MOB-008.md` (nowy)
- `simple-event-checkin/.agents/bugs/BUG-MOB-001-uczestnicy-lista-pusta-mimo-stats-93.md` (zaktualizowany status + sekcja Resolution)
- `simple-event-checkin/.agents/bugs/INDEX.md` (zaktualizowany status + WO link)
