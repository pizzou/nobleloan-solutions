"use client";

import Link from "next/link";
import { useTenant } from "./layout";
import LoanCalculator from "../../components/LoanCalculator";
import FxRatePanel from "../../components/FxWidget";

function safeNumber(value: unknown): number | null {
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

function money(value: unknown, currency: string) {
  const amount = safeNumber(value);
  if (amount == null) return "—";
  return new Intl.NumberFormat("en-RW", {
    style: "currency",
    currency,
    maximumFractionDigits: 0,
  }).format(amount);
}

function IconBadge({ children }: { children: React.ReactNode }) {
  return (
    <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-slate-950 text-lg text-white shadow-sm">
      {children}
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
  const interestRate = safeNumber(
    highlight?.monthlyInterestRate ?? tenant.monthlyInterestRate,
  );
  const managementRate = safeNumber(
    highlight?.monthlyManagementFeeRate ?? tenant.monthlyManagementFeeRate,
  );
  const processingRate = safeNumber(
    highlight?.processingFeeRate ?? tenant.processingFeeRate,
  );

  return (
    <div className="overflow-hidden bg-white">
      {/* Hero */}
      <section className="relative overflow-hidden border-b border-slate-200 bg-slate-50">
        <div
          className="absolute inset-0 opacity-[0.055]"
          style={{
            backgroundImage: `linear-gradient(${primary} 1px, transparent 1px), linear-gradient(90deg, ${primary} 1px, transparent 1px)`,
            backgroundSize: "46px 46px",
          }}
        />
        <div
          className="absolute -right-32 top-20 h-80 w-80 rounded-full blur-3xl"
          style={{ backgroundColor: `${accent}30` }}
        />
        <div className="relative mx-auto grid max-w-7xl gap-12 px-4 pb-20 pt-14 md:grid-cols-[1.02fr_0.98fr] md:pb-28 md:pt-20">
          <div className="flex flex-col justify-center">
            <div
              className="inline-flex w-fit items-center gap-2 rounded-full border px-4 py-2 text-[11px] font-black uppercase tracking-[0.2em]"
              style={{
                borderColor: `${accent}90`,
                backgroundColor: `${accent}12`,
                color: primary,
              }}
            >
              <span
                className="h-2 w-2 rounded-full"
                style={{ backgroundColor: accent }}
              />
              Official {tenant.name} website
            </div>

            <h1 className="mt-7 max-w-3xl text-5xl font-black leading-[0.98] tracking-[-0.04em] text-slate-950 md:text-7xl">
              {tenant.hero?.headline ||
                tenant.tagline ||
                `Financial solutions from ${tenant.name}`}
            </h1>

            <p className="mt-7 max-w-2xl text-lg leading-8 text-slate-600 md:text-xl">
              {tenant.hero?.subtext ||
                tenant.mission ||
                `Professional lending solutions designed around real financial needs.`}
            </p>

            <div className="mt-9 flex flex-wrap gap-3">
              <Link
                href="/apply"
                className="rounded-2xl px-7 py-4 text-sm font-black text-white shadow-xl transition hover:-translate-y-0.5"
                style={{ backgroundColor: primary }}
              >
                Start an application
              </Link>
              <Link
                href="/calculator"
                className="rounded-2xl border-2 px-7 py-4 text-sm font-black transition hover:bg-white"
                style={{ borderColor: primary, color: primary }}
              >
                Calculate a loan
              </Link>
            </div>

            <div className="mt-8 grid max-w-2xl grid-cols-1 gap-3 sm:grid-cols-3">
              {[
                ["Transparent", "Published pricing and terms"],
                ["Secure", "Protected online applications"],
                ["Responsive", "Support from your lender"],
              ].map(([title, text]) => (
                <div
                  key={title}
                  className="rounded-2xl border border-slate-200 bg-white/80 p-4 shadow-sm backdrop-blur"
                >
                  <div className="text-xs font-black text-slate-900">
                    {title}
                  </div>
                  <div className="mt-1 text-[11px] leading-5 text-slate-500">
                    {text}
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="relative">
            <div
              className="overflow-hidden rounded-[2rem] p-7 text-white shadow-[0_30px_90px_rgba(15,23,42,0.22)] md:p-9"
              style={{
                background: `linear-gradient(145deg, ${primary}, #071427)`,
              }}
            >
              <div className="flex items-start justify-between gap-4">
                <div>
                  <div className="text-[10px] font-black uppercase tracking-[0.22em] text-white/50">
                    Lending profile
                  </div>
                  <div className="mt-2 text-2xl font-black tracking-tight">
                    {tenant.name}
                  </div>
                </div>
                <div className="rounded-2xl border border-white/10 bg-white/[0.06] px-3 py-2 text-right">
                  <div className="text-[9px] font-black uppercase tracking-widest text-white/40">
                    Currency
                  </div>
                  <div className="mt-1 text-sm font-black">{currency}</div>
                </div>
              </div>

              <div className="mt-8 grid gap-3 sm:grid-cols-2">
                <div className="rounded-2xl border border-white/10 bg-white/[0.05] p-4">
                  <div className="text-[10px] font-black uppercase tracking-wider text-white/45">
                    Published products
                  </div>
                  <div className="mt-2 text-3xl font-black">
                    {services.length}
                  </div>
                </div>
                <div className="rounded-2xl border border-white/10 bg-white/[0.05] p-4">
                  <div className="text-[10px] font-black uppercase tracking-wider text-white/45">
                    Loan currency
                  </div>
                  <div className="mt-2 text-3xl font-black">{currency}</div>
                </div>
              </div>

              <div className="mt-5 rounded-2xl border border-white/10 bg-white/[0.05] p-5">
                <div className="text-[10px] font-black uppercase tracking-wider text-white/45">
                  Representative product pricing
                </div>
                <div className="mt-4 space-y-3">
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
                        ? "Published at application"
                        : `${processingRate.toFixed(2)}% once`,
                    ],
                  ].map(([label, value]) => (
                    <div
                      key={label}
                      className="flex items-center justify-between border-b border-white/10 py-2.5 last:border-0"
                    >
                      <span className="text-sm text-white/55">{label}</span>
                      <span className="text-sm font-black text-white">
                        {value}
                      </span>
                    </div>
                  ))}
                </div>
              </div>

              <div className="mt-5 grid grid-cols-2 gap-3">
                <Link
                  href="/services"
                  className="rounded-2xl bg-white px-4 py-3 text-center text-sm font-black"
                  style={{ color: primary }}
                >
                  Explore services
                </Link>
                <Link
                  href="/track"
                  className="rounded-2xl border border-white/20 px-4 py-3 text-center text-sm font-bold text-white"
                >
                  Track application
                </Link>
              </div>
            </div>

            <div className="absolute -bottom-8 -left-5 hidden w-52 rounded-2xl border border-slate-200 bg-white p-4 shadow-2xl md:block">
              <div className="text-[10px] font-black uppercase tracking-wider text-slate-400">
                Customer first
              </div>
              <div className="mt-2 text-sm font-black text-slate-900">
                Clear information. Confident decisions.
              </div>
              <div className="mt-3 h-1.5 overflow-hidden rounded-full bg-slate-100">
                <div
                  className="h-full w-[92%] rounded-full"
                  style={{ backgroundColor: accent }}
                />
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Stats */}
      {stats.length > 0 && (
        <section className="border-b border-slate-200 bg-white">
          <div className="mx-auto grid max-w-7xl grid-cols-2 divide-x divide-slate-200 px-4 py-8 md:grid-cols-4">
            {stats.slice(0, 4).map((stat) => (
              <div
                key={`${stat.label}-${stat.value}`}
                className="px-5 py-4 text-center first:pl-0 last:pr-0"
              >
                <div className="text-xl">{stat.icon || "•"}</div>
                <div className="mt-2 text-2xl font-black tracking-tight text-slate-950">
                  {stat.value}
                </div>
                <div className="mt-1 text-[10px] font-black uppercase tracking-[0.14em] text-slate-400">
                  {stat.label}
                </div>
              </div>
            ))}
          </div>
        </section>
      )}

      {/* Calculator */}
      <section className="mx-auto max-w-7xl px-4 py-16 md:py-20">
        <LoanCalculator tenant={tenant} />
      </section>

      {/* Services */}
      <section className="bg-slate-50 py-20">
        <div className="mx-auto max-w-7xl px-4">
          <div className="flex flex-col justify-between gap-5 md:flex-row md:items-end">
            <div className="max-w-2xl">
              <div
                className="text-[11px] font-black uppercase tracking-[0.22em]"
                style={{ color: accent }}
              >
                Products & services
              </div>
              <h2 className="mt-3 text-4xl font-black tracking-tight text-slate-950">
                Financing built around your needs.
              </h2>
              <p className="mt-4 text-base leading-7 text-slate-600">
                Compare the products currently published by {tenant.name}. Every
                card uses the lender's configured pricing and limits.
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
            <div className="mt-10 grid gap-5 md:grid-cols-2 lg:grid-cols-3">
              {services.slice(0, 6).map((service, index) => (
                <article
                  key={service.title}
                  className="group rounded-[1.7rem] border border-slate-200 bg-white p-6 shadow-sm transition hover:-translate-y-1 hover:shadow-xl"
                >
                  <div className="flex items-start justify-between gap-4">
                    <IconBadge>{service.icon || "💼"}</IconBadge>
                    <span className="rounded-full bg-slate-100 px-3 py-1 text-[10px] font-black uppercase tracking-wider text-slate-400">
                      {String(index + 1).padStart(2, "0")}
                    </span>
                  </div>
                  <h3 className="mt-5 text-xl font-black text-slate-950">
                    {service.title}
                  </h3>
                  <p className="mt-2 min-h-14 text-sm leading-6 text-slate-600">
                    {service.description ||
                      "Product-specific terms are disclosed during the application and approval process."}
                  </p>
                  <div className="mt-6 grid grid-cols-2 gap-3">
                    <div className="rounded-xl bg-slate-50 p-3">
                      <div className="text-[10px] font-black uppercase tracking-wider text-slate-400">
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
                      <div className="text-[10px] font-black uppercase tracking-wider text-slate-400">
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
                    className="mt-6 inline-flex text-sm font-black"
                    style={{ color: primary }}
                  >
                    Apply for {service.title} →
                  </Link>
                </article>
              ))}
            </div>
          ) : (
            <div className="mt-10 rounded-3xl border border-slate-200 bg-white p-10 text-center text-sm text-slate-500">
              Products are currently being configured.
            </div>
          )}
        </div>
      </section>

      {/* Trust / digital tools */}
      <section className="mx-auto max-w-7xl px-4 py-20">
        <div className="grid gap-6 lg:grid-cols-[1fr_1fr]">
          <div className="rounded-[2rem] border border-slate-200 bg-white p-8 shadow-sm md:p-10">
            <div
              className="text-[11px] font-black uppercase tracking-[0.22em]"
              style={{ color: accent }}
            >
              A modern lender experience
            </div>
            <h2 className="mt-3 text-3xl font-black tracking-tight text-slate-950">
              Professional lending, without the unnecessary friction.
            </h2>
            <div className="mt-8 space-y-4">
              {[
                [
                  "01",
                  "Transparent pricing",
                  "Review published product terms before submitting an application.",
                ],
                [
                  "02",
                  "Digital workflow",
                  "Apply online, upload supporting documents and track progress.",
                ],
                [
                  "03",
                  "Financial visibility",
                  "Use the calculator and live FX reference to understand your numbers.",
                ],
                [
                  "04",
                  "Direct support",
                  "Contact the lender through the verified details shown on this website.",
                ],
              ].map(([n, title, text]) => (
                <div
                  key={n}
                  className="flex gap-4 rounded-2xl border border-slate-100 bg-slate-50 p-4"
                >
                  <span
                    className="text-xs font-black"
                    style={{ color: accent }}
                  >
                    {n}
                  </span>
                  <div>
                    <div className="text-sm font-black text-slate-900">
                      {title}
                    </div>
                    <div className="mt-1 text-xs leading-5 text-slate-500">
                      {text}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div
            className="rounded-[2rem] border border-slate-200 bg-slate-950 p-8 text-white shadow-sm md:p-10"
            style={{
              background: `linear-gradient(145deg, ${primary}, #071427)`,
            }}
          >
            <div className="text-[11px] font-black uppercase tracking-[0.22em] text-white/45">
              Live FX reference
            </div>
            <h2 className="mt-3 text-3xl font-black tracking-tight">
              One platform for lending and financial clarity.
            </h2>
            <p className="mt-3 max-w-xl text-sm leading-6 text-white/60">
              Check current market reference rates without leaving {tenant.name}
              's website.
            </p>
            <div className="mt-8 rounded-2xl border border-white/10 bg-white/[0.04] p-5">
              <div className="grid grid-cols-3 gap-3">
                {[tenant.currency || "RWF", "USD", "EUR"].map((code, index) => (
                  <div
                    key={`${code}-${index}`}
                    className="rounded-xl bg-white/[0.04] p-4 text-center"
                  >
                    <div className="text-[10px] font-black uppercase tracking-wider text-white/40">
                      Currency
                    </div>
                    <div className="mt-2 text-xl font-black">{code}</div>
                  </div>
                ))}
              </div>
            </div>
            <Link
              href="/calculator"
              className="mt-7 inline-flex rounded-2xl px-5 py-3 text-sm font-black"
              style={{ backgroundColor: accent, color: primary }}
            >
              Open calculator & FX tools →
            </Link>
          </div>
        </div>
      </section>

      {/* FX */}
      <section className="bg-slate-50 py-20">
        <div className="mx-auto max-w-7xl px-4">
          <FxRatePanel tenant={tenant} />
        </div>
      </section>

      {/* Testimonials */}
      {testimonials.length > 0 && (
        <section className="mx-auto max-w-7xl px-4 py-20">
          <div className="max-w-2xl">
            <div
              className="text-[11px] font-black uppercase tracking-[0.22em]"
              style={{ color: accent }}
            >
              Client voice
            </div>
            <h2 className="mt-3 text-4xl font-black tracking-tight text-slate-950">
              Trusted by the people we serve.
            </h2>
          </div>
          <div className="mt-10 grid gap-5 md:grid-cols-3">
            {testimonials.slice(0, 6).map((item) => (
              <article
                key={`${item.name}-${item.text.slice(0, 14)}`}
                className="rounded-[1.6rem] border border-slate-200 bg-white p-6 shadow-sm"
              >
                <div
                  className="text-sm tracking-wide"
                  style={{ color: accent }}
                >
                  {"★".repeat(Math.max(1, Math.min(5, item.rating || 5)))}
                </div>
                <p className="mt-4 text-sm leading-7 text-slate-600">
                  “{item.text}”
                </p>
                <div className="mt-6 text-sm font-black text-slate-900">
                  {item.name}
                </div>
                {item.role && (
                  <div className="mt-1 text-xs text-slate-400">{item.role}</div>
                )}
              </article>
            ))}
          </div>
        </section>
      )}

      {/* CTA */}
      <section className="px-4 pb-20">
        <div
          className="mx-auto max-w-6xl overflow-hidden rounded-[2rem] text-white shadow-[0_25px_80px_rgba(15,23,42,0.18)]"
          style={{ background: `linear-gradient(135deg, ${primary}, #071427)` }}
        >
          <div className="grid gap-8 px-8 py-12 md:grid-cols-[1fr_auto] md:items-center md:px-12 md:py-14">
            <div>
              <div className="text-[10px] font-black uppercase tracking-[0.22em] text-white/45">
                Next step
              </div>
              <h2 className="mt-2 text-3xl font-black md:text-4xl">
                Ready to move forward?
              </h2>
              <p className="mt-3 max-w-2xl text-sm leading-7 text-white/65">
                Review the published products, calculate your numbers, then
                submit an application through {tenant.name}'s secure digital
                workflow.
              </p>
            </div>
            <div className="flex flex-wrap gap-3">
              <Link
                href="/apply"
                className="rounded-2xl px-6 py-3.5 text-sm font-black"
                style={{ backgroundColor: accent, color: primary }}
              >
                Apply online
              </Link>
              <Link
                href="/contact"
                className="rounded-2xl border border-white/20 px-6 py-3.5 text-sm font-bold text-white"
              >
                Talk to us
              </Link>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
