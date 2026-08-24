import API from "./api";
import { BorrowerFile } from "../types/index";

const unwrap = (body: unknown): unknown => {
  if (body !== null && typeof body === "object" && "data" in body) {
    return (body as { data: unknown }).data;
  }
  return body;
};

export const getFilesByBorrower = (
  borrowerId: number,
): Promise<BorrowerFile[]> =>
  API.get(`/files/borrower/${borrowerId}`).then(
    (r) => unwrap(r.data) as BorrowerFile[],
  );

export const uploadFile = (
  borrowerId: number,
  file: File,
  documentType = "OTHER",
): Promise<BorrowerFile> => {
  const form = new FormData();
  form.append("file", file);
  form.append("documentType", documentType);
  return API.post(`/files/upload/${borrowerId}`, form, {
    headers: { "Content-Type": "multipart/form-data" },
  }).then((r) => unwrap(r.data) as BorrowerFile);
};

export const deleteFile = (fileId: number): Promise<void> =>
  API.delete(`/files/${fileId}`).then(() => undefined);

export const verifyFile = (
  fileId: number,
  status: "VERIFIED" | "REJECTED" | "REPLACEMENT_REQUESTED",
  comment?: string,
  loanId?: number,
): Promise<BorrowerFile> =>
  API.patch(`/files/${fileId}/verify`, {
    status,
    comment,
    ...(loanId ? { loanId: String(loanId) } : {}),
  }).then((r) => unwrap(r.data) as BorrowerFile);

// `/api/files/**` requires a JWT. A normal window.open()/anchor cannot attach
// the Authorization header, so document bytes must be fetched through the
// authenticated axios instance first.
async function fetchAsBlob(
  path: string,
): Promise<{ blob: Blob; filename?: string }> {
  const res = await API.get(path, {
    responseType: "blob",
    validateStatus: (status) => status >= 200 && status < 300,
  });

  const blob = res.data instanceof Blob ? res.data : new Blob([res.data]);

  const disposition = res.headers?.["content-disposition"];
  let filename: string | undefined;

  if (typeof disposition === "string") {
    const utf8Match = disposition.match(/filename\*=UTF-8''([^;]+)/i);
    const basicMatch = disposition.match(/filename="?([^";]+)"?/i);

    if (utf8Match?.[1]) {
      try {
        filename = decodeURIComponent(utf8Match[1]);
      } catch {
        filename = utf8Match[1];
      }
    } else if (basicMatch?.[1]) {
      filename = basicMatch[1].trim();
    }
  }

  return { blob, filename };
}

/**
 * Opens the document inline in a new tab.
 *
 * The new window is deliberately opened synchronously from the click event.
 * Opening it only after the authenticated request completes is commonly
 * blocked by Chrome/Edge popup protection, which made the old Preview button
 * appear to do nothing.
 */
export const previewFile = async (fileId: number): Promise<void> => {
  if (typeof window === "undefined") {
    throw new Error("Document preview is only available in a browser.");
  }

  const previewWindow = window.open("", "_blank");

  if (!previewWindow) {
    throw new Error(
      "Your browser blocked the preview window. Please allow pop-ups for this site and try again.",
    );
  }

  previewWindow.opener = null;
  previewWindow.document.title = "Document Preview";
  previewWindow.document.body.innerHTML =
    '<div style="font-family:Arial,sans-serif;padding:32px;color:#475569">Loading document preview…</div>';

  try {
    const { blob } = await fetchAsBlob(`/files/preview/${fileId}`);
    const url = URL.createObjectURL(blob);

    previewWindow.location.href = url;

    window.setTimeout(
      () => {
        URL.revokeObjectURL(url);
      },
      5 * 60 * 1000,
    );
  } catch (error) {
    previewWindow.close();
    throw error;
  }
};

/** Forces a browser download using the authenticated API request. */
export const downloadFile = async (
  fileId: number,
  fileName?: string,
): Promise<void> => {
  if (typeof document === "undefined") {
    throw new Error("Document download is only available in a browser.");
  }

  const { blob, filename } = await fetchAsBlob(`/files/download/${fileId}`);
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");

  anchor.href = url;
  anchor.download = filename || fileName || "document";
  anchor.style.display = "none";
  anchor.rel = "noopener";

  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();

  window.setTimeout(() => {
    URL.revokeObjectURL(url);
  }, 60 * 1000);
};

/** Resolves to an object URL for authenticated inline rendering. */
export const getInlineBlobUrl = async (fileId: number): Promise<string> => {
  const { blob } = await fetchAsBlob(`/files/preview/${fileId}`);
  return URL.createObjectURL(blob);
};

export const formatFileSize = (bytes?: number): string => {
  if (!bytes) return "—";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
};

export const fileIcon = (fileType?: string): string => {
  if (!fileType) return "📄";
  if (fileType.includes("pdf")) return "📕";
  if (fileType.includes("image")) return "🖼️";
  if (fileType.includes("word") || fileType.includes("document")) return "📝";
  if (fileType.includes("sheet") || fileType.includes("excel")) return "📊";
  return "📄";
};

export const DOCUMENT_TYPE_LABELS: Record<string, string> = {
  NATIONAL_ID: "National ID",
  PASSPORT: "Passport",
  DRIVING_LICENSE: "Driving License",
  PROOF_OF_ADDRESS: "Proof of Address",
  BANK_STATEMENT: "Bank Statement",
  PAYSLIP: "Payslip",
  EMPLOYMENT_LETTER: "Employment Letter",
  BUSINESS_REGISTRATION: "Business Registration Certificate",
  COLLATERAL_DOCUMENT: "Collateral Document",
  SINGLE_CERTIFICATE: "Single Status Certificate",
  MARRIAGE_CERTIFICATE: "Marriage Certificate",
  SELFIE: "Selfie",
  OTHER: "Other Document",
};

export const VERIFICATION_STATUS_META: Record<
  string,
  { label: string; className: string }
> = {
  PENDING_VERIFICATION: {
    label: "Pending Verification",
    className: "bg-gray-100 text-gray-600",
  },
  VERIFIED: { label: "Verified", className: "bg-green-50 text-green-700" },
  REJECTED: { label: "Rejected", className: "bg-red-50 text-red-700" },
  REPLACEMENT_REQUESTED: {
    label: "Replacement Requested",
    className: "bg-amber-50 text-amber-700",
  },
};
