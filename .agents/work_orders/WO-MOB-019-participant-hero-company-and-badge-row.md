# WO-MOB-019 — Karta uczestnika: HERO — firma z zamówienia + badge w jednej linii

**Scope:** mobile (simple-event-checkin)
**Status:** OPEN
**Utworzono:** 2026-05-28
**Sizing:** 🟢 mały (1 plik, 1 warstwa — czysty Compose UI)
**Typ:** UI tweak (frontend mobile)

---

## 1. Cel

Poprawić wygląd nagłówka (HERO) na ekranie szczegółów uczestnika (`ParticipantDetailsScreen`) zgodnie z feedbackiem użytkownika:

- **OG (inicjały avatara)** — OK, bez zmian.
- **Olga Gojska (imię i nazwisko)** — OK, bez zmian.
- **Firma** — obecnie pod nazwiskiem wyświetla się **osoba, która kupiła** (`buyerName`), a powinna się wyświetlać **firma z zamówienia** (`purchaserCompany`). Do poprawki.
- **Badge nazwy biletu + status/tag osoby** — obecnie są w dwóch osobnych liniach (jeden pod drugim). Mają być w **jednej linii** (mogą być ciutkę rozsunięte od siebie).

## 2. Zakres — konkretne pliki

- `simple-event-checkin/features/feature-participants/src/main/java/pl/medidesk/mobile/feature/participants/presentation/screen/ParticipantDetailsScreen.kt` — funkcja `HeroHeader` (~L219-324).

## 3. Czego NIE ruszać

- Avatar / inicjały (`initials`, gradient).
- `displayName` (imię i nazwisko).
- Pozostałe sekcje: `StatusIconsRow`, `CheckinBanner`, `ContactCard`, `OrderSection`.
- Model `Participant` — wszystkie potrzebne pola (`purchaserCompany`, `company`, `ticketName`, `tags`) już istnieją.
- Backend / API — bez zmian (pola już dostarczane).

## 4. Zmiany (DoD)

### 4.1 Linia firmy
- `identifier` w `HeroHeader`: zmiana precedencji z `company ?: buyerName` na **`purchaserCompany ?: company`**.
- Nigdy nie pokazuj `buyerName` (osoby kupującej) jako "firmy". Gdy brak `purchaserCompany` i `company` → linia ukryta (jak dotychczas).

### 4.2 Badge + tag w jednej linii
- Scalić blok badge'a `ticketName` (Surface/primaryContainer) oraz Row z `ParticipantTagChip`(ami) w **jeden wycentrowany `Row`**.
- `horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)`, `verticalAlignment = CenterVertically`.
- Kolejność: najpierw badge biletu, potem tag(i).

## 5. Test akceptacyjny

1. Otwórz kartę uczestnika, który ma firmę z zamówienia (`purchaserCompany`) → pod nazwiskiem widać **nazwę firmy z zamówienia**, a NIE imię/nazwisko kupującego.
2. Uczestnik z `ticketName` + tagiem → badge biletu i tag są **w jednej linii**, wycentrowane, lekko rozsunięte.
3. Uczestnik bez `purchaserCompany`, ale z `company` → widać `company`.
4. Uczestnik bez żadnej firmy → brak linii firmy (brak imienia kupującego).
5. `./gradlew :features:feature-participants:compileDebugKotlin` (lub assembleDebug) — PASS.

## 6. Oczekiwany efekt wizualny

HERO: avatar → imię i nazwisko → (📇 firma z zamówienia) → [badge biletu] [tag] w jednej linii.

## 7. Kontrakt API

Bez zmian. Pola `purchaserCompany`, `company`, `ticketName`, `tags` już są w `Participant` i dostarczane przez `/api/mobile/...`.
