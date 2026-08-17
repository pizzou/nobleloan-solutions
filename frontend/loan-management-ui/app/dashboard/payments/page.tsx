"use client";

import { useCallback, useEffect, useState } from "react";
import { paymentApi, loanApi } from "@/services/api";
import API from "@/services/api";

type Loan = {
  id: number;
  referenceNumber?: string;
  status?: string;
  currency?: string;
  borrower?: {
    fullName?: string;
  };
  borrowerName?: string;
  outstandingBalance?: number;
  nextInstallmentAmount?: number;
};

type Payment = {
  id?: number;
  installmentNumber?: number;
  amount?: number;
  amountPaid?: number;

  principalComponent?: number;
  interestComponent?: number;
  penalty?: number;

  cycleInterestDue?: number;
  cycleInterestRemaining?: number;

  outstandingAfter?: number;

  paid?: boolean;
  status?: string;

  dueDate?: string;
  paidDate?: string;

  paymentMethod?: string;
  transactionId?: string;
  paymentReference?: string;
  channel?: string;
  notes?: string;

  daysLate?: number;
  interestCalculationDate?: string;
};

type PaymentTransaction = {
  id?: number;
  transactionReference?: string;
  amount?: number;
  principalComponent?: number;
  interestComponent?: number;
  penaltyComponent?: number;
  unappliedAmount?: number;
  paymentMethod?: string;
  channel?: string;
  status?: string;
  reversed?: boolean;
  createdAt?: string;
};

const money = (value: unknown, currency = "RWF") => {
  const n = Number(value ?? 0);

  return new Intl.NumberFormat("en-RW", {
    style: "currency",
    currency,
    maximumFractionDigits: 2,
  }).format(Number.isFinite(n) ? n : 0);
};

const num = (v: unknown) => {
  const n = Number(v ?? 0);
  return Number.isFinite(n) ? n : 0;
};

const date = (value?: string) => {
  if (!value) return "—";

  const d = new Date(value);

  if (Number.isNaN(d.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat("en-RW", {
    year: "numeric",
    month: "short",
    day: "2-digit",
  }).format(d);
};

const idempotencyKey = () => `WEB-${Date.now()}-${crypto.randomUUID()}`;

export default function PaymentsPage() {
  const [loanIdInput, setLoanIdInput] = useState("");
  const [loan, setLoan] = useState<Loan | null>(null);
  const [payments, setPayments] = useState<Payment[]>([]);

  const [loadingLoan, setLoadingLoan] = useState(false);
  const [saving, setSaving] = useState(false);

  const [amount, setAmount] = useState("");
  const [paymentMethod, setPaymentMethod] = useState("BANK_TRANSFER");
  const [transactionId, setTransactionId] = useState("");
  const [channel, setChannel] = useState("MANUAL");
  const [notes, setNotes] = useState("");

  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  const loadLoan = useCallback(async (id: number) => {
    setLoadingLoan(true);
    setError("");
    setSuccess("");

    try {
      const [loanResult, scheduleResult] = await Promise.all([
        loanApi.get(id),
        paymentApi.schedule(id),
      ]);

      setLoan(loanResult as Loan);

      setPayments(
        Array.isArray(scheduleResult) ? (scheduleResult as Payment[]) : [],
      );
    } catch (err: any) {
      setLoan(null);
      setPayments([]);

      setError(err?.message || "Unable to load the loan.");
    } finally {
      setLoadingLoan(false);
    }
  }, []);

  const loadFromInput = async () => {
    const id = Number(loanIdInput.trim());

    if (!Number.isInteger(id) || id <= 0) {
      setError("Enter a valid loan ID.");
      return;
    }

    await loadLoan(id);
  };

  const recordPayment = async () => {
    if (!loan) {
      setError("Load a loan before recording a payment.");
      return;
    }

    const paymentAmount = Number(amount);

    if (!Number.isFinite(paymentAmount) || paymentAmount <= 0) {
      setError("Payment amount must be greater than zero.");
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
          paymentMethod,
          transactionId: transactionId.trim(),
          channel,
          notes: notes.trim(),
        },
        idempotencyKey(),
      );

      setSuccess("Payment recorded successfully.");

      setAmount("");
      setTransactionId("");
      setNotes("");

      await loadLoan(loan.id);
    } catch (err: any) {
      setError(err?.message || "Payment could not be recorded.");
    } finally {
      setSaving(false);
    }
  };

  const currency = loan?.currency || "RWF";

  return (
    <main className="min-h-screen bg-[#F6F8FB]">
      <div className="mx-auto max-w-[1500px] px-4 py-6 sm:px-6 lg:px-8">
        <div className="mb-7">
          <div className="text-[11px] font-black uppercase tracking-[0.2em] text-emerald-700">
            Collections
          </div>

          <h1 className="mt-2 text-3xl font-black tracking-tight text-slate-950">
            Payments
          </h1>

          <p className="mt-2 text-sm text-slate-500">
            Record payments with idempotency protection, review allocation and
            reverse posted transactions when authorized.
          </p>
        </div>

        {error && (
          <div className="mb-5 rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-semibold text-red-700">
            {error}
          </div>
        )}

        {success && (
          <div className="mb-5 rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-semibold text-emerald-700">
            {success}
          </div>
        )}

        <section className="mb-6 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="flex flex-col gap-3 sm:flex-row">
            <input
              type="number"
              min="1"
              value={loanIdInput}
              onChange={(e) => setLoanIdInput(e.target.value)}
              placeholder="Loan ID"
              className="flex-1 rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm outline-none focus:border-emerald-500"
            />

            <button
              onClick={loadFromInput}
              disabled={loadingLoan}
              className="rounded-xl bg-slate-950 px-6 py-3 text-sm font-black text-white disabled:opacity-50"
            >
              {loadingLoan ? "Loading…" : "Load loan"}
            </button>
          </div>
        </section>

        {loan && (
          <>
            <section className="mb-6 grid gap-4 md:grid-cols-4">
              <Metric
                label="Loan"
                value={loan.referenceNumber || `#${loan.id}`}
              />

              <Metric
                label="Outstanding"
                value={money(loan.outstandingBalance, currency)}
              />

              <Metric
                label="Next installment"
                value={money(loan.nextInstallmentAmount, currency)}
              />

              <Metric label="Status" value={loan.status || "UNKNOWN"} />
            </section>

            <div className="grid gap-6 xl:grid-cols-[420px_1fr]">
              <div className="space-y-6">
                <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
                  <div className="mb-5">
                    <div className="text-[10px] font-black uppercase tracking-[0.16em] text-emerald-700">
                      New transaction
                    </div>

                    <h2 className="mt-1 text-xl font-black text-slate-950">
                      Record payment
                    </h2>
                  </div>

                  <div className="space-y-4">
                    <Field
                      label="Amount"
                      value={amount}
                      onChange={setAmount}
                      type="number"
                      placeholder="0.00"
                    />

                    <div>
                      <label className="mb-2 block text-xs font-black text-slate-600">
                        Payment method
                      </label>

                      <select
                        value={paymentMethod}
                        onChange={(e) => setPaymentMethod(e.target.value)}
                        className="w-full rounded-xl border border-slate-200 px-4 py-3 text-sm font-semibold"
                      >
                        <option value="BANK_TRANSFER">Bank transfer</option>
                        <option value="CASH">Cash</option>
                        <option value="MOBILE_MONEY">Mobile money</option>
                        <option value="CARD">Card</option>
                        <option value="CHEQUE">Cheque</option>
                      </select>
                    </div>

                    <Field
                      label="Transaction ID"
                      value={transactionId}
                      onChange={setTransactionId}
                      placeholder="External transaction reference"
                    />

                    <Field
                      label="Channel"
                      value={channel}
                      onChange={setChannel}
                      placeholder="MANUAL / MTN / BANK / CARD"
                    />

                    <div>
                      <label className="mb-2 block text-xs font-black text-slate-600">
                        Notes
                      </label>

                      <textarea
                        rows={3}
                        value={notes}
                        onChange={(e) => setNotes(e.target.value)}
                        className="w-full resize-none rounded-xl border border-slate-200 px-4 py-3 text-sm outline-none focus:border-emerald-500"
                      />
                    </div>

                    <div className="rounded-xl bg-slate-50 p-4 text-xs text-slate-500">
                      Payment allocation is handled by the backend. The
                      transaction is protected by an idempotency key and posted
                      to accounting before the operation completes.
                    </div>

                    <button
                      onClick={recordPayment}
                      disabled={saving}
                      className="w-full rounded-xl bg-[#0D6B3E] px-5 py-3.5 text-sm font-black text-white hover:bg-[#095832] disabled:opacity-50"
                    >
                      {saving ? "Posting payment…" : "Record payment"}
                    </button>
                  </div>
                </section>
              </div>

              <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
                <div className="border-b border-slate-200 p-6">
                  <div className="text-[10px] font-black uppercase tracking-[0.16em] text-emerald-700">
                    Loan ledger
                  </div>

                  <h2 className="mt-1 text-xl font-black text-slate-950">
                    Payment schedule & allocation
                  </h2>
                </div>

                <div className="overflow-x-auto">
                  <table className="min-w-[1050px] w-full">
                    <thead className="bg-slate-50">
                      <tr className="border-b border-slate-200 text-left text-[10px] font-black uppercase tracking-wider text-slate-400">
                        <th className="px-5 py-4">Installment</th>
                        <th className="px-5 py-4">Due</th>
                        <th className="px-5 py-4">Amount</th>
                        <th className="px-5 py-4">Principal</th>
                        <th className="px-5 py-4">Interest</th>
                        <th className="px-5 py-4">Penalty</th>
                        <th className="px-5 py-4">Balance</th>
                        <th className="px-5 py-4">Status</th>
                      </tr>
                    </thead>

                    <tbody>
                      {payments.length === 0 ? (
                        <tr>
                          <td
                            colSpan={8}
                            className="px-5 py-16 text-center text-sm text-slate-400"
                          >
                            No payment schedule found.
                          </td>
                        </tr>
                      ) : (
                        payments.map((payment, index) => (
                          <tr
                            key={payment.id ?? index}
                            className="border-b border-slate-100 last:border-0"
                          >
                            <td className="px-5 py-4">
                              <div className="font-black text-slate-800">
                                #{payment.installmentNumber ?? index + 1}
                              </div>

                              {payment.paymentReference && (
                                <div className="mt-1 text-[10px] text-slate-400">
                                  {payment.paymentReference}
                                </div>
                              )}
                            </td>

                            <td className="px-5 py-4 text-sm font-semibold text-slate-600">
                              {date(payment.dueDate)}
                            </td>

                            <td className="px-5 py-4 text-sm font-black text-slate-900">
                              {money(payment.amount, currency)}
                            </td>

                            <td className="px-5 py-4 text-sm font-bold text-slate-700">
                              {money(payment.principalComponent, currency)}
                            </td>

                            <td className="px-5 py-4 text-sm font-bold text-slate-700">
                              {money(payment.interestComponent, currency)}
                            </td>

                            <td className="px-5 py-4 text-sm font-bold text-slate-700">
                              {money(payment.penalty, currency)}
                            </td>

                            <td className="px-5 py-4 text-sm font-black text-slate-900">
                              {money(payment.outstandingAfter, currency)}
                            </td>

                            <td className="px-5 py-4">
                              <span className="rounded-full border border-slate-200 bg-slate-50 px-2.5 py-1 text-[10px] font-black uppercase text-slate-600">
                                {payment.status ||
                                  (payment.paid ? "PAID" : "PENDING")}
                              </span>

                              {num(payment.daysLate) > 0 && (
                                <div className="mt-1 text-[10px] font-bold text-red-600">
                                  {payment.daysLate} days late
                                </div>
                              )}
                            </td>
                          </tr>
                        ))
                      )}
                    </tbody>
                  </table>
                </div>
              </section>
            </div>
          </>
        )}
      </div>
    </main>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="text-[10px] font-black uppercase tracking-[0.16em] text-slate-400">
        {label}
      </div>

      <div className="mt-3 truncate text-xl font-black text-slate-950">
        {value}
      </div>
    </div>
  );
}

function Field({
  label,
  value,
  onChange,
  type = "text",
  placeholder,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: string;
  placeholder?: string;
}) {
  return (
    <div>
      <label className="mb-2 block text-xs font-black text-slate-600">
        {label}
      </label>

      <input
        type={type}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        className="w-full rounded-xl border border-slate-200 px-4 py-3 text-sm outline-none focus:border-emerald-500"
      />
    </div>
  );
}
