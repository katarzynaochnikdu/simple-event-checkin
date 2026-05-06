/**
 * Warstwa kompatybilności — eksportuje isOnline() i deleguje syncOfflineCheckins()
 * do SyncEngine. Istniejący kod (scanner.tsx, stats.tsx) może importować stąd
 * bez zmian; nowy kod powinien używać SyncEngine bezpośrednio.
 */
import * as Network from "expo-network";
import { triggerSync } from "./sync/SyncEngine";

export async function isOnline(): Promise<boolean> {
  try {
    const state = await Network.getNetworkStateAsync();
    return !!(state.isConnected && state.isInternetReachable);
  } catch {
    return false;
  }
}

/**
 * @deprecated Użyj SyncEngine.triggerSync(eventId) bezpośrednio.
 * Zachowane dla kompatybilności z istniejącym kodem stats.tsx.
 */
export async function syncOfflineCheckins(
  eventId?: string
): Promise<{ synced: number; errors: number } | null> {
  if (!eventId) return null;
  try {
    await triggerSync(eventId);
    return { synced: 0, errors: 0 };
  } catch {
    return null;
  }
}
