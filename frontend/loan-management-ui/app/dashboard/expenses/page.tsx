"use client";

import { useEffect, useState, useCallback, useRef } from "react";
import { expenseApi, bankAccountApi, branchApi } from "@/services/api";
import { PageSpinner } from "@/components/ui/Skeleton";
import { useAuth } from "@/hooks/useAuth";

const CATEGORIES = [
  { value: "SALARIES_AND_WAGES", label: "Salaries and Wages" },
  { value: "RENT", label: "Rent" },
  { value: "UTILITIES", label: "Utilities" },
  { value: "INTERNET", label: "Internet" },
  { value: "TRANSPORT", label: "Transport" },
  { value: "FUEL", label: "Fuel" },
  { value: "OFFICE_SUPPLIES", label: "Office Supplies" },
  { value: "BANK_CHARGES", label: "Bank Charges" },
  { value: "INSURANCE", label: "Insurance" },
  { value: "MARKETING", label: "Marketing" },
  { value: "LEGAL_FEES", label: "Legal Fees" },
  { value: "AUDIT_FEES", label: "Audit Fees" },
  { value: "DEPRECIATION", label: "Depreciation" },
  {
    value: "LOAN_RECOVERY_EXPENSES",
    label: "Loan Recovery Expenses",
  },
  { value: "IT_EXPENSES", label: "IT Expenses" },
  {
    value: "OTHER_OPERATING_EXPENSES",
    label: "Other Operating Expenses",
  },
];

/*
 * IMPORTANT:
 * These values must match your Java Expense.PaymentMethod enum.
 */
const PAYMENT_METHODS = [
  {
    value: "CASH",
    label: "Cash",
    description: "Paid directly from physical cash",
  },
  {
    value: "BANK_TRANSFER",
    label: "Bank Transfer",
    description: "Paid through a bank transfer",
  },
  {
    value: "MOBILE_MONEY",
    label: "Mobile Money",
    description: "Paid using a mobile money wallet",
  },
  {
    value: "MOMO_PAY",
    label: "MoMo Pay",
    description: "Paid using a MoMo Pay code",
  },
  {
    value: "CARD",
    label: "Card",
    description: "Paid using a debit or credit card",
  },
  {
    value: "CHEQUE",
    label: "Cheque",
    description: "Paid using a cheque",
  },
];

const MOBILE_MONEY_PROVIDERS = ["MTN Mobile Money", "Airtel Money", "Other"];

const MOMO_PAY_PROVIDERS = ["MTN MoMo Pay", "Other"];

const CARD_BRANDS = ["Visa", "Mastercard", "American Express", "Other"];

interface BankAccountRow {
  id: number;
  name: string;
  accountType: string;
  glAccountCode?: string;
  active: boolean;
}

interface BranchRow {
  id: number;
  name: string;
}

interface ExpenseRow {
  id: number;
  expenseDate: string;
  category: string;
  amount: number;
  currency: string;

  description?: string;

  status: "POSTED" | "VOID";

  paymentAccount?: {
    id: number;
    name: string;
  };

  branch?: {
    id: number;
    name: string;
  };

  paymentMethod?: string;
  paymentProvider?: string;
  paymentPhoneNumber?: string;
  paymentTransactionReference?: string;
  paymentCode?: string;

  cardBrand?: string;
  cardLastFour?: string;
  cardAuthorizationCode?: string;

  chequeNumber?: string;
  paymentNotes?: string;

  createdByName?: string;
  receiptFileName?: string;
}

interface ExpensePageResult {
  content: ExpenseRow[];
  totalElements: number;
  totalPages: number;
}

const PAGE_SIZE = 20;

const categoryLabel = (value: string) =>
  CATEGORIES.find((c) => c.value === value)?.label || value;

const paymentMethodLabel = (value?: string) =>
  PAYMENT_METHODS.find((m) => m.value === value)?.label || value || "—";

export default function ExpensesPage() {
  const { currency } = useAuth();

  const [expenses, setExpenses] = useState<ExpenseRow[]>([]);
  const [bankAccounts, setBankAccounts] = useState<BankAccountRow[]>([]);
  const [branches, setBranches] = useState<BranchRow[]>([]);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalRecords, setTotalRecords] = useState(0);

  const [showForm, setShowForm] = useState(false);
  const [loadingPaymentOptions, setLoadingPaymentOptions] = useState(false);
  const [paymentOptionsError, setPaymentOptionsError] = useState("");
  const expenseRequest = useRef(0);

  const [filterCategory, setFilterCategory] = useState("");
  const [filterPaymentMethod, setFilterPaymentMethod] = useState("");

  const load = useCallback(async () => {
    const request = ++expenseRequest.current;
    setLoading(true);
    setError("");

    try {
      const result: ExpensePageResult | ExpenseRow[] = await expenseApi.list({
        page,
        size: PAGE_SIZE,
        category: filterCategory || undefined,
      });
      if (request !== expenseRequest.current) return;
      setExpenses(Array.isArray(result) ? result : (result.content ?? []));
      setTotalPages(Array.isArray(result) ? 1 : (result.totalPages ?? 0));
      setTotalRecords(Array.isArray(result) ? result.length : (result.totalElements ?? 0));
    } catch (failure: unknown) {
      if (request !== expenseRequest.current) return;
      setExpenses([]);
      setTotalPages(0);
      setTotalRecords(0);
      setError(failure instanceof Error ? failure.message : "Expenses could not be loaded.");
    } finally {
      if (request === expenseRequest.current) setLoading(false);
    }
  }, [filterCategory, page]);

  const openExpenseForm = async () => {
    setLoadingPaymentOptions(true);
    setPaymentOptionsError("");
    try {
      const [accountsResult, branchesResult] = await Promise.allSettled([
        bankAccountApi.list(),
        branchApi.list(),
      ]);
      if (accountsResult.status === "rejected" || branchesResult.status === "rejected") {
        const failures = [
          accountsResult.status === "rejected" ? "payment accounts" : null,
          branchesResult.status === "rejected" ? "branches" : null,
        ].filter(Boolean);
        setPaymentOptionsError(`Could not load ${failures.join(" or ")}. Select Record Expense to retry.`);
        return;
      }
      setBankAccounts(accountsResult.value as BankAccountRow[]);
      setBranches(branchesResult.value as BranchRow[]);
      setShowForm(true);
    } finally {
      setLoadingPaymentOptions(false);
    }
  };

  useEffect(() => {
    load();
  }, [load]);

  const handleVoid = async (id: number) => {
    const reason =
      window.prompt("Reason for voiding this expense (required):");

    // The server requires a reason and writes a GL reversal. Cancel or blank
    // input must not initiate an irreversible financial action.
    if (!reason?.trim()) return;

    try {
      await expenseApi.void(id, reason.trim());

      load();
    } catch (e) {
      alert(e instanceof Error ? e.message : "Could not void expense");
    }
  };

  const fmt = (n: number) =>
    new Intl.NumberFormat("en-US", {
      maximumFractionDigits: 0,
    }).format(n || 0);

  if (loading) {
    return <PageSpinner />;
  }

  const visibleExpenses = filterPaymentMethod
    ? expenses.filter((e) => e.paymentMethod === filterPaymentMethod)
    : expenses;

  const total = visibleExpenses
    .filter((e) => e.status === "POSTED")
    .reduce((sum, e) => sum + (Number(e.amount) || 0), 0);

  const resetCategory = (value: string) => {
    setPage(0);
    setFilterCategory(value);
  };

  return (
    <div className="nls-finance-page space-y-6">
      {/* Header */}
      <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
        <div>
          <h1 className="text-3xl font-extrabold tracking-tight text-[#0B1F3A]">
            Operating Expenses
          </h1>

          <p className="text-sm text-gray-500 mt-1">
            Record, track and audit institutional operating expenses.
          </p>
        </div>

        <button
          type="button"
          disabled={loadingPaymentOptions}
          onClick={() => void openExpenseForm()}
          className="inline-flex items-center justify-center px-5 py-2.5 bg-[#0B1F3A] hover:bg-[#16365F] text-white text-sm font-semibold rounded-xl shadow-sm transition focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-amber-500"
        >
          {loadingPaymentOptions ? "Loading payment options…" : "+ Record Expense"}
        </button>
      </div>

      {paymentOptionsError && (
        <p role="alert" className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">
          {paymentOptionsError}
        </p>
      )}

      {/* Summary cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="bg-white border border-gray-200 rounded-xl p-4">
          <p className="text-xs font-medium text-gray-500 uppercase">Total records</p>

          <p className="text-2xl font-bold text-gray-900 mt-1">
            {totalRecords}
          </p>
        </div>

        <div className="bg-white border border-gray-200 rounded-xl p-4">
          <p className="text-xs font-medium text-gray-500 uppercase">
            Posted on this page
          </p>

          <p className="text-2xl font-bold text-gray-900 mt-1">
            {visibleExpenses.filter((e) => e.status === "POSTED").length}
          </p>
        </div>

        <div className="bg-white border border-gray-200 rounded-xl p-4">
          <p className="text-xs font-medium text-gray-500 uppercase">
            Posted amount on this page
          </p>

          <p className="text-2xl font-bold text-teal-700 mt-1">
            {currency} {fmt(total)}
          </p>
        </div>
      </div>

      {error && (
        <div className="px-4 py-3 bg-red-50 text-red-700 text-sm rounded-lg border border-red-200">
          {error}
        </div>
      )}

      {/* Filters */}
      <div className="bg-white border border-gray-200 rounded-xl p-4">
        <div className="flex flex-col md:flex-row gap-3">
          <div>
            <label className="block text-xs font-medium text-gray-500 mb-1">
              Expense Category
            </label>

            <select
              value={filterCategory}
              onChange={(e) => resetCategory(e.target.value)}
              className="px-3 py-2 border border-gray-300 rounded-lg text-sm"
            >
              <option value="">All categories</option>

              {CATEGORIES.map((category) => (
                <option key={category.value} value={category.value}>
                  {category.label}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-xs font-medium text-gray-500 mb-1">
              Payment Method
            </label>

            <select
              value={filterPaymentMethod}
              onChange={(e) => setFilterPaymentMethod(e.target.value)}
              className="px-3 py-2 border border-gray-300 rounded-lg text-sm"
            >
              <option value="">All payment methods</option>

              {PAYMENT_METHODS.map((method) => (
                <option key={method.value} value={method.value}>
                  {method.label}
                </option>
              ))}
            </select>
          </div>
        </div>
      </div>

      {/* Expense table */}
      <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 text-left text-xs font-semibold text-gray-500 uppercase">
              <tr>
                <th className="px-4 py-3">Date</th>
                <th className="px-4 py-3">Expense</th>
                <th className="px-4 py-3">Amount</th>
                <th className="px-4 py-3">Paid From</th>
                <th className="px-4 py-3">Method</th>
                <th className="px-4 py-3">Branch</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Created By</th>
                <th className="px-4 py-3"></th>
              </tr>
            </thead>

            <tbody className="divide-y divide-gray-100">
              {visibleExpenses.map((expense) => (
                <tr
                  key={expense.id}
                  className={expense.status === "VOID" ? "opacity-50" : ""}
                >
                  <td className="px-4 py-3 whitespace-nowrap">
                    {expense.expenseDate}
                  </td>

                  <td className="px-4 py-3">
                    <div className="font-medium text-gray-900">
                      {categoryLabel(expense.category)}
                    </div>

                    {expense.description && (
                      <div className="text-xs text-gray-500 max-w-xs truncate">
                        {expense.description}
                      </div>
                    )}
                  </td>

                  <td className="px-4 py-3 font-semibold whitespace-nowrap">
                    {expense.currency} {fmt(expense.amount)}
                  </td>

                  <td className="px-4 py-3">
                    {expense.paymentAccount?.name ?? "—"}
                  </td>

                  <td className="px-4 py-3">
                    <span className="text-xs font-medium px-2 py-1 rounded-md bg-gray-100 text-gray-700">
                      {paymentMethodLabel(expense.paymentMethod)}
                    </span>
                  </td>

                  <td className="px-4 py-3">
                    {expense.branch?.name ?? "Head Office"}
                  </td>

                  <td className="px-4 py-3">
                    <span
                      className={`text-xs font-semibold px-2 py-1 rounded-full ${
                        expense.status === "POSTED"
                          ? "bg-green-50 text-green-700"
                          : "bg-gray-100 text-gray-500"
                      }`}
                    >
                      {expense.status}
                    </span>
                  </td>

                  <td className="px-4 py-3 text-gray-500">
                    {expense.createdByName ?? "—"}
                  </td>

                  <td className="px-4 py-3 text-right whitespace-nowrap">
                    {expense.receiptFileName && (
                      <a
                        href={expenseApi.receiptUrl(expense.id)}
                        target="_blank"
                        rel="noreferrer"
                        className="text-xs text-blue-600 hover:underline mr-3"
                      >
                        Receipt
                      </a>
                    )}

                    {expense.status === "POSTED" && (
                      <button
                        onClick={() => handleVoid(expense.id)}
                        className="text-xs text-red-600 hover:underline"
                      >
                        Void
                      </button>
                    )}
                  </td>
                </tr>
              ))}

              {visibleExpenses.length === 0 && !error && (
                <tr>
                  <td
                    colSpan={9}
                    className="px-4 py-12 text-center text-gray-400"
                  >
                    No expenses found.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      {totalPages > 1 && (
        <nav aria-label="Expense pages" className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-slate-200 bg-white px-5 py-4">
          <p className="text-sm text-slate-600">
            Page <span className="font-semibold text-slate-900">{page + 1}</span> of {totalPages} · {totalRecords} records
          </p>
          <div className="flex gap-2">
            <button type="button" disabled={page === 0} onClick={() => setPage((value) => Math.max(0, value - 1))}
              className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-semibold text-[#0B1F3A] disabled:cursor-not-allowed disabled:opacity-40 hover:bg-slate-50">
              Previous
            </button>
            <button type="button" disabled={page + 1 >= totalPages} onClick={() => setPage((value) => value + 1)}
              className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-semibold text-[#0B1F3A] disabled:cursor-not-allowed disabled:opacity-40 hover:bg-slate-50">
              Next
            </button>
          </div>
        </nav>
      )}

      {showForm && (
        <AddExpenseModal
          bankAccounts={bankAccounts}
          branches={branches}
          onClose={() => setShowForm(false)}
          onSaved={() => {
            setShowForm(false);
            load();
          }}
        />
      )}
    </div>
  );
}

/* =========================================================
   ADD EXPENSE MODAL
========================================================= */

function AddExpenseModal({
  bankAccounts,
  branches,
  onClose,
  onSaved,
}: {
  bankAccounts: BankAccountRow[];
  branches: BranchRow[];
  onClose: () => void;
  onSaved: () => void;
}) {
  const [expenseDate, setExpenseDate] = useState(
    new Date().toISOString().slice(0, 10),
  );

  const [category, setCategory] = useState("OFFICE_SUPPLIES");

  const [amount, setAmount] = useState("");

  const [paymentAccountId, setPaymentAccountId] = useState("");

  const [approvalId, setApprovalId] = useState("");

  const [branchId, setBranchId] = useState("");

  const [description, setDescription] = useState("");

  const [paymentMethod, setPaymentMethod] = useState("CASH");

  const [paymentProvider, setPaymentProvider] = useState("");

  const [paymentPhoneNumber, setPaymentPhoneNumber] = useState("");

  const [paymentTransactionReference, setPaymentTransactionReference] =
    useState("");

  const [paymentCode, setPaymentCode] = useState("");

  const [cardBrand, setCardBrand] = useState("");

  const [cardLastFour, setCardLastFour] = useState("");

  const [cardAuthorizationCode, setCardAuthorizationCode] = useState("");

  const [chequeNumber, setChequeNumber] = useState("");

  const [paymentNotes, setPaymentNotes] = useState("");

  const [receipt, setReceipt] = useState<File | null>(null);

  const [saving, setSaving] = useState(false);

  const [error, setError] = useState("");

  const activePaymentAccounts = bankAccounts.filter((account) => account.active);
  const selectedPaymentAccount = activePaymentAccounts.find(
    (account) => String(account.id) === paymentAccountId,
  );

  const selectedPaymentMethod = PAYMENT_METHODS.find(
    (method) => method.value === paymentMethod,
  );

  const requiresMobileDetails = paymentMethod === "MOBILE_MONEY";

  const requiresMomoDetails = paymentMethod === "MOMO_PAY";

  const requiresCardDetails = paymentMethod === "CARD";

  const requiresChequeDetails = paymentMethod === "CHEQUE";

  const requiresBankReference = paymentMethod === "BANK_TRANSFER";

  const resetPaymentDetails = (method: string) => {
    setPaymentProvider("");
    setPaymentPhoneNumber("");
    setPaymentTransactionReference("");
    setPaymentCode("");

    setCardBrand("");
    setCardLastFour("");
    setCardAuthorizationCode("");

    setChequeNumber("");

    /*
     * We intentionally keep payment notes.
     */
    setPaymentMethod(method);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    setError("");

    if (!selectedPaymentAccount) {
      setError("Select the account from which this expense was paid.");
      return;
    }

    const numericApprovalId = Number(approvalId);

    if (!Number.isInteger(numericApprovalId) || numericApprovalId <= 0) {
      setError(
        "Enter the approved financial approval ID before recording the expense.",
      );
      return;
    }

    const numericAmount = Number(amount);

    if (!Number.isFinite(numericAmount) || numericAmount <= 0) {
      setError("Enter a valid expense amount.");
      return;
    }

    if (requiresMobileDetails && !paymentTransactionReference.trim()) {
      setError("Enter the mobile money transaction reference.");
      return;
    }

    if (requiresMomoDetails && !paymentCode.trim()) {
      setError("Enter the MoMo Pay payment code.");
      return;
    }

    if (requiresCardDetails && !cardLastFour.trim()) {
      setError("Enter the last four digits of the card.");
      return;
    }

    if (requiresChequeDetails && !chequeNumber.trim()) {
      setError("Enter the cheque number.");
      return;
    }

    if (requiresBankReference && !paymentTransactionReference.trim()) {
      setError("Enter the bank transaction reference.");
      return;
    }

    setSaving(true);

    try {
      await expenseApi.create({
        expenseDate,
        category,
        amount: numericAmount,

        paymentAccountId: Number(paymentAccountId),

        approvalId: numericApprovalId,

        branchId: branchId ? Number(branchId) : undefined,

        description: description.trim() || undefined,

        paymentMethod,

        paymentProvider: paymentProvider.trim() || undefined,

        paymentPhoneNumber: paymentPhoneNumber.trim() || undefined,

        paymentTransactionReference:
          paymentTransactionReference.trim() || undefined,

        paymentCode: paymentCode.trim() || undefined,

        cardBrand: cardBrand.trim() || undefined,

        cardLastFour: cardLastFour.trim() || undefined,

        cardAuthorizationCode: cardAuthorizationCode.trim() || undefined,

        chequeNumber: chequeNumber.trim() || undefined,

        paymentNotes: paymentNotes.trim() || undefined,

        receipt,
      });

      onSaved();
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Could not record expense.",
      );
    } finally {
      setSaving(false);
    }
  };

  const inputClass =
    "w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-teal-500 focus:border-teal-500";

  const labelClass = "block text-sm font-medium text-gray-700 mb-1.5";

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl w-full max-w-2xl max-h-[94vh] overflow-y-auto shadow-2xl">
        <form onSubmit={handleSubmit} className="p-6">
          {/* Modal header */}
          <div className="flex items-start justify-between mb-6">
            <div>
              <h2 className="text-xl font-bold text-gray-900">
                Record Operating Expense
              </h2>

              <p className="text-sm text-gray-500 mt-1">
                Record the expense and payment evidence for accounting and audit
                purposes.
              </p>
            </div>

            <button
              type="button"
              onClick={onClose}
              className="text-gray-400 hover:text-gray-600 text-xl"
            >
              ×
            </button>
          </div>

          {error && (
            <div className="mb-5 px-4 py-3 bg-red-50 border border-red-200 text-red-700 text-sm rounded-lg">
              {error}
            </div>
          )}

          {/* =================================================
              EXPENSE INFORMATION
          ================================================= */}

          <div className="mb-6">
            <div className="flex items-center gap-2 mb-4">
              <div className="w-7 h-7 rounded-full bg-teal-100 text-teal-700 flex items-center justify-center text-xs font-bold">
                1
              </div>

              <h3 className="font-semibold text-gray-900">
                Expense Information
              </h3>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className={labelClass}>Expense Date <span className="text-red-700" aria-hidden="true">*</span></label>

                <input
                  type="date"
                  required
                  value={expenseDate}
                  onChange={(e) => setExpenseDate(e.target.value)}
                  className={inputClass}
                />
              </div>

              <div>
                <label className={labelClass}>Expense Category <span className="text-red-700" aria-hidden="true">*</span></label>

                <select
                  required
                  value={category}
                  onChange={(e) => setCategory(e.target.value)}
                  className={inputClass}
                >
                  {CATEGORIES.map((c) => (
                    <option key={c.value} value={c.value}>
                      {c.label}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className={labelClass}>Amount <span className="text-red-700" aria-hidden="true">*</span></label>

                <input
                  type="number"
                  min="0.01"
                  step="0.01"
                  required
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                  placeholder="0.00"
                  className={inputClass}
                />
              </div>

              <div>
                <label className={labelClass}>
                  Approved Financial Approval ID <span className="text-red-700" aria-hidden="true">*</span>
                </label>

                <input
                  type="number"
                  min="1"
                  step="1"
                  required
                  value={approvalId}
                  onChange={(e) => setApprovalId(e.target.value)}
                  placeholder="Approval ID"
                  className={inputClass}
                />

                <p className="mt-1 text-xs text-gray-500">
                  The approval must already be approved by an authorized
                  checker.
                </p>
              </div>

              <div>
                <label className={labelClass}>Branch</label>

                <select
                  value={branchId}
                  onChange={(e) => setBranchId(e.target.value)}
                  className={inputClass}
                >
                  <option value="">Head Office / Organization-wide</option>

                  {branches.map((branch) => (
                    <option key={branch.id} value={branch.id}>
                      {branch.name}
                    </option>
                  ))}
                </select>
              </div>
            </div>
          </div>

          {/* =================================================
              PAYMENT SOURCE
          ================================================= */}

          <div className="mb-6">
            <div className="flex items-center gap-2 mb-4">
              <div className="w-7 h-7 rounded-full bg-teal-100 text-teal-700 flex items-center justify-center text-xs font-bold">
                2
              </div>

              <h3 className="font-semibold text-gray-900">Payment Source</h3>
            </div>

            <div className="space-y-4">
              <div>
                <label htmlFor="expense-payment-account" className={labelClass}>Account that actually paid <span className="text-red-700" aria-hidden="true">*</span></label>

                <select
                  id="expense-payment-account"
                  aria-describedby="expense-payment-account-help"
                  required
                  value={paymentAccountId}
                  onChange={(e) => setPaymentAccountId(e.target.value)}
                  className={inputClass}
                >
                  <option value="">
                    Select bank, cash or wallet account...
                  </option>

                  {activePaymentAccounts.map((account) => (
                    <option key={account.id} value={account.id}>
                      {account.name} — {account.accountType}
                      {account.glAccountCode ? ` · GL ${account.glAccountCode}` : ""}
                    </option>
                  ))}
                </select>

                <p id="expense-payment-account-help" className="mt-2 text-xs text-gray-600">
                  Reuse the company bank or cash account that paid. The expense category
                  tracks what the money was spent on.
                </p>
                {activePaymentAccounts.length === 0 && (
                  <p className="mt-2 text-xs text-amber-800">
                    Ask your accountant to verify and configure the actual payment account
                    under Accounting → Bank Accounts.
                  </p>
                )}
              </div>

              <div>
                <label className={labelClass}>Payment Method <span className="text-red-700" aria-hidden="true">*</span></label>

                <select
                  required
                  value={paymentMethod}
                  onChange={(e) => resetPaymentDetails(e.target.value)}
                  className={inputClass}
                >
                  {PAYMENT_METHODS.map((method) => (
                    <option key={method.value} value={method.value}>
                      {method.label}
                    </option>
                  ))}
                </select>

                {selectedPaymentMethod && (
                  <p className="text-xs text-gray-500 mt-1.5">
                    {selectedPaymentMethod.description}
                  </p>
                )}
              </div>
            </div>
          </div>

          {/* =================================================
              MOBILE MONEY
          ================================================= */}

          {requiresMobileDetails && (
            <div className="mb-6 p-4 bg-blue-50 border border-blue-200 rounded-xl">
              <h4 className="font-semibold text-blue-900 mb-3">
                Mobile Money Payment Details
              </h4>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label className={labelClass}>Mobile Money Provider</label>

                  <select
                    value={paymentProvider}
                    onChange={(e) => setPaymentProvider(e.target.value)}
                    className={inputClass}
                  >
                    <option value="">Select provider...</option>

                    {MOBILE_MONEY_PROVIDERS.map((provider) => (
                      <option key={provider} value={provider}>
                        {provider}
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className={labelClass}>Phone Number</label>

                  <input
                    type="tel"
                    value={paymentPhoneNumber}
                    onChange={(e) => setPaymentPhoneNumber(e.target.value)}
                    placeholder="07XXXXXXXX"
                    className={inputClass}
                  />
                </div>

                <div className="md:col-span-2">
                  <label className={labelClass}>Transaction Reference <span className="text-red-700" aria-hidden="true">*</span></label>

                  <input
                    type="text"
                    required
                    value={paymentTransactionReference}
                    onChange={(e) =>
                      setPaymentTransactionReference(e.target.value)
                    }
                    placeholder="e.g. transaction ID / reference"
                    className={inputClass}
                  />

                  <p className="text-xs text-gray-500 mt-1">
                    Record the exact transaction ID shown by the mobile money
                    provider.
                  </p>
                </div>
              </div>
            </div>
          )}

          {/* =================================================
              MOMO PAY
          ================================================= */}

          {requiresMomoDetails && (
            <div className="mb-6 p-4 bg-yellow-50 border border-yellow-200 rounded-xl">
              <h4 className="font-semibold text-yellow-900 mb-3">
                MoMo Pay Details
              </h4>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label className={labelClass}>Provider</label>

                  <select
                    value={paymentProvider}
                    onChange={(e) => setPaymentProvider(e.target.value)}
                    className={inputClass}
                  >
                    <option value="">Select provider...</option>

                    {MOMO_PAY_PROVIDERS.map((provider) => (
                      <option key={provider} value={provider}>
                        {provider}
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className={labelClass}>Phone Number</label>

                  <input
                    type="tel"
                    value={paymentPhoneNumber}
                    onChange={(e) => setPaymentPhoneNumber(e.target.value)}
                    placeholder="07XXXXXXXX"
                    className={inputClass}
                  />
                </div>

                <div>
                  <label className={labelClass}>MoMo Pay Code <span className="text-red-700" aria-hidden="true">*</span></label>

                  <input
                    type="text"
                    required
                    value={paymentCode}
                    onChange={(e) => setPaymentCode(e.target.value)}
                    placeholder="Enter MoMo Pay code"
                    className={inputClass}
                  />
                </div>

                <div>
                  <label className={labelClass}>Transaction Reference</label>

                  <input
                    type="text"
                    value={paymentTransactionReference}
                    onChange={(e) =>
                      setPaymentTransactionReference(e.target.value)
                    }
                    placeholder="Optional transaction ID"
                    className={inputClass}
                  />
                </div>
              </div>
            </div>
          )}

          {/* =================================================
              BANK TRANSFER
          ================================================= */}

          {requiresBankReference && (
            <div className="mb-6 p-4 bg-gray-50 border border-gray-200 rounded-xl">
              <h4 className="font-semibold text-gray-900 mb-3">
                Bank Transfer Details
              </h4>

              <div>
                <label className={labelClass}>
                  Bank Transaction Reference <span className="text-red-700" aria-hidden="true">*</span>
                </label>

                <input
                  type="text"
                  required
                  value={paymentTransactionReference}
                  onChange={(e) =>
                    setPaymentTransactionReference(e.target.value)
                  }
                  placeholder="e.g. bank transfer reference"
                  className={inputClass}
                />
              </div>
            </div>
          )}

          {/* =================================================
              CARD
          ================================================= */}

          {requiresCardDetails && (
            <div className="mb-6 p-4 bg-purple-50 border border-purple-200 rounded-xl">
              <h4 className="font-semibold text-purple-900 mb-3">
                Card Payment Details
              </h4>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label className={labelClass}>Card Brand</label>

                  <select
                    value={cardBrand}
                    onChange={(e) => setCardBrand(e.target.value)}
                    className={inputClass}
                  >
                    <option value="">Select card brand...</option>

                    {CARD_BRANDS.map((brand) => (
                      <option key={brand} value={brand}>
                        {brand}
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className={labelClass}>Last 4 Digits <span className="text-red-700" aria-hidden="true">*</span></label>

                  <input
                    type="text"
                    inputMode="numeric"
                    maxLength={4}
                    required
                    value={cardLastFour}
                    onChange={(e) =>
                      setCardLastFour(
                        e.target.value.replace(/\D/g, "").slice(0, 4),
                      )
                    }
                    placeholder="1234"
                    className={inputClass}
                  />

                  <p className="text-xs text-gray-500 mt-1">
                    Never record the full card number.
                  </p>
                </div>

                <div>
                  <label className={labelClass}>Authorization Code</label>

                  <input
                    type="text"
                    value={cardAuthorizationCode}
                    onChange={(e) => setCardAuthorizationCode(e.target.value)}
                    placeholder="Card authorization code"
                    className={inputClass}
                  />
                </div>

                <div>
                  <label className={labelClass}>Transaction Reference</label>

                  <input
                    type="text"
                    value={paymentTransactionReference}
                    onChange={(e) =>
                      setPaymentTransactionReference(e.target.value)
                    }
                    placeholder="Card transaction reference"
                    className={inputClass}
                  />
                </div>
              </div>
            </div>
          )}

          {/* =================================================
              CHEQUE
          ================================================= */}

          {requiresChequeDetails && (
            <div className="mb-6 p-4 bg-orange-50 border border-orange-200 rounded-xl">
              <h4 className="font-semibold text-orange-900 mb-3">
                Cheque Details
              </h4>

              <div>
                <label className={labelClass}>Cheque Number <span className="text-red-700" aria-hidden="true">*</span></label>

                <input
                  type="text"
                  required
                  value={chequeNumber}
                  onChange={(e) => setChequeNumber(e.target.value)}
                  placeholder="Enter cheque number"
                  className={inputClass}
                />
              </div>
            </div>
          )}

          {/* =================================================
              DESCRIPTION & NOTES
          ================================================= */}

          <div className="mb-6">
            <div className="flex items-center gap-2 mb-4">
              <div className="w-7 h-7 rounded-full bg-teal-100 text-teal-700 flex items-center justify-center text-xs font-bold">
                3
              </div>

              <h3 className="font-semibold text-gray-900">
                Supporting Information
              </h3>
            </div>

            <div className="space-y-4">
              <div>
                <label className={labelClass}>Expense Description</label>

                <textarea
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  rows={3}
                  placeholder="Describe what this expense was for..."
                  className={inputClass}
                />
              </div>

              <div>
                <label className={labelClass}>Payment Notes</label>

                <textarea
                  value={paymentNotes}
                  onChange={(e) => setPaymentNotes(e.target.value)}
                  rows={2}
                  placeholder="Additional payment or reconciliation notes..."
                  className={inputClass}
                />
              </div>
            </div>
          </div>

          {/* =================================================
              RECEIPT
          ================================================= */}

          <div className="mb-6">
            <div className="flex items-center gap-2 mb-4">
              <div className="w-7 h-7 rounded-full bg-teal-100 text-teal-700 flex items-center justify-center text-xs font-bold">
                4
              </div>

              <h3 className="font-semibold text-gray-900">
                Supporting Document
              </h3>
            </div>

            <div className="border-2 border-dashed border-gray-300 rounded-xl p-5">
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Receipt / Proof of Payment
              </label>

              <input
                type="file"
                accept="application/pdf,image/jpeg,image/png,image/webp"
                onChange={(e) => setReceipt(e.target.files?.[0] ?? null)}
                className="w-full text-sm text-gray-600"
              />

              <p className="text-xs text-gray-500 mt-2">
                Accepted formats: PDF, JPG, PNG and WEBP. Maximum size: 8MB.
              </p>

              {receipt && (
                <div className="mt-3 text-sm text-teal-700">
                  Selected: {receipt.name}
                </div>
              )}
            </div>
          </div>

          {/* =================================================
              PAYMENT SUMMARY
          ================================================= */}

          <div className="mb-6 bg-gray-50 border border-gray-200 rounded-xl p-4">
            <h4 className="font-semibold text-gray-900 mb-3">
              Transaction Summary
            </h4>

            <div className="grid grid-cols-2 gap-y-2 text-sm">
              <span className="text-gray-500">Amount</span>

              <span className="font-semibold text-right">
                {amount ? Number(amount).toLocaleString() : "0"}
              </span>

              <span className="text-gray-500">Payment Method</span>

              <span className="font-medium text-right">
                {paymentMethodLabel(paymentMethod)}
              </span>

              <span className="text-gray-500">Paid From</span>

              <span className="font-medium text-right">
                {selectedPaymentAccount?.name ?? "Not selected"}
              </span>

              {paymentTransactionReference && (
                <>
                  <span className="text-gray-500">Reference</span>

                  <span className="font-medium text-right truncate">
                    {paymentTransactionReference}
                  </span>
                </>
              )}
            </div>
          </div>

          {/* Actions */}

          <div className="flex gap-3 pt-2 border-t border-gray-200">
            <button
              type="button"
              onClick={onClose}
              disabled={saving}
              className="flex-1 px-4 py-2.5 border border-gray-300 rounded-lg text-sm font-semibold text-gray-700 hover:bg-gray-50 disabled:opacity-50"
            >
              Cancel
            </button>

            <button
              type="submit"
              disabled={saving}
              className="flex-1 px-4 py-2.5 bg-teal-600 hover:bg-teal-700 disabled:opacity-50 text-white text-sm font-semibold rounded-lg"
            >
              {saving ? "Recording..." : "Record Expense"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
