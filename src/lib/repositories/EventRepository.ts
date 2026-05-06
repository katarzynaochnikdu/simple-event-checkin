import { getSyncMetadata, setSyncMetadata } from "../db";

export const EventRepository = {
  async getLastParticipantsSync(eventId: string): Promise<string | null> {
    const meta = await getSyncMetadata(eventId);
    return meta.last_participants_sync;
  },

  async setLastParticipantsSync(eventId: string, timestamp: string): Promise<void> {
    return setSyncMetadata(eventId, "last_participants_sync", timestamp);
  },

  async getLastCheckinPush(eventId: string): Promise<string | null> {
    const meta = await getSyncMetadata(eventId);
    return meta.last_checkin_push;
  },

  async setLastCheckinPush(eventId: string, timestamp: string): Promise<void> {
    return setSyncMetadata(eventId, "last_checkin_push", timestamp);
  },
};
