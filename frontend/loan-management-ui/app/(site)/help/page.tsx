import ProcedureGuide, { type Procedure } from "@/components/ProcedureGuide";

const borrowerGuides: Procedure[] = [
  {
    title: "Choose a loan", area: "Before applying", path: "/services",
    keywords: "products personal business vehicle salary agriculture eligibility requirements",
    steps: [
      "Compare the products and open the one that matches what you need to finance.",
      "Read its eligibility requirements, terms, and document requirements.",
      "Use the calculators for an estimate and verify the final terms with Noble before accepting an offer.",
    ],
  },
  {
    title: "Estimate repayments", area: "Before applying", path: "/calculators",
    keywords: "loan calculator interest monthly installments payment affordability",
    steps: ["Choose a calculator and enter the loan amount, rate, and term it asks for.", "Review the estimated payment against your budget. The final agreement sets your actual terms."],
  },
  {
    title: "Submit a loan application", area: "Applications", path: "/apply",
    keywords: "apply new application register upload documents reference",
    steps: ["Start Apply and choose the loan product.", "Fill in the requested information and upload the documents required by the form.", "Review your details before submitting. Save the application reference shown on confirmation."],
  },
  {
    title: "Track an application or view loan details", area: "Applications", path: "/track",
    keywords: "reference number registered phone status application payment schedule document",
    steps: ["Enter your application reference and the phone number you registered.", "Check the status and any available next steps. The page may also show the repayment schedule and documents for your loan.", "If the reference cannot be found, recheck it against your confirmation. Contact Noble if you no longer have it."],
  },
  {
    title: "Review or sign a loan agreement", area: "Applications", path: "/track",
    keywords: "sign contract download agreement link verification terms",
    steps: ["Open the agreement through the link Noble sent you or the action shown in your tracked application.", "Read the loan terms and follow the verification and signing steps on screen.", "Contact Noble if the terms are unclear or the signing link has expired."],
  },
  {
    title: "Make a repayment online", area: "Existing loans", path: "/track",
    keywords: "pay securely payment mobile money installment provider",
    steps: ["Open Track with your application reference and registered phone number.", "If Pay securely is available, open it, verify the loan and amount, and choose a payment method.", "Follow the provider instructions. Keep the reference and check the tracked loan before paying again if confirmation is delayed."],
  },
  {
    title: "Upload a document requested for your application", area: "Applications", path: "/track",
    keywords: "missing file document identity attachment upload",
    steps: ["Open your application in Track using your reference and registered phone number.", "Find the requested document and use Upload document when the action is available.", "Check the displayed result. Contact Noble if you cannot upload the requested file."],
  },
  {
    title: "Get help with a repayment", area: "Existing loans", path: "/contact",
    keywords: "pay manual repayment missing receipt bank details balance collection",
    steps: ["Use Track application to review the loan details available to you.", "Contact Noble through the published channels for verified payment instructions or to report a missing repayment.", "Keep your payment receipt and reference available for the support team."],
  },
  {
    title: "Ask a question or report an access problem", area: "Support", path: "/contact",
    keywords: "contact complaint accessibility technical problem support",
    steps: ["Open Contact and choose the published support channel that suits you.", "Explain what you were trying to do and the message you saw. Share your application reference if relevant."],
    check: "Keep passwords, one-time codes and banking credentials private.",
  },
  {
    title: "Review privacy information", area: "Support", path: "/privacy",
    keywords: "data protection privacy information choices",
    steps: ["Read how Noble handles personal information and the options available to you.", "Use the published contact details for a privacy question or request."],
  },
];

export default function HelpPage() {
  return (
    <div className="px-4 py-8 sm:px-6 sm:py-12">
      <ProcedureGuide title="Borrower help centre" guides={borrowerGuides}
        guidePath="/help" placeholder="Search applications, agreements, or repayments" />
    </div>
  );
}
