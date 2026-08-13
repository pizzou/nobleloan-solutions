"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";

import API from "../../../services/api";
import {
  getDashboardStats,
  getLoanChartData,
  getCollectionChart,
} from "../../../services/dashboardService";
import { getOverduePayments } from "../../../services/paymentService";
import { getLoans } from "../../../services/loanService";

import { PageSpinner } from "../../../components/ui/Skeleton";

type ReportFormat = "csv" | "excel";

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
  id?: number;
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

interface ChartPointLike {
  label?: string;
  name?: string;
  date?: string;
  value?: Numeric;
  amount?: Numeric;
}

const numberValue = (value: Numeric): number => {
  if (value === null || value === undefined || value === "") {
    return 0;
  }

  const parsed = Number(value);

  return Number.isFinite(parsed) ? parsed : 0;
};

const fmt = (value: Numeric): string =>
  new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "RWF",
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  }).format(numberValue(value));

const fmtPrecise = (value: Numeric): string =>
  new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "RWF",
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(numberValue(value));

const fmtNumber = (value: Numeric): string =>
  new Intl.NumberFormat("en-US").format(numberValue(value));

const fmtDate = (value?: string | null): string => {
  if (!value) return "—";

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

const unwrap = <T,>(value: unknown): T => {
  if (!value || typeof value !== "object") {
    return value as T;
  }

  const first = value as {
    data?: unknown;
  };

  const data = first.data;

  if (data && typeof data === "object" && "data" in data) {
    return (data as { data?: T }).data as T;
  }

  if (data && typeof data === "object" && "content" in data) {
    return (data as { content?: T }).content as T;
  }

  return (data ?? value) as T;
};

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

  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();

  window.setTimeout(() => {
    window.URL.revokeObjectURL(objectUrl);
  }, 60_000);
};

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

  const exportReport = async (format: "csv" | "excel") => {
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

      await downloadBlob(
        url,
        `${label}-${new Date().toISOString().slice(0, 10)}.${extension}`,
      );
    } catch (error) {
      console.error(`Failed to export ${label}`, error);

      window.alert(
        error instanceof Error ? error.message : `Unable to export ${label}.`,
      );
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
        className="rounded-lg border border-gray-200 bg-white px-3 py-2 text-xs font-semibold text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
      >
        {loading === "csv" ? "Preparing..." : "CSV"}
      </button>

      <button
        type="button"
        disabled={loading !== null}
        onClick={() => void exportReport("excel")}
        className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-xs font-semibold text-emerald-700 hover:bg-emerald-100 disabled:cursor-not-allowed disabled:opacity-50"
      >
        {loading === "excel" ? "Preparing..." : "Excel"}
      </button>
    </div>
  );
}

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
    <div className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm">
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

        <ExportButtons
          endpoint={endpoint}
          label={title.toLowerCase().replace(/\s+/g, "-")}
        />
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
    <div className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm">
      <div className="flex gap-3">
        <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-indigo-50 text-xl">
          {icon}
        </div>

        <div>
          <h3 className="text-sm font-semibold text-gray-900">{title}</h3>

          <p className="mt-1 text-xs leading-5 text-gray-500">{description}</p>
        </div>
      </div>

      <div className="mt-5 flex items-center justify-between border-t border-gray-100 pt-4">
        <span className="text-[10px] font-semibold uppercase tracking-wider text-gray-400">
          Financial
        </span>

        <ExportButtons
          endpoint={endpoint}
          label={title.toLowerCase().replace(/\s+/g, "-")}
          accounting
        />
      </div>
    </div>
  );
}

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
    <div className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm">
      <p className="text-[10px] font-semibold uppercase tracking-wider text-gray-400">
        {label}
      </p>

      <p className="mt-2 text-2xl font-bold tracking-tight text-gray-900">
        {value}
      </p>

      {description && (
        <p className="mt-1 text-xs text-gray-400">{description}</p>
      )}
    </div>
  );
}

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

      {!rows?.length ? (
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
                    <td className="px-4 py-3 text-gray-400">
                      {row.code ?? "—"}
                    </td>

                    <td className="px-4 py-3 font-medium text-gray-700">
                      {row.name ?? "Unnamed Account"}
                    </td>

                    <td className="px-4 py-3 text-right font-semibold text-gray-900">
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

    const load = async () => {
      setLoading(true);

      try {
        const [dashboardStats, overduePayments, loanList] = await Promise.all([
          getDashboardStats().catch(() => null),
          getOverduePayments().catch(() => []),
          getLoans().catch(() => []),
        ]);

        if (!mounted) return;

        setStats((dashboardStats ?? null) as DashboardStatsLike | null);

        setOverdue(
          (Array.isArray(overduePayments)
            ? overduePayments
            : []) as PaymentLike[],
        );

        setLoans((Array.isArray(loanList) ? loanList : []) as LoanLike[]);

        await Promise.all([
          getLoanChartData().catch(() => []),
          getCollectionChart().catch(() => []),
        ]);

        try {
          const [tb, bs, pnl, cf] = await Promise.all([
            API.get("/accounting/trial-balance"),
            API.get("/accounting/balance-sheet"),
            API.get("/accounting/profit-and-loss"),
            API.get("/accounting/cash-flow"),
          ]);

          if (!mounted) return;

          setTrialBalance(unwrap<TrialBalanceReport>(tb));

          setBalanceSheet(unwrap<BalanceSheetReport>(bs));

          setProfitAndLoss(unwrap<ProfitAndLossReport>(pnl));

          setCashFlow(unwrap<CashFlowReport>(cf));

          setAccountingError(null);

          const today = new Date();

          const periods = Array.from({ length: 6 }, (_, index) => {
            const date = previousMonth(today, 5 - index);

            const from = toDateString(monthStart(date));

            const to = toDateString(monthEnd(date));

            return {
              date,
              from,
              to,
            };
          });

          const monthly = await Promise.all(
            periods.map(async (period) => {
              try {
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
                  expenses: numberValue(
                    data?.totalExpense ?? data?.totalExpenses,
                  ),
                  profit: numberValue(data?.netIncome),
                };
              } catch {
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
              }
            }),
          );

          if (mounted) {
            setMonthlyAccounting(monthly);
          }
        } catch (error) {
          console.error("Accounting reports failed", error);

          if (mounted) {
            setAccountingError(
              "Accounting reports could not be loaded. Operational reports remain available.",
            );
          }
        }
      } catch (error) {
        console.error("Reports loading failed", error);
      } finally {
        if (mounted) {
          setLoading(false);
        }
      }
    };

    void load();

    return () => {
      mounted = false;
    };
  }, []);

  const collectionRate = useMemo(() => {
    const disbursed = numberValue(stats?.totalDisbursed);

    const collected = numberValue(stats?.totalCollected);

    return disbursed > 0 ? ((collected / disbursed) * 100).toFixed(1) : "0.0";
  }, [stats]);

  const penalties = useMemo(
    () =>
      overdue.reduce((sum, payment) => sum + numberValue(payment.penalty), 0),
    [overdue],
  );

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
    (loan) => loan.status === "REJECTED",
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

  if (loading) {
    return <PageSpinner />;
  }

  return (
    <div className="min-h-full space-y-7 pb-10">
      <section className="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <div className="mb-2 flex items-center gap-2">
            <span className="inline-flex h-7 w-7 items-center justify-center rounded-lg bg-indigo-50 text-sm">
              ◫
            </span>

            <span className="text-xs font-semibold uppercase tracking-[0.14em] text-indigo-600">
              Business Intelligence
            </span>
          </div>

          <h1 className="text-3xl font-bold tracking-tight text-gray-950">
            Reports &amp; Analytics
          </h1>

          <p className="mt-1.5 max-w-2xl text-sm text-gray-500">
            Portfolio, operational, regulatory and accounting intelligence from
            one reporting workspace.
          </p>
        </div>

        <Link
          href="/dashboard/reports/regulatory"
          className="rounded-xl border border-gray-200 bg-white px-4 py-2.5 text-xs font-semibold text-gray-700 shadow-sm hover:bg-gray-50"
        >
          Regulatory Reports →
        </Link>
      </section>

      {accountingError && (
        <section className="rounded-2xl border border-amber-200 bg-amber-50 px-5 py-4">
          <p className="text-sm font-semibold text-amber-900">
            Accounting reporting unavailable
          </p>

          <p className="mt-1 text-xs text-amber-700">{accountingError}</p>
        </section>
      )}

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

      <section>
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 className="text-sm font-semibold text-gray-900">
              Financial Performance
            </h2>

            <p className="mt-1 text-xs text-gray-400">
              Official figures sourced from double-entry accounting.
            </p>
          </div>

          <div className="flex flex-wrap gap-2">
            <AccountingStatus
              label="Trial Balance"
              balanced={trialBalance?.balanced}
            />

            <AccountingStatus
              label="Balance Sheet"
              balanced={balanceSheet?.balanced}
            />
          </div>
        </div>

        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-6">
          <Kpi label="Revenue" value={fmt(totalIncome)} />

          <Kpi label="Expenses" value={fmt(totalExpenses)} />

          <Kpi label="Net Profit" value={fmt(netIncome)} />

          <Kpi label="Assets" value={fmt(totalAssets)} />

          <Kpi label="Liabilities" value={fmt(totalLiabilities)} />

          <Kpi label="Equity" value={fmt(totalEquity)} />
        </div>
      </section>

      {(trialBalance || balanceSheet || profitAndLoss || cashFlow) && (
        <section className="rounded-2xl border border-gray-200 bg-white shadow-sm">
          <div className="border-b border-gray-100 px-6 py-5">
            <h2 className="text-sm font-semibold text-gray-900">
              Accounting Control Summary
            </h2>
          </div>

          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            <Kpi label="Total Debits" value={fmtPrecise(totalDebit)} />

            <Kpi label="Total Credits" value={fmtPrecise(totalCredit)} />

            <Kpi
              label="Current Period Profit"
              value={fmtPrecise(currentPeriodNetIncome)}
            />

            <Kpi label="Net Cash Movement" value={fmtPrecise(netCashChange)} />
          </div>

          {trialBalance && (
            <div className="grid gap-6 border-t border-gray-100 p-6 xl:grid-cols-2">
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
          )}

          {balanceSheet && (
            <div className="grid gap-6 border-t border-gray-100 p-6 xl:grid-cols-3">
              <AccountTable title="Assets" rows={balanceSheet.assets} />

              <AccountTable
                title="Liabilities"
                rows={balanceSheet.liabilities}
              />

              <AccountTable title="Equity" rows={balanceSheet.equity} />
            </div>
          )}

          {profitAndLoss && (
            <div className="grid gap-6 border-t border-gray-100 p-6 xl:grid-cols-2">
              <AccountTable title="Income" rows={profitAndLoss.income} />

              <AccountTable title="Expenses" rows={profitAndLoss.expense} />
            </div>
          )}
        </section>
      )}

      <section className="rounded-2xl border border-gray-200 bg-white shadow-sm">
        <div className="border-b border-gray-100 px-6 py-5">
          <h2 className="text-sm font-semibold text-gray-900">
            Monthly Profitability
          </h2>

          <p className="mt-1 text-xs text-gray-400">
            Six-month accounting trend from the general ledger.
          </p>
        </div>

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
                  key={row.month}
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
      </section>

      <section>
        <div className="mb-4">
          <h2 className="text-sm font-semibold text-gray-900">
            Accounting Report Center
          </h2>

          <p className="mt-1 text-xs text-gray-400">
            Download official financial reports from AccountingService.
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

      <section>
        <div className="mb-4">
          <h2 className="text-sm font-semibold text-gray-900">
            Regulatory Reporting
          </h2>

          <p className="mt-1 text-xs text-gray-400">
            BNR portfolio reporting, Credit Bureau exports and secure regulatory
            API access.
          </p>
        </div>

        <div className="grid gap-4 md:grid-cols-3">
          <Link
            href="/dashboard/reports/regulatory/bnr"
            className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm transition hover:-translate-y-0.5 hover:shadow-md"
          >
            <div className="text-2xl">🏦</div>

            <h3 className="mt-3 text-sm font-semibold text-gray-900">
              BNR Reports
            </h3>

            <p className="mt-1 text-xs leading-5 text-gray-500">
              Portfolio, PAR, NPL, borrower, financial statement and regulatory
              breakdown reports.
            </p>
          </Link>

          <Link
            href="/dashboard/reports/regulatory/credit-bureau"
            className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm transition hover:-translate-y-0.5 hover:shadow-md"
          >
            <div className="text-2xl">🧾</div>

            <h3 className="mt-3 text-sm font-semibold text-gray-900">
              Credit Bureau
            </h3>

            <p className="mt-1 text-xs leading-5 text-gray-500">
              Credit records, repayment history and approved Credit Bureau
              exports.
            </p>
          </Link>

          <Link
            href="/dashboard/reports/regulatory"
            className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm transition hover:-translate-y-0.5 hover:shadow-md"
          >
            <div className="text-2xl">🔐</div>

            <h3 className="mt-3 text-sm font-semibold text-gray-900">
              Regulatory API Access
            </h3>

            <p className="mt-1 text-xs leading-5 text-gray-500">
              Manage BNR and Credit Bureau API clients and access.
            </p>
          </Link>
        </div>
      </section>

      <footer className="flex flex-col gap-2 border-t border-gray-200 pt-5 text-xs text-gray-400 sm:flex-row sm:items-center sm:justify-between">
        <p>Financial figures are sourced from the accounting records.</p>

        <p>
          Operational, financial and regulatory reports remain available through
          their respective report centers.
        </p>
      </footer>
    </div>
  );
}
