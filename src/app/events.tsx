import { useEffect, useState, useCallback, useMemo } from "react";
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  StyleSheet,
  ActivityIndicator,
  Alert,
  RefreshControl,
  TextInput,
} from "react-native";
import { router } from "expo-router";
import { Ionicons } from "@expo/vector-icons";
import { fetchEvents } from "../lib/api";
import { getLocalStats } from "../lib/db";
import { logout } from "../lib/auth";
import { isOnline } from "../lib/sync";
import { initialSync, startBackgroundSync } from "../lib/sync/SyncEngine";
import { useEvent } from "../contexts/EventContext";
import type { EventItem } from "../types";

type Tab = "upcoming" | "past" | "sandbox";

const TODAY = new Date().toISOString().slice(0, 10);

function categorizeEvent(event: EventItem): Tab {
  const name = event.event_name.toLowerCase();
  const status = (event.status || "").toLowerCase();

  if (
    status === "sandbox" ||
    status === "draft" ||
    status === "test" ||
    name.includes("sandbox") ||
    name.includes("test")
  ) {
    return "sandbox";
  }

  const compareDate = event.end_date || event.start_date;
  if (compareDate && compareDate < TODAY) {
    return "past";
  }

  return "upcoming";
}

export default function EventsScreen() {
  const { setEvent } = useEvent();
  const [events, setEvents] = useState<EventItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [syncing, setSyncing] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [activeTab, setActiveTab] = useState<Tab>("upcoming");
  const [progressMap, setProgressMap] = useState<Record<string, { total: number; checkedIn: number }>>({});

  const loadEvents = useCallback(async () => {
    try {
      const data = await fetchEvents();
      const sorted = [...data].sort((a, b) => {
        if (!a.start_date) return 1;
        if (!b.start_date) return -1;
        return b.start_date.localeCompare(a.start_date);
      });
      setEvents(sorted);
      loadLocalProgress(sorted);
    } catch (e: any) {
      const msg = e.message || "";
      if (
        msg.includes("401") ||
        msg.toLowerCase().includes("token") ||
        msg.toLowerCase().includes("unauthorized")
      ) {
        Alert.alert(
          "Sesja wygasła",
          "Twój token jest nieważny. Zaloguj się ponownie.",
          [
            {
              text: "OK",
              onPress: async () => {
                await logout();
                router.replace("/login");
              },
            },
          ]
        );
        return;
      }
      Alert.alert("Błąd", msg || "Nie udało się pobrać wydarzeń");
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  async function loadLocalProgress(eventList: EventItem[]) {
    const map: Record<string, { total: number; checkedIn: number }> = {};
    for (const e of eventList) {
      try {
        const stats = await getLocalStats(e.event_id);
        if (stats.total > 0) {
          map[e.event_id] = stats;
        }
      } catch {
        // brak danych lokalnych
      }
    }
    setProgressMap(map);
  }

  useEffect(() => {
    loadEvents();
  }, [loadEvents]);

  const filteredEvents = useMemo(() => {
    const byTab = events.filter((e) => categorizeEvent(e) === activeTab);
    if (!search.trim()) return byTab;
    const q = search.trim().toLowerCase();
    return byTab.filter(
      (e) =>
        e.event_name.toLowerCase().includes(q) ||
        (e.venue && e.venue.toLowerCase().includes(q))
    );
  }, [events, search, activeTab]);

  const counts = useMemo(() => {
    const result = { upcoming: 0, past: 0, sandbox: 0 };
    for (const e of events) {
      result[categorizeEvent(e)]++;
    }
    return result;
  }, [events]);

  async function selectEvent(event: EventItem) {
    const online = await isOnline();
    if (!online) {
      Alert.alert(
        "Brak internetu",
        "Nie można pobrać listy uczestników. Jeśli wcześniej pobrano dane, możesz kontynuować.",
        [
          { text: "Anuluj" },
          {
            text: "Kontynuuj offline",
            onPress: () => {
              setEvent(event);
              router.push("/(tabs)/overview");
            },
          },
        ]
      );
      return;
    }

    setSyncing(event.event_id);
    try {
      await initialSync(event.event_id);
      setEvent(event);
      startBackgroundSync(event.event_id);
      router.push("/(tabs)/overview");
    } catch (e: any) {
      Alert.alert("Błąd", e.message || "Nie udało się pobrać uczestników");
    } finally {
      setSyncing(null);
    }
  }

  if (loading) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color="#0d9488" />
        <Text style={styles.loadingText}>Ładowanie wydarzeń...</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      {/* Search */}
      <View style={styles.searchContainer}>
        <Ionicons name="search-outline" size={18} color="#94a3b8" style={styles.searchIcon} />
        <TextInput
          style={styles.searchInput}
          placeholder="Szukaj wydarzeń..."
          placeholderTextColor="#94a3b8"
          value={search}
          onChangeText={setSearch}
          returnKeyType="search"
          clearButtonMode="while-editing"
        />
      </View>

      {/* Tabs */}
      <View style={styles.tabBar}>
        <TabBtn
          label="Nadchodzące"
          count={counts.upcoming}
          active={activeTab === "upcoming"}
          onPress={() => setActiveTab("upcoming")}
        />
        <TabBtn
          label="Przeszłe"
          count={counts.past}
          active={activeTab === "past"}
          onPress={() => setActiveTab("past")}
        />
        <TabBtn
          label="Sandbox"
          count={counts.sandbox}
          active={activeTab === "sandbox"}
          onPress={() => setActiveTab("sandbox")}
        />
      </View>

      <FlatList
        data={filteredEvents}
        keyExtractor={(item) => item.event_id}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={() => {
              setRefreshing(true);
              loadEvents();
            }}
            tintColor="#0d9488"
          />
        }
        contentContainerStyle={styles.list}
        ListEmptyComponent={
          <View style={styles.center}>
            <Ionicons name="calendar-outline" size={48} color="#94a3b8" />
            <Text style={styles.emptyText}>
              {search ? "Brak wyników wyszukiwania" : "Brak wydarzeń w tej kategorii"}
            </Text>
          </View>
        }
        renderItem={({ item }) => {
          const progress = progressMap[item.event_id];
          const pct =
            progress && progress.total > 0
              ? Math.round((progress.checkedIn / progress.total) * 100)
              : null;

          return (
            <TouchableOpacity
              style={styles.card}
              onPress={() => selectEvent(item)}
              disabled={syncing === item.event_id}
            >
              <View style={styles.cardContent}>
                <Text style={styles.eventName}>{item.event_name}</Text>
                {item.start_date ? (
                  <Text style={styles.eventDate}>{item.start_date}</Text>
                ) : null}
                {item.venue ? (
                  <Text style={styles.eventVenue}>
                    <Ionicons name="location-outline" size={13} color="#64748b" />{" "}
                    {item.venue}
                  </Text>
                ) : null}
                {pct !== null && (
                  <View style={styles.progressContainer}>
                    <View style={styles.progressBar}>
                      <View style={[styles.progressFill, { width: `${pct}%` as any }]} />
                    </View>
                    <Text style={styles.progressText}>
                      {progress!.checkedIn}/{progress!.total} ({pct}%)
                    </Text>
                  </View>
                )}
              </View>
              {syncing === item.event_id ? (
                <ActivityIndicator color="#0d9488" />
              ) : (
                <Ionicons name="chevron-forward" size={22} color="#94a3b8" />
              )}
            </TouchableOpacity>
          );
        }}
      />
    </View>
  );
}

function TabBtn({
  label,
  count,
  active,
  onPress,
}: {
  label: string;
  count: number;
  active: boolean;
  onPress: () => void;
}) {
  return (
    <TouchableOpacity
      style={[styles.tab, active && styles.tabActive]}
      onPress={onPress}
      activeOpacity={0.7}
    >
      <Text style={[styles.tabText, active && styles.tabTextActive]}>{label}</Text>
      {count > 0 && (
        <View style={[styles.tabBadge, active && styles.tabBadgeActive]}>
          <Text style={[styles.tabBadgeText, active && styles.tabBadgeTextActive]}>
            {count}
          </Text>
        </View>
      )}
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#f8fafc" },
  center: { flex: 1, justifyContent: "center", alignItems: "center", padding: 32 },
  loadingText: { marginTop: 12, color: "#64748b", fontSize: 15 },
  emptyText: { marginTop: 12, color: "#94a3b8", fontSize: 16, textAlign: "center" },

  searchContainer: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "#fff",
    marginHorizontal: 16,
    marginTop: 12,
    marginBottom: 4,
    borderRadius: 10,
    paddingHorizontal: 12,
    borderWidth: 1,
    borderColor: "#e2e8f0",
  },
  searchIcon: { marginRight: 8 },
  searchInput: {
    flex: 1,
    height: 42,
    fontSize: 15,
    color: "#1e293b",
  },

  tabBar: {
    flexDirection: "row",
    backgroundColor: "#fff",
    marginHorizontal: 16,
    marginTop: 10,
    marginBottom: 8,
    borderRadius: 12,
    padding: 4,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.04,
    shadowRadius: 4,
    elevation: 1,
  },
  tab: {
    flex: 1,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    paddingVertical: 8,
    paddingHorizontal: 4,
    borderRadius: 9,
    gap: 5,
  },
  tabActive: { backgroundColor: "#0d9488" },
  tabText: { fontSize: 12, fontWeight: "600", color: "#94a3b8" },
  tabTextActive: { color: "#fff" },
  tabBadge: {
    backgroundColor: "#f1f5f9",
    borderRadius: 10,
    paddingHorizontal: 5,
    paddingVertical: 1,
    minWidth: 20,
    alignItems: "center",
  },
  tabBadgeActive: { backgroundColor: "rgba(255,255,255,0.25)" },
  tabBadgeText: { fontSize: 10, fontWeight: "700", color: "#64748b" },
  tabBadgeTextActive: { color: "#fff" },

  list: { padding: 16, paddingTop: 8 },
  card: {
    backgroundColor: "#fff",
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
    flexDirection: "row",
    alignItems: "center",
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.06,
    shadowRadius: 4,
    elevation: 2,
  },
  cardContent: { flex: 1 },
  eventName: { fontSize: 17, fontWeight: "600", color: "#1e293b", marginBottom: 4 },
  eventDate: { fontSize: 13, color: "#64748b", marginBottom: 2 },
  eventVenue: { fontSize: 13, color: "#64748b" },
  progressContainer: {
    marginTop: 8,
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
  },
  progressBar: {
    flex: 1,
    height: 4,
    backgroundColor: "#e2e8f0",
    borderRadius: 2,
    overflow: "hidden",
  },
  progressFill: {
    height: "100%",
    backgroundColor: "#0d9488",
    borderRadius: 2,
  },
  progressText: { fontSize: 11, color: "#64748b", minWidth: 70 },
});
