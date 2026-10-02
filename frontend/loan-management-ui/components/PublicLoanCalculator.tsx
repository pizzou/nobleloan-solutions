"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import {
  calculateContractualSchedule,
  percentageCharge,
  safeRate,
} from "../lib/loanRepaymentCalculator";

type Product = {
  title: string;
  description?: string;
  icon?: string;
  loanType?: string;
  interestRate?: number | string;
  managementFeeRate?: number | string;
  applicationFeeRate?: number | string;
  rate?: number | string;
  rateType?: string;
  minAmount?: number | string;
  maxAmount?: number | string | null;
  term?: string;
  minTermMonths?: number;
  maxTermMonths?: number;
};

function numeric(value: unknown, fallback: number) {
  if (value === null || value === undefined || value === "") return fallback;
  const n = Number(String(value).replace(/[^0-9.-]/g, ""));
  return Number.isFinite(n) && n >= 0 ? n : fallback;
}

function parseTerm(product?: Product) {
  if (!product) return { min: 1, max: 6 };
  const min = Math.max(1, product.minTermMonths ?? 1);
  const max = Math.max(min, product.maxTermMonths ?? min);
  return { min, max };
}

function hasMaximum(product?: Product) {
  return (
    product?.maxAmount !== null &&
    product?.maxAmount !== undefined &&
    product.maxAmount !== ""
  );
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
  const maxAmount = hasMaximum(product)
    ? numeric(product?.maxAmount, minAmount)
    : null;
  const interestRate = product?.interestRate ?? product?.rate ?? 5;
  const managementRate = product?.managementFeeRate ?? 5;
  const applicationRate = product?.applicationFeeRate ?? 2;
  const [amount, setAmount] = useState(minAmount);
  const [amountInput, setAmountInput] = useState(String(minAmount));
  const [months, setMonths] = useState(terms.min);

  const estimate = useMemo(
    () =>
      calculateContractualSchedule(
        amount,
        months,
        safeRate(interestRate, 5),
        safeRate(managementRate, 5),
      ),
    [amount, months, interestRate, managementRate],
  );

  const fmt = (value: number) =>
    value.toLocaleString("en-RW", { maximumFractionDigits: 0 });
  const termOptions = Array.from(
    { length: terms.max - terms.min + 1 },
    (_, i) => terms.min + i,
  );
  const amountStep = maxAmount
    ? Math.max(1000, Math.round((maxAmount - minAmount) / 100))
    : 1000;

  function switchProduct(index: number) {
    const next = products[index];
    const nextMin = numeric(next?.minAmount, 500000);
    setProductIndex(index);
    setAmount(nextMin);
    setAmountInput(String(nextMin));
    setMonths(parseTerm(next).min);
  }

  function commitAmount(value: number) {
    if (!Number.isFinite(value)) return;
    const bounded = Math.max(
      minAmount,
      maxAmount == null ? value : Math.min(maxAmount, value),
    );
    setAmount(bounded);
    setAmountInput(String(bounded));
  }

  return (
    <div className="overflow-hidden rounded-[28px] border border-white/10 bg-[#07162b] text-white shadow-[0_30px_90px_rgba(0,0,0,.35)]">
      <div className="border-b border-white/10 bg-white/[.04] px-6 py-5 sm:px-8">
        <div className="flex items-start justify-between gap-4">
          <div>
            <div
              className="text-[10px] font-black uppercase tracking-[.22em]"
              style={{ color: accent }}
            >
              Noble loan calculator
            </div>
            <h2 className="mt-2 text-2xl font-black tracking-[-.04em] sm:text-3xl">
              See the numbers before you apply.
            </h2>
            <p className="mt-2 max-w-xl text-xs leading-6 text-white/55">
              Choose a Noble loan, enter an amount and see an indicative
              repayment schedule using the published product terms.
            </p>
          </div>
          <div
            className="hidden h-12 w-12 shrink-0 items-center justify-center rounded-2xl sm:flex"
            style={{ backgroundColor: `${accent}18`, color: accent }}
          >
            RWF
          </div>
        </div>
      </div>

      <div className="grid lg:grid-cols-[1.05fr_.95fr]">
        <div className="p-6 sm:p-8">
          <div className="text-[10px] font-black uppercase tracking-[.18em] text-white/35">
            1 · Choose a loan
          </div>
          <div className="mt-3 grid gap-2 sm:grid-cols-2">
            {products.map((item, index) => (
              <button
                key={`${item.title}-${index}`}
                type="button"
                onClick={() => switchProduct(index)}
                className="rounded-2xl border px-4 py-3 text-left transition hover:-translate-y-0.5"
                style={
                  productIndex === index
                    ? { borderColor: accent, backgroundColor: `${accent}15` }
                    : {
                        borderColor: "rgba(255,255,255,.09)",
                        backgroundColor: "rgba(255,255,255,.025)",
                      }
                }
              >
                <span className="text-lg">{item.icon || "◈"}</span>
                <span className="ml-2 text-xs font-black text-white">
                  {item.title}
                </span>
                <span className="mt-1 block text-[10px] text-white/40">
                  {item.term || `${terms.min}-${terms.max} months`}
                </span>
              </button>
            ))}
          </div>

          {product && (
            <>
              <div className="mt-8 text-[10px] font-black uppercase tracking-[.18em] text-white/35">
                2 · Set your amount
              </div>
              <div className="mt-3 rounded-2xl border border-white/10 bg-white/[.035] p-4 focus-within:border-white/25">
                <div className="flex items-center gap-3">
                  <span className="text-xs font-black text-white/35">
                    {currency}
                  </span>
                  <input
                    value={amountInput}
                    inputMode="numeric"
                    onChange={(e) => {
                      const raw = e.target.value.replace(/[^0-9]/g, "");
                      setAmountInput(raw);
                      if (raw) setAmount(Number(raw));
                    }}
                    onBlur={() => commitAmount(Number(amountInput))}
                    onKeyDown={(e) => {
                      if (e.key === "Enter") commitAmount(Number(amountInput));
                    }}
                    className="w-full bg-transparent text-3xl font-black tracking-[-.03em] text-white outline-none"
                    aria-label={`Loan amount in ${currency}`}
                  />
                </div>
                <div className="mt-2 flex justify-between text-[10px] font-semibold text-white/35">
                  <span>
                    Minimum {currency} {fmt(minAmount)}
                  </span>
                  <span>
                    {maxAmount
                      ? `Maximum ${currency} ${fmt(maxAmount)}`
                      : "No configured maximum"}
                  </span>
                </div>
                {maxAmount && (
                  <input
                    type="range"
                    min={minAmount}
                    max={maxAmount}
                    step={amountStep}
                    value={Math.min(amount, maxAmount)}
                    onChange={(e) => {
                      const next = Number(e.target.value);
                      setAmount(next);
                      setAmountInput(String(next));
                    }}
                    className="mt-5 w-full"
                    style={{ accentColor: accent }}
                    aria-label="Loan amount range"
                  />
                )}
              </div>

              <div className="mt-7 text-[10px] font-black uppercase tracking-[.18em] text-white/35">
                3 · Choose your term
              </div>
              <div className="mt-3 flex flex-wrap gap-2">
                {termOptions.map((term) => (
                  <button
                    key={term}
                    type="button"
                    onClick={() => setMonths(term)}
                    className="rounded-xl px-4 py-2.5 text-xs font-black transition"
                    style={
                      months === term
                        ? { backgroundColor: accent, color: primary }
                        : {
                            backgroundColor: "rgba(255,255,255,.06)",
                            color: "rgba(255,255,255,.65)",
                          }
                    }
                  >
                    {term} {term === 1 ? "month" : "months"}
                  </button>
                ))}
              </div>
            </>
          )}
        </div>

        <div
          className="relative overflow-hidden p-6 sm:p-8"
          style={{ background: `linear-gradient(145deg, ${primary}, #061326)` }}
        >
          <div className="absolute -right-24 -top-24 h-56 w-56 rounded-full border border-white/10" />
          <div className="relative">
            <div
              className="text-[10px] font-black uppercase tracking-[.18em]"
              style={{ color: accent }}
            >
              Your estimate
            </div>
            {product ? (
              <>
                <div className="mt-7 text-xs font-semibold text-white/45">
                  Indicative total repayment
                </div>
                <div className="mt-1 text-4xl font-black tracking-[-.04em] sm:text-5xl">
                  {currency} {fmt(estimate.total)}
                </div>
                <div className="mt-2 text-xs text-white/45">
                  over {months} {months === 1 ? "month" : "months"}
                </div>
                <div className="mt-7 grid grid-cols-2 gap-2">
                  {[
                    ["Loan amount", `${currency} ${fmt(amount)}`],
                    [
                      "First instalment",
                      `${currency} ${fmt(estimate.firstInstallment)}`,
                    ],
                    ["Interest", `${currency} ${fmt(estimate.interest)}`],
                    ["Management", `${currency} ${fmt(estimate.management)}`],
                    [
                      "Application fee",
                      `${currency} ${fmt(percentageCharge(amount, Number(applicationRate)))}`,
                    ],
                    [
                      "Last instalment",
                      `${currency} ${fmt(estimate.lastInstallment)}`,
                    ],
                  ].map(([label, value]) => (
                    <div
                      key={label}
                      className="rounded-2xl border border-white/10 bg-white/[.045] p-3"
                    >
                      <div className="text-[9px] font-bold uppercase tracking-wider text-white/35">
                        {label}
                      </div>
                      <div className="mt-1 text-xs font-black text-white">
                        {value}
                      </div>
                    </div>
                  ))}
                </div>
                <Link
                  href={`/apply?type=${encodeURIComponent(product.loanType || product.title)}`}
                  className="mt-7 block rounded-2xl px-5 py-4 text-center text-sm font-black transition hover:-translate-y-0.5"
                  style={{ backgroundColor: accent, color: primary }}
                >
                  Apply for {product.title} →
                </Link>
                <p className="mt-4 text-[9px] leading-4 text-white/35">
                  Planning estimate only. Eligibility, approved amount, fees and
                  final repayment schedule are determined through Noble&apos;s
                  credit assessment and loan agreement.
                </p>
              </>
            ) : (
              <div className="mt-8 rounded-2xl border border-white/10 bg-white/5 p-5 text-sm text-white/50">
                Select a loan product to begin.
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
