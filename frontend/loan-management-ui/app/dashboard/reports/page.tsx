"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";

import API from "../../../services/api";
import { getDashboardStats } from "../../../services/dashboardService";
import { getOverduePayments } from "../../../services/paymentService";

import { PageSpinner } from "../../../components/ui/Skeleton";

/* ==========================================================================
   TYPES
   ========================================================================== */

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
  totalLoans?: Numeric;
  totalDisbursed?: Numeric;
  totalCollected?: Numeric;
  outstandingBalance?: Numeric;
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
    gender?: string;
    sex?: string;
  };

  amount?: Numeric;
  outstandingBalance?: Numeric;
  creditQuality?: string;
  daysOverdue?: Numeric;

  loanType?: string;
  productName?: string;
  product?: {
    name?: string;
  };
}

interface PaymentLike {
  penalty?: Numeric;
}

/* ==========================================================================
   HELPERS
   ========================================================================== */

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

const fmtPercent = (value: number): string => `${value.toFixed(1)}%`;

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

const getAllReportLoans = async (): Promise<LoanLike[]> => {
  const pageSize = 100;
  const maxPages = 100;
  const allLoans: LoanLike[] = [];

  for (let page = 0; page < maxPages; page += 1) {
    const response = await API.get("/loans", {
      params: {
        page,
        size: pageSize,
      },
    });

    const payload = unwrap<unknown>(response);

    let content: LoanLike[] = [];
    let last = false;

    if (Array.isArray(payload)) {
      content = payload as LoanLike[];
      last = content.length < pageSize;
    } else if (payload && typeof payload === "object") {
      const pageData = payload as {
        content?: unknown;
        last?: unknown;
        totalPages?: unknown;
      };

      content = normalizeArray<LoanLike>(pageData.content);
      last =
        pageData.last === true ||
        (typeof pageData.totalPages === "number" &&
          page + 1 >= pageData.totalPages) ||
        content.length < pageSize;
    }

    allLoans.push(...content);

    if (last || content.length === 0) {
      break;
    }
  }

  return allLoans;
};

const filenamePart = (value: string): string =>
  value
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");

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

/* ==========================================================================
   ICONS
   ========================================================================== */

function Icon({
  name,
  size = 18,
}: {
  name:
    | "download"
    | "chevron"
    | "arrow"
    | "document"
    | "ledger"
    | "balance"
    | "profit"
    | "cash"
    | "portfolio"
    | "users"
    | "risk"
    | "calendar";
  size?: number;
}) {
  const common = {
    width: size,
    height: size,
    viewBox: "0 0 24 24",
    fill: "none",
    stroke: "currentColor",
    strokeWidth: 1.7,
    strokeLinecap: "round" as const,
    strokeLinejoin: "round" as const,
    "aria-hidden": true,
  };

  switch (name) {
    case "download":
      return (
        <svg {...common}>
          <path d="M12 3v12" />
          <path d="m7 10 5 5 5-5" />
          <path d="M4 21h16" />
        </svg>
      );

    case "chevron":
      return (
        <svg {...common}>
          <path d="m9 18 6-6-6-6" />
        </svg>
      );

    case "arrow":
      return (
        <svg {...common}>
          <path d="M5 12h14" />
          <path d="m13 6 6 6-6 6" />
        </svg>
      );

    case "document":
      return (
        <svg {...common}>
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
          <path d="M14 2v6h6" />
          <path d="M8 13h8" />
          <path d="M8 17h6" />
        </svg>
      );

    case "ledger":
      return (
        <svg {...common}>
          <path d="M4 5a2 2 0 0 1 2-2h14v18H6a2 2 0 0 1-2-2z" />
          <path d="M8 7h8" />
          <path d="M8 11h8" />
          <path d="M8 15h5" />
        </svg>
      );

    case "balance":
      return (
        <svg {...common}>
          <path d="M12 3v18" />
          <path d="M5 7h14" />
          <path d="M7 7 4 13h6z" />
          <path d="m17 7-3 6h6z" />
        </svg>
      );

    case "profit":
      return (
        <svg {...common}>
          <path d="M4 19V5" />
          <path d="M4 19h16" />
          <path d="m7 15 4-4 3 2 5-6" />
        </svg>
      );

    case "cash":
      return (
        <svg {...common}>
          <rect x="3" y="5" width="18" height="14" rx="2" />
          <circle cx="12" cy="12" r="3" />
          <path d="M7 9h.01M17 15h.01" />
        </svg>
      );

    case "portfolio":
      return (
        <svg {...common}>
          <path d="M4 7h16v13H4z" />
          <path d="M8 7V5a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
          <path d="M4 11h16" />
        </svg>
      );

    case "users":
      return (
        <svg {...common}>
          <circle cx="9" cy="8" r="3" />
          <path d="M3 20c0-3.3 2.7-6 6-6s6 2.7 6 6" />
          <path d="M16 5.5a3 3 0 0 1 0 5.8" />
          <path d="M18 14c1.8.8 3 2.7 3 6" />
        </svg>
      );

    case "risk":
      return (
        <svg {...common}>
          <path d="M12 3 4 6v5c0 5 3.2 8.7 8 10 4.8-1.3 8-5 8-10V6z" />
          <path d="M12 8v5" />
          <path d="M12 16h.01" />
        </svg>
      );

    case "calendar":
      return (
        <svg {...common}>
          <rect x="3" y="5" width="18" height="16" rx="2" />
          <path d="M16 3v4M8 3v4M3 10h18" />
        </svg>
      );
  }
}

/* ==========================================================================
   EXPORT BUTTON
   ========================================================================== */

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
    if (loading !== null) return;

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
    <div className="flex items-center gap-2">
      <button
        type="button"
        disabled={loading !== null}
        onClick={() => void exportReport("csv")}
        className="inline-flex h-9 items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3 text-[11px] font-semibold text-slate-600 transition hover:border-slate-300 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
      >
        <Icon name="download" size={13} />
        {loading === "csv" ? "Preparing" : "CSV"}
      </button>

      <button
        type="button"
        disabled={loading !== null}
        onClick={() => void exportReport("excel")}
        className="inline-flex h-9 items-center gap-1.5 rounded-lg border border-slate-300 bg-slate-900 px-3 text-[11px] font-semibold text-white transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-50"
      >
        <Icon name="download" size={13} />
        {loading === "excel" ? "Preparing" : "Excel"}
      </button>
    </div>
  );
}

/* ==========================================================================
   SECTION TITLE
   ========================================================================== */

function SectionHeading({
  eyebrow,
  title,
  description,
}: {
  eyebrow?: string;
  title: string;
  description?: string;
}) {
  return (
    <div className="mb-5">
      {eyebrow ? (
        <p className="mb-1 text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400">
          {eyebrow}
        </p>
      ) : null}

      <h2 className="text-base font-bold tracking-tight text-slate-900">
        {title}
      </h2>

      {description ? (
        <p className="mt-1 text-xs leading-5 text-slate-500">{description}</p>
      ) : null}
    </div>
  );
}

/* ==========================================================================
   EXECUTIVE METRIC
   ========================================================================== */

function ExecutiveMetric({
  label,
  value,
  detail,
  emphasis = false,
}: {
  label: string;
  value: string;
  detail?: string;
  emphasis?: boolean;
}) {
  return (
    <div className="min-w-0 px-5 py-5">
      <p className="text-[10px] font-bold uppercase tracking-[0.13em] text-slate-400">
        {label}
      </p>

      <p
        className={`mt-2 truncate text-xl font-bold tracking-tight ${
          emphasis ? "text-slate-950" : "text-slate-800"
        }`}
      >
        {value}
      </p>

      {detail ? (
        <p className="mt-1 truncate text-[11px] text-slate-400">{detail}</p>
      ) : null}
    </div>
  );
}

/* ==========================================================================
   STATEMENT TABLE
   ========================================================================== */

function StatementTable({
  title,
  rows,
  total,
}: {
  title: string;
  rows?: AccountingAccountRow[];
  total: Numeric;
}) {
  return (
    <div className="overflow-hidden rounded-xl border border-slate-200 bg-white">
      <div className="border-b border-slate-200 bg-slate-50 px-4 py-3">
        <h3 className="text-xs font-bold uppercase tracking-[0.12em] text-slate-600">
          {title}
        </h3>
      </div>

      <table className="min-w-full">
        <tbody className="divide-y divide-slate-100">
          {rows && rows.length > 0 ? (
            rows.map((row, index) => {
              const value =
                row.balance ?? row.amount ?? row.credit ?? row.debit ?? 0;

              return (
                <tr
                  key={`${row.code ?? row.name ?? "row"}-${index}`}
                  className="hover:bg-slate-50/70"
                >
                  <td className="w-24 px-4 py-3 text-[11px] text-slate-400">
                    {row.code ?? "—"}
                  </td>

                  <td className="px-4 py-3 text-xs font-medium text-slate-700">
                    {row.name ?? "Unnamed Account"}
                  </td>

                  <td className="whitespace-nowrap px-4 py-3 text-right text-xs font-semibold text-slate-800">
                    {fmtPrecise(value)}
                  </td>
                </tr>
              );
            })
          ) : (
            <tr>
              <td
                colSpan={3}
                className="px-4 py-8 text-center text-xs text-slate-400"
              >
                No accounts reported.
              </td>
            </tr>
          )}

          <tr className="bg-slate-50">
            <td />
            <td className="px-4 py-3 text-xs font-bold uppercase tracking-wide text-slate-700">
              Total
            </td>
            <td className="px-4 py-3 text-right text-xs font-bold text-slate-950">
              {fmtPrecise(total)}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  );
}

/* ==========================================================================
   REPORT DOCUMENT
   ========================================================================== */

function ReportDocument({
  icon,
  title,
  description,
  endpoint,
}: {
  icon: Parameters<typeof Icon>[0]["name"];
  title: string;
  description: string;
  endpoint: string;
}) {
  return (
    <div className="flex flex-col justify-between rounded-xl border border-slate-200 bg-white p-5 transition hover:border-slate-300 hover:shadow-sm">
      <div>
        <div className="flex items-start justify-between gap-4">
          <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-slate-100 text-slate-700">
            <Icon name={icon} size={17} />
          </div>

          <Icon name="chevron" size={15} />
        </div>

        <h3 className="mt-4 text-sm font-bold text-slate-900">{title}</h3>

        <p className="mt-1 text-xs leading-5 text-slate-500">{description}</p>
      </div>

      <div className="mt-5 border-t border-slate-100 pt-4">
        <ExportButtons endpoint={endpoint} label={title} accounting />
      </div>
    </div>
  );
}

/* ==========================================================================
   PAGE
   ========================================================================== */

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
            getAllReportLoans(),
          ]);

        if (!mounted) return;

        if (dashboardStatsResult.status === "fulfilled") {
          setStats(
            unwrap<DashboardStatsLike>(dashboardStatsResult.value) ?? null,
          );
        } else {
          console.error(
            "Dashboard statistics failed",
            dashboardStatsResult.reason,
          );
          setStats(null);
        }

        if (overduePaymentsResult.status === "fulfilled") {
          setOverdue(
            normalizeArray<PaymentLike>(
              unwrap<unknown>(overduePaymentsResult.value),
            ),
          );
        } else {
          console.error(
            "Overdue payments failed",
            overduePaymentsResult.reason,
          );
          setOverdue([]);
        }

        if (loanListResult.status === "fulfilled") {
          setLoans(
            normalizeArray<LoanLike>(unwrap<unknown>(loanListResult.value)),
          );
        } else {
          console.error("Loan portfolio failed", loanListResult.reason);
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

        if (!mounted) return;

        const trial = results[0];
        const balance = results[1];
        const pnl = results[2];
        const cash = results[3];

        if (trial.status === "fulfilled") {
          setTrialBalance(unwrap<TrialBalanceReport>(trial.value));
        } else {
          console.error("Trial balance failed", trial.reason);
          setTrialBalance(null);
        }

        if (balance.status === "fulfilled") {
          setBalanceSheet(unwrap<BalanceSheetReport>(balance.value));
        } else {
          console.error("Balance sheet failed", balance.reason);
          setBalanceSheet(null);
        }

        if (pnl.status === "fulfilled") {
          setProfitAndLoss(unwrap<ProfitAndLossReport>(pnl.value));
        } else {
          console.error("Profit and loss failed", pnl.reason);
          setProfitAndLoss(null);
        }

        if (cash.status === "fulfilled") {
          setCashFlow(unwrap<CashFlowReport>(cash.value));
        } else {
          console.error("Cash flow failed", cash.reason);
          setCashFlow(null);
        }

        const accountingSucceeded = results.some(
          (result) => result.status === "fulfilled",
        );

        if (accountingSucceeded) {
          setAccountingError(null);
        } else {
          setAccountingError("Accounting reports could not be loaded.");
        }

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

        if (!mounted) return;

        setMonthlyAccounting(
          monthlyResults.map((result, index) => {
            const period = periods[index];

            if (result.status === "fulfilled") {
              return result.value;
            }

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
          }),
        );
      } catch (error) {
        console.error("Accounting reports failed", error);

        if (mounted) {
          setAccountingError("Accounting reports could not be loaded.");

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

  /* ==========================================================================
     DERIVED VALUES
     ========================================================================== */

  const today = new Date();

  const asOfDate = toDateString(today);

  const collectionRate = useMemo(() => {
    const disbursed = numberValue(stats?.totalDisbursed);

    const collected = numberValue(stats?.totalCollected);

    if (disbursed <= 0) return 0;

    return (collected / disbursed) * 100;
  }, [stats]);

  const outstandingPortfolio = useMemo(() => {
    /*
     * Outstanding portfolio is an operational receivable balance, not
     * "gross disbursements minus all collections". Collections can contain
     * principal, interest, management fees, penalties and one-time fees, so
     * subtracting the entire collection total from principal disbursements
     * produces a false figure (for example RF 242,025,800 in the previous
     * report).
     *
     * Use the authoritative DashboardStats outstandingBalance, which is now
     * aligned with the Loan Portfolio, BNR portfolio population and GL 1100.
     */
    if (
      stats?.outstandingBalance !== undefined &&
      stats?.outstandingBalance !== null
    ) {
      return numberValue(stats.outstandingBalance);
    }

    return loans.reduce(
      (sum, loan) => sum + numberValue(loan.outstandingBalance),
      0,
    );
  }, [stats, loans]);

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
    (loan) => String(loan.status ?? "").toUpperCase() === "REJECTED",
  ).length;

  const portfolioCount =
    stats?.totalLoans !== undefined && stats?.totalLoans !== null
      ? numberValue(stats.totalLoans)
      : loans.length;

  const currentLoans = loans.filter(
    (loan) => String(loan.status ?? "").toUpperCase() === "ACTIVE",
  );

  const qualityBreakdown = useMemo(() => {
    const groups = new Map<
      string,
      {
        count: number;
        amount: number;
      }
    >();

    currentLoans.forEach((loan) => {
      const quality = loan.creditQuality?.trim() || "Not Classified";

      const existing = groups.get(quality) ?? {
        count: 0,
        amount: 0,
      };

      existing.count += 1;
      existing.amount += numberValue(loan.outstandingBalance ?? loan.amount);

      groups.set(quality, existing);
    });

    return Array.from(groups.entries())
      .map(([quality, value]) => ({
        quality,
        ...value,
      }))
      .sort((a, b) => b.amount - a.amount);
  }, [currentLoans]);

  const borrowerGender = useMemo(() => {
    let male = 0;
    let female = 0;
    let unknown = 0;

    const seen = new Set<string>();

    loans.forEach((loan) => {
      const borrower = loan.borrower;

      if (!borrower) return;

      const key =
        borrower.nationalId ?? `${borrower.firstName}-${borrower.lastName}`;

      if (seen.has(key)) return;

      seen.add(key);

      const gender = String(borrower.gender ?? borrower.sex ?? "")
        .trim()
        .toUpperCase();

      if (gender === "M" || gender === "MALE") {
        male += 1;
      } else if (gender === "F" || gender === "FEMALE") {
        female += 1;
      } else {
        unknown += 1;
      }
    });

    const total = male + female + unknown;

    return {
      male,
      female,
      unknown,
      total,
    };
  }, [loans]);

  const loanProducts = useMemo(() => {
    const groups = new Map<
      string,
      {
        count: number;
        amount: number;
      }
    >();

    loans.forEach((loan) => {
      const product =
        loan.productName ??
        loan.loanType ??
        loan.product?.name ??
        "Unclassified";

      const existing = groups.get(product) ?? {
        count: 0,
        amount: 0,
      };

      existing.count += 1;
      existing.amount += numberValue(loan.amount);

      groups.set(product, existing);
    });

    const total = Array.from(groups.values()).reduce(
      (sum, item) => sum + item.amount,
      0,
    );

    return Array.from(groups.entries())
      .map(([name, value]) => ({
        name,
        ...value,
        percentage: total > 0 ? (value.amount / total) * 100 : 0,
      }))
      .sort((a, b) => b.amount - a.amount);
  }, [loans]);

  const monthlyMax = Math.max(
    1,
    ...monthlyAccounting.flatMap((row) => [
      Math.abs(row.revenue),
      Math.abs(row.expenses),
      Math.abs(row.profit),
    ]),
  );

  const reportingFrom =
    profitAndLoss?.from ?? monthlyAccounting[0]?.from ?? asOfDate;

  const reportingTo =
    profitAndLoss?.to ??
    monthlyAccounting[monthlyAccounting.length - 1]?.to ??
    asOfDate;

  const hasAccountingReports = Boolean(
    trialBalance || balanceSheet || profitAndLoss || cashFlow,
  );

  /* ==========================================================================
     LOADING
     ========================================================================== */

  if (loading) {
    return <PageSpinner />;
  }

  /* ==========================================================================
     RENDER
     ========================================================================== */

  return (
    <main className="min-h-full bg-[#f7f8fa] pb-16 text-slate-900 print:bg-white">
      <div className="mx-auto max-w-[1600px] px-4 py-5 sm:px-6 lg:px-8">
        {/* ==================================================================
           REPORT HEADER
           ================================================================== */}

        <header className="border-b border-slate-300 pb-6">
          <div className="flex flex-col gap-6 lg:flex-row lg:items-start lg:justify-between">
            <div>
              <div className="flex items-center gap-3">
                <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-slate-900 text-white">
                  <span className="text-sm font-black">NL</span>
                </div>

                <div>
                  <p className="text-xs font-bold uppercase tracking-[0.18em] text-slate-500">
                    Noble Loan
                  </p>

                  <p className="text-[10px] uppercase tracking-[0.14em] text-slate-400">
                    Management Information System
                  </p>
                </div>
              </div>

              <h1 className="mt-7 text-3xl font-bold tracking-tight text-slate-950">
                Management &amp; Financial Report
              </h1>

              <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-500">
                Executive overview of lending operations, portfolio performance,
                borrower activity and financial position.
              </p>
            </div>

            <div className="min-w-[250px] border-l border-slate-200 pl-5 lg:text-right">
              <p className="text-[10px] font-bold uppercase tracking-[0.15em] text-slate-400">
                Reporting date
              </p>

              <p className="mt-1 text-sm font-bold text-slate-900">
                {fmtDate(asOfDate)}
              </p>

              <p className="mt-4 text-[10px] font-bold uppercase tracking-[0.15em] text-slate-400">
                Reporting period
              </p>

              <p className="mt-1 text-sm font-semibold text-slate-700">
                {fmtDate(reportingFrom)} — {fmtDate(reportingTo)}
              </p>
            </div>
          </div>

          <div className="mt-6 flex flex-col gap-3 border-t border-slate-200 pt-4 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex items-center gap-2 text-xs text-slate-500">
              <Icon name="calendar" size={14} />
              <span>Prepared from operational and accounting records</span>
            </div>

            <Link
              href="/dashboard/reports/regulatory"
              className="inline-flex items-center gap-2 text-xs font-bold text-slate-700 transition hover:text-slate-950"
            >
              Regulatory reporting
              <Icon name="arrow" size={14} />
            </Link>
          </div>
        </header>

        {/* ==================================================================
           WARNING
           ================================================================== */}

        {accountingError ? (
          <div className="mt-5 border border-amber-200 bg-amber-50 px-5 py-4">
            <p className="text-xs font-bold uppercase tracking-wide text-amber-900">
              Accounting data unavailable
            </p>

            <p className="mt-1 text-xs text-amber-700">{accountingError}</p>
          </div>
        ) : null}

        {/* ==================================================================
           EXECUTIVE SUMMARY
           ================================================================== */}

        <section className="mt-8">
          <SectionHeading
            eyebrow="01 / Executive Summary"
            title="Portfolio at a glance"
            description="Key indicators for management review."
          />

          <div className="overflow-hidden rounded-xl border border-slate-200 bg-white">
            <div className="grid divide-y divide-slate-200 sm:grid-cols-2 sm:divide-y-0 lg:grid-cols-4 lg:divide-x">
              <ExecutiveMetric
                label="Gross Disbursements"
                value={fmt(stats?.totalDisbursed)}
                detail={`${fmtNumber(portfolioCount)} recorded loans`}
                emphasis
              />

              <ExecutiveMetric
                label="Collections"
                value={fmt(stats?.totalCollected)}
                detail={`${fmtPercent(collectionRate)} collection rate`}
              />

              <ExecutiveMetric
                label="Outstanding Portfolio"
                value={fmt(outstandingPortfolio)}
                detail="Estimated remaining exposure"
                emphasis
              />

              <ExecutiveMetric
                label="Overdue Penalties"
                value={fmt(penalties)}
                detail={`${fmtNumber(stats?.overdueLoans)} overdue loans`}
              />
            </div>
          </div>

          <div className="mt-4 overflow-hidden rounded-xl border border-slate-200 bg-white">
            <div className="grid divide-y divide-slate-200 sm:grid-cols-2 sm:divide-y-0 lg:grid-cols-4 lg:divide-x">
              <ExecutiveMetric
                label="Borrowers"
                value={fmtNumber(stats?.totalBorrowers ?? borrowerGender.total)}
                detail="Customer base"
              />

              <ExecutiveMetric
                label="Active Loans"
                value={fmtNumber(stats?.activeLoans)}
                detail="Currently performing"
              />

              <ExecutiveMetric
                label="Overdue Loans"
                value={fmtNumber(stats?.overdueLoans)}
                detail="Require collection attention"
              />

              <ExecutiveMetric
                label="Closed Loans"
                value={fmtNumber(stats?.completedLoans)}
                detail="Completed facilities"
              />
            </div>
          </div>
        </section>

        {/* ==================================================================
           PORTFOLIO COMPOSITION
           ================================================================== */}

        <section className="mt-10">
          <SectionHeading
            eyebrow="02 / Portfolio Analysis"
            title="Portfolio composition"
            description="Distribution of loan exposure by product or loan type."
          />

          <div className="overflow-hidden rounded-xl border border-slate-200 bg-white">
            <div className="overflow-x-auto">
              <table className="min-w-full">
                <thead className="border-b border-slate-200 bg-slate-50">
                  <tr>
                    <th className="px-5 py-3 text-left text-[10px] font-bold uppercase tracking-[0.12em] text-slate-500">
                      Loan product
                    </th>

                    <th className="px-5 py-3 text-right text-[10px] font-bold uppercase tracking-[0.12em] text-slate-500">
                      Facilities
                    </th>

                    <th className="px-5 py-3 text-right text-[10px] font-bold uppercase tracking-[0.12em] text-slate-500">
                      Amount
                    </th>

                    <th className="px-5 py-3 text-right text-[10px] font-bold uppercase tracking-[0.12em] text-slate-500">
                      Portfolio %
                    </th>
                  </tr>
                </thead>

                <tbody className="divide-y divide-slate-100">
                  {loanProducts.length > 0 ? (
                    loanProducts.map((product) => (
                      <tr key={product.name} className="hover:bg-slate-50">
                        <td className="px-5 py-3.5 text-xs font-semibold text-slate-800">
                          {product.name}
                        </td>

                        <td className="px-5 py-3.5 text-right text-xs text-slate-600">
                          {fmtNumber(product.count)}
                        </td>

                        <td className="px-5 py-3.5 text-right text-xs font-semibold text-slate-800">
                          {fmt(product.amount)}
                        </td>

                        <td className="px-5 py-3.5 text-right text-xs font-semibold text-slate-700">
                          {fmtPercent(product.percentage)}
                        </td>
                      </tr>
                    ))
                  ) : (
                    <tr>
                      <td
                        colSpan={4}
                        className="px-5 py-10 text-center text-xs text-slate-400"
                      >
                        No portfolio composition data available.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </section>

        {/* ==================================================================
           BORROWER + RISK
           ================================================================== */}

        <section className="mt-10">
          <div className="grid gap-6 lg:grid-cols-2">
            <div>
              <SectionHeading
                eyebrow="03 / Borrower Profile"
                title="Borrower composition"
                description="Borrower distribution based on available customer records."
              />

              <div className="overflow-hidden rounded-xl border border-slate-200 bg-white">
                <table className="min-w-full">
                  <thead className="border-b border-slate-200 bg-slate-50">
                    <tr>
                      <th className="px-5 py-3 text-left text-[10px] font-bold uppercase tracking-[0.12em] text-slate-500">
                        Category
                      </th>

                      <th className="px-5 py-3 text-right text-[10px] font-bold uppercase tracking-[0.12em] text-slate-500">
                        Borrowers
                      </th>

                      <th className="px-5 py-3 text-right text-[10px] font-bold uppercase tracking-[0.12em] text-slate-500">
                        %
                      </th>
                    </tr>
                  </thead>

                  <tbody className="divide-y divide-slate-100">
                    {[
                      {
                        label: "Male",
                        value: borrowerGender.male,
                      },
                      {
                        label: "Female",
                        value: borrowerGender.female,
                      },
                      {
                        label: "Not specified",
                        value: borrowerGender.unknown,
                      },
                    ].map((row) => {
                      const percentage =
                        borrowerGender.total > 0
                          ? (row.value / borrowerGender.total) * 100
                          : 0;

                      return (
                        <tr key={row.label}>
                          <td className="px-5 py-4 text-xs font-semibold text-slate-700">
                            {row.label}
                          </td>

                          <td className="px-5 py-4 text-right text-xs text-slate-600">
                            {fmtNumber(row.value)}
                          </td>

                          <td className="px-5 py-4 text-right text-xs font-semibold text-slate-800">
                            {fmtPercent(percentage)}
                          </td>
                        </tr>
                      );
                    })}

                    <tr className="bg-slate-50">
                      <td className="px-5 py-4 text-xs font-bold uppercase tracking-wide text-slate-700">
                        Total
                      </td>

                      <td className="px-5 py-4 text-right text-xs font-bold text-slate-900">
                        {fmtNumber(borrowerGender.total)}
                      </td>

                      <td className="px-5 py-4 text-right text-xs font-bold text-slate-900">
                        100.0%
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>

            <div>
              <SectionHeading
                eyebrow="04 / Credit Risk"
                title="Portfolio quality"
                description="Outstanding exposure by credit-quality classification."
              />

              <div className="overflow-hidden rounded-xl border border-slate-200 bg-white">
                <table className="min-w-full">
                  <thead className="border-b border-slate-200 bg-slate-50">
                    <tr>
                      <th className="px-5 py-3 text-left text-[10px] font-bold uppercase tracking-[0.12em] text-slate-500">
                        Classification
                      </th>

                      <th className="px-5 py-3 text-right text-[10px] font-bold uppercase tracking-[0.12em] text-slate-500">
                        Loans
                      </th>

                      <th className="px-5 py-3 text-right text-[10px] font-bold uppercase tracking-[0.12em] text-slate-500">
                        Exposure
                      </th>
                    </tr>
                  </thead>

                  <tbody className="divide-y divide-slate-100">
                    {qualityBreakdown.length > 0 ? (
                      qualityBreakdown.map((row) => (
                        <tr key={row.quality}>
                          <td className="px-5 py-4 text-xs font-semibold text-slate-700">
                            {row.quality}
                          </td>

                          <td className="px-5 py-4 text-right text-xs text-slate-600">
                            {fmtNumber(row.count)}
                          </td>

                          <td className="px-5 py-4 text-right text-xs font-semibold text-slate-800">
                            {fmt(row.amount)}
                          </td>
                        </tr>
                      ))
                    ) : (
                      <tr>
                        <td
                          colSpan={3}
                          className="px-5 py-10 text-center text-xs text-slate-400"
                        >
                          No credit-quality classifications available.
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </section>

        {/* ==================================================================
           FINANCIAL PERFORMANCE
           ================================================================== */}

        <section className="mt-10">
          <SectionHeading
            eyebrow="05 / Financial Performance"
            title="Income statement summary"
            description="Financial performance derived from the accounting records."
          />

          <div className="overflow-hidden rounded-xl border border-slate-200 bg-white">
            <div className="grid divide-y divide-slate-200 lg:grid-cols-3 lg:divide-x lg:divide-y-0">
              <div className="p-6">
                <p className="text-[10px] font-bold uppercase tracking-[0.14em] text-slate-400">
                  Total income
                </p>

                <p className="mt-3 text-2xl font-bold tracking-tight text-slate-950">
                  {fmt(totalIncome)}
                </p>

                <p className="mt-2 text-xs text-slate-500">
                  Revenue recognized during the reporting period.
                </p>
              </div>

              <div className="p-6">
                <p className="text-[10px] font-bold uppercase tracking-[0.14em] text-slate-400">
                  Total expenses
                </p>

                <p className="mt-3 text-2xl font-bold tracking-tight text-slate-950">
                  {fmt(totalExpenses)}
                </p>

                <p className="mt-2 text-xs text-slate-500">
                  Expenses recognized during the reporting period.
                </p>
              </div>

              <div className="bg-slate-950 p-6 text-white">
                <p className="text-[10px] font-bold uppercase tracking-[0.14em] text-slate-400">
                  Net income
                </p>

                <p className="mt-3 text-2xl font-bold tracking-tight">
                  {fmt(netIncome)}
                </p>

                <p className="mt-2 text-xs text-slate-300">
                  {netIncome >= 0
                    ? "Positive earnings for the reporting period."
                    : "Negative earnings for the reporting period."}
                </p>
              </div>
            </div>

            {profitAndLoss ? (
              <details className="border-t border-slate-200">
                <summary className="cursor-pointer px-6 py-4 text-xs font-bold text-slate-700 hover:bg-slate-50">
                  View income and expense accounts
                </summary>

                <div className="grid gap-6 border-t border-slate-100 bg-slate-50/50 p-6 xl:grid-cols-2">
                  <StatementTable
                    title="Income"
                    rows={profitAndLoss.income}
                    total={profitAndLoss.totalIncome}
                  />

                  <StatementTable
                    title="Expenses"
                    rows={profitAndLoss.expense}
                    total={
                      profitAndLoss.totalExpense ?? profitAndLoss.totalExpenses
                    }
                  />
                </div>
              </details>
            ) : null}
          </div>
        </section>

        {/* ==================================================================
           STATEMENT OF FINANCIAL POSITION
           ================================================================== */}

        <section className="mt-10">
          <SectionHeading
            eyebrow="06 / Financial Position"
            title="Statement of financial position"
            description="Assets, liabilities and equity at the reporting date."
          />

          <div className="grid gap-6 xl:grid-cols-3">
            <StatementTable
              title="Assets"
              rows={balanceSheet?.assets}
              total={balanceSheet?.totalAssets}
            />

            <StatementTable
              title="Liabilities"
              rows={balanceSheet?.liabilities}
              total={balanceSheet?.totalLiabilities}
            />

            <StatementTable
              title="Equity"
              rows={balanceSheet?.equity}
              total={balanceSheet?.totalEquity}
            />
          </div>

          <div className="mt-4 overflow-hidden rounded-xl border border-slate-200 bg-white">
            <div className="grid divide-y divide-slate-200 sm:grid-cols-3 sm:divide-y-0 sm:divide-x">
              <ExecutiveMetric label="Total assets" value={fmt(totalAssets)} />

              <ExecutiveMetric
                label="Total liabilities"
                value={fmt(totalLiabilities)}
              />

              <ExecutiveMetric
                label="Total equity"
                value={fmt(totalEquity)}
                emphasis
              />
            </div>
          </div>
        </section>

        {/* ==================================================================
           CASH FLOW
           ================================================================== */}

        <section className="mt-10">
          <SectionHeading
            eyebrow="07 / Liquidity"
            title="Cash flow summary"
            description="Summary of cash movements recorded by the accounting system."
          />

          <div className="overflow-hidden rounded-xl border border-slate-200 bg-white">
            <table className="min-w-full">
              <tbody className="divide-y divide-slate-100">
                <tr>
                  <td className="px-5 py-4 text-xs font-semibold text-slate-700">
                    Cash used for lending
                  </td>

                  <td className="px-5 py-4 text-right text-xs font-semibold text-slate-800">
                    {fmt(cashFlow?.cashUsedForLending)}
                  </td>
                </tr>

                <tr>
                  <td className="px-5 py-4 text-xs font-semibold text-slate-700">
                    Cash from collections
                  </td>

                  <td className="px-5 py-4 text-right text-xs font-semibold text-slate-800">
                    {fmt(cashFlow?.cashFromCollections)}
                  </td>
                </tr>

                <tr>
                  <td className="px-5 py-4 text-xs font-semibold text-slate-700">
                    Cash from fees
                  </td>

                  <td className="px-5 py-4 text-right text-xs font-semibold text-slate-800">
                    {fmt(cashFlow?.cashFromFees)}
                  </td>
                </tr>

                <tr>
                  <td className="px-5 py-4 text-xs font-semibold text-slate-700">
                    Other cash movements
                  </td>

                  <td className="px-5 py-4 text-right text-xs font-semibold text-slate-800">
                    {fmt(cashFlow?.otherCashMovement)}
                  </td>
                </tr>

                <tr className="bg-slate-50">
                  <td className="px-5 py-4 text-xs font-bold uppercase tracking-wide text-slate-700">
                    Net change in cash
                  </td>

                  <td
                    className={`px-5 py-4 text-right text-sm font-bold ${
                      netCashChange >= 0 ? "text-slate-900" : "text-red-700"
                    }`}
                  >
                    {fmt(netCashChange)}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        {/* ==================================================================
           MONTHLY PERFORMANCE
           ================================================================== */}

        <section className="mt-10">
          <SectionHeading
            eyebrow="08 / Trend Analysis"
            title="Six-month financial trend"
            description="Monthly income, expenses and net income."
          />

          <div className="overflow-hidden rounded-xl border border-slate-200 bg-white">
            <div className="overflow-x-auto">
              <div className="min-w-[900px]">
                <div className="grid grid-cols-[130px_1fr_150px_150px_150px] border-b border-slate-200 bg-slate-50 px-5 py-3">
                  <span className="text-[10px] font-bold uppercase tracking-wide text-slate-500">
                    Period
                  </span>

                  <span className="text-[10px] font-bold uppercase tracking-wide text-slate-500">
                    Relative trend
                  </span>

                  <span className="text-right text-[10px] font-bold uppercase tracking-wide text-slate-500">
                    Income
                  </span>

                  <span className="text-right text-[10px] font-bold uppercase tracking-wide text-slate-500">
                    Expenses
                  </span>

                  <span className="text-right text-[10px] font-bold uppercase tracking-wide text-slate-500">
                    Net income
                  </span>
                </div>

                {monthlyAccounting.length > 0 ? (
                  monthlyAccounting.map((row) => {
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
                        className="grid grid-cols-[130px_1fr_150px_150px_150px] items-center border-b border-slate-100 px-5 py-4 last:border-0"
                      >
                        <div>
                          <p className="text-xs font-bold text-slate-800">
                            {row.label}
                          </p>

                          <p className="mt-0.5 text-[9px] text-slate-400">
                            {row.from}
                          </p>
                        </div>

                        <div className="space-y-2 pr-8">
                          <div className="flex items-center gap-2">
                            <span className="w-3 text-[8px] font-bold text-slate-400">
                              I
                            </span>

                            <div className="h-1.5 flex-1 rounded-full bg-slate-100">
                              <div
                                className="h-full rounded-full bg-slate-700"
                                style={{
                                  width: `${revenueWidth}%`,
                                }}
                              />
                            </div>
                          </div>

                          <div className="flex items-center gap-2">
                            <span className="w-3 text-[8px] font-bold text-slate-400">
                              E
                            </span>

                            <div className="h-1.5 flex-1 rounded-full bg-slate-100">
                              <div
                                className="h-full rounded-full bg-slate-400"
                                style={{
                                  width: `${expenseWidth}%`,
                                }}
                              />
                            </div>
                          </div>

                          <div className="flex items-center gap-2">
                            <span className="w-3 text-[8px] font-bold text-slate-400">
                              P
                            </span>

                            <div className="h-1.5 flex-1 rounded-full bg-slate-100">
                              <div
                                className={`h-full rounded-full ${
                                  row.profit >= 0
                                    ? "bg-slate-950"
                                    : "bg-red-600"
                                }`}
                                style={{
                                  width: `${profitWidth}%`,
                                }}
                              />
                            </div>
                          </div>
                        </div>

                        <p className="text-right text-xs font-semibold text-slate-700">
                          {fmt(row.revenue)}
                        </p>

                        <p className="text-right text-xs font-semibold text-slate-600">
                          {fmt(row.expenses)}
                        </p>

                        <p
                          className={`text-right text-xs font-bold ${
                            row.profit >= 0 ? "text-slate-950" : "text-red-700"
                          }`}
                        >
                          {fmt(row.profit)}
                        </p>
                      </div>
                    );
                  })
                ) : (
                  <div className="px-5 py-10 text-center text-xs text-slate-400">
                    No monthly financial trend data available.
                  </div>
                )}
              </div>
            </div>
          </div>
        </section>

        {/* ==================================================================
           ACCOUNTING CONTROL
           ================================================================== */}

        {hasAccountingReports ? (
          <section className="mt-10">
            <SectionHeading
              eyebrow="09 / Accounting Control"
              title="Accounting integrity"
              description="Internal control indicators for management and finance users."
            />

            <div className="overflow-hidden rounded-xl border border-slate-200 bg-white">
              <div className="grid divide-y divide-slate-200 sm:grid-cols-2 lg:grid-cols-4 lg:divide-y-0 lg:divide-x">
                <ExecutiveMetric
                  label="Total debits"
                  value={fmtPrecise(totalDebit)}
                  detail="Trial balance"
                />

                <ExecutiveMetric
                  label="Total credits"
                  value={fmtPrecise(totalCredit)}
                  detail="Trial balance"
                />

                <ExecutiveMetric
                  label="Current period income"
                  value={fmtPrecise(currentPeriodNetIncome)}
                  detail="Financial position"
                />

                <ExecutiveMetric
                  label="Net cash movement"
                  value={fmtPrecise(netCashChange)}
                  detail={netCashChange >= 0 ? "Increase" : "Decrease"}
                />
              </div>

              <div className="flex flex-col gap-4 border-t border-slate-200 bg-slate-50 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <p className="text-xs font-bold text-slate-800">
                    Trial balance control
                  </p>

                  <p className="mt-1 text-[11px] text-slate-500">
                    Debit and credit totals should remain mathematically
                    balanced.
                  </p>
                </div>

                <div
                  className={`inline-flex w-fit items-center gap-2 rounded-full border px-3 py-1.5 text-[10px] font-bold uppercase tracking-wide ${
                    trialBalance?.balanced
                      ? "border-emerald-200 bg-emerald-50 text-emerald-700"
                      : "border-red-200 bg-red-50 text-red-700"
                  }`}
                >
                  <span className="h-1.5 w-1.5 rounded-full bg-current" />

                  {trialBalance?.balanced ? "Balanced" : "Review required"}
                </div>
              </div>

              {trialBalance ? (
                <details className="border-t border-slate-200">
                  <summary className="cursor-pointer px-5 py-4 text-xs font-bold text-slate-700 hover:bg-slate-50">
                    View trial balance accounts
                  </summary>

                  <div className="border-t border-slate-100 p-5">
                    <StatementTable
                      title="Trial Balance"
                      rows={trialBalance.accounts}
                      total={trialBalance.totalDebit}
                    />
                  </div>
                </details>
              ) : null}
            </div>
          </section>
        ) : null}

        {/* ==================================================================
           REPORT DOCUMENTS
           ================================================================== */}

        <section className="mt-10">
          <SectionHeading
            eyebrow="10 / Official Reports"
            title="Financial report centre"
            description="Download controlled accounting reports for management, finance and audit purposes."
          />

          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            <ReportDocument
              icon="ledger"
              endpoint="trial-balance"
              title="Trial Balance"
              description="Account balances and debit/credit control totals."
            />

            <ReportDocument
              icon="balance"
              endpoint="balance-sheet"
              title="Statement of Financial Position"
              description="Assets, liabilities and equity at the reporting date."
            />

            <ReportDocument
              icon="profit"
              endpoint="profit-and-loss"
              title="Statement of Profit or Loss"
              description="Income, expenses and net earnings for the period."
            />

            <ReportDocument
              icon="cash"
              endpoint="cash-flow"
              title="Statement of Cash Flows"
              description="Cash generated and used through lending and collections."
            />
          </div>
        </section>

        {/* ==================================================================
           OPERATIONAL REPORTS
           ================================================================== */}

        <section className="mt-10">
          <SectionHeading
            eyebrow="11 / Operational Reporting"
            title="Portfolio reports"
            description="Operational exports supporting lending, collections and customer management."
          />

          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            {[
              {
                endpoint: "loans",
                title: "Loan Portfolio",
                description:
                  "Complete portfolio of facilities, balances and statuses.",
                icon: "portfolio" as const,
              },
              {
                endpoint: "payments",
                title: "Payments",
                description: "Payment and collection activity.",
                icon: "cash" as const,
              },
              {
                endpoint: "borrowers",
                title: "Borrowers",
                description: "Borrower and customer portfolio information.",
                icon: "users" as const,
              },
              {
                endpoint: "overdue",
                title: "Overdue Portfolio",
                description: "Overdue facilities and repayment exposure.",
                icon: "risk" as const,
              },
            ].map((report) => (
              <div
                key={report.endpoint}
                className="flex flex-col justify-between rounded-xl border border-slate-200 bg-white p-5"
              >
                <div>
                  <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-slate-100 text-slate-700">
                    <Icon name={report.icon} size={17} />
                  </div>

                  <h3 className="mt-4 text-sm font-bold text-slate-900">
                    {report.title}
                  </h3>

                  <p className="mt-1 text-xs leading-5 text-slate-500">
                    {report.description}
                  </p>
                </div>

                <div className="mt-5 border-t border-slate-100 pt-4">
                  <ExportButtons
                    endpoint={report.endpoint}
                    label={report.title}
                  />
                </div>
              </div>
            ))}
          </div>
        </section>

        {/* ==================================================================
           REGULATORY
           ================================================================== */}

        <section className="mt-10 border border-slate-300 bg-slate-900 px-5 py-5">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <p className="text-[10px] font-bold uppercase tracking-[0.16em] text-slate-400">
                Regulatory reporting
              </p>

              <p className="mt-1 text-sm font-semibold text-white">
                Regulatory reports are maintained in the dedicated reporting
                workspace.
              </p>
            </div>

            <Link
              href="/dashboard/reports/regulatory"
              className="inline-flex shrink-0 items-center gap-2 text-xs font-bold text-white hover:text-slate-300"
            >
              Open regulatory reports
              <Icon name="arrow" size={14} />
            </Link>
          </div>
        </section>

        <footer className="mt-8 flex flex-col gap-2 border-t border-slate-300 pt-5 text-[10px] text-slate-400 sm:flex-row sm:items-center sm:justify-between">
          <p>Noble Loan Management Information System</p>

          <p>
            Confidential management information • Generated {fmtDate(asOfDate)}
          </p>
        </footer>
      </div>
    </main>
  );
}
