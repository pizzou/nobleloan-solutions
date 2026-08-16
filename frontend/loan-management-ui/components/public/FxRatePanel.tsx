"use client";

import React, { useEffect, useMemo, useState } from "react";

type FxRatePanelProps = {
  baseCurrency?: string;
  defaultCurrency?: string;
  className?: string;
};

type RateResponse = {
  rates?: Record<string, number>;
};

const SUPPORTED_CURRENCIES = [
  { code: "RWF", name: "Rwandan Franc" },
  { code: "USD", name: "US Dollar" },
  { code: "EUR", name: "Euro" },
  { code: "GBP", name: "British Pound" },
  { code: "KES", name: "Kenyan Shilling" },
  { code: "UGX", name: "Ugandan Shilling" },
  { code: "TZS", name: "Tanzanian Shilling" },
];

const currencyFormatter = new Intl.NumberFormat("en-US", {
  maximumFractionDigits: 2,
});

export default function FxRatePanel({
  baseCurrency = "RWF",
  defaultCurrency = "USD",
  className = "",
}: FxRatePanelProps) {
  const [currency, setCurrency] = useState(defaultCurrency);
  const [amount, setAmount] = useState("1");
  const [rate, setRate] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);

  const selectedCurrency = useMemo(
    () =>
      SUPPORTED_CURRENCIES.find((item) => item.code === currency) ??
      SUPPORTED_CURRENCIES[1],
    [currency],
  );

  useEffect(() => {
    let cancelled = false;

    async function loadRate() {
      setLoading(true);
      setError(false);

      try {
        const response = await fetch(
          `https://open.er-api.com/v6/latest/${encodeURIComponent(
            baseCurrency,
          )}`,
          {
            method: "GET",
            headers: {
              Accept: "application/json",
            },
            cache: "no-store",
          },
        );

        if (!response.ok) {
          throw new Error("Unable to retrieve FX rate");
        }

        const data = (await response.json()) as RateResponse;

        const nextRate = data.rates?.[currency];

        if (
          typeof nextRate !== "number" ||
          !Number.isFinite(nextRate) ||
          nextRate <= 0
        ) {
          throw new Error("Invalid FX rate");
        }

        if (!cancelled) {
          setRate(nextRate);
          setLastUpdated(new Date());
        }
      } catch {
        if (!cancelled) {
          setRate(null);
          setError(true);
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    loadRate();

    return () => {
      cancelled = true;
    };
  }, [baseCurrency, currency]);

  const numericAmount = Number(amount.replace(/,/g, ""));

  const convertedAmount =
    rate !== null && Number.isFinite(numericAmount)
      ? numericAmount * rate
      : null;

  return (
    <section
      className={[
        "relative overflow-hidden rounded-3xl border border-slate-200/80",
        "bg-white shadow-[0_20px_60px_rgba(15,23,42,0.08)]",
        className,
      ].join(" ")}
      aria-label="Foreign exchange information"
    >
      <div className="absolute right-0 top-0 h-40 w-40 rounded-full bg-emerald-100/40 blur-3xl" />

      <div className="relative p-6 sm:p-7">
        <div className="flex flex-col gap-5 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <div className="mb-3 inline-flex items-center gap-2 rounded-full border border-emerald-100 bg-emerald-50 px-3 py-1.5 text-xs font-semibold text-emerald-700">
              <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
              Indicative FX
            </div>

            <h3 className="text-xl font-semibold tracking-tight text-slate-950">
              Foreign exchange reference
            </h3>

            <p className="mt-1 max-w-lg text-sm leading-6 text-slate-500">
              Indicative currency information for planning and international
              transactions.
            </p>
          </div>

          <div className="flex items-center gap-2 text-xs text-slate-500">
            <span className="h-2 w-2 rounded-full bg-emerald-500" />
            Live reference
          </div>
        </div>

        <div className="mt-7 grid gap-4 lg:grid-cols-[1fr_auto_1fr] lg:items-end">
          <div>
            <label
              htmlFor="fx-amount"
              className="mb-2 block text-xs font-semibold uppercase tracking-[0.14em] text-slate-500"
            >
              Amount
            </label>

            <div className="flex overflow-hidden rounded-2xl border border-slate-200 bg-slate-50 transition focus-within:border-emerald-500 focus-within:ring-4 focus-within:ring-emerald-500/10">
              <input
                id="fx-amount"
                type="text"
                inputMode="decimal"
                value={amount}
                onChange={(event) => {
                  const value = event.target.value;

                  if (/^[0-9,]*\.?[0-9]*$/.test(value)) {
                    setAmount(value);
                  }
                }}
                className="min-w-0 flex-1 bg-transparent px-4 py-3.5 text-lg font-semibold text-slate-950 outline-none"
                placeholder="1"
              />

              <div className="border-l border-slate-200 px-4 py-3.5 text-sm font-bold text-slate-600">
                {baseCurrency}
              </div>
            </div>
          </div>

          <div className="hidden h-11 w-11 items-center justify-center rounded-full border border-slate-200 bg-white text-slate-400 lg:flex">
            →
          </div>

          <div>
            <label
              htmlFor="fx-currency"
              className="mb-2 block text-xs font-semibold uppercase tracking-[0.14em] text-slate-500"
            >
              Convert to
            </label>

            <select
              id="fx-currency"
              value={currency}
              onChange={(event) => setCurrency(event.target.value)}
              className="w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3.5 text-sm font-semibold text-slate-900 outline-none transition focus:border-emerald-500 focus:ring-4 focus:ring-emerald-500/10"
            >
              {SUPPORTED_CURRENCIES.filter(
                (item) => item.code !== baseCurrency,
              ).map((item) => (
                <option key={item.code} value={item.code}>
                  {item.code} — {item.name}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className="mt-5 rounded-2xl bg-slate-950 p-5 text-white">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <p className="text-xs font-semibold uppercase tracking-[0.14em] text-slate-400">
                Indicative conversion
              </p>

              {loading ? (
                <div className="mt-2 h-9 w-48 animate-pulse rounded-lg bg-white/10" />
              ) : convertedAmount !== null ? (
                <p className="mt-2 text-3xl font-semibold tracking-tight">
                  {currencyFormatter.format(convertedAmount)}{" "}
                  <span className="text-lg text-slate-400">
                    {selectedCurrency.code}
                  </span>
                </p>
              ) : (
                <p className="mt-2 text-lg font-medium text-slate-300">
                  Rate unavailable
                </p>
              )}
            </div>

            <div className="text-left sm:text-right">
              {rate !== null ? (
                <>
                  <p className="text-sm font-medium text-slate-300">
                    1 {baseCurrency} ≈ {currencyFormatter.format(rate)}{" "}
                    {currency}
                  </p>

                  {lastUpdated && (
                    <p className="mt-1 text-xs text-slate-500">
                      Updated {lastUpdated.toLocaleTimeString()}
                    </p>
                  )}
                </>
              ) : (
                <p className="text-xs text-slate-500">
                  {error
                    ? "Reference rate temporarily unavailable."
                    : "Loading reference rate…"}
                </p>
              )}
            </div>
          </div>
        </div>

        <p className="mt-4 text-xs leading-5 text-slate-400">
          FX information is indicative only and must not be used as the
          authoritative rate for loan settlement, accounting, or regulatory
          calculations.
        </p>
      </div>
    </section>
  );
}
