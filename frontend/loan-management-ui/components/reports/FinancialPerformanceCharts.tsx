"use client";

import {
  Bar,
  CartesianGrid,
  Cell,
  ComposedChart,
  Legend,
  Line,
  LineChart,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

type Numeric = number | string | null | undefined;

export interface MonthlyFinancialPoint {
  month: string;
  label: string;
  from: string;
  to: string;
  revenue: number;
  expenses: number;
  profit: number;
  available?: boolean;
}

export interface CreditQualityPoint {
  quality: string;
  count: number;
  amount: number;
}

export interface CashFlowSummary {
  cashUsedForLending?: Numeric;
  operatingCashInflows?: Numeric;
  cashFromCollections?: Numeric;
  cashFromFees?: Numeric;
  otherCashMovement?: Numeric;
  netChangeInCash?: Numeric;
}

interface Props {
  monthly: MonthlyFinancialPoint[];
  creditQuality: CreditQualityPoint[];
  cashFlow: CashFlowSummary | null;
  reportingFrom: string;
  reportingTo: string;
}

const numberValue = (value: Numeric): number => {
  if (value === null || value === undefined || value === "") return 0;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
};

const formatRwf = (value: number): string =>
  new Intl.NumberFormat("en-RW", {
    style: "currency",
    currency: "RWF",
    maximumFractionDigits: 0,
  }).format(Number.isFinite(value) ? value : 0);

const formatCompactRwf = (value: number): string => {
  const absolute = Math.abs(value);
  if (absolute >= 1_000_000_000)
    return `RF ${(value / 1_000_000_000).toFixed(1)}B`;
  if (absolute >= 1_000_000) return `RF ${(value / 1_000_000).toFixed(1)}M`;
  if (absolute >= 1_000) return `RF ${(value / 1_000).toFixed(0)}K`;
  return `RF ${value.toFixed(0)}`;
};

const tooltipFormatter = (value: number | string) => formatRwf(Number(value));

const qualityColors: Record<string, string> = {
  NORMAL: "#15803d",
  WATCH: "#ca8a04",
  SUBSTANDARD: "#ea580c",
  DOUBTFUL: "#dc2626",
  LOSS: "#7f1d1d",
  RESTRUCTURED: "#64748b",
};

function ChartCard({
  title,
  description,
  children,
}: {
  title: string;
  description: string;
  children: React.ReactNode;
}) {
  return (
    <section className="min-w-0 rounded-xl border border-slate-200 bg-white p-4 shadow-sm sm:p-5">
      <h3 className="text-sm font-bold text-slate-900">{title}</h3>
      <p className="mt-1 text-xs leading-5 text-slate-500">{description}</p>
      <div className="mt-4 h-[280px] w-full" role="img" aria-label={title}>
        {children}
      </div>
    </section>
  );
}

function EmptyChart({ message }: { message: string }) {
  return (
    <div className="flex h-full items-center justify-center rounded-lg border border-dashed border-slate-200 px-5 text-center text-xs text-slate-500">
      {message}
    </div>
  );
}

export default function FinancialPerformanceCharts({
  monthly,
  creditQuality,
  cashFlow,
  reportingFrom,
  reportingTo,
}: Props) {
  const trend = monthly.filter((row) => row.available !== false);
  const quality = creditQuality
    .filter((row) => Number.isFinite(row.amount) && row.amount >= 0)
    .sort((a, b) => b.amount - a.amount);

  const cashMovements = cashFlow
    ? [
        {
          source: "Lending deployed",
          amount: -Math.abs(numberValue(cashFlow.cashUsedForLending)),
          color: "#dc2626",
        },
        {
          source: "Operating cash inflows",
          amount: numberValue(
            cashFlow.operatingCashInflows ?? cashFlow.cashFromCollections,
          ),
          color: "#15803d",
        },
        {
          source: "Fees received",
          amount: numberValue(cashFlow.cashFromFees),
          color: "#0f766e",
        },
        {
          source: "Other movements",
          amount: numberValue(cashFlow.otherCashMovement),
          color: "#64748b",
        },
      ]
    : [];

  const netCashChange = numberValue(cashFlow?.netChangeInCash);
  const trendFrom = trend[0]?.from || "—";
  const trendTo = trend[trend.length - 1]?.to || "—";

  return (
    <section className="mt-10" aria-labelledby="financial-performance-heading">
      <div className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-[10px] font-bold uppercase tracking-[0.15em] text-slate-500">
            Management analytics
          </p>
          <h2
            id="financial-performance-heading"
            className="mt-1 text-xl font-bold tracking-tight text-slate-950"
          >
            Financial performance charts
          </h2>
          <p className="mt-1 max-w-3xl text-xs leading-5 text-slate-500">
            Accounting-based trends and portfolio exposure for business review.
            Values are drawn from the system&apos;s accounting reports and
            current classified loan balances; they are not forecasts.
          </p>
        </div>
        <div className="text-xs font-medium text-slate-500 sm:text-right">
          <p>
            Monthly trend: {trendFrom} to {trendTo}
          </p>
          <p className="mt-1">
            Statement period: {reportingFrom || "—"} to {reportingTo || "—"}
          </p>
        </div>
      </div>

      <div className="grid min-w-0 gap-4 xl:grid-cols-2">
        <ChartCard
          title="Income, expenses and net income"
          description="Monthly financial results. A sustained gap between income and expenses indicates operating profitability; net income may be negative."
        >
          {trend.length ? (
            <ResponsiveContainer width="100%" height="100%">
              <LineChart
                data={trend}
                margin={{ top: 8, right: 12, left: 8, bottom: 4 }}
              >
                <CartesianGrid stroke="#e2e8f0" strokeDasharray="3 3" />
                <XAxis
                  dataKey="label"
                  tick={{ fontSize: 11, fill: "#64748b" }}
                  tickLine={false}
                  axisLine={{ stroke: "#cbd5e1" }}
                />
                <YAxis
                  tickFormatter={formatCompactRwf}
                  tick={{ fontSize: 10, fill: "#64748b" }}
                  tickLine={false}
                  axisLine={false}
                  width={82}
                />
                <Tooltip
                  formatter={tooltipFormatter}
                  labelFormatter={(label) => `Month: ${label}`}
                  contentStyle={{
                    borderRadius: 8,
                    borderColor: "#cbd5e1",
                    fontSize: 12,
                  }}
                />
                <Legend wrapperStyle={{ fontSize: 11 }} />
                <ReferenceLine y={0} stroke="#64748b" />
                <Line
                  type="monotone"
                  dataKey="revenue"
                  name="Income"
                  stroke="#2563eb"
                  strokeWidth={2.5}
                  dot={{ r: 3 }}
                  activeDot={{ r: 5 }}
                  connectNulls={false}
                />
                <Line
                  type="monotone"
                  dataKey="expenses"
                  name="Expenses"
                  stroke="#ea580c"
                  strokeWidth={2.5}
                  dot={{ r: 3 }}
                  activeDot={{ r: 5 }}
                  connectNulls={false}
                />
                <Line
                  type="monotone"
                  dataKey="profit"
                  name="Net income"
                  stroke="#15803d"
                  strokeWidth={2.5}
                  dot={{ r: 3 }}
                  activeDot={{ r: 5 }}
                  connectNulls={false}
                />
              </LineChart>
            </ResponsiveContainer>
          ) : (
            <EmptyChart message="Monthly accounting results are not available for this period." />
          )}
        </ChartCard>

        <ChartCard
          title="Outstanding exposure by credit quality"
          description="Current outstanding balance grouped by the loan's recorded credit-quality classification. This is exposure, not a delinquency ratio or regulatory provision calculation."
        >
          {quality.length ? (
            <ResponsiveContainer width="100%" height="100%">
              <ComposedChart
                data={quality}
                layout="vertical"
                margin={{ top: 4, right: 18, left: 8, bottom: 4 }}
              >
                <CartesianGrid
                  stroke="#e2e8f0"
                  strokeDasharray="3 3"
                  horizontal={false}
                />
                <XAxis
                  type="number"
                  tickFormatter={formatCompactRwf}
                  tick={{ fontSize: 10, fill: "#64748b" }}
                  tickLine={false}
                  axisLine={{ stroke: "#cbd5e1" }}
                />
                <YAxis
                  type="category"
                  dataKey="quality"
                  width={105}
                  tick={{ fontSize: 10, fill: "#475569" }}
                  tickLine={false}
                  axisLine={false}
                />
                <Tooltip
                  formatter={(
                    value: number | string,
                    _name: string,
                    item: any,
                  ) => [
                    formatRwf(Number(value)),
                    `${item?.payload?.count ?? 0} facilities · outstanding`,
                  ]}
                  contentStyle={{
                    borderRadius: 8,
                    borderColor: "#cbd5e1",
                    fontSize: 12,
                  }}
                />
                <Bar
                  dataKey="amount"
                  name="Outstanding exposure"
                  radius={[0, 4, 4, 0]}
                  maxBarSize={28}
                >
                  {quality.map((item) => (
                    <Cell
                      key={item.quality}
                      fill={
                        qualityColors[item.quality.trim().toUpperCase()] ??
                        "#64748b"
                      }
                    />
                  ))}
                </Bar>
              </ComposedChart>
            </ResponsiveContainer>
          ) : (
            <EmptyChart message="Credit-quality exposure is unavailable. Check whether active loan classifications have been recorded." />
          )}
        </ChartCard>

        <ChartCard
          title="Cash movement by source"
          description="Cash used for lending is shown as an outflow; operating inflows, application-fee cash and other movements retain their reported sign. Operating inflows are not labelled as collections because the accounting API does not separately expose a verified collection-only cash total. Net change is shown separately to avoid double-counting it in the component bars."
        >
          {cashMovements.length ? (
            <ResponsiveContainer width="100%" height="100%">
              <ComposedChart
                data={cashMovements}
                margin={{ top: 8, right: 12, left: 8, bottom: 34 }}
              >
                <CartesianGrid stroke="#e2e8f0" strokeDasharray="3 3" />
                <XAxis
                  dataKey="source"
                  interval={0}
                  angle={-14}
                  textAnchor="end"
                  height={55}
                  tick={{ fontSize: 10, fill: "#64748b" }}
                  tickLine={false}
                  axisLine={{ stroke: "#cbd5e1" }}
                />
                <YAxis
                  tickFormatter={formatCompactRwf}
                  tick={{ fontSize: 10, fill: "#64748b" }}
                  tickLine={false}
                  axisLine={false}
                  width={82}
                />
                <Tooltip
                  formatter={tooltipFormatter}
                  contentStyle={{
                    borderRadius: 8,
                    borderColor: "#cbd5e1",
                    fontSize: 12,
                  }}
                />
                <ReferenceLine y={0} stroke="#64748b" />
                <Bar
                  dataKey="amount"
                  name="Cash movement"
                  radius={[4, 4, 0, 0]}
                >
                  {cashMovements.map((item) => (
                    <Cell key={item.source} fill={item.color} />
                  ))}
                </Bar>
              </ComposedChart>
            </ResponsiveContainer>
          ) : (
            <EmptyChart message="Cash-flow components are not available for this reporting period." />
          )}
        </ChartCard>

        <section className="flex min-w-0 flex-col justify-between rounded-xl border border-slate-200 bg-slate-900 p-5 text-white sm:p-6">
          <div>
            <p className="text-[10px] font-bold uppercase tracking-[0.15em] text-slate-300">
              Liquidity indicator
            </p>
            <h3 className="mt-2 text-lg font-bold">Net change in cash</h3>
            <p className="mt-2 text-xs leading-5 text-slate-300">
              Net movement reported by the cash-flow statement for the selected
              statement period. This is not the same as the bank-account balance
              or available cash unless those accounts are fully reconciled.
            </p>
          </div>
          <div className="mt-8">
            <p
              className={`text-3xl font-bold tracking-tight ${netCashChange < 0 ? "text-rose-300" : "text-emerald-300"}`}
            >
              {cashFlow ? formatRwf(netCashChange) : "Not available"}
            </p>
            <p className="mt-2 text-xs text-slate-300">
              {cashFlow
                ? netCashChange < 0
                  ? "Cash decreased during the period"
                  : netCashChange > 0
                    ? "Cash increased during the period"
                    : "No net cash movement reported"
                : "Cash-flow data could not be loaded."}
            </p>
          </div>
          <div className="mt-8 border-t border-slate-700 pt-4 text-[11px] leading-5 text-slate-300">
            For external presentation, confirm the trial balance is balanced and
            cash accounts reconcile to bank statements before relying on this
            indicator.
          </div>
        </section>
      </div>
    </section>
  );
}
