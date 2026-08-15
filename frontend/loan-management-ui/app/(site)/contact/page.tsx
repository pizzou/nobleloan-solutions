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
      await publicApi.submitContact({ ...form, tenantSlug: tenant.slug });
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
    <div className="bg-slate-50 pb-20">
      <section
        className="relative overflow-hidden text-white"
        style={{ background: `linear-gradient(135deg, ${primary}, #071427)` }}
      >
        <div className="absolute -right-24 -top-24 h-80 w-80 rounded-full bg-white/10 blur-3xl" />
        <div className="relative mx-auto max-w-7xl px-4 py-20 md:py-24">
          <div className="max-w-3xl">
            <div className="text-[11px] font-black uppercase tracking-[0.24em] text-white/45">
              Contact {tenant.name}
            </div>
            <h1 className="mt-4 text-4xl font-black tracking-tight md:text-6xl">
              Talk to the right people.
            </h1>
            <p className="mt-5 max-w-2xl text-lg leading-8 text-white/65">
              Use the verified contact details below or send a message directly
              through the lender's secure public contact form.
            </p>
          </div>
        </div>
      </section>

      <main className="mx-auto grid max-w-7xl gap-8 px-4 pt-10 lg:grid-cols-[0.82fr_1.18fr]">
        <div className="space-y-5">
          <div className="rounded-[2rem] border border-slate-200 bg-white p-7 shadow-sm">
            <div
              className="text-[11px] font-black uppercase tracking-[0.2em]"
              style={{ color: accent }}
            >
              Verified contact details
            </div>
            <div className="mt-6 space-y-4">
              {tenant.address && (
                <div className="rounded-2xl bg-slate-50 p-4">
                  <div className="text-[10px] font-black uppercase tracking-wider text-slate-400">
                    Office
                  </div>
                  <div className="mt-2 text-sm font-bold text-slate-900">
                    {tenant.address}
                  </div>
                </div>
              )}
              {tenant.contactPhone && (
                <a
                  href={`tel:${tenant.contactPhone}`}
                  className="block rounded-2xl bg-slate-50 p-4 hover:bg-slate-100"
                >
                  <div className="text-[10px] font-black uppercase tracking-wider text-slate-400">
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
                  className="block rounded-2xl bg-slate-50 p-4 hover:bg-slate-100"
                >
                  <div className="text-[10px] font-black uppercase tracking-wider text-slate-400">
                    Email
                  </div>
                  <div className="mt-2 text-sm font-bold text-slate-900">
                    {tenant.contactEmail}
                  </div>
                </a>
              )}
              {tenant.registrationNumber && (
                <div className="rounded-2xl bg-slate-50 p-4">
                  <div className="text-[10px] font-black uppercase tracking-wider text-slate-400">
                    Registration
                  </div>
                  <div className="mt-2 text-sm font-bold text-slate-900">
                    {tenant.registrationNumber}
                  </div>
                </div>
              )}
            </div>
          </div>

          {Object.values(socials).some(Boolean) && (
            <div className="rounded-[2rem] border border-slate-200 bg-white p-7 shadow-sm">
              <div
                className="text-[11px] font-black uppercase tracking-[0.2em]"
                style={{ color: accent }}
              >
                Connect with us
              </div>
              <div className="mt-5 flex flex-wrap gap-2">
                {Object.entries(socials)
                  .filter(([, value]) => Boolean(value))
                  .map(([label, value]) => (
                    <a
                      key={label}
                      href={value as string}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="rounded-full border px-4 py-2 text-xs font-black capitalize"
                      style={{ borderColor: primary, color: primary }}
                    >
                      {label}
                    </a>
                  ))}
              </div>
            </div>
          )}

          {tenant.mapUrl && (
            <div className="overflow-hidden rounded-[2rem] border border-slate-200 bg-white shadow-sm">
              <div className="border-b border-slate-100 px-6 py-4 text-[11px] font-black uppercase tracking-[0.2em] text-slate-400">
                Office location
              </div>
              <iframe
                title={`${tenant.name} office location`}
                src={tenant.mapUrl}
                className="h-72 w-full border-0"
                loading="lazy"
                referrerPolicy="no-referrer-when-downgrade"
              />
            </div>
          )}
        </div>

        <div className="rounded-[2rem] border border-slate-200 bg-white p-7 shadow-sm md:p-9">
          <div className="flex items-start justify-between gap-5">
            <div>
              <div
                className="text-[11px] font-black uppercase tracking-[0.2em]"
                style={{ color: accent }}
              >
                Customer support
              </div>
              <h2 className="mt-2 text-3xl font-black tracking-tight text-slate-950">
                Send a message
              </h2>
              <p className="mt-2 text-sm leading-6 text-slate-500">
                Your message will be associated with {tenant.name}.
              </p>
            </div>
            <div
              className="hidden h-12 w-12 items-center justify-center rounded-2xl sm:flex"
              style={{ backgroundColor: `${accent}18`, color: primary }}
            >
              ✦
            </div>
          </div>

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
                className="mt-5 rounded-2xl px-6 py-3 text-sm font-black text-white"
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
                <label className="text-xs font-black uppercase tracking-wider text-slate-500">
                  Subject <span className="text-red-500">*</span>
                </label>
                <select
                  required
                  value={form.subject}
                  onChange={(e) =>
                    setForm((f) => ({ ...f, subject: e.target.value }))
                  }
                  className="mt-2 w-full rounded-2xl border border-slate-300 px-4 py-3.5 text-sm font-semibold outline-none focus:border-slate-900"
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
                <label className="text-xs font-black uppercase tracking-wider text-slate-500">
                  Message <span className="text-red-500">*</span>
                </label>
                <textarea
                  required
                  minLength={10}
                  value={form.message}
                  onChange={(e) =>
                    setForm((f) => ({ ...f, message: e.target.value }))
                  }
                  className="mt-2 min-h-44 w-full rounded-2xl border border-slate-300 px-4 py-3.5 text-sm outline-none focus:border-slate-900"
                  placeholder="How can we help?"
                />
              </div>
              <button
                disabled={sending}
                type="submit"
                className="w-full rounded-2xl px-5 py-4 text-sm font-black text-white shadow-lg disabled:opacity-60"
                style={{ backgroundColor: primary }}
              >
                {sending ? "Sending…" : "Send secure message"}
              </button>
              {error && (
                <div
                  role="alert"
                  className="rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"
                >
                  {error}
                </div>
              )}
            </form>
          )}
        </div>
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
      <label className="text-xs font-black uppercase tracking-wider text-slate-500">
        {label} {required && <span className="text-red-500">*</span>}
      </label>
      <input
        required={required}
        type={type}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="mt-2 w-full rounded-2xl border border-slate-300 px-4 py-3.5 text-sm outline-none focus:border-slate-900"
      />
    </div>
  );
}
