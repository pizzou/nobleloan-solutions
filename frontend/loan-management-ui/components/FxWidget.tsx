"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { currencyApi } from "../services/api";
import type { TenantConfig } from "../app/(site)/layout";

interface RateRow {
  targetCurrency?: string;
  rate?: number | string;
  fetchedAt?: string;
}

function numberValue(value: unknown): number | null {
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

function money(value: number, currency: string) {
  return new Intl.NumberFormat("en-RW", {
    style: "currency",
    currency,
    maximumFractionDigits: 2,
  }).format(value);
}

export default function FxRatePanel({ tenant }: { tenant: TenantConfig }) {
  const base = (tenant.currency || "RWF").toUpperCase();
  const [rates, setRates] = useState<RateRow[]>([]);
  const [target, setTarget] = useState(base === "USD" ? "EUR" : "USD");
  const [amount, setAmount] = useState(1);
  const [converted, setConverted] = useState<number | null>(null);
  const [rate, setRate] = useState<number | null>(null);
  const [updatedAt, setUpdatedAt] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const result = await currencyApi.rates("USD");
      const list = Array.isArray(result) ? (result as RateRow[]) : [];
      setRates(list);

      if (base === "USD") {
        const targetRow = list.find((item) => item.targetCurrency === target);
        const targetRate = numberValue(targetRow?.rate);
        setRate(targetRate);
        setConverted(targetRate == null ? null : amount * targetRate);
        setUpdatedAt(targetRow?.fetchedAt || null);
      } else {
        const baseRow = list.find((item) => item.targetCurrency === base);
        const targetRow = list.find((item) => item.targetCurrency === target);
        const baseRate = numberValue(baseRow?.rate);
        const targetRate = numberValue(targetRow?.rate);
        const cross = baseRate && targetRate ? targetRate / baseRate : null;
        setRate(cross);
        setConverted(cross == null ? null : amount * cross);
        setUpdatedAt(targetRow?.fetchedAt || baseRow?.fetchedAt || null);
      }
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "Live FX rates are temporarily unavailable.",
      );
      setRates([]);
      setRate(null);
      setConverted(null);
    } finally {
      setLoading(false);
    }
  }, [amount, base, target]);

  useEffect(() => {
    void load();
    const timer = window.setInterval(() => void load(), 5 * 60 * 1000);
    return () => window.clearInterval(timer);
  }, [load]);

  const supportedTargets = useMemo(() => {
    const codes = rates
      .map((item) => item.targetCurrency)
      .filter(Boolean) as string[];
    return Array.from(
      new Set(["USD", "EUR", "GBP", "KES", "UGX", "TZS", ...codes]),
    ).filter((code) => code !== base);
  }, [base, rates]);

  return (
    <section className="rounded-[2rem] border border-slate-200 bg-white p-7 shadow-[0_18px_60px_rgba(15,23,42,0.08)] md:p-8">
      <div className="flex flex-col gap-5 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <div className="text-[11px] font-black uppercase tracking-[0.22em] text-slate-500">
            Live foreign exchange
          </div>
          <h2 className="mt-2 text-2xl font-black tracking-tight text-slate-950">
            Real-time FX reference
          </h2>
          <p className="mt-2 max-w-xl text-sm leading-6 text-slate-600">
            Indicative conversion using the lender platform's latest cached
            market rates. Rates refresh automatically.
          </p>
        </div>
        <button
          type="button"
          onClick={() => void load()}
          disabled={loading}
          className="rounded-xl border border-slate-300 px-4 py-2.5 text-xs font-black text-slate-700 hover:bg-slate-50 disabled:opacity-50"
        >
          {loading ? "Refreshing…" : "Refresh rates"}
        </button>
      </div>

      <div className="mt-7 grid gap-5 lg:grid-cols-[1fr_0.82fr]">
        <div className="rounded-2xl bg-slate-50 p-5">
          <div className="grid gap-4 sm:grid-cols-3">
            <div>
              <label className="text-[10px] font-black uppercase tracking-wider text-slate-500">
                Amount
              </label>
              <input
                value={amount}
                onChange={(event) =>
                  setAmount(Math.max(0, Number(event.target.value) || 0))
                }
                type="number"
                min="0"
                step="0.01"
                className="mt-2 w-full rounded-xl border border-slate-300 bg-white px-4 py-3 text-sm font-semibold outline-none"
              />
            </div>
            <div>
              <label className="text-[10px] font-black uppercase tracking-wider text-slate-500">
                From
              </label>
              <div className="mt-2 rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm font-black text-slate-900">
                {base}
              </div>
            </div>
            <div>
              <label className="text-[10px] font-black uppercase tracking-wider text-slate-500">
                To
              </label>
              <select
                value={target}
                onChange={(event) => setTarget(event.target.value)}
                className="mt-2 w-full rounded-xl border border-slate-300 bg-white px-4 py-3 text-sm font-semibold outline-none"
              >
                {supportedTargets.map((code) => (
                  <option key={code} value={code}>
                    {code}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div
            className="mt-7 rounded-2xl bg-slate-950 p-6 text-white"
            style={{
              background: `linear-gradient(145deg, ${tenant.primaryColor}, #071427)`,
            }}
          >
            <div className="text-[10px] font-black uppercase tracking-[0.2em] text-white/50">
              Indicative conversion
            </div>
            {converted != null ? (
              <>
                <div className="mt-3 text-3xl font-black">
                  {money(amount, base)} → {money(converted, target)}
                </div>
                <div className="mt-2 text-sm text-white/60">
                  1 {base} ={" "}
                  {rate?.toLocaleString(undefined, {
                    maximumFractionDigits: 8,
                  })}{" "}
                  {target}
                </div>
              </>
            ) : (
              <div className="mt-3 text-lg font-bold text-white/60">
                Rate unavailable
              </div>
            )}
          </div>

          <div className="mt-4 flex flex-wrap items-center justify-between gap-3 text-xs text-slate-500">
            <span>
              {updatedAt
                ? `Last updated ${new Date(updatedAt).toLocaleString()}`
                : "Waiting for latest rate timestamp"}
            </span>
            <span>Market reference only — not a settlement quote.</span>
          </div>
          {error && (
            <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs text-amber-800">
              {error}
            </div>
          )}
        </div>

        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-1">
          {supportedTargets.slice(0, 5).map((code) => (
            <button
              key={code}
              type="button"
              onClick={() => setTarget(code)}
              className={`rounded-2xl border p-4 text-left transition ${target === code ? "border-slate-900 bg-slate-50 shadow-sm" : "border-slate-200 bg-white hover:border-slate-300"}`}
            >
              <div className="flex items-center justify-between gap-4">
                <span className="text-sm font-black text-slate-900">
                  {base} / {code}
                </span>
                <span className="text-xs font-bold text-slate-400">View</span>
              </div>
              <div className="mt-2 text-sm text-slate-500">
                {target === code && rate != null
                  ? rate.toLocaleString(undefined, { maximumFractionDigits: 8 })
                  : "Live reference"}
              </div>
            </button>
          ))}
        </div>
      </div>
    </section>
  );
}
