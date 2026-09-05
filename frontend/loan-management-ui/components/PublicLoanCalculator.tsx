"use client";

import { useMemo, useState } from "react";
import Link from "next/link";

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

  if (
    product.minTermMonths !== undefined ||
    product.maxTermMonths !== undefined
  ) {
    const min = Math.max(1, product.minTermMonths ?? 1);
    const max = Math.max(min, product.maxTermMonths ?? min);
    return { min, max };
  }

  const matches =
    String(product.term ?? "")
      .match(/\d+/g)
      ?.map(Number) ?? [];

  if (matches.length >= 2) {
    return {
      min: Math.max(1, matches[0]),
      max: Math.max(matches[0], matches[1]),
    };
  }

  if (matches.length === 1) {
    return { min: Math.max(1, matches[0]), max: Math.max(1, matches[0]) };
  }

  return { min: 1, max: 6 };
}

function hasMaximum(product?: Product) {
  return (
    product?.maxAmount !== null &&
    product?.maxAmount !== undefined &&
    product.maxAmount !== ""
  );
}

function parseDecimal(value: number | string | undefined, scale: bigint) {
  const text = String(value ?? "0")
    .trim()
    .replace(/,/g, "");

  if (!/^\d+(?:\.\d+)?$/.test(text)) return 0n;

  const [whole, fraction = ""] = text.split(".");
  const digits = fraction.padEnd(Number(scale), "0").slice(0, Number(scale));
  return BigInt(whole) * 10n ** scale + BigInt(digits || "0");
}

function halfUpDivide(numerator: bigint, denominator: bigint) {
  if (denominator <= 0n) throw new Error("Invalid financial denominator");
  const quotient = numerator / denominator;
  const remainder = numerator % denominator;
  return remainder * 2n >= denominator ? quotient + 1n : quotient;
}

function centsToNumber(cents: bigint) {
  return Number(cents) / 100;
}

function percentageCharge(principal: number, rate: number | string) {
  const principalCents = BigInt(Math.max(0, Math.round(principal * 100)));
  const rateScale = 9n;
  const rateUnits = parseDecimal(rate, rateScale);
  const denominator = 100n * 10n ** rateScale;
  return centsToNumber(halfUpDivide(principalCents * rateUnits, denominator));
}

/**
 * Exact browser-side mirror of the published declining-principal schedule.
 * Monetary calculations use integer cents and rate precision of 9 decimals.
 */
function calculateSchedule(
  principal: number,
  months: number,
  interestRate: number | string,
  managementRate: number | string,
) {
  const originalPrincipalCents = BigInt(
    Math.max(0, Math.round(principal * 100)),
  );

  let balanceCents = originalPrincipalCents;
  let totalInterestCents = 0n;
  let totalManagementCents = 0n;
  let firstInstallmentCents = 0n;
  let lastInstallmentCents = 0n;

  const rateScale = 9n;
  const rateDenominator = 100n * 10n ** rateScale;
  const interestRateUnits = parseDecimal(interestRate, rateScale);
  const managementRateUnits = parseDecimal(managementRate, rateScale);

  for (
    let installmentNumber = 1;
    installmentNumber <= months;
    installmentNumber += 1
  ) {
    const remainingInstallments = months - installmentNumber + 1;

    const principalComponentCents =
      remainingInstallments === 1
        ? balanceCents
        : halfUpDivide(balanceCents, BigInt(remainingInstallments));

    const interestCents = halfUpDivide(
      balanceCents * interestRateUnits,
      rateDenominator,
    );

    const managementCents = halfUpDivide(
      balanceCents * managementRateUnits,
      rateDenominator,
    );

    const installmentCents =
      principalComponentCents + interestCents + managementCents;

    totalInterestCents += interestCents;
    totalManagementCents += managementCents;

    if (installmentNumber === 1) firstInstallmentCents = installmentCents;
    if (installmentNumber === months) lastInstallmentCents = installmentCents;

    balanceCents -= principalComponentCents;
  }

  return {
    interest: centsToNumber(totalInterestCents),
    management: centsToNumber(totalManagementCents),
    total: centsToNumber(
      originalPrincipalCents + totalInterestCents + totalManagementCents,
    ),
    firstInstallment: centsToNumber(firstInstallmentCents),
    lastInstallment: centsToNumber(lastInstallmentCents),
  };
}

function clamp(value: number, minimum: number, maximum: number | null) {
  if (!Number.isFinite(value)) return minimum;
  if (value < minimum) return minimum;
  if (maximum !== null && value > maximum) return maximum;
  return value;
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
  const configuredMax = hasMaximum(product)
    ? numeric(product?.maxAmount, minAmount)
    : null;

  const interestRate = product?.interestRate ?? product?.rate ?? "5.00";
  const managementRate = product?.managementFeeRate ?? "5.00";
  const applicationRate = product?.applicationFeeRate ?? "2.00";

  const [amount, setAmount] = useState(minAmount);
  const [amountInput, setAmountInput] = useState(String(minAmount));
  const [months, setMonths] = useState(terms.min);

  const fmt = (value: number) =>
    value.toLocaleString("en-RW", {
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    });

  const currencyFormatter = (value: number) => `${currency} ${fmt(value)}`;

  function updateAmount(value: number) {
    const next = clamp(value, minAmount, configuredMax);
    setAmount(next);
    setAmountInput(String(Math.round(next)));
  }

  function handleAmountInput(value: string) {
    // Keep the raw field editable. This avoids the old behaviour where
    // deleting a digit immediately snapped the field back to the minimum.
    setAmountInput(value.replace(/[^0-9]/g, ""));

    if (value === "") return;

    const parsed = Number(value.replace(/[^0-9]/g, ""));
    if (!Number.isFinite(parsed)) return;

    setAmount(clamp(parsed, minAmount, configuredMax));
  }

  function commitAmountInput() {
    const parsed = Number(amountInput.replace(/[^0-9]/g, ""));

    if (!Number.isFinite(parsed) || parsed < minAmount) {
      updateAmount(minAmount);
      return;
    }

    updateAmount(parsed);
  }

  function switchProduct(index: number) {
    const next = products[index];
    const nextMin = numeric(next?.minAmount, 500000);
    const nextMax = hasMaximum(next) ? numeric(next?.maxAmount, nextMin) : null;
    const nextAmount = clamp(nextMin, nextMin, nextMax);

    setProductIndex(index);
    setAmount(nextAmount);
    setAmountInput(String(Math.round(nextAmount)));
    setMonths(parseTerm(next).min);
  }

  const estimate = useMemo(
    () => calculateSchedule(amount, months, interestRate, managementRate),
    [amount, months, interestRate, managementRate],
  );

  const processingFee = useMemo(
    () => percentageCharge(amount, applicationRate),
    [amount, applicationRate],
  );

  const totalCashCost = estimate.total + processingFee;

  const termOptions = Array.from(
    { length: Math.max(1, terms.max - terms.min + 1) },
    (_, index) => terms.min + index,
  );

  const amountStep =
    configuredMax !== null
      ? Math.max(1000, Math.round((configuredMax - minAmount) / 100))
      : 1000;

  const quickAmounts = (() => {
    const candidates = [minAmount, minAmount * 2, minAmount * 3, minAmount * 5];

    if (configuredMax !== null) candidates.push(configuredMax);

    return Array.from(new Set(candidates))
      .filter((value) => value >= minAmount)
      .filter((value) => configuredMax === null || value <= configuredMax)
      .sort((a, b) => a - b)
      .slice(0, 5);
  })();

  return (
    <div className="overflow-hidden rounded-[30px] border border-slate-200 bg-white shadow-[0_24px_80px_rgba(15,23,42,0.14)]">
      <div className="grid lg:grid-cols-[1.02fr_.98fr]">
        <div className="p-5 sm:p-7 lg:p-8">
          <div className="flex items-start justify-between gap-4">
            <div>
              <div
                className="text-[10px] font-black uppercase tracking-[0.2em]"
                style={{ color: accent }}
              >
                Loan planning tool
              </div>
              <h2 className="mt-2 text-2xl font-black tracking-[-.035em] text-slate-950 sm:text-3xl">
                Choose your amount.
              </h2>
              <p className="mt-2 max-w-xl text-sm leading-6 text-slate-500">
                Adjust the amount and term below to see an indicative schedule
                using the published product terms.
              </p>
            </div>
            <div className="hidden rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-right sm:block">
              <div className="text-[9px] font-black uppercase tracking-[.15em] text-slate-400">
                Currency
              </div>
              <div className="mt-0.5 text-sm font-black text-slate-900">
                {currency}
              </div>
            </div>
          </div>

          {products.length > 0 ? (
            <>
              <div className="mt-6">
                <div className="mb-2.5 text-[10px] font-black uppercase tracking-[.16em] text-slate-400">
                  Loan product
                </div>
                <div className="flex flex-wrap gap-2">
                  {products.map((item, index) => (
                    <button
                      key={`${item.title}-${index}`}
                      type="button"
                      onClick={() => switchProduct(index)}
                      className="rounded-xl border px-3.5 py-2.5 text-xs font-black transition hover:-translate-y-0.5 focus:outline-none focus:ring-2 focus:ring-offset-2"
                      style={
                        productIndex === index
                          ? {
                              borderColor: primary,
                              backgroundColor: primary,
                              color: "#fff",
                            }
                          : {
                              borderColor: "#E2E8F0",
                              backgroundColor: "#fff",
                              color: "#475569",
                            }
                      }
                    >
                      {item.title}
                    </button>
                  ))}
                </div>
              </div>

              <div className="mt-6 rounded-2xl border border-slate-200 bg-slate-50/80 p-4 sm:p-5">
                <div className="flex items-center justify-between gap-4">
                  <label
                    htmlFor="loan-amount"
                    className="text-[10px] font-black uppercase tracking-[.16em] text-slate-500"
                  >
                    Requested loan amount
                  </label>
                  <span className="text-[10px] font-black uppercase tracking-[.12em] text-slate-400">
                    {currency}
                  </span>
                </div>

                <div className="mt-2 flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() => updateAmount(amount - amountStep)}
                    disabled={amount <= minAmount}
                    className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl border border-slate-200 bg-white text-xl font-bold text-slate-700 transition hover:border-slate-300 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
                    aria-label="Decrease loan amount"
                  >
                    −
                  </button>

                  <div className="flex min-w-0 flex-1 items-center rounded-xl border border-slate-200 bg-white px-4 shadow-sm focus-within:border-slate-400 focus-within:ring-2 focus-within:ring-slate-200">
                    <span className="mr-2 text-sm font-black text-slate-400">
                      {currency}
                    </span>
                    <input
                      id="loan-amount"
                      type="text"
                      inputMode="numeric"
                      value={amountInput}
                      onChange={(event) =>
                        handleAmountInput(event.target.value)
                      }
                      onBlur={commitAmountInput}
                      onKeyDown={(event) => {
                        if (event.key === "Enter") {
                          event.currentTarget.blur();
                        }
                      }}
                      className="h-12 min-w-0 w-full bg-transparent text-xl font-black tracking-tight text-slate-950 outline-none sm:text-2xl"
                      aria-describedby="loan-amount-help"
                    />
                  </div>

                  <button
                    type="button"
                    onClick={() => updateAmount(amount + amountStep)}
                    disabled={configuredMax !== null && amount >= configuredMax}
                    className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl border border-slate-200 bg-white text-xl font-bold text-slate-700 transition hover:border-slate-300 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
                    aria-label="Increase loan amount"
                  >
                    +
                  </button>
                </div>

                <div className="mt-3 flex flex-wrap gap-2">
                  {quickAmounts.map((value) => (
                    <button
                      key={value}
                      type="button"
                      onClick={() => updateAmount(value)}
                      className="rounded-full border border-slate-200 bg-white px-3 py-1.5 text-[10px] font-black text-slate-600 transition hover:border-slate-300 hover:text-slate-950"
                    >
                      {currency} {fmt(value)}
                    </button>
                  ))}
                </div>

                {configuredMax !== null ? (
                  <>
                    <input
                      type="range"
                      min={minAmount}
                      max={configuredMax}
                      step={amountStep}
                      value={Math.min(amount, configuredMax)}
                      onChange={(event) =>
                        updateAmount(Number(event.target.value))
                      }
                      className="mt-5 w-full cursor-pointer"
                      style={{ accentColor: accent }}
                      aria-label="Adjust loan amount"
                    />
                    <div className="mt-2 flex justify-between text-[9px] font-bold text-slate-400">
                      <span>
                        Minimum {currency} {fmt(minAmount)}
                      </span>
                      <span>
                        Maximum {currency} {fmt(configuredMax)}
                      </span>
                    </div>
                  </>
                ) : (
                  <div
                    id="loan-amount-help"
                    className="mt-3 text-[10px] leading-5 text-slate-500"
                  >
                    Minimum published amount: {currency} {fmt(minAmount)}. Enter
                    any amount above the minimum; final eligibility is subject
                    to credit assessment and approved limits.
                  </div>
                )}
              </div>

              <div className="mt-5">
                <div className="mb-2.5 flex items-center justify-between">
                  <div className="text-[10px] font-black uppercase tracking-[.16em] text-slate-400">
                    Repayment period
                  </div>
                  <div
                    className="text-xs font-black"
                    style={{ color: primary }}
                  >
                    {months} {months === 1 ? "month" : "months"}
                  </div>
                </div>
                <div className="flex flex-wrap gap-2">
                  {termOptions.map((term) => (
                    <button
                      key={term}
                      type="button"
                      onClick={() => setMonths(term)}
                      className="rounded-xl border px-3 py-2 text-xs font-black transition hover:-translate-y-0.5 focus:outline-none focus:ring-2 focus:ring-offset-2"
                      style={
                        months === term
                          ? {
                              borderColor: primary,
                              backgroundColor: primary,
                              color: "#fff",
                            }
                          : {
                              borderColor: "#E2E8F0",
                              backgroundColor: "#fff",
                              color: "#475569",
                            }
                      }
                    >
                      {term} mo
                    </button>
                  ))}
                </div>
              </div>

              <div className="mt-5 grid grid-cols-3 gap-2">
                <div className="rounded-xl border border-slate-100 bg-slate-50 p-3">
                  <div className="text-[9px] font-black uppercase tracking-wider text-slate-400">
                    Interest
                  </div>
                  <div className="mt-1 text-xs font-black text-slate-900">
                    {interestRate}% / mo
                  </div>
                </div>
                <div className="rounded-xl border border-slate-100 bg-slate-50 p-3">
                  <div className="text-[9px] font-black uppercase tracking-wider text-slate-400">
                    Management
                  </div>
                  <div className="mt-1 text-xs font-black text-slate-900">
                    {managementRate}% / mo
                  </div>
                </div>
                <div className="rounded-xl border border-slate-100 bg-slate-50 p-3">
                  <div className="text-[9px] font-black uppercase tracking-wider text-slate-400">
                    Processing
                  </div>
                  <div className="mt-1 text-xs font-black text-slate-900">
                    {applicationRate}% once
                  </div>
                </div>
              </div>
            </>
          ) : (
            <div className="mt-6 rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm leading-6 text-amber-800">
              No active loan products are currently available. Please check back
              later or contact our lending team.
            </div>
          )}
        </div>

        <div
          className="p-5 text-white sm:p-7 lg:p-8"
          style={{ background: `linear-gradient(160deg, ${primary}, #0B223E)` }}
        >
          <div className="flex items-center justify-between gap-4">
            <div className="text-[10px] font-black uppercase tracking-[.18em] text-white/60">
              Indicative repayment
            </div>
            <span className="rounded-full border border-white/10 bg-white/[.08] px-2.5 py-1 text-[9px] font-black uppercase tracking-[.12em] text-white/60">
              {currency}
            </span>
          </div>

          {product ? (
            <>
              <div className="mt-5 rounded-2xl border border-white/10 bg-white/[.07] p-4">
                <div className="text-[10px] font-bold uppercase tracking-[.14em] text-white/50">
                  Your requested amount
                </div>
                <div className="mt-1 flex items-baseline gap-2">
                  <span className="text-sm font-bold text-white/60">
                    {currency}
                  </span>
                  <span className="text-3xl font-black tracking-tight text-white">
                    {fmt(amount)}
                  </span>
                </div>
                <div className="mt-1 text-[10px] text-white/50">
                  Adjust the amount on the left at any time.
                </div>
              </div>

              <div className="mt-5">
                <div className="text-sm text-white/65">
                  First scheduled installment
                </div>
                <div className="mt-1 text-3xl font-black tracking-[-.03em] text-white sm:text-4xl">
                  {currencyFormatter(estimate.firstInstallment)}
                </div>
                <div className="mt-1 text-[10px] leading-5 text-white/50">
                  Indicative installment based on declining principal.
                </div>
              </div>

              <div className="mt-6 space-y-3 border-t border-white/10 pt-5">
                <div className="flex items-center justify-between gap-4 text-sm">
                  <span className="text-white/55">Principal</span>
                  <strong className="text-white">
                    {currencyFormatter(amount)}
                  </strong>
                </div>
                <div className="flex items-center justify-between gap-4 text-sm">
                  <span className="text-white/55">Interest</span>
                  <strong className="text-white">
                    {currencyFormatter(estimate.interest)}
                  </strong>
                </div>
                <div className="flex items-center justify-between gap-4 text-sm">
                  <span className="text-white/55">Management fee</span>
                  <strong className="text-white">
                    {currencyFormatter(estimate.management)}
                  </strong>
                </div>
                <div className="flex items-center justify-between gap-4 text-sm">
                  <span className="text-white/55">Processing fee</span>
                  <strong className="text-white">
                    {currencyFormatter(processingFee)}
                  </strong>
                </div>
                <div className="flex items-center justify-between gap-4 text-sm">
                  <span className="text-white/55">
                    Last scheduled installment
                  </span>
                  <strong className="text-white">
                    {currencyFormatter(estimate.lastInstallment)}
                  </strong>
                </div>
                <div className="border-t border-white/10 pt-3">
                  <div className="flex items-end justify-between gap-4">
                    <div>
                      <div className="text-xs font-black text-white/80">
                        Scheduled repayments
                      </div>
                      <div className="mt-0.5 text-[9px] text-white/40">
                        Excludes the one-time processing fee
                      </div>
                    </div>
                    <strong className="text-lg text-white">
                      {currencyFormatter(estimate.total)}
                    </strong>
                  </div>
                </div>
                <div
                  className="rounded-xl px-3.5 py-3"
                  style={{ backgroundColor: `${accent}18` }}
                >
                  <div className="flex items-center justify-between gap-4">
                    <span className="text-xs font-black text-white/85">
                      Total cash cost
                    </span>
                    <strong className="text-base font-black text-white">
                      {currencyFormatter(totalCashCost)}
                    </strong>
                  </div>
                </div>
              </div>

              <Link
                href={`/apply${product.title ? `?type=${encodeURIComponent(product.loanType || product.title)}` : ""}`}
                className="mt-6 block rounded-xl bg-white px-5 py-3.5 text-center text-sm font-black transition hover:-translate-y-0.5 hover:shadow-lg focus:outline-none focus:ring-2 focus:ring-white"
                style={{ color: primary }}
              >
                Start an application
              </Link>

              <div className="mt-3 text-center text-[9px] leading-4 text-white/45">
                Indicative only. Eligibility, final fees, approved amount and
                repayment schedule are determined by credit assessment and the
                final loan agreement.
              </div>
            </>
          ) : (
            <div className="mt-8 rounded-2xl border border-white/10 bg-white/5 p-5 text-sm leading-6 text-white/60">
              Select an active loan product to calculate an indicative
              repayment.
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
