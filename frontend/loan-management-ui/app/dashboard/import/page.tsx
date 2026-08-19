"use client";

import { useEffect, useRef, useState } from "react";

import { importApi } from "../../../services/api";
import { toast } from "../../../hooks/useToast";
import { PageSpinner } from "../../../components/ui/Skeleton";
import { Pill } from "../../../components/ui/Badge";

/* ============================================================
   TYPES
   ============================================================ */

interface RowResult {
  rowNumber: number;
  success: boolean;
  borrowerAction?: string;
  borrowerName?: string;
  loanReferenceNumber?: string;
  error?: string;
}

interface Batch {
  id: number;
  fileName: string;
  totalRows: number;
  successCount: number;
  failureCount: number;
  status: string;
  createdAt: string;
  importedBy?: {
    name?: string;
  };
}

/* ============================================================
   CONSTANTS
   ============================================================ */

const MAX_FILE_SIZE_MB = 25;

const ACCEPTED_EXTENSIONS = [".csv", ".xlsx", ".xls"];

/* ============================================================
   HELPERS
   ============================================================ */

function getFileExtension(fileName: string) {
  const dot = fileName.lastIndexOf(".");

  return dot >= 0 ? fileName.substring(dot).toLowerCase() : "";
}

function formatFileSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;

  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }

  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function formatDate(value?: string) {
  if (!value) return "—";

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) return "—";

  return date.toLocaleString(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  });
}

/* ============================================================
   PAGE
   ============================================================ */

export default function ImportLegacyLoansPage() {
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const [file, setFile] = useState<File | null>(null);

  const [preview, setPreview] = useState<RowResult[] | null>(null);

  const [previewMsg, setPreviewMsg] = useState("");

  const [busy, setBusy] = useState(false);

  const [dragActive, setDragActive] = useState(false);

  const [batches, setBatches] = useState<Batch[]>([]);

  const [loadingBatches, setLoadingBatches] = useState(true);

  const [showFailedOnly, setShowFailedOnly] = useState(false);

  /* ==========================================================
     LOAD HISTORY
     ========================================================== */

  const loadBatches = async () => {
    setLoadingBatches(true);

    try {
      const result: any = await importApi.batches();

      setBatches(Array.isArray(result) ? result : []);
    } catch (err: any) {
      toast("error", err?.message || "Unable to load import history.");
    } finally {
      setLoadingBatches(false);
    }
  };

  useEffect(() => {
    loadBatches();
  }, []);

  /* ==========================================================
     FILE VALIDATION
     ========================================================== */

  const validateFile = (selectedFile: File) => {
    const extension = getFileExtension(selectedFile.name);

    if (!ACCEPTED_EXTENSIONS.includes(extension)) {
      toast("error", "Unsupported file type. Please upload CSV, XLSX, or XLS.");

      return false;
    }

    const maxBytes = MAX_FILE_SIZE_MB * 1024 * 1024;

    if (selectedFile.size > maxBytes) {
      toast(
        "error",
        `File is too large. Maximum allowed size is ${MAX_FILE_SIZE_MB} MB.`,
      );

      return false;
    }

    if (selectedFile.size === 0) {
      toast("error", "The selected file is empty.");

      return false;
    }

    return true;
  };

  /* ==========================================================
     SELECT FILE
     ========================================================== */

  const handleFileChange = (selectedFile: File | null) => {
    if (!selectedFile) return;

    if (!validateFile(selectedFile)) {
      return;
    }

    setFile(selectedFile);

    setPreview(null);

    setPreviewMsg("");

    setShowFailedOnly(false);
  };

  /* ==========================================================
     DRAG & DROP
     ========================================================== */

  const handleDrop = (event: React.DragEvent<HTMLDivElement>) => {
    event.preventDefault();

    setDragActive(false);

    const droppedFile = event.dataTransfer.files?.[0];

    if (droppedFile) {
      handleFileChange(droppedFile);
    }
  };

  /* ==========================================================
     DOWNLOAD TEMPLATE
     ========================================================== */

  const handleDownloadTemplate = async () => {
    try {
      setBusy(true);

      const res: any = await importApi.template();

      const url = window.URL.createObjectURL(new Blob([res.data]));

      const anchor = document.createElement("a");

      anchor.href = url;

      anchor.download = "legacy-loan-import-template.csv";

      document.body.appendChild(anchor);

      anchor.click();

      anchor.remove();

      window.URL.revokeObjectURL(url);

      toast("success", "Import template downloaded.");
    } catch (err: any) {
      toast("error", err?.message || "Unable to download the import template.");
    } finally {
      setBusy(false);
    }
  };

  /* ==========================================================
     PREVIEW
     ========================================================== */

  const handlePreview = async () => {
    if (!file) {
      toast("error", "Please select an import file first.");

      return;
    }

    setBusy(true);

    setPreview(null);

    setPreviewMsg("");

    try {
      const result: any = await importApi.preview(file);

      const rows: RowResult[] = Array.isArray(result) ? result : [];

      setPreview(rows);

      const successful = rows.filter((row) => row.success).length;

      const failed = rows.filter((row) => !row.success).length;

      setPreviewMsg(
        `${successful} of ${rows.length} rows passed validation${
          failed > 0 ? `, ${failed} require attention` : ""
        }. No data has been saved.`,
      );
    } catch (err: any) {
      toast("error", err?.message || "Unable to preview the import file.");
    } finally {
      setBusy(false);
    }
  };

  /* ==========================================================
     COMMIT
     ========================================================== */

  const handleCommit = async () => {
    if (!file || !preview) return;

    const successfulRows = preview.filter((row) => row.success);

    if (successfulRows.length === 0) {
      toast("error", "There are no valid rows available for import.");

      return;
    }

    const confirmed = window.confirm(
      `You are about to create ${successfulRows.length} loan record${
        successfulRows.length === 1 ? "" : "s"
      } from this file.\n\n` +
        "Existing borrowers may be matched by National ID and new borrowers may be created.\n\n" +
        "This action writes data to the production system and should only be performed after reviewing the preview.\n\n" +
        "Continue?",
    );

    if (!confirmed) return;

    setBusy(true);

    try {
      const batch: any = await importApi.commit(file);

      toast(
        "success",
        `Import completed: ${batch.successCount ?? successfulRows.length}/${batch.totalRows ?? preview.length} rows processed.`,
      );

      setFile(null);

      setPreview(null);

      setPreviewMsg("");

      setShowFailedOnly(false);

      if (fileInputRef.current) {
        fileInputRef.current.value = "";
      }

      await loadBatches();
    } catch (err: any) {
      toast("error", err?.message || "The import could not be completed.");
    } finally {
      setBusy(false);
    }
  };

  /* ==========================================================
     PREVIEW METRICS
     ========================================================== */

  const previewRows = preview || [];

  const successfulRows = previewRows.filter((row) => row.success);

  const failedRows = previewRows.filter((row) => !row.success);

  const newBorrowers = successfulRows.filter(
    (row) => row.borrowerAction === "CREATED_NEW_BORROWER",
  );

  const matchedBorrowers = successfulRows.filter(
    (row) => row.borrowerAction !== "CREATED_NEW_BORROWER",
  );

  const visiblePreviewRows = showFailedOnly ? failedRows : previewRows;

  /* ==========================================================
     RENDER
     ========================================================== */

  return (
    <div className="min-h-screen bg-[#F5F7FA]">
      <div className="mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8">
        {/* ====================================================
            PAGE HEADER
            ==================================================== */}

        <div className="mb-7">
          <div className="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
            <div>
              <div className="mb-3 flex items-center gap-2">
                <span className="inline-flex items-center rounded-full bg-[#E8EEF6] px-3 py-1 text-[11px] font-bold uppercase tracking-wider text-[#0B1F3A]">
                  Data Migration
                </span>

                <span className="text-xs text-gray-400">
                  Legacy Loan Import
                </span>
              </div>

              <h1 className="text-2xl font-extrabold tracking-tight text-[#0B1F3A] sm:text-3xl">
                Import Legacy Loans
              </h1>

              <p className="mt-2 max-w-3xl text-sm leading-6 text-gray-500">
                Safely migrate historical borrower and loan records into Noble
                Loan Solutions. Files are validated before any production data
                is created.
              </p>
            </div>

            <button
              type="button"
              onClick={handleDownloadTemplate}
              disabled={busy}
              className="inline-flex h-11 items-center justify-center rounded-xl border border-[#D7E0EA] bg-white px-4 text-sm font-semibold text-[#0B1F3A] shadow-sm transition hover:border-[#B9C7D8] hover:bg-[#F8FAFC] disabled:cursor-not-allowed disabled:opacity-60"
            >
              <svg
                className="mr-2 h-4 w-4"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
              >
                <path d="M12 3v12" />
                <path d="m7 10 5 5 5-5" />
                <path d="M5 21h14" />
              </svg>
              Download Template
            </button>
          </div>
        </div>

        {/* ====================================================
            SECURITY / PROCESS NOTICE
            ==================================================== */}

        <div className="mb-6 rounded-2xl border border-[#D9E3EF] bg-white shadow-sm">
          <div className="flex flex-col gap-4 p-5 sm:flex-row sm:items-start">
            <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-[#EAF1F8] text-[#0B1F3A]">
              <svg
                className="h-5 w-5"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.8"
              >
                <path d="M12 3 5 6v5c0 4.7 2.9 8.9 7 10 4.1-1.1 7-5.3 7-10V6l-7-3Z" />
                <path d="m9 12 2 2 4-4" />
              </svg>
            </div>

            <div className="min-w-0">
              <p className="text-sm font-bold text-[#0B1F3A]">
                Controlled production import
              </p>

              <p className="mt-1 text-sm leading-6 text-gray-500">
                Preview validates the file without saving anything. Only the
                rows that pass validation can be committed. Borrowers are
                matched using the existing import rules, including National ID
                matching.
              </p>
            </div>
          </div>
        </div>

        {/* ====================================================
            THREE STEP PROCESS
            ==================================================== */}

        <div className="mb-6 grid gap-4 md:grid-cols-3">
          {[
            {
              number: "01",
              title: "Prepare",
              description:
                "Download the approved template and populate your legacy records.",
              active: !file,
            },
            {
              number: "02",
              title: "Validate",
              description:
                "Upload the file and run a preview before anything is saved.",
              active: !!file && !preview,
            },
            {
              number: "03",
              title: "Commit",
              description:
                "Review every result, then safely create the valid records.",
              active: !!preview,
            },
          ].map((step) => (
            <div
              key={step.number}
              className={`rounded-2xl border bg-white p-5 shadow-sm transition ${
                step.active
                  ? "border-[#C7D5E5] ring-1 ring-[#E8EEF6]"
                  : "border-gray-200"
              }`}
            >
              <div className="flex items-start gap-4">
                <div
                  className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl text-xs font-extrabold ${
                    step.active
                      ? "bg-[#0B1F3A] text-white"
                      : "bg-gray-100 text-gray-400"
                  }`}
                >
                  {step.number}
                </div>

                <div>
                  <p className="text-sm font-bold text-gray-900">
                    {step.title}
                  </p>

                  <p className="mt-1 text-xs leading-5 text-gray-500">
                    {step.description}
                  </p>
                </div>
              </div>
            </div>
          ))}
        </div>

        {/* ====================================================
            IMPORT WORKSPACE
            ==================================================== */}

        <div className="overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm">
          {/* Workspace header */}

          <div className="border-b border-gray-100 bg-gradient-to-r from-[#07152A] via-[#0B1F3A] to-[#16365F] px-5 py-5 text-white sm:px-7">
            <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <h2 className="text-base font-bold">Import workspace</h2>

                <p className="mt-1 text-xs text-blue-100">
                  CSV, XLSX and XLS files · Maximum {MAX_FILE_SIZE_MB} MB
                </p>
              </div>

              {file && (
                <span className="inline-flex w-fit items-center rounded-full bg-white/10 px-3 py-1.5 text-xs font-medium text-blue-50">
                  File selected
                </span>
              )}
            </div>
          </div>

          <div className="p-5 sm:p-7">
            {/* ==================================================
                UPLOAD
                ================================================== */}

            <div
              onDragOver={(event) => {
                event.preventDefault();
                setDragActive(true);
              }}
              onDragLeave={() => setDragActive(false)}
              onDrop={handleDrop}
              onClick={() => fileInputRef.current?.click()}
              className={`group cursor-pointer rounded-2xl border-2 border-dashed p-8 text-center transition sm:p-10 ${
                dragActive
                  ? "border-[#0B1F3A] bg-[#F1F5F9]"
                  : "border-[#CBD5E1] bg-[#FAFBFC] hover:border-[#94A3B8] hover:bg-[#F8FAFC]"
              }`}
            >
              <input
                ref={fileInputRef}
                type="file"
                accept=".csv,.xlsx,.xls"
                className="hidden"
                onChange={(event) =>
                  handleFileChange(event.target.files?.[0] ?? null)
                }
              />

              <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-[#EAF1F8] text-[#0B1F3A] transition group-hover:scale-105">
                <svg
                  className="h-7 w-7"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="1.7"
                >
                  <path d="M12 16V4" />
                  <path d="m7 9 5-5 5 5" />
                  <path d="M5 20h14" />
                </svg>
              </div>

              <h3 className="mt-4 text-sm font-bold text-gray-900">
                {dragActive
                  ? "Drop your file here"
                  : "Upload your legacy loan file"}
              </h3>

              <p className="mt-1 text-sm text-gray-500">
                Drag and drop or{" "}
                <span className="font-semibold text-[#0B1F3A]">
                  browse your computer
                </span>
              </p>

              <p className="mt-3 text-xs text-gray-400">
                Accepted: CSV, XLSX, XLS · Maximum {MAX_FILE_SIZE_MB} MB
              </p>
            </div>

            {/* ==================================================
                SELECTED FILE
                ================================================== */}

            {file && (
              <div className="mt-5 rounded-2xl border border-[#D9E3EF] bg-[#F8FAFC] p-4">
                <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                  <div className="flex min-w-0 items-center gap-3">
                    <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-white border border-[#D9E3EF] text-[#0B1F3A]">
                      <svg
                        className="h-5 w-5"
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="1.7"
                      >
                        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z" />
                        <path d="M14 2v6h6" />
                        <path d="M8 13h8" />
                        <path d="M8 17h6" />
                      </svg>
                    </div>

                    <div className="min-w-0">
                      <p className="truncate text-sm font-bold text-gray-900">
                        {file.name}
                      </p>

                      <p className="mt-0.5 text-xs text-gray-500">
                        {formatFileSize(file.size)} ·{" "}
                        {getFileExtension(file.name)
                          .toUpperCase()
                          .replace(".", "")}
                      </p>
                    </div>
                  </div>

                  <button
                    type="button"
                    onClick={(event) => {
                      event.stopPropagation();

                      setFile(null);
                      setPreview(null);
                      setPreviewMsg("");
                      setShowFailedOnly(false);

                      if (fileInputRef.current) {
                        fileInputRef.current.value = "";
                      }
                    }}
                    className="text-xs font-semibold text-gray-500 transition hover:text-red-600"
                  >
                    Remove file
                  </button>
                </div>
              </div>
            )}

            {/* ==================================================
                ACTIONS
                ================================================== */}

            {file && (
              <div className="mt-5 flex flex-col gap-3 sm:flex-row">
                <button
                  type="button"
                  onClick={handlePreview}
                  disabled={busy}
                  className="inline-flex h-11 items-center justify-center rounded-xl border border-[#C7D5E5] bg-white px-5 text-sm font-bold text-[#0B1F3A] shadow-sm transition hover:bg-[#F8FAFC] disabled:cursor-not-allowed disabled:opacity-60"
                >
                  {busy && !preview ? (
                    <>
                      <span className="mr-2 h-4 w-4 animate-spin rounded-full border-2 border-[#CBD5E1] border-t-[#0B1F3A]" />
                      Validating…
                    </>
                  ) : (
                    <>
                      <svg
                        className="mr-2 h-4 w-4"
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="2"
                      >
                        <circle cx="11" cy="11" r="7" />
                        <path d="m20 20-4-4" />
                      </svg>
                      Preview Import
                    </>
                  )}
                </button>

                {preview && successfulRows.length > 0 && (
                  <button
                    type="button"
                    onClick={handleCommit}
                    disabled={busy}
                    className="inline-flex h-11 items-center justify-center rounded-xl bg-[#0B1F3A] px-5 text-sm font-bold text-white shadow-sm transition hover:bg-[#16365F] disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    {busy ? (
                      <>
                        <span className="mr-2 h-4 w-4 animate-spin rounded-full border-2 border-white/30 border-t-white" />
                        Importing…
                      </>
                    ) : (
                      <>
                        Commit {successfulRows.length} Valid{" "}
                        {successfulRows.length === 1 ? "Row" : "Rows"}
                      </>
                    )}
                  </button>
                )}
              </div>
            )}

            {/* ==================================================
                PREVIEW MESSAGE
                ================================================== */}

            {previewMsg && (
              <div className="mt-5 flex items-start gap-3 rounded-xl border border-[#D9E3EF] bg-[#F8FAFC] px-4 py-3">
                <svg
                  className="mt-0.5 h-4 w-4 shrink-0 text-[#0B1F3A]"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                >
                  <circle cx="12" cy="12" r="9" />
                  <path d="M12 11v5" />
                  <path d="M12 8h.01" />
                </svg>

                <p className="text-sm text-gray-600">{previewMsg}</p>
              </div>
            )}

            {/* ==================================================
                PREVIEW SUMMARY
                ================================================== */}

            {preview && (
              <div className="mt-6">
                <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
                  <div>
                    <h3 className="text-sm font-bold text-[#0B1F3A]">
                      Validation results
                    </h3>

                    <p className="mt-1 text-xs text-gray-400">
                      Review the migration results before committing.
                    </p>
                  </div>

                  {failedRows.length > 0 && (
                    <button
                      type="button"
                      onClick={() => setShowFailedOnly((current) => !current)}
                      className="text-xs font-bold text-[#0B1F3A] hover:underline"
                    >
                      {showFailedOnly
                        ? "Show all rows"
                        : "Show failed rows only"}
                    </button>
                  )}
                </div>

                {/* Summary cards */}

                <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
                  <div className="rounded-xl border border-gray-200 bg-white p-4">
                    <p className="text-[11px] font-semibold uppercase tracking-wide text-gray-400">
                      Total rows
                    </p>

                    <p className="mt-1 text-2xl font-extrabold text-[#0B1F3A]">
                      {previewRows.length}
                    </p>
                  </div>

                  <div className="rounded-xl border border-emerald-100 bg-emerald-50/60 p-4">
                    <p className="text-[11px] font-semibold uppercase tracking-wide text-emerald-700">
                      Valid
                    </p>

                    <p className="mt-1 text-2xl font-extrabold text-emerald-800">
                      {successfulRows.length}
                    </p>
                  </div>

                  <div className="rounded-xl border border-red-100 bg-red-50/60 p-4">
                    <p className="text-[11px] font-semibold uppercase tracking-wide text-red-700">
                      Failed
                    </p>

                    <p className="mt-1 text-2xl font-extrabold text-red-800">
                      {failedRows.length}
                    </p>
                  </div>

                  <div className="rounded-xl border border-[#F0E1A1] bg-[#FFFBEA] p-4">
                    <p className="text-[11px] font-semibold uppercase tracking-wide text-[#806200]">
                      New borrowers
                    </p>

                    <p className="mt-1 text-2xl font-extrabold text-[#665000]">
                      {newBorrowers.length}
                    </p>

                    <p className="mt-1 text-[11px] text-[#806200]">
                      {matchedBorrowers.length} matched
                    </p>
                  </div>
                </div>

                {/* ==================================================
                    RESULT TABLE
                    ================================================== */}

                <div className="mt-5 overflow-hidden rounded-2xl border border-gray-200">
                  <div className="overflow-x-auto">
                    <table className="w-full min-w-[850px] text-sm">
                      <thead className="bg-[#F8FAFC]">
                        <tr className="border-b border-gray-200 text-left">
                          <th className="px-4 py-3 text-[11px] font-bold uppercase tracking-wide text-gray-500">
                            Row
                          </th>

                          <th className="px-4 py-3 text-[11px] font-bold uppercase tracking-wide text-gray-500">
                            Result
                          </th>

                          <th className="px-4 py-3 text-[11px] font-bold uppercase tracking-wide text-gray-500">
                            Borrower
                          </th>

                          <th className="px-4 py-3 text-[11px] font-bold uppercase tracking-wide text-gray-500">
                            Loan reference
                          </th>

                          <th className="px-4 py-3 text-[11px] font-bold uppercase tracking-wide text-gray-500">
                            Processing detail
                          </th>
                        </tr>
                      </thead>

                      <tbody className="divide-y divide-gray-100 bg-white">
                        {visiblePreviewRows.length === 0 ? (
                          <tr>
                            <td
                              colSpan={5}
                              className="px-4 py-12 text-center text-sm text-gray-400"
                            >
                              No failed rows.
                            </td>
                          </tr>
                        ) : (
                          visiblePreviewRows.map((row) => (
                            <tr
                              key={row.rowNumber}
                              className={
                                row.success
                                  ? "transition hover:bg-[#FAFBFC]"
                                  : "bg-red-50/60"
                              }
                            >
                              <td className="px-4 py-3 font-mono text-xs text-gray-500">
                                {row.rowNumber}
                              </td>

                              <td className="px-4 py-3">
                                {row.success ? (
                                  <Pill label="Valid" color="green" />
                                ) : (
                                  <Pill label="Failed" color="red" />
                                )}
                              </td>

                              <td className="px-4 py-3">
                                <div className="font-semibold text-gray-900">
                                  {row.borrowerName || "—"}
                                </div>

                                {row.success && (
                                  <div className="mt-1 text-[11px] text-gray-400">
                                    {row.borrowerAction ===
                                    "CREATED_NEW_BORROWER"
                                      ? "New borrower"
                                      : "Existing borrower matched"}
                                  </div>
                                )}
                              </td>

                              <td className="px-4 py-3">
                                <code className="rounded-md bg-[#F1F5F9] px-2 py-1 text-xs text-[#0B1F3A]">
                                  {row.loanReferenceNumber || "—"}
                                </code>
                              </td>

                              <td className="max-w-md px-4 py-3 text-xs leading-5 text-gray-600">
                                {row.success
                                  ? row.borrowerAction ===
                                    "CREATED_NEW_BORROWER"
                                    ? "Borrower will be created and the loan record will be imported."
                                    : "Existing borrower matched and loan record is ready for import."
                                  : row.error || "Validation failed."}
                              </td>
                            </tr>
                          ))
                        )}
                      </tbody>
                    </table>
                  </div>
                </div>

                {/* Commit warning */}

                {successfulRows.length > 0 && (
                  <div className="mt-5 rounded-xl border border-[#F0E1A1] bg-[#FFFBEA] px-4 py-3">
                    <div className="flex items-start gap-3">
                      <svg
                        className="mt-0.5 h-4 w-4 shrink-0 text-[#806200]"
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="2"
                      >
                        <path d="M12 3 2.8 19h18.4L12 3Z" />
                        <path d="M12 9v4" />
                        <path d="M12 16h.01" />
                      </svg>

                      <div>
                        <p className="text-xs font-bold text-[#665000]">
                          Before committing
                        </p>

                        <p className="mt-1 text-xs leading-5 text-[#806200]">
                          Confirm that the borrower identity, loan references,
                          amounts, dates, rates and statuses in the source file
                          are correct. Committing creates production records.
                        </p>
                      </div>
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>

        {/* ====================================================
            IMPORT HISTORY
            ==================================================== */}

        <div className="mt-6 overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm">
          <div className="flex flex-col gap-2 border-b border-gray-100 px-5 py-5 sm:flex-row sm:items-center sm:justify-between sm:px-7">
            <div>
              <h2 className="text-base font-bold text-[#0B1F3A]">
                Import history
              </h2>

              <p className="mt-1 text-xs text-gray-400">
                Previous legacy migration batches processed by your
                organization.
              </p>
            </div>

            <span className="text-xs font-medium text-gray-400">
              {batches.length} batch
              {batches.length === 1 ? "" : "es"}
            </span>
          </div>

          {loadingBatches ? (
            <div className="flex min-h-[180px] items-center justify-center">
              <PageSpinner />
            </div>
          ) : batches.length === 0 ? (
            <div className="px-6 py-14 text-center">
              <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-xl bg-[#F1F5F9] text-[#0B1F3A]">
                <svg
                  className="h-5 w-5"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="1.7"
                >
                  <path d="M4 4h16v16H4z" />
                  <path d="M8 8h8" />
                  <path d="M8 12h8" />
                  <path d="M8 16h5" />
                </svg>
              </div>

              <h3 className="mt-4 text-sm font-bold text-gray-900">
                No imports yet
              </h3>

              <p className="mt-1 text-xs text-gray-400">
                Completed migration batches will appear here.
              </p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[800px] text-sm">
                <thead className="bg-[#F8FAFC]">
                  <tr className="border-b border-gray-200 text-left">
                    <th className="px-5 py-3 text-[11px] font-bold uppercase tracking-wide text-gray-500">
                      File
                    </th>

                    <th className="px-5 py-3 text-[11px] font-bold uppercase tracking-wide text-gray-500">
                      Rows
                    </th>

                    <th className="px-5 py-3 text-[11px] font-bold uppercase tracking-wide text-gray-500">
                      Status
                    </th>

                    <th className="px-5 py-3 text-[11px] font-bold uppercase tracking-wide text-gray-500">
                      Imported by
                    </th>

                    <th className="px-5 py-3 text-[11px] font-bold uppercase tracking-wide text-gray-500">
                      Date
                    </th>
                  </tr>
                </thead>

                <tbody className="divide-y divide-gray-100">
                  {batches.map((batch) => (
                    <tr
                      key={batch.id}
                      className="transition hover:bg-[#FAFBFC]"
                    >
                      <td className="px-5 py-4">
                        <div className="flex items-center gap-3">
                          <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-[#EAF1F8] text-[#0B1F3A]">
                            <svg
                              className="h-4 w-4"
                              viewBox="0 0 24 24"
                              fill="none"
                              stroke="currentColor"
                              strokeWidth="1.7"
                            >
                              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z" />
                              <path d="M14 2v6h6" />
                            </svg>
                          </div>

                          <span className="font-semibold text-gray-900">
                            {batch.fileName}
                          </span>
                        </div>
                      </td>

                      <td className="px-5 py-4">
                        <div className="font-semibold text-gray-900">
                          {batch.successCount}/{batch.totalRows}
                        </div>

                        {batch.failureCount > 0 && (
                          <div className="mt-0.5 text-xs text-red-500">
                            {batch.failureCount} failed
                          </div>
                        )}
                      </td>

                      <td className="px-5 py-4">
                        <Pill
                          label={batch.status}
                          color={
                            batch.status === "COMPLETED"
                              ? "green"
                              : batch.status === "PARTIAL"
                                ? "yellow"
                                : "red"
                          }
                        />
                      </td>

                      <td className="px-5 py-4 text-gray-600">
                        {batch.importedBy?.name || "—"}
                      </td>

                      <td className="px-5 py-4 text-xs text-gray-500">
                        {formatDate(batch.createdAt)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* ====================================================
            FOOTER
            ==================================================== */}

        <div className="flex flex-col gap-2 px-1 py-5 text-xs text-gray-400 sm:flex-row sm:items-center sm:justify-between">
          <span>Noble Loan Solutions · Controlled Data Migration</span>

          <span>Preview first · Review carefully · Commit deliberately</span>
        </div>
      </div>
    </div>
  );
}
