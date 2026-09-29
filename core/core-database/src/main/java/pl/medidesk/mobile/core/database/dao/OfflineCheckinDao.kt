package pl.medidesk.mobile.core.database.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import pl.medidesk.mobile.core.database.entities.OfflineCheckinEntity

@Dao
interface OfflineCheckinDao {

    @Query("SELECT * FROM offline_checkins WHERE synced = 0 AND ticket_id IS NOT NULL AND ticket_id != '' ORDER BY scanned_at ASC")
    suspend fun getUnsynced(): List<OfflineCheckinEntity>

    @Query("SELECT COUNT(*) FROM offline_checkins WHERE synced = 0 AND ticket_id IS NOT NULL AND ticket_id != ''")
    fun getUnsyncedCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM offline_checkins WHERE synced = 0 AND ticket_id IS NOT NULL AND ticket_id != ''")
    suspend fun getUnsyncedCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(checkin: OfflineCheckinEntity): Long

    @Query("UPDATE offline_checkins SET synced = 1 WHERE synced = 0 AND event_id = :eventId AND ticket_id IS NOT NULL AND ticket_id != ''")
    suspend fun markAllSyncedForEvent(eventId: String)

    @Query("UPDATE offline_checkins SET synced = 1 WHERE ticket_id IS NOT NULL AND ticket_id != ''")
    suspend fun markAllSynced()

    @Query("UPDATE offline_checkins SET retry_count = retry_count + 1, next_retry_at = :nextRetryAt WHERE ticket_id = :ticketId AND synced = 0")
    suspend fun incrementRetry(ticketId: String, nextRetryAt: String)

    @Query("DELETE FROM offline_checkins WHERE synced = 1 AND ticket_id IS NOT NULL AND ticket_id != ''")
    suspend fun deleteSynced()

    @Query("DELETE FROM offline_checkins WHERE ticket_id = :ticketId AND synced = 0 AND action = 'checkin'")
    suspend fun deleteUnsyncedCheckin(ticketId: String): Int
}
