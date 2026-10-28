"use client";

import { Suspense, useEffect, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { borrowerApi, loanApi } from "@/services/api";

// The backend is the authority for product terms, charges and eligibility.
// The figures displayed here are indicative only, not posted to the ledger.
type BorrowerOption = {
  id: number;
  firstName?: string;
  lastName?: string;
  name?: string;
  phone?: string;
  nationalId?: string;
};
const LOAN_TYPES = [
  ["PERSONAL", "Personal"],
  ["BUSINESS", "Business"],
  ["SALARY_ADVANCE", "Salary advance"],
  ["AGRICULTURAL", "Agriculture"],
  ["AUTO", "Vehicle"],
  ["ASSET_FINANCE", "Asset finance"],
  ["MICROFINANCE", "Microfinance"],
  ["EMERGENCY", "Emergency"],
] as const;
const fieldStyle =
  "mt-1 w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm focus:border-amber-500 focus:outline-none focus:ring-2 focus:ring-amber-100";
const money = (n: number) =>
  new Intl.NumberFormat("en-RW", { maximumFractionDigits: 0 }).format(n) +
  " RWF";

function NewLoanForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [borrowers, setBorrowers] = useState<BorrowerOption[]>([]);
  const [borrowerQuery, setBorrowerQuery] = useState("");
  const [borrowerId, setBorrowerId] = useState(
    searchParams.get("borrowerId") ?? "",
  );
  const [loadingBorrowers, setLoadingBorrowers] = useState(false);
  const [loanType, setLoanType] = useState("PERSONAL");
  const [amount, setAmount] = useState("");
  const [durationMonths, setDurationMonths] = useState("3");
  const [startDate, setStartDate] = useState(() =>
    new Date().toLocaleDateString("en-CA"),
  );
  const [purpose, setPurpose] = useState("");
  const [collateralValue, setCollateralValue] = useState("");
  const [collateralDescription, setCollateralDescription] = useState("");
  const [notes, setNotes] = useState("");
  const [review, setReview] = useState(false);
  const [confirmed, setConfirmed] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  // Search the API rather than limiting the officer to the first 200 borrowers.
  useEffect(() => {
    let active = true;
    const timer = window.setTimeout(async () => {
      setLoadingBorrowers(true);
      try {
        const data = await borrowerApi.list(0, 50, borrowerQuery.trim());
        const options = Array.isArray(data)
          ? data
          : Array.isArray(data?.content)
            ? data.content
            : Array.isArray(data?.items)
              ? data.items
              : [];
        if (active)
          setBorrowers(
            options.filter((b: BorrowerOption) =>
              Number.isSafeInteger(Number(b?.id)),
            ),
          );
      } catch (err) {
        if (active)
          setError(
            err instanceof Error ? err.message : "Unable to find borrowers",
          );
      } finally {
        if (active) setLoadingBorrowers(false);
      }
    }, 300);
    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [borrowerQuery]);

  const selected = borrowers.find((b) => String(b.id) === borrowerId);
  const principal = Number(amount);
  const duration = Number(durationMonths);
  const valid =
    Number.isSafeInteger(Number(borrowerId)) &&
    Number(borrowerId) > 0 &&
    Number.isFinite(principal) &&
    principal >= 1_000_000 &&
    Number.isInteger(duration) &&
    duration >= 1 &&
    duration <= 6 &&
    Boolean(startDate) &&
    purpose.trim().length >= 10 &&
    (!collateralValue ||
      (Number.isFinite(Number(collateralValue)) &&
        Number(collateralValue) >= 0));
  const estimate = useMemo(
    () => ({
      interest: principal > 0 ? principal * 0.05 : 0,
      management: principal > 0 ? principal * 0.05 : 0,
      application: principal > 0 ? principal * 0.02 : 0,
    }),
    [principal],
  );

  async function submit() {
    if (!valid || !confirmed || submitting) return;
    setSubmitting(true);
    setError("");
    try {
      const created = await loanApi.create({
        borrowerId: Number(borrowerId),
        loanType,
        amount: principal,
        durationMonths: duration,
        currency: "RWF",
        startDate,
        repaymentFrequency: "MONTHLY",
        purpose: purpose.trim(),
        notes: notes.trim() || undefined,
        collateralValue: collateralValue ? Number(collateralValue) : undefined,
        collateralDescription: collateralDescription.trim() || undefined,
      });
      const id = Number(created?.id);
      router.push(
        Number.isSafeInteger(id) && id > 0
          ? `/dashboard/loans/${id}`
          : "/dashboard/loans",
      );
      router.refresh();
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "Unable to create the loan application",
      );
      setConfirmed(false);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="mx-auto max-w-4xl space-y-5 px-4 py-6 sm:px-6">
      <header className="flex items-start justify-between gap-3">
        <div>
          <p className="text-xs font-bold uppercase tracking-wider text-amber-700">
            Loan origination
          </p>
          <h1 className="mt-1 text-2xl font-bold text-slate-900">
            New loan application
          </h1>
          <p className="mt-1 text-sm text-slate-600">
            Select a verified borrower, define the proposal and review it before
            submitting for approval.
          </p>
        </div>
        <button
          type="button"
          className="rounded-lg border px-3 py-2 text-sm"
          onClick={() => router.push("/dashboard/loans")}
        >
          Back to loans
        </button>
      </header>
      {error && (
        <div
          role="alert"
          className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800"
        >
          {error}
        </div>
      )}
      {!review ? (
        <form
          className="space-y-5"
          onSubmit={(e) => {
            e.preventDefault();
            if (!valid) {
              setError(
                "Check the borrower, amount (minimum 1,000,000 RWF), 1–6 month term and purpose (at least 10 characters).",
              );
              return;
            }
            setError("");
            setReview(true);
          }}
        >
          <section className="rounded-xl border bg-white p-5 shadow-sm">
            <h2 className="font-semibold text-slate-900">
              1. Borrower identity
            </h2>
            <p className="mt-1 text-xs text-slate-500">
              Search the borrower register; create and verify the borrower
              profile separately before applying. Never create a second profile
              for another loan.
            </p>
            <label className="mt-4 block text-sm font-medium">
              Find borrower by name / ID / phone
            </label>
            <input
              className={fieldStyle}
              value={borrowerQuery}
              onChange={(e) => setBorrowerQuery(e.target.value)}
              placeholder="Search borrower register"
            />
            <label className="mt-3 block text-sm font-medium">Borrower *</label>
            <select
              required
              className={fieldStyle}
              value={borrowerId}
              onChange={(e) => setBorrowerId(e.target.value)}
              disabled={loadingBorrowers}
            >
              <option value="">
                {loadingBorrowers ? "Searching…" : "Choose existing borrower"}
              </option>
              {borrowerId && !selected && (
                <option value={borrowerId}>
                  Selected borrower #{borrowerId} (open profile to verify)
                </option>
              )}
              {borrowers.map((b) => (
                <option key={b.id} value={b.id}>
                  {`${b.firstName ?? ""} ${b.lastName ?? ""}`.trim() ||
                    b.name ||
                    `Borrower ${b.id}`}{" "}
                  — #{b.id}
                  {b.phone ? ` · ${b.phone}` : ""}
                </option>
              ))}
            </select>
          </section>
          <section className="rounded-xl border bg-white p-5 shadow-sm">
            <h2 className="font-semibold text-slate-900">
              2. Requested loan terms
            </h2>
            <div className="mt-3 grid gap-4 sm:grid-cols-2">
              <label className="block text-sm font-medium">
                Product / loan type
                <select
                  className={fieldStyle}
                  value={loanType}
                  onChange={(e) => setLoanType(e.target.value)}
                >
                  {LOAN_TYPES.map(([v, l]) => (
                    <option value={v} key={v}>
                      {l}
                    </option>
                  ))}
                </select>
              </label>
              <label className="block text-sm font-medium">
                Principal (RWF) *
                <input
                  className={fieldStyle}
                  type="number"
                  min="1000000"
                  step="1"
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                  required
                />
              </label>
              <label className="block text-sm font-medium">
                Term (months) *
                <input
                  className={fieldStyle}
                  type="number"
                  min="1"
                  max="6"
                  step="1"
                  value={durationMonths}
                  onChange={(e) => setDurationMonths(e.target.value)}
                  required
                />
              </label>
              <label className="block text-sm font-medium">
                Proposed start date *
                <input
                  className={fieldStyle}
                  type="date"
                  value={startDate}
                  onChange={(e) => setStartDate(e.target.value)}
                  required
                />
              </label>
            </div>
            <label className="mt-4 block text-sm font-medium">
              Loan purpose *
              <textarea
                rows={3}
                maxLength={1500}
                required
                className={fieldStyle}
                value={purpose}
                onChange={(e) => setPurpose(e.target.value)}
                placeholder="How will the funds be used?"
              />
            </label>
            <p className="mt-3 text-xs text-slate-600">
              Repayment frequency: <strong>monthly</strong>. Interest and
              application fees are determined by current backend product rules
              at submission and approval. Management fee is 5% monthly on
              outstanding principal; no fee is automatically collected by
              completing this form.
            </p>
          </section>
          <section className="rounded-xl border bg-white p-5 shadow-sm">
            <h2 className="font-semibold text-slate-900">
              3. Security and supporting information
            </h2>
            <div className="mt-3 grid gap-4 sm:grid-cols-2">
              <label className="block text-sm font-medium">
                Collateral value (RWF, optional)
                <input
                  className={fieldStyle}
                  type="number"
                  min="0"
                  step="1"
                  value={collateralValue}
                  onChange={(e) => setCollateralValue(e.target.value)}
                />
              </label>
              <label className="block text-sm font-medium">
                Collateral description (optional)
                <input
                  className={fieldStyle}
                  maxLength={1000}
                  value={collateralDescription}
                  onChange={(e) => setCollateralDescription(e.target.value)}
                />
              </label>
            </div>
            <label className="mt-4 block text-sm font-medium">
              Internal assessment notes
              <textarea
                rows={3}
                maxLength={5000}
                className={fieldStyle}
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                placeholder="Repayment source, verified documents, risks and next actions"
              />
            </label>
            <p className="mt-3 text-xs text-amber-800">
              Loans enter the maker-checker workflow. Creating the application
              does not approve or disburse funds.
            </p>
          </section>
          <div className="flex justify-end">
            <button
              className="rounded-lg bg-slate-900 px-6 py-3 text-sm font-semibold text-white disabled:opacity-50"
              disabled={loadingBorrowers || submitting}
              type="submit"
            >
              Review application →
            </button>
          </div>
        </form>
      ) : (
        <section className="space-y-4 rounded-xl border bg-white p-5 shadow-sm">
          <h2 className="text-lg font-bold">4. Confirm before creating</h2>
          <dl className="grid gap-3 text-sm sm:grid-cols-2">
            <div>
              <dt className="text-slate-500">Borrower</dt>
              <dd className="font-semibold">
                {selected
                  ? `${selected.firstName ?? ""} ${selected.lastName ?? ""}`.trim() ||
                    selected.name
                  : `Borrower #${borrowerId}`}{" "}
                (#{borrowerId})
              </dd>
            </div>
            <div>
              <dt className="text-slate-500">Loan product</dt>
              <dd className="font-semibold">{loanType.replaceAll("_", " ")}</dd>
            </div>
            <div>
              <dt className="text-slate-500">Requested principal</dt>
              <dd className="font-semibold">{money(principal)}</dd>
            </div>
            <div>
              <dt className="text-slate-500">Term / frequency</dt>
              <dd className="font-semibold">{duration} months / monthly</dd>
            </div>
            <div>
              <dt className="text-slate-500">Proposed start</dt>
              <dd className="font-semibold">{startDate}</dd>
            </div>
            <div>
              <dt className="text-slate-500">Collateral value</dt>
              <dd className="font-semibold">
                {collateralValue
                  ? money(Number(collateralValue))
                  : "Not supplied"}
              </dd>
            </div>
            <div className="sm:col-span-2">
              <dt className="text-slate-500">Purpose</dt>
              <dd className="font-semibold whitespace-pre-wrap">{purpose}</dd>
            </div>
          </dl>
          <div className="rounded-lg bg-amber-50 p-4 text-sm text-amber-950">
            <strong>Indicative charge illustration only.</strong> At 5%
            interest, estimated one-month interest is {money(estimate.interest)}
            ; one-month management fee is {money(estimate.management)}; at 2% an
            application fee would be {money(estimate.application)}. Actual
            charges, disbursement deductions and schedules must be confirmed by
            backend product rules and approval. These are not contractual
            totals.
          </div>
          <label className="flex gap-2 text-sm">
            <input
              type="checkbox"
              checked={confirmed}
              onChange={(e) => setConfirmed(e.target.checked)}
            />{" "}
            I verified the borrower identity, principal and purpose, and
            understand this is an application—not a disbursement.
          </label>
          <div className="flex justify-end gap-3">
            <button
              type="button"
              className="rounded-lg border px-4 py-2 text-sm"
              disabled={submitting}
              onClick={() => {
                setReview(false);
                setConfirmed(false);
              }}
            >
              Edit details
            </button>
            <button
              type="button"
              className="rounded-lg bg-slate-900 px-6 py-2 text-sm font-semibold text-white disabled:opacity-50"
              disabled={!confirmed || submitting}
              onClick={() => void submit()}
            >
              {submitting ? "Submitting…" : "Create and send for approval"}
            </button>
          </div>
        </section>
      )}
    </div>
  );
}

export default function NewLoanPage() {
  return (
    <Suspense fallback={<div className="p-6">Loading loan application…</div>}>
      <NewLoanForm />
    </Suspense>
  );
}
