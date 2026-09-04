"use client";

import { useEffect, useState, useCallback } from "react";
import { publicApi } from "@/services/api";
import CameraCapture from "./CameraCapture";

interface DocItem {
  id: number;
  documentType: string;
  fileName: string;
  fileSize: number;
  verificationStatus?: string;
  uploadedByApplicant?: boolean;
  officerComment?: string;
  verifiedByName?: string;
  verifiedAt?: string;
}

interface RequiredDoc {
  type: string;
  label: string;
  count: number;
  camera?: boolean;
  accept?: string;
}

const OPTIONAL_DOC_TYPES: { type: string; label: string }[] = [
  { type: "PASSPORT", label: "Passport" },
  { type: "DRIVING_LICENSE", label: "Driving License" },
  { type: "PROOF_OF_ADDRESS", label: "Proof of Address" },
  { type: "PAYSLIP", label: "Payslip" },
  { type: "EMPLOYMENT_LETTER", label: "Employment Letter" },
  {
    type: "BUSINESS_REGISTRATION",
    label: "Business Registration Certificate",
  },
  {
    type: "COLLATERAL_DOCUMENT",
    label: "Collateral Document",
  },
  { type: "OTHER", label: "Other Document" },
];

function documentLabel(type: string): string {
  const labels: Record<string, string> = {
    NATIONAL_ID: "National ID",
    SELFIE: "Selfie (for identity verification)",
    PROOF_OF_ADDRESS: "Proof of Address",
    BANK_STATEMENT: "Bank Statements",
    MARRIAGE_CERTIFICATE: "Marriage Certificate",
    SINGLE_CERTIFICATE: "Single Status Certificate",
    PASSPORT: "Passport",
    DRIVING_LICENSE: "Driving License",
    PAYSLIP: "Payslip",
    EMPLOYMENT_LETTER: "Employment Letter",
    BUSINESS_REGISTRATION: "Business Registration Certificate",
    COLLATERAL_DOCUMENT: "Collateral Document",
  };

  return (
    labels[type] ||
    type
      .toLowerCase()
      .split("_")
      .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
      .join(" ")
  );
}

function requiredDocsFor(types?: string[]): RequiredDoc[] {
  const resolved = types?.length
    ? types
    : ["NATIONAL_ID", "SELFIE", "PROOF_OF_ADDRESS"];

  return resolved.map((type) => ({
    type,
    label: documentLabel(type),
    count: 1,
    camera: type === "SELFIE",
    accept:
      ".png,.jpg,.jpeg,.webp,.pdf,image/png,image/jpeg,image/webp,application/pdf",
  }));
}

export default function DocumentUploadPanel({
  reference,
  phone,
  maritalStatus: _maritalStatus,
  primary = "#0D6B3E",
  onStatusChange,
}: {
  reference: string;
  phone: string;
  maritalStatus?: string;
  primary?: string;
  onStatusChange?: (complete: boolean) => void;
}) {
  const [uploaded, setUploaded] = useState<DocItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [uploadingType, setUploadingType] = useState<string | null>(null);
  const [error, setError] = useState("");
  const [cameraFor, setCameraFor] = useState<string | null>(null);
  const [cameraReplaceId, setCameraReplaceId] = useState<number | null>(null);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [required, setRequired] = useState<RequiredDoc[]>(requiredDocsFor());

  const refresh = useCallback(async () => {
    setError("");

    try {
      const [documents, application] = await Promise.all([
        publicApi.listDocuments(reference, phone),
        publicApi.trackApplication(reference, phone),
      ]);

      setUploaded(documents as DocItem[]);

      const configuredRequired =
        application &&
        typeof application === "object" &&
        application.documentsRequired &&
        typeof application.documentsRequired === "object" &&
        Array.isArray(application.documentsRequired.required)
          ? application.documentsRequired.required.filter(
              (type: unknown): type is string =>
                typeof type === "string" && type.trim().length > 0,
            )
          : [];

      setRequired(requiredDocsFor(configuredRequired));
    } catch {
      setError(
        "Could not load your document checklist. Please refresh and try again.",
      );
    } finally {
      setLoading(false);
    }
  }, [reference, phone]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const countFor = (type: string) =>
    uploaded.filter((d) => d.documentType === type).length;

  const filesFor = (type: string) =>
    uploaded.filter((d) => d.documentType === type);

  const doUpload = async (type: string, file: File | Blob) => {
    setError("");
    setUploadingType(type);

    try {
      await publicApi.uploadDocument(reference, phone, type, file);

      refresh();
    } catch (err: any) {
      setError(err.message || "Upload failed. Please try again.");
    } finally {
      setUploadingType(null);
    }
  };

  const doReplace = async (doc: DocItem, file: File | Blob) => {
    setError("");
    setUploadingType(doc.documentType);

    try {
      await publicApi.replaceDocument(reference, phone, doc.id, file);

      refresh();
    } catch (err: any) {
      setError(err.message || "Replacement failed. Please try again.");
    } finally {
      setUploadingType(null);
    }
  };

  const handleDelete = async (doc: DocItem) => {
    if (doc.verificationStatus === "VERIFIED") {
      return;
    }

    if (
      !confirm(
        `Remove "${doc.fileName}"? You can upload a replacement right after.`,
      )
    ) {
      return;
    }

    setError("");
    setDeletingId(doc.id);

    try {
      await publicApi.deleteDocument(reference, phone, doc.id);

      refresh();
    } catch (err: any) {
      setError(err.message || "Could not remove that file. Please try again.");
    } finally {
      setDeletingId(null);
    }
  };

  const handleFileInput =
    (type: string) => (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0];

      if (file) {
        doUpload(type, file);
      }

      e.target.value = "";
    };

  const handleReplaceFileInput =
    (doc: DocItem) => (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0];

      if (file) {
        doReplace(doc, file);
      }

      e.target.value = "";
    };

  /*
   * Opens the camera.
   *
   * For SELFIE this is the ONLY way an applicant can upload
   * or replace the selfie.
   */
  const openCameraForDocument = (type: string, replaceId?: number) => {
    setCameraFor(type);
    setCameraReplaceId(replaceId !== undefined ? replaceId : null);
  };

  const allComplete = required.every((d) => countFor(d.type) >= d.count);

  useEffect(() => {
    if (!loading) {
      onStatusChange?.(allComplete);
    }
  }, [allComplete, loading, onStatusChange]);

  if (loading) {
    return (
      <div className="text-center py-8 text-sm text-gray-400">
        Loading document checklist…
      </div>
    );
  }

  return (
    <div className="bg-white rounded-xl border border-gray-200 p-6">
      <div className="flex items-center justify-between mb-1">
        <h3 className="font-bold text-gray-900">Required Documents</h3>

        {allComplete && (
          <span className="text-xs font-bold px-2.5 py-1 rounded-full bg-green-50 text-green-700">
            All uploaded ✓
          </span>
        )}
      </div>

      <p className="text-xs text-gray-500 mb-5">
        Accepted formats: PDF, JPG, PNG (max 8MB each).
        <br />
        <span className="font-medium">
          Selfie must be captured live using your camera.
        </span>
      </p>

      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded-lg px-3 py-2.5 mb-4">
          {error}
        </div>
      )}

      <div className="space-y-3">
        {required.map((doc) => {
          const count = countFor(doc.type);
          const complete = count >= doc.count;
          const busy = uploadingType === doc.type;

          /*
           * SELFIE:
           * Always camera.
           *
           * If a selfie already exists, the latest selfie is
           * replaced using the camera.
           */
          if (doc.type === "SELFIE") {
            const latestSelfie = filesFor("SELFIE").sort(
              (a, b) => b.id - a.id,
            )[0];

            return (
              <div
                key={doc.type}
                className="flex items-center justify-between gap-3 border border-gray-100 rounded-lg px-4 py-3"
              >
                <div className="flex items-center gap-3 min-w-0">
                  <div
                    className={`w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold shrink-0 ${
                      complete ? "text-white" : "bg-gray-100 text-gray-400"
                    }`}
                    style={complete ? { backgroundColor: primary } : {}}
                  >
                    {complete ? "✓" : ""}
                  </div>

                  <div className="min-w-0">
                    <div className="text-sm font-semibold text-gray-800 truncate">
                      {doc.label}
                    </div>

                    <div className="text-xs text-gray-400">
                      {complete
                        ? "Live selfie captured"
                        : "Live camera capture required"}
                    </div>
                  </div>
                </div>

                {latestSelfie?.verificationStatus === "VERIFIED" ? (
                  <span className="text-xs font-bold text-green-600 shrink-0">
                    Verified
                  </span>
                ) : (
                  <button
                    type="button"
                    onClick={() =>
                      openCameraForDocument("SELFIE", latestSelfie?.id)
                    }
                    disabled={busy}
                    className="text-xs font-bold px-3 py-2 rounded-md border shrink-0 disabled:opacity-50"
                    style={{
                      borderColor: primary,
                      color: primary,
                    }}
                  >
                    {busy
                      ? "Uploading…"
                      : latestSelfie?.verificationStatus ===
                          "REPLACEMENT_REQUESTED"
                        ? "Capture Replacement"
                        : complete
                          ? "Retake Selfie"
                          : "Open Camera"}
                  </button>
                )}
              </div>
            );
          }

          return (
            <div
              key={doc.type}
              className="flex items-center justify-between gap-3 border border-gray-100 rounded-lg px-4 py-3"
            >
              <div className="flex items-center gap-3 min-w-0">
                <div
                  className={`w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold shrink-0 ${
                    complete ? "text-white" : "bg-gray-100 text-gray-400"
                  }`}
                  style={complete ? { backgroundColor: primary } : {}}
                >
                  {complete ? "✓" : ""}
                </div>

                <div className="min-w-0">
                  <div className="text-sm font-semibold text-gray-800 truncate">
                    {doc.label}
                  </div>

                  {doc.count > 1 && (
                    <div className="text-xs text-gray-400">
                      {count} of {doc.count} uploaded
                    </div>
                  )}
                </div>
              </div>

              <label
                className="text-xs font-bold px-3 py-2 rounded-md border shrink-0 cursor-pointer text-center"
                style={{
                  borderColor: primary,
                  color: primary,
                  opacity: busy ? 0.5 : 1,
                }}
              >
                {busy
                  ? "Uploading…"
                  : count >= doc.count
                    ? "Add More"
                    : "Upload"}

                <input
                  type="file"
                  accept={doc.accept}
                  className="hidden"
                  disabled={busy}
                  onChange={handleFileInput(doc.type)}
                />
              </label>
            </div>
          );
        })}
      </div>

      {/* Uploaded required documents */}
      {uploaded.length > 0 && (
        <div className="mt-4 space-y-1.5">
          {required
            .filter((d) => countFor(d.type) > 0)
            .map((doc) => (
              <div key={doc.type}>
                {filesFor(doc.type).map((f) => (
                  <div
                    key={f.id}
                    className="flex items-center justify-between gap-3 text-xs text-gray-500 px-4 py-1.5"
                  >
                    <span className="truncate">
                      📄 {f.fileName}{" "}
                      <span className="text-gray-400">({doc.label})</span>
                      {f.officerComment &&
                      f.verificationStatus !== "VERIFIED" ? (
                        <span className="block text-red-600 mt-0.5">
                          {f.officerComment}
                        </span>
                      ) : null}
                    </span>

                    <div className="flex items-center gap-2 shrink-0">
                      {f.verificationStatus === "VERIFIED" && (
                        <span className="text-green-600 font-semibold">
                          Verified
                        </span>
                      )}

                      {/*
                       * IMPORTANT:
                       *
                       * SELFIE NEVER gets a file-picker replacement.
                       * Clicking Replace/Retake opens the camera.
                       */}
                      {f.documentType === "SELFIE"
                        ? f.verificationStatus !== "VERIFIED" && (
                            <button
                              type="button"
                              onClick={() =>
                                openCameraForDocument("SELFIE", f.id)
                              }
                              disabled={uploadingType === f.documentType}
                              className="text-slate-700 hover:text-slate-950 font-semibold disabled:opacity-50"
                            >
                              {uploadingType === f.documentType
                                ? "Opening…"
                                : f.verificationStatus ===
                                    "REPLACEMENT_REQUESTED"
                                  ? "Capture Replacement"
                                  : "Retake Selfie"}
                            </button>
                          )
                        : f.uploadedByApplicant === true && (
                            <label className="text-slate-700 hover:text-slate-950 font-semibold cursor-pointer">
                              Replace
                              <input
                                type="file"
                                accept=".png,.jpg,.jpeg,.pdf,image/png,image/jpeg,application/pdf"
                                className="hidden"
                                disabled={uploadingType === f.documentType}
                                onChange={handleReplaceFileInput(f)}
                              />
                            </label>
                          )}

                      {f.verificationStatus !== "VERIFIED" && (
                        <button
                          type="button"
                          onClick={() => handleDelete(f)}
                          disabled={deletingId === f.id}
                          className="text-red-500 hover:text-red-700 font-semibold disabled:opacity-50"
                        >
                          {deletingId === f.id ? "Removing…" : "✕ Remove"}
                        </button>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            ))}
        </div>
      )}

      {/* Camera */}
      {cameraFor && (
        <CameraCapture
          primary={primary}
          onClose={() => {
            setCameraFor(null);
            setCameraReplaceId(null);
          }}
          onCapture={(blob) => {
            const type = cameraFor;
            const replaceId = cameraReplaceId;

            setCameraFor(null);
            setCameraReplaceId(null);

            /*
             * If replacing an existing selfie,
             * replace that exact document.
             */
            if (replaceId != null) {
              const existing = uploaded.find((doc) => doc.id === replaceId);

              if (existing) {
                doReplace(existing, blob);

                return;
              }
            }

            /*
             * No existing selfie:
             * create a new selfie document.
             */
            doUpload(type, blob);
          }}
        />
      )}

      {/* Additional documents */}
      <div className="mt-6 pt-5 border-t border-gray-100">
        <h4 className="text-sm font-bold text-gray-800 mb-1">
          Additional Documents
        </h4>

        <p className="text-xs text-gray-500 mb-3">
          Upload anything else your loan officer may need — payslip, employment
          letter, proof of address, business registration, collateral documents,
          etc.
        </p>

        <div className="space-y-2">
          {OPTIONAL_DOC_TYPES.map((doc) => {
            const count = countFor(doc.type);

            const busy = uploadingType === doc.type;

            return (
              <div
                key={doc.type}
                className="border border-gray-100 rounded-lg px-4 py-2.5"
              >
                <div className="flex items-center justify-between gap-3">
                  <div className="text-sm text-gray-700 flex items-center gap-2 min-w-0">
                    <span className="truncate">{doc.label}</span>

                    {count > 0 && (
                      <span className="text-xs text-gray-400 shrink-0">
                        ({count} uploaded)
                      </span>
                    )}
                  </div>

                  <label
                    className="text-xs font-bold px-3 py-1.5 rounded-md border shrink-0 cursor-pointer text-center"
                    style={{
                      borderColor: primary,
                      color: primary,
                      opacity: busy ? 0.5 : 1,
                    }}
                  >
                    {busy ? "Uploading…" : count > 0 ? "Add More" : "Upload"}

                    <input
                      type="file"
                      accept=".png,.jpg,.jpeg,.pdf,image/png,image/jpeg,application/pdf"
                      className="hidden"
                      disabled={busy}
                      onChange={handleFileInput(doc.type)}
                    />
                  </label>
                </div>

                {filesFor(doc.type).map((f) => (
                  <div
                    key={f.id}
                    className="flex items-center justify-between gap-3 text-xs text-gray-500 pt-1.5 mt-1.5 border-t border-gray-50"
                  >
                    <span className="truncate">
                      📄 {f.fileName}
                      {f.officerComment &&
                      f.verificationStatus !== "VERIFIED" ? (
                        <span className="block text-red-600 mt-0.5">
                          {f.officerComment}
                        </span>
                      ) : null}
                    </span>

                    <div className="flex items-center gap-2 shrink-0">
                      {f.verificationStatus === "VERIFIED" && (
                        <span className="text-green-600 font-semibold">
                          Verified
                        </span>
                      )}

                      {f.uploadedByApplicant !== false && (
                        <label className="text-slate-700 hover:text-slate-950 font-semibold cursor-pointer">
                          Replace
                          <input
                            type="file"
                            accept=".png,.jpg,.jpeg,.pdf,image/png,image/jpeg,application/pdf"
                            className="hidden"
                            disabled={uploadingType === f.documentType}
                            onChange={handleReplaceFileInput(f)}
                          />
                        </label>
                      )}

                      {f.verificationStatus !== "VERIFIED" && (
                        <button
                          type="button"
                          onClick={() => handleDelete(f)}
                          disabled={deletingId === f.id}
                          className="text-red-500 hover:text-red-700 font-semibold disabled:opacity-50"
                        >
                          {deletingId === f.id ? "Removing…" : "✕ Remove"}
                        </button>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
