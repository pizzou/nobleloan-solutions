"use client";

import Link from "next/link";
import { useTenant } from "../layout";

function amount(value: unknown, currency: string) {
  const n = Number(value);

  if (!Number.isFinite(n)) {
    return "Not specified";
  }

  return new Intl.NumberFormat("en-RW", {
    style: "currency",
    currency,
    maximumFractionDigits: 0,
  }).format(n);
}

function SectionEyebrow({
  children,
  color,
}: {
  children: React.ReactNode;
  color: string;
}) {
  return (
    <div
      className="text-[10px] font-black uppercase tracking-[0.24em]"
      style={{ color }}
    >
      {children}
    </div>
  );
}

export default function ServicesPage() {
  const tenant = useTenant();

  if (!tenant) return null;

  const primary = tenant.primaryColor;
  const accent = tenant.accentColor;
  const currency = tenant.currency || "RWF";
  const services = tenant.services ?? [];

  return (
    <div className="bg-white pb-24">
      {/* HERO */}
      <section
        className="relative overflow-hidden text-white"
        style={{
          background: `linear-gradient(135deg, ${primary}, #071426)`,
        }}
      >
        <div
          className="absolute -right-32 -top-32 h-[450px] w-[450px] rounded-full blur-3xl"
          style={{
            backgroundColor: `${accent}18`,
          }}
        />

        <div
          className="absolute inset-0 opacity-[0.04]"
          style={{
            backgroundImage: `
              linear-gradient(#fff 1px, transparent 1px),
              linear-gradient(90deg, #fff 1px, transparent 1px)
            `,
            backgroundSize: "55px 55px",
          }}
        />

        <div className="relative mx-auto max-w-7xl px-4 py-20 md:py-28">
          <div className="max-w-4xl">
            <SectionEyebrow color={`${accent}`}>
              Lending solutions
            </SectionEyebrow>

            <h1 className="mt-5 text-5xl font-black leading-[0.98] tracking-[-0.05em] md:text-7xl">
              Financing with clarity, from {tenant.name}.
            </h1>

            <p className="mt-7 max-w-2xl text-lg leading-8 text-white/60">
              Explore the lending products currently published by {tenant.name}.
              Product information, limits and pricing are presented directly
              from the lender&apos;s configuration.
            </p>

            <div className="mt-9 flex flex-wrap gap-3">
              <Link
                href="/calculator"
                className="rounded-xl px-6 py-4 text-sm font-black"
                style={{
                  backgroundColor: accent,
                  color: primary,
                }}
              >
                Calculate a Loan
              </Link>

              <Link
                href="/apply"
                className="rounded-xl border border-white/15 px-6 py-4 text-sm font-black text-white"
              >
                Apply Online
              </Link>
            </div>
          </div>
        </div>
      </section>

      {/* INTRODUCTION */}
      <section className="mx-auto max-w-7xl px-4 py-16 md:py-20">
        <div className="grid gap-8 lg:grid-cols-[0.75fr_1.25fr]">
          <div>
            <SectionEyebrow color={accent}>
              Our financing approach
            </SectionEyebrow>

            <h2 className="mt-4 text-3xl font-black tracking-tight text-slate-950 md:text-4xl">
              Solutions built around real financial needs.
            </h2>
          </div>

          <div className="grid gap-4 sm:grid-cols-3">
            {[
              [
                "01",
                "Clear terms",
                "Review published pricing and product limits before applying.",
              ],
              [
                "02",
                "Structured process",
                "Applications move through the lender's established review workflow.",
              ],
              [
                "03",
                "Ongoing service",
                "Customers retain access to servicing and support after approval.",
              ],
            ].map(([number, title, text]) => (
              <div
                key={number}
                className="border-l-2 bg-slate-50 p-5"
                style={{
                  borderColor: accent,
                }}
              >
                <div
                  className="text-[10px] font-black"
                  style={{ color: accent }}
                >
                  {number}
                </div>

                <div className="mt-3 text-sm font-black text-slate-950">
                  {title}
                </div>

                <p className="mt-2 text-xs leading-6 text-slate-500">{text}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* PRODUCTS */}
      <main className="mx-auto max-w-7xl space-y-7 px-4">
        {services.length === 0 ? (
          <section className="rounded-[2rem] border border-slate-200 bg-white p-12 text-center shadow-sm">
            <div
              className="mx-auto flex h-16 w-16 items-center justify-center rounded-2xl"
              style={{
                backgroundColor: `${accent}18`,
                color: primary,
              }}
            >
              ◈
            </div>

            <h2 className="mt-6 text-2xl font-black text-slate-950">
              No public products are currently published
            </h2>

            <p className="mx-auto mt-3 max-w-xl text-sm leading-7 text-slate-500">
              Please contact {tenant.name} for current loan availability and
              approved product terms.
            </p>

            <Link
              href="/contact"
              className="mt-7 inline-flex rounded-xl px-6 py-3.5 text-sm font-black text-white"
              style={{
                backgroundColor: primary,
              }}
            >
              Contact the Lender
            </Link>
          </section>
        ) : (
          services.map((service, index) => {
            const interest =
              service.monthlyInterestRate != null
                ? `${Number(service.monthlyInterestRate).toFixed(2)}% / month`
                : service.rate || "Published at application";

            const management =
              service.monthlyManagementFeeRate != null
                ? `${Number(service.monthlyManagementFeeRate).toFixed(
                    2,
                  )}% / month`
                : "Published at application";

            const processing =
              service.processingFeeRate != null
                ? `${Number(service.processingFeeRate).toFixed(2)}% once`
                : "Published at application";

            const minTerm = service.minTermMonths ?? null;

            const maxTerm = service.maxTermMonths ?? null;

            const term =
              service.term ||
              (minTerm != null && maxTerm != null
                ? `${minTerm}–${maxTerm} months`
                : "See approved offer");

            return (
              <article
                key={service.id ?? service.title}
                className="group overflow-hidden rounded-[2rem] border border-slate-200 bg-white shadow-sm transition duration-300 hover:-translate-y-1 hover:shadow-2xl"
              >
                <div className="grid lg:grid-cols-[1fr_0.72fr]">
                  <div className="p-7 md:p-10 lg:p-12">
                    <div className="flex items-start justify-between gap-5">
                      <div className="flex items-center gap-4">
                        <div
                          className="flex h-16 w-16 items-center justify-center rounded-2xl text-2xl"
                          style={{
                            backgroundColor: `${primary}10`,
                            color: primary,
                          }}
                        >
                          {service.icon || "◈"}
                        </div>

                        <div>
                          <div
                            className="text-[9px] font-black uppercase tracking-[0.22em]"
                            style={{
                              color: accent,
                            }}
                          >
                            Product {String(index + 1).padStart(2, "0")}
                          </div>

                          <h2 className="mt-1 text-2xl font-black tracking-tight text-slate-950 md:text-3xl">
                            {service.title}
                          </h2>
                        </div>
                      </div>
                    </div>

                    <p className="mt-8 max-w-2xl text-base leading-8 text-slate-600">
                      {service.description ||
                        "Product-specific terms are disclosed during the lender's application and approval process."}
                    </p>

                    <div className="mt-9 flex flex-wrap gap-3">
                      <Link
                        href={`/apply?type=${encodeURIComponent(
                          service.title,
                        )}`}
                        className="rounded-xl px-6 py-4 text-sm font-black text-white shadow-sm transition hover:-translate-y-0.5 hover:shadow-lg"
                        style={{
                          backgroundColor: primary,
                        }}
                      >
                        Apply for this Product
                      </Link>

                      <Link
                        href="/contact"
                        className="rounded-xl border border-slate-300 px-6 py-4 text-sm font-bold text-slate-800 transition hover:bg-slate-50"
                      >
                        Ask a Question
                      </Link>
                    </div>
                  </div>

                  <div className="border-t border-slate-200 bg-[#f7f8fa] p-7 md:p-10 lg:border-l lg:border-t-0">
                    <div className="text-[9px] font-black uppercase tracking-[0.22em] text-slate-400">
                      Published product terms
                    </div>

                    <div className="mt-5 space-y-3">
                      {[
                        ["Interest", interest],
                        ["Management fee", management],
                        ["Processing fee", processing],
                        ["Term", term],
                        [
                          "Minimum amount",
                          service.minAmount != null
                            ? amount(service.minAmount, currency)
                            : "See offer",
                        ],
                        [
                          "Maximum amount",
                          service.maxAmount != null
                            ? amount(service.maxAmount, currency)
                            : "Not specified",
                        ],
                      ].map(([label, value]) => (
                        <div
                          key={label}
                          className="rounded-xl border border-slate-200 bg-white p-4"
                        >
                          <div className="text-[9px] font-black uppercase tracking-wider text-slate-400">
                            {label}
                          </div>

                          <div className="mt-2 text-sm font-black text-slate-950">
                            {value}
                          </div>
                        </div>
                      ))}
                    </div>

                    <div
                      className="mt-5 rounded-xl border p-4 text-xs leading-6"
                      style={{
                        borderColor: `${accent}40`,
                        backgroundColor: `${accent}08`,
                        color: "#64748b",
                      }}
                    >
                      Final pricing, approval and repayment schedule are
                      determined by the approved loan agreement.
                    </div>
                  </div>
                </div>
              </article>
            );
          })
        )}
      </main>

      {/* HOW IT WORKS */}
      <section className="mx-auto max-w-7xl px-4 pt-20 md:pt-28">
        <div className="rounded-[2rem] bg-[#f7f8fa] p-8 md:p-12">
          <SectionEyebrow color={accent}>How financing works</SectionEyebrow>

          <h2 className="mt-4 text-3xl font-black tracking-tight text-slate-950 md:text-4xl">
            From first enquiry to ongoing servicing.
          </h2>

          <div className="mt-10 grid gap-4 md:grid-cols-4">
            {[
              ["01", "Choose", "Review the financing solutions available."],
              ["02", "Apply", "Submit your information securely."],
              ["03", "Review", "Your application is assessed by the lender."],
              [
                "04",
                "Service",
                "Manage your approved facility and repayments.",
              ],
            ].map(([number, title, text]) => (
              <div
                key={number}
                className="rounded-2xl border border-slate-200 bg-white p-6"
              >
                <div
                  className="text-[10px] font-black tracking-[0.2em]"
                  style={{ color: accent }}
                >
                  {number}
                </div>

                <h3 className="mt-5 text-base font-black text-slate-950">
                  {title}
                </h3>

                <p className="mt-2 text-xs leading-6 text-slate-500">{text}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* CTA */}
      <section className="mx-auto max-w-7xl px-4 pt-8">
        <div
          className="rounded-[2rem] p-8 text-white md:p-12"
          style={{
            background: `linear-gradient(135deg, ${primary}, #071426)`,
          }}
        >
          <div className="flex flex-col justify-between gap-8 md:flex-row md:items-center">
            <div>
              <SectionEyebrow color={accent}>
                Continue your journey
              </SectionEyebrow>

              <h2 className="mt-3 text-3xl font-black tracking-tight md:text-4xl">
                Found the right financing solution?
              </h2>

              <p className="mt-3 max-w-2xl text-sm leading-7 text-white/55">
                Start your application or speak with {tenant.name}
                before proceeding.
              </p>
            </div>

            <div className="flex flex-wrap gap-3">
              <Link
                href="/apply"
                className="rounded-xl px-6 py-4 text-sm font-black"
                style={{
                  backgroundColor: accent,
                  color: primary,
                }}
              >
                Apply Now
              </Link>

              <Link
                href="/contact"
                className="rounded-xl border border-white/15 px-6 py-4 text-sm font-bold text-white"
              >
                Contact Us
              </Link>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
