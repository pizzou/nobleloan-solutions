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

interface NotificationPageProps {
  organizationId?: number;
}

interface DisplayNotification
  extends PaymentNotification {
  localId: string;
  receivedAt: string;
  read: boolean;
}

const MAX_NOTIFICATIONS = 100;

function toNumber(
  value: number | string | undefined | null
): number {
  if (
    value === undefined ||
    value === null ||
    value === ""
  ) {
    return 0;
  }

  const parsed =
    typeof value === "number"
      ? value
      : Number(value);

  return Number.isFinite(parsed)
    ? parsed
    : 0;
}

function formatCurrency(
  value: number | string | undefined | null,
  currency = "RWF"
): string {
  const amount = toNumber(value);

  return new Intl.NumberFormat("en-RW", {
    style: "currency",
    currency,
    maximumFractionDigits: 2,
  }).format(amount);
}

function formatDate(
  value?: string
): string {
  if (!value) {
    return "—";
  }

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat(
    "en-RW",
    {
      dateStyle: "medium",
      timeStyle: "short",
    }
  ).format(date);
}

function formatRelativeTime(
  value: string
): string {
  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return "";
  }

  const seconds =
    Math.floor(
      (Date.now() - date.getTime()) / 1000
    );

  if (seconds < 10) {
    return "Just now";
  }

  if (seconds < 60) {
    return `${seconds}s ago`;
  }

  const minutes =
    Math.floor(seconds / 60);

  if (minutes < 60) {
    return `${minutes}m ago`;
  }

  const hours =
    Math.floor(minutes / 60);

  if (hours < 24) {
    return `${hours}h ago`;
  }

  const days =
    Math.floor(hours / 24);

  return `${days}d ago`;
}

function getPaymentMethodLabel(
  notification: PaymentNotification
): string {
  if (
    notification.paymentMethod &&
    notification.channel
  ) {
    return `${notification.paymentMethod} • ${notification.channel}`;
  }

  return (
    notification.paymentMethod ||
    notification.channel ||
    "Payment"
  );
}

function getBorrowerDisplayName(
  notification: PaymentNotification
): string {
  if (notification.borrowerName) {
    return notification.borrowerName;
  }

  if (notification.borrowerId) {
    return `Borrower #${notification.borrowerId}`;
  }

  return "Borrower";
}

function getPaymentStatusClass(
  status?: string
): string {
  switch (
    status?.toUpperCase()
  ) {
    case "PAID":
    case "COMPLETED":
    case "SUCCESS":
    case "FULLY_PAID":
      return "status-success";

    case "PARTIALLY_PAID":
    case "PARTIAL":
      return "status-warning";

    case "FAILED":
    case "REJECTED":
    case "CANCELLED":
      return "status-danger";

    default:
      return "status-neutral";
  }
}

export default function NotificationPage({
  organizationId = 1,
}: NotificationPageProps) {
  const [
    notifications,
    setNotifications,
  ] = useState<DisplayNotification[]>(
    []
  );

  const [
    realtimeConnected,
    setRealtimeConnected,
  ] = useState(false);

  const [
    realtimeError,
    setRealtimeError,
  ] = useState<string | null>(
    null
  );

  const [
    connecting,
    setConnecting,
  ] = useState(true);

  const [
    showUnreadOnly,
    setShowUnreadOnly,
  ] = useState(false);

  const [
    selectedNotification,
    setSelectedNotification,
  ] =
    useState<DisplayNotification | null>(
      null
    );

  const reconnectTimer =
    useRef<ReturnType<
      typeof setTimeout
    > | null>(null);

  const connectionAttempt =
    useRef(0);

  const mountedRef =
    useRef(true);

  const audioRef =
    useRef<HTMLAudioElement | null>(
      null
    );

  const playNotificationSound =
    useCallback(() => {
      try {
        if (!audioRef.current) {
          audioRef.current =
            new Audio(
              "/sounds/notification.mp3"
            );

          audioRef.current.volume =
            0.35;
        }

        void audioRef.current.play();
      } catch {
        // Browser may block autoplay.
      }
    }, []);

  const addNotification =
    useCallback(
      (
        notification: PaymentNotification
      ) => {
        if (!mountedRef.current) {
          return;
        }

        const now =
          new Date().toISOString();

        const displayNotification:
          DisplayNotification = {
          ...notification,

          localId:
            `${notification.paymentId}-${notification.transactionId || now}-${Date.now()}`,

          receivedAt: now,

          read: false,
        };

        setNotifications(
          (current) => {
            const exists =
              current.some(
                (item) =>
                  item.paymentId ===
                    notification.paymentId &&
                  item.transactionId ===
                    notification.transactionId
              );

            if (exists) {
              return current;
            }

            return [
              displayNotification,
              ...current,
            ].slice(
              0,
              MAX_NOTIFICATIONS
            );
          }
        );

        playNotificationSound();
      },
      [playNotificationSound]
    );

  const connect =
    useCallback(() => {
      if (!organizationId) {
        setRealtimeError(
          "Organization ID is missing."
        );

        setConnecting(false);

        return;
      }

      connectionAttempt.current += 1;

      const attempt =
        connectionAttempt.current;

      setConnecting(true);

      setRealtimeError(null);

      try {
        disconnectFromPaymentNotifications();

        connectToPaymentNotifications(
          organizationId,
          {
            onPaymentReceived:
              addNotification,

            onConnected: () => {
              if (
                !mountedRef.current ||
                attempt !==
                  connectionAttempt.current
              ) {
                return;
              }

              console.info(
                "[REALTIME] Payment notification connection established."
              );

              setRealtimeConnected(
                true
              );

              setConnecting(false);

              setRealtimeError(
                null
              );
            },

            onDisconnected: () => {
              if (
                !mountedRef.current ||
                attempt !==
                  connectionAttempt.current
              ) {
                return;
              }

              console.warn(
                "[REALTIME] Payment notification connection disconnected."
              );

              setRealtimeConnected(
                false
              );

              setConnecting(false);

              setRealtimeError(
                "Realtime connection temporarily unavailable."
              );
            },

            onError: (error) => {
              if (
                !mountedRef.current ||
                attempt !==
                  connectionAttempt.current
              ) {
                return;
              }

              console.error(
                "[REALTIME] Payment notification connection error:",
                error
              );

              setRealtimeConnected(
                false
              );

              setConnecting(false);

              setRealtimeError(
                "Realtime connection temporarily unavailable."
              );
            },
          }
        );
      } catch (error) {
        console.error(
          "[REALTIME] Failed to start payment notification connection:",
          error
        );

        if (
          !mountedRef.current ||
          attempt !==
            connectionAttempt.current
        ) {
          return;
        }

        setRealtimeConnected(
          false
        );

        setConnecting(false);

        setRealtimeError(
          error instanceof Error
            ? error.message
            : "Unable to connect to realtime notifications."
        );
      }
    }, [
      organizationId,
      addNotification,
    ]);

  useEffect(() => {
    mountedRef.current = true;

    connect();

    return () => {
      mountedRef.current = false;

      connectionAttempt.current += 1;

      if (
        reconnectTimer.current
      ) {
        clearTimeout(
          reconnectTimer.current
        );

        reconnectTimer.current =
          null;
      }

      disconnectFromPaymentNotifications();
    };
  }, [connect]);

  useEffect(() => {
    if (realtimeConnected) {
      return;
    }

    if (
      reconnectTimer.current
    ) {
      return;
    }

    reconnectTimer.current =
      setTimeout(() => {
        reconnectTimer.current =
          null;

        if (
          mountedRef.current
        ) {
          connect();
        }
      }, 5000);

    return () => {
      if (
        reconnectTimer.current
      ) {
        clearTimeout(
          reconnectTimer.current
        );

        reconnectTimer.current =
          null;
      }
    };
  }, [
    realtimeConnected,
    connect,
  ]);

  const unreadCount =
    useMemo(
      () =>
        notifications.filter(
          (item) => !item.read
        ).length,
      [notifications]
    );

  const visibleNotifications =
    useMemo(() => {
      if (!showUnreadOnly) {
        return notifications;
      }

      return notifications.filter(
        (item) => !item.read
      );
    }, [
      notifications,
      showUnreadOnly,
    ]);

  const markAsRead =
    useCallback(
      (localId: string) => {
        setNotifications(
          (current) =>
            current.map(
              (item) =>
                item.localId ===
                localId
                  ? {
                      ...item,
                      read: true,
                    }
                  : item
            )
        );
      },
      []
    );

  const markAllAsRead =
    useCallback(() => {
      setNotifications(
        (current) =>
          current.map(
            (item) => ({
              ...item,
              read: true,
            })
          )
      );
    }, []);

  const clearNotifications =
    useCallback(() => {
      setNotifications([]);
      setSelectedNotification(
        null
      );
    }, []);

  return (
    <main className="notification-page">
      <style jsx>{`
        .notification-page {
          min-height: 100vh;
          padding: 32px;
          background: #f7f8fa;
          color: #172033;
        }

        .container {
          max-width: 1400px;
          margin: 0 auto;
        }

        .header {
          display: flex;
          justify-content: space-between;
          align-items: flex-start;
          gap: 24px;
          margin-bottom: 28px;
        }

        .title-area h1 {
          margin: 0;
          font-size: 30px;
          font-weight: 700;
          letter-spacing: -0.02em;
        }

        .title-area p {
          margin: 8px 0 0;
          color: #687386;
          font-size: 14px;
        }

        .connection {
          display: inline-flex;
          align-items: center;
          gap: 8px;
          padding: 9px 13px;
          border-radius: 999px;
          font-size: 13px;
          font-weight: 600;
        }

        .connection.live {
          background: #e9f9ef;
          color: #187a43;
        }

        .connection.offline {
          background: #fff1f0;
          color: #c9342d;
        }

        .connection.connecting {
          background: #fff7e5;
          color: #9a6700;
        }

        .dot {
          width: 8px;
          height: 8px;
          border-radius: 50%;
          background: currentColor;
        }

        .toolbar {
          display: flex;
          align-items: center;
          justify-content: space-between;
          gap: 16px;
          margin-bottom: 18px;
        }

        .toolbar-left,
        .toolbar-right {
          display: flex;
          gap: 10px;
          align-items: center;
          flex-wrap: wrap;
        }

        .button {
          border: 1px solid #d9dee7;
          background: white;
          color: #293449;
          padding: 9px 13px;
          border-radius: 8px;
          cursor: pointer;
          font-size: 13px;
          font-weight: 600;
        }

        .button:hover {
          background: #f4f6f9;
        }

        .button.primary {
          background: #172033;
          border-color: #172033;
          color: white;
        }

        .button.danger {
          color: #b42318;
        }

        .count {
          background: #eef2f7;
          border-radius: 999px;
          padding: 4px 9px;
          font-size: 12px;
          font-weight: 700;
        }

        .warning {
          margin-bottom: 18px;
          padding: 14px 16px;
          border-radius: 10px;
          border: 1px solid #f2d58a;
          background: #fff9e8;
          color: #755500;
          font-size: 14px;
        }

        .success {
          margin-bottom: 18px;
          padding: 12px 16px;
          border-radius: 10px;
          background: #ecfdf3;
          color: #167647;
          border: 1px solid #b7ebc9;
          font-size: 13px;
        }

        .grid {
          display: grid;
          grid-template-columns: minmax(0, 1fr);
          gap: 14px;
        }

        .card {
          background: white;
          border: 1px solid #e2e6ed;
          border-radius: 14px;
          padding: 20px;
          box-shadow:
            0 2px 8px rgba(20, 30, 50, 0.04);
          cursor: pointer;
          transition:
            border-color 0.15s ease,
            box-shadow 0.15s ease,
            transform 0.15s ease;
        }

        .card:hover {
          border-color: #c7ced9;
          box-shadow:
            0 8px 24px rgba(20, 30, 50, 0.08);
          transform: translateY(-1px);
        }

        .card.unread {
          border-left: 4px solid #1d72f3;
        }

        .card-top {
          display: flex;
          justify-content: space-between;
          gap: 16px;
          align-items: flex-start;
        }

        .payment-title {
          display: flex;
          gap: 12px;
          align-items: flex-start;
        }

        .payment-icon {
          width: 42px;
          height: 42px;
          border-radius: 10px;
          display: flex;
          align-items: center;
          justify-content: center;
          background: #edf6ff;
          color: #1464c4;
          font-size: 20px;
          flex-shrink: 0;
        }

        .payment-title h3 {
          margin: 0;
          font-size: 16px;
          font-weight: 700;
        }

        .payment-title p {
          margin: 5px 0 0;
          color: #6c7789;
          font-size: 13px;
        }

        .amount {
          font-size: 20px;
          font-weight: 800;
          white-space: nowrap;
          color: #147a45;
        }

        .details {
          display: grid;
          grid-template-columns:
            repeat(4, minmax(0, 1fr));
          gap: 12px;
          margin-top: 20px;
        }

        .detail {
          background: #f8f9fb;
          border-radius: 9px;
          padding: 12px;
          min-width: 0;
        }

        .detail-label {
          color: #7b8595;
          font-size: 11px;
          text-transform: uppercase;
          letter-spacing: 0.05em;
          font-weight: 700;
        }

        .detail-value {
          margin-top: 5px;
          font-size: 13px;
          font-weight: 650;
          color: #253047;
          word-break: break-word;
        }

        .status {
          display: inline-flex;
          align-items: center;
          border-radius: 999px;
          padding: 5px 9px;
          font-size: 11px;
          font-weight: 700;
        }

        .status-success {
          background: #e9f9ef;
          color: #187a43;
        }

        .status-warning {
          background: #fff7e5;
          color: #996600;
        }

        .status-danger {
          background: #fff0ef;
          color: #c52b25;
        }

        .status-neutral {
          background: #edf0f4;
          color: #596579;
        }

        .bottom-row {
          display: flex;
          justify-content: space-between;
          gap: 12px;
          margin-top: 16px;
          padding-top: 14px;
          border-top: 1px solid #edf0f4;
          color: #7b8595;
          font-size: 12px;
        }

        .empty {
          padding: 70px 20px;
          background: white;
          border: 1px solid #e2e6ed;
          border-radius: 14px;
          text-align: center;
        }

        .empty-icon {
          font-size: 42px;
          margin-bottom: 12px;
        }

        .empty h3 {
          margin: 0;
          font-size: 18px;
        }

        .empty p {
          color: #707b8c;
          font-size: 14px;
          margin: 8px 0 0;
        }

        .modal-backdrop {
          position: fixed;
          inset: 0;
          background: rgba(12, 18, 30, 0.55);
          display: flex;
          justify-content: center;
          align-items: center;
          padding: 20px;
          z-index: 1000;
        }

        .modal {
          width: min(760px, 100%);
          max-height: 90vh;
          overflow-y: auto;
          background: white;
          border-radius: 16px;
          box-shadow:
            0 24px 80px rgba(0, 0, 0, 0.2);
        }

        .modal-header {
          padding: 22px;
          border-bottom: 1px solid #e6e9ef;
          display: flex;
          justify-content: space-between;
          gap: 20px;
        }

        .modal-header h2 {
          margin: 0;
          font-size: 20px;
        }

        .modal-body {
          padding: 22px;
        }

        .modal-section {
          margin-bottom: 22px;
        }

        .modal-section h4 {
          margin: 0 0 12px;
          font-size: 13px;
          text-transform: uppercase;
          letter-spacing: 0.05em;
          color: #687386;
        }

        .modal-grid {
          display: grid;
          grid-template-columns:
            repeat(2, minmax(0, 1fr));
          gap: 10px;
        }

        .modal-item {
          padding: 12px;
          border: 1px solid #e3e7ed;
          border-radius: 9px;
        }

        .modal-item span {
          display: block;
          font-size: 11px;
          color: #7a8494;
          margin-bottom: 4px;
        }

        .modal-item strong {
          font-size: 14px;
          color: #202b3e;
        }

        .close {
          border: 0;
          background: #f0f2f5;
          width: 34px;
          height: 34px;
          border-radius: 50%;
          cursor: pointer;
          font-size: 18px;
        }

        @media (max-width: 900px) {
          .notification-page {
            padding: 20px;
          }

          .header {
            flex-direction: column;
          }

          .details {
            grid-template-columns:
              repeat(2, minmax(0, 1fr));
          }

          .toolbar {
            flex-direction: column;
            align-items: stretch;
          }
        }

        @media (max-width: 600px) {
          .notification-page {
            padding: 14px;
          }

          .details {
            grid-template-columns: 1fr;
          }

          .card-top {
            flex-direction: column;
          }

          .amount {
            font-size: 18px;
          }

          .modal-grid {
            grid-template-columns: 1fr;
          }
        }
      `}</style>

      <div className="container">
        <header className="header">
          <div className="title-area">
            <h1>
              Payment Notifications
            </h1>

            <p>
              Real-time payment activity
              for organization{" "}
              <strong>
                #{organizationId}
              </strong>
            </p>
          </div>

          <div
            className={`connection ${
              realtimeConnected
                ? "live"
                : connecting
                ? "connecting"
                : "offline"
            }`}
          >
            <span className="dot" />

            {realtimeConnected
              ? "Realtime live"
              : connecting
              ? "Connecting..."
              : "Realtime unavailable"}
          </div>
        </header>

        {!realtimeConnected &&
          realtimeError && (
            <div className="warning">
              ⚠️{" "}
              <strong>
                Realtime notifications
                unavailable
              </strong>
              <br />

              <span>
                Existing notifications are
                still available. The system
                will continue trying to
                reconnect.
              </span>
            </div>
          )}

        {realtimeConnected && (
          <div className="success">
            ✓ Realtime payment notifications
            are connected and listening for
            new payments.
          </div>
        )}

        <div className="toolbar">
          <div className="toolbar-left">
            <button
              className="button"
              onClick={() =>
                setShowUnreadOnly(
                  (value) => !value
                )
              }
            >
              {showUnreadOnly
                ? "Show All"
                : "Unread Only"}
            </button>

            <span className="count">
              {unreadCount} unread
            </span>

            <span className="count">
              {notifications.length} total
            </span>
          </div>

          <div className="toolbar-right">
            {unreadCount > 0 && (
              <button
                className="button"
                onClick={
                  markAllAsRead
                }
              >
                Mark all as read
              </button>
            )}

            {notifications.length >
              0 && (
              <button
                className="button danger"
                onClick={
                  clearNotifications
                }
              >
                Clear
              </button>
            )}

            {!realtimeConnected && (
              <button
                className="button primary"
                onClick={connect}
              >
                Reconnect
              </button>
            )}
          </div>
        </div>

        <section className="grid">
          {visibleNotifications.length ===
          0 ? (
            <div className="empty">
              <div className="empty-icon">
                💳
              </div>

              <h3>
                No payment notifications
              </h3>

              <p>
                New borrower payments will
                appear here automatically in
                real time.
              </p>
            </div>
          ) : (
            visibleNotifications.map(
              (notification) => (
                <article
                  key={
                    notification.localId
                  }
                  className={`card ${
                    notification.read
                      ? ""
                      : "unread"
                  }`}
                  onClick={() => {
                    markAsRead(
                      notification.localId
                    );

                    setSelectedNotification(
                      notification
                    );
                  }}
                >
                  <div className="card-top">
                    <div className="payment-title">
                      <div className="payment-icon">
                        💳
                      </div>

                      <div>
                        <h3>
                          {notification.title ||
                            "Payment Received"}
                        </h3>

                        <p>
                          {getBorrowerDisplayName(
                            notification
                          )}
                          {" • "}
                          {notification.loanReference ||
                            `Loan #${notification.loanId}`}
                        </p>
                      </div>
                    </div>

                    <div className="amount">
                      {formatCurrency(
                        notification.amount,
                        notification.currency
                      )}
                    </div>
                  </div>

                  <div className="details">
                    <div className="detail">
                      <div className="detail-label">
                        Borrower
                      </div>

                      <div className="detail-value">
                        {getBorrowerDisplayName(
                          notification
                        )}
                      </div>
                    </div>

                    <div className="detail">
                      <div className="detail-label">
                        Loan
                      </div>

                      <div className="detail-value">
                        {notification.loanReference ||
                          `Loan #${notification.loanId}`}
                      </div>
                    </div>

                    <div className="detail">
                      <div className="detail-label">
                        Amount Paid
                      </div>

                      <div className="detail-value">
                        {formatCurrency(
                          notification.amount,
                          notification.currency
                        )}
                      </div>
                    </div>

                    <div className="detail">
                      <div className="detail-label">
                        Principal
                      </div>

                      <div className="detail-value">
                        {formatCurrency(
                          notification.principalPaid,
                          notification.currency
                        )}
                      </div>
                    </div>

                    <div className="detail">
                      <div className="detail-label">
                        Interest
                      </div>

                      <div className="detail-value">
                        {formatCurrency(
                          notification.interestPaid,
                          notification.currency
                        )}
                      </div>
                    </div>

                    <div className="detail">
                      <div className="detail-label">
                        Penalty
                      </div>

                      <div className="detail-value">
                        {formatCurrency(
                          notification.penaltyPaid,
                          notification.currency
                        )}
                      </div>
                    </div>

                    <div className="detail">
                      <div className="detail-label">
                        Outstanding Balance
                      </div>

                      <div className="detail-value">
                        {formatCurrency(
                          notification.outstandingBalance,
                          notification.currency
                        )}
                      </div>
                    </div>

                    <div className="detail">
                      <div className="detail-label">
                        Status
                      </div>

                      <div className="detail-value">
                        <span
                          className={`status ${getPaymentStatusClass(
                            notification.paymentStatus
                          )}`}
                        >
                          {notification.paymentStatus ||
                            "RECEIVED"}
                        </span>
                      </div>
                    </div>

                    <div className="detail">
                      <div className="detail-label">
                        Payment Method
                      </div>

                      <div className="detail-value">
                        {getPaymentMethodLabel(
                          notification
                        )}
                      </div>
                    </div>

                    <div className="detail">
                      <div className="detail-label">
                        Transaction ID
                      </div>

                      <div className="detail-value">
                        {notification.transactionId ||
                          "—"}
                      </div>
                    </div>

                    <div className="detail">
                      <div className="detail-label">
                        Payment Reference
                      </div>

                      <div className="detail-value">
                        {notification.paymentReference ||
                          "—"}
                      </div>
                    </div>

                    <div className="detail">
                      <div className="detail-label">
                        Loan Status
                      </div>

                      <div className="detail-value">
                        {notification.loanStatus ||
                          "—"}
                      </div>
                    </div>
                  </div>

                  <div className="bottom-row">
                    <span>
                      {formatDate(
                        notification.paymentTimestamp
                      )}
                    </span>

                    <span>
                      {formatRelativeTime(
                        notification.receivedAt
                      )}
                    </span>
                  </div>
                </article>
              )
            )
          )}
        </section>
      </div>

      {selectedNotification && (
        <div
          className="modal-backdrop"
          onClick={() =>
            setSelectedNotification(
              null
            )
          }
        >
          <div
            className="modal"
            onClick={(event) =>
              event.stopPropagation()
            }
          >
            <div className="modal-header">
              <div>
                <h2>
                  Payment Details
                </h2>

                <p
                  style={{
                    margin:
                      "6px 0 0",
                    color:
                      "#707b8c",
                    fontSize:
                      "13px",
                  }}
                >
                  {
                    selectedNotification
                      .loanReference
                  }
                </p>
              </div>

              <button
                className="close"
                onClick={() =>
                  setSelectedNotification(
                    null
                  )
                }
                aria-label="Close"
              >
                ×
              </button>
            </div>

            <div className="modal-body">
              <div className="modal-section">
                <h4>
                  Payment
                </h4>

                <div className="modal-grid">
                  <div className="modal-item">
                    <span>
                      Amount Received
                    </span>

                    <strong>
                      {formatCurrency(
                        selectedNotification.amount,
                        selectedNotification.currency
                      )}
                    </strong>
                  </div>

                  <div className="modal-item">
                    <span>
                      Payment Status
                    </span>

                    <strong>
                      {
                        selectedNotification.paymentStatus
                      }
                    </strong>
                  </div>

                  <div className="modal-item">
                    <span>
                      Principal Paid
                    </span>

                    <strong>
                      {formatCurrency(
                        selectedNotification.principalPaid,
                        selectedNotification.currency
                      )}
                    </strong>
                  </div>

                  <div className="modal-item">
                    <span>
                      Interest Paid
                    </span>

                    <strong>
                      {formatCurrency(
                        selectedNotification.interestPaid,
                        selectedNotification.currency
                      )}
                    </strong>
                  </div>

                  <div className="modal-item">
                    <span>
                      Penalty Paid
                    </span>

                    <strong>
                      {formatCurrency(
                        selectedNotification.penaltyPaid,
                        selectedNotification.currency
                      )}
                    </strong>
                  </div>

                  <div className="modal-item">
                    <span>
                      Outstanding Balance
                    </span>

                    <strong>
                      {formatCurrency(
                        selectedNotification.outstandingBalance,
                        selectedNotification.currency
                      )}
                    </strong>
                  </div>
                </div>
              </div>

              <div className="modal-section">
                <h4>
                  Borrower & Loan
                </h4>

                <div className="modal-grid">
                  <div className="modal-item">
                    <span>
                      Borrower
                    </span>

                    <strong>
                      {getBorrowerDisplayName(
                        selectedNotification
                      )}
                    </strong>
                  </div>

                  <div className="modal-item">
                    <span>
                      Borrower ID
                    </span>

                    <strong>
                      {selectedNotification.borrowerId ||
                        "—"}
                    </strong>
                  </div>

                  <div className="modal-item">
                    <span>
                      Loan
                    </span>

                    <strong>
                      {selectedNotification.loanReference ||
                        `Loan #${selectedNotification.loanId}`}
                    </strong>
                  </div>

                  <div className="modal-item">
                    <span>
                      Loan Status
                    </span>

                    <strong>
                      {selectedNotification.loanStatus ||
                        "—"}
                    </strong>
                  </div>
                </div>
              </div>

              <div className="modal-section">
                <h4>
                  Transaction
                </h4>

                <div className="modal-grid">
                  <div className="modal-item">
                    <span>
                      Payment Method
                    </span>

                    <strong>
                      {selectedNotification.paymentMethod ||
                        "—"}
                    </strong>
                  </div>

                  <div className="modal-item">
                    <span>
                      Channel
                    </span>

                    <strong>
                      {selectedNotification.channel ||
                        "—"}
                    </strong>
                  </div>

                  <div className="modal-item">
                    <span>
                      Transaction ID
                    </span>

                    <strong>
                      {selectedNotification.transactionId ||
                        "—"}
                    </strong>
                  </div>

                  <div className="modal-item">
                    <span>
                      Payment Reference
                    </span>

                    <strong>
                      {selectedNotification.paymentReference ||
                        "—"}
                    </strong>
                  </div>

                  <div className="modal-item">
                    <span>
                      Payment Date
                    </span>

                    <strong>
                      {formatDate(
                        selectedNotification.paymentTimestamp
                      )}
                    </strong>
                  </div>

                  <div className="modal-item">
                    <span>
                      Currency
                    </span>

                    <strong>
                      {selectedNotification.currency ||
                        "RWF"}
                    </strong>
                  </div>
                </div>
              </div>

              <div className="modal-section">
                <h4>
                  Message
                </h4>

                <div
                  style={{
                    padding:
                      "14px",
                    background:
                      "#f7f8fa",
                    borderRadius:
                      "10px",
                    fontSize:
                      "14px",
                    lineHeight:
                      1.6,
                    color:
                      "#3f4a5d",
                  }}
                >
                  {selectedNotification.message ||
                    "Payment received successfully."}
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
    </main>
  );
}