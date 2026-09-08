"use client";

import Link from "next/link";
import { useTenant } from "../layout";

function Check() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      className="h-4 w-4"
    >
      <path d="m5 12 4 4L19 6" />
    </svg>
  );
}
function Shield() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      className="h-5 w-5"
    >
      <path d="M12 3 20 6v6c0 5-3.5 8.5-8 10-4.5-1.5-8-5-8-10V6l8-3Z" />
      <path d="m9 12 2 2 4-4" />
    </svg>
  );
}

export default function AboutPage() {
  const tenant = useTenant();
  if (!tenant) return null;
  const primary = tenant.primaryColor || "#0B1F3A";
  const accent = tenant.accentColor || "#D4AF37";
  const stats = [
    [tenant.founded || "—", "Founded"],
    [tenant.registrationNumber || "—", "Registration"],
    [String(tenant.services?.length || 0), "Active solutions"],
    [tenant.country || "Rwanda", "Market"],
  ];
  return (
    <main className="bg-white text-slate-950">
      <section className="relative overflow-hidden bg-[#061326] text-white">
        <div
          className="absolute inset-0"
          style={{
            background: `radial-gradient(circle at 90% 15%,${accent}24,transparent 26%),linear-gradient(135deg,#061326,${primary})`,
          }}
        />
        <div className="relative mx-auto grid max-w-7xl gap-12 px-5 py-20 sm:px-8 lg:grid-cols-[1fr_.8fr] lg:items-center lg:py-28">
          <div>
            <div
              className="text-[10px] font-black uppercase tracking-[.22em]"
              style={{ color: accent }}
            >
              About {tenant.name}
            </div>
            <h1 className="mt-4 text-5xl font-black leading-[1.02] tracking-[-.05em] sm:text-6xl">
              Built to make responsible finance feel simpler.
            </h1>
            <p className="mt-6 max-w-2xl text-base leading-7 text-white/60 sm:text-lg">
              {tenant.mission ||
                tenant.tagline ||
                "We help individuals and businesses access clear, responsible financial solutions with confidence."}
            </p>
          </div>
          <div className="grid grid-cols-2 gap-3">
            {stats.map(([value, label]) => (
              <div
                key={label}
                className="rounded-2xl border border-white/10 bg-white/[.055] p-5 backdrop-blur"
              >
                <div className="text-xl font-black" style={{ color: accent }}>
                  {value}
                </div>
                <div className="mt-2 text-[9px] font-black uppercase tracking-[.15em] text-white/40">
                  {label}
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>
      <section className="mx-auto max-w-7xl px-5 py-20 sm:px-8 lg:py-28">
        <div className="grid gap-6 md:grid-cols-3">
          {[
            [
              "01",
              "Mission",
              tenant.mission ||
                "Deliver responsible financial support with clarity and dignity.",
            ],
            [
              "02",
              "Vision",
              tenant.vision ||
                "Build long-term financial confidence through trusted relationships.",
            ],
            [
              "03",
              "Values",
              "Integrity, transparency, inclusion, innovation and excellence.",
            ],
          ].map(([n, title, text]) => (
            <article
              key={title}
              className="rounded-[26px] border border-slate-200 bg-white p-7 shadow-[0_12px_45px_rgba(15,23,42,.045)]"
            >
              <div className="flex items-center justify-between">
                <span
                  className="text-[10px] font-black tracking-[.18em]"
                  style={{ color: accent }}
                >
                  {n}
                </span>
                <span
                  className="flex h-10 w-10 items-center justify-center rounded-xl"
                  style={{ backgroundColor: `${primary}0D`, color: primary }}
                >
                  <Shield />
                </span>
              </div>
              <h2 className="mt-8 text-xl font-black">{title}</h2>
              <p className="mt-3 text-sm leading-7 text-slate-500">{text}</p>
            </article>
          ))}
        </div>
      </section>
      <section className="bg-slate-50">
        <div className="mx-auto grid max-w-7xl gap-12 px-5 py-20 sm:px-8 lg:grid-cols-[.8fr_1.2fr] lg:items-center lg:py-24">
          <div>
            <div
              className="text-[10px] font-black uppercase tracking-[.22em]"
              style={{ color: accent }}
            >
              Why clients choose us
            </div>
            <h2 className="mt-4 text-4xl font-black tracking-[-.04em] sm:text-5xl">
              Trust is part of the product.
            </h2>
            <p className="mt-5 text-base leading-7 text-slate-500">
              A premium financial experience is not only about speed. It is
              about knowing what you are agreeing to, protecting your
              information and having someone available when you need help.
            </p>
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            {[
              "Transparent pricing and terms",
              "Secure digital application journey",
              "Responsible credit assessment",
              "Human support throughout repayment",
              "Clear communication at every stage",
              "Solutions designed for real needs",
            ].map((x) => (
              <div
                key={x}
                className="flex items-center gap-3 rounded-2xl border border-slate-200 bg-white p-4"
              >
                <span
                  className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg"
                  style={{ backgroundColor: `${accent}18`, color: primary }}
                >
                  <Check />
                </span>
                <span className="text-sm font-bold text-slate-700">{x}</span>
              </div>
            ))}
          </div>
        </div>
      </section>
      {tenant.team?.length ? (
        <section className="mx-auto max-w-7xl px-5 py-20 sm:px-8 lg:py-28">
          <div className="text-center">
            <div
              className="text-[10px] font-black uppercase tracking-[.22em]"
              style={{ color: accent }}
            >
              Leadership
            </div>
            <h2 className="mt-3 text-4xl font-black tracking-[-.04em]">
              Experienced people behind the service.
            </h2>
          </div>
          <div className="mt-12 grid grid-cols-2 gap-5 md:grid-cols-4">
            {tenant.team.map((p) => (
              <div
                key={p.name}
                className="rounded-[24px] border border-slate-200 bg-white p-6 text-center shadow-sm"
              >
                <div
                  className="mx-auto flex h-16 w-16 items-center justify-center rounded-2xl text-lg font-black text-white"
                  style={{
                    background: `linear-gradient(145deg,${primary},#16365F)`,
                  }}
                >
                  {p.initials}
                </div>
                <div className="mt-4 text-sm font-black">{p.name}</div>
                <div className="mt-1 text-xs text-slate-400">{p.role}</div>
              </div>
            ))}
          </div>
        </section>
      ) : null}
      <section className="px-5 pb-20 sm:px-8">
        <div
          className="mx-auto max-w-5xl rounded-[30px] px-6 py-14 text-center text-white"
          style={{ background: `linear-gradient(135deg,${primary},#061326)` }}
        >
          <h2 className="text-3xl font-black tracking-[-.035em] sm:text-4xl">
            Ready to move forward?
          </h2>
          <p className="mx-auto mt-4 max-w-xl text-sm leading-7 text-white/55">
            Explore our solutions or speak directly with the team about your
            financial needs.
          </p>
          <div className="mt-7 flex flex-col justify-center gap-3 sm:flex-row">
            <Link
              href="/services"
              className="rounded-xl border border-white/15 px-5 py-3 text-sm font-bold"
            >
              Explore solutions
            </Link>
            <Link
              href="/apply"
              className="rounded-xl px-5 py-3 text-sm font-black text-[#111827]"
              style={{ backgroundColor: accent }}
            >
              Start an application
            </Link>
          </div>
        </div>
      </section>
    </main>
  );
}
