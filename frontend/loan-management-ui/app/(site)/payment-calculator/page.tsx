"use client";

import Link from "next/link";
import PublicPaymentCalculator from "../../../components/PublicPaymentCalculator";
import { useTenant } from "../layout";
import { SITE_CONTENT } from "../../../lib/siteContent";
import { pageContent } from "../../../lib/websiteContent";

export default function PaymentCalculatorPage() {
  const tenant = useTenant() || SITE_CONTENT;
  const cms = pageContent(tenant.websiteContent, "payment-calculator");
  const primary = tenant.primaryColor || "#0F1B3D";
  const accent = tenant.accentColor || "#C9A227";

  return (
    <main className="overflow-hidden bg-[#f5f7fa] text-slate-950">
      <section className="relative overflow-hidden text-white" style={{ background: `linear-gradient(125deg,#061326 0%,${primary} 66%,#1a416b 100%)` }}>
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_84%_12%,rgba(201,162,39,.25),transparent_24%),radial-gradient(circle_at_12%_90%,rgba(255,255,255,.10),transparent_28%)]" />
        <div className="relative mx-auto max-w-7xl px-5 py-16 sm:px-8 sm:py-24">
          <Link href="/calculators" className="text-xs font-bold text-white/55 hover:text-white">← All calculators</Link>
          <p className="mt-8 text-[10px] font-black uppercase tracking-[.24em]" style={{ color: accent }}>{cms.eyebrow}</p>
          <h1 className="mt-4 max-w-4xl text-4xl font-black leading-[.98] tracking-[-.06em] sm:text-6xl">{cms.title}</h1>
          <p className="mt-6 max-w-2xl text-base leading-8 text-white/70 sm:text-lg">{cms.description}</p>
          <div className="mt-7 flex flex-wrap gap-2 text-[10px] font-bold text-white/55">
            <span className="rounded-full border border-white/10 bg-white/5 px-3 py-2">RWF planning</span>
            <span className="rounded-full border border-white/10 bg-white/5 px-3 py-2">Active product configuration</span>
            <span className="rounded-full border border-white/10 bg-white/5 px-3 py-2">Indicative only</span>
          </div>
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-5 py-10 sm:px-8 sm:py-16">
        <PublicPaymentCalculator products={tenant.services} currency={tenant.currency} primary={primary} accent={accent} />
        <div className="mt-8 grid gap-5 md:grid-cols-3">
          {cms.sections.slice(0, 3).map((section, i) => (
            <article key={section.title || i} className="rounded-[28px] border border-slate-200 bg-white p-6 shadow-sm sm:p-7">
              <span className="text-[10px] font-black uppercase tracking-[.2em]" style={{ color: accent }}>{String(i + 1).padStart(2, "0")}</span>
              <h2 className="mt-3 text-lg font-black tracking-tight">{section.title}</h2>
              <p className="mt-2 text-sm leading-7 text-slate-500">{section.body}</p>
            </article>
          ))}
        </div>
        <div className="mt-8 rounded-[28px] border border-slate-200 bg-white p-7 text-sm leading-7 text-slate-500 shadow-sm sm:p-9">
          <strong className="text-slate-900">Planning note:</strong> The calculator uses the selected Noble product configuration to create an indicative scenario. It does not determine eligibility, approval or a binding repayment obligation. Review the exact offer and agreement before committing.
        </div>
      </section>
    </main>
  );
}
