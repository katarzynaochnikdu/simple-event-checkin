import { View, Text, StyleSheet } from "react-native";
import Svg, { Circle } from "react-native-svg";

interface Props {
  size?: number;
  strokeWidth?: number;
  progress: number; // 0–100
  color?: string;
  trackColor?: string;
  label?: string;
  sublabel?: string;
}

export default function CircularProgress({
  size = 120,
  strokeWidth = 10,
  progress,
  color = "#0d9488",
  trackColor = "#e2e8f0",
  label,
  sublabel,
}: Props) {
  const r = (size - strokeWidth) / 2;
  const cx = size / 2;
  const cy = size / 2;
  const circumference = 2 * Math.PI * r;
  const clamped = Math.min(100, Math.max(0, progress));
  const dashOffset = circumference * (1 - clamped / 100);

  return (
    <View style={[styles.container, { width: size, height: size }]}>
      <Svg width={size} height={size} style={StyleSheet.absoluteFill}>
        {/* Track */}
        <Circle
          cx={cx}
          cy={cy}
          r={r}
          stroke={trackColor}
          strokeWidth={strokeWidth}
          fill="none"
        />
        {/* Progress arc */}
        <Circle
          cx={cx}
          cy={cy}
          r={r}
          stroke={color}
          strokeWidth={strokeWidth}
          fill="none"
          strokeDasharray={`${circumference} ${circumference}`}
          strokeDashoffset={dashOffset}
          strokeLinecap="round"
          rotation="-90"
          origin={`${cx}, ${cy}`}
        />
      </Svg>
      <View style={styles.center}>
        <Text style={[styles.percent, { color }]}>{Math.round(clamped)}%</Text>
        {label ? <Text style={styles.label}>{label}</Text> : null}
        {sublabel ? <Text style={styles.sublabel}>{sublabel}</Text> : null}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    alignItems: "center",
    justifyContent: "center",
  },
  center: {
    alignItems: "center",
    justifyContent: "center",
  },
  percent: {
    fontSize: 22,
    fontWeight: "800",
  },
  label: {
    fontSize: 10,
    color: "#94a3b8",
    marginTop: 2,
    textAlign: "center",
  },
  sublabel: {
    fontSize: 10,
    color: "#94a3b8",
    textAlign: "center",
  },
});
