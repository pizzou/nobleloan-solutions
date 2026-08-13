"use client";

import { useCallback, useEffect, useState } from "react";
import API from "@/services/api";

type BreakdownRow = {
  [key: string]: any;
};

type BnrSummary = {
  [key: string]: any;
};

type BnrFinancialStatement = {
  assets?: BreakdownRow[];
  liabilities?: BreakdownRow[];
  equity?: BreakdownRow[];
  income?: BreakdownRow[];
  expenses?: BreakdownRow[];

  totalAssets?: number;
  totalLiabilities?: number;
  totalEquity?: number;
  currentPeriodNetIncome?: number;

  totalIncome?: number;
  totalExpenses?: number;
  netIncome?: number;

  trialBalanceDebit?: number;
  trialBalanceCredit?: number;
  trialBalanceBalanced?: boolean;

  cashUsedForLending?: number;
  cashFromCollections?: number;
  cashFromFees?: number;
  otherCashMovement?: number;
  netChangeInCash?: number;

  balanceSheetBalanced?: boolean;

  [key: string]: any;
};

type Period = "MONTHLY" | "QUARTERLY" | "ANNUAL" | "CUSTOM";

const money = (value: unknown) => {
  const n = Number(value ?? 0);

  return new Intl.NumberFormat("en-RW", {
    style: "currency",
    currency: "RWF",
    maximumFractionDigits: 2,
  }).format(Number.isFinite(n) ? n : 0);
};

const num = (value: unknown) => {
  const n = Number(value ?? 0);
  return Number.isFinite(n) ? n : 0;
};

const unwrap = <T,>(value: any): T => {
  if (value && typeof value === "object" && value.data !== undefined) {
    return value.data;
  }

  return value;
};

const rows = (value: any): BreakdownRow[] => {
  const result = unwrap<any>(value);

  if (Array.isArray(result)) {
    return result;
  }

  return [];
};

export default function ReportsPage() {
  const [period, setPeriod] = useState<Period>("MONTHLY");

  const [from, setFrom] = useState(
    new Date(new Date().getFullYear(), new Date().getMonth(), 1)
      .toISOString()
      .slice(0, 10),
  );

  const [to, setTo] = useState(new Date().toISOString().slice(0, 10));

  const [summary, setSummary] = useState<BnrSummary>({});

  const [financialStatement, setFinancialStatement] =
    useState<BnrFinancialStatement>({});

  const [byLoanType, setByLoanType] = useState<BreakdownRow[]>([]);

  const [byBranch, setByBranch] = useState<BreakdownRow[]>([]);

  const [byGender, setByGender] = useState<BreakdownRow[]>([]);

  const [loading, setLoading] = useState(false);

  const [error, setError] = useState("");

  const query = () => {
    const params: Record<string, string> = {
      period,
    };

    if (period === "CUSTOM") {
      params.from = from;
      params.to = to;
    }

    return params;
  };

  const loadReports = useCallback(async () => {
    setLoading(true);
    setError("");

    try {
      const params = query();

      const [
        summaryResponse,
        statementResponse,
        loanTypeResponse,
        branchResponse,
        genderResponse,
      ] = await Promise.all([
        API.get("/regulatory/bnr/summary", {
          params,
        }),

        API.get("/regulatory/bnr/financial-statement", {
          params,
        }),

        API.get("/regulatory/bnr/by-loan-type", {
          params,
        }),

        API.get("/regulatory/bnr/by-branch", {
          params,
        }),

        API.get("/regulatory/bnr/by-gender", {
          params,
        }),
      ]);

      setSummary(unwrap<BnrSummary>(summaryResponse.data) || {});

      setFinancialStatement(
        unwrap<BnrFinancialStatement>(statementResponse.data) || {},
      );

      setByLoanType(rows(loanTypeResponse.data));

      setByBranch(rows(branchResponse.data));

      setByGender(rows(genderResponse.data));
    } catch (err: any) {
      setError(
        err?.response?.data?.message ||
          err?.response?.data?.error ||
          err?.message ||
          "Unable to load regulatory reports.",
      );
    } finally {
      setLoading(false);
    }
  }, [period, from, to]);

  useEffect(() => {
    loadReports();
  }, [loadReports]);

  const exportFinancialStatement = async (format: "xlsx" | "csv" | "pdf") => {
    try {
      const response = await API.get(
        "/regulatory/bnr/financial-statement/export",
        {
          params: {
            ...query(),
            format,
          },
          responseType: "blob",
        },
      );

      const blob =
        response.data instanceof Blob
          ? response.data
          : new Blob([response.data]);

      const url = window.URL.createObjectURL(blob);

      const anchor = document.createElement("a");

      anchor.href = url;

      anchor.download = `BNR-Financial-Statement-${new Date()
        .toISOString()
        .slice(0, 10)}.${format}`;

      document.body.appendChild(anchor);

      anchor.click();
      anchor.remove();

      window.URL.revokeObjectURL(url);
    } catch (err: any) {
      setError(err?.message || `Unable to export ${format.toUpperCase()}.`);
    }
  };

  return (
    <main className="min-h-screen bg-[#F6F8FB]">
      <div className="mx-auto max-w-[1600px] px-4 py-6 sm:px-6 lg:px-8">
        <div className="mb-7 flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <div className="text-[11px] font-black uppercase tracking-[0.2em] text-emerald-700">
              Regulatory reporting
            </div>

            <h1 className="mt-2 text-3xl font-black tracking-tight text-slate-950">
              BNR Reports
            </h1>

            <p className="mt-2 max-w-3xl text-sm text-slate-500">
              Portfolio, financial statement and breakdown reporting for
              management, audit and regulatory review.
            </p>
          </div>

          <button
            onClick={loadReports}
            disabled={loading}
            className="rounded-xl bg-slate-950 px-5 py-3 text-sm font-black text-white disabled:opacity-50"
          >
            {loading ? "Refreshing…" : "Refresh reports"}
          </button>
        </div>

        {error && (
          <div className="mb-5 rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-semibold text-red-700">
            {error}
          </div>
        )}

        <section className="mb-6 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="grid gap-4 lg:grid-cols-[220px_180px_180px_1fr]">
            <div>
              <label className="mb-2 block text-[10px] font-black uppercase tracking-wider text-slate-400">
                Reporting period
              </label>

              <select
                value={period}
                onChange={(e) => setPeriod(e.target.value as Period)}
                className="w-full rounded-xl border border-slate-200 px-3 py-3 text-sm font-bold"
              >
                <option value="MONTHLY">Monthly</option>

                <option value="QUARTERLY">Quarterly</option>

                <option value="ANNUAL">Annual</option>

                <option value="CUSTOM">Custom</option>
              </select>
            </div>

            {period === "CUSTOM" && (
              <>
                <div>
                  <label className="mb-2 block text-[10px] font-black uppercase tracking-wider text-slate-400">
                    From
                  </label>

                  <input
                    type="date"
                    value={from}
                    onChange={(e) => setFrom(e.target.value)}
                    className="w-full rounded-xl border border-slate-200 px-3 py-3 text-sm"
                  />
                </div>

                <div>
                  <label className="mb-2 block text-[10px] font-black uppercase tracking-wider text-slate-400">
                    To
                  </label>

                  <input
                    type="date"
                    value={to}
                    onChange={(e) => setTo(e.target.value)}
                    className="w-full rounded-xl border border-slate-200 px-3 py-3 text-sm"
                  />
                </div>
              </>
            )}

            <div className="flex items-end gap-2">
              <button
                onClick={() => loadReports()}
                className="rounded-xl bg-[#0D6B3E] px-5 py-3 text-sm font-black text-white"
              >
                Run report
              </button>

              <button
                onClick={() => exportFinancialStatement("xlsx")}
                className="rounded-xl border border-slate-200 bg-white px-4 py-3 text-xs font-black text-slate-700"
              >
                XLSX
              </button>

              <button
                onClick={() => exportFinancialStatement("csv")}
                className="rounded-xl border border-slate-200 bg-white px-4 py-3 text-xs font-black text-slate-700"
              >
                CSV
              </button>

              <button
                onClick={() => exportFinancialStatement("pdf")}
                className="rounded-xl border border-slate-200 bg-white px-4 py-3 text-xs font-black text-slate-700"
              >
                PDF
              </button>
            </div>
          </div>
        </section>

        <section className="mb-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Metric
            label="Total assets"
            value={money(financialStatement.totalAssets)}
          />

          <Metric
            label="Total liabilities"
            value={money(financialStatement.totalLiabilities)}
          />

          <Metric
            label="Total equity"
            value={money(financialStatement.totalEquity)}
          />

          <Metric
            label="Net income"
            value={money(
              financialStatement.netIncome ??
                financialStatement.currentPeriodNetIncome,
            )}
          />
        </section>

        <section className="mb-6 grid gap-6 xl:grid-cols-2">
          <Statement title="Assets" rows={financialStatement.assets} />

          <Statement
            title="Liabilities"
            rows={financialStatement.liabilities}
          />

          <Statement title="Equity" rows={financialStatement.equity} />

          <Statement title="Income" rows={financialStatement.income} />

          <Statement title="Expenses" rows={financialStatement.expenses} />

          <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
            <div className="text-[10px] font-black uppercase tracking-[0.16em] text-blue-700">
              Control
            </div>

            <h2 className="mt-1 text-lg font-black text-slate-950">
              Accounting controls
            </h2>

            <div className="mt-5 grid gap-3 sm:grid-cols-2">
              <Control
                label="Balance sheet"
                value={
                  financialStatement.balanceSheetBalanced
                    ? "BALANCED"
                    : "CHECK REQUIRED"
                }
                good={!!financialStatement.balanceSheetBalanced}
              />

              <Control
                label="Trial balance"
                value={
                  financialStatement.trialBalanceBalanced
                    ? "BALANCED"
                    : "CHECK REQUIRED"
                }
                good={!!financialStatement.trialBalanceBalanced}
              />

              <Control
                label="Total debit"
                value={money(financialStatement.trialBalanceDebit)}
              />

              <Control
                label="Total credit"
                value={money(financialStatement.trialBalanceCredit)}
              />
            </div>
          </section>
        </section>

        <section className="mb-6 grid gap-6 xl:grid-cols-3">
          <Breakdown title="By loan type" rows={byLoanType} />

          <Breakdown title="By branch" rows={byBranch} />

          <Breakdown title="By gender" rows={byGender} />
        </section>

        <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <div className="mb-5">
            <div className="text-[10px] font-black uppercase tracking-[0.16em] text-purple-700">
              Portfolio summary
            </div>

            <h2 className="mt-1 text-lg font-black text-slate-950">
              BNR summary
            </h2>
          </div>

          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4 xl:grid-cols-6">
            {Object.entries(summary || {})
              .filter(([, value]) => typeof value !== "object")
              .map(([key, value]) => (
                <div key={key} className="rounded-xl bg-slate-50 p-4">
                  <div className="text-[10px] font-black uppercase tracking-wider text-slate-400">
                    {key}
                  </div>

                  <div className="mt-2 text-lg font-black text-slate-900">
                    {typeof value === "number" ? money(value) : String(value)}
                  </div>
                </div>
              ))}
          </div>
        </section>
      </div>
    </main>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="text-[10px] font-black uppercase tracking-[0.16em] text-slate-400">
        {label}
      </div>

      <div className="mt-3 truncate text-xl font-black text-slate-950">
        {value}
      </div>
    </div>
  );
}

function Statement({ title, rows }: { title: string; rows?: BreakdownRow[] }) {
  const data = Array.isArray(rows) ? rows : [];

  return (
    <section className="rounded-2xl border border-slate-200 bg-white shadow-sm">
      <div className="border-b border-slate-200 p-5">
        <h2 className="text-lg font-black text-slate-950">{title}</h2>
      </div>

      <div className="divide-y divide-slate-100">
        {data.length === 0 ? (
          <div className="p-8 text-center text-xs text-slate-400">
            No entries.
          </div>
        ) : (
          data.map((row, index) => {
            const name =
              row.name ?? row.account ?? row.code ?? `Entry ${index + 1}`;

            const value = row.balance ?? row.amount ?? row.value ?? 0;

            return (
              <div
                key={index}
                className="flex items-center justify-between gap-4 px-5 py-4"
              >
                <div>
                  <div className="text-xs font-black text-slate-800">
                    {name}
                  </div>

                  {row.code && row.name && (
                    <div className="mt-1 text-[10px] text-slate-400">
                      {row.code}
                    </div>
                  )}
                </div>

                <div className="text-sm font-black text-slate-900">
                  {money(value)}
                </div>
              </div>
            );
          })
        )}
      </div>
    </section>
  );
}

function Breakdown({ title, rows }: { title: string; rows: BreakdownRow[] }) {
  return (
    <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
      <div className="border-b border-slate-200 p-5">
        <h2 className="text-lg font-black text-slate-950">{title}</h2>
      </div>

      <div className="overflow-x-auto">
        <table className="min-w-full">
          <thead className="bg-slate-50">
            <tr className="text-left text-[10px] font-black uppercase tracking-wider text-slate-400">
              {rows[0] &&
                Object.keys(rows[0])
                  .slice(0, 3)
                  .map((key) => (
                    <th key={key} className="px-5 py-3">
                      {key}
                    </th>
                  ))}
            </tr>
          </thead>

          <tbody>
            {rows.length === 0 ? (
              <tr>
                <td className="px-5 py-10 text-center text-xs text-slate-400">
                  No data.
                </td>
              </tr>
            ) : (
              rows.map((row, index) => (
                <tr key={index} className="border-t border-slate-100">
                  {Object.keys(row)
                    .slice(0, 3)
                    .map((key) => {
                      const value = row[key];

                      return (
                        <td
                          key={key}
                          className="px-5 py-3 text-xs font-semibold text-slate-600"
                        >
                          {typeof value === "number"
                            ? money(value)
                            : String(value ?? "—")}
                        </td>
                      );
                    })}
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function Control({
  label,
  value,
  good,
}: {
  label: string;
  value: string;
  good?: boolean;
}) {
  return (
    <div className="rounded-xl bg-slate-50 p-4">
      <div className="text-[10px] font-black uppercase tracking-wider text-slate-400">
        {label}
      </div>

      <div
        className={`mt-2 text-sm font-black ${
          good === undefined
            ? "text-slate-900"
            : good
              ? "text-emerald-700"
              : "text-red-600"
        }`}
      >
        {value}
      </div>
    </div>
  );
}
