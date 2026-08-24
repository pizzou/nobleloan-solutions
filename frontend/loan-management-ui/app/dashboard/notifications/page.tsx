"use client";

import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";

import {
  connectToPaymentNotifications,
  disconnectFromPaymentNotifications,
  PaymentNotification,
} from "@/services/realtimeNotificationService";

import {
  DisplayNotification,
  getMyNotifications,
  markAllNotificationsAsRead,
  markNotificationAsRead,
  normalizeNotification,
} from "@/services/notificationsService";

type FilterType =
  | "ALL"
  | "UNREAD"
  | "PAYMENT"
  | "LOAN"
  | "APPROVAL"
  | "REMINDER"
  | "OVERDUE"
  | "ALERT";

function getOrganizationId(): number {
  if (typeof window === "undefined") {
    return 1;
  }

  const keys = [
    "organizationId",
    "organization_id",
    "orgId",
    "tenantOrganizationId",
  ];

  for (const key of keys) {
    const value = window.localStorage.getItem(key);

    if (value) {
      const parsed = Number(value);

      if (Number.isFinite(parsed) && parsed > 0) {
        return parsed;
      }
    }
  }

  return 1;
}

function toNumber(value: number | string | undefined | null): number {
  if (value === undefined || value === null || value === "") {
    return 0;
  }

  const number = typeof value === "number" ? value : Number(value);

  return Number.isFinite(number) ? number : 0;
}

function formatCurrency(
  value: number | string | undefined | null,
  currency = "RWF",
): string {
  const amount = toNumber(value);

  try {
    return new Intl.NumberFormat("en-RW", {
      style: "currency",
      currency: currency || "RWF",
      maximumFractionDigits: 0,
    }).format(amount);
  } catch {
    return `${currency || "RWF"} ${amount.toLocaleString()}`;
  }
}

function formatDate(value?: string): string {
  if (!value) {
    return "—";
  }

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat("en-RW", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(date);
}

function relativeTime(value?: string): string {
  if (!value) {
    return "";
  }

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return "";
  }

  const seconds = Math.floor((Date.now() - date.getTime()) / 1000);

  if (seconds < 10) {
    return "Just now";
  }

  if (seconds < 60) {
    return `${seconds}s ago`;
  }

  const minutes = Math.floor(seconds / 60);

  if (minutes < 60) {
    return `${minutes}m ago`;
  }

  const hours = Math.floor(minutes / 60);

  if (hours < 24) {
    return `${hours}h ago`;
  }

  const days = Math.floor(hours / 24);

  if (days < 30) {
    return `${days}d ago`;
  }

  return formatDate(value);
}

function getType(notification: DisplayNotification): string {
  const type = notification.type?.toUpperCase();

  if (type === "PAYMENT_RECEIVED" || type === "PAYMENT") {
    return "PAYMENT";
  }

  if (type?.includes("APPROV")) {
    return "APPROVAL";
  }

  if (type?.includes("REMINDER")) {
    return "REMINDER";
  }

  if (type?.includes("OVERDUE")) {
    return "OVERDUE";
  }

  if (type?.includes("LOAN")) {
    return "LOAN";
  }

  if (
    type?.includes("SECURITY") ||
    type?.includes("ALERT") ||
    type?.includes("DANGER")
  ) {
    return "ALERT";
  }

  return type || "INFO";
}

function getIcon(type: string): string {
  switch (type) {
    case "PAYMENT":
      return "💳";

    case "APPROVAL":
      return "✓";

    case "LOAN":
      return "▣";

    case "REMINDER":
      return "⏰";

    case "OVERDUE":
      return "⚠";

    case "ALERT":
      return "🔴";

    default:
      return "●";
  }
}

function getSeverity(notification: DisplayNotification): string {
  const explicit = notification.severity?.toLowerCase();

  if (explicit) {
    return explicit;
  }

  const type = getType(notification);

  if (type === "OVERDUE" || type === "ALERT") {
    return "danger";
  }

  if (type === "REMINDER") {
    return "warning";
  }

  if (type === "PAYMENT" || type === "APPROVAL") {
    return "success";
  }

  return "info";
}

function paymentToNotification(
  payment: PaymentNotification,
): DisplayNotification {
  const timestamp =
    payment.paymentTimestamp || payment.paymentDate || new Date().toISOString();

  const borrower =
    payment.borrowerName ||
    (payment.borrowerId ? `Borrower #${payment.borrowerId}` : "Borrower");

  const loan = payment.loanReference || `Loan #${payment.loanId}`;

  const amount = formatCurrency(payment.amount, payment.currency);

  return {
    id: `realtime-payment-${payment.paymentId}-${payment.transactionId || timestamp}`,

    type: "PAYMENT",

    title: payment.title || "Payment Received",

    message: payment.message || `${borrower} paid ${amount} for ${loan}.`,

    organizationId: Number(payment.organizationId),

    loanId: Number(payment.loanId),

    paymentId: Number(payment.paymentId),

    loanReference: payment.loanReference,

    borrowerId:
      payment.borrowerId !== undefined && payment.borrowerId !== null
        ? Number(payment.borrowerId)
        : undefined,

    borrowerName: payment.borrowerName,

    amount: payment.amount,

    principalPaid: payment.principalPaid,

    interestPaid: payment.interestPaid,

    penaltyPaid: payment.penaltyPaid,

    outstandingBalance: payment.outstandingBalance,

    currency: payment.currency || "RWF",

    paymentMethod: payment.paymentMethod,

    channel: payment.channel,

    transactionId: payment.transactionId,

    paymentReference: payment.paymentReference,

    paymentStatus: payment.paymentStatus,

    loanStatus: payment.loanStatus,

    paymentDate: payment.paymentDate,

    paymentTimestamp: payment.paymentTimestamp || timestamp,

    createdAt: payment.paymentTimestamp || timestamp,

    receivedAt: timestamp,

    severity: "SUCCESS",

    priority: "HIGH",

    read: false,

    realtime: true,
  };
}

function sameNotification(
  a: DisplayNotification,
  b: DisplayNotification,
): boolean {
  if (a.paymentId && b.paymentId && a.paymentId === b.paymentId) {
    return true;
  }

  if (a.id && b.id && a.id === b.id) {
    return true;
  }

  if (
    a.transactionId &&
    b.transactionId &&
    a.transactionId === b.transactionId
  ) {
    return true;
  }

  return false;
}

export default function Page() {
  const organizationId = useMemo(() => getOrganizationId(), []);

  const [notifications, setNotifications] = useState<DisplayNotification[]>([]);

  const [loading, setLoading] = useState(true);

  const [refreshing, setRefreshing] = useState(false);

  const [loadError, setLoadError] = useState<string | null>(null);

  const [realtimeConnected, setRealtimeConnected] = useState(false);

  const [realtimeError, setRealtimeError] = useState<string | null>(null);

  const [filter, setFilter] = useState<FilterType>("ALL");

  const [search, setSearch] = useState("");

  const [selected, setSelected] = useState<DisplayNotification | null>(null);

  const [markingAll, setMarkingAll] = useState(false);

  const mounted = useRef(true);

  const reconnectTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const connectGeneration = useRef(0);

  const loadNotifications = useCallback(
    async (silent = false) => {
      try {
        if (!silent) {
          setLoading(true);
        } else {
          setRefreshing(true);
        }

        setLoadError(null);

        const data = await getMyNotifications(organizationId);

        if (!mounted.current) {
          return;
        }

        setNotifications((current) => {
          const realtime = current.filter(
            (notification) => notification.realtime,
          );

          const merged = [...data, ...realtime];

          const unique = merged.filter(
            (notification, index, array) =>
              array.findIndex((candidate) =>
                sameNotification(candidate, notification),
              ) === index,
          );

          return unique.sort(
            (a, b) =>
              new Date(b.createdAt || b.paymentTimestamp || 0).getTime() -
              new Date(a.createdAt || a.paymentTimestamp || 0).getTime(),
          );
        });
      } catch (error) {
        console.error("[NOTIFICATIONS] Failed to load notifications:", error);

        if (mounted.current) {
          setLoadError(
            error instanceof Error
              ? error.message
              : "Unable to load notifications.",
          );
        }
      } finally {
        if (mounted.current) {
          setLoading(false);

          setRefreshing(false);
        }
      }
    },
    [organizationId],
  );

  const handlePaymentReceived = useCallback(
    (payment: PaymentNotification) => {
      if (Number(payment.organizationId) !== organizationId) {
        return;
      }

      const notification = paymentToNotification(payment);

      setNotifications((current) => {
        const exists = current.some((item) =>
          sameNotification(item, notification),
        );

        if (exists) {
          return current;
        }

        return [notification, ...current];
      });
    },
    [organizationId],
  );

  const connectRealtime = useCallback(() => {
    const generation = ++connectGeneration.current;

    try {
      disconnectFromPaymentNotifications();

      setRealtimeError(null);

      connectToPaymentNotifications(organizationId, {
        onPaymentReceived: handlePaymentReceived,

        onConnected: () => {
          if (!mounted.current || generation !== connectGeneration.current) {
            return;
          }

          setRealtimeConnected(true);

          setRealtimeError(null);
        },

        onDisconnected: () => {
          if (!mounted.current || generation !== connectGeneration.current) {
            return;
          }

          setRealtimeConnected(false);
        },

        onError: (error) => {
          if (!mounted.current || generation !== connectGeneration.current) {
            return;
          }

          console.error("[REALTIME] Notification error:", error);

          setRealtimeConnected(false);

          setRealtimeError("Realtime connection unavailable.");
        },
      });
    } catch (error) {
      console.error("[REALTIME] Failed to connect:", error);

      setRealtimeConnected(false);

      setRealtimeError(
        error instanceof Error
          ? error.message
          : "Unable to connect to realtime notifications.",
      );
    }
  }, [organizationId, handlePaymentReceived]);

  useEffect(() => {
    mounted.current = true;
    const generationAtMount = connectGeneration.current;

    void loadNotifications();

    connectRealtime();

    return () => {
      mounted.current = false;

      connectGeneration.current = generationAtMount + 1;

      if (reconnectTimer.current) {
        clearTimeout(reconnectTimer.current);

        reconnectTimer.current = null;
      }

      disconnectFromPaymentNotifications();
    };
  }, [loadNotifications, connectRealtime]);

  useEffect(() => {
    if (realtimeConnected) {
      return;
    }

    if (reconnectTimer.current) {
      return;
    }

    reconnectTimer.current = setTimeout(() => {
      reconnectTimer.current = null;

      if (mounted.current) {
        connectRealtime();
      }
    }, 10000);

    return () => {
      if (reconnectTimer.current) {
        clearTimeout(reconnectTimer.current);

        reconnectTimer.current = null;
      }
    };
  }, [realtimeConnected, connectRealtime]);

  const unreadCount = useMemo(
    () => notifications.filter((notification) => !notification.read).length,
    [notifications],
  );

  const urgentCount = useMemo(
    () =>
      notifications.filter((notification) => {
        const severity = getSeverity(notification);

        return severity === "danger" || severity === "critical";
      }).length,
    [notifications],
  );

  const todayCount = useMemo(() => {
    const now = new Date();

    return notifications.filter((notification) => {
      const date = new Date(
        notification.createdAt || notification.paymentTimestamp || "",
      );

      return date.toDateString() === now.toDateString();
    }).length;
  }, [notifications]);

  const paymentCount = useMemo(
    () =>
      notifications.filter(
        (notification) => getType(notification) === "PAYMENT",
      ).length,
    [notifications],
  );

  const filteredNotifications = useMemo(() => {
    const normalizedSearch = search.trim().toLowerCase();

    return notifications.filter((notification) => {
      const type = getType(notification);

      let matchesFilter = true;

      switch (filter) {
        case "UNREAD":
          matchesFilter = !notification.read;
          break;

        case "PAYMENT":
          matchesFilter = type === "PAYMENT";
          break;

        case "LOAN":
          matchesFilter = type === "LOAN";
          break;

        case "APPROVAL":
          matchesFilter = type === "APPROVAL";
          break;

        case "REMINDER":
          matchesFilter = type === "REMINDER";
          break;

        case "OVERDUE":
          matchesFilter = type === "OVERDUE";
          break;

        case "ALERT":
          matchesFilter =
            type === "ALERT" || getSeverity(notification) === "danger";
          break;

        default:
          matchesFilter = true;
      }

      if (!matchesFilter) {
        return false;
      }

      if (!normalizedSearch) {
        return true;
      }

      const haystack = [
        notification.title,
        notification.message,
        notification.type,
        notification.loanReference,
        notification.borrowerName,
        notification.transactionId,
        notification.paymentReference,
        notification.paymentStatus,
        notification.loanStatus,
      ]
        .filter(Boolean)
        .join(" ")
        .toLowerCase();

      return haystack.includes(normalizedSearch);
    });
  }, [notifications, filter, search]);

  const markRead = useCallback(async (notification: DisplayNotification) => {
    if (notification.read) {
      return;
    }

    setNotifications((current) =>
      current.map((item) =>
        sameNotification(item, notification)
          ? {
              ...item,
              read: true,
            }
          : item,
      ),
    );

    if (notification.realtime) {
      return;
    }

    try {
      await markNotificationAsRead(notification.id);
    } catch (error) {
      console.error(
        "[NOTIFICATIONS] Failed to mark notification as read:",
        error,
      );
    }
  }, []);

  const markAllRead = useCallback(async () => {
    if (unreadCount === 0) {
      return;
    }

    setMarkingAll(true);

    setNotifications((current) =>
      current.map((item) => ({
        ...item,
        read: true,
      })),
    );

    try {
      await markAllNotificationsAsRead();
    } catch (error) {
      console.error("[NOTIFICATIONS] Failed to mark all as read:", error);
    } finally {
      if (mounted.current) {
        setMarkingAll(false);
      }
    }
  }, [unreadCount]);

  const clearLocal = useCallback(() => {
    setNotifications([]);

    setSelected(null);
  }, []);

  return (
    <main className="notification-shell">
      <style jsx global>{`
        * {
          box-sizing: border-box;
        }

        body {
          margin: 0;
          background: #f5f7fb;
        }
      `}</style>

      <style jsx>{`
        .notification-shell {
          min-height: 100vh;
          padding: 28px;
          background:
            radial-gradient(
              circle at top right,
              rgba(39, 116, 255, 0.07),
              transparent 30%
            ),
            #f5f7fb;
          color: #152238;
        }

        .container {
          width: 100%;
          max-width: 1480px;
          margin: 0 auto;
        }

        .hero {
          display: flex;
          justify-content: space-between;
          align-items: flex-start;
          gap: 24px;
          margin-bottom: 24px;
        }

        .eyebrow {
          display: inline-flex;
          align-items: center;
          gap: 7px;
          margin-bottom: 9px;
          color: #3775d6;
          font-size: 12px;
          font-weight: 800;
          text-transform: uppercase;
          letter-spacing: 0.08em;
        }

        .hero h1 {
          margin: 0;
          font-size: 34px;
          line-height: 1.15;
          letter-spacing: -0.04em;
          color: #101b2e;
        }

        .hero p {
          margin: 9px 0 0;
          color: #69758a;
          font-size: 14px;
        }

        .connection-card {
          display: inline-flex;
          align-items: center;
          gap: 10px;
          padding: 11px 15px;
          border: 1px solid #e1e7f0;
          border-radius: 12px;
          background: rgba(255, 255, 255, 0.88);
          box-shadow: 0 5px 20px rgba(24, 39, 75, 0.05);
          font-size: 13px;
          font-weight: 700;
        }

        .connection-dot {
          width: 9px;
          height: 9px;
          border-radius: 50%;
          background: #9aa5b5;
        }

        .connection-dot.live {
          background: #1fa463;
          box-shadow: 0 0 0 5px rgba(31, 164, 99, 0.12);
        }

        .connection-dot.error {
          background: #d83b32;
        }

        .stats {
          display: grid;
          grid-template-columns: repeat(4, minmax(0, 1fr));
          gap: 14px;
          margin-bottom: 20px;
        }

        .stat {
          min-height: 112px;
          padding: 18px;
          border: 1px solid #e4e9f1;
          border-radius: 15px;
          background: white;
          box-shadow: 0 4px 18px rgba(26, 42, 73, 0.045);
        }

        .stat-top {
          display: flex;
          align-items: center;
          justify-content: space-between;
          gap: 12px;
        }

        .stat-label {
          color: #778297;
          font-size: 12px;
          font-weight: 700;
        }

        .stat-icon {
          width: 34px;
          height: 34px;
          display: flex;
          align-items: center;
          justify-content: center;
          border-radius: 9px;
          background: #f0f4fa;
          font-size: 16px;
        }

        .stat-value {
          margin-top: 13px;
          font-size: 26px;
          font-weight: 800;
          letter-spacing: -0.03em;
        }

        .toolbar {
          display: flex;
          align-items: center;
          justify-content: space-between;
          gap: 15px;
          margin-bottom: 15px;
        }

        .search {
          flex: 1;
          position: relative;
        }

        .search input {
          width: 100%;
          height: 46px;
          border: 1px solid #dfe5ee;
          border-radius: 11px;
          padding: 0 16px 0 43px;
          outline: none;
          background: white;
          color: #1c2940;
          font-size: 14px;
          box-shadow: 0 3px 12px rgba(22, 35, 60, 0.035);
        }

        .search input:focus {
          border-color: #6c9bf1;
          box-shadow: 0 0 0 3px rgba(52, 112, 225, 0.1);
        }

        .search-icon {
          position: absolute;
          left: 15px;
          top: 50%;
          transform: translateY(-50%);
          color: #8994a6;
        }

        .actions {
          display: flex;
          gap: 8px;
        }

        .button {
          height: 44px;
          border: 1px solid #dce2eb;
          border-radius: 10px;
          background: white;
          color: #29364b;
          padding: 0 14px;
          cursor: pointer;
          font-size: 13px;
          font-weight: 700;
        }

        .button:hover {
          background: #f7f9fc;
        }

        .button.primary {
          border-color: #172d52;
          background: #172d52;
          color: white;
        }

        .button.danger {
          color: #b52d28;
        }

        .tabs {
          display: flex;
          align-items: center;
          gap: 6px;
          padding: 6px;
          margin-bottom: 18px;
          overflow-x: auto;
          border: 1px solid #e3e8f0;
          border-radius: 12px;
          background: white;
        }

        .tab {
          flex: 0 0 auto;
          border: 0;
          border-radius: 8px;
          padding: 9px 13px;
          background: transparent;
          color: #6d788b;
          cursor: pointer;
          font-size: 12px;
          font-weight: 750;
        }

        .tab:hover {
          background: #f4f6fa;
        }

        .tab.active {
          background: #172d52;
          color: white;
        }

        .content {
          display: grid;
          grid-template-columns: minmax(0, 1fr);
          gap: 16px;
        }

        .notification-card {
          position: relative;
          padding: 18px;
          margin-bottom: 11px;
          border: 1px solid #e1e6ee;
          border-radius: 15px;
          background: white;
          cursor: pointer;
          transition: 0.18s ease;
          box-shadow: 0 3px 15px rgba(26, 41, 69, 0.035);
        }

        .notification-card:hover {
          border-color: #cbd5e4;
          box-shadow: 0 9px 28px rgba(26, 41, 69, 0.075);
          transform: translateY(-1px);
        }

        .notification-card.unread {
          border-left: 4px solid #3178ed;
          background: linear-gradient(90deg, #f9fbff, white);
        }

        .notification-card.danger {
          border-left-color: #d93b32;
        }

        .notification-card.warning {
          border-left-color: #d99b1e;
        }

        .notification-card.success {
          border-left-color: #23a363;
        }

        .notification-header {
          display: flex;
          justify-content: space-between;
          align-items: flex-start;
          gap: 18px;
        }

        .notification-main {
          display: flex;
          gap: 13px;
          min-width: 0;
        }

        .notification-icon {
          flex: 0 0 44px;
          width: 44px;
          height: 44px;
          display: flex;
          align-items: center;
          justify-content: center;
          border-radius: 11px;
          background: #edf3fc;
          font-size: 19px;
        }

        .notification-card.danger .notification-icon {
          background: #fff0ef;
        }

        .notification-card.warning .notification-icon {
          background: #fff7e6;
        }

        .notification-card.success .notification-icon {
          background: #ebfaf1;
        }

        .notification-title {
          margin: 0;
          color: #18253b;
          font-size: 15px;
          font-weight: 800;
        }

        .notification-message {
          margin: 5px 0 0;
          color: #667287;
          font-size: 13px;
          line-height: 1.5;
        }

        .notification-time {
          flex: 0 0 auto;
          color: #8a94a5;
          font-size: 11px;
          white-space: nowrap;
        }

        .payment-highlight {
          display: grid;
          grid-template-columns: repeat(4, minmax(0, 1fr));
          gap: 8px;
          margin-top: 16px;
        }

        .payment-item {
          padding: 11px 12px;
          border-radius: 9px;
          background: #f7f9fc;
        }

        .payment-item-label {
          color: #8993a4;
          font-size: 10px;
          font-weight: 800;
          text-transform: uppercase;
          letter-spacing: 0.045em;
        }

        .payment-item-value {
          margin-top: 4px;
          color: #243149;
          font-size: 13px;
          font-weight: 800;
        }

        .notification-footer {
          display: flex;
          justify-content: space-between;
          gap: 12px;
          align-items: center;
          margin-top: 15px;
          padding-top: 13px;
          border-top: 1px solid #edf0f5;
        }

        .metadata {
          display: flex;
          flex-wrap: wrap;
          gap: 8px;
        }

        .badge {
          display: inline-flex;
          align-items: center;
          padding: 5px 8px;
          border-radius: 999px;
          background: #f0f3f7;
          color: #637086;
          font-size: 10px;
          font-weight: 800;
        }

        .badge.realtime {
          background: #e9f8ef;
          color: #187a43;
        }

        .unread-label {
          color: #3276df;
          font-size: 11px;
          font-weight: 800;
        }

        .empty {
          padding: 75px 20px;
          border: 1px solid #e2e7ef;
          border-radius: 16px;
          background: white;
          text-align: center;
        }

        .empty-icon {
          width: 62px;
          height: 62px;
          display: flex;
          align-items: center;
          justify-content: center;
          margin: 0 auto 15px;
          border-radius: 18px;
          background: #edf3fb;
          font-size: 27px;
        }

        .empty h2 {
          margin: 0;
          font-size: 19px;
        }

        .empty p {
          margin: 8px 0 0;
          color: #788397;
          font-size: 13px;
        }

        .loading-card {
          height: 145px;
          margin-bottom: 11px;
          border-radius: 15px;
          background: linear-gradient(
            90deg,
            #eef1f5 25%,
            #f7f8fa 37%,
            #eef1f5 63%
          );
          background-size: 400% 100%;
          animation: shimmer 1.3s infinite;
        }

        @keyframes shimmer {
          0% {
            background-position: 100% 0;
          }

          100% {
            background-position: -100% 0;
          }
        }

        .error {
          margin-bottom: 15px;
          padding: 14px 16px;
          border: 1px solid #f1c4c1;
          border-radius: 11px;
          background: #fff4f3;
          color: #a62b25;
          font-size: 13px;
        }

        .modal-backdrop {
          position: fixed;
          inset: 0;
          z-index: 1000;
          display: flex;
          align-items: center;
          justify-content: center;
          padding: 20px;
          background: rgba(12, 19, 32, 0.58);
          backdrop-filter: blur(5px);
        }

        .modal {
          width: min(850px, 100%);
          max-height: 90vh;
          overflow-y: auto;
          border-radius: 18px;
          background: white;
          box-shadow: 0 30px 100px rgba(0, 0, 0, 0.25);
        }

        .modal-header {
          display: flex;
          justify-content: space-between;
          gap: 18px;
          padding: 22px;
          border-bottom: 1px solid #e8ecf2;
        }

        .modal-header h2 {
          margin: 0;
          font-size: 20px;
        }

        .modal-header p {
          margin: 6px 0 0;
          color: #768195;
          font-size: 12px;
        }

        .close {
          width: 36px;
          height: 36px;
          border: 0;
          border-radius: 50%;
          background: #f0f3f7;
          color: #47546a;
          cursor: pointer;
          font-size: 19px;
        }

        .modal-body {
          padding: 22px;
        }

        .modal-section {
          margin-bottom: 24px;
        }

        .modal-section h3 {
          margin: 0 0 11px;
          color: #6c778a;
          font-size: 11px;
          text-transform: uppercase;
          letter-spacing: 0.06em;
        }

        .modal-grid {
          display: grid;
          grid-template-columns: repeat(2, minmax(0, 1fr));
          gap: 10px;
        }

        .modal-item {
          padding: 13px;
          border: 1px solid #e4e8ee;
          border-radius: 10px;
        }

        .modal-item span {
          display: block;
          margin-bottom: 5px;
          color: #8490a2;
          font-size: 10px;
          font-weight: 800;
          text-transform: uppercase;
        }

        .modal-item strong {
          color: #26344b;
          font-size: 13px;
          word-break: break-word;
        }

        @media (max-width: 1050px) {
          .stats {
            grid-template-columns: repeat(2, 1fr);
          }

          .payment-highlight {
            grid-template-columns: repeat(2, 1fr);
          }
        }

        @media (max-width: 760px) {
          .notification-shell {
            padding: 17px;
          }

          .hero {
            flex-direction: column;
          }

          .hero h1 {
            font-size: 28px;
          }

          .toolbar {
            flex-direction: column;
            align-items: stretch;
          }

          .actions {
            justify-content: flex-end;
          }

          .notification-header {
            flex-direction: column;
          }

          .notification-time {
            margin-left: 57px;
          }
        }

        @media (max-width: 520px) {
          .stats {
            grid-template-columns: 1fr 1fr;
            gap: 9px;
          }

          .stat {
            padding: 13px;
            min-height: 95px;
          }

          .stat-value {
            font-size: 21px;
          }

          .payment-highlight {
            grid-template-columns: 1fr 1fr;
          }

          .modal-grid {
            grid-template-columns: 1fr;
          }

          .notification-card {
            padding: 14px;
          }
        }
      `}</style>

      <div className="container">
        <header className="hero">
          <div>
            <div className="eyebrow">
              <span>●</span>
              Notification Center
            </div>

            <h1>Stay on top of your portfolio</h1>

            <p>
              Real-time activity, loan alerts, payments and important system
              updates.
            </p>
          </div>

          <div className="connection-card">
            <span
              className={`connection-dot ${
                realtimeConnected ? "live" : realtimeError ? "error" : ""
              }`}
            />

            {realtimeConnected
              ? "Realtime Connected"
              : realtimeError
                ? "Realtime Offline"
                : "Connecting"}
          </div>
        </header>

        <section className="stats">
          <div className="stat">
            <div className="stat-top">
              <span className="stat-label">Total Notifications</span>

              <span className="stat-icon">🔔</span>
            </div>

            <div className="stat-value">{notifications.length}</div>
          </div>

          <div className="stat">
            <div className="stat-top">
              <span className="stat-label">Unread</span>

              <span className="stat-icon">✉</span>
            </div>

            <div className="stat-value">{unreadCount}</div>
          </div>

          <div className="stat">
            <div className="stat-top">
              <span className="stat-label">Urgent</span>

              <span className="stat-icon">⚠</span>
            </div>

            <div className="stat-value">{urgentCount}</div>
          </div>

          <div className="stat">
            <div className="stat-top">
              <span className="stat-label">Today</span>

              <span className="stat-icon">◷</span>
            </div>

            <div className="stat-value">{todayCount}</div>
          </div>
        </section>

        {loadError && (
          <div className="error">
            <strong>Could not load saved notifications.</strong> {loadError}
          </div>
        )}

        <div className="toolbar">
          <div className="search">
            <span className="search-icon">🔍</span>

            <input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Search notifications, borrowers, loans, transactions..."
            />
          </div>

          <div className="actions">
            <button
              className="button"
              onClick={() => void loadNotifications(true)}
              disabled={refreshing}
            >
              {refreshing ? "Refreshing..." : "Refresh"}
            </button>

            {unreadCount > 0 && (
              <button
                className="button"
                onClick={() => void markAllRead()}
                disabled={markingAll}
              >
                {markingAll ? "Updating..." : "Mark all read"}
              </button>
            )}

            {notifications.length > 0 && (
              <button className="button danger" onClick={clearLocal}>
                Clear view
              </button>
            )}
          </div>
        </div>

        <nav className="tabs">
          {(
            [
              ["ALL", "All"],
              ["UNREAD", `Unread ${unreadCount}`],
              ["PAYMENT", `Payments ${paymentCount}`],
              ["LOAN", "Loans"],
              ["APPROVAL", "Approvals"],
              ["REMINDER", "Reminders"],
              ["OVERDUE", "Overdue"],
              ["ALERT", "Alerts"],
            ] as [FilterType, string][]
          ).map(([value, label]) => (
            <button
              key={value}
              className={`tab ${filter === value ? "active" : ""}`}
              onClick={() => setFilter(value)}
            >
              {label}
            </button>
          ))}
        </nav>

        <section className="content">
          {loading ? (
            <>
              <div className="loading-card" />
              <div className="loading-card" />
              <div className="loading-card" />
            </>
          ) : filteredNotifications.length === 0 ? (
            <div className="empty">
              <div className="empty-icon">🔔</div>

              <h2>No notifications</h2>

              <p>
                {search
                  ? "No notifications match your search."
                  : filter === "UNREAD"
                    ? "You have no unread notifications."
                    : "New activity will appear here automatically."}
              </p>
            </div>
          ) : (
            filteredNotifications.map((notification) => {
              const type = getType(notification);

              const severity = getSeverity(notification);

              const payment = type === "PAYMENT";

              return (
                <article
                  key={notification.id}
                  className={`notification-card ${
                    notification.read ? "" : "unread"
                  } ${severity}`}
                  onClick={() => {
                    void markRead(notification);

                    setSelected(notification);
                  }}
                >
                  <div className="notification-header">
                    <div className="notification-main">
                      <div className="notification-icon">{getIcon(type)}</div>

                      <div>
                        <h2 className="notification-title">
                          {notification.title}
                        </h2>

                        <p className="notification-message">
                          {notification.message ||
                            "You have a new notification."}
                        </p>
                      </div>
                    </div>

                    <div className="notification-time">
                      {relativeTime(
                        notification.createdAt || notification.paymentTimestamp,
                      )}
                    </div>
                  </div>

                  {payment && (
                    <div className="payment-highlight">
                      <div className="payment-item">
                        <div className="payment-item-label">Borrower</div>

                        <div className="payment-item-value">
                          {notification.borrowerName ||
                            (notification.borrowerId
                              ? `#${notification.borrowerId}`
                              : "Borrower")}
                        </div>
                      </div>

                      <div className="payment-item">
                        <div className="payment-item-label">Amount Paid</div>

                        <div className="payment-item-value">
                          {formatCurrency(
                            notification.amount,
                            notification.currency,
                          )}
                        </div>
                      </div>

                      <div className="payment-item">
                        <div className="payment-item-label">Principal</div>

                        <div className="payment-item-value">
                          {formatCurrency(
                            notification.principalPaid,
                            notification.currency,
                          )}
                        </div>
                      </div>

                      <div className="payment-item">
                        <div className="payment-item-label">Outstanding</div>

                        <div className="payment-item-value">
                          {formatCurrency(
                            notification.outstandingBalance,
                            notification.currency,
                          )}
                        </div>
                      </div>
                    </div>
                  )}

                  <div className="notification-footer">
                    <div className="metadata">
                      <span className="badge">{type}</span>

                      {notification.loanReference && (
                        <span className="badge">
                          {notification.loanReference}
                        </span>
                      )}

                      {notification.realtime && (
                        <span className="badge realtime">● LIVE</span>
                      )}

                      {notification.paymentStatus && (
                        <span className="badge">
                          {notification.paymentStatus}
                        </span>
                      )}
                    </div>

                    {!notification.read && (
                      <span className="unread-label">NEW</span>
                    )}
                  </div>
                </article>
              );
            })
          )}
        </section>
      </div>

      {selected && (
        <div className="modal-backdrop" onClick={() => setSelected(null)}>
          <div className="modal" onClick={(event) => event.stopPropagation()}>
            <div className="modal-header">
              <div>
                <h2>{selected.title}</h2>

                <p>
                  {formatDate(selected.createdAt || selected.paymentTimestamp)}
                </p>
              </div>

              <button className="close" onClick={() => setSelected(null)}>
                ×
              </button>
            </div>

            <div className="modal-body">
              <div className="modal-section">
                <h3>Notification</h3>

                <div className="modal-item">
                  <strong>
                    {selected.message || "No additional message."}
                  </strong>
                </div>
              </div>

              <div className="modal-section">
                <h3>Borrower & Loan</h3>

                <div className="modal-grid">
                  <div className="modal-item">
                    <span>Borrower</span>

                    <strong>
                      {selected.borrowerName ||
                        (selected.borrowerId
                          ? `Borrower #${selected.borrowerId}`
                          : "—")}
                    </strong>
                  </div>

                  <div className="modal-item">
                    <span>Borrower ID</span>

                    <strong>{selected.borrowerId || "—"}</strong>
                  </div>

                  <div className="modal-item">
                    <span>Loan</span>

                    <strong>
                      {selected.loanReference ||
                        (selected.loanId ? `Loan #${selected.loanId}` : "—")}
                    </strong>
                  </div>

                  <div className="modal-item">
                    <span>Loan Status</span>

                    <strong>{selected.loanStatus || "—"}</strong>
                  </div>
                </div>
              </div>

              {getType(selected) === "PAYMENT" && (
                <>
                  <div className="modal-section">
                    <h3>Payment</h3>

                    <div className="modal-grid">
                      <div className="modal-item">
                        <span>Amount Paid</span>

                        <strong>
                          {formatCurrency(selected.amount, selected.currency)}
                        </strong>
                      </div>

                      <div className="modal-item">
                        <span>Principal Paid</span>

                        <strong>
                          {formatCurrency(
                            selected.principalPaid,
                            selected.currency,
                          )}
                        </strong>
                      </div>

                      <div className="modal-item">
                        <span>Interest Paid</span>

                        <strong>
                          {formatCurrency(
                            selected.interestPaid,
                            selected.currency,
                          )}
                        </strong>
                      </div>

                      <div className="modal-item">
                        <span>Penalty Paid</span>

                        <strong>
                          {formatCurrency(
                            selected.penaltyPaid,
                            selected.currency,
                          )}
                        </strong>
                      </div>

                      <div className="modal-item">
                        <span>Outstanding Balance</span>

                        <strong>
                          {formatCurrency(
                            selected.outstandingBalance,
                            selected.currency,
                          )}
                        </strong>
                      </div>

                      <div className="modal-item">
                        <span>Payment Status</span>

                        <strong>{selected.paymentStatus || "—"}</strong>
                      </div>
                    </div>
                  </div>

                  <div className="modal-section">
                    <h3>Transaction</h3>

                    <div className="modal-grid">
                      <div className="modal-item">
                        <span>Payment Method</span>

                        <strong>{selected.paymentMethod || "—"}</strong>
                      </div>

                      <div className="modal-item">
                        <span>Channel</span>

                        <strong>{selected.channel || "—"}</strong>
                      </div>

                      <div className="modal-item">
                        <span>Transaction ID</span>

                        <strong>{selected.transactionId || "—"}</strong>
                      </div>

                      <div className="modal-item">
                        <span>Payment Reference</span>

                        <strong>{selected.paymentReference || "—"}</strong>
                      </div>
                    </div>
                  </div>
                </>
              )}
            </div>
          </div>
        </div>
      )}
    </main>
  );
}
