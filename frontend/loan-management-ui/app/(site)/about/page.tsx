"use client";

import Link from "next/link";
import { useTenant } from "../layout";

export default function AboutPage() {
  const tenant = useTenant();
  if (!tenant) return null;
  const primary = tenant.primaryColor;
  const accent = tenant.accentColor;
  const team = tenant.team ?? [];

  return (
    <div>
      <section className="bg-slate-50">
        <div className="mx-auto grid max-w-7xl gap-12 px-4 py-20 sm:py-24 lg:grid-cols-[0.9fr_1.1fr] lg:items-center">
          <div>
            <div
              className="text-[11px] font-black uppercase tracking-[0.22em]"
              style={{ color: accent }}
            >
              About {tenant.name}
            </div>
            <h1 className="mt-4 text-4xl font-black tracking-tight text-slate-950 sm:text-6xl">
              A lender built around responsible, practical finance.
            </h1>
            <p className="mt-6 max-w-2xl text-base leading-8 text-slate-600">
              {tenant.mission ||
                `Learn more about ${tenant.name}, the products we publish, and the standards we aim to bring to every borrower interaction.`}
            </p>
            <div className="mt-8 flex flex-wrap gap-3">
              <Link
                href="/services"
                className="rounded-2xl px-6 py-3.5 text-sm font-black text-white"
                style={{ backgroundColor: primary }}
              >
                Explore our products
              </Link>
              <Link
                href="/contact"
                className="rounded-2xl border border-slate-200 bg-white px-6 py-3.5 text-sm font-bold text-slate-700"
              >
                Contact us
              </Link>
            </div>
          </div>

          <div className="rounded-[2rem] bg-slate-950 p-8 text-white shadow-2xl sm:p-10">
            <div className="text-[10px] font-black uppercase tracking-[0.2em] text-white/45">
              Our vision
            </div>
            <div className="mt-4 text-2xl font-black leading-tight">
              {tenant.vision ||
                `A trusted financial partner for the communities and businesses we serve.`}
            </div>
            <div className="mt-8 grid grid-cols-2 gap-3">
              {[
                ["Country", tenant.country || "—"],
                ["Currency", tenant.currency || "—"],
                ["Founded", tenant.founded || "—"],
                ["Registration", tenant.registrationNumber || "—"],
              ].map(([label, value]) => (
                <div
                  key={label}
                  className="rounded-2xl border border-white/10 bg-white/5 p-4"
                >
                  <div className="text-[10px] font-black uppercase tracking-wider text-white/40">
                    {label}
                  </div>
                  <div className="mt-1 break-words text-sm font-black text-white">
                    {value}
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-4 py-20">
        <div className="grid gap-5 md:grid-cols-2">
          <div className="rounded-[2rem] border border-slate-200 bg-white p-8 shadow-sm">
            <div
              className="text-[11px] font-black uppercase tracking-[0.18em]"
              style={{ color: accent }}
            >
              Mission
            </div>
            <h2 className="mt-3 text-2xl font-black text-slate-950">
              What we are here to do
            </h2>
            <p className="mt-4 text-sm leading-7 text-slate-600">
              {tenant.mission ||
                "Provide clear, practical financing and a professional borrower experience."}
            </p>
          </div>
          <div className="rounded-[2rem] border border-slate-200 bg-white p-8 shadow-sm">
            <div
              className="text-[11px] font-black uppercase tracking-[0.18em]"
              style={{ color: accent }}
            >
              Responsible lending
            </div>
            <h2 className="mt-3 text-2xl font-black text-slate-950">
              Clear terms. Clear process.
            </h2>
            <p className="mt-4 text-sm leading-7 text-slate-600">
              Our public website is designed to make product information,
              application steps and lender contact information easy to
              understand before you commit.
            </p>
          </div>
        </div>
      </section>

      {team.length > 0 && (
        <section className="bg-slate-50">
          <div className="mx-auto max-w-7xl px-4 py-20">
            <div className="text-center">
              <div
                className="text-[11px] font-black uppercase tracking-[0.18em]"
                style={{ color: accent }}
              >
                Leadership & team
              </div>
              <h2 className="mt-3 text-3xl font-black text-slate-950">
                People behind the institution
              </h2>
            </div>
            <div className="mx-auto mt-10 grid max-w-5xl gap-5 md:grid-cols-3">
              {team.map((member) => (
                <div
                  key={`${member.name}-${member.role}`}
                  className="rounded-[1.5rem] border border-slate-200 bg-white p-6 text-center shadow-sm"
                >
                  <div
                    className="mx-auto flex h-16 w-16 items-center justify-center rounded-full text-lg font-black text-white"
                    style={{ backgroundColor: primary }}
                  >
                    {member.initials || member.name.slice(0, 2).toUpperCase()}
                  </div>
                  <div className="mt-4 font-black text-slate-950">
                    {member.name}
                  </div>
                  <div className="mt-1 text-xs font-semibold uppercase tracking-wider text-slate-400">
                    {member.role || "Team"}
                  </div>
                </div>
              ))}
            </div>
          </div>
        </section>
      )}
    </div>
  );
}
