"use client";

import { FormEvent, useMemo, useState } from "react";
import Link from "next/link";
import { publicApi } from "../../../services/api";
import { useTenant } from "../layout";

export default function ContactPage() {
  const tenant = useTenant();
  if (!tenant) return null;

  const primary = tenant.primaryColor;
  const accent = tenant.accentColor;
  const [form, setForm] = useState({
    name: "",
    email: "",
    phone: "",
    subject: "",
    message: "",
  });
  const [saving, setSaving] = useState(false);
  const [success, setSuccess] = useState("");
  const [error, setError] = useState("");

  const contactCards = useMemo(
    () =>
      [
        tenant.contactPhone
          ? ["Phone", tenant.contactPhone, `tel:${tenant.contactPhone}`]
          : null,
        tenant.contactEmail
          ? ["Email", tenant.contactEmail, `mailto:${tenant.contactEmail}`]
          : null,
        tenant.address ? ["Office", tenant.address, null] : null,
      ].filter(Boolean) as string[][],
    [tenant],
  );

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setSaving(true);
    setSuccess("");
    setError("");
    try {
      await publicApi.submitContact({ ...form, tenantSlug: tenant.slug });
      setSuccess(
        "Thank you. Your message has been delivered to the lender team.",
      );
      setForm({ name: "", email: "", phone: "", subject: "", message: "" });
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "We could not send your message.",
      );
    } finally {
      setSaving(false);
    }
  };

  return (
    <div>
      <section className="bg-slate-50">
        <div className="mx-auto max-w-7xl px-4 py-20 sm:py-24">
          <div className="max-w-3xl">
            <div
              className="text-[11px] font-black uppercase tracking-[0.22em]"
              style={{ color: accent }}
            >
              Contact {tenant.name}
            </div>
            <h1 className="mt-4 text-4xl font-black tracking-tight text-slate-950 sm:text-6xl">
              Let's talk about your financing needs.
            </h1>
            <p className="mt-6 text-base leading-8 text-slate-600">
              Use the secure form below or contact {tenant.name} directly using
              the official details published here.
            </p>
          </div>
        </div>
      </section>

      <section className="mx-auto grid max-w-7xl gap-8 px-4 py-16 lg:grid-cols-[0.72fr_1.28fr] lg:py-20">
        <div>
          <div className="grid gap-3">
            {contactCards.map(([label, value, href]) => (
              <div
                key={label}
                className="rounded-[1.5rem] border border-slate-200 bg-white p-6 shadow-sm"
              >
                <div className="text-[10px] font-black uppercase tracking-[0.18em] text-slate-400">
                  {label}
                </div>
                {href ? (
                  <a
                    href={href}
                    className="mt-2 block break-words text-lg font-black hover:underline"
                    style={{ color: primary }}
                  >
                    {value}
                  </a>
                ) : (
                  <div className="mt-2 text-lg font-black text-slate-900">
                    {value}
                  </div>
                )}
              </div>
            ))}
          </div>
          {tenant.mapUrl && (
            <a
              href={tenant.mapUrl}
              target="_blank"
              rel="noreferrer"
              className="mt-4 block rounded-[1.5rem] border border-slate-200 bg-slate-50 p-6 text-sm font-bold text-slate-700 hover:bg-white"
            >
              Open office location →
            </a>
          )}
          <div className="mt-6 rounded-[1.5rem] border border-amber-100 bg-amber-50 p-6 text-sm leading-7 text-amber-800">
            Never share passwords, PINs, one-time verification codes or full
            banking credentials through the contact form.
          </div>
          <div className="mt-6 flex flex-wrap gap-2">
            <Link
              href="/track"
              className="rounded-xl border border-slate-200 px-4 py-2.5 text-xs font-bold text-slate-700"
            >
              Track an application
            </Link>
            <Link
              href="/services"
              className="rounded-xl px-4 py-2.5 text-xs font-black text-white"
              style={{ backgroundColor: primary }}
            >
              View products
            </Link>
          </div>
        </div>

        <div className="rounded-[2rem] border border-slate-200 bg-white p-6 shadow-[0_24px_80px_rgba(15,23,42,0.08)] sm:p-8">
          <div className="mb-7">
            <div
              className="text-[11px] font-black uppercase tracking-[0.18em]"
              style={{ color: accent }}
            >
              Send a message
            </div>
            <h2 className="mt-2 text-2xl font-black text-slate-950">
              How can we help?
            </h2>
          </div>
          {success && (
            <div className="mb-5 rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-semibold text-emerald-800">
              {success}
            </div>
          )}
          {error && (
            <div className="mb-5 rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-semibold text-red-700">
              {error}
            </div>
          )}
          <form onSubmit={submit} className="space-y-5">
            <div className="grid gap-5 sm:grid-cols-2">
              {[
                ["Name", "name", "text", true],
                ["Email", "email", "email", false],
                ["Phone", "phone", "tel", false],
                ["Subject", "subject", "text", false],
              ].map(([label, key, type, required]) => (
                <label key={String(key)} className="block">
                  <span className="text-[10px] font-black uppercase tracking-[0.16em] text-slate-500">
                    {label}
                    {required ? " *" : ""}
                  </span>
                  <input
                    required={Boolean(required)}
                    type={String(type)}
                    value={String(form[key as keyof typeof form])}
                    onChange={(event) =>
                      setForm((prev) => ({
                        ...prev,
                        [key as keyof typeof prev]: event.target.value,
                      }))
                    }
                    className="mt-2 w-full rounded-2xl border border-slate-200 px-4 py-3 text-sm font-semibold outline-none transition focus:border-slate-400 focus:ring-4 focus:ring-slate-100"
                  />
                </label>
              ))}
            </div>
            <label className="block">
              <span className="text-[10px] font-black uppercase tracking-[0.16em] text-slate-500">
                Message *
              </span>
              <textarea
                required
                rows={7}
                value={form.message}
                onChange={(event) =>
                  setForm((prev) => ({ ...prev, message: event.target.value }))
                }
                className="mt-2 w-full resize-y rounded-2xl border border-slate-200 px-4 py-3 text-sm font-medium leading-6 outline-none transition focus:border-slate-400 focus:ring-4 focus:ring-slate-100"
              />
            </label>
            <div className="flex flex-col gap-3 border-t border-slate-100 pt-5 sm:flex-row sm:items-center sm:justify-between">
              <p className="text-xs leading-5 text-slate-400">
                By submitting, you agree that {tenant.name} may use your details
                to respond to this enquiry.
              </p>
              <button
                disabled={saving}
                className="rounded-2xl px-7 py-3.5 text-sm font-black text-white shadow-sm transition hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-50"
                style={{ backgroundColor: primary }}
              >
                {saving ? "Sending…" : "Send message →"}
              </button>
            </div>
          </form>
        </div>
      </section>
    </div>
  );
}
