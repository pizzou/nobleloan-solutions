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

/* ============================================================
   NOBLE LOAN SOLUTIONS
   PREMIUM FINANCE COMMAND CENTER
   ============================================================ */

type Money = number | string | null | undefined;

interface CashflowMonth {
  month?: string;
  label?: string;
  disbursed?: Money;
  collected?: Money;
}

interface PortfolioAging {
  label: string;
  percentage?: Money;
  principal?: Money;
}

interface FinancialIncome {
  interestIncome?: Money;
  managementFeeIncome?: Money;
  applicationFeeIncome?: Money;
  penaltyIncome?: Money;
  operatingExpenses?: Money;
  impairmentExpense?: Money;
  operatingResult?: Money;
}

interface RecentLoan {
  id?: string | number;
  referenceNumber?: string;
  borrowerName?: string;
  amount?: Money;
  status?: string;
  nextDueDate?: string;
  createdAt?: string;
  borrower?: {
    firstName?: string;
    lastName?: string;
  };
}

interface DashboardData {
  reportingScope?: string;
  asOfDate?: string;
  currency?: string;

  totalLoans?: Money;
  totalBorrowers?: Money;
  activeLoans?: Money;
  pendingLoans?: Money;
  overdueLoans?: Money;
  defaultedLoans?: Money;
  completedLoans?: Money;

  totalDisbursed?: Money;
  disbursedThisMonth?: Money;
  totalCollected?: Money;
  collectedThisMonth?: Money;
  historicalCollected?: Money;
  outstandingBalance?: Money;
  totalReceivables?: Money;

  portfolioAtRiskPct?: Money;
  portfolioAtRiskAmount?: Money;
  applicationFeesCollected?: Money;
  latePaymentsCount?: Money;

  ownerOnlyOutstanding?: Money;
  ownerOnlyLoanCount?: Money;

  // Optional extensions from the financial reporting API.
  cashflowTrend?: CashflowMonth[];
  portfolioAging?: PortfolioAging[];
  incomeSummary?: FinancialIncome;

  collectionEfficiencyPct?: Money;
  principalGrowthPct?: Money;
  disbursementGrowthPct?: Money;

  kycRenewalsDue?: Money;
  glReconciliationExceptions?: Money;

  recentLoans?: RecentLoan[];
}

interface WorkflowKpi {
  openTasks?: Money;
  urgentTasks?: Money;
  overdueTasks?: Money;
}

const THEME = {
  navy: "#102A46",
  blue: "#214B76",
  gold: "#CCA252",
  green: "#249778",
  amber: "#D5A250",
  orange: "#DD8D59",
  red: "#C65E68",
};

const RISK_COLORS = [THEME.green, THEME.gold, THEME.orange, THEME.red];

/* ============================================================
   SAFE FINANCIAL FORMATTERS
   ============================================================ */

function numberValue(value: Money): number {
  if (value === null || value === undefined || value === "") {
    return 0;
  }

  const parsed = Number(value);

  return Number.isFinite(parsed) ? parsed : 0;
}

function hasNumber(value: Money): boolean {
  return (
    value !== null &&
    value !== undefined &&
    value !== "" &&
    Number.isFinite(Number(value))
  );
}

function formatCount(value: Money): string {
  if (!hasNumber(value)) return "—";

  return numberValue(value).toLocaleString("en-RW", {
    maximumFractionDigits: 0,
  });
}

function formatMoney(value: Money, currency: string = "RWF"): string {
  if (!hasNumber(value)) return "—";

  return `${currency} ${numberValue(value).toLocaleString("en-RW", {
    maximumFractionDigits: 2,
  })}`;
}

function compactMoney(value: Money): string {
  if (!hasNumber(value)) return "—";

  const n = numberValue(value);
  const absolute = Math.abs(n);

  if (absolute >= 1_000_000_000) {
    return `${(n / 1_000_000_000).toFixed(2)}B`;
  }

  if (absolute >= 1_000_000) {
    return `${(n / 1_000_000).toFixed(2)}M`;
  }

  if (absolute >= 1_000) {
    return `${(n / 1_000).toFixed(1)}K`;
  }

  return n.toLocaleString("en-RW");
}

function percentage(value: Money): string {
  return hasNumber(value) ? `${numberValue(value).toFixed(2)}%` : "—";
}

function unwrapDashboard(response: unknown): DashboardData {
  let current: unknown = response;

  // Supports direct API responses and normal response wrappers.
  for (let i = 0; i < 3; i++) {
    if (!current || typeof current !== "object") break;

    const value = current as Record<string, unknown>;

    if (
      "totalLoans" in value ||
      "totalDisbursed" in value ||
      "outstandingBalance" in value
    ) {
      return value as DashboardData;
    }

    current = value.data;
  }

  throw new Error("Dashboard API did not return financial data.");
}

function isOwnerAccount(auth: unknown): boolean {
  if (!auth || typeof auth !== "object") return false;

  const context = auth as {
    isBusinessOwner?: boolean;
    user?: {
      role?: string;
      roles?: Array<string | { name?: string }>;
    };
  };

  if (context.isBusinessOwner === true) return true;

  const roles = [
    context.user?.role || "",
    ...(context.user?.roles || []).map((r) =>
      typeof r === "string" ? r : r.name || "",
    ),
  ];

  return roles.some(
    (role) =>
      role
        .trim()
        .toUpperCase()
        .replace(/[\s-]+/g, "_") === "BUSINESS_OWNER",
  );
}

/* ============================================================
   UI COMPONENTS
   ============================================================ */

function FinanceCard({
  label,
  value,
  note,
  trend,
  symbol,
  accent,
}: {
  label: string;
  value: string;
  note: string;
  trend?: string;
  symbol: string;
  accent: string;
}) {
  return (
    <article className="relative min-w-0 overflow-hidden rounded-xl border border-[#DFE7F0] bg-white p-5 shadow-[0_4px_20px_rgba(15,34,60,0.035)]">
      <div
        className="absolute inset-x-0 top-0 h-[3px]"
        style={{ backgroundColor: accent }}
      />

      <div className="flex items-start justify-between gap-3">
        <span className="max-w-[175px] text-[12px] font-bold leading-5 text-[#677A92]">
          {label}
        </span>

        <span
          aria-hidden="true"
          className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-[#F0F4F9] text-xl font-bold"
          style={{ color: accent }}
        >
          {symbol}
        </span>
      </div>

      <div className="mt-5 break-words text-[clamp(1.35rem,2vw,1.95rem)] font-black tracking-tight text-[#142C49]">
        {value}
      </div>

      <p className="mt-3 text-[11px] leading-5 text-[#8392A4]">
        {trend && <strong className="mr-2 text-[#23876C]">{trend}</strong>}
        {note}
      </p>
    </article>
  );
}

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
      className={`min-w-0 overflow-hidden rounded-xl border border-[#DFE7F0] bg-white shadow-[0_4px_20px_rgba(15,34,60,0.03)] ${className}`}
    >
      <div className="flex flex-wrap items-start justify-between gap-3 border-b border-[#EDF1F5] px-5 py-4">
        <div>
          <h2 className="text-[14px] font-extrabold text-[#18334F]">{title}</h2>

          {subtitle && (
            <p className="mt-1.5 text-[11px] leading-5 text-[#8292A5]">
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

function FinancialLine({
  label,
  value,
  total = false,
}: {
  label: string;
  value: string;
  total?: boolean;
}) {
  return (
    <div
      className={
        total
          ? "mt-3 flex items-center justify-between gap-3 rounded-lg bg-[#EDF7F2] px-4 py-3.5"
          : "flex items-center justify-between gap-3 border-b border-[#EEF1F5] py-3"
      }
    >
      <span
        className={
          total
            ? "text-xs font-extrabold text-[#2D6957]"
            : "text-xs text-[#697B92]"
        }
      >
        {label}
      </span>

      <strong
        className={
          total
            ? "text-right text-lg font-black text-[#188768]"
            : "text-right text-[13px] font-extrabold text-[#203B56]"
        }
      >
        {value}
      </strong>
    </div>
  );
}

function Unavailable({ title, detail }: { title: string; detail: string }) {
  return (
    <div className="flex min-h-[190px] flex-col items-center justify-center px-4 text-center">
      <span className="flex h-12 w-12 items-center justify-center rounded-xl bg-[#F0F4F9] text-2xl text-[#8396AA]">
        ▤
      </span>

      <p className="mt-4 text-sm font-bold text-[#556E86]">{title}</p>

      <p className="mt-2 max-w-sm text-[11px] leading-5 text-[#90A0B1]">
        {detail}
      </p>
    </div>
  );
}

function ActionItem({
  title,
  detail,
  value,
  urgent,
  onClick,
}: {
  title: string;
  detail: string;
  value: string;
  urgent?: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="group flex w-full items-center justify-between gap-4 border-b border-[#EDF1F5] py-4 text-left last:border-0 hover:bg-[#FAFBFD] focus-visible:rounded-md focus-visible:outline focus-visible:outline-2 focus-visible:outline-[#CCA252]"
    >
      <div className="min-w-0">
        <p className="text-[12px] font-extrabold text-[#183552]">{title}</p>

        <p className="mt-1.5 text-[11px] text-[#8595A6]">{detail}</p>
      </div>

      <div className="flex shrink-0 items-center gap-2">
        <span
          className={
            urgent
              ? "rounded-lg bg-[#FFF0ED] px-3 py-2 text-xs font-extrabold text-[#B95859]"
              : "rounded-lg bg-[#F0F4F8] px-3 py-2 text-xs font-extrabold text-[#365672]"
          }
        >
          {value}
        </span>

        <span className="text-slate-400 transition-transform group-hover:translate-x-1">
          →
        </span>
      </div>
    </button>
  );
}

/* ============================================================
   DASHBOARD PAGE
   ============================================================ */

export default function DashboardPage() {
  const router = useRouter();
  const auth = useAuth();

  const [data, setData] = useState<DashboardData | null>(null);
  const [workflow, setWorkflow] = useState<WorkflowKpi>({});

  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState("");

  const [months, setMonths] = useState<6 | 12>(6);
  const [retrievedAt, setRetrievedAt] = useState<Date | null>(null);

  const authenticatedOwner = isOwnerAccount(auth);

  // Reporting scope MUST originate from the backend.
  const consolidatedView =
    authenticatedOwner &&
    data?.reportingScope === "BUSINESS_OWNER_CONSOLIDATED";

  const currency = data?.currency || auth.currency || "RWF";

  const money = useCallback(
    (value: Money) => formatMoney(value, currency),
    [currency],
  );

  /* ==========================================================
     LOAD AUTHORITATIVE DASHBOARD DATA
     ========================================================== */

  const load = useCallback(async (refresh = false) => {
    if (refresh) {
      setRefreshing(true);
    } else {
      setLoading(true);
    }

    setError("");

    try {
      // No client-supplied includeOwnerOnly parameter.
      const response = await loanApi.dashboard();
      const payload = unwrapDashboard(response);

      setData(payload);
      setRetrievedAt(new Date());

      try {
        const taskResult = await taskApi.kpi();
        setWorkflow(taskResult as WorkflowKpi);
      } catch {
        // Task service failure must not hide financial data.
      }
    } catch (err: unknown) {
      setError(
        err instanceof Error
          ? err.message
          : "Unable to retrieve dashboard financial data.",
      );
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  /* ==========================================================
     CHART SERIES
     ========================================================== */

  const hasMonthlyData =
    Array.isArray(data?.cashflowTrend) && data.cashflowTrend.length > 0;

  const cashflow = useMemo(() => {
    if (data?.cashflowTrend?.length) {
      return data.cashflowTrend.slice(-months).map((item) => ({
        period: item.month || item.label || "Period",
        disbursed: numberValue(item.disbursed),
        collected: numberValue(item.collected),
      }));
    }

    // Fallback shows true cumulative values, not fake months.
    if (hasNumber(data?.totalDisbursed) || hasNumber(data?.totalCollected)) {
      return [
        {
          period: "To date",
          disbursed: numberValue(data?.totalDisbursed),
          collected: numberValue(data?.totalCollected),
        },
      ];
    }

    return [];
  }, [data, months]);

  const hasAging =
    Array.isArray(data?.portfolioAging) && data.portfolioAging.length > 0;

  const portfolioRisk = Math.max(
    0,
    Math.min(100, numberValue(data?.portfolioAtRiskPct)),
  );

  const agingData = useMemo(() => {
    if (hasAging && data?.portfolioAging) {
      const outstanding = numberValue(data.outstandingBalance);

      return data.portfolioAging
        .map((item) => {
          let value = 0;

          if (hasNumber(item.percentage)) {
            value = numberValue(item.percentage);
          } else if (hasNumber(item.principal) && outstanding > 0) {
            value = (numberValue(item.principal) / outstanding) * 100;
          }

          return {
            name: item.label,
            value: Math.max(0, value),
          };
        })
        .filter((item) => item.value > 0);
    }

    if (!hasNumber(data?.portfolioAtRiskPct)) {
      return [];
    }

    return [
      {
        name: "Not at risk",
        value: 100 - portfolioRisk,
      },
      {
        name: "At risk",
        value: portfolioRisk,
      },
    ].filter((item) => item.value > 0);
  }, [data, hasAging, portfolioRisk]);

  const riskColors = hasAging ? RISK_COLORS : [THEME.green, THEME.red];

  const income = data?.incomeSummary;

  const incomeAvailable =
    !!income && Object.values(income).some((value) => hasNumber(value));

  const ownerOnly = numberValue(data?.ownerOnlyOutstanding);

  const normalOutstanding = Math.max(
    0,
    numberValue(data?.outstandingBalance) - ownerOnly,
  );

  const snapshotDate =
    data?.asOfDate ||
    (retrievedAt
      ? retrievedAt.toLocaleDateString("en-RW", {
          day: "2-digit",
          month: "short",
          year: "numeric",
        })
      : "—");

  /* ==========================================================
     LOADING AND ERROR STATES
     ========================================================== */

  if (loading && !data) {
    return (
      <main role="status" className="min-h-screen space-y-5 bg-[#F4F7FB] p-5">
        <div className="h-28 animate-pulse rounded-xl bg-[#E5EBF3]" />

        <div className="grid grid-cols-2 gap-4 xl:grid-cols-4">
          {[1, 2, 3, 4].map((item) => (
            <div
              key={item}
              className="h-36 animate-pulse rounded-xl bg-[#E5EBF3]"
            />
          ))}
        </div>

        <div className="h-80 animate-pulse rounded-xl bg-[#E5EBF3]" />

        <p className="text-xs text-slate-500">
          Loading financial command center…
        </p>
      </main>
    );
  }

  if (!data) {
    return (
      <main className="flex min-h-[70vh] items-center justify-center bg-[#F4F7FB] p-6">
        <section className="max-w-md rounded-xl border border-[#E0E7F0] bg-white p-9 text-center">
          <div className="text-3xl text-red-600">!</div>

          <h1 className="mt-4 text-xl font-black text-[#142C49]">
            Financial Dashboard Unavailable
          </h1>

          <p className="mt-3 text-sm leading-6 text-slate-500">{error}</p>

          <button
            type="button"
            onClick={() => void load()}
            className="mt-6 rounded-lg bg-[#102A46] px-5 py-3 text-xs font-extrabold text-white"
          >
            Try Again
          </button>
        </section>
      </main>
    );
  }

  /* ==========================================================
     PREMIUM FINANCE COMMAND CENTER
     ========================================================== */

  return (
    <main className="min-h-screen bg-[#F4F7FB] text-[#172E49]">
      <div className="mx-auto max-w-[1600px] space-y-4 px-4 py-6 lg:px-7 lg:py-8">
        {/* ================= HEADER ================= */}

        <header className="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
          <div className="max-w-2xl">
            <p className="mb-2 text-[10px] font-black uppercase tracking-[0.18em] text-[#AB8438]">
              Executive Financial Intelligence
            </p>

            <h1 className="text-[28px] font-black tracking-[-0.04em] text-[#102A46] md:text-[34px]">
              Finance Command Center
            </h1>

            <p className="mt-2 text-[12px] leading-6 text-[#74849B]">
              Financial performance, portfolio quality, collections, and the
              priorities that matter today.
            </p>
          </div>

          <div className="flex flex-wrap gap-2">
            <span className="rounded-lg border border-[#DFE6EF] bg-white px-3 py-2.5 text-[11px] font-bold text-[#435B76]">
              {consolidatedView
                ? "Business Owner · Consolidated"
                : authenticatedOwner
                  ? "Business Owner · Authorized view"
                  : "Normal Portfolio"}
            </span>

            <span className="rounded-lg border border-[#DFE6EF] bg-white px-3 py-2.5 text-[11px] font-bold text-[#435B76]">
              {snapshotDate}
            </span>

            <button
              type="button"
              disabled={refreshing}
              onClick={() => void load(true)}
              className="rounded-lg bg-[#102A46] px-4 py-2.5 text-[11px] font-extrabold text-white hover:bg-[#204263] disabled:opacity-50"
            >
              {refreshing ? "Refreshing…" : "↻ Refresh"}
            </button>
          </div>
        </header>

        <div className="flex flex-wrap justify-between gap-3 text-[10px] font-semibold text-[#8897AA]">
          <span>
            <span className="mr-1 text-emerald-600">●</span>
            Authenticated financial reporting
          </span>

          <span>RWF financial reporting · Backend-controlled scope</span>
        </div>

        {error && (
          <div
            role="alert"
            className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs text-amber-800"
          >
            Financial refresh failed. Previously loaded figures remain visible.
          </div>
        )}

        {/* ============= OWNER CONSOLIDATION ============= */}

        {consolidatedView && (
          <section className="overflow-hidden rounded-xl border border-[#E9D5A1] bg-[#FFFBF0] p-4">
            <h2 className="text-xs font-black text-[#815E21]">
              ◈ Business Owner · Consolidated Portfolio
            </h2>

            <p className="mt-1 text-[11px] leading-5 text-[#92794A]">
              Owner-only loans are included in management figures and remain
              excluded from normal staff, accounting, BNR and CRB reporting.
            </p>

            {hasNumber(data.ownerOnlyOutstanding) && (
              <div className="mt-3 grid grid-cols-1 gap-2 sm:grid-cols-3">
                <div className="rounded-lg border border-[#EDE3CE] bg-white p-4">
                  <p className="text-[11px] text-[#7C8A99]">
                    Normal outstanding
                  </p>

                  <p className="mt-2 break-words text-lg font-black text-[#193A58]">
                    {money(normalOutstanding)}
                  </p>
                </div>

                <div className="rounded-lg border border-[#EDE3CE] bg-[#FFF4DC] p-4">
                  <p className="text-[11px] text-[#9A7A3F]">
                    Owner-only outstanding
                  </p>

                  <p className="mt-2 break-words text-lg font-black text-[#92702D]">
                    {money(ownerOnly)}
                  </p>
                </div>

                <div className="rounded-lg border border-[#EDE3CE] bg-white p-4">
                  <p className="text-[11px] text-[#7C8A99]">
                    Consolidated outstanding
                  </p>

                  <p className="mt-2 break-words text-lg font-black text-[#193A58]">
                    {money(data.outstandingBalance)}
                  </p>
                </div>
              </div>
            )}
          </section>
        )}

        {/* ================= FINANCIAL KPIS ================= */}

        <section
          aria-label="Financial performance"
          className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4"
        >
          <FinanceCard
            label="Outstanding Principal"
            value={compactMoney(data.outstandingBalance)}
            note={`${currency} · Current loan principal exposure`}
            trend={
              hasNumber(data.principalGrowthPct)
                ? `${percentage(data.principalGrowthPct)} change`
                : undefined
            }
            symbol="▣"
            accent={THEME.gold}
          />

          <FinanceCard
            label={
              hasNumber(data.disbursedThisMonth)
                ? "Disbursed This Month"
                : "Total Disbursements"
            }
            value={compactMoney(
              hasNumber(data.disbursedThisMonth)
                ? data.disbursedThisMonth
                : data.totalDisbursed,
            )}
            note={
              hasNumber(data.disbursedThisMonth)
                ? `${currency} · Monthly gross principal`
                : `${currency} · Cumulative disbursements`
            }
            trend={
              hasNumber(data.disbursementGrowthPct)
                ? `${percentage(data.disbursementGrowthPct)} change`
                : undefined
            }
            symbol="↗"
            accent={THEME.blue}
          />

          <FinanceCard
            label="Collections This Month"
            value={compactMoney(data.collectedThisMonth)}
            note={`${currency} · Cash actually received`}
            trend={
              hasNumber(data.collectionEfficiencyPct)
                ? `${percentage(data.collectionEfficiencyPct)} efficiency`
                : undefined
            }
            symbol="↙"
            accent={THEME.green}
          />

          <FinanceCard
            label="Portfolio at Risk"
            value={percentage(data.portfolioAtRiskPct)}
            note={`At-risk exposure: ${money(data.portfolioAtRiskAmount)}`}
            symbol="!"
            accent={THEME.red}
          />
        </section>

        {/* ================= PRIMARY CHARTS ================= */}

        <div className="grid grid-cols-1 gap-3 xl:grid-cols-5">
          <Panel
            className="xl:col-span-3"
            title="Disbursements & Collections"
            subtitle={
              hasMonthlyData
                ? "Monthly trend · RWF"
                : "Cumulative financial movement · RWF"
            }
            right={
              <select
                value={months}
                disabled={!hasMonthlyData}
                onChange={(event) =>
                  setMonths(Number(event.target.value) as 6 | 12)
                }
                aria-label="Reporting period"
                className="rounded-lg border border-[#E0E7F0] bg-white px-3 py-2 text-[11px] font-bold text-[#435B76] disabled:opacity-50"
              >
                <option value={6}>Last 6 Months</option>
                <option value={12}>Last 12 Months</option>
              </select>
            }
          >
            {cashflow.length > 0 ? (
              <div className="h-[270px] w-full">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart
                    data={cashflow}
                    margin={{
                      top: 10,
                      right: 8,
                      bottom: 4,
                      left: -10,
                    }}
                    barGap={5}
                  >
                    <CartesianGrid
                      stroke="#EDF1F5"
                      vertical={false}
                      strokeDasharray="3 3"
                    />

                    <XAxis
                      dataKey="period"
                      axisLine={false}
                      tickLine={false}
                      tick={{
                        fill: "#8292A5",
                        fontSize: 11,
                      }}
                    />

                    <YAxis
                      axisLine={false}
                      tickLine={false}
                      tick={{
                        fill: "#8292A5",
                        fontSize: 10,
                      }}
                      tickFormatter={(value) => compactMoney(value)}
                    />

                    <Tooltip
                      formatter={(value) => money(value as Money)}
                      contentStyle={{
                        border: "1px solid #E0E7F0",
                        borderRadius: 10,
                        fontSize: 12,
                      }}
                    />

                    <Legend
                      verticalAlign="top"
                      height={38}
                      wrapperStyle={{
                        fontSize: 11,
                      }}
                    />

                    <Bar
                      name="Disbursed"
                      dataKey="disbursed"
                      fill={THEME.blue}
                      radius={[4, 4, 0, 0]}
                      maxBarSize={32}
                    />

                    <Bar
                      name="Collected"
                      dataKey="collected"
                      fill={THEME.gold}
                      radius={[4, 4, 0, 0]}
                      maxBarSize={32}
                    />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            ) : (
              <Unavailable
                title="Cash-flow figures unavailable"
                detail="No successful disbursement or posted collection totals were returned."
              />
            )}

            <div className="mt-4 grid grid-cols-1 gap-4 border-t border-[#EDF1F5] pt-4 sm:grid-cols-2">
              <div>
                <p className="text-[11px] text-[#8292A5]">
                  Cumulative disbursed
                </p>

                <p className="mt-1 text-sm font-black text-[#183552]">
                  {money(data.totalDisbursed)}
                </p>
              </div>

              <div className="sm:text-right">
                <p className="text-[11px] text-[#8292A5]">
                  Cumulative collected
                </p>

                <p className="mt-1 text-sm font-black text-[#183552]">
                  {money(data.totalCollected)}
                </p>
              </div>
            </div>
          </Panel>

          <Panel
            className="xl:col-span-2"
            title="Portfolio Quality"
            subtitle={
              hasAging
                ? "Outstanding principal by delinquency category"
                : "Current credit-risk classification"
            }
            right={
              <span className="text-[10px] font-extrabold uppercase tracking-wide text-[#A77E38]">
                Credit Risk
              </span>
            }
          >
            {agingData.length > 0 ? (
              <div className="flex flex-col items-center justify-center gap-4 sm:flex-row xl:flex-col 2xl:flex-row">
                <div className="relative h-[200px] w-[200px] shrink-0">
                  <ResponsiveContainer width="100%" height="100%">
                    <PieChart>
                      <Pie
                        data={agingData}
                        dataKey="value"
                        nameKey="name"
                        innerRadius={66}
                        outerRadius={90}
                        stroke="#FFFFFF"
                        strokeWidth={3}
                      >
                        {agingData.map((item, index) => (
                          <Cell
                            key={`${item.name}-${index}`}
                            fill={riskColors[index % riskColors.length]}
                          />
                        ))}
                      </Pie>

                      <Tooltip
                        formatter={(value) =>
                          `${numberValue(value as Money).toFixed(2)}%`
                        }
                      />
                    </PieChart>
                  </ResponsiveContainer>

                  <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
                    <strong className="text-3xl font-black text-[#C15B65]">
                      {percentage(data.portfolioAtRiskPct)}
                    </strong>

                    <span className="mt-1 text-[11px] text-[#8795A5]">
                      At risk
                    </span>
                  </div>
                </div>

                <div className="w-full flex-1 space-y-3">
                  {agingData.map((item, index) => (
                    <div
                      key={`${item.name}-${index}`}
                      className="flex items-center justify-between gap-3 text-[11px]"
                    >
                      <span className="flex items-center gap-2 text-[#6F8197]">
                        <span
                          className="h-2.5 w-2.5 rounded-sm"
                          style={{
                            backgroundColor:
                              riskColors[index % riskColors.length],
                          }}
                        />
                        {item.name}
                      </span>

                      <strong className="text-[#1D3854]">
                        {item.value.toFixed(1)}%
                      </strong>
                    </div>
                  ))}
                </div>
              </div>
            ) : (
              <Unavailable
                title="Credit-risk data unavailable"
                detail="Portfolio quality metrics were not returned."
              />
            )}

            <div className="mt-5 rounded-lg border border-[#F3DFDA] bg-[#FFF4F1] p-3 text-[11px] leading-5 text-[#A75D59]">
              <strong>Credit watch:</strong> At-risk principal exposure is{" "}
              {money(data.portfolioAtRiskAmount)}. Review recovery and
              provisioning requirements.
            </div>
          </Panel>
        </div>

        {/* ================= FINANCE & OPERATIONS ================= */}

        <div className="grid grid-cols-1 gap-3 xl:grid-cols-2">
          <Panel
            title="Income & Operating Result"
            subtitle="Recognized general-ledger performance"
            right={
              <span className="text-[10px] font-bold text-[#9BA5B2]">
                {currency}
              </span>
            }
          >
            {incomeAvailable ? (
              <>
                <FinancialLine
                  label="Interest income"
                  value={money(income?.interestIncome)}
                />

                <FinancialLine
                  label="Management fee income"
                  value={money(income?.managementFeeIncome)}
                />

                <FinancialLine
                  label="Application fee income · GL 4100"
                  value={money(income?.applicationFeeIncome)}
                />

                <FinancialLine
                  label="Penalty income"
                  value={money(income?.penaltyIncome)}
                />

                <FinancialLine
                  label="Operating expenses"
                  value={money(income?.operatingExpenses)}
                />

                <FinancialLine
                  label="Impairment / provision expense"
                  value={money(income?.impairmentExpense)}
                />

                <FinancialLine
                  label="Operating result"
                  value={money(income?.operatingResult)}
                  total
                />
              </>
            ) : (
              <>
                <Unavailable
                  title="Accounting results not yet available"
                  detail="Recognized income, operating expenses and impairment must be provided by the general ledger. Cash collections are not treated as profit."
                />

                <div className="border-t border-[#EDF1F5] pt-2">
                  <FinancialLine
                    label="Collections this month"
                    value={money(data.collectedThisMonth)}
                  />

                  <FinancialLine
                    label="Total collections"
                    value={money(data.totalCollected)}
                  />
                </div>
              </>
            )}
          </Panel>

          <Panel
            title="Requires Attention"
            subtitle="High-priority financial operations"
            right={
              <span className="rounded-md bg-[#FFF2E1] px-2.5 py-1.5 text-[10px] font-black uppercase tracking-wide text-[#A67934]">
                Action Queue
              </span>
            }
          >
            <ActionItem
              title="Overdue Repayments"
              detail="Review aging and recovery assignments"
              value={`${formatCount(data.overdueLoans)} loans`}
              urgent={numberValue(data.overdueLoans) > 0}
              onClick={() => router.push("/dashboard/loans")}
            />

            <ActionItem
              title="Pending Lending Approvals"
              detail="Applications awaiting credit decisions"
              value={`${formatCount(data.pendingLoans)} loans`}
              onClick={() => router.push("/dashboard/loans")}
            />

            <ActionItem
              title="Open Workflow Tasks"
              detail="Assigned operational work"
              value={formatCount(workflow.openTasks)}
              urgent={numberValue(workflow.urgentTasks) > 0}
              onClick={() => router.push("/dashboard/tasks")}
            />

            <ActionItem
              title="KYC Documents Requiring Renewal"
              detail="Expired, missing or rejected borrower documents"
              value={
                hasNumber(data.kycRenewalsDue)
                  ? `${formatCount(data.kycRenewalsDue)} files`
                  : "Review"
              }
              onClick={() => router.push("/dashboard/borrowers")}
            />

            <ActionItem
              title="GL Reconciliation Exceptions"
              detail="Review differences between finance systems"
              value={formatCount(data.glReconciliationExceptions)}
              urgent={numberValue(data.glReconciliationExceptions) > 0}
              onClick={() => router.push("/dashboard/accounting")}
            />
          </Panel>
        </div>

        {/* ================= SECONDARY METRICS ================= */}

        <section
          aria-label="Portfolio summary"
          className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4"
        >
          <FinanceCard
            label="Total Loan Receivables"
            value={compactMoney(data.totalReceivables)}
            note={`${currency} · Total contractual receivables`}
            symbol="◈"
            accent={THEME.blue}
          />

          <FinanceCard
            label="Active Loans"
            value={formatCount(data.activeLoans)}
            note="Current active lending accounts"
            symbol="✓"
            accent={THEME.green}
          />

          <FinanceCard
            label="Borrowers"
            value={formatCount(data.totalBorrowers)}
            note="Borrowers in the authorized portfolio"
            symbol="♙"
            accent={THEME.gold}
          />

          <FinanceCard
            label="Defaulted Loans"
            value={formatCount(data.defaultedLoans)}
            note="Accounts requiring recovery"
            symbol="!"
            accent={THEME.red}
          />
        </section>

        {/* ================= RECENT LENDING ================= */}

        <Panel
          title="Recent Lending Activity"
          subtitle="Latest loan records within your authorized reporting scope"
          right={
            <button
              type="button"
              onClick={() => router.push("/dashboard/loans")}
              className="text-[11px] font-extrabold text-[#A27A34] hover:underline"
            >
              View Portfolio →
            </button>
          }
        >
          <div className="overflow-x-auto">
            <table className="w-full min-w-[700px] text-left">
              <thead>
                <tr className="bg-[#F4F7FA] text-[10px] font-extrabold uppercase tracking-wide text-[#77899C]">
                  <th className="px-4 py-3">Loan Reference</th>
                  <th className="px-4 py-3">Borrower</th>
                  <th className="px-4 py-3 text-right">Principal</th>
                  <th className="px-4 py-3">Status</th>
                  <th className="px-4 py-3">Due Date</th>
                </tr>
              </thead>

              <tbody>
                {(data.recentLoans || []).length === 0 ? (
                  <tr>
                    <td
                      colSpan={5}
                      className="px-4 py-12 text-center text-xs text-slate-400"
                    >
                      No recent lending activity is available.
                    </td>
                  </tr>
                ) : (
                  data.recentLoans?.map((loan, index) => {
                    const borrowerName =
                      loan.borrowerName ||
                      [loan.borrower?.firstName, loan.borrower?.lastName]
                        .filter(Boolean)
                        .join(" ") ||
                      "Borrower";

                    const loanStatus = (loan.status || "UNKNOWN").toUpperCase();

                    const overdue =
                      loanStatus === "OVERDUE" || loanStatus === "DEFAULTED";

                    return (
                      <tr
                        key={String(loan.id ?? index)}
                        className="border-b border-[#EDF1F5] text-xs last:border-0 hover:bg-[#F9FBFD]"
                      >
                        <td className="px-4 py-4 font-extrabold text-[#285580]">
                          {loan.referenceNumber || "—"}
                        </td>

                        <td className="px-4 py-4 font-semibold text-[#415B74]">
                          {borrowerName}
                        </td>

                        <td className="px-4 py-4 text-right font-extrabold text-[#1C3954]">
                          {money(loan.amount)}
                        </td>

                        <td className="px-4 py-4">
                          <span
                            className={
                              overdue
                                ? "rounded-md bg-[#FFF0ED] px-2.5 py-1.5 text-[10px] font-black text-[#BD5C59]"
                                : "rounded-md bg-[#EAF7F0] px-2.5 py-1.5 text-[10px] font-black text-[#168063]"
                            }
                          >
                            {loanStatus.replaceAll("_", " ")}
                          </span>
                        </td>

                        <td className="px-4 py-4 text-[#64798E]">
                          {loan.nextDueDate?.slice(0, 10) || "—"}
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        </Panel>

        {/* ================= FOOTER ================= */}

        <footer className="flex flex-col justify-between gap-2 border-t border-[#E1E8F0] pt-4 text-[10px] text-[#8999AB] sm:flex-row">
          <span>Noble Loan Solutions Ltd · Finance Command Center</span>

          <span>
            {consolidatedView
              ? "Business Owner consolidated management reporting"
              : "Authorized standard portfolio reporting"}
          </span>
        </footer>
      </div>
    </main>
  );
}
