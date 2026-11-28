"use client";

import {
  Bar,
  CartesianGrid,
  Cell,
  ComposedChart,
  Legend,
  Line,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

import { reportAmount, type LedgerCashFlowData } from "./LedgerCashFlow";

export interface MonthlyFinancialPoint {
  month: string;
  label: string;
  from: string;
  to: string;
  revenue: number | null;
  expenses: number | null;
  profit: number | null;
}

export interface CreditQualityPoint {
  quality: string;
  count: number;
  amount: number;
}

interface Props {
  monthly: MonthlyFinancialPoint[];
  creditQuality: CreditQualityPoint[];
  cashFlow: LedgerCashFlowData | null;
  currency: string;
  reportingFrom: string;
  reportingTo: string;
}

function amountOrNull(value: number | string | null | undefined): number | null {
  if (value == null || value === "") return null;
  const amount = Number(value);
  return Number.isFinite(amount) ? amount : null;
}

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
  currency,
  reportingFrom,
  reportingTo,
}: Props) {
  const trend = monthly;
  const quality = creditQuality
    .filter((row) => Number.isFinite(row.amount) && row.amount >= 0)
    .sort((a, b) => b.amount - a.amount);

  const outflow = (value: number | string | null | undefined) => {
    const amount = amountOrNull(value);
    return amount == null ? null : -amount;
  };
  const cashMovements = [
    { source: "Repayments", amount: amountOrNull(cashFlow?.cashFromRepayments), color: "#0f766e" },
    { source: "Loan payouts", amount: outflow(cashFlow?.netCashDisbursed), color: "#b91c1c" },
    { source: "Refunds", amount: outflow(cashFlow?.cashRefundedToBorrowers), color: "#b91c1c" },
    { source: "Other cash, net", amount: amountOrNull(cashFlow?.otherCashFlowsNet), color: "#475569" },
  ].filter((movement) => movement.amount != null);

  const netCashChange = amountOrNull(cashFlow?.netChangeInCash);
  const trendFrom = trend[0]?.from || "—";
  const trendTo = trend[trend.length - 1]?.to || "—";
  const compactAmount = new Intl.NumberFormat("en", { notation: "compact", maximumFractionDigits: 1 });
  const formatAxis = (amount: number) => compactAmount.format(amount);
  const tooltipFormatter = (amount: number | string) => reportAmount(amount, currency);

  return (
    <section className="mt-10" aria-labelledby="financial-performance-heading">
      <div className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h2
            id="financial-performance-heading"
            className="mt-1 text-xl font-bold tracking-tight text-slate-950"
          >
            Financial performance
          </h2>
          <p className="mt-1 max-w-3xl text-xs leading-5 text-slate-500">
            Ledger activity and current loan balances · {currency}
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
          description="Compare income with expenses. The net income line shows the profit or loss for each month."
        >
          {trend.length ? (
            <ResponsiveContainer width="100%" height="100%">
              <ComposedChart
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
                  tickFormatter={formatAxis}
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
                <Bar dataKey="revenue" name="Income" fill="#0B1F3A" isAnimationActive={false} />
                <Bar dataKey="expenses" name="Expenses" fill="#C4851B" isAnimationActive={false} />
                <Line
                  type="linear"
                  dataKey="profit"
                  name="Net income"
                  stroke="#0f766e"
                  strokeWidth={2.5}
                  dot={{ r: 3 }}
                  activeDot={{ r: 5 }}
                  connectNulls={false}
                  isAnimationActive={false}
                />
              </ComposedChart>
            </ResponsiveContainer>
          ) : (
            <EmptyChart message="Monthly accounting results are not available for this period." />
          )}
        </ChartCard>

        <ChartCard
          title="Outstanding loans by credit quality"
          description="Current balances by recorded credit rating. Hover to see the amount and number of loans."
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
                  tickFormatter={formatAxis}
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
                    reportAmount(value, currency),
                    `${item?.payload?.count ?? 0} loans · outstanding`,
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
                  isAnimationActive={false}
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
          description="GL 1000 for the selected dates. Positive bars show cash in. Negative bars show cash out."
        >
          {cashMovements.length ? (
            <ResponsiveContainer width="100%" height="100%">
              <ComposedChart
                data={cashMovements}
                layout="vertical"
                margin={{ top: 8, right: 18, left: 4, bottom: 4 }}
              >
                <CartesianGrid stroke="#e2e8f0" strokeDasharray="3 3" horizontal={false} />
                <XAxis
                  type="number"
                  tickFormatter={formatAxis}
                  tick={{ fontSize: 10, fill: "#64748b" }}
                  tickLine={false}
                  axisLine={false}
                />
                <YAxis
                  type="category"
                  dataKey="source"
                  tick={{ fontSize: 11, fill: "#475569" }}
                  tickLine={false}
                  axisLine={false}
                  width={93}
                />
                <Tooltip
                  formatter={tooltipFormatter}
                  contentStyle={{
                    borderRadius: 8,
                    borderColor: "#cbd5e1",
                    fontSize: 12,
                  }}
                />
                <ReferenceLine x={0} stroke="#64748b" />
                <Bar
                  dataKey="amount"
                  name="Cash movement"
                  isAnimationActive={false}
                  maxBarSize={30}
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
            <h3 className="mt-2 text-lg font-bold">Net change in cash</h3>
            <p className="mt-2 text-xs leading-5 text-slate-300">
              Total movement in GL 1000 during the selected dates.
              See Cash Flow for the starting and ending balances.
            </p>
          </div>
          <div className="mt-8">
            <p
              className={`text-3xl font-bold tracking-tight ${netCashChange == null ? "text-slate-300" : netCashChange < 0 ? "text-rose-300" : "text-emerald-300"}`}
            >
              {reportAmount(netCashChange, currency)}
            </p>
            <p className="mt-2 text-xs text-slate-300">
              {netCashChange != null
                ? netCashChange < 0
                  ? "Cash decreased during the period"
                  : netCashChange > 0
                    ? "Cash increased during the period"
                    : "No net cash movement reported"
                : "No net cash movement figure was returned."}
            </p>
          </div>
          <div className="mt-8 border-t border-slate-700 pt-4 text-[11px] leading-5 text-slate-300">
            Compare the recorded balance with your bank statement to confirm available cash.
          </div>
        </section>
      </div>
    </section>
  );
}
