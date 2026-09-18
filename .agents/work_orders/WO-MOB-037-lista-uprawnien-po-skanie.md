# WO-MOB-037: Po skanie — jeden kod osoby + lista uprawnień (bilety)

**Data:** 2026-09-18
**Worker:** Implementer
**Stage:** AMOZ XI — bramka
**Priorytet:** Krytyczny (weekend przed 23 IX)
**Scope:** mobile (simple-event-checkin)
**Depends:** backend już wysyła `tickets[]` w `GET /api/mobile/events/{id}/participants` oraz w `POST /api/mobile/checkin` (AMOZ XI). WO-646 dołoży `tickets[]` do `/my-mentees`.

## Cel

Po skanie każdy na bramce widzi **jeden kod osoby** i **wszystkie bilety, do których ta osoba jest uprawniona**. Jeden QR na osobę zostaje. Nie przełączamy trybu skanowania na `entitlement` (zamek na zawsze po pierwszym skanie).

## Zakres

- `core/core-model` — `TicketEntitlement`, helper nazw, `ticketNumber` + `tickets` na `Participant` / `ParticipantSummary` / `CheckinResult`
- `core/core-network/dto/ResponseDtos.kt` — `tickets` na `ParticipantDto`, `CheckinResponse`, `MenteeDto`
- `core/core-database` — Room v10→v11, kolumna `tickets_json`, migracja
- `core/core-mappers/ParticipantMappers.kt`
- `core/core-sync` — lookup + check-in mapują listę biletów
- `feature-scanner` — dialog potwierdzenia + overlay po skanie
- `feature-participants` — karta osoby: kod + chipy biletów
- `feature-dashboard` — Moi podopieczni: lista biletów zamiast jednego `ticketName`
- `core/core-ui` — wspólne chipy nazw biletów
- `Szkolenia/PENDING.md` — zmiana widoczna dla obsługi

## Czego NIE ruszać

- `multi_ticket_scan_mode` — zostaje `participant` na AMOZ XI
- IDEA-MOB-002 (skan per bilet) — po wydarzeniu
- IDEA-018 pełny edytor wielu biletów w jednej zmianie — po wydarzeniu
- Backend `pg_storage.py` (osobny WO-646 tylko dla `/my-mentees`)
- Unieważnianie kodów (BUG-114)

## Schema dependency

**NONE po stronie backendu tego WO.** Room v11 jest lokalnym cache apki: nowa kolumna JSON z polami, które serwer już zwraca.

## DoD

1. Sync AMOZ XI zapisuje `tickets[]` do Room.
2. Dialog skanu i overlay sukcesu pokazują kod osoby + listę nazw biletów.
3. Karta osoby pokazuje kod + wszystkie bilety (fallback: jeden `ticketName` gdy lista pusta — eventy historyczne).
4. Moi podopieczni pokazują listę, gdy backend przysłał `tickets` (WO-646).
5. Brak zmiany trybu skanowania.
6. Min. 1 test: encode/decode `tickets_json` + fallback do `ticketName`.
7. `./gradlew :core:core-model:test :core:core-mappers:compileDebugKotlin :features:feature-scanner:compileDebugKotlin` — PASS.

## Test akceptacyjny (właścicielka)

1. Zeskanuj osobę z jednym biletem — widać kod i ten bilet (jak dziś, tylko jaśniej).
2. Zeskanuj osobę z dwoma różnymi biletami (gdy takie dane są) — widać **oba**.
3. Karta tej osoby i podopieczni pokazują to samo.
4. Historyczne wydarzenie bez `tickets[]` — nadal widać dotychczasową nazwę biletu.
