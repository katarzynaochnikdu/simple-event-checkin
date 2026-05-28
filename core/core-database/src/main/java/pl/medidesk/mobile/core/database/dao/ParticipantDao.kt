package pl.medidesk.mobile.core.database.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import pl.medidesk.mobile.core.database.entities.ParticipantEntity

@Dao
interface ParticipantDao {

    @Query("SELECT * FROM participants WHERE event_id = :eventId ORDER BY last_name, first_name")
    fun getParticipantsFlow(eventId: String): Flow<List<ParticipantEntity>>

    @Query("SELECT * FROM participants WHERE event_id = :eventId ORDER BY last_name, first_name")
    suspend fun getParticipants(eventId: String): List<ParticipantEntity>

    @Query("SELECT * FROM participants WHERE id = :participantId LIMIT 1")
    suspend fun getParticipantById(participantId: Long): ParticipantEntity?

    @Query("SELECT * FROM participants WHERE id = :participantId LIMIT 1")
    fun getParticipantByIdFlow(participantId: Long): Flow<ParticipantEntity?>

    @Query("SELECT * FROM participants WHERE backstage_ticket_id = :ticketId LIMIT 1")
    suspend fun findByTicketId(ticketId: String): ParticipantEntity?

    @Query("""
        SELECT * FROM participants
        WHERE ticket_number = :ticketId
           OR ticket_id = :ticketId
           OR backstage_ticket_id = :ticketId
        LIMIT 1
    """)
    suspend fun findByAnyTicketId(ticketId: String): ParticipantEntity?

    @Query("""
        SELECT * FROM participants
        WHERE event_id = :eventId
          AND (ticket_number = :ticketId OR ticket_id = :ticketId OR backstage_ticket_id = :ticketId)
        LIMIT 1
    """)
    suspend fun findByTicketAndEvent(ticketId: String, eventId: String): ParticipantEntity?

    @Query("SELECT COUNT(*) FROM participants WHERE event_id = :eventId")
    suspend fun countForEvent(eventId: String): Int
    
    @Query("SELECT COUNT(*) FROM participants WHERE event_id = :eventId AND checked_in_at IS NOT NULL")
    fun countCheckedInFlow(eventId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM participants WHERE event_id = :eventId")
    fun countTotalFlow(eventId: String): Flow<Int>

    @Query("SELECT * FROM participants WHERE event_id = :eventId AND checked_in_at IS NOT NULL ORDER BY checked_in_at DESC LIMIT 10")
    fun getRecentCheckinsFlow(eventId: String): Flow<List<ParticipantEntity>>

    // WO-MOB-020 (2026-05-28): full checked-in list (NO limit) for company ranking on StatsScreen.
    // Distinct from getRecentCheckinsFlow ("recent 10") so TOP FIRMY can aggregate the whole event.
    @Query("SELECT * FROM participants WHERE event_id = :eventId AND checked_in_at IS NOT NULL")
    fun getCheckedInParticipantsFlow(eventId: String): Flow<List<ParticipantEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(participants: List<ParticipantEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(participant: ParticipantEntity)

    @Query("DELETE FROM participants WHERE event_id = :eventId")
    suspend fun deleteAllForEvent(eventId: String)

    @Query("UPDATE participants SET checked_in_at = :checkedInAt, status = 'checked_in' WHERE ticket_number = :ticketId OR ticket_id = :ticketId OR backstage_ticket_id = :ticketId")
    suspend fun markCheckedIn(ticketId: String, checkedInAt: String)

    @Query("UPDATE participants SET checked_in_at = :checkedInAt, status = 'checked_in' WHERE id = :participantId")
    suspend fun markCheckedInById(participantId: Long, checkedInAt: String)
    
    @Query("UPDATE participants SET checked_in_at = NULL, status = 'rsvp_confirmed' WHERE ticket_number = :ticketId OR ticket_id = :ticketId OR backstage_ticket_id = :ticketId")
    suspend fun markCheckedOut(ticketId: String)

    @Query("UPDATE participants SET checked_in_at = NULL, status = 'rsvp_confirmed' WHERE id = :participantId")
    suspend fun markCheckedOutById(participantId: Long)

    @Transaction
    suspend fun replaceAll(eventId: String, participants: List<ParticipantEntity>) {
        deleteAllForEvent(eventId)
        insertAll(participants)
    }
}
