"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";

import { loanApi } from "@/services/api";
import { Loan, LoanStatus, LoanType } from "@/types";
import { formatCurrency, formatDate, LOAN_TYPE_META } from "@/lib/utils";
import { useAuth } from "@/hooks/useAuth";
import { useOnlineStatus } from "@/hooks/useOnlineStatus";

import { StatusBadge, RiskBadge, Pill } from "@/components/ui/Badge";

import { PageSpinner } from "@/components/ui/Skeleton";

import {
  IconAlertTriangle,
  IconSearch,
} from "@/components/ui/Icons";

/* ============================================================
   CONFIGURATION
   ============================================================ */

const PAGE_SIZE = 25;
type PortfolioVisibility = "NORMAL" | "BUSINESS_OWNER_ONLY" | "ALL";

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
  historicalCollected?: number | string;
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
  collected: number;
  historicalCollected: number;
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
  const nestedName = `${first} ${last}`.trim();

  return nestedName || loan.borrowerName?.trim() || "Unnamed borrower";
};

const getBorrowerInitials = (loan: Loan): string => {
  const first = loan.borrower?.firstName?.trim()?.charAt(0) ?? "";
  const last = loan.borrower?.lastName?.trim()?.charAt(0) ?? "";

  if (first || last) {
    return `${first}${last}`.toUpperCase();
  }

  const name = loan.borrowerName?.trim() ?? "";
  const parts = name.split(/\s+/).filter(Boolean);

  if (parts.length >= 2) {
    return `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase();
  }

  return parts[0]?.slice(0, 2).toUpperCase() || "BR";
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

function PortfolioMetric({ label, value, description }: {
  label: string;
  value: string;
  description?: string;
}) {
  return (
    <div className="min-w-0 rounded-xl border border-slate-200 bg-white p-4 sm:p-5">
      <dt className="text-sm font-medium text-slate-600">{label}</dt>
      <dd data-portfolio-metric className="mt-2 text-xl font-semibold tabular-nums tracking-tight text-[#0B1F3A] [overflow-wrap:anywhere] sm:text-2xl">
        {value}
      </dd>
      {description && <dd className="mt-2 text-xs leading-5 text-slate-600 [overflow-wrap:anywhere]">{description}</dd>}
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
          ? "Try a loan ID, reference number, borrower name, phone number, email or national ID."
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
  canDelete,
  onDelete,
  onVisibility,
  visibilityUpdating,
}: {
  loan: Loan;
  currency: string;
  locale: string;
  canDelete: boolean;
  onDelete: (loan: Loan) => void;
  onVisibility: (loan: Loan) => void;
  visibilityUpdating: boolean;
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
                (loan.borrowerId
                  ? `Borrower #${loan.borrowerId}`
                  : "No borrower identifier")}
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

          <div className="mt-1 text-[11px] text-slate-500">
            Principal balance (excludes accrued interest and fees)
          </div>
          <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-slate-100">
            <div
              className="h-full rounded-full bg-teal-600 transition-all duration-500"
              style={{
                width: `${progress}%`,
              }}
            />
          </div>

          <div className="mt-1 text-xs font-semibold text-teal-800">
            Paid: {formatCurrency(totalPaid, currency, locale)}
          </div>
          <div className="mt-1 text-[11px] text-slate-500">
            Principal{" "}
            {formatCurrency(toNumber(loan.principalPaid), currency, locale)} ·
            Interest{" "}
            {formatCurrency(toNumber(loan.interestPaid), currency, locale)}
          </div>
          <div className="mt-1 text-[11px] text-slate-500">
            Management fees paid:{" "}
            {formatCurrency(toNumber(loan.managementFeePaid), currency, locale)}
            {loan.imported ? " · Historical opening amounts included" : ""}
          </div>
          <div className="mt-1 text-[11px] text-slate-400">
            {progress}% collected · Principal balance shown above
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

        {loan.imported &&
        loan.nextDueDate &&
        loan.startDate &&
        loan.nextDueDate <= loan.startDate ? (
          <div
            className="mt-2 text-xs font-semibold text-amber-700"
            title="Review the oldest unpaid contractual installment date before relying on days past due"
          >
            Review due date — cannot measure arrears from disbursement
          </div>
        ) : overdue > 0 ? (
          <div className="mt-2 flex items-center justify-end gap-1 text-xs font-bold text-red-600">
            <IconAlertTriangle className="h-3.5 w-3.5" />
            {overdue} day
            {overdue === 1 ? "" : "s"} overdue
            {loan.imported && loan.nextDueDate && (
              <span className="font-normal text-slate-500">
                (from {formatDate(loan.nextDueDate, locale)})
              </span>
            )}
          </div>
        ) : loan.nextDueDate ? (
          <div className="mt-2 text-xs text-slate-400">
            Next due {formatDate(loan.nextDueDate, locale)}
          </div>
        ) : null}
      </td>

      {canDelete ? (
        <td className="px-5 py-4 align-top">
          <div className="mb-2 text-[11px] font-semibold text-slate-600">
            {loan.businessOwnerOnly
              ? "Business Owner only"
              : "Normal · everyone authorized"}
          </div>
          <button
            type="button"
            disabled={visibilityUpdating}
            onClick={() => onVisibility(loan)}
            className="mb-2 block rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-xs font-bold text-amber-900 hover:bg-amber-100 disabled:opacity-50"
          >
            {visibilityUpdating
              ? "Saving…"
              : loan.businessOwnerOnly
                ? "Make normal"
                : "Owner only"}
          </button>
          <button
            type="button"
            onClick={() => onDelete(loan)}
            className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-xs font-bold text-red-700 hover:bg-red-100"
          >
            Delete
          </button>
        </td>
      ) : null}
    </tr>
  );
}

/* ============================================================
   MAIN PAGE
   ============================================================ */

export default function LoanListPage() {
  const { currency, locale, isOfficer, isBusinessOwner } = useAuth();

  const online = useOnlineStatus();

  const [loans, setLoans] = useState<Loan[]>([]);

  const [dashboard, setDashboard] = useState<LoanDashboardResponse | null>(
    null,
  );

  const [loading, setLoading] = useState(true);
  const [initialLoadFinished, setInitialLoadFinished] = useState(false);

  const [refreshing, setRefreshing] = useState(false);
  const [visibilityUpdatingId, setVisibilityUpdatingId] = useState<
    number | null
  >(null);

  const [deleteLoan, setDeleteLoan] = useState<Loan | null>(null);
  const [deleteConfirmation, setDeleteConfirmation] = useState("");
  const [deleteReason, setDeleteReason] = useState("");
  const [deletingLoan, setDeletingLoan] = useState(false);

  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const [page, setPage] = useState(0);

  const [totalPages, setTotalPages] = useState(0);

  const [totalElements, setTotalElements] = useState(0);

  const [status, setStatus] = useState<"" | LoanStatus>("");

  const [type, setType] = useState<"" | LoanType>("");

  // Start in the ordinary portfolio even for the Business Owner. The
  // confidential register requires an explicit owner-only selection.
  const [visibility, setVisibility] = useState<PortfolioVisibility>("NORMAL");

  const [query, setQuery] = useState("");

  const [debouncedQuery, setDebouncedQuery] = useState("");
  const latestPortfolioRequest = useRef(0);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedQuery(query.trim());
      setPage(0);
    }, 300);

    return () => window.clearTimeout(timer);
  }, [query]);

  /* ==========================================================
     LOAD PORTFOLIO
     ========================================================== */

  const loadPortfolio = useCallback(async () => {
    const requestId = ++latestPortfolioRequest.current;
    setError(null);
    // Confidential loan records must not be rendered from a stale browser
    // cache after a change of access or reporting visibility.
    try {
      const [listResponse, dashboardResponse] = await Promise.all([
        loanApi.list(page, PAGE_SIZE, status, type, debouncedQuery, visibility),
        // Dashboard totals are cross-portfolio for the Business Owner. Never
        // present those as scoped totals for Normal or Owner-only registers.
        page === 0 &&
        !status &&
        !type &&
        !debouncedQuery &&
        (!isBusinessOwner || visibility === "ALL")
          ? loanApi.dashboard()
          : Promise.resolve(null),
      ]);
      if (requestId !== latestPortfolioRequest.current) return;
      const pageLoans = getPageLoans(listResponse);
      const meta = getPageMeta(listResponse);
      setLoans(pageLoans);
      setTotalPages(meta.totalPages);
      setTotalElements(meta.totalElements || pageLoans.length);
      setDashboard(dashboardResponse as LoanDashboardResponse | null);
    } catch (requestError) {
      if (requestId !== latestPortfolioRequest.current) return;
      // Fail closed: an old cached list may contain recently reclassified
      // confidential loans, or data from another authorized session.
      setLoans([]);
      setTotalPages(0);
      setTotalElements(0);
      setDashboard(null);
      setError(
        requestError instanceof Error
          ? requestError.message
          : "Unable to load live loan portfolio. Reconnect and refresh.",
      );
    }
  }, [page, status, type, debouncedQuery, visibility, isBusinessOwner]);

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
        setInitialLoadFinished(true);
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

  const handleVisibilityChange = async (loan: Loan) => {
    if (!isBusinessOwner || visibilityUpdatingId !== null) return;
    const desired = !Boolean(loan.businessOwnerOnly);
    const label = desired
      ? "Business Owner only"
      : "Normal reporting (all authorized staff)";
    if (
      !window.confirm(
        `Change ${loan.referenceNumber} to ${label}? This changes visibility and report scope, not the loan balance. The action is audited.`,
      )
    )
      return;
    setVisibilityUpdatingId(loan.id);
    setNotice(null);
    setError(null);
    try {
      await loanApi.changeReportingVisibility(loan.id, desired);
      // Requery the server for the selected scope; a newly confidential loan
      // must disappear from NORMAL, and vice versa for OWNER_ONLY.
      if (page !== 0) setPage(0);
      else await loadPortfolio();
      setNotice(
        desired
          ? `${loan.referenceNumber} moved to Business Owner only. It is no longer in Normal loans; find it under Business Owner only or All loans.`
          : `${loan.referenceNumber} moved to Normal loans. It is no longer in Business Owner only.`,
      );
    } catch (e) {
      setError(
        e instanceof Error
          ? e.message
          : "Unable to change reporting visibility",
      );
    } finally {
      setVisibilityUpdatingId(null);
    }
  };

  const handleDeleteLoan = async () => {
    if (!deleteLoan) return;

    const expected = `sudo ${deleteLoan.referenceNumber ?? ""}`;
    if (deleteConfirmation !== expected) {
      setError(`Type exactly: ${expected}`);
      return;
    }

    if (deleteReason.trim().length < 20) {
      setError("Enter a deletion reason of at least 20 characters.");
      return;
    }

    setDeletingLoan(true);
    try {
      await loanApi.deleteWithConfirmation(
        deleteLoan.id,
        deleteConfirmation,
        deleteReason.trim(),
      );
      setDeleteLoan(null);
      setDeleteConfirmation("");
      setDeleteReason("");
      await loadPortfolio();
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : "Unable to recycle the loan.",
      );
    } finally {
      setDeletingLoan(false);
    }
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
        loan.id,
        loan.referenceNumber,
        getBorrowerName(loan),
        loan.borrower?.nationalId,
        loan.borrower?.phone,
        loan.borrower?.email,
        loan.loanType,
        loan.status,
      ]
        .filter((value) => value !== undefined && value !== null)
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

        acc.collected += toNumber(loan.totalPaid);

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
        collected: 0,
        historicalCollected: 0,
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

      collected:
        dashboard?.totalCollected != null
          ? toNumber(dashboard.totalCollected)
          : fallback.collected,

      historicalCollected:
        dashboard?.historicalCollected != null
          ? toNumber(dashboard.historicalCollected)
          : 0,

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

  const isScopedPageSummary =
    (isBusinessOwner && visibility !== "ALL") ||
    page !== 0 ||
    Boolean(status || type || debouncedQuery);

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

  if (loading && !initialLoadFinished) {
    return <PageSpinner />;
  }

  /* ==========================================================
     RENDER
     ========================================================== */

  return (
    <div className="min-h-full bg-[#F7F9FC] pb-12">
      <div className="mx-auto max-w-[1680px] space-y-6">
        <header className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div className="min-w-0">
            <h1 className="text-2xl font-semibold tracking-tight text-[#0B1F3A] sm:text-3xl">
              Loan Portfolio
            </h1>
            {isBusinessOwner && (
              <p className="mt-1 text-sm text-slate-600">
                {visibility === "NORMAL"
                  ? "Normal portfolio · Excludes Business Owner-only loans"
                  : visibility === "BUSINESS_OWNER_ONLY"
                    ? "Confidential portfolio · Business Owner-only loans"
                    : "All loans · Normal and confidential portfolios"}
              </p>
            )}
          </div>
          <div className="flex flex-wrap items-center gap-2">
            {isBusinessOwner && (
              <Link href="/dashboard/loans/recycle-bin" className="inline-flex min-h-10 items-center rounded-lg border border-slate-200 bg-white px-3 text-sm font-medium text-slate-700 hover:bg-slate-100">
                Recycle bin
              </Link>
            )}
            <button
              type="button"
              onClick={() => void handleRefresh()}
              disabled={refreshing}
              className="inline-flex min-h-10 items-center rounded-lg border border-slate-200 bg-white px-3 text-sm font-medium text-slate-700 hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {refreshing ? "Refreshing…" : "Refresh portfolio"}
            </button>
            {isOfficer && (
              <Link href="/dashboard/loans/new" className="inline-flex min-h-10 items-center rounded-lg bg-[#0B1F3A] px-4 text-sm font-semibold text-white hover:bg-[#16365F]">
                + New loan
              </Link>
            )}
          </div>
        </header>

        {/* ====================================================
            OFFLINE
            ==================================================== */}

        {!online ? (
          <div className="rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-950 shadow-sm">
            <div className="font-bold">Offline mode</div>

            <div className="mt-0.5 text-xs text-amber-800">
              Loan records require live verification of your permissions. A
              cached loan register is intentionally not displayed.
            </div>
          </div>
        ) : null}

        {notice && (
          <div
            role="status"
            className="rounded-2xl border border-teal-200 bg-teal-50 px-4 py-3 text-sm font-semibold text-teal-900"
          >
            {notice}
          </div>
        )}

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

        <section aria-labelledby="portfolio-summary" className="space-y-3">
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <h2 id="portfolio-summary" className="text-sm font-semibold text-slate-900">
              Portfolio summary
            </h2>
            {isScopedPageSummary && <span className="text-xs text-slate-600">Amounts and status counts cover this page</span>}
          </div>
          <dl className="grid grid-cols-[repeat(auto-fit,minmax(min(100%,14rem),1fr))] gap-3">
            <PortfolioMetric label={isScopedPageSummary ? "Matching loans" : "Total loans"} value={summary.totalLoans.toLocaleString(locale)} />
            <PortfolioMetric label="Disbursed" value={formatCurrency(summary.disbursed, currency, locale)} />
            <PortfolioMetric
              label="Collected"
              value={formatCurrency(summary.collected, currency, locale)}
              description={dashboard?.historicalCollected != null ? `Includes ${formatCurrency(summary.historicalCollected, currency, locale)} in legacy collections` : undefined}
            />
            <PortfolioMetric label="Outstanding" value={formatCurrency(summary.outstanding, currency, locale)} />
          </dl>
          <dl className="flex flex-wrap gap-x-6 gap-y-2 rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm">
            <div className="flex items-baseline gap-2">
              <dt className="text-slate-600">Active</dt><dd className="font-semibold tabular-nums text-slate-900">{summary.active.toLocaleString(locale)}</dd>
            </div>
            <div className="flex items-baseline gap-2">
              <dt className="text-slate-600">Overdue</dt><dd className="font-semibold tabular-nums text-red-700">{summary.overdue.toLocaleString(locale)}</dd>
            </div>
            <div className="flex items-baseline gap-2">
              <dt className="text-slate-600">Pending</dt><dd className="font-semibold tabular-nums text-slate-900">{summary.pending.toLocaleString(locale)}</dd>
            </div>
          </dl>
        </section>

        {/* ====================================================
            SEARCH / FILTER PANEL
            ==================================================== */}

        <section className="overflow-hidden rounded-xl border border-slate-200 bg-white">
          <div className="border-b border-slate-100 p-5 sm:p-6">
            <div className="flex flex-col gap-5 xl:flex-row xl:items-end xl:justify-between">
              <div>
                <div className="flex items-center gap-2">
                  <h2 className="text-base font-bold text-slate-950">
                    {visibility === "BUSINESS_OWNER_ONLY"
                      ? "Business Owner-only loans"
                      : visibility === "ALL"
                        ? "All loans"
                        : "Normal loans"}
                  </h2>

                  <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-bold text-slate-500">
                    {totalElements.toLocaleString()}
                  </span>
                </div>

                <p className="mt-1 text-xs text-slate-400">
                  Search by loan ID, reference, borrower name, national ID,
                  phone, email, loan type or status.
                </p>
              </div>

              <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
                {isBusinessOwner && (
                  <select
                    aria-label="Loan visibility register"
                    value={visibility}
                    onChange={(event) => {
                      setVisibility(event.target.value as PortfolioVisibility);
                      setNotice(null);
                      setPage(0);
                      setLoans([]);
                      setDashboard(null);
                    }}
                    className="h-10 rounded-xl border border-amber-300 bg-amber-50 px-3 text-sm font-bold text-amber-950"
                  >
                    <option value="NORMAL">Normal loans</option>
                    <option value="BUSINESS_OWNER_ONLY">
                      Business Owner only
                    </option>
                    <option value="ALL">All loans (owner view)</option>
                  </select>
                )}
                {/* Search */}

                <div className="relative min-w-0 sm:min-w-[230px]">
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
                    aria-label="Search loans"
                    placeholder="Loan reference or borrower name…"
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
                  aria-label="Loan status"
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
                  aria-label="Loan type"
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

          <div role="region" aria-label="Loan table" tabIndex={0} className="overflow-x-auto focus-visible:outline focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-[#0B1F3A]">
            <table className="min-w-[1280px] w-full text-sm">
              <thead className="border-b border-slate-100 bg-slate-50/80 text-left">
                <tr>
                  {[
                    "Loan",
                    "Borrower",
                    "Status & risk",
                    "Original principal",
                    "Disbursed",
                    "Principal outstanding / payments",
                    "Type / repayment",
                    ...(isBusinessOwner ? ["Actions"] : []),
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
                    <td colSpan={isBusinessOwner ? 8 : 7} className="p-0">
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
                      canDelete={isBusinessOwner}
                      onDelete={setDeleteLoan}
                      onVisibility={handleVisibilityChange}
                      visibilityUpdating={visibilityUpdatingId === loan.id}
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

        {deleteLoan ? (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-4">
            <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-2xl">
              <h2 className="text-lg font-black text-slate-900">
                Delete loan — high-risk confirmation
              </h2>
              <p className="mt-2 text-sm leading-6 text-slate-500">
                This action is restricted to the Business Owner. To prevent
                accidental deletion, type exactly:
              </p>
              <code className="mt-3 block rounded-lg bg-slate-100 px-3 py-2 text-sm font-bold text-slate-900">
                sudo {deleteLoan.referenceNumber}
              </code>
              <input
                value={deleteConfirmation}
                onChange={(event) => setDeleteConfirmation(event.target.value)}
                placeholder={`sudo ${deleteLoan.referenceNumber}`}
                autoComplete="off"
                className="mt-4 w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm outline-none focus:border-red-400 focus:ring-2 focus:ring-red-100"
              />
              <textarea
                value={deleteReason}
                onChange={(event) => setDeleteReason(event.target.value)}
                placeholder="Reason for deleting/recycling this loan (minimum 20 characters)"
                rows={3}
                className="mt-3 w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm outline-none focus:border-red-400 focus:ring-2 focus:ring-red-100"
              />
              <p className="mt-1 text-xs text-slate-500">
                {deleteReason.trim().length}/20 minimum characters
              </p>
              <p className="mt-2 text-xs leading-5 text-red-600">
                The loan is moved to the recycle bin, removed from normal
                accounting/reporting/BNR/Credit Bureau visibility, and remains
                restorable for 30 days. Financial formulas and original values
                are not recalculated.
              </p>
              <div className="mt-5 flex justify-end gap-2">
                <button
                  type="button"
                  onClick={() => {
                    setDeleteLoan(null);
                    setDeleteConfirmation("");
                    setDeleteReason("");
                  }}
                  className="rounded-xl border border-slate-200 px-4 py-2 text-sm font-bold text-slate-700"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  disabled={
                    deletingLoan ||
                    deleteConfirmation !==
                      `sudo ${deleteLoan.referenceNumber}` ||
                    deleteReason.trim().length < 20
                  }
                  onClick={() => void handleDeleteLoan()}
                  className="rounded-xl bg-red-600 px-4 py-2 text-sm font-black text-white disabled:cursor-not-allowed disabled:opacity-40"
                >
                  {deletingLoan ? "Deleting…" : "Delete loan"}
                </button>
              </div>
            </div>
          </div>
        ) : null}

        <details className="rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm">
          <summary className="cursor-pointer font-medium text-slate-700">Portfolio definitions</summary>
          <dl className="mt-3 grid gap-3 text-slate-600 sm:grid-cols-3">
            <div><dt className="font-medium text-slate-900">Original principal</dt><dd>Principal recorded for the loan.</dd></div>
            <div><dt className="font-medium text-slate-900">Disbursed</dt><dd>Cash released to the borrower.</dd></div>
            <div><dt className="font-medium text-slate-900">Outstanding</dt><dd>Current balance calculated by the lending system.</dd></div>
          </dl>
        </details>
      </div>
    </div>
  );
}
