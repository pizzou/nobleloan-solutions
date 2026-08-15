"use client";

import Link from "next/link";
import { useTenant } from "../layout";

export default function AboutPage() {
  const tenant = useTenant();
  if (!tenant) return null;
  const primary = tenant.primaryColor;
  const accent = tenant.accentColor;

  return (
    <div>
      <section className="bg-[#06172D] text-white">
        <div className="mx-auto grid max-w-7xl items-center gap-12 px-4 py-16 sm:py-20 lg:grid-cols-[1.15fr_.85fr]">
          <div>
            <div
              className="text-[11px] font-bold uppercase tracking-[0.2em]"
              style={{ color: accent }}
            >
              About {tenant.name}
            </div>
            <h1 className="mt-3 text-4xl font-black tracking-tight sm:text-5xl">
              A lending partner built for clarity, access and confidence.
            </h1>
            <p className="mt-6 max-w-2xl text-base leading-8 text-white/65">
              {tenant.mission ||
                "We provide structured financial solutions through a secure digital experience designed around responsible lending and strong customer relationships."}
            </p>
          </div>
          <div className="rounded-[30px] border border-white/10 bg-white/5 p-6 backdrop-blur">
            <div className="text-[11px] font-bold uppercase tracking-[0.18em] text-white/45">
              Institutional profile
            </div>
            <div className="mt-5 grid grid-cols-2 gap-3">
              {[
                ["Founded", tenant.founded || "—"],
                ["Country", tenant.country || "—"],
                ["Registration", tenant.registrationNumber || "—"],
                ["Currency", tenant.currency || "—"],
              ].map(([label, value]) => (
                <div key={label} className="rounded-2xl bg-white/5 p-4">
                  <div className="text-[10px] font-bold uppercase tracking-wider text-white/40">
                    {label}
                  </div>
                  <div className="mt-1 text-sm font-black text-white">
                    {value}
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-4 py-16 sm:py-20">
        <div className="grid gap-5 md:grid-cols-3">
          {[
            [
              "Mission",
              tenant.mission ||
                "Deliver transparent, responsible financial services that help customers move toward meaningful goals.",
            ],
            [
              "Vision",
              tenant.vision ||
                "Build long-term customer relationships through trusted, accessible and modern financial services.",
            ],
            [
              "Principles",
              "Integrity, transparency, customer protection, responsible lending and operational excellence.",
            ],
          ].map(([title, text]) => (
            <div
              key={title}
              className="rounded-3xl border border-slate-200 bg-white p-7 shadow-sm"
            >
              <div
                className="text-[11px] font-bold uppercase tracking-[0.18em]"
                style={{ color: accent }}
              >
                {title}
              </div>
              <p className="mt-3 text-sm leading-7 text-slate-600">{text}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="bg-white py-16 sm:py-20">
        <div className="mx-auto max-w-7xl px-4">
          <div className="max-w-2xl">
            <div
              className="text-[11px] font-bold uppercase tracking-[0.18em]"
              style={{ color: accent }}
            >
              Why customers choose us
            </div>
            <h2 className="mt-2 text-3xl font-black tracking-tight text-slate-950">
              A professional experience at every step
            </h2>
          </div>
          <div className="mt-10 grid gap-5 md:grid-cols-2 lg:grid-cols-3">
            {[
              [
                "Clear terms",
                "Understand the commercial terms before submitting a commitment.",
              ],
              [
                "Digital access",
                "Start and monitor your financing journey without unnecessary branch visits.",
              ],
              [
                "Structured credit",
                "Decisions are supported by documented risk and affordability processes.",
              ],
              [
                "Customer support",
                "Reach a real team for questions, guidance and application support.",
              ],
              [
                "Secure operations",
                "Customer data and financial actions move through controlled workflows.",
              ],
              [
                "Long-term view",
                "We aim to build sustainable customer relationships rather than one-off transactions.",
              ],
            ].map(([title, text]) => (
              <div
                key={title}
                className="rounded-3xl border border-slate-200 p-6"
              >
                <div
                  className="h-2 w-10 rounded-full"
                  style={{ backgroundColor: primary }}
                />
                <div className="mt-5 text-sm font-black text-slate-950">
                  {title}
                </div>
                <div className="mt-2 text-sm leading-6 text-slate-500">
                  {text}
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {tenant.team && tenant.team.length > 0 && (
        <section className="mx-auto max-w-7xl px-4 py-16">
          <div className="mb-9">
            <div
              className="text-[11px] font-bold uppercase tracking-[0.18em]"
              style={{ color: accent }}
            >
              Leadership
            </div>
            <h2 className="mt-2 text-3xl font-black text-slate-950">
              The people behind the platform
            </h2>
          </div>
          <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
            {tenant.team.map((person) => (
              <div
                key={person.name}
                className="rounded-3xl border border-slate-200 bg-white p-6 text-center"
              >
                <div
                  className="mx-auto flex h-16 w-16 items-center justify-center rounded-full text-lg font-black text-white"
                  style={{ backgroundColor: primary }}
                >
                  {person.initials}
                </div>
                <div className="mt-4 text-sm font-black text-slate-950">
                  {person.name}
                </div>
                <div className="mt-1 text-xs text-slate-500">{person.role}</div>
              </div>
            ))}
          </div>
        </section>
      )}

      <section className="mx-auto max-w-7xl px-4 pb-16">
        <div
          className="rounded-[30px] px-7 py-12 text-center"
          style={{ background: `linear-gradient(135deg, ${primary}, #092844)` }}
        >
          <h2 className="text-3xl font-black text-white">
            Ready to move forward?
          </h2>
          <p className="mx-auto mt-3 max-w-xl text-sm leading-6 text-white/60">
            Explore the available financial solutions or speak with our team
            before starting your application.
          </p>
          <div className="mt-6 flex flex-wrap justify-center gap-3">
            <Link
              href="/services"
              className="rounded-xl bg-white px-5 py-3 text-sm font-black"
              style={{ color: primary }}
            >
              Explore solutions
            </Link>
            <Link
              href="/contact"
              className="rounded-xl border border-white/20 px-5 py-3 text-sm font-bold text-white"
            >
              Contact us
            </Link>
          </div>
        </div>
      </section>
    </div>
  );
}
