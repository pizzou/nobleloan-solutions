type Amount = number | string | null | undefined;

export interface LedgerCashFlowData {
  from?: string;
  to?: string;
  openingCash?: Amount;
  cashFromRepayments?: Amount;
  netCashDisbursed?: Amount;
  cashRefundedToBorrowers?: Amount;
  otherCashFlowsNet?: Amount;
  netChangeInCash?: Amount;
  actualClosingCash?: Amount;
  reconciliationDifference?: Amount;
  reconciles?: boolean;
  unverifiedDisbursementBreakdowns?: number;
}

export function reportAmount(value: Amount, currency: string) {
  if (value == null || value === "" || !Number.isFinite(Number(value))) return "Unavailable";
  return new Intl.NumberFormat("en", {
    style: "currency",
    currency,
    currencyDisplay: "code",
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(Number(value));
}

export default function LedgerCashFlow({ data, currency }: {
  data?: LedgerCashFlowData | null;
  currency: string;
}) {
  const negate = (value: Amount) => value != null && value !== "" ? -Number(value) : null;
  const summary = [
    { label: "Loan repayments received", value: data?.cashFromRepayments, context: "During this period" },
    { label: "Cash paid out for loans", value: data?.netCashDisbursed, context: "During this period" },
    { label: "Recorded ending balance", value: data?.actualClosingCash, context: "At the end of this period" },
  ];
  const rows = [
    ["Recorded cash at the start", data?.openingCash],
    ["Money received from loan repayments", data?.cashFromRepayments],
    ["Money paid out for new loans", negate(data?.netCashDisbursed)],
    ["Money refunded to borrowers", negate(data?.cashRefundedToBorrowers)],
    ["Other money in or out (net)", data?.otherCashFlowsNet],
    ["Total change in recorded cash", data?.netChangeInCash],
    ["Recorded cash at the end", data?.actualClosingCash],
  ] as const;

  return (
    <section aria-label="Cash in, cash out, and ending balance" className="min-w-0 rounded-xl border border-slate-200 bg-white">
      <div className="border-b border-slate-200 px-4 py-4 sm:px-5">
        <h3 className="font-semibold text-slate-900">Cash in, cash out, and ending balance</h3>
        <p className="mt-1 text-xs leading-5 text-slate-600">
          Cash and Bank (GL 1000) · {data?.from && data?.to ? `${data.from} to ${data.to}` : "Selected period"} · {currency}
        </p>
        <p className="mt-2 text-sm leading-6 text-slate-700">
          Repayments show money received during these dates. The ending balance includes the starting balance and all recorded money in and out for this account.
        </p>
      </div>
      {data ? (
        <>
          <dl className="grid gap-px border-b border-slate-200 bg-slate-200 sm:grid-cols-3">
            {summary.map(({ label, value, context }) => (
              <div key={label} className="min-w-0 bg-slate-50 px-4 py-4 sm:px-5">
                <dt className="text-sm font-medium text-slate-700">{label}</dt>
                <dd className="mt-1 text-lg font-semibold tabular-nums text-slate-950 [overflow-wrap:anywhere]">
                  {reportAmount(value, currency)}
                </dd>
                <dd className="mt-1 text-xs text-slate-600">{context}</dd>
              </div>
            ))}
          </dl>
          <details>
            <summary className="cursor-pointer px-4 py-3 text-sm font-semibold text-slate-800 hover:bg-slate-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-teal-600 sm:px-5">
              See how the ending balance was calculated
            </summary>
            <dl className="divide-y divide-slate-100">
              {rows.map(([label, value], index) => (
                <div key={label} className={`grid grid-cols-1 gap-1 px-4 py-2.5 text-sm sm:grid-cols-[minmax(0,1fr)_auto] sm:gap-4 sm:px-5 ${index >= 5 ? "bg-slate-50 font-semibold" : ""}`}>
                  <dt className="text-slate-700">{label}</dt>
                  <dd className="tabular-nums text-slate-900 [overflow-wrap:anywhere] sm:text-right">
                    {reportAmount(value, currency)}
                  </dd>
                </div>
              ))}
            </dl>
          </details>
          <div className="space-y-2 border-t border-slate-200 px-4 py-3 text-xs leading-5 sm:px-5">
            <p className={data.reconciles === false ? "font-semibold text-red-800" : "text-slate-600"}>
              {data.reconciles === true
                ? "The starting balance plus money in and out matches the ending balance in the accounting records."
                : data.reconciles === false
                  ? `Cash flow differs from the ledger by ${reportAmount(data.reconciliationDifference, currency)}.`
                  : "Ledger tie-out unavailable."}
            </p>
            <p className="text-slate-600">To confirm available cash, compare the recorded balance with the bank statement.</p>
            {Number(data.actualClosingCash) < 0 && (
              <p className="font-medium text-amber-900">The accounting records show a negative ending balance. Verify the opening balance, deposits, and transfers.</p>
            )}
            {(data.unverifiedDisbursementBreakdowns ?? 0) > 0 && (
              <p className="text-amber-900">
                {data.unverifiedDisbursementBreakdowns} disbursement journals need review to confirm the gross principal and withheld fee. Money paid out for new loans reflects the cash recorded in the ledger.
              </p>
            )}
          </div>
        </>
      ) : <p className="px-4 py-4 text-sm text-slate-600 sm:px-5">Cash flow is unavailable.</p>}
    </section>
  );
}
