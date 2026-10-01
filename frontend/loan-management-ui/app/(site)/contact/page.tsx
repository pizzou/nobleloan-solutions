import Link from "next/link";
import { SITE_CONTENT } from "../../../lib/siteContent";

function Icon({ type }: { type: "phone" | "mail" | "pin" }) {
  if (type === "phone") {
    return (
      <svg
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.8"
        className="h-5 w-5"
        aria-hidden="true"
      >
        <path d="M22 16.9v3a2 2 0 0 1-2.2 2A19.8 19.8 0 0 1 11.2 19a19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 2.1 4.3 2 2 0 0 1 4.1 2h3a2 2 0 0 1 2 1.7c.1 1 .4 1.9.7 2.8a2 2 0 0 1-.4 2.1L8.1 9.9a16 16 0 0 0 6 6l1.3-1.3a2 2 0 0 1 2.1-.4c.9.3 1.8.6 2.8.7a2 2 0 0 1 1.7 2Z" />
      </svg>
    );
  }

  if (type === "mail") {
    return (
      <svg
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.8"
        className="h-5 w-5"
        aria-hidden="true"
      >
        <rect x="3" y="5" width="18" height="14" rx="2" />
        <path d="m3 7 9 6 9-6" />
      </svg>
    );
  }

  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      className="h-5 w-5"
      aria-hidden="true"
    >
      <path d="M20 10c0 5-8 11-8 11S4 15 4 10a8 8 0 1 1 16 0Z" />
      <circle cx="12" cy="10" r="2.5" />
    </svg>
  );
}

export default function ContactPage() {
  const tenant = SITE_CONTENT;
  const primary = tenant.primaryColor;
  const accent = tenant.accentColor;

  const email = tenant.contactEmail;
  const phone = tenant.contactPhone;
  const address = tenant.address;

  const mapHref = address
    ? `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(
        address,
      )}`
    : null;

  return (
    <main className="bg-white text-slate-950">
      <section
        className="relative overflow-hidden text-white"
        style={{ backgroundColor: primary }}
      >
        <div className="absolute -right-20 -top-32 h-96 w-96 rounded-full border border-white/10" />

        <div className="relative mx-auto max-w-7xl px-5 py-20 sm:px-8 lg:py-24">
          <div
            className="text-[10px] font-black uppercase tracking-[.22em]"
            style={{ color: accent }}
          >
            Client support
          </div>

          <h1 className="mt-4 max-w-3xl text-5xl font-black tracking-[-.05em] sm:text-6xl">
            We&apos;re here when you need us.
          </h1>

          <p className="mt-6 max-w-2xl text-base leading-7 text-white/65 sm:text-lg">
            Questions about a loan, an application, repayment, or our financing
            solutions? Reach the Noble team through the channel that works best
            for you.
          </p>
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-5 py-16 sm:px-8 lg:py-24">
        <div className="grid gap-10 lg:grid-cols-[.8fr_1.2fr]">
          <div>
            <div
              className="text-[10px] font-black uppercase tracking-[.22em]"
              style={{ color: accent }}
            >
              Contact details
            </div>

            <h2 className="mt-3 text-3xl font-black tracking-[-.035em]">
              Get in touch with Noble.
            </h2>

            <p className="mt-4 text-sm leading-7 text-slate-500">
              For loan applications, use the secure application journey. For
              existing applications, use the tracking service.
            </p>

            <div className="mt-8 space-y-3">
              {address && (
                <a
                  href={mapHref || "#"}
                  target={mapHref ? "_blank" : undefined}
                  rel={mapHref ? "noreferrer" : undefined}
                  className="flex gap-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition hover:-translate-y-0.5 hover:shadow-md"
                >
                  <span
                    className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl"
                    style={{
                      backgroundColor: `${primary}0D`,
                      color: primary,
                    }}
                  >
                    <Icon type="pin" />
                  </span>

                  <div>
                    <div className="text-[9px] font-black uppercase tracking-[.15em] text-slate-400">
                      Office
                    </div>
                    <div className="mt-1 text-sm font-bold text-slate-800">
                      {address}
                    </div>
                  </div>
                </a>
              )}

              {phone && (
                <a
                  href={`tel:${phone.replace(/\s+/g, "")}`}
                  className="flex gap-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition hover:-translate-y-0.5 hover:shadow-md"
                >
                  <span
                    className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl"
                    style={{
                      backgroundColor: `${primary}0D`,
                      color: primary,
                    }}
                  >
                    <Icon type="phone" />
                  </span>

                  <div>
                    <div className="text-[9px] font-black uppercase tracking-[.15em] text-slate-400">
                      Phone
                    </div>
                    <div className="mt-1 text-sm font-bold text-slate-800">
                      {phone}
                    </div>
                  </div>
                </a>
              )}

              {email && (
                <a
                  href={`mailto:${email}`}
                  className="flex gap-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition hover:-translate-y-0.5 hover:shadow-md"
                >
                  <span
                    className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl"
                    style={{
                      backgroundColor: `${primary}0D`,
                      color: primary,
                    }}
                  >
                    <Icon type="mail" />
                  </span>

                  <div>
                    <div className="text-[9px] font-black uppercase tracking-[.15em] text-slate-400">
                      Email
                    </div>
                    <div className="mt-1 break-all text-sm font-bold text-slate-800">
                      {email}
                    </div>
                  </div>
                </a>
              )}
            </div>

            <Link
              href="/track"
              className="mt-6 inline-flex text-sm font-black"
              style={{ color: primary }}
            >
              Already applied? Track your application →
            </Link>
          </div>

          <div className="rounded-[30px] border border-slate-200 bg-slate-50 p-8 sm:p-10">
            <div
              className="text-[10px] font-black uppercase tracking-[.22em]"
              style={{ color: accent }}
            >
              What do you need?
            </div>

            <h2 className="mt-3 text-2xl font-black">
              Choose the fastest route.
            </h2>

            <div className="mt-8 grid gap-3 sm:grid-cols-2">
              <Link
                href="/apply"
                className="rounded-2xl border border-slate-200 bg-white p-6 transition hover:-translate-y-0.5 hover:shadow-md"
              >
                <div className="text-sm font-black">Apply for financing</div>
                <div className="mt-2 text-xs leading-6 text-slate-500">
                  Submit a secure loan application and supporting documents.
                </div>
                <div
                  className="mt-5 text-xs font-black"
                  style={{ color: primary }}
                >
                  Start application →
                </div>
              </Link>

              <Link
                href="/track"
                className="rounded-2xl border border-slate-200 bg-white p-6 transition hover:-translate-y-0.5 hover:shadow-md"
              >
                <div className="text-sm font-black">Track an application</div>
                <div className="mt-2 text-xs leading-6 text-slate-500">
                  Check the current status of an application you already
                  submitted.
                </div>
                <div
                  className="mt-5 text-xs font-black"
                  style={{ color: primary }}
                >
                  Track application →
                </div>
              </Link>

              <Link
                href="/services"
                className="rounded-2xl border border-slate-200 bg-white p-6 transition hover:-translate-y-0.5 hover:shadow-md"
              >
                <div className="text-sm font-black">Explore loan solutions</div>
                <div className="mt-2 text-xs leading-6 text-slate-500">
                  Compare the financing options available through Noble.
                </div>
                <div
                  className="mt-5 text-xs font-black"
                  style={{ color: primary }}
                >
                  Explore solutions →
                </div>
              </Link>

              <Link
                href="/faq"
                className="rounded-2xl border border-slate-200 bg-white p-6 transition hover:-translate-y-0.5 hover:shadow-md"
              >
                <div className="text-sm font-black">Read FAQs</div>
                <div className="mt-2 text-xs leading-6 text-slate-500">
                  Find answers about applications, terms, repayment, and
                  eligibility.
                </div>
                <div
                  className="mt-5 text-xs font-black"
                  style={{ color: primary }}
                >
                  View FAQs →
                </div>
              </Link>
            </div>
          </div>
        </div>
      </section>

      {tenant.mapUrl && (
        <section className="border-t border-slate-100 bg-slate-50">
          <div className="mx-auto max-w-7xl px-5 py-16 sm:px-8">
            <div className="overflow-hidden rounded-[28px] border border-slate-200 bg-white shadow-sm">
              <iframe
                title="Noble Loan Solutions office location"
                src={tenant.mapUrl}
                className="h-[360px] w-full border-0"
                loading="lazy"
                referrerPolicy="no-referrer-when-downgrade"
              />
            </div>
          </div>
        </section>
      )}
    </main>
  );
}
