import { clearOfflineData } from "@/lib/offlineDb";

const OWNED_CACHE_PREFIXES = ["noble-public-", "loansaas-"];

/** Clear browser-persisted identity, offline data, and this app's caches. */
export async function clearSensitiveClientState(): Promise<void> {
  if (typeof window === "undefined") return;

  try { window.localStorage.clear(); } catch { /* Browser storage may be disabled. */ }
  try { window.sessionStorage.clear(); } catch { /* Browser storage may be disabled. */ }

  const cleanupTasks: Promise<unknown>[] = [
    clearOfflineData().catch(() => undefined),
  ];

  if ("caches" in window) {
    cleanupTasks.push(
      caches.keys()
        .then((keys) => Promise.all(keys
          .filter((key) => OWNED_CACHE_PREFIXES.some((prefix) => key.startsWith(prefix)))
          .map((key) => caches.delete(key))))
        .catch(() => undefined),
    );
  }

  if ("serviceWorker" in navigator) {
    cleanupTasks.push(
      navigator.serviceWorker.getRegistrations()
        .then((registrations) => {
          for (const registration of registrations) {
            registration.active?.postMessage({ type: "PURGE_RUNTIME_CACHES" });
            registration.waiting?.postMessage({ type: "PURGE_RUNTIME_CACHES" });
            registration.installing?.postMessage({ type: "PURGE_RUNTIME_CACHES" });
          }
        })
        .catch(() => undefined),
    );
  }

  await Promise.allSettled(cleanupTasks);
  window.dispatchEvent(new Event("noble:session-cleared"));
}
