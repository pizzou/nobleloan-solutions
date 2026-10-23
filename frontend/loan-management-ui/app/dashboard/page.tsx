"use client";

import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { useRouter } from "next/navigation";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

import { loanApi, taskApi } from "@/services/api";
import { useAuth } from "@/hooks/useAuth";

/* =========================================================
   NOBLE LOAN SOLUTIONS
   FINANCE COMMAND CENTER

   Authenticated role-specific portfolio reporting.
   Backend controls owner-only visibility.

   No synthetic financial or accounting figures.
   ========================================================= */

type Amount = string | number | null | undefined;

type CashflowRow = {
  month?: string;
  label?: string;
  disbursed?: Amount;
  collected?: Amount;
};

type AgingRow = {
  label: string;
  percentage?: Amount;
  principal?: Amount;
};

type IncomeSummary = {
  interestIncome?: Amount;
  managementFeeIncome?: Amount;
  applicationFeeIncome?: Amount;
  penaltyIncome?: Amount;
  operatingExpenses?: Amount;
  impairmentExpense?: Amount;
  operatingResult?: Amount;
};

type RecentLoan = {
  id?: string | number;
  referenceNumber?: string;
  borrowerName?: string;
  amount?: Amount;
  status?: string;
  createdAt?: string;
  startDate?: string;
  borrower?: {
    firstName?: string;
    lastName?: string;
  };
};

type FinanceData = {
  reportingScope?: string;
  asOfDate?: string;
  currency?: string;

  totalLoans?: Amount;
  totalBorrowers?: Amount;
  activeLoans?: Amount;
  pendingLoans?: Amount;
  overdueLoans?: Amount;
  defaultedLoans?: Amount;
  completedLoans?: Amount;
  latePaymentsCount?: Amount;

  totalDisbursed?: Amount;
  disbursedThisMonth?: Amount;
  totalCollected?: Amount;
  historicalCollected?: Amount;
  collectedThisMonth?: Amount;

  outstandingBalance?: Amount;
  totalReceivables?: Amount;
  portfolioAtRiskPct?: Amount;
  portfolioAtRiskAmount?: Amount;
  applicationFeesCollected?: Amount;

  ownerOnlyOutstanding?: Amount;
  ownerOnlyLoanCount?: Amount;

  cashflowTrend?: CashflowRow[];
  portfolioAging?: AgingRow[];
  incomeSummary?: IncomeSummary;

  recentLoans?: RecentLoan[];
};

type TaskData = {
  openTasks?: Amount;
  overdueTasks?: Amount;
  urgentTasks?: Amount;
};

const NAVY = "#102B4A";
const GOLD = "#D0A853";
const GREEN = "#299579";
const ORANGE = "#D88354";
const RED = "#C45C64";

function numeric(value: Amount): number {
  if (value === null || value === undefined || value === "") {
    return 0;
  }

  const parsed = Number(value);

  return Number.isFinite(parsed) ? parsed : 0;
}

function exists(value: Amount): boolean {
  return (
    value !== null &&
    value !== undefined &&
    value !== "" &&
    Number.isFinite(Number(value))
  );
}

function formatRwf(value: Amount, currency = "RWF"): string {
  if (!exists(value)) return "—";

  return `${currency} ${numeric(value).toLocaleString("en-RW", {
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  })}`;
}

function compact(value: Amount): string {
  if (!exists(value)) return "—";

  const n = numeric(value);
  const abs = Math.abs(n);

  if (abs >= 1_000_000_000) {
    return `${(n / 1_000_000_000).toFixed(2)}B`;
  }

  if (abs >= 1_000_000) {
    return `${(n / 1_000_000).toFixed(2)}M`;
  }

  if (abs >= 1_000) {
    return `${(n / 1_000).toFixed(1)}K`;
  }

  return n.toLocaleString("en-RW");
}

function count(value: Amount): string {
  if (!exists(value)) return "—";
  return numeric(value).toLocaleString("en-RW");
}

function unwrapDashboard(result: unknown): FinanceData {
  if (!result || typeof result !== "object") return {};

  let current: unknown = result;

  // Supports direct responses and API wrappers.
  for (let i = 0; i < 3; i++) {
    if (!current || typeof current !== "object") break;

    const object = current as Record<string, unknown>;

    if (
      "totalLoans" in object ||
      "outstandingBalance" in object ||
      "totalDisbursed" in object
    ) {
      return object as FinanceData;
    }

    current = object.data;
  }

  return {};
}

function getAuthenticatedOwner(auth: unknown): boolean {
  if (!auth || typeof auth !== "object") return false;

  const context = auth as {
    isBusinessOwner?: boolean;
    user?: {
      role?: string;
      roles?: Array<string | { name?: string }>;
    };
  };

  if (context.isBusinessOwner === true) return true;

  const user = context.user;
  if (!user) return false;

  const roles = [
    user.role ?? "",
    ...(user.roles ?? []).map((role) =>
      typeof role === "string" ? role : (role.name ?? ""),
    ),
  ];

  return roles.some(
    (role) => role.toUpperCase().replace(/[\s-]+/g, "_") === "BUSINESS_OWNER",
  );
}

/* =========================================================
   SHARED FINANCE UI
   ========================================================= */

function Panel({
  title,
  subtitle,
  right,
  children,
  className = "",
}: {
  title: string;
  subtitle?: string;
  right?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <section
      className={[
        "min-w-0 overflow-hidden rounded-xl",
        "border border-[#E0E7EF] bg-white",
        "shadow-[0_5px_22px_rgba(14,35,64,0.035)]",
        className,
      ].join(" ")}
    >
      <div className="flex flex-wrap items-start justify-between gap-3 border-b border-[#EDF1F5] px-5 py-4">
        <div>
          <h2 className="text-[13px] font-extrabold text-[#142B46]">{title}</h2>

          {subtitle && (
            <p className="mt-1 text-[11px] leading-5 text-slate-500">
              {subtitle}
            </p>
          )}
        </div>

        {right}
      </div>

      <div className="p-5">{children}</div>
    </section>
  );
}

function Metric({
  title,
  value,
  description,
  symbol,
  accent,
}: {
  title: string;
  value: string;
  description: string;
  symbol: string;
  accent: string;
}) {
  return (
    <article className="relative min-w-0 overflow-hidden rounded-xl border border-[#E0E7EF] bg-white p-4 shadow-[0_5px_22px_rgba(14,35,64,0.035)]">
      <div
        className="absolute bottom-0 left-0 top-0 w-[3px]"
        style={{ backgroundColor: accent }}
      />

      <div className="flex items-start justify-between gap-2">
        <p className="min-h-8 text-[11px] font-bold leading-5 text-[#697C92]">
          {title}
        </p>

        <span
          aria-hidden="true"
          className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-[#F2F5F9] text-lg font-black"
          style={{ color: accent }}
        >
          {symbol}
        </span>
      </div>

      <p className="mt-2 break-words text-[clamp(1.2rem,1.9vw,1.8rem)] font-black tracking-tight text-[#152E4B]">
        {value}
      </p>

      <p className="mt-2 text-[10px] leading-5 text-[#8593A4]">{description}</p>
    </article>
  );
}

function FinancialRow({
  title,
  value,
  emphasis = false,
}: {
  title: string;
  value: string;
  emphasis?: boolean;
}) {
  return (
    <div
      className={[
        "flex items-center justify-between gap-4 py-2.5",
        "border-b border-[#EEF2F5] last:border-0",
        emphasis ? "font-extrabold" : "",
      ].join(" ")}
    >
      <span className="text-xs text-[#65778D]">{title}</span>

      <span
        className={
          emphasis
            ? "text-right text-[15px] font-black text-[#238267]"
            : "text-right text-[12px] font-extrabold text-[#193553]"
        }
      >
        {value}
      </span>
    </div>
  );
}

function NoData({ title, detail }: { title: string; detail: string }) {
  return (
    <div className="flex min-h-48 flex-col items-center justify-center px-4 text-center">
      <span
        aria-hidden="true"
        className="mb-3 flex h-11 w-11 items-center justify-center rounded-xl bg-[#F2F5F9] text-xl text-[#7A8FA7]"
      >
        ▥
      </span>
      <p className="text-xs font-bold text-[#647890]">{title}</p>
      <p className="mt-2 max-w-sm text-[11px] leading-5 text-[#91A0AF]">
        {detail}
      </p>
    </div>
  );
}

function StatusPill({ value }: { value?: string }) {
  const status = (value || "UNKNOWN").toUpperCase();

  let classes = "bg-[#EFF3F7] text-[#41617F]";

  if (status === "ACTIVE") {
    classes = "bg-emerald-50 text-emerald-700";
  } else if (status === "OVERDUE" || status === "DEFAULTED") {
    classes = "bg-red-50 text-red-700";
  } else if (
    status === "PAID" ||
    status === "COMPLETED" ||
    status === "CLOSED"
  ) {
    classes = "bg-blue-50 text-blue-700";
  }

  return (
    <span
      className={`inline-flex rounded-md px-2.5 py-1.5 text-[10px] font-extrabold ${classes}`}
    >
      {status.replaceAll("_", " ")}
    </span>
  );
}

/* =========================================================
   MAIN DASHBOARD
   ========================================================= */

export default function DashboardPage() {
  const router = useRouter();
  const auth = useAuth();

  const authenticatedOwner = getAuthenticatedOwner(auth);

  const [stats, setStats] = useState<FinanceData | null>(null);
  const [tasks, setTasks] = useState<TaskData>({});
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState("");
  const [months, setMonths] = useState<6 | 12>(6);
  const [retrievedAt, setRetrievedAt] = useState<Date | null>(null);

  const currency = stats?.currency || auth.currency || "RWF";

  const money = useCallback(
    (value: Amount) => formatRwf(value, currency),
    [currency],
  );

  const load = useCallback(async (refresh = false) => {
    if (refresh) setRefreshing(true);
    else setLoading(true);

    setError("");

    try {
      // The API determines the authorized financial scope.
      // Never send includeOwnerOnly=true from the browser.
      const response = await loanApi.dashboard();
      const result = unwrapDashboard(response);

      if (Object.keys(result).length === 0) {
        throw new Error("The dashboard API returned no financial data.");
      }

      setStats(result);
      setRetrievedAt(new Date());

      try {
        const taskResult = await taskApi.kpi();
        setTasks(taskResult as TaskData);
      } catch {
        // Financial results remain visible if workflow KPIs fail.
      }
    } catch (err: unknown) {
      setError(
        err instanceof Error
          ? err.message
          : "Unable to load the financial dashboard.",
      );
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const ownerView =
    authenticatedOwner &&
    stats?.reportingScope === "BUSINESS_OWNER_CONSOLIDATED";

  const snapshot = useMemo(() => {
    const data = stats;

    return {
      outstanding: data?.outstandingBalance,
      receivables: data?.totalReceivables,
      disbursed: data?.totalDisbursed,
      disbursedMonth: data?.disbursedThisMonth,
      collected: data?.totalCollected,
      collectedMonth: data?.collectedThisMonth,
      historicalCollected: data?.historicalCollected,
      riskPct: data?.portfolioAtRiskPct,
      riskAmount: data?.portfolioAtRiskAmount,

      totalLoans: data?.totalLoans,
      borrowers: data?.totalBorrowers,
      active: data?.activeLoans,
      pending: data?.pendingLoans,
      overdue: data?.overdueLoans,
      defaulted: data?.defaultedLoans,
      completed: data?.completedLoans,
      latePayments: data?.latePaymentsCount,
    };
  }, [stats]);

  /*
   * MONTHLY CASH FLOW
   *
   * Prefer backend month-grouped transactions.
   *
   * Until those are supplied, use an accurately labeled
   * cumulative disbursement vs collection chart.
   * The cumulative fallback does NOT claim monthly figures.
   */
  const hasMonthlyTrend =
    Array.isArray(stats?.cashflowTrend) && stats.cashflowTrend.length > 0;

  const cashflow = useMemo(() => {
    if (stats?.cashflowTrend?.length) {
      return stats.cashflowTrend.slice(-months).map((row) => ({
        period: row.month || row.label || "Period",
        disbursed: numeric(row.disbursed),
        collected: numeric(row.collected),
      }));
    }

    if (exists(stats?.totalDisbursed) || exists(stats?.totalCollected)) {
      return [
        {
          period: "To date",
          disbursed: numeric(stats?.totalDisbursed),
          collected: numeric(stats?.totalCollected),
        },
      ];
    }

    return [];
  }, [stats, months]);

  /*
   * RISK CHART
   *
   * Four aging segments when the API supplies them.
   * Otherwise use backend portfolio at risk vs not at risk.
   */
  const agingAvailable =
    Array.isArray(stats?.portfolioAging) && stats.portfolioAging.length > 0;

  const riskPercent = Math.min(100, Math.max(0, numeric(snapshot.riskPct)));

  const riskChart = useMemo(() => {
    if (stats?.portfolioAging?.length) {
      const values = stats.portfolioAging
        .map((row) => ({
          name: row.label,
          value: Math.max(0, numeric(row.percentage)),
        }))
        .filter((row) => row.value > 0);

      return values;
    }

    if (!exists(stats?.portfolioAtRiskPct)) return [];

    return [
      {
        name: "Current / Not at risk",
        value: 100 - riskPercent,
      },
      {
        name: "At risk",
        value: riskPercent,
      },
    ].filter((row) => row.value > 0);
  }, [stats, riskPercent]);

  const riskColors = agingAvailable ? [GREEN, GOLD, ORANGE, RED] : [GREEN, RED];

  const ownerOnly = numeric(stats?.ownerOnlyOutstanding);
  const normalOutstanding = Math.max(
    0,
    numeric(stats?.outstandingBalance) - ownerOnly,
  );

  const income = stats?.incomeSummary;

  const hasAccounting =
    !!income && Object.values(income).some((value) => exists(value));

  const dateText =
    stats?.asOfDate ??
    (retrievedAt
      ? retrievedAt.toLocaleDateString("en-RW", {
          day: "2-digit",
          month: "short",
          year: "numeric",
        })
      : "—");

  if (loading && !stats) {
    return (
      <div role="status" className="min-h-screen space-y-5 bg-[#F3F6FA] p-5">
        <div className="h-24 animate-pulse rounded-xl bg-slate-200" />
        <div className="grid grid-cols-2 gap-4 xl:grid-cols-4">
          {[1, 2, 3, 4].map((item) => (
            <div
              key={item}
              className="h-32 animate-pulse rounded-xl bg-slate-200"
            />
          ))}
        </div>
        <div className="h-72 animate-pulse rounded-xl bg-slate-200" />
        <p className="text-xs text-slate-500">
          Loading the financial command center…
        </p>
      </div>
    );
  }

  if (!stats) {
    return (
      <main className="flex min-h-[65vh] items-center justify-center bg-[#F3F6FA] p-5">
        <div className="max-w-md rounded-xl border border-slate-200 bg-white p-8 text-center">
          <p className="text-3xl text-red-500">!</p>
          <h1 className="mt-4 text-lg font-black text-[#142B46]">
            Financial Dashboard Unavailable
          </h1>
          <p className="mt-3 text-xs leading-5 text-slate-500">
            {error || "Unable to retrieve financial statistics."}
          </p>
          <button
            type="button"
            onClick={() => void load()}
            className="mt-6 rounded-lg bg-[#102B4A] px-5 py-2.5 text-xs font-extrabold text-white"
          >
            Retry
          </button>
        </div>
      </main>
    );
  }

  return (
    <main className="min-h-screen bg-[#F3F6FA] text-[#142B46]">
      <div className="mx-auto max-w-[1600px] space-y-4 p-4 md:p-5 xl:p-7">
        {/* ===================================================
            DASHBOARD HEADER — MATCHES ORIGINAL PREVIEW
           =================================================== */}

        <header className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <p className="mb-2 flex items-center gap-2 text-[10px] font-black uppercase tracking-[0.18em] text-[#A17D36]">
              <span className="h-2 w-2 rounded-full bg-[#CCA454]" />
              Noble Loan Solutions · Financial Intelligence
            </p>

            <h1 className="text-[25px] font-black tracking-[-0.035em] text-[#102B4A] md:text-[29px]">
              Financial Command Center
            </h1>

            <p className="mt-2 text-[12px] text-[#7D8CA0]">
              Portfolio performance, lending income, credit risk and operational
              priorities
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <span className="rounded-lg border border-[#DEE6EF] bg-white px-3 py-2.5 text-[11px] font-bold text-[#38536F]">
              ◷ {dateText}
            </span>

            <span className="rounded-lg border border-[#DEE6EF] bg-white px-3 py-2.5 text-[11px] font-bold text-[#38536F]">
              {ownerView
                ? "Business Owner · Consolidated"
                : authenticatedOwner
                  ? "Business Owner"
                  : "Normal Portfolio"}
            </span>

            <button
              type="button"
              onClick={() => void load(true)}
              disabled={refreshing}
              className="rounded-lg bg-[#102B4A] px-4 py-2.5 text-[11px] font-extrabold text-white hover:bg-[#193D62] disabled:opacity-60"
            >
              {refreshing ? "Refreshing…" : "↻ Refresh"}
            </button>
          </div>
        </header>

        <div className="flex flex-wrap justify-between gap-2 text-[10px] text-slate-500">
          <span>
            <span className="mr-1 text-emerald-600">●</span>
            Authenticated financial snapshot
          </span>
          <span>Currency: {currency}</span>
        </div>

        {error && (
          <div
            role="alert"
            className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs text-amber-800"
          >
            Refresh failed. Previously retrieved financial figures remain
            visible.
            <button
              type="button"
              onClick={() => void load(true)}
              className="ml-2 font-extrabold underline"
            >
              Retry
            </button>
          </div>
        )}

        {/* ===================================================
            FOUR FINANCIAL KPI CARDS
           =================================================== */}

        <section
          aria-label="Financial overview"
          className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4"
        >
          <Metric
            title="Gross Outstanding Principal"
            value={compact(snapshot.outstanding)}
            description={`${currency} · Current principal exposure`}
            symbol="▣"
            accent={GOLD}
          />

          <Metric
            title={
              exists(snapshot.disbursedMonth)
                ? "Disbursed This Month"
                : "Cumulative Disbursements"
            }
            value={compact(
              exists(snapshot.disbursedMonth)
                ? snapshot.disbursedMonth
                : snapshot.disbursed,
            )}
            description={
              exists(snapshot.disbursedMonth)
                ? `${currency} · Current-month principal`
                : `${currency} · All recorded disbursements`
            }
            symbol="↗"
            accent={NAVY}
          />

          <Metric
            title="Cash Collected This Month"
            value={compact(snapshot.collectedMonth)}
            description={`${currency} · Cash payments received`}
            symbol="↙"
            accent={GREEN}
          />

          <Metric
            title="Portfolio at Risk"
            value={
              exists(snapshot.riskPct) ? `${riskPercent.toFixed(2)}%` : "—"
            }
            description={
              exists(snapshot.riskAmount)
                ? `Exposure: ${money(snapshot.riskAmount)}`
                : "No risk exposure data available"
            }
            symbol="!"
            accent={RED}
          />
        </section>

        {/* ===================================================
            BUSINESS OWNER CONSOLIDATED BREAKDOWN
           =================================================== */}

        {ownerView && (
          <section className="overflow-hidden rounded-xl border border-[#E9D8AF] bg-white">
            <div className="border-b border-[#E9D8AF] bg-[#FFF9EA] px-5 py-4">
              <h2 className="text-xs font-extrabold text-[#81612A]">
                Business Owner — Confidential Portfolio Consolidation
              </h2>
              <p className="mt-1 text-[11px] leading-5 text-[#987E4D]">
                Owner-only loans are included in the consolidated management
                view but excluded from ordinary staff and regulatory reporting.
              </p>
            </div>

            {exists(stats.ownerOnlyOutstanding) ? (
              <div className="grid grid-cols-1 gap-3 p-4 sm:grid-cols-3">
                <div className="rounded-lg bg-[#F3F6FA] p-4">
                  <p className="text-[11px] text-[#8190A0]">
                    Normal outstanding
                  </p>
                  <p className="mt-2 break-words text-xl font-black text-[#102B4A]">
                    {money(normalOutstanding)}
                  </p>
                </div>

                <div className="rounded-lg bg-[#FFF6E4] p-4">
                  <p className="text-[11px] text-[#987637]">
                    Owner-only outstanding
                  </p>
                  <p className="mt-2 break-words text-xl font-black text-[#94702C]">
                    {money(ownerOnly)}
                  </p>
                </div>

                <div className="rounded-lg bg-[#F3F6FA] p-4">
                  <p className="text-[11px] text-[#8190A0]">
                    Consolidated outstanding
                  </p>
                  <p className="mt-2 break-words text-xl font-black text-[#102B4A]">
                    {money(snapshot.outstanding)}
                  </p>
                  <p className="mt-2 text-[10px] text-slate-500">
                    Owner-only loans: {count(stats.ownerOnlyLoanCount)}
                  </p>
                </div>
              </div>
            ) : (
              <p className="p-5 text-xs text-slate-500">
                Consolidated portfolio totals are available. A separate
                owner-only breakdown has not yet been returned by the dashboard
                API.
              </p>
            )}
          </section>
        )}

        {/* ===================================================
            MONTHLY CASH FLOW AND PORTFOLIO QUALITY
           =================================================== */}

        <div className="grid grid-cols-1 gap-3 xl:grid-cols-5">
          <Panel
            className="xl:col-span-3"
            title="Disbursements & Collections"
            subtitle={
              hasMonthlyTrend
                ? "Monthly cash-flow activity · RWF"
                : "Cumulative portfolio cash flow · RWF"
            }
            right={
              <select
                value={months}
                onChange={(event) =>
                  setMonths(Number(event.target.value) as 6 | 12)
                }
                disabled={!hasMonthlyTrend}
                aria-label="Cash-flow period"
                className="rounded-lg border border-[#E2E8F0] bg-white px-3 py-2 text-[11px] font-bold text-[#36516B] disabled:opacity-50"
              >
                <option value={6}>Last 6 months</option>
                <option value={12}>Last 12 months</option>
              </select>
            }
          >
            {cashflow.length ? (
              <div className="h-[240px] w-full">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart
                    data={cashflow}
                    margin={{
                      top: 10,
                      right: 10,
                      bottom: 5,
                      left: -12,
                    }}
                    barGap={4}
                  >
                    <CartesianGrid
                      vertical={false}
                      stroke="#EAF0F5"
                      strokeDasharray="3 3"
                    />

                    <XAxis
                      dataKey="period"
                      axisLine={false}
                      tickLine={false}
                      tick={{
                        fill: "#8190A3",
                        fontSize: 10,
                      }}
                    />

                    <YAxis
                      axisLine={false}
                      tickLine={false}
                      tick={{
                        fill: "#8190A3",
                        fontSize: 10,
                      }}
                      tickFormatter={(value) => compact(value)}
                    />

                    <Tooltip
                      formatter={(value) => money(value as Amount)}
                      contentStyle={{
                        border: "1px solid #E1E8EF",
                        borderRadius: 10,
                        fontSize: 12,
                      }}
                    />

                    <Legend
                      verticalAlign="top"
                      height={35}
                      wrapperStyle={{
                        fontSize: 11,
                      }}
                    />

                    <Bar
                      dataKey="disbursed"
                      name="Disbursed"
                      fill={NAVY}
                      maxBarSize={35}
                      radius={[4, 4, 0, 0]}
                    />

                    <Bar
                      dataKey="collected"
                      name="Collected"
                      fill={GOLD}
                      maxBarSize={35}
                      radius={[4, 4, 0, 0]}
                    />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            ) : (
              <NoData
                title="Cash-flow history unavailable"
                detail="No successful disbursement or collection figures were returned."
              />
            )}

            <div className="mt-4 flex flex-wrap justify-between gap-4 border-t border-slate-100 pt-4">
              <div>
                <p className="text-[10px] text-slate-500">
                  Cumulative disbursed
                </p>
                <p className="mt-1 text-sm font-extrabold text-[#173553]">
                  {money(snapshot.disbursed)}
                </p>
              </div>

              <div className="text-right">
                <p className="text-[10px] text-slate-500">
                  Cumulative collected
                </p>
                <p className="mt-1 text-sm font-extrabold text-[#173553]">
                  {money(snapshot.collected)}
                </p>
              </div>
            </div>
          </Panel>

          <Panel
            className="xl:col-span-2"
            title="Portfolio Quality"
            subtitle={
              agingAvailable
                ? "Outstanding principal by days past due"
                : "Current portfolio risk classification"
            }
          >
            {riskChart.length ? (
              <div className="flex flex-col items-center gap-4 sm:flex-row xl:flex-col 2xl:flex-row">
                <div className="relative h-[190px] w-[190px] shrink-0">
                  <ResponsiveContainer width="100%" height="100%">
                    <PieChart>
                      <Pie
                        data={riskChart}
                        nameKey="name"
                        dataKey="value"
                        innerRadius={62}
                        outerRadius={84}
                        stroke="#FFFFFF"
                        strokeWidth={3}
                      >
                        {riskChart.map((segment, index) => (
                          <Cell
                            key={`${segment.name}-${index}`}
                            fill={riskColors[index % riskColors.length]}
                          />
                        ))}
                      </Pie>

                      <Tooltip
                        formatter={(value) =>
                          `${numeric(value as Amount).toFixed(2)}%`
                        }
                      />
                    </PieChart>
                  </ResponsiveContainer>

                  <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
                    <strong className="text-[24px] font-black text-[#BF5760]">
                      {riskPercent.toFixed(1)}%
                    </strong>
                    <span className="text-[10px] text-slate-500">At risk</span>
                  </div>
                </div>

                <div className="w-full flex-1 space-y-3">
                  {riskChart.map((segment, index) => (
                    <div
                      key={`${segment.name}-${index}`}
                      className="flex items-center justify-between gap-3"
                    >
                      <span className="flex items-center gap-2 text-[11px] text-[#728297]">
                        <span
                          className="h-2.5 w-2.5 shrink-0 rounded-sm"
                          style={{
                            backgroundColor:
                              riskColors[index % riskColors.length],
                          }}
                        />
                        {segment.name}
                      </span>

                      <strong className="text-[11px] text-[#183552]">
                        {segment.value.toFixed(1)}%
                      </strong>
                    </div>
                  ))}
                </div>
              </div>
            ) : (
              <NoData
                title="Portfolio quality unavailable"
                detail="The financial API did not return a risk classification."
              />
            )}

            <div className="mt-5 rounded-lg border border-[#F4DEDA] bg-[#FFF5F3] p-3">
              <p className="text-[11px] leading-5 text-[#A15C58]">
                <strong>Credit watch:</strong> At-risk exposure is{" "}
                {money(snapshot.riskAmount)}. Review delinquent loans and
                provisioning requirements.
              </p>
            </div>
          </Panel>
        </div>

        {/* ===================================================
            ACCOUNTING INCOME AND ATTENTION QUEUE
           =================================================== */}

        <div className="grid grid-cols-1 gap-3 xl:grid-cols-2">
          <Panel
            title="Income & Operating Result"
            subtitle="Recognized general-ledger performance"
            right={
              <span className="text-[10px] font-bold text-slate-400">
                {currency}
              </span>
            }
          >
            {hasAccounting ? (
              <>
                <FinancialRow
                  title="Interest income"
                  value={money(income?.interestIncome)}
                />

                <FinancialRow
                  title="Management fee income"
                  value={money(income?.managementFeeIncome)}
                />

                <FinancialRow
                  title="Application fee income · GL 4100"
                  value={money(income?.applicationFeeIncome)}
                />

                <FinancialRow
                  title="Penalty income"
                  value={money(income?.penaltyIncome)}
                />

                <FinancialRow
                  title="Operating expenses"
                  value={money(income?.operatingExpenses)}
                />

                <FinancialRow
                  title="Impairment / provision expense"
                  value={money(income?.impairmentExpense)}
                />

                <div className="mt-3 rounded-lg bg-[#EFF7F3] px-3">
                  <FinancialRow
                    title="Operating result"
                    value={money(income?.operatingResult)}
                    emphasis
                  />
                </div>

                <p className="mt-3 text-[10px] leading-5 text-slate-500">
                  Recognized GL figures exclude owner-only loans from ordinary
                  company accounting.
                </p>
              </>
            ) : (
              <>
                <NoData
                  title="GL income series not yet available"
                  detail="Recognized interest income, management fees, application fees, expenses and provisions must be supplied by the accounting service."
                />

                <div className="border-t border-slate-100 pt-3">
                  <FinancialRow
                    title="Cash collected this month"
                    value={money(snapshot.collectedMonth)}
                  />

                  <FinancialRow
                    title="Total cash collected"
                    value={money(snapshot.collected)}
                  />

                  <p className="mt-3 text-[10px] text-slate-500">
                    Cash collections are displayed for reference, not treated as
                    accounting revenue or profit.
                  </p>
                </div>
              </>
            )}
          </Panel>

          <Panel
            title="Requires Attention"
            subtitle="Prioritized daily lending operations"
            right={
              <span className="rounded-md bg-[#FFF3E1] px-2.5 py-1 text-[10px] font-extrabold text-[#A47632]">
                ACTION QUEUE
              </span>
            }
          >
            {[
              {
                name: "Overdue repayments",
                subtitle: "Collection follow-up required",
                value: count(snapshot.overdue),
                url: "/dashboard/loans",
                danger: true,
              },
              {
                name: "Pending loan applications",
                subtitle: "Awaiting lending decision",
                value: count(snapshot.pending),
                url: "/dashboard/loans",
                danger: false,
              },
              {
                name: "Open workflow tasks",
                subtitle: "Assigned operational work",
                value: count(tasks.openTasks),
                url: "/dashboard/tasks",
                danger: false,
              },
              {
                name: "Borrower documents",
                subtitle: "Review borrower KYC and documentation",
                value: "Review",
                url: "/dashboard/borrowers",
                danger: false,
              },
            ].map((action) => (
              <button
                type="button"
                key={action.name}
                onClick={() => router.push(action.url)}
                className="group flex w-full items-center justify-between gap-3 border-b border-[#EDF1F5] px-1 py-4 text-left last:border-0 hover:bg-[#F8FAFC]"
              >
                <div className="min-w-0">
                  <p className="text-xs font-bold text-[#193553]">
                    {action.name}
                  </p>

                  <p className="mt-1 text-[11px] text-slate-500">
                    {action.subtitle}
                  </p>
                </div>

                <div className="flex shrink-0 items-center gap-3">
                  <span
                    className={
                      action.danger && numeric(snapshot.overdue) > 0
                        ? "rounded-md bg-red-50 px-2.5 py-1.5 text-[11px] font-extrabold text-red-700"
                        : "rounded-md bg-[#EEF2F7] px-2.5 py-1.5 text-[11px] font-extrabold text-[#3D5874]"
                    }
                  >
                    {action.value}
                  </span>

                  <span className="text-slate-400 transition-transform group-hover:translate-x-1">
                    →
                  </span>
                </div>
              </button>
            ))}

            <div className="mt-4 rounded-lg bg-[#F4F7FA] p-3 text-[11px] leading-5 text-[#7A899B]">
              Select an item to open its related operational workspace. Counts
              come from the authenticated loan and task services.
            </div>
          </Panel>
        </div>

        {/* ===================================================
            ADDITIONAL FINANCE STATISTICS
           =================================================== */}

        <section className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <Metric
            title="Total Receivables"
            value={compact(snapshot.receivables)}
            description={`${currency} · Contractual amounts receivable`}
            symbol="◈"
            accent={NAVY}
          />

          <Metric
            title="Active Loans"
            value={count(snapshot.active)}
            description="Loans currently in active status"
            symbol="✓"
            accent={GREEN}
          />

          <Metric
            title="Borrowers"
            value={count(snapshot.borrowers)}
            description="Reported borrowers in this portfolio"
            symbol="♙"
            accent={GOLD}
          />

          <Metric
            title="Defaulted Loans"
            value={count(snapshot.defaulted)}
            description="Accounts classified as defaulted"
            symbol="!"
            accent={RED}
          />
        </section>

        {/* ===================================================
            RECENT LOANS
           =================================================== */}

        <Panel
          title="Recent Lending Activity"
          subtitle="Recent loans visible in your authorized portfolio"
          right={
            <button
              type="button"
              onClick={() => router.push("/dashboard/loans")}
              className="text-[11px] font-extrabold text-[#99742E] hover:underline"
            >
              View loans →
            </button>
          }
        >
          <div className="overflow-x-auto">
            <table className="w-full min-w-[680px] text-left">
              <thead>
                <tr className="bg-[#F3F6FA] text-[10px] font-extrabold uppercase tracking-wider text-slate-500">
                  <th className="px-4 py-3">Loan Reference</th>
                  <th className="px-4 py-3">Borrower</th>
                  <th className="px-4 py-3 text-right">Principal</th>
                  <th className="px-4 py-3">Status</th>
                  <th className="px-4 py-3">Date</th>
                </tr>
              </thead>

              <tbody>
                {(stats.recentLoans ?? []).length === 0 ? (
                  <tr>
                    <td
                      colSpan={5}
                      className="px-4 py-10 text-center text-xs text-slate-400"
                    >
                      No recent lending activity is available.
                    </td>
                  </tr>
                ) : (
                  (stats.recentLoans ?? []).map((loan, index) => {
                    const borrower =
                      loan.borrowerName ||
                      [loan.borrower?.firstName, loan.borrower?.lastName]
                        .filter(Boolean)
                        .join(" ") ||
                      "Borrower";

                    return (
                      <tr
                        key={String(loan.id ?? index)}
                        className="border-b border-slate-100 text-xs last:border-0 hover:bg-[#F9FBFD]"
                      >
                        <td className="px-4 py-4 font-bold text-[#24496F]">
                          {loan.referenceNumber || "—"}
                        </td>

                        <td className="px-4 py-4 text-[#183552]">{borrower}</td>

                        <td className="px-4 py-4 text-right font-extrabold text-[#183552]">
                          {money(loan.amount)}
                        </td>

                        <td className="px-4 py-4">
                          <StatusPill value={loan.status} />
                        </td>

                        <td className="px-4 py-4 text-slate-500">
                          {(loan.createdAt || loan.startDate)?.slice(0, 10) ??
                            "—"}
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        </Panel>

        {/* ===================================================
            FOOTER
           =================================================== */}

        <footer className="flex flex-col justify-between gap-2 border-t border-[#E0E7EF] pt-4 text-[10px] text-slate-500 sm:flex-row">
          <span>Noble Loan Solutions Ltd · Finance Command Center</span>

          <span>
            {ownerView
              ? "Consolidated management scope"
              : "Standard reporting scope"}
            {" · "}
            Financial figures supplied by the backend
          </span>
        </footer>
      </div>
    </main>
  );
}
