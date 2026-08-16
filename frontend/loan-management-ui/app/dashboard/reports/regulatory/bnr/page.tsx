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

const PERIODS: RegulatoryPeriod[] = [
  "DAILY",
  "WEEKLY",
  "MONTHLY",
  "QUARTERLY",
  "YEARLY",
  "CUSTOM",
];

const safeNumber = (value: unknown): number => {
  if (typeof value === "number") {
    return Number.isFinite(value) ? value : 0;
  }

  const parsed = Number(value);

  return Number.isFinite(parsed) ? parsed : 0;
};

const labelize = (value?: string | null): string =>
  value ? value.replace(/_/g, " ") : "—";

const percentageOf = (value: unknown, total: number): number => {
  const numerator = safeNumber(value);

  if (total <= 0) {
    return 0;
  }

  return (numerator / total) * 100;
};

const moneyValue = (value: unknown): number => safeNumber(value);

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

function formatPercent(value: number): string {
  return `${value.toFixed(2)}%`;
}

function unwrapRows(rows: unknown): BreakdownRow[] {
  if (!Array.isArray(rows)) {
    return [];
  }

  return rows
    .filter((row): row is BreakdownRow =>
      Boolean(row && typeof row === "object"),
    )
    .map((row: any) => ({
      label:
        typeof row.label === "string"
          ? row.label
          : String(row.category ?? row.name ?? "Unknown"),
      count: safeNumber(row.count ?? row.total ?? 0),
      amount: safeNumber(row.amount ?? 0),
    }));
}

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

      {secondary && <p className="mt-1 text-xs text-slate-400">{secondary}</p>}
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

      {secondary && <p className="mt-1 text-xs text-slate-400">{secondary}</p>}
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

                {showPercentage && (
                  <th className="px-5 py-3 text-right text-[10px] font-bold uppercase tracking-wider text-slate-400">
                    %
                  </th>
                )}

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

                    {showPercentage && (
                      <td className="px-5 py-3 text-right font-semibold text-indigo-600">
                        {formatPercent(percentage)}
                      </td>
                    )}

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

                {showPercentage && (
                  <td className="px-5 py-3 text-right font-bold text-indigo-700">
                    100.00%
                  </td>
                )}

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

                  {row.code && (
                    <div className="text-xs text-slate-400">{row.code}</div>
                  )}
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
          value={formatMoney(report.totalAssets, currency)}
        />

        <Metric
          label="Total Liabilities"
          value={formatMoney(report.totalLiabilities, currency)}
        />

        <Metric
          label="Total Equity"
          value={formatMoney(report.totalEquity, currency)}
        />

        <Metric
          label="Net Income"
          value={formatMoney(
            report.netIncome ?? report.currentPeriodNetIncome,
            currency,
          )}
        />
      </div>

      <div className="mt-6 grid gap-5 xl:grid-cols-3">
        <div className="overflow-hidden rounded-2xl border border-slate-200">
          <div className="border-b border-slate-200 px-5 py-3">
            <h3 className="font-semibold text-slate-900">Assets</h3>
          </div>

          {renderRows(report.assets)}
        </div>

        <div className="overflow-hidden rounded-2xl border border-slate-200">
          <div className="border-b border-slate-200 px-5 py-3">
            <h3 className="font-semibold text-slate-900">Liabilities</h3>
          </div>

          {renderRows(report.liabilities)}
        </div>

        <div className="overflow-hidden rounded-2xl border border-slate-200">
          <div className="border-b border-slate-200 px-5 py-3">
            <h3 className="font-semibold text-slate-900">Equity</h3>
          </div>

          {renderRows(report.equity)}
        </div>
      </div>

      <div className="mt-5 grid gap-4 sm:grid-cols-2">
        <Metric
          label="Cash Used for Lending"
          value={formatMoney(report.cashUsedForLending, currency)}
        />

        <Metric
          label="Cash From Collections"
          value={formatMoney(report.cashFromCollections, currency)}
        />

        <Metric
          label="Cash From Fees"
          value={formatMoney(report.cashFromFees, currency)}
        />

        <Metric
          label="Net Change in Cash"
          value={formatMoney(report.netChangeInCash, currency)}
        />
      </div>
    </Section>
  );
}

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

  const validate = useCallback(() => {
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

  const currency = summary?.currency ?? financialStatement?.currency ?? "RWF";

  // Gender breakdown counts distinct borrowers, so the denominator must
  // be the same borrower population used by the summary KPIs.
  const genderTotal = safeNumber(summary?.totalBorrowers);

  const maleRow = genders.find(
    (row) => row.label?.toLowerCase().trim() === "male",
  );

  const femaleRow = genders.find(
    (row) => row.label?.toLowerCase().trim() === "female",
  );

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

  return (
    <main className="min-h-screen bg-slate-50">
      <div className="mx-auto max-w-[1600px] space-y-6 p-4 md:p-6 lg:p-8">
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
              {(["pdf", "xlsx", "csv"] as ExportFormat[]).map((format) => (
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

        {error && (
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
        )}

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

        {summary && (
          <>
            <section className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
              <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
                <div>
                  <p className="text-xs font-semibold uppercase tracking-wider text-blue-600">
                    Reporting Institution
                  </p>

                  <h2 className="mt-1 text-2xl font-bold text-slate-900">
                    {summary.organizationName ?? "Organization"}
                  </h2>

                  <p className="mt-1 text-sm text-slate-500">
                    BNR Institution Code:{" "}
                    <span className="font-semibold text-slate-700">
                      {summary.bnrInstitutionCode ?? "Not configured"}
                    </span>
                  </p>
                </div>

                <div className="text-sm md:text-right">
                  <p className="text-slate-400">Reporting Period</p>

                  <p className="font-semibold text-slate-800">
                    {summary.periodStart ?? "—"} → {summary.periodEnd ?? "—"}
                  </p>

                  <p className="mt-1 text-xs text-slate-400">
                    {summary.reportReference ?? "No report reference"}
                  </p>
                </div>
              </div>
            </section>

            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
              <Kpi
                title="Total Loans"
                value={formatNumber(summary.totalLoans)}
              />

              <Kpi
                title="Loans Disbursed"
                value={formatNumber(summary.loansDisbursedDuringPeriod)}
              />

              <Kpi
                title="Active Loans"
                value={formatNumber(summary.activeLoans)}
              />

              <Kpi
                title="Principal Disbursed"
                value={formatMoney(summary.totalPrincipalDisbursed, currency)}
              />

              <Kpi
                title="Outstanding Principal"
                value={formatMoney(summary.outstandingPrincipal, currency)}
              />

              <Kpi
                title="Total Collected"
                value={formatMoney(summary.totalAmountCollected, currency)}
              />

              <Kpi
                title="Interest Collected"
                value={formatMoney(summary.totalInterestCollected, currency)}
              />

              <Kpi
                title="Overdue Loans"
                value={formatNumber(summary.overdueLoans)}
                danger
              />
            </div>

            <Section title="Portfolio Quality">
              <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
                <Metric
                  label="PAR"
                  value={formatPercent(safeNumber(summary.parRatio))}
                  secondary={formatMoney(summary.parAmount, currency)}
                />

                <Metric
                  label="PAR > 30 Days"
                  value={formatPercent(safeNumber(summary.par30Ratio))}
                />

                <Metric
                  label="PAR > 60 Days"
                  value={formatPercent(safeNumber(summary.par60Ratio))}
                />

                <Metric
                  label="PAR > 90 Days"
                  value={formatPercent(safeNumber(summary.par90Ratio))}
                />

                <Metric
                  label="NPL Ratio"
                  value={formatPercent(safeNumber(summary.nplRatio))}
                  secondary={formatMoney(summary.nplAmount, currency)}
                />

                <Metric
                  label="NPL Loans"
                  value={formatNumber(summary.nplLoanCount)}
                />

                <Metric
                  label="Loans > 30 DPD"
                  value={formatNumber(summary.loansOver30Days)}
                />

                <Metric
                  label="Loans > 90 DPD"
                  value={formatNumber(summary.loansOver90Days)}
                />
              </div>
            </Section>

            <Section title="Borrower Statistics">
              <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
                <Metric
                  label="Total Borrowers"
                  value={formatNumber(summary.totalBorrowers)}
                />

                <Metric
                  label="Active Borrowers"
                  value={formatNumber(summary.activeBorrowers)}
                />

                <Metric
                  label="Male Borrowers"
                  value={formatNumber(summary.maleBorrowers)}
                  secondary={`${formatPercent(
                    percentageOf(
                      summary.maleBorrowers,
                      safeNumber(summary.totalBorrowers),
                    ),
                  )} of borrowers`}
                />

                <Metric
                  label="Female Borrowers"
                  value={formatNumber(summary.femaleBorrowers)}
                  secondary={`${formatPercent(
                    percentageOf(
                      summary.femaleBorrowers,
                      safeNumber(summary.totalBorrowers),
                    ),
                  )} of borrowers`}
                />

                <Metric
                  label="Other Gender"
                  value={formatNumber(summary.otherGenderBorrowers)}
                  secondary={`${formatPercent(
                    percentageOf(
                      summary.otherGenderBorrowers,
                      safeNumber(summary.totalBorrowers),
                    ),
                  )} of borrowers`}
                />

                <Metric
                  label="Youth Borrowers"
                  value={formatNumber(summary.youthBorrowers)}
                />

                <Metric
                  label="Adult Borrowers"
                  value={formatNumber(summary.adultBorrowers)}
                />

                <Metric
                  label="Senior Borrowers"
                  value={formatNumber(summary.seniorBorrowers)}
                />
              </div>
            </Section>

            <Section title="Repayment Performance">
              <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
                <Metric
                  label="Principal Collected"
                  value={formatMoney(summary.totalPrincipalCollected, currency)}
                />

                <Metric
                  label="Interest Collected"
                  value={formatMoney(summary.totalInterestCollected, currency)}
                />

                <Metric
                  label="Fees Collected"
                  value={formatMoney(summary.totalFeesCollected, currency)}
                />

                <Metric
                  label="Total Collected"
                  value={formatMoney(summary.totalAmountCollected, currency)}
                />

                <Metric
                  label="Unpaid Interest"
                  value={formatMoney(summary.interestAccruedUnpaid, currency)}
                />

                <Metric
                  label="Unpaid Fees"
                  value={formatMoney(summary.feesAccruedUnpaid, currency)}
                />

                <Metric
                  label="Missed Payments"
                  value={formatNumber(summary.missedPayments)}
                />

                <Metric
                  label="Overdue Payments"
                  value={formatNumber(summary.overduePayments)}
                />
              </div>
            </Section>
          </>
        )}

        <FinancialStatement report={financialStatement} currency={currency} />

        <div className="grid gap-6 xl:grid-cols-2">
          <Breakdown
            title="Loans by Loan Type"
            rows={loanTypes}
            currency={currency}
            showPercentage
          />

          <Breakdown
            title="Borrowers by Gender"
            rows={genders}
            currency={currency}
            showPercentage
          />
        </div>

        <Breakdown
          title="Loans by Branch"
          rows={branches}
          currency={currency}
          showPercentage={false}
        />

        {summary && (
          <Section title="Portfolio Aging">
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              <Metric
                label="1–30 Days"
                value={formatMoney(summary.par1To30Amount, currency)}
              />

              <Metric
                label="31–60 Days"
                value={formatMoney(summary.par31To60Amount, currency)}
              />

              <Metric
                label="61–90 Days"
                value={formatMoney(summary.par61To90Amount, currency)}
              />

              <Metric
                label="91–180 Days"
                value={formatMoney(summary.par91To180Amount, currency)}
              />

              <Metric
                label="181–365 Days"
                value={formatMoney(summary.par181To365Amount, currency)}
              />

              <Metric
                label="Over 365 Days"
                value={formatMoney(summary.parOver365Amount, currency)}
              />
            </div>
          </Section>
        )}

        {summary && (
          <Section title="Data Quality">
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
              <Metric
                label="Missing Borrower"
                value={formatNumber(summary.loansMissingBorrower)}
              />

              <Metric
                label="Missing National ID"
                value={formatNumber(summary.borrowersMissingNationalId)}
              />

              <Metric
                label="Missing Branch"
                value={formatNumber(summary.loansMissingBranch)}
              />

              <Metric
                label="Missing Currency"
                value={formatNumber(summary.loansMissingCurrency)}
              />

              <Metric
                label="Missing Schedule"
                value={formatNumber(summary.loansMissingRepaymentSchedule)}
              />
            </div>

            {summary.dataQualityWarnings?.length ? (
              <div className="mt-5 rounded-2xl border border-amber-200 bg-amber-50 p-4">
                <p className="font-semibold text-amber-900">
                  Data quality warnings
                </p>

                <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-amber-800">
                  {summary.dataQualityWarnings.map((warning, index) => (
                    <li key={index}>{warning}</li>
                  ))}
                </ul>
              </div>
            ) : null}
          </Section>
        )}

        <div className="grid gap-6 xl:grid-cols-2">
          <section className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
            <h2 className="text-lg font-bold text-slate-900">
              Gender Distribution
            </h2>

            <div className="mt-5 grid gap-4 sm:grid-cols-2">
              <Metric
                label="Male"
                value={formatNumber(maleRow?.count ?? summary?.maleBorrowers)}
                secondary={`${formatPercent(
                  percentageOf(
                    maleRow?.count ?? summary?.maleBorrowers,
                    genderTotal || safeNumber(summary?.totalBorrowers),
                  ),
                )}`}
              />

              <Metric
                label="Female"
                value={formatNumber(
                  femaleRow?.count ?? summary?.femaleBorrowers,
                )}
                secondary={`${formatPercent(
                  percentageOf(
                    femaleRow?.count ?? summary?.femaleBorrowers,
                    genderTotal || safeNumber(summary?.totalBorrowers),
                  ),
                )}`}
              />
            </div>
          </section>

          <section className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
            <h2 className="text-lg font-bold text-slate-900">
              Loan Type Distribution
            </h2>

            <div className="mt-5 space-y-3">
              {loanTypes.length === 0 ? (
                <p className="text-sm text-slate-400">
                  No loan-type data available.
                </p>
              ) : (
                (() => {
                  const total = loanTypes.reduce(
                    (sum, row) => sum + safeNumber(row.count),
                    0,
                  );

                  return loanTypes.map((row, index) => {
                    const percentage = percentageOf(row.count, total);

                    return (
                      <div
                        key={`${row.label}-${index}`}
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
                              width: `${Math.min(100, percentage)}%`,
                            }}
                          />
                        </div>

                        <div className="mt-2 flex justify-between text-xs text-slate-400">
                          <span>{formatNumber(row.count)} loans</span>

                          <span>{formatMoney(row.amount, currency)}</span>
                        </div>
                      </div>
                    );
                  });
                })()
              )}
            </div>
          </section>
        </div>

        <div className="pb-8 text-center text-xs text-slate-400">
          BNR regulatory report • {labelize(period)}
        </div>
      </div>
    </main>
  );
}
