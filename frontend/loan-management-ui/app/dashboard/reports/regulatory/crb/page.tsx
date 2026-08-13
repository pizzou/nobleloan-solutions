"use client";

import { useEffect, useState } from "react";

import { regulatoryApi } from "@/services/regulatoryService";
import { PageSpinner } from "@/components/ui/Skeleton";
import { Button } from "@/components/ui/Button";

type CreditBureauRecord = {
  borrowerId?: number;
  nationalId?: string;
  fullName?: string;
  dateOfBirth?: string;
  gender?: string;
  phone?: string;

  loanNumber?: string;
  loanType?: string;
  loanStatus?: string;
  repaymentClassification?: string;

  loanAmount?: number;
  outstandingBalance?: number;
  daysPastDue?: number;
  creditScore?: number;

  dateOpened?: string;
  lastPayment?: string;
  maturityDate?: string;
  dateClosed?: string;

  branchName?: string;
  currency?: string;
};

const fmtMoney = (value?: number, currency = "RWF") =>
  value == null
    ? "—"
    : `${new Intl.NumberFormat("en-US", {
        minimumFractionDigits: 0,
        maximumFractionDigits: 2,
      }).format(Number(value))} ${currency}`;

const label = (value?: string) =>
  value
    ? value
        .replace(/_/g, " ")
        .toLowerCase()
        .replace(/\b\w/g, (c) => c.toUpperCase())
    : "—";

export default function CreditBureauReportPage() {
  const [records, setRecords] = useState<CreditBureauRecord[]>([]);

  const [from, setFrom] = useState("");

  const [to, setTo] = useState("");

  const [loading, setLoading] = useState(true);

  const [error, setError] = useState("");

  const [exporting, setExporting] = useState(false);

  const load = async () => {
    setLoading(true);
    setError("");

    try {
      const response = await regulatoryApi.creditBureauPreview({
        from: from || undefined,
        to: to || undefined,
      });

      setRecords(response as CreditBureauRecord[]);
    } catch (err: any) {
      console.error(err);

      setError(
        err?.response?.data?.error ||
          err?.message ||
          "Unable to load credit bureau records.",
      );
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const exportReport = async (format: "xlsx" | "csv" | "pdf") => {
    setExporting(true);

    try {
      await regulatoryApi.creditBureauExport(format, {
        from: from || undefined,
        to: to || undefined,
      });
    } catch (err) {
      window.alert(err instanceof Error ? err.message : "Export failed.");
    } finally {
      setExporting(false);
    }
  };

  if (loading) {
    return <PageSpinner />;
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-xl font-bold text-gray-900">
          Credit Bureau Report
        </h1>

        <p className="mt-1 text-sm text-gray-500">
          Authorized borrower and credit-performance reporting.
        </p>
      </div>

      <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-xs leading-5 text-amber-800">
        This report contains sensitive borrower and credit information. Use only
        for authorized credit-reporting purposes.
      </div>

      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="flex flex-wrap items-end gap-3">
          <div>
            <label className="mb-1 block text-xs font-semibold text-gray-500">
              From
            </label>

            <input
              type="date"
              value={from}
              onChange={(event) => setFrom(event.target.value)}
              className="rounded-lg border border-gray-200 px-3 py-2 text-sm"
            />
          </div>

          <div>
            <label className="mb-1 block text-xs font-semibold text-gray-500">
              To
            </label>

            <input
              type="date"
              value={to}
              onChange={(event) => setTo(event.target.value)}
              className="rounded-lg border border-gray-200 px-3 py-2 text-sm"
            />
          </div>

          <Button size="sm" variant="secondary" onClick={() => void load()}>
            Apply
          </Button>
        </div>

        <div className="flex gap-2">
          {(["xlsx", "csv", "pdf"] as const).map((format) => (
            <Button
              key={format}
              size="sm"
              variant="outline"
              loading={exporting}
              onClick={() => void exportReport(format)}
            >
              ↓ {format.toUpperCase()}
            </Button>
          ))}
        </div>
      </div>

      {error && (
        <div className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          {error}
        </div>
      )}

      <div className="overflow-x-auto rounded-xl border border-gray-200 bg-white">
        <table className="w-full min-w-[1500px] text-sm">
          <thead>
            <tr className="bg-gray-50 text-left text-[10px] uppercase tracking-wide text-gray-500">
              <th className="px-4 py-3">Borrower</th>

              <th className="px-4 py-3">National ID</th>

              <th className="px-4 py-3">Loan #</th>

              <th className="px-4 py-3">Loan Type</th>

              <th className="px-4 py-3">Status</th>

              <th className="px-4 py-3">Classification</th>

              <th className="px-4 py-3 text-right">Loan Amount</th>

              <th className="px-4 py-3 text-right">Outstanding</th>

              <th className="px-4 py-3 text-right">Days Past Due</th>

              <th className="px-4 py-3 text-right">Credit Score</th>

              <th className="px-4 py-3">Date Opened</th>

              <th className="px-4 py-3">Last Payment</th>

              <th className="px-4 py-3">Maturity</th>

              <th className="px-4 py-3">Branch</th>
            </tr>
          </thead>

          <tbody>
            {records.slice(0, 200).map((record, index) => (
              <tr
                key={`${record.loanNumber ?? "loan"}-${index}`}
                className="border-t border-gray-50"
              >
                <td className="px-4 py-3 font-medium text-gray-800">
                  {record.fullName || "—"}
                </td>

                <td className="px-4 py-3 text-gray-500">
                  {record.nationalId || "—"}
                </td>

                <td className="px-4 py-3 text-gray-600">
                  {record.loanNumber || "—"}
                </td>

                <td className="px-4 py-3 text-gray-600">
                  {label(record.loanType)}
                </td>

                <td className="px-4 py-3">
                  <span className="rounded-full bg-gray-100 px-2 py-1 text-[10px] font-semibold text-gray-700">
                    {label(record.loanStatus)}
                  </span>
                </td>

                <td className="px-4 py-3">
                  <span className="rounded-full bg-indigo-50 px-2 py-1 text-[10px] font-semibold text-indigo-700">
                    {label(record.repaymentClassification)}
                  </span>
                </td>

                <td className="px-4 py-3 text-right">
                  {fmtMoney(record.loanAmount, record.currency)}
                </td>

                <td className="px-4 py-3 text-right font-medium">
                  {fmtMoney(record.outstandingBalance, record.currency)}
                </td>

                <td className="px-4 py-3 text-right">
                  {record.daysPastDue ?? 0}
                </td>

                <td className="px-4 py-3 text-right">
                  {record.creditScore ?? "—"}
                </td>

                <td className="px-4 py-3 text-gray-500">
                  {record.dateOpened || "—"}
                </td>

                <td className="px-4 py-3 text-gray-500">
                  {record.lastPayment || "—"}
                </td>

                <td className="px-4 py-3 text-gray-500">
                  {record.maturityDate || "—"}
                </td>

                <td className="px-4 py-3 text-gray-500">
                  {record.branchName || "—"}
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        {records.length === 0 && (
          <div className="py-12 text-center text-sm text-gray-400">
            No credit bureau records found.
          </div>
        )}

        {records.length > 200 && (
          <div className="border-t border-gray-100 px-4 py-3 text-center text-xs text-gray-400">
            Showing the first 200 records. The complete report is available
            through export.
          </div>
        )}
      </div>
    </div>
  );
}
