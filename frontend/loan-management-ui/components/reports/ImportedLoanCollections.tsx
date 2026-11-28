import { reportAmount } from "./LedgerCashFlow";

export interface ImportedPortfolioData {
  loanCount: number;
  principalPaid?: number;
  interestPaid?: number;
  managementFeePaid?: number;
  extensionFeePaid?: number;
  penaltiesRecordedPaid?: number;
  applicationFeePaid?: number;
  interestOutstanding?: number;
  managementFeeOutstanding?: number;
  penaltyLoanCount?: number;
  penaltyAllocationRequiresVerification?: boolean;
  recordedHistoricCollections?: number;
}

export default function ImportedLoanCollections({ data, currency }: {
  data?: ImportedPortfolioData | null;
  currency: string;
}) {
  if (!data) return null;
  const paid = [
    ["Principal repaid", data.principalPaid],
    ["Interest paid", data.interestPaid],
    ["Management fees paid", data.managementFeePaid],
    ["Penalties marked paid", data.penaltiesRecordedPaid],
    ["Application fees paid", data.applicationFeePaid],
    ["Extension fees paid", data.extensionFeePaid],
  ] as const;

  return (
    <details className="min-w-0 rounded-xl border border-slate-200 bg-white">
      <summary className="cursor-pointer px-4 py-4 text-sm font-semibold text-slate-900 sm:px-5">
        Lifetime collections on imported loans
        <span className="block pl-4 pt-1 font-medium text-slate-600 sm:ml-2 sm:inline sm:p-0">
          {reportAmount(data.recordedHistoricCollections, currency)}
        </span>
      </summary>
      <div className="space-y-4 border-t border-slate-200 px-4 py-4 sm:px-5">
        <p className="text-xs leading-5 text-slate-600">
          {data.loanCount} imported loans. These are cumulative balances on loan records. Use the ledger cash flow for dated cash movements.
        </p>
        <dl className="grid gap-x-8 gap-y-2 text-sm sm:grid-cols-2">
          {paid.map(([label, amount]) => (
            <div key={label} className="flex flex-wrap justify-between gap-x-3 gap-y-1 border-b border-slate-100 pb-2">
              <dt className="text-slate-600">{label}</dt>
              <dd className="font-medium tabular-nums text-slate-900 [overflow-wrap:anywhere]">{reportAmount(amount, currency)}</dd>
            </div>
          ))}
        </dl>
        <p className="text-xs leading-5 text-slate-600">
          Outstanding on these loans: {reportAmount(data.interestOutstanding, currency)} interest and {reportAmount(data.managementFeeOutstanding, currency)} management fees.
        </p>
        {data.penaltyAllocationRequiresVerification && (
          <p className="rounded-lg bg-amber-50 px-3 py-2 text-xs leading-5 text-amber-900">
            Verify penalty allocations for {data.penaltyLoanCount} imported loans against their receipts.
          </p>
        )}
      </div>
    </details>
  );
}
