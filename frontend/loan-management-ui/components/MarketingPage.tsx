import Link from "next/link";
import { SITE_CONTENT } from "../lib/siteContent";

export type MarketingSection = {
  title: string;
  body: string;
  bullets?: string[];
};

export function MarketingPage({
  eyebrow,
  title,
  description,
  primaryHref = "/apply",
  primaryLabel = "Check your options",
  secondaryHref = "/loan-calculator",
  secondaryLabel = "Use a calculator",
  sections,
}: {
  eyebrow: string;
  title: string;
  description: string;
  primaryHref?: string;
  primaryLabel?: string;
  secondaryHref?: string;
  secondaryLabel?: string;
  sections: MarketingSection[];
}) {
  const primary = SITE_CONTENT.primaryColor;
  const accent = SITE_CONTENT.accentColor;

  return (
    <main className="bg-white text-slate-950">
      <section className="relative overflow-hidden text-white" style={{ background: `linear-gradient(120deg, #061326 0%, ${primary} 58%, #18385f 100%)` }}>
        <div className="absolute inset-0 opacity-30" style={{ backgroundImage: "radial-gradient(circle at 80% 15%, rgba(201,162,39,.35), transparent 28%), radial-gradient(circle at 20% 90%, rgba(255,255,255,.12), transparent 25%)" }} />
        <div className="relative mx-auto max-w-7xl px-5 py-16 sm:px-8 sm:py-24">
          <p className="text-xs font-black uppercase tracking-[.22em]" style={{ color: accent }}>{eyebrow}</p>
          <h1 className="mt-5 max-w-4xl text-4xl font-black leading-[.98] tracking-[-.055em] sm:text-6xl">{title}</h1>
          <p className="mt-6 max-w-2xl text-base leading-8 text-white/65 sm:text-lg">{description}</p>
          <div className="mt-8 flex flex-col gap-3 sm:flex-row">
            <Link href={primaryHref} className="rounded-xl px-6 py-4 text-center text-sm font-black text-slate-950" style={{ backgroundColor: accent }}>{primaryLabel} →</Link>
            <Link href={secondaryHref} className="rounded-xl border border-white/15 bg-white/[.06] px-6 py-4 text-center text-sm font-bold text-white">{secondaryLabel}</Link>
          </div>
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-5 py-14 sm:px-8 sm:py-20">
        <div className="grid gap-5 md:grid-cols-2">
          {sections.map((section) => (
            <article key={section.title} className="rounded-3xl border border-slate-200 bg-white p-7 shadow-[0_14px_50px_rgba(15,23,42,.06)]">
              <h2 className="text-xl font-black tracking-tight">{section.title}</h2>
              <p className="mt-3 text-sm leading-7 text-slate-600">{section.body}</p>
              {section.bullets?.length ? <ul className="mt-5 space-y-3 text-sm text-slate-600">{section.bullets.map((bullet) => <li key={bullet} className="flex gap-3"><span className="mt-1 h-2 w-2 shrink-0 rounded-full" style={{ backgroundColor: accent }} />{bullet}</li>)}</ul> : null}
            </article>
          ))}
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-5 pb-20 sm:px-8">
        <div className="rounded-[32px] p-8 text-white sm:p-12" style={{ background: `linear-gradient(120deg, ${primary}, #061326)` }}>
          <h2 className="text-3xl font-black tracking-tight">Clear information. A simpler borrowing journey.</h2>
          <p className="mt-3 max-w-2xl text-sm leading-7 text-white/60">Review the applicable product terms and use the public application when you are ready. Approval and final terms remain subject to Noble&apos;s assessment and verification process.</p>
          <Link href="/apply" className="mt-7 inline-flex rounded-xl px-6 py-3.5 text-sm font-black text-slate-950" style={{ backgroundColor: accent }}>Start an application →</Link>
        </div>
      </section>
    </main>
  );
}
