"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { loanApi } from "@/services/api";
import { useAuth } from "@/hooks/useAuth";

type PendingCorrection = {
  id: number;
  loanId: number;
  makerId: number;
  status: string;
  reason: string;
  evidenceReference: string;
  beforeSnapshot: string;
  afterSnapshot: string;
  createdAt: string;
};

/** Reviewer inbox; actions happen on the individual loan with explicit NLS confirmation. */
export default function BusinessOwnerCorrectionInbox() {
  const { user, loading: authLoading } = useAuth();
  const isOwner = String(user?.role ?? "").toUpperCase() === "BUSINESS_OWNER";
  const [requests, setRequests] = useState<PendingCorrection[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  useEffect(() => {
    if (authLoading) return;
    if (!isOwner) {
      setLoading(false);
      return;
    }
    let active = true;
    setLoading(true);
    void loanApi
      .pendingLegacyCorrections()
      .then((data) => {
        if (active) setRequests(Array.isArray(data) ? data : []);
      })
      .catch((err) => {
        if (active)
          setError(err?.message ?? "Could not retrieve pending corrections");
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [authLoading, isOwner]);

  if (!authLoading && !isOwner) {
    return (
      <main className="p-8" role="alert">
        Only a Business Owner may review and approve imported-loan corrections.
      </main>
    );
  }
  return (
    <main className="mx-auto max-w-6xl space-y-5 p-4 sm:p-8">
      <header className="rounded-xl border border-slate-200 bg-white p-6">
        <p className="text-xs font-bold uppercase tracking-wide text-amber-700">
          Business Owner · Independent approval
        </p>
        <h1 className="mt-2 text-2xl font-bold text-slate-900">
          Pending legacy-loan corrections
        </h1>
        <p className="mt-2 text-sm text-slate-600">
          These are staff-proposed corrections to previously imported Excel
          data. A request does not change any loan or ledger balance. Open the
          loan to inspect the full before/after comparison, verify the
          supporting evidence, and enter its exact NLS reference before making a
          decision.
        </p>
      </header>
      {error && (
        <div
          role="alert"
          className="rounded border border-red-300 bg-red-50 p-4 text-red-900"
        >
          {error}
        </div>
      )}
      {loading || authLoading ? (
        <p>Loading pending correction requests…</p>
      ) : requests.length === 0 ? (
        <p className="rounded-lg border bg-white p-6 text-slate-700">
          No correction requests are pending your review.
        </p>
      ) : (
        <div className="space-y-3">
          {requests.map((item) => (
            <article
              key={item.id}
              className="rounded-lg border border-slate-200 bg-white p-5"
            >
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <h2 className="font-bold text-slate-900">
                    Loan #{item.loanId} · Correction request #{item.id}
                  </h2>
                  <p className="text-xs text-slate-500">
                    Requested by user #{item.makerId} ·{" "}
                    {item.createdAt
                      ? new Date(item.createdAt).toLocaleString()
                      : "Date unavailable"}
                  </p>
                </div>
                <Link
                  href={`/dashboard/loans/${item.loanId}/corrections`}
                  className="rounded-lg bg-amber-700 px-4 py-2 text-sm font-bold text-white hover:bg-amber-800"
                >
                  Inspect and decide
                </Link>
              </div>
              <p className="mt-3 text-sm text-slate-700">{item.reason}</p>
              <p className="mt-2 text-xs text-slate-600">
                Evidence reference: {item.evidenceReference}
              </p>
              <details className="mt-3 rounded-lg bg-slate-50 p-3 text-xs">
                <summary className="cursor-pointer font-semibold">
                  View recorded and proposed values
                </summary>
                <div className="mt-3 grid gap-3 md:grid-cols-2">
                  <pre className="overflow-auto whitespace-pre-wrap break-all">
                    {safeJson(item.beforeSnapshot)}
                  </pre>
                  <pre className="overflow-auto whitespace-pre-wrap break-all">
                    {safeJson(item.afterSnapshot)}
                  </pre>
                </div>
              </details>
            </article>
          ))}
        </div>
      )}
    </main>
  );
}

function safeJson(raw: string): string {
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return "Invalid stored review snapshot";
  }
}
