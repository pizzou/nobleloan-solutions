"use client";

import { useEffect, useState } from "react";

export type FinancialPeriod = { from: string; to: string };

type Preset = "this-month" | "last-month" | "this-year" | "all-history" | "custom";

function iso(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

export function financialPeriodPreset(preset: Preset, now = new Date()): FinancialPeriod {
  const y = now.getFullYear();
  const m = now.getMonth();
  switch (preset) {
    case "last-month":
      return { from: iso(new Date(y, m - 1, 1)), to: iso(new Date(y, m, 0)) };
    case "this-year":
      return { from: `${y}-01-01`, to: iso(now) };
    case "all-history":
      return { from: "1900-01-01", to: iso(now) };
    case "this-month":
    case "custom":
      return { from: iso(new Date(y, m, 1)), to: iso(now) };
  }
}

export default function FinancialPeriodFilter({
  value,
  onApply,
  title = "Financial reporting period",
}: {
  value: FinancialPeriod;
  onApply: (period: FinancialPeriod) => void;
  title?: string;
}) {
  const [draft, setDraft] = useState<FinancialPeriod>(value);
  const [preset, setPreset] = useState<Preset>(() => {
    for (const candidate of ["this-month", "last-month", "this-year", "all-history"] as const) {
      const expected = financialPeriodPreset(candidate);
      if (expected.from === value.from && expected.to === value.to) {
        return candidate;
      }
    }
    return "custom";
  });
  useEffect(() => setDraft(value), [value.from, value.to]);
  const valid = /^\d{4}-\d{2}-\d{2}$/.test(draft.from)
    && /^\d{4}-\d{2}-\d{2}$/.test(draft.to)
    && draft.from <= draft.to;
  const changed = draft.from !== value.from || draft.to !== value.to;

  function choose(next: Preset) {
    setPreset(next);
    if (next === "custom") return;
    const range = financialPeriodPreset(next);
    setDraft(range);
    // One click immediately refreshes the statements for a preset.
    onApply(range);
  }

  return (
    <section aria-label={title} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-sm font-bold text-slate-900">{title}</h2>
        <span className="text-xs text-slate-500">Statements: {value.from} to {value.to}</span>
      </div>
      <div className="grid gap-3 md:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_minmax(0,1fr)_auto] md:items-end">
        <label className="block text-xs font-semibold text-slate-700">
          Period
          <select
            value={preset}
            onChange={event => choose(event.target.value as Preset)}
            className="mt-1 block w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
          >
            <option value="this-month">This month</option>
            <option value="last-month">Last month</option>
            <option value="this-year">This year</option>
            <option value="all-history">All history</option>
            <option value="custom">Custom period</option>
          </select>
        </label>
        <label className="block text-xs font-semibold text-slate-700">
          From date
          <input type="date" value={draft.from}
            onChange={event => { setPreset("custom"); setDraft(old => ({ ...old, from: event.target.value })); }}
            className="mt-1 block w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
        </label>
        <label className="block text-xs font-semibold text-slate-700">
          To date
          <input type="date" value={draft.to}
            onChange={event => { setPreset("custom"); setDraft(old => ({ ...old, to: event.target.value })); }}
            className="mt-1 block w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
        </label>
        <button type="button" onClick={() => onApply({ ...draft })} disabled={!valid || !changed}
          className="rounded-lg bg-slate-900 px-5 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-40">
          Apply period
        </button>
      </div>
      {!valid && <p className="mt-2 text-xs text-red-700">Enter valid dates with From on or before To.</p>}
      <p className="mt-2 text-xs text-slate-500">
        Profit &amp; Loss and Cash Flow use both dates. Trial Balance and Balance Sheet show the position as of the To date.
        Portfolio overview figures may represent current lifetime totals.
      </p>
    </section>
  );
}
