import { clearSensitiveOfflineData } from "@/lib/offlineDb";

/**
 * Best-effort client-side cleanup after logout/session invalidation. Server-side
 * session revocation remains authoritative; this only removes local residue.
 */
export async function clearSensitiveClientState(): Promise<void> {
  if (typeof window === "undefined") return;

  try {
    await clearSensitiveOfflineData();
  } catch (error) {
    // Continue to remove other browser data even if IndexedDB is unavailable.
    console.warn("Could not clear offline financial data", error);
  }

  try { window.localStorage.clear(); } catch { /* storage may be disabled */ }
  try { window.sessionStorage.clear(); } catch { /* storage may be disabled */ }

  try {
    if ("caches" in window) {
      const cacheNames = await window.caches.keys();
      await Promise.all(cacheNames.map((name) => window.caches.delete(name)));
    }
  } catch (error) {
    console.warn("Could not clear browser caches", error);
  }

  try {
    if ("serviceWorker" in navigator) {
      const registrations = await navigator.serviceWorker.getRegistrations();
      await Promise.all(registrations.map((registration) => registration.unregister()));
    }
  } catch (error) {
    console.warn("Could not unregister old service workers", error);
  }
}
