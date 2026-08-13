"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { loanApi } from "@/services/api";

type Loan = {
  id: number;
  referenceNumber?: string;
  reference?: string;

  borrower?: {
    id?: number;
    fullName?: string;
    firstName?: string;
    lastName?: string;
    phone?: string;
    email?: string;
  };

  borrowerName?: string;

  loanType?: string;
  status?: string;

  amount?: number;
  principal?: number;
  disbursedAmount?: number;
  netDisbursedAmount?: number;

  outstandingBalance?: number;
  totalPaid?: number;
  totalRepayable?: number;

  interestRate?: number;
  interestRateType?: string;

  managementFee?: number;
  managementFeePaid?: number;

  totalInterest?: number;
  interestPaid?: number;

  processingFee?: number;
  processingFeePaid?: number;

  currency?: string;

  nextInstallmentAmount?: number;
  nextPaymentDate?: string;
  nextDueDate?: string;
  maturityDate?: string;

  daysOverdue?: number;
  missedInstallments?: number;

  creditQuality?: string;
  arrearsStatus?: string;
  collectionsStage?: string;

  createdAt?: string;
  disbursedAt?: string;
};

type PageResult<T> = {
  content?: T[];
  totalElements?: number;
  totalPages?: number;
  number?: number;
  size?: number;
};

const STATUS_OPTIONS = [
  "",
  "PENDING",
  "UNDER_REVIEW",
  "APPROVED",
  "ACTIVE",
  "OVERDUE",
  "PAID",
  "REJECTED",
  "CLOSED",
];

const TYPE_OPTIONS = [
  "",
  "PERSONAL",
  "MORTGAGE",
  "AUTO",
  "BUSINESS",
  "STUDENT",
  "EMERGENCY",
  "ASSET_FINANCE",
  "SALARY_ADVANCE",
  "MICROFINANCE",
  "AGRICULTURAL",
  "TRADE_FINANCE",
  "GROUP",
];

const money = (value: unknown, currency = "RWF") => {
  const number = Number(value ?? 0);

  return new Intl.NumberFormat("en-RW", {
    style: "currency",
    currency,
    maximumFractionDigits: 2,
  }).format(Number.isFinite(number) ? number : 0);
};

const numberValue = (value: unknown) => {
  const n = Number(value ?? 0);
  return Number.isFinite(n) ? n : 0;
};

const dateValue = (value?: string) => {
  if (!value) return "—";

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat("en-RW", {
    year: "numeric",
    month: "short",
    day: "2-digit",
  }).format(date);
};

const borrowerName = (loan: Loan) => {
  if (loan.borrowerName) return loan.borrowerName;

  if (loan.borrower?.fullName) {
    return loan.borrower.fullName;
  }

  const name = [loan.borrower?.firstName, loan.borrower?.lastName]
    .filter(Boolean)
    .join(" ");

  return name || "Unknown borrower";
};

const reference = (loan: Loan) =>
  loan.referenceNumber || loan.reference || `LOAN-${loan.id}`;

const badgeClass = (status?: string) => {
  switch (status) {
    case "ACTIVE":
      return "bg-emerald-50 text-emerald-700 border-emerald-200";

    case "OVERDUE":
      return "bg-red-50 text-red-700 border-red-200";

    case "APPROVED":
      return "bg-blue-50 text-blue-700 border-blue-200";

    case "PAID":
    case "CLOSED":
      return "bg-slate-100 text-slate-700 border-slate-200";

    case "REJECTED":
      return "bg-red-50 text-red-700 border-red-200";

    case "UNDER_REVIEW":
      return "bg-amber-50 text-amber-700 border-amber-200";

    default:
      return "bg-gray-50 text-gray-600 border-gray-200";
  }
};

export default function LoansPage() {
  const [loans, setLoans] = useState<Loan[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const [page, setPage] = useState(0);
  const [size] = useState(20);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  const [status, setStatus] = useState("");
  const [type, setType] = useState("");
  const [search, setSearch] = useState("");

  const [selectedLoan, setSelectedLoan] = useState<Loan | null>(null);

  const [action, setAction] = useState<
    "approve" | "reject" | "disburse" | null
  >(null);

  const [actionValue, setActionValue] = useState("");
  const [actionNotes, setActionNotes] = useState("");
  const [actionLoading, setActionLoading] = useState(false);
  const [error, setError] = useState("");

  const loadLoans = useCallback(async () => {
    try {
      setError("");

      const response = await loanApi.list(page, size, status, type);

      const result = response as PageResult<Loan> | Loan[];

      if (Array.isArray(result)) {
        setLoans(result);
        setTotalElements(result.length);
        setTotalPages(1);
        return;
      }

      setLoans(result.content ?? []);
      setTotalElements(numberValue(result.totalElements));
      setTotalPages(numberValue(result.totalPages));
    } catch (err: any) {
      setError(err?.message || "Unable to load loans.");
    }
  }, [page, size, status, type]);

  useEffect(() => {
    let mounted = true;

    const run = async () => {
      setLoading(true);

      try {
        await loadLoans();
      } finally {
        if (mounted) {
          setLoading(false);
        }
      }
    };

    run();

    return () => {
      mounted = false;
    };
  }, [loadLoans]);

  const refresh = async () => {
    setRefreshing(true);

    try {
      await loadLoans();
    } finally {
      setRefreshing(false);
    }
  };

  const filteredLoans = useMemo(() => {
    const q = search.trim().toLowerCase();

    if (!q) return loans;

    return loans.filter((loan) => {
      return (
        reference(loan).toLowerCase().includes(q) ||
        borrowerName(loan).toLowerCase().includes(q) ||
        String(loan.loanType ?? "")
          .toLowerCase()
          .includes(q) ||
        String(loan.status ?? "")
          .toLowerCase()
          .includes(q)
      );
    });
  }, [loans, search]);

  const metrics = useMemo(() => {
    const active = loans.filter((l) => l.status === "ACTIVE").length;

    const overdue = loans.filter((l) => l.status === "OVERDUE").length;

    const outstanding = loans.reduce(
      (sum, loan) => sum + numberValue(loan.outstandingBalance),
      0,
    );

    const totalPaid = loans.reduce(
      (sum, loan) => sum + numberValue(loan.totalPaid),
      0,
    );

    return {
      active,
      overdue,
      outstanding,
      totalPaid,
    };
  }, [loans]);

  const openAction = (loan: Loan, type: "approve" | "reject" | "disburse") => {
    setSelectedLoan(loan);
    setAction(type);
    setActionValue("");
    setActionNotes("");
  };

  const closeAction = () => {
    if (actionLoading) return;

    setSelectedLoan(null);
    setAction(null);
    setActionValue("");
    setActionNotes("");
  };

  const executeAction = async () => {
    if (!selectedLoan || !action) return;

    setActionLoading(true);
    setError("");

    try {
      if (action === "approve") {
        const rate =
          actionValue.trim() === "" ? undefined : Number(actionValue);

        if (rate !== undefined && (!Number.isFinite(rate) || rate <= 0)) {
          throw new Error("Enter a valid positive interest rate.");
        }

        await loanApi.approve(selectedLoan.id, actionNotes, rate);
      }

      if (action === "reject") {
        if (!actionNotes.trim()) {
          throw new Error("A rejection reason is required.");
        }

        await loanApi.reject(selectedLoan.id, actionNotes.trim());
      }

      if (action === "disburse") {
        const method = actionValue.trim() || "BANK_TRANSFER";

        await loanApi.disburse(selectedLoan.id, method);
      }

      closeAction();
      await loadLoans();
    } catch (err: any) {
      setError(err?.message || "The requested action failed.");
    } finally {
      setActionLoading(false);
    }
  };

  return (
    <main className="min-h-screen bg-[#F6F8FB]">
      <div className="mx-auto max-w-[1600px] px-4 py-6 sm:px-6 lg:px-8">
        <div className="mb-7 flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <div className="mb-2 text-[11px] font-black uppercase tracking-[0.2em] text-emerald-700">
              Loan portfolio
            </div>

            <h1 className="text-3xl font-black tracking-tight text-slate-950">
              Loans
            </h1>

            <p className="mt-2 max-w-2xl text-sm text-slate-500">
              Manage applications, approvals, disbursements, outstanding
              balances and overdue accounts.
            </p>
          </div>

          <div className="flex gap-2">
            <button
              onClick={refresh}
              disabled={refreshing}
              className="rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-bold text-slate-700 shadow-sm hover:bg-slate-50 disabled:opacity-50"
            >
              {refreshing ? "Refreshing…" : "Refresh"}
            </button>

            <Link
              href="/dashboard/loans/new"
              className="rounded-xl bg-[#0D6B3E] px-4 py-2.5 text-sm font-black text-white shadow-sm hover:bg-[#095832]"
            >
              + New loan
            </Link>
          </div>
        </div>

        {error && (
          <div className="mb-5 rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-semibold text-red-700">
            {error}
          </div>
        )}

        <section className="mb-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <Metric label="Loans on page" value={loans.length.toLocaleString()} />

          <Metric label="Active" value={metrics.active.toLocaleString()} />

          <Metric label="Overdue" value={metrics.overdue.toLocaleString()} />

          <Metric label="Outstanding" value={money(metrics.outstanding)} />
        </section>

        <section className="mb-6 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
          <div className="grid gap-3 md:grid-cols-[1fr_190px_190px_auto]">
            <input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search reference, borrower or loan type…"
              className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm outline-none ring-0 focus:border-emerald-500"
            />

            <select
              value={status}
              onChange={(e) => {
                setPage(0);
                setStatus(e.target.value);
              }}
              className="rounded-xl border border-slate-200 bg-white px-3 py-3 text-sm font-semibold text-slate-700"
            >
              {STATUS_OPTIONS.map((option) => (
                <option key={option} value={option}>
                  {option || "All statuses"}
                </option>
              ))}
            </select>

            <select
              value={type}
              onChange={(e) => {
                setPage(0);
                setType(e.target.value);
              }}
              className="rounded-xl border border-slate-200 bg-white px-3 py-3 text-sm font-semibold text-slate-700"
            >
              {TYPE_OPTIONS.map((option) => (
                <option key={option} value={option}>
                  {option || "All loan types"}
                </option>
              ))}
            </select>

            <button
              onClick={() => {
                setPage(0);
                loadLoans();
              }}
              className="rounded-xl bg-slate-950 px-5 py-3 text-sm font-black text-white hover:bg-slate-800"
            >
              Apply
            </button>
          </div>
        </section>

        <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
          <div className="overflow-x-auto">
            <table className="min-w-[1200px] w-full">
              <thead className="bg-slate-50">
                <tr className="border-b border-slate-200 text-left text-[10px] font-black uppercase tracking-wider text-slate-400">
                  <th className="px-5 py-4">Loan</th>
                  <th className="px-5 py-4">Borrower</th>
                  <th className="px-5 py-4">Principal</th>
                  <th className="px-5 py-4">Outstanding</th>
                  <th className="px-5 py-4">Interest</th>
                  <th className="px-5 py-4">Due</th>
                  <th className="px-5 py-4">Risk</th>
                  <th className="px-5 py-4">Status</th>
                  <th className="px-5 py-4">Actions</th>
                </tr>
              </thead>

              <tbody>
                {loading ? (
                  <tr>
                    <td
                      colSpan={9}
                      className="px-5 py-16 text-center text-sm text-slate-400"
                    >
                      Loading portfolio…
                    </td>
                  </tr>
                ) : filteredLoans.length === 0 ? (
                  <tr>
                    <td
                      colSpan={9}
                      className="px-5 py-16 text-center text-sm text-slate-400"
                    >
                      No loans found.
                    </td>
                  </tr>
                ) : (
                  filteredLoans.map((loan) => (
                    <tr
                      key={loan.id}
                      className="border-b border-slate-100 last:border-0 hover:bg-slate-50/70"
                    >
                      <td className="px-5 py-4">
                        <Link
                          href={`/dashboard/loans/${loan.id}`}
                          className="font-black text-slate-950 hover:text-emerald-700"
                        >
                          {reference(loan)}
                        </Link>

                        <div className="mt-1 text-[11px] font-semibold text-slate-400">
                          {loan.loanType?.replace(/_/g, " ") || "—"}
                        </div>
                      </td>

                      <td className="px-5 py-4">
                        <div className="font-bold text-slate-800">
                          {borrowerName(loan)}
                        </div>

                        <div className="mt-1 text-[11px] text-slate-400">
                          {loan.borrower?.phone || loan.borrower?.email || "—"}
                        </div>
                      </td>

                      <td className="px-5 py-4 text-sm font-bold text-slate-700">
                        {money(
                          loan.amount ?? loan.principal,
                          loan.currency || "RWF",
                        )}
                      </td>

                      <td className="px-5 py-4">
                        <div className="text-sm font-black text-slate-900">
                          {money(
                            loan.outstandingBalance,
                            loan.currency || "RWF",
                          )}
                        </div>

                        <div className="mt-1 text-[10px] text-slate-400">
                          Paid {money(loan.totalPaid, loan.currency || "RWF")}
                        </div>
                      </td>

                      <td className="px-5 py-4">
                        <div className="text-sm font-bold text-slate-700">
                          {numberValue(loan.interestRate).toFixed(2)}%
                        </div>

                        <div className="mt-1 text-[10px] text-slate-400">
                          {loan.interestRateType || "MONTHLY"}
                        </div>
                      </td>

                      <td className="px-5 py-4">
                        <div className="text-sm font-bold text-slate-700">
                          {dateValue(loan.nextDueDate || loan.nextPaymentDate)}
                        </div>

                        {numberValue(loan.daysOverdue) > 0 && (
                          <div className="mt-1 text-[10px] font-black text-red-600">
                            {loan.daysOverdue} days overdue
                          </div>
                        )}
                      </td>

                      <td className="px-5 py-4">
                        <div className="text-xs font-black text-slate-700">
                          {loan.creditQuality || "CURRENT"}
                        </div>

                        <div className="mt-1 text-[10px] text-slate-400">
                          {loan.collectionsStage || "NORMAL"}
                        </div>
                      </td>

                      <td className="px-5 py-4">
                        <span
                          className={`inline-flex rounded-full border px-2.5 py-1 text-[10px] font-black uppercase ${badgeClass(
                            loan.status,
                          )}`}
                        >
                          {loan.status || "UNKNOWN"}
                        </span>
                      </td>

                      <td className="px-5 py-4">
                        <div className="flex flex-wrap gap-2">
                          <Link
                            href={`/dashboard/loans/${loan.id}`}
                            className="rounded-lg border border-slate-200 px-3 py-2 text-[11px] font-black text-slate-700 hover:bg-slate-50"
                          >
                            View
                          </Link>

                          {loan.status === "PENDING" ||
                          loan.status === "UNDER_REVIEW" ? (
                            <>
                              <button
                                onClick={() => openAction(loan, "approve")}
                                className="rounded-lg bg-emerald-600 px-3 py-2 text-[11px] font-black text-white hover:bg-emerald-700"
                              >
                                Approve
                              </button>

                              <button
                                onClick={() => openAction(loan, "reject")}
                                className="rounded-lg bg-red-600 px-3 py-2 text-[11px] font-black text-white hover:bg-red-700"
                              >
                                Reject
                              </button>
                            </>
                          ) : null}

                          {loan.status === "APPROVED" && (
                            <button
                              onClick={() => openAction(loan, "disburse")}
                              className="rounded-lg bg-blue-600 px-3 py-2 text-[11px] font-black text-white hover:bg-blue-700"
                            >
                              Disburse
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>

          <div className="flex flex-col gap-3 border-t border-slate-200 bg-slate-50 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
            <div className="text-xs font-semibold text-slate-500">
              Showing {filteredLoans.length} of {totalElements.toLocaleString()}{" "}
              loans
            </div>

            <div className="flex items-center gap-2">
              <button
                disabled={page <= 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-black disabled:opacity-40"
              >
                Previous
              </button>

              <span className="px-2 text-xs font-bold text-slate-500">
                Page {page + 1}
                {totalPages > 0 ? ` of ${totalPages}` : ""}
              </span>

              <button
                disabled={totalPages > 0 && page >= totalPages - 1}
                onClick={() => setPage((p) => p + 1)}
                className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-black disabled:opacity-40"
              >
                Next
              </button>
            </div>
          </div>
        </section>
      </div>

      {selectedLoan && action && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-4">
          <div className="w-full max-w-lg rounded-3xl bg-white p-6 shadow-2xl">
            <div className="mb-6">
              <div className="text-[10px] font-black uppercase tracking-[0.18em] text-emerald-700">
                Loan action
              </div>

              <h2 className="mt-1 text-xl font-black text-slate-950">
                {action === "approve"
                  ? "Approve loan"
                  : action === "reject"
                    ? "Reject loan"
                    : "Disburse loan"}
              </h2>

              <p className="mt-2 text-sm text-slate-500">
                {reference(selectedLoan)}
              </p>
            </div>

            {action === "approve" && (
              <div className="mb-4">
                <label className="mb-2 block text-xs font-black text-slate-600">
                  Interest rate %
                </label>

                <input
                  value={actionValue}
                  onChange={(e) => setActionValue(e.target.value)}
                  type="number"
                  min="0.01"
                  step="0.01"
                  placeholder={
                    selectedLoan.interestRate != null
                      ? String(selectedLoan.interestRate)
                      : "Optional"
                  }
                  className="w-full rounded-xl border border-slate-200 px-4 py-3 text-sm outline-none focus:border-emerald-500"
                />
              </div>
            )}

            {action === "disburse" && (
              <div className="mb-4">
                <label className="mb-2 block text-xs font-black text-slate-600">
                  Disbursement method
                </label>

                <select
                  value={actionValue || "BANK_TRANSFER"}
                  onChange={(e) => setActionValue(e.target.value)}
                  className="w-full rounded-xl border border-slate-200 px-4 py-3 text-sm font-semibold"
                >
                  <option value="BANK_TRANSFER">Bank transfer</option>
                  <option value="MOBILE_MONEY">Mobile money</option>
                  <option value="CASH">Cash</option>
                </select>
              </div>
            )}

            {action !== "disburse" && (
              <div className="mb-5">
                <label className="mb-2 block text-xs font-black text-slate-600">
                  {action === "reject" ? "Reason" : "Approval notes"}
                </label>

                <textarea
                  value={actionNotes}
                  onChange={(e) => setActionNotes(e.target.value)}
                  rows={4}
                  className="w-full resize-none rounded-xl border border-slate-200 px-4 py-3 text-sm outline-none focus:border-emerald-500"
                  placeholder={
                    action === "reject"
                      ? "Enter the rejection reason…"
                      : "Optional approval notes…"
                  }
                />
              </div>
            )}

            <div className="flex justify-end gap-3">
              <button
                onClick={closeAction}
                disabled={actionLoading}
                className="rounded-xl border border-slate-200 px-4 py-3 text-sm font-black text-slate-700"
              >
                Cancel
              </button>

              <button
                onClick={executeAction}
                disabled={actionLoading}
                className={`rounded-xl px-5 py-3 text-sm font-black text-white ${
                  action === "reject"
                    ? "bg-red-600 hover:bg-red-700"
                    : "bg-[#0D6B3E] hover:bg-[#095832]"
                } disabled:opacity-50`}
              >
                {actionLoading
                  ? "Processing…"
                  : action === "approve"
                    ? "Approve"
                    : action === "reject"
                      ? "Reject"
                      : "Disburse"}
              </button>
            </div>
          </div>
        </div>
      )}
    </main>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="text-[10px] font-black uppercase tracking-[0.16em] text-slate-400">
        {label}
      </div>

      <div className="mt-3 text-2xl font-black tracking-tight text-slate-950">
        {value}
      </div>
    </div>
  );
}
