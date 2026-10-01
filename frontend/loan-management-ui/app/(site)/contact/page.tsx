import Link from "next/link";
import { SITE_CONTENT } from "../../../lib/siteContent";

function Icon({ type }: { type: "phone" | "mail" | "pin" }) {
  if (type === "phone")
    return (
      <svg
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.8"
        className="h-5 w-5"
      >
        <path d="M22 16.9v3a2 2 0 0 1-2.2 2A19.8 19.8 0 0 1 11.2 19a19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 2.1 4.3 2 2 0 0 1 4.1 2h3a2 2 0 0 1 2 1.7c.1 1 .4 1.9.7 2.8a2 2 0 0 1-.4 2.1L8.1 9.9a16 16 0 0 0 6 6l1.3-1.3a2 2 0 0 1 2.1-.4c.9.3 1.8.6 2.8.7a2 2 0 0 1 1.7 2Z" />
      </svg>
    );
  if (type === "mail")
    return (
      <svg
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.8"
        className="h-5 w-5"
      >
        <rect x="3" y="5" width="18" height="14" rx="2" />
        <path d="m3 7 9 6 9-6" />
      </svg>
    );
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      className="h-5 w-5"
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
  const email = tenant.contactEmail || "Email address not configured";
  const phone = tenant.contactPhone || "Phone number not configured";
  const address = tenant.address || "Office address not configured";
  const mailHref = tenant.contactEmail ? `mailto:${tenant.contactEmail}` : "#";
  const phoneHref = tenant.contactPhone
    ? `tel:${tenant.contactPhone.replace(/\s+/g, "")}`
    : "#";
  const mapHref =
    address !== "Office address not configured"
      ? `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(address)}`
      : "#";

  return (
    <main className="bg-white text-slate-950">
      <section className="relative overflow-hidden bg-[#061326] text-white">
        <div
          className="absolute inset-0"
          style={{
            background: `radial-gradient(circle at 80% 10%,${accent}24,transparent 25%),linear-gradient(135deg,#061326,${primary})`,
          }}
        />
        <div className="relative mx-auto max-w-7xl px-5 py-20 sm:px-8 lg:py-24">
          <div className="max-w-3xl">
            <div
              className="text-[10px] font-black uppercase tracking-[.22em]"
              style={{ color: accent }}
            >
              Client care
            </div>
            <h1 className="mt-4 text-5xl font-black tracking-[-.05em] sm:text-6xl">
              Let&apos;s talk about what you need.
            </h1>
            <p className="mt-6 max-w-2xl text-base leading-7 text-white/60 sm:text-lg">
              Questions about a loan, an application or repayment? Reach the{" "}
              {tenant.name} team through the channel that works best for you.
            </p>
          </div>
        </div>
      </section>
      <section className="mx-auto max-w-7xl px-5 py-16 sm:px-8 lg:py-24">
        <div className="grid gap-10 lg:grid-cols-[.75fr_1.25fr]">
          <div>
            <div
              className="text-[10px] font-black uppercase tracking-[.22em]"
              style={{ color: accent }}
            >
              Contact details
            </div>
            <h2 className="mt-3 text-3xl font-black tracking-[-.035em]">
              We&apos;re here to help.
            </h2>
            <p className="mt-4 text-sm leading-7 text-slate-500">
              Use the published details below for direct assistance. This page
              does not depend on the lending backend.
            </p>
            <div className="mt-8 space-y-3">
              <a
                href={mapHref}
                target="_blank"
                rel="noreferrer"
                className="flex gap-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition hover:-translate-y-0.5 hover:shadow-md"
              >
                <span
                  className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl"
                  style={{ backgroundColor: `${primary}0D`, color: primary }}
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
                  <div className="mt-1 text-xs text-slate-400">
                    Monday–Friday · 8:00 AM–5:00 PM
                  </div>
                </div>
              </a>
              <a
                href={phoneHref}
                className="flex gap-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition hover:-translate-y-0.5 hover:shadow-md"
              >
                <span
                  className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl"
                  style={{ backgroundColor: `${primary}0D`, color: primary }}
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
                  <div className="mt-1 text-xs text-slate-400">
                    Available during business hours
                  </div>
                </div>
              </a>
              <a
                href={mailHref}
                className="flex gap-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition hover:-translate-y-0.5 hover:shadow-md"
              >
                <span
                  className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl"
                  style={{ backgroundColor: `${primary}0D`, color: primary }}
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
                  <div className="mt-1 text-xs text-slate-400">
                    Use email for general enquiries and support.
                  </div>
                </div>
              </a>
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
              Direct support
            </div>
            <h2 className="mt-3 text-2xl font-black">
              Choose how you want to reach us.
            </h2>
            <p className="mt-4 text-sm leading-7 text-slate-500">
              Contact information is delivered with the website itself. There is
              no backend request just to view this page.
            </p>
            <div className="mt-8 grid gap-3 sm:grid-cols-2">
              <a
                href={mailHref}
                className="rounded-2xl border border-slate-200 bg-white p-5 hover:shadow-md"
              >
                <div className="text-sm font-black">Email us</div>
                <div className="mt-1 break-all text-xs text-slate-500">
                  {email}
                </div>
              </a>
              <a
                href={phoneHref}
                className="rounded-2xl border border-slate-200 bg-white p-5 hover:shadow-md"
              >
                <div className="text-sm font-black">Call us</div>
                <div className="mt-1 text-xs text-slate-500">{phone}</div>
              </a>
              <Link
                href="/apply"
                className="rounded-2xl border border-slate-200 bg-white p-5 hover:shadow-md"
              >
                <div className="text-sm font-black">Apply for a loan</div>
                <div className="mt-1 text-xs text-slate-500">
                  Application submission uses the lending backend.
                </div>
              </Link>
              <Link
                href="/track"
                className="rounded-2xl border border-slate-200 bg-white p-5 hover:shadow-md"
              >
                <div className="text-sm font-black">Track an application</div>
                <div className="mt-1 text-xs text-slate-500">
                  Live application status uses the lending backend.
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
                title="Office location"
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
