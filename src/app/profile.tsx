import { useEffect, useState } from "react";
import { View, Text, StyleSheet, ScrollView } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { getUser } from "../lib/auth";
import type { User } from "../types";

export default function ProfileScreen() {
  const [user, setUser] = useState<User | null>(null);

  useEffect(() => {
    getUser().then(setUser);
  }, []);

  if (!user) return <View style={styles.container} />;

  const initials = `${user.first_name?.[0] ?? ""}${user.last_name?.[0] ?? ""}`.toUpperCase()
    || user.email[0].toUpperCase();

  const fullName =
    user.first_name && user.last_name
      ? `${user.first_name} ${user.last_name}`
      : user.email;

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      {/* Avatar */}
      <View style={styles.avatarSection}>
        <View style={styles.avatar}>
          <Text style={styles.avatarText}>{initials}</Text>
        </View>
        <Text style={styles.fullName}>{fullName}</Text>
        <View style={styles.rolePill}>
          <Text style={styles.roleText}>{user.role}</Text>
        </View>
      </View>

      {/* Info cards */}
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Dane konta</Text>
        <View style={styles.card}>
          <InfoRow icon="person-outline" label="Imię i nazwisko" value={fullName} />
          <Divider />
          <InfoRow icon="mail-outline" label="Email" value={user.email} />
          <Divider />
          <InfoRow icon="shield-outline" label="Typ profilu" value={user.role} />
        </View>
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Firma</Text>
        <View style={styles.card}>
          <InfoRow icon="business-outline" label="Nazwa firmy" value={null} />
          <Divider />
          <InfoRow icon="card-outline" label="NIP" value={null} />
          <Divider />
          <InfoRow icon="briefcase-outline" label="Typ firmy" value={null} />
        </View>
      </View>
    </ScrollView>
  );
}

function InfoRow({
  icon,
  label,
  value,
}: {
  icon: string;
  label: string;
  value: string | null | undefined;
}) {
  return (
    <View style={styles.row}>
      <View style={styles.rowIcon}>
        <Ionicons name={icon as any} size={18} color="#64748b" />
      </View>
      <View style={styles.rowContent}>
        <Text style={styles.rowLabel}>{label}</Text>
        <Text style={[styles.rowValue, !value && styles.rowValueEmpty]}>
          {value || "—"}
        </Text>
      </View>
    </View>
  );
}

function Divider() {
  return <View style={styles.divider} />;
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#f8fafc" },
  content: { padding: 20, paddingBottom: 40 },

  avatarSection: {
    alignItems: "center",
    paddingVertical: 28,
  },
  avatar: {
    width: 88,
    height: 88,
    borderRadius: 44,
    backgroundColor: "#0d9488",
    alignItems: "center",
    justifyContent: "center",
    marginBottom: 14,
    shadowColor: "#0d9488",
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 8,
    elevation: 6,
  },
  avatarText: { color: "#fff", fontSize: 32, fontWeight: "800" },
  fullName: { fontSize: 22, fontWeight: "700", color: "#1e293b", marginBottom: 8 },
  rolePill: {
    backgroundColor: "#f0fdfa",
    borderRadius: 20,
    paddingHorizontal: 14,
    paddingVertical: 5,
    borderWidth: 1,
    borderColor: "#ccfbf1",
  },
  roleText: { fontSize: 13, color: "#0d9488", fontWeight: "600" },

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
    overflow: "hidden",
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.04,
    shadowRadius: 4,
    elevation: 1,
  },
  row: {
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: 16,
    paddingVertical: 14,
    gap: 12,
  },
  rowIcon: {
    width: 34,
    height: 34,
    borderRadius: 8,
    backgroundColor: "#f8fafc",
    alignItems: "center",
    justifyContent: "center",
  },
  rowContent: { flex: 1 },
  rowLabel: { fontSize: 11, color: "#94a3b8", fontWeight: "500", marginBottom: 2 },
  rowValue: { fontSize: 15, color: "#1e293b", fontWeight: "500" },
  rowValueEmpty: { color: "#cbd5e1" },
  divider: { height: 1, backgroundColor: "#f1f5f9", marginLeft: 62 },
});
