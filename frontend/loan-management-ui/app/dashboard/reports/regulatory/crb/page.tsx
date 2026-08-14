"use client";

import React, { useCallback, useEffect, useMemo, useState } from "react";

import {
  regulatoryApi,
  type CreditRecord,
  type ExportFormat,
} from "@/services/regulatoryService";

const safeNumber = (value: unknown): number => {
  const result = Number(value);

  return Number.isFinite(result) ? result : 0;
};

const labelize = (value?: string | null): string =>
  value ? value.replace(/_/g, " ") : "—";

const formatMoney = (value: unknown, currency = "RWF"): string => {
  const amount = safeNumber(value);

  try {
    return new Intl.NumberFormat("en-RW", {
      style: "currency",
      currency,
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(amount);
  } catch {
    return `${currency} ${amount.toLocaleString()}`;
  }
};

const formatDate = (value?: string | null): string => {
  if (!value) {
    return "—";
  }

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat("en-RW", {
    year: "numeric",
    month: "short",
    day: "numeric",
  }).format(date);
};

const statusClass = (value?: string | null): string => {
  const normalized = value?.toUpperCase();

  if (
    normalized === "ACTIVE" ||
    normalized === "PAID" ||
    normalized === "CLOSED" ||
    normalized === "CURRENT"
  ) {
    return "bg-emerald-50 text-emerald-700 border-emerald-200";
  }

  if (
    normalized === "OVERDUE" ||
    normalized === "WATCH" ||
    normalized === "PAST_DUE"
  ) {
    return "bg-amber-50 text-amber-700 border-amber-200";
  }

  if (
    normalized === "DEFAULTED" ||
    normalized === "DOUBTFUL" ||
    normalized === "WRITTEN_OFF"
  ) {
    return "bg-red-50 text-red-700 border-red-200";
  }

  return "bg-slate-100 text-slate-600 border-slate-200";
};

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <p className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
        {label}
      </p>

      <p className="mt-2 text-2xl font-bold text-slate-900">{value}</p>
    </div>
  );
}

export default function CreditBureauPage() {
  const [borrowerId, setBorrowerId] = useState("");

  const [branchId, setBranchId] = useState("");

  const [from, setFrom] = useState("");

  const [to, setTo] = useState("");

  const [records, setRecords] = useState<CreditRecord[]>([]);

  const [loading, setLoading] = useState(false);

  const [exporting, setExporting] = useState<ExportFormat | null>(null);

  const [error, setError] = useState<string | null>(null);

  const validate = useCallback(() => {
    if (borrowerId) {
      const id = Number(borrowerId);

      if (!Number.isInteger(id) || id <= 0) {
        return "Borrower ID must be a positive whole number.";
      }
    }

    if (branchId) {
      const id = Number(branchId);

      if (!Number.isInteger(id) || id <= 0) {
        return "Branch ID must be a positive whole number.";
      }
    }

    if (from && to && from > to) {
      return "Start date cannot be after the end date.";
    }

    return null;
  }, [borrowerId, branchId, from, to]);

  const loadPreview = useCallback(async () => {
    const validationError = validate();

    if (validationError) {
      setError(validationError);
      return;
    }

    try {
      setLoading(true);
      setError(null);

      const result = await regulatoryApi.creditBureauPreview({
        ...(borrowerId
          ? {
              borrowerId: Number(borrowerId),
            }
          : {}),
        ...(branchId
          ? {
              branchId: Number(branchId),
            }
          : {}),
        ...(from ? { from } : {}),
        ...(to ? { to } : {}),
      });

      setRecords(Array.isArray(result) ? result : []);
    } catch (err) {
      console.error("Credit Bureau preview error:", err);

      setRecords([]);

      setError(
        regulatoryApi.getErrorMessage(
          err,
          "Unable to load Credit Bureau records.",
        ),
      );
    } finally {
      setLoading(false);
    }
  }, [borrowerId, branchId, from, to, validate]);

  useEffect(() => {
    void loadPreview();
  }, [loadPreview]);

  const exportRecords = async (format: ExportFormat) => {
    const validationError = validate();

    if (validationError) {
      setError(validationError);
      return;
    }

    try {
      setExporting(format);
      setError(null);

      await regulatoryApi.creditBureauExport(format, {
        ...(borrowerId
          ? {
              borrowerId: Number(borrowerId),
            }
          : {}),
        ...(branchId
          ? {
              branchId: Number(branchId),
            }
          : {}),
        ...(from ? { from } : {}),
        ...(to ? { to } : {}),
      });
    } catch (err) {
      setError(
        regulatoryApi.getErrorMessage(
          err,
          `Unable to export Credit Bureau ${format.toUpperCase()} report.`,
        ),
      );
    } finally {
      setExporting(null);
    }
  };

  const borrowerCount = useMemo(
    () => new Set(records.map((record) => record.borrowerId)).size,
    [records],
  );

  const overdueCount = useMemo(
    () => records.filter((record) => safeNumber(record.daysPastDue) > 0).length,
    [records],
  );

  const maleCount = records.filter(
    (record) => record.gender?.toLowerCase() === "male",
  ).length;

  const femaleCount = records.filter(
    (record) => record.gender?.toLowerCase() === "female",
  ).length;

  const totalGender = maleCount + femaleCount;

  return (
    <main className="min-h-screen bg-slate-50">
      <div className="mx-auto max-w-[1600px] space-y-6 p-4 md:p-6 lg:p-8">
        <section className="rounded-3xl bg-gradient-to-br from-slate-950 via-indigo-950 to-violet-950 p-6 text-white shadow-xl md:p-8">
          <div className="flex flex-col gap-6 lg:flex-row lg:items-center lg:justify-between">
            <div>
              <div className="mb-3 inline-flex rounded-full border border-white/10 bg-white/10 px-3 py-1 text-xs font-medium">
                Credit Information
              </div>

              <h1 className="text-3xl font-bold md:text-4xl">Credit Bureau</h1>

              <p className="mt-2 max-w-3xl text-sm leading-6 text-indigo-200 md:text-base">
                Review borrower credit information, loan history, repayment
                performance and credit reporting records.
              </p>
            </div>

            <div className="flex flex-wrap gap-2">
              {(["pdf", "xlsx", "csv"] as ExportFormat[]).map((format) => (
                <button
                  key={format}
                  type="button"
                  onClick={() => void exportRecords(format)}
                  disabled={exporting !== null}
                  className="rounded-xl border border-white/15 bg-white/10 px-4 py-2 text-sm font-semibold hover:bg-white/20 disabled:opacity-50"
                >
                  {exporting === format
                    ? "Exporting…"
                    : `Export ${format.toUpperCase()}`}
                </button>
              ))}
            </div>
          </div>
        </section>

        {error && (
          <div className="rounded-2xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
            <div className="flex items-center justify-between gap-4">
              <span>{error}</span>

              <button
                type="button"
                onClick={() => setError(null)}
                className="font-semibold"
              >
                Dismiss
              </button>
            </div>
          </div>
        )}

        <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm md:p-6">
          <h2 className="text-xl font-bold text-slate-900">
            Credit Bureau Search
          </h2>

          <p className="mt-1 text-sm text-slate-500">
            Filter the credit records you want to review or export.
          </p>

          <div className="mt-5 grid gap-4 md:grid-cols-2 xl:grid-cols-5">
            <Field
              label="Borrower ID"
              value={borrowerId}
              onChange={setBorrowerId}
              placeholder="Optional"
            />

            <Field
              label="Branch ID"
              value={branchId}
              onChange={setBranchId}
              placeholder="Optional"
            />

            <Field label="From" type="date" value={from} onChange={setFrom} />

            <Field label="To" type="date" value={to} onChange={setTo} />

            <div className="flex items-end">
              <button
                type="button"
                onClick={() => void loadPreview()}
                disabled={loading}
                className="w-full rounded-xl bg-slate-950 px-5 py-2.5 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-50"
              >
                {loading ? "Searching…" : "Search Records"}
              </button>
            </div>
          </div>
        </section>

        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Stat label="Records" value={records.length.toLocaleString()} />

          <Stat label="Borrowers" value={borrowerCount.toLocaleString()} />

          <Stat
            label="Overdue / Delinquent"
            value={overdueCount.toLocaleString()}
          />

          <Stat
            label="Female / Male"
            value={`${femaleCount.toLocaleString()} / ${maleCount.toLocaleString()}`}
          />
        </div>

        <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="text-xl font-bold text-slate-900">
            Gender Distribution
          </h2>

          <div className="mt-5 grid gap-4 sm:grid-cols-2">
            <Stat
              label="Female"
              value={`${femaleCount.toLocaleString()} (${(totalGender > 0
                ? (femaleCount / totalGender) * 100
                : 0
              ).toFixed(2)}%)`}
            />

            <Stat
              label="Male"
              value={`${maleCount.toLocaleString()} (${(totalGender > 0
                ? (maleCount / totalGender) * 100
                : 0
              ).toFixed(2)}%)`}
            />
          </div>
        </section>

        <section className="overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-sm">
          <div className="border-b border-slate-200 p-5">
            <h2 className="text-xl font-bold text-slate-900">Credit Records</h2>
          </div>

          {records.length === 0 ? (
            <div className="p-12 text-center">
              <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-slate-100 text-2xl">
                🧾
              </div>

              <h3 className="mt-4 font-semibold text-slate-900">
                No credit records found
              </h3>

              <p className="mt-1 text-sm text-slate-500">
                Try changing your search criteria.
              </p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-[1700px] w-full text-sm">
                <thead className="bg-slate-50">
                  <tr>
                    {[
                      "Borrower",
                      "National ID",
                      "Gender",
                      "Loan Number",
                      "Loan Type",
                      "Loan Status",
                      "Classification",
                      "Loan Amount",
                      "Outstanding",
                      "Days Past Due",
                      "Credit Score",
                      "Date Opened",
                      "Last Payment",
                      "Maturity",
                      "Date Closed",
                      "Branch",
                      "Currency",
                    ].map((header) => (
                      <th
                        key={header}
                        className="px-5 py-3 text-left text-[10px] font-bold uppercase tracking-wider text-slate-400"
                      >
                        {header}
                      </th>
                    ))}
                  </tr>
                </thead>

                <tbody className="divide-y divide-slate-100">
                  {records.map((record, index) => (
                    <tr
                      key={`${record.borrowerId ?? "borrower"}-${record.loanNumber ?? "loan"}-${index}`}
                      className="hover:bg-slate-50"
                    >
                      <td className="px-5 py-4">
                        <div className="font-semibold text-slate-900">
                          {record.fullName ?? "N/A"}
                        </div>

                        <div className="text-xs text-slate-400">
                          ID: {record.borrowerId ?? "—"}
                        </div>
                      </td>

                      <td className="px-5 py-4 text-slate-600">
                        {record.nationalId ?? "N/A"}
                      </td>

                      <td className="px-5 py-4">{record.gender ?? "—"}</td>

                      <td className="px-5 py-4 font-mono text-xs text-slate-600">
                        {record.loanNumber ?? "—"}
                      </td>

                      <td className="px-5 py-4">{labelize(record.loanType)}</td>

                      <td className="px-5 py-4">
                        <span
                          className={`inline-flex rounded-full border px-2 py-1 text-[10px] font-bold uppercase ${statusClass(
                            record.loanStatus,
                          )}`}
                        >
                          {labelize(record.loanStatus)}
                        </span>
                      </td>

                      <td className="px-5 py-4">
                        <span
                          className={`inline-flex rounded-full border px-2 py-1 text-[10px] font-bold uppercase ${statusClass(
                            record.repaymentClassification,
                          )}`}
                        >
                          {labelize(record.repaymentClassification)}
                        </span>
                      </td>

                      <td className="px-5 py-4 whitespace-nowrap font-semibold">
                        {formatMoney(
                          record.loanAmount,
                          record.currency ?? "RWF",
                        )}
                      </td>

                      <td className="px-5 py-4 whitespace-nowrap font-semibold">
                        {formatMoney(
                          record.outstandingBalance,
                          record.currency ?? "RWF",
                        )}
                      </td>

                      <td className="px-5 py-4 text-center">
                        {safeNumber(record.daysPastDue)}
                      </td>

                      <td className="px-5 py-4 font-semibold">
                        {record.creditScore ?? "—"}
                      </td>

                      <td className="px-5 py-4 whitespace-nowrap text-slate-500">
                        {formatDate(record.dateOpened)}
                      </td>

                      <td className="px-5 py-4 whitespace-nowrap text-slate-500">
                        {formatDate(record.lastPaymentDate)}
                      </td>

                      <td className="px-5 py-4 whitespace-nowrap text-slate-500">
                        {formatDate(record.maturityDate)}
                      </td>

                      <td className="px-5 py-4 whitespace-nowrap text-slate-500">
                        {formatDate(record.dateClosed)}
                      </td>

                      <td className="px-5 py-4">{record.branchName ?? "—"}</td>

                      <td className="px-5 py-4 font-semibold">
                        {record.currency ?? "RWF"}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </div>
    </main>
  );
}

function Field({
  label,
  value,
  onChange,
  type = "text",
  placeholder,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: string;
  placeholder?: string;
}) {
  return (
    <label className="block">
      <span className="mb-2 block text-xs font-semibold uppercase tracking-wider text-slate-400">
        {label}
      </span>

      <input
        type={type}
        value={value}
        placeholder={placeholder}
        onChange={(event) => onChange(event.target.value)}
        className="w-full rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-sm outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-100"
      />
    </label>
  );
}
