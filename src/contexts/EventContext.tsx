import { createContext, useContext, useState, ReactNode } from "react";
import type { EventItem } from "../types";

interface EventContextValue {
  event: EventItem | null;
  setEvent: (e: EventItem) => void;
  clearEvent: () => void;
  isInHubMode: boolean;
  enterInHubMode: () => void;
  exitInHubMode: () => void;
}

const EventContext = createContext<EventContextValue | null>(null);

export function EventProvider({ children }: { children: ReactNode }) {
  const [event, setEventState] = useState<EventItem | null>(null);
  const [isInHubMode, setIsInHubMode] = useState(false);

  function setEvent(e: EventItem) {
    setEventState(e);
  }

  function clearEvent() {
    setEventState(null);
    setIsInHubMode(false);
  }

  function enterInHubMode() {
    setIsInHubMode(true);
  }

  function exitInHubMode() {
    setIsInHubMode(false);
  }

  return (
    <EventContext.Provider
      value={{ event, setEvent, clearEvent, isInHubMode, enterInHubMode, exitInHubMode }}
    >
      {children}
    </EventContext.Provider>
  );
}

export function useEvent(): EventContextValue {
  const ctx = useContext(EventContext);
  if (!ctx) {
    throw new Error("useEvent must be used within EventProvider");
  }
  return ctx;
}
