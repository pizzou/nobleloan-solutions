"use client";

import Link from "next/link";
import { useTenant } from "./layout";
import LoanCalculator from "../../components/public/LoanCalculator";
import FxRatePanel from "../../components/public/FxRatePanel";

function numberValue(value: unknown): number | null {
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

function money(value: unknown, currency: string) {
  const amount = numberValue(value);

  if (amount == null) return "—";

  return new Intl.NumberFormat("en-RW", {
    style: "currency",
    currency,
    maximumFractionDigits: 0,
  }).format(amount);
}

function SectionEyebrow({
  children,
  color,
}: {
  children: React.ReactNode;
  color: string;
}) {
  return (
    <div
      className="text-[10px] font-black uppercase tracking-[0.24em]"
      style={{ color }}
    >
      {children}
    </div>
  );
}

function Check({ color }: { color: string }) {
  return (
    <span
      className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-xs font-black text-white"
      style={{ backgroundColor: color }}
    >
      ✓
    </span>
  );
}

export default function HomePage() {
  const tenant = useTenant();

  if (!tenant) return null;

  const primary = tenant.primaryColor;
  const accent = tenant.accentColor;
  const currency = tenant.currency || "RWF";

  const services = tenant.services ?? [];
  const stats = tenant.stats ?? [];
  const testimonials = tenant.testimonials ?? [];

  const highlight = services[0];

  const interestRate = numberValue(
    highlight?.monthlyInterestRate ?? tenant.monthlyInterestRate,
  );

  const managementRate = numberValue(
    highlight?.monthlyManagementFeeRate ?? tenant.monthlyManagementFeeRate,
  );

  const processingRate = numberValue(
    highlight?.processingFeeRate ?? tenant.processingFeeRate,
  );

  return (
    <div className="overflow-hidden bg-white">
      {/* ======================================================
          HERO
      ====================================================== */}
      <section className="relative overflow-hidden bg-[#f7f8fa]">
        <div
          className="absolute inset-0 opacity-[0.035]"
          style={{
            backgroundImage: `
              linear-gradient(${primary} 1px, transparent 1px),
              linear-gradient(90deg, ${primary} 1px, transparent 1px)
            `,
            backgroundSize: "54px 54px",
          }}
        />

        <div
          className="absolute -right-32 top-10 h-[520px] w-[520px] rounded-full blur-3xl"
          style={{
            backgroundColor: `${accent}18`,
          }}
        />

        <div
          className="absolute -left-32 bottom-0 h-72 w-72 rounded-full blur-3xl"
          style={{
            backgroundColor: `${primary}10`,
          }}
        />

        <div className="relative mx-auto grid max-w-7xl gap-12 px-4 pb-20 pt-16 md:pb-28 md:pt-24 lg:grid-cols-[1.08fr_0.92fr] lg:items-center">
          <div>
            <div
              className="inline-flex items-center gap-3 rounded-full border bg-white px-4 py-2.5 text-[10px] font-black uppercase tracking-[0.18em] shadow-sm"
              style={{
                borderColor: `${accent}70`,
                color: primary,
              }}
            >
              <span
                className="h-2 w-2 rounded-full"
                style={{ backgroundColor: accent }}
              />
              Official {tenant.name} website
            </div>

            <h1 className="mt-8 max-w-4xl text-[3.3rem] font-black leading-[0.96] tracking-[-0.055em] text-slate-950 sm:text-6xl lg:text-[5.4rem]">
              {tenant.hero?.headline ||
                tenant.tagline ||
                `Financial solutions from ${tenant.name}`}
            </h1>

            <p className="mt-7 max-w-2xl text-base leading-8 text-slate-600 sm:text-lg">
              {tenant.hero?.subtext ||
                tenant.mission ||
                `Professional lending solutions designed around real financial needs.`}
            </p>

            <div className="mt-9 flex flex-wrap gap-3">
              <Link
                href="/apply"
                className="group inline-flex items-center gap-3 rounded-xl px-6 py-4 text-sm font-black text-white shadow-xl transition duration-300 hover:-translate-y-1 hover:shadow-2xl"
                style={{ backgroundColor: primary }}
              >
                Start an Application
                <span className="transition-transform group-hover:translate-x-1">
                  →
                </span>
              </Link>

              <Link
                href="/services"
                className="inline-flex items-center rounded-xl border border-slate-300 bg-white px-6 py-4 text-sm font-black text-slate-800 transition hover:-translate-y-0.5 hover:border-slate-400 hover:shadow-lg"
              >
                Explore Financing
              </Link>
            </div>

            <div className="mt-10 grid max-w-2xl gap-3 sm:grid-cols-3">
              {[
                ["01", "Transparent", "Published products and terms."],
                ["02", "Professional", "Structured lending experience."],
                ["03", "Secure", "Protected digital workflow."],
              ].map(([number, title, text]) => (
                <div
                  key={number}
                  className="border-l-2 bg-white/70 px-4 py-3"
                  style={{
                    borderColor: accent,
                  }}
                >
                  <div
                    className="text-[10px] font-black tracking-[0.2em]"
                    style={{ color: accent }}
                  >
                    {number}
                  </div>

                  <div className="mt-1 text-sm font-black text-slate-900">
                    {title}
                  </div>

                  <div className="mt-1 text-[11px] leading-5 text-slate-500">
                    {text}
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Premium lending panel */}
          <div className="relative">
            <div
              className="relative overflow-hidden rounded-[2.25rem] p-7 text-white shadow-[0_35px_100px_rgba(15,23,42,0.22)] md:p-9"
              style={{
                background: `linear-gradient(145deg, ${primary}, #071426)`,
              }}
            >
              <div
                className="absolute -right-24 -top-24 h-64 w-64 rounded-full blur-3xl"
                style={{
                  backgroundColor: `${accent}22`,
                }}
              />

              <div className="relative">
                <div className="flex items-start justify-between gap-5">
                  <div>
                    <div className="text-[9px] font-black uppercase tracking-[0.25em] text-white/40">
                      Lending institution
                    </div>

                    <div className="mt-2 text-2xl font-black tracking-tight">
                      {tenant.name}
                    </div>
                  </div>

                  <div className="rounded-xl border border-white/10 bg-white/[0.05] px-3 py-2 text-right">
                    <div className="text-[8px] font-black uppercase tracking-widest text-white/35">
                      Currency
                    </div>

                    <div className="mt-1 text-sm font-black">{currency}</div>
                  </div>
                </div>

                <div className="mt-9">
                  <div className="text-[9px] font-black uppercase tracking-[0.22em] text-white/40">
                    Published lending profile
                  </div>

                  <div className="mt-5 grid grid-cols-2 gap-3">
                    <div className="rounded-2xl border border-white/10 bg-white/[0.045] p-5">
                      <div className="text-[9px] uppercase tracking-widest text-white/35">
                        Products
                      </div>

                      <div className="mt-2 text-3xl font-black">
                        {services.length}
                      </div>

                      <div className="mt-1 text-xs text-white/40">
                        published
                      </div>
                    </div>

                    <div className="rounded-2xl border border-white/10 bg-white/[0.045] p-5">
                      <div className="text-[9px] uppercase tracking-widest text-white/35">
                        Market
                      </div>

                      <div className="mt-2 text-2xl font-black">
                        {tenant.country || "—"}
                      </div>

                      <div className="mt-1 text-xs text-white/40">
                        operating market
                      </div>
                    </div>
                  </div>
                </div>

                <div className="mt-5 rounded-2xl border border-white/10 bg-white/[0.045] p-5">
                  <div className="text-[9px] font-black uppercase tracking-[0.2em] text-white/35">
                    Representative published pricing
                  </div>

                  <div className="mt-4 divide-y divide-white/10">
                    {[
                      [
                        "Interest",
                        interestRate == null
                          ? "Published per product"
                          : `${interestRate.toFixed(2)}% / month`,
                      ],
                      [
                        "Management fee",
                        managementRate == null
                          ? "Published per product"
                          : `${managementRate.toFixed(2)}% / month`,
                      ],
                      [
                        "Processing fee",
                        processingRate == null
                          ? "Published per product"
                          : `${processingRate.toFixed(2)}% once`,
                      ],
                    ].map(([label, value]) => (
                      <div
                        key={label}
                        className="flex items-center justify-between gap-4 py-3"
                      >
                        <span className="text-sm text-white/50">{label}</span>

                        <span className="text-sm font-black text-white">
                          {value}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>

                <div className="mt-5 grid grid-cols-2 gap-3">
                  <Link
                    href="/calculator"
                    className="rounded-xl bg-white px-4 py-3.5 text-center text-xs font-black transition hover:-translate-y-0.5"
                    style={{
                      color: primary,
                    }}
                  >
                    Calculate
                  </Link>

                  <Link
                    href="/track"
                    className="rounded-xl border border-white/15 px-4 py-3.5 text-center text-xs font-black text-white transition hover:bg-white/5"
                  >
                    Track application
                  </Link>
                </div>
              </div>
            </div>

            <div className="absolute -bottom-8 -left-6 hidden w-56 rounded-2xl border border-slate-200 bg-white p-5 shadow-2xl md:block">
              <div className="text-[9px] font-black uppercase tracking-[0.18em] text-slate-400">
                Noble standard
              </div>

              <div className="mt-2 text-sm font-black leading-6 text-slate-900">
                Clear information for confident financial decisions.
              </div>

              <div className="mt-4 flex gap-1.5">
                {[1, 2, 3, 4, 5].map((item) => (
                  <span
                    key={item}
                    className="h-1.5 flex-1 rounded-full"
                    style={{
                      backgroundColor: item < 5 ? accent : "#e2e8f0",
                    }}
                  />
                ))}
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ======================================================
          STATS
      ====================================================== */}
      {stats.length > 0 && (
        <section className="border-y border-slate-200 bg-white">
          <div className="mx-auto grid max-w-7xl grid-cols-2 md:grid-cols-4">
            {stats.slice(0, 4).map((stat, index) => (
              <div
                key={`${stat.label}-${stat.value}`}
                className={`px-6 py-8 ${
                  index !== 0 ? "border-l border-slate-200" : ""
                }`}
              >
                <div className="text-lg">{stat.icon || "•"}</div>

                <div className="mt-3 text-2xl font-black tracking-tight text-slate-950 md:text-3xl">
                  {stat.value}
                </div>

                <div className="mt-2 text-[9px] font-black uppercase tracking-[0.16em] text-slate-400">
                  {stat.label}
                </div>
              </div>
            ))}
          </div>
        </section>
      )}

      {/* ======================================================
          INSTITUTIONAL VALUE
      ====================================================== */}
      <section className="mx-auto max-w-7xl px-4 py-20 md:py-28">
        <div className="grid gap-12 lg:grid-cols-[0.75fr_1.25fr] lg:items-end">
          <div>
            <SectionEyebrow color={accent}>The Noble experience</SectionEyebrow>

            <h2 className="mt-4 text-4xl font-black tracking-[-0.035em] text-slate-950 md:text-5xl">
              A more considered way to access finance.
            </h2>

            <p className="mt-5 max-w-xl text-base leading-8 text-slate-600">
              {tenant.name} combines professional lending, transparent product
              information and digital convenience in one client experience.
            </p>

            <Link
              href="/about"
              className="mt-7 inline-flex items-center gap-2 text-sm font-black"
              style={{ color: primary }}
            >
              Discover {tenant.name}
              <span>→</span>
            </Link>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            {[
              [
                "01",
                "Responsible lending",
                "Loan products and published terms are presented clearly before an application is submitted.",
              ],
              [
                "02",
                "Digital convenience",
                "Apply, submit information and follow your application through a professional online workflow.",
              ],
              [
                "03",
                "Financial visibility",
                "Use planning tools to understand indicative amounts before proceeding.",
              ],
              [
                "04",
                "Human support",
                "When you need assistance, verified contact channels remain available throughout your journey.",
              ],
            ].map(([number, title, text]) => (
              <div
                key={number}
                className="group rounded-2xl border border-slate-200 bg-white p-6 transition duration-300 hover:-translate-y-1 hover:border-slate-300 hover:shadow-xl"
              >
                <div
                  className="text-[10px] font-black tracking-[0.18em]"
                  style={{ color: accent }}
                >
                  {number}
                </div>

                <h3 className="mt-4 text-lg font-black text-slate-950">
                  {title}
                </h3>

                <p className="mt-2 text-sm leading-6 text-slate-500">{text}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ======================================================
          SERVICES
      ====================================================== */}
      <section className="border-y border-slate-200 bg-[#f7f8fa] py-20 md:py-28">
        <div className="mx-auto max-w-7xl px-4">
          <div className="flex flex-col justify-between gap-6 md:flex-row md:items-end">
            <div className="max-w-2xl">
              <SectionEyebrow color={accent}>Lending solutions</SectionEyebrow>

              <h2 className="mt-4 text-4xl font-black tracking-[-0.035em] text-slate-950 md:text-5xl">
                Financing designed around your needs.
              </h2>

              <p className="mt-4 text-base leading-8 text-slate-600">
                Explore the products currently published by {tenant.name}.
              </p>
            </div>

            <Link
              href="/services"
              className="text-sm font-black"
              style={{ color: primary }}
            >
              View all services →
            </Link>
          </div>

          {services.length > 0 ? (
            <div className="mt-12 grid gap-5 md:grid-cols-2 lg:grid-cols-3">
              {services.slice(0, 6).map((service, index) => (
                <article
                  key={service.id ?? service.title}
                  className="group relative overflow-hidden rounded-[1.7rem] border border-slate-200 bg-white p-7 transition duration-300 hover:-translate-y-1 hover:shadow-2xl"
                >
                  <div
                    className="absolute right-0 top-0 h-28 w-28 rounded-full blur-3xl opacity-0 transition group-hover:opacity-100"
                    style={{
                      backgroundColor: `${accent}25`,
                    }}
                  />

                  <div className="relative">
                    <div className="flex items-start justify-between">
                      <div
                        className="flex h-14 w-14 items-center justify-center rounded-2xl text-2xl"
                        style={{
                          backgroundColor: `${primary}10`,
                          color: primary,
                        }}
                      >
                        {service.icon || "◈"}
                      </div>

                      <span className="text-[9px] font-black tracking-[0.2em] text-slate-300">
                        {String(index + 1).padStart(2, "0")}
                      </span>
                    </div>

                    <h3 className="mt-7 text-xl font-black text-slate-950">
                      {service.title}
                    </h3>

                    <p className="mt-3 min-h-[72px] text-sm leading-6 text-slate-500">
                      {service.description ||
                        "Product-specific terms are disclosed during the application and approval process."}
                    </p>

                    <div className="mt-6 grid grid-cols-2 gap-3">
                      <div className="rounded-xl bg-slate-50 p-3">
                        <div className="text-[9px] font-black uppercase tracking-wider text-slate-400">
                          Interest
                        </div>

                        <div
                          className="mt-1 text-sm font-black"
                          style={{ color: primary }}
                        >
                          {service.rate || "Published"}
                        </div>
                      </div>

                      <div className="rounded-xl bg-slate-50 p-3">
                        <div className="text-[9px] font-black uppercase tracking-wider text-slate-400">
                          Term
                        </div>

                        <div
                          className="mt-1 text-sm font-black"
                          style={{ color: primary }}
                        >
                          {service.term || "Published"}
                        </div>
                      </div>
                    </div>

                    <Link
                      href={`/apply?type=${encodeURIComponent(service.title)}`}
                      className="mt-7 inline-flex items-center gap-2 text-sm font-black"
                      style={{ color: primary }}
                    >
                      Explore this product
                      <span className="transition-transform group-hover:translate-x-1">
                        →
                      </span>
                    </Link>
                  </div>
                </article>
              ))}
            </div>
          ) : (
            <div className="mt-10 rounded-3xl border border-slate-200 bg-white p-12 text-center text-sm text-slate-500">
              Products are currently being configured.
            </div>
          )}
        </div>
      </section>

      {/* ======================================================
          CALCULATOR
      ====================================================== */}
      <section className="mx-auto max-w-7xl px-4 py-20 md:py-28">
        <div className="mb-10 max-w-2xl">
          <SectionEyebrow color={accent}>Plan with confidence</SectionEyebrow>

          <h2 className="mt-4 text-4xl font-black tracking-[-0.035em] text-slate-950 md:text-5xl">
            Understand the numbers before you apply.
          </h2>

          <p className="mt-4 text-base leading-8 text-slate-600">
            Use the published product information to explore an indicative
            financing scenario.
          </p>
        </div>

        <LoanCalculator tenant={tenant} />
      </section>

      {/* ======================================================
          HOW IT WORKS
      ====================================================== */}
      <section className="bg-[#f7f8fa] py-20 md:py-28">
        <div className="mx-auto max-w-7xl px-4">
          <div className="max-w-2xl">
            <SectionEyebrow color={accent}>Your journey</SectionEyebrow>

            <h2 className="mt-4 text-4xl font-black tracking-[-0.035em] text-slate-950 md:text-5xl">
              A clear path from application to servicing.
            </h2>
          </div>

          <div className="mt-12 grid gap-4 md:grid-cols-5">
            {[
              ["01", "Explore", "Review the available financing solutions."],
              ["02", "Apply", "Submit your application securely."],
              [
                "03",
                "Review",
                "Your information is assessed through the lender's process.",
              ],
              ["04", "Approval", "Receive the lender's approved terms."],
              ["05", "Service", "Manage repayment and ongoing support."],
            ].map(([number, title, text], index) => (
              <div
                key={number}
                className="relative rounded-2xl border border-slate-200 bg-white p-6"
              >
                {index < 4 && (
                  <div
                    className="absolute right-[-17px] top-10 z-10 hidden h-px w-8 md:block"
                    style={{
                      backgroundColor: `${accent}70`,
                    }}
                  />
                )}

                <div
                  className="text-[10px] font-black tracking-[0.2em]"
                  style={{ color: accent }}
                >
                  {number}
                </div>

                <h3 className="mt-5 text-base font-black text-slate-950">
                  {title}
                </h3>

                <p className="mt-2 text-xs leading-6 text-slate-500">{text}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ======================================================
          TRUST
      ====================================================== */}
      <section className="mx-auto max-w-7xl px-4 py-20 md:py-28">
        <div className="grid gap-6 lg:grid-cols-[1fr_0.85fr]">
          <div
            className="rounded-[2rem] p-8 text-white md:p-10"
            style={{
              background: `linear-gradient(145deg, ${primary}, #071426)`,
            }}
          >
            <SectionEyebrow color={`${accent}`}>
              Confidence matters
            </SectionEyebrow>

            <h2 className="mt-4 max-w-2xl text-3xl font-black tracking-tight md:text-4xl">
              A lending experience built around clarity.
            </h2>

            <p className="mt-5 max-w-2xl text-sm leading-7 text-white/60">
              {tenant.name} provides a structured digital experience while
              keeping the important financial information visible throughout the
              customer journey.
            </p>

            <div className="mt-8 grid gap-3 sm:grid-cols-2">
              {[
                "Published product information",
                "Secure digital application",
                "Clear repayment visibility",
                "Verified support channels",
              ].map((item) => (
                <div
                  key={item}
                  className="flex items-center gap-3 rounded-xl border border-white/10 bg-white/[0.04] p-4"
                >
                  <Check color={accent} />

                  <span className="text-sm font-bold text-white/80">
                    {item}
                  </span>
                </div>
              ))}
            </div>
          </div>

          <div className="rounded-[2rem] border border-slate-200 bg-white p-8 shadow-sm md:p-10">
            <SectionEyebrow color={accent}>Financial tools</SectionEyebrow>

            <h2 className="mt-4 text-3xl font-black tracking-tight text-slate-950">
              More than an application form.
            </h2>

            <p className="mt-4 text-sm leading-7 text-slate-500">
              Explore financing, calculate indicative scenarios and access
              financial reference tools from one place.
            </p>

            <div className="mt-7 space-y-3">
              <Link
                href="/calculator"
                className="flex items-center justify-between rounded-xl border border-slate-200 p-4 transition hover:border-slate-300 hover:bg-slate-50"
              >
                <span className="text-sm font-black text-slate-900">
                  Loan calculator
                </span>
                <span style={{ color: primary }}>→</span>
              </Link>

              <Link
                href="/services"
                className="flex items-center justify-between rounded-xl border border-slate-200 p-4 transition hover:border-slate-300 hover:bg-slate-50"
              >
                <span className="text-sm font-black text-slate-900">
                  Compare services
                </span>
                <span style={{ color: primary }}>→</span>
              </Link>

              <Link
                href="/track"
                className="flex items-center justify-between rounded-xl border border-slate-200 p-4 transition hover:border-slate-300 hover:bg-slate-50"
              >
                <span className="text-sm font-black text-slate-900">
                  Track application
                </span>
                <span style={{ color: primary }}>→</span>
              </Link>
            </div>
          </div>
        </div>
      </section>

      {/* ======================================================
          FX
      ====================================================== */}
      <section className="border-y border-slate-200 bg-[#f7f8fa] py-20 md:py-28">
        <div className="mx-auto max-w-7xl px-4">
          <div className="mb-10 max-w-2xl">
            <SectionEyebrow color={accent}>Market reference</SectionEyebrow>

            <h2 className="mt-4 text-4xl font-black tracking-tight text-slate-950">
              Financial visibility in one place.
            </h2>

            <p className="mt-4 text-base leading-7 text-slate-600">
              Access the platform&apos;s available foreign-exchange reference
              information without leaving the Noble Loan experience.
            </p>
          </div>

          <FxRatePanel />
        </div>
      </section>

      {/* ======================================================
          TESTIMONIALS
      ====================================================== */}
      {testimonials.length > 0 && (
        <section className="mx-auto max-w-7xl px-4 py-20 md:py-28">
          <div className="flex flex-col justify-between gap-5 md:flex-row md:items-end">
            <div className="max-w-2xl">
              <SectionEyebrow color={accent}>Client experience</SectionEyebrow>

              <h2 className="mt-4 text-4xl font-black tracking-tight text-slate-950 md:text-5xl">
                Trusted by the people we serve.
              </h2>
            </div>
          </div>

          <div className="mt-12 grid gap-5 md:grid-cols-3">
            {testimonials.slice(0, 6).map((item) => (
              <article
                key={`${item.name}-${item.text.slice(0, 15)}`}
                className="rounded-[1.7rem] border border-slate-200 bg-white p-7 shadow-sm transition hover:-translate-y-1 hover:shadow-xl"
              >
                <div
                  className="text-sm tracking-wide"
                  style={{ color: accent }}
                >
                  {"★".repeat(Math.max(1, Math.min(5, item.rating || 5)))}
                </div>

                <p className="mt-5 text-sm leading-7 text-slate-600">
                  “{item.text}”
                </p>

                <div className="mt-7 border-t border-slate-100 pt-5">
                  <div className="text-sm font-black text-slate-950">
                    {item.name}
                  </div>

                  {item.role && (
                    <div className="mt-1 text-xs text-slate-400">
                      {item.role}
                    </div>
                  )}
                </div>
              </article>
            ))}
          </div>
        </section>
      )}

      {/* ======================================================
          FINAL CTA
      ====================================================== */}
      <section className="px-4 pb-24">
        <div
          className="mx-auto max-w-7xl overflow-hidden rounded-[2.25rem] text-white shadow-[0_35px_100px_rgba(15,23,42,0.18)]"
          style={{
            background: `linear-gradient(135deg, ${primary}, #071426)`,
          }}
        >
          <div className="relative grid gap-10 px-8 py-12 md:px-14 md:py-16 lg:grid-cols-[1fr_auto] lg:items-center">
            <div
              className="absolute -right-32 -top-32 h-80 w-80 rounded-full blur-3xl"
              style={{
                backgroundColor: `${accent}18`,
              }}
            />

            <div className="relative">
              <SectionEyebrow color={`${accent}`}>
                Your next step
              </SectionEyebrow>

              <h2 className="mt-4 max-w-3xl text-3xl font-black tracking-tight md:text-5xl">
                Ready to explore your financing options?
              </h2>

              <p className="mt-4 max-w-2xl text-sm leading-7 text-white/60">
                Review the available products, calculate an indicative scenario
                and submit your application through {tenant.name}.
              </p>
            </div>

            <div className="relative flex flex-wrap gap-3">
              <Link
                href="/apply"
                className="rounded-xl px-6 py-4 text-sm font-black"
                style={{
                  backgroundColor: accent,
                  color: primary,
                }}
              >
                Start an Application
              </Link>

              <Link
                href="/contact"
                className="rounded-xl border border-white/15 px-6 py-4 text-sm font-bold text-white transition hover:bg-white/5"
              >
                Talk to Us
              </Link>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
