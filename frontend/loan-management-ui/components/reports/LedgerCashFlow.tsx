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
  const rows = [
    ["Opening ledger cash", data?.openingCash],
    ["Loan repayments received", data?.cashFromRepayments],
    ["Cash paid to borrowers", negate(data?.netCashDisbursed)],
    ["Borrower refunds", negate(data?.cashRefundedToBorrowers)],
    ["Other cash movements, net", data?.otherCashFlowsNet],
    ["Net cash movement", data?.netChangeInCash],
    ["Closing ledger cash", data?.actualClosingCash],
  ] as const;

  return (
    <section aria-label="Ledger cash flow" className="min-w-0 rounded-xl border border-slate-200 bg-white">
      <div className="border-b border-slate-200 px-4 py-4 sm:px-5">
        <h3 className="font-semibold text-slate-900">Ledger cash flow</h3>
        <p className="mt-1 text-xs leading-5 text-slate-600">
          GL 1000 · {data?.from && data?.to ? `${data.from} to ${data.to}` : "Selected period"} · {currency}
        </p>
      </div>
      {data ? (
        <>
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
          <div className="space-y-2 border-t border-slate-200 px-4 py-3 text-xs leading-5 sm:px-5">
            <p className={data.reconciles === false ? "font-semibold text-red-800" : "text-slate-600"}>
              {data.reconciles === true
                ? "Cash flow ties to the ledger. Bank statement matching is a separate check."
                : data.reconciles === false
                  ? `Cash flow differs from the ledger by ${reportAmount(data.reconciliationDifference, currency)}.`
                  : "Ledger tie-out unavailable."}
            </p>
            {Number(data.actualClosingCash) < 0 && (
              <p className="font-medium text-amber-900">Cash has a credit balance. Check it against bank statements and recorded funding.</p>
            )}
            {(data.unverifiedDisbursementBreakdowns ?? 0) > 0 && (
              <p className="text-amber-900">
                {data.unverifiedDisbursementBreakdowns} disbursement journals need review to confirm the gross principal and withheld fee. The cash paid line uses the posted movement.
              </p>
            )}
          </div>
        </>
      ) : <p className="px-4 py-4 text-sm text-slate-600 sm:px-5">Cash flow is unavailable.</p>}
    </section>
  );
}
