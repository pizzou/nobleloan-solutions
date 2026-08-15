"use client";

import Link from "next/link";
import { useTenant } from "../layout";

export default function ServicesPage() {
  const tenant = useTenant();
  if (!tenant) return null;

  const primary = tenant.primaryColor;
  const accent = tenant.accentColor;
  const services = tenant.services ?? [];

  return (
    <div>
      <section
        className="text-white"
        style={{
          background: `linear-gradient(135deg, ${primary}, ${primary}D9)`,
        }}
      >
        <div className="mx-auto max-w-4xl px-4 py-20 text-center md:py-24">
          <div className="text-xs font-black uppercase tracking-[0.2em] text-white/60">
            Services
          </div>
          <h1 className="mt-4 text-4xl font-black md:text-6xl">
            Loan products available from {tenant.name}
          </h1>
          <p className="mx-auto mt-5 max-w-2xl text-lg leading-8 text-white/75">
            Rates, terms, limits, and descriptions below are loaded from the
            lender's published product configuration.
          </p>
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-4 py-20">
        {services.length === 0 ? (
          <div className="rounded-3xl border border-slate-200 bg-slate-50 p-10 text-center">
            <h2 className="text-2xl font-black">
              No public products are currently published
            </h2>
            <p className="mt-3 text-sm text-slate-500">
              Please contact {tenant.name} for current loan availability.
            </p>
            <Link
              href="/contact"
              className="mt-6 inline-flex rounded-full px-6 py-3 text-sm font-bold text-white"
              style={{ backgroundColor: primary }}
            >
              Contact Us
            </Link>
          </div>
        ) : (
          <div className="space-y-8">
            {services.map((service, index) => (
              <article
                key={service.title}
                className="grid gap-8 rounded-3xl border border-slate-200 bg-white p-7 shadow-sm md:grid-cols-[1fr_0.9fr] md:p-10"
              >
                <div>
                  <div className="flex items-center gap-4">
                    <div
                      className="flex h-16 w-16 items-center justify-center rounded-2xl text-3xl"
                      style={{ backgroundColor: `${primary}12` }}
                    >
                      {service.icon || "💼"}
                    </div>
                    <div>
                      <div
                        className="text-xs font-black uppercase tracking-[0.18em]"
                        style={{ color: accent }}
                      >
                        Product {String(index + 1).padStart(2, "0")}
                      </div>
                      <h2 className="mt-1 text-2xl font-black text-slate-950">
                        {service.title}
                      </h2>
                    </div>
                  </div>
                  <p className="mt-6 text-base leading-8 text-slate-600">
                    {service.description ||
                      "Product-specific terms are provided during the application and approval process."}
                  </p>
                  <div className="mt-7 flex flex-wrap gap-3">
                    <Link
                      href={`/apply?type=${encodeURIComponent(service.title)}`}
                      className="rounded-full px-7 py-3 text-sm font-black text-white"
                      style={{ backgroundColor: primary }}
                    >
                      Apply for {service.title}
                    </Link>
                    <Link
                      href="/contact"
                      className="rounded-full border px-7 py-3 text-sm font-bold"
                      style={{ borderColor: primary, color: primary }}
                    >
                      Ask a question
                    </Link>
                  </div>
                </div>

                <div className="rounded-2xl bg-slate-50 p-6">
                  <div className="grid grid-cols-2 gap-4">
                    <div className="rounded-xl bg-white p-4">
                      <div className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
                        Interest rate
                      </div>
                      <div
                        className="mt-2 text-lg font-black"
                        style={{ color: primary }}
                      >
                        {service.rate || "See loan offer"}
                      </div>
                    </div>
                    <div className="rounded-xl bg-white p-4">
                      <div className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
                        Term
                      </div>
                      <div
                        className="mt-2 text-lg font-black"
                        style={{ color: primary }}
                      >
                        {service.term || "See loan offer"}
                      </div>
                    </div>
                    <div className="rounded-xl bg-white p-4">
                      <div className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
                        Maximum amount
                      </div>
                      <div
                        className="mt-2 text-lg font-black"
                        style={{ color: primary }}
                      >
                        {service.maxAmount || "See loan offer"}
                      </div>
                    </div>
                    <div className="rounded-xl bg-white p-4">
                      <div className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
                        Management fee
                      </div>
                      <div
                        className="mt-2 text-lg font-black"
                        style={{ color: primary }}
                      >
                        {tenant.monthlyManagementFeeRate != null
                          ? `${tenant.monthlyManagementFeeRate}% / month`
                          : "See loan offer"}
                      </div>
                    </div>
                  </div>
                  <div className="mt-6 border-t border-slate-200 pt-5 text-xs leading-6 text-slate-500">
                    Final pricing, eligibility, documentation, and approval are
                    determined under the lender's applicable credit policy and
                    individual loan agreement.
                  </div>
                </div>
              </article>
            ))}
          </div>
        )}
      </section>

      <section className="px-4 pb-20">
        <div
          className="mx-auto max-w-4xl rounded-3xl p-10 text-center"
          style={{ backgroundColor: `${primary}08` }}
        >
          <h2 className="text-3xl font-black text-slate-950">
            Need help choosing a product?
          </h2>
          <p className="mx-auto mt-3 max-w-2xl text-slate-600">
            Contact {tenant.name} for current eligibility, documentation, and
            repayment information.
          </p>
          <div className="mt-7 flex flex-wrap justify-center gap-3">
            <Link
              href="/contact"
              className="rounded-full px-7 py-3.5 text-sm font-black text-white"
              style={{ backgroundColor: primary }}
            >
              Talk to Us
            </Link>
            <Link
              href="/apply"
              className="rounded-full px-7 py-3.5 text-sm font-black"
              style={{ backgroundColor: accent, color: primary }}
            >
              Apply Online →
            </Link>
          </div>
        </div>
      </section>
    </div>
  );
}
