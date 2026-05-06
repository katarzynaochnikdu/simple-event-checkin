export interface User {
  id: number;
  email: string;
  first_name: string;
  last_name: string;
  role: string;
}

export interface LoginResponse {
  success: boolean;
  token?: string;
  user?: User;
  error?: string;
}

export interface EventItem {
  event_id: string;
  event_name: string;
  status: string;
  start_date: string;
  end_date: string;
  venue: string;
}

export interface Participant {
  id: number;
  backstage_ticket_id: string;
  first_name: string;
  last_name: string;
  email: string;
  company: string;
  ticket_class_id: string;
  ticket_name: string;
  status: string;
  attendance_status: string;
  event_order_id: string;
  checked_in_at: string | null;
  /** Ustawiane lokalnie — oznacza uczestnika dodanego na miejscu (walk-in) */
  is_walkin?: boolean;
}

export interface CheckinResult {
  success: boolean;
  already_checked_in?: boolean;
  checked_in_at?: string | null;
  participant?: ParticipantSummary;
  error?: string;
}

export interface ParticipantSummary {
  id: number;
  first_name: string;
  last_name: string;
  email: string;
  company: string;
  ticket_name: string;
  ticket_class_id: string;
}

export interface CheckinStats {
  event_id: string;
  total_with_qr: number;
  checked_in: number;
  not_checked_in: number;
  scanners: { scanned_by: string; scan_count: number }[];
}

export interface OfflineCheckin {
  backstage_ticket_id: string;
  event_id: string;
  scanned_at: string;
  device_id: string;
  synced: boolean;
  action: string;
}

/** Tryb skanowania QR */
export type ScanMode = "checkin" | "review";

export interface TicketClass {
  ticket_class_id: string;
  ticket_name: string;
  event_id: string;
}

export interface WalkinParticipant {
  id: number;
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
  checked_in_at?: string | null;
  status: string;
  created_at: string;
  sync_status: string;
}

export interface DashboardData {
  event_id: string;
  total_registered: number;
  total_with_qr: number;
  checked_in: number;
  walk_ins: number;
  check_in_rate: number;
  by_ticket_class: { ticket_name: string; total: number; checked_in: number }[];
  timeline: { hour: string; count: number }[];
  top_scanners: { email: string; count: number }[];
  /** ISO timestamp — kiedy dane były pobrane */
  fetched_at?: string;
}
