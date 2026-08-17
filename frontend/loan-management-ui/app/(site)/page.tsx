"use client";

import Link from "next/link";
import PublicLoanCalculator from "../../components/PublicLoanCalculator";
import { useTenant } from "./layout";

function ArrowIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      className="h-4 w-4"
    >
      <path d="M5 12h14" />
      <path d="m13 6 6 6-6 6" />
    </svg>
  );
}

function ShieldIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      className="h-5 w-5"
    >
      <path d="M12 3 20 6v6c0 5-3.5 8.5-8 10-4.5-1.5-8-5-8-10V6l8-3Z" />
      <path d="m9 12 2 2 4-4" />
    </svg>
  );
}

function formatAmount(
  currency: string,
  value: string | number | null | undefined,
) {
  if (value === null || value === undefined || value === "") return "Unlimited";
  const amount = Number(String(value).replace(/[^0-9.-]/g, ""));
  return Number.isFinite(amount)
    ? `${currency} ${amount.toLocaleString("en-RW", { maximumFractionDigits: 0 })}`
    : "Unlimited";
}

export default function HomePage() {
  const tenant = useTenant();
  if (!tenant) return null;

  const primary = tenant.primaryColor || "#0F1B3D";
  const accent = tenant.accentColor || "#C9A227";
  const products = tenant.services || [];

  return (
    <main className="overflow-hidden bg-white text-slate-950">
      <section
        className="relative isolate overflow-hidden"
        style={{
          background: `linear-gradient(135deg,#07111F 0%,${primary} 58%,#16264D 100%)`,
        }}
      >
        <div
          className="absolute inset-0 opacity-20"
          style={{
            backgroundImage:
              "radial-gradient(circle at 15% 20%,rgba(255,255,255,.35),transparent 26%),radial-gradient(circle at 85% 10%,rgba(201,162,39,.30),transparent 24%)",
          }}
        />
        <div className="relative mx-auto grid max-w-7xl gap-12 px-5 py-16 sm:px-8 lg:grid-cols-[1.08fr_.92fr] lg:items-center lg:py-24">
          <div className="text-white">
            <div className="inline-flex items-center gap-2 rounded-full border border-white/15 bg-white/5 px-4 py-2 text-[11px] font-black uppercase tracking-[.18em] text-white/75">
              <span
                className="h-2 w-2 rounded-full"
                style={{ backgroundColor: accent }}
              />
              {tenant.name}
            </div>
            <h1 className="mt-7 max-w-3xl text-4xl font-black leading-[1.03] tracking-[-.04em] sm:text-5xl lg:text-7xl">
              {tenant.hero?.headline ||
                tenant.tagline ||
                "Your trusted lending partner"}
            </h1>
            <p className="mt-7 max-w-2xl text-base leading-7 text-white/70 sm:text-lg">
              {tenant.hero?.subtext ||
                tenant.mission ||
                "Clear, responsible lending built around your needs."}
            </p>
            <div className="mt-9 flex flex-wrap gap-3">
              <Link
                href="/apply"
                className="inline-flex items-center gap-2 rounded-xl px-6 py-3.5 text-sm font-black shadow-2xl"
                style={{ backgroundColor: accent, color: "#111827" }}
              >
                Start an application <ArrowIcon />
              </Link>
              <Link
                href="/services"
                className="rounded-xl border border-white/20 bg-white/5 px-6 py-3.5 text-sm font-bold text-white"
              >
                Explore loan products
              </Link>
            </div>
            <div className="mt-10 grid max-w-2xl gap-3 sm:grid-cols-3">
              {[
                ["Clear terms", "Published rates, fees and repayment periods"],
                [
                  "Secure journey",
                  "Protected online application and documents",
                ],
                [
                  "Human support",
                  "A lending team available throughout the journey",
                ],
              ].map(([title, text]) => (
                <div
                  key={title}
                  className="rounded-2xl border border-white/10 bg-white/5 p-4"
                >
                  <div className="flex items-center gap-2 text-xs font-black">
                    <ShieldIcon />
                    {title}
                  </div>
                  <div className="mt-2 text-[11px] leading-5 text-white/45">
                    {text}
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="relative">
            <div
              className="absolute -inset-4 rounded-[36px] opacity-30 blur-2xl"
              style={{ backgroundColor: accent }}
            />
            <div className="relative rounded-[30px] border border-white/10 bg-white p-2 shadow-2xl">
              <PublicLoanCalculator
                products={products}
                currency={tenant.currency}
                primary={primary}
                accent={accent}
              />
            </div>
          </div>
        </div>
      </section>

      {tenant.stats?.length ? (
        <section className="border-b border-slate-200 bg-slate-50">
          <div className="mx-auto grid max-w-7xl grid-cols-2 divide-x divide-slate-200 px-5 sm:grid-cols-4 sm:px-8">
            {tenant.stats.slice(0, 4).map((stat) => (
              <div key={stat.label} className="px-5 py-8 text-center">
                <div className="text-2xl font-black" style={{ color: primary }}>
                  {stat.value}
                </div>
                <div className="mt-1 text-[10px] font-black uppercase tracking-[.12em] text-slate-400">
                  {stat.label}
                </div>
              </div>
            ))}
          </div>
        </section>
      ) : null}

      <section className="mx-auto max-w-7xl px-5 py-20 sm:px-8 lg:py-28">
        <div className="flex flex-col justify-between gap-6 lg:flex-row lg:items-end">
          <div className="max-w-2xl">
            <div
              className="text-[11px] font-black uppercase tracking-[.2em]"
              style={{ color: accent }}
            >
              Noble loan portfolio
            </div>
            <h2 className="mt-3 text-3xl font-black tracking-[-.03em] sm:text-5xl">
              Financing built around real needs.
            </h2>
            <p className="mt-5 text-base leading-7 text-slate-500">
              Our public products are loaded directly from Noble Loan
              Solutions&apos; active lending configuration. The website does not
              invent rates or maximum limits.
            </p>
          </div>
          <Link
            href="/services"
            className="inline-flex items-center gap-2 rounded-xl border border-slate-200 px-5 py-3 text-sm font-black"
            style={{ color: primary }}
          >
            View all products <ArrowIcon />
          </Link>
        </div>

        <div className="mt-12 grid gap-5 md:grid-cols-2 lg:grid-cols-3">
          {products.map((service) => (
            <article
              key={service.title}
              className="group rounded-3xl border border-slate-200 bg-white p-7 shadow-[0_12px_45px_rgba(15,23,42,.05)] transition hover:-translate-y-1 hover:shadow-[0_24px_70px_rgba(15,23,42,.10)]"
            >
              <div className="flex items-start justify-between gap-4">
                <div
                  className="flex h-12 w-12 items-center justify-center rounded-2xl text-2xl"
                  style={{ backgroundColor: `${primary}10` }}
                >
                  {service.icon}
                </div>
                <span
                  className="rounded-full px-3 py-1 text-[10px] font-black uppercase tracking-wider"
                  style={{ backgroundColor: `${accent}22`, color: primary }}
                >
                  Active
                </span>
              </div>
              <h3 className="mt-7 text-xl font-black tracking-tight">
                {service.title}
              </h3>
              <p className="mt-3 min-h-[72px] text-sm leading-6 text-slate-500">
                {service.description}
              </p>
              <div className="mt-6 grid grid-cols-2 gap-3 border-y border-slate-100 py-5">
                <div>
                  <div className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
                    Rate
                  </div>
                  <div className="mt-1 text-sm font-black">
                    {service.interestRate ?? service.rate}
                    {service.rateType
                      ? ` / ${String(service.rateType).toLowerCase()}`
                      : ""}
                  </div>
                </div>
                <div>
                  <div className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
                    Term
                  </div>
                  <div className="mt-1 text-sm font-black">{service.term}</div>
                </div>
              </div>
              <div className="mt-4 text-xs text-slate-400">
                Maximum:{" "}
                <span className="font-bold text-slate-600">
                  {formatAmount(tenant.currency, service.maxAmount)}
                </span>
              </div>
              <Link
                href={`/apply?type=${encodeURIComponent(service.title)}`}
                className="mt-6 inline-flex w-full items-center justify-center gap-2 rounded-xl py-3 text-sm font-black"
                style={{ backgroundColor: primary, color: "#fff" }}
              >
                Apply for {service.title} <ArrowIcon />
              </Link>
            </article>
          ))}
        </div>
      </section>

      <section className="bg-slate-950 text-white">
        <div className="mx-auto grid max-w-7xl gap-12 px-5 py-20 sm:px-8 lg:grid-cols-2 lg:items-center lg:py-24">
          <div>
            <div
              className="text-[11px] font-black uppercase tracking-[.2em]"
              style={{ color: accent }}
            >
              Responsible lending
            </div>
            <h2 className="mt-4 text-3xl font-black tracking-[-.03em] sm:text-5xl">
              A professional lending journey from application to repayment.
            </h2>
            <p className="mt-6 max-w-xl text-base leading-7 text-white/60">
              {tenant.mission ||
                tenant.tagline ||
                "Transparent lending designed to build long-term financial relationships."}
            </p>
            <div className="mt-8 grid gap-4 sm:grid-cols-2">
              {[
                "Clear product pricing and terms",
                "Secure digital application journey",
                "Credit-led assessment process",
                "Support through repayment",
              ].map((item) => (
                <div
                  key={item}
                  className="flex gap-3 rounded-2xl border border-white/10 bg-white/5 p-4"
                >
                  <span style={{ color: accent }}>
                    <ShieldIcon />
                  </span>
                  <span className="text-sm font-semibold text-white/80">
                    {item}
                  </span>
                </div>
              ))}
            </div>
          </div>
          <div className="rounded-[32px] border border-white/10 bg-white/[.06] p-8 sm:p-10">
            <div className="text-[11px] font-black uppercase tracking-[.18em] text-white/40">
              Our promise
            </div>
            <div className="mt-5 text-2xl font-black leading-tight">
              {tenant.tagline || "Your trusted partner in financial support."}
            </div>
            <p className="mt-5 text-sm leading-7 text-white/55">
              {tenant.vision ||
                "Building long-term trust through fair, transparent and responsible lending."}
            </p>
            <div className="mt-8 flex flex-wrap gap-3">
              <Link
                href="/about"
                className="rounded-xl border border-white/15 px-5 py-3 text-sm font-bold"
              >
                About us
              </Link>
              <Link
                href="/contact"
                className="rounded-xl px-5 py-3 text-sm font-black"
                style={{ backgroundColor: accent, color: "#111827" }}
              >
                Talk to us
              </Link>
            </div>
          </div>
        </div>
      </section>

      {tenant.testimonials?.length ? (
        <section className="mx-auto max-w-7xl px-5 py-20 sm:px-8 lg:py-28">
          <div className="text-center">
            <div
              className="text-[11px] font-black uppercase tracking-[.2em]"
              style={{ color: accent }}
            >
              Client confidence
            </div>
            <h2 className="mt-3 text-3xl font-black sm:text-5xl">
              Built for long-term relationships.
            </h2>
          </div>
          <div className="mt-12 grid gap-5 md:grid-cols-3">
            {tenant.testimonials.slice(0, 3).map((t) => (
              <article
                key={t.name}
                className="rounded-3xl border border-slate-200 bg-white p-7 shadow-sm"
              >
                <div
                  className="text-lg tracking-[.2em]"
                  style={{ color: accent }}
                >
                  ★★★★★
                </div>
                <p className="mt-5 text-sm leading-7 text-slate-600">
                  &ldquo;{t.text}&rdquo;
                </p>
                <div className="mt-7 border-t border-slate-100 pt-5">
                  <div className="text-sm font-black">{t.name}</div>
                  <div className="mt-1 text-xs text-slate-400">{t.role}</div>
                </div>
              </article>
            ))}
          </div>
        </section>
      ) : null}

      <section className="px-5 pb-20 sm:px-8 lg:pb-28">
        <div
          className="mx-auto max-w-7xl rounded-[32px] px-6 py-12 text-center sm:px-10 lg:py-16"
          style={{ background: `linear-gradient(135deg,${primary},#07111F)` }}
        >
          <div className="mx-auto max-w-2xl text-white">
            <div
              className="text-[11px] font-black uppercase tracking-[.2em]"
              style={{ color: accent }}
            >
              Ready when you are
            </div>
            <h2 className="mt-4 text-3xl font-black sm:text-5xl">
              Take the next step with confidence.
            </h2>
            <p className="mt-5 text-sm leading-7 text-white/60">
              Review Noble&apos;s available products, estimate your repayment
              and submit your application securely online.
            </p>
            <div className="mt-8 flex flex-wrap justify-center gap-3">
              <Link
                href="/apply"
                className="rounded-xl px-6 py-3.5 text-sm font-black"
                style={{ backgroundColor: accent, color: "#111827" }}
              >
                Apply now
              </Link>
              <Link
                href="/track"
                className="rounded-xl border border-white/15 px-6 py-3.5 text-sm font-bold text-white"
              >
                Track an application
              </Link>
            </div>
          </div>
        </div>
      </section>
    </main>
  );
}
