"use client";

import Link from "next/link";
import PublicInterestCalculator from "../../../components/PublicInterestCalculator";
import { useTenant } from "../layout";
import { SITE_CONTENT } from "../../../lib/siteContent";
import { pageContent } from "../../../lib/websiteContent";

export default function InterestCalculatorPage() {
  const tenant = useTenant() || SITE_CONTENT;
  const cms = pageContent(tenant.websiteContent, "interest-calculator");
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
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-5 py-10 sm:px-8 sm:py-16">
        <PublicInterestCalculator products={tenant.services} currency={tenant.currency} primary={primary} accent={accent} />
        <div className="mt-8 grid gap-5 md:grid-cols-2">
          {cms.sections.slice(0, 2).map((section, i) => (
            <article key={section.title || i} className="rounded-[28px] border border-slate-200 bg-white p-7 shadow-sm sm:p-8">
              <span className="text-[10px] font-black uppercase tracking-[.2em]" style={{ color: accent }}>Cost insight {String(i + 1).padStart(2, "0")}</span>
              <h2 className="mt-3 text-xl font-black tracking-tight">{section.title}</h2>
              <p className="mt-3 text-sm leading-7 text-slate-500">{section.body}</p>
            </article>
          ))}
        </div>
        <div className="mt-8 rounded-[28px] border border-slate-200 bg-white p-7 text-sm leading-7 text-slate-500 shadow-sm sm:p-9">
          <strong className="text-slate-900">Important:</strong> This is an indicative calculation based on the selected product configuration. Exact rates, fees, dates and contractual repayments are determined by the applicable loan agreement.
        </div>
      </section>
    </main>
  );
}
