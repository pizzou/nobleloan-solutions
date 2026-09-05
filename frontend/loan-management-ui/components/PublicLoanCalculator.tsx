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

function hasMaximum(
  product?: Product,
): product is Product & { maxAmount: number | string } {
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
 * Exact client-side mirror of FinancialPolicy.contractualScheduleLine().
 * Monetary state is integer cents; rates are represented at the database's
 * nine-decimal precision. No binary floating-point participates in money math.
 */
function calculateSchedule(
  principal: number,
  months: number,
  interestRate: number | string,
  managementRate: number | string,
) {
  let balanceCents = BigInt(Math.max(0, Math.round(principal * 100)));
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

    balanceCents = balanceCents - principalComponentCents;
  }

  return {
    interest: centsToNumber(totalInterestCents),
    management: centsToNumber(totalManagementCents),
    total: centsToNumber(
      BigInt(Math.max(0, Math.round(principal * 100))) +
        totalInterestCents +
        totalManagementCents,
    ),
    firstInstallment: centsToNumber(firstInstallmentCents),
    lastInstallment: centsToNumber(lastInstallmentCents),
  };
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
    ? numeric(product.maxAmount, minAmount)
    : null;
  const interestRate = product?.interestRate ?? product?.rate ?? "5.00";
  const managementRate = product?.managementFeeRate ?? "5.00";
  const applicationRate = product?.applicationFeeRate ?? "2.00";

  const [amountInput, setAmountInput] = useState(String(Math.round(minAmount)));
  const [months, setMonths] = useState(terms.min);

  function switchProduct(index: number) {
    const next = products[index];
    const nextMin = numeric(next?.minAmount, 500000);
    setProductIndex(index);
    setAmountInput(String(Math.round(nextMin)));
    setMonths(parseTerm(next).min);
  }

  const parsedAmount = amountInput === "" ? 0 : Number(amountInput);
  const amountIsNumeric = Number.isFinite(parsedAmount);
  const amountBelowMinimum =
    amountInput !== "" && amountIsNumeric && parsedAmount < minAmount;
  const amountAboveMaximum =
    configuredMax !== null &&
    amountInput !== "" &&
    amountIsNumeric &&
    parsedAmount > configuredMax;
  const effectiveAmount =
    amountInput === "" || !amountIsNumeric ? minAmount : parsedAmount;

  function handleAmountChange(value: string) {
    const normalized = value.replace(/[^0-9]/g, "");
    setAmountInput(normalized);
  }

  function normalizeAmountOnBlur() {
    if (amountInput === "") {
      setAmountInput(String(Math.round(minAmount)));
      return;
    }

    const value = Number(amountInput);
    if (!Number.isFinite(value)) {
      setAmountInput(String(Math.round(minAmount)));
      return;
    }

    const clamped = Math.min(
      configuredMax !== null ? configuredMax : Number.MAX_SAFE_INTEGER,
      Math.max(minAmount, value),
    );
    setAmountInput(String(Math.round(clamped)));
  }

  const estimate = useMemo(
    () =>
      calculateSchedule(effectiveAmount, months, interestRate, managementRate),
    [effectiveAmount, months, interestRate, managementRate],
  );

  const fmt = (value: number) =>
    value.toLocaleString("en-RW", { maximumFractionDigits: 0 });

  const termOptions = Array.from(
    { length: Math.max(1, terms.max - terms.min + 1) },
    (_, index) => terms.min + index,
  );

  const amountStep =
    configuredMax !== null
      ? Math.max(1000, Math.round((configuredMax - minAmount) / 100))
      : 1000;

  return (
    <div className="overflow-hidden rounded-[32px] border border-slate-200 bg-white shadow-[0_24px_80px_rgba(15,23,42,0.12)]">
      <div className="grid lg:grid-cols-[1.08fr_.92fr]">
        <div className="p-6 sm:p-8">
          <div
            className="text-[11px] font-bold uppercase tracking-[0.18em]"
            style={{ color: accent }}
          >
            Loan planning tool
          </div>
          <h2 className="mt-2 text-2xl font-black tracking-tight text-slate-950 sm:text-3xl">
            Plan before you apply
          </h2>
          <p className="mt-2 max-w-xl text-sm leading-6 text-slate-500">
            Estimate the contractual repayment using the published product rates
            and the same declining-principal method used by the lending
            schedule.
          </p>

          {products.length > 0 ? (
            <div className="mt-7">
              <div className="mb-3 text-[11px] font-bold uppercase tracking-[0.16em] text-slate-400">
                Product
              </div>
              <div className="flex flex-wrap gap-2">
                {products.map((item, index) => (
                  <button
                    key={`${item.title}-${index}`}
                    type="button"
                    onClick={() => switchProduct(index)}
                    className="rounded-full border px-4 py-2 text-xs font-bold transition hover:-translate-y-0.5 focus:outline-none focus:ring-2 focus:ring-offset-2"
                    style={
                      productIndex === index
                        ? {
                            borderColor: primary,
                            backgroundColor: primary,
                            color: "#fff",
                          }
                        : { borderColor: "#E2E8F0", color: "#475569" }
                    }
                  >
                    {item.title}
                  </button>
                ))}
              </div>
            </div>
          ) : (
            <div className="mt-7 rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800">
              No active loan products are currently available.
            </div>
          )}

          {product && (
            <>
              <div className="mt-7">
                <div className="flex items-center justify-between gap-4">
                  <label
                    htmlFor="loan-amount"
                    className="text-[11px] font-bold uppercase tracking-[0.16em] text-slate-400"
                  >
                    Loan amount
                  </label>
                  <span className="text-xs font-bold text-slate-500">
                    {currency}
                  </span>
                </div>

                <div className="mt-2 flex items-center gap-3 rounded-2xl border border-slate-200 px-4 py-3 focus-within:border-slate-400">
                  <span className="text-xs font-bold text-slate-400">
                    {currency}
                  </span>
                  <input
                    id="loan-amount"
                    type="number"
                    min={minAmount}
                    max={configuredMax ?? undefined}
                    step={amountStep}
                    value={amountInput}
                    onChange={(event) => handleAmountChange(event.target.value)}
                    onBlur={normalizeAmountOnBlur}
                    inputMode="numeric"
                    pattern="[0-9]*"
                    placeholder={fmt(minAmount)}
                    aria-describedby="loan-amount-help loan-amount-error"
                    className="w-full min-w-0 bg-transparent text-2xl font-black tracking-tight text-slate-950 outline-none placeholder:text-slate-300"
                  />
                </div>

                <div
                  id="loan-amount-help"
                  className="mt-2 flex items-center justify-between gap-3 text-[10px] font-semibold text-slate-400"
                >
                  <span>Enter the amount you need</span>
                  <span>Use whole RWF amounts</span>
                </div>
                {(amountBelowMinimum || amountAboveMaximum) && (
                  <p
                    id="loan-amount-error"
                    className="mt-2 text-xs font-semibold text-rose-600"
                    role="alert"
                  >
                    {amountBelowMinimum
                      ? `Minimum available amount is ${currency} ${fmt(minAmount)}.`
                      : `Maximum available amount is ${currency} ${fmt(configuredMax ?? minAmount)}.`}
                  </p>
                )}

                {configuredMax !== null ? (
                  <>
                    <input
                      type="range"
                      min={minAmount}
                      max={configuredMax}
                      step={amountStep}
                      value={Math.min(effectiveAmount, configuredMax)}
                      onChange={(event) =>
                        setAmountInput(
                          String(Math.round(Number(event.target.value))),
                        )
                      }
                      className="mt-4 w-full"
                      style={{ accentColor: primary }}
                      aria-label="Loan amount range"
                    />
                    <div className="mt-2 flex justify-between text-[10px] font-semibold text-slate-400">
                      <span>
                        {currency} {fmt(minAmount)}
                      </span>
                      <span>
                        {currency} {fmt(configuredMax)}
                      </span>
                    </div>
                  </>
                ) : (
                  <div className="mt-3 flex items-center justify-between gap-3 rounded-xl bg-slate-50 px-3 py-2 text-[10px] font-bold text-slate-500">
                    <span>
                      Minimum: {currency} {fmt(minAmount)}
                    </span>
                    <span style={{ color: primary }}>
                      No configured maximum
                    </span>
                  </div>
                )}
              </div>

              <div className="mt-7">
                <div className="mb-3 text-[11px] font-bold uppercase tracking-[0.16em] text-slate-400">
                  Repayment period
                </div>
                <div className="flex flex-wrap gap-2">
                  {termOptions.map((term) => (
                    <button
                      key={term}
                      type="button"
                      onClick={() => setMonths(term)}
                      className="rounded-full border px-4 py-2 text-xs font-bold transition hover:-translate-y-0.5 focus:outline-none focus:ring-2 focus:ring-offset-2"
                      style={
                        months === term
                          ? {
                              borderColor: primary,
                              backgroundColor: primary,
                              color: "#fff",
                            }
                          : { borderColor: "#E2E8F0", color: "#475569" }
                      }
                    >
                      {term} {term === 1 ? "month" : "months"}
                    </button>
                  ))}
                </div>
              </div>

              <div className="mt-7 grid grid-cols-3 gap-3">
                <div className="rounded-2xl bg-slate-50 p-4">
                  <div className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
                    Interest
                  </div>
                  <div className="mt-1 text-sm font-black text-slate-900">
                    {interestRate}% / mo
                  </div>
                </div>
                <div className="rounded-2xl bg-slate-50 p-4">
                  <div className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
                    Management
                  </div>
                  <div className="mt-1 text-sm font-black text-slate-900">
                    {managementRate}% / mo
                  </div>
                </div>
                <div className="rounded-2xl bg-slate-50 p-4">
                  <div className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
                    Processing
                  </div>
                  <div className="mt-1 text-sm font-black text-slate-900">
                    {applicationRate}% once
                  </div>
                </div>
              </div>
            </>
          )}
        </div>

        <div
          className="p-6 sm:p-8"
          style={{ background: `linear-gradient(160deg, ${primary}, #0B223E)` }}
        >
          <div className="text-[11px] font-bold uppercase tracking-[0.18em] text-white/60">
            Indicative contractual repayment
          </div>

          {product ? (
            <>
              <div className="mt-6 text-sm text-white/70">
                First scheduled installment
              </div>
              <div className="mt-1 text-4xl font-black tracking-tight text-white">
                {currency} {fmt(estimate.firstInstallment)}
              </div>
              <div className="mt-1 text-xs text-white/60">
                The installment normally reduces as principal declines.
              </div>

              <div className="mt-8 space-y-3 border-t border-white/10 pt-5">
                <div className="flex items-center justify-between text-sm">
                  <span className="text-white/60">Principal</span>
                  <strong className="text-white">
                    {currency} {fmt(effectiveAmount)}
                  </strong>
                </div>
                <div className="flex items-center justify-between text-sm">
                  <span className="text-white/60">
                    Interest ({interestRate}%/mo)
                  </span>
                  <strong className="text-white">
                    {currency} {fmt(estimate.interest)}
                  </strong>
                </div>
                <div className="flex items-center justify-between text-sm">
                  <span className="text-white/60">
                    Management fee ({managementRate}%/mo)
                  </span>
                  <strong className="text-white">
                    {currency} {fmt(estimate.management)}
                  </strong>
                </div>
                <div className="flex items-center justify-between text-sm">
                  <span className="text-white/60">Processing fee</span>
                  <strong className="text-white">
                    {currency}{" "}
                    {fmt(percentageCharge(effectiveAmount, applicationRate))}
                  </strong>
                </div>
                <div className="flex items-center justify-between text-sm">
                  <span className="text-white/60">
                    Last scheduled installment
                  </span>
                  <strong className="text-white">
                    {currency} {fmt(estimate.lastInstallment)}
                  </strong>
                </div>
                <div className="flex items-center justify-between border-t border-white/10 pt-3 text-sm">
                  <span className="font-bold text-white/80">
                    Contractual repayment total
                  </span>
                  <strong className="text-lg text-white">
                    {currency} {fmt(estimate.total)}
                  </strong>
                </div>
              </div>

              <Link
                href={`/apply${product.title ? `?type=${encodeURIComponent(product.loanType || product.title)}` : ""}`}
                className="mt-8 block rounded-2xl bg-white px-5 py-3.5 text-center text-sm font-black transition hover:-translate-y-0.5 focus:outline-none focus:ring-2 focus:ring-white"
                style={{ color: primary }}
              >
                Start application
              </Link>

              <div className="mt-4 text-center text-[10px] leading-4 text-white/50">
                Indicative only. The final agreement, eligibility, fees and
                schedule are determined by credit assessment and approved loan
                terms. Processing fee is collected separately at disbursement.
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
