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
const MIN = 500000,
  MAX = 6;
const client = (b?: Borrower) =>
  b ? `${b.firstName} ${b.lastName}`.trim() : "Select a client";
export default function NewLoanPage() {
  const router = useRouter();
  const { currency, locale } = useAuth();
  const [borrowers, setBorrowers] = useState<Borrower[]>([]);
  const [borrowerId, setBorrowerId] = useState("");
  const [search, setSearch] = useState("");
  const [amount, setAmount] = useState("");
  const [months, setMonths] = useState("3");
  const [purpose, setPurpose] = useState("");
  const [collateralValue, setCollateralValue] = useState("");
  const [collateralDescription, setCollateralDescription] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState<Loan | null>(null);
  useEffect(() => {
    borrowerApi
      .list(0, 100, "")
      .then((r: any) =>
        setBorrowers(
          Array.isArray(r) ? r : r?.content || r?.items || r?.data || [],
        ),
      )
      .catch((e) => setError(e?.message || "Unable to load clients."))
      .finally(() => setLoading(false));
  }, []);
  const selected = useMemo(
    () => borrowers.find((b) => String(b.id) === borrowerId),
    [borrowers, borrowerId],
  );
  const filtered = useMemo(() => {
    const q = search.toLowerCase().trim();
    return q
      ? borrowers.filter((b) =>
          `${client(b)} ${b.nationalId || ""} ${b.phone || ""} ${b.email || ""}`
            .toLowerCase()
            .includes(q),
        )
      : borrowers;
  }, [borrowers, search]);
  async function submit(e: FormEvent) {
    e.preventDefault();
    setError("");
    const principal = Number(amount),
      duration = Number(months);
    if (!borrowerId) return setError("Select a client.");
    if (!Number.isFinite(principal) || principal < MIN)
      return setError(
        `Minimum principal is ${formatCurrency(MIN, currency, locale)}.`,
      );
    if (!Number.isInteger(duration) || duration < 1 || duration > MAX)
      return setError("Duration must be between 1 and 6 months.");
    if (selected?.status === "BLACKLISTED")
      return setError("This client is blacklisted.");
    setSaving(true);
    try {
      const r = await loanApi.create({
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
      setSuccess(r as Loan);
    } catch (e: any) {
      setError(e?.message || "The application could not be submitted.");
    } finally {
      setSaving(false);
    }
  }
  if (loading) return <PageSpinner />;
  if (success)
    return (
      <main className="premium-page grid min-h-[80vh] place-items-center p-6">
        <Card>
          <div className="bg-[#071a2d] px-7 py-8 text-white">
            <div className="premium-kicker">Origination complete</div>
            <h1 className="mt-2 text-3xl font-black">Application submitted</h1>
            <p className="mt-3 text-sm leading-6 text-slate-300">
              The application is now inside the existing approval workflow. The
              backend remains authoritative for schedule, accrual and
              accounting.
            </p>
          </div>
          <CardBody>
            <div className="grid gap-3 sm:grid-cols-2">
              <Info label="Client" value={client(selected)} />
              <Info
                label="Reference"
                value={success.referenceNumber || `#${success.id}`}
              />
              <Info
                label="Principal"
                value={formatCurrency(
                  success.amount || Number(amount),
                  currency,
                  locale,
                )}
              />
              <Info label="Status" value={success.status || "PENDING"} />
            </div>
            <div className="mt-6 flex gap-2">
              <Button
                onClick={() =>
                  success.id && router.push(`/dashboard/loans/${success.id}`)
                }
              >
                Open facility
              </Button>
              <Button
                variant="secondary"
                onClick={() => router.push("/dashboard/loans")}
              >
                Portfolio
              </Button>
            </div>
          </CardBody>
        </Card>
      </main>
    );
  return (
    <main className="premium-page pb-14">
      <div className="mx-auto max-w-[1280px] space-y-6 px-4 py-6 sm:px-6 lg:px-8">
        <section className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <div className="premium-eyebrow">Credit origination</div>
            <h1 className="premium-section-title">New lending facility</h1>
            <p className="premium-section-copy">
              A private-bank style origination workspace. The interface captures
              the request; the lending engine owns final pricing, daily accrual
              and repayment schedules.
            </p>
          </div>
          <Button variant="secondary" onClick={() => router.back()}>
            Back
          </Button>
        </section>
        {error && (
          <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-xs font-bold text-red-800">
            {error}
          </div>
        )}
        <form onSubmit={submit} className="grid gap-5 xl:grid-cols-[1fr_350px]">
          <div className="space-y-5">
            <Card>
              <CardHeader
                title="01 · Client relationship"
                subtitle="Select a verified lending customer."
              />
              <CardBody>
                <input
                  className="premium-input"
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  placeholder="Search name, national ID, phone or email"
                />
                <select
                  className="premium-input mt-3"
                  value={borrowerId}
                  onChange={(e) => setBorrowerId(e.target.value)}
                  required
                >
                  <option value="">Select client</option>
                  {filtered.map((b) => (
                    <option key={b.id} value={b.id}>
                      {client(b)}
                      {b.nationalId ? ` · ${b.nationalId}` : ""}
                    </option>
                  ))}
                </select>
                {selected && (
                  <div className="mt-4 grid gap-3 sm:grid-cols-3">
                    <Info label="Client status" value={selected.status} />
                    <Info label="KYC" value={selected.kycStatus} />
                    <Info
                      label="Contact"
                      value={selected.phone || selected.email || "—"}
                    />
                  </div>
                )}
              </CardBody>
            </Card>
            <Card>
              <CardHeader
                title="02 · Facility terms"
                subtitle="Institutional pricing policy is displayed for transparency; calculated schedules are not recreated in the browser."
              />
              <CardBody>
                <div className="grid gap-4 md:grid-cols-2">
                  <Field label="Principal amount">
                    <input
                      className="premium-input"
                      type="number"
                      min={MIN}
                      step="1000"
                      value={amount}
                      onChange={(e) => setAmount(e.target.value)}
                      required
                      placeholder="25,000,000"
                    />
                  </Field>
                  <Field label="Duration">
                    <select
                      className="premium-input"
                      value={months}
                      onChange={(e) => setMonths(e.target.value)}
                    >
                      {Array.from({ length: MAX }, (_, i) => i + 1).map((m) => (
                        <option key={m}>
                          {m} {m === 1 ? "month" : "months"}
                        </option>
                      ))}
                    </select>
                  </Field>
                  <Field label="Purpose" wide>
                    <textarea
                      className="premium-input min-h-[100px]"
                      value={purpose}
                      onChange={(e) => setPurpose(e.target.value)}
                      placeholder="Purpose and credit rationale"
                    />
                  </Field>
                </div>
              </CardBody>
            </Card>
            <Card>
              <CardHeader
                title="03 · Security & collateral"
                subtitle="Capture available security without calculating a separate risk model in the browser."
              />
              <CardBody>
                <div className="grid gap-4 md:grid-cols-2">
                  <Field label="Collateral value">
                    <input
                      className="premium-input"
                      type="number"
                      min="0"
                      value={collateralValue}
                      onChange={(e) => setCollateralValue(e.target.value)}
                      placeholder="Optional"
                    />
                  </Field>
                  <Field label="Collateral description">
                    <input
                      className="premium-input"
                      value={collateralDescription}
                      onChange={(e) => setCollateralDescription(e.target.value)}
                      placeholder="Property, vehicle, guarantee…"
                    />
                  </Field>
                </div>
              </CardBody>
            </Card>
          </div>
          <aside className="space-y-5">
            <Card>
              <CardHeader
                title="Pricing policy"
                subtitle="Authoritative lending rules"
              />
              <CardBody>
                <Policy label="Interest" value="5% monthly" />
                <Policy label="Management fee" value="5% monthly" />
                <Policy
                  label="Processing fee"
                  value="2% once at disbursement"
                />
                <div className="premium-divider my-4" />
                <p className="text-[10px] leading-5 text-slate-500">
                  Interest and management fees are accrued daily on outstanding
                  principal using the backend financial engine. Future
                  installments are recalculated after principal repayments.
                </p>
              </CardBody>
            </Card>
            <Card>
              <CardHeader title="Submission control" />
              <CardBody>
                <div className="rounded-xl bg-slate-50 p-4 text-[10px] leading-5 text-slate-500">
                  Submitting creates a facility request. Approval, documentation
                  checks, disbursement and accounting remain controlled by the
                  existing backend workflow.
                </div>
                <Button
                  className="mt-4 w-full"
                  size="lg"
                  loading={saving}
                  type="submit"
                >
                  Submit application
                </Button>
              </CardBody>
            </Card>
          </aside>
        </form>
      </div>
    </main>
  );
}
function Field({
  label,
  children,
  wide = false,
}: {
  label: string;
  children: any;
  wide?: boolean;
}) {
  return (
    <label className={wide ? "md:col-span-2" : ""}>
      <span className="mb-1.5 block text-[9px] font-black uppercase tracking-[.14em] text-slate-500">
        {label}
      </span>
      {children}
    </label>
  );
}
function Info({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl bg-slate-50 p-3">
      <div className="text-[9px] font-black uppercase tracking-wider text-slate-400">
        {label}
      </div>
      <div className="mt-1 text-xs font-black text-slate-900">{value}</div>
    </div>
  );
}
function Policy({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between border-b border-slate-100 py-3 last:border-0">
      <span className="text-xs font-semibold text-slate-500">{label}</span>
      <span className="text-xs font-black text-[#071a2d]">{value}</span>
    </div>
  );
}
