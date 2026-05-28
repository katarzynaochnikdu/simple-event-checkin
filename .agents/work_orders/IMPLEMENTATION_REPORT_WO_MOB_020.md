# IMPLEMENTATION REPORT — WO-MOB-020

**Data:** 2026-05-28
**WO:** [WO-MOB-020](WO-MOB-020-fix-stats-timeline-scaling-and-top-companies.md)
**Worker:** worker-implementer
**Scope:** mobile (`simple-event-checkin`), frontend-only (Android Kotlin/Compose)

## Co zrobione
Naprawa dwóch wad ekranu **Statystyki wejścia** (StatsScreen):
1. **TOP FIRMY / ORGANIZACJE** — działa: realny ranking firm wśród zameldowanych uczestników (top 8). Naprawiony PRIMARY bug (cast `Entity as? Domain` → zawsze pusta lista), zniesiony LIMIT 10 (nowa pełna query), dodany fallback `company` → `purchaserCompany`.
2. **CZAS PRZYBYCIA** — przewijalny poziomo (`horizontalScroll`), liczby check-inów nad słupkami, lepsze skalowanie (min szerokość 28dp, `spacedBy(12.dp)`, guard `maxCount.coerceAtLeast(1)`).

## Zmienione pliki (5)
| Plik | Zmiana |
|---|---|
| `core/core-database/.../dao/ParticipantDao.kt` | Nowa `getCheckedInParticipantsFlow(eventId)` (pełna lista checked-in, bez LIMIT). `getRecentCheckinsFlow` nietknięte. |
| `core/core-model/.../model/DashboardData.kt` | `data class CompanyStat(name,count)` + pole `companyStats`; usunięte martwe `recentCheckins`. |
| `features/feature-dashboard/build.gradle.kts` | `implementation(project(":core:core-mappers"))` — wymagane by `toDomain()` się rozwiązało (convention plugin nie dodaje core-mappers). |
| `features/feature-dashboard/.../viewmodel/DashboardViewModel.kt` | Combine → `getCheckedInParticipantsFlow`; map `.filterIsInstance<ParticipantEntity>().map{it.toDomain()}` (fix cast); budowa `companyStats` (fallback+trim+group+sort desc+take 8). |
| `features/feature-dashboard/.../screen/StatsScreen.kt` | Timeline: horizontalScroll + count label + skalowanie; TOP FIRMY: render `data.companyStats`. |

Diff: 5 plików, +56 / -22.

> ⚠️ `feature-participants/.../ParticipantDetailsScreen.kt` było DIRTY przed WO (pre-existing working-tree change, purchaserCompany hero fallback) — NIE część WO-MOB-020, pozostawione nietknięte.

## Gates
- **QA:** PASS (build-only). `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL (394 tasks, APK 58MB). Native Android → brak ścieżki browser QA; code-review scenariuszy OK. Live smoke = post-install na urządzeniu (opcjonalnie).
- **Security:** N/A — frontend-only, zero endpointów/auth/PII/sekretów/upload. Payload backendu już zawierał `company`/`purchaser_company`.
- **Contract Sync:** N/A — zero zmian API/DTO shape.
- **Migration:** N/A — zero zmian schema (kolumny `company`/`purchaser_company` już istnieją w `ParticipantEntity`).

## DoD ✅
- [x] `getCheckedInParticipantsFlow` (bez LIMIT).
- [x] ViewModel `.toDomain()` + `companyStats` z fallbackiem.
- [x] `companyStats` zasilane, `recentCheckins` usunięte.
- [x] TOP FIRMY pokazuje ranking zamiast "Brak danych".
- [x] Timeline przewijalny + liczby + skalowanie.
- [x] `./gradlew :app:assembleDebug` PASS.
- [x] Zero zmian backend/schema/migracji.
- [x] Review note.

## Postmortem
1. **Nieoczywiste:** TAK — (a) feature modules wymagają jawnego `core-mappers` dep dla `.toDomain()`/`.toEntity()` (convention plugin go nie wstrzykuje); (b) Room DAO flow emituje `Entity`, nigdy modelu domenowego — `it as? Domain` cicho zwraca pustą listę bez błędu kompilacji. Oba → gotcha.
2. **Security:** NIE — czysto prezentacyjna zmiana Android.
3. **Test debt:** brak JVM unit testu agregacji `companyStats` — `feature-dashboard` nie ma jeszcze `src/test/` ani test deps. Follow-up: wyciągnąć agregację do czystej funkcji + test (fallback/sort/take 8) po bootstrapie infra testowej feature-modules.
4. **Build flakiness (env, nie kod):** intermittent file-lock / daemon stop przy nakładających się background buildach na Windows — rozwiązane `./gradlew --stop` + single `--no-daemon` run.
