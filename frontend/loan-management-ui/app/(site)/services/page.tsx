"use client";

import Link from "next/link";
import { useTenant } from "../layout";

function amount(value: unknown, currency: string) {
  const n = Number(value);
  if (!Number.isFinite(n)) return "Not specified";
  return new Intl.NumberFormat("en-RW", {
    style: "currency",
    currency,
    maximumFractionDigits: 0,
  }).format(n);
}

export default function ServicesPage() {
  const tenant = useTenant();
  if (!tenant) return null;

  const primary = tenant.primaryColor;
  const accent = tenant.accentColor;
  const currency = tenant.currency || "RWF";
  const services = tenant.services ?? [];

  return (
    <div className="bg-slate-50 pb-20">
      <section
        className="relative overflow-hidden text-white"
        style={{ background: `linear-gradient(135deg, ${primary}, #071427)` }}
      >
        <div className="absolute -right-24 -top-24 h-80 w-80 rounded-full bg-white/10 blur-3xl" />
        <div className="relative mx-auto max-w-7xl px-4 py-20 md:py-24">
          <div className="max-w-4xl">
            <div className="text-[11px] font-black uppercase tracking-[0.24em] text-white/45">
              Lending products
            </div>
            <h1 className="mt-4 text-4xl font-black tracking-tight md:text-6xl">
              Finance with clarity, from {tenant.name}.
            </h1>
            <p className="mt-5 max-w-2xl text-lg leading-8 text-white/65">
              Every product shown here is loaded from the lender's active
              product configuration, including pricing, limits and term.
            </p>
            <div className="mt-8 flex flex-wrap gap-3">
              <Link
                href="/calculator"
                className="rounded-2xl px-6 py-3 text-sm font-black"
                style={{ backgroundColor: accent, color: primary }}
              >
                Calculate a loan
              </Link>
              <Link
                href="/apply"
                className="rounded-2xl border border-white/20 px-6 py-3 text-sm font-bold text-white"
              >
                Apply online
              </Link>
            </div>
          </div>
        </div>
      </section>

      <main className="mx-auto max-w-7xl space-y-7 px-4 pt-10">
        {services.length === 0 ? (
          <section className="rounded-[2rem] border border-slate-200 bg-white p-12 text-center shadow-sm">
            <h2 className="text-2xl font-black text-slate-950">
              No public products are currently published
            </h2>
            <p className="mx-auto mt-3 max-w-xl text-sm leading-6 text-slate-500">
              Please contact {tenant.name} for current loan availability and
              approved product terms.
            </p>
            <Link
              href="/contact"
              className="mt-6 inline-flex rounded-2xl px-6 py-3 text-sm font-black text-white"
              style={{ backgroundColor: primary }}
            >
              Contact the lender
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
                ? `${Number(service.monthlyManagementFeeRate).toFixed(2)}% / month`
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
                className="overflow-hidden rounded-[2rem] border border-slate-200 bg-white shadow-sm"
              >
                <div className="grid lg:grid-cols-[1fr_0.85fr]">
                  <div className="p-7 md:p-9 lg:p-11">
                    <div className="flex items-start justify-between gap-4">
                      <div className="flex items-center gap-4">
                        <div
                          className="flex h-16 w-16 items-center justify-center rounded-2xl text-3xl"
                          style={{ backgroundColor: `${primary}12` }}
                        >
                          {service.icon || "💼"}
                        </div>
                        <div>
                          <div
                            className="text-[10px] font-black uppercase tracking-[0.2em]"
                            style={{ color: accent }}
                          >
                            Product {String(index + 1).padStart(2, "0")}
                          </div>
                          <h2 className="mt-1 text-2xl font-black tracking-tight text-slate-950">
                            {service.title}
                          </h2>
                        </div>
                      </div>
                      <span className="hidden rounded-full bg-slate-100 px-3 py-1.5 text-[10px] font-black uppercase tracking-wider text-slate-400 sm:inline-flex">
                        {tenant.name}
                      </span>
                    </div>

                    <p className="mt-7 max-w-2xl text-base leading-8 text-slate-600">
                      {service.description ||
                        "Product-specific terms are disclosed during the lender's application and approval process."}
                    </p>

                    <div className="mt-8 flex flex-wrap gap-3">
                      <Link
                        href={`/apply?type=${encodeURIComponent(service.title)}`}
                        className="rounded-2xl px-6 py-3.5 text-sm font-black text-white"
                        style={{ backgroundColor: primary }}
                      >
                        Apply for this product
                      </Link>
                      <Link
                        href="/contact"
                        className="rounded-2xl border px-6 py-3.5 text-sm font-bold"
                        style={{ borderColor: primary, color: primary }}
                      >
                        Ask a question
                      </Link>
                    </div>
                  </div>

                  <div className="border-t border-slate-200 bg-slate-50 p-7 md:p-9 lg:border-l lg:border-t-0">
                    <div className="text-[10px] font-black uppercase tracking-[0.2em] text-slate-400">
                      Published terms
                    </div>
                    <div className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-1">
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
                            : "Unlimited / not specified",
                        ],
                      ].map(([label, value]) => (
                        <div
                          key={label}
                          className="rounded-2xl border border-slate-200 bg-white p-4"
                        >
                          <div className="text-[10px] font-black uppercase tracking-wider text-slate-400">
                            {label}
                          </div>
                          <div className="mt-2 text-sm font-black text-slate-900">
                            {value}
                          </div>
                        </div>
                      ))}
                    </div>
                    <p className="mt-5 text-[11px] leading-5 text-slate-400">
                      Final pricing and repayment schedule are determined by the
                      approved loan agreement.
                    </p>
                  </div>
                </div>
              </article>
            );
          })
        )}
      </main>
    </div>
  );
}
