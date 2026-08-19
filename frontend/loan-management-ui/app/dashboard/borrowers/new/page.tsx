"use client";

import React, { useCallback, useRef, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";

import { createBorrower } from "../../../../services/borrowerService";
import { toast } from "../../../../hooks/useToast";

type FormErrors = Record<string, string>;

type DuplicateBorrower = {
  id: number;
  firstName: string;
  lastName: string;
  matchedOn: string;
};

type ApiErrorShape = {
  status?: number;
  message?: string;
  data?: {
    existingBorrower?: DuplicateBorrower;
    message?: string;
    error?: string;
  };
};

/* -------------------------------------------------------------------------- */
/* Helpers                                                                    */
/* -------------------------------------------------------------------------- */

const NAME_PATTERN = /^[A-Za-zÀ-ÿ' -]+$/;

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/**
 * Rwanda phone validation.
 *
 * Accepts common forms such as:
 * +250788123456
 * +250 788 123 456
 * 0788123456
 * 0788 123 456
 */
const RWANDA_PHONE_PATTERN =
  /^(?:\+250\s?7\d{2}\s?\d{3}\s?\d{3}|07\d{2}\s?\d{3}\s?\d{3})$/;

function normalizePhone(value: string): string {
  const cleaned = value.replace(/[^\d+]/g, "");

  if (cleaned.startsWith("+250")) {
    return `+250${cleaned.slice(4)}`;
  }

  if (cleaned.startsWith("0")) {
    return cleaned;
  }

  return cleaned;
}

function normalizeNationalId(value: string): string {
  return value.replace(/\D/g, "").slice(0, 16);
}

function normalizeName(value: string): string {
  return value.replace(/\s+/g, " ").trim();
}

function getErrorMessage(error: unknown): string {
  if (error instanceof Error && error.message) {
    return error.message;
  }

  if (
    error &&
    typeof error === "object" &&
    "message" in error &&
    typeof (error as { message?: unknown }).message === "string"
  ) {
    return (error as { message: string }).message;
  }

  return "Something went wrong while creating the borrower.";
}

function getApiError(error: unknown): ApiErrorShape {
  if (!error || typeof error !== "object") {
    return {};
  }

  return error as ApiErrorShape;
}

/* -------------------------------------------------------------------------- */
/* Reusable UI components                                                     */
/* -------------------------------------------------------------------------- */

function Field({
  label,
  error,
  required,
  hint,
  id,
  children,
}: {
  label: string;
  error?: string;
  required?: boolean;
  hint?: string;
  id: string;
  children: React.ReactNode;
}) {
  const errorId = `${id}-error`;
  const hintId = `${id}-hint`;

  return (
    <div className="min-w-0">
      <div className="mb-1.5 flex items-baseline justify-between gap-3">
        <label
          htmlFor={id}
          className="block text-sm font-semibold text-slate-700"
        >
          {label}
          {required && (
            <span className="ml-1 text-red-500" aria-label="required">
              *
            </span>
          )}
        </label>

        {hint && (
          <span id={hintId} className="text-xs text-slate-400">
            {hint}
          </span>
        )}
      </div>

      {children}

      {error && (
        <p
          id={errorId}
          role="alert"
          className="mt-1.5 flex items-start gap-1.5 text-xs font-medium text-red-600"
        >
          <svg
            className="mt-0.5 h-3.5 w-3.5 shrink-0"
            viewBox="0 0 20 20"
            fill="currentColor"
            aria-hidden="true"
          >
            <path
              fillRule="evenodd"
              d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z"
              clipRule="evenodd"
            />
          </svg>

          <span>{error}</span>
        </p>
      )}
    </div>
  );
}

function inputClass(error?: string): string {
  return [
    "w-full rounded-xl border bg-white px-3.5 py-3 text-sm text-slate-900",
    "placeholder:text-slate-400",
    "outline-none transition",
    "disabled:cursor-not-allowed disabled:bg-slate-50 disabled:text-slate-500",
    error
      ? "border-red-300 bg-red-50/40 focus:border-red-500 focus:ring-4 focus:ring-red-100"
      : "border-slate-200 hover:border-slate-300 focus:border-blue-600 focus:ring-4 focus:ring-blue-100",
  ].join(" ");
}

function SectionHeader({
  number,
  title,
  description,
}: {
  number: string;
  title: string;
  description: string;
}) {
  return (
    <div className="flex items-start gap-4">
      <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-blue-50 text-sm font-bold text-blue-700 ring-1 ring-blue-100">
        {number}
      </div>

      <div>
        <h2 className="text-base font-bold text-slate-900">{title}</h2>

        <p className="mt-0.5 text-sm leading-5 text-slate-500">{description}</p>
      </div>
    </div>
  );
}

function FormDivider() {
  return <div className="border-t border-slate-100" />;
}

/* -------------------------------------------------------------------------- */
/* Page                                                                       */
/* -------------------------------------------------------------------------- */

export default function NewBorrowerPage() {
  const router = useRouter();

  const firstNameRef = useRef<HTMLInputElement | null>(null);

  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [nationalId, setNationalId] = useState("");
  const [address, setAddress] = useState("");
  const [creditScore, setCreditScore] = useState("");

  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState<FormErrors>({});
  const [serverError, setServerError] = useState("");

  const [duplicateBorrower, setDuplicateBorrower] =
    useState<DuplicateBorrower | null>(null);

  /* ------------------------------------------------------------------------ */
  /* Validation                                                               */
  /* ------------------------------------------------------------------------ */

  const validate = useCallback((): boolean => {
    const nextErrors: FormErrors = {};

    const cleanFirstName = normalizeName(firstName);
    const cleanLastName = normalizeName(lastName);
    const cleanNationalId = normalizeNationalId(nationalId);
    const cleanPhone = normalizePhone(phone);
    const cleanEmail = email.trim();
    const cleanCreditScore = creditScore.trim();

    if (!cleanFirstName) {
      nextErrors.firstName = "First name is required.";
    } else if (cleanFirstName.length < 2) {
      nextErrors.firstName = "First name must contain at least 2 characters.";
    } else if (cleanFirstName.length > 100) {
      nextErrors.firstName = "First name cannot exceed 100 characters.";
    } else if (!NAME_PATTERN.test(cleanFirstName)) {
      nextErrors.firstName = "First name contains invalid characters.";
    }

    if (!cleanLastName) {
      nextErrors.lastName = "Last name is required.";
    } else if (cleanLastName.length < 2) {
      nextErrors.lastName = "Last name must contain at least 2 characters.";
    } else if (cleanLastName.length > 100) {
      nextErrors.lastName = "Last name cannot exceed 100 characters.";
    } else if (!NAME_PATTERN.test(cleanLastName)) {
      nextErrors.lastName = "Last name contains invalid characters.";
    }

    if (!cleanNationalId) {
      nextErrors.nationalId = "National ID is required.";
    } else if (!/^\d{16}$/.test(cleanNationalId)) {
      nextErrors.nationalId = "National ID must contain exactly 16 digits.";
    }

    if (!cleanPhone) {
      nextErrors.phone = "Phone number is required.";
    } else if (!RWANDA_PHONE_PATTERN.test(phone.trim())) {
      nextErrors.phone =
        "Enter a valid Rwanda phone number, for example +250 788 123 456.";
    }

    if (cleanEmail && !EMAIL_PATTERN.test(cleanEmail)) {
      nextErrors.email = "Enter a valid email address.";
    } else if (cleanEmail.length > 150) {
      nextErrors.email = "Email address cannot exceed 150 characters.";
    }

    if (address.trim().length > 255) {
      nextErrors.address = "Address cannot exceed 255 characters.";
    }

    if (cleanCreditScore) {
      const score = Number(cleanCreditScore);

      if (!Number.isInteger(score)) {
        nextErrors.creditScore = "Credit score must be a whole number.";
      } else if (score < 0 || score > 1000) {
        nextErrors.creditScore = "Credit score must be between 0 and 1000.";
      }
    }

    setErrors(nextErrors);

    return Object.keys(nextErrors).length === 0;
  }, [firstName, lastName, email, phone, nationalId, address, creditScore]);

  /* ------------------------------------------------------------------------ */
  /* Submit                                                                   */
  /* ------------------------------------------------------------------------ */

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (loading) {
      return;
    }

    setServerError("");
    setDuplicateBorrower(null);

    if (!validate()) {
      window.scrollTo({
        top: 0,
        behavior: "smooth",
      });

      requestAnimationFrame(() => {
        firstNameRef.current?.focus();
      });

      return;
    }

    setLoading(true);

    try {
      await createBorrower({
        firstName: normalizeName(firstName),
        lastName: normalizeName(lastName),
        email: email.trim() || undefined,
        phone: normalizePhone(phone),
        nationalId: normalizeNationalId(nationalId),
        addressLine1: address.trim() || undefined,
        creditScore: creditScore.trim() ? Number(creditScore) : undefined,
      });

      toast("success", "Borrower created successfully.");

      router.push("/dashboard/borrowers");
    } catch (error: unknown) {
      console.error("Create borrower error:", error);

      const apiError = getApiError(error);

      if (apiError.status === 409 && apiError.data?.existingBorrower) {
        setDuplicateBorrower(apiError.data.existingBorrower);
      } else {
        setServerError(
          apiError.data?.message ||
            apiError.data?.error ||
            getErrorMessage(error),
        );
      }

      window.scrollTo({
        top: 0,
        behavior: "smooth",
      });
    } finally {
      setLoading(false);
    }
  };

  /* ------------------------------------------------------------------------ */
  /* Render                                                                   */
  /* ------------------------------------------------------------------------ */

  return (
    <main className="min-h-full bg-slate-50">
      <div className="mx-auto w-full max-w-5xl px-4 py-6 sm:px-6 lg:px-8">
        {/* Page heading */}
        <div className="mb-6">
          <div className="mb-3 flex items-center gap-2 text-sm text-slate-500">
            <Link
              href="/dashboard/borrowers"
              className="transition hover:text-blue-600"
            >
              Borrowers
            </Link>

            <svg
              className="h-4 w-4"
              viewBox="0 0 20 20"
              fill="currentColor"
              aria-hidden="true"
            >
              <path
                fillRule="evenodd"
                d="M7.21 14.77a.75.75 0 010-1.06L10.92 10 7.21 6.29a.75.75 0 111.06-1.06l4.24 4.24a.75.75 0 010 1.06l-4.24 4.24a.75.75 0 01-1.06 0z"
                clipRule="evenodd"
              />
            </svg>

            <span className="font-medium text-slate-700">New Borrower</span>
          </div>

          <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <h1 className="text-2xl font-bold tracking-tight text-slate-950 sm:text-3xl">
                Create New Borrower
              </h1>

              <p className="mt-1.5 max-w-2xl text-sm leading-6 text-slate-500">
                Register a borrower profile securely. Required fields are marked
                with an asterisk.
              </p>
            </div>

            <Link
              href="/dashboard/borrowers"
              className="inline-flex w-fit items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 shadow-sm transition hover:border-slate-300 hover:bg-slate-50"
            >
              <svg
                className="h-4 w-4"
                viewBox="0 0 20 20"
                fill="currentColor"
                aria-hidden="true"
              >
                <path
                  fillRule="evenodd"
                  d="M12.79 14.77a.75.75 0 010-1.06L9.08 10l3.71-3.71a.75.75 0 10-1.06-1.06L7.49 9.47a.75.75 0 000 1.06l4.24 4.24a.75.75 0 001.06 0z"
                  clipRule="evenodd"
                />
              </svg>
              Back to Borrowers
            </Link>
          </div>
        </div>

        {/* Server error */}
        {serverError && (
          <div
            role="alert"
            className="mb-5 rounded-2xl border border-red-200 bg-red-50 p-4"
          >
            <div className="flex items-start gap-3">
              <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-red-100 text-red-600">
                <svg
                  className="h-5 w-5"
                  viewBox="0 0 20 20"
                  fill="currentColor"
                  aria-hidden="true"
                >
                  <path
                    fillRule="evenodd"
                    d="M8.257 3.099c.765-1.36 2.72-1.36 3.485 0l5.58 9.92c.75 1.334-.213 2.981-1.742 2.981H4.42c-1.53 0-2.492-1.647-1.742-2.98l5.58-9.921zM10 7a.75.75 0 01.75.75v2.5a.75.75 0 01-1.5 0v-2.5A.75.75 0 0110 7zm0 6a1 1 0 100-2 1 1 0 000 2z"
                    clipRule="evenodd"
                  />
                </svg>
              </div>

              <div className="min-w-0 flex-1">
                <p className="text-sm font-bold text-red-900">
                  Unable to create borrower
                </p>

                <p className="mt-1 text-sm leading-5 text-red-700">
                  {serverError}
                </p>
              </div>

              <button
                type="button"
                onClick={() => setServerError("")}
                className="rounded-lg px-2 py-1 text-xs font-semibold text-red-700 transition hover:bg-red-100"
              >
                Dismiss
              </button>
            </div>
          </div>
        )}

        {/* Duplicate borrower */}
        {duplicateBorrower && (
          <div
            role="alert"
            className="mb-5 overflow-hidden rounded-2xl border border-amber-200 bg-amber-50"
          >
            <div className="p-5">
              <div className="flex items-start gap-4">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-amber-100 text-amber-700">
                  <svg
                    className="h-5 w-5"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    aria-hidden="true"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2}
                      d="M12 9v3m0 4h.01M5.07 19h13.86a2 2 0 001.74-3L13.74 4a2 2 0 00-3.48 0L3.33 16a2 2 0 001.74 3z"
                    />
                  </svg>
                </div>

                <div className="min-w-0 flex-1">
                  <h2 className="text-sm font-bold text-amber-950">
                    Existing borrower detected
                  </h2>

                  <p className="mt-1 text-sm leading-6 text-amber-800">
                    A borrower already exists matching{" "}
                    <span className="font-semibold">
                      {duplicateBorrower.matchedOn}
                    </span>
                    .
                  </p>

                  <div className="mt-3 rounded-xl border border-amber-200 bg-white/70 p-3">
                    <p className="text-sm font-bold text-slate-900">
                      {duplicateBorrower.firstName} {duplicateBorrower.lastName}
                    </p>

                    <p className="mt-0.5 text-xs text-slate-500">
                      Borrower ID: {duplicateBorrower.id}
                    </p>
                  </div>

                  <p className="mt-3 text-xs leading-5 text-amber-800">
                    Do not create another borrower profile for the same person.
                    If the borrower needs another facility, create a new loan
                    against the existing profile.
                  </p>

                  <div className="mt-4 flex flex-col gap-2 sm:flex-row">
                    <Link
                      href={`/dashboard/borrowers/${duplicateBorrower.id}`}
                      className="inline-flex items-center justify-center rounded-xl bg-amber-600 px-4 py-2.5 text-xs font-bold text-white transition hover:bg-amber-700"
                    >
                      View Existing Profile
                    </Link>

                    <Link
                      href={`/dashboard/loans/new?borrowerId=${duplicateBorrower.id}`}
                      className="inline-flex items-center justify-center rounded-xl border border-amber-300 bg-white px-4 py-2.5 text-xs font-bold text-amber-800 transition hover:bg-amber-100"
                    >
                      Create Loan for Borrower
                    </Link>

                    <button
                      type="button"
                      onClick={() => setDuplicateBorrower(null)}
                      className="rounded-xl px-4 py-2.5 text-xs font-semibold text-amber-800 transition hover:bg-amber-100"
                    >
                      Close
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Form */}
        <form
          onSubmit={handleSubmit}
          noValidate
          className="overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-sm"
        >
          {/* Form header */}
          <div className="border-b border-slate-100 bg-gradient-to-r from-slate-50 to-white px-5 py-5 sm:px-7">
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-slate-900 text-white">
                <svg
                  className="h-5 w-5"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  aria-hidden="true"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={1.8}
                    d="M16 21v-2a4 4 0 00-4-4H6a4 4 0 00-4 4v2M9 11a4 4 0 100-8 4 4 0 000 8zM19 8v6M22 11h-6"
                  />
                </svg>
              </div>

              <div>
                <h2 className="text-base font-bold text-slate-900">
                  Borrower Information
                </h2>

                <p className="text-xs text-slate-500">
                  Enter verified customer information carefully.
                </p>
              </div>
            </div>
          </div>

          <div className="space-y-8 p-5 sm:p-7">
            {/* Personal information */}
            <section>
              <SectionHeader
                number="01"
                title="Personal Information"
                description="Provide the borrower's legal name and official identification."
              />

              <div className="mt-5 grid gap-5 sm:grid-cols-2">
                <Field
                  id="firstName"
                  label="First Name"
                  required
                  error={errors.firstName}
                >
                  <input
                    ref={firstNameRef}
                    id="firstName"
                    name="firstName"
                    type="text"
                    value={firstName}
                    autoComplete="given-name"
                    maxLength={100}
                    disabled={loading}
                    aria-invalid={Boolean(errors.firstName)}
                    aria-describedby={
                      errors.firstName ? "firstName-error" : undefined
                    }
                    onChange={(event) => {
                      setFirstName(event.target.value);

                      if (errors.firstName) {
                        setErrors((current) => ({
                          ...current,
                          firstName: "",
                        }));
                      }
                    }}
                    placeholder="e.g. Jean"
                    className={inputClass(errors.firstName)}
                  />
                </Field>

                <Field
                  id="lastName"
                  label="Last Name"
                  required
                  error={errors.lastName}
                >
                  <input
                    id="lastName"
                    name="lastName"
                    type="text"
                    value={lastName}
                    autoComplete="family-name"
                    maxLength={100}
                    disabled={loading}
                    aria-invalid={Boolean(errors.lastName)}
                    aria-describedby={
                      errors.lastName ? "lastName-error" : undefined
                    }
                    onChange={(event) => {
                      setLastName(event.target.value);

                      if (errors.lastName) {
                        setErrors((current) => ({
                          ...current,
                          lastName: "",
                        }));
                      }
                    }}
                    placeholder="e.g. Uwimana"
                    className={inputClass(errors.lastName)}
                  />
                </Field>

                <Field
                  id="nationalId"
                  label="National ID"
                  required
                  hint="16 digits"
                  error={errors.nationalId}
                >
                  <input
                    id="nationalId"
                    name="nationalId"
                    type="text"
                    value={nationalId}
                    inputMode="numeric"
                    autoComplete="off"
                    maxLength={16}
                    disabled={loading}
                    aria-invalid={Boolean(errors.nationalId)}
                    aria-describedby={
                      errors.nationalId ? "nationalId-error" : undefined
                    }
                    onChange={(event) => {
                      const value = normalizeNationalId(event.target.value);

                      setNationalId(value);

                      if (errors.nationalId) {
                        setErrors((current) => ({
                          ...current,
                          nationalId: "",
                        }));
                      }
                    }}
                    placeholder="16-digit National ID"
                    className={inputClass(errors.nationalId)}
                  />

                  <div className="mt-1.5 flex items-center justify-between">
                    <span className="text-xs text-slate-400">
                      Official identification number
                    </span>

                    <span
                      className={`text-xs font-semibold ${
                        nationalId.length === 16
                          ? "text-emerald-600"
                          : "text-slate-400"
                      }`}
                    >
                      {nationalId.length}/16
                    </span>
                  </div>
                </Field>

                <Field
                  id="creditScore"
                  label="Credit Score"
                  hint="Optional · 0–1000"
                  error={errors.creditScore}
                >
                  <input
                    id="creditScore"
                    name="creditScore"
                    type="number"
                    value={creditScore}
                    inputMode="numeric"
                    min={0}
                    max={1000}
                    step={1}
                    disabled={loading}
                    aria-invalid={Boolean(errors.creditScore)}
                    aria-describedby={
                      errors.creditScore ? "creditScore-error" : undefined
                    }
                    onChange={(event) => {
                      const value = event.target.value;

                      if (
                        value === "" ||
                        (/^\d+$/.test(value) && Number(value) <= 1000)
                      ) {
                        setCreditScore(value);
                      }

                      if (errors.creditScore) {
                        setErrors((current) => ({
                          ...current,
                          creditScore: "",
                        }));
                      }
                    }}
                    placeholder="e.g. 650"
                    className={inputClass(errors.creditScore)}
                  />
                </Field>
              </div>
            </section>

            <FormDivider />

            {/* Contact information */}
            <section>
              <SectionHeader
                number="02"
                title="Contact Information"
                description="Provide reliable contact details for communication and account servicing."
              />

              <div className="mt-5 grid gap-5 sm:grid-cols-2">
                <Field
                  id="phone"
                  label="Phone Number"
                  required
                  hint="Rwanda"
                  error={errors.phone}
                >
                  <input
                    id="phone"
                    name="phone"
                    type="tel"
                    value={phone}
                    inputMode="tel"
                    autoComplete="tel"
                    maxLength={20}
                    disabled={loading}
                    aria-invalid={Boolean(errors.phone)}
                    aria-describedby={errors.phone ? "phone-error" : undefined}
                    onChange={(event) => {
                      const value = event.target.value;

                      setPhone(value);

                      if (errors.phone) {
                        setErrors((current) => ({
                          ...current,
                          phone: "",
                        }));
                      }
                    }}
                    placeholder="+250 788 123 456"
                    className={inputClass(errors.phone)}
                  />
                </Field>

                <Field
                  id="email"
                  label="Email Address"
                  hint="Optional"
                  error={errors.email}
                >
                  <input
                    id="email"
                    name="email"
                    type="email"
                    value={email}
                    autoComplete="email"
                    maxLength={150}
                    disabled={loading}
                    aria-invalid={Boolean(errors.email)}
                    aria-describedby={errors.email ? "email-error" : undefined}
                    onChange={(event) => {
                      setEmail(event.target.value);

                      if (errors.email) {
                        setErrors((current) => ({
                          ...current,
                          email: "",
                        }));
                      }
                    }}
                    placeholder="customer@example.com"
                    className={inputClass(errors.email)}
                  />
                </Field>
              </div>
            </section>

            <FormDivider />

            {/* Address */}
            <section>
              <SectionHeader
                number="03"
                title="Residential Information"
                description="Record the borrower's primary residential address."
              />

              <div className="mt-5">
                <Field
                  id="address"
                  label="Address"
                  hint="Optional"
                  error={errors.address}
                >
                  <input
                    id="address"
                    name="address"
                    type="text"
                    value={address}
                    autoComplete="street-address"
                    maxLength={255}
                    disabled={loading}
                    aria-invalid={Boolean(errors.address)}
                    aria-describedby={
                      errors.address ? "address-error" : undefined
                    }
                    onChange={(event) => {
                      setAddress(event.target.value);

                      if (errors.address) {
                        setErrors((current) => ({
                          ...current,
                          address: "",
                        }));
                      }
                    }}
                    placeholder="e.g. Kigali, Gasabo, Rwanda"
                    className={inputClass(errors.address)}
                  />
                </Field>
              </div>
            </section>

            <FormDivider />

            {/* Compliance note */}
            <div className="rounded-2xl border border-blue-100 bg-blue-50/70 p-4">
              <div className="flex items-start gap-3">
                <div className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-blue-100 text-blue-700">
                  <svg
                    className="h-4 w-4"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    aria-hidden="true"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={1.8}
                      d="M12 11v5m0-9h.01M5.5 20h13a2 2 0 001.73-3L13.73 4a2 2 0 00-3.46 0L3.77 17A2 2 0 005.5 20z"
                    />
                  </svg>
                </div>

                <div>
                  <p className="text-sm font-semibold text-blue-900">
                    Data accuracy is important
                  </p>

                  <p className="mt-1 text-xs leading-5 text-blue-800">
                    Verify the borrower's identification and contact information
                    against the appropriate source documents before submitting
                    the profile. Existing borrower records are checked to reduce
                    duplicate customer profiles.
                  </p>
                </div>
              </div>
            </div>
          </div>

          {/* Actions */}
          <div className="border-t border-slate-100 bg-slate-50/70 px-5 py-5 sm:px-7">
            <div className="flex flex-col-reverse gap-3 sm:flex-row sm:items-center sm:justify-between">
              <p className="text-xs text-slate-400">
                <span className="text-red-500">*</span> Required field
              </p>

              <div className="flex flex-col gap-3 sm:flex-row">
                <Link
                  href="/dashboard/borrowers"
                  aria-disabled={loading}
                  className={`inline-flex items-center justify-center rounded-xl border border-slate-200 bg-white px-5 py-3 text-sm font-semibold text-slate-700 shadow-sm transition hover:bg-slate-50 ${
                    loading ? "pointer-events-none opacity-50" : ""
                  }`}
                >
                  Cancel
                </Link>

                <button
                  type="submit"
                  disabled={loading}
                  className="inline-flex min-w-[170px] items-center justify-center gap-2 rounded-xl bg-blue-600 px-5 py-3 text-sm font-bold text-white shadow-sm transition hover:bg-blue-700 focus:outline-none focus:ring-4 focus:ring-blue-100 disabled:cursor-not-allowed disabled:bg-blue-400"
                >
                  {loading ? (
                    <>
                      <svg
                        className="h-4 w-4 animate-spin"
                        viewBox="0 0 24 24"
                        fill="none"
                        aria-hidden="true"
                      >
                        <circle
                          className="opacity-25"
                          cx="12"
                          cy="12"
                          r="9"
                          stroke="currentColor"
                          strokeWidth="3"
                        />

                        <path
                          className="opacity-90"
                          fill="currentColor"
                          d="M21 12a9 9 0 01-9 9v-3a6 6 0 006-6h3z"
                        />
                      </svg>
                      Creating Borrower…
                    </>
                  ) : (
                    <>
                      <svg
                        className="h-4 w-4"
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        aria-hidden="true"
                      >
                        <path
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          strokeWidth={2}
                          d="M12 4v16m8-8H4"
                        />
                      </svg>
                      Create Borrower
                    </>
                  )}
                </button>
              </div>
            </div>
          </div>
        </form>

        {/* Footer */}
        <div className="pb-4 text-center text-xs text-slate-400">
          Borrower registration • Ensure all customer information is accurate
          before submission.
        </div>
      </div>
    </main>
  );
}
