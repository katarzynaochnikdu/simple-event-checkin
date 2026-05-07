package pl.medidesk.mobile.core.database.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import pl.medidesk.mobile.core.database.entities.TicketClassEntity

@Dao
interface TicketClassDao {

    @Query("SELECT * FROM ticket_classes WHERE event_id = :eventId")
    suspend fun getForEvent(eventId: String): List<TicketClassEntity>
    
    @Query("SELECT * FROM ticket_classes WHERE event_id = :eventId")
    fun getTicketClassesFlow(eventId: String): Flow<List<TicketClassEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(classes: List<TicketClassEntity>)

    @Query("DELETE FROM ticket_classes WHERE event_id = :eventId")
    suspend fun deleteForEvent(eventId: String)
}
