"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";

import { loanApi } from "@/services/api";
import { Loan, LoanStatus } from "@/types";
import { formatCurrency, formatDate } from "@/lib/utils";
import { useAuth } from "@/hooks/useAuth";
import { cacheGet, cacheSet } from "@/lib/offlineDb";
import { useOnlineStatus } from "@/hooks/useOnlineStatus";
import { StatusBadge, RiskBadge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { PageSpinner } from "@/components/ui/Skeleton";
import { Card, CardBody, CardHeader, StatCard } from "@/components/ui/Card";
import {
  IconAlertTriangle,
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

type PageResponse = {
  content?: Loan[];
  items?: Loan[];
  data?: Loan[];
  totalElements?: number;
  totalPages?: number;
  page?: number;
  number?: number;
  size?: number;
  last?: boolean;
};

const num = (v: unknown) => {
  const n = Number(v ?? 0);
  return Number.isFinite(n) ? n : 0;
};
const loansFrom = (v: unknown): Loan[] =>
  Array.isArray(v)
    ? (v as Loan[])
    : v && typeof v === "object"
      ? (v as PageResponse).content ||
        (v as PageResponse).items ||
        (v as PageResponse).data ||
        []
      : [];
const metaFrom = (v: unknown) => {
  const root = (v && typeof v === "object" ? v : {}) as PageResponse;
  const totalElements = Math.max(0, num(root.totalElements));
  const size = Math.max(1, num(root.size) || PAGE_SIZE);
  const totalPages = Math.max(
    0,
    num(root.totalPages) ||
      (totalElements ? Math.ceil(totalElements / size) : 0),
  );
  const page = Math.max(0, num(root.number ?? root.page ?? 0));
  return {
    totalElements,
    totalPages,
    page,
    size,
    last: root.last ?? (totalPages === 0 || page >= totalPages - 1),
  };
};

function borrowerName(loan: Loan) {
  const first = loan.borrower?.firstName?.trim() || "";
  const last = loan.borrower?.lastName?.trim() || "";
  return `${first} ${last}`.trim() || "Unnamed borrower";
}

export default function LoansPage() {
  const { currency, locale } = useAuth();
  const online = useOnlineStatus();
  const [loans, setLoans] = useState<Loan[]>([]);
  const [summary, setSummary] = useState<any>(null);
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState<"" | LoanStatus>("");
  const [page, setPage] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [result, dashboard] = await Promise.all([
        loanApi.list(page, PAGE_SIZE, status),
        loanApi.dashboard(),
      ]);
      const rows = loansFrom(result);
      const meta = metaFrom(result);
      setLoans(rows);
      setTotalElements(meta.totalElements);
      setTotalPages(meta.totalPages);
      setSummary(dashboard);
      await cacheSet("premium-loans-page", { rows, meta, dashboard }).catch(
        () => undefined,
      );
    } catch (err: any) {
      const cached = await cacheGet<any>("premium-loans-page").catch(
        () => null,
      );
      if (cached?.rows) {
        setLoans(cached.rows);
        setTotalElements(cached.meta?.totalElements || cached.rows.length);
        setTotalPages(cached.meta?.totalPages || 1);
        setSummary(cached.dashboard || null);
        setError(
          "You're offline. Showing the latest cached portfolio snapshot.",
        );
      } else setError(err?.message || "Unable to retrieve the loan portfolio.");
    } finally {
      setLoading(false);
    }
  }, [page, status]);

  useEffect(() => {
    void load();
  }, [load]);

  const filtered = useMemo(() => {
    const needle = query.trim().toLowerCase();
    if (!needle) return loans;
    return loans.filter((loan) => {
      const haystack = [
        loan.referenceNumber,
        borrowerName(loan),
        loan.borrower?.nationalId,
        loan.borrower?.phone,
      ]
        .filter(Boolean)
        .join(" ")
        .toLowerCase();
      return haystack.includes(needle);
    });
  }, [loans, query]);

  if (loading && !loans.length) return <PageSpinner />;

  return (
    <main className="premium-page pb-12">
      <div className="mx-auto max-w-[1700px] space-y-6 px-4 py-6 sm:px-6 lg:px-8">
        <section className="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <div className="premium-eyebrow">Lending operations</div>
            <h1 className="premium-section-title">Loan portfolio</h1>
            <p className="premium-section-copy">
              A controlled view of every facility, its current status, principal
              exposure and repayment position. Search is local to the loaded
              server page; status filtering is server-side.
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button variant="secondary" onClick={() => void load()}>
              Refresh
            </Button>
            <Link href="/dashboard/import">
              <Button variant="secondary">Import</Button>
            </Link>
            <Link href="/dashboard/loans/new">
              <Button>New loan</Button>
            </Link>
          </div>
        </section>

        {error ? (
          <div
            className={`rounded-xl border px-4 py-3 text-xs font-semibold ${error.startsWith("You're offline") ? "border-amber-200 bg-amber-50 text-amber-900" : "border-red-200 bg-red-50 text-red-900"}`}
          >
            {error}
          </div>
        ) : null}
        {!online ? (
          <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs font-semibold text-amber-900">
            Offline mode — cached portfolio data is readable. Server actions are
            not assumed to have completed.
          </div>
        ) : null}

        <section className="grid grid-cols-2 gap-4 xl:grid-cols-6">
          <StatCard
            icon={<IconFileText className="h-5 w-5" />}
            label="Facilities"
            value={num(summary?.totalLoans).toLocaleString()}
            sub="Current organization"
            color="#0B1F3A"
          />
          <StatCard
            icon={<IconCheckCircle className="h-5 w-5" />}
            label="Active"
            value={num(summary?.activeLoans).toLocaleString()}
            sub="Performing facilities"
            color="#0F766E"
          />
          <StatCard
            icon={<IconAlertTriangle className="h-5 w-5" />}
            label="Overdue"
            value={num(summary?.overdueLoans).toLocaleString()}
            sub="Collections attention"
            color="#B42318"
          />
          <StatCard
            icon={<IconClock className="h-5 w-5" />}
            label="Pending"
            value={num(summary?.pendingLoans).toLocaleString()}
            sub="Awaiting decision"
            color="#C8A84E"
          />
          <StatCard
            icon={<IconSend className="h-5 w-5" />}
            label="Disbursed"
            value={formatCurrency(
              num(summary?.totalDisbursed),
              currency,
              locale,
            )}
            sub="Gross disbursed"
            color="#16365F"
          />
          <StatCard
            icon={<IconCoins className="h-5 w-5" />}
            label="Outstanding"
            value={formatCurrency(
              num(summary?.outstandingBalance),
              currency,
              locale,
            )}
            sub="Principal exposure"
            color="#0F766E"
          />
        </section>

        <Card>
          <CardHeader
            title="Portfolio search & control"
            subtitle="Use status for server-side narrowing and search the currently loaded page for client identity or reference."
          />
          <CardBody>
            <div className="grid gap-3 lg:grid-cols-[1fr_230px_auto]">
              <div className="relative">
                <IconSearch className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                <input
                  className="premium-input pl-10"
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  placeholder="Reference, borrower, national ID or phone"
                />
              </div>
              <select
                className="premium-input"
                value={status}
                onChange={(e) => {
                  setStatus(e.target.value as "" | LoanStatus);
                  setPage(0);
                }}
              >
                {STATUS_OPTIONS.map((item) => (
                  <option key={item.value} value={item.value}>
                    {item.label}
                  </option>
                ))}
              </select>
              <Button
                variant="secondary"
                onClick={() => {
                  setQuery("");
                  setStatus("");
                  setPage(0);
                }}
              >
                Reset
              </Button>
            </div>
          </CardBody>
        </Card>

        <Card>
          <CardHeader
            title="Facilities"
            subtitle={`${totalElements.toLocaleString()} records in the selected portfolio view`}
            action={
              <span className="text-[10px] font-black uppercase tracking-[.14em] text-slate-400">
                Page {page + 1}
                {totalPages ? ` / ${totalPages}` : ""}
              </span>
            }
          />
          <div className="overflow-x-auto">
            <table className="premium-table min-w-[1180px] w-full text-sm">
              <thead>
                <tr>
                  <th>Facility</th>
                  <th>Borrower</th>
                  <th>Status & risk</th>
                  <th>Principal</th>
                  <th>Disbursed</th>
                  <th>Outstanding</th>
                  <th>Next due</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((loan) => {
                  const paid = num(loan.totalPaid);
                  const repayable = num(loan.totalRepayable);
                  const progress =
                    repayable > 0
                      ? Math.min(100, Math.max(0, (paid / repayable) * 100))
                      : 0;
                  return (
                    <tr key={loan.id}>
                      <td>
                        <Link
                          href={`/dashboard/loans/${loan.id}`}
                          className="font-black text-[#07152A] hover:text-[#0F766E]"
                        >
                          {loan.referenceNumber}
                        </Link>
                        <div className="mt-1 text-[10px] text-slate-400">
                          {loan.createdAt
                            ? formatDate(loan.createdAt, locale)
                            : "—"}
                        </div>
                      </td>
                      <td>
                        <div className="font-bold text-slate-800">
                          {borrowerName(loan)}
                        </div>
                        <div className="mt-1 text-[10px] text-slate-400">
                          {loan.borrower?.nationalId ||
                            loan.borrower?.phone ||
                            "No identifier"}
                        </div>
                      </td>
                      <td>
                        <div className="flex flex-wrap gap-1.5">
                          <StatusBadge status={loan.status} />
                          {loan.riskCategory ? (
                            <RiskBadge
                              category={loan.riskCategory}
                              score={loan.riskScore}
                            />
                          ) : null}
                        </div>
                      </td>
                      <td className="font-bold tabular-nums text-slate-900">
                        {formatCurrency(loan.amount, currency, locale)}
                        <div className="mt-1 text-[10px] text-slate-400">
                          {loan.durationMonths} months
                        </div>
                      </td>
                      <td className="font-bold tabular-nums text-slate-900">
                        {formatCurrency(
                          loan.disbursedAmount ?? 0,
                          currency,
                          locale,
                        )}
                        <div className="mt-1 text-[10px] text-slate-400">
                          net{" "}
                          {formatCurrency(
                            loan.netDisbursedAmount ?? 0,
                            currency,
                            locale,
                          )}
                        </div>
                      </td>
                      <td>
                        <div className="font-black tabular-nums text-slate-900">
                          {formatCurrency(
                            loan.outstandingBalance ?? 0,
                            currency,
                            locale,
                          )}
                        </div>
                        <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-slate-100">
                          <div
                            className="h-full rounded-full bg-[#0F766E]"
                            style={{ width: `${progress}%` }}
                          />
                        </div>
                        <div className="mt-1 text-[10px] text-slate-400">
                          {progress.toFixed(0)}% repaid
                        </div>
                      </td>
                      <td>
                        <div className="font-semibold text-slate-700">
                          {loan.nextDueDate
                            ? formatDate(loan.nextDueDate, locale)
                            : "Not scheduled"}
                        </div>
                        {num(loan.daysOverdue) > 0 ? (
                          <div className="mt-1 flex items-center gap-1 text-[10px] font-black text-red-600">
                            <IconAlertTriangle className="h-3 w-3" />
                            {loan.daysOverdue} days overdue
                          </div>
                        ) : null}
                      </td>
                    </tr>
                  );
                })}
                {!filtered.length ? (
                  <tr>
                    <td colSpan={7} className="py-16 text-center">
                      <div className="mx-auto grid h-12 w-12 place-items-center rounded-2xl bg-slate-100 text-slate-400">
                        <IconSearch className="h-5 w-5" />
                      </div>
                      <p className="mt-3 text-sm font-black text-slate-800">
                        No matching facilities
                      </p>
                      <p className="mt-1 text-xs text-slate-400">
                        Try a different search term or status.
                      </p>
                    </td>
                  </tr>
                ) : null}
              </tbody>
            </table>
          </div>
          <div className="flex flex-col gap-3 border-t border-slate-100 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
            <span className="text-[11px] text-slate-400">
              Showing page {page + 1}
              {totalPages ? ` of ${totalPages}` : ""} ·{" "}
              {totalElements.toLocaleString()} total facilities
            </span>
            <div className="flex gap-2">
              <Button
                size="sm"
                variant="secondary"
                disabled={page === 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
              >
                Previous
              </Button>
              <Button
                size="sm"
                variant="secondary"
                disabled={
                  totalPages > 0
                    ? page >= totalPages - 1
                    : loans.length < PAGE_SIZE
                }
                onClick={() => setPage((p) => p + 1)}
              >
                Next
              </Button>
            </div>
          </div>
        </Card>
      </div>
    </main>
  );
}
