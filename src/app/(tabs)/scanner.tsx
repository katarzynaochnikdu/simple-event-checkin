import { useState, useEffect, useRef } from "react";
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  Vibration,
  Platform,
} from "react-native";
import { CameraView, useCameraPermissions } from "expo-camera";
import { Ionicons } from "@expo/vector-icons";
import { checkinOnline } from "../../lib/api";
import {
  findParticipantByTicketId,
  markLocalCheckin,
  addOfflineCheckin,
  getLocalStats,
} from "../../lib/db";
import { isOnline } from "../../lib/sync";
import ScanResult from "../../components/ScanResult";
import ScannerModeSelector from "../../components/ScannerModeSelector";
import { useEvent } from "../../contexts/EventContext";
import type { CheckinResult, ScanMode } from "../../types";

const SCAN_COOLDOWN_MS = 2500;

type ScanResultState = CheckinResult | null;

export default function ScannerScreen() {
  const { event } = useEvent();
  const eventId = event?.event_id;
  const [permission, requestPermission] = useCameraPermissions();
  const [scanMode, setScanMode] = useState<ScanMode>("checkin");
  const [scanResult, setScanResult] = useState<ScanResultState>(null);
  const [scanning, setScanning] = useState(true);
  const [stats, setStats] = useState({ total: 0, checkedIn: 0 });
  const lastScanRef = useRef<string>("");
  const cooldownRef = useRef<number>(0);

  useEffect(() => {
    if (eventId) {
      getLocalStats(eventId).then(setStats);
    }
  }, [eventId, scanResult]);

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

  async function handleBarCodeScanned(data: string) {
    const now = Date.now();
    if (data === lastScanRef.current && now - cooldownRef.current < SCAN_COOLDOWN_MS) {
      return;
    }
    lastScanRef.current = data;
    cooldownRef.current = now;
    setScanning(false);
    Vibration.vibrate(100);

    if (!eventId) {
      setScanResult({ success: false, error: "no_event" });
      return;
    }

    if (scanMode === "review") {
      await handleReviewScan(data, eventId);
      return;
    }

    await handleCheckinScan(data, eventId);
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
      } catch {
        // fallback to offline
      }
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

  const frameColor = scanMode === "review" ? "#f59e0b" : "#0d9488";

  return (
    <View style={styles.container}>
      {scanning ? (
        <CameraView
          style={styles.camera}
          barcodeScannerSettings={{ barcodeTypes: ["qr"] }}
          onBarcodeScanned={(result) => handleBarCodeScanned(result.data)}
        >
          <View style={styles.overlay}>
            <View style={[styles.scanFrame, { borderColor: frameColor }]} />
            <Text style={styles.scanHint}>
              {scanMode === "review" ? "Skanuj QR — podgląd" : "Skanuj QR — zameldowanie"}
            </Text>
          </View>
          <View style={styles.modeSelectorOverlay}>
            <ScannerModeSelector mode={scanMode} onChange={setScanMode} />
          </View>
        </CameraView>
      ) : (
        <ScanResult result={scanResult} onDismiss={dismissResult} scanMode={scanMode} />
      )}

      <View style={styles.statsBar}>
        <Ionicons name="people" size={16} color="#0d9488" />
        <Text style={styles.statsText}>
          {stats.checkedIn} / {stats.total} zarejestrowanych
        </Text>
      </View>
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
  camera: { flex: 1 },
  overlay: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    backgroundColor: "rgba(0,0,0,0.4)",
  },
  modeSelectorOverlay: {
    position: "absolute",
    bottom: 0,
    left: 0,
    right: 0,
    paddingBottom: 12,
    paddingTop: 8,
    backgroundColor: "rgba(0,0,0,0.5)",
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
  statsBar: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: "#fff",
    paddingVertical: 12,
    paddingHorizontal: 16,
    gap: 6,
  },
  statsText: { fontSize: 14, color: "#1e293b", fontWeight: "500" },
});
