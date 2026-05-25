package pl.medidesk.mobile.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit Room migrations — one object per version step.
 *
 * Convention (WO-202):
 *  • Name: MIGRATION_<from>_<to>
 *  • Always use `IF NOT EXISTS` / `IF EXISTS` guards so migrations are re-runnable.
 *  • Never call `fallbackToDestructiveMigration` without also listing all migrations here.
 *
 * History:
 *  v1..v6 — pre-migration era; users on these versions will fall back to destructive reset
 *            (acceptable: fresh install required; versions never shipped to production store).
 *  v7 → v8 — added `ticket_number TEXT` column + index (WO-TKT-003).
 *  v8 → v9 — added `rsvp_sent INTEGER NOT NULL DEFAULT 0`, `rsvp_response TEXT`,
 *            `rsvp_responded_at TEXT` columns to participants (WO-MOB-003).
 *  v9 → v10 — added speaker_checkin_queue table for manual speaker check-in
 *             offline queue (WO-MOB-015 2026-05-25). New table, no data loss.
 */

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add ticket_number column (nullable TEXT — existing rows get NULL, filled on next sync)
        db.execSQL(
            "ALTER TABLE participants ADD COLUMN ticket_number TEXT"
        )
        // Recreate the index that Room expects for v8 schema
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_participants_ticket_number " +
            "ON participants (ticket_number)"
        )
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add 3 RSVP columns. Existing rows get default false/null;
        // next sync from backend (post WO-MOB-002) backfills real values.
        db.execSQL(
            "ALTER TABLE participants ADD COLUMN rsvp_sent INTEGER NOT NULL DEFAULT 0"
        )
        db.execSQL(
            "ALTER TABLE participants ADD COLUMN rsvp_response TEXT"
        )
        db.execSQL(
            "ALTER TABLE participants ADD COLUMN rsvp_responded_at TEXT"
        )
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // WO-MOB-015 (2026-05-25): manual speaker check-in offline queue.
        // New table only — existing data untouched.
        // Schema MUST mirror SpeakerCheckinEntity column order/types exactly so Room
        // schema validator passes on first launch.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS speaker_checkin_queue (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                speaker_id TEXT NOT NULL,
                event_id TEXT NOT NULL,
                scanned_at TEXT NOT NULL,
                device_id TEXT NOT NULL DEFAULT 'android',
                action TEXT NOT NULL DEFAULT 'check-in',
                synced INTEGER NOT NULL DEFAULT 0,
                retry_count INTEGER NOT NULL DEFAULT 0,
                next_retry_at TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_speaker_checkin_queue_synced ON speaker_checkin_queue (synced)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_speaker_checkin_queue_event_id ON speaker_checkin_queue (event_id)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_speaker_checkin_queue_speaker_id ON speaker_checkin_queue (speaker_id)"
        )
    }
}
