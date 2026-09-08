"use client";
import Link from "next/link";
import { useTenant } from "../layout";

export default function TermsPage() {
  const tenant = useTenant();
  if (!tenant) return null;
  const primary = tenant.primaryColor || "#0B1F3A";
  const accent = tenant.accentColor || "#D4AF37";
  const sections = [
    [
      "1. Acceptance",
      `By submitting an application through this website, you confirm that you meet the eligibility requirements communicated by ${tenant.name}, that the information you provide is accurate and that you agree to these terms and the Privacy Policy.`,
    ],
    [
      "2. Nature of an application",
      `Submitting an application is a request to be considered for credit. It is not an offer, approval or guarantee of credit. ${tenant.name} may approve, decline or request additional information after its assessment.`,
    ],
    [
      "3. Verification",
      `You authorize ${tenant.name} to verify information supplied in connection with an application, including identity, employment, income and documents, subject to applicable law.`,
    ],
    [
      "4. Interest, fees and repayment",
      `If approved, the individual loan agreement will state the principal, interest, repayment schedule, applicable fees and any consequences of late or missed payments. The signed loan agreement takes precedence over website estimates.`,
    ],
    [
      "5. Default and collections",
      `Failure to repay according to the agreed schedule may result in charges and collection or credit-reporting consequences permitted by the loan agreement and applicable law.`,
    ],
    [
      "6. Communications",
      `By applying, you consent to receive service communications concerning your application or loan through the contact channels you provide, subject to applicable law.`,
    ],
    [
      "7. Changes",
      `${tenant.name} may update these website terms from time to time. Material changes should be reviewed before continued use of the relevant service.`,
    ],
    [
      "8. Governing law",
      `The governing law, regulator and dispute-resolution arrangements applicable to a specific financial product are those stated in the applicable customer agreement and required by law.`,
    ],
    [
      "9. Contact",
      `Questions about these terms can be sent to ${tenant.contactEmail || "the contact email published on this website"}${tenant.contactPhone ? ` or ${tenant.contactPhone}` : ""}.`,
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
              Legal & terms
            </span>
          </div>
          <h1 className="mt-5 text-4xl font-black tracking-[-.045em] sm:text-5xl">
            Terms & Conditions
          </h1>
          <p className="mt-3 text-xs text-slate-400">
            Last updated: 8 September 2026 · {tenant.name}
          </p>
          <div className="mt-10 rounded-2xl border border-amber-200 bg-amber-50 p-5 text-sm leading-6 text-amber-900">
            <strong>Important:</strong> This website copy should be reviewed by
            qualified local counsel and aligned with your actual licensing and
            regulatory obligations before customer publication.
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
            Read our{" "}
            <Link
              href="/privacy"
              className="font-bold underline"
              style={{ color: primary }}
            >
              Privacy Policy
            </Link>
            .
          </div>
        </div>
      </div>
    </main>
  );
}
