"use client";
import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { loanApi, paymentApi } from "@/services/api";
import { Loan } from "@/types";
import { useAuth } from "@/hooks/useAuth";
import { Button } from "@/components/ui/Button";
import { Card, CardBody, CardHeader, StatCard } from "@/components/ui/Card";
import { StatusBadge, RiskBadge } from "@/components/ui/Badge";
import { PageSpinner } from "@/components/ui/Skeleton";
import { formatCurrency, formatDate } from "@/lib/utils";
const n = (v: unknown) =>
  Number.isFinite(Number(v ?? 0)) ? Number(v ?? 0) : 0;
export default function LoanDetail() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const { currency, locale } = useAuth();
  const id = Number(params.id);
  const [loan, setLoan] = useState<Loan | null>(null);
  const [schedule, setSchedule] = useState<any[]>([]);
  const [payments, setPayments] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  useEffect(() => {
    let on = true;
    Promise.all([
      loanApi.get(id),
      loanApi.schedule(id),
      paymentApi.schedule(id),
    ])
      .then(([l, s, p]) => {
        if (!on) return;
        setLoan(l as Loan);
        setSchedule(Array.isArray(s) ? s : s?.content || s?.items || []);
        setPayments(Array.isArray(p) ? p : p?.content || p?.items || []);
      })
      .catch((e) => on && setError(e?.message || "Unable to load facility."))
      .finally(() => on && setLoading(false));
    return () => {
      on = false;
    };
  }, [id]);
  const borrower = loan
    ? `${loan.borrower?.firstName || ""} ${loan.borrower?.lastName || ""}`.trim()
    : "—";
  const paid = n(loan?.totalPaid),
    principal = n(loan?.amount),
    progress = principal ? Math.min(100, (paid / principal) * 100) : 0;
  const next =
    schedule.find(
      (x) => !x.paid && String(x.status || "").toUpperCase() !== "PAID",
    ) || schedule.find((x) => !x.paid);
  const nextAmount = n(
    next?.amount ?? next?.totalDue ?? next?.installmentAmount,
  );
  const outstanding = n(loan?.outstandingBalance);
  if (loading) return <PageSpinner />;
  if (!loan)
    return (
      <main className="premium-page grid min-h-[80vh] place-items-center p-6">
        <Card>
          <CardBody>
            <h1 className="text-xl font-black">Facility unavailable</h1>
            <p className="mt-2 text-xs text-slate-500">
              {error || "No facility data was returned."}
            </p>
            <Button className="mt-4" onClick={() => router.back()}>
              Go back
            </Button>
          </CardBody>
        </Card>
      </main>
    );
  return (
    <main className="premium-page pb-14">
      <div className="mx-auto max-w-[1680px] space-y-6 px-4 py-6 sm:px-6 lg:px-8">
        <Link
          href="/dashboard/loans"
          className="text-[10px] font-black uppercase tracking-wider text-[#087f74]"
        >
          ← Portfolio
        </Link>
        <section className="premium-hero px-6 py-7 text-white sm:px-9">
          <div className="relative z-10 flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
            <div>
              <div className="premium-kicker">Facility workspace</div>
              <div className="mt-2 flex flex-wrap items-center gap-3">
                <h1 className="text-3xl font-black tracking-[-.04em]">
                  {loan.referenceNumber}
                </h1>
                <StatusBadge status={loan.status} />
              </div>
              <p className="mt-2 text-sm text-slate-300">
                {borrower} · Facility #{loan.id}
              </p>
            </div>
            <div className="flex gap-2">
              <Button
                variant="secondary"
                onClick={() => router.push(`/dashboard/payments?loanId=${id}`)}
              >
                Record payment
              </Button>
              <Button onClick={() => router.push("/dashboard/collections")}>
                Collections
              </Button>
            </div>
          </div>
        </section>
        <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <StatCard
            icon={<span>◆</span>}
            label="Outstanding principal"
            value={formatCurrency(outstanding, currency, locale)}
            sub="Current backend balance"
            color="#087f74"
          />
          <StatCard
            icon={<span>₣</span>}
            label="Original principal"
            value={formatCurrency(principal, currency, locale)}
            sub={`${progress.toFixed(0)}% paid`}
            color="#0b2944"
          />
          <StatCard
            icon={<span>◷</span>}
            label="Next installment"
            value={
              nextAmount ? formatCurrency(nextAmount, currency, locale) : "—"
            }
            sub={
              next?.dueDate
                ? `Due ${formatDate(next.dueDate, locale)}`
                : "No future installment returned"
            }
            color="#c9a227"
          />
          <StatCard
            icon={<span>!</span>}
            label="Risk / arrears"
            value={loan.riskCategory || loan.creditQuality || "CURRENT"}
            sub={
              n(loan.daysOverdue) > 0
                ? `${loan.daysOverdue} days overdue`
                : "No overdue days"
            }
            color={n(loan.daysOverdue) > 0 ? "#b42318" : "#087f74"}
          />
        </section>
        <section className="grid gap-5 xl:grid-cols-[1.35fr_.65fr]">
          <Card>
            <CardHeader
              title="Facility financial profile"
              subtitle="Authoritative values returned by the loan service"
            />
            <CardBody>
              <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                <Metric
                  label="Interest rate"
                  value={`${n(loan.interestRate).toFixed(2)}%`}
                />
                <Metric
                  label="Management fee"
                  value={`${n(loan.managementFeeRate).toFixed(2)}%`}
                />
                <Metric
                  label="Processing fee"
                  value={
                    loan.processingFeeRate != null
                      ? `${n(loan.processingFeeRate).toFixed(2)}%`
                      : "—"
                  }
                />
                <Metric
                  label="Duration"
                  value={`${n(loan.durationMonths)} months`}
                />
                <Metric
                  label="Disbursed"
                  value={
                    loan.disbursedAt
                      ? formatDate(loan.disbursedAt, locale)
                      : "Not disbursed"
                  }
                />
                <Metric
                  label="Maturity"
                  value={
                    loan.maturityDate
                      ? formatDate(loan.maturityDate, locale)
                      : "—"
                  }
                />
                <Metric
                  label="Last payment"
                  value={
                    loan.lastPaymentDate
                      ? formatDate(loan.lastPaymentDate, locale)
                      : "—"
                  }
                />
                <Metric
                  label="Collection stage"
                  value={loan.collectionsStage || "NORMAL"}
                />
              </div>
            </CardBody>
          </Card>
          <Card>
            <CardHeader title="Client relationship" />
            <CardBody>
              <div className="text-lg font-black text-[#071a2d]">
                {borrower}
              </div>
              <div className="mt-2 text-xs text-slate-500">
                {loan.borrower?.phone ||
                  loan.borrower?.email ||
                  "Contact information unavailable"}
              </div>
              <div className="mt-5 grid grid-cols-2 gap-2">
                <Metric label="KYC" value={loan.borrower?.kycStatus || "—"} />
                <Metric
                  label="Client status"
                  value={loan.borrower?.status || "—"}
                />
              </div>
              <Link
                href={`/dashboard/borrowers/${loan.borrower?.id}`}
                className="mt-5 inline-flex text-[10px] font-black text-[#087f74]"
              >
                Open client profile →
              </Link>
            </CardBody>
          </Card>
        </section>
        <Card>
          <CardHeader
            title="Repayment schedule"
            subtitle="Schedule returned by the backend. The browser does not recalculate accruals."
          />
          <div className="overflow-x-auto">
            <table className="premium-table">
              <thead>
                <tr>
                  <th>#</th>
                  <th>Due</th>
                  <th>Principal</th>
                  <th>Interest</th>
                  <th>Management fee</th>
                  <th>Total</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {schedule.slice(0, 24).map((r, i) => (
                  <tr key={r.id || i}>
                    <td className="font-black">
                      {r.installmentNumber ?? i + 1}
                    </td>
                    <td>{r.dueDate ? formatDate(r.dueDate, locale) : "—"}</td>
                    <td className="font-bold">
                      {formatCurrency(
                        n(r.principalComponent ?? r.principal),
                        currency,
                        locale,
                      )}
                    </td>
                    <td>
                      {formatCurrency(
                        n(r.interestComponent ?? r.interest),
                        currency,
                        locale,
                      )}
                    </td>
                    <td>
                      {formatCurrency(
                        n(r.managementFeeComponent ?? r.managementFee),
                        currency,
                        locale,
                      )}
                    </td>
                    <td className="font-black">
                      {formatCurrency(
                        n(r.amount ?? r.totalDue ?? r.installmentAmount),
                        currency,
                        locale,
                      )}
                    </td>
                    <td>
                      {r.paid ? (
                        <span className="premium-badge bg-emerald-50 text-emerald-700">
                          Paid
                        </span>
                      ) : (
                        <span className="premium-badge bg-slate-100 text-slate-600">
                          Open
                        </span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
        <Card>
          <CardHeader
            title="Payment activity"
            subtitle="Posted collection transactions associated with this facility"
          />
          <div className="overflow-x-auto">
            <table className="premium-table">
              <thead>
                <tr>
                  <th>Reference</th>
                  <th>Date</th>
                  <th>Amount</th>
                  <th>Principal</th>
                  <th>Interest</th>
                  <th>Management fee</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {payments.slice(0, 15).map((p, i) => (
                  <tr key={p.id || i}>
                    <td className="font-black">
                      {p.paymentReference ||
                        p.transactionId ||
                        `Payment ${i + 1}`}
                    </td>
                    <td>
                      {p.paidDate
                        ? formatDate(p.paidDate, locale)
                        : p.dueDate
                          ? formatDate(p.dueDate, locale)
                          : "—"}
                    </td>
                    <td className="font-black">
                      {formatCurrency(
                        n(p.amountPaid ?? p.amount),
                        currency,
                        locale,
                      )}
                    </td>
                    <td>
                      {formatCurrency(
                        n(p.principalComponent),
                        currency,
                        locale,
                      )}
                    </td>
                    <td>
                      {formatCurrency(n(p.interestComponent), currency, locale)}
                    </td>
                    <td>
                      {formatCurrency(
                        n(p.managementFeeComponent ?? p.managementFee),
                        currency,
                        locale,
                      )}
                    </td>
                    <td>{p.status || "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      </div>
    </main>
  );
}
function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl bg-slate-50 p-3">
      <div className="text-[9px] font-black uppercase tracking-wider text-slate-400">
        {label}
      </div>
      <div className="mt-1 text-xs font-black text-[#071a2d]">{value}</div>
    </div>
  );
}
