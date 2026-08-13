"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { accountingApi, bankAccountApi } from "@/services/api";

type Account = {
  id: number;
  code?: string;
  name?: string;
  type?: string;
  normalBalance?: string;
  active?: boolean;
};

type JournalEntry = {
  id: number;
  entryDate?: string;
  sourceType?: string;
  sourceId?: string;
  reference?: string;
  description?: string;
  totalDebit?: number;
  totalCredit?: number;
  reversed?: boolean;
};

type GenericReport = Record<string, any>;

const money = (value: unknown) => {
  const n = Number(value ?? 0);

  return new Intl.NumberFormat("en-RW", {
    style: "currency",
    currency: "RWF",
    maximumFractionDigits: 2,
  }).format(Number.isFinite(n) ? n : 0);
};

const date = (value?: string) => {
  if (!value) return "—";

  const d = new Date(value);

  if (Number.isNaN(d.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat("en-RW", {
    year: "numeric",
    month: "short",
    day: "2-digit",
  }).format(d);
};

const numberValue = (value: unknown) => {
  const n = Number(value ?? 0);
  return Number.isFinite(n) ? n : 0;
};

const arrayValue = <T,>(value: unknown): T[] =>
  Array.isArray(value) ? value : [];

const extractReport = (value: any): GenericReport => {
  if (
    value &&
    typeof value === "object" &&
    value.data &&
    typeof value.data === "object"
  ) {
    return value.data;
  }

  return value || {};
};

const findNumber = (obj: any, keys: string[]) => {
  for (const key of keys) {
    if (obj && obj[key] !== undefined && obj[key] !== null) {
      return numberValue(obj[key]);
    }
  }

  return 0;
};

export default function AccountingPage() {
  const [accounts, setAccounts] = useState<Account[]>([]);

  const [journal, setJournal] = useState<JournalEntry[]>([]);

  const [trialBalance, setTrialBalance] = useState<GenericReport>({});

  const [balanceSheet, setBalanceSheet] = useState<GenericReport>({});

  const [profitLoss, setProfitLoss] = useState<GenericReport>({});

  const [cashFlow, setCashFlow] = useState<GenericReport>({});

  const [loading, setLoading] = useState(true);

  const [error, setError] = useState("");

  const [from, setFrom] = useState(
    new Date(new Date().getFullYear(), new Date().getMonth(), 1)
      .toISOString()
      .slice(0, 10),
  );

  const [to, setTo] = useState(new Date().toISOString().slice(0, 10));

  const [selectedAccount, setSelectedAccount] = useState<number | null>(null);

  const [ledger, setLedger] = useState<any[]>([]);

  const [accountModal, setAccountModal] = useState(false);

  const [newCode, setNewCode] = useState("");

  const [newName, setNewName] = useState("");

  const [newType, setNewType] = useState("ASSET");

  const [newNormalBalance, setNewNormalBalance] = useState("DEBIT");

  const [creatingAccount, setCreatingAccount] = useState(false);

  const [reverseId, setReverseId] = useState<number | null>(null);

  const [reverseReason, setReverseReason] = useState("");

  const [reversing, setReversing] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");

    try {
      const [coa, journalResult, trial, balance, pnl, cash] = await Promise.all(
        [
          accountingApi.chartOfAccounts(),
          accountingApi.journal(),
          accountingApi.trialBalance(),
          accountingApi.balanceSheet(),
          accountingApi.profitAndLoss(from, to),
          accountingApi.cashFlow(from, to),
        ],
      );

      setAccounts(arrayValue<Account>(coa));

      setJournal(arrayValue<JournalEntry>(journalResult));

      setTrialBalance(extractReport(trial));

      setBalanceSheet(extractReport(balance));

      setProfitLoss(extractReport(pnl));

      setCashFlow(extractReport(cash));
    } catch (err: any) {
      setError(err?.message || "Unable to load accounting data.");
    } finally {
      setLoading(false);
    }
  }, [from, to]);

  useEffect(() => {
    load();
  }, [load]);

  const loadLedger = async (accountId: number) => {
    setSelectedAccount(accountId);

    try {
      const result = await accountingApi.ledger(accountId);

      setLedger(arrayValue<any>(result));
    } catch (err: any) {
      setError(err?.message || "Unable to load ledger.");
    }
  };

  const createAccount = async () => {
    if (!newCode.trim() || !newName.trim()) {
      setError("Account code and name are required.");
      return;
    }

    setCreatingAccount(true);
    setError("");

    try {
      await accountingApi.createAccount({
        code: newCode.trim(),
        name: newName.trim(),
        type: newType,
        normalBalance: newNormalBalance,
      });

      setNewCode("");
      setNewName("");
      setAccountModal(false);

      await load();
    } catch (err: any) {
      setError(err?.message || "Unable to create account.");
    } finally {
      setCreatingAccount(false);
    }
  };

  const reverseEntry = async () => {
    if (!reverseId) return;

    if (!reverseReason.trim()) {
      setError("A reversal reason is required.");
      return;
    }

    setReversing(true);
    setError("");

    try {
      await accountingApi.reverseEntry(reverseId, reverseReason.trim());

      setReverseId(null);
      setReverseReason("");

      await load();
    } catch (err: any) {
      setError(err?.message || "Unable to reverse journal entry.");
    } finally {
      setReversing(false);
    }
  };

  const totals = useMemo(() => {
    const debit = findNumber(trialBalance, [
      "totalDebit",
      "totalDebits",
      "debit",
    ]);

    const credit = findNumber(trialBalance, [
      "totalCredit",
      "totalCredits",
      "credit",
    ]);

    const netIncome = findNumber(profitLoss, [
      "netIncome",
      "netProfit",
      "profit",
    ]);

    const assets = findNumber(balanceSheet, ["totalAssets", "assets"]);

    const cash = findNumber(cashFlow, [
      "netChangeInCash",
      "cashBalance",
      "closingCash",
    ]);

    return {
      debit,
      credit,
      netIncome,
      assets,
      cash,
      balanced: Math.abs(debit - credit) < 0.005,
    };
  }, [trialBalance, profitLoss, balanceSheet, cashFlow]);

  return (
    <main className="min-h-screen bg-[#F6F8FB]">
      <div className="mx-auto max-w-[1600px] px-4 py-6 sm:px-6 lg:px-8">
        <div className="mb-7 flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <div className="text-[11px] font-black uppercase tracking-[0.2em] text-emerald-700">
              Finance
            </div>

            <h1 className="mt-2 text-3xl font-black tracking-tight text-slate-950">
              Accounting
            </h1>

            <p className="mt-2 text-sm text-slate-500">
              General ledger, chart of accounts, journal control and financial
              statements.
            </p>
          </div>

          <button
            onClick={load}
            className="rounded-xl bg-slate-950 px-5 py-3 text-sm font-black text-white"
          >
            Refresh
          </button>
        </div>

        {error && (
          <div className="mb-5 rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-semibold text-red-700">
            {error}
          </div>
        )}

        <section className="mb-6 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
          <div className="grid gap-3 md:grid-cols-[180px_180px_1fr]">
            <div>
              <label className="mb-2 block text-[10px] font-black uppercase text-slate-400">
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
              <label className="mb-2 block text-[10px] font-black uppercase text-slate-400">
                To
              </label>

              <input
                type="date"
                value={to}
                onChange={(e) => setTo(e.target.value)}
                className="w-full rounded-xl border border-slate-200 px-3 py-3 text-sm"
              />
            </div>

            <div className="flex items-end">
              <div className="rounded-xl bg-slate-50 px-4 py-3 text-xs font-semibold text-slate-500">
                Reporting period:{" "}
                <span className="font-black text-slate-800">
                  {from} → {to}
                </span>
              </div>
            </div>
          </div>
        </section>

        <section className="mb-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
          <Metric label="Total debit" value={money(totals.debit)} />

          <Metric label="Total credit" value={money(totals.credit)} />

          <Metric label="Net income" value={money(totals.netIncome)} />

          <Metric label="Total assets" value={money(totals.assets)} />

          <Metric
            label="Trial balance"
            value={totals.balanced ? "BALANCED" : "OUT OF BALANCE"}
            danger={!totals.balanced}
          />
        </section>

        <div className="grid gap-6 xl:grid-cols-[460px_1fr]">
          <section className="rounded-2xl border border-slate-200 bg-white shadow-sm">
            <div className="flex items-center justify-between border-b border-slate-200 p-5">
              <div>
                <div className="text-[10px] font-black uppercase tracking-[0.16em] text-emerald-700">
                  General ledger
                </div>

                <h2 className="mt-1 text-lg font-black text-slate-950">
                  Chart of accounts
                </h2>
              </div>

              <button
                onClick={() => setAccountModal(true)}
                className="rounded-xl bg-[#0D6B3E] px-3 py-2 text-xs font-black text-white"
              >
                + Account
              </button>
            </div>

            <div className="max-h-[650px] overflow-y-auto">
              {loading ? (
                <div className="p-10 text-center text-sm text-slate-400">
                  Loading accounts…
                </div>
              ) : accounts.length === 0 ? (
                <div className="p-10 text-center text-sm text-slate-400">
                  No accounts found.
                </div>
              ) : (
                accounts.map((account) => (
                  <button
                    key={account.id}
                    onClick={() => loadLedger(account.id)}
                    className={`w-full border-b border-slate-100 px-5 py-4 text-left hover:bg-slate-50 ${
                      selectedAccount === account.id ? "bg-emerald-50" : ""
                    }`}
                  >
                    <div className="flex items-center justify-between">
                      <span className="font-black text-slate-900">
                        {account.code}
                      </span>

                      <span className="text-[10px] font-black uppercase text-slate-400">
                        {account.type}
                      </span>
                    </div>

                    <div className="mt-1 text-sm font-semibold text-slate-600">
                      {account.name}
                    </div>

                    <div className="mt-1 text-[10px] text-slate-400">
                      Normal balance: {account.normalBalance}
                    </div>
                  </button>
                ))
              )}
            </div>
          </section>

          <div className="space-y-6">
            <section className="rounded-2xl border border-slate-200 bg-white shadow-sm">
              <div className="border-b border-slate-200 p-5">
                <div className="text-[10px] font-black uppercase tracking-[0.16em] text-blue-700">
                  Journal
                </div>

                <h2 className="mt-1 text-lg font-black text-slate-950">
                  Recent journal entries
                </h2>
              </div>

              <div className="overflow-x-auto">
                <table className="min-w-[850px] w-full">
                  <thead className="bg-slate-50">
                    <tr className="text-left text-[10px] font-black uppercase tracking-wider text-slate-400">
                      <th className="px-5 py-4">Date</th>
                      <th className="px-5 py-4">Reference</th>
                      <th className="px-5 py-4">Source</th>
                      <th className="px-5 py-4">Description</th>
                      <th className="px-5 py-4">Status</th>
                      <th className="px-5 py-4">Action</th>
                    </tr>
                  </thead>

                  <tbody>
                    {journal.length === 0 ? (
                      <tr>
                        <td
                          colSpan={6}
                          className="px-5 py-12 text-center text-sm text-slate-400"
                        >
                          No journal entries.
                        </td>
                      </tr>
                    ) : (
                      journal.slice(0, 50).map((entry) => (
                        <tr
                          key={entry.id}
                          className="border-t border-slate-100"
                        >
                          <td className="px-5 py-4 text-xs font-semibold text-slate-500">
                            {date(entry.entryDate)}
                          </td>

                          <td className="px-5 py-4">
                            <div className="text-xs font-black text-slate-800">
                              {entry.reference || `#${entry.id}`}
                            </div>
                          </td>

                          <td className="px-5 py-4">
                            <div className="text-[10px] font-black uppercase text-slate-500">
                              {entry.sourceType}
                            </div>

                            <div className="mt-1 text-[10px] text-slate-400">
                              {entry.sourceId}
                            </div>
                          </td>

                          <td className="max-w-xs px-5 py-4 text-xs font-semibold text-slate-600">
                            {entry.description}
                          </td>

                          <td className="px-5 py-4">
                            <span className="rounded-full bg-slate-100 px-2.5 py-1 text-[10px] font-black text-slate-600">
                              {entry.reversed ? "REVERSED" : "POSTED"}
                            </span>
                          </td>

                          <td className="px-5 py-4">
                            {!entry.reversed && (
                              <button
                                onClick={() => setReverseId(entry.id)}
                                className="rounded-lg border border-red-200 px-3 py-2 text-[10px] font-black text-red-600 hover:bg-red-50"
                              >
                                Reverse
                              </button>
                            )}
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </section>

            {selectedAccount && (
              <section className="rounded-2xl border border-slate-200 bg-white shadow-sm">
                <div className="border-b border-slate-200 p-5">
                  <div className="text-[10px] font-black uppercase tracking-[0.16em] text-purple-700">
                    Ledger
                  </div>

                  <h2 className="mt-1 text-lg font-black text-slate-950">
                    Account #{selectedAccount}
                  </h2>
                </div>

                <div className="max-h-[400px] overflow-auto">
                  {ledger.length === 0 ? (
                    <div className="p-10 text-center text-sm text-slate-400">
                      No ledger entries found.
                    </div>
                  ) : (
                    <pre className="overflow-auto p-5 text-xs text-slate-700">
                      {JSON.stringify(ledger, null, 2)}
                    </pre>
                  )}
                </div>
              </section>
            )}

            <section className="grid gap-4 md:grid-cols-3">
              <ReportCard title="Balance sheet" data={balanceSheet} />

              <ReportCard title="Profit & loss" data={profitLoss} />

              <ReportCard title="Cash flow" data={cashFlow} />
            </section>
          </div>
        </div>
      </div>

      {accountModal && (
        <Modal
          title="Create chart-of-accounts entry"
          onClose={() => setAccountModal(false)}
        >
          <div className="space-y-4">
            <Field
              label="Account code"
              value={newCode}
              onChange={setNewCode}
              placeholder="e.g. 1250"
            />

            <Field
              label="Account name"
              value={newName}
              onChange={setNewName}
              placeholder="e.g. Mobile Money"
            />

            <Select
              label="Account type"
              value={newType}
              onChange={setNewType}
              options={["ASSET", "LIABILITY", "EQUITY", "INCOME", "EXPENSE"]}
            />

            <Select
              label="Normal balance"
              value={newNormalBalance}
              onChange={setNewNormalBalance}
              options={["DEBIT", "CREDIT"]}
            />

            <button
              onClick={createAccount}
              disabled={creatingAccount}
              className="w-full rounded-xl bg-[#0D6B3E] px-5 py-3 font-black text-white disabled:opacity-50"
            >
              {creatingAccount ? "Creating…" : "Create account"}
            </button>
          </div>
        </Modal>
      )}

      {reverseId && (
        <Modal
          title={`Reverse journal entry #${reverseId}`}
          onClose={() => {
            if (!reversing) {
              setReverseId(null);
              setReverseReason("");
            }
          }}
        >
          <div className="space-y-4">
            <textarea
              rows={5}
              value={reverseReason}
              onChange={(e) => setReverseReason(e.target.value)}
              placeholder="Enter the reason for reversal…"
              className="w-full resize-none rounded-xl border border-slate-200 px-4 py-3 text-sm"
            />

            <button
              onClick={reverseEntry}
              disabled={reversing}
              className="w-full rounded-xl bg-red-600 px-5 py-3 font-black text-white disabled:opacity-50"
            >
              {reversing ? "Reversing…" : "Confirm reversal"}
            </button>
          </div>
        </Modal>
      )}
    </main>
  );
}

function Metric({
  label,
  value,
  danger = false,
}: {
  label: string;
  value: string;
  danger?: boolean;
}) {
  return (
    <div
      className={`rounded-2xl border bg-white p-5 shadow-sm ${
        danger ? "border-red-200" : "border-slate-200"
      }`}
    >
      <div className="text-[10px] font-black uppercase tracking-[0.16em] text-slate-400">
        {label}
      </div>

      <div
        className={`mt-3 truncate text-xl font-black ${
          danger ? "text-red-600" : "text-slate-950"
        }`}
      >
        {value}
      </div>
    </div>
  );
}

function Field({
  label,
  value,
  onChange,
  placeholder,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  placeholder?: string;
}) {
  return (
    <div>
      <label className="mb-2 block text-xs font-black text-slate-600">
        {label}
      </label>

      <input
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        className="w-full rounded-xl border border-slate-200 px-4 py-3 text-sm"
      />
    </div>
  );
}

function Select({
  label,
  value,
  onChange,
  options,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  options: string[];
}) {
  return (
    <div>
      <label className="mb-2 block text-xs font-black text-slate-600">
        {label}
      </label>

      <select
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="w-full rounded-xl border border-slate-200 px-4 py-3 text-sm font-semibold"
      >
        {options.map((option) => (
          <option key={option} value={option}>
            {option}
          </option>
        ))}
      </select>
    </div>
  );
}

function Modal({
  title,
  onClose,
  children,
}: {
  title: string;
  onClose: () => void;
  children: React.ReactNode;
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-4">
      <div className="w-full max-w-lg rounded-3xl bg-white p-6 shadow-2xl">
        <div className="mb-5 flex items-center justify-between">
          <h2 className="text-xl font-black text-slate-950">{title}</h2>

          <button
            onClick={onClose}
            className="rounded-lg px-3 py-2 text-slate-400 hover:bg-slate-100"
          >
            ✕
          </button>
        </div>

        {children}
      </div>
    </div>
  );
}

function ReportCard({ title, data }: { title: string; data: GenericReport }) {
  const entries = Object.entries(data || {})
    .filter(([, value]) => typeof value !== "object")
    .slice(0, 8);

  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="text-sm font-black text-slate-950">{title}</div>

      <div className="mt-4 space-y-2">
        {entries.length === 0 ? (
          <div className="text-xs text-slate-400">No summary data.</div>
        ) : (
          entries.map(([key, value]) => (
            <div key={key} className="flex justify-between gap-4 text-xs">
              <span className="font-semibold text-slate-500">{key}</span>

              <span className="font-black text-slate-800">
                {typeof value === "number" ? money(value) : String(value)}
              </span>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
