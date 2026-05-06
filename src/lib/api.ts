import { getToken } from "./auth";
import type {
  LoginResponse,
  EventItem,
  Participant,
  CheckinResult,
  CheckinStats,
  TicketClass,
  WalkinParticipant,
  DashboardData,
} from "../types";

let _baseUrl = "";

export function setApiBaseUrl(url: string) {
  _baseUrl = url.replace(/\/+$/, "");
}

export function getApiBaseUrl(): string {
  return _baseUrl;
}

async function apiFetch<T>(
  path: string,
  options: RequestInit = {}
): Promise<T> {
  const token = await getToken();
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(options.headers as Record<string, string>),
  };
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  const res = await fetch(`${_baseUrl}${path}`, {
    ...options,
    headers,
  });

  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.error || `HTTP ${res.status}`);
  }

  return res.json();
}

export async function login(
  email: string,
  password: string
): Promise<LoginResponse> {
  const res = await fetch(`${_baseUrl}/api/mobile/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });
  return res.json();
}

export async function fetchEvents(): Promise<EventItem[]> {
  const data = await apiFetch<{ events: EventItem[] }>("/api/mobile/events");
  return data.events;
}

export async function fetchParticipants(
  eventId: string,
  since?: string
): Promise<Participant[]> {
  const url = since
    ? `/api/mobile/events/${eventId}/participants?since=${encodeURIComponent(since)}`
    : `/api/mobile/events/${eventId}/participants`;
  const data = await apiFetch<{ participants: Participant[] }>(url);
  return data.participants;
}

export async function checkinOnline(
  backstageTicketId: string,
  eventId: string,
  deviceId?: string
): Promise<CheckinResult> {
  return apiFetch<CheckinResult>("/api/mobile/checkin", {
    method: "POST",
    body: JSON.stringify({
      backstage_ticket_id: backstageTicketId,
      event_id: eventId,
      device_id: deviceId,
    }),
  });
}


export async function syncCheckins(
  items: {
    backstage_ticket_id: string;
    event_id: string;
    scanned_at: string;
    device_id: string;
    action?: string;
  }[]
): Promise<{ synced: number; duplicates: number; errors: number }> {
  return apiFetch("/api/mobile/checkin/sync", {
    method: "POST",
    body: JSON.stringify({ items }),
  });
}

export async function fetchCheckinStats(
  eventId: string
): Promise<CheckinStats> {
  return apiFetch<CheckinStats>(
    `/api/mobile/events/${eventId}/checkin-stats`
  );
}

export async function fetchTicketClasses(eventId: string): Promise<TicketClass[]> {
  const data = await apiFetch<{ ticket_classes: TicketClass[] }>(
    `/api/mobile/events/${eventId}/ticket-classes`
  );
  return data.ticket_classes;
}

export async function createWalkin(data: {
  event_id: string;
  first_name: string;
  last_name: string;
  walk_in_code: string;
  email?: string;
  phone?: string;
  company?: string;
  ticket_class_id?: string;
  notes?: string;
  checked_in_at?: string;
  device_id?: string;
}): Promise<WalkinParticipant & { success: boolean; error?: string }> {
  return apiFetch("/api/mobile/walkin", {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function syncWalkins(
  items: {
    walk_in_code: string;
    event_id: string;
    first_name: string;
    last_name: string;
    email?: string;
    phone?: string;
    company?: string;
    ticket_class_id?: string;
    notes?: string;
    checked_in_at?: string;
    device_id?: string;
  }[]
): Promise<{ synced: number; duplicates: number; errors: number }> {
  return apiFetch("/api/mobile/walkin/batch", {
    method: "POST",
    body: JSON.stringify({ items }),
  });
}

export interface InHubConfig {
  exists: boolean;
  id?: number;
  event_id?: string;
  auto_checkin?: boolean;
  show_search?: boolean;
  show_walkin?: boolean;
  created_at?: string;
  updated_at?: string;
}

export async function fetchInHubConfig(eventId: string): Promise<InHubConfig> {
  return apiFetch<InHubConfig>(`/api/mobile/events/${eventId}/inhub-config`);
}

export async function saveInHubConfig(
  eventId: string,
  data: {
    pin: string;
    auto_checkin?: boolean;
    show_search?: boolean;
    show_walkin?: boolean;
  }
): Promise<{ success: boolean; error?: string }> {
  return apiFetch(`/api/mobile/events/${eventId}/inhub-config`, {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function verifyInHubPin(
  eventId: string,
  pin: string
): Promise<{ valid: boolean }> {
  return apiFetch(`/api/mobile/events/${eventId}/inhub/verify-pin`, {
    method: "POST",
    body: JSON.stringify({ pin }),
  });
}

export async function fetchDashboard(eventId: string): Promise<DashboardData> {
  return apiFetch<DashboardData>(`/api/mobile/events/${eventId}/dashboard`);
}

export interface GusCompanyData {
  name: string;
  regon?: string;
  street?: string;
  zip?: string;
  city?: string;
  voivodeship?: string;
  krs?: string;
}

export async function fetchCompanyByNip(
  nip: string
): Promise<{ success: boolean; data?: GusCompanyData; error?: string }> {
  const clean = nip.replace(/[\s-]/g, "");
  return apiFetch(`/api/mobile/gus/lookup/${encodeURIComponent(clean)}`);
}
