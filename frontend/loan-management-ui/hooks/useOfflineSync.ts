"use client";

import {
  getAllPendingActions,
  queueAction as queueDurableAction,
  removePendingAction,
} from "@/lib/offlineDb";

export type OfflineActionType = string;

export interface OfflineAction {
  id: string;
  type: OfflineActionType;
  payload: unknown;
  timestamp: number;
  synced: boolean;
  retries: number;
}

function assertOfflineSafeUrl(url: string): void {
  const normalized = url.toLowerCase();
  const forbidden = [
    "/approve",
    "/reject",
    "/disburse",
    "/status",
    "/restructure",
    "/write-off",
    "/moratorium",
  ];

  if (forbidden.some((pattern) => normalized.includes(pattern))) {
    throw new Error(
      "This financial operation requires a live server connection and cannot be queued offline.",
    );
  }
}

export async function queueAction(
  action: Parameters<typeof queueDurableAction>[0],
): Promise<void> {
  assertOfflineSafeUrl(action.url);

  await queueDurableAction(action);
}

/**
 * Legacy compatibility helper.
 *
 * New financial mutations should use the canonical durable queue directly,
 * because the legacy endpoint is intentionally not a valid production
 * financial operation.
 */
export async function queueLegacyAction(
  type: OfflineActionType,
  payload: unknown,
): Promise<void> {
  await queueDurableAction({
    url: "/offline/legacy-action",
    method: "POST",
    body: { type, payload },
    label: `Legacy offline action: ${type}`,
  });
}

export async function getPendingActions(): Promise<OfflineAction[]> {
  const actions = await getAllPendingActions();

  return actions.map((action) => ({
    id: action.id,
    type: action.method,
    payload: action.body,
    timestamp: new Date(action.createdAt).getTime(),
    synced: action.status === "FAILED",
    retries: action.attempts,
  }));
}

export async function markSynced(id: string): Promise<void> {
  await removePendingAction(id);
}

export async function clearSynced(): Promise<void> {
  // Successful canonical actions are removed during synchronization.
}
