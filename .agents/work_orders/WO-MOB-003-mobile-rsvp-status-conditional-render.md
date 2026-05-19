# WO-MOB-003: Mobile — Room migration v8→v9 + DTO/Domain/Entity dla pól RSVP + Composable warunkowy render ikony RSVP

**Data:** 2026-05-19
**Worker:** worker-implementer (mobile Kotlin) — dispatch przez `/master`
**Stage:** Mobile / Participant detail screen
**Priorytet:** Wysoki (bug widoczny w UI mobile produkcyjnym)
**Scope:** **mobile** (`simple-event-checkin/`, natywny Android Kotlin)
**Depends on:** [WO-MOB-002](WO-MOB-002-fix-rsvp-status-participant-details.md) — wymaga merge'u backend dodającego 3 pola RSVP w mobile API response

## Cel
Aplikacja mobile (`simple-event-checkin/`) ma:
1. Pobierać 3 nowe pola RSVP z backend response (po WO-MOB-002) — `rsvp_sent`, `rsvp_response`, `rsvp_responded_at`.
2. Przechowywać je lokalnie w Room DB (migracja schemy 8→9).
3. **Warunkowo renderować** ikonę RSVP w rzędzie 3 statusów (`StatusIconsRow`) na ekranie szczegółów uczestnika:
   - `rsvp_sent == false` → **ikona ukryta** (slot wycięty z rzędu, zostaje Płatność + Check-In) — decyzja UX z `/master ustal odpowiedzi` 2026-05-19
   - `rsvp_sent == true && rsvp_response == 'confirmed'` → zielona ikona "potwierdzam"
   - `rsvp_sent == true && rsvp_response == 'declined'` → czerwona ikona "odmawiam"
   - `rsvp_sent == true && rsvp_response == null` → ikona "czeka na odpowiedź" (kolor neutralny, np. szary)

## Kontekst i diagnoza (z research 2026-05-19)
- Composable `ParticipantDetailsScreen.kt:325-371` (`StatusIconsRow`) używa `participant.attendanceStatus` jako proxy dla RSVP (linie 339-349):
  ```kotlin
  attendanceRaw in listOf("attending", "confirmed", "rsvp_confirmed") -> green tick
  ```
- `attendance_status` jest backendowo **derived** z `order.status` — każdy `paid` order auto-mapuje na `confirmed`, niezależnie od tego czy mail RSVP poszedł.
- Domain model `Participant` (`core-model/.../Participant.kt:3-32`) **NIE ma** pól `rsvpSent`/`rsvpResponse`/`rsvpRespondedAt`.
- API DTO `ParticipantDto` (`core-network/.../ResponseDtos.kt:67-93`) **NIE ma** pól RSVP.
- Room Entity `ParticipantEntity` (`core-database/.../ParticipantEntity.kt:17-44`) **NIE ma** kolumn RSVP. Aktualna wersja schemy = **8** (`core-database/schemas/.../MdDatabase/8.json`).
- Mapper Entity → Domain w `ParticipantDetailsViewModel.kt:158-183` (i legacy 196-221) — wymaga rozszerzenia.

## Oczekiwane zachowanie ✅
Karta szczegółów uczestnika pokazuje:

| Stan uczestnika | Co widać w rzędzie 3 ikon |
|---|---|
| Nie dostał maila RSVP (`rsvp_sent=false`) | 2 ikony: 💰 Płatność + 🚪 Check-In. RSVP **ukryty całkowicie** — żadnego slotu. |
| Dostał RSVP, kliknął "potwierdzam" | 3 ikony: ✅ RSVP zielony + 💰 Płatność + 🚪 Check-In |
| Dostał RSVP, kliknął "odmawiam" | 3 ikony: ❌ RSVP czerwony + 💰 Płatność + 🚪 Check-In |
| Dostał RSVP, brak kliku | 3 ikony: ⏳ RSVP szary "czeka" + 💰 Płatność + 🚪 Check-In |

## Zakres

### Warstwa 1: API DTO
- `core-network/src/main/java/pl/medidesk/mobile/core/network/dto/ResponseDtos.kt:67-93` — `ParticipantDto`:
  ```kotlin
  @Json(name = "rsvp_sent") val rsvpSent: Boolean = false,
  @Json(name = "rsvp_response") val rsvpResponse: String? = null,
  @Json(name = "rsvp_responded_at") val rsvpRespondedAt: String? = null,
  ```
  Default `false` / `null` zapewnia tolerancyjne parsowanie gdy backend (pre WO-MOB-002) nie zwraca pól.

### Warstwa 2: Domain model
- `core-model/src/main/java/pl/medidesk/mobile/core/model/Participant.kt:3-32` — dodać 3 pola odpowiadające DTO.

### Warstwa 3: Room Entity + migration
- `core-database/src/main/java/pl/medidesk/mobile/core/database/entities/ParticipantEntity.kt:17-44`:
  ```kotlin
  @ColumnInfo(name = "rsvp_sent", defaultValue = "0") val rsvpSent: Boolean = false,
  @ColumnInfo(name = "rsvp_response") val rsvpResponse: String? = null,
  @ColumnInfo(name = "rsvp_responded_at") val rsvpRespondedAt: String? = null,
  ```
- `core-database/src/main/java/pl/medidesk/mobile/core/database/MdDatabase.kt` — bump `version = 9`, register `MIGRATION_8_9`
- `core-database/src/main/java/pl/medidesk/mobile/core/database/Migrations.kt` — dodać:
  ```kotlin
  val MIGRATION_8_9 = object : Migration(8, 9) {
      override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE participants ADD COLUMN rsvp_sent INTEGER NOT NULL DEFAULT 0")
          db.execSQL("ALTER TABLE participants ADD COLUMN rsvp_response TEXT")
          db.execSQL("ALTER TABLE participants ADD COLUMN rsvp_responded_at TEXT")
      }
  }
  ```
- `core-database/schemas/pl.medidesk.mobile.core.database.MdDatabase/9.json` — wygenerowane przez `assembleDebug` (Room schema export) — NIE pisz ręcznie

### Warstwa 4: Mappery
- Mapper `ParticipantDto → ParticipantEntity` (zlokalizuj w `core-sync/SyncEngine` lub w repo participants) — dodać 3 pola
- Mapper `ParticipantEntity → Participant` (domain) w `ParticipantDetailsViewModel.kt:158-183` + legacy 196-221 — dodać 3 pola

### Warstwa 5: UI Composable
- `feature-participants/src/main/java/pl/medidesk/mobile/feature/participants/presentation/screen/ParticipantDetailsScreen.kt:325-371` (`StatusIconsRow`) — przepisać logikę:
  ```kotlin
  Row(...) {
      if (participant.rsvpSent) {
          val (icon, color) = when (participant.rsvpResponse?.lowercase()) {
              "confirmed" -> Icons.Default.EventAvailable to StatusGreen
              "declined" -> Icons.Default.EventBusy to StatusRed
              else -> Icons.Default.HourglassEmpty to StatusGray  // czeka na odpowiedź
          }
          StatusIcon(icon, color, "RSVP")
      }
      // Płatność i Check-In renderowane bez zmian
      StatusIcon(paymentIcon, paymentColor, "Płatność")
      StatusIcon(checkinIcon, checkinColor, "Check-In")
  }
  ```
- Usuń dotychczasowe mapowanie `attendance_status → RSVP icon` (linie 339-349)
- Sprawdź czy `translateRsvp()` (linie 736-743) jest martwy — usuń jeśli nieużywany (constraints monorepo: dead code clean)

## Czego NIE ruszać 🛑
- 🛑 Logiki Check-In (queue / sync / scanner) — to osobny obszar (constraints WO-MOB-001 area)
- 🛑 Logiki Płatności (`payment` status mapping)
- 🛑 Mapowania `attendance_status` — pole zostaje w DTO/Entity/Domain do innych celów (np. statystyki, listy filtrowane), tylko **nie używamy** go do ikony RSVP
- 🛑 EncryptedSharedPreferences / AuthDataStore (constraints §16) — nie ma związku
- 🛑 Innych ekranów: lista uczestników (jeśli też pokazuje ikonę RSVP — to do osobnego WO-MOB-004 follow-up, poza scope)

## Pliki startowe 📂

**API + Domain + Entity:**
- `simple-event-checkin/core/core-network/src/main/java/pl/medidesk/mobile/core/network/dto/ResponseDtos.kt:67-93`
- `simple-event-checkin/core/core-model/src/main/java/pl/medidesk/mobile/core/model/Participant.kt:3-32`
- `simple-event-checkin/core/core-database/src/main/java/pl/medidesk/mobile/core/database/entities/ParticipantEntity.kt:17-44`

**Migration:**
- `simple-event-checkin/core/core-database/src/main/java/pl/medidesk/mobile/core/database/MdDatabase.kt` (version bump + register migration)
- `simple-event-checkin/core/core-database/src/main/java/pl/medidesk/mobile/core/database/Migrations.kt`
- `simple-event-checkin/core/core-database/schemas/pl.medidesk.mobile.core.database.MdDatabase/8.json` (referencja)

**Mappery:**
- `simple-event-checkin/core/core-sync/` (zlokalizować mapper DTO→Entity — `SyncEngine.kt` lub `ParticipantSyncMapper.kt`)
- `simple-event-checkin/features/feature-participants/src/main/java/pl/medidesk/mobile/feature/participants/presentation/viewmodel/ParticipantDetailsViewModel.kt:158-183` (mapper Entity→Domain) + linie 196-221 (legacy)

**UI:**
- `simple-event-checkin/features/feature-participants/src/main/java/pl/medidesk/mobile/feature/participants/presentation/screen/ParticipantDetailsScreen.kt:325-371` (`StatusIconsRow`)
- ten sam plik linie 736-744 (`translateRsvp` — sprawdzić czy martwy)

## Ryzyko
- **Room migration safety** — `ALTER TABLE ADD COLUMN ... NOT NULL DEFAULT 0` jest idempotentny w SQLite; ryzyko niskie. Default `0` (`false`) dla `rsvp_sent` → istniejący cache pokaże "ikona ukryta" do pierwszego sync — **acceptable** (bezpieczna degradacja).
- **Drift backend ↔ mobile** — jeśli WO-MOB-003 wyjdzie na prod **przed** WO-MOB-002, mobile dostanie response bez nowych pól → Moshi default `false`/`null` → ikona RSVP zniknie u wszystkich. **Mitigation:** WYMUŚ kolejność deploy (najpierw backend = WO-MOB-002 merged + deployed, potem mobile = WO-MOB-003 build).
- **Sync mapper drift** — jeśli pominiemy mapper w `core-sync`, dane z API nie przepiszą się do Room — UI dostanie stale `false`. **Mitigation:** ręczna weryfikacja mapowania w QA + integration test.
- **Duplikat folderu** `feature-participants/.../presentationviewmodel/` (bez kropki) — ostrzeżenie z research. Sprawdzić czy główny pakiet to `presentation.viewmodel` (z kropką) i czy duplikat to dead code do sprzątnięcia (poza scope, ale udokumentować w postmortem).
- **`translateRsvp()` martwy kod** — jeśli rzeczywiście nieużywany, usuń przy okazji (low risk).
- **`attendance_status` używany w innych miejscach** — przed usunięciem mapowania z linii 339-349, `grep`-uj `attendanceStatus` w całym `simple-event-checkin/` żeby nie zepsuć innego widoku.

## Definition of Done ✅
- [ ] `ParticipantDto` rozszerzony o 3 pola RSVP z `@Json(name=...)` + default values
- [ ] `Participant` (domain) rozszerzony o 3 pola RSVP
- [ ] `ParticipantEntity` rozszerzony o 3 kolumny Room z defaultValue
- [ ] `MdDatabase.kt` version=9, registered `MIGRATION_8_9`
- [ ] `Migrations.kt` definiuje `MIGRATION_8_9` z 3 ALTER TABLE
- [ ] Mapper `Dto→Entity` przenosi 3 pola
- [ ] Mapper `Entity→Domain` w ViewModel przenosi 3 pola (oba mappery — main + legacy)
- [ ] `StatusIconsRow` Composable: `if (participant.rsvpSent)` warunkowy render — bez slotu gdy `false`
- [ ] `when (rsvpResponse)`: confirmed → green, declined → red, null → gray "czeka"
- [ ] Usunięty dead code `translateRsvp()` jeśli rzeczywiście nieużywany (opcjonalnie)
- [ ] `./gradlew :feature-participants:assembleDebug :core-database:assembleDebug` — PASS
- [ ] `./gradlew :core-database:test` — Room migration test PASS (jeśli istnieje test infra)
- [ ] Schema `9.json` wygenerowany (sprawdzenie git diff w `core-database/schemas/`)
- [ ] Test akceptacyjny przechodzi (4 stany w event 24311000000909074)
- [ ] 4 screenshoty (confirmed / declined / czeka / brak ikony RSVP)
- [ ] Decyzja UX dopisana do `decision_log.md` (ikona ukryta vs slot szary — wybrana: ukryta)

## Test akceptacyjny 🧪

**Pre-condition:** WO-MOB-002 zmergowane + deploy backend na środowisko, którego mobile używa (prod = `md-order-portal-backend.onrender.com`).

**Event:** `24311000000909074` ([panel.medidesk.edu.pl](https://panel.medidesk.edu.pl/admin/events/24311000000909074/))

1. Build mobile debug: `cd simple-event-checkin && ./gradlew assembleDebug`
2. Zainstaluj APK na fizycznym urządzeniu Android lub emulatorze
3. Zaloguj się do mobile app na konto admina z dostępem do eventu 24311000000909074
4. Otwórz listę uczestników eventu, poczekaj na sync z backendu
5. **Test A** — znajdź uczestnika który dostał RSVP i kliknął "potwierdzam":
   - Otwórz szczegóły → **oczekiwane:** rząd 3 ikon, RSVP zielony "✅"
   - Screenshot
6. **Test B** — uczestnik który dostał RSVP i kliknął "odmawiam":
   - **Oczekiwane:** RSVP czerwony "❌"
   - Screenshot
7. **Test C** — uczestnik który dostał RSVP ale nie kliknął:
   - **Oczekiwane:** RSVP szary "⏳ czeka"
   - Screenshot
8. **Test D** — uczestnik bez wysłanego RSVP (Sylwia Baran lub analogiczna):
   - **Oczekiwane:** rząd ma TYLKO 2 ikony — Płatność + Check-In. **Brak slotu RSVP** w ogóle.
   - Screenshot
9. **Migration test:** zainstaluj poprzednią wersję APK (v8 schema), wykonaj sync, **zaktualizuj** do nowej wersji (v9). Sprawdź że:
   - Aplikacja startuje bez crashu (migration PASS)
   - Lista uczestników ładuje się (default `rsvp_sent=0` na istniejących wpisach Room)
   - Po pull-to-refresh sync z backendem przepisuje nowe pola → ikony pojawiają się prawidłowo

## Oczekiwany efekt wizualny 🖼️
Karta uczestnika Sylwia Baran (i analogicznych) — rząd `StatusIconsRow` ma **tylko 2 ikony**: 💰 Płatność + 🚪 Check-In. Slot RSVP **nieobecny** (rząd jest węższy lub Płatność+Check-In wycentrowane — zależy od layoutu Composable).

Karty uczestników po wysłanym RSVP — 3 ikony jak dotychczas, z **prawdziwym** statusem RSVP.

## Format zwrotki
- Lista zmienionych plików z jednolinijkowym opisem (split: core-network, core-model, core-database, core-sync, feature-participants)
- Git diff summary
- Wynik buildu: `./gradlew :feature-participants:assembleDebug` — log końcowy
- Schema diff: `core-database/schemas/pl.medidesk.mobile.core.database.MdDatabase/8.json` vs `9.json`
- 4 screenshoty (A/B/C/D)
- Wpis do `decision_log.md` ADR-2026-05-19: "Mobile RSVP icon: hidden slot when rsvp_sent=false (vs gray 'nie dotyczy')"
- Postmortem 4 pytania (`IMPLEMENTATION_REPORT_WO_MOB_003.md`)

## Załączniki
- Research raport źródłowy (transcript sesji 2026-05-19 `/master ustal odpowiedzi`)
- WO-MOB-002 (parent — backend dependency)
