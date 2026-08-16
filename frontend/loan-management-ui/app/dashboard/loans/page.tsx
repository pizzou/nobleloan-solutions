"use client";
import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { loanApi } from "@/services/api";
import { Loan } from "@/types";
import { useAuth } from "@/hooks/useAuth";
import { Button } from "@/components/ui/Button";
import { Card, CardBody, CardHeader, StatCard } from "@/components/ui/Card";
import { StatusBadge, RiskBadge } from "@/components/ui/Badge";
import { PageSpinner } from "@/components/ui/Skeleton";
import { formatCurrency, formatDate, formatNumber } from "@/lib/utils";
const n = (v: unknown) =>
  Number.isFinite(Number(v ?? 0)) ? Number(v ?? 0) : 0;
const name = (l: Loan) =>
  `${l.borrower?.firstName || ""} ${l.borrower?.lastName || ""}`.trim() ||
  "Unnamed client";
export default function LoansPage() {
  const { currency, locale } = useAuth();
  const [rows, setRows] = useState<Loan[]>([]);
  const [page, setPage] = useState(0);
  const [total, setTotal] = useState(0);
  const [status, setStatus] = useState("");
  const [q, setQ] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const r: any = await loanApi.list(page, 20, status);
      const content = Array.isArray(r)
        ? r
        : r?.content || r?.items || r?.data || [];
      setRows(content);
      setTotal(Number(r?.totalElements ?? r?.total ?? content.length));
    } catch (e: any) {
      setError(e?.message || "Unable to retrieve the loan portfolio.");
    } finally {
      setLoading(false);
    }
  }, [page, status]);
  useEffect(() => {
    void load();
  }, [load]);
  const filtered = useMemo(() => {
    const x = q.trim().toLowerCase();
    return x
      ? rows.filter((l) =>
          `${l.referenceNumber} ${name(l)} ${l.borrower?.nationalId || ""} ${l.borrower?.phone || ""}`
            .toLowerCase()
            .includes(x),
        )
      : rows;
  }, [rows, q]);
  const outstanding = rows.reduce((s, l) => s + n(l.outstandingBalance), 0),
    disbursed = rows.reduce((s, l) => s + n(l.disbursedAmount), 0),
    overdue = rows.filter((l) => n(l.daysOverdue) > 0).length;
  if (loading && !rows.length) return <PageSpinner />;
  return (
    <main className="premium-page pb-14">
      <div className="mx-auto max-w-[1680px] space-y-6 px-4 py-6 sm:px-6 lg:px-8">
        <section className="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <div className="premium-eyebrow">Lending book</div>
            <h1 className="premium-section-title">Loan portfolio</h1>
            <p className="premium-section-copy">
              A controlled view of facilities, exposure, repayment progress and
              credit quality. No loan type is assumed by the interface.
            </p>
          </div>
          <div className="flex gap-2">
            <Button variant="secondary" onClick={() => void load()}>
              Refresh
            </Button>
            <Button onClick={() => (location.href = "/dashboard/loans/new")}>
              New facility
            </Button>
          </div>
        </section>
        <section className="grid gap-4 sm:grid-cols-3">
          <StatCard
            icon={<span>◈</span>}
            label="Facilities on page"
            value={formatNumber(rows.length)}
            sub={`${formatNumber(total)} total records`}
            color="#0b2944"
          />
          <StatCard
            icon={<span>◆</span>}
            label="Visible exposure"
            value={formatCurrency(outstanding, currency, locale)}
            sub="Outstanding principal in view"
            color="#087f74"
          />
          <StatCard
            icon={<span>!</span>}
            label="Overdue in view"
            value={formatNumber(overdue)}
            sub={`Disbursed ${formatCurrency(disbursed, currency, locale)}`}
            color="#b42318"
          />
        </section>
        <Card>
          <CardBody>
            <div className="grid gap-3 lg:grid-cols-[1fr_180px_auto]">
              <input
                className="premium-input"
                value={q}
                onChange={(e) => setQ(e.target.value)}
                placeholder="Search reference, borrower, phone or national ID"
              />
              <select
                className="premium-input"
                value={status}
                onChange={(e) => {
                  setPage(0);
                  setStatus(e.target.value);
                }}
              >
                <option value="">All statuses</option>
                {[
                  "PENDING",
                  "UNDER_REVIEW",
                  "APPROVED",
                  "DISBURSED",
                  "ACTIVE",
                  "OVERDUE",
                  "DEFAULTED",
                  "RESTRUCTURED",
                  "PAID",
                  "CLOSED",
                  "REJECTED",
                ].map((s) => (
                  <option key={s}>{s}</option>
                ))}
              </select>
              <Button
                variant="outline"
                onClick={() => {
                  setQ("");
                  setStatus("");
                  setPage(0);
                }}
              >
                Clear filters
              </Button>
            </div>
          </CardBody>
        </Card>
        {error ? (
          <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-xs font-bold text-red-800">
            {error}
          </div>
        ) : null}
        <Card>
          <CardHeader
            title="Facility register"
            subtitle="Open a facility to enter the full operational workspace."
            action={
              <span className="text-[10px] font-bold text-slate-400">
                Page {page + 1}
              </span>
            }
          />
          <div className="overflow-x-auto">
            <table className="premium-table">
              <thead>
                <tr>
                  <th>Facility</th>
                  <th>Client</th>
                  <th>Status</th>
                  <th>Principal</th>
                  <th>Outstanding</th>
                  <th>Repayment</th>
                  <th>Next due</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((l) => {
                  const amount = n(l.amount),
                    paid = n(l.totalPaid),
                    progress = amount
                      ? Math.min(100, (paid / amount) * 100)
                      : 0;
                  return (
                    <tr key={l.id}>
                      <td>
                        <Link
                          href={`/dashboard/loans/${l.id}`}
                          className="font-black text-[#071a2d] hover:text-[#087f74]"
                        >
                          {l.referenceNumber}
                        </Link>
                        <div className="mt-1 text-[9px] text-slate-400">
                          Facility #{l.id}
                        </div>
                      </td>
                      <td>
                        <div className="font-bold">{name(l)}</div>
                        <div className="mt-1 text-[9px] text-slate-400">
                          {l.borrower?.phone || l.borrower?.nationalId || "—"}
                        </div>
                      </td>
                      <td>
                        <div className="flex flex-wrap gap-1">
                          {l.status && <StatusBadge status={l.status} />}{" "}
                          {l.riskCategory && (
                            <RiskBadge
                              category={l.riskCategory}
                              score={l.riskScore}
                            />
                          )}
                        </div>
                      </td>
                      <td className="font-black tabular-nums">
                        {formatCurrency(amount, currency, locale)}
                        <div className="mt-1 text-[9px] text-slate-400">
                          {l.durationMonths} months
                        </div>
                      </td>
                      <td className="font-black tabular-nums">
                        {formatCurrency(
                          n(l.outstandingBalance),
                          currency,
                          locale,
                        )}
                        {n(l.daysOverdue) > 0 ? (
                          <div className="mt-1 text-[9px] font-black text-red-600">
                            {n(l.daysOverdue)} days overdue
                          </div>
                        ) : null}
                      </td>
                      <td>
                        <div className="text-[10px] font-bold">
                          {progress.toFixed(0)}% paid
                        </div>
                        <div className="mt-2 h-1.5 w-28 rounded-full bg-slate-100">
                          <div
                            className="h-1.5 rounded-full bg-[#087f74]"
                            style={{ width: `${progress}%` }}
                          />
                        </div>
                      </td>
                      <td className="text-[10px] font-semibold">
                        {l.nextDueDate
                          ? formatDate(l.nextDueDate, locale)
                          : "—"}
                      </td>
                    </tr>
                  );
                })}
                {!filtered.length ? (
                  <tr>
                    <td colSpan={7} className="py-16 text-center">
                      <div className="text-sm font-black text-slate-800">
                        No facilities match the current filters
                      </div>
                      <div className="mt-1 text-xs text-slate-400">
                        Try another search or clear the filters.
                      </div>
                    </td>
                  </tr>
                ) : null}
              </tbody>
            </table>
          </div>
          <div className="flex flex-col gap-3 border-t border-slate-100 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
            <span className="text-[10px] text-slate-400">
              Showing {rows.length} of {total} facilities
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
                disabled={rows.length < 20}
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
