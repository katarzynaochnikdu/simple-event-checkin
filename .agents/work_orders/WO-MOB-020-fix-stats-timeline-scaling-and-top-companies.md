# WO-MOB-020: Statystyki wejścia — fix wykresu CZAS PRZYBYCIA (skalowanie/scroll/ilości) + naprawa TOP FIRMY / ORGANIZACJE

**Data:** 2026-05-28
**Scope:** mobile (`simple-event-checkin`, Android natywny Kotlin/Compose)
**Worker:** Implementer (worker-implementer)
**Stage:** Mobile — Dashboard / Statystyki wejścia
**Priorytet:** Normalny
**Status:** ✅ DONE 2026-05-28 (build PASS, niezacommitowane — czeka na user commit). Raport: [IMPLEMENTATION_REPORT_WO_MOB_020.md](IMPLEMENTATION_REPORT_WO_MOB_020.md)

## Cel
Naprawić dwie wady w ekranie **Statystyki wejścia** (`StatsScreen`) dla konkretnego wydarzenia:
1. **CZAS PRZYBYCIA** — wykres słupkowy źle się skaluje, nie pokazuje liczb (ilości check-inów), nie przewija się przy wielu przedziałach godzinowych (backend zwraca do ~48 godzinnych bucketów dla 48h okna).
2. **TOP FIRMY / ORGANIZACJE** — sekcja **całkowicie nie działa**, zawsze pokazuje "Brak danych o firmach".

## Root cause (potwierdzony research, 2026-05-28)

**TOP FIRMY — 3 nakładające się przyczyny:**
- **A3 (PRIMARY, frontend bug):** `DashboardViewModel.kt:118-123` — `getRecentCheckinsFlow` emituje `List<ParticipantEntity>`, ale combine rzutuje każdy element `it as? Participant` (model domenowy). `ParticipantEntity` NIE jest `Participant` → `mapNotNull` odrzuca KAŻDY wiersz → `recentCheckins` ZAWSZE pusty → "Brak danych o firmach". Brak wywołania `.toDomain()`.
- **A1 (limit):** DAO `getRecentCheckinsFlow` (`ParticipantDao.kt:51`) ma `ORDER BY checked_in_at DESC LIMIT 10` → nawet po naprawie cast'a TOP FIRMY widziałoby tylko 10 ostatnich check-inów, a nie pełny ranking firm dla eventu.
- **A4 (puste pole):** `participants.company` (per-uczestnik free-text) jest często NULL/puste w tym produkcie; realna nazwa organizacji siedzi w `purchaser_company` (domena: `Participant.purchaserCompany`, kolumna w `ParticipantEntity:41`). Trzeba fallback `company` → `purchaserCompany`.

**Timeline (CZAS PRZYBYCIA) — UI:** `StatsScreen.kt:113-133` używa `Row` z `Arrangement.SpaceBetween`, słupki fixed `.width(24.dp)`, brak `horizontalScroll`, brak etykiet liczbowych nad słupkami (tylko godzina pod spodem), wysokość skalowana do lokalnego `maxCount`. Wiele bucketów się ściska/wychodzi poza ekran.

## Zakres (pliki, które Worker MA PRAWO modyfikować)
- `core/core-database/src/main/java/pl/medidesk/mobile/core/database/dao/ParticipantDao.kt` — dodać nową query (pełna lista checked-in dla eventu, BEZ limitu).
- `core/core-model/src/main/java/pl/medidesk/mobile/core/model/DashboardData.kt` — dodać `data class CompanyStat(name, count)` + pole `companyStats` w `DashboardData`; usunąć martwe pole `recentCheckins` (po przepięciu).
- `features/feature-dashboard/.../presentation/viewmodel/DashboardViewModel.kt` — przepiąć combine na nową query, mapować `.toDomain()`, zagregować firmy (fallback `company` → `purchaserCompany`) → `companyStats`.
- `features/feature-dashboard/.../presentation/screen/StatsScreen.kt` — (a) timeline: `horizontalScroll` + etykiety liczbowe nad słupkami + lepsze skalowanie/min szerokość/odstępy; (b) TOP FIRMY: renderować `data.companyStats` zamiast agregacji w Compose.

## Czego NIE ruszać 🛑
- Backend (`backend/api/mobile.py`, `pg_storage.py`) — pole `company`/`purchaser_company` JUŻ jest w payloadzie mobile (`get_participants_for_mobile`), nie trzeba zmian backendu. **To WO jest frontend-only (Android).**
- `getRecentCheckinsFlow` semantyka "recent 10" — NIE zmieniaj limitu istniejącej metody; dodaj NOWĄ metodę dla pełnej listy (gdyby recent było użyte gdzie indziej — choć obecnie nie jest).
- Schema Room / migracje DB — kolumny `company` i `purchaser_company` już istnieją w `ParticipantEntity`. Brak nowej migracji.
- Mapper `ParticipantMappers.kt` — `company` i `purchaserCompany` już mapowane; tylko zweryfikuj, nie zmieniaj bez potrzeby.
- Sekcja "STRUKTURA BILETÓW" w `StatsScreen` — działa, nie ruszać.

## Pliki startowe
- `StatsScreen.kt:106-175` (timeline 106-136, TOP FIRMY 148-172).
- `DashboardViewModel.kt:88-153` (combine block, cast 118-123, budowa DashboardData 129-150).
- `ParticipantDao.kt:51` (`getRecentCheckinsFlow`).
- `ParticipantMappers.kt` (`ParticipantEntity.toDomain()` — potwierdzić `company` + `purchaserCompany`).
- `Participant.kt:11,26` (`company`, `purchaserCompany`).

## Proponowane podejście
1. **DAO:** `@Query("SELECT * FROM participants WHERE event_id = :eventId AND checked_in_at IS NOT NULL") fun getCheckedInParticipantsFlow(eventId: String): Flow<List<ParticipantEntity>>`.
2. **ViewModel:** w combine podmień `participantDao.getRecentCheckinsFlow(eventId)` → `getCheckedInParticipantsFlow(eventId)`; zmapuj `(it as ParticipantEntity).toDomain()`; zagreguj:
   ```
   companyStats = checkedIn
     .map { (it.company?.takeIf{c->c.isNotBlank()}) ?: it.purchaserCompany }
     .filterNot { it.isNullOrBlank() }
     .groupingBy { it!!.trim() }.eachCount()
     .toList().sortedByDescending { it.second }.take(8)
     .map { CompanyStat(it.first, it.second) }
   ```
   (logika ciężka w ViewModel, nie w Compose).
3. **Model:** `CompanyStat(val name: String, val count: Int)` + `val companyStats: List<CompanyStat> = emptyList()`; usuń `recentCheckins` z `DashboardData` (martwe po przepięciu).
4. **StatsScreen TOP FIRMY:** `if (data.companyStats.isEmpty()) "Brak danych o firmach" else data.companyStats.forEach { ... it.name / it.count }`.
5. **StatsScreen timeline:** owinąć `Row` w `Modifier.horizontalScroll(rememberScrollState())`; usunąć `SpaceBetween` na rzecz `Arrangement.spacedBy(12.dp)`; nad słupkiem `Text(entry.count.toString(), fontSize = 9-10.sp)`; słupek min. szerokość (~28dp) i wysokość skalowana do `maxCount` (z guardem dzielenia przez 0); zachować etykietę godziny pod spodem.

## Ryzyko
- Cast `as ParticipantEntity` — jeśli flow kiedyś zwróci inny typ, rzuci wyjątek. Mitygacja: query zwraca `List<ParticipantEntity>` jednoznacznie.
- `horizontalScroll` wewnątrz `LazyColumn` item — bezpieczne (oś pozioma vs pionowa scroll konfliktu nie ma).
- Usunięcie `recentCheckins` — grep potwierdził brak innych konsumentów (tylko ViewModel set + StatsForm read). Build wychwyci ewentualne referencje.
- `purchaserCompany` z cache: kolumna `purchaser_company` istnieje w entity od schema v9/v10 — dla starszych cache może być NULL do najbliższego sync. Akceptowalne (degraduje do `company`).

## Definition of Done ✅
- [ ] `getCheckedInParticipantsFlow` dodane do DAO, zwraca pełną listę checked-in (bez LIMIT).
- [ ] ViewModel mapuje encje przez `.toDomain()` (cast naprawiony) i buduje `companyStats` z fallbackiem `company`→`purchaserCompany`.
- [ ] `DashboardData.companyStats` zasilane; martwe `recentCheckins` usunięte.
- [ ] TOP FIRMY pokazuje realny ranking firm (po check-inie) zamiast zawsze "Brak danych".
- [ ] CZAS PRZYBYCIA: przewijalny poziomo, etykiety liczbowe nad słupkami, sensowne skalowanie przy wielu bucketach.
- [ ] Build mobilny przechodzi: `./gradlew :app:assembleDebug` (w `simple-event-checkin/`).
- [ ] Brak zmian backendu / schema / migracji.
- [ ] Review note w `review_notes/`.

## Test akceptacyjny 🧪
Build + (jeśli możliwe) instalacja APK i smoke na wydarzeniu z check-inami:
1. `cd simple-event-checkin && ./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
2. Otwórz wydarzenie z ≥kilkoma check-inami z różnych firm → zakładka **Statystyki** (Statystyki wejścia).
3. **TOP FIRMY:** lista firm z liczbą osób (sortowana malejąco, max 8), NIE "Brak danych o firmach" (o ile uczestnicy mają company/purchaserCompany).
4. **CZAS PRZYBYCIA:** nad każdym słupkiem widoczna liczba; przy wielu godzinach wykres da się przewinąć w poziomie; najwyższy słupek = najwięcej check-inów.
5. Sekcja STRUKTURA BILETÓW bez zmian (regresja).

> Uwaga QA: emulator/urządzenie + konto admin z dostępem do eventu. Jeśli build-only (brak urządzenia) — QA = `./gradlew :app:assembleDebug` PASS + code-review scenariuszy + opcjonalnie `:features:feature-dashboard:testDebugUnitTest`.

## Oczekiwany efekt wizualny 🖼️
- **TOP FIRMY:** wiersze `🏢 <Nazwa firmy> ........ <N> osób` (ranking malejąco), zamiast szarego "Brak danych o firmach".
- **CZAS PRZYBYCIA:** słupki z liczbą check-inów nad nimi, godzina pod spodem, poziomy scroll przy >~6-8 bucketach, słupki nie ściśnięte do zera.

## Kontrakt API 🔗
Brak zmian — frontend-only. Payload mobile `GET /api/mobile/events/<id>/participants` już zawiera `company` i `purchaser_company` (per research). `GET /api/mobile/events/<id>/dashboard` `timeline[]` = `{hour, count}` (bez zmian).

## Test (TESTING_STRATEGY)
WO modyfikuje logikę agregacji w ViewModel → rekomendowany 1 test JVM (JUnit + MockK/Turbine) weryfikujący że `companyStats` agreguje z fallbackiem `company`→`purchaserCompany` i sortuje malejąco. UI Compose chart = smoke wizualny (poza zakresem unit). Jeśli infra testowa feature-dashboard jeszcze nieobecna — odnotuj w review jako follow-up (nie blokuj).

## Format zwrotki
- Lista zmienionych plików + 1-linijkowy opis per plik.
- Git diff summary.
- Wynik `./gradlew :app:assembleDebug`.
- (jeśli urządzenie) screenshot Statystyki wejścia przed/po; inaczej code-review potwierdzenie scenariuszy.
- Ewentualna propozycja gotcha (cast Entity→Domain w combine, company vs purchaserCompany).
