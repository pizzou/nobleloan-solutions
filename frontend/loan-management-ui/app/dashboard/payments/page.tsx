"use client";

import { Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";

import { loanApi, paymentApi } from "@/services/api";
import { Button } from "@/components/ui/Button";
import { Card, CardBody, CardHeader, StatCard } from "@/components/ui/Card";
import { formatCurrency, formatDate } from "@/lib/utils";

/* -------------------------------------------------------------------------- */
/* Helpers                                                                    */
/* -------------------------------------------------------------------------- */

const n = (v: unknown): number => {
  const value = Number(v ?? 0);
  return Number.isFinite(value) ? value : 0;
};

const createIdempotencyKey = (): string => {
  if (
    typeof crypto !== "undefined" &&
    typeof crypto.randomUUID === "function"
  ) {
    return `WEB-${Date.now()}-${crypto.randomUUID()}`;
  }

  return `WEB-${Date.now()}-${Math.random().toString(36).slice(2)}`;
};

/* -------------------------------------------------------------------------- */
/* Types                                                                      */
/* -------------------------------------------------------------------------- */

type PaymentScheduleItem = {
  id?: number | string;
  installmentNumber?: number;
  dueDate?: string;
  amount?: number;
  totalDue?: number;
  principalComponent?: number;
  interestComponent?: number;
  managementFeeComponent?: number;
  managementFee?: number;
  penaltyComponent?: number;
  penalty?: number;
  paid?: boolean;
  status?: string;
};

type Loan = {
  id: number;
  referenceNumber?: string;
  currency?: string;
  outstandingBalance?: number;

  borrower?: {
    firstName?: string;
    lastName?: string;
    name?: string;
  };

  [key: string]: unknown;
};

/* -------------------------------------------------------------------------- */
/* Loading state                                                              */
/* -------------------------------------------------------------------------- */

function PaymentsLoading() {
  return (
    <main className="premium-page pb-14">
      <div className="mx-auto max-w-[1500px] space-y-6 px-4 py-6 sm:px-6 lg:px-8">
        <section>
          <div className="premium-eyebrow">Treasury & collections</div>

          <div className="mt-3 h-10 w-80 animate-pulse rounded-lg bg-slate-200" />

          <div className="mt-3 h-5 max-w-2xl animate-pulse rounded bg-slate-100" />
        </section>

        <section className="grid gap-4 sm:grid-cols-3">
          {[1, 2, 3].map((item) => (
            <div
              key={item}
              className="h-32 animate-pulse rounded-2xl border border-slate-200 bg-white"
            />
          ))}
        </section>

        <div className="h-20 animate-pulse rounded-2xl border border-slate-200 bg-white" />
      </div>
    </main>
  );
}

/* -------------------------------------------------------------------------- */
/* Main payments application                                                  */
/* -------------------------------------------------------------------------- */

function PaymentsContent() {
  const params = useSearchParams();

  const [loanId, setLoanId] = useState("");
  const [loan, setLoan] = useState<Loan | null>(null);
  const [schedule, setSchedule] = useState<PaymentScheduleItem[]>([]);

  const [amount, setAmount] = useState("");
  const [method, setMethod] = useState("BANK_TRANSFER");
  const [transactionId, setTransactionId] = useState("");
  const [channel, setChannel] = useState("MANUAL");
  const [notes, setNotes] = useState("");

  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  /* ------------------------------------------------------------------------ */
  /* Read loanId from URL                                                     */
  /* ------------------------------------------------------------------------ */

  useEffect(() => {
    const queryLoanId = params.get("loanId");

    if (queryLoanId) {
      setLoanId(queryLoanId);
    }
  }, [params]);

  /* ------------------------------------------------------------------------ */
  /* Load loan + schedule                                                     */
  /* ------------------------------------------------------------------------ */

  async function load() {
    const id = Number(loanId);

    if (!Number.isInteger(id) || id <= 0) {
      setError("Enter a valid loan ID.");
      return;
    }

    setLoading(true);
    setError("");
    setSuccess("");

    try {
      const [loanResponse, scheduleResponse] = await Promise.all([
        loanApi.get(id),
        paymentApi.schedule(id),
      ]);

      const normalizedLoan = loanResponse as Loan;

      const normalizedSchedule = Array.isArray(scheduleResponse)
        ? scheduleResponse
        : scheduleResponse?.content || scheduleResponse?.items || [];

      setLoan(normalizedLoan);

      setSchedule(Array.isArray(normalizedSchedule) ? normalizedSchedule : []);
    } catch (e: any) {
      setLoan(null);
      setSchedule([]);

      setError(e?.message || "Unable to load the facility. Please try again.");
    } finally {
      setLoading(false);
    }
  }

  /* ------------------------------------------------------------------------ */
  /* Record payment                                                           */
  /* ------------------------------------------------------------------------ */

  async function save() {
    if (!loan) {
      setError("Load a facility before posting a payment.");
      return;
    }

    const paymentAmount = Number(amount);

    if (!Number.isFinite(paymentAmount) || paymentAmount <= 0) {
      setError("Enter a payment amount greater than zero.");
      return;
    }

    setSaving(true);
    setError("");
    setSuccess("");

    try {
      await paymentApi.record(
        loan.id,
        {
          amount: paymentAmount,
          paymentMethod: method,
          transactionId: transactionId.trim(),
          channel,
          notes: notes.trim(),
        },
        createIdempotencyKey(),
      );

      setSuccess(
        "Payment posted successfully. The authoritative loan schedule has been refreshed.",
      );

      setAmount("");
      setTransactionId("");
      setNotes("");

      await load();
    } catch (e: any) {
      setError(
        e?.message || "Payment could not be posted. No changes were confirmed.",
      );
    } finally {
      setSaving(false);
    }
  }

  /* ------------------------------------------------------------------------ */
  /* Derived values                                                           */
  /* ------------------------------------------------------------------------ */

  const currency = loan?.currency || "RWF";

  const borrower = loan
    ? loan.borrower?.name ||
      `${loan.borrower?.firstName || ""} ${
        loan.borrower?.lastName || ""
      }`.trim() ||
      "Unknown borrower"
    : "—";

  const openInstallments = schedule.filter(
    (item) =>
      item.paid !== true && String(item.status || "").toUpperCase() !== "PAID",
  );

  const dueAmount = openInstallments.reduce(
    (sum, item) => sum + n(item.amount ?? item.totalDue),
    0,
  );

  const outstanding = n(loan?.outstandingBalance);

  /* ------------------------------------------------------------------------ */
  /* UI                                                                       */
  /* ------------------------------------------------------------------------ */

  return (
    <main className="premium-page min-h-screen pb-14">
      <div className="mx-auto max-w-[1500px] space-y-6 px-4 py-6 sm:px-6 lg:px-8">
        {/* ------------------------------------------------------------------ */}
        {/* Page header                                                        */}
        {/* ------------------------------------------------------------------ */}

        <section className="relative overflow-hidden rounded-3xl border border-slate-200 bg-white p-6 shadow-sm sm:p-8">
          <div className="absolute right-0 top-0 h-48 w-48 rounded-full bg-emerald-50 blur-3xl" />

          <div className="relative">
            <div className="premium-eyebrow">Treasury & collections</div>

            <div className="mt-2 flex flex-col justify-between gap-5 lg:flex-row lg:items-end">
              <div>
                <h1 className="premium-section-title">
                  Payment command centre
                </h1>

                <p className="premium-section-copy max-w-3xl">
                  Manage loan collections, review the authoritative repayment
                  schedule and post payments with idempotency protection.
                </p>
              </div>

              {loan && (
                <div className="rounded-2xl border border-slate-200 bg-slate-50 px-5 py-4 text-right">
                  <div className="text-[9px] font-black uppercase tracking-[0.18em] text-slate-400">
                    Facility
                  </div>

                  <div className="mt-1 text-lg font-black text-[#0b2944]">
                    {loan.referenceNumber || `Loan #${loan.id}`}
                  </div>

                  <div className="mt-1 text-xs font-semibold text-slate-500">
                    {borrower}
                  </div>
                </div>
              )}
            </div>
          </div>
        </section>

        {/* ------------------------------------------------------------------ */}
        {/* KPI cards                                                          */}
        {/* ------------------------------------------------------------------ */}

        <section className="grid gap-4 sm:grid-cols-3">
          <StatCard
            icon={<span>₣</span>}
            label="Selected facility"
            value={loan ? loan.referenceNumber || `#${loan.id}` : "—"}
            sub={borrower}
            color="#0b2944"
          />

          <StatCard
            icon={<span>◆</span>}
            label="Outstanding principal"
            value={loan ? formatCurrency(outstanding, currency, "en-RW") : "—"}
            sub="Current principal balance"
            color="#087f74"
          />

          <StatCard
            icon={<span>◷</span>}
            label="Scheduled open"
            value={formatCurrency(dueAmount, currency, "en-RW")}
            sub={`${openInstallments.length} open installments`}
            color="#c9a227"
          />
        </section>

        {/* ------------------------------------------------------------------ */}
        {/* Facility selector                                                  */}
        {/* ------------------------------------------------------------------ */}

        <Card>
          <CardBody>
            <div className="grid gap-3 sm:grid-cols-[1fr_auto]">
              <div>
                <label
                  htmlFor="loan-id"
                  className="mb-2 block text-[9px] font-black uppercase tracking-[0.16em] text-slate-500"
                >
                  Facility reference
                </label>

                <input
                  id="loan-id"
                  className="premium-input"
                  value={loanId}
                  onChange={(event) => setLoanId(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter") {
                      void load();
                    }
                  }}
                  inputMode="numeric"
                  placeholder="Enter loan ID"
                />
              </div>

              <div className="flex items-end">
                <Button
                  className="w-full sm:w-auto"
                  onClick={() => void load()}
                  loading={loading}
                >
                  Load facility
                </Button>
              </div>
            </div>
          </CardBody>
        </Card>

        {/* ------------------------------------------------------------------ */}
        {/* Alerts                                                              */}
        {/* ------------------------------------------------------------------ */}

        {error && (
          <div
            role="alert"
            className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-xs font-bold text-red-800"
          >
            {error}
          </div>
        )}

        {success && (
          <div
            role="status"
            className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-xs font-bold text-emerald-800"
          >
            {success}
          </div>
        )}

        {/* ------------------------------------------------------------------ */}
        {/* Main workspace                                                      */}
        {/* ------------------------------------------------------------------ */}

        {loan && (
          <div className="grid gap-5 xl:grid-cols-[0.7fr_1.3fr]">
            {/* -------------------------------------------------------------- */}
            {/* Payment form                                                    */}
            {/* -------------------------------------------------------------- */}

            <Card>
              <CardHeader
                title="Post collection"
                subtitle="Review the transaction carefully before submitting it to the lending engine."
              />

              <CardBody>
                <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
                  <div className="text-[9px] font-black uppercase tracking-[0.16em] text-slate-400">
                    Current principal
                  </div>

                  <div className="mt-1 text-2xl font-black tracking-tight text-[#0b2944]">
                    {formatCurrency(outstanding, currency, "en-RW")}
                  </div>

                  <div className="mt-1 text-xs text-slate-500">{borrower}</div>
                </div>

                {/* Amount */}
                <label className="mt-5 block">
                  <span className="mb-1.5 block text-[9px] font-black uppercase tracking-wider text-slate-500">
                    Payment amount
                  </span>

                  <div className="relative">
                    <input
                      className="premium-input pr-20 text-lg font-black"
                      type="number"
                      min="0.01"
                      step="0.01"
                      value={amount}
                      onChange={(event) => setAmount(event.target.value)}
                      placeholder="0.00"
                    />

                    <span className="pointer-events-none absolute right-4 top-1/2 -translate-y-1/2 text-xs font-black text-slate-400">
                      {currency}
                    </span>
                  </div>
                </label>

                {/* Method + channel */}
                <div className="mt-4 grid gap-3 sm:grid-cols-2">
                  <label>
                    <span className="mb-1 block text-[9px] font-black uppercase tracking-wider text-slate-500">
                      Method
                    </span>

                    <select
                      className="premium-input"
                      value={method}
                      onChange={(event) => setMethod(event.target.value)}
                    >
                      <option value="BANK_TRANSFER">Bank transfer</option>

                      <option value="CASH">Cash</option>

                      <option value="MOBILE_MONEY">Mobile money</option>

                      <option value="CHEQUE">Cheque</option>

                      <option value="CARD">Card</option>
                    </select>
                  </label>

                  <label>
                    <span className="mb-1 block text-[9px] font-black uppercase tracking-wider text-slate-500">
                      Channel
                    </span>

                    <select
                      className="premium-input"
                      value={channel}
                      onChange={(event) => setChannel(event.target.value)}
                    >
                      <option value="MANUAL">Manual</option>

                      <option value="BANK">Bank</option>

                      <option value="MOBILE">Mobile</option>

                      <option value="BRANCH">Branch</option>
                    </select>
                  </label>
                </div>

                {/* Transaction reference */}
                <label className="mt-4 block">
                  <span className="mb-1 block text-[9px] font-black uppercase tracking-wider text-slate-500">
                    Transaction reference
                  </span>

                  <input
                    className="premium-input"
                    value={transactionId}
                    onChange={(event) => setTransactionId(event.target.value)}
                    placeholder="Bank / mobile money / receipt reference"
                  />
                </label>

                {/* Notes */}
                <label className="mt-4 block">
                  <span className="mb-1 block text-[9px] font-black uppercase tracking-wider text-slate-500">
                    Notes
                  </span>

                  <textarea
                    className="premium-input min-h-[90px]"
                    value={notes}
                    onChange={(event) => setNotes(event.target.value)}
                    placeholder="Optional collection notes"
                  />
                </label>

                {/* Submit */}
                <Button
                  className="mt-5 w-full"
                  size="lg"
                  loading={saving}
                  onClick={() => void save()}
                >
                  Post payment
                </Button>

                <div className="mt-4 rounded-xl border border-amber-100 bg-amber-50 px-4 py-3">
                  <p className="text-[10px] leading-5 text-amber-800">
                    Payment allocation, interest, management fee, penalties and
                    future installment recalculation are controlled by the
                    backend lending engine. Nothing is manually calculated in
                    the browser.
                  </p>
                </div>
              </CardBody>
            </Card>

            {/* -------------------------------------------------------------- */}
            {/* Schedule                                                         */}
            {/* -------------------------------------------------------------- */}

            <Card>
              <CardHeader
                title="Installment allocation"
                subtitle="Authoritative repayment schedule returned by the lending engine."
              />

              <div className="overflow-x-auto">
                <table className="premium-table">
                  <thead>
                    <tr>
                      <th>#</th>
                      <th>Due</th>
                      <th>Total</th>
                      <th>Principal</th>
                      <th>Interest</th>
                      <th>Management</th>
                      <th>Status</th>
                    </tr>
                  </thead>

                  <tbody>
                    {schedule.length === 0 ? (
                      <tr>
                        <td colSpan={7} className="py-12 text-center">
                          <div className="text-sm font-black text-slate-500">
                            No repayment schedule available
                          </div>

                          <div className="mt-1 text-xs text-slate-400">
                            The backend returned no installments for this
                            facility.
                          </div>
                        </td>
                      </tr>
                    ) : (
                      schedule.slice(0, 24).map((item, index) => {
                        const paid =
                          item.paid === true ||
                          String(item.status || "").toUpperCase() === "PAID";

                        return (
                          <tr
                            key={
                              item.id ||
                              `${item.installmentNumber || index}-${item.dueDate || ""}`
                            }
                          >
                            <td>{item.installmentNumber ?? index + 1}</td>

                            <td>
                              {item.dueDate
                                ? formatDate(item.dueDate, "en-RW")
                                : "—"}
                            </td>

                            <td className="font-black">
                              {formatCurrency(
                                n(item.amount ?? item.totalDue),
                                currency,
                                "en-RW",
                              )}
                            </td>

                            <td>
                              {formatCurrency(
                                n(item.principalComponent),
                                currency,
                                "en-RW",
                              )}
                            </td>

                            <td>
                              {formatCurrency(
                                n(item.interestComponent),
                                currency,
                                "en-RW",
                              )}
                            </td>

                            <td>
                              {formatCurrency(
                                n(
                                  item.managementFeeComponent ??
                                    item.managementFee,
                                ),
                                currency,
                                "en-RW",
                              )}
                            </td>

                            <td>
                              <span
                                className={`premium-badge ${
                                  paid
                                    ? "bg-emerald-50 text-emerald-700"
                                    : "bg-slate-100 text-slate-600"
                                }`}
                              >
                                {paid ? "Paid" : "Open"}
                              </span>
                            </td>
                          </tr>
                        );
                      })
                    )}
                  </tbody>
                </table>
              </div>

              {schedule.length > 24 && (
                <div className="border-t border-slate-100 px-5 py-4 text-center text-[10px] font-bold text-slate-400">
                  Showing first 24 installments
                </div>
              )}
            </Card>
          </div>
        )}

        {/* ------------------------------------------------------------------ */}
        {/* Empty state                                                         */}
        {/* ------------------------------------------------------------------ */}

        {!loan && !loading && !error && (
          <section className="rounded-3xl border border-dashed border-slate-300 bg-white px-6 py-16 text-center shadow-sm">
            <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-slate-50 text-xl text-[#0b2944]">
              ₣
            </div>

            <h2 className="mt-5 text-lg font-black text-[#0b2944]">
              Select a lending facility
            </h2>

            <p className="mx-auto mt-2 max-w-md text-sm leading-6 text-slate-500">
              Enter a valid loan ID above to review the borrower, outstanding
              principal, repayment schedule and post a collection.
            </p>
          </section>
        )}
      </div>
    </main>
  );
}

/* -------------------------------------------------------------------------- */
/* Page                                                                       */
/* -------------------------------------------------------------------------- */

export default function PaymentsPage() {
  return (
    <Suspense fallback={<PaymentsLoading />}>
      <PaymentsContent />
    </Suspense>
  );
}
