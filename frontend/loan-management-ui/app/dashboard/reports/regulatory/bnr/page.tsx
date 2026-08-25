"use client";

import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";

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

const EXPORT_FORMATS: ExportFormat[] = ["pdf", "xlsx", "csv"];

const safeNumber = (value: unknown): number => {
  if (typeof value === "number") {
    return Number.isFinite(value) ? value : 0;
  }

  if (typeof value === "string" && value.trim() === "") {
    return 0;
  }

  const parsed = Number(value);

  return Number.isFinite(parsed) ? parsed : 0;
};

const labelize = (value?: string | null): string => {
  if (!value) return "—";

  return value
    .replace(/_/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .replace(/\b\w/g, (character) => character.toUpperCase());
};

const percentageOf = (value: unknown, total: number): number => {
  const numerator = safeNumber(value);

  if (total <= 0) {
    return 0;
  }

  return (numerator / total) * 100;
};

const formatMoney = (value: unknown, currency = "RWF"): string => {
  const amount = safeNumber(value);

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
};

const formatNumber = (value: unknown): string => {
  return new Intl.NumberFormat("en-US", {
    maximumFractionDigits: 0,
  }).format(safeNumber(value));
};

const formatPercent = (value: number): string => {
  if (!Number.isFinite(value)) {
    return "0.00%";
  }

  return `${value.toFixed(2)}%`;
};

/**
 * Backend ratio fields are mathematical ratios.
 *
 * Example:
 *   0.011 = 1.10%
 *
 * This is intentionally separate from percentageOf(), which already
 * returns a human-readable percentage between 0 and 100.
 */
const formatRatioPercent = (value: unknown): string => {
  const ratio = safeNumber(value);

  return `${(ratio * 100).toFixed(2)}%`;
};

const normalizeGender = (value?: string | null): string => {
  return String(value ?? "")
    .trim()
    .toLowerCase()
    .replace(/[_-]/g, " ");
};

const unwrapRows = (rows: unknown): BreakdownRow[] => {
  if (!Array.isArray(rows)) {
    return [];
  }

  return rows
    .filter((row): row is Record<string, unknown> =>
      Boolean(row && typeof row === "object"),
    )
    .map((row) => ({
      label:
        typeof row.label === "string"
          ? row.label
          : String(row.category ?? row.name ?? "Unknown"),
      count: safeNumber(row.count ?? row.total ?? 0),
      amount: safeNumber(row.amount ?? 0),
    }));
};

const getReconciliationDifference = (
  reportedTotal: unknown,
  breakdownTotal: number,
): number => {
  return safeNumber(reportedTotal) - breakdownTotal;
};

const isMaterialDifference = (difference: number): boolean => {
  return Math.abs(difference) > 0;
};

function LoadingBlock({ className = "" }: { className?: string }) {
  return (
    <div
      aria-hidden="true"
      className={`animate-pulse rounded-2xl bg-slate-200 ${className}`}
    />
  );
}

function PageSkeleton() {
  return (
    <main className="min-h-screen bg-slate-50">
      <div className="mx-auto max-w-[1600px] space-y-6 p-4 md:p-6 lg:p-8">
        <LoadingBlock className="h-48 rounded-3xl" />

        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          {Array.from({ length: 8 }).map((_, index) => (
            <LoadingBlock key={index} className="h-32" />
          ))}
        </div>

        <LoadingBlock className="h-64 rounded-3xl" />

        <div className="grid gap-6 xl:grid-cols-2">
          <LoadingBlock className="h-80 rounded-3xl" />
          <LoadingBlock className="h-80 rounded-3xl" />
        </div>
      </div>
    </main>
  );
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
      aria-busy={loading}
      className="inline-flex min-h-10 items-center justify-center rounded-xl border border-white/15 bg-white/10 px-4 py-2 text-sm font-semibold text-white transition hover:bg-white/20 focus:outline-none focus:ring-2 focus:ring-white/60 disabled:cursor-not-allowed disabled:opacity-50"
    >
      {loading ? (
        <>
          <span
            className="mr-2 h-4 w-4 animate-spin rounded-full border-2 border-white/30 border-t-white"
            aria-hidden="true"
          />
          Exporting…
        </>
      ) : (
        `Export ${format.toUpperCase()}`
      )}
    </button>
  );
}

function Kpi({
  title,
  value,
  secondary,
  danger = false,
  warning = false,
}: {
  title: string;
  value: string;
  secondary?: string;
  danger?: boolean;
  warning?: boolean;
}) {
  return (
    <div
      className={`rounded-2xl border bg-white p-5 shadow-sm ${
        danger
          ? "border-red-200"
          : warning
            ? "border-amber-200"
            : "border-slate-200"
      }`}
    >
      <p className="text-[11px] font-bold uppercase tracking-[0.12em] text-slate-400">
        {title}
      </p>

      <p
        className={`mt-2 text-2xl font-bold tracking-tight ${
          danger
            ? "text-red-600"
            : warning
              ? "text-amber-600"
              : "text-slate-900"
        }`}
      >
        {value}
      </p>

      {secondary ? (
        <p className="mt-1 text-xs leading-5 text-slate-400">{secondary}</p>
      ) : null}
    </div>
  );
}

function Metric({
  label,
  value,
  secondary,
  danger = false,
}: {
  label: string;
  value: string;
  secondary?: string;
  danger?: boolean;
}) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-4">
      <p className="text-xs font-medium text-slate-400">{label}</p>

      <p
        className={`mt-2 text-lg font-bold ${
          danger ? "text-red-600" : "text-slate-900"
        }`}
      >
        {value}
      </p>

      {secondary ? (
        <p className="mt-1 text-xs leading-5 text-slate-400">{secondary}</p>
      ) : null}
    </div>
  );
}

function Section({
  title,
  description,
  children,
}: {
  title: string;
  description?: string;
  children: React.ReactNode;
}) {
  return (
    <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm md:p-6">
      <div className="mb-5">
        <h2 className="text-lg font-bold tracking-tight text-slate-900">
          {title}
        </h2>

        {description ? (
          <p className="mt-1 text-sm text-slate-500">{description}</p>
        ) : null}
      </div>

      {children}
    </section>
  );
}

function Alert({
  type,
  title,
  children,
  onDismiss,
}: {
  type: "error" | "warning" | "info" | "success";
  title: string;
  children: React.ReactNode;
  onDismiss?: () => void;
}) {
  const styles = {
    error: {
      container: "border-red-200 bg-red-50 text-red-900",
      text: "text-red-700",
    },
    warning: {
      container: "border-amber-200 bg-amber-50 text-amber-900",
      text: "text-amber-800",
    },
    info: {
      container: "border-blue-200 bg-blue-50 text-blue-900",
      text: "text-blue-800",
    },
    success: {
      container: "border-emerald-200 bg-emerald-50 text-emerald-900",
      text: "text-emerald-800",
    },
  };

  const style = styles[type];

  return (
    <div
      role={type === "error" ? "alert" : "status"}
      className={`rounded-2xl border p-4 ${style.container}`}
    >
      <div className="flex items-start justify-between gap-4">
        <div>
          <p className="font-semibold">{title}</p>

          <div className={`mt-1 text-sm leading-6 ${style.text}`}>
            {children}
          </div>
        </div>

        {onDismiss ? (
          <button
            type="button"
            onClick={onDismiss}
            aria-label="Dismiss notification"
            className="rounded-lg px-2 py-1 text-sm font-semibold opacity-70 hover:bg-black/5 hover:opacity-100 focus:outline-none focus:ring-2 focus:ring-current"
          >
            Dismiss
          </button>
        ) : null}
      </div>
    </div>
  );
}

function Breakdown({
  title,
  description,
  rows,
  currency,
  showPercentage,
  totalCountOverride,
}: {
  title: string;
  description?: string;
  rows: BreakdownRow[];
  currency: string;
  showPercentage: boolean;
  totalCountOverride?: number;
}) {
  const breakdownCount = rows.reduce(
    (sum, row) => sum + safeNumber(row.count),
    0,
  );

  const totalCount =
    totalCountOverride !== undefined
      ? safeNumber(totalCountOverride)
      : breakdownCount;

  const totalAmount = rows.reduce(
    (sum, row) => sum + safeNumber(row.amount),
    0,
  );

  const hasReconciliationDifference =
    totalCountOverride !== undefined &&
    isMaterialDifference(totalCount - breakdownCount);

  return (
    <section className="overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-sm">
      <div className="border-b border-slate-200 px-5 py-4">
        <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <h2 className="font-bold text-slate-900">{title}</h2>

            {description ? (
              <p className="mt-1 text-xs leading-5 text-slate-400">
                {description}
              </p>
            ) : null}
          </div>

          <div className="text-left sm:text-right">
            <p className="text-xs text-slate-400">Reported total</p>

            <p className="font-bold text-slate-800">
              {formatNumber(totalCount)}
            </p>
          </div>
        </div>
      </div>

      {hasReconciliationDifference ? (
        <div className="border-b border-amber-200 bg-amber-50 px-5 py-3 text-xs leading-5 text-amber-800">
          <strong>Reconciliation warning:</strong> the category breakdown totals{" "}
          {formatNumber(breakdownCount)}, while the reported total is{" "}
          {formatNumber(totalCount)}. The system has not silently adjusted the
          source data.
        </div>
      ) : null}

      {rows.length === 0 ? (
        <div className="p-8 text-center">
          <p className="font-medium text-slate-700">
            No data available for this period.
          </p>

          <p className="mt-1 text-sm text-slate-400">
            Try another reporting period or verify the underlying data.
          </p>
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead className="bg-slate-50">
              <tr>
                <th
                  scope="col"
                  className="px-5 py-3 text-left text-[10px] font-bold uppercase tracking-wider text-slate-400"
                >
                  Category
                </th>

                <th
                  scope="col"
                  className="px-5 py-3 text-right text-[10px] font-bold uppercase tracking-wider text-slate-400"
                >
                  Count
                </th>

                {showPercentage ? (
                  <th
                    scope="col"
                    className="px-5 py-3 text-right text-[10px] font-bold uppercase tracking-wider text-slate-400"
                  >
                    %
                  </th>
                ) : null}

                <th
                  scope="col"
                  className="px-5 py-3 text-right text-[10px] font-bold uppercase tracking-wider text-slate-400"
                >
                  Amount
                </th>
              </tr>
            </thead>

            <tbody className="divide-y divide-slate-100">
              {rows.map((row, index) => {
                const rowCount = safeNumber(row.count);

                const percentage =
                  showPercentage && totalCount > 0
                    ? percentageOf(rowCount, totalCount)
                    : 0;

                return (
                  <tr
                    key={`${row.label}-${index}`}
                    className="transition hover:bg-slate-50"
                  >
                    <td className="px-5 py-3 font-semibold text-slate-800">
                      {labelize(row.label)}
                    </td>

                    <td className="px-5 py-3 text-right text-slate-600">
                      {formatNumber(rowCount)}
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
                <td className="px-5 py-3 font-bold text-slate-900">
                  Breakdown Total
                </td>

                <td className="px-5 py-3 text-right font-bold text-slate-900">
                  {formatNumber(breakdownCount)}
                </td>

                {showPercentage ? (
                  <td className="px-5 py-3 text-right font-bold text-indigo-700">
                    {totalCount > 0
                      ? formatPercent(percentageOf(breakdownCount, totalCount))
                      : "0.00%"}
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
              <th
                scope="col"
                className="px-5 py-3 text-left text-[10px] font-bold uppercase tracking-wider text-slate-400"
              >
                Account
              </th>

              <th
                scope="col"
                className="px-5 py-3 text-right text-[10px] font-bold uppercase tracking-wider text-slate-400"
              >
                Debit
              </th>

              <th
                scope="col"
                className="px-5 py-3 text-right text-[10px] font-bold uppercase tracking-wider text-slate-400"
              >
                Credit
              </th>

              <th
                scope="col"
                className="px-5 py-3 text-right text-[10px] font-bold uppercase tracking-wider text-slate-400"
              >
                Balance
              </th>
            </tr>
          </thead>

          <tbody className="divide-y divide-slate-100">
            {rows.map((row, index) => (
              <tr
                key={`${row.code ?? row.name ?? "row"}-${index}`}
                className="transition hover:bg-slate-50"
              >
                <td className="px-5 py-3">
                  <div className="font-semibold text-slate-800">
                    {row.name ?? "Unnamed Account"}
                  </div>

                  {row.code ? (
                    <div className="mt-0.5 text-xs text-slate-400">
                      {row.code}
                    </div>
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
    <Section
      title="Financial Statement"
      description="Accounting position generated for the selected regulatory reporting period."
    >
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

      <div className="mt-5 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
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
  const [refreshing, setRefreshing] = useState(false);

  const [error, setError] = useState<string | null>(null);

  const [downloading, setDownloading] = useState<ExportFormat | null>(null);

  const hasLoadedInitially = useRef(false);

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

  const loadReport = useCallback(
    async (options?: { initial?: boolean }) => {
      const validationError = validate();

      if (validationError) {
        setError(validationError);
        return;
      }

      try {
        if (options?.initial) {
          setLoading(true);
        } else {
          setRefreshing(true);
        }

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
          regulatoryApi.getErrorMessage(
            err,
            "Unable to load the BNR report. Please try again.",
          ),
        );
      } finally {
        setLoading(false);
        setRefreshing(false);
      }
    },
    [params, validate],
  );

  /**
   * Initial load only.
   *
   * IMPORTANT:
   * We intentionally do NOT include the changing custom dates in an
   * automatic effect. This prevents API requests on every date edit.
   */
  useEffect(() => {
    if (hasLoadedInitially.current) {
      return;
    }

    hasLoadedInitially.current = true;

    void loadReport({ initial: true });
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

  const genderBreakdownTotal = genders.reduce(
    (sum, row) => sum + safeNumber(row.count),
    0,
  );

  const maleRow = genders.find((row) => normalizeGender(row.label) === "male");

  const femaleRow = genders.find(
    (row) => normalizeGender(row.label) === "female",
  );

  const otherGenderRows = genders.filter((row) => {
    const normalized = normalizeGender(row.label);

    return normalized !== "male" && normalized !== "female";
  });

  const otherGenderCount = otherGenderRows.reduce(
    (sum, row) => sum + safeNumber(row.count),
    0,
  );

  const reportedMaleCount = safeNumber(summary?.maleBorrowers);
  const reportedFemaleCount = safeNumber(summary?.femaleBorrowers);
  const reportedOtherGenderCount = safeNumber(summary?.otherGenderBorrowers);

  const totalBorrowers = safeNumber(summary?.totalBorrowers);

  const genderReportedTotal =
    reportedMaleCount + reportedFemaleCount + reportedOtherGenderCount;

  const genderSummaryReconciles = totalBorrowers === genderReportedTotal;

  const genderBreakdownReconciles = totalBorrowers === genderBreakdownTotal;

  const displayedMaleCount = maleRow
    ? safeNumber(maleRow.count)
    : reportedMaleCount;

  const displayedFemaleCount = femaleRow
    ? safeNumber(femaleRow.count)
    : reportedFemaleCount;

  const displayedOtherGenderCount =
    maleRow || femaleRow ? otherGenderCount : reportedOtherGenderCount;

  if (loading) {
    return <PageSkeleton />;
  }

  return (
    <main className="min-h-screen bg-slate-50">
      <div className="mx-auto max-w-[1600px] space-y-6 p-4 md:p-6 lg:p-8">
        {/* Header */}
        <section className="overflow-hidden rounded-3xl bg-gradient-to-br from-slate-950 via-slate-900 to-blue-950 p-6 text-white shadow-xl md:p-8">
          <div className="flex flex-col gap-6 lg:flex-row lg:items-center lg:justify-between">
            <div>
              <div className="mb-3 inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/10 px-3 py-1 text-xs font-medium">
                <span
                  className="h-2 w-2 rounded-full bg-emerald-400"
                  aria-hidden="true"
                />
                Regulatory Reporting
              </div>

              <h1 className="text-3xl font-bold tracking-tight md:text-4xl">
                BNR Reports
              </h1>

              <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-300 md:text-base">
                Regulatory portfolio reporting, financial position, repayment
                performance, portfolio quality and institutional reporting
                information.
              </p>
            </div>

            <div
              className="flex flex-wrap gap-2"
              aria-label="Report export options"
            >
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

        {/* Error */}
        {error ? (
          <Alert
            type="error"
            title="Report Error"
            onDismiss={() => setError(null)}
          >
            {error}
          </Alert>
        ) : null}

        {/* Filters */}
        <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm md:p-6">
          <div className="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
            <div className="grid flex-1 gap-4 md:grid-cols-3">
              <label className="block">
                <span className="mb-2 block text-xs font-bold uppercase tracking-wider text-slate-400">
                  Reporting Period
                </span>

                <select
                  value={period}
                  onChange={(event) =>
                    setPeriod(event.target.value as RegulatoryPeriod)
                  }
                  className="min-h-11 w-full rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-sm font-medium text-slate-800 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                >
                  {PERIODS.map((item) => (
                    <option key={item} value={item}>
                      {labelize(item)}
                    </option>
                  ))}
                </select>
              </label>

              <label className="block">
                <span className="mb-2 block text-xs font-bold uppercase tracking-wider text-slate-400">
                  From
                </span>

                <input
                  type="date"
                  value={from}
                  disabled={period !== "CUSTOM"}
                  onChange={(event) => setFrom(event.target.value)}
                  className="min-h-11 w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-100 disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-400"
                />
              </label>

              <label className="block">
                <span className="mb-2 block text-xs font-bold uppercase tracking-wider text-slate-400">
                  To
                </span>

                <input
                  type="date"
                  value={to}
                  disabled={period !== "CUSTOM"}
                  onChange={(event) => setTo(event.target.value)}
                  className="min-h-11 w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-100 disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-400"
                />
              </label>
            </div>

            <button
              type="button"
              onClick={() => void loadReport()}
              disabled={refreshing}
              aria-busy={refreshing}
              className="inline-flex min-h-11 items-center justify-center rounded-xl bg-slate-950 px-6 py-2.5 text-sm font-semibold text-white transition hover:bg-slate-800 focus:outline-none focus:ring-2 focus:ring-slate-400 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {refreshing ? (
                <>
                  <span
                    className="mr-2 h-4 w-4 animate-spin rounded-full border-2 border-white/30 border-t-white"
                    aria-hidden="true"
                  />
                  Loading…
                </>
              ) : (
                "Refresh Report"
              )}
            </button>
          </div>

          <div className="mt-4 flex flex-wrap items-center gap-2 text-xs text-slate-400">
            <span className="rounded-full bg-slate-100 px-3 py-1 font-medium">
              {labelize(period)}
            </span>

            {period === "CUSTOM" && from && to ? (
              <span>
                {from} → {to}
              </span>
            ) : null}

            {refreshing ? (
              <span className="font-medium text-blue-600">
                Updating regulatory data…
              </span>
            ) : null}
          </div>
        </section>

        {/* Institution */}
        {summary ? (
          <section className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
            <div className="flex flex-col gap-5 md:flex-row md:items-center md:justify-between">
              <div>
                <p className="text-xs font-bold uppercase tracking-wider text-blue-600">
                  Reporting Institution
                </p>

                <h2 className="mt-1 text-2xl font-bold tracking-tight text-slate-900">
                  {summary.organizationName ?? "Organization"}
                </h2>

                <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-slate-500">
                  <span>
                    BNR Institution Code:{" "}
                    <strong className="text-slate-700">
                      {summary.bnrInstitutionCode ?? "Not configured"}
                    </strong>
                  </span>

                  <span className="hidden text-slate-300 sm:inline">•</span>

                  <span>Currency: {currency}</span>
                </div>
              </div>

              <div className="rounded-2xl bg-slate-50 p-4 text-sm md:min-w-[300px] md:text-right">
                <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                  Reporting Period
                </p>

                <p className="mt-1 font-semibold text-slate-800">
                  {summary.periodStart ?? "—"} → {summary.periodEnd ?? "—"}
                </p>

                <p className="mt-2 text-xs text-slate-400">
                  Reference:{" "}
                  <span className="font-medium text-slate-600">
                    {summary.reportReference ?? "No report reference"}
                  </span>
                </p>
              </div>
            </div>
          </section>
        ) : null}

        {/* Main KPIs */}
        {summary ? (
          <>
            <section>
              <div className="mb-4 flex items-center justify-between">
                <div>
                  <h2 className="text-lg font-bold text-slate-900">
                    Portfolio Overview
                  </h2>

                  <p className="mt-1 text-sm text-slate-500">
                    High-level lending activity for the selected reporting
                    period.
                  </p>
                </div>
              </div>

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
                  danger={safeNumber(summary.overdueLoans) > 0}
                />
              </div>
            </section>

            {safeNumber(summary.legacyImportedLoanCount) > 0 && (
              <Section
                title="Legacy Portfolio Migration"
                description="Historical cumulative values imported from the legacy ledger. These are shown separately from current-period cash flows because the legacy source has no individual historical payment dates."
              >
                <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
                  <Metric
                    label="Imported Loans"
                    value={formatNumber(summary.legacyImportedLoanCount)}
                  />
                  <Metric
                    label="Historical Principal Disbursed"
                    value={formatMoney(
                      summary.legacyHistoricalPrincipalDisbursed,
                      currency,
                    )}
                  />
                  <Metric
                    label="Historical Principal Collected"
                    value={formatMoney(
                      summary.legacyHistoricalPrincipalCollected,
                      currency,
                    )}
                  />
                  <Metric
                    label="Historical Interest Collected"
                    value={formatMoney(
                      summary.legacyHistoricalInterestCollected,
                      currency,
                    )}
                  />
                  <Metric
                    label="Historical Fees Collected"
                    value={formatMoney(
                      summary.legacyHistoricalFeesCollected,
                      currency,
                    )}
                  />
                  <Metric
                    label="Historical Penalties Collected"
                    value={formatMoney(
                      summary.legacyHistoricalPenaltiesCollected,
                      currency,
                    )}
                  />
                  <Metric
                    label="Historical Total Collected"
                    value={formatMoney(
                      summary.legacyHistoricalTotalCollected,
                      currency,
                    )}
                  />
                </div>
              </Section>
            )}

            {/* Portfolio quality */}
            <Section
              title="Portfolio Quality"
              description="Delinquency, PAR and non-performing loan indicators."
            >
              <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
                <Metric
                  label="PAR"
                  value={formatRatioPercent(summary.parRatio)}
                  secondary={formatMoney(summary.parAmount, currency)}
                  danger={safeNumber(summary.parRatio) > 0.05}
                />

                <Metric
                  label="PAR > 30 Days"
                  value={formatRatioPercent(summary.par30Ratio)}
                />

                <Metric
                  label="PAR > 60 Days"
                  value={formatRatioPercent(summary.par60Ratio)}
                />

                <Metric
                  label="PAR > 90 Days"
                  value={formatRatioPercent(summary.par90Ratio)}
                />

                <Metric
                  label="NPL Ratio"
                  value={formatRatioPercent(summary.nplRatio)}
                  secondary={formatMoney(summary.nplAmount, currency)}
                  danger={safeNumber(summary.nplRatio) > 0}
                />

                <Metric
                  label="NPL Loans"
                  value={formatNumber(summary.nplLoanCount)}
                  danger={safeNumber(summary.nplLoanCount) > 0}
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

            {/* Borrowers */}
            <Section
              title="Borrower Statistics"
              description="Borrower demographics reported for the selected period."
            >
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
                    percentageOf(summary.maleBorrowers, totalBorrowers),
                  )} of borrowers`}
                />

                <Metric
                  label="Female Borrowers"
                  value={formatNumber(summary.femaleBorrowers)}
                  secondary={`${formatPercent(
                    percentageOf(summary.femaleBorrowers, totalBorrowers),
                  )} of borrowers`}
                />

                <Metric
                  label="Other Gender"
                  value={formatNumber(summary.otherGenderBorrowers)}
                  secondary={`${formatPercent(
                    percentageOf(summary.otherGenderBorrowers, totalBorrowers),
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

            {/* Repayment */}
            <Section
              title="Repayment Performance"
              description="Collections and repayment obligations recorded during the reporting period."
            >
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
                  danger={safeNumber(summary.interestAccruedUnpaid) > 0}
                />

                <Metric
                  label="Unpaid Fees"
                  value={formatMoney(summary.feesAccruedUnpaid, currency)}
                  danger={safeNumber(summary.feesAccruedUnpaid) > 0}
                />

                <Metric
                  label="Missed Payments"
                  value={formatNumber(summary.missedPayments)}
                  danger={safeNumber(summary.missedPayments) > 0}
                />

                <Metric
                  label="Overdue Payments"
                  value={formatNumber(summary.overduePayments)}
                  danger={safeNumber(summary.overduePayments) > 0}
                />
              </div>
            </Section>
          </>
        ) : (
          <Alert type="info" title="No summary data">
            The report endpoint returned no summary for the selected period.
          </Alert>
        )}

        {/* Financial statement */}
        <FinancialStatement report={financialStatement} currency={currency} />

        {/* Breakdowns */}
        <div className="grid gap-6 xl:grid-cols-2">
          <Breakdown
            title="Loans by Loan Type"
            description="Loan portfolio distribution by product or loan type."
            rows={loanTypes}
            currency={currency}
            showPercentage
          />

          <Breakdown
            title="Borrowers by Gender"
            description="Gender distribution from the regulatory borrower breakdown."
            rows={genders}
            currency={currency}
            showPercentage
            totalCountOverride={totalBorrowers}
          />
        </div>

        <Breakdown
          title="Loans by Branch"
          description="Loan distribution across reporting branches."
          rows={branches}
          currency={currency}
          showPercentage={false}
        />

        {/* Gender reconciliation */}
        {summary ? (
          <Section
            title="Gender Reconciliation"
            description="Source-level reconciliation between summary borrower totals and the gender breakdown."
          >
            {!genderSummaryReconciles || !genderBreakdownReconciles ? (
              <Alert type="warning" title="Reconciliation requires attention">
                <div className="space-y-1">
                  <p>
                    Summary gender total:{" "}
                    <strong>{formatNumber(genderReportedTotal)}</strong>
                  </p>

                  <p>
                    Reported total borrowers:{" "}
                    <strong>{formatNumber(totalBorrowers)}</strong>
                  </p>

                  <p>
                    Gender breakdown total:{" "}
                    <strong>{formatNumber(genderBreakdownTotal)}</strong>
                  </p>

                  <p className="pt-1">
                    The application is displaying the values returned by the
                    backend and is not silently correcting the discrepancy.
                  </p>
                </div>
              </Alert>
            ) : (
              <Alert type="success" title="Gender data reconciles">
                The reported male, female and other-gender counts reconcile with
                the total borrower count.
              </Alert>
            )}

            <div className="mt-5 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <Metric
                label="Total Borrowers"
                value={formatNumber(totalBorrowers)}
              />

              <Metric
                label="Male"
                value={formatNumber(displayedMaleCount)}
                secondary={formatPercent(
                  percentageOf(displayedMaleCount, totalBorrowers),
                )}
              />

              <Metric
                label="Female"
                value={formatNumber(displayedFemaleCount)}
                secondary={formatPercent(
                  percentageOf(displayedFemaleCount, totalBorrowers),
                )}
              />

              <Metric
                label="Other / Unspecified"
                value={formatNumber(displayedOtherGenderCount)}
                secondary={formatPercent(
                  percentageOf(displayedOtherGenderCount, totalBorrowers),
                )}
              />
            </div>
          </Section>
        ) : null}

        {/* Portfolio aging */}
        {summary ? (
          <Section
            title="Portfolio Aging"
            description="Outstanding portfolio amounts grouped by delinquency age."
          >
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
        ) : null}

        {/* Data quality */}
        {summary ? (
          <Section
            title="Data Quality"
            description="Exceptions that may affect regulatory reporting completeness."
          >
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
              <Metric
                label="Missing Borrower"
                value={formatNumber(summary.loansMissingBorrower)}
                danger={safeNumber(summary.loansMissingBorrower) > 0}
              />

              <Metric
                label="Missing National ID"
                value={formatNumber(summary.borrowersMissingNationalId)}
                danger={safeNumber(summary.borrowersMissingNationalId) > 0}
              />

              <Metric
                label="Missing Branch"
                value={formatNumber(summary.loansMissingBranch)}
                danger={safeNumber(summary.loansMissingBranch) > 0}
              />

              <Metric
                label="Missing Currency"
                value={formatNumber(summary.loansMissingCurrency)}
                danger={safeNumber(summary.loansMissingCurrency) > 0}
              />

              <Metric
                label="Missing Schedule"
                value={formatNumber(summary.loansMissingRepaymentSchedule)}
                danger={safeNumber(summary.loansMissingRepaymentSchedule) > 0}
              />
            </div>

            {summary.dataQualityWarnings?.length ? (
              <div className="mt-5">
                <Alert type="warning" title="Data quality warnings">
                  <ul className="list-disc space-y-1 pl-5">
                    {summary.dataQualityWarnings.map((warning, index) => (
                      <li key={`${warning}-${index}`}>{warning}</li>
                    ))}
                  </ul>
                </Alert>
              </div>
            ) : (
              <div className="mt-5">
                <Alert type="success" title="No data quality warnings">
                  The backend did not return any data-quality warnings for this
                  reporting period.
                </Alert>
              </div>
            )}
          </Section>
        ) : null}

        {/* Visual distributions */}
        <div className="grid gap-6 xl:grid-cols-2">
          <Section
            title="Gender Distribution"
            description="Visual representation of borrower gender distribution."
          >
            <div className="space-y-3">
              {genders.length === 0 ? (
                <p className="text-sm text-slate-400">
                  No gender data available.
                </p>
              ) : (
                genders.map((row, index) => {
                  const count = safeNumber(row.count);

                  const percentage =
                    totalBorrowers > 0
                      ? percentageOf(count, totalBorrowers)
                      : 0;

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
                          className="h-full rounded-full bg-indigo-500 transition-all"
                          style={{
                            width: `${Math.min(100, Math.max(0, percentage))}%`,
                          }}
                        />
                      </div>

                      <div className="mt-2 flex justify-between text-xs text-slate-400">
                        <span>{formatNumber(count)} borrowers</span>

                        <span>{formatMoney(row.amount, currency)}</span>
                      </div>
                    </div>
                  );
                })
              )}
            </div>
          </Section>

          <Section
            title="Loan Type Distribution"
            description="Portfolio distribution by loan product."
          >
            <div className="space-y-3">
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
                    const count = safeNumber(row.count);

                    const percentage = percentageOf(count, total);

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
                            className="h-full rounded-full bg-indigo-500 transition-all"
                            style={{
                              width: `${Math.min(
                                100,
                                Math.max(0, percentage),
                              )}%`,
                            }}
                          />
                        </div>

                        <div className="mt-2 flex justify-between text-xs text-slate-400">
                          <span>{formatNumber(count)} loans</span>

                          <span>{formatMoney(row.amount, currency)}</span>
                        </div>
                      </div>
                    );
                  });
                })()
              )}
            </div>
          </Section>
        </div>

        {/* Footer */}
        <footer className="border-t border-slate-200 pb-8 pt-5 text-center">
          <p className="text-xs font-medium text-slate-500">
            BNR Regulatory Report
          </p>

          <p className="mt-1 text-xs text-slate-400">
            {labelize(period)}
            {summary?.periodStart && summary?.periodEnd
              ? ` • ${summary.periodStart} → ${summary.periodEnd}`
              : ""}
            {summary?.reportReference
              ? ` • Reference ${summary.reportReference}`
              : ""}
          </p>
        </footer>
      </div>
    </main>
  );
}
