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

export default function ServicesPage() {
  const tenant = useTenant();
  if (!tenant) return null;
  const primary = tenant.primaryColor || "#0D2C54";
  const gold = tenant.accentColor || "#D4AF37";
  const services = tenant.services || [];
  return (
    <div className="public-site">
      <section className="relative overflow-hidden bg-[#071B35] py-20 text-white md:py-28">
        <div className="public-grid absolute inset-0 opacity-25" />
        <div className="relative mx-auto max-w-5xl px-5 text-center">
          <div className="public-kicker mx-auto border border-[#D4AF37]/35 bg-white/5 text-[#E7CC78]">
            Our lending architecture
          </div>
          <h1 className="mt-6 font-serif text-5xl font-semibold tracking-[-.03em] md:text-6xl">
            Solutions designed around the purpose of the capital.
          </h1>
          <p className="mx-auto mt-6 max-w-3xl text-lg leading-8 text-white/65">
            From personal priorities to professional and business needs, our
            facilities are presented with clear structure, transparent terms and
            a disciplined credit journey.
          </p>
        </div>
      </section>

      <section className="border-b border-slate-200 bg-white">
        <div className="mx-auto grid max-w-7xl gap-4 px-5 py-7 sm:grid-cols-3">
          <div className="public-mini-stat">
            <b>Purpose-led</b>
            <span>Facilities matched to real needs</span>
          </div>
          <div className="public-mini-stat">
            <b>Transparent</b>
            <span>Terms presented before commitment</span>
          </div>
          <div className="public-mini-stat">
            <b>Supported</b>
            <span>Human help throughout the relationship</span>
          </div>
        </div>
      </section>

      <section className="public-section bg-[#F7F8FA]">
        <div className="mx-auto max-w-7xl px-5">
          <div className="public-eyebrow text-[#B08A27]">
            Explore facilities
          </div>
          <h2 className="public-title text-[#0B1F3A]">
            A focused portfolio of lending options
          </h2>
          <div className="mt-12 space-y-5">
            {services.map((s: any, i: number) => (
              <article key={s.title} className="public-service-row">
                <div
                  className="flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl text-2xl"
                  style={{ backgroundColor: primary + "12", color: primary }}
                >
                  {s.icon || "◈"}
                </div>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div>
                      <div className="text-xs font-bold uppercase tracking-[.18em] text-slate-400">
                        Facility {String(i + 1).padStart(2, "0")}
                      </div>
                      <h3 className="mt-1 font-serif text-2xl font-semibold text-[#0B1F3A]">
                        {s.title}
                      </h3>
                    </div>
                    <Link
                      href="/apply"
                      className="hidden items-center gap-2 text-sm font-bold text-[#0D6B5B] sm:flex"
                    >
                      Apply <Arrow />
                    </Link>
                  </div>
                  <p className="mt-3 max-w-3xl text-sm leading-6 text-slate-500">
                    {s.description}
                  </p>
                  <div className="mt-5 grid max-w-2xl grid-cols-2 gap-3 sm:grid-cols-4">
                    <div>
                      <span className="public-field-label">Rate</span>
                      <b>
                        {s.rate}
                        {s.rateType === "MONTHLY" ? " / month" : ""}
                      </b>
                    </div>
                    <div>
                      <span className="public-field-label">Maximum</span>
                      <b>
                        {tenant.currency} {s.maxAmount}
                      </b>
                    </div>
                    <div>
                      <span className="public-field-label">Term</span>
                      <b>{s.term}</b>
                    </div>
                    <div>
                      <span className="public-field-label">Digital</span>
                      <b>Available</b>
                    </div>
                  </div>
                  <Link
                    href="/apply"
                    className="mt-5 inline-flex items-center gap-2 text-sm font-bold text-[#0D6B5B] sm:hidden"
                  >
                    Apply for this facility <Arrow />
                  </Link>
                </div>
              </article>
            ))}
            {!services.length && (
              <div className="public-empty">
                No public facilities have been configured yet. Contact our team
                for current lending options.
              </div>
            )}
          </div>
        </div>
      </section>

      <section className="public-section bg-white">
        <div className="mx-auto grid max-w-7xl gap-12 px-5 lg:grid-cols-2">
          <div>
            <div className="public-eyebrow text-[#B08A27]">
              Responsible lending
            </div>
            <h2 className="public-title text-[#0B1F3A]">
              The facility is only part of the relationship.
            </h2>
            <p className="mt-5 text-base leading-7 text-slate-500">
              We aim to make every stage understandable: what information is
              required, how the application is assessed, what the facility costs
              and how servicing works after disbursement.
            </p>
            <div className="mt-8 space-y-4">
              {[
                "Clear documentation and facility terms",
                "Secure online application journey",
                "Structured credit review",
                "Ongoing repayment and account support",
                "Accessible client communication",
              ].map((x) => (
                <div
                  key={x}
                  className="flex items-center gap-3 text-sm font-semibold text-slate-700"
                >
                  <span className="text-[#0D6B5B]">
                    <Check />
                  </span>
                  {x}
                </div>
              ))}
            </div>
          </div>
          <div className="rounded-[2rem] bg-[#071B35] p-8 text-white md:p-10">
            <div className="public-eyebrow text-[#D9B95B]">
              Application journey
            </div>
            <div className="mt-7 space-y-6">
              {[
                ["01", "Apply", "Complete the essential information securely."],
                [
                  "02",
                  "Review",
                  "We assess the application using the required information.",
                ],
                [
                  "03",
                  "Decision",
                  "Receive the outcome and understand the next step.",
                ],
                [
                  "04",
                  "Service",
                  "Manage your facility with continued support.",
                ],
              ].map(([n, t, d]) => (
                <div key={n} className="flex gap-4">
                  <div className="font-serif text-2xl text-[#D4AF37]">{n}</div>
                  <div>
                    <b>{t}</b>
                    <p className="mt-1 text-sm leading-6 text-white/50">{d}</p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      <section className="public-section bg-[#F7F8FA]">
        <div className="mx-auto max-w-5xl px-5 text-center">
          <div className="public-eyebrow text-[#B08A27]">Need guidance?</div>
          <h2 className="public-title text-[#0B1F3A]">
            Not sure which facility fits your objective?
          </h2>
          <p className="mt-4 text-slate-500">
            Speak with our team before applying. We can help you understand the
            available options and the information you will need.
          </p>
          <div className="mt-8 flex flex-wrap justify-center gap-3">
            <Link
              href="/contact"
              className="public-btn-dark"
              style={{ backgroundColor: primary }}
            >
              Talk to an advisor <Arrow />
            </Link>
            <Link
              href="/apply"
              className="public-btn-outline"
              style={{ borderColor: gold, color: primary }}
            >
              Start application <Arrow />
            </Link>
          </div>
        </div>
      </section>
    </div>
  );
}
