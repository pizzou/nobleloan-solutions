"use client";

import { useCallback, useEffect, useRef, useState } from "react";

import { checkBackendHealth, drainOfflineQueue } from "./offlineSync";
import {
  failedCount,
  pendingCount,
  releasePendingActionsForImmediateSync,
  retryAllFailedActions,
  purgeQueuedPublicLoanApplications,
} from "./offlineDb";

/**
 * Single canonical synchronization provider for the whole application.
 *
 * It deliberately separates:
 *   1. browser connectivity;
 *   2. Noble Loan API availability;
 *   3. durable queued mutations;
 *   4. actions requiring manual attention.
 */
export default function SyncProvider() {
  const [pending, setPending] = useState(0);
  const [failed, setFailed] = useState(0);
  const [syncing, setSyncing] = useState(false);
  const [backendOnline, setBackendOnline] = useState(true);
  const mounted = useRef(false);
  const running = useRef(false);
  const backendHealthyRef = useRef(true);

  const refreshState = useCallback(async () => {
    try {
      const [pendingCountValue, failedCountValue] = await Promise.all([
        pendingCount(),
        failedCount(),
      ]);

      if (mounted.current) {
        setPending(pendingCountValue);
        setFailed(failedCountValue);
      }
    } catch (error) {
      console.error("Failed to read offline synchronization state", error);
    }
  }, []);

  const getAuthHeader = useCallback((): Record<string, string> => ({}), []);

  const syncNow = useCallback(async () => {
    if (
      running.current ||
      typeof navigator === "undefined" ||
      !navigator.onLine
    ) {
      return;
    }

    const count = await pendingCount();
    if (count <= 0) {
      await refreshState();
      return;
    }

    running.current = true;
    setSyncing(true);

    try {
      const healthy = await checkBackendHealth();
      const recovered = healthy && !backendHealthyRef.current;

      backendHealthyRef.current = healthy;

      if (mounted.current) setBackendOnline(healthy);

      if (!healthy) return;

      if (recovered) {
        await releasePendingActionsForImmediateSync();
      }

      const result = await drainOfflineQueue(getAuthHeader);

      if (result.succeeded.length > 0) {
        console.info(
          `[OfflineSync] ${result.succeeded.length} queued mutation(s) synchronized successfully.`,
        );
      }

      if (result.failed.length > 0) {
        console.warn(
          "[OfflineSync] queued mutation(s) require retry/attention",
          result.failed,
        );
      }

      await refreshState();
    } catch (error) {
      console.error("Offline synchronization failed", error);
      if (mounted.current) setBackendOnline(false);
      await refreshState();
    } finally {
      running.current = false;
      if (mounted.current) setSyncing(false);
    }
  }, [getAuthHeader, refreshState]);

  const probeAndSync = useCallback(async () => {
    const queued = await pendingCount();

    if (queued <= 0) {
      await refreshState();
      return;
    }

    if (typeof navigator === "undefined" || !navigator.onLine) {
      if (mounted.current) setBackendOnline(false);
      await refreshState();
      return;
    }

    const healthy = await checkBackendHealth();

    if (healthy && !backendHealthyRef.current) {
      await releasePendingActionsForImmediateSync();
    }

    backendHealthyRef.current = healthy;

    if (mounted.current) setBackendOnline(healthy);

    if (healthy) {
      await syncNow();
    } else {
      await refreshState();
    }
  }, [refreshState, syncNow]);

  useEffect(() => {
    mounted.current = true;

    const handleOnline = () => {
      window.setTimeout(() => void probeAndSync(), 500);
    };

    const handleOffline = () => {
      backendHealthyRef.current = false;
      if (mounted.current) setBackendOnline(false);
    };

    const initializeSynchronization = async () => {
      // Remove legacy public-application mutations before ANY synchronization
      // can replay them. A public application must never be auto-submitted
      // later without the applicant completing the document step.
      await purgeQueuedPublicLoanApplications();
      await probeAndSync();
      await refreshState();
    };

    void initializeSynchronization();

    window.addEventListener("online", handleOnline);
    window.addEventListener("offline", handleOffline);

    // A backend outage does not fire the browser "offline" event. This
    // lightweight readiness probe therefore runs while queued work exists.
    const interval = window.setInterval(() => {
      void probeAndSync();
    }, 3_000);

    return () => {
      mounted.current = false;
      window.removeEventListener("online", handleOnline);
      window.removeEventListener("offline", handleOffline);
      window.clearInterval(interval);
    };
  }, [probeAndSync, refreshState]);

  const handleRetryFailed = async () => {
    // Never revive a legacy public-application mutation from the manual Retry
    // button. It is not a valid offline financial workflow.
    await purgeQueuedPublicLoanApplications();
    await retryAllFailedActions();
    await refreshState();
    await probeAndSync();
  };

  if (pending === 0 && failed === 0 && backendOnline) return null;

  const offline = typeof navigator !== "undefined" && !navigator.onLine;

  return (
    <div
      role="status"
      aria-live="polite"
      className="fixed inset-x-0 top-0 z-[9999] border-b border-slate-200/80 bg-white/95 px-4 py-2.5 shadow-sm backdrop-blur-xl"
    >
      <div className="mx-auto flex max-w-7xl items-center justify-center gap-3 text-xs font-semibold text-slate-700 sm:text-sm">
        <span
          className={`h-2.5 w-2.5 rounded-full ${
            offline || !backendOnline
              ? "bg-amber-500"
              : syncing
                ? "animate-pulse bg-blue-600"
                : "bg-emerald-500"
          }`}
        />

        {offline ? (
          <span>
            Offline mode — {pending} saved change{pending === 1 ? "" : "s"} will
            synchronize automatically when connectivity returns.
          </span>
        ) : !backendOnline && pending > 0 ? (
          <span>
            Noble Loan server unavailable — {pending} saved change
            {pending === 1 ? "" : "s"} are securely queued on this device.
          </span>
        ) : failed > 0 ? (
          <span>Some saved changes could not be synchronized.</span>
        ) : syncing ? (
          <span>
            Synchronizing {pending} saved change{pending === 1 ? "" : "s"}…
          </span>
        ) : (
          <span>Synchronization complete.</span>
        )}

        {failed > 0 && (
          <button
            type="button"
            onClick={handleRetryFailed}
            className="rounded-md border border-slate-300 bg-white px-2.5 py-1 text-xs font-bold text-slate-700 transition hover:bg-slate-50"
          >
            Retry {failed} failed item{failed === 1 ? "" : "s"}
          </button>
        )}
      </div>
    </div>
  );
}
