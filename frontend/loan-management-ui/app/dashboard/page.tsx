"use client";

import { useEffect, useState } from "react";
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
   NOBLE LOAN SOLUTIONS BRAND
   ============================================================ */

const NAVY = "#0B1F3A";
const NAVY_LIGHT = "#16365F";
const NAVY_DARK = "#07152A";

const YELLOW = "#F4C430";
const YELLOW_DARK = "#C99A00";

const COLORS = [NAVY, YELLOW, "#2563EB", "#F59E0B", "#1E40AF", "#D97706"];

/* ============================================================
   DASHBOARD
   ============================================================ */

export default function DashboardPage() {
  const router = useRouter();

  const { currency, locale, user } = useAuth();

  const [stats, setStats] = useState<DashboardStats | null>(null);

  const [loading, setLoading] = useState(true);

  const [error, setError] = useState("");

  /* ==========================================================
     LOAD DASHBOARD
     ========================================================== */

  useEffect(() => {
    let mounted = true;

    const cacheKey = user?.userId
      ? `noble-dashboard:${user.organizationId}:${user.userId}:v2`
      : "";

    let hasCachedDashboard = false;

    /*
     * Use a very short-lived, user-scoped session cache for perceived
     * performance. The API is still called immediately afterwards so
     * financial figures remain fresh.
     */
    if (cacheKey) {
      try {
        const raw = window.sessionStorage.getItem(cacheKey);

        if (raw) {
          const cached = JSON.parse(raw);

          if (
            cached &&
            typeof cached === "object" &&
            typeof cached.cachedAt === "number" &&
            Date.now() - cached.cachedAt < 120_000 &&
            cached.data
          ) {
            setStats(cached.data as DashboardStats);
            setLoading(false);
            hasCachedDashboard = true;
          }
        }
      } catch {
        // Session cache is best-effort only.
      }
    }

    if (!hasCachedDashboard) {
      setLoading(true);
    }

    setError("");

    loanApi
      .dashboard()
      .then((data: DashboardStats) => {
        if (!mounted) return;

        setStats(data);

        if (cacheKey) {
          try {
            window.sessionStorage.setItem(
              cacheKey,
              JSON.stringify({
                cachedAt: Date.now(),
                data,
              }),
            );
          } catch {
            // Cache storage is best-effort only.
          }
        }
      })
      .catch((e: any) => {
        if (!mounted) return;

        /*
         * If a cached dashboard is already visible, keep it on screen
         * instead of replacing useful information with an error state.
         */
        if (!hasCachedDashboard) {
          setError(e?.message || "Unable to load dashboard information.");
        }
      })
      .finally(() => {
        if (!mounted) return;

        setLoading(false);
      });

    return () => {
      mounted = false;
    };
  }, [user?.organizationId, user?.userId]);

  /* ==========================================================
     HELPERS
     ========================================================== */

  const fc = (value?: number | null) =>
    formatCurrency(Number(value || 0), currency, locale);

  const today = new Date().toLocaleDateString(locale || "en-US", {
    weekday: "long",
    year: "numeric",
    month: "long",
    day: "numeric",
  });

  /* ==========================================================
     LOADING
     ========================================================== */

  if (loading) {
    return (
      <div className="space-y-6 pb-12" aria-busy="true">
        <div className="overflow-hidden rounded-[26px] bg-gradient-to-br from-[#07152A] via-[#0B1F3A] to-[#16365F] p-7 shadow-[0_24px_60px_rgba(7,21,42,0.16)]">
          <div className="h-3 w-40 animate-pulse rounded-full bg-white/15" />
          <div className="mt-5 h-8 w-2/3 max-w-md animate-pulse rounded-lg bg-white/15" />
          <div className="mt-3 h-3 w-full max-w-xl animate-pulse rounded-full bg-white/10" />
          <div className="mt-7 h-10 w-32 animate-pulse rounded-xl bg-white/10" />
        </div>

        <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          {Array.from({ length: 4 }).map((_, index) => (
            <div
              key={`metric-skeleton-${index}`}
              className="rounded-2xl border border-slate-200/80 bg-white p-6 shadow-sm"
            >
              <div className="h-11 w-11 animate-pulse rounded-xl bg-slate-100" />
              <div className="mt-5 h-2.5 w-24 animate-pulse rounded-full bg-slate-100" />
              <div className="mt-3 h-7 w-32 animate-pulse rounded-lg bg-slate-100" />
              <div className="mt-2 h-2.5 w-20 animate-pulse rounded-full bg-slate-100" />
            </div>
          ))}
        </div>

        <div className="grid grid-cols-1 gap-5 xl:grid-cols-3">
          <div className="h-[330px] animate-pulse rounded-2xl border border-slate-200/80 bg-white xl:col-span-2" />
          <div className="h-[330px] animate-pulse rounded-2xl border border-slate-200/80 bg-white" />
        </div>
      </div>
    );
  }

  /* ==========================================================
     ERROR
     ========================================================== */

  if (error) {
    return (
      <div className="min-h-[60vh] flex items-center justify-center bg-gray-50 px-4">
        <div className="max-w-md w-full bg-white border border-red-200 rounded-2xl shadow-sm p-8 text-center">
          <div className="w-12 h-12 mx-auto rounded-full bg-red-50 flex items-center justify-center text-xl">
            ⚠️
          </div>

          <h2 className="mt-4 text-lg font-bold text-gray-900">
            Unable to load dashboard
          </h2>

          <p className="mt-2 text-sm text-gray-500">{error}</p>

          <Button className="mt-6" onClick={() => window.location.reload()}>
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
      <div className="min-h-[60vh] flex items-center justify-center bg-gray-50">
        <div className="text-center">
          <div className="text-4xl mb-3">📊</div>

          <h2 className="text-lg font-bold text-gray-900">No dashboard data</h2>

          <p className="text-sm text-gray-500 mt-1">
            There is currently no portfolio information available.
          </p>
        </div>
      </div>
    );
  }

  /* ==========================================================
     DERIVED DATA
     ========================================================== */

  const pieData = [
    {
      name: "Active",
      value: Number(stats.activeLoans || 0),
    },
    {
      name: "Pending",
      value: Number(stats.pendingLoans || 0),
    },
    {
      name: "Overdue",
      value: Number(stats.overdueLoans || 0),
    },
    {
      name: "Paid",
      value: Number(stats.completedLoans || 0),
    },
    {
      name: "Defaulted",
      value: Number(stats.defaultedLoans || 0),
    },
  ].filter((item) => item.value > 0);

  const typeData = (stats.loanTypeBreakdown || []).map((item) => ({
    name: LOAN_TYPE_META[String(item.type)]?.label ?? String(item.type),

    count: Number(item.count || 0),

    amount: Number(item.amount || 0),
  }));

  const totalDisbursed = Number(stats.totalDisbursed || 0);

  const outstandingBalance = Number(stats.outstandingBalance || 0);

  const calculatedPar =
    totalDisbursed > 0 ? (outstandingBalance / totalDisbursed) * 100 : 0;

  const portfolioAtRisk = Number(
    stats.portfolioAtRiskPct ?? calculatedPar ?? 0,
  );

  const recentLoans = stats.recentLoans || [];

  const collectionRate =
    totalDisbursed > 0
      ? Math.min(
          100,
          (Number(stats.totalCollected || 0) / totalDisbursed) * 100,
        )
      : 0;

  const outstandingRatio =
    totalDisbursed > 0
      ? Math.min(100, (outstandingBalance / totalDisbursed) * 100)
      : 0;

  const healthyPortfolioPct = Math.max(0, Math.min(100, 100 - portfolioAtRisk));

  /* ==========================================================
     RENDER
     ========================================================== */

  return (
    <div className="space-y-6 pb-12">
      {/* ======================================================
          HEADER
          ====================================================== */}

      <div className="relative overflow-hidden rounded-[26px] bg-gradient-to-br from-[#07152A] via-[#0B1F3A] to-[#16365F] text-white shadow-[0_24px_60px_rgba(7,21,42,0.18)]">
        {/* Decorative circles */}

        <div className="absolute -right-16 -top-16 w-56 h-56 rounded-full bg-[#F4C430]/20" />

        <div className="absolute right-20 -bottom-20 w-48 h-48 rounded-full bg-[#F4C430]/10" />

        <div className="absolute left-1/2 -top-20 w-40 h-40 rounded-full bg-white/5" />

        <div className="relative px-6 py-7 lg:px-8">
          <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-5">
            <div>
              {/* Brand */}

              <div className="flex flex-wrap items-center gap-2 mb-3">
                <span className="inline-flex items-center px-3 py-1 rounded-full bg-[#F4C430] text-[#07152A] text-xs font-extrabold tracking-wide">
                  NOBLE LOAN SOLUTIONS
                </span>

                <span className="text-blue-100 text-xs">
                  Loan Management Platform
                </span>
              </div>

              <h1 className="text-2xl lg:text-3xl font-extrabold tracking-tight">
                Welcome back
                {user?.name ? `, ${user.name}` : ""}
              </h1>

              <p className="text-blue-100 text-sm mt-1">
                Here is your portfolio overview for today.
              </p>

              <p className="text-blue-200 text-xs mt-3">
                {user?.organizationName || "Organization"} · {today}
              </p>
            </div>

            {/* Header actions */}

            <div className="flex flex-wrap gap-3">
              <Button
                variant="secondary"
                icon="👥"
                onClick={() => router.push("/dashboard/borrowers")}
              >
                Borrowers
              </Button>

              <Button
                variant="secondary"
                icon="＋"
                onClick={() => router.push("/dashboard/loans/new")}
              >
                New Loan
              </Button>

              <Button icon="💼" onClick={() => router.push("/dashboard/loans")}>
                View Portfolio
              </Button>
            </div>
          </div>
        </div>
      </div>

      {/* ======================================================
          EXECUTIVE KPI SECTION
          ====================================================== */}

      <div>
        <div className="flex items-center justify-between mb-3">
          <div>
            <h2 className="text-sm font-bold text-[#0B1F3A] uppercase tracking-wide">
              Portfolio Overview
            </h2>

            <p className="text-xs text-gray-400 mt-1">
              Real-time lending performance
            </p>
          </div>
        </div>

        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          <StatCard
            icon="💼"
            label="Total Loans"
            value={formatNumber(stats.totalLoans || 0)}
            sub={`${formatNumber(stats.activeLoans || 0)} active`}
            color={NAVY}
          />

          <StatCard
            icon="👥"
            label="Total Borrowers"
            value={formatNumber(stats.totalBorrowers || 0)}
            sub="Registered clients"
            color={NAVY_LIGHT}
          />

          <StatCard
            icon="💰"
            label="Total Disbursed"
            value={fc(stats.totalDisbursed)}
            sub="Loan portfolio"
            color={NAVY}
          />

          <StatCard
            icon="📊"
            label="Outstanding"
            value={fc(stats.outstandingBalance)}
            sub="Current balance"
            color={YELLOW_DARK}
          />
        </div>
      </div>

      {/* ======================================================
          ATTENTION / PERFORMANCE KPI
          ====================================================== */}

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          icon="⏳"
          label="Pending Review"
          value={formatNumber(stats.pendingLoans || 0)}
          sub="Awaiting action"
          color={YELLOW_DARK}
        />

        <StatCard
          icon="⚠️"
          label="Overdue Loans"
          value={formatNumber(stats.overdueLoans || 0)}
          sub={`${formatNumber(stats.latePaymentsCount || 0)} late payments`}
          color="#DC2626"
        />

        <StatCard
          icon="✅"
          label="Collected"
          value={fc(stats.totalCollected)}
          sub={`${fc(stats.collectedThisMonth)} this month`}
          color={NAVY}
        />

        <StatCard
          icon="🎯"
          label="Portfolio at Risk"
          value={`${portfolioAtRisk.toFixed(1)}%`}
          sub={portfolioAtRisk > 5 ? "Requires attention" : "Healthy portfolio"}
          color={portfolioAtRisk > 5 ? "#DC2626" : NAVY}
        />
      </div>

      {/* ======================================================
          MANAGEMENT ATTENTION PANEL
          ====================================================== */}

      {(stats.pendingLoans > 0 ||
        stats.overdueLoans > 0 ||
        stats.defaultedLoans > 0) && (
        <Card className="border-[#F4C430]/50 bg-gradient-to-r from-[#FFF9DB] via-[#FFFDF0] to-white">
          <CardBody>
            <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-5">
              <div className="flex items-start gap-4">
                <div className="w-11 h-11 rounded-xl bg-[#FFF3B0] flex items-center justify-center text-xl shrink-0">
                  ⚡
                </div>

                <div>
                  <h3 className="font-bold text-[#0B1F3A]">
                    Management attention required
                  </h3>

                  <p className="text-sm text-gray-500 mt-1">
                    There are portfolio items that may require immediate action.
                  </p>

                  <div className="flex flex-wrap gap-2 mt-3">
                    {stats.pendingLoans > 0 && (
                      <span className="px-2.5 py-1 rounded-full bg-[#FFF3B0] text-[#806200] text-xs font-semibold">
                        {stats.pendingLoans} pending
                      </span>
                    )}

                    {stats.overdueLoans > 0 && (
                      <span className="px-2.5 py-1 rounded-full bg-red-100 text-red-700 text-xs font-semibold">
                        {stats.overdueLoans} overdue
                      </span>
                    )}

                    {stats.defaultedLoans > 0 && (
                      <span className="px-2.5 py-1 rounded-full bg-red-100 text-red-700 text-xs font-semibold">
                        {stats.defaultedLoans} defaulted
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

      {/* ======================================================
          EXECUTIVE HEALTH SNAPSHOT
          ====================================================== */}

      <Card className="border-[#DCE5F0]">
        <CardHeader
          title="Executive portfolio health"
          subtitle="Key ratios for management and credit oversight"
          action={
            <Button
              variant="ghost"
              size="sm"
              onClick={() => router.push("/dashboard/reports")}
            >
              Open reports →
            </Button>
          }
        />

        <CardBody>
          <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
            <div className="rounded-2xl border border-emerald-100 bg-emerald-50/60 p-5">
              <div className="mb-3 flex items-center justify-between">
                <span className="text-[10px] font-bold uppercase tracking-[0.16em] text-emerald-700">
                  Collection efficiency
                </span>
                <span className="text-lg">✓</span>
              </div>
              <div className="text-3xl font-black tracking-tight text-emerald-950">
                {collectionRate.toFixed(1)}%
              </div>
              <p className="mt-1 text-xs leading-5 text-emerald-800/70">
                Total collected relative to the disbursed book.
              </p>
              <div className="mt-4 h-1.5 overflow-hidden rounded-full bg-emerald-100">
                <div
                  className="h-full rounded-full bg-emerald-600 transition-all"
                  style={{ width: `${collectionRate}%` }}
                />
              </div>
            </div>

            <div className="rounded-2xl border border-blue-100 bg-blue-50/60 p-5">
              <div className="mb-3 flex items-center justify-between">
                <span className="text-[10px] font-bold uppercase tracking-[0.16em] text-blue-700">
                  Outstanding ratio
                </span>
                <span className="text-lg">◈</span>
              </div>
              <div className="text-3xl font-black tracking-tight text-blue-950">
                {outstandingRatio.toFixed(1)}%
              </div>
              <p className="mt-1 text-xs leading-5 text-blue-800/70">
                Outstanding balance as a share of the disbursed portfolio.
              </p>
              <div className="mt-4 h-1.5 overflow-hidden rounded-full bg-blue-100">
                <div
                  className="h-full rounded-full bg-blue-700 transition-all"
                  style={{ width: `${outstandingRatio}%` }}
                />
              </div>
            </div>

            <div
              className={`rounded-2xl border p-5 ${
                portfolioAtRisk > 5
                  ? "border-red-100 bg-red-50/60"
                  : "border-slate-200 bg-slate-50"
              }`}
            >
              <div className="mb-3 flex items-center justify-between">
                <span
                  className={`text-[10px] font-bold uppercase tracking-[0.16em] ${
                    portfolioAtRisk > 5 ? "text-red-700" : "text-slate-600"
                  }`}
                >
                  Healthy portfolio
                </span>
                <span className="text-lg">◉</span>
              </div>
              <div
                className={`text-3xl font-black tracking-tight ${
                  portfolioAtRisk > 5 ? "text-red-950" : "text-slate-950"
                }`}
              >
                {healthyPortfolioPct.toFixed(1)}%
              </div>
              <p
                className={`mt-1 text-xs leading-5 ${
                  portfolioAtRisk > 5 ? "text-red-800/70" : "text-slate-600"
                }`}
              >
                Portfolio outside the current at-risk share.
              </p>
              <div
                className={`mt-4 h-1.5 overflow-hidden rounded-full ${
                  portfolioAtRisk > 5 ? "bg-red-100" : "bg-slate-200"
                }`}
              >
                <div
                  className={`h-full rounded-full ${
                    portfolioAtRisk > 5 ? "bg-red-600" : "bg-[#0B1F3A]"
                  }`}
                  style={{ width: `${healthyPortfolioPct}%` }}
                />
              </div>
            </div>
          </div>
        </CardBody>
      </Card>

      {/* ======================================================
          ANALYTICS
          ====================================================== */}

      <div className="grid grid-cols-1 xl:grid-cols-3 gap-5">
        {/* ====================================================
            LOAN TYPE
            ==================================================== */}

        <Card className="xl:col-span-2">
          <CardHeader
            title="Portfolio by Loan Type"
            action={
              <span className="text-xs text-gray-400">Number of loans</span>
            }
          />

          <CardBody>
            {typeData.length > 0 ? (
              <ResponsiveContainer width="100%" height={280}>
                <BarChart
                  data={typeData}
                  barSize={28}
                  margin={{
                    top: 10,
                    right: 10,
                    left: -10,
                    bottom: 5,
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
                      fill: "#6B7280",
                    }}
                    axisLine={false}
                    tickLine={false}
                  />

                  <YAxis
                    allowDecimals={false}
                    tick={{
                      fontSize: 10,
                      fill: "#9CA3AF",
                    }}
                    axisLine={false}
                    tickLine={false}
                  />

                  <Tooltip
                    formatter={(value: number) =>
                      formatNumber(Number(value || 0))
                    }
                    contentStyle={{
                      borderRadius: 12,
                      border: "1px solid #E5E7EB",
                      boxShadow: "0 10px 30px rgba(0,0,0,0.08)",
                    }}
                  />

                  <Bar
                    dataKey="count"
                    fill={NAVY}
                    radius={[6, 6, 0, 0]}
                    name="Loans"
                  />
                </BarChart>
              </ResponsiveContainer>
            ) : (
              <div className="h-[280px] flex flex-col items-center justify-center">
                <div className="text-3xl mb-3">📊</div>

                <p className="text-sm font-medium text-gray-500">
                  No loan data yet
                </p>

                <p className="text-xs text-gray-400 mt-1">
                  Loan analytics will appear here.
                </p>
              </div>
            )}
          </CardBody>
        </Card>

        {/* ====================================================
            STATUS DISTRIBUTION
            ==================================================== */}

        <Card>
          <CardHeader
            title="Loan Status"
            action={<span className="text-xs text-gray-400">Distribution</span>}
          />

          <CardBody>
            {pieData.length > 0 ? (
              <ResponsiveContainer width="100%" height={280}>
                <PieChart>
                  <Pie
                    data={pieData}
                    dataKey="value"
                    nameKey="name"
                    cx="50%"
                    cy="45%"
                    outerRadius={82}
                    innerRadius={48}
                    paddingAngle={3}
                  >
                    {pieData.map((_, index) => (
                      <Cell
                        key={`status-${index}`}
                        fill={COLORS[index % COLORS.length]}
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
              <div className="h-[280px] flex items-center justify-center text-sm text-gray-400">
                No loan status data
              </div>
            )}
          </CardBody>
        </Card>
      </div>

      {/* ======================================================
          COLLECTION PERFORMANCE
          ====================================================== */}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
        <Card className="lg:col-span-2">
          <CardHeader
            title="Collections Performance"
            action={
              <span className="text-xs text-gray-400">Current portfolio</span>
            }
          />

          <CardBody>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
              {/* Total collected */}

              <div className="rounded-xl bg-[#EEF3F9] p-4 border border-[#DCE5F0]">
                <p className="text-xs font-medium text-[#16365F]">
                  Total Collected
                </p>

                <p className="text-lg font-extrabold text-[#0B1F3A] mt-1">
                  {fc(stats.totalCollected)}
                </p>
              </div>

              {/* This month */}

              <div className="rounded-xl bg-[#FFF9DB] p-4 border border-[#F4C430]/30">
                <p className="text-xs font-medium text-[#806200]">This Month</p>

                <p className="text-lg font-extrabold text-[#665000] mt-1">
                  {fc(stats.collectedThisMonth)}
                </p>
              </div>

              {/* Outstanding */}

              <div className="rounded-xl bg-gray-50 p-4 border border-gray-100">
                <p className="text-xs font-medium text-gray-600">Outstanding</p>

                <p className="text-lg font-extrabold text-gray-900 mt-1">
                  {fc(stats.outstandingBalance)}
                </p>
              </div>

              {/* Overdue */}

              <div className="rounded-xl bg-red-50 p-4 border border-red-100">
                <p className="text-xs font-medium text-red-600">Overdue</p>

                <p className="text-lg font-extrabold text-red-900 mt-1">
                  {formatNumber(stats.overdueLoans || 0)}
                </p>
              </div>
            </div>
          </CardBody>
        </Card>

        {/* ====================================================
            QUICK ACTIONS
            ==================================================== */}

        <Card>
          <CardHeader title="Quick Actions" />

          <CardBody>
            <div className="space-y-2">
              {/* Loans */}

              <button
                type="button"
                onClick={() => router.push("/dashboard/loans")}
                className="w-full flex items-center gap-3 rounded-xl border border-gray-100 px-4 py-3 text-left hover:bg-[#EEF3F9] hover:border-[#C7D5E5] transition"
              >
                <span className="w-9 h-9 rounded-lg bg-[#E1EAF4] flex items-center justify-center">
                  💼
                </span>

                <span>
                  <span className="block text-sm font-semibold text-gray-900">
                    Manage Loans
                  </span>

                  <span className="block text-xs text-gray-400">
                    Review loan portfolio
                  </span>
                </span>
              </button>

              {/* Borrowers */}

              <button
                type="button"
                onClick={() => router.push("/dashboard/borrowers")}
                className="w-full flex items-center gap-3 rounded-xl border border-gray-100 px-4 py-3 text-left hover:bg-[#FFF9DB] hover:border-[#F4C430]/50 transition"
              >
                <span className="w-9 h-9 rounded-lg bg-[#FFF3B0] flex items-center justify-center">
                  👥
                </span>

                <span>
                  <span className="block text-sm font-semibold text-gray-900">
                    Borrowers
                  </span>

                  <span className="block text-xs text-gray-400">
                    Manage customer profiles
                  </span>
                </span>
              </button>

              {/* Payments */}

              <button
                type="button"
                onClick={() => router.push("/dashboard/payments")}
                className="w-full flex items-center gap-3 rounded-xl border border-gray-100 px-4 py-3 text-left hover:bg-[#EEF3F9] hover:border-[#C7D5E5] transition"
              >
                <span className="w-9 h-9 rounded-lg bg-[#E1EAF4] flex items-center justify-center">
                  💳
                </span>

                <span>
                  <span className="block text-sm font-semibold text-gray-900">
                    Payments
                  </span>

                  <span className="block text-xs text-gray-400">
                    Track collections
                  </span>
                </span>
              </button>
            </div>
          </CardBody>
        </Card>
      </div>

      {/* ======================================================
          RECENT LOANS
          ====================================================== */}

      <Card>
        <CardHeader
          title="Recent Loan Applications"
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
                <Td className="text-center py-12 text-gray-400">
                  <div className="flex flex-col items-center">
                    <span className="text-3xl mb-2">💼</span>

                    <span className="text-sm font-medium">
                      No loan applications yet
                    </span>

                    <span className="text-xs mt-1">
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
                >
                  {/* Reference */}

                  <Td>
                    <code className="text-xs bg-[#EEF3F9] text-[#0B1F3A] px-2 py-1 rounded-md font-mono">
                      {loan.referenceNumber}
                    </code>
                  </Td>

                  {/* Borrower */}

                  <Td>
                    <div className="flex items-center gap-3">
                      <div className="w-8 h-8 rounded-full bg-[#E1EAF4] text-[#0B1F3A] flex items-center justify-center text-xs font-bold">
                        {(
                          loan.borrower?.firstName?.charAt(0) || ""
                        ).toUpperCase()}

                        {(
                          loan.borrower?.lastName?.charAt(0) || ""
                        ).toUpperCase()}
                      </div>

                      <div>
                        <div className="font-semibold text-gray-900 text-sm">
                          {loan.borrower?.firstName} {loan.borrower?.lastName}
                        </div>

                        <div className="text-xs text-gray-400">
                          {loan.borrower?.nationalId ||
                            loan.borrower?.email ||
                            "Borrower"}
                        </div>
                      </div>
                    </div>
                  </Td>

                  {/* Loan type */}

                  <Td>
                    <span className="text-xs font-medium">
                      {LOAN_TYPE_META[loan.loanType]?.icon}{" "}
                      {LOAN_TYPE_META[loan.loanType]?.label ?? loan.loanType}
                    </span>
                  </Td>

                  {/* Amount */}

                  <Td>
                    <span className="font-bold text-gray-900">
                      {fc(loan.amount)}
                    </span>
                  </Td>

                  {/* Rate */}

                  <Td className="text-gray-500">{loan.interestRate}%</Td>

                  {/* Risk */}

                  <Td>
                    {loan.riskCategory ? (
                      <RiskBadge
                        category={loan.riskCategory}
                        score={loan.riskScore}
                      />
                    ) : (
                      <span className="text-gray-400">—</span>
                    )}
                  </Td>

                  {/* Status */}

                  <Td>
                    <StatusBadge status={loan.status} />
                  </Td>

                  {/* Applied */}

                  <Td className="text-gray-400 text-xs">
                    {formatDate(loan.startDate || loan.createdAt, locale)}
                  </Td>
                </Tr>
              ))
            )}
          </Tbody>
        </Table>
      </Card>

      {/* ======================================================
          FOOTER
          ====================================================== */}

      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 px-1">
        <p className="text-xs text-gray-400">
          Noble Loan Solutions · Loan Management Platform
        </p>

        <p className="text-xs text-gray-400">
          Portfolio data is updated from your organization account.
        </p>
      </div>
    </div>
  );
}
