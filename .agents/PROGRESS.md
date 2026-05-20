# PROGRESS — simple-event-checkin (Android Kotlin)

> Chronologiczny log "co robiłem" w obszarze **mobile** (Android natywny, `simple-event-checkin/`).
> Desktop → [.agents/PROGRESS.md](../../.agents/PROGRESS.md).
>
> Wpisy dodawane przez `/mlog <opis>` (subagent `progress-logger`, scope=mobile).
> Najnowsze na górze. Jeden bullet = jedna istotna rzecz (commit / decyzja / odkrycie / blocker).

---

## 2026-05-20

- **WO-MOB-010 DONE** — Fix UNDONE color (zielony→czerwony, `ScanError`) + usunięcie button'a "Cofnij wejście" z `ScanResultOverlay` SUCCESS branch. Cel: operator widzi semantycznie różnicę między WEJŚCIE OK (zielony) a COFNIĘTO (czerwony, exit-state); eliminacja przypadkowego undo w trakcie 3s auto-dismiss. Pliki: `ScannerScreen.kt` (+1/-15). Build PASS 36s. Commits: `b4d2d2f` mobile / `c21cfd8` monorepo bump. `ScannerViewModel.undoLastScan()` teraz orphaned (jedyny caller usunięty) — follow-up cleanup w przyszłym WO. (worker-implementer)
- **WO-MOB-009 DONE** — Fix scan result feedback overlay (zielony + czerwony) jako window-level `Dialog` zamiast in-tree `Box(fillMaxSize)`. Oba warianty teraz nakrywają cały ekran włącznie z `Scaffold(bottomBar)` z `AppNavHost`. `DialogProperties(usePlatformDefaultWidth=false, decorFitsSystemWindows=false, dismissOnBackPress=true, dismissOnClickOutside=false)`. Dodano `ScannerViewModel.dismissFeedback()` dla back-press handlingu. Ujednolicenie timing auto-dismiss DENIED 1500→3000ms (symetria z SUCCESS). Pliki: `ScannerScreen.kt` (+22/-9) + `ScannerViewModel.kt` (+11/-1). Build PASS 3m28s. Commits: `40dff85` mobile / `4368e91` monorepo bump. Gotcha → `known_gotchas.md`. (worker-implementer)

## 2026-05-19

- **WO-MOB-001 DONE** — Code-level QA auth flow po security WO-201/202/204: 6/6 scenariuszy PASS, PII guard PASS, brak regresji. APK gotowy do dystrybucji. (worker-research, ~140s)
- **APK debug rebuild** — mEventLab-debug.apk (58 MB) zbudowany z najnowszym commitem `f22e2bd` (PostHog analytics + custom events). Przekazać zespołowi.
- **AAB release** — mEventLab-release.aab (28 MB) zbudowany (WO-188), keystore `medidesk-release.jks` wygenerowany, dane w RELEASE_SIGNING.md (gitignored).

## 2026-05-18

- _(brak wpisów — PROGRESS.md utworzony przy bootstrap mobile workflow)_
