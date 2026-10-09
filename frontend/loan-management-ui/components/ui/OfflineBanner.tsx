"use client";

import { useNetworkStatus } from "../../hooks/useNetworkStatus";

/** Financial writes are online-only. This banner never claims an offline write was saved. */
export function OfflineBanner() {
  const { isOnline } = useNetworkStatus();
  if (isOnline) return null;

  return (
    <div className="fixed top-0 left-0 right-0 z-50 flex items-center justify-center gap-2 px-4 py-2 text-sm font-medium text-white bg-red-700">
      <span className="inline-block h-2 w-2 rounded-full bg-white animate-pulse" />
      You are offline. Financial changes are not saved on this device. Reconnect
      before recording a payment or changing loan status.
    </div>
  );
}
