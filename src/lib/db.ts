import * as SQLite from "expo-sqlite";
import type { Participant, OfflineCheckin, WalkinParticipant, TicketClass } from "../types";

let _db: SQLite.SQLiteDatabase | null = null;

export async function getDb(): Promise<SQLite.SQLiteDatabase> {
  if (!_db) {
    _db = await SQLite.openDatabaseAsync("checkin.db");

    await _db.execAsync(`
      PRAGMA journal_mode=WAL;

      CREATE TABLE IF NOT EXISTS participants (
        id INTEGER PRIMARY KEY,
        backstage_ticket_id TEXT NOT NULL,
        first_name TEXT,
        last_name TEXT,
        email TEXT,
        company TEXT,
        ticket_class_id TEXT,
        ticket_name TEXT,
        status TEXT,
        attendance_status TEXT,
        event_order_id TEXT,
        event_id TEXT NOT NULL,
        checked_in_at TEXT
      );
      CREATE INDEX IF NOT EXISTS idx_p_ticket ON participants(backstage_ticket_id);
      CREATE INDEX IF NOT EXISTS idx_p_event ON participants(event_id);

      CREATE TABLE IF NOT EXISTS offline_checkins (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        backstage_ticket_id TEXT NOT NULL,
        event_id TEXT NOT NULL,
        scanned_at TEXT NOT NULL,
        device_id TEXT,
        synced INTEGER NOT NULL DEFAULT 0
      );
      CREATE INDEX IF NOT EXISTS idx_oc_synced ON offline_checkins(synced);

      CREATE TABLE IF NOT EXISTS sync_metadata (
        event_id TEXT PRIMARY KEY,
        last_participants_sync TEXT,
        last_checkin_push TEXT
      );

      CREATE TABLE IF NOT EXISTS walkin_participants (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        walk_in_code TEXT NOT NULL UNIQUE,
        event_id TEXT NOT NULL,
        first_name TEXT NOT NULL,
        last_name TEXT NOT NULL,
        email TEXT,
        phone TEXT,
        company TEXT,
        ticket_class_id TEXT,
        ticket_name TEXT,
        notes TEXT,
        checked_in_at TEXT,
        status TEXT NOT NULL DEFAULT 'registered',
        created_at TEXT NOT NULL,
        sync_status TEXT NOT NULL DEFAULT 'pending'
      );
      CREATE INDEX IF NOT EXISTS idx_walkin_event ON walkin_participants(event_id);

      CREATE TABLE IF NOT EXISTS ticket_classes (
        ticket_class_id TEXT NOT NULL,
        event_id TEXT NOT NULL,
        ticket_name TEXT NOT NULL,
        PRIMARY KEY (ticket_class_id, event_id)
      );
    `);

    // Idempotent column additions for offline_checkins
    for (const stmt of [
      "ALTER TABLE offline_checkins ADD COLUMN retry_count INTEGER NOT NULL DEFAULT 0",
      "ALTER TABLE offline_checkins ADD COLUMN next_retry_at TEXT",
      "ALTER TABLE offline_checkins ADD COLUMN action TEXT NOT NULL DEFAULT 'checkin'",
    ]) {
      try {
        await _db.execAsync(stmt);
      } catch {
        // column already exists — safe to ignore
      }
    }
  }
  return _db;
}

// ---------------------------------------------------------------------------
// Participants cache
// ---------------------------------------------------------------------------

export async function cacheParticipants(
  eventId: string,
  participants: Participant[]
): Promise<void> {
  const db = await getDb();
  await db.runAsync("DELETE FROM participants WHERE event_id = ?", eventId);
  for (const p of participants) {
    await db.runAsync(
      `INSERT OR REPLACE INTO participants
        (id, backstage_ticket_id, first_name, last_name, email, company,
         ticket_class_id, ticket_name, status, attendance_status, event_order_id,
         event_id, checked_in_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      p.id,
      p.backstage_ticket_id,
      p.first_name,
      p.last_name,
      p.email,
      p.company || "",
      p.ticket_class_id || "",
      p.ticket_name || "",
      p.status,
      p.attendance_status,
      p.event_order_id,
      eventId,
      p.checked_in_at
    );
  }
}

/**
 * Merge-upsert participants from incremental sync.
 * Existing rows are updated; rows absent from the delta are NOT deleted.
 */
export async function upsertParticipants(
  eventId: string,
  participants: Participant[]
): Promise<void> {
  const db = await getDb();
  for (const p of participants) {
    await db.runAsync(
      `INSERT OR REPLACE INTO participants
        (id, backstage_ticket_id, first_name, last_name, email, company,
         ticket_class_id, ticket_name, status, attendance_status, event_order_id,
         event_id, checked_in_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      p.id,
      p.backstage_ticket_id,
      p.first_name,
      p.last_name,
      p.email,
      p.company || "",
      p.ticket_class_id || "",
      p.ticket_name || "",
      p.status,
      p.attendance_status,
      p.event_order_id,
      eventId,
      p.checked_in_at
    );
  }
}

export async function findParticipantByTicketId(
  backstageTicketId: string,
  eventId: string
): Promise<Participant | null> {
  const db = await getDb();
  const row = await db.getFirstAsync<Participant>(
    "SELECT * FROM participants WHERE backstage_ticket_id = ? AND event_id = ?",
    backstageTicketId,
    eventId
  );
  return row || null;
}

export async function markLocalCheckin(
  backstageTicketId: string,
  eventId: string
): Promise<void> {
  const db = await getDb();
  const now = new Date().toISOString();
  await db.runAsync(
    "UPDATE participants SET status = 'checked_in', checked_in_at = ? WHERE backstage_ticket_id = ? AND event_id = ?",
    now,
    backstageTicketId,
    eventId
  );
}


export async function getLocalParticipants(
  eventId: string
): Promise<Participant[]> {
  const db = await getDb();
  return db.getAllAsync<Participant>(
    "SELECT * FROM participants WHERE event_id = ? ORDER BY last_name, first_name",
    eventId
  );
}

export async function getLocalStats(
  eventId: string
): Promise<{ total: number; checkedIn: number }> {
  const db = await getDb();
  const total = await db.getFirstAsync<{ cnt: number }>(
    "SELECT COUNT(*) as cnt FROM participants WHERE event_id = ?",
    eventId
  );
  const checkedIn = await db.getFirstAsync<{ cnt: number }>(
    "SELECT COUNT(*) as cnt FROM participants WHERE event_id = ? AND status = 'checked_in'",
    eventId
  );
  return {
    total: total?.cnt || 0,
    checkedIn: checkedIn?.cnt || 0,
  };
}

// ---------------------------------------------------------------------------
// Offline check-in queue
// ---------------------------------------------------------------------------

export async function addOfflineCheckin(
  backstageTicketId: string,
  eventId: string,
  deviceId: string,
  action: string = "checkin"
): Promise<void> {
  const db = await getDb();
  await db.runAsync(
    "INSERT INTO offline_checkins (backstage_ticket_id, event_id, scanned_at, device_id, synced, action) VALUES (?, ?, ?, ?, 0, ?)",
    backstageTicketId,
    eventId,
    new Date().toISOString(),
    deviceId,
    action
  );
}

export async function getUnsyncedCheckins(): Promise<OfflineCheckin[]> {
  const db = await getDb();
  const rows = await db.getAllAsync<OfflineCheckin>(
    `SELECT backstage_ticket_id, event_id, scanned_at, device_id, action
     FROM offline_checkins
     WHERE synced = 0
       AND (next_retry_at IS NULL OR next_retry_at <= ?)`,
    new Date().toISOString()
  );
  return rows;
}

export async function markCheckinsSynced(): Promise<void> {
  const db = await getDb();
  await db.runAsync("UPDATE offline_checkins SET synced = 1 WHERE synced = 0");
}

export async function incrementRetry(
  backstageTicketId: string,
  eventId: string
): Promise<void> {
  const db = await getDb();
  const row = await db.getFirstAsync<{ retry_count: number }>(
    "SELECT retry_count FROM offline_checkins WHERE backstage_ticket_id = ? AND event_id = ? AND synced = 0",
    backstageTicketId,
    eventId
  );
  if (!row) return;
  const nextCount = (row.retry_count || 0) + 1;
  const delayMinutes = Math.min(Math.pow(2, nextCount), 60);
  const nextRetryAt = new Date(Date.now() + delayMinutes * 60 * 1000).toISOString();
  await db.runAsync(
    "UPDATE offline_checkins SET retry_count = ?, next_retry_at = ? WHERE backstage_ticket_id = ? AND event_id = ? AND synced = 0",
    nextCount,
    nextRetryAt,
    backstageTicketId,
    eventId
  );
}

// ---------------------------------------------------------------------------
// Sync metadata
// ---------------------------------------------------------------------------

export async function getSyncMetadata(
  eventId: string
): Promise<{ last_participants_sync: string | null; last_checkin_push: string | null }> {
  const db = await getDb();
  const row = await db.getFirstAsync<{
    last_participants_sync: string | null;
    last_checkin_push: string | null;
  }>(
    "SELECT last_participants_sync, last_checkin_push FROM sync_metadata WHERE event_id = ?",
    eventId
  );
  return row || { last_participants_sync: null, last_checkin_push: null };
}

export async function setSyncMetadata(
  eventId: string,
  field: "last_participants_sync" | "last_checkin_push",
  value: string
): Promise<void> {
  const db = await getDb();
  // Use a full upsert to safely handle both insert and update
  if (field === "last_participants_sync") {
    await db.runAsync(
      `INSERT INTO sync_metadata (event_id, last_participants_sync, last_checkin_push)
       VALUES (?, ?, NULL)
       ON CONFLICT(event_id) DO UPDATE SET last_participants_sync = excluded.last_participants_sync`,
      eventId,
      value
    );
  } else {
    await db.runAsync(
      `INSERT INTO sync_metadata (event_id, last_participants_sync, last_checkin_push)
       VALUES (?, NULL, ?)
       ON CONFLICT(event_id) DO UPDATE SET last_checkin_push = excluded.last_checkin_push`,
      eventId,
      value
    );
  }
}

// ---------------------------------------------------------------------------
// Ticket classes cache
// ---------------------------------------------------------------------------

export async function upsertTicketClasses(
  eventId: string,
  classes: TicketClass[]
): Promise<void> {
  const db = await getDb();
  for (const tc of classes) {
    await db.runAsync(
      `INSERT OR REPLACE INTO ticket_classes (ticket_class_id, event_id, ticket_name)
       VALUES (?, ?, ?)`,
      tc.ticket_class_id,
      eventId,
      tc.ticket_name
    );
  }
}

export async function getTicketClasses(eventId: string): Promise<TicketClass[]> {
  const db = await getDb();
  return db.getAllAsync<TicketClass>(
    "SELECT ticket_class_id, event_id, ticket_name FROM ticket_classes WHERE event_id = ? ORDER BY ticket_name",
    eventId
  );
}

// ---------------------------------------------------------------------------
// Walk-in participants
// ---------------------------------------------------------------------------

export async function insertWalkin(w: {
  walk_in_code: string;
  event_id: string;
  first_name: string;
  last_name: string;
  email?: string;
  phone?: string;
  company?: string;
  ticket_class_id?: string;
  ticket_name?: string;
  notes?: string;
  status: string;
  checked_in_at?: string | null;
}): Promise<number> {
  const db = await getDb();
  const result = await db.runAsync(
    `INSERT INTO walkin_participants
      (walk_in_code, event_id, first_name, last_name, email, phone, company,
       ticket_class_id, ticket_name, notes, status, checked_in_at, created_at, sync_status)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'pending')`,
    w.walk_in_code,
    w.event_id,
    w.first_name,
    w.last_name,
    w.email ?? null,
    w.phone ?? null,
    w.company ?? null,
    w.ticket_class_id ?? null,
    w.ticket_name ?? null,
    w.notes ?? null,
    w.status,
    w.checked_in_at ?? null,
    new Date().toISOString()
  );
  return result.lastInsertRowId;
}

export async function getWalkinParticipants(eventId: string): Promise<WalkinParticipant[]> {
  const db = await getDb();
  return db.getAllAsync<WalkinParticipant>(
    "SELECT * FROM walkin_participants WHERE event_id = ? ORDER BY last_name, first_name",
    eventId
  );
}

export async function getPendingWalkins(eventId: string): Promise<WalkinParticipant[]> {
  const db = await getDb();
  return db.getAllAsync<WalkinParticipant>(
    "SELECT * FROM walkin_participants WHERE event_id = ? AND sync_status = 'pending'",
    eventId
  );
}

export async function markWalkinsSynced(eventId: string): Promise<void> {
  const db = await getDb();
  await db.runAsync(
    "UPDATE walkin_participants SET sync_status = 'synced' WHERE event_id = ? AND sync_status = 'pending'",
    eventId
  );
}

export async function markWalkinCheckedIn(walkinCode: string): Promise<void> {
  const db = await getDb();
  const now = new Date().toISOString();
  await db.runAsync(
    "UPDATE walkin_participants SET status = 'checked_in', checked_in_at = ? WHERE walk_in_code = ?",
    now,
    walkinCode
  );
}
