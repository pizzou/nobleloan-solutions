"use client";

import { useOnlineStatus } from "../hooks/useOnlineStatus";

/**
 * Production policy is online-only for every mutation. This banner is
 * informational; no mutation is queued or replayed in the background.
 */
export function OfflineProvider(_props: {
  authHeader: () => Record<string, string>;
}) {
  const online = useOnlineStatus();

  if (online) return null;

  return (
    <div
      role="status"
      aria-live="polite"
      className="fixed top-0 left-0 right-0 z-[60] bg-amber-600 px-4 py-2 text-center text-xs font-semibold text-white"
    >
      You are offline. Changes are not saved. Reconnect before approving loans,
      disbursing funds, recording payments, or submitting other changes.
    </div>
  );
}
