import { useState, useEffect } from "react";
import {
  View,
  Text,
  Modal,
  TouchableOpacity,
  StyleSheet,
  ActivityIndicator,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";

interface Props {
  visible: boolean;
  title?: string;
  subtitle?: string;
  error?: string | null;
  loading?: boolean;
  onConfirm: (pin: string) => void;
  onCancel: () => void;
}

export default function InHubPinModal({
  visible,
  title = "Wprowadź PIN",
  subtitle,
  error,
  loading = false,
  onConfirm,
  onCancel,
}: Props) {
  const [pin, setPin] = useState("");

  // Reset PIN when modal becomes visible or when error changes (let user retry)
  useEffect(() => {
    if (visible) setPin("");
  }, [visible]);

  useEffect(() => {
    if (error) setPin("");
  }, [error]);

  function handleDigit(digit: string) {
    if (loading) return;
    setPin((p) => (p.length >= 6 ? p : p + digit));
  }

  function handleDelete() {
    if (loading) return;
    setPin((p) => p.slice(0, -1));
  }

  function handleConfirm() {
    if (pin.length < 4 || loading) return;
    onConfirm(pin);
  }

  function handleCancel() {
    setPin("");
    onCancel();
  }

  return (
    <Modal visible={visible} transparent animationType="fade">
      <View style={styles.backdrop}>
        <View style={styles.card}>
          <Text style={styles.title}>{title}</Text>
          {subtitle ? <Text style={styles.subtitle}>{subtitle}</Text> : null}

          {/* PIN dots */}
          <View style={styles.dotsRow}>
            {Array.from({ length: 4 }).map((_, i) => (
              <View
                key={i}
                style={[styles.dot, i < pin.length && styles.dotFilled]}
              />
            ))}
          </View>

          {/* Error message */}
          {error ? (
            <View style={styles.errorRow}>
              <Ionicons name="alert-circle-outline" size={15} color="#dc2626" />
              <Text style={styles.errorText}>{error}</Text>
            </View>
          ) : null}

          {/* Keypad */}
          <View style={styles.keypad}>
            {(["1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "del"] as const).map(
              (key, idx) => {
                if (key === "") return <View key={`empty-${idx}`} style={styles.keyEmpty} />;
                if (key === "del") {
                  return (
                    <TouchableOpacity
                      key="del"
                      style={[styles.key, loading && styles.keyDisabled]}
                      onPress={handleDelete}
                      activeOpacity={0.6}
                    >
                      <Ionicons name="backspace-outline" size={22} color={loading ? "#cbd5e1" : "#1e293b"} />
                    </TouchableOpacity>
                  );
                }
                return (
                  <TouchableOpacity
                    key={key}
                    style={[styles.key, loading && styles.keyDisabled]}
                    onPress={() => handleDigit(key)}
                    activeOpacity={0.6}
                  >
                    <Text style={[styles.keyText, loading && styles.keyTextDisabled]}>{key}</Text>
                  </TouchableOpacity>
                );
              }
            )}
          </View>

          {/* Confirm button */}
          <TouchableOpacity
            style={[styles.confirmBtn, pin.length < 4 && styles.confirmBtnDisabled]}
            onPress={handleConfirm}
            disabled={pin.length < 4 || loading}
            activeOpacity={0.85}
          >
            {loading ? (
              <ActivityIndicator size="small" color="#fff" />
            ) : (
              <Text style={styles.confirmBtnText}>Zatwierdź</Text>
            )}
          </TouchableOpacity>

          <TouchableOpacity style={styles.cancelBtn} onPress={handleCancel} disabled={loading}>
            <Text style={styles.cancelText}>Anuluj</Text>
          </TouchableOpacity>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    backgroundColor: "rgba(0,0,0,0.6)",
    alignItems: "center",
    justifyContent: "center",
  },
  card: {
    backgroundColor: "#fff",
    borderRadius: 20,
    paddingVertical: 28,
    paddingHorizontal: 24,
    width: 300,
    alignItems: "center",
  },
  title: {
    fontSize: 18,
    fontWeight: "700",
    color: "#1e293b",
    marginBottom: 4,
  },
  subtitle: {
    fontSize: 13,
    color: "#64748b",
    marginBottom: 4,
    textAlign: "center",
  },
  dotsRow: {
    flexDirection: "row",
    gap: 14,
    marginVertical: 16,
  },
  dot: {
    width: 18,
    height: 18,
    borderRadius: 9,
    borderWidth: 2,
    borderColor: "#cbd5e1",
    backgroundColor: "transparent",
  },
  dotFilled: {
    backgroundColor: "#7c3aed",
    borderColor: "#7c3aed",
  },
  errorRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 5,
    backgroundColor: "#fef2f2",
    borderRadius: 8,
    paddingVertical: 6,
    paddingHorizontal: 10,
    marginBottom: 8,
    alignSelf: "stretch",
  },
  errorText: { fontSize: 13, color: "#dc2626", flex: 1 },
  keypad: {
    flexDirection: "row",
    flexWrap: "wrap",
    width: 228,
    gap: 10,
    justifyContent: "center",
    marginTop: 4,
  },
  key: {
    width: 68,
    height: 68,
    borderRadius: 34,
    backgroundColor: "#f1f5f9",
    alignItems: "center",
    justifyContent: "center",
  },
  keyEmpty: { width: 68, height: 68 },
  keyDisabled: { backgroundColor: "#f8fafc" },
  keyText: { fontSize: 24, fontWeight: "600", color: "#1e293b" },
  keyTextDisabled: { color: "#cbd5e1" },
  confirmBtn: {
    marginTop: 20,
    backgroundColor: "#7c3aed",
    borderRadius: 12,
    paddingVertical: 13,
    alignSelf: "stretch",
    alignItems: "center",
    minHeight: 48,
    justifyContent: "center",
  },
  confirmBtnDisabled: { backgroundColor: "#e2e8f0" },
  confirmBtnText: { color: "#fff", fontWeight: "700", fontSize: 16 },
  cancelBtn: {
    marginTop: 12,
    paddingVertical: 10,
    paddingHorizontal: 32,
  },
  cancelText: { fontSize: 15, color: "#64748b" },
});
