"use client";

import React, { useCallback, useEffect, useMemo, useState } from "react";

import {
  regulatoryApi,
  type CreditRecord,
  type ExportFormat,
} from "@/services/regulatoryService";

/* ==========================================================================
   NOBLE LOAN SOLUTIONS
   CREDIT BUREAU / REGULATORY CREDIT INFORMATION
   BANK-GRADE PRODUCTION UI

   IMPORTANT:
   - Uses the existing regulatoryApi.
   - Does not calculate loan interest or repayment rules.
   - Does not modify backend financial logic.
   - Does not introduce a new API.
   ========================================================================== */

/* --------------------------------------------------------------------------
   DESIGN SYSTEM
   -------------------------------------------------------------------------- */

const BRAND = {
  navy: "#071A2F",
  navy2: "#0B2747",
  blue: "#155EEF",
  blueSoft: "#EFF6FF",
  gold: "#F4C430",
  goldSoft: "#FFF9DB",
  green: "#15803D",
  greenSoft: "#ECFDF3",
  amber: "#B45309",
  amberSoft: "#FFFBEB",
  red: "#B91C1C",
  redSoft: "#FEF2F2",
  slate: "#475569",
  slateSoft: "#F8FAFC",
  border: "#E2E8F0",
};

/* --------------------------------------------------------------------------
   UTILITIES
   -------------------------------------------------------------------------- */

const safeNumber = (value: unknown): number => {
  if (typeof value === "number") {
    return Number.isFinite(value) ? value : 0;
  }

  const parsed = Number(value);

  return Number.isFinite(parsed) ? parsed : 0;
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

const formatDateTime = (value: Date | null): string => {
  if (!value) {
    return "—";
  }

  return value.toLocaleString("en-RW", {
    year: "numeric",
    month: "short",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
};

const formatPercent = (value: number, decimals = 1): string => {
  if (!Number.isFinite(value)) {
    return "0.0%";
  }

  return `${value.toFixed(decimals)}%`;
};

/* --------------------------------------------------------------------------
   STATUS
   -------------------------------------------------------------------------- */

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

/* --------------------------------------------------------------------------
   ICONS
   -------------------------------------------------------------------------- */

function Icon({
  name,
  className = "h-5 w-5",
}: {
  name:
    | "search"
    | "download"
    | "refresh"
    | "users"
    | "alert"
    | "check"
    | "money"
    | "document"
    | "filter"
    | "calendar"
    | "shield"
    | "close";
  className?: string;
}) {
  const common = {
    className,
    viewBox: "0 0 24 24",
    fill: "none",
    stroke: "currentColor",
    strokeWidth: 1.8,
    strokeLinecap: "round" as const,
    strokeLinejoin: "round" as const,
  };

  switch (name) {
    case "search":
      return (
        <svg {...common}>
          <circle cx="11" cy="11" r="7" />
          <path d="m20 20-4-4" />
        </svg>
      );

    case "download":
      return (
        <svg {...common}>
          <path d="M12 3v12" />
          <path d="m7 10 5 5 5-5" />
          <path d="M5 21h14" />
        </svg>
      );

    case "refresh":
      return (
        <svg {...common}>
          <path d="M20 11a8 8 0 0 0-14.7-4" />
          <path d="M4 4v4h4" />
          <path d="M4 13a8 8 0 0 0 14.7 4" />
          <path d="M20 20v-4h-4" />
        </svg>
      );

    case "users":
      return (
        <svg {...common}>
          <circle cx="9" cy="8" r="3" />
          <path d="M3 20a6 6 0 0 1 12 0" />
          <path d="M16 5a3 3 0 0 1 0 6" />
          <path d="M18 14a5 5 0 0 1 3 6" />
        </svg>
      );

    case "alert":
      return (
        <svg {...common}>
          <path d="M10.3 3.4 2.6 17a2 2 0 0 0 1.7 3h15.4a2 2 0 0 0 1.7-3L13.7 3.4a2 2 0 0 0-3.4 0Z" />
          <path d="M12 9v4" />
          <path d="M12 17h.01" />
        </svg>
      );

    case "check":
      return (
        <svg {...common}>
          <path d="m5 12 4 4L19 6" />
        </svg>
      );

    case "money":
      return (
        <svg {...common}>
          <rect x="3" y="5" width="18" height="14" rx="2" />
          <circle cx="12" cy="12" r="3" />
          <path d="M7 9h.01M17 15h.01" />
        </svg>
      );

    case "document":
      return (
        <svg {...common}>
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z" />
          <path d="M14 2v6h6" />
          <path d="M8 13h8M8 17h6" />
        </svg>
      );

    case "filter":
      return (
        <svg {...common}>
          <path d="M4 6h16" />
          <path d="M7 12h10" />
          <path d="M10 18h4" />
        </svg>
      );

    case "calendar":
      return (
        <svg {...common}>
          <rect x="3" y="4" width="18" height="17" rx="2" />
          <path d="M16 2v4M8 2v4M3 10h18" />
        </svg>
      );

    case "shield":
      return (
        <svg {...common}>
          <path d="M12 3 20 6v5c0 5-3.3 8.5-8 10-4.7-1.5-8-5-8-10V6l8-3Z" />
          <path d="m9 12 2 2 4-4" />
        </svg>
      );

    case "close":
      return (
        <svg {...common}>
          <path d="m6 6 12 12M18 6 6 18" />
        </svg>
      );

    default:
      return null;
  }
}

/* --------------------------------------------------------------------------
   KPI CARD
   -------------------------------------------------------------------------- */

function KpiCard({
  label,
  value,
  description,
  icon,
  tone = "navy",
}: {
  label: string;
  value: string;
  description?: string;
  icon: React.ReactNode;
  tone?: "navy" | "green" | "amber" | "red";
}) {
  const toneMap = {
    navy: {
      icon: "bg-[#EAF1F8] text-[#0B2747]",
      value: "text-[#071A2F]",
    },
    green: {
      icon: "bg-emerald-50 text-emerald-700",
      value: "text-emerald-700",
    },
    amber: {
      icon: "bg-amber-50 text-amber-700",
      value: "text-amber-700",
    },
    red: {
      icon: "bg-red-50 text-red-700",
      value: "text-red-700",
    },
  };

  const styles = toneMap[tone];

  return (
    <div className="group rounded-2xl border border-slate-200 bg-white p-5 shadow-[0_2px_12px_rgba(15,23,42,0.04)] transition duration-200 hover:-translate-y-0.5 hover:shadow-[0_10px_30px_rgba(15,23,42,0.08)]">
      <div className="flex items-start justify-between gap-4">
        <div
          className={`flex h-11 w-11 items-center justify-center rounded-xl ${styles.icon}`}
        >
          {icon}
        </div>

        <span className="text-[9px] font-bold uppercase tracking-[0.14em] text-slate-300">
          Noble
        </span>
      </div>

      <p className="mt-5 text-[10px] font-bold uppercase tracking-[0.12em] text-slate-400">
        {label}
      </p>

      <p
        className={`mt-1 text-2xl font-extrabold tracking-tight ${styles.value}`}
      >
        {value}
      </p>

      {description ? (
        <p className="mt-1.5 text-xs leading-5 text-slate-400">{description}</p>
      ) : null}
    </div>
  );
}

/* --------------------------------------------------------------------------
   FILTER FIELD
   -------------------------------------------------------------------------- */

function FilterField({
  label,
  value,
  onChange,
  type = "text",
  placeholder,
  icon,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: "text" | "date";
  placeholder?: string;
  icon?: React.ReactNode;
}) {
  return (
    <label className="block">
      <span className="mb-2 flex items-center gap-1.5 text-[10px] font-bold uppercase tracking-[0.12em] text-slate-400">
        {icon}
        {label}
      </span>

      <input
        type={type}
        value={value}
        placeholder={placeholder}
        onChange={(event) => onChange(event.target.value)}
        className="h-11 w-full rounded-xl border border-slate-200 bg-white px-3.5 text-sm font-medium text-slate-900 outline-none transition placeholder:text-slate-300 hover:border-slate-300 focus:border-[#155EEF] focus:ring-4 focus:ring-blue-50"
      />
    </label>
  );
}

/* --------------------------------------------------------------------------
   EMPTY STATE
   -------------------------------------------------------------------------- */

function EmptyState() {
  return (
    <div className="px-6 py-20 text-center">
      <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-2xl bg-[#EEF3F9] text-[#0B2747]">
        <Icon name="document" className="h-7 w-7" />
      </div>

      <h3 className="mt-5 text-sm font-extrabold text-slate-900">
        No credit records found
      </h3>

      <p className="mx-auto mt-2 max-w-md text-sm leading-6 text-slate-500">
        No credit bureau records match the current search criteria. Adjust the
        borrower, branch, or reporting period and search again.
      </p>
    </div>
  );
}

/* --------------------------------------------------------------------------
   LOADING
   -------------------------------------------------------------------------- */

function LoadingTable() {
  return (
    <div className="space-y-3 p-5">
      {Array.from({ length: 8 }).map((_, index) => (
        <div
          key={index}
          className="h-14 animate-pulse rounded-xl bg-slate-100"
        />
      ))}
    </div>
  );
}

/* --------------------------------------------------------------------------
   MAIN PAGE
   -------------------------------------------------------------------------- */

export default function CreditBureauPage() {
  const [borrowerId, setBorrowerId] = useState("");
  const [branchId, setBranchId] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");

  const [records, setRecords] = useState<CreditRecord[]>([]);

  const [loading, setLoading] = useState(true);

  const [exporting, setExporting] = useState<ExportFormat | null>(null);

  const [error, setError] = useState<string | null>(null);

  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);

  /* ----------------------------------------------------------------------
     SEARCH PARAMS
     ---------------------------------------------------------------------- */

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

  /* ----------------------------------------------------------------------
     VALIDATION
     ---------------------------------------------------------------------- */

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

  /* ----------------------------------------------------------------------
     LOAD
     ---------------------------------------------------------------------- */

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

      setLastUpdated(new Date());
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

  useEffect(() => {
    void loadPreview();

    // Initial page load only.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /* ----------------------------------------------------------------------
     EXPORT
     ---------------------------------------------------------------------- */

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

  /* ----------------------------------------------------------------------
     STATISTICS
     ---------------------------------------------------------------------- */

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

    const active = records.filter(
      (record) => safeString(record.loanStatus).toUpperCase() === "ACTIVE",
    );

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

    const averageOutstanding =
      records.length > 0 ? outstanding / records.length : 0;

    const averageLoanAmount =
      records.length > 0 ? loanAmount / records.length : 0;

    return {
      borrowerCount: uniqueBorrowers.size,
      overdueCount: overdue.length,
      maleCount: male.length,
      femaleCount: female.length,
      activeCount: active.length,
      defaultedCount: defaulted.length,
      outstanding,
      loanAmount,
      averageOutstanding,
      averageLoanAmount,
    };
  }, [records]);

  const genderTotal = statistics.maleCount + statistics.femaleCount;

  const femalePercentage =
    genderTotal > 0 ? (statistics.femaleCount / genderTotal) * 100 : 0;

  const malePercentage =
    genderTotal > 0 ? (statistics.maleCount / genderTotal) * 100 : 0;

  const overduePercentage =
    records.length > 0 ? (statistics.overdueCount / records.length) * 100 : 0;

  const defaultPercentage =
    records.length > 0 ? (statistics.defaultedCount / records.length) * 100 : 0;

  /* ----------------------------------------------------------------------
     BORROWER DISPLAY
     ---------------------------------------------------------------------- */

  const getBorrowerName = (record: CreditRecord): string => {
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

  /* ----------------------------------------------------------------------
     RENDER
     ---------------------------------------------------------------------- */

  return (
    <main className="min-h-full bg-[#F8FAFC] pb-12">
      <div className="mx-auto max-w-[1900px] space-y-6 p-4 md:p-6 lg:p-8">
        {/* ================================================================
            EXECUTIVE HEADER
            ================================================================ */}

        <section className="relative overflow-hidden rounded-3xl bg-gradient-to-br from-[#071A2F] via-[#0B2747] to-[#163D68] text-white shadow-[0_20px_60px_rgba(7,26,47,0.18)]">
          <div className="pointer-events-none absolute inset-0 overflow-hidden">
            <div className="absolute -right-24 -top-32 h-80 w-80 rounded-full bg-[#F4C430]/10 blur-3xl" />
            <div className="absolute -bottom-32 left-[42%] h-72 w-72 rounded-full bg-blue-400/10 blur-3xl" />
            <div className="absolute bottom-0 left-0 right-0 h-px bg-white/10" />
          </div>

          <div className="relative p-6 md:p-8 lg:p-10">
            <div className="flex flex-col gap-8 xl:flex-row xl:items-center xl:justify-between">
              <div className="max-w-4xl">
                <div className="mb-4 flex flex-wrap items-center gap-2">
                  <span className="inline-flex items-center rounded-full bg-[#F4C430] px-3 py-1 text-[10px] font-extrabold tracking-[0.14em] text-[#071A2F]">
                    NOBLE LOAN SOLUTIONS
                  </span>

                  <span className="inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/5 px-3 py-1 text-[10px] font-semibold text-blue-100">
                    <span className="h-1.5 w-1.5 rounded-full bg-emerald-400" />
                    Regulatory Credit Information
                  </span>
                </div>

                <h1 className="text-3xl font-extrabold tracking-tight md:text-4xl lg:text-[42px]">
                  Credit Bureau
                </h1>

                <p className="mt-3 max-w-3xl text-sm leading-7 text-blue-100 md:text-base">
                  Centralized borrower credit information, loan history,
                  repayment performance and regulatory credit reporting records.
                </p>

                <div className="mt-5 flex flex-wrap items-center gap-x-4 gap-y-2 text-xs text-blue-200">
                  <span className="inline-flex items-center gap-1.5">
                    <Icon name="shield" className="h-3.5 w-3.5" />
                    Controlled regulatory workspace
                  </span>

                  <span className="hidden h-1 w-1 rounded-full bg-blue-300/50 sm:block" />

                  <span>Last updated {formatDateTime(lastUpdated)}</span>
                </div>
              </div>

              <div className="flex flex-wrap gap-2.5">
                <button
                  type="button"
                  onClick={() => void loadPreview()}
                  disabled={loading || exporting !== null}
                  className="inline-flex h-11 items-center gap-2 rounded-xl border border-white/15 bg-white/10 px-4 text-sm font-bold text-white transition hover:bg-white/15 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  <Icon
                    name="refresh"
                    className={`h-4 w-4 ${loading ? "animate-spin" : ""}`}
                  />

                  {loading ? "Refreshing..." : "Refresh"}
                </button>

                {(["pdf", "xlsx", "csv"] as ExportFormat[]).map((format) => (
                  <button
                    key={format}
                    type="button"
                    onClick={() => void exportRecords(format)}
                    disabled={exporting !== null || loading}
                    className="inline-flex h-11 items-center gap-2 rounded-xl bg-[#F4C430] px-4 text-sm font-extrabold text-[#071A2F] shadow-sm transition hover:bg-[#FFD84D] disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    <Icon name="download" className="h-4 w-4" />

                    {exporting === format
                      ? "Exporting..."
                      : format.toUpperCase()}
                  </button>
                ))}
              </div>
            </div>
          </div>
        </section>

        {/* ================================================================
            ERROR
            ================================================================ */}

        {error ? (
          <section
            role="alert"
            className="rounded-2xl border border-red-200 bg-red-50 p-4"
          >
            <div className="flex items-start gap-3">
              <div className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-red-100 text-red-700">
                <Icon name="alert" className="h-4 w-4" />
              </div>

              <div className="min-w-0 flex-1">
                <p className="text-sm font-bold text-red-800">
                  Unable to complete request
                </p>

                <p className="mt-1 text-sm leading-6 text-red-700">{error}</p>
              </div>

              <button
                type="button"
                aria-label="Dismiss error"
                onClick={() => setError(null)}
                className="rounded-lg p-2 text-red-500 transition hover:bg-red-100 hover:text-red-700"
              >
                <Icon name="close" className="h-4 w-4" />
              </button>
            </div>
          </section>
        ) : null}

        {/* ================================================================
            SEARCH CONTROL
            ================================================================ */}

        <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-[0_2px_12px_rgba(15,23,42,0.04)]">
          <div className="border-b border-slate-200 px-5 py-5 md:px-6">
            <div className="flex items-start gap-3">
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-[#EAF1F8] text-[#0B2747]">
                <Icon name="filter" className="h-5 w-5" />
              </div>

              <div>
                <h2 className="text-base font-extrabold text-[#071A2F]">
                  Credit Bureau Search
                </h2>

                <p className="mt-1 text-xs leading-5 text-slate-500">
                  Apply controlled search criteria before reviewing or exporting
                  credit information.
                </p>
              </div>
            </div>
          </div>

          <div className="p-5 md:p-6">
            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
              <FilterField
                label="Borrower ID"
                value={borrowerId}
                onChange={setBorrowerId}
                placeholder="Optional"
                icon={<Icon name="users" className="h-3 w-3" />}
              />

              <FilterField
                label="Branch ID"
                value={branchId}
                onChange={setBranchId}
                placeholder="Optional"
              />

              <FilterField
                label="Reporting From"
                type="date"
                value={from}
                onChange={setFrom}
                icon={<Icon name="calendar" className="h-3 w-3" />}
              />

              <FilterField
                label="Reporting To"
                type="date"
                value={to}
                onChange={setTo}
                icon={<Icon name="calendar" className="h-3 w-3" />}
              />

              <div className="flex items-end">
                <button
                  type="button"
                  onClick={() => void loadPreview()}
                  disabled={loading}
                  className="inline-flex h-11 w-full items-center justify-center gap-2 rounded-xl bg-[#071A2F] px-5 text-sm font-bold text-white shadow-sm transition hover:bg-[#0B2747] disabled:cursor-not-allowed disabled:opacity-50"
                >
                  <Icon name="search" className="h-4 w-4" />

                  {loading ? "Searching..." : "Search Records"}
                </button>
              </div>
            </div>

            {(borrowerId || branchId || from || to) && (
              <div className="mt-5 flex flex-wrap items-center gap-2 border-t border-slate-100 pt-4">
                <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
                  Active filters
                </span>

                {borrowerId ? (
                  <FilterChip
                    label={`Borrower ${borrowerId}`}
                    onRemove={() => setBorrowerId("")}
                  />
                ) : null}

                {branchId ? (
                  <FilterChip
                    label={`Branch ${branchId}`}
                    onRemove={() => setBranchId("")}
                  />
                ) : null}

                {from ? (
                  <FilterChip
                    label={`From ${from}`}
                    onRemove={() => setFrom("")}
                  />
                ) : null}

                {to ? (
                  <FilterChip label={`To ${to}`} onRemove={() => setTo("")} />
                ) : null}
              </div>
            )}
          </div>
        </section>

        {/* ================================================================
            EXECUTIVE KPIs
            ================================================================ */}

        <section>
          <div className="mb-3">
            <h2 className="text-xs font-extrabold uppercase tracking-[0.14em] text-[#071A2F]">
              Credit portfolio intelligence
            </h2>

            <p className="mt-1 text-xs text-slate-400">
              Summary indicators calculated from the returned regulatory credit
              records.
            </p>
          </div>

          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <KpiCard
              label="Credit Records"
              value={formatNumber(records.length)}
              description="Records returned by the current search"
              icon={<Icon name="document" className="h-5 w-5" />}
            />

            <KpiCard
              label="Unique Borrowers"
              value={formatNumber(statistics.borrowerCount)}
              description="Distinct borrower IDs represented"
              icon={<Icon name="users" className="h-5 w-5" />}
            />

            <KpiCard
              label="Active Loans"
              value={formatNumber(statistics.activeCount)}
              description="Currently active credit records"
              icon={<Icon name="check" className="h-5 w-5" />}
              tone="green"
            />

            <KpiCard
              label="Overdue / Delinquent"
              value={formatNumber(statistics.overdueCount)}
              description={`${formatPercent(
                overduePercentage,
                1,
              )} of returned records`}
              icon={<Icon name="alert" className="h-5 w-5" />}
              tone={statistics.overdueCount > 0 ? "amber" : "navy"}
            />
          </div>
        </section>

        {/* ================================================================
            FINANCIAL / RISK KPIs
            ================================================================ */}

        <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <KpiCard
            label="Total Loan Amount"
            value={formatMoney(statistics.loanAmount)}
            description="Loan amounts represented in results"
            icon={<Icon name="money" className="h-5 w-5" />}
          />

          <KpiCard
            label="Outstanding Balance"
            value={formatMoney(statistics.outstanding)}
            description="Outstanding balance represented"
            icon={<Icon name="money" className="h-5 w-5" />}
          />

          <KpiCard
            label="Defaulted / High Risk"
            value={formatNumber(statistics.defaultedCount)}
            description={`${formatPercent(
              defaultPercentage,
              1,
            )} of returned records`}
            icon={<Icon name="alert" className="h-5 w-5" />}
            tone={statistics.defaultedCount > 0 ? "red" : "navy"}
          />

          <KpiCard
            label="Average Loan Amount"
            value={formatMoney(statistics.averageLoanAmount)}
            description="Average across returned records"
            icon={<Icon name="money" className="h-5 w-5" />}
          />
        </section>

        {/* ================================================================
            GENDER / RISK SUMMARY
            ================================================================ */}

        <div className="grid gap-5 xl:grid-cols-3">
          {/* Gender */}

          <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-[0_2px_12px_rgba(15,23,42,0.04)] md:p-6 xl:col-span-2">
            <div className="flex items-start justify-between gap-4">
              <div>
                <h2 className="text-base font-extrabold text-[#071A2F]">
                  Gender distribution
                </h2>

                <p className="mt-1 text-xs text-slate-400">
                  Gender composition of returned credit records.
                </p>
              </div>

              <div className="rounded-xl bg-slate-50 px-3 py-2 text-xs font-bold text-slate-500">
                {formatNumber(genderTotal)} classified
              </div>
            </div>

            <div className="mt-6 space-y-5">
              <GenderBar
                label="Female"
                count={statistics.femaleCount}
                percentage={femalePercentage}
                icon="F"
              />

              <GenderBar
                label="Male"
                count={statistics.maleCount}
                percentage={malePercentage}
                icon="M"
              />
            </div>
          </section>

          {/* Risk overview */}

          <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-[0_2px_12px_rgba(15,23,42,0.04)] md:p-6">
            <div>
              <h2 className="text-base font-extrabold text-[#071A2F]">
                Risk overview
              </h2>

              <p className="mt-1 text-xs text-slate-400">
                Current credit quality indicators.
              </p>
            </div>

            <div className="mt-6 space-y-3">
              <RiskRow
                label="Performing / Active"
                value={statistics.activeCount}
                total={records.length}
                tone="green"
              />

              <RiskRow
                label="Overdue"
                value={statistics.overdueCount}
                total={records.length}
                tone="amber"
              />

              <RiskRow
                label="Default / NPL"
                value={statistics.defaultedCount}
                total={records.length}
                tone="red"
              />
            </div>

            <div className="mt-5 rounded-xl border border-blue-100 bg-blue-50 p-3">
              <div className="flex items-start gap-2">
                <Icon
                  name="shield"
                  className="mt-0.5 h-4 w-4 shrink-0 text-blue-600"
                />

                <p className="text-xs leading-5 text-blue-700">
                  Risk indicators shown here are derived from the credit records
                  returned by the regulatory service.
                </p>
              </div>
            </div>
          </section>
        </div>

        {/* ================================================================
            RECORDS TABLE
            ================================================================ */}

        <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-[0_2px_12px_rgba(15,23,42,0.04)]">
          <div className="border-b border-slate-200 px-5 py-5 md:px-6">
            <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
              <div>
                <div className="flex items-center gap-2">
                  <h2 className="text-base font-extrabold text-[#071A2F]">
                    Credit Records
                  </h2>

                  <span className="rounded-full bg-[#EAF1F8] px-2.5 py-1 text-[10px] font-extrabold text-[#0B2747]">
                    {formatNumber(records.length)}
                  </span>
                </div>

                <p className="mt-1 text-xs leading-5 text-slate-400">
                  Detailed borrower, loan, repayment and credit reporting
                  information.
                </p>
              </div>

              <div className="flex flex-wrap gap-2">
                <button
                  type="button"
                  onClick={() => void exportRecords("csv")}
                  disabled={
                    exporting !== null || loading || records.length === 0
                  }
                  className="inline-flex h-10 items-center gap-2 rounded-xl border border-slate-200 bg-white px-3.5 text-xs font-bold text-slate-700 transition hover:border-slate-300 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  <Icon name="download" className="h-3.5 w-3.5" />
                  CSV
                </button>

                <button
                  type="button"
                  onClick={() => void exportRecords("xlsx")}
                  disabled={
                    exporting !== null || loading || records.length === 0
                  }
                  className="inline-flex h-10 items-center gap-2 rounded-xl border border-slate-200 bg-white px-3.5 text-xs font-bold text-slate-700 transition hover:border-slate-300 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  <Icon name="download" className="h-3.5 w-3.5" />
                  XLSX
                </button>

                <button
                  type="button"
                  onClick={() => void exportRecords("pdf")}
                  disabled={
                    exporting !== null || loading || records.length === 0
                  }
                  className="inline-flex h-10 items-center gap-2 rounded-xl bg-[#071A2F] px-3.5 text-xs font-bold text-white transition hover:bg-[#0B2747] disabled:cursor-not-allowed disabled:opacity-50"
                >
                  <Icon name="download" className="h-3.5 w-3.5" />
                  PDF
                </button>
              </div>
            </div>
          </div>

          {loading ? (
            <LoadingTable />
          ) : records.length === 0 ? (
            <EmptyState />
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-[2050px] w-full text-sm">
                <thead className="bg-[#F8FAFC]">
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
                      "DPD",
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
                        className="whitespace-nowrap border-b border-slate-200 px-5 py-3.5 text-left text-[9px] font-extrabold uppercase tracking-[0.12em] text-slate-400"
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
                        className="group transition-colors hover:bg-[#F8FAFC]"
                      >
                        {/* Borrower */}

                        <td className="px-5 py-4">
                          <div className="min-w-[210px]">
                            <div className="flex items-center gap-3">
                              <BorrowerAvatar name={getBorrowerName(record)} />

                              <div className="min-w-0">
                                <div className="truncate font-bold text-slate-900">
                                  {getBorrowerName(record)}
                                </div>

                                <div className="mt-0.5 text-[11px] text-slate-400">
                                  ID: {record.borrowerId ?? "—"}
                                </div>
                              </div>
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

                        <td className="px-5 py-4">
                          <code className="rounded-lg bg-slate-100 px-2 py-1 text-[11px] font-bold text-[#0B2747]">
                            {record.loanNumber || "—"}
                          </code>
                        </td>

                        {/* Loan type */}

                        <td className="px-5 py-4 font-medium text-slate-700">
                          {labelize(record.loanType)}
                        </td>

                        {/* Status */}

                        <td className="px-5 py-4">
                          <StatusBadge value={record.loanStatus} />
                        </td>

                        {/* Classification */}

                        <td className="px-5 py-4">
                          <StatusBadge value={record.repaymentClassification} />
                        </td>

                        {/* Loan amount */}

                        <td className="whitespace-nowrap px-5 py-4 text-right font-bold text-slate-800">
                          {formatMoney(record.loanAmount, currency)}
                        </td>

                        {/* Outstanding */}

                        <td className="whitespace-nowrap px-5 py-4 text-right font-extrabold text-[#071A2F]">
                          {formatMoney(record.outstandingBalance, currency)}
                        </td>

                        {/* DPD */}

                        <td className="px-5 py-4 text-center">
                          <span
                            className={`inline-flex min-w-10 items-center justify-center rounded-lg px-2 py-1 text-xs font-extrabold ${
                              daysPastDue > 0
                                ? "bg-red-50 text-red-700"
                                : "bg-emerald-50 text-emerald-700"
                            }`}
                          >
                            {formatNumber(daysPastDue)}
                          </span>
                        </td>

                        {/* Credit score */}

                        <td className="px-5 py-4">
                          {record.creditScore !== null &&
                          record.creditScore !== undefined ? (
                            <CreditScore
                              score={safeNumber(record.creditScore)}
                            />
                          ) : (
                            <span className="text-slate-300">—</span>
                          )}
                        </td>

                        {/* Date opened */}

                        <td className="whitespace-nowrap px-5 py-4 text-xs text-slate-500">
                          {formatDate(record.dateOpened)}
                        </td>

                        {/* Last payment */}

                        <td className="whitespace-nowrap px-5 py-4 text-xs text-slate-500">
                          {formatDate(record.lastPaymentDate)}
                        </td>

                        {/* Maturity */}

                        <td className="whitespace-nowrap px-5 py-4 text-xs text-slate-500">
                          {formatDate(record.maturityDate)}
                        </td>

                        {/* Closed */}

                        <td className="whitespace-nowrap px-5 py-4 text-xs text-slate-500">
                          {formatDate(record.dateClosed)}
                        </td>

                        {/* Branch */}

                        <td className="px-5 py-4 font-medium text-slate-700">
                          {record.branchName || "—"}
                        </td>

                        {/* Currency */}

                        <td className="px-5 py-4 font-extrabold text-[#0B2747]">
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

        {/* ================================================================
            REGULATORY FOOTER
            ================================================================ */}

        <footer className="flex flex-col gap-2 border-t border-slate-200 px-1 pt-5 text-[11px] text-slate-400 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p className="font-bold text-[#0B2747]">Noble Loan Solutions</p>

            <p className="mt-0.5">Credit Information & Regulatory Reporting</p>
          </div>

          <div className="flex items-center gap-2">
            <Icon name="shield" className="h-3.5 w-3.5" />

            <span>Confidential regulatory information</span>
          </div>
        </footer>
      </div>
    </main>
  );
}

/* --------------------------------------------------------------------------
   FILTER CHIP
   -------------------------------------------------------------------------- */

function FilterChip({
  label,
  onRemove,
}: {
  label: string;
  onRemove: () => void;
}) {
  return (
    <span className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 bg-slate-50 px-2.5 py-1.5 text-[10px] font-bold text-slate-600">
      {label}

      <button
        type="button"
        onClick={onRemove}
        className="rounded p-0.5 text-slate-400 transition hover:bg-slate-200 hover:text-slate-700"
        aria-label={`Remove ${label}`}
      >
        <Icon name="close" className="h-3 w-3" />
      </button>
    </span>
  );
}

/* --------------------------------------------------------------------------
   GENDER BAR
   -------------------------------------------------------------------------- */

function GenderBar({
  label,
  count,
  percentage,
  icon,
}: {
  label: string;
  count: number;
  percentage: number;
  icon: string;
}) {
  return (
    <div>
      <div className="flex items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-[#EAF1F8] text-xs font-extrabold text-[#0B2747]">
            {icon}
          </div>

          <div>
            <p className="text-sm font-bold text-slate-800">{label}</p>

            <p className="text-xs text-slate-400">
              {formatNumber(count)} borrowers
            </p>
          </div>
        </div>

        <span className="text-sm font-extrabold text-[#155EEF]">
          {formatPercent(percentage, 2)}
        </span>
      </div>

      <div className="mt-3 h-2 overflow-hidden rounded-full bg-slate-100">
        <div
          className="h-full rounded-full bg-[#155EEF] transition-all duration-500"
          style={{
            width: `${Math.min(100, Math.max(0, percentage))}%`,
          }}
        />
      </div>
    </div>
  );
}

/* --------------------------------------------------------------------------
   RISK ROW
   -------------------------------------------------------------------------- */

function RiskRow({
  label,
  value,
  total,
  tone,
}: {
  label: string;
  value: number;
  total: number;
  tone: "green" | "amber" | "red";
}) {
  const percentage = total > 0 ? (value / total) * 100 : 0;

  const styles = {
    green: {
      bar: "bg-emerald-500",
      value: "text-emerald-700",
    },
    amber: {
      bar: "bg-amber-500",
      value: "text-amber-700",
    },
    red: {
      bar: "bg-red-500",
      value: "text-red-700",
    },
  };

  return (
    <div className="rounded-xl border border-slate-100 bg-slate-50 p-3.5">
      <div className="flex items-center justify-between gap-3">
        <span className="text-xs font-semibold text-slate-600">{label}</span>

        <span className={`text-xs font-extrabold ${styles[tone].value}`}>
          {formatNumber(value)}
        </span>
      </div>

      <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-slate-200">
        <div
          className={`h-full rounded-full ${styles[tone].bar}`}
          style={{
            width: `${Math.min(100, Math.max(0, percentage))}%`,
          }}
        />
      </div>

      <p className="mt-1.5 text-[10px] text-slate-400">
        {formatPercent(percentage, 1)} of records
      </p>
    </div>
  );
}

/* --------------------------------------------------------------------------
   BORROWER AVATAR
   -------------------------------------------------------------------------- */

function BorrowerAvatar({ name }: { name: string }) {
  const parts = name.trim().split(/\s+/).filter(Boolean);

  const initials =
    parts.length >= 2
      ? `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase()
      : parts[0]
        ? parts[0].slice(0, 2).toUpperCase()
        : "B";

  return (
    <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-[#E1EAF4] text-[10px] font-extrabold text-[#0B2747] ring-2 ring-white">
      {initials}
    </div>
  );
}

/* --------------------------------------------------------------------------
   CREDIT SCORE
   -------------------------------------------------------------------------- */

function CreditScore({ score }: { score: number }) {
  const tone =
    score >= 700
      ? "text-emerald-700 bg-emerald-50"
      : score >= 600
        ? "text-amber-700 bg-amber-50"
        : "text-red-700 bg-red-50";

  return (
    <span
      className={`inline-flex min-w-12 items-center justify-center rounded-lg px-2 py-1 text-xs font-extrabold ${tone}`}
    >
      {formatNumber(score)}
    </span>
  );
}
