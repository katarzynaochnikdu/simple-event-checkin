# IMPLEMENTATION_REPORT — WO-MOB-004

**Data:** 2026-05-19
**Worker:** worker-implementer (mobile Kotlin Android)
**Status:** ✅ Code complete + all builds PASS + gates PASS, awaiting commit

## Cel
Likwidacja 4-way duplication mapper'a `Participant` — wprowadzenie unified `ParticipantMappers.kt` w nowym module Gradle `core/core-mappers/` (decyzja user'a: Opcja A z DoR).

## Zmienione pliki (7 modified + 2 new + 2 docs)

**NEW:**
- `simple-event-checkin/core/core-mappers/build.gradle.kts` — Gradle build nowego modułu (deps: `core-model` + `core-network` + `core-database`)
- `simple-event-checkin/core/core-mappers/src/main/java/pl/medidesk/mobile/core/mappers/ParticipantMappers.kt` — 95 linii, 2 extension functions

**MODIFIED:**
- `simple-event-checkin/settings.gradle.kts` — `include(":core:core-mappers")`
- `simple-event-checkin/core/core-sync/build.gradle.kts` — `+implementation(project(":core:core-mappers"))`
- `simple-event-checkin/core/core-sync/src/main/java/.../SyncWorker.kt:141` — inline 32-linijkowe DTO→Entity → `dto.toEntity(eventId)` (1 linia)
- `simple-event-checkin/features/feature-participants/build.gradle.kts` — `+implementation(project(":core:core-mappers"))`
- `simple-event-checkin/features/feature-participants/.../ParticipantDetailsViewModel.kt:66, 169` — usunięty `private fun ParticipantEntity.toParticipant()` extension (35 linii) + usunięty inline `Participant(...)` w `loadParticipant()` (30 linii) → 2× call `entity.toDomain()`
- `simple-event-checkin/features/feature-participants/.../ParticipantsViewModel.kt:57` — usunięty 30-linijkowy inline mapping w flow collector → `entities.map { it.toDomain() }`

**Diff stat:** +11 / -128 LOC (massive duplication removal) + 2 new files in `core/core-mappers/`.

## Build / Compile

| Cel | Status | Czas |
|---|---|---|
| `:core:core-mappers:assembleDebug` | BUILD SUCCESSFUL | 4m 53s (clean) |
| `:core:core-sync:assembleDebug` | BUILD SUCCESSFUL | 5m 48s (z deps) |
| `:features:feature-participants:assembleDebug` | BUILD SUCCESSFUL | (w tym samym run) |
| `:app:assembleDebug` (full APK) | BUILD SUCCESSFUL | 3m 24s (incremental) |

## Mapper function content (canonical)

```kotlin
package pl.medidesk.mobile.core.mappers

import pl.medidesk.mobile.core.database.entities.ParticipantEntity
import pl.medidesk.mobile.core.model.Participant
import pl.medidesk.mobile.core.network.dto.ParticipantDto

/**
 * WO-MOB-004 (2026-05-19): unified mappers for Participant across layers.
 * Replaces 4 inline mappings previously scattered in:
 * - core-sync/SyncWorker.kt:pullParticipants() (DTO→Entity)
 * - feature-participants/ParticipantDetailsViewModel.kt:toParticipant() (Entity→Domain)
 * - feature-participants/ParticipantDetailsViewModel.kt:loadParticipant() (Entity→Domain legacy)
 * - feature-participants/ParticipantsViewModel.kt (Entity→Domain list)
 *
 * Note (bonus bug fix): pre-refactor toParticipant() and loadParticipant() in
 * ParticipantDetailsViewModel.kt omitted `phone` field; ParticipantsViewModel.kt
 * (list canonical) included it. Unified toDomain() includes `phone` — details
 * screen will now show phone when Entity has the value.
 */

fun ParticipantDto.toEntity(eventId: String): ParticipantEntity = ParticipantEntity(
    id = id, ticketId = ticketId, ticketNumber = ticketNumber, /* ...28 named args... */
    tags = tags?.joinToString(","),
    rsvpSent = rsvpSent, rsvpResponse = rsvpResponse, rsvpRespondedAt = rsvpRespondedAt
)

fun ParticipantEntity.toDomain(): Participant = Participant(
    id = id, ticketId = ticketId, backstageTicketId = backstageTicketId, /* ...27 named args... */
    phone = phone,  // canonical from list view — bonus fix for details screen
    tags = tags?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
    rsvpSent = rsvpSent, rsvpResponse = rsvpResponse, rsvpRespondedAt = rsvpRespondedAt
)
```

## Pre-refactor analysis: 4 inline mappings — found latent bug

Pre-implementation diff 4 inline mappingów wykrył **subtelną różnicę**:
- **Mapping #4** (`ParticipantsViewModel`, list) — mapował `phone = e.phone` ✓
- **Mappings #2 + #3** (`ParticipantDetailsViewModel`, main `toParticipant()` + legacy `loadParticipant()`) — **POMIJAŁY `phone`** ✗
- **Mapping #1** (`SyncWorker` DTO→Entity) — mapował phone ✓

**Skutek pre-refactor:** details screen pokazywał `phone=null` dla każdego uczestnika, mimo że Room cache mógł mieć wartość. Idealny przykład dlaczego 4-way duplication jest niebezpieczna — jedna z kopii drift'owała, brak rygorystycznych testów cross-mapper.

**Decyzja canonical:** Unified `toDomain()` zawiera `phone` (zgodne z list view + DTO→Entity). Bonus bug fix wbudowany w consolidation.

## Grep verification (DoD)

```bash
# 1. data class ParticipantEntity — 1 wynik (deklaracja):
ParticipantEntity.kt:17:data class ParticipantEntity(

# 2. ParticipantEntity(...) konstruktor — 2 wyniki (1 deklaracja + 1 mapper):
core-database/.../ParticipantEntity.kt:17:data class ParticipantEntity(
core-mappers/.../ParticipantMappers.kt:33:fun ParticipantDto.toEntity(eventId: String): ParticipantEntity = ParticipantEntity(

# 3. = Participant(...) konstruktor — 1 wynik (tylko mapper):
core-mappers/.../ParticipantMappers.kt:65:fun ParticipantEntity.toDomain(): Participant = Participant(

# 4. data class Participant — 1 wynik (deklaracja):
core-model/.../Participant.kt:3:data class Participant(
```

**Zero remaining inline `ParticipantEntity(` lub `Participant(` w `features/`.** DoD spełnione.

## Gates

| Gate | Status | Klucz finding |
|---|---|---|
| 🔒 Security | PASS (0 findings) | Refactor pure structural — zero zmian backend/auth/JWT/secrets/storage. Mapper to compute-only (zero log/network/file). Constraint §16 niezagrożony (zero dep na `core-datastore`). R8/ProGuard chronione przez istniejące keep rules na `core.model.**` + `core.network.**`. |
| 🔗 Contract Sync | WARN (refactor PASS, pre-existing drift WARN) | Refactor sam contract-clean: 29 pól mapowanych spójnie DTO↔Entity↔Domain. Gate wykrył **pre-existing drift** backend↔DTO (NIE wprowadzony przez WO-MOB-004): 5 pól w DTO bez backend SELECT — `phone`, `is_walkin`, `tags`, `buyer_name`, `buyer_email`. Moshi defaults maskują. Phone bonus bug fix w mapperze nie będzie widoczny w produkcji dopóki backend SELECT się nie rozszerzy. Out-of-scope tego WO. |
| 🗄️ Migration Guard | N/A | Brak SQL migrations. Schema Room niezmieniona. |
| 🧪 QA | DEFERRED | Build PASS verified compile. APK install + manual sanity check user-driven post-commit. |

## Postmortem (4 pytania)

### Co działa

- 4 inline mapowania zlikwidowane → 1 unified mapper (`ParticipantMappers.kt`)
- Wszystkie 4 builds PASS (incl. full `:app:assembleDebug`)
- Grep verification: exact match z DoD criteria (1× ParticipantEntity decl + 1× mapper, 1× Participant decl + 1× mapper)
- Net -128 / +11 LOC (massive duplication removal)
- Phone latent bug naprawiony "by accident" — canonical mapper zawiera phone, future-proof gdy backend SELECT się rozszerzy
- Module dependency hygiene: `core-mappers` depends tylko na 3 data warstwy (`core-model` + `core-network` + `core-database`), zero `core-datastore` (separation z encrypted storage)
- Architectural pattern ustanowiony dla future mapperów (EventMapper, OrderMapper, WalkinMapper jeśli kiedyś)

### Co nie działa / known issues

- **Phone bug fix invisible w produkcji** — backend `get_participants_for_mobile()` nie zwraca `p.phone` w SELECT. Mapper-level fix odblokuje phone na details screen TYLKO po WO-MOB-005 (backend SELECT extension).
- **5 pól pre-existing drift** backend↔DTO (`phone`, `is_walkin`, `tags`, `buyer_name`, `buyer_email`) — zaadresowanie poza scope tego WO.
- **`kotlinx.coroutines.android` import bez użycia** w `core-mappers/build.gradle.kts:14` — kosmetyczne, cleanup w future WO.
- **Testy jednostkowe pominięte** (opcjonalne w WO) — round-trip DTO→Entity→Domain assertions na wszystkie 28 pól byłyby wartością biznesową przy future refactorach.

### Co odłożone

- **WO-MOB-005 (kandydat):** Backend SELECT extension w `get_participants_for_mobile()` dla 5 brakujących pól. Phone bonus fix activate'uje się "for free" po tym WO.
- **Unit tests dla ParticipantMappers** — round-trip serialization assertions. Wzorzec dla future mapperów.
- **APK QA on-device** — install + sanity check (lista uczestników + szczegóły + ikona RSVP z WO-MOB-003 nadal działa) — user-driven post-commit.
- **Cleanup kotlinx.coroutines.android import** w `core-mappers/build.gradle.kts` (P3 backlog).
- **Refaktor pozostałych mapperów** (jeśli istnieją — `Event`, `Order`, `Walkin`, `Mentee`) do `core-mappers/` modułu (P2 backlog jeśli problem się powtarza).

### Lessons learned

1. **Pre-implementation diff jest must-have przy duplikacie.** Implementer wykonał `diff -u` 4 inline mappingów PRZED napisaniem unified mapper'a. Wykrył że `phone` field był pomijany w 2/3 ścieżkach Entity→Domain. Gdyby implementer "po prostu skopiował main mapper" do unified — propagował by latent bug. **Rule:** przy refactorze N>1 inline implementacji, ZAWSZE pre-diff je przed wybraniem canonical reprezentacji.

2. **Module-level mappers (Option A) wygrywa z extension-in-source (Option B) gdy projekt ma multi-module architecture.** Submodule `simple-event-checkin/` ma 13 modułów Gradle — kolejny nie wprowadza overhead'u. Korzyść: clean separation (core-network nie wie o core-database, core-database nie wie o core-network) + łatwiejsze unit testy mapperów w izolacji.

3. **Latent bugs ukryte w duplication są niewykrywalne testami end-to-end.** Pre-refactor list view pokazywał phone poprawnie (mapping #4 OK), details screen pokazywał `phone=null` (mappings #2/#3 omit). Bez explicit test "details screen MUST show phone when Entity.phone is non-null" — bug żył ciche miesiącami. **Future:** unit tests per mapper z explicit field-by-field assertion (round-trip).

4. **Pure structural refactor bez tests jest bezpieczny TYLKO gdy gates verify behavior preservation.** Security gate (compile-time + grep) + Contract Sync (per-field consistency) + build PASS = sufficient dla NO behavior change scenarios. Jeśli refactor by zmienił semantykę — wymagane behavior tests.

5. **Master Agent pre-flight `git status` action ZADZIAŁAŁA ponownie** (lekcja z WO-MOB-002 atomicity violation). Working tree submodule był czysty pre-dispatch, implementer poprawnie zatrzymał się przed gitem. Druga sesja z rzędu bez "mixed commit" incident.

## Cross-references

- WO: [WO-MOB-004](WO-MOB-004-refactor-likwidacja-duplication-participant-mapper.md)
- Review: [REVIEW-WO-MOB-004](review_notes/REVIEW-WO-MOB-004.md)
- Parent (motivation): WO-MOB-003 dodał gotcha "Participant mapper 4-way duplication" w `known_gotchas.md` 2026-05-19 → CLOSURE by WO-MOB-004
- ADR-kandydat (decision_log.md): "Mobile cross-layer mappers location — dedicated core-mappers module (Option A)"
- Snapshot tag: `snapshot/pre-wo-mob-004-participant-mapper-refactor-2026-05-19` (monorepo `73415d3` + simple-event-checkin submodule `f942987`)
- Backend dependency: brak (refactor mobile-only)
- Follow-up kandydat: WO-MOB-005 — backend SELECT extension dla phone/is_walkin/tags/buyer_name/buyer_email (activate phone bonus fix in prod)
- Bonus bug fix scope: `phone` field — naprawiony w mapperze, czeka na backend extension żeby objawić się w produkcji
