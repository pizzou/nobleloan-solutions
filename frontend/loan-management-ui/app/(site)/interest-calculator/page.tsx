import UpstartStyleCalculator from "../../../components/UpstartStyleCalculator";
import { SITE_CONTENT } from "../../../lib/siteContent";

export default function Page() {
  return <main className="bg-slate-50"><div className="mx-auto max-w-7xl px-5 py-12 sm:px-8 sm:py-16"><div className="mb-10 max-w-3xl"><p className="text-xs font-black uppercase tracking-[.18em] text-[#C9A227]">Noble Loan Solutions</p><h1 className="mt-2 text-4xl font-black tracking-[-.04em] text-slate-950 sm:text-5xl">Interest calculator</h1><p className="mt-4 text-base leading-7 text-slate-500">Understand how payments, APR and time affect your borrowing cost.</p></div><UpstartStyleCalculator currency={SITE_CONTENT.currency} primary={SITE_CONTENT.primaryColor} accent={SITE_CONTENT.accentColor} mode="interest" /><div className="mx-auto mt-10 max-w-3xl rounded-3xl border border-slate-200 bg-white p-6 text-sm leading-7 text-slate-600"><strong className="text-slate-950">How this works:</strong> the estimate uses standard fixed-payment amortization. Your actual Noble Loan Solutions agreement can use product-specific rates, fees, repayment rules and assessment criteria.</div></div></main>;
}
