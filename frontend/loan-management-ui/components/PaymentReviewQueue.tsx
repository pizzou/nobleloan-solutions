"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { paymentApi } from "@/services/api";

type Review = {
  id: number;
  loanId: number;
  loanReference: string;
  amount: number;
  currency: string;
  method: string;
  reference: string;
  channel?: string;
  notes?: string;
  submittedByName: string;
  submittedAt: string;
  status: "PENDING" | "APPROVED" | "REJECTED";
  decisionReason?: string;
};

export default function PaymentReviewQueue({ owner, refresh, onPosted }: {
  owner: boolean;
  refresh: number;
  onPosted: () => void;
}) {
  const [rows, setRows] = useState<Review[]>([]);
  const [pendingOnly, setPendingOnly] = useState(owner);
  const [page, setPage] = useState(0);
  const [reload, setReload] = useState(0);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [status, setStatus] = useState("");
  const [rejectId, setRejectId] = useState<number | null>(null);
  const [reason, setReason] = useState("");

  useEffect(() => {
    let current = true;
    setLoading(true);
    setError("");
    paymentApi.reviews(pendingOnly, page)
      .then((result) => {
        if (current) setRows(Array.isArray(result) ? result as Review[] : []);
      })
      .catch((error: unknown) => {
        if (current) {
          setRows([]);
          setError(error instanceof Error ? error.message : "Payment reviews could not be loaded.");
        }
      })
      .finally(() => { if (current) setLoading(false); });
    return () => { current = false; };
  }, [page, pendingOnly, refresh, reload]);

  const amountLabel = (review: Review) =>
    `${review.currency || "RWF"} ${Number(review.amount).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

  async function decide(review: Review, accept: boolean) {
    if (busy) return;
    if (accept && !window.confirm(`Confirm you verified ${amountLabel(review)} against the bank or collection record for reference ${review.reference}. Approval posts the payment.`)) return;
    if (!accept && !reason.trim()) return;
    setBusy(true);
    setStatus("");
    setError("");
    try {
      await paymentApi.decideReview(review.id, accept, reason.trim());
      setRows((current) => pendingOnly
        ? current.filter((row) => row.id !== review.id)
        : current.map((row) => row.id === review.id
          ? { ...row, status: accept ? "APPROVED" : "REJECTED", decisionReason: accept ? undefined : reason.trim() }
          : row));
      setStatus(accept ? "Payment approved and posted." : "Payment request rejected.");
      setRejectId(null);
      setReason("");
      setReload((value) => value + 1);
      if (accept) onPosted();
    } catch (error: unknown) {
      setError(error instanceof Error ? error.message : "The decision could not be saved.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section id="payment-reviews" className="mb-6 scroll-mt-20 rounded-xl border border-slate-200 bg-white p-4 sm:p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="font-semibold text-slate-900">{owner ? "Payment reviews" : "My payment requests"}</h2>
          <p className="mt-1 text-sm text-slate-600">
            {owner ? "Verify the transaction reference against your bank or collection record before approving."
              : "The Business Owner reviews each request. Loan balances update after approval."}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {owner && (
            <select aria-label="Review status" value={pendingOnly ? "pending" : "all"} disabled={busy}
              onChange={(event) => { setPendingOnly(event.target.value === "pending"); setPage(0); }}
              className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm">
              <option value="pending">Pending only</option>
              <option value="all">All decisions</option>
            </select>
          )}
          <button type="button" onClick={() => setReload((value) => value + 1)} disabled={busy || loading}
            className="rounded-lg border border-slate-300 px-3 py-2 text-sm font-medium disabled:opacity-50">
            Refresh reviews
          </button>
        </div>
      </div>
      {status && <p role="status" className="mt-3 text-sm text-emerald-800">{status}</p>}
      {error && <p role="alert" className="mt-3 text-sm text-red-800">{error}</p>}
      {loading ? <p className="mt-4 text-sm text-slate-600">Loading payment reviews…</p> :
        rows.length === 0 ? <p className="mt-4 text-sm text-slate-600">No payment requests to show.</p> : (
          <ul className="mt-4 divide-y divide-slate-200 border-t border-slate-200">
            {rows.map((review) => (
              <li key={review.id} className="py-4">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="min-w-0">
                    <Link href={`/dashboard/loans/${review.loanId}`} className="font-semibold text-slate-900 underline underline-offset-2">
                      {review.loanReference || `Loan #${review.loanId}`}
                    </Link>
                    <p className="mt-1 text-sm tabular-nums text-slate-800">
                      {amountLabel(review)} · {review.method.replaceAll("_", " ").toLowerCase()}
                    </p>
                    <p className="mt-1 break-words text-sm text-slate-600">Reference: {review.reference}</p>
                    <p className="mt-1 text-xs text-slate-600">
                      {review.submittedByName} · {new Date(review.submittedAt).toLocaleString()}
                    </p>
                    {review.channel && <p className="mt-1 break-words text-xs text-slate-600">Channel: {review.channel}</p>}
                    {review.notes && <p className="mt-1 break-words text-sm text-slate-600">{review.notes}</p>}
                    {review.decisionReason && <p className="mt-1 break-words text-sm text-red-800">{review.decisionReason}</p>}
                  </div>
                  <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-700">
                    {review.status === "PENDING" ? "Pending verification" : review.status === "APPROVED" ? "Posted" : "Rejected"}
                  </span>
                </div>
                {owner && review.status === "PENDING" && (
                  rejectId === review.id ? (
                    <form className="mt-3 space-y-2" onSubmit={(event) => { event.preventDefault(); void decide(review, false); }}>
                      <label htmlFor={`reject-${review.id}`} className="block text-sm font-medium text-slate-900">Rejection reason</label>
                      <textarea id={`reject-${review.id}`} required maxLength={1000} value={reason} onChange={(event) => setReason(event.target.value)}
                        className="w-full max-w-xl rounded-lg border border-slate-300 p-2 text-sm" />
                      <div className="flex flex-wrap gap-2">
                        <button type="submit" disabled={busy || !reason.trim()} className="rounded-lg bg-red-800 px-3 py-2 text-sm font-semibold text-white disabled:opacity-50">Reject request</button>
                        <button type="button" disabled={busy} onClick={() => setRejectId(null)} className="rounded-lg border border-slate-300 px-3 py-2 text-sm">Cancel</button>
                      </div>
                    </form>
                  ) : (
                    <div className="mt-3 flex flex-wrap gap-2">
                      <button type="button" disabled={busy} onClick={() => void decide(review, true)} className="rounded-lg bg-[#0B1F3A] px-3 py-2 text-sm font-semibold text-white disabled:opacity-50">Approve and post</button>
                      <button type="button" disabled={busy} onClick={() => { setRejectId(review.id); setReason(""); }} className="rounded-lg border border-slate-300 px-3 py-2 text-sm font-semibold text-red-800 disabled:opacity-50">Reject</button>
                    </div>
                  )
                )}
              </li>
            ))}
          </ul>
        )}
      {(page > 0 || rows.length === 25) && (
        <div className="mt-3 flex items-center gap-3 text-sm">
          <button type="button" disabled={loading || busy || page === 0} onClick={() => setPage(page - 1)} className="rounded-lg border px-3 py-2 disabled:opacity-50">Previous</button>
          <span>Page {page + 1}</span>
          <button type="button" disabled={loading || busy || rows.length < 25} onClick={() => setPage(page + 1)} className="rounded-lg border px-3 py-2 disabled:opacity-50">Next</button>
        </div>
      )}
    </section>
  );
}
