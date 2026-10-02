"use client";

import Image from "next/image";
import Link from "next/link";
import { SITE_CONTENT } from "../lib/siteContent";
import { useTenant } from "../app/(site)/layout";

export function ResourceHubPage() {
  const tenant = useTenant() || SITE_CONTENT;
  const primary = tenant.primaryColor || "#0F1B3D";
  const accent = tenant.accentColor || "#C9A227";
  const cards = [
    [
      "Loan calculator",
      "Model an amount, term and indicative repayment before you apply.",
      "/loan-calculator",
      "Plan the numbers",
    ],
    [
      "How lending works",
      "See the journey from choosing a product through application and tracking.",
      "/how-it-works",
      "Understand the process",
    ],
    [
      "Frequently asked questions",
      "Get clear answers about products, applications, repayment and tracking.",
      "/faq",
      "Get answers",
    ],
    [
      "Borrower help",
      "Find the right support route for a new application or an existing loan.",
      "/help",
      "Find support",
    ],
    [
      "Inflation calculator",
      "Explore how purchasing power can change over time for planning purposes.",
      "/inflation-calculator",
      "Explore the tool",
    ],
    [
      "Contact Noble",
      "Send a secure enquiry to the Noble team through the public contact form.",
      "/contact",
      "Talk to the team",
    ],
  ];

  return (
    <main className="overflow-hidden bg-[#f5f7fa] text-slate-950">
      <section
        className="relative overflow-hidden text-white"
        style={{
          background: `linear-gradient(120deg,#061326 0%,${primary} 64%,#1b4169 100%)`,
        }}
      >
        <Image
          src="/business-loan.jpg"
          alt=""
          fill
          sizes="100vw"
          className="object-cover object-center opacity-[.13] mix-blend-screen"
          priority
        />
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_78%_20%,rgba(201,162,39,.25),transparent_26%)]" />
        <div className="relative mx-auto max-w-7xl px-5 py-16 sm:px-8 sm:py-24">
          <p
            className="text-[10px] font-black uppercase tracking-[.24em]"
            style={{ color: accent }}
          >
            Noble Learn
          </p>
          <h1 className="mt-5 max-w-4xl text-4xl font-black leading-[.98] tracking-[-.06em] sm:text-6xl">
            Useful information for better financial decisions.
          </h1>
          <p className="mt-6 max-w-2xl text-base leading-8 text-white/70 sm:text-lg">
            Practical guidance, calculators and support routes built around
            Noble Loan Solutions&apos; Rwanda lending experience.
          </p>
        </div>
      </section>

      <section className="border-b border-slate-200 bg-white">
        <div className="mx-auto grid max-w-7xl gap-px sm:grid-cols-3">
          {[
            [
              "Know the product",
              "Review the active loan options and published terms.",
            ],
            [
              "Know the numbers",
              "Use the calculator for an indicative planning scenario.",
            ],
            [
              "Know the next step",
              "Apply securely or contact the team when you need help.",
            ],
          ].map(([title, body]) => (
            <div
              key={title}
              className="border-b border-slate-100 p-6 last:border-b-0 sm:border-b-0 sm:border-r sm:last:border-r-0 sm:p-7"
            >
              <h2 className="text-sm font-black">{title}</h2>
              <p className="mt-2 text-xs leading-6 text-slate-500">{body}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-5 py-14 sm:px-8 sm:py-20">
        <div className="grid gap-5 md:grid-cols-2 lg:grid-cols-3">
          {cards.map(([title, body, href, action]) => (
            <Link
              href={href}
              key={href}
              className="group rounded-[28px] border border-slate-200 bg-white p-7 shadow-[0_14px_45px_rgba(15,23,42,.05)] transition duration-300 hover:-translate-y-1 hover:shadow-[0_24px_65px_rgba(15,23,42,.10)]"
            >
              <div className="flex items-center justify-between gap-4">
                <span
                  className="text-[10px] font-black uppercase tracking-[.18em]"
                  style={{ color: accent }}
                >
                  Resource
                </span>
                <span
                  className="flex h-9 w-9 items-center justify-center rounded-xl bg-slate-50 text-sm transition group-hover:translate-x-0.5"
                  style={{ color: primary }}
                >
                  ↗
                </span>
              </div>
              <h2 className="mt-5 text-xl font-black tracking-tight">
                {title}
              </h2>
              <p className="mt-3 text-sm leading-7 text-slate-500">{body}</p>
              <span
                className="mt-7 block text-sm font-black"
                style={{ color: primary }}
              >
                {action} →
              </span>
            </Link>
          ))}
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-5 pb-20 sm:px-8">
        <div className="rounded-[32px] border border-slate-200 bg-white p-8 shadow-sm sm:p-10">
          <div className="grid gap-8 lg:grid-cols-[1fr_auto] lg:items-center">
            <div>
              <p
                className="text-[10px] font-black uppercase tracking-[.2em]"
                style={{ color: accent }}
              >
                A helpful reminder
              </p>
              <h2 className="mt-3 text-3xl font-black tracking-[-.045em]">
                Use the public site to understand the journey before you commit.
              </h2>
              <p className="mt-4 max-w-2xl text-sm leading-7 text-slate-500">
                Calculators are estimates for planning. Eligibility, approval,
                fees and final repayment obligations are governed by the
                applicable assessment, product terms and signed loan agreement.
              </p>
            </div>
            <div className="flex flex-col gap-3 sm:flex-row lg:flex-col">
              <Link
                href="/services"
                className="rounded-2xl px-6 py-3.5 text-center text-sm font-black text-white"
                style={{ backgroundColor: primary }}
              >
                Explore loan solutions →
              </Link>
              <Link
                href="/contact"
                className="rounded-2xl border border-slate-200 px-6 py-3.5 text-center text-sm font-bold"
                style={{ color: primary }}
              >
                Contact Noble
              </Link>
            </div>
          </div>
        </div>
      </section>
    </main>
  );
}
