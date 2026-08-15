"use client";

import Link from "next/link";
import { useTenant } from "./layout";

function Icon({ children }: { children: React.ReactNode }) {
  return (
    <span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-white/10 text-xl">
      {children}
    </span>
  );
}

export default function HomePage() {
  const tenant = useTenant();
  if (!tenant) return null;

  const primary = tenant.primaryColor;
  const accent = tenant.accentColor;
  const country = tenant.country || "your market";
  const services = tenant.services ?? [];
  const stats = tenant.stats ?? [];
  const testimonials = tenant.testimonials ?? [];

  return (
    <div>
      <section className="relative overflow-hidden border-b border-slate-100 bg-white">
        <div
          className="absolute inset-0 opacity-[0.035]"
          style={{
            backgroundImage: `radial-gradient(circle at 2px 2px, ${primary} 1px, transparent 0)`,
            backgroundSize: "28px 28px",
          }}
        />
        <div className="relative mx-auto grid max-w-7xl gap-12 px-4 py-20 md:grid-cols-2 md:py-28">
          <div className="flex flex-col justify-center">
            <div
              className="mb-6 inline-flex w-fit items-center rounded-full border px-3.5 py-1.5 text-xs font-bold uppercase tracking-wide"
              style={{
                borderColor: `${accent}80`,
                color: primary,
                backgroundColor: `${accent}14`,
              }}
            >
              Official website • {country}
            </div>
            <h1 className="text-4xl font-black leading-[1.1] text-slate-950 md:text-6xl">
              {tenant.hero?.headline ||
                tenant.tagline ||
                `Financial solutions from ${tenant.name}`}
            </h1>
            <p className="mt-6 max-w-xl text-lg leading-8 text-slate-600">
              {tenant.hero?.subtext ||
                tenant.mission ||
                `Explore the financial products offered by ${tenant.name}.`}
            </p>
            <div className="mt-8 flex flex-wrap gap-3">
              <Link
                href="/apply"
                className="rounded-full px-7 py-3.5 text-sm font-bold text-white shadow-lg hover:opacity-95"
                style={{ backgroundColor: primary }}
              >
                Apply for a Loan →
              </Link>
              <Link
                href="/services"
                className="rounded-full border-2 px-7 py-3.5 text-sm font-bold"
                style={{ borderColor: primary, color: primary }}
              >
                View Services
              </Link>
            </div>
            <div className="mt-8 flex flex-wrap gap-x-7 gap-y-3 text-sm font-semibold text-slate-600">
              <span>Clear loan terms</span>
              <span>Secure application process</span>
              <span>Online application</span>
            </div>
          </div>

          <div
            className="rounded-3xl p-8 text-white shadow-2xl"
            style={{
              background: `linear-gradient(145deg, ${primary}, ${primary}CC)`,
            }}
          >
            <div className="text-xs font-bold uppercase tracking-[0.2em] text-white/60">
              {tenant.name}
            </div>
            <div className="mt-2 text-2xl font-black">
              Loan solutions designed around real needs
            </div>
            <div className="mt-6 space-y-4">
              {[
                ["Products", `${services.length || 0} active options`],
                [
                  "Monthly interest",
                  tenant.monthlyInterestRate != null
                    ? `${tenant.monthlyInterestRate}%`
                    : "Published per product",
                ],
                [
                  "Management fee",
                  tenant.monthlyManagementFeeRate != null
                    ? `${tenant.monthlyManagementFeeRate}% / month`
                    : "Published by lender",
                ],
                [
                  "Processing fee",
                  tenant.processingFeeRate != null
                    ? `${tenant.processingFeeRate}%`
                    : "As disclosed at application",
                ],
              ].map(([label, value]) => (
                <div
                  key={label}
                  className="flex items-center justify-between border-b border-white/10 py-3 last:border-0"
                >
                  <span className="text-sm text-white/60">{label}</span>
                  <span className="text-sm font-bold text-white">{value}</span>
                </div>
              ))}
            </div>
            <Link
              href="/apply"
              className="mt-6 block rounded-full py-3 text-center text-sm font-black"
              style={{ backgroundColor: accent, color: primary }}
            >
              Start an Application →
            </Link>
          </div>
        </div>
      </section>

      {stats.length > 0 && (
        <section className="border-b border-slate-100 bg-slate-50">
          <div className="mx-auto grid max-w-7xl grid-cols-2 gap-px px-4 py-8 md:grid-cols-4">
            {stats.map((stat) => (
              <div
                key={`${stat.label}-${stat.value}`}
                className="bg-slate-50 px-5 py-4 text-center"
              >
                <div className="text-2xl">{stat.icon || "•"}</div>
                <div className="mt-1 text-2xl font-black text-slate-900">
                  {stat.value}
                </div>
                <div className="text-xs font-semibold uppercase tracking-wider text-slate-500">
                  {stat.label}
                </div>
              </div>
            ))}
          </div>
        </section>
      )}

      <section className="mx-auto max-w-7xl px-4 py-20">
        <div className="max-w-2xl">
          <div
            className="text-xs font-black uppercase tracking-[0.18em]"
            style={{ color: accent }}
          >
            What we offer
          </div>
          <h2 className="mt-2 text-3xl font-black text-slate-950 md:text-4xl">
            Financial products published by {tenant.name}
          </h2>
          <p className="mt-4 text-slate-600">
            Review the products, rates, limits, and terms before you apply.
          </p>
        </div>
        {services.length > 0 ? (
          <div className="mt-10 grid gap-6 md:grid-cols-2 lg:grid-cols-3">
            {services.slice(0, 6).map((service) => (
              <div
                key={service.title}
                className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm hover:-translate-y-0.5 hover:shadow-lg"
              >
                <div className="text-3xl">{service.icon || "💼"}</div>
                <h3 className="mt-4 text-lg font-black text-slate-900">
                  {service.title}
                </h3>
                <p className="mt-2 min-h-12 text-sm leading-6 text-slate-600">
                  {service.description ||
                    "Loan product information is available through the application process."}
                </p>
                <div className="mt-5 grid grid-cols-2 gap-3">
                  <div className="rounded-xl bg-slate-50 p-3">
                    <div className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
                      Rate
                    </div>
                    <div
                      className="mt-1 text-sm font-black"
                      style={{ color: primary }}
                    >
                      {service.rate || "See terms"}
                    </div>
                  </div>
                  <div className="rounded-xl bg-slate-50 p-3">
                    <div className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
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
                  className="mt-5 inline-flex text-sm font-black hover:underline"
                  style={{ color: primary }}
                >
                  Apply for this product →
                </Link>
              </div>
            ))}
          </div>
        ) : (
          <div className="mt-10 rounded-2xl border border-slate-200 bg-slate-50 p-8 text-center text-sm text-slate-500">
            Loan products are currently being configured. Please contact{" "}
            {tenant.name} for current availability.
          </div>
        )}
      </section>

      <section className="bg-slate-50">
        <div className="mx-auto max-w-7xl px-4 py-20">
          <div className="text-center">
            <div
              className="text-xs font-black uppercase tracking-[0.18em]"
              style={{ color: accent }}
            >
              Why choose us
            </div>
            <h2 className="mt-2 text-3xl font-black text-slate-950">
              A clearer way to borrow
            </h2>
          </div>
          <div className="mt-10 grid gap-5 md:grid-cols-3">
            <div className="rounded-2xl border border-slate-200 bg-white p-6">
              <Icon>🔎</Icon>
              <h3 className="mt-4 font-black">Know the terms</h3>
              <p className="mt-2 text-sm leading-6 text-slate-600">
                Rates, fees, repayment expectations, and application
                requirements are presented before submission.
              </p>
            </div>
            <div className="rounded-2xl border border-slate-200 bg-white p-6">
              <Icon>🔐</Icon>
              <h3 className="mt-4 font-black">Protect your information</h3>
              <p className="mt-2 text-sm leading-6 text-slate-600">
                The public application flow is connected to the lender's
                authenticated loan platform.
              </p>
            </div>
            <div className="rounded-2xl border border-slate-200 bg-white p-6">
              <Icon>🤝</Icon>
              <h3 className="mt-4 font-black">Get support</h3>
              <p className="mt-2 text-sm leading-6 text-slate-600">
                Use the contact details published by {tenant.name} when you need
                help with an application or repayment.
              </p>
            </div>
          </div>
        </div>
      </section>

      {testimonials.length > 0 && (
        <section className="mx-auto max-w-7xl px-4 py-20">
          <div className="text-center">
            <div
              className="text-xs font-black uppercase tracking-[0.18em]"
              style={{ color: accent }}
            >
              Client feedback
            </div>
            <h2 className="mt-2 text-3xl font-black text-slate-950">
              What clients say
            </h2>
          </div>
          <div className="mt-10 grid gap-5 md:grid-cols-3">
            {testimonials.slice(0, 6).map((item) => (
              <article
                key={`${item.name}-${item.text.slice(0, 12)}`}
                className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm"
              >
                <div
                  className="text-sm tracking-wide"
                  style={{ color: accent }}
                >
                  {"★".repeat(Math.max(1, Math.min(5, item.rating || 5)))}
                </div>
                <p className="mt-3 text-sm leading-7 text-slate-600">
                  “{item.text}”
                </p>
                <div className="mt-5 font-black text-slate-900">
                  {item.name}
                </div>
                {item.role && (
                  <div className="text-xs text-slate-400">{item.role}</div>
                )}
              </article>
            ))}
          </div>
        </section>
      )}

      <section className="px-4 pb-20">
        <div
          className="mx-auto max-w-4xl rounded-3xl p-10 text-center text-white shadow-2xl"
          style={{ backgroundColor: primary }}
        >
          <h2 className="text-3xl font-black md:text-4xl">
            Ready to explore your options?
          </h2>
          <p className="mx-auto mt-3 max-w-2xl text-white/70">
            Start an application or contact {tenant.name} for product-specific
            guidance.
          </p>
          <div className="mt-7 flex flex-wrap justify-center gap-3">
            <Link
              href="/apply"
              className="rounded-full px-7 py-3.5 text-sm font-black"
              style={{ backgroundColor: accent, color: primary }}
            >
              Apply online
            </Link>
            <Link
              href="/contact"
              className="rounded-full border border-white/30 px-7 py-3.5 text-sm font-bold text-white"
            >
              Contact us
            </Link>
          </div>
        </div>
      </section>
    </div>
  );
}
