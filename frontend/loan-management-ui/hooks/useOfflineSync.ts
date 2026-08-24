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

export async function queueAction(
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
  // Legacy callers used this to clear the old in-memory-style store.
  // Successful canonical actions are removed at sync time, so there is
  // intentionally nothing else to clear here.
}
