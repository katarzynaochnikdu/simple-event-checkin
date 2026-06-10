# WO-MOB-031: PostHog hardening bundle — stop leak tokenu resetu hasła + logcat + nagłówki

**Data:** 2026-06-10
**Worker:** `worker-implementer`
**Stage:** Mobile Security Audit Sprint 2 — Faza 2.5 (inline remediation)
**Findings:** F2B-001 (WYSOKIE) + ridery z tego samego bloku konfiguracji: F2B-002 (ŚR), F2B-007 (NISK), F2B-009 (NISK) — [F2B_NETWORK_AUTH_TELEMETRY_REVIEW.md](../../../.agents/context/_audits/2026-06-mobile-security-sprint-2/F2B_NETWORK_AUTH_TELEMETRY_REVIEW.md)
**Status:** ✅ DONE 2026-06-10 (implementer) — MdApplication.kt +36/-10 (jedyny plik): (1) `captureDeepLinks=false` + komentarz-strażnik; (2) `captureLogcat=false` jawnie (default SDK=true!), blok sessionReplayConfig zdjęty z warunku consent (data holder, zero fail-open); (3) redactHeader = n/a UDOKUMENTOWANE (PostHogOkHttpInterceptor nieużywany w NetworkModule; brak captureNetworkTelemetry w 3.11.0; zweryfikowane sources.jar) + instrukcja re-weryfikacji przy bumpie SDK; (4) kolejność optOut JUŻ poprawna (optOut w configu przed setup) + komentarz zakazu. assembleDebug BUILD SUCCESSFUL. Grep: captureDeepLinks ×1 (false). COMMITTED mobile `09a1300` (2026-06-10, pushed). **Op-actions usera:** purge eventów "Deep Link Opened" w PostHog 178536; opcjonalnie inwalidacja wiszących reset-tokenów; release bump versionCode 4. **Residualy (ridery przyszłe):** HttpLoggingInterceptor DEBUG loguje Bearer do LOKALNEGO logcat (1 LOC redactHeader w core-network); SettingsViewModel optOut przed capture(CONSENT_CHANGED) gubi audit-event; LogoutUseCase reset bez optOut → SDK opted-in do restartu; TELEMETRY.md §3.4/§5.2 drift.
**Snapshot:** `snapshot/pre-mobile-security-sprint-2-remediation-2026-06-10` @ mobile `1876fbe`
**Sizing:** 🟢 mały (1-2 pliki)

---

## Cel

Zatrzymać wysyłkę tokenu resetu hasła do PostHog Cloud (potwierdzone dekompilacją SDK 3.11.0: `captureDeepLinks=true` → event "Deep Link Opened" z KAŻDYM query paramem, w tym `token=`) + domknąć sąsiednie gapy konfiguracji telemetrii.

## Zakres

`app/src/main/java/pl/medidesk/mobile/MdApplication.kt` (blok konfiguracji PostHog, okolice :60-75):

1. **F2B-001:** `captureDeepLinks = false` (linia 67).
2. **F2B-002:** `captureLogcat = false` — jawnie (default SDK = true przy session replay; logcat aplikacji NIE może płynąć do 3rd party — niezadeklarowane w polityce prywatności).
3. **F2B-009:** dodaj redakcję nagłówka auth w network capture session replay (jeśli config eksponuje — w PostHog Android: `sessionReplayConfig.captureNetworkTelemetry` / OkHttp interceptor `PostHogOkHttpInterceptor.redactHeader("Authorization")` — zastosuj wariant właściwy dla używanej integracji; jeśli nie używamy network capture, dopisz jawny komentarz że nie aplikuje).
4. **F2B-007:** kolejność init: upewnij się że stan consent (`optOut`) jest aplikowany PRZED jakimkolwiek `capture` (jeśli obecny kod robi setup → optOut, odwróć: skonfiguruj `optOut = !consentGranted` w configu przed `PostHogAndroid.setup`).

Zachowaj resztę konfiguracji bez zmian (masking, host, klucz). Dopisz krótki komentarz przy `captureDeepLinks = false` z referencją „WO-MOB-031 / F2B-001: deep link niesie token resetu hasła — NIGDY nie włączać".

**Op-action usera (NIE w tym WO, do raportu):** purge historycznych eventów "Deep Link Opened" w projekcie PostHog 178536.

## Czego NIE ruszać 🛑

- NIE wyłączaj całej telemetrii (kill-switch zostaje jak jest), NIE zmieniaj klucza/hosta, NIE ruszaj event call-site'ów (11 eventów = zweryfikowane zero PII). NIE commituj.

## Test akceptacyjny 🧪

1. `./gradlew :app:assembleDebug` PASS.
2. Code-review: `captureDeepLinks = false` + `captureLogcat = false` obecne; brak `capture` przed aplikacją consent.
3. Grep: `captureDeepLinks` w repo = 1 wystąpienie (false).

## Definition of Done

- [ ] 4 punkty konfiguracji domknięte (lub jawnie skomentowane n/a)
- [ ] assembleDebug PASS
- [ ] Zero zmian poza blokiem PostHog config
