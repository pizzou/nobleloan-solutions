"use client";

import Link from "next/link";
import { useTenant } from "../app/(site)/layout";

export default function PublicProductPage({ type, title, description }: { type: string; title?: string; description?: string }) {
  const tenant = useTenant();
  if (!tenant) return null;
  const p = tenant.services.find(x => x.loanType === type) || tenant.services.find(x => x.title.toLowerCase().includes(type.toLowerCase()));
  const resolvedTitle = title || p?.title || "Noble loan solution";
  const resolvedDescription = description || p?.description || "Explore the applicable Noble lending product and review the terms before you apply.";
  const primary = tenant.primaryColor;
  const accent = tenant.accentColor;
  return <main className="bg-white">
    <section className="relative overflow-hidden text-white" style={{ background: `linear-gradient(120deg,#061326 0%,${primary} 60%,#193b64 100%)` }}><div className="mx-auto max-w-7xl px-5 py-16 sm:px-8 sm:py-24"><p className="text-xs font-black uppercase tracking-[.2em]" style={{color:accent}}>Noble lending</p><h1 className="mt-5 max-w-4xl text-4xl font-black tracking-[-.055em] sm:text-6xl">{resolvedTitle}</h1><p className="mt-6 max-w-2xl text-base leading-8 text-white/65">{resolvedDescription}</p><div className="mt-8 flex flex-wrap gap-3"><Link href={`/apply?type=${encodeURIComponent(p?.loanType || type)}`} className="rounded-xl px-6 py-4 text-sm font-black text-slate-950" style={{backgroundColor:accent}}>Check your options →</Link><Link href="/loan-calculator" className="rounded-xl border border-white/15 px-6 py-4 text-sm font-bold text-white">Calculate repayment</Link></div></div></section>
    <section className="mx-auto max-w-7xl px-5 py-14 sm:px-8 sm:py-20"><div className="grid gap-5 md:grid-cols-3">{[["Configured product terms",p ? `${p.rateType || "Rate"}: ${p.interestRate ?? p.rate}% · Management fee: ${p.managementFeeRate ?? "—"}% · Application fee: ${p.applicationFeeRate ?? "—"}%` : "Review the applicable terms shown during your application."],["Loan range",p ? `${tenant.currency} ${(Number(p.minAmount)||0).toLocaleString("en-RW")} minimum${p.maxAmount ? ` · up to ${tenant.currency} ${Number(p.maxAmount).toLocaleString("en-RW")}` : " · no configured maximum"}` : "Subject to product configuration."],["Repayment term",p ? p.term : "Subject to product configuration."]].map(([a,b])=><div key={a} className="rounded-3xl border border-slate-200 p-7"><h2 className="text-lg font-black">{a}</h2><p className="mt-3 text-sm leading-7 text-slate-500">{b}</p></div>)}</div><div className="mt-8 grid gap-5 lg:grid-cols-2"><div className="rounded-3xl bg-slate-50 p-8"><h2 className="text-2xl font-black">What to expect</h2><ul className="mt-5 space-y-3 text-sm leading-6 text-slate-600"><li>• Provide the information needed for identity and eligibility assessment.</li><li>• Review the product terms and repayment information presented to you.</li><li>• Submit the application and keep your reference for tracking.</li></ul></div><div className="rounded-3xl bg-slate-50 p-8"><h2 className="text-2xl font-black">Important information</h2><p className="mt-5 text-sm leading-7 text-slate-600">Rates, fees, amounts and terms displayed here are sourced from Noble&apos;s configured public product data. Final approval and contractual terms remain subject to assessment and the applicable loan agreement.</p></div></div></section>
  </main>;
}
