"use client";

import { useEffect, useMemo, useState } from "react";

import {
  getDashboardStats,
  getLoanChartData,
  getCollectionChart,
} from "../../../services/dashboardService";

import { getOverduePayments } from "../../../services/paymentService";
import { getLoans } from "../../../services/loanService";

import {
  DashboardStats,
  Payment,
  ChartPoint,
  Loan,
} from "../../../types/index";

import { PageSpinner } from "../../../components/ui/Skeleton";

import { BarChart, AreaChart } from "../../../components/charts/BarChart";

import API from "../../../services/api";
import Link from "next/link";

/* ============================================================
   TYPES
============================================================ */

type ReportFormat = "csv" | "excel";

interface AccountingAccountRow {
  code?: string;
  name?: string;
  type?: string;
  balance?: number | string | null;
  debit?: number | string | null;
  credit?: number | string | null;
}

interface TrialBalanceReport {
  accounts?: AccountingAccountRow[];
  totalDebit?: number | string | null;
  totalCredit?: number | string | null;
  balanced?: boolean;
}

interface BalanceSheetReport {
  asOf?: string;
  assets?: AccountingAccountRow[];
  liabilities?: AccountingAccountRow[];
  equity?: AccountingAccountRow[];
  currentPeriodNetIncome?: number | string | null;
  totalAssets?: number | string | null;
  totalLiabilities?: number | string | null;
  totalEquity?: number | string | null;
  balanced?: boolean;
}

interface ProfitAndLossReport {
  from?: string;
  to?: string;
  income?: AccountingAccountRow[];
  expense?: AccountingAccountRow[];
  totalIncome?: number | string | null;
  totalExpense?: number | string | null;
  netIncome?: number | string | null;
}

interface CashFlowReport {
  from?: string;
  to?: string;
  cashUsedForLending?: number | string | null;
  cashFromCollections?: number | string | null;
  cashFromFees?: number | string | null;
  otherCashMovement?: number | string | null;
  netChangeInCash?: number | string | null;
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

/* ============================================================
   HELPERS
============================================================ */

const fmt = (n?: number | null) =>
  n == null || Number.isNaN(n)
    ? "—"
    : new Intl.NumberFormat("en-US", {
        style: "currency",
        currency: "USD",
        minimumFractionDigits: 0,
        maximumFractionDigits: 0,
      }).format(n);

const fmtMoneyPrecise = (n?: number | null) =>
  n == null || Number.isNaN(n)
    ? "—"
    : new Intl.NumberFormat("en-US", {
        style: "currency",
        currency: "USD",
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      }).format(n);

const fmtNumber = (n?: number | null) =>
  n == null || Number.isNaN(n) ? "0" : new Intl.NumberFormat("en-US").format(n);

const parseMoney = (value?: number | string | null): number => {
  if (value == null) {
    return 0;
  }

  if (typeof value === "number") {
    return Number.isFinite(value) ? value : 0;
  }

  const parsed = Number(value);

  return Number.isFinite(parsed) ? parsed : 0;
};

const fmtDate = (value?: string) => {
  if (!value) return "—";

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat("en-US", {
    year: "numeric",
    month: "short",
    day: "numeric",
  }).format(date);
};

const formatMonthLabel = (date: Date) =>
  new Intl.DateTimeFormat("en-US", {
    month: "short",
    year: "numeric",
  }).format(date);

const toLocalDateString = (date: Date): string => {
  const year = date.getFullYear();

  const month = String(date.getMonth() + 1).padStart(2, "0");

  const day = String(date.getDate()).padStart(2, "0");

  return `${year}-${month}-${day}`;
};

const getMonthStart = (date: Date): Date =>
  new Date(date.getFullYear(), date.getMonth(), 1);

const getMonthEnd = (date: Date): Date =>
  new Date(date.getFullYear(), date.getMonth() + 1, 0);

const getPreviousMonth = (date: Date, monthsBack: number): Date =>
  new Date(date.getFullYear(), date.getMonth() - monthsBack, 1);

/* ============================================================
   REPORT DOWNLOAD
============================================================ */

async function downloadReport(
  endpoint: string,
  label: string,
  format: ReportFormat,
) {
  try {
    const url =
      format === "excel"
        ? `/reports/export/${endpoint}/excel`
        : `/reports/export/${endpoint}`;

    const response = await API.get(url, {
      responseType: "blob",
    });

    const blob =
      response.data instanceof Blob ? response.data : new Blob([response.data]);

    const objectUrl = URL.createObjectURL(blob);

    const anchor = document.createElement("a");

    anchor.href = objectUrl;

    const date = new Date().toISOString().slice(0, 10);

    const extension = format === "excel" ? "xlsx" : "csv";

    anchor.download = `${label}-${date}.${extension}`;

    document.body.appendChild(anchor);

    anchor.click();

    anchor.remove();

    setTimeout(() => {
      URL.revokeObjectURL(objectUrl);
    }, 60000);
  } catch (error: unknown) {
    console.error(`Could not export ${label} as ${format}`, error);

    alert(
      error instanceof Error
        ? error.message
        : `Could not export ${label} as ${format.toUpperCase()}`,
    );
  }
}

/* ============================================================
   ACCOUNTING REPORT DOWNLOAD
============================================================ */

async function downloadAccountingReport(
  endpoint: string,
  label: string,
  format: ReportFormat,
) {
  try {
    const url =
      format === "excel"
        ? `/accounting/${endpoint}/export/excel`
        : `/accounting/${endpoint}/export`;

    const response = await API.get(url, {
      responseType: "blob",
    });

    const blob =
      response.data instanceof Blob ? response.data : new Blob([response.data]);

    const objectUrl = URL.createObjectURL(blob);

    const anchor = document.createElement("a");

    anchor.href = objectUrl;

    const date = new Date().toISOString().slice(0, 10);

    const extension = format === "excel" ? "xlsx" : "csv";

    anchor.download = `${label}-${date}.${extension}`;

    document.body.appendChild(anchor);

    anchor.click();

    anchor.remove();

    setTimeout(() => {
      URL.revokeObjectURL(objectUrl);
    }, 60000);
  } catch (error: unknown) {
    console.error(
      `Could not export accounting report ${label} as ${format}`,
      error,
    );

    alert(
      error instanceof Error
        ? error.message
        : `Could not export ${label} as ${format.toUpperCase()}`,
    );
  }
}

/* ============================================================
   EXPORT BUTTON
============================================================ */

function ExportButton({
  endpoint,
  label,
  format,
}: {
  endpoint: string;
  label: string;
  format: ReportFormat;
}) {
  const isExcel = format === "excel";

  return (
    <button
      type="button"
      onClick={() => downloadReport(endpoint, label, format)}
      className={`
        inline-flex
        items-center
        justify-center
        gap-1.5
        rounded-lg
        border
        px-3
        py-2
        text-xs
        font-semibold
        transition-all
        duration-200
        focus:outline-none
        focus:ring-2
        focus:ring-offset-1
        ${
          isExcel
            ? `
              border-emerald-200
              bg-emerald-50
              text-emerald-700
              hover:bg-emerald-100
              hover:border-emerald-300
              focus:ring-emerald-300
            `
            : `
              border-gray-200
              bg-white
              text-gray-600
              hover:bg-gray-50
              hover:border-gray-300
              focus:ring-gray-300
            `
        }
      `}
    >
      <span className="text-[11px]">{isExcel ? "▣" : "⇩"}</span>

      {isExcel ? "Excel" : "CSV"}
    </button>
  );
}

/* ============================================================
   ACCOUNTING EXPORT BUTTON
============================================================ */

function AccountingExportButton({
  endpoint,
  label,
  format,
}: {
  endpoint: string;
  label: string;
  format: ReportFormat;
}) {
  const isExcel = format === "excel";

  return (
    <button
      type="button"
      onClick={() => downloadAccountingReport(endpoint, label, format)}
      className={`
        inline-flex
        items-center
        justify-center
        gap-1.5
        rounded-lg
        border
        px-3
        py-2
        text-xs
        font-semibold
        transition-all
        duration-200
        focus:outline-none
        focus:ring-2
        focus:ring-offset-1
        ${
          isExcel
            ? `
              border-emerald-200
              bg-emerald-50
              text-emerald-700
              hover:bg-emerald-100
              hover:border-emerald-300
              focus:ring-emerald-300
            `
            : `
              border-gray-200
              bg-white
              text-gray-600
              hover:bg-gray-50
              hover:border-gray-300
              focus:ring-gray-300
            `
        }
      `}
    >
      <span className="text-[11px]">{isExcel ? "▣" : "⇩"}</span>

      {isExcel ? "Excel" : "CSV"}
    </button>
  );
}

/* ============================================================
   REPORT CARD
============================================================ */

function ReportCard({
  endpoint,
  title,
  description,
  icon,
  label,
}: {
  endpoint: string;
  title: string;
  description: string;
  icon: string;
  label: string;
}) {
  return (
    <div
      className="
        group
        rounded-2xl
        border
        border-gray-200
        bg-white
        p-5
        shadow-sm
        transition-all
        duration-200
        hover:-translate-y-0.5
        hover:border-gray-300
        hover:shadow-md
      "
    >
      <div className="flex items-start justify-between gap-4">
        <div className="flex items-start gap-3">
          <div
            className="
              flex
              h-11
              w-11
              shrink-0
              items-center
              justify-center
              rounded-xl
              bg-gray-50
              text-xl
              ring-1
              ring-gray-100
            "
          >
            {icon}
          </div>

          <div>
            <h3 className="text-sm font-semibold text-gray-900">{title}</h3>

            <p className="mt-1 text-xs leading-5 text-gray-500">
              {description}
            </p>
          </div>
        </div>
      </div>

      <div className="mt-5 flex items-center justify-between border-t border-gray-100 pt-4">
        <span className="text-[11px] font-medium uppercase tracking-wider text-gray-400">
          Export report
        </span>

        <div className="flex items-center gap-1.5">
          <ExportButton endpoint={endpoint} label={label} format="csv" />

          <ExportButton endpoint={endpoint} label={label} format="excel" />
        </div>
      </div>
    </div>
  );
}

/* ============================================================
   ACCOUNTING REPORT CARD
============================================================ */

function AccountingReportCard({
  endpoint,
  title,
  description,
  icon,
  label,
}: {
  endpoint: string;
  title: string;
  description: string;
  icon: string;
  label: string;
}) {
  return (
    <div
      className="
        group
        rounded-2xl
        border
        border-gray-200
        bg-white
        p-5
        shadow-sm
        transition-all
        duration-200
        hover:-translate-y-0.5
        hover:border-indigo-200
        hover:shadow-md
      "
    >
      <div className="flex items-start gap-3">
        <div
          className="
            flex
            h-11
            w-11
            shrink-0
            items-center
            justify-center
            rounded-xl
            bg-indigo-50
            text-xl
            ring-1
            ring-indigo-100
          "
        >
          {icon}
        </div>

        <div className="min-w-0">
          <h3 className="text-sm font-semibold text-gray-900">{title}</h3>

          <p className="mt-1 text-xs leading-5 text-gray-500">{description}</p>
        </div>
      </div>

      <div className="mt-5 flex items-center justify-between border-t border-gray-100 pt-4">
        <span className="text-[11px] font-medium uppercase tracking-wider text-gray-400">
          Download
        </span>

        <div className="flex items-center gap-1.5">
          <AccountingExportButton
            endpoint={endpoint}
            label={label}
            format="csv"
          />

          <AccountingExportButton
            endpoint={endpoint}
            label={label}
            format="excel"
          />
        </div>
      </div>
    </div>
  );
}

/* ============================================================
   KPI CARD
============================================================ */

function KpiCard({
  label,
  value,
  description,
  icon,
  iconBg,
  valueColor,
}: {
  label: string;
  value: string;
  description: string;
  icon: string;
  iconBg: string;
  valueColor: string;
}) {
  return (
    <div
      className="
        relative
        overflow-hidden
        rounded-2xl
        border
        border-gray-200
        bg-white
        p-5
        shadow-sm
      "
    >
      <div className="flex items-start justify-between">
        <div>
          <p className="text-[11px] font-semibold uppercase tracking-[0.12em] text-gray-400">
            {label}
          </p>

          <p
            className={`
              mt-2
              text-2xl
              font-bold
              tracking-tight
              ${valueColor}
            `}
          >
            {value}
          </p>

          <p className="mt-1 text-xs text-gray-400">{description}</p>
        </div>

        <div
          className={`
            flex
            h-10
            w-10
            items-center
            justify-center
            rounded-xl
            text-lg
            ${iconBg}
          `}
        >
          {icon}
        </div>
      </div>
    </div>
  );
}

/* ============================================================
   ACCOUNTING STATUS BADGE
============================================================ */

function AccountingStatusBadge({
  balanced,
  label,
}: {
  balanced: boolean | undefined;
  label: string;
}) {
  if (balanced === undefined) {
    return (
      <span className="inline-flex items-center gap-1.5 rounded-full bg-gray-100 px-3 py-1 text-[11px] font-semibold text-gray-500">
        <span className="h-1.5 w-1.5 rounded-full bg-gray-400" />
        {label}: unavailable
      </span>
    );
  }

  return balanced ? (
    <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-3 py-1 text-[11px] font-semibold text-emerald-700">
      <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
      {label}: balanced
    </span>
  ) : (
    <span className="inline-flex items-center gap-1.5 rounded-full bg-red-50 px-3 py-1 text-[11px] font-semibold text-red-700">
      <span className="h-1.5 w-1.5 rounded-full bg-red-500" />
      {label}: attention required
    </span>
  );
}

/* ============================================================
   PAGE
============================================================ */

export default function ReportsPage() {
  const [stats, setStats] = useState<DashboardStats | null>(null);

  const [overdue, setOverdue] = useState<Payment[]>([]);

  const [loans, setLoans] = useState<Loan[]>([]);

  const [loanChart, setLoanChart] = useState<ChartPoint[]>([]);

  const [collectChart, setCollectChart] = useState<ChartPoint[]>([]);

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

  /* ==========================================================
     LOAD DATA
  ========================================================== */

  useEffect(() => {
    let mounted = true;

    const loadReports = async () => {
      try {
        setLoading(true);

        /*
         * Operational reporting.
         */
        const [
          dashboardStats,
          overduePayments,
          loanList,
          loanChartData,
          collectionChartData,
        ] = await Promise.all([
          getDashboardStats(),

          getOverduePayments(),

          getLoans().catch(() => [] as Loan[]),

          getLoanChartData().catch(() => [] as ChartPoint[]),

          getCollectionChart().catch(() => [] as ChartPoint[]),
        ]);

        if (!mounted) {
          return;
        }

        setStats(dashboardStats as DashboardStats);

        setOverdue(overduePayments as Payment[]);

        setLoans(loanList as Loan[]);

        setLoanChart(loanChartData as ChartPoint[]);

        setCollectChart(collectionChartData as ChartPoint[]);

        /*
         * Accounting reporting.
         *
         * AccountingService is the financial source of truth.
         * Do not reconstruct official accounting figures here
         * from operational payment/loan data.
         */
        try {
          const [
            trialBalanceResponse,
            balanceSheetResponse,
            profitAndLossResponse,
            cashFlowResponse,
          ] = await Promise.all([
            API.get("/accounting/trial-balance"),

            API.get("/accounting/balance-sheet"),

            API.get("/accounting/profit-and-loss"),

            API.get("/accounting/cash-flow"),
          ]);

          if (!mounted) {
            return;
          }

          /*
           * ApiResponse normally wraps the actual data in
           * `data`.
           *
           * This helper also tolerates a direct response so
           * that the frontend remains resilient if the API
           * wrapper changes.
           */
          const unwrap = <T,>(responseData: any): T => {
            if (
              responseData &&
              typeof responseData === "object" &&
              "data" in responseData
            ) {
              return responseData.data as T;
            }

            return responseData as T;
          };

          setTrialBalance(
            unwrap<TrialBalanceReport>(trialBalanceResponse.data),
          );

          setBalanceSheet(
            unwrap<BalanceSheetReport>(balanceSheetResponse.data),
          );

          setProfitAndLoss(
            unwrap<ProfitAndLossReport>(profitAndLossResponse.data),
          );

          setCashFlow(unwrap<CashFlowReport>(cashFlowResponse.data));

          setAccountingError(null);

          /*
           * Fetch six monthly P&L periods.
           *
           * This is intentionally fetched from the
           * accounting backend instead of calculating
           * monthly profit in React.
           */
          const today = new Date();

          const monthlyRequests = Array.from({ length: 6 }, (_, index) => {
            const monthDate = getPreviousMonth(today, 5 - index);

            const start = getMonthStart(monthDate);

            const end = getMonthEnd(monthDate);

            return {
              monthDate,
              from: toLocalDateString(start),
              to: toLocalDateString(end),
            };
          });

          const monthlyResponses = await Promise.all(
            monthlyRequests.map(async (period) => {
              try {
                const response = await API.get("/accounting/profit-and-loss", {
                  params: {
                    from: period.from,
                    to: period.to,
                  },
                });

                const data = unwrap<ProfitAndLossReport>(response.data);

                return {
                  month: period.from.slice(0, 7),
                  label: formatMonthLabel(period.monthDate),
                  from: period.from,
                  to: period.to,
                  revenue: parseMoney(data?.totalIncome),
                  expenses: parseMoney(data?.totalExpense),
                  profit: parseMoney(data?.netIncome),
                };
              } catch (error) {
                console.error(
                  "Failed to load monthly accounting report",
                  period,
                  error,
                );

                return {
                  month: period.from.slice(0, 7),
                  label: formatMonthLabel(period.monthDate),
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
            setMonthlyAccounting(monthlyResponses);
          }
        } catch (error) {
          console.error("Failed to load accounting reports", error);

          if (mounted) {
            setAccountingError(
              "Accounting reports could not be loaded. Operational reports remain available.",
            );
          }
        }
      } catch (error) {
        console.error("Failed to load reports", error);
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

  /* ==========================================================
     LOADING
  ========================================================== */

  if (loading) {
    return <PageSpinner />;
  }

  /* ==========================================================
     OPERATIONAL CALCULATIONS
  ========================================================== */

  const collectionRate =
    stats && stats.totalDisbursed > 0
      ? ((stats.totalCollected / stats.totalDisbursed) * 100).toFixed(1)
      : "0.0";

  /*
   * This remains an operational overdue KPI.
   *
   * It is NOT official accounting penalty income.
   */
  const penaltiesSum = overdue.reduce(
    (sum, payment) => sum + parseMoney(payment.penalty),
    0,
  );

  const rejectedCount = loans.filter(
    (loan) => loan.status === "REJECTED",
  ).length;

  const totalPortfolioLoans =
    (stats?.activeLoans ?? 0) +
    (stats?.pendingLoans ?? 0) +
    rejectedCount +
    (stats?.completedLoans ?? 0);

  /* ==========================================================
     ACCOUNTING VALUES
  ========================================================== */

  const totalIncome = parseMoney(profitAndLoss?.totalIncome);

  const totalExpense = parseMoney(profitAndLoss?.totalExpense);

  const netIncome = parseMoney(profitAndLoss?.netIncome);

  const totalAssets = parseMoney(balanceSheet?.totalAssets);

  const totalLiabilities = parseMoney(balanceSheet?.totalLiabilities);

  const totalEquity = parseMoney(balanceSheet?.totalEquity);

  const currentPeriodNetIncome = parseMoney(
    balanceSheet?.currentPeriodNetIncome,
  );

  const totalDebit = parseMoney(trialBalance?.totalDebit);

  const totalCredit = parseMoney(trialBalance?.totalCredit);

  const netCashChange = parseMoney(cashFlow?.netChangeInCash);

  const accountingLoaded =
    trialBalance !== null ||
    balanceSheet !== null ||
    profitAndLoss !== null ||
    cashFlow !== null;

  /* ==========================================================
     MONTHLY MAX VALUE
  ========================================================== */

  const monthlyMax = useMemo(() => {
    const maximum = monthlyAccounting.reduce(
      (max, row) =>
        Math.max(
          max,
          Math.abs(row.revenue),
          Math.abs(row.expenses),
          Math.abs(row.profit),
        ),
      0,
    );

    return maximum > 0 ? maximum : 1;
  }, [monthlyAccounting]);

  /* ==========================================================
     STATUS ROWS
  ========================================================== */

  const statusRows = [
    {
      label: "Active",
      count: stats?.activeLoans ?? 0,
      percentage:
        totalPortfolioLoans > 0
          ? Math.round(((stats?.activeLoans ?? 0) / totalPortfolioLoans) * 100)
          : 0,
      bar: "bg-emerald-500",
      dot: "bg-emerald-500",
    },
    {
      label: "Pending",
      count: stats?.pendingLoans ?? 0,
      percentage:
        totalPortfolioLoans > 0
          ? Math.round(((stats?.pendingLoans ?? 0) / totalPortfolioLoans) * 100)
          : 0,
      bar: "bg-amber-400",
      dot: "bg-amber-400",
    },
    {
      label: "Rejected",
      count: rejectedCount,
      percentage:
        totalPortfolioLoans > 0
          ? Math.round((rejectedCount / totalPortfolioLoans) * 100)
          : 0,
      bar: "bg-red-500",
      dot: "bg-red-500",
    },
    {
      label: "Closed",
      count: stats?.completedLoans ?? 0,
      percentage:
        totalPortfolioLoans > 0
          ? Math.round(
              ((stats?.completedLoans ?? 0) / totalPortfolioLoans) * 100,
            )
          : 0,
      bar: "bg-gray-400",
      dot: "bg-gray-400",
    },
  ];

  /* ==========================================================
     PAGE
  ========================================================== */

  return (
    <div className="min-h-full space-y-7 pb-10">
      {/* ======================================================
          HEADER
      ====================================================== */}

      <section
        className="
          flex
          flex-col
          gap-5
          lg:flex-row
          lg:items-end
          lg:justify-between
        "
      >
        <div>
          <div className="mb-2 flex items-center gap-2">
            <span
              className="
                inline-flex
                h-7
                w-7
                items-center
                justify-center
                rounded-lg
                bg-indigo-50
                text-sm
              "
            >
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
            Portfolio, operational and accounting intelligence from one central
            reporting workspace.
          </p>
        </div>

        <div
          className="
            flex
            items-center
            gap-2
            rounded-xl
            border
            border-gray-200
            bg-white
            px-4
            py-2.5
            shadow-sm
          "
        >
          <span className="h-2 w-2 rounded-full bg-emerald-500" />

          <span className="text-xs font-medium text-gray-600">
            Reporting data
          </span>

          <span className="text-xs text-gray-400">Updated live</span>
        </div>
      </section>

      {/* ======================================================
          ACCOUNTING WARNING
      ====================================================== */}

      {accountingError && (
        <section
          className="
            rounded-2xl
            border
            border-amber-200
            bg-amber-50
            px-5
            py-4
          "
        >
          <div className="flex items-start gap-3">
            <span className="mt-0.5 text-lg">⚠️</span>

            <div>
              <p className="text-sm font-semibold text-amber-900">
                Accounting reporting unavailable
              </p>

              <p className="mt-1 text-xs leading-5 text-amber-700">
                {accountingError}
              </p>
            </div>
          </div>
        </section>
      )}

      {/* ======================================================
          PORTFOLIO PERFORMANCE
      ====================================================== */}

      <section>
        <div className="mb-3">
          <h2 className="text-sm font-semibold text-gray-900">
            Portfolio Performance
          </h2>

          <p className="mt-0.5 text-xs text-gray-400">
            Operational portfolio indicators
          </p>
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <KpiCard
            label="Total Disbursed"
            value={fmt(stats?.totalDisbursed)}
            description="Total loan principal released"
            icon="↗"
            iconBg="bg-indigo-50 text-indigo-600"
            valueColor="text-indigo-600"
          />

          <KpiCard
            label="Total Collected"
            value={fmt(stats?.totalCollected)}
            description="Payments collected to date"
            icon="✓"
            iconBg="bg-emerald-50 text-emerald-600"
            valueColor="text-emerald-600"
          />

          <KpiCard
            label="Collection Rate"
            value={`${collectionRate}%`}
            description="Collected versus disbursed"
            icon="%"
            iconBg="bg-blue-50 text-blue-600"
            valueColor="text-blue-600"
          />

          <KpiCard
            label="Overdue Penalties"
            value={fmt(penaltiesSum)}
            description="Operational overdue penalty exposure"
            icon="!"
            iconBg="bg-orange-50 text-orange-600"
            valueColor="text-orange-600"
          />
        </div>
      </section>

      {/* ======================================================
          ACCOUNTING PERFORMANCE
      ====================================================== */}

      <section>
        <div className="mb-4 flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-sm font-semibold text-gray-900">
                Financial Performance
              </h2>

              <span
                className="
                  rounded-full
                  bg-indigo-50
                  px-2
                  py-0.5
                  text-[10px]
                  font-semibold
                  uppercase
                  tracking-wider
                  text-indigo-600
                "
              >
                Accounting
              </span>
            </div>

            <p className="mt-1 text-xs text-gray-400">
              Official financial figures generated from the double-entry
              accounting records.
            </p>
          </div>

          <div className="flex flex-wrap gap-2">
            <AccountingStatusBadge
              balanced={trialBalance?.balanced}
              label="Trial Balance"
            />

            <AccountingStatusBadge
              balanced={balanceSheet?.balanced}
              label="Balance Sheet"
            />
          </div>
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-6">
          <KpiCard
            label="Revenue"
            value={fmt(totalIncome)}
            description="Accounting income"
            icon="↗"
            iconBg="bg-emerald-50 text-emerald-600"
            valueColor="text-emerald-600"
          />

          <KpiCard
            label="Expenses"
            value={fmt(totalExpense)}
            description="Accounting expenses"
            icon="↘"
            iconBg="bg-red-50 text-red-600"
            valueColor="text-red-600"
          />

          <KpiCard
            label="Net Profit"
            value={fmt(netIncome)}
            description="Revenue less expenses"
            icon="◆"
            iconBg={
              netIncome >= 0
                ? "bg-blue-50 text-blue-600"
                : "bg-red-50 text-red-600"
            }
            valueColor={netIncome >= 0 ? "text-blue-600" : "text-red-600"}
          />

          <KpiCard
            label="Total Assets"
            value={fmt(totalAssets)}
            description="Balance sheet assets"
            icon="A"
            iconBg="bg-indigo-50 text-indigo-600"
            valueColor="text-indigo-600"
          />

          <KpiCard
            label="Liabilities"
            value={fmt(totalLiabilities)}
            description="Balance sheet liabilities"
            icon="L"
            iconBg="bg-orange-50 text-orange-600"
            valueColor="text-orange-600"
          />

          <KpiCard
            label="Equity"
            value={fmt(totalEquity)}
            description="Balance sheet equity"
            icon="E"
            iconBg="bg-purple-50 text-purple-600"
            valueColor="text-purple-600"
          />
        </div>
      </section>

      {/* ======================================================
          ACCOUNTING CONTROL SUMMARY
      ====================================================== */}

      {accountingLoaded && (
        <section
          className="
            rounded-2xl
            border
            border-gray-200
            bg-white
            shadow-sm
          "
        >
          <div className="border-b border-gray-100 px-6 py-5">
            <div className="flex flex-col gap-2 lg:flex-row lg:items-center lg:justify-between">
              <div>
                <h2 className="text-sm font-semibold text-gray-900">
                  Accounting Control Summary
                </h2>

                <p className="mt-1 text-xs text-gray-400">
                  Core accounting integrity indicators
                </p>
              </div>

              <div className="flex flex-wrap gap-2">
                <AccountingStatusBadge
                  balanced={trialBalance?.balanced}
                  label="Trial Balance"
                />

                <AccountingStatusBadge
                  balanced={balanceSheet?.balanced}
                  label="Balance Sheet"
                />
              </div>
            </div>
          </div>

          <div className="grid grid-cols-1 divide-y divide-gray-100 md:grid-cols-2 md:divide-x md:divide-y-0 xl:grid-cols-4">
            <div className="px-6 py-5">
              <p className="text-[10px] font-semibold uppercase tracking-wider text-gray-400">
                Total Debits
              </p>

              <p className="mt-2 text-xl font-bold text-gray-900">
                {fmtMoneyPrecise(totalDebit)}
              </p>

              <p className="mt-1 text-xs text-gray-400">
                Trial balance debit total
              </p>
            </div>

            <div className="px-6 py-5">
              <p className="text-[10px] font-semibold uppercase tracking-wider text-gray-400">
                Total Credits
              </p>

              <p className="mt-2 text-xl font-bold text-gray-900">
                {fmtMoneyPrecise(totalCredit)}
              </p>

              <p className="mt-1 text-xs text-gray-400">
                Trial balance credit total
              </p>
            </div>

            <div className="px-6 py-5">
              <p className="text-[10px] font-semibold uppercase tracking-wider text-gray-400">
                Current Period Profit
              </p>

              <p
                className={`mt-2 text-xl font-bold ${
                  currentPeriodNetIncome >= 0
                    ? "text-emerald-600"
                    : "text-red-600"
                }`}
              >
                {fmtMoneyPrecise(currentPeriodNetIncome)}
              </p>

              <p className="mt-1 text-xs text-gray-400">
                Balance sheet current-period result
              </p>
            </div>

            <div className="px-6 py-5">
              <p className="text-[10px] font-semibold uppercase tracking-wider text-gray-400">
                Net Cash Movement
              </p>

              <p
                className={`mt-2 text-xl font-bold ${
                  netCashChange >= 0 ? "text-emerald-600" : "text-red-600"
                }`}
              >
                {fmtMoneyPrecise(netCashChange)}
              </p>

              <p className="mt-1 text-xs text-gray-400">
                Cash flow period movement
              </p>
            </div>
          </div>
        </section>
      )}

      {/* ======================================================
          MONTHLY PROFITABILITY
      ====================================================== */}

      <section
        className="
          rounded-2xl
          border
          border-gray-200
          bg-white
          shadow-sm
        "
      >
        <div className="border-b border-gray-100 px-6 py-5">
          <div className="flex flex-col gap-2 lg:flex-row lg:items-end lg:justify-between">
            <div>
              <h2 className="text-sm font-semibold text-gray-900">
                Monthly Profitability
              </h2>

              <p className="mt-1 text-xs text-gray-400">
                Six-month accounting trend from the general ledger.
              </p>
            </div>

            <span className="text-[10px] font-semibold uppercase tracking-wider text-gray-400">
              Revenue · Expenses · Profit
            </span>
          </div>
        </div>

        {monthlyAccounting.length === 0 ? (
          <div className="flex min-h-[220px] items-center justify-center px-6 text-center">
            <div>
              <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-gray-50 text-xl">
                📈
              </div>

              <p className="text-sm font-semibold text-gray-800">
                No monthly accounting data
              </p>

              <p className="mt-1 text-xs text-gray-400">
                Monthly financial information will appear when accounting
                entries are available.
              </p>
            </div>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <div className="min-w-[760px]">
              <div className="grid grid-cols-[130px_1fr_150px_150px_150px] border-b border-gray-100 bg-gray-50/70 px-6 py-3">
                <span className="text-[10px] font-semibold uppercase tracking-wider text-gray-400">
                  Period
                </span>

                <span className="text-[10px] font-semibold uppercase tracking-wider text-gray-400">
                  Performance
                </span>

                <span className="text-right text-[10px] font-semibold uppercase tracking-wider text-gray-400">
                  Revenue
                </span>

                <span className="text-right text-[10px] font-semibold uppercase tracking-wider text-gray-400">
                  Expenses
                </span>

                <span className="text-right text-[10px] font-semibold uppercase tracking-wider text-gray-400">
                  Net Profit
                </span>
              </div>

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
                    className="
                        grid
                        grid-cols-[130px_1fr_150px_150px_150px]
                        items-center
                        border-b
                        border-gray-100
                        px-6
                        py-4
                        last:border-b-0
                      "
                  >
                    <div>
                      <p className="text-sm font-semibold text-gray-800">
                        {row.label}
                      </p>

                      <p className="mt-0.5 text-[10px] text-gray-400">
                        {row.from}
                        {" → "}
                        {row.to}
                      </p>
                    </div>

                    <div className="space-y-2 pr-8">
                      <div className="h-1.5 overflow-hidden rounded-full bg-gray-100">
                        <div
                          className="h-full rounded-full bg-emerald-500 transition-all"
                          style={{
                            width: `${revenueWidth}%`,
                          }}
                        />
                      </div>

                      <div className="h-1.5 overflow-hidden rounded-full bg-gray-100">
                        <div
                          className="h-full rounded-full bg-red-400 transition-all"
                          style={{
                            width: `${expenseWidth}%`,
                          }}
                        />
                      </div>

                      <div className="h-1.5 overflow-hidden rounded-full bg-gray-100">
                        <div
                          className={`h-full rounded-full transition-all ${
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

      {/* ======================================================
          ACCOUNTING REPORT CENTER
      ====================================================== */}

      <section>
        <div className="mb-4">
          <div className="flex items-center gap-2">
            <h2 className="text-sm font-semibold text-gray-900">
              Accounting Report Center
            </h2>

            <span
              className="
                rounded-full
                bg-indigo-50
                px-2
                py-0.5
                text-[10px]
                font-semibold
                uppercase
                tracking-wider
                text-indigo-600
              "
            >
              Financial
            </span>
          </div>

          <p className="mt-1 text-xs text-gray-400">
            Download official accounting reports generated from the
            organization&apos;s accounting records.
          </p>
        </div>

        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
          <AccountingReportCard
            endpoint="trial-balance/export"
            label="trial-balance"
            title="Trial Balance"
            description="Debit and credit balances for every chart-of-account account."
            icon="⚖️"
          />

          <AccountingReportCard
            endpoint="balance-sheet/export"
            label="balance-sheet"
            title="Balance Sheet"
            description="Assets, liabilities, equity and current-period earnings."
            icon="🏦"
          />

          <AccountingReportCard
            endpoint="profit-and-loss/export"
            label="profit-and-loss"
            title="Profit & Loss"
            description="Income, expenses and net profit for the accounting period."
            icon="📈"
          />

          <AccountingReportCard
            endpoint="cash-flow/export"
            label="cash-flow"
            title="Cash Flow"
            description="Cash generated and used through lending, collections and other movements."
            icon="💵"
          />
        </div>
      </section>

      {/* ======================================================
          OPERATIONAL EXPORT CENTER
      ====================================================== */}

      <section>
        <div className="mb-4">
          <div className="flex items-center gap-2">
            <h2 className="text-sm font-semibold text-gray-900">
              Operational Report Center
            </h2>

            <span
              className="
                rounded-full
                bg-gray-100
                px-2
                py-0.5
                text-[10px]
                font-semibold
                uppercase
                tracking-wider
                text-gray-500
              "
            >
              Export
            </span>
          </div>

          <p className="mt-1 text-xs text-gray-400">
            Operational portfolio reports in CSV or Microsoft Excel format.
          </p>
        </div>

        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
          <ReportCard
            endpoint="loans"
            label="loans"
            title="Loan Portfolio"
            description="Complete loan portfolio including status, amount, balance and branch information."
            icon="📋"
          />

          <ReportCard
            endpoint="payments"
            label="payments"
            title="Payment Register"
            description="Payment activity, amounts, penalties, payment status and references."
            icon="💳"
          />

          <ReportCard
            endpoint="overdue"
            label="overdue-payments"
            title="Overdue Portfolio"
            description="Outstanding payments with overdue days, borrower details and penalties."
            icon="⚠️"
          />

          <ReportCard
            endpoint="summary"
            label="portfolio-summary"
            title="Portfolio Summary"
            description="High-level portfolio metrics and consolidated operational performance."
            icon="📊"
          />
        </div>
      </section>

      {/* ======================================================
          FINANCIAL TRENDS
      ====================================================== */}

      {(loanChart.length > 0 || collectChart.length > 0) && (
        <section>
          <div className="mb-4">
            <h2 className="text-sm font-semibold text-gray-900">
              Portfolio Trends
            </h2>

            <p className="mt-1 text-xs text-gray-400">
              Operational loan disbursement and collection performance.
            </p>
          </div>

          <div className="grid grid-cols-1 gap-5 xl:grid-cols-2">
            {loanChart.length > 0 && (
              <div
                className="
                  overflow-hidden
                  rounded-2xl
                  border
                  border-gray-200
                  bg-white
                  shadow-sm
                "
              >
                <BarChart
                  data={loanChart}
                  label="Monthly Loan Disbursements"
                  color="bg-indigo-500"
                  valuePrefix="$"
                />
              </div>
            )}

            {collectChart.length > 0 && (
              <div
                className="
                  overflow-hidden
                  rounded-2xl
                  border
                  border-gray-200
                  bg-white
                  shadow-sm
                "
              >
                <AreaChart
                  data={collectChart}
                  label="Monthly Collections"
                  color="#10b981"
                  valuePrefix="$"
                />
              </div>
            )}
          </div>
        </section>
      )}

      {/* ======================================================
          PORTFOLIO STATUS + OVERDUE
      ====================================================== */}

      <section className="grid grid-cols-1 gap-5 xl:grid-cols-2">
        {/* ====================================================
            STATUS
        ==================================================== */}

        <div
          className="
            rounded-2xl
            border
            border-gray-200
            bg-white
            p-6
            shadow-sm
          "
        >
          <div className="flex items-start justify-between">
            <div>
              <h2 className="text-sm font-semibold text-gray-900">
                Loan Status
              </h2>

              <p className="mt-1 text-xs text-gray-400">
                Current portfolio distribution
              </p>
            </div>

            <div
              className="
                rounded-lg
                bg-gray-50
                px-2.5
                py-1.5
                text-xs
                font-semibold
                text-gray-600
              "
            >
              {fmtNumber(totalPortfolioLoans)} loans
            </div>
          </div>

          <div className="mt-6 space-y-5">
            {statusRows.map((row) => (
              <div key={row.label}>
                <div className="mb-2 flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span
                      className={`
                          h-2
                          w-2
                          rounded-full
                          ${row.dot}
                        `}
                    />

                    <span className="text-sm font-medium text-gray-700">
                      {row.label}
                    </span>
                  </div>

                  <div className="flex items-center gap-2">
                    <span className="text-sm font-semibold text-gray-900">
                      {fmtNumber(row.count)}
                    </span>

                    <span className="text-xs text-gray-400">
                      {row.percentage}%
                    </span>
                  </div>
                </div>

                <div className="h-2 overflow-hidden rounded-full bg-gray-100">
                  <div
                    className={`
                        h-full
                        rounded-full
                        transition-all
                        duration-500
                        ${row.bar}
                      `}
                    style={{
                      width: `${row.percentage}%`,
                    }}
                  />
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* ====================================================
            OVERDUE
        ==================================================== */}

        <div
          className="
            rounded-2xl
            border
            border-gray-200
            bg-white
            p-6
            shadow-sm
          "
        >
          <div className="flex items-start justify-between">
            <div>
              <h2 className="text-sm font-semibold text-gray-900">
                Overdue Payments
              </h2>

              <p className="mt-1 text-xs text-gray-400">
                Payments requiring attention
              </p>
            </div>

            {overdue.length > 0 ? (
              <span
                className="
                  rounded-full
                  bg-red-50
                  px-2.5
                  py-1
                  text-[11px]
                  font-semibold
                  text-red-600
                "
              >
                {overdue.length} overdue
              </span>
            ) : (
              <span
                className="
                  rounded-full
                  bg-emerald-50
                  px-2.5
                  py-1
                  text-[11px]
                  font-semibold
                  text-emerald-600
                "
              >
                Up to date
              </span>
            )}
          </div>

          {overdue.length === 0 ? (
            <div
              className="
                flex
                min-h-[220px]
                flex-col
                items-center
                justify-center
                text-center
              "
            >
              <div
                className="
                  mb-3
                  flex
                  h-14
                  w-14
                  items-center
                  justify-center
                  rounded-full
                  bg-emerald-50
                  text-2xl
                "
              >
                ✓
              </div>

              <p className="text-sm font-semibold text-gray-800">
                No overdue payments
              </p>

              <p className="mt-1 max-w-xs text-xs leading-5 text-gray-400">
                Your current payment portfolio has no outstanding overdue
                installments.
              </p>
            </div>
          ) : (
            <div className="mt-5 max-h-[300px] space-y-1 overflow-y-auto pr-1">
              {overdue.slice(0, 12).map((payment) => {
                const days = Math.max(
                  0,
                  Math.floor(
                    (Date.now() - new Date(payment.dueDate).getTime()) /
                      86400000,
                  ),
                );

                return (
                  <div
                    key={payment.id}
                    className="
                          flex
                          items-center
                          justify-between
                          gap-4
                          rounded-xl
                          border
                          border-transparent
                          px-3
                          py-3
                          transition
                          hover:border-gray-100
                          hover:bg-gray-50
                        "
                  >
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        <span
                          className="
                                flex
                                h-7
                                w-7
                                shrink-0
                                items-center
                                justify-center
                                rounded-lg
                                bg-red-50
                                text-xs
                                text-red-600
                              "
                        >
                          !
                        </span>

                        <p className="truncate text-sm font-semibold text-gray-800">
                          Payment #{payment.id}
                        </p>
                      </div>

                      <p className="mt-1 pl-9 text-xs text-gray-400">
                        Due {fmtDate(payment.dueDate)} ·{" "}
                        <span className="font-medium text-red-500">
                          {days}d overdue
                        </span>
                      </p>
                    </div>

                    <div className="shrink-0 text-right">
                      <p className="text-sm font-bold text-gray-900">
                        {fmt(parseMoney(payment.amount))}
                      </p>

                      {parseMoney(payment.penalty) > 0 && (
                        <p className="mt-0.5 text-[11px] font-medium text-orange-500">
                          +{fmt(parseMoney(payment.penalty))} penalty
                        </p>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </section>

      {/* ======================================================
          REGULATORY REPORTING
      ====================================================== */}

      <Link
        href="/dashboard/reports/regulatory"
        className="
          group
          relative
          block
          overflow-hidden
          rounded-2xl
          bg-[#0D1B2A]
          p-6
          text-white
          shadow-sm
          transition-all
          duration-200
          hover:shadow-lg
        "
      >
        <div
          className="
            absolute
            -right-16
            -top-16
            h-40
            w-40
            rounded-full
            bg-white/5
          "
        />

        <div
          className="
            absolute
            -bottom-20
            right-32
            h-48
            w-48
            rounded-full
            bg-indigo-500/10
          "
        />

        <div className="relative flex items-center justify-between gap-5">
          <div className="flex items-center gap-4">
            <div
              className="
                flex
                h-12
                w-12
                shrink-0
                items-center
                justify-center
                rounded-xl
                bg-white/10
                text-xl
                ring-1
                ring-white/10
              "
            >
              🏦
            </div>

            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-sm font-semibold">Regulatory Reporting</h2>

                <span
                  className="
                    rounded-full
                    border
                    border-white/10
                    bg-white/5
                    px-2
                    py-0.5
                    text-[9px]
                    font-semibold
                    uppercase
                    tracking-wider
                    text-white/60
                  "
                >
                  Compliance
                </span>
              </div>

              <p className="mt-1 max-w-2xl text-xs leading-5 text-white/50">
                BNR portfolio reports, credit bureau exports and API access for
                approved external reporting systems.
              </p>
            </div>
          </div>

          <div
            className="
              flex
              h-10
              w-10
              shrink-0
              items-center
              justify-center
              rounded-full
              border
              border-white/10
              bg-white/5
              text-white/60
              transition
              group-hover:translate-x-1
              group-hover:text-white
            "
          >
            →
          </div>
        </div>
      </Link>

      {/* ======================================================
          PORTFOLIO HEALTH
      ====================================================== */}

      <section
        className="
          overflow-hidden
          rounded-2xl
          border
          border-gray-200
          bg-white
          shadow-sm
        "
      >
        <div className="border-b border-gray-100 px-6 py-5">
          <h2 className="text-sm font-semibold text-gray-900">
            Portfolio Health
          </h2>

          <p className="mt-1 text-xs text-gray-400">
            Current operational portfolio indicators
          </p>
        </div>

        <div className="grid grid-cols-2 divide-x divide-gray-100 md:grid-cols-4">
          <div className="px-5 py-6 text-center">
            <div className="mx-auto mb-3 flex h-11 w-11 items-center justify-center rounded-xl bg-indigo-50 text-lg">
              👥
            </div>

            <p className="text-2xl font-bold tracking-tight text-gray-900">
              {fmtNumber(stats?.totalBorrowers)}
            </p>

            <p className="mt-1 text-xs font-medium text-gray-400">Borrowers</p>
          </div>

          <div className="px-5 py-6 text-center">
            <div className="mx-auto mb-3 flex h-11 w-11 items-center justify-center rounded-xl bg-emerald-50 text-lg">
              📋
            </div>

            <p className="text-2xl font-bold tracking-tight text-gray-900">
              {fmtNumber(stats?.activeLoans)}
            </p>

            <p className="mt-1 text-xs font-medium text-gray-400">
              Active Loans
            </p>
          </div>

          <div className="px-5 py-6 text-center">
            <div className="mx-auto mb-3 flex h-11 w-11 items-center justify-center rounded-xl bg-red-50 text-lg">
              ⚠️
            </div>

            <p className="text-2xl font-bold tracking-tight text-gray-900">
              {fmtNumber(stats?.overdueLoans)}
            </p>

            <p className="mt-1 text-xs font-medium text-gray-400">
              Overdue Loans
            </p>
          </div>

          <div className="px-5 py-6 text-center">
            <div className="mx-auto mb-3 flex h-11 w-11 items-center justify-center rounded-xl bg-gray-100 text-lg">
              ✓
            </div>

            <p className="text-2xl font-bold tracking-tight text-gray-900">
              {fmtNumber(stats?.completedLoans)}
            </p>

            <p className="mt-1 text-xs font-medium text-gray-400">
              Closed Loans
            </p>
          </div>
        </div>
      </section>

      {/* ======================================================
          FOOTER
      ====================================================== */}

      <div
        className="
          flex
          flex-col
          gap-2
          border-t
          border-gray-200
          pt-5
          text-xs
          text-gray-400
          sm:flex-row
          sm:items-center
          sm:justify-between
        "
      >
        <p>
          Accounting figures are sourced from the organization&apos;s accounting
          records.
        </p>

        <p>Operational and accounting exports available</p>
      </div>
    </div>
  );
}
