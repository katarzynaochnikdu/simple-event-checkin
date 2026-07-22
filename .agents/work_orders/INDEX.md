# Work Orders INDEX — simple-event-checkin

> Rejestr Work Orderów **mobile** (Android natywny).
> Desktop WO → [.agents/work_orders/](../../../.agents/work_orders/).
>
> Dodawane przez `/mwo <opis>` (subagent `work-order-logger`, scope=mobile).
> Format pliku: `WO-MOB-NNN-<slug>.md`. Numeracja chronologiczna.

| ID | Tytuł | Worker | Status | Utworzony |
|---|---|---|---|---|
| WO-MOB-001 | Weryfikacja auth flow po security WO (WO-201/202/204) | [TBD] | Otwarty | 2026-05-19 |
| WO-MOB-002 | Backend — dodaj rsvp_sent / rsvp_response / rsvp_responded_at do mobile participants endpoint | worker-implementer | Otwarty (DoR ✅) | 2026-05-19 |
| WO-MOB-003 | Mobile — Room v8→v9 + DTO/Domain + Composable warunkowy render RSVP (depends WO-MOB-002) | worker-implementer | Otwarty (DoR ✅) | 2026-05-19 |
| WO-MOB-004 | Refactor — likwidacja 4-way duplication mappera Participant (DTO.toEntity + Entity.toDomain) | [TBD] | ⏳ Otwarty | 2026-05-19 |
| WO-MOB-005 | Backend SELECT extension — uzupełnij brakujące 5 pól w get_participants_for_mobile() endpoint | [TBD] | ⏳ Otwarty | 2026-05-19 |
| WO-MOB-006 | RSVP semantics unification desktop↔mobile (strict dla wszystkich, modyfikuje backend desktop) | [TBD] | ⏳ Otwarty | 2026-05-19 |
| WO-MOB-007 | Fix drift `get_all_participants` rsvp_sent — dodaj scheduled campaign OR clause (linia 10791) | [TBD] | ⏳ Otwarty | 2026-05-19 |
| WO-MOB-009 | Fix — feedback check-in (zielony/czerwony) zawsze jako top-level overlay nad tab barem | worker-implementer | ✅ DONE | 2026-05-19 |
| WO-MOB-010 | Fix UNDONE color (ScanError) + remove "Cofnij wejście" button from ScanResultOverlay SUCCESS | worker-implementer | ✅ DONE | 2026-05-20 |
| WO-MOB-011 | Bottom NavBar — wrap labela "Wydarzenie" do 2 linii (opcja A, accept icon shift) | Master | ✅ DONE | 2026-05-20 |
| WO-MOB-012 | Fix mobile undercount check-in (Wątek A z BUG-MOB-002) — kanoniczny predykat OR-3 | Master + worker-debugger | ✅ DONE | 2026-05-20 |
| WO-MOB-013 | Fix totals mismatch — attendance_status filter (Wątek B z BUG-MOB-002) | Master + worker-implementer | ✅ DONE | 2026-05-20 |
| WO-MOB-014 | Sync button — odswiezanie listy uczestnikow w cache wydarzenia (manual refresh) | Master + worker-implementer | ✅ DONE | 2026-05-25 |
| WO-MOB-015 | Reczny check-in prelegentow (bez QR) w mobile (3 fazy: backend+DB + mobile online + offline queue) | Master + worker-implementer | ✅ DONE | 2026-05-25 |
| WO-MOB-016 | Mobile etykiety tagów uczestników zgodne z modelem kanonicznym (desktop) | worker-implementer | ✅ DONE | 2026-05-25 |
| WO-MOB-017 | Zakładka „Trwające" na liście wydarzeń (dynamiczna, dev-gated Sandbox) + fix data-granularny „dzień wydarzenia wpada w przeszłość" | Master + worker-implementer | ✅ DONE | 2026-05-28 |
| WO-MOB-018 | Sandbox events inline w grupach z pomarańczowym pillem „SANDBOX" (dev-only), usunięcie osobnej zakładki Sandbox | Master + worker-implementer | ✅ DONE | 2026-05-28 |
| WO-MOB-021 | Autoscroll na sekcję „Szczegóły zamówienia" w karcie uczestnika (ParticipantDetailsScreen) | worker-implementer | ✅ DONE | 2026-05-28 |
| WO-MOB-022 | Auto-scroll/focus rozwiniętej CompanyCard na ekranie „Moi podopieczni" (MyMenteesScreen, LazyColumn → animateScrollToItem) | worker-implementer | ✅ DONE | 2026-05-28 |
| WO-MOB-023 | Fix dashboard firmy (Review360) z „Moi podopieczni" — dosłanie `event_id` w body (WO-SEC-009 compat) | worker-debugger | ✅ DONE | 2026-05-28 |
| WO-MOB-025 | Mobile Security Sprint 2 — Faza 1: pre-flight (closures re-verify + disposition WO-207/MASVS) + FULL surface mapping | worker-research | ✅ DONE (10/10 closures PASS; 47 endpointów; 0 KRYT) | 2026-06-10 |
| WO-MOB-026 | Mobile Security Sprint 2 — Faza 2: FULL MASVS deep dive ×3 parallel (F2A storage/platform · F2B network/auth/PostHog · F2C mobile.py 47 endpointów) | worker-security ×3 | ✅ DONE (0K/5W/10Ś/16N — 31 findings) | 2026-06-10 |
| WO-MOB-027 | Mobile Security Sprint 2 — Faza 3: op debt + final report (PO Fazie 2.5 inline remediation) + scaffoldy ŚR/NISK | worker-research + technical-writer | ✅ DONE (CONDITIONAL SAFE; 8 scaffoldów; R-135..146) | 2026-06-10 |
| WO-MOB-028 | Faza 2.5: Logout wipe — clearAllTables() Room w 5 ścieżkach logout (F2A-001 WYS: cross-user PII + misatrybucja audytu) | worker-implementer | ✅ DONE (build PASS, 5/5 testów; committed `09a1300`) | 2026-06-10 |
| WO-MOB-029 | Deep link resetu hasła: App Links migration LUB ADR accepted-risk (F2A-002 WYS — jedyny WYS deferowany) | Master | ✅ CLOSED — ACCEPTED RISK (ADR-WO-MOB-029; re-review przy Production track) | 2026-06-10 |
| WO-MOB-030 | Faza 2.5 rider: FLAG_SECURE w MainActivity gated !DEBUG (F2A-003 ŚR — low-cost, 8 ekranów PII) | worker-implementer | ✅ DONE (+8 linii, build PASS; committed `09a1300`) | 2026-06-10 |
| WO-MOB-031 | Faza 2.5: PostHog hardening — captureDeepLinks=false (F2B-001 WYS: token resetu→PostHog) + captureLogcat=false + redactHeader n/a + optOut order | worker-implementer | ✅ DONE (+36/-10, build PASS; committed `09a1300`) | 2026-06-10 |
| WO-MOB-032 | Privacy Policy + dialog zgody — sync z implementacją telemetrii (F2B-004 ŚR) + link w Settings + TELEMETRY.md drift §3.4/§5.2 | worker-implementer | ✅ DONE 2026-06-10 (committed `272526f`) | 2026-06-10 |
| WO-MOB-033 | Sesja/telemetria NISK bundle: N-1 NonCancellable w LogoutUseCase + N-2 Coil cache wipe + F2B-007 residualy (optOut order) + F2B-009 redactHeader local logcat | worker-implementer | ✅ DONE 2026-06-10 (committed `5362138`) | 2026-06-10 |
| WO-MOB-034 | Platform/build NISK bundle: N-3 FLAG_SECURE dialogs + F2A-007 ProGuard + F2A-008 manifest + F2A-011 ACTION_VIEW allowlist + F2B-005/006 + F2A-012 dead modules | worker-implementer | ✅ DONE 2026-06-10 (committed `5362138`) | 2026-06-10 |
| WO-MOB-035 | SQLCipher — encryption-at-rest Room md_checkin.db (F2A-004 ŚR, deferred od WO-207#2; pilność spadła po WO-MOB-028) | [TBD] | OPEN (deferred — decyzja usera) | 2026-06-10 |
| WO-MOB-036 | AppNavHost consent-order (F2B-007c) + SecureDialogEffect na 5 residualnych dialogach PII (3 ekrany — N-3 residual) | Master | 🔧 IN PROGRESS 2026-06-12 (build pending) | 2026-06-12 |
