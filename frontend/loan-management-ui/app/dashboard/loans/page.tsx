"use client";

import { useEffect, useMemo, useState } from "react";

import { getLoans } from "@/services/loanService";
import { PageSpinner } from "@/components/ui/Skeleton";

type Loan = {
  id?: number;
  referenceNumber?: string;

  borrower?: {
    id?: number;
    firstName?: string;
    lastName?: string;
    fullName?: string;
    name?: string;
  };

  loanType?: string;
  status?: string;

  creditQuality?: string;
  arrearsStatus?: string;
  collectionsStage?: string;
  classifiedAt?: string;

  daysOverdue?: number;
  missedInstallments?: number;

  amount?: number;
  disbursedAmount?: number;
  netDisbursedAmount?: number;

  interestRate?: number;
  interestRateType?: string;

  managementFeeRate?: number;
  managementFee?: number;
  managementFeePaid?: number;

  processingFeeRate?: number;
  processingFee?: number;
  processingFeePaid?: number;

  totalInterest?: number;
  interestPaid?: number;

  totalRepayable?: number;
  totalPaid?: number;
  outstandingBalance?: number;

  currency?: string;

  riskScore?: number;
  riskCategory?: string;
  debtToIncomeRatio?: number;
  creditScoreSnapshot?: number;

  startDate?: string;
  approvedAt?: string;
  disbursedAt?: string;
  maturityDate?: string;
  nextPaymentDate?: string;
  nextDueDate?: string;
  lastPaymentDate?: string;

  loanOfficer?: {
    firstName?: string;
    lastName?: string;
    name?: string;
  };
};

const money = (value?: number, currency = "RWF") =>
  value == null
    ? "—"
    : `${new Intl.NumberFormat("en-US", {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      }).format(Number(value))} ${currency}`;

const number = (value?: number) =>
  value == null ? "—" : new Intl.NumberFormat("en-US").format(Number(value));

const date = (value?: string) => {
  if (!value) return "—";

  const parsed = new Date(value);

  if (Number.isNaN(parsed.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat("en-US", {
    year: "numeric",
    month: "short",
    day: "numeric",
  }).format(parsed);
};

const text = (value?: string) =>
  value
    ? value
        .replace(/_/g, " ")
        .toLowerCase()
        .replace(/\b\w/g, (char) => char.toUpperCase())
    : "—";

const borrowerName = (loan: Loan) => {
  const borrower = loan.borrower;

  if (!borrower) return "—";

  if (borrower.fullName) {
    return borrower.fullName;
  }

  if (borrower.name) {
    return borrower.name;
  }

  return (
    [borrower.firstName, borrower.lastName].filter(Boolean).join(" ") || "—"
  );
};

const officerName = (loan: Loan) => {
  const officer = loan.loanOfficer;

  if (!officer) return "—";

  if (officer.name) return officer.name;

  return [officer.firstName, officer.lastName].filter(Boolean).join(" ") || "—";
};

function Badge({
  value,
  tone = "gray",
}: {
  value?: string;
  tone?: "gray" | "green" | "yellow" | "orange" | "red" | "blue";
}) {
  const colors = {
    gray: "bg-gray-100 text-gray-700",
    green: "bg-emerald-100 text-emerald-700",
    yellow: "bg-yellow-100 text-yellow-700",
    orange: "bg-orange-100 text-orange-700",
    red: "bg-red-100 text-red-700",
    blue: "bg-blue-100 text-blue-700",
  };

  return (
    <span
      className={`inline-flex rounded-full px-2 py-1 text-[10px] font-semibold ${colors[tone]}`}
    >
      {text(value)}
    </span>
  );
}

function qualityTone(
  quality?: string,
): "green" | "yellow" | "orange" | "red" | "gray" {
  switch (quality) {
    case "CURRENT":
      return "green";

    case "WATCH":
      return "yellow";

    case "SUBSTANDARD":
      return "orange";

    case "DOUBTFUL":
    case "WRITTEN_OFF":
    case "LOSS":
      return "red";

    default:
      return "gray";
  }
}

function statusTone(
  status?: string,
): "green" | "yellow" | "orange" | "red" | "blue" | "gray" {
  switch (status) {
    case "ACTIVE":
      return "green";

    case "PENDING":
      return "yellow";

    case "UNDER_REVIEW":
      return "blue";

    case "OVERDUE":
    case "DEFAULTED":
      return "red";

    case "PAID":
    case "CLOSED":
      return "green";

    case "REJECTED":
    case "WRITTEN_OFF":
      return "red";

    default:
      return "gray";
  }
}

function Field({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="rounded-lg border border-gray-100 bg-gray-50 p-3">
      <p className="text-[9px] font-semibold uppercase tracking-wide text-gray-400">
        {label}
      </p>

      <p className="mt-1 text-sm font-semibold text-gray-800">{value}</p>
    </div>
  );
}

export default function LoansPage() {
  const [loans, setLoans] = useState<Loan[]>([]);

  const [loading, setLoading] = useState(true);

  const [error, setError] = useState("");

  const [search, setSearch] = useState("");

  const [status, setStatus] = useState("");

  const [type, setType] = useState("");

  const [selected, setSelected] = useState<Loan | null>(null);

  const load = async () => {
    setLoading(true);
    setError("");

    try {
      const response = await getLoans();

      const raw: any = response;

      const rows = Array.isArray(raw)
        ? raw
        : Array.isArray(raw?.content)
          ? raw.content
          : Array.isArray(raw?.data)
            ? raw.data
            : Array.isArray(raw?.data?.content)
              ? raw.data.content
              : [];

      setLoans(rows as Loan[]);
    } catch (err) {
      console.error(err);

      setError(err instanceof Error ? err.message : "Unable to load loans.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const loanTypes = useMemo(
    () =>
      Array.from(
        new Set(loans.map((loan) => loan.loanType).filter(Boolean)),
      ) as string[],
    [loans],
  );

  const filtered = useMemo(() => {
    const query = search.trim().toLowerCase();

    return loans.filter((loan) => {
      const matchesStatus =
        !status || String(loan.status).toUpperCase() === status;

      const matchesType = !type || String(loan.loanType).toUpperCase() === type;

      if (!matchesStatus || !matchesType) {
        return false;
      }

      if (!query) {
        return true;
      }

      const haystack = [
        loan.referenceNumber,
        borrowerName(loan),
        loan.borrower?.id,
        loan.loanType,
        loan.status,
        loan.creditQuality,
        loan.arrearsStatus,
        loan.collectionsStage,
        loan.riskCategory,
        loan.creditScoreSnapshot,
      ]
        .filter(Boolean)
        .join(" ")
        .toLowerCase();

      return haystack.includes(query);
    });
  }, [loans, search, status, type]);

  if (loading) {
    return <PageSpinner />;
  }

  if (error) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <div className="max-w-md rounded-2xl border border-red-200 bg-white p-8 text-center shadow-sm">
          <div className="text-3xl">⚠️</div>

          <h2 className="mt-4 text-lg font-bold text-gray-900">
            Unable to load loans
          </h2>

          <p className="mt-2 text-sm text-gray-500">{error}</p>

          <button
            type="button"
            onClick={() => void load()}
            className="mt-6 rounded-lg bg-gray-900 px-4 py-2 text-sm font-semibold text-white"
          >
            Try Again
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Loans</h1>

          <p className="mt-1 text-sm text-gray-500">
            Loan portfolio, balances, repayment status and credit-risk
            classification.
          </p>
        </div>

        <div className="text-sm text-gray-500">
          {filtered.length} of {loans.length} loans
        </div>
      </div>

      <div className="grid grid-cols-1 gap-3 md:grid-cols-[1fr_180px_180px_auto]">
        <input
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder="Search reference, borrower, classification..."
          className="rounded-xl border border-gray-200 bg-white px-4 py-2.5 text-sm outline-none focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100"
        />

        <select
          value={status}
          onChange={(event) => setStatus(event.target.value)}
          className="rounded-xl border border-gray-200 bg-white px-3 py-2.5 text-sm"
        >
          <option value="">All statuses</option>

          {[
            "PENDING",
            "UNDER_REVIEW",
            "ACTIVE",
            "OVERDUE",
            "DEFAULTED",
            "PAID",
            "CLOSED",
            "REJECTED",
            "WRITTEN_OFF",
          ].map((item) => (
            <option key={item} value={item}>
              {text(item)}
            </option>
          ))}
        </select>

        <select
          value={type}
          onChange={(event) => setType(event.target.value)}
          className="rounded-xl border border-gray-200 bg-white px-3 py-2.5 text-sm"
        >
          <option value="">All loan types</option>

          {loanTypes.map((item) => (
            <option key={item} value={item}>
              {text(item)}
            </option>
          ))}
        </select>

        <button
          type="button"
          onClick={() => {
            setSearch("");
            setStatus("");
            setType("");
          }}
          className="rounded-xl border border-gray-200 bg-white px-4 py-2.5 text-sm font-semibold text-gray-600 hover:bg-gray-50"
        >
          Clear
        </button>
      </div>

      <div className="overflow-x-auto rounded-2xl border border-gray-200 bg-white shadow-sm">
        <table className="w-full min-w-[1500px] text-sm">
          <thead>
            <tr className="bg-gray-50 text-left text-[10px] uppercase tracking-wide text-gray-500">
              <th className="px-4 py-3">Loan</th>

              <th className="px-4 py-3">Borrower</th>

              <th className="px-4 py-3">Type</th>

              <th className="px-4 py-3">Status</th>

              <th className="px-4 py-3">Credit Quality</th>

              <th className="px-4 py-3">Arrears</th>

              <th className="px-4 py-3">Collection Stage</th>

              <th className="px-4 py-3 text-right">Days Overdue</th>

              <th className="px-4 py-3 text-right">Principal</th>

              <th className="px-4 py-3 text-right">Outstanding</th>

              <th className="px-4 py-3">Risk</th>

              <th className="px-4 py-3">Classified</th>

              <th className="px-4 py-3" />
            </tr>
          </thead>

          <tbody>
            {filtered.map((loan) => (
              <tr
                key={loan.id}
                className="border-t border-gray-100 hover:bg-gray-50"
              >
                <td className="px-4 py-3">
                  <div className="font-semibold text-gray-900">
                    {loan.referenceNumber || `Loan #${loan.id ?? "—"}`}
                  </div>

                  <div className="mt-1 text-[10px] text-gray-400">
                    ID {loan.id ?? "—"}
                  </div>
                </td>

                <td className="px-4 py-3">
                  <div className="font-medium text-gray-800">
                    {borrowerName(loan)}
                  </div>

                  <div className="mt-1 text-[10px] text-gray-400">
                    Borrower #{loan.borrower?.id ?? "—"}
                  </div>
                </td>

                <td className="px-4 py-3 text-gray-600">
                  {text(loan.loanType)}
                </td>

                <td className="px-4 py-3">
                  <Badge value={loan.status} tone={statusTone(loan.status)} />
                </td>

                <td className="px-4 py-3">
                  <Badge
                    value={loan.creditQuality}
                    tone={qualityTone(loan.creditQuality)}
                  />
                </td>

                <td className="px-4 py-3">
                  <Badge
                    value={loan.arrearsStatus}
                    tone={loan.arrearsStatus === "PAST_DUE" ? "red" : "green"}
                  />
                </td>

                <td className="px-4 py-3">
                  <Badge
                    value={loan.collectionsStage}
                    tone={
                      loan.collectionsStage === "NORMAL"
                        ? "green"
                        : loan.collectionsStage === "REMINDER"
                          ? "yellow"
                          : loan.collectionsStage === "RECOVERY"
                            ? "red"
                            : "orange"
                    }
                  />
                </td>

                <td className="px-4 py-3 text-right font-semibold">
                  {number(loan.daysOverdue)}
                </td>

                <td className="px-4 py-3 text-right">
                  {money(loan.amount, loan.currency)}
                </td>

                <td className="px-4 py-3 text-right font-semibold text-gray-900">
                  {money(loan.outstandingBalance, loan.currency)}
                </td>

                <td className="px-4 py-3">
                  <div className="font-semibold text-gray-800">
                    {text(loan.riskCategory)}
                  </div>

                  <div className="mt-1 text-[10px] text-gray-400">
                    Score: {loan.riskScore ?? "—"}
                  </div>
                </td>

                <td className="px-4 py-3 text-xs text-gray-500">
                  {date(loan.classifiedAt)}
                </td>

                <td className="px-4 py-3 text-right">
                  <button
                    type="button"
                    onClick={() => setSelected(loan)}
                    className="rounded-lg border border-gray-200 bg-white px-3 py-1.5 text-xs font-semibold text-gray-700 hover:bg-gray-50"
                  >
                    View
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        {filtered.length === 0 && (
          <div className="py-12 text-center text-sm text-gray-400">
            No loans match the selected filters.
          </div>
        )}
      </div>

      {selected && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="max-h-[92vh] w-full max-w-6xl overflow-y-auto rounded-2xl bg-white shadow-2xl">
            <div className="sticky top-0 z-10 flex items-center justify-between border-b border-gray-100 bg-white px-6 py-4">
              <div>
                <h2 className="text-lg font-bold text-gray-900">
                  {selected.referenceNumber || `Loan #${selected.id}`}
                </h2>

                <p className="mt-1 text-xs text-gray-500">
                  {borrowerName(selected)}
                </p>
              </div>

              <button
                type="button"
                onClick={() => setSelected(null)}
                className="flex h-9 w-9 items-center justify-center rounded-full bg-gray-100 text-gray-600 hover:bg-gray-200"
              >
                ×
              </button>
            </div>

            <div className="space-y-6 p-6">
              <section>
                <h3 className="mb-3 text-sm font-semibold text-gray-900">
                  Credit &amp; Collections Classification
                </h3>

                <div className="grid grid-cols-2 gap-3 md:grid-cols-5">
                  <Field
                    label="Credit Quality"
                    value={
                      <Badge
                        value={selected.creditQuality}
                        tone={qualityTone(selected.creditQuality)}
                      />
                    }
                  />

                  <Field
                    label="Arrears Status"
                    value={
                      <Badge
                        value={selected.arrearsStatus}
                        tone={
                          selected.arrearsStatus === "PAST_DUE"
                            ? "red"
                            : "green"
                        }
                      />
                    }
                  />

                  <Field
                    label="Collection Stage"
                    value={
                      <Badge
                        value={selected.collectionsStage}
                        tone={
                          selected.collectionsStage === "NORMAL"
                            ? "green"
                            : selected.collectionsStage === "REMINDER"
                              ? "yellow"
                              : selected.collectionsStage === "RECOVERY"
                                ? "red"
                                : "orange"
                        }
                      />
                    }
                  />

                  <Field
                    label="Days Overdue"
                    value={number(selected.daysOverdue)}
                  />

                  <Field
                    label="Classified At"
                    value={date(selected.classifiedAt)}
                  />
                </div>
              </section>

              <section>
                <h3 className="mb-3 text-sm font-semibold text-gray-900">
                  Loan Financial Position
                </h3>

                <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
                  <Field
                    label="Original Principal"
                    value={money(selected.amount, selected.currency)}
                  />

                  <Field
                    label="Gross Disbursed"
                    value={money(selected.disbursedAmount, selected.currency)}
                  />

                  <Field
                    label="Net Disbursed"
                    value={money(
                      selected.netDisbursedAmount,
                      selected.currency,
                    )}
                  />

                  <Field
                    label="Outstanding Balance"
                    value={money(
                      selected.outstandingBalance,
                      selected.currency,
                    )}
                  />

                  <Field
                    label="Total Repayable"
                    value={money(selected.totalRepayable, selected.currency)}
                  />

                  <Field
                    label="Total Paid"
                    value={money(selected.totalPaid, selected.currency)}
                  />

                  <Field
                    label="Total Interest"
                    value={money(selected.totalInterest, selected.currency)}
                  />

                  <Field
                    label="Interest Paid"
                    value={money(selected.interestPaid, selected.currency)}
                  />
                </div>
              </section>

              <section>
                <h3 className="mb-3 text-sm font-semibold text-gray-900">
                  Interest &amp; Fees
                </h3>

                <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
                  <Field
                    label="Interest Rate"
                    value={
                      selected.interestRate != null
                        ? `${selected.interestRate}%`
                        : "—"
                    }
                  />

                  <Field
                    label="Rate Type"
                    value={text(selected.interestRateType)}
                  />

                  <Field
                    label="Management Fee Rate"
                    value={
                      selected.managementFeeRate != null
                        ? `${selected.managementFeeRate}%`
                        : "—"
                    }
                  />

                  <Field
                    label="Management Fee"
                    value={money(selected.managementFee, selected.currency)}
                  />

                  <Field
                    label="Management Fee Paid"
                    value={money(selected.managementFeePaid, selected.currency)}
                  />

                  <Field
                    label="Processing Fee Rate"
                    value={
                      selected.processingFeeRate != null
                        ? `${selected.processingFeeRate}%`
                        : "—"
                    }
                  />

                  <Field
                    label="Processing Fee"
                    value={money(selected.processingFee, selected.currency)}
                  />

                  <Field
                    label="Processing Fee Paid"
                    value={money(selected.processingFeePaid, selected.currency)}
                  />
                </div>
              </section>

              <section>
                <h3 className="mb-3 text-sm font-semibold text-gray-900">
                  Risk Profile
                </h3>

                <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
                  <Field label="Risk Score" value={selected.riskScore ?? "—"} />

                  <Field
                    label="Risk Category"
                    value={text(selected.riskCategory)}
                  />

                  <Field
                    label="Debt-to-Income Ratio"
                    value={
                      selected.debtToIncomeRatio != null
                        ? `${selected.debtToIncomeRatio}%`
                        : "—"
                    }
                  />

                  <Field
                    label="Credit Score Snapshot"
                    value={selected.creditScoreSnapshot ?? "—"}
                  />
                </div>
              </section>

              <section>
                <h3 className="mb-3 text-sm font-semibold text-gray-900">
                  Loan Dates
                </h3>

                <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
                  <Field label="Start Date" value={date(selected.startDate)} />

                  <Field
                    label="Approved At"
                    value={date(selected.approvedAt)}
                  />

                  <Field
                    label="Disbursed At"
                    value={date(selected.disbursedAt)}
                  />

                  <Field
                    label="Maturity Date"
                    value={date(selected.maturityDate)}
                  />

                  <Field
                    label="Next Payment"
                    value={date(selected.nextPaymentDate)}
                  />

                  <Field
                    label="Next Due Date"
                    value={date(selected.nextDueDate)}
                  />

                  <Field
                    label="Last Payment"
                    value={date(selected.lastPaymentDate)}
                  />

                  <Field
                    label="Missed Installments"
                    value={number(selected.missedInstallments)}
                  />
                </div>
              </section>

              <section>
                <h3 className="mb-3 text-sm font-semibold text-gray-900">
                  Responsible Officer
                </h3>

                <Field label="Loan Officer" value={officerName(selected)} />
              </section>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
