const DB_NAME = "loansaas-offline";
const DB_VERSION = 2;

const STORE_QUEUE = "pendingActions";
const STORE_CACHE = "cache";

/**
 * ============================================================
 * PENDING ACTION
 * ============================================================
 *
 * Canonical offline mutation structure.
 *
 * Financial POST/PUT/PATCH/DELETE operations can be persisted
 * here while the browser is offline and replayed later.
 *
 * Headers are intentionally persisted because operations may
 * require:
 *
 * - Idempotency-Key
 * - Content-Type
 * - tenant headers
 * - other request-specific headers
 */
export interface PendingAction {
  id: string;

  url: string;

  method: "POST" | "PUT" | "PATCH" | "DELETE";

  body?: unknown;

  /**
   * HTTP headers required when replaying the request.
   */
  headers?: Record<string, string>;

  /**
   * Human-readable queue description.
   */
  label: string;

  createdAt: string;

  /**
   * Number of replay attempts.
   */
  attempts: number;

  /**
   * Last replay error, if any.
   */
  lastError?: string;
}

/**
 * ============================================================
 * CACHED RESPONSE
 * ============================================================
 *
 * GET responses are stored under:
 *
 * {
 *   url,
 *   data,
 *   cachedAt
 * }
 *
 * IMPORTANT:
 * Consumers must access the actual response through:
 *
 * cached.data
 */
export interface CachedResponse<T = unknown> {
  url: string;

  data: T;

  cachedAt: string;
}

/**
 * ============================================================
 * OPEN DATABASE
 * ============================================================
 */

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

      /**
       * --------------------------------------------------------
       * Pending financial mutations
       * --------------------------------------------------------
       */

      if (!db.objectStoreNames.contains(STORE_QUEUE)) {
        db.createObjectStore(STORE_QUEUE, {
          keyPath: "id",
        });
      }

      /**
       * --------------------------------------------------------
       * Cached GET responses
       * --------------------------------------------------------
       */

      if (!db.objectStoreNames.contains(STORE_CACHE)) {
        db.createObjectStore(STORE_CACHE, {
          keyPath: "url",
        });
      }
    };

    request.onsuccess = () => {
      const db = request.result;

      /**
       * If another tab upgrades the database, close this
       * connection so the upgrade is not permanently blocked.
       */
      db.onversionchange = () => {
        db.close();
      };

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

/**
 * ============================================================
 * GENERIC OBJECT STORE HELPER
 * ============================================================
 */

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
          /**
           * Let the transaction error handler also handle
           * transaction-level failures. Rejecting here makes
           * request failures immediately visible.
           */
          reject(request?.error || new Error("IndexedDB operation failed."));
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

/**
 * ============================================================
 * QUEUE ACTION
 * ============================================================
 *
 * Adds an offline mutation.
 *
 * The caller may provide headers such as:
 *
 * {
 *   "Content-Type": "application/json",
 *   "Idempotency-Key": "..."
 * }
 *
 * Those headers are persisted and replayed later.
 */
export async function queueAction(
  action: Omit<PendingAction, "id" | "createdAt" | "attempts">,
): Promise<PendingAction> {
  const id =
    typeof crypto !== "undefined" && typeof crypto.randomUUID === "function"
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(36).slice(2)}`;

  const fullAction: PendingAction = {
    ...action,

    id,

    createdAt: new Date().toISOString(),

    attempts: 0,
  };

  await withStore(STORE_QUEUE, "readwrite", (store) => store.put(fullAction));

  return fullAction;
}

/**
 * ============================================================
 * GET PENDING ACTIONS
 * ============================================================
 */

export async function getPendingActions(): Promise<PendingAction[]> {
  const actions = await withStore<PendingAction[]>(
    STORE_QUEUE,
    "readonly",
    (store) => store.getAll(),
  );

  return (actions || []).sort(
    (a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime(),
  );
}

/**
 * ============================================================
 * PENDING COUNT
 * ============================================================
 *
 * Used by:
 *
 * - SyncProvider
 * - OfflineProvider
 * - dashboard offline indicators
 *
 * Returns the number of financial mutations currently
 * waiting in IndexedDB.
 */
export async function pendingCount(): Promise<number> {
  const count = await withStore<number>(STORE_QUEUE, "readonly", (store) =>
    store.count(),
  );

  return count || 0;
}

/**
 * ============================================================
 * REMOVE PENDING ACTION
 * ============================================================
 */

export async function removePendingAction(id: string): Promise<void> {
  await withStore(STORE_QUEUE, "readwrite", (store) => store.delete(id));
}

/**
 * ============================================================
 * UPDATE PENDING ACTION
 * ============================================================
 */

export async function updatePendingAction(
  action: PendingAction,
): Promise<void> {
  await withStore(STORE_QUEUE, "readwrite", (store) => store.put(action));
}

/**
 * ============================================================
 * BUMP ATTEMPT
 * ============================================================
 *
 * Increments the replay-attempt counter for a queued action.
 *
 * This is intentionally implemented against the existing
 * PendingAction structure so offlineSync.ts does not need to
 * change architecture.
 *
 * Returns the updated action.
 *
 * Returns null if the action no longer exists.
 */
export async function bumpAttempt(
  id: string,
  lastError?: string,
): Promise<PendingAction | null> {
  const action = await withStore<PendingAction | undefined>(
    STORE_QUEUE,
    "readonly",
    (store) => store.get(id),
  );

  if (!action) {
    return null;
  }

  const updated: PendingAction = {
    ...action,

    attempts: Number.isFinite(action.attempts) ? action.attempts + 1 : 1,

    ...(lastError
      ? {
          lastError,
        }
      : {}),
  };

  await withStore(STORE_QUEUE, "readwrite", (store) => store.put(updated));

  return updated;
}

/**
 * ============================================================
 * CACHE RESPONSE
 * ============================================================
 */

export async function cacheSet<T>(url: string, data: T): Promise<void> {
  const cached: CachedResponse<T> = {
    url,

    data,

    cachedAt: new Date().toISOString(),
  };

  await withStore(STORE_CACHE, "readwrite", (store) => store.put(cached));
}

/**
 * ============================================================
 * GET CACHED RESPONSE
 * ============================================================
 */

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

/**
 * ============================================================
 * DELETE CACHE
 * ============================================================
 */

export async function cacheDelete(url: string): Promise<void> {
  await withStore(STORE_CACHE, "readwrite", (store) => store.delete(url));
}

/**
 * ============================================================
 * CLEAR CACHE
 * ============================================================
 */

export async function cacheClear(): Promise<void> {
  await withStore(STORE_CACHE, "readwrite", (store) => store.clear());
}
