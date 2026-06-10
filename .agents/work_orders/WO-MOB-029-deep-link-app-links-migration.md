# WO-MOB-029: Deep link resetu hasła — migracja custom scheme → Android App Links (LUB ADR accepted-risk)

**Data:** 2026-06-10
**Worker:** [TBD — wymaga decyzji usera]
**Stage:** Mobile Security Audit Sprint 2 — scaffold z Fazy 2 (DEFER — NIE wykonany inline)
**Finding:** F2A-002 (WYSOKIE, OPEN od MASVS Sprint 1 jako MOB-HIGH-003) — [F2A_STORAGE_PLATFORM_REVIEW.md](../../../.agents/context/_audits/2026-06-mobile-security-sprint-2/F2A_STORAGE_PLATFORM_REVIEW.md)
**Status:** ✅ CLOSED 2026-06-10 — **ACCEPTED RISK (decyzja usera: opcja B)**. ADR-WO-MOB-029 w `decision_log.md`: custom scheme zostaje; mitygacje backendowe mocne (single-use + TTL 2h + regex + anti-enum), leak do PostHog odcięty (WO-MOB-031), wąski profil ataku. **Re-review przy następnym audycie mobile LUB przy wejściu na publiczny Production track Google Play** — wtedy opcja A (App Links) wg instrukcji niżej.
**Sizing:** 🟡 średni (3 warstwy: manifest+nav / backend assetlinks / email template + Play Console)

---

## Dlaczego DEFER (nota Mastera 2026-06-10)

Fix wymaga działań NIEMOŻLIWYCH do wykonania autonomicznie w sprincie: SHA-256 fingerprint z Play Console, hosting `/.well-known/assetlinks.json` na domenie produkcyjnej, zmiana template emaila resetu, deploy-order-sensitive rollout (link HTTPS w emailu działa dopiero gdy assetlinks żyje — inaczej reset ląduje w przeglądarce zamiast w apce). Mitygacje backendowe są MOCNE i zweryfikowane w F0/F2C: token single-use + TTL 2h + regex + anti-enum.

## Dwie opcje (user wybiera)

**A) Migracja App Links (~0.5-1 dnia, 3 warstwy):**
1. Backend: serwuj `/.well-known/assetlinks.json` (package `pl.medidesk.mobile` + SHA-256 z Play Console App Signing).
2. Manifest: intent-filter `https://<domena>/open-app` z `android:autoVerify="true"` (zostaw stary `medidesk://` jako fallback przez okres przejściowy).
3. Email template resetu: link HTTPS zamiast `medidesk://`.
4. Rollout: backend najpierw → weryfikacja assetlinks (`adb shell pm get-app-links`) → release apki → zmiana emaila.

**B) ADR accepted-risk (~15 min):** wpis do `decision_log.md`: custom scheme świadomie zaakceptowany; uzasadnienie: backend mitygacje (single-use, 2h TTL, regex, anti-enum) + niski profil ataku (wymaga zainstalowanej złośliwej apki na urządzeniu operatora w 2h oknie); review przy następnym audycie.

## Powiązane (domknięte w Fazie 2.5)

- F2B-001: leak tokenu do PostHog przez `captureDeepLinks` — FIXED w WO-MOB-031 (niezależnie od decyzji A/B).
- F2A-010 (NISK): surowa interpolacja tokena w nav route — kandydat do sprzątnięcia przy opcji A.
