"use client";

import { useState } from "react";
import { useTenant } from "../layout";
import { TENANT_SLUG } from "../../../lib/tenant";

export default function ContactPage() {
  const tenant = useTenant();
  const [form, setForm] = useState({
    name: "",
    email: "",
    phone: "",
    subject: "",
    message: "",
  });
  const [state, setState] = useState<"idle" | "sending" | "sent" | "error">(
    "idle",
  );
  const [error, setError] = useState("");

  if (!tenant) return null;
  const primary = tenant.primaryColor;
  const accent = tenant.accentColor;

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setState("sending");
    setError("");
    try {
      const base =
        process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api";
      const response = await fetch(`${base}/public/contact`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ...form, tenantSlug: TENANT_SLUG }),
      });
      const json = await response.json();
      if (!response.ok || json?.success === false)
        throw new Error(
          json?.error || json?.message || "Unable to send your message.",
        );
      setState("sent");
      setForm({ name: "", email: "", phone: "", subject: "", message: "" });
    } catch (err) {
      setState("error");
      setError(
        err instanceof Error ? err.message : "Unable to send your message.",
      );
    }
  }

  return (
    <div>
      <section className="bg-[#06172D] text-white">
        <div className="mx-auto max-w-7xl px-4 py-16 sm:py-20">
          <div className="max-w-3xl">
            <div
              className="text-[11px] font-bold uppercase tracking-[0.2em]"
              style={{ color: accent }}
            >
              Contact
            </div>
            <h1 className="mt-3 text-4xl font-black tracking-tight sm:text-5xl">
              Let’s talk about what you need.
            </h1>
            <p className="mt-5 text-base leading-8 text-white/65">
              Questions about a loan product, an application, repayment or a
              partnership? Reach the team using the channels below.
            </p>
          </div>
        </div>
      </section>

      <section className="mx-auto grid max-w-7xl gap-6 px-4 py-12 sm:py-16 lg:grid-cols-[.86fr_1.14fr]">
        <div className="space-y-4">
          <div className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
            <div
              className="text-[11px] font-bold uppercase tracking-[0.18em]"
              style={{ color: accent }}
            >
              Direct contact
            </div>
            <div className="mt-5 space-y-5">
              <div>
                <div className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
                  Phone
                </div>
                <a
                  className="mt-1 block text-sm font-black text-slate-900"
                  href={`tel:${tenant.contactPhone || ""}`}
                >
                  {tenant.contactPhone || "Not published"}
                </a>
              </div>
              <div>
                <div className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
                  Email
                </div>
                <a
                  className="mt-1 block text-sm font-black text-slate-900"
                  href={`mailto:${tenant.contactEmail || ""}`}
                >
                  {tenant.contactEmail || "Not published"}
                </a>
              </div>
              <div>
                <div className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
                  Office
                </div>
                <div className="mt-1 text-sm leading-6 text-slate-600">
                  {tenant.address || "Contact us for office information."}
                </div>
              </div>
            </div>
          </div>
          <div className="rounded-3xl border border-slate-200 bg-slate-50 p-6">
            <div
              className="text-[11px] font-bold uppercase tracking-[0.18em]"
              style={{ color: primary }}
            >
              Before you contact us
            </div>
            <ul className="mt-4 space-y-3 text-sm leading-6 text-slate-600">
              <li>
                Have your application reference ready when asking about an
                existing application.
              </li>
              <li>Never send a full card number, PIN or password by email.</li>
              <li>
                For loan enquiries, tell us the amount and purpose so we can
                guide you faster.
              </li>
            </ul>
          </div>
        </div>

        <div className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm sm:p-8">
          <div className="mb-6">
            <div
              className="text-[11px] font-bold uppercase tracking-[0.18em]"
              style={{ color: accent }}
            >
              Message the team
            </div>
            <h2 className="mt-2 text-2xl font-black text-slate-950">
              How can we help?
            </h2>
          </div>
          {state === "sent" ? (
            <div className="rounded-3xl border border-emerald-200 bg-emerald-50 p-8 text-center">
              <div className="text-2xl">✓</div>
              <div className="mt-3 text-lg font-black text-emerald-900">
                Message received
              </div>
              <p className="mt-2 text-sm leading-6 text-emerald-800">
                Your message has been submitted to the organization’s support
                workflow. We’ll respond using the contact details you provided.
              </p>
              <button
                type="button"
                onClick={() => setState("idle")}
                className="mt-5 rounded-xl bg-white px-5 py-3 text-sm font-black text-emerald-800"
              >
                Send another message
              </button>
            </div>
          ) : (
            <form className="space-y-5" onSubmit={submit}>
              <div className="grid gap-5 sm:grid-cols-2">
                <label className="text-xs font-bold text-slate-500">
                  Full name
                  <input
                    required
                    value={form.name}
                    onChange={(e) =>
                      setForm((v) => ({ ...v, name: e.target.value }))
                    }
                    className="mt-2 w-full rounded-xl border border-slate-200 px-4 py-3 text-sm outline-none focus:border-slate-400"
                  />
                </label>
                <label className="text-xs font-bold text-slate-500">
                  Phone
                  <input
                    required
                    value={form.phone}
                    onChange={(e) =>
                      setForm((v) => ({ ...v, phone: e.target.value }))
                    }
                    className="mt-2 w-full rounded-xl border border-slate-200 px-4 py-3 text-sm outline-none focus:border-slate-400"
                  />
                </label>
              </div>
              <label className="block text-xs font-bold text-slate-500">
                Email
                <input
                  type="email"
                  value={form.email}
                  onChange={(e) =>
                    setForm((v) => ({ ...v, email: e.target.value }))
                  }
                  className="mt-2 w-full rounded-xl border border-slate-200 px-4 py-3 text-sm outline-none focus:border-slate-400"
                />
              </label>
              <label className="block text-xs font-bold text-slate-500">
                Subject
                <select
                  required
                  value={form.subject}
                  onChange={(e) =>
                    setForm((v) => ({ ...v, subject: e.target.value }))
                  }
                  className="mt-2 w-full rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm outline-none"
                >
                  <option value="">Select a topic</option>
                  <option>Loan enquiry</option>
                  <option>Application status</option>
                  <option>Repayment question</option>
                  <option>Complaint or feedback</option>
                  <option>Partnership</option>
                  <option>Other</option>
                </select>
              </label>
              <label className="block text-xs font-bold text-slate-500">
                Message
                <textarea
                  required
                  minLength={10}
                  value={form.message}
                  onChange={(e) =>
                    setForm((v) => ({ ...v, message: e.target.value }))
                  }
                  rows={6}
                  className="mt-2 w-full resize-y rounded-xl border border-slate-200 px-4 py-3 text-sm outline-none focus:border-slate-400"
                />
              </label>
              {state === "error" && (
                <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
                  {error}
                </div>
              )}
              <button
                type="submit"
                disabled={state === "sending"}
                className="w-full rounded-xl px-5 py-3.5 text-sm font-black text-white disabled:opacity-60"
                style={{ backgroundColor: primary }}
              >
                {state === "sending" ? "Sending…" : "Send message"}
              </button>
              <p className="text-[11px] leading-5 text-slate-400">
                For your security, do not include passwords, card security codes
                or other authentication secrets in this form.
              </p>
            </form>
          )}
        </div>
      </section>
    </div>
  );
}
