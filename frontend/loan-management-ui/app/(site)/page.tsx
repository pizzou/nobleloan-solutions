"use client";

import Link from "next/link";
import PublicLoanCalculator from "../../components/PublicLoanCalculator";
import { useTenant } from "./layout";

function ArrowIcon({ className = "h-4 w-4" }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.9"
      className={className}
      aria-hidden="true"
    >
      <path d="M5 12h14" />
      <path d="m13 6 6 6-6 6" />
    </svg>
  );
}

function ShieldIcon({ className = "h-5 w-5" }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      className={className}
      aria-hidden="true"
    >
      <path d="M12 3 20 6v6c0 5-3.5 8.5-8 10-4.5-1.5-8-5-8-10V6l8-3Z" />
      <path d="m9 12 2 2 4-4" />
    </svg>
  );
}

function CheckIcon({ className = "h-4 w-4" }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.2"
      className={className}
      aria-hidden="true"
    >
      <path d="m5 12 4 4L19 6" />
    </svg>
  );
}

function CalculatorIcon({ className = "h-5 w-5" }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      className={className}
      aria-hidden="true"
    >
      <rect x="5" y="3" width="14" height="18" rx="2" />
      <path d="M8 7h8M8 11h2M14 11h2M8 15h2M14 15h2M8 18h8" />
    </svg>
  );
}

function formatAmount(
  currency: string,
  value: string | number | null | undefined,
) {
  if (value === null || value === undefined || value === "")
    return "No stated limit";
  const amount = Number(String(value).replace(/[^0-9.-]/g, ""));
  return Number.isFinite(amount)
    ? `${currency} ${amount.toLocaleString("en-RW", { maximumFractionDigits: 0 })}`
    : "No stated limit";
}

function formatRate(value: string | number | null | undefined) {
  if (value === null || value === undefined || value === "")
    return "Contact us";
  return String(value).includes("%") ? String(value) : `${value}%`;
}

export default function HomePage() {
  const tenant = useTenant();
  if (!tenant) return null;

  const primary = tenant.primaryColor || "#0F1B3D";
  const accent = tenant.accentColor || "#C9A227";
  const products = tenant.services || [];

  const trustPoints = [
    [
      "Transparent terms",
      "Rates, fees and repayment periods are presented before you apply.",
    ],
    [
      "Secure application",
      "Your application journey is designed around responsible handling of your information.",
    ],
    [
      "Human support",
      "Our lending team remains available throughout the application and repayment journey.",
    ],
  ];

  return (
    <main className="overflow-hidden bg-white text-slate-950">
      <section
        className="relative isolate overflow-hidden"
        style={{
          background: `linear-gradient(135deg,#06101E 0%,${primary} 55%,#1A2B54 100%)`,
        }}
      >
        <div
          className="pointer-events-none absolute inset-0 opacity-30"
          aria-hidden="true"
          style={{
            backgroundImage:
              "radial-gradient(circle at 12% 18%,rgba(255,255,255,.26),transparent 24%),radial-gradient(circle at 88% 8%,rgba(201,162,39,.25),transparent 22%),linear-gradient(rgba(255,255,255,.025) 1px,transparent 1px),linear-gradient(90deg,rgba(255,255,255,.025) 1px,transparent 1px)",
            backgroundSize: "auto,auto,42px 42px,42px 42px",
          }}
        />
        <div className="relative mx-auto grid max-w-7xl gap-12 px-5 py-14 sm:px-8 sm:py-20 lg:grid-cols-[1.03fr_.97fr] lg:items-center lg:gap-16 lg:py-24">
          <div className="text-white">
            <div className="inline-flex items-center gap-2 rounded-full border border-white/15 bg-white/[.07] px-4 py-2 text-[10px] font-black uppercase tracking-[.18em] text-white/80 backdrop-blur">
              <span
                className="h-2 w-2 rounded-full"
                style={{ backgroundColor: accent }}
              />
              {tenant.name}
            </div>
            <h1 className="mt-7 max-w-3xl text-4xl font-black leading-[1.02] tracking-[-.045em] sm:text-5xl lg:text-[4.45rem]">
              {tenant.hero?.headline ||
                tenant.tagline ||
                "Finance built around your next step."}
            </h1>
            <p className="mt-7 max-w-2xl text-base leading-7 text-white/70 sm:text-lg">
              {tenant.hero?.subtext ||
                tenant.mission ||
                "Clear, responsible lending with transparent terms and a straightforward digital application journey."}
            </p>
            <div className="mt-9 flex flex-col gap-3 sm:flex-row">
              <Link
                href="/apply"
                className="inline-flex min-h-12 items-center justify-center gap-2 rounded-xl px-6 py-3.5 text-sm font-black shadow-2xl transition hover:-translate-y-0.5 focus:outline-none focus:ring-2 focus:ring-white focus:ring-offset-2 focus:ring-offset-slate-950"
                style={{ backgroundColor: accent, color: "#111827" }}
              >
                Start an application <ArrowIcon />
              </Link>
              <Link
                href="/services"
                className="inline-flex min-h-12 items-center justify-center rounded-xl border border-white/15 bg-white/[.06] px-6 py-3.5 text-sm font-bold text-white backdrop-blur transition hover:bg-white/[.11] focus:outline-none focus:ring-2 focus:ring-white"
              >
                Explore loan products
              </Link>
            </div>
            <div className="mt-10 grid max-w-3xl gap-3 sm:grid-cols-3">
              {trustPoints.map(([title, text]) => (
                <div
                  key={title}
                  className="rounded-2xl border border-white/10 bg-white/[.055] p-4 backdrop-blur"
                >
                  <div className="flex items-center gap-2 text-xs font-black">
                    <span
                      className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg"
                      style={{ backgroundColor: `${accent}20`, color: accent }}
                    >
                      <ShieldIcon className="h-4 w-4" />
                    </span>
                    {title}
                  </div>
                  <div className="mt-3 text-[11px] leading-5 text-white/50">
                    {text}
                  </div>
                </div>
              ))}
            </div>
          </div>
          <div className="relative lg:pl-4">
            <div
              className="pointer-events-none absolute -inset-5 rounded-[40px] opacity-25 blur-3xl"
              style={{ backgroundColor: accent }}
              aria-hidden="true"
            />
            <div className="relative overflow-hidden rounded-[30px] border border-white/10 bg-white p-2 shadow-[0_30px_90px_rgba(0,0,0,.30)]">
              <div className="rounded-[23px] bg-slate-50 p-1">
                <PublicLoanCalculator
                  products={products}
                  currency={tenant.currency}
                  primary={primary}
                  accent={accent}
                />
              </div>
            </div>
            <div className="relative mt-4 flex items-center justify-center gap-2 text-[10px] font-bold uppercase tracking-[.12em] text-white/40">
              <ShieldIcon className="h-3.5 w-3.5" />
              Indicative calculation · subject to assessment and approval
            </div>
          </div>
        </div>
      </section>

      {tenant.stats?.length ? (
        <section className="border-b border-slate-200 bg-slate-50">
          <div className="mx-auto grid max-w-7xl grid-cols-2 divide-x divide-y divide-slate-200 sm:grid-cols-4 sm:divide-y-0">
            {tenant.stats.slice(0, 4).map((stat) => (
              <div key={stat.label} className="px-5 py-7 text-center sm:py-8">
                <div
                  className="text-2xl font-black tracking-tight sm:text-3xl"
                  style={{ color: primary }}
                >
                  {stat.value}
                </div>
                <div className="mt-1.5 text-[10px] font-black uppercase tracking-[.12em] text-slate-400">
                  {stat.label}
                </div>
              </div>
            ))}
          </div>
        </section>
      ) : null}

      <section
        id="loan-products"
        className="mx-auto max-w-7xl px-5 py-20 sm:px-8 lg:py-28"
      >
        <div className="flex flex-col justify-between gap-7 lg:flex-row lg:items-end">
          <div className="max-w-2xl">
            <div
              className="text-[10px] font-black uppercase tracking-[.2em]"
              style={{ color: accent }}
            >
              Lending solutions
            </div>
            <h2 className="mt-3 text-3xl font-black tracking-[-.035em] sm:text-5xl">
              Financing designed around real needs.
            </h2>
            <p className="mt-5 text-base leading-7 text-slate-500">
              Explore the active lending products configured by {tenant.name}.
              Product information shown here is sourced from the lending
              configuration rather than invented by the public website.
            </p>
          </div>
          <Link
            href="/services"
            className="inline-flex w-fit items-center gap-2 rounded-xl border border-slate-200 bg-white px-5 py-3 text-sm font-black shadow-sm transition hover:border-slate-300 hover:shadow-md focus:outline-none focus:ring-2 focus:ring-slate-300"
            style={{ color: primary }}
          >
            View all products <ArrowIcon />
          </Link>
        </div>

        {products.length > 0 ? (
          <div className="mt-12 grid gap-5 md:grid-cols-2 lg:grid-cols-3">
            {products.map((service, index) => (
              <article
                key={`${service.title}-${index}`}
                className="group relative overflow-hidden rounded-3xl border border-slate-200 bg-white p-7 shadow-[0_12px_45px_rgba(15,23,42,.045)] transition duration-300 hover:-translate-y-1 hover:border-slate-300 hover:shadow-[0_24px_70px_rgba(15,23,42,.10)]"
              >
                <div
                  className="pointer-events-none absolute right-0 top-0 h-28 w-28 rounded-full opacity-60 blur-3xl"
                  style={{ backgroundColor: `${accent}18` }}
                  aria-hidden="true"
                />
                <div className="relative flex items-start justify-between gap-4">
                  <div
                    className="flex h-12 w-12 items-center justify-center rounded-2xl text-2xl"
                    style={{ backgroundColor: `${primary}0D`, color: primary }}
                    aria-hidden="true"
                  >
                    {service.icon || "•"}
                  </div>
                  <span
                    className="rounded-full border px-3 py-1 text-[9px] font-black uppercase tracking-[.13em]"
                    style={{
                      backgroundColor: `${accent}12`,
                      borderColor: `${accent}35`,
                      color: primary,
                    }}
                  >
                    Available
                  </span>
                </div>
                <h3 className="relative mt-7 text-xl font-black tracking-tight">
                  {service.title}
                </h3>
                <p className="relative mt-3 min-h-[72px] text-sm leading-6 text-slate-500">
                  {service.description}
                </p>
                <div className="relative mt-6 grid grid-cols-2 gap-3 border-y border-slate-100 py-5">
                  <div>
                    <div className="text-[9px] font-black uppercase tracking-[.12em] text-slate-400">
                      Interest rate
                    </div>
                    <div className="mt-1.5 text-sm font-black text-slate-900">
                      {formatRate(service.interestRate ?? service.rate)}
                      {service.rateType
                        ? ` / ${String(service.rateType).toLowerCase()}`
                        : ""}
                    </div>
                  </div>
                  <div>
                    <div className="text-[9px] font-black uppercase tracking-[.12em] text-slate-400">
                      Term
                    </div>
                    <div className="mt-1.5 text-sm font-black text-slate-900">
                      {service.term || "Contact us"}
                    </div>
                  </div>
                </div>
                <div className="relative mt-4 flex items-baseline justify-between gap-4">
                  <span className="text-xs text-slate-400">
                    Maximum financing
                  </span>
                  <span className="text-sm font-black text-slate-700">
                    {formatAmount(tenant.currency, service.maxAmount)}
                  </span>
                </div>
                <Link
                  href={`/apply?type=${encodeURIComponent(service.title)}`}
                  className="relative mt-6 inline-flex min-h-11 w-full items-center justify-center gap-2 rounded-xl px-4 py-3 text-sm font-black transition hover:-translate-y-0.5 focus:outline-none focus:ring-2 focus:ring-offset-2"
                  style={{
                    backgroundColor: primary,
                    color: "#fff",
                    outlineColor: primary,
                  }}
                >
                  Apply for {service.title} <ArrowIcon />
                </Link>
              </article>
            ))}
          </div>
        ) : (
          <div className="mt-12 rounded-3xl border border-dashed border-slate-300 bg-slate-50 px-6 py-12 text-center">
            <div
              className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl"
              style={{ backgroundColor: `${primary}0D`, color: primary }}
            >
              <CalculatorIcon />
            </div>
            <h3 className="mt-4 text-lg font-black text-slate-900">
              Lending products are being updated
            </h3>
            <p className="mx-auto mt-2 max-w-md text-sm leading-6 text-slate-500">
              Please contact our lending team for currently available financing
              options.
            </p>
            <Link
              href="/contact"
              className="mt-5 inline-flex items-center gap-2 rounded-xl px-5 py-3 text-sm font-black text-white"
              style={{ backgroundColor: primary }}
            >
              Contact us <ArrowIcon />
            </Link>
          </div>
        )}
      </section>

      <section className="bg-slate-950 text-white">
        <div className="mx-auto grid max-w-7xl gap-12 px-5 py-20 sm:px-8 lg:grid-cols-2 lg:items-center lg:py-24">
          <div>
            <div
              className="text-[10px] font-black uppercase tracking-[.2em]"
              style={{ color: accent }}
            >
              Responsible lending
            </div>
            <h2 className="mt-4 text-3xl font-black tracking-[-.035em] sm:text-5xl">
              A professional journey from application to repayment.
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
                  className="flex gap-3 rounded-2xl border border-white/10 bg-white/[.05] p-4"
                >
                  <span
                    className="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-lg"
                    style={{ backgroundColor: `${accent}16`, color: accent }}
                  >
                    <CheckIcon />
                  </span>
                  <span className="text-sm font-semibold leading-6 text-white/80">
                    {item}
                  </span>
                </div>
              ))}
            </div>
          </div>
          <div className="rounded-[32px] border border-white/10 bg-white/[.06] p-8 shadow-2xl sm:p-10">
            <div className="flex items-center gap-3">
              <span
                className="flex h-10 w-10 items-center justify-center rounded-xl"
                style={{ backgroundColor: `${accent}18`, color: accent }}
              >
                <ShieldIcon />
              </span>
              <div className="text-[10px] font-black uppercase tracking-[.18em] text-white/40">
                Our commitment
              </div>
            </div>
            <div className="mt-5 text-2xl font-black leading-tight">
              {tenant.tagline ||
                "A trusted partner for responsible financial support."}
            </div>
            <p className="mt-5 text-sm leading-7 text-white/55">
              {tenant.vision ||
                "Building long-term trust through fair, transparent and responsible lending."}
            </p>
            <div className="mt-8 flex flex-col gap-3 sm:flex-row">
              <Link
                href="/about"
                className="inline-flex min-h-11 items-center justify-center rounded-xl border border-white/15 px-5 py-3 text-sm font-bold transition hover:bg-white/10 focus:outline-none focus:ring-2 focus:ring-white"
              >
                About us
              </Link>
              <Link
                href="/contact"
                className="inline-flex min-h-11 items-center justify-center rounded-xl px-5 py-3 text-sm font-black transition hover:-translate-y-0.5 focus:outline-none focus:ring-2 focus:ring-white"
                style={{ backgroundColor: accent, color: "#111827" }}
              >
                Talk to our team
              </Link>
            </div>
          </div>
        </div>
      </section>

      {tenant.testimonials?.length ? (
        <section className="mx-auto max-w-7xl px-5 py-20 sm:px-8 lg:py-28">
          <div className="mx-auto max-w-2xl text-center">
            <div
              className="text-[10px] font-black uppercase tracking-[.2em]"
              style={{ color: accent }}
            >
              Client confidence
            </div>
            <h2 className="mt-3 text-3xl font-black tracking-[-.035em] sm:text-5xl">
              Built for long-term relationships.
            </h2>
          </div>
          <div className="mt-12 grid gap-5 md:grid-cols-3">
            {tenant.testimonials.slice(0, 3).map((t, index) => (
              <article
                key={`${t.name}-${index}`}
                className="rounded-3xl border border-slate-200 bg-white p-7 shadow-[0_10px_40px_rgba(15,23,42,.04)]"
              >
                <div
                  className="text-base tracking-[.2em]"
                  aria-label="5 out of 5 stars"
                  style={{ color: accent }}
                >
                  ★★★★★
                </div>
                <p className="mt-5 text-sm leading-7 text-slate-600">
                  &ldquo;{t.text}&rdquo;
                </p>
                <div className="mt-7 border-t border-slate-100 pt-5">
                  <div className="text-sm font-black text-slate-900">
                    {t.name}
                  </div>
                  <div className="mt-1 text-xs text-slate-400">{t.role}</div>
                </div>
              </article>
            ))}
          </div>
        </section>
      ) : null}

      <section className="px-5 pb-20 sm:px-8 lg:pb-28">
        <div
          className="relative mx-auto max-w-7xl overflow-hidden rounded-[32px] px-6 py-12 text-center sm:px-10 lg:py-16"
          style={{ background: `linear-gradient(135deg,${primary},#07111F)` }}
        >
          <div
            className="pointer-events-none absolute -right-24 -top-24 h-72 w-72 rounded-full opacity-20 blur-3xl"
            style={{ backgroundColor: accent }}
            aria-hidden="true"
          />
          <div className="relative mx-auto max-w-2xl text-white">
            <div
              className="text-[10px] font-black uppercase tracking-[.2em]"
              style={{ color: accent }}
            >
              Ready when you are
            </div>
            <h2 className="mt-4 text-3xl font-black tracking-[-.035em] sm:text-5xl">
              Take the next step with confidence.
            </h2>
            <p className="mt-5 text-sm leading-7 text-white/60">
              Review available products, estimate your repayment and submit your
              application securely online.
            </p>
            <div className="mt-8 flex flex-col justify-center gap-3 sm:flex-row">
              <Link
                href="/apply"
                className="inline-flex min-h-12 items-center justify-center rounded-xl px-6 py-3.5 text-sm font-black transition hover:-translate-y-0.5 focus:outline-none focus:ring-2 focus:ring-white"
                style={{ backgroundColor: accent, color: "#111827" }}
              >
                Apply now{" "}
                <span className="ml-2">
                  <ArrowIcon />
                </span>
              </Link>
              <Link
                href="/track"
                className="inline-flex min-h-12 items-center justify-center rounded-xl border border-white/15 px-6 py-3.5 text-sm font-bold text-white transition hover:bg-white/10 focus:outline-none focus:ring-2 focus:ring-white"
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
