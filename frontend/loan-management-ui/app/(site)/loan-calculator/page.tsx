"use client";

import PublicLoanCalculator from "../../../components/PublicLoanCalculator";
import { useTenant } from "../layout";

export default function Page() {
  const tenant = useTenant();
  if (!tenant) return null;
  return <main className="bg-slate-50"><div className="mx-auto max-w-7xl px-5 py-12 sm:px-8 sm:py-16"><div className="mb-10 max-w-3xl"><p className="text-xs font-black uppercase tracking-[.18em] text-[#C9A227]">Noble Loan Solutions</p><h1 className="mt-2 text-4xl font-black tracking-[-.04em] text-slate-950 sm:text-5xl">Loan calculator</h1><p className="mt-4 text-base leading-7 text-slate-500">Estimate a Noble product repayment using the configured product rate, management fee and repayment method.</p></div><PublicLoanCalculator products={tenant.services} currency={tenant.currency} primary={tenant.primaryColor} accent={tenant.accentColor} /><div className="mx-auto mt-10 max-w-4xl rounded-3xl border border-slate-200 bg-white p-6 text-sm leading-7 text-slate-600"><strong className="text-slate-950">Important:</strong> the calculator mirrors the public contractual schedule logic. It is an estimate only; the exact amount, fees, repayment dates and approved terms are determined by the applicable product and loan agreement.</div></div></main>;
}
