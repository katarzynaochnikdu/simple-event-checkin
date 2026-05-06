import { useState, useEffect } from "react";
import { getSyncState, subscribe, type SyncState } from "../lib/sync/SyncEngine";

/**
 * Hook subskrybujący status synchronizacji z SyncEngine.
 * Bezpieczny do użycia w wielu komponentach jednocześnie.
 */
export function useSyncStatus(): SyncState {
  const [status, setStatus] = useState<SyncState>(getSyncState());

  useEffect(() => {
    const unsubscribe = subscribe(setStatus);
    return unsubscribe;
  }, []);

  return status;
}
