"use client";

import Link from "next/link";
import { useTenant } from "../layout";

function displayAmount(
  currency: string,
  value: string | number | null | undefined,
) {
  if (value === null || value === undefined || value === "") return "Unlimited";
  const amount = Number(String(value).replace(/[^0-9.-]/g, ""));
  return Number.isFinite(amount)
    ? `${currency} ${amount.toLocaleString("en-RW", { maximumFractionDigits: 0 })}`
    : "Unlimited";
}

export default function ServicesPage() {
  const tenant = useTenant();
  if (!tenant) return null;

  const primary = tenant.primaryColor || "#0F1B3D";
  const accent = tenant.accentColor || "#C9A227";
  const services = tenant.services || [];

  return (
    <main className="bg-white text-slate-950">
      <section
        className="relative overflow-hidden text-white"
        style={{ background: `linear-gradient(135deg,#07111F,${primary})` }}
      >
        <div className="mx-auto max-w-7xl px-5 py-20 sm:px-8 lg:py-28">
          <div className="max-w-3xl">
            <div
              className="text-[11px] font-black uppercase tracking-[.2em]"
              style={{ color: accent }}
            >
              Noble Loan Solutions
            </div>
            <h1 className="mt-4 text-4xl font-black tracking-[-.04em] sm:text-6xl">
              Loan products built around your financial need.
            </h1>
            <p className="mt-6 text-base leading-7 text-white/65 sm:text-lg">
              Explore Noble&apos;s active lending products, their published
              pricing and terms, and choose the facility that best fits your
              purpose.
            </p>
          </div>
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-5 py-16 sm:px-8 lg:py-24">
        <div className="grid gap-6 lg:grid-cols-2">
          {services.map((service, index) => (
            <article
              key={service.title}
              className="rounded-3xl border border-slate-200 bg-white p-7 shadow-[0_15px_50px_rgba(15,23,42,.06)] sm:p-9"
            >
              <div className="flex items-start justify-between gap-5">
                <div
                  className="flex h-14 w-14 items-center justify-center rounded-2xl text-2xl"
                  style={{ backgroundColor: `${primary}10` }}
                >
                  {service.icon}
                </div>
                <span
                  className="rounded-full px-3 py-1 text-[10px] font-black uppercase tracking-[.14em]"
                  style={{ backgroundColor: `${accent}22`, color: primary }}
                >
                  Product {String(index + 1).padStart(2, "0")}
                </span>
              </div>
              <h2 className="mt-7 text-2xl font-black tracking-tight">
                {service.title}
              </h2>
              <p className="mt-3 min-h-[56px] text-sm leading-6 text-slate-500">
                {service.description}
              </p>

              <div className="mt-7 grid gap-3 sm:grid-cols-2">
                <div className="rounded-2xl bg-slate-50 p-4">
                  <div className="text-[10px] font-black uppercase tracking-wider text-slate-400">
                    Interest
                  </div>
                  <div
                    className="mt-1 text-lg font-black"
                    style={{ color: primary }}
                  >
                    {service.interestRate ?? service.rate}%{" "}
                    <span className="text-xs font-bold text-slate-400">
                      {service.rateType
                        ? String(service.rateType).toLowerCase()
                        : "monthly"}
                    </span>
                  </div>
                </div>
                <div className="rounded-2xl bg-slate-50 p-4">
                  <div className="text-[10px] font-black uppercase tracking-wider text-slate-400">
                    Term
                  </div>
                  <div
                    className="mt-1 text-lg font-black"
                    style={{ color: primary }}
                  >
                    {service.term}
                  </div>
                </div>
                <div className="rounded-2xl bg-slate-50 p-4">
                  <div className="text-[10px] font-black uppercase tracking-wider text-slate-400">
                    Minimum
                  </div>
                  <div className="mt-1 text-sm font-black">
                    {displayAmount(tenant.currency, service.minAmount)}
                  </div>
                </div>
                <div className="rounded-2xl bg-slate-50 p-4">
                  <div className="text-[10px] font-black uppercase tracking-wider text-slate-400">
                    Maximum
                  </div>
                  <div className="mt-1 text-sm font-black">
                    {displayAmount(tenant.currency, service.maxAmount)}
                  </div>
                </div>
              </div>

              <div className="mt-5 grid grid-cols-2 gap-3 text-xs text-slate-500">
                <div>
                  Processing fee{" "}
                  <strong className="text-slate-800">
                    {service.processingFeeRate ?? 2}%
                  </strong>
                </div>
                <div>
                  Management fee{" "}
                  <strong className="text-slate-800">
                    {service.managementFeeRate ?? 5}%
                  </strong>
                </div>
              </div>

              <div className="mt-7 flex flex-wrap gap-3">
                <Link
                  href={`/apply?type=${encodeURIComponent(service.title)}`}
                  className="rounded-xl px-6 py-3 text-sm font-black text-white"
                  style={{ backgroundColor: primary }}
                >
                  Apply for this product
                </Link>
                <Link
                  href="/contact"
                  className="rounded-xl border border-slate-200 px-6 py-3 text-sm font-bold"
                  style={{ color: primary }}
                >
                  Talk to Noble
                </Link>
              </div>
            </article>
          ))}
        </div>
      </section>
    </main>
  );
}
