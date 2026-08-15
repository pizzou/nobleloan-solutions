"use client";

import Link from "next/link";
import { useTenant } from "../layout";

export default function TermsPage() {
  const tenant = useTenant();
  if (!tenant) return null;

  return (
    <div className="mx-auto max-w-4xl px-4 py-16">
      <h1 className="text-4xl font-black text-slate-950">Terms & Conditions</h1>
      <p className="mt-3 text-sm text-slate-500">
        {tenant.name} • {tenant.country || "Applicable jurisdiction"}
      </p>
      <div className="mt-10 space-y-8 text-sm leading-7 text-slate-700">
        <section>
          <h2 className="text-xl font-black text-slate-950">1. Website use</h2>
          <p className="mt-3">
            By using this website you agree to use it lawfully and to provide
            accurate information when submitting forms or applications.
          </p>
        </section>
        <section>
          <h2 className="text-xl font-black text-slate-950">
            2. Loan applications
          </h2>
          <p className="mt-3">
            Submitting an application is a request for credit consideration. It
            is not a promise of approval, disbursement, or a particular loan
            amount. The lender applies its eligibility, verification, risk, and
            credit policies before making a decision.
          </p>
        </section>
        <section>
          <h2 className="text-xl font-black text-slate-950">
            3. Product terms and charges
          </h2>
          <p className="mt-3">
            Published product information is provided for guidance. The final
            approved agreement and repayment schedule control the amount, rate,
            fees, dates, and other contractual terms that apply to a borrower.
          </p>
        </section>
        <section>
          <h2 className="text-xl font-black text-slate-950">
            4. Repayment and default
          </h2>
          <p className="mt-3">
            Borrowers are responsible for making payments according to the
            agreed schedule. Missed or late payments may result in charges,
            collections activity, restructuring, credit reporting, or other
            lawful consequences under the loan agreement.
          </p>
        </section>
        <section>
          <h2 className="text-xl font-black text-slate-950">
            5. Communications
          </h2>
          <p className="mt-3">
            The lender may communicate with applicants and borrowers through the
            contact methods supplied during the application or account
            lifecycle, subject to applicable consent and communication laws.
          </p>
        </section>
        <section>
          <h2 className="text-xl font-black text-slate-950">6. Privacy</h2>
          <p className="mt-3">
            Use of this website is also subject to the{" "}
            <Link href="/privacy" className="font-bold underline">
              Privacy Policy
            </Link>
            .
          </p>
        </section>
        <section>
          <h2 className="text-xl font-black text-slate-950">
            7. Governing law and complaints
          </h2>
          <p className="mt-3">
            The governing law, regulator, dispute process, and complaint
            mechanisms applicable to {tenant.name} depend on the jurisdiction
            and licensing framework of the organisation. Contact the lender
            directly using its published contact details for the current
            procedure.
          </p>
        </section>
        <p className="border-t border-slate-200 pt-6 text-xs text-slate-400">
          These website terms are general and should be reviewed and approved by
          the lender's legal and compliance team before publication as a
          contractual notice.
        </p>
      </div>
    </div>
  );
}
