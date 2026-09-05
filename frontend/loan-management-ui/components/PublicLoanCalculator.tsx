"use client";

import { useEffect, useState } from "react";
import { publicApi } from "@/services/api";
import { TENANT_SLUG } from "@/lib/tenant";
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
  const interestRate = numeric(product?.interestRate ?? product?.rate, 5);
  const managementRate = numeric(product?.managementFeeRate, 5);
  const applicationRate = numeric(product?.applicationFeeRate, 2);

  const [amount, setAmount] = useState(minAmount);
  const [months, setMonths] = useState(terms.min);

  function switchProduct(index: number) {
    const next = products[index];
    const nextMin = numeric(next?.minAmount, 500000);
    setProductIndex(index);
    setAmount(nextMin);
    setMonths(parseTerm(next).min);
  }

  function setSafeAmount(value: number) {
    if (!Number.isFinite(value) || value < minAmount) {
      setAmount(minAmount);
      return;
    }

    if (configuredMax !== null && value > configuredMax) {
      setAmount(configuredMax);
      return;
    }

    setAmount(value);
  }

  const [estimate, setEstimate] = useState({
    interest: 0,
    management: 0,
    applicationFee: 0,
    total: 0,
    totalCostIncludingApplicationFee: 0,
    firstInstallment: 0,
    lastInstallment: 0,
  });
  const [quoteLoading, setQuoteLoading] = useState(false);
  const [quoteError, setQuoteError] = useState("");

  useEffect(() => {
    if (
      !product ||
      !Number.isFinite(amount) ||
      amount <= 0 ||
      !Number.isFinite(months)
    ) {
      return;
    }

    let cancelled = false;
    const timer = window.setTimeout(async () => {
      setQuoteLoading(true);
      setQuoteError("");
      try {
        const quote = await publicApi.calculateLoan({
          tenantSlug: TENANT_SLUG,
          loanType: product.loanType || product.title,
          amount,
          durationMonths: months,
        });
        if (!cancelled) {
          setEstimate({
            interest: Number(quote.totalInterest ?? 0),
            management: Number(quote.totalManagementFee ?? 0),
            applicationFee: Number(quote.applicationFee ?? 0),
            total: Number(quote.contractualRepaymentTotal ?? 0),
            totalCostIncludingApplicationFee: Number(
              quote.totalCostIncludingApplicationFee ?? 0,
            ),
            firstInstallment: Number(quote.firstInstallment ?? 0),
            lastInstallment: Number(quote.lastInstallment ?? 0),
          });
        }
      } catch (error: any) {
        if (!cancelled) {
          setQuoteError(
            error?.message ||
              "Unable to obtain the current lender calculation.",
          );
        }
      } finally {
        if (!cancelled) setQuoteLoading(false);
      }
    }, 250);

    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [product, amount, months]);

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
                    value={amount}
                    onChange={(event) =>
                      setSafeAmount(Number(event.target.value))
                    }
                    className="w-full bg-transparent text-xl font-black text-slate-950 outline-none"
                  />
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
                        setSafeAmount(Number(event.target.value))
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
                    Application
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
                {quoteLoading
                  ? "Calculating…"
                  : `${currency} ${fmt(estimate.firstInstallment)}`}
              </div>
              <div className="mt-1 text-xs text-white/60">
                The installment normally reduces as principal declines.
              </div>

              <div className="mt-8 space-y-3 border-t border-white/10 pt-5">
                <div className="flex items-center justify-between text-sm">
                  <span className="text-white/60">Principal</span>
                  <strong className="text-white">
                    {currency} {fmt(amount)}
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
                    {currency} {fmt(estimate.applicationFee)}
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

              {quoteError && (
                <div className="mt-4 rounded-xl border border-white/10 bg-white/5 px-3 py-2 text-center text-[11px] leading-4 text-white/70">
                  {quoteError}
                </div>
              )}

              <div className="mt-3 text-center text-[10px] text-white/50">
                Total cost including the one-time application fee: {currency}{" "}
                {fmt(estimate.totalCostIncludingApplicationFee)}
              </div>

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
