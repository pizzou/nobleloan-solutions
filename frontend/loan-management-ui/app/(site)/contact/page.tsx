"use client";
import { useState } from "react";
import { useTenant } from "../layout";
import { TENANT_SLUG } from "../../../lib/tenant";
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
  const primary = tenant.primaryColor || "#0D2C54";
  const gold = tenant.accentColor || "#D4AF37";
  const update = (k: string) => (e: any) =>
    setForm((f) => ({ ...f, [k]: e.target.value }));
  async function submit(e: any) {
    e.preventDefault();
    setSending(true);
    setError("");
    try {
      const base = process.env.NEXT_PUBLIC_API_URL || "/api";
      const r = await fetch(`${base}/public/contact`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ...form, tenantSlug: TENANT_SLUG }),
      });
      const j = await r.json().catch(() => ({}));
      if (!r.ok || j.success === false)
        throw new Error(
          j.error || j.message || "We could not send your message.",
        );
      setSent(true);
    } catch (x: any) {
      setError(x?.message || "Something went wrong.");
    } finally {
      setSending(false);
    }
  }
  return (
    <div className="public-site">
      <section className="relative overflow-hidden bg-[#071B35] py-20 text-white md:py-28">
        <div className="public-grid absolute inset-0 opacity-25" />
        <div className="relative mx-auto max-w-5xl px-5 text-center">
          <div className="public-kicker mx-auto border border-[#D4AF37]/35 bg-white/5 text-[#E7CC78]">
            Client relations
          </div>
          <h1 className="mt-6 font-serif text-5xl font-semibold md:text-6xl">
            Let’s talk about your next financial move.
          </h1>
          <p className="mx-auto mt-6 max-w-2xl text-lg leading-8 text-white/65">
            Questions about a facility, an application, repayment or a
            partnership? Our team is ready to help.
          </p>
        </div>
      </section>
      <section className="public-section bg-white">
        <div className="mx-auto grid max-w-7xl gap-12 px-5 lg:grid-cols-[.8fr_1.2fr]">
          <div>
            <div className="public-eyebrow text-[#B08A27]">Connect with us</div>
            <h2 className="public-title text-[#0B1F3A]">
              A direct line to the team.
            </h2>
            <div className="mt-9 space-y-4">
              {[
                ["Office", tenant.address || "Kigali, Rwanda"],
                ["Phone", tenant.contactPhone || "—"],
                ["Email", tenant.contactEmail || "—"],
                ["Hours", "Monday – Friday, 8:00 – 17:00"],
              ].map(([a, b]) => (
                <div
                  key={a}
                  className="rounded-2xl border border-slate-200 p-5"
                >
                  <div className="text-[10px] font-bold uppercase tracking-[.18em] text-slate-400">
                    {a}
                  </div>
                  <div className="mt-2 text-sm font-semibold text-[#0B1F3A] whitespace-pre-line">
                    {b}
                  </div>
                </div>
              ))}
            </div>
            <div className="mt-7 flex flex-wrap gap-3">
              {tenant.contactPhone && (
                <a
                  href={`tel:${tenant.contactPhone}`}
                  className="public-btn-dark"
                  style={{ backgroundColor: primary }}
                >
                  Call our team <Arrow />
                </a>
              )}
              {tenant.socialMedia?.whatsapp && (
                <a
                  href={tenant.socialMedia.whatsapp}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="public-btn-outline"
                  style={{ borderColor: "#D4AF37", color: primary }}
                >
                  WhatsApp <Arrow />
                </a>
              )}
            </div>
          </div>
          <div className="rounded-[2rem] border border-slate-200 bg-[#F7F8FA] p-6 md:p-9">
            <div className="mb-7">
              <div className="public-eyebrow text-[#B08A27]">
                Secure enquiry
              </div>
              <h2 className="font-serif text-3xl font-semibold text-[#0B1F3A]">
                Send a message
              </h2>
              <p className="mt-2 text-sm text-slate-500">
                Tell us what you need and the right team member can follow up.
              </p>
            </div>
            {sent ? (
              <div className="rounded-3xl border border-[#0D6B5B]/20 bg-[#0D6B5B]/5 p-10 text-center">
                <div className="text-3xl text-[#0D6B5B]">✓</div>
                <h3 className="mt-4 font-serif text-2xl font-semibold text-[#0B1F3A]">
                  Message received
                </h3>
                <p className="mt-2 text-sm text-slate-500">
                  Thank you {form.name}. We will review your enquiry and respond
                  through the contact details provided.
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
                  className="mt-6 text-sm font-bold text-[#0D6B5B]"
                >
                  Send another message
                </button>
              </div>
            ) : (
              <form onSubmit={submit} className="space-y-4">
                <div className="grid gap-4 sm:grid-cols-2">
                  <label className="public-label">
                    Full name
                    <input
                      required
                      value={form.name}
                      onChange={update("name")}
                      className="public-input"
                      placeholder="Your full name"
                    />
                  </label>
                  <label className="public-label">
                    Phone
                    <input
                      required
                      value={form.phone}
                      onChange={update("phone")}
                      className="public-input"
                      placeholder="+250 7XX XXX XXX"
                    />
                  </label>
                </div>
                <label className="public-label">
                  Email
                  <input
                    type="email"
                    value={form.email}
                    onChange={update("email")}
                    className="public-input"
                    placeholder="name@example.com"
                  />
                </label>
                <label className="public-label">
                  Subject
                  <select
                    required
                    value={form.subject}
                    onChange={update("subject")}
                    className="public-input"
                  >
                    <option value="">Select a topic</option>
                    <option>Loan enquiry</option>
                    <option>Application status</option>
                    <option>Repayment support</option>
                    <option>Partnership</option>
                    <option>Feedback or complaint</option>
                    <option>Other</option>
                  </select>
                </label>
                <label className="public-label">
                  Message
                  <textarea
                    required
                    value={form.message}
                    onChange={update("message")}
                    className="public-input min-h-36 resize-y"
                    placeholder="How can we help?"
                  />
                </label>
                {error && (
                  <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
                    {error}
                  </div>
                )}
                <button
                  disabled={sending}
                  className="public-btn-dark w-full justify-center disabled:opacity-50"
                  style={{ backgroundColor: primary }}
                >
                  {sending ? "Sending…" : "Send secure enquiry"} <Arrow />
                </button>
                <p className="text-center text-[11px] leading-5 text-slate-400">
                  Please do not include passwords, card PINs or other sensitive
                  authentication information in this form.
                </p>
              </form>
            )}
          </div>
        </div>
      </section>
      <section className="public-section bg-[#F7F8FA]">
        <div className="mx-auto max-w-5xl px-5 text-center">
          <div className="public-eyebrow text-[#B08A27]">Before you visit</div>
          <h2 className="public-title text-[#0B1F3A]">
            Prefer to start online?
          </h2>
          <p className="mt-4 text-slate-500">
            You can begin a loan application digitally and return to your
            application journey when convenient.
          </p>
          <a
            href="/apply"
            className="public-btn-dark mt-7"
            style={{ backgroundColor: primary }}
          >
            Start application <Arrow />
          </a>
        </div>
      </section>
    </div>
  );
}
