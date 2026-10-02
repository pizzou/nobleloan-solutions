"use client";

import Link from "next/link";
import PublicLoanCalculator from "../../components/PublicLoanCalculator";
import { useTenant } from "./layout";

const Arrow = () => <span aria-hidden="true">→</span>;

function MiniIcon({ kind }: { kind: string }) {
  if (kind === "shield") return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" className="h-5 w-5"><path d="M12 3 20 6v6c0 5-3.5 8.5-8 10-4.5-1.5-8-5-8-10V6l8-3Z"/><path d="m9 12 2 2 4-4"/></svg>;
  if (kind === "clock") return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" className="h-5 w-5"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>;
  if (kind === "track") return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" className="h-5 w-5"><path d="M4 17V7m0 10 4-4 4 2 8-8"/><path d="M16 7h4v4"/></svg>;
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" className="h-5 w-5"><path d="M12 3v18M5 7h10a3 3 0 1 1 0 6H8a3 3 0 1 0 0 6h11"/></svg>;
}

export default function HomePage() {
  const tenant = useTenant();
  if (!tenant) return null;
  const primary = tenant.primaryColor || "#0F1B3D";
  const accent = tenant.accentColor || "#C9A227";
  const products = tenant.services || [];

  const purposes = [
    ["Personal needs", "For eligible household, education and personal expenses.", "/personal-loans", "PERSONAL"],
    ["Business growth", "Working capital and approved business expansion needs.", "/business-loans", "BUSINESS"],
    ["Vehicle purchase", "Financing for eligible vehicle purchases.", "/vehicle-loans", "AUTO"],
    ["Salary advance", "Short-term financing against verified salary income.", "/salary-advance", "SALARY_ADVANCE"],
    ["Agriculture", "Funding for approved agricultural and agribusiness activities.", "/agriculture-loans", "AGRICULTURAL"],
  ];

  return (
    <main className="overflow-hidden bg-[#f4f6f9] text-slate-950">
      <section className="relative overflow-hidden text-white" style={{ background: `linear-gradient(118deg,#040d1b 0%,${primary} 54%,#173d67 100%)` }}>
        <div className="absolute inset-0 opacity-80" style={{ backgroundImage: `radial-gradient(circle at 80% 12%,${accent}28,transparent 25%),radial-gradient(circle at 15% 82%,rgba(53,113,167,.2),transparent 30%)` }} />
        <div className="absolute -right-40 top-20 h-[520px] w-[520px] rounded-full border border-white/10" />
        <div className="absolute right-24 top-48 h-32 w-32 rounded-full border border-white/10" />
        <div className="relative mx-auto max-w-7xl px-5 pb-16 pt-14 sm:px-8 sm:pb-24 sm:pt-20">
          <div className="grid gap-12 lg:grid-cols-[.93fr_1.07fr] lg:items-center">
            <div>
              <div className="inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/[.055] px-4 py-2 text-[9px] font-black uppercase tracking-[.22em] text-white/65"><span className="h-1.5 w-1.5 rounded-full" style={{ backgroundColor: accent }} />{tenant.tagline}</div>
              <h1 className="mt-7 max-w-3xl text-5xl font-black leading-[.94] tracking-[-.065em] sm:text-6xl lg:text-[5.5rem]">{tenant.hero.headline}</h1>
              <p className="mt-7 max-w-xl text-base leading-8 text-white/60 sm:text-lg">{tenant.hero.subtext}</p>
              <div className="mt-8 flex flex-col gap-3 sm:flex-row"><Link href="/apply" className="rounded-2xl px-6 py-4 text-center text-sm font-black" style={{ backgroundColor: accent, color: primary }}>Start a loan application <Arrow /></Link><Link href="/track" className="rounded-2xl border border-white/15 bg-white/[.045] px-6 py-4 text-center text-sm font-black text-white">Track an application</Link></div>
              <div className="mt-9 grid max-w-xl grid-cols-3 border-t border-white/10 pt-6"><div><div className="text-lg font-black">5%</div><div className="mt-1 text-[9px] uppercase tracking-wider text-white/35">Monthly interest*</div></div><div className="border-l border-white/10 pl-5"><div className="text-lg font-black">1–6</div><div className="mt-1 text-[9px] uppercase tracking-wider text-white/35">Months*</div></div><div className="border-l border-white/10 pl-5"><div className="text-lg font-black">RWF</div><div className="mt-1 text-[9px] uppercase tracking-wider text-white/35">Lending currency</div></div></div>
              <p className="mt-3 max-w-xl text-[9px] leading-4 text-white/30">*Published configuration may vary by product. Review the applicable product terms and final loan agreement before accepting.</p>
            </div>
            <div className="lg:pl-5"><PublicLoanCalculator products={products} currency={tenant.currency} primary={primary} accent={accent} /></div>
          </div>
        </div>
      </section>

      <section className="relative z-10 mx-auto -mt-7 max-w-7xl px-5 sm:px-8">
        <div className="grid overflow-hidden rounded-3xl border border-white/10 bg-white shadow-[0_25px_70px_rgba(15,27,61,.12)] md:grid-cols-4">
          {[['shield','Secure application','A protected digital journey for loan applications.'],['clock','Clear next steps','Know what happens from application to decision.'],['track','Track progress','Use your reference to check your application status.'],['money','Published terms','Review product rates, fees and repayment periods.']].map(([kind,title,body]) => <div key={title} className="flex gap-4 border-b border-slate-100 p-6 last:border-0 md:border-b-0 md:border-r md:last:border-r-0"><div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl" style={{ backgroundColor: `${primary}0c`, color: primary }}><MiniIcon kind={kind} /></div><div><h2 className="text-xs font-black">{title}</h2><p className="mt-1 text-[10px] leading-5 text-slate-500">{body}</p></div></div>)}
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-5 py-20 sm:px-8 lg:py-24">
        <div className="flex flex-col justify-between gap-5 md:flex-row md:items-end"><div><div className="text-[10px] font-black uppercase tracking-[.22em]" style={{ color: accent }}>Choose your loan</div><h2 className="mt-3 max-w-2xl text-4xl font-black tracking-[-.05em] sm:text-5xl">One lender. Loan options built for real needs.</h2></div><Link href="/services" className="text-sm font-black" style={{ color: primary }}>View all loan products <Arrow /></Link></div>
        <div className="mt-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-5">{products.map((p, i) => <Link key={p.loanType || p.title} href={`/apply?type=${encodeURIComponent(p.loanType || p.title)}`} className="group relative overflow-hidden rounded-3xl border border-slate-200 bg-white p-6 transition duration-300 hover:-translate-y-2 hover:border-slate-300 hover:shadow-[0_25px_60px_rgba(15,27,61,.12)]"><div className="flex h-12 w-12 items-center justify-center rounded-2xl text-xl" style={{ backgroundColor: `${primary}0c` }}>{p.icon || ['◈','▣','◇','◍','✦'][i % 5]}</div><h3 className="mt-6 text-sm font-black">{p.title}</h3><p className="mt-2 min-h-[48px] text-xs leading-5 text-slate-500">{p.description}</p><div className="mt-5 text-xs font-black" style={{ color: primary }}>Apply for this loan <Arrow /></div><div className="absolute right-0 top-0 h-24 w-24 rounded-bl-full" style={{ backgroundColor: `${accent}0a` }} /></Link>)}</div>
      </section>

      <section className="relative overflow-hidden text-white" style={{ background: primary }}>
        <div className="absolute inset-0 opacity-60" style={{ backgroundImage: `linear-gradient(rgba(255,255,255,.035) 1px,transparent 1px),linear-gradient(90deg,rgba(255,255,255,.035) 1px,transparent 1px)`, backgroundSize: '42px 42px' }} />
        <div className="relative mx-auto max-w-7xl px-5 py-20 sm:px-8 lg:py-24"><div className="grid gap-12 lg:grid-cols-[.8fr_1.2fr] lg:items-center"><div><div className="text-[10px] font-black uppercase tracking-[.22em]" style={{ color: accent }}>How Noble works</div><h2 className="mt-3 text-4xl font-black tracking-[-.05em] sm:text-5xl">A lending journey without the guesswork.</h2><p className="mt-5 text-sm leading-7 text-white/50">From choosing a loan to tracking your application, Noble keeps the borrower journey clear and practical.</p><Link href="/how-it-works" className="mt-7 inline-flex rounded-xl px-5 py-3 text-sm font-black" style={{ backgroundColor: accent, color: primary }}>See how it works <span className="ml-2"><Arrow /></span></Link></div><div className="grid gap-3 sm:grid-cols-3">{[['01','Explore','Choose the loan product that matches your borrowing need.'],['02','Apply','Provide your information and supporting documents securely.'],['03','Track','Keep your application reference and follow progress online.']].map(([n,t,b]) => <div key={n} className="rounded-3xl border border-white/10 bg-white/[.045] p-6"><span className="text-[10px] font-black" style={{ color: accent }}>{n}</span><h3 className="mt-6 text-lg font-black">{t}</h3><p className="mt-3 text-xs leading-6 text-white/45">{b}</p></div>)}</div></div></div>
      </section>

      <section className="mx-auto max-w-7xl px-5 py-20 sm:px-8 lg:py-24">
        <div className="grid gap-6 lg:grid-cols-[1.1fr_.9fr]">
          <div className="rounded-[32px] p-8 text-white sm:p-10" style={{ background: `linear-gradient(135deg,${primary},#061326)` }}><div className="text-[10px] font-black uppercase tracking-[.22em]" style={{ color: accent }}>Borrow with confidence</div><h2 className="mt-4 max-w-2xl text-3xl font-black tracking-[-.04em] sm:text-4xl">Understand the cost. Understand the process. Then decide.</h2><p className="mt-4 max-w-xl text-sm leading-7 text-white/50">Use Noble&apos;s calculators, published product information and application tracking tools to make an informed borrowing decision.</p><div className="mt-7 flex flex-wrap gap-3"><Link href="/calculators" className="rounded-xl px-5 py-3 text-xs font-black" style={{ backgroundColor: accent, color: primary }}>Explore calculators</Link><Link href="/learn" className="rounded-xl border border-white/15 px-5 py-3 text-xs font-black">Learn about borrowing</Link></div></div>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-1">{[['Transparent terms','Review the applicable interest, fees, amount limits and term before accepting.'],['Application visibility','Your reference helps you follow your application after submission.'],['Responsible information','We explain the lending journey without promising approval or outcomes.']].map(([t,b]) => <div key={t} className="rounded-3xl border border-slate-200 bg-white p-6"><h3 className="text-sm font-black">{t}</h3><p className="mt-2 text-xs leading-6 text-slate-500">{b}</p></div>)}</div>
        </div>
      </section>

      <section className="border-y border-slate-200 bg-white"><div className="mx-auto max-w-7xl px-5 py-16 sm:px-8"><div className="grid gap-10 lg:grid-cols-[1fr_auto] lg:items-center"><div><div className="text-[10px] font-black uppercase tracking-[.22em]" style={{ color: accent }}>Need help?</div><h2 className="mt-3 text-3xl font-black tracking-[-.04em]">Talk to Noble before you apply.</h2><p className="mt-3 max-w-2xl text-sm leading-7 text-slate-500">Ask about loan products, applications, repayments or general lending questions. Messages submitted through the website go directly to the Noble dashboard team.</p></div><div className="flex flex-col gap-3 sm:flex-row"><Link href="/contact" className="rounded-xl px-6 py-4 text-center text-sm font-black text-white" style={{ backgroundColor: primary }}>Contact Noble <Arrow /></Link><Link href="/apply" className="rounded-xl border border-slate-200 px-6 py-4 text-center text-sm font-black" style={{ color: primary }}>Apply now</Link></div></div></div></section>

      <section className="mx-auto max-w-7xl px-5 py-16 sm:px-8"><div className="rounded-[32px] p-8 text-white sm:p-12" style={{ background: `linear-gradient(120deg,#061326,${primary})` }}><div className="max-w-3xl"><div className="text-[10px] font-black uppercase tracking-[.22em]" style={{ color: accent }}>Ready to borrow?</div><h2 className="mt-4 text-4xl font-black tracking-[-.05em] sm:text-5xl">Start your Noble loan application.</h2><p className="mt-4 text-sm leading-7 text-white/50">Choose a loan, review the applicable terms, submit your information and keep your application reference for tracking.</p><div className="mt-7 flex flex-col gap-3 sm:flex-row"><Link href="/apply" className="rounded-xl px-6 py-4 text-center text-sm font-black" style={{ backgroundColor: accent, color: primary }}>Apply for a loan <Arrow /></Link><Link href="/track" className="rounded-xl border border-white/15 px-6 py-4 text-center text-sm font-black">Track application</Link></div></div></div></section>
    </main>
  );
}
