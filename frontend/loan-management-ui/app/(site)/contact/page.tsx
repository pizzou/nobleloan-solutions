"use client";

import { useState } from "react";
import Link from "next/link";
import { useTenant } from "../layout";
import { TENANT_SLUG } from "../../../lib/tenant";
import { getApiBaseUrl } from "../../../lib/apiBase";

function Arrow() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.9"
      className="h-4 w-4"
    >
      <path d="M5 12h14" />
      <path d="m13 6 6 6-6 6" />
    </svg>
  );
}
function Phone() {
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
}
function Mail() {
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
}
function Pin() {
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
  const tenant = useTenant();
  const [form, setForm] = useState({
    name: "",
    email: "",
    phone: "",
    subject: "",
    message: "",
  });
  const [sent, setSent] = useState(false);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState("");
  if (!tenant) return null;
  const primary = tenant.primaryColor || "#0B1F3A";
  const accent = tenant.accentColor || "#D4AF37";
  const update =
    (key: keyof typeof form) =>
    (
      e: React.ChangeEvent<
        HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement
      >,
    ) =>
      setForm((f) => ({ ...f, [key]: e.target.value }));
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSending(true);
    setError("");
    try {
      const res = await fetch(`${getApiBaseUrl()}/public/contact`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ...form, tenantSlug: TENANT_SLUG }),
      });
      const json = await res.json();
      if (!res.ok || json.success === false)
        throw new Error(
          json.error || json.message || "Could not send your message.",
        );
      setSent(true);
    } catch (err: any) {
      setError(err.message || "Something went wrong. Please try again.");
    } finally {
      setSending(false);
    }
  };
  const field =
    "mt-2 w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-3.5 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-slate-400 focus:bg-white focus:ring-4 focus:ring-slate-100";
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
              Use the details below for direct assistance, or send a message and
              our team will follow up.
            </p>
            <div className="mt-8 space-y-3">
              <div className="flex gap-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                <span
                  className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl"
                  style={{ backgroundColor: `${primary}0D`, color: primary }}
                >
                  <Pin />
                </span>
                <div>
                  <div className="text-[9px] font-black uppercase tracking-[.15em] text-slate-400">
                    Office
                  </div>
                  <div className="mt-1 text-sm font-bold text-slate-800 whitespace-pre-line">
                    {tenant.address || "Kigali, Rwanda"}
                  </div>
                  <div className="mt-1 text-xs text-slate-400">
                    Monday–Friday · 8:00 AM–5:00 PM
                  </div>
                </div>
              </div>
              <div className="flex gap-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                <span
                  className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl"
                  style={{ backgroundColor: `${primary}0D`, color: primary }}
                >
                  <Phone />
                </span>
                <div>
                  <div className="text-[9px] font-black uppercase tracking-[.15em] text-slate-400">
                    Phone
                  </div>
                  <div className="mt-1 text-sm font-bold text-slate-800">
                    {tenant.contactPhone || "+250 788 000 000"}
                  </div>
                  <div className="mt-1 text-xs text-slate-400">
                    Available during business hours
                  </div>
                </div>
              </div>
              <div className="flex gap-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                <span
                  className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl"
                  style={{ backgroundColor: `${primary}0D`, color: primary }}
                >
                  <Mail />
                </span>
                <div>
                  <div className="text-[9px] font-black uppercase tracking-[.15em] text-slate-400">
                    Email
                  </div>
                  <div className="mt-1 break-all text-sm font-bold text-slate-800">
                    {tenant.contactEmail || "info@nobleloansolutions.rw"}
                  </div>
                  <div className="mt-1 text-xs text-slate-400">
                    We aim to respond promptly
                  </div>
                </div>
              </div>
            </div>
            <Link
              href="/track"
              className="mt-6 inline-flex items-center gap-2 text-sm font-black"
              style={{ color: primary }}
            >
              Already applied? Track your application <Arrow />
            </Link>
          </div>
          <div className="rounded-[30px] border border-slate-200 bg-white p-6 shadow-[0_25px_80px_rgba(15,23,42,.08)] sm:p-9">
            {sent ? (
              <div className="flex min-h-[440px] flex-col items-center justify-center text-center">
                <div
                  className="flex h-16 w-16 items-center justify-center rounded-2xl text-2xl"
                  style={{ backgroundColor: `${accent}20`, color: primary }}
                >
                  ✓
                </div>
                <h2 className="mt-6 text-2xl font-black">Message received.</h2>
                <p className="mt-3 max-w-md text-sm leading-7 text-slate-500">
                  Thank you for contacting {tenant.name}. Our team will get back
                  to you using the details you provided.
                </p>
                <button
                  onClick={() => {
                    setSent(false);
                    setForm({
                      name: "",
                      email: "",
                      phone: "",
                      subject: "",
                      message: "",
                    });
                  }}
                  className="mt-7 rounded-xl px-5 py-3 text-sm font-black text-white"
                  style={{ backgroundColor: primary }}
                >
                  Send another message
                </button>
              </div>
            ) : (
              <>
                <div
                  className="text-[10px] font-black uppercase tracking-[.22em]"
                  style={{ color: accent }}
                >
                  Send a message
                </div>
                <h2 className="mt-3 text-2xl font-black">How can we help?</h2>
                <form onSubmit={submit} className="mt-7 space-y-4">
                  <div className="grid gap-4 sm:grid-cols-2">
                    <label className="text-[10px] font-black uppercase tracking-[.14em] text-slate-500">
                      Full name
                      <input
                        required
                        value={form.name}
                        onChange={update("name")}
                        className={field}
                      />
                    </label>
                    <label className="text-[10px] font-black uppercase tracking-[.14em] text-slate-500">
                      Phone
                      <input
                        required
                        value={form.phone}
                        onChange={update("phone")}
                        className={field}
                      />
                    </label>
                  </div>
                  <div className="grid gap-4 sm:grid-cols-2">
                    <label className="text-[10px] font-black uppercase tracking-[.14em] text-slate-500">
                      Email
                      <input
                        required
                        type="email"
                        value={form.email}
                        onChange={update("email")}
                        className={field}
                      />
                    </label>
                    <label className="text-[10px] font-black uppercase tracking-[.14em] text-slate-500">
                      Subject
                      <select
                        required
                        value={form.subject}
                        onChange={update("subject")}
                        className={field}
                      >
                        <option value="">Select a subject</option>
                        <option>Loan enquiry</option>
                        <option>Application support</option>
                        <option>Repayment support</option>
                        <option>General enquiry</option>
                      </select>
                    </label>
                  </div>
                  <label className="block text-[10px] font-black uppercase tracking-[.14em] text-slate-500">
                    Message
                    <textarea
                      required
                      value={form.message}
                      onChange={update("message")}
                      className={`${field} min-h-[150px] resize-y`}
                    />
                  </label>
                  {error && (
                    <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
                      {error}
                    </div>
                  )}
                  <button
                    disabled={sending}
                    className="inline-flex w-full items-center justify-center gap-2 rounded-xl px-5 py-3.5 text-sm font-black text-white shadow-lg disabled:opacity-60"
                    style={{ backgroundColor: primary }}
                  >
                    {sending ? "Sending…" : "Send message"}
                    <Arrow />
                  </button>
                  <p className="text-center text-[10px] leading-5 text-slate-400">
                    Do not include passwords, card details or other sensitive
                    credentials in this form.
                  </p>
                </form>
              </>
            )}
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
