"use client";

import { Suspense, useEffect, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { borrowerApi, post } from "@/services/api";
import {
  DOCUMENT_TYPE_LABELS,
  getFilesByBorrower,
  uploadFile,
} from "@/services/fileService";
import type { BorrowerFile } from "@/types/index";

type BorrowerOption = {
  id: number;
  firstName?: string;
  lastName?: string;
  name?: string;
  phone?: string;
  nationalId?: string;
};

type BorrowerDetails = {
  borrowerId: number;
  maritalStatus?: string | null;
  employmentType?: string | null;
  status?: string | null;
};

type DocumentMetadata = BorrowerFile & {
  updateRequired?: boolean;
  documentReviewDueAt?: string | null;
};

type DocumentState = {
  borrowerId: string;
  details: BorrowerDetails | null;
  files: DocumentMetadata[];
  loading: boolean;
  error: string;
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

const EMPLOYEE_TYPES = new Set([
  "EMPLOYEE",
  "EMPLOYED",
  "PERMANENT",
  "CONTRACT",
  "SALARIED",
]);

const BUSINESS_EMPLOYMENT_TYPES = new Set([
  "SELF_EMPLOYED",
  "SELFEMPLOYED",
  "BUSINESS",
  "BUSINESS_OWNER",
  "COMPANY",
  "COMPANY_OWNER",
]);

const ALLOWED_MIME_TYPES = new Set([
  "application/pdf",
  "image/jpeg",
  "image/png",
  "image/webp",
]);

const MAX_DOCUMENT_BYTES = 8 * 1024 * 1024;

const fieldStyle =
  "mt-1 w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm focus:border-amber-500 focus:outline-none focus:ring-2 focus:ring-amber-100";

const money = (n: number) =>
  new Intl.NumberFormat("en-RW", {
    maximumFractionDigits: 0,
  }).format(n) + " RWF";

function normalize(value?: string | null): string {
  return (value ?? "")
    .trim()
    .toUpperCase()
    .replaceAll("-", "_")
    .replaceAll(" ", "_");
}

/**
 * Mirrors the current backend BorrowerDocumentRequirements.
 *
 * The backend remains authoritative and independently validates
 * submission through LoanCreationDocumentGuard.
 */
function requiredDocumentsFor(details: BorrowerDetails | null): string[] {
  if (!details) return [];

  const required = ["NATIONAL_ID", "BANK_STATEMENT", "SELFIE"];

  required.push(
    normalize(details.maritalStatus) === "MARRIED"
      ? "MARRIAGE_CERTIFICATE"
      : "SINGLE_CERTIFICATE",
  );

  const employment = normalize(details.employmentType);

  if (EMPLOYEE_TYPES.has(employment)) {
    required.push("PAYSLIP");
  } else if (BUSINESS_EMPLOYMENT_TYPES.has(employment)) {
    required.push("BUSINESS_REGISTRATION");
  }

  return required;
}

function documentLabel(type: string): string {
  return (
    DOCUMENT_TYPE_LABELS[type] ??
    type
      .replaceAll("_", " ")
      .toLowerCase()
      .replace(/\b\w/g, (letter) => letter.toUpperCase())
  );
}

function latestDocument(
  files: DocumentMetadata[],
  type: string,
): DocumentMetadata | null {
  const matches = files
    .filter((file) => normalize(file.documentType) === type)
    .sort((a, b) => {
      const aTime = Date.parse(a.uploadedAt ?? "") || 0;
      const bTime = Date.parse(b.uploadedAt ?? "") || 0;

      return bTime - aTime || b.id - a.id;
    });

  return matches[0] ?? null;
}

function documentAcceptable(file: DocumentMetadata | null): boolean {
  if (!file) return false;

  if (file.contentAvailable !== true) return false;
  if (file.updateRequired === true) return false;

  const status = normalize(file.verificationStatus);

  return (
    status === "VERIFIED" || status === "PENDING" || status === "UNDER_REVIEW"
  );
}

function missingDocuments(
  required: string[],
  files: DocumentMetadata[],
): string[] {
  return required.filter(
    (type) => !documentAcceptable(latestDocument(files, type)),
  );
}

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
  const [collateralType, setCollateralType] = useState<
    "NONE" | "MOVABLE" | "IMMOVABLE"
  >("NONE");
  const [collateralExpertise, setCollateralExpertise] = useState<File | null>(
    null,
  );
  const [extraEvidence, setExtraEvidence] = useState<File[]>([]);
  const [collateralValue, setCollateralValue] = useState("");
  const [collateralDescription, setCollateralDescription] = useState("");
  const [notes, setNotes] = useState("");

  const [review, setReview] = useState(false);
  const [confirmed, setConfirmed] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  const [documents, setDocuments] = useState<DocumentState>({
    borrowerId: "",
    details: null,
    files: [],
    loading: false,
    error: "",
  });

  const [documentVersion, setDocumentVersion] = useState(0);
  const [uploadingType, setUploadingType] = useState<string | null>(null);
  const [uploadError, setUploadError] = useState("");

  const numericBorrowerId = Number(borrowerId);

  const validBorrowerId =
    Number.isSafeInteger(numericBorrowerId) && numericBorrowerId > 0;

  // Search borrowers through the existing API.
  useEffect(() => {
    let active = true;

    const timer = window.setTimeout(async () => {
      setLoadingBorrowers(true);

      try {
        const data = await borrowerApi.list(0, 50, borrowerQuery.trim());

        const options: BorrowerOption[] = Array.isArray(data)
          ? data
          : Array.isArray(data?.content)
            ? data.content
            : Array.isArray(data?.items)
              ? data.items
              : [];

        if (active) {
          setBorrowers(
            options.filter((borrower) =>
              Number.isSafeInteger(Number(borrower?.id)),
            ),
          );
        }
      } catch (err) {
        if (active) {
          setError(
            err instanceof Error ? err.message : "Unable to find borrowers.",
          );
        }
      } finally {
        if (active) setLoadingBorrowers(false);
      }
    }, 300);

    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [borrowerQuery]);

  // Load the exact borrower's profile and stored documents.
  useEffect(() => {
    let active = true;

    setUploadError("");

    if (!validBorrowerId) {
      setDocuments({
        borrowerId: "",
        details: null,
        files: [],
        loading: false,
        error: "",
      });

      return () => {
        active = false;
      };
    }

    setDocuments({
      borrowerId,
      details: null,
      files: [],
      loading: true,
      error: "",
    });

    Promise.all([
      borrowerApi.getDetails(numericBorrowerId),
      getFilesByBorrower(numericBorrowerId),
    ])
      .then(([details, files]) => {
        if (!active) return;

        const profile = details as BorrowerDetails;

        if (!profile || Number(profile.borrowerId) !== numericBorrowerId) {
          throw new Error("Unable to verify the selected borrower's profile.");
        }

        if (!Array.isArray(files)) {
          throw new Error("The document service did not return a valid list.");
        }

        setDocuments({
          borrowerId,
          details: profile,
          files: files as DocumentMetadata[],
          loading: false,
          error: "",
        });
      })
      .catch((err: unknown) => {
        if (!active) return;

        setDocuments({
          borrowerId,
          details: null,
          files: [],
          loading: false,
          error:
            err instanceof Error
              ? err.message
              : "Unable to load borrower documents.",
        });
      });

    return () => {
      active = false;
    };
  }, [borrowerId, numericBorrowerId, validBorrowerId, documentVersion]);

  const selected = borrowers.find(
    (borrower) => String(borrower.id) === borrowerId,
  );

  const principal = Number(amount);
  const duration = Number(durationMonths);

  const valid =
    validBorrowerId &&
    Number.isFinite(principal) &&
    principal >= 1_000_000 &&
    Number.isInteger(duration) &&
    duration >= 1 &&
    duration <= 6 &&
    Boolean(startDate) &&
    purpose.trim().length >= 10 &&
    (collateralType !== "IMMOVABLE" || Boolean(collateralExpertise)) &&
    (!collateralExpertise ||
      (ALLOWED_MIME_TYPES.has(collateralExpertise.type) &&
        collateralExpertise.size > 0 &&
        collateralExpertise.size <= MAX_DOCUMENT_BYTES)) &&
    extraEvidence.length <= 9 &&
    extraEvidence.every(
      (file) =>
        ALLOWED_MIME_TYPES.has(file.type) &&
        file.size > 0 &&
        file.size <= MAX_DOCUMENT_BYTES,
    ) &&
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

  const documentsMatchBorrower =
    validBorrowerId && documents.borrowerId === borrowerId;

  const requiredDocuments = useMemo(
    () =>
      documentsMatchBorrower ? requiredDocumentsFor(documents.details) : [],
    [documentsMatchBorrower, documents.details],
  );

  const missing = useMemo(
    () =>
      missingDocuments(
        requiredDocuments,
        documentsMatchBorrower ? documents.files : [],
      ),
    [requiredDocuments, documentsMatchBorrower, documents.files],
  );

  const documentsReady =
    documentsMatchBorrower &&
    documents.details !== null &&
    !documents.loading &&
    !documents.error &&
    !uploadError &&
    uploadingType === null &&
    requiredDocuments.length > 0 &&
    missing.length === 0;

  const readyToReview =
    valid &&
    documentsReady &&
    !loadingBorrowers &&
    !submitting &&
    normalize(documents.details?.status) !== "BLACKLISTED";

  async function uploadRequiredDocument(type: string, file: File | null) {
    if (!file || !validBorrowerId || uploadingType !== null) {
      return;
    }

    if (file.size === 0 || file.size > MAX_DOCUMENT_BYTES) {
      setUploadError("Select a nonempty document no larger than 8 MB.");
      return;
    }

    if (!ALLOWED_MIME_TYPES.has(file.type.toLowerCase())) {
      setUploadError("Supported formats are PDF, JPG, PNG and WEBP.");
      return;
    }

    setUploadingType(type);
    setUploadError("");
    setError("");

    try {
      // Existing API client: correct tenant headers,
      // session handling and multipart boundary.
      await uploadFile(numericBorrowerId, file, type);

      // Re-fetch authoritative document metadata.
      const updated = await getFilesByBorrower(numericBorrowerId);

      if (!Array.isArray(updated)) {
        throw new Error("The server did not confirm the uploaded document.");
      }

      setDocuments((previous) => {
        if (previous.borrowerId !== borrowerId) {
          return previous;
        }

        return {
          ...previous,
          files: updated as DocumentMetadata[],
          error: "",
        };
      });
    } catch (err) {
      setUploadError(
        err instanceof Error ? err.message : "Document upload failed.",
      );
    } finally {
      setUploadingType(null);
    }
  }

  function reviewApplication() {
    setError("");

    if (!valid) {
      setError(
        "Check the borrower, amount (minimum 1,000,000 RWF), " +
          "1–6 month term and purpose (at least 10 characters).",
      );
      return;
    }

    if (!documentsReady) {
      setError(
        "Upload or update all required borrower documents " +
          "before reviewing this application.",
      );
      return;
    }

    setReview(true);
    setConfirmed(false);
  }

  async function submit() {
    if (!valid || !confirmed || !documentsReady || submitting) {
      return;
    }

    setSubmitting(true);
    setError("");

    try {
      // Recheck both the borrower profile and document metadata
      // immediately before creation.
      const [latestDetails, latestFiles] = await Promise.all([
        borrowerApi.getDetails(numericBorrowerId),
        getFilesByBorrower(numericBorrowerId),
      ]);

      const profile = latestDetails as BorrowerDetails;

      if (
        Number(profile?.borrowerId) !== numericBorrowerId ||
        !Array.isArray(latestFiles)
      ) {
        throw new Error(
          "Borrower document verification failed. Please review again.",
        );
      }

      const latestRequired = requiredDocumentsFor(profile);

      const latestMissing = missingDocuments(
        latestRequired,
        latestFiles as DocumentMetadata[],
      );

      if (latestMissing.length > 0) {
        setReview(false);
        setConfirmed(false);
        setDocumentVersion((value) => value + 1);

        throw new Error(
          "Required documents are missing or outdated: " +
            latestMissing.map(documentLabel).join(", "),
        );
      }

      // Preserve maker-checker flow and existing validation; submit collateral
      // evidence in the SAME database transaction as the loan application.
      const request = {
        borrowerId: numericBorrowerId,
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
        collateralType,
      };
      const multipart = new FormData();
      multipart.append(
        "loan",
        new Blob([JSON.stringify(request)], { type: "application/json" }),
      );
      if (collateralExpertise)
        multipart.append("collateralExpertiseDocument", collateralExpertise);
      extraEvidence.forEach((file) =>
        multipart.append("otherSupportingDocuments", file),
      );
      const created = await post("/loans/with-collateral-documents", multipart);

      const id = Number(created?.id);

      router.push(
        Number.isSafeInteger(id) && id > 0
          ? `/dashboard/loans/${id}`
          : "/dashboard/loans",
      );

      router.refresh();
    } catch (err) {
      setConfirmed(false);

      setError(
        err instanceof Error
          ? err.message
          : "Unable to create the loan application.",
      );
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
            Select a borrower, enter the proposed loan terms, supply required
            documents and submit for approval.
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
          onSubmit={(event) => {
            event.preventDefault();
            reviewApplication();
          }}
        >
          <section className="rounded-xl border bg-white p-5 shadow-sm">
            <h2 className="font-semibold text-slate-900">
              1. Borrower identity
            </h2>

            <p className="mt-1 text-xs text-slate-500">
              Search the existing borrower register. Do not create a second
              borrower profile for another loan.
            </p>

            <label className="mt-4 block text-sm font-medium">
              Find borrower by name / ID / phone
              <input
                className={fieldStyle}
                value={borrowerQuery}
                onChange={(event) => setBorrowerQuery(event.target.value)}
                placeholder="Search borrower register"
              />
            </label>

            <label className="mt-3 block text-sm font-medium">
              Borrower *
              <select
                required
                className={fieldStyle}
                value={borrowerId}
                disabled={loadingBorrowers || uploadingType !== null}
                onChange={(event) => {
                  setBorrowerId(event.target.value);
                  setError("");
                  setConfirmed(false);
                  setUploadError("");
                }}
              >
                <option value="">
                  {loadingBorrowers
                    ? "Searching..."
                    : "Choose existing borrower"}
                </option>

                {borrowerId && !selected && (
                  <option value={borrowerId}>
                    Selected borrower #{borrowerId}
                  </option>
                )}

                {borrowers.map((borrower) => (
                  <option key={borrower.id} value={borrower.id}>
                    {`${borrower.firstName ?? ""} ${
                      borrower.lastName ?? ""
                    }`.trim() ||
                      borrower.name ||
                      `Borrower ${borrower.id}`}{" "}
                    — #{borrower.id}
                    {borrower.phone ? ` · ${borrower.phone}` : ""}
                  </option>
                ))}
              </select>
            </label>
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
                  onChange={(event) => setLoanType(event.target.value)}
                >
                  {LOAN_TYPES.map(([value, label]) => (
                    <option key={value} value={value}>
                      {label}
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
                  onChange={(event) => setAmount(event.target.value)}
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
                  onChange={(event) => setDurationMonths(event.target.value)}
                  required
                />
              </label>

              <label className="block text-sm font-medium">
                Proposed start date *
                <input
                  className={fieldStyle}
                  type="date"
                  value={startDate}
                  onChange={(event) => setStartDate(event.target.value)}
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
                onChange={(event) => setPurpose(event.target.value)}
                placeholder="How will the funds be used?"
              />
            </label>

            <p className="mt-3 text-xs text-slate-600">
              Repayment frequency: <strong>monthly</strong>. Actual interest,
              application fees and repayment schedules are determined by backend
              product rules. Management fee is 5% monthly on outstanding
              principal. No funds are collected by this form.
            </p>
          </section>

          <section className="rounded-xl border bg-white p-5 shadow-sm">
            <h2 className="font-semibold text-slate-900">
              3. Security and supporting information
            </h2>

            <label className="mt-4 block text-sm font-medium">
              Collateral type
              <select
                className={fieldStyle}
                value={collateralType}
                onChange={(event) => {
                  setCollateralType(
                    event.target.value as "NONE" | "MOVABLE" | "IMMOVABLE",
                  );
                  setCollateralExpertise(null);
                }}
              >
                <option value="NONE">No collateral</option>
                <option value="MOVABLE">
                  Movable — vehicles, machinery, equipment
                </option>
                <option value="IMMOVABLE">
                  Immovable — land, buildings, property
                </option>
              </select>
            </label>
            {collateralType !== "NONE" && (
              <label className="mt-4 block text-sm font-medium">
                Expertise / valuation report{" "}
                {collateralType === "IMMOVABLE" ? "(required)" : "(optional)"}
                <input
                  className={fieldStyle}
                  type="file"
                  accept=".pdf,.jpg,.jpeg,.png,.webp"
                  onChange={(event) =>
                    setCollateralExpertise(event.target.files?.[0] ?? null)
                  }
                />
              </label>
            )}
            <label className="mt-4 block text-sm font-medium">
              Other supporting documents (optional, up to 9)
              <input
                className={fieldStyle}
                type="file"
                multiple
                accept=".pdf,.jpg,.jpeg,.png,.webp"
                onChange={(event) =>
                  setExtraEvidence(Array.from(event.target.files ?? []))
                }
              />
            </label>

            <div className="mt-3 grid gap-4 sm:grid-cols-2">
              <label className="block text-sm font-medium">
                Collateral value (RWF, optional)
                <input
                  className={fieldStyle}
                  type="number"
                  min="0"
                  step="1"
                  value={collateralValue}
                  onChange={(event) => setCollateralValue(event.target.value)}
                />
              </label>

              <label className="block text-sm font-medium">
                Collateral description (optional)
                <input
                  className={fieldStyle}
                  maxLength={1000}
                  value={collateralDescription}
                  onChange={(event) =>
                    setCollateralDescription(event.target.value)
                  }
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
                onChange={(event) => setNotes(event.target.value)}
                placeholder="Repayment source, risks and next actions"
              />
            </label>

            <p className="mt-3 text-xs text-amber-800">
              Creating the application starts the existing maker-checker
              process. It does not approve or disburse funds.
            </p>
          </section>

          <section className="rounded-xl border bg-white p-5 shadow-sm">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <h2 className="font-semibold text-slate-900">
                  4. Required documents
                </h2>

                <p className="mt-1 text-xs text-slate-500">
                  The same identity, financial and marital-status requirements
                  used by public applications.
                </p>
              </div>

              <button
                type="button"
                className="rounded-lg border px-3 py-2 text-xs font-semibold disabled:opacity-50"
                disabled={!validBorrowerId || uploadingType !== null}
                onClick={() => setDocumentVersion((value) => value + 1)}
              >
                Refresh documents
              </button>
            </div>

            {!validBorrowerId && (
              <p className="mt-4 rounded-lg bg-amber-50 p-3 text-sm text-amber-800">
                Choose a borrower to view required documents.
              </p>
            )}

            {documentsMatchBorrower && documents.loading && (
              <p className="mt-4 text-sm text-slate-500">
                Checking borrower profile and documents...
              </p>
            )}

            {documentsMatchBorrower && documents.error && (
              <p
                role="alert"
                className="mt-4 rounded-lg bg-red-50 p-3 text-sm text-red-700"
              >
                {documents.error}
              </p>
            )}

            {uploadError && (
              <p
                role="alert"
                className="mt-4 rounded-lg bg-red-50 p-3 text-sm text-red-700"
              >
                {uploadError}
              </p>
            )}

            {documents.details && documentsMatchBorrower && (
              <div className="mt-4 space-y-3">
                {requiredDocuments.map((type) => {
                  const existing = latestDocument(documents.files, type);

                  const supplied = documentAcceptable(existing);

                  return (
                    <div
                      key={type}
                      className="rounded-xl border border-slate-200 p-4"
                    >
                      <div className="flex flex-wrap items-start justify-between gap-3">
                        <div>
                          <p className="font-semibold text-slate-900">
                            {documentLabel(type)}
                          </p>

                          {existing && (
                            <p className="mt-1 break-all text-xs text-slate-600">
                              {existing.fileName}
                            </p>
                          )}

                          <p
                            className={`mt-1 text-xs ${
                              supplied ? "text-emerald-700" : "text-amber-800"
                            }`}
                          >
                            {supplied
                              ? `Supplied — ${
                                  existing?.verificationStatus ??
                                  "pending verification"
                                }`
                              : existing
                                ? "Replacement or updated document required"
                                : "Document required"}
                          </p>
                        </div>

                        <span
                          className={`rounded-full px-3 py-1 text-xs font-semibold ${
                            supplied
                              ? "bg-emerald-50 text-emerald-700"
                              : "bg-amber-50 text-amber-800"
                          }`}
                        >
                          {supplied ? "Uploaded" : "Missing"}
                        </span>
                      </div>

                      {!supplied && (
                        <label className="mt-4 block text-sm font-medium">
                          Upload {documentLabel(type)}
                          <input
                            type="file"
                            accept=".pdf,.jpg,.jpeg,.png,.webp"
                            className="mt-2 block w-full rounded-lg border border-slate-300 p-2 text-sm"
                            disabled={
                              documents.loading || uploadingType !== null
                            }
                            onChange={(event) => {
                              const file =
                                event.currentTarget.files?.[0] ?? null;

                              void uploadRequiredDocument(type, file);

                              event.currentTarget.value = "";
                            }}
                          />
                        </label>
                      )}
                    </div>
                  );
                })}
              </div>
            )}

            {documents.details && (
              <div
                aria-live="polite"
                className={`mt-4 rounded-lg border p-4 text-sm ${
                  documentsReady
                    ? "border-emerald-200 bg-emerald-50 text-emerald-800"
                    : "border-amber-200 bg-amber-50 text-amber-900"
                }`}
              >
                {documentsReady ? (
                  <strong>
                    All required documents are supplied. You may review the loan
                    application.
                  </strong>
                ) : (
                  <>
                    <strong>Required documents are not complete.</strong>

                    {missing.length > 0 && (
                      <p className="mt-2">
                        Missing or outdated:{" "}
                        {missing.map(documentLabel).join(", ")}
                      </p>
                    )}
                  </>
                )}
              </div>
            )}

            <p className="mt-3 text-xs text-slate-500">
              Accepted formats: PDF, JPG, PNG and WEBP. Maximum size: 8 MB per
              file. Uploading a document does not automatically verify it; staff
              verification remains required before disbursement.
            </p>
          </section>

          <div className="flex justify-end">
            <button
              className="rounded-lg bg-slate-900 px-6 py-3 text-sm font-semibold text-white disabled:opacity-50"
              disabled={!readyToReview}
              type="submit"
            >
              {uploadingType
                ? "Uploading documents..."
                : "Review application →"}
            </button>
          </div>
        </form>
      ) : (
        <section className="space-y-4 rounded-xl border bg-white p-5 shadow-sm">
          <h2 className="text-lg font-bold">5. Confirm before creating</h2>

          <dl className="grid gap-3 text-sm sm:grid-cols-2">
            <div>
              <dt className="text-slate-500">Borrower</dt>
              <dd className="font-semibold">
                {selected
                  ? `${selected.firstName ?? ""} ${
                      selected.lastName ?? ""
                    }`.trim() || selected.name
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
              <dt className="text-slate-500">Collateral type</dt>
              <dd className="font-semibold">
                {collateralType.replaceAll("_", " ")}
              </dd>
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
              <dd className="whitespace-pre-wrap font-semibold">{purpose}</dd>
            </div>
          </dl>

          <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-900">
            <strong>Required documents supplied</strong>

            <p className="mt-1">
              {requiredDocuments.map(documentLabel).join(", ")}
            </p>
          </div>

          <div className="rounded-lg bg-amber-50 p-4 text-sm text-amber-950">
            <strong>Indicative charge illustration only.</strong> At 5%
            interest, estimated one-month interest is {money(estimate.interest)}
            ; one-month management fee is {money(estimate.management)}; at 2% an
            application fee would be {money(estimate.application)}. Actual
            charges, disbursement deductions and schedules are determined by
            backend rules and approval. These are not contractual totals.
          </div>

          <label className="flex gap-2 text-sm">
            <input
              type="checkbox"
              checked={confirmed}
              disabled={submitting}
              onChange={(event) => setConfirmed(event.target.checked)}
            />

            <span>
              I verified the borrower identity, principal, purpose and required
              documents, and understand this is an application—not a
              disbursement.
            </span>
          </label>

          <div className="flex justify-end gap-3">
            <button
              type="button"
              className="rounded-lg border px-4 py-2 text-sm"
              disabled={submitting}
              onClick={() => {
                setReview(false);
                setConfirmed(false);
                setDocumentVersion((value) => value + 1);
              }}
            >
              Edit details
            </button>

            <button
              type="button"
              className="rounded-lg bg-slate-900 px-6 py-2 text-sm font-semibold text-white disabled:opacity-50"
              disabled={!confirmed || !documentsReady || submitting}
              onClick={() => void submit()}
            >
              {submitting ? "Submitting..." : "Create and send for approval"}
            </button>
          </div>
        </section>
      )}
    </div>
  );
}

export default function NewLoanPage() {
  return (
    <Suspense fallback={<div className="p-6">Loading loan application...</div>}>
      <NewLoanForm />
    </Suspense>
  );
}
