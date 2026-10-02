"use client";

import Link from "next/link";
import Image from "next/image";
import { FormEvent, useMemo, useState } from "react";
import PublicLoanCalculator from "../../components/PublicLoanCalculator";
import { publicApi } from "../../services/api";
import { SITE_CONTENT } from "../../lib/siteContent";
import { useTenant } from "./layout";

const Arrow = () => <span aria-hidden="true">→</span>;
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

function LineIcon({
  kind,
}: {
  kind: "shield" | "clock" | "people" | "handshake" | "mail";
}) {
  const common = {
    viewBox: "0 0 24 24",
    fill: "none",
    stroke: "currentColor",
    strokeWidth: 1.65,
    className: "h-6 w-6",
    "aria-hidden": true as const,
  };
  if (kind === "shield")
    return (
      <svg {...common}>
        <path d="M12 3 20 6v6c0 5-3.5 8.5-8 10-4.5-1.5-8-5-8-10V6l8-3Z" />
        <path d="m9 12 2 2 4-4" />
      </svg>
    );
  if (kind === "clock")
    return (
      <svg {...common}>
        <circle cx="12" cy="12" r="9" />
        <path d="M12 7v5l3 2" />
      </svg>
    );
  if (kind === "people")
    return (
      <svg {...common}>
        <circle cx="9" cy="8" r="3" />
        <path d="M3 20v-1.5A4.5 4.5 0 0 1 7.5 14h3a4.5 4.5 0 0 1 4.5 4.5V20M16 5.5a3 3 0 0 1 0 5.8M18 14.5a4 4 0 0 1 3 3.9V20" />
      </svg>
    );
  if (kind === "handshake")
    return (
      <svg {...common}>
        <path d="m3 10 4-4 4 2 3-1 7 4-3 3-4-2-3 2-4-2-4 2Z" />
        <path d="m8 13 4 3 3-2M7 6l2-2 4 2 3-1 5 3" />
      </svg>
    );
  return (
    <svg {...common}>
      <rect x="3" y="5" width="18" height="14" rx="2" />
      <path d="m3 7 9 6 9-6" />
    </svg>
  );
}

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

export default function HomePage() {
  const tenant = useTenant() || SITE_CONTENT;
  const primary = tenant.primaryColor || "#0F1B3D";
  const accent = tenant.accentColor || "#C9A227";
  const products = tenant.services?.length
    ? tenant.services
    : SITE_CONTENT.services;
  const contactPhone =
    tenant.contactPhone || SITE_CONTENT.contactPhone || "+250 788 123 456";
  const contactEmail =
    tenant.contactEmail ||
    SITE_CONTENT.contactEmail ||
    "info@nobleloansolutions.rw";
  const phoneHref = contactPhone.replace(/[^+\d]/g, "");
  const social = [
    {
      key: "facebook",
      label: "Facebook",
      mark: "f",
      url: tenant.socialMedia?.facebook,
    },
    { key: "twitter", label: "X", mark: "𝕏", url: tenant.socialMedia?.twitter },
    {
      key: "linkedin",
      label: "LinkedIn",
      mark: "in",
      url: tenant.socialMedia?.linkedin,
    },
    {
      key: "youtube",
      label: "YouTube",
      mark: "▶",
      url: tenant.socialMedia?.youtube,
    },
  ];
  const highlights = useMemo(
    () => [
      {
        icon: "shield" as const,
        title: "Clear lending terms",
        body: "Review published rates, fees and repayment periods before you decide.",
      },
      {
        icon: "clock" as const,
        title: "A guided application",
        body: "Understand the steps and what information you may need to provide.",
      },
      {
        icon: "people" as const,
        title: "Real borrower support",
        body: "Reach our team for help with your loan journey and questions.",
      },
      {
        icon: "handshake" as const,
        title: "Your information matters",
        body: "Use the secure application and tracking journeys for borrower tasks.",
      },
    ],
    [],
  );

  return (
    <main className="overflow-hidden bg-[#f5f6f8] text-[#0F1B3D]">
      {/* HERO: photography, transparent overlays and the calculator share the first screen. */}
      <section className="relative isolate overflow-hidden bg-[#061326] text-white">
        <div className="absolute inset-0 -z-20">
          <Image
            src="/images/noble/hero-borrower.jpg"
            alt=""
            fill
            priority
            sizes="100vw"
            className="object-cover object-[62%_center] opacity-100"
          />
          <div className="absolute inset-0 bg-gradient-to-r from-[#041126]/95 via-[#071a32]/72 to-[#071a32]/12" />
          <div className="absolute inset-0 bg-gradient-to-t from-[#061326]/65 via-transparent to-transparent" />
        </div>
        <div className="absolute -left-24 top-16 -z-10 h-72 w-72 rounded-full border border-[#C9A227]/20" />
        <div className="absolute -left-16 top-28 -z-10 h-52 w-52 rounded-full border border-[#C9A227]/15" />
        <div className="mx-auto grid max-w-7xl gap-10 px-5 pb-16 pt-12 sm:px-8 sm:pb-20 sm:pt-16 lg:grid-cols-[.92fr_1.08fr] lg:items-center lg:gap-12 lg:pb-24 lg:pt-20">
          <div className="relative z-10 max-w-2xl">
            <div className="inline-flex items-center gap-2 rounded-full border border-white/15 bg-[#071a32]/55 px-4 py-2 text-[10px] font-black uppercase tracking-[.22em] text-white/80 backdrop-blur-md">
              <span
                className="h-2 w-2 rounded-full"
                style={{ backgroundColor: accent }}
              />
              {tenant.tagline || "Your trusted lending partner"}
            </div>
            <h1 className="mt-6 text-[clamp(2.9rem,5.8vw,5.4rem)] font-black leading-[.97] tracking-[-.065em]">
              Real support.{" "}
              <span className="block" style={{ color: accent }}>
                Bigger dreams.
              </span>
            </h1>
            <p className="mt-6 max-w-xl text-base leading-7 text-white/75 sm:text-lg sm:leading-8">
              Flexible loans for personal needs, business growth, vehicles,
              salary advances and agriculture — with clear terms and support at
              every step.
            </p>
            <div className="mt-7 flex flex-col gap-3 sm:flex-row">
              <Link
                href="/apply"
                className="inline-flex items-center justify-center gap-3 rounded-xl px-6 py-4 text-sm font-black text-[#0F1B3D] shadow-[0_12px_35px_rgba(201,162,39,.22)] transition hover:-translate-y-0.5"
                style={{ backgroundColor: accent }}
              >
                Explore a loan <Arrow />
              </Link>
              <Link
                href="/how-it-works"
                className="inline-flex items-center justify-center gap-2 rounded-xl border border-white/25 bg-white/[.07] px-6 py-4 text-sm font-bold text-white backdrop-blur-md transition hover:bg-white/15"
              >
                How it works <Arrow />
              </Link>
            </div>
            <div className="mt-9 grid max-w-xl grid-cols-3 border-t border-white/20 pt-5">
              <div className="pr-3">
                <div className="text-sm font-black sm:text-base">
                  Clear terms
                </div>
                <div className="mt-1 text-[10px] leading-4 text-white/55 sm:text-xs">
                  Know the fees
                </div>
              </div>
              <div className="border-l border-white/20 px-4">
                <div className="text-sm font-black sm:text-base">
                  1–6 months*
                </div>
                <div className="mt-1 text-[10px] leading-4 text-white/55 sm:text-xs">
                  Published terms
                </div>
              </div>
              <div className="border-l border-white/20 pl-4">
                <div className="text-sm font-black sm:text-base">RWF</div>
                <div className="mt-1 text-[10px] leading-4 text-white/55 sm:text-xs">
                  Rwanda lending
                </div>
              </div>
            </div>
            <p className="mt-3 max-w-lg text-[10px] leading-4 text-white/40">
              *Terms vary by product and are subject to assessment and the final
              loan agreement.
            </p>
          </div>
          <div className="relative lg:pl-3">
            <div className="absolute -inset-3 rounded-[32px] bg-[#C9A227]/15 blur-2xl" />
            <div className="relative rounded-[28px] border border-white/20 bg-[#06172d]/90 p-1 shadow-[0_35px_100px_rgba(0,0,0,.4)] backdrop-blur-xl">
              <div className="rounded-[24px] border border-white/10 bg-gradient-to-br from-white/[.09] to-white/[.025] p-5 sm:p-7">
                <div className="flex items-start justify-between gap-4">
                  <div>
                    <div
                      className="text-[10px] font-black uppercase tracking-[.2em]"
                      style={{ color: accent }}
                    >
                      Noble loan calculator
                    </div>
                    <h2 className="mt-2 text-2xl font-black tracking-[-.04em] sm:text-3xl">
                      Plan your repayments.
                    </h2>
                    <p className="mt-2 max-w-md text-xs leading-5 text-white/55">
                      Explore an indicative estimate using the published product
                      terms.
                    </p>
                  </div>
                  <div className="hidden h-12 w-12 items-center justify-center rounded-2xl border border-[#C9A227]/30 bg-[#C9A227]/10 text-xl font-black text-[#C9A227] sm:flex">
                    ₣
                  </div>
                </div>
                <div className="mt-5">
                  <PublicLoanCalculator
                    products={products}
                    currency={tenant.currency || "RWF"}
                    primary={primary}
                    accent={accent}
                  />
                </div>
                <div className="mt-4 flex items-start gap-2 rounded-xl border border-white/10 bg-white/[.035] p-3 text-[10px] leading-4 text-white/45">
                  <span style={{ color: accent }}>ⓘ</span>
                  <span>
                    Illustrative estimate only. Final eligibility, fees and
                    repayment amounts are confirmed during assessment.
                  </span>
                </div>
              </div>
            </div>
            <div className="absolute -bottom-5 -left-4 hidden rounded-2xl border border-white/15 bg-[#0b2440]/90 px-4 py-3 shadow-xl backdrop-blur-md sm:flex sm:items-center sm:gap-3">
              <span
                className="flex h-9 w-9 items-center justify-center rounded-xl text-[#C9A227]"
                style={{ backgroundColor: `${accent}20` }}
              >
                <LineIcon kind="shield" />
              </span>
              <span>
                <span className="block text-xs font-black">
                  Borrow with clarity
                </span>
                <span className="mt-1 block text-[10px] text-white/50">
                  Terms, fees and steps in view
                </span>
              </span>
            </div>
          </div>
        </div>
        <div className="absolute bottom-0 left-0 right-0 h-px bg-gradient-to-r from-transparent via-[#C9A227]/70 to-transparent" />
      </section>

      {/* LOAN PRODUCTS */}
      <section
        id="loans"
        className="relative bg-gradient-to-b from-[#f9fafb] to-[#edf1f5] py-16 sm:py-20"
      >
        <div className="absolute right-0 top-0 h-48 w-48 rounded-bl-full bg-[#C9A227]/[.06]" />
        <div className="relative mx-auto max-w-7xl px-5 sm:px-8">
          <div className="flex flex-col justify-between gap-5 md:flex-row md:items-end">
            <div>
              <p className="text-[10px] font-black uppercase tracking-[.24em] text-[#B58A18]">
                Our loan products
              </p>
              <h2 className="mt-3 max-w-2xl text-3xl font-black leading-tight tracking-[-.05em] sm:text-4xl lg:text-5xl">
                The right loan for the road ahead.
              </h2>
              <p className="mt-4 max-w-2xl text-sm leading-6 text-slate-600">
                Different goals call for different kinds of support. Explore
                Noble&apos;s lending options and review the terms that apply.
              </p>
            </div>
            <Link
              href="/services"
              className="inline-flex shrink-0 items-center gap-2 text-sm font-black text-[#0F1B3D]"
            >
              View all loan products <Arrow />
            </Link>
          </div>
          <div className="mt-9 grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
            {products.slice(0, 5).map((p, i) => {
              const route =
                productRoutes[String(p.loanType || "").toUpperCase()] ||
                `/apply?type=${encodeURIComponent(p.loanType || p.title)}`;
              return (
                <article
                  key={p.loanType || p.title}
                  className="group overflow-hidden rounded-[22px] border border-slate-200/80 bg-white shadow-[0_8px_28px_rgba(15,27,61,.045)] transition duration-300 hover:-translate-y-1.5 hover:border-[#C9A227]/50 hover:shadow-[0_22px_50px_rgba(15,27,61,.13)]"
                >
                  <Link href={route} className="block">
                    <div className="relative h-36 overflow-hidden bg-[#0F1B3D]">
                      <Image
                        src={productImages[i]}
                        alt={`${p.title} loan product`}
                        fill
                        sizes="(max-width: 640px) 100vw, (max-width: 1024px) 50vw, 20vw"
                        className="object-cover transition duration-500 group-hover:scale-105"
                      />
                      <div className="absolute inset-0 bg-gradient-to-t from-[#07172c]/45 to-transparent" />
                      <span className="absolute bottom-3 left-3 rounded-full border border-white/20 bg-[#07172c]/85 px-3 py-1 text-[9px] font-black uppercase tracking-wider text-white backdrop-blur">
                        Noble lending
                      </span>
                    </div>
                    <div className="p-5">
                      <h3 className="text-sm font-black">{p.title}</h3>
                      <p className="mt-2 min-h-[54px] text-xs leading-5 text-slate-500">
                        {p.description ||
                          "Explore the loan details, eligibility and published repayment terms."}
                      </p>
                      <span className="mt-4 inline-flex items-center gap-2 text-xs font-black text-[#B58A18]">
                        Explore product <Arrow />
                      </span>
                    </div>
                  </Link>
                </article>
              );
            })}
          </div>
        </div>
      </section>

      {/* DARK TRUST BAND */}
      <section className="relative overflow-hidden bg-[#071a32] text-white">
        <div
          className="absolute inset-0 opacity-20"
          style={{
            backgroundImage:
              "linear-gradient(135deg,transparent 48%,rgba(201,162,39,.25) 49%,transparent 50%),radial-gradient(circle at 85% 50%,rgba(201,162,39,.28),transparent 32%)",
            backgroundSize: "56px 56px, auto",
          }}
        />
        <div className="relative mx-auto grid max-w-7xl gap-7 px-5 py-9 sm:px-8 md:grid-cols-2 lg:grid-cols-4">
          {highlights.map((item) => (
            <div
              key={item.title}
              className="flex gap-4 border-b border-white/10 pb-6 last:border-0 md:border-0 md:pb-0"
            >
              <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl border border-[#C9A227]/45 bg-[#C9A227]/10 text-[#E5BD52]">
                <LineIcon kind={item.icon} />
              </span>
              <div>
                <h3 className="text-sm font-black">{item.title}</h3>
                <p className="mt-1.5 text-xs leading-5 text-white/55">
                  {item.body}
                </p>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* HOW IT WORKS — image-backed process band keeps the long page visually rich. */}
      <section className="relative isolate overflow-hidden bg-[#071a32] py-16 text-white sm:py-20">
        <div className="absolute inset-0 -z-20">
          <Image
            src="/images/noble/rwanda-landscape.jpg"
            alt=""
            fill
            sizes="100vw"
            className="object-cover opacity-40"
          />
          <div className="absolute inset-0 bg-gradient-to-r from-[#061326]/95 via-[#071a32]/85 to-[#071a32]/80" />
        </div>
        <div className="absolute -right-24 top-0 h-80 w-80 rounded-full bg-[#C9A227]/[.08] blur-2xl" />
        <div className="relative mx-auto max-w-7xl px-5 sm:px-8">
          <div className="grid gap-10 lg:grid-cols-[.72fr_1.28fr] lg:items-center">
            <div>
              <p className="text-[10px] font-black uppercase tracking-[.24em] text-[#B58A18]">
                How it works
              </p>
              <h2 className="mt-3 text-3xl font-black leading-tight tracking-[-.05em] text-white sm:text-4xl">
                A clear path from enquiry to repayment.
              </h2>
              <p className="mt-4 text-sm leading-7 text-white/70">
                Know what to expect at each stage, from choosing a product to
                reviewing an offer and keeping up with repayments.
              </p>
              <Link
                href="/how-it-works"
                className="mt-6 inline-flex items-center gap-2 rounded-xl bg-[#C9A227] px-5 py-3.5 text-xs font-black text-[#0F1B3D] transition hover:bg-[#e1b941]"
              >
                See the full process <Arrow />
              </Link>
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              {[
                {
                  n: "01",
                  title: "Explore",
                  body: "Compare loan types and read the terms that apply to your needs.",
                },
                {
                  n: "02",
                  title: "Apply",
                  body: "Complete the secure application and provide requested information.",
                },
                {
                  n: "03",
                  title: "Review",
                  body: "Noble assesses your application and communicates next steps.",
                },
                {
                  n: "04",
                  title: "Manage",
                  body: "Keep your reference and follow application or repayment instructions.",
                },
              ].map((step, i) => (
                <div
                  key={step.n}
                  className="relative rounded-2xl border border-white/20 bg-white/[.96] p-6 text-[#0F1B3D] shadow-xl transition hover:-translate-y-1 hover:border-[#C9A227] hover:bg-white"
                >
                  <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-[#0F1B3D] text-sm font-black text-[#E6BE52]">
                    {step.n}
                  </span>
                  <h3 className="mt-5 text-base font-black">{step.title}</h3>
                  <p className="mt-2 text-xs leading-6 text-slate-500">
                    {step.body}
                  </p>
                  {i < 3 && (
                    <span className="absolute right-5 top-7 hidden text-xl text-[#C9A227] sm:block">
                      ↗
                    </span>
                  )}
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      {/* RESOURCES + QUOTE */}
      <section className="bg-[#eef1f5] py-14 sm:py-16">
        <div className="mx-auto grid max-w-7xl gap-6 px-5 sm:px-8 lg:grid-cols-[1fr_1fr]">
          <div className="rounded-[28px] bg-gradient-to-br from-[#0F1B3D] to-[#071326] p-7 text-white sm:p-9">
            <p className="text-[10px] font-black uppercase tracking-[.23em] text-[#DDB449]">
              Borrower resources
            </p>
            <h2 className="mt-3 text-3xl font-black tracking-[-.04em]">
              Make your decision with confidence.
            </h2>
            <p className="mt-4 max-w-lg text-sm leading-7 text-white/60">
              Understand repayments, learn about loan terms and prepare for the
              application process before you commit.
            </p>
            <div className="mt-7 grid grid-cols-2 gap-3">
              {[
                { title: "Loan calculators", href: "/calculators", icon: "▦" },
                { title: "Loan terms", href: "/loan-terms", icon: "≋" },
                { title: "Borrower guide", href: "/learn", icon: "◫" },
                {
                  title: "Frequently asked questions",
                  href: "/faq",
                  icon: "?",
                },
              ].map((tool) => (
                <Link
                  key={tool.href}
                  href={tool.href}
                  className="rounded-xl border border-white/10 bg-white/[.045] p-4 transition hover:border-[#C9A227]/60 hover:bg-white/[.08]"
                >
                  <span className="text-lg text-[#E6BE52]">{tool.icon}</span>
                  <span className="mt-2 block text-xs font-black">
                    {tool.title}
                  </span>
                  <span className="mt-2 block text-[10px] text-white/45">
                    Explore resource →
                  </span>
                </Link>
              ))}
            </div>
          </div>
          <div className="relative overflow-hidden rounded-[28px] border border-white bg-white p-7 shadow-[0_18px_50px_rgba(15,27,61,.06)] sm:p-9">
            <div className="absolute -right-10 -top-10 h-44 w-44 rounded-full bg-[#C9A227]/10" />
            <div className="relative">
              <p className="text-[10px] font-black uppercase tracking-[.23em] text-[#B58A18]">
                Your next step
              </p>
              <h2 className="mt-3 text-3xl font-black tracking-[-.04em]">
                Already started an application?
              </h2>
              <p className="mt-4 text-sm leading-7 text-slate-600">
                Keep your application reference close. You can check its status
                online and follow any instructions shared by the Noble team.
              </p>
              <Link
                href="/track"
                className="mt-6 inline-flex items-center gap-2 rounded-xl border border-[#0F1B3D]/15 bg-[#0F1B3D] px-5 py-3.5 text-xs font-black text-white"
              >
                Track your application <Arrow />
              </Link>
              <div className="mt-8 border-t border-slate-100 pt-6">
                <p className="text-xs font-black">New to Noble?</p>
                <p className="mt-1 text-xs leading-5 text-slate-500">
                  Browse loan products first, then start an application when
                  you&apos;re ready.
                </p>
                <Link
                  href="/apply"
                  className="mt-4 inline-flex items-center gap-2 text-xs font-black text-[#B58A18]"
                >
                  Start a loan application <Arrow />
                </Link>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* HOMEPAGE CONTACT FORM — sends to existing backend/dashboard message flow. */}
      <section
        id="contact"
        className="relative isolate overflow-hidden bg-[#061326] py-16 text-white sm:py-20"
      >
        <div className="absolute inset-0 -z-20">
          <Image
            src="/images/noble/rwanda-landscape.jpg"
            alt=""
            fill
            sizes="100vw"
            className="object-cover opacity-30"
          />
          <div className="absolute inset-0 bg-gradient-to-r from-[#061326]/95 via-[#071a32]/92 to-[#061326]/85" />
        </div>
        <div className="absolute left-0 top-0 h-72 w-72 rounded-full bg-[#C9A227]/[.08] blur-3xl" />
        <div className="relative mx-auto grid max-w-7xl gap-10 px-5 sm:px-8 lg:grid-cols-[.75fr_1.25fr]">
          <div className="pt-2">
            <p className="text-[10px] font-black uppercase tracking-[.24em] text-[#B58A18]">
              Talk to Noble
            </p>
            <h2 className="mt-3 text-3xl font-black leading-tight tracking-[-.05em] sm:text-4xl">
              A question about borrowing? We&apos;re here to help.
            </h2>
            <p className="mt-5 max-w-lg text-sm leading-7 text-white/65">
              Send the team a message about loan products, eligibility,
              applications or repayments. Your enquiry is submitted through
              Noble&apos;s existing contact service for staff to review.
            </p>
            <div className="mt-7 space-y-3">
              {contactPhone && (
                <a
                  href={`tel:${phoneHref}`}
                  className="flex items-center gap-3 rounded-xl border border-slate-200 bg-[#f8f9fb] p-4 transition hover:border-[#C9A227]/60"
                >
                  <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-[#0F1B3D] text-[#E6BE52]">
                    ☎
                  </span>
                  <span>
                    <span className="block text-[9px] font-black uppercase tracking-wider text-slate-400">
                      Call us
                    </span>
                    <span className="mt-1 block text-sm font-black">
                      {contactPhone}
                    </span>
                  </span>
                </a>
              )}
              {contactEmail && (
                <a
                  href={`mailto:${contactEmail}`}
                  className="flex items-center gap-3 rounded-xl border border-slate-200 bg-[#f8f9fb] p-4 transition hover:border-[#C9A227]/60"
                >
                  <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-[#0F1B3D] text-[#E6BE52]">
                    <LineIcon kind="mail" />
                  </span>
                  <span>
                    <span className="block text-[9px] font-black uppercase tracking-wider text-slate-400">
                      Email us
                    </span>
                    <span className="mt-1 block break-all text-sm font-black">
                      {contactEmail}
                    </span>
                  </span>
                </a>
              )}
              {tenant.address && (
                <p className="text-xs text-white/55">
                  ⌖ {tenant.address || "Kigali, Rwanda"}
                </p>
              )}
            </div>
            <div className="mt-6">
              <p className="text-[10px] font-black uppercase tracking-[.18em] text-white/45">
                Follow Noble
              </p>
              <div className="mt-3 flex flex-wrap gap-2">
                {social.map((item) =>
                  item.url ? (
                    <a
                      key={item.key}
                      href={item.url}
                      target="_blank"
                      rel="noreferrer"
                      aria-label={`Noble on ${item.label}`}
                      title={item.label}
                      className="flex h-10 w-10 items-center justify-center rounded-xl border border-white/20 bg-white/[.06] text-sm font-black text-white transition hover:border-[#C9A227] hover:bg-[#C9A227]/15"
                    >
                      {item.mark}
                    </a>
                  ) : (
                    <span
                      key={item.key}
                      aria-label={`${item.label} URL not configured`}
                      title={`${item.label}: configure the official profile URL in the public site environment`}
                      className="flex h-10 w-10 cursor-default items-center justify-center rounded-xl border border-white/10 bg-white/[.025] text-sm font-black text-white/40"
                    >
                      {item.mark}
                    </span>
                  ),
                )}
              </div>
            </div>
          </div>
          <div className="rounded-[28px] border border-slate-200 bg-[#f8f9fb] p-5 shadow-[0_25px_75px_rgba(15,27,61,.08)] sm:p-8">
            <div className="flex flex-col justify-between gap-3 border-b border-slate-200 pb-5 sm:flex-row sm:items-end">
              <div>
                <p className="text-[10px] font-black uppercase tracking-[.2em] text-[#B58A18]">
                  Send an enquiry
                </p>
                <h3 className="mt-2 text-2xl font-black tracking-[-.04em]">
                  How can we help?
                </h3>
              </div>
              <span className="rounded-full border border-[#C9A227]/30 bg-[#C9A227]/10 px-3 py-1.5 text-[9px] font-black uppercase tracking-wider text-[#80600e]">
                Noble customer support
              </span>
            </div>
            <div className="pt-6">
              <ContactQuickForm
                primary={primary}
                accent={accent}
                slug={tenant.slug}
              />
            </div>
          </div>
        </div>
      </section>

      {/* FINAL CTA */}
      <section className="bg-[#071a32] px-5 py-12 text-white sm:px-8">
        <div className="mx-auto flex max-w-7xl flex-col justify-between gap-6 md:flex-row md:items-center">
          <div>
            <p className="text-[10px] font-black uppercase tracking-[.23em] text-[#E6BE52]">
              When you&apos;re ready
            </p>
            <h2 className="mt-2 text-3xl font-black tracking-[-.04em] sm:text-4xl">
              Let&apos;s take the next step together.
            </h2>
            <p className="mt-3 max-w-2xl text-sm leading-6 text-white/55">
              Review your options, understand the terms and apply when the time
              is right for you.
            </p>
          </div>
          <div className="flex flex-col gap-3 sm:flex-row">
            <Link
              href="/apply"
              className="rounded-xl px-6 py-4 text-center text-sm font-black text-[#0F1B3D]"
              style={{ backgroundColor: accent }}
            >
              Apply for a loan <Arrow />
            </Link>
            <Link
              href="/track"
              className="rounded-xl border border-white/20 px-6 py-4 text-center text-sm font-black text-white transition hover:bg-white/10"
            >
              Track application
            </Link>
          </div>
        </div>
      </section>
    </main>
  );
}
