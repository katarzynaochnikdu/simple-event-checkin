import { useState, useEffect, useCallback, useMemo } from "react";
import {
  View,
  Text,
  FlatList,
  TextInput,
  StyleSheet,
  RefreshControl,
  TouchableOpacity,
  Alert,
  Platform,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import {
  getLocalParticipants,
  markLocalCheckin,
  addOfflineCheckin,
} from "../../lib/db";
import { isOnline } from "../../lib/sync";
import { checkinOnline, fetchTicketClasses } from "../../lib/api";
import { useEvent } from "../../contexts/EventContext";
import { WalkinRepository } from "../../lib/repositories/WalkinRepository";
import ParticipantFilterBar, { type StatusFilter } from "../../components/ParticipantFilterBar";
import ParticipantProfileModal from "../../components/ParticipantProfileModal";
import WalkinFormModal, { type WalkinFormData } from "../../components/WalkinFormModal";
import type { Participant, TicketClass } from "../../types";

export default function ParticipantsScreen() {
  const { event } = useEvent();
  const eventId = event?.event_id;

  const [participants, setParticipants] = useState<Participant[]>([]);
  const [search, setSearch] = useState("");
  const [refreshing, setRefreshing] = useState(false);
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("all");
  const [ticketClassFilter, setTicketClassFilter] = useState<string | null>(null);
  const [selectedParticipant, setSelectedParticipant] = useState<Participant | null>(null);
  const [modalLoading, setModalLoading] = useState(false);
  const [walkinModalVisible, setWalkinModalVisible] = useState(false);
  const [ticketClasses, setTicketClasses] = useState<TicketClass[]>([]);

  const load = useCallback(async () => {
    if (!eventId) return;

    // Zwykli uczestnicy
    const regular = await getLocalParticipants(eventId);

    // Walk-iny — mapowane do kształtu Participant
    const walkins = await WalkinRepository.getAll(eventId);
    const walkinAsPart: Participant[] = walkins.map((w) => ({
      id: w.id,
      backstage_ticket_id: w.walk_in_code,
      first_name: w.first_name,
      last_name: w.last_name,
      email: w.email || "",
      company: w.company || "",
      ticket_class_id: w.ticket_class_id || "",
      ticket_name: w.ticket_name || "",
      status: w.status,
      attendance_status: w.status,
      event_order_id: "",
      checked_in_at: w.checked_in_at ?? null,
      is_walkin: true,
    }));

    // UNION posortowany alfabetycznie
    const all = [...regular, ...walkinAsPart].sort((a, b) =>
      `${a.last_name}${a.first_name}`.localeCompare(`${b.last_name}${b.first_name}`, "pl")
    );
    setParticipants(all);
    setRefreshing(false);
  }, [eventId]);

  // Ładowanie klas biletów (z cache + sieć)
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

  useEffect(() => {
    load();
  }, [load]);

  const ticketClassNames = useMemo(() => {
    const fromParticipants = participants
      .map((p) => p.ticket_name)
      .filter((n): n is string => !!n);
    const fromClasses = ticketClasses.map((tc) => tc.ticket_name);
    return [...new Set([...fromParticipants, ...fromClasses])].sort();
  }, [participants, ticketClasses]);

  const filtered = useMemo(() => {
    return participants.filter((p) => {
      if (statusFilter === "checked_in" && p.status !== "checked_in") return false;
      if (statusFilter === "not_checked_in" && p.status === "checked_in") return false;
      if (ticketClassFilter && p.ticket_name !== ticketClassFilter) return false;
      if (search.trim()) {
        const q = search.toLowerCase();
        return (
          (p.first_name || "").toLowerCase().includes(q) ||
          (p.last_name || "").toLowerCase().includes(q) ||
          (p.email || "").toLowerCase().includes(q) ||
          (p.company || "").toLowerCase().includes(q) ||
          (p.backstage_ticket_id || "").toLowerCase().includes(q)
        );
      }
      return true;
    });
  }, [participants, statusFilter, ticketClassFilter, search]);

  const checkedInCount = useMemo(
    () => participants.filter((p) => p.status === "checked_in").length,
    [participants]
  );

  async function handleWalkinSubmit(formData: WalkinFormData, checkInNow: boolean) {
    if (!eventId) return;
    const walkin = await WalkinRepository.create({
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

  async function handleCheckin(participant: Participant) {
    setModalLoading(true);
    try {
      const online = await isOnline();
      if (online) {
        const result = await checkinOnline(participant.backstage_ticket_id, eventId!);
        if (result.success) {
          await markLocalCheckin(participant.backstage_ticket_id, eventId!);
          await load();
          setSelectedParticipant((prev) =>
            prev
              ? { ...prev, status: "checked_in", checked_in_at: result.checked_in_at ?? null }
              : null
          );
        } else {
          Alert.alert("Błąd", result.error || "Nie udało się odznaczać uczestnika");
        }
      } else {
        await markLocalCheckin(participant.backstage_ticket_id, eventId!);
        await addOfflineCheckin(participant.backstage_ticket_id, eventId!, Platform.OS, "checkin");
        await load();
        setSelectedParticipant((prev) =>
          prev
            ? { ...prev, status: "checked_in", checked_in_at: new Date().toISOString() }
            : null
        );
      }
    } catch (e: any) {
      Alert.alert("Błąd", e?.message || "Nieznany błąd");
    } finally {
      setModalLoading(false);
    }
  }

  if (!eventId) {
    return (
      <View style={styles.emptyContainer}>
        <Ionicons name="calendar-outline" size={42} color="#94a3b8" />
        <Text style={styles.emptyTitle}>Nie wybrano wydarzenia</Text>
        <Text style={styles.emptySubtitle}>
          Wróć do listy wydarzeń i wybierz event, aby zobaczyć uczestników.
        </Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <View style={styles.searchBar}>
        <Ionicons name="search" size={18} color="#94a3b8" />
        <TextInput
          style={styles.searchInput}
          placeholder="Szukaj po imieniu, firmie, ID biletu..."
          placeholderTextColor="#94a3b8"
          value={search}
          onChangeText={setSearch}
          autoCapitalize="none"
          autoCorrect={false}
        />
        {search.length > 0 && (
          <TouchableOpacity onPress={() => setSearch("")}>
            <Ionicons name="close-circle" size={18} color="#94a3b8" />
          </TouchableOpacity>
        )}
      </View>

      <ParticipantFilterBar
        activeStatus={statusFilter}
        onStatusChange={setStatusFilter}
        ticketClasses={ticketClassNames}
        activeTicketClass={ticketClassFilter}
        onTicketClassChange={setTicketClassFilter}
      />

      <View style={styles.summaryBar}>
        <Text style={styles.summaryText}>
          {checkedInCount} / {participants.length} odznaczonych
        </Text>
        <Text style={styles.filterText}>
          {filtered.length !== participants.length ? `Wyniki: ${filtered.length}` : ""}
        </Text>
      </View>

      <FlatList
        data={filtered}
        keyExtractor={(item) => String(item.id)}
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
        contentContainerStyle={styles.list}
        ListEmptyComponent={
          <View style={styles.empty}>
            <Text style={styles.emptyText}>
              {search.trim() || statusFilter !== "all" || ticketClassFilter
                ? "Brak wyników"
                : "Brak uczestników"}
            </Text>
          </View>
        }
        renderItem={({ item }) => (
          <TouchableOpacity
            style={[styles.card, item.is_walkin && styles.walkinCard]}
            onPress={() => setSelectedParticipant(item)}
            activeOpacity={0.7}
          >
            <View
              style={[
                styles.statusDot,
                {
                  backgroundColor:
                    item.status === "checked_in" ? "#059669" : "#e2e8f0",
                },
              ]}
            />
            <View style={styles.cardContent}>
              <View style={styles.nameRow}>
                <Text style={styles.name}>
                  {item.first_name} {item.last_name}
                </Text>
                {item.is_walkin && (
                  <View style={styles.walkinChip}>
                    <Text style={styles.walkinChipText}>Walk-in</Text>
                  </View>
                )}
              </View>
              {item.company ? (
                <Text style={styles.detail}>{item.company}</Text>
              ) : null}
              {item.ticket_name ? (
                <Text style={styles.detail}>{item.ticket_name}</Text>
              ) : null}
              <Text style={styles.email}>{item.email}</Text>
            </View>
            {item.status === "checked_in" && (
              <Ionicons name="checkmark-circle" size={22} color="#059669" />
            )}
          </TouchableOpacity>
        )}
      />

      <ParticipantProfileModal
        participant={selectedParticipant}
        visible={!!selectedParticipant}
        onClose={() => setSelectedParticipant(null)}
        onCheckin={handleCheckin}
        loading={modalLoading}
      />

      {/* FAB — rejestracja walk-in */}
      <TouchableOpacity
        style={styles.fab}
        onPress={() => setWalkinModalVisible(true)}
        activeOpacity={0.85}
      >
        <Ionicons name="person-add" size={24} color="#fff" />
      </TouchableOpacity>

      <WalkinFormModal
        visible={walkinModalVisible}
        eventId={eventId}
        ticketClasses={ticketClasses}
        onClose={() => setWalkinModalVisible(false)}
        onSubmit={handleWalkinSubmit}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#f8fafc" },
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
  searchBar: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "#fff",
    margin: 12,
    marginBottom: 0,
    borderRadius: 10,
    paddingHorizontal: 12,
    borderWidth: 1,
    borderColor: "#e2e8f0",
  },
  searchInput: {
    flex: 1,
    paddingVertical: 12,
    paddingHorizontal: 8,
    fontSize: 15,
    color: "#1e293b",
  },
  summaryBar: {
    flexDirection: "row",
    justifyContent: "space-between",
    paddingHorizontal: 16,
    paddingVertical: 6,
  },
  summaryText: { fontSize: 13, color: "#64748b", fontWeight: "500" },
  filterText: { fontSize: 13, color: "#94a3b8" },
  list: { padding: 12, paddingTop: 4 },
  card: {
    backgroundColor: "#fff",
    borderRadius: 10,
    padding: 14,
    marginBottom: 8,
    flexDirection: "row",
    alignItems: "center",
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.04,
    shadowRadius: 2,
    elevation: 1,
  },
  walkinCard: { backgroundColor: "#faf5ff" },
  statusDot: {
    width: 10,
    height: 10,
    borderRadius: 5,
    marginRight: 12,
  },
  cardContent: { flex: 1 },
  nameRow: { flexDirection: "row", alignItems: "center", gap: 6 },
  name: { fontSize: 15, fontWeight: "600", color: "#1e293b" },
  walkinChip: {
    backgroundColor: "#ede9fe",
    borderRadius: 6,
    paddingHorizontal: 6,
    paddingVertical: 2,
  },
  walkinChipText: { fontSize: 10, fontWeight: "600", color: "#7c3aed" },
  detail: { fontSize: 13, color: "#64748b", marginTop: 2 },
  email: { fontSize: 12, color: "#94a3b8", marginTop: 2 },
  empty: { alignItems: "center", padding: 32 },
  emptyText: { color: "#94a3b8", fontSize: 15 },
  fab: {
    position: "absolute",
    bottom: 20,
    right: 20,
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: "#7c3aed",
    alignItems: "center",
    justifyContent: "center",
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.2,
    shadowRadius: 8,
    elevation: 6,
  },
});
