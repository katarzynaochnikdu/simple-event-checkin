package pl.medidesk.mobile.core.database.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import pl.medidesk.mobile.core.database.entities.WalkinEntity

@Dao
interface WalkinDao {

    @Query("SELECT * FROM walkin_participants WHERE event_id = :eventId ORDER BY created_at DESC")
    fun getWalkinsFlow(eventId: String): Flow<List<WalkinEntity>>

    @Query("SELECT * FROM walkin_participants WHERE sync_status = 'pending'")
    suspend fun getPending(): List<WalkinEntity>

    @Query("SELECT COUNT(*) FROM walkin_participants WHERE sync_status = 'pending'")
    fun getPendingCountFlow(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(walkin: WalkinEntity): Long

    @Query("UPDATE walkin_participants SET sync_status = 'synced' WHERE sync_status = 'pending'")
    suspend fun markAllSynced()

    @Query("SELECT * FROM walkin_participants WHERE event_id = :eventId ORDER BY created_at DESC")
    suspend fun getWalkins(eventId: String): List<WalkinEntity>
}
