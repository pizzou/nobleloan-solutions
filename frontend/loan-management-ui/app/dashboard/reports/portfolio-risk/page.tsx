"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";

import {
  getPortfolioRiskAnalytics,
  PortfolioRiskAnalytics,
} from "../../../../services/portfolioRiskService";

function numberValue(value: unknown): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function money(value: unknown): string {
  return new Intl.NumberFormat("en-RW", {
    style: "currency",
    currency: "RWF",
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  }).format(numberValue(value));
}

function percent(value: unknown): string {
  return `${numberValue(value).toFixed(2)}%`;
}

function count(value: unknown): string {
  return new Intl.NumberFormat("en-RW").format(numberValue(value));
}

function riskTone(value: unknown): string {
  const pct = numberValue(value);
  if (pct >= 20) return "border-red-200 bg-red-50 text-red-800";
  if (pct >= 10) return "border-amber-200 bg-amber-50 text-amber-800";
  return "border-emerald-200 bg-emerald-50 text-emerald-800";
}

export default function PortfolioRiskPage() {
  const [data, setData] = useState<PortfolioRiskAnalytics | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState("");

  const load = useCallback(async (refresh = false) => {
    try {
      setError("");
      if (refresh) setRefreshing(true);
      else setLoading(true);

      setData(await getPortfolioRiskAnalytics());
    } catch (err: any) {
      console.error("Portfolio risk analytics failed", err);
      setError(err?.message || "Unable to load portfolio risk analytics.");
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const parCards = useMemo(() => {
    if (!data) return [];
    return [
      {
        key: "PAR1",
        label: "PAR1",
        days: "1+ days",
        loans: data.par1LoanCount,
        amount: data.par1Amount,
        pct: data.par1Pct,
      },
      {
        key: "PAR7",
        label: "PAR7",
        days: "7+ days",
        loans: data.par7LoanCount,
        amount: data.par7Amount,
        pct: data.par7Pct,
      },
      {
        key: "PAR30",
        label: "PAR30",
        days: "30+ days",
        loans: data.par30LoanCount,
        amount: data.par30Amount,
        pct: data.par30Pct,
      },
      {
        key: "PAR60",
        label: "PAR60",
        days: "60+ days",
        loans: data.par60LoanCount,
        amount: data.par60Amount,
        pct: data.par60Pct,
      },
      {
        key: "PAR90",
        label: "PAR90",
        days: "90+ days",
        loans: data.par90LoanCount,
        amount: data.par90Amount,
        pct: data.par90Pct,
      },
    ];
  }, [data]);

  if (loading) {
    return (
      <main className="min-h-screen bg-slate-50 px-4 py-10 sm:px-8">
        <div className="mx-auto max-w-7xl animate-pulse space-y-6">
          <div className="h-8 w-72 rounded bg-slate-200" />
          <div className="grid gap-4 md:grid-cols-3 lg:grid-cols-6">
            {Array.from({ length: 6 }).map((_, i) => (
              <div key={i} className="h-32 rounded-xl bg-white shadow-sm" />
            ))}
          </div>
          <div className="h-96 rounded-xl bg-white shadow-sm" />
        </div>
      </main>
    );
  }

  return (
    <main className="min-h-screen bg-slate-50 px-4 py-8 sm:px-8">
      <div className="mx-auto max-w-7xl">
        <header className="mb-8 flex flex-col gap-4 border-b border-slate-200 pb-6 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <div className="mb-2 text-[10px] font-bold uppercase tracking-[0.18em] text-slate-500">
              Credit Risk / Portfolio Quality
            </div>
            <h1 className="text-2xl font-black tracking-tight text-slate-950">
              Portfolio at Risk
            </h1>
            <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-600">
              Cumulative PAR1, PAR7, PAR30, PAR60 and PAR90 measured against
              current outstanding principal. The dashboard uses persisted loan
              days overdue and excludes non-receivable pipeline loans.
            </p>
          </div>

          <div className="flex items-center gap-3">
            <Link
              href="/dashboard/reports"
              className="rounded-lg border border-slate-300 bg-white px-4 py-2 text-xs font-bold text-slate-700 hover:bg-slate-50"
            >
              Back to reports
            </Link>
            <button
              type="button"
              onClick={() => void load(true)}
              disabled={refreshing}
              className="rounded-lg bg-slate-950 px-4 py-2 text-xs font-bold text-white hover:bg-slate-800 disabled:opacity-50"
            >
              {refreshing ? "Refreshing…" : "Refresh"}
            </button>
          </div>
        </header>

        {error ? (
          <div className="mb-6 rounded-xl border border-red-200 bg-red-50 px-4 py-4 text-sm text-red-800">
            {error}
          </div>
        ) : null}

        {data ? (
          <>
            <section className="mb-8 grid gap-4 md:grid-cols-3">
              <Metric
                label="Current portfolio loans"
                value={count(data.currentPortfolioLoans)}
                detail={`As of ${data.asOfDate}`}
              />
              <Metric
                label="Outstanding principal"
                value={money(data.currentOutstandingPrincipal)}
                detail="PAR denominator"
              />
              <Metric
                label="PAR90"
                value={percent(data.par90Pct)}
                detail={`${money(data.par90Amount)} outstanding at 90+ days`}
                tone={riskTone(data.par90Pct)}
              />
            </section>

            <section className="mb-10">
              <SectionTitle
                title="Cumulative PAR ladder"
                description="Each threshold includes all loans at or beyond that delinquency age. Therefore PAR90 is always a subset of PAR60, PAR60 of PAR30, and so on."
              />
              <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-5">
                {parCards.map((card) => (
                  <article
                    key={card.key}
                    className={`rounded-xl border p-5 ${riskTone(card.pct)}`}
                  >
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-black tracking-wide">
                        {card.label}
                      </span>
                      <span className="text-[10px] font-bold opacity-70">
                        {card.days}
                      </span>
                    </div>
                    <div className="mt-5 text-2xl font-black">
                      {percent(card.pct)}
                    </div>
                    <div className="mt-1 text-xs font-semibold">
                      {money(card.amount)}
                    </div>
                    <div className="mt-3 text-[11px] font-medium opacity-75">
                      {count(card.loans)} loan{card.loans === 1 ? "" : "s"}
                    </div>
                  </article>
                ))}
              </div>
            </section>

            <section className="rounded-xl border border-slate-200 bg-white shadow-sm">
              <div className="border-b border-slate-200 px-5 py-5">
                <SectionTitle
                  title="Delinquency ageing"
                  description="Mutually exclusive operational buckets. Use these for collection workload; use cumulative PAR figures for portfolio-risk reporting."
                />
              </div>
              <div className="overflow-x-auto">
                <table className="w-full min-w-[760px] text-left text-sm">
                  <thead className="bg-slate-50 text-[10px] font-black uppercase tracking-wide text-slate-500">
                    <tr>
                      <th className="px-5 py-3">Bucket</th>
                      <th className="px-5 py-3 text-right">Loans</th>
                      <th className="px-5 py-3 text-right">
                        Outstanding principal
                      </th>
                      <th className="px-5 py-3 text-right">% of portfolio</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.ageingBuckets.map((bucket) => (
                      <tr
                        key={bucket.code}
                        className="border-t border-slate-100"
                      >
                        <td className="px-5 py-4 font-bold text-slate-900">
                          {bucket.label}
                        </td>
                        <td className="px-5 py-4 text-right font-semibold text-slate-700">
                          {count(bucket.loanCount)}
                        </td>
                        <td className="px-5 py-4 text-right font-semibold text-slate-700">
                          {money(bucket.outstandingPrincipal)}
                        </td>
                        <td className="px-5 py-4 text-right font-bold text-slate-950">
                          {percent(bucket.percentageOfPortfolio)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>

            <section className="mt-8 rounded-xl border border-slate-200 bg-slate-900 p-5 text-white">
              <p className="text-[10px] font-black uppercase tracking-[0.18em] text-slate-400">
                Control definition
              </p>
              <p className="mt-2 max-w-4xl text-sm leading-6 text-slate-200">
                PAR amount = outstanding principal of loans whose days overdue
                meet the threshold. PAR % = PAR amount ÷ current outstanding
                principal × 100. This is a risk metric, not a sum of delinquent
                instalments and not a measure of original disbursements.
              </p>
              <p className="mt-3 text-[11px] text-slate-400">
                Generated {new Date(data.generatedAt).toLocaleString("en-RW")}
              </p>
            </section>
          </>
        ) : null}
      </div>
    </main>
  );
}

function Metric({
  label,
  value,
  detail,
  tone = "border-slate-200 bg-white",
}: {
  label: string;
  value: string;
  detail: string;
  tone?: string;
}) {
  return (
    <article className={`rounded-xl border p-5 shadow-sm ${tone}`}>
      <p className="text-[10px] font-black uppercase tracking-wide opacity-60">
        {label}
      </p>
      <p className="mt-3 text-2xl font-black tracking-tight">{value}</p>
      <p className="mt-1 text-[11px] font-medium opacity-60">{detail}</p>
    </article>
  );
}

function SectionTitle({
  title,
  description,
}: {
  title: string;
  description: string;
}) {
  return (
    <div>
      <h2 className="text-base font-black text-slate-950">{title}</h2>
      <p className="mt-1 max-w-4xl text-xs leading-5 text-slate-500">
        {description}
      </p>
    </div>
  );
}
