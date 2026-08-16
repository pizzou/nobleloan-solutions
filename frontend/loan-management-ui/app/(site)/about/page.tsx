"use client";

import Link from "next/link";
import { useTenant } from "../layout";

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

export default function AboutPage() {
  const tenant = useTenant();

  if (!tenant) return null;

  const primary = tenant.primaryColor;
  const accent = tenant.accentColor;
  const services = tenant.services ?? [];
  const team = tenant.team ?? [];
  const stats = tenant.stats ?? [];

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
          className="absolute -left-32 -top-32 h-[480px] w-[480px] rounded-full blur-3xl"
          style={{
            backgroundColor: `${accent}16`,
          }}
        />

        <div className="relative mx-auto grid max-w-7xl gap-12 px-4 py-20 md:py-28 lg:grid-cols-[1.1fr_0.9fr] lg:items-end">
          <div>
            <SectionEyebrow color={accent}>About {tenant.name}</SectionEyebrow>

            <h1 className="mt-5 max-w-4xl text-5xl font-black leading-[0.98] tracking-[-0.05em] md:text-7xl">
              Built around trust, clarity and responsible lending.
            </h1>

            <p className="mt-7 max-w-2xl text-lg leading-8 text-white/60">
              {tenant.tagline ||
                tenant.hero?.subtext ||
                `Learn about ${tenant.name}, our purpose and the people behind our financial services.`}
            </p>
          </div>

          <div className="rounded-[2rem] border border-white/10 bg-white/[0.05] p-7 backdrop-blur-xl">
            <div className="text-[9px] font-black uppercase tracking-[0.22em] text-white/35">
              Institutional profile
            </div>

            <div className="mt-6 grid grid-cols-2 gap-3">
              {[
                ["Country", tenant.country || "—"],
                ["Currency", tenant.currency || "—"],
                ["Founded", tenant.founded || "—"],
                ["Products", String(services.length)],
              ].map(([label, value]) => (
                <div
                  key={label}
                  className="rounded-xl border border-white/10 bg-white/[0.035] p-4"
                >
                  <div className="text-[9px] font-black uppercase tracking-wider text-white/35">
                    {label}
                  </div>

                  <div className="mt-2 text-sm font-black text-white">
                    {value}
                  </div>
                </div>
              ))}
            </div>

            {tenant.registrationNumber && (
              <div className="mt-3 rounded-xl border border-white/10 bg-white/[0.035] p-4">
                <div className="text-[9px] font-black uppercase tracking-wider text-white/35">
                  Registration
                </div>

                <div className="mt-2 text-sm font-black text-white">
                  {tenant.registrationNumber}
                </div>
              </div>
            )}
          </div>
        </div>
      </section>

      {/* MISSION / VISION */}
      <main className="mx-auto max-w-7xl px-4 pt-16 md:pt-20">
        <section className="grid gap-5 lg:grid-cols-2">
          <div className="rounded-[2rem] bg-[#f7f8fa] p-8 md:p-10">
            <SectionEyebrow color={accent}>Our mission</SectionEyebrow>

            <h2 className="mt-4 text-3xl font-black tracking-tight text-slate-950 md:text-4xl">
              Purpose first.
            </h2>

            <p className="mt-5 text-base leading-8 text-slate-600">
              {tenant.mission ||
                `${tenant.name} is committed to delivering clear, responsible and accessible financial services.`}
            </p>
          </div>

          <div className="rounded-[2rem] border border-slate-200 bg-white p-8 shadow-sm md:p-10">
            <SectionEyebrow color={accent}>Our vision</SectionEyebrow>

            <h2 className="mt-4 text-3xl font-black tracking-tight text-slate-950 md:text-4xl">
              Long-term trust matters.
            </h2>

            <p className="mt-5 text-base leading-8 text-slate-600">
              {tenant.vision ||
                `To be a trusted financial partner for the communities and businesses we serve.`}
            </p>
          </div>
        </section>

        {/* STATS */}
        {stats.length > 0 && (
          <section className="mt-6 grid grid-cols-2 overflow-hidden rounded-[2rem] border border-slate-200 bg-white md:grid-cols-4">
            {stats.slice(0, 4).map((stat, index) => (
              <div
                key={`${stat.label}-${stat.value}`}
                className={`p-7 ${
                  index !== 0 ? "border-l border-slate-200" : ""
                }`}
              >
                <div className="text-lg">{stat.icon || "•"}</div>

                <div className="mt-3 text-3xl font-black tracking-tight text-slate-950">
                  {stat.value}
                </div>

                <div className="mt-2 text-[9px] font-black uppercase tracking-[0.16em] text-slate-400">
                  {stat.label}
                </div>
              </div>
            ))}
          </section>
        )}

        {/* OPERATING MODEL */}
        <section className="mt-6 overflow-hidden rounded-[2rem] border border-slate-200 bg-white shadow-sm">
          <div className="grid lg:grid-cols-[1fr_0.8fr]">
            <div className="p-8 md:p-12">
              <SectionEyebrow color={accent}>How we work</SectionEyebrow>

              <h2 className="mt-4 text-3xl font-black tracking-tight text-slate-950 md:text-4xl">
                A modern lending experience with human support.
              </h2>

              <p className="mt-5 max-w-2xl text-base leading-8 text-slate-600">
                {tenant.name} combines digital convenience with structured
                lending processes so customers can access information, apply and
                follow their financial journey through one experience.
              </p>

              <div className="mt-8 grid gap-3 sm:grid-cols-2">
                {[
                  "Clear product information",
                  "Secure online application",
                  "Digital document workflow",
                  "Repayment visibility",
                  "Direct customer support",
                  "Professional servicing",
                ].map((item, index) => (
                  <div
                    key={item}
                    className="flex items-center gap-3 rounded-xl bg-slate-50 p-4"
                  >
                    <span
                      className="flex h-8 w-8 items-center justify-center rounded-full text-[10px] font-black text-white"
                      style={{
                        backgroundColor: index % 2 === 0 ? primary : accent,
                        color: index % 2 === 0 ? "#fff" : primary,
                      }}
                    >
                      {index + 1}
                    </span>

                    <span className="text-sm font-bold text-slate-800">
                      {item}
                    </span>
                  </div>
                ))}
              </div>
            </div>

            <div
              className="p-8 text-white md:p-12"
              style={{
                background: `linear-gradient(150deg, ${primary}, #071426)`,
              }}
            >
              <SectionEyebrow color={accent}>What guides us</SectionEyebrow>

              <div className="mt-8 space-y-7">
                {[
                  [
                    "Transparency",
                    "Important product information should be clear before a customer commits.",
                  ],
                  [
                    "Responsibility",
                    "Credit decisions and final repayment schedules remain subject to the lender's approval process.",
                  ],
                  [
                    "Service",
                    "Customers deserve professional support before, during and after financing.",
                  ],
                ].map(([title, text]) => (
                  <div key={title}>
                    <h3 className="text-2xl font-black">{title}</h3>

                    <p className="mt-2 text-sm leading-7 text-white/55">
                      {text}
                    </p>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </section>

        {/* LEADERSHIP */}
        {team.length > 0 && (
          <section className="mt-20">
            <SectionEyebrow color={accent}>Leadership</SectionEyebrow>

            <h2 className="mt-4 text-3xl font-black tracking-tight text-slate-950 md:text-4xl">
              The people behind {tenant.name}.
            </h2>

            <div className="mt-9 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              {team.map((person) => (
                <article
                  key={`${person.name}-${person.role}`}
                  className="rounded-[1.5rem] border border-slate-200 bg-white p-6 transition hover:-translate-y-1 hover:shadow-xl"
                >
                  <div
                    className="flex h-16 w-16 items-center justify-center rounded-2xl text-lg font-black"
                    style={{
                      backgroundColor: `${accent}18`,
                      color: primary,
                    }}
                  >
                    {person.initials || person.name.slice(0, 2).toUpperCase()}
                  </div>

                  <h3 className="mt-5 text-base font-black text-slate-950">
                    {person.name}
                  </h3>

                  <p className="mt-1 text-xs text-slate-500">
                    {person.role || "Team member"}
                  </p>
                </article>
              ))}
            </div>
          </section>
        )}

        {/* CTA */}
        <section className="mt-20">
          <div
            className="rounded-[2rem] p-8 text-white md:p-12"
            style={{
              background: `linear-gradient(135deg, ${primary}, #071426)`,
            }}
          >
            <div className="flex flex-col justify-between gap-8 md:flex-row md:items-center">
              <div>
                <SectionEyebrow color={accent}>
                  Work with {tenant.name}
                </SectionEyebrow>

                <h2 className="mt-3 text-3xl font-black tracking-tight md:text-4xl">
                  Explore the financing solutions available to you.
                </h2>
              </div>

              <div className="flex flex-wrap gap-3">
                <Link
                  href="/services"
                  className="rounded-xl bg-white px-6 py-4 text-sm font-black"
                  style={{
                    color: primary,
                  }}
                >
                  View Services
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
      </main>
    </div>
  );
}
