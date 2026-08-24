const DB_NAME = "loansaas-offline";
const DB_VERSION = 3;

const STORE_QUEUE = "pendingActions";
const STORE_CACHE = "cache";

export type PendingActionStatus = "PENDING" | "FAILED";

/**
 * Durable client-side mutation.
 *
 * IMPORTANT FOR FINANCIAL OPERATIONS:
 * - the id is generated once and never changes;
 * - Idempotency-Key, when present, is persisted with the action;
 * - retryAt prevents aggressive retry loops;
 * - FAILED actions are retained for manual recovery but are no longer
 *   reported as silently "waiting to sync".
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

function withStore<T>(
  storeName: string,
  mode: IDBTransactionMode,
  operation: (store: IDBObjectStore) => IDBRequest | void,
): Promise<T> {
  return new Promise(async (resolve, reject) => {
    let db: IDBDatabase | null = null;

    try {
      db = await openDb();
      const transaction = db.transaction(storeName, mode);
      const store = transaction.objectStore(storeName);
      let request: IDBRequest | void;

      try {
        request = operation(store);
      } catch (error) {
        db.close();
        reject(error);
        return;
      }

      let result: unknown;

      if (request) {
        request.onsuccess = () => {
          result = request.result;
        };
        request.onerror = () => {
          reject(request.error || new Error("IndexedDB operation failed."));
        };
      }

      transaction.oncomplete = () => {
        db?.close();
        resolve(result as T);
      };

      transaction.onerror = () => {
        const error =
          transaction.error || new Error("IndexedDB transaction failed.");
        db?.close();
        reject(error);
      };

      transaction.onabort = () => {
        const error =
          transaction.error || new Error("IndexedDB transaction was aborted.");
        db?.close();
        reject(error);
      };
    } catch (error) {
      db?.close();
      reject(error);
    }
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
  action: Omit<PendingAction, "id" | "createdAt" | "attempts">,
): Promise<PendingAction> {
  const actionId = createIdempotencyKey();

  const fullAction: PendingAction = {
    ...action,
    id: actionId,
    headers: {
      ...(action.headers || {}),
      "Idempotency-Key": action.headers?.["Idempotency-Key"] || actionId,
    },
    createdAt: new Date().toISOString(),
    attempts: 0,
    status: action.status ?? "PENDING",
  };

  await withStore(STORE_QUEUE, "readwrite", (store) => store.put(fullAction));
  return fullAction;
}

export async function getPendingActions(): Promise<PendingAction[]> {
  const actions = await withStore<PendingAction[]>(
    STORE_QUEUE,
    "readonly",
    (store) => store.getAll(),
  );

  const now = Date.now();

  return (actions || [])
    .map((action) => ({
      ...action,
      status: action.status ?? "PENDING",
    }))
    .filter((action) => {
      if (action.status !== "PENDING") return false;
      if (!action.retryAt) return true;
      return new Date(action.retryAt).getTime() <= now;
    })
    .sort(
      (a, b) =>
        new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime(),
    );
}

export async function getAllPendingActions(): Promise<PendingAction[]> {
  const actions = await withStore<PendingAction[]>(
    STORE_QUEUE,
    "readonly",
    (store) => store.getAll(),
  );

  return (actions || [])
    .map((action) => ({
      ...action,
      status: action.status ?? "PENDING",
    }))
    .sort(
      (a, b) =>
        new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime(),
    );
}

export async function pendingCount(): Promise<number> {
  const actions = await getAllPendingActions();
  return actions.filter((action) => (action.status ?? "PENDING") === "PENDING")
    .length;
}

export async function failedCount(): Promise<number> {
  const actions = await getAllPendingActions();
  return actions.filter((action) => action.status === "FAILED").length;
}

export async function removePendingAction(id: string): Promise<void> {
  await withStore(STORE_QUEUE, "readwrite", (store) => store.delete(id));
}

export async function updatePendingAction(
  action: PendingAction,
): Promise<void> {
  await withStore(STORE_QUEUE, "readwrite", (store) => store.put(action));
}

export async function bumpAttempt(
  id: string,
  lastError?: string,
  retryAt?: string,
): Promise<PendingAction | null> {
  const action = await withStore<PendingAction | undefined>(
    STORE_QUEUE,
    "readonly",
    (store) => store.get(id),
  );

  if (!action) return null;

  const updated: PendingAction = {
    ...action,
    attempts: Number.isFinite(action.attempts) ? action.attempts + 1 : 1,
    status: "PENDING",
    ...(lastError ? { lastError } : {}),
    ...(retryAt ? { retryAt } : {}),
  };

  await updatePendingAction(updated);
  return updated;
}

export async function markPendingActionFailed(
  id: string,
  lastError: string,
): Promise<PendingAction | null> {
  const action = await withStore<PendingAction | undefined>(
    STORE_QUEUE,
    "readonly",
    (store) => store.get(id),
  );

  if (!action) return null;

  const updated: PendingAction = {
    ...action,
    status: "FAILED",
    lastError,
    retryAt: undefined,
  };

  await updatePendingAction(updated);
  return updated;
}

/**
 * Makes every pending action immediately eligible for synchronization.
 *
 * This is intentionally used when the backend transitions from unavailable
 * to healthy. A previous exponential backoff must not delay a financial
 * mutation after connectivity has genuinely returned. The action id and
 * idempotency key remain unchanged.
 */
export async function releasePendingActionsForImmediateSync(): Promise<number> {
  const actions = await getAllPendingActions();
  const pending = actions.filter(
    (action) => (action.status ?? "PENDING") === "PENDING",
  );

  for (const action of pending) {
    await updatePendingAction({
      ...action,
      retryAt: undefined,
    });
  }

  return pending.length;
}

export async function retryFailedAction(
  id: string,
): Promise<PendingAction | null> {
  const action = await withStore<PendingAction | undefined>(
    STORE_QUEUE,
    "readonly",
    (store) => store.get(id),
  );

  if (!action) return null;

  const updated: PendingAction = {
    ...action,
    status: "PENDING",
    retryAt: new Date().toISOString(),
    lastError: undefined,
  };

  await updatePendingAction(updated);
  return updated;
}

export async function retryAllFailedActions(): Promise<number> {
  const actions = await getAllPendingActions();
  const failed = actions.filter((action) => action.status === "FAILED");

  for (const action of failed) {
    await retryFailedAction(action.id);
  }

  return failed.length;
}

export async function cacheSet<T>(url: string, data: T): Promise<void> {
  const cached: CachedResponse<T> = {
    url,
    data,
    cachedAt: new Date().toISOString(),
  };

  await withStore(STORE_CACHE, "readwrite", (store) => store.put(cached));
}

export async function cacheGet<T>(
  url: string,
): Promise<CachedResponse<T> | null> {
  const result = await withStore<CachedResponse<T> | undefined>(
    STORE_CACHE,
    "readonly",
    (store) => store.get(url),
  );

  return result || null;
}

export async function cacheDelete(url: string): Promise<void> {
  await withStore(STORE_CACHE, "readwrite", (store) => store.delete(url));
}

export async function cacheClear(): Promise<void> {
  await withStore(STORE_CACHE, "readwrite", (store) => store.clear());
}
