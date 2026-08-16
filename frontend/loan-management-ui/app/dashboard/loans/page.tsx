"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";

import { loanApi } from "@/services/api";
import { Loan, LoanStatus } from "@/types";
import { formatCurrency, formatDate } from "@/lib/utils";
import { useAuth } from "@/hooks/useAuth";
import { cacheGet, cacheSet } from "@/lib/offlineDb";
import { useOnlineStatus } from "@/hooks/useOnlineStatus";

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
  totalLoans?: number | string;
  activeLoans?: number | string;
  pendingLoans?: number | string;
  completedLoans?: number | string;
  defaultedLoans?: number | string;
  overdueLoans?: number | string;
  totalBorrowers?: number | string;

  totalDisbursed?: number | string;
  totalCollected?: number | string;
  outstandingBalance?: number | string;
  collectedThisMonth?: number | string;
  latePaymentsCount?: number | string;
  portfolioAtRiskPct?: number | string;

  recentLoans?: Loan[];
};

type Summary = {
  totalLoans: number;
  activeLoans: number;
  pendingLoans: number;
  overdueLoans: number;
  defaultedLoans: number;
  completedLoans: number;
  totalBorrowers: number;
  totalDisbursed: number;
  totalCollected: number;
  outstandingBalance: number;
  collectedThisMonth: number;
  latePaymentsCount: number;
  portfolioAtRiskPct: number;
};

const toNumber = (value: unknown): number => {
  const result = Number(value);

  return Number.isFinite(result) ? result : 0;
};

const humanizeStatus = (value?: string | null): string => {
  if (!value) return "Unknown";

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

const getPageLoans = (response: unknown): Loan[] => {
  const root = response as LoanPageResponse | undefined;

  if (!root) return [];

  if (Array.isArray(root.content)) {
    return root.content;
  }

  if (Array.isArray(root.items)) {
    return root.items;
  }

  if (Array.isArray(root.data)) {
    return root.data;
  }

  return [];
};

const getPageMeta = (response: unknown) => {
  const root = response as LoanPageResponse | undefined;

  const totalElements = toNumber(root?.totalElements);

  const totalPages =
    root?.totalPages != null
      ? toNumber(root.totalPages)
      : totalElements > 0
        ? Math.ceil(totalElements / PAGE_SIZE)
        : 0;

  return {
    totalElements,
    totalPages,
    page: toNumber(root?.page ?? root?.number),
  };
};

function StatusPill({ status }: { status?: string | null }) {
  const value = status ?? "";

  const styles: Record<string, string> = {
    ACTIVE: "border-emerald-200 bg-emerald-50 text-emerald-700",
    PAID: "border-slate-200 bg-slate-100 text-slate-700",
    CLOSED: "border-slate-200 bg-slate-100 text-slate-700",
    PENDING: "border-amber-200 bg-amber-50 text-amber-700",
    UNDER_REVIEW: "border-blue-200 bg-blue-50 text-blue-700",
    APPROVED: "border-indigo-200 bg-indigo-50 text-indigo-700",
    DISBURSED: "border-teal-200 bg-teal-50 text-teal-700",
    OVERDUE: "border-red-200 bg-red-50 text-red-700",
    DEFAULTED: "border-red-300 bg-red-100 text-red-800",
    RESTRUCTURED: "border-violet-200 bg-violet-50 text-violet-700",
    REJECTED: "border-slate-200 bg-slate-100 text-slate-500",
    WRITTEN_OFF: "border-slate-300 bg-slate-100 text-slate-600",
    CANCELLED: "border-slate-200 bg-slate-100 text-slate-500",
  };

  return (
    <span
      className={`inline-flex items-center rounded-full border px-2.5 py-1 text-[11px] font-bold ${styles[value] ?? "border-slate-200 bg-slate-50 text-slate-600"}`}
    >
      {humanizeStatus(value)}
    </span>
  );
}

function RiskPill({ loan }: { loan: Loan }) {
  const category = String(
    (loan as Loan & { riskCategory?: unknown }).riskCategory ??
      (loan as Loan & { creditQuality?: unknown }).creditQuality ??
      "",
  ).toUpperCase();

  if (!category) {
    return <span className="text-xs text-slate-400">Not scored</span>;
  }

  const styles: Record<string, string> = {
    LOW: "border-emerald-200 bg-emerald-50 text-emerald-700",
    MEDIUM: "border-amber-200 bg-amber-50 text-amber-700",
    HIGH: "border-red-200 bg-red-50 text-red-700",
    CRITICAL: "border-red-300 bg-red-100 text-red-800",
  };

  return (
    <span
      className={`inline-flex rounded-full border px-2.5 py-1 text-[11px] font-bold ${
        styles[category] ?? "border-slate-200 bg-slate-50 text-slate-600"
      }`}
    >
      {humanizeStatus(category)}
    </span>
  );
}

function KpiCard({
  label,
  value,
  description,
  icon,
  emphasis = false,
}: {
  label: string;
  value: string;
  description: string;
  icon: React.ReactNode;
  emphasis?: boolean;
}) {
  return (
    <div
      className={[
        "relative overflow-hidden rounded-2xl border p-5 transition",
        emphasis
          ? "border-slate-800 bg-slate-950 text-white shadow-[0_18px_50px_rgba(15,23,42,0.16)]"
          : "border-slate-200 bg-white shadow-sm hover:shadow-md",
      ].join(" ")}
    >
      <div className="flex items-start justify-between gap-4">
        <div>
          <p
            className={
              emphasis
                ? "text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400"
                : "text-[10px] font-bold uppercase tracking-[0.18em] text-slate-500"
            }
          >
            {label}
          </p>

          <p
            className={
              emphasis
                ? "mt-3 text-2xl font-black tracking-tight text-white"
                : "mt-3 text-2xl font-black tracking-tight text-slate-950"
            }
          >
            {value}
          </p>

          <p
            className={
              emphasis
                ? "mt-1 text-xs text-slate-400"
                : "mt-1 text-xs text-slate-500"
            }
          >
            {description}
          </p>
        </div>

        <div
          className={
            emphasis
              ? "rounded-xl border border-white/10 bg-white/5 p-2.5 text-teal-300"
              : "rounded-xl border border-slate-200 bg-slate-50 p-2.5 text-slate-600"
          }
        >
          {icon}
        </div>
      </div>
    </div>
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

  const [query, setQuery] = useState("");

  const loadPortfolio = useCallback(async () => {
    const cacheKey = `/loans/list?page=${page}&size=${PAGE_SIZE}&status=${status}`;

    setError(null);

    try {
      /*
       * IMPORTANT:
       *
       * No LoanType is sent anymore.
       *
       * The Loan model does not expose loanType,
       * therefore the frontend must not invent it.
       */
      const [listResponse, dashboardResponse] = await Promise.all([
        loanApi.list(page, PAGE_SIZE, status),
        page === 0 && !status ? loanApi.dashboard() : Promise.resolve(null),
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
          setLoans(Array.isArray(cached.loans) ? cached.loans : []);

          setTotalPages(cached.meta.totalPages);

          setTotalElements(cached.meta.totalElements || cached.loans.length);

          if (cached.dashboard) {
            setDashboard(cached.dashboard);
          }

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
        console.error("Failed to load cached portfolio", cacheError);

        setLoans([]);

        setError("Unable to load the loan portfolio.");
      }
    }
  }, [page, status]);

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

  const handleRefresh = async () => {
    setRefreshing(true);

    try {
      await loadPortfolio();
    } finally {
      setRefreshing(false);
    }
  };

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

        acc.totalDisbursed += Number(loan.disbursedAmount ?? 0);

        acc.outstandingBalance += Number(loan.outstandingBalance ?? 0);

        if (loan.status === "ACTIVE") {
          acc.activeLoans += 1;
        }

        if (loan.status === "OVERDUE") {
          acc.overdueLoans += 1;
        }

        if (loan.status === "PENDING" || loan.status === "UNDER_REVIEW") {
          acc.pendingLoans += 1;
        }

        if (loan.status === "DEFAULTED") {
          acc.defaultedLoans += 1;
        }

        if (loan.status === "PAID") {
          acc.completedLoans += 1;
        }

        return acc;
      },
      {
        totalLoans: 0,
        activeLoans: 0,
        pendingLoans: 0,
        overdueLoans: 0,
        defaultedLoans: 0,
        completedLoans: 0,
        totalBorrowers: 0,
        totalDisbursed: 0,
        totalCollected: 0,
        outstandingBalance: 0,
        collectedThisMonth: 0,
        latePaymentsCount: 0,
        portfolioAtRiskPct: 0,
      },
    );

    return {
      totalLoans:
        dashboard?.totalLoans != null
          ? toNumber(dashboard.totalLoans)
          : totalElements || fallback.totalLoans,

      activeLoans:
        dashboard?.activeLoans != null
          ? toNumber(dashboard.activeLoans)
          : fallback.activeLoans,

      pendingLoans:
        dashboard?.pendingLoans != null
          ? toNumber(dashboard.pendingLoans)
          : fallback.pendingLoans,

      overdueLoans:
        dashboard?.overdueLoans != null
          ? toNumber(dashboard.overdueLoans)
          : fallback.overdueLoans,

      defaultedLoans:
        dashboard?.defaultedLoans != null
          ? toNumber(dashboard.defaultedLoans)
          : fallback.defaultedLoans,

      completedLoans:
        dashboard?.completedLoans != null
          ? toNumber(dashboard.completedLoans)
          : fallback.completedLoans,

      totalBorrowers:
        dashboard?.totalBorrowers != null
          ? toNumber(dashboard.totalBorrowers)
          : fallback.totalBorrowers,

      totalDisbursed:
        dashboard?.totalDisbursed != null
          ? toNumber(dashboard.totalDisbursed)
          : fallback.totalDisbursed,

      totalCollected:
        dashboard?.totalCollected != null
          ? toNumber(dashboard.totalCollected)
          : fallback.totalCollected,

      outstandingBalance:
        dashboard?.outstandingBalance != null
          ? toNumber(dashboard.outstandingBalance)
          : fallback.outstandingBalance,

      collectedThisMonth:
        dashboard?.collectedThisMonth != null
          ? toNumber(dashboard.collectedThisMonth)
          : fallback.collectedThisMonth,

      latePaymentsCount:
        dashboard?.latePaymentsCount != null
          ? toNumber(dashboard.latePaymentsCount)
          : fallback.latePaymentsCount,

      portfolioAtRiskPct:
        dashboard?.portfolioAtRiskPct != null
          ? toNumber(dashboard.portfolioAtRiskPct)
          : fallback.portfolioAtRiskPct,
    };
  }, [dashboard, loans, totalElements]);

  const collectionRate =
    summary.totalDisbursed > 0
      ? Math.min(
          100,
          Math.max(0, (summary.totalCollected / summary.totalDisbursed) * 100),
        )
      : 0;

  const resetFilters = () => {
    setQuery("");
    setStatus("");
    setPage(0);
  };

  const startItem = totalElements === 0 ? 0 : page * PAGE_SIZE + 1;

  const endItem = Math.min(
    totalElements,
    page * PAGE_SIZE + visibleLoans.length,
  );

  if (loading) {
    return (
      <div className="min-h-full bg-[#f6f8f7] p-6">
        <div className="mx-auto max-w-[1680px] animate-pulse space-y-6">
          <div className="h-52 rounded-3xl bg-slate-900" />

          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            {Array.from({
              length: 4,
            }).map((_, index) => (
              <div key={index} className="h-32 rounded-2xl bg-white" />
            ))}
          </div>

          <div className="h-[500px] rounded-2xl bg-white" />
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-full bg-[#f6f8f7] pb-12">
      <div className="mx-auto max-w-[1680px] space-y-6 px-4 py-6 sm:px-6 lg:px-8">
        {/* ============================================================
            HERO
        ============================================================ */}

        <section className="relative overflow-hidden rounded-[28px] bg-slate-950 px-6 py-8 text-white shadow-[0_20px_70px_rgba(15,23,42,0.16)] sm:px-8 lg:px-10">
          <div className="absolute -right-24 -top-32 h-80 w-80 rounded-full bg-teal-500/10 blur-3xl" />
          <div className="absolute -bottom-32 left-1/3 h-72 w-72 rounded-full bg-cyan-500/10 blur-3xl" />

          <div className="relative flex flex-col gap-7 lg:flex-row lg:items-end lg:justify-between">
            <div>
              <div className="mb-4 inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/5 px-3 py-1.5 text-[10px] font-bold uppercase tracking-[0.18em] text-teal-300">
                <span className="h-1.5 w-1.5 rounded-full bg-teal-400" />
                Lending operations
              </div>

              <h1 className="text-3xl font-black tracking-tight sm:text-4xl">
                Loan Portfolio
              </h1>

              <p className="mt-3 max-w-2xl text-sm leading-6 text-slate-300">
                Executive view of lending exposure, collections, arrears and
                portfolio quality.
              </p>
            </div>

            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                onClick={() => void handleRefresh()}
                disabled={refreshing}
                className="rounded-xl border border-white/10 bg-white/5 px-4 py-2.5 text-xs font-bold text-white transition hover:bg-white/10 disabled:opacity-50"
              >
                {refreshing ? "Refreshing…" : "Refresh"}
              </button>

              {isOfficer ? (
                <Link
                  href="/dashboard/loans/new"
                  className="rounded-xl bg-teal-400 px-4 py-2.5 text-xs font-black text-slate-950 transition hover:bg-teal-300"
                >
                  + New loan
                </Link>
              ) : null}
            </div>
          </div>

          {/* Primary financial metrics */}
          <div className="relative mt-8 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <div className="rounded-2xl border border-white/10 bg-white/[0.06] p-5">
              <p className="text-[10px] font-bold uppercase tracking-[0.16em] text-slate-400">
                Outstanding portfolio
              </p>

              <p className="mt-2 text-2xl font-black">
                {formatCurrency(summary.outstandingBalance, currency, locale)}
              </p>

              <p className="mt-1 text-xs text-slate-400">
                Current principal exposure
              </p>
            </div>

            <div className="rounded-2xl border border-white/10 bg-white/[0.06] p-5">
              <p className="text-[10px] font-bold uppercase tracking-[0.16em] text-slate-400">
                Total disbursed
              </p>

              <p className="mt-2 text-2xl font-black">
                {formatCurrency(summary.totalDisbursed, currency, locale)}
              </p>

              <p className="mt-1 text-xs text-slate-400">
                Capital released to borrowers
              </p>
            </div>

            <div className="rounded-2xl border border-white/10 bg-white/[0.06] p-5">
              <p className="text-[10px] font-bold uppercase tracking-[0.16em] text-slate-400">
                Collected this month
              </p>

              <p className="mt-2 text-2xl font-black">
                {formatCurrency(summary.collectedThisMonth, currency, locale)}
              </p>

              <p className="mt-1 text-xs text-slate-400">
                Confirmed paid collections
              </p>
            </div>

            <div className="rounded-2xl border border-white/10 bg-white/[0.06] p-5">
              <p className="text-[10px] font-bold uppercase tracking-[0.16em] text-slate-400">
                Portfolio at risk
              </p>

              <p className="mt-2 text-2xl font-black">
                {summary.portfolioAtRiskPct.toFixed(2)}%
              </p>

              <p className="mt-1 text-xs text-slate-400">
                Overdue, defaulted and restructured exposure
              </p>
            </div>
          </div>
        </section>

        {/* ============================================================
            OFFLINE / ERROR
        ============================================================ */}

        {!online ? (
          <div className="flex items-start gap-3 rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
            <IconAlertTriangle className="mt-0.5 h-5 w-5 shrink-0" />

            <div>
              <div className="font-bold">Offline mode</div>

              <div className="mt-0.5 text-xs text-amber-800">
                Showing the latest saved portfolio. Server-side actions are not
                assumed to have completed.
              </div>
            </div>
          </div>
        ) : null}

        {error ? (
          <div className="flex items-start justify-between gap-4 rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-900">
            <div>
              <div className="font-bold">Portfolio unavailable</div>

              <div className="mt-1 text-xs text-red-700">{error}</div>
            </div>

            <button
              type="button"
              onClick={() => void handleRefresh()}
              className="shrink-0 rounded-lg border border-red-200 bg-white px-3 py-2 text-xs font-bold text-red-700"
            >
              Retry
            </button>
          </div>
        ) : null}

        {/* ============================================================
            KPI GRID
        ============================================================ */}

        <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <KpiCard
            label="Total facilities"
            value={summary.totalLoans.toLocaleString()}
            description={`${summary.totalBorrowers.toLocaleString()} borrowers`}
            icon={<IconFileText className="h-5 w-5" />}
            emphasis
          />

          <KpiCard
            label="Active facilities"
            value={summary.activeLoans.toLocaleString()}
            description="Currently performing loans"
            icon={<IconCheckCircle className="h-5 w-5" />}
          />

          <KpiCard
            label="Collection performance"
            value={`${collectionRate.toFixed(1)}%`}
            description={`${formatCurrency(summary.totalCollected, currency, locale)} collected`}
            icon={<IconCoins className="h-5 w-5" />}
          />

          <KpiCard
            label="Attention required"
            value={(
              summary.overdueLoans +
              summary.defaultedLoans +
              summary.pendingLoans
            ).toLocaleString()}
            description={`${summary.overdueLoans} overdue · ${summary.pendingLoans} pending`}
            icon={<IconAlertTriangle className="h-5 w-5" />}
          />
        </section>

        {/* ============================================================
            ATTENTION + PORTFOLIO QUALITY
        ============================================================ */}

        <section className="grid gap-6 xl:grid-cols-[1.35fr_0.65fr]">
          <div className="rounded-2xl border border-slate-200 bg-white shadow-sm">
            <div className="border-b border-slate-100 px-5 py-5">
              <div className="flex items-center justify-between gap-4">
                <div>
                  <p className="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400">
                    Management view
                  </p>

                  <h2 className="mt-1 text-lg font-black text-slate-950">
                    Portfolio attention
                  </h2>

                  <p className="mt-1 text-xs text-slate-500">
                    Areas requiring operational attention.
                  </p>
                </div>

                <IconAlertTriangle className="h-5 w-5 text-amber-500" />
              </div>
            </div>

            <div className="grid divide-y divide-slate-100 sm:grid-cols-3 sm:divide-x sm:divide-y-0">
              <Link
                href="/dashboard/loans?status=OVERDUE"
                className="group p-5 transition hover:bg-red-50/50"
              >
                <div className="flex items-center justify-between">
                  <span className="rounded-xl bg-red-50 p-2.5 text-red-600">
                    <IconClock className="h-5 w-5" />
                  </span>

                  <span className="text-xs font-bold text-slate-400 group-hover:text-red-600">
                    Review →
                  </span>
                </div>

                <p className="mt-5 text-2xl font-black text-slate-950">
                  {summary.overdueLoans.toLocaleString()}
                </p>

                <p className="mt-1 text-xs font-semibold text-slate-500">
                  Overdue facilities
                </p>

                <p className="mt-2 text-xs text-slate-400">
                  {summary.latePaymentsCount.toLocaleString()} late payments
                </p>
              </Link>

              <Link
                href="/dashboard/loans?status=PENDING"
                className="group p-5 transition hover:bg-amber-50/50"
              >
                <div className="flex items-center justify-between">
                  <span className="rounded-xl bg-amber-50 p-2.5 text-amber-600">
                    <IconSend className="h-5 w-5" />
                  </span>

                  <span className="text-xs font-bold text-slate-400 group-hover:text-amber-600">
                    Review →
                  </span>
                </div>

                <p className="mt-5 text-2xl font-black text-slate-950">
                  {summary.pendingLoans.toLocaleString()}
                </p>

                <p className="mt-1 text-xs font-semibold text-slate-500">
                  Pending applications
                </p>

                <p className="mt-2 text-xs text-slate-400">
                  Awaiting credit workflow
                </p>
              </Link>

              <Link
                href="/dashboard/loans?status=DEFAULTED"
                className="group p-5 transition hover:bg-slate-100"
              >
                <div className="flex items-center justify-between">
                  <span className="rounded-xl bg-slate-100 p-2.5 text-slate-700">
                    <IconCard className="h-5 w-5" />
                  </span>

                  <span className="text-xs font-bold text-slate-400 group-hover:text-slate-700">
                    Review →
                  </span>
                </div>

                <p className="mt-5 text-2xl font-black text-slate-950">
                  {summary.defaultedLoans.toLocaleString()}
                </p>

                <p className="mt-1 text-xs font-semibold text-slate-500">
                  Defaulted facilities
                </p>

                <p className="mt-2 text-xs text-slate-400">
                  Require escalation
                </p>
              </Link>
            </div>
          </div>

          <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <div>
              <p className="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400">
                Portfolio quality
              </p>

              <h2 className="mt-1 text-lg font-black text-slate-950">
                Lending book
              </h2>
            </div>

            <div className="mt-6 space-y-5">
              <div>
                <div className="flex justify-between text-xs">
                  <span className="font-semibold text-slate-600">Active</span>

                  <span className="font-black text-slate-900">
                    {summary.activeLoans}
                  </span>
                </div>

                <div className="mt-2 h-2 overflow-hidden rounded-full bg-slate-100">
                  <div
                    className="h-full rounded-full bg-emerald-500"
                    style={{
                      width: `${
                        summary.totalLoans
                          ? Math.min(
                              100,
                              (summary.activeLoans / summary.totalLoans) * 100,
                            )
                          : 0
                      }%`,
                    }}
                  />
                </div>
              </div>

              <div>
                <div className="flex justify-between text-xs">
                  <span className="font-semibold text-slate-600">Pending</span>

                  <span className="font-black text-slate-900">
                    {summary.pendingLoans}
                  </span>
                </div>

                <div className="mt-2 h-2 overflow-hidden rounded-full bg-slate-100">
                  <div
                    className="h-full rounded-full bg-amber-400"
                    style={{
                      width: `${
                        summary.totalLoans
                          ? Math.min(
                              100,
                              (summary.pendingLoans / summary.totalLoans) * 100,
                            )
                          : 0
                      }%`,
                    }}
                  />
                </div>
              </div>

              <div>
                <div className="flex justify-between text-xs">
                  <span className="font-semibold text-slate-600">Overdue</span>

                  <span className="font-black text-red-600">
                    {summary.overdueLoans}
                  </span>
                </div>

                <div className="mt-2 h-2 overflow-hidden rounded-full bg-slate-100">
                  <div
                    className="h-full rounded-full bg-red-500"
                    style={{
                      width: `${
                        summary.totalLoans
                          ? Math.min(
                              100,
                              (summary.overdueLoans / summary.totalLoans) * 100,
                            )
                          : 0
                      }%`,
                    }}
                  />
                </div>
              </div>

              <div>
                <div className="flex justify-between text-xs">
                  <span className="font-semibold text-slate-600">
                    Completed
                  </span>

                  <span className="font-black text-slate-900">
                    {summary.completedLoans}
                  </span>
                </div>

                <div className="mt-2 h-2 overflow-hidden rounded-full bg-slate-100">
                  <div
                    className="h-full rounded-full bg-slate-700"
                    style={{
                      width: `${
                        summary.totalLoans
                          ? Math.min(
                              100,
                              (summary.completedLoans / summary.totalLoans) *
                                100,
                            )
                          : 0
                      }%`,
                    }}
                  />
                </div>
              </div>
            </div>

            <div className="mt-7 border-t border-slate-100 pt-5">
              <div className="flex items-center justify-between">
                <span className="text-xs font-semibold text-slate-500">
                  Portfolio at risk
                </span>

                <span
                  className={[
                    "text-sm font-black",
                    summary.portfolioAtRiskPct >= 10
                      ? "text-red-600"
                      : summary.portfolioAtRiskPct >= 5
                        ? "text-amber-600"
                        : "text-emerald-600",
                  ].join(" ")}
                >
                  {summary.portfolioAtRiskPct.toFixed(2)}%
                </span>
              </div>
            </div>
          </div>
        </section>

        {/* ============================================================
            SEARCH + FILTER
        ============================================================ */}

        <section className="rounded-2xl border border-slate-200 bg-white shadow-sm">
          <div className="flex flex-col gap-4 p-5 lg:flex-row lg:items-center lg:justify-between">
            <div>
              <p className="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400">
                Portfolio register
              </p>

              <h2 className="mt-1 text-lg font-black text-slate-950">
                Loan facilities
              </h2>
            </div>

            <div className="flex flex-col gap-2 sm:flex-row">
              <div className="relative">
                <IconSearch className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />

                <input
                  value={query}
                  onChange={(event) => {
                    setQuery(event.target.value);
                    setPage(0);
                  }}
                  placeholder="Search borrower, reference, ID or phone"
                  className="h-11 w-full rounded-xl border border-slate-200 bg-slate-50 pl-10 pr-4 text-sm font-medium text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-teal-500 focus:bg-white focus:ring-4 focus:ring-teal-500/10 sm:w-[330px]"
                />
              </div>

              <select
                value={status}
                onChange={(event) => {
                  setStatus(event.target.value as "" | LoanStatus);
                  setPage(0);
                }}
                className="h-11 rounded-xl border border-slate-200 bg-slate-50 px-4 text-sm font-semibold text-slate-700 outline-none focus:border-teal-500 focus:ring-4 focus:ring-teal-500/10"
              >
                {STATUS_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>

              {(query || status) && (
                <button
                  type="button"
                  onClick={resetFilters}
                  className="h-11 rounded-xl border border-slate-200 bg-white px-4 text-xs font-bold text-slate-600 hover:bg-slate-50"
                >
                  Clear
                </button>
              )}
            </div>
          </div>

          {/* ==========================================================
              TABLE
          ========================================================== */}

          <div className="overflow-x-auto">
            <table className="min-w-[1100px] w-full border-collapse">
              <thead>
                <tr className="border-y border-slate-100 bg-slate-50/80">
                  <th className="px-5 py-3 text-left text-[10px] font-black uppercase tracking-[0.15em] text-slate-400">
                    Facility
                  </th>

                  <th className="px-5 py-3 text-left text-[10px] font-black uppercase tracking-[0.15em] text-slate-400">
                    Borrower
                  </th>

                  <th className="px-5 py-3 text-left text-[10px] font-black uppercase tracking-[0.15em] text-slate-400">
                    Status
                  </th>

                  <th className="px-5 py-3 text-right text-[10px] font-black uppercase tracking-[0.15em] text-slate-400">
                    Principal
                  </th>

                  <th className="px-5 py-3 text-right text-[10px] font-black uppercase tracking-[0.15em] text-slate-400">
                    Outstanding
                  </th>

                  <th className="px-5 py-3 text-left text-[10px] font-black uppercase tracking-[0.15em] text-slate-400">
                    Risk
                  </th>

                  <th className="px-5 py-3 text-right text-[10px] font-black uppercase tracking-[0.15em] text-slate-400">
                    Next due
                  </th>
                </tr>
              </thead>

              <tbody className="divide-y divide-slate-100">
                {visibleLoans.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="px-6 py-20 text-center">
                      <div className="mx-auto max-w-sm">
                        <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-slate-100">
                          <IconSearch className="h-5 w-5 text-slate-500" />
                        </div>

                        <p className="mt-4 text-sm font-black text-slate-900">
                          No facilities found
                        </p>

                        <p className="mt-1 text-xs leading-5 text-slate-500">
                          Adjust your search or status filter and try again.
                        </p>
                      </div>
                    </td>
                  </tr>
                ) : (
                  visibleLoans.map((loan) => {
                    const outstanding = Number(loan.outstandingBalance ?? 0);

                    const principal = Number(
                      loan.amount ?? loan.disbursedAmount ?? 0,
                    );

                    const paid = Math.max(0, principal - outstanding);

                    const progress =
                      principal > 0
                        ? Math.min(100, Math.max(0, (paid / principal) * 100))
                        : 0;

                    const daysOverdue = Number(
                      (
                        loan as Loan & {
                          daysOverdue?: unknown;
                        }
                      ).daysOverdue ?? 0,
                    );

                    return (
                      <tr
                        key={loan.id}
                        className="group transition hover:bg-slate-50/70"
                      >
                        <td className="px-5 py-4 align-top">
                          <Link
                            href={`/dashboard/loans/${loan.id}`}
                            className="block"
                          >
                            <div className="font-black text-slate-950 group-hover:text-teal-700">
                              {loan.referenceNumber ?? `Loan #${loan.id}`}
                            </div>

                            <div className="mt-1 text-xs text-slate-400">
                              {loan.createdAt
                                ? formatDate(loan.createdAt, locale)
                                : "Date unavailable"}
                            </div>
                          </Link>
                        </td>

                        <td className="px-5 py-4 align-top">
                          <div className="font-semibold text-slate-900">
                            {getBorrowerName(loan)}
                          </div>

                          <div className="mt-1 text-xs text-slate-400">
                            {loan.borrower?.nationalId ??
                              loan.borrower?.phone ??
                              "Borrower profile"}
                          </div>
                        </td>

                        <td className="px-5 py-4 align-top">
                          <StatusPill status={loan.status} />
                        </td>

                        <td className="px-5 py-4 text-right align-top">
                          <div className="font-bold text-slate-900">
                            {formatCurrency(principal, currency, locale)}
                          </div>
                        </td>

                        <td className="px-5 py-4 text-right align-top">
                          <div className="font-black text-slate-950">
                            {formatCurrency(outstanding, currency, locale)}
                          </div>

                          <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-slate-100">
                            <div
                              className="h-full rounded-full bg-teal-500"
                              style={{
                                width: `${progress}%`,
                              }}
                            />
                          </div>

                          <div className="mt-1 text-[11px] text-slate-400">
                            {progress.toFixed(0)}% repaid
                          </div>
                        </td>

                        <td className="px-5 py-4 align-top">
                          <RiskPill loan={loan} />

                          {daysOverdue > 0 ? (
                            <div className="mt-2 flex items-center gap-1 text-[11px] font-bold text-red-600">
                              <IconAlertTriangle className="h-3.5 w-3.5" />
                              {daysOverdue}d overdue
                            </div>
                          ) : null}
                        </td>

                        <td className="px-5 py-4 text-right align-top">
                          {loan.nextDueDate ? (
                            <div>
                              <div className="text-sm font-semibold text-slate-800">
                                {formatDate(loan.nextDueDate, locale)}
                              </div>

                              <div className="mt-1 text-[11px] text-slate-400">
                                Next scheduled payment
                              </div>
                            </div>
                          ) : (
                            <span className="text-xs text-slate-400">—</span>
                          )}
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>

          {/* ==========================================================
              PAGINATION
          ========================================================== */}

          <div className="flex flex-col gap-3 border-t border-slate-100 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
            <p className="text-xs text-slate-500">
              Showing{" "}
              <span className="font-bold text-slate-800">{startItem}</span> to{" "}
              <span className="font-bold text-slate-800">{endItem}</span> of{" "}
              <span className="font-bold text-slate-800">{totalElements}</span>{" "}
              facilities
            </p>

            <div className="flex items-center gap-2">
              <button
                type="button"
                disabled={page <= 0}
                onClick={() => setPage((current) => Math.max(0, current - 1))}
                className="rounded-xl border border-slate-200 bg-white px-4 py-2 text-xs font-bold text-slate-600 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
              >
                Previous
              </button>

              <div className="rounded-xl bg-slate-950 px-4 py-2 text-xs font-black text-white">
                {page + 1}
                {totalPages > 0 ? ` / ${totalPages}` : ""}
              </div>

              <button
                type="button"
                disabled={totalPages === 0 || page >= totalPages - 1}
                onClick={() => setPage((current) => current + 1)}
                className="rounded-xl border border-slate-200 bg-white px-4 py-2 text-xs font-bold text-slate-600 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
              >
                Next
              </button>
            </div>
          </div>
        </section>
      </div>
    </div>
  );
}
