'use client';

import { useEffect, useMemo, useState } from 'react';

type RatePoint = {
  base: string;
  quote: string;
  rate: number;
  date: string;
};

const WATCH = ['USD', 'EUR', 'GBP'];

function formatRate(rate: number) {
  if (!Number.isFinite(rate)) return '—';
  return rate >= 100
    ? rate.toLocaleString(undefined, { maximumFractionDigits: 2 })
    : rate.toLocaleString(undefined, { maximumFractionDigits: 4 });
}

export default function ExchangeRatePanel({
  baseCurrency = 'RWF',
  primary,
  accent,
}: {
  baseCurrency?: string;
  primary: string;
  accent: string;
}) {
  const [rates, setRates] = useState<RatePoint[]>([]);
  const [selected, setSelected] = useState('USD');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const cleanBase = baseCurrency.toUpperCase();

  useEffect(() => {
    let cancelled = false;

    async function load() {
      setLoading(true);
      setError('');

      try {
        if (WATCH.includes(cleanBase)) {
          const quotes = WATCH.filter((currency) => currency !== cleanBase).join(',');
          const response = await fetch(
            `https://api.frankfurter.dev/v2/rates?base=${encodeURIComponent(cleanBase)}&quotes=${encodeURIComponent(quotes)}`,
            { cache: 'no-store' },
          );
          if (!response.ok) throw new Error('Rate request failed');
          const json = (await response.json()) as RatePoint[];
          if (!cancelled) setRates(Array.isArray(json) ? json : []);
        } else {
          const results = await Promise.all(
            WATCH.map(async (quote) => {
              const response = await fetch(
                `https://api.frankfurter.dev/v2/rate/${encodeURIComponent(cleanBase)}/${encodeURIComponent(quote)}`,
                { cache: 'no-store' },
              );
              if (!response.ok) throw new Error('Rate request failed');
              return (await response.json()) as RatePoint;
            }),
          );
          if (!cancelled) setRates(results.filter(Boolean));
        }
      } catch {
        if (!cancelled) setError('Rates are temporarily unavailable.');
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    void load();
    const timer = window.setInterval(load, 15 * 60 * 1000);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [cleanBase]);

  const selectedRate = useMemo(
    () => rates.find((rate) => rate.quote === selected) ?? rates[0],
    [rates, selected],
  );

  const updated = selectedRate?.date
    ? new Date(`${selectedRate.date}T00:00:00`).toLocaleDateString(undefined, {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
      })
    : null;

  return (
    <div className="overflow-hidden rounded-[28px] border border-slate-200 bg-white shadow-[0_18px_60px_rgba(15,23,42,0.08)]">
      <div className="flex items-center justify-between gap-4 border-b border-slate-100 px-5 py-4">
        <div>
          <div className="text-[11px] font-bold uppercase tracking-[0.18em]" style={{ color: accent }}>
            Currency monitor
          </div>
          <div className="mt-1 text-lg font-bold text-slate-950">Latest published reference rates</div>
        </div>
        <div className="rounded-full px-3 py-1 text-[11px] font-bold" style={{ backgroundColor: `${accent}14`, color: primary }}>
          {cleanBase}
        </div>
      </div>

      <div className="p-5 sm:p-6">
        <div className="mb-5 flex flex-wrap gap-2">
          {WATCH.map((currency) => (
            <button
              key={currency}
              type="button"
              onClick={() => setSelected(currency)}
              className="rounded-full border px-3 py-1.5 text-xs font-bold transition"
              style={
                selected === currency
                  ? { borderColor: primary, backgroundColor: primary, color: '#fff' }
                  : { borderColor: '#E2E8F0', color: '#475569' }
              }
            >
              {currency}
            </button>
          ))}
        </div>

        {loading ? (
          <div className="animate-pulse space-y-3">
            <div className="h-10 rounded-2xl bg-slate-100" />
            <div className="h-4 w-2/3 rounded bg-slate-100" />
          </div>
        ) : error ? (
          <div className="rounded-2xl border border-amber-200 bg-amber-50 px-4 py-4 text-sm text-amber-800">
            {error}
          </div>
        ) : selectedRate ? (
          <>
            <div className="flex items-end justify-between gap-4">
              <div>
                <div className="text-xs font-semibold text-slate-500">1 {cleanBase} equals</div>
                <div className="mt-1 text-3xl font-black tracking-tight text-slate-950">
                  {formatRate(selectedRate.rate)} {selectedRate.quote}
                </div>
              </div>
              <div className="rounded-2xl px-3 py-2 text-right" style={{ backgroundColor: `${primary}08` }}>
                <div className="text-[10px] font-bold uppercase tracking-wider text-slate-400">Reference date</div>
                <div className="mt-1 text-xs font-bold" style={{ color: primary }}>{updated ?? '—'}</div>
              </div>
            </div>
            <p className="mt-4 text-[11px] leading-5 text-slate-400">
              Indicative reference-rate information only. This is not a live dealing or settlement quote.
            </p>
          </>
        ) : (
          <div className="text-sm text-slate-500">No rate available.</div>
        )}
      </div>
    </div>
  );
}
