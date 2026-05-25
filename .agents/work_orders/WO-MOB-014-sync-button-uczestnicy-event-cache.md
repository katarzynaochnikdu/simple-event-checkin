# WO-MOB-014: Sync button — odswiezanie listy uczestnikow w cache wydarzenia (manual refresh)

**Data:** 2026-05-25
**Worker:** Master + worker-research + worker-implementer
**Stage:** mobile-checkin
**Priorytet:** Normalny
**Scope:** mobile (simple-event-checkin)
**Status:** ✅ DONE 2026-05-25 — build PASS (compileDebugKotlin 3m49s + assembleDebug 2m02s), czeka na user smoke test + commit

## Cel

Pytanie użytkownika dotyczy opóźnienia widoczności nowo zarejestrowanych uczestników w aplikacji mobilnej oraz propozycja dodania przycisku ręcznej synchronizacji bazy uczestników dla wybranego eventu. Zadanie obejmuje: (1) udokumentowanie / wyjaśnienie obecnego cyklu synchronizacji cache (interwał, trigger, TTL) oraz (2) zaprojektowanie i implementację przycisku "Synchronizuj uczestników" w UI wybranego wydarzenia, który wymusza refetch listy z backendu (`GET /api/mobile/events/:id/participants`) i nadpisuje lokalną bazę Room z confirmation snackbar / loadingiem.

## Audyt obecnego cyklu sync (research read-only, 2026-05-25)

**Odpowiedź na pytanie usera "Ile czasu czeka się?":**
- **0 sekund** jeśli wyjdziesz z ekranu uczestników i wrócisz — `LifecycleResumeEffect` w `ParticipantsScreen.kt:60-64` triggeruje `silentSync` przy każdym `onResume`.
- **0 sekund** jeśli zrobisz pull-to-refresh — gest już DZIAŁA (`PullToRefreshBox` w `ParticipantsScreen.kt:190-194` wzywa `viewModel.refresh(eventId)` → `runImmediateSyncAndWait` w `SyncEngine.kt:86-96`).
- **Nigdy (do końca świata)** jeśli trzymasz ekran "Uczestnicy" otwarty bez interakcji i ktoś rejestruje się przez panel. **Brak auto-pollingu** — `startPeriodicSync` (5 min) w `SyncEngine.kt:65-71` jest zdefiniowany ale **NIGDY nie wywoływany w prod kodzie** (martwy kod, jedyny consumer to `MoreViewModel.stopPeriodicSync()` przy logout).
- **Brak TTL** — każdy sync wymusza `forceFullPull=true` → `since=null` → `replaceAll()` (`SyncWorker.kt:129,143-144`), więc cache zawsze świeży po każdym sync trigger.

**Wniosek:** Manual button w `TopAppBar` jest sensowny — daje **explicit affordance** dla usera trzymającego ekran otwarty (pull-to-refresh nie jest oczywisty wizualnie). Reuse istniejącego `refresh()` w VM — zero nowej logiki sync.

## Zakres

| Plik | Ścieżka | Rola | Linie |
|---|---|---|---|
| `ParticipantsScreen.kt` | `simple-event-checkin/features/feature-participants/src/main/java/pl/medidesk/mobile/feature/participants/presentation/screen/` | Przycisk Sync (IconButton `Refresh`) w `TopAppBar` actions + `SnackbarHost` w Scaffold + observer dla `viewModel.events` SharedFlow | 100-132 (TopAppBar), 36-46 (sygnatura), Scaffold body |
| `ParticipantsViewModel.kt` | `simple-event-checkin/features/feature-participants/src/main/java/pl/medidesk/mobile/feature/participants/presentation/viewmodel/` | Dodać `_events: MutableSharedFlow<SyncResultEvent>` (sealed class Success/Error), owinąć `refresh()` w try/catch, emit po `runImmediateSyncAndWait` zwraca | 114-130 (refresh), nowy state |

**Brak `ParticipantsRepository`** — wzorzec aplikacji to **bezpośredni DAO + SyncEngine + Worker**, nie repository pattern. NIE wymyślaj nowej warstwy.

## Czego NIE ruszać 🛑

- Backend endpointy mobile (`backend/api/mobile.py`) — nie wymagamy zmian, używamy istniejącego `GET /api/mobile/events/:id/participants`.
- Logikę check-in (`POST /checkin`, kolejkę offline) — sync uczestników to osobny przepływ niż sync check-inów.
- `SyncEngine.startPeriodicSync` (`SyncEngine.kt:65-71`) — martwy kod, ale OUT OF SCOPE. NIE aktywować polling, NIE usuwać (osobny WO potem jeśli decyzja).
- `LifecycleResumeEffect` w `ParticipantsScreen.kt:60-64` — obecny silent auto-sync zostaje bez zmian (nadal wywołuje `silentSync`).
- `PullToRefreshBox` (`ParticipantsScreen.kt:190-194`) — gest pull działa, NIE zmieniaj logiki, ale **obie ścieżki (pull + button) emitują Snackbar przez ten sam mechanizm**.
- `SyncWorker.kt` / `SyncEngine.kt:86-96` — workflow `runImmediateSyncAndWait` zostaje bez zmian. VM tylko owija jego rezultat w SharedFlow.

## Pliki startowe

- `simple-event-checkin/features/feature-participants/src/main/java/pl/medidesk/mobile/feature/participants/presentation/screen/ParticipantsScreen.kt` — TopAppBar actions + SnackbarHost
- `simple-event-checkin/features/feature-participants/src/main/java/pl/medidesk/mobile/feature/participants/presentation/viewmodel/ParticipantsViewModel.kt` — SharedFlow events + refresh() owrap
- `simple-event-checkin/core/core-sync/src/main/java/pl/medidesk/mobile/core/sync/SyncEngine.kt` (read-only reference) — `runImmediateSyncAndWait`
- Referencje wzorca Snackbar: `SettingsScreen.kt:33,54`, `OrdersScreen.kt:152,168`, `ParticipantDetailsScreen.kt:72,175`

## Ryzyko

[do uzupełnienia]
- Wielokrotne tapnięcie przycisku → duplicate requesty (mitygacja: debounce / disable button podczas trwania sync)
- Duża lista uczestników (>1000) → długi czas sync, ryzyko timeout (mitygacja: spinner + cancel option)
- Konflikt z queue offline check-inów w trakcie sync (mitygacja: sync uczestników nie czyści queue check-inów oczekujących na flush)

## Definition of Done ✅

- [ ] Przycisk "Synchronizuj uczestników" widoczny w UI wybranego wydarzenia (np. w toolbar / pull-to-refresh / dedykowany button)
- [ ] Tapnięcie wywołuje `GET /api/mobile/events/:id/participants` i nadpisuje lokalną tabelę Room
- [ ] Loading state widoczny (spinner / progress bar / disable button)
- [ ] Success toast / snackbar po zakończeniu (np. "Zsynchronizowano N uczestników")
- [ ] Error handling — brak sieci / 401 / 500 wyświetla komunikat
- [ ] Build APK przechodzi (`./gradlew assembleDebug` lub `eas build` jeśli relevant)
- [ ] Dokumentacja w `simple-event-checkin/README.md` lub MOBILE_API.md — opis cyklu sync (auto + manual)
- [ ] Wpis do `simple-event-checkin/.agents/PROGRESS.md`

## Test akceptacyjny 🧪

[do uzupełnienia — przykładowy scenariusz]
1. Zarejestruj nowego uczestnika przez Purchase Cart / Zoho Backstage (np. test@example.com).
2. W aplikacji mobilnej otwórz wybrane wydarzenie — nowy uczestnik **NIE jest widoczny** (cache nieaktualny).
3. Tapnij przycisk "Synchronizuj uczestników" — pokazuje się loading.
4. Po zakończeniu: snackbar "Zsynchronizowano N uczestników", lista uczestników odświeżona, nowy uczestnik **widoczny**.
5. Sprawdź offline: wyłącz sieć → tap sync → komunikat błędu "Brak połączenia", lista pozostaje bez zmian.

## Oczekiwany efekt wizualny 🖼️

- **Lokalizacja:** `TopAppBar` ekranu uczestników, `actions = { IconButton(onClick = refresh, enabled = !uiState.isRefreshing) { Icon(Icons.Default.Refresh) } }` — obok istniejącego badge'a `uiState.filteredParticipants.size` (NIE w FAB, bo FAB "Dodaj uczestnika" już zajmuje to miejsce).
- **Loading state:** przycisk disabled (`enabled = !uiState.isRefreshing`) + spinner w slot ikony (`CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)` zamiast `Icon`). Pull-to-refresh nadal pokazuje swój spinner via Material3.
- **Success:** Snackbar "Lista zaktualizowana" (krótko, bez liczby — bo `replaceAll` zwraca tę samą count często co przed, co myli usera). Tylko dla **explicit user action** (button + pull) — silent auto-sync (LifecycleResumeEffect) NIE pokazuje snackbara (anti-spam).
- **Error:** Snackbar "Nie udało się zsynchronizować. Sprawdź połączenie." z action button `Ponów` (`SnackbarResult.ActionPerformed` → ponowne wywołanie `refresh(eventId)`).
- **Strings inline po polsku** — `simple-event-checkin/app/src/main/res/values/strings.xml` zawiera TYLKO `app_name`, cała appka ma hardcoded polskie literały (spójne ze stylem ParticipantsScreen.kt:69,73,77).

## Kontrakt API (jeśli zmiana full-stack) 🔗

- **Brak nowych endpointów** — `runImmediateSyncAndWait` (`SyncEngine.kt:86-96`) wykorzystuje istniejący `GET /api/mobile/events/<id>/participants` via `SyncWorker`. Backend bez zmian.
- **Brak nowych typów API** — `SyncResultEvent` to lokalny sealed class w VM (`sealed class SyncResultEvent { object Success; data class Error(val message: String?) }`), NIE wystawiany przez network.

## Decyzje usera (2026-05-25, master loop)

1. **Lokalizacja:** `TopAppBar` action icon (`Icons.Default.Refresh`) — minimalna powierzchnia, spójne z Material guidelines.
2. **Komunikat sukcesu:** "Zsynchronizowano (N uczestników)" — z liczbą total dla danego eventu (z `participantDao.countForEvent(eventId)` po sync).
3. **Error handling:** Snackbar z action button "Ponów" — `SnackbarResult.ActionPerformed` → ponowne wywołanie `refresh(eventId)`.
4. **Auto-sync UX:** Silent — `LifecycleResumeEffect` zostaje bez snackbara. Snackbar TYLKO dla explicit user action (button + pull-to-refresh).

## Pytanie do wyjaśnienia (kontekst od użytkownika)

> "Ile czasu trzeba czekać żeby osoba zarejstorwana przez apliakcje była w niej widoczna?"

Wymaga audytu obecnej logiki sync w `ParticipantsRepository`:
- Czy jest auto-refresh przy każdym wejściu na ekran wydarzenia?
- Czy jest TTL na cache (np. 5 min, 1h)?
- Czy synchronizacja jest tylko ręczna (login → fetch raz)?

Wynik audytu → konkretna odpowiedź dla użytkownika + decyzja, czy oprócz manual sync button warto też dorzucić auto-refresh on screen resume lub pull-to-refresh.

## Format zwrotki

[do uzupełnienia]
- Lista zmienionych plików z opisem zmian
- Git diff summary
- Screenshot z nowym przyciskiem sync + flow (przed/po)
- Odpowiedź na pytanie użytkownika o obecny cykl sync (z linkami do kodu)
- Wynik build (`./gradlew assembleDebug`)
- Propozycja wpisu do `decision_log.md` (jeśli decyzja: auto-refresh on resume vs tylko manual)
