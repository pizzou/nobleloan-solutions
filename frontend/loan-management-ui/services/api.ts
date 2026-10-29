import { clearSensitiveClientState } from "@/lib/securityCleanup";
import axios, {
  AxiosError,
  AxiosHeaders,
  AxiosInstance,
  AxiosRequestConfig,
} from "axios";

/**
 * ============================================================
 * API CONFIGURATION
 * ============================================================
 */

const API_BASE_URL = "/api";

const API: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 20000,
  withCredentials: true,
  xsrfCookieName: "XSRF-TOKEN",
  xsrfHeaderName: "X-XSRF-TOKEN",
  headers: {
    "Content-Type": "application/json",
    Accept: "application/json",
    "Cache-Control": "no-store, no-cache, must-revalidate",
    Pragma: "no-cache",
  },
});

let sessionVerificationPromise: Promise<void> | null = null;
let csrfToken: string | null = null;
let csrfRefreshPromise: Promise<string | null> | null = null;

function isMutationMethod(method?: string): boolean {
  const value = String(method ?? "GET").toUpperCase();
  return (
    value === "POST" ||
    value === "PUT" ||
    value === "PATCH" ||
    value === "DELETE"
  );
}

function readCookie(name: string): string | null {
  if (typeof document === "undefined") return null;

  const prefix = `${name}=`;
  const item = document.cookie
    .split(";")
    .map((part) => part.trim())
    .find((part) => part.startsWith(prefix));

  return item ? decodeURIComponent(item.slice(prefix.length)) : null;
}

async function refreshCsrfToken(): Promise<string | null> {
  if (typeof window === "undefined") return null;

  if (!csrfRefreshPromise) {
    csrfRefreshPromise = axios
      .get(`${API_BASE_URL}/auth/csrf`, {
        withCredentials: true,
        timeout: 10000,
      })
      .then((response) => {
        const token =
          typeof response.data?.token === "string"
            ? response.data.token
            : readCookie("XSRF-TOKEN");
        csrfToken = token || null;
        return csrfToken;
      })
      .finally(() => {
        csrfRefreshPromise = null;
      });
  }

  return csrfRefreshPromise;
}

API.interceptors.request.use(async (config) => {
  // Never send the JSON default Content-Type with FormData. The browser must
  // generate the multipart boundary automatically; forcing multipart/form-data
  // without that boundary can cause Spring/Tomcat to expose an empty MultipartFile
  // in production even though a file was selected in the browser.
  if (typeof FormData !== "undefined" && config.data instanceof FormData) {
    const headers = AxiosHeaders.from(config.headers);
    headers.delete("Content-Type");
    config.headers = headers;
  }

  if (!isMutationMethod(config.method)) {
    return config;
  }

  const url = String(config.url ?? "");
  if (
    url.includes("/auth/login") ||
    url.includes("/auth/register") ||
    url.includes("/auth/forgot-password") ||
    url.includes("/auth/reset-password") ||
    url.includes("/auth/logout") ||
    url.includes("/auth/send-login-otp")
  ) {
    return config;
  }

  // Browser mutations are protected server-side by SameOriginMutationFilter.
  // Do not force every mutation through a second CSRF cookie/header handshake:
  // the Next.js/Vercel -> backend proxy can legitimately omit that cookie and
  // Spring's old CSRF layer would then turn valid authenticated requests into
  // 403 responses.

  return config;
});

API.interceptors.response.use(
  (response) => {
    // API calls normally return JSON. Reverse proxies and expired-session
    // gateways sometimes return an HTML document with HTTP 200; reject that
    // response rather than letting pages treat the HTML as application data.
    // Binary endpoints opt into blob/arraybuffer and validate their own bytes.
    const responseType = String(
      response.config.responseType ?? "",
    ).toLowerCase();
    const isBinaryResponse =
      responseType === "blob" || responseType === "arraybuffer";
    const contentType = String(
      response.headers?.["content-type"] ?? "",
    ).toLowerCase();
    if (
      !isBinaryResponse &&
      (contentType.includes("text/html") || isHtmlErrorPayload(response.data))
    ) {
      const requestId = String(response.headers?.["x-request-id"] ?? "").trim();
      throw new Error(
        `The server or security proxy returned an HTML error page instead of JSON. Please retry.${
          requestId ? ` Reference: ${requestId}` : ""
        }`,
      );
    }
    return response;
  },

  (error: AxiosError<unknown>) => {
    const status = error.response?.status;

    const responseData = error.response?.data;

    const requestUrl = String(error.config?.url ?? "");
    const isAuthMeRequest = requestUrl.includes("/auth/me");
    const isPublicRequest = requestUrl.includes("/public/");

    // A 401 on one resource is not proof that the entire login expired:
    // an upstream API can return 401 for its own authorization requirements.
    // Verify the signed session with /auth/me before clearing user data.
    // Use raw axios to avoid recursively intercepting the confirmation call.
    if (
      status === 401 &&
      !isAuthMeRequest &&
      !isPublicRequest &&
      !requestUrl.includes("/auth/login") &&
      !requestUrl.includes("/auth/send-login-otp") &&
      typeof window !== "undefined" &&
      window.location.pathname !== "/login"
    ) {
      if (!sessionVerificationPromise) {
        sessionVerificationPromise = axios
          .get(`${API_BASE_URL}/auth/me`, {
            withCredentials: true,
            timeout: 8000,
            headers: {
              Accept: "application/json",
              "Cache-Control": "no-store",
            },
          })
          .catch(async (checkError: unknown) => {
            // Only a confirmed /auth/me 401 can invalidate the browser session.
            if (
              axios.isAxiosError(checkError) &&
              checkError.response?.status === 401
            ) {
              await clearSensitiveClientState();
              if (window.location.pathname !== "/login")
                window.location.replace("/login");
            }
          })
          .then(() => undefined)
          .finally(() => {
            sessionVerificationPromise = null;
          });
      }
    }

    const requestId = String(
      error.response?.headers?.["x-request-id"] || "",
    ).trim();
    const htmlError = isHtmlErrorPayload(responseData);
    const message = htmlError
      ? `The server or security proxy returned an HTML error page instead of JSON. Please retry.${requestId ? ` Reference: ${requestId}` : " If the problem continues, contact support with the time of the error."}`
      : getAxiosErrorMessage(responseData) ||
        error.message ||
        `Request failed with status ${status ?? "unknown"}`;

    error.message = message;
    if (htmlError && error.response) {
      // Ensure callers inspecting error.response.data also cannot accidentally
      // render/log a full Cloudflare/proxy HTML challenge document.
      error.response.data = { message };
    }

    const enhancedError = error as AxiosError<unknown> & {
      status?: number;
      data?: unknown;
    };

    enhancedError.status = status;
    // Never copy a full HTML challenge/error document into view-model state or
    // application logging. Keep only its safe diagnostic message.
    enhancedError.data = htmlError ? { message } : responseData;

    return Promise.reject(enhancedError);
  },
);

function isHtmlErrorPayload(data: unknown): boolean {
  if (typeof data !== "string") return false;
  const sample = data.trimStart().slice(0, 1000).toLowerCase();
  return (
    sample.startsWith("<!doctype html") ||
    sample.startsWith("<html") ||
    sample.includes("<title>just a moment") ||
    (sample.includes("cloudflare") && sample.includes("challenge"))
  );
}

function getAxiosErrorMessage(data: unknown): string | null {
  if (!data) return null;

  if (typeof data === "string") {
    if (isHtmlErrorPayload(data))
      return "The server returned an HTML error page instead of JSON. Please retry.";
    // Avoid presenting very large plain-text proxy/exception bodies in the UI.
    return data.length > 1000 ? `${data.slice(0, 1000)}…` : data || null;
  }

  if (typeof data === "object") {
    const value = data as {
      message?: unknown;
      error?: unknown;
      detail?: unknown;
    };

    // Spring validation responses use a generic top-level `error` such as
    // "Validation failed" and put actionable field errors in `detail`. Prefer
    // those details so a 400 is diagnosable rather than displaying only the
    // generic summary.
    if (
      value.detail &&
      typeof value.detail === "object" &&
      !Array.isArray(value.detail)
    ) {
      const details = Object.entries(value.detail as Record<string, unknown>)
        .filter(
          (entry): entry is [string, string] =>
            typeof entry[1] === "string" && Boolean(entry[1].trim()),
        )
        .slice(0, 3)
        .map(([field, message]) => `${field}: ${message}`);
      if (details.length) return details.join("; ").slice(0, 400);
    }

    if (typeof value.message === "string" && value.message) {
      return value.message;
    }

    if (typeof value.error === "string" && value.error) {
      return value.error;
    }

    if (typeof value.detail === "string" && value.detail) {
      return value.detail;
    }
  }

  return null;
}

/**
 * ============================================================
 * RESPONSE UNWRAPPER
 * ============================================================
 */

const unwrap = (body: unknown): unknown => {
  if (body && typeof body === "object" && "data" in body) {
    return (body as Record<string, unknown>).data;
  }

  return body;
};

/**
 * ============================================================
 * GENERIC HTTP HELPERS
 * ============================================================
 */

export const get = (url: string, config?: AxiosRequestConfig): Promise<any> =>
  API.get(url, config).then((response) => unwrap(response.data));

export const post = (
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig,
): Promise<any> =>
  API.post(url, data, config).then((response) => unwrap(response.data));

export const put = (
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig,
): Promise<any> =>
  API.put(url, data, config).then((response) => unwrap(response.data));

export const patch = (
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig,
): Promise<any> =>
  API.patch(url, data, config).then((response) => unwrap(response.data));

export const del = (url: string, config?: AxiosRequestConfig): Promise<any> =>
  API.delete(url, config).then((response) => unwrap(response.data));

/**
 * Generate a stable key once for a mutation and reuse it if the request
 * must be moved into the durable offline queue.
 */
export function createIdempotencyKey(): string {
  if (
    typeof crypto !== "undefined" &&
    typeof crypto.randomUUID === "function"
  ) {
    return crypto.randomUUID();
  }

  return `${Date.now()}-${Math.random().toString(36).slice(2)}-${Math.random()
    .toString(36)
    .slice(2)}`;
}

/**
 * True only for failures where retrying later is safe/appropriate.
 */
export function isRetryableRequestError(error: unknown): boolean {
  if (axios.isAxiosError(error)) {
    const status = error.response?.status;

    if (status == null) return true;
    if ([401, 403, 400, 404, 405, 409, 422].includes(status)) return false;

    return status === 408 || status === 425 || status === 429 || status >= 500;
  }

  return error instanceof TypeError;
}

/**
 * ============================================================
 * AUTH API
 * ============================================================
 */

export const authApi = {
  csrf: () => get("/auth/csrf"),

  login: (
    email: string,
    password: string,
    mfaCode?: string,
    otp?: string,
    otpChallengeToken?: string,
  ) =>
    post("/auth/login", {
      email,
      password,
      mfaCode,
      otp,
      otpChallengeToken,
    }),

  sendLoginOtp: (otpChallengeToken: string) => {
    const token = otpChallengeToken?.trim();

    if (!token) {
      return Promise.reject(
        new Error(
          "OTP verification challenge is missing. Please sign in again.",
        ),
      );
    }

    return post(
      "/auth/send-login-otp",
      { otpChallengeToken: token },
      { timeout: 20000 },
    );
  },

  register: (data: unknown) => post("/auth/register", data),

  forgotPassword: (email: string) =>
    post("/auth/forgot-password", { email: email.trim() }),

  resetPassword: (token: string, newPassword: string) =>
    post("/auth/reset-password", { token, newPassword }),

  me: () => get("/auth/me"),

  /** Only call after a real keyboard, pointer, touch or scroll interaction. */
  renew: () => post("/auth/renew", {}),

  logout: () => post("/auth/logout"),
};

export const taskApi = {
  mine: (limit = 25) =>
    get(`/tasks/mine?limit=${Math.max(1, Math.min(limit, 50))}`),
  kpi: () => get("/tasks/mine/kpi"),
  create: (data: unknown) => post("/tasks", data),
  start: (id: number) => post(`/tasks/${id}/start`, {}),
  complete: (id: number) => post(`/tasks/${id}/complete`, {}),
};

/**
 * ============================================================
 * LOAN API
 * ============================================================
 */

export const loanApi = {
  list: (page = 0, size = 20, status = "", type = "", search = "") =>
    get(
      `/loans?page=${page}&size=${size}` +
        `${status ? `&status=${encodeURIComponent(status)}` : ""}` +
        `${type ? `&type=${encodeURIComponent(type)}` : ""}` +
        `${search.trim() ? `&search=${encodeURIComponent(search.trim())}` : ""}`,
    ),

  get: (id: number) => get(`/loans/${id}`),

  correctionHistory: (loanId: number) =>
    get(`/loans/${loanId}/legacy-corrections`),

  previewLegacyCorrection: (loanId: number, data: unknown) =>
    post(`/loans/${loanId}/legacy-corrections/preview`, data),

  submitLegacyCorrection: (loanId: number, data: unknown) =>
    post(`/loans/${loanId}/legacy-corrections`, data),

  decideLegacyCorrection: (
    loanId: number,
    correctionId: number,
    approve: boolean,
    decision: { confirmReference: string; reason: string },
  ) =>
    post(
      `/loans/${loanId}/legacy-corrections/${correctionId}/${approve ? "approve" : "reject"}`,
      decision,
    ),

  pendingLegacyCorrections: () => get("/legacy-corrections/pending"),

  changeReportingVisibility: (id: number, businessOwnerOnly: boolean) =>
    patch(`/loans/${id}/visibility`, { businessOwnerOnly }),

  deleteWithConfirmation: (id: number, confirmation: string, reason: string) =>
    del(`/loans/${id}`, { data: { confirmation, reason } }),

  recycleBin: () => get("/loans/recycle-bin"),

  restore: (id: number, confirmation: string) =>
    post(`/loans/${id}/restore`, { confirmation }),

  create: (data: unknown, idempotencyKey?: string) =>
    post(
      "/loans",
      data,
      idempotencyKey
        ? { headers: { "Idempotency-Key": idempotencyKey } }
        : undefined,
    ),

  approve: (
    id: number,
    notes = "",
    interestRate?: number,
    applicationFeeRate?: number,
    approvedAmount?: number,
    idempotencyKey?: string,
    businessOwnerOnly?: boolean,
  ) =>
    post(
      `/loans/${id}/approve`,
      {
        notes,
        interestRate: interestRate != null ? String(interestRate) : undefined,
        applicationFeeRate:
          applicationFeeRate != null ? String(applicationFeeRate) : undefined,
        approvedAmount:
          approvedAmount != null ? String(approvedAmount) : undefined,
        businessOwnerOnly,
      },
      idempotencyKey
        ? { headers: { "Idempotency-Key": idempotencyKey } }
        : undefined,
    ),

  reject: (id: number, reason: string, idempotencyKey?: string) =>
    post(
      `/loans/${id}/reject`,
      { reason },
      idempotencyKey
        ? { headers: { "Idempotency-Key": idempotencyKey } }
        : undefined,
    ),

  disburse: (id: number, method: string, idempotencyKey?: string) =>
    post(
      `/loans/${id}/disburse`,
      { disbursementMethod: method },
      idempotencyKey
        ? { headers: { "Idempotency-Key": idempotencyKey } }
        : undefined,
    ),

  updateStatus: (
    id: number,
    status: string,
    notes?: string,
    idempotencyKey?: string,
  ) =>
    post(
      `/loans/${id}/status`,
      { status, notes },
      idempotencyKey
        ? { headers: { "Idempotency-Key": idempotencyKey } }
        : undefined,
    ),

  dashboard: () => get(`/loans/dashboard?_ts=${Date.now()}`),

  schedule: (id: number) => get(`/loans/${id}/schedule`),

  risk: (id: number) => get(`/loans/${id}/risk`),

  documentRequirements: (id: number) =>
    get(`/loans/${id}/document-requirements`),

  restructure: (id: number, data: unknown) =>
    post(`/loans/${id}/restructure`, data),

  extend: (id: number, data: unknown) => post(`/loans/${id}/extend`, data),

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
 * ============================================================
 * BRANCH API
 * ============================================================
 */

export const branchApi = {
  list: () => get("/branches"),
};

/**
 * ============================================================
 * EXPENSE API
 * ============================================================
 */

export const expenseApi = {
  list: (
    params: {
      page?: number;
      size?: number;
      category?: string;
      branchId?: number;
      from?: string;
      to?: string;
    } = {},
  ) => {
    const query = new URLSearchParams();

    query.set("page", String(params.page ?? 0));
    query.set("size", String(params.size ?? 20));

    if (params.category) {
      query.set("category", params.category);
    }

    if (params.branchId != null) {
      query.set("branchId", String(params.branchId));
    }

    if (params.from) {
      query.set("from", params.from);
    }

    if (params.to) {
      query.set("to", params.to);
    }

    return get(`/expenses?${query.toString()}`);
  },

  get: (id: number) => get(`/expenses/${id}`),

  summary: (from?: string, to?: string) =>
    get(
      `/expenses/summary${
        from && to
          ? `?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`
          : ""
      }`,
    ),

  void: (id: number, reason?: string) =>
    post(`/expenses/${id}/void`, {
      reason,
    }),

  receiptUrl: (id: number) => `${API_BASE_URL}/expenses/${id}/receipt`,

  create: (data: {
    expenseDate: string;
    category: string;
    amount: number;
    paymentAccountId: number;
    branchId?: number;
    description?: string;
    paymentMethod: string;
    paymentProvider?: string;
    paymentPhoneNumber?: string;
    paymentTransactionReference?: string;
    paymentCode?: string;
    cardBrand?: string;
    cardLastFour?: string;
    cardAuthorizationCode?: string;
    chequeNumber?: string;
    paymentNotes?: string;
    receipt?: File | null;
    approvalId: number;
  }) => {
    const form = new FormData();

    form.append("expenseDate", data.expenseDate);
    form.append("category", data.category);
    form.append("amount", String(data.amount));
    form.append("paymentAccountId", String(data.paymentAccountId));
    form.append("approvalId", String(data.approvalId));

    if (data.branchId != null) {
      form.append("branchId", String(data.branchId));
    }

    if (data.description) {
      form.append("description", data.description);
    }

    form.append("paymentMethod", data.paymentMethod);

    if (data.paymentProvider) {
      form.append("paymentProvider", data.paymentProvider);
    }

    if (data.paymentPhoneNumber) {
      form.append("paymentPhoneNumber", data.paymentPhoneNumber);
    }

    if (data.paymentTransactionReference) {
      form.append(
        "paymentTransactionReference",
        data.paymentTransactionReference,
      );
    }

    if (data.paymentCode) {
      form.append("paymentCode", data.paymentCode);
    }

    if (data.cardBrand) {
      form.append("cardBrand", data.cardBrand);
    }

    if (data.cardLastFour) {
      form.append("cardLastFour", data.cardLastFour);
    }

    if (data.cardAuthorizationCode) {
      form.append("cardAuthorizationCode", data.cardAuthorizationCode);
    }

    if (data.chequeNumber) {
      form.append("chequeNumber", data.chequeNumber);
    }

    if (data.paymentNotes) {
      form.append("paymentNotes", data.paymentNotes);
    }

    if (data.receipt) {
      form.append("receipt", data.receipt);
    }

    return API.post("/expenses", form).then((response) =>
      unwrap(response.data),
    );
  },
};

/**
 * ============================================================
 * PAYMENT API
 * ============================================================
 */

export const paymentApi = {
  record: (loanId: number, data: unknown, idempotencyKey?: string) => {
    const key = idempotencyKey || createIdempotencyKey();

    return API.post(`/loans/${loanId}/payments`, data, {
      headers: {
        "Idempotency-Key": key,
      },
    }).then((response) => unwrap(response.data));
  },

  createIdempotencyKey,

  schedule: (loanId: number) => get(`/loans/${loanId}/payments`),

  transactions: () => get("/payment-transactions"),
};

/**
 * ============================================================
 * BORROWER API
 * ============================================================
 */

export const borrowerApi = {
  list: (page = 0, size = 20, q = "") =>
    get(
      `/borrowers?page=${page}&size=${size}` +
        `${q ? `&q=${encodeURIComponent(q)}` : ""}`,
    ),

  get: (id: number) => get(`/borrowers/${id}`),

  getDetails: (id: number) => get(`/borrowers/${id}/details`),

  create: (data: unknown) => post("/borrowers", data),

  update: (id: number, data: unknown) => put(`/borrowers/${id}`, data),
};

/**
 * ============================================================
 * COMPLIANCE API
 * ============================================================
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
 * ============================================================
 * MFA API
 * ============================================================
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
 * ============================================================
 * BULK API
 * ============================================================
 */

export const bulkApi = {
  disburse: (loanIds: number[], method = "BANK_TRANSFER") =>
    post("/bulk/disburse", {
      loanIds,
      disbursementMethod: method,
    }),
};

/**
 * ============================================================
 * ORGANIZATION API
 * ============================================================
 */

export const orgApi = {
  me: () => get("/organizations/me"),

  update: (data: unknown) => put("/organizations/me", data),

  users: () => get("/organizations/me/users"),
};

/**
 * ============================================================
 * WEBHOOK API
 * ============================================================
 */

export const webhookApi = {
  list: () => get("/webhooks"),

  create: (data: unknown) => post("/webhooks", data),

  remove: (id: number) => del(`/webhooks/${id}`),
};

/**
 * ============================================================
 * CURRENCY API
 * ============================================================
 */

export const currencyApi = {
  rates: (base = "USD") => get(`/currencies?base=${encodeURIComponent(base)}`),

  convert: (from: string, to: string, amount: number) =>
    get(
      `/currencies/convert?from=${encodeURIComponent(
        from,
      )}&to=${encodeURIComponent(to)}&amount=${amount}`,
    ),

  supported: () => get("/currencies/supported"),

  status: () => get("/currencies/status"),

  refresh: () => post("/currencies/refresh"),
};

/**
 * ============================================================
 * PRIVACY API
 * ============================================================
 */

export const privacyApi = {
  exportData: (id: number) => get(`/privacy/borrowers/${id}/export`),

  eraseData: (id: number) => del(`/privacy/borrowers/${id}/erase`),
};

/**
 * ============================================================
 * CREDIT BUREAU API
 * ============================================================
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

/**
 * ============================================================
 * E-SIGNATURE API
 * ============================================================
 */

export const esignatureApi = {
  initiate: (loanId: number, documentType = "LOAN_AGREEMENT") =>
    post(`/loans/${loanId}/esignature/initiate`, {
      documentType,
    }),

  history: (loanId: number) => get(`/loans/${loanId}/esignature`),
};

/**
 * ============================================================
 * ACCOUNTING API
 * ============================================================
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

  journalEntry: (id: number) => get(`/accounting/journal/${id}`),

  reverseEntry: (id: number, reason?: string) =>
    post(`/accounting/journal/${id}/reverse`, {
      reason,
    }),

  ledger: (accountId: number) => get(`/accounting/ledger/${accountId}`),

  trialBalance: () => get("/accounting/trial-balance"),

  balanceSheet: () => get("/accounting/balance-sheet"),

  profitAndLoss: (from?: string, to?: string) =>
    get(
      `/accounting/profit-and-loss${
        from && to
          ? `?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`
          : ""
      }`,
    ),

  cashFlow: (from?: string, to?: string) =>
    get(
      `/accounting/cash-flow${
        from && to
          ? `?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`
          : ""
      }`,
    ),

  branchSummary: (from?: string, to?: string) =>
    get(
      `/accounting/branch-summary${
        from && to
          ? `?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`
          : ""
      }`,
    ),

  /**
   * Starts the long-running imported-loan accounting reconciliation.
   * The backend returns HTTP 202 with a durable job id; callers must poll
   * reconcileLegacyLoansStatus(jobId) until the job is COMPLETED or FAILED.
   */
  reconcileLegacyLoans: () => post("/accounting/legacy-loans/reconcile"),

  /**
   * Reads the durable reconciliation job for the current organization.
   */
  reconcileLegacyLoansStatus: (jobId: number) =>
    get(`/accounting/legacy-loans/reconcile/${jobId}`),

  /**
   * Lists reconciliation history for the current organization.
   */
  reconciliationJobs: () => get("/accounting/legacy-loans/reconcile/jobs"),
};

/**
 * ============================================================
 * BANK ACCOUNT API
 * ============================================================
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
 * ============================================================
 * CONTACT MESSAGE API
 * ============================================================
 */

export const contactMessageApi = {
  list: () => get("/contact-messages"),

  unreadCount: () => get("/contact-messages/unread-count"),

  markRead: (id: number) => post(`/contact-messages/${id}/read`),

  delete: (id: number) => del(`/contact-messages/${id}`),
};

/**
 * ============================================================
 * PUBLIC API
 * ============================================================
 */

export const publicApi = {
  contact: (data: {
    tenantSlug: string;
    name: string;
    email?: string;
    phone?: string;
    subject?: string;
    message: string;
  }) => post("/public/contact", data),

  getTenant: (slug: string) =>
    get(`/public/tenant/${encodeURIComponent(slug)}`),

  getProducts: (slug: string) =>
    get(`/public/tenant/${encodeURIComponent(slug)}/products`),

  apply: (data: unknown) => post("/public/loan-application", data),

  trackApplication: (reference: string, phone: string) =>
    get(
      `/public/applications/${encodeURIComponent(
        reference.trim(),
      )}/status?phone=${encodeURIComponent(phone.trim())}`,
    ),

  getBorrowerProfile: (reference: string, phone: string) =>
    get(
      `/public/applications/${encodeURIComponent(reference.trim())}/profile?phone=${encodeURIComponent(phone.trim())}`,
    ),

  updateBorrowerProfile: (
    reference: string,
    phone: string,
    profile: Record<string, string>,
  ) =>
    put(
      `/public/applications/${encodeURIComponent(reference.trim())}/profile?phone=${encodeURIComponent(phone.trim())}`,
      profile,
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
    idempotencyKey?: string,
  ) =>
    post(
      `/public/applications/${encodeURIComponent(
        reference.trim(),
      )}/payments/initiate?phone=${encodeURIComponent(phone.trim())}`,
      data,
      idempotencyKey
        ? {
            headers: {
              "Idempotency-Key": idempotencyKey,
            },
          }
        : undefined,
    ),

  trackComments: (reference: string, phone: string) =>
    get(
      `/public/applications/${encodeURIComponent(
        reference.trim(),
      )}/comments?phone=${encodeURIComponent(phone.trim())}`,
    ),

  listDocuments: (reference: string, phone: string) =>
    get(
      `/public/applications/${encodeURIComponent(
        reference.trim(),
      )}/documents?phone=${encodeURIComponent(phone.trim())}`,
    ),

  /**
   * Legacy same-origin URL builder. Borrower preview/download handlers should
   * use fetchDocumentContent so binary signatures and HTML error pages are checked.
   */
  documentContentUrl: (
    reference: string,
    phone: string,
    fileId: number,
    mode: "preview" | "download",
  ) => {
    if (!Number.isSafeInteger(fileId) || fileId <= 0) {
      throw new Error("Invalid document ID.");
    }

    return `/api/public/applications/${encodeURIComponent(
      reference.trim().toUpperCase(),
    )}/documents/${fileId}/${mode}?phone=${encodeURIComponent(phone.trim())}`;
  },

  /**
   * Fetch document bytes through Axios rather than navigating directly to the
   * endpoint. This lets the portal reject proxy/login HTML pages and JSON
   * errors before presenting them as borrower documents.
   */
  fetchDocumentContent: async (
    reference: string,
    phone: string,
    fileId: number,
    mode: "preview" | "download",
  ): Promise<Blob> => {
    if (!Number.isSafeInteger(fileId) || fileId <= 0) {
      throw new Error("Invalid document ID.");
    }
    const url = `/public/applications/${encodeURIComponent(
      reference.trim().toUpperCase(),
    )}/documents/${fileId}/${mode}?phone=${encodeURIComponent(phone.trim())}`;
    try {
      const response = await API.get<Blob>(url, {
        responseType: "blob",
        headers: {
          Accept: "application/pdf, image/jpeg, image/png, image/webp",
        },
      });
      const blob = response.data;
      const type = String(response.headers["content-type"] || blob.type || "")
        .split(";", 1)[0]
        .trim()
        .toLowerCase();
      const signature = await blob
        .slice(0, 32)
        .text()
        .catch(() => "");
      if (
        type === "text/html" ||
        type === "application/json" ||
        signature.trimStart().toLowerCase().startsWith("<!doctype html") ||
        signature.trimStart().toLowerCase().startsWith("<html") ||
        signature.trimStart().startsWith("{")
      ) {
        throw new Error(
          "The server returned an error page instead of the document. Please retry; if the problem continues, contact support.",
        );
      }
      if (blob.size === 0) {
        throw new Error(
          "The document has no stored content. Please upload it again.",
        );
      }
      const bytes = new Uint8Array(await blob.slice(0, 16).arrayBuffer());
      const isPdf =
        bytes.length >= 4 &&
        bytes[0] === 0x25 &&
        bytes[1] === 0x50 &&
        bytes[2] === 0x44 &&
        bytes[3] === 0x46;
      const isJpeg =
        bytes.length >= 3 &&
        bytes[0] === 0xff &&
        bytes[1] === 0xd8 &&
        bytes[2] === 0xff;
      const isPng =
        bytes.length >= 8 &&
        bytes[0] === 0x89 &&
        bytes[1] === 0x50 &&
        bytes[2] === 0x4e &&
        bytes[3] === 0x47;
      const isWebp =
        bytes.length >= 12 &&
        bytes[0] === 0x52 &&
        bytes[1] === 0x49 &&
        bytes[2] === 0x46 &&
        bytes[8] === 0x57 &&
        bytes[9] === 0x45 &&
        bytes[10] === 0x42 &&
        bytes[11] === 0x50;
      if (!isPdf && !isJpeg && !isPng && !isWebp) {
        throw new Error(
          "The downloaded response is not a supported PDF or image. Please retry or contact support.",
        );
      }
      const safeType = isPdf
        ? "application/pdf"
        : isJpeg
          ? "image/jpeg"
          : isPng
            ? "image/png"
            : "image/webp";
      return new Blob([blob], { type: safeType });
    } catch (error: unknown) {
      const axiosError = error as AxiosError<unknown>;
      const payload = axiosError.response?.data;
      if (typeof Blob !== "undefined" && payload instanceof Blob) {
        const text = await payload
          .slice(0, 1000)
          .text()
          .catch(() => "");
        if (
          isHtmlErrorPayload(text) ||
          text.trimStart().startsWith("{") ||
          text.trimStart().startsWith("[")
        ) {
          throw new Error(
            "The document service returned an error page instead of the file. Please retry; if the problem continues, contact support.",
          );
        }
      }
      if (error instanceof Error && error.message) throw error;
      throw new Error("The document could not be retrieved. Please retry.");
    }
  },

  /**
   * Get the document requirements for a submitted public
   * loan application.
   *
   * The backend determines the required documents from the
   * loan product/application rather than the frontend using
   * a hard-coded document list.
   */
  documentRequirements: (reference: string, phone: string) =>
    get(
      `/public/applications/${encodeURIComponent(
        reference.trim(),
      )}/documents/requirements?phone=${encodeURIComponent(phone.trim())}`,
    ),

  downloadDocument: (
    reference: string,
    phone: string,
    doc: "agreement" | "schedule" | "receipt",
  ) =>
    API.get(
      `/public/applications/${encodeURIComponent(
        reference.trim(),
      )}/documents/${doc}.pdf?phone=${encodeURIComponent(phone.trim())}`,
      {
        responseType: "blob",
        headers: {
          Accept: "application/pdf, */*",
        },
      },
    ),

  deleteDocument: (reference: string, phone: string, fileId: number) =>
    del(
      `/public/applications/${encodeURIComponent(
        reference.trim(),
      )}/documents/${fileId}?phone=${encodeURIComponent(phone.trim())}`,
    ),

  replaceDocument: (
    reference: string,
    phone: string,
    fileId: number,
    file: File | Blob,
    fileName?: string,
  ) => {
    const form = new FormData();

    form.append("phone", phone.trim());

    const resolvedFileName =
      fileName ||
      ("name" in file && typeof file.name === "string" && file.name.trim()
        ? file.name
        : "replacement.jpg");

    if (file.size <= 0) {
      throw new Error(
        "The selected replacement document is empty. Please choose another file.",
      );
    }

    const uploadFile =
      file instanceof File
        ? file
        : new File([file], resolvedFileName, {
            type: file.type || "application/octet-stream",
          });

    form.append("file", uploadFile);

    return API.post(
      `/public/applications/${encodeURIComponent(
        reference.trim(),
      )}/documents/${fileId}/replace`,
      form,
    ).then((response) => unwrap(response.data));
  },

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

    const resolvedFileName =
      fileName ||
      ("name" in file && typeof file.name === "string" && file.name.trim()
        ? file.name
        : "upload.jpg");

    if (file.size <= 0) {
      throw new Error(
        "The selected document is empty. Please choose another file.",
      );
    }

    const uploadFile =
      file instanceof File
        ? file
        : new File([file], resolvedFileName, {
            type: file.type || "application/octet-stream",
          });

    form.append("file", uploadFile);

    return API.post(
      `/public/applications/${encodeURIComponent(reference.trim())}/documents`,
      form,
    ).then((response) => unwrap(response.data));
  },
};

/**
 * ============================================================
 * IMPORT API
 * ============================================================
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
      timeout: 120000,
    }).then((response) => unwrap(response.data));
  },

  commit: (file: File) => {
    const form = new FormData();

    form.append("file", file);

    return API.post("/import/legacy-loans/commit", form).then((response) =>
      unwrap(response.data),
    );
  },

  /**
   * Lists import batches for the current organization.
   */
  batches: () => get("/import/legacy-loans/batches"),

  /**
   * Loads one import batch by ID.
   */
  batch: (batchId: number) => {
    if (!Number.isInteger(batchId) || batchId <= 0) {
      return Promise.reject(new Error("Invalid import batch ID."));
    }

    return get(`/import/legacy-loans/batches/${batchId}`);
  },
};

/**
 * ============================================================
 * REGULATORY / BNR API
 * ============================================================
 */

export const regulatoryApi = {
  bnrSummary: (params?: {
    branchId?: number;
    period?: string;
    from?: string;
    to?: string;
  }) =>
    get(
      `/regulatory/bnr/summary${
        params
          ? `?${new URLSearchParams(
              Object.entries(params)
                .filter(
                  ([, value]) =>
                    value !== undefined && value !== null && value !== "",
                )
                .map(([key, value]) => [key, String(value)]),
            ).toString()}`
          : ""
      }`,
    ),

  bnrFinancialStatement: (params?: {
    branchId?: number;
    period?: string;
    from?: string;
    to?: string;
  }) =>
    get(
      `/regulatory/bnr/financial-statement${
        params
          ? `?${new URLSearchParams(
              Object.entries(params)
                .filter(
                  ([, value]) =>
                    value !== undefined && value !== null && value !== "",
                )
                .map(([key, value]) => [key, String(value)]),
            ).toString()}`
          : ""
      }`,
    ),

  bnrByLoanType: (params?: {
    branchId?: number;
    period?: string;
    from?: string;
    to?: string;
  }) =>
    get(
      `/regulatory/bnr/by-loan-type${
        params
          ? `?${new URLSearchParams(
              Object.entries(params)
                .filter(
                  ([, value]) =>
                    value !== undefined && value !== null && value !== "",
                )
                .map(([key, value]) => [key, String(value)]),
            ).toString()}`
          : ""
      }`,
    ),

  bnrByBranch: (params?: { period?: string; from?: string; to?: string }) =>
    get(
      `/regulatory/bnr/by-branch${
        params
          ? `?${new URLSearchParams(
              Object.entries(params)
                .filter(
                  ([, value]) =>
                    value !== undefined && value !== null && value !== "",
                )
                .map(([key, value]) => [key, String(value)]),
            ).toString()}`
          : ""
      }`,
    ),

  bnrByGender: (params?: {
    branchId?: number;
    period?: string;
    from?: string;
    to?: string;
  }) =>
    get(
      `/regulatory/bnr/by-gender${
        params
          ? `?${new URLSearchParams(
              Object.entries(params)
                .filter(
                  ([, value]) =>
                    value !== undefined && value !== null && value !== "",
                )
                .map(([key, value]) => [key, String(value)]),
            ).toString()}`
          : ""
      }`,
    ),
};

function getExportAcceptHeader(format: string): string {
  switch (format.toLowerCase()) {
    case "pdf":
      return "application/pdf";

    case "csv":
      return "text/csv";

    case "xlsx":
      return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    default:
      return "*/*";
  }
}

export default API;

export { API };
