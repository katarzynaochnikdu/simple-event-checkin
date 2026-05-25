package pl.medidesk.mobile.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pl.medidesk.mobile.core.database.entities.SpeakerCheckinEntity

/**
 * DAO dla offline queue speaker check-inow (WO-MOB-015).
 *
 * Wzorzec analogiczny do `OfflineCheckinDao` (uczestnicy):
 *   - `getUnsynced()` — pobierz pending entries do batch push
 *   - `markAllSynced...` — ustaw synced=1 po udanym pushu
 *   - `incrementRetry` — exponential backoff przez nextRetryAt timestamp
 *   - `deleteSynced` — cleanup
 *   - `getUnsyncedCountFlow` — UI reactive licznik (sync badge)
 *   - `getLatestActionForSpeaker` — local-first idempotency dla optimistic UI
 *     (przed wyslaniem do API mozna sprawdzic czy w queue jest juz CI/CO dla tego speakera).
 */
@Dao
interface SpeakerCheckinDao {

    @Query("SELECT * FROM speaker_checkin_queue WHERE synced = 0 ORDER BY scanned_at ASC")
    suspend fun getUnsynced(): List<SpeakerCheckinEntity>

    @Query("SELECT COUNT(*) FROM speaker_checkin_queue WHERE synced = 0")
    suspend fun getUnsyncedCount(): Int

    @Query("SELECT COUNT(*) FROM speaker_checkin_queue WHERE synced = 0")
    fun getUnsyncedCountFlow(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SpeakerCheckinEntity): Long

    @Query("UPDATE speaker_checkin_queue SET synced = 1 WHERE synced = 0 AND event_id = :eventId")
    suspend fun markAllSyncedForEvent(eventId: String)

    @Query("UPDATE speaker_checkin_queue SET synced = 1")
    suspend fun markAllSynced()

    @Query("UPDATE speaker_checkin_queue SET retry_count = retry_count + 1, next_retry_at = :nextRetryAt WHERE event_id = :eventId AND speaker_id = :speakerId AND synced = 0")
    suspend fun incrementRetry(eventId: String, speakerId: String, nextRetryAt: String)

    @Query("DELETE FROM speaker_checkin_queue WHERE synced = 1")
    suspend fun deleteSynced()

    /**
     * Najnowsza akcja zakolejkowana lokalnie dla (event_id, speaker_id) — uzywane do
     * local-first idempotency przed wyslaniem (np. unika podwojnego INSERTu gdy user
     * sprezy klika checkbox kilka razy z rzedu). Zwraca null jezeli kolejka nie zna
     * tego speakera.
     */
    @Query("SELECT action FROM speaker_checkin_queue WHERE event_id = :eventId AND speaker_id = :speakerId ORDER BY scanned_at DESC, id DESC LIMIT 1")
    suspend fun getLatestActionForSpeaker(eventId: String, speakerId: String): String?
}
