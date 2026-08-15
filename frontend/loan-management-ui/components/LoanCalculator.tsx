"use client";

import { useEffect, useMemo, useState } from "react";

export interface CalculatorProduct {
  title: string;
  interestRate?: string;
  rateType?: string;
  term?: string;
  maxAmount?: string;
}

type Props = {
  currency: string;
  primary: string;
  accent: string;
  products: CalculatorProduct[];
  defaultInterest?: number | string | null;
  defaultManagementFee?: number | string | null;
  processingFee?: number | string | null;
};

const money = (value: number, currency: string) =>
  `${currency} ${Math.max(0, value).toLocaleString(undefined, {
    maximumFractionDigits: 0,
  })}`;

export default function LoanCalculator({
  currency,
  primary,
  accent,
  products,
  defaultInterest,
  defaultManagementFee,
  processingFee,
}: Props) {
  const firstProduct = products[0];
  const defaultRate = Number(defaultInterest ?? 5);
  const defaultFee = Number(defaultManagementFee ?? 5);
  const defaultProcessing = Number(processingFee ?? 2);

  const [amount, setAmount] = useState(1000000);
  const [months, setMonths] = useState(3);
  const [interestRate, setInterestRate] = useState(
    Number.isFinite(defaultRate) ? defaultRate : 5,
  );
  const [managementFeeRate, setManagementFeeRate] = useState(
    Number.isFinite(defaultFee) ? defaultFee : 5,
  );
  const [product, setProduct] = useState(firstProduct?.title ?? "");

  const selectedProduct = products.find((item) => item.title === product) ?? firstProduct;

  useEffect(() => {
    if (!selectedProduct) return;
    const nextInterest = Number(selectedProduct.monthlyInterestRate ?? selectedProduct.interestRate ?? defaultRate);
    const nextManagement = Number(selectedProduct.monthlyManagementFeeRate ?? defaultFee);
    if (Number.isFinite(nextInterest)) setInterestRate(nextInterest);
    if (Number.isFinite(nextManagement)) setManagementFeeRate(nextManagement);
  }, [product, selectedProduct, defaultFee, defaultRate]);

  const result = useMemo(() => {
    const principal = Math.max(0, Number(amount) || 0);
    const term = Math.max(1, Number(months) || 1);
    const monthlyInterest = principal * (Math.max(0, interestRate) / 100);
    const monthlyManagement =
      principal * (Math.max(0, managementFeeRate) / 100);
    const monthlyPayment =
      principal / term + monthlyInterest + monthlyManagement;
    const totalInterest = monthlyInterest * term;
    const totalManagement = monthlyManagement * term;
    const totalFees = principal * (Math.max(0, defaultProcessing) / 100);
    const totalRepayment = principal + totalInterest + totalManagement;

    return {
      monthlyInterest,
      monthlyManagement,
      monthlyPayment,
      totalInterest,
      totalManagement,
      totalFees,
      totalRepayment,
    };
  }, [amount, months, interestRate, managementFeeRate, defaultProcessing]);

  return (
    <div className="overflow-hidden rounded-[2rem] border border-slate-200 bg-white shadow-[0_25px_80px_rgba(15,23,42,0.10)]">
      <div
        className="px-6 py-6 text-white sm:px-8"
        style={{
          background: `linear-gradient(135deg, ${primary}, ${primary}E8)`,
        }}
      >
        <div className="text-[11px] font-black uppercase tracking-[0.22em] text-white/60">
          Loan planning tool
        </div>
        <h3 className="mt-2 text-2xl font-black tracking-tight">
          Estimate your repayment before you apply
        </h3>
        <p className="mt-2 max-w-2xl text-sm leading-6 text-white/70">
          A planning estimate based on the published product terms. Your final
          offer is subject to eligibility, verification, approval and the
          contract issued by the lender.
        </p>
      </div>

      <div className="grid lg:grid-cols-[1.1fr_0.9fr]">
        <div className="p-6 sm:p-8">
          {products.length > 0 && (
            <label className="block">
              <span className="text-[11px] font-black uppercase tracking-[0.16em] text-slate-500">
                Product
              </span>
              <select
                value={product}
                onChange={(event) => setProduct(event.target.value)}
                className="mt-2 w-full rounded-2xl border border-slate-200 bg-white px-4 py-3 text-sm font-semibold text-slate-900 outline-none transition focus:border-slate-400 focus:ring-4 focus:ring-slate-100"
              >
                {products.map((item) => (
                  <option key={item.title} value={item.title}>
                    {item.title}
                  </option>
                ))}
              </select>
            </label>
          )}

          <div className="mt-6">
            <div className="flex items-center justify-between gap-4">
              <label className="text-[11px] font-black uppercase tracking-[0.16em] text-slate-500">
                Loan amount
              </label>
              <span
                className="text-lg font-black"
                style={{ color: primary }}
              >
                {money(amount, currency)}
              </span>
            </div>
            <input
              type="range"
              min="100000"
              max="20000000"
              step="50000"
              value={amount}
              onChange={(event) => setAmount(Number(event.target.value))}
              className="mt-4 w-full accent-slate-900"
            />
            <input
              type="number"
              min="100000"
              step="10000"
              value={amount}
              onChange={(event) => setAmount(Number(event.target.value))}
              className="mt-3 w-full rounded-2xl border border-slate-200 px-4 py-3 text-sm font-semibold outline-none focus:border-slate-400 focus:ring-4 focus:ring-slate-100"
            />
          </div>

          <div className="mt-6">
            <div className="flex items-center justify-between gap-4">
              <label className="text-[11px] font-black uppercase tracking-[0.16em] text-slate-500">
                Repayment term
              </label>
              <span className="text-lg font-black text-slate-950">
                {months} month{months === 1 ? "" : "s"}
              </span>
            </div>
            <input
              type="range"
              min="1"
              max="24"
              step="1"
              value={months}
              onChange={(event) => setMonths(Number(event.target.value))}
              className="mt-4 w-full accent-slate-900"
            />
            <div className="mt-2 flex justify-between text-[10px] font-bold uppercase tracking-wider text-slate-400">
              <span>1 month</span>
              <span>24 months</span>
            </div>
          </div>

          <div className="mt-6 grid grid-cols-2 gap-4">
            <label className="block">
              <span className="text-[11px] font-black uppercase tracking-[0.16em] text-slate-500">
                Monthly interest %
              </span>
              <input
                type="number"
                min="0"
                max="100"
                step="0.01"
                value={interestRate}
                onChange={(event) => setInterestRate(Number(event.target.value))}
                className="mt-2 w-full rounded-2xl border border-slate-200 px-4 py-3 text-sm font-semibold outline-none focus:border-slate-400 focus:ring-4 focus:ring-slate-100"
              />
            </label>
            <label className="block">
              <span className="text-[11px] font-black uppercase tracking-[0.16em] text-slate-500">
                Monthly management %
              </span>
              <input
                type="number"
                min="0"
                max="100"
                step="0.01"
                value={managementFeeRate}
                onChange={(event) =>
                  setManagementFeeRate(Number(event.target.value))
                }
                className="mt-2 w-full rounded-2xl border border-slate-200 px-4 py-3 text-sm font-semibold outline-none focus:border-slate-400 focus:ring-4 focus:ring-slate-100"
              />
            </label>
          </div>

          <div className="mt-5 rounded-2xl border border-amber-100 bg-amber-50 p-4 text-xs leading-6 text-amber-800">
            Rates shown here are configurable by the lender and may differ by
            product or borrower. This calculator is illustrative and does not
            constitute a credit approval or offer.
          </div>
        </div>

        <div className="border-t border-slate-100 bg-slate-50 p-6 sm:p-8 lg:border-l lg:border-t-0">
          <div className="text-[11px] font-black uppercase tracking-[0.18em] text-slate-400">
            Estimated repayment
          </div>
          <div className="mt-3 text-4xl font-black tracking-tight text-slate-950">
            {money(result.monthlyPayment, currency)}
          </div>
          <div className="mt-1 text-xs font-semibold text-slate-500">
            estimated monthly payment
          </div>

          <div className="mt-8 space-y-3">
            {[
              ["Principal", money(amount, currency)],
              ["Interest over term", money(result.totalInterest, currency)],
              [
                "Management fees",
                money(result.totalManagement, currency),
              ],
              ["Processing fee", money(result.totalFees, currency)],
              ["Estimated total repayment", money(result.totalRepayment, currency)],
            ].map(([label, value], index) => (
              <div
                key={label}
                className={`flex items-center justify-between gap-4 py-3 ${
                  index === 4 ? "border-t border-slate-200 pt-5" : "border-b border-slate-200/80"
                }`}
              >
                <span className="text-sm text-slate-500">{label}</span>
                <span
                  className={`text-sm font-black ${
                    index === 4 ? "text-base" : "text-slate-900"
                  }`}
                  style={index === 4 ? { color: primary } : undefined}
                >
                  {value}
                </span>
              </div>
            ))}
          </div>

          <div
            className="mt-8 rounded-2xl p-4"
            style={{ backgroundColor: `${accent}16`, border: `1px solid ${accent}35` }}
          >
            <div className="text-[11px] font-black uppercase tracking-[0.16em]" style={{ color: primary }}>
              Published pricing
            </div>
            <div className="mt-2 grid grid-cols-3 gap-2 text-center">
              <div>
                <div className="text-lg font-black" style={{ color: primary }}>
                  {interestRate.toFixed(2)}%
                </div>
                <div className="text-[10px] font-bold uppercase tracking-wider text-slate-500">Interest</div>
              </div>
              <div>
                <div className="text-lg font-black" style={{ color: primary }}>
                  {managementFeeRate.toFixed(2)}%
                </div>
                <div className="text-[10px] font-bold uppercase tracking-wider text-slate-500">Management</div>
              </div>
              <div>
                <div className="text-lg font-black" style={{ color: primary }}>
                  {defaultProcessing.toFixed(2)}%
                </div>
                <div className="text-[10px] font-bold uppercase tracking-wider text-slate-500">Processing</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
