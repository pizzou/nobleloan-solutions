"use client";

import Link from "next/link";
import PublicLoanCalculator from "../../components/PublicLoanCalculator";
import { useTenant } from "./layout";

function Arrow() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.9"
      className="h-4 w-4"
    >
      <path d="M5 12h14" />
      <path d="m13 6 6 6-6 6" />
    </svg>
  );
}
function Shield() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      className="h-5 w-5"
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
      strokeWidth="2.2"
      className="h-4 w-4"
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
  const n = Number(String(value).replace(/[^0-9.-]/g, ""));
  return Number.isFinite(n)
    ? `${currency} ${n.toLocaleString("en-RW", { maximumFractionDigits: 0 })}`
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
      <section className="relative isolate overflow-hidden bg-[#061326] text-white">
        <div
          className="absolute inset-0 opacity-60"
          style={{
            background: `radial-gradient(circle at 8% 10%, ${primary} 0, transparent 35%), radial-gradient(circle at 88% 18%, ${accent}25, transparent 25%), linear-gradient(120deg,#061326 0%,${primary} 62%,#102B50 100%)`,
          }}
        />
        <div
          className="absolute inset-0 opacity-[.06]"
          style={{
            backgroundImage:
              "linear-gradient(rgba(255,255,255,.8) 1px,transparent 1px),linear-gradient(90deg,rgba(255,255,255,.8) 1px,transparent 1px)",
            backgroundSize: "54px 54px",
          }}
        />
        <div className="relative mx-auto grid max-w-7xl gap-14 px-5 pb-20 pt-14 sm:px-8 sm:pb-24 sm:pt-20 lg:grid-cols-[1fr_500px] lg:items-center lg:gap-20 lg:py-24">
          <div className="max-w-3xl">
            <div className="inline-flex items-center gap-2 rounded-full border border-white/15 bg-white/[.07] px-3.5 py-2 text-[10px] font-black uppercase tracking-[.18em] text-white/75 backdrop-blur">
              <span
                className="h-1.5 w-1.5 rounded-full"
                style={{ backgroundColor: accent }}
              />
              Trusted financial support
            </div>
            <h1 className="mt-7 text-5xl font-black leading-[.98] tracking-[-.055em] sm:text-6xl lg:text-[5.1rem]">
              {tenant.hero?.headline ||
                tenant.tagline ||
                "Finance with clarity. Progress with confidence."}
            </h1>
            <p className="mt-7 max-w-2xl text-base leading-7 text-white/65 sm:text-lg">
              {tenant.hero?.subtext ||
                tenant.mission ||
                "Clear, responsible lending with transparent terms and a secure digital application journey."}
            </p>
            <div className="mt-9 flex flex-col gap-3 sm:flex-row">
              <Link
                href="/apply"
                className="inline-flex min-h-12 items-center justify-center gap-2 rounded-xl px-6 py-3.5 text-sm font-black text-[#111827] shadow-[0_18px_40px_rgba(0,0,0,.25)] transition hover:-translate-y-0.5"
                style={{ backgroundColor: accent }}
              >
                Start an application <Arrow />
              </Link>
              <Link
                href="/services"
                className="inline-flex min-h-12 items-center justify-center rounded-xl border border-white/15 bg-white/[.06] px-6 py-3.5 text-sm font-bold text-white backdrop-blur transition hover:bg-white/10"
              >
                Explore solutions
              </Link>
            </div>
            <div className="mt-10 flex flex-wrap gap-x-7 gap-y-3 text-[11px] font-bold text-white/55">
              <span className="flex items-center gap-2">
                <Shield /> Secure application journey
              </span>
              <span className="flex items-center gap-2">
                <Check /> Transparent terms
              </span>
              <span className="flex items-center gap-2">
                <Check /> Human support
              </span>
            </div>
          </div>
          <div className="relative">
            <div
              className="absolute -inset-8 rounded-[48px] blur-3xl"
              style={{ backgroundColor: `${accent}20` }}
            />
            <div className="relative rounded-[30px] border border-white/10 bg-white/95 p-2 shadow-[0_35px_100px_rgba(0,0,0,.35)]">
              <PublicLoanCalculator
                products={products}
                currency={tenant.currency}
                primary={primary}
                accent={accent}
              />
            </div>
            <div className="mt-4 flex justify-center text-[9px] font-bold uppercase tracking-[.16em] text-white/35">
              Indicative estimate • subject to assessment and approval
            </div>
          </div>
        </div>
      </section>

      {tenant.stats?.length ? (
        <section className="border-b border-slate-200 bg-white">
          <div className="mx-auto grid max-w-7xl grid-cols-2 sm:grid-cols-4">
            {tenant.stats.slice(0, 4).map((s) => (
              <div
                key={s.label}
                className="border-r border-b border-slate-100 px-5 py-7 text-center last:border-r-0 sm:border-b-0"
              >
                <div
                  className="text-2xl font-black tracking-tight"
                  style={{ color: primary }}
                >
                  {s.value}
                </div>
                <div className="mt-1 text-[9px] font-black uppercase tracking-[.18em] text-slate-400">
                  {s.label}
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
                    {service.icon}
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
            {tenant.testimonials.slice(0, 3).map((t, i) => (
              <article
                key={`${t.name}-${i}`}
                className="rounded-[26px] border border-slate-200 bg-white p-7 shadow-[0_12px_45px_rgba(15,23,42,.045)]"
              >
                <div
                  className="text-sm tracking-[.25em]"
                  style={{ color: accent }}
                >
                  ★★★★★
                </div>
                <p className="mt-5 text-sm leading-7 text-slate-600">
                  “{t.text}”
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
