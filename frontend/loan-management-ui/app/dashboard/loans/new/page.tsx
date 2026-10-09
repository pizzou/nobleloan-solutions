"use client";
import { useState, useEffect, useCallback, Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import {
  createLoan,
  CreateLoanPayload,
} from "../../../../services/loanService";
import { getBorrowers } from "../../../../services/borrowerService";
import { Borrower } from "../../../../types/index";
import { toast } from "../../../../hooks/useToast";

export default function NewLoanPage() {
  return (
    <Suspense
      fallback={<div className="p-6 text-sm text-gray-400">Loading…</div>}
    >
      <NewLoanForm />
    </Suspense>
  );
}

function NewLoanForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [borrowers, setBorrowers] = useState<Borrower[]>([]);
  const [loading, setLoading] = useState(false);
  const [borrowerId, setBorrowerId] = useState(
    searchParams.get("borrowerId") ?? "",
  );
  const [amount, setAmount] = useState("");
  const [interestRate, setInterestRate] = useState("5");
  const [interestRateType, setInterestRateType] = useState<
    "MONTHLY" | "ANNUAL"
  >("MONTHLY");
  const [durationMonths, setDurationMonths] = useState("1");
  const [currency, setCurrency] = useState("RWF");
  const [startDate, setStartDate] = useState(
    new Date().toISOString().slice(0, 10),
  );
  const [notes, setNotes] = useState("");
  const [loanType, setLoanType] =
    useState<CreateLoanPayload["loanType"]>("PERSONAL");
  const [borrowersLoading, setBorrowersLoading] = useState(true);
  const [borrowerLoadError, setBorrowerLoadError] = useState("");
  const [collateralValue, setCollateralValue] = useState("");
  const [collateralDesc, setCollateralDesc] = useState("");

  useEffect(() => {
    let active = true;
    setBorrowersLoading(true);
    getBorrowers()
      .then((data) => {
        if (!active) return;
        const validBorrowers = (Array.isArray(data) ? data : []).filter(
          (borrower): borrower is Borrower =>
            Number.isSafeInteger(borrower?.id) && borrower.id > 0,
        );
        setBorrowers(validBorrowers);
        setBorrowerLoadError("");
      })
      .catch((error: unknown) => {
        if (!active) return;
        setBorrowerLoadError(getMsg(error));
      })
      .finally(() => {
        if (active) setBorrowersLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  function getMsg(err: unknown): string {
    if (err instanceof Error && err.message) return err.message;
    return "Something went wrong. Please try again.";
  }

  const monthlyPreview = (() => {
    const P = Number(amount);
    // Previously this always divided by 1200 (i.e. treated every rate as annual, /100/12) —
    // so entering "10" here silently meant 10% per YEAR even when the officer intended 10%
    // per MONTH. Now it respects whichever type is actually selected below.
    const r =
      interestRateType === "MONTHLY"
        ? Number(interestRate) / 100
        : Number(interestRate) / 1200;
    const n = Number(durationMonths);
    if (!P || !n) return null;
    if (r === 0) return (P / n).toFixed(2);
    const M = (P * r * Math.pow(1 + r, n)) / (Math.pow(1 + r, n) - 1);
    return isFinite(M) ? M.toFixed(2) : null;
  })();

  const ltv = (() => {
    const a = Number(amount);
    const c = Number(collateralValue);
    if (!a || !c) return null;
    return ((a / c) * 100).toFixed(1);
  })();

  const handleSubmit = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault();
      // Reject invalid IDs and numeric values before constructing any API URL or payload.
      const parsedBorrowerId = Number(borrowerId);
      const parsedAmount = Number(amount);
      const parsedRate = Number(interestRate);
      const parsedDuration = Number(durationMonths);
      const parsedCollateral =
        collateralValue.trim() === "" ? undefined : Number(collateralValue);
      if (
        !Number.isSafeInteger(parsedBorrowerId) ||
        parsedBorrowerId <= 0 ||
        !borrowers.some((borrower) => borrower.id === parsedBorrowerId)
      ) {
        toast("error", "Select a valid borrower and try again.");
        return;
      }
      if (!Number.isFinite(parsedAmount) || parsedAmount <= 0) {
        toast("error", "Loan amount must be greater than zero.");
        return;
      }
      if (!Number.isFinite(parsedRate) || parsedRate < 0 || parsedRate > 100) {
        toast("error", "Enter a valid interest rate between 0 and 100.");
        return;
      }
      // The backend LoanRequest currently enforces a maximum duration of six months.
      if (
        !Number.isSafeInteger(parsedDuration) ||
        parsedDuration < 1 ||
        parsedDuration > 6
      ) {
        toast("error", "Loan duration must be between 1 and 6 months.");
        return;
      }
      if (!startDate || Number.isNaN(Date.parse(`${startDate}T00:00:00`))) {
        toast("error", "Choose a valid loan start date.");
        return;
      }
      if (
        parsedCollateral !== undefined &&
        (!Number.isFinite(parsedCollateral) || parsedCollateral < 0)
      ) {
        toast("error", "Collateral value must be zero or greater.");
        return;
      }
      setLoading(true);
      const payload: CreateLoanPayload = {
        borrowerId: parsedBorrowerId,
        loanType,
        amount: parsedAmount,
        interestRate: parsedRate,
        interestRateType,
        durationMonths: parsedDuration,
        currency,
        startDate,
        notes: notes.trim() || undefined,
        collateralValue: parsedCollateral,
        collateralDescription: collateralDesc.trim() || undefined,
      };
      try {
        await createLoan(payload);
        toast("success", "Loan application submitted!");
        router.push("/dashboard/loans");
      } catch (err: unknown) {
        toast("error", getMsg(err));
      } finally {
        setLoading(false);
      }
    },
    [
      borrowerId,
      amount,
      interestRate,
      durationMonths,
      currency,
      startDate,
      notes,
      collateralValue,
      collateralDesc,
      interestRateType,
      loanType,
      borrowers,
      router,
    ],
  );

  return (
    <div className="max-w-2xl">
      <div className="mb-6">
        <Link
          href="/dashboard/loans"
          className="text-sm text-gray-500 hover:text-gray-700"
        >
          ← Back
        </Link>
        <h1 className="text-xl font-bold text-gray-900 mt-2">
          New Loan Application
        </h1>
      </div>

      <form
        onSubmit={handleSubmit}
        className="bg-white rounded-xl border border-gray-200 p-6 space-y-5"
      >
        {/* Borrower */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1.5">
            Borrower *
          </label>
          <select
            value={borrowerId}
            onChange={(e) => setBorrowerId(e.target.value)}
            className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="">
              {borrowersLoading ? "Loading borrowers…" : "Select a borrower..."}
            </option>
            {borrowers.map((b) => (
              <option key={b.id} value={b.id}>
                {b.firstName} {b.lastName}
              </option>
            ))}
          </select>
          {borrowerLoadError && (
            <div role="alert" className="mt-2 text-sm text-red-600">
              Could not load borrowers: {borrowerLoadError}{" "}
              <button
                type="button"
                className="underline font-semibold"
                onClick={() => {
                  setBorrowerLoadError("");
                  setBorrowersLoading(true);
                  getBorrowers()
                    .then((data) =>
                      setBorrowers(
                        (Array.isArray(data) ? data : []).filter(
                          (b) => Number.isSafeInteger(b?.id) && b.id > 0,
                        ),
                      ),
                    )
                    .catch((error: unknown) =>
                      setBorrowerLoadError(getMsg(error)),
                    )
                    .finally(() => setBorrowersLoading(false));
                }}
              >
                Retry
              </button>
            </div>
          )}
          {!borrowersLoading &&
            !borrowerLoadError &&
            borrowers.length === 0 && (
              <p className="mt-2 text-sm text-amber-700">
                No borrowers found. Create a borrower first.
              </p>
            )}
        </div>

        {/* Loan product/type */}
        <div>
          <label
            htmlFor="loanType"
            className="block text-sm font-medium text-gray-700 mb-1.5"
          >
            Loan Type *
          </label>
          <select
            id="loanType"
            value={loanType ?? "PERSONAL"}
            onChange={(e) =>
              setLoanType(e.target.value as CreateLoanPayload["loanType"])
            }
            className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            {[
              ["PERSONAL", "Personal"],
              ["BUSINESS", "Business"],
              ["MORTGAGE", "Mortgage"],
              ["AUTO", "Auto"],
              ["STUDENT", "Student"],
              ["EMERGENCY", "Emergency"],
              ["ASSET_FINANCE", "Asset Finance"],
              ["SALARY_ADVANCE", "Salary Advance"],
              ["MICROFINANCE", "Microfinance"],
              ["AGRICULTURAL", "Agricultural"],
              ["TRADE_FINANCE", "Trade Finance"],
              ["GROUP", "Group"],
            ].map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </div>

        {/* Amount + Currency */}
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">
              Amount *
            </label>
            <div className="flex">
              <select
                value={currency}
                onChange={(e) => setCurrency(e.target.value)}
                className="px-3 py-2.5 border border-r-0 border-gray-300 rounded-l-lg text-sm bg-gray-50 focus:outline-none"
              >
                {["USD", "RWF", "EUR", "KES", "GBP", "NGN"].map((c) => (
                  <option key={c}>{c}</option>
                ))}
              </select>
              <input
                type="number"
                min="1"
                required
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                placeholder="0.00"
                className="flex-1 px-4 py-2.5 border border-gray-300 rounded-r-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">
              Interest Rate (
              {interestRateType === "MONTHLY" ? "% per month" : "% per year"}) *
            </label>
            <div className="flex gap-2 mb-2">
              <button
                type="button"
                onClick={() => setInterestRateType("MONTHLY")}
                className={`flex-1 text-xs font-semibold py-1.5 rounded-lg border transition ${
                  interestRateType === "MONTHLY"
                    ? "bg-blue-600 text-white border-blue-600"
                    : "border-gray-300 text-gray-600 hover:border-blue-400"
                }`}
              >
                Monthly
              </button>
              <button
                type="button"
                onClick={() => setInterestRateType("ANNUAL")}
                className={`flex-1 text-xs font-semibold py-1.5 rounded-lg border transition ${
                  interestRateType === "ANNUAL"
                    ? "bg-blue-600 text-white border-blue-600"
                    : "border-gray-300 text-gray-600 hover:border-blue-400"
                }`}
              >
                Annual
              </button>
            </div>
            <input
              type="number"
              step="0.01"
              min="0"
              max="100"
              required
              value={interestRate}
              onChange={(e) => setInterestRate(e.target.value)}
              placeholder={
                interestRateType === "MONTHLY" ? "e.g. 10" : "e.g. 12"
              }
              className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            {interestRateType === "MONTHLY" && (
              <div className="flex gap-1.5 mt-1.5">
                {[6, 8, 10].map((r) => (
                  <button
                    key={r}
                    type="button"
                    onClick={() => setInterestRate(String(r))}
                    className={`text-xs px-2.5 py-1 rounded border font-semibold transition-colors ${
                      interestRate === String(r)
                        ? "bg-blue-50 text-blue-700 border-blue-300"
                        : "border-gray-300 text-gray-600 hover:border-blue-400"
                    }`}
                  >
                    {r}%/mo
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* Duration + Start Date */}
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">
              Duration (months) *
            </label>
            <input
              type="number"
              min="1"
              max="6"
              step="1"
              required
              value={durationMonths}
              onChange={(e) => setDurationMonths(e.target.value)}
              placeholder="1–6 months"
              className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">
              Start Date *
            </label>
            <input
              type="date"
              required
              value={startDate}
              onChange={(e) => setStartDate(e.target.value)}
              className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
        </div>

        {/* Collateral */}
        <div className="border-t border-gray-100 pt-5">
          <h3 className="text-sm font-semibold text-gray-700 mb-3">
            Collateral{" "}
            <span className="text-gray-400 font-normal">(optional)</span>
          </h3>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">
                Collateral Value ({currency})
              </label>
              <input
                type="number"
                min="0"
                value={collateralValue}
                onChange={(e) => setCollateralValue(e.target.value)}
                placeholder="e.g. 50000"
                className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">
                Description
              </label>
              <input
                type="text"
                value={collateralDesc}
                onChange={(e) => setCollateralDesc(e.target.value)}
                placeholder="e.g. Land title, Vehicle"
                className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
          </div>
          {ltv && (
            <div
              className={`mt-3 flex items-center gap-2 text-xs font-medium px-3 py-2 rounded-lg ${
                Number(ltv) <= 70
                  ? "bg-green-50 text-green-700"
                  : Number(ltv) <= 90
                    ? "bg-yellow-50 text-yellow-700"
                    : "bg-red-50 text-red-700"
              }`}
            >
              <span>📊 Loan-to-Value (LTV): {ltv}%</span>
              <span>&mdash;</span>
              <span>
                {Number(ltv) <= 70
                  ? "Excellent coverage"
                  : Number(ltv) <= 90
                    ? "Acceptable"
                    : "High risk — collateral may be insufficient"}
              </span>
            </div>
          )}
        </div>

        {/* Notes */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1.5">
            Notes <span className="text-gray-400 font-normal">(optional)</span>
          </label>
          <textarea
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            rows={2}
            placeholder="Purpose of loan, additional terms, etc."
            className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
          />
        </div>

        {/* Monthly preview */}
        {monthlyPreview && (
          <div className="bg-blue-50 border border-blue-200 rounded-xl p-4">
            <p className="text-sm text-blue-700 font-medium">
              Estimated monthly installment
            </p>
            <p className="text-2xl font-bold text-blue-800 mt-0.5">
              {currency} {monthlyPreview}
            </p>
            <p className="text-xs text-blue-500 mt-1">
              Reducing balance (amortization) method
            </p>
          </div>
        )}

        <div className="flex gap-3 pt-2">
          <button
            type="submit"
            disabled={loading || borrowersLoading || borrowers.length === 0}
            className="bg-blue-600 hover:bg-blue-700 text-white px-6 py-2.5 rounded-lg text-sm font-medium disabled:opacity-60 transition"
          >
            {loading ? "Submitting..." : "Submit Application"}
          </button>
          <Link
            href="/dashboard/loans"
            className="px-6 py-2.5 rounded-lg text-sm font-medium text-gray-600 hover:bg-gray-100 transition"
          >
            Cancel
          </Link>
        </div>
      </form>
    </div>
  );
}
