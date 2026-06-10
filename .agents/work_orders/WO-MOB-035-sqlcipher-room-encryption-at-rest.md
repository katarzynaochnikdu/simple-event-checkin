# WO-MOB-035: SQLCipher — encryption-at-rest dla Room `md_checkin.db` (DEFERRED — decyzja usera)

**Data utworzenia:** 2026-06-10 (scaffolded w Mobile Security Sprint 2, Faza 3)
**Worker:** worker-implementer
**Stage:** Mobile Sprint 2 Remediation — Faza 3 (duże / deferred)
**Priorytet:** 🟠 P3 — **świadomie DEFERRED** (od WO-207#2, 2026-05-18); wykonanie = osobna decyzja usera przy planowaniu większego okna
**Status:** **OPEN (deferred)**
**Finding:** **F2A-004 (ŚR)** = WO-207#2 / MOB-HIGH-002 — [F2A_STORAGE_PLATFORM_REVIEW.md](../../../.agents/context/_audits/2026-06-mobile-security-sprint-2/F2A_STORAGE_PLATFORM_REVIEW.md)

---

## Cel

Zaszyfrować lokalną bazę Room (`md_checkin.db`, plaintext SQLite) — PII offline cache: `ParticipantEntity` 9 pól (first/last/email/phone/company/buyer_name/buyer_email/**purchaser_nip**/purchaser_company — ParticipantEntity.kt:22-46) + `WalkinEntity` 5 pól + wolnotekstowe `notes` (WalkinEntity.kt:16-23).

## Kontekst pilności (dlaczego deferred jest OK)

Po **WO-MOB-028** (logout wipe, Faza 2.5) okno ekspozycji znacząco zmalało: PII żyje na urządzeniu TYLKO w trakcie zalogowanej sesji. Pozostałe ryzyko: utrata urządzenia z aktywną sesją (zalogowany operator). Mitygacje aktywne: `allowBackup=false` (manifest:15), EncryptedSharedPreferences dla tokenów (WO-201), FLAG_SECURE (WO-MOB-030).

## Zakres (szkic — ~120 LOC + migracja)

1. Zależność `net.zetetic:sqlcipher-android` (+ `androidx.sqlite`) w `libs.versions.toml` + `core-database/build.gradle.kts`.
2. Klucz DB: generowany per-device (SecureRandom 32B), przechowywany w EncryptedSharedPreferences (AuthDataStore lub dedykowany store — zgodnie z constraint §16).
3. `DatabaseModule.kt:20` — `SupportFactory(passphrase)` w buildzie Room.
4. **Migracja danych v10 → v11 (encrypted):** istniejąca plaintext DB → `sqlcipher_export()` LUB świadoma decyzja: drop + resync (dane to CACHE — pełny resync z backendu jest tani; pending offline queue = jedyne dane nieodtwarzalne → flush przed migracją albo akceptacja utraty; rekomendacja w DoR).
5. Testy: instrumentation open-with-key + odmowa otwarcia bez klucza; regresja DAO suite.

## Czego NIE ruszać 🛑

- Semantyka logout wipe (WO-MOB-028) i migracje 7→10 (WO-202) — bez zmian.
- `fallbackToDestructiveMigration` (dotyczy tylko v1-6 pre-prod) — nie rozszerzać na v11.
- Backend — zero zmian.

## Test akceptacyjny 🧪

1. Po update apki: dane czytelne (lub czysty resync wg decyzji), zero crash na istniejących instalacjach.
2. Plik `md_checkin.db` pobrany z urządzenia (adb, debug) → nieczytelny bez klucza (header ≠ "SQLite format 3").
3. `assembleDebug` + `assembleRelease` PASS; pełna suite testów zielona.

## Sizing / Estymata

🔴 duży — ~1-2 dni (zależność natywna ~7MB APK, migracja danych, testy na fizycznym urządzeniu). Decyzja usera wymagana PRZED startem (rozmiar APK + strategia migracji).
