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

type LoanClassification = {
  current: number;
  watch: number;
  substandard: number;
  doubtful: number;
  writtenOff: number;
};

type ArrearsSummary = {
  notDue: number;
  pastDue: number;
};

type CollectionStageSummary = {
  normal: number;
  reminder: number;
  collection: number;
  legal: number;
  recovery: number;
};

/* ============================================================
   HELPERS
============================================================ */

const DEFAULT_CURRENCY = "RWF";

const normalizeCurrency = (currency?: string | null): string => {
  if (!currency || !currency.trim()) {
    return DEFAULT_CURRENCY;
  }

  return currency.trim().toUpperCase();
};

const fmt = (value?: number | null, currency = DEFAULT_CURRENCY): string => {
  if (value == null || Number.isNaN(Number(value))) {
    return "—";
  }

  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: normalizeCurrency(currency),
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  }).format(Number(value));
};

const fmtDecimal = (
  value?: number | null,
  currency = DEFAULT_CURRENCY,
): string => {
  if (value == null || Number.isNaN(Number(value))) {
    return "—";
  }

  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: normalizeCurrency(currency),
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(Number(value));
};

const fmtNumber = (value?: number | null): string => {
  if (value == null || Number.isNaN(Number(value))) {
    return "0";
  }

  return new Intl.NumberFormat("en-US").format(Number(value));
};

const fmtDate = (value?: string | null): string => {
  if (!value) {
    return "—";
  }

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

const safeStatus = (value?: unknown): string => {
  if (value == null) {
    return "";
  }

  return String(value).trim().toUpperCase();
};

const prettyLabel = (value?: unknown): string => {
  if (value == null || String(value).trim() === "") {
    return "—";
  }

  return String(value)
    .trim()
    .toLowerCase()
    .split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
};

/* ============================================================
   REPORT DOWNLOAD
============================================================ */

async function downloadReport(
  endpoint: string,
  label: string,
  format: "csv" | "excel",
): Promise<void> {
  try {
    const url =
      format === "excel"
        ? `/reports/export/${endpoint}/excel`
        : `/reports/export/${endpoint}`;

    const response = await API.get(url, {
      responseType: "blob",
      validateStatus: (status) => status >= 200 && status < 300,
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

    window.setTimeout(() => {
      URL.revokeObjectURL(objectUrl);
    }, 60000);
  } catch (error: unknown) {
    console.error(`Could not export ${label} as ${format}`, error);

    const message =
      error instanceof Error
        ? error.message
        : `Could not export ${label} as ${format.toUpperCase()}`;

    window.alert(message);
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
  format: "csv" | "excel";
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

      <div
        className="
          mt-5
          flex
          items-center
          justify-between
          border-t
          border-gray-100
          pt-4
        "
      >
        <span
          className="
            text-[11px]
            font-medium
            uppercase
            tracking-wider
            text-gray-400
          "
        >
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
          <p
            className="
              text-[11px]
              font-semibold
              uppercase
              tracking-[0.12em]
              text-gray-400
            "
          >
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
   CLASSIFICATION BADGE
============================================================ */

function ClassificationBadge({
  label,
  count,
  className,
}: {
  label: string;
  count: number;
  className: string;
}) {
  return (
    <div
      className="
        flex
        items-center
        justify-between
        rounded-xl
        border
        border-gray-100
        bg-gray-50/70
        px-4
        py-3
      "
    >
      <div className="flex items-center gap-2">
        <span
          className={`
            h-2.5
            w-2.5
            rounded-full
            ${className}
          `}
        />

        <span className="text-sm text-gray-700">{label}</span>
      </div>

      <span className="text-sm font-bold text-gray-900">
        {fmtNumber(count)}
      </span>
    </div>
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

  const [loading, setLoading] = useState(true);

  const [loadError, setLoadError] = useState<string | null>(null);

  /* ==========================================================
     LOAD DATA
  ========================================================== */

  useEffect(() => {
    let mounted = true;

    setLoading(true);
    setLoadError(null);

    Promise.all([
      getDashboardStats(),

      getOverduePayments(),

      getLoans().catch(() => [] as Loan[]),

      getLoanChartData().catch(() => [] as ChartPoint[]),

      getCollectionChart().catch(() => [] as ChartPoint[]),
    ])
      .then(
        ([
          dashboardStats,
          overduePayments,
          loanList,
          loanChartData,
          collectionChartData,
        ]) => {
          if (!mounted) {
            return;
          }

          setStats(dashboardStats as DashboardStats);

          setOverdue(overduePayments as Payment[]);

          setLoans(loanList as Loan[]);

          setLoanChart(loanChartData as ChartPoint[]);

          setCollectChart(collectionChartData as ChartPoint[]);
        },
      )
      .catch((error) => {
        if (!mounted) {
          return;
        }

        console.error("Failed to load reports", error);

        setLoadError(
          error instanceof Error
            ? error.message
            : "Unable to load reporting data.",
        );
      })
      .finally(() => {
        if (mounted) {
          setLoading(false);
        }
      });

    return () => {
      mounted = false;
    };
  }, []);

  /* ==========================================================
     CURRENCY
  ========================================================== */

  const currency = useMemo(() => {
    const loanCurrency = loans.find(
      (loan) =>
        typeof (loan as any)?.currency === "string" &&
        String((loan as any).currency).trim(),
    );

    return normalizeCurrency((loanCurrency as any)?.currency);
  }, [loans]);

  /* ==========================================================
     CALCULATIONS
  ========================================================== */

  const collectionRate =
    stats && Number(stats.totalDisbursed) > 0
      ? (
          (Number(stats.totalCollected) / Number(stats.totalDisbursed)) *
          100
        ).toFixed(1)
      : "0.0";

  const penaltiesSum = overdue.reduce(
    (sum, payment) => sum + Number(payment.penalty ?? 0),
    0,
  );

  const rejectedCount = loans.filter(
    (loan) => safeStatus(loan.status) === "REJECTED",
  ).length;

  const defaultedCount = loans.filter(
    (loan) =>
      safeStatus(loan.status) === "DEFAULTED" ||
      safeStatus(loan.status) === "WRITTEN_OFF",
  ).length;

  const totalPortfolioLoans = Math.max(
    Number((stats as any)?.totalLoans ?? 0),
    Number(stats?.activeLoans ?? 0) +
      Number(stats?.pendingLoans ?? 0) +
      rejectedCount +
      Number(stats?.completedLoans ?? 0) +
      defaultedCount,
  );

  /* ==========================================================
     CREDIT QUALITY
  ========================================================== */

  const classificationSummary = useMemo<LoanClassification>(() => {
    const result: LoanClassification = {
      current: 0,
      watch: 0,
      substandard: 0,
      doubtful: 0,
      writtenOff: 0,
    };

    for (const loan of loans) {
      const quality = safeStatus((loan as any)?.creditQuality);

      switch (quality) {
        case "WATCH":
          result.watch += 1;
          break;

        case "SUBSTANDARD":
          result.substandard += 1;
          break;

        case "DOUBTFUL":
          result.doubtful += 1;
          break;

        case "WRITTEN_OFF":
          result.writtenOff += 1;
          break;

        case "CURRENT":
        default:
          result.current += 1;
          break;
      }
    }

    return result;
  }, [loans]);

  /* ==========================================================
     ARREARS
  ========================================================== */

  const arrearsSummary = useMemo<ArrearsSummary>(() => {
    const result: ArrearsSummary = {
      notDue: 0,
      pastDue: 0,
    };

    for (const loan of loans) {
      const arrears = safeStatus((loan as any)?.arrearsStatus);

      if (arrears === "PAST_DUE") {
        result.pastDue += 1;
      } else {
        result.notDue += 1;
      }
    }

    return result;
  }, [loans]);

  /* ==========================================================
     COLLECTION STAGES
  ========================================================== */

  const collectionStages = useMemo<CollectionStageSummary>(() => {
    const result: CollectionStageSummary = {
      normal: 0,
      reminder: 0,
      collection: 0,
      legal: 0,
      recovery: 0,
    };

    for (const loan of loans) {
      const stage = safeStatus((loan as any)?.collectionsStage);

      switch (stage) {
        case "REMINDER":
          result.reminder += 1;
          break;

        case "COLLECTION":
          result.collection += 1;
          break;

        case "LEGAL":
          result.legal += 1;
          break;

        case "RECOVERY":
          result.recovery += 1;
          break;

        case "NORMAL":
        default:
          result.normal += 1;
          break;
      }
    }

    return result;
  }, [loans]);

  /* ==========================================================
     OVERDUE DAYS
  ========================================================== */

  const overdueDaysSummary = useMemo(() => {
    if (loans.length === 0) {
      return {
        overdueLoans: 0,
        totalDays: 0,
        maximumDays: 0,
        averageDays: 0,
      };
    }

    const overdueValues = loans
      .map((loan) => Math.max(0, Number((loan as any)?.daysOverdue ?? 0)))
      .filter((days) => days > 0);

    if (overdueValues.length === 0) {
      return {
        overdueLoans: 0,
        totalDays: 0,
        maximumDays: 0,
        averageDays: 0,
      };
    }

    const totalDays = overdueValues.reduce((sum, value) => sum + value, 0);

    return {
      overdueLoans: overdueValues.length,

      totalDays,

      maximumDays: Math.max(...overdueValues),

      averageDays: totalDays / overdueValues.length,
    };
  }, [loans]);

  /* ==========================================================
     STATUS ROWS
  ========================================================== */

  const statusRows = [
    {
      label: "Active",
      count: Number(stats?.activeLoans ?? 0),
      percentage:
        totalPortfolioLoans > 0
          ? Math.round(
              (Number(stats?.activeLoans ?? 0) / totalPortfolioLoans) * 100,
            )
          : 0,
      bar: "bg-emerald-500",
      dot: "bg-emerald-500",
    },

    {
      label: "Pending",
      count: Number(stats?.pendingLoans ?? 0),
      percentage:
        totalPortfolioLoans > 0
          ? Math.round(
              (Number(stats?.pendingLoans ?? 0) / totalPortfolioLoans) * 100,
            )
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
      count: Number(stats?.completedLoans ?? 0),
      percentage:
        totalPortfolioLoans > 0
          ? Math.round(
              (Number(stats?.completedLoans ?? 0) / totalPortfolioLoans) * 100,
            )
          : 0,
      bar: "bg-gray-400",
      dot: "bg-gray-400",
    },
  ];

  /* ==========================================================
     LOADING
  ========================================================== */

  if (loading) {
    return <PageSpinner />;
  }

  /* ==========================================================
     ERROR
  ========================================================== */

  if (loadError && !stats && loans.length === 0) {
    return (
      <div className="min-h-full p-6">
        <div
          className="
            mx-auto
            max-w-3xl
            rounded-2xl
            border
            border-red-200
            bg-red-50
            p-6
            text-center
          "
        >
          <div className="text-3xl">⚠️</div>

          <h1 className="mt-3 text-lg font-bold text-red-900">
            Reporting data could not be loaded
          </h1>

          <p className="mt-2 text-sm text-red-700">{loadError}</p>

          <button
            type="button"
            onClick={() => window.location.reload()}
            className="
              mt-5
              rounded-lg
              bg-red-700
              px-4
              py-2
              text-sm
              font-semibold
              text-white
              hover:bg-red-800
            "
          >
            Retry
          </button>
        </div>
      </div>
    );
  }

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

            <span
              className="
                text-xs
                font-semibold
                uppercase
                tracking-[0.14em]
                text-indigo-600
              "
            >
              Business Intelligence
            </span>
          </div>

          <h1
            className="
              text-3xl
              font-bold
              tracking-tight
              text-gray-950
            "
          >
            Reports &amp; Analytics
          </h1>

          <p
            className="
              mt-1.5
              max-w-2xl
              text-sm
              text-gray-500
            "
          >
            Monitor portfolio performance, collections, loan activity and
            financial trends from one central reporting workspace.
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
          <span
            className="
              h-2
              w-2
              rounded-full
              bg-emerald-500
            "
          />

          <span className="text-xs font-medium text-gray-600">
            Reporting data
          </span>

          <span className="text-xs text-gray-400">{currency}</span>
        </div>
      </section>

      {/* ======================================================
          KPI SECTION
      ====================================================== */}

      <section>
        <div className="mb-3">
          <h2 className="text-sm font-semibold text-gray-900">
            Portfolio Performance
          </h2>

          <p className="mt-0.5 text-xs text-gray-400">
            Key financial indicators
          </p>
        </div>

        <div
          className="
            grid
            grid-cols-1
            gap-4
            sm:grid-cols-2
            xl:grid-cols-4
          "
        >
          <KpiCard
            label="Total Disbursed"
            value={fmt(stats?.totalDisbursed, currency)}
            description="Total loan principal released"
            icon="↗"
            iconBg="bg-indigo-50 text-indigo-600"
            valueColor="text-indigo-600"
          />

          <KpiCard
            label="Total Collected"
            value={fmt(stats?.totalCollected, currency)}
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
            label="Penalty Income"
            value={fmt(penaltiesSum, currency)}
            description="Penalties recorded on overdue payments"
            icon="!"
            iconBg="bg-orange-50 text-orange-600"
            valueColor="text-orange-600"
          />
        </div>
      </section>

      {/* ======================================================
          EXPORT CENTER
      ====================================================== */}

      <section>
        <div className="mb-4">
          <div className="flex items-center gap-2">
            <h2 className="text-sm font-semibold text-gray-900">
              Report Center
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
            Download the existing operational reports in CSV or Microsoft Excel
            format.
          </p>
        </div>

        <div
          className="
            grid
            grid-cols-1
            gap-4
            md:grid-cols-2
            xl:grid-cols-4
          "
        >
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
            description="High-level portfolio metrics and consolidated financial performance."
            icon="📊"
          />
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

        <div
          className="
            relative
            flex
            items-center
            justify-between
            gap-5
          "
        >
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

              <p
                className="
                  mt-1
                  max-w-2xl
                  text-xs
                  leading-5
                  text-white/50
                "
              >
                BNR portfolio reports, Credit Bureau exports and secure API
                access for approved external reporting systems.
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
          FINANCIAL TRENDS
      ====================================================== */}

      {(loanChart.length > 0 || collectChart.length > 0) && (
        <section>
          <div className="mb-4">
            <h2 className="text-sm font-semibold text-gray-900">
              Financial Trends
            </h2>

            <p className="mt-1 text-xs text-gray-400">
              Recent loan disbursement and collection performance.
            </p>
          </div>

          <div
            className="
              grid
              grid-cols-1
              gap-5
              xl:grid-cols-2
            "
          >
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
                  valuePrefix={`${currency} `}
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
                  valuePrefix={`${currency} `}
                />
              </div>
            )}
          </div>
        </section>
      )}

      {/* ======================================================
          PORTFOLIO STATUS + OVERDUE
      ====================================================== */}

      <section
        className="
          grid
          grid-cols-1
          gap-5
          xl:grid-cols-2
        "
      >
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
                <div
                  className="
                    mb-2
                    flex
                    items-center
                    justify-between
                  "
                >
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

                <div
                  className="
                    h-2
                    overflow-hidden
                    rounded-full
                    bg-gray-100
                  "
                >
                  <div
                    className={`
                      h-full
                      rounded-full
                      transition-all
                      duration-500
                      ${row.bar}
                    `}
                    style={{
                      width: `${Math.min(100, Math.max(0, row.percentage))}%`,
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
                {fmtNumber(overdue.length)} overdue
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

              <p
                className="
                  mt-1
                  max-w-xs
                  text-xs
                  leading-5
                  text-gray-400
                "
              >
                Your current payment portfolio has no outstanding overdue
                installments.
              </p>
            </div>
          ) : (
            <div
              className="
                mt-5
                max-h-[300px]
                space-y-1
                overflow-y-auto
                pr-1
              "
            >
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

                        <p
                          className="
                              truncate
                              text-sm
                              font-semibold
                              text-gray-800
                            "
                        >
                          Payment #{payment.id}
                        </p>
                      </div>

                      <p
                        className="
                            mt-1
                            pl-9
                            text-xs
                            text-gray-400
                          "
                      >
                        Due {fmtDate(payment.dueDate)} ·{" "}
                        <span className="font-medium text-red-500">
                          {fmtNumber(days)}d overdue
                        </span>
                      </p>
                    </div>

                    <div className="shrink-0 text-right">
                      <p className="text-sm font-bold text-gray-900">
                        {fmt(payment.amount, currency)}
                      </p>

                      {Number(payment.penalty ?? 0) > 0 && (
                        <p
                          className="
                              mt-0.5
                              text-[11px]
                              font-medium
                              text-orange-500
                            "
                        >
                          +{fmtDecimal(payment.penalty, currency)} penalty
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
          CREDIT QUALITY
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
        <div
          className="
            border-b
            border-gray-100
            px-6
            py-5
          "
        >
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <h2 className="text-sm font-semibold text-gray-900">
                Credit Quality
              </h2>

              <p className="mt-1 text-xs text-gray-400">
                Loan-level classification supplied by the backend LoanService.
              </p>
            </div>

            <span
              className="
                rounded-full
                bg-gray-100
                px-2.5
                py-1
                text-[10px]
                font-semibold
                uppercase
                tracking-wider
                text-gray-500
              "
            >
              Risk classification
            </span>
          </div>
        </div>

        <div
          className="
            grid
            grid-cols-1
            gap-3
            p-5
            sm:grid-cols-2
            lg:grid-cols-5
          "
        >
          <ClassificationBadge
            label="Current"
            count={classificationSummary.current}
            className="bg-emerald-500"
          />

          <ClassificationBadge
            label="Watch"
            count={classificationSummary.watch}
            className="bg-amber-400"
          />

          <ClassificationBadge
            label="Substandard"
            count={classificationSummary.substandard}
            className="bg-orange-500"
          />

          <ClassificationBadge
            label="Doubtful"
            count={classificationSummary.doubtful}
            className="bg-red-500"
          />

          <ClassificationBadge
            label="Written Off"
            count={classificationSummary.writtenOff}
            className="bg-gray-700"
          />
        </div>
      </section>

      {/* ======================================================
          ARREARS + COLLECTION STAGE
      ====================================================== */}

      <section
        className="
          grid
          grid-cols-1
          gap-5
          xl:grid-cols-2
        "
      >
        {/* ARREARS */}

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
          <div className="mb-5">
            <h2 className="text-sm font-semibold text-gray-900">
              Arrears Status
            </h2>

            <p className="mt-1 text-xs text-gray-400">
              Current overdue state supplied by the loan classification service.
            </p>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div
              className="
                rounded-xl
                border
                border-emerald-100
                bg-emerald-50
                p-4
              "
            >
              <p className="text-xs font-semibold uppercase tracking-wide text-emerald-600">
                Not Due
              </p>

              <p className="mt-2 text-2xl font-bold text-emerald-800">
                {fmtNumber(arrearsSummary.notDue)}
              </p>
            </div>

            <div
              className="
                rounded-xl
                border
                border-red-100
                bg-red-50
                p-4
              "
            >
              <p className="text-xs font-semibold uppercase tracking-wide text-red-600">
                Past Due
              </p>

              <p className="mt-2 text-2xl font-bold text-red-800">
                {fmtNumber(arrearsSummary.pastDue)}
              </p>
            </div>
          </div>
        </div>

        {/* COLLECTION STAGE */}

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
          <div className="mb-5">
            <h2 className="text-sm font-semibold text-gray-900">
              Collections Stage
            </h2>

            <p className="mt-1 text-xs text-gray-400">
              Loan-level collection stage from the backend classification.
            </p>
          </div>

          <div className="grid grid-cols-2 gap-3 sm:grid-cols-5">
            <div className="rounded-xl bg-gray-50 p-3">
              <p className="text-[10px] font-semibold uppercase text-gray-400">
                Normal
              </p>

              <p className="mt-1 text-lg font-bold text-gray-900">
                {fmtNumber(collectionStages.normal)}
              </p>
            </div>

            <div className="rounded-xl bg-amber-50 p-3">
              <p className="text-[10px] font-semibold uppercase text-amber-600">
                Reminder
              </p>

              <p className="mt-1 text-lg font-bold text-amber-800">
                {fmtNumber(collectionStages.reminder)}
              </p>
            </div>

            <div className="rounded-xl bg-orange-50 p-3">
              <p className="text-[10px] font-semibold uppercase text-orange-600">
                Collection
              </p>

              <p className="mt-1 text-lg font-bold text-orange-800">
                {fmtNumber(collectionStages.collection)}
              </p>
            </div>

            <div className="rounded-xl bg-red-50 p-3">
              <p className="text-[10px] font-semibold uppercase text-red-600">
                Legal
              </p>

              <p className="mt-1 text-lg font-bold text-red-800">
                {fmtNumber(collectionStages.legal)}
              </p>
            </div>

            <div className="rounded-xl bg-gray-900 p-3">
              <p className="text-[10px] font-semibold uppercase text-gray-400">
                Recovery
              </p>

              <p className="mt-1 text-lg font-bold text-white">
                {fmtNumber(collectionStages.recovery)}
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* ======================================================
          OVERDUE HEALTH
      ====================================================== */}

      <section
        className="
          rounded-2xl
          border
          border-gray-200
          bg-white
          p-6
          shadow-sm
        "
      >
        <div className="mb-5">
          <h2 className="text-sm font-semibold text-gray-900">
            Overdue Health
          </h2>

          <p className="mt-1 text-xs text-gray-400">
            Days-overdue indicators based on the current LoanService data.
          </p>
        </div>

        <div
          className="
            grid
            grid-cols-2
            gap-4
            md:grid-cols-4
          "
        >
          <div className="rounded-xl bg-gray-50 p-4">
            <p className="text-xs uppercase tracking-wide text-gray-400">
              Overdue Loans
            </p>

            <p className="mt-2 text-2xl font-bold text-gray-900">
              {fmtNumber(overdueDaysSummary.overdueLoans)}
            </p>
          </div>

          <div className="rounded-xl bg-gray-50 p-4">
            <p className="text-xs uppercase tracking-wide text-gray-400">
              Total Overdue Days
            </p>

            <p className="mt-2 text-2xl font-bold text-gray-900">
              {fmtNumber(overdueDaysSummary.totalDays)}
            </p>
          </div>

          <div className="rounded-xl bg-red-50 p-4">
            <p className="text-xs uppercase tracking-wide text-red-500">
              Maximum Days Overdue
            </p>

            <p className="mt-2 text-2xl font-bold text-red-800">
              {fmtNumber(overdueDaysSummary.maximumDays)}
            </p>
          </div>

          <div className="rounded-xl bg-amber-50 p-4">
            <p className="text-xs uppercase tracking-wide text-amber-600">
              Average Days Overdue
            </p>

            <p className="mt-2 text-2xl font-bold text-amber-800">
              {overdueDaysSummary.averageDays
                ? overdueDaysSummary.averageDays.toFixed(1)
                : "0.0"}
            </p>
          </div>
        </div>
      </section>

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
        <div
          className="
            border-b
            border-gray-100
            px-6
            py-5
          "
        >
          <h2 className="text-sm font-semibold text-gray-900">
            Portfolio Health
          </h2>

          <p className="mt-1 text-xs text-gray-400">
            Current operational portfolio indicators
          </p>
        </div>

        <div
          className="
            grid
            grid-cols-2
            divide-x
            divide-gray-100
            md:grid-cols-4
          "
        >
          <div className="px-5 py-6 text-center">
            <div
              className="
                mx-auto
                mb-3
                flex
                h-11
                w-11
                items-center
                justify-center
                rounded-xl
                bg-indigo-50
                text-lg
              "
            >
              👥
            </div>

            <p className="text-2xl font-bold tracking-tight text-gray-900">
              {fmtNumber(stats?.totalBorrowers)}
            </p>

            <p className="mt-1 text-xs font-medium text-gray-400">Borrowers</p>
          </div>

          <div className="px-5 py-6 text-center">
            <div
              className="
                mx-auto
                mb-3
                flex
                h-11
                w-11
                items-center
                justify-center
                rounded-xl
                bg-emerald-50
                text-lg
              "
            >
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
            <div
              className="
                mx-auto
                mb-3
                flex
                h-11
                w-11
                items-center
                justify-center
                rounded-xl
                bg-red-50
                text-lg
              "
            >
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
            <div
              className="
                mx-auto
                mb-3
                flex
                h-11
                w-11
                items-center
                justify-center
                rounded-xl
                bg-gray-100
                text-lg
              "
            >
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
          INFORMATION / DATA SOURCE
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
          Reports are generated using your organization&apos;s current portfolio
          data.
        </p>

        <p>Currency: {currency} · CSV &amp; Excel exports available</p>
      </div>
    </div>
  );
}
