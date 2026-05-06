/**
 * SyncEngine — centralny orchestrator synchronizacji offline-first.
 *
 * Pull: pobiera uczestników (przyrostowo gdy dostępny timestamp ?since=).
 * Push: wysyła kolejkę offline check-inów do backendu (z exponential backoff).
 * Background: automatyczny tick co 5 minut gdy aplikacja jest otwarta.
 *
 * Nie używa expo-background-fetch — działa tylko gdy apka jest na pierwszym planie.
 */

import * as Network from "expo-network";
import { fetchParticipants, syncCheckins, syncWalkins } from "../api";
import { ParticipantRepository } from "../repositories/ParticipantRepository";
import { CheckinRepository } from "../repositories/CheckinRepository";
import { EventRepository } from "../repositories/EventRepository";
import { WalkinRepository } from "../repositories/WalkinRepository";

const BACKGROUND_INTERVAL_MS = 5 * 60 * 1000; // 5 minut

// ---------------------------------------------------------------------------
// Stan synchronizacji
// ---------------------------------------------------------------------------

export type SyncState = {
  state: "idle" | "syncing" | "error";
  lastSynced: string | null;
  pendingCount: number;
  errorMessage: string | null;
};

let _syncState: SyncState = {
  state: "idle",
  lastSynced: null,
  pendingCount: 0,
  errorMessage: null,
};

type Listener = (state: SyncState) => void;
const _listeners: Listener[] = [];

function _setState(patch: Partial<SyncState>) {
  _syncState = { ..._syncState, ...patch };
  for (const fn of _listeners) {
    fn(_syncState);
  }
}

export function getSyncState(): SyncState {
  return _syncState;
}

export function subscribe(listener: Listener): () => void {
  _listeners.push(listener);
  return () => {
    const idx = _listeners.indexOf(listener);
    if (idx !== -1) _listeners.splice(idx, 1);
  };
}

// ---------------------------------------------------------------------------
// Sprawdzanie połączenia
// ---------------------------------------------------------------------------

async function _isOnline(): Promise<boolean> {
  try {
    const state = await Network.getNetworkStateAsync();
    return !!(state.isConnected && state.isInternetReachable);
  } catch {
    return false;
  }
}

// ---------------------------------------------------------------------------
// Pull — pobieranie uczestników
// ---------------------------------------------------------------------------

async function _pullParticipants(eventId: string): Promise<void> {
  const since = await EventRepository.getLastParticipantsSync(eventId);
  const syncStart = new Date().toISOString();

  const participants = await fetchParticipants(eventId, since ?? undefined);

  if (since) {
    // przyrostowy merge
    await ParticipantRepository.mergeUpdates(eventId, participants);
  } else {
    // pierwsze pobranie — pełna wymiana
    await ParticipantRepository.replaceAll(eventId, participants);
  }

  await EventRepository.setLastParticipantsSync(eventId, syncStart);
}

// ---------------------------------------------------------------------------
// Push — wysyłanie offline check-inów
// ---------------------------------------------------------------------------

async function _pushCheckins(eventId: string): Promise<void> {
  const unsynced = await CheckinRepository.getUnsynced();
  const forThisEvent = unsynced.filter((c) => c.event_id === eventId);
  if (forThisEvent.length === 0) return;

  try {
    await syncCheckins(
      forThisEvent.map((c) => ({
        backstage_ticket_id: c.backstage_ticket_id,
        event_id: c.event_id,
        scanned_at: c.scanned_at,
        device_id: c.device_id,
        action: c.action,
      }))
    );
    await CheckinRepository.markAllSynced();
    await EventRepository.setLastCheckinPush(eventId, new Date().toISOString());
  } catch {
    // Backoff dla każdego nieudanego check-inu
    for (const c of forThisEvent) {
      await CheckinRepository.incrementRetry(c.backstage_ticket_id, c.event_id);
    }
    throw new Error("Błąd wysyłania check-inów");
  }
}

// ---------------------------------------------------------------------------
// Push — wysyłanie offline walk-inów
// ---------------------------------------------------------------------------

async function _pushWalkins(eventId: string): Promise<void> {
  const pending = await WalkinRepository.getPending(eventId);
  if (pending.length === 0) return;

  await syncWalkins(
    pending.map((w) => ({
      walk_in_code: w.walk_in_code,
      event_id: w.event_id,
      first_name: w.first_name,
      last_name: w.last_name,
      email: w.email,
      phone: w.phone,
      company: w.company,
      ticket_class_id: w.ticket_class_id,
      notes: w.notes,
      checked_in_at: w.checked_in_at ?? undefined,
    }))
  );
  await WalkinRepository.markSynced(eventId);
}

// ---------------------------------------------------------------------------
// Tick — jedna runda synchronizacji
// ---------------------------------------------------------------------------

async function _tick(eventId: string): Promise<void> {
  if (_syncState.state === "syncing") return;

  const online = await _isOnline();
  if (!online) {
    const pending = await CheckinRepository.getUnsynced();
    _setState({ pendingCount: pending.length });
    return;
  }

  _setState({ state: "syncing", errorMessage: null });
  try {
    await _pullParticipants(eventId);
    await _pushCheckins(eventId);
    await _pushWalkins(eventId).catch(() => {
      // walk-in push failures are non-fatal
    });

    const pending = await CheckinRepository.getUnsynced();
    _setState({
      state: "idle",
      lastSynced: new Date().toLocaleTimeString("pl-PL", {
        hour: "2-digit",
        minute: "2-digit",
      }),
      pendingCount: pending.length,
      errorMessage: null,
    });
  } catch (e: any) {
    const pending = await CheckinRepository.getUnsynced();
    _setState({
      state: "error",
      pendingCount: pending.length,
      errorMessage: e?.message || "Błąd synchronizacji",
    });
  }
}

// ---------------------------------------------------------------------------
// Background sync
// ---------------------------------------------------------------------------

let _intervalId: ReturnType<typeof setInterval> | null = null;
let _currentEventId: string | null = null;

export function startBackgroundSync(eventId: string): void {
  stopBackgroundSync();
  _currentEventId = eventId;
  _intervalId = setInterval(() => {
    _tick(eventId);
  }, BACKGROUND_INTERVAL_MS);
}

export function stopBackgroundSync(): void {
  if (_intervalId !== null) {
    clearInterval(_intervalId);
    _intervalId = null;
  }
  _currentEventId = null;
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * Pierwsze pobranie danych po wyborze eventu.
 * Zawsze pobiera pełną listę uczestników i wysyła pending check-iny.
 */
export async function initialSync(eventId: string): Promise<void> {
  const online = await _isOnline();

  // Odśwież liczbę oczekujących niezależnie od sieci
  const pending = await CheckinRepository.getUnsynced();
  _setState({ pendingCount: pending.length });

  if (!online) return;

  _setState({ state: "syncing", errorMessage: null });
  try {
    const participants = await fetchParticipants(eventId);
    await ParticipantRepository.replaceAll(eventId, participants);
    await EventRepository.setLastParticipantsSync(eventId, new Date().toISOString());

    await _pushCheckins(eventId).catch(() => {
      // push failures in initial sync are non-fatal
    });
    await _pushWalkins(eventId).catch(() => {
      // walk-in push failures are non-fatal
    });

    const pendingAfter = await CheckinRepository.getUnsynced();
    _setState({
      state: "idle",
      lastSynced: new Date().toLocaleTimeString("pl-PL", {
        hour: "2-digit",
        minute: "2-digit",
      }),
      pendingCount: pendingAfter.length,
      errorMessage: null,
    });
  } catch (e: any) {
    const pendingAfter = await CheckinRepository.getUnsynced();
    _setState({
      state: "error",
      pendingCount: pendingAfter.length,
      errorMessage: e?.message || "Błąd pobierania uczestników",
    });
    throw e;
  }
}

/**
 * Ręczne wyzwolenie synchronizacji (np. z ekranu Statystyki).
 */
export async function triggerSync(eventId: string): Promise<void> {
  return _tick(eventId);
}
