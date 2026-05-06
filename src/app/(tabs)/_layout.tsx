import { Tabs } from "expo-router";
import { Ionicons } from "@expo/vector-icons";
import { useEvent } from "../../contexts/EventContext";

export default function TabsLayout() {
  const { event, isInHubMode } = useEvent();

  const eventId = event?.event_id;
  const eventName = event?.event_name;

  return (
    <Tabs
      screenOptions={{
        tabBarActiveTintColor: "#0d9488",
        tabBarInactiveTintColor: "#94a3b8",
        tabBarStyle: isInHubMode
          ? { display: "none" }
          : { backgroundColor: "#fff", borderTopColor: "#e2e8f0" },
        headerStyle: { backgroundColor: "#0d9488" },
        headerTintColor: "#fff",
        headerTitleStyle: { fontWeight: "bold" },
      }}
    >
      {/* === Visible tabs (new UX) === */}
      <Tabs.Screen
        name="overview"
        options={{
          title: "Przegląd",
          headerTitle: eventName || "Przegląd",
          href: isInHubMode ? null : eventId ? { pathname: "/(tabs)/overview" } : null,
          tabBarIcon: ({ color, size }) => (
            <Ionicons name="telescope-outline" size={size} color={color} />
          ),
        }}
      />
      <Tabs.Screen
        name="operations"
        options={{
          title: "Operacje",
          headerShown: false,
          href: isInHubMode ? null : eventId ? { pathname: "/(tabs)/operations" } : null,
          tabBarIcon: ({ color, size }) => (
            <Ionicons name="flash-outline" size={size} color={color} />
          ),
        }}
      />
      <Tabs.Screen
        name="people"
        options={{
          title: "Uczestnicy",
          headerTitle: "Uczestnicy",
          href: isInHubMode ? null : eventId ? { pathname: "/(tabs)/people" } : null,
          tabBarIcon: ({ color, size }) => (
            <Ionicons name="people-outline" size={size} color={color} />
          ),
        }}
      />
      <Tabs.Screen
        name="communication"
        options={{
          title: "Komunikacja",
          headerTitle: "Komunikacja",
          href: isInHubMode ? null : { pathname: "/(tabs)/communication" },
          tabBarIcon: ({ color, size }) => (
            <Ionicons name="megaphone-outline" size={size} color={color} />
          ),
        }}
      />
      <Tabs.Screen
        name="system"
        options={{
          title: "System",
          headerTitle: "System",
          href: isInHubMode ? null : { pathname: "/(tabs)/system" },
          tabBarIcon: ({ color, size }) => (
            <Ionicons name="settings-outline" size={size} color={color} />
          ),
        }}
      />

      {/* === Hidden legacy screens (OTA compatibility) === */}
      <Tabs.Screen name="home" options={{ href: null, tabBarButton: () => null }} />
      <Tabs.Screen name="scanner" options={{ href: null, tabBarButton: () => null }} />
      <Tabs.Screen name="inhub" options={{ href: null, tabBarButton: () => null }} />
      <Tabs.Screen name="wiecej" options={{ href: null, tabBarButton: () => null }} />
      <Tabs.Screen name="participants" options={{ href: null, tabBarButton: () => null }} />
      <Tabs.Screen name="dashboard" options={{ href: null, tabBarButton: () => null }} />
      <Tabs.Screen name="stats" options={{ href: null, tabBarButton: () => null }} />
    </Tabs>
  );
}
