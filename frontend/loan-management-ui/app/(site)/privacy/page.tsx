"use client";

import Link from "next/link";
import { useTenant } from "../layout";

export default function PrivacyPage() {
  const tenant = useTenant();
  if (!tenant) return null;

  return (
    <div className="mx-auto max-w-4xl px-4 py-16">
      <h1 className="text-4xl font-black text-slate-950">Privacy Policy</h1>
      <p className="mt-3 text-sm text-slate-500">
        {tenant.name} • {tenant.country || "Applicable jurisdiction"}
      </p>
      <div className="mt-10 space-y-8 text-sm leading-7 text-slate-700">
        <section>
          <h2 className="text-xl font-black text-slate-950">
            1. Information we collect
          </h2>
          <p className="mt-3">
            When you use this website or apply for a loan, the lender may
            collect the information required to identify you, assess your
            application, service your loan, process payments, communicate with
            you, and meet applicable legal and regulatory obligations.
          </p>
        </section>
        <section>
          <h2 className="text-xl font-black text-slate-950">
            2. How information is used
          </h2>
          <p className="mt-3">
            Information may be used for identity verification, credit
            assessment, application processing, loan servicing, repayment
            collection, customer support, fraud prevention, compliance, audit,
            reporting, and lawful business administration.
          </p>
        </section>
        <section>
          <h2 className="text-xl font-black text-slate-950">
            3. Sharing and service providers
          </h2>
          <p className="mt-3">
            Information may be shared where necessary with payment providers,
            credit reference services, document or communication providers,
            regulators, auditors, professional advisers, or authorities where
            legally required.
          </p>
        </section>
        <section>
          <h2 className="text-xl font-black text-slate-950">4. Security</h2>
          <p className="mt-3">
            The lender applies administrative, technical, and organisational
            safeguards appropriate to the information it processes. Access is
            limited according to role and business need.
          </p>
        </section>
        <section>
          <h2 className="text-xl font-black text-slate-950">
            5. Retention and rights
          </h2>
          <p className="mt-3">
            Information may be retained for as long as required for legitimate
            business, contractual, regulatory, accounting, dispute, or legal
            purposes. Your rights depend on applicable law and may include
            access, correction, and other privacy rights.
          </p>
        </section>
        <section>
          <h2 className="text-xl font-black text-slate-950">6. Contact</h2>
          <p className="mt-3">
            For privacy questions, contact{" "}
            {tenant.contactEmail || tenant.contactPhone || tenant.name}.
          </p>
        </section>
        <p className="border-t border-slate-200 pt-6 text-xs text-slate-400">
          This page describes general privacy practices and should be reviewed
          by the lender's legal and compliance team for the exact jurisdiction
          and notices that apply.
        </p>
      </div>
      <div className="mt-8">
        <Link href="/terms" className="font-bold underline">
          Read Terms & Conditions →
        </Link>
      </div>
    </div>
  );
}
