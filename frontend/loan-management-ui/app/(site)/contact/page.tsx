"use client";

import { useState } from "react";
import { useTenant } from "../layout";
import { publicApi } from "../../../services/api";

const INITIAL_FORM = {
  name: "",
  email: "",
  phone: "",
  subject: "",
  message: "",
};

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
      await publicApi.submitContact({ ...form });
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
    <div>
      <section
        className="text-white"
        style={{
          background: `linear-gradient(135deg, ${primary}, ${primary}D9)`,
        }}
      >
        <div className="mx-auto max-w-4xl px-4 py-20 text-center md:py-24">
          <div className="text-xs font-black uppercase tracking-[0.2em] text-white/60">
            Contact
          </div>
          <h1 className="mt-4 text-4xl font-black md:text-6xl">
            Talk to {tenant.name}
          </h1>
          <p className="mx-auto mt-5 max-w-2xl text-lg leading-8 text-white/75">
            Use the contact details published by the lender or send a message
            through the secure form below.
          </p>
        </div>
      </section>

      <section className="mx-auto grid max-w-7xl gap-10 px-4 py-20 lg:grid-cols-[0.9fr_1.1fr]">
        <div>
          <h2 className="text-2xl font-black text-slate-950">
            Contact Information
          </h2>
          <div className="mt-8 space-y-5">
            {tenant.address && (
              <div className="rounded-2xl border border-slate-200 bg-white p-5">
                <div className="text-xs font-bold uppercase tracking-wider text-slate-400">
                  Office
                </div>
                <div className="mt-2 font-semibold text-slate-900">
                  {tenant.address}
                </div>
              </div>
            )}
            {tenant.contactPhone && (
              <a
                href={`tel:${tenant.contactPhone}`}
                className="block rounded-2xl border border-slate-200 bg-white p-5 hover:shadow-md"
              >
                <div className="text-xs font-bold uppercase tracking-wider text-slate-400">
                  Phone
                </div>
                <div className="mt-2 font-semibold text-slate-900">
                  {tenant.contactPhone}
                </div>
              </a>
            )}
            {tenant.contactEmail && (
              <a
                href={`mailto:${tenant.contactEmail}`}
                className="block rounded-2xl border border-slate-200 bg-white p-5 hover:shadow-md"
              >
                <div className="text-xs font-bold uppercase tracking-wider text-slate-400">
                  Email
                </div>
                <div className="mt-2 font-semibold text-slate-900">
                  {tenant.contactEmail}
                </div>
              </a>
            )}
          </div>

          {Object.values(socials).some(Boolean) && (
            <div className="mt-8">
              <div
                className="text-xs font-black uppercase tracking-[0.18em]"
                style={{ color: accent }}
              >
                Social
              </div>
              <div className="mt-4 flex flex-wrap gap-3">
                {Object.entries(socials)
                  .filter(([, value]) => Boolean(value))
                  .map(([label, value]) => (
                    <a
                      key={label}
                      href={value as string}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="rounded-full border px-4 py-2 text-xs font-bold capitalize"
                      style={{ borderColor: primary, color: primary }}
                    >
                      {label}
                    </a>
                  ))}
              </div>
            </div>
          )}

          {tenant.mapUrl && (
            <div className="mt-8 overflow-hidden rounded-2xl border border-slate-200">
              <iframe
                title="Office location"
                src={tenant.mapUrl}
                className="h-72 w-full border-0"
                loading="lazy"
                referrerPolicy="no-referrer-when-downgrade"
              />
            </div>
          )}
        </div>

        <div className="rounded-3xl border border-slate-200 bg-white p-7 shadow-sm md:p-9">
          <h2 className="text-2xl font-black text-slate-950">Send a message</h2>
          {sent ? (
            <div className="mt-8 rounded-2xl border border-emerald-200 bg-emerald-50 p-8 text-center">
              <div className="text-3xl">✓</div>
              <h3 className="mt-3 text-xl font-black text-emerald-900">
                Message received
              </h3>
              <p className="mt-2 text-sm leading-6 text-emerald-800">
                Thank you, {form.name}. Your message has been submitted to{" "}
                {tenant.name}.
              </p>
              <button
                type="button"
                className="mt-5 rounded-full px-6 py-3 text-sm font-bold text-white"
                style={{ backgroundColor: primary }}
                onClick={() => {
                  setForm(INITIAL_FORM);
                  setSent(false);
                }}
              >
                Send another message
              </button>
            </div>
          ) : (
            <form onSubmit={submit} className="mt-7 space-y-5">
              <div className="grid gap-5 sm:grid-cols-2">
                <Field
                  label="Full name"
                  required
                  value={form.name}
                  onChange={(v) => setForm((f) => ({ ...f, name: v }))}
                />
                <Field
                  label="Phone"
                  required
                  value={form.phone}
                  onChange={(v) => setForm((f) => ({ ...f, phone: v }))}
                />
              </div>
              <Field
                label="Email"
                type="email"
                value={form.email}
                onChange={(v) => setForm((f) => ({ ...f, email: v }))}
              />
              <div>
                <label className="text-xs font-bold uppercase tracking-wider text-slate-500">
                  Subject <span className="text-red-500">*</span>
                </label>
                <select
                  required
                  value={form.subject}
                  onChange={(e) =>
                    setForm((f) => ({ ...f, subject: e.target.value }))
                  }
                  className="mt-1.5 w-full rounded-xl border border-slate-300 px-4 py-3 text-sm outline-none"
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
                <label className="text-xs font-bold uppercase tracking-wider text-slate-500">
                  Message <span className="text-red-500">*</span>
                </label>
                <textarea
                  required
                  minLength={10}
                  value={form.message}
                  onChange={(e) =>
                    setForm((f) => ({ ...f, message: e.target.value }))
                  }
                  className="mt-1.5 min-h-40 w-full rounded-xl border border-slate-300 px-4 py-3 text-sm outline-none"
                  placeholder="How can we help?"
                />
              </div>
              <button
                disabled={sending}
                type="submit"
                className="w-full rounded-xl px-5 py-3.5 text-sm font-black text-white disabled:opacity-60"
                style={{ backgroundColor: primary }}
              >
                {sending ? "Sending…" : "Send Message"}
              </button>
              {error && (
                <div
                  role="alert"
                  className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"
                >
                  {error}
                </div>
              )}
            </form>
          )}
        </div>
      </section>
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
      <label className="text-xs font-bold uppercase tracking-wider text-slate-500">
        {label} {required && <span className="text-red-500">*</span>}
      </label>
      <input
        required={required}
        type={type}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="mt-1.5 w-full rounded-xl border border-slate-300 px-4 py-3 text-sm outline-none"
      />
    </div>
  );
}
