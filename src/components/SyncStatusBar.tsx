import { View, Text, StyleSheet, ActivityIndicator, TouchableOpacity } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useSyncStatus } from "../hooks/useSyncStatus";

interface Props {
  eventId: string | undefined;
  onManualSync?: () => void;
}

export default function SyncStatusBar({ eventId, onManualSync }: Props) {
  const { state, lastSynced, pendingCount, errorMessage } = useSyncStatus();

  if (!eventId) return null;

  if (state === "syncing") {
    return (
      <View style={[styles.bar, styles.syncing]}>
        <ActivityIndicator size="small" color="#0d9488" />
        <Text style={styles.syncingText}>Synchronizuję...</Text>
      </View>
    );
  }

  if (state === "error") {
    return (
      <TouchableOpacity
        style={[styles.bar, styles.error]}
        onPress={onManualSync}
        activeOpacity={0.7}
      >
        <Ionicons name="cloud-offline-outline" size={14} color="#dc2626" />
        <Text style={styles.errorText}>
          {errorMessage || "Błąd synchronizacji"} — dotknij, aby spróbować
        </Text>
      </TouchableOpacity>
    );
  }

  // idle
  return (
    <View style={[styles.bar, styles.idle]}>
      <Ionicons
        name={pendingCount > 0 ? "cloud-upload-outline" : "checkmark-circle-outline"}
        size={14}
        color={pendingCount > 0 ? "#d97706" : "#059669"}
      />
      <Text style={styles.idleText}>
        {pendingCount > 0
          ? `${pendingCount} oczekuje na sync`
          : lastSynced
          ? `Zsynchronizowano o ${lastSynced}`
          : "Gotowy do synchronizacji"}
      </Text>
      {onManualSync && pendingCount > 0 && (
        <TouchableOpacity onPress={onManualSync} style={styles.syncButton}>
          <Text style={styles.syncButtonText}>Synchronizuj</Text>
        </TouchableOpacity>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  bar: {
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: 12,
    paddingVertical: 6,
    gap: 6,
  },
  syncing: {
    backgroundColor: "#f0fdfa",
    borderTopWidth: 1,
    borderTopColor: "#ccfbf1",
  },
  error: {
    backgroundColor: "#fef2f2",
    borderTopWidth: 1,
    borderTopColor: "#fecaca",
  },
  idle: {
    backgroundColor: "#f8fafc",
    borderTopWidth: 1,
    borderTopColor: "#e2e8f0",
  },
  syncingText: { fontSize: 12, color: "#0d9488" },
  errorText: { flex: 1, fontSize: 12, color: "#dc2626" },
  idleText: { flex: 1, fontSize: 12, color: "#64748b" },
  syncButton: {
    paddingHorizontal: 10,
    paddingVertical: 3,
    backgroundColor: "#0d9488",
    borderRadius: 6,
  },
  syncButtonText: { fontSize: 11, color: "#fff", fontWeight: "600" },
});
