import axios from "axios";
import { currentTenantDomain } from "../lib/tenant";

const API = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api",

  timeout: 20000,

  headers: {
    "Content-Type": "application/json",
  },
});

API.interceptors.request.use(
  (config) => {
    if (typeof window !== "undefined") {
      const token = localStorage.getItem("token");

      if (token) {
        config.headers.Authorization = `Bearer ${token}`;
      }

      const tenantDomain = currentTenantDomain();

      if (tenantDomain) {
        config.headers["X-Tenant-Domain"] = tenantDomain;
      }
    }

    return config;
  },

  (error) => {
    return Promise.reject(error);
  },
);

API.interceptors.response.use(
  (response) => {
    return response;
  },

  (error) => {
    const status = error.response?.status;

    if (status === 401 && typeof window !== "undefined") {
      const requestUrl = error.config?.url || "";

      const isLoginRequest = requestUrl.includes("/auth/login");

      if (!isLoginRequest) {
        localStorage.removeItem("token");
        localStorage.removeItem("user");

        window.location.href = "/login";
      }
    }

    const message =
      error.response?.data?.error ||
      error.response?.data?.message ||
      error.message ||
      "An error occurred";

    const err = new Error(message) as Error & {
      status?: number;
      data?: unknown;
    };

    err.status = status;

    err.data = error.response?.data;

    return Promise.reject(err);
  },
);

export default API;

const unwrap = (body: unknown) => {
  if (
    body &&
    typeof body === "object" &&
    "data" in (body as Record<string, unknown>)
  ) {
    return (body as Record<string, unknown>).data;
  }

  return body;
};

export const get = (url: string) =>
  API.get(url).then((response) => unwrap(response.data));

export const post = (url: string, data?: unknown) =>
  API.post(url, data).then((response) => unwrap(response.data));

export const put = (url: string, data?: unknown) =>
  API.put(url, data).then((response) => unwrap(response.data));

export const del = (url: string) =>
  API.delete(url).then((response) => unwrap(response.data));

/**
 * =========================================================
 * AUTH
 * =========================================================
 */

export const authApi = {
  login: (email: string, password: string, mfaCode?: string, otp?: string) =>
    post("/auth/login", {
      email: email.trim().toLowerCase(),

      password,

      mfaCode: mfaCode?.trim() || undefined,

      otp: otp?.trim() || undefined,
    }),

  register: (data: unknown) => post("/auth/register", data),

  me: () => get("/auth/me"),
};

/**
 * =========================================================
 * LOANS
 * =========================================================
 */

export const loanApi = {
  list: (page = 0, size = 20, status = "", type = "") => {
    const params = new URLSearchParams();

    params.set("page", String(page));

    params.set("size", String(size));

    if (status) {
      params.set("status", status);
    }

    if (type) {
      params.set("type", type);
    }

    return get(`/loans?${params.toString()}`);
  },

  get: (id: number) => get(`/loans/${id}`),

  create: (data: unknown) => post("/loans", data),

  approve: (id: number, notes = "", interestRate?: number) =>
    post(`/loans/${id}/approve`, {
      notes,

      interestRate: interestRate != null ? String(interestRate) : undefined,
    }),

  reject: (id: number, reason: string) =>
    post(`/loans/${id}/reject`, {
      reason,
    }),

  disburse: (id: number, method: string) =>
    post(`/loans/${id}/disburse`, {
      disbursementMethod: method,
    }),

  updateStatus: (id: number, status: string, notes?: string) =>
    post(`/loans/${id}/status`, {
      status,
      notes,
    }),

  dashboard: () => get("/loans/dashboard"),

  schedule: (id: number) => get(`/loans/${id}/schedule`),

  risk: (id: number) => get(`/loans/${id}/risk`),

  documentRequirements: (id: number) =>
    get(`/loans/${id}/document-requirements`),

  restructure: (id: number, data: unknown) =>
    post(`/loans/${id}/restructure`, data),

  writeOff: (id: number, reason: string) =>
    post(`/loans/${id}/write-off`, {
      reason,
    }),

  moratorium: (id: number, data: unknown) =>
    post(`/loans/${id}/moratorium`, data),

  getComments: (id: number) => get(`/loans/${id}/comments`),

  addComment: (id: number, message: string, visibleToApplicant = true) =>
    post(`/loans/${id}/comments`, {
      message,
      visibleToApplicant,
    }),
};

/**
 * =========================================================
 * PAYMENTS
 * =========================================================
 */

export const paymentApi = {
  record: (loanId: number, data: unknown, idempotencyKey?: string) =>
    API.post(`/loans/${loanId}/payments`, data, {
      headers: idempotencyKey
        ? {
            "Idempotency-Key": idempotencyKey,
          }
        : {},
    }).then((response) => unwrap(response.data)),

  schedule: (loanId: number) => get(`/loans/${loanId}/payments`),
};

/**
 * =========================================================
 * BORROWERS
 * =========================================================
 */

export const borrowerApi = {
  list: (page = 0, size = 20, q = "") => {
    const params = new URLSearchParams();

    params.set("page", String(page));

    params.set("size", String(size));

    if (q) {
      params.set("q", q);
    }

    return get(`/borrowers?${params.toString()}`);
  },

  get: (id: number) => get(`/borrowers/${id}`),

  create: (data: unknown) => post("/borrowers", data),

  update: (id: number, data: unknown) => put(`/borrowers/${id}`, data),
};

/**
 * =========================================================
 * COMPLIANCE
 * =========================================================
 */

export const complianceApi = {
  screen: (borrowerId: number) =>
    post(`/compliance/borrowers/${borrowerId}/screen`),

  history: (borrowerId: number) =>
    get(`/compliance/borrowers/${borrowerId}/history`),

  status: (borrowerId: number) =>
    get(`/compliance/borrowers/${borrowerId}/status`),

  pendingReviews: () => get("/compliance/pending-reviews"),

  decide: (checkId: number, data: unknown) =>
    post(`/compliance/checks/${checkId}/decide`, data),
};

/**
 * =========================================================
 * MFA
 * =========================================================
 */

export const mfaApi = {
  setup: () => post("/mfa/setup"),

  confirm: (code: string) =>
    post("/mfa/confirm", {
      code,
    }),

  disable: () => post("/mfa/disable"),
};

/**
 * =========================================================
 * BULK
 * =========================================================
 */

export const bulkApi = {
  disburse: (loanIds: number[], method = "BANK_TRANSFER") =>
    post("/bulk/disburse", {
      loanIds,
      disbursementMethod: method,
    }),
};

/**
 * =========================================================
 * ORGANIZATION
 * =========================================================
 */

export const orgApi = {
  me: () => get("/organizations/me"),

  update: (data: unknown) => put("/organizations/me", data),

  users: () => get("/organizations/me/users"),
};

/**
 * =========================================================
 * WEBHOOKS
 * =========================================================
 */

export const webhookApi = {
  list: () => get("/webhooks"),

  create: (data: unknown) => post("/webhooks", data),

  remove: (id: number) => del(`/webhooks/${id}`),
};

/**
 * =========================================================
 * CURRENCY
 * =========================================================
 */

export const currencyApi = {
  rates: (base = "USD") => get(`/currencies?base=${encodeURIComponent(base)}`),

  convert: (from: string, to: string, amount: number) =>
    get(
      `/currencies/convert?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}&amount=${amount}`,
    ),

  supported: () => get("/currencies/supported"),

  status: () => get("/currencies/status"),

  refresh: () => post("/currencies/refresh"),
};

/**
 * =========================================================
 * PRIVACY
 * =========================================================
 */

export const privacyApi = {
  exportData: (id: number) => get(`/privacy/borrowers/${id}/export`),

  eraseData: (id: number) => del(`/privacy/borrowers/${id}/erase`),
};

/**
 * =========================================================
 * CREDIT BUREAU
 * =========================================================
 */

export const creditBureauApi = {
  check: (borrowerId: number) =>
    post(`/credit-bureau/borrowers/${borrowerId}/check`),

  history: (borrowerId: number) =>
    get(`/credit-bureau/borrowers/${borrowerId}/history`),

  latest: (borrowerId: number) =>
    get(`/credit-bureau/borrowers/${borrowerId}/latest`),

  reportForLoan: (loanId: number) =>
    get(`/credit-bureau/loans/${loanId}/report`),

  retryReport: (loanId: number) =>
    post(`/credit-bureau/loans/${loanId}/report/retry`),
};

export const esignatureApi = {
  initiate: (loanId: number, documentType = "LOAN_AGREEMENT") =>
    post(`/loans/${loanId}/esignature/initiate`, {
      documentType,
    }),

  history: (loanId: number) => get(`/loans/${loanId}/esignature`),
};

/**
 * =========================================================
 * ACCOUNTING
 * =========================================================
 */

export const accountingApi = {
  chartOfAccounts: () => get("/accounting/chart-of-accounts"),

  createAccount: (data: {
    code: string;
    name: string;
    type: string;
    normalBalance: string;
  }) => post("/accounting/chart-of-accounts", data),

  updateAccount: (
    id: number,
    data: {
      name?: string;
      active?: boolean;
    },
  ) => put(`/accounting/chart-of-accounts/${id}`, data),

  journal: () => get("/accounting/journal"),

  reverseEntry: (id: number, reason?: string) =>
    post(`/accounting/journal/${id}/reverse`, {
      reason,
    }),

  ledger: (accountId: number) => get(`/accounting/ledger/${accountId}`),

  trialBalance: () => get("/accounting/trial-balance"),

  balanceSheet: () => get("/accounting/balance-sheet"),

  profitAndLoss: (from?: string, to?: string) => {
    if (from && to) {
      return get(
        `/accounting/profit-and-loss?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
      );
    }

    return get("/accounting/profit-and-loss");
  },

  cashFlow: (from?: string, to?: string) => {
    if (from && to) {
      return get(
        `/accounting/cash-flow?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
      );
    }

    return get("/accounting/cash-flow");
  },

  branchSummary: (from?: string, to?: string) => {
    if (from && to) {
      return get(
        `/accounting/branch-summary?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
      );
    }

    return get("/accounting/branch-summary");
  },
};

/**
 * =========================================================
 * BANK ACCOUNTS
 * =========================================================
 */

export const bankAccountApi = {
  list: () => get("/bank-accounts"),

  create: (data: {
    name: string;
    accountType: string;
    bankName?: string;
    accountNumber?: string;
    openingBalance?: number;
    branchId?: number;
  }) => post("/bank-accounts", data),

  recordTransaction: (
    id: number,
    data: {
      type: string;
      amount: number;
      counterAccountId: number;
      description?: string;
    },
  ) => post(`/bank-accounts/${id}/transactions`, data),

  transfer: (data: {
    fromAccountId: number;
    toAccountId: number;
    amount: number;
    description?: string;
  }) => post("/bank-accounts/transfer", data),
};

/**
 * =========================================================
 * CONTACT MESSAGES
 * =========================================================
 */

export const contactMessageApi = {
  list: () => get("/contact-messages"),

  unreadCount: () => get("/contact-messages/unread-count"),

  markRead: (id: number) => post(`/contact-messages/${id}/read`),

  delete: (id: number) => del(`/contact-messages/${id}`),
};

export const publicApi = {
  getOrganization: () => get("/public/organization"),

  getTenant: (slug: string) =>
    get(`/public/tenant/${encodeURIComponent(slug)}`),

  getProducts: (slug: string) =>
    get(`/public/tenant/${encodeURIComponent(slug)}/products`),

  apply: (data: unknown) => post("/public/loan-application", data),

  trackApplication: (reference: string, phone: string) =>
    get(
      `/public/applications/${encodeURIComponent(reference.trim())}/status?phone=${encodeURIComponent(phone.trim())}`,
    ),

  trackDashboard: (reference: string, phone: string) =>
    post("/public/dashboard", {
      reference: reference.trim(),

      phone: phone.trim(),
    }),

  initiatePayment: (
    reference: string,
    phone: string,
    data: {
      amount?: number;

      paymentMethod: "MOBILE_MONEY" | "CARD" | "BANK_TRANSFER";

      phoneNumber?: string;
      network?: string;

      cardNumber?: string;
      cardCvv?: string;

      cardExpiryMonth?: string;
      cardExpiryYear?: string;

      accountNumber?: string;
      bankCode?: string;

      email?: string;
    },
  ) =>
    post(
      `/public/applications/${encodeURIComponent(reference.trim())}/payments/initiate?phone=${encodeURIComponent(phone.trim())}`,
      data,
    ),

  trackComments: (reference: string, phone: string) =>
    get(
      `/public/applications/${encodeURIComponent(reference.trim())}/comments?phone=${encodeURIComponent(phone.trim())}`,
    ),

  listDocuments: (reference: string, phone: string) =>
    get(
      `/public/applications/${encodeURIComponent(reference.trim())}/documents?phone=${encodeURIComponent(phone.trim())}`,
    ),

  downloadDocument: (
    reference: string,
    phone: string,
    doc: "agreement" | "schedule" | "receipt",
  ) =>
    API.get(
      `/public/applications/${encodeURIComponent(reference.trim())}/documents/${doc}.pdf?phone=${encodeURIComponent(phone.trim())}`,
      {
        responseType: "blob",
      },
    ),

  deleteDocument: (reference: string, phone: string, fileId: number) =>
    del(
      `/public/applications/${encodeURIComponent(reference.trim())}/documents/${fileId}?phone=${encodeURIComponent(phone.trim())}`,
    ),

  uploadDocument: (
    reference: string,
    phone: string,
    documentType: string,
    file: File | Blob,
    fileName?: string,
  ) => {
    const form = new FormData();

    form.append("phone", phone.trim());

    form.append("documentType", documentType);

    form.append(
      "file",
      file,
      fileName || (file instanceof File ? file.name : "upload.jpg"),
    );

    return API.post(
      `/public/applications/${encodeURIComponent(reference.trim())}/documents`,
      form,
      {
        headers: {
          "Content-Type": "multipart/form-data",
        },
      },
    ).then((response) => (response.data as any)?.data ?? response.data);
  },
};

/**
 * =========================================================
 * LEGACY LOAN IMPORT
 * =========================================================
 */

export const importApi = {
  template: () =>
    API.get("/import/legacy-loans/template", {
      responseType: "blob",
    }),

  preview: (file: File) => {
    const form = new FormData();

    form.append("file", file);

    return API.post("/import/legacy-loans/preview", form, {
      headers: {
        "Content-Type": "multipart/form-data",
      },
    }).then((response) => (response.data as any)?.data ?? response.data);
  },

  commit: (file: File) => {
    const form = new FormData();

    form.append("file", file);

    return API.post("/import/legacy-loans/commit", form, {
      headers: {
        "Content-Type": "multipart/form-data",
      },
    }).then((response) => (response.data as any)?.data ?? response.data);
  },

  batches: () => get("/import/legacy-loans/batches"),
};

/**
 * =========================================================
 * ACCOUNTING — EXPENSES & BRANCHES
 * =========================================================
 */
export const expenseApi = {
  list: (params?: {
    category?: string;
    branchId?: number;
    from?: string;
    to?: string;
    page?: number;
    size?: number;
  }) =>
    get(
      `/expenses?${new URLSearchParams(
        Object.entries(params ?? {}).reduce<Record<string, string>>(
          (acc, [key, value]) => {
            if (value !== undefined && value !== null && value !== "")
              acc[key] = String(value);
            return acc;
          },
          {},
        ),
      ).toString()}`,
    ),

  create: (data: Record<string, unknown> | FormData) => {
    if (data instanceof FormData) {
      return API.post("/expenses", data, {
        headers: { "Content-Type": "multipart/form-data" },
      }).then((response) => unwrap(response.data));
    }
    const form = new FormData();
    Object.entries(data).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== "") {
        form.append(key, value instanceof File ? value : String(value));
      }
    });
    return API.post("/expenses", form, {
      headers: { "Content-Type": "multipart/form-data" },
    }).then((response) => unwrap(response.data));
  },

  void: (id: number, reason?: string) =>
    API.patch(`/expenses/${id}/void`, reason ? { reason } : undefined).then(
      (response) => unwrap(response.data),
    ),

  receiptUrl: (id: number) => `${API.defaults.baseURL}/expenses/${id}/receipt`,
};

export const branchApi = {
  list: () => get("/branches"),
  create: (data: Record<string, unknown>) => post("/branches", data),
  update: (id: number, data: Record<string, unknown>) =>
    put(`/branches/${id}`, data),
  delete: (id: number) => del(`/branches/${id}`),
};
