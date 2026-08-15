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

  return (
    <div>
      <section
        className="relative overflow-hidden text-white"
        style={{
          background: `linear-gradient(135deg, ${primary}, ${primary}D9)`,
        }}
      >
        <div className="mx-auto grid max-w-7xl gap-12 px-4 py-20 md:grid-cols-[1.2fr_0.8fr] md:py-24">
          <div>
            <div className="text-xs font-black uppercase tracking-[0.2em] text-white/60">
              About {tenant.name}
            </div>
            <h1 className="mt-4 text-4xl font-black leading-tight md:text-6xl">
              {tenant.tagline || "A financial partner built around your needs"}
            </h1>
            <p className="mt-6 max-w-2xl text-lg leading-8 text-white/75">
              {tenant.mission ||
                `Learn more about ${tenant.name}, its products, and the financial services made available through this website.`}
            </p>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="rounded-2xl border border-white/10 bg-white/10 p-5">
              <div className="text-xs uppercase tracking-wider text-white/50">
                Founded
              </div>
              <div className="mt-2 text-xl font-black">
                {tenant.founded || "—"}
              </div>
            </div>
            <div className="rounded-2xl border border-white/10 bg-white/10 p-5">
              <div className="text-xs uppercase tracking-wider text-white/50">
                Products
              </div>
              <div className="mt-2 text-xl font-black">{services.length}</div>
            </div>
            <div className="rounded-2xl border border-white/10 bg-white/10 p-5">
              <div className="text-xs uppercase tracking-wider text-white/50">
                Country
              </div>
              <div className="mt-2 text-xl font-black">
                {tenant.country || "—"}
              </div>
            </div>
            <div className="rounded-2xl border border-white/10 bg-white/10 p-5">
              <div className="text-xs uppercase tracking-wider text-white/50">
                Registration
              </div>
              <div className="mt-2 break-words text-xl font-black">
                {tenant.registrationNumber || "—"}
              </div>
            </div>
          </div>
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-4 py-20">
        <div className="grid gap-6 md:grid-cols-2">
          <div className="rounded-3xl border border-slate-200 bg-white p-8 shadow-sm">
            <div
              className="text-xs font-black uppercase tracking-[0.18em]"
              style={{ color: accent }}
            >
              Mission
            </div>
            <h2 className="mt-2 text-2xl font-black">What we aim to do</h2>
            <p className="mt-4 leading-8 text-slate-600">
              {tenant.mission ||
                "Our mission is published by the lender and is shown here so applicants can understand the organisation they are dealing with."}
            </p>
          </div>
          <div className="rounded-3xl border border-slate-200 bg-white p-8 shadow-sm">
            <div
              className="text-xs font-black uppercase tracking-[0.18em]"
              style={{ color: accent }}
            >
              Vision
            </div>
            <h2 className="mt-2 text-2xl font-black">Where we are going</h2>
            <p className="mt-4 leading-8 text-slate-600">
              {tenant.vision ||
                "Our vision is published by the lender and is shown here so applicants can understand its long-term direction."}
            </p>
          </div>
        </div>
      </section>

      <section className="bg-slate-50">
        <div className="mx-auto max-w-7xl px-4 py-20">
          <div className="max-w-2xl">
            <div
              className="text-xs font-black uppercase tracking-[0.18em]"
              style={{ color: accent }}
            >
              Our platform
            </div>
            <h2 className="mt-2 text-3xl font-black text-slate-950">
              A public website connected to the lender's actual loan platform
            </h2>
            <p className="mt-4 leading-7 text-slate-600">
              The information shown here is loaded from the organisation
              configuration and active loan products. This helps keep public
              rates, contact details, branding, and product availability aligned
              with the lender's system.
            </p>
          </div>
          <div className="mt-10 grid gap-5 md:grid-cols-3">
            <div className="rounded-2xl bg-white p-6 shadow-sm">
              <div className="text-2xl">📋</div>
              <h3 className="mt-4 font-black">Clear products</h3>
              <p className="mt-2 text-sm leading-6 text-slate-600">
                Visitors can review active loan products before starting an
                application.
              </p>
            </div>
            <div className="rounded-2xl bg-white p-6 shadow-sm">
              <div className="text-2xl">🧾</div>
              <h3 className="mt-4 font-black">Transparent charges</h3>
              <p className="mt-2 text-sm leading-6 text-slate-600">
                Published platform rates and fees are displayed instead of
                invented website-only numbers.
              </p>
            </div>
            <div className="rounded-2xl bg-white p-6 shadow-sm">
              <div className="text-2xl">🔐</div>
              <h3 className="mt-4 font-black">Connected workflows</h3>
              <p className="mt-2 text-sm leading-6 text-slate-600">
                Applications, tracking, payments, and documents connect to the
                same loan platform.
              </p>
            </div>
          </div>
        </div>
      </section>

      {team.length > 0 && (
        <section className="mx-auto max-w-7xl px-4 py-20">
          <div className="text-center">
            <div
              className="text-xs font-black uppercase tracking-[0.18em]"
              style={{ color: accent }}
            >
              Leadership
            </div>
            <h2 className="mt-2 text-3xl font-black text-slate-950">
              Our team
            </h2>
          </div>
          <div className="mx-auto mt-10 grid max-w-5xl gap-6 sm:grid-cols-2 md:grid-cols-4">
            {team.slice(0, 8).map((member) => {
              const initials =
                member.initials ||
                member.name
                  .split(/\s+/)
                  .map((p) => p[0])
                  .join("")
                  .slice(0, 2)
                  .toUpperCase();
              return (
                <div key={member.name} className="text-center">
                  <div
                    className="mx-auto flex h-20 w-20 items-center justify-center rounded-full text-lg font-black text-white"
                    style={{ backgroundColor: primary }}
                  >
                    {initials}
                  </div>
                  <div className="mt-4 font-black text-slate-900">
                    {member.name}
                  </div>
                  {member.role && (
                    <div className="mt-1 text-sm text-slate-500">
                      {member.role}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </section>
      )}

      <section className="px-4 pb-20 text-center">
        <h2 className="text-3xl font-black text-slate-950">
          Explore the services available from {tenant.name}
        </h2>
        <div className="mt-7 flex justify-center gap-3">
          <Link
            href="/services"
            className="rounded-full px-7 py-3.5 text-sm font-black text-white"
            style={{ backgroundColor: primary }}
          >
            View Services
          </Link>
          <Link
            href="/contact"
            className="rounded-full border px-7 py-3.5 text-sm font-bold"
            style={{ borderColor: primary, color: primary }}
          >
            Contact Us
          </Link>
        </div>
      </section>
    </div>
  );
}
