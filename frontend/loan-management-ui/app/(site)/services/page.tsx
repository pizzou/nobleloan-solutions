"use client";

import Link from "next/link";
import { useTenant } from "../layout";

function n(value: unknown, fallback = 0) {
  const parsed = Number(String(value ?? "").replace(/[^0-9.-]/g, ""));
  return Number.isFinite(parsed) ? parsed : fallback;
}

export default function ServicesPage() {
  const tenant = useTenant();
  if (!tenant) return null;
  const primary = tenant.primaryColor;
  const accent = tenant.accentColor;
  const products = tenant.services ?? [];

  return (
    <div>
      <section className="bg-[#06172D] text-white">
        <div className="mx-auto max-w-7xl px-4 py-16 sm:py-20">
          <div className="max-w-3xl">
            <div
              className="text-[11px] font-bold uppercase tracking-[0.2em]"
              style={{ color: accent }}
            >
              Financial solutions
            </div>
            <h1 className="mt-3 text-4xl font-black tracking-tight sm:text-5xl">
              Solutions designed around real financial goals.
            </h1>
            <p className="mt-5 text-base leading-8 text-white/65">
              Review the products published by {tenant.name}. Pricing, limits
              and terms are shown before you apply.
            </p>
          </div>
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-4 py-12 sm:py-16">
        <div className="grid gap-5 lg:grid-cols-2">
          {products.map((product, index) => (
            <article
              key={product.title}
              className="group rounded-[30px] border border-slate-200 bg-white p-6 shadow-sm transition hover:-translate-y-1 hover:shadow-xl sm:p-7"
            >
              <div className="flex items-start justify-between gap-5">
                <div
                  className="flex h-14 w-14 items-center justify-center rounded-2xl text-2xl"
                  style={{ backgroundColor: `${primary}0D` }}
                >
                  {product.icon || ["◈", "◆", "◇", "✦", "▣", "○"][index % 6]}
                </div>
                <div
                  className="rounded-full px-3 py-1.5 text-[10px] font-black uppercase tracking-wide"
                  style={{ backgroundColor: `${accent}16`, color: primary }}
                >
                  Available
                </div>
              </div>
              <h2 className="mt-6 text-2xl font-black tracking-tight text-slate-950">
                {product.title}
              </h2>
              <p className="mt-2 min-h-[52px] text-sm leading-6 text-slate-500">
                {product.description ||
                  "A structured financing product with clear commercial terms and digital application support."}
              </p>
              <div className="mt-7 grid grid-cols-2 gap-px overflow-hidden rounded-2xl border border-slate-200 bg-slate-200 sm:grid-cols-4">
                {[
                  [
                    "Interest",
                    product.rate
                      ? `${product.rate}${product.rateType === "MONTHLY" || product.rateType == null ? " / mo" : ""}`
                      : "See terms",
                  ],
                  [
                    "Management",
                    product.managementFeeRate != null
                      ? `${product.managementFeeRate}% / mo`
                      : "See terms",
                  ],
                  [
                    "Amount",
                    product.maxAmount
                      ? `${tenant.currency} ${product.maxAmount}`
                      : "Flexible",
                  ],
                  ["Term", product.term || "Flexible"],
                ].map(([label, value]) => (
                  <div key={label} className="bg-white p-4">
                    <div className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
                      {label}
                    </div>
                    <div className="mt-1 text-sm font-black text-slate-900">
                      {value}
                    </div>
                  </div>
                ))}
              </div>
              <div className="mt-6 flex flex-wrap gap-3">
                <Link
                  href={`/apply?type=${encodeURIComponent(product.title)}`}
                  className="rounded-xl px-5 py-3 text-sm font-black text-white"
                  style={{ backgroundColor: primary }}
                >
                  Apply for this product
                </Link>
                <Link
                  href="/contact"
                  className="rounded-xl border border-slate-200 px-5 py-3 text-sm font-bold text-slate-700"
                >
                  Talk to an advisor
                </Link>
              </div>
            </article>
          ))}
        </div>

        {products.length === 0 && (
          <div className="rounded-3xl border border-dashed border-slate-300 bg-white p-12 text-center text-sm text-slate-500">
            No public loan products are currently configured.
          </div>
        )}
      </section>

      <section className="border-y border-slate-200 bg-white">
        <div className="mx-auto grid max-w-7xl gap-8 px-4 py-12 md:grid-cols-3">
          {[
            [
              "Transparent pricing",
              "Know the applicable rate, fees and repayment structure before you commit.",
            ],
            [
              "Responsible assessment",
              "Applications are evaluated using affordability and credit-risk controls.",
            ],
            [
              "Digital convenience",
              "Start, monitor and communicate through a secure online experience.",
            ],
          ].map(([title, text]) => (
            <div key={title}>
              <div className="text-sm font-black text-slate-950">{title}</div>
              <div className="mt-2 text-sm leading-6 text-slate-500">
                {text}
              </div>
            </div>
          ))}
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-4 py-14">
        <div
          className="rounded-[30px] p-8 sm:p-10"
          style={{ backgroundColor: `${primary}08` }}
        >
          <div className="max-w-2xl">
            <div
              className="text-[11px] font-bold uppercase tracking-[0.18em]"
              style={{ color: accent }}
            >
              Need guidance?
            </div>
            <h2 className="mt-2 text-2xl font-black text-slate-950">
              Not sure which solution fits?
            </h2>
            <p className="mt-2 text-sm leading-6 text-slate-500">
              Contact the team and explain what you are trying to finance. We
              can help you understand the available options before you apply.
            </p>
            <div className="mt-6 flex flex-wrap gap-3">
              <Link
                href="/contact"
                className="rounded-xl px-5 py-3 text-sm font-black text-white"
                style={{ backgroundColor: primary }}
              >
                Talk to us
              </Link>
              <Link
                href="/apply"
                className="rounded-xl border border-slate-200 bg-white px-5 py-3 text-sm font-bold text-slate-700"
              >
                Start application
              </Link>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
