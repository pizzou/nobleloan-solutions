"use client";

import { useEffect } from "react";

export default function ServiceWorkerRegistration() {
  useEffect(() => {
    if (!("serviceWorker" in navigator)) return;

    let reloadRequested = false;
    const handleMessage = (event: MessageEvent) => {
      if (event.data?.type === "SECURITY_RELOAD_REQUIRED" && !reloadRequested) {
        reloadRequested = true;
        window.location.reload();
      }
    };

    navigator.serviceWorker.addEventListener("message", handleMessage);

    navigator.serviceWorker
      .register("/sw.js", { updateViaCache: "none" })
      .then((registration) => registration.update().catch(() => undefined))
      .catch(() => {
        // The app remains usable online when service workers are unavailable.
      });

    return () => {
      navigator.serviceWorker.removeEventListener("message", handleMessage);
    };
  }, []);

  return null;
}
