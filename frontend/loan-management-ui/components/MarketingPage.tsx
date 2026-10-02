"use client";

import Image from "next/image";
import Link from "next/link";
import { SITE_CONTENT } from "../lib/siteContent";
import { useTenant } from "../app/(site)/layout";

export type MarketingSection = {
  title: string;
  body: string;
  bullets?: string[];
};

function Arrow() {
  return (
    <svg
      viewBox="0 0 20 20"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      className="h-4 w-4"
      aria-hidden="true"
    >
      <path d="M4 10h11" />
      <path d="m10 5 5 5-5 5" />
    </svg>
  );
}

function Check() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      className="h-4 w-4"
      aria-hidden="true"
    >
      <path d="m5 12 4 4L19 6" />
    </svg>
  );
}

export function MarketingPage({
  eyebrow,
  title,
  description,
  primaryHref = "/apply",
  primaryLabel = "Check your options",
  secondaryHref = "/loan-calculator",
  secondaryLabel = "Use a calculator",
  sections,
}: {
  eyebrow: string;
  title: string;
  description: string;
  primaryHref?: string;
  primaryLabel?: string;
  secondaryHref?: string;
  secondaryLabel?: string;
  sections: MarketingSection[];
}) {
  const tenant = useTenant() || SITE_CONTENT;
  const primary = tenant.primaryColor || "#0F1B3D";
  const accent = tenant.accentColor || "#C9A227";
  const products = tenant.services || SITE_CONTENT.services;

  return (
    <main className="overflow-hidden bg-[#f5f7fa] text-slate-950">
      <section
        className="relative overflow-hidden text-white"
        style={{
          background: `linear-gradient(120deg,#061326 0%,${primary} 62%,#1a416b 100%)`,
        }}
      >
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_82%_16%,rgba(201,162,39,.24),transparent_24%),radial-gradient(circle_at_14%_86%,rgba(255,255,255,.10),transparent_26%)]" />
        <Image
          src="/hero-borrower.jpg"
          alt=""
          fill
          sizes="100vw"
          className="object-cover object-[66%_center] opacity-[.15] mix-blend-screen"
          priority
        />
        <div className="relative mx-auto grid max-w-7xl gap-10 px-5 py-16 sm:px-8 sm:py-24 lg:grid-cols-[minmax(0,1fr)_380px] lg:items-center lg:py-28">
          <div>
            <p
              className="text-[10px] font-black uppercase tracking-[.24em]"
              style={{ color: accent }}
            >
              {eyebrow}
            </p>
            <h1 className="mt-5 max-w-4xl text-4xl font-black leading-[.98] tracking-[-.06em] sm:text-6xl lg:text-[4.35rem]">
              {title}
            </h1>
            <p className="mt-6 max-w-2xl text-base leading-8 text-white/70 sm:text-lg">
              {description}
            </p>
            <div className="mt-8 flex flex-col gap-3 sm:flex-row">
              <Link
                href={primaryHref}
                className="inline-flex items-center justify-center gap-2 rounded-2xl px-6 py-4 text-sm font-black text-slate-950 shadow-xl transition hover:-translate-y-0.5"
                style={{ backgroundColor: accent }}
              >
                {primaryLabel}
                <Arrow />
              </Link>
              <Link
                href={secondaryHref}
                className="inline-flex items-center justify-center gap-2 rounded-2xl border border-white/18 bg-white/[.06] px-6 py-4 text-sm font-bold text-white backdrop-blur-sm transition hover:bg-white/[.11]"
              >
                {secondaryLabel}
              </Link>
            </div>
          </div>

          <aside className="rounded-[28px] border border-white/12 bg-white/[.07] p-6 shadow-[0_30px_90px_rgba(0,0,0,.20)] backdrop-blur-md">
            <p
              className="text-[9px] font-black uppercase tracking-[.2em]"
              style={{ color: accent }}
            >
              Noble at a glance
            </p>
            <div className="mt-5 divide-y divide-white/10">
              <div className="flex items-center justify-between gap-4 py-4 first:pt-0">
                <span className="text-xs text-white/50">Market</span>
                <span className="text-sm font-black text-white">
                  {tenant.country}
                </span>
              </div>
              <div className="flex items-center justify-between gap-4 py-4">
                <span className="text-xs text-white/50">Currency</span>
                <span className="text-sm font-black text-white">
                  {tenant.currency}
                </span>
              </div>
              <div className="flex items-center justify-between gap-4 py-4">
                <span className="text-xs text-white/50">Public solutions</span>
                <span className="text-sm font-black text-white">
                  {products.length}
                </span>
              </div>
              <div className="py-4 last:pb-0">
                <span className="text-xs text-white/50">Customer journey</span>
                <span className="mt-1 block text-sm font-black text-white">
                  Explore → apply → track
                </span>
              </div>
            </div>
          </aside>
        </div>
      </section>

      <section className="border-b border-slate-200 bg-white">
        <div className="mx-auto grid max-w-7xl gap-px sm:grid-cols-3">
          {[
            [
              "01",
              "Clear information",
              "Understand the product, fees and repayment plan before committing.",
            ],
            [
              "02",
              "Secure application",
              "Complete the digital journey with the information and documents requested.",
            ],
            [
              "03",
              "Ongoing support",
              "Keep your reference and use the right support channel when you need help.",
            ],
          ].map(([n, title, body]) => (
            <div
              key={n}
              className="border-b border-slate-100 p-6 last:border-b-0 sm:border-b-0 sm:border-r sm:last:border-r-0 sm:p-7"
            >
              <span
                className="text-[10px] font-black tracking-[.16em]"
                style={{ color: accent }}
              >
                {n}
              </span>
              <h2 className="mt-2 text-base font-black">{title}</h2>
              <p className="mt-2 text-xs leading-6 text-slate-500">{body}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-5 py-14 sm:px-8 sm:py-20">
        <div className="max-w-2xl">
          <p
            className="text-[10px] font-black uppercase tracking-[.22em]"
            style={{ color: accent }}
          >
            What you should know
          </p>
          <h2 className="mt-3 text-3xl font-black tracking-[-.045em] sm:text-4xl">
            Useful detail, not just marketing copy.
          </h2>
          <p className="mt-4 text-sm leading-7 text-slate-500">
            The information below is designed to answer the practical questions
            that matter before you borrow. The applicable product terms and your
            signed agreement remain the source of truth for any specific loan.
          </p>
        </div>

        <div className="mt-10 grid gap-5 lg:grid-cols-2">
          {sections.map((section, index) => (
            <article
              key={section.title}
              className="group relative overflow-hidden rounded-[28px] border border-slate-200 bg-white p-7 shadow-[0_16px_55px_rgba(15,23,42,.055)] transition duration-300 hover:-translate-y-1 hover:shadow-[0_28px_70px_rgba(15,23,42,.10)] sm:p-8"
            >
              <div
                className="absolute right-0 top-0 h-32 w-32 rounded-full blur-3xl opacity-50"
                style={{ backgroundColor: `${accent}18` }}
              />
              <div className="relative flex items-center justify-between gap-4">
                <span
                  className="flex h-10 w-10 items-center justify-center rounded-xl text-xs font-black text-white"
                  style={{
                    background: `linear-gradient(145deg,${primary},#173b64)`,
                  }}
                >
                  {String(index + 1).padStart(2, "0")}
                </span>
                <span className="text-[9px] font-black uppercase tracking-[.16em] text-slate-400">
                  Noble guidance
                </span>
              </div>
              <h3 className="relative mt-6 text-xl font-black tracking-tight">
                {section.title}
              </h3>
              <p className="relative mt-3 text-sm leading-7 text-slate-600">
                {section.body}
              </p>
              {section.bullets?.length ? (
                <ul className="relative mt-6 space-y-3 border-t border-slate-100 pt-5">
                  {section.bullets.map((bullet) => (
                    <li
                      key={bullet}
                      className="flex gap-3 text-sm leading-6 text-slate-600"
                    >
                      <span
                        className="mt-1 flex h-5 w-5 shrink-0 items-center justify-center rounded-full"
                        style={{
                          backgroundColor: `${accent}20`,
                          color: primary,
                        }}
                      >
                        <Check />
                      </span>
                      <span>{bullet}</span>
                    </li>
                  ))}
                </ul>
              ) : null}
            </article>
          ))}
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-5 pb-20 sm:px-8">
        <div
          className="relative overflow-hidden rounded-[34px] px-7 py-10 text-white sm:px-10 sm:py-12"
          style={{ background: `linear-gradient(125deg,${primary},#061326)` }}
        >
          <div className="absolute -right-20 -top-20 h-64 w-64 rounded-full border border-white/10" />
          <div className="relative grid gap-8 lg:grid-cols-[1fr_auto] lg:items-center">
            <div>
              <p
                className="text-[10px] font-black uppercase tracking-[.22em]"
                style={{ color: accent }}
              >
                Ready for the next step?
              </p>
              <h2 className="mt-3 max-w-2xl text-3xl font-black tracking-[-.04em] sm:text-4xl">
                Choose a loan, understand the numbers and apply when you are
                ready.
              </h2>
              <p className="mt-4 max-w-2xl text-sm leading-7 text-white/60">
                Noble's public journey is designed to keep the important
                information visible before you submit an application.
              </p>
            </div>
            <div className="flex flex-col gap-3 sm:flex-row lg:flex-col">
              <Link
                href="/services"
                className="rounded-2xl border border-white/15 px-6 py-3.5 text-center text-sm font-bold text-white transition hover:bg-white/10"
              >
                Explore loan solutions
              </Link>
              <Link
                href="/contact"
                className="rounded-2xl px-6 py-3.5 text-center text-sm font-black text-slate-950 transition hover:-translate-y-0.5"
                style={{ backgroundColor: accent }}
              >
                Talk to Noble
              </Link>
            </div>
          </div>
        </div>
      </section>
    </main>
  );
}
