'use client';

import { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';

import { getLoans } from '../../../services/loanService';
import { getOverduePayments } from '../../../services/paymentService';
import { getDashboardStats } from '../../../services/dashboardService';
import {
  getMyNotifications,
  markNotificationRead,
} from '../../../services/notificationsService';

import {
  Loan,
  Payment,
  DashboardStats,
} from '../../../types/index';

import { PageSpinner } from '../../../components/ui/Skeleton';

interface Notif {
  id: string;
  type: 'danger' | 'warning' | 'success' | 'info';
  title: string;
  message: string;
  link?: string;
  time: string;
  realId?: number;
  read?: boolean;
}

interface RealtimeNotification {
  id?: number | string;
  type?: string;
  title?: string;
  message?: string;
  link?: string;
  createdAt?: string;
  read?: boolean;

  // Common payment-event fields.
  borrowerName?: string;
  loanNumber?: string;
  amount?: number | string;
  paymentAmount?: number | string;
  principalComponent?: number | string;
  interestComponent?: number | string;
}

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_URL ||
  'http://localhost:8080/api';

/**
 * SSE endpoint.
 *
 * If your backend exposes the realtime stream at another path,
 * change this value only.
 */
const REALTIME_URL =
  `${API_BASE_URL}/notifications/stream`;

function normalizeNotificationType(
  type?: string
): Notif['type'] {
  switch ((type || '').toUpperCase()) {
    case 'DANGER':
    case 'ERROR':
    case 'OVERDUE':
      return 'danger';

    case 'WARNING':
    case 'ALERT':
      return 'warning';

    case 'SUCCESS':
    case 'PAYMENT_RECEIVED':
    case 'PAYMENT':
      return 'success';

    default:
      return 'info';
  }
}

function formatNotificationTime(
  createdAt?: string
): string {
  if (!createdAt) {
    return 'Just now';
  }

  const date = new Date(createdAt);

  if (Number.isNaN(date.getTime())) {
    return 'Just now';
  }

  return date.toLocaleString();
}

function toNumber(
  value: unknown
): number | null {
  if (
    typeof value === 'number' &&
    Number.isFinite(value)
  ) {
    return value;
  }

  if (typeof value === 'string') {
    const parsed = Number(value);

    if (Number.isFinite(parsed)) {
      return parsed;
    }
  }

  return null;
}

function formatPaymentMessage(
  notification: RealtimeNotification
): string {
  const amount =
    toNumber(
      notification.amount ??
        notification.paymentAmount
    );

  const borrower =
    notification.borrowerName?.trim();

  const loan =
    notification.loanNumber?.trim();

  if (
    borrower &&
    loan &&
    amount !== null
  ) {
    return `${borrower} made a payment of RWF ${amount.toLocaleString()} for loan ${loan}.`;
  }

  if (
    borrower &&
    amount !== null
  ) {
    return `${borrower} made a payment of RWF ${amount.toLocaleString()}.`;
  }

  if (loan && amount !== null) {
    return `A payment of RWF ${amount.toLocaleString()} was received for loan ${loan}.`;
  }

  if (amount !== null) {
    return `A payment of RWF ${amount.toLocaleString()} was received.`;
  }

  return (
    notification.message ||
    'A new payment has been received.'
  );
}

function buildRealtimeNotification(
  incoming: RealtimeNotification
): Notif {
  const rawType =
    (incoming.type || '').toUpperCase();

  const isPayment =
    rawType === 'PAYMENT' ||
    rawType === 'PAYMENT_RECEIVED' ||
    rawType === 'PAYMENT_COMPLETED';

  const type =
    normalizeNotificationType(
      incoming.type
    );

  const id =
    incoming.id !== undefined
      ? String(incoming.id)
      : `${Date.now()}-${Math.random()}`;

  const title =
    incoming.title ||
    (isPayment
      ? 'Payment Received'
      : 'New Notification');

  const message =
    isPayment
      ? formatPaymentMessage(incoming)
      : (
          incoming.message ||
          'You have a new notification.'
        );

  return {
    id: `realtime-${id}`,
    realId:
      typeof incoming.id === 'number'
        ? incoming.id
        : undefined,
    type,
    title,
    message,
    link:
      incoming.link ||
      (isPayment
        ? '/dashboard/payments'
        : undefined),
    time: formatNotificationTime(
      incoming.createdAt
    ),
    read: false,
  };
}

function isSameNotification(
  a: Notif,
  b: Notif
): boolean {
  if (a.realId !== undefined &&
      b.realId !== undefined) {
    return a.realId === b.realId;
  }

  return a.id === b.id;
}

export default function NotificationsPage() {
  const [notifs, setNotifs] =
    useState<Notif[]>([]);

  const [loading, setLoading] =
    useState(true);

  const [filter, setFilter] =
    useState<
      'all' |
      'danger' |
      'warning' |
      'success' |
      'info'
    >('all');

  const [realtimeConnected, setRealtimeConnected] =
    useState(false);

  /**
   * ============================================================
   * LOAD EXISTING NOTIFICATIONS
   * ============================================================
   *
   * Persisted notifications are loaded first.
   * This means notifications are not lost if the user was
   * offline when the payment/event occurred.
   */
  useEffect(() => {
    let mounted = true;

    Promise.all([
      getLoans().catch((e) => {
        console.error(
          'notifications: getLoans failed',
          e
        );

        return [];
      }),

      getOverduePayments().catch((e) => {
        console.error(
          'notifications: getOverduePayments failed',
          e
        );

        return [];
      }),

      getDashboardStats().catch((e) => {
        console.error(
          'notifications: getDashboardStats failed',
          e
        );

        return null;
      }),

      getMyNotifications().catch((e) => {
        console.error(
          'notifications: getMyNotifications failed',
          e
        );

        return [];
      }),
    ])
      .then(
        ([
          loans,
          overdue,
          stats,
          real,
        ]) => {
          if (!mounted) {
            return;
          }

          const l =
            Array.isArray(loans)
              ? (loans as Loan[])
              : [];

          const o =
            Array.isArray(overdue)
              ? (overdue as Payment[])
              : [];

          const s =
            stats as DashboardStats | null;

          const realList =
            Array.isArray(real)
              ? (real as any[])
              : [];

          if (!Array.isArray(real)) {
            console.error(
              'notifications: getMyNotifications did not return an array:',
              real
            );
          }

          const n: Notif[] = [];

          /**
           * --------------------------------------------------------
           * REAL BACKEND NOTIFICATIONS
           * --------------------------------------------------------
           */
          realList.forEach((r) => {
            if (!r) {
              return;
            }

            n.push({
              id: `real-${r.id}`,
              realId:
                typeof r.id === 'number'
                  ? r.id
                  : undefined,

              read:
                r.read === true,

              type:
                normalizeNotificationType(
                  r.type
                ),

              title:
                r.title ||
                'Notification',

              message:
                r.message ||
                'You have a new notification.',

              link:
                r.link,

              time:
                formatNotificationTime(
                  r.createdAt
                ),
            });
          });

          /**
           * --------------------------------------------------------
           * PORTFOLIO-DERIVED ALERTS
           * --------------------------------------------------------
           */
          try {
            if (o.length > 0) {
              n.push({
                id: 'ov',
                type: 'danger',

                title:
                  `${o.length} Overdue Payment${
                    o.length > 1
                      ? 's'
                      : ''
                  }`,

                message:
                  `${o.length} payment${
                    o.length > 1
                      ? 's are'
                      : ' is'
                  } past due. Penalties accruing daily.`,

                link:
                  '/dashboard/payments',

                time:
                  'Now',
              });
            }

            const pending =
              l.filter(
                (x) =>
                  x.status === 'PENDING'
              );

            if (pending.length > 0) {
              n.push({
                id: 'pend',
                type: 'warning',

                title:
                  `${pending.length} Loan${
                    pending.length > 1
                      ? 's'
                      : ''
                  } Awaiting Approval`,

                message:
                  `${pending.length} application${
                    pending.length > 1
                      ? 's need'
                      : ' needs'
                  } your review.`,

                link:
                  '/dashboard/approvals',

                time:
                  'Today',
              });
            }

            const hr =
              l.filter(
                (x) =>
                  x.riskCategory === 'HIGH' ||
                  x.riskCategory === 'CRITICAL'
              );

            if (hr.length > 0) {
              n.push({
                id: 'hr',
                type: 'warning',

                title:
                  `${hr.length} High-Risk Loan${
                    hr.length > 1
                      ? 's'
                      : ''
                  }`,

                message:
                  `${hr.length} loan${
                    hr.length > 1
                      ? 's are'
                      : ' is'
                  } rated HIGH or CRITICAL risk. Review collateral.`,

                link:
                  '/dashboard/loans',

                time:
                  'Today',
              });
            }

            const totalDisbursed =
              toNumber(
                s?.totalDisbursed
              ) ?? 0;

            const totalCollected =
              toNumber(
                s?.totalCollected
              ) ?? 0;

            const rate =
              totalDisbursed > 0
                ? (
                    totalCollected /
                    totalDisbursed
                  ) * 100
                : 0;

            if (
              s &&
              rate >= 80
            ) {
              n.push({
                id: 'cr-good',
                type: 'success',

                title:
                  'Strong Collection Rate',

                message:
                  `Portfolio collection rate is ${rate.toFixed(
                    0
                  )}% — excellent performance!`,

                time:
                  'This week',
              });
            } else if (
              s &&
              rate < 50 &&
              totalDisbursed > 0
            ) {
              n.push({
                id: 'cr-low',
                type: 'warning',

                title:
                  'Low Collection Rate',

                message:
                  `Collection rate is only ${rate.toFixed(
                    0
                  )}%. Consider sending payment reminders.`,

                time:
                  'This week',
              });
            }

            if (
              s &&
              s.completedLoans > 0
            ) {
              n.push({
                id: 'closed',
                type: 'success',

                title:
                  `${s.completedLoans} Loan${
                    s.completedLoans > 1
                      ? 's'
                      : ''
                  } Fully Repaid`,

                message:
                  `${s.completedLoans} loan${
                    s.completedLoans > 1
                      ? 's have'
                      : ' has'
                  } been fully repaid. Great portfolio health!`,

                link:
                  '/dashboard/loans',

                time:
                  'This month',
              });
            }

            if (s) {
              n.push({
                id: 'summary',
                type: 'info',

                title:
                  'Portfolio Summary',

                message:
                  `${s.totalBorrowers} borrowers · ${s.activeLoans} active loans · RWF ${totalDisbursed.toLocaleString()} disbursed.`,

                link:
                  '/dashboard/reports',

                time:
                  'Today',
              });
            }
          } catch (e) {
            console.error(
              'notifications: failed to build portfolio-derived alerts',
              e
            );
          }

          setNotifs(n);
        }
      )
      .catch((e) => {
        console.error(
          'notifications: unexpected failure loading notifications',
          e
        );
      })
      .finally(() => {
        if (mounted) {
          setLoading(false);
        }
      });

    return () => {
      mounted = false;
    };
  }, []);

  /**
   * ============================================================
   * REAL-TIME NOTIFICATIONS
   * ============================================================
   *
   * The backend sends notification events through SSE.
   *
   * Example:
   *
   * {
   *   "id": 123,
   *   "type": "PAYMENT_RECEIVED",
   *   "title": "Payment Received",
   *   "message": "...",
   *   "borrowerName": "John Doe",
   *   "loanNumber": "LN-1001",
   *   "amount": 100000,
   *   "createdAt": "2026-08-12T07:20:00Z"
   * }
   */
  useEffect(() => {
    if (
      typeof window === 'undefined' ||
      typeof EventSource === 'undefined'
    ) {
      return;
    }

    let source: EventSource | null = null;
    let reconnectTimer:
      ReturnType<typeof setTimeout> | null =
        null;

    let stopped = false;

    const connect = () => {
      if (stopped) {
        return;
      }

      try {
        source =
          new EventSource(
            REALTIME_URL,
            {
              withCredentials: true,
            }
          );

        source.onopen = () => {
          if (!stopped) {
            setRealtimeConnected(true);
          }
        };

        /**
         * Standard SSE message.
         */
        source.onmessage = (
          event
        ) => {
          if (stopped) {
            return;
          }

          try {
            const parsed =
              JSON.parse(
                event.data
              ) as RealtimeNotification;

            const notification =
              buildRealtimeNotification(
                parsed
              );

            setNotifs(
              (current) => {
                const exists =
                  current.some(
                    (item) =>
                      isSameNotification(
                        item,
                        notification
                      )
                  );

                if (exists) {
                  return current;
                }

                return [
                  notification,
                  ...current,
                ];
              }
            );
          } catch (error) {
            console.error(
              'notifications: invalid realtime event',
              error
            );
          }
        };

        /**
         * Explicit payment event.
         *
         * This supports a backend that sends:
         *
         * event: PAYMENT_RECEIVED
         * data: {...}
         */
        source.addEventListener(
          'PAYMENT_RECEIVED',
          (
            event
          ) => {
            if (stopped) {
              return;
            }

            try {
              const messageEvent =
                event as MessageEvent;

              const parsed =
                JSON.parse(
                  messageEvent.data
                ) as RealtimeNotification;

              const notification =
                buildRealtimeNotification({
                  ...parsed,
                  type:
                    parsed.type ||
                    'PAYMENT_RECEIVED',
                });

              setNotifs(
                (current) => {
                  const exists =
                    current.some(
                      (item) =>
                        isSameNotification(
                          item,
                          notification
                        )
                    );

                  if (exists) {
                    return current;
                  }

                  return [
                    notification,
                    ...current,
                  ];
                }
              );
            } catch (error) {
              console.error(
                'notifications: invalid PAYMENT_RECEIVED event',
                error
              );
            }
          }
        );

        source.onerror = (
          error
        ) => {
          console.error(
            'notifications: realtime connection error',
            error
          );

          setRealtimeConnected(
            false
          );

          if (source) {
            source.close();
            source = null;
          }

          if (
            !stopped &&
            !reconnectTimer
          ) {
            reconnectTimer =
              setTimeout(
                () => {
                  reconnectTimer =
                    null;

                  connect();
                },
                5000
              );
          }
        };
      } catch (error) {
        console.error(
          'notifications: failed to create realtime connection',
          error
        );

        setRealtimeConnected(
          false
        );

        if (
          !stopped &&
          !reconnectTimer
        ) {
          reconnectTimer =
            setTimeout(
              () => {
                reconnectTimer =
                  null;

                connect();
              },
              5000
            );
        }
      }
    };

    connect();

    return () => {
      stopped = true;

      setRealtimeConnected(
        false
      );

      if (reconnectTimer) {
        clearTimeout(
          reconnectTimer
        );

        reconnectTimer = null;
      }

      if (source) {
        source.close();
        source = null;
      }
    };
  }, []);

  /**
   * ============================================================
   * FILTERED NOTIFICATIONS
   * ============================================================
   */
  const filtered =
    useMemo(
      () =>
        filter === 'all'
          ? notifs
          : notifs.filter(
              (n) =>
                n.type === filter
            ),
      [
        filter,
        notifs,
      ]
    );

  /**
   * ============================================================
   * UNREAD COUNT
   * ============================================================
   */
  const unreadCount =
    notifs.filter(
      (n) =>
        n.realId !== undefined &&
        !n.read
    ).length;

  const ICON = {
    danger: '🔴',
    warning: '⚠️',
    success: '✅',
    info: '💡',
  };

  const BG = {
    danger:
      'bg-red-50 border-red-100',

    warning:
      'bg-yellow-50 border-yellow-100',

    success:
      'bg-green-50 border-green-100',

    info:
      'bg-blue-50 border-blue-100',
  };

  const TXT = {
    danger:
      'text-red-700',

    warning:
      'text-yellow-700',

    success:
      'text-green-700',

    info:
      'text-blue-700',
  };

  const BTN = {
    danger:
      'bg-red-100 text-red-700 hover:bg-red-200',

    warning:
      'bg-yellow-100 text-yellow-700 hover:bg-yellow-200',

    success:
      'bg-green-100 text-green-700 hover:bg-green-200',

    info:
      'bg-blue-100 text-blue-700 hover:bg-blue-200',
  };

  if (loading) {
    return <PageSpinner />;
  }

  return (
    <div className="space-y-5 max-w-3xl">

      {/* ========================================================
          HEADER
      ======================================================== */}
      <div className="flex items-start justify-between gap-4">

        <div>
          <div className="flex items-center gap-2">

            <h1 className="text-xl font-bold text-gray-900">
              Notifications
            </h1>

            {unreadCount > 0 && (
              <span className="inline-flex items-center justify-center min-w-6 h-6 px-2 rounded-full bg-red-500 text-white text-xs font-bold">
                {unreadCount}
              </span>
            )}

          </div>

          <p className="text-sm text-gray-500">
            {notifs.length} alerts for your portfolio
          </p>
        </div>

        <div className="flex items-center gap-2 text-xs">

          <span
            className={`w-2 h-2 rounded-full ${
              realtimeConnected
                ? 'bg-green-500'
                : 'bg-gray-300'
            }`}
          />

          <span className="text-gray-500">
            {realtimeConnected
              ? 'Live'
              : 'Connecting...'}
          </span>

        </div>

      </div>

      {/* ========================================================
          FILTERS
      ======================================================== */}
      <div className="flex gap-1 bg-gray-100 p-1 rounded-xl w-fit flex-wrap">

        {(
          [
            'all',
            'danger',
            'warning',
            'success',
            'info',
          ] as const
        ).map((f) => (

          <button
            key={f}
            type="button"
            onClick={() =>
              setFilter(f)
            }
            className={`px-3 py-1.5 rounded-lg text-xs font-medium capitalize transition ${
              filter === f
                ? 'bg-white shadow text-green-600'
                : 'text-gray-500 hover:text-gray-800'
            }`}
          >
            {f === 'all'
              ? 'All'
              : `${ICON[f]} ${f
                  .charAt(0)
                  .toUpperCase()}${f.slice(1)}`}
          </button>

        ))}

      </div>

      {/* ========================================================
          NOTIFICATIONS
      ======================================================== */}
      <div className="space-y-3">

        {filtered.length === 0 && (
          <div className="text-center py-16 bg-white rounded-2xl border border-gray-100">

            <p className="text-3xl mb-3">
              🔔
            </p>

            <p className="text-gray-500 font-medium">
              No notifications
            </p>

            <p className="text-gray-400 text-sm mt-1">
              All caught up!
            </p>

          </div>
        )}

        {filtered.map((n) => (

          <div
            key={n.id}
            className={`rounded-2xl border p-5 ${
              BG[n.type]
            } ${
              n.realId &&
              !n.read
                ? 'ring-2 ring-offset-1 ring-teal-300'
                : ''
            }`}
          >

            <div className="flex items-start gap-4">

              <span className="text-2xl flex-shrink-0">
                {ICON[n.type]}
              </span>

              <div className="flex-1 min-w-0">

                <div className="flex items-center justify-between gap-2 mb-1">

                  <div className="flex items-center gap-2">

                    <p
                      className={`font-semibold text-sm ${TXT[n.type]}`}
                    >
                      {n.title}
                    </p>

                    {n.realId &&
                      !n.read && (
                        <span className="text-[10px] font-bold uppercase tracking-wide text-teal-600">
                          New
                        </span>
                      )}

                  </div>

                  <span className="text-xs text-gray-400 flex-shrink-0">
                    {n.time}
                  </span>

                </div>

                <p className="text-sm text-gray-600 leading-relaxed">
                  {n.message}
                </p>

                {n.link && (
                  <Link
                    href={n.link}
                    onClick={() => {
                      if (
                        n.realId &&
                        !n.read
                      ) {
                        markNotificationRead(
                          n.realId
                        )
                          .then(() => {
                            setNotifs(
                              (current) =>
                                current.map(
                                  (item) =>
                                    item.id ===
                                    n.id
                                      ? {
                                          ...item,
                                          read: true,
                                        }
                                      : item
                                )
                            );
                          })
                          .catch(
                            (error) => {
                              console.error(
                                'notifications: mark read failed',
                                error
                              );
                            }
                          );
                      }
                    }}
                    className={`inline-block mt-3 text-xs font-semibold px-3 py-1.5 rounded-lg transition ${BTN[n.type]}`}
                  >
                    Take action →
                  </Link>
                )}

              </div>

            </div>

          </div>

        ))}

      </div>

    </div>
  );
}