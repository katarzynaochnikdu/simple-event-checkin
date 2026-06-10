# WO-MOB-032: Privacy Policy + dialog zgody — sync z faktyczną implementacją telemetrii (+ link w Settings + TELEMETRY.md drift)

**Data utworzenia:** 2026-06-10 (scaffolded w Mobile Security Sprint 2, Faza 3)
**Worker:** worker-implementer (docs + drobny UI)
**Stage:** Mobile Sprint 2 Remediation — Faza 2
**Priorytet:** 🟡 P2 (ŚR — GDPR art. 13 / Play Data Safety)
**Status:** ✅ DONE 2026-06-10 — PRIVACY_POLICY_MOBILE.md v1.2 (monorepo docs/: anonimowe→pseudonimowe §4.4/§8/§12 + ścieżka Settings) + AnalyticsConsentDialog copy (pseudonimowe + lista kategorii) + SettingsScreen pozycja „Polityka prywatności" (reuse `openExternalUrl` z WO-MOB-034, stała `PRIVACY_POLICY_URL`, placeholder `digitalunity.pl/privacy-policy` — **URL do potwierdzenia/publikacji usera**) + TELEMETRY.md drift (§3.4 captureLogcat=false, §5.2 captureDeepLinks=false, §4.1/§4.3 consent order). assembleDebug PASS. MdApplication/ChangePasswordDialog NIETKNIĘTE. Security gate NIEwymagany (docs + 1 link na audytowanym helperze; obniża ekspozycję RODO). **Residual flagga:** first-launch decline path (`AppNavHost.AuthViewModel.saveAnalyticsConsent`) nadal gubi event opted_out (F2B-007, poza WO-MOB-033) — follow-up. COMMITTED mobile wave + monorepo (poniżej).
**Finding:** **F2B-004 (ŚR)** — [F2B_NETWORK_AUTH_TELEMETRY_REVIEW.md](../../../.agents/context/_audits/2026-06-mobile-security-sprint-2/F2B_NETWORK_AUTH_TELEMETRY_REVIEW.md) + doc-residual WO-MOB-031

---

## Cel

Doprowadzić deklaracje (Privacy Policy, dialog zgody, TELEMETRY.md) do zgodności z faktyczną implementacją telemetrii — zgoda zbierana na podstawie nieścisłego opisu jest podważalna (GDPR art. 13).

## Zakres (4 rozjazdy z F2B-004 + drift po WO-MOB-031)

1. **`docs/PRIVACY_POLICY_MOBILE.md` → v1.2** (plik w docs/ monorepo):
   - §4.4 (:85): „Anonimowy identyfikator urządzenia (UUID)" → faktycznie `Analytics.identify(userId = admin_id, role)` (`LoginViewModel.kt:51`) = **pseudonimowy identyfikator użytkownika** — poprawić treść;
   - §8 (:151): „dane anonimowe i NIE pozwalają na identyfikację" → sprzeczne z identify + super-props device_manufacturer/model — przeformułować na pseudonimizację;
   - §12 (:207): „dostępna w aplikacji w sekcji ustawień" → dziś NIEPRAWDA (grep privacy w feature-more = 0) — patrz pkt 3.
2. **`app/src/main/java/pl/medidesk/mobile/ui/AnalyticsConsentDialog.kt:58-69`** — copy: „anonimowe dane… nie zawierają PII" → „pseudonimowe" + zwięzła poprawna lista kategorii (eventy techniczne, identyfikator konta, model urządzenia).
3. **`features/feature-more/.../SettingsScreen.kt`** — dodać pozycję „Polityka prywatności" (ACTION_VIEW → publiczny URL polityki; URL przez stałą, nie hardcode w composable; tylko http(s)).
4. **`simple-event-checkin/docs/TELEMETRY.md`** — drift po WO-MOB-031: §3.4 (:98-107) dopisać `captureLogcat = false` (jawnie, z uzasadnieniem F2B-002); §5.2 (:193) `captureDeepLinks = true` → `false` (WO-MOB-031 / F2B-001 — token resetu); §4.3/„consent change" (:136) — skorygować twierdzenie o wysyłce audit-eventu zgody (F2B-007: event ginie przy opt-out; do czasu fixu w WO-MOB-033 opisać stan faktyczny).

## Czego NIE ruszać 🛑

- Konfiguracja PostHog w `MdApplication.kt` (domknięta w WO-MOB-031) — zero zmian kodu telemetrii.
- Call-site'y eventów (11 eventów = zweryfikowane zero PII) — bez zmian.
- Publikacja polityki na `digitalunity.pl` = op-action usera (poza repo) — w WO tylko link.

## Test akceptacyjny 🧪

1. `./gradlew :app:assembleDebug` PASS.
2. Settings → „Polityka prywatności" otwiera przeglądarkę z URL polityki.
3. Review treści: zero słowa „anonimowe" w odniesieniu do danych identyfikowanych per admin_id; TELEMETRY.md zgodny z `MdApplication.kt` (grep captureDeepLinks/captureLogcat — wartości w docu = wartości w kodzie).

## Sizing / Estymata

🟢 mały — ~1-2h (docs + 1 pozycja w Settings).
