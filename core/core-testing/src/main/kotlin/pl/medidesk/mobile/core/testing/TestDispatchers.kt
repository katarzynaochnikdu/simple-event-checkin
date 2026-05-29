package pl.medidesk.mobile.core.testing

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * Thin factory helpers for coroutine test scopes/dispatchers, so test classes don't sprinkle
 * `@OptIn(ExperimentalCoroutinesApi)` everywhere and share one consistent construction style.
 *
 * Faza 0 (WO-MOB-024): infra only — expanded with project-specific defaults in Faza 3 as real
 * ViewModel/UseCase tests land.
 */
@OptIn(ExperimentalCoroutinesApi::class)
object TestDispatchers {

    /** Deterministic, virtual-time dispatcher — call `advanceUntilIdle()` to drain. */
    fun standard(): TestDispatcher = StandardTestDispatcher()

    /** Eager dispatcher — coroutines run immediately, no manual advancement needed. */
    fun unconfined(): TestDispatcher = UnconfinedTestDispatcher()

    /** A [TestScope] backed by [dispatcher] (defaults to standard/virtual-time). */
    fun scope(dispatcher: TestDispatcher = standard()): TestScope = TestScope(dispatcher)
}
