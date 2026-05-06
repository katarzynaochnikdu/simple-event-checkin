import { View, Text, StyleSheet, TouchableOpacity, Alert } from "react-native";
import { Ionicons } from "@expo/vector-icons";

export default function CommunicationScreen() {
  return (
    <View style={styles.container}>
      <View style={styles.card}>
        <Ionicons name="megaphone-outline" size={52} color="#7c3aed" style={styles.icon} />
        <Text style={styles.title}>Komunikacja</Text>
        <Text style={styles.subtitle}>
          Wkrótce — ogłoszenia, powiadomienia i live chat dla uczestników eventu.
        </Text>
        <TouchableOpacity
          style={styles.btn}
          onPress={() => Alert.alert("Dziękujemy!", "Twoja sugestia zostanie uwzględniona w kolejnej wersji.")}
          activeOpacity={0.8}
        >
          <Ionicons name="bulb-outline" size={16} color="#7c3aed" />
          <Text style={styles.btnText}>Zgłoś pomysł</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#f8fafc",
    alignItems: "center",
    justifyContent: "center",
    padding: 32,
  },
  card: {
    backgroundColor: "#fff",
    borderRadius: 20,
    padding: 32,
    alignItems: "center",
    width: "100%",
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.06,
    shadowRadius: 8,
    elevation: 3,
  },
  icon: { marginBottom: 16 },
  title: {
    fontSize: 22,
    fontWeight: "800",
    color: "#1e293b",
    marginBottom: 10,
  },
  subtitle: {
    fontSize: 14,
    color: "#64748b",
    textAlign: "center",
    lineHeight: 22,
    marginBottom: 24,
  },
  btn: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    borderWidth: 1,
    borderColor: "#ede9fe",
    backgroundColor: "#f5f3ff",
    borderRadius: 10,
    paddingHorizontal: 20,
    paddingVertical: 12,
  },
  btnText: { fontSize: 14, fontWeight: "600", color: "#7c3aed" },
});
