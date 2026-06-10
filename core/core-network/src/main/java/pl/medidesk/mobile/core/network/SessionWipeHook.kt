package pl.medidesk.mobile.core.network

/**
 * Port czyszczenia lokalnych danych przy wygaśnięciu sesji (WO-MOB-028, finding F2A-001).
 *
 * WHY interfejs tutaj, a implementacja gdzie indziej: [AuthInterceptor] na 401 MUSI wyczyścić
 * cały lokalny cache PII (Room `md_checkin.db`), ale core-network nie może zależeć od
 * core-database/core-sync — core-sync już zależy od core-network, więc powstałby cykl modułów.
 * Implementacja ([pl.medidesk.mobile.core.sync.LogoutSessionWipeHook] → LogoutUseCase) żyje
 * w core-sync i jest bindowana przez Hilt (@Binds w LogoutModule).
 *
 * WHY nie SessionManager.sessionExpired: to SharedFlow z replay=0 — 401 złapany podczas
 * background sync (SyncWorker bez żywego UI) nie miałby kolektora i wipe by się nie wykonał.
 * Hook wywoływany jest synchronicznie w interceptorze, więc działa też bez UI.
 */
interface SessionWipeHook {

    /** Czyści lokalne dane (Room + prefs) po utracie sesji. Implementacja musi być idempotentna. */
    suspend fun onSessionExpired()
}
