import { useEffect, useRef } from "react";
import { View, Text, StyleSheet, Animated } from "react-native";
import { Ionicons } from "@expo/vector-icons";

export type OverlayResult =
  | { type: "success"; name: string; ticketName?: string }
  | { type: "already"; name: string }
  | { type: "error"; message: string };

interface Props {
  result: OverlayResult | null;
  onDismiss: () => void;
}

const AUTO_DISMISS_MS = 2000;

export default function InHubScanOverlay({ result, onDismiss }: Props) {
  const opacity = useRef(new Animated.Value(0)).current;
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (!result) {
      Animated.timing(opacity, { toValue: 0, duration: 200, useNativeDriver: true }).start();
      return;
    }
    Animated.timing(opacity, { toValue: 1, duration: 150, useNativeDriver: true }).start();
    if (timerRef.current) clearTimeout(timerRef.current);
    timerRef.current = setTimeout(() => {
      onDismiss();
    }, AUTO_DISMISS_MS);
    return () => {
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, [result]);

  if (!result) return null;

  const isSuccess = result.type === "success";
  const isAlready = result.type === "already";
  const bgColor = isSuccess ? "#059669" : isAlready ? "#f59e0b" : "#dc2626";
  const icon = isSuccess ? "checkmark-circle" : isAlready ? "alert-circle" : "close-circle";

  return (
    <Animated.View style={[styles.container, { backgroundColor: bgColor, opacity }]}>
      <Ionicons name={icon as any} size={72} color="#fff" />
      {result.type !== "error" ? (
        <>
          <Text style={styles.name}>{result.name}</Text>
          {result.type === "success" && result.ticketName ? (
            <Text style={styles.ticket}>{result.ticketName}</Text>
          ) : null}
          {result.type === "already" ? (
            <Text style={styles.ticket}>Już odznaczony</Text>
          ) : null}
        </>
      ) : (
        <Text style={styles.errorText}>{result.message}</Text>
      )}
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  container: {
    ...StyleSheet.absoluteFillObject,
    alignItems: "center",
    justifyContent: "center",
    zIndex: 100,
  },
  name: {
    fontSize: 32,
    fontWeight: "800",
    color: "#fff",
    marginTop: 16,
    textAlign: "center",
    paddingHorizontal: 24,
  },
  ticket: {
    fontSize: 18,
    color: "rgba(255,255,255,0.85)",
    marginTop: 8,
  },
  errorText: {
    fontSize: 20,
    color: "#fff",
    marginTop: 16,
    textAlign: "center",
    paddingHorizontal: 32,
  },
});
