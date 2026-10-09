"use client";

import { useEffect } from "react";
import { clearSensitiveOfflineData } from "@/lib/offlineDb";

export default function ServiceWorkerRegistration() {
  useEffect(() => {
    let cancelled = false;

    const hardenBrowserStorage = async () => {
      // Opening version 6 triggers the migration that clears every legacy
      // queued mutation and cached loan/borrower payload from earlier releases.
      try {
        await clearSensitiveOfflineData();
      } catch (error) {
        console.error("Unable to clear legacy browser financial data", error);
      }

      // Purge caches created by earlier service-worker versions before the new
      // static-assets-only worker is installed.
      try {
        const cacheNames = await caches.keys();
        await Promise.all(cacheNames.map((name) => caches.delete(name)));
      } catch (error) {
        console.error("Unable to purge legacy browser caches", error);
      }

      if (cancelled || !("serviceWorker" in navigator)) return;
      try {
        await navigator.serviceWorker.register("/sw.js", {
          updateViaCache: "none",
        });
      } catch (error) {
        // Failure to register a worker must not block authenticated app use;
        // API requests never rely on the worker for transport/cache fallback.
        console.error("Service worker registration failed", error);
      }
    };

    void hardenBrowserStorage();
    return () => {
      cancelled = true;
    };
  }, []);

  return null;
}
