"use client";

import Link from "next/link";
import { useTenant } from "./layout";
import LoanCalculator from "../../components/LoanCalculator";
import FxWidget from "../../components/FxWidget";

function MiniIcon({
  children,
  primary,
}: {
  children: React.ReactNode;
  primary: string;
}) {
  return (
    <span
      className="flex h-11 w-11 items-center justify-center rounded-2xl text-lg"
      style={{ backgroundColor: `${primary}10`, color: primary }}
    >
      {children}
    </span>
  );
}

export default function HomePage() {
  const tenant = useTenant();
  if (!tenant) return null;

  const primary = tenant.primaryColor;
  const accent = tenant.accentColor;
  const services = tenant.services ?? [];
  const stats = tenant.stats ?? [];
  const testimonials = tenant.testimonials ?? [];
  const currency = tenant.currency || "RWF";

  return (
    <div>
      <section className="relative overflow-hidden bg-slate-50">
        <div
          className="absolute inset-0 opacity-[0.04]"
          style={{
            backgroundImage: `radial-gradient(circle at 2px 2px, ${primary} 1px, transparent 0)`,
            backgroundSize: "30px 30px",
          }}
        />
        <div className="relative mx-auto grid max-w-7xl gap-12 px-4 py-16 sm:py-20 lg:grid-cols-[1.08fr_0.92fr] lg:py-24">
          <div className="flex flex-col justify-center">
            <div
              className="inline-flex w-fit items-center gap-2 rounded-full border px-3.5 py-1.5 text-[10px] font-black uppercase tracking-[0.16em]"
              style={{
                borderColor: `${accent}65`,
                color: primary,
                backgroundColor: `${accent}10`,
              }}
            >
              <span
                className="h-2 w-2 rounded-full"
                style={{ backgroundColor: accent }}
              />
              Official website • {tenant.country || "your market"}
            </div>
            <h1 className="mt-6 max-w-3xl text-4xl font-black leading-[1.04] tracking-tight text-slate-950 sm:text-5xl lg:text-6xl">
              {tenant.hero?.headline ||
                tenant.tagline ||
                `Financial solutions from ${tenant.name}`}
            </h1>
            <p className="mt-6 max-w-2xl text-base leading-8 text-slate-600 sm:text-lg">
              {tenant.hero?.subtext ||
                tenant.mission ||
                `Explore financing options from ${tenant.name}, review the terms clearly, and apply online through a secure process.`}
            </p>
            <div className="mt-8 flex flex-wrap gap-3">
              <Link
                href="/apply"
                className="rounded-2xl px-7 py-3.5 text-sm font-black text-white shadow-lg transition hover:-translate-y-0.5 hover:shadow-xl"
                style={{ backgroundColor: primary }}
              >
                Apply for financing
              </Link>
              <Link
                href="/services"
                className="rounded-2xl border-2 bg-white px-7 py-3.5 text-sm font-black transition hover:bg-slate-50"
                style={{ borderColor: primary, color: primary }}
              >
                Explore products
              </Link>
            </div>
            <div className="mt-8 grid max-w-2xl grid-cols-1 gap-3 sm:grid-cols-3">
              {[
                ["Transparent", "Clear published terms"],
                ["Digital", "Online application flow"],
                ["Human", "Dedicated support"],
              ].map(([title, text]) => (
                <div
                  key={title}
                  className="rounded-2xl border border-slate-200 bg-white/80 p-4 shadow-sm"
                >
                  <div
                    className="text-sm font-black"
                    style={{ color: primary }}
                  >
                    {title}
                  </div>
                  <div className="mt-1 text-xs leading-5 text-slate-500">
                    {text}
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="relative">
            <div
              className="absolute -inset-6 rounded-[3rem] opacity-60 blur-3xl"
              style={{
                background: `linear-gradient(135deg, ${accent}25, ${primary}25)`,
              }}
            />
            <div
              className="relative overflow-hidden rounded-[2rem] border border-white/10 p-7 text-white shadow-2xl sm:p-9"
              style={{
                background: `linear-gradient(145deg, ${primary}, ${primary}E6)`,
              }}
            >
              <div className="flex items-start justify-between gap-4">
                <div>
                  <div className="text-[10px] font-black uppercase tracking-[0.22em] text-white/60">
                    {tenant.name}
                  </div>
                  <div className="mt-2 max-w-sm text-2xl font-black tracking-tight">
                    A professional lending journey, from application to
                    repayment.
                  </div>
                </div>
                <div className="rounded-2xl border border-white/10 bg-white/10 p-3 text-xl">
                  ◆
                </div>
              </div>
              <div className="mt-8 grid grid-cols-2 gap-3">
                {[
                  ["Products", `${services.length} available`],
                  [
                    "Interest",
                    tenant.monthlyInterestRate != null
                      ? `${tenant.monthlyInterestRate}% / month`
                      : "By product",
                  ],
                  [
                    "Management",
                    tenant.monthlyManagementFeeRate != null
                      ? `${tenant.monthlyManagementFeeRate}% / month`
                      : "By product",
                  ],
                  [
                    "Processing",
                    tenant.processingFeeRate != null
                      ? `${tenant.processingFeeRate}%`
                      : "As disclosed",
                  ],
                ].map(([label, value]) => (
                  <div
                    key={label}
                    className="rounded-2xl border border-white/10 bg-white/5 p-4"
                  >
                    <div className="text-[10px] font-bold uppercase tracking-wider text-white/45">
                      {label}
                    </div>
                    <div className="mt-2 text-sm font-black text-white">
                      {value}
                    </div>
                  </div>
                ))}
              </div>
              <div className="mt-7 rounded-2xl border border-white/10 bg-white/5 p-5">
                <div className="flex items-center justify-between gap-4">
                  <span className="text-xs font-semibold text-white/60">
                    Need to understand your options?
                  </span>
                  <Link
                    href="/"
                    className="text-xs font-black"
                    style={{ color: accent }}
                  >
                    Use calculator
                  </Link>
                </div>
                <div className="mt-4 flex items-end gap-2">
                  <div className="text-4xl font-black">
                    {tenant.minLoanAmount
                      ? `${currency} ${Number(tenant.minLoanAmount).toLocaleString()}`
                      : `${currency}`}
                  </div>
                  <div className="pb-1 text-xs text-white/50">
                    starting from published lender limits
                  </div>
                </div>
              </div>
              <Link
                href="/apply"
                className="mt-6 block rounded-2xl py-3.5 text-center text-sm font-black"
                style={{ backgroundColor: accent, color: primary }}
              >
                Start an application →
              </Link>
            </div>
          </div>
        </div>
      </section>

      <FxWidget baseCurrency={currency} primary={primary} />

      {stats.length > 0 && (
        <section className="border-b border-slate-100 bg-white">
          <div className="mx-auto grid max-w-7xl grid-cols-2 divide-x divide-slate-100 px-4 py-8 md:grid-cols-4">
            {stats.slice(0, 4).map((stat) => (
              <div
                key={`${stat.label}-${stat.value}`}
                className="px-5 text-center first:pl-0 last:pr-0"
              >
                <div className="text-2xl">{stat.icon || "•"}</div>
                <div className="mt-1 text-2xl font-black text-slate-950">
                  {stat.value}
                </div>
                <div className="text-[10px] font-black uppercase tracking-[0.16em] text-slate-400">
                  {stat.label}
                </div>
              </div>
            ))}
          </div>
        </section>
      )}

      <section className="mx-auto max-w-7xl px-4 py-20 sm:py-24">
        <div className="grid gap-12 lg:grid-cols-[0.72fr_1.28fr] lg:items-end">
          <div>
            <div
              className="text-[11px] font-black uppercase tracking-[0.2em]"
              style={{ color: accent }}
            >
              Published products
            </div>
            <h2 className="mt-3 text-3xl font-black tracking-tight text-slate-950 sm:text-4xl">
              Choose the financing product that fits your purpose.
            </h2>
            <p className="mt-4 max-w-xl text-sm leading-7 text-slate-600">
              Review product-level terms before starting your application. Final
              pricing and eligibility are confirmed by {tenant.name}.
            </p>
          </div>
          <div className="flex justify-start gap-3 lg:justify-end">
            <Link
              href="/services"
              className="rounded-xl border border-slate-200 px-5 py-3 text-sm font-bold text-slate-700 hover:bg-slate-50"
            >
              View all products
            </Link>
            <Link
              href="/apply"
              className="rounded-xl px-5 py-3 text-sm font-black text-white"
              style={{ backgroundColor: primary }}
            >
              Apply now
            </Link>
          </div>
        </div>

        {services.length > 0 ? (
          <div className="mt-10 grid gap-5 md:grid-cols-2 xl:grid-cols-3">
            {services.slice(0, 6).map((service) => (
              <article
                key={service.title}
                className="group rounded-[1.5rem] border border-slate-200 bg-white p-6 shadow-sm transition duration-200 hover:-translate-y-1 hover:shadow-xl"
              >
                <MiniIcon primary={primary}>{service.icon || "◆"}</MiniIcon>
                <h3 className="mt-5 text-lg font-black text-slate-950">
                  {service.title}
                </h3>
                <p className="mt-2 min-h-14 text-sm leading-6 text-slate-600">
                  {service.description ||
                    "Published product terms are available before application."}
                </p>
                <div className="mt-5 grid grid-cols-2 gap-3">
                  <div className="rounded-2xl bg-slate-50 p-3">
                    <div className="text-[10px] font-black uppercase tracking-wider text-slate-400">
                      Rate
                    </div>
                    <div
                      className="mt-1 text-sm font-black"
                      style={{ color: primary }}
                    >
                      {service.rate || "See terms"}
                    </div>
                  </div>
                  <div className="rounded-2xl bg-slate-50 p-3">
                    <div className="text-[10px] font-black uppercase tracking-wider text-slate-400">
                      Term
                    </div>
                    <div
                      className="mt-1 text-sm font-black"
                      style={{ color: primary }}
                    >
                      {service.term || "See terms"}
                    </div>
                  </div>
                </div>
                <Link
                  href={`/apply?type=${encodeURIComponent(service.title)}`}
                  className="mt-5 inline-flex text-sm font-black transition group-hover:translate-x-0.5"
                  style={{ color: primary }}
                >
                  Explore this product →
                </Link>
              </article>
            ))}
          </div>
        ) : (
          <div className="mt-10 rounded-2xl border border-slate-200 bg-slate-50 p-8 text-center text-sm text-slate-500">
            Products are currently being configured. Please contact{" "}
            {tenant.name} for current availability.
          </div>
        )}
      </section>

      <section className="bg-slate-50">
        <div className="mx-auto max-w-7xl px-4 py-20 sm:py-24">
          <div className="max-w-2xl">
            <div
              className="text-[11px] font-black uppercase tracking-[0.2em]"
              style={{ color: accent }}
            >
              Smart planning
            </div>
            <h2 className="mt-3 text-3xl font-black tracking-tight text-slate-950 sm:text-4xl">
              Know the numbers before you commit.
            </h2>
            <p className="mt-4 text-sm leading-7 text-slate-600">
              Use the calculator to explore an illustrative repayment scenario
              using the lender's published pricing.
            </p>
          </div>
          <div className="mt-10">
            <LoanCalculator
              currency={currency}
              primary={primary}
              accent={accent}
              products={services}
              defaultInterest={tenant.monthlyInterestRate}
              defaultManagementFee={tenant.monthlyManagementFeeRate}
              processingFee={tenant.processingFeeRate}
            />
          </div>
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-4 py-20">
        <div className="grid gap-5 md:grid-cols-3">
          {[
            [
              "01",
              "Transparent terms",
              "Published product information is presented clearly before you apply.",
            ],
            [
              "02",
              "Secure process",
              "Your application and repayment journey is connected to the lender's protected platform.",
            ],
            [
              "03",
              "Practical support",
              `Reach ${tenant.name} through the contact details published on this website.`,
            ],
          ].map(([number, title, text]) => (
            <div
              key={number}
              className="rounded-[1.5rem] border border-slate-200 bg-white p-7 shadow-sm"
            >
              <div className="text-xs font-black" style={{ color: accent }}>
                {number}
              </div>
              <h3 className="mt-3 text-lg font-black text-slate-950">
                {title}
              </h3>
              <p className="mt-2 text-sm leading-7 text-slate-600">{text}</p>
            </div>
          ))}
        </div>
      </section>

      {testimonials.length > 0 && (
        <section className="bg-slate-950 text-white">
          <div className="mx-auto max-w-7xl px-4 py-20 sm:py-24">
            <div className="text-center">
              <div
                className="text-[11px] font-black uppercase tracking-[0.2em]"
                style={{ color: accent }}
              >
                Client experience
              </div>
              <h2 className="mt-3 text-3xl font-black sm:text-4xl">
                Trusted by the people we serve.
              </h2>
            </div>
            <div className="mt-10 grid gap-5 md:grid-cols-3">
              {testimonials.slice(0, 3).map((item) => (
                <article
                  key={`${item.name}-${item.text.slice(0, 10)}`}
                  className="rounded-[1.5rem] border border-white/10 bg-white/5 p-6"
                >
                  <div
                    className="text-sm tracking-widest"
                    style={{ color: accent }}
                  >
                    {"★".repeat(Math.max(1, Math.min(5, item.rating || 5)))}
                  </div>
                  <p className="mt-4 text-sm leading-7 text-white/70">
                    “{item.text}”
                  </p>
                  <div className="mt-6 font-black">{item.name}</div>
                  {item.role && (
                    <div className="text-xs text-white/40">{item.role}</div>
                  )}
                </article>
              ))}
            </div>
          </div>
        </section>
      )}

      <section className="px-4 py-20">
        <div
          className="mx-auto max-w-5xl overflow-hidden rounded-[2rem] p-10 text-center text-white shadow-2xl sm:p-14"
          style={{ background: `linear-gradient(135deg, ${primary}, #071B35)` }}
        >
          <div className="mx-auto max-w-3xl">
            <div
              className="text-[11px] font-black uppercase tracking-[0.2em]"
              style={{ color: accent }}
            >
              Ready when you are
            </div>
            <h2 className="mt-3 text-3xl font-black sm:text-5xl">
              Move from planning to application with confidence.
            </h2>
            <p className="mt-4 text-sm leading-7 text-white/65">
              Start online or speak with {tenant.name} about the product that
              fits your needs.
            </p>
            <div className="mt-8 flex flex-wrap justify-center gap-3">
              <Link
                href="/apply"
                className="rounded-2xl px-7 py-3.5 text-sm font-black"
                style={{ backgroundColor: accent, color: primary }}
              >
                Apply online
              </Link>
              <Link
                href="/contact"
                className="rounded-2xl border border-white/25 px-7 py-3.5 text-sm font-bold text-white"
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
