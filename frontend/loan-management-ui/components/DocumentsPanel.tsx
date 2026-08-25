"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useParams } from "next/navigation";
import {
  getFilesByBorrower,
  uploadFile,
  deleteFile,
  previewFile,
  downloadFile,
  verifyFile,
  formatFileSize,
  fileIcon,
  DOCUMENT_TYPE_LABELS,
  VERIFICATION_STATUS_META,
} from "../services/fileService";
import { BorrowerFile } from "../types/index";
import { toast } from "../hooks/useToast";

type ReviewAction = "VERIFIED" | "REJECTED" | "REPLACEMENT_REQUESTED";

const MAX_UPLOAD_BYTES = 8 * 1024 * 1024;
const MAX_COMMENT_LENGTH = 2000;

export default function DocumentsPanel({ borrowerId }: { borrowerId: number }) {
  const params = useParams<{ id?: string | string[] }>();
  const rawLoanId = params?.id;
  const loanIdValue = Array.isArray(rawLoanId) ? rawLoanId[0] : rawLoanId;
  const loanId = loanIdValue ? Number(loanIdValue) : Number.NaN;
  const hasValidLoanId = Number.isSafeInteger(loanId) && loanId > 0;

  const [files, setFiles] = useState<BorrowerFile[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [busyFileId, setBusyFileId] = useState<number | null>(null);
  const [verifyingId, setVerifyingId] = useState<number | null>(null);
  const [commentDraft, setCommentDraft] = useState("");
  const [pendingAction, setPendingAction] = useState<ReviewAction | null>(null);

  const getMsg = useCallback((err: unknown) => {
    if (!err || typeof err !== "object") {
      return "Something went wrong. Please try again.";
    }

    const value = err as {
      message?: unknown;
      response?: { data?: unknown };
    };

    const responseData = value.response?.data;

    if (
      responseData &&
      typeof responseData === "object" &&
      !(responseData instanceof Blob)
    ) {
      const body = responseData as Record<string, unknown>;
      const message = [body.message, body.error, body.detail].find(
        (item) => typeof item === "string" && item.trim(),
      );

      if (typeof message === "string") {
        return message.trim();
      }
    }

    if (err instanceof Error && err.message.trim()) {
      return err.message;
    }

    if (typeof value.message === "string" && value.message.trim()) {
      return value.message.trim();
    }

    return "Something went wrong. Please try again.";
  }, []);

  const load = useCallback(async () => {
    setLoadError(null);

    try {
      const result = await getFilesByBorrower(borrowerId);
      setFiles(Array.isArray(result) ? result : []);
    } catch (error: unknown) {
      console.error("[DOCUMENTS] Failed to load borrower documents:", error);
      setFiles([]);
      setLoadError(getMsg(error));
      throw error;
    }
  }, [borrowerId, getMsg]);

  useEffect(() => {
    let mounted = true;

    setLoading(true);

    load()
      .catch(() => undefined)
      .finally(() => {
        if (mounted) {
          setLoading(false);
        }
      });

    return () => {
      mounted = false;
    };
  }, [load]);

  const handleUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const input = e.currentTarget;
    const file = input.files?.[0];

    if (!file) return;

    if (file.size > MAX_UPLOAD_BYTES) {
      toast("error", "File must be 8MB or smaller.");
      input.value = "";
      return;
    }

    setUploading(true);

    try {
      await uploadFile(borrowerId, file);
      toast("success", `${file.name} uploaded successfully.`);
      await load();
    } catch (err: unknown) {
      console.error("[DOCUMENTS] Upload failed:", err);
      toast("error", getMsg(err));
    } finally {
      setUploading(false);
      input.value = "";
    }
  };

  const handleDelete = async (fileId: number, fileName: string) => {
    const confirmed = window.confirm(
      `Delete ${fileName}?\n\nThis action is recorded in the audit trail. Verified documents cannot be deleted.`,
    );

    if (!confirmed) return;

    setBusyFileId(fileId);

    try {
      await deleteFile(fileId);
      toast("success", "File deleted successfully.");
      await load();
    } catch (err: unknown) {
      console.error("[DOCUMENTS] Delete failed:", err);
      toast("error", getMsg(err));
    } finally {
      setBusyFileId(null);
    }
  };

  const handleDownload = async (fileId: number, fileName: string) => {
    setBusyFileId(fileId);

    try {
      await downloadFile(fileId, fileName);
      toast("success", "Document download started.");
    } catch (err: unknown) {
      console.error("[DOCUMENTS] Download failed:", err);
      toast("error", getMsg(err));
    } finally {
      setBusyFileId(null);
    }
  };

  const handlePreview = async (fileId: number) => {
    setBusyFileId(fileId);

    try {
      await previewFile(fileId);
    } catch (err: unknown) {
      console.error("[DOCUMENTS] Preview failed:", err);
      toast("error", getMsg(err));
    } finally {
      setBusyFileId(null);
    }
  };

  const startVerifyAction = (fileId: number, action: ReviewAction) => {
    if (action === "VERIFIED") {
      void submitVerify(fileId, action, "");
      return;
    }

    setVerifyingId(fileId);
    setPendingAction(action);
    setCommentDraft("");
  };

  const cancelReview = () => {
    if (busyFileId !== null) return;
    setVerifyingId(null);
    setPendingAction(null);
    setCommentDraft("");
  };

  const submitVerify = async (
    fileId: number,
    action: ReviewAction,
    comment: string,
  ) => {
    const normalizedComment = comment.trim();

    if (
      (action === "REJECTED" || action === "REPLACEMENT_REQUESTED") &&
      !normalizedComment
    ) {
      toast(
        "error",
        action === "REJECTED"
          ? "A rejection reason is required."
          : "A replacement reason is required.",
      );
      return;
    }

    if (normalizedComment.length > MAX_COMMENT_LENGTH) {
      toast(
        "error",
        `The review comment must not exceed ${MAX_COMMENT_LENGTH} characters.`,
      );
      return;
    }

    if (
      (action === "REJECTED" || action === "REPLACEMENT_REQUESTED") &&
      !hasValidLoanId
    ) {
      toast(
        "error",
        "The loan reference could not be determined. Refresh the loan page and try again.",
      );
      return;
    }

    setBusyFileId(fileId);

    try {
      await verifyFile(
        fileId,
        action,
        normalizedComment || undefined,
        hasValidLoanId ? loanId : undefined,
      );

      if (action === "REPLACEMENT_REQUESTED") {
        toast(
          "success",
          "Replacement requested. The borrower can now see the request in the application portal.",
        );
      } else if (action === "REJECTED") {
        toast(
          "success",
          "Document rejected and the borrower has been notified through the application portal.",
        );
      } else {
        toast("success", "Document verified successfully.");
      }

      setVerifyingId(null);
      setPendingAction(null);
      setCommentDraft("");
      await load();
    } catch (err: unknown) {
      console.error("[DOCUMENTS] Verification action failed:", err);
      toast("error", getMsg(err));
    } finally {
      setBusyFileId(null);
    }
  };

  const acceptedTypes = useMemo(() => ".pdf,.jpg,.jpeg,.png,.webp", []);

  if (loading) {
    return (
      <div className="text-center py-10 text-gray-400 text-sm">
        Loading documents…
      </div>
    );
  }

  return (
    <div className="bg-white rounded-xl border border-gray-200 overflow-hidden shadow-sm mt-4">
      <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100 bg-gray-50/50">
        <div>
          <h2 className="font-semibold text-gray-800 text-sm">
            Documents and KYC Files
          </h2>
          <p className="text-xs text-gray-400 mt-0.5">
            {files.length} {files.length !== 1 ? "files" : "file"} uploaded
          </p>
        </div>

        <label
          className={
            "cursor-pointer bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 " +
            "rounded-lg text-sm font-medium transition shadow-sm " +
            (uploading ? "opacity-60 pointer-events-none" : "")
          }
        >
          {uploading ? "Uploading..." : "+ Upload File"}
          <input
            type="file"
            className="hidden"
            onChange={handleUpload}
            disabled={uploading}
            accept={acceptedTypes}
          />
        </label>
      </div>

      {loadError && (
        <div className="mx-6 mt-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 flex items-center justify-between gap-4">
          <span>{loadError}</span>
          <button
            type="button"
            onClick={() => {
              setLoading(true);
              load()
                .catch(() => undefined)
                .finally(() => setLoading(false));
            }}
            className="shrink-0 font-semibold underline hover:no-underline"
          >
            Retry
          </button>
        </div>
      )}

      {files.length === 0 ? (
        <div className="text-center py-12">
          <p className="text-3xl mb-2">📁</p>
          <p className="text-gray-500 text-sm font-medium">
            No documents uploaded yet
          </p>
          <p className="text-gray-400 text-xs mt-1">
            Upload ID documents, KYC files, income proof, etc.
          </p>
        </div>
      ) : (
        <div className="divide-y divide-gray-100">
          {files.map((f) => {
            const currentStatus = String(
              f.verificationStatus || "PENDING",
            ).toUpperCase();

            let normalizedKey = "PENDING_VERIFICATION";

            if (currentStatus === "VERIFIED" || currentStatus === "APPROVED") {
              normalizedKey = "VERIFIED";
            } else if (
              currentStatus === "REJECTED" ||
              currentStatus === "DECLINED"
            ) {
              normalizedKey = "REJECTED";
            } else if (currentStatus === "REPLACEMENT_REQUESTED") {
              normalizedKey = "REPLACEMENT_REQUESTED";
            }

            const statusMeta = VERIFICATION_STATUS_META[normalizedKey] || {
              className: "bg-gray-100 text-gray-600 border-gray-200",
              label: "Pending Review",
            };

            const isVerified = normalizedKey === "VERIFIED";
            const isBusy = busyFileId === f.id;
            const contentAvailable = f.contentAvailable !== false;
            const isReviewOpen = verifyingId === f.id;
            const isSelfie =
              f.documentType === "SELFIE" ||
              f.documentType === "SELFIE_LIVENESS";
            const isIdFront = f.documentType === "NATIONAL_ID_FRONT";
            const isIdBack = f.documentType === "NATIONAL_ID_BACK";

            return (
              <div
                key={f.id}
                className="px-6 py-4 hover:bg-gray-50 transition-colors"
              >
                <div className="flex flex-col md:flex-row md:items-center gap-4">
                  <span className="text-2xl flex-shrink-0">
                    {isSelfie
                      ? "🤳"
                      : isIdFront
                        ? "🪪"
                        : isIdBack
                          ? "🆔"
                          : fileIcon(f.fileType)}
                  </span>

                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-gray-800 truncate">
                      {f.fileName}
                    </p>

                    <p className="text-xs text-gray-400 mt-0.5 flex items-center gap-2 flex-wrap">
                      <span>
                        {f.fileType || "application/octet-stream"} ·{" "}
                        {formatFileSize(f.fileSize)}
                      </span>

                      {f.documentType && (
                        <span className="px-1.5 py-0.5 rounded bg-gray-100 text-gray-600 font-semibold text-[10px] uppercase tracking-wide">
                          {isIdFront
                            ? "National ID (Front)"
                            : isIdBack
                              ? "National ID (Back)"
                              : DOCUMENT_TYPE_LABELS[f.documentType] ||
                                f.documentType.replace(/_/g, " ")}
                        </span>
                      )}

                      {f.uploadedByApplicant && (
                        <span className="px-1.5 py-0.5 rounded bg-blue-50 text-blue-600 font-semibold text-[10px] uppercase tracking-wide">
                          From Applicant
                        </span>
                      )}

                      <span
                        className={`px-1.5 py-0.5 rounded font-semibold text-[10px] uppercase tracking-wide ${statusMeta.className}`}
                      >
                        {statusMeta.label}
                      </span>
                    </p>
                  </div>

                  <div className="flex items-center gap-2 flex-shrink-0 flex-wrap justify-end">
                    <button
                      type="button"
                      onClick={() => void handlePreview(f.id)}
                      disabled={isBusy || !contentAvailable}
                      title={
                        contentAvailable
                          ? "Preview document"
                          : "The document record exists, but its stored file content is unavailable."
                      }
                      className="text-gray-600 hover:text-gray-800 text-xs font-medium border border-gray-200 bg-gray-50 hover:bg-gray-100 px-3 py-1.5 rounded-lg transition disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      {isBusy
                        ? "Working..."
                        : contentAvailable
                          ? "Preview"
                          : "Unavailable"}
                    </button>

                    <button
                      type="button"
                      onClick={() => void handleDownload(f.id, f.fileName)}
                      disabled={isBusy || !contentAvailable}
                      title={
                        contentAvailable
                          ? "Download document"
                          : "The document record exists, but its stored file content is unavailable."
                      }
                      className="text-blue-600 hover:text-blue-800 text-xs font-medium border border-blue-200 bg-blue-50 hover:bg-blue-100 px-3 py-1.5 rounded-lg transition disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      Download
                    </button>

                    {!contentAvailable && (
                      <span
                        className="text-amber-700 text-xs font-medium border border-amber-200 bg-amber-50 px-3 py-1.5 rounded-lg"
                        title="The document record exists, but its stored file content is missing."
                      >
                        File content unavailable
                      </span>
                    )}

                    {!isVerified && (
                      <button
                        type="button"
                        onClick={() => startVerifyAction(f.id, "VERIFIED")}
                        disabled={isBusy}
                        className="text-green-700 hover:text-green-800 text-xs font-medium border border-green-200 bg-green-50 hover:bg-green-100 px-3 py-1.5 rounded-lg transition disabled:opacity-50 disabled:cursor-not-allowed"
                      >
                        Verify
                      </button>
                    )}

                    <button
                      type="button"
                      onClick={() =>
                        startVerifyAction(f.id, "REPLACEMENT_REQUESTED")
                      }
                      disabled={isBusy}
                      className="text-amber-700 hover:text-amber-800 text-xs font-medium border border-amber-200 bg-amber-50 hover:bg-amber-100 px-3 py-1.5 rounded-lg transition disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      Request Replacement
                    </button>

                    {!isVerified && (
                      <button
                        type="button"
                        onClick={() => startVerifyAction(f.id, "REJECTED")}
                        disabled={isBusy}
                        className="text-red-500 hover:text-red-700 text-xs font-medium border border-red-100 bg-white hover:bg-red-50 px-3 py-1.5 rounded-lg transition disabled:opacity-50 disabled:cursor-not-allowed"
                      >
                        Reject
                      </button>
                    )}

                    <button
                      type="button"
                      onClick={() => void handleDelete(f.id, f.fileName)}
                      disabled={isBusy || isVerified}
                      title={
                        isVerified
                          ? "Verified documents cannot be deleted."
                          : "Delete document"
                      }
                      className="text-red-400 hover:text-red-600 text-xs font-medium border border-red-200 bg-red-50 hover:bg-red-100 px-3 py-1.5 rounded-lg transition disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      Delete
                    </button>
                  </div>
                </div>

                {f.officerComment && (
                  <div className="mt-2 ml-11 text-xs bg-gray-50 border border-gray-100 rounded-lg px-3 py-2 text-gray-600">
                    <span className="font-semibold text-gray-700">
                      Officer Comment
                    </span>
                    {f.verifiedByName && (
                      <span className="text-gray-400">
                        {" "}
                        · {f.verifiedByName}
                      </span>
                    )}
                    <p className="mt-0.5">{f.officerComment}</p>
                  </div>
                )}

                {isReviewOpen && (
                  <div className="mt-3 ml-11 bg-white border border-gray-200 rounded-lg p-3">
                    <label className="text-xs font-semibold text-gray-600 block mb-1.5">
                      {pendingAction === "REJECTED"
                        ? "Reason for rejection"
                        : "Note for the borrower (what to re-upload)"}
                    </label>

                    <textarea
                      value={commentDraft}
                      onChange={(e) => setCommentDraft(e.target.value)}
                      rows={2}
                      maxLength={MAX_COMMENT_LENGTH}
                      disabled={isBusy}
                      className="w-full text-sm border border-gray-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-200 disabled:bg-gray-50"
                      placeholder={
                        pendingAction === "REJECTED"
                          ? "e.g. The uploaded ID photo is blurry and cannot be verified."
                          : "e.g. Please upload a bank statement covering the last six months."
                      }
                    />

                    <div className="flex items-center justify-between mt-1">
                      <span className="text-[10px] text-gray-400">
                        {commentDraft.length}/{MAX_COMMENT_LENGTH}
                      </span>
                    </div>

                    <div className="flex gap-2 mt-2">
                      <button
                        type="button"
                        disabled={isBusy}
                        onClick={() => {
                          if (pendingAction) {
                            void submitVerify(
                              f.id,
                              pendingAction,
                              commentDraft,
                            );
                          }
                        }}
                        className="text-xs font-bold px-3 py-1.5 rounded-lg bg-gray-800 text-white hover:bg-gray-900 disabled:opacity-50 disabled:cursor-not-allowed"
                      >
                        {isBusy ? "Submitting..." : "Submit"}
                      </button>

                      <button
                        type="button"
                        disabled={isBusy}
                        onClick={cancelReview}
                        className="text-xs font-medium px-3 py-1.5 rounded-lg border border-gray-200 text-gray-600 hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
                      >
                        Cancel
                      </button>
                    </div>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}

      <div className="px-6 py-3 bg-gray-50 border-t border-gray-100">
        <p className="text-xs text-gray-400">
          Accepted: PDF, JPG, PNG, WEBP. Max 8MB per file.
        </p>
      </div>
    </div>
  );
}
