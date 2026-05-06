import { useEffect, useState } from "react";
import { Stack } from "expo-router";
import { StatusBar } from "expo-status-bar";
import * as NavigationBar from "expo-navigation-bar";
import { getToken } from "../lib/auth";
import { setApiBaseUrl } from "../lib/api";
import { EventProvider } from "../contexts/EventContext";

const API_BASE_URL = process.env.EXPO_PUBLIC_API_URL || "https://md-order-portal-backend.onrender.com";

export default function RootLayout() {
  const [ready, setReady] = useState(false);

  useEffect(() => {
    setApiBaseUrl(API_BASE_URL);
    getToken().then(() => setReady(true));
    NavigationBar.setVisibilityAsync("hidden");
    NavigationBar.setBehaviorAsync("overlay-swipe");
  }, []);

  if (!ready) return null;

  return (
    <EventProvider>
      <StatusBar style="light" />
      <Stack
        screenOptions={{
          headerStyle: { backgroundColor: "#0d9488" },
          headerTintColor: "#fff",
          headerTitleStyle: { fontWeight: "bold" },
        }}
      >
        <Stack.Screen name="login" options={{ headerShown: false }} />
        <Stack.Screen name="app-home" options={{ headerShown: false }} />
        <Stack.Screen name="events" options={{ title: "Wybierz wydarzenie" }} />
        <Stack.Screen name="profile" options={{ title: "Profil" }} />
        <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
      </Stack>
    </EventProvider>
  );
}
