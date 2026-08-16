"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";

import { borrowerApi, loanApi } from "@/services/api";
import { Borrower, Loan } from "@/types";
import { useAuth } from "@/hooks/useAuth";
import { Button } from "@/components/ui/Button";
import { Card, CardBody, CardHeader } from "@/components/ui/Card";
import { PageSpinner } from "@/components/ui/Skeleton";
import { Pill } from "@/components/ui/Badge";
import { formatCurrency } from "@/lib/utils";

const MIN_AMOUNT = 500000;
const MAX_MONTHS = 6;

function nameOf(borrower?: Borrower) {
  if (!borrower) return "Select a borrower";
  return `${borrower.firstName} ${borrower.lastName}`.trim();
}

export default function NewLoanPage() {
  const router = useRouter();
  const { currency, locale } = useAuth();
  const [borrowers, setBorrowers] = useState<Borrower[]>([]);
  const [borrowerId, setBorrowerId] = useState("");
  const [amount, setAmount] = useState("");
  const [months, setMonths] = useState("3");
  const [purpose, setPurpose] = useState("");
  const [collateralValue, setCollateralValue] = useState("");
  const [collateralDescription, setCollateralDescription] = useState("");
  const [borrowerSearch, setBorrowerSearch] = useState("");
  const [loadingBorrowers, setLoadingBorrowers] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState<Loan | null>(null);

  useEffect(() => {
    let mounted = true;
    borrowerApi
      .list(0, 100, "")
      .then((result: any) => {
        const rows = Array.isArray(result)
          ? result
          : result?.content || result?.items || result?.data || [];
        if (mounted) setBorrowers(rows);
      })
      .catch((err: any) => {
        if (mounted) setError(err?.message || "Unable to load borrowers.");
      })
      .finally(() => {
        if (mounted) setLoadingBorrowers(false);
      });
    return () => {
      mounted = false;
    };
  }, []);

  const selectedBorrower = useMemo(
    () => borrowers.find((b) => String(b.id) === borrowerId),
    [borrowers, borrowerId],
  );
  const filteredBorrowers = useMemo(() => {
    const q = borrowerSearch.trim().toLowerCase();
    if (!q) return borrowers;
    return borrowers.filter((b) =>
      [nameOf(b), b.nationalId, b.phone, b.email]
        .filter(Boolean)
        .join(" ")
        .toLowerCase()
        .includes(q),
    );
  }, [borrowers, borrowerSearch]);

  const principal = Number(amount || 0);
  const processingFee = principal > 0 ? principal * 0.02 : 0;
  const netDisbursement = Math.max(0, principal - processingFee);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError("");
    const duration = Number(months);
    if (!borrowerId)
      return setError("Select a borrower before submitting the application.");
    if (!Number.isFinite(principal) || principal < MIN_AMOUNT)
      return setError(
        `The minimum loan amount is ${formatCurrency(MIN_AMOUNT, currency, locale)}.`,
      );
    if (!Number.isInteger(duration) || duration < 1 || duration > MAX_MONTHS)
      return setError("Loan duration must be between 1 and 6 months.");
    if (selectedBorrower?.status === "BLACKLISTED")
      return setError(
        "This borrower is blacklisted and cannot receive a new loan.",
      );

    setSubmitting(true);
    try {
      const created = await loanApi.create({
        borrowerId: Number(borrowerId),
        amount: principal,
        interestRate: 5,
        interestRateType: "MONTHLY",
        durationMonths: duration,
        currency,
        repaymentFrequency: "MONTHLY",
        startDate: new Date().toISOString().slice(0, 10),
        purpose: purpose.trim() || undefined,
        collateralValue: collateralValue ? Number(collateralValue) : undefined,
        collateralDescription: collateralDescription.trim() || undefined,
      });
      const loan = created as Loan;
      setSuccess(loan);
    } catch (err: any) {
      setError(err?.message || "The loan application could not be submitted.");
    } finally {
      setSubmitting(false);
    }
  }

  if (loadingBorrowers) return <PageSpinner />;

  if (success) {
    return (
      <main className="premium-page grid min-h-[calc(100vh-72px)] place-items-center p-6">
        <section className="premium-card w-full max-w-2xl overflow-hidden">
          <div className="bg-[#07152A] px-7 py-8 text-white sm:px-10">
            <div className="premium-kicker">Application submitted</div>
            <h1 className="mt-2 text-3xl font-black tracking-tight">
              Credit workflow initiated
            </h1>
            <p className="mt-2 text-sm leading-6 text-slate-300">
              The application has been submitted to the existing approval
              workflow. The backend remains authoritative for pricing, schedule
              and accounting.
            </p>
          </div>
          <CardBody>
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="rounded-xl bg-slate-50 p-4">
                <div className="premium-eyebrow">Borrower</div>
                <div className="mt-1 font-black text-slate-900">
                  {nameOf(selectedBorrower)}
                </div>
              </div>
              <div className="rounded-xl bg-slate-50 p-4">
                <div className="premium-eyebrow">Reference</div>
                <div className="mt-1 font-black text-slate-900">
                  {success.referenceNumber || "Created"}
                </div>
              </div>
              <div className="rounded-xl bg-slate-50 p-4">
                <div className="premium-eyebrow">Principal</div>
                <div className="mt-1 font-black text-slate-900">
                  {formatCurrency(
                    success.amount || principal,
                    currency,
                    locale,
                  )}
                </div>
              </div>
              <div className="rounded-xl bg-slate-50 p-4">
                <div className="premium-eyebrow">Status</div>
                <div className="mt-1">
                  <Pill label={success.status || "PENDING"} color="yellow" />
                </div>
              </div>
            </div>
            <div className="mt-6 flex flex-wrap gap-2">
              <Button
                onClick={() =>
                  success.id
                    ? router.push(`/dashboard/loans/${success.id}`)
                    : router.push("/dashboard/loans")
                }
              >
                Open application
              </Button>
              <Button
                variant="secondary"
                onClick={() => router.push("/dashboard/loans")}
              >
                Return to portfolio
              </Button>
            </div>
          </CardBody>
        </section>
      </main>
    );
  }

  return (
    <main className="premium-page pb-12">
      <div className="mx-auto max-w-[1250px] space-y-6 px-4 py-6 sm:px-6 lg:px-8">
        <section className="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <div className="premium-eyebrow">Credit origination</div>
            <h1 className="premium-section-title">
              Create a new loan application
            </h1>
            <p className="premium-section-copy">
              A guided, controlled workflow for creating a facility. Pricing is
              shown as the platform policy; repayment schedules are calculated
              by the backend after creation.
            </p>
          </div>
          <Button variant="secondary" onClick={() => router.back()}>
            Back
          </Button>
        </section>

        {error ? (
          <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-xs font-semibold text-red-800">
            {error}
          </div>
        ) : null}

        <form onSubmit={submit} className="grid gap-5 xl:grid-cols-[1fr_360px]">
          <div className="space-y-5">
            <Card>
              <CardHeader
                title="01 · Borrower"
                subtitle="Select the customer receiving the facility."
              />
              <CardBody>
                <div className="grid gap-3 sm:grid-cols-[1fr_220px]">
                  <input
                    className="premium-input"
                    value={borrowerSearch}
                    onChange={(e) => setBorrowerSearch(e.target.value)}
                    placeholder="Search by name, national ID, phone or email"
                  />
                  <select
                    className="premium-input"
                    value={borrowerId}
                    onChange={(e) => setBorrowerId(e.target.value)}
                    required
                  >
                    <option value="">Select borrower</option>
                    {filteredBorrowers.map((b) => (
                      <option key={b.id} value={b.id}>
                        {nameOf(b)}
                        {b.nationalId ? ` · ${b.nationalId}` : ""}
                      </option>
                    ))}
                  </select>
                </div>
                {selectedBorrower ? (
                  <div className="mt-4 grid gap-3 rounded-2xl border border-slate-200 bg-slate-50 p-4 sm:grid-cols-3">
                    <div>
                      <div className="premium-eyebrow">Client</div>
                      <div className="mt-1 text-sm font-black text-slate-900">
                        {nameOf(selectedBorrower)}
                      </div>
                    </div>
                    <div>
                      <div className="premium-eyebrow">KYC</div>
                      <div className="mt-1">
                        <Pill
                          label={selectedBorrower.kycStatus || "PENDING"}
                          color={
                            selectedBorrower.kycStatus === "VERIFIED"
                              ? "green"
                              : "yellow"
                          }
                        />
                      </div>
                    </div>
                    <div>
                      <div className="premium-eyebrow">Status</div>
                      <div className="mt-1">
                        <Pill
                          label={selectedBorrower.status}
                          color={
                            selectedBorrower.status === "ACTIVE"
                              ? "green"
                              : "gray"
                          }
                        />
                      </div>
                    </div>
                  </div>
                ) : null}
              </CardBody>
            </Card>

            <Card>
              <CardHeader
                title="02 · Facility terms"
                subtitle="The financial engine owns the final schedule and calculated charges."
              />
              <CardBody>
                <div className="grid gap-4 md:grid-cols-2">
                  <label>
                    <span className="mb-1.5 block text-[10px] font-black uppercase tracking-[.14em] text-slate-500">
                      Principal amount
                    </span>
                    <input
                      className="premium-input"
                      type="number"
                      min={MIN_AMOUNT}
                      step="1000"
                      value={amount}
                      onChange={(e) => setAmount(e.target.value)}
                      placeholder="25,000,000"
                      required
                    />
                  </label>
                  <label>
                    <span className="mb-1.5 block text-[10px] font-black uppercase tracking-[.14em] text-slate-500">
                      Duration
                    </span>
                    <select
                      className="premium-input"
                      value={months}
                      onChange={(e) => setMonths(e.target.value)}
                    >
                      {Array.from({ length: MAX_MONTHS }, (_, i) => i + 1).map(
                        (m) => (
                          <option key={m} value={m}>
                            {m} {m === 1 ? "month" : "months"}
                          </option>
                        ),
                      )}
                    </select>
                  </label>
                  <label className="md:col-span-2">
                    <span className="mb-1.5 block text-[10px] font-black uppercase tracking-[.14em] text-slate-500">
                      Purpose
                    </span>
                    <textarea
                      className="min-h-[100px] w-full rounded-xl border border-slate-200 bg-white p-3 text-sm outline-none focus:border-teal-600 focus:ring-4 focus:ring-teal-600/10"
                      value={purpose}
                      onChange={(e) => setPurpose(e.target.value)}
                      placeholder="Describe the intended use of funds."
                    />
                  </label>
                </div>
              </CardBody>
            </Card>

            <Card>
              <CardHeader
                title="03 · Security"
                subtitle="Collateral information is recorded with the application when applicable."
              />
              <CardBody>
                <div className="grid gap-4 md:grid-cols-2">
                  <label>
                    <span className="mb-1.5 block text-[10px] font-black uppercase tracking-[.14em] text-slate-500">
                      Collateral value
                    </span>
                    <input
                      className="premium-input"
                      type="number"
                      min="0"
                      step="1000"
                      value={collateralValue}
                      onChange={(e) => setCollateralValue(e.target.value)}
                      placeholder="Optional"
                    />
                  </label>
                  <label>
                    <span className="mb-1.5 block text-[10px] font-black uppercase tracking-[.14em] text-slate-500">
                      Description
                    </span>
                    <input
                      className="premium-input"
                      value={collateralDescription}
                      onChange={(e) => setCollateralDescription(e.target.value)}
                      placeholder="Property, vehicle, equipment…"
                    />
                  </label>
                </div>
              </CardBody>
            </Card>
          </div>

          <aside className="space-y-5 xl:sticky xl:top-24 xl:self-start">
            <Card className="overflow-hidden">
              <div className="bg-[#07152A] px-5 py-5 text-white">
                <div className="premium-kicker">Institutional pricing</div>
                <h2 className="mt-2 text-xl font-black">Terms at a glance</h2>
              </div>
              <CardBody>
                <div className="space-y-4">
                  <div className="flex items-center justify-between border-b border-slate-100 pb-3">
                    <span className="text-xs font-semibold text-slate-500">
                      Interest
                    </span>
                    <span className="text-sm font-black text-slate-900">
                      5% monthly
                    </span>
                  </div>
                  <div className="flex items-center justify-between border-b border-slate-100 pb-3">
                    <span className="text-xs font-semibold text-slate-500">
                      Management fee
                    </span>
                    <span className="text-sm font-black text-slate-900">
                      5% monthly
                    </span>
                  </div>
                  <div className="flex items-center justify-between border-b border-slate-100 pb-3">
                    <span className="text-xs font-semibold text-slate-500">
                      Processing fee
                    </span>
                    <span className="text-sm font-black text-slate-900">
                      2% once
                    </span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-semibold text-slate-500">
                      Repayment
                    </span>
                    <span className="text-sm font-black text-slate-900">
                      Monthly
                    </span>
                  </div>
                </div>
              </CardBody>
            </Card>

            <Card>
              <CardHeader
                title="Disbursement preview"
                subtitle="Illustrative only — not a repayment schedule."
              />
              <CardBody>
                <div className="space-y-3">
                  <div className="flex justify-between text-xs">
                    <span className="text-slate-500">Gross principal</span>
                    <span className="font-black text-slate-900">
                      {formatCurrency(principal, currency, locale)}
                    </span>
                  </div>
                  <div className="flex justify-between text-xs">
                    <span className="text-slate-500">2% processing fee</span>
                    <span className="font-black text-slate-900">
                      {formatCurrency(processingFee, currency, locale)}
                    </span>
                  </div>
                  <div className="border-t border-slate-100 pt-3 flex justify-between">
                    <span className="text-xs font-bold text-slate-700">
                      Estimated net cash
                    </span>
                    <span className="text-base font-black text-[#07152A]">
                      {formatCurrency(netDisbursement, currency, locale)}
                    </span>
                  </div>
                  <p className="pt-2 text-[10px] leading-5 text-slate-400">
                    The backend calculates actual daily accruals and future
                    installments from outstanding principal. This page
                    intentionally does not reproduce that calculation.
                  </p>
                </div>
              </CardBody>
            </Card>

            <Button
              type="submit"
              size="lg"
              className="w-full"
              loading={submitting}
            >
              {submitting ? "Submitting application…" : "Submit for approval"}
            </Button>
            <p className="text-center text-[10px] leading-5 text-slate-400">
              Submission creates a loan application and enters the existing
              maker-checker workflow. It does not independently approve or
              disburse funds.
            </p>
          </aside>
        </form>
      </div>
    </main>
  );
}
