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
  const [reviews, setReviews] = useState<Record<number, string>>({});
  const [decision, setDecision] = useState<{ id: number; text: string; error?: boolean; finished?: boolean }>();
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

  useEffect(() => {
    const target = window.location.hash.slice(1);
    if (target.startsWith("correction-request-")) {
      document.getElementById(target)?.scrollIntoView({ block: "start" });
    }
  }, [items]);

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

  async function decide(id: number, action: "approve" | "reject" | "apply") {
    if (!loan || busy) return;
    const reason = (reviews[id] ?? "").trim();
    if (action !== "apply" && reason.length < 20) {
      setDecision({ id, text: "Enter a review reason of at least 20 characters.", error: true });
      document.getElementById(`review-reason-${id}`)?.focus();
      return;
    }
    setBusy(true);
    setDecision({ id, text: "Saving your decision…" });
    try {
      const result = (await post(
        `/loans/${loanId}/principal-amendments/${id}/${action}`,
        action === "apply" ? {} : { confirmReference: loan.referenceNumber, reason },
      )) as Amendment;
      const text = action === "reject"
        ? "Correction rejected."
        : action === "apply" || result?.status === "APPLIED"
          ? "Correction applied. The loan and accounting balances were updated."
          : "Correction approved for reconciliation.";
      setDecision({ id, text, finished: true });
      try {
        await refresh();
      } catch {
        setDecision({ id, text: `${text} The history could not refresh. Reload this page to see the latest status.`, finished: true });
      }
    } catch (err) {
      setDecision({ id, text: err instanceof Error ? err.message : "Could not save the decision.", error: true });
    } finally {
      setBusy(false);
    }
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
                Corrected value <span className="text-red-700" aria-hidden="true">*</span>
                <input required type={field.type} min={field.type === "number" ? field.key === "amount" ? 0.01 : 0 : undefined} max={field.key === "interest" || field.key === "management" ? 5 : undefined} step={field.type === "number" ? "0.01" : undefined} className="mt-1 block w-full rounded-lg border border-slate-300 p-3" value={field.value} onChange={e => field.setter(e.target.value)} />
              </label>}
            </div>
          ))}
          <label className="block text-sm font-medium">Reason for correction <span className="text-red-700" aria-hidden="true">*</span>
            <textarea required minLength={30} className="mt-1 block w-full rounded-lg border p-3" rows={3} value={reason} onChange={(e) => setReason(e.target.value)} />
          </label>
          <label className="block text-sm font-medium">Supporting document reference <span className="text-red-700" aria-hidden="true">*</span>
            <input required minLength={8} className="mt-1 block w-full rounded-lg border p-3" value={evidence} onChange={(e) => setEvidence(e.target.value)} />
          </label>
          {!selected.length && <p className="text-sm text-slate-600">Choose at least one field to correct.</p>}
          <button disabled={busy || !selected.length} onClick={submit} className="rounded-lg bg-slate-900 px-5 py-3 font-semibold text-white disabled:opacity-50">Submit to Business Owner</button>
        </section>
      )}
      <section className="space-y-3">
        <h2 className="text-lg font-semibold">Loan correction history</h2>
        {items.length === 0 && <p className="text-sm text-slate-500">No correction requests yet.</p>}
        {items.map((item) => (
          <article key={item.id} id={`correction-request-${item.id}`} className="scroll-mt-6 space-y-2 rounded-xl border border-slate-200 bg-white p-5 target:border-amber-400">
            <div className="flex flex-wrap justify-between gap-2"><strong>Request #{item.id} · {item.correctionType.replaceAll("_", " ")}</strong><span className="text-sm font-semibold">{item.status.replaceAll("_", " ")}</span></div>
            <p className="text-sm">{money(item.oldPrincipal)} → {money(item.correctedPrincipal)}</p>
            {item.oldDisbursementDate !== item.correctedDisbursementDate && <p className="text-sm">Disbursement: {item.oldDisbursementDate || "Not set"} → {item.correctedDisbursementDate || "Not set"}</p>}
            {item.oldInterestRate !== item.correctedInterestRate && <p className="text-sm">Interest: {item.oldInterestRate}% → {item.correctedInterestRate}%</p>}
            {item.oldManagementFeeRate !== item.correctedManagementFeeRate && <p className="text-sm">Management fee: {item.oldManagementFeeRate}% → {item.correctedManagementFeeRate}%</p>}
            <p className="text-sm text-slate-700">{item.reason}</p>
            <p className="text-xs text-slate-500">Evidence: {item.evidenceReference}</p>
            {item.reviewerReason && <p className="text-sm">Review: {item.reviewerReason}</p>}
            {(isOwner || user?.role === "ACCOUNTANT") && item.status === "APPROVED_FOR_RECONCILIATION" && (
              <button type="button" disabled={busy} onClick={() => void decide(item.id, "apply")}
                className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50">
                Apply approved correction
              </button>
            )}
            {isOwner && item.status === "PENDING_REVIEW" && (
              <div className="space-y-2 border-t pt-3">
                <label className="block text-sm font-medium">
                  Independent review reason (at least 20 characters) <span className="text-red-700" aria-hidden="true">*</span>
                  <textarea id={`review-reason-${item.id}`} aria-describedby={`review-feedback-${item.id}`} required minLength={20} maxLength={2000} className="mt-1 w-full rounded-lg border p-3"
                    rows={2} value={reviews[item.id] ?? ""}
                    onChange={event => {
                      if (decision?.id === item.id && decision.error) setDecision(undefined);
                      const value = event.target.value;
                      setReviews(current => ({ ...current, [item.id]: value }));
                    }}
                  />
                </label>
                <div className="flex gap-3"><button type="button" disabled={busy} onClick={() => decide(item.id, "approve")} className="rounded-lg bg-green-700 px-4 py-2 text-sm text-white">Approve for reconciliation</button>
                  <button type="button" disabled={busy} onClick={() => decide(item.id, "reject")} className="rounded-lg bg-red-700 px-4 py-2 text-sm text-white">Reject</button></div>
              </div>
            )}
            <div id={`review-feedback-${item.id}`} role={decision?.id === item.id && decision.error ? "alert" : "status"}>
              {decision?.id === item.id && (
                <div className={`mt-2 rounded-lg p-3 text-sm ${decision.error ? "bg-red-50 text-red-800" : "bg-blue-50 text-blue-900"}`}>
                  {decision.text}
                  {decision.finished && <Link href="/dashboard/tasks" className="ml-3 font-semibold underline">Back to My Tasks</Link>}
                </div>
              )}
            </div>
          </article>
        ))}
      </section>
    </main>
  );
}
