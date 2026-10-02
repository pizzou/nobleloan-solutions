import { MarketingPage } from "./MarketingPage";

export function LoanUseCasePage({ title, description, bullets }: { title: string; description: string; bullets: string[] }) {
  return <MarketingPage eyebrow="Personal lending" title={title} description={description} sections={[
    { title: "A practical way to fund a planned need", body: "Noble Loan Solutions offers personal financing for approved purposes. The use case helps you understand why you may borrow; the actual loan product, amount, fees, term and assessment remain the source of truth.", bullets },
    { title: "Know the cost before you commit", body: "Use the Noble calculator to model a repayment scenario, then review the exact terms presented during the application process. Estimates are for planning and are not an offer or approval." },
    { title: "Digital application", body: "Start online, provide the information requested for verification, and use Track application to follow the status of a submitted application." },
    { title: "Responsible borrowing", body: "Only borrow an amount you can reasonably repay. Compare the total cost, repayment schedule and applicable fees before accepting any offer." },
  ]} />;
}
