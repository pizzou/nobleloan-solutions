import { get, post } from "./api";

/**
 * ============================================================
 * API NOTIFICATION
 * ============================================================
 */

export interface AppNotification {
  id: number;

  title?: string;
  message?: string;
  type?: string;
  link?: string;

  read?: boolean;
  createdAt?: string;

  organizationId?: number;
  loanId?: number;
  paymentId?: number;

  loanReference?: string;

  borrowerId?: number;
  borrowerName?: string;

  amount?: number | string;
  principalPaid?: number | string;
  interestPaid?: number | string;
  penaltyPaid?: number | string;

  outstandingBalance?: number | string;

  totalInterestPaid?: number | string;
  totalInterestDue?: number | string;
  remainingInterest?: number | string;

  totalPrincipalPaid?: number | string;

  totalPenalty?: number | string;
  totalPenaltyPaid?: number | string;
  remainingPenalty?: number | string;

  currency?: string;

  paymentMethod?: string;
  channel?: string;

  transactionId?: string;
  paymentReference?: string;

  paymentStatus?: string;
  loanStatus?: string;

  paymentDate?: string;
  paymentTimestamp?: string;

  severity?: string;
  priority?: string;

  realtime?: boolean;
  receivedAt?: string;
}

/**
 * ============================================================
 * DISPLAY NOTIFICATION
 * ============================================================
 */

export interface DisplayNotification {
  id: string;

  type: string;

  title: string;

  message: string;

  link?: string;

  organizationId?: number;

  loanId?: number;

  paymentId?: number;

  loanReference?: string;

  borrowerId?: number;

  borrowerName?: string;

  amount?: number | string;

  principalPaid?: number | string;

  interestPaid?: number | string;

  penaltyPaid?: number | string;

  outstandingBalance?: number | string;

  totalInterestPaid?: number | string;

  totalInterestDue?: number | string;

  remainingInterest?: number | string;

  totalPrincipalPaid?: number | string;

  totalPenalty?: number | string;

  totalPenaltyPaid?: number | string;

  remainingPenalty?: number | string;

  currency?: string;

  paymentMethod?: string;

  channel?: string;

  transactionId?: string;

  paymentReference?: string;

  paymentStatus?: string;

  loanStatus?: string;

  paymentDate?: string;

  paymentTimestamp?: string;

  createdAt: string;

  receivedAt: string;

  read: boolean;

  severity: string;

  priority: string;

  realtime: boolean;
}

/**
 * ============================================================
 * INTERNAL CONSTANTS
 * ============================================================
 *
 * Notification unread count is non-critical.
 *
 * The dashboard must NEVER wait 20 seconds for this request.
 *
 * Your normal API client currently has a much longer timeout,
 * so this service applies a local upper limit.
 */
const UNREAD_COUNT_TIMEOUT_MS = 3500;

/**
 * ============================================================
 * SAFE NUMBER
 * ============================================================
 */

function optionalNumber(
  value: number | string | null | undefined,
): number | undefined {
  if (value === undefined || value === null || value === "") {
    return undefined;
  }

  const parsed = typeof value === "number" ? value : Number(value);

  return Number.isFinite(parsed) ? parsed : undefined;
}

/**
 * ============================================================
 * SAFE STRING
 * ============================================================
 */

function optionalString(value: unknown): string | undefined {
  if (value === undefined || value === null) {
    return undefined;
  }

  const text = String(value).trim();

  return text.length > 0 ? text : undefined;
}

/**
 * ============================================================
 * TYPE NORMALIZATION
 * ============================================================
 */

function normalizeType(type?: string): string {
  const normalized = optionalString(type)?.toUpperCase();

  if (!normalized) {
    return "GENERAL";
  }

  return normalized;
}

/**
 * ============================================================
 * SEVERITY NORMALIZATION
 * ============================================================
 */

function normalizeSeverity(notification: AppNotification): string {
  const explicit = optionalString(notification.severity)?.toUpperCase();

  if (explicit) {
    return explicit;
  }

  const type = normalizeType(notification.type);

  switch (type) {
    case "PAYMENT":
    case "PAYMENT_RECEIVED":
    case "PAYMENT_SUCCESS":
      return "SUCCESS";

    case "LOAN_OVERDUE":
    case "OVERDUE":
    case "PAYMENT_FAILED":
    case "PAYMENT_REJECTED":
      return "ERROR";

    case "LOAN_APPROVED":
    case "APPROVAL":
      return "SUCCESS";

    case "LOAN_REJECTED":
      return "ERROR";

    case "WARNING":
    case "SYSTEM_WARNING":
      return "WARNING";

    default:
      return "INFO";
  }
}

/**
 * ============================================================
 * PRIORITY NORMALIZATION
 * ============================================================
 */

function normalizePriority(notification: AppNotification): string {
  const explicit = optionalString(notification.priority)?.toUpperCase();

  if (explicit) {
    return explicit;
  }

  const type = normalizeType(notification.type);

  switch (type) {
    case "PAYMENT_FAILED":
    case "PAYMENT_REJECTED":
    case "LOAN_OVERDUE":
    case "OVERDUE":
      return "HIGH";

    case "PAYMENT_RECEIVED":
    case "PAYMENT_SUCCESS":
    case "LOAN_APPROVED":
      return "MEDIUM";

    default:
      return "LOW";
  }
}

/**
 * ============================================================
 * REALTIME NORMALIZATION
 * ============================================================
 */

function normalizeRealtime(notification: AppNotification): boolean {
  return Boolean(notification.realtime);
}

/**
 * ============================================================
 * CREATED DATE
 * ============================================================
 */

function resolveCreatedAt(notification: AppNotification): string {
  return (
    optionalString(notification.createdAt) ??
    optionalString(notification.paymentTimestamp) ??
    new Date().toISOString()
  );
}

/**
 * ============================================================
 * RECEIVED DATE
 * ============================================================
 */

function resolveReceivedAt(notification: AppNotification): string {
  return (
    optionalString(notification.receivedAt) ??
    optionalString(notification.paymentTimestamp) ??
    optionalString(notification.createdAt) ??
    new Date().toISOString()
  );
}

/**
 * ============================================================
 * NOTIFICATION ID
 * ============================================================
 */

function resolveNotificationId(notification: AppNotification): string {
  if (notification.id !== undefined && notification.id !== null) {
    return String(notification.id);
  }

  if (notification.paymentId !== undefined && notification.paymentId !== null) {
    return `payment-${notification.paymentId}`;
  }

  if (notification.transactionId) {
    return `transaction-${notification.transactionId}`;
  }

  return `notification-${Date.now()}`;
}

/**
 * ============================================================
 * PAYMENT TITLE
 * ============================================================
 */

function resolveTitle(notification: AppNotification): string {
  if (optionalString(notification.title)) {
    return notification.title!.trim();
  }

  const type = normalizeType(notification.type);

  switch (type) {
    case "PAYMENT":
    case "PAYMENT_RECEIVED":
    case "PAYMENT_SUCCESS":
      return "Payment Received";

    case "PAYMENT_FAILED":
      return "Payment Failed";

    case "PAYMENT_REJECTED":
      return "Payment Rejected";

    case "LOAN_APPROVED":
      return "Loan Approved";

    case "LOAN_REJECTED":
      return "Loan Rejected";

    case "LOAN_OVERDUE":
    case "OVERDUE":
      return "Loan Overdue";

    default:
      return "Notification";
  }
}

/**
 * ============================================================
 * PAYMENT MESSAGE
 * ============================================================
 */

function resolveMessage(notification: AppNotification): string {
  if (optionalString(notification.message)) {
    return notification.message!.trim();
  }

  const type = normalizeType(notification.type);

  if (
    type === "PAYMENT" ||
    type === "PAYMENT_RECEIVED" ||
    type === "PAYMENT_SUCCESS"
  ) {
    const currency = notification.currency || "RWF";

    const amount = notification.amount ?? 0;

    const borrower = notification.borrowerName
      ? notification.borrowerName
      : notification.borrowerId
        ? `Borrower #${notification.borrowerId}`
        : "Borrower";

    const loan = notification.loanReference
      ? notification.loanReference
      : notification.loanId
        ? `Loan #${notification.loanId}`
        : "loan";

    return `${borrower} paid ${currency} ${amount} for ${loan}.`;
  }

  return "You have a new notification.";
}

/**
 * ============================================================
 * NORMALIZE NOTIFICATION
 * ============================================================
 */

export function normalizeNotification(
  notification: AppNotification,
): DisplayNotification {
  const createdAt = resolveCreatedAt(notification);

  const receivedAt = resolveReceivedAt(notification);

  return {
    id: resolveNotificationId(notification),

    type: normalizeType(notification.type),

    title: resolveTitle(notification),

    message: resolveMessage(notification),

    link: optionalString(notification.link),

    organizationId: optionalNumber(notification.organizationId),

    loanId: optionalNumber(notification.loanId),

    paymentId: optionalNumber(notification.paymentId),

    loanReference: optionalString(notification.loanReference),

    borrowerId: optionalNumber(notification.borrowerId),

    borrowerName: optionalString(notification.borrowerName),

    amount: notification.amount,

    principalPaid: notification.principalPaid,

    interestPaid: notification.interestPaid,

    penaltyPaid: notification.penaltyPaid,

    outstandingBalance: notification.outstandingBalance,

    totalInterestPaid: notification.totalInterestPaid,

    totalInterestDue: notification.totalInterestDue,

    remainingInterest: notification.remainingInterest,

    totalPrincipalPaid: notification.totalPrincipalPaid,

    totalPenalty: notification.totalPenalty,

    totalPenaltyPaid: notification.totalPenaltyPaid,

    remainingPenalty: notification.remainingPenalty,

    currency: optionalString(notification.currency) ?? "RWF",

    paymentMethod: optionalString(notification.paymentMethod),

    channel: optionalString(notification.channel),

    transactionId: optionalString(notification.transactionId),

    paymentReference: optionalString(notification.paymentReference),

    paymentStatus: optionalString(notification.paymentStatus),

    loanStatus: optionalString(notification.loanStatus),

    paymentDate: optionalString(notification.paymentDate),

    paymentTimestamp: optionalString(notification.paymentTimestamp),

    createdAt,

    receivedAt,

    read: Boolean(notification.read),

    severity: normalizeSeverity(notification),

    priority: normalizePriority(notification),

    realtime: normalizeRealtime(notification),
  };
}

/**
 * ============================================================
 * API RESPONSE NORMALIZER
 * ============================================================
 */

function extractNotificationArray(response: unknown): AppNotification[] {
  if (Array.isArray(response)) {
    return response as AppNotification[];
  }

  if (response && typeof response === "object") {
    const object = response as {
      data?: unknown;
      content?: unknown;
      notifications?: unknown;
      items?: unknown;
    };

    if (Array.isArray(object.data)) {
      return object.data as AppNotification[];
    }

    if (Array.isArray(object.content)) {
      return object.content as AppNotification[];
    }

    if (Array.isArray(object.notifications)) {
      return object.notifications as AppNotification[];
    }

    if (Array.isArray(object.items)) {
      return object.items as AppNotification[];
    }
  }

  return [];
}

/**
 * ============================================================
 * GET MY NOTIFICATIONS
 * ============================================================
 */

export const getMyNotifications = async (
  organizationId?: number,
): Promise<DisplayNotification[]> => {
  let endpoint = "/notifications";

  if (organizationId !== undefined && organizationId !== null) {
    endpoint += `?organizationId=${encodeURIComponent(organizationId)}`;
  }

  const response = await get(endpoint);

  const notifications = extractNotificationArray(response);

  return notifications.map(normalizeNotification);
};

/**
 * ============================================================
 * GET UNREAD COUNT
 * ============================================================
 *
 * IMPORTANT:
 *
 * This endpoint is NOT critical to dashboard operation.
 *
 * If the backend is slow, sleeping, temporarily unavailable,
 * or the request exceeds the local timeout, the dashboard
 * receives { count: 0 } instead of waiting for the normal
 * 20-second API timeout.
 *
 * This prevents notification availability from making the
 * entire dashboard feel slow.
 */
export const getUnreadCount = async (): Promise<{
  count: number;
}> => {
  let timeoutId: ReturnType<typeof setTimeout> | undefined;

  try {
    const request = get("/notifications/unread-count");

    const timeout = new Promise<never>((_, reject) => {
      timeoutId = setTimeout(() => {
        reject(new Error("Unread notification count request timed out."));
      }, UNREAD_COUNT_TIMEOUT_MS);
    });

    const response = await Promise.race([request, timeout]);

    if (response && typeof response === "object") {
      const result = response as {
        count?: unknown;
      };

      const count = Number(result.count ?? 0);

      return {
        count: Number.isFinite(count) ? Math.max(0, count) : 0,
      };
    }

    return {
      count: 0,
    };
  } catch (error) {
    /**
     * Notification count is deliberately
     * fail-safe.
     *
     * A notification failure must never
     * break the dashboard.
     */
    console.warn(
      "[NOTIFICATIONS] Unread count unavailable:",
      error instanceof Error ? error.message : error,
    );

    return {
      count: 0,
    };
  } finally {
    if (timeoutId) {
      clearTimeout(timeoutId);
    }
  }
};

/**
 * ============================================================
 * MARK ONE NOTIFICATION AS READ
 * ============================================================
 */

export const markNotificationRead = async (
  id: number | string,
): Promise<unknown> => {
  const numericId = Number(id);

  if (!Number.isFinite(numericId)) {
    throw new Error(`Invalid notification ID: ${id}`);
  }

  return post(`/notifications/${numericId}/read`);
};

/**
 * ============================================================
 * ALIAS EXPECTED BY YOUR PAGE
 * ============================================================
 */

export const markNotificationAsRead = markNotificationRead;

/**
 * ============================================================
 * MARK ALL NOTIFICATIONS AS READ
 * ============================================================
 */

export const markAllNotificationsRead = async (): Promise<unknown> => {
  return post("/notifications/read-all");
};

/**
 * ============================================================
 * ALIAS EXPECTED BY YOUR PAGE
 * ============================================================
 */

export const markAllNotificationsAsRead = markAllNotificationsRead;

/**
 * ============================================================
 * NORMALIZE REALTIME PAYMENT NOTIFICATION
 * ============================================================
 */

export function normalizeRealtimePaymentNotification(
  notification: AppNotification,
): DisplayNotification {
  return normalizeNotification({
    ...notification,

    type: notification.type || "PAYMENT_RECEIVED",

    title: notification.title || "Payment Received",

    realtime: true,

    read: false,

    receivedAt:
      notification.receivedAt ||
      notification.paymentTimestamp ||
      new Date().toISOString(),

    createdAt:
      notification.createdAt ||
      notification.paymentTimestamp ||
      new Date().toISOString(),

    message:
      notification.message ||
      resolveMessage({
        ...notification,

        type: notification.type || "PAYMENT_RECEIVED",
      }),
  });
}

/**
 * ============================================================
 * MERGE NOTIFICATIONS
 * ============================================================
 */

export function mergeNotifications(
  current: DisplayNotification[],
  incoming: DisplayNotification[],
): DisplayNotification[] {
  const map = new Map<string, DisplayNotification>();

  for (const notification of current) {
    map.set(notification.id, notification);
  }

  for (const notification of incoming) {
    const existing = map.get(notification.id);

    if (existing) {
      map.set(notification.id, {
        ...existing,
        ...notification,

        read: existing.read || notification.read,
      });
    } else {
      map.set(notification.id, notification);
    }
  }

  return Array.from(map.values()).sort((first, second) => {
    const firstDate = new Date(first.receivedAt || first.createdAt).getTime();

    const secondDate = new Date(
      second.receivedAt || second.createdAt,
    ).getTime();

    return secondDate - firstDate;
  });
}

/**
 * ============================================================
 * DEFAULT EXPORT
 * ============================================================
 */

const notificationsService = {
  getMyNotifications,

  getUnreadCount,

  markNotificationRead,

  markNotificationAsRead,

  markAllNotificationsRead,

  markAllNotificationsAsRead,

  normalizeNotification,

  normalizeRealtimePaymentNotification,

  mergeNotifications,
};

export default notificationsService;
