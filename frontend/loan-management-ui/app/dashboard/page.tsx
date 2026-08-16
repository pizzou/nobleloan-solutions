"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  PieChart,
  Pie,
  Cell,
} from "recharts";

import { loanApi } from "@/services/api";
import { DashboardStats, Loan } from "@/types";
import { Button } from "@/components/ui/Button";
import { Card, CardBody, CardHeader, StatCard } from "@/components/ui/Card";
import { StatusBadge, RiskBadge } from "@/components/ui/Badge";
import { PageSpinner } from "@/components/ui/Skeleton";
import { formatCurrency, formatDate, formatNumber } from "@/lib/utils";
import { useAuth } from "@/hooks/useAuth";
import {
  IconAlertTriangle,
  IconCard,
  IconCheckCircle,
  IconClock,
  IconCoins,
  IconFileText,
  IconSend,
} from "@/components/ui/Icons";

const CHART_COLORS = ["#0B1F3A", "#0F766E", "#C8A84E", "#B42318", "#64748B"];

const n = (value: unknown) => {
  const result = Number(value ?? 0);
  return Number.isFinite(result) ? result : 0;
};

function borrowerName(loan: Loan) {
  const first = loan.borrower?.firstName?.trim() || "";
  const last = loan.borrower?.lastName?.trim() || "";
  return `${first} ${last}`.trim() || "Unnamed borrower";
}

function money(value: unknown, currency: string, locale: string) {
  return formatCurrency(n(value), currency, locale);
}

function statusData(stats: DashboardStats) {
  return [
    { name: "Active", value: n(stats.activeLoans) },
    { name: "Pending", value: n(stats.pendingLoans) },
    { name: "Overdue", value: n(stats.overdueLoans) },
    { name: "Defaulted", value: n(stats.defaultedLoans) },
    { name: "Paid", value: n(stats.completedLoans) },
  ].filter((row) => row.value > 0);
}

function PerformanceBar({
  label,
  value,
  total,
  currency,
  locale,
}: {
  label: string;
  value: number;
  total: number;
  currency: string;
  locale: string;
}) {
  const percentage =
    total > 0 ? Math.min(100, Math.max(0, (value / total) * 100)) : 0;
  return (
    <div>
      <div className="mb-2 flex items-center justify-between gap-4">
        <span className="text-xs font-bold text-slate-700">{label}</span>
        <span className="text-xs font-black tabular-nums text-slate-950">
          {money(value, currency, locale)}
        </span>
      </div>
      <div className="h-2 overflow-hidden rounded-full bg-slate-100">
        <div
          className="h-full rounded-full bg-[#0B1F3A] transition-all"
          style={{ width: `${percentage}%` }}
        />
      </div>
      <div className="mt-1 text-[10px] text-slate-400">
        {percentage.toFixed(1)}% of gross disbursed
      </div>
    </div>
  );
}

export default function DashboardPage() {
  const router = useRouter();
  const { currency, locale, user } = useAuth();
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const result = await loanApi.dashboard();
      setStats(result as DashboardStats);
    } catch (err: any) {
      setError(err?.message || "Unable to retrieve the current portfolio.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const distribution = useMemo(() => (stats ? statusData(stats) : []), [stats]);
  const activeExposure = n(stats?.outstandingBalance);
  const grossDisbursed = n(stats?.totalDisbursed);
  const collected = n(stats?.totalCollected);
  const collectionCoverage =
    grossDisbursed > 0 ? Math.min(100, (collected / grossDisbursed) * 100) : 0;
  const par = n(stats?.portfolioAtRiskPct);
  const riskLabel =
    par <= 3
      ? "Controlled"
      : par <= 5
        ? "Watch"
        : par <= 10
          ? "Elevated"
          : "Critical";
  const today = new Intl.DateTimeFormat(locale || "en-RW", {
    weekday: "long",
    day: "numeric",
    month: "long",
    year: "numeric",
  }).format(new Date());

  if (loading) return <PageSpinner />;

  if (error || !stats) {
    return (
      <div className="premium-page grid min-h-[calc(100vh-72px)] place-items-center p-6">
        <div className="premium-card w-full max-w-lg p-8 text-center">
          <div className="mx-auto grid h-14 w-14 place-items-center rounded-2xl bg-red-50 text-red-700">
            <IconAlertTriangle className="h-7 w-7" />
          </div>
          <p className="premium-eyebrow mt-5">Portfolio service</p>
          <h1 className="mt-2 text-2xl font-black tracking-tight text-[#07152A]">
            Dashboard unavailable
          </h1>
          <p className="mx-auto mt-2 max-w-md text-sm leading-6 text-slate-500">
            {error || "The server did not return portfolio statistics."}
          </p>
          <Button className="mt-6" onClick={() => void load()}>
            Try again
          </Button>
        </div>
      </div>
    );
  }

  return (
    <main className="premium-page pb-12">
      <div className="mx-auto max-w-[1680px] space-y-6 px-4 py-6 sm:px-6 lg:px-8">
        <section className="relative overflow-hidden rounded-[26px] border border-[#173252] bg-[#07152A] px-6 py-7 text-white shadow-[0_28px_80px_rgba(7,21,42,.18)] sm:px-8 lg:px-10">
          <div className="absolute -right-24 -top-28 h-80 w-80 rounded-full border border-[#C8A84E]/10 bg-[#C8A84E]/5" />
          <div className="absolute -bottom-40 right-48 h-80 w-80 rounded-full border border-teal-300/10 bg-teal-400/5" />
          <div className="relative flex flex-col gap-7 lg:flex-row lg:items-end lg:justify-between">
            <div>
              <div className="flex flex-wrap items-center gap-3">
                <span className="premium-kicker">
                  Executive portfolio command centre
                </span>
                <span className="h-1 w-1 rounded-full bg-[#C8A84E]" />
                <span className="text-[10px] font-bold uppercase tracking-[.14em] text-slate-400">
                  {user?.organizationName || "Organization"}
                </span>
              </div>
              <h1 className="mt-3 text-3xl font-black tracking-[-.04em] sm:text-4xl">
                Good to see you
                {user?.name ? `, ${user.name.split(" ")[0]}` : ""}.
              </h1>
              <p className="mt-3 max-w-3xl text-sm leading-6 text-slate-300">
                A controlled view of portfolio exposure, collections, credit
                quality and operational attention. Financial values displayed
                here remain authoritative to the lending engine.
              </p>
              <div className="mt-5 flex items-center gap-3 text-[11px] font-semibold text-slate-400">
                <span className="h-2 w-2 rounded-full bg-emerald-400" /> Live
                portfolio snapshot <span className="text-slate-600">•</span>{" "}
                {today}
              </div>
            </div>
            <div className="flex flex-wrap gap-2.5">
              <Button
                variant="secondary"
                onClick={() => router.push("/dashboard/borrowers")}
              >
                Borrowers
              </Button>
              <Button
                variant="secondary"
                onClick={() => router.push("/dashboard/import")}
              >
                Import
              </Button>
              <Button onClick={() => router.push("/dashboard/loans/new")}>
                New loan
              </Button>
            </div>
          </div>
        </section>

        <section className="grid grid-cols-2 gap-4 xl:grid-cols-4">
          <StatCard
            icon={<IconFileText className="h-5 w-5" />}
            label="Total facilities"
            value={formatNumber(n(stats.totalLoans))}
            sub={`${formatNumber(n(stats.activeLoans))} currently active`}
            color="#0B1F3A"
          />
          <StatCard
            icon={<IconCoins className="h-5 w-5" />}
            label="Outstanding principal"
            value={money(activeExposure, currency, locale)}
            sub="Current portfolio exposure"
            color="#0F766E"
          />
          <StatCard
            icon={<IconSend className="h-5 w-5" />}
            label="Gross disbursed"
            value={money(grossDisbursed, currency, locale)}
            sub="Eligible lifetime disbursements"
            color="#16365F"
          />
          <StatCard
            icon={<IconCheckCircle className="h-5 w-5" />}
            label="Portfolio at risk"
            value={`${par.toFixed(2)}%`}
            sub={`${riskLabel} monitoring position`}
            color={par > 5 ? "#B42318" : "#C8A84E"}
          />
        </section>

        <section className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          <StatCard
            icon={<IconClock className="h-5 w-5" />}
            label="Pending review"
            value={formatNumber(n(stats.pendingLoans))}
            sub="Applications awaiting decision"
            color="#C8A84E"
          />
          <StatCard
            icon={<IconAlertTriangle className="h-5 w-5" />}
            label="Overdue loans"
            value={formatNumber(n(stats.overdueLoans))}
            sub={`${formatNumber(n(stats.latePaymentsCount))} late payments`}
            color="#B42318"
          />
          <StatCard
            icon={<IconCard className="h-5 w-5" />}
            label="Collections this month"
            value={money(stats.collectedThisMonth, currency, locale)}
            sub={`Lifetime collected ${money(collected, currency, locale)}`}
            color="#0F766E"
          />
          <StatCard
            icon={<IconCheckCircle className="h-5 w-5" />}
            label="Completed facilities"
            value={formatNumber(n(stats.completedLoans))}
            sub={`${formatNumber(n(stats.defaultedLoans))} defaulted`}
            color="#64748B"
          />
        </section>

        {n(stats.pendingLoans) > 0 ||
        n(stats.overdueLoans) > 0 ||
        n(stats.defaultedLoans) > 0 ? (
          <section className="premium-card border-amber-200 bg-[linear-gradient(90deg,#fffdf6,#fff)]">
            <CardBody>
              <div className="flex flex-col gap-5 lg:flex-row lg:items-center lg:justify-between">
                <div>
                  <div className="flex items-center gap-2">
                    <span className="h-2 w-2 rounded-full bg-[#C8A84E]" />
                    <p className="text-sm font-black text-[#07152A]">
                      Management attention
                    </p>
                  </div>
                  <p className="mt-1 text-xs leading-5 text-slate-500">
                    Priority items should be reviewed from their authoritative
                    operational workspaces.
                  </p>
                  <div className="mt-4 flex flex-wrap gap-2">
                    {n(stats.pendingLoans) > 0 ? (
                      <span className="premium-badge border border-amber-100 bg-amber-50 text-amber-800">
                        {formatNumber(n(stats.pendingLoans))} pending
                      </span>
                    ) : null}
                    {n(stats.overdueLoans) > 0 ? (
                      <span className="premium-badge border border-red-100 bg-red-50 text-red-800">
                        {formatNumber(n(stats.overdueLoans))} overdue
                      </span>
                    ) : null}
                    {n(stats.defaultedLoans) > 0 ? (
                      <span className="premium-badge border border-red-100 bg-red-50 text-red-800">
                        {formatNumber(n(stats.defaultedLoans))} defaulted
                      </span>
                    ) : null}
                  </div>
                </div>
                <div className="flex flex-wrap gap-2">
                  <Button onClick={() => router.push("/dashboard/loans")}>
                    Review portfolio
                  </Button>
                  <Button
                    variant="secondary"
                    onClick={() => router.push("/dashboard/collections")}
                  >
                    Open collections
                  </Button>
                </div>
              </div>
            </CardBody>
          </section>
        ) : null}

        <div className="grid gap-5 xl:grid-cols-3">
          <Card className="xl:col-span-2">
            <CardHeader
              title="Portfolio position"
              subtitle="Backend-authoritative balances presented for executive review"
            />
            <CardBody>
              <div className="grid gap-7 lg:grid-cols-[1.2fr_.8fr]">
                <div className="space-y-7">
                  <PerformanceBar
                    label="Outstanding principal"
                    value={activeExposure}
                    total={grossDisbursed}
                    currency={currency}
                    locale={locale}
                  />
                  <PerformanceBar
                    label="Lifetime collections"
                    value={collected}
                    total={grossDisbursed}
                    currency={currency}
                    locale={locale}
                  />
                  <div>
                    <div className="mb-2 flex items-center justify-between">
                      <span className="text-xs font-bold text-slate-700">
                        Collection coverage
                      </span>
                      <span className="text-xs font-black text-[#07152A]">
                        {collectionCoverage.toFixed(1)}%
                      </span>
                    </div>
                    <div className="h-2 overflow-hidden rounded-full bg-slate-100">
                      <div
                        className="h-full rounded-full bg-[#0F766E]"
                        style={{ width: `${collectionCoverage}%` }}
                      />
                    </div>
                    <p className="mt-1 text-[10px] text-slate-400">
                      Display ratio of lifetime collected against gross
                      disbursed.
                    </p>
                  </div>
                </div>
                <div className="min-h-[220px]">
                  {distribution.length ? (
                    <ResponsiveContainer width="100%" height={230}>
                      <PieChart>
                        <Pie
                          data={distribution}
                          dataKey="value"
                          nameKey="name"
                          innerRadius={58}
                          outerRadius={82}
                          paddingAngle={3}
                          stroke="none"
                        >
                          {distribution.map((row, index) => (
                            <Cell
                              key={row.name}
                              fill={CHART_COLORS[index % CHART_COLORS.length]}
                            />
                          ))}
                        </Pie>
                        <Tooltip
                          formatter={(value: number) => formatNumber(value)}
                        />
                      </PieChart>
                    </ResponsiveContainer>
                  ) : (
                    <div className="grid h-full min-h-[220px] place-items-center text-xs text-slate-400">
                      No status distribution is available.
                    </div>
                  )}
                  <div className="flex flex-wrap justify-center gap-x-4 gap-y-2">
                    {distribution.map((row, index) => (
                      <div
                        key={row.name}
                        className="flex items-center gap-1.5 text-[10px] font-bold text-slate-500"
                      >
                        <span
                          className="h-2 w-2 rounded-full"
                          style={{
                            background:
                              CHART_COLORS[index % CHART_COLORS.length],
                          }}
                        />
                        {row.name} {formatNumber(row.value)}
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            </CardBody>
          </Card>

          <Card>
            <CardHeader
              title="Portfolio quality"
              subtitle="Current facility distribution"
            />
            <CardBody>
              <ResponsiveContainer width="100%" height={250}>
                <BarChart
                  data={distribution}
                  layout="vertical"
                  margin={{ left: 4, right: 12, top: 5, bottom: 5 }}
                >
                  <CartesianGrid horizontal={false} stroke="#edf1f5" />
                  <XAxis type="number" allowDecimals={false} hide />
                  <YAxis
                    type="category"
                    dataKey="name"
                    width={70}
                    tick={{ fontSize: 10, fill: "#64748b", fontWeight: 700 }}
                    axisLine={false}
                    tickLine={false}
                  />
                  <Tooltip cursor={{ fill: "#f8fafc" }} />
                  <Bar
                    dataKey="value"
                    radius={[0, 6, 6, 0]}
                    fill="#0B1F3A"
                    barSize={18}
                  />
                </BarChart>
              </ResponsiveContainer>
            </CardBody>
          </Card>
        </div>

        <div className="grid gap-5 xl:grid-cols-[1.35fr_.65fr]">
          <Card>
            <CardHeader
              title="Recent lending activity"
              subtitle="Latest facilities returned by the lending engine"
              action={
                <Link
                  href="/dashboard/loans"
                  className="text-[11px] font-black text-[#0F766E]"
                >
                  View portfolio →
                </Link>
              }
            />
            <div className="overflow-x-auto">
              <table className="premium-table min-w-[760px] w-full text-sm">
                <thead>
                  <tr>
                    <th>Facility</th>
                    <th>Borrower</th>
                    <th>Status</th>
                    <th>Principal</th>
                    <th>Outstanding</th>
                    <th>Next due</th>
                  </tr>
                </thead>
                <tbody>
                  {(stats.recentLoans || []).slice(0, 8).map((loan) => (
                    <tr key={loan.id}>
                      <td>
                        <Link
                          href={`/dashboard/loans/${loan.id}`}
                          className="font-black text-[#07152A] hover:text-[#0F766E]"
                        >
                          {loan.referenceNumber}
                        </Link>
                        <div className="mt-1 text-[10px] text-slate-400">
                          {loan.createdAt
                            ? formatDate(loan.createdAt, locale)
                            : "—"}
                        </div>
                      </td>
                      <td>
                        <div className="font-semibold text-slate-800">
                          {borrowerName(loan)}
                        </div>
                        <div className="mt-1 text-[10px] text-slate-400">
                          {loan.borrower?.phone ||
                            loan.borrower?.nationalId ||
                            "—"}
                        </div>
                      </td>
                      <td>
                        <div className="flex flex-wrap gap-1.5">
                          <StatusBadge status={loan.status} />
                          {loan.riskCategory ? (
                            <RiskBadge category={loan.riskCategory} />
                          ) : null}
                        </div>
                      </td>
                      <td className="font-bold tabular-nums text-slate-900">
                        {money(loan.amount, currency, locale)}
                      </td>
                      <td className="font-bold tabular-nums text-slate-900">
                        {money(loan.outstandingBalance, currency, locale)}
                      </td>
                      <td>
                        <div className="font-semibold text-slate-700">
                          {loan.nextDueDate
                            ? formatDate(loan.nextDueDate, locale)
                            : "Not scheduled"}
                        </div>
                        {loan.daysOverdue && loan.daysOverdue > 0 ? (
                          <div className="mt-1 text-[10px] font-bold text-red-600">
                            {loan.daysOverdue} days overdue
                          </div>
                        ) : null}
                      </td>
                    </tr>
                  ))}
                  {!stats.recentLoans?.length ? (
                    <tr>
                      <td
                        colSpan={6}
                        className="py-14 text-center text-xs text-slate-400"
                      >
                        No recent lending activity is available.
                      </td>
                    </tr>
                  ) : null}
                </tbody>
              </table>
            </div>
          </Card>

          <Card>
            <CardHeader
              title="Executive actions"
              subtitle="Controlled operational shortcuts"
            />
            <CardBody>
              <div className="space-y-2">
                {[
                  [
                    "Create a loan application",
                    "/dashboard/loans/new",
                    "Start a new credit facility",
                  ],
                  [
                    "Review approvals",
                    "/dashboard/approvals",
                    "Maker-checker workflow",
                  ],
                  [
                    "Open collections",
                    "/dashboard/collections",
                    "Prioritize overdue exposure",
                  ],
                  [
                    "Reconcile imports",
                    "/dashboard/import",
                    "Validate historical portfolios",
                  ],
                  [
                    "Financial reports",
                    "/dashboard/reports",
                    "Statements and management reporting",
                  ],
                ].map(([title, href, copy]) => (
                  <Link
                    key={href}
                    href={href}
                    className="group flex items-center gap-3 rounded-xl border border-slate-100 bg-slate-50/60 p-3 transition hover:border-slate-200 hover:bg-white"
                  >
                    <span className="grid h-9 w-9 place-items-center rounded-xl bg-[#07152A] text-xs font-black text-[#C8A84E]">
                      →
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block text-xs font-black text-slate-800 group-hover:text-[#0F766E]">
                        {title}
                      </span>
                      <span className="mt-0.5 block text-[10px] text-slate-400">
                        {copy}
                      </span>
                    </span>
                  </Link>
                ))}
              </div>
            </CardBody>
          </Card>
        </div>

        <section className="rounded-2xl border border-slate-200 bg-white px-5 py-4 shadow-sm">
          <div className="flex flex-col gap-2 lg:flex-row lg:items-center lg:justify-between">
            <div>
              <div className="premium-eyebrow">Lending policy display</div>
              <div className="mt-1 text-xs font-bold text-slate-700">
                5% monthly interest · 5% monthly management fee · 2% one-time
                processing fee
              </div>
            </div>
            <div className="text-[10px] leading-5 text-slate-500 lg:max-w-2xl lg:text-right">
              The dashboard does not recreate the repayment engine. Actual
              calendar-day accrual, principal reduction and schedule
              recalculation remain backend responsibilities.
            </div>
          </div>
        </section>
      </div>
    </main>
  );
}
