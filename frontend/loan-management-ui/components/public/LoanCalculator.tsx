'use client';

import { useMemo, useState } from 'react';
import Link from 'next/link';

type Product = {
  title: string;
  description?: string;
  interestRate?: number | string;
  managementFeeRate?: number | string;
  rate?: number | string;
  rateType?: string;
  minAmount?: number | string;
  maxAmount?: number | string;
  term?: string;
  minTermMonths?: number;
  maxTermMonths?: number;
};

function numeric(value: unknown, fallback: number) {
  const n = Number(String(value ?? '').replace(/[^0-9.-]/g, ''));
  return Number.isFinite(n) && n >= 0 ? n : fallback;
}

function parseTerm(product?: Product) {
  if (!product) return { min: 3, max: 12 };
  if (product.minTermMonths || product.maxTermMonths) {
    return {
      min: product.minTermMonths ?? 3,
      max: product.maxTermMonths ?? 12,
    };
  }
  const matches = String(product.term ?? '').match(/\d+/g)?.map(Number) ?? [];
  if (matches.length >= 2) return { min: matches[0], max: matches[1] };
  if (matches.length === 1) return { min: matches[0], max: matches[0] };
  return { min: 3, max: 12 };
}

export default function PublicLoanCalculator({
  products,
  currency,
  primary,
  accent,
}: {
  products: Product[];
  currency: string;
  primary: string;
  accent: string;
}) {
  const [productIndex, setProductIndex] = useState(0);
  const product = products[productIndex];
  const terms = parseTerm(product);
  const minAmount = numeric(product?.minAmount, 500000);
  const maxAmount = Math.max(minAmount, numeric(product?.maxAmount, 10000000));
  const interestRate = numeric(product?.interestRate ?? product?.rate, 5);
  const managementRate = numeric(product?.managementFeeRate, 5);

  const [amount, setAmount] = useState(minAmount);
  const [months, setMonths] = useState(terms.min);

  function switchProduct(index: number) {
    const next = products[index];
    setProductIndex(index);
    setAmount(numeric(next?.minAmount, 500000));
    setMonths(parseTerm(next).min);
  }

  const estimate = useMemo(() => {
    const interest = amount * (interestRate / 100) * months;
    const management = amount * (managementRate / 100) * months;
    const total = amount + interest + management;
    return {
      interest,
      management,
      total,
      installment: months > 0 ? total / months : total,
    };
  }, [amount, interestRate, managementRate, months]);

  const fmt = (value: number) => value.toLocaleString(undefined, { maximumFractionDigits: 0 });
  const termOptions = Array.from({ length: Math.min(8, Math.max(1, terms.max - terms.min + 1)) }, (_, index) => terms.min + index);

  return (
    <div className="overflow-hidden rounded-[32px] border border-slate-200 bg-white shadow-[0_24px_80px_rgba(15,23,42,0.12)]">
      <div className="grid lg:grid-cols-[1.08fr_.92fr]">
        <div className="p-6 sm:p-8">
          <div className="text-[11px] font-bold uppercase tracking-[0.18em]" style={{ color: accent }}>Loan planning tool</div>
          <h2 className="mt-2 text-2xl font-black tracking-tight text-slate-950 sm:text-3xl">Plan before you apply</h2>
          <p className="mt-2 max-w-xl text-sm leading-6 text-slate-500">
            Compare products and see an indicative repayment estimate using the organization’s published pricing.
          </p>

          {products.length > 0 && (
            <div className="mt-7">
              <div className="mb-3 text-[11px] font-bold uppercase tracking-[0.16em] text-slate-400">Product</div>
              <div className="flex flex-wrap gap-2">
                {products.map((item, index) => (
                  <button
                    key={item.title}
                    type="button"
                    onClick={() => switchProduct(index)}
                    className="rounded-full border px-4 py-2 text-xs font-bold transition"
                    style={productIndex === index
                      ? { borderColor: primary, backgroundColor: primary, color: '#fff' }
                      : { borderColor: '#E2E8F0', color: '#475569' }}
                  >
                    {item.title}
                  </button>
                ))}
              </div>
            </div>
          )}

          <div className="mt-7">
            <div className="flex items-center justify-between gap-4">
              <label className="text-[11px] font-bold uppercase tracking-[0.16em] text-slate-400">Loan amount</label>
              <span className="text-xs font-bold text-slate-500">{currency}</span>
            </div>
            <div className="mt-2 flex items-center gap-3 rounded-2xl border border-slate-200 px-4 py-3">
              <span className="text-xs font-bold text-slate-400">{currency}</span>
              <input
                type="number"
                min={minAmount}
                max={maxAmount}
                step={Math.max(1, Math.round((maxAmount - minAmount) / 100))}
                value={amount}
                onChange={(event) => setAmount(Math.min(maxAmount, Math.max(minAmount, Number(event.target.value))))}
                className="w-full bg-transparent text-xl font-black text-slate-950 outline-none"
              />
            </div>
            <input
              type="range"
              min={minAmount}
              max={maxAmount}
              step={Math.max(1, Math.round((maxAmount - minAmount) / 100))}
              value={amount}
              onChange={(event) => setAmount(Number(event.target.value))}
              className="mt-4 w-full"
              style={{ accentColor: primary }}
            />
            <div className="mt-2 flex justify-between text-[10px] font-semibold text-slate-400">
              <span>{currency} {fmt(minAmount)}</span>
              <span>{currency} {fmt(maxAmount)}</span>
            </div>
          </div>

          <div className="mt-7">
            <div className="mb-3 text-[11px] font-bold uppercase tracking-[0.16em] text-slate-400">Repayment period</div>
            <div className="flex flex-wrap gap-2">
              {termOptions.map((term) => (
                <button
                  key={term}
                  type="button"
                  onClick={() => setMonths(term)}
                  className="rounded-full border px-4 py-2 text-xs font-bold transition"
                  style={months === term
                    ? { borderColor: primary, backgroundColor: primary, color: '#fff' }
                    : { borderColor: '#E2E8F0', color: '#475569' }}
                >
                  {term} months
                </button>
              ))}
            </div>
          </div>
        </div>

        <div className="p-6 sm:p-8" style={{ background: `linear-gradient(160deg, ${primary}, #0B223E)` }}>
          <div className="text-[11px] font-bold uppercase tracking-[0.18em] text-white/60">Indicative repayment</div>
          <div className="mt-6 text-sm text-white/70">Estimated monthly payment</div>
          <div className="mt-1 text-4xl font-black tracking-tight text-white">{currency} {fmt(estimate.installment)}</div>
          <div className="mt-1 text-xs text-white/60">over {months} months</div>

          <div className="mt-8 space-y-3 border-t border-white/10 pt-5">
            <div className="flex items-center justify-between text-sm"><span className="text-white/60">Principal</span><strong className="text-white">{currency} {fmt(amount)}</strong></div>
            <div className="flex items-center justify-between text-sm"><span className="text-white/60">Interest ({interestRate}%/mo)</span><strong className="text-white">{currency} {fmt(estimate.interest)}</strong></div>
            <div className="flex items-center justify-between text-sm"><span className="text-white/60">Management fee ({managementRate}%/mo)</span><strong className="text-white">{currency} {fmt(estimate.management)}</strong></div>
            <div className="flex items-center justify-between border-t border-white/10 pt-3 text-sm"><span className="font-bold text-white/80">Estimated total</span><strong className="text-lg text-white">{currency} {fmt(estimate.total)}</strong></div>
          </div>

          <Link
            href={`/apply${product?.title ? `?type=${encodeURIComponent(product.title)}` : ''}`}
            className="mt-8 block rounded-2xl bg-white px-5 py-3.5 text-center text-sm font-black transition hover:-translate-y-0.5"
            style={{ color: primary }}
          >
            Start application
          </Link>
          <div className="mt-4 text-center text-[10px] leading-4 text-white/50">Indicative only. Final pricing, eligibility and repayment terms are subject to credit assessment and the published loan agreement.</div>
        </div>
      </div>
    </div>
  );
}
