"use client";

import React, { useCallback, useEffect, useMemo, useState } from "react";

import {
  regulatoryApi,
  type BnrFinancialStatementReport,
  type BnrReportParams,
  type BnrSummary,
  type BreakdownRow,
  type ExportFormat,
  type FinancialStatementRow,
  type RegulatoryPeriod,
} from "@/services/regulatoryService";

/* ============================================================
   CONSTANTS
   ============================================================ */

const PERIODS: RegulatoryPeriod[] = [
  "DAILY",
  "WEEKLY",
  "MONTHLY",
  "QUARTERLY",
  "YEARLY",
  "CUSTOM",
];

const EXPORT_FORMATS: ExportFormat[] = ["pdf", "xlsx", "csv"];

/* ============================================================
   FLEXIBLE BACKEND RESPONSE SUPPORT
   ============================================================ */

/**
 * The frontend regulatoryService currently exposes BnrSummary
 * with one naming convention while the backend BnrSummaryReport
 * contains additional regulatory-specific names such as:
 *
 * totalLoansIssued
 * disbursedLoans
 * loansCurrent
 * loans1To30DaysPastDue
 * femaleLoanCount
 * maleLoanCount
 *
 * Keep the imported BnrSummary type while safely supporting the
 * actual backend response without forcing unsafe property access.
 */
type FlexibleBnrSummary = BnrSummary & Record<string, unknown>;

type FlexibleFinancialStatement = BnrFinancialStatementReport &
  Record<string, unknown>;

type FlexibleBreakdownRow = BreakdownRow & Record<string, unknown>;

/* ============================================================
   SAFE VALUE HELPERS
   ============================================================ */

function safeNumber(value: unknown): number {
  if (typeof value === "number") {
    return Number.isFinite(value) ? value : 0;
  }

  if (typeof value === "bigint") {
    return Number(value);
  }

  if (value === null || value === undefined) {
    return 0;
  }

  const parsed = Number(value);

  return Number.isFinite(parsed) ? parsed : 0;
}

function safeString(value: unknown, fallback = "—"): string {
  if (typeof value === "string" && value.trim().length > 0) {
    return value.trim();
  }

  if (typeof value === "number" && Number.isFinite(value)) {
    return String(value);
  }

  return fallback;
}

function labelize(value?: string | null): string {
  if (!value) {
    return "—";
  }

  return value
    .replace(/_/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function percentageOf(value: unknown, total: number): number {
  const numerator = safeNumber(value);

  if (total <= 0) {
    return 0;
  }

  return (numerator / total) * 100;
}

function moneyValue(value: unknown): number {
  return safeNumber(value);
}

function formatMoney(value: unknown, currency = "RWF"): string {
  const amount = moneyValue(value);

  try {
    return new Intl.NumberFormat("en-RW", {
      style: "currency",
      currency,
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(amount);
  } catch {
    return `${currency} ${amount.toLocaleString("en-US", {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    })}`;
  }
}

function formatNumber(value: unknown): string {
  return new Intl.NumberFormat("en-US").format(safeNumber(value));
}

function formatPercent(value: unknown): string {
  return `${safeNumber(value).toFixed(2)}%`;
}

function formatDateTime(value: unknown): string {
  if (!value) {
    return "—";
  }

  const date = new Date(String(value));

  if (Number.isNaN(date.getTime())) {
    return String(value);
  }

  return new Intl.DateTimeFormat("en-RW", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(date);
}

/* ============================================================
   GENERIC BACKEND FIELD READER
   ============================================================ */

function readField(
  source: Record<string, unknown> | null | undefined,
  ...keys: string[]
): unknown {
  if (!source) {
    return undefined;
  }

  for (const key of keys) {
    const value = source[key];

    if (value !== undefined && value !== null) {
      return value;
    }
  }

  return undefined;
}

function numberField(
  source: Record<string, unknown> | null | undefined,
  ...keys: string[]
): number {
  return safeNumber(readField(source, ...keys));
}

function stringField(
  source: Record<string, unknown> | null | undefined,
  ...keys: string[]
): string {
  return safeString(readField(source, ...keys));
}

/* ============================================================
   BREAKDOWN NORMALIZATION
   ============================================================ */

function unwrapRows(rows: unknown): BreakdownRow[] {
  if (!Array.isArray(rows)) {
    return [];
  }

  return rows
    .filter((row): row is Record<string, unknown> =>
      Boolean(row && typeof row === "object"),
    )
    .map((row) => ({
      label: safeString(
        row.label ??
          row.category ??
          row.name ??
          row.type ??
          row.gender ??
          "Unknown",
        "Unknown",
      ),

      count: safeNumber(
        row.count ?? row.total ?? row.loanCount ?? row.borrowerCount ?? 0,
      ),

      amount: safeNumber(
        row.amount ?? row.totalAmount ?? row.loanAmount ?? row.value ?? 0,
      ),
    }));
}

/* ============================================================
   COMPONENTS
   ============================================================ */

function ExportButton({
  format,
  loading,
  onClick,
}: {
  format: ExportFormat;
  loading: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={loading}
      className="rounded-xl border border-white/15 bg-white/10 px-4 py-2 text-sm font-semibold text-white transition hover:bg-white/20 disabled:cursor-not-allowed disabled:opacity-50"
    >
      {loading ? "Exporting…" : `Export ${format.toUpperCase()}`}
    </button>
  );
}

function Kpi({
  title,
  value,
  secondary,
  danger,
}: {
  title: string;
  value: string;
  secondary?: string;
  danger?: boolean;
}) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <p className="text-[11px] font-semibold uppercase tracking-[0.12em] text-slate-400">
        {title}
      </p>

      <p
        className={`mt-2 text-2xl font-bold ${
          danger ? "text-red-600" : "text-slate-900"
        }`}
      >
        {value}
      </p>

      {secondary ? (
        <p className="mt-1 text-xs text-slate-400">{secondary}</p>
      ) : null}
    </div>
  );
}

function Metric({
  label,
  value,
  secondary,
}: {
  label: string;
  value: string;
  secondary?: string;
}) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-4">
      <p className="text-xs font-medium text-slate-400">{label}</p>

      <p className="mt-2 text-lg font-bold text-slate-900">{value}</p>

      {secondary ? (
        <p className="mt-1 text-xs text-slate-400">{secondary}</p>
      ) : null}
    </div>
  );
}

function Section({
  title,
  children,
}: {
  title: string;
  children: React.ReactNode;
}) {
  return (
    <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm md:p-6">
      <h2 className="mb-5 text-lg font-bold text-slate-900">{title}</h2>

      {children}
    </section>
  );
}

/* ============================================================
   BREAKDOWN TABLE
   ============================================================ */

function Breakdown({
  title,
  rows,
  currency,
  showPercentage,
}: {
  title: string;
  rows: BreakdownRow[];
  currency: string;
  showPercentage: boolean;
}) {
  const totalCount = rows.reduce((sum, row) => sum + safeNumber(row.count), 0);

  const totalAmount = rows.reduce(
    (sum, row) => sum + safeNumber(row.amount),
    0,
  );

  return (
    <section className="overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-sm">
      <div className="border-b border-slate-200 px-5 py-4">
        <h2 className="font-bold text-slate-900">{title}</h2>

        <p className="mt-1 text-xs text-slate-400">
          {formatNumber(totalCount)} total
          {showPercentage ? " • percentage calculated from total count" : ""}
        </p>
      </div>

      {rows.length === 0 ? (
        <div className="p-8 text-center text-sm text-slate-400">
          No data available for this period.
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead className="bg-slate-50">
              <tr>
                <th className="px-5 py-3 text-left text-[10px] font-bold uppercase tracking-wider text-slate-400">
                  Category
                </th>

                <th className="px-5 py-3 text-right text-[10px] font-bold uppercase tracking-wider text-slate-400">
                  Count
                </th>

                {showPercentage ? (
                  <th className="px-5 py-3 text-right text-[10px] font-bold uppercase tracking-wider text-slate-400">
                    %
                  </th>
                ) : null}

                <th className="px-5 py-3 text-right text-[10px] font-bold uppercase tracking-wider text-slate-400">
                  Amount
                </th>
              </tr>
            </thead>

            <tbody className="divide-y divide-slate-100">
              {rows.map((row, index) => {
                const percentage = percentageOf(row.count, totalCount);

                return (
                  <tr
                    key={`${row.label}-${index}`}
                    className="hover:bg-slate-50"
                  >
                    <td className="px-5 py-3 font-semibold text-slate-800">
                      {labelize(row.label)}
                    </td>

                    <td className="px-5 py-3 text-right text-slate-600">
                      {formatNumber(row.count)}
                    </td>

                    {showPercentage ? (
                      <td className="px-5 py-3 text-right font-semibold text-indigo-600">
                        {formatPercent(percentage)}
                      </td>
                    ) : null}

                    <td className="px-5 py-3 text-right font-semibold text-slate-800">
                      {formatMoney(row.amount, currency)}
                    </td>
                  </tr>
                );
              })}

              <tr className="bg-slate-50">
                <td className="px-5 py-3 font-bold text-slate-900">Total</td>

                <td className="px-5 py-3 text-right font-bold text-slate-900">
                  {formatNumber(totalCount)}
                </td>

                {showPercentage ? (
                  <td className="px-5 py-3 text-right font-bold text-indigo-700">
                    100.00%
                  </td>
                ) : null}

                <td className="px-5 py-3 text-right font-bold text-slate-900">
                  {formatMoney(totalAmount, currency)}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

/* ============================================================
   FINANCIAL STATEMENT
   ============================================================ */

function FinancialStatement({
  report,
  currency,
}: {
  report: BnrFinancialStatementReport | null;
  currency: string;
}) {
  if (!report) {
    return null;
  }

  const financial = report as FlexibleFinancialStatement;

  const renderRows = (rows?: FinancialStatementRow[]) => {
    if (!rows?.length) {
      return (
        <div className="p-5 text-sm text-slate-400">
          No accounting data available.
        </div>
      );
    }

    return (
      <div className="overflow-x-auto">
        <table className="min-w-full text-sm">
          <thead className="bg-slate-50">
            <tr>
              <th className="px-5 py-3 text-left text-[10px] font-bold uppercase tracking-wider text-slate-400">
                Account
              </th>

              <th className="px-5 py-3 text-right text-[10px] font-bold uppercase tracking-wider text-slate-400">
                Debit
              </th>

              <th className="px-5 py-3 text-right text-[10px] font-bold uppercase tracking-wider text-slate-400">
                Credit
              </th>

              <th className="px-5 py-3 text-right text-[10px] font-bold uppercase tracking-wider text-slate-400">
                Balance
              </th>
            </tr>
          </thead>

          <tbody className="divide-y divide-slate-100">
            {rows.map((row, index) => (
              <tr key={`${row.code ?? row.name ?? "row"}-${index}`}>
                <td className="px-5 py-3">
                  <div className="font-semibold text-slate-800">
                    {row.name ?? "Unnamed Account"}
                  </div>

                  {row.code ? (
                    <div className="text-xs text-slate-400">{row.code}</div>
                  ) : null}
                </td>

                <td className="px-5 py-3 text-right text-slate-600">
                  {formatMoney(row.debit, currency)}
                </td>

                <td className="px-5 py-3 text-right text-slate-600">
                  {formatMoney(row.credit, currency)}
                </td>

                <td className="px-5 py-3 text-right font-semibold text-slate-900">
                  {formatMoney(row.balance ?? row.amount, currency)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    );
  };

  return (
    <Section title="Financial Statement">
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Metric
          label="Total Assets"
          value={formatMoney(financial.totalAssets, currency)}
        />

        <Metric
          label="Total Liabilities"
          value={formatMoney(financial.totalLiabilities, currency)}
        />

        <Metric
          label="Total Equity"
          value={formatMoney(financial.totalEquity, currency)}
        />

        <Metric
          label="Net Income"
          value={formatMoney(
            financial.netIncome ?? financial.currentPeriodNetIncome,
            currency,
          )}
        />
      </div>

      <div className="mt-6 grid gap-5 xl:grid-cols-3">
        <div className="overflow-hidden rounded-2xl border border-slate-200">
          <div className="border-b border-slate-200 px-5 py-3">
            <h3 className="font-semibold text-slate-900">Assets</h3>
          </div>

          {renderRows(financial.assets)}
        </div>

        <div className="overflow-hidden rounded-2xl border border-slate-200">
          <div className="border-b border-slate-200 px-5 py-3">
            <h3 className="font-semibold text-slate-900">Liabilities</h3>
          </div>

          {renderRows(financial.liabilities)}
        </div>

        <div className="overflow-hidden rounded-2xl border border-slate-200">
          <div className="border-b border-slate-200 px-5 py-3">
            <h3 className="font-semibold text-slate-900">Equity</h3>
          </div>

          {renderRows(financial.equity)}
        </div>
      </div>

      <div className="mt-5 grid gap-4 sm:grid-cols-2">
        <Metric
          label="Cash Used for Lending"
          value={formatMoney(financial.cashUsedForLending, currency)}
        />

        <Metric
          label="Cash From Collections"
          value={formatMoney(financial.cashFromCollections, currency)}
        />

        <Metric
          label="Cash From Fees"
          value={formatMoney(financial.cashFromFees, currency)}
        />

        <Metric
          label="Net Change in Cash"
          value={formatMoney(financial.netChangeInCash, currency)}
        />
      </div>
    </Section>
  );
}

/* ============================================================
   CREDIT QUALITY
   ============================================================ */

type CreditQualityRow = {
  label: string;
  count: number;
  percentage: number;
  amount: number;
  tone: string;
};

function CreditQualitySection({
  summary,
  currency,
}: {
  summary: FlexibleBnrSummary;
  currency: string;
}) {
  const totalLoans = numberField(summary, "totalLoansIssued", "totalLoans");

  const current = numberField(summary, "loansCurrent");

  const watch = numberField(summary, "loans1To30DaysPastDue");

  const substandard = numberField(summary, "loans31To60DaysPastDue");

  const doubtful = numberField(summary, "loans61To90DaysPastDue");

  const writtenOff = numberField(
    summary,
    "loansOver90DaysPastDue",
    "writtenOffLoans",
  );

  /**
   * The backend BNR DTO exposes regulatory aging buckets:
   *
   * CURRENT
   * 1–30 DAYS
   * 31–60 DAYS
   * 61–90 DAYS
   * OVER 90 DAYS
   *
   * The Loan model's credit-quality enum is:
   *
   * CURRENT
   * WATCH
   * SUBSTANDARD
   * DOUBTFUL
   * WRITTEN_OFF
   *
   * Therefore the page must not pretend the backend supplied a
   * separate credit-quality endpoint. It displays the available
   * regulatory classification buckets and maps them safely.
   */

  const rows: CreditQualityRow[] = [
    {
      label: "Current",
      count: current,
      percentage: percentageOf(current, totalLoans),
      amount: numberField(summary, "currentAmount", "currentLoanAmount"),
      tone: "border-emerald-200 bg-emerald-50 text-emerald-800",
    },
    {
      label: "Watch",
      count: watch,
      percentage: percentageOf(watch, totalLoans),
      amount: numberField(summary, "par1Amount", "par1To30Amount"),
      tone: "border-amber-200 bg-amber-50 text-amber-800",
    },
    {
      label: "Substandard",
      count: substandard,
      percentage: percentageOf(substandard, totalLoans),
      amount: numberField(
        summary,
        "par30Amount",
        "par31To60Amount",
        "par31To60Amount",
      ),
      tone: "border-orange-200 bg-orange-50 text-orange-800",
    },
    {
      label: "Doubtful",
      count: doubtful,
      percentage: percentageOf(doubtful, totalLoans),
      amount: numberField(summary, "par60Amount", "par61To90Amount"),
      tone: "border-red-200 bg-red-50 text-red-800",
    },
    {
      label: "Written Off / Severe Default",
      count: writtenOff,
      percentage: percentageOf(writtenOff, totalLoans),
      amount: numberField(summary, "writtenOffPrincipal", "writtenOffAmount"),
      tone: "border-rose-200 bg-rose-50 text-rose-900",
    },
  ];

  const displayedTotal = rows.reduce((sum, row) => sum + row.count, 0);

  return (
    <Section title="Credit Quality">
      <div className="mb-5 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <Metric
          label="Total Loans Classified"
          value={formatNumber(totalLoans)}
          secondary={
            totalLoans > 0 ? "100% reporting base" : "No loan population"
          }
        />

        <Metric
          label="Current Loans"
          value={formatNumber(current)}
          secondary={formatPercent(percentageOf(current, totalLoans))}
        />

        <Metric
          label="Loans Past Due"
          value={formatNumber(Math.max(0, totalLoans - current))}
          secondary={formatPercent(
            percentageOf(Math.max(0, totalLoans - current), totalLoans),
          )}
        />
      </div>

      <div className="overflow-x-auto">
        <table className="min-w-full text-sm">
          <thead className="bg-slate-50">
            <tr>
              <th className="px-5 py-3 text-left text-[10px] font-bold uppercase tracking-wider text-slate-400">
                Credit Quality
              </th>

              <th className="px-5 py-3 text-right text-[10px] font-bold uppercase tracking-wider text-slate-400">
                Loans
              </th>

              <th className="px-5 py-3 text-right text-[10px] font-bold uppercase tracking-wider text-slate-400">
                %
              </th>

              <th className="px-5 py-3 text-right text-[10px] font-bold uppercase tracking-wider text-slate-400">
                Amount
              </th>
            </tr>
          </thead>

          <tbody className="divide-y divide-slate-100">
            {rows.map((row) => (
              <tr key={row.label}>
                <td className="px-5 py-4">
                  <span
                    className={`inline-flex rounded-full border px-3 py-1 text-xs font-semibold ${row.tone}`}
                  >
                    {row.label}
                  </span>
                </td>

                <td className="px-5 py-4 text-right font-semibold text-slate-800">
                  {formatNumber(row.count)}
                </td>

                <td className="px-5 py-4 text-right font-bold text-indigo-600">
                  {formatPercent(row.percentage)}
                </td>

                <td className="px-5 py-4 text-right font-semibold text-slate-800">
                  {formatMoney(row.amount, currency)}
                </td>
              </tr>
            ))}

            <tr className="bg-slate-50">
              <td className="px-5 py-4 font-bold text-slate-900">Total</td>

              <td className="px-5 py-4 text-right font-bold text-slate-900">
                {formatNumber(Math.max(totalLoans, displayedTotal))}
              </td>

              <td className="px-5 py-4 text-right font-bold text-indigo-700">
                100.00%
              </td>

              <td className="px-5 py-4 text-right font-bold text-slate-900">
                {formatMoney(
                  rows.reduce((sum, row) => sum + row.amount, 0),
                  currency,
                )}
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <p className="mt-4 text-xs leading-5 text-slate-400">
        Credit-quality presentation uses the regulatory ageing information
        supplied by the BNR reporting backend. It does not invent a separate
        credit-quality API that the backend does not expose.
      </p>
    </Section>
  );
}

/* ============================================================
   GENDER DISTRIBUTION
   ============================================================ */

function GenderDistribution({
  genders,
  summary,
  currency,
}: {
  genders: BreakdownRow[];
  summary: FlexibleBnrSummary;
  currency: string;
}) {
  const backendMale = numberField(summary, "maleLoanCount", "maleBorrowers");

  const backendFemale = numberField(
    summary,
    "femaleLoanCount",
    "femaleBorrowers",
  );

  const backendOther = numberField(
    summary,
    "otherGenderLoanCount",
    "otherGenderBorrowers",
  );

  const backendMaleAmount = numberField(summary, "maleLoanAmount");

  const backendFemaleAmount = numberField(summary, "femaleLoanAmount");

  const backendOtherAmount = numberField(summary, "otherGenderLoanAmount");

  const maleFromBreakdown = genders.find(
    (row) => row.label?.toLowerCase().trim() === "male",
  );

  const femaleFromBreakdown = genders.find(
    (row) => row.label?.toLowerCase().trim() === "female",
  );

  const otherFromBreakdown = genders.find((row) => {
    const label = row.label?.toLowerCase().trim();

    return label === "other" || label === "unknown" || label === "other gender";
  });

  const male = maleFromBreakdown
    ? safeNumber(maleFromBreakdown.count)
    : backendMale;

  const female = femaleFromBreakdown
    ? safeNumber(femaleFromBreakdown.count)
    : backendFemale;

  const other = otherFromBreakdown
    ? safeNumber(otherFromBreakdown.count)
    : backendOther;

  const maleAmount = maleFromBreakdown
    ? safeNumber(maleFromBreakdown.amount)
    : backendMaleAmount;

  const femaleAmount = femaleFromBreakdown
    ? safeNumber(femaleFromBreakdown.amount)
    : backendFemaleAmount;

  const otherAmount = otherFromBreakdown
    ? safeNumber(otherFromBreakdown.amount)
    : backendOtherAmount;

  const total = male + female + other;

  return (
    <Section title="Gender Distribution">
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Metric
          label="Total"
          value={formatNumber(total)}
          secondary="100.00% of gender population"
        />

        <Metric
          label="Male"
          value={formatNumber(male)}
          secondary={formatPercent(percentageOf(male, total))}
        />

        <Metric
          label="Female"
          value={formatNumber(female)}
          secondary={formatPercent(percentageOf(female, total))}
        />

        <Metric
          label="Other / Unknown"
          value={formatNumber(other)}
          secondary={formatPercent(percentageOf(other, total))}
        />
      </div>

      <div className="mt-6 overflow-x-auto">
        <table className="min-w-full text-sm">
          <thead className="bg-slate-50">
            <tr>
              <th className="px-5 py-3 text-left text-[10px] font-bold uppercase tracking-wider text-slate-400">
                Gender
              </th>

              <th className="px-5 py-3 text-right text-[10px] font-bold uppercase tracking-wider text-slate-400">
                Total
              </th>

              <th className="px-5 py-3 text-right text-[10px] font-bold uppercase tracking-wider text-slate-400">
                Percentage
              </th>

              <th className="px-5 py-3 text-right text-[10px] font-bold uppercase tracking-wider text-slate-400">
                Loan Amount
              </th>
            </tr>
          </thead>

          <tbody className="divide-y divide-slate-100">
            <tr>
              <td className="px-5 py-4 font-semibold text-slate-800">Male</td>

              <td className="px-5 py-4 text-right">{formatNumber(male)}</td>

              <td className="px-5 py-4 text-right font-bold text-indigo-600">
                {formatPercent(percentageOf(male, total))}
              </td>

              <td className="px-5 py-4 text-right font-semibold">
                {formatMoney(maleAmount, currency)}
              </td>
            </tr>

            <tr>
              <td className="px-5 py-4 font-semibold text-slate-800">Female</td>

              <td className="px-5 py-4 text-right">{formatNumber(female)}</td>

              <td className="px-5 py-4 text-right font-bold text-indigo-600">
                {formatPercent(percentageOf(female, total))}
              </td>

              <td className="px-5 py-4 text-right font-semibold">
                {formatMoney(femaleAmount, currency)}
              </td>
            </tr>

            <tr>
              <td className="px-5 py-4 font-semibold text-slate-800">
                Other / Unknown
              </td>

              <td className="px-5 py-4 text-right">{formatNumber(other)}</td>

              <td className="px-5 py-4 text-right font-bold text-indigo-600">
                {formatPercent(percentageOf(other, total))}
              </td>

              <td className="px-5 py-4 text-right font-semibold">
                {formatMoney(otherAmount, currency)}
              </td>
            </tr>

            <tr className="bg-slate-50">
              <td className="px-5 py-4 font-bold text-slate-900">Total</td>

              <td className="px-5 py-4 text-right font-bold text-slate-900">
                {formatNumber(total)}
              </td>

              <td className="px-5 py-4 text-right font-bold text-indigo-700">
                100.00%
              </td>

              <td className="px-5 py-4 text-right font-bold text-slate-900">
                {formatMoney(maleAmount + femaleAmount + otherAmount, currency)}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </Section>
  );
}

/* ============================================================
   LOAN TYPE DISTRIBUTION
   ============================================================ */

function LoanTypeDistribution({
  rows,
  summary,
  currency,
}: {
  rows: BreakdownRow[];
  summary: FlexibleBnrSummary;
  currency: string;
}) {
  const normalizedRows =
    rows.length > 0
      ? rows
      : unwrapRows(readField(summary, "loanTypeBreakdown"));

  const totalCount = normalizedRows.reduce(
    (sum, row) => sum + safeNumber(row.count),
    0,
  );

  const totalAmount = normalizedRows.reduce(
    (sum, row) => sum + safeNumber(row.amount),
    0,
  );

  return (
    <Section title="Loan Type Distribution">
      {normalizedRows.length === 0 ? (
        <div className="rounded-2xl border border-slate-200 bg-slate-50 p-6 text-center text-sm text-slate-400">
          No loan-type data available for this reporting period.
        </div>
      ) : (
        <>
          <div className="mb-5 grid gap-4 sm:grid-cols-2">
            <Metric
              label="Total Loans by Type"
              value={formatNumber(totalCount)}
              secondary="100.00% of loan-type population"
            />

            <Metric
              label="Total Loan Amount"
              value={formatMoney(totalAmount, currency)}
              secondary="Combined amount across all loan types"
            />
          </div>

          <div className="overflow-x-auto">
            <table className="min-w-full text-sm">
              <thead className="bg-slate-50">
                <tr>
                  <th className="px-5 py-3 text-left text-[10px] font-bold uppercase tracking-wider text-slate-400">
                    Loan Type
                  </th>

                  <th className="px-5 py-3 text-right text-[10px] font-bold uppercase tracking-wider text-slate-400">
                    Total Loans
                  </th>

                  <th className="px-5 py-3 text-right text-[10px] font-bold uppercase tracking-wider text-slate-400">
                    %
                  </th>

                  <th className="px-5 py-3 text-right text-[10px] font-bold uppercase tracking-wider text-slate-400">
                    Loan Amount
                  </th>

                  <th className="px-5 py-3 text-right text-[10px] font-bold uppercase tracking-wider text-slate-400">
                    Amount %
                  </th>
                </tr>
              </thead>

              <tbody className="divide-y divide-slate-100">
                {normalizedRows.map((row, index) => {
                  const count = safeNumber(row.count);

                  const amount = safeNumber(row.amount);

                  const countPct = percentageOf(count, totalCount);

                  const amountPct = percentageOf(amount, totalAmount);

                  return (
                    <tr
                      key={`${row.label}-${index}`}
                      className="hover:bg-slate-50"
                    >
                      <td className="px-5 py-4 font-semibold text-slate-800">
                        {labelize(row.label)}
                      </td>

                      <td className="px-5 py-4 text-right font-semibold text-slate-700">
                        {formatNumber(count)}
                      </td>

                      <td className="px-5 py-4 text-right font-bold text-indigo-600">
                        {formatPercent(countPct)}
                      </td>

                      <td className="px-5 py-4 text-right font-semibold text-slate-800">
                        {formatMoney(amount, currency)}
                      </td>

                      <td className="px-5 py-4 text-right font-bold text-blue-600">
                        {formatPercent(amountPct)}
                      </td>
                    </tr>
                  );
                })}

                <tr className="bg-slate-50">
                  <td className="px-5 py-4 font-bold text-slate-900">Total</td>

                  <td className="px-5 py-4 text-right font-bold text-slate-900">
                    {formatNumber(totalCount)}
                  </td>

                  <td className="px-5 py-4 text-right font-bold text-indigo-700">
                    100.00%
                  </td>

                  <td className="px-5 py-4 text-right font-bold text-slate-900">
                    {formatMoney(totalAmount, currency)}
                  </td>

                  <td className="px-5 py-4 text-right font-bold text-blue-700">
                    100.00%
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <div className="mt-6 space-y-3">
            {normalizedRows.map((row, index) => {
              const percentage = percentageOf(row.count, totalCount);

              return (
                <div
                  key={`bar-${row.label}-${index}`}
                  className="rounded-2xl border border-slate-100 bg-slate-50 p-4"
                >
                  <div className="flex items-center justify-between gap-4">
                    <span className="font-semibold text-slate-800">
                      {labelize(row.label)}
                    </span>

                    <span className="font-bold text-indigo-600">
                      {formatPercent(percentage)}
                    </span>
                  </div>

                  <div className="mt-2 h-2 overflow-hidden rounded-full bg-slate-200">
                    <div
                      className="h-full rounded-full bg-indigo-500"
                      style={{
                        width: `${Math.min(100, Math.max(0, percentage))}%`,
                      }}
                    />
                  </div>

                  <div className="mt-2 flex justify-between text-xs text-slate-400">
                    <span>{formatNumber(row.count)} loans</span>

                    <span>{formatMoney(row.amount, currency)}</span>
                  </div>
                </div>
              );
            })}
          </div>
        </>
      )}
    </Section>
  );
}

/* ============================================================
   PAGE
   ============================================================ */

export default function BnrReportPage() {
  const [period, setPeriod] = useState<RegulatoryPeriod>("MONTHLY");

  const [from, setFrom] = useState("");

  const [to, setTo] = useState("");

  const [summary, setSummary] = useState<BnrSummary | null>(null);

  const [financialStatement, setFinancialStatement] =
    useState<BnrFinancialStatementReport | null>(null);

  const [loanTypes, setLoanTypes] = useState<BreakdownRow[]>([]);

  const [branches, setBranches] = useState<BreakdownRow[]>([]);

  const [genders, setGenders] = useState<BreakdownRow[]>([]);

  const [loading, setLoading] = useState(true);

  const [error, setError] = useState<string | null>(null);

  const [downloading, setDownloading] = useState<ExportFormat | null>(null);

  /* ==========================================================
     PARAMETERS
     ========================================================== */

  const params = useMemo<BnrReportParams>(() => {
    const result: BnrReportParams = {
      period,
    };

    if (period === "CUSTOM") {
      if (from) {
        result.from = from;
      }

      if (to) {
        result.to = to;
      }
    }

    return result;
  }, [period, from, to]);

  /* ==========================================================
     VALIDATION
     ========================================================== */

  const validate = useCallback((): string | null => {
    if (period !== "CUSTOM") {
      return null;
    }

    if (!from) {
      return "Please select the start date.";
    }

    if (!to) {
      return "Please select the end date.";
    }

    if (from > to) {
      return "Start date cannot be after the end date.";
    }

    return null;
  }, [period, from, to]);

  /* ==========================================================
     LOAD REPORT
     ========================================================== */

  const loadReport = useCallback(async () => {
    const validationError = validate();

    if (validationError) {
      setError(validationError);
      return;
    }

    try {
      setLoading(true);
      setError(null);

      const [
        summaryResponse,
        financialResponse,
        loanTypeResponse,
        branchResponse,
        genderResponse,
      ] = await Promise.all([
        regulatoryApi.bnrSummary(params),

        regulatoryApi.bnrFinancialStatement(params),

        regulatoryApi.bnrByLoanType(params),

        regulatoryApi.bnrByBranch(params),

        regulatoryApi.bnrByGender(params),
      ]);

      setSummary(summaryResponse ?? null);

      setFinancialStatement(financialResponse ?? null);

      setLoanTypes(unwrapRows(loanTypeResponse));

      setBranches(unwrapRows(branchResponse));

      setGenders(unwrapRows(genderResponse));
    } catch (err) {
      console.error("BNR report error:", err);

      setError(
        regulatoryApi.getErrorMessage(err, "Unable to load the BNR report."),
      );
    } finally {
      setLoading(false);
    }
  }, [params, validate]);

  useEffect(() => {
    void loadReport();
  }, [loadReport]);

  /* ==========================================================
     EXPORT
     ========================================================== */

  const exportReport = useCallback(
    async (format: ExportFormat) => {
      const validationError = validate();

      if (validationError) {
        setError(validationError);
        return;
      }

      try {
        setDownloading(format);

        setError(null);

        await regulatoryApi.bnrExport(format, params);
      } catch (err) {
        console.error("BNR export error:", err);

        setError(
          regulatoryApi.getErrorMessage(
            err,
            `Unable to export BNR ${format.toUpperCase()} report.`,
          ),
        );
      } finally {
        setDownloading(null);
      }
    },
    [params, validate],
  );

  /* ==========================================================
     FLEXIBLE SUMMARY
     ========================================================== */

  const flexibleSummary = summary as FlexibleBnrSummary | null;

  const currency =
    stringField(flexibleSummary, "currency") ||
    financialStatement?.currency ||
    "RWF";

  /* ==========================================================
     BACKEND COMPATIBLE SUMMARY VALUES
     ========================================================== */

  const totalLoans = numberField(
    flexibleSummary,
    "totalLoansIssued",
    "totalLoans",
  );

  const disbursedLoans = numberField(
    flexibleSummary,
    "disbursedLoans",
    "loansDisbursedDuringPeriod",
  );

  const activeLoans = numberField(flexibleSummary, "activeLoans");

  const overdueLoans = numberField(flexibleSummary, "overdueLoans");

  const principalDisbursed = numberField(
    flexibleSummary,
    "totalLoanAmount",
    "totalPrincipalDisbursed",
  );

  const outstandingPrincipal = numberField(
    flexibleSummary,
    "outstandingPrincipal",
  );

  const totalCollected = numberField(
    flexibleSummary,
    "principalCollected",
    "totalAmountCollected",
  );

  const interestPaid = numberField(
    flexibleSummary,
    "interestPaid",
    "totalInterestCollected",
  );

  const parRatio = numberField(flexibleSummary, "par1Ratio", "parRatio");

  const par30Ratio = numberField(flexibleSummary, "par30Ratio");

  const par60Ratio = numberField(flexibleSummary, "par60Ratio");

  const par90Ratio = numberField(flexibleSummary, "par90Ratio");

  const nplRatio = numberField(flexibleSummary, "nplRatio");

  const nplAmount = numberField(
    flexibleSummary,
    "nplAmount",
    "defaultedPrincipal",
  );

  const nplCount = numberField(flexibleSummary, "nplLoanCount");

  const totalBorrowers = numberField(
    flexibleSummary,
    "totalBorrowers",
    "individualBorrowers",
  );

  const activeBorrowers = numberField(flexibleSummary, "activeBorrowers");

  const missingBorrower = numberField(flexibleSummary, "loansMissingBorrower");

  const missingNationalId = numberField(
    flexibleSummary,
    "borrowersMissingNationalId",
  );

  const missingBranch = numberField(flexibleSummary, "loansMissingBranch");

  const missingCurrency = numberField(flexibleSummary, "loansMissingCurrency");

  const missingSchedule = numberField(
    flexibleSummary,
    "loansMissingRepaymentSchedule",
  );

  const organizationName = stringField(flexibleSummary, "organizationName");

  const institutionCode = stringField(
    flexibleSummary,
    "bnrInstitutionCode",
    "branchCode",
  );

  const reportReference = stringField(flexibleSummary, "reportReference");

  const periodStart = stringField(flexibleSummary, "periodStart");

  const periodEnd = stringField(flexibleSummary, "periodEnd");

  const dataQualityWarnings = Array.isArray(
    flexibleSummary?.dataQualityWarnings,
  )
    ? flexibleSummary.dataQualityWarnings
    : [];

  /* ==========================================================
     LOADING
     ========================================================== */

  if (loading) {
    return (
      <main className="min-h-screen bg-slate-50 p-6">
        <div className="mx-auto max-w-[1600px] space-y-6">
          <div className="h-32 animate-pulse rounded-3xl bg-white shadow-sm" />

          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            {Array.from({
              length: 8,
            }).map((_, index) => (
              <div
                key={index}
                className="h-28 animate-pulse rounded-2xl bg-white shadow-sm"
              />
            ))}
          </div>

          <div className="h-96 animate-pulse rounded-3xl bg-white shadow-sm" />
        </div>
      </main>
    );
  }

  /* ==========================================================
     PAGE
     ========================================================== */

  return (
    <main className="min-h-screen bg-slate-50">
      <div className="mx-auto max-w-[1600px] space-y-6 p-4 md:p-6 lg:p-8">
        {/* ======================================================
            HEADER
        ====================================================== */}

        <section className="overflow-hidden rounded-3xl bg-gradient-to-br from-slate-950 via-slate-900 to-blue-950 p-6 text-white shadow-xl md:p-8">
          <div className="flex flex-col gap-6 lg:flex-row lg:items-center lg:justify-between">
            <div>
              <div className="mb-3 inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/10 px-3 py-1 text-xs font-medium">
                <span className="h-2 w-2 rounded-full bg-emerald-400" />
                Regulatory Reporting
              </div>

              <h1 className="text-3xl font-bold tracking-tight md:text-4xl">
                BNR Reports
              </h1>

              <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-300 md:text-base">
                Regulatory portfolio reporting, financial statements, portfolio
                quality and institutional reporting information.
              </p>
            </div>

            <div className="flex flex-wrap gap-2">
              {EXPORT_FORMATS.map((format) => (
                <ExportButton
                  key={format}
                  format={format}
                  loading={downloading === format}
                  onClick={() => void exportReport(format)}
                />
              ))}
            </div>
          </div>
        </section>

        {/* ======================================================
            ERROR
        ====================================================== */}

        {error ? (
          <div className="flex items-start justify-between gap-4 rounded-2xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
            <div>
              <p className="font-semibold">Report Error</p>

              <p className="mt-1">{error}</p>
            </div>

            <button
              type="button"
              onClick={() => setError(null)}
              className="font-semibold"
            >
              Dismiss
            </button>
          </div>
        ) : null}

        {/* ======================================================
            FILTERS
        ====================================================== */}

        <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm md:p-6">
          <div className="grid gap-4 md:grid-cols-3">
            <label className="block">
              <span className="mb-2 block text-xs font-semibold uppercase tracking-wider text-slate-400">
                Reporting Period
              </span>

              <select
                value={period}
                onChange={(event) =>
                  setPeriod(event.target.value as RegulatoryPeriod)
                }
                className="w-full rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-sm outline-none focus:border-slate-400"
              >
                {PERIODS.map((item) => (
                  <option key={item} value={item}>
                    {labelize(item)}
                  </option>
                ))}
              </select>
            </label>

            <label className="block">
              <span className="mb-2 block text-xs font-semibold uppercase tracking-wider text-slate-400">
                From
              </span>

              <input
                type="date"
                value={from}
                disabled={period !== "CUSTOM"}
                onChange={(event) => setFrom(event.target.value)}
                className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm disabled:bg-slate-100"
              />
            </label>

            <label className="block">
              <span className="mb-2 block text-xs font-semibold uppercase tracking-wider text-slate-400">
                To
              </span>

              <input
                type="date"
                value={to}
                disabled={period !== "CUSTOM"}
                onChange={(event) => setTo(event.target.value)}
                className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm disabled:bg-slate-100"
              />
            </label>
          </div>

          <div className="mt-4 flex justify-end">
            <button
              type="button"
              onClick={() => void loadReport()}
              className="rounded-xl bg-slate-950 px-6 py-2.5 text-sm font-semibold text-white hover:bg-slate-800"
            >
              Refresh Report
            </button>
          </div>
        </section>

        {/* ======================================================
            INSTITUTION
        ====================================================== */}

        {flexibleSummary ? (
          <>
            <section className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
              <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
                <div>
                  <p className="text-xs font-semibold uppercase tracking-wider text-blue-600">
                    Reporting Institution
                  </p>

                  <h2 className="mt-1 text-2xl font-bold text-slate-900">
                    {organizationName || "Organization"}
                  </h2>

                  <p className="mt-1 text-sm text-slate-500">
                    BNR Institution Code:{" "}
                    <span className="font-semibold text-slate-700">
                      {institutionCode || "Not configured"}
                    </span>
                  </p>
                </div>

                <div className="text-sm md:text-right">
                  <p className="text-slate-400">Reporting Period</p>

                  <p className="font-semibold text-slate-800">
                    {periodStart || "—"} → {periodEnd || "—"}
                  </p>

                  <p className="mt-1 text-xs text-slate-400">
                    {reportReference || "No report reference"}
                  </p>
                </div>
              </div>
            </section>

            {/* ==================================================
                MAIN KPI
            ================================================== */}

            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
              <Kpi title="Total Loans" value={formatNumber(totalLoans)} />

              <Kpi
                title="Loans Disbursed"
                value={formatNumber(disbursedLoans)}
              />

              <Kpi title="Active Loans" value={formatNumber(activeLoans)} />

              <Kpi
                title="Principal Disbursed"
                value={formatMoney(principalDisbursed, currency)}
              />

              <Kpi
                title="Outstanding Principal"
                value={formatMoney(outstandingPrincipal, currency)}
              />

              <Kpi
                title="Total Collected"
                value={formatMoney(totalCollected, currency)}
              />

              <Kpi
                title="Interest Collected"
                value={formatMoney(interestPaid, currency)}
              />

              <Kpi
                title="Overdue Loans"
                value={formatNumber(overdueLoans)}
                danger
              />
            </div>

            {/* ==================================================
                CREDIT QUALITY
            ================================================== */}

            <CreditQualitySection
              summary={flexibleSummary}
              currency={currency}
            />

            {/* ==================================================
                PORTFOLIO QUALITY
            ================================================== */}

            <Section title="Portfolio Quality">
              <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
                <Metric
                  label="PAR"
                  value={formatPercent(parRatio)}
                  secondary={formatMoney(
                    numberField(flexibleSummary, "parAmount", "par1Amount"),
                    currency,
                  )}
                />

                <Metric
                  label="PAR > 30 Days"
                  value={formatPercent(par30Ratio)}
                  secondary={formatMoney(
                    numberField(flexibleSummary, "par30Amount"),
                    currency,
                  )}
                />

                <Metric
                  label="PAR > 60 Days"
                  value={formatPercent(par60Ratio)}
                  secondary={formatMoney(
                    numberField(flexibleSummary, "par60Amount"),
                    currency,
                  )}
                />

                <Metric
                  label="PAR > 90 Days"
                  value={formatPercent(par90Ratio)}
                  secondary={formatMoney(
                    numberField(flexibleSummary, "par90Amount"),
                    currency,
                  )}
                />

                <Metric
                  label="NPL Ratio"
                  value={formatPercent(nplRatio)}
                  secondary={formatMoney(nplAmount, currency)}
                />

                <Metric label="NPL Loans" value={formatNumber(nplCount)} />

                <Metric
                  label="Loans > 30 DPD"
                  value={formatNumber(
                    numberField(
                      flexibleSummary,
                      "loansOver30Days",
                      "loans1To30DaysPastDue",
                      "loans31To60DaysPastDue",
                      "loans61To90DaysPastDue",
                      "loansOver90DaysPastDue",
                    ),
                  )}
                />

                <Metric
                  label="Loans > 90 DPD"
                  value={formatNumber(
                    numberField(
                      flexibleSummary,
                      "loansOver90Days",
                      "loansOver90DaysPastDue",
                    ),
                  )}
                />
              </div>
            </Section>

            {/* ==================================================
                BORROWER STATISTICS
            ================================================== */}

            <Section title="Borrower Statistics">
              <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
                <Metric
                  label="Total Borrowers"
                  value={formatNumber(totalBorrowers)}
                />

                <Metric
                  label="Active Borrowers"
                  value={formatNumber(activeBorrowers)}
                />

                <Metric
                  label="Male Borrowers / Loans"
                  value={formatNumber(
                    numberField(
                      flexibleSummary,
                      "maleLoanCount",
                      "maleBorrowers",
                    ),
                  )}
                  secondary={formatPercent(
                    percentageOf(
                      numberField(
                        flexibleSummary,
                        "maleLoanCount",
                        "maleBorrowers",
                      ),
                      totalBorrowers,
                    ),
                  )}
                />

                <Metric
                  label="Female Borrowers / Loans"
                  value={formatNumber(
                    numberField(
                      flexibleSummary,
                      "femaleLoanCount",
                      "femaleBorrowers",
                    ),
                  )}
                  secondary={formatPercent(
                    percentageOf(
                      numberField(
                        flexibleSummary,
                        "femaleLoanCount",
                        "femaleBorrowers",
                      ),
                      totalBorrowers,
                    ),
                  )}
                />

                <Metric
                  label="Other Gender"
                  value={formatNumber(
                    numberField(
                      flexibleSummary,
                      "otherGenderLoanCount",
                      "otherGenderBorrowers",
                    ),
                  )}
                  secondary={formatPercent(
                    percentageOf(
                      numberField(
                        flexibleSummary,
                        "otherGenderLoanCount",
                        "otherGenderBorrowers",
                      ),
                      totalBorrowers,
                    ),
                  )}
                />

                <Metric
                  label="Youth Borrowers"
                  value={formatNumber(
                    numberField(flexibleSummary, "youthBorrowers"),
                  )}
                />

                <Metric
                  label="Adult Borrowers"
                  value={formatNumber(
                    numberField(flexibleSummary, "adultBorrowers"),
                  )}
                />

                <Metric
                  label="Senior Borrowers"
                  value={formatNumber(
                    numberField(flexibleSummary, "seniorBorrowers"),
                  )}
                />
              </div>
            </Section>

            {/* ==================================================
                REPAYMENT PERFORMANCE
            ================================================== */}

            <Section title="Repayment Performance">
              <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
                <Metric
                  label="Principal Collected"
                  value={formatMoney(
                    numberField(
                      flexibleSummary,
                      "principalCollected",
                      "totalPrincipalCollected",
                    ),
                    currency,
                  )}
                />

                <Metric
                  label="Interest Collected"
                  value={formatMoney(
                    numberField(
                      flexibleSummary,
                      "interestPaid",
                      "totalInterestCollected",
                    ),
                    currency,
                  )}
                />

                <Metric
                  label="Fees Collected"
                  value={formatMoney(
                    numberField(
                      flexibleSummary,
                      "totalProcessingFees",
                      "totalFeesCollected",
                    ),
                    currency,
                  )}
                />

                <Metric
                  label="Total Collected"
                  value={formatMoney(
                    numberField(
                      flexibleSummary,
                      "totalAmountCollected",
                      "principalCollected",
                    ),
                    currency,
                  )}
                />

                <Metric
                  label="Unpaid Interest"
                  value={formatMoney(
                    numberField(
                      flexibleSummary,
                      "interestAccruedUnpaid",
                      "overdueInterest",
                    ),
                    currency,
                  )}
                />

                <Metric
                  label="Unpaid Fees"
                  value={formatMoney(
                    numberField(
                      flexibleSummary,
                      "feesAccruedUnpaid",
                      "totalFeesOutstanding",
                    ),
                    currency,
                  )}
                />

                <Metric
                  label="Missed Payments"
                  value={formatNumber(
                    numberField(flexibleSummary, "missedPayments"),
                  )}
                />

                <Metric
                  label="Overdue Payments"
                  value={formatNumber(
                    numberField(flexibleSummary, "overduePayments"),
                  )}
                />
              </div>
            </Section>
          </>
        ) : null}

        {/* ======================================================
            FINANCIAL STATEMENT
        ====================================================== */}

        <FinancialStatement report={financialStatement} currency={currency} />

        {/* ======================================================
            LOAN TYPE
        ====================================================== */}

        <LoanTypeDistribution
          rows={loanTypes}
          summary={flexibleSummary ?? ({} as FlexibleBnrSummary)}
          currency={currency}
        />

        {/* ======================================================
            GENDER
        ====================================================== */}

        <GenderDistribution
          genders={genders}
          summary={flexibleSummary ?? ({} as FlexibleBnrSummary)}
          currency={currency}
        />

        {/* ======================================================
            BRANCH
        ====================================================== */}

        <Breakdown
          title="Loans by Branch"
          rows={branches}
          currency={currency}
          showPercentage={false}
        />

        {/* ======================================================
            PORTFOLIO AGING
        ====================================================== */}

        {flexibleSummary ? (
          <Section title="Portfolio Aging">
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              <Metric
                label="1–30 Days"
                value={formatMoney(
                  numberField(flexibleSummary, "par1To30Amount", "par1Amount"),
                  currency,
                )}
              />

              <Metric
                label="31–60 Days"
                value={formatMoney(
                  numberField(flexibleSummary, "par31To60Amount"),
                  currency,
                )}
              />

              <Metric
                label="61–90 Days"
                value={formatMoney(
                  numberField(flexibleSummary, "par61To90Amount"),
                  currency,
                )}
              />

              <Metric
                label="91–180 Days"
                value={formatMoney(
                  numberField(flexibleSummary, "par91To180Amount"),
                  currency,
                )}
              />

              <Metric
                label="181–365 Days"
                value={formatMoney(
                  numberField(flexibleSummary, "par181To365Amount"),
                  currency,
                )}
              />

              <Metric
                label="Over 365 Days"
                value={formatMoney(
                  numberField(flexibleSummary, "parOver365Amount"),
                  currency,
                )}
              />
            </div>
          </Section>
        ) : null}

        {/* ======================================================
            DATA QUALITY
        ====================================================== */}

        {flexibleSummary ? (
          <Section title="Data Quality">
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
              <Metric
                label="Missing Borrower"
                value={formatNumber(missingBorrower)}
              />

              <Metric
                label="Missing National ID"
                value={formatNumber(missingNationalId)}
              />

              <Metric
                label="Missing Branch"
                value={formatNumber(missingBranch)}
              />

              <Metric
                label="Missing Currency"
                value={formatNumber(missingCurrency)}
              />

              <Metric
                label="Missing Schedule"
                value={formatNumber(missingSchedule)}
              />
            </div>

            {dataQualityWarnings.length > 0 ? (
              <div className="mt-5 rounded-2xl border border-amber-200 bg-amber-50 p-4">
                <p className="font-semibold text-amber-900">
                  Data quality warnings
                </p>

                <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-amber-800">
                  {dataQualityWarnings.map((warning, index) => (
                    <li key={index}>{warning}</li>
                  ))}
                </ul>
              </div>
            ) : null}
          </Section>
        ) : null}

        {/* ======================================================
            REGULATORY STATUS
        ====================================================== */}

        {flexibleSummary ? (
          <Section title="Regulatory Report Status">
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <Metric
                label="Records Included"
                value={formatNumber(
                  numberField(flexibleSummary, "recordsIncluded"),
                )}
              />

              <Metric
                label="Records With Missing Data"
                value={formatNumber(
                  numberField(flexibleSummary, "recordsWithMissingData"),
                )}
              />

              <Metric
                label="Validation"
                value={
                  Boolean(readField(flexibleSummary, "validated"))
                    ? "VALIDATED"
                    : "REVIEW"
                }
              />

              <Metric
                label="Report Status"
                value={stringField(flexibleSummary, "reportStatus")}
              />
            </div>
          </Section>
        ) : null}

        {/* ======================================================
            FOOTER
        ====================================================== */}

        <div className="pb-8 text-center text-xs text-slate-400">
          BNR regulatory report • {labelize(period)}
        </div>
      </div>
    </main>
  );
}
