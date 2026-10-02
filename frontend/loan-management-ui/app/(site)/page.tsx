"use client";

import Link from "next/link";
import PublicLoanCalculator from "../../components/PublicLoanCalculator";
import { useTenant } from "./layout";

export default function HomePage() {
  const tenant = useTenant();
  if (!tenant) return null;
  const primary = tenant.primaryColor;
  const accent = tenant.accentColor;
  const products = tenant.services || [];
  const purposes = [
    ["Debt consolidation", "Bring qualifying debts together into a simpler repayment plan.", "/debt-consolidation"],
    ["Home improvement", "Plan approved repairs, upgrades and household projects.", "/home-improvement-loans"],
    ["Medical expenses", "Prepare for eligible healthcare and unexpected expenses.", "/medical-loans"],
    ["Moving expenses", "Manage eligible costs associated with a move or relocation.", "/moving-loans"],
    ["Wedding expenses", "Plan eligible wedding-related costs with a structured loan.", "/wedding-loans"],
    ["Credit card consolidation", "Explore whether consolidating eligible card balances could simplify payments.", "/credit-card-consolidation"],
  ];

  return <main className="overflow-hidden bg-white text-slate-950">
    <section className="relative overflow-hidden text-white" style={{ background: `linear-gradient(120deg,#061326 0%,${primary} 58%,#193b64 100%)` }}>
      <div className="absolute inset-0 opacity-30" style={{ backgroundImage: `radial-gradient(circle at 78% 12%,${accent}55,transparent 26%),radial-gradient(circle at 15% 90%,rgba(255,255,255,.12),transparent 28%)` }} />
      <div className="relative mx-auto grid max-w-7xl gap-10 px-5 pb-14 pt-14 sm:px-8 sm:pb-20 sm:pt-20 lg:grid-cols-[1fr_500px] lg:items-center">
        <div>
          <div className="inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/[.06] px-4 py-2 text-[10px] font-black uppercase tracking-[.2em] text-white/65"><span className="h-1.5 w-1.5 rounded-full" style={{ backgroundColor: accent }} />{tenant.tagline}</div>
          <h1 className="mt-7 max-w-3xl text-5xl font-black leading-[.96] tracking-[-.06em] sm:text-6xl lg:text-[5.2rem]">{tenant.hero.headline}</h1>
          <p className="mt-7 max-w-2xl text-base leading-8 text-white/65 sm:text-lg">{tenant.hero.subtext}</p>
          <div className="mt-8 flex flex-col gap-3 sm:flex-row"><Link href="/apply" className="rounded-xl px-6 py-4 text-center text-sm font-black text-slate-950" style={{ backgroundColor: accent }}>Start an application →</Link><Link href="/how-it-works" className="rounded-xl border border-white/15 bg-white/[.05] px-6 py-4 text-center text-sm font-bold text-white">See how it works</Link></div>
          <div className="mt-8 flex flex-wrap gap-x-7 gap-y-3 border-t border-white/10 pt-6 text-xs font-bold text-white/45"><span>RWF-denominated lending</span><span>Clear published product terms</span><span>Secure digital application</span></div>
        </div>
        <div><div className="rounded-[30px] border border-white/15 bg-white p-2 shadow-[0_35px_100px_rgba(0,0,0,.4)]"><PublicLoanCalculator products={products} currency={tenant.currency} primary={primary} accent={accent} /></div><p className="mt-3 text-center text-[9px] font-bold uppercase tracking-[.15em] text-white/35">Planning estimate only · final terms are subject to assessment</p></div>
      </div>
    </section>

    <section className="border-b border-slate-100 bg-white"><div className="mx-auto max-w-7xl px-5 py-10 sm:px-8"><div className="flex flex-col justify-between gap-5 md:flex-row md:items-end"><div><p className="text-xs font-black uppercase tracking-[.2em]" style={{ color: accent }}>Choose a path</p><h2 className="mt-2 text-3xl font-black tracking-[-.04em]">Financing built around real needs.</h2></div><Link href="/services" className="text-sm font-black" style={{ color: primary }}>View all loan solutions →</Link></div><div className="mt-8 grid gap-3 sm:grid-cols-2 lg:grid-cols-5">{products.map((p) => <Link key={p.loanType || p.title} href={`/apply?type=${encodeURIComponent(p.loanType || "")}`} className="group rounded-2xl border border-slate-200 p-5 transition hover:-translate-y-1 hover:border-slate-300 hover:shadow-xl"><span className="text-2xl">{p.icon}</span><h3 className="mt-4 text-sm font-black">{p.title}</h3><p className="mt-2 text-xs leading-5 text-slate-500">{p.description}</p><span className="mt-4 block text-xs font-black" style={{ color: primary }}>Explore →</span></Link>)}</div></div></section>

    <section className="bg-slate-50"><div className="mx-auto max-w-7xl px-5 py-16 sm:px-8 sm:py-20"><div className="grid gap-12 lg:grid-cols-[.75fr_1.25fr] lg:items-start"><div><p className="text-xs font-black uppercase tracking-[.2em]" style={{ color: accent }}>Borrow with context</p><h2 className="mt-3 text-4xl font-black tracking-[-.05em]">A professional lending journey from first question to final repayment.</h2><p className="mt-5 text-sm leading-7 text-slate-600">Noble combines transparent product information, repayment planning, digital applications and application tracking in one experience.</p><Link href="/how-it-works" className="mt-6 inline-flex rounded-xl px-5 py-3 text-sm font-black text-white" style={{ backgroundColor: primary }}>How it works →</Link></div><div className="grid gap-4 sm:grid-cols-3">{[["01","Explore","Compare the lending solutions and understand the applicable terms."],["02","Plan","Use the calculator to understand a repayment scenario before applying."],["03","Apply & track","Submit the application securely, then use your reference to follow progress."]].map(([n,t,b]) => <div key={n} className="rounded-3xl border border-slate-200 bg-white p-6"><span className="text-xs font-black" style={{ color: accent }}>{n}</span><h3 className="mt-5 text-lg font-black">{t}</h3><p className="mt-3 text-sm leading-6 text-slate-500">{b}</p></div>)}</div></div></div></section>

    <section className="bg-white"><div className="mx-auto max-w-7xl px-5 py-16 sm:px-8 sm:py-20"><div className="flex flex-col justify-between gap-5 md:flex-row md:items-end"><div><p className="text-xs font-black uppercase tracking-[.2em]" style={{ color: accent }}>Popular purposes</p><h2 className="mt-2 text-3xl font-black tracking-[-.04em]">One lending experience, many reasons to borrow.</h2></div><Link href="/learn" className="text-sm font-black" style={{ color: primary }}>Explore financial education →</Link></div><div className="mt-8 grid gap-4 md:grid-cols-2 lg:grid-cols-3">{purposes.map(([t,b,h]) => <Link href={h} key={h} className="rounded-3xl border border-slate-200 p-6 transition hover:-translate-y-1 hover:shadow-lg"><h3 className="text-lg font-black">{t}</h3><p className="mt-3 text-sm leading-6 text-slate-500">{b}</p><span className="mt-5 block text-sm font-black" style={{ color: primary }}>Learn more →</span></Link>)}</div></div></section>

    <section className="bg-slate-50"><div className="mx-auto max-w-7xl px-5 py-16 sm:px-8 sm:py-20"><div className="grid gap-5 md:grid-cols-3">{[["Transparent terms","Review applicable interest, management fees, application fees, amount limits and repayment terms before accepting."],["Secure digital journey","Provide the information needed for assessment through the public application and keep your reference secure."],["Application visibility","Once submitted, use Track application to check status and see the next steps available to you."]].map(([t,b]) => <div key={t} className="rounded-3xl border border-slate-200 bg-white p-7"><h3 className="text-lg font-black">{t}</h3><p className="mt-3 text-sm leading-7 text-slate-500">{b}</p></div>)}</div></div></section>

    <section className="mx-auto max-w-7xl px-5 py-16 sm:px-8 sm:py-20"><div className="rounded-[32px] p-8 text-white sm:p-12" style={{ background: `linear-gradient(120deg,${primary},#061326)` }}><p className="text-xs font-black uppercase tracking-[.2em]" style={{ color: accent }}>Ready when you are</p><h2 className="mt-4 max-w-3xl text-4xl font-black tracking-[-.05em]">Make the next financial step with information you can understand.</h2><p className="mt-4 max-w-2xl text-sm leading-7 text-white/60">Start a new application, or track an application you have already submitted.</p><div className="mt-7 flex flex-col gap-3 sm:flex-row"><Link href="/apply" className="rounded-xl px-6 py-4 text-center text-sm font-black text-slate-950" style={{ backgroundColor: accent }}>Apply now →</Link><Link href="/track" className="rounded-xl border border-white/15 px-6 py-4 text-center text-sm font-black text-white">Track application</Link></div></div></section>
  </main>;
}
