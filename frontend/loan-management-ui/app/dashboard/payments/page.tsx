"use client";
import { useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { loanApi, paymentApi } from "@/services/api";
import { Button } from "@/components/ui/Button";
import { Card, CardBody, CardHeader, StatCard } from "@/components/ui/Card";
import { formatCurrency, formatDate } from "@/lib/utils";
const n = (v: unknown) =>
  Number.isFinite(Number(v ?? 0)) ? Number(v ?? 0) : 0;
const key = () => `WEB-${Date.now()}-${crypto.randomUUID()}`;
export default function Payments() {
  const params = useSearchParams();
  const [loanId, setLoanId] = useState(params.get("loanId") || "");
  const [loan, setLoan] = useState<any>(null);
  const [schedule, setSchedule] = useState<any[]>([]);
  const [amount, setAmount] = useState("");
  const [method, setMethod] = useState("BANK_TRANSFER");
  const [transactionId, setTransactionId] = useState("");
  const [channel, setChannel] = useState("MANUAL");
  const [notes, setNotes] = useState("");
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  async function load() {
    const id = Number(loanId);
    if (!Number.isInteger(id) || id <= 0)
      return setError("Enter a valid loan ID.");
    setLoading(true);
    setError("");
    try {
      const [l, s] = await Promise.all([
        loanApi.get(id),
        paymentApi.schedule(id),
      ]);
      setLoan(l);
      setSchedule(Array.isArray(s) ? s : s?.content || s?.items || []);
    } catch (e: any) {
      setLoan(null);
      setSchedule([]);
      setError(e?.message || "Unable to load facility.");
    } finally {
      setLoading(false);
    }
  }
  async function save() {
    if (!loan) return;
    const a = Number(amount);
    if (!Number.isFinite(a) || a <= 0)
      return setError("Enter a payment amount greater than zero.");
    setSaving(true);
    setError("");
    setSuccess("");
    try {
      await paymentApi.record(
        loan.id,
        {
          amount: a,
          paymentMethod: method,
          transactionId: transactionId.trim(),
          channel,
          notes: notes.trim(),
        },
        key(),
      );
      setSuccess("Payment posted successfully.");
      setAmount("");
      setTransactionId("");
      setNotes("");
      await load();
    } catch (e: any) {
      setError(e?.message || "Payment could not be posted.");
    } finally {
      setSaving(false);
    }
  }
  const currency = loan?.currency || "RWF";
  const borrower = loan
    ? `${loan.borrower?.firstName || ""} ${loan.borrower?.lastName || ""}`.trim()
    : "—";
  const due = schedule.filter((x) => !x.paid);
  const dueAmount = due.reduce((s, x) => s + n(x.amount ?? x.totalDue), 0);
  return (
    <main className="premium-page pb-14">
      <div className="mx-auto max-w-[1500px] space-y-6 px-4 py-6 sm:px-6 lg:px-8">
        <section>
          <div className="premium-eyebrow">Treasury & collections</div>
          <h1 className="premium-section-title">Payment command centre</h1>
          <p className="premium-section-copy">
            Post collections with idempotency protection and review the backend
            allocation across principal, interest, management fee and penalties.
          </p>
        </section>
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
            label="Outstanding"
            value={
              loan
                ? formatCurrency(n(loan.outstandingBalance), currency, "en-RW")
                : "—"
            }
            sub="Current principal balance"
            color="#087f74"
          />
          <StatCard
            icon={<span>◷</span>}
            label="Scheduled open"
            value={formatCurrency(dueAmount, currency, "en-RW")}
            sub={`${due.length} open installments`}
            color="#c9a227"
          />
        </section>
        <Card>
          <CardBody>
            <div className="grid gap-3 sm:grid-cols-[1fr_auto]">
              <input
                className="premium-input"
                value={loanId}
                onChange={(e) => setLoanId(e.target.value)}
                placeholder="Enter loan ID or use the facility shortcut"
              />
              <Button onClick={() => void load()} loading={loading}>
                Load facility
              </Button>
            </div>
          </CardBody>
        </Card>
        {error && (
          <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-xs font-bold text-red-800">
            {error}
          </div>
        )}
        {success && (
          <div className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-xs font-bold text-emerald-800">
            {success}
          </div>
        )}
        {loan && (
          <div className="grid gap-5 xl:grid-cols-[.7fr_1.3fr]">
            <Card>
              <CardHeader
                title="Post collection"
                subtitle="A posted transaction is an accounting event. Review before submitting."
              />
              <CardBody>
                <label className="block">
                  <span className="mb-1.5 block text-[9px] font-black uppercase tracking-wider text-slate-500">
                    Amount
                  </span>
                  <input
                    className="premium-input text-lg font-black"
                    type="number"
                    min="0.01"
                    step="0.01"
                    value={amount}
                    onChange={(e) => setAmount(e.target.value)}
                    placeholder="0.00"
                  />
                </label>
                <div className="mt-4 grid gap-3 sm:grid-cols-2">
                  <label>
                    <span className="mb-1 block text-[9px] font-black uppercase tracking-wider text-slate-500">
                      Method
                    </span>
                    <select
                      className="premium-input"
                      value={method}
                      onChange={(e) => setMethod(e.target.value)}
                    >
                      <option>BANK_TRANSFER</option>
                      <option>CASH</option>
                      <option>MOBILE_MONEY</option>
                      <option>CHEQUE</option>
                      <option>CARD</option>
                    </select>
                  </label>
                  <label>
                    <span className="mb-1 block text-[9px] font-black uppercase tracking-wider text-slate-500">
                      Channel
                    </span>
                    <select
                      className="premium-input"
                      value={channel}
                      onChange={(e) => setChannel(e.target.value)}
                    >
                      <option>MANUAL</option>
                      <option>BANK</option>
                      <option>MOBILE</option>
                      <option>BRANCH</option>
                    </select>
                  </label>
                </div>
                <label className="mt-4 block">
                  <span className="mb-1 block text-[9px] font-black uppercase tracking-wider text-slate-500">
                    Transaction reference
                  </span>
                  <input
                    className="premium-input"
                    value={transactionId}
                    onChange={(e) => setTransactionId(e.target.value)}
                  />
                </label>
                <label className="mt-4 block">
                  <span className="mb-1 block text-[9px] font-black uppercase tracking-wider text-slate-500">
                    Notes
                  </span>
                  <textarea
                    className="premium-input min-h-[90px]"
                    value={notes}
                    onChange={(e) => setNotes(e.target.value)}
                  />
                </label>
                <Button
                  className="mt-5 w-full"
                  size="lg"
                  loading={saving}
                  onClick={() => void save()}
                >
                  Post payment
                </Button>
                <p className="mt-3 text-[9px] leading-5 text-slate-400">
                  Allocation and recalculation are performed by the backend. Do
                  not manually split the payment in the browser.
                </p>
              </CardBody>
            </Card>
            <Card>
              <CardHeader
                title="Installment allocation"
                subtitle="The authoritative schedule returned by the lending engine."
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
                    {schedule.slice(0, 24).map((p, i) => (
                      <tr key={p.id || i}>
                        <td>{p.installmentNumber ?? i + 1}</td>
                        <td>
                          {p.dueDate ? formatDate(p.dueDate, "en-RW") : "—"}
                        </td>
                        <td className="font-black">
                          {formatCurrency(
                            n(p.amount ?? p.totalDue),
                            currency,
                            "en-RW",
                          )}
                        </td>
                        <td>
                          {formatCurrency(
                            n(p.principalComponent),
                            currency,
                            "en-RW",
                          )}
                        </td>
                        <td>
                          {formatCurrency(
                            n(p.interestComponent),
                            currency,
                            "en-RW",
                          )}
                        </td>
                        <td>
                          {formatCurrency(
                            n(p.managementFeeComponent ?? p.managementFee),
                            currency,
                            "en-RW",
                          )}
                        </td>
                        <td>
                          <span
                            className={`premium-badge ${p.paid ? "bg-emerald-50 text-emerald-700" : "bg-slate-100 text-slate-600"}`}
                          >
                            {p.paid ? "Paid" : "Open"}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </Card>
          </div>
        )}
      </div>
    </main>
  );
}
