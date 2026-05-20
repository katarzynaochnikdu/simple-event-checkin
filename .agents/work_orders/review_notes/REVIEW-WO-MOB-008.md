# REVIEW-WO-MOB-008 — Fix `get_participants_for_mobile` psycopg2 comment escape

**Data:** 2026-05-19
**Status:** Compile PASS, Regression scan PASS, awaiting commit
**Worker:** worker-debugger

## DoD checklist

- [x] Linia `backend/pg_storage.py:24005` zaktualizowana z `'rsvp_%'` -> `'rsvp_%%'`
- [x] `py -m py_compile backend/pg_storage.py` -> PASS
- [x] Regression scan `pg_storage.py` -> `Functions with bad %: 0`
- [x] Diff atomic: 1 plik, 1 linia, 1 znak (`%` -> `%%`)
- [x] Pattern spojny z linia 24014 (istniejacy kanon escape w tej samej funkcji)
- [x] Mobile Kotlin codebase — bez zmian (bug 100% backendowy)
- [x] IMPLEMENTATION_REPORT_WO_MOB_008.md utworzony
- [x] BUG-MOB-001 status updated -> Resolved (WO-MOB-008)
- [x] BUG-MOB-001 INDEX.md zaktualizowany
- [x] Security gate: N/A (komentarz SQL)
- [x] Contract Sync gate: N/A (brak zmiany shape)
- [x] Migration gate: N/A (brak zmian DB)
- [ ] Commit message wskazuje `WO-MOB-008` + `BUG-MOB-001` — Krok 6.7 Mastera
- [ ] Backend deploy na Render (auto z `master`) — Krok 6.7
- [ ] Render logs post-deploy: brak `[DB] get_participants_for_mobile error` przez >=5 min — QA post-deploy
- [ ] QA mobile: pull-to-refresh -> licznik 93 na "AMOZ Connect Gdansk" — QA post-deploy
- [ ] Aktualizacja `system_state.md` + `current_stage.md` — Krok 6 Mastera

## Recommendation

**APPROVED dla commit + push.** Trivial 1-character fix z dramatic blast radius (cala mobile lista uczestnikow przestala dzialac na wszystkich eventach od WO-MOB-002 commit `b7e2cc5`).

Pre-fix verification:
- Production logs evidence: dziesiatki wpisow `IndexError: list index out of range` w funkcji
- Diagnostyka root cause: psycopg2 parsuje `%` w komentarzach SQL jako format spec
- Diagnostyka czemu mobile dostawal 200 OK: `except Exception: return []` w funkcji

Post-fix verification (lokalna):
- Compile PASS
- Regression scan czysty (0 bad % w calym pg_storage.py)
- Diff dopasowany do kanonicznego patternu z linii 24014

## Architectural decision do dopisania do `.agents/context/decision_log.md`

**Proponowany ADR (Master Agent dopisze w Krok 6):**

```
### 2026-05-19 — ADR-NNN: psycopg2 SQL comment escape pattern dla `cur.execute(""" ... """)` blocks

Kontekst. WO-MOB-002 wprowadzil komentarz SQL w funkcji `get_participants_for_mobile`
z literalnym `%` (linia 24005). psycopg2 NIE pomija komentarzy przy parsowaniu format
specifierow, wiec pojedynczy `%` w komentarzu jest interpretowany jako placeholder bez
parametru -> `IndexError: list index out of range`. Funkcja miala `except Exception: return []`,
wiec mobile dostawal 200 OK z pusta lista mimo `IndexError` w logach (silent failure).

Decyzja. Reguly dla wszystkich `cur.execute(""" ... """)` w `pg_storage.py`:

1. Komentarze SQL `--` w f-stringach do `cur.execute()` MUSZA escape'owac `%` jako `%%`,
   identycznie jak literaly w aktywnym SQL.
2. Pattern reference: `LIKE 'rsvp_%%'` (linia 24014 tej samej funkcji to kanon).
3. Walidacja: dedykowany regex skaner (`cur\\.execute\\(...\\)` -> zlicz bad `%`)
   uruchamiany jako pre-commit hook lub manualnie po edycji SQL bloku
   (worker-debugger procedura).
4. Alternatywa odrzucona: "usuwac `%` z komentarzy" — traci czytelnosc patternu SQL.
5. Alternatywa odrzucona: "owrap komentarz w Python `#` zamiast SQL `--`" — niemozliwe,
   komentarz musi byc czesc SQL (psycopg2 widzi caly string).

Konsekwencje.
- Zero ryzyka regresu — `%%` po psycopg2 unescape jest `%`, semantyka SQL bez zmian.
- Drobny tradeoff czytelnosci — czlowiek czytajacy plik widzi `%%` w komentarzu.
  Akceptowalne, bo pattern jest pojedynczy i lokalny.
- Mozliwy refactor `except Exception: return []` na bardziej widoczne error handling
  (proponowany WO-MOB-009 follow-up — error visibility dla mobile endpoints).
```

## Follow-up

- **WO-MOB-009 (proponowany):** Error visibility refactor dla `get_participants_for_mobile`
  i innych `get_*_for_mobile` funkcji w `pg_storage.py`. Zamiast `except Exception: return []`,
  proponowac propagacje wyjatku do warstwy API ktora zwroci 500 z error code. Argumenty:
  silent failure ukryl tego buga przez X godzin na produkcji; mobile UI nie odroznia
  "pustego eventu" od "crashu serwera"; debugging wymagal Render logs ktore admin musi
  recznie sprawdzac.
- **Audyt:** `Grep` pattern `except Exception:\\s*\\n\\s*return \\[\\]` w `pg_storage.py`
  dla wykrycia analogicznych silent-failure miejsc.
- **Pre-commit hook:** dodanie regression scan SQL `%` jako lint check w CI.

## Postmortem workera-debugger (3 pytania per procedura)

### 1. Czy ten bug moze wystapic w innych miejscach? — NIE

Regression scan calego `pg_storage.py` (512 cur.execute calls) potwierdzil ZERO innych
funkcji z bad `%`. To bylo jedyne wystapienie patternu — wprowadzone przez WO-MOB-002
przez kopiowanie komentarza desktop endpointu bez audytu psycopg2 escape.

Niemniej **gotcha jest godna dopisania** do `.agents/context/known_gotchas.md` (proponowana tresc):

```markdown
### psycopg2 NIE pomija komentarzy SQL przy parsowaniu `%` format specifierow

Symptom. `IndexError: list index out of range` z `cur.execute("""...""", params)`,
mimo ze liczba `%s` w aktywnym SQL zgadza sie z params.

Przyczyna. psycopg2 parsuje caly string przed substytucja, wlacznie z komentarzami
`--`. Pojedynczy `%` w komentarzu = placeholder bez parametru.

Fix. Escape jako `%%` (literalny `%` po psycopg2 unescape) — identycznie jak
literaly w aktywnym SQL.

Pulapki:
- Funkcje z `except Exception: return []` ukryja crash — log wpis bedzie, ale klient
  dostanie pusta liste z 200 OK.
- Komentarze kopiowane z innych funkcji (desktop endpoint -> mobile endpoint) sa
  glownym vectorem — pattern w komentarzu opisuje SQL, ale escape jest inny dla
  innej funkcji.

Detekcja. Regex scanner `cur\\.execute\\(\\s*f?"""(.*?)""",\\s*[^)]*\\)` z liczeniem
bad `%` (rozroznia `%s`, `%(name)s`, `%%`, vs goly `%`).

Wystapienia:
- WO-MOB-008 (2026-05-19): `pg_storage.py:24005` w `get_participants_for_mobile`,
  caly mobile participants endpoint byl crashowany od WO-MOB-002 commit `b7e2cc5`.
```

### 2. Czy fix wymusza nowa regule "tego nie wolno"? — TAK (slabo)

Proponowany dopisek do `.agents/context/constraints_do_not_break.md`:

```markdown
### NN. psycopg2 SQL comments — escape `%` jako `%%`

Wszystkie komentarze SQL `--` w f-stringach do `cur.execute()` MUSZA escape'owac
literalny `%` jako `%%`. psycopg2 parsuje komentarze tak samo jak aktywny SQL.

Pattern reference: `backend/pg_storage.py:24014` (`LIKE 'rsvp_%%'`).

Pulapka: silent failure przez `except Exception: return []` ukryje bug — klient
dostanie 200 OK z pusta lista zamiast 500.

Incydent: 2026-05-19, BUG-MOB-001 -> WO-MOB-008. Cala mobile lista uczestnikow
przestala dzialac na wszystkich eventach przez ~7h od WO-MOB-002 commit b7e2cc5
do WO-MOB-008 fix.
```

Slabo, bo to bardziej "gotcha techniczna" niz "iron rule biznesowy" jak Unicode
albo JSONB. Master Agent zdecyduje czy promotnac do constraints_do_not_break.md
czy zostawic w known_gotchas.md.

### 3. Czy sa implikacje bezpieczenstwa? — NIE

Fix dotyczy WYLACZNIE komentarza SQL — zero zmian w:
- auth flow (JWT mobile bez zmian)
- PII exposure (response shape bez zmian, dane juz byly dostepne dla autoryzowanych mobile clients)
- secrets / config (zero zmian)
- audit log (zero zmian, ale ironicznie — `except: return []` ZAMASKOWAL audit signal)
- upload / file handling (N/A)

Security audit MANDATORY — **NIE**. To czysto bugfix poziomu syntax SQL.

EDGE CASE: Argumentowac mozna ze silent failure jest **availability concern** (DoS-like
self-inflicted — backend rzucal IndexError na kazde wywolanie, ale mobile dostawalo 200 OK),
ale to **operational concern**, nie security. Threat model: nie da sie tego wyexploitowac
ofensywnie.
