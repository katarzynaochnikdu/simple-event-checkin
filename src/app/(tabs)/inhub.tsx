import { useState, useEffect, useRef, useCallback } from "react";
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  Platform,
} from "react-native";
import { CameraView, useCameraPermissions } from "expo-camera";
import { Ionicons } from "@expo/vector-icons";
import * as SecureStore from "expo-secure-store";
import { useEvent } from "../../contexts/EventContext";
import { checkinOnline } from "../../lib/api";
import { verifyInHubPin } from "../../lib/api";
import {
  findParticipantByTicketId,
  markLocalCheckin,
  addOfflineCheckin,
} from "../../lib/db";
import { isOnline } from "../../lib/sync";
import InHubPinModal from "../../components/InHubPinModal";
import InHubScanOverlay, { type OverlayResult } from "../../components/InHubScanOverlay";
import InHubScreensaver from "../../components/InHubScreensaver";

const SCREENSAVER_TIMEOUT_MS = 60_000;
const SCAN_COOLDOWN_MS = 2_500;
const INHUB_STORE_KEY_PREFIX = "inhub_active_";

export default function InHubScreen() {
  const { event, isInHubMode, enterInHubMode, exitInHubMode } = useEvent();
  const eventId = event?.event_id;

  const [permission, requestPermission] = useCameraPermissions();
  const [scanning, setScanning] = useState(true);
  const [overlayResult, setOverlayResult] = useState<OverlayResult | null>(null);
  const [screensaverActive, setScreensaverActive] = useState(false);
  const [pinModalVisible, setPinModalVisible] = useState(false);
  const [pinPurpose, setPinPurpose] = useState<"enter" | "exit">("exit");
  const [pinError, setPinError] = useState<string | null>(null);
  const [pinLoading, setPinLoading] = useState(false);

  const screensaverTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const cooldownRef = useRef(false);

  // --- Screensaver logic ---
  const resetScreensaverTimer = useCallback(() => {
    if (screensaverTimer.current) clearTimeout(screensaverTimer.current);
    setScreensaverActive(false);
    screensaverTimer.current = setTimeout(() => {
      setScreensaverActive(true);
    }, SCREENSAVER_TIMEOUT_MS);
  }, []);

  useEffect(() => {
    if (isInHubMode) {
      resetScreensaverTimer();
    } else {
      if (screensaverTimer.current) clearTimeout(screensaverTimer.current);
      setScreensaverActive(false);
    }
    return () => {
      if (screensaverTimer.current) clearTimeout(screensaverTimer.current);
    };
  }, [isInHubMode]);

  // --- Scan handler ---
  async function handleBarCodeScanned({ data }: { data: string }) {
    if (!eventId || cooldownRef.current || !scanning) return;
    cooldownRef.current = true;
    resetScreensaverTimer();

    const ticketId = data.trim();
    try {
      const online = await isOnline();
      let result: OverlayResult;

      if (online) {
        const res = await checkinOnline(ticketId, eventId, Platform.OS);
        if (res.success) {
          const name = res.participant
            ? `${res.participant.first_name} ${res.participant.last_name}`
            : ticketId;
          result = { type: "success", name, ticketName: res.participant?.ticket_name };
          await markLocalCheckin(ticketId, eventId);
        } else if (res.already_checked_in) {
          const participant = await findParticipantByTicketId(ticketId, eventId);
          const name = participant
            ? `${participant.first_name} ${participant.last_name}`
            : ticketId;
          result = { type: "already", name };
        } else {
          result = { type: "error", message: res.error || "Nieznany błąd" };
        }
      } else {
        const participant = await findParticipantByTicketId(ticketId, eventId);
        if (!participant) {
          result = { type: "error", message: "Nie znaleziono uczestnika (offline)" };
        } else if (participant.status === "checked_in") {
          result = { type: "already", name: `${participant.first_name} ${participant.last_name}` };
        } else {
          await markLocalCheckin(ticketId, eventId);
          await addOfflineCheckin(ticketId, eventId, Platform.OS, "checkin");
          result = {
            type: "success",
            name: `${participant.first_name} ${participant.last_name}`,
            ticketName: participant.ticket_name,
          };
        }
      }

      setOverlayResult(result);
    } catch (e: any) {
      setOverlayResult({ type: "error", message: e?.message || "Błąd skanowania" });
    }

    setTimeout(() => {
      cooldownRef.current = false;
    }, SCAN_COOLDOWN_MS);
  }

  function handleOverlayDismiss() {
    setOverlayResult(null);
  }

  // --- InHub enter/exit ---
  function requestEnterInHub() {
    setPinPurpose("enter");
    setPinError(null);
    setPinModalVisible(true);
  }

  function requestExitInHub() {
    setPinPurpose("exit");
    setPinError(null);
    setPinModalVisible(true);
  }

  async function handlePinConfirm(pin: string) {
    if (!eventId) return;
    setPinLoading(true);
    setPinError(null);
    try {
      const res = await verifyInHubPin(eventId, pin);
      if (res.valid) {
        setPinModalVisible(false);
        setPinLoading(false);
        if (pinPurpose === "enter") {
          await SecureStore.setItemAsync(`${INHUB_STORE_KEY_PREFIX}${eventId}`, "1");
          enterInHubMode();
          resetScreensaverTimer();
        } else {
          await SecureStore.deleteItemAsync(`${INHUB_STORE_KEY_PREFIX}${eventId}`);
          exitInHubMode();
        }
      } else {
        setPinError("Nieprawidłowy PIN");
      }
    } catch {
      setPinError("Błąd weryfikacji PIN");
    } finally {
      setPinLoading(false);
    }
  }

  // --- No event ---
  if (!eventId) {
    return (
      <View style={styles.emptyContainer}>
        <Ionicons name="tv-outline" size={42} color="#94a3b8" />
        <Text style={styles.emptyTitle}>Nie wybrano wydarzenia</Text>
        <Text style={styles.emptySubtitle}>
          Wróć do listy wydarzeń i wybierz event.
        </Text>
      </View>
    );
  }

  // --- Camera permission ---
  if (!permission) {
    return <View style={styles.emptyContainer} />;
  }
  if (!permission.granted) {
    return (
      <View style={styles.emptyContainer}>
        <Ionicons name="camera-outline" size={42} color="#94a3b8" />
        <Text style={styles.emptyTitle}>Brak dostępu do kamery</Text>
        <TouchableOpacity style={styles.permissionBtn} onPress={requestPermission}>
          <Text style={styles.permissionBtnText}>Udziel dostępu</Text>
        </TouchableOpacity>
      </View>
    );
  }

  // --- Not in InHub mode: show "Enter InHub" screen ---
  if (!isInHubMode) {
    return (
      <View style={styles.setupContainer}>
        <Ionicons name="tv-outline" size={56} color="#7c3aed" />
        <Text style={styles.setupTitle}>Tryb InHub</Text>
        <Text style={styles.setupSubtitle}>
          Fullscreen scanner z automatycznym check-in. Aby wejść, potrzebny jest PIN ustawiony w zakładce Więcej.
        </Text>
        <TouchableOpacity style={styles.enterBtn} onPress={requestEnterInHub} activeOpacity={0.85}>
          <Ionicons name="lock-open-outline" size={20} color="#fff" />
          <Text style={styles.enterBtnText}>Wejdź w tryb InHub</Text>
        </TouchableOpacity>

        <InHubPinModal
          visible={pinModalVisible}
          title="PIN InHub"
          subtitle="Wprowadź PIN aby aktywować tryb InHub"
          error={pinError}
          loading={pinLoading}
          onConfirm={handlePinConfirm}
          onCancel={() => { setPinModalVisible(false); setPinError(null); }}
        />
      </View>
    );
  }

  // --- InHub mode: fullscreen scanner ---
  return (
    <View style={styles.inhubContainer}>
      <CameraView
        style={StyleSheet.absoluteFill}
        facing="back"
        barcodeScannerSettings={{ barcodeTypes: ["qr"] }}
        onBarcodeScanned={overlayResult ? undefined : handleBarCodeScanned}
      />

      {/* Top bar with exit button */}
      <View style={styles.topBar}>
        <Text style={styles.topBarTitle}>{event?.event_name}</Text>
        <TouchableOpacity style={styles.exitBtn} onPress={requestExitInHub} activeOpacity={0.8}>
          <Ionicons name="lock-closed-outline" size={16} color="#fff" />
          <Text style={styles.exitBtnText}>Wyjdź</Text>
        </TouchableOpacity>
      </View>

      {/* QR frame guide */}
      <View style={styles.frameGuide}>
        <View style={[styles.corner, styles.cornerTL]} />
        <View style={[styles.corner, styles.cornerTR]} />
        <View style={[styles.corner, styles.cornerBL]} />
        <View style={[styles.corner, styles.cornerBR]} />
      </View>

      <Text style={styles.scanPrompt}>Przyłóż kod QR do kamery</Text>

      <InHubScanOverlay result={overlayResult} onDismiss={handleOverlayDismiss} />

      <InHubScreensaver
        active={screensaverActive}
        onWake={resetScreensaverTimer}
        eventName={event?.event_name}
      />

      <InHubPinModal
        visible={pinModalVisible}
        title="Wyjdź z InHub"
        subtitle="Wprowadź PIN aby opuścić tryb InHub"
        error={pinError}
        loading={pinLoading}
        onConfirm={handlePinConfirm}
        onCancel={() => { setPinModalVisible(false); setPinError(null); }}
      />
    </View>
  );
}

const CORNER = 28;
const BORDER = 4;

const styles = StyleSheet.create({
  emptyContainer: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: 28,
    backgroundColor: "#f8fafc",
  },
  emptyTitle: {
    marginTop: 12,
    fontSize: 18,
    fontWeight: "600",
    color: "#1e293b",
  },
  emptySubtitle: {
    marginTop: 8,
    textAlign: "center",
    color: "#64748b",
    fontSize: 14,
  },
  permissionBtn: {
    marginTop: 16,
    backgroundColor: "#7c3aed",
    paddingVertical: 12,
    paddingHorizontal: 28,
    borderRadius: 10,
  },
  permissionBtnText: { color: "#fff", fontWeight: "600", fontSize: 15 },
  setupContainer: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: "#f8fafc",
    paddingHorizontal: 32,
  },
  setupTitle: {
    marginTop: 16,
    fontSize: 22,
    fontWeight: "700",
    color: "#1e293b",
  },
  setupSubtitle: {
    marginTop: 10,
    fontSize: 14,
    color: "#64748b",
    textAlign: "center",
    lineHeight: 20,
  },
  enterBtn: {
    marginTop: 28,
    backgroundColor: "#7c3aed",
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    paddingVertical: 14,
    paddingHorizontal: 32,
    borderRadius: 12,
  },
  enterBtnText: { color: "#fff", fontWeight: "700", fontSize: 16 },
  inhubContainer: {
    flex: 1,
    backgroundColor: "#000",
    alignItems: "center",
    justifyContent: "center",
  },
  topBar: {
    position: "absolute",
    top: 0,
    left: 0,
    right: 0,
    paddingTop: 48,
    paddingBottom: 12,
    paddingHorizontal: 20,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    backgroundColor: "rgba(0,0,0,0.5)",
    zIndex: 10,
  },
  topBarTitle: {
    flex: 1,
    color: "#fff",
    fontSize: 16,
    fontWeight: "600",
    marginRight: 12,
  },
  exitBtn: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    backgroundColor: "rgba(255,255,255,0.15)",
    paddingVertical: 6,
    paddingHorizontal: 12,
    borderRadius: 20,
  },
  exitBtnText: { color: "#fff", fontSize: 13, fontWeight: "600" },
  frameGuide: {
    width: 260,
    height: 260,
    position: "relative",
    zIndex: 5,
  },
  corner: {
    position: "absolute",
    width: CORNER,
    height: CORNER,
    borderColor: "#fff",
  },
  cornerTL: { top: 0, left: 0, borderTopWidth: BORDER, borderLeftWidth: BORDER },
  cornerTR: { top: 0, right: 0, borderTopWidth: BORDER, borderRightWidth: BORDER },
  cornerBL: { bottom: 0, left: 0, borderBottomWidth: BORDER, borderLeftWidth: BORDER },
  cornerBR: { bottom: 0, right: 0, borderBottomWidth: BORDER, borderRightWidth: BORDER },
  scanPrompt: {
    position: "absolute",
    bottom: "20%",
    color: "rgba(255,255,255,0.7)",
    fontSize: 16,
    zIndex: 5,
  },
});
