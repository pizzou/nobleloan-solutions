"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { accountingApi, expenseApi, get, loanApi } from "@/services/api";
import { DashboardStats, Loan } from "@/types";
import { useAuth } from "@/hooks/useAuth";
import { Button } from "@/components/ui/Button";
import { Card, CardBody, CardHeader, StatCard } from "@/components/ui/Card";
import { PageSpinner } from "@/components/ui/Skeleton";
import { formatCurrency, formatDate, formatNumber } from "@/lib/utils";

const CACHE_KEY = "noble:dashboard:v3";
const CACHE_TTL = 30_000;

const num = (v: unknown) => {
  const n = Number(v ?? 0);
  return Number.isFinite(n) ? n : 0;
};

const money = (v: unknown, currency: string, locale: string) =>
  formatCurrency(num(v), currency, locale);

const borrowerName = (loan: Loan) =>
  `${loan.borrower?.firstName || ""} ${loan.borrower?.lastName || ""}`.trim() ||
  "Unnamed client";

function quality(par: number) {
  if (par <= 3)
    return {
      label: "Controlled",
      tone: "text-emerald-700 bg-emerald-50 border-emerald-100",
    };
  if (par <= 5)
    return {
      label: "Watch",
      tone: "text-amber-700 bg-amber-50 border-amber-100",
    };
  if (par <= 10)
    return {
      label: "Elevated",
      tone: "text-orange-700 bg-orange-50 border-orange-100",
    };
  return { label: "Critical", tone: "text-red-700 bg-red-50 border-red-100" };
}

function Metric({
  label,
  value,
  sub,
  href,
}: {
  label: string;
  value: string;
  sub: string;
  href?: string;
}) {
  const content = (
    <div className="group rounded-2xl border border-slate-200 bg-white p-5 shadow-[0_10px_30px_rgba(7,26,45,.035)] transition hover:-translate-y-0.5 hover:border-slate-300 hover:shadow-[0_18px_45px_rgba(7,26,45,.07)]">
      <div className="text-[9px] font-black uppercase tracking-[.18em] text-slate-400">
        {label}
      </div>
      <div className="mt-2 text-2xl font-black tracking-[-.04em] text-[#071a2d] tabular-nums">
        {value}
      </div>
      <div className="mt-1 text-[10px] leading-5 text-slate-500">{sub}</div>
      {href ? (
        <div className="mt-3 text-[9px] font-black uppercase tracking-wider text-[#087f74] opacity-0 transition group-hover:opacity-100">
          Open →
        </div>
      ) : null}
    </div>
  );
  return href ? <Link href={href}>{content}</Link> : content;
}

function cacheRead(): DashboardStats | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = sessionStorage.getItem(CACHE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as { at: number; data: DashboardStats };
    if (!parsed?.data || Date.now() - Number(parsed.at) > CACHE_TTL)
      return null;
    return parsed.data;
  } catch {
    return null;
  }
}

function cacheWrite(data: DashboardStats) {
  if (typeof window === "undefined") return;
  try {
    sessionStorage.setItem(CACHE_KEY, JSON.stringify({ at: Date.now(), data }));
  } catch {
    // Storage is an optimization only; never fail the dashboard because of it.
  }
}

export default function DashboardPage() {
  const { currency, locale, user } = useAuth();
  const [stats, setStats] = useState<DashboardStats | null>(() => cacheRead());
  const [loading, setLoading] = useState(() => !cacheRead());
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState("");
  const [finance, setFinance] = useState<{
    income: number;
    expenses: number;
    profit: number;
    cash: number;
    expenseCount: number;
  } | null>(null);

  const canFinance = ["ADMIN", "MANAGER", "ACCOUNTANT", "FINANCE"].includes(
    user?.role || "",
  );

  const load = useCallback(async (background = false) => {
    if (background) setRefreshing(true);
    else setLoading(true);
    setError("");
    try {
      const data = (await loanApi.dashboard()) as DashboardStats;
      setStats(data);
      cacheWrite(data);
    } catch (e: any) {
      setError(e?.message || "Unable to load the portfolio snapshot.");
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    if (!stats) {
      void load();
      return;
    }
    // Revalidate silently; the cached snapshot makes route navigation instant.
    void load(true);
  }, []); // intentionally run once per mounted dashboard

  useEffect(() => {
    if (!canFinance || !user?.organizationId) return;
    let cancelled = false;
    const run = () => {
      const today = new Date();
      const from = new Date(today.getFullYear(), today.getMonth(), 1)
        .toISOString()
        .slice(0, 10);
      const to = today.toISOString().slice(0, 10);
      Promise.allSettled([
        get(
          `/reports/accounting/${user.organizationId}/profit-and-loss?from=${from}&to=${to}`,
        ),
        accountingApi.cashFlow(from, to),
        expenseApi.summary(from, to),
      ]).then(([pnl, cash, expenses]) => {
        if (cancelled) return;
        const p =
          pnl.status === "fulfilled" ? (pnl.value?.data ?? pnl.value) : {};
        const c = cash.status === "fulfilled" ? (cash.value ?? {}) : {};
        const e = expenses.status === "fulfilled" ? (expenses.value ?? {}) : {};
        setFinance({
          income: num(p?.totalIncome),
          expenses: num(
            p?.totalExpense ?? p?.totalExpenses ?? e?.totalExpenses,
          ),
          profit: num(p?.netIncome),
          cash: num(c?.netChangeInCash),
          expenseCount: num(e?.count ?? e?.totalCount),
        });
      });
    };
    const idle = window.setTimeout(run, 500);
    return () => {
      cancelled = true;
      window.clearTimeout(idle);
    };
  }, [canFinance, user?.organizationId]);

  const par = num(stats?.portfolioAtRiskPct);
  const qualityState = useMemo(() => quality(par), [par]);
  const disbursed = num(stats?.totalDisbursed);
  const collected = num(stats?.totalCollected);
  const collectionRate =
    disbursed > 0 ? Math.min(100, (collected / disbursed) * 100) : 0;
  const attention =
    num(stats?.pendingLoans) +
    num(stats?.overdueLoans) +
    num(stats?.defaultedLoans);
  const recentLoans = (stats?.recentLoans || []).slice(0, 7);

  if (loading && !stats) return <PageSpinner />;

  if (!stats) {
    return (
      <main className="premium-page grid min-h-[80vh] place-items-center p-6">
        <Card>
          <CardBody>
            <div className="max-w-md text-center">
              <div className="mx-auto grid h-12 w-12 place-items-center rounded-2xl bg-red-50 text-xl font-black text-red-700">
                !
              </div>
              <h1 className="mt-4 text-xl font-black text-[#071a2d]">
                Portfolio snapshot unavailable
              </h1>
              <p className="mt-2 text-xs leading-6 text-slate-500">
                {error ||
                  "The lending engine did not return dashboard statistics."}
              </p>
              <Button className="mt-5" onClick={() => void load()}>
                Retry
              </Button>
            </div>
          </CardBody>
        </Card>
      </main>
    );
  }

  return (
    <main className="premium-page pb-16">
      <div className="mx-auto max-w-[1680px] space-y-6 px-4 py-6 sm:px-6 lg:px-8">
        <section className="premium-hero relative overflow-hidden px-6 py-8 text-white sm:px-9 lg:px-11">
          <div className="absolute -right-20 -top-28 h-72 w-72 rounded-full border border-white/10" />
          <div className="absolute -right-8 -bottom-40 h-80 w-80 rounded-full border border-[#c9a227]/15" />
          <div className="relative z-10 flex flex-col gap-7 lg:flex-row lg:items-end lg:justify-between">
            <div>
              <div className="premium-kicker">
                Noble Loan · Executive portfolio
              </div>
              <h1 className="mt-3 max-w-4xl text-3xl font-black tracking-[-.05em] sm:text-5xl">
                Good to see you
                {user?.name ? `, ${user.name.split(" ")[0]}` : ""}.
              </h1>
              <p className="mt-4 max-w-3xl text-sm leading-7 text-slate-300">
                A controlled view of lending exposure, collections, credit
                quality and finance performance — with the lending engine
                remaining the financial authority.
              </p>
              <div className="mt-5 flex flex-wrap items-center gap-3 text-[10px] font-bold uppercase tracking-[.13em] text-slate-400">
                <span className="h-2 w-2 rounded-full bg-emerald-400" />
                {refreshing ? "Refreshing" : "Live portfolio snapshot"}
                <span>•</span>
                {new Intl.DateTimeFormat(locale || "en-RW", {
                  dateStyle: "long",
                }).format(new Date())}
              </div>
            </div>
            <div className="flex flex-wrap gap-2">
              <Link
                href="/dashboard/loans/new"
                className="premium-btn premium-btn-primary px-4 py-2.5 text-[11px]"
              >
                Originate loan
              </Link>
              <Link
                href="/dashboard/payments"
                className="premium-btn premium-btn-secondary px-4 py-2.5 text-[11px]"
              >
                Record payment
              </Link>
              <Link
                href="/dashboard/reports"
                className="premium-btn premium-btn-secondary px-4 py-2.5 text-[11px]"
              >
                Management reports
              </Link>
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
            sub="Disbursed principal base"
            color="#315b7f"
          />
          <StatCard
            icon={<span>!</span>}
            label="Portfolio at risk"
            value={`${par.toFixed(2)}%`}
            sub={`${qualityState.label} monitoring position`}
            color={par > 5 ? "#b42318" : "#c9a227"}
          />
        </section>

        <section className="grid gap-4 lg:grid-cols-[1.25fr_.75fr]">
          <Card>
            <CardHeader
              title="Portfolio command"
              subtitle="The operating numbers management should see first."
            />
            <CardBody>
              <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
                <Metric
                  label="Pending decisions"
                  value={formatNumber(num(stats.pendingLoans))}
                  sub="Awaiting credit action"
                  href="/dashboard/approvals"
                />
                <Metric
                  label="Overdue facilities"
                  value={formatNumber(num(stats.overdueLoans))}
                  sub={`${formatNumber(num(stats.latePaymentsCount))} late payments`}
                  href="/dashboard/collections"
                />
                <Metric
                  label="Collections this month"
                  value={money(stats.collectedThisMonth, currency, locale)}
                  sub={`Lifetime ${money(stats.totalCollected, currency, locale)}`}
                  href="/dashboard/payments"
                />
                <Metric
                  label="Client relationships"
                  value={formatNumber(num(stats.totalBorrowers))}
                  sub={`${formatNumber(num(stats.completedLoans))} completed facilities`}
                  href="/dashboard/borrowers"
                />
              </div>
            </CardBody>
          </Card>

          <Card>
            <CardHeader
              title="Credit quality"
              subtitle="Risk classification stays with each borrower and facility."
            />
            <CardBody>
              <div className="flex items-end justify-between gap-4">
                <div>
                  <div className="text-4xl font-black tracking-[-.05em] text-[#071a2d]">
                    {par.toFixed(2)}%
                  </div>
                  <div className="mt-1 text-[9px] font-black uppercase tracking-widest text-slate-400">
                    Portfolio at risk
                  </div>
                </div>
                <span
                  className={`rounded-full border px-3 py-1.5 text-[9px] font-black uppercase tracking-wider ${qualityState.tone}`}
                >
                  {qualityState.label}
                </span>
              </div>
              <div className="mt-7 h-2 overflow-hidden rounded-full bg-slate-100">
                <div
                  className="h-full rounded-full bg-[#087f74]"
                  style={{ width: `${Math.min(100, Math.max(2, 100 - par))}%` }}
                />
              </div>
              <div className="mt-3 flex justify-between text-[10px] text-slate-500">
                <span>Healthy portfolio share</span>
                <span>{Math.max(0, 100 - par).toFixed(1)}%</span>
              </div>
              <div className="mt-5 grid grid-cols-3 gap-2">
                <Link
                  href="/dashboard/loans?status=ACTIVE"
                  className="rounded-xl bg-emerald-50 p-3 text-center"
                >
                  <b className="text-lg text-emerald-800">
                    {num(stats.activeLoans)}
                  </b>
                  <span className="block text-[9px] font-black uppercase text-emerald-700">
                    Active
                  </span>
                </Link>
                <Link
                  href="/dashboard/loans?status=OVERDUE"
                  className="rounded-xl bg-red-50 p-3 text-center"
                >
                  <b className="text-lg text-red-800">
                    {num(stats.overdueLoans)}
                  </b>
                  <span className="block text-[9px] font-black uppercase text-red-700">
                    Overdue
                  </span>
                </Link>
                <Link
                  href="/dashboard/loans?status=DEFAULTED"
                  className="rounded-xl bg-slate-100 p-3 text-center"
                >
                  <b className="text-lg text-slate-800">
                    {num(stats.defaultedLoans)}
                  </b>
                  <span className="block text-[9px] font-black uppercase text-slate-600">
                    Defaulted
                  </span>
                </Link>
              </div>
              <div className="mt-5 rounded-xl border border-slate-200 bg-slate-50 p-3 text-[10px] leading-5 text-slate-500">
                Individual credit quality is shown on the borrower profile and
                linked loan record. The dashboard intentionally does not create
                a separate credit-quality module.
              </div>
            </CardBody>
          </Card>
        </section>

        {attention > 0 && (
          <section className="premium-card">
            <CardBody>
              <div className="flex flex-col gap-5 lg:flex-row lg:items-center lg:justify-between">
                <div>
                  <div className="premium-eyebrow">Management attention</div>
                  <h2 className="mt-2 text-xl font-black text-[#071a2d]">
                    {attention} portfolio items require action
                  </h2>
                  <p className="mt-2 text-xs leading-6 text-slate-500">
                    Resolve exceptions from the existing operational workspaces;
                    no parallel workflow is introduced.
                  </p>
                </div>
                <div className="flex flex-wrap gap-2">
                  <Link
                    href="/dashboard/approvals"
                    className="rounded-xl border border-amber-100 bg-amber-50 px-4 py-3"
                  >
                    <b className="text-lg text-amber-800">
                      {num(stats.pendingLoans)}
                    </b>
                    <span className="ml-2 text-[9px] font-black uppercase text-amber-700">
                      Pending
                    </span>
                  </Link>
                  <Link
                    href="/dashboard/collections"
                    className="rounded-xl border border-red-100 bg-red-50 px-4 py-3"
                  >
                    <b className="text-lg text-red-800">
                      {num(stats.overdueLoans)}
                    </b>
                    <span className="ml-2 text-[9px] font-black uppercase text-red-700">
                      Overdue
                    </span>
                  </Link>
                  <Link
                    href="/dashboard/loans?status=DEFAULTED"
                    className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-3"
                  >
                    <b className="text-lg text-slate-800">
                      {num(stats.defaultedLoans)}
                    </b>
                    <span className="ml-2 text-[9px] font-black uppercase text-slate-600">
                      Defaulted
                    </span>
                  </Link>
                </div>
              </div>
            </CardBody>
          </section>
        )}

        <section className="grid gap-5 xl:grid-cols-[1.05fr_.95fr]">
          <Card>
            <CardHeader
              title="Collections performance"
              subtitle="Collection coverage and current delinquency pressure."
              action={
                <Link
                  href="/dashboard/collections"
                  className="text-[10px] font-black text-[#087f74]"
                >
                  Open collections →
                </Link>
              }
            />
            <CardBody>
              <div className="grid gap-6 md:grid-cols-[180px_1fr] md:items-center">
                <div
                  className="grid h-44 w-44 place-items-center rounded-full"
                  style={{
                    background: `conic-gradient(#087f74 ${collectionRate}%, #e9eef2 ${collectionRate}% 100%)`,
                  }}
                >
                  <div className="grid h-32 w-32 place-items-center rounded-full bg-white text-center shadow-inner">
                    <div>
                      <div className="text-3xl font-black text-[#071a2d]">
                        {collectionRate.toFixed(1)}%
                      </div>
                      <div className="text-[9px] font-black uppercase tracking-widest text-slate-400">
                        coverage
                      </div>
                    </div>
                  </div>
                </div>
                <div className="min-w-0">
                  <div className="grid gap-3 sm:grid-cols-2">
                    <Metric
                      label="Disbursed"
                      value={money(disbursed, currency, locale)}
                      sub="Lifetime disbursement base"
                    />
                    <Metric
                      label="Collected"
                      value={money(collected, currency, locale)}
                      sub="Lifetime posted collections"
                      href="/dashboard/payments"
                    />
                  </div>
                  <div className="mt-4 grid gap-3 sm:grid-cols-2">
                    <div className="rounded-xl border border-red-100 bg-red-50 p-4">
                      <div className="text-[9px] font-black uppercase tracking-widest text-red-500">
                        Overdue facilities
                      </div>
                      <div className="mt-2 text-2xl font-black text-red-800">
                        {num(stats.overdueLoans)}
                      </div>
                      <Link
                        href="/dashboard/collections"
                        className="mt-1 block text-[10px] font-bold text-red-700"
                      >
                        Review collection queue →
                      </Link>
                    </div>
                    <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
                      <div className="text-[9px] font-black uppercase tracking-widest text-slate-400">
                        Late payments
                      </div>
                      <div className="mt-2 text-2xl font-black text-[#071a2d]">
                        {num(stats.latePaymentsCount)}
                      </div>
                      <div className="mt-1 text-[10px] text-slate-500">
                        Backend-detected past-due installments
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </CardBody>
          </Card>

          <Card>
            <CardHeader
              title="Finance control"
              subtitle="Accounting and expenses are visible here without duplicating the finance engine."
              action={
                canFinance ? (
                  <Link
                    href="/dashboard/accounting"
                    className="text-[10px] font-black text-[#087f74]"
                  >
                    Open accounting →
                  </Link>
                ) : undefined
              }
            />
            <CardBody>
              {canFinance ? (
                <div className="grid grid-cols-2 gap-3">
                  <Metric
                    label="Income"
                    value={
                      finance
                        ? money(finance.income, currency, locale)
                        : "Loading…"
                    }
                    sub="Current period"
                    href="/dashboard/accounting"
                  />
                  <Metric
                    label="Expenses"
                    value={
                      finance
                        ? money(finance.expenses, currency, locale)
                        : "Loading…"
                    }
                    sub={
                      finance
                        ? `${finance.expenseCount || 0} expense records`
                        : "Current period"
                    }
                    href="/dashboard/expenses"
                  />
                  <Metric
                    label="Net income"
                    value={
                      finance
                        ? money(finance.profit, currency, locale)
                        : "Loading…"
                    }
                    sub="Current period"
                    href="/dashboard/reports"
                  />
                  <Metric
                    label="Net cash movement"
                    value={
                      finance
                        ? money(finance.cash, currency, locale)
                        : "Loading…"
                    }
                    sub="Current period"
                    href="/dashboard/accounting"
                  />
                </div>
              ) : (
                <div className="rounded-2xl border border-slate-200 bg-slate-50 p-6">
                  <div className="text-sm font-black text-[#071a2d]">
                    Finance control is permission protected.
                  </div>
                  <p className="mt-2 text-xs leading-6 text-slate-500">
                    Authorised users can access accounting and expenses from
                    Finance & Control.
                  </p>
                </div>
              )}
            </CardBody>
          </Card>
        </section>

        <section className="grid gap-5 xl:grid-cols-[1.35fr_.65fr]">
          <Card>
            <CardHeader
              title="Recent lending & credit quality"
              subtitle="Borrower-linked facility quality, not a separate credit module."
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
                    <th>Borrower</th>
                    <th>Outstanding</th>
                    <th>Status</th>
                    <th>Credit quality</th>
                    <th>Due</th>
                  </tr>
                </thead>
                <tbody>
                  {recentLoans.length ? (
                    recentLoans.map((loan, i) => (
                      <tr key={loan.id ?? i}>
                        <td>
                          <Link
                            href={`/dashboard/loans/${loan.id}`}
                            className="font-black text-[#071a2d] hover:text-[#087f74]"
                          >
                            {loan.referenceNumber || `#${loan.id}`}
                          </Link>
                        </td>
                        <td>
                          <Link
                            href={
                              loan.borrower?.id
                                ? `/dashboard/borrowers/${loan.borrower.id}`
                                : "/dashboard/borrowers"
                            }
                            className="font-bold text-slate-700 hover:text-[#087f74]"
                          >
                            {borrowerName(loan)}
                          </Link>
                        </td>
                        <td className="font-black tabular-nums">
                          {money(
                            loan.outstandingBalance ?? loan.amount,
                            loan.currency || currency,
                            locale,
                          )}
                        </td>
                        <td>
                          <span className="premium-badge bg-slate-100 text-slate-600">
                            {String(loan.status || "—")}
                          </span>
                        </td>
                        <td>
                          <span
                            className={`premium-badge ${loan.creditQuality === "CURRENT" ? "bg-emerald-50 text-emerald-700" : loan.creditQuality === "WATCH" ? "bg-amber-50 text-amber-700" : "bg-red-50 text-red-700"}`}
                          >
                            {String(
                              loan.creditQuality ||
                                loan.riskCategory ||
                                "CURRENT",
                            )}
                          </span>
                        </td>
                        <td>
                          {loan.nextDueDate
                            ? formatDate(loan.nextDueDate, locale)
                            : "—"}
                        </td>
                      </tr>
                    ))
                  ) : (
                    <tr>
                      <td
                        colSpan={6}
                        className="py-10 text-center text-xs text-slate-400"
                      >
                        No recent lending activity returned.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </Card>
          <Card>
            <CardHeader
              title="Executive access"
              subtitle="Existing operating areas, kept intentionally focused."
            />
            <CardBody>
              <div className="grid gap-2">
                {[
                  [
                    "Loan portfolio",
                    "/dashboard/loans",
                    "Exposure, facilities and borrower positions",
                  ],
                  [
                    "Collections",
                    "/dashboard/collections",
                    "Overdue facilities connected to their loans",
                  ],
                  [
                    "Accounting",
                    "/dashboard/accounting",
                    "Ledger, statements and controls",
                  ],
                  [
                    "Expenses",
                    "/dashboard/expenses",
                    "Operating costs and payment records",
                  ],
                  [
                    "Reports",
                    "/dashboard/reports",
                    "Management summaries and Excel exports",
                  ],
                ].map(([title, href, desc]) => (
                  <Link
                    key={href}
                    href={href}
                    className="group rounded-xl border border-slate-200 p-4 transition hover:border-slate-300 hover:bg-slate-50"
                  >
                    <div className="flex items-center justify-between">
                      <div className="text-xs font-black text-[#071a2d]">
                        {title}
                      </div>
                      <span className="text-[#087f74] transition group-hover:translate-x-1">
                        →
                      </span>
                    </div>
                    <div className="mt-1 text-[10px] leading-5 text-slate-500">
                      {desc}
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
