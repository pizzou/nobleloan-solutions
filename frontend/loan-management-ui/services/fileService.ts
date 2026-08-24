import API from "./api";
import { BorrowerFile } from "../types/index";

const unwrap = (body: unknown): unknown => {
  if (body !== null && typeof body === "object" && "data" in body) {
    return (body as { data: unknown }).data;
  }
  return body;
};

const normalizeFileId = (fileId: number): number => {
  if (!Number.isSafeInteger(fileId) || fileId <= 0) {
    throw new Error("Invalid document ID.");
  }
  return fileId;
};

const getApiErrorMessage = (error: unknown): string | null => {
  if (!error || typeof error !== "object") return null;

  const candidate = error as {
    response?: { data?: unknown };
    message?: unknown;
  };

  const responseData = candidate.response?.data;

  if (responseData instanceof Blob) {
    return null;
  }

  if (responseData && typeof responseData === "object") {
    const data = responseData as Record<string, unknown>;

    const direct = [data.message, data.error, data.detail].find(
      (value) => typeof value === "string" && value.trim(),
    );

    if (typeof direct === "string") return direct.trim();

    if (data.data && typeof data.data === "object") {
      const nested = data.data as Record<string, unknown>;
      const nestedMessage = [nested.message, nested.error, nested.detail].find(
        (value) => typeof value === "string" && value.trim(),
      );
      if (typeof nestedMessage === "string") return nestedMessage.trim();
    }
  }

  if (typeof candidate.message === "string" && candidate.message.trim()) {
    return candidate.message.trim();
  }

  return null;
};

const parseBlobError = async (error: unknown): Promise<Error | null> => {
  if (!error || typeof error !== "object") return null;

  const candidate = error as {
    response?: { data?: unknown; status?: number };
    message?: unknown;
  };

  const responseData = candidate.response?.data;

  if (!(responseData instanceof Blob)) {
    const message = getApiErrorMessage(error);
    return message ? new Error(message) : null;
  }

  try {
    const text = await responseData.text();
    if (!text.trim()) return null;

    try {
      const parsed = JSON.parse(text) as Record<string, unknown>;
      const message = [parsed.message, parsed.error, parsed.detail].find(
        (value) => typeof value === "string" && value.trim(),
      );

      if (typeof message === "string") {
        return new Error(message.trim());
      }

      if (parsed.data && typeof parsed.data === "object") {
        const nested = parsed.data as Record<string, unknown>;
        const nestedMessage = [
          nested.message,
          nested.error,
          nested.detail,
        ].find((value) => typeof value === "string" && value.trim());
        if (typeof nestedMessage === "string") {
          return new Error(nestedMessage.trim());
        }
      }
    } catch {
      // The response was not JSON. Keep the original Axios error below.
    }
  } catch {
    // Ignore blob parsing failures and preserve the original error.
  }

  return null;
};

const throwReadableApiError = async (error: unknown): Promise<never> => {
  const blobError = await parseBlobError(error);
  if (blobError) throw blobError;

  const message = getApiErrorMessage(error);
  throw new Error(message || "The document request could not be completed.");
};

export const getFilesByBorrower = async (
  borrowerId: number,
): Promise<BorrowerFile[]> => {
  if (!Number.isSafeInteger(borrowerId) || borrowerId <= 0) {
    throw new Error("Invalid borrower ID.");
  }

  try {
    const response = await API.get(`/files/borrower/${borrowerId}`);
    const data = unwrap(response.data);
    return Array.isArray(data) ? (data as BorrowerFile[]) : [];
  } catch (error) {
    return await throwReadableApiError(error);
  }
};

export const uploadFile = async (
  borrowerId: number,
  file: File,
  documentType = "OTHER",
): Promise<BorrowerFile> => {
  if (!Number.isSafeInteger(borrowerId) || borrowerId <= 0) {
    throw new Error("Invalid borrower ID.");
  }

  if (!file) {
    throw new Error("Please select a document.");
  }

  const form = new FormData();
  form.append("file", file);
  form.append("documentType", documentType);

  try {
    // Do not manually set Content-Type here. The browser/Axios must add the
    // multipart boundary itself; manually forcing it can produce malformed
    // multipart requests in production browsers.
    const response = await API.post(`/files/upload/${borrowerId}`, form);

    return unwrap(response.data) as BorrowerFile;
  } catch (error) {
    return await throwReadableApiError(error);
  }
};

export const deleteFile = async (fileId: number): Promise<void> => {
  const id = normalizeFileId(fileId);

  try {
    await API.delete(`/files/${id}`);
  } catch (error) {
    return await throwReadableApiError(error);
  }
};

export const verifyFile = async (
  fileId: number,
  status: "VERIFIED" | "REJECTED" | "REPLACEMENT_REQUESTED",
  comment?: string,
  loanId?: number,
): Promise<BorrowerFile> => {
  const id = normalizeFileId(fileId);

  if (
    (status === "REJECTED" || status === "REPLACEMENT_REQUESTED") &&
    (!Number.isSafeInteger(loanId) || (loanId as number) <= 0)
  ) {
    throw new Error("A valid loan ID is required for this review action.");
  }

  try {
    const response = await API.patch(`/files/${id}/verify`, {
      status,
      ...(comment?.trim() ? { comment: comment.trim() } : {}),
      ...(loanId ? { loanId: String(loanId) } : {}),
    });

    return unwrap(response.data) as BorrowerFile;
  } catch (error) {
    return await throwReadableApiError(error);
  }
};

interface BlobFetchResult {
  blob: Blob;
  filename?: string;
}

function parseContentDispositionFilename(
  disposition: string | undefined,
): string | undefined {
  if (!disposition) return undefined;

  const utf8Match = disposition.match(/filename\*=UTF-8''([^;]+)/i);
  if (utf8Match?.[1]) {
    try {
      return decodeURIComponent(utf8Match[1]);
    } catch {
      return utf8Match[1];
    }
  }

  const basicMatch = disposition.match(/filename="?([^";]+)"?/i);
  return basicMatch?.[1]?.trim() || undefined;
}

async function fetchAsBlob(path: string): Promise<BlobFetchResult> {
  try {
    const response = await API.get(path, {
      responseType: "blob",
      validateStatus: (status: number) => status >= 200 && status < 300,
    });

    const blob =
      response.data instanceof Blob ? response.data : new Blob([response.data]);

    if (blob.size === 0) {
      throw new Error("The server returned an empty document.");
    }

    const disposition = response.headers?.["content-disposition"];
    const filename = parseContentDispositionFilename(
      typeof disposition === "string" ? disposition : undefined,
    );

    return { blob, filename };
  } catch (error) {
    return await throwReadableApiError(error);
  }
}

/**
 * Opens the document inline in a new tab.
 *
 * The window is opened synchronously from the click event so Chrome/Edge does
 * not classify it as an unsolicited popup after the authenticated request
 * completes.
 */
export const previewFile = async (fileId: number): Promise<void> => {
  const id = normalizeFileId(fileId);

  if (typeof window === "undefined") {
    throw new Error("Document preview is only available in a browser.");
  }

  const previewWindow = window.open("", "_blank");

  if (!previewWindow) {
    throw new Error(
      "Your browser blocked the preview window. Please allow pop-ups for this site and try again.",
    );
  }

  try {
    previewWindow.opener = null;
    previewWindow.document.title = "Document Preview";
    previewWindow.document.body.innerHTML =
      '<div style="font-family:Arial,sans-serif;padding:32px;color:#475569">Loading document preview…</div>';

    const { blob } = await fetchAsBlob(`/files/preview/${id}`);
    const url = URL.createObjectURL(blob);

    previewWindow.location.replace(url);

    window.setTimeout(
      () => {
        URL.revokeObjectURL(url);
      },
      5 * 60 * 1000,
    );
  } catch (error) {
    try {
      previewWindow.close();
    } catch {
      // Ignore browser close failures.
    }
    throw error;
  }
};

/** Forces a browser download using the authenticated API request. */
export const downloadFile = async (
  fileId: number,
  fileName?: string,
): Promise<void> => {
  const id = normalizeFileId(fileId);

  if (typeof document === "undefined") {
    throw new Error("Document download is only available in a browser.");
  }

  const { blob, filename } = await fetchAsBlob(`/files/download/${id}`);
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
  const id = normalizeFileId(fileId);
  const { blob } = await fetchAsBlob(`/files/preview/${id}`);
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
  VERIFIED: {
    label: "Verified",
    className: "bg-green-50 text-green-700",
  },
  REJECTED: {
    label: "Rejected",
    className: "bg-red-50 text-red-700",
  },
  REPLACEMENT_REQUESTED: {
    label: "Replacement Requested",
    className: "bg-amber-50 text-amber-700",
  },
};
