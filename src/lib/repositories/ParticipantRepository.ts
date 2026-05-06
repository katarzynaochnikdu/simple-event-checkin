import {
  getDb,
  cacheParticipants,
  upsertParticipants,
  findParticipantByTicketId,
  markLocalCheckin,
  getLocalParticipants,
  getLocalStats,
} from "../db";
import type { Participant } from "../../types";

export const ParticipantRepository = {
  /**
   * Full replace — używane przy pierwszym pobraniu lub wymuszonym odświeżeniu.
   */
  async replaceAll(eventId: string, participants: Participant[]): Promise<void> {
    return cacheParticipants(eventId, participants);
  },

  /**
   * Przyrostowy merge — używane przy synchronizacji ?since=.
   */
  async mergeUpdates(eventId: string, participants: Participant[]): Promise<void> {
    return upsertParticipants(eventId, participants);
  },

  async findByTicketId(ticketId: string, eventId: string): Promise<Participant | null> {
    return findParticipantByTicketId(ticketId, eventId);
  },

  async getAll(eventId: string): Promise<Participant[]> {
    return getLocalParticipants(eventId);
  },

  async getStats(eventId: string): Promise<{ total: number; checkedIn: number }> {
    return getLocalStats(eventId);
  },

  async markCheckedIn(ticketId: string, eventId: string): Promise<void> {
    return markLocalCheckin(ticketId, eventId);
  },

  async countByStatus(
    eventId: string
  ): Promise<{ registered: number; checked_in: number }> {
    const db = await getDb();
    const rows = await db.getAllAsync<{ status: string; cnt: number }>(
      "SELECT status, COUNT(*) as cnt FROM participants WHERE event_id = ? GROUP BY status",
      eventId
    );
    const result = { registered: 0, checked_in: 0 };
    for (const row of rows) {
      if (row.status === "registered") result.registered = row.cnt;
      else if (row.status === "checked_in") result.checked_in = row.cnt;
    }
    return result;
  },
};
