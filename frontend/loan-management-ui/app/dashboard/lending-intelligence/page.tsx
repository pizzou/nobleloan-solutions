"use client";

import { useEffect, useState } from "react";
import API from "@/services/api";

const cards = [
  ["PAR", "/portfolio-risk"],
  ["Vintage", "/vintage"],
  ["ECL", "/ecl"],
  ["Collections", "/collections-queue"],
  ["Profitability", "/profitability"],
  ["Concentration", "/concentration"],
  ["Stress", "/stress"],
  ["Liquidity", "/liquidity"],
  ["Reconciliation", "/reconciliation-alerts"],
  ["BNR / CRB validation", "/regulatory-validation"],
] as const;

export default function LendingIntelligencePage() {
  const [data, setData] = useState<Record<string, unknown>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    async function load() {
      try {
        setLoading(true);
        const responses = await Promise.all(
          cards.map(async ([name, path]) => {
            const response = await API.get(`/lending-intelligence${path}`);
            return [name, response.data] as const;
          }),
        );
        if (active) setData(Object.fromEntries(responses));
      } catch (err) {
        if (active) {
          setError(
            err instanceof Error
              ? err.message
              : "Unable to load lending intelligence.",
          );
        }
      } finally {
        if (active) setLoading(false);
      }
    }

    void load();
    return () => {
      active = false;
    };
  }, []);

  return (
    <main className="space-y-6 p-6">
      <div>
        <h1 className="text-2xl font-semibold">Lending Intelligence</h1>
        <p className="text-sm text-muted-foreground">
          Portfolio risk, credit, collections, profitability, liquidity and regulatory controls.
        </p>
      </div>

      {error ? <div className="rounded-md border p-4 text-sm">{error}</div> : null}

      {loading ? (
        <div className="rounded-md border p-6 text-sm">Loading lending intelligence…</div>
      ) : (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {cards.map(([name]) => (
            <section key={name} className="rounded-xl border p-4 shadow-sm">
              <h2 className="mb-3 font-medium">{name}</h2>
              <pre className="max-h-72 overflow-auto whitespace-pre-wrap text-xs">
                {JSON.stringify(data[name], null, 2)}
              </pre>
            </section>
          ))}
        </div>
      )}
    </main>
  );
}
