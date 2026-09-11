"use client";

import { useMemo, useState } from "react";
import {
  calculateContractualSchedule,
  percentageCharge,
  safeRate,
} from "../../../lib/loanRepaymentCalculator";

interface Installment {
  month: number;
  payment: number;
  principal: number;
  interest: number;
  management: number;
  balance: number;
}

const DEFAULT_INTEREST_RATE = 5;
const DEFAULT_MANAGEMENT_RATE = 5;
const DEFAULT_APPLICATION_FEE_RATE = 2;

export default function CalculatorPage() {
  const [amount, setAmount] = useState("500000");
  const [interestRate, setInterestRate] = useState(
    String(DEFAULT_INTEREST_RATE),
  );
  const [managementRate, setManagementRate] = useState(
    String(DEFAULT_MANAGEMENT_RATE),
  );
  const [applicationRate, setApplicationRate] = useState(
    String(DEFAULT_APPLICATION_FEE_RATE),
  );
  const [duration, setDuration] = useState("6");
  const [currency, setCurrency] = useState("RWF");
  const [schedule, setSchedule] = useState<Installment[]>([]);
  const [error, setError] = useState("");

  const summary = useMemo(() => {
    const principal = Number(amount);
    const months = Number(duration);
    if (
      !Number.isFinite(principal) ||
      principal <= 0 ||
      !Number.isInteger(months) ||
      months < 1 ||
      months > 6
    ) {
      return null;
    }

    const interest = safeRate(interestRate, DEFAULT_INTEREST_RATE);
    const management = safeRate(managementRate, DEFAULT_MANAGEMENT_RATE);
    const application = safeRate(applicationRate, DEFAULT_APPLICATION_FEE_RATE);
    const contractual = calculateContractualSchedule(
      principal,
      months,
      interest,
      management,
    );
    const applicationFee = percentageCharge(principal, application);

    return {
      principal,
      months,
      interestRate: interest,
      managementRate: management,
      applicationRate: application,
      interest: contractual.interest,
      management: contractual.management,
      totalScheduledRepayable: contractual.total,
      applicationFee,
      totalCostOfCredit:
        contractual.interest + contractual.management + applicationFee,
      netDisbursement: principal - applicationFee,
      firstInstallment: contractual.firstInstallment,
    };
  }, [amount, duration, interestRate, managementRate, applicationRate]);

  const calculate = () => {
    if (!summary) {
      setError("Enter a valid principal and a term from 1 to 6 months.");
      setSchedule([]);
      return;
    }
    setError("");

    let balanceCents = BigInt(Math.round(summary.principal * 100));
    const rateScale = 9n;
    const denominator = 100n * 10n ** rateScale;
    const interestUnits = BigInt(
      Math.round(summary.interestRate * 10 ** Number(rateScale)),
    );
    const managementUnits = BigInt(
      Math.round(summary.managementRate * 10 ** Number(rateScale)),
    );
    const rows: Installment[] = [];

    const divideHalfUp = (n: bigint, d: bigint) => {
      const q = n / d;
      const r = n % d;
      return r * 2n >= d ? q + 1n : q;
    };

    for (let month = 1; month <= summary.months; month += 1) {
      const remainingInstallments = summary.months - month + 1;
      const principalCents =
        remainingInstallments === 1
          ? balanceCents
          : divideHalfUp(balanceCents, BigInt(remainingInstallments));
      const interestCents = divideHalfUp(
        balanceCents * interestUnits,
        denominator,
      );
      const managementCents = divideHalfUp(
        balanceCents * managementUnits,
        denominator,
      );
      const paymentCents = principalCents + interestCents + managementCents;
      balanceCents -= principalCents;

      rows.push({
        month,
        payment: Number(paymentCents) / 100,
        principal: Number(principalCents) / 100,
        interest: Number(interestCents) / 100,
        management: Number(managementCents) / 100,
        balance: Number(balanceCents) / 100,
      });
    }
    setSchedule(rows);
  };

  const fmt = (n: number) =>
    `${currency} ${n.toLocaleString("en-RW", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

  const exportCSV = () => {
    const rows = [
      [
        "Installment",
        "Payment",
        "Principal",
        "Interest",
        "Management fee",
        "Remaining principal",
      ],
      ...schedule.map((s) => [
        String(s.month),
        s.payment.toFixed(2),
        s.principal.toFixed(2),
        s.interest.toFixed(2),
        s.management.toFixed(2),
        s.balance.toFixed(2),
      ]),
    ];
    const csv = rows
      .map((row) =>
        row.map((cell) => `"${cell.replaceAll('"', '""')}"`).join(","),
      )
      .join("\n");
    const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = "contractual-loan-schedule.csv";
    anchor.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="space-y-6 max-w-5xl">
      <div>
        <h1 className="text-xl font-bold text-gray-900">Loan Calculator</h1>
        <p className="text-sm text-gray-500">
          Contractual declining-principal schedule. Annual-rate/EMI calculations
          are intentionally not used.
        </p>
      </div>

      <div className="bg-white rounded-2xl border border-gray-100 p-6">
        <div className="grid grid-cols-2 lg:grid-cols-7 gap-4 mb-6">
          <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wide">
            Currency
            <select
              value={currency}
              onChange={(e) => setCurrency(e.target.value)}
              className="mt-2 w-full border border-gray-200 rounded-xl px-3 py-2.5 text-sm"
            >
              {["RWF", "USD", "EUR", "GBP", "KES", "UGX", "TZS"].map((c) => (
                <option key={c}>{c}</option>
              ))}
            </select>
          </label>
          <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wide lg:col-span-2">
            Principal amount
            <input
              type="number"
              min="500000"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              className="mt-2 w-full border border-gray-200 rounded-xl px-3 py-2.5 text-sm"
            />
          </label>
          <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wide">
            Monthly interest %
            <input
              type="number"
              min="0"
              step="0.001"
              value={interestRate}
              onChange={(e) => setInterestRate(e.target.value)}
              className="mt-2 w-full border border-gray-200 rounded-xl px-3 py-2.5 text-sm"
            />
          </label>
          <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wide">
            Monthly management %
            <input
              type="number"
              min="0"
              step="0.001"
              value={managementRate}
              onChange={(e) => setManagementRate(e.target.value)}
              className="mt-2 w-full border border-gray-200 rounded-xl px-3 py-2.5 text-sm"
            />
          </label>
          <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wide">
            Application fee %
            <input
              type="number"
              min="0"
              step="0.001"
              value={applicationRate}
              onChange={(e) => setApplicationRate(e.target.value)}
              className="mt-2 w-full border border-gray-200 rounded-xl px-3 py-2.5 text-sm"
            />
          </label>
          <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wide">
            Term (months)
            <input
              type="number"
              min="1"
              max="6"
              value={duration}
              onChange={(e) => setDuration(e.target.value)}
              className="mt-2 w-full border border-gray-200 rounded-xl px-3 py-2.5 text-sm"
            />
          </label>
        </div>

        <button
          onClick={calculate}
          className="w-full bg-green-600 hover:bg-green-700 text-white font-semibold py-3 rounded-xl transition text-sm"
        >
          Calculate contractual schedule
        </button>
        {error && <p className="mt-3 text-sm text-red-600">{error}</p>}
      </div>

      {summary && (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          {[
            ["First installment", fmt(summary.firstInstallment)],
            ["Scheduled repayment", fmt(summary.totalScheduledRepayable)],
            ["Total cost of credit", fmt(summary.totalCostOfCredit)],
            ["Net disbursement", fmt(summary.netDisbursement)],
          ].map(([label, value]) => (
            <div
              key={label}
              className="bg-white rounded-2xl border border-gray-100 p-5"
            >
              <p className="text-gray-400 text-xs mb-1">{label}</p>
              <p className="text-xl font-bold text-gray-900">{value}</p>
            </div>
          ))}
        </div>
      )}

      {summary && (
        <div className="bg-white rounded-2xl border border-gray-100 p-6">
          <div className="grid gap-4 md:grid-cols-4 text-sm">
            <div>
              <span className="text-gray-500">Principal</span>
              <div className="font-semibold">{fmt(summary.principal)}</div>
            </div>
            <div>
              <span className="text-gray-500">Contractual interest</span>
              <div className="font-semibold">{fmt(summary.interest)}</div>
            </div>
            <div>
              <span className="text-gray-500">Management fee</span>
              <div className="font-semibold">{fmt(summary.management)}</div>
            </div>
            <div>
              <span className="text-gray-500">Application fee</span>
              <div className="font-semibold">{fmt(summary.applicationFee)}</div>
            </div>
          </div>
        </div>
      )}

      {schedule.length > 0 && (
        <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
          <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
            <h2 className="font-semibold text-gray-800 text-sm">
              Contractual schedule ({schedule.length} installments)
            </h2>
            <button
              onClick={exportCSV}
              className="text-xs font-medium text-green-700 border border-green-200 bg-green-50 px-3 py-1.5 rounded-xl"
            >
              Export CSV
            </button>
          </div>
          <div className="overflow-x-auto max-h-[30rem] overflow-y-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 sticky top-0">
                <tr>
                  {[
                    "Installment",
                    "Payment",
                    "Principal",
                    "Interest",
                    "Management fee",
                    "Balance",
                  ].map((h) => (
                    <th
                      key={h}
                      className="px-5 py-3 text-left text-xs font-semibold text-gray-500 uppercase"
                    >
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {schedule.map((s) => (
                  <tr key={s.month}>
                    <td className="px-5 py-3 font-medium">{s.month}</td>
                    <td className="px-5 py-3 font-semibold">
                      {fmt(s.payment)}
                    </td>
                    <td className="px-5 py-3">{fmt(s.principal)}</td>
                    <td className="px-5 py-3">{fmt(s.interest)}</td>
                    <td className="px-5 py-3">{fmt(s.management)}</td>
                    <td className="px-5 py-3 text-gray-500">
                      {fmt(s.balance)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
