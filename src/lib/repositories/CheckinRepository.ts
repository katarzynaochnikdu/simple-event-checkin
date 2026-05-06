import {
  addOfflineCheckin,
  getUnsyncedCheckins,
  markCheckinsSynced,
  incrementRetry,
} from "../db";
import type { OfflineCheckin } from "../../types";

export const CheckinRepository = {
  /**
   * Dodaje zdarzenie check-in do kolejki offline.
   */
  async addOfflineAction(
    backstageTicketId: string,
    eventId: string,
    deviceId: string,
    action: "checkin" = "checkin"
  ): Promise<void> {
    return addOfflineCheckin(backstageTicketId, eventId, deviceId, action);
  },

  /**
   * Zwraca niezsynchronizowane zdarzenia (z uwzględnieniem backoff).
   */
  async getUnsynced(): Promise<OfflineCheckin[]> {
    return getUnsyncedCheckins();
  },

  /**
   * Oznacza wszystkie niezsynchronizowane zdarzenia jako zsynchronizowane.
   */
  async markAllSynced(): Promise<void> {
    return markCheckinsSynced();
  },

  /**
   * Zwiększa licznik retry i ustawia next_retry_at z exponential backoff.
   */
  async incrementRetry(backstageTicketId: string, eventId: string): Promise<void> {
    return incrementRetry(backstageTicketId, eventId);
  },
};
