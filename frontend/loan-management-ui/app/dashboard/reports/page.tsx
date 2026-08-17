"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";

import API from "../../../services/api";
import { getDashboardStats } from "../../../services/dashboardService";
import { getOverduePayments } from "../../../services/paymentService";
import { getLoans } from "../../../services/loanService";

import { PageSpinner } from "../../../components/ui/Skeleton";

/* ========================================================================== */
/* TYPES                                                                      */
/* ========================================================================== */

type Numeric = number | string | null | undefined;

interface AccountingAccountRow {
  code?: string;
  name?: string;
  type?: string;
  balance?: Numeric;
  debit?: Numeric;
  credit?: Numeric;
  amount?: Numeric;
}

interface TrialBalanceReport {
  accounts?: AccountingAccountRow[];
  totalDebit?: Numeric;
  totalCredit?: Numeric;
  balanced?: boolean;
}

interface BalanceSheetReport {
  asOf?: string;
  assets?: AccountingAccountRow[];
  liabilities?: AccountingAccountRow[];
  equity?: AccountingAccountRow[];
  currentPeriodNetIncome?: Numeric;
  totalAssets?: Numeric;
  totalLiabilities?: Numeric;
  totalEquity?: Numeric;
  balanced?: boolean;
}

interface ProfitAndLossReport {
  from?: string;
  to?: string;
  income?: AccountingAccountRow[];
  expense?: AccountingAccountRow[];
  totalIncome?: Numeric;
  totalExpense?: Numeric;
  totalExpenses?: Numeric;
  netIncome?: Numeric;
}

interface CashFlowReport {
  from?: string;
  to?: string;
  cashUsedForLending?: Numeric;
  cashFromCollections?: Numeric;
  cashFromFees?: Numeric;
  otherCashMovement?: Numeric;
  netChangeInCash?: Numeric;
}

interface MonthlyAccountingReport {
  month: string;
  label: string;
  from: string;
  to: string;
  revenue: number;
  expenses: number;
  profit: number;
}

interface DashboardStatsLike {
  totalDisbursed?: Numeric;
  totalCollected?: Numeric;
  activeLoans?: Numeric;
  pendingLoans?: Numeric;
  overdueLoans?: Numeric;
  completedLoans?: Numeric;
  totalBorrowers?: Numeric;
}

interface LoanLike {
  id?: number | string;
  referenceNumber?: string;
  status?: string;

  borrower?: {
    firstName?: string;
    lastName?: string;
    nationalId?: string;
  };

  amount?: Numeric;
  outstandingBalance?: Numeric;
  creditQuality?: string;
  daysOverdue?: Numeric;
}

interface PaymentLike {
  penalty?: Numeric;
}

/* ========================================================================== */
/* HELPERS                                                                    */
/* ========================================================================== */

const numberValue = (value: Numeric): number => {
  if (value === null || value === undefined || value === "") {
    return 0;
  }

  const parsed = Number(value);

  return Number.isFinite(parsed) ? parsed : 0;
};

const fmt = (value: Numeric): string =>
  new Intl.NumberFormat("en-RW", {
    style: "currency",
    currency: "RWF",
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  }).format(numberValue(value));

const fmtPrecise = (value: Numeric): string =>
  new Intl.NumberFormat("en-RW", {
    style: "currency",
    currency: "RWF",
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(numberValue(value));

const fmtNumber = (value: Numeric): string =>
  new Intl.NumberFormat("en-RW").format(numberValue(value));

const fmtDate = (value?: string | null): string => {
  if (!value) {
    return "—";
  }

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat("en-RW", {
    year: "numeric",
    month: "short",
    day: "numeric",
  }).format(date);
};

const toDateString = (date: Date): string => {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");

  return `${year}-${month}-${day}`;
};

const monthStart = (date: Date): Date =>
  new Date(date.getFullYear(), date.getMonth(), 1);

const monthEnd = (date: Date): Date =>
  new Date(date.getFullYear(), date.getMonth() + 1, 0);

const previousMonth = (date: Date, monthsBack: number): Date =>
  new Date(date.getFullYear(), date.getMonth() - monthsBack, 1);

/**
 * Safely unwraps common Axios/API response structures:
 *
 * response
 * response.data
 * response.data.data
 * response.data.content
 */
const unwrap = <T,>(value: unknown): T => {
  if (value === null || value === undefined) {
    return value as T;
  }

  if (typeof value !== "object") {
    return value as T;
  }

  const root = value as Record<string, unknown>;

  if (!("data" in root)) {
    return value as T;
  }

  const data = root.data;

  if (data === null || data === undefined) {
    return data as T;
  }

  if (typeof data !== "object") {
    return data as T;
  }

  const nested = data as Record<string, unknown>;

  if ("data" in nested) {
    return nested.data as T;
  }

  if ("content" in nested) {
    return nested.content as T;
  }

  return data as T;
};

const normalizeArray = <T,>(value: unknown): T[] => {
  if (Array.isArray(value)) {
    return value as T[];
  }

  if (!value || typeof value !== "object") {
    return [];
  }

  const object = value as Record<string, unknown>;

  if (Array.isArray(object.content)) {
    return object.content as T[];
  }

  if (Array.isArray(object.items)) {
    return object.items as T[];
  }

  if (Array.isArray(object.data)) {
    return object.data as T[];
  }

  return [];
};

/**
 * Converts a frontend report identifier to a safe filename.
 */
const filenamePart = (value: string): string =>
  value
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");

/**
 * Downloads a binary response returned by Axios.
 */
const downloadBlob = async (url: string, filename: string): Promise<void> => {
  const response = await API.get(url, {
    responseType: "blob",
  });

  const blob =
    response.data instanceof Blob ? response.data : new Blob([response.data]);

  const objectUrl = window.URL.createObjectURL(blob);

  const anchor = document.createElement("a");

  anchor.href = objectUrl;
  anchor.download = filename;
  anchor.style.display = "none";

  document.body.appendChild(anchor);

  anchor.click();

  anchor.remove();

  window.setTimeout(() => {
    window.URL.revokeObjectURL(objectUrl);
  }, 60_000);
};

/* ========================================================================== */
/* EXPORT BUTTONS                                                             */
/* ========================================================================== */

function ExportButtons({
  endpoint,
  label,
  accounting = false,
}: {
  endpoint: string;
  label: string;
  accounting?: boolean;
}) {
  const [loading, setLoading] = useState<"csv" | "excel" | null>(null);

  const exportReport = async (format: "csv" | "excel"): Promise<void> => {
    if (loading !== null) {
      return;
    }

    try {
      setLoading(format);

      const url = accounting
        ? format === "excel"
          ? `/accounting/${endpoint}/export/excel`
          : `/accounting/${endpoint}/export`
        : format === "excel"
          ? `/reports/export/${endpoint}/excel`
          : `/reports/export/${endpoint}`;

      const extension = format === "excel" ? "xlsx" : "csv";

      const datePart = new Date().toISOString().slice(0, 10);

      await downloadBlob(
        url,
        `${filenamePart(label)}-${datePart}.${extension}`,
      );
    } catch (error) {
      console.error(`Failed to export ${label}`, error);

      const message =
        error instanceof Error ? error.message : `Unable to export ${label}.`;

      window.alert(message);
    } finally {
      setLoading(null);
    }
  };

  return (
    <div className="flex flex-wrap gap-2">
      <button
        type="button"
        disabled={loading !== null}
        onClick={() => void exportReport("csv")}
        className="rounded-lg border border-gray-200 bg-white px-3 py-2 text-xs font-semibold text-gray-700 transition hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
      >
        {loading === "csv" ? "Preparing..." : "CSV"}
      </button>

      <button
        type="button"
        disabled={loading !== null}
        onClick={() => void exportReport("excel")}
        className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-xs font-semibold text-emerald-700 transition hover:bg-emerald-100 disabled:cursor-not-allowed disabled:opacity-50"
      >
        {loading === "excel" ? "Preparing..." : "Excel"}
      </button>
    </div>
  );
}

/* ========================================================================== */
/* REPORT CARDS                                                               */
/* ========================================================================== */

function ReportCard({
  endpoint,
  title,
  description,
  icon,
}: {
  endpoint: string;
  title: string;
  description: string;
  icon: string;
}) {
  return (
    <div className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm transition-all duration-200 hover:-translate-y-0.5 hover:shadow-md">
      <div className="flex gap-3">
        <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-gray-50 text-xl">
          {icon}
        </div>

        <div className="min-w-0">
          <h3 className="text-sm font-semibold text-gray-900">{title}</h3>

          <p className="mt-1 text-xs leading-5 text-gray-500">{description}</p>
        </div>
      </div>

      <div className="mt-5 flex items-center justify-between border-t border-gray-100 pt-4">
        <span className="text-[10px] font-semibold uppercase tracking-wider text-gray-400">
          Export
        </span>

        <ExportButtons endpoint={endpoint} label={title} />
      </div>
    </div>
  );
}

function AccountingReportCard({
  endpoint,
  title,
  description,
  icon,
}: {
  endpoint: string;
  title: string;
  description: string;
  icon: string;
}) {
  return (
    <div className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm transition-all duration-200 hover:-translate-y-0.5 hover:shadow-md">
      <div className="flex gap-3">
        <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-indigo-50 text-xl">
          {icon}
        </div>

        <div className="min-w-0">
          <h3 className="text-sm font-semibold text-gray-900">{title}</h3>

          <p className="mt-1 text-xs leading-5 text-gray-500">{description}</p>
        </div>
      </div>

      <div className="mt-5 flex items-center justify-between border-t border-gray-100 pt-4">
        <span className="text-[10px] font-semibold uppercase tracking-wider text-gray-400">
          Financial
        </span>

        <ExportButtons endpoint={endpoint} label={title} accounting />
      </div>
    </div>
  );
}

/* ========================================================================== */
/* KPI                                                                         */
/* ========================================================================== */

function Kpi({
  label,
  value,
  description,
}: {
  label: string;
  value: string;
  description?: string;
}) {
  return (
    <div className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm transition-all duration-200 hover:-translate-y-0.5 hover:shadow-md">
      <p className="text-[10px] font-semibold uppercase tracking-wider text-gray-400">
        {label}
      </p>

      <p className="mt-2 text-2xl font-bold tracking-tight text-gray-900">
        {value}
      </p>

      {description ? (
        <p className="mt-1 text-xs text-gray-400">{description}</p>
      ) : null}
    </div>
  );
}

/* ========================================================================== */
/* ACCOUNTING STATUS                                                          */
/* ========================================================================== */

function AccountingStatus({
  label,
  balanced,
}: {
  label: string;
  balanced?: boolean;
}) {
  if (balanced === undefined) {
    return (
      <span className="rounded-full bg-gray-100 px-3 py-1 text-[10px] font-semibold text-gray-500">
        {label}: unavailable
      </span>
    );
  }

  return (
    <span
      className={
        balanced
          ? "rounded-full bg-emerald-50 px-3 py-1 text-[10px] font-semibold text-emerald-700"
          : "rounded-full bg-red-50 px-3 py-1 text-[10px] font-semibold text-red-700"
      }
    >
      {label}: {balanced ? "balanced" : "attention required"}
    </span>
  );
}

/* ========================================================================== */
/* ACCOUNT TABLE                                                              */
/* ========================================================================== */

function AccountTable({
  title,
  rows,
}: {
  title: string;
  rows?: AccountingAccountRow[];
}) {
  return (
    <div className="overflow-hidden rounded-2xl border border-gray-200">
      <div className="border-b border-gray-100 bg-gray-50 px-4 py-3">
        <h3 className="text-sm font-semibold text-gray-800">{title}</h3>
      </div>

      {!rows || rows.length === 0 ? (
        <div className="p-5 text-sm text-gray-400">No accounts reported.</div>
      ) : (
        <div className="overflow-x-auto">
          <table className="min-w-full text-sm">
            <tbody className="divide-y divide-gray-100">
              {rows.map((row, index) => {
                const value =
                  row.balance ?? row.amount ?? row.credit ?? row.debit ?? 0;

                return (
                  <tr key={`${row.code ?? row.name ?? "account"}-${index}`}>
                    <td className="whitespace-nowrap px-4 py-3 text-gray-400">
                      {row.code ?? "—"}
                    </td>

                    <td className="px-4 py-3 font-medium text-gray-700">
                      {row.name ?? "Unnamed Account"}
                    </td>

                    <td className="whitespace-nowrap px-4 py-3 text-right font-semibold text-gray-900">
                      {fmtPrecise(value)}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

/* ========================================================================== */
/* PAGE                                                                       */
/* ========================================================================== */

export default function ReportsPage() {
  const [stats, setStats] = useState<DashboardStatsLike | null>(null);

  const [loans, setLoans] = useState<LoanLike[]>([]);

  const [overdue, setOverdue] = useState<PaymentLike[]>([]);

  const [trialBalance, setTrialBalance] = useState<TrialBalanceReport | null>(
    null,
  );

  const [balanceSheet, setBalanceSheet] = useState<BalanceSheetReport | null>(
    null,
  );

  const [profitAndLoss, setProfitAndLoss] =
    useState<ProfitAndLossReport | null>(null);

  const [cashFlow, setCashFlow] = useState<CashFlowReport | null>(null);

  const [monthlyAccounting, setMonthlyAccounting] = useState<
    MonthlyAccountingReport[]
  >([]);

  const [loading, setLoading] = useState(true);

  const [accountingError, setAccountingError] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;

    const loadOperationalReports = async (): Promise<void> => {
      try {
        const [dashboardStatsResult, overduePaymentsResult, loanListResult] =
          await Promise.allSettled([
            getDashboardStats(),
            getOverduePayments(),
            getLoans(),
          ]);

        if (!mounted) {
          return;
        }

        if (dashboardStatsResult.status === "fulfilled") {
          const dashboardData = unwrap<DashboardStatsLike>(
            dashboardStatsResult.value,
          );

          setStats(dashboardData ?? null);
        } else {
          console.error(
            "Dashboard statistics failed",
            dashboardStatsResult.reason,
          );

          setStats(null);
        }

        if (overduePaymentsResult.status === "fulfilled") {
          const overdueData = unwrap<unknown>(overduePaymentsResult.value);

          setOverdue(normalizeArray<PaymentLike>(overdueData));
        } else {
          console.error(
            "Overdue payments report failed",
            overduePaymentsResult.reason,
          );

          setOverdue([]);
        }

        if (loanListResult.status === "fulfilled") {
          const loanData = unwrap<unknown>(loanListResult.value);

          setLoans(normalizeArray<LoanLike>(loanData));
        } else {
          console.error("Loan portfolio report failed", loanListResult.reason);

          setLoans([]);
        }
      } catch (error) {
        console.error("Operational reports failed", error);

        if (mounted) {
          setStats(null);
          setOverdue([]);
          setLoans([]);
        }
      }
    };

    const loadAccountingReports = async (): Promise<void> => {
      try {
        const results = await Promise.allSettled([
          API.get("/accounting/trial-balance"),
          API.get("/accounting/balance-sheet"),
          API.get("/accounting/profit-and-loss"),
          API.get("/accounting/cash-flow"),
        ]);

        if (!mounted) {
          return;
        }

        const trialBalanceResult = results[0];

        const balanceSheetResult = results[1];

        const profitAndLossResult = results[2];

        const cashFlowResult = results[3];

        if (trialBalanceResult.status === "fulfilled") {
          setTrialBalance(unwrap<TrialBalanceReport>(trialBalanceResult.value));
        } else {
          console.error("Trial balance failed", trialBalanceResult.reason);

          setTrialBalance(null);
        }

        if (balanceSheetResult.status === "fulfilled") {
          setBalanceSheet(unwrap<BalanceSheetReport>(balanceSheetResult.value));
        } else {
          console.error("Balance sheet failed", balanceSheetResult.reason);

          setBalanceSheet(null);
        }

        if (profitAndLossResult.status === "fulfilled") {
          setProfitAndLoss(
            unwrap<ProfitAndLossReport>(profitAndLossResult.value),
          );
        } else {
          console.error("Profit and loss failed", profitAndLossResult.reason);

          setProfitAndLoss(null);
        }

        if (cashFlowResult.status === "fulfilled") {
          setCashFlow(unwrap<CashFlowReport>(cashFlowResult.value));
        } else {
          console.error("Cash flow failed", cashFlowResult.reason);

          setCashFlow(null);
        }

        const accountingSucceeded = results.some(
          (result) => result.status === "fulfilled",
        );

        if (accountingSucceeded) {
          setAccountingError(null);
        } else {
          setAccountingError(
            "Accounting reports could not be loaded. Operational reports remain available.",
          );
        }

        /*
         * Monthly accounting trend.
         *
         * This is deliberately loaded independently for each month.
         * One failed month must not prevent the other months from
         * appearing.
         */
        const today = new Date();

        const periods = Array.from({ length: 6 }, (_, index) => {
          const date = previousMonth(today, 5 - index);

          return {
            date,
            from: toDateString(monthStart(date)),
            to: toDateString(monthEnd(date)),
          };
        });

        const monthlyResults = await Promise.allSettled(
          periods.map(async (period) => {
            const response = await API.get("/accounting/profit-and-loss", {
              params: {
                from: period.from,
                to: period.to,
              },
            });

            const data = unwrap<ProfitAndLossReport>(response);

            return {
              month: period.from.slice(0, 7),

              label: new Intl.DateTimeFormat("en-RW", {
                month: "short",
                year: "numeric",
              }).format(period.date),

              from: period.from,
              to: period.to,

              revenue: numberValue(data?.totalIncome),

              expenses: numberValue(data?.totalExpense ?? data?.totalExpenses),

              profit: numberValue(data?.netIncome),
            } satisfies MonthlyAccountingReport;
          }),
        );

        if (!mounted) {
          return;
        }

        const monthly = monthlyResults.map((result, index) => {
          const period = periods[index];

          if (result.status === "fulfilled") {
            return result.value;
          }

          console.error(
            `Monthly accounting report failed for ${period.from}`,
            result.reason,
          );

          return {
            month: period.from.slice(0, 7),

            label: new Intl.DateTimeFormat("en-RW", {
              month: "short",
              year: "numeric",
            }).format(period.date),

            from: period.from,
            to: period.to,

            revenue: 0,
            expenses: 0,
            profit: 0,
          };
        });

        setMonthlyAccounting(monthly);
      } catch (error) {
        console.error("Accounting reports failed", error);

        if (mounted) {
          setAccountingError(
            "Accounting reports could not be loaded. Operational reports remain available.",
          );

          setTrialBalance(null);
          setBalanceSheet(null);
          setProfitAndLoss(null);
          setCashFlow(null);
          setMonthlyAccounting([]);
        }
      }
    };

    const loadReports = async (): Promise<void> => {
      setLoading(true);

      try {
        /*
         * Operational and accounting reporting are intentionally
         * independent. A failure in AccountingService must not
         * prevent the dashboard/operational report page from loading.
         */
        await Promise.all([loadOperationalReports(), loadAccountingReports()]);
      } finally {
        if (mounted) {
          setLoading(false);
        }
      }
    };

    void loadReports();

    return () => {
      mounted = false;
    };
  }, []);

  /* ======================================================================== */
  /* DERIVED VALUES                                                           */
  /* ======================================================================== */

  const collectionRate = useMemo(() => {
    const disbursed = numberValue(stats?.totalDisbursed);

    const collected = numberValue(stats?.totalCollected);

    if (disbursed <= 0) {
      return "0.0";
    }

    const rate = (collected / disbursed) * 100;

    return rate.toFixed(1);
  }, [stats]);

  const penalties = useMemo(() => {
    return overdue.reduce(
      (sum, payment) => sum + numberValue(payment.penalty),
      0,
    );
  }, [overdue]);

  const totalIncome = numberValue(profitAndLoss?.totalIncome);

  const totalExpenses = numberValue(
    profitAndLoss?.totalExpense ?? profitAndLoss?.totalExpenses,
  );

  const netIncome = numberValue(profitAndLoss?.netIncome);

  const totalAssets = numberValue(balanceSheet?.totalAssets);

  const totalLiabilities = numberValue(balanceSheet?.totalLiabilities);

  const totalEquity = numberValue(balanceSheet?.totalEquity);

  const netCashChange = numberValue(cashFlow?.netChangeInCash);

  const currentPeriodNetIncome = numberValue(
    balanceSheet?.currentPeriodNetIncome,
  );

  const totalDebit = numberValue(trialBalance?.totalDebit);

  const totalCredit = numberValue(trialBalance?.totalCredit);

  const rejectedCount = loans.filter(
    (loan) => String(loan.status ?? "").toUpperCase() === "REJECTED",
  ).length;

  const portfolioCount =
    numberValue(stats?.activeLoans) +
    numberValue(stats?.pendingLoans) +
    numberValue(stats?.completedLoans) +
    rejectedCount;

  const monthlyMax = Math.max(
    1,
    ...monthlyAccounting.flatMap((row) => [
      Math.abs(row.revenue),
      Math.abs(row.expenses),
      Math.abs(row.profit),
    ]),
  );

  const hasAccountingReports = Boolean(
    trialBalance || balanceSheet || profitAndLoss || cashFlow,
  );

  /* ======================================================================== */
  /* LOADING                                                                   */
  /* ======================================================================== */

  if (loading) {
    return <PageSpinner />;
  }

  /* ======================================================================== */
  /* RENDER                                                                    */
  /* ======================================================================== */

  return (
    <div className="min-h-full space-y-8 pb-12">
      {/* ==================================================================== */}
      {/* HEADER                                                               */}
      {/* ==================================================================== */}

      <section className="relative overflow-hidden rounded-3xl border border-slate-200/80 bg-gradient-to-br from-slate-950 via-slate-900 to-indigo-950 p-6 shadow-[0_18px_50px_rgba(15,23,42,0.12)] sm:p-8 lg:flex lg:items-end lg:justify-between">
        <div>
          <div className="mb-2 flex items-center gap-2">
            <span className="inline-flex h-7 w-7 items-center justify-center rounded-lg bg-indigo-50 text-sm">
              ◫
            </span>

            <span className="text-xs font-bold uppercase tracking-[0.16em] text-indigo-300">
              Business Intelligence
            </span>
          </div>

          <h1 className="text-3xl font-black tracking-tight text-white sm:text-4xl">
            Reports &amp; Analytics
          </h1>

          <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-300">
            Portfolio, operational, regulatory and accounting intelligence from
            one reporting workspace.
          </p>
        </div>

        <Link
          href="/dashboard/reports/regulatory"
          className="inline-flex items-center rounded-xl border border-white/15 bg-white/10 px-4 py-2.5 text-xs font-bold text-white shadow-sm transition hover:bg-white/15"
        >
          Regulatory Reports →
        </Link>
      </section>

      {/* ==================================================================== */}
      {/* ACCOUNTING WARNING                                                   */}
      {/* ==================================================================== */}

      {accountingError ? (
        <section className="rounded-2xl border border-amber-200 bg-amber-50 px-5 py-4">
          <p className="text-sm font-semibold text-amber-900">
            Accounting reporting unavailable
          </p>

          <p className="mt-1 text-xs text-amber-700">{accountingError}</p>
        </section>
      ) : null}

      {/* ==================================================================== */}
      {/* PORTFOLIO PERFORMANCE                                                */}
      {/* ==================================================================== */}

      <section>
        <div className="mb-3">
          <h2 className="text-sm font-semibold text-gray-900">
            Portfolio Performance
          </h2>

          <p className="mt-0.5 text-xs text-gray-400">
            Operational portfolio indicators.
          </p>
        </div>

        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <Kpi label="Total Disbursed" value={fmt(stats?.totalDisbursed)} />

          <Kpi label="Total Collected" value={fmt(stats?.totalCollected)} />

          <Kpi label="Collection Rate" value={`${collectionRate}%`} />

          <Kpi
            label="Overdue Penalties"
            value={fmt(penalties)}
            description="Operational overdue exposure."
          />
        </div>
      </section>

      {/* ==================================================================== */}
      {/* PORTFOLIO HEALTH                                                     */}
      {/* ==================================================================== */}

      <section>
        <div className="mb-3">
          <h2 className="text-sm font-semibold text-gray-900">
            Portfolio Health
          </h2>
        </div>

        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <Kpi label="Borrowers" value={fmtNumber(stats?.totalBorrowers)} />

          <Kpi label="Active Loans" value={fmtNumber(stats?.activeLoans)} />

          <Kpi label="Overdue Loans" value={fmtNumber(stats?.overdueLoans)} />

          <Kpi label="Closed Loans" value={fmtNumber(stats?.completedLoans)} />
        </div>
      </section>

      {/* ==================================================================== */}
      {/* FINANCIAL PERFORMANCE                                                */}
      {/* ==================================================================== */}

      <section>
        <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <h2 className="text-sm font-semibold text-gray-900">
              Financial Performance
            </h2>
            <p className="mt-1 text-xs text-gray-500">
              Plain-language view of the business financial position. Figures
              come directly from the accounting records.
            </p>
          </div>

          <div className="flex flex-wrap gap-2">
            <AccountingStatus label="Books" balanced={trialBalance?.balanced} />
            <AccountingStatus
              label="Financial Position"
              balanced={balanceSheet?.balanced}
            />
          </div>
        </div>

        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          <Kpi
            label="Money Earned"
            value={fmt(totalIncome)}
            description="Income recognized during the reporting period."
          />

          <Kpi
            label="Money Spent"
            value={fmt(totalExpenses)}
            description="Expenses recognized during the reporting period."
          />

          <Kpi
            label="Profit"
            value={fmt(netIncome)}
            description={
              netIncome >= 0
                ? "Business earned more than it spent."
                : "Business spent more than it earned."
            }
          />

          <Kpi
            label="What the Business Owns"
            value={fmt(totalAssets)}
            description="Assets recorded in the accounting system."
          />

          <Kpi
            label="What the Business Owes"
            value={fmt(totalLiabilities)}
            description="Liabilities recorded in the accounting system."
          />

          <Kpi
            label="Business Value"
            value={fmt(totalEquity)}
            description="Assets minus what the business owes."
          />
        </div>
      </section>

      {/* ==================================================================== */}
      {/* ACCOUNTING CONTROL SUMMARY                                           */}
      {/* ==================================================================== */}

      {hasAccountingReports ? (
        <section className="rounded-2xl border border-slate-200/80 bg-white shadow-sm overflow-hidden">
          <div className="border-b border-slate-100 px-6 py-5">
            <div className="flex flex-col gap-1">
              <h2 className="text-sm font-semibold text-gray-900">
                Financial Health Check
              </h2>
              <p className="text-xs text-gray-500">
                These checks help management verify that the books are complete
                and internally consistent.
              </p>
            </div>
          </div>

          <div className="grid gap-4 p-6 sm:grid-cols-2 xl:grid-cols-4">
            <Kpi
              label="Total Recorded Debits"
              value={fmtPrecise(totalDebit)}
              description="Internal accounting control total."
            />

            <Kpi
              label="Total Recorded Credits"
              value={fmtPrecise(totalCredit)}
              description="Should match total debits."
            />

            <Kpi
              label="Current Period Profit"
              value={fmtPrecise(currentPeriodNetIncome)}
              description="Profit carried into the current financial position."
            />

            <Kpi
              label="Cash Movement"
              value={fmtPrecise(netCashChange)}
              description={
                netCashChange >= 0
                  ? "Net cash increased."
                  : "Net cash decreased."
              }
            />
          </div>

          <div className="border-t border-slate-100 bg-slate-50/50 px-6 py-5">
            <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <p className="text-sm font-semibold text-slate-900">
                  Accounting control
                </p>
                <p className="text-xs text-slate-500">
                  A balanced trial balance means the recorded financial entries
                  are mathematically in balance.
                </p>
              </div>

              <span
                className={`inline-flex w-fit rounded-full px-3 py-1.5 text-xs font-bold ${
                  trialBalance?.balanced
                    ? "bg-emerald-50 text-emerald-700"
                    : "bg-red-50 text-red-700"
                }`}
              >
                {trialBalance?.balanced
                  ? "✓ Records are balanced"
                  : "⚠ Review required"}
              </span>
            </div>
          </div>

          {trialBalance ? (
            <details className="border-t border-slate-100 px-6 py-5">
              <summary className="cursor-pointer text-sm font-semibold text-slate-800">
                Show detailed accounting accounts
              </summary>
              <p className="mt-2 text-xs text-slate-500">
                This technical detail is available for authorized finance users
                without making it the main business view.
              </p>

              <div className="mt-4 grid gap-6 xl:grid-cols-2">
                <AccountTable
                  title="Trial Balance Accounts"
                  rows={trialBalance.accounts}
                />

                <div className="grid gap-4 sm:grid-cols-2">
                  <Kpi
                    label="Debit"
                    value={fmtPrecise(trialBalance.totalDebit)}
                  />
                  <Kpi
                    label="Credit"
                    value={fmtPrecise(trialBalance.totalCredit)}
                  />
                </div>
              </div>
            </details>
          ) : null}

          {balanceSheet ? (
            <details className="border-t border-slate-100 px-6 py-5">
              <summary className="cursor-pointer text-sm font-semibold text-slate-800">
                Show detailed business position
              </summary>

              <div className="mt-4 grid gap-6 xl:grid-cols-3">
                <AccountTable
                  title="What the Business Owns"
                  rows={balanceSheet.assets}
                />
                <AccountTable
                  title="What the Business Owes"
                  rows={balanceSheet.liabilities}
                />
                <AccountTable
                  title="Business Value"
                  rows={balanceSheet.equity}
                />
              </div>
            </details>
          ) : null}

          {profitAndLoss ? (
            <details className="border-t border-slate-100 px-6 py-5">
              <summary className="cursor-pointer text-sm font-semibold text-slate-800">
                Show income and expense accounts
              </summary>

              <div className="mt-4 grid gap-6 xl:grid-cols-2">
                <AccountTable title="Income" rows={profitAndLoss.income} />
                <AccountTable title="Expenses" rows={profitAndLoss.expense} />
              </div>
            </details>
          ) : null}
        </section>
      ) : null}

      {/* ==================================================================== */}
      {/* MONTHLY PROFITABILITY                                                */}
      {/* ==================================================================== */}

      <section className="rounded-2xl border border-gray-200 bg-white shadow-sm">
        <div className="border-b border-gray-100 px-6 py-5">
          <h2 className="text-sm font-semibold text-gray-900">
            Monthly Profitability
          </h2>

          <p className="mt-1 text-xs text-gray-400">
            Six-month trend of income, expenses and profit. Higher bars show
            larger amounts.
          </p>
        </div>

        {monthlyAccounting.length === 0 ? (
          <div className="p-6 text-sm text-gray-400">
            No monthly accounting data is currently available.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <div className="min-w-[800px]">
              {monthlyAccounting.map((row) => {
                const revenueWidth = Math.min(
                  100,
                  (Math.abs(row.revenue) / monthlyMax) * 100,
                );

                const expenseWidth = Math.min(
                  100,
                  (Math.abs(row.expenses) / monthlyMax) * 100,
                );

                const profitWidth = Math.min(
                  100,
                  (Math.abs(row.profit) / monthlyMax) * 100,
                );

                return (
                  <div
                    key={`${row.month}-${row.from}`}
                    className="grid grid-cols-[130px_1fr_160px_160px_160px] items-center border-b border-gray-100 px-6 py-4 last:border-b-0"
                  >
                    <div>
                      <p className="text-sm font-semibold text-gray-800">
                        {row.label}
                      </p>

                      <p className="text-[10px] text-gray-400">
                        {row.from} → {row.to}
                      </p>
                    </div>

                    <div className="space-y-2 pr-8">
                      <div className="h-1.5 rounded-full bg-gray-100">
                        <div
                          className="h-full rounded-full bg-emerald-500"
                          style={{
                            width: `${revenueWidth}%`,
                          }}
                        />
                      </div>

                      <div className="h-1.5 rounded-full bg-gray-100">
                        <div
                          className="h-full rounded-full bg-red-400"
                          style={{
                            width: `${expenseWidth}%`,
                          }}
                        />
                      </div>

                      <div className="h-1.5 rounded-full bg-gray-100">
                        <div
                          className={`h-full rounded-full ${
                            row.profit >= 0 ? "bg-indigo-500" : "bg-red-500"
                          }`}
                          style={{
                            width: `${profitWidth}%`,
                          }}
                        />
                      </div>
                    </div>

                    <p className="text-right text-sm font-semibold text-emerald-600">
                      {fmt(row.revenue)}
                    </p>

                    <p className="text-right text-sm font-semibold text-red-500">
                      {fmt(row.expenses)}
                    </p>

                    <p
                      className={`text-right text-sm font-bold ${
                        row.profit >= 0 ? "text-indigo-600" : "text-red-600"
                      }`}
                    >
                      {fmt(row.profit)}
                    </p>
                  </div>
                );
              })}
            </div>
          </div>
        )}
      </section>

      {/* ==================================================================== */}
      {/* ACCOUNTING REPORT CENTER                                             */}
      {/* ==================================================================== */}

      <section>
        <div className="mb-4">
          <h2 className="text-sm font-semibold text-gray-900">
            Accounting Report Center
          </h2>

          <p className="mt-1 text-xs text-gray-400">
            Download the official reports. Technical accounting detail is
            available for finance users.
          </p>
        </div>

        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          <AccountingReportCard
            endpoint="trial-balance"
            title="Trial Balance"
            description="Debit and credit balances for every accounting account."
            icon="⚖️"
          />

          <AccountingReportCard
            endpoint="balance-sheet"
            title="Balance Sheet"
            description="Assets, liabilities, equity and current-period earnings."
            icon="🏦"
          />

          <AccountingReportCard
            endpoint="profit-and-loss"
            title="Profit & Loss"
            description="Income, expenses and net profit for the accounting period."
            icon="📈"
          />

          <AccountingReportCard
            endpoint="cash-flow"
            title="Cash Flow"
            description="Cash generated and used through lending, collections and other movements."
            icon="💵"
          />
        </div>
      </section>

      {/* ==================================================================== */}
      {/* OPERATIONAL REPORT CENTER                                            */}
      {/* ==================================================================== */}

      <section>
        <div className="mb-4">
          <h2 className="text-sm font-semibold text-gray-900">
            Operational Report Center
          </h2>

          <p className="mt-1 text-xs text-gray-400">
            Existing operational portfolio exports.
          </p>
        </div>

        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          <ReportCard
            endpoint="loans"
            title="Loan Portfolio"
            description="Complete loan portfolio including status, amount, balance and branch information."
            icon="📋"
          />

          <ReportCard
            endpoint="payments"
            title="Payments"
            description="Payment and collection activity."
            icon="💳"
          />

          <ReportCard
            endpoint="borrowers"
            title="Borrowers"
            description="Borrower portfolio and customer information."
            icon="👥"
          />

          <ReportCard
            endpoint="overdue"
            title="Overdue Portfolio"
            description="Loans and repayment schedules with overdue exposure."
            icon="⚠️"
          />
        </div>
      </section>

      <section className="rounded-2xl border border-indigo-100 bg-indigo-50/60 px-5 py-4">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p className="text-sm font-semibold text-indigo-900">
              Regulatory reporting is available from the dedicated workspace.
            </p>
            <p className="mt-1 text-xs text-indigo-700">
              This page stays focused on operational and accounting analytics.
            </p>
          </div>

          <Link
            href="/dashboard/reports/regulatory"
            className="inline-flex shrink-0 items-center justify-center rounded-xl bg-indigo-600 px-4 py-2.5 text-xs font-bold text-white shadow-sm transition hover:bg-indigo-700"
          >
            Open Regulatory Reports →
          </Link>
        </div>
      </section>

      {/* ==================================================================== */}
      {/* FOOTER                                                               */}
      {/* ==================================================================== */}

      <footer className="flex flex-col gap-2 border-t border-slate-200 pt-6 text-xs text-slate-400 sm:flex-row sm:items-center sm:justify-between">
        <p>Financial figures are sourced from the accounting records.</p>

        <p>
          Operational, financial and regulatory reports remain available through
          their respective report centers.
        </p>
      </footer>
    </div>
  );
}
