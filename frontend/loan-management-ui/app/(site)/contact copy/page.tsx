"use client";

import Link from "next/link";
import { FormEvent, useState } from "react";
import { publicApi } from "../../../services/api";
import { SITE_CONTENT } from "../../../lib/siteContent";
import { useTenant } from "../layout";

function SocialIcon({ type }: { type: string }) {
  const common = { viewBox: "0 0 24 24", fill: "none", stroke: "currentColor", strokeWidth: 1.8, className: "h-4 w-4" };
  if (type === "facebook") return <svg {...common}><path d="M14 8h3V4h-3c-3.3 0-5 1.8-5 5v3H6v4h3v4h4v-4h3.2l.8-4H13V9c0-.7.3-1 1-1Z" /></svg>;
  if (type === "instagram") return <svg {...common}><rect x="3" y="3" width="18" height="18" rx="5" /><circle cx="12" cy="12" r="4" /><circle cx="17.5" cy="6.5" r=".8" fill="currentColor" stroke="none" /></svg>;
  if (type === "linkedin") return <svg {...common}><path d="M5 8v11M5 5.2v.1M10 19v-6a4 4 0 0 1 8 0v6M10 11V19" /><path d="M10 8v3" /></svg>;
  return <svg {...common}><path d="M20 4 9.4 14.6" /><path d="m20 4-6.8 16-3.8-5.4L4 11l16-7Z" /></svg>;
}

export default function ContactPage() {
  const tenant = useTenant() || SITE_CONTENT;
  const primary = tenant.primaryColor;
  const accent = tenant.accentColor;
  const [form, setForm] = useState({ name: "", email: "", phone: "", subject: "Loan enquiry", message: "" });
  const [status, setStatus] = useState<"idle" | "sending" | "success" | "error">("idle");
  const [error, setError] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setStatus("sending");
    setError("");
    try {
      await publicApi.contact({ tenantSlug: tenant.slug, ...form });
      setForm({ name: "", email: "", phone: "", subject: "Loan enquiry", message: "" });
      setStatus("success");
    } catch (e) {
      setStatus("error");
      setError(e instanceof Error ? e.message : "We could not send your message. Please try again.");
    }
  }

  const social = Object.entries(tenant.socialMedia || {}).filter(([, url]) => Boolean(url));

  return (
    <main className="overflow-hidden bg-[#f5f7fa] text-slate-950">
      <section className="relative overflow-hidden text-white" style={{ background: `linear-gradient(120deg,#061326 0%,${primary} 65%,#173a61 100%)` }}>
        <div className="absolute -right-32 -top-40 h-[480px] w-[480px] rounded-full border border-white/10" />
        <div className="absolute bottom-[-220px] left-[-100px] h-[400px] w-[400px] rounded-full" style={{ background: `${accent}18` }} />
        <div className="relative mx-auto max-w-7xl px-5 py-20 sm:px-8 lg:py-24">
          <div className="text-[10px] font-black uppercase tracking-[.24em]" style={{ color: accent }}>Noble customer support</div>
          <h1 className="mt-4 max-w-4xl text-5xl font-black tracking-[-.06em] sm:text-6xl lg:text-7xl">Talk to the team behind your loan journey.</h1>
          <p className="mt-6 max-w-2xl text-base leading-8 text-white/60 sm:text-lg">Questions about a loan, application, repayment or eligibility? Send us a message and it will be delivered directly into the Noble dashboard for our team to review.</p>
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-5 py-12 sm:px-8 lg:py-20">
        <div className="grid gap-7 lg:grid-cols-[.72fr_1.28fr]">
          <aside className="space-y-3">
            {[tenant.contactPhone && ["Phone", tenant.contactPhone, `tel:${tenant.contactPhone.replace(/\s+/g, "")}`], tenant.contactEmail && ["Email", tenant.contactEmail, `mailto:${tenant.contactEmail}`], tenant.address && ["Office", tenant.address, `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(tenant.address)}`]].filter(Boolean).map((item) => {
              const [label, value, href] = item as string[];
              return <a key={label} href={href} target={href.startsWith("http") ? "_blank" : undefined} rel={href.startsWith("http") ? "noreferrer" : undefined} className="block rounded-3xl border border-slate-200 bg-white p-6 shadow-sm transition hover:-translate-y-1 hover:shadow-xl"><div className="text-[9px] font-black uppercase tracking-[.18em]" style={{ color: accent }}>{label}</div><div className="mt-2 break-words text-sm font-black text-slate-800">{value}</div></a>;
            })}
            <div className="rounded-3xl p-6 text-white" style={{ background: primary }}>
              <div className="text-[9px] font-black uppercase tracking-[.18em]" style={{ color: accent }}>Already applied?</div>
              <h2 className="mt-3 text-xl font-black">Check your application status.</h2>
              <p className="mt-2 text-xs leading-6 text-white/55">Use your application reference and phone number to securely track progress.</p>
              <Link href="/track" className="mt-5 inline-flex rounded-xl px-4 py-3 text-xs font-black" style={{ backgroundColor: accent, color: primary }}>Track application →</Link>
            </div>
            {social.length > 0 && <div className="rounded-3xl border border-slate-200 bg-white p-6"><div className="text-[9px] font-black uppercase tracking-[.18em] text-slate-400">Follow Noble</div><div className="mt-4 flex flex-wrap gap-2">{social.map(([name, url]) => <a key={name} href={url} target="_blank" rel="noreferrer" aria-label={name} className="flex h-10 w-10 items-center justify-center rounded-xl border border-slate-200 text-slate-600 hover:border-slate-300 hover:text-slate-950"><SocialIcon type={name} /></a>)}</div></div>}
          </aside>

          <div className="rounded-[32px] border border-slate-200 bg-white p-6 shadow-[0_25px_80px_rgba(15,27,61,.08)] sm:p-9">
            <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-end"><div><div className="text-[10px] font-black uppercase tracking-[.22em]" style={{ color: accent }}>Send a message</div><h2 className="mt-2 text-3xl font-black tracking-[-.04em]">How can Noble help?</h2></div><span className="rounded-full bg-slate-100 px-3 py-1.5 text-[9px] font-black uppercase tracking-wider text-slate-500">Secure enquiry</span></div>
            <form onSubmit={submit} className="mt-8 space-y-5">
              <div className="grid gap-5 sm:grid-cols-2"><label className="block"><span className="text-xs font-black text-slate-600">Full name</span><input required value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} className="mt-2 w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3.5 text-sm font-semibold outline-none transition focus:border-slate-400 focus:bg-white" /></label><label className="block"><span className="text-xs font-black text-slate-600">Email address</span><input type="email" value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} className="mt-2 w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3.5 text-sm font-semibold outline-none transition focus:border-slate-400 focus:bg-white" /></label></div>
              <div className="grid gap-5 sm:grid-cols-2"><label className="block"><span className="text-xs font-black text-slate-600">Phone number</span><input value={form.phone} onChange={e => setForm({ ...form, phone: e.target.value })} className="mt-2 w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3.5 text-sm font-semibold outline-none transition focus:border-slate-400 focus:bg-white" /></label><label className="block"><span className="text-xs font-black text-slate-600">Subject</span><select value={form.subject} onChange={e => setForm({ ...form, subject: e.target.value })} className="mt-2 w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3.5 text-sm font-semibold outline-none focus:border-slate-400 focus:bg-white"><option>Loan enquiry</option><option>Application support</option><option>Repayment question</option><option>Existing loan</option><option>General enquiry</option></select></label></div>
              <label className="block"><span className="text-xs font-black text-slate-600">Message</span><textarea required minLength={5} rows={7} value={form.message} onChange={e => setForm({ ...form, message: e.target.value })} placeholder="Tell us what you need help with…" className="mt-2 w-full resize-y rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3.5 text-sm font-semibold outline-none transition focus:border-slate-400 focus:bg-white" /></label>
              {status === "success" && <div role="status" className="rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-bold text-emerald-800">Message sent successfully. The Noble team has received your enquiry.</div>}
              {status === "error" && <div role="alert" className="rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-bold text-red-700">{error}</div>}
              <div className="flex flex-col justify-between gap-4 border-t border-slate-100 pt-5 sm:flex-row sm:items-center"><p className="max-w-md text-[10px] leading-5 text-slate-400">Do not send passwords, OTP codes, card PINs or other confidential credentials through this form.</p><button disabled={status === "sending"} type="submit" className="rounded-2xl px-7 py-4 text-sm font-black text-white shadow-lg transition hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-60" style={{ backgroundColor: primary }}>{status === "sending" ? "Sending…" : "Send message →"}</button></div>
            </form>
          </div>
        </div>
      </section>
    </main>
  );
}
