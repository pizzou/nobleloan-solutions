"use client";
import Link from "next/link";
import { useTenant } from "../layout";

export default function PrivacyPage() {
  const tenant = useTenant();
  if (!tenant) return null;
  const primary = tenant.primaryColor || "#0B1F3A";
  const accent = tenant.accentColor || "#D4AF37";
  const sections = [
    [
      "1. What we collect",
      `When you apply for a loan or use our borrower portal, we collect information needed to verify and service your account, including identity, contact, employment, income, loan, document, payment and communication information.`,
    ],
    [
      "2. How we use it",
      `We use information to verify identity, assess applications, service approved loans, communicate with clients, meet legal and regulatory obligations and improve our services.`,
    ],
    [
      "3. How we protect it",
      `Sensitive information is protected using appropriate technical and organizational controls. Access is limited to staff who need it for legitimate business purposes and access is logged.`,
    ],
    [
      "4. Who we share it with",
      `We may share information with payment providers, credit reference bureaus where applicable, regulators, law enforcement where legally required, and other service providers necessary to operate the financial service. We do not sell personal information.`,
    ],
    [
      "5. Retention",
      `Personal information is retained for the period required by applicable financial, tax, regulatory and legal recordkeeping obligations.`,
    ],
    [
      "6. Your rights",
      `Depending on applicable law, you may have rights to access, correct or ask about the use of personal information. Contact us using the details published on this website.`,
    ],
    [
      "7. Contact",
      `Questions about this policy can be sent to ${tenant.contactEmail || "the contact email published on this website"}${tenant.contactPhone ? ` or ${tenant.contactPhone}` : ""}.`,
    ],
  ];
  return (
    <main className="bg-slate-50 py-12 sm:py-16">
      <div className="mx-auto max-w-4xl px-5 sm:px-8">
        <div className="rounded-[30px] border border-slate-200 bg-white p-7 shadow-[0_20px_70px_rgba(15,23,42,.06)] sm:p-10 lg:p-14">
          <div className="flex items-center gap-3">
            <div
              className="h-2 w-2 rounded-full"
              style={{ backgroundColor: accent }}
            />
            <span
              className="text-[10px] font-black uppercase tracking-[.22em]"
              style={{ color: primary }}
            >
              Legal & privacy
            </span>
          </div>
          <h1 className="mt-5 text-4xl font-black tracking-[-.045em] sm:text-5xl">
            Privacy Policy
          </h1>
          <p className="mt-3 text-xs text-slate-400">
            Last updated: 8 September 2026 · {tenant.name}
          </p>
          <div className="mt-10 rounded-2xl border border-slate-200 bg-slate-50 p-5 text-sm leading-6 text-slate-600">
            <strong className="text-slate-900">Important:</strong> This policy
            should be reviewed against your actual licensing, regulatory,
            data-processing and retention obligations before publication to
            customers.
          </div>
          <div className="mt-10 space-y-9">
            {sections.map(([title, text]) => (
              <section key={title}>
                <h2 className="text-lg font-black text-slate-900">{title}</h2>
                <p className="mt-3 text-sm leading-7 text-slate-600">{text}</p>
              </section>
            ))}
          </div>
          <div className="mt-10 border-t border-slate-100 pt-6 text-xs text-slate-400">
            See also{" "}
            <Link
              href="/terms"
              className="font-bold underline"
              style={{ color: primary }}
            >
              Terms & Conditions
            </Link>
            .
          </div>
        </div>
      </div>
    </main>
  );
}
