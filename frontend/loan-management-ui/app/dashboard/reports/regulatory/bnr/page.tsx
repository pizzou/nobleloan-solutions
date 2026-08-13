"use client";

import React, { useCallback, useEffect, useMemo, useState } from "react";

import {
  regulatoryApi,
  type BnrFinancialStatementReport,
  type BnrReportParams,
  type BnrSummary,
  type BreakdownRow,
  type ExportFormat,
  type FinancialStatementRow,
  type RegulatoryPeriod,
} from "@/services/regulatoryService";

/**
 * BNR Regulatory Report
 *
 * Existing functionality preserved:
 * - BNR summary
 * - Loan status
 * - Gender breakdown
 * - Loan-type breakdown
 * - Branch breakdown
 * - PDF export
 * - Excel export
 * - CSV export
 * - Period filters
 * - Custom date filters
 * - Error handling
 *
 * Backend-aligned additions/fixes:
 * - Uses summary.totalLoans instead of the old/non-existent totalLoansIssued
 * - Loads the BNR financial statement endpoint exposed by RegulatoryReportingService
 * - Displays accounting financial-statement totals
 * - Displays accounting validation controls
 * - Displays PAR/NPL and additional BNR fields exposed by the backend
 * - Uses the existing regulatoryApi service instead of inventing API calls
 * - Keeps the existing page structure and styling approach
 */

type DownloadingFormat = ExportFormat | null;

type MetricValue = number | null | undefined;

const PERIOD_OPTIONS: Array<{
  value: RegulatoryPeriod;
  label: string;
}> = [
  {
    value: "DAILY",
    label: "Daily",
  },
  {
    value: "WEEKLY",
    label: "Weekly",
  },
  {
    value: "MONTHLY",
    label: "Monthly",
  },
  {
    value: "QUARTERLY",
    label: "Quarterly",
  },
  {
    value: "YEARLY",
    label: "Yearly",
  },
  {
    value: "CUSTOM",
    label: "Custom",
  },
];

export default function BnrReportPage() {
  // ============================================================
  // FILTERS
  // ============================================================

  const [period, setPeriod] = useState<RegulatoryPeriod>("MONTHLY");

  const [from, setFrom] = useState<string>("");

  const [to, setTo] = useState<string>("");

  // ============================================================
  // DATA
  // ============================================================

  const [summary, setSummary] = useState<BnrSummary | null>(null);

  const [financialStatement, setFinancialStatement] =
    useState<BnrFinancialStatementReport | null>(null);

  const [loanTypeBreakdown, setLoanTypeBreakdown] = useState<BreakdownRow[]>(
    [],
  );

  const [branchBreakdown, setBranchBreakdown] = useState<BreakdownRow[]>([]);

  const [genderBreakdown, setGenderBreakdown] = useState<BreakdownRow[]>([]);

  // ============================================================
  // UI STATE
  // ============================================================

  const [loading, setLoading] = useState<boolean>(true);

  const [downloadingFormat, setDownloadingFormat] =
    useState<DownloadingFormat>(null);

  const [error, setError] = useState<string | null>(null);

  // ============================================================
  // REPORT PARAMETERS
  // ============================================================

  const reportParams = useMemo<BnrReportParams>(() => {
    const params: BnrReportParams = {
      period,
    };

    if (period === "CUSTOM") {
      if (from) {
        params.from = from;
      }

      if (to) {
        params.to = to;
      }
    }

    return params;
  }, [period, from, to]);

  // ============================================================
  // VALIDATE FILTERS
  // ============================================================

  const validateFilters = useCallback((): string | null => {
    if (period !== "CUSTOM") {
      return null;
    }

    if (!from) {
      return "Please select a start date.";
    }

    if (!to) {
      return "Please select an end date.";
    }

    if (from > to) {
      return "The start date cannot be after the end date.";
    }

    return null;
  }, [period, from, to]);

  // ============================================================
  // LOAD REPORT
  // ============================================================

  const loadReport = useCallback(async (): Promise<void> => {
    const validationError = validateFilters();

    if (validationError) {
      setError(validationError);
      return;
    }

    try {
      setLoading(true);
      setError(null);

      /*
       * Load all BNR sections together.
       *
       * These endpoints are all exposed by the existing
       * /api/regulatory/bnr controller.
       */
      const [
        summaryResult,
        financialStatementResult,
        loanTypeResult,
        branchResult,
        genderResult,
      ] = await Promise.all([
        regulatoryApi.bnrSummary(reportParams),

        regulatoryApi.bnrFinancialStatement(reportParams),

        regulatoryApi.bnrByLoanType(reportParams),

        regulatoryApi.bnrByBranch(reportParams),

        regulatoryApi.bnrByGender(reportParams),
      ]);

      setSummary(summaryResult ?? null);

      setFinancialStatement(financialStatementResult ?? null);

      setLoanTypeBreakdown(Array.isArray(loanTypeResult) ? loanTypeResult : []);

      setBranchBreakdown(Array.isArray(branchResult) ? branchResult : []);

      setGenderBreakdown(Array.isArray(genderResult) ? genderResult : []);
    } catch (err) {
      console.error("Failed to load BNR report:", err);

      setError(
        regulatoryApi.getErrorMessage(err, "Failed to load the BNR report."),
      );
    } finally {
      setLoading(false);
    }
  }, [reportParams, validateFilters]);

  // ============================================================
  // INITIAL / FILTER LOAD
  // ============================================================

  useEffect(() => {
    void loadReport();
  }, [loadReport]);

  // ============================================================
  // DOWNLOAD
  // ============================================================

  const downloadReport = useCallback(
    async (format: ExportFormat): Promise<void> => {
      const validationError = validateFilters();

      if (validationError) {
        setError(validationError);
        return;
      }

      try {
        setError(null);

        setDownloadingFormat(format);

        await regulatoryApi.bnrExport(format, reportParams);
      } catch (err) {
        console.error(`Failed to download BNR ${format} report:`, err);

        setError(
          regulatoryApi.getErrorMessage(
            err,
            `Failed to download BNR ${format.toUpperCase()} report.`,
          ),
        );
      } finally {
        setDownloadingFormat(null);
      }
    },
    [reportParams, validateFilters],
  );

  const handleDownloadPdf = useCallback(async (): Promise<void> => {
    await downloadReport("pdf");
  }, [downloadReport]);

  const handleDownloadExcel = useCallback(async (): Promise<void> => {
    await downloadReport("xlsx");
  }, [downloadReport]);

  const handleDownloadCsv = useCallback(async (): Promise<void> => {
    await downloadReport("csv");
  }, [downloadReport]);

  // ============================================================
  // FORMATTERS
  // ============================================================

  const formatMoney = useCallback(
    (value: MetricValue): string => {
      const currency =
        summary?.currency || financialStatement?.currency || "RWF";

      const amount = Number(value ?? 0);

      if (!Number.isFinite(amount)) {
        return new Intl.NumberFormat("en-RW", {
          style: "currency",
          currency,
          maximumFractionDigits: 2,
        }).format(0);
      }

      return new Intl.NumberFormat("en-RW", {
        style: "currency",
        currency,
        maximumFractionDigits: 2,
      }).format(amount);
    },
    [summary?.currency, financialStatement?.currency],
  );

  const formatNumber = useCallback((value: MetricValue): string => {
    const amount = Number(value ?? 0);

    return new Intl.NumberFormat("en-US").format(
      Number.isFinite(amount) ? amount : 0,
    );
  }, []);

  const formatPercent = useCallback((value: MetricValue): string => {
    const amount = Number(value ?? 0);

    return `${(Number.isFinite(amount) ? amount : 0).toFixed(2)}%`;
  }, []);

  const formatDate = useCallback((value?: string): string => {
    if (!value) {
      return "—";
    }

    const date = new Date(value);

    if (Number.isNaN(date.getTime())) {
      return value;
    }

    return new Intl.DateTimeFormat("en-RW", {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    }).format(date);
  }, []);

  const formatDateTime = useCallback((value?: string): string => {
    if (!value) {
      return "—";
    }

    const date = new Date(value);

    if (Number.isNaN(date.getTime())) {
      return value;
    }

    return new Intl.DateTimeFormat("en-RW", {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
    }).format(date);
  }, []);

  // ============================================================
  // FINANCIAL STATEMENT HELPERS
  // ============================================================

  const getStatementRows = useCallback(
    (rows?: FinancialStatementRow[]): FinancialStatementRow[] => {
      return Array.isArray(rows) ? rows : [];
    },
    [],
  );

  const renderStatementRows = useCallback(
    (rows?: FinancialStatementRow[]) => {
      const safeRows = getStatementRows(rows);

      if (safeRows.length === 0) {
        return (
          <tr>
            <td
              colSpan={4}
              className="px-5 py-5 text-center text-sm text-gray-400"
            >
              No accounting entries for this period.
            </td>
          </tr>
        );
      }

      return safeRows.map((row, index) => (
        <tr
          key={`${row.code || row.name || "row"}-${index}`}
          className="border-t border-gray-100"
        >
          <td className="px-5 py-3 text-sm font-medium text-gray-800">
            {row.code || "—"}
          </td>

          <td className="px-5 py-3 text-sm text-gray-700">{row.name || "—"}</td>

          <td className="px-5 py-3 text-right text-sm text-gray-700">
            {formatMoney(row.balance ?? row.amount)}
          </td>

          <td className="px-5 py-3 text-right text-sm text-gray-500">
            {row.debit !== undefined || row.credit !== undefined
              ? `${formatMoney(row.debit)} / ${formatMoney(row.credit)}`
              : "—"}
          </td>
        </tr>
      ));
    },
    [formatMoney, getStatementRows],
  );

  // ============================================================
  // LOADING STATE
  // ============================================================

  if (loading && !summary) {
    return (
      <div className="min-h-screen bg-gray-50 p-6">
        <div className="mx-auto max-w-7xl">
          <div className="animate-pulse space-y-6">
            <div className="h-10 w-72 rounded bg-gray-200" />

            <div className="h-24 rounded-xl bg-gray-200" />

            <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4">
              {Array.from({ length: 8 }).map((_, index) => (
                <div key={index} className="h-32 rounded-xl bg-gray-200" />
              ))}
            </div>

            <div className="h-64 rounded-xl bg-gray-200" />

            <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
              {Array.from({ length: 3 }).map((_, index) => (
                <div key={index} className="h-64 rounded-xl bg-gray-200" />
              ))}
            </div>
          </div>
        </div>
      </div>
    );
  }

  // ============================================================
  // RENDER
  // ============================================================

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="mx-auto max-w-7xl space-y-6 p-6">
        {/* ======================================================
            HEADER
        ====================================================== */}

        <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">
              BNR Regulatory Report
            </h1>

            <p className="mt-1 text-sm text-gray-500">
              Regulatory reporting and portfolio information.
            </p>
          </div>

          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              onClick={handleDownloadPdf}
              disabled={downloadingFormat !== null}
              className="rounded-lg bg-red-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-red-700 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {downloadingFormat === "pdf"
                ? "Downloading PDF..."
                : "Download PDF"}
            </button>

            <button
              type="button"
              onClick={handleDownloadExcel}
              disabled={downloadingFormat !== null}
              className="rounded-lg bg-green-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-green-700 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {downloadingFormat === "xlsx"
                ? "Downloading Excel..."
                : "Download Excel"}
            </button>

            <button
              type="button"
              onClick={handleDownloadCsv}
              disabled={downloadingFormat !== null}
              className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {downloadingFormat === "csv"
                ? "Downloading CSV..."
                : "Download CSV"}
            </button>
          </div>
        </div>

        {/* ======================================================
            ERROR
        ====================================================== */}

        {error && (
          <div className="rounded-lg border border-red-200 bg-red-50 p-4">
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="font-semibold text-red-800">Report error</p>

                <p className="mt-1 text-sm text-red-700">{error}</p>
              </div>

              <button
                type="button"
                onClick={() => setError(null)}
                className="text-sm font-medium text-red-700 hover:text-red-900"
              >
                Dismiss
              </button>
            </div>
          </div>
        )}

        {/* ======================================================
            REPORT PERIOD
        ====================================================== */}

        <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
          <div className="mb-4">
            <h2 className="font-semibold text-gray-900">Report period</h2>

            <p className="text-sm text-gray-500">
              Select the reporting period used for the BNR report.
            </p>
          </div>

          <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
            {/* PERIOD */}

            <div>
              <label
                htmlFor="bnr-period"
                className="mb-1 block text-sm font-medium text-gray-700"
              >
                Period
              </label>

              <select
                id="bnr-period"
                value={period}
                onChange={(event) => {
                  const nextPeriod = event.target.value as RegulatoryPeriod;

                  setPeriod(nextPeriod);

                  if (nextPeriod !== "CUSTOM") {
                    setFrom("");
                    setTo("");
                  }
                }}
                className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
              >
                {PERIOD_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </div>

            {/* FROM */}

            <div>
              <label
                htmlFor="bnr-from"
                className="mb-1 block text-sm font-medium text-gray-700"
              >
                From
              </label>

              <input
                id="bnr-from"
                type="date"
                value={from}
                disabled={period !== "CUSTOM"}
                onChange={(event) => setFrom(event.target.value)}
                className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm outline-none disabled:cursor-not-allowed disabled:bg-gray-100 focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
              />
            </div>

            {/* TO */}

            <div>
              <label
                htmlFor="bnr-to"
                className="mb-1 block text-sm font-medium text-gray-700"
              >
                To
              </label>

              <input
                id="bnr-to"
                type="date"
                value={to}
                disabled={period !== "CUSTOM"}
                onChange={(event) => setTo(event.target.value)}
                className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm outline-none disabled:cursor-not-allowed disabled:bg-gray-100 focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
              />
            </div>
          </div>

          <div className="mt-4 flex items-center justify-between gap-3">
            <p className="text-xs text-gray-500">
              {period === "CUSTOM"
                ? "Custom dates are sent directly to the BNR reporting API."
                : "The backend determines the reporting date range for the selected period."}
            </p>

            <button
              type="button"
              onClick={() => void loadReport()}
              disabled={loading}
              className="rounded-lg bg-gray-900 px-5 py-2 text-sm font-medium text-white transition hover:bg-gray-800 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {loading ? "Refreshing..." : "Refresh Report"}
            </button>
          </div>
        </div>

        {/* ======================================================
            ORGANIZATION
        ====================================================== */}

        {summary && (
          <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
            <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
              <div>
                <h2 className="text-lg font-semibold text-gray-900">
                  {summary.organizationName || "Organization"}
                </h2>

                <p className="mt-1 text-sm text-gray-500">
                  BNR Institution Code:{" "}
                  {summary.bnrInstitutionCode || "Not configured"}
                </p>

                {summary.registrationNumber && (
                  <p className="mt-1 text-xs text-gray-400">
                    Registration Number: {summary.registrationNumber}
                  </p>
                )}

                {summary.institutionType && (
                  <p className="mt-1 text-xs text-gray-400">
                    Institution Type: {summary.institutionType}
                  </p>
                )}
              </div>

              <div className="text-left md:text-right">
                <p className="text-xs font-medium uppercase tracking-wide text-gray-400">
                  Reporting period
                </p>

                <p className="mt-1 text-sm font-medium text-gray-700">
                  {formatDate(summary.periodStart)}
                  {" → "}
                  {formatDate(summary.periodEnd)}
                </p>

                <p className="mt-1 text-xs text-gray-400">
                  {summary.reportPeriod || period}
                </p>
              </div>
            </div>
          </div>
        )}

        {/* ======================================================
            REPORT METADATA
        ====================================================== */}

        {summary && (
          <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
            <InfoCard
              label="Report Reference"
              value={summary.reportReference || "—"}
            />

            <InfoCard
              label="Report Date"
              value={formatDate(summary.reportDate)}
            />

            <InfoCard
              label="Generated At"
              value={formatDateTime(summary.generatedAt)}
            />
          </div>
        )}

        {/* ======================================================
            KPI CARDS
        ====================================================== */}

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <MetricCard
            label="Total Loans Issued"
            value={formatNumber(summary?.totalLoans)}
          />

          <MetricCard
            label="Loans Disbursed During Period"
            value={formatNumber(summary?.loansDisbursedDuringPeriod)}
          />

          <MetricCard
            label="Active Loans"
            value={formatNumber(summary?.activeLoans)}
          />

          <MetricCard
            label="Principal Disbursed"
            value={formatMoney(summary?.totalPrincipalDisbursed)}
          />

          <MetricCard
            label="Outstanding Principal"
            value={formatMoney(summary?.outstandingPrincipal)}
          />

          <MetricCard
            label="Total Outstanding"
            value={formatMoney(summary?.totalOutstanding)}
          />

          <MetricCard
            label="Interest Collected"
            value={formatMoney(summary?.totalInterestCollected)}
          />

          <MetricCard
            label="Total Fees Collected"
            value={formatMoney(summary?.totalFeesCollected)}
          />
        </div>

        {/* ======================================================
            COLLECTION SUMMARY
        ====================================================== */}

        <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
          <div className="mb-4">
            <h2 className="text-lg font-semibold text-gray-900">
              Collections and Outstanding Amounts
            </h2>

            <p className="mt-1 text-sm text-gray-500">
              Portfolio collection values reported by the BNR reporting service.
            </p>
          </div>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <MetricCard
              label="Principal Collected"
              value={formatMoney(summary?.totalPrincipalCollected)}
              compact
            />

            <MetricCard
              label="Interest Collected"
              value={formatMoney(summary?.totalInterestCollected)}
              compact
            />

            <MetricCard
              label="Total Amount Collected"
              value={formatMoney(summary?.totalAmountCollected)}
              compact
            />

            <MetricCard
              label="Payments"
              value={formatNumber(summary?.totalPayments)}
              compact
            />

            <MetricCard
              label="Outstanding Interest"
              value={formatMoney(summary?.outstandingInterest)}
              compact
            />

            <MetricCard
              label="Outstanding Fees"
              value={formatMoney(summary?.outstandingFees)}
              compact
            />

            <MetricCard
              label="Interest Accrued Unpaid"
              value={formatMoney(summary?.interestAccruedUnpaid)}
              compact
            />

            <MetricCard
              label="Fees Accrued Unpaid"
              value={formatMoney(summary?.feesAccruedUnpaid)}
              compact
            />
          </div>
        </div>

        {/* ======================================================
            LOAN STATUS
        ====================================================== */}

        <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
          <h2 className="mb-4 text-lg font-semibold text-gray-900">
            Loan Status
          </h2>

          <div className="grid grid-cols-2 gap-4 md:grid-cols-4 lg:grid-cols-6">
            <StatusItem label="Active" value={summary?.activeLoans} />

            <StatusItem label="Closed" value={summary?.closedLoans} />

            <StatusItem label="Paid" value={summary?.paidLoans} />

            <StatusItem label="Pending" value={summary?.pendingLoans} />

            <StatusItem label="Approved" value={summary?.approvedLoans} />

            <StatusItem label="Rejected" value={summary?.rejectedLoans} />

            <StatusItem label="Cancelled" value={summary?.cancelledLoans} />

            <StatusItem label="Overdue" value={summary?.overdueLoans} />

            <StatusItem label="Defaulted" value={summary?.defaultedLoans} />

            <StatusItem label="Written Off" value={summary?.writtenOffLoans} />

            <StatusItem
              label="Restructured"
              value={summary?.restructuredLoans}
            />

            <StatusItem
              label="Missed Payments"
              value={summary?.missedPayments}
            />
          </div>
        </div>

        {/* ======================================================
            PAR / NPL
        ====================================================== */}

        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <RiskCard
            title="Portfolio at Risk"
            ratio={formatPercent(summary?.parRatio)}
            amount={formatMoney(summary?.parAmount)}
            description="Outstanding portfolio exposed to arrears according to the BNR reporting calculation."
          />

          <RiskCard
            title="Non-Performing Loans"
            ratio={formatPercent(summary?.nplRatio)}
            amount={formatMoney(summary?.nplAmount)}
            description="NPL exposure reported by the BNR portfolio classification."
            count={summary?.nplLoanCount}
          />
        </div>

        {/* ======================================================
            PAR AGING
        ====================================================== */}

        <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
          <div className="mb-4">
            <h2 className="text-lg font-semibold text-gray-900">
              Portfolio Aging
            </h2>

            <p className="mt-1 text-sm text-gray-500">
              Portfolio exposure by overdue aging buckets.
            </p>
          </div>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <MetricCard
              label="PAR 1–30 Amount"
              value={formatMoney(summary?.par1To30Amount)}
              compact
            />

            <MetricCard
              label="PAR 31–60 Amount"
              value={formatMoney(summary?.par31To60Amount)}
              compact
            />

            <MetricCard
              label="PAR 61–90 Amount"
              value={formatMoney(summary?.par61To90Amount)}
              compact
            />

            <MetricCard
              label="PAR 91–180 Amount"
              value={formatMoney(summary?.par91To180Amount)}
              compact
            />

            <MetricCard
              label="PAR 181–365 Amount"
              value={formatMoney(summary?.par181To365Amount)}
              compact
            />

            <MetricCard
              label="PAR Over 365 Amount"
              value={formatMoney(summary?.parOver365Amount)}
              compact
            />
          </div>

          <div className="mt-5 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-5">
            <StatusItem
              label="Loans > 30 Days"
              value={summary?.loansOver30Days}
            />

            <StatusItem
              label="Loans > 60 Days"
              value={summary?.loansOver60Days}
            />

            <StatusItem
              label="Loans > 90 Days"
              value={summary?.loansOver90Days}
            />

            <StatusItem
              label="Loans > 180 Days"
              value={summary?.loansOver180Days}
            />

            <StatusItem
              label="Loans > 365 Days"
              value={summary?.loansOver365Days}
            />
          </div>
        </div>

        {/* ======================================================
            DEFAULT / PROVISION
        ====================================================== */}

        <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
          <div className="mb-4">
            <h2 className="text-lg font-semibold text-gray-900">
              Default, Write-Off and Provision
            </h2>
          </div>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <MetricCard
              label="Defaulted Amount"
              value={formatMoney(summary?.defaultedAmount)}
              compact
            />

            <MetricCard
              label="Written-Off Amount"
              value={formatMoney(summary?.writtenOffAmount)}
              compact
            />

            <MetricCard
              label="Recoveries After Write-Off"
              value={formatMoney(summary?.recoveriesAfterWriteOff)}
              compact
            />

            <MetricCard
              label="Required Provision"
              value={formatMoney(summary?.requiredProvision)}
              compact
            />

            <MetricCard
              label="Existing Provision"
              value={formatMoney(summary?.existingProvision)}
              compact
            />

            <MetricCard
              label="Provision Shortfall"
              value={formatMoney(summary?.provisionShortfall)}
              compact
            />
          </div>
        </div>

        {/* ======================================================
            BORROWER INFORMATION
        ====================================================== */}

        <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
          <div className="mb-4">
            <h2 className="text-lg font-semibold text-gray-900">
              Borrower Information
            </h2>
          </div>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <StatusItem
              label="Total Borrowers"
              value={summary?.totalBorrowers}
            />

            <StatusItem
              label="Active Borrowers"
              value={summary?.activeBorrowers}
            />

            <StatusItem label="Male Borrowers" value={summary?.maleBorrowers} />

            <StatusItem
              label="Female Borrowers"
              value={summary?.femaleBorrowers}
            />

            <StatusItem
              label="Other Gender"
              value={summary?.otherGenderBorrowers}
            />

            <StatusItem
              label="Multiple Loans"
              value={summary?.borrowersWithMultipleLoans}
            />

            <StatusItem
              label="Youth Borrowers"
              value={summary?.youthBorrowers}
            />

            <StatusItem
              label="Adult Borrowers"
              value={summary?.adultBorrowers}
            />

            <StatusItem
              label="Senior Borrowers"
              value={summary?.seniorBorrowers}
            />

            <StatusItem
              label="Credit Checked"
              value={summary?.borrowersCreditChecked}
            />

            <StatusItem
              label="Default History"
              value={summary?.borrowersWithDefaultHistory}
            />

            <StatusItem
              label="Active Listing"
              value={summary?.borrowersWithActiveListing}
            />

            <StatusItem
              label="Multiple Facilities"
              value={summary?.borrowersWithMultipleFacilities}
            />
          </div>

          <div className="mt-5">
            <MetricCard
              label="Total External Debt"
              value={formatMoney(summary?.totalExternalDebt)}
              compact
            />
          </div>
        </div>

        {/* ======================================================
            DATA QUALITY
        ====================================================== */}

        <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
          <div className="mb-4 flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
            <div>
              <h2 className="text-lg font-semibold text-gray-900">
                Data Quality
              </h2>

              <p className="mt-1 text-sm text-gray-500">
                BNR reporting data-quality indicators returned by the backend.
              </p>
            </div>

            <span
              className={`inline-flex w-fit rounded-full px-3 py-1 text-xs font-semibold ${
                summary?.reportStatus?.toUpperCase() === "READY"
                  ? "bg-green-100 text-green-700"
                  : "bg-gray-100 text-gray-700"
              }`}
            >
              {summary?.reportStatus || "Report status unavailable"}
            </span>
          </div>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-5">
            <StatusItem
              label="Loans Missing Borrower"
              value={summary?.loansMissingBorrower}
            />

            <StatusItem
              label="Borrowers Missing National ID"
              value={summary?.borrowersMissingNationalId}
            />

            <StatusItem
              label="Loans Missing Branch"
              value={summary?.loansMissingBranch}
            />

            <StatusItem
              label="Loans Missing Currency"
              value={summary?.loansMissingCurrency}
            />

            <StatusItem
              label="Missing Repayment Schedule"
              value={summary?.loansMissingRepaymentSchedule}
            />
          </div>

          {Array.isArray(summary?.dataQualityWarnings) &&
            summary.dataQualityWarnings.length > 0 && (
              <div className="mt-5 rounded-lg border border-amber-200 bg-amber-50 p-4">
                <p className="text-sm font-semibold text-amber-900">
                  Data quality warnings
                </p>

                <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-amber-800">
                  {summary.dataQualityWarnings.map((warning, index) => (
                    <li key={`${warning}-${index}`}>{warning}</li>
                  ))}
                </ul>
              </div>
            )}
        </div>

        {/* ======================================================
            BORROWERS BY GENDER
        ====================================================== */}

        <BreakdownTable
          title="Borrowers by Gender"
          rows={genderBreakdown}
          formatMoney={formatMoney}
          formatNumber={formatNumber}
        />

        {/* ======================================================
            LOANS BY TYPE
        ====================================================== */}

        <BreakdownTable
          title="Loans by Loan Type"
          rows={loanTypeBreakdown}
          formatMoney={formatMoney}
          formatNumber={formatNumber}
        />

        {/* ======================================================
            LOANS BY BRANCH
        ====================================================== */}

        <BreakdownTable
          title="Loans by Branch"
          rows={branchBreakdown}
          formatMoney={formatMoney}
          formatNumber={formatNumber}
        />

        {/* ======================================================
            FINANCIAL REPORT
        ====================================================== */}

        <div className="rounded-xl border border-gray-200 bg-white shadow-sm">
          <div className="border-b border-gray-200 p-5">
            <div className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
              <div>
                <h2 className="text-lg font-semibold text-gray-900">
                  Financial Report
                </h2>

                <p className="mt-1 text-sm text-gray-500">
                  Accounting-based financial statement returned by the BNR
                  reporting service.
                </p>
              </div>

              {financialStatement?.balanceSheetBalanced !== undefined && (
                <span
                  className={`inline-flex rounded-full px-3 py-1 text-xs font-semibold ${
                    financialStatement.balanceSheetBalanced
                      ? "bg-green-100 text-green-700"
                      : "bg-red-100 text-red-700"
                  }`}
                >
                  Balance Sheet:{" "}
                  {financialStatement.balanceSheetBalanced
                    ? "Balanced"
                    : "Not Balanced"}
                </span>
              )}
            </div>
          </div>

          {/* FINANCIAL TOTALS */}

          <div className="grid grid-cols-1 gap-4 p-5 sm:grid-cols-2 lg:grid-cols-4">
            <MetricCard
              label="Total Assets"
              value={formatMoney(financialStatement?.totalAssets)}
              compact
            />

            <MetricCard
              label="Total Liabilities"
              value={formatMoney(financialStatement?.totalLiabilities)}
              compact
            />

            <MetricCard
              label="Total Equity"
              value={formatMoney(financialStatement?.totalEquity)}
              compact
            />

            <MetricCard
              label="Net Income"
              value={formatMoney(
                financialStatement?.netIncome ??
                  financialStatement?.currentPeriodNetIncome,
              )}
              compact
            />

            <MetricCard
              label="Total Income"
              value={formatMoney(financialStatement?.totalIncome)}
              compact
            />

            <MetricCard
              label="Total Expenses"
              value={formatMoney(financialStatement?.totalExpenses)}
              compact
            />

            <MetricCard
              label="Net Change in Cash"
              value={formatMoney(financialStatement?.netChangeInCash)}
              compact
            />

            <MetricCard
              label="Cash Used for Lending"
              value={formatMoney(financialStatement?.cashUsedForLending)}
              compact
            />
          </div>

          {/* ACCOUNTING CONTROL */}

          <div className="border-t border-gray-200 p-5">
            <h3 className="mb-4 text-sm font-semibold uppercase tracking-wide text-gray-500">
              Accounting Controls
            </h3>

            <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4">
              <ControlCard
                label="Trial Balance Debit"
                value={formatMoney(financialStatement?.trialBalanceDebit)}
              />

              <ControlCard
                label="Trial Balance Credit"
                value={formatMoney(financialStatement?.trialBalanceCredit)}
              />

              <ControlCard
                label="Trial Balance"
                value={
                  financialStatement?.trialBalanceBalanced
                    ? "Balanced"
                    : "Not Balanced"
                }
                positive={financialStatement?.trialBalanceBalanced}
              />

              <ControlCard
                label="Balance Sheet"
                value={
                  financialStatement?.balanceSheetBalanced
                    ? "Balanced"
                    : "Not Balanced"
                }
                positive={financialStatement?.balanceSheetBalanced}
              />
            </div>
          </div>

          {/* STATEMENT TABLES */}

          <StatementSection
            title="Statement of Financial Position — Assets"
            rows={financialStatement?.assets}
            renderRows={renderStatementRows}
          />

          <StatementSection
            title="Statement of Financial Position — Liabilities"
            rows={financialStatement?.liabilities}
            renderRows={renderStatementRows}
          />

          <StatementSection
            title="Statement of Financial Position — Equity"
            rows={financialStatement?.equity}
            renderRows={renderStatementRows}
          />

          <StatementSection
            title="Income Statement — Income"
            rows={financialStatement?.income}
            renderRows={renderStatementRows}
          />

          <StatementSection
            title="Income Statement — Expenses"
            rows={financialStatement?.expenses}
            renderRows={renderStatementRows}
          />
        </div>

        {/* ======================================================
            REPORT FOOTER
        ====================================================== */}

        <div className="pb-8 text-center text-xs text-gray-400">
          <p>BNR regulatory report • {summary?.reportPeriod || period}</p>

          {summary?.submissionReference && (
            <p className="mt-1">
              Submission Reference: {summary.submissionReference}
            </p>
          )}
        </div>
      </div>
    </div>
  );
}

// ============================================================
// METRIC CARD
// ============================================================

function MetricCard({
  label,
  value,
  compact = false,
}: {
  label: string;
  value: string;
  compact?: boolean;
}) {
  return (
    <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
      <p className="text-sm text-gray-500">{label}</p>

      <p
        className={
          compact
            ? "mt-2 text-lg font-bold text-gray-900"
            : "mt-2 text-2xl font-bold text-gray-900"
        }
      >
        {value}
      </p>
    </div>
  );
}

// ============================================================
// INFO CARD
// ============================================================

function InfoCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
      <p className="text-xs font-semibold uppercase tracking-wide text-gray-400">
        {label}
      </p>

      <p className="mt-1 truncate text-sm font-medium text-gray-800">{value}</p>
    </div>
  );
}

// ============================================================
// STATUS ITEM
// ============================================================

function StatusItem({ label, value }: { label: string; value?: number }) {
  return (
    <div className="rounded-lg bg-gray-50 p-4">
      <p className="text-sm text-gray-500">{label}</p>

      <p className="mt-1 text-xl font-semibold text-gray-900">
        {new Intl.NumberFormat("en-US").format(Number(value ?? 0))}
      </p>
    </div>
  );
}

// ============================================================
// RISK CARD
// ============================================================

function RiskCard({
  title,
  ratio,
  amount,
  description,
  count,
}: {
  title: string;
  ratio: string;
  amount: string;
  description: string;
  count?: number;
}) {
  return (
    <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 className="text-lg font-semibold text-gray-900">{title}</h2>

          <p className="mt-1 text-sm text-gray-500">{description}</p>
        </div>

        <div className="text-right">
          <p className="text-2xl font-bold text-gray-900">{ratio}</p>

          <p className="mt-1 text-xs text-gray-500">{amount}</p>
        </div>
      </div>

      {count !== undefined && (
        <div className="mt-4 rounded-lg bg-gray-50 p-3">
          <p className="text-xs uppercase tracking-wide text-gray-400">
            Loan Count
          </p>

          <p className="mt-1 text-lg font-semibold text-gray-900">
            {new Intl.NumberFormat("en-US").format(Number(count))}
          </p>
        </div>
      )}
    </div>
  );
}

// ============================================================
// CONTROL CARD
// ============================================================

function ControlCard({
  label,
  value,
  positive,
}: {
  label: string;
  value: string;
  positive?: boolean;
}) {
  return (
    <div className="rounded-lg border border-gray-100 bg-gray-50 p-4">
      <p className="text-xs font-semibold uppercase tracking-wide text-gray-400">
        {label}
      </p>

      <p
        className={`mt-1 text-sm font-semibold ${
          positive === undefined
            ? "text-gray-900"
            : positive
              ? "text-green-700"
              : "text-red-700"
        }`}
      >
        {value}
      </p>
    </div>
  );
}

// ============================================================
// BREAKDOWN TABLE
// ============================================================

function BreakdownTable({
  title,
  rows,
  formatMoney,
  formatNumber,
}: {
  title: string;

  rows: BreakdownRow[];

  formatMoney: (value?: number) => string;

  formatNumber: (value?: number) => string;
}) {
  return (
    <div className="rounded-xl border border-gray-200 bg-white shadow-sm">
      <div className="border-b border-gray-200 p-5">
        <h2 className="text-lg font-semibold text-gray-900">{title}</h2>
      </div>

      {!rows || rows.length === 0 ? (
        <div className="p-6 text-center text-sm text-gray-500">
          No data available for this period.
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th
                  scope="col"
                  className="px-5 py-3 text-left text-xs font-semibold uppercase tracking-wide text-gray-500"
                >
                  Category
                </th>

                <th
                  scope="col"
                  className="px-5 py-3 text-right text-xs font-semibold uppercase tracking-wide text-gray-500"
                >
                  Count
                </th>

                <th
                  scope="col"
                  className="px-5 py-3 text-right text-xs font-semibold uppercase tracking-wide text-gray-500"
                >
                  Amount
                </th>
              </tr>
            </thead>

            <tbody className="divide-y divide-gray-200 bg-white">
              {rows.map((row, index) => (
                <tr key={`${row.label}-${index}`} className="hover:bg-gray-50">
                  <td className="px-5 py-3 text-sm font-medium text-gray-900">
                    {row.label || "—"}
                  </td>

                  <td className="px-5 py-3 text-right text-sm text-gray-700">
                    {formatNumber(row.count)}
                  </td>

                  <td className="px-5 py-3 text-right text-sm text-gray-700">
                    {formatMoney(row.amount)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

// ============================================================
// STATEMENT SECTION
// ============================================================

function StatementSection({
  title,
  rows,
  renderRows,
}: {
  title: string;
  rows?: FinancialStatementRow[];
  renderRows: (rows?: FinancialStatementRow[]) => React.ReactNode;
}) {
  return (
    <div className="border-t border-gray-200">
      <div className="border-b border-gray-100 bg-gray-50 px-5 py-4">
        <h3 className="text-sm font-semibold text-gray-800">{title}</h3>
      </div>

      <div className="overflow-x-auto">
        <table className="min-w-full">
          <thead>
            <tr className="bg-white">
              <th className="px-5 py-3 text-left text-xs font-semibold uppercase tracking-wide text-gray-400">
                Code
              </th>

              <th className="px-5 py-3 text-left text-xs font-semibold uppercase tracking-wide text-gray-400">
                Account
              </th>

              <th className="px-5 py-3 text-right text-xs font-semibold uppercase tracking-wide text-gray-400">
                Balance
              </th>

              <th className="px-5 py-3 text-right text-xs font-semibold uppercase tracking-wide text-gray-400">
                Debit / Credit
              </th>
            </tr>
          </thead>

          <tbody>{renderRows(rows)}</tbody>
        </table>
      </div>
    </div>
  );
}
