package pl.medidesk.mobile.core.testing.fakes

/**
 * Placeholder for hand-written fakes (WO-MOB-024, Faza 0).
 *
 * Faza 3: add in-memory fake implementations of repositories / data sources (preferred over
 * mockk for stateful collaborators, e.g. a `FakeParticipantsRepository` backed by a MutableList /
 * MutableStateFlow) so flow-emission and state transitions are observable via Turbine.
 */
internal object Fakes
