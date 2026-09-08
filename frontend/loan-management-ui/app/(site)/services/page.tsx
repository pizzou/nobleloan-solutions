"use client";

import Link from "next/link";
import { useTenant } from "../layout";

function amount(currency: string, value: string | number | null | undefined) {
  if (value == null || value === "") return "No stated limit";
  const n = Number(String(value).replace(/[^0-9.-]/g, ""));
  return Number.isFinite(n)
    ? `${currency} ${n.toLocaleString("en-RW", { maximumFractionDigits: 0 })}`
    : "No stated limit";
}
function Arrow() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.9"
      className="h-4 w-4"
    >
      <path d="M5 12h14" />
      <path d="m13 6 6 6-6 6" />
    </svg>
  );
}

export default function ServicesPage() {
  const tenant = useTenant();
  if (!tenant) return null;
  const primary = tenant.primaryColor || "#0B1F3A";
  const accent = tenant.accentColor || "#D4AF37";
  const services = tenant.services || [];

  return (
    <main className="bg-white text-slate-950">
      <section className="relative overflow-hidden bg-[#061326] text-white">
        <div
          className="absolute inset-0"
          style={{
            background: `radial-gradient(circle at 85% 10%,${accent}24,transparent 24%),linear-gradient(120deg,#061326,${primary})`,
          }}
        />
        <div className="relative mx-auto max-w-7xl px-5 py-20 sm:px-8 lg:py-28">
          <div className="max-w-3xl">
            <div
              className="text-[10px] font-black uppercase tracking-[.22em]"
              style={{ color: accent }}
            >
              Our solutions
            </div>
            <h1 className="mt-4 text-5xl font-black leading-[1.02] tracking-[-.05em] sm:text-6xl">
              Finance that fits the purpose, not the other way around.
            </h1>
            <p className="mt-6 max-w-2xl text-base leading-7 text-white/60 sm:text-lg">
              Compare the active lending products configured by {tenant.name}.
              Pricing and terms shown here are based on the institution&apos;s
              published product configuration.
            </p>
          </div>
        </div>
      </section>
      <section className="mx-auto max-w-7xl px-5 py-16 sm:px-8 lg:py-24">
        <div className="grid gap-6 lg:grid-cols-2">
          {services.map((service, index) => (
            <article
              key={`${service.title}-${index}`}
              className="group relative overflow-hidden rounded-[28px] border border-slate-200 bg-white p-7 shadow-[0_15px_50px_rgba(15,23,42,.05)] transition duration-300 hover:-translate-y-1 hover:shadow-[0_28px_70px_rgba(15,23,42,.11)] sm:p-9"
            >
              <div
                className="absolute right-0 top-0 h-40 w-40 rounded-full opacity-10 blur-3xl"
                style={{ backgroundColor: accent }}
              />
              <div className="relative flex items-start justify-between">
                <div
                  className="flex h-14 w-14 items-center justify-center rounded-2xl text-2xl"
                  style={{ backgroundColor: `${primary}0D`, color: primary }}
                >
                  {service.icon}
                </div>
                <span className="rounded-full border border-slate-200 px-3 py-1 text-[9px] font-black uppercase tracking-[.16em] text-slate-400">
                  Solution {String(index + 1).padStart(2, "0")}
                </span>
              </div>
              <h2 className="relative mt-7 text-2xl font-black tracking-tight">
                {service.title}
              </h2>
              <p className="relative mt-3 min-h-[56px] text-sm leading-6 text-slate-500">
                {service.description}
              </p>
              <div className="relative mt-7 grid grid-cols-2 gap-3">
                <div className="rounded-2xl bg-slate-50 p-4">
                  <div className="text-[9px] font-black uppercase tracking-[.14em] text-slate-400">
                    Interest
                  </div>
                  <div
                    className="mt-1 text-lg font-black"
                    style={{ color: primary }}
                  >
                    {service.interestRate ?? service.rate}%{" "}
                    <span className="text-[10px] font-bold text-slate-400">
                      {service.rateType || "monthly"}
                    </span>
                  </div>
                </div>
                <div className="rounded-2xl bg-slate-50 p-4">
                  <div className="text-[9px] font-black uppercase tracking-[.14em] text-slate-400">
                    Term
                  </div>
                  <div className="mt-1 text-lg font-black">
                    {service.term || "Contact us"}
                  </div>
                </div>
                <div className="rounded-2xl bg-slate-50 p-4">
                  <div className="text-[9px] font-black uppercase tracking-[.14em] text-slate-400">
                    From
                  </div>
                  <div className="mt-1 text-sm font-black">
                    {amount(tenant.currency, service.minAmount)}
                  </div>
                </div>
                <div className="rounded-2xl bg-slate-50 p-4">
                  <div className="text-[9px] font-black uppercase tracking-[.14em] text-slate-400">
                    Up to
                  </div>
                  <div className="mt-1 text-sm font-black">
                    {amount(tenant.currency, service.maxAmount)}
                  </div>
                </div>
              </div>
              <div className="relative mt-5 grid grid-cols-2 gap-3 text-xs text-slate-500">
                <div>
                  Application fee{" "}
                  <strong className="text-slate-800">
                    {service.applicationFeeRate ?? 2}%
                  </strong>
                </div>
                <div>
                  Management fee{" "}
                  <strong className="text-slate-800">
                    {service.managementFeeRate ?? 5}%
                  </strong>
                </div>
              </div>
              <div className="relative mt-7 flex flex-col gap-3 sm:flex-row">
                <Link
                  href={`/apply?type=${encodeURIComponent(service.title)}`}
                  className="inline-flex flex-1 items-center justify-center gap-2 rounded-xl px-5 py-3 text-sm font-black text-white"
                  style={{ backgroundColor: primary }}
                >
                  Apply for this solution <Arrow />
                </Link>
                <Link
                  href="/contact"
                  className="inline-flex items-center justify-center rounded-xl border border-slate-200 px-5 py-3 text-sm font-bold"
                  style={{ color: primary }}
                >
                  Talk to us
                </Link>
              </div>
            </article>
          ))}
        </div>
        {services.length === 0 && (
          <div className="rounded-[28px] border border-dashed border-slate-300 bg-slate-50 p-12 text-center">
            <h2 className="text-xl font-black">Products are being updated</h2>
            <p className="mx-auto mt-2 max-w-md text-sm leading-6 text-slate-500">
              Please contact our lending team for currently available financing
              options.
            </p>
          </div>
        )}
      </section>
    </main>
  );
}
