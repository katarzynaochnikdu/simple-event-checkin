import { View, Text, StyleSheet, TouchableOpacity } from "react-native";
import type { ScanMode } from "../types";

interface Props {
  mode: ScanMode;
  onChange: (mode: ScanMode) => void;
}

const MODES: { key: ScanMode; label: string }[] = [
  { key: "checkin", label: "Wejście" },
  { key: "review", label: "Podgląd" },
];

export default function ScannerModeSelector({ mode, onChange }: Props) {
  return (
    <View style={styles.container}>
      {MODES.map((m, i) => {
        const active = mode === m.key;
        return (
          <TouchableOpacity
            key={m.key}
            style={[
              styles.segment,
              i === 0 && styles.first,
              i === MODES.length - 1 && styles.last,
              active && getActiveStyle(m.key),
            ]}
            onPress={() => onChange(m.key)}
            activeOpacity={0.7}
          >
            <Text style={[styles.label, active && getActiveLabelStyle(m.key)]}>
              {m.label}
            </Text>
          </TouchableOpacity>
        );
      })}
    </View>
  );
}

function getActiveStyle(mode: ScanMode) {
  if (mode === "checkin") return styles.activeCheckin;
  return styles.activeReview;
}

function getActiveLabelStyle(mode: ScanMode) {
  if (mode === "checkin") return styles.activeLabelCheckin;
  return styles.activeLabelReview;
}

const styles = StyleSheet.create({
  container: {
    flexDirection: "row",
    backgroundColor: "#e2e8f0",
    borderRadius: 10,
    padding: 3,
    marginHorizontal: 16,
    marginBottom: 8,
  },
  segment: {
    flex: 1,
    paddingVertical: 8,
    alignItems: "center",
    borderRadius: 8,
  },
  first: { borderTopLeftRadius: 8, borderBottomLeftRadius: 8 },
  last: { borderTopRightRadius: 8, borderBottomRightRadius: 8 },
  label: { fontSize: 13, fontWeight: "500", color: "#64748b" },
  activeCheckin: { backgroundColor: "#059669" },
  activeReview: { backgroundColor: "#f59e0b" },
  activeLabelCheckin: { color: "#fff" },
  activeLabelReview: { color: "#fff" },
});
