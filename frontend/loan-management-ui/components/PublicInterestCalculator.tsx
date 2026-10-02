"use client";

import { useMemo, useState } from "react";
import { calculateContractualSchedule, safeRate } from "../lib/loanRepaymentCalculator";

interface InterestCalculatorProps {
  products: Array<{
    title: string;
    interestRate?: string | number;
    rate?: string | number;
    rateType?: string;
    managementFeeRate?: string | number;
    minAmount?: string | number;
    maxAmount?: string | number | null;
    minTermMonths?: number;
    maxTermMonths?: number;
  }>;
  currency: string;
  primary: string;
  accent: string;
}

const money = (currency: string, value: number) => `${currency} ${Math.round(value).toLocaleString("en-RW")}`;

export default function PublicInterestCalculator({ products, currency, primary, accent }: InterestCalculatorProps) {
  const [index, setIndex] = useState(0);
  const product = products[index] || products[0];
  const minAmount = Number(product?.minAmount ?? 500000);
  const maxAmount = product?.maxAmount == null ? 20_000_000 : Number(product.maxAmount);
  const minTerm = Number(product?.minTermMonths ?? 1);
  const maxTerm = Math.max(minTerm, Number(product?.maxTermMonths ?? 6));
  const [amount, setAmount] = useState(minAmount);
  const [months, setMonths] = useState(minTerm);

  const interestRate = safeRate(product?.interestRate ?? product?.rate, 5);
  const managementRate = safeRate(product?.managementFeeRate, 5);
  const actualAmount = Math.min(maxAmount, Math.max(minAmount, amount));
  const actualMonths = Math.min(maxTerm, Math.max(minTerm, months));

  const result = useMemo(() => calculateContractualSchedule(actualAmount, actualMonths, interestRate, managementRate), [actualAmount, actualMonths, interestRate, managementRate]);

  const selectProduct = (next: number) => {
    const selected = products[next];
    setIndex(next);
    setAmount(Number(selected?.minAmount ?? 500000));
    setMonths(Number(selected?.minTermMonths ?? 1));
  };

  return (
    <div className="grid gap-6 lg:grid-cols-[.82fr_1.18fr]">
      <section className="rounded-[30px] border border-slate-200 bg-white p-6 shadow-[0_22px_70px_rgba(15,23,42,.07)] sm:p-8">
        <p className="text-[10px] font-black uppercase tracking-[.2em]" style={{ color: accent }}>Loan assumptions</p>
        <h2 className="mt-2 text-2xl font-black tracking-tight">Separate the cost components.</h2>
        <p className="mt-3 text-sm leading-7 text-slate-500">Adjust the amount and term to see how indicative interest and management charges contribute to scheduled repayment.</p>
        <div className="mt-8 space-y-6">
          <label className="block"><span className="text-xs font-black uppercase tracking-[.14em] text-slate-500">Loan product</span><select value={index} onChange={(e) => selectProduct(Number(e.target.value))} className="mt-2 h-13 w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 text-sm font-bold text-slate-900 outline-none focus:border-slate-400">{products.map((p, i) => <option key={`${p.title}-${i}`} value={i}>{p.title}</option>)}</select></label>
          <label className="block"><span className="text-xs font-black uppercase tracking-[.14em] text-slate-500">Loan amount</span><div className="relative mt-2"><span className="absolute left-4 top-1/2 -translate-y-1/2 text-sm font-black text-slate-400">{currency}</span><input type="number" min={minAmount} max={maxAmount} step={50000} value={amount} onChange={(e) => setAmount(Number(e.target.value))} className="h-16 w-full rounded-2xl border border-slate-200 bg-slate-50 pl-20 pr-4 text-xl font-black text-slate-950 outline-none focus:border-slate-400" inputMode="numeric" /></div></label>
          <div><div className="flex items-center justify-between"><span className="text-xs font-black uppercase tracking-[.14em] text-slate-500">Repayment term</span><span className="text-sm font-black" style={{ color: primary }}>{actualMonths} months</span></div><input type="range" min={minTerm} max={maxTerm} value={actualMonths} onChange={(e) => setMonths(Number(e.target.value))} className="mt-4 w-full" style={{ accentColor: primary }} /><div className="mt-2 flex justify-between text-[10px] font-bold text-slate-400"><span>{minTerm} month</span><span>{maxTerm} months</span></div></div>
        </div>
      </section>

      <section className="rounded-[30px] border border-slate-200 bg-white p-6 shadow-[0_22px_70px_rgba(15,23,42,.07)] sm:p-8">
        <div className="flex items-start justify-between gap-4"><div><p className="text-[10px] font-black uppercase tracking-[.2em]" style={{ color: accent }}>Cost breakdown</p><h2 className="mt-2 text-2xl font-black">What the schedule is made of.</h2></div><div className="flex h-11 w-11 items-center justify-center rounded-2xl" style={{ backgroundColor: `${accent}22`, color: primary }}>%</div></div>
        <div className="mt-8 grid gap-3 sm:grid-cols-3">
          {[
            ["Interest", result.interest, "Rate applied per month"],
            ["Management", result.management, "Configured management charge"],
            ["Total repayment", result.total, "Principal plus charges"],
          ].map(([label, value, note]) => <div key={label} className="rounded-2xl border border-slate-200 bg-slate-50 p-5"><div className="text-[9px] font-black uppercase tracking-[.15em] text-slate-400">{label}</div><div className="mt-2 text-xl font-black" style={{ color: label === "Total repayment" ? primary : undefined }}>{money(currency, Number(value))}</div><div className="mt-2 text-[10px] leading-5 text-slate-400">{note}</div></div>)}
        </div>
        <div className="mt-6 rounded-[26px] border border-slate-200 p-6">
          <div className="flex items-end justify-between gap-6"><div><div className="text-[10px] font-black uppercase tracking-[.15em] text-slate-400">First scheduled payment</div><div className="mt-2 text-3xl font-black" style={{ color: primary }}>{money(currency, result.firstInstallment)}</div></div><div className="text-right"><div className="text-[10px] font-black uppercase tracking-[.15em] text-slate-400">Selected rate</div><div className="mt-2 text-sm font-black">{interestRate}% {product?.rateType?.toLowerCase() || "monthly"}</div></div></div>
          <div className="mt-6 grid gap-2"><div className="flex items-center justify-between text-xs font-bold text-slate-500"><span>Interest share</span><span>{result.total > 0 ? Math.round((result.interest / result.total) * 100) : 0}%</span></div><div className="h-2 overflow-hidden rounded-full bg-slate-100"><div className="h-full rounded-full" style={{ width: `${result.total > 0 ? Math.min(100, (result.interest / result.total) * 100) : 0}%`, backgroundColor: accent }} /></div></div>
        </div>
        <div className="mt-6 rounded-2xl bg-slate-50 p-4 text-xs leading-6 text-slate-500"><strong className="text-slate-800">Important:</strong> this is an indicative calculation using the selected product configuration. Exact charges and repayment dates are set by the applicable loan agreement.</div>
      </section>
    </div>
  );
}
