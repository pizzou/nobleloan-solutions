"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { SITE_CONTENT } from "../../lib/siteContent";

const faqs = [
  {
    q: "What loan options does Noble Loan Solutions provide?",
    a: "Noble Loan Solutions provides personal, business, vehicle, salary advance, and agriculture financing, subject to eligibility, verification, and approval.",
  },
  {
    q: "How does the application process work?",
    a: "Choose the financing option that fits your needs, complete the secure application, provide the required supporting documents, and our credit team reviews your application before a decision is made.",
  },
  {
    q: "How much can I borrow?",
    a: "The available amount depends on the selected loan product, your verified financial information, repayment capacity, supporting documentation, and the applicable credit assessment.",
  },
  {
    q: "How long does repayment take?",
    a: "Loan terms depend on the selected product and approved agreement. The current public product configuration supports terms from 1 to 6 months.",
  },
  {
    q: "Are there additional loan costs?",
    a: "Applicable interest, management fees, and the application fee are disclosed as part of the loan terms. Review the agreement carefully before accepting an offer.",
  },
  {
    q: "Can I track an application after submitting it?",
    a: "Yes. Use the application reference provided after submission through the Track Application page.",
  },
];

function ArrowIcon() {
  return (
    <svg viewBox="0 0 20 20" fill="none" className="h-4 w-4" aria-hidden="true">
      <path
        d="M4 10h11M11 5l5 5-5 5"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

function CheckIcon() {
  return (
    <svg viewBox="0 0 20 20" fill="none" className="h-4 w-4" aria-hidden="true">
      <path
        d="m4 10 3.5 3.5L16 5.5"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

function formatMoney(value: number, currency: string) {
  return `${currency} ${Math.round(value).toLocaleString()}`;
}

export default function PublicHomePage() {
  const tenant = SITE_CONTENT;
  const primary = tenant.primaryColor;
  const accent = tenant.accentColor;

  const [amount, setAmount] = useState(1000000);
  const [term, setTerm] = useState(3);
  const [activeFaq, setActiveFaq] = useState<number | null>(0);

  const selectedProduct = tenant.services[0];

  const calculation = useMemo(() => {
    const principal = Math.max(0, Number(amount) || 0);
    const months = Math.max(1, Number(term) || 1);

    const interestRate =
      Number(selectedProduct?.interestRate ?? tenant.monthlyInterestRate ?? 5) /
      100;

    const managementRate =
      Number(
        selectedProduct?.managementFeeRate ??
          tenant.monthlyManagementFeeRate ??
          5,
      ) / 100;

    const applicationRate =
      Number(
        selectedProduct?.applicationFeeRate ?? tenant.applicationFeeRate ?? 2,
      ) / 100;

    const interest = principal * interestRate * months;
    const managementFee = principal * managementRate * months;
    const applicationFee = principal * applicationRate;
    const totalCost = interest + managementFee + applicationFee;
    const totalRepayment = principal + totalCost;
    const monthlyPayment = totalRepayment / months;

    return {
      interest,
      managementFee,
      applicationFee,
      totalCost,
      totalRepayment,
      monthlyPayment,
    };
  }, [amount, term, selectedProduct, tenant]);

  return (
    <main className="bg-white text-slate-950">
      {/* HERO */}
      <section
        className="relative overflow-hidden"
        style={{
          background: `linear-gradient(135deg, ${primary} 0%, ${primary} 58%, #172a52 100%)`,
        }}
      >
        <div className="absolute -right-40 -top-40 h-[520px] w-[520px] rounded-full border border-white/10" />
        <div className="absolute -right-16 top-20 h-[360px] w-[360px] rounded-full border border-white/10" />
        <div className="absolute bottom-0 left-1/2 h-48 w-48 -translate-x-1/2 rounded-full bg-white/5 blur-3xl" />

        <div className="relative mx-auto grid max-w-7xl gap-14 px-5 pb-20 pt-16 sm:px-8 lg:grid-cols-[1.05fr_.95fr] lg:items-center lg:gap-20 lg:pb-28 lg:pt-24">
          <div className="max-w-3xl text-white">
            <div
              className="inline-flex items-center gap-2 rounded-full border px-3 py-1.5 text-[10px] font-black uppercase tracking-[.18em]"
              style={{
                borderColor: `${accent}80`,
                color: accent,
                backgroundColor: `${accent}10`,
              }}
            >
              <span
                className="h-1.5 w-1.5 rounded-full"
                style={{ backgroundColor: accent }}
              />
              Responsible lending in Rwanda
            </div>

            <h1 className="mt-7 max-w-3xl text-5xl font-black leading-[.98] tracking-[-.055em] sm:text-6xl lg:text-7xl">
              Financing designed around what you need.
            </h1>

            <p className="mt-7 max-w-2xl text-base leading-7 text-white/70 sm:text-lg">
              {tenant.tagline ||
                "Straightforward financing, transparent terms, and a secure application experience."}
            </p>

            <div className="mt-9 flex flex-col gap-3 sm:flex-row">
              <Link
                href="/apply"
                className="inline-flex items-center justify-center gap-2 rounded-xl px-6 py-3.5 text-sm font-black shadow-2xl transition hover:-translate-y-0.5"
                style={{
                  backgroundColor: accent,
                  color: primary,
                }}
              >
                Check your options
                <ArrowIcon />
              </Link>

              <Link
                href="/services"
                className="inline-flex items-center justify-center rounded-xl border border-white/20 px-6 py-3.5 text-sm font-bold text-white transition hover:bg-white/10"
              >
                Explore loan solutions
              </Link>
            </div>

            <div className="mt-9 flex flex-wrap gap-x-6 gap-y-3 text-xs text-white/60">
              <span className="flex items-center gap-2">
                <span style={{ color: accent }}>
                  <CheckIcon />
                </span>
                Secure application
              </span>
              <span className="flex items-center gap-2">
                <span style={{ color: accent }}>
                  <CheckIcon />
                </span>
                Clear loan terms
              </span>
              <span className="flex items-center gap-2">
                <span style={{ color: accent }}>
                  <CheckIcon />
                </span>
                Human support
              </span>
            </div>
          </div>

          {/* HERO APPLICATION CARD */}
          <div className="relative">
            <div className="rounded-[30px] border border-white/15 bg-white p-6 shadow-[0_30px_100px_rgba(0,0,0,.25)] sm:p-8">
              <div className="flex items-start justify-between gap-5">
                <div>
                  <div
                    className="text-[10px] font-black uppercase tracking-[.18em]"
                    style={{ color: primary }}
                  >
                    Start here
                  </div>
                  <h2 className="mt-2 text-2xl font-black tracking-[-.03em]">
                    See an estimated repayment
                  </h2>
                  <p className="mt-2 text-sm leading-6 text-slate-500">
                    Adjust the amount and term to understand the configured loan
                    costs before applying.
                  </p>
                </div>

                <div
                  className="hidden h-11 w-11 shrink-0 items-center justify-center rounded-2xl sm:flex"
                  style={{
                    backgroundColor: `${accent}20`,
                    color: primary,
                  }}
                >
                  <span className="text-lg font-black">R</span>
                </div>
              </div>

              <div className="mt-7">
                <div className="flex items-end justify-between gap-3">
                  <label
                    htmlFor="loan-amount"
                    className="text-xs font-black uppercase tracking-[.12em] text-slate-500"
                  >
                    Loan amount
                  </label>
                  <span
                    className="text-xl font-black"
                    style={{ color: primary }}
                  >
                    {formatMoney(amount, tenant.currency)}
                  </span>
                </div>

                <input
                  id="loan-amount"
                  type="range"
                  min={Number(selectedProduct?.minAmount ?? 500000)}
                  max={Math.max(
                    Number(selectedProduct?.minAmount ?? 500000) * 10,
                    10000000,
                  )}
                  step={100000}
                  value={amount}
                  onChange={(event) => setAmount(Number(event.target.value))}
                  className="mt-4 w-full accent-slate-900"
                />

                <div className="mt-1 flex justify-between text-[10px] font-semibold text-slate-400">
                  <span>
                    {formatMoney(
                      Number(selectedProduct?.minAmount ?? 500000),
                      tenant.currency,
                    )}
                  </span>
                  <span>10M+</span>
                </div>
              </div>

              <div className="mt-7">
                <label
                  htmlFor="loan-term"
                  className="text-xs font-black uppercase tracking-[.12em] text-slate-500"
                >
                  Repayment term
                </label>

                <div className="mt-3 grid grid-cols-3 gap-2">
                  {[1, 3, 6].map((months) => (
                    <button
                      key={months}
                      type="button"
                      onClick={() => setTerm(months)}
                      className="rounded-xl border px-3 py-3 text-sm font-black transition"
                      style={
                        term === months
                          ? {
                              borderColor: primary,
                              backgroundColor: `${primary}0A`,
                              color: primary,
                            }
                          : undefined
                      }
                    >
                      {months} {months === 1 ? "month" : "months"}
                    </button>
                  ))}
                </div>
              </div>

              <div className="mt-7 rounded-2xl bg-slate-50 p-5">
                <div className="flex items-center justify-between text-sm">
                  <span className="text-slate-500">
                    Estimated monthly payment
                  </span>
                  <strong
                    className="text-xl font-black"
                    style={{ color: primary }}
                  >
                    {formatMoney(calculation.monthlyPayment, tenant.currency)}
                  </strong>
                </div>

                <div className="mt-4 space-y-2 border-t border-slate-200 pt-4 text-xs">
                  <div className="flex justify-between">
                    <span className="text-slate-500">Principal</span>
                    <span className="font-bold">
                      {formatMoney(amount, tenant.currency)}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-500">Estimated interest</span>
                    <span className="font-bold">
                      {formatMoney(calculation.interest, tenant.currency)}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-500">Management fees</span>
                    <span className="font-bold">
                      {formatMoney(calculation.managementFee, tenant.currency)}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-500">Application fee</span>
                    <span className="font-bold">
                      {formatMoney(calculation.applicationFee, tenant.currency)}
                    </span>
                  </div>
                </div>
              </div>

              <Link
                href="/apply"
                className="mt-5 flex w-full items-center justify-center gap-2 rounded-xl px-5 py-3.5 text-sm font-black text-white transition hover:opacity-95"
                style={{ backgroundColor: primary }}
              >
                Start an application
                <ArrowIcon />
              </Link>

              <p className="mt-3 text-center text-[10px] leading-5 text-slate-400">
                Estimate only. Final terms are determined after verification and
                credit assessment.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* PRODUCT TYPES */}
      <section className="border-b border-slate-100 bg-white">
        <div className="mx-auto max-w-7xl px-5 py-16 sm:px-8 lg:py-20">
          <div className="flex flex-col justify-between gap-5 md:flex-row md:items-end">
            <div className="max-w-2xl">
              <div
                className="text-[10px] font-black uppercase tracking-[.2em]"
                style={{ color: accent }}
              >
                Loan solutions
              </div>
              <h2 className="mt-3 text-3xl font-black tracking-[-.04em] sm:text-4xl">
                One place for the financing you need.
              </h2>
              <p className="mt-4 text-sm leading-7 text-slate-500 sm:text-base">
                Choose a financing solution based on your personal,
                professional, or business needs.
              </p>
            </div>

            <Link
              href="/services"
              className="inline-flex items-center gap-2 text-sm font-black"
              style={{ color: primary }}
            >
              View all solutions
              <ArrowIcon />
            </Link>
          </div>

          <div className="mt-10 grid gap-4 md:grid-cols-2 lg:grid-cols-5">
            {tenant.services.map((service) => (
              <Link
                key={service.title}
                href={`/apply?type=${encodeURIComponent(
                  service.title.replace(/ /g, "_"),
                )}`}
                className="group rounded-[24px] border border-slate-200 bg-white p-5 transition duration-200 hover:-translate-y-1 hover:border-slate-300 hover:shadow-xl"
              >
                <div
                  className="flex h-12 w-12 items-center justify-center rounded-2xl text-xl"
                  style={{
                    backgroundColor: `${primary}0A`,
                  }}
                >
                  {service.icon}
                </div>

                <h3 className="mt-6 text-base font-black tracking-[-.02em]">
                  {service.title}
                </h3>

                <p className="mt-2 min-h-[72px] text-xs leading-6 text-slate-500">
                  {service.description}
                </p>

                <div
                  className="mt-5 flex items-center gap-2 text-xs font-black"
                  style={{ color: primary }}
                >
                  Explore
                  <ArrowIcon />
                </div>
              </Link>
            ))}
          </div>
        </div>
      </section>

      {/* TRUST STRIP */}
      <section style={{ backgroundColor: `${primary}08` }}>
        <div className="mx-auto max-w-7xl px-5 py-8 sm:px-8">
          <div className="grid grid-cols-2 gap-6 md:grid-cols-4">
            {tenant.stats.map((stat) => (
              <div
                key={stat.label}
                className="border-r border-slate-200/80 last:border-0 md:px-5"
              >
                <div className="text-xl">{stat.icon}</div>
                <div
                  className="mt-2 text-2xl font-black tracking-[-.04em]"
                  style={{ color: primary }}
                >
                  {stat.value}
                </div>
                <div className="mt-1 text-[10px] font-black uppercase tracking-[.12em] text-slate-400">
                  {stat.label}
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* HOW IT WORKS */}
      <section className="bg-white">
        <div className="mx-auto max-w-7xl px-5 py-20 sm:px-8 lg:py-28">
          <div className="mx-auto max-w-2xl text-center">
            <div
              className="text-[10px] font-black uppercase tracking-[.2em]"
              style={{ color: accent }}
            >
              Simple process
            </div>
            <h2 className="mt-3 text-3xl font-black tracking-[-.04em] sm:text-4xl">
              A straightforward path from application to funding.
            </h2>
            <p className="mt-4 text-sm leading-7 text-slate-500 sm:text-base">
              We keep the journey clear so you know what happens at every stage.
            </p>
          </div>

          <div className="relative mt-14 grid gap-5 md:grid-cols-4">
            <div className="absolute left-[12%] right-[12%] top-7 hidden h-px bg-slate-200 md:block" />

            {[
              {
                number: "01",
                title: "Choose your solution",
                text: "Select the loan product that matches your financial need.",
              },
              {
                number: "02",
                title: "Apply securely",
                text: "Complete your application and submit the required information.",
              },
              {
                number: "03",
                title: "We review",
                text: "Your information and supporting documents are verified and assessed.",
              },
              {
                number: "04",
                title: "Receive your decision",
                text: "Approved applicants proceed according to the agreed loan terms.",
              },
            ].map((step) => (
              <div
                key={step.number}
                className="relative rounded-3xl border border-slate-200 bg-white p-6 md:border-0 md:p-4 md:text-center"
              >
                <div
                  className="relative mx-auto flex h-14 w-14 items-center justify-center rounded-full border-4 border-white text-xs font-black shadow-lg"
                  style={{
                    backgroundColor: primary,
                    color: "white",
                  }}
                >
                  {step.number}
                </div>
                <h3 className="mt-6 text-base font-black">{step.title}</h3>
                <p className="mt-2 text-xs leading-6 text-slate-500">
                  {step.text}
                </p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* WHY NOBLE */}
      <section style={{ backgroundColor: `${primary}06` }}>
        <div className="mx-auto grid max-w-7xl gap-12 px-5 py-20 sm:px-8 lg:grid-cols-[.9fr_1.1fr] lg:items-center lg:py-28">
          <div>
            <div
              className="text-[10px] font-black uppercase tracking-[.2em]"
              style={{ color: accent }}
            >
              Why Noble
            </div>
            <h2 className="mt-4 max-w-xl text-4xl font-black tracking-[-.05em] sm:text-5xl">
              Lending built around clarity and responsibility.
            </h2>
            <p className="mt-6 max-w-xl text-sm leading-7 text-slate-500 sm:text-base">
              {tenant.mission ||
                "We provide responsible financial solutions with transparent terms and respectful client support."}
            </p>

            <Link
              href="/about"
              className="mt-7 inline-flex items-center gap-2 text-sm font-black"
              style={{ color: primary }}
            >
              Learn about Noble
              <ArrowIcon />
            </Link>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            {[
              {
                title: "Transparent terms",
                text: "Understand applicable rates, fees, repayment terms, and obligations before accepting financing.",
              },
              {
                title: "Secure application",
                text: "Your application journey is designed around controlled access and secure document submission.",
              },
              {
                title: "Local understanding",
                text: "Financing products are designed for the realities of individuals and businesses operating in Rwanda.",
              },
              {
                title: "Human support",
                text: "Digital convenience is combined with access to a real lending and client-support team.",
              },
            ].map((item) => (
              <div
                key={item.title}
                className="rounded-[24px] border border-slate-200 bg-white p-6 shadow-sm"
              >
                <div
                  className="flex h-10 w-10 items-center justify-center rounded-xl"
                  style={{
                    backgroundColor: `${accent}18`,
                    color: primary,
                  }}
                >
                  <CheckIcon />
                </div>
                <h3 className="mt-5 text-sm font-black">{item.title}</h3>
                <p className="mt-2 text-xs leading-6 text-slate-500">
                  {item.text}
                </p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* CLIENT STORIES */}
      {tenant.testimonials.length > 0 && (
        <section className="bg-white">
          <div className="mx-auto max-w-7xl px-5 py-20 sm:px-8 lg:py-28">
            <div className="flex flex-col justify-between gap-5 md:flex-row md:items-end">
              <div>
                <div
                  className="text-[10px] font-black uppercase tracking-[.2em]"
                  style={{ color: accent }}
                >
                  Client experience
                </div>
                <h2 className="mt-3 text-3xl font-black tracking-[-.04em] sm:text-4xl">
                  Built to make borrowing clearer.
                </h2>
              </div>

              <Link
                href="/testimonials"
                className="text-sm font-black"
                style={{ color: primary }}
              >
                View client stories →
              </Link>
            </div>

            <div className="mt-10 grid gap-5 md:grid-cols-3">
              {tenant.testimonials.slice(0, 3).map((item) => (
                <article
                  key={`${item.name}-${item.role}`}
                  className="rounded-[26px] border border-slate-200 bg-white p-7 shadow-sm"
                >
                  <div
                    className="text-sm tracking-[.12em]"
                    style={{ color: accent }}
                  >
                    {"★".repeat(Math.max(0, Math.min(5, item.rating)))}
                  </div>

                  <blockquote className="mt-5 text-sm font-semibold leading-7 text-slate-700">
                    “{item.text}”
                  </blockquote>

                  <div className="mt-7 border-t border-slate-100 pt-5">
                    <div className="text-sm font-black">{item.name}</div>
                    <div className="mt-1 text-xs text-slate-400">
                      {item.role}
                    </div>
                  </div>
                </article>
              ))}
            </div>
          </div>
        </section>
      )}

      {/* FAQ */}
      <section className="border-t border-slate-100 bg-slate-50">
        <div className="mx-auto max-w-5xl px-5 py-20 sm:px-8 lg:py-28">
          <div className="mx-auto max-w-2xl text-center">
            <div
              className="text-[10px] font-black uppercase tracking-[.2em]"
              style={{ color: accent }}
            >
              Questions
            </div>
            <h2 className="mt-3 text-3xl font-black tracking-[-.04em] sm:text-4xl">
              Let&apos;s answer the important things first.
            </h2>
            <p className="mt-4 text-sm leading-7 text-slate-500">
              Clear information helps you make an informed borrowing decision.
            </p>
          </div>

          <div className="mt-10 overflow-hidden rounded-[26px] border border-slate-200 bg-white">
            {faqs.map((faq, index) => {
              const open = activeFaq === index;

              return (
                <div
                  key={faq.q}
                  className="border-b border-slate-100 last:border-b-0"
                >
                  <button
                    type="button"
                    onClick={() => setActiveFaq(open ? null : index)}
                    className="flex w-full items-center justify-between gap-6 px-6 py-5 text-left sm:px-7"
                    aria-expanded={open}
                  >
                    <span className="text-sm font-black">{faq.q}</span>

                    <span
                      className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-lg font-light"
                      style={{
                        backgroundColor: `${primary}08`,
                        color: primary,
                      }}
                    >
                      {open ? "−" : "+"}
                    </span>
                  </button>

                  {open && (
                    <div className="px-6 pb-6 sm:px-7">
                      <p className="max-w-3xl text-sm leading-7 text-slate-500">
                        {faq.a}
                      </p>
                    </div>
                  )}
                </div>
              );
            })}
          </div>

          <div className="mt-8 text-center">
            <Link
              href="/faq"
              className="text-sm font-black"
              style={{ color: primary }}
            >
              View all frequently asked questions →
            </Link>
          </div>
        </div>
      </section>

      {/* FINAL CTA */}
      <section
        className="relative overflow-hidden text-white"
        style={{ backgroundColor: primary }}
      >
        <div
          className="absolute right-0 top-0 h-full w-1/2 opacity-20"
          style={{
            background: `radial-gradient(circle at center, ${accent}, transparent 65%)`,
          }}
        />

        <div className="relative mx-auto flex max-w-7xl flex-col gap-8 px-5 py-16 sm:px-8 md:flex-row md:items-center md:justify-between lg:py-20">
          <div className="max-w-2xl">
            <div
              className="text-[10px] font-black uppercase tracking-[.2em]"
              style={{ color: accent }}
            >
              Ready when you are
            </div>
            <h2 className="mt-3 text-3xl font-black tracking-[-.04em] sm:text-4xl">
              Find the financing solution that fits your next step.
            </h2>
            <p className="mt-4 text-sm leading-7 text-white/60">
              Start your application online or track an application you have
              already submitted.
            </p>
          </div>

          <div className="flex shrink-0 flex-col gap-3 sm:flex-row">
            <Link
              href="/apply"
              className="inline-flex items-center justify-center gap-2 rounded-xl px-6 py-3.5 text-sm font-black"
              style={{
                backgroundColor: accent,
                color: primary,
              }}
            >
              Apply now
              <ArrowIcon />
            </Link>

            <Link
              href="/track"
              className="inline-flex items-center justify-center rounded-xl border border-white/20 px-6 py-3.5 text-sm font-bold text-white hover:bg-white/10"
            >
              Track application
            </Link>
          </div>
        </div>
      </section>
    </main>
  );
}
