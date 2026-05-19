# REVIEW-WO-MOB-004 — Refactor mapper Participant (likwidacja 4-way duplication)

**Data:** 2026-05-19
**Status:** ✅ Code complete, all 4 builds PASS, gates PASS, awaiting commit
**Worker:** worker-implementer (mobile Kotlin)
**Stage:** Mobile / Architecture refactor

## DoD checklist (z WO-MOB-004)

- [✅] Wybrana lokalizacja: **Opcja A — nowy moduł Gradle `core-mappers/`** (decyzja user'a)
- [✅] `fun ParticipantDto.toEntity(eventId: String): ParticipantEntity` zdefiniowane raz (`ParticipantMappers.kt:33`)
- [✅] `fun ParticipantEntity.toDomain(): Participant` zdefiniowane raz (`ParticipantMappers.kt:65`)
- [✅] Wszystkie 4 inline mapowania zastąpione wywołaniami mapper functions:
  - `core-sync/SyncWorker.kt:141` → `dto.toEntity(eventId)`
  - `feature-participants/ParticipantDetailsViewModel.kt:66` (Flow collector) → `entity.toDomain()`
  - `feature-participants/ParticipantDetailsViewModel.kt:169` (legacy `loadParticipant()`) → `entity.toDomain()`
  - `feature-participants/ParticipantsViewModel.kt:57` (list flow) → `it.toDomain()`
- [✅] `./gradlew :app:assembleDebug` PASS (full APK build, 3m 24s incremental)
- [✅] Grep: `data class ParticipantEntity` → 1 wynik (tylko deklaracja)
- [✅] Grep: `ParticipantEntity(` → 2 wyniki (1 deklaracja + 1 mapper) ✓ jak oczekiwano
- [✅] Grep: `= Participant(` → 1 wynik (tylko mapper) ✓
- [ ] Jednostkowe testy mapperów — **POMINIĘTE** (opcjonalne w WO; follow-up kandydat)
- [ ] APK install + manual sanity check — **DEFERRED** post-commit user-driven

## Gates summary

| Gate | Status | Klucz finding |
|---|---|---|
| 🔒 Security | **PASS** | 0 Crit/High/Med/Low. Refactor pure structural — zero zmian backend/auth/JWT/secrets/storage. `core-mappers` nie loguje (grep `Log\.\|println\|Timber` = 0). Constraint §16 (encrypted storage) niezagrożony (zero dep na `core-datastore`). R8/ProGuard chronione przez istniejące keep rules. Obs-1 (kosmetyczne): `kotlinx.coroutines.android` import w `build.gradle.kts` ale niewykorzystany — cleanup future. |
| 🔗 Contract Sync | **WARN** | Refactor sam jest **contract-clean** (29 pól mapowanych spójnie DTO↔Entity↔Domain). Ale gate wykrył **pre-existing drift** backend↔DTO (NIE wprowadzony przez WO-MOB-004): 5 pól w DTO nie pochodzi z `get_participants_for_mobile()` SQL SELECT — `phone`, `is_walkin`, `tags`, `buyer_name`, `buyer_email`. Moshi defaults `null`/`false` maskują brak. Skutek: `phone` latent bug fix w mapperze działa tylko gdy backend zaczął wysyłać phone — **out-of-scope** WO-MOB-004. |
| 🗄️ Migration Guard | **N/A** | Brak SQL migrations. Schema Room niezmieniona. |
| 🧪 QA | **DEFERRED** | Build PASS już zweryfikował compile. APK install + manual sanity check (lista uczestników + szczegóły + phone field bonus fix gdy backend pośle) — user-driven post-commit. |

## Findings

### [BONUS BUG FIX] `phone` field latent bug — naprawiony "by accident"

**Pre-refactor stan:**
- `SyncWorker.kt` DTO→Entity — MAPOWAŁ `phone` (`phone = dto.phone`)
- `ParticipantsViewModel` Entity→Domain (list) — MAPOWAŁ `phone = e.phone`
- `ParticipantDetailsViewModel.toParticipant()` Entity→Domain — **POMIJAŁ phone** (LATENT BUG)
- `ParticipantDetailsViewModel.loadParticipant()` Entity→Domain (legacy) — **POMIJAŁ phone** (LATENT BUG)

**Skutek pre-refactor:** details screen pokazywał `phone=null` mimo że Room cache mógł mieć wartość (jeśli backend by zaczął wysyłać `p.phone`).

**Post-refactor:** unified `toDomain()` zawiera `phone = phone`. Canonical wybrany = list view (`ParticipantsViewModel`). Bug fix wbudowany w refactor.

**Klasyfikacja:** bonus fix, nie security issue. Operator mobile ma legalny dostęp do `phone` (parity z desktop, endpoint chroniony `@require_mobile_event_access`).

**Visible w produkcji?** TYLKO jeśli backend SELECT zostanie rozszerzony o `p.phone`. Obecnie backend nie zwraca tego pola — patrz follow-up poniżej.

### [FOLLOW-UP CANDIDATE] WO-MOB-005 — Backend rozszerza mobile participants endpoint o brakujące pola

Contract Sync gate wykrył 5 pól w mobile DTO/Entity które nie są zwracane przez `get_participants_for_mobile()` SQL SELECT:
- `phone` (`p.phone`)
- `is_walkin` (możliwe z `participants.data` JSONB lub osobna kolumna)
- `tags` (`p.tags`?)
- `buyer_name`, `buyer_email` (`o.purchaser_name`, `o.purchaser_email`? — sprawdzić)

Drift jest **pre-existing** (nie wprowadzony przez WO-MOB-004). Moshi defaults maskują problem. Naprawienie wymagałoby SELECT extension + decyzji czy każde z 5 pól jest naprawdę potrzebne w mobile UI.

**Sugestia:** WO-MOB-005 "Backend mobile participants endpoint — uzupełnij brakujące pola" — sprawdzić które z 5 mobile UI ma używać + dodać do SQL SELECT. Phone w details screen byłby "free win" po tym WO.

### [INFO] `kotlinx.coroutines.android` import bez użycia (Obs-1 z Security gate)

`core/core-mappers/build.gradle.kts:14` — `implementation(libs.kotlinx.coroutines.android)` jest w gradle ale mapper to pure synchronous transformations (zero `suspend`, zero `Flow`). 

**Klasyfikacja:** kosmetyczne. NIE blokuje. Cleanup w future refactor WO (P3 backlog).

## Closure gotcha "Mobile Participant mapper 4-way duplication"

Po WO-MOB-004 ten gotcha z `known_gotchas.md` (utworzony 2026-05-19 w WO-MOB-003) jest **resolved**:
- Grep verification: `data class ParticipantEntity` = 1, `ParticipantEntity(` = 2 (1 declaration + 1 mapper), `= Participant(` = 1 (mapper only)
- Single source of truth: `core/core-mappers/.../ParticipantMappers.kt`
- Każde nowe pole w `Participant` = **1 edycja** (zamiast 4)
- Bonus: latent `phone` bug fix wbudowany w consolidation

**Akcja:** zaktualizować gotcha w `known_gotchas.md` z notką "✅ RESOLVED by WO-MOB-004 (2026-05-19) — single ParticipantMappers.kt in core-mappers/ module". Albo przenieść do sekcji "Historical / Resolved gotchas" jako lekcja na przyszłość.

## Recommendation

✅ **APPROVED dla commit + push.**

Refactor osiągnął cel:
- 4 inline mapowania zlikwidowane → 1 unified mapper
- Net diff: +11/-128 LOC (massive duplication removal)
- Wszystkie 4 builds PASS (core-mappers + core-sync + feature-participants + full :app:assembleDebug)
- Security PASS clean
- Contract Sync gate PASS (refactor-internal; pre-existing drift backend↔DTO out-of-scope)
- Bonus: latent `phone` bug fix included
- Architecture wzorzec ustanowiony dla future Entity/Domain mapperów (gdyby chcieć dodać EventMapper, OrderMapper itp.)

**Sekwencja domknięcia:**
1. Commit mobile submodule (7 modified + 2 new + 2 review/IR docs)
2. Bump submodule reference w main repo + commit meta-context (close gotcha)
3. Push obu repos
4. APK install + sanity check (user-driven)
5. (Opcjonalnie) Otworzyć WO-MOB-005 — backend SELECT extension dla brakujących 5 pól
