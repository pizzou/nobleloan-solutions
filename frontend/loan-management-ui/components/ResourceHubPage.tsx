import Link from "next/link";
import { SITE_CONTENT } from "../lib/siteContent";

export function ResourceHubPage() {
  const cards = [
    ["Credit score", "Understand the role credit history can play in borrowing decisions.", "/credit-score"],
    ["Debt-to-income", "Learn how income and existing commitments affect affordability planning.", "/learn"],
    ["Loan calculator", "Estimate a payment and total repayment before applying.", "/loan-calculator"],
    ["Payment calculator", "Explore how the amount and term change a repayment scenario.", "/payment-calculator"],
    ["Interest calculator", "See how payment size and interest affect time and total cost.", "/interest-calculator"],
    ["Inflation calculator", "Understand how purchasing power can change over time.", "/inflation-calculator"],
  ];
  return <main className="bg-slate-50"><section className="bg-white"><div className="mx-auto max-w-7xl px-5 py-16 sm:px-8 sm:py-24"><p className="text-xs font-black uppercase tracking-[.22em] text-[#C9A227]">Noble Learn</p><h1 className="mt-4 max-w-4xl text-4xl font-black tracking-[-.055em] sm:text-6xl">Useful information for better financial decisions.</h1><p className="mt-6 max-w-2xl text-base leading-8 text-slate-600">Explore practical education and calculators designed around Noble Loan Solutions&apos; lending products and the financial decisions our clients make.</p></div></section><section className="mx-auto max-w-7xl px-5 py-14 sm:px-8 sm:py-20"><div className="grid gap-5 md:grid-cols-2 lg:grid-cols-3">{cards.map(([title, body, href]) => <Link href={href} key={href} className="group rounded-3xl border border-slate-200 bg-white p-7 transition hover:-translate-y-1 hover:shadow-xl"><div className="text-xs font-black uppercase tracking-[.16em] text-[#C9A227]">Resource</div><h2 className="mt-4 text-xl font-black">{title}</h2><p className="mt-3 text-sm leading-7 text-slate-500">{body}</p><span className="mt-6 block text-sm font-black text-[#0F1B3D]">Explore →</span></Link>)}</div></section></main>;
}
