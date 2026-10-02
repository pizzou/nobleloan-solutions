import { MarketingPage } from "./MarketingPage";

export function LoanUseCasePage({
  title,
  description,
  bullets,
  pageKey,
}: {
  title: string;
  description: string;
  bullets: string[];
  pageKey?: string;
}) {
  return (
    <MarketingPage
      eyebrow="Personal lending"
      title={title}
      description={description}
      pageKey={pageKey}
      sections={[
        {
          title: "A practical way to fund a planned need",
          body: "Noble Loan Solutions provides financing for approved purposes. The actual product, amount, fees, term and assessment remain the source of truth.",
          bullets,
        },
        {
          title: "Know the cost before you commit",
          body: "Use the Noble calculators to model an indicative repayment scenario, then review the exact terms presented during the application process.",
        },
        {
          title: "Digital application",
          body: "Start online, provide the information requested for verification, and use Track application to follow a submitted application.",
        },
        {
          title: "Responsible borrowing",
          body: "Only borrow an amount you can reasonably repay. Compare the total cost, repayment schedule and applicable fees before accepting an offer.",
        },
      ]}
    />
  );
}
