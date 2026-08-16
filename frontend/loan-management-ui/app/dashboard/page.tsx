"use client";
import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
  PieChart,
  Pie,
  Cell,
} from "recharts";
import { loanApi } from "@/services/api";
import { DashboardStats, Loan } from "@/types";
import { useAuth } from "@/hooks/useAuth";
import { Button } from "@/components/ui/Button";
import { Card, CardBody, CardHeader, StatCard } from "@/components/ui/Card";
import { StatusBadge, RiskBadge } from "@/components/ui/Badge";
import { PageSpinner } from "@/components/ui/Skeleton";
import { formatCurrency, formatDate, formatNumber } from "@/lib/utils";

const COLORS = ["#0b2944", "#087f74", "#c9a227", "#b42318", "#738397"];
const num = (v: unknown) =>
  Number.isFinite(Number(v ?? 0)) ? Number(v ?? 0) : 0;
const borrower = (l: Loan) =>
  `${l.borrower?.firstName || ""} ${l.borrower?.lastName || ""}`.trim() ||
  "Unnamed client";
const money = (v: unknown, c: string, l: string) =>
  formatCurrency(num(v), c, l);
function risk(par: number) {
  return par <= 3
    ? "Controlled"
    : par <= 5
      ? "Watch"
      : par <= 10
        ? "Elevated"
        : "Critical";
}
export default function DashboardPage() {
  const { currency, locale, user } = useAuth();
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      setStats((await loanApi.dashboard()) as DashboardStats);
    } catch (e: any) {
      setError(e?.message || "Unable to load the portfolio snapshot.");
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => {
    void load();
  }, [load]);
  const status = useMemo(
    () =>
      stats
        ? ([
            ["Active", num(stats.activeLoans)],
            ["Pending", num(stats.pendingLoans)],
            ["Overdue", num(stats.overdueLoans)],
            ["Defaulted", num(stats.defaultedLoans)],
            ["Paid", num(stats.completedLoans)],
          ] as [string, number][])
            .filter((x) => x[1] > 0)
            .map(([name, value]) => ({ name, value }))
        : [],
    [stats],
  );
  if (loading) return <PageSpinner />;
  if (!stats)
    return (
      <main className="premium-page grid min-h-[80vh] place-items-center p-6">
        <Card>
          <CardBody>
            <div className="text-center">
              <div className="text-3xl">!</div>
              <h1 className="mt-3 text-xl font-black text-[#071a2d]">
                Portfolio snapshot unavailable
              </h1>
              <p className="mt-2 max-w-md text-xs text-slate-500">
                {error || "The server did not return dashboard statistics."}
              </p>
              <Button className="mt-5" onClick={() => void load()}>
                Retry
              </Button>
            </div>
          </CardBody>
        </Card>
      </main>
    );
  const par = num(stats.portfolioAtRiskPct),
    disb = num(stats.totalDisbursed),
    col = num(stats.totalCollected),
    coverage = disb ? Math.min(100, (col / disb) * 100) : 0;
  return (
    <main className="premium-page pb-14">
      <div className="mx-auto max-w-[1680px] space-y-6 px-4 py-6 sm:px-6 lg:px-8">
        <section className="premium-hero px-6 py-8 text-white sm:px-9 lg:px-11">
          <div className="relative z-10 flex flex-col gap-7 lg:flex-row lg:items-end lg:justify-between">
            <div>
              <div className="premium-kicker">
                Executive portfolio command centre
              </div>
              <h1 className="mt-3 text-3xl font-black tracking-[-.045em] sm:text-5xl">
                Good to see you
                {user?.name ? `, ${user.name.split(" ")[0]}` : ""}.
              </h1>
              <p className="mt-4 max-w-3xl text-sm leading-7 text-slate-300">
                One controlled view of exposure, liquidity, collections, credit
                quality and operational attention. All financial values are
                sourced from the lending engine.
              </p>
              <div className="mt-5 flex flex-wrap items-center gap-3 text-[10px] font-bold uppercase tracking-[.13em] text-slate-400">
                <span className="h-2 w-2 rounded-full bg-emerald-400" /> Live
                snapshot <span>•</span>
                {new Intl.DateTimeFormat(locale || "en-RW", {
                  dateStyle: "long",
                }).format(new Date())}
              </div>
            </div>
            <div className="flex flex-wrap gap-2">
              <Button
                variant="secondary"
                onClick={() => (location.href = "/dashboard/payments")}
              >
                Record payment
              </Button>
              <Button
                variant="secondary"
                onClick={() => (location.href = "/dashboard/import")}
              >
                Import
              </Button>
              <Button onClick={() => (location.href = "/dashboard/loans/new")}>
                Originate loan
              </Button>
            </div>
          </div>
        </section>
        <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <StatCard
            icon={<span>◈</span>}
            label="Portfolio facilities"
            value={formatNumber(num(stats.totalLoans))}
            sub={`${formatNumber(num(stats.activeLoans))} active facilities`}
            color="#0b2944"
          />
          <StatCard
            icon={<span>◆</span>}
            label="Outstanding principal"
            value={money(stats.outstandingBalance, currency, locale)}
            sub="Current disbursed exposure"
            color="#087f74"
          />
          <StatCard
            icon={<span>↗</span>}
            label="Gross disbursed"
            value={money(stats.totalDisbursed, currency, locale)}
            sub="Lifetime disbursement base"
            color="#315b7f"
          />
          <StatCard
            icon={<span>!</span>}
            label="Portfolio at risk"
            value={`${par.toFixed(2)}%`}
            sub={`${risk(par)} monitoring position`}
            color={par > 5 ? "#b42318" : "#c9a227"}
          />
        </section>
        <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <StatCard
            icon={<span>◷</span>}
            label="Pending decisions"
            value={formatNumber(num(stats.pendingLoans))}
            sub="Awaiting credit action"
            color="#c9a227"
          />
          <StatCard
            icon={<span>!</span>}
            label="Overdue facilities"
            value={formatNumber(num(stats.overdueLoans))}
            sub={`${formatNumber(num(stats.latePaymentsCount))} late payments`}
            color="#b42318"
          />
          <StatCard
            icon={<span>₣</span>}
            label="Collections this month"
            value={money(stats.collectedThisMonth, currency, locale)}
            sub={`Lifetime ${money(stats.totalCollected, currency, locale)}`}
            color="#087f74"
          />
          <StatCard
            icon={<span>♙</span>}
            label="Client relationships"
            value={formatNumber(num(stats.totalBorrowers))}
            sub={`${formatNumber(num(stats.completedLoans))} completed facilities`}
            color="#53677c"
          />
        </section>
        {num(stats.pendingLoans) +
          num(stats.overdueLoans) +
          num(stats.defaultedLoans) >
          0 && (
          <section className="premium-card">
            <CardBody>
              <div className="flex flex-col gap-5 lg:flex-row lg:items-center lg:justify-between">
                <div>
                  <div className="premium-eyebrow">Management attention</div>
                  <h2 className="mt-2 text-xl font-black text-[#071a2d]">
                    Items requiring controlled action
                  </h2>
                  <p className="mt-2 text-xs leading-6 text-slate-500">
                    Use the dedicated operational workspaces to resolve the
                    following portfolio exceptions.
                  </p>
                </div>
                <div className="grid grid-cols-3 gap-2">
                  <Link
                    href="/dashboard/approvals"
                    className="rounded-xl border border-amber-100 bg-amber-50 p-3 text-center"
                  >
                    <div className="text-lg font-black text-amber-800">
                      {num(stats.pendingLoans)}
                    </div>
                    <div className="mt-1 text-[9px] font-black uppercase tracking-wider text-amber-700">
                      Pending
                    </div>
                  </Link>
                  <Link
                    href="/dashboard/collections"
                    className="rounded-xl border border-red-100 bg-red-50 p-3 text-center"
                  >
                    <div className="text-lg font-black text-red-800">
                      {num(stats.overdueLoans)}
                    </div>
                    <div className="mt-1 text-[9px] font-black uppercase tracking-wider text-red-700">
                      Overdue
                    </div>
                  </Link>
                  <Link
                    href="/dashboard/loans?status=DEFAULTED"
                    className="rounded-xl border border-slate-200 bg-slate-50 p-3 text-center"
                  >
                    <div className="text-lg font-black text-slate-800">
                      {num(stats.defaultedLoans)}
                    </div>
                    <div className="mt-1 text-[9px] font-black uppercase tracking-wider text-slate-600">
                      Defaulted
                    </div>
                  </Link>
                </div>
              </div>
            </CardBody>
          </section>
        )}
        <section className="grid gap-5 xl:grid-cols-[1.35fr_.65fr]">
          <Card>
            <CardHeader
              title="Portfolio status architecture"
              subtitle="Facility distribution by current backend status"
            />
            <CardBody>
              <div className="h-[300px] w-full">
                <ResponsiveContainer>
                  <BarChart data={status}>
                    <CartesianGrid vertical={false} stroke="#edf1f5" />
                    <XAxis
                      dataKey="name"
                      axisLine={false}
                      tickLine={false}
                      tick={{ fontSize: 10 }}
                    />
                    <YAxis
                      axisLine={false}
                      tickLine={false}
                      allowDecimals={false}
                      tick={{ fontSize: 10 }}
                    />
                    <Tooltip
                      contentStyle={{
                        borderRadius: 12,
                        border: "1px solid #e5eaf0",
                        fontSize: 11,
                      }}
                    />
                    <Bar dataKey="value" radius={[7, 7, 0, 0]} fill="#0b2944" />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </CardBody>
          </Card>
          <Card>
            <CardHeader
              title="Portfolio quality"
              subtitle="At-risk exposure and collection coverage"
            />
            <CardBody>
              <div className="flex items-center justify-center">
                <div className="relative h-48 w-48">
                  <ResponsiveContainer>
                    <PieChart>
                      <Pie
                        data={status}
                        dataKey="value"
                        innerRadius={62}
                        outerRadius={82}
                        paddingAngle={2}
                      >
                        {status.map((_, i) => (
                          <Cell key={i} fill={COLORS[i % COLORS.length]} />
                        ))}
                      </Pie>
                    </PieChart>
                  </ResponsiveContainer>
                  <div className="absolute inset-0 grid place-items-center text-center">
                    <div>
                      <div className="text-3xl font-black text-[#071a2d]">
                        {par.toFixed(1)}%
                      </div>
                      <div className="text-[9px] font-black uppercase tracking-widest text-slate-400">
                        PAR
                      </div>
                    </div>
                  </div>
                </div>
              </div>
              <div className="mt-4 space-y-4">
                <div>
                  <div className="flex justify-between text-[10px] font-bold">
                    <span className="text-slate-500">Collection coverage</span>
                    <span>{coverage.toFixed(1)}%</span>
                  </div>
                  <div className="mt-2 h-2 rounded-full bg-slate-100">
                    <div
                      className="h-2 rounded-full bg-[#087f74]"
                      style={{ width: `${coverage}%` }}
                    />
                  </div>
                </div>
                <div className="grid grid-cols-2 gap-2">
                  <div className="rounded-xl bg-slate-50 p-3">
                    <div className="text-[9px] uppercase tracking-wider text-slate-400">
                      Collected
                    </div>
                    <div className="mt-1 text-sm font-black">
                      {money(col, currency, locale)}
                    </div>
                  </div>
                  <div className="rounded-xl bg-slate-50 p-3">
                    <div className="text-[9px] uppercase tracking-wider text-slate-400">
                      Risk position
                    </div>
                    <div className="mt-1 text-sm font-black">{risk(par)}</div>
                  </div>
                </div>
              </div>
            </CardBody>
          </Card>
        </section>
        <section className="grid gap-5 xl:grid-cols-[1.4fr_.6fr]">
          <Card>
            <CardHeader
              title="Recent lending activity"
              subtitle="Latest facilities returned by the lending engine"
              action={
                <Link
                  href="/dashboard/loans"
                  className="text-[10px] font-black text-[#087f74]"
                >
                  View portfolio →
                </Link>
              }
            />
            <div className="overflow-x-auto">
              <table className="premium-table">
                <thead>
                  <tr>
                    <th>Facility</th>
                    <th>Client</th>
                    <th>Status</th>
                    <th className="text-right">Principal</th>
                    <th>Due</th>
                  </tr>
                </thead>
                <tbody>
                  {(stats.recentLoans || []).slice(0, 8).map((l) => (
                    <tr key={l.id}>
                      <td>
                        <Link
                          href={`/dashboard/loans/${l.id}`}
                          className="font-black text-[#071a2d] hover:text-[#087f74]"
                        >
                          {l.referenceNumber}
                        </Link>
                        <div className="mt-1 text-[9px] text-slate-400">
                          #{l.id}
                        </div>
                      </td>
                      <td>
                        <div className="font-bold">{borrower(l)}</div>
                        <div className="mt-1 text-[9px] text-slate-400">
                          {l.borrower?.phone ||
                            l.borrower?.email ||
                            "Client relationship"}
                        </div>
                      </td>
                      <td>
                        <StatusBadge status={l.status} />
                      </td>
                      <td className="text-right font-black tabular-nums">
                        {money(l.amount, currency, locale)}
                      </td>
                      <td className="text-[10px] font-semibold">
                        {l.nextDueDate
                          ? formatDate(l.nextDueDate, locale)
                          : "—"}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>
          <Card>
            <CardHeader
              title="Executive shortcuts"
              subtitle="Move directly to controlled workflows"
            />
            <CardBody>
              <div className="space-y-2">
                {[
                  [
                    "Originate a facility",
                    "Create a controlled credit application",
                    "/dashboard/loans/new",
                  ],
                  [
                    "Review approvals",
                    "Open pending credit decisions",
                    "/dashboard/approvals",
                  ],
                  [
                    "Manage collections",
                    "Resolve overdue exposure",
                    "/dashboard/collections",
                  ],
                  [
                    "Finance control",
                    "Open accounting and reconciliation",
                    "/dashboard/accounting",
                  ],
                  [
                    "Regulatory",
                    "Open BNR reporting workspace",
                    "/dashboard/reports/regulatory/bnr",
                  ],
                ].map(([t, s, h]) => (
                  <Link
                    href={h}
                    key={h}
                    className="group flex items-center gap-3 rounded-xl border border-slate-100 p-3 hover:border-teal-100 hover:bg-teal-50/30"
                  >
                    <span className="grid h-9 w-9 place-items-center rounded-lg bg-slate-50 text-sm group-hover:bg-white">
                      →
                    </span>
                    <div className="min-w-0">
                      <div className="text-xs font-black text-[#071a2d]">
                        {t}
                      </div>
                      <div className="mt-1 text-[10px] text-slate-500">{s}</div>
                    </div>
                  </Link>
                ))}
              </div>
            </CardBody>
          </Card>
        </section>
      </div>
    </main>
  );
}
