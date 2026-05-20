# PROGRESS — simple-event-checkin (Android Kotlin)

> Chronologiczny log "co robiłem" w obszarze **mobile** (Android natywny, `simple-event-checkin/`).
> Desktop → [.agents/PROGRESS.md](../../.agents/PROGRESS.md).
>
> Wpisy dodawane przez `/mlog <opis>` (subagent `progress-logger`, scope=mobile).
> Najnowsze na górze. Jeden bullet = jedna istotna rzecz (commit / decyzja / odkrycie / blocker).

---

## 2026-05-20

- **WO-MOB-013 DONE** — Fix Wątek B z BUG-MOB-002 (rozjazd "łącznie uczestników" mobile vs web). AMOZ Kraków mobile=107 vs web=100, Dental mobile=49 vs web=46. Mobile filtrował po `o.status NOT IN ('cancelled','refunded')` (anulowane orders), web "Pewni" filtruje po `p.attendance_status IN ('confirmed','pending')` (anulowane RSVP) — filtry ortogonalne. Decyzja autonomous (opcja b user "Działaj dalej"): mobile używa identycznego filtra jak web → spójność UX operatora. Zmiana 5 lokalizacji `pg_storage.py` w 3 funkcjach mobile. Sanity prod: AMOZ 100=100 ✅, Dental 45 vs 46 (offset 1 edge case akceptowalny). Gotcha: initial `replace_all` złapał `recompute_discount_code_uses:6241/6264` (discount accounting, NIE check-in) — zrewertowane manualnie. Snapshot SKIPPED (continuity). Commit: backend `ceaf0c8`. Follow-up: WO-MOB-014 (pokrewne mobile.py). (Master + worker-implementer)
- **WO-MOB-012 DONE** — Fix mobile undercount check-in (Wątek A z BUG-MOB-002). Dental Practice Academy Poznań pokazywał 0 odznaczonych vs web 46. Root cause potwierdzony prod SELECT-ami (`backend/.env` DATABASE_URL): 46 uczestników miało `data.checked_in='true'` ale `status` ∈ `{emailed, pending, registered}` (NIE `checked_in`). Mobile aggregator (`get_participants_for_mobile`, `get_checkin_stats`, `get_mobile_dashboard` ×4 sub-query) filtrował tylko po `status='checked_in'`. Wyrównanie do kanonicznego predykatu OR-3 z `pg_storage.py:9963-9966` (web admin): `status='checked_in' OR data->>'checked_in' IN ('true','True') OR attendance_status IN ('checked_in','present')`. Pure backend fix — mobile DTO/Room/Kotlin bez zmian, APK niepotrzebny. Sanity check po fixie: Dental 46/46 ✅, AMOZ 66/66 (brak regresji). Snapshot SKIPPED (user explicit). Commits: backend `69cf404`, mobile docs `44971e8`, bump TBA. Follow-ups: WO-MOB-013 (Wątek B — różny filtr "łącznie"), WO-MOB-014 (pokrewne mobile.py:1176/1846/1921/1927). Bonus odkrycie: `checkin_log` niekompletny (Dental 17/46, AMOZ 50/66) — `known_gotchas.md`. (Master + worker-debugger + worker-implementer)
- **WO-MOB-011 DONE** — Bottom NavigationBar tab "Wydarzenie" (`AppNavHost.kt:317`): `maxLines = 1` → `maxLines = 2` żeby długie nazwy eventu zawijały się na 2 linie zamiast wcześnie urywać ellipsisem. Decyzja user: **opcja A** (3 linie zmiany) — świadoma rezygnacja z punktu 1 warunku bramkowego (stała pozycja icon). M3 `NavigationBarItem` ma fixed-height (~80dp) Column → przy 2-liniowym labelu icon `Icons.Default.Home` podnosi się ~6-8dp; opcja B (custom NavBarItem ~80-120 linii) odrzucona jako overkill. Pozostałe taby (Uczestnicy, Skaner) bez zmian. User weryfikuje wizualnie na buildzie. Snapshot: `snapshot/wo-mob-011-pre-navbar-label-wrap-2026-05-20` @ `4993c83`. (Master)
- **WO-MOB-010 DONE** — Fix UNDONE color (zielony→czerwony, `ScanError`) + usunięcie button'a "Cofnij wejście" z `ScanResultOverlay` SUCCESS branch. Cel: operator widzi semantycznie różnicę między WEJŚCIE OK (zielony) a COFNIĘTO (czerwony, exit-state); eliminacja przypadkowego undo w trakcie 3s auto-dismiss. Pliki: `ScannerScreen.kt` (+1/-15). Build PASS 36s. Commits: `b4d2d2f` mobile / `c21cfd8` monorepo bump. `ScannerViewModel.undoLastScan()` teraz orphaned (jedyny caller usunięty) — follow-up cleanup w przyszłym WO. (worker-implementer)
- **WO-MOB-009 DONE** — Fix scan result feedback overlay (zielony + czerwony) jako window-level `Dialog` zamiast in-tree `Box(fillMaxSize)`. Oba warianty teraz nakrywają cały ekran włącznie z `Scaffold(bottomBar)` z `AppNavHost`. `DialogProperties(usePlatformDefaultWidth=false, decorFitsSystemWindows=false, dismissOnBackPress=true, dismissOnClickOutside=false)`. Dodano `ScannerViewModel.dismissFeedback()` dla back-press handlingu. Ujednolicenie timing auto-dismiss DENIED 1500→3000ms (symetria z SUCCESS). Pliki: `ScannerScreen.kt` (+22/-9) + `ScannerViewModel.kt` (+11/-1). Build PASS 3m28s. Commits: `40dff85` mobile / `4368e91` monorepo bump. Gotcha → `known_gotchas.md`. (worker-implementer)

## 2026-05-19

- **WO-MOB-001 DONE** — Code-level QA auth flow po security WO-201/202/204: 6/6 scenariuszy PASS, PII guard PASS, brak regresji. APK gotowy do dystrybucji. (worker-research, ~140s)
- **APK debug rebuild** — mEventLab-debug.apk (58 MB) zbudowany z najnowszym commitem `f22e2bd` (PostHog analytics + custom events). Przekazać zespołowi.
- **AAB release** — mEventLab-release.aab (28 MB) zbudowany (WO-188), keystore `medidesk-release.jks` wygenerowany, dane w RELEASE_SIGNING.md (gitignored).

## 2026-05-18

- _(brak wpisów — PROGRESS.md utworzony przy bootstrap mobile workflow)_
