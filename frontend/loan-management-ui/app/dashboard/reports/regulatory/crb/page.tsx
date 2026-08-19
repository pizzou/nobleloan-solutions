"use client";

import React, { useCallback, useEffect, useMemo, useState } from "react";

import {
  regulatoryApi,
  type CreditRecord,
  type ExportFormat,
} from "@/services/regulatoryService";

/* -------------------------------------------------------------------------- */
/* Utilities                                                                  */
/* -------------------------------------------------------------------------- */

const safeNumber = (value: unknown): number => {
  if (typeof value === "number") {
    return Number.isFinite(value) ? value : 0;
  }

  const result = Number(value);

  return Number.isFinite(result) ? result : 0;
};

const safeString = (value: unknown): string => {
  if (value === null || value === undefined) {
    return "";
  }

  return String(value).trim();
};

const labelize = (value?: string | null): string => {
  if (!value) {
    return "—";
  }

  return value
    .replace(/_/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .replace(/\b\w/g, (character) => character.toUpperCase());
};

const formatNumber = (value: unknown): string => {
  return new Intl.NumberFormat("en-US").format(safeNumber(value));
};

const formatMoney = (value: unknown, currency = "RWF"): string => {
  const amount = safeNumber(value);
  const normalizedCurrency = currency || "RWF";

  try {
    return new Intl.NumberFormat("en-RW", {
      style: "currency",
      currency: normalizedCurrency,
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(amount);
  } catch {
    return `${normalizedCurrency} ${amount.toLocaleString("en-US", {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    })}`;
  }
};

const formatDate = (value?: string | null): string => {
  if (!value) {
    return "—";
  }

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat("en-RW", {
    year: "numeric",
    month: "short",
    day: "numeric",
  }).format(date);
};

const formatPercent = (value: number, decimals = 1): string => {
  if (!Number.isFinite(value)) {
    return "0.0%";
  }

  return `${value.toFixed(decimals)}%`;
};

/* -------------------------------------------------------------------------- */
/* Credit status                                                              */
/* -------------------------------------------------------------------------- */

type StatusTone = "success" | "warning" | "danger" | "neutral";

const getStatusTone = (value?: string | null): StatusTone => {
  const normalized = safeString(value).toUpperCase();

  if (
    [
      "ACTIVE",
      "PAID",
      "CLOSED",
      "CURRENT",
      "PERFORMING",
      "GOOD",
      "NORMAL",
    ].includes(normalized)
  ) {
    return "success";
  }

  if (
    [
      "OVERDUE",
      "WATCH",
      "PAST_DUE",
      "SPECIAL_MENTION",
      "WATCHLIST",
      "SUBSTANDARD",
    ].includes(normalized)
  ) {
    return "warning";
  }

  if (
    [
      "DEFAULTED",
      "DOUBTFUL",
      "WRITTEN_OFF",
      "LOSS",
      "NON_PERFORMING",
      "NPL",
    ].includes(normalized)
  ) {
    return "danger";
  }

  return "neutral";
};

const statusClasses: Record<StatusTone, string> = {
  success: "border-emerald-200 bg-emerald-50 text-emerald-700",
  warning: "border-amber-200 bg-amber-50 text-amber-700",
  danger: "border-red-200 bg-red-50 text-red-700",
  neutral: "border-slate-200 bg-slate-100 text-slate-600",
};

function StatusBadge({ value }: { value?: string | null }) {
  if (!value) {
    return (
      <span className="inline-flex items-center rounded-full border border-slate-200 bg-slate-50 px-2.5 py-1 text-[10px] font-bold uppercase tracking-wide text-slate-500">
        Not Available
      </span>
    );
  }

  const tone = getStatusTone(value);

  return (
    <span
      className={`inline-flex items-center rounded-full border px-2.5 py-1 text-[10px] font-bold uppercase tracking-wide ${statusClasses[tone]}`}
    >
      {labelize(value)}
    </span>
  );
}

/* -------------------------------------------------------------------------- */
/* Components                                                                 */
/* -------------------------------------------------------------------------- */

function StatCard({
  label,
  value,
  description,
  tone = "default",
}: {
  label: string;
  value: string;
  description?: string;
  tone?: "default" | "danger" | "warning" | "success";
}) {
  const valueClass =
    tone === "danger"
      ? "text-red-600"
      : tone === "warning"
        ? "text-amber-600"
        : tone === "success"
          ? "text-emerald-600"
          : "text-slate-950";

  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition-shadow hover:shadow-md">
      <p className="text-[10px] font-bold uppercase tracking-[0.12em] text-slate-400">
        {label}
      </p>

      <p className={`mt-2 text-2xl font-bold tracking-tight ${valueClass}`}>
        {value}
      </p>

      {description ? (
        <p className="mt-1 text-xs text-slate-400">{description}</p>
      ) : null}
    </div>
  );
}

function FilterField({
  label,
  value,
  onChange,
  type = "text",
  placeholder,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: "text" | "date";
  placeholder?: string;
}) {
  return (
    <label className="block">
      <span className="mb-2 block text-[10px] font-bold uppercase tracking-[0.12em] text-slate-400">
        {label}
      </span>

      <input
        type={type}
        value={value}
        placeholder={placeholder}
        onChange={(event) => onChange(event.target.value)}
        className="w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm text-slate-900 outline-none transition placeholder:text-slate-300 focus:border-indigo-400 focus:ring-4 focus:ring-indigo-50"
      />
    </label>
  );
}

function EmptyState() {
  return (
    <div className="px-6 py-16 text-center">
      <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-2xl bg-slate-100">
        <svg
          viewBox="0 0 24 24"
          fill="none"
          className="h-8 w-8 text-slate-400"
          stroke="currentColor"
          strokeWidth="1.7"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M9 12h6m-6 4h4m-7 5h10a2 2 0 0 0 2-2V7.828a2 2 0 0 0-.586-1.414l-3.828-3.828A2 2 0 0 0 12.172 2H6a2 2 0 0 0-2 2v15a2 2 0 0 0 2 2Z"
          />
        </svg>
      </div>

      <h3 className="mt-5 text-sm font-bold text-slate-900">
        No credit records found
      </h3>

      <p className="mx-auto mt-1 max-w-md text-sm text-slate-500">
        No credit bureau records match the current search criteria. Try
        adjusting the borrower, branch or reporting period.
      </p>
    </div>
  );
}

function LoadingTable() {
  return (
    <div className="space-y-3 p-5">
      {Array.from({ length: 7 }).map((_, index) => (
        <div
          key={index}
          className="h-14 animate-pulse rounded-xl bg-slate-100"
        />
      ))}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Main page                                                                  */
/* -------------------------------------------------------------------------- */

export default function CreditBureauPage() {
  const [borrowerId, setBorrowerId] = useState("");
  const [branchId, setBranchId] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");

  const [records, setRecords] = useState<CreditRecord[]>([]);

  const [loading, setLoading] = useState(true);

  const [exporting, setExporting] = useState<ExportFormat | null>(null);

  const [error, setError] = useState<string | null>(null);

  /* ---------------------------------------------------------------------- */
  /* Search parameters                                                      */
  /* ---------------------------------------------------------------------- */

  const searchParams = useMemo(
    () => ({
      ...(borrowerId
        ? {
            borrowerId: Number(borrowerId),
          }
        : {}),
      ...(branchId
        ? {
            branchId: Number(branchId),
          }
        : {}),
      ...(from ? { from } : {}),
      ...(to ? { to } : {}),
    }),
    [borrowerId, branchId, from, to],
  );

  /* ---------------------------------------------------------------------- */
  /* Validation                                                             */
  /* ---------------------------------------------------------------------- */

  const validate = useCallback(() => {
    if (borrowerId) {
      const id = Number(borrowerId);

      if (!Number.isInteger(id) || id <= 0) {
        return "Borrower ID must be a positive whole number.";
      }
    }

    if (branchId) {
      const id = Number(branchId);

      if (!Number.isInteger(id) || id <= 0) {
        return "Branch ID must be a positive whole number.";
      }
    }

    if (from && to && from > to) {
      return "Start date cannot be after the end date.";
    }

    return null;
  }, [borrowerId, branchId, from, to]);

  /* ---------------------------------------------------------------------- */
  /* Load records                                                           */
  /* ---------------------------------------------------------------------- */

  const loadPreview = useCallback(async () => {
    const validationError = validate();

    if (validationError) {
      setError(validationError);
      return;
    }

    try {
      setLoading(true);
      setError(null);

      const result = await regulatoryApi.creditBureauPreview(searchParams);

      setRecords(Array.isArray(result) ? result : []);
    } catch (err) {
      console.error("Credit Bureau preview error:", err);

      setRecords([]);

      setError(
        regulatoryApi.getErrorMessage(
          err,
          "Unable to load Credit Bureau records.",
        ),
      );
    } finally {
      setLoading(false);
    }
  }, [searchParams, validate]);

  /*
   * Load once when the page opens.
   *
   * Filters are intentionally NOT automatically submitted whenever
   * the user types. This prevents unnecessary API requests.
   */
  useEffect(() => {
    void loadPreview();

    // Intentionally run only on initial page load.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /* ---------------------------------------------------------------------- */
  /* Export                                                                 */
  /* ---------------------------------------------------------------------- */

  const exportRecords = useCallback(
    async (format: ExportFormat) => {
      const validationError = validate();

      if (validationError) {
        setError(validationError);
        return;
      }

      try {
        setExporting(format);
        setError(null);

        await regulatoryApi.creditBureauExport(format, searchParams);
      } catch (err) {
        console.error("Credit Bureau export error:", err);

        setError(
          regulatoryApi.getErrorMessage(
            err,
            `Unable to export Credit Bureau ${format.toUpperCase()} report.`,
          ),
        );
      } finally {
        setExporting(null);
      }
    },
    [searchParams, validate],
  );

  /* ---------------------------------------------------------------------- */
  /* Derived statistics                                                     */
  /* ---------------------------------------------------------------------- */

  const statistics = useMemo(() => {
    const uniqueBorrowers = new Set(
      records
        .map((record) => record.borrowerId)
        .filter((value) => value !== null && value !== undefined),
    );

    const overdue = records.filter(
      (record) => safeNumber(record.daysPastDue) > 0,
    );

    const male = records.filter(
      (record) => safeString(record.gender).toLowerCase() === "male",
    );

    const female = records.filter(
      (record) => safeString(record.gender).toLowerCase() === "female",
    );

    const active = records.filter((record) => {
      const status = safeString(record.loanStatus).toUpperCase();

      return status === "ACTIVE";
    });

    const defaulted = records.filter((record) => {
      const status = safeString(record.loanStatus).toUpperCase();

      const classification = safeString(
        record.repaymentClassification,
      ).toUpperCase();

      return (
        ["DEFAULTED", "WRITTEN_OFF"].includes(status) ||
        ["DEFAULTED", "DOUBTFUL", "LOSS", "NPL"].includes(classification)
      );
    });

    const outstanding = records.reduce(
      (total, record) => total + safeNumber(record.outstandingBalance),
      0,
    );

    const loanAmount = records.reduce(
      (total, record) => total + safeNumber(record.loanAmount),
      0,
    );

    return {
      borrowerCount: uniqueBorrowers.size,
      overdueCount: overdue.length,
      maleCount: male.length,
      femaleCount: female.length,
      activeCount: active.length,
      defaultedCount: defaulted.length,
      outstanding,
      loanAmount,
    };
  }, [records]);

  const genderTotal = statistics.maleCount + statistics.femaleCount;

  const femalePercentage =
    genderTotal > 0 ? (statistics.femaleCount / genderTotal) * 100 : 0;

  const malePercentage =
    genderTotal > 0 ? (statistics.maleCount / genderTotal) * 100 : 0;

  /* ---------------------------------------------------------------------- */
  /* Borrower display                                                       */
  /* ---------------------------------------------------------------------- */

  const getBorrowerName = (record: CreditRecord): string => {
    /*
     * fullName is the current CreditRecord field.
     *
     * The second fallback is intentionally defensive for API responses
     * that may expose borrowerName while the frontend type is catching up.
     */
    const fullName = safeString(record.fullName);

    if (fullName) {
      return fullName;
    }

    const dynamicRecord = record as CreditRecord & {
      borrowerName?: string | null;
    };

    const borrowerName = safeString(dynamicRecord.borrowerName);

    return borrowerName || "Unknown Borrower";
  };

  /* ---------------------------------------------------------------------- */
  /* Render                                                                 */
  /* ---------------------------------------------------------------------- */

  return (
    <main className="min-h-screen bg-slate-50">
      <div className="mx-auto max-w-[1800px] space-y-6 p-4 md:p-6 lg:p-8">
        {/* ---------------------------------------------------------------- */}
        {/* Header                                                            */}
        {/* ---------------------------------------------------------------- */}

        <section className="overflow-hidden rounded-3xl bg-gradient-to-br from-slate-950 via-indigo-950 to-violet-950 text-white shadow-xl">
          <div className="relative p-6 md:p-8 lg:p-10">
            <div className="pointer-events-none absolute -right-24 -top-24 h-72 w-72 rounded-full bg-indigo-500/10 blur-3xl" />

            <div className="relative flex flex-col gap-8 lg:flex-row lg:items-center lg:justify-between">
              <div>
                <div className="mb-4 inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/10 px-3 py-1.5 text-xs font-semibold">
                  <span className="h-2 w-2 rounded-full bg-emerald-400" />
                  Credit Information
                </div>

                <h1 className="text-3xl font-bold tracking-tight md:text-4xl">
                  Credit Bureau
                </h1>

                <p className="mt-3 max-w-3xl text-sm leading-6 text-indigo-200 md:text-base">
                  Centralized borrower credit information, loan history,
                  repayment performance and credit reporting records.
                </p>
              </div>

              <div className="flex flex-wrap gap-2">
                {(["pdf", "xlsx", "csv"] as ExportFormat[]).map((format) => (
                  <button
                    key={format}
                    type="button"
                    onClick={() => void exportRecords(format)}
                    disabled={exporting !== null || loading}
                    className="rounded-xl border border-white/15 bg-white/10 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-white/20 disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    {exporting === format
                      ? "Exporting…"
                      : `Export ${format.toUpperCase()}`}
                  </button>
                ))}
              </div>
            </div>
          </div>
        </section>

        {/* ---------------------------------------------------------------- */}
        {/* Error                                                             */}
        {/* ---------------------------------------------------------------- */}

        {error ? (
          <div
            role="alert"
            className="rounded-2xl border border-red-200 bg-red-50 p-4 text-sm text-red-700"
          >
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="font-bold">Unable to complete request</p>

                <p className="mt-1">{error}</p>
              </div>

              <button
                type="button"
                onClick={() => setError(null)}
                className="shrink-0 rounded-lg px-2 py-1 font-semibold hover:bg-red-100"
              >
                Dismiss
              </button>
            </div>
          </div>
        ) : null}

        {/* ---------------------------------------------------------------- */}
        {/* Search                                                            */}
        {/* ---------------------------------------------------------------- */}

        <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm md:p-6">
          <div className="flex flex-col gap-1">
            <h2 className="text-lg font-bold text-slate-900">
              Credit Bureau Search
            </h2>

            <p className="text-sm text-slate-500">
              Search and review credit records before exporting a regulatory
              report.
            </p>
          </div>

          <div className="mt-6 grid gap-4 md:grid-cols-2 xl:grid-cols-5">
            <FilterField
              label="Borrower ID"
              value={borrowerId}
              onChange={setBorrowerId}
              placeholder="Optional"
            />

            <FilterField
              label="Branch ID"
              value={branchId}
              onChange={setBranchId}
              placeholder="Optional"
            />

            <FilterField
              label="From"
              type="date"
              value={from}
              onChange={setFrom}
            />

            <FilterField label="To" type="date" value={to} onChange={setTo} />

            <div className="flex items-end">
              <button
                type="button"
                onClick={() => void loadPreview()}
                disabled={loading}
                className="w-full rounded-xl bg-slate-950 px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-50"
              >
                {loading ? "Searching…" : "Search Records"}
              </button>
            </div>
          </div>
        </section>

        {/* ---------------------------------------------------------------- */}
        {/* KPI cards                                                         */}
        {/* ---------------------------------------------------------------- */}

        <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <StatCard
            label="Credit Records"
            value={formatNumber(records.length)}
            description="Records returned by search"
          />

          <StatCard
            label="Unique Borrowers"
            value={formatNumber(statistics.borrowerCount)}
            description="Distinct borrower IDs"
          />

          <StatCard
            label="Overdue / Delinquent"
            value={formatNumber(statistics.overdueCount)}
            description="Records with days past due"
            tone={statistics.overdueCount > 0 ? "warning" : "default"}
          />

          <StatCard
            label="Defaulted / High Risk"
            value={formatNumber(statistics.defaultedCount)}
            description="Default or non-performing records"
            tone={statistics.defaultedCount > 0 ? "danger" : "default"}
          />
        </section>

        {/* ---------------------------------------------------------------- */}
        {/* Portfolio summary                                                 */}
        {/* ---------------------------------------------------------------- */}

        <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <StatCard
            label="Active Loans"
            value={formatNumber(statistics.activeCount)}
            description="Currently active credit records"
            tone="success"
          />

          <StatCard
            label="Total Loan Amount"
            value={formatMoney(statistics.loanAmount)}
            description="Loan amounts represented in results"
          />

          <StatCard
            label="Outstanding Balance"
            value={formatMoney(statistics.outstanding)}
            description="Outstanding principal represented"
          />

          <StatCard
            label="Female / Male"
            value={`${formatNumber(statistics.femaleCount)} / ${formatNumber(
              statistics.maleCount,
            )}`}
            description="Gender distribution in returned records"
          />
        </section>

        {/* ---------------------------------------------------------------- */}
        {/* Gender distribution                                               */}
        {/* ---------------------------------------------------------------- */}

        <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm md:p-6">
          <div>
            <h2 className="text-lg font-bold text-slate-900">
              Gender Distribution
            </h2>

            <p className="mt-1 text-sm text-slate-500">
              Gender composition of the returned credit records.
            </p>
          </div>

          <div className="mt-6 grid gap-4 md:grid-cols-2">
            <div className="rounded-2xl border border-slate-200 bg-slate-50 p-5">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                    Female
                  </p>

                  <p className="mt-2 text-2xl font-bold text-slate-900">
                    {formatNumber(statistics.femaleCount)}
                  </p>
                </div>

                <p className="text-lg font-bold text-indigo-600">
                  {formatPercent(femalePercentage, 2)}
                </p>
              </div>

              <div className="mt-4 h-2 overflow-hidden rounded-full bg-slate-200">
                <div
                  className="h-full rounded-full bg-indigo-500 transition-all"
                  style={{
                    width: `${Math.min(100, femalePercentage)}%`,
                  }}
                />
              </div>
            </div>

            <div className="rounded-2xl border border-slate-200 bg-slate-50 p-5">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                    Male
                  </p>

                  <p className="mt-2 text-2xl font-bold text-slate-900">
                    {formatNumber(statistics.maleCount)}
                  </p>
                </div>

                <p className="text-lg font-bold text-indigo-600">
                  {formatPercent(malePercentage, 2)}
                </p>
              </div>

              <div className="mt-4 h-2 overflow-hidden rounded-full bg-slate-200">
                <div
                  className="h-full rounded-full bg-indigo-500 transition-all"
                  style={{
                    width: `${Math.min(100, malePercentage)}%`,
                  }}
                />
              </div>
            </div>
          </div>
        </section>

        {/* ---------------------------------------------------------------- */}
        {/* Credit records                                                    */}
        {/* ---------------------------------------------------------------- */}

        <section className="overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-sm">
          <div className="border-b border-slate-200 px-5 py-5 md:px-6">
            <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
              <div>
                <h2 className="text-lg font-bold text-slate-900">
                  Credit Records
                </h2>

                <p className="mt-1 text-sm text-slate-500">
                  Detailed borrower and loan-level credit information.
                </p>
              </div>

              <div className="rounded-xl bg-slate-100 px-3 py-2 text-xs font-semibold text-slate-600">
                {formatNumber(records.length)} records
              </div>
            </div>
          </div>

          {loading ? (
            <LoadingTable />
          ) : records.length === 0 ? (
            <EmptyState />
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-[1900px] w-full text-sm">
                <thead className="sticky top-0 z-10 bg-slate-50">
                  <tr>
                    {[
                      "Borrower",
                      "National ID",
                      "Gender",
                      "Loan Number",
                      "Loan Type",
                      "Loan Status",
                      "Classification",
                      "Loan Amount",
                      "Outstanding",
                      "Days Past Due",
                      "Credit Score",
                      "Date Opened",
                      "Last Payment",
                      "Maturity",
                      "Date Closed",
                      "Branch",
                      "Currency",
                    ].map((header) => (
                      <th
                        key={header}
                        className="whitespace-nowrap border-b border-slate-200 px-5 py-3 text-left text-[10px] font-bold uppercase tracking-[0.1em] text-slate-400"
                      >
                        {header}
                      </th>
                    ))}
                  </tr>
                </thead>

                <tbody className="divide-y divide-slate-100">
                  {records.map((record, index) => {
                    const currency = safeString(record.currency) || "RWF";

                    const daysPastDue = safeNumber(record.daysPastDue);

                    return (
                      <tr
                        key={`${record.borrowerId ?? "borrower"}-${record.loanNumber ?? "loan"}-${index}`}
                        className="transition-colors hover:bg-slate-50"
                      >
                        {/* Borrower */}
                        <td className="px-5 py-4">
                          <div className="min-w-[190px]">
                            <div className="font-semibold text-slate-900">
                              {getBorrowerName(record)}
                            </div>

                            <div className="mt-0.5 text-xs text-slate-400">
                              Borrower ID: {record.borrowerId ?? "—"}
                            </div>
                          </div>
                        </td>

                        {/* National ID */}
                        <td className="px-5 py-4 font-mono text-xs text-slate-600">
                          {record.nationalId || "Not available"}
                        </td>

                        {/* Gender */}
                        <td className="px-5 py-4 text-slate-600">
                          {record.gender ? labelize(record.gender) : "—"}
                        </td>

                        {/* Loan number */}
                        <td className="px-5 py-4 font-mono text-xs font-semibold text-slate-700">
                          {record.loanNumber || "—"}
                        </td>

                        {/* Loan type */}
                        <td className="px-5 py-4 text-slate-700">
                          {labelize(record.loanType)}
                        </td>

                        {/* Loan status */}
                        <td className="px-5 py-4">
                          <StatusBadge value={record.loanStatus} />
                        </td>

                        {/* Classification */}
                        <td className="px-5 py-4">
                          <StatusBadge value={record.repaymentClassification} />
                        </td>

                        {/* Loan amount */}
                        <td className="whitespace-nowrap px-5 py-4 text-right font-semibold text-slate-800">
                          {formatMoney(record.loanAmount, currency)}
                        </td>

                        {/* Outstanding */}
                        <td className="whitespace-nowrap px-5 py-4 text-right font-semibold text-slate-900">
                          {formatMoney(record.outstandingBalance, currency)}
                        </td>

                        {/* DPD */}
                        <td className="px-5 py-4 text-center">
                          <span
                            className={
                              daysPastDue > 0
                                ? "font-bold text-red-600"
                                : "font-semibold text-emerald-600"
                            }
                          >
                            {formatNumber(daysPastDue)}
                          </span>
                        </td>

                        {/* Credit score */}
                        <td className="px-5 py-4">
                          {record.creditScore !== null &&
                          record.creditScore !== undefined ? (
                            <span className="font-bold text-slate-800">
                              {formatNumber(record.creditScore)}
                            </span>
                          ) : (
                            "—"
                          )}
                        </td>

                        {/* Dates */}
                        <td className="whitespace-nowrap px-5 py-4 text-slate-500">
                          {formatDate(record.dateOpened)}
                        </td>

                        <td className="whitespace-nowrap px-5 py-4 text-slate-500">
                          {formatDate(record.lastPaymentDate)}
                        </td>

                        <td className="whitespace-nowrap px-5 py-4 text-slate-500">
                          {formatDate(record.maturityDate)}
                        </td>

                        <td className="whitespace-nowrap px-5 py-4 text-slate-500">
                          {formatDate(record.dateClosed)}
                        </td>

                        {/* Branch */}
                        <td className="px-5 py-4 font-medium text-slate-700">
                          {record.branchName || "—"}
                        </td>

                        {/* Currency */}
                        <td className="px-5 py-4 font-semibold text-slate-700">
                          {currency}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </section>

        {/* ---------------------------------------------------------------- */}
        {/* Footer                                                            */}
        {/* ---------------------------------------------------------------- */}

        <footer className="border-t border-slate-200 pb-8 pt-2 text-center text-xs text-slate-400">
          Credit Bureau • Confidential regulatory information
        </footer>
      </div>
    </main>
  );
}
