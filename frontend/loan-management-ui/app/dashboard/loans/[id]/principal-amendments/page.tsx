"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { get, post } from "@/services/api";
import { getLoanById } from "@/services/loanService";
import type { Loan } from "@/types";
import { useAuth } from "@/hooks/useAuth";

type Amendment = {
  id: number;
  loanId: number;
  loanReference: string;
  correctionType: "RECORDING_ERROR" | "ADDITIONAL_DISBURSEMENT" | "CONTRACT_TERMS";
  oldPrincipal: number;
  correctedPrincipal: number;
  oldDisbursementDate?: string;
  correctedDisbursementDate?: string;
  oldInterestRate?: number;
  correctedInterestRate?: number;
  oldManagementFeeRate?: number;
  correctedManagementFeeRate?: number;
  status: "PENDING_REVIEW" | "REJECTED" | "APPROVED_FOR_RECONCILIATION" | "APPLIED";
  reason: string;
  evidenceReference: string;
  reviewerReason?: string;
  createdAt: string;
};

const money = (n: number) => `RWF ${Number(n).toLocaleString("en-RW", { maximumFractionDigits: 2 })}`;

export default function PrincipalAmendmentsPage() {
  const params = useParams();
  const loanId = Number(params.id);
  const { user, isBusinessOwner: isOwner } = useAuth();
  const canRequest = ["ADMIN", "MANAGER", "LOAN_OFFICER", "ACCOUNTANT"].includes(user?.role ?? "");
  const [loan, setLoan] = useState<Loan | null>(null);
  const [items, setItems] = useState<Amendment[]>([]);
  const [corrected, setCorrected] = useState("");
  const [selected, setSelected] = useState<string[]>([]);
  const toggle = (key: string) => setSelected(prev => prev.includes(key) ? prev.filter(v => v !== key) : [...prev, key]);
  const [disbursementDate, setDisbursementDate] = useState("");
  const [interestRate, setInterestRate] = useState("");
  const [managementFeeRate, setManagementFeeRate] = useState("");
  const [reason, setReason] = useState("");
  const [evidence, setEvidence] = useState("");
  const [review, setReview] = useState("");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [loadError, setLoadError] = useState("");

  const refresh = useCallback(async () => {
    if (!Number.isInteger(loanId) || loanId <= 0) return;
    const [current, history] = await Promise.all([
      getLoanById(loanId),
      get(`/loans/${loanId}/principal-amendments`) as Promise<Amendment[]>,
    ]);
    setLoan(current);
    setItems(history);
  }, [loanId]);

  useEffect(() => {
    refresh().catch((err) => setLoadError(err instanceof Error ? err.message : "Could not load amendments"));
  }, [refresh]);

  async function submit() {
    if (!loan || busy) return;
    const amount = Number(corrected);
    const values: Record<string, string> = {amount: corrected, date: disbursementDate, interest: interestRate, management: managementFeeRate};
    if (selected.some(key => !values[key]?.trim())) { setMessage("Enter a corrected value for each selected field."); return; }
    if (selected.includes("amount") && (!Number.isFinite(amount) || amount <= 0)) {
      setMessage("Corrected principal must be positive.");
      return;
    }
    const interest = selected.includes("interest") ? Number(interestRate) : null;
    const management = selected.includes("management") ? Number(managementFeeRate) : null;
    if ((interest != null && (!Number.isFinite(interest) || interest < 0 || interest > 5))
      || (management != null && (!Number.isFinite(management) || management < 0 || management > 5))) {
      setMessage("Negotiated interest and management rates must each be between 0% and 5%.");
      return;
    }
    if (!selected.length) {
      setMessage("Choose at least one field to correct.");
      return;
    }
    if (reason.trim().length < 30 || evidence.trim().length < 8) {
      setMessage("Explain the mistake in at least 30 characters and provide an evidence reference (8+ characters).");
      return;
    }
    setBusy(true);
    try {
      await post(`/loans/${loanId}/principal-amendments`, {
        confirmReference: loan.referenceNumber,
        correctedPrincipal: selected.includes("amount") ? amount : undefined,
        correctionType: selected.includes("amount") ? "RECORDING_ERROR" : "CONTRACT_TERMS",
        correctedDisbursementDate: selected.includes("date") ? disbursementDate : undefined,
        correctedInterestRate: interest ?? undefined,
        correctedManagementFeeRate: management ?? undefined,
        reason: reason.trim(),
        evidenceReference: evidence.trim(),
      });
      setCorrected(""); setDisbursementDate(""); setInterestRate(""); setManagementFeeRate("");
      setReason(""); setEvidence(""); setSelected([]);
      setMessage("Correction request submitted for independent Business Owner review.");
      await refresh();
    } catch (err) {
      setMessage(err instanceof Error ? err.message : "Submission failed");
    } finally { setBusy(false); }
  }

  async function decide(id: number, approved: boolean) {
    if (!loan || busy) return;
    if (review.trim().length < 20) {
      setMessage("Independent review must explain the decision in at least 20 characters.");
      return;
    }
    setBusy(true);
    try {
      await post(`/loans/${loanId}/principal-amendments/${id}/${approved ? "approve" : "reject"}`, {
        confirmReference: loan.referenceNumber,
        reason: review.trim(),
      });
      setReview("");
      setMessage(approved
        ? "Correction approved. Check the request status: APPLIED means the loan, schedules and accounting were updated; APPROVED FOR RECONCILIATION means application is still pending."
        : "Correction rejected and recorded in the audit trail.");
      await refresh();
    } catch (err) {
      setMessage(err instanceof Error ? err.message : "Review failed");
    } finally { setBusy(false); }
  }

  return (
    <main className="mx-auto max-w-4xl space-y-6 p-6">
      <Link className="text-sm text-blue-700 hover:underline" href={`/dashboard/loans/${loanId}`}>← Back to loan</Link>
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Loan correction requests</h1>
        <p className="mt-2 text-sm text-slate-600">Correct recorded loan details. Changes require Business Owner approval and successful reconciliation.</p>
        {loan && <p className="mt-2 font-medium">{loan.referenceNumber} · Current principal: {money(Number(loan.amount))}</p>}
      </div>
      {loadError && <div role="alert" className="rounded-lg border border-red-300 bg-red-50 p-3 text-sm text-red-900">{loadError}</div>}
      {message && <div role="status" className="rounded-lg border border-blue-200 bg-blue-50 p-3 text-sm text-blue-900">{message}</div>}
      {!canRequest && <p className="rounded-lg bg-slate-100 p-3 text-sm text-slate-700">Your role can review existing correction requests here, but only Admin, Manager, Loan Officer or Accountant can submit a new financial correction.</p>}
      {canRequest && loan && (
        <section className="space-y-4 rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <h2 className="text-lg font-semibold">Request a loan correction</h2>
          <p className="text-sm text-slate-500">Select only the information that needs to change.</p>
          {[
            { key: "date", label: "Loan disbursement date", current: String(loan.disbursedAt ?? "").slice(0, 10), value: disbursementDate, setter: setDisbursementDate, type: "date" },
            { key: "amount", label: "Loan amount (RWF)", current: money(Number(loan.amount)), value: corrected, setter: setCorrected, type: "number" },
            { key: "interest", label: "Monthly interest rate (%)", current: loan.interestRate == null ? "Not recorded" : loan.interestRate + "%", value: interestRate, setter: setInterestRate, type: "number" },
            { key: "management", label: "Monthly management fee (%)", current: loan.managementFeeRate == null ? "Not recorded" : loan.managementFeeRate + "%", value: managementFeeRate, setter: setManagementFeeRate, type: "number" },
          ].map(field => (
            <div key={field.key} className="rounded-lg border border-slate-200 p-4">
              <label className="flex cursor-pointer items-center gap-3 text-sm font-semibold text-slate-900">
                <input type="checkbox" checked={selected.includes(field.key)} onChange={() => toggle(field.key)} />
                {field.label}
              </label>
              <p className="ml-7 mt-1 text-xs text-slate-500">Current: {field.current || "Not recorded"}</p>
              {selected.includes(field.key) && <label className="ml-7 mt-3 block text-sm font-medium text-slate-700">
                Corrected value
                <input type={field.type} min={field.type === "number" ? field.key === "amount" ? 0.01 : 0 : undefined} max={field.key === "interest" || field.key === "management" ? 5 : undefined} step={field.type === "number" ? "0.01" : undefined} className="mt-1 block w-full rounded-lg border border-slate-300 p-3" value={field.value} onChange={e => field.setter(e.target.value)} />
              </label>}
            </div>
          ))}
          <label className="block text-sm font-medium">Reason for correction
            <textarea className="mt-1 block w-full rounded-lg border p-3" rows={3} value={reason} onChange={(e) => setReason(e.target.value)} />
          </label>
          <label className="block text-sm font-medium">Supporting document reference
            <input className="mt-1 block w-full rounded-lg border p-3" value={evidence} onChange={(e) => setEvidence(e.target.value)} />
          </label>
          <button disabled={busy || !selected.length} onClick={submit} className="rounded-lg bg-slate-900 px-5 py-3 font-semibold text-white disabled:opacity-50">Submit to Business Owner</button>
        </section>
      )}
      <section className="space-y-3">
        <h2 className="text-lg font-semibold">Loan correction history</h2>
        {items.length === 0 && <p className="text-sm text-slate-500">No correction requests yet.</p>}
        {items.map((item) => (
          <article key={item.id} className="space-y-2 rounded-xl border border-slate-200 bg-white p-5">
            <div className="flex flex-wrap justify-between gap-2"><strong>Request #{item.id} · {item.correctionType.replaceAll("_", " ")}</strong><span className="text-sm font-semibold">{item.status.replaceAll("_", " ")}</span></div>
            <p className="text-sm">{money(item.oldPrincipal)} → {money(item.correctedPrincipal)}</p>
            {item.oldDisbursementDate !== item.correctedDisbursementDate && <p className="text-sm">Disbursement: {item.oldDisbursementDate || "Not set"} → {item.correctedDisbursementDate || "Not set"}</p>}
            {item.oldInterestRate !== item.correctedInterestRate && <p className="text-sm">Interest: {item.oldInterestRate}% → {item.correctedInterestRate}%</p>}
            {item.oldManagementFeeRate !== item.correctedManagementFeeRate && <p className="text-sm">Management fee: {item.oldManagementFeeRate}% → {item.correctedManagementFeeRate}%</p>}
            <p className="text-sm text-slate-700">{item.reason}</p>
            <p className="text-xs text-slate-500">Evidence: {item.evidenceReference}</p>
            {item.reviewerReason && <p className="text-sm">Review: {item.reviewerReason}</p>}
            {isOwner && item.status === "PENDING_REVIEW" && (
              <div className="space-y-2 border-t pt-3">
                <textarea className="w-full rounded-lg border p-3" rows={2} value={review} placeholder="Independent review justification (20+ characters)" onChange={(e) => setReview(e.target.value)} />
                <div className="flex gap-3"><button disabled={busy} onClick={() => decide(item.id, true)} className="rounded-lg bg-green-700 px-4 py-2 text-sm text-white">Approve for reconciliation</button>
                  <button disabled={busy} onClick={() => decide(item.id, false)} className="rounded-lg bg-red-700 px-4 py-2 text-sm text-white">Reject</button></div>
              </div>
            )}
          </article>
        ))}
      </section>
    </main>
  );
}
