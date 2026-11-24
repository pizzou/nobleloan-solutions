"use client";

import { useState } from "react";
import { get, post } from "@/services/api";

type Money = number | string | null;
type Correction = {
  reference: string;
  asOf: string;
  needsCorrection: boolean;
  postingStatus: string;
  adjustment: {
    principal: Money;
    interest: Money;
    management: Money;
    penaltyOutstanding: Money;
  };
  corrected: {
    receipts: Array<{
      transactionId: number;
      date: string;
      amount: Money;
      penalty: Money;
      interest: Money;
      managementFee: Money;
      extensionFee: Money;
      principal: Money;
    }>;
  };
};

function rwf(amount: Money): string {
  const value = Number(amount ?? 0);
  return `RWF ${Number.isFinite(value) ?
    value.toLocaleString("en-RW", { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : "—"}`;
}

/**
 * Business Owner review of historical immutable cash receipts.
 * The preview performs no writes. The separate apply endpoint posts a
 * balanced audited non-cash adjustment only for supported verified histories.
 */
export default function LoanPolicyReconciliationPanel({
  loanId, reference, onApplied,
}: {
  loanId: number;
  reference: string;
  onApplied: () => Promise<void>;
}) {
  const [preview, setPreview] = useState<Correction | null>(null);
  const [loading, setLoading] = useState(false);
  const [applying, setApplying] = useState(false);
  const [confirmedReference, setConfirmedReference] = useState("");
  const [message, setMessage] = useState("");

  async function review() {
    setLoading(true);
    setMessage("");
    setPreview(null);
    setConfirmedReference("");
    try {
      const result = await get(`/loans/${loanId}/policy-recalculation/preview`);
      setPreview(result as Correction);
    } catch (error) {
      setMessage(error instanceof Error ? error.message :
        "Unable to preview historical payment allocation.");
    } finally {
      setLoading(false);
    }
  }

  async function apply() {
    if (!preview?.needsCorrection || preview.reference !== reference ||
        confirmedReference.trim() !== reference || applying) return;
    if (!window.confirm(
      `Apply audited non-cash reconciliation to loan ${reference}? This changes balances and posts accounting adjustments, but does not edit or duplicate the original cash receipt.`
    )) return;
    setApplying(true);
    setMessage("");
    try {
      const result = await post(`/loans/${loanId}/policy-recalculation/apply`);
      setMessage(`Financial reconciliation completed: ${String(result?.status ?? "APPLIED")}. Review accounting and the loan ledger.`);
      setPreview(null);
      setConfirmedReference("");
      await onApplied();
    } catch (error) {
      setMessage(error instanceof Error ? error.message :
        "Reconciliation was blocked. No cash receipt should be re-recorded.");
    } finally {
      setApplying(false);
    }
  }

  return (
    <section className="mt-3 max-w-3xl rounded-xl border border-slate-200 bg-white p-4 text-sm shadow-sm">
      <h2 className="font-bold text-slate-900">Historical payment reconciliation</h2>
      <p className="mt-1 text-xs text-slate-600">
        Business Owner only. Review penalty-first, all due-months fee allocation
        before any principal reduction. Original cash transactions remain immutable.
      </p>
      <button type="button" onClick={() => void review()} disabled={loading || applying}
        className="mt-3 rounded-lg border border-slate-400 px-3 py-2 text-xs font-semibold text-slate-800 disabled:opacity-50">
        {loading ? "Checking original receipts..." : "Preview corrected allocation"}
      </button>
      {message && <p role="alert" className="mt-3 text-xs text-slate-800">{message}</p>}
      {preview && (
        <div className="mt-4 space-y-3">
          <p className="text-xs font-semibold text-slate-800">
            As of {preview.asOf} · {preview.needsCorrection ? "Accounting adjustment required" : "Already consistent"}
          </p>
          <div className="grid grid-cols-2 gap-2 text-xs sm:grid-cols-4">
            <div>Principal change<strong className="block">{rwf(preview.adjustment.principal)}</strong></div>
            <div>Interest change<strong className="block">{rwf(preview.adjustment.interest)}</strong></div>
            <div>Management change<strong className="block">{rwf(preview.adjustment.management)}</strong></div>
            <div>Penalty outstanding change<strong className="block">{rwf(preview.adjustment.penaltyOutstanding)}</strong></div>
          </div>
          {preview.corrected?.receipts?.map((tx) => (
            <div key={tx.transactionId} className="rounded-lg border border-slate-100 bg-slate-50 p-3 text-xs">
              <p className="font-bold">Receipt #{tx.transactionId} · {tx.date} · {rwf(tx.amount)}</p>
              <p className="mt-1">Penalty {rwf(tx.penalty)} · Interest {rwf(tx.interest)}
                {" "}· Management {rwf(tx.managementFee)} · Principal {rwf(tx.principal)}</p>
            </div>
          ))}
          {preview.needsCorrection && (
            <div className="space-y-2 border-t border-slate-200 pt-3">
              <p className="text-xs text-amber-900">
                Apply only after checking the signed contract, original receipts,
                balances and GL journals. Unsupported multi-receipt histories are blocked.
              </p>
              <label className="block text-xs font-medium text-slate-800">
                Type exact loan reference to authorize financial reconciliation
                <input value={confirmedReference} onChange={(e) => setConfirmedReference(e.target.value)}
                  placeholder={reference} className="mt-1 block w-full rounded-lg border border-slate-300 px-3 py-2" />
              </label>
              <button type="button" onClick={() => void apply()}
                disabled={applying || confirmedReference.trim() !== reference}
                className="rounded-lg bg-slate-900 px-4 py-2 text-xs font-bold text-white disabled:opacity-50">
                {applying ? "Reconciling..." : "Apply audited non-cash correction"}
              </button>
            </div>
          )}
        </div>
      )}
    </section>
  );
}
