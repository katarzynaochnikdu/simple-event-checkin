import { View, Text, StyleSheet, ScrollView, TouchableOpacity } from "react-native";

export type StatusFilter = "all" | "checked_in" | "not_checked_in";

interface Props {
  activeStatus: StatusFilter;
  onStatusChange: (s: StatusFilter) => void;
  ticketClasses: string[];
  activeTicketClass: string | null;
  onTicketClassChange: (tc: string | null) => void;
}

const STATUS_OPTIONS: { key: StatusFilter; label: string; color: string }[] = [
  { key: "all", label: "Wszyscy", color: "#0d9488" },
  { key: "checked_in", label: "Odznaczeni", color: "#059669" },
  { key: "not_checked_in", label: "Oczekujący", color: "#d97706" },
];

export default function ParticipantFilterBar({
  activeStatus,
  onStatusChange,
  ticketClasses,
  activeTicketClass,
  onTicketClassChange,
}: Props) {
  return (
    <View style={styles.wrapper}>
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={styles.row}
      >
        {STATUS_OPTIONS.map((opt) => {
          const active = activeStatus === opt.key;
          return (
            <TouchableOpacity
              key={opt.key}
              style={[
                styles.pill,
                active && { backgroundColor: opt.color, borderColor: opt.color },
              ]}
              onPress={() => onStatusChange(opt.key)}
              activeOpacity={0.7}
            >
              <Text style={[styles.pillText, active && styles.pillTextActive]}>
                {opt.label}
              </Text>
            </TouchableOpacity>
          );
        })}

        {ticketClasses.length > 0 && (
          <View style={styles.divider} />
        )}

        {ticketClasses.map((tc) => {
          const active = activeTicketClass === tc;
          return (
            <TouchableOpacity
              key={tc}
              style={[
                styles.pill,
                active && { backgroundColor: "#7c3aed", borderColor: "#7c3aed" },
              ]}
              onPress={() => onTicketClassChange(active ? null : tc)}
              activeOpacity={0.7}
            >
              <Text style={[styles.pillText, active && styles.pillTextActive]}>
                {tc}
              </Text>
            </TouchableOpacity>
          );
        })}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: {
    backgroundColor: "#f8fafc",
    borderBottomWidth: 1,
    borderBottomColor: "#e2e8f0",
  },
  row: {
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: 12,
    paddingVertical: 8,
    gap: 6,
  },
  pill: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: "#e2e8f0",
    backgroundColor: "#fff",
  },
  pillText: { fontSize: 13, fontWeight: "500", color: "#64748b" },
  pillTextActive: { color: "#fff" },
  divider: {
    width: 1,
    height: 20,
    backgroundColor: "#e2e8f0",
    marginHorizontal: 4,
  },
});
