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
