import Link from "next/link";
const articles = [
  ["What is APR?", "APR helps you compare the annual cost of borrowing by considering interest and applicable fees."],
  ["How loan payments work", "Each scheduled payment generally contributes toward principal and the cost of borrowing."],
  ["How to choose a loan term", "A longer term can reduce a scheduled payment but may increase the total cost of borrowing."],
  ["Debt consolidation basics", "Combining eligible debts into one structured payment can simplify repayment planning."],
  ["Understanding your application", "Know what information may be requested and why accurate information matters."],
  ["How to prepare before applying", "Review your budget, income, existing commitments and the amount you actually need."],
];
export default function LearnPage(){return <main className="bg-white"><section className="bg-slate-950 text-white"><div className="mx-auto max-w-7xl px-5 py-16 sm:px-8 sm:py-24"><p className="text-xs font-black uppercase tracking-[.18em] text-[#C9A227]">Noble Learn</p><h1 className="mt-3 max-w-3xl text-4xl font-black tracking-[-.05em] sm:text-6xl">Clear answers for better borrowing decisions.</h1><p className="mt-5 max-w-2xl text-base leading-7 text-white/60">Simple guides to help you understand loans, payments, fees and the application journey.</p></div></section><section className="mx-auto max-w-7xl px-5 py-14 sm:px-8"><div className="grid gap-5 md:grid-cols-2 lg:grid-cols-3">{articles.map(([title,text])=><article key={title} className="rounded-3xl border border-slate-200 bg-white p-7 shadow-sm"><div className="h-10 w-10 rounded-2xl bg-slate-100"/><h2 className="mt-6 text-xl font-black text-slate-950">{title}</h2><p className="mt-3 text-sm leading-6 text-slate-500">{text}</p><Link href="/faq" className="mt-5 inline-block text-sm font-black text-[#0F1B3D]">Learn more →</Link></article>)}</div></section></main>}
