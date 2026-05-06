import {
  Modal,
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ActivityIndicator,
  ScrollView,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import type { Participant } from "../types";

interface Props {
  participant: Participant | null;
  visible: boolean;
  onClose: () => void;
  onCheckin: (participant: Participant) => Promise<void>;
  loading?: boolean;
}

export default function ParticipantProfileModal({
  participant,
  visible,
  onClose,
  onCheckin,
  loading = false,
}: Props) {
  if (!participant) return null;

  const statusColor = participant.status === "checked_in" ? "#059669" : "#64748b";
  const statusLabel = participant.status === "checked_in" ? "Odznaczony" : "Nieodznaczony";
  const statusIcon = participant.status === "checked_in" ? "checkmark-circle" : "time-outline";

  return (
    <Modal
      visible={visible}
      transparent
      animationType="slide"
      onRequestClose={onClose}
    >
      <TouchableOpacity style={styles.overlay} activeOpacity={1} onPress={onClose} />
      <View style={styles.sheet}>
        <View style={styles.handle} />

        <ScrollView bounces={false} showsVerticalScrollIndicator={false}>
          {/* Header */}
          <View style={styles.header}>
            <View style={styles.avatar}>
              <Text style={styles.avatarText}>
                {(participant.first_name?.[0] ?? "?").toUpperCase()}
                {(participant.last_name?.[0] ?? "").toUpperCase()}
              </Text>
            </View>
            <View style={styles.headerInfo}>
              <Text style={styles.name} numberOfLines={1}>
                {participant.first_name} {participant.last_name}
              </Text>
              <View style={styles.statusRow}>
                <Ionicons name={statusIcon as any} size={14} color={statusColor} />
                <Text style={[styles.statusText, { color: statusColor }]}>
                  {statusLabel}
                </Text>
              </View>
            </View>
            <TouchableOpacity onPress={onClose} style={styles.closeBtn}>
              <Ionicons name="close" size={22} color="#64748b" />
            </TouchableOpacity>
          </View>

          {/* Details */}
          <View style={styles.section}>
            <DetailRow icon="ticket-outline" label="Bilet" value={participant.ticket_name} />
            <DetailRow icon="business-outline" label="Firma" value={participant.company} />
            <DetailRow icon="mail-outline" label="E-mail" value={participant.email} />
            {participant.backstage_ticket_id ? (
              <DetailRow icon="qr-code-outline" label="ID biletu" value={participant.backstage_ticket_id} mono />
            ) : null}
            {participant.checked_in_at ? (
              <DetailRow
                icon="time-outline"
                label="Odznaczono"
                value={formatDateTime(participant.checked_in_at)}
              />
            ) : null}
            {participant.is_walkin ? (
              <View style={styles.walkinChip}>
                <Text style={styles.walkinChipText}>Walk-in</Text>
              </View>
            ) : null}
          </View>

          {/* Actions */}
          <View style={styles.actions}>
            {loading ? (
              <ActivityIndicator color="#0d9488" size="large" style={{ marginVertical: 16 }} />
            ) : (
              <>
                {participant.status !== "checked_in" && (
                  <TouchableOpacity
                    style={[styles.actionBtn, styles.checkinBtn]}
                    onPress={() => onCheckin(participant)}
                  >
                    <Ionicons name="checkmark-circle-outline" size={20} color="#fff" />
                    <Text style={styles.actionBtnText}>Odznacz wejście</Text>
                  </TouchableOpacity>
                )}
              </>
            )}
          </View>
        </ScrollView>
      </View>
    </Modal>
  );
}

function DetailRow({
  icon,
  label,
  value,
  mono = false,
}: {
  icon: string;
  label: string;
  value?: string | null;
  mono?: boolean;
}) {
  if (!value) return null;
  return (
    <View style={detailStyles.row}>
      <Ionicons name={icon as any} size={16} color="#94a3b8" style={detailStyles.icon} />
      <View style={detailStyles.content}>
        <Text style={detailStyles.label}>{label}</Text>
        <Text style={[detailStyles.value, mono && detailStyles.mono]}>{value}</Text>
      </View>
    </View>
  );
}

function formatDateTime(ts: string | null): string {
  if (!ts) return "";
  try {
    const d = new Date(ts);
    return d.toLocaleString("pl-PL", {
      day: "2-digit",
      month: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch {
    return ts;
  }
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: "rgba(0,0,0,0.4)",
  },
  sheet: {
    backgroundColor: "#fff",
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    paddingBottom: 32,
    maxHeight: "80%",
  },
  handle: {
    width: 40,
    height: 4,
    backgroundColor: "#e2e8f0",
    borderRadius: 2,
    alignSelf: "center",
    marginTop: 12,
    marginBottom: 4,
  },
  header: {
    flexDirection: "row",
    alignItems: "center",
    padding: 16,
    gap: 12,
    borderBottomWidth: 1,
    borderBottomColor: "#f1f5f9",
  },
  avatar: {
    width: 48,
    height: 48,
    borderRadius: 24,
    backgroundColor: "#0d9488",
    alignItems: "center",
    justifyContent: "center",
  },
  avatarText: { fontSize: 18, fontWeight: "700", color: "#fff" },
  headerInfo: { flex: 1 },
  name: { fontSize: 17, fontWeight: "700", color: "#1e293b" },
  statusRow: { flexDirection: "row", alignItems: "center", gap: 4, marginTop: 3 },
  statusText: { fontSize: 12, fontWeight: "500" },
  closeBtn: { padding: 4 },
  section: { padding: 16 },
  walkinChip: {
    alignSelf: "flex-start",
    backgroundColor: "#ede9fe",
    borderRadius: 8,
    paddingHorizontal: 10,
    paddingVertical: 4,
    marginTop: 8,
  },
  walkinChipText: { fontSize: 12, fontWeight: "600", color: "#7c3aed" },
  actions: {
    paddingHorizontal: 16,
    paddingTop: 4,
    gap: 10,
  },
  actionBtn: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    borderRadius: 12,
    paddingVertical: 14,
    gap: 8,
  },
  checkinBtn: { backgroundColor: "#059669" },
  actionBtnText: { fontSize: 16, fontWeight: "600", color: "#fff" },
});

const detailStyles = StyleSheet.create({
  row: {
    flexDirection: "row",
    alignItems: "flex-start",
    paddingVertical: 8,
    borderBottomWidth: 1,
    borderBottomColor: "#f8fafc",
    gap: 10,
  },
  icon: { marginTop: 2 },
  content: { flex: 1 },
  label: { fontSize: 11, color: "#94a3b8", textTransform: "uppercase", letterSpacing: 0.5 },
  value: { fontSize: 14, color: "#1e293b", marginTop: 2 },
  mono: { fontFamily: "monospace", fontSize: 12, color: "#475569" },
});
