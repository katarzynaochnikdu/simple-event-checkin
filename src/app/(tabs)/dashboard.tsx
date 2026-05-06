import { ScrollView, View, Text, StyleSheet, TouchableOpacity, ActivityIndicator } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useEvent } from "../../contexts/EventContext";
import { useDashboard } from "../../hooks/useDashboard";
import CircularProgress from "../../components/CircularProgress";
import KpiCard from "../../components/KpiCard";

export default function DashboardScreen() {
  const { event } = useEvent();
  const eventId = event?.event_id;
  const { data, loading, error, fromCache, refresh } = useDashboard(eventId);

  if (!eventId) {
    return (
      <View style={styles.emptyContainer}>
        <Ionicons name="bar-chart-outline" size={42} color="#94a3b8" />
        <Text style={styles.emptyTitle}>Nie wybrano wydarzenia</Text>
        <Text style={styles.emptySubtitle}>
          Wróć do listy wydarzeń i wybierz event, aby zobaczyć pulpit.
        </Text>
      </View>
    );
  }

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      {/* Header */}
      <View style={styles.header}>
        <View style={{ flex: 1 }}>
          <Text style={styles.headerTitle}>Pulpit</Text>
          <Text style={styles.headerSub} numberOfLines={1}>{event?.event_name}</Text>
        </View>
        <TouchableOpacity onPress={refresh} style={styles.refreshBtn} disabled={loading}>
          {loading ? (
            <ActivityIndicator size="small" color="#0d9488" />
          ) : (
            <Ionicons name="refresh-outline" size={22} color="#0d9488" />
          )}
        </TouchableOpacity>
      </View>

      {fromCache && data?.fetched_at && (
        <Text style={styles.cacheNote}>
          Dane z {new Date(data.fetched_at).toLocaleTimeString("pl-PL", { hour: "2-digit", minute: "2-digit" })}
        </Text>
      )}
      {error && !data && (
        <View style={styles.errorBanner}>
          <Ionicons name="alert-circle-outline" size={16} color="#dc2626" />
          <Text style={styles.errorText}>{error}</Text>
        </View>
      )}

      {data && (
        <>
          {/* Ring + main KPI */}
          <View style={styles.ringRow}>
            <CircularProgress
              size={130}
              strokeWidth={11}
              progress={data.check_in_rate}
              color="#0d9488"
              label="obecnych"
              sublabel={`${data.checked_in} / ${data.total_with_qr}`}
            />
            <View style={styles.ringStats}>
              <StatRow icon="people-outline" iconColor="#0d9488" label="Zarejestrowani" value={data.total_registered} />
              <StatRow icon="checkmark-circle-outline" iconColor="#059669" label="Odznaczeni" value={data.checked_in} />
              <StatRow icon="person-add-outline" iconColor="#7c3aed" label="Walk-in" value={data.walk_ins} />
            </View>
          </View>

          {/* KPI row */}
          <View style={styles.kpiRow}>
            <KpiCard
              icon="checkmark-done-outline"
              iconColor="#059669"
              iconBg="#f0fdf4"
              value={data.checked_in}
              label="Odznaczeni"
            />
            <View style={{ width: 10 }} />
            <KpiCard
              icon="person-add-outline"
              iconColor="#7c3aed"
              iconBg="#f5f3ff"
              value={data.walk_ins}
              label="Walk-in"
            />
          </View>

          {/* By ticket class */}
          {data.by_ticket_class.length > 0 && (
            <View style={styles.section}>
              <Text style={styles.sectionTitle}>Klasy biletów</Text>
              <View style={styles.card}>
                {data.by_ticket_class.map((tc, i) => {
                  const pct = tc.total > 0 ? (tc.checked_in / tc.total) * 100 : 0;
                  return (
                    <View key={i} style={[styles.tcRow, i > 0 && styles.tcRowBorder]}>
                      <View style={{ flex: 1 }}>
                        <Text style={styles.tcName}>{tc.ticket_name}</Text>
                        <View style={styles.barBg}>
                          <View style={[styles.barFill, { width: `${pct}%` as any }]} />
                        </View>
                      </View>
                      <Text style={styles.tcCount}>
                        {tc.checked_in}/{tc.total}
                      </Text>
                    </View>
                  );
                })}
              </View>
            </View>
          )}

          {/* Timeline */}
          {data.timeline.length > 0 && (
            <View style={styles.section}>
              <Text style={styles.sectionTitle}>Odznaczenia wg godziny</Text>
              <View style={styles.card}>
                <TimelineChart data={data.timeline} />
              </View>
            </View>
          )}

          {/* Top scanners */}
          {data.top_scanners.length > 0 && (
            <View style={styles.section}>
              <Text style={styles.sectionTitle}>Najaktywniejsze urządzenia</Text>
              <View style={styles.card}>
                {data.top_scanners.map((s, i) => (
                  <View key={i} style={[styles.scannerRow, i > 0 && styles.tcRowBorder]}>
                    <View style={styles.scannerNum}>
                      <Text style={styles.scannerNumText}>{i + 1}</Text>
                    </View>
                    <Text style={styles.scannerEmail} numberOfLines={1}>{s.email}</Text>
                    <Text style={styles.scannerCount}>{s.count} skan.</Text>
                  </View>
                ))}
              </View>
            </View>
          )}
        </>
      )}

      {loading && !data && (
        <View style={styles.loadingContainer}>
          <ActivityIndicator size="large" color="#0d9488" />
          <Text style={styles.loadingText}>Pobieranie danych...</Text>
        </View>
      )}
    </ScrollView>
  );
}

function StatRow({ icon, iconColor, label, value }: { icon: string; iconColor: string; label: string; value: number }) {
  return (
    <View style={styles.statRow}>
      <Ionicons name={icon as any} size={16} color={iconColor} />
      <Text style={styles.statLabel}>{label}</Text>
      <Text style={styles.statValue}>{value}</Text>
    </View>
  );
}

function TimelineChart({ data }: { data: { hour: string; count: number }[] }) {
  const max = Math.max(...data.map((d) => d.count), 1);
  return (
    <ScrollView horizontal showsHorizontalScrollIndicator={false}>
      <View style={styles.timelineWrap}>
        {data.map((d, i) => {
          const pct = d.count / max;
          return (
            <View key={i} style={styles.timelineBar}>
              <Text style={styles.timelineCount}>{d.count}</Text>
              <View style={styles.timelineBarBg}>
                <View style={[styles.timelineBarFill, { height: `${Math.max(pct * 100, 4)}%` as any }]} />
              </View>
              <Text style={styles.timelineHour}>{d.hour}</Text>
            </View>
          );
        })}
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#f8fafc" },
  content: { padding: 16, paddingBottom: 32 },
  emptyContainer: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: 28,
    backgroundColor: "#f8fafc",
  },
  emptyTitle: { marginTop: 12, fontSize: 18, fontWeight: "600", color: "#1e293b" },
  emptySubtitle: { marginTop: 8, textAlign: "center", color: "#64748b", fontSize: 14 },
  header: {
    flexDirection: "row",
    alignItems: "center",
    marginBottom: 8,
  },
  headerTitle: { fontSize: 22, fontWeight: "800", color: "#1e293b" },
  headerSub: { fontSize: 13, color: "#64748b", marginTop: 2 },
  refreshBtn: {
    width: 40,
    height: 40,
    alignItems: "center",
    justifyContent: "center",
  },
  cacheNote: { fontSize: 11, color: "#94a3b8", marginBottom: 8 },
  errorBanner: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    backgroundColor: "#fef2f2",
    borderRadius: 8,
    padding: 10,
    marginBottom: 12,
  },
  errorText: { fontSize: 13, color: "#dc2626", flex: 1 },
  ringRow: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "#fff",
    borderRadius: 16,
    padding: 20,
    marginBottom: 12,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.05,
    shadowRadius: 8,
    elevation: 2,
    gap: 20,
  },
  ringStats: { flex: 1, gap: 6 },
  statRow: { flexDirection: "row", alignItems: "center", gap: 6 },
  statLabel: { flex: 1, fontSize: 13, color: "#64748b" },
  statValue: { fontSize: 13, fontWeight: "700", color: "#1e293b" },
  kpiRow: { flexDirection: "row", marginBottom: 16 },
  section: { marginBottom: 16 },
  sectionTitle: {
    fontSize: 12,
    fontWeight: "600",
    color: "#94a3b8",
    textTransform: "uppercase",
    letterSpacing: 0.8,
    marginBottom: 8,
    marginLeft: 4,
  },
  card: {
    backgroundColor: "#fff",
    borderRadius: 14,
    padding: 14,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 4,
    elevation: 1,
  },
  tcRow: { flexDirection: "row", alignItems: "center", paddingVertical: 8, gap: 12 },
  tcRowBorder: { borderTopWidth: 1, borderTopColor: "#f1f5f9" },
  tcName: { fontSize: 13, color: "#1e293b", marginBottom: 4 },
  barBg: {
    height: 6,
    backgroundColor: "#f1f5f9",
    borderRadius: 3,
    overflow: "hidden",
  },
  barFill: { height: 6, backgroundColor: "#0d9488", borderRadius: 3 },
  tcCount: { fontSize: 13, fontWeight: "600", color: "#64748b", minWidth: 50, textAlign: "right" },
  timelineWrap: { flexDirection: "row", alignItems: "flex-end", height: 100, gap: 6, paddingVertical: 4 },
  timelineBar: { alignItems: "center", width: 36 },
  timelineCount: { fontSize: 10, color: "#64748b", marginBottom: 2 },
  timelineBarBg: {
    width: 24,
    height: 60,
    backgroundColor: "#f1f5f9",
    borderRadius: 4,
    justifyContent: "flex-end",
    overflow: "hidden",
  },
  timelineBarFill: { width: "100%", backgroundColor: "#0d9488", borderRadius: 4 },
  timelineHour: { fontSize: 9, color: "#94a3b8", marginTop: 3 },
  scannerRow: {
    flexDirection: "row",
    alignItems: "center",
    paddingVertical: 10,
    gap: 10,
  },
  scannerNum: {
    width: 26,
    height: 26,
    borderRadius: 13,
    backgroundColor: "#f1f5f9",
    alignItems: "center",
    justifyContent: "center",
  },
  scannerNumText: { fontSize: 12, fontWeight: "700", color: "#64748b" },
  scannerEmail: { flex: 1, fontSize: 13, color: "#1e293b" },
  scannerCount: { fontSize: 12, fontWeight: "600", color: "#0d9488" },
  loadingContainer: { paddingVertical: 48, alignItems: "center", gap: 12 },
  loadingText: { fontSize: 14, color: "#94a3b8" },
});
