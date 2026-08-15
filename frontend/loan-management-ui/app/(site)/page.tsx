"use client";
import Link from "next/link";
import React from "react";
import { useTenant } from "./layout";
import { useScrollReveal, useCountUp } from "../../hooks/useScrollReveal";

function IconCheck() {
  return (
    <svg
      width="18"
      height="18"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.5"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M20 6 9 17l-5-5" />
    </svg>
  );
}
function IconBolt() {
  return (
    <svg
      width="22"
      height="22"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M13 2 3 14h9l-1 8 10-12h-9l1-8z" />
    </svg>
  );
}
function IconShieldLg() {
  return (
    <svg
      width="22"
      height="22"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
      <path d="m9 12 2 2 4-4" />
    </svg>
  );
}
function IconHandshake() {
  return (
    <svg
      width="22"
      height="22"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M11 17 6 12l-4 4 5 5 4-4Z" />
      <path d="m8 14 4-4 3 3 5-5" />
      <path d="M14 6h6v6" />
    </svg>
  );
}
function IconHeadset() {
  return (
    <svg
      width="22"
      height="22"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M3 18v-6a9 9 0 0 1 18 0v6" />
      <path d="M21 19a2 2 0 0 1-2 2h-1a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2h3zM3 19a2 2 0 0 0 2 2h1a2 2 0 0 0 2-2v-3a2 2 0 0 0-2-2H3z" />
    </svg>
  );
}

export default function HomePage() {
  const tenant = useTenant();
  if (!tenant) return null;

  const primary = tenant.primaryColor;
  const accent = tenant.accentColor;
  const serif: React.CSSProperties = {
    fontFamily: "'Playfair Display', serif",
  };

  const pillars = [
    {
      Icon: IconBolt,
      title: "Fast Decisions",
      desc: "Applications reviewed by our credit team within 24 hours — no unnecessary delays.",
    },
    {
      Icon: IconShieldLg,
      title: "Secure & Regulated",
      desc: "Licensed lending, bank-grade encryption, and strict data protection standards.",
    },
    {
      Icon: IconHandshake,
      title: "Transparent Terms",
      desc: "Every rate and fee is disclosed upfront — what we quote is what you pay.",
    },
    {
      Icon: IconHeadset,
      title: "Dedicated Support",
      desc: "A real loan officer assigned to your application, from submission to disbursement.",
    },
  ];

  return (
    <div>
      {/* ── HERO — light, formal, split with a credibility card (not a calculator) ── */}
      <section className="relative overflow-hidden bg-white border-b border-gray-100">
        <div
          className="absolute inset-0 opacity-[0.03]"
          style={{
            backgroundImage: `radial-gradient(circle at 2px 2px, ${primary} 1px, transparent 0)`,
            backgroundSize: "28px 28px",
          }}
        />
        <div className="relative max-w-7xl mx-auto px-4 py-20 md:py-28 grid md:grid-cols-2 gap-14 items-center">
          <div>
            <div
              className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full border text-xs font-bold mb-6 tracking-wide uppercase"
              style={{
                borderColor: accent,
                color: primary,
                backgroundColor: accent + "14",
              }}
            >
              Licensed &amp; Regulated in{" "}
              {tenant.country === "RW" ? "Rwanda" : tenant.country}
            </div>
            <h1
              className="text-4xl md:text-5xl font-bold leading-[1.15] mb-6 text-gray-900"
              style={serif}
            >
              {tenant.hero?.headline ??
                "Need Cash Fast? We've Got You Covered!"}
            </h1>
            <p className="text-gray-500 text-lg leading-relaxed mb-8 max-w-lg">
              {tenant.hero?.subtext ??
                "Your trusted partner in financial support — personal, business, vehicle, salary advance, and agriculture loans, backed by a secure, fully compliant lending platform."}
            </p>
            <div className="flex flex-wrap gap-4 mb-10">
              <Link
                href="/apply"
                className="px-8 py-3.5 rounded-full font-bold text-base shadow-md hover:opacity-90 transition-opacity"
                style={{ backgroundColor: primary, color: "#fff" }}
              >
                Apply for a Loan →
              </Link>
              <Link
                href="/services"
                className="px-8 py-3.5 rounded-full font-semibold text-base border-2 text-gray-700 hover:bg-gray-50 transition-colors"
                style={{ borderColor: "#E5E7EB" }}
              >
                View Our Services
              </Link>
            </div>
            <div className="flex flex-wrap gap-x-8 gap-y-3">
              {[
                "No hidden fees",
                "Same-day response",
                "Apply from anywhere",
              ].map((label) => (
                <div
                  key={label}
                  className="flex items-center gap-2 text-gray-600 text-sm font-medium"
                >
                  <span style={{ color: accent }}>
                    <IconCheck />
                  </span>{" "}
                  {label}
                </div>
              ))}
            </div>
          </div>

          {/* Credibility card — formal ledger-style, not a calculator */}
          <div
            className="rounded-2xl shadow-xl p-8 border-t-4"
            style={{ backgroundColor: primary, borderColor: accent }}
          >
            <div className="text-white/50 text-xs font-bold uppercase tracking-widest mb-1">
              {tenant.name}
            </div>
            <div className="text-white text-2xl font-bold mb-6" style={serif}>
              Why Clients Choose Us
            </div>
            <div className="space-y-5">
              {pillars.slice(0, 3).map((p) => (
                <div key={p.title} className="flex items-start gap-3">
                  <div
                    className="w-9 h-9 rounded-full flex items-center justify-center flex-shrink-0"
                    style={{ backgroundColor: accent, color: primary }}
                  >
                    <p.Icon />
                  </div>
                  <div>
                    <div className="text-white font-semibold text-sm">
                      {p.title}
                    </div>
                    <div className="text-white/50 text-xs leading-relaxed mt-0.5">
                      {p.desc}
                    </div>
                  </div>
                </div>
              ))}
            </div>
            <Link
              href="/apply"
              className="block text-center mt-7 py-3 rounded-full font-bold text-sm"
              style={{ backgroundColor: accent, color: primary }}
            >
              Start Your Application →
            </Link>
          </div>
        </div>
      </section>

      {/* ── STATS — by the numbers ── */}
      {tenant.stats && tenant.stats.length > 0 && (
        <section className="border-b border-gray-100 bg-gray-50/80">
          <div className="max-w-7xl mx-auto px-4 py-10">
            <div className="grid grid-cols-2 md:grid-cols-4 divide-x divide-gray-200">
              {tenant.stats.map((stat, i) => (
                <StatCard
                  key={stat.label}
                  stat={stat}
                  primary={primary}
                  delay={i}
                />
              ))}
            </div>
          </div>
        </section>
      )}

      {/* ── TRUST STRIP ── */}
      <section
        className="py-6 border-b border-gray-100"
        style={{ backgroundColor: primary + "06" }}
      >
        <div
          className="max-w-7xl mx-auto px-4 flex flex-wrap items-center justify-center gap-x-10 gap-y-3 text-sm font-semibold"
          style={{ color: primary }}
        >
          <span className="flex items-center gap-2">
            🛡️ Regulated Institution
          </span>
          <span className="flex items-center gap-2">
            🔒 Bank-Grade Security
          </span>
          <span className="flex items-center gap-2">
            📄 Transparent Documentation
          </span>
          <span className="flex items-center gap-2">⏱ 24-Hour Response</span>
        </div>
      </section>

      {/* ── WHY CHOOSE US — pillars ── */}
      <section className="py-20 max-w-7xl mx-auto px-4">
        <div className="text-center mb-14">
          <div
            className="text-xs font-bold uppercase tracking-widest mb-2"
            style={{ color: accent }}
          >
            Our Commitment
          </div>
          <h2 className="text-3xl font-bold text-gray-900" style={serif}>
            Why Clients Choose {tenant.name}
          </h2>
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
          {pillars.map((p) => (
            <div
              key={p.title}
              className="text-center p-6 rounded-xl border border-gray-100 hover:shadow-md transition-shadow"
            >
              <div
                className="w-14 h-14 rounded-full flex items-center justify-center mx-auto mb-4"
                style={{ backgroundColor: primary + "10", color: primary }}
              >
                <p.Icon />
              </div>
              <div className="font-bold text-gray-900 mb-2">{p.title}</div>
              <div className="text-gray-500 text-sm leading-relaxed">
                {p.desc}
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* ── SERVICES ── */}
      <section className="py-20" style={{ backgroundColor: "#FAFAFA" }}>
        <div className="max-w-7xl mx-auto px-4">
          <div className="text-center mb-14">
            <div
              className="text-xs font-bold uppercase tracking-widest mb-2"
              style={{ color: accent }}
            >
              Our Products
            </div>
            <h2 className="text-3xl font-bold text-gray-900 mb-4" style={serif}>
              Lending Solutions For Every Need
            </h2>
            <p className="text-gray-500 text-lg max-w-2xl mx-auto">
              Tailored credit for individuals, businesses, and salaried
              employees across{" "}
              {tenant.country === "RW" ? "Rwanda" : tenant.country}.
            </p>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {tenant.services?.map((service, i) => (
              <div
                key={service.title}
                className="relative bg-white rounded-xl border border-gray-200 p-7 pt-9 hover:shadow-lg transition-all duration-200"
              >
                <div
                  className="absolute -top-4 left-7 w-9 h-9 rounded-full flex items-center justify-center text-sm font-bold text-white shadow"
                  style={{ backgroundColor: accent }}
                >
                  {String(i + 1).padStart(2, "0")}
                </div>
                <h3
                  className="text-lg font-bold text-gray-900 mb-2"
                  style={serif}
                >
                  {service.title}
                </h3>
                <p className="text-gray-500 text-sm leading-relaxed mb-5">
                  {service.description}
                </p>
                <div className="flex items-center justify-between text-xs mb-5 pb-5 border-b border-gray-100">
                  <span
                    className="font-bold px-3 py-1.5 rounded-full"
                    style={{ backgroundColor: primary + "12", color: primary }}
                  >
                    From {service.rate} p.a.
                  </span>
                  <span className="text-gray-400">
                    Up to {tenant.currency} {service.maxAmount}
                  </span>
                </div>
                <Link
                  href={`/apply?type=${service.title.replace(/ /g, "_").toUpperCase()}`}
                  className="block text-center py-2.5 rounded-full text-sm font-bold border-2 transition-colors"
                  style={{ borderColor: primary, color: primary }}
                  onMouseEnter={(e) => {
                    (e.target as HTMLElement).style.backgroundColor = primary;
                    (e.target as HTMLElement).style.color = "#fff";
                  }}
                  onMouseLeave={(e) => {
                    (e.target as HTMLElement).style.backgroundColor =
                      "transparent";
                    (e.target as HTMLElement).style.color = primary;
                  }}
                >
                  Apply Now →
                </Link>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ── LOAN CALCULATOR — its own dedicated section ── */}
      <section className="py-20 max-w-5xl mx-auto px-4">
        <div className="rounded-2xl border border-gray-200 shadow-sm overflow-hidden grid md:grid-cols-2">
          <div
            className="p-10 text-white flex flex-col justify-center"
            style={{ backgroundColor: primary }}
          >
            <div
              className="text-xs font-bold uppercase tracking-widest mb-3"
              style={{ color: accent }}
            >
              Plan Ahead
            </div>
            <h2 className="text-2xl font-bold mb-4" style={serif}>
              Estimate Your Repayment
            </h2>
            <p className="text-white/55 text-sm leading-relaxed">
              Use our calculator to get an instant estimate of your monthly
              repayment. Final rates and terms are confirmed after credit
              assessment.
            </p>
          </div>
          <div className="p-8 bg-white">
            <LoanCalculator
              primary={primary}
              accent={accent}
              currency={tenant.currency}
            />
          </div>
        </div>
      </section>

      {/* ── HOW IT WORKS — vertical stepper ── */}
      <section className="py-20" style={{ backgroundColor: "#FAFAFA" }}>
        <div className="max-w-3xl mx-auto px-4">
          <div className="text-center mb-14">
            <div
              className="text-xs font-bold uppercase tracking-widest mb-2"
              style={{ color: accent }}
            >
              Our Process
            </div>
            <h2 className="text-3xl font-bold text-gray-900" style={serif}>
              How to Get a Loan
            </h2>
          </div>
          <div className="relative">
            <div
              className="absolute left-5 top-2 bottom-2 w-[2px]"
              style={{ backgroundColor: primary + "20" }}
            />
            {[
              {
                step: "1",
                title: "Apply Online",
                desc: "Complete our application form in a few minutes, from any device.",
              },
              {
                step: "2",
                title: "Submit Documents",
                desc: "Upload your ID and supporting documents securely — no branch visit required.",
              },
              {
                step: "3",
                title: "Get Approved",
                desc: "Our credit team reviews your application and responds within 24 hours.",
              },
              {
                step: "4",
                title: "Receive Funds",
                desc: "Approved funds are disbursed directly to your mobile money or bank account.",
              },
            ].map((item) => (
              <div
                key={item.step}
                className="relative flex items-start gap-6 pb-10 last:pb-0"
              >
                <div
                  className="w-10 h-10 rounded-full flex items-center justify-center text-white font-bold text-sm flex-shrink-0 relative z-10"
                  style={{ backgroundColor: primary }}
                >
                  {item.step}
                </div>
                <div className="bg-white rounded-lg p-5 border border-gray-100 flex-1">
                  <div className="font-bold text-gray-900 mb-1">
                    {item.title}
                  </div>
                  <div className="text-gray-500 text-sm">{item.desc}</div>
                </div>
              </div>
            ))}
          </div>
          <div className="text-center mt-4">
            <Link
              href="/apply"
              className="inline-block px-10 py-3.5 rounded-full text-white font-bold text-base shadow-md hover:opacity-90 transition-opacity"
              style={{ backgroundColor: primary }}
            >
              Start Your Application →
            </Link>
          </div>
        </div>
      </section>

      {/* ── TEAM ── */}
      {tenant.team && tenant.team.length > 0 && (
        <section className="py-20 max-w-7xl mx-auto px-4">
          <div className="text-center mb-14">
            <div
              className="text-xs font-bold uppercase tracking-widest mb-2"
              style={{ color: accent }}
            >
              Our People
            </div>
            <h2 className="text-3xl font-bold text-gray-900" style={serif}>
              Meet the {tenant.name} Team
            </h2>
          </div>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-6">
            {tenant.team.map((member) => (
              <div
                key={member.name}
                className="text-center p-6 rounded-xl border border-gray-100 hover:shadow-md transition-shadow"
              >
                <div
                  className="w-16 h-16 rounded-full flex items-center justify-center mx-auto mb-4 text-white font-bold text-lg"
                  style={{ backgroundColor: primary }}
                >
                  {member.initials}
                </div>
                <div className="font-bold text-gray-900 mb-1">
                  {member.name}
                </div>
                <div className="text-gray-500 text-sm">{member.role}</div>
              </div>
            ))}
          </div>
        </section>
      )}

      {/* ── TESTIMONIALS ── */}
      {tenant.testimonials && tenant.testimonials.length > 0 && (
        <section className="py-20 max-w-7xl mx-auto px-4">
          <div className="text-center mb-14">
            <div
              className="text-xs font-bold uppercase tracking-widest mb-2"
              style={{ color: accent }}
            >
              Client Stories
            </div>
            <h2 className="text-3xl font-bold text-gray-900" style={serif}>
              What Our Clients Say
            </h2>
          </div>
          <div className="grid md:grid-cols-3 gap-6">
            {tenant.testimonials.map((t, i) => (
              <TestimonialCard
                key={t.name}
                t={t}
                primary={primary}
                accent={accent}
                delay={i}
              />
            ))}
          </div>
        </section>
      )}

      {/* ── CTA BANNER — light, formal ── */}
      <section className="py-16 mx-4 md:mx-auto max-w-7xl mb-16">
        <div
          className="rounded-2xl p-12 text-center relative overflow-hidden border-2"
          style={{ backgroundColor: accent + "10", borderColor: accent + "40" }}
        >
          <h2
            className="text-3xl md:text-4xl font-bold mb-4 relative z-10 text-gray-900"
            style={serif}
          >
            Ready to Take the Next Step?
          </h2>
          <p className="text-gray-600 text-lg mb-8 relative z-10 max-w-xl mx-auto">
            Apply today and get a response within 24 hours. No hidden fees, no
            surprises — just honest lending.
          </p>
          <Link
            href="/apply"
            className="inline-block px-12 py-3.5 rounded-full font-bold text-base shadow-lg hover:opacity-90 transition-opacity relative z-10"
            style={{ backgroundColor: primary, color: "#fff" }}
          >
            Apply for a Loan Now →
          </Link>
        </div>
      </section>
    </div>
  );
}

// Inline calculator component
function LoanCalculator({
  primary,
  accent,
  currency,
}: {
  primary: string;
  accent: string;
  currency: string;
}) {
  const [amount, setAmount] = React.useState(500000);
  const [months, setMonths] = React.useState(12);
  const [rate, setRate] = React.useState(15);
  const mr = rate / 100 / 12;
  const monthly =
    mr === 0
      ? amount / months
      : (amount * (mr * Math.pow(1 + mr, months))) /
        (Math.pow(1 + mr, months) - 1);
  const total = monthly * months;
  const interest = total - amount;
  const fmt = (n: number) =>
    n.toLocaleString("en-RW", { maximumFractionDigits: 0 });

  return (
    <div>
      <div className="mb-4">
        <label className="text-xs font-bold text-gray-500 uppercase tracking-wider">
          Loan Amount ({currency})
        </label>
        <input
          type="range"
          min={100000}
          max={10000000}
          step={100000}
          value={amount}
          onChange={(e) => setAmount(Number(e.target.value))}
          className="w-full mt-2"
          style={{ accentColor: primary }}
        />
        <div className="text-2xl font-bold mt-1" style={{ color: primary }}>
          {currency} {fmt(amount)}
        </div>
      </div>
      <div className="mb-4">
        <label className="text-xs font-bold text-gray-500 uppercase tracking-wider">
          Loan Term
        </label>
        <div className="flex gap-2 flex-wrap mt-2">
          {[3, 6, 12, 24, 36, 48].map((m) => (
            <button
              key={m}
              onClick={() => setMonths(m)}
              className="px-3 py-1.5 rounded-full text-sm font-semibold border-2 transition-all"
              style={
                months === m
                  ? {
                      backgroundColor: primary,
                      color: "#fff",
                      borderColor: primary,
                    }
                  : { borderColor: "#e5e7eb", color: "#6b7280" }
              }
            >
              {m}mo
            </button>
          ))}
        </div>
      </div>
      <div className="grid grid-cols-3 gap-3 my-6 bg-gray-50 rounded-lg p-4 border border-gray-100">
        {[
          ["Monthly", currency + " " + fmt(monthly)],
          ["Total", currency + " " + fmt(total)],
          ["Interest", currency + " " + fmt(interest)],
        ].map(([label, value]) => (
          <div key={label} className="text-center">
            <div className="text-[10px] text-gray-400 uppercase font-bold">
              {label}
            </div>
            <div
              className="text-sm font-bold mt-0.5"
              style={{ color: primary }}
            >
              {value}
            </div>
          </div>
        ))}
      </div>
      <p className="text-[11px] text-gray-400 mb-4">
        Estimate only. Final rate and terms are confirmed after credit
        assessment.
      </p>
      <Link
        href="/apply"
        className="block text-center py-3.5 rounded-full text-white font-bold text-base shadow-md hover:opacity-90 transition-opacity"
        style={{ backgroundColor: primary }}
      >
        Apply for This Loan →
      </Link>
    </div>
  );
}

function StatCard({
  stat,
  primary,
  delay,
}: {
  stat: { icon?: string; value: string; label: string };
  primary: string;
  delay: number;
}) {
  const { ref, visible } = useScrollReveal();
  const numericMatch = stat.value.match(/^([\d,]+)$/);
  const numericTarget = numericMatch
    ? Number(numericMatch[1].replace(/,/g, ""))
    : null;
  const animated = useCountUp(
    numericTarget ?? 0,
    visible && numericTarget !== null,
  );

  return (
    <div
      ref={ref}
      className={`reveal reveal-delay-${Math.min(delay + 1, 4)} ${visible ? "reveal-visible" : ""} text-center px-4`}
    >
      {stat.icon && <div className="text-2xl mb-1">{stat.icon}</div>}
      <div
        className="text-2xl md:text-3xl font-black"
        style={{ color: primary }}
      >
        {numericTarget !== null ? `${animated.toLocaleString()}+` : stat.value}
      </div>
      <div className="text-xs md:text-sm text-gray-500 font-semibold mt-1">
        {stat.label}
      </div>
    </div>
  );
}

function TestimonialCard({
  t,
  primary,
  accent,
  delay,
}: {
  t: { name: string; role: string; text: string; rating?: number };
  primary: string;
  accent: string;
  delay: number;
}) {
  const { ref, visible } = useScrollReveal();
  return (
    <div
      ref={ref}
      className={`reveal reveal-delay-${Math.min(delay + 1, 4)} ${visible ? "reveal-visible" : ""}
      card-lift bg-white rounded-xl p-6 border border-gray-100 border-t-4`}
      style={{ borderTopColor: accent }}
    >
      <div className="flex mb-3">
        {"★★★★★".split("").map((s, i) => (
          <span key={i} style={{ color: accent }} className="text-lg">
            {s}
          </span>
        ))}
      </div>
      <p className="text-gray-600 text-sm leading-relaxed mb-4">
        &ldquo;{t.text}&rdquo;
      </p>
      <div className="flex items-center gap-3">
        <div
          className="w-9 h-9 rounded-full flex items-center justify-center text-white font-bold text-sm"
          style={{ backgroundColor: primary }}
        >
          {t.name[0]}
        </div>
        <div>
          <div className="font-bold text-gray-900 text-sm">{t.name}</div>
          <div className="text-gray-400 text-xs">{t.role}</div>
        </div>
      </div>
    </div>
  );
}
