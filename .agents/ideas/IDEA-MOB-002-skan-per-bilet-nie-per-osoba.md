# IDEA-MOB-002: Skan na bramce per bilet, nie per osoba

**Zgłoszone:** 2026-09-18 — wymaganie właścicielki przy IDEA-018
**Scope:** mobile (`simple-event-checkin`)
**Status:** 🟡 Inbox — **warunek** kodu wejścia per bilet; bez tego nie wolno
przełączać wydarzenia na tryb `entitlement`
**Para desktop:** [IDEA-018](../../../.agents/ideas/IDEA-018-jedna-zmiana-jeden-bilet-dwie-osoby-dwie-doplaty.md)

## Po co

Właścicielka: zamówienie na 4 osoby, w jednej zmianie różne bilety różnym
osobom, a czwarta dostaje **dwa różne**. Kod do wejścia ma być **biletu**,
nie osoby. Na bramce stoi ta apka.

Dziś apka wpuszcza **osobę**. Przy dwóch biletach (dzień + bankiet) jeden
skan zalicza całość. Obsługa nie widzi, który bilet zeskanowała.

## Co już jest po stronie serwera (apka tego nie używa)

- `services/participant_entitlement_checkin.py` — tryby `participant` /
  `entitlement`. AMOZ XI jest na `participant`.
- Każde uprawnienie ma własny `qr_token`.
- `POST /api/mobile/checkin` przyjmuje `ticket_number | ticket_id |
  backstage_ticket_id` — to identyfikatory **osoby**. Kody
  `entitlement_inactive` istnieją, bo serwer dla AMOZ XI wchodzi w moduł
  biletów wielokrotnych, ale nadal po kodzie osoby.
- Tryb wydarzenia **zatrzaskuje się przy pierwszym skanie**. Przestawienie
  AMOZ XI na `entitlement` przed zmianą apki = zepsuta bramka bez drogi
  powrotu.

## Weekend 18–21 IX (WO-MOB-037) — lista po skanie, nadal jeden kod osoby

Serwer **już** wysyła `tickets[]` w sync i check-inie. WO-MOB-037 każe apce
**pokazać** ten kod + listę nazw. To **nie** jest skan per bilet i **nie**
wolno przełączać AMOZ XI na `entitlement`.

## Czego apka nadal nie ma (skan per bilet — po wydarzeniu)

Zero obsługi `qr_token` uprawnienia jako osobnego skanu. Lookup i check-in
idą po kodzie **osoby**.

| Miejsce | Dziś (po WO-MOB-037) |
|---|---|
| `ParticipantEntity` | kod osoby + `tickets_json` (nazwy) |
| `ParticipantDao` | lookup po kodzie osoby |
| `ScannerScreen` / karta / podopieczni | jeden kod + lista nazw biletów |
| Tryb skanowania | nadal `participant` |

## Przypadki, które apka musi umieć (te same co IDEA-018)

1. Osoba z jednym biletem — jak dziś, nic się nie psuje.
2. Dwie osoby, ten sam dodatkowy bilet X — skan X wpuszcza **tę** osobę na
   X, nie siostrę / brata z tym samym nazwiskiem.
3. Osoba z biletem Y — skan X jej nie wpuszcza.
4. Osoba z X **i** Y — skan X nie zalicza Y; drugi skan Y jest osobnym
   wejściem; ekran pokazuje **który** bilet wszedł.
5. Cofnij wejście — cofa ten bilet, nie wszystkie bilety osoby.
6. Offline / kolejka sync — ten sam rozdział (bilet, nie osoba).
7. Zamówienie anulowane / osoba anulowana — odmowa z jasnym powodem
   (to styka się z BUG-114; AMOZ XI już odmawia po statusie po stronie
   serwera, apka ma pokazać ten powód, nie „nie znaleziono”).
8. Podmiana osoby — stary kod nie wpuszcza, nowy wpuszcza; apka po syncu
   nie trzyma starego kodu jako żywego.

## Kierunek (nie implementacja)

1. Serwer: sync i check-in przyjmują `qr_token` uprawnienia **obok**
   dotychczasowego kodu osoby (stara apka nadal działa w trybie
   `participant`).
2. Apka: karta osoby = lista biletów; skaner szuka po `qr_token` albo po
   kodzie osoby, zależnie od trybu wydarzenia (tryb przychodzi z syncu,
   nie zgadujemy).
3. Wydarzenie przestawia się na `entitlement` **dopiero** gdy apka na
   bramce to umie. Nie odwrotnie.

## Czego nie ruszać przed AMOZ XI (23 IX)

Nie przełączać trybu skanowania na produkcji. Nie wydawać apki, która
wymaga trybu `entitlement`. To robota po wydarzeniu, razem z listą
przypisań w panelu albo tuż przed jej włączeniem.
