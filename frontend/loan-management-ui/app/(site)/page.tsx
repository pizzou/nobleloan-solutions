"use client";

import Link from "next/link";
import { useTenant } from "./layout";

const Arrow = () => (
  <svg
    viewBox="0 0 24 24"
    className="h-4 w-4"
    fill="none"
    stroke="currentColor"
    strokeWidth="2"
    strokeLinecap="round"
    strokeLinejoin="round"
  >
    <path d="M5 12h14" />
    <path d="m13 6 6 6-6 6" />
  </svg>
);
const Shield = () => (
  <svg
    viewBox="0 0 24 24"
    className="h-5 w-5"
    fill="none"
    stroke="currentColor"
    strokeWidth="1.8"
  >
    <path d="M12 3 20 6v5c0 5-3.4 8.3-8 10-4.6-1.7-8-5-8-10V6l8-3Z" />
    <path d="m8.5 12 2.2 2.2 4.8-5" />
  </svg>
);
const Clock = () => (
  <svg
    viewBox="0 0 24 24"
    className="h-5 w-5"
    fill="none"
    stroke="currentColor"
    strokeWidth="1.8"
  >
    <circle cx="12" cy="12" r="9" />
    <path d="M12 7v5l3 2" />
  </svg>
);
const User = () => (
  <svg
    viewBox="0 0 24 24"
    className="h-5 w-5"
    fill="none"
    stroke="currentColor"
    strokeWidth="1.8"
  >
    <circle cx="12" cy="8" r="3" />
    <path d="M5 20c.7-3.2 3.1-5 7-5s6.3 1.8 7 5" />
  </svg>
);
const Building = () => (
  <svg
    viewBox="0 0 24 24"
    className="h-5 w-5"
    fill="none"
    stroke="currentColor"
    strokeWidth="1.8"
  >
    <path d="M4 21V5l8-3 8 3v16" />
    <path d="M8 9h1M15 9h1M8 13h1M15 13h1M8 17h1M15 17h1" />
  </svg>
);
const Check = () => (
  <svg
    viewBox="0 0 24 24"
    className="h-4 w-4"
    fill="none"
    stroke="currentColor"
    strokeWidth="2.4"
  >
    <path d="m5 12 4 4L19 6" />
  </svg>
);

function SectionTitle({
  eyebrow,
  title,
  text,
  light = false,
}: {
  eyebrow: string;
  title: string;
  text?: string;
  light?: boolean;
}) {
  return (
    <div className="mx-auto max-w-3xl text-center">
      <div
        className="public-eyebrow"
        style={{ color: light ? "#D9B95B" : "#B08A27" }}
      >
        {eyebrow}
      </div>
      <h2 className={`public-title ${light ? "text-white" : "text-[#0B1F3A]"}`}>
        {title}
      </h2>
      {text && (
        <p
          className={`mt-4 text-base leading-7 ${light ? "text-white/65" : "text-slate-500"}`}
        >
          {text}
        </p>
      )}
    </div>
  );
}

export default function HomePage() {
  const tenant = useTenant();
  if (!tenant) return null;
  const primary = tenant.primaryColor || "#0D2C54";
  const gold = tenant.accentColor || "#D4AF37";
  const country =
    tenant.country === "RW" ? "Rwanda" : tenant.country || "Rwanda";

  const services = (tenant.services || []).slice(0, 6);
  const stats = tenant.stats || [];

  return (
    <div className="public-site">
      <section className="relative overflow-hidden bg-[#071B35] text-white">
        <div className="public-grid absolute inset-0 opacity-30" />
        <div className="absolute -right-32 -top-32 h-[520px] w-[520px] rounded-full border border-white/10 bg-[#D4AF37]/10 blur-2xl" />
        <div className="absolute -bottom-40 left-1/3 h-[420px] w-[420px] rounded-full bg-[#0D6B5B]/20 blur-3xl" />
        <div className="relative mx-auto grid max-w-7xl gap-14 px-5 pb-20 pt-16 lg:grid-cols-[1.08fr_.92fr] lg:items-center lg:pb-28 lg:pt-24">
          <div>
            <div className="public-kicker mb-6 border border-[#D4AF37]/35 bg-white/5 text-[#E7CC78]">
              Private-standard lending, built around you
            </div>
            <h1 className="max-w-4xl font-serif text-5xl font-semibold leading-[1.02] tracking-[-.035em] md:text-6xl lg:text-7xl">
              Financial confidence for the decisions that matter.
            </h1>
            <p className="mt-7 max-w-2xl text-lg leading-8 text-white/70 md:text-xl">
              {tenant.hero?.subtext ||
                `Thoughtful lending for individuals, professionals and growing businesses across ${country}. Clear terms, disciplined credit processes and a premium client experience.`}
            </p>
            <div className="mt-9 flex flex-wrap gap-3">
              <Link
                href="/apply"
                className="public-btn-primary"
                style={{ backgroundColor: gold, color: "#071B35" }}
              >
                Start an application <Arrow />
              </Link>
              <Link href="/services" className="public-btn-ghost">
                Explore lending solutions <Arrow />
              </Link>
            </div>
            <div className="mt-9 flex flex-wrap gap-x-7 gap-y-3 text-sm text-white/60">
              {[
                "Transparent lending terms",
                "Secure digital application",
                "Dedicated client support",
              ].map((x) => (
                <span key={x} className="flex items-center gap-2">
                  <span className="text-[#D4AF37]">
                    <Check />
                  </span>
                  {x}
                </span>
              ))}
            </div>
          </div>

          <div className="relative">
            <div className="public-hero-card">
              <div className="flex items-center justify-between border-b border-white/10 pb-5">
                <div>
                  <div className="text-[10px] font-bold uppercase tracking-[.22em] text-white/40">
                    Client experience
                  </div>
                  <div className="mt-1 font-serif text-2xl">
                    A better way to borrow
                  </div>
                </div>
                <div className="public-seal">
                  <Shield />
                </div>
              </div>
              <div className="grid grid-cols-2 gap-3 py-6">
                {[
                  ["01", "Assess", "Understand your needs and affordability."],
                  [
                    "02",
                    "Structure",
                    "Match you with an appropriate facility.",
                  ],
                  [
                    "03",
                    "Review",
                    "Credit decisions follow a disciplined process.",
                  ],
                  ["04", "Support", "Stay informed throughout the facility."],
                ].map(([n, t, d]) => (
                  <div
                    key={n}
                    className="rounded-2xl border border-white/10 bg-white/[.035] p-4"
                  >
                    <div className="text-xs font-bold text-[#D4AF37]">{n}</div>
                    <div className="mt-2 font-semibold">{t}</div>
                    <div className="mt-1 text-xs leading-5 text-white/45">
                      {d}
                    </div>
                  </div>
                ))}
              </div>
              <div className="rounded-2xl bg-white p-5 text-[#0B1F3A] shadow-2xl">
                <div className="flex items-center justify-between">
                  <div>
                    <div className="text-[10px] font-bold uppercase tracking-[.18em] text-slate-400">
                      Application journey
                    </div>
                    <div className="mt-1 text-sm font-bold">
                      Simple. Secure. Structured.
                    </div>
                  </div>
                  <div className="rounded-full bg-[#0D6B5B]/10 px-3 py-1 text-xs font-bold text-[#0D6B5B]">
                    Online
                  </div>
                </div>
                <div className="mt-5 h-1.5 rounded-full bg-slate-100">
                  <div
                    className="h-full w-2/3 rounded-full"
                    style={{ backgroundColor: gold }}
                  />
                </div>
                <div className="mt-2 flex justify-between text-[10px] font-semibold text-slate-400">
                  <span>Application</span>
                  <span>Review</span>
                  <span>Decision</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section className="border-b border-slate-200 bg-white">
        <div className="mx-auto grid max-w-7xl grid-cols-2 divide-x divide-slate-200 px-5 py-7 md:grid-cols-4">
          {(stats.length
            ? stats
            : [
                { value: "Secure", label: "Digital applications" },
                { value: "Clear", label: "Transparent terms" },
                { value: "Human", label: "Dedicated support" },
                { value: "Focused", label: "Responsible lending" },
              ]
          )
            .slice(0, 4)
            .map((s: any, i: number) => (
              <div
                key={s.label}
                className="px-5 text-center first:pl-0 last:pr-0"
              >
                <div className="font-serif text-2xl font-semibold text-[#0B1F3A] md:text-3xl">
                  {s.value}
                </div>
                <div className="mt-1 text-[11px] font-bold uppercase tracking-[.15em] text-slate-400">
                  {s.label}
                </div>
              </div>
            ))}
        </div>
      </section>

      <section className="public-section bg-[#F7F8FA]">
        <SectionTitle
          eyebrow="Lending solutions"
          title="Purpose-built facilities for real financial needs"
          text="Choose a lending solution designed around the way you earn, operate, invest or grow."
        />
        <div className="mx-auto mt-14 grid max-w-7xl gap-5 px-5 md:grid-cols-2 lg:grid-cols-3">
          {services.map((s: any, i: number) => (
            <div key={s.title} className="public-product-card group">
              <div className="flex items-start justify-between">
                <div
                  className="public-icon-box"
                  style={{ backgroundColor: primary + "12", color: primary }}
                >
                  {s.icon || ["◈", "◌", "◇"][i % 3]}
                </div>
                <span className="text-[10px] font-bold uppercase tracking-[.18em] text-slate-400">
                  Facility {String(i + 1).padStart(2, "0")}
                </span>
              </div>
              <h3 className="mt-6 font-serif text-2xl font-semibold text-[#0B1F3A]">
                {s.title}
              </h3>
              <p className="mt-3 min-h-[72px] text-sm leading-6 text-slate-500">
                {s.description}
              </p>
              <div className="mt-6 grid grid-cols-2 gap-2 border-y border-slate-100 py-4">
                <div>
                  <div className="text-[9px] font-bold uppercase tracking-wider text-slate-400">
                    Rate
                  </div>
                  <div className="mt-1 text-sm font-bold text-[#0B1F3A]">
                    {s.rate}
                    {s.rateType === "MONTHLY" ? " / month" : ""}
                  </div>
                </div>
                <div>
                  <div className="text-[9px] font-bold uppercase tracking-wider text-slate-400">
                    Term
                  </div>
                  <div className="mt-1 text-sm font-bold text-[#0B1F3A]">
                    {s.term}
                  </div>
                </div>
              </div>
              <Link
                href="/apply"
                className="mt-5 inline-flex items-center gap-2 text-sm font-bold text-[#0D6B5B]"
              >
                Explore this facility <Arrow />
              </Link>
            </div>
          ))}
          {!services.length && (
            <div className="public-empty col-span-full">
              Our lending solutions are being prepared. Please contact our team
              for current facilities.
            </div>
          )}
        </div>
      </section>

      <section className="public-section bg-white">
        <div className="mx-auto grid max-w-7xl gap-12 px-5 lg:grid-cols-[.8fr_1.2fr] lg:items-center">
          <div>
            <div className="public-eyebrow text-[#B08A27]">
              Institutional standard
            </div>
            <h2 className="public-title text-[#0B1F3A]">
              A lending relationship should feel considered, not transactional.
            </h2>
            <p className="mt-5 text-base leading-7 text-slate-500">
              We combine digital convenience with the discipline clients expect
              from a serious financial institution: clear documentation,
              responsible assessment, secure information handling and human
              support.
            </p>
            <Link
              href="/about"
              className="mt-7 inline-flex items-center gap-2 text-sm font-bold text-[#0D6B5B]"
            >
              Meet the institution <Arrow />
            </Link>
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            {[
              [
                "01",
                Shield,
                "Security by design",
                "Client information and application activity are handled through a secure digital workflow.",
              ],
              [
                "02",
                Clock,
                "Clear process",
                "Know what happens next from application through review and servicing.",
              ],
              [
                "03",
                User,
                "Human guidance",
                "A premium experience still has people behind it when you need help.",
              ],
              [
                "04",
                Building,
                "Built for growth",
                "Solutions can support personal priorities, professional needs and business expansion.",
              ],
            ].map(([n, Icon, title, desc]: any) => (
              <div key={title} className="public-standard-card">
                <div className="flex items-center gap-3">
                  <span className="text-xs font-bold text-[#B08A27]">{n}</span>
                  <span className="public-icon-box small">
                    <Icon />
                  </span>
                </div>
                <h3 className="mt-5 font-semibold text-[#0B1F3A]">{title}</h3>
                <p className="mt-2 text-sm leading-6 text-slate-500">{desc}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="public-section bg-[#071B35] text-white">
        <SectionTitle
          light
          eyebrow="How it works"
          title="A calm, clear path from need to facility"
          text="No unnecessary complexity. We keep the journey structured so you can focus on the decision itself."
        />
        <div className="mx-auto mt-14 grid max-w-7xl gap-4 px-5 md:grid-cols-4">
          {[
            [
              "01",
              "Tell us what you need",
              "Submit your application and the essential information.",
            ],
            [
              "02",
              "We review responsibly",
              "Our process considers the information required for an informed credit decision.",
            ],
            [
              "03",
              "Understand the offer",
              "Review the facility structure, pricing and obligations before proceeding.",
            ],
            [
              "04",
              "Stay supported",
              "Once active, your account remains connected to our servicing and support team.",
            ],
          ].map(([n, t, d]) => (
            <div
              key={n}
              className="relative rounded-3xl border border-white/10 bg-white/[.035] p-6"
            >
              <div className="text-4xl font-serif text-[#D4AF37]/50">{n}</div>
              <h3 className="mt-7 font-semibold">{t}</h3>
              <p className="mt-2 text-sm leading-6 text-white/50">{d}</p>
            </div>
          ))}
        </div>
      </section>

      {tenant.testimonials?.length ? (
        <section className="public-section bg-white">
          <SectionTitle
            eyebrow="Client perspective"
            title="Trust is built through the experience"
          />
          <div className="mx-auto mt-12 grid max-w-7xl gap-5 px-5 md:grid-cols-3">
            {tenant.testimonials.slice(0, 3).map((t: any) => (
              <div key={t.name} className="public-quote">
                <div className="text-[#D4AF37]">
                  {"★".repeat(Math.min(5, t.rating || 5))}
                </div>
                <p className="mt-5 text-base leading-7 text-slate-600">
                  “{t.text}”
                </p>
                <div className="mt-7 border-t border-slate-100 pt-4">
                  <div className="font-semibold text-[#0B1F3A]">{t.name}</div>
                  <div className="text-xs text-slate-400">{t.role}</div>
                </div>
              </div>
            ))}
          </div>
        </section>
      ) : null}

      <section
        className="public-cta mx-5 mb-20 overflow-hidden rounded-[2rem]"
        style={{ backgroundColor: primary }}
      >
        <div className="public-grid absolute inset-0 opacity-10" />
        <div className="relative mx-auto max-w-4xl px-6 py-16 text-center text-white md:py-20">
          <div className="public-eyebrow text-[#D9B95B]">
            Your next financial move
          </div>
          <h2 className="mt-3 font-serif text-4xl font-semibold md:text-5xl">
            Let’s structure the right solution for you.
          </h2>
          <p className="mx-auto mt-5 max-w-2xl text-white/65">
            Start online, review our solutions or speak with our team before you
            apply.
          </p>
          <div className="mt-8 flex flex-wrap justify-center gap-3">
            <Link
              href="/apply"
              className="public-btn-primary"
              style={{ backgroundColor: gold, color: "#071B35" }}
            >
              Apply securely <Arrow />
            </Link>
            <Link href="/contact" className="public-btn-ghost">
              Speak to our team <Arrow />
            </Link>
          </div>
        </div>
      </section>
    </div>
  );
}
