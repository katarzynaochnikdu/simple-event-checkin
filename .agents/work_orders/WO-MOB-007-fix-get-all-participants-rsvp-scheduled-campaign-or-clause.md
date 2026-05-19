# WO-MOB-007: Fix drift `get_all_participants` rsvp_sent — dodaj scheduled campaign OR clause

**Data:** 2026-05-19
**Scope:** mobile (simple-event-checkin)
**Worker:** [do uzupełnienia przez Mastera]
**Stage:** [placeholder]
**Priorytet:** [placeholder — sugerowany: Normalny (microscopowy fix konsystencji, ale silent drift z konsekwencją dla RSVP raportowanego z scheduled campaigns)]

## Cel

Drobny fix konsystencji — w `backend/pg_storage.py:10791` funkcja `get_all_participants` ml_sub WHERE clause używa wyłącznie `AND ml.template_key LIKE 'rsvp_%%'`, podczas gdy 4 pozostałe funkcje rsvp_sent (linie 10525, 10702, 10968 desktop + 24007 mobile) używają OR ze scheduled campaign matcher (`OR ml.template_key ~ '^sched_campaign_[0-9]+_'`). Skutek: uczestnicy którzy otrzymali RSVP przez zaplanowaną kampanię (`sched_campaign_<id>_rsvp_initial`) nie pojawią się z `rsvp_sent=true` w response `get_all_participants` — przy obecności tego flagi w 4 innych funkcjach. Pre-existing drift, flagged przez Contract Sync gate WO-MOB-006.

## Zakres

Worker MA PRAWO modyfikować:
- `backend/pg_storage.py` — wyłącznie linia 10791 (1 linia SQL → 4 linie z OR clause, w obrębie subzapytania ml_sub funkcji `get_all_participants`)

## Czego NIE ruszać 🛑

- Pozostałe 4 funkcje rsvp_sent (linie 10525, 10702, 10968, 24007) — są już poprawne, NIE dotykać.
- Inne sekcje `pg_storage.py` — plik 868KB, ostrożnie ze zmianami SQL.
- Migracje DB — N/A, czysto warstwa odczytu.
- Frontend / mobile Android — N/A.

## Pliki startowe

- `backend/pg_storage.py:10791` — linia do zmiany (subzapytanie `ml_sub` w funkcji `get_all_participants`).
- `backend/pg_storage.py:10525` — referencyjny wzorzec WHERE clause (już z OR).
- `backend/pg_storage.py:10702` — referencyjny wzorzec WHERE clause (już z OR).
- `backend/pg_storage.py:10968` — referencyjny wzorzec WHERE clause (już z OR).
- `backend/pg_storage.py:24007` — referencyjny wzorzec WHERE clause (już z OR) — funkcja mobile `get_participants_for_mobile`.

## Konkretna zmiana

W `backend/pg_storage.py:10791`:

**PRZED:**
```sql
                      AND ml.template_key LIKE 'rsvp_%%'
```

**PO:**
```sql
                      AND (
                          ml.template_key LIKE 'rsvp_%%'
                          OR ml.template_key ~ '^sched_campaign_[0-9]+_'
                      )
```

## Ryzyko

- 🟢 Niskie. Zmiana czysto rozszerzająca matcher — uczestnicy którzy do tej pory mieli `rsvp_sent=false` mimo wysłki przez scheduled campaign zaczną pojawiać się jako `rsvp_sent=true`. Brak ryzyka false-positive (matcher dokładny: `^sched_campaign_[0-9]+_`).
- Konsystencja z 4 innymi funkcjami — drift eliminated, identical WHERE pattern across all 5 rsvp_sent matchers.
- Brak zmiany schema, brak migracji, brak nowych endpointów.

## Definition of Done ✅

- [ ] Linia 10791 zaktualizowana z OR clause (matching pattern z linii 10525/10702/10968/24007).
- [ ] `py -m py_compile backend/pg_storage.py` PASS.
- [ ] Contract Sync gate: PASS — 5 funkcji × identical WHERE pattern (`rsvp_%%` LIKE OR `^sched_campaign_[0-9]+_` regex).
- [ ] Security N/A (czysto wewnętrzna logika SQL, brak nowych user inputs).
- [ ] Migration N/A.
- [ ] QA deferred post-deploy (microscopowy fix, weryfikacja real-data po wdrożeniu).
- [ ] Review note w `simple-event-checkin/.agents/work_orders/review_notes/REVIEW-WO-MOB-007.md`.

## Test akceptacyjny 🧪

QA deferred post-deploy. Po deploy:
1. Otwórz event z uczestnikami, którzy otrzymali RSVP przez scheduled campaign (`sched_campaign_<id>_rsvp_initial`).
2. Wywołaj endpoint korzystający z `get_all_participants` (admin participants list).
3. Oczekiwany wynik: uczestnicy z scheduled RSVP mają `rsvp_sent=true` w response.
4. Porównaj liczbę uczestników z `rsvp_sent=true` z liczbą zwracaną przez funkcje 10525/10702/10968/24007 dla tego samego eventu — wartości muszą być identyczne (drift wyzerowany).

## Oczekiwany efekt wizualny 🖼️

- N/A — czysta warstwa danych. Efekt obserwowalny wyłącznie przez API response shape (`rsvp_sent: true` dla uczestników z scheduled campaign RSVP).

## Kontrakt API (jeśli zmiana full-stack) 🔗

- `GET /admin/api/...` (endpoint korzystający z `get_all_participants`) → response shape NIEZMIENIONY, ale wartość pola `rsvp_sent` może zmienić się z `false` na `true` dla podzbioru uczestników (tych z scheduled campaign RSVP).
- Brak zmiany typu, brak nowych pól — czysto naprawa drift wartości boolean.

## Sizing

🟢 **Mały** — 1 plik, ~3 LOC zmiana. Microscopowy fix konsystencji.

## Format zwrotki

- Diff linii 10791 (przed/po).
- Wynik `py -m py_compile backend/pg_storage.py` (PASS).
- Confirmation Contract Sync: grep/findstr 5 wystąpień `rsvp_%%` w `pg_storage.py` z identycznym OR pattern.
- Review note w `review_notes/REVIEW-WO-MOB-007.md`.

## Kontekst / źródło

- Pre-existing drift, flagged przez Contract Sync gate **WO-MOB-006** jako out-of-scope follow-up.
- 4 funkcje reference (już poprawne): `pg_storage.py:10525`, `10702`, `10968` (desktop), `24007` (mobile).
- Funkcja do naprawy: `get_all_participants` @ `pg_storage.py:10791`.
