import { View, Text, TouchableOpacity, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import type { CheckinResult, ScanMode } from "../types";

interface Props {
  result: CheckinResult | null;
  onDismiss: () => void;
  scanMode?: ScanMode;
}

export default function ScanResult({ result, onDismiss, scanMode = "checkin" }: Props) {
  if (!result) return null;

  const { bgColor, icon, title, subtitle } = resolveDisplay(result, scanMode);
  const p = result.participant;

  return (
    <View style={[styles.container, { backgroundColor: bgColor }]}>
      <Ionicons name={icon as any} size={80} color="#fff" />
      <Text style={styles.title}>{title}</Text>
      <Text style={styles.subtitle}>{subtitle}</Text>

      {p && (
        <View style={styles.participantCard}>
          <Text style={styles.participantName}>
            {p.first_name} {p.last_name}
          </Text>
          {p.company ? (
            <Text style={styles.participantDetail}>{p.company}</Text>
          ) : null}
          {p.ticket_name ? (
            <Text style={styles.participantDetail}>{p.ticket_name}</Text>
          ) : null}
          {p.email ? (
            <Text style={styles.participantEmail}>{p.email}</Text>
          ) : null}
        </View>
      )}

      <TouchableOpacity style={styles.button} onPress={onDismiss}>
        <Ionicons name="scan-outline" size={20} color={bgColor} />
        <Text style={[styles.buttonText, { color: bgColor }]}>Skanuj następny</Text>
      </TouchableOpacity>
    </View>
  );
}

function resolveDisplay(
  result: CheckinResult,
  scanMode: ScanMode
): { bgColor: string; icon: string; title: string; subtitle: string } {
  const isError = !result.success;

  if (isError) {
    return {
      bgColor: "#dc2626",
      icon: "close-circle",
      title: "Nie znaleziono",
      subtitle: result.error === "not_found"
        ? "Nie znaleziono uczestnika z tym kodem QR"
        : result.error || "Wystąpił błąd",
    };
  }

  // review mode — show current status without changing it
  if (scanMode === "review") {
    const alreadyIn = result.already_checked_in;
    return {
      bgColor: alreadyIn ? "#059669" : "#d97706",
      icon: alreadyIn ? "checkmark-circle" : "time-outline",
      title: alreadyIn ? "Zameldowany" : "Niezameldowany",
      subtitle: alreadyIn
        ? `Zameldowano: ${formatTime(result.checked_in_at)}`
        : "Uczestnik jeszcze się nie zameldował",
    };
  }

  // checkin mode (default)
  if (result.already_checked_in) {
    return {
      bgColor: "#d97706",
      icon: "alert-circle",
      title: "Już odznaczony",
      subtitle: `Odznaczony wcześniej: ${formatTime(result.checked_in_at)}`,
    };
  }
  return {
    bgColor: "#059669",
    icon: "checkmark-circle",
    title: "Zarejestrowano!",
    subtitle: "Uczestnik został pomyślnie odznaczony",
  };
}

function formatTime(ts: string | null | undefined): string {
  if (!ts) return "";
  try {
    const d = new Date(ts);
    return d.toLocaleTimeString("pl-PL", {
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch {
    return ts;
  }
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    padding: 32,
  },
  title: {
    fontSize: 28,
    fontWeight: "bold",
    color: "#fff",
    marginTop: 16,
    textAlign: "center",
  },
  subtitle: {
    fontSize: 15,
    color: "rgba(255,255,255,0.85)",
    marginTop: 8,
    textAlign: "center",
  },
  participantCard: {
    backgroundColor: "rgba(255,255,255,0.2)",
    borderRadius: 12,
    padding: 20,
    marginTop: 24,
    width: "100%",
    alignItems: "center",
  },
  participantName: {
    fontSize: 22,
    fontWeight: "700",
    color: "#fff",
    textAlign: "center",
  },
  participantDetail: {
    fontSize: 15,
    color: "rgba(255,255,255,0.9)",
    marginTop: 4,
    textAlign: "center",
  },
  participantEmail: {
    fontSize: 13,
    color: "rgba(255,255,255,0.7)",
    marginTop: 6,
    textAlign: "center",
  },
  button: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "#fff",
    borderRadius: 12,
    paddingHorizontal: 28,
    paddingVertical: 14,
    marginTop: 32,
    gap: 8,
  },
  buttonText: {
    fontSize: 17,
    fontWeight: "600",
  },
});
