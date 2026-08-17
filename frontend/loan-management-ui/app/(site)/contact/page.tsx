"use client";

import { useState } from "react";
import Link from "next/link";
import { useTenant } from "../layout";
import { publicApi } from "../../../services/api";

const INITIAL_FORM = {
  name: "",
  email: "",
  phone: "",
  subject: "",
  message: "",
};

function SectionEyebrow({
  children,
  color,
}: {
  children: React.ReactNode;
  color: string;
}) {
  return (
    <div
      className="text-[10px] font-black uppercase tracking-[0.24em]"
      style={{ color }}
    >
      {children}
    </div>
  );
}

export default function ContactPage() {
  const tenant = useTenant();

  const [form, setForm] = useState(INITIAL_FORM);
  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState("");

  if (!tenant) return null;

  const primary = tenant.primaryColor;
  const accent = tenant.accentColor;
  const socials = tenant.socialMedia || {};

  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (sending) return;

    setSending(true);
    setError("");

    try {
      await publicApi.submitContact({
        ...form,
        tenantSlug: tenant.slug,
      });

      setSent(true);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Unable to send your message.",
      );
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="bg-white pb-24">
      {/* HERO */}
      <section
        className="relative overflow-hidden text-white"
        style={{
          background: `linear-gradient(135deg, ${primary}, #071426)`,
        }}
      >
        <div
          className="absolute -right-32 -top-32 h-[450px] w-[450px] rounded-full blur-3xl"
          style={{
            backgroundColor: `${accent}18`,
          }}
        />

        <div className="relative mx-auto max-w-7xl px-4 py-20 md:py-28">
          <SectionEyebrow color={accent}>Contact {tenant.name}</SectionEyebrow>

          <h1 className="mt-5 max-w-4xl text-5xl font-black leading-[0.98] tracking-[-0.05em] md:text-7xl">
            Let&apos;s have a professional conversation.
          </h1>

          <p className="mt-7 max-w-2xl text-lg leading-8 text-white/60">
            Speak with {tenant.name} about financing, an existing application,
            repayment or any other enquiry.
          </p>
        </div>
      </section>

      <main className="mx-auto grid max-w-7xl gap-6 px-4 pt-10 lg:grid-cols-[0.78fr_1.22fr]">
        {/* LEFT */}
        <div className="space-y-5">
          <section className="rounded-[2rem] border border-slate-200 bg-white p-7 shadow-sm md:p-8">
            <SectionEyebrow color={accent}>
              Verified contact details
            </SectionEyebrow>

            <h2 className="mt-3 text-2xl font-black tracking-tight text-slate-950">
              Reach {tenant.name}
            </h2>

            <div className="mt-7 space-y-3">
              {tenant.address && (
                <div className="rounded-xl bg-[#f7f8fa] p-5">
                  <div className="text-[9px] font-black uppercase tracking-wider text-slate-400">
                    Office
                  </div>

                  <div className="mt-2 text-sm font-bold leading-6 text-slate-900">
                    {tenant.address}
                  </div>
                </div>
              )}

              {tenant.contactPhone && (
                <a
                  href={`tel:${tenant.contactPhone}`}
                  className="block rounded-xl bg-[#f7f8fa] p-5 transition hover:bg-slate-100"
                >
                  <div className="text-[9px] font-black uppercase tracking-wider text-slate-400">
                    Phone
                  </div>

                  <div className="mt-2 text-sm font-bold text-slate-900">
                    {tenant.contactPhone}
                  </div>
                </a>
              )}

              {tenant.contactEmail && (
                <a
                  href={`mailto:${tenant.contactEmail}`}
                  className="block rounded-xl bg-[#f7f8fa] p-5 transition hover:bg-slate-100"
                >
                  <div className="text-[9px] font-black uppercase tracking-wider text-slate-400">
                    Email
                  </div>

                  <div className="mt-2 break-all text-sm font-bold text-slate-900">
                    {tenant.contactEmail}
                  </div>
                </a>
              )}

              {tenant.registrationNumber && (
                <div className="rounded-xl bg-[#f7f8fa] p-5">
                  <div className="text-[9px] font-black uppercase tracking-wider text-slate-400">
                    Registration
                  </div>

                  <div className="mt-2 text-sm font-bold text-slate-900">
                    {tenant.registrationNumber}
                  </div>
                </div>
              )}
            </div>
          </section>

          {/* SERVICE SHORTCUTS */}
          <section className="rounded-[2rem] border border-slate-200 bg-white p-7 shadow-sm md:p-8">
            <SectionEyebrow color={accent}>Client services</SectionEyebrow>

            <div className="mt-6 space-y-2">
              <Link
                href="/services"
                className="flex items-center justify-between rounded-xl border border-slate-200 p-4 transition hover:bg-slate-50"
              >
                <span className="text-sm font-black text-slate-900">
                  Explore financing
                </span>

                <span style={{ color: primary }}>→</span>
              </Link>

              <Link
                href="/calculator"
                className="flex items-center justify-between rounded-xl border border-slate-200 p-4 transition hover:bg-slate-50"
              >
                <span className="text-sm font-black text-slate-900">
                  Calculate a loan
                </span>

                <span style={{ color: primary }}>→</span>
              </Link>

              <Link
                href="/track"
                className="flex items-center justify-between rounded-xl border border-slate-200 p-4 transition hover:bg-slate-50"
              >
                <span className="text-sm font-black text-slate-900">
                  Track an application
                </span>

                <span style={{ color: primary }}>→</span>
              </Link>
            </div>
          </section>

          {/* SOCIAL */}
          {Object.values(socials).some(Boolean) && (
            <section className="rounded-[2rem] border border-slate-200 bg-white p-7 shadow-sm md:p-8">
              <SectionEyebrow color={accent}>Connect with us</SectionEyebrow>

              <div className="mt-5 flex flex-wrap gap-2">
                {Object.entries(socials)
                  .filter(([, value]) => Boolean(value))
                  .map(([label, value]) => (
                    <a
                      key={label}
                      href={value as string}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="rounded-full border px-4 py-2.5 text-xs font-black capitalize transition hover:bg-slate-50"
                      style={{
                        borderColor: `${primary}40`,
                        color: primary,
                      }}
                    >
                      {label}
                    </a>
                  ))}
              </div>
            </section>
          )}

          {tenant.mapUrl && (
            <section className="overflow-hidden rounded-[2rem] border border-slate-200 bg-white shadow-sm">
              <div className="border-b border-slate-100 px-6 py-4">
                <div className="text-[9px] font-black uppercase tracking-[0.2em] text-slate-400">
                  Office location
                </div>
              </div>

              <iframe
                title={`${tenant.name} office location`}
                src={tenant.mapUrl}
                className="h-72 w-full border-0"
                loading="lazy"
                referrerPolicy="no-referrer-when-downgrade"
              />
            </section>
          )}
        </div>

        {/* FORM */}
        <section className="rounded-[2rem] border border-slate-200 bg-white p-7 shadow-sm md:p-10">
          <div className="flex items-start justify-between gap-5">
            <div>
              <SectionEyebrow color={accent}>Customer support</SectionEyebrow>

              <h2 className="mt-3 text-3xl font-black tracking-tight text-slate-950 md:text-4xl">
                Send a message
              </h2>

              <p className="mt-3 max-w-xl text-sm leading-7 text-slate-500">
                Your enquiry will be associated with {tenant.name}.
              </p>
            </div>

            <div
              className="hidden h-14 w-14 items-center justify-center rounded-2xl text-xl sm:flex"
              style={{
                backgroundColor: `${accent}15`,
                color: primary,
              }}
            >
              ✦
            </div>
          </div>

          {sent ? (
            <div className="mt-9 rounded-2xl border border-emerald-200 bg-emerald-50 p-8 text-center">
              <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-emerald-600 text-xl font-black text-white">
                ✓
              </div>

              <h3 className="mt-5 text-xl font-black text-emerald-950">
                Message received
              </h3>

              <p className="mt-2 text-sm leading-7 text-emerald-800">
                Thank you, {form.name}. Your message has been submitted to{" "}
                {tenant.name}.
              </p>

              <button
                type="button"
                className="mt-6 rounded-xl px-6 py-3.5 text-sm font-black text-white"
                style={{
                  backgroundColor: primary,
                }}
                onClick={() => {
                  setForm(INITIAL_FORM);
                  setSent(false);
                }}
              >
                Send Another Message
              </button>
            </div>
          ) : (
            <form onSubmit={submit} className="mt-9 space-y-5">
              <div className="grid gap-5 sm:grid-cols-2">
                <Field
                  label="Full name"
                  required
                  value={form.name}
                  onChange={(value) =>
                    setForm((current) => ({
                      ...current,
                      name: value,
                    }))
                  }
                />

                <Field
                  label="Phone"
                  required
                  value={form.phone}
                  onChange={(value) =>
                    setForm((current) => ({
                      ...current,
                      phone: value,
                    }))
                  }
                />
              </div>

              <Field
                label="Email"
                type="email"
                value={form.email}
                onChange={(value) =>
                  setForm((current) => ({
                    ...current,
                    email: value,
                  }))
                }
              />

              <div>
                <label className="text-[10px] font-black uppercase tracking-wider text-slate-500">
                  Subject <span className="text-red-500">*</span>
                </label>

                <select
                  required
                  value={form.subject}
                  onChange={(event) =>
                    setForm((current) => ({
                      ...current,
                      subject: event.target.value,
                    }))
                  }
                  className="mt-2 w-full rounded-xl border border-slate-300 bg-white px-4 py-4 text-sm font-semibold text-slate-800 outline-none transition focus:border-slate-900 focus:ring-2 focus:ring-slate-900/5"
                >
                  <option value="">Select a topic</option>
                  <option value="Loan inquiry">Loan inquiry</option>
                  <option value="Application status">Application status</option>
                  <option value="Repayment">Repayment</option>
                  <option value="Complaint or feedback">
                    Complaint or feedback
                  </option>
                  <option value="Partnership">Partnership</option>
                  <option value="Other">Other</option>
                </select>
              </div>

              <div>
                <label className="text-[10px] font-black uppercase tracking-wider text-slate-500">
                  Message <span className="text-red-500">*</span>
                </label>

                <textarea
                  required
                  minLength={10}
                  value={form.message}
                  onChange={(event) =>
                    setForm((current) => ({
                      ...current,
                      message: event.target.value,
                    }))
                  }
                  className="mt-2 min-h-48 w-full resize-y rounded-xl border border-slate-300 px-4 py-4 text-sm leading-7 outline-none transition focus:border-slate-900 focus:ring-2 focus:ring-slate-900/5"
                  placeholder="Tell us how we can help."
                />
              </div>

              <button
                disabled={sending}
                type="submit"
                className="w-full rounded-xl px-5 py-4 text-sm font-black text-white shadow-lg transition hover:-translate-y-0.5 hover:shadow-xl disabled:cursor-not-allowed disabled:opacity-60"
                style={{
                  backgroundColor: primary,
                }}
              >
                {sending ? "Sending..." : "Send Secure Message"}
              </button>

              {error && (
                <div
                  role="alert"
                  className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm leading-6 text-red-700"
                >
                  {error}
                </div>
              )}
            </form>
          )}
        </section>
      </main>
    </div>
  );
}

function Field({
  label,
  required,
  value,
  onChange,
  type = "text",
}: {
  label: string;
  required?: boolean;
  value: string;
  onChange: (value: string) => void;
  type?: string;
}) {
  return (
    <div>
      <label className="text-[10px] font-black uppercase tracking-wider text-slate-500">
        {label} {required && <span className="text-red-500">*</span>}
      </label>

      <input
        required={required}
        type={type}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="mt-2 w-full rounded-xl border border-slate-300 px-4 py-4 text-sm outline-none transition focus:border-slate-900 focus:ring-2 focus:ring-slate-900/5"
      />
    </div>
  );
}
