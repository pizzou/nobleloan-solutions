import { get, post, put } from "./api";
import { Borrower } from "../types";

/**
 * ============================================================
 * BORROWER SERVICE
 * ============================================================
 *
 * This service is kept for pages/components that import:
 *
 *   createBorrower
 *   getBorrowers
 *   getBorrowerById
 *   updateBorrower
 *
 * The actual HTTP configuration remains centralized in api.ts.
 * ============================================================
 */

export const getBorrowers = (search?: string): Promise<Borrower[]> =>
  get(
    `/borrowers?page=0&size=5000${
      search ? `&q=${encodeURIComponent(search)}` : ""
    }`,
  ).then((response: any) => {
    /*
     * Backend may return either:
     *
     * [
     *   borrower,
     *   borrower
     * ]
     *
     * or:
     *
     * {
     *   content: [...]
     * }
     */

    if (Array.isArray(response)) {
      return response as Borrower[];
    }

    if (response && Array.isArray(response.content)) {
      return response.content as Borrower[];
    }

    return [];
  });

/**
 * ============================================================
 * GET BORROWER BY ID
 * ============================================================
 */

export const getBorrowerById = (id: number): Promise<Borrower> =>
  get(`/borrowers/${id}`) as Promise<Borrower>;

/**
 * ============================================================
 * CREATE BORROWER
 * ============================================================
 *
 * This is the function currently required by:
 *
 * app/dashboard/borrowers/new/page.tsx
 *
 * The backend endpoint is:
 *
 * POST /api/borrowers
 * ============================================================
 */

export const createBorrower = (
  payload: Partial<Borrower> & {
    singleCertificateNumber?: string;
    spouseFullName?: string;
    spouseNationalId?: string;
    spousePhone?: string;
    spouseConsent?: boolean;
  },
): Promise<Borrower> => post("/borrowers", payload) as Promise<Borrower>;

/**
 * ============================================================
 * UPDATE BORROWER
 * ============================================================
 */

export const updateBorrower = (
  id: number,
  payload: Partial<Borrower>,
): Promise<Borrower> => put(`/borrowers/${id}`, payload) as Promise<Borrower>;

/** Save profile and required documents atomically through the staff API. */
export const createBorrowerWithDocuments = (
  payload: Parameters<typeof createBorrower>[0],
  documents: Record<string, File>,
): Promise<Borrower> => {
  const form = new FormData();
  form.append(
    "borrower",
    new Blob([JSON.stringify(payload)], { type: "application/json" }),
  );
  Object.entries(documents).forEach(([type, file]) => form.append(type, file));
  return post("/borrowers/with-documents", form, {
    timeout: 120000,
  }) as Promise<Borrower>;
};
