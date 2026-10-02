"use client";

import Image from "next/image";
import Link from "next/link";
import { useTenant } from "../app/(site)/layout";
import { pageContent } from "../lib/websiteContent";

const productImages: Record<string, string> = {
  PERSONAL: "/personal-loan.jpg",
  BUSINESS: "/business-loan.jpg",
  AUTO: "/vehicle-loan.jpg",
  SALARY_ADVANCE: "/salary-advance.jpg",
  AGRICULTURAL: "/agriculture-loan.jpg",
};

const productAudience: Record<string, { title: string; body: string }> = {
  PERSONAL: {
    title: "Personal needs",
    body: "For approved individual and household needs where a structured repayment plan is appropriate.",
  },
  BUSINESS: {
    title: "Business activity",
    body: "For eligible working-capital and business-growth needs, subject to the applicable assessment.",
  },
  AUTO: {
    title: "Vehicle purchase",
    body: "For an approved vehicle financing need with the amount and repayment terms confirmed through the application process.",
  },
  SALARY_ADVANCE: {
    title: "Verified salary income",
    body: "For eligible short-term borrowing needs supported by verified salary income.",
  },
  AGRICULTURAL: {
    title: "Agriculture & agribusiness",
    body: "For approved agricultural and agribusiness activities within Noble's active lending policy.",
  },
};

function valueOrDash(value: unknown) {
  return value === null || value === undefined || value === ""
    ? "—"
    : String(value);
}

function amount(currency: string, value: unknown) {
  if (value === null || value === undefined || value === "")
    return "No stated limit";
  const n = Number(String(value).replace(/[^0-9.-]/g, ""));
  return Number.isFinite(n)
    ? `${currency} ${n.toLocaleString("en-RW", { maximumFractionDigits: 0 })}`
    : "No stated limit";
}

export default function PublicProductPage({
  type,
  title,
  description,
  pageKey,
}: {
  type: string;
  title?: string;
  description?: string;
  pageKey?: string;
}) {
  const tenant = useTenant();
  if (!tenant) return null;

  const normalizedType = type.toUpperCase();
  const cms = pageKey ? pageContent(tenant.websiteContent, pageKey) : undefined;
  const product =
    tenant.services.find((x) => x.loanType === normalizedType) ||
    tenant.services.find((x) =>
      x.title.toLowerCase().includes(type.toLowerCase()),
    );
  const resolvedTitle =
    cms?.title?.trim() || title || product?.title || "Noble loan solution";
  const resolvedDescription =
    cms?.description?.trim() ||
    description ||
    product?.description ||
    "Explore the applicable Noble lending product and review the terms before you apply.";
  const resolvedEyebrow = cms?.eyebrow?.trim() || "Noble lending";
  const resolvedSections = cms?.sections?.length ? cms.sections : [];
  const primary = tenant.primaryColor || "#0F1B3D";
  const accent = tenant.accentColor || "#C9A227";
  const image = productImages[normalizedType] || "/hero-borrower.jpg";
  const audience = productAudience[normalizedType] || {
    title: "Your borrowing need",
    body: "Review the active product terms and eligibility requirements shown through Noble's application journey.",
  };

  const related = tenant.services
    .filter((item) => item !== product)
    .slice(0, 3);

  if (cms?.visible === false) {
    return (
      <main className="min-h-[60vh] bg-[#f5f7fa] px-5 py-20 text-slate-950 sm:px-8">
        <div className="mx-auto max-w-3xl rounded-[32px] border border-slate-200 bg-white p-10 text-center shadow-sm">
          <p
            className="text-[10px] font-black uppercase tracking-[.22em]"
            style={{ color: accent }}
          >
            {resolvedEyebrow}
          </p>
          <h1 className="mt-4 text-3xl font-black tracking-tight sm:text-5xl">
            This product page is currently unavailable.
          </h1>
          <p className="mt-4 text-sm leading-7 text-slate-500">
            Please view Noble&apos;s current loan catalogue or contact the team
            for current information.
          </p>
          <Link
            href="/services"
            className="mt-7 inline-flex rounded-2xl px-5 py-3 text-sm font-black text-white"
            style={{ backgroundColor: primary }}
          >
            View current loans →
          </Link>
        </div>
      </main>
    );
  }

  return (
    <main className="overflow-hidden bg-[#f5f7fa] text-slate-950">
      <section
        className="relative overflow-hidden text-white"
        style={{
          background: `linear-gradient(120deg,#061326 0%,${primary} 62%,#1a416b 100%)`,
        }}
      >
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_84%_15%,rgba(201,162,39,.25),transparent_24%)]" />
        <div className="relative mx-auto grid max-w-7xl gap-10 px-5 py-14 sm:px-8 sm:py-20 lg:grid-cols-[1.05fr_.95fr] lg:items-center lg:py-24">
          <div>
            <p
              className="text-[10px] font-black uppercase tracking-[.24em]"
              style={{ color: accent }}
            >
              {resolvedEyebrow}
            </p>
            <h1 className="mt-5 max-w-3xl text-4xl font-black leading-[.98] tracking-[-.06em] sm:text-6xl">
              {resolvedTitle}
            </h1>
            <p className="mt-6 max-w-2xl text-base leading-8 text-white/70 sm:text-lg">
              {resolvedDescription}
            </p>
            <div className="mt-8 flex flex-col gap-3 sm:flex-row">
              <Link
                href={`/apply?type=${encodeURIComponent(product?.loanType || normalizedType)}`}
                className="rounded-2xl px-6 py-4 text-center text-sm font-black text-slate-950 shadow-lg transition hover:-translate-y-0.5"
                style={{ backgroundColor: accent }}
              >
                Apply for this solution →
              </Link>
              <Link
                href="/loan-calculator"
                className="rounded-2xl border border-white/15 bg-white/[.05] px-6 py-4 text-center text-sm font-bold text-white"
              >
                Calculate repayment
              </Link>
            </div>
          </div>
          <div className="relative h-[320px] overflow-hidden rounded-[32px] border border-white/15 shadow-[0_30px_100px_rgba(0,0,0,.25)] sm:h-[390px]">
            <Image
              src={image}
              alt=""
              fill
              sizes="(max-width: 1024px) 100vw, 46vw"
              className="object-cover"
              priority
            />
            <div className="absolute inset-0 bg-gradient-to-t from-[#071a32]/70 via-transparent to-transparent" />
            <div className="absolute bottom-5 left-5 right-5 rounded-2xl border border-white/15 bg-[#061326]/55 p-4 backdrop-blur-md">
              <div
                className="text-[9px] font-black uppercase tracking-[.18em]"
                style={{ color: accent }}
              >
                Designed around the need
              </div>
              <div className="mt-1 text-sm font-bold text-white">
                {audience.title}
              </div>
            </div>
          </div>
        </div>
      </section>

      <section className="border-b border-slate-200 bg-white">
        <div className="mx-auto grid max-w-7xl gap-px px-5 sm:grid-cols-2 sm:px-8 lg:grid-cols-4">
          {[
            [
              "Interest",
              `${valueOrDash(product?.interestRate ?? product?.rate)}% ${product?.rateType || "monthly"}`,
            ],
            ["Application fee", `${valueOrDash(product?.applicationFeeRate)}%`],
            ["Management fee", `${valueOrDash(product?.managementFeeRate)}%`],
            ["Repayment term", product?.term || "Review applicable terms"],
          ].map(([label, value]) => (
            <div
              key={label}
              className="border-b border-slate-100 p-6 last:border-b-0 sm:border-r lg:border-b-0 lg:p-7 lg:last:border-r-0"
            >
              <div className="text-[9px] font-black uppercase tracking-[.16em] text-slate-400">
                {label}
              </div>
              <div
                className="mt-2 text-base font-black"
                style={{ color: primary }}
              >
                {value}
              </div>
            </div>
          ))}
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-5 py-14 sm:px-8 sm:py-20">
        <div className="grid gap-5 lg:grid-cols-3">
          <article className="rounded-[28px] border border-slate-200 bg-white p-7 shadow-sm sm:p-8">
            <span
              className="text-[10px] font-black uppercase tracking-[.18em]"
              style={{ color: accent }}
            >
              01 · Who it serves
            </span>
            <h2 className="mt-4 text-xl font-black">
              {resolvedSections[0]?.title || audience.title}
            </h2>
            <p className="mt-3 text-sm leading-7 text-slate-600">
              {resolvedSections[0]?.body || audience.body}
            </p>
          </article>
          <article className="rounded-[28px] border border-slate-200 bg-white p-7 shadow-sm sm:p-8">
            <span
              className="text-[10px] font-black uppercase tracking-[.18em]"
              style={{ color: accent }}
            >
              02 · Loan range
            </span>
            <h2 className="mt-4 text-xl font-black">
              {resolvedSections[1]?.title || "Understand the amount"}
            </h2>
            <p className="mt-3 text-sm leading-7 text-slate-600">
              {resolvedSections[1]?.body ? `${resolvedSections[1].body} ` : ""}
              Minimum:{" "}
              <strong className="text-slate-800">
                {amount(tenant.currency, product?.minAmount)}
              </strong>
              . Maximum:{" "}
              <strong className="text-slate-800">
                {amount(tenant.currency, product?.maxAmount)}
              </strong>
              .
            </p>
            <p className="mt-3 text-xs leading-6 text-slate-400">
              A requested amount is not a promise of approval. The approved
              amount is determined through assessment.
            </p>
          </article>
          <article className="rounded-[28px] border border-slate-200 bg-white p-7 shadow-sm sm:p-8">
            <span
              className="text-[10px] font-black uppercase tracking-[.18em]"
              style={{ color: accent }}
            >
              03 · Before you apply
            </span>
            <h2 className="mt-4 text-xl font-black">
              {resolvedSections[2]?.title || "Plan the repayment"}
            </h2>
            <p className="mt-3 text-sm leading-7 text-slate-600">
              {resolvedSections[2]?.body ||
                "Use the calculator to model an indicative schedule, then review the exact fees, dates and obligations shown in the application and agreement."}
            </p>
          </article>
        </div>

        <div className="mt-8 grid gap-5 lg:grid-cols-[.9fr_1.1fr]">
          <div
            className="rounded-[30px] p-8 text-white sm:p-10"
            style={{ background: `linear-gradient(145deg,${primary},#061326)` }}
          >
            <div
              className="text-[10px] font-black uppercase tracking-[.18em]"
              style={{ color: accent }}
            >
              Your application path
            </div>
            <h2 className="mt-3 text-2xl font-black">
              Simple steps. Clear hand-offs.
            </h2>
            <ol className="mt-7 space-y-5">
              {[
                "Choose the product and decide the amount you are comfortable repaying.",
                "Complete the secure application and provide the information requested.",
                "Review the assessment outcome and any loan terms presented to you.",
                "Keep your application reference so you can track progress online.",
              ].map((step, index) => (
                <li key={step} className="flex gap-4">
                  <span
                    className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-xs font-black text-slate-950"
                    style={{ backgroundColor: accent }}
                  >
                    {index + 1}
                  </span>
                  <span className="pt-0.5 text-sm leading-6 text-white/70">
                    {step}
                  </span>
                </li>
              ))}
            </ol>
          </div>

          <div className="rounded-[30px] border border-slate-200 bg-white p-8 shadow-sm sm:p-10">
            <div
              className="text-[10px] font-black uppercase tracking-[.18em]"
              style={{ color: accent }}
            >
              Important information
            </div>
            <h2 className="mt-3 text-2xl font-black">
              The published terms guide the decision.
            </h2>
            <p className="mt-4 text-sm leading-7 text-slate-600">
              Rates, fees, amounts and repayment periods shown on this page come
              from Noble's configured public product data. They are provided for
              transparency and planning; final eligibility, approval,
              disbursement and contractual repayment obligations are determined
              through the application assessment and signed agreement.
            </p>
            <div className="mt-6 grid gap-3 sm:grid-cols-2">
              {[
                "Review total repayment",
                "Confirm your repayment dates",
                "Understand every applicable fee",
                "Keep your application reference",
              ].map((item) => (
                <div
                  key={item}
                  className="flex gap-3 rounded-2xl bg-slate-50 p-4 text-xs font-bold text-slate-700"
                >
                  <span
                    className="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full"
                    style={{ backgroundColor: `${accent}20`, color: primary }}
                  >
                    ✓
                  </span>
                  {item}
                </div>
              ))}
            </div>
            <div className="mt-7 flex flex-col gap-3 sm:flex-row">
              <Link
                href={`/apply?type=${encodeURIComponent(product?.loanType || normalizedType)}`}
                className="rounded-2xl px-5 py-3.5 text-center text-sm font-black text-white"
                style={{ backgroundColor: primary }}
              >
                Start application →
              </Link>
              <Link
                href="/contact"
                className="rounded-2xl border border-slate-200 px-5 py-3.5 text-center text-sm font-bold"
                style={{ color: primary }}
              >
                Ask Noble a question
              </Link>
            </div>
          </div>
        </div>
      </section>

      {related.length > 0 && (
        <section className="border-t border-slate-200 bg-white py-14 sm:py-20">
          <div className="mx-auto max-w-7xl px-5 sm:px-8">
            <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-end">
              <div>
                <p
                  className="text-[10px] font-black uppercase tracking-[.2em]"
                  style={{ color: accent }}
                >
                  More Noble solutions
                </p>
                <h2 className="mt-2 text-3xl font-black tracking-[-.04em]">
                  Explore another financing option.
                </h2>
              </div>
              <Link
                href="/services"
                className="text-sm font-black"
                style={{ color: primary }}
              >
                View all solutions →
              </Link>
            </div>
            <div className="mt-8 grid gap-4 md:grid-cols-3">
              {related.map((item) => (
                <Link
                  key={item.title}
                  href={`/apply?type=${encodeURIComponent(item.loanType || item.title)}`}
                  className="rounded-[24px] border border-slate-200 bg-slate-50 p-6 transition hover:-translate-y-1 hover:bg-white hover:shadow-xl"
                >
                  <div className="text-xl">{item.icon || "◈"}</div>
                  <h3 className="mt-4 text-base font-black">{item.title}</h3>
                  <p className="mt-2 text-xs leading-6 text-slate-500">
                    {item.description}
                  </p>
                </Link>
              ))}
            </div>
          </div>
        </section>
      )}
    </main>
  );
}
