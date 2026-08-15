"use client";

import Link from "next/link";
import { useTenant } from "../layout";

export default function ServicesPage() {
  const tenant = useTenant();
  if (!tenant) return null;

  const primary = tenant.primaryColor;
  const accent = tenant.accentColor;
  const products = tenant.services ?? [];
  const currency = tenant.currency || "RWF";

  return (
    <div className="bg-white">
      <section className="relative overflow-hidden bg-slate-950 text-white">
        <div
          className="absolute inset-0 opacity-20"
          style={{
            background: `radial-gradient(circle at 15% 20%, ${accent} 0, transparent 32%), radial-gradient(circle at 85% 80%, ${primary} 0, transparent 38%)`,
          }}
        />
        <div className="relative mx-auto max-w-7xl px-4 py-20 sm:py-24">
          <div className="max-w-3xl">
            <div
              className="text-[11px] font-black uppercase tracking-[0.22em]"
              style={{ color: accent }}
            >
              Products & services
            </div>
            <h1 className="mt-4 text-4xl font-black tracking-tight sm:text-6xl">
              Financing built around real purposes.
            </h1>
            <p className="mt-6 max-w-2xl text-base leading-8 text-white/65">
              Explore the products currently published by {tenant.name}. Each
              product can have its own pricing, amount limits and repayment
              terms.
            </p>
          </div>
          <div className="mt-10 flex flex-wrap gap-3">
            <Link
              href="/apply"
              className="rounded-2xl px-6 py-3.5 text-sm font-black"
              style={{ backgroundColor: accent, color: primary }}
            >
              Apply online
            </Link>
            <Link
              href="/contact"
              className="rounded-2xl border border-white/20 px-6 py-3.5 text-sm font-bold text-white"
            >
              Speak with {tenant.name}
            </Link>
          </div>
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-4 py-16 sm:py-20">
        {products.length === 0 ? (
          <div className="rounded-[2rem] border border-slate-200 bg-slate-50 p-12 text-center">
            <div className="text-4xl">◇</div>
            <h2 className="mt-4 text-xl font-black text-slate-950">
              Products are being configured
            </h2>
            <p className="mx-auto mt-2 max-w-xl text-sm leading-7 text-slate-500">
              Please contact {tenant.name} for current product availability.
            </p>
          </div>
        ) : (
          <div className="grid gap-6 md:grid-cols-2">
            {products.map((product) => (
              <article
                key={product.title}
                className="group overflow-hidden rounded-[2rem] border border-slate-200 bg-white shadow-sm transition hover:-translate-y-1 hover:shadow-xl"
              >
                <div className="border-b border-slate-100 bg-slate-50 px-7 py-6">
                  <div className="flex items-start justify-between gap-4">
                    <div>
                      <div
                        className="flex h-12 w-12 items-center justify-center rounded-2xl bg-white text-2xl shadow-sm"
                        style={{ color: primary }}
                      >
                        {product.icon || "◆"}
                      </div>
                      <h2 className="mt-4 text-2xl font-black tracking-tight text-slate-950">
                        {product.title}
                      </h2>
                    </div>
                    <span
                      className="rounded-full border px-3 py-1.5 text-[10px] font-black uppercase tracking-wider"
                      style={{
                        borderColor: `${accent}55`,
                        color: primary,
                        backgroundColor: `${accent}12`,
                      }}
                    >
                      Available
                    </span>
                  </div>
                  <p className="mt-4 text-sm leading-7 text-slate-600">
                    {product.description ||
                      "A lender-published financing product. Review eligibility and final terms during application."}
                  </p>
                </div>

                <div className="px-7 py-7">
                  <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
                    {[
                      ["Interest", product.rate || "See terms"],
                      ["Term", product.term || "See terms"],
                      [
                        "Maximum",
                        product.maxAmount
                          ? `${currency} ${Number(product.maxAmount).toLocaleString()}`
                          : "No stated cap",
                      ],
                    ].map(([label, value]) => (
                      <div key={label} className="rounded-2xl bg-slate-50 p-4">
                        <div className="text-[10px] font-black uppercase tracking-wider text-slate-400">
                          {label}
                        </div>
                        <div
                          className="mt-1 text-sm font-black"
                          style={{ color: primary }}
                        >
                          {value}
                        </div>
                      </div>
                    ))}
                  </div>

                  <div className="mt-6 rounded-2xl border border-slate-200 p-4">
                    <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
                      <div>
                        <div className="text-[10px] font-black uppercase tracking-wider text-slate-400">
                          Minimum amount
                        </div>
                        <div className="mt-1 text-sm font-bold text-slate-900">
                          {product.minAmount
                            ? `${currency} ${Number(product.minAmount).toLocaleString()}`
                            : "Contact lender"}
                        </div>
                      </div>
                      <div>
                        <div className="text-[10px] font-black uppercase tracking-wider text-slate-400">
                          Management fee
                        </div>
                        <div className="mt-1 text-sm font-bold text-slate-900">
                          {product.managementFee || "See product terms"}
                        </div>
                      </div>
                      <div>
                        <div className="text-[10px] font-black uppercase tracking-wider text-slate-400">
                          Processing fee
                        </div>
                        <div className="mt-1 text-sm font-bold text-slate-900">
                          {product.processingFee || "See product terms"}
                        </div>
                      </div>
                    </div>
                  </div>

                  <div className="mt-6 flex flex-wrap items-center justify-between gap-3">
                    <div className="text-xs leading-5 text-slate-500">
                      Final terms are subject to eligibility and approval.
                    </div>
                    <Link
                      href={`/apply?type=${encodeURIComponent(product.title)}`}
                      className="rounded-xl px-5 py-3 text-sm font-black text-white transition hover:-translate-y-0.5"
                      style={{ backgroundColor: primary }}
                    >
                      Apply for {product.title} →
                    </Link>
                  </div>
                </div>
              </article>
            ))}
          </div>
        )}
      </section>

      <section className="bg-slate-50">
        <div className="mx-auto max-w-7xl px-4 py-16 sm:py-20">
          <div className="grid gap-5 md:grid-cols-3">
            {[
              [
                "Product clarity",
                "See the published rate, term and key fee information before you submit an application.",
              ],
              [
                "Flexible pricing",
                "Each lender organization can manage its own product pricing without affecting another organization.",
              ],
              [
                "Secure application",
                "Applications are submitted through the lender's connected loan management platform.",
              ],
            ].map(([title, text]) => (
              <div
                key={title}
                className="rounded-[1.5rem] border border-slate-200 bg-white p-6 shadow-sm"
              >
                <div className="text-lg font-black text-slate-950">{title}</div>
                <p className="mt-2 text-sm leading-7 text-slate-600">{text}</p>
              </div>
            ))}
          </div>
        </div>
      </section>
    </div>
  );
}
