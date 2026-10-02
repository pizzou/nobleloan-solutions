"use client";

import Link from "next/link";
import Image from "next/image";
import { FormEvent, useMemo, useState } from "react";
import {
  calculateContractualSchedule,
  safeRate,
} from "../../lib/loanRepaymentCalculator";
import { publicApi } from "../../services/api";
import { SITE_CONTENT } from "../../lib/siteContent";
import { useTenant } from "./layout";

function ContactQuickForm({
  primary,
  accent,
  slug,
}: {
  primary: string;
  accent: string;
  slug: string;
}) {
  const [form, setForm] = useState({
    name: "",
    email: "",
    phone: "",
    subject: "Loan enquiry",
    message: "",
  });
  const [status, setStatus] = useState<
    "idle" | "sending" | "success" | "error"
  >("idle");
  const [error, setError] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setStatus("sending");
    setError("");
    try {
      await publicApi.contact({ tenantSlug: slug, ...form });
      setForm({
        name: "",
        email: "",
        phone: "",
        subject: "Loan enquiry",
        message: "",
      });
      setStatus("success");
    } catch (cause) {
      setStatus("error");
      setError(
        cause instanceof Error
          ? cause.message
          : "We could not send your message. Please try again.",
      );
    }
  }

  return (
    <form onSubmit={submit} className="grid gap-3 sm:grid-cols-2">
      <label className="text-xs font-bold text-slate-600">
        Your name
        <input
          required
          maxLength={120}
          value={form.name}
          onChange={(e) => setForm({ ...form, name: e.target.value })}
          placeholder="Full name"
          className="mt-2 w-full rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm outline-none transition focus:border-[#C9A227]"
        />
      </label>
      <label className="text-xs font-bold text-slate-600">
        Email address
        <input
          type="email"
          maxLength={180}
          value={form.email}
          onChange={(e) => setForm({ ...form, email: e.target.value })}
          placeholder="you@example.com"
          className="mt-2 w-full rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm outline-none transition focus:border-[#C9A227]"
        />
      </label>
      <label className="text-xs font-bold text-slate-600">
        Phone number{" "}
        <span className="font-normal text-slate-400">(optional)</span>
        <input
          maxLength={40}
          value={form.phone}
          onChange={(e) => setForm({ ...form, phone: e.target.value })}
          placeholder="Your phone number"
          className="mt-2 w-full rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm outline-none transition focus:border-[#C9A227]"
        />
      </label>
      <label className="text-xs font-bold text-slate-600">
        What can we help with?
        <select
          value={form.subject}
          onChange={(e) => setForm({ ...form, subject: e.target.value })}
          className="mt-2 w-full rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm outline-none focus:border-[#C9A227]"
        >
          <option>Loan enquiry</option>
          <option>Application support</option>
          <option>Repayment question</option>
          <option>Existing loan</option>
          <option>General enquiry</option>
        </select>
      </label>
      <label className="text-xs font-bold text-slate-600 sm:col-span-2">
        Your message
        <textarea
          required
          minLength={5}
          maxLength={3000}
          rows={3}
          value={form.message}
          onChange={(e) => setForm({ ...form, message: e.target.value })}
          placeholder="Tell us a little about your question…"
          className="mt-2 w-full resize-y rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm outline-none focus:border-[#C9A227]"
        />
      </label>
      {status === "success" && (
        <p
          role="status"
          className="rounded-xl bg-emerald-50 p-3 text-sm font-semibold text-emerald-800 sm:col-span-2"
        >
          Your message has been sent to the Noble team.
        </p>
      )}
      {status === "error" && (
        <p
          role="alert"
          className="rounded-xl bg-red-50 p-3 text-sm font-semibold text-red-700 sm:col-span-2"
        >
          {error}
        </p>
      )}
      <div className="flex flex-col justify-between gap-3 sm:col-span-2 sm:flex-row sm:items-center">
        <p className="max-w-md text-[10px] leading-5 text-slate-400">
          Please do not include passwords, OTPs, PINs or other confidential
          credentials.
        </p>
        <button
          type="submit"
          disabled={status === "sending"}
          className="rounded-xl px-6 py-3.5 text-sm font-black text-white transition hover:-translate-y-0.5 disabled:opacity-60"
          style={{ backgroundColor: primary }}
        >
          {status === "sending" ? "Sending message…" : "Send a message →"}
        </button>
      </div>
    </form>
  );
}

type LoanProduct = (typeof SITE_CONTENT.services)[number];

function CompactLoanCalculator({
  products,
  currency,
  accent,
}: {
  products: LoanProduct[];
  currency: string;
  accent: string;
}) {
  const [index, setIndex] = useState(0);
  const product = products[index] || products[0];
  const [amount, setAmount] = useState(1000000);
  const [months, setMonths] = useState(6);
  const min = Number(product?.minAmount ?? 500000);
  const maxTerm = Number(product?.maxTermMonths ?? 6);
  const minTerm = Number(product?.minTermMonths ?? 1);
  const actualMonths = Math.min(maxTerm, Math.max(minTerm, months));
  const boundedAmount = Math.max(min, amount || min);
  const schedule = useMemo(
    () =>
      calculateContractualSchedule(
        boundedAmount,
        actualMonths,
        safeRate(product?.interestRate ?? product?.rate, 5),
        safeRate(product?.managementFeeRate, 5),
      ),
    [boundedAmount, actualMonths, product],
  );
  const money = (n: number) =>
    `${currency} ${Math.round(n).toLocaleString("en-RW")}`;
  const selectProduct = (next: number) => {
    setIndex(next);
    setAmount(Math.max(500000, Number(products[next]?.minAmount ?? 500000)));
    setMonths(Number(products[next]?.minTermMonths ?? 1));
  };
  return (
    <div className="overflow-hidden rounded-[24px] border border-white/70 bg-white shadow-[0_28px_80px_rgba(4,20,42,.28)]">
      <div className="flex items-start justify-between gap-3 border-b border-slate-100 px-5 pb-4 pt-5 sm:px-6">
        <div>
          <p className="text-[10px] font-black uppercase tracking-[.2em] text-[#9a7415]">
            Noble loan planner
          </p>
          <h2 className="mt-1 text-xl font-black tracking-tight text-[#0F1B3D] sm:text-2xl">
            Make a plan that fits.
          </h2>
          <p className="mt-1 text-xs leading-5 text-slate-500">
            Explore an estimate before you apply.
          </p>
        </div>
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-[#0F1B3D] text-lg font-black text-[#E4B943]">
          ₣
        </div>
      </div>
      <div className="p-5 sm:p-6">
        <label className="block text-[11px] font-bold text-slate-600">
          Choose a loan type
        </label>
        <select
          value={index}
          onChange={(e) => selectProduct(Number(e.target.value))}
          className="mt-2 h-11 w-full rounded-xl border border-slate-200 bg-slate-50 px-3 text-sm font-semibold text-[#0F1B3D] outline-none focus:border-[#C9A227]"
        >
          {products.map((p, i) => (
            <option key={`${p.title}-${i}`} value={i}>
              {p.title}
            </option>
          ))}
        </select>
        <div className="mt-4 flex items-center justify-between gap-3">
          <label
            htmlFor="noble-amount"
            className="text-[11px] font-bold text-slate-600"
          >
            Loan amount
          </label>
          <span className="text-[10px] text-slate-400">
            Minimum {money(min)}
          </span>
        </div>
        <div className="mt-2 flex h-12 items-center rounded-xl border border-slate-200 bg-white px-3 focus-within:border-[#C9A227]">
          <span className="mr-2 text-xs font-bold text-slate-400">
            {currency}
          </span>
          <input
            id="noble-amount"
            inputMode="numeric"
            value={String(amount)}
            onChange={(e) =>
              setAmount(Number(e.target.value.replace(/\D/g, "")))
            }
            className="w-full min-w-0 bg-transparent text-lg font-black text-[#0F1B3D] outline-none"
            aria-label="Loan amount"
          />
        </div>
        <input
          aria-label="Adjust loan amount"
          type="range"
          min={min}
          max={Math.max(min + 100000, 20000000)}
          step={50000}
          value={Math.min(
            Math.max(boundedAmount, min),
            Math.max(min + 100000, 20000000),
          )}
          onChange={(e) => setAmount(Number(e.target.value))}
          className="mt-3 w-full accent-[#C9A227]"
        />
        <div className="mt-4">
          <label
            htmlFor="noble-term"
            className="text-[11px] font-bold text-slate-600"
          >
            Repayment term
          </label>
          <select
            id="noble-term"
            value={actualMonths}
            onChange={(e) => setMonths(Number(e.target.value))}
            className="mt-2 h-11 w-full rounded-xl border border-slate-200 bg-slate-50 px-3 text-sm font-semibold text-[#0F1B3D] outline-none focus:border-[#C9A227]"
          >
            {Array.from(
              { length: maxTerm - minTerm + 1 },
              (_, i) => minTerm + i,
            ).map((n) => (
              <option key={n} value={n}>
                {n} {n === 1 ? "month" : "months"}
              </option>
            ))}
          </select>
        </div>
        <div className="mt-5 rounded-2xl bg-[#0F1B3D] p-4 text-white">
          <p className="text-[10px] font-semibold text-white/65">
            Indicative total repayment
          </p>
          <p className="mt-1 text-2xl font-black tracking-tight text-[#F2C653] sm:text-3xl">
            {money(schedule.total)}
          </p>
          <div className="mt-4 grid grid-cols-2 gap-3 border-t border-white/15 pt-3">
            <div>
              <p className="text-[10px] text-white/55">First instalment</p>
              <p className="mt-1 text-sm font-bold">
                {money(schedule.firstInstallment)}
              </p>
            </div>
            <div>
              <p className="text-[10px] text-white/55">Monthly interest*</p>
              <p className="mt-1 text-sm font-bold">
                {safeRate(product?.interestRate ?? product?.rate, 5)}%
              </p>
            </div>
          </div>
        </div>
        <Link
          href="/apply"
          className="mt-4 flex h-12 items-center justify-center gap-2 rounded-xl bg-[#C9A227] px-4 text-sm font-black text-[#0F1B3D] transition hover:bg-[#e0b73f]"
        >
          Apply for this loan <span>→</span>
        </Link>
        <p className="mt-3 text-[10px] leading-4 text-slate-400">
          Illustration only. Fees, eligibility and the final repayment schedule
          are confirmed during assessment and in your loan agreement.
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
    "/images/noble/personal-loan.jpg",
    "/images/noble/business-loan.jpg",
    "/images/noble/vehicle-loan.jpg",
    "/images/noble/salary-advance.jpg",
    "/images/noble/agriculture-loan.jpg",
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
      <section className="relative isolate min-h-[650px] overflow-hidden bg-[#071a32] text-white lg:min-h-[690px]">
        <Image
          src="/images/noble/hero-borrower.jpg"
          alt="Professional woman looking toward the future in Kigali"
          fill
          priority
          sizes="100vw"
          className="-z-20 object-cover object-[65%_center]"
        />
        <div className="absolute inset-0 -z-10 bg-[linear-gradient(90deg,rgba(4,19,39,.95)_0%,rgba(4,19,39,.84)_31%,rgba(4,19,39,.45)_59%,rgba(4,19,39,.12)_100%)]" />
        <div className="absolute inset-0 -z-10 bg-[linear-gradient(0deg,rgba(4,17,35,.7),transparent_38%,rgba(4,17,35,.12))]" />
        <div className="mx-auto grid max-w-7xl items-center gap-10 px-5 pb-16 pt-14 sm:px-8 sm:pb-20 sm:pt-16 lg:grid-cols-[1fr_430px] lg:gap-14 lg:py-20 xl:grid-cols-[1fr_455px]">
          <div className="max-w-2xl">
            <div className="inline-flex items-center gap-2 rounded-full border border-white/25 bg-[#071a32]/45 px-4 py-2 text-[10px] font-extrabold uppercase tracking-[.22em] text-white/90 backdrop-blur-md">
              <span className="h-2 w-2 rounded-full bg-[#C9A227]" /> Trusted
              lending for Rwanda
            </div>
            <h1 className="mt-6 max-w-2xl text-[clamp(3rem,5.7vw,5.7rem)] font-black leading-[.96] tracking-[-.065em]">
              Real support.
              <span className="block text-[#F0C34E]">Bigger dreams.</span>
            </h1>
            <p className="mt-6 max-w-xl text-base leading-7 text-white/85 sm:text-lg sm:leading-8">
              From a personal milestone to your next business step, find a loan
              designed around your needs—with clear terms, practical guidance
              and a team ready to help.
            </p>
            <div className="mt-8 flex flex-col gap-3 sm:flex-row">
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
            <div className="mt-9 grid max-w-xl grid-cols-3 border-t border-white/25 pt-5">
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

      <section className="relative bg-white py-14 sm:py-17">
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
          <div className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
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
            src="/images/noble/business-loan.jpg"
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
              href="/loan-terms"
              className="rounded-full border border-white/30 px-6 py-3 text-center text-sm font-bold text-white hover:bg-white/10"
            >
              Review loan terms
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
        className="relative overflow-hidden bg-[#f4f6f8] py-14 sm:py-16"
      >
        <div className="absolute right-0 top-0 h-64 w-64 rounded-bl-full bg-[#C9A227]/10" />
        <div className="relative mx-auto grid max-w-7xl gap-8 px-5 sm:px-8 lg:grid-cols-[.8fr_1.2fr] lg:gap-12">
          <div className="rounded-[26px] bg-[#071a32] p-6 text-white shadow-xl sm:p-8">
            <p className="text-[10px] font-black uppercase tracking-[.22em] text-[#F0C34E]">
              We are here to help
            </p>
            <h2 className="mt-3 text-3xl font-black tracking-[-.04em]">
              Talk to the Noble team.
            </h2>
            <p className="mt-3 text-sm leading-6 text-white/65">
              Have a question about a loan, your application or repayment terms?
              Send us a message or contact us directly.
            </p>
            <div className="mt-7 space-y-4 border-t border-white/15 pt-6">
              <a
                href={`tel:${phoneHref}`}
                className="flex items-center gap-3 text-sm font-semibold hover:text-[#F0C34E]"
              >
                <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-white/10 text-[#F0C34E]">
                  ☎
                </span>
                <span>
                  <span className="block text-[10px] text-white/50">
                    Call us
                  </span>
                  {contactPhone}
                </span>
              </a>
              <a
                href={`mailto:${contactEmail}`}
                className="flex items-center gap-3 text-sm font-semibold hover:text-[#F0C34E]"
              >
                <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-white/10 text-[#F0C34E]">
                  ✉
                </span>
                <span>
                  <span className="block text-[10px] text-white/50">
                    Email us
                  </span>
                  {contactEmail}
                </span>
              </a>
              <div className="flex items-center gap-3 text-sm font-semibold">
                <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-white/10 text-[#F0C34E]">
                  ⌖
                </span>
                <span>
                  <span className="block text-[10px] text-white/50">
                    Visit us
                  </span>
                  {tenant.address || "Kigali, Rwanda"}
                </span>
              </div>
            </div>
            <div className="mt-6 border-t border-white/15 pt-5">
              <p className="text-xs font-bold text-white/65">Follow Noble</p>
              <div className="mt-3 flex flex-wrap gap-2">
                {social.map((s) => (
                  <a
                    key={s.label}
                    href={s.url || undefined}
                    target={s.url ? "_blank" : undefined}
                    rel={s.url ? "noreferrer" : undefined}
                    aria-label={s.label}
                    title={
                      s.url
                        ? s.label
                        : `Configure the official ${s.label} profile URL`
                    }
                    className={`flex h-10 min-w-10 items-center justify-center rounded-full border border-white/20 px-3 text-sm font-black ${s.url ? "text-white hover:border-[#C9A227] hover:text-[#F0C34E]" : "cursor-default text-white/45"}`}
                  >
                    {s.mark}
                  </a>
                ))}
              </div>
            </div>
          </div>
          <div className="rounded-[26px] border border-slate-200 bg-white p-5 shadow-[0_18px_55px_rgba(15,27,61,.08)] sm:p-8">
            <div className="mb-6">
              <p className="text-[10px] font-black uppercase tracking-[.22em] text-[#9A7415]">
                Send an enquiry
              </p>
              <h2 className="mt-2 text-2xl font-black tracking-tight">
                How can we help you?
              </h2>
              <p className="mt-2 text-sm leading-6 text-slate-500">
                Your message goes to the Noble team through our existing enquiry
                system.
              </p>
            </div>
            <ContactQuickForm
              primary={primary}
              accent={accent}
              slug={tenant.slug}
            />
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
