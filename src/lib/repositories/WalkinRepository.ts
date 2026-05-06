import {
  insertWalkin,
  getWalkinParticipants,
  getPendingWalkins,
  markWalkinsSynced,
  markWalkinCheckedIn,
  upsertTicketClasses,
  getTicketClasses,
} from "../db";
import type { WalkinParticipant, TicketClass } from "../../types";

export const WalkinRepository = {
  /**
   * Generuje unikalny kod walk-in: WI-<event_id[:8]>-<timestamp_ms>
   */
  generateCode(eventId: string): string {
    return `WI-${eventId.slice(0, 8).toUpperCase()}-${Date.now()}`;
  },

  async create(data: {
    event_id: string;
    first_name: string;
    last_name: string;
    email?: string;
    phone?: string;
    company?: string;
    ticket_class_id?: string;
    ticket_name?: string;
    notes?: string;
    check_in_immediately?: boolean;
  }): Promise<WalkinParticipant> {
    const walk_in_code = WalkinRepository.generateCode(data.event_id);
    const status = data.check_in_immediately ? "checked_in" : "registered";
    const checked_in_at = data.check_in_immediately ? new Date().toISOString() : null;

    const id = await insertWalkin({
      walk_in_code,
      event_id: data.event_id,
      first_name: data.first_name,
      last_name: data.last_name,
      email: data.email,
      phone: data.phone,
      company: data.company,
      ticket_class_id: data.ticket_class_id,
      ticket_name: data.ticket_name,
      notes: data.notes,
      status,
      checked_in_at,
    });

    return {
      id,
      walk_in_code,
      event_id: data.event_id,
      first_name: data.first_name,
      last_name: data.last_name,
      email: data.email,
      phone: data.phone,
      company: data.company,
      ticket_class_id: data.ticket_class_id,
      ticket_name: data.ticket_name,
      notes: data.notes,
      status,
      checked_in_at,
      created_at: new Date().toISOString(),
      sync_status: "pending",
    };
  },

  async getAll(eventId: string): Promise<WalkinParticipant[]> {
    return getWalkinParticipants(eventId);
  },

  async getPending(eventId: string): Promise<WalkinParticipant[]> {
    return getPendingWalkins(eventId);
  },

  async markSynced(eventId: string): Promise<void> {
    return markWalkinsSynced(eventId);
  },

  async markCheckedIn(walkinCode: string): Promise<void> {
    return markWalkinCheckedIn(walkinCode);
  },

  // Ticket classes
  async cacheTicketClasses(eventId: string, classes: TicketClass[]): Promise<void> {
    return upsertTicketClasses(eventId, classes);
  },

  async getTicketClasses(eventId: string): Promise<TicketClass[]> {
    return getTicketClasses(eventId);
  },
};
