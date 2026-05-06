import { useState, useEffect, useCallback } from "react";
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  RefreshControl,
  TouchableOpacity,
  ActivityIndicator,
  Platform,
} from "react-native";
import { router } from "expo-router";
import { Ionicons } from "@expo/vector-icons";
import { useEvent } from "../../contexts/EventContext";
import { getLocalStats, getUnsyncedCheckins } from "../../lib/db";
import { fetchTicketClasses } from "../../lib/api";
import { triggerSync } from "../../lib/sync/SyncEngine";
import { WalkinRepository } from "../../lib/repositories/WalkinRepository";
import CircularProgress from "../../components/CircularProgress";
import WalkinFormModal, { type WalkinFormData } from "../../components/WalkinFormModal";
import type { TicketClass } from "../../types";

export default function OverviewScreen() {
  const { event } = useEvent();
  const eventId = event?.event_id;

  const [stats, setStats] = useState({ total: 0, checkedIn: 0 });
  const [pendingSync, setPendingSync] = useState(0);
  const [refreshing, setRefreshing] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [walkinVisible, setWalkinVisible] = useState(false);
  const [ticketClasses, setTicketClasses] = useState<TicketClass[]>([]);

  const load = useCallback(async () => {
    if (!eventId) return;
    const local = await getLocalStats(eventId);
    setStats(local);
    const unsynced = await getUnsyncedCheckins();
    setPendingSync(unsynced.length);
    setRefreshing(false);
  }, [eventId]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (!eventId) return;
    WalkinRepository.getTicketClasses(eventId).then(setTicketClasses);
    fetchTicketClasses(eventId)
      .then((classes) => {
        WalkinRepository.cacheTicketClasses(eventId, classes);
        setTicketClasses(classes);
      })
      .catch(() => {});
  }, [eventId]);

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

  async function handleWalkinSubmit(formData: WalkinFormData, checkInNow: boolean) {
    if (!eventId) return;
    await WalkinRepository.create({
      event_id: eventId,
      first_name: formData.first_name.trim(),
      last_name: formData.last_name.trim(),
      email: formData.email.trim() || undefined,
      phone: formData.phone.trim() || undefined,
      company: formData.company.trim() || undefined,
      ticket_class_id: formData.ticket_class_id || undefined,
      ticket_name: formData.ticket_name || undefined,
      notes: formData.notes.trim() || undefined,
      check_in_immediately: checkInNow,
    });
    await load();
  }

  if (!event) {
    return (
      <View style={styles.empty}>
        <Ionicons name="calendar-outline" size={52} color="#94a3b8" />
        <Text style={styles.emptyText}>Nie wybrano wydarzenia</Text>
        <TouchableOpacity style={styles.emptyBtn} onPress={() => router.replace("/events")}>
          <Text style={styles.emptyBtnText}>Wybierz wydarzenie</Text>
        </TouchableOpacity>
      </View>
    );
  }

  const pct = stats.total > 0 ? Math.round((stats.checkedIn / stats.total) * 100) : 0;
  const waiting = stats.total - stats.checkedIn;

  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={styles.content}
      refreshControl={
        <RefreshControl refreshing={refreshing} onRefresh={() => { setRefreshing(true); load(); }} tintColor="#0d9488" />
      }
    >
      {/* Event card */}
      <View style={styles.eventCard}>
        <View style={styles.eventBadge}>
          <Ionicons name="calendar" size={18} color="#0d9488" />
        </View>
        <View style={styles.eventInfo}>
          <Text style={styles.eventName} numberOfLines={2}>{event.event_name}</Text>
          {event.start_date ? <Text style={styles.eventMeta}>{event.start_date}</Text> : null}
          {event.venue ? <Text style={styles.eventMeta}>{event.venue}</Text> : null}
        </View>
      </View>

      {/* Progress ring */}
      <View style={styles.progressCard}>
        <CircularProgress
          size={140}
          strokeWidth={12}
          progress={pct}
          color="#0d9488"
          label="odznaczonych"
        />
        <View style={styles.progressStats}>
          <View style={styles.progressStat}>
            <Text style={styles.progressStatValue}>{stats.checkedIn}</Text>
            <Text style={styles.progressStatLabel}>zameldowanych</Text>
          </View>
          <View style={styles.progressDivider} />
          <View style={styles.progressStat}>
            <Text style={[styles.progressStatValue, { color: "#94a3b8" }]}>{waiting}</Text>
            <Text style={styles.progressStatLabel}>oczekujących</Text>
          </View>
          <View style={styles.progressDivider} />
          <View style={styles.progressStat}>
            <Text style={styles.progressStatValue}>{stats.total}</Text>
            <Text style={styles.progressStatLabel}>łącznie</Text>
          </View>
        </View>
      </View>

      {/* Alert strip — only when pending > 0 */}
      {pendingSync > 0 && (
        <View style={styles.alertStrip}>
          <Ionicons name="cloud-upload-outline" size={16} color="#92400e" />
          <Text style={styles.alertText}>
            {pendingSync} {pendingSync === 1 ? "check-in oczekuje" : "check-iny oczekują"} na synchronizację
          </Text>
        </View>
      )}

      {/* Quick actions */}
      <Text style={styles.sectionTitle}>Szybkie akcje</Text>
      <View style={styles.actionsGrid}>
        <QuickAction
          icon="qr-code"
          label="Skanuj"
          color="#0d9488"
          bg="#f0fdfa"
          onPress={() => router.push("/(tabs)/operations")}
        />
        <QuickAction
          icon="person-add"
          label="Walk-in"
          color="#7c3aed"
          bg="#f5f3ff"
          onPress={() => setWalkinVisible(true)}
        />
        <QuickAction
          icon="people"
          label="Uczestnicy"
          color="#2563eb"
          bg="#eff6ff"
          onPress={() => router.push("/(tabs)/people")}
        />
        <QuickAction
          icon={syncing ? "hourglass-outline" : "sync-outline"}
          label={syncing ? "Sync..." : "Sync"}
          color="#f59e0b"
          bg="#fffbeb"
          onPress={handleSync}
          disabled={syncing}
        />
      </View>

      <WalkinFormModal
        visible={walkinVisible}
        eventId={eventId}
        ticketClasses={ticketClasses}
        onClose={() => setWalkinVisible(false)}
        onSubmit={handleWalkinSubmit}
      />
    </ScrollView>
  );
}

function QuickAction({
  icon,
  label,
  color,
  bg,
  onPress,
  disabled,
}: {
  icon: string;
  label: string;
  color: string;
  bg: string;
  onPress: () => void;
  disabled?: boolean;
}) {
  return (
    <TouchableOpacity
      style={[styles.actionCard, { backgroundColor: bg }, disabled && styles.actionDisabled]}
      onPress={onPress}
      activeOpacity={0.75}
      disabled={disabled}
    >
      <View style={[styles.actionIcon, { backgroundColor: color }]}>
        <Ionicons name={icon as any} size={26} color="#fff" />
      </View>
      <Text style={[styles.actionLabel, { color }]}>{label}</Text>
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
    padding: 32,
  },
  emptyText: { marginTop: 12, fontSize: 16, color: "#94a3b8", marginBottom: 20 },
  emptyBtn: {
    backgroundColor: "#0d9488",
    borderRadius: 10,
    paddingHorizontal: 24,
    paddingVertical: 12,
  },
  emptyBtnText: { color: "#fff", fontWeight: "600", fontSize: 15 },

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
  eventMeta: { fontSize: 13, color: "#64748b", marginTop: 3 },

  progressCard: {
    backgroundColor: "#fff",
    borderRadius: 16,
    padding: 20,
    marginBottom: 12,
    flexDirection: "row",
    alignItems: "center",
    gap: 20,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.06,
    shadowRadius: 8,
    elevation: 3,
  },
  progressStats: {
    flex: 1,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-around",
  },
  progressStat: { alignItems: "center" },
  progressStatValue: { fontSize: 24, fontWeight: "800", color: "#1e293b" },
  progressStatLabel: { fontSize: 11, color: "#94a3b8", marginTop: 2, textAlign: "center" },
  progressDivider: { width: 1, height: 36, backgroundColor: "#f1f5f9" },

  alertStrip: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "#fef3c7",
    borderRadius: 10,
    padding: 12,
    marginBottom: 12,
    gap: 8,
    borderWidth: 1,
    borderColor: "#fde68a",
  },
  alertText: { flex: 1, fontSize: 13, color: "#92400e", fontWeight: "500" },

  sectionTitle: {
    fontSize: 12,
    fontWeight: "600",
    color: "#94a3b8",
    textTransform: "uppercase",
    letterSpacing: 0.8,
    marginBottom: 10,
    marginLeft: 2,
    marginTop: 4,
  },

  actionsGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 12,
  },
  actionCard: {
    width: "47%",
    borderRadius: 14,
    padding: 16,
    alignItems: "flex-start",
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.04,
    shadowRadius: 4,
    elevation: 1,
  },
  actionDisabled: { opacity: 0.6 },
  actionIcon: {
    width: 48,
    height: 48,
    borderRadius: 12,
    alignItems: "center",
    justifyContent: "center",
    marginBottom: 10,
  },
  actionLabel: { fontSize: 15, fontWeight: "700" },
});
