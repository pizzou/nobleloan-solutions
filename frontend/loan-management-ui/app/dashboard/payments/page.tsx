"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { paymentApi, loanApi } from "@/services/api";
import { PageSpinner } from "@/components/ui/Skeleton";
import type { DashboardStats } from "@/types";
import {
  IconAlertTriangle,
  IconCheckCircle,
  IconClock,
  IconCoins,
  IconSearch,
  IconSend,
} from "@/components/ui/Icons";

type Loan = {
  id: number;
  referenceNumber?: string;
  status?: string;
  currency?: string;
  borrower?: {
    fullName?: string;
  };
  borrowerName?: string;
  outstandingBalance?: number;
  nextInstallmentAmount?: number;
};

type Payment = {
  id?: number;
  installmentNumber?: number;
  amount?: number;
  amountPaid?: number;

  principalComponent?: number;
  interestComponent?: number;
  penalty?: number;

  cycleInterestDue?: number;
  cycleInterestRemaining?: number;

  outstandingAfter?: number;

  paid?: boolean;
  status?: string;

  dueDate?: string;
  paidDate?: string;

  paymentMethod?: string;
  transactionId?: string;
  paymentReference?: string;
  channel?: string;
  notes?: string;

  daysLate?: number;
  interestCalculationDate?: string;
};

const money = (value: unknown, currency = "RWF") => {
  const n = Number(value ?? 0);

  return new Intl.NumberFormat("en-RW", {
    style: "currency",
    currency,
    maximumFractionDigits: 2,
  }).format(Number.isFinite(n) ? n : 0);
};

const num = (value: unknown) => {
  const n = Number(value ?? 0);
  return Number.isFinite(n) ? n : 0;
};

const formatDate = (value?: string) => {
  if (!value) return "—";

  const parsed = new Date(value);

  if (Number.isNaN(parsed.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat("en-RW", {
    year: "numeric",
    month: "short",
    day: "2-digit",
  }).format(parsed);
};

const formatDateTime = (value?: string) => {
  if (!value) return "—";

  const parsed = new Date(value);

  if (Number.isNaN(parsed.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat("en-RW", {
    year: "numeric",
    month: "short",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(parsed);
};

const createIdempotencyKey = () => `WEB-${Date.now()}-${crypto.randomUUID()}`;

const getBorrowerName = (loan: Loan) =>
  loan.borrower?.fullName ||
  loan.borrowerName ||
  "Borrower information unavailable";

const humanize = (value?: string) =>
  value
    ? value
        .replace(/_/g, " ")
        .toLowerCase()
        .replace(/\b\w/g, (letter) => letter.toUpperCase())
    : "—";

function StatusBadge({ status }: { status?: string }) {
  const normalized = status?.toUpperCase();

  const styles =
    normalized === "PAID" || normalized === "COMPLETED"
      ? "border-emerald-200 bg-emerald-50 text-emerald-700"
      : normalized === "OVERDUE" || normalized === "DEFAULTED"
        ? "border-red-200 bg-red-50 text-red-700"
        : normalized === "PARTIAL"
          ? "border-amber-200 bg-amber-50 text-amber-700"
          : "border-slate-200 bg-slate-50 text-slate-600";

  return (
    <span
      className={`inline-flex items-center rounded-full border px-2.5 py-1 text-[10px] font-black uppercase tracking-[0.08em] ${styles}`}
    >
      {humanize(status || "PENDING")}
    </span>
  );
}

function MetricCard({
  label,
  value,
  description,
  tone = "slate",
  icon,
}: {
  label: string;
  value: string;
  description: string;
  tone?: "teal" | "blue" | "amber" | "red" | "slate";
  icon: React.ReactNode;
}) {
  const tones = {
    teal: "border-teal-100 bg-teal-50/70 text-teal-700",
    blue: "border-blue-100 bg-blue-50/70 text-blue-700",
    amber: "border-amber-100 bg-amber-50/70 text-amber-700",
    red: "border-red-100 bg-red-50/70 text-red-700",
    slate: "border-slate-200 bg-slate-50 text-slate-700",
  };

  return (
    <div className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm transition duration-200 hover:-translate-y-0.5 hover:shadow-md">
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <p className="text-[10px] font-black uppercase tracking-[0.16em] text-slate-400">
            {label}
          </p>

          <p className="mt-2 truncate text-xl font-black tracking-tight text-slate-950">
            {value}
          </p>

          <p className="mt-1 text-xs leading-5 text-slate-500">{description}</p>
        </div>

        <div
          className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border ${tones[tone]}`}
        >
          {icon}
        </div>
      </div>
    </div>
  );
}

function Field({
  label,
  value,
  onChange,
  type = "text",
  placeholder,
  required = false,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: string;
  placeholder?: string;
  required?: boolean;
}) {
  return (
    <div>
      <label className="mb-2 block text-xs font-bold text-slate-700">
        {label}
        {required ? <span className="ml-1 text-red-500">*</span> : null}
      </label>

      <input
        type={type}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        className="h-11 w-full rounded-xl border border-slate-200 bg-white px-3.5 text-sm font-medium text-slate-900 outline-none transition placeholder:text-slate-400 hover:border-slate-300 focus:border-teal-500 focus:ring-4 focus:ring-teal-500/10"
      />
    </div>
  );
}

function EmptySchedule() {
  return (
    <div className="flex flex-col items-center justify-center px-6 py-20 text-center">
      <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-slate-100 text-slate-400">
        <IconClock className="h-6 w-6" />
      </div>

      <h3 className="mt-4 text-sm font-black text-slate-900">
        No payment schedule found
      </h3>

      <p className="mt-1 max-w-md text-sm leading-6 text-slate-500">
        This loan does not currently have a schedule available from the
        collections service.
      </p>
    </div>
  );
}

export default function PaymentsPage() {
  const [loanIdInput, setLoanIdInput] = useState("");
  const [loan, setLoan] = useState<Loan | null>(null);
  const [payments, setPayments] = useState<Payment[]>([]);

  const [loadingLoan, setLoadingLoan] = useState(false);
  const [saving, setSaving] = useState(false);

  const [amount, setAmount] = useState("");
  const [paymentMethod, setPaymentMethod] = useState("BANK_TRANSFER");
  const [transactionId, setTransactionId] = useState("");
  const [channel, setChannel] = useState("MANUAL");
  const [notes, setNotes] = useState("");

  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [portfolioStats, setPortfolioStats] = useState<DashboardStats | null>(
    null,
  );
  const [confirming, setConfirming] = useState(false);

  useEffect(() => {
    let mounted = true;

    void loanApi
      .dashboard()
      .then((stats) => {
        if (mounted) {
          setPortfolioStats(stats);
        }
      })
      .catch((err) => {
        console.warn("Unable to load portfolio collection controls", err);
      });

    return () => {
      mounted = false;
    };
  }, []);

  const loadLoan = useCallback(async (id: number) => {
    setLoadingLoan(true);
    setError("");
    setSuccess("");

    try {
      const [loanResult, scheduleResult] = await Promise.all([
        loanApi.get(id),
        paymentApi.schedule(id),
      ]);

      setLoan(loanResult as Loan);

      setPayments(
        Array.isArray(scheduleResult) ? (scheduleResult as Payment[]) : [],
      );
    } catch (err: any) {
      setLoan(null);
      setPayments([]);
      setError(err?.message || "Unable to load the loan.");
    } finally {
      setLoadingLoan(false);
    }
  }, []);

  const loadFromInput = async () => {
    const id = Number(loanIdInput.trim());

    if (!Number.isInteger(id) || id <= 0) {
      setError("Enter a valid loan ID.");
      return;
    }

    await loadLoan(id);
  };

  const paymentSummary = useMemo(() => {
    const paid = payments.filter(
      (payment) =>
        payment.paid ||
        ["PAID", "COMPLETED"].includes(payment.status?.toUpperCase() || ""),
    );

    const overdue = payments.filter((payment) => num(payment.daysLate) > 0);

    const scheduled = payments.reduce(
      (total, payment) => total + num(payment.amount),
      0,
    );

    const principal = payments.reduce(
      (total, payment) => total + num(payment.principalComponent),
      0,
    );

    const interest = payments.reduce(
      (total, payment) => total + num(payment.interestComponent),
      0,
    );

    return {
      paidCount: paid.length,
      overdueCount: overdue.length,
      scheduled,
      principal,
      interest,
    };
  }, [payments]);

  const recordPayment = async () => {
    if (!loan) {
      setError("Load a loan before recording a payment.");
      return;
    }

    const paymentAmount = Number(amount);

    if (!Number.isFinite(paymentAmount) || paymentAmount <= 0) {
      setError("Payment amount must be greater than zero.");
      return;
    }

    setConfirming(false);
    setSaving(true);
    setError("");
    setSuccess("");

    try {
      await paymentApi.record(
        loan.id,
        {
          amount: paymentAmount,
          paymentMethod,
          transactionId: transactionId.trim(),
          channel,
          notes: notes.trim(),
        },
        createIdempotencyKey(),
      );

      setSuccess(
        `Payment of ${money(paymentAmount, loan.currency || "RWF")} was recorded successfully.`,
      );

      setAmount("");
      setTransactionId("");
      setNotes("");

      await loadLoan(loan.id);
    } catch (err: any) {
      setError(err?.message || "Payment could not be recorded.");
    } finally {
      setSaving(false);
    }
  };

  const currency = loan?.currency || "RWF";
  const borrowerName = loan ? getBorrowerName(loan) : "";

  return (
    <main className="min-h-full bg-slate-50/60 pb-12">
      <div className="mx-auto max-w-[1680px] px-4 py-6 sm:px-6 lg:px-8">
        {/* Header */}
        <section className="mb-6 overflow-hidden rounded-3xl bg-slate-950 p-6 text-white shadow-[0_18px_50px_rgba(15,23,42,0.12)] sm:p-8">
          <div className="flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
            <div>
              <div className="mb-3 inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/5 px-3 py-1.5 text-[10px] font-black uppercase tracking-[0.18em] text-teal-200">
                <span className="h-1.5 w-1.5 rounded-full bg-teal-400" />
                Collections Operations
              </div>

              <h1 className="text-3xl font-black tracking-tight sm:text-4xl">
                Payments
              </h1>

              <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-300">
                Record borrower payments, review allocation across the loan
                schedule, and maintain a clear operational trail for every
                collection transaction.
              </p>
            </div>

            {loan ? (
              <div className="rounded-2xl border border-white/10 bg-white/5 px-4 py-3">
                <div className="text-[10px] font-black uppercase tracking-[0.14em] text-slate-400">
                  Selected loan
                </div>

                <div className="mt-1 font-black text-white">
                  {loan.referenceNumber || `Loan #${loan.id}`}
                </div>

                <div className="mt-0.5 text-xs text-slate-400">
                  {borrowerName}
                </div>
              </div>
            ) : null}
          </div>
        </section>

        {/* Alerts */}
        {error ? (
          <div
            role="alert"
            className="mb-5 flex items-start gap-3 rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800"
          >
            <IconAlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
            <span className="font-semibold">{error}</span>
          </div>
        ) : null}

        {success ? (
          <div
            role="status"
            className="mb-5 flex items-start gap-3 rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800"
          >
            <IconCheckCircle className="mt-0.5 h-4 w-4 shrink-0" />
            <span className="font-semibold">{success}</span>
          </div>
        ) : null}

        {/* Loan lookup */}
        <section className="mb-6 rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm sm:p-6">
          <div className="flex flex-col gap-4 lg:flex-row lg:items-end">
            <div className="flex-1">
              <label className="mb-2 block text-xs font-bold text-slate-700">
                Find loan
              </label>

              <div className="relative">
                <IconSearch className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />

                <input
                  type="number"
                  min="1"
                  value={loanIdInput}
                  onChange={(event) => setLoanIdInput(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter") {
                      void loadFromInput();
                    }
                  }}
                  placeholder="Enter loan ID"
                  className="h-11 w-full rounded-xl border border-slate-200 bg-slate-50 pl-10 pr-4 text-sm font-medium outline-none transition focus:border-teal-500 focus:bg-white focus:ring-4 focus:ring-teal-500/10"
                />
              </div>

              <p className="mt-2 text-[11px] text-slate-400">
                Load a loan to review its current balance and payment schedule.
              </p>
            </div>

            <button
              type="button"
              onClick={() => void loadFromInput()}
              disabled={loadingLoan}
              className="h-11 rounded-xl bg-slate-950 px-6 text-sm font-black text-white transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {loadingLoan ? "Loading loan…" : "Load loan"}
            </button>
          </div>
        </section>

        <section className="mb-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <MetricCard
            label="Portfolio collected"
            value={money(portfolioStats?.totalCollected, currency)}
            description="Cumulative collections, including legacy history"
            tone="teal"
            icon={<IconCoins className="h-5 w-5" />}
          />

          <MetricCard
            label="Legacy collections"
            value={money(portfolioStats?.historicalCollected, currency)}
            description="Historical collections brought forward"
            tone="blue"
            icon={<IconClock className="h-5 w-5" />}
          />

          <MetricCard
            label="Collected this month"
            value={money(portfolioStats?.collectedThisMonth, currency)}
            description="Posted payment transactions in the current month"
            tone="blue"
            icon={<IconCheckCircle className="h-5 w-5" />}
          />

          <MetricCard
            label="Processing fees"
            value={money(portfolioStats?.applicationFeesCollected, currency)}
            description="One-time fees collected at disbursement"
            tone="amber"
            icon={<IconSend className="h-5 w-5" />}
          />
        </section>

        {!loan ? (
          <section className="rounded-3xl border border-dashed border-slate-300 bg-white px-6 py-20 text-center shadow-sm">
            <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-2xl bg-teal-50 text-teal-700">
              <IconCoins className="h-7 w-7" />
            </div>

            <h2 className="mt-5 text-lg font-black text-slate-950">
              Select a loan to begin
            </h2>

            <p className="mx-auto mt-2 max-w-lg text-sm leading-6 text-slate-500">
              Enter a loan ID above to open its collections workspace. The
              payment schedule and financial position will be loaded from the
              server before any transaction can be posted.
            </p>
          </section>
        ) : (
          <>
            {/* Loan metrics */}
            <section className="mb-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
              <MetricCard
                label="Loan"
                value={loan.referenceNumber || `#${loan.id}`}
                description={borrowerName}
                tone="slate"
                icon={<IconCoins className="h-5 w-5" />}
              />

              <MetricCard
                label="Outstanding"
                value={money(loan.outstandingBalance, currency)}
                description="Current loan balance"
                tone="red"
                icon={<IconAlertTriangle className="h-5 w-5" />}
              />

              <MetricCard
                label="Next installment"
                value={money(loan.nextInstallmentAmount, currency)}
                description="Next scheduled amount"
                tone="amber"
                icon={<IconClock className="h-5 w-5" />}
              />

              <MetricCard
                label="Schedule paid"
                value={`${paymentSummary.paidCount}/${payments.length}`}
                description="Installments marked paid"
                tone="teal"
                icon={<IconCheckCircle className="h-5 w-5" />}
              />

              <MetricCard
                label="Overdue"
                value={paymentSummary.overdueCount.toLocaleString()}
                description="Schedule items with lateness"
                tone="red"
                icon={<IconAlertTriangle className="h-5 w-5" />}
              />
            </section>

            {/* Main workspace */}
            <div className="grid gap-6 xl:grid-cols-[390px_minmax(0,1fr)]">
              {/* Posting panel */}
              <section className="rounded-3xl border border-slate-200/80 bg-white shadow-sm">
                <div className="border-b border-slate-100 p-6">
                  <div className="flex items-center justify-between gap-3">
                    <div>
                      <div className="text-[10px] font-black uppercase tracking-[0.16em] text-teal-700">
                        Financial transaction
                      </div>

                      <h2 className="mt-1 text-xl font-black text-slate-950">
                        Record payment
                      </h2>
                    </div>

                    <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-teal-50 text-teal-700">
                      <IconSend className="h-5 w-5" />
                    </div>
                  </div>

                  <p className="mt-3 text-xs leading-5 text-slate-500">
                    Enter the amount received from the borrower. Allocation is
                    determined by the backend loan-payment rules.
                  </p>
                </div>

                <div className="space-y-5 p-6">
                  <div className="rounded-2xl border border-teal-100 bg-teal-50/60 p-4">
                    <div className="text-[10px] font-black uppercase tracking-[0.14em] text-teal-700">
                      Current outstanding
                    </div>

                    <div className="mt-1 text-2xl font-black tracking-tight text-slate-950">
                      {money(loan.outstandingBalance, currency)}
                    </div>
                  </div>

                  <Field
                    label="Payment amount"
                    value={amount}
                    onChange={setAmount}
                    type="number"
                    placeholder="0.00"
                    required
                  />

                  <div>
                    <label className="mb-2 block text-xs font-bold text-slate-700">
                      Payment method
                      <span className="ml-1 text-red-500">*</span>
                    </label>

                    <select
                      value={paymentMethod}
                      onChange={(event) => setPaymentMethod(event.target.value)}
                      className="h-11 w-full rounded-xl border border-slate-200 bg-white px-3.5 text-sm font-medium text-slate-900 outline-none transition focus:border-teal-500 focus:ring-4 focus:ring-teal-500/10"
                    >
                      <option value="BANK_TRANSFER">Bank transfer</option>
                      <option value="CASH">Cash</option>
                      <option value="MOBILE_MONEY">Mobile money</option>
                      <option value="CARD">Card</option>
                      <option value="CHEQUE">Cheque</option>
                    </select>
                  </div>

                  <Field
                    label="Transaction reference"
                    value={transactionId}
                    onChange={setTransactionId}
                    placeholder="External transaction reference"
                  />

                  <div>
                    <label className="mb-2 block text-xs font-bold text-slate-700">
                      Collection channel
                    </label>

                    <select
                      value={channel}
                      onChange={(event) => setChannel(event.target.value)}
                      className="h-11 w-full rounded-xl border border-slate-200 bg-white px-3.5 text-sm font-medium text-slate-900 outline-none transition focus:border-teal-500 focus:ring-4 focus:ring-teal-500/10"
                    >
                      <option value="MANUAL">Manual</option>
                      <option value="BANK">Bank</option>
                      <option value="MTN">MTN Mobile Money</option>
                      <option value="AIRTEL">Airtel Money</option>
                      <option value="CARD">Card</option>
                    </select>
                  </div>

                  <div>
                    <label className="mb-2 block text-xs font-bold text-slate-700">
                      Internal notes
                    </label>

                    <textarea
                      rows={4}
                      value={notes}
                      onChange={(event) => setNotes(event.target.value)}
                      placeholder="Optional collection note…"
                      className="w-full resize-none rounded-xl border border-slate-200 px-3.5 py-3 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-teal-500 focus:ring-4 focus:ring-teal-500/10"
                    />
                  </div>

                  <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
                    <div className="text-xs font-bold text-slate-700">
                      Posting protection
                    </div>

                    <p className="mt-1 text-[11px] leading-5 text-slate-500">
                      This transaction is submitted with a unique idempotency
                      key to reduce the risk of duplicate posting when a request
                      is retried.
                    </p>
                  </div>

                  <button
                    type="button"
                    onClick={() => setConfirming(true)}
                    disabled={saving || !amount}
                    className="w-full rounded-xl bg-[#0D6B3E] px-5 py-3.5 text-sm font-black text-white shadow-sm transition hover:bg-[#095832] hover:shadow-md disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    {saving ? "Posting payment…" : "Review & record payment"}
                  </button>
                </div>
              </section>

              {/* Schedule */}
              <section className="min-w-0 overflow-hidden rounded-3xl border border-slate-200/80 bg-white shadow-sm">
                <div className="border-b border-slate-100 p-6">
                  <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
                    <div>
                      <div className="text-[10px] font-black uppercase tracking-[0.16em] text-teal-700">
                        Loan ledger
                      </div>

                      <h2 className="mt-1 text-xl font-black text-slate-950">
                        Payment schedule
                      </h2>

                      <p className="mt-1 text-xs text-slate-400">
                        Scheduled obligations and current allocation position.
                      </p>
                    </div>

                    <StatusBadge status={loan.status} />
                  </div>
                </div>

                <div className="grid gap-px border-b border-slate-100 bg-slate-100 sm:grid-cols-3">
                  <div className="bg-white p-4">
                    <div className="text-[10px] font-black uppercase tracking-[0.12em] text-slate-400">
                      Scheduled
                    </div>
                    <div className="mt-1 text-sm font-black text-slate-900">
                      {money(paymentSummary.scheduled, currency)}
                    </div>
                  </div>

                  <div className="bg-white p-4">
                    <div className="text-[10px] font-black uppercase tracking-[0.12em] text-slate-400">
                      Principal
                    </div>
                    <div className="mt-1 text-sm font-black text-slate-900">
                      {money(paymentSummary.principal, currency)}
                    </div>
                  </div>

                  <div className="bg-white p-4">
                    <div className="text-[10px] font-black uppercase tracking-[0.12em] text-slate-400">
                      Interest
                    </div>
                    <div className="mt-1 text-sm font-black text-slate-900">
                      {money(paymentSummary.interest, currency)}
                    </div>
                  </div>
                </div>

                <div className="overflow-x-auto">
                  <table className="min-w-[1120px] w-full text-sm">
                    <thead className="border-b border-slate-100 bg-slate-50/80 text-left">
                      <tr>
                        {[
                          "Installment",
                          "Due date",
                          "Scheduled",
                          "Principal",
                          "Interest",
                          "Penalty",
                          "Balance",
                          "Status",
                        ].map((label) => (
                          <th
                            key={label}
                            className="px-5 py-3 text-[10px] font-black uppercase tracking-[0.13em] text-slate-400"
                          >
                            {label}
                          </th>
                        ))}
                      </tr>
                    </thead>

                    <tbody className="divide-y divide-slate-100">
                      {payments.length === 0 ? (
                        <tr>
                          <td colSpan={8}>
                            <EmptySchedule />
                          </td>
                        </tr>
                      ) : (
                        payments.map((payment, index) => (
                          <tr
                            key={payment.id ?? index}
                            className="transition hover:bg-slate-50/70"
                          >
                            <td className="px-5 py-4 align-top">
                              <div className="font-black text-slate-900">
                                #{payment.installmentNumber ?? index + 1}
                              </div>

                              {payment.paymentReference ? (
                                <div className="mt-1 max-w-[150px] truncate font-mono text-[10px] text-slate-400">
                                  {payment.paymentReference}
                                </div>
                              ) : null}
                            </td>

                            <td className="px-5 py-4 align-top">
                              <div className="font-semibold text-slate-700">
                                {formatDate(payment.dueDate)}
                              </div>

                              {num(payment.daysLate) > 0 ? (
                                <div className="mt-1 flex items-center gap-1 text-[10px] font-bold text-red-600">
                                  <IconAlertTriangle className="h-3 w-3" />
                                  {payment.daysLate} days late
                                </div>
                              ) : null}
                            </td>

                            <td className="px-5 py-4 align-top font-black text-slate-950">
                              {money(payment.amount, currency)}
                            </td>

                            <td className="px-5 py-4 align-top font-semibold text-slate-700">
                              {money(payment.principalComponent, currency)}
                            </td>

                            <td className="px-5 py-4 align-top font-semibold text-slate-700">
                              {money(payment.interestComponent, currency)}
                            </td>

                            <td className="px-5 py-4 align-top font-semibold text-slate-700">
                              {money(payment.penalty, currency)}
                            </td>

                            <td className="px-5 py-4 align-top font-black text-slate-950">
                              {money(payment.outstandingAfter, currency)}
                            </td>

                            <td className="px-5 py-4 align-top">
                              <StatusBadge
                                status={
                                  payment.status ||
                                  (payment.paid ? "PAID" : "PENDING")
                                }
                              />

                              {payment.paidDate ? (
                                <div className="mt-1 text-[10px] text-slate-400">
                                  Paid {formatDate(payment.paidDate)}
                                </div>
                              ) : null}
                            </td>
                          </tr>
                        ))
                      )}
                    </tbody>
                  </table>
                </div>
              </section>
            </div>

            {/* Audit note */}
            <section className="mt-6 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
              <div className="flex items-start gap-3">
                <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-slate-100 text-slate-600">
                  <IconCheckCircle className="h-4 w-4" />
                </div>

                <div>
                  <h3 className="text-sm font-black text-slate-900">
                    Collections control
                  </h3>

                  <p className="mt-1 max-w-4xl text-xs leading-5 text-slate-500">
                    Payment allocation and accounting treatment are controlled
                    by the backend. The interface does not calculate or override
                    principal, interest, penalty, or outstanding balances
                    locally.
                  </p>
                </div>
              </div>
            </section>
          </>
        )}

        {/* Confirmation dialog */}
        {confirming && loan ? (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/50 p-4 backdrop-blur-sm">
            <div className="w-full max-w-md rounded-3xl border border-slate-200 bg-white p-6 shadow-2xl">
              <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-amber-50 text-amber-600">
                <IconAlertTriangle className="h-6 w-6" />
              </div>

              <h2 className="mt-5 text-xl font-black text-slate-950">
                Confirm payment
              </h2>

              <p className="mt-2 text-sm leading-6 text-slate-500">
                You are about to record a payment against{" "}
                <span className="font-bold text-slate-900">
                  {loan.referenceNumber || `Loan #${loan.id}`}
                </span>
                .
              </p>

              <div className="mt-5 rounded-2xl bg-slate-50 p-4">
                <div className="text-[10px] font-black uppercase tracking-[0.14em] text-slate-400">
                  Payment amount
                </div>

                <div className="mt-1 text-2xl font-black text-slate-950">
                  {money(Number(amount), currency)}
                </div>

                <div className="mt-3 grid grid-cols-2 gap-3 text-xs">
                  <div>
                    <div className="text-slate-400">Method</div>
                    <div className="mt-1 font-bold text-slate-700">
                      {humanize(paymentMethod)}
                    </div>
                  </div>

                  <div>
                    <div className="text-slate-400">Channel</div>
                    <div className="mt-1 font-bold text-slate-700">
                      {humanize(channel)}
                    </div>
                  </div>
                </div>
              </div>

              <div className="mt-5 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
                <button
                  type="button"
                  onClick={() => setConfirming(false)}
                  className="rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-bold text-slate-700 transition hover:bg-slate-50"
                >
                  Cancel
                </button>

                <button
                  type="button"
                  onClick={() => void recordPayment()}
                  className="rounded-xl bg-[#0D6B3E] px-5 py-2.5 text-sm font-black text-white transition hover:bg-[#095832]"
                >
                  Confirm & post payment
                </button>
              </div>
            </div>
          </div>
        ) : null}
      </div>
    </main>
  );
}
