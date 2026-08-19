"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";

import { loanApi } from "@/services/api";
import { Loan, LoanStatus, LoanType } from "@/types";
import { formatCurrency, formatDate, LOAN_TYPE_META } from "@/lib/utils";
import { useAuth } from "@/hooks/useAuth";
import { cacheGet, cacheSet } from "@/lib/offlineDb";
import { useOnlineStatus } from "@/hooks/useOnlineStatus";

import { StatusBadge, RiskBadge, Pill } from "@/components/ui/Badge";

import { PageSpinner } from "@/components/ui/Skeleton";

import {
  IconAlertTriangle,
  IconCard,
  IconCheckCircle,
  IconClock,
  IconCoins,
  IconFileText,
  IconSearch,
  IconSend,
} from "@/components/ui/Icons";

/* ============================================================
   CONFIGURATION
   ============================================================ */

const PAGE_SIZE = 25;

/* ============================================================
   FILTER OPTIONS
   ============================================================ */

const STATUS_OPTIONS: Array<{
  value: "" | LoanStatus;
  label: string;
}> = [
  { value: "", label: "All statuses" },
  { value: "PENDING", label: "Pending" },
  { value: "UNDER_REVIEW", label: "Under review" },
  { value: "APPROVED", label: "Approved" },
  { value: "REJECTED", label: "Rejected" },
  { value: "DISBURSED", label: "Disbursed" },
  { value: "ACTIVE", label: "Active" },
  { value: "OVERDUE", label: "Overdue" },
  { value: "DEFAULTED", label: "Defaulted" },
  { value: "RESTRUCTURED", label: "Restructured" },
  { value: "WRITTEN_OFF", label: "Written off" },
  { value: "PAID", label: "Paid" },
  { value: "CLOSED", label: "Closed" },
  { value: "CANCELLED", label: "Cancelled" },
];

const TYPE_OPTIONS: Array<{
  value: "" | LoanType;
  label: string;
}> = [
  { value: "", label: "All loan types" },
  ...Object.entries(LOAN_TYPE_META).map(([value, meta]) => ({
    value: value as LoanType,
    label: meta.label,
  })),
];

/* ============================================================
   API TYPES
   ============================================================ */

type LoanPageResponse = {
  content?: Loan[];
  items?: Loan[];
  data?: Loan[];

  totalElements?: number;
  totalPages?: number;
  page?: number;
  number?: number;
  size?: number;
  numberOfElements?: number;
  last?: boolean;
};

type LoanDashboardResponse = {
  totalDisbursed?: number | string;
  totalCollected?: number | string;
  outstandingBalance?: number | string;

  activeLoans?: number | string;
  overdueLoans?: number | string;
  pendingLoans?: number | string;
  completedLoans?: number | string;
  totalLoans?: number | string;
};

type Summary = {
  totalLoans: number;
  active: number;
  overdue: number;
  pending: number;
  disbursed: number;
  outstanding: number;
};

/* ============================================================
   HELPERS
   ============================================================ */

const toNumber = (value: unknown): number => {
  const result = Number(value);

  return Number.isFinite(result) ? result : 0;
};

const humanize = (value?: string): string => {
  if (!value) return "—";

  return value
    .replace(/_/g, " ")
    .toLowerCase()
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
};

const getBorrowerName = (loan: Loan): string => {
  const first = loan.borrower?.firstName?.trim() ?? "";
  const last = loan.borrower?.lastName?.trim() ?? "";

  return `${first} ${last}`.trim() || "Unnamed borrower";
};

const getBorrowerInitials = (loan: Loan): string => {
  const first = loan.borrower?.firstName?.trim()?.charAt(0) ?? "";
  const last = loan.borrower?.lastName?.trim()?.charAt(0) ?? "";

  const initials = `${first}${last}`.toUpperCase();

  return initials || "BR";
};

const getLoanTypeLabel = (loan: Loan): string =>
  LOAN_TYPE_META[loan.loanType]?.label ?? humanize(loan.loanType);

const getPageLoans = (response: unknown): Loan[] => {
  if (Array.isArray(response)) {
    return response as Loan[];
  }

  if (!response || typeof response !== "object") {
    return [];
  }

  const root = response as LoanPageResponse;

  if (Array.isArray(root.content)) return root.content;

  if (Array.isArray(root.items)) return root.items;

  if (Array.isArray(root.data)) return root.data;

  return [];
};

const getPageMeta = (response: unknown) => {
  if (!response || typeof response !== "object") {
    return {
      totalElements: 0,
      totalPages: 0,
      page: 0,
      size: PAGE_SIZE,
      last: true,
    };
  }

  const root = response as LoanPageResponse;

  const totalElements = Math.max(0, toNumber(root.totalElements));

  const size = Math.max(1, toNumber(root.size) || PAGE_SIZE);

  const totalPages = Math.max(
    0,
    toNumber(root.totalPages) ||
      (totalElements ? Math.ceil(totalElements / size) : 0),
  );

  const page = Math.max(0, toNumber(root.number ?? root.page ?? 0));

  return {
    totalElements,
    totalPages,
    page,
    size,
    last: root.last ?? (totalPages === 0 || page >= totalPages - 1),
  };
};

/* ============================================================
   METRIC CARD
   ============================================================ */

function MetricCard({
  label,
  value,
  description,
  icon,
  tone,
}: {
  label: string;
  value: string;
  description: string;
  icon: React.ReactNode;
  tone: "blue" | "teal" | "amber" | "red" | "violet" | "slate";
}) {
  const tones = {
    blue: "border-blue-100 bg-blue-50 text-blue-700",
    teal: "border-teal-100 bg-teal-50 text-teal-700",
    amber: "border-amber-100 bg-amber-50 text-amber-700",
    red: "border-red-100 bg-red-50 text-red-700",
    violet: "border-violet-100 bg-violet-50 text-violet-700",
    slate: "border-slate-200 bg-slate-50 text-slate-700",
  };

  return (
    <div
      className="
        group
        rounded-2xl
        border
        border-slate-200/80
        bg-white
        p-5
        shadow-[0_2px_10px_rgba(15,23,42,0.03)]
        transition-all
        duration-200
        hover:-translate-y-0.5
        hover:border-slate-300
        hover:shadow-[0_10px_30px_rgba(15,23,42,0.07)]
      "
    >
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <p className="text-[10px] font-bold uppercase tracking-[0.16em] text-slate-400">
            {label}
          </p>

          <p className="mt-2 truncate text-2xl font-black tracking-tight text-slate-950">
            {value}
          </p>

          <p className="mt-1 text-xs leading-5 text-slate-500">{description}</p>
        </div>

        <div
          className={`
            flex
            h-10
            w-10
            shrink-0
            items-center
            justify-center
            rounded-xl
            border
            transition-transform
            duration-200
            group-hover:scale-105
            ${tones[tone]}
          `}
        >
          {icon}
        </div>
      </div>
    </div>
  );
}

/* ============================================================
   EMPTY STATE
   ============================================================ */

function EmptyState({ searching }: { searching: boolean }) {
  return (
    <div className="flex flex-col items-center justify-center px-6 py-20 text-center">
      <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-slate-100 text-slate-400">
        <IconSearch className="h-7 w-7" />
      </div>

      <h3 className="mt-5 text-sm font-bold text-slate-900">
        {searching ? "No matching loans" : "No loans in this portfolio"}
      </h3>

      <p className="mt-2 max-w-md text-sm leading-6 text-slate-500">
        {searching
          ? "Try a different reference number, borrower name, phone number or national ID."
          : "Once loans are created or imported, they will appear here with their current operational and financial position."}
      </p>
    </div>
  );
}

/* ============================================================
   LOAN ROW
   ============================================================ */

function LoanRow({
  loan,
  currency,
  locale,
}: {
  loan: Loan;
  currency: string;
  locale: string;
}) {
  const borrowerName = getBorrowerName(loan);
  const borrowerInitials = getBorrowerInitials(loan);
  const loanTypeLabel = getLoanTypeLabel(loan);

  const outstanding = toNumber(loan.outstandingBalance);

  const totalPaid = toNumber(loan.totalPaid);

  const repayable = toNumber(loan.totalRepayable);

  /*
   * This is a presentation progress indicator only.
   *
   * Financial balances themselves are NOT recalculated here.
   * They remain authoritative from the backend.
   */
  const progress =
    repayable > 0
      ? Math.min(100, Math.round((totalPaid / repayable) * 100))
      : 0;

  const overdue = Number(loan.daysOverdue || 0);

  return (
    <tr
      className="
        group
        border-b
        border-slate-100
        last:border-0
        transition-colors
        hover:bg-slate-50/70
      "
    >
      {/* =====================================================
          LOAN
          ===================================================== */}

      <td className="px-5 py-4 align-top">
        <Link
          href={`/dashboard/loans/${loan.id}`}
          className="block min-w-[190px]"
        >
          <div className="flex items-start gap-3">
            <div
              className="
                mt-0.5
                flex
                h-9
                w-9
                shrink-0
                items-center
                justify-center
                rounded-xl
                bg-slate-950
                text-[10px]
                font-black
                text-white
                shadow-sm
              "
            >
              #
            </div>

            <div className="min-w-0">
              <div
                className="
                  truncate
                  font-bold
                  text-slate-900
                  transition-colors
                  group-hover:text-teal-700
                "
              >
                {loan.referenceNumber || "No reference"}
              </div>

              <div className="mt-1 text-[11px] text-slate-400">
                {loan.disbursedAt
                  ? `Disbursed ${formatDate(loan.disbursedAt, locale)}`
                  : loan.createdAt
                    ? `Created ${formatDate(loan.createdAt, locale)}`
                    : "Date unavailable"}
              </div>
            </div>
          </div>
        </Link>
      </td>

      {/* =====================================================
          BORROWER
          ===================================================== */}

      <td className="px-5 py-4 align-top">
        <div className="flex min-w-[210px] items-center gap-3">
          <div
            className="
              flex
              h-10
              w-10
              shrink-0
              items-center
              justify-center
              rounded-full
              border
              border-slate-200
              bg-gradient-to-br
              from-slate-100
              to-slate-200
              text-xs
              font-black
              text-slate-700
            "
          >
            {borrowerInitials}
          </div>

          <div className="min-w-0">
            <div className="truncate font-semibold text-slate-900">
              {borrowerName}
            </div>

            <div className="mt-1 truncate text-xs text-slate-400">
              {loan.borrower?.nationalId ??
                loan.borrower?.phone ??
                loan.borrower?.email ??
                "No borrower identifier"}
            </div>
          </div>
        </div>
      </td>

      {/* =====================================================
          STATUS & RISK
          ===================================================== */}

      <td className="px-5 py-4 align-top">
        <div className="flex min-w-[180px] flex-wrap gap-1.5">
          <StatusBadge status={loan.status} />

          {loan.riskCategory ? (
            <RiskBadge category={loan.riskCategory} score={loan.riskScore} />
          ) : null}
        </div>
      </td>

      {/* =====================================================
          ORIGINAL PRINCIPAL
          ===================================================== */}

      <td className="px-5 py-4 align-top">
        <div className="min-w-[145px]">
          <div className="font-bold text-slate-900">
            {formatCurrency(loan.amount, currency, locale)}
          </div>

          <div className="mt-1 text-xs text-slate-400">Original principal</div>

          <div className="mt-1 text-[11px] text-slate-400">
            {loan.durationMonths
              ? `${loan.durationMonths} months`
              : "Term unavailable"}
          </div>
        </div>
      </td>

      {/* =====================================================
          DISBURSED
          ===================================================== */}

      <td className="px-5 py-4 align-top">
        <div className="min-w-[145px]">
          <div className="font-bold text-slate-900">
            {formatCurrency(loan.disbursedAmount ?? 0, currency, locale)}
          </div>

          <div className="mt-1 text-xs text-slate-400">Cash released</div>

          {loan.disbursedAt ? (
            <div className="mt-1 text-[11px] text-slate-400">
              {formatDate(loan.disbursedAt, locale)}
            </div>
          ) : null}
        </div>
      </td>

      {/* =====================================================
          OUTSTANDING
          ===================================================== */}

      <td className="px-5 py-4 align-top">
        <div className="min-w-[175px]">
          <div className="font-bold text-slate-900">
            {formatCurrency(outstanding, currency, locale)}
          </div>

          <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-slate-100">
            <div
              className="h-full rounded-full bg-teal-600 transition-all duration-500"
              style={{
                width: `${progress}%`,
              }}
            />
          </div>

          <div className="mt-1 text-[11px] text-slate-400">
            {progress}% collected
          </div>
        </div>
      </td>

      {/* =====================================================
          TYPE / REPAYMENT
          ===================================================== */}

      <td className="px-5 py-4 align-top text-right">
        <div className="flex min-w-[155px] justify-end">
          <Pill label={loanTypeLabel} color="blue" />
        </div>

        {loan.repaymentFrequency ? (
          <div className="mt-2 text-xs text-slate-400">
            {humanize(loan.repaymentFrequency)}
          </div>
        ) : null}

        {overdue > 0 ? (
          <div className="mt-2 flex items-center justify-end gap-1 text-xs font-bold text-red-600">
            <IconAlertTriangle className="h-3.5 w-3.5" />
            {overdue} day
            {overdue === 1 ? "" : "s"} overdue
          </div>
        ) : loan.nextDueDate ? (
          <div className="mt-2 text-xs text-slate-400">
            Next due {formatDate(loan.nextDueDate, locale)}
          </div>
        ) : null}
      </td>
    </tr>
  );
}

/* ============================================================
   MAIN PAGE
   ============================================================ */

export default function LoanListPage() {
  const { currency, locale, isOfficer } = useAuth();

  const online = useOnlineStatus();

  const [loans, setLoans] = useState<Loan[]>([]);

  const [dashboard, setDashboard] = useState<LoanDashboardResponse | null>(
    null,
  );

  const [loading, setLoading] = useState(true);

  const [refreshing, setRefreshing] = useState(false);

  const [error, setError] = useState<string | null>(null);

  const [page, setPage] = useState(0);

  const [totalPages, setTotalPages] = useState(0);

  const [totalElements, setTotalElements] = useState(0);

  const [status, setStatus] = useState<"" | LoanStatus>("");

  const [type, setType] = useState<"" | LoanType>("");

  const [query, setQuery] = useState("");

  /* ==========================================================
     LOAD PORTFOLIO
     ========================================================== */

  const loadPortfolio = useCallback(async () => {
    const cacheKey = `/loans/list?page=${page}&size=${PAGE_SIZE}&status=${status}&type=${type}`;

    setError(null);

    try {
      const [listResponse, dashboardResponse] = await Promise.all([
        loanApi.list(page, PAGE_SIZE, status, type),

        page === 0 && !status && !type
          ? loanApi.dashboard()
          : Promise.resolve(null),
      ]);

      const pageLoans = getPageLoans(listResponse);

      const meta = getPageMeta(listResponse);

      setLoans(pageLoans);

      setTotalPages(meta.totalPages);

      setTotalElements(meta.totalElements || pageLoans.length);

      if (dashboardResponse) {
        setDashboard(dashboardResponse as LoanDashboardResponse);
      }

      await cacheSet(cacheKey, {
        loans: pageLoans,
        meta,
        dashboard: dashboardResponse ?? undefined,
      });
    } catch (requestError) {
      console.error("Failed to load loan portfolio", requestError);

      try {
        const cached = await cacheGet<{
          loans: Loan[];
          meta: ReturnType<typeof getPageMeta>;
          dashboard?: LoanDashboardResponse;
        }>(cacheKey);

        if (cached) {
          const cachedData = cached.data;

          setLoans(Array.isArray(cachedData.loans) ? cachedData.loans : []);

          setTotalPages(
            Number.isFinite(cachedData.meta?.totalPages)
              ? cachedData.meta.totalPages
              : 0,
          );

          setTotalElements(
            Number.isFinite(cachedData.meta?.totalElements)
              ? cachedData.meta.totalElements
              : Array.isArray(cachedData.loans)
                ? cachedData.loans.length
                : 0,
          );

          if (cachedData.dashboard) {
            setDashboard(cachedData.dashboard);
          }

          setError(
            online
              ? "Live portfolio data is temporarily unavailable. Showing the latest cached portfolio."
              : "You're offline. Showing the latest cached portfolio.",
          );
        } else {
          setLoans([]);

          setError(
            requestError instanceof Error
              ? requestError.message
              : "Unable to load the loan portfolio.",
          );
        }
      } catch (cacheError) {
        console.error("Failed to load cached loan portfolio", cacheError);

        setLoans([]);

        setError("Unable to load the loan portfolio.");
      }
    }
  }, [page, status, type, online]);

  /* ==========================================================
     INITIAL / FILTER LOAD
     ========================================================== */

  useEffect(() => {
    let mounted = true;

    const run = async () => {
      if (mounted) {
        setLoading(true);
      }

      await loadPortfolio();

      if (mounted) {
        setLoading(false);
      }
    };

    void run();

    return () => {
      mounted = false;
    };
  }, [loadPortfolio]);

  /* ==========================================================
     REFRESH
     ========================================================== */

  const handleRefresh = async () => {
    setRefreshing(true);

    await loadPortfolio();

    setRefreshing(false);
  };

  /* ==========================================================
     CLIENT-SIDE SEARCH
     ========================================================== */

  const visibleLoans = useMemo(() => {
    const needle = query.trim().toLowerCase();

    if (!needle) {
      return loans;
    }

    return loans.filter((loan) => {
      const haystack = [
        loan.referenceNumber,
        getBorrowerName(loan),
        loan.borrower?.nationalId,
        loan.borrower?.phone,
        loan.borrower?.email,
        loan.loanType,
        loan.status,
      ]
        .filter(Boolean)
        .join(" ")
        .toLowerCase();

      return haystack.includes(needle);
    });
  }, [loans, query]);

  /* ==========================================================
     SUMMARY
     ========================================================== */

  const summary = useMemo<Summary>(() => {
    const fallback = loans.reduce(
      (acc, loan) => {
        acc.totalLoans += 1;

        acc.disbursed += toNumber(loan.disbursedAmount);

        acc.outstanding += toNumber(loan.outstandingBalance);

        if (loan.status === "ACTIVE" || loan.status === "RESTRUCTURED") {
          acc.active += 1;
        }

        if (loan.status === "OVERDUE") {
          acc.overdue += 1;
        }

        if (loan.status === "PENDING" || loan.status === "UNDER_REVIEW") {
          acc.pending += 1;
        }

        return acc;
      },
      {
        totalLoans: 0,
        active: 0,
        overdue: 0,
        pending: 0,
        disbursed: 0,
        outstanding: 0,
      },
    );

    return {
      totalLoans:
        dashboard?.totalLoans != null
          ? toNumber(dashboard.totalLoans)
          : totalElements || fallback.totalLoans,

      active:
        dashboard?.activeLoans != null
          ? toNumber(dashboard.activeLoans)
          : fallback.active,

      overdue:
        dashboard?.overdueLoans != null
          ? toNumber(dashboard.overdueLoans)
          : fallback.overdue,

      pending:
        dashboard?.pendingLoans != null
          ? toNumber(dashboard.pendingLoans)
          : fallback.pending,

      disbursed:
        dashboard?.totalDisbursed != null
          ? toNumber(dashboard.totalDisbursed)
          : fallback.disbursed,

      outstanding:
        dashboard?.outstandingBalance != null
          ? toNumber(dashboard.outstandingBalance)
          : fallback.outstanding,
    };
  }, [dashboard, loans, totalElements]);

  /* ==========================================================
     FILTER RESET
     ========================================================== */

  const resetFilters = () => {
    setQuery("");
    setStatus("");
    setType("");
    setPage(0);
  };

  /* ==========================================================
     PAGINATION
     ========================================================== */

  const startItem = totalElements === 0 ? 0 : page * PAGE_SIZE + 1;

  const endItem = Math.min(
    totalElements,
    page * PAGE_SIZE + visibleLoans.length,
  );

  const hasPrevious = page > 0;

  const hasNext =
    totalPages > 0 ? page < totalPages - 1 : loans.length >= PAGE_SIZE;

  /* ==========================================================
     LOADING
     ========================================================== */

  if (loading) {
    return <PageSpinner />;
  }

  /* ==========================================================
     RENDER
     ========================================================== */

  return (
    <div className="min-h-full bg-[#F7F9FC] pb-12">
      <div className="mx-auto max-w-[1680px] space-y-6">
        {/* ====================================================
            EXECUTIVE HEADER
            ==================================================== */}

        <section
          className="
            relative
            overflow-hidden
            rounded-[28px]
            border
            border-slate-800
            bg-[#07152A]
            text-white
            shadow-[0_20px_60px_rgba(15,23,42,0.14)]
          "
        >
          {/* Decorative background */}

          <div className="pointer-events-none absolute -right-24 -top-28 h-72 w-72 rounded-full bg-teal-500/10" />

          <div className="pointer-events-none absolute -bottom-40 left-1/3 h-80 w-80 rounded-full bg-blue-500/5" />

          <div className="relative p-6 sm:p-8 lg:p-9">
            <div className="flex flex-col gap-7 lg:flex-row lg:items-end lg:justify-between">
              <div className="max-w-4xl">
                <div
                  className="
                    mb-4
                    inline-flex
                    items-center
                    gap-2
                    rounded-full
                    border
                    border-white/10
                    bg-white/[0.06]
                    px-3
                    py-1.5
                    text-[10px]
                    font-bold
                    uppercase
                    tracking-[0.18em]
                    text-teal-200
                  "
                >
                  <span className="h-1.5 w-1.5 rounded-full bg-teal-400 shadow-[0_0_8px_rgba(45,212,191,0.8)]" />
                  Lending Operations
                </div>

                <h1 className="text-3xl font-black tracking-tight sm:text-4xl">
                  Loan Portfolio
                </h1>

                <p className="mt-3 max-w-3xl text-sm leading-6 text-slate-300">
                  Monitor the institution's loan book, disbursements,
                  outstanding balances, repayment performance and collection
                  risk from one controlled operational view.
                </p>

                <div className="mt-4 flex flex-wrap items-center gap-x-5 gap-y-2 text-xs text-slate-400">
                  <span>
                    Portfolio:{" "}
                    <strong className="text-slate-200">
                      {summary.totalLoans.toLocaleString()}
                    </strong>{" "}
                    loans
                  </span>

                  <span className="hidden sm:inline text-slate-700">•</span>

                  <span>
                    {online ? "Live connection" : "Offline / cached data"}
                  </span>
                </div>
              </div>

              {/* Actions */}

              <div className="flex shrink-0 flex-wrap gap-2">
                <button
                  type="button"
                  onClick={() => void handleRefresh()}
                  disabled={refreshing}
                  className="
                    rounded-xl
                    border
                    border-white/10
                    bg-white/[0.06]
                    px-4
                    py-2.5
                    text-xs
                    font-bold
                    text-white
                    transition
                    hover:bg-white/[0.11]
                    disabled:cursor-not-allowed
                    disabled:opacity-50
                  "
                >
                  {refreshing ? "Refreshing…" : "Refresh portfolio"}
                </button>

                {isOfficer ? (
                  <Link
                    href="/dashboard/loans/new"
                    className="
                      rounded-xl
                      bg-teal-500
                      px-4
                      py-2.5
                      text-xs
                      font-black
                      text-slate-950
                      shadow-[0_8px_20px_rgba(20,184,166,0.2)]
                      transition
                      hover:bg-teal-400
                      hover:shadow-[0_10px_25px_rgba(20,184,166,0.3)]
                    "
                  >
                    + New loan
                  </Link>
                ) : null}
              </div>
            </div>

            {/* Header financial summary */}

            <div className="mt-8 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
              <div className="rounded-2xl border border-white/10 bg-white/[0.045] p-4">
                <div className="text-[10px] font-bold uppercase tracking-[0.16em] text-slate-500">
                  Total loans
                </div>

                <div className="mt-2 text-2xl font-black">
                  {summary.totalLoans.toLocaleString()}
                </div>

                <div className="mt-1 text-xs text-slate-500">
                  Current portfolio count
                </div>
              </div>

              <div className="rounded-2xl border border-white/10 bg-white/[0.045] p-4">
                <div className="text-[10px] font-bold uppercase tracking-[0.16em] text-slate-500">
                  Disbursed
                </div>

                <div className="mt-2 text-2xl font-black">
                  {formatCurrency(summary.disbursed, currency, locale)}
                </div>

                <div className="mt-1 text-xs text-slate-500">
                  Cash released to borrowers
                </div>
              </div>

              <div className="rounded-2xl border border-white/10 bg-white/[0.045] p-4">
                <div className="text-[10px] font-bold uppercase tracking-[0.16em] text-slate-500">
                  Outstanding
                </div>

                <div className="mt-2 text-2xl font-black">
                  {formatCurrency(summary.outstanding, currency, locale)}
                </div>

                <div className="mt-1 text-xs text-slate-500">
                  Current system-calculated balance
                </div>
              </div>

              <div className="rounded-2xl border border-white/10 bg-white/[0.045] p-4">
                <div className="text-[10px] font-bold uppercase tracking-[0.16em] text-slate-500">
                  Attention
                </div>

                <div className="mt-2 flex items-baseline gap-2">
                  <span className="text-2xl font-black">{summary.overdue}</span>

                  <span className="text-sm font-semibold text-red-300">
                    overdue
                  </span>
                </div>

                <div className="mt-1 text-xs text-slate-500">
                  {summary.active} active · {summary.pending} pending
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* ====================================================
            OFFLINE
            ==================================================== */}

        {!online ? (
          <div className="rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-950 shadow-sm">
            <div className="font-bold">Offline mode</div>

            <div className="mt-0.5 text-xs text-amber-800">
              The portfolio remains readable from the latest cached version.
              Server-dependent actions are not assumed to have completed.
            </div>
          </div>
        ) : null}

        {/* ====================================================
            ERROR / FALLBACK NOTICE
            ==================================================== */}

        {error ? (
          <div
            className={`
              rounded-2xl
              border
              px-4
              py-3
              text-sm
              shadow-sm
              ${
                error.startsWith("You're offline") || error.includes("cached")
                  ? "border-amber-200 bg-amber-50 text-amber-950"
                  : "border-red-200 bg-red-50 text-red-950"
              }
            `}
          >
            <div className="font-semibold">{error}</div>
          </div>
        ) : null}

        {/* ====================================================
            KPI GRID
            ==================================================== */}

        <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-6">
          <MetricCard
            label="Active"
            value={summary.active.toLocaleString()}
            description="Loans currently performing."
            icon={<IconCheckCircle className="h-5 w-5" />}
            tone="teal"
          />

          <MetricCard
            label="Overdue"
            value={summary.overdue.toLocaleString()}
            description="Loans requiring collections attention."
            icon={<IconAlertTriangle className="h-5 w-5" />}
            tone="red"
          />

          <MetricCard
            label="Pending"
            value={summary.pending.toLocaleString()}
            description="Applications awaiting action."
            icon={<IconClock className="h-5 w-5" />}
            tone="amber"
          />

          <MetricCard
            label="Disbursed"
            value={formatCurrency(summary.disbursed, currency, locale)}
            description="Total cash released."
            icon={<IconSend className="h-5 w-5" />}
            tone="blue"
          />

          <MetricCard
            label="Outstanding"
            value={formatCurrency(summary.outstanding, currency, locale)}
            description="Current system-calculated balance."
            icon={<IconCoins className="h-5 w-5" />}
            tone="violet"
          />

          <MetricCard
            label="Portfolio size"
            value={summary.totalLoans.toLocaleString()}
            description="Loans in the current portfolio."
            icon={<IconFileText className="h-5 w-5" />}
            tone="slate"
          />
        </section>

        {/* ====================================================
            SEARCH / FILTER PANEL
            ==================================================== */}

        <section className="overflow-hidden rounded-3xl border border-slate-200/80 bg-white shadow-[0_3px_15px_rgba(15,23,42,0.04)]">
          <div className="border-b border-slate-100 p-5 sm:p-6">
            <div className="flex flex-col gap-5 xl:flex-row xl:items-end xl:justify-between">
              <div>
                <div className="flex items-center gap-2">
                  <h2 className="text-base font-bold text-slate-950">
                    Loan register
                  </h2>

                  <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-bold text-slate-500">
                    {totalElements.toLocaleString()}
                  </span>
                </div>

                <p className="mt-1 text-xs text-slate-400">
                  Search the loaded page and narrow the server-side portfolio by
                  status or loan type.
                </p>
              </div>

              <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
                {/* Search */}

                <div className="relative min-w-[260px]">
                  <IconSearch
                    className="
                      pointer-events-none
                      absolute
                      left-3
                      top-1/2
                      h-4
                      w-4
                      -translate-y-1/2
                      text-slate-400
                    "
                  />

                  <input
                    value={query}
                    onChange={(event) => setQuery(event.target.value)}
                    placeholder="Reference, borrower, ID…"
                    className="
                      h-10
                      w-full
                      rounded-xl
                      border
                      border-slate-200
                      bg-slate-50
                      pl-9
                      pr-3
                      text-sm
                      text-slate-900
                      outline-none
                      transition
                      placeholder:text-slate-400
                      focus:border-teal-500
                      focus:bg-white
                      focus:ring-4
                      focus:ring-teal-500/10
                    "
                  />
                </div>

                {/* Status */}

                <select
                  value={status}
                  onChange={(event) => {
                    setStatus(event.target.value as "" | LoanStatus);

                    setPage(0);
                  }}
                  className="
                    h-10
                    rounded-xl
                    border
                    border-slate-200
                    bg-white
                    px-3
                    text-sm
                    text-slate-700
                    outline-none
                    transition
                    focus:border-teal-500
                    focus:ring-4
                    focus:ring-teal-500/10
                  "
                >
                  {STATUS_OPTIONS.map((item) => (
                    <option key={item.value} value={item.value}>
                      {item.label}
                    </option>
                  ))}
                </select>

                {/* Loan type */}

                <select
                  value={type}
                  onChange={(event) => {
                    setType(event.target.value as "" | LoanType);

                    setPage(0);
                  }}
                  className="
                    h-10
                    rounded-xl
                    border
                    border-slate-200
                    bg-white
                    px-3
                    text-sm
                    text-slate-700
                    outline-none
                    transition
                    focus:border-teal-500
                    focus:ring-4
                    focus:ring-teal-500/10
                  "
                >
                  {TYPE_OPTIONS.map((item) => (
                    <option key={item.value} value={item.value}>
                      {item.label}
                    </option>
                  ))}
                </select>

                {/* Reset */}

                <button
                  type="button"
                  onClick={resetFilters}
                  className="
                    h-10
                    rounded-xl
                    border
                    border-slate-200
                    bg-white
                    px-3
                    text-xs
                    font-bold
                    text-slate-600
                    transition
                    hover:bg-slate-50
                    hover:text-slate-900
                  "
                >
                  Reset
                </button>
              </div>
            </div>
          </div>

          {/* ==================================================
              TABLE
              ================================================== */}

          <div className="overflow-x-auto">
            <table className="min-w-[1280px] w-full text-sm">
              <thead className="border-b border-slate-100 bg-slate-50/80 text-left">
                <tr>
                  {[
                    "Loan",
                    "Borrower",
                    "Status & risk",
                    "Original principal",
                    "Disbursed",
                    "Outstanding",
                    "Type / repayment",
                  ].map((label) => (
                    <th
                      key={label}
                      scope="col"
                      className="
                        whitespace-nowrap
                        px-5
                        py-3
                        text-[10px]
                        font-bold
                        uppercase
                        tracking-[0.14em]
                        text-slate-400
                      "
                    >
                      {label}
                    </th>
                  ))}
                </tr>
              </thead>

              <tbody>
                {visibleLoans.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="p-0">
                      <EmptyState
                        searching={Boolean(query || status || type)}
                      />
                    </td>
                  </tr>
                ) : (
                  visibleLoans.map((loan) => (
                    <LoanRow
                      key={loan.id}
                      loan={loan}
                      currency={currency}
                      locale={locale}
                    />
                  ))
                )}
              </tbody>
            </table>
          </div>

          {/* ==================================================
              PAGINATION
              ================================================== */}

          <div className="flex flex-col gap-3 border-t border-slate-100 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
            <div className="text-xs text-slate-400">
              Showing{" "}
              <span className="font-bold text-slate-700">{startItem}</span> –{" "}
              <span className="font-bold text-slate-700">{endItem}</span> of{" "}
              <span className="font-bold text-slate-700">{totalElements}</span>{" "}
              loans
            </div>

            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => setPage((current) => Math.max(0, current - 1))}
                disabled={!hasPrevious}
                className="
                  rounded-xl
                  border
                  border-slate-200
                  bg-white
                  px-3
                  py-2
                  text-xs
                  font-bold
                  text-slate-700
                  transition
                  hover:bg-slate-50
                  disabled:cursor-not-allowed
                  disabled:opacity-40
                "
              >
                Previous
              </button>

              <div className="rounded-xl bg-slate-950 px-3 py-2 text-xs font-bold text-white">
                Page {page + 1}
                {totalPages ? ` of ${totalPages}` : ""}
              </div>

              <button
                type="button"
                onClick={() => setPage((current) => current + 1)}
                disabled={!hasNext}
                className="
                  rounded-xl
                  border
                  border-slate-200
                  bg-white
                  px-3
                  py-2
                  text-xs
                  font-bold
                  text-slate-700
                  transition
                  hover:bg-slate-50
                  disabled:cursor-not-allowed
                  disabled:opacity-40
                "
              >
                Next
              </button>
            </div>
          </div>
        </section>

        {/* ====================================================
            FINANCIAL DEFINITIONS
            ==================================================== */}

        <section>
          <div className="mb-3">
            <h2 className="text-sm font-bold uppercase tracking-[0.14em] text-slate-700">
              Portfolio definitions
            </h2>

            <p className="mt-1 text-xs text-slate-400">
              These definitions keep operational figures clearly separated from
              accounting and journal balances.
            </p>
          </div>

          <div className="grid gap-4 lg:grid-cols-3">
            <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition hover:shadow-md">
              <div className="flex items-center gap-3">
                <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-blue-50 text-blue-700">
                  <IconCard className="h-5 w-5" />
                </div>

                <div>
                  <div className="text-sm font-bold text-slate-900">
                    Original principal
                  </div>

                  <div className="mt-0.5 text-xs leading-5 text-slate-400">
                    The principal amount recorded for the loan.
                  </div>
                </div>
              </div>
            </div>

            <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition hover:shadow-md">
              <div className="flex items-center gap-3">
                <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-violet-50 text-violet-700">
                  <IconSend className="h-5 w-5" />
                </div>

                <div>
                  <div className="text-sm font-bold text-slate-900">
                    Disbursed
                  </div>

                  <div className="mt-0.5 text-xs leading-5 text-slate-400">
                    The amount actually released to the borrower.
                  </div>
                </div>
              </div>
            </div>

            <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition hover:shadow-md">
              <div className="flex items-center gap-3">
                <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-teal-50 text-teal-700">
                  <IconCoins className="h-5 w-5" />
                </div>

                <div>
                  <div className="text-sm font-bold text-slate-900">
                    Outstanding balance
                  </div>

                  <div className="mt-0.5 text-xs leading-5 text-slate-400">
                    The current balance supplied by the lending system; interest
                    and fees must follow the institution's configured financial
                    rules.
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* ====================================================
            FOOTER
            ==================================================== */}

        <footer className="flex flex-col gap-2 border-t border-slate-200 pt-5 text-xs text-slate-400 sm:flex-row sm:items-center sm:justify-between">
          <span>Noble Loan Solutions · Loan Portfolio</span>

          <span>
            Operational portfolio figures are sourced from the lending system.
          </span>
        </footer>
      </div>
    </div>
  );
}
