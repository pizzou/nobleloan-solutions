"use client";

import { useTenant } from "../layout";
import LoanCalculator from "../../../components/LoanCalculator";
import FxRatePanel from "../../../components/FxWidget";

export default function CalculatorPage() {
  const tenant = useTenant();
  if (!tenant) return null;

  return (
    <div className="bg-slate-50 pb-20">
      <section
        className="relative overflow-hidden text-white"
        style={{
          background: `linear-gradient(135deg, ${tenant.primaryColor}, #071427)`,
        }}
      >
        <div className="absolute -right-24 top-0 h-72 w-72 rounded-full bg-white/10 blur-3xl" />
        <div className="relative mx-auto max-w-7xl px-4 py-20 md:py-24">
          <div className="max-w-3xl">
            <div className="text-[11px] font-black uppercase tracking-[0.24em] text-white/55">
              Planning tools
            </div>
            <h1 className="mt-4 text-4xl font-black tracking-tight md:text-6xl">
              Calculate before you commit.
            </h1>
            <p className="mt-5 max-w-2xl text-lg leading-8 text-white/70">
              Compare published loan pricing and use live FX references in one
              professional workspace from {tenant.name}.
            </p>
          </div>
        </div>
      </section>

      <main className="mx-auto max-w-7xl space-y-8 px-4 pt-10">
        <LoanCalculator tenant={tenant} />
        <FxRatePanel tenant={tenant} />
      </main>
    </div>
  );
}
