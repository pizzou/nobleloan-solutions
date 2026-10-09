const DB_NAME = "loansaas-offline";
const DB_VERSION = 6;

const STORE_QUEUE = "pendingActions";
const STORE_CACHE = "cache";

/** Browser-side financial mutation queue is intentionally disabled for real-money safety. */
export const OFFLINE_FINANCIAL_MUTATIONS_ENABLED = false as const;

export type PendingActionStatus = "PENDING" | "FAILED";

/**
 * Legacy queue types are kept for source compatibility with the old sync UI.
 * Production financial mutations are deliberately not persisted or replayed
 * from browser storage; the functions below fail closed and return no queue.
 */
export interface PendingAction {
  id: string;
  url: string;
  method: "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  headers?: Record<string, string>;
  label: string;
  createdAt: string;
  attempts: number;
  lastError?: string;
  status?: PendingActionStatus;
  retryAt?: string;
}

export interface CachedResponse<T = unknown> {
  url: string;
  data: T;
  cachedAt: string;
}

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    if (typeof window === "undefined") {
      reject(
        new Error("IndexedDB is unavailable during server-side rendering."),
      );
      return;
    }

    if (!("indexedDB" in window)) {
      reject(new Error("IndexedDB is not supported by this browser."));
      return;
    }

    const request = indexedDB.open(DB_NAME, DB_VERSION);

    request.onupgradeneeded = () => {
      const db = request.result;

      if (!db.objectStoreNames.contains(STORE_QUEUE)) {
        db.createObjectStore(STORE_QUEUE, { keyPath: "id" });
      }

      if (!db.objectStoreNames.contains(STORE_CACHE)) {
        db.createObjectStore(STORE_CACHE, { keyPath: "url" });
      }

      // This release deliberately removes all legacy client-side financial
      // mutations and cached borrower/loan data. Replaying a stale queued write
      // can move money or change loan state without current server review.
      const transaction = request.transaction;
      if (transaction) {
        transaction.objectStore(STORE_QUEUE).clear();
        transaction.objectStore(STORE_CACHE).clear();
      }
    };

    request.onsuccess = () => {
      const db = request.result;
      db.onversionchange = () => db.close();
      resolve(db);
    };

    request.onerror = () => {
      reject(request.error || new Error("Unable to open offline storage."));
    };

    request.onblocked = () => {
      reject(
        new Error(
          "Offline storage upgrade is blocked by another database connection.",
        ),
      );
    };
  });
}

export function createIdempotencyKey(): string {
  if (
    typeof crypto !== "undefined" &&
    typeof crypto.randomUUID === "function"
  ) {
    return crypto.randomUUID();
  }

  return `${Date.now()}-${Math.random().toString(36).slice(2)}-${Math.random()
    .toString(36)
    .slice(2)}`;
}

export async function queueAction(
  _action: Omit<PendingAction, "id" | "createdAt" | "attempts">,
): Promise<PendingAction> {
  // Real-money writes must be checked against current balances, loan state,
  // role permissions, and idempotency records on the server. Never persist or
  // replay money movement/loan workflow mutations from a browser device.
  throw new Error(
    "This financial action requires a live connection and was not saved. Reconnect and submit it again after checking the current server state.",
  );
}

export async function getPendingActions(): Promise<PendingAction[]> {
  return [];
}

export async function getAllPendingActions(): Promise<PendingAction[]> {
  return [];
}

export async function pendingCount(): Promise<number> {
  return 0;
}

export async function failedCount(): Promise<number> {
  return 0;
}

export async function removePendingAction(_id: string): Promise<void> {
  // Offline financial writes are disabled in this release.
}

export async function updatePendingAction(
  _action: PendingAction,
): Promise<void> {
  // Offline financial writes are disabled in this release.
}

export async function bumpAttempt(
  _id: string,
  _lastError?: string,
  _retryAt?: string,
): Promise<PendingAction | null> {
  return null;
}

export async function markPendingActionFailed(
  _id: string,
  _lastError: string,
): Promise<PendingAction | null> {
  return null;
}

export async function releasePendingActionsForImmediateSync(): Promise<number> {
  return 0;
}

export async function purgeQueuedPublicLoanApplications(): Promise<number> {
  return 0;
}

export async function retryFailedAction(
  _id: string,
): Promise<PendingAction | null> {
  return null;
}

export async function retryAllFailedActions(): Promise<number> {
  return 0;
}

/** Clear legacy client-side copies of borrower data and queued financial writes. */
export async function clearSensitiveOfflineData(): Promise<void> {
  if (typeof window === "undefined" || !("indexedDB" in window)) return;

  const db = await openDb();
  await new Promise<void>((resolve, reject) => {
    let transaction: IDBTransaction;
    try {
      transaction = db.transaction([STORE_QUEUE, STORE_CACHE], "readwrite");
      transaction.objectStore(STORE_QUEUE).clear();
      transaction.objectStore(STORE_CACHE).clear();
    } catch (error) {
      db.close();
      reject(error);
      return;
    }
    transaction.oncomplete = () => {
      db.close();
      resolve();
    };
    transaction.onerror = () => {
      const error =
        transaction.error || new Error("Unable to clear offline data.");
      db.close();
      reject(error);
    };
    transaction.onabort = () => {
      const error =
        transaction.error || new Error("Offline data cleanup was aborted.");
      db.close();
      reject(error);
    };
  });
}

export async function cacheSet<T>(_url: string, _data: T): Promise<void> {
  // No customer/financial response is persisted in browser storage.
}

export async function cacheGet<T>(
  _url: string,
): Promise<CachedResponse<T> | null> {
  return null;
}

export async function cacheDelete(_url: string): Promise<void> {
  // No cached financial response is maintained.
}

export async function cacheClear(): Promise<void> {
  await clearSensitiveOfflineData();
}
