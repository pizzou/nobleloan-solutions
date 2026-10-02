"use client";

import { useMemo, useState } from "react";
import Link from "next/link";

type Props = { currency: string; primary: string; accent: string; mode?: "loan" | "interest" | "payment" };

function money(currency: string, value: number) {
  return `${currency} ${Math.max(0, value).toLocaleString("en-RW", { maximumFractionDigits: 0 })}`;
}

function payment(principal: number, annualRate: number, months: number) {
  if (months <= 0) return 0;
  const r = annualRate / 100 / 12;
  if (r === 0) return principal / months;
  return principal * (r / (1 - Math.pow(1 + r, -months)));
}

function solveMonths(principal: number, annualRate: number, monthlyPayment: number) {
  if (principal <= 0 || monthlyPayment <= 0) return 0;
  const r = annualRate / 100 / 12;
  if (r === 0) return Math.ceil(principal / monthlyPayment);
  if (monthlyPayment <= principal * r) return 0;
  return Math.ceil(-Math.log(1 - (principal * r) / monthlyPayment) / Math.log(1 + r));
}

export default function UpstartStyleCalculator({ currency, primary, accent, mode = "loan" }: Props) {
  const [amount, setAmount] = useState(5000000);
  const [apr, setApr] = useState(18);
  const [months, setMonths] = useState(36);
  const [monthly, setMonthly] = useState(180000);

  const result = useMemo(() => {
    if (mode === "interest") {
      const pmt = monthly;
      const n = solveMonths(amount, apr, pmt);
      return { pmt, n, interest: n ? pmt * n - amount : 0, total: n ? pmt * n : 0 };
    }
    const pmt = payment(amount, apr, months);
    return { pmt, n: months, interest: pmt * months - amount, total: pmt * months };
  }, [amount, apr, months, monthly, mode]);

  const title = mode === "interest" ? "Interest calculator" : mode === "payment" ? "Payment calculator" : "Loan calculator";
  const description = mode === "interest"
    ? "See how your monthly payment and APR affect the total interest and time needed to repay a loan."
    : "Estimate your monthly payment and total interest before you apply.";

  return (
    <div className="overflow-hidden rounded-[28px] border border-slate-200 bg-white shadow-[0_24px_70px_rgba(15,23,42,.10)]">
      <div className="grid lg:grid-cols-[1fr_0.9fr]">
        <div className="p-6 sm:p-8 lg:p-10">
          <p className="text-[11px] font-black uppercase tracking-[.18em]" style={{ color: accent }}>{title}</p>
          <h1 className="mt-2 text-3xl font-black tracking-[-.04em] text-slate-950">Understand the cost before you borrow.</h1>
          <p className="mt-3 max-w-xl text-sm leading-6 text-slate-500">{description}</p>

          <div className="mt-8 space-y-6">
            <label className="block">
              <span className="text-xs font-black uppercase tracking-[.12em] text-slate-400">Loan amount</span>
              <div className="mt-2 flex items-center gap-3 rounded-2xl border border-slate-200 px-4 py-3 focus-within:border-slate-400">
                <span className="font-bold text-slate-400">{currency}</span>
                <input value={amount} onChange={e => setAmount(Math.max(0, Number(e.target.value.replace(/\D/g, ""))))} inputMode="numeric" className="w-full bg-transparent text-2xl font-black outline-none" />
              </div>
            </label>

            <label className="block">
              <div className="flex justify-between"><span className="text-xs font-black uppercase tracking-[.12em] text-slate-400">APR</span><strong>{apr.toFixed(2)}%</strong></div>
              <input type="range" min="0" max="60" step="0.01" value={apr} onChange={e => setApr(Number(e.target.value))} className="mt-3 w-full" style={{ accentColor: primary }} />
            </label>

            {mode === "interest" ? (
              <label className="block">
                <span className="text-xs font-black uppercase tracking-[.12em] text-slate-400">Monthly payment</span>
                <div className="mt-2 flex items-center gap-3 rounded-2xl border border-slate-200 px-4 py-3">
                  <span className="font-bold text-slate-400">{currency}</span>
                  <input value={monthly} onChange={e => setMonthly(Math.max(0, Number(e.target.value.replace(/\D/g, ""))))} inputMode="numeric" className="w-full bg-transparent text-2xl font-black outline-none" />
                </div>
              </label>
            ) : (
              <label className="block">
                <div className="flex justify-between"><span className="text-xs font-black uppercase tracking-[.12em] text-slate-400">Length of loan</span><strong>{months} months</strong></div>
                <input type="range" min="6" max="84" step="1" value={months} onChange={e => setMonths(Number(e.target.value))} className="mt-3 w-full" style={{ accentColor: primary }} />
                <div className="mt-2 flex justify-between text-[10px] font-bold text-slate-400"><span>6 months</span><span>7 years</span></div>
              </label>
            )}
          </div>
        </div>

        <div className="p-6 sm:p-8" style={{ background: `linear-gradient(145deg, ${primary}, #07152a)` }}>
          <div className="rounded-3xl border border-white/10 bg-white/[.06] p-6 text-white backdrop-blur">
            <p className="text-[10px] font-black uppercase tracking-[.18em] text-white/45">Estimated results</p>
            <div className="mt-6">
              <span className="text-xs font-bold text-white/55">Monthly payment</span>
              <div className="mt-1 text-4xl font-black tracking-tight">{money(currency, result.pmt)}</div>
            </div>
            <div className="mt-7 grid grid-cols-2 gap-3">
              <div className="rounded-2xl border border-white/10 bg-white/[.05] p-4"><span className="text-[10px] font-bold text-white/45">Total interest</span><strong className="mt-1 block text-lg">{money(currency, result.interest)}</strong></div>
              <div className="rounded-2xl border border-white/10 bg-white/[.05] p-4"><span className="text-[10px] font-bold text-white/45">Total repayment</span><strong className="mt-1 block text-lg">{money(currency, result.total)}</strong></div>
            </div>
            <div className="mt-4 rounded-2xl bg-white/[.05] p-4 text-xs text-white/60">Estimated payoff period: <strong className="text-white">{result.n || "Not achievable"} months</strong></div>
            <Link href="/apply" className="mt-7 block rounded-2xl px-5 py-4 text-center text-sm font-black text-slate-950" style={{ backgroundColor: accent }}>Check your options</Link>
            <p className="mt-4 text-center text-[10px] leading-4 text-white/40">For planning only. This estimate is not an offer, approval or guarantee of terms.</p>
          </div>
        </div>
      </div>
    </div>
  );
}
