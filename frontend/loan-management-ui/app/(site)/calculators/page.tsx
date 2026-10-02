"use client";

import Link from "next/link";
import { useTenant } from "../layout";
import { SITE_CONTENT } from "../../../lib/siteContent";
import { pageContent } from "../../../lib/websiteContent";

const buildTools = (cms: ReturnType<typeof pageContent>) =>
  [
    [
      cms.sections[0]?.title || "Loan calculator",
      cms.sections[0]?.body ||
        "Model an amount, term and indicative repayment using a live Noble product configuration.",
      "/loan-calculator",
      "◈",
    ],
    [
      cms.sections[1]?.title || "Payment calculator",
      cms.sections[1]?.body ||
        "Start with a payment target and explore an indicative borrowing scenario.",
      "/payment-calculator",
      "↗",
    ],
    [
      cms.sections[2]?.title || "Interest calculator",
      cms.sections[2]?.body ||
        "See interest, management charges and total scheduled repayment separately.",
      "/interest-calculator",
      "%",
    ],
    [
      cms.sections[3]?.title || "Inflation calculator",
      cms.sections[3]?.body ||
        "Explore how purchasing power changes over time under an assumed inflation rate.",
      "/inflation-calculator",
      "↺",
    ],
  ] as const;

export default function CalculatorsPage() {
  const tenant = useTenant() || SITE_CONTENT;
  const cms = pageContent(tenant.websiteContent, "calculators");
  const primary = tenant.primaryColor || "#0F1B3D";
  const accent = tenant.accentColor || "#C9A227";
  const tools = buildTools(cms);

  return (
    <main className="overflow-hidden bg-[#f5f7fa] text-slate-950">
      <section
        className="relative overflow-hidden text-white"
        style={{
          background: `linear-gradient(125deg,#061326 0%,${primary} 65%,#1a416b 100%)`,
        }}
      >
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_82%_16%,rgba(201,162,39,.27),transparent_24%),radial-gradient(circle_at_10%_90%,rgba(255,255,255,.09),transparent_28%)]" />
        <div className="relative mx-auto max-w-7xl px-5 py-16 sm:px-8 sm:py-24 lg:py-28">
          <p
            className="text-[10px] font-black uppercase tracking-[.24em]"
            style={{ color: accent }}
          >
            {cms.eyebrow}
          </p>
          <h1 className="mt-5 max-w-4xl text-4xl font-black leading-[.98] tracking-[-.06em] sm:text-6xl">
            {cms.title}
          </h1>
          <p className="mt-6 max-w-2xl text-base leading-8 text-white/70 sm:text-lg">
            {cms.description}
          </p>
          <div className="mt-8 flex flex-wrap gap-2 text-[10px] font-bold text-white/55">
            <span className="rounded-full border border-white/10 bg-white/5 px-3 py-2">
              RWF planning
            </span>
            <span className="rounded-full border border-white/10 bg-white/5 px-3 py-2">
              Current product configuration
            </span>
            <span className="rounded-full border border-white/10 bg-white/5 px-3 py-2">
              Illustrative results
            </span>
          </div>
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-5 py-12 sm:px-8 sm:py-20">
        <div className="grid gap-5 md:grid-cols-2">
          {tools.map(([title, body, href, icon], index) => (
            <Link
              key={href}
              href={href}
              className="group relative overflow-hidden rounded-[30px] border border-slate-200 bg-white p-7 shadow-[0_16px_55px_rgba(15,23,42,.055)] transition duration-300 hover:-translate-y-1 hover:shadow-[0_28px_75px_rgba(15,23,42,.10)] sm:p-9"
            >
              <div
                className="absolute -right-12 -top-12 h-36 w-36 rounded-full blur-3xl"
                style={{ backgroundColor: `${accent}18` }}
              />
              <div className="relative flex items-center justify-between gap-4">
                <span
                  className="text-[10px] font-black uppercase tracking-[.2em]"
                  style={{ color: accent }}
                >
                  Calculator {String(index + 1).padStart(2, "0")}
                </span>
                <span
                  className="flex h-11 w-11 items-center justify-center rounded-2xl bg-slate-50 text-sm font-black transition group-hover:translate-x-0.5"
                  style={{ color: primary }}
                >
                  {icon}
                </span>
              </div>
              <h2 className="relative mt-7 text-2xl font-black tracking-tight sm:text-3xl">
                {title}
              </h2>
              <p className="relative mt-3 max-w-xl text-sm leading-7 text-slate-500">
                {body}
              </p>
              <span
                className="relative mt-7 inline-flex items-center gap-2 rounded-2xl border border-slate-200 px-4 py-3 text-xs font-black"
                style={{ color: primary }}
              >
                Open calculator <span>→</span>
              </span>
            </Link>
          ))}
        </div>

        <div className="mt-8 rounded-[30px] border border-slate-200 bg-white p-7 shadow-sm sm:p-9">
          <div className="grid gap-6 lg:grid-cols-[.8fr_1.2fr] lg:items-center">
            <div>
              <p
                className="text-[10px] font-black uppercase tracking-[.2em]"
                style={{ color: accent }}
              >
                Before you apply
              </p>
              <h2 className="mt-3 text-2xl font-black tracking-tight">
                Use the tools to understand the full repayment picture.
              </h2>
            </div>
            <p className="text-sm leading-7 text-slate-500">
              {cms.sections[0]?.body ||
                "Calculator results are estimates. The applicable product configuration, assessment outcome and signed loan agreement determine the actual transaction."}
            </p>
          </div>
        </div>
      </section>
    </main>
  );
}
