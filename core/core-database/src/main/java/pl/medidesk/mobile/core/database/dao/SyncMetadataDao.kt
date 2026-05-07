package pl.medidesk.mobile.core.database.dao

import androidx.room.*
import pl.medidesk.mobile.core.database.entities.SyncMetadataEntity

@Dao
interface SyncMetadataDao {

    @Query("SELECT * FROM sync_metadata WHERE event_id = :eventId")
    suspend fun get(eventId: String): SyncMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metadata: SyncMetadataEntity)

    @Query("UPDATE sync_metadata SET last_participants_sync = :timestamp WHERE event_id = :eventId")
    suspend fun updateParticipantsSync(eventId: String, timestamp: String)

    @Query("UPDATE sync_metadata SET last_checkin_push = :timestamp WHERE event_id = :eventId")
    suspend fun updateCheckinPush(eventId: String, timestamp: String)
}
