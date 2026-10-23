"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
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
import {
  Activity,
  AlertTriangle,
  ArrowDownLeft,
  ArrowRight,
  ArrowUpRight,
  BriefcaseBusiness,
  CalendarClock,
  CheckCircle2,
  ClipboardList,
  FileBarChart2,
  Landmark,
  RefreshCw,
  ShieldCheck,
  Users,
  Wallet,
} from "lucide-react";

import { loanApi, taskApi } from "@/services/api";
import { useAuth } from "@/hooks/useAuth";

type Money = number | string | null | undefined;

type CashflowPoint = {
  month: string;
  disbursed: Money;
  collected: Money;
};

type RiskBucket = {
  label: string;
  percentage: Money;
};

type IncomeData = {
  interestIncome?: Money;
  managementFeeIncome?: Money;
  applicationFeeIncome?: Money;
  penaltyIncome?: Money;
  operatingExpenses?: Money;
  impairmentExpense?: Money;
  operatingResult?: Money;
};

type RecentLoan = {
  id?: string | number;
  referenceNumber?: string;
  borrowerName?: string;
  status?: string;
  amount?: Money;
  createdAt?: string;
  borrower?: {
    firstName?: string;
    lastName?: string;
  };
};

type Snapshot = {
  reportingScope?: string;
  asOfDate?: string;
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
  outstandingBalance?: Money;
  totalReceivables?: Money;
  portfolioAtRiskPct?: Money;
  portfolioAtRiskAmount?: Money;
  latePaymentsCount?: Money;
  cashflowTrend?: CashflowPoint[];
  portfolioAging?: RiskBucket[];
  incomeSummary?: IncomeData;
  ownerOnlyOutstanding?: Money;
  ownerOnlyLoanCount?: Money;
  recentLoans?: RecentLoan[];
};

const C = {
  navy: "#0A2039",
  blue: "#173E66",
  gold: "#CCA454",
  green: "#258E73",
  amber: "#D59C52",
  orange: "#D97F55",
  red: "#C75A64",
};

function n(value: Money): number {
  if (value === null || value === undefined || value === "") {
    return 0;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function has(value: Money): boolean {
  return (
    value !== null &&
    value !== undefined &&
    value !== "" &&
    Number.isFinite(Number(value))
  );
}

function unwrap(value: unknown): Snapshot {
  if (!value || typeof value !== "object") return {};
  const object = value as Record<string, unknown>;
  const data = object.data;

  if (
    data &&
    typeof data === "object" &&
    ("totalLoans" in data ||
      "outstandingBalance" in data ||
      "totalDisbursed" in data)
  ) {
    return data as Snapshot;
  }

  return object as Snapshot;
}

function formatMoney(value: Money, currency: string): string {
  if (!has(value)) return "—";
  return `${currency} ${n(value).toLocaleString("en-RW", {
    maximumFractionDigits: 2,
  })}`;
}

function compactMoney(value: Money): string {
  if (!has(value)) return "—";
  const amount = n(value);
  if (Math.abs(amount) >= 1_000_000_000) {
    return `${(amount / 1_000_000_000).toFixed(2)}B`;
  }
  if (Math.abs(amount) >= 1_000_000) {
    return `${(amount / 1_000_000).toFixed(2)}M`;
  }
  if (Math.abs(amount) >= 1_000) {
    return `${(amount / 1_000).toFixed(1)}K`;
  }
  return amount.toLocaleString("en-RW");
}

function Panel({
  title,
  subtitle,
  children,
  className = "",
  right,
}: {
  title: string;
  subtitle?: string;
  children: React.ReactNode;
  className?: string;
  right?: React.ReactNode;
}) {
  return (
    <section
      className={`min-w-0 overflow-hidden rounded-2xl border border-[#E0E7F0] bg-white shadow-[0_4px_22px_rgba(15,34,60,.035)] ${className}`}
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

function Kpi({
  label,
  value,
  foot,
  icon: Icon,
  accent,
}: {
  label: string;
  value: string;
  foot: string;
  icon: React.ElementType;
  accent: string;
}) {
  return (
    <div className="relative min-w-0 rounded-2xl border border-[#E0E7F0] bg-white p-5 shadow-[0_4px_22px_rgba(15,34,60,.035)]">
      <span
        className="absolute bottom-0 left-0 top-0 w-[3px]"
        style={{ background: accent }}
      />
      <div className="flex items-start justify-between gap-2">
        <p className="text-[11px] font-bold text-slate-500">{label}</p>
        <span className="rounded-xl bg-[#F1F5FA] p-2.5">
          <Icon size={17} style={{ color: accent }} />
        </span>
      </div>
      <div className="mt-4 break-words text-[clamp(1.2rem,1.8vw,1.75rem)] font-black tracking-tight text-[#102943]">
        {value}
      </div>
      <p className="mt-2 text-[11px] text-slate-500">{foot}</p>
    </div>
  );
}

function Row({
  label,
  value,
  highlight = false,
}: {
  label: string;
  value: string;
  highlight?: boolean;
}) {
  return (
    <div className="flex items-center justify-between gap-4 border-b border-slate-100 py-3 last:border-0">
      <span className="text-[12px] text-slate-600">{label}</span>
      <span
        className={`text-right text-[13px] font-extrabold ${
          highlight ? "text-emerald-700" : "text-[#18324F]"
        }`}
      >
        {value}
      </span>
    </div>
  );
}

function Empty({ message }: { message: string }) {
  return (
    <div className="flex min-h-52 flex-col items-center justify-center gap-3 text-center text-slate-400">
      <FileBarChart2 size={26} strokeWidth={1.5} />
      <p className="max-w-xs text-xs leading-5">{message}</p>
    </div>
  );
}

export default function DashboardPage() {
  const router = useRouter();
  const auth = useAuth();
  const currency = auth.currency || "RWF";

  const [data, setData] = useState<Snapshot | null>(null);
  const [tasks, setTasks] = useState({
    openTasks: 0,
    urgentTasks: 0,
  });
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState("");
  const [period, setPeriod] = useState<6 | 12>(6);
  const [loadedAt, setLoadedAt] = useState<Date | null>(null);

  // Do not derive portfolio access from an arbitrary browser
  // parameter. The backend must enforce the authorized scope.
  const ownerRole = Boolean(auth.isBusinessOwner);
  const ownerScope =
    ownerRole && data?.reportingScope === "BUSINESS_OWNER_CONSOLIDATED";

  const money = useCallback(
    (value: Money) => formatMoney(value, currency),
    [currency],
  );

  const fetchDashboard = useCallback(async (refresh = false) => {
    if (refresh) setRefreshing(true);
    else setLoading(true);

    setError("");

    try {
      const response = await loanApi.dashboard();
      setData(unwrap(response));
      setLoadedAt(new Date());

      try {
        const kpi = await taskApi.kpi();
        setTasks({
          openTasks: n(kpi?.openTasks),
          urgentTasks: n(kpi?.urgentTasks),
        });
      } catch {
        // The financial dashboard remains available even
        // when the workflow service is temporarily unavailable.
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "Dashboard request failed");
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    void fetchDashboard();
  }, [fetchDashboard]);

  const chart = useMemo(() => {
    const points = Array.isArray(data?.cashflowTrend) ? data.cashflowTrend : [];

    return points.slice(-period).map((point) => ({
      month: point.month,
      disbursed: n(point.disbursed),
      collected: n(point.collected),
    }));
  }, [data, period]);

  const risk = Math.min(100, Math.max(0, n(data?.portfolioAtRiskPct)));

  const riskData = useMemo(() => {
    if (data?.portfolioAging?.length) {
      return data.portfolioAging
        .map((item) => ({
          name: item.label,
          value: n(item.percentage),
        }))
        .filter((item) => item.value > 0);
    }

    if (!has(data?.portfolioAtRiskPct)) return [];

    return [
      { name: "Not at risk", value: 100 - risk },
      { name: "At risk", value: risk },
    ];
  }, [data, risk]);

  const riskColors = data?.portfolioAging?.length
    ? [C.green, C.gold, C.orange, C.red]
    : [C.green, C.red];

  const income = data?.incomeSummary;
  const hasIncome = income && Object.values(income).some(has);

  const ownerAmount = n(data?.ownerOnlyOutstanding);
  const normalAmount = Math.max(0, n(data?.outstandingBalance) - ownerAmount);

  if (loading && !data) {
    return (
      <main className="space-y-5 bg-[#F3F6FA] p-5">
        <div className="h-40 animate-pulse rounded-2xl bg-slate-200" />
        <div className="grid grid-cols-2 gap-4 xl:grid-cols-4">
          {[1, 2, 3, 4].map((item) => (
            <div
              key={item}
              className="h-32 animate-pulse rounded-2xl bg-slate-200"
            />
          ))}
        </div>
        <div className="h-64 animate-pulse rounded-2xl bg-slate-200" />
      </main>
    );
  }

  if (!data) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center p-6">
        <div className="max-w-md rounded-2xl border bg-white p-8 text-center">
          <AlertTriangle className="mx-auto text-red-500" size={32} />
          <h1 className="mt-4 text-lg font-extrabold">
            Financial dashboard unavailable
          </h1>
          <p className="mt-3 text-sm text-slate-500">
            {error || "The authorized portfolio could not be loaded."}
          </p>
          <button
            type="button"
            onClick={() => void fetchDashboard()}
            className="mt-5 rounded-lg bg-[#0A2039] px-5 py-2.5 text-sm font-bold text-white"
          >
            Retry
          </button>
        </div>
      </div>
    );
  }

  return (
    <main className="min-h-screen bg-[#F3F6FA] text-[#142B46]">
      <div className="mx-auto max-w-[1600px] space-y-4 p-4 lg:p-6">
        {/* Header: original finance dashboard visual */}
        <header className="flex flex-wrap items-start justify-between gap-4 pb-1">
          <div>
            <div className="mb-2 text-[10px] font-black uppercase tracking-[.2em] text-[#A17E3B]">
              Noble Loan Solutions · Financial Intelligence
            </div>
            <h1 className="text-[26px] font-black tracking-tight text-[#0A2039]">
              Financial Command Center
            </h1>
            <p className="mt-1 text-xs text-slate-500">
              Lending performance, cash collections, credit quality and
              financial operations
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <span className="rounded-lg border border-[#E1E8EF] bg-white px-3 py-2 text-[11px] font-bold text-[#36516B]">
              <ShieldCheck size={13} className="mr-1 inline" />
              {ownerScope
                ? "Business Owner · Consolidated"
                : "Normal Portfolio"}
            </span>
            <button
              type="button"
              onClick={() => void fetchDashboard(true)}
              disabled={refreshing}
              className="flex items-center gap-2 rounded-lg bg-[#0A2039] px-4 py-2.5 text-[11px] font-extrabold text-white hover:bg-[#173E66] disabled:opacity-60"
            >
              <RefreshCw
                size={14}
                className={refreshing ? "animate-spin" : ""}
              />
              Refresh
            </button>
          </div>
        </header>

        <div className="flex flex-wrap justify-between gap-2 text-[10px] text-slate-500">
          <span>
            {data.asOfDate
              ? `Financial snapshot: ${data.asOfDate}`
              : "Latest available financial snapshot"}
          </span>
          <span>
            {loadedAt
              ? `Retrieved ${loadedAt.toLocaleTimeString("en-RW")}`
              : ""}
          </span>
        </div>

        {error && (
          <div
            role="alert"
            className="rounded-xl border border-amber-200 bg-amber-50 p-3 text-xs text-amber-900"
          >
            Refresh failed. Previously retrieved figures are shown.
          </div>
        )}

        {/* Four main KPI cards */}
        <section className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <Kpi
            label="Gross Outstanding Principal"
            value={money(data.outstandingBalance)}
            foot="Outstanding loan principal exposure"
            icon={Wallet}
            accent={C.gold}
          />
          <Kpi
            label="Disbursed This Month"
            value={money(data.disbursedThisMonth)}
            foot="Gross principal disbursed in the current month"
            icon={ArrowUpRight}
            accent={C.blue}
          />
          <Kpi
            label="Cash Collected This Month"
            value={money(data.collectedThisMonth)}
            foot="Actual cash received during the month"
            icon={ArrowDownLeft}
            accent={C.green}
          />
          <Kpi
            label="Portfolio at Risk"
            value={
              has(data.portfolioAtRiskPct)
                ? `${n(data.portfolioAtRiskPct).toFixed(2)}%`
                : "—"
            }
            foot={`At-risk exposure: ${money(data.portfolioAtRiskAmount)}`}
            icon={Activity}
            accent={C.red}
          />
        </section>

        {/* Private owner reporting — no scope toggle */}
        {ownerScope && (
          <section className="overflow-hidden rounded-2xl border border-[#E9D9B0] bg-white">
            <div className="border-b border-[#E9D9B0] bg-[#FFF8E9] px-5 py-4">
              <div className="flex items-center gap-2 text-[12px] font-extrabold text-[#8C6929]">
                <BriefcaseBusiness size={16} />
                Business Owner — Portfolio Consolidation
              </div>
              <p className="mt-1 text-[11px] text-[#947B4D]">
                Owner-only loans are included in management totals, not in
                normal regulatory or staff reporting.
              </p>
            </div>
            {has(data.ownerOnlyOutstanding) ? (
              <div className="grid gap-3 p-4 sm:grid-cols-3">
                <div className="rounded-xl bg-[#F5F7FA] p-4">
                  <p className="text-[11px] text-slate-500">Normal principal</p>
                  <p className="mt-2 break-words text-lg font-black">
                    {money(normalAmount)}
                  </p>
                </div>
                <div className="rounded-xl bg-[#FBF3E3] p-4">
                  <p className="text-[11px] text-[#987A39]">
                    Owner-only principal
                  </p>
                  <p className="mt-2 break-words text-lg font-black text-[#8D6827]">
                    {money(ownerAmount)}
                  </p>
                </div>
                <div className="rounded-xl bg-[#F5F7FA] p-4">
                  <p className="text-[11px] text-slate-500">
                    Consolidated principal
                  </p>
                  <p className="mt-2 break-words text-lg font-black">
                    {money(data.outstandingBalance)}
                  </p>
                </div>
              </div>
            ) : (
              <p className="p-5 text-xs text-slate-500">
                Separate owner-only breakdown is not supplied by the current
                dashboard API.
              </p>
            )}
          </section>
        )}

        {/* Main analytics */}
        <div className="grid grid-cols-1 gap-4 xl:grid-cols-5">
          <Panel
            className="xl:col-span-3"
            title="Disbursements & Collections"
            subtitle="Monthly cash movement · RWF"
            right={
              <select
                value={period}
                onChange={(e) => setPeriod(Number(e.target.value) as 6 | 12)}
                disabled={!data.cashflowTrend?.length}
                aria-label="Cash-flow chart period"
                className="rounded-lg border border-[#E1E8EF] bg-white px-3 py-2 text-[11px] font-bold text-[#36516B] disabled:opacity-50"
              >
                <option value={6}>Last 6 months</option>
                <option value={12}>Last 12 months</option>
              </select>
            }
          >
            {chart.length ? (
              <div className="h-[285px] w-full">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart
                    data={chart}
                    margin={{ left: -15, right: 8, top: 8, bottom: 0 }}
                    barGap={4}
                  >
                    <CartesianGrid
                      vertical={false}
                      stroke="#EAF0F5"
                      strokeDasharray="3 3"
                    />
                    <XAxis
                      dataKey="month"
                      tick={{ fontSize: 10, fill: "#8190A3" }}
                      axisLine={false}
                      tickLine={false}
                    />
                    <YAxis
                      tickFormatter={(value) => compactMoney(value)}
                      tick={{ fontSize: 10, fill: "#8190A3" }}
                      axisLine={false}
                      tickLine={false}
                    />
                    <Tooltip
                      formatter={(value) => money(Number(value))}
                      contentStyle={{
                        borderRadius: 10,
                        border: "1px solid #E1E8EF",
                        fontSize: 12,
                      }}
                    />
                    <Legend
                      wrapperStyle={{
                        fontSize: 11,
                        paddingTop: 12,
                      }}
                    />
                    <Bar
                      dataKey="disbursed"
                      name="Disbursed"
                      fill={C.blue}
                      radius={[4, 4, 0, 0]}
                      maxBarSize={28}
                    />
                    <Bar
                      dataKey="collected"
                      name="Collected"
                      fill={C.gold}
                      radius={[4, 4, 0, 0]}
                      maxBarSize={28}
                    />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            ) : (
              <Empty message="Monthly disbursement and collection series is not available in the dashboard API yet. No historical amounts have been estimated." />
            )}
            <div className="mt-4 grid grid-cols-2 gap-3 border-t border-slate-100 pt-4">
              <div>
                <p className="text-[11px] text-slate-500">
                  Cumulative disbursed
                </p>
                <p className="mt-1 text-sm font-extrabold">
                  {money(data.totalDisbursed)}
                </p>
              </div>
              <div>
                <p className="text-[11px] text-slate-500">
                  Cumulative collected
                </p>
                <p className="mt-1 text-sm font-extrabold">
                  {money(data.totalCollected)}
                </p>
              </div>
            </div>
          </Panel>

          <Panel
            className="xl:col-span-2"
            title="Portfolio Quality"
            subtitle="Outstanding principal by delinquency classification"
          >
            {riskData.length ? (
              <div className="flex flex-col items-center gap-4 sm:flex-row">
                <div className="relative h-[190px] w-[190px] shrink-0">
                  <ResponsiveContainer width="100%" height="100%">
                    <PieChart>
                      <Pie
                        data={riskData}
                        dataKey="value"
                        nameKey="name"
                        innerRadius={61}
                        outerRadius={82}
                        stroke="#FFF"
                        strokeWidth={3}
                      >
                        {riskData.map((item, index) => (
                          <Cell
                            key={`${item.name}-${index}`}
                            fill={riskColors[index % riskColors.length]}
                          />
                        ))}
                      </Pie>
                      <Tooltip
                        formatter={(value) => `${Number(value).toFixed(2)}%`}
                      />
                    </PieChart>
                  </ResponsiveContainer>
                  <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
                    <strong className="text-2xl font-black text-[#B74F59]">
                      {risk.toFixed(1)}%
                    </strong>
                    <span className="text-[10px] text-slate-500">At risk</span>
                  </div>
                </div>
                <div className="w-full min-w-0 space-y-3">
                  {riskData.map((item, index) => (
                    <div
                      key={`${item.name}-${index}`}
                      className="flex items-center justify-between gap-3 text-[11px]"
                    >
                      <span className="flex items-center gap-2 text-slate-600">
                        <i
                          className="h-2.5 w-2.5 rounded-sm"
                          style={{
                            background: riskColors[index % riskColors.length],
                          }}
                        />
                        {item.name}
                      </span>
                      <strong className="text-[#142B46]">
                        {item.value.toFixed(1)}%
                      </strong>
                    </div>
                  ))}
                </div>
              </div>
            ) : (
              <Empty message="No risk classification is available." />
            )}

            <div className="mt-4 rounded-xl border border-red-100 bg-[#FFF5F4] p-3 text-[11px] leading-5 text-[#AA5C59]">
              <AlertTriangle size={14} className="mr-1 inline" />
              At-risk principal: {money(data.portfolioAtRiskAmount)}. Review
              overdue and defaulted loans regularly.
            </div>
          </Panel>
        </div>

        {/* Income and operational attention */}
        <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
          <Panel
            title="Income & Operating Result"
            subtitle="Accounting recognition · general ledger"
          >
            {hasIncome ? (
              <>
                <Row
                  label="Interest income"
                  value={money(income?.interestIncome)}
                />
                <Row
                  label="Management fee income"
                  value={money(income?.managementFeeIncome)}
                />
                <Row
                  label="Application fee income · GL 4100"
                  value={money(income?.applicationFeeIncome)}
                />
                <Row
                  label="Penalty income"
                  value={money(income?.penaltyIncome)}
                />
                <Row
                  label="Operating expenses"
                  value={money(income?.operatingExpenses)}
                />
                <Row
                  label="Provision / impairment expense"
                  value={money(income?.impairmentExpense)}
                />
                <div className="mt-3 rounded-lg bg-[#F0F7F3] px-3">
                  <Row
                    label="Operating result"
                    value={money(income?.operatingResult)}
                    highlight
                  />
                </div>
              </>
            ) : (
              <Empty message="Recognized GL income, operating expenses and provisions have not been supplied by the dashboard API. Collections are not treated as income or profit." />
            )}
          </Panel>

          <Panel
            title="Requires Attention"
            subtitle="Daily financial and lending operations"
            right={
              <span className="rounded-md bg-[#FFF2DF] px-2.5 py-1 text-[10px] font-extrabold text-[#A57732]">
                ACTION QUEUE
              </span>
            }
          >
            {[
              {
                name: "Overdue repayments",
                subtitle: "Collections follow-up required",
                count: n(data.overdueLoans),
                path: "/dashboard/loans",
                icon: CalendarClock,
              },
              {
                name: "Pending loan applications",
                subtitle: "Waiting for lending decisions",
                count: n(data.pendingLoans),
                path: "/dashboard/loans",
                icon: ClipboardList,
              },
              {
                name: "Open workflow tasks",
                subtitle: "Assigned operational work",
                count: tasks.openTasks,
                path: "/dashboard/tasks",
                icon: BriefcaseBusiness,
              },
              {
                name: "Borrower records",
                subtitle: "Review borrower information and documents",
                count: null,
                path: "/dashboard/borrowers",
                icon: Users,
              },
            ].map((item) => {
              const Icon = item.icon;
              return (
                <button
                  type="button"
                  key={item.name}
                  onClick={() => router.push(item.path)}
                  className="group flex w-full items-center justify-between gap-3 border-b border-slate-100 py-4 text-left last:border-0 hover:bg-slate-50"
                >
                  <span className="flex items-center gap-3">
                    <span className="rounded-lg bg-[#EFF3F8] p-2.5 text-[#325473]">
                      <Icon size={16} />
                    </span>
                    <span>
                      <span className="block text-xs font-bold text-[#18324F]">
                        {item.name}
                      </span>
                      <span className="mt-1 block text-[11px] text-slate-500">
                        {item.subtitle}
                      </span>
                    </span>
                  </span>
                  <span className="flex items-center gap-2">
                    {item.count !== null && (
                      <span className="rounded-lg bg-[#F0F3F7] px-2.5 py-1.5 text-xs font-extrabold">
                        {item.count}
                      </span>
                    )}
                    <ArrowRight
                      size={15}
                      className="text-slate-400 transition-transform group-hover:translate-x-1"
                    />
                  </span>
                </button>
              );
            })}
          </Panel>
        </div>

        {/* Supporting metrics */}
        <section className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <Kpi
            label="Total Receivables"
            value={money(data.totalReceivables)}
            foot="All recorded contractual receivables"
            icon={Landmark}
            accent={C.blue}
          />
          <Kpi
            label="Active Loans"
            value={String(n(data.activeLoans))}
            foot="Current active loan accounts"
            icon={CheckCircle2}
            accent={C.green}
          />
          <Kpi
            label="Borrowers"
            value={String(n(data.totalBorrowers))}
            foot="Count supplied by the reporting API"
            icon={Users}
            accent={C.gold}
          />
          <Kpi
            label="Defaulted Loans"
            value={String(n(data.defaultedLoans))}
            foot="Loans classified as defaulted"
            icon={AlertTriangle}
            accent={C.red}
          />
        </section>

        {/* Recent lending activity */}
        <Panel
          title="Recent Loan Activity"
          subtitle="Loans visible in the authenticated reporting scope"
          right={
            <button
              type="button"
              onClick={() => router.push("/dashboard/loans")}
              className="text-xs font-extrabold text-[#99722F]"
            >
              View loans →
            </button>
          }
        >
          <div className="overflow-x-auto">
            <table className="w-full min-w-[620px] text-left">
              <thead>
                <tr className="bg-[#F3F6FA] text-[10px] uppercase tracking-wider text-slate-500">
                  <th className="px-4 py-3">Reference</th>
                  <th className="px-4 py-3">Borrower</th>
                  <th className="px-4 py-3 text-right">Principal</th>
                  <th className="px-4 py-3">Status</th>
                  <th className="px-4 py-3">Date</th>
                </tr>
              </thead>
              <tbody>
                {(data.recentLoans ?? []).length ? (
                  data.recentLoans?.map((loan, index) => {
                    const borrower =
                      loan.borrowerName ||
                      [loan.borrower?.firstName, loan.borrower?.lastName]
                        .filter(Boolean)
                        .join(" ") ||
                      "—";

                    return (
                      <tr
                        key={`${loan.id ?? index}`}
                        className="border-b border-slate-100 text-xs last:border-0"
                      >
                        <td className="px-4 py-4 font-bold text-[#244A70]">
                          {loan.referenceNumber || "—"}
                        </td>
                        <td className="px-4 py-4">{borrower}</td>
                        <td className="px-4 py-4 text-right font-extrabold">
                          {money(loan.amount)}
                        </td>
                        <td className="px-4 py-4">
                          <span className="rounded-md bg-[#F0F3F7] px-2 py-1 font-bold">
                            {loan.status || "—"}
                          </span>
                        </td>
                        <td className="px-4 py-4 text-slate-500">
                          {loan.createdAt?.slice(0, 10) || "—"}
                        </td>
                      </tr>
                    );
                  })
                ) : (
                  <tr>
                    <td
                      colSpan={5}
                      className="py-10 text-center text-xs text-slate-400"
                    >
                      No recent loans available.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </Panel>

        <footer className="flex flex-wrap items-center justify-between gap-2 border-t border-[#E1E8F0] pt-4 text-[10px] text-slate-500">
          <span>Noble Loan Solutions Ltd · Finance Command Center</span>
          <span>
            {ownerScope
              ? "Consolidated management reporting"
              : "Normal portfolio reporting"}
          </span>
        </footer>
      </div>
    </main>
  );
}
