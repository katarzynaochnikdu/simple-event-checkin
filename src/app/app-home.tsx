import { useEffect, useState } from "react";
import { View, Text, StyleSheet, TouchableOpacity, ScrollView } from "react-native";
import { router } from "expo-router";
import { Ionicons } from "@expo/vector-icons";
import { getUser, logout } from "../lib/auth";
import type { User } from "../types";

export default function AppHomeScreen() {
  const [user, setUser] = useState<User | null>(null);

  useEffect(() => {
    getUser().then(setUser);
  }, []);

  const initials = user
    ? `${user.first_name?.[0] ?? ""}${user.last_name?.[0] ?? ""}`.toUpperCase() || user.email[0].toUpperCase()
    : "?";

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      {/* Top user bar */}
      {user && (
        <View style={styles.userBar}>
          <View style={styles.avatar}>
            <Text style={styles.avatarText}>{initials}</Text>
          </View>
          <View style={styles.userInfo}>
            <Text style={styles.userName}>
              {user.first_name && user.last_name
                ? `${user.first_name} ${user.last_name}`
                : user.email}
            </Text>
            <Text style={styles.userRole}>{user.role}</Text>
          </View>
          <TouchableOpacity
            style={styles.logoutBtn}
            onPress={() =>
              logout().then(() => router.replace("/login"))
            }
          >
            <Ionicons name="log-out-outline" size={22} color="#94a3b8" />
          </TouchableOpacity>
        </View>
      )}

      <Text style={styles.greeting}>Witaj w Medidesk Check-in</Text>

      {/* Main tiles */}
      <View style={styles.tilesGrid}>
        <MainTile
          icon="calendar"
          label="Wydarzenia"
          sublabel="Wybierz event i zacznij check-in"
          color="#0d9488"
          bg="#f0fdfa"
          onPress={() => router.push("/events")}
        />
        <MainTile
          icon="person-circle"
          label="Profil"
          sublabel="Twoje dane i ustawienia"
          color="#7c3aed"
          bg="#f5f3ff"
          onPress={() => router.push("/profile")}
        />
      </View>
    </ScrollView>
  );
}

function MainTile({
  icon,
  label,
  sublabel,
  color,
  bg,
  onPress,
}: {
  icon: string;
  label: string;
  sublabel: string;
  color: string;
  bg: string;
  onPress: () => void;
}) {
  return (
    <TouchableOpacity
      style={[styles.tile, { backgroundColor: bg }]}
      onPress={onPress}
      activeOpacity={0.75}
    >
      <View style={[styles.tileIconWrap, { backgroundColor: color }]}>
        <Ionicons name={icon as any} size={36} color="#fff" />
      </View>
      <Text style={[styles.tileLabel, { color }]}>{label}</Text>
      <Text style={styles.tileSublabel}>{sublabel}</Text>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#f8fafc" },
  content: { padding: 20, paddingBottom: 40 },

  userBar: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "#fff",
    borderRadius: 16,
    padding: 14,
    marginBottom: 24,
    gap: 12,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.06,
    shadowRadius: 8,
    elevation: 3,
  },
  avatar: {
    width: 46,
    height: 46,
    borderRadius: 23,
    backgroundColor: "#0d9488",
    alignItems: "center",
    justifyContent: "center",
  },
  avatarText: { color: "#fff", fontSize: 18, fontWeight: "700" },
  userInfo: { flex: 1 },
  userName: { fontSize: 15, fontWeight: "700", color: "#1e293b" },
  userRole: { fontSize: 12, color: "#94a3b8", marginTop: 2 },
  logoutBtn: { padding: 6 },

  greeting: {
    fontSize: 13,
    color: "#94a3b8",
    fontWeight: "600",
    textTransform: "uppercase",
    letterSpacing: 0.8,
    marginBottom: 16,
    marginLeft: 2,
  },

  tilesGrid: {
    flexDirection: "row",
    gap: 14,
  },
  tile: {
    flex: 1,
    borderRadius: 20,
    padding: 22,
    alignItems: "flex-start",
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.06,
    shadowRadius: 8,
    elevation: 3,
    minHeight: 180,
    justifyContent: "space-between",
  },
  tileIconWrap: {
    width: 64,
    height: 64,
    borderRadius: 18,
    alignItems: "center",
    justifyContent: "center",
    marginBottom: 16,
  },
  tileLabel: { fontSize: 20, fontWeight: "800" },
  tileSublabel: { fontSize: 12, color: "#94a3b8", marginTop: 4, lineHeight: 18 },
});
