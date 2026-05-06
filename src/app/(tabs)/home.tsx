import { useEffect, useState } from "react";
import { View, Text, StyleSheet, TouchableOpacity, ScrollView } from "react-native";
import { router } from "expo-router";
import { Ionicons } from "@expo/vector-icons";
import { useEvent } from "../../contexts/EventContext";
import { getLocalStats } from "../../lib/db";

interface Stats {
  total: number;
  checkedIn: number;
}

export default function HomeScreen() {
  const { event } = useEvent();
  const [stats, setStats] = useState<Stats>({ total: 0, checkedIn: 0 });

  useEffect(() => {
    if (event?.event_id) {
      getLocalStats(event.event_id).then(setStats).catch(() => {});
    }
  }, [event?.event_id]);

  if (!event) {
    return (
      <View style={styles.empty}>
        <Ionicons name="calendar-outline" size={52} color="#94a3b8" />
        <Text style={styles.emptyText}>Nie wybrano wydarzenia</Text>
      </View>
    );
  }

  const pct = stats.total > 0 ? Math.round((stats.checkedIn / stats.total) * 100) : 0;

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      {/* Event header card */}
      <View style={styles.eventCard}>
        <View style={styles.eventBadge}>
          <Ionicons name="calendar" size={18} color="#0d9488" />
        </View>
        <View style={styles.eventInfo}>
          <Text style={styles.eventName} numberOfLines={2}>
            {event.event_name}
          </Text>
          {event.start_date ? (
            <Text style={styles.eventDate}>{event.start_date}</Text>
          ) : null}
          {event.venue ? (
            <Text style={styles.eventVenue}>
              {event.venue}
            </Text>
          ) : null}
        </View>
      </View>

      {/* Stats card */}
      {stats.total > 0 && (
        <View style={styles.statsCard}>
          <View style={styles.statsRow}>
            <View style={styles.statItem}>
              <Text style={styles.statValue}>{stats.checkedIn}</Text>
              <Text style={styles.statLabel}>odznaczonych</Text>
            </View>
            <View style={styles.statDivider} />
            <View style={styles.statItem}>
              <Text style={styles.statValue}>{stats.total - stats.checkedIn}</Text>
              <Text style={styles.statLabel}>oczekujących</Text>
            </View>
            <View style={styles.statDivider} />
            <View style={styles.statItem}>
              <Text style={[styles.statValue, { color: "#0d9488" }]}>{pct}%</Text>
              <Text style={styles.statLabel}>wypełnienie</Text>
            </View>
          </View>
          <View style={styles.progressTrack}>
            <View style={[styles.progressFill, { width: `${pct}%` as any }]} />
          </View>
          <Text style={styles.progressLabel}>
            {stats.checkedIn} / {stats.total} zarejestrowanych
          </Text>
        </View>
      )}

      {/* Action tiles */}
      <Text style={styles.sectionTitle}>Akcje</Text>

      <View style={styles.tilesGrid}>
        <ActionTile
          icon="qr-code"
          label="Skaner"
          sublabel="Skanuj kody QR"
          color="#0d9488"
          bg="#f0fdfa"
          onPress={() => router.push("/(tabs)/scanner")}
        />
        <ActionTile
          icon="tv"
          label="InHub"
          sublabel="Tryb kiosku"
          color="#7c3aed"
          bg="#f5f3ff"
          onPress={() => router.push("/(tabs)/inhub")}
        />
      </View>

      <View style={styles.tilesGrid}>
        <ActionTile
          icon="people"
          label="Uczestnicy"
          sublabel="Lista i wyszukiwanie"
          color="#2563eb"
          bg="#eff6ff"
          onPress={() => router.push("/(tabs)/participants")}
        />
        <ActionTile
          icon="bar-chart"
          label="Statystyki"
          sublabel="Synchronizacja"
          color="#f59e0b"
          bg="#fffbeb"
          onPress={() => router.push("/(tabs)/stats")}
        />
      </View>
    </ScrollView>
  );
}

function ActionTile({
  icon,
  label,
  sublabel,
  color,
  bg,
  onPress,
}: {
  icon: string;
  label: string;
  sublabel: string;
  color: string;
  bg: string;
  onPress: () => void;
}) {
  return (
    <TouchableOpacity style={[styles.tile, { backgroundColor: bg }]} onPress={onPress} activeOpacity={0.75}>
      <View style={[styles.tileIcon, { backgroundColor: color }]}>
        <Ionicons name={icon as any} size={28} color="#fff" />
      </View>
      <Text style={[styles.tileLabel, { color }]}>{label}</Text>
      <Text style={styles.tileSublabel}>{sublabel}</Text>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#f8fafc" },
  content: { padding: 16, paddingBottom: 32 },
  empty: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: "#f8fafc",
  },
  emptyText: { marginTop: 12, fontSize: 16, color: "#94a3b8" },

  // Event header
  eventCard: {
    flexDirection: "row",
    alignItems: "flex-start",
    backgroundColor: "#fff",
    borderRadius: 16,
    padding: 16,
    marginBottom: 12,
    gap: 12,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.06,
    shadowRadius: 8,
    elevation: 3,
  },
  eventBadge: {
    width: 40,
    height: 40,
    borderRadius: 10,
    backgroundColor: "#f0fdfa",
    alignItems: "center",
    justifyContent: "center",
  },
  eventInfo: { flex: 1 },
  eventName: { fontSize: 17, fontWeight: "700", color: "#1e293b", lineHeight: 22 },
  eventDate: { fontSize: 13, color: "#64748b", marginTop: 3 },
  eventVenue: { fontSize: 12, color: "#94a3b8", marginTop: 2 },

  // Stats
  statsCard: {
    backgroundColor: "#fff",
    borderRadius: 16,
    padding: 16,
    marginBottom: 20,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.06,
    shadowRadius: 8,
    elevation: 3,
  },
  statsRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-around",
    marginBottom: 14,
  },
  statItem: { alignItems: "center", flex: 1 },
  statValue: { fontSize: 28, fontWeight: "800", color: "#1e293b" },
  statLabel: { fontSize: 12, color: "#94a3b8", marginTop: 2 },
  statDivider: { width: 1, height: 40, backgroundColor: "#f1f5f9" },
  progressTrack: {
    height: 6,
    backgroundColor: "#f1f5f9",
    borderRadius: 3,
    overflow: "hidden",
  },
  progressFill: {
    height: "100%",
    backgroundColor: "#0d9488",
    borderRadius: 3,
  },
  progressLabel: {
    marginTop: 8,
    fontSize: 12,
    color: "#94a3b8",
    textAlign: "center",
  },

  // Tiles
  sectionTitle: {
    fontSize: 12,
    fontWeight: "600",
    color: "#94a3b8",
    textTransform: "uppercase",
    letterSpacing: 0.8,
    marginBottom: 10,
    marginLeft: 2,
  },
  tilesGrid: {
    flexDirection: "row",
    gap: 12,
    marginBottom: 12,
  },
  tile: {
    flex: 1,
    borderRadius: 16,
    padding: 18,
    alignItems: "flex-start",
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.04,
    shadowRadius: 4,
    elevation: 1,
  },
  tileIcon: {
    width: 52,
    height: 52,
    borderRadius: 14,
    alignItems: "center",
    justifyContent: "center",
    marginBottom: 12,
  },
  tileLabel: { fontSize: 16, fontWeight: "700" },
  tileSublabel: { fontSize: 12, color: "#94a3b8", marginTop: 3 },
});
