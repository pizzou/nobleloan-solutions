"use client";

import Link from "next/link";
import { useTenant } from "../layout";
import LoanCalculator from "../../../components/site/LoanCalculator";
import FxRateBoard from "../../../components/site/FxRateBoard";

export default function CalculatorPage() {
  const tenant = useTenant();
  if (!tenant) return null;

  return (
    <div>
      <section className="bg-slate-950 px-4 py-16 text-white md:px-6 md:py-24">
        <div className="mx-auto max-w-4xl text-center">
          <div className="text-xs font-black uppercase tracking-[0.2em] text-white/45">Planning tools</div>
          <h1 className="mt-4 text-4xl font-black tracking-tight md:text-6xl">Calculate your borrowing before you apply.</h1>
          <p className="mx-auto mt-5 max-w-2xl text-base leading-7 text-white/65">Use the lender's published pricing as a planning assumption. The approved loan agreement and generated repayment schedule remain the authoritative figures.</p>
        </div>
      </section>
      <LoanCalculator tenant={tenant} />
      <FxRateBoard tenant={tenant} />
      <section className="px-4 py-16"><div className="mx-auto max-w-4xl rounded-3xl border border-slate-200 bg-slate-50 p-8 text-center"><h2 className="text-2xl font-black text-slate-950">Ready to move forward?</h2><p className="mx-auto mt-3 max-w-xl text-sm leading-6 text-slate-500">Review the available products before beginning an application with {tenant.name}.</p><Link href="/services" className="mt-6 inline-flex rounded-xl px-6 py-3 text-sm font-black text-white" style={{ backgroundColor: tenant.primaryColor }}>View loan products →</Link></div></section>
    </div>
  );
}
