"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";

import { getDashboardStats } from "@/services/dashboardService";
import { getOverduePayments } from "@/services/paymentService";
import { accountingApi, expenseApi } from "@/services/api";

type Numeric = number | string | null | undefined;

type DashboardLoan = {
  id?: number;
  referenceNumber?: string;
  status?: string;
  creditQuality?: string | null;
  riskLevel?: string | null;
  daysOverdue?: number | null;
  amount?: Numeric;
  disbursedAmount?: Numeric;
  outstandingBalance?: Numeric;
  createdAt?: string;

  borrower?: {
    id?: number;
    firstName?: string;
    lastName?: string;
    fullName?: string;
    phone?: string;
    nationalId?: string;
  };
};

type DashboardStats = {
  totalLoans?: Numeric;
  pendingLoans?: Numeric;
  activeLoans?: Numeric;
  overdueLoans?: Numeric;
  completedLoans?: Numeric;
  defaultedLoans?: Numeric;

  totalDisbursed?: Numeric;
  totalCollected?: Numeric;
  outstandingBalance?: Numeric;
  collectedThisMonth?: Numeric;

  totalBorrowers?: Numeric;
  latePaymentsCount?: Numeric;
  portfolioAtRiskPct?: Numeric;

  recentLoans?: DashboardLoan[];
};

type AccountingSummary = {
  balanceSheet?: {
    totalAssets?: Numeric;
    totalLiabilities?: Numeric;
    totalEquity?: Numeric;
    currentPeriodNetIncome?: Numeric;
    balanced?: boolean;
  } | null;

  profitAndLoss?: {
    totalIncome?: Numeric;
    totalExpense?: Numeric;
    totalExpenses?: Numeric;
    netIncome?: Numeric;
  } | null;

  cashFlow?: {
    cashUsedForLending?: Numeric;
    cashFromCollections?: Numeric;
    cashFromFees?: Numeric;
    otherCashMovement?: Numeric;
    netChangeInCash?: Numeric;
  } | null;
};

type ExpenseSummary = {
  total?: Numeric;
  totalAmount?: Numeric;
  amount?: Numeric;
  count?: Numeric;
  byCategory?: Array<{
    category?: string;
    amount?: Numeric;
    total?: Numeric;
  }>;
};

type OverdueItem = {
  id?: number;
  amount?: Numeric;
  totalDue?: Numeric;
  dueDate?: string;
  daysOverdue?: Numeric;
  loan?: DashboardLoan;
  loanId?: number;
  borrower?: DashboardLoan["borrower"];
};

const numberValue = (value: Numeric): number => {
  const parsed = Number(value ?? 0);
  return Number.isFinite(parsed) ? parsed : 0;
};

const formatNumber = (value: Numeric): string =>
  new Intl.NumberFormat("en-RW").format(numberValue(value));

const formatMoney = (value: Numeric, currency = "RWF"): string =>
  new Intl.NumberFormat("en-RW", {
    style: "currency",
    currency,
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  }).format(numberValue(value));

const formatDate = (value?: string): string => {
  if (!value) return "—";

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return "—";
  }

  return new Intl.DateTimeFormat("en-RW", {
    day: "2-digit",
    month: "short",
    year: "numeric",
  }).format(date);
};

const unwrap = <T,>(value: unknown): T => {
  if (value && typeof value === "object" && "data" in value) {
    return (value as { data: T }).data;
  }

  return value as T;
};

const normalizeArray = <T,>(value: unknown): T[] => {
  const data = unwrap<unknown>(value);

  if (Array.isArray(data)) {
    return data as T[];
  }

  if (data && typeof data === "object") {
    const object = data as {
      content?: unknown;
      items?: unknown;
      results?: unknown;
    };

    if (Array.isArray(object.content)) {
      return object.content as T[];
    }

    if (Array.isArray(object.items)) {
      return object.items as T[];
    }

    if (Array.isArray(object.results)) {
      return object.results as T[];
    }
  }

  return [];
};

const borrowerName = (borrower?: DashboardLoan["borrower"]): string => {
  if (!borrower) return "Unlinked borrower";

  if (borrower.fullName?.trim()) {
    return borrower.fullName.trim();
  }

  const full = [borrower.firstName, borrower.lastName]
    .filter(Boolean)
    .join(" ")
    .trim();

  return full || "Unnamed borrower";
};

const qualityLabel = (value?: string | null): string => {
  if (!value) return "Not rated";

  return value
    .replace(/_/g, " ")
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
};

function QualityBadge({ value }: { value?: string | null }) {
  const normalized = value?.toUpperCase() || "";

  const classes =
    normalized === "CURRENT"
      ? "border-emerald-200 bg-emerald-50 text-emerald-700"
      : normalized === "WATCH"
        ? "border-amber-200 bg-amber-50 text-amber-700"
        : normalized === "SUBSTANDARD"
          ? "border-orange-200 bg-orange-50 text-orange-700"
          : normalized === "DOUBTFUL"
            ? "border-red-200 bg-red-50 text-red-700"
            : normalized === "WRITTEN_OFF"
              ? "border-slate-300 bg-slate-100 text-slate-700"
              : "border-slate-200 bg-slate-50 text-slate-500";

  return (
    <span
      className={`inline-flex items-center rounded-full border px-2.5 py-1 text-[10px] font-bold uppercase tracking-[0.08em] ${classes}`}
    >
      {qualityLabel(value)}
    </span>
  );
}

function StatusBadge({ status }: { status?: string }) {
  const normalized = status?.toUpperCase() || "";

  const classes =
    normalized === "ACTIVE"
      ? "bg-emerald-50 text-emerald-700 border-emerald-200"
      : normalized === "OVERDUE"
        ? "bg-red-50 text-red-700 border-red-200"
        : normalized === "PENDING"
          ? "bg-amber-50 text-amber-700 border-amber-200"
          : normalized === "PAID"
            ? "bg-slate-100 text-slate-600 border-slate-200"
            : "bg-slate-50 text-slate-600 border-slate-200";

  return (
    <span
      className={`inline-flex rounded-full border px-2.5 py-1 text-[10px] font-bold uppercase tracking-[0.08em] ${classes}`}
    >
      {status ? status.replace(/_/g, " ") : "Unknown"}
    </span>
  );
}

function SkeletonBlock({ className = "" }: { className?: string }) {
  return (
    <div className={`animate-pulse rounded-xl bg-slate-100 ${className}`} />
  );
}

function MetricCard({
  label,
  value,
  detail,
  tone = "navy",
  href,
}: {
  label: string;
  value: string;
  detail: string;
  tone?: "navy" | "gold" | "green" | "red";
  href?: string;
}) {
  const toneMap = {
    navy: {
      icon: "bg-slate-950 text-white",
      line: "bg-slate-950",
    },
    gold: {
      icon: "bg-[#c9a227]/10 text-[#8a6b00]",
      line: "bg-[#c9a227]",
    },
    green: {
      icon: "bg-emerald-50 text-emerald-700",
      line: "bg-emerald-600",
    },
    red: {
      icon: "bg-red-50 text-red-700",
      line: "bg-red-600",
    },
  };

  const content = (
    <div className="noble-metric-card group">
      <div className="flex items-start justify-between gap-4">
        <div>
          <div className="noble-kicker">{label}</div>

          <div className="mt-3 text-[clamp(1.45rem,2.2vw,2.15rem)] font-black tracking-[-0.035em] text-slate-950">
            {value}
          </div>

          <div className="mt-2 text-xs leading-5 text-slate-500">{detail}</div>
        </div>

        <div
          className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl ${toneMap[tone].icon}`}
        >
          <span className="text-sm font-black">
            {tone === "red"
              ? "!"
              : tone === "green"
                ? "✓"
                : tone === "gold"
                  ? "◆"
                  : "N"}
          </span>
        </div>
      </div>

      <div
        className={`mt-5 h-0.5 w-10 rounded-full transition-all duration-300 group-hover:w-16 ${toneMap[tone].line}`}
      />
    </div>
  );

  if (!href) return content;

  return <Link href={href}>{content}</Link>;
}

function SectionHeader({
  eyebrow,
  title,
  description,
  action,
}: {
  eyebrow?: string;
  title: string;
  description?: string;
  action?: React.ReactNode;
}) {
  return (
    <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div>
        {eyebrow && (
          <div className="noble-kicker text-[#8a6b00]">{eyebrow}</div>
        )}

        <h2 className="mt-2 text-xl font-black tracking-[-0.025em] text-slate-950 sm:text-2xl">
          {title}
        </h2>

        {description && (
          <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-500">
            {description}
          </p>
        )}
      </div>

      {action}
    </div>
  );
}

export default function DashboardPage() {
  const currency = "RWF";

  const [stats, setStats] = useState<DashboardStats | null>(null);

  const [overdue, setOverdue] = useState<OverdueItem[]>([]);

  const [accounting, setAccounting] = useState<AccountingSummary>({
    balanceSheet: null,
    profitAndLoss: null,
    cashFlow: null,
  });

  const [expenses, setExpenses] = useState<ExpenseSummary | null>(null);

  const [loading, setLoading] = useState(true);

  const [financialLoading, setFinancialLoading] = useState(true);

  const [error, setError] = useState("");

  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);

  const loadDashboard = useCallback(async (background = false) => {
    if (!background) {
      setLoading(true);
    }

    setError("");

    try {
      const result = await getDashboardStats();

      const dashboard = unwrap<DashboardStats>(result);

      if (dashboard) {
        setStats(dashboard);

        if (typeof window !== "undefined") {
          sessionStorage.setItem(
            "noble.dashboard.stats",
            JSON.stringify({
              timestamp: Date.now(),
              data: dashboard,
            }),
          );
        }
      }

      setLastUpdated(new Date());
    } catch (err) {
      console.error("Dashboard statistics failed", err);

      if (!background) {
        setError(
          err instanceof Error ? err.message : "Unable to load dashboard data.",
        );
      }
    } finally {
      if (!background) {
        setLoading(false);
      }
    }
  }, []);

  const loadFinancialSummary = useCallback(async () => {
    setFinancialLoading(true);

    const [pnlResult, balanceResult, cashResult, expenseResult, overdueResult] =
      await Promise.allSettled([
        accountingApi.profitAndLoss(),
        accountingApi.balanceSheet(),
        accountingApi.cashFlow(),
        expenseApi.summary(),
        getOverduePayments(),
      ]);

    if (pnlResult.status === "fulfilled") {
      setAccounting((current) => ({
        ...current,
        profitAndLoss: unwrap<AccountingSummary["profitAndLoss"]>(
          pnlResult.value,
        ),
      }));
    }

    if (balanceResult.status === "fulfilled") {
      setAccounting((current) => ({
        ...current,
        balanceSheet: unwrap<AccountingSummary["balanceSheet"]>(
          balanceResult.value,
        ),
      }));
    }

    if (cashResult.status === "fulfilled") {
      setAccounting((current) => ({
        ...current,
        cashFlow: unwrap<AccountingSummary["cashFlow"]>(cashResult.value),
      }));
    }

    if (expenseResult.status === "fulfilled") {
      setExpenses(unwrap<ExpenseSummary>(expenseResult.value));
    }

    if (overdueResult.status === "fulfilled") {
      setOverdue(normalizeArray<OverdueItem>(overdueResult.value));
    }

    setFinancialLoading(false);
  }, []);

  useEffect(() => {
    let active = true;

    if (typeof window !== "undefined") {
      try {
        const cached = sessionStorage.getItem("noble.dashboard.stats");

        if (cached) {
          const parsed = JSON.parse(cached);

          if (
            parsed?.data &&
            Date.now() - Number(parsed.timestamp || 0) < 60_000
          ) {
            setStats(parsed.data);
            setLoading(false);
          }
        }
      } catch {
        // Ignore malformed cache.
      }
    }

    void loadDashboard(Boolean(stats));

    const timer = window.setTimeout(() => {
      if (active) {
        void loadFinancialSummary();
      }
    }, 80);

    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [loadDashboard, loadFinancialSummary]);

  const recentLoans = useMemo(
    () =>
      Array.isArray(stats?.recentLoans) ? stats.recentLoans.slice(0, 6) : [],
    [stats],
  );

  const qualityBreakdown = useMemo(() => {
    const map = new Map<string, number>();

    recentLoans.forEach((loan) => {
      const quality = loan.creditQuality || loan.riskLevel || "NOT_RATED";

      map.set(quality, (map.get(quality) || 0) + 1);
    });

    return Array.from(map.entries()).sort((a, b) => b[1] - a[1]);
  }, [recentLoans]);

  const totalOutstanding = numberValue(stats?.outstandingBalance);

  const par = numberValue(stats?.portfolioAtRiskPct);

  const expenseAmount = numberValue(
    expenses?.totalAmount ?? expenses?.total ?? expenses?.amount,
  );

  const netIncome = numberValue(accounting.profitAndLoss?.netIncome);

  const totalAssets = numberValue(accounting.balanceSheet?.totalAssets);

  const cashMovement = numberValue(accounting.cashFlow?.netChangeInCash);

  const overdueAmount = overdue.reduce(
    (total, item) => total + numberValue(item.amount ?? item.totalDue),
    0,
  );

  const hasFinancialData = Boolean(
    accounting.balanceSheet ||
    accounting.profitAndLoss ||
    accounting.cashFlow ||
    expenses,
  );

  return (
    <main className="noble-dashboard">
      <div className="mx-auto max-w-[1600px] px-4 py-5 sm:px-6 lg:px-8 lg:py-7">
        {/* =========================================================
            EXECUTIVE HEADER
        ========================================================= */}
        <section className="noble-dashboard-hero">
          <div className="relative z-10">
            <div className="flex flex-wrap items-center gap-2">
              <span className="noble-hero-chip">Noble Loan Solutions</span>

              <span className="noble-hero-chip noble-hero-chip-muted">
                Lending operations
              </span>

              {lastUpdated && (
                <span className="hidden text-[10px] font-semibold text-white/45 sm:inline">
                  Updated{" "}
                  {lastUpdated.toLocaleTimeString([], {
                    hour: "2-digit",
                    minute: "2-digit",
                  })}
                </span>
              )}
            </div>

            <h1 className="mt-5 max-w-4xl text-3xl font-black tracking-[-0.04em] text-white sm:text-4xl lg:text-5xl">
              Portfolio command centre
            </h1>

            <p className="mt-3 max-w-2xl text-sm leading-6 text-white/60 sm:text-base">
              A consolidated view of lending performance, collections, credit
              quality and financial position.
            </p>

            <div className="mt-7 flex flex-wrap gap-2.5">
              <Link
                href="/dashboard/loans/new"
                className="noble-primary-action"
              >
                + New loan
              </Link>

              <Link
                href="/dashboard/payments"
                className="noble-secondary-action"
              >
                Record collection
              </Link>

              <Link
                href="/dashboard/reports"
                className="noble-secondary-action"
              >
                Executive reports
              </Link>

              <Link
                href="/dashboard/accounting"
                className="noble-secondary-action"
              >
                Accounting
              </Link>
            </div>
          </div>

          <div className="relative z-10 mt-8 hidden lg:block">
            <div className="noble-hero-sidecard">
              <div className="noble-kicker text-white/40">
                Portfolio position
              </div>

              <div className="mt-3 text-3xl font-black text-white">
                {formatMoney(totalOutstanding, currency)}
              </div>

              <div className="mt-1 text-xs text-white/45">
                Outstanding principal
              </div>

              <div className="mt-6 grid grid-cols-2 gap-3">
                <div>
                  <div className="text-[9px] font-bold uppercase tracking-wider text-white/35">
                    PAR
                  </div>

                  <div className="mt-1 text-lg font-black text-white">
                    {par.toFixed(2)}%
                  </div>
                </div>

                <div>
                  <div className="text-[9px] font-bold uppercase tracking-wider text-white/35">
                    Active
                  </div>

                  <div className="mt-1 text-lg font-black text-white">
                    {formatNumber(stats?.activeLoans)}
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* =========================================================
            ERROR
        ========================================================= */}
        {error && (
          <div className="mt-5 noble-alert noble-alert-error">
            <div>
              <div className="font-bold">Dashboard data unavailable</div>

              <div className="mt-1 text-xs">{error}</div>
            </div>

            <button
              type="button"
              onClick={() => void loadDashboard()}
              className="noble-alert-button"
            >
              Retry
            </button>
          </div>
        )}

        {/* =========================================================
            PRIMARY KPIs
        ========================================================= */}
        <section className="mt-6 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          {loading && !stats ? (
            Array.from({ length: 4 }).map((_, index) => (
              <div key={index} className="noble-panel p-6">
                <SkeletonBlock className="h-3 w-28" />
                <SkeletonBlock className="mt-5 h-9 w-36" />
                <SkeletonBlock className="mt-3 h-3 w-44" />
              </div>
            ))
          ) : (
            <>
              <MetricCard
                label="Active portfolio"
                value={formatMoney(totalOutstanding, currency)}
                detail={`${formatNumber(stats?.activeLoans)} active facilities`}
                tone="navy"
                href="/dashboard/loans"
              />

              <MetricCard
                label="Disbursed"
                value={formatMoney(stats?.totalDisbursed, currency)}
                detail={`${formatNumber(stats?.totalLoans)} total facilities`}
                tone="gold"
              />

              <MetricCard
                label="Collections"
                value={formatMoney(stats?.collectedThisMonth, currency)}
                detail="Collected this month"
                tone="green"
                href="/dashboard/payments"
              />

              <MetricCard
                label="Portfolio at risk"
                value={`${par.toFixed(2)}%`}
                detail={`${formatNumber(
                  stats?.overdueLoans,
                )} loans currently overdue`}
                tone={par > 10 ? "red" : "green"}
                href="/dashboard/loans"
              />
            </>
          )}
        </section>

        {/* =========================================================
            OPERATING POSITION
        ========================================================= */}
        <section className="mt-5 grid gap-5 xl:grid-cols-[1.45fr_.75fr]">
          {/* Portfolio quality */}
          <div className="noble-panel overflow-hidden">
            <div className="border-b border-slate-100 px-5 py-5 sm:px-6">
              <SectionHeader
                eyebrow="Portfolio intelligence"
                title="Lending performance"
                description="The figures below are taken from the existing lending engine. No frontend financial calculations are used."
                action={
                  <Link href="/dashboard/reports" className="noble-text-action">
                    View report →
                  </Link>
                }
              />
            </div>

            <div className="grid grid-cols-2 divide-x divide-slate-100 sm:grid-cols-4">
              <div className="px-5 py-5">
                <div className="noble-kicker">Active</div>
                <div className="mt-2 text-2xl font-black text-slate-950">
                  {formatNumber(stats?.activeLoans)}
                </div>
              </div>

              <div className="px-5 py-5">
                <div className="noble-kicker">Pending</div>
                <div className="mt-2 text-2xl font-black text-slate-950">
                  {formatNumber(stats?.pendingLoans)}
                </div>
              </div>

              <div className="px-5 py-5">
                <div className="noble-kicker">Overdue</div>
                <div className="mt-2 text-2xl font-black text-red-700">
                  {formatNumber(stats?.overdueLoans)}
                </div>
              </div>

              <div className="px-5 py-5">
                <div className="noble-kicker">Completed</div>
                <div className="mt-2 text-2xl font-black text-slate-950">
                  {formatNumber(stats?.completedLoans)}
                </div>
              </div>
            </div>

            <div className="border-t border-slate-100 px-5 py-6 sm:px-6">
              <div className="flex items-center justify-between">
                <div>
                  <div className="text-sm font-black text-slate-950">
                    Credit quality
                  </div>

                  <div className="mt-1 text-xs text-slate-500">
                    Quality attached to the actual loan records.
                  </div>
                </div>

                <Link href="/dashboard/borrowers" className="noble-text-action">
                  Review borrowers →
                </Link>
              </div>

              {qualityBreakdown.length === 0 ? (
                <div className="mt-5 rounded-xl border border-dashed border-slate-200 px-5 py-8 text-center text-xs text-slate-400">
                  Credit quality information is not present in the current
                  dashboard loan payload.
                </div>
              ) : (
                <div className="mt-5 space-y-3">
                  {qualityBreakdown.map(([quality, count]) => {
                    const total = recentLoans.length || 1;

                    const percentage = Math.round((count / total) * 100);

                    return (
                      <div
                        key={quality}
                        className="grid grid-cols-[110px_1fr_45px] items-center gap-3"
                      >
                        <QualityBadge value={quality} />

                        <div className="h-2 overflow-hidden rounded-full bg-slate-100">
                          <div
                            className="h-full rounded-full bg-slate-900 transition-all"
                            style={{
                              width: `${percentage}%`,
                            }}
                          />
                        </div>

                        <div className="text-right text-xs font-black text-slate-700">
                          {count}
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          </div>

          {/* Attention */}
          <div className="noble-panel">
            <div className="border-b border-slate-100 px-5 py-5">
              <div className="noble-kicker text-red-600">Operations</div>

              <h2 className="mt-2 text-xl font-black text-slate-950">
                Attention required
              </h2>

              <p className="mt-1 text-xs leading-5 text-slate-500">
                Items that require operational attention.
              </p>
            </div>

            <div className="divide-y divide-slate-100">
              <Link href="/dashboard/payments" className="noble-attention-row">
                <span className="noble-attention-icon danger">!</span>

                <span className="min-w-0 flex-1">
                  <span className="block text-sm font-bold text-slate-900">
                    Overdue collections
                  </span>

                  <span className="mt-0.5 block text-xs text-slate-500">
                    {formatNumber(stats?.overdueLoans)} loans require collection
                    attention
                  </span>
                </span>

                <span className="text-lg font-black text-red-700">
                  {formatNumber(stats?.overdueLoans)}
                </span>
              </Link>

              <Link href="/dashboard/loans" className="noble-attention-row">
                <span className="noble-attention-icon warning">◷</span>

                <span className="min-w-0 flex-1">
                  <span className="block text-sm font-bold text-slate-900">
                    Pending credit decisions
                  </span>

                  <span className="mt-0.5 block text-xs text-slate-500">
                    Applications awaiting review
                  </span>
                </span>

                <span className="text-lg font-black text-amber-700">
                  {formatNumber(stats?.pendingLoans)}
                </span>
              </Link>

              <Link href="/dashboard/loans" className="noble-attention-row">
                <span className="noble-attention-icon danger">×</span>

                <span className="min-w-0 flex-1">
                  <span className="block text-sm font-bold text-slate-900">
                    Defaulted facilities
                  </span>

                  <span className="mt-0.5 block text-xs text-slate-500">
                    Accounts requiring escalation
                  </span>
                </span>

                <span className="text-lg font-black text-red-700">
                  {formatNumber(stats?.defaultedLoans)}
                </span>
              </Link>

              <Link
                href="/dashboard/accounting"
                className="noble-attention-row"
              >
                <span className="noble-attention-icon neutral">$</span>

                <span className="min-w-0 flex-1">
                  <span className="block text-sm font-bold text-slate-900">
                    Accounting control
                  </span>

                  <span className="mt-0.5 block text-xs text-slate-500">
                    Review current financial position
                  </span>
                </span>

                <span className="text-xs font-bold text-slate-500">Open</span>
              </Link>
            </div>
          </div>
        </section>

        {/* =========================================================
            COLLECTIONS + FINANCIAL POSITION
        ========================================================= */}
        <section className="mt-5 grid gap-5 xl:grid-cols-[1.25fr_.75fr]">
          <div className="noble-panel overflow-hidden">
            <div className="border-b border-slate-100 px-5 py-5 sm:px-6">
              <SectionHeader
                eyebrow="Collections"
                title="Overdue facilities"
                description="Collections are linked directly to the underlying loan records."
                action={
                  <Link
                    href="/dashboard/payments"
                    className="noble-text-action"
                  >
                    Collections workspace →
                  </Link>
                }
              />
            </div>

            {overdue.length === 0 ? (
              <div className="px-6 py-12 text-center">
                <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-emerald-50 text-emerald-700">
                  ✓
                </div>

                <div className="mt-4 text-sm font-black text-slate-950">
                  No overdue collection records returned
                </div>

                <div className="mt-1 text-xs text-slate-500">
                  The dashboard is using the same overdue payment source as the
                  collection workflow.
                </div>
              </div>
            ) : (
              <>
                <div className="grid grid-cols-2 border-b border-slate-100">
                  <div className="px-5 py-4 sm:px-6">
                    <div className="noble-kicker">Overdue exposure</div>

                    <div className="mt-2 text-xl font-black text-red-700">
                      {formatMoney(overdueAmount, currency)}
                    </div>
                  </div>

                  <div className="border-l border-slate-100 px-5 py-4 sm:px-6">
                    <div className="noble-kicker">Open installments</div>

                    <div className="mt-2 text-xl font-black text-slate-950">
                      {overdue.length}
                    </div>
                  </div>
                </div>

                <div className="divide-y divide-slate-100">
                  {overdue.slice(0, 6).map((item, index) => {
                    const loan = item.loan;

                    const id = loan?.id ?? item.loanId;

                    return (
                      <div
                        key={item.id ?? `${id}-${index}`}
                        className="px-5 py-4 transition hover:bg-slate-50 sm:px-6"
                      >
                        <div className="flex items-center justify-between gap-4">
                          <div className="min-w-0">
                            {id ? (
                              <Link
                                href={`/dashboard/loans/${id}`}
                                className="text-sm font-black text-slate-950 hover:text-[#8a6b00]"
                              >
                                {loan?.referenceNumber || `Loan #${id}`}
                              </Link>
                            ) : (
                              <div className="text-sm font-black text-slate-950">
                                Loan facility
                              </div>
                            )}

                            <div className="mt-1 text-xs text-slate-500">
                              {borrowerName(loan?.borrower ?? item.borrower)}
                            </div>
                          </div>

                          <div className="text-right">
                            <div className="text-sm font-black text-red-700">
                              {formatMoney(
                                item.amount ?? item.totalDue,
                                currency,
                              )}
                            </div>

                            <div className="mt-1 text-[10px] font-semibold uppercase tracking-wider text-slate-400">
                              Due {formatDate(item.dueDate)}
                            </div>
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </>
            )}
          </div>

          <div className="noble-panel">
            <div className="border-b border-slate-100 px-5 py-5">
              <SectionHeader
                eyebrow="Finance"
                title="Financial position"
                description="Live accounting summaries from the existing accounting engine."
                action={
                  <Link
                    href="/dashboard/accounting"
                    className="noble-text-action"
                  >
                    Open accounting →
                  </Link>
                }
              />
            </div>

            {financialLoading && !hasFinancialData ? (
              <div className="space-y-4 px-5 py-6">
                <SkeletonBlock className="h-16" />
                <SkeletonBlock className="h-16" />
                <SkeletonBlock className="h-16" />
              </div>
            ) : (
              <div className="divide-y divide-slate-100">
                <div className="px-5 py-5">
                  <div className="noble-kicker">Total assets</div>

                  <div className="mt-2 text-xl font-black text-slate-950">
                    {formatMoney(totalAssets, currency)}
                  </div>
                </div>

                <div className="px-5 py-5">
                  <div className="noble-kicker">Net income</div>

                  <div
                    className={`mt-2 text-xl font-black ${
                      netIncome < 0 ? "text-red-700" : "text-emerald-700"
                    }`}
                  >
                    {formatMoney(netIncome, currency)}
                  </div>
                </div>

                <div className="px-5 py-5">
                  <div className="noble-kicker">Net cash movement</div>

                  <div
                    className={`mt-2 text-xl font-black ${
                      cashMovement < 0 ? "text-red-700" : "text-slate-950"
                    }`}
                  >
                    {formatMoney(cashMovement, currency)}
                  </div>
                </div>

                <div className="px-5 py-5">
                  <div className="noble-kicker">Expenses</div>

                  <div className="mt-2 text-xl font-black text-slate-950">
                    {formatMoney(expenseAmount, currency)}
                  </div>

                  <Link
                    href="/dashboard/expenses"
                    className="mt-2 inline-block text-xs font-bold text-[#8a6b00] hover:underline"
                  >
                    Review expenses →
                  </Link>
                </div>
              </div>
            )}
          </div>
        </section>

        {/* =========================================================
            RECENT LOANS
        ========================================================= */}
        <section className="mt-5 noble-panel overflow-hidden">
          <div className="border-b border-slate-100 px-5 py-5 sm:px-6">
            <SectionHeader
              eyebrow="Lending book"
              title="Recent facilities"
              description="Borrower, facility status, outstanding exposure and credit quality in one operational view."
              action={
                <Link href="/dashboard/loans" className="noble-text-action">
                  Open loan portfolio →
                </Link>
              }
            />
          </div>

          {recentLoans.length === 0 ? (
            <div className="px-6 py-12 text-center text-sm text-slate-400">
              No recent loan records are available.
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="noble-table">
                <thead>
                  <tr>
                    <th>Facility</th>
                    <th>Borrower</th>
                    <th>Status</th>
                    <th>Credit quality</th>
                    <th>Outstanding</th>
                    <th>Created</th>
                  </tr>
                </thead>

                <tbody>
                  {recentLoans.map((loan, index) => (
                    <tr key={loan.id ?? `${loan.referenceNumber}-${index}`}>
                      <td>
                        <Link
                          href={
                            loan.id
                              ? `/dashboard/loans/${loan.id}`
                              : "/dashboard/loans"
                          }
                          className="font-black text-slate-950 hover:text-[#8a6b00]"
                        >
                          {loan.referenceNumber || `Loan #${loan.id ?? "—"}`}
                        </Link>
                      </td>

                      <td>
                        <div className="font-semibold text-slate-800">
                          {borrowerName(loan.borrower)}
                        </div>

                        {loan.borrower?.nationalId && (
                          <div className="mt-0.5 text-[10px] text-slate-400">
                            {loan.borrower.nationalId}
                          </div>
                        )}
                      </td>

                      <td>
                        <StatusBadge status={loan.status} />
                      </td>

                      <td>
                        <QualityBadge
                          value={loan.creditQuality ?? loan.riskLevel}
                        />
                      </td>

                      <td className="font-black text-slate-950">
                        {formatMoney(loan.outstandingBalance, currency)}
                      </td>

                      <td className="text-xs text-slate-500">
                        {formatDate(loan.createdAt)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>

        {/* =========================================================
            EXECUTIVE SNAPSHOT
        ========================================================= */}
        <section className="mt-5 grid gap-5 lg:grid-cols-3">
          <Link href="/dashboard/accounting" className="noble-command-card">
            <div className="noble-command-icon">$</div>

            <div>
              <div className="text-sm font-black text-slate-950">
                Accounting
              </div>

              <div className="mt-1 text-xs leading-5 text-slate-500">
                Trial balance, balance sheet, P&L, cash flow, journals and bank
                accounts.
              </div>

              <div className="mt-4 text-xs font-black text-[#8a6b00]">
                Open finance workspace →
              </div>
            </div>
          </Link>

          <Link href="/dashboard/expenses" className="noble-command-card">
            <div className="noble-command-icon">−</div>

            <div>
              <div className="text-sm font-black text-slate-950">Expenses</div>

              <div className="mt-1 text-xs leading-5 text-slate-500">
                Monitor operating expenses and their effect on the
                institution&apos;s financial position.
              </div>

              <div className="mt-4 text-xs font-black text-[#8a6b00]">
                Review expenses →
              </div>
            </div>
          </Link>

          <Link href="/dashboard/reports" className="noble-command-card">
            <div className="noble-command-icon">↗</div>

            <div>
              <div className="text-sm font-black text-slate-950">Reports</div>

              <div className="mt-1 text-xs leading-5 text-slate-500">
                Executive summaries on screen, with complete Excel exports
                available when detail is required.
              </div>

              <div className="mt-4 text-xs font-black text-[#8a6b00]">
                Open reporting centre →
              </div>
            </div>
          </Link>
        </section>

        <div className="mt-7 flex flex-col items-center justify-between gap-3 border-t border-slate-200 pt-5 text-[10px] font-semibold text-slate-400 sm:flex-row">
          <span>Noble Loan Solutions • Lending operations</span>

          <span>
            Financial figures are sourced from the existing backend services.
          </span>
        </div>
      </div>
    </main>
  );
}
