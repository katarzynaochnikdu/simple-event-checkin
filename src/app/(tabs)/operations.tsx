import { useState, useEffect, useRef, useCallback } from "react";
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  Vibration,
  Platform,
  SafeAreaView,
} from "react-native";
import { CameraView, useCameraPermissions } from "expo-camera";
import { Ionicons } from "@expo/vector-icons";
import * as SecureStore from "expo-secure-store";
import { checkinOnline, verifyInHubPin, fetchTicketClasses } from "../../lib/api";
import {
  findParticipantByTicketId,
  markLocalCheckin,
  addOfflineCheckin,
  getLocalStats,
} from "../../lib/db";
import { isOnline } from "../../lib/sync";
import { useEvent } from "../../contexts/EventContext";
import ScanResult from "../../components/ScanResult";
import WalkinFormModal, { type WalkinFormData } from "../../components/WalkinFormModal";
import InHubPinModal from "../../components/InHubPinModal";
import InHubScanOverlay, { type OverlayResult } from "../../components/InHubScanOverlay";
import InHubScreensaver from "../../components/InHubScreensaver";
import { WalkinRepository } from "../../lib/repositories/WalkinRepository";
import type { CheckinResult, ScanMode, TicketClass } from "../../types";

const SCAN_COOLDOWN_MS = 2500;
const SCREENSAVER_TIMEOUT_MS = 60_000;
const INHUB_STORE_KEY_PREFIX = "inhub_active_";

type Segment = "checkin" | "review" | "inhub";

export default function OperationsScreen() {
  const { event, isInHubMode, enterInHubMode, exitInHubMode } = useEvent();
  const eventId = event?.event_id;

  const [permission, requestPermission] = useCameraPermissions();
  const [segment, setSegment] = useState<Segment>("checkin");

  // Scanner state
  const [scanResult, setScanResult] = useState<CheckinResult | null>(null);
  const [scanning, setScanning] = useState(true);
  const [stats, setStats] = useState({ total: 0, checkedIn: 0 });
  const lastScanRef = useRef<string>("");
  const cooldownRef = useRef<number>(0);

  // Walk-in
  const [walkinVisible, setWalkinVisible] = useState(false);
  const [ticketClasses, setTicketClasses] = useState<TicketClass[]>([]);

  // InHub state
  const [pinModalVisible, setPinModalVisible] = useState(false);
  const [pinError, setPinError] = useState<string | null>(null);
  const [pinLoading, setPinLoading] = useState(false);
  const [overlayResult, setOverlayResult] = useState<OverlayResult | null>(null);
  const [screensaverActive, setScreensaverActive] = useState(false);
  const screensaverTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const inhubCooldownRef = useRef(false);
  const [inhubScanning, setInhubScanning] = useState(true);

  // Load stats on mount and after scan
  useEffect(() => {
    if (eventId) getLocalStats(eventId).then(setStats);
  }, [eventId, scanResult]);

  // Load ticket classes for walk-in
  useEffect(() => {
    if (!eventId) return;
    WalkinRepository.getTicketClasses(eventId).then(setTicketClasses);
    fetchTicketClasses(eventId)
      .then((classes) => {
        WalkinRepository.cacheTicketClasses(eventId, classes);
        setTicketClasses(classes);
      })
      .catch(() => {});
  }, [eventId]);

  // Screensaver timer for InHub
  const resetScreensaverTimer = useCallback(() => {
    if (screensaverTimer.current) clearTimeout(screensaverTimer.current);
    setScreensaverActive(false);
    screensaverTimer.current = setTimeout(() => setScreensaverActive(true), SCREENSAVER_TIMEOUT_MS);
  }, []);

  useEffect(() => {
    if (isInHubMode) {
      resetScreensaverTimer();
    } else {
      if (screensaverTimer.current) clearTimeout(screensaverTimer.current);
      setScreensaverActive(false);
    }
    return () => { if (screensaverTimer.current) clearTimeout(screensaverTimer.current); };
  }, [isInHubMode, resetScreensaverTimer]);

  if (!permission) return null;

  if (!permission.granted) {
    return (
      <View style={styles.center}>
        <Ionicons name="camera-outline" size={64} color="#94a3b8" />
        <Text style={styles.permText}>Potrzebny dostęp do kamery</Text>
        <Text style={styles.permSubtext}>
          Aby skanować kody QR uczestników, pozwól na dostęp do kamery.
        </Text>
        <TouchableOpacity style={styles.permButton} onPress={requestPermission}>
          <Text style={styles.permButtonText}>Zezwól na kamerę</Text>
        </TouchableOpacity>
      </View>
    );
  }

  // --- Scanner handlers ---
  async function handleBarCodeScanned(data: string) {
    const now = Date.now();
    if (data === lastScanRef.current && now - cooldownRef.current < SCAN_COOLDOWN_MS) return;
    lastScanRef.current = data;
    cooldownRef.current = now;
    setScanning(false);
    Vibration.vibrate(100);

    if (!eventId) { setScanResult({ success: false, error: "no_event" }); return; }

    if (segment === "review") {
      await handleReviewScan(data, eventId);
    } else {
      await handleCheckinScan(data, eventId);
    }
  }

  async function handleCheckinScan(ticketId: string, evId: string) {
    const localParticipant = await findParticipantByTicketId(ticketId, evId);
    const online = await isOnline();

    if (online) {
      try {
        const result = await checkinOnline(ticketId, evId);
        if (result.success && !result.already_checked_in) {
          await markLocalCheckin(ticketId, evId);
        }
        setScanResult(result);
        return;
      } catch { /* fallback to offline */ }
    }

    if (localParticipant) {
      if (localParticipant.status === "checked_in") {
        setScanResult({
          success: true,
          already_checked_in: true,
          checked_in_at: localParticipant.checked_in_at,
          participant: participantSummary(localParticipant),
        });
      } else {
        await markLocalCheckin(ticketId, evId);
        await addOfflineCheckin(ticketId, evId, Platform.OS, "checkin");
        setScanResult({
          success: true,
          already_checked_in: false,
          checked_in_at: new Date().toISOString(),
          participant: participantSummary(localParticipant),
        });
      }
    } else {
      setScanResult({ success: false, error: "not_found" });
    }
  }

  async function handleReviewScan(ticketId: string, evId: string) {
    const localParticipant = await findParticipantByTicketId(ticketId, evId);
    if (localParticipant) {
      setScanResult({
        success: true,
        already_checked_in: localParticipant.status === "checked_in",
        checked_in_at: localParticipant.checked_in_at,
        participant: participantSummary(localParticipant),
      });
    } else {
      setScanResult({ success: false, error: "not_found" });
    }
  }

  function dismissResult() {
    setScanResult(null);
    setScanning(true);
  }

  // --- InHub handlers ---
  async function handlePinConfirm(pin: string) {
    if (!eventId) return;
    setPinLoading(true);
    setPinError(null);
    try {
      const result = await verifyInHubPin(eventId, pin);
      if (result.valid) {
        await SecureStore.setItemAsync(`${INHUB_STORE_KEY_PREFIX}${eventId}`, "1");
        enterInHubMode();
        setPinModalVisible(false);
        resetScreensaverTimer();
      } else {
        setPinError("Nieprawidłowy PIN. Spróbuj ponownie.");
      }
    } catch {
      setPinError("Błąd weryfikacji PIN.");
    } finally {
      setPinLoading(false);
    }
  }

  async function handleInHubBarCodeScanned({ data }: { data: string }) {
    if (!eventId || inhubCooldownRef.current || !inhubScanning) return;
    inhubCooldownRef.current = true;
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
          const name = participant ? `${participant.first_name} ${participant.last_name}` : ticketId;
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
          result = { type: "success", name: `${participant.first_name} ${participant.last_name}`, ticketName: participant.ticket_name };
        }
      }

      setInhubScanning(false);
      setOverlayResult(result);
      Vibration.vibrate(result.type === "success" ? 80 : [0, 60, 60, 60]);
    } finally {
      setTimeout(() => {
        inhubCooldownRef.current = false;
        setOverlayResult(null);
        setInhubScanning(true);
      }, 2000);
    }
  }

  async function handleWalkinSubmit(formData: WalkinFormData, checkInNow: boolean) {
    if (!eventId) return;
    await WalkinRepository.create({
      event_id: eventId,
      first_name: formData.first_name.trim(),
      last_name: formData.last_name.trim(),
      email: formData.email.trim() || undefined,
      phone: formData.phone.trim() || undefined,
      company: formData.company.trim() || undefined,
      ticket_class_id: formData.ticket_class_id || undefined,
      ticket_name: formData.ticket_name || undefined,
      notes: formData.notes.trim() || undefined,
      check_in_immediately: checkInNow,
    });
    getLocalStats(eventId).then(setStats);
  }

  // When InHub mode is active — fullscreen kiosk
  if (segment === "inhub" && isInHubMode) {
    return (
      <View style={StyleSheet.absoluteFill}>
        <CameraView
          style={StyleSheet.absoluteFill}
          barcodeScannerSettings={{ barcodeTypes: ["qr"] }}
          onBarcodeScanned={inhubScanning ? handleInHubBarCodeScanned : undefined}
        />
        {screensaverActive && (
          <InHubScreensaver onPress={resetScreensaverTimer} />
        )}
        {overlayResult && (
          <InHubScanOverlay result={overlayResult} />
        )}
      </View>
    );
  }

  const scanMode: ScanMode = segment === "review" ? "review" : "checkin";
  const frameColor = segment === "review" ? "#f59e0b" : "#0d9488";

  return (
    <View style={styles.container}>
      {/* Segment selector at top */}
      <SafeAreaView style={styles.topBar}>
        <View style={styles.segmentRow}>
          {(["checkin", "review", "inhub"] as Segment[]).map((seg) => (
            <TouchableOpacity
              key={seg}
              style={[styles.segBtn, segment === seg && styles.segBtnActive]}
              onPress={() => setSegment(seg)}
              activeOpacity={0.7}
            >
              <Text style={[styles.segBtnText, segment === seg && styles.segBtnTextActive]}>
                {seg === "checkin" ? "Wejście" : seg === "review" ? "Podgląd" : "InHub"}
              </Text>
            </TouchableOpacity>
          ))}
        </View>
      </SafeAreaView>

      {/* InHub panel when not active */}
      {segment === "inhub" && !isInHubMode ? (
        <View style={styles.inhubPanel}>
          <Ionicons name="tv-outline" size={64} color="#7c3aed" style={{ marginBottom: 16 }} />
          <Text style={styles.inhubPanelTitle}>Tryb InHub</Text>
          <Text style={styles.inhubPanelSub}>
            Tryb kiosku do automatycznego check-inu. Kamera zajmuje cały ekran.
          </Text>
          <TouchableOpacity
            style={styles.inhubEnterBtn}
            onPress={() => setPinModalVisible(true)}
            activeOpacity={0.85}
          >
            <Ionicons name="lock-open-outline" size={18} color="#fff" />
            <Text style={styles.inhubEnterBtnText}>Wejdź w tryb InHub</Text>
          </TouchableOpacity>
        </View>
      ) : segment !== "inhub" ? (
        <>
          {/* Camera scanner */}
          {scanning ? (
            <CameraView
              style={styles.camera}
              barcodeScannerSettings={{ barcodeTypes: ["qr"] }}
              onBarcodeScanned={(result) => handleBarCodeScanned(result.data)}
            >
              <View style={styles.overlay}>
                <View style={[styles.scanFrame, { borderColor: frameColor }]} />
                <Text style={styles.scanHint}>
                  {segment === "review" ? "Skanuj QR — podgląd" : "Skanuj QR — zameldowanie"}
                </Text>
              </View>
            </CameraView>
          ) : (
            <ScanResult result={scanResult} onDismiss={dismissResult} scanMode={scanMode} />
          )}

          {/* Stats bar + Walk-in FAB */}
          <View style={styles.bottomBar}>
            <View style={styles.statsRow}>
              <Ionicons name="people" size={16} color="#0d9488" />
              <Text style={styles.statsText}>
                {stats.checkedIn} / {stats.total} zarejestrowanych
              </Text>
            </View>
            <TouchableOpacity
              style={styles.walkinFab}
              onPress={() => setWalkinVisible(true)}
              activeOpacity={0.85}
            >
              <Ionicons name="person-add" size={20} color="#fff" />
            </TouchableOpacity>
          </View>
        </>
      ) : null}

      {/* InHub PIN modal */}
      <InHubPinModal
        visible={pinModalVisible}
        title="Wejdź w tryb InHub"
        subtitle="Wprowadź PIN skonfigurowany w Systemie"
        error={pinError}
        loading={pinLoading}
        onConfirm={handlePinConfirm}
        onCancel={() => { setPinModalVisible(false); setPinError(null); }}
      />

      {/* Walk-in modal */}
      <WalkinFormModal
        visible={walkinVisible}
        eventId={eventId ?? ""}
        ticketClasses={ticketClasses}
        onClose={() => setWalkinVisible(false)}
        onSubmit={handleWalkinSubmit}
      />
    </View>
  );
}

function participantSummary(p: {
  id: number;
  first_name: string;
  last_name: string;
  email: string;
  company: string;
  ticket_name: string;
  ticket_class_id: string;
}) {
  return {
    id: p.id,
    first_name: p.first_name,
    last_name: p.last_name,
    email: p.email,
    company: p.company,
    ticket_name: p.ticket_name,
    ticket_class_id: p.ticket_class_id,
  };
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#000" },

  center: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    padding: 32,
    backgroundColor: "#f8fafc",
  },
  permText: { fontSize: 20, fontWeight: "600", color: "#1e293b", marginTop: 16 },
  permSubtext: {
    fontSize: 14,
    color: "#64748b",
    textAlign: "center",
    marginTop: 8,
    marginBottom: 24,
  },
  permButton: {
    backgroundColor: "#0d9488",
    borderRadius: 10,
    paddingHorizontal: 24,
    paddingVertical: 14,
  },
  permButtonText: { color: "#fff", fontSize: 16, fontWeight: "600" },

  topBar: {
    backgroundColor: "rgba(0,0,0,0.85)",
    paddingBottom: 8,
  },
  segmentRow: {
    flexDirection: "row",
    marginHorizontal: 16,
    marginTop: 8,
    backgroundColor: "rgba(255,255,255,0.1)",
    borderRadius: 10,
    padding: 3,
    gap: 2,
  },
  segBtn: {
    flex: 1,
    paddingVertical: 8,
    borderRadius: 8,
    alignItems: "center",
  },
  segBtnActive: { backgroundColor: "#fff" },
  segBtnText: { fontSize: 13, fontWeight: "600", color: "rgba(255,255,255,0.7)" },
  segBtnTextActive: { color: "#1e293b" },

  camera: { flex: 1 },
  overlay: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    backgroundColor: "rgba(0,0,0,0.4)",
  },
  scanFrame: {
    width: 250,
    height: 250,
    borderWidth: 3,
    borderRadius: 16,
    backgroundColor: "transparent",
  },
  scanHint: {
    color: "#fff",
    fontSize: 15,
    marginTop: 20,
    textAlign: "center",
  },

  bottomBar: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "#fff",
    paddingVertical: 10,
    paddingHorizontal: 16,
    gap: 12,
  },
  statsRow: {
    flex: 1,
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
  },
  statsText: { fontSize: 14, color: "#1e293b", fontWeight: "500" },
  walkinFab: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: "#7c3aed",
    alignItems: "center",
    justifyContent: "center",
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.15,
    shadowRadius: 4,
    elevation: 4,
  },

  inhubPanel: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: "#f8fafc",
    padding: 32,
  },
  inhubPanelTitle: {
    fontSize: 22,
    fontWeight: "800",
    color: "#7c3aed",
    marginBottom: 10,
  },
  inhubPanelSub: {
    fontSize: 14,
    color: "#64748b",
    textAlign: "center",
    marginBottom: 28,
    lineHeight: 20,
  },
  inhubEnterBtn: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    backgroundColor: "#7c3aed",
    borderRadius: 12,
    paddingHorizontal: 24,
    paddingVertical: 14,
  },
  inhubEnterBtnText: { color: "#fff", fontWeight: "700", fontSize: 16 },
});
