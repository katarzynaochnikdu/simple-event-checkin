package pl.medidesk.mobile.core.sync

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import pl.medidesk.mobile.core.analytics.Analytics
import pl.medidesk.mobile.core.database.MdDatabase
import pl.medidesk.mobile.core.datastore.AuthDataStore
import pl.medidesk.mobile.core.network.SessionWipeHook
import javax.inject.Inject

/**
 * Wspólna ścieżka wylogowania — WO-MOB-028 (finding F2A-001, WYS).
 *
 * Przed tym WO logout czyścił wyłącznie [AuthDataStore] (EncryptedSharedPreferences),
 * a Room `md_checkin.db` (pełne profile uczestników, walk-iny, kolejki offline) zostawał
 * na urządzeniu bezterminowo. Skutki: cross-user PII bypass na wspólnym urządzeniu,
 * PII at-rest po wylogowaniu, misatrybucja audytu (pending queue operatora A wypchnięta
 * pod JWT operatora B).
 *
 * WHY core-sync (a nie sugerowany w WO "core-data", który NIE istnieje w grafie modułów):
 * core-sync jako jedyny moduł widzi wszystkie zależności use case'u (core-database,
 * core-datastore, core-analytics, core-network), a każdy z konsumentów (feature-auth,
 * feature-more, app) już od core-sync zależy — zero zmian w grafie zależności.
 *
 * Decyzja o pending queue (wariant MINIMAL z WO): przy logout czyścimy WSZYSTKO, w tym
 * niezsynchronizowane check-iny — misatrybucja audytu jest gorsza niż utrata wpisów.
 * Na ścieżce manualnego logout (Settings/More) próbujemy wcześniej JEDNEGO best-effort
 * flushu kolejek z twardym timeoutem [BEST_EFFORT_FLUSH_TIMEOUT_MS]; na 401/auth-fail
 * pomijamy flush (token martwy — sync i tak padnie).
 */
class LogoutUseCase @Inject constructor(
    private val database: MdDatabase,
    private val authDataStore: AuthDataStore,
    private val syncEngine: SyncEngine
) {

    /**
     * @param flushPendingQueues `true` TYLKO na ścieżce manualnego logout (Settings/More) —
     *   przed wipe'em próbuje zsynchronizować pending check-iny/walk-iny (best-effort, ≤5 s).
     *   `false` (default) dla 401 auto-logout i nieudanego auth-checku.
     */
    suspend operator fun invoke(flushPendingQueues: Boolean = false) {
        // 1. Najpierw zatrzymaj periodic sync — żeby SyncWorker nie repopulował bazy
        //    ani nie pchał kolejek równolegle z wipe'em.
        syncEngine.stopPeriodicSync()

        // 2. Best-effort flush pending queues (tylko manual logout).
        if (flushPendingQueues) {
            bestEffortFlushPendingQueues()
        }

        // 3. Skasuj też ewentualny zakolejkowany immediate sync (np. nasz flush, który nie
        //    zdążył ruszyć przed timeoutem w trybie offline). Bez tego straggler-worker
        //    odpaliłby się PO wipe bez tokenu → 401 → pętla notifySessionExpired (reset
        //    ekranu logowania pod palcami usera).
        syncEngine.cancelImmediateSync()

        try {
            // 4. Wipe całego cache'u PII w Room. clearAllTables() jest blokujące i nie może
            //    iść na main thread (call-site'y to viewModelScope na Main) — stąd IO.
            withContext(Dispatchers.IO) {
                database.clearAllTables()
            }
        } finally {
            // 5. Token i dane usera czyścimy ZAWSZE — nawet gdy wipe Room rzuci (uszkodzony
            //    plik DB itp.). Żywy token + brak wipe'u = najgorszy scenariusz F2A-001.
            authDataStore.clearAll()

            // 6. Reset tożsamości analytics (distinct id PostHog) — koniec sesji usera.
            Analytics.reset()
        }
    }

    /**
     * Jeden best-effort przebieg syncu dla każdego eventu z niezsynchronizowanymi wpisami,
     * całość pod wspólnym timeoutem. Korzysta z istniejącego awaitable
     * [SyncEngine.runImmediateSyncAndWait]; eventId wyprowadzamy z samych kolejek, bo
     * call-site'y logout (Settings/More) nie znają "bieżącego" eventu, a pending wpisy
     * mogą pochodzić z wielu eventów.
     */
    private suspend fun bestEffortFlushPendingQueues() {
        try {
            withTimeoutOrNull(BEST_EFFORT_FLUSH_TIMEOUT_MS) {
                val pendingEventIds = buildSet {
                    database.offlineCheckinDao().getUnsynced().forEach { add(it.eventId) }
                    database.walkinDao().getPending().forEach { add(it.eventId) }
                    database.speakerCheckinDao().getUnsynced().forEach { add(it.eventId) }
                }
                pendingEventIds.forEach { eventId ->
                    syncEngine.runImmediateSyncAndWait(eventId)
                }
            }
        } catch (ce: CancellationException) {
            throw ce // nie połykamy anulowania rodzica (anty-wzorzec runCatching)
        } catch (e: Exception) {
            // Best-effort: błąd syncu NIE blokuje wylogowania — wipe jest ważniejszy.
            Log.w(TAG, "Best-effort flush pending queues failed — proceeding with wipe", e)
        }
    }

    companion object {
        private const val TAG = "LogoutUseCase"

        /** Twardy limit na best-effort flush — logout nie może wisieć na słabym łączu. */
        const val BEST_EFFORT_FLUSH_TIMEOUT_MS = 5_000L
    }
}

/**
 * Adapter [SessionWipeHook] → [LogoutUseCase] dla 401 auto-logout w AuthInterceptorze
 * (core-network nie może zależeć od core-sync — patrz docstring SessionWipeHook).
 * Bez flushu kolejek: token jest już martwy, sync by tylko opóźnił wipe.
 */
class LogoutSessionWipeHook @Inject constructor(
    private val logoutUseCase: LogoutUseCase
) : SessionWipeHook {
    override suspend fun onSessionExpired() {
        logoutUseCase(flushPendingQueues = false)
    }
}
