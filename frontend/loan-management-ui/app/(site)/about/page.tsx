"use client";
import Link from "next/link";
import { useTenant } from "../layout";
const Arrow = () => (
  <svg
    viewBox="0 0 24 24"
    className="h-4 w-4"
    fill="none"
    stroke="currentColor"
    strokeWidth="2"
  >
    <path d="M5 12h14" />
    <path d="m13 6 6 6-6 6" />
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
export default function AboutPage() {
  const tenant = useTenant();
  if (!tenant) return null;
  const primary = tenant.primaryColor || "#0D2C54";
  const gold = tenant.accentColor || "#D4AF37";
  return (
    <div className="public-site">
      <section className="relative overflow-hidden bg-[#071B35] py-20 text-white md:py-28">
        <div className="public-grid absolute inset-0 opacity-25" />
        <div className="relative mx-auto grid max-w-7xl gap-12 px-5 lg:grid-cols-[1.15fr_.85fr] lg:items-end">
          <div>
            <div className="public-kicker border border-[#D4AF37]/35 bg-white/5 text-[#E7CC78]">
              About {tenant.name}
            </div>
            <h1 className="mt-6 max-w-4xl font-serif text-5xl font-semibold tracking-[-.035em] md:text-6xl">
              A financial partner built on clarity, discipline and trust.
            </h1>
            <p className="mt-6 max-w-2xl text-lg leading-8 text-white/65">
              {tenant.mission ||
                "We provide structured lending support with a focus on responsible decisions, transparent communication and a premium client experience."}
            </p>
          </div>
          <div className="rounded-[2rem] border border-white/10 bg-white/[.045] p-7">
            <div className="text-[10px] font-bold uppercase tracking-[.2em] text-white/40">
              Institution profile
            </div>
            <div className="mt-6 space-y-4">
              {[
                ["Founded", tenant.founded || "—"],
                ["Country", tenant.country || "Rwanda"],
                ["Registration", tenant.registrationNumber || "—"],
                ["Currency", tenant.currency || "RWF"],
              ].map(([a, b]) => (
                <div
                  key={a}
                  className="flex justify-between gap-5 border-b border-white/10 pb-3 text-sm"
                >
                  <span className="text-white/45">{a}</span>
                  <b>{b}</b>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      <section className="public-section bg-white">
        <div className="mx-auto grid max-w-7xl gap-14 px-5 lg:grid-cols-[.9fr_1.1fr]">
          <div>
            <div className="public-eyebrow text-[#B08A27]">Our purpose</div>
            <h2 className="public-title text-[#0B1F3A]">
              Modern lending should still feel personal.
            </h2>
          </div>
          <div className="space-y-6 text-base leading-8 text-slate-500">
            <p>
              {tenant.mission ||
                "Our mission is to make access to appropriate finance more understandable and more dignified for the clients we serve."}
            </p>
            <p>
              {tenant.vision ||
                "Our vision is to build long-term financial relationships through responsible lending, thoughtful service and disciplined operations."}
            </p>
            <div className="grid gap-3 pt-3 sm:grid-cols-2">
              {[
                "Integrity in every decision",
                "Transparency in every offer",
                "Security in every interaction",
                "Excellence in every service",
              ].map((x) => (
                <div
                  key={x}
                  className="rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm font-semibold text-[#0B1F3A]"
                >
                  <span className="mr-2 text-[#0D6B5B]">
                    <Check />
                  </span>
                  {x}
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      <section className="public-section bg-[#F7F8FA]">
        <div className="mx-auto max-w-7xl px-5">
          <div className="max-w-2xl">
            <div className="public-eyebrow text-[#B08A27]">Our standards</div>
            <h2 className="public-title text-[#0B1F3A]">
              What clients should expect from us
            </h2>
          </div>
          <div className="mt-12 grid gap-5 md:grid-cols-2 lg:grid-cols-4">
            {[
              [
                "01",
                "Responsible",
                "We focus on appropriate facilities and clear obligations.",
              ],
              [
                "02",
                "Transparent",
                "Key terms and charges should be understandable before commitment.",
              ],
              [
                "03",
                "Secure",
                "Digital interactions are designed with privacy and security in mind.",
              ],
              [
                "04",
                "Responsive",
                "Clients should know where to go for help and what happens next.",
              ],
            ].map(([n, t, d]) => (
              <div key={t} className="public-standard-card">
                <div className="text-xs font-bold text-[#B08A27]">{n}</div>
                <h3 className="mt-5 font-serif text-xl font-semibold text-[#0B1F3A]">
                  {t}
                </h3>
                <p className="mt-2 text-sm leading-6 text-slate-500">{d}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {tenant.team?.length ? (
        <section className="public-section bg-white">
          <div className="mx-auto max-w-7xl px-5">
            <div className="text-center">
              <div className="public-eyebrow text-[#B08A27]">Leadership</div>
              <h2 className="public-title text-[#0B1F3A]">
                People behind the relationship
              </h2>
              <p className="mt-4 text-slate-500">
                Experienced professionals supporting responsible lending and
                client service.
              </p>
            </div>
            <div className="mt-12 grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
              {tenant.team.map((p: any) => (
                <div
                  key={p.name}
                  className="rounded-3xl border border-slate-200 bg-white p-6 text-center shadow-[0_12px_35px_rgba(7,27,53,.05)]"
                >
                  <div
                    className="mx-auto flex h-20 w-20 items-center justify-center rounded-full text-xl font-bold text-white"
                    style={{ backgroundColor: primary }}
                  >
                    {p.initials}
                  </div>
                  <div className="mt-5 font-semibold text-[#0B1F3A]">
                    {p.name}
                  </div>
                  <div className="mt-1 text-sm text-slate-400">{p.role}</div>
                </div>
              ))}
            </div>
          </div>
        </section>
      ) : null}

      <section className="public-section bg-[#071B35] text-white">
        <div className="mx-auto grid max-w-7xl gap-10 px-5 md:grid-cols-3">
          <div className="md:col-span-2">
            <div className="public-eyebrow text-[#D9B95B]">
              A long-term relationship
            </div>
            <h2 className="mt-2 font-serif text-4xl font-semibold">
              We measure success by the quality of the financial relationship,
              not just the transaction.
            </h2>
          </div>
          <div className="flex items-end">
            <Link
              href="/services"
              className="public-btn-primary"
              style={{ backgroundColor: gold, color: "#071B35" }}
            >
              Explore our services <Arrow />
            </Link>
          </div>
        </div>
      </section>
    </div>
  );
}
