/**
 * Securely opens applicant-uploaded borrower documents through the same-origin
 * Next.js API proxy. Opening the blank tab happens synchronously in the click
 * event, before fetch() yields, so popup blockers do not discard the preview.
 */
export type ApplicantDocumentMode = "preview" | "download";

const PREVIEWABLE_TYPES = new Set([
  "application/pdf",
  "image/jpeg",
  "image/png",
  "image/webp",
]);

function safeFilename(value: string | undefined): string {
  const name = (value || "borrower-document").trim();
  const safe = name.replace(/[\\/\r\n\0]/g, "_").replace(/^\.+$/, "document");
  return safe.slice(0, 180) || "borrower-document";
}

function filenameMediaType(filename: string): string | null {
  const extension = filename.toLowerCase().split(".").pop();
  switch (extension) {
    case "pdf": return "application/pdf";
    case "jpg":
    case "jpeg": return "image/jpeg";
    case "png": return "image/png";
    case "webp": return "image/webp";
    default: return null;
  }
}

async function readableFailure(response: Response): Promise<Error> {
  const fallback = `The document request failed (HTTP ${response.status}). Please retry.`;
  const contentType = response.headers.get("content-type")?.toLowerCase() || "";

  if (contentType.includes("json") || contentType.startsWith("text/")) {
    try {
      const text = await response.text();
      if (text) {
        try {
          const body = JSON.parse(text) as Record<string, unknown>;
          const message = [body.message, body.error, body.detail].find(
            (value) => typeof value === "string" && value.trim(),
          );
          if (typeof message === "string") return new Error(message.trim());
        } catch {
          // Do not surface arbitrary HTML proxy pages or server stack traces.
        }
      }
    } catch {
      // Keep the safe status-based fallback.
    }
  }

  return new Error(fallback);
}

export async function openApplicantDocument(
  url: string,
  mode: ApplicantDocumentMode,
  requestedFilename?: string,
): Promise<void> {
  if (typeof window === "undefined" || typeof document === "undefined") {
    throw new Error("Borrower documents can only be opened in a browser.");
  }

  const filename = safeFilename(requestedFilename);
  let previewWindow: Window | null = null;

  if (mode === "preview") {
    // Do not pass the `noopener` feature here: browsers intentionally return
    // null from window.open() when noopener is requested, even when a tab opens.
    previewWindow = window.open("about:blank", "_blank");
    if (!previewWindow) {
      throw new Error("Your browser blocked the preview window. Please allow pop-ups and retry.");
    }

    try {
      previewWindow.opener = null;
      previewWindow.document.title = "Borrower document preview";
      const status = previewWindow.document.createElement("p");
      status.textContent = "Loading document preview…";
      status.style.cssText = "font:14px Arial,sans-serif;color:#475569;padding:24px";
      previewWindow.document.body.replaceChildren(status);
    } catch {
      // Continue the request; the preview tab may have navigated already.
    }
  }

  try {
    const response = await fetch(url, {
      method: "GET",
      credentials: "same-origin",
      cache: "no-store",
      headers: { Accept: "application/pdf, image/jpeg, image/png, image/webp, application/octet-stream" },
    });

    if (!response.ok) throw await readableFailure(response);

    const blob = await response.blob();
    if (blob.size === 0) throw new Error("The server returned an empty document. Please upload the document again.");

    const responseType = (response.headers.get("content-type") || blob.type || "")
      .split(";", 1)[0]
      .trim()
      .toLowerCase();
    const inferredType = filenameMediaType(filename);
    const mediaType = PREVIEWABLE_TYPES.has(responseType)
      ? responseType
      : (responseType === "application/octet-stream" && inferredType ? inferredType : responseType);

    if (mode === "preview" && !PREVIEWABLE_TYPES.has(mediaType)) {
      throw new Error("This document type cannot be previewed in the browser. Use Download instead.");
    }

    const viewBlob = mediaType && mediaType !== blob.type
      ? new Blob([blob], { type: mediaType })
      : blob;
    const objectUrl = URL.createObjectURL(viewBlob);

    if (mode === "preview") {
      if (!previewWindow || previewWindow.closed) {
        URL.revokeObjectURL(objectUrl);
        throw new Error("The preview window was closed before the document loaded. Please retry.");
      }
      previewWindow.location.replace(objectUrl);
      // Avoid revoking immediately: browser PDF/image viewers may load lazily.
      window.setTimeout(() => URL.revokeObjectURL(objectUrl), 10 * 60 * 1000);
      return;
    }

    const anchor = document.createElement("a");
    anchor.href = objectUrl;
    anchor.download = filename;
    anchor.rel = "noopener";
    anchor.style.display = "none";
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60 * 1000);
  } catch (error) {
    if (previewWindow && !previewWindow.closed) {
      try { previewWindow.close(); } catch { /* Browser may deny close. */ }
    }
    throw error instanceof Error ? error : new Error("Unable to open the borrower document. Please retry.");
  }
}
