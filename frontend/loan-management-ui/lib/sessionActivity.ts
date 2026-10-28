/** Session activity is presentation state only. Authorization remains in
 * a 5-minute signed HttpOnly JWT and is independently checked server-side. */
export const SESSION_IDLE_MS = 5 * 60 * 1000;
export const SESSION_ACTIVITY_KEY = "nls:last-authenticated-activity";

export function lastSessionActivity(): number {
  if (typeof window === "undefined") return 0;
  const value = Number(window.localStorage.getItem(SESSION_ACTIVITY_KEY));
  return Number.isFinite(value) && value > 0 ? value : 0;
}

export function noteSessionActivity(now: number = Date.now()): void {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(SESSION_ACTIVITY_KEY, String(now));
}
