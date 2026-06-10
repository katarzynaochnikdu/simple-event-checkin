package pl.medidesk.mobile.core.sync

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.Runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pl.medidesk.mobile.core.analytics.Analytics
import pl.medidesk.mobile.core.database.MdDatabase
import pl.medidesk.mobile.core.database.dao.OfflineCheckinDao
import pl.medidesk.mobile.core.database.dao.SpeakerCheckinDao
import pl.medidesk.mobile.core.database.dao.WalkinDao
import pl.medidesk.mobile.core.database.entities.OfflineCheckinEntity
import pl.medidesk.mobile.core.datastore.AuthDataStore

/**
 * WO-MOB-028 (finding F2A-001): logout MUSI czyścić cały lokalny cache PII —
 * Room (`clearAllTables()`) ORAZ encrypted prefs (`AuthDataStore.clearAll()`).
 *
 * Czyste testy JVM (MockK) — MdDatabase/AuthDataStore/SyncEngine są mockami,
 * object Analytics zmockowany, więc żaden Android framework nie jest dotykany
 * (WorkManager schowany za SyncEngine.cancelImmediateSync — celowo, bo mockowanie
 * statycznego WorkManager.getInstance na czystym JVM kończy się AbstractMethodError).
 */
class LogoutUseCaseTest {

    private lateinit var database: MdDatabase
    private lateinit var authDataStore: AuthDataStore
    private lateinit var syncEngine: SyncEngine
    private lateinit var imageCacheCleaner: ImageCacheCleaner

    private lateinit var offlineCheckinDao: OfflineCheckinDao
    private lateinit var walkinDao: WalkinDao
    private lateinit var speakerCheckinDao: SpeakerCheckinDao

    private lateinit var logoutUseCase: LogoutUseCase

    @Before
    fun setUp() {
        database = mockk()
        authDataStore = mockk()
        syncEngine = mockk()
        imageCacheCleaner = mockk()

        offlineCheckinDao = mockk()
        walkinDao = mockk()
        speakerCheckinDao = mockk()

        // Analytics to object delegujący do statyków PostHog — stubujemy, żeby test
        // nie dotykał niezainicjalizowanego SDK.
        mockkObject(Analytics)
        every { Analytics.reset() } just Runs
        every { Analytics.optOut() } just Runs

        every { database.clearAllTables() } just Runs
        every { database.offlineCheckinDao() } returns offlineCheckinDao
        every { database.walkinDao() } returns walkinDao
        every { database.speakerCheckinDao() } returns speakerCheckinDao
        coEvery { offlineCheckinDao.getUnsynced() } returns emptyList()
        coEvery { walkinDao.getPending() } returns emptyList()
        coEvery { speakerCheckinDao.getUnsynced() } returns emptyList()

        coEvery { authDataStore.clearAll() } just Runs
        every { syncEngine.stopPeriodicSync() } just Runs
        every { syncEngine.cancelImmediateSync() } just Runs
        coEvery { syncEngine.runImmediateSyncAndWait(any()) } just Runs
        every { imageCacheCleaner.clear() } just Runs

        logoutUseCase = LogoutUseCase(database, authDataStore, syncEngine, imageCacheCleaner)
    }

    @After
    fun tearDown() {
        // Statyczne/obiektowe mocki NIE mogą wyciec do innych testów (izolacja testów).
        unmockkAll()
    }

    @Test
    fun test_logout_clears_room_cache_and_auth_datastore() = runTest {
        // Act
        logoutUseCase()

        // Assert — sync zatrzymany PRZED wipe'em, Room wyczyszczony przed prefs,
        // a po prefs reset+optOut analytics oraz czyszczenie cache'u obrazów (WO-MOB-033).
        coVerifyOrder {
            syncEngine.stopPeriodicSync()
            database.clearAllTables()
            authDataStore.clearAll()
            Analytics.reset()
            Analytics.optOut()
            imageCacheCleaner.clear()
        }
        verify(exactly = 1) { Analytics.reset() }
        verify(exactly = 1) { Analytics.optOut() }
        verify(exactly = 1) { imageCacheCleaner.clear() }
        verify(exactly = 1) { syncEngine.cancelImmediateSync() }
    }

    @Test
    fun test_manual_logout_flushes_pending_queues_before_room_wipe() = runTest {
        // Arrange — jedna niezsynchronizowana pozycja w kolejce offline check-inów.
        coEvery { offlineCheckinDao.getUnsynced() } returns listOf(
            OfflineCheckinEntity(eventId = "evt-1", scannedAt = "2026-06-10T10:00:00Z")
        )

        // Act
        logoutUseCase(flushPendingQueues = true)

        // Assert — best-effort sync dla eventu z pending wpisami wykonany PRZED wipe'em.
        coVerifyOrder {
            syncEngine.runImmediateSyncAndWait("evt-1")
            database.clearAllTables()
            authDataStore.clearAll()
        }
    }

    @Test
    fun test_session_expiry_logout_skips_best_effort_sync() = runTest {
        // Arrange — pending wpisy istnieją, ale flushPendingQueues=false (401: token martwy).
        coEvery { offlineCheckinDao.getUnsynced() } returns listOf(
            OfflineCheckinEntity(eventId = "evt-1", scannedAt = "2026-06-10T10:00:00Z")
        )

        // Act
        logoutUseCase(flushPendingQueues = false)

        // Assert — zero prób syncu, wipe wykonany.
        coVerify(exactly = 0) { syncEngine.runImmediateSyncAndWait(any()) }
        verify(exactly = 1) { database.clearAllTables() }
        coVerify(exactly = 1) { authDataStore.clearAll() }
    }

    @Test
    fun test_logout_clears_auth_datastore_even_when_room_wipe_throws() = runTest {
        // Arrange — symulacja awarii Room (np. uszkodzony plik DB).
        every { database.clearAllTables() } throws RuntimeException("simulated disk I/O failure")

        // Act
        val result = runCatching { logoutUseCase() }

        // Assert — wyjątek propaguje, ale token/dane usera, tożsamość analytics, opt-out
        // SDK i cache obrazów są wyczyszczone MIMO awarii wipe'u (blok finally w use casie).
        assertTrue(result.isFailure)
        coVerify(exactly = 1) { authDataStore.clearAll() }
        verify(exactly = 1) { Analytics.reset() }
        verify(exactly = 1) { Analytics.optOut() }
        verify(exactly = 1) { imageCacheCleaner.clear() }
    }

    @Test
    fun test_logout_finishes_cleanup_when_coroutine_cancelled_during_room_wipe() = runTest {
        // Arrange — symulujemy anulowanie coroutine W TRAKCIE wipe'u Room: clearAllTables()
        // anuluje Joba, pod którym leci logout. Bez NonCancellable w bloku finally
        // (finding N-1) suspendowy authDataStore.clearAll() rzuciłby CancellationException
        // na pierwszym suspension poincie i sekrety/analytics/cache zostałyby nietknięte.
        val job = Job()
        every { database.clearAllTables() } answers { job.cancel() }

        // Act — uruchamiamy logout pod anulowalnym Jobem i czekamy aż się zakończy.
        launch(job) { runCatching { logoutUseCase() } }.join()

        // Assert — mimo anulowania całe sprzątanie z bloku finally wykonane (NonCancellable):
        // token/dane usera, reset+optOut analytics oraz cache obrazów.
        coVerify(exactly = 1) { authDataStore.clearAll() }
        verify(exactly = 1) { Analytics.reset() }
        verify(exactly = 1) { Analytics.optOut() }
        verify(exactly = 1) { imageCacheCleaner.clear() }
    }

    @Test
    fun test_session_wipe_hook_delegates_to_logout_use_case_without_flush() = runTest {
        // Arrange — hook 401 z AuthInterceptora (core-network) deleguje do use case'u.
        val useCase = mockk<LogoutUseCase>()
        coEvery { useCase.invoke(flushPendingQueues = false) } just Runs
        val hook = LogoutSessionWipeHook(useCase)

        // Act
        hook.onSessionExpired()

        // Assert
        coVerify(exactly = 1) { useCase.invoke(flushPendingQueues = false) }
    }
}
