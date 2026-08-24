import {
  bumpAttempt,
  getPendingActions,
  markPendingActionFailed,
  PendingAction,
  removePendingAction,
} from "./offlineDb";

const API_BASE = (
  process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api"
).replace(/\/+$/, "");

const MAX_RETRYABLE_ATTEMPTS = 12;
const HEALTH_TIMEOUT_MS = 5000;
const MAX_BACKOFF_MS = 5 * 60 * 1000;

let syncInProgress = false;

export interface SyncResult {
  succeeded: PendingAction[];
  failed: {
    action: PendingAction;
    error: string;
    status?: number;
    retryable: boolean;
  }[];
}

function buildUrl(actionUrl: string): string {
  if (actionUrl.startsWith("http://") || actionUrl.startsWith("https://")) {
    return actionUrl;
  }

  if (actionUrl.startsWith("/")) {
    return `${API_BASE}${actionUrl}`;
  }

  return `${API_BASE}/${actionUrl}`;
}

function isBrowserOnline(): boolean {
  return typeof navigator === "undefined" || navigator.onLine;
}

function getErrorMessage(status: number, responseText: string): string {
  if (responseText) {
    try {
      const parsed = JSON.parse(responseText);
      return parsed?.error || parsed?.message || parsed?.detail || responseText;
    } catch {
      return responseText;
    }
  }

  switch (status) {
    case 401:
      return "Authentication expired. Please log in again.";
    case 403:
      return "You do not have permission to perform this action.";
    case 404:
      return "The requested API endpoint was not found.";
    case 409:
      return "The action conflicts with the current server state.";
    case 422:
      return "The server rejected the submitted data.";
    case 429:
      return "Too many requests. The action will be retried later.";
    case 500:
    case 502:
    case 503:
    case 504:
      return "The Noble Loan server is temporarily unavailable.";
    default:
      return `HTTP ${status}`;
  }
}

function isRetryableStatus(status: number): boolean {
  if (status === 401 || status === 403) return false;
  if ([400, 404, 405, 409, 422].includes(status)) return false;
  return status === 408 || status === 425 || status === 429 || status >= 500;
}

function calculateBackoff(attempt: number): number {
  const base = Math.min(MAX_BACKOFF_MS, 2000 * 2 ** Math.max(0, attempt - 1));
  const jitter = Math.floor(Math.random() * Math.min(1500, base / 2));
  return Math.min(MAX_BACKOFF_MS, base + jitter);
}

function retryAtForAttempt(attempt: number): string {
  return new Date(Date.now() + calculateBackoff(attempt)).toISOString();
}

/**
 * Checks the actual Noble Loan API, not just navigator.onLine.
 * This closes the important gap where the browser has internet access
 * but Render/the backend is down.
 */
export async function checkBackendHealth(): Promise<boolean> {
  if (!isBrowserOnline()) return false;

  const controller = new AbortController();
  const timeout = window.setTimeout(
    () => controller.abort(),
    HEALTH_TIMEOUT_MS,
  );

  try {
    const response = await fetch(`${API_BASE}/actuator/health/readiness`, {
      method: "GET",
      cache: "no-store",
      signal: controller.signal,
      headers: { Accept: "application/json" },
    });

    return response.ok;
  } catch {
    return false;
  } finally {
    window.clearTimeout(timeout);
  }
}

export async function drainOfflineQueue(
  authHeader: () => Record<string, string>,
): Promise<SyncResult> {
  if (syncInProgress) {
    return { succeeded: [], failed: [] };
  }

  if (!isBrowserOnline()) {
    return { succeeded: [], failed: [] };
  }

  const backendReady = await checkBackendHealth();

  if (!backendReady) {
    return {
      succeeded: [],
      failed: [],
    };
  }

  syncInProgress = true;

  const result: SyncResult = {
    succeeded: [],
    failed: [],
  };

  try {
    const actions = await getPendingActions();

    for (const action of actions) {
      if (!isBrowserOnline()) break;

      const url = buildUrl(action.url);

      try {
        const headers: Record<string, string> = {
          Accept: "application/json",
          ...authHeader(),
          ...(action.headers || {}),
        };

        if (action.body !== undefined && action.body !== null) {
          headers["Content-Type"] = "application/json";
        }

        const response = await fetch(url, {
          method: action.method,
          headers,
          body:
            action.body !== undefined && action.body !== null
              ? JSON.stringify(action.body)
              : undefined,
          cache: "no-store",
        });

        const responseText = await response.text();

        if (!response.ok) {
          const error = getErrorMessage(response.status, responseText);
          const retryable = isRetryableStatus(response.status);

          if (!retryable) {
            // Keep a recoverable audit trail, but do not lie to the UI that
            // the action is still automatically syncing.
            const failed = await markPendingActionFailed(action.id, error);
            result.failed.push({
              action: failed || action,
              error,
              status: response.status,
              retryable: false,
            });

            // A 401/403 can affect every following action. Stop here and
            // let the user authenticate/review before continuing.
            if (response.status === 401 || response.status === 403) break;
            continue;
          }

          const nextAttempt = action.attempts + 1;

          if (nextAttempt >= MAX_RETRYABLE_ATTEMPTS) {
            const failed = await markPendingActionFailed(action.id, error);
            result.failed.push({
              action: failed || action,
              error: `${error} Automatic retries were exhausted; manual retry is required.`,
              status: response.status,
              retryable: true,
            });
            break;
          }

          const updated = await bumpAttempt(
            action.id,
            error,
            retryAtForAttempt(nextAttempt),
          );

          result.failed.push({
            action: updated || action,
            error,
            status: response.status,
            retryable: true,
          });

          // Do not execute later financial mutations while the API is
          // returning server-side failures.
          break;
        }

        await removePendingAction(action.id);
        result.succeeded.push(action);
      } catch (error) {
        const message =
          error instanceof Error ? error.message : "Network error";
        const nextAttempt = action.attempts + 1;

        if (nextAttempt >= MAX_RETRYABLE_ATTEMPTS) {
          const failed = await markPendingActionFailed(
            action.id,
            `${message} Automatic retries were exhausted; manual retry is required.`,
          );
          result.failed.push({
            action: failed || action,
            error: message,
            retryable: true,
          });
          break;
        }

        const updated = await bumpAttempt(
          action.id,
          message,
          retryAtForAttempt(nextAttempt),
        );

        result.failed.push({
          action: updated || action,
          error: message,
          retryable: true,
        });

        break;
      }
    }

    return result;
  } finally {
    syncInProgress = false;
  }
}
