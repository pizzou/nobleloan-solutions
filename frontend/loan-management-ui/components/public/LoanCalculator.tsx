"use client";

import React, { useMemo, useState } from "react";

export interface TenantConfig {
  id?: number;
  name?: string;
  slug?: string;
  country?: string;
  currency?: string;
  primaryColor?: string;
  accentColor?: string;
  logoUrl?: string | null;
}

export interface LoanCalculatorProps {
  tenant?: TenantConfig;
  className?: string;
}

const getCurrencySymbol = (currency?: string): string => {
  if (!currency) return "RWF";

  const normalized = currency.toUpperCase();

  const symbols: Record<string, string> = {
    RWF: "RWF",
    USD: "$",
    EUR: "€",
    GBP: "£",
    KES: "KSh",
    UGX: "UGX",
    TZS: "TSh",
    ZAR: "R",
    NGN: "₦",
    GHS: "GH₵",
    XOF: "CFA",
    XAF: "FCFA",
  };

  return symbols[normalized] ?? normalized;
};

const formatMoney = (
  amount: number,
  currency?: string,
  locale = "en-RW",
): string => {
  const safeCurrency = currency || "RWF";

  try {
    return new Intl.NumberFormat(locale, {
      style: "currency",
      currency: safeCurrency,
      maximumFractionDigits: 0,
    }).format(amount);
  } catch {
    return `${getCurrencySymbol(safeCurrency)} ${amount.toLocaleString(
      locale,
    )}`;
  }
};

export default function LoanCalculator({
  tenant,
  className = "",
}: LoanCalculatorProps) {
  const currency = tenant?.currency || "RWF";

  const currencySymbol = useMemo(() => getCurrencySymbol(currency), [currency]);

  const [amount, setAmount] = useState<number>(1000000);
  const [term, setTerm] = useState<number>(6);

  /*
   * IMPORTANT:
   * This calculator is a public planning tool.
   * It must not replace the backend loan calculation.
   *
   * The backend remains authoritative for:
   * - daily interest accrual
   * - management fee
   * - processing fee
   * - repayment schedule
   * - outstanding principal
   */

  const estimatedPrincipal = amount;

  return (
    <section
      className={[
        "w-full overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-[0_24px_80px_rgba(15,23,42,0.10)]",
        className,
      ].join(" ")}
    >
      <div className="border-b border-slate-100 bg-slate-950 px-6 py-8 text-white sm:px-8">
        <div className="max-w-2xl">
          <p className="mb-2 text-xs font-semibold uppercase tracking-[0.22em] text-emerald-300">
            {tenant?.name || "Noble Loan"}
          </p>

          <h2 className="text-2xl font-semibold tracking-tight sm:text-3xl">
            Plan your financing
          </h2>

          <p className="mt-3 text-sm leading-6 text-slate-300">
            Explore an indicative financing amount and term. Final pricing, fees
            and repayment schedules are determined by the approved loan terms
            and the lending platform.
          </p>
        </div>
      </div>

      <div className="grid gap-8 p-6 sm:p-8 lg:grid-cols-[1.1fr_0.9fr]">
        <div className="space-y-7">
          <div>
            <label
              htmlFor="loan-amount"
              className="mb-2 block text-sm font-semibold text-slate-900"
            >
              Financing amount
            </label>

            <div className="relative">
              <span className="absolute left-4 top-1/2 -translate-y-1/2 text-sm font-semibold text-slate-500">
                {currencySymbol}
              </span>

              <input
                id="loan-amount"
                type="number"
                min={0}
                value={amount}
                onChange={(event) =>
                  setAmount(Math.max(0, Number(event.target.value)))
                }
                className="w-full rounded-2xl border border-slate-200 bg-slate-50 py-4 pl-16 pr-4 text-lg font-semibold text-slate-950 outline-none transition focus:border-slate-950 focus:bg-white focus:ring-4 focus:ring-slate-950/5"
              />
            </div>
          </div>

          <div>
            <label
              htmlFor="loan-term"
              className="mb-2 block text-sm font-semibold text-slate-900"
            >
              Preferred term
            </label>

            <select
              id="loan-term"
              value={term}
              onChange={(event) => setTerm(Number(event.target.value))}
              className="w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4 text-base font-medium text-slate-950 outline-none transition focus:border-slate-950 focus:bg-white focus:ring-4 focus:ring-slate-950/5"
            >
              {[3, 6, 9, 12, 18, 24, 36].map((months) => (
                <option key={months} value={months}>
                  {months} months
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className="rounded-3xl bg-slate-50 p-6">
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">
            Indicative amount
          </p>

          <p className="mt-3 text-3xl font-bold tracking-tight text-slate-950">
            {formatMoney(estimatedPrincipal, currency)}
          </p>

          <div className="mt-6 border-t border-slate-200 pt-5">
            <div className="flex items-center justify-between py-2">
              <span className="text-sm text-slate-500">Requested amount</span>
              <span className="font-semibold text-slate-900">
                {formatMoney(amount, currency)}
              </span>
            </div>

            <div className="flex items-center justify-between py-2">
              <span className="text-sm text-slate-500">Preferred term</span>
              <span className="font-semibold text-slate-900">
                {term} months
              </span>
            </div>
          </div>

          <div className="mt-6 rounded-2xl border border-slate-200 bg-white p-4">
            <p className="text-xs font-semibold uppercase tracking-[0.15em] text-slate-500">
              Important
            </p>

            <p className="mt-2 text-sm leading-6 text-slate-600">
              This calculator provides an indicative planning view. Your final
              repayment schedule is generated by the lending system after
              approval and is based on the actual approved terms.
            </p>
          </div>
        </div>
      </div>
    </section>
  );
}
