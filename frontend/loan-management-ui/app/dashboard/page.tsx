"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";

import { loanApi } from "@/services/api";
import { DashboardStats, Loan } from "@/types";

import { StatCard, Card, CardHeader, CardBody } from "@/components/ui/Card";

import { StatusBadge, RiskBadge } from "@/components/ui/Badge";

import { Button } from "@/components/ui/Button";

import { Table, Thead, Th, Tbody, Tr, Td } from "@/components/ui/Table";

import {
  formatCurrency,
  formatDate,
  formatNumber,
  LOAN_TYPE_META,
} from "@/lib/utils";

import { useAuth } from "@/hooks/useAuth";

import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
  Legend,
} from "recharts";

/* ============================================================
   NOBLE LOAN SOLUTIONS
   BANK-GRADE DASHBOARD
   ============================================================ */

const BRAND = {
  navy: "#0B1F3A",
  navyDark: "#07152A",
  navyLight: "#16365F",
  yellow: "#F4C430",
  yellowDark: "#C99A00",

  green: "#15803D",
  greenLight: "#ECFDF3",

  red: "#B91C1C",
  redLight: "#FEF2F2",

  amber: "#B45309",
  amberLight: "#FFFBEB",

  blue: "#1D4ED8",
  blueLight: "#EFF6FF",

  slate: "#475569",
  slateLight: "#F8FAFC",

  border: "#E2E8F0",
};

const CHART_COLORS = [
  BRAND.navy,
  BRAND.yellow,
  "#2563EB",
  "#F59E0B",
  "#1E40AF",
  "#D97706",
];

/* ============================================================
   SAFE NUMBER HELPERS
   ============================================================ */

function safeNumber(value: unknown): number {
  const parsed = Number(value);

  return Number.isFinite(parsed) ? parsed : 0;
}

function normalizeDashboardResponse(value: unknown): DashboardStats {
  if (!value || typeof value !== "object") {
    return value as DashboardStats;
  }

  const root = value as Record<string, unknown>;
  const nested = root.data;

  if (nested && typeof nested === "object") {
    const candidate = nested as Record<string, unknown>;

    if (
      "totalLoans" in candidate ||
      "totalBorrowers" in candidate ||
      "totalDisbursed" in candidate
    ) {
      return candidate as unknown as DashboardStats;
    }
  }

  return value as DashboardStats;
}

function hasValue(value: unknown): boolean {
  if (value === null || value === undefined || value === "") {
    return false;
  }

  const parsed = Number(value);

  return Number.isFinite(parsed);
}

/* ============================================================
   DASHBOARD
   ============================================================ */

export default function DashboardPage() {
  const router = useRouter();

  const { currency, locale, user } = useAuth();

  const [stats, setStats] = useState<DashboardStats | null>(null);

  const [loading, setLoading] = useState(true);

  const [refreshing, setRefreshing] = useState(false);

  const [error, setError] = useState("");

  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);

  /* ==========================================================
     FORMATTERS
     ========================================================== */

  const fc = useCallback(
    (value?: number | string | null) => {
      if (!hasValue(value)) {
        return "—";
      }

      return formatCurrency(safeNumber(value), currency, locale);
    },
    [currency, locale],
  );

  const fn = useCallback((value?: number | string | null) => {
    if (!hasValue(value)) {
      return "—";
    }

    return formatNumber(safeNumber(value));
  }, []);

  const today = useMemo(() => {
    return new Date().toLocaleDateString(locale || "en-US", {
      weekday: "long",
      year: "numeric",
      month: "long",
      day: "numeric",
    });
  }, [locale]);

  /* ==========================================================
     LOAD DASHBOARD
     ========================================================== */

  const loadDashboard = useCallback(async (isRefresh = false) => {
    try {
      if (isRefresh) {
        setRefreshing(true);
      } else {
        setLoading(true);
      }

      setError("");

      const data = await loanApi.dashboard();

      setStats(normalizeDashboardResponse(data));

      setLastUpdated(new Date());
    } catch (e: any) {
      console.error("Dashboard loading failed:", e);

      setError(e?.message || "Unable to load dashboard information.");
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    let mounted = true;

    const run = async () => {
      try {
        setLoading(true);
        setError("");

        const data = await loanApi.dashboard();

        if (!mounted) {
          return;
        }

        setStats(normalizeDashboardResponse(data));
        setLastUpdated(new Date());
      } catch (e: any) {
        if (!mounted) {
          return;
        }

        console.error("Dashboard loading failed:", e);

        setError(e?.message || "Unable to load dashboard information.");
      } finally {
        if (!mounted) {
          return;
        }

        setLoading(false);
      }
    };

    run();

    return () => {
      mounted = false;
    };
  }, []);

  /* ==========================================================
     DERIVED DATA
     ========================================================== */

  const portfolio = useMemo(() => {
    if (!stats) {
      return {
        totalLoans: 0,
        totalBorrowers: 0,
        activeLoans: 0,
        pendingLoans: 0,
        overdueLoans: 0,
        completedLoans: 0,
        defaultedLoans: 0,
        totalDisbursed: 0,
        outstandingBalance: 0,
        totalCollected: 0,
        collectedThisMonth: 0,
        latePaymentsCount: 0,
        portfolioAtRiskAmount: 0,
        portfolioAtRiskPct: null as number | null,
      };
    }

    /*
     * IMPORTANT:
     *
     * Financial figures are deliberately READ from DashboardStats.
     *
     * This component does NOT calculate:
     * - interest
     * - management fees
     * - principal
     * - repayment allocation
     * - accrued interest
     * - outstanding principal
     * - portfolio-at-risk
     *
     * Those values belong to the backend financial engine so every
     * screen uses exactly the same financial rules.
     */

    const parValue = hasValue(stats.portfolioAtRiskPct)
      ? safeNumber(stats.portfolioAtRiskPct)
      : null;

    return {
      totalLoans: safeNumber(stats.totalLoans),
      totalBorrowers: safeNumber(stats.totalBorrowers),
      activeLoans: safeNumber(stats.activeLoans),
      pendingLoans: safeNumber(stats.pendingLoans),
      overdueLoans: safeNumber(stats.overdueLoans),
      completedLoans: safeNumber(stats.completedLoans),
      defaultedLoans: safeNumber(stats.defaultedLoans),

      totalDisbursed: safeNumber(stats.totalDisbursed),

      outstandingBalance: safeNumber(stats.outstandingBalance),

      totalCollected: safeNumber(stats.totalCollected),

      collectedThisMonth: safeNumber(stats.collectedThisMonth),

      latePaymentsCount: safeNumber(stats.latePaymentsCount),

      portfolioAtRiskAmount: safeNumber(stats.portfolioAtRiskAmount),

      portfolioAtRiskPct: parValue,
    };
  }, [stats]);

  const pieData = useMemo(() => {
    return [
      {
        name: "Active",
        value: portfolio.activeLoans,
      },
      {
        name: "Pending",
        value: portfolio.pendingLoans,
      },
      {
        name: "Overdue",
        value: portfolio.overdueLoans,
      },
      {
        name: "Paid",
        value: portfolio.completedLoans,
      },
      {
        name: "Defaulted",
        value: portfolio.defaultedLoans,
      },
    ].filter((item) => item.value > 0);
  }, [portfolio]);

  const typeData = useMemo(() => {
    return (stats?.loanTypeBreakdown || []).map((item) => ({
      name: LOAN_TYPE_META[String(item.type)]?.label ?? String(item.type),

      count: safeNumber(item.count),

      amount: safeNumber(item.amount),
    }));
  }, [stats]);

  const recentLoans = useMemo(() => {
    return stats?.recentLoans || [];
  }, [stats]);

  const attentionRequired =
    portfolio.pendingLoans > 0 ||
    portfolio.overdueLoans > 0 ||
    portfolio.defaultedLoans > 0;

  const hasPortfolio =
    portfolio.totalLoans > 0 ||
    portfolio.totalDisbursed > 0 ||
    portfolio.outstandingBalance > 0;

  /* ==========================================================
     LOADING STATE
     ========================================================== */

  if (loading) {
    return <DashboardSkeleton />;
  }

  /* ==========================================================
     ERROR STATE
     ========================================================== */

  if (error && !stats) {
    return (
      <div className="min-h-[70vh] flex items-center justify-center bg-[#F8FAFC] px-4">
        <div className="w-full max-w-lg rounded-2xl border border-red-200 bg-white shadow-sm p-8 text-center">
          <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-red-50 text-2xl">
            !
          </div>

          <h2 className="mt-5 text-xl font-bold text-[#0B1F3A]">
            Dashboard unavailable
          </h2>

          <p className="mt-2 text-sm leading-6 text-slate-500">
            We could not retrieve the portfolio dashboard from the server.
          </p>

          <div className="mt-4 rounded-xl border border-red-100 bg-red-50 px-4 py-3 text-left">
            <p className="text-xs font-semibold uppercase tracking-wide text-red-700">
              Server response
            </p>

            <p className="mt-1 break-words text-sm text-red-800">{error}</p>
          </div>

          <Button className="mt-6" onClick={() => loadDashboard(false)}>
            Try Again
          </Button>
        </div>
      </div>
    );
  }

  /* ==========================================================
     EMPTY STATE
     ========================================================== */

  if (!stats) {
    return (
      <div className="min-h-[70vh] flex items-center justify-center bg-[#F8FAFC] px-4">
        <div className="text-center">
          <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-2xl bg-[#EEF3F9] text-2xl">
            ▦
          </div>

          <h2 className="mt-5 text-xl font-bold text-[#0B1F3A]">
            No dashboard data
          </h2>

          <p className="mt-2 max-w-md text-sm leading-6 text-slate-500">
            There is currently no portfolio information available for this
            organization.
          </p>
        </div>
      </div>
    );
  }

  /* ==========================================================
     RENDER
     ========================================================== */

  return (
    <div className="min-h-full bg-[#F8FAFC] pb-12">
      <div className="space-y-6">
        {/* ====================================================
            SERVER ERROR BANNER
            ==================================================== */}

        {error && (
          <div
            role="alert"
            className="flex flex-col gap-3 rounded-xl border border-red-200 bg-red-50 px-4 py-3 sm:flex-row sm:items-center sm:justify-between"
          >
            <div>
              <p className="text-sm font-semibold text-red-800">
                Dashboard refresh failed
              </p>

              <p className="mt-0.5 text-xs text-red-700">
                Existing dashboard data is still displayed.
              </p>
            </div>

            <Button
              variant="secondary"
              size="sm"
              onClick={() => loadDashboard(true)}
              disabled={refreshing}
            >
              {refreshing ? "Refreshing..." : "Retry"}
            </Button>
          </div>
        )}

        {/* ====================================================
            EXECUTIVE HEADER
            ==================================================== */}

        <section className="relative overflow-hidden rounded-2xl bg-gradient-to-br from-[#07152A] via-[#0B1F3A] to-[#16365F] text-white shadow-[0_18px_50px_rgba(7,21,42,0.18)]">
          <div className="pointer-events-none absolute inset-0 overflow-hidden">
            <div className="absolute -right-20 -top-24 h-72 w-72 rounded-full bg-[#F4C430]/10" />

            <div className="absolute right-32 -bottom-32 h-64 w-64 rounded-full bg-[#F4C430]/10" />

            <div className="absolute left-[48%] -top-24 h-52 w-52 rounded-full bg-white/[0.035]" />

            <div className="absolute bottom-0 left-0 h-px w-full bg-white/10" />
          </div>

          <div className="relative px-5 py-6 sm:px-7 lg:px-9 lg:py-8">
            <div className="flex flex-col gap-7 xl:flex-row xl:items-center xl:justify-between">
              <div className="min-w-0">
                <div className="mb-4 flex flex-wrap items-center gap-2">
                  <span className="inline-flex items-center rounded-full bg-[#F4C430] px-3 py-1 text-[10px] font-extrabold tracking-[0.14em] text-[#07152A]">
                    NOBLE LOAN SOLUTIONS
                  </span>

                  <span className="inline-flex items-center gap-1.5 rounded-full border border-white/10 bg-white/5 px-3 py-1 text-[10px] font-medium text-blue-100">
                    <span className="h-1.5 w-1.5 rounded-full bg-emerald-400" />
                    Lending Operations
                  </span>
                </div>

                <h1 className="text-2xl font-extrabold tracking-tight sm:text-3xl lg:text-[34px]">
                  Welcome back
                  {user?.name ? `, ${user.name}` : ""}
                </h1>

                <p className="mt-2 max-w-2xl text-sm leading-6 text-blue-100">
                  Monitor lending performance, collections, portfolio risk, and
                  operational activity from one controlled workspace.
                </p>

                <div className="mt-4 flex flex-wrap items-center gap-x-4 gap-y-2 text-xs text-blue-200">
                  <span>{user?.organizationName || "Organization"}</span>

                  <span className="hidden h-1 w-1 rounded-full bg-blue-300/50 sm:block" />

                  <span>{today}</span>

                  {lastUpdated && (
                    <>
                      <span className="hidden h-1 w-1 rounded-full bg-blue-300/50 sm:block" />

                      <span>
                        Updated{" "}
                        {lastUpdated.toLocaleTimeString(locale || "en-US", {
                          hour: "2-digit",
                          minute: "2-digit",
                        })}
                      </span>
                    </>
                  )}
                </div>
              </div>

              <div className="flex flex-wrap gap-2.5">
                <Button
                  variant="secondary"
                  icon="↻"
                  onClick={() => loadDashboard(true)}
                  disabled={refreshing}
                >
                  {refreshing ? "Refreshing..." : "Refresh"}
                </Button>

                <Button
                  variant="secondary"
                  icon="👥"
                  onClick={() => router.push("/dashboard/borrowers")}
                >
                  Borrowers
                </Button>

                <Button
                  icon="💼"
                  onClick={() => router.push("/dashboard/loans")}
                >
                  View Loans
                </Button>
              </div>
            </div>
          </div>
        </section>

        {/* ====================================================
            PORTFOLIO KPI
            ==================================================== */}

        <section>
          <div className="mb-3 flex flex-col gap-1 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <h2 className="text-sm font-extrabold uppercase tracking-[0.08em] text-[#0B1F3A]">
                Portfolio overview
              </h2>

              <p className="mt-1 text-xs text-slate-400">
                Authoritative portfolio figures from the lending system.
              </p>
            </div>

            {!hasPortfolio && (
              <span className="inline-flex w-fit items-center rounded-full border border-slate-200 bg-white px-2.5 py-1 text-[10px] font-semibold uppercase tracking-wide text-slate-500">
                No active portfolio
              </span>
            )}
          </div>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <StatCard
              icon="💼"
              label="Total Loans"
              value={fn(portfolio.totalLoans)}
              sub={`${fn(portfolio.activeLoans)} active`}
              color={BRAND.navy}
            />

            <StatCard
              icon="👥"
              label="Total Borrowers"
              value={fn(portfolio.totalBorrowers)}
              sub="Registered clients"
              color={BRAND.navyLight}
            />

            <StatCard
              icon="💰"
              label="Total Disbursed"
              value={fc(portfolio.totalDisbursed)}
              sub="Capital released"
              color={BRAND.navy}
            />

            <StatCard
              icon="📊"
              label="Outstanding"
              value={fc(portfolio.outstandingBalance)}
              sub="Current portfolio balance"
              color={BRAND.yellowDark}
            />
          </div>
        </section>

        {/* ====================================================
            RISK / PERFORMANCE
            ==================================================== */}

        <section>
          <div className="mb-3">
            <h2 className="text-sm font-extrabold uppercase tracking-[0.08em] text-[#0B1F3A]">
              Performance & risk
            </h2>

            <p className="mt-1 text-xs text-slate-400">
              Operational indicators requiring management visibility.
            </p>
          </div>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <StatCard
              icon="⏳"
              label="Pending Review"
              value={fn(portfolio.pendingLoans)}
              sub="Awaiting action"
              color={BRAND.yellowDark}
            />

            <StatCard
              icon="⚠️"
              label="Overdue Loans"
              value={fn(portfolio.overdueLoans)}
              sub={`${fn(portfolio.latePaymentsCount)} late payments`}
              color="#DC2626"
            />

            <StatCard
              icon="✓"
              label="Total Collected"
              value={fc(portfolio.totalCollected)}
              sub={`${fc(portfolio.collectedThisMonth)} this month`}
              color={BRAND.green}
            />

            <StatCard
              icon="🎯"
              label="Portfolio at Risk"
              value={
                portfolio.portfolioAtRiskPct === null
                  ? "—"
                  : `${portfolio.portfolioAtRiskPct.toFixed(1)}%`
              }
              sub={
                portfolio.portfolioAtRiskPct === null
                  ? "Metric unavailable"
                  : portfolio.portfolioAtRiskPct > 5
                    ? "Requires attention"
                    : "Within monitoring threshold"
              }
              color={
                portfolio.portfolioAtRiskPct !== null &&
                portfolio.portfolioAtRiskPct > 5
                  ? "#DC2626"
                  : BRAND.navy
              }
            />
          </div>
        </section>

        {/* ====================================================
            MANAGEMENT ATTENTION
            ==================================================== */}

        {attentionRequired && (
          <Card className="overflow-hidden border-[#F4C430]/50 bg-gradient-to-r from-[#FFFDF2] via-white to-white">
            <CardBody>
              <div className="flex flex-col gap-5 lg:flex-row lg:items-center lg:justify-between">
                <div className="flex min-w-0 items-start gap-4">
                  <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-[#FFF3B0] text-lg">
                    !
                  </div>

                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <h3 className="font-bold text-[#0B1F3A]">
                        Management attention required
                      </h3>

                      <span className="rounded-full bg-[#FFF3B0] px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide text-[#806200]">
                        Action
                      </span>
                    </div>

                    <p className="mt-1 text-sm leading-6 text-slate-500">
                      The current portfolio contains items that require
                      operational review.
                    </p>

                    <div className="mt-3 flex flex-wrap gap-2">
                      {portfolio.pendingLoans > 0 && (
                        <span className="rounded-full bg-[#FFF3B0] px-2.5 py-1 text-xs font-semibold text-[#806200]">
                          {fn(portfolio.pendingLoans)} pending
                        </span>
                      )}

                      {portfolio.overdueLoans > 0 && (
                        <span className="rounded-full bg-red-100 px-2.5 py-1 text-xs font-semibold text-red-700">
                          {fn(portfolio.overdueLoans)} overdue
                        </span>
                      )}

                      {portfolio.defaultedLoans > 0 && (
                        <span className="rounded-full bg-red-100 px-2.5 py-1 text-xs font-semibold text-red-700">
                          {fn(portfolio.defaultedLoans)} defaulted
                        </span>
                      )}
                    </div>
                  </div>
                </div>

                <Button onClick={() => router.push("/dashboard/loans")}>
                  Review Portfolio →
                </Button>
              </div>
            </CardBody>
          </Card>
        )}

        {/* ====================================================
            ANALYTICS
            ==================================================== */}

        <div className="grid grid-cols-1 gap-5 xl:grid-cols-3">
          {/* ==================================================
              LOAN TYPE
              ================================================== */}

          <Card className="xl:col-span-2">
            <CardHeader
              title="Portfolio by loan type"
              action={
                <span className="text-xs text-slate-400">Loan count</span>
              }
            />

            <CardBody>
              {typeData.length > 0 ? (
                <ResponsiveContainer width="100%" height={300}>
                  <BarChart
                    data={typeData}
                    barSize={30}
                    margin={{
                      top: 10,
                      right: 10,
                      left: -10,
                      bottom: 10,
                    }}
                  >
                    <CartesianGrid
                      strokeDasharray="3 3"
                      stroke="#E5E7EB"
                      vertical={false}
                    />

                    <XAxis
                      dataKey="name"
                      tick={{
                        fontSize: 10,
                        fill: "#64748B",
                      }}
                      axisLine={false}
                      tickLine={false}
                    />

                    <YAxis
                      allowDecimals={false}
                      tick={{
                        fontSize: 10,
                        fill: "#94A3B8",
                      }}
                      axisLine={false}
                      tickLine={false}
                    />

                    <Tooltip
                      formatter={(value: number) =>
                        formatNumber(safeNumber(value))
                      }
                      contentStyle={{
                        borderRadius: 12,
                        border: "1px solid #E2E8F0",
                        boxShadow: "0 12px 30px rgba(15,23,42,0.10)",
                        fontSize: 12,
                      }}
                    />

                    <Bar
                      dataKey="count"
                      fill={BRAND.navy}
                      radius={[6, 6, 0, 0]}
                      name="Loans"
                    />
                  </BarChart>
                </ResponsiveContainer>
              ) : (
                <ChartEmptyState
                  title="No loan type data"
                  description="Loan type analytics will appear once portfolio data is available."
                />
              )}
            </CardBody>
          </Card>

          {/* ==================================================
              STATUS DISTRIBUTION
              ================================================== */}

          <Card>
            <CardHeader
              title="Loan status"
              action={
                <span className="text-xs text-slate-400">Distribution</span>
              }
            />

            <CardBody>
              {pieData.length > 0 ? (
                <ResponsiveContainer width="100%" height={300}>
                  <PieChart>
                    <Pie
                      data={pieData}
                      dataKey="value"
                      nameKey="name"
                      cx="50%"
                      cy="43%"
                      outerRadius={86}
                      innerRadius={52}
                      paddingAngle={3}
                      stroke="none"
                    >
                      {pieData.map((_, index) => (
                        <Cell
                          key={`status-${index}`}
                          fill={CHART_COLORS[index % CHART_COLORS.length]}
                        />
                      ))}
                    </Pie>

                    <Tooltip />

                    <Legend
                      iconSize={8}
                      wrapperStyle={{
                        fontSize: 11,
                      }}
                    />
                  </PieChart>
                </ResponsiveContainer>
              ) : (
                <ChartEmptyState
                  title="No status data"
                  description="Loan status distribution will appear here."
                />
              )}
            </CardBody>
          </Card>
        </div>

        {/* ====================================================
            COLLECTION PERFORMANCE
            ==================================================== */}

        <div className="grid grid-cols-1 gap-5 lg:grid-cols-3">
          <Card className="lg:col-span-2">
            <CardHeader
              title="Collections performance"
              action={
                <span className="text-xs text-slate-400">
                  Current portfolio
                </span>
              }
            />

            <CardBody>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
                <FinancialMetric
                  label="Total collected"
                  value={fc(portfolio.totalCollected)}
                  description="All recorded collections"
                  tone="navy"
                />

                <FinancialMetric
                  label="This month"
                  value={fc(portfolio.collectedThisMonth)}
                  description="Collections in current month"
                  tone="yellow"
                />

                <FinancialMetric
                  label="Outstanding"
                  value={fc(portfolio.outstandingBalance)}
                  description="Current balance"
                  tone="slate"
                />

                <FinancialMetric
                  label="Overdue loans"
                  value={fn(portfolio.overdueLoans)}
                  description="Loans requiring follow-up"
                  tone="red"
                />
              </div>
            </CardBody>
          </Card>

          {/* ==================================================
              QUICK ACTIONS
              ================================================== */}

          <Card>
            <CardHeader title="Quick actions" />

            <CardBody>
              <div className="space-y-2">
                <QuickAction
                  icon="💼"
                  title="Manage Loans"
                  description="Review loan portfolio"
                  onClick={() => router.push("/dashboard/loans")}
                />

                <QuickAction
                  icon="👥"
                  title="Borrowers"
                  description="Manage customer profiles"
                  onClick={() => router.push("/dashboard/borrowers")}
                  tone="yellow"
                />

                <QuickAction
                  icon="💳"
                  title="Payments"
                  description="Track collections"
                  onClick={() => router.push("/dashboard/payments")}
                />

                <QuickAction
                  icon="📑"
                  title="Reports"
                  description="Review portfolio reports"
                  onClick={() => router.push("/dashboard/reports")}
                />
              </div>
            </CardBody>
          </Card>
        </div>

        {/* ====================================================
            RECENT LOANS
            ==================================================== */}

        <Card>
          <CardHeader
            title="Recent loan applications"
            action={
              <Button
                variant="ghost"
                size="sm"
                onClick={() => router.push("/dashboard/loans")}
              >
                See all →
              </Button>
            }
          />

          <Table>
            <Thead>
              <tr>
                <Th>Reference</Th>
                <Th>Borrower</Th>
                <Th>Type</Th>
                <Th>Amount</Th>
                <Th>Rate</Th>
                <Th>Risk</Th>
                <Th>Status</Th>
                <Th>Applied</Th>
              </tr>
            </Thead>

            <Tbody>
              {recentLoans.length === 0 ? (
                <Tr>
                  <Td colSpan={8} className="py-14 text-center text-slate-400">
                    <div className="flex flex-col items-center">
                      <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-[#EEF3F9] text-xl">
                        💼
                      </div>

                      <span className="mt-3 text-sm font-semibold text-slate-600">
                        No recent loan applications
                      </span>

                      <span className="mt-1 text-xs text-slate-400">
                        New applications will appear here.
                      </span>
                    </div>
                  </Td>
                </Tr>
              ) : (
                recentLoans.map((loan: Loan) => (
                  <Tr
                    key={loan.id}
                    onClick={() => router.push(`/dashboard/loans/${loan.id}`)}
                    className="cursor-pointer transition hover:bg-[#F8FAFC]"
                  >
                    {/* Reference */}

                    <Td>
                      <code className="rounded-md bg-[#EEF3F9] px-2 py-1 text-[11px] font-semibold text-[#0B1F3A]">
                        {loan.referenceNumber || "—"}
                      </code>
                    </Td>

                    {/* Borrower */}

                    <Td>
                      <div className="flex items-center gap-3">
                        <BorrowerAvatar
                          firstName={loan.borrower?.firstName}
                          lastName={loan.borrower?.lastName}
                          borrowerName={loan.borrowerName}
                        />

                        <div className="min-w-0">
                          <div className="truncate text-sm font-semibold text-slate-900">
                            {loan.borrowerName ||
                              (loan.borrower?.firstName || loan.borrower?.lastName
                                ? `${loan.borrower?.firstName || ""} ${
                                    loan.borrower?.lastName || ""
                                  }`.trim()
                                : "Unnamed borrower")}
                          </div>

                          <div className="truncate text-xs text-slate-400">
                            {loan.borrower?.nationalId ||
                              loan.borrower?.email ||
                              (loan.borrowerId ? `Borrower #${loan.borrowerId}` : "No identifier")}
                          </div>
                        </div>
                      </div>
                    </Td>

                    {/* Type */}

                    <Td>
                      <span className="text-xs font-medium text-slate-600">
                        {LOAN_TYPE_META[loan.loanType]?.icon}{" "}
                        {LOAN_TYPE_META[loan.loanType]?.label ??
                          loan.loanType ??
                          "—"}
                      </span>
                    </Td>

                    {/* Amount */}

                    <Td>
                      <span className="font-bold text-slate-900">
                        {fc(loan.amount)}
                      </span>
                    </Td>

                    {/* Rate */}

                    <Td>
                      <span className="text-sm text-slate-600">
                        {hasValue(loan.interestRate)
                          ? `${loan.interestRate}%`
                          : "—"}
                      </span>
                    </Td>

                    {/* Risk */}

                    <Td>
                      {loan.riskCategory ? (
                        <RiskBadge
                          category={loan.riskCategory}
                          score={loan.riskScore}
                        />
                      ) : (
                        <span className="text-slate-400">—</span>
                      )}
                    </Td>

                    {/* Status */}

                    <Td>
                      {loan.status ? (
                        <StatusBadge status={loan.status} />
                      ) : (
                        <span className="text-slate-400">—</span>
                      )}
                    </Td>

                    {/* Applied */}

                    <Td className="whitespace-nowrap text-xs text-slate-400">
                      {loan.startDate || loan.createdAt
                        ? formatDate(loan.startDate || loan.createdAt, locale)
                        : "—"}
                    </Td>
                  </Tr>
                ))
              )}
            </Tbody>
          </Table>
        </Card>

        {/* ====================================================
            CONTROL FOOTER
            ==================================================== */}

        <div className="flex flex-col gap-2 border-t border-slate-200 px-1 pt-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p className="text-xs font-semibold text-[#0B1F3A]">
              Noble Loan Solutions
            </p>

            <p className="mt-0.5 text-[11px] text-slate-400">
              Loan Management Platform
            </p>
          </div>

          <p className="text-[11px] text-slate-400">
            Dashboard figures are supplied by the organization lending system.
          </p>
        </div>
      </div>
    </div>
  );
}

/* ============================================================
   FINANCIAL METRIC
   ============================================================ */

function FinancialMetric({
  label,
  value,
  description,
  tone,
}: {
  label: string;
  value: string;
  description: string;
  tone: "navy" | "yellow" | "slate" | "red";
}) {
  const styles = {
    navy: {
      wrapper: "border-[#DCE5F0] bg-[#F8FAFC]",
      label: "text-[#16365F]",
      value: "text-[#0B1F3A]",
    },

    yellow: {
      wrapper: "border-[#F4C430]/30 bg-[#FFFDF2]",
      label: "text-[#806200]",
      value: "text-[#665000]",
    },

    slate: {
      wrapper: "border-slate-200 bg-slate-50",
      label: "text-slate-600",
      value: "text-slate-900",
    },

    red: {
      wrapper: "border-red-100 bg-red-50",
      label: "text-red-600",
      value: "text-red-900",
    },
  };

  const style = styles[tone];

  return (
    <div className={`rounded-xl border p-4 ${style.wrapper}`}>
      <p className={`text-xs font-semibold ${style.label}`}>{label}</p>

      <p
        className={`mt-1 text-lg font-extrabold tracking-tight ${style.value}`}
      >
        {value}
      </p>

      <p className="mt-1 text-[10px] text-slate-400">{description}</p>
    </div>
  );
}

/* ============================================================
   QUICK ACTION
   ============================================================ */

function QuickAction({
  icon,
  title,
  description,
  onClick,
  tone = "navy",
}: {
  icon: string;
  title: string;
  description: string;
  onClick: () => void;
  tone?: "navy" | "yellow";
}) {
  const isYellow = tone === "yellow";

  return (
    <button
      type="button"
      onClick={onClick}
      className={`group flex w-full items-center gap-3 rounded-xl border px-4 py-3 text-left transition-all duration-200 ${
        isYellow
          ? "border-gray-100 hover:border-[#F4C430]/50 hover:bg-[#FFFDF2]"
          : "border-gray-100 hover:border-[#C7D5E5] hover:bg-[#F8FAFC]"
      }`}
    >
      <span
        className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-sm transition-transform group-hover:scale-105 ${
          isYellow ? "bg-[#FFF3B0]" : "bg-[#E1EAF4]"
        }`}
      >
        {icon}
      </span>

      <span className="min-w-0 flex-1">
        <span className="block text-sm font-semibold text-slate-900">
          {title}
        </span>

        <span className="mt-0.5 block truncate text-xs text-slate-400">
          {description}
        </span>
      </span>

      <span className="text-slate-300 transition-transform group-hover:translate-x-0.5">
        →
      </span>
    </button>
  );
}

/* ============================================================
   BORROWER AVATAR
   ============================================================ */

function BorrowerAvatar({
  firstName,
  lastName,
  borrowerName,
}: {
  firstName?: string | null;
  lastName?: string | null;
  borrowerName?: string | null;
}) {
  const directInitials = `${firstName?.charAt(0) || ""}${lastName?.charAt(0) || ""}`
    .trim()
    .toUpperCase();

  const parts = (borrowerName || "").trim().split(/\s+/).filter(Boolean);
  const nameInitials =
    parts.length >= 2
      ? `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase()
      : parts[0]?.slice(0, 2).toUpperCase();

  const initials = directInitials || nameInitials || "B";

  return (
    <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-[#E1EAF4] text-xs font-bold text-[#0B1F3A] ring-2 ring-white">
      {initials}
    </div>
  );
}

/* ============================================================
   EMPTY CHART
   ============================================================ */

function ChartEmptyState({
  title,
  description,
}: {
  title: string;
  description: string;
}) {
  return (
    <div className="flex h-[300px] flex-col items-center justify-center text-center">
      <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-[#EEF3F9] text-xl">
        ▦
      </div>

      <p className="mt-3 text-sm font-semibold text-slate-600">{title}</p>

      <p className="mt-1 max-w-xs text-xs leading-5 text-slate-400">
        {description}
      </p>
    </div>
  );
}

/* ============================================================
   DASHBOARD SKELETON
   ============================================================ */

function DashboardSkeleton() {
  return (
    <div className="min-h-[70vh] animate-pulse space-y-6 bg-[#F8FAFC] pb-10">
      <div className="h-52 rounded-2xl bg-[#0B1F3A]" />

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {[1, 2, 3, 4].map((item) => (
          <div
            key={item}
            className="h-32 rounded-2xl border border-slate-200 bg-white"
          />
        ))}
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {[1, 2, 3, 4].map((item) => (
          <div
            key={item}
            className="h-32 rounded-2xl border border-slate-200 bg-white"
          />
        ))}
      </div>

      <div className="grid grid-cols-1 gap-5 xl:grid-cols-3">
        <div className="h-96 rounded-2xl border border-slate-200 bg-white xl:col-span-2" />

        <div className="h-96 rounded-2xl border border-slate-200 bg-white" />
      </div>

      <div className="h-80 rounded-2xl border border-slate-200 bg-white" />
    </div>
  );
}
