import { Redirect } from "expo-router";
import { useEffect, useState } from "react";
import { getToken, removeToken, removeUser } from "../lib/auth";
import { getApiBaseUrl } from "../lib/api";

export default function Index() {
  const [checking, setChecking] = useState(true);
  const [hasToken, setHasToken] = useState(false);

  useEffect(() => {
    (async () => {
      const token = await getToken();
      if (token) {
        try {
          const res = await fetch(`${getApiBaseUrl()}/api/mobile/me`, {
            headers: { Authorization: `Bearer ${token}` },
          });
          if (res.ok) {
            setHasToken(true);
          } else {
            await removeToken();
            await removeUser();
          }
        } catch {
          setHasToken(true);
        }
      }
      setChecking(false);
    })();
  }, []);

  if (checking) return null;

  if (hasToken) {
    return <Redirect href="/app-home" />;
  }
  return <Redirect href="/login" />;
}
