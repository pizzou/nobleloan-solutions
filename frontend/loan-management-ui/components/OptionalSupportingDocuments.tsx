"use client";

import { useState } from "react";

export type CollateralType = "NONE" | "MOVABLE" | "IMMOVABLE";

export type OptionalSupportingDocument = {
  id: string;
  category: "COLLATERAL_DOCUMENT" | "COLLATERAL_EXPERTISE" | "OTHER";
  file: File;
};

type Props = {
  documents: OptionalSupportingDocument[];
  onChange: (documents: OptionalSupportingDocument[]) => void;
  disabled?: boolean;
  collateralType: CollateralType;
  onCollateralTypeChange: (type: CollateralType) => void;
  showCollateralSelector?: boolean;
};

const MAX_DOCUMENTS = 10;
const MAX_FILE_BYTES = 8 * 1024 * 1024;
const ACCEPTED_TYPES = new Set([
  "application/pdf",
  "image/jpeg",
  "image/png",
  "image/webp",
]);

export default function OptionalSupportingDocuments({
  documents,
  onChange,
  disabled = false,
  collateralType,
  onCollateralTypeChange,
  showCollateralSelector = true,
}: Props) {
  const [category, setCategory] =
    useState<OptionalSupportingDocument["category"]>("OTHER");
  const [error, setError] = useState("");

  const addFiles = (files: FileList | null, chosenCategory = category) => {
    if (!files?.length) return;
    const incoming = Array.from(files);
    if (documents.length + incoming.length > MAX_DOCUMENTS) {
      setError(
        `A maximum of ${MAX_DOCUMENTS} supporting documents may be attached.`,
      );
      return;
    }
    const invalid = incoming.find(
      (file) =>
        file.size <= 0 ||
        file.size > MAX_FILE_BYTES ||
        !ACCEPTED_TYPES.has(file.type),
    );
    if (invalid) {
      setError(
        `Invalid file: ${invalid.name}. Use a non-empty PDF, JPG, PNG, or WEBP up to 8 MB.`,
      );
      return;
    }
    setError("");
    onChange([
      ...documents,
      ...incoming.map((file) => ({
        id:
          typeof crypto !== "undefined" &&
          typeof crypto.randomUUID === "function"
            ? crypto.randomUUID()
            : `${Date.now()}-${Math.random().toString(36).slice(2)}`,
        category: chosenCategory,
        file,
      })),
    ]);
  };

  return (
    <section className="rounded-xl border border-slate-200 bg-white p-4 sm:p-5">
      <h3 className="text-base font-bold text-slate-900">
        {showCollateralSelector
          ? "Loan collateral and supporting documents"
          : "Additional supporting documents (optional)"}
      </h3>
      <p className="mt-1 text-sm text-slate-600">
        Add collateral valuation or expertise reports, land titles, property
        ownership evidence, guarantor evidence, or any other document supporting
        the loan. These do not replace mandatory identity and KYC documents.
      </p>
      {showCollateralSelector && (
        <label className="mt-4 block text-sm font-semibold text-slate-700">
          Collateral type
          <select
            value={collateralType}
            onChange={(event) => {
              const next = event.target.value as CollateralType;
              onCollateralTypeChange(next);
            }}
            disabled={disabled}
            className="mt-1 block w-full rounded-lg border border-slate-300 bg-white p-2.5 text-sm"
            aria-label="Collateral type"
          >
            <option value="NONE">No collateral</option>
            <option value="MOVABLE">
              Movable — vehicle, machinery, equipment, etc.
            </option>
            <option value="IMMOVABLE">
              Immovable — land, buildings, property, etc.
            </option>
          </select>
        </label>
      )}
      {showCollateralSelector && collateralType !== "NONE" && (
        <div className="mt-4 rounded-lg border border-amber-300 bg-amber-50 p-4">
          <label className="block text-sm font-semibold text-amber-950">
            Collateral expertise / professional valuation report{" "}
            <span className="text-red-700">*</span>
            <input
              type="file"
              accept=".pdf,.jpg,.jpeg,.png,.webp"
              required={
                collateralType === "IMMOVABLE" &&
                !documents.some(
                  (doc) => doc.category === "COLLATERAL_EXPERTISE",
                )
              }
              disabled={disabled || documents.length >= MAX_DOCUMENTS}
              className="mt-2 block w-full rounded-lg border border-amber-300 bg-white p-2.5 text-sm"
              onChange={(event) => {
                const selected = event.target.files;
                if (selected?.length) {
                  const existing = documents.filter(
                    (doc) => doc.category !== "COLLATERAL_EXPERTISE",
                  );
                  // Replacement is one file; preserve all other attachments.
                  if (existing.length >= MAX_DOCUMENTS) {
                    setError(
                      `A maximum of ${MAX_DOCUMENTS} supporting documents may be attached.`,
                    );
                  } else {
                    const file = selected[0];
                    if (
                      file.size <= 0 ||
                      file.size > MAX_FILE_BYTES ||
                      !ACCEPTED_TYPES.has(file.type)
                    ) {
                      setError(
                        "The expertise document must be a non-empty PDF or image up to 8 MB.",
                      );
                    } else {
                      setError("");
                      onChange([
                        ...existing,
                        {
                          id:
                            typeof crypto !== "undefined" && crypto.randomUUID
                              ? crypto.randomUUID()
                              : `${Date.now()}`,
                          category: "COLLATERAL_EXPERTISE",
                          file,
                        },
                      ]);
                    }
                  }
                }
                event.target.value = "";
              }}
            />
          </label>
          <p className="mt-2 text-xs text-amber-900">
            {collateralType === "IMMOVABLE"
              ? "Mandatory for immovable collateral."
              : "Optional for movable collateral."}{" "}
            Upload a professional collateral expertise or valuation document.
          </p>
        </div>
      )}
      <div className="mt-4 grid gap-3 sm:grid-cols-2">
        <label className="text-sm font-semibold text-slate-700">
          Document category
          <select
            value={category}
            onChange={(event) =>
              setCategory(
                event.target.value as OptionalSupportingDocument["category"],
              )
            }
            disabled={disabled}
            className="mt-1 block w-full rounded-lg border border-slate-300 bg-white p-2.5 text-sm"
          >
            {showCollateralSelector && (
              <option value="COLLATERAL_DOCUMENT">
                Collateral ownership, title deed or other collateral evidence
              </option>
            )}
            <option value="OTHER">Other loan supporting document</option>
          </select>
        </label>
        <label className="text-sm font-semibold text-slate-700">
          Select files
          <input
            type="file"
            multiple
            accept=".pdf,.jpg,.jpeg,.png,.webp"
            disabled={disabled || documents.length >= MAX_DOCUMENTS}
            className="mt-1 block w-full rounded-lg border border-slate-300 p-2.5 text-sm"
            onChange={(event) => {
              addFiles(event.target.files);
              event.target.value = "";
            }}
          />
        </label>
      </div>
      <p className="mt-2 text-xs text-slate-500">
        Maximum {MAX_DOCUMENTS} files per submission including the expertise
        report; 8 MB each. Documents will be reviewed by staff.
      </p>
      {error && (
        <p role="alert" className="mt-2 text-sm text-red-700">
          {error}
        </p>
      )}
      {documents.length > 0 && (
        <ul className="mt-3 space-y-2">
          {documents.map((item) => (
            <li
              key={item.id}
              className="flex items-center justify-between gap-3 rounded-lg bg-slate-50 px-3 py-2 text-sm"
            >
              <span className="min-w-0 break-all text-slate-700">
                {item.file.name} —{" "}
                {item.category === "COLLATERAL_EXPERTISE"
                  ? "Expertise / valuation report"
                  : item.category === "COLLATERAL_DOCUMENT"
                    ? "Collateral evidence"
                    : "Other evidence"}
              </span>
              <button
                type="button"
                disabled={disabled}
                onClick={() =>
                  onChange(documents.filter((doc) => doc.id !== item.id))
                }
                className="shrink-0 rounded-lg border border-slate-300 px-3 py-1 font-semibold text-slate-700 hover:bg-white disabled:opacity-50"
                aria-label={`Remove ${item.file.name}`}
              >
                Remove
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
