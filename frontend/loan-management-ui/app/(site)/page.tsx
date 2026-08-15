"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useTenant, type TenantService } from "./layout";
import { publicApi } from "../../services/api";
import { TENANT_SLUG } from "../../lib/tenant";
import PublicLoanCalculator from "../../components/PublicLoanCalculator";
import ExchangeRatePanel from "../../components/ExchangeRatePanel";

function Icon({ name, size = 22 }: { name: string; size?: number }) {
  const common = {
    width: size,
    height: size,
    viewBox: "0 0 24 24",
    fill: "none",
    stroke: "currentColor",
    strokeWidth: 1.8,
    strokeLinecap: "round" as const,
    strokeLinejoin: "round" as const,
  };
  const paths: Record<string, React.ReactNode> = {
    shield: (
      <>
        <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z" />
        <path d="m9 12 2 2 4-4" />
      </>
    ),
    bolt: <path d="m13 2-10 12h9l-1 8 10-12h-9l1-8Z" />,
    check: <path d="m20 6-11 11-5-5" />,
    users: (
      <>
        <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
        <circle cx="9" cy="7" r="4" />
        <path d="M22 21v-2a4 4 0 0 0-3-3.9M16 3.1a4 4 0 0 1 0 7.8" />
      </>
    ),
    chart: (
      <>
        <path d="M3 3v18h18" />
        <path d="m7 16 4-5 3 3 5-7" />
      </>
    ),
    arrow: (
      <>
        <path d="M5 12h14" />
        <path d="m13 6 6 6-6 6" />
      </>
    ),
    lock: (
      <>
        <rect x="4" y="10" width="16" height="11" rx="2" />
        <path d="M8 10V7a4 4 0 0 1 8 0v3" />
      </>
    ),
  };
  return <svg {...common}>{paths[name]}</svg>;
}

function numberValue(value: unknown, fallback = 0) {
  const n = Number(String(value ?? "").replace(/[^0-9.-]/g, ""));
  return Number.isFinite(n) ? n : fallback;
}

export default function HomePage() {
  const tenant = useTenant();
  const [products, setProducts] = useState<TenantService[]>([]);

  useEffect(() => {
    let active = true;
    publicApi
      .getProducts(TENANT_SLUG)
      .then((res: any) => {
        const data = Array.isArray(res?.data)
          ? res.data
          : Array.isArray(res)
            ? res
            : [];
        if (active && data.length) setProducts(data);
      })
      .catch(() => undefined);
    return () => {
      active = false;
    };
  }, []);

  if (!tenant) return null;

  const serviceProducts = products.length ? products : (tenant.services ?? []);
  const primary = tenant.primaryColor;
  const accent = tenant.accentColor;

  const highlights = [
    {
      icon: "bolt",
      title: "Fast decisions",
      text: "Digital application intake and a structured credit review process designed to reduce unnecessary delays.",
    },
    {
      icon: "shield",
      title: "Secure by design",
      text: "Sensitive customer information is handled through controlled workflows and protected access.",
    },
    {
      icon: "check",
      title: "Clear pricing",
      text: "Loan amount, interest, management fees and repayment terms are shown before you commit.",
    },
    {
      icon: "users",
      title: "Human support",
      text: "Digital convenience without losing access to people who can help with your application.",
    },
  ];

  const stats = tenant.stats ?? [];

  return (
    <div>
      <section className="relative overflow-hidden bg-[#06172D] text-white">
        <div
          className="absolute inset-0 opacity-30"
          style={{
            background: `radial-gradient(circle at 76% 20%, ${accent}55 0, transparent 32%), radial-gradient(circle at 15% 85%, ${primary}AA 0, transparent 40%)`,
          }}
        />
        <div className="relative mx-auto max-w-7xl px-4 pb-20 pt-14 sm:pb-28 sm:pt-20 lg:pt-24">
          <div className="grid items-center gap-12 lg:grid-cols-[1.02fr_.98fr]">
            <div>
              <div className="mb-6 inline-flex items-center gap-2 rounded-full border border-white/15 bg-white/5 px-3.5 py-2 text-[11px] font-bold uppercase tracking-[0.18em] text-white/80">
                <span
                  className="h-1.5 w-1.5 rounded-full"
                  style={{ backgroundColor: accent }}
                />
                Digital lending • {tenant.country || "Local"}
              </div>
              <h1 className="max-w-3xl text-4xl font-black leading-[1.05] tracking-tight sm:text-5xl lg:text-[62px]">
                {tenant.hero?.headline ||
                  "Finance built around your next move."}
              </h1>
              <p className="mt-6 max-w-2xl text-base leading-8 text-white/68 sm:text-lg">
                {tenant.hero?.subtext ||
                  "A professional digital lending experience with transparent terms, guided applications and secure customer service."}
              </p>
              <div className="mt-8 flex flex-wrap gap-3">
                <Link
                  href="/apply"
                  className="rounded-2xl bg-white px-6 py-3.5 text-sm font-black shadow-xl transition hover:-translate-y-0.5"
                  style={{ color: primary }}
                >
                  Start an application
                </Link>
                <Link
                  href="/services"
                  className="rounded-2xl border border-white/20 bg-white/5 px-6 py-3.5 text-sm font-bold text-white transition hover:bg-white/10"
                >
                  Explore solutions
                </Link>
              </div>
              <div className="mt-9 flex flex-wrap gap-x-7 gap-y-3 text-xs font-semibold text-white/50">
                <span className="inline-flex items-center gap-2">
                  <Icon name="lock" size={15} />
                  Secure digital workflow
                </span>
                <span className="inline-flex items-center gap-2">
                  <Icon name="shield" size={15} />
                  Clear terms
                </span>
                <span className="inline-flex items-center gap-2">
                  <Icon name="check" size={15} />
                  Track your application online
                </span>
              </div>
            </div>

            <div className="lg:justify-self-end lg:max-w-xl">
              <div className="rounded-[30px] border border-white/12 bg-white/8 p-2 shadow-2xl backdrop-blur">
                <div className="rounded-[24px] bg-white p-6 text-slate-900 sm:p-7">
                  <div className="flex items-start justify-between gap-4">
                    <div>
                      <div
                        className="text-[11px] font-bold uppercase tracking-[0.18em]"
                        style={{ color: accent }}
                      >
                        Financial snapshot
                      </div>
                      <div className="mt-1 text-2xl font-black tracking-tight">
                        A clearer way to borrow
                      </div>
                    </div>
                    <div
                      className="rounded-2xl px-3 py-2 text-right"
                      style={{ backgroundColor: `${primary}09` }}
                    >
                      <div className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
                        Currency
                      </div>
                      <div
                        className="mt-1 text-sm font-black"
                        style={{ color: primary }}
                      >
                        {tenant.currency}
                      </div>
                    </div>
                  </div>
                  <div className="mt-6 grid grid-cols-2 gap-3">
                    <div className="rounded-2xl border border-slate-100 bg-slate-50 p-4">
                      <div className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
                        Products
                      </div>
                      <div className="mt-2 text-2xl font-black text-slate-950">
                        {serviceProducts.length || "—"}
                      </div>
                      <div className="mt-1 text-xs text-slate-500">
                        available today
                      </div>
                    </div>
                    <div className="rounded-2xl border border-slate-100 bg-slate-50 p-4">
                      <div className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
                        Application
                      </div>
                      <div className="mt-2 text-2xl font-black text-slate-950">
                        Online
                      </div>
                      <div className="mt-1 text-xs text-slate-500">
                        from any device
                      </div>
                    </div>
                  </div>
                  <div
                    className="mt-5 rounded-2xl p-4"
                    style={{
                      background: `linear-gradient(135deg, ${primary}, #0B223E)`,
                    }}
                  >
                    <div className="text-xs font-semibold text-white/60">
                      Need help choosing?
                    </div>
                    <div className="mt-1 text-lg font-black text-white">
                      Compare loan solutions before you apply.
                    </div>
                    <Link
                      href="/services"
                      className="mt-4 inline-flex items-center gap-2 text-xs font-black"
                      style={{ color: accent }}
                    >
                      View products <Icon name="arrow" size={15} />
                    </Link>
                  </div>
                </div>
              </div>
            </div>
          </div>

          {stats.length > 0 && (
            <div className="mt-14 grid grid-cols-2 gap-px overflow-hidden rounded-3xl border border-white/10 bg-white/10 md:grid-cols-4">
              {stats.slice(0, 4).map((stat) => (
                <div key={stat.label} className="bg-white/[0.04] px-5 py-6">
                  <div className="text-2xl">{stat.icon}</div>
                  <div className="mt-2 text-2xl font-black">{stat.value}</div>
                  <div className="mt-1 text-xs font-semibold text-white/45">
                    {stat.label}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </section>

      <section className="border-b border-slate-200 bg-white">
        <div className="mx-auto grid max-w-7xl grid-cols-2 gap-px bg-slate-100 px-4 sm:grid-cols-4">
          {highlights.map((item) => (
            <div key={item.title} className="bg-white px-4 py-7 sm:px-6">
              <div
                className="flex h-10 w-10 items-center justify-center rounded-xl"
                style={{ backgroundColor: `${primary}0D`, color: primary }}
              >
                <Icon name={item.icon} />
              </div>
              <div className="mt-4 text-sm font-black text-slate-950">
                {item.title}
              </div>
              <div className="mt-1.5 text-xs leading-5 text-slate-500">
                {item.text}
              </div>
            </div>
          ))}
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-4 py-16 sm:py-20">
        <div className="mb-10 flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
          <div>
            <div
              className="text-[11px] font-bold uppercase tracking-[0.18em]"
              style={{ color: accent }}
            >
              Featured solutions
            </div>
            <h2 className="mt-2 text-3xl font-black tracking-tight text-slate-950 sm:text-4xl">
              Financial products for real needs
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
        <div className="grid gap-5 md:grid-cols-2 lg:grid-cols-3">
          {serviceProducts.slice(0, 6).map((product, index) => (
            <Link
              key={product.title}
              href={`/apply?type=${encodeURIComponent(product.title)}`}
              className="group rounded-3xl border border-slate-200 bg-white p-6 shadow-sm transition hover:-translate-y-1 hover:shadow-xl"
            >
              <div className="flex items-start justify-between gap-4">
                <div
                  className="flex h-12 w-12 items-center justify-center rounded-2xl text-2xl"
                  style={{ backgroundColor: `${primary}0D` }}
                >
                  {product.icon || ["◈", "◆", "◇", "✦", "▣", "○"][index % 6]}
                </div>
                <span
                  className="rounded-full px-2.5 py-1 text-[10px] font-black uppercase"
                  style={{ backgroundColor: `${accent}16`, color: primary }}
                >
                  Explore
                </span>
              </div>
              <h3 className="mt-5 text-lg font-black text-slate-950">
                {product.title}
              </h3>
              <p className="mt-2 min-h-[48px] text-sm leading-6 text-slate-500">
                {product.description ||
                  "A structured financing solution with clear terms and digital application support."}
              </p>
              <div className="mt-6 grid grid-cols-2 gap-3 border-t border-slate-100 pt-5 text-xs">
                <div>
                  <div className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
                    Interest
                  </div>
                  <div className="mt-1 font-black text-slate-800">
                    {product.rate || "See terms"}
                  </div>
                </div>
                <div>
                  <div className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
                    Term
                  </div>
                  <div className="mt-1 font-black text-slate-800">
                    {product.term || "Flexible"}
                  </div>
                </div>
              </div>
            </Link>
          ))}
        </div>
      </section>

      <section className="bg-white py-4 sm:py-6">
        <div className="mx-auto max-w-7xl px-4">
          <PublicLoanCalculator
            products={serviceProducts as any[]}
            currency={tenant.currency}
            primary={primary}
            accent={accent}
          />
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-4 py-16 sm:py-20">
        <div className="grid gap-6 lg:grid-cols-[1.15fr_.85fr]">
          <ExchangeRatePanel
            baseCurrency={tenant.currency}
            primary={primary}
            accent={accent}
          />
          <div className="rounded-[28px] border border-slate-200 bg-white p-6 shadow-sm sm:p-7">
            <div
              className="text-[11px] font-bold uppercase tracking-[0.18em]"
              style={{ color: accent }}
            >
              How it works
            </div>
            <h2 className="mt-2 text-2xl font-black tracking-tight text-slate-950">
              From application to decision
            </h2>
            <div className="mt-7 space-y-5">
              {[
                [
                  "01",
                  "Choose a solution",
                  "Compare loan products, pricing and terms before starting an application.",
                ],
                [
                  "02",
                  "Apply securely",
                  "Complete the digital form and provide the documents requested for your profile.",
                ],
                [
                  "03",
                  "Credit assessment",
                  "Your application is reviewed against the organization’s lending policy and affordability checks.",
                ],
                [
                  "04",
                  "Decision & disbursement",
                  "Once approved and documentation is complete, funds can move through the configured payment channel.",
                ],
              ].map(([number, title, text]) => (
                <div key={number} className="flex gap-4">
                  <div
                    className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-xs font-black text-white"
                    style={{ backgroundColor: primary }}
                  >
                    {number}
                  </div>
                  <div>
                    <div className="text-sm font-black text-slate-950">
                      {title}
                    </div>
                    <div className="mt-1 text-xs leading-5 text-slate-500">
                      {text}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      {tenant.testimonials && tenant.testimonials.length > 0 && (
        <section className="bg-[#F1F5F9] py-16 sm:py-20">
          <div className="mx-auto max-w-7xl px-4">
            <div className="mb-10 max-w-2xl">
              <div
                className="text-[11px] font-bold uppercase tracking-[0.18em]"
                style={{ color: accent }}
              >
                Customer perspective
              </div>
              <h2 className="mt-2 text-3xl font-black tracking-tight text-slate-950">
                Built for trust, clarity and progress
              </h2>
            </div>
            <div className="grid gap-5 md:grid-cols-3">
              {tenant.testimonials.slice(0, 3).map((item) => (
                <div
                  key={item.name}
                  className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm"
                >
                  <div
                    className="text-sm tracking-widest"
                    style={{ color: accent }}
                  >
                    {"★".repeat(Math.max(1, Math.min(5, item.rating ?? 5)))}
                  </div>
                  <p className="mt-4 text-sm leading-7 text-slate-600">
                    “{item.text}”
                  </p>
                  <div className="mt-6 flex items-center gap-3">
                    <div
                      className="flex h-10 w-10 items-center justify-center rounded-full text-xs font-black text-white"
                      style={{ backgroundColor: primary }}
                    >
                      {item.name.slice(0, 1)}
                    </div>
                    <div>
                      <div className="text-sm font-black text-slate-900">
                        {item.name}
                      </div>
                      <div className="text-xs text-slate-400">{item.role}</div>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </section>
      )}

      <section className="mx-auto max-w-7xl px-4 py-16">
        <div
          className="overflow-hidden rounded-[32px] px-6 py-12 text-center sm:px-10"
          style={{ background: `linear-gradient(135deg, ${primary}, #092844)` }}
        >
          <div className="mx-auto max-w-2xl">
            <div className="text-[11px] font-bold uppercase tracking-[0.18em] text-white/50">
              Ready when you are
            </div>
            <h2 className="mt-2 text-3xl font-black tracking-tight text-white sm:text-4xl">
              Choose the right financing path with confidence.
            </h2>
            <p className="mt-4 text-sm leading-7 text-white/65">
              Review the options, understand the cost, and submit your
              application through a secure digital process.
            </p>
            <div className="mt-7 flex flex-wrap justify-center gap-3">
              <Link
                href="/apply"
                className="rounded-2xl bg-white px-6 py-3.5 text-sm font-black"
                style={{ color: primary }}
              >
                Apply now
              </Link>
              <Link
                href="/contact"
                className="rounded-2xl border border-white/20 bg-white/5 px-6 py-3.5 text-sm font-bold text-white"
              >
                Talk to us
              </Link>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
