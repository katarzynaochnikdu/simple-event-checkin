import { useEffect, useRef } from "react";
import {
  View,
  Text,
  StyleSheet,
  Animated,
  TouchableWithoutFeedback,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";

interface Props {
  active: boolean;
  onWake: () => void;
  eventName?: string;
}

export default function InHubScreensaver({ active, onWake, eventName }: Props) {
  const opacity = useRef(new Animated.Value(0)).current;
  const pulse = useRef(new Animated.Value(1)).current;

  useEffect(() => {
    Animated.timing(opacity, {
      toValue: active ? 1 : 0,
      duration: 500,
      useNativeDriver: true,
    }).start();
  }, [active]);

  useEffect(() => {
    if (!active) return;
    const loop = Animated.loop(
      Animated.sequence([
        Animated.timing(pulse, { toValue: 1.12, duration: 900, useNativeDriver: true }),
        Animated.timing(pulse, { toValue: 1, duration: 900, useNativeDriver: true }),
      ])
    );
    loop.start();
    return () => loop.stop();
  }, [active]);

  if (!active) return null;

  return (
    <TouchableWithoutFeedback onPress={onWake}>
      <Animated.View style={[styles.container, { opacity }]}>
        <Animated.View style={{ transform: [{ scale: pulse }] }}>
          <Ionicons name="qr-code-outline" size={100} color="rgba(255,255,255,0.8)" />
        </Animated.View>
        <Text style={styles.title}>Dotknij, aby skanować</Text>
        {eventName ? <Text style={styles.eventName}>{eventName}</Text> : null}
      </Animated.View>
    </TouchableWithoutFeedback>
  );
}

const styles = StyleSheet.create({
  container: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: "#0f172a",
    alignItems: "center",
    justifyContent: "center",
    zIndex: 50,
  },
  title: {
    fontSize: 26,
    fontWeight: "700",
    color: "#fff",
    marginTop: 28,
  },
  eventName: {
    fontSize: 15,
    color: "rgba(255,255,255,0.5)",
    marginTop: 10,
    textAlign: "center",
    paddingHorizontal: 32,
  },
});
