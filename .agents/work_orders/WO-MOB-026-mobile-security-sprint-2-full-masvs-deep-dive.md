# WO-MOB-026: Mobile Security Sprint 2 — FULL MASVS Deep Dive (3 sub-audyty równoległe)

**Data:** 2026-06-10
**Worker:** `worker-security` × 3 (parallel — decyzja usera B)
**Stage:** Mobile Security Audit Sprint 2 — Faza 2
**Status:** ✅ DONE 2026-06-10 — 3× worker-security parallel. F2A FAIL (0K/2W/3Ś/7N: logout-Room-wipe NOWE WYS, deep-link autoVerify WYS open, keystore-hasła-na-dysku ŚR) · F2B FAIL (0K/1W/4Ś/5N: captureDeepLinks→reset-token do PostHog WYS potwierdzony sources.jar; JWT bez revocation ŚR) · F2C FAIL (0K/2W/3Ś/4N, 47/47 endpointów: SVG upload WYS, checkout bez per-actor RL WYS; verified-clean: JWT/auth-flow/§19/race/SQL). Razem **0 KRYT / 5 WYS / 10 ŚR / 16 NISK**. Kandydaci 2.5 przekazani → 5 fixów wykonanych + 1 DEFER (WO-MOB-029).
**Plan:** [.agents/plans/plan_mobile_security_audit_sprint_2.md](../../../.agents/plans/plan_mobile_security_audit_sprint_2.md)
**Zależy od:** WO-MOB-025 (F0+F1 jako input — inventory i kandydaci)

---

## ⚠️ SPRINT MODE: HYBRID — ale TEN WO jest READ-ONLY

- ❌ **NIE modyfikuj kodu** (`simple-event-checkin/`, `backend/`, `database/`); zero `git add/commit/push`
- ✅ **Edycja TYLKO:** `.agents/context/_audits/2026-06-mobile-security-sprint-2/`
- ✅ Findings → severity-tagged dokumentacja. Fixy KRYT/WYS = Faza 2.5 (osobne WO PO tym WO).

---

## Cel

Pełny audyt MASVS L1+L2 (MOB-1..MOB-9) aplikacji `simple-event-checkin` + pełny przegląd powierzchni serwerowej `backend/api/mobile.py` (~46 endpointów). Trzy równoległe sub-audyty, każdy z własnym dokumentem i verdictem per kategoria.

## Zakres — 3 sub-audyty

### F2A — Storage & Platform (MASVS MOB-2 / MOB-3 / MOB-6 / MOB-8)
**Output:** `F2A_STORAGE_PLATFORM_REVIEW.md`
- Room DB: pola PII w encjach (participants/speakers/walkins/orders), szyfrowanie (SQLCipher?), migracje jawne, co zostaje po logout (czyszczenie cache przy zmianie usera!)
- DataStore/EncryptedSharedPreferences: §16 intact, co jeszcze jest przechowywane (PostHog id? consent?)
- Keystore/MasterKey usage, fallbacki
- AndroidManifest: permissions minimalne, `exported`, `allowBackup`, `usesCleartextTraffic`, deep links (`/open-app` — autoVerify? parametry?)
- FLAG_SECURE na ekranach z PII (lista uczestników, szczegóły, QR)
- ProGuard/R8: `isMinifyEnabled`, keep rules (zbyt szerokie — NISKIE z WO-207)
- **Higiena repo: `medidesk-release.jks`, `local.properties`, `RELEASE_SIGNING.md` — tracked? sekrety w git history? `.gitignore` coverage**
- CVE check deps z `libs.versions.toml` (OkHttp, Retrofit, Room, security-crypto, PostHog SDK, Compose BOM)

### F2B — Network, Auth & Telemetry (MASVS MOB-1 / MOB-4 / MOB-5 / MOB-7 / MOB-9)
**Output:** `F2B_NETWORK_AUTH_TELEMETRY_REVIEW.md`
- OkHttp/Retrofit: TLS only, cert pinning (decyzja świadoma?), timeouty, retry semantics, HttpLoggingInterceptor gated `BuildConfig.DEBUG`
- JWT lifecycle client-side: storage (§16), attach (interceptor), expiry handling (72h), logout (czy token unieważniany? czy tylko lokalnie kasowany?), brak in-app session timeout (MASVS Średnie — disposition)
- **PostHog (WO-160/161, NOWE od ostatniego audytu):** consent opt-in/out flow, `BuildConfig` API key, host, event properties — czy NIE niosą PII (email/nazwisko/QR/ticket id), masking config, kill-switch
- Logging w release: nowe `Log.*` z delty WO-MOB-009..024 + WO-296 — PII bez DEBUG guard? (WO-204 re-check na NOWYM kodzie)
- QR scanner input: walidacja formatu (WO-204), co się dzieje z malformed/oversized payload
- Privacy: zgodność z Privacy Policy, dane wysyłane third-parties (PostHog only?)

### F2C — Backend Mobile API (CAŁY `backend/api/mobile.py`)
**Output:** `F2C_MOBILE_API_BACKEND_REVIEW.md`
- **WSZYSTKIE ~46 endpointów** z tabeli F1 (WO-MOB-025): authz (JWT verify, §15 compare), event-scope (`@require_mobile_event_access` — WO-SEC-002 pattern na KAŻDYM event-scoped), **rate limiting (ground-check: ZERO `@limiter` — spodziewany główny finding WYSOKIE, kandydat Fazy 2.5)**, input validation (parametry path/query/body), audit log na mutacjach, PII minimization w response (WO-SEC-022 pattern)
- Auth flow: `/login` (brute-force, lockout, audit), `/forgot-password` (email flooding, enumeration), `/reset-password` (token entropy/expiry/single-use), `/change-password`
- `/upload-image`: typ/rozmiar/magic bytes/ścieżka zapisu/serving
- Offline sync: `/checkin/sync`, `/speakers/checkin/sync`, `/walkin/batch` — idempotencja, dedup, limity batch size, race conditions (`SELECT FOR UPDATE`)
- Add-order path (`_mobile_admin_create_order` + cart-config): server-side pricing §19, multi-participant limits (follow-up P1 z WO-171 — status), audit scope
- `/inhub/verify-pin`: brute-force PIN, `/gus/lookup/<nip>`: SSRF/injection/rate na zewn. API
- JWT issue/verify helpers: algorytm, secret (`FLASK_SECRET_KEY`?), claims, expiry

## Format findings (wszystkie 3 dokumenty)

Per finding: `ID (F2A-NNN/F2B-NNN/F2C-NNN)`, `severity (KRYT/WYS/ŚR/NISK)`, `MASVS ref`, `opis`, `file:line`, `ryzyko`, `remediation hint`, `proposed WO (WO-MOB-NNN mobile-side / WO-SEC-NNN backend-side)`, `kandydat Fazy 2.5 (KRYT/WYS=TAK)`.
Plus sekcja "verified-clean" (co sprawdzono i jest OK) — wzorzec Sprint 3.

---

## Czego NIE ruszać 🛑

- Zero modyfikacji kodu; zero buildów (`gradlew` zakazany — decyzja D); zero Render env (constraint §14); zero testów/aplikacji.

## Pliki startowe

1. F0+F1 z WO-MOB-025 (`_audits/2026-06-mobile-security-sprint-2/`)
2. [worker_security_audit.md](../../../.agents/workflows/worker_security_audit.md) — procedura 12 kategorii (adaptacja mobile)
3. Stare raporty: MASVS Sprint 1 + WO-207 (kalibracja severity)
4. Constraints §14/§15/§16/§19

## Definition of Done ✅

- [ ] 3 dokumenty F2A/F2B/F2C z verdictami per kategoria MASVS (PASS/WARN/FAIL)
- [ ] Każdy finding severity + file:line + remediation hint + proposed WO
- [ ] Sekcje verified-clean (pozytywy jawnie)
- [ ] Skonsolidowana lista kandydatów Fazy 2.5 (KRYT/WYS) na końcu każdego dokumentu
- [ ] Post-check: `git status` kod = clean
- [ ] Update approval log w planie

## Test akceptacyjny 🧪

N/A (audit). Smoke: 3 pliki istnieją, każdy ma coverage matrix MASVS + histogram severity; F2C pokrywa 100% endpointów z F1 (cross-check liczby).

## Snapshot

**SKIPPED** — read-only audit.

## Gates

| Gate | Status |
|---|---|
| QA | SKIPPED |
| Security | N/A — to JEST security audit |
| Contract Sync | SKIPPED |
| Migration Guard | SKIPPED |

## Estymata czasu

**4-6h effort, ~2-2.5h wall-clock** (3× parallel).

## Definition of Ready check (7/7)

1. ✅ Cel — full MASVS + full mobile.py
2. ✅ Zakres — 3 sub-audyty z listami obszarów
3. ✅ Czego nie ruszać — 🛑
4. ✅ Test akceptacyjny — smoke dokumentów
5. ✅ Oczekiwany efekt — 3 pliki MD z verdictami
6. ✅ Kontrakt API — N/A
7. ✅ Pliki startowe — F0/F1 + procedury

**Sizing: 🔴 duży zakresem czytania, ale akceptowany jako pojedynczy audit-WO bez rozbijania (precedens WO-SEC-030 Sprint 3 — 3 parallel sub-audyty w 1 WO).**
