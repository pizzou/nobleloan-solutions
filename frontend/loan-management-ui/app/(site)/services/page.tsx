"use client";

import Image from "next/image";
import Link from "next/link";
import { useTenant } from "../layout";

const images: Record<string, string> = {
  PERSONAL: "/personal-loan.jpg",
  BUSINESS: "/business-loan.jpg",
  AUTO: "/vehicle-loan.jpg",
  SALARY_ADVANCE: "/salary-advance.jpg",
  AGRICULTURAL: "/agriculture-loan.jpg",
};

function amount(currency: string, value: string | number | null | undefined) {
  if (value == null || value === "") return "No stated limit";
  const n = Number(String(value).replace(/[^0-9.-]/g, ""));
  return Number.isFinite(n)
    ? `${currency} ${n.toLocaleString("en-RW", { maximumFractionDigits: 0 })}`
    : "No stated limit";
}

export default function ServicesPage() {
  const tenant = useTenant();
  if (!tenant) return null;
  const primary = tenant.primaryColor || "#0F1B3D";
  const accent = tenant.accentColor || "#C9A227";
  const services = tenant.services || [];

  return (
    <main className="overflow-hidden bg-[#f5f7fa] text-slate-950">
      <section
        className="relative overflow-hidden text-white"
        style={{
          background: `linear-gradient(120deg,#061326 0%,${primary} 64%,#1b4169 100%)`,
        }}
      >
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_82%_12%,rgba(201,162,39,.25),transparent_24%),radial-gradient(circle_at_15%_90%,rgba(255,255,255,.08),transparent_26%)]" />
        <div className="relative mx-auto max-w-7xl px-5 py-16 sm:px-8 sm:py-24 lg:py-28">
          <p
            className="text-[10px] font-black uppercase tracking-[.24em]"
            style={{ color: accent }}
          >
            Loan solutions
          </p>
          <h1 className="mt-5 max-w-4xl text-4xl font-black leading-[.98] tracking-[-.06em] sm:text-6xl lg:text-[4.35rem]">
            Finance that fits the purpose, not the other way around.
          </h1>
          <p className="mt-6 max-w-2xl text-base leading-8 text-white/70 sm:text-lg">
            Explore the active lending products currently published by{" "}
            {tenant.name}. Compare the visible pricing and repayment
            information, then open the product that matches your need.
          </p>
          <div className="mt-8 flex flex-col gap-3 sm:flex-row">
            <Link
              href="/loan-calculator"
              className="rounded-2xl px-6 py-4 text-center text-sm font-black text-slate-950 shadow-lg transition hover:-translate-y-0.5"
              style={{ backgroundColor: accent }}
            >
              Plan a repayment →
            </Link>
            <Link
              href="/contact"
              className="rounded-2xl border border-white/15 bg-white/[.05] px-6 py-4 text-center text-sm font-bold text-white"
            >
              Ask the lending team
            </Link>
          </div>
        </div>
      </section>

      <section className="border-b border-slate-200 bg-white">
        <div className="mx-auto grid max-w-7xl gap-px sm:grid-cols-3">
          {[
            [
              "Transparent view",
              "Published product details are visible before you start an application.",
            ],
            [
              "RWF planning",
              "Amounts are presented in the configured local currency: Rwandan francs.",
            ],
            [
              "One journey",
              "Choose a loan, review the numbers, apply securely and track your reference.",
            ],
          ].map(([title, body]) => (
            <div
              key={title}
              className="border-b border-slate-100 p-6 last:border-b-0 sm:border-b-0 sm:border-r sm:last:border-r-0 sm:p-7"
            >
              <h2 className="text-sm font-black">{title}</h2>
              <p className="mt-2 text-xs leading-6 text-slate-500">{body}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-5 py-14 sm:px-8 sm:py-20">
        <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
          <div>
            <p
              className="text-[10px] font-black uppercase tracking-[.22em]"
              style={{ color: accent }}
            >
              Compare available options
            </p>
            <h2 className="mt-3 text-3xl font-black tracking-[-.05em] sm:text-4xl">
              Find the financing purpose that fits your situation.
            </h2>
          </div>
          <Link
            href="/apply"
            className="text-sm font-black"
            style={{ color: primary }}
          >
            Go to application →
          </Link>
        </div>

        {services.length > 0 ? (
          <div className="mt-10 space-y-5">
            {services.map((service, index) => {
              const type = String(service.loanType || "").toUpperCase();
              return (
                <article
                  key={`${service.title}-${index}`}
                  className="group overflow-hidden rounded-[30px] border border-slate-200 bg-white shadow-[0_16px_55px_rgba(15,23,42,.055)] transition duration-300 hover:-translate-y-1 hover:shadow-[0_28px_80px_rgba(15,23,42,.10)]"
                >
                  <div className="grid lg:grid-cols-[280px_1fr]">
                    <div className="relative min-h-[220px] overflow-hidden bg-slate-100 lg:min-h-full">
                      <Image
                        src={images[type] || "/hero-borrower.jpg"}
                        alt=""
                        fill
                        sizes="(max-width: 1024px) 100vw, 280px"
                        className="object-cover transition duration-500 group-hover:scale-105"
                      />
                      <div className="absolute inset-0 bg-gradient-to-t from-[#061326]/70 via-transparent to-transparent" />
                      <div className="absolute bottom-4 left-4 flex h-11 w-11 items-center justify-center rounded-2xl border border-white/25 bg-[#061326]/75 text-lg text-white backdrop-blur">
                        {service.icon || "◈"}
                      </div>
                    </div>
                    <div className="p-7 sm:p-9">
                      <div className="flex flex-wrap items-start justify-between gap-4">
                        <div>
                          <div
                            className="text-[9px] font-black uppercase tracking-[.18em]"
                            style={{ color: accent }}
                          >
                            Solution {String(index + 1).padStart(2, "0")}
                          </div>
                          <h3 className="mt-2 text-2xl font-black tracking-[-.035em]">
                            {service.title}
                          </h3>
                          <p className="mt-3 max-w-2xl text-sm leading-7 text-slate-500">
                            {service.description}
                          </p>
                        </div>
                        <span className="rounded-full bg-slate-50 px-3 py-1.5 text-[9px] font-black uppercase tracking-[.15em] text-slate-500">
                          {service.term || "Review terms"}
                        </span>
                      </div>

                      <div className="mt-7 grid gap-3 sm:grid-cols-4">
                        {[
                          [
                            "Interest",
                            `${service.interestRate ?? service.rate ?? "—"}% ${service.rateType || "monthly"}`,
                          ],
                          [
                            "Application fee",
                            `${service.applicationFeeRate ?? "—"}%`,
                          ],
                          [
                            "Management fee",
                            `${service.managementFeeRate ?? "—"}%`,
                          ],
                          [
                            "Starting amount",
                            amount(tenant.currency, service.minAmount),
                          ],
                        ].map(([label, value]) => (
                          <div
                            key={label}
                            className="rounded-2xl bg-slate-50 p-4"
                          >
                            <div className="text-[9px] font-black uppercase tracking-[.14em] text-slate-400">
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

                      <div className="mt-7 flex flex-col gap-3 sm:flex-row">
                        <Link
                          href={`/apply?type=${encodeURIComponent(service.loanType || service.title)}`}
                          className="rounded-2xl px-5 py-3.5 text-center text-sm font-black text-white"
                          style={{ backgroundColor: primary }}
                        >
                          Apply for this solution →
                        </Link>
                        <Link
                          href={`/loan-calculator?type=${encodeURIComponent(service.loanType || service.title)}`}
                          className="rounded-2xl border border-slate-200 px-5 py-3.5 text-center text-sm font-bold"
                          style={{ color: primary }}
                        >
                          Calculate repayment
                        </Link>
                        <Link
                          href="/contact"
                          className="rounded-2xl border border-slate-200 px-5 py-3.5 text-center text-sm font-bold text-slate-600"
                        >
                          Ask a question
                        </Link>
                      </div>
                    </div>
                  </div>
                </article>
              );
            })}
          </div>
        ) : (
          <div className="mt-10 rounded-[28px] border border-dashed border-slate-300 bg-white p-12 text-center">
            <h2 className="text-xl font-black">Products are being updated</h2>
            <p className="mx-auto mt-2 max-w-md text-sm leading-7 text-slate-500">
              Please contact the Noble lending team for the currently available
              financing options.
            </p>
          </div>
        )}
      </section>

      <section className="border-t border-slate-200 bg-white py-14 sm:py-20">
        <div className="mx-auto max-w-7xl px-5 sm:px-8">
          <div className="grid gap-5 md:grid-cols-3">
            {[
              [
                "1",
                "Start with purpose",
                "Choose the product that most closely matches why you need the finance.",
              ],
              [
                "2",
                "Check affordability",
                "Use the calculator and review the total repayment rather than only the amount borrowed.",
              ],
              [
                "3",
                "Read before accepting",
                "Review the exact fees, dates and obligations presented in your application and loan agreement.",
              ],
            ].map(([n, title, body]) => (
              <div
                key={n}
                className="rounded-[26px] border border-slate-200 bg-slate-50 p-6"
              >
                <span
                  className="flex h-9 w-9 items-center justify-center rounded-xl text-xs font-black text-white"
                  style={{ backgroundColor: primary }}
                >
                  {n}
                </span>
                <h3 className="mt-4 text-base font-black">{title}</h3>
                <p className="mt-2 text-xs leading-6 text-slate-500">{body}</p>
              </div>
            ))}
          </div>
        </div>
      </section>
    </main>
  );
}
