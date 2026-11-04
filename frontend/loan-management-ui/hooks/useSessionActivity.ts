"use client";

import { useEffect, useRef } from "react";
import { authApi } from "@/services/api";
import {
  SESSION_ACTIVITY_KEY,
  SESSION_IDLE_MS,
  lastSessionActivity,
  noteSessionActivity,
} from "@/lib/sessionActivity";

/** Background API polling must not extend sessions. Only recent user actions
 * authorize a refresh. Cross-tab last activity prevents one inactive tab
 * from revoking an account while the user is actively working in another.
 */
export function useSessionActivity(
  authenticated: boolean,
  onTimeout: () => void,
): void {
  const logoutRef = useRef(onTimeout);
  const ending = useRef(false);
  useEffect(() => {
    logoutRef.current = onTimeout;
  }, [onTimeout]);

  useEffect(() => {
    if (!authenticated) {
      ending.current = false;
      return;
    }
    if (!lastSessionActivity()) noteSessionActivity();
    let lastWrite = 0;
    let lastRenewal = 0;
    let renewedActivity = 0;
    let stopped = false;
    let renewing = false;
    const recordActivity = () => {
      const now = Date.now();
      if (now - lastSessionActivity() >= SESSION_IDLE_MS) {
        expire();
        return;
      }
      if (now - lastWrite >= 2000) {
        lastWrite = now;
        noteSessionActivity(now);
        check();
      }
    };
    const expire = () => {
      if (ending.current) return;
      ending.current = true;
      // The server will revoke the cookie; never clear only the UI state.
      logoutRef.current();
    };
    const check = () => {
      if (stopped || ending.current) return;
      const now = Date.now();
      const last = lastSessionActivity();
      if (!last || now - last >= SESSION_IDLE_MS) {
        expire();
        return;
      }
      // Renew once for each batch of actual interactions, including the last
      // action before the user pauses. Polling alone never extends a session.
      if (!renewing && last > renewedActivity && now - lastRenewal >= 45_000) {
        renewing = true;
        lastRenewal = now;
        const activityBeingRenewed = last;
        void authApi
          .renew()
          .then(() => {
            renewedActivity = activityBeingRenewed;
          })
          .catch(() => {
            // Temporary network/5xx errors should not force logout; when
            // truly expired the signed cookie and normal 401 handling prevail.
          })
          .finally(() => {
            renewing = false;
          });
      }
    };
    const options: AddEventListenerOptions = { passive: true, capture: true };
    const events = [
      "pointerdown",
      "pointermove",
      "keydown",
      "touchstart",
      "scroll",
    ] as const;
    events.forEach((event) =>
      window.addEventListener(event, recordActivity, options),
    );
    const handleStorage = (event: StorageEvent) => {
      if (event.key === SESSION_ACTIVITY_KEY && event.newValue === null)
        expire();
    };
    window.addEventListener("storage", handleStorage);
    const handleVisibility = () => {
      if (document.visibilityState === "visible") check();
    };
    document.addEventListener("visibilitychange", handleVisibility);
    const interval = window.setInterval(check, 10_000);
    check();
    return () => {
      stopped = true;
      window.clearInterval(interval);
      document.removeEventListener("visibilitychange", handleVisibility);
      window.removeEventListener("storage", handleStorage);
      events.forEach((event) =>
        window.removeEventListener(event, recordActivity),
      );
    };
  }, [authenticated]);
}
