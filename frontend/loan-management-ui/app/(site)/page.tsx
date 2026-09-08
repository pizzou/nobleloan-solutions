"use client";

import Link from "next/link";
import PublicLoanCalculator from "../../components/PublicLoanCalculator";
import { useTenant } from "./layout";

function Arrow({ className = "h-4 w-4" }: { className?: string }) {
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

function Shield({ className = "h-4 w-4" }: { className?: string }) {
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

function Check() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.3"
      className="h-4 w-4"
      aria-hidden="true"
    >
      <path d="m5 12 4 4L19 6" />
    </svg>
  );
}

function formatAmount(
  currency: string,
  value: string | number | null | undefined,
) {
  if (value == null || value === "") return "No stated limit";
  const amount = Number(String(value).replace(/[^0-9.-]/g, ""));
  return Number.isFinite(amount)
    ? `${currency} ${amount.toLocaleString("en-RW", { maximumFractionDigits: 0 })}`
    : "No stated limit";
}

function formatRate(value: string | number | null | undefined) {
  if (value == null || value === "") return "Contact us";
  return String(value).includes("%") ? String(value) : `${value}%`;
}

export default function HomePage() {
  const tenant = useTenant();
  if (!tenant) return null;

  const primary = tenant.primaryColor || "#0B1F3A";
  const accent = tenant.accentColor || "#D4AF37";
  const products = tenant.services || [];

  return (
    <main className="overflow-hidden bg-white text-slate-950">
      {/* ==========================================================
          HERO — ABOVE THE FOLD
         ========================================================== */}
      <section
        className="relative isolate overflow-hidden text-white"
        style={{
          background: `linear-gradient(118deg, #061326 0%, ${primary} 55%, #102B50 100%)`,
        }}
      >
        <div
          className="pointer-events-none absolute inset-0"
          aria-hidden="true"
          style={{
            backgroundImage: `radial-gradient(circle at 9% 18%, ${accent}20 0, transparent 26%), radial-gradient(circle at 83% 9%, ${accent}18 0, transparent 23%), linear-gradient(rgba(255,255,255,.045) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,.045) 1px, transparent 1px)`,
            backgroundSize: "auto, auto, 56px 56px, 56px 56px",
          }}
        />
        <div
          className="pointer-events-none absolute -right-48 top-20 h-[520px] w-[520px] rounded-full border border-white/[.06]"
          aria-hidden="true"
        />
        <div
          className="pointer-events-none absolute -right-28 top-48 h-[380px] w-[380px] rounded-full border border-white/[.05]"
          aria-hidden="true"
        />

        <div className="relative mx-auto grid min-h-[610px] max-w-7xl grid-cols-1 gap-10 px-5 pb-12 pt-12 sm:px-8 sm:pb-16 sm:pt-14 lg:grid-cols-[minmax(0,1fr)_500px] lg:gap-14 lg:pb-14 lg:pt-16">
          {/* Left: start high, never vertically centered into empty space */}
          <div className="relative z-10 flex flex-col justify-start lg:pt-4">
            <div className="inline-flex w-fit items-center gap-2 rounded-full border border-white/15 bg-white/[.07] px-4 py-2 text-[10px] font-black uppercase tracking-[.2em] text-white/80 backdrop-blur-xl">
              <span
                className="h-1.5 w-1.5 rounded-full"
                style={{ backgroundColor: accent }}
              />
              Trusted financial support
            </div>

            <h1 className="mt-6 max-w-[720px] text-[2.8rem] font-black leading-[.98] tracking-[-.055em] sm:text-5xl lg:text-[4.7rem]">
              {tenant.hero?.headline ||
                tenant.tagline ||
                "Finance with clarity. Progress with confidence."}
            </h1>

            <p className="mt-6 max-w-[650px] text-[15px] leading-7 text-white/68 sm:text-lg sm:leading-8">
              {tenant.hero?.subtext ||
                tenant.mission ||
                "Clear, responsible lending with transparent terms and a secure digital application journey."}
            </p>

            <div className="mt-8 flex flex-col gap-3 sm:flex-row">
              <Link
                href="/apply"
                className="inline-flex min-h-12 items-center justify-center gap-2 rounded-xl px-6 py-3.5 text-sm font-black text-[#111827] shadow-[0_18px_45px_rgba(0,0,0,.28)] transition hover:-translate-y-0.5"
                style={{ backgroundColor: accent }}
              >
                Start an application <Arrow />
              </Link>
              <Link
                href="/services"
                className="inline-flex min-h-12 items-center justify-center gap-2 rounded-xl border border-white/15 bg-white/[.06] px-6 py-3.5 text-sm font-bold text-white backdrop-blur transition hover:bg-white/[.11]"
              >
                Explore solutions <Arrow className="h-3.5 w-3.5" />
              </Link>
            </div>

            <div className="mt-8 grid max-w-[700px] gap-3 sm:grid-cols-3">
              {[
                [
                  "Clear terms",
                  "Rates and repayment information presented before you apply.",
                ],
                [
                  "Secure journey",
                  "A structured digital application experience.",
                ],
                [
                  "Human support",
                  "A lending team available throughout your journey.",
                ],
              ].map(([title, description]) => (
                <div
                  key={title}
                  className="rounded-2xl border border-white/10 bg-white/[.055] p-4 backdrop-blur-xl"
                >
                  <div className="flex items-center gap-2 text-xs font-black text-white">
                    <span
                      className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg"
                      style={{ backgroundColor: `${accent}18`, color: accent }}
                    >
                      <Check />
                    </span>
                    {title}
                  </div>
                  <p className="mt-2.5 text-[10px] leading-5 text-white/48">
                    {description}
                  </p>
                </div>
              ))}
            </div>

            {tenant.stats?.length ? (
              <div className="mt-7 flex flex-wrap gap-x-7 gap-y-3 border-t border-white/10 pt-5">
                {tenant.stats.slice(0, 3).map((stat) => (
                  <div key={stat.label} className="flex items-baseline gap-2">
                    <span className="text-lg font-black text-white">
                      {stat.value}
                    </span>
                    <span className="text-[9px] font-black uppercase tracking-[.14em] text-white/40">
                      {stat.label}
                    </span>
                  </div>
                ))}
              </div>
            ) : null}
          </div>

          {/* Right: calculator begins near the top of the hero */}
          <div className="relative z-10 lg:pt-0">
            <div
              className="pointer-events-none absolute -inset-8 rounded-[48px] blur-3xl"
              style={{ backgroundColor: `${accent}18` }}
              aria-hidden="true"
            />
            <div className="relative rounded-[30px] border border-white/15 bg-white p-2 shadow-[0_35px_100px_rgba(0,0,0,.38)]">
              <div className="overflow-hidden rounded-[24px] bg-slate-50">
                <PublicLoanCalculator
                  products={products}
                  currency={tenant.currency}
                  primary={primary}
                  accent={accent}
                />
              </div>
            </div>
            <div className="mt-3 flex items-center justify-center gap-2 text-center text-[9px] font-bold uppercase tracking-[.15em] text-white/38">
              <Shield className="h-3.5 w-3.5" />
              Indicative estimate · subject to assessment and approval
            </div>

            <div className="mx-auto mt-5 grid max-w-[500px] grid-cols-2 gap-3">
              <div className="rounded-2xl border border-white/10 bg-white/[.06] px-4 py-3 backdrop-blur-xl">
                <div className="text-[9px] font-black uppercase tracking-[.14em] text-white/35">
                  Digital
                </div>
                <div className="mt-1 text-xs font-bold text-white/80">
                  Apply online
                </div>
              </div>
              <div className="rounded-2xl border border-white/10 bg-white/[.06] px-4 py-3 backdrop-blur-xl">
                <div className="text-[9px] font-black uppercase tracking-[.14em] text-white/35">
                  Planning
                </div>
                <div className="mt-1 text-xs font-bold text-white/80">
                  Estimate repayment
                </div>
              </div>
            </div>
          </div>
        </div>

        <div className="relative border-t border-white/10 bg-black/10">
          <div className="mx-auto flex max-w-7xl flex-col gap-3 px-5 py-4 text-[10px] font-bold uppercase tracking-[.14em] text-white/45 sm:flex-row sm:items-center sm:justify-between sm:px-8">
            <span>{tenant.name}</span>
            <span className="flex items-center gap-2">
              <Shield className="h-3.5 w-3.5" /> Secure digital lending ·{" "}
              {tenant.country || "Rwanda"}
            </span>
          </div>
        </div>
      </section>

      {/* ==========================================================
          PRODUCTS
         ========================================================== */}
      <section
        id="loan-products"
        className="mx-auto max-w-7xl px-5 py-20 sm:px-8 lg:py-28"
      >
        <div className="flex flex-col justify-between gap-7 lg:flex-row lg:items-end">
          <div className="max-w-2xl">
            <div
              className="text-[10px] font-black uppercase tracking-[.22em]"
              style={{ color: accent }}
            >
              Lending solutions
            </div>
            <h2 className="mt-3 text-4xl font-black tracking-[-.045em] sm:text-5xl">
              Financing designed around real needs.
            </h2>
            <p className="mt-5 text-base leading-7 text-slate-500">
              Explore the lending products configured by {tenant.name}, compare
              key terms and move directly into a secure application.
            </p>
          </div>
          <Link
            href="/services"
            className="inline-flex w-fit items-center gap-2 rounded-xl border border-slate-200 px-5 py-3 text-sm font-black shadow-sm transition hover:-translate-y-0.5 hover:shadow-md"
            style={{ color: primary }}
          >
            View all solutions <Arrow />
          </Link>
        </div>

        {products.length ? (
          <div className="mt-12 grid gap-5 md:grid-cols-2 lg:grid-cols-3">
            {products.slice(0, 6).map((service, index) => (
              <article
                key={`${service.title}-${index}`}
                className="group relative overflow-hidden rounded-[26px] border border-slate-200 bg-white p-7 shadow-[0_12px_45px_rgba(15,23,42,.045)] transition duration-300 hover:-translate-y-1 hover:shadow-[0_25px_65px_rgba(15,23,42,.10)]"
              >
                <div
                  className="absolute right-0 top-0 h-28 w-28 rounded-full opacity-10 blur-2xl"
                  style={{ backgroundColor: accent }}
                />
                <div className="relative flex items-start justify-between gap-4">
                  <div
                    className="flex h-12 w-12 items-center justify-center rounded-2xl text-xl"
                    style={{ backgroundColor: `${primary}0D`, color: primary }}
                  >
                    {service.icon || "•"}
                  </div>
                  <span className="text-[9px] font-black uppercase tracking-[.16em] text-slate-400">
                    {String(index + 1).padStart(2, "0")}
                  </span>
                </div>
                <h3 className="relative mt-7 text-xl font-black tracking-tight">
                  {service.title}
                </h3>
                <p className="relative mt-3 min-h-[72px] text-sm leading-6 text-slate-500">
                  {service.description}
                </p>
                <div className="relative mt-6 grid grid-cols-2 gap-2">
                  <div className="rounded-2xl bg-slate-50 p-3.5">
                    <div className="text-[9px] font-black uppercase tracking-[.12em] text-slate-400">
                      Rate
                    </div>
                    <div
                      className="mt-1 text-sm font-black"
                      style={{ color: primary }}
                    >
                      {formatRate(service.interestRate ?? service.rate)}
                    </div>
                  </div>
                  <div className="rounded-2xl bg-slate-50 p-3.5">
                    <div className="text-[9px] font-black uppercase tracking-[.12em] text-slate-400">
                      Term
                    </div>
                    <div className="mt-1 text-sm font-black">
                      {service.term || "Contact us"}
                    </div>
                  </div>
                </div>
                <div className="relative mt-4 flex items-center justify-between text-[11px] text-slate-400">
                  <span>Maximum financing</span>
                  <strong className="text-slate-700">
                    {formatAmount(tenant.currency, service.maxAmount)}
                  </strong>
                </div>
                <Link
                  href={`/apply?type=${encodeURIComponent(service.title)}`}
                  className="relative mt-6 inline-flex w-full items-center justify-center gap-2 rounded-xl px-4 py-3 text-sm font-black text-white transition hover:-translate-y-0.5"
                  style={{ backgroundColor: primary }}
                >
                  Apply for {service.title} <Arrow />
                </Link>
              </article>
            ))}
          </div>
        ) : (
          <div className="mt-12 rounded-[28px] border border-dashed border-slate-300 bg-slate-50 px-6 py-12 text-center">
            <h3 className="text-lg font-black">
              Lending products are being updated
            </h3>
            <p className="mx-auto mt-2 max-w-md text-sm leading-6 text-slate-500">
              Please contact our lending team for currently available financing
              options.
            </p>
            <Link
              href="/contact"
              className="mt-5 inline-flex rounded-xl px-5 py-3 text-sm font-black text-white"
              style={{ backgroundColor: primary }}
            >
              Contact us
            </Link>
          </div>
        )}
      </section>

      <section className="bg-[#07152A] text-white">
        <div className="mx-auto grid max-w-7xl gap-12 px-5 py-20 sm:px-8 lg:grid-cols-2 lg:items-center lg:py-24">
          <div>
            <div
              className="text-[10px] font-black uppercase tracking-[.22em]"
              style={{ color: accent }}
            >
              A better lending journey
            </div>
            <h2 className="mt-4 text-4xl font-black tracking-[-.045em] sm:text-5xl">
              Professional from application to repayment.
            </h2>
            <p className="mt-6 max-w-xl text-base leading-7 text-white/55">
              {tenant.mission ||
                "We combine responsible credit assessment, clear pricing and attentive client service to build lasting financial relationships."}
            </p>
            <div className="mt-8 grid gap-3 sm:grid-cols-2">
              {[
                "Clear product pricing and terms",
                "Secure digital application journey",
                "Responsible credit assessment",
                "Support throughout repayment",
              ].map((x) => (
                <div
                  key={x}
                  className="flex gap-3 rounded-2xl border border-white/10 bg-white/[.045] p-4"
                >
                  <span
                    className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg"
                    style={{ backgroundColor: `${accent}16`, color: accent }}
                  >
                    <Check />
                  </span>
                  <span className="text-sm font-semibold leading-6 text-white/80">
                    {x}
                  </span>
                </div>
              ))}
            </div>
          </div>
          <div className="rounded-[30px] border border-white/10 bg-white/[.055] p-8 shadow-2xl sm:p-10">
            <div className="flex items-center gap-3">
              <span
                className="flex h-10 w-10 items-center justify-center rounded-xl"
                style={{ backgroundColor: `${accent}18`, color: accent }}
              >
                <Shield />
              </span>
              <span className="text-[10px] font-black uppercase tracking-[.18em] text-white/40">
                Our commitment
              </span>
            </div>
            <div className="mt-6 text-2xl font-black leading-tight">
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
                className="rounded-xl border border-white/15 px-5 py-3 text-center text-sm font-bold hover:bg-white/10"
              >
                About us
              </Link>
              <Link
                href="/contact"
                className="rounded-xl px-5 py-3 text-center text-sm font-black text-[#111827]"
                style={{ backgroundColor: accent }}
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
              className="text-[10px] font-black uppercase tracking-[.22em]"
              style={{ color: accent }}
            >
              Client confidence
            </div>
            <h2 className="mt-3 text-4xl font-black tracking-[-.045em] sm:text-5xl">
              Built for long-term relationships.
            </h2>
          </div>
          <div className="mt-12 grid gap-5 md:grid-cols-3">
            {tenant.testimonials.slice(0, 3).map((testimonial, index) => (
              <article
                key={`${testimonial.name}-${index}`}
                className="rounded-[26px] border border-slate-200 bg-white p-7 shadow-[0_12px_45px_rgba(15,23,42,.045)]"
              >
                <div
                  className="text-sm tracking-[.25em]"
                  style={{ color: accent }}
                >
                  ★★★★★
                </div>
                <p className="mt-5 text-sm leading-7 text-slate-600">
                  “{testimonial.text}”
                </p>
                <div className="mt-7 border-t border-slate-100 pt-5">
                  <div className="text-sm font-black">{testimonial.name}</div>
                  <div className="mt-1 text-xs text-slate-400">
                    {testimonial.role}
                  </div>
                </div>
              </article>
            ))}
          </div>
        </section>
      ) : null}

      <section className="px-5 pb-20 sm:px-8 lg:pb-28">
        <div
          className="relative mx-auto max-w-7xl overflow-hidden rounded-[34px] px-6 py-14 text-center sm:px-10 lg:py-18"
          style={{ background: `linear-gradient(135deg,${primary},#061326)` }}
        >
          <div
            className="absolute -right-20 -top-28 h-80 w-80 rounded-full blur-3xl"
            style={{ backgroundColor: `${accent}20` }}
          />
          <div className="relative mx-auto max-w-2xl text-white">
            <div
              className="text-[10px] font-black uppercase tracking-[.22em]"
              style={{ color: accent }}
            >
              Ready when you are
            </div>
            <h2 className="mt-4 text-4xl font-black tracking-[-.045em] sm:text-5xl">
              Take the next step with confidence.
            </h2>
            <p className="mt-5 text-sm leading-7 text-white/55">
              Review available products, estimate repayment and submit your
              application securely online.
            </p>
            <div className="mt-8 flex flex-col justify-center gap-3 sm:flex-row">
              <Link
                href="/apply"
                className="rounded-xl px-6 py-3.5 text-sm font-black text-[#111827]"
                style={{ backgroundColor: accent }}
              >
                Apply now <span className="ml-2">→</span>
              </Link>
              <Link
                href="/track"
                className="rounded-xl border border-white/15 px-6 py-3.5 text-sm font-bold text-white hover:bg-white/10"
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
