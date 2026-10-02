"use client";

import Link from "next/link";
import Image from "next/image";
import { useMemo, useState } from "react";
import {
  calculateContractualSchedule,
  percentageCharge,
  safeRate,
} from "../../lib/loanRepaymentCalculator";
import { SITE_CONTENT } from "../../lib/siteContent";
import { useTenant } from "./layout";

type LoanProduct = (typeof SITE_CONTENT.services)[number];

function CompactLoanCalculator({
  products,
  currency,
}: {
  products: LoanProduct[];
  currency: string;
  accent?: string;
}) {
  const [index, setIndex] = useState(0);
  const product = products[index] || products[0];
  const [amount, setAmount] = useState(1000000);
  const [months, setMonths] = useState(6);
  const min = Number(product?.minAmount ?? 500000);
  const max = Math.max(
    min,
    product?.maxAmount == null ? 20000000 : Number(product.maxAmount),
  );
  const minTerm = Number(product?.minTermMonths ?? 1);
  const maxTerm = Math.max(minTerm, Number(product?.maxTermMonths ?? 12));
  const actualMonths = Math.min(maxTerm, Math.max(minTerm, months));
  const boundedAmount = Math.min(max, Math.max(min, amount || min));
  const interestRate = safeRate(product?.interestRate ?? product?.rate, 5);
  const managementRate = safeRate(product?.managementFeeRate, 5);
  const applicationRate = safeRate(product?.applicationFeeRate ?? 2, 2);
  const schedule = useMemo(
    () =>
      calculateContractualSchedule(
        boundedAmount,
        actualMonths,
        interestRate,
        managementRate,
      ),
    [boundedAmount, actualMonths, interestRate, managementRate],
  );
  const applicationFee = percentageCharge(boundedAmount, applicationRate);
  const money = (n: number) =>
    `${currency} ${Math.round(n).toLocaleString("en-RW")}`;
  const selectProduct = (next: number) => {
    const selected = products[next];
    setIndex(next);
    setAmount(Math.max(500000, Number(selected?.minAmount ?? 500000)));
    setMonths(Number(selected?.minTermMonths ?? 1));
  };
  return (
    <div className="noble-loan-calculator relative overflow-hidden rounded-[20px] border border-[#e8bd49] text-white shadow-[0_24px_70px_rgba(0,0,0,.38)]">
      <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(ellipse_at_top_right,rgba(238,190,67,.17),transparent_40%),linear-gradient(135deg,rgba(3,25,49,.96),rgba(4,39,70,.91))]" />
      <div className="relative border-b border-white/10 px-5 pb-3 pt-5 sm:px-6">
        <p className="text-[10px] font-black uppercase tracking-[.18em] text-[#f2c653]">
          Your plans, made clearer
        </p>
        <h2 className="mt-1 text-xl font-black tracking-tight sm:text-2xl">
          Calculate Your Loan
        </h2>
        <p className="mt-1 text-xs leading-5 text-white/75">
          Find out how much you can borrow and explore estimated repayments.
        </p>
      </div>
      <div className="relative space-y-3 p-5 sm:px-6 sm:pb-5">
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="block text-[10px] font-semibold text-white/85">
            Loan Product
            <select
              value={index}
              onChange={(e) => selectProduct(Number(e.target.value))}
              className="noble-calc-field mt-1.5 h-10 w-full rounded-md border border-white/30 px-3 text-xs font-semibold text-[#102543] outline-none focus:border-[#f2c653] focus:ring-2 focus:ring-[#f2c653]/30"
            >
              {products.map((p, i) => (
                <option key={`${p.title}-${i}`} value={i}>
                  {p.title}
                </option>
              ))}
            </select>
          </label>
          <label className="block text-[10px] font-semibold text-white/85">
            Loan Amount (RWF)
            <input
              type="number"
              inputMode="numeric"
              min={min}
              max={max}
              step={50000}
              value={String(amount)}
              onChange={(e) => setAmount(Number(e.target.value))}
              className="noble-calc-field mt-1.5 h-10 w-full rounded-md border border-white/30 px-3 text-xs font-semibold text-[#102543] outline-none focus:border-[#f2c653] focus:ring-2 focus:ring-[#f2c653]/30"
              aria-label="Loan amount in Rwandan francs"
            />
          </label>
        </div>
        <div className="flex justify-between text-[10px] text-white/55">
          <span>From {money(min)}</span>
          <span>Up to {money(max)}</span>
        </div>
        <input
          aria-label="Adjust loan amount"
          type="range"
          min={min}
          max={max}
          step={50000}
          value={boundedAmount}
          onChange={(e) => setAmount(Number(e.target.value))}
          className="w-full cursor-pointer accent-[#f2c653]"
        />
        <div className="grid gap-3 sm:grid-cols-[1fr_auto] sm:items-end">
          <label className="block text-[10px] font-semibold text-white/85">
            Repayment Term
            <select
              value={actualMonths}
              onChange={(e) => setMonths(Number(e.target.value))}
              className="noble-calc-field mt-1.5 h-10 w-full rounded-md border border-white/30 px-3 text-xs font-semibold text-[#102543] outline-none focus:border-[#f2c653] focus:ring-2 focus:ring-[#f2c653]/30"
            >
              {Array.from(
                { length: maxTerm - minTerm + 1 },
                (_, i) => minTerm + i,
              ).map((n) => (
                <option key={n} value={n}>
                  {n} {n === 1 ? "Month" : "Months"}
                </option>
              ))}
            </select>
          </label>
          <Link
            href="/apply"
            className="inline-flex h-10 items-center justify-center rounded-full bg-[#efc451] px-6 text-xs font-black text-[#09203c] transition hover:bg-[#ffda70]"
          >
            Calculate <span className="ml-2">→</span>
          </Link>
        </div>
        <div className="rounded-lg border border-white/10 bg-[#061a30]/75 p-3.5">
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
            <div className="sm:border-r sm:border-white/15 sm:pr-3">
              <p className="text-[9px] text-white/65">
                Average Monthly Payment
              </p>
              <p className="mt-1 text-base font-black text-white">
                {money(schedule.total / actualMonths)}
              </p>
            </div>
            <div className="sm:border-r sm:border-white/15 sm:px-3">
              <p className="text-[9px] text-white/65">Total Interest</p>
              <p className="mt-1 text-base font-black text-white">
                {money(schedule.interest)}
              </p>
            </div>
            <div className="col-span-2 sm:col-span-1 sm:pl-3">
              <p className="text-[9px] text-white/65">Total Repayment</p>
              <p className="mt-1 text-base font-black text-[#f2c653]">
                {money(schedule.total)}
              </p>
            </div>
          </div>
          <div className="mt-3 grid grid-cols-2 gap-3 border-t border-white/10 pt-3 sm:grid-cols-4">
            <div>
              <p className="text-[9px] text-white/55">
                Management Fee ({managementRate}%)
              </p>
              <p className="mt-1 text-[11px] font-bold">
                {money(schedule.management)}
              </p>
            </div>
            <div>
              <p className="text-[9px] text-white/55">
                Application Fee ({applicationRate}%)
              </p>
              <p className="mt-1 text-[11px] font-bold">
                {money(applicationFee)}
              </p>
            </div>
            <div>
              <p className="text-[9px] text-white/55">First Instalment</p>
              <p className="mt-1 text-[11px] font-bold">
                {money(schedule.firstInstallment)}
              </p>
            </div>
            <div>
              <p className="text-[9px] text-white/55">Last Instalment</p>
              <p className="mt-1 text-[11px] font-bold">
                {money(schedule.lastInstallment)}
              </p>
            </div>
          </div>
        </div>
        <p className="text-[9px] leading-4 text-white/55">
          Estimate only. Application fees are shown separately. Final fees,
          eligibility and repayment dates are confirmed in your loan agreement.
        </p>
      </div>
    </div>
  );
}

export default function HomePage() {
  const tenant = useTenant() || SITE_CONTENT;
  const primary = tenant.primaryColor || "#0F1B3D";
  const accent = tenant.accentColor || "#C9A227";
  const products = tenant.services?.length
    ? tenant.services
    : SITE_CONTENT.services;
  const contactPhone = tenant.contactPhone || "+250 788 123 456";
  const contactEmail = tenant.contactEmail || "info@nobleloansolutions.rw";
  const phoneHref = contactPhone.replace(/[^+\d]/g, "");
  const productImages = [
    "/personal-loan.jpg",
    "/business-loan.jpg",
    "/vehicle-loan.jpg",
    "/salary-advance.jpg",
    "/agriculture-loan.jpg",
  ];
  const productRoutes: Record<string, string> = {
    PERSONAL: "/personal-loans",
    BUSINESS: "/business-loans",
    AUTO: "/vehicle-loans",
    SALARY_ADVANCE: "/salary-advance",
    AGRICULTURAL: "/agriculture-loans",
  };
  const social = [
    { label: "Facebook", mark: "f", url: tenant.socialMedia?.facebook },
    { label: "Instagram", mark: "◎", url: tenant.socialMedia?.instagram },
    { label: "X", mark: "𝕏", url: tenant.socialMedia?.twitter },
    { label: "LinkedIn", mark: "in", url: tenant.socialMedia?.linkedin },
    { label: "YouTube", mark: "▶", url: tenant.socialMedia?.youtube },
  ];
  return (
    <main className="overflow-hidden bg-[#f3f5f8] text-[#0F1B3D]">
      <section className="relative isolate min-h-[560px] overflow-hidden bg-[#071a32] text-white lg:min-h-[570px]">
        <Image
          src="/hero-borrower.jpg"
          alt="Professional woman looking toward the future in Kigali"
          fill
          priority
          sizes="100vw"
          className="-z-20 object-cover object-[65%_center]"
        />
        <div className="absolute inset-0 -z-10 bg-[linear-gradient(90deg,rgba(4,19,39,.95)_0%,rgba(4,19,39,.84)_31%,rgba(4,19,39,.45)_59%,rgba(4,19,39,.12)_100%)]" />
        <div className="absolute inset-0 -z-10 bg-[linear-gradient(0deg,rgba(4,17,35,.7),transparent_38%,rgba(4,17,35,.12))]" />
        <div className="mx-auto grid max-w-7xl items-center gap-8 px-5 pb-10 pt-10 sm:px-8 sm:pb-12 sm:pt-12 lg:grid-cols-[minmax(0,1fr)_minmax(420px,455px)] lg:gap-10 lg:py-8 xl:gap-14">
          <div className="max-w-2xl">
            <div className="inline-flex items-center gap-2 rounded-full border border-white/25 bg-[#071a32]/45 px-4 py-2 text-[10px] font-extrabold uppercase tracking-[.22em] text-white/90 backdrop-blur-md">
              <span className="h-2 w-2 rounded-full bg-[#C9A227]" /> Trusted
              lending for Rwanda
            </div>
            <h1 className="mt-6 max-w-2xl text-[clamp(2.8rem,4.8vw,4.7rem)] font-black leading-[.96] tracking-[-.065em]">
              Real support.
              <span className="block text-[#F0C34E]">Bigger dreams.</span>
            </h1>
            <p className="mt-5 max-w-xl text-sm leading-6 text-white/85 sm:text-base sm:leading-7">
              From a personal milestone to your next business step, find a loan
              designed around your needs—with clear terms, practical guidance
              and a team ready to help.
            </p>
            <div className="mt-6 flex flex-col gap-3 sm:flex-row">
              <Link
                href="/apply"
                className="inline-flex min-h-12 items-center justify-center gap-3 rounded-full bg-[#C9A227] px-7 py-3 text-sm font-black text-[#0F1B3D] shadow-lg transition hover:-translate-y-0.5 hover:bg-[#e3b83e]"
              >
                Explore our loans <span>→</span>
              </Link>
              <Link
                href="/how-it-works"
                className="inline-flex min-h-12 items-center justify-center rounded-full border border-white/45 bg-white/10 px-7 py-3 text-sm font-bold text-white backdrop-blur-md transition hover:bg-white/20"
              >
                How it works
              </Link>
            </div>
            <div className="mt-7 grid max-w-xl grid-cols-3 border-t border-white/25 pt-4">
              <div className="pr-3">
                <p className="text-lg font-black text-[#F0C34E]">Clear</p>
                <p className="mt-1 text-xs leading-5 text-white/75">
                  Published fees & terms
                </p>
              </div>
              <div className="border-l border-white/25 px-4">
                <p className="text-lg font-black text-[#F0C34E]">Guided</p>
                <p className="mt-1 text-xs leading-5 text-white/75">
                  Step-by-step application
                </p>
              </div>
              <div className="border-l border-white/25 pl-4">
                <p className="text-lg font-black text-[#F0C34E]">Local</p>
                <p className="mt-1 text-xs leading-5 text-white/75">
                  Lending in Rwanda
                </p>
              </div>
            </div>
          </div>
          <div className="relative z-10">
            <CompactLoanCalculator
              products={products}
              currency={tenant.currency || "RWF"}
              accent={accent}
            />
          </div>
        </div>
        <div className="absolute bottom-0 left-0 right-0 h-1 bg-gradient-to-r from-transparent via-[#C9A227] to-transparent" />
      </section>

      <section className="relative bg-[#f7f8fa] py-9 sm:py-11">
        <div className="mx-auto max-w-7xl px-5 sm:px-8">
          <div className="flex flex-col justify-between gap-4 md:flex-row md:items-end">
            <div>
              <p className="text-[10px] font-black uppercase tracking-[.23em] text-[#9A7415]">
                Lending for real life
              </p>
              <h2 className="mt-2 text-3xl font-black tracking-[-.05em] sm:text-4xl">
                A loan for your next step.
              </h2>
              <p className="mt-3 max-w-2xl text-sm leading-6 text-slate-600">
                Explore the loan types available through Noble. Each product has
                its own eligibility and terms, so you can review the details
                before you apply.
              </p>
            </div>
            <Link
              href="/services"
              className="font-bold text-[#0F1B3D] hover:text-[#9A7415]"
            >
              Explore all loan products <span aria-hidden="true">→</span>
            </Link>
          </div>
          <div className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
            {products.slice(0, 5).map((p, i) => {
              const route =
                productRoutes[String(p.loanType || "").toUpperCase()] ||
                "/apply";
              return (
                <Link
                  key={`${p.title}-${i}`}
                  href={route}
                  className="group overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-[0_8px_28px_rgba(15,27,61,.06)] transition duration-300 hover:-translate-y-1 hover:border-[#C9A227]/60 hover:shadow-[0_20px_42px_rgba(15,27,61,.13)]"
                >
                  <div className="relative h-36 overflow-hidden bg-slate-100">
                    <Image
                      src={productImages[i] || productImages[0]}
                      alt={p.title}
                      fill
                      sizes="(max-width: 768px) 100vw, 20vw"
                      className="object-cover transition duration-500 group-hover:scale-105"
                    />
                    <span className="absolute bottom-3 left-3 flex h-10 w-10 items-center justify-center rounded-xl border border-white/60 bg-[#0F1B3D]/95 text-lg text-[#F0C34E] shadow-lg">
                      {p.icon || "◈"}
                    </span>
                  </div>
                  <div className="p-4">
                    <h3 className="font-black text-[#0F1B3D]">{p.title}</h3>
                    <p className="mt-2 min-h-[42px] text-xs leading-5 text-slate-600">
                      {p.description}
                    </p>
                    <div className="mt-3 flex items-center justify-between border-t border-slate-100 pt-3 text-xs font-bold text-[#9A7415]">
                      <span>View loan details</span>
                      <span className="transition group-hover:translate-x-1">
                        →
                      </span>
                    </div>
                  </div>
                </Link>
              );
            })}
          </div>
        </div>
      </section>

      <section className="relative overflow-hidden bg-[#e9eef3] py-14 sm:py-16">
        <div className="absolute inset-0 opacity-25">
          <Image
            src="/business-loan.jpg"
            alt=""
            fill
            sizes="100vw"
            className="object-cover"
          />
        </div>
        <div className="absolute inset-0 bg-gradient-to-r from-[#edf1f5]/95 via-[#edf1f5]/90 to-[#edf1f5]/75" />
        <div className="relative mx-auto grid max-w-7xl gap-10 px-5 sm:px-8 lg:grid-cols-[.8fr_1.2fr] lg:items-center">
          <div>
            <p className="text-[10px] font-black uppercase tracking-[.22em] text-[#9A7415]">
              A clearer borrowing journey
            </p>
            <h2 className="mt-3 text-3xl font-black tracking-[-.05em] sm:text-4xl">
              Know what comes next.
            </h2>
            <p className="mt-4 max-w-md text-sm leading-6 text-slate-600">
              From your first question to your repayment plan, we make the steps
              easier to understand—so you can make an informed decision.
            </p>
            <Link
              href="/how-it-works"
              className="mt-6 inline-flex items-center gap-2 rounded-full bg-[#0F1B3D] px-6 py-3 text-sm font-bold text-white hover:bg-[#18325c]"
            >
              See how it works <span>→</span>
            </Link>
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            {[
              {
                n: "01",
                title: "Choose your loan",
                body: "Compare the available loan types and review the published terms.",
              },
              {
                n: "02",
                title: "Apply securely",
                body: "Complete the application with the information requested.",
              },
              {
                n: "03",
                title: "Review the decision",
                body: "The team assesses your application and confirms next steps.",
              },
              {
                n: "04",
                title: "Understand repayment",
                body: "Read your agreement carefully and follow the agreed schedule.",
              },
            ].map((x, i) => (
              <div
                key={x.n}
                className="rounded-2xl border border-white/80 bg-white/90 p-5 shadow-[0_8px_24px_rgba(15,27,61,.06)] backdrop-blur-sm"
              >
                <div className="flex items-center gap-3">
                  <span className="flex h-10 w-10 items-center justify-center rounded-full bg-[#0F1B3D] text-sm font-black text-[#F0C34E]">
                    {x.n}
                  </span>
                  <h3 className="font-black">{x.title}</h3>
                </div>
                <p className="mt-3 text-xs leading-5 text-slate-600">
                  {x.body}
                </p>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="bg-[#0F1B3D] py-12 text-white sm:py-14">
        <div className="mx-auto grid max-w-7xl gap-8 px-5 sm:px-8 lg:grid-cols-[1fr_auto] lg:items-center">
          <div>
            <p className="text-[10px] font-black uppercase tracking-[.22em] text-[#E4B943]">
              Borrow with confidence
            </p>
            <h2 className="mt-2 text-3xl font-black tracking-tight">
              Clarity belongs at every step.
            </h2>
            <p className="mt-3 max-w-2xl text-sm leading-6 text-white/70">
              Review the costs, understand the repayment period and ask
              questions whenever something is unclear. A loan should fit your
              circumstances and your ability to repay.
            </p>
          </div>
          <div className="flex flex-col gap-3 sm:flex-row">
            <Link
              href="/services"
              className="rounded-full border border-white/30 px-6 py-3 text-center text-sm font-bold text-white hover:bg-white/10"
            >
              Review loan solutions
            </Link>
            <Link
              href="/track"
              className="rounded-full bg-[#C9A227] px-6 py-3 text-center text-sm font-black text-[#0F1B3D] hover:bg-[#e3b83e]"
            >
              Track application →
            </Link>
          </div>
        </div>
      </section>

      <section
        id="contact"
        className="relative overflow-hidden bg-[#071a32] py-14 text-white sm:py-20"
      >
        <div className="absolute -right-24 -top-24 h-72 w-72 rounded-full border border-white/10" />
        <div
          className="absolute -bottom-28 -left-16 h-64 w-64 rounded-full"
          style={{ backgroundColor: `${accent}12` }}
        />
        <div className="relative mx-auto grid max-w-7xl gap-8 px-5 sm:px-8 lg:grid-cols-[1fr_auto] lg:items-center">
          <div className="max-w-3xl">
            <p
              className="text-[10px] font-black uppercase tracking-[.22em]"
              style={{ color: accent }}
            >
              Customer support
            </p>
            <h2 className="mt-3 text-3xl font-black tracking-[-.045em] sm:text-4xl">
              Questions before you borrow? Talk to Noble.
            </h2>
            <p className="mt-4 max-w-2xl text-sm leading-7 text-white/65">
              Use the dedicated Contact page for detailed enquiries. Your
              message is routed into the Noble dashboard for authorised staff to
              review, while existing applications can be tracked separately.
            </p>
            <div className="mt-7 flex flex-col gap-3 sm:flex-row">
              <Link
                href="/contact"
                className="rounded-2xl px-6 py-3.5 text-center text-sm font-black text-slate-950"
                style={{ backgroundColor: accent }}
              >
                Contact the Noble team →
              </Link>
              <Link
                href="/track"
                className="rounded-2xl border border-white/15 px-6 py-3.5 text-center text-sm font-bold text-white"
              >
                Track an application
              </Link>
            </div>
          </div>
          <div className="grid gap-3 sm:grid-cols-2 lg:w-[420px]">
            <a
              href={`tel:${phoneHref}`}
              className="rounded-2xl border border-white/10 bg-white/[.05] p-5 transition hover:bg-white/[.09]"
            >
              <span className="text-[9px] font-black uppercase tracking-[.18em] text-white/40">
                Call
              </span>
              <span className="mt-2 block text-sm font-black">
                {contactPhone}
              </span>
            </a>
            <a
              href={`mailto:${contactEmail}`}
              className="rounded-2xl border border-white/10 bg-white/[.05] p-5 transition hover:bg-white/[.09]"
            >
              <span className="text-[9px] font-black uppercase tracking-[.18em] text-white/40">
                Email
              </span>
              <span className="mt-2 block break-all text-sm font-black">
                {contactEmail}
              </span>
            </a>
          </div>
        </div>
      </section>

      <section className="border-t border-slate-200 bg-white py-5">
        <div className="mx-auto flex max-w-7xl flex-col gap-2 px-5 text-[10px] leading-5 text-slate-500 sm:px-8 md:flex-row md:items-center md:justify-between">
          <span>
            © {new Date().getFullYear()} {tenant.name}. All rights reserved.
          </span>
          <span>
            Loan estimates are illustrative. Approval and final terms are
            subject to assessment and a signed agreement.
          </span>
        </div>
      </section>
    </main>
  );
}
