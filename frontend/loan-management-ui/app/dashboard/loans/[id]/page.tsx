"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { loanApi } from "@/services/api";
import { useAuth } from "@/hooks/useAuth";

type MoneyField =
  | "principalPaid"
  | "interestPaid"
  | "interestOutstanding"
  | "managementFeePaid"
  | "managementFeeOutstanding"
  | "penaltiesAssessed"
  | "penaltiesPaid";
type Proposal = {
  id: number;
  status: string;
  makerId: number;
  checkerId?: number;
  ownerDirect?: boolean;
  reason: string;
  evidenceReference: string;
  beforeSnapshot: string;
  afterSnapshot: string;
  reviewReason?: string;
  createdAt: string;
  journalEntryId?: number;
};
const MONEY_FIELDS: { key: MoneyField; label: string }[] = [
  { key: "principalPaid", label: "Principal paid (historical)" },
  { key: "interestPaid", label: "Interest paid (historical)" },
  { key: "interestOutstanding", label: "Interest remaining" },
  { key: "managementFeePaid", label: "Management fee paid" },
  { key: "managementFeeOutstanding", label: "Management fee remaining" },
  { key: "penaltiesAssessed", label: "Penalties assessed" },
  { key: "penaltiesPaid", label: "Penalties paid" },
];
const inputClass =
  "mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-amber-500 focus:outline-none focus:ring-2 focus:ring-amber-100";

export default function LegacyCorrectionPage() {
  const router = useRouter();
  const params = useParams<{ id: string }>();
  const loanId = Number(params.id);
  const { user } = useAuth();
  const role = String(user?.role ?? "").toUpperCase();
  const owner = role === "BUSINESS_OWNER";
  const reviewer = owner;
  // Owner-only proposals must never be exposed to non-owners.
  const [loan, setLoan] = useState<any>(null);
  const canPropose =
    owner ||
    ([
      "ADMIN",
      "MANAGER",
      "ACCOUNTANT",
      "LOAN_OFFICER",
      "CREDIT_ANALYST",
    ].includes(role) &&
      !loan?.businessOwnerOnly);
  const [history, setHistory] = useState<Proposal[]>([]);
  const [form, setForm] = useState<Record<MoneyField, string>>({
    principalPaid: "",
    interestPaid: "",
    interestOutstanding: "",
    managementFeePaid: "",
    managementFeeOutstanding: "",
    penaltiesAssessed: "",
    penaltiesPaid: "",
  });
  const [due, setDue] = useState("");
  const [duration, setDuration] = useState("");
  const [reference, setReference] = useState("");
  const [reason, setReason] = useState("");
  const [evidence, setEvidence] = useState("");
  const [reviewReason, setReviewReason] = useState("");
  const [preview, setPreview] = useState<any>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(true);
  const load = async () => {
    const [l, h] = await Promise.all([
      loanApi.get(loanId),
      loanApi.correctionHistory(loanId),
    ]);
    setLoan(l);
    setHistory(Array.isArray(h) ? h : []);
    const next: Record<MoneyField, string> = {} as Record<MoneyField, string>;
    for (const field of MONEY_FIELDS)
      next[field.key] = String(l[field.key] ?? 0);
    setForm(next);
    setDue(l.nextDueDate ?? "");
    setDuration(l.durationMonths == null ? "" : String(l.durationMonths));
  };
  useEffect(() => {
    if (!Number.isSafeInteger(loanId) || loanId <= 0) {
      setError("Invalid loan");
      setLoading(false);
      return;
    }
    let active = true;
    void load()
      .catch((e) => {
        if (active) setError(String(e?.message ?? e));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loanId]);
  const payload = () => ({
    confirmReference: reference,
    reason,
    evidenceReference: evidence,
    oldestUnpaidDueDate: due || null,
    durationMonths: duration.trim() === "" ? null : Number(duration),
    ...Object.fromEntries(
      MONEY_FIELDS.map(({ key }) => [
        key,
        form[key] === "" ? null : Number(form[key]),
      ]),
    ),
  });
  const run = async (action: "preview" | "submit" | "ownerApply") => {
    setBusy(true);
    setError("");
    setMessage("");
    try {
      if (action === "preview") {
        setPreview(await loanApi.previewLegacyCorrection(loanId, payload()));
      } else if (action === "ownerApply") {
        if (!owner)
          throw new Error(
            "Only the Business Owner can apply a direct correction",
          );
        if (!preview)
          throw new Error("Preview the correction before applying it");
        if (
          !window.confirm(
            `APPLY CORRECTION NOW to ${loan.referenceNumber}? This is an audited Business Owner exception. It may post a current-period receivable adjustment. Historical payments are never overwritten.`,
          )
        )
          return;
        await loanApi.applyOwnerLegacyCorrection(loanId, payload());
        setPreview(null);
        setMessage(
          "Verified import data corrected and audited. Check the loan balances and general ledger.",
        );
        await load();
      } else {
        if (!preview)
          throw new Error("Preview the correction before submitting it");
        if (
          !window.confirm(
            `Submit correction request for ${loan.referenceNumber}? Only the Business Owner can approve and apply it. No amounts change now.`,
          )
        )
          return;
        await loanApi.submitLegacyCorrection(loanId, payload());
        setPreview(null);
        setMessage(
          "Correction request recorded. Loan and journal balances remain unchanged pending Business Owner approval.",
        );
        await load();
      }
    } catch (e: any) {
      setError(e?.message ?? "Request failed");
    } finally {
      setBusy(false);
    }
  };
  const decide = async (item: Proposal, approve: boolean) => {
    setBusy(true);
    setError("");
    setMessage("");
    try {
      if (
        !window.confirm(
          `${approve ? "APPROVE AND POST" : "REJECT"} correction #${item.id} for ${loan.referenceNumber}? This action is recorded in the audit trail.`,
        )
      )
        return;
      await loanApi.decideLegacyCorrection(loanId, item.id, approve, {
        confirmReference: reference,
        reason: reviewReason,
      });
      setMessage(
        approve
          ? "Correction approved and posted. Inspect the loan and reconciliation report."
          : "Correction rejected; no loan data was changed.",
      );
      await load();
    } catch (e: any) {
      setError(e?.message ?? "Review failed");
    } finally {
      setBusy(false);
    }
  };
  if (loading)
    return <main className="p-8">Loading loan correction controls…</main>;
  if (!loan)
    return (
      <main className="p-8 text-red-800" role="alert">
        {error || "Loan details are not available"}
      </main>
    );
  return (
    <main className="mx-auto max-w-6xl space-y-6 p-4 sm:p-8">
      <button
        onClick={() => router.push(`/dashboard/loans/${loanId}`)}
        className="text-sm font-medium text-slate-600 underline"
      >
        ← Back to loan
      </button>
      <header className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
        <p className="text-xs font-bold uppercase tracking-wider text-amber-700">
          Controlled historical data correction
        </p>
        <h1 className="mt-1 text-2xl font-extrabold text-slate-900">
          {loan?.referenceNumber ?? "Loan"}
        </h1>
        <p className="mt-2 text-sm text-slate-600">
          Correct mistaken Excel values after import, without deleting or
          reimporting loans. Staff may request correction of a normal imported
          loan for independent Business Owner approval. A Business Owner can
          also preview and directly apply a verified import correction using the
          audited one-owner exception. Owner-only loan data stays confidential.
          A correction does not invent payment receipts or reverse a real
          payment.
        </p>
        <p className="mt-2 text-sm text-rose-700">
          A Business Owner direct correction is allowed only before subsequent
          payment, schedule or accounting activity. Otherwise, use a specialist
          reconciled adjustment. Existing payment and journal history is never
          erased. The ordinary request/approval workflow still requires
          different users.
        </p>
      </header>
      {error && (
        <div
          role="alert"
          className="rounded-lg border border-red-300 bg-red-50 p-3 text-red-900"
        >
          {error}
        </div>
      )}
      {message && (
        <div
          role="status"
          className="rounded-lg border border-green-300 bg-green-50 p-3 text-green-900"
        >
          {message}
        </div>
      )}
      <section className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
        <h2 className="text-lg font-bold text-slate-900">
          Reference verification
        </h2>
        <label className="mt-2 block text-sm font-semibold text-slate-700">
          Type exact NLS loan reference to enable a request or decision
          <input
            className={inputClass}
            autoComplete="off"
            value={reference}
            onChange={(e) => setReference(e.target.value)}
            placeholder={loan?.referenceNumber}
          />
        </label>
      </section>
      {canPropose && (
        <section className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <h2 className="text-lg font-bold text-slate-900">
            1. Correct a verified imported record
          </h2>
          <p className="mt-1 text-sm text-slate-500">
            Enter verified corrected TOTALS, not differences. Keep unchanged
            values as they are. Correctly recorded original principal, borrower
            identity and NLS reference are protected. A duration correction is
            an Excel transcription repair, not a new restructuring.
          </p>
          <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {MONEY_FIELDS.map((f) => (
              <label key={f.key} className="text-sm font-medium text-slate-700">
                {f.label} · RWF
                <input
                  type="number"
                  min="0"
                  step="0.01"
                  className={inputClass}
                  value={form[f.key]}
                  onChange={(e) => {
                    setForm({ ...form, [f.key]: e.target.value });
                    setPreview(null);
                  }}
                />
              </label>
            ))}
          </div>
          <div className="mt-4 grid max-w-2xl gap-4 sm:grid-cols-2">
            <label className="block text-sm font-medium text-slate-700">
              Correct contractual duration (months, 1–6)
              <input
                type="number"
                min="1"
                max="6"
                step="1"
                className={inputClass}
                value={duration}
                onChange={(e) => {
                  setDuration(e.target.value);
                  setPreview(null);
                }}
              />
            </label>
            <label className="block text-sm font-medium text-slate-700">
              Oldest installment still unpaid
              <input
                type="date"
                className={inputClass}
                value={due}
                onChange={(e) => {
                  setDue(e.target.value);
                  setPreview(null);
                }}
              />
            </label>
          </div>
          <p className="mt-2 text-xs text-slate-500">
            A verified duration change updates maturity (original start date +
            corrected months), but will not silently regenerate accounting,
            reschedule an active transaction, or create a payment receipt.
          </p>
          <div className="mt-4 grid gap-4 md:grid-cols-2">
            <label className="text-sm font-medium text-slate-700">
              Supporting evidence reference (document/receipt ID)
              <input
                className={inputClass}
                maxLength={250}
                value={evidence}
                onChange={(e) => {
                  setEvidence(e.target.value);
                  setPreview(null);
                }}
              />
            </label>
            <label className="text-sm font-medium text-slate-700">
              Why is the stored record wrong? (30+ characters)
              <textarea
                className={inputClass}
                rows={3}
                maxLength={2000}
                value={reason}
                onChange={(e) => {
                  setReason(e.target.value);
                  setPreview(null);
                }}
              />
            </label>
          </div>
          <div className="mt-5 flex flex-wrap gap-3">
            <button
              disabled={busy || reference !== loan?.referenceNumber}
              onClick={() => void run("preview")}
              className="rounded-lg bg-slate-800 px-5 py-2 text-sm font-bold text-white disabled:opacity-40"
            >
              Preview changes
            </button>
            {!owner && (
              <button
                disabled={
                  busy || !preview || reference !== loan?.referenceNumber
                }
                onClick={() => void run("submit")}
                className="rounded-lg bg-amber-600 px-5 py-2 text-sm font-bold text-white disabled:opacity-40"
              >
                Submit to Business Owner
              </button>
            )}
            {owner && (
              <button
                disabled={
                  busy ||
                  !preview ||
                  reference !== loan?.referenceNumber ||
                  reason.trim().length < 30 ||
                  evidence.trim().length < 8
                }
                onClick={() => void run("ownerApply")}
                className="rounded-lg bg-emerald-700 px-5 py-2 text-sm font-bold text-white disabled:opacity-40"
              >
                Business Owner: Apply verified correction
              </button>
            )}
          </div>
          {preview && (
            <div className="mt-5 overflow-auto rounded-lg border border-slate-200 p-3">
              <h3 className="font-bold">Before → After (read-only preview)</h3>
              <table className="mt-2 w-full text-left text-xs">
                <thead>
                  <tr>
                    <th>Field</th>
                    <th>Current</th>
                    <th>Proposed</th>
                  </tr>
                </thead>
                <tbody>
                  {Object.keys(preview.proposed ?? {}).map((k) => (
                    <tr key={k} className="border-t">
                      <td className="py-1 font-medium">{k}</td>
                      <td>{String(preview.current?.[k] ?? "—")}</td>
                      <td>{String(preview.proposed?.[k] ?? "—")}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <p className="mt-2 text-xs text-slate-500">
                Receivable GL changes:{" "}
                {JSON.stringify(preview.receivableDeltas ?? {})}
              </p>
            </div>
          )}
        </section>
      )}
      <section className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
        <h2 className="text-lg font-bold text-slate-900">
          2. Correction requests and Business Owner decisions
        </h2>
        {owner && (
          <Link
            href="/dashboard/loans/correction-requests"
            className="mt-2 inline-block text-sm font-bold text-amber-800 underline"
          >
            View all pending Business Owner approvals
          </Link>
        )}
        {reviewer && (
          <label className="mt-3 block text-sm font-medium text-slate-700">
            Independent review reason (20+ characters)
            <textarea
              rows={2}
              className={inputClass}
              value={reviewReason}
              maxLength={2000}
              onChange={(e) => setReviewReason(e.target.value)}
            />
          </label>
        )}
        <div className="mt-4 space-y-3">
          {history.length === 0 ? (
            <p className="text-sm text-slate-500">
              No correction requests yet.
            </p>
          ) : (
            history.map((item) => (
              <article
                key={item.id}
                className="rounded-lg border border-slate-200 p-4"
              >
                <div className="flex items-center justify-between gap-2">
                  <h3 className="font-bold text-slate-900">
                    Request #{item.id}
                  </h3>
                  <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold">
                    {item.status}
                  </span>
                </div>
                <p className="mt-2 text-sm text-slate-700">{item.reason}</p>
                <p className="mt-1 text-xs text-slate-500">
                  Evidence: {item.evidenceReference} · Maker ID: {item.makerId}{" "}
                  · Requested: {item.createdAt}
                </p>
                {item.ownerDirect && (
                  <p className="mt-1 text-xs font-semibold text-amber-800">
                    Business Owner direct correction (one-person exception)
                  </p>
                )}
                {item.journalEntryId && (
                  <p className="mt-1 text-xs font-semibold text-green-700">
                    Posted adjustment journal #{item.journalEntryId}
                  </p>
                )}
                {item.reviewReason && (
                  <p className="mt-1 text-xs text-slate-600">
                    Review: {item.reviewReason}
                  </p>
                )}
                <details className="mt-2 text-xs">
                  <summary className="cursor-pointer font-semibold text-slate-700">
                    Compare recorded before / after snapshots
                  </summary>
                  <div className="mt-2 grid gap-3 md:grid-cols-2">
                    <pre className="overflow-auto rounded bg-slate-50 p-3">
                      {JSON.stringify(JSON.parse(item.beforeSnapshot), null, 2)}
                    </pre>
                    <pre className="overflow-auto rounded bg-slate-50 p-3">
                      {JSON.stringify(JSON.parse(item.afterSnapshot), null, 2)}
                    </pre>
                  </div>
                </details>
                {reviewer &&
                  item.status === "PENDING" &&
                  Number(item.makerId) !== Number(user?.userId) && (
                    <div className="mt-3 flex gap-3">
                      <button
                        disabled={
                          busy ||
                          reference !== loan?.referenceNumber ||
                          reviewReason.trim().length < 20
                        }
                        onClick={() => void decide(item, true)}
                        className="rounded-lg bg-emerald-700 px-4 py-2 text-xs font-bold text-white disabled:opacity-40"
                      >
                        Business Owner: approve and apply
                      </button>
                      <button
                        disabled={
                          busy ||
                          reference !== loan?.referenceNumber ||
                          reviewReason.trim().length < 20
                        }
                        onClick={() => void decide(item, false)}
                        className="rounded-lg border border-red-500 px-4 py-2 text-xs font-bold text-red-700 disabled:opacity-40"
                      >
                        Reject
                      </button>
                    </div>
                  )}
              </article>
            ))
          )}
        </div>
      </section>
    </main>
  );
}
