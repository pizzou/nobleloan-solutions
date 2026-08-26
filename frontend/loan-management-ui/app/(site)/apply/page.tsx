"use client";

import { useRef, useState } from "react";
import { useSearchParams } from "next/navigation";
import { useTenant } from "../layout";
import { useOnlineStatus } from "../../../hooks/useOnlineStatus";
import { queueAction } from "../../../lib/offlineDb";
import { TENANT_SLUG } from "../../../lib/tenant";
import DocumentUploadPanel from "../../../components/DocumentUploadPanel";

type Step = 1 | 2 | 3 | 4;

export default function ApplyPage() {
  const searchParams = useSearchParams();
  const slug = TENANT_SLUG;
  const tenant = useTenant();

  const [step, setStep] = useState<Step>(1);
  const [submitted, setSubmitted] = useState(false);
  const [saving, setSaving] = useState(false);
  const [reference, setReference] = useState("");
  const [queuedOffline, setQueuedOffline] = useState(false);
  const [error, setError] = useState("");
  const [docsComplete, setDocsComplete] = useState(false);

  const idempotencyKeyRef = useRef<string | null>(null);

  const [form, setForm] = useState({
    // Personal
    firstName: "",
    lastName: "",
    email: "",
    phone: "",
    nationalId: "",
    dateOfBirth: "",
    gender: "",
    maritalStatus: "",
    singleCertificateNumber: "",
    spouseFullName: "",
    spouseNationalId: "",
    spousePhone: "",
    spouseConsent: false,

    // Address
    address: "",
    city: "",
    province: "",

    // Employment
    employmentType: "EMPLOYED",
    employerName: "",
    jobTitle: "",
    natureOfBusiness: "",
    employmentTypeOther: "",
    monthlyIncome: "",
    monthlyExpenses: "",

    // Loan
    loanType: searchParams?.get("type")?.replace(/\_/g, " ") || "Personal Loan",
    amount: "",
    durationMonths: "1",
    purpose: "",
    collateral: "",
    collateralValue: "",

    // Declaration
    acceptedTerms: false,
  });

  const online = useOnlineStatus();

  if (!tenant) return null;

  const selectedService = tenant.services?.find(
    (s) => s.title === form.loanType || s.loanType === form.loanType,
  );
  const minimumLoanAmount = Number(selectedService?.minAmount ?? 500000);
  const configuredMaximum = selectedService?.maxAmount ?? null;
  const maximumLoanAmount =
    configuredMaximum === null ||
    configuredMaximum === undefined ||
    configuredMaximum === ""
      ? null
      : Number(configuredMaximum);
  const minimumTermMonths = Number(selectedService?.minTermMonths ?? 1);
  const maximumTermMonths = Number(selectedService?.maxTermMonths ?? 6);

  const primary = tenant.primaryColor;
  const accent = tenant.accentColor;

  // ============================================================
  // GENERIC FORM SETTER
  // ============================================================

  const set =
    (k: string) =>
    (
      e: React.ChangeEvent<
        HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement
      >,
    ) => {
      const target = e.target as HTMLInputElement;

      setForm((f) => ({
        ...f,
        [k]: target.type === "checkbox" ? target.checked : target.value,
      }));
    };

  // ============================================================
  // NATIONAL ID VALIDATION
  // Rwanda national IDs are exactly 16 digits.
  // ============================================================

  const NATIONAL_ID_REGEX = /^\d{16}$/;

  const isNationalIdValid = (value: string) => {
    return NATIONAL_ID_REGEX.test(value);
  };

  const setNationalId =
    (k: "nationalId" | "spouseNationalId") =>
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const digitsOnly = e.target.value.replace(/\D/g, "").slice(0, 16);

      setForm((f) => ({
        ...f,
        [k]: digitsOnly,
      }));
    };

  // ============================================================
  // PHONE VALIDATION
  // ============================================================

  const PHONE_REGEX = /^\+\d{10,15}$/;

  const isPhoneValid = (value: string) => {
    return PHONE_REGEX.test(value);
  };

  const setPhone =
    (k: "phone" | "spousePhone") =>
    (e: React.ChangeEvent<HTMLInputElement>) => {
      let value = e.target.value.replace(/[^\d+]/g, "");

      value = (value.startsWith("+") ? "+" : "") + value.replace(/\+/g, "");

      setForm((f) => ({
        ...f,
        [k]: value,
      }));
    };

  // ============================================================
  // AGE / DATE OF BIRTH VALIDATION
  // ============================================================

  const getMaximumDateOfBirth = (): string => {
    const today = new Date();

    const maximumDob = new Date(
      today.getFullYear() - 18,
      today.getMonth(),
      today.getDate(),
    );

    const year = maximumDob.getFullYear();

    const month = String(maximumDob.getMonth() + 1).padStart(2, "0");

    const day = String(maximumDob.getDate()).padStart(2, "0");

    return `${year}-${month}-${day}`;
  };

  /**
   * Checks whether the applicant is at least 18 years old.
   *
   * The birthday itself counts.
   *
   * Example:
   *
   * Today: 2026-08-11
   * DOB:   2008-08-11
   * Age:   18 -> VALID
   *
   * DOB:   2008-08-12
   * Age:   17 -> INVALID
   */
  const isAtLeast18 = (dateOfBirth: string): boolean => {
    if (!dateOfBirth) {
      return false;
    }

    const today = new Date();

    const dob = new Date(`${dateOfBirth}T00:00:00`);

    if (Number.isNaN(dob.getTime())) {
      return false;
    }

    // Prevent future DOBs.
    if (dob > today) {
      return false;
    }

    let age = today.getFullYear() - dob.getFullYear();

    const monthDifference = today.getMonth() - dob.getMonth();

    if (
      monthDifference < 0 ||
      (monthDifference === 0 && today.getDate() < dob.getDate())
    ) {
      age--;
    }

    return age >= 18;
  };

  const maximumDateOfBirth = getMaximumDateOfBirth();

  // ============================================================
  // SUBMIT APPLICATION
  // ============================================================

  const handleSubmit = async () => {
    setSaving(true);
    setError("");

    // ----------------------------------------------------------
    // FINAL CLIENT-SIDE AGE VALIDATION
    // ----------------------------------------------------------

    if (!form.dateOfBirth) {
      setError("Date of birth is required.");
      setSaving(false);
      return;
    }

    if (!isAtLeast18(form.dateOfBirth)) {
      setError(
        "The applicant must be at least 18 years old to apply for a loan.",
      );
      setSaving(false);
      return;
    }

    // ----------------------------------------------------------
    // IDEMPOTENCY KEY
    // ----------------------------------------------------------

    if (!idempotencyKeyRef.current) {
      idempotencyKeyRef.current =
        typeof crypto !== "undefined" && typeof crypto.randomUUID === "function"
          ? crypto.randomUUID()
          : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
    }

    const idempotencyKey = idempotencyKeyRef.current;

    // ----------------------------------------------------------
    // OFFLINE SUBMISSION
    // ----------------------------------------------------------

    if (!online) {
      try {
        await queueAction({
          url: "/public/loan-application",
          method: "POST",
          body: {
            ...form,
            tenantSlug: slug,
          },
          headers: {
            "Content-Type": "application/json",
            "Idempotency-Key": idempotencyKey,
          },
          label: `Loan application — ${form.firstName} ${form.lastName} (${form.amount} ${tenant.currency})`,
        });

        setReference("Will be assigned once submitted");

        setQueuedOffline(true);
        setSubmitted(true);
      } catch (e: any) {
        setError(
          "Could not save your application on this device. Please try again once you&apos;re back online.",
        );
      } finally {
        setSaving(false);
      }

      return;
    }

    // ----------------------------------------------------------
    // ONLINE SUBMISSION
    // ----------------------------------------------------------

    let responseStatus: number | null = null;

    try {
      const API_BASE =
        process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api";

      const res = await fetch(`${API_BASE}/public/loan-application`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Idempotency-Key": idempotencyKey,
        },
        body: JSON.stringify({
          ...form,
          tenantSlug: slug,
        }),
      });

      responseStatus = res.status;
      const json = await res.json();

      if (!res.ok || json.success === false) {
        throw new Error(
          json.error ||
            json.message ||
            "Could not submit your application. Please try again.",
        );
      }

      setReference(json.data?.reference || "");

      setSubmitted(true);

      idempotencyKeyRef.current = null;
    } catch (e: any) {
      // --------------------------------------------------------
      // NETWORK FAILURE
      // --------------------------------------------------------

      if (
        e instanceof TypeError ||
        responseStatus === 408 ||
        responseStatus === 425 ||
        responseStatus === 429 ||
        (responseStatus !== null && responseStatus >= 500)
      ) {
        try {
          await queueAction({
            url: "/public/loan-application",
            method: "POST",
            body: {
              ...form,
              tenantSlug: slug,
            },
            headers: {
              "Content-Type": "application/json",
              "Idempotency-Key": idempotencyKey,
            },
            label: `Loan application — ${form.firstName} ${form.lastName} (${form.amount} ${tenant.currency})`,
          });

          setReference("Will be assigned once submitted");

          setQueuedOffline(true);
          setSubmitted(true);
        } catch {
          setError(
            "Lost connection and could not save on this device. Please try again.",
          );
        }
      } else {
        setError(
          e.message ||
            "Something went wrong. Please check your connection and try again.",
        );
      }
    } finally {
      setSaving(false);
    }
  };

  // ============================================================
  // STEPS
  // ============================================================

  const steps = [
    {
      n: 1,
      label: "Personal Info",
    },
    {
      n: 2,
      label: "Employment",
    },
    {
      n: 3,
      label: "Loan Details",
    },
    {
      n: 4,
      label: "Confirm",
    },
  ];

  // ============================================================
  // SUBMITTED SCREEN
  // ============================================================

  if (submitted) {
    return (
      <div className="min-h-screen flex items-center justify-center px-4 py-20">
        <div className="max-w-xl w-full text-center">
          <div
            className="w-24 h-24 rounded-full flex items-center justify-center text-5xl mx-auto mb-6 shadow-xl"
            style={{
              backgroundColor: primary + "10",
            }}
          >
            {queuedOffline ? "📡" : docsComplete ? "🎉" : "📎"}
          </div>

          <h2 className="text-3xl font-extrabold text-gray-900 mb-4">
            {queuedOffline
              ? "Saved — Will Submit Automatically"
              : docsComplete
                ? "All Set — Application Complete!"
                : "One More Step — Upload Your Documents"}
          </h2>

          <p className="text-gray-600 mb-6 text-lg">
            {queuedOffline ? (
              <>
                Thanks <strong>{form.firstName}</strong> — you&apos;re offline
                right now, so we&apos;ve saved your application on this device.
                It will submit itself the moment this device reconnects to the
                internet. You don&apos;t need to do anything else — just
                don&apos;t clear your browser data before then.
              </>
            ) : docsComplete ? (
              <>
                Thank you <strong>{form.firstName}</strong>! Your application
                and all required documents have been received. We will review
                everything and contact you within <strong>24–48 hours</strong>.
              </>
            ) : (
              <>
                Thanks <strong>{form.firstName}</strong> — your application
                details are saved, but it isn&apos;t complete yet. Please upload
                the required documents below so our team can begin reviewing it
                — applications without documents can&apos;t be processed.
              </>
            )}
          </p>

          <div className="bg-gray-50 rounded-2xl p-6 text-left space-y-3 text-sm mb-8">
            <div className="flex justify-between">
              <span className="text-gray-500">Application Reference</span>

              <code className="font-bold text-gray-800">
                {reference || "—"}
              </code>
            </div>

            <div className="flex justify-between">
              <span className="text-gray-500">Loan Type</span>

              <span className="font-bold">{form.loanType}</span>
            </div>

            <div className="flex justify-between">
              <span className="text-gray-500">Amount Requested</span>

              <span className="font-bold">
                {tenant.currency} {Number(form.amount).toLocaleString()}
              </span>
            </div>

            <div className="flex justify-between">
              <span className="text-gray-500">Contact</span>

              <span className="font-bold">{form.phone}</span>
            </div>
          </div>

          {!queuedOffline && reference && !docsComplete && (
            <div className="bg-amber-50 border border-amber-200 text-amber-800 text-sm rounded-xl px-4 py-3 mb-6 text-left">
              ⚠️ <strong>Your application is not yet complete.</strong> Upload
              the documents below to send it for review. You can safely bookmark
              this page or use "Track Your Application" later — your progress is
              saved.
            </div>
          )}

          {!queuedOffline && reference && (
            <div className="text-left mb-8">
              <DocumentUploadPanel
                reference={reference}
                phone={form.phone}
                maritalStatus={form.maritalStatus}
                primary={primary}
                onStatusChange={setDocsComplete}
              />
            </div>
          )}

          <p className="text-gray-500 text-sm mb-4">
            Questions? Call us at <strong>{tenant.contactPhone}</strong> or
            email <strong>{tenant.contactEmail}</strong>
          </p>

          {!queuedOffline && reference && (
            <a
              href="/track"
              className="inline-block px-6 py-2.5 rounded-md text-sm font-bold text-white shadow-sm hover:opacity-90 transition-opacity"
              style={{
                backgroundColor: primary,
              }}
            >
              Track Your Application →
            </a>
          )}
        </div>
      </div>
    );
  }

  // ============================================================
  // MAIN APPLICATION
  // ============================================================

  return (
    <div>
      {/* HEADER */}

      <section
        className="py-12 text-white text-center"
        style={{
          background: `linear-gradient(135deg, ${primary}, #0a4a2b)`,
        }}
      >
        <h1 className="text-3xl md:text-4xl font-extrabold mb-2">
          Loan Application
        </h1>

        <p className="text-white/70">
          Complete the form below — takes less than 5 minutes
        </p>
      </section>

      <div className="max-w-2xl mx-auto px-4 py-12">
        {/* PROGRESS */}

        <div className="flex items-center justify-center gap-0 mb-10">
          {steps.map((s, i) => (
            <div key={s.n} className="flex items-center">
              <div className="flex flex-col items-center">
                <div
                  className={`
                    w-10 h-10 rounded-full
                    flex items-center justify-center
                    font-bold text-sm transition-all
                    ${
                      step > s.n
                        ? "text-white"
                        : step === s.n
                          ? "text-white ring-4 ring-offset-2"
                          : "bg-gray-100 text-gray-400"
                    }
                  `}
                  style={
                    step >= s.n
                      ? {
                          backgroundColor: primary,
                          boxShadow: `0 0 0 4px ${primary}40`,
                        }
                      : undefined
                  }
                >
                  {step > s.n ? "✓" : s.n}
                </div>

                <div
                  className={`
                    text-xs mt-1 font-semibold
                    hidden md:block
                    ${step === s.n ? "text-gray-900" : "text-gray-400"}
                  `}
                >
                  {s.label}
                </div>
              </div>

              {i < steps.length - 1 && (
                <div
                  className="w-12 h-0.5 mb-4 transition-all"
                  style={{
                    backgroundColor: step > s.n ? primary : "#E5E7EB",
                  }}
                />
              )}
            </div>
          ))}
        </div>

        <div className="bg-white rounded-3xl shadow-sm border border-gray-100 p-8">
          {/* ================================================== */}
          {/* STEP 1 — PERSONAL INFORMATION */}
          {/* ================================================== */}

          {step === 1 && (
            <div>
              <h2 className="text-xl font-extrabold text-gray-900 mb-6">
                Personal Information
              </h2>

              <div className="grid grid-cols-2 gap-4">
                <Field label="First Name" required>
                  <input
                    required
                    className={inp}
                    value={form.firstName}
                    onChange={set("firstName")}
                  />
                </Field>

                <Field label="Last Name" required>
                  <input
                    required
                    className={inp}
                    value={form.lastName}
                    onChange={set("lastName")}
                  />
                </Field>

                <Field label="Email Address">
                  <input
                    type="email"
                    className={inp}
                    value={form.email}
                    onChange={set("email")}
                  />
                </Field>

                <Field
                  label="Phone Number"
                  required
                  hint={
                    form.phone && !isPhoneValid(form.phone)
                      ? undefined
                      : "Include country code, e.g. +2507XXXXXXXX"
                  }
                >
                  <input
                    required
                    inputMode="tel"
                    className={inp}
                    placeholder="+250 7XX XXX XXX"
                    value={form.phone}
                    onChange={setPhone("phone")}
                  />

                  {form.phone && !isPhoneValid(form.phone) && (
                    <p className="text-xs mt-1 text-red-600 font-semibold">
                      Enter a valid number with country code, digits only (e.g.
                      +2507XXXXXXXX)
                    </p>
                  )}
                </Field>

                <Field
                  label="National ID"
                  required
                  hint={
                    form.nationalId && !isNationalIdValid(form.nationalId)
                      ? undefined
                      : "16 digits, numbers only"
                  }
                >
                  <input
                    required
                    inputMode="numeric"
                    maxLength={16}
                    className={inp}
                    value={form.nationalId}
                    onChange={setNationalId("nationalId")}
                  />

                  {form.nationalId && !isNationalIdValid(form.nationalId) && (
                    <p className="text-xs mt-1 text-red-600 font-semibold">
                      National ID must be exactly 16 digits
                    </p>
                  )}
                </Field>

                {/* ================================================= */}
                {/* DATE OF BIRTH — 18+ AGE RESTRICTION */}
                {/* ================================================= */}

                <Field
                  label="Date of Birth"
                  required
                  hint="You must be at least 18 years old to apply for a loan."
                >
                  <input
                    required
                    type="date"
                    max={maximumDateOfBirth}
                    className={inp}
                    value={form.dateOfBirth}
                    onChange={set("dateOfBirth")}
                  />

                  {form.dateOfBirth && !isAtLeast18(form.dateOfBirth) && (
                    <p className="text-xs mt-1 text-red-600 font-semibold">
                      You must be at least 18 years old to apply for a loan.
                    </p>
                  )}
                </Field>

                <Field label="Gender">
                  <select
                    className={inp}
                    value={form.gender}
                    onChange={set("gender")}
                  >
                    <option value="">Select…</option>

                    {["Male", "Female", "Other"].map((g) => (
                      <option key={g} value={g}>
                        {g}
                      </option>
                    ))}
                  </select>
                </Field>

                <Field label="Marital Status">
                  <select
                    className={inp}
                    value={form.maritalStatus}
                    onChange={set("maritalStatus")}
                  >
                    <option value="">Select…</option>

                    {["Single", "Married", "Divorced", "Widowed"].map((m) => (
                      <option key={m} value={m}>
                        {m}
                      </option>
                    ))}
                  </select>
                </Field>

                {form.maritalStatus === "Single" && (
                  <Field
                    label="Single Status Certificate Number"
                    required
                    hint="Issued by your Sector/Cell office (Icyemezo cy'ubudashyingiwe)"
                  >
                    <input
                      required
                      className={inp}
                      value={form.singleCertificateNumber}
                      onChange={set("singleCertificateNumber")}
                      placeholder="Certificate reference number"
                    />
                  </Field>
                )}

                {form.maritalStatus === "Married" && (
                  <>
                    <Field label="Spouse Full Name" required>
                      <input
                        required
                        className={inp}
                        value={form.spouseFullName}
                        onChange={set("spouseFullName")}
                      />
                    </Field>

                    <Field label="Spouse National ID">
                      <input
                        inputMode="numeric"
                        maxLength={16}
                        className={inp}
                        value={form.spouseNationalId}
                        onChange={setNationalId("spouseNationalId")}
                      />

                      {form.spouseNationalId &&
                        !isNationalIdValid(form.spouseNationalId) && (
                          <p className="text-xs mt-1 text-red-600 font-semibold">
                            Must be exactly 16 digits
                          </p>
                        )}
                    </Field>

                    <Field label="Spouse Phone">
                      <input
                        inputMode="tel"
                        className={inp}
                        placeholder="+250 7XX XXX XXX"
                        value={form.spousePhone}
                        onChange={setPhone("spousePhone")}
                      />

                      {form.spousePhone && !isPhoneValid(form.spousePhone) && (
                        <p className="text-xs mt-1 text-red-600 font-semibold">
                          Enter a valid number with country code
                        </p>
                      )}
                    </Field>

                    <div className="sm:col-span-2">
                      <label className="flex items-start gap-2.5 text-sm text-gray-600 cursor-pointer">
                        <input
                          type="checkbox"
                          className="mt-1"
                          checked={form.spouseConsent}
                          onChange={(e) =>
                            setForm((f) => ({
                              ...f,
                              spouseConsent: e.target.checked,
                            }))
                          }
                        />
                        My spouse consents to this loan application and any use
                        of jointly-owned property as collateral.
                      </label>
                    </div>
                  </>
                )}
              </div>

              <Field label="Home Address" required>
                <input
                  required
                  className={inp}
                  placeholder="Street, Sector"
                  value={form.address}
                  onChange={set("address")}
                />
              </Field>

              <div className="grid grid-cols-2 gap-4">
                <Field label="City/District" required>
                  <input
                    required
                    className={inp}
                    value={form.city}
                    onChange={set("city")}
                  />
                </Field>

                <Field label="Province">
                  <select
                    className={inp}
                    value={form.province}
                    onChange={set("province")}
                  >
                    <option value="">Select…</option>

                    {[
                      "Kigali",
                      "Northern",
                      "Southern",
                      "Eastern",
                      "Western",
                    ].map((p) => (
                      <option key={p} value={p}>
                        {p}
                      </option>
                    ))}
                  </select>
                </Field>
              </div>
            </div>
          )}

          {/* ================================================== */}
          {/* STEP 2 — EMPLOYMENT */}
          {/* ================================================== */}

          {step === 2 && (
            <div>
              <h2 className="text-xl font-extrabold text-gray-900 mb-6">
                Employment & Financial Info
              </h2>

              <Field label="Employment Type" required>
                <select
                  required
                  className={inp}
                  value={form.employmentType}
                  onChange={set("employmentType")}
                >
                  {[
                    "EMPLOYED",
                    "SELF_EMPLOYED",
                    "BUSINESS_OWNER",
                    "FARMER",
                    "RETIRED",
                    "OTHER",
                  ].map((t) => (
                    <option key={t} value={t}>
                      {t.replace(/\_/g, " ")}
                    </option>
                  ))}
                </select>
              </Field>

              {form.employmentType === "OTHER" && (
                <Field label="Please Specify" required>
                  <input
                    required
                    className={inp}
                    placeholder="Describe your employment situation"
                    value={form.employmentTypeOther}
                    onChange={set("employmentTypeOther")}
                  />
                </Field>
              )}

              {form.employmentType === "BUSINESS_OWNER" && (
                <Field label="Nature of Business" required>
                  <input
                    required
                    className={inp}
                    placeholder="e.g. Retail shop, restaurant, transport"
                    value={form.natureOfBusiness}
                    onChange={set("natureOfBusiness")}
                  />
                </Field>
              )}

              <div className="grid grid-cols-2 gap-4">
                {!["SELF_EMPLOYED", "FARMER"].includes(form.employmentType) && (
                  <>
                    <Field label="Employer / Business Name">
                      <input
                        className={inp}
                        value={form.employerName}
                        onChange={set("employerName")}
                      />
                    </Field>

                    <Field label="Job Title / Occupation">
                      <input
                        className={inp}
                        value={form.jobTitle}
                        onChange={set("jobTitle")}
                      />
                    </Field>
                  </>
                )}

                <Field label={`Monthly Income (${tenant.currency})`} required>
                  <input
                    required
                    type="number"
                    className={inp}
                    value={form.monthlyIncome}
                    onChange={set("monthlyIncome")}
                  />
                </Field>

                <Field label={`Monthly Expenses (${tenant.currency})`}>
                  <input
                    type="number"
                    className={inp}
                    value={form.monthlyExpenses}
                    onChange={set("monthlyExpenses")}
                  />
                </Field>
              </div>

              <div className="bg-blue-50 border border-blue-200 rounded-2xl p-4 text-sm text-blue-700 mt-4">
                ℹ️ Your income and expense information helps us determine the
                best loan terms for you. All information is kept strictly
                confidential.
              </div>
            </div>
          )}

          {/* ================================================== */}
          {/* STEP 3 — LOAN DETAILS */}
          {/* ================================================== */}

          {step === 3 && (
            <div>
              <h2 className="text-xl font-extrabold text-gray-900 mb-6">
                Loan Details
              </h2>

              <Field label="Loan Type" required>
                <select
                  required
                  className={inp}
                  value={form.loanType}
                  onChange={set("loanType")}
                >
                  {tenant.services?.map((s) => (
                    <option key={s.title} value={s.title}>
                      {s.title}
                    </option>
                  ))}
                </select>
              </Field>

              <div className="grid grid-cols-2 gap-4">
                <Field label={`Amount Requested (${tenant.currency})`} required>
                  <input
                    required
                    type="number"
                    min={minimumLoanAmount}
                    className={inp}
                    value={form.amount}
                    onChange={set("amount")}
                  />

                  {(() => {
                    const svc = tenant.services?.find(
                      (s) => s.title === form.loanType,
                    );

                    if (!svc) {
                      return null;
                    }

                    const min = Number(svc.minAmount ?? 500000);
                    const max =
                      svc.maxAmount === null ||
                      svc.maxAmount === undefined ||
                      svc.maxAmount === ""
                        ? null
                        : Number(svc.maxAmount);

                    const over =
                      form.amount && max !== null && Number(form.amount) > max;
                    const belowMinimum =
                      form.amount && Number(form.amount) < min;

                    return (
                      <p
                        className={`text-xs mt-1 ${
                          over ? "text-red-600 font-semibold" : "text-gray-400"
                        }`}
                      >
                        {over
                          ? `This exceeds the ${max!.toLocaleString()} ${tenant.currency} limit for ${form.loanType}`
                          : belowMinimum
                            ? `${form.loanType} minimum is ${min.toLocaleString()} ${tenant.currency}`
                            : max !== null
                              ? `${form.loanType}: ${min.toLocaleString()}–${max.toLocaleString()} ${tenant.currency}`
                              : `${form.loanType}: minimum ${min.toLocaleString()} ${tenant.currency}; no maximum configured`}
                      </p>
                    );
                  })()}
                </Field>

                <Field label="Repayment Period" required>
                  <select
                    required
                    className={inp}
                    value={form.durationMonths}
                    onChange={set("durationMonths")}
                  >
                    {Array.from(
                      {
                        length: Math.max(
                          1,
                          maximumTermMonths - minimumTermMonths + 1,
                        ),
                      },
                      (_, index) => minimumTermMonths + index,
                    ).map((m) => (
                      <option key={m} value={m}>
                        {m} month
                        {m > 1 ? "s" : ""}
                      </option>
                    ))}
                  </select>
                </Field>
              </div>

              <Field label="Purpose of Loan" required>
                <textarea
                  required
                  className={`${inp} min-h-[80px] resize-y`}
                  placeholder="Describe how you will use this loan…"
                  value={form.purpose}
                  onChange={set("purpose")}
                />
              </Field>

              <div className="grid grid-cols-2 gap-4">
                <Field label="Collateral (if any)">
                  <input
                    className={inp}
                    placeholder="e.g. Land title, vehicle logbook"
                    value={form.collateral}
                    onChange={set("collateral")}
                  />
                </Field>

                <Field label={`Collateral Value (${tenant.currency})`}>
                  <input
                    type="number"
                    className={inp}
                    value={form.collateralValue}
                    onChange={set("collateralValue")}
                  />
                </Field>
              </div>

              {/* MINI LOAN SUMMARY */}

              {form.amount && (
                <div className="mt-4 bg-gray-50 rounded-2xl p-4 border border-gray-100">
                  <div className="text-xs font-bold text-gray-400 uppercase tracking-wider mb-3">
                    Estimated Repayment
                  </div>

                  <div className="grid grid-cols-2 gap-3 text-center text-sm">
                    {(() => {
                      const principal = Number(form.amount);
                      const months = Number(form.durationMonths);
                      const interestRate = Number(
                        selectedService?.interestRate ??
                          selectedService?.rate ??
                          5,
                      );
                      const managementRate = Number(
                        selectedService?.managementFeeRate ?? 5,
                      );
                      const applicationRate = Number(
                        selectedService?.applicationFeeRate ?? 2,
                      );

                      let balance = Math.max(0, principal);
                      let interest = 0;
                      let management = 0;
                      let firstInstallment = 0;

                      for (let i = 1; i <= months; i += 1) {
                        const remaining = months - i + 1;
                        const principalComponent =
                          remaining === 1
                            ? balance
                            : Math.round((balance / remaining) * 100) / 100;
                        const monthInterest =
                          Math.round(balance * (interestRate / 100) * 100) /
                          100;
                        const monthManagement =
                          Math.round(balance * (managementRate / 100) * 100) /
                          100;
                        const installment =
                          Math.round(
                            (principalComponent +
                              monthInterest +
                              monthManagement) *
                              100,
                          ) / 100;

                        if (i === 1) firstInstallment = installment;
                        interest =
                          Math.round((interest + monthInterest) * 100) / 100;
                        management =
                          Math.round((management + monthManagement) * 100) /
                          100;
                        balance = Math.max(
                          0,
                          Math.round((balance - principalComponent) * 100) /
                            100,
                        );
                      }

                      const application =
                        Math.round(principal * (applicationRate / 100) * 100) /
                        100;
                      const contractualTotal =
                        Math.round((principal + interest + management) * 100) /
                        100;

                      return [
                        [
                          "First installment",
                          `${tenant.currency} ${firstInstallment.toLocaleString(
                            "en",
                            {
                              maximumFractionDigits: 0,
                            },
                          )}`,
                        ],
                        [
                          "Repayment total",
                          `${tenant.currency} ${contractualTotal.toLocaleString(
                            "en",
                            {
                              maximumFractionDigits: 0,
                            },
                          )}`,
                        ],
                        [
                          "Processing fee",
                          `${tenant.currency} ${application.toLocaleString(
                            "en",
                            {
                              maximumFractionDigits: 0,
                            },
                          )}`,
                        ],
                        ["Rate", `${interestRate}% / mo`],
                      ].map(([l, v]) => (
                        <div key={l}>
                          <div className="text-gray-400 text-[10px]">{l}</div>
                          <div
                            className="font-extrabold"
                            style={{ color: primary }}
                          >
                            {v}
                          </div>
                        </div>
                      ));
                    })()}
                  </div>
                </div>
              )}
            </div>
          )}

          {/* ================================================== */}
          {/* STEP 4 — DECLARATION */}
          {/* ================================================== */}

          {step === 4 && (
            <div>
              <h2 className="text-xl font-extrabold text-gray-900 mb-6">
                Review & Declaration
              </h2>

              <div className="space-y-3 bg-gray-50 rounded-2xl p-6 mb-6 text-sm">
                {[
                  ["Name", `${form.firstName} ${form.lastName}`],
                  ["ID", form.nationalId],
                  ["Date of Birth", form.dateOfBirth],
                  ["Phone", form.phone],
                  ["Loan Type", form.loanType],
                  [
                    "Amount",
                    `${tenant.currency} ${Number(
                      form.amount,
                    ).toLocaleString()}`,
                  ],
                  ["Term", `${form.durationMonths} months`],
                  [
                    "Employment",
                    form.employmentType === "OTHER" && form.employmentTypeOther
                      ? `Other — ${form.employmentTypeOther}`
                      : form.employmentType.replace(/\_/g, " "),
                  ],
                  ...(form.employmentType === "BUSINESS_OWNER" &&
                  form.natureOfBusiness
                    ? [["Nature of Business", form.natureOfBusiness]]
                    : []),
                  [
                    "Income",
                    `${tenant.currency} ${Number(
                      form.monthlyIncome,
                    ).toLocaleString()}/mo`,
                  ],
                ].map(([l, v]) => (
                  <div
                    key={l}
                    className="flex justify-between border-b border-gray-200 last:border-0 pb-2 last:pb-0"
                  >
                    <span className="text-gray-500">{l}</span>

                    <span className="font-semibold text-gray-800">{v}</span>
                  </div>
                ))}
              </div>

              {error && (
                <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded-lg px-4 py-3 mb-4">
                  {error}
                </div>
              )}

              <label className="flex items-start gap-3 cursor-pointer mb-6">
                <input
                  type="checkbox"
                  checked={form.acceptedTerms}
                  onChange={(e) =>
                    setForm((f) => ({
                      ...f,
                      acceptedTerms: e.target.checked,
                    }))
                  }
                  className="mt-1 w-4 h-4 rounded"
                  style={{
                    accentColor: primary,
                  }}
                />

                <span className="text-sm text-gray-700">
                  I confirm that all information provided is true and accurate.
                  I authorize {tenant.name} to verify my information and contact
                  me regarding this application. I agree to the{" "}
                  <a
                    href="#"
                    className="font-semibold underline ml-1"
                    style={{
                      color: primary,
                    }}
                  >
                    Terms &amp; Conditions
                  </a>
                  .
                </span>
              </label>
            </div>
          )}

          {/* ================================================== */}
          {/* NAVIGATION */}
          {/* ================================================== */}

          <div className="flex justify-between mt-8 pt-6 border-t border-gray-100">
            {step > 1 ? (
              <button
                onClick={() => setStep((s) => (s - 1) as Step)}
                className="px-6 py-2.5 rounded-xl font-semibold text-gray-600 border border-gray-200 hover:bg-gray-50 transition"
              >
                ← Back
              </button>
            ) : (
              <div />
            )}

            {step < 4 ? (
              <button
                onClick={() => setStep((s) => (s + 1) as Step)}
                disabled={
                  // ------------------------------------------
                  // STEP 1
                  // ------------------------------------------

                  (step === 1 && !form.dateOfBirth) ||
                  (step === 1 && !isAtLeast18(form.dateOfBirth)) ||
                  (step === 1 && !isNationalIdValid(form.nationalId)) ||
                  (step === 1 && !isPhoneValid(form.phone)) ||
                  (step === 1 &&
                    form.maritalStatus === "Single" &&
                    !form.singleCertificateNumber) ||
                  (step === 1 &&
                    form.maritalStatus === "Married" &&
                    !form.spouseFullName) ||
                  (step === 1 &&
                    form.maritalStatus === "Married" &&
                    !!form.spouseNationalId &&
                    !isNationalIdValid(form.spouseNationalId)) ||
                  (step === 1 &&
                    form.maritalStatus === "Married" &&
                    !!form.spousePhone &&
                    !isPhoneValid(form.spousePhone)) ||
                  // ------------------------------------------
                  // STEP 2
                  // ------------------------------------------

                  (step === 2 &&
                    form.employmentType === "OTHER" &&
                    !form.employmentTypeOther) ||
                  (step === 2 &&
                    form.employmentType === "BUSINESS_OWNER" &&
                    !form.natureOfBusiness) ||
                  // ------------------------------------------
                  // STEP 3
                  // ------------------------------------------

                  (step === 3 &&
                    (() => {
                      const svc = tenant.services?.find(
                        (s) =>
                          s.title === form.loanType ||
                          s.loanType === form.loanType,
                      );

                      if (!svc) return true;

                      return (
                        Number(form.amount) < Number(svc.minAmount ?? 500000) ||
                        (svc.maxAmount !== null &&
                          svc.maxAmount !== undefined &&
                          svc.maxAmount !== "" &&
                          Number(form.amount) > Number(svc.maxAmount))
                      );
                    })())
                }
                className="px-8 py-2.5 rounded-xl font-bold text-white transition hover:opacity-90 disabled:opacity-50 disabled:cursor-not-allowed"
                style={{
                  backgroundColor: primary,
                }}
              >
                Continue →
              </button>
            ) : (
              <button
                onClick={handleSubmit}
                disabled={
                  !form.acceptedTerms ||
                  saving ||
                  !form.dateOfBirth ||
                  !isAtLeast18(form.dateOfBirth)
                }
                className="px-10 py-3 rounded-xl font-bold text-white transition hover:opacity-90 disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
                style={{
                  backgroundColor: accent,
                }}
              >
                {saving && (
                  <span className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                )}

                {saving ? "Submitting…" : "Submit Application ✓"}
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

// ============================================================
// INPUT STYLING
// ============================================================

const inp =
  "w-full px-3 py-2.5 border border-gray-300 rounded-xl text-sm focus:outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-500/20 transition-colors mt-1.5";

// ============================================================
// FIELD COMPONENT
// ============================================================

function Field({
  label,
  required,
  hint,
  children,
}: {
  label: string;
  required?: boolean;
  hint?: string;
  children: React.ReactNode;
}) {
  return (
    <div className="mb-4">
      <label className="text-xs font-bold text-gray-500 uppercase tracking-wider">
        {label}

        {required && <span className="text-red-500 ml-1">*</span>}
      </label>

      {children}

      {hint && (
        <p className="text-xs text-gray-400 mt-1 normal-case font-normal">
          {hint}
        </p>
      )}
    </div>
  );
}
