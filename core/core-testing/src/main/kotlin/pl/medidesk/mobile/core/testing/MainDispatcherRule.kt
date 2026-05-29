package pl.medidesk.mobile.core.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * JUnit rule that swaps [Dispatchers.Main] for a [TestDispatcher] for the duration of a test.
 *
 * WHY a rule (not manual setMain/resetMain in @Before/@After): guarantees `resetMain()` runs even
 * when a test throws, so a leaked test dispatcher can't bleed into the next test. Defaults to
 * [UnconfinedTestDispatcher] so coroutines launched on Main run eagerly without needing
 * `advanceUntilIdle()` — pass a [kotlinx.coroutines.test.StandardTestDispatcher] when a test needs
 * explicit virtual-time control.
 *
 * Faza 0 (WO-MOB-024): provided as test infra only — first consumers arrive in Faza 3.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
