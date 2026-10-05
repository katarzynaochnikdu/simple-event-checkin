package pl.medidesk.mobile.core.database.dao

import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import pl.medidesk.mobile.core.database.MdDatabase
import pl.medidesk.mobile.core.database.entities.ParticipantEntity

@RunWith(RobolectricTestRunner::class)
class ParticipantDaoHistoryPreservationTest {
    private lateinit var database: MdDatabase
    private lateinit var participantDao: ParticipantDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            MdDatabase::class.java
        ).allowMainThreadQueries().build()
        participantDao = database.participantDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun test_insert_all_incremental_sync_preserves_historical_ticket_id() = runBlocking {
        // Arrange
        participantDao.insertAll(
            listOf(participant(id = 1, firstName = "Old", backstageTicketId = "legacy-1"))
        )

        // Act
        participantDao.insertAll(
            listOf(participant(id = 1, firstName = "Updated", backstageTicketId = null))
        )

        val incrementallyUpdated = participantDao.getParticipantById(1)!!
        assertEquals("Updated", incrementallyUpdated.firstName)
        assertEquals("legacy-1", incrementallyUpdated.backstageTicketId)
    }

    @Test
    fun test_replace_all_full_sync_preserves_existing_and_drops_missing() = runBlocking {
        // Arrange
        participantDao.insertAll(
            listOf(
                participant(id = 1, firstName = "Existing", backstageTicketId = "legacy-1"),
                participant(id = 2, firstName = "Removed", backstageTicketId = "legacy-2")
            )
        )

        // Act
        participantDao.replaceAll(
            eventId = EVENT_ID,
            participants = listOf(
                participant(id = 1, firstName = "Full refresh", backstageTicketId = null),
                participant(id = 3, firstName = "New", backstageTicketId = null)
            )
        )

        val fullyRefreshed = participantDao.getParticipantById(1)!!
        assertEquals("Full refresh", fullyRefreshed.firstName)
        assertEquals("legacy-1", fullyRefreshed.backstageTicketId)
        assertNull(participantDao.getParticipantById(3)!!.backstageTicketId)
        assertNull(participantDao.getParticipantById(2))
    }

    @Test
    fun test_replace_all_does_not_copy_history_across_events() = runBlocking {
        // Arrange
        participantDao.insertAll(
            listOf(participant(id = 1, firstName = "Original", backstageTicketId = "legacy-event-a"))
        )

        // Act
        participantDao.replaceAll(
            eventId = EVENT_ID,
            participants = listOf(
                participant(
                    id = 1,
                    firstName = "Moved event",
                    backstageTicketId = null,
                    eventId = OTHER_EVENT_ID
                )
            )
        )

        val replaced = participantDao.getParticipantById(1)!!
        assertEquals(OTHER_EVENT_ID, replaced.eventId)
        assertNull(replaced.backstageTicketId)
    }

    private fun participant(
        id: Long,
        firstName: String,
        backstageTicketId: String?,
        eventId: String = EVENT_ID
    ) = ParticipantEntity(
        id = id,
        ticketId = "ticket-$id",
        ticketNumber = null,
        backstageTicketId = backstageTicketId,
        firstName = firstName,
        lastName = "Test",
        email = null,
        phone = null,
        company = null,
        ticketClassId = null,
        ticketName = null,
        status = "confirmed",
        attendanceStatus = null,
        eventOrderId = null,
        eventId = eventId,
        checkedInAt = null
    )

    private companion object {
        const val EVENT_ID = "synthetic-event"
        const val OTHER_EVENT_ID = "synthetic-other-event"
    }
}
