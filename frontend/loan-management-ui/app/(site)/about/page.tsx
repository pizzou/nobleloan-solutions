"use client";

import Link from "next/link";
import { useTenant } from "../layout";

export default function AboutPage() {
  const tenant = useTenant();
  if (!tenant) return null;

  const primary = tenant.primaryColor;
  const accent = tenant.accentColor;
  const services = tenant.services ?? [];
  const team = tenant.team ?? [];
  const currency = tenant.currency || "RWF";

  return (
    <div className="bg-white pb-20">
      <section
        className="relative overflow-hidden text-white"
        style={{ background: `linear-gradient(135deg, ${primary}, #071427)` }}
      >
        <div className="absolute -left-32 -top-24 h-80 w-80 rounded-full bg-white/10 blur-3xl" />
        <div className="relative mx-auto grid max-w-7xl gap-10 px-4 py-20 md:grid-cols-[1.15fr_0.85fr] md:items-end md:py-24">
          <div>
            <div className="text-[11px] font-black uppercase tracking-[0.24em] text-white/45">
              About {tenant.name}
            </div>
            <h1 className="mt-4 max-w-4xl text-4xl font-black tracking-tight md:text-6xl">
              A professional lending partner built around transparency.
            </h1>
            <p className="mt-5 max-w-2xl text-lg leading-8 text-white/65">
              {tenant.tagline ||
                tenant.hero?.subtext ||
                `Learn about ${tenant.name}, our purpose and the services we provide.`}
            </p>
          </div>
          <div className="rounded-[2rem] border border-white/10 bg-white/[0.06] p-6 backdrop-blur">
            <div className="text-[10px] font-black uppercase tracking-[0.2em] text-white/45">
              Company profile
            </div>
            <div className="mt-5 grid grid-cols-2 gap-3">
              {[
                ["Country", tenant.country || "—"],
                ["Currency", currency],
                ["Founded", tenant.founded || "—"],
                ["Products", String(services.length)],
              ].map(([label, value]) => (
                <div key={label} className="rounded-2xl bg-white/[0.05] p-4">
                  <div className="text-[10px] font-black uppercase tracking-wider text-white/40">
                    {label}
                  </div>
                  <div className="mt-2 text-sm font-black text-white">
                    {value}
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      <main className="mx-auto max-w-7xl px-4 pt-16">
        <section className="grid gap-7 lg:grid-cols-2">
          <div className="rounded-[2rem] border border-slate-200 bg-slate-50 p-8 md:p-10">
            <div
              className="text-[11px] font-black uppercase tracking-[0.2em]"
              style={{ color: accent }}
            >
              Our mission
            </div>
            <h2 className="mt-3 text-3xl font-black tracking-tight text-slate-950">
              Purpose first. Lending second.
            </h2>
            <p className="mt-5 text-base leading-8 text-slate-600">
              {tenant.mission ||
                `${tenant.name} is committed to delivering clear, responsible and accessible financial services.`}
            </p>
          </div>
          <div className="rounded-[2rem] border border-slate-200 bg-white p-8 shadow-sm md:p-10">
            <div
              className="text-[11px] font-black uppercase tracking-[0.2em]"
              style={{ color: accent }}
            >
              Our vision
            </div>
            <h2 className="mt-3 text-3xl font-black tracking-tight text-slate-950">
              Long-term trust matters.
            </h2>
            <p className="mt-5 text-base leading-8 text-slate-600">
              {tenant.vision ||
                `To be a trusted financial partner for the communities and businesses we serve.`}
            </p>
          </div>
        </section>

        <section className="mt-10 rounded-[2rem] border border-slate-200 bg-white p-8 shadow-sm md:p-10">
          <div className="grid gap-8 lg:grid-cols-[1fr_0.9fr] lg:items-center">
            <div>
              <div
                className="text-[11px] font-black uppercase tracking-[0.2em]"
                style={{ color: accent }}
              >
                How we work
              </div>
              <h2 className="mt-3 text-3xl font-black tracking-tight text-slate-950">
                A modern digital lending experience.
              </h2>
              <p className="mt-4 max-w-2xl text-base leading-8 text-slate-600">
                {tenant.name} combines human support with a digital workflow for
                applications, documents, repayment visibility and secure
                communication.
              </p>
              <div className="mt-7 grid gap-3 sm:grid-cols-2">
                {[
                  "Clear product information",
                  "Secure online application",
                  "Digital document workflow",
                  "Repayment visibility",
                  "Direct customer support",
                  "Audit-ready records",
                ].map((item, index) => (
                  <div
                    key={item}
                    className="flex items-center gap-3 rounded-2xl bg-slate-50 p-4"
                  >
                    <span
                      className="flex h-8 w-8 items-center justify-center rounded-full text-xs font-black text-white"
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
              className="rounded-[2rem] p-7 text-white"
              style={{
                background: `linear-gradient(150deg, ${primary}, #071427)`,
              }}
            >
              <div className="text-[10px] font-black uppercase tracking-[0.2em] text-white/45">
                Why it matters
              </div>
              <div className="mt-4 space-y-5">
                <div>
                  <div className="text-2xl font-black">Transparent</div>
                  <p className="mt-1 text-sm leading-6 text-white/55">
                    Published products and terms reduce uncertainty before you
                    apply.
                  </p>
                </div>
                <div>
                  <div className="text-2xl font-black">Connected</div>
                  <p className="mt-1 text-sm leading-6 text-white/55">
                    Applications, documents and servicing operate through one
                    digital platform.
                  </p>
                </div>
                <div>
                  <div className="text-2xl font-black">Responsible</div>
                  <p className="mt-1 text-sm leading-6 text-white/55">
                    Final credit decisions and repayment schedules remain
                    subject to the lender's approval process.
                  </p>
                </div>
              </div>
            </div>
          </div>
        </section>

        {team.length > 0 && (
          <section className="mt-16">
            <div className="max-w-2xl">
              <div
                className="text-[11px] font-black uppercase tracking-[0.2em]"
                style={{ color: accent }}
              >
                Leadership
              </div>
              <h2 className="mt-3 text-3xl font-black tracking-tight text-slate-950">
                The people behind {tenant.name}.
              </h2>
            </div>
            <div className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              {team.map((person) => (
                <div
                  key={`${person.name}-${person.role}`}
                  className="rounded-[1.5rem] border border-slate-200 bg-white p-5 shadow-sm"
                >
                  <div
                    className="flex h-14 w-14 items-center justify-center rounded-2xl text-lg font-black"
                    style={{ backgroundColor: `${accent}18`, color: primary }}
                  >
                    {person.initials || person.name.slice(0, 2).toUpperCase()}
                  </div>
                  <div className="mt-4 text-base font-black text-slate-950">
                    {person.name}
                  </div>
                  <div className="mt-1 text-xs text-slate-500">
                    {person.role || "Team member"}
                  </div>
                </div>
              ))}
            </div>
          </section>
        )}

        <section
          className="mt-16 overflow-hidden rounded-[2rem] p-8 text-white md:p-12"
          style={{ background: `linear-gradient(135deg, ${primary}, #071427)` }}
        >
          <div className="grid gap-7 md:grid-cols-[1fr_auto] md:items-center">
            <div>
              <div className="text-[10px] font-black uppercase tracking-[0.2em] text-white/45">
                Ready when you are
              </div>
              <h2 className="mt-2 text-3xl font-black">
                Explore the products offered by {tenant.name}.
              </h2>
            </div>
            <div className="flex flex-wrap gap-3">
              <Link
                href="/services"
                className="rounded-2xl bg-white px-6 py-3 text-sm font-black"
                style={{ color: primary }}
              >
                View services
              </Link>
              <Link
                href="/contact"
                className="rounded-2xl border border-white/20 px-6 py-3 text-sm font-bold text-white"
              >
                Contact us
              </Link>
            </div>
          </div>
        </section>
      </main>
    </div>
  );
}
