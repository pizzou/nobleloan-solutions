"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { loanApi } from "@/services/api";
import { useAuth } from "@/hooks/useAuth";

type RecycleItem = {
  id: number;
  referenceNumber: string;
  status: string;
  deletedAt: string;
  purgeAfter: string | null;
  deletionReason: string;
  deletedBy?: number | null;
};

function formatDate(value: string | null) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

export default function LoanRecycleBinPage() {
  const { user } = useAuth();
  const [items, setItems] = useState<RecycleItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [confirmingId, setConfirmingId] = useState<number | null>(null);
  const [confirmation, setConfirmation] = useState("");
  const [query, setQuery] = useState("");

  // AuthResponse.role is a string (for example "BUSINESS_OWNER"), as
  // defined by useAuthState and services/authService. Treating it as
  // user.role.name makes a valid Business Owner appear unauthorized and
  // hides the recycle-bin list and restore controls.
  const normalizedRole = String(user?.role ?? "")
    .trim()
    .toUpperCase()
    .replace(/^ROLE_/, "")
    .replace(/[- ]/g, "_");
  const isBusinessOwner = normalizedRole === "BUSINESS_OWNER";

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      // The shared API helper unwraps ApiResponse.data before returning.
      // Therefore the normal successful shape here is already the array;
      // retain the nested-data fallback for older/mock adapters.
      const response = await loanApi.recycleBin();
      setItems(
        Array.isArray(response)
          ? response
          : Array.isArray(response?.data)
            ? response.data
            : [],
      );
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "Unable to load the recycle bin.",
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (isBusinessOwner) void load();
    else setLoading(false);
  }, [isBusinessOwner, load]);

  const filteredItems = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    if (!normalized) return items;
    return items.filter((item) =>
      [item.referenceNumber, item.status, item.deletionReason]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(normalized)),
    );
  }, [items, query]);

  const daysRemaining = (purgeAfter: string | null) => {
    if (!purgeAfter) return 0;
    const deadline = new Date(purgeAfter).getTime();
    if (!Number.isFinite(deadline)) return 0;
    return Math.max(0, Math.ceil((deadline - Date.now()) / 86_400_000));
  };

  const canRestore = (purgeAfter: string | null) => {
    if (!purgeAfter) return false;
    const deadline = new Date(purgeAfter).getTime();
    return Number.isFinite(deadline) && deadline > Date.now();
  };

  const restore = async (item: RecycleItem) => {
    if (!canRestore(item.purgeAfter)) {
      setError(
        "The 30-day restore window has expired, or permanent purge was blocked by protected contractual or regulatory evidence. This loan cannot be restored from the recycle bin.",
      );
      return;
    }
    const expected = `sudo ${item.referenceNumber}`;
    if (confirmation !== expected) {
      setError(`Type exactly: ${expected}`);
      return;
    }

    setBusyId(item.id);
    try {
      await loanApi.restore(item.id, confirmation);
      setConfirmingId(null);
      setConfirmation("");
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unable to restore the loan.");
    } finally {
      setBusyId(null);
    }
  };

  if (!isBusinessOwner) {
    return (
      <main className="p-8">
        <p className="text-sm font-semibold text-red-600">
          Only the Business Owner can access the loan recycle bin.
        </p>
      </main>
    );
  }

  return (
    <main className="space-y-6 p-6">
      <div className="flex items-center justify-between">
        <div>
          <p className="text-xs font-bold uppercase tracking-wider text-slate-500">
            Loan administration
          </p>
          <h1 className="text-2xl font-black text-slate-900">
            Loan recycle bin
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            Recycled loans remain restorable for 30 days. After the retention
            period, operational loan records are purged when no signed contract
            or regulatory evidence requires retention. Posted ledger and
            settlement history is retained in the Business Owner-only reporting
            scope.
          </p>
        </div>
        <Link
          href="/dashboard/loans"
          className="rounded-xl border border-slate-200 px-4 py-2 text-sm font-bold text-slate-700"
        >
          Back to loans
        </Link>
      </div>

      {error ? (
        <div className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          {error}
        </div>
      ) : null}

      <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white">
        {!loading && items.length > 0 ? (
          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 p-4">
            <div className="text-xs font-bold uppercase tracking-wider text-slate-500">
              {items.length} recycled loan{items.length === 1 ? "" : "s"} ·{" "}
              {filteredItems.length} shown
            </div>
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Search reference, status, reason…"
              className="w-full max-w-md rounded-xl border border-slate-200 px-3 py-2.5 text-sm"
              aria-label="Search recycle bin"
            />
          </div>
        ) : null}
        {loading ? (
          <div className="p-8 text-sm text-slate-500">Loading recycle bin…</div>
        ) : items.length === 0 ? (
          <div className="p-8 text-sm text-slate-500">
            No loans are currently in the recycle bin.
          </div>
        ) : filteredItems.length === 0 ? (
          <div className="p-8 text-sm text-slate-500">
            No recycled loans match your search.
          </div>
        ) : (
          <div className="divide-y divide-slate-100">
            {filteredItems.map((item) => (
              <div key={item.id} className="p-5">
                <div className="flex flex-wrap items-start justify-between gap-4">
                  <div>
                    <div className="font-black text-slate-900">
                      {item.referenceNumber}
                    </div>
                    <div className="mt-1 text-xs text-slate-500">
                      Status before recycle: {item.status}
                    </div>
                    <div className="mt-1 text-xs text-slate-500">
                      Recycled: {formatDate(item.deletedAt)}
                    </div>
                    <div className="mt-1 text-xs font-semibold text-amber-700">
                      {canRestore(item.purgeAfter) ? (
                        <>
                          Purge after: {formatDate(item.purgeAfter)} ·{" "}
                          {daysRemaining(item.purgeAfter)} day
                          {daysRemaining(item.purgeAfter) === 1 ? "" : "s"}{" "}
                          remaining
                        </>
                      ) : (
                        "Restore window closed; protected contractual or regulatory evidence may require retention"
                      )}
                    </div>
                  </div>
                  <button
                    type="button"
                    disabled={!canRestore(item.purgeAfter)}
                    onClick={() => {
                      setConfirmingId(item.id);
                      setConfirmation("");
                      setError(null);
                    }}
                    className="rounded-xl bg-slate-900 px-4 py-2 text-sm font-black text-white disabled:cursor-not-allowed disabled:opacity-40"
                  >
                    {canRestore(item.purgeAfter)
                      ? "Restore"
                      : "Restore unavailable"}
                  </button>
                </div>

                <div className="mt-4 rounded-xl bg-slate-50 p-3 text-sm text-slate-600">
                  <span className="font-bold text-slate-800">Reason:</span>{" "}
                  {item.deletionReason}
                </div>

                {confirmingId === item.id && canRestore(item.purgeAfter) ? (
                  <div className="mt-4 rounded-xl border border-slate-200 p-4">
                    <p className="text-sm font-semibold text-slate-700">
                      Type exactly: <code>sudo {item.referenceNumber}</code>
                    </p>
                    <input
                      value={confirmation}
                      onChange={(event) => setConfirmation(event.target.value)}
                      className="mt-3 w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm"
                      placeholder={`sudo ${item.referenceNumber}`}
                      autoComplete="off"
                    />
                    <div className="mt-3 flex justify-end gap-2">
                      <button
                        type="button"
                        onClick={() => {
                          setConfirmingId(null);
                          setConfirmation("");
                        }}
                        className="rounded-xl border border-slate-200 px-4 py-2 text-sm font-bold text-slate-700"
                      >
                        Cancel
                      </button>
                      <button
                        type="button"
                        disabled={
                          busyId === item.id ||
                          confirmation !== `sudo ${item.referenceNumber}`
                        }
                        onClick={() => void restore(item)}
                        className="rounded-xl bg-emerald-600 px-4 py-2 text-sm font-black text-white disabled:cursor-not-allowed disabled:opacity-40"
                      >
                        {busyId === item.id ? "Restoring…" : "Confirm restore"}
                      </button>
                    </div>
                  </div>
                ) : null}
              </div>
            ))}
          </div>
        )}
      </section>
    </main>
  );
}
