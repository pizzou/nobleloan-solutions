"use client";

import { useEffect, useState, type ReactNode } from "react";
import Link from "next/link";
import {
  getLoans,
  approveLoan,
  rejectLoan,
} from "../../../services/loanService";
import { Loan } from "../../../types/index";
import { formatInterestRate } from "../../../lib/utils";
import { PageSpinner } from "../../../components/ui/Skeleton";
import { toast } from "../../../hooks/useToast";
import DocumentsPanel from "../../../components/DocumentsPanel";
import { useAuth } from "@/hooks/useAuth";

type ApprovalDraft = {
  interestRate: string;
  processingFeeRate: string;
  notes: string;
};

export default function ApprovalsPage() {
  const { user } = useAuth();

  const [loans, setLoans] = useState<Loan[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<number | null>(null);
  const [rejectId, setRejectId] = useState<number | null>(null);
  const [rejectReason, setRejectReason] = useState("");
  const [docsOpenFor, setDocsOpenFor] = useState<number | null>(null);
  const [approvalId, setApprovalId] = useState<number | null>(null);
  const [draft, setDraft] = useState<ApprovalDraft>({
    interestRate: "5",
    processingFeeRate: "2",
    notes: "",
  });

  const isManagerOrAdmin = user?.role === "MANAGER" || user?.role === "ADMIN";

  const getMsg = (err: unknown) =>
    err instanceof Error ? err.message : "Something went wrong";

  const load = () =>
    getLoans()
      .then((all) => setLoans(all.filter((l) => l.status === "PENDING")))
      .finally(() => setLoading(false));

  useEffect(() => {
    load();
  }, []);

  const openApproval = (loan: Loan) => {
    setApprovalId(loan.id);
    setDraft({
      interestRate: loan.interestRate != null ? String(loan.interestRate) : "5",
      processingFeeRate:
        loan.processingFeeRate != null ? String(loan.processingFeeRate) : "2",
      notes: "",
    });
  };

  const closeApproval = () => {
    if (busy !== null) return;
    setApprovalId(null);
  };

  const handleApprove = async () => {
    if (approvalId == null) return;

    const interestRate = Number(draft.interestRate);
    const processingFeeRate = Number(draft.processingFeeRate);

    if (!Number.isFinite(interestRate) || interestRate < 0) {
      toast("error", "Enter a valid monthly interest rate.");
      return;
    }

    if (
      !Number.isFinite(processingFeeRate) ||
      processingFeeRate < 0 ||
      processingFeeRate > 100
    ) {
      toast("error", "Enter a valid processing fee rate between 0% and 100%.");
      return;
    }

    if (!isManagerOrAdmin && processingFeeRate !== 2) {
      toast("error", "Only a Manager or Admin may change the processing fee.");
      return;
    }

    setBusy(approvalId);

    try {
      await approveLoan(
        approvalId,
        interestRate,
        draft.notes.trim() || undefined,
        processingFeeRate,
      );

      setApprovalId(null);
      await load();
      toast("success", "Loan approved and contractual pricing locked.");
    } catch (err: unknown) {
      toast("error", getMsg(err));
    } finally {
      setBusy(null);
    }
  };

  const handleReject = async () => {
    if (!rejectId || !rejectReason.trim()) return;

    setBusy(rejectId);

    try {
      await rejectLoan(rejectId, rejectReason.trim());
      setRejectId(null);
      setRejectReason("");
      await load();
      toast("success", "Loan rejected.");
    } catch (err: unknown) {
      toast("error", getMsg(err));
    } finally {
      setBusy(null);
    }
  };

  if (loading) return <PageSpinner />;

  return (
    <div className="space-y-6">
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">
              Credit control
            </p>
            <h1 className="mt-1 text-2xl font-bold tracking-tight text-slate-900">
              Loan Approvals
            </h1>
            <p className="mt-1 text-sm text-slate-500">
              Review the final contractual pricing before authorization.
            </p>
          </div>
          <div className="rounded-xl bg-slate-50 px-4 py-3 text-right">
            <p className="text-xs text-slate-500">Pending review</p>
            <p className="text-xl font-bold text-slate-900">{loans.length}</p>
          </div>
        </div>
      </div>

      {loans.length === 0 ? (
        <div className="rounded-2xl border border-slate-200 bg-white p-16 text-center shadow-sm">
          <p className="text-3xl mb-3">✓</p>
          <p className="font-semibold text-slate-700">
            All caught up — no pending loans.
          </p>
          <p className="mt-1 text-sm text-slate-400">
            Approved loans will carry their pricing snapshot into disbursement,
            repayment, accounting, BNR and Credit Bureau reporting.
          </p>
        </div>
      ) : (
        <div className="space-y-4">
          {loans.map((loan) => (
            <div
              key={loan.id}
              className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition hover:shadow-md"
            >
              <div className="flex flex-col gap-6 xl:flex-row xl:items-start xl:justify-between">
                <div className="min-w-0 flex-1 space-y-4">
                  <div>
                    <p className="text-lg font-bold text-slate-900">
                      {loan.borrower?.firstName} {loan.borrower?.lastName}
                    </p>
                    <p className="text-xs text-slate-400">
                      Loan #{loan.id} · {loan.loanType ?? "Loan"}
                    </p>
                  </div>

                  <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                    <Metric label="Principal">
                      {loan.currency} {loan.amount?.toLocaleString()}
                    </Metric>
                    <Metric label="Interest">
                      {formatInterestRate(
                        loan.interestRate,
                        loan.interestRateType,
                      )}
                    </Metric>
                    <Metric label="Management fee">5% monthly</Metric>
                    <Metric label="Processing fee">
                      {loan.processingFeeRate ?? 2}% one-time
                    </Metric>
                  </div>

                  <div className="rounded-xl border border-slate-100 bg-slate-50 p-4 text-sm text-slate-600">
                    <div className="flex flex-wrap gap-x-6 gap-y-2">
                      <span>
                        <strong className="text-slate-800">Term:</strong>{" "}
                        {loan.durationMonths} months
                      </span>
                      <span>
                        <strong className="text-slate-800">Interest:</strong>{" "}
                        monthly contractual
                      </span>
                      <span>
                        <strong className="text-slate-800">Accrual:</strong> no
                        daily interest
                      </span>
                    </div>
                  </div>

                  {loan.notes && (
                    <p className="text-sm italic text-slate-500">
                      &quot;{loan.notes}&quot;
                    </p>
                  )}

                  {loan.borrower && (
                    <button
                      onClick={() =>
                        setDocsOpenFor(docsOpenFor === loan.id ? null : loan.id)
                      }
                      className="inline-flex items-center gap-2 text-xs font-semibold text-slate-700 hover:text-slate-900"
                    >
                      <span>📎</span>
                      {docsOpenFor === loan.id ? "Hide" : "View"} KYC &
                      documents
                      <span className="text-slate-400">
                        {docsOpenFor === loan.id ? "▲" : "▼"}
                      </span>
                    </button>
                  )}
                </div>

                <div className="flex w-full flex-col gap-2 xl:w-48">
                  {loan.borrower && (
                    <Link
                      href={`/dashboard/loans/${loan.id}`}
                      className="rounded-xl border border-slate-200 px-4 py-2.5 text-center text-sm font-semibold text-slate-700 hover:bg-slate-50"
                    >
                      Full application
                    </Link>
                  )}

                  <button
                    onClick={() => openApproval(loan)}
                    disabled={busy === loan.id}
                    className="rounded-xl bg-slate-900 px-4 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-slate-800 disabled:opacity-60"
                  >
                    Review & approve
                  </button>

                  <button
                    onClick={() => {
                      setRejectId(loan.id);
                      setRejectReason("");
                    }}
                    disabled={busy === loan.id}
                    className="rounded-xl border border-red-200 px-4 py-2.5 text-sm font-semibold text-red-600 hover:bg-red-50 disabled:opacity-60"
                  >
                    Reject
                  </button>
                </div>
              </div>

              {docsOpenFor === loan.id && loan.borrower && (
                <div className="mt-5 border-t border-slate-100 pt-5">
                  <DocumentsPanel borrowerId={loan.borrower.id} />
                </div>
              )}

              {rejectId === loan.id && (
                <div className="mt-5 rounded-xl border border-red-100 bg-red-50 p-4">
                  <textarea
                    value={rejectReason}
                    onChange={(e) => setRejectReason(e.target.value)}
                    placeholder="Reason for rejection (required)..."
                    rows={3}
                    className="w-full resize-none rounded-xl border border-red-200 bg-white p-3 text-sm outline-none focus:border-red-400"
                  />
                  <div className="mt-3 flex gap-2">
                    <button
                      onClick={handleReject}
                      disabled={!rejectReason.trim() || busy === loan.id}
                      className="rounded-lg bg-red-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-60"
                    >
                      Confirm rejection
                    </button>
                    <button
                      onClick={() => setRejectId(null)}
                      disabled={busy === loan.id}
                      className="rounded-lg border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-600"
                    >
                      Cancel
                    </button>
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {approvalId !== null && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/50 p-4 backdrop-blur-sm">
          <div className="w-full max-w-xl rounded-2xl bg-white shadow-2xl">
            <div className="border-b border-slate-100 p-6">
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">
                Final authorization
              </p>
              <h2 className="mt-1 text-xl font-bold text-slate-900">
                Confirm contractual pricing
              </h2>
              <p className="mt-1 text-sm text-slate-500">
                These values become the authoritative terms used by repayment,
                accounting, BNR and Credit Bureau reporting.
              </p>
            </div>

            <div className="space-y-5 p-6">
              <div className="grid gap-4 sm:grid-cols-2">
                <RateField
                  label="Interest rate"
                  suffix="% monthly"
                  value={draft.interestRate}
                  onChange={(value) =>
                    setDraft((d) => ({ ...d, interestRate: value }))
                  }
                />

                <RateField
                  label="Management fee"
                  suffix="% monthly"
                  value="5"
                  disabled
                />

                <RateField
                  label="Processing fee"
                  suffix="% one-time"
                  value={draft.processingFeeRate}
                  disabled={!isManagerOrAdmin}
                  onChange={(value) =>
                    setDraft((d) => ({ ...d, processingFeeRate: value }))
                  }
                />
              </div>

              <div className="rounded-xl border border-amber-100 bg-amber-50 p-4 text-sm text-amber-900">
                <strong>No daily accrual:</strong> interest and management fees
                are contractual monthly charges. The processing fee is deducted
                once when disbursement is triggered.
              </div>

              <textarea
                value={draft.notes}
                onChange={(e) =>
                  setDraft((d) => ({ ...d, notes: e.target.value }))
                }
                placeholder="Optional approval notes..."
                rows={3}
                className="w-full resize-none rounded-xl border border-slate-200 p-3 text-sm outline-none focus:border-slate-400"
              />
            </div>

            <div className="flex justify-end gap-3 border-t border-slate-100 p-6">
              <button
                onClick={closeApproval}
                disabled={busy !== null}
                className="rounded-xl border border-slate-200 px-5 py-2.5 text-sm font-semibold text-slate-700"
              >
                Cancel
              </button>
              <button
                onClick={handleApprove}
                disabled={busy !== null}
                className="rounded-xl bg-slate-900 px-5 py-2.5 text-sm font-semibold text-white disabled:opacity-60"
              >
                {busy !== null ? "Authorizing..." : "Approve & lock terms"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function Metric({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="rounded-xl border border-slate-100 bg-white p-3">
      <p className="text-[11px] font-semibold uppercase tracking-wide text-slate-400">
        {label}
      </p>
      <p className="mt-1 text-sm font-bold text-slate-800">{children}</p>
    </div>
  );
}

function RateField({
  label,
  suffix,
  value,
  disabled,
  onChange,
}: {
  label: string;
  suffix: string;
  value: string;
  disabled?: boolean;
  onChange?: (value: string) => void;
}) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-xs font-semibold text-slate-600">
        {label}
      </span>
      <div className="flex items-center rounded-xl border border-slate-200 bg-white px-3 focus-within:border-slate-400">
        <input
          type="number"
          min="0"
          max="100"
          step="0.01"
          value={value}
          disabled={disabled}
          onChange={(e) => onChange?.(e.target.value)}
          className="w-full bg-transparent py-3 text-sm font-semibold outline-none disabled:text-slate-400"
        />
        <span className="whitespace-nowrap text-xs text-slate-400">
          {suffix}
        </span>
      </div>
    </label>
  );
}
