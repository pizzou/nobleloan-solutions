"use client";

import { useEffect, useMemo, useState } from "react";
import { currencyApi } from "../../services/api";

const WATCH = ["USD", "EUR", "GBP", "KES", "UGX", "TZS", "RWF"];

type Rate = {
  baseCurrency?: string;
  targetCurrency?: string;
  rate?: number;
  fetchedAt?: string;
};

type Props = {
  baseCurrency: string;
  primary: string;
};

export default function FxWidget({ baseCurrency, primary }: Props) {
  const base = baseCurrency || "RWF";
  const [rates, setRates] = useState<Rate[]>([]);
  const [loading, setLoading] = useState(true);
  const [lastUpdated, setLastUpdated] = useState<string | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;

    const load = async () => {
      try {
        setError("");
        const response = await currencyApi.rates("USD");
        const usdRates = Array.isArray(response) ? (response as Rate[]) : [];
        const usdToBase = base === "USD" ? 1 : Number(usdRates.find((item) => item.targetCurrency === base)?.rate ?? 0);
        const next = usdRates.map((item) => ({
          ...item,
          baseCurrency: base,
          rate: usdToBase > 0 ? Number(item.rate ?? 0) / usdToBase : 0,
        }));
        if (cancelled) return;
        setRates(next);
        const latest = next
          .map((item) => item.fetchedAt)
          .filter(Boolean)
          .sort()
          .at(-1);
        setLastUpdated(latest ?? null);
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "FX rates unavailable");
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    void load();
    const timer = window.setInterval(load, 60_000);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [base]);

  const visibleRates = useMemo(
    () =>
      WATCH.filter((currency) => currency !== base)
        .map((currency) => rates.find((rate) => rate.targetCurrency === currency))
        .filter(Boolean) as Rate[],
    [rates, base],
  );

  return (
    <section className="border-y border-slate-100 bg-white">
      <div className="mx-auto max-w-7xl px-4 py-4">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div className="flex items-start gap-3">
            <div
              className="flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl text-lg shadow-sm"
              style={{ backgroundColor: `${primary}10`, color: primary }}
            >
              FX
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="text-sm font-black text-slate-950">Latest FX reference rates</h3>
                <span className="rounded-full bg-emerald-50 px-2 py-1 text-[9px] font-black uppercase tracking-wider text-emerald-700">
                  Live reference
                </span>
              </div>
              <p className="mt-1 text-xs text-slate-500">
                Base currency: <strong>{base}</strong>. Rates are supplied by the platform FX service and may change.
              </p>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            {loading ? (
              <span className="text-xs font-semibold text-slate-400">Loading rates…</span>
            ) : error ? (
              <span className="text-xs font-semibold text-amber-600">FX temporarily unavailable</span>
            ) : visibleRates.length === 0 ? (
              <span className="text-xs font-semibold text-slate-400">No rates available</span>
            ) : (
              visibleRates.map((rate) => (
                <div
                  key={rate.targetCurrency}
                  className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2"
                >
                  <div className="text-[9px] font-black uppercase tracking-wider text-slate-400">
                    1 {base}
                  </div>
                  <div className="text-xs font-black text-slate-900">
                    {Number(rate.rate ?? 0).toLocaleString(undefined, {
                      maximumFractionDigits: 4,
                    })} {rate.targetCurrency}
                  </div>
                </div>
              ))
            )}
          </div>

          <div className="text-[10px] font-semibold text-slate-400 lg:text-right">
            {lastUpdated ? `Updated ${new Date(lastUpdated).toLocaleString()}` : "Awaiting latest published rate"}
          </div>
        </div>
      </div>
    </section>
  );
}
