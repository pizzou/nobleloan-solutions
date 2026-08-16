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
import { Button } from "@/components/ui/Button";
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

const PAGE_SIZE = 25;

const STATUS_OPTIONS: Array<{ value: "" | LoanStatus; label: string }> = [
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

const TYPE_OPTIONS: Array<{ value: "" | LoanType; label: string }> = [
  { value: "", label: "All loan types" },
  ...Object.entries(LOAN_TYPE_META).map(([value, meta]) => ({
    value: value as LoanType,
    label: meta.label,
  })),
];

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

const toNumber = (value: unknown): number => {
  const result = Number(value);
  return Number.isFinite(result) ? result : 0;
};

const humanize = (value?: string): string =>
  value ? value.replace(/_/g, " ") : "—";

const getBorrowerName = (loan: Loan): string => {
  const first = loan.borrower?.firstName?.trim() ?? "";
  const last = loan.borrower?.lastName?.trim() ?? "";

  return `${first} ${last}`.trim() || "Unnamed borrower";
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
    blue: "border-blue-100 bg-blue-50/60 text-blue-700",
    teal: "border-teal-100 bg-teal-50/60 text-teal-700",
    amber: "border-amber-100 bg-amber-50/60 text-amber-700",
    red: "border-red-100 bg-red-50/60 text-red-700",
    violet: "border-violet-100 bg-violet-50/60 text-violet-700",
    slate: "border-slate-200 bg-slate-50 text-slate-700",
  };

  return (
    <div className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm transition hover:-translate-y-0.5 hover:shadow-md">
      <div className="flex items-start justify-between gap-4">
        <div>
          <p className="text-[10px] font-bold uppercase tracking-[0.16em] text-slate-400">
            {label}
          </p>
          <p className="mt-2 text-2xl font-black tracking-tight text-slate-950">
            {value}
          </p>
          <p className="mt-1 text-xs leading-5 text-slate-500">{description}</p>
        </div>
        <div
          className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border ${tones[tone]}`}
        >
          {icon}
        </div>
      </div>
    </div>
  );
}

function EmptyState({ searching }: { searching: boolean }) {
  return (
    <div className="flex flex-col items-center justify-center px-6 py-16 text-center">
      <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-slate-100 text-slate-400">
        <IconSearch className="h-6 w-6" />
      </div>
      <h3 className="mt-4 text-sm font-bold text-slate-900">
        {searching ? "No matching loans" : "No loans in this portfolio"}
      </h3>
      <p className="mt-1 max-w-md text-sm leading-6 text-slate-500">
        {searching
          ? "Try a different reference number, borrower name or national ID."
          : "Once loans are created or imported, they will appear here with their current financial position."}
      </p>
    </div>
  );
}

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
  const loanTypeLabel = getLoanTypeLabel(loan);
  const outstanding = loan.outstandingBalance ?? 0;
  const totalPaid = loan.totalPaid ?? 0;
  const repayable = loan.totalRepayable ?? 0;
  const progress =
    repayable > 0
      ? Math.min(100, Math.round((totalPaid / repayable) * 100))
      : 0;

  return (
    <tr className="group border-b border-slate-100 last:border-0 hover:bg-slate-50/80">
      <td className="px-4 py-4 align-top">
        <Link
          href={`/dashboard/loans/${loan.id}`}
          className="block min-w-[180px]"
        >
          <div className="font-bold text-slate-900 transition group-hover:text-teal-700">
            {loan.referenceNumber}
          </div>
          <div className="mt-1 text-[11px] text-slate-400">
            {loan.disbursedAt
              ? `Disbursed ${formatDate(loan.disbursedAt, locale)}`
              : loan.createdAt
                ? `Created ${formatDate(loan.createdAt, locale)}`
                : "—"}
          </div>
        </Link>
      </td>

      <td className="px-4 py-4 align-top">
        <div className="min-w-[170px]">
          <div className="font-semibold text-slate-800">{borrowerName}</div>
          <div className="mt-1 text-xs text-slate-400">
            {loan.borrower?.nationalId ??
              loan.borrower?.phone ??
              "No identifier"}
          </div>
        </div>
      </td>

      <td className="px-4 py-4 align-top">
        <div className="flex min-w-[160px] flex-wrap gap-1.5">
          <StatusBadge status={loan.status} />
          {loan.riskCategory ? (
            <RiskBadge category={loan.riskCategory} score={loan.riskScore} />
          ) : null}
        </div>
      </td>

      <td className="px-4 py-4 align-top">
        <div className="min-w-[130px]">
          <div className="font-semibold text-slate-900">
            {formatCurrency(loan.amount, currency, locale)}
          </div>
          <div className="mt-1 text-xs text-slate-400">
            {loan.durationMonths} mo · {loan.repaymentFrequency.toLowerCase()}
          </div>
        </div>
      </td>

      <td className="px-4 py-4 align-top">
        <div className="min-w-[150px]">
          <div className="font-semibold text-slate-900">
            {formatCurrency(loan.disbursedAmount ?? 0, currency, locale)}
          </div>
          <div className="mt-1 text-xs text-slate-400">Cash released</div>
        </div>
      </td>

      <td className="px-4 py-4 align-top">
        <div className="min-w-[150px]">
          <div className="font-semibold text-slate-900">
            {formatCurrency(outstanding, currency, locale)}
          </div>
          <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-slate-100">
            <div
              className="h-full rounded-full bg-teal-600 transition-all"
              style={{ width: `${progress}%` }}
            />
          </div>
          <div className="mt-1 text-[11px] text-slate-400">
            {progress}% repaid · {formatCurrency(totalPaid, currency, locale)}{" "}
            collected
          </div>
        </div>
      </td>

      <td className="px-4 py-4 align-top text-right">
        <div className="flex min-w-[120px] justify-end">
          <Pill label={loanTypeLabel} color="blue" />
        </div>
        {loan.daysOverdue && loan.daysOverdue > 0 ? (
          <div className="mt-2 flex items-center justify-end gap-1 text-xs font-semibold text-red-600">
            <IconAlertTriangle className="h-3.5 w-3.5" />
            {loan.daysOverdue}d overdue
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
        setDashboard(
          (dashboardResponse ?? null) as LoanDashboardResponse | null,
        );
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
          setLoans(Array.isArray(cached.loans) ? cached.loans : []);
          setTotalPages(cached.meta.totalPages);
          setTotalElements(cached.meta.totalElements || cached.loans.length);
          if (cached.dashboard) setDashboard(cached.dashboard);
          setError("You're offline — showing the last saved loan portfolio.");
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
  }, [page, status, type]);

  useEffect(() => {
    let mounted = true;

    const run = async () => {
      if (mounted) setLoading(true);
      await loadPortfolio();
      if (mounted) setLoading(false);
    };

    void run();
    return () => {
      mounted = false;
    };
  }, [loadPortfolio]);

  const handleRefresh = async () => {
    setRefreshing(true);
    await loadPortfolio();
    setRefreshing(false);
  };

  const visibleLoans = useMemo(() => {
    const needle = query.trim().toLowerCase();
    if (!needle) return loans;

    return loans.filter((loan) => {
      const haystack = [
        loan.referenceNumber,
        getBorrowerName(loan),
        loan.borrower?.nationalId,
        loan.borrower?.phone,
        loan.loanType,
        loan.status,
      ]
        .filter(Boolean)
        .join(" ")
        .toLowerCase();

      return haystack.includes(needle);
    });
  }, [loans, query]);

  const summary = useMemo<Summary>(() => {
    const fallback = loans.reduce(
      (acc, loan) => {
        acc.totalLoans += 1;
        acc.disbursed += loan.disbursedAmount ?? 0;
        acc.outstanding += loan.outstandingBalance ?? 0;
        if (loan.status === "ACTIVE" || loan.status === "RESTRUCTURED")
          acc.active += 1;
        if (loan.status === "OVERDUE") acc.overdue += 1;
        if (loan.status === "PENDING" || loan.status === "UNDER_REVIEW")
          acc.pending += 1;
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

  const resetFilters = () => {
    setQuery("");
    setStatus("");
    setType("");
    setPage(0);
  };

  const startItem = totalElements === 0 ? 0 : page * PAGE_SIZE + 1;
  const endItem = Math.min(
    totalElements,
    page * PAGE_SIZE + visibleLoans.length,
  );

  if (loading) {
    return <PageSpinner />;
  }

  return (
    <div className="min-h-full bg-slate-50/40 pb-10">
      <div className="mx-auto max-w-[1680px] space-y-6">
        <section className="rounded-3xl border border-slate-200/80 bg-slate-950 p-6 text-white shadow-[0_18px_50px_rgba(15,23,42,0.12)] sm:p-8">
          <div className="flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
            <div>
              <div className="mb-3 inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/5 px-3 py-1.5 text-[10px] font-bold uppercase tracking-[0.18em] text-teal-200">
                <span className="h-1.5 w-1.5 rounded-full bg-teal-400" />
                Lending Operations
              </div>
              <h1 className="text-3xl font-black tracking-tight sm:text-4xl">
                Loan Portfolio
              </h1>
              <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-300">
                One place to review loan balances, collections, risk, arrears
                and repayment progress without mixing operational figures with
                accounting journals.
              </p>
            </div>

            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                onClick={() => void handleRefresh()}
                disabled={refreshing}
                className="rounded-xl border border-white/10 bg-white/5 px-4 py-2.5 text-xs font-bold text-white transition hover:bg-white/10 disabled:cursor-not-allowed disabled:opacity-60"
              >
                {refreshing ? "Refreshing…" : "Refresh portfolio"}
              </button>
              {isOfficer ? (
                <Link
                  href="/dashboard/loans/new"
                  className="rounded-xl bg-teal-500 px-4 py-2.5 text-xs font-bold text-slate-950 transition hover:bg-teal-400"
                >
                  + New loan
                </Link>
              ) : null}
            </div>
          </div>

          <div className="mt-8 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <div className="rounded-2xl border border-white/10 bg-white/5 p-4">
              <div className="text-[10px] font-bold uppercase tracking-[0.16em] text-slate-400">
                Total loans
              </div>
              <div className="mt-2 text-2xl font-black">
                {summary.totalLoans.toLocaleString()}
              </div>
              <div className="mt-1 text-xs text-slate-400">
                Current portfolio count
              </div>
            </div>
            <div className="rounded-2xl border border-white/10 bg-white/5 p-4">
              <div className="text-[10px] font-bold uppercase tracking-[0.16em] text-slate-400">
                Disbursed
              </div>
              <div className="mt-2 text-2xl font-black">
                {formatCurrency(summary.disbursed, currency, locale)}
              </div>
              <div className="mt-1 text-xs text-slate-400">
                Cash released to borrowers
              </div>
            </div>
            <div className="rounded-2xl border border-white/10 bg-white/5 p-4">
              <div className="text-[10px] font-bold uppercase tracking-[0.16em] text-slate-400">
                Outstanding
              </div>
              <div className="mt-2 text-2xl font-black">
                {formatCurrency(summary.outstanding, currency, locale)}
              </div>
              <div className="mt-1 text-xs text-slate-400">
                Current principal exposure
              </div>
            </div>
            <div className="rounded-2xl border border-white/10 bg-white/5 p-4">
              <div className="text-[10px] font-bold uppercase tracking-[0.16em] text-slate-400">
                Portfolio attention
              </div>
              <div className="mt-2 flex items-center gap-2 text-2xl font-black">
                <span>{summary.overdue}</span>
                <span className="text-sm font-semibold text-slate-400">
                  overdue
                </span>
              </div>
              <div className="mt-1 text-xs text-slate-400">
                {summary.active} active · {summary.pending} pending
              </div>
            </div>
          </div>
        </section>

        {!online ? (
          <div className="rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
            <div className="font-bold">Offline mode</div>
            <div className="mt-0.5 text-xs text-amber-800">
              The portfolio remains readable from the latest cached version.
              Actions that require the server are not assumed to have completed.
            </div>
          </div>
        ) : null}

        {error ? (
          <div
            className={`rounded-2xl border px-4 py-3 text-sm ${error.startsWith("You're offline") ? "border-amber-200 bg-amber-50 text-amber-900" : "border-red-200 bg-red-50 text-red-900"}`}
          >
            {error}
          </div>
        ) : null}

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
            description="Current principal still due."
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

        <section className="rounded-3xl border border-slate-200/80 bg-white shadow-sm">
          <div className="border-b border-slate-100 p-5 sm:p-6">
            <div className="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
              <div>
                <h2 className="text-base font-bold text-slate-950">
                  Find a loan
                </h2>
                <p className="mt-1 text-xs text-slate-400">
                  Search the current page and narrow the server-side portfolio
                  by status or loan type.
                </p>
              </div>

              <div className="flex flex-col gap-2 sm:flex-row">
                <div className="relative min-w-[260px]">
                  <IconSearch className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                  <input
                    value={query}
                    onChange={(event) => setQuery(event.target.value)}
                    placeholder="Reference, borrower, ID…"
                    className="h-10 w-full rounded-xl border border-slate-200 bg-slate-50 pl-9 pr-3 text-sm text-slate-900 outline-none transition focus:border-teal-500 focus:bg-white focus:ring-4 focus:ring-teal-500/10"
                  />
                </div>

                <select
                  value={status}
                  onChange={(event) => {
                    setStatus(event.target.value as "" | LoanStatus);
                    setPage(0);
                  }}
                  className="h-10 rounded-xl border border-slate-200 bg-white px-3 text-sm text-slate-700 outline-none focus:border-teal-500 focus:ring-4 focus:ring-teal-500/10"
                >
                  {STATUS_OPTIONS.map((item) => (
                    <option key={item.value} value={item.value}>
                      {item.label}
                    </option>
                  ))}
                </select>

                <select
                  value={type}
                  onChange={(event) => {
                    setType(event.target.value as "" | LoanType);
                    setPage(0);
                  }}
                  className="h-10 rounded-xl border border-slate-200 bg-white px-3 text-sm text-slate-700 outline-none focus:border-teal-500 focus:ring-4 focus:ring-teal-500/10"
                >
                  {TYPE_OPTIONS.map((item) => (
                    <option key={item.value} value={item.value}>
                      {item.label}
                    </option>
                  ))}
                </select>

                <button
                  type="button"
                  onClick={resetFilters}
                  className="h-10 rounded-xl border border-slate-200 bg-white px-3 text-xs font-bold text-slate-600 transition hover:bg-slate-50"
                >
                  Reset
                </button>
              </div>
            </div>
          </div>

          <div className="overflow-x-auto">
            <table className="min-w-[1180px] w-full text-sm">
              <thead className="border-b border-slate-100 bg-slate-50/80 text-left">
                <tr>
                  {[
                    "Loan",
                    "Borrower",
                    "Status & risk",
                    "Original principal",
                    "Disbursed",
                    "Outstanding",
                    "Type / due",
                  ].map((label) => (
                    <th
                      key={label}
                      className="px-4 py-3 text-[10px] font-bold uppercase tracking-[0.14em] text-slate-400"
                    >
                      {label}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {visibleLoans.length === 0 ? (
                  <tr>
                    <td colSpan={7}>
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

          <div className="flex flex-col gap-3 border-t border-slate-100 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
            <div className="text-xs text-slate-400">
              Showing{" "}
              <span className="font-semibold text-slate-700">{startItem}</span>–
              <span className="font-semibold text-slate-700">{endItem}</span> of{" "}
              <span className="font-semibold text-slate-700">
                {totalElements}
              </span>{" "}
              loans
            </div>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => setPage((current) => Math.max(0, current - 1))}
                disabled={page <= 0}
                className="rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs font-bold text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
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
                disabled={
                  totalPages > 0
                    ? page >= totalPages - 1
                    : loans.length < PAGE_SIZE
                }
                className="rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs font-bold text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
              >
                Next
              </button>
            </div>
          </div>
        </section>

        <section className="grid gap-4 lg:grid-cols-3">
          <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-blue-50 text-blue-700">
                <IconCard className="h-5 w-5" />
              </div>
              <div>
                <div className="text-sm font-bold text-slate-900">
                  Original principal
                </div>
                <div className="text-xs text-slate-400">
                  The amount approved for lending.
                </div>
              </div>
            </div>
          </div>
          <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-violet-50 text-violet-700">
                <IconSend className="h-5 w-5" />
              </div>
              <div>
                <div className="text-sm font-bold text-slate-900">
                  Disbursed
                </div>
                <div className="text-xs text-slate-400">
                  Cash actually released to the borrower.
                </div>
              </div>
            </div>
          </div>
          <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-teal-50 text-teal-700">
                <IconCoins className="h-5 w-5" />
              </div>
              <div>
                <div className="text-sm font-bold text-slate-900">
                  Outstanding
                </div>
                <div className="text-xs text-slate-400">
                  Current principal exposure, separate from future interest and
                  fees.
                </div>
              </div>
            </div>
          </div>
        </section>

        <footer className="border-t border-slate-200 pt-5 text-xs text-slate-400">
          Portfolio figures are operational loan metrics. Financial statements
          and journal-level balances remain the responsibility of the accounting
          workspace.
        </footer>
      </div>
    </div>
  );
}
