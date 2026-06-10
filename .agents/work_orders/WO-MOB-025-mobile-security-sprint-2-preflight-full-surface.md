# WO-MOB-025: Mobile Security Sprint 2 — Pre-flight (re-verification closures + disposition) + FULL Surface Mapping

**Data:** 2026-06-10
**Worker:** `worker-research` (READ-ONLY)
**Stage:** Mobile Security Audit Sprint 2 — Faza 1
**Priorytet:** Normalny (regularne okno bezpieczeństwa; poprzedni audyt mobilny 2026-05-18, od tego czasu ~40 commitów)
**Status:** ✅ DONE 2026-06-10 — F0: 10/10 closures PASS (zero regresji); disposition WO-207 6+1+1 CLOSED / 1 PARTIAL / 3 OPEN, MASVS 4 CLOSED / 8 OPEN; zero KRYT kandydatów 2.5 (1 WYS: deep-link autoVerify). KOREKTA: rate-limit mobile auth = CLOSED (żyje w `backend/security.py:42-66`, WO-203). F1: 47/47 endpointów + 21 modułów Kotlin + 11 PostHog events. Nowe flagi F2: PostHog captureDeepLinks→reset token leak, verify_sub=False, add-order bez role guard, upload SVG, companies-legacy PII.
**Plan:** [.agents/plans/plan_mobile_security_audit_sprint_2.md](../../../.agents/plans/plan_mobile_security_audit_sprint_2.md)
**Poprzednie audyty:** [F1_MOBILE_MASVS_AUDIT](../../../.agents/context/_audits/2026-05-comprehensive-security/F1_MOBILE_MASVS_AUDIT.md) (2026-05-15) · [WO-207 report](../../../.agents/work_orders/review_notes/SECURITY_AUDIT_WO207_simple-event-checkin.md) (2026-05-18)

---

## ⚠️ SPRINT MODE: HYBRID — ale TEN WO jest READ-ONLY

- ❌ **NIE modyfikuj kodu w:** `simple-event-checkin/` (Kotlin/gradle/manifest), `backend/`, `database/`
- ❌ **NIE wykonuj** `git add`, `git commit`, `git push` w submodułach kodu
- ✅ **MOŻESZ edytować TYLKO:** `.agents/context/_audits/2026-06-mobile-security-sprint-2/` + approval log planu
- ✅ Findings/regresje → dokumentacja MD. Fixy KRYT/WYS = Faza 2.5 (osobne WO) — NIE w tym WO.

---

## Cel

Pierwsza faza Mobile Sprint 2 — (1) sanity check, że closures z poprzednich audytów mobile nadal działają, (2) disposition KAŻDEGO starego finding (WO-207 + MASVS Sprint 1), (3) FULL inventory powierzchni kodu (decyzja usera A: pełny re-audit, nie delta). Output karmi Fazę 2 (FULL MASVS Deep Dive ×3).

**Deliverables:**

1. **F0_MOBILE_CLOSURES_RE_VERIFICATION.md** — re-verify closures + disposition table starych findings (OPEN/CLOSED/REGRESSED per pozycja).
2. **F1_MOBILE_CODE_SURFACE.md** — FULL inventory: wszystkie moduły Kotlin + KOMPLETNA tabela ~46 endpointów `backend/api/mobile.py` + PostHog event-katalog + encje Room + manifest/gradle/proguard/keystore.

---

## Zakres

### F0 — Re-verification closures (per pozycja: grep wzorca + file:line + verdict PASS/WARN/REGRESSED)

| Closure | Pattern | Plik |
|---|---|---|
| WO-201 (§16) | JWT w `EncryptedSharedPreferences` (AES256-SIV keys / AES256-GCM values), NIE plain DataStore | `core/core-datastore/.../AuthDataStore.kt:54-65` |
| WO-202 | jawne `addMigrations(...)` zamiast `fallbackToDestructiveMigration()` (Room v8→v9+ po WO-MOB-003) | `core/core-database/.../DatabaseModule.kt` |
| WO-204 | PII log guards (`BuildConfig.DEBUG`) + QR input validation | mobile commit `9663a1a` — zweryfikuj pliki |
| WO-SEC-002 | `_mobile_user_has_event_access` + `@require_mobile_event_access` na event-scoped endpointach | `backend/api/mobile.py:93,114` |
| WO-SEC-006 | change_password tuple bug fix | `backend/api/mobile.py:222+` |
| WO-SEC-008 | `mobile_order_update_status` event-scope authz | `backend/api/mobile.py:2361+` |
| WO-SEC-009 | review360/company_detail/person_detail CRM enumeration block (wymagany `event_id`) | `backend/api/mobile.py:1302,1745,1927` |
| WO-SEC-011 | mobile_login success/fail + change_password → `insert_admin_audit_log` | `backend/api/mobile.py` |
| WO-SEC-022 | PII role-aware filtering w `get_participants_for_mobile` (backend `cd17499`) | `backend/pg_storage.py` |
| WO-MOB-023 | klient dosyła `event_id` w body review360 (WO-SEC-009 compat) | mobile commit `80e7691` |

### F0 — Disposition table starych findings

- **WO-207 (2026-05-18):** wszystkie findings 0K/3W/3Ś/5N — m.in. JWT plaintext (→WO-201?), SQLite PII + destructive migration (→WO-202 częściowo? SQLCipher?), **brak rate limit `/login` + `/forgot-password` (ground-check Mastera 2026-06-10: nadal ZERO `@limiter` w mobile.py — spodziewany OPEN)**, resend-ticket auth gap, keep rules, logi.
- **MASVS Sprint 1 (2026-05-15):** wszystkie findings 0K/3W/5Ś/4N — m.in. MOB-6 deep link bez `autoVerify`, FLAG_SECURE, in-app session timeout, ungated `Log.e/w`.
- Kolumny: `finding`, `severity`, `źródło`, `status (CLOSED/OPEN/REGRESSED/PARTIAL)`, `evidence file:line`, `kandydat Fazy 2.5 (KRYT/WYS otwarte = TAK)`.

### F1 — FULL Code Surface Mapping

**Kotlin (`simple-event-checkin/` — WSZYSTKIE moduły, nie tylko delta):**
- `app/` — manifest (permissions, exported, deep links, backup), `build.gradle.kts` (release config, signing), `proguard-rules.pro`
- `core/core-network` — OkHttp/Retrofit config, interceptory (auth, logging), timeouty, error handling
- `core/core-datastore` — AuthDataStore (encrypted), inne datastores
- `core/core-database` — encje Room (pola PII!), DAO, migracje, DatabaseModule
- `core/core-analytics` — PostHog: init, consent, masking, event-katalog (lista WSZYSTKICH eventów + properties — flag PII), klucz API (BuildConfig?)
- `core/core-data` + `core/core-mappers` + `core/core-testing` — repository layer, offline queue (checkin/speakers), mappery
- `features/*` — feature-auth (login/logout/reset), feature-scanner (QR → walidacja), feature-participants, feature-events, feature-add-order (EB/sales-window WO-296), speakers, my-mentees, review360, stats
- Pliki wrażliwe w drzewie: `medidesk-release.jks`, `local.properties`, `RELEASE_SIGNING.md` — **tracked czy ignored?** (`git ls-files`), czy sekrety w historii git
- `gradle/libs.versions.toml` — wersje deps → CVE check (OkHttp, Retrofit, Room, PostHog SDK, security-crypto)

**Backend (`backend/api/mobile.py` — KOMPLETNA tabela wszystkich ~46 route'ów):**
- Kolumny per endpoint: `route`, `methods`, `auth (JWT/none)`, `event_scope (decorator/manual/brak)`, `rate_limit (ground-check: spodziewane "brak" wszędzie)`, `input_validation`, `audit_log (yes/no)`, `PII_returned`, `mutating (yes/no)`, `risk_flags (F2C hints)`
- Specjalna uwaga: `/login`, `/forgot-password`, `/reset-password`, `/change-password`, `/open-app` (deep link), `/upload-image`, `/walkin*`, `/checkin*`, `/speakers/*` (WO-MOB-015), `/inhub/verify-pin`, `/gus/lookup/<nip>`, add-order path (`_mobile_admin_create_order` §19), CRM-adjacent (companies/people/orders/partners/review360)
- Helpery: JWT issue/verify (algorytm, expiry, secret source, §15 compare), `get_participants_for_mobile` i inne `pg_storage` funkcje mobile

---

## Czego NIE ruszać 🛑

- **Zero modyfikacji kodu** — read-only research (Read/Grep/Glob/Bash read-only: `git log/diff/show/ls-files`).
- **Nie dotykaj Render env vars** (constraint §14).
- **Nie buduj APK** (decyzja usera D — bez decompile; `gradlew` NIE uruchamiać).
- **Nie uruchamiaj** testów ani aplikacji.

---

## Pliki startowe

1. Plan: [plan_mobile_security_audit_sprint_2.md](../../../.agents/plans/plan_mobile_security_audit_sprint_2.md)
2. [F1_MOBILE_MASVS_AUDIT.md](../../../.agents/context/_audits/2026-05-comprehensive-security/F1_MOBILE_MASVS_AUDIT.md) + [WO-207 report](../../../.agents/work_orders/review_notes/SECURITY_AUDIT_WO207_simple-event-checkin.md) — pełne listy starych findings
3. `git log --oneline --since="2026-05-18"` w `simple-event-checkin/` (delta jako sygnał priorytetu)
4. Constraints: [constraints_do_not_break.md](../../../.agents/context/constraints_do_not_break.md) §14/§15/§16/§19
5. `backend/api/mobile.py` (2758 linii)

---

## Definition of Done ✅

- [ ] F0 matrix: 10 closures z verdictami + disposition table KAŻDEGO finding WO-207 i MASVS (zero "nieustalono")
- [ ] F0: lista kandydatów Fazy 2.5 (otwarte/zregresowane KRYT/WYS)
- [ ] F1: 100% route'ów mobile.py w tabeli + 100% modułów Kotlin opisane + PostHog event-katalog + status keystore/local.properties (tracked/ignored)
- [ ] Cross-cutting flags dla F2A/F2B/F2C
- [ ] Pliki: `F0_MOBILE_CLOSURES_RE_VERIFICATION.md`, `F1_MOBILE_CODE_SURFACE.md`, `README.md` w `.agents/context/_audits/2026-06-mobile-security-sprint-2/`
- [ ] Post-check: `git status` w `simple-event-checkin/` i `backend/` = clean
- [ ] Update approval log w planie

## Test akceptacyjny 🧪

**N/A** (research). Smoke check: README → F0 (matrix + disposition kompletne) → F1 (46 endpointów policzalnych w tabeli, cross-check z `grep -c "@mobile_bp.route"`).

## Oczekiwany efekt 📋

3 pliki MD w `_audits/2026-06-mobile-security-sprint-2/` + gotowy input dla F2A/F2B/F2C.

## Kontrakt API 🔗

N/A — read-only.

## Snapshot

**SKIPPED** — read-only research (per master_agent §2.5).

## Gates

| Gate | Status |
|---|---|
| QA | SKIPPED — brak UI/API |
| Security | N/A — WO jest częścią Security Sprintu |
| Contract Sync | SKIPPED |
| Migration Guard | SKIPPED |

## Estymata czasu

**~2-2.5h** (F0: 45-60min; F1: 1.5h — full coverage Kotlin + 46 endpointów; README: 10min).

## Definition of Ready check (7/7)

1. ✅ Cel jasny — F0 closures+disposition / F1 full inventory
2. ✅ Zakres z konkretnymi plikami — listy powyżej
3. ✅ Czego nie ruszać — sekcja 🛑
4. ✅ Test akceptacyjny — smoke check dokumentów
5. ✅ Oczekiwany efekt — 3 pliki MD (efekt wizualny N/A)
6. ✅ Kontrakt API — N/A (read-only)
7. ✅ Pliki startowe — wskazane

**Sizing: 🟡 średni** (dużo plików do CZYTANIA, 1 warstwa — dokumentacja, zero kodu).
