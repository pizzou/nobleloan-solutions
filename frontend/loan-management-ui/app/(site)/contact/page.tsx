"use client";

import Link from "next/link";
import { FormEvent, useMemo, useState } from "react";
import { publicApi } from "../../../services/api";
import { SITE_CONTENT } from "../../../lib/siteContent";
import { useTenant } from "../layout";

function Icon({ type }: { type: "phone" | "mail" | "pin" | "clock" }) {
  const base = {
    viewBox: "0 0 24 24",
    fill: "none",
    stroke: "currentColor",
    strokeWidth: 1.8,
    className: "h-5 w-5",
    "aria-hidden": true,
  } as const;
  if (type === "phone")
    return (
      <svg {...base}>
        <path d="M22 16.9v3a2 2 0 0 1-2.2 2A19.8 19.8 0 0 1 11.2 19a19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 2.1 4.3 2 2 0 0 1 4.1 2h3a2 2 0 0 1 2 1.7c.1 1 .4 1.9.7 2.8a2 2 0 0 1-.4 2.1L8.1 9.9a16 16 0 0 0 6 6l1.3-1.3a2 2 0 0 1 2.1-.4c.9.3 1.8.6 2.8.7a2 2 0 0 1 1.7 2Z" />
      </svg>
    );
  if (type === "mail")
    return (
      <svg {...base}>
        <rect x="3" y="5" width="18" height="14" rx="2" />
        <path d="m3 7 9 6 9-6" />
      </svg>
    );
  if (type === "clock")
    return (
      <svg {...base}>
        <circle cx="12" cy="12" r="9" />
        <path d="M12 7v5l3 2" />
      </svg>
    );
  return (
    <svg {...base}>
      <path d="M20 10c0 5-8 11-8 11S4 15 4 10a8 8 0 1 1 16 0Z" />
      <circle cx="12" cy="10" r="2.5" />
    </svg>
  );
}

const SUBJECTS = [
  "Loan enquiry",
  "Application support",
  "Repayment question",
  "Existing loan",
  "Business finance",
  "General enquiry",
];

export default function ContactPage() {
  const tenant = useTenant() || SITE_CONTENT;
  const primary = tenant.primaryColor || "#0F1B3D";
  const accent = tenant.accentColor || "#C9A227";
  const email = tenant.contactEmail || "info@nobleloansolutions.rw";
  const phone = tenant.contactPhone || "+250 788 123 456";
  const address = tenant.address || "Kigali, Rwanda";
  const [form, setForm] = useState({
    name: "",
    email: "",
    phone: "",
    subject: "Loan enquiry",
    message: "",
  });
  const [status, setStatus] = useState<
    "idle" | "sending" | "success" | "error"
  >("idle");
  const [error, setError] = useState("");

  const mapHref = useMemo(
    () =>
      `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(address)}`,
    [address],
  );

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setStatus("sending");
    setError("");
    try {
      await publicApi.contact({ tenantSlug: tenant.slug, ...form });
      setForm({
        name: "",
        email: "",
        phone: "",
        subject: "Loan enquiry",
        message: "",
      });
      setStatus("success");
    } catch (cause) {
      setStatus("error");
      setError(
        cause instanceof Error
          ? cause.message
          : "We could not send your message. Please try again.",
      );
    }
  }

  return (
    <main className="overflow-hidden bg-[#f5f7fa] text-slate-950">
      <section
        className="relative overflow-hidden text-white"
        style={{
          background: `linear-gradient(120deg,#061326 0%,${primary} 63%,#1a416b 100%)`,
        }}
      >
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_85%_10%,rgba(201,162,39,.25),transparent_24%),radial-gradient(circle_at_12%_90%,rgba(255,255,255,.10),transparent_28%)]" />
        <div className="relative mx-auto grid max-w-7xl gap-10 px-5 py-16 sm:px-8 sm:py-24 lg:grid-cols-[1fr_390px] lg:items-end lg:py-28">
          <div>
            <p
              className="text-[10px] font-black uppercase tracking-[.24em]"
              style={{ color: accent }}
            >
              Noble customer support
            </p>
            <h1 className="mt-5 max-w-4xl text-4xl font-black leading-[.98] tracking-[-.06em] sm:text-6xl lg:text-[4.2rem]">
              Talk to the team behind your loan journey.
            </h1>
            <p className="mt-6 max-w-2xl text-base leading-8 text-white/70 sm:text-lg">
              Ask a question about a loan, application, repayment or
              eligibility. Your message is routed into the Noble dashboard so
              the lending team can review and respond.
            </p>
          </div>
          <div className="rounded-[28px] border border-white/12 bg-white/[.07] p-6 backdrop-blur-md">
            <div
              className="text-[9px] font-black uppercase tracking-[.2em]"
              style={{ color: accent }}
            >
              Already applied?
            </div>
            <h2 className="mt-3 text-2xl font-black">
              Track your application.
            </h2>
            <p className="mt-2 text-xs leading-6 text-white/55">
              Keep your application reference and phone number ready to check
              your current status.
            </p>
            <Link
              href="/track"
              className="mt-5 inline-flex rounded-2xl px-5 py-3 text-xs font-black text-slate-950"
              style={{ backgroundColor: accent }}
            >
              Track application →
            </Link>
          </div>
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-5 py-12 sm:px-8 sm:py-20">
        <div className="grid gap-8 lg:grid-cols-[.72fr_1.28fr] lg:items-start">
          <aside className="space-y-4">
            <div>
              <p
                className="text-[10px] font-black uppercase tracking-[.22em]"
                style={{ color: accent }}
              >
                Contact details
              </p>
              <h2 className="mt-3 text-3xl font-black tracking-[-.045em]">
                Choose the channel that suits you.
              </h2>
              <p className="mt-4 text-sm leading-7 text-slate-500">
                For a new loan, the secure application is the right starting
                point. For an existing application or loan, you can track
                progress or contact the support team.
              </p>
            </div>

            <div className="space-y-3">
              <a
                href={`tel:${phone.replace(/[^+\d]/g, "")}`}
                className="group flex gap-4 rounded-3xl border border-slate-200 bg-white p-5 shadow-sm transition hover:-translate-y-1 hover:shadow-xl"
              >
                <span
                  className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl"
                  style={{ backgroundColor: `${primary}0D`, color: primary }}
                >
                  <Icon type="phone" />
                </span>
                <span>
                  <span className="block text-[9px] font-black uppercase tracking-[.16em] text-slate-400">
                    Phone
                  </span>
                  <span className="mt-1 block text-sm font-black text-slate-800">
                    {phone}
                  </span>
                  <span className="mt-1 block text-[10px] text-slate-400">
                    Tap to call
                  </span>
                </span>
              </a>
              <a
                href={`mailto:${email}`}
                className="group flex gap-4 rounded-3xl border border-slate-200 bg-white p-5 shadow-sm transition hover:-translate-y-1 hover:shadow-xl"
              >
                <span
                  className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl"
                  style={{ backgroundColor: `${primary}0D`, color: primary }}
                >
                  <Icon type="mail" />
                </span>
                <span>
                  <span className="block text-[9px] font-black uppercase tracking-[.16em] text-slate-400">
                    Email
                  </span>
                  <span className="mt-1 block break-all text-sm font-black text-slate-800">
                    {email}
                  </span>
                  <span className="mt-1 block text-[10px] text-slate-400">
                    For detailed enquiries
                  </span>
                </span>
              </a>
              <a
                href={mapHref}
                target="_blank"
                rel="noreferrer"
                className="group flex gap-4 rounded-3xl border border-slate-200 bg-white p-5 shadow-sm transition hover:-translate-y-1 hover:shadow-xl"
              >
                <span
                  className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl"
                  style={{ backgroundColor: `${primary}0D`, color: primary }}
                >
                  <Icon type="pin" />
                </span>
                <span>
                  <span className="block text-[9px] font-black uppercase tracking-[.16em] text-slate-400">
                    Office
                  </span>
                  <span className="mt-1 block text-sm font-black text-slate-800">
                    {address}
                  </span>
                  <span className="mt-1 block text-[10px] text-slate-400">
                    Open location in Maps
                  </span>
                </span>
              </a>
              <div className="flex gap-4 rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
                <span
                  className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl"
                  style={{ backgroundColor: `${accent}18`, color: primary }}
                >
                  <Icon type="clock" />
                </span>
                <span>
                  <span className="block text-[9px] font-black uppercase tracking-[.16em] text-slate-400">
                    Submission
                  </span>
                  <span className="mt-1 block text-sm font-black text-slate-800">
                    Confirmation after successful submission
                  </span>
                  <span className="mt-1 block text-[10px] text-slate-400">
                    Authorised staff receive the enquiry in the dashboard
                  </span>
                </span>
              </div>
            </div>

            <div
              className="rounded-3xl p-6 text-white"
              style={{
                background: `linear-gradient(145deg,${primary},#061326)`,
              }}
            >
              <div
                className="text-[9px] font-black uppercase tracking-[.18em]"
                style={{ color: accent }}
              >
                Need a different route?
              </div>
              <div className="mt-4 grid gap-2">
                <Link
                  href="/services"
                  className="rounded-2xl border border-white/10 bg-white/[.05] px-4 py-3 text-xs font-bold text-white"
                >
                  Explore loan solutions →
                </Link>
                <Link
                  href="/faq"
                  className="rounded-2xl border border-white/10 bg-white/[.05] px-4 py-3 text-xs font-bold text-white"
                >
                  Read common questions →
                </Link>
              </div>
            </div>
          </aside>

          <div className="rounded-[34px] border border-slate-200 bg-white p-6 shadow-[0_28px_90px_rgba(15,27,61,.08)] sm:p-9 lg:p-10">
            <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-end">
              <div>
                <p
                  className="text-[10px] font-black uppercase tracking-[.22em]"
                  style={{ color: accent }}
                >
                  Send a message
                </p>
                <h2 className="mt-2 text-3xl font-black tracking-[-.045em]">
                  How can Noble help?
                </h2>
              </div>
              <span className="rounded-full bg-slate-100 px-3 py-1.5 text-[9px] font-black uppercase tracking-[.15em] text-slate-500">
                Secure enquiry
              </span>
            </div>

            <form onSubmit={submit} className="mt-8 space-y-5">
              <div className="grid gap-5 sm:grid-cols-2">
                <label className="block">
                  <span className="text-xs font-black text-slate-700">
                    Full name
                  </span>
                  <input
                    required
                    maxLength={120}
                    autoComplete="name"
                    value={form.name}
                    onChange={(e) => setForm({ ...form, name: e.target.value })}
                    placeholder="Your full name"
                    className="mt-2 w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3.5 text-sm font-semibold outline-none transition focus:border-slate-400 focus:bg-white"
                  />
                </label>
                <label className="block">
                  <span className="text-xs font-black text-slate-700">
                    Email address{" "}
                    <span className="font-normal text-slate-400">
                      (optional)
                    </span>
                  </span>
                  <input
                    type="email"
                    maxLength={180}
                    autoComplete="email"
                    value={form.email}
                    onChange={(e) =>
                      setForm({ ...form, email: e.target.value })
                    }
                    placeholder="you@example.com"
                    className="mt-2 w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3.5 text-sm font-semibold outline-none transition focus:border-slate-400 focus:bg-white"
                  />
                </label>
              </div>
              <div className="grid gap-5 sm:grid-cols-2">
                <label className="block">
                  <span className="text-xs font-black text-slate-700">
                    Phone number
                  </span>
                  <input
                    maxLength={40}
                    autoComplete="tel"
                    value={form.phone}
                    onChange={(e) =>
                      setForm({ ...form, phone: e.target.value })
                    }
                    placeholder="Your phone number"
                    className="mt-2 w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3.5 text-sm font-semibold outline-none transition focus:border-slate-400 focus:bg-white"
                  />
                </label>
                <label className="block">
                  <span className="text-xs font-black text-slate-700">
                    Subject
                  </span>
                  <select
                    value={form.subject}
                    onChange={(e) =>
                      setForm({ ...form, subject: e.target.value })
                    }
                    className="mt-2 w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3.5 text-sm font-semibold outline-none focus:border-slate-400 focus:bg-white"
                  >
                    {SUBJECTS.map((subject) => (
                      <option key={subject}>{subject}</option>
                    ))}
                  </select>
                </label>
              </div>
              <label className="block">
                <span className="text-xs font-black text-slate-700">
                  Your message
                </span>
                <textarea
                  required
                  minLength={5}
                  maxLength={3000}
                  rows={8}
                  value={form.message}
                  onChange={(e) =>
                    setForm({ ...form, message: e.target.value })
                  }
                  placeholder="Tell us what you need help with…"
                  className="mt-2 w-full resize-y rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3.5 text-sm font-semibold outline-none transition focus:border-slate-400 focus:bg-white"
                />
                <span className="mt-1 block text-[10px] text-slate-400">
                  Do not include passwords, OTP codes, card PINs or other
                  confidential credentials.
                </span>
              </label>

              {status === "success" && (
                <div
                  role="status"
                  className="rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-bold text-emerald-800"
                >
                  Message sent successfully. The Noble team has received your
                  enquiry.
                </div>
              )}
              {status === "error" && (
                <div
                  role="alert"
                  className="rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-bold text-red-700"
                >
                  {error}
                </div>
              )}

              <div className="flex flex-col justify-between gap-4 border-t border-slate-100 pt-5 sm:flex-row sm:items-center">
                <p className="max-w-md text-[10px] leading-5 text-slate-400">
                  Your message is submitted to the public contact endpoint and
                  becomes available to authorised Noble staff in the dashboard
                  message inbox.
                </p>
                <button
                  disabled={status === "sending"}
                  type="submit"
                  className="rounded-2xl px-7 py-4 text-sm font-black text-white shadow-lg transition hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-60"
                  style={{ backgroundColor: primary }}
                >
                  {status === "sending" ? "Sending…" : "Send message →"}
                </button>
              </div>
            </form>
          </div>
        </div>
      </section>

      <section className="border-t border-slate-200 bg-white py-14 sm:py-20">
        <div className="mx-auto grid max-w-7xl gap-5 px-5 sm:px-8 md:grid-cols-3">
          {[
            [
              "New application",
              "Ready to borrow? Review the active products and start the secure application.",
              "/apply",
              "Start an application",
            ],
            [
              "Existing application",
              "Already submitted? Use your reference and phone number to track progress.",
              "/track",
              "Track application",
            ],
            [
              "Need answers first",
              "Review practical questions about borrowing, repayment and the customer journey.",
              "/faq",
              "View FAQs",
            ],
          ].map(([title, body, href, cta]) => (
            <Link
              href={href}
              key={href}
              className="rounded-[26px] border border-slate-200 bg-slate-50 p-6 transition hover:-translate-y-1 hover:bg-white hover:shadow-xl"
            >
              <div
                className="text-[9px] font-black uppercase tracking-[.18em]"
                style={{ color: accent }}
              >
                Support route
              </div>
              <h2 className="mt-3 text-lg font-black">{title}</h2>
              <p className="mt-2 text-xs leading-6 text-slate-500">{body}</p>
              <div
                className="mt-5 text-xs font-black"
                style={{ color: primary }}
              >
                {cta} →
              </div>
            </Link>
          ))}
        </div>
      </section>
    </main>
  );
}
