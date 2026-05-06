import { useState, useEffect } from "react";
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  Alert,
  TextInput,
  Switch,
  ActivityIndicator,
} from "react-native";
import { useRouter } from "expo-router";
import { Ionicons } from "@expo/vector-icons";
import * as SecureStore from "expo-secure-store";
import { getUser, logout } from "../../lib/auth";
import { useEvent } from "../../contexts/EventContext";
import { fetchInHubConfig, saveInHubConfig } from "../../lib/api";
import { getUnsyncedCheckins } from "../../lib/db";
import { triggerSync } from "../../lib/sync/SyncEngine";
import type { User } from "../../types";

const INHUB_STORE_KEY_PREFIX = "inhub_active_";

export default function SystemScreen() {
  const router = useRouter();
  const { event, clearEvent, isInHubMode, exitInHubMode } = useEvent();
  const [user, setUser] = useState<User | null>(null);

  // Sync state
  const [pendingSync, setPendingSync] = useState(0);
  const [syncing, setSyncing] = useState(false);

  // InHub config state
  const [inHubPin, setInHubPin] = useState("");
  const [inHubPinConfirm, setInHubPinConfirm] = useState("");
  const [autoCheckin, setAutoCheckin] = useState(true);
  const [showSearch, setShowSearch] = useState(true);
  const [showWalkin, setShowWalkin] = useState(false);
  const [inHubLoading, setInHubLoading] = useState(false);
  const [inHubConfigExists, setInHubConfigExists] = useState(false);
  const [inHubSectionOpen, setInHubSectionOpen] = useState(false);

  useEffect(() => {
    getUser().then(setUser);
    getUnsyncedCheckins().then((rows) => setPendingSync(rows.length));
  }, []);

  useEffect(() => {
    if (event?.event_id && inHubSectionOpen) {
      fetchInHubConfig(event.event_id)
        .then((cfg) => {
          setInHubConfigExists(cfg.exists ?? false);
          if (cfg.exists) {
            setAutoCheckin(cfg.auto_checkin ?? true);
            setShowSearch(cfg.show_search ?? true);
            setShowWalkin(cfg.show_walkin ?? false);
          }
        })
        .catch(() => {});
    }
  }, [event?.event_id, inHubSectionOpen]);

  async function handleLogout() {
    Alert.alert("Wyloguj się", "Czy na pewno chcesz się wylogować?", [
      { text: "Anuluj", style: "cancel" },
      {
        text: "Wyloguj",
        style: "destructive",
        onPress: async () => {
          clearEvent();
          await logout();
          router.replace("/login");
        },
      },
    ]);
  }

  function handleChangeEvent() {
    clearEvent();
    router.replace("/events");
  }

  async function handleSaveInHubConfig() {
    if (!event?.event_id) return;
    if (!inHubPin || inHubPin.length < 4) {
      Alert.alert("Błąd", "PIN musi mieć co najmniej 4 cyfry.");
      return;
    }
    if (inHubPin !== inHubPinConfirm) {
      Alert.alert("Błąd", "Podane PINy nie są identyczne.");
      return;
    }
    setInHubLoading(true);
    try {
      const result = await saveInHubConfig(event.event_id, {
        pin: inHubPin,
        auto_checkin: autoCheckin,
        show_search: showSearch,
        show_walkin: showWalkin,
      });
      if (result.success) {
        setInHubConfigExists(true);
        setInHubPin("");
        setInHubPinConfirm("");
        Alert.alert("Zapisano", "Konfiguracja InHub została zapisana.");
      } else {
        Alert.alert("Błąd", result.error || "Nie udało się zapisać konfiguracji.");
      }
    } catch (e: any) {
      Alert.alert("Błąd", e?.message || "Błąd zapisu konfiguracji.");
    } finally {
      setInHubLoading(false);
    }
  }

  async function handleExitInHub() {
    if (!event?.event_id) return;
    await SecureStore.deleteItemAsync(`${INHUB_STORE_KEY_PREFIX}${event.event_id}`);
    exitInHubMode();
  }

  async function handleSync() {
    if (!event?.event_id) return;
    setSyncing(true);
    try {
      await triggerSync(event.event_id);
      const rows = await getUnsyncedCheckins();
      setPendingSync(rows.length);
    } finally {
      setSyncing(false);
    }
  }

  const initials = user
    ? `${user.first_name?.[0] ?? user.email[0]}`.toUpperCase()
    : "?";

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>

      {/* === Konto === */}
      <Text style={styles.sectionTitle}>Konto</Text>
      {user && (
        <View style={styles.card}>
          <View style={styles.profileRow}>
            <View style={styles.avatar}>
              <Text style={styles.avatarText}>{initials}</Text>
            </View>
            <View style={styles.profileInfo}>
              <Text style={styles.profileName}>
                {user.first_name && user.last_name
                  ? `${user.first_name} ${user.last_name}`
                  : user.email}
              </Text>
              <Text style={styles.profileEmail}>{user.email}</Text>
              <View style={styles.roleBadge}>
                <Text style={styles.roleText}>{user.role}</Text>
              </View>
            </View>
          </View>
          <TouchableOpacity style={styles.logoutBtn} onPress={handleLogout}>
            <Ionicons name="log-out-outline" size={18} color="#dc2626" />
            <Text style={styles.logoutBtnText}>Wyloguj się</Text>
          </TouchableOpacity>
        </View>
      )}

      {/* === Aktywne wydarzenie === */}
      {event && (
        <>
          <Text style={styles.sectionTitle}>Aktywne wydarzenie</Text>
          <View style={styles.card}>
            <View style={styles.eventRow}>
              <Ionicons name="calendar" size={20} color="#0d9488" />
              <Text style={styles.eventName} numberOfLines={2}>{event.event_name}</Text>
            </View>
            {event.start_date ? (
              <Text style={styles.eventMeta}>{event.start_date}</Text>
            ) : null}
            {event.venue ? (
              <Text style={styles.eventMeta}>{event.venue}</Text>
            ) : null}
            <TouchableOpacity style={styles.changeEventBtn} onPress={handleChangeEvent}>
              <Ionicons name="swap-horizontal-outline" size={16} color="#0d9488" />
              <Text style={styles.changeEventBtnText}>Zmień wydarzenie</Text>
            </TouchableOpacity>
          </View>
        </>
      )}

      {/* === Synchronizacja === */}
      <Text style={styles.sectionTitle}>Synchronizacja</Text>
      <View style={styles.card}>
        <View style={styles.syncRow}>
          <View style={styles.syncInfo}>
            <Text style={styles.syncLabel}>
              {pendingSync === 0
                ? "Wszystkie check-iny zsynchronizowane"
                : `${pendingSync} check-${pendingSync === 1 ? "in oczekuje" : "iny oczekują"} na sync`}
            </Text>
          </View>
          {pendingSync > 0 && (
            <View style={styles.syncBadge}>
              <Text style={styles.syncBadgeText}>{pendingSync}</Text>
            </View>
          )}
        </View>
        <TouchableOpacity
          style={[styles.syncBtn, syncing && styles.syncBtnDisabled]}
          onPress={handleSync}
          disabled={syncing}
          activeOpacity={0.85}
        >
          {syncing ? (
            <ActivityIndicator color="#fff" size="small" />
          ) : (
            <>
              <Ionicons name="sync-outline" size={16} color="#fff" />
              <Text style={styles.syncBtnText}>Synchronizuj ręcznie</Text>
            </>
          )}
        </TouchableOpacity>
      </View>

      {/* === Check-in / InHub === */}
      <Text style={styles.sectionTitle}>Check-in</Text>
      <View style={styles.card}>
        {isInHubMode && (
          <View style={styles.inHubActiveBar}>
            <Ionicons name="tv-outline" size={16} color="#7c3aed" />
            <Text style={styles.inHubActiveText}>Tryb InHub jest aktywny</Text>
            <TouchableOpacity onPress={handleExitInHub} style={styles.inHubExitBtn}>
              <Text style={styles.inHubExitBtnText}>Wyłącz</Text>
            </TouchableOpacity>
          </View>
        )}

        <TouchableOpacity
          style={styles.menuItem}
          onPress={() => setInHubSectionOpen((v) => !v)}
        >
          <Ionicons name="tv-outline" size={22} color="#1e293b" />
          <View style={styles.menuItemText}>
            <Text style={styles.menuItemLabel}>Tryb InHub</Text>
            <Text style={styles.menuItemDescription}>
              {inHubConfigExists ? "PIN skonfigurowany" : "Konfiguruj PIN InHub"}
            </Text>
          </View>
          <Ionicons
            name={inHubSectionOpen ? "chevron-up" : "chevron-forward"}
            size={18}
            color="#94a3b8"
          />
        </TouchableOpacity>

        {inHubSectionOpen && event && (
          <View style={styles.inHubConfig}>
            <Text style={styles.fieldLabel}>Nowy PIN (min. 4 cyfry)</Text>
            <TextInput
              style={styles.pinInput}
              value={inHubPin}
              onChangeText={setInHubPin}
              placeholder="••••"
              secureTextEntry
              keyboardType="number-pad"
              maxLength={6}
              placeholderTextColor="#94a3b8"
            />
            <Text style={styles.fieldLabel}>Powtórz PIN</Text>
            <TextInput
              style={styles.pinInput}
              value={inHubPinConfirm}
              onChangeText={setInHubPinConfirm}
              placeholder="••••"
              secureTextEntry
              keyboardType="number-pad"
              maxLength={6}
              placeholderTextColor="#94a3b8"
            />
            <SwitchRow
              label="Automatyczny check-in"
              sub="Check-in po skanowaniu QR bez potwierdzenia"
              value={autoCheckin}
              onValueChange={setAutoCheckin}
            />
            <SwitchRow
              label="Pokazuj wyszukiwarkę"
              value={showSearch}
              onValueChange={setShowSearch}
            />
            <SwitchRow
              label="Rejestracja walk-in"
              value={showWalkin}
              onValueChange={setShowWalkin}
            />
            <TouchableOpacity
              style={styles.saveInHubBtn}
              onPress={handleSaveInHubConfig}
              disabled={inHubLoading}
              activeOpacity={0.85}
            >
              {inHubLoading ? (
                <ActivityIndicator color="#fff" size="small" />
              ) : (
                <>
                  <Ionicons name="save-outline" size={18} color="#fff" />
                  <Text style={styles.saveInHubBtnText}>
                    {inHubConfigExists ? "Zaktualizuj konfigurację" : "Zapisz konfigurację"}
                  </Text>
                </>
              )}
            </TouchableOpacity>
          </View>
        )}
      </View>

      {/* === Drukarka === */}
      <Text style={styles.sectionTitle}>Drukarka</Text>
      <View style={styles.card}>
        <TouchableOpacity
          style={[styles.menuItem, styles.menuItemDisabled]}
          onPress={() => Alert.alert("Wkrótce", "Ustawienia drukarki będą dostępne wkrótce.")}
          disabled
        >
          <Ionicons name="print-outline" size={22} color="#cbd5e1" />
          <View style={styles.menuItemText}>
            <Text style={[styles.menuItemLabel, styles.menuItemLabelDisabled]}>
              Ustawienia drukarki
            </Text>
            <Text style={styles.menuItemDescription}>Konfiguracja drukarki identyfikatorów</Text>
          </View>
          <Ionicons name="chevron-forward" size={18} color="#e2e8f0" />
        </TouchableOpacity>
      </View>

    </ScrollView>
  );
}

function SwitchRow({
  label,
  sub,
  value,
  onValueChange,
}: {
  label: string;
  sub?: string;
  value: boolean;
  onValueChange: (v: boolean) => void;
}) {
  return (
    <View style={styles.switchRow}>
      <View style={styles.switchLabel}>
        <Text style={styles.switchLabelText}>{label}</Text>
        {sub ? <Text style={styles.switchLabelSub}>{sub}</Text> : null}
      </View>
      <Switch value={value} onValueChange={onValueChange} trackColor={{ true: "#7c3aed" }} />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#f8fafc" },
  content: { padding: 16, paddingBottom: 40 },

  sectionTitle: {
    fontSize: 12,
    fontWeight: "600",
    color: "#94a3b8",
    textTransform: "uppercase",
    letterSpacing: 0.8,
    marginBottom: 8,
    marginLeft: 4,
    marginTop: 4,
  },

  card: {
    backgroundColor: "#fff",
    borderRadius: 14,
    padding: 16,
    marginBottom: 16,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 4,
    elevation: 2,
  },

  profileRow: { flexDirection: "row", alignItems: "center", gap: 14, marginBottom: 14 },
  avatar: {
    width: 50,
    height: 50,
    borderRadius: 25,
    backgroundColor: "#0d9488",
    alignItems: "center",
    justifyContent: "center",
  },
  avatarText: { color: "#fff", fontSize: 20, fontWeight: "700" },
  profileInfo: { flex: 1 },
  profileName: { fontSize: 16, fontWeight: "700", color: "#1e293b" },
  profileEmail: { fontSize: 13, color: "#64748b", marginTop: 2 },
  roleBadge: {
    marginTop: 5,
    backgroundColor: "#f0fdf4",
    borderRadius: 6,
    paddingHorizontal: 8,
    paddingVertical: 3,
    alignSelf: "flex-start",
  },
  roleText: { fontSize: 11, color: "#059669", fontWeight: "600" },
  logoutBtn: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 6,
    borderWidth: 1,
    borderColor: "#fecaca",
    borderRadius: 9,
    paddingVertical: 10,
    backgroundColor: "#fff5f5",
  },
  logoutBtnText: { fontSize: 14, fontWeight: "600", color: "#dc2626" },

  eventRow: { flexDirection: "row", alignItems: "center", gap: 10, marginBottom: 6 },
  eventName: { flex: 1, fontSize: 15, fontWeight: "600", color: "#1e293b" },
  eventMeta: { fontSize: 13, color: "#64748b", marginBottom: 2, marginLeft: 30 },
  changeEventBtn: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 6,
    marginTop: 10,
    paddingVertical: 9,
    borderRadius: 8,
    backgroundColor: "#f0fdfa",
    borderWidth: 1,
    borderColor: "#ccfbf1",
  },
  changeEventBtnText: { fontSize: 13, color: "#0d9488", fontWeight: "600" },

  syncRow: {
    flexDirection: "row",
    alignItems: "center",
    marginBottom: 12,
    gap: 10,
  },
  syncInfo: { flex: 1 },
  syncLabel: { fontSize: 14, color: "#1e293b", fontWeight: "500" },
  syncBadge: {
    backgroundColor: "#fef3c7",
    borderRadius: 12,
    paddingHorizontal: 8,
    paddingVertical: 3,
  },
  syncBadgeText: { fontSize: 13, fontWeight: "700", color: "#92400e" },
  syncBtn: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 6,
    backgroundColor: "#0d9488",
    borderRadius: 9,
    paddingVertical: 11,
  },
  syncBtnDisabled: { opacity: 0.6 },
  syncBtnText: { color: "#fff", fontWeight: "600", fontSize: 14 },

  inHubActiveBar: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "#f5f3ff",
    borderRadius: 8,
    padding: 10,
    marginBottom: 12,
    gap: 6,
  },
  inHubActiveText: { flex: 1, fontSize: 13, color: "#7c3aed", fontWeight: "600" },
  inHubExitBtn: {
    backgroundColor: "#7c3aed",
    paddingVertical: 4,
    paddingHorizontal: 12,
    borderRadius: 6,
  },
  inHubExitBtnText: { color: "#fff", fontSize: 12, fontWeight: "600" },

  menuItem: { flexDirection: "row", alignItems: "center", gap: 12 },
  menuItemDisabled: { opacity: 0.5 },
  menuItemText: { flex: 1 },
  menuItemLabel: { fontSize: 15, fontWeight: "500", color: "#1e293b" },
  menuItemLabelDisabled: { color: "#94a3b8" },
  menuItemDescription: { fontSize: 12, color: "#94a3b8", marginTop: 2 },

  inHubConfig: { marginTop: 14 },
  fieldLabel: {
    fontSize: 12,
    fontWeight: "600",
    color: "#64748b",
    marginBottom: 6,
    marginTop: 12,
  },
  pinInput: {
    backgroundColor: "#f8fafc",
    borderRadius: 8,
    borderWidth: 1,
    borderColor: "#e2e8f0",
    paddingHorizontal: 14,
    paddingVertical: 12,
    fontSize: 18,
    color: "#1e293b",
    letterSpacing: 6,
  },
  switchRow: {
    flexDirection: "row",
    alignItems: "center",
    paddingVertical: 12,
    borderTopWidth: 1,
    borderTopColor: "#f1f5f9",
    marginTop: 8,
  },
  switchLabel: { flex: 1 },
  switchLabelText: { fontSize: 14, color: "#1e293b", fontWeight: "500" },
  switchLabelSub: { fontSize: 11, color: "#94a3b8", marginTop: 2 },
  saveInHubBtn: {
    marginTop: 16,
    backgroundColor: "#7c3aed",
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
    paddingVertical: 13,
    borderRadius: 10,
  },
  saveInHubBtnText: { color: "#fff", fontWeight: "700", fontSize: 15 },
});
