"use client";

import { useMemo, useState } from "react";
import { calculateContractualSchedule, safeRate } from "../lib/loanRepaymentCalculator";

interface PaymentCalculatorProps {
  products: Array<{
    title: string;
    loanType?: string;
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

const money = (currency: string, value: number) =>
  `${currency} ${Math.round(value).toLocaleString("en-RW")}`;

function solvePrincipalForFirstPayment(target: number, months: number, interest: number, management: number) {
  const denominator = 1 / Math.max(1, months) + interest / 100 + management / 100;
  return denominator > 0 ? target / denominator : 0;
}

export default function PublicPaymentCalculator({ products, currency, primary, accent }: PaymentCalculatorProps) {
  const [index, setIndex] = useState(0);
  const product = products[index] || products[0];
  const [target, setTarget] = useState(200000);
  const [months, setMonths] = useState(Number(product?.minTermMonths ?? 1));

  const minAmount = Number(product?.minAmount ?? 500000);
  const maxAmount = product?.maxAmount == null ? 20_000_000 : Number(product.maxAmount);
  const minTerm = Number(product?.minTermMonths ?? 1);
  const maxTerm = Math.max(minTerm, Number(product?.maxTermMonths ?? 6));
  const actualMonths = Math.min(maxTerm, Math.max(minTerm, months));
  const interestRate = safeRate(product?.interestRate ?? product?.rate, 5);
  const managementRate = safeRate(product?.managementFeeRate, 5);

  const result = useMemo(() => {
    const rawPrincipal = solvePrincipalForFirstPayment(target, actualMonths, interestRate, managementRate);
    const principal = Math.min(maxAmount, Math.max(minAmount, rawPrincipal));
    const schedule = calculateContractualSchedule(principal, actualMonths, interestRate, managementRate);
    return { principal, schedule };
  }, [target, actualMonths, interestRate, managementRate, minAmount, maxAmount]);

  const selectProduct = (next: number) => {
    const selected = products[next];
    setIndex(next);
    setMonths(Number(selected?.minTermMonths ?? 1));
  };

  const targetDifference = Math.round(result.schedule.firstInstallment - target);

  return (
    <div className="grid gap-6 lg:grid-cols-[.85fr_1.15fr]">
      <section className="rounded-[30px] border border-slate-200 bg-white p-6 shadow-[0_22px_70px_rgba(15,23,42,.07)] sm:p-8">
        <div className="flex items-start justify-between gap-4">
          <div>
            <p className="text-[10px] font-black uppercase tracking-[.2em]" style={{ color: accent }}>Payment target</p>
            <h2 className="mt-2 text-2xl font-black tracking-tight text-slate-950">Start with what feels manageable.</h2>
          </div>
          <span className="flex h-11 w-11 items-center justify-center rounded-2xl" style={{ backgroundColor: `${accent}22`, color: primary }}>◎</span>
        </div>

        <div className="mt-8 space-y-6">
          <label className="block">
            <span className="text-xs font-black uppercase tracking-[.14em] text-slate-500">Loan product</span>
            <select
              value={index}
              onChange={(e) => selectProduct(Number(e.target.value))}
              className="mt-2 h-13 w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 text-sm font-bold text-slate-900 outline-none transition focus:border-slate-400 focus:bg-white"
            >
              {products.map((item, i) => <option key={`${item.title}-${i}`} value={i}>{item.title}</option>)}
            </select>
          </label>

          <label className="block">
            <span className="text-xs font-black uppercase tracking-[.14em] text-slate-500">Target first payment</span>
            <div className="relative mt-2">
              <span className="absolute left-4 top-1/2 -translate-y-1/2 text-sm font-black text-slate-400">{currency}</span>
              <input
                type="number"
                min={1}
                value={target}
                onChange={(e) => setTarget(Math.max(0, Number(e.target.value)))}
                className="h-16 w-full rounded-2xl border border-slate-200 bg-slate-50 pl-20 pr-4 text-xl font-black text-slate-950 outline-none transition focus:border-slate-400 focus:bg-white"
                inputMode="numeric"
              />
            </div>
          </label>

          <div>
            <div className="flex items-center justify-between gap-4">
              <span className="text-xs font-black uppercase tracking-[.14em] text-slate-500">Repayment term</span>
              <span className="text-sm font-black" style={{ color: primary }}>{actualMonths} months</span>
            </div>
            <input
              type="range"
              min={minTerm}
              max={maxTerm}
              value={actualMonths}
              onChange={(e) => setMonths(Number(e.target.value))}
              className="mt-4 w-full"
              style={{ accentColor: primary }}
            />
            <div className="mt-2 flex justify-between text-[10px] font-bold text-slate-400"><span>{minTerm} mo</span><span>{maxTerm} mo</span></div>
          </div>
        </div>
      </section>

      <section className="overflow-hidden rounded-[30px] text-white shadow-[0_30px_90px_rgba(7,21,42,.18)]" style={{ background: `linear-gradient(145deg, ${primary}, #061326)` }}>
        <div className="p-6 sm:p-8">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <p className="text-[10px] font-black uppercase tracking-[.2em]" style={{ color: accent }}>Illustrative scenario</p>
              <h2 className="mt-2 text-2xl font-black">Estimated borrowing amount</h2>
            </div>
            <span className="rounded-full border border-white/10 bg-white/5 px-3 py-2 text-[10px] font-bold text-white/60">{product?.title || "Selected product"}</span>
          </div>

          <div className="mt-9 rounded-[26px] border border-white/10 bg-white/[.06] p-6">
            <div className="text-xs font-bold text-white/50">Approximate amount supported by the target first payment</div>
            <div className="mt-2 text-4xl font-black tracking-tight sm:text-5xl">{money(currency, result.principal)}</div>
            <div className="mt-4 flex flex-wrap gap-2 text-[10px] font-bold text-white/55">
              <span className="rounded-full border border-white/10 bg-white/5 px-3 py-1.5">Interest {interestRate}% {product?.rateType?.toLowerCase() || "monthly"}</span>
              <span className="rounded-full border border-white/10 bg-white/5 px-3 py-1.5">Management {managementRate}%</span>
            </div>
          </div>

          <div className="mt-6 grid gap-3 sm:grid-cols-3">
            {[
              ["First payment", money(currency, result.schedule.firstInstallment)],
              ["Total charges", money(currency, result.schedule.interest + result.schedule.management)],
              ["Total repayment", money(currency, result.schedule.total)],
            ].map(([label, value]) => (
              <div key={label} className="rounded-2xl border border-white/10 bg-white/[.045] p-4">
                <div className="text-[9px] font-black uppercase tracking-[.14em] text-white/40">{label}</div>
                <div className="mt-2 text-sm font-black">{value}</div>
              </div>
            ))}
          </div>

          <div className="mt-6 rounded-2xl border border-white/10 bg-black/10 p-4">
            <div className="flex items-center justify-between gap-4 text-xs">
              <span className="text-white/55">Target vs. estimated first payment</span>
              <span className="font-black" style={{ color: targetDifference === 0 ? accent : "#fff" }}>
                {targetDifference === 0 ? "On target" : `${targetDifference > 0 ? "+" : "−"}${money(currency, Math.abs(targetDifference))}`}
              </span>
            </div>
            <div className="mt-3 h-2 overflow-hidden rounded-full bg-white/10"><div className="h-full rounded-full" style={{ width: `${Math.min(100, Math.max(6, (result.schedule.firstInstallment / Math.max(target, 1)) * 100))}%`, backgroundColor: accent }} /></div>
            <p className="mt-3 text-[10px] leading-5 text-white/40">The amount is bounded by the selected product&apos;s published range. It is not a promise of eligibility or approval.</p>
          </div>
        </div>
      </section>
    </div>
  );
}
