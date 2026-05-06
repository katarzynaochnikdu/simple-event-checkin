import { useState, useEffect, useCallback } from "react";
import {
  View,
  Text,
  StyleSheet,
  RefreshControl,
  ScrollView,
  ActivityIndicator,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useEvent } from "../../contexts/EventContext";
import { getLocalStats, getUnsyncedCheckins } from "../../lib/db";
import { fetchCheckinStats } from "../../lib/api";
import { isOnline } from "../../lib/sync";
import { triggerSync } from "../../lib/sync/SyncEngine";
import SyncStatusBar from "../../components/SyncStatusBar";
import type { CheckinStats } from "../../types";

export default function StatsScreen() {
  const { event } = useEvent();
  const eventId = event?.event_id;
  const [localStats, setLocalStats] = useState({ total: 0, checkedIn: 0 });
  const [serverStats, setServerStats] = useState<CheckinStats | null>(null);
  const [pendingSync, setPendingSync] = useState(0);
  const [refreshing, setRefreshing] = useState(false);
  const [syncing, setSyncing] = useState(false);

  const load = useCallback(async () => {
    if (!eventId) return;

    const local = await getLocalStats(eventId);
    setLocalStats(local);

    const pending = await getUnsyncedCheckins();
    setPendingSync(pending.length);

    const online = await isOnline();
    if (online) {
      try {
        const stats = await fetchCheckinStats(eventId);
        setServerStats(stats);
      } catch {
        // offline — use local only
      }
    }
    setRefreshing(false);
  }, [eventId]);

  useEffect(() => {
    load();
  }, [load]);

  async function handleSync() {
    if (!eventId) return;
    setSyncing(true);
    try {
      await triggerSync(eventId);
      await load();
    } finally {
      setSyncing(false);
    }
  }

  const stats = serverStats || {
    total_with_qr: localStats.total,
    checked_in: localStats.checkedIn,
    not_checked_in: localStats.total - localStats.checkedIn,
  };

  const pct = stats.total_with_qr > 0
    ? Math.round((stats.checked_in / stats.total_with_qr) * 100)
    : 0;

  if (!eventId) {
    return (
      <View style={styles.emptyContainer}>
        <Ionicons name="calendar-outline" size={42} color="#94a3b8" />
        <Text style={styles.emptyTitle}>Nie wybrano wydarzenia</Text>
        <Text style={styles.emptySubtitle}>
          Wróć do listy wydarzeń i wybierz event, aby zobaczyć statystyki.
        </Text>
      </View>
    );
  }

  return (
    <View style={styles.wrapper}>
    <ScrollView
      style={styles.container}
      contentContainerStyle={styles.content}
      refreshControl={
        <RefreshControl
          refreshing={refreshing}
          onRefresh={() => {
            setRefreshing(true);
            load();
          }}
          tintColor="#0d9488"
        />
      }
    >
      <View style={styles.progressCard}>
        <Text style={styles.progressPct}>{pct}%</Text>
        <Text style={styles.progressLabel}>uczestników odznaczonych</Text>
        <View style={styles.progressBar}>
          <View style={[styles.progressFill, { width: `${pct}%` }]} />
        </View>
      </View>

      <View style={styles.row}>
        <StatCard
          icon="checkmark-circle"
          iconColor="#059669"
          label="Odznaczeni"
          value={stats.checked_in}
        />
        <StatCard
          icon="time-outline"
          iconColor="#d97706"
          label="Oczekujący"
          value={stats.not_checked_in}
        />
      </View>

      <View style={styles.row}>
        <StatCard
          icon="people"
          iconColor="#0d9488"
          label="Łącznie z QR"
          value={stats.total_with_qr}
        />
        <StatCard
          icon="cloud-upload-outline"
          iconColor={pendingSync > 0 ? "#dc2626" : "#059669"}
          label="Do synchronizacji"
          value={pendingSync}
        />
      </View>

      {pendingSync > 0 && (
        <View style={styles.syncCard}>
          <Ionicons name="cloud-offline-outline" size={20} color="#d97706" />
          <Text style={styles.syncText}>
            {pendingSync} check-in(ów) oczekuje na synchronizację
          </Text>
          {syncing ? (
            <ActivityIndicator color="#0d9488" size="small" />
          ) : (
            <Text style={styles.syncAction} onPress={handleSync}>
              Synchronizuj
            </Text>
          )}
        </View>
      )}

      {serverStats?.scanners && serverStats.scanners.length > 0 && (
        <View style={styles.scannersCard}>
          <Text style={styles.scannersTitle}>Skanery</Text>
          {serverStats.scanners.map((s, i) => (
            <View key={i} style={styles.scannerRow}>
              <Ionicons name="phone-portrait-outline" size={16} color="#64748b" />
              <Text style={styles.scannerEmail}>{s.scanned_by}</Text>
              <Text style={styles.scannerCount}>{s.scan_count}</Text>
            </View>
          ))}
        </View>
      )}

      {!serverStats && (
        <View style={styles.offlineNote}>
          <Ionicons name="cloud-offline-outline" size={16} color="#94a3b8" />
          <Text style={styles.offlineText}>
            Dane lokalne — brak połączenia z serwerem
          </Text>
        </View>
      )}
    </ScrollView>
    <SyncStatusBar eventId={eventId} onManualSync={handleSync} />
    </View>
  );
}

function StatCard({
  icon,
  iconColor,
  label,
  value,
}: {
  icon: string;
  iconColor: string;
  label: string;
  value: number;
}) {
  return (
    <View style={styles.statCard}>
      <Ionicons name={icon as any} size={24} color={iconColor} />
      <Text style={styles.statValue}>{value}</Text>
      <Text style={styles.statLabel}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: { flex: 1, backgroundColor: "#f8fafc" },
  container: { flex: 1, backgroundColor: "#f8fafc" },
  content: { padding: 16 },
  emptyContainer: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: 28,
    backgroundColor: "#f8fafc",
  },
  emptyTitle: {
    marginTop: 12,
    fontSize: 18,
    fontWeight: "600",
    color: "#1e293b",
  },
  emptySubtitle: {
    marginTop: 8,
    textAlign: "center",
    color: "#64748b",
    fontSize: 14,
  },
  progressCard: {
    backgroundColor: "#fff",
    borderRadius: 16,
    padding: 24,
    alignItems: "center",
    marginBottom: 16,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.06,
    shadowRadius: 8,
    elevation: 3,
  },
  progressPct: { fontSize: 48, fontWeight: "bold", color: "#0d9488" },
  progressLabel: { fontSize: 14, color: "#64748b", marginTop: 4 },
  progressBar: {
    width: "100%",
    height: 8,
    backgroundColor: "#e2e8f0",
    borderRadius: 4,
    marginTop: 16,
    overflow: "hidden",
  },
  progressFill: {
    height: "100%",
    backgroundColor: "#0d9488",
    borderRadius: 4,
  },
  row: { flexDirection: "row", gap: 12, marginBottom: 12 },
  statCard: {
    flex: 1,
    backgroundColor: "#fff",
    borderRadius: 12,
    padding: 16,
    alignItems: "center",
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.04,
    shadowRadius: 4,
    elevation: 1,
  },
  statValue: { fontSize: 28, fontWeight: "bold", color: "#1e293b", marginTop: 8 },
  statLabel: { fontSize: 12, color: "#64748b", marginTop: 4 },
  syncCard: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "#fffbeb",
    borderRadius: 10,
    padding: 14,
    marginBottom: 12,
    gap: 8,
    borderWidth: 1,
    borderColor: "#fde68a",
  },
  syncText: { flex: 1, fontSize: 13, color: "#92400e" },
  syncAction: { fontSize: 14, color: "#0d9488", fontWeight: "600" },
  scannersCard: {
    backgroundColor: "#fff",
    borderRadius: 12,
    padding: 16,
    marginTop: 4,
  },
  scannersTitle: { fontSize: 15, fontWeight: "600", color: "#1e293b", marginBottom: 12 },
  scannerRow: {
    flexDirection: "row",
    alignItems: "center",
    paddingVertical: 8,
    gap: 8,
    borderBottomWidth: 1,
    borderBottomColor: "#f1f5f9",
  },
  scannerEmail: { flex: 1, fontSize: 13, color: "#64748b" },
  scannerCount: { fontSize: 15, fontWeight: "600", color: "#1e293b" },
  offlineNote: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    padding: 12,
    gap: 6,
    marginTop: 8,
  },
  offlineText: { fontSize: 13, color: "#94a3b8" },
});
