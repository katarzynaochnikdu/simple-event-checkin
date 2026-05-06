import { useState, useEffect, useCallback } from "react";
import AsyncStorage from "@react-native-async-storage/async-storage";
import { fetchDashboard } from "../lib/api";
import type { DashboardData } from "../types";

const CACHE_TTL_MS = 60_000; // 60s
const CACHE_KEY_PREFIX = "dashboard_cache_";

export type DashboardState = {
  data: DashboardData | null;
  loading: boolean;
  error: string | null;
  fromCache: boolean;
  refresh: () => void;
};

export function useDashboard(eventId: string | undefined): DashboardState {
  const [data, setData] = useState<DashboardData | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fromCache, setFromCache] = useState(false);

  const load = useCallback(async () => {
    if (!eventId) return;
    const cacheKey = `${CACHE_KEY_PREFIX}${eventId}`;

    // Try cache first
    try {
      const cached = await AsyncStorage.getItem(cacheKey);
      if (cached) {
        const parsed: DashboardData & { _cachedAt?: number } = JSON.parse(cached);
        const age = Date.now() - (parsed._cachedAt ?? 0);
        if (age < CACHE_TTL_MS) {
          setData(parsed);
          setFromCache(true);
          setError(null);
          return;
        }
      }
    } catch {
      // cache miss — continue to fetch
    }

    setLoading(true);
    setError(null);
    try {
      const fresh = await fetchDashboard(eventId);
      const toStore = { ...fresh, fetched_at: new Date().toISOString(), _cachedAt: Date.now() };
      await AsyncStorage.setItem(cacheKey, JSON.stringify(toStore));
      setData(fresh);
      setFromCache(false);
    } catch (e: any) {
      setError(e?.message || "Błąd pobierania danych");
      // Show stale cache if available
      try {
        const stale = await AsyncStorage.getItem(cacheKey);
        if (stale) {
          setData(JSON.parse(stale));
          setFromCache(true);
        }
      } catch {
        // no stale data
      }
    } finally {
      setLoading(false);
    }
  }, [eventId]);

  useEffect(() => {
    load();
  }, [load]);

  return { data, loading, error, fromCache, refresh: load };
}
