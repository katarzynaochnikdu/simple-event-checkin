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
import type { User } from "../../types";

const INHUB_STORE_KEY_PREFIX = "inhub_active_";

export default function WiecejScreen() {
  const router = useRouter();
  const { event, clearEvent, isInHubMode, enterInHubMode, exitInHubMode } = useEvent();
  const [user, setUser] = useState<User | null>(null);

  // InHub config state
  const [inHubSectionOpen, setInHubSectionOpen] = useState(false);
  const [inHubPin, setInHubPin] = useState("");
  const [inHubPinConfirm, setInHubPinConfirm] = useState("");
  const [autoCheckin, setAutoCheckin] = useState(true);
  const [showSearch, setShowSearch] = useState(true);
  const [showWalkin, setShowWalkin] = useState(false);
  const [inHubLoading, setInHubLoading] = useState(false);
  const [inHubConfigExists, setInHubConfigExists] = useState(false);

  useEffect(() => {
    getUser().then(setUser);
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
    Alert.alert(
      "Wyloguj się",
      "Czy na pewno chcesz się wylogować?",
      [
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
      ]
    );
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

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      {user && (
        <View style={styles.profileCard}>
          <View style={styles.avatar}>
            <Text style={styles.avatarText}>
              {(user.first_name?.[0] ?? user.email[0]).toUpperCase()}
            </Text>
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
      )}

      {event && (
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Aktywne wydarzenie</Text>
          <View style={styles.eventCard}>
            <Ionicons name="calendar" size={20} color="#0d9488" />
            <Text style={styles.eventName} numberOfLines={2}>
              {event.event_name}
            </Text>
          </View>
          <TouchableOpacity style={styles.changeEventButton} onPress={handleChangeEvent}>
            <Ionicons name="swap-horizontal-outline" size={18} color="#0d9488" />
            <Text style={styles.changeEventText}>Zmień wydarzenie</Text>
          </TouchableOpacity>
        </View>
      )}

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Aplikacja</Text>
        <View style={styles.menuCard}>
          <MenuItem
            icon="print-outline"
            label="Ustawienia drukarki"
            description="Konfiguracja drukarki identyfikatorów"
            onPress={() => Alert.alert("Wkrótce", "Ustawienia drukarki będą dostępne wkrótce.")}
            disabled
          />
          <View style={styles.divider} />
          <MenuItem
            icon="tv-outline"
            label="Tryb InHub"
            description={inHubConfigExists ? "PIN skonfigurowany" : "Konfiguruj PIN InHub"}
            onPress={() => setInHubSectionOpen((v) => !v)}
            chevronOverride={inHubSectionOpen ? "chevron-up" : "chevron-forward"}
          />
        </View>
      </View>

      {/* InHub configuration panel */}
      {inHubSectionOpen && event && (
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Ustawienia InHub</Text>
          <View style={styles.inHubCard}>
            {isInHubMode && (
              <View style={styles.inHubActiveBar}>
                <Ionicons name="tv-outline" size={16} color="#7c3aed" />
                <Text style={styles.inHubActiveText}>Tryb InHub jest aktywny</Text>
                <TouchableOpacity onPress={handleExitInHub} style={styles.inHubExitBtn}>
                  <Text style={styles.inHubExitBtnText}>Wyłącz</Text>
                </TouchableOpacity>
              </View>
            )}

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

            <View style={styles.switchRow}>
              <View style={styles.switchLabel}>
                <Text style={styles.switchLabelText}>Automatyczny check-in</Text>
                <Text style={styles.switchLabelSub}>Check-in po skanowaniu QR bez potwierdzenia</Text>
              </View>
              <Switch
                value={autoCheckin}
                onValueChange={setAutoCheckin}
                trackColor={{ true: "#7c3aed" }}
              />
            </View>

            <View style={styles.switchRow}>
              <View style={styles.switchLabel}>
                <Text style={styles.switchLabelText}>Pokazuj wyszukiwarkę</Text>
              </View>
              <Switch
                value={showSearch}
                onValueChange={setShowSearch}
                trackColor={{ true: "#7c3aed" }}
              />
            </View>

            <View style={styles.switchRow}>
              <View style={styles.switchLabel}>
                <Text style={styles.switchLabelText}>Rejestracja walk-in</Text>
              </View>
              <Switch
                value={showWalkin}
                onValueChange={setShowWalkin}
                trackColor={{ true: "#7c3aed" }}
              />
            </View>

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
        </View>
      )}

      <View style={styles.section}>
        <TouchableOpacity style={styles.logoutButton} onPress={handleLogout}>
          <Ionicons name="log-out-outline" size={20} color="#dc2626" />
          <Text style={styles.logoutText}>Wyloguj się</Text>
        </TouchableOpacity>
      </View>
    </ScrollView>
  );
}

function MenuItem({
  icon,
  label,
  description,
  onPress,
  disabled,
  chevronOverride,
}: {
  icon: string;
  label: string;
  description?: string;
  onPress: () => void;
  disabled?: boolean;
  chevronOverride?: string;
}) {
  return (
    <TouchableOpacity
      style={[styles.menuItem, disabled && styles.menuItemDisabled]}
      onPress={onPress}
      disabled={disabled}
    >
      <Ionicons name={icon as any} size={22} color={disabled ? "#cbd5e1" : "#1e293b"} />
      <View style={styles.menuItemText}>
        <Text style={[styles.menuItemLabel, disabled && styles.menuItemLabelDisabled]}>
          {label}
        </Text>
        {description && (
          <Text style={styles.menuItemDescription}>{description}</Text>
        )}
      </View>
      <Ionicons
        name={(chevronOverride ?? "chevron-forward") as any}
        size={18}
        color={disabled ? "#e2e8f0" : "#94a3b8"}
      />
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#f8fafc" },
  content: { padding: 16, paddingBottom: 32 },
  profileCard: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "#fff",
    borderRadius: 16,
    padding: 20,
    marginBottom: 16,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.06,
    shadowRadius: 8,
    elevation: 3,
    gap: 16,
  },
  avatar: {
    width: 52,
    height: 52,
    borderRadius: 26,
    backgroundColor: "#0d9488",
    alignItems: "center",
    justifyContent: "center",
  },
  avatarText: { color: "#fff", fontSize: 22, fontWeight: "700" },
  profileInfo: { flex: 1 },
  profileName: { fontSize: 16, fontWeight: "700", color: "#1e293b" },
  profileEmail: { fontSize: 13, color: "#64748b", marginTop: 2 },
  roleBadge: {
    marginTop: 6,
    backgroundColor: "#f0fdf4",
    borderRadius: 6,
    paddingHorizontal: 8,
    paddingVertical: 3,
    alignSelf: "flex-start",
  },
  roleText: { fontSize: 11, color: "#059669", fontWeight: "600" },
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
  eventCard: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "#fff",
    borderRadius: 12,
    padding: 14,
    gap: 10,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.04,
    shadowRadius: 4,
    elevation: 1,
  },
  eventName: { flex: 1, fontSize: 14, fontWeight: "600", color: "#1e293b" },
  changeEventButton: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 6,
    marginTop: 8,
    paddingVertical: 10,
    borderRadius: 8,
    backgroundColor: "#f0fdfa",
    borderWidth: 1,
    borderColor: "#ccfbf1",
  },
  changeEventText: { fontSize: 14, color: "#0d9488", fontWeight: "600" },
  menuCard: {
    backgroundColor: "#fff",
    borderRadius: 12,
    overflow: "hidden",
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.04,
    shadowRadius: 4,
    elevation: 1,
  },
  menuItem: {
    flexDirection: "row",
    alignItems: "center",
    padding: 16,
    gap: 12,
  },
  menuItemDisabled: { opacity: 0.5 },
  menuItemText: { flex: 1 },
  menuItemLabel: { fontSize: 15, fontWeight: "500", color: "#1e293b" },
  menuItemLabelDisabled: { color: "#94a3b8" },
  menuItemDescription: { fontSize: 12, color: "#94a3b8", marginTop: 2 },
  divider: { height: 1, backgroundColor: "#f1f5f9", marginLeft: 50 },
  inHubCard: {
    backgroundColor: "#fff",
    borderRadius: 12,
    padding: 16,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.04,
    shadowRadius: 4,
    elevation: 1,
  },
  inHubActiveBar: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "#f5f3ff",
    borderRadius: 8,
    padding: 10,
    marginBottom: 16,
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
    marginTop: 20,
    backgroundColor: "#7c3aed",
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
    paddingVertical: 13,
    borderRadius: 10,
  },
  saveInHubBtnText: { color: "#fff", fontWeight: "700", fontSize: 15 },
  logoutButton: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
    backgroundColor: "#fff",
    borderRadius: 12,
    padding: 16,
    borderWidth: 1,
    borderColor: "#fecaca",
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.04,
    shadowRadius: 4,
    elevation: 1,
  },
  logoutText: { fontSize: 16, fontWeight: "600", color: "#dc2626" },
});
