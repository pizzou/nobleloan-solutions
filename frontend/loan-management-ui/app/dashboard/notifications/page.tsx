'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';

import { getLoans } from '../../../services/loanService';
import { getOverduePayments } from '../../../services/paymentService';
import { getDashboardStats } from '../../../services/dashboardService';
import {
  getMyNotifications,
  markNotificationRead,
} from '../../../services/notificationsService';

import {
  connectToPaymentNotifications,
  PaymentNotification,
} from '../../../services/realtimeNotificationService';

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
  realtime?: boolean;
}

/**
 * Attempts to extract the organization ID from the currently
 * authenticated frontend session.
 *
 * The function checks common localStorage structures first and
 * then falls back to decoding the JWT if organizationId is present
 * inside the token.
 */
function getCurrentOrganizationId(): number | null {
  if (typeof window === 'undefined') {
    return null;
  }

  const possibleStorageKeys = [
    'user',
    'currentUser',
    'authUser',
    'auth',
    'session',
    'userData',
  ];

  for (const key of possibleStorageKeys) {
    try {
      const raw = window.localStorage.getItem(key);

      if (!raw) {
        continue;
      }

      const parsed = JSON.parse(raw);

      const possibleIds = [
        parsed?.organizationId,
        parsed?.organization?.id,
        parsed?.user?.organizationId,
        parsed?.user?.organization?.id,
        parsed?.data?.organizationId,
        parsed?.data?.organization?.id,
        parsed?.data?.user?.organizationId,
        parsed?.data?.user?.organization?.id,
      ];

      for (const value of possibleIds) {
        const id = Number(value);

        if (Number.isInteger(id) && id > 0) {
          return id;
        }
      }
    } catch {
      // Ignore invalid JSON and continue searching.
    }
  }

  /**
   * JWT fallback.
   *
   * We inspect common token keys without assuming one particular
   * authentication implementation.
   */
  const possibleTokenKeys = [
    'token',
    'accessToken',
    'access_token',
    'jwt',
    'authToken',
    'authorization',
  ];

  for (const key of possibleTokenKeys) {
    try {
      const token = window.localStorage.getItem(key);

      if (!token) {
        continue;
      }

      const cleanToken = token.replace(/^Bearer\s+/i, '').trim();

      const parts = cleanToken.split('.');

      if (parts.length !== 3) {
        continue;
      }

      const payload = parts[1];

      const normalizedPayload = payload
        .replace(/-/g, '+')
        .replace(/_/g, '/');

      const paddedPayload =
        normalizedPayload +
        '='.repeat(
          (4 - (normalizedPayload.length % 4)) % 4
        );

      const decodedPayload = atob(paddedPayload);

      const claims = JSON.parse(decodedPayload);

      const possibleIds = [
        claims?.organizationId,
        claims?.organization_id,
        claims?.orgId,
        claims?.org_id,
        claims?.organization?.id,
      ];

      for (const value of possibleIds) {
        const id = Number(value);

        if (Number.isInteger(id) && id > 0) {
          return id;
        }
      }
    } catch {
      // Ignore invalid tokens and continue.
    }
  }

  return null;
}

function formatMoney(value: number | string | undefined | null): string {
  const amount = Number(value ?? 0);

  if (!Number.isFinite(amount)) {
    return 'RWF 0';
  }

  return `RWF ${amount.toLocaleString(undefined, {
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  })}`;
}

function formatRealtimePaymentMessage(
  notification: PaymentNotification
): string {
  if (notification.message) {
    return notification.message;
  }

  const amount = formatMoney(notification.amount);

  const loanNumber =
    notification.loanNumber ||
    `Loan #${notification.loanId}`;

  const balance =
    notification.outstandingBalance !== undefined &&
    notification.outstandingBalance !== null
      ? ` Balance: ${formatMoney(
          notification.outstandingBalance
        )}.`
      : '';

  return `${amount} payment received for ${loanNumber}.${balance}`;
}

function formatRealtimeTime(
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

export default function NotificationsPage() {
  const [notifs, setNotifs] = useState<Notif[]>([]);
  const [loading, setLoading] = useState(true);

  const [filter, setFilter] = useState<
    'all' | 'danger' | 'warning' | 'success' | 'info'
  >('all');

  const [realtimeConnected, setRealtimeConnected] =
    useState(false);

  const [realtimeError, setRealtimeError] =
    useState(false);

  useEffect(() => {
    let cancelled = false;

    /**
     * ------------------------------------------------------------
     * LOAD EXISTING NOTIFICATIONS
     * ------------------------------------------------------------
     */
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
          if (cancelled) {
            return;
          }

          const l = Array.isArray(loans)
            ? (loans as Loan[])
            : [];

          const o = Array.isArray(overdue)
            ? (overdue as Payment[])
            : [];

          const s =
            stats as DashboardStats | null;

          const realList = Array.isArray(real)
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
           * ------------------------------------------------------
           * REAL BACKEND-PERSISTED NOTIFICATIONS
           * ------------------------------------------------------
           */
          realList.forEach((r) => {
            n.push({
              id: `real-${r.id}`,

              realId: r.id,

              read: Boolean(r.read),

              type:
                (r.type as Notif['type']) ||
                'info',

              title:
                r.title ||
                'Notification',

              message:
                r.message ||
                '',

              link: r.link,

              time: r.createdAt
                ? new Date(
                    r.createdAt
                  ).toLocaleString()
                : '',
            });
          });

          /**
           * ------------------------------------------------------
           * PORTFOLIO-DERIVED NOTIFICATIONS
           * ------------------------------------------------------
           */
          try {
            if (o.length > 0) {
              n.push({
                id: 'ov',

                type: 'danger',

                title: `${
                  o.length
                } Overdue Payment${
                  o.length > 1 ? 's' : ''
                }`,

                message: `${
                  o.length
                } payment${
                  o.length > 1
                    ? 's are'
                    : ' is'
                } past due. Penalties accruing daily.`,

                link: '/dashboard/payments',

                time: 'Now',
              });
            }

            const pending = l.filter(
              (x) => x.status === 'PENDING'
            );

            if (pending.length > 0) {
              n.push({
                id: 'pend',

                type: 'warning',

                title: `${
                  pending.length
                } Loan${
                  pending.length > 1
                    ? 's'
                    : ''
                } Awaiting Approval`,

                message: `${
                  pending.length
                } application${
                  pending.length > 1
                    ? 's need'
                    : ' needs'
                } your review.`,

                link: '/dashboard/approvals',

                time: 'Today',
              });
            }

            const hr = l.filter(
              (x) =>
                x.riskCategory === 'HIGH' ||
                x.riskCategory === 'CRITICAL'
            );

            if (hr.length > 0) {
              n.push({
                id: 'hr',

                type: 'warning',

                title: `${
                  hr.length
                } High-Risk Loan${
                  hr.length > 1
                    ? 's'
                    : ''
                }`,

                message: `${
                  hr.length
                } loan${
                  hr.length > 1
                    ? 's are'
                    : ' is'
                } rated HIGH or CRITICAL risk. Review collateral.`,

                link: '/dashboard/loans',

                time: 'Today',
              });
            }

            const totalDisbursed =
              Number(
                s?.totalDisbursed ?? 0
              );

            const totalCollected =
              Number(
                s?.totalCollected ?? 0
              );

            const rate =
              totalDisbursed > 0
                ? (totalCollected /
                    totalDisbursed) *
                  100
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

                message: `Portfolio collection rate is ${rate.toFixed(
                  0
                )}% — excellent performance!`,

                time: 'This week',
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

                message: `Collection rate is only ${rate.toFixed(
                  0
                )}%. Consider sending payment reminders.`,

                time: 'This week',
              });
            }

            const completedLoans =
              Number(
                s?.completedLoans ?? 0
              );

            if (
              s &&
              completedLoans > 0
            ) {
              n.push({
                id: 'closed',

                type: 'success',

                title: `${
                  completedLoans
                } Loan${
                  completedLoans > 1
                    ? 's'
                    : ''
                } Fully Repaid`,

                message: `${
                  completedLoans
                } loan${
                  completedLoans > 1
                    ? 's have'
                    : ' has'
                } been fully repaid. Great portfolio health!`,

                link:
                  '/dashboard/loans',

                time: 'This month',
              });
            }

            if (s) {
              const totalBorrowers =
                Number(
                  s.totalBorrowers ?? 0
                );

              const activeLoans =
                Number(
                  s.activeLoans ?? 0
                );

              n.push({
                id: 'summary',

                type: 'info',

                title:
                  'Portfolio Summary',

                message: `${totalBorrowers} borrowers · ${activeLoans} active loans · ${formatMoney(
                  s.totalDisbursed
                )} disbursed.`,

                link:
                  '/dashboard/reports',

                time: 'Today',
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
        if (!cancelled) {
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, []);

  /**
   * ============================================================
   * REALTIME PAYMENT NOTIFICATIONS
   * ============================================================
   *
   * Backend:
   *
   * WebSocket:
   *     /ws
   *
   * STOMP topic:
   *     /topic/organization/{organizationId}/payments
   *
   * The backend RealtimePaymentNotificationService publishes
   * payment notifications here after the payment transaction
   * commits.
   */
  useEffect(() => {
    if (typeof window === 'undefined') {
      return;
    }

    const organizationId =
      getCurrentOrganizationId();

    if (!organizationId) {
      console.error(
        '[REALTIME NOTIFICATIONS] Unable to determine organizationId. ' +
          'The dashboard will continue using persisted notifications.'
      );

      setRealtimeError(true);

      return;
    }

    let cleanup: (() => void) | null =
      null;

    try {
      cleanup =
        connectToPaymentNotifications(
          organizationId,
          {
            onConnected: () => {
              if (process.env.NODE_ENV === 'development') {
                console.log(
                  `[REALTIME NOTIFICATIONS] Connected for organization ${organizationId}.`
                );
              }

              setRealtimeConnected(
                true
              );

              setRealtimeError(false);
            },

            onDisconnected: () => {
              if (process.env.NODE_ENV === 'development') {
                console.log(
                  '[REALTIME NOTIFICATIONS] Disconnected.'
                );
              }

              setRealtimeConnected(
                false
              );
            },

            onError: (error) => {
              console.error(
                '[REALTIME NOTIFICATIONS] WebSocket/STOMP error:',
                error
              );

              setRealtimeConnected(
                false
              );

              setRealtimeError(true);
            },

            onPaymentReceived: (
              notification
            ) => {
              if (
                notification.organizationId !==
                organizationId
              ) {
                return;
              }

              /**
               * Create the notification that appears immediately
               * in the dashboard.
               */
              const realtimeNotification: Notif =
                {
                  id: `realtime-payment-${notification.paymentId}-${notification.transactionId || Date.now()}`,

                  type: 'success',

                  title:
                    'Payment Received',

                  message:
                    formatRealtimePaymentMessage(
                      notification
                    ),

                  link: `/dashboard/loans/${notification.loanId}`,

                  time:
                    formatRealtimeTime(
                      notification.createdAt
                    ),

                  read: false,

                  realtime: true,
                };

              /**
               * Put newest notification at the top.
               *
               * We also prevent duplicate payment notifications
               * in case a provider retries or the connection
               * delivers the same event more than once.
               */
              setNotifs(
                (current) => {
                  const duplicate =
                    current.some(
                      (existing) =>
                        existing.id ===
                        realtimeNotification.id
                    );

                  if (duplicate) {
                    return current;
                  }

                  return [
                    realtimeNotification,
                    ...current,
                  ];
                }
              );
            },
          }
        );
    } catch (error) {
      console.error(
        '[REALTIME NOTIFICATIONS] Failed to initialize:',
        error
      );

      setRealtimeConnected(
        false
      );

      setRealtimeError(true);
    }

    return () => {
      if (cleanup) {
        cleanup();
      }
    };
  }, []);

  /**
   * ============================================================
   * FILTERING
   * ============================================================
   */
  const filtered =
    filter === 'all'
      ? notifs
      : notifs.filter(
          (n) => n.type === filter
        );

  /**
   * ============================================================
   * UI CONFIGURATION
   * ============================================================
   */
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

  /**
   * ============================================================
   * LOADING
   * ============================================================
   */
  if (loading) {
    return <PageSpinner />;
  }

  /**
   * ============================================================
   * PAGE
   * ============================================================
   */
  return (
    <div className="space-y-5 max-w-3xl">

      {/* ======================================================
          HEADER
      ====================================================== */}
      <div className="flex items-start justify-between gap-4">

        <div>
          <h1 className="text-xl font-bold text-gray-900">
            Notifications
          </h1>

          <p className="text-sm text-gray-500">
            {notifs.length} alerts for your portfolio
          </p>
        </div>

        {/* Realtime connection indicator */}
        <div className="flex items-center gap-2 text-xs">

          <span
            className={`h-2.5 w-2.5 rounded-full ${
              realtimeConnected
                ? 'bg-green-500'
                : 'bg-gray-300'
            }`}
          />

          <span className="text-gray-500">
            {realtimeConnected
              ? 'Live'
              : 'Offline'}
          </span>

        </div>
      </div>

      {/* ======================================================
          REALTIME WARNING
      ====================================================== */}
      {realtimeError && (
        <div className="rounded-xl border border-yellow-200 bg-yellow-50 px-4 py-3">

          <div className="flex items-start gap-3">

            <span className="text-lg">
              ⚠️
            </span>

            <div>
              <p className="text-sm font-semibold text-yellow-800">
                Live notifications unavailable
              </p>

              <p className="text-xs text-yellow-700 mt-1">
                Existing notifications are still available.
                New payment notifications may require a page refresh.
              </p>
            </div>

          </div>

        </div>
      )}

      {/* ======================================================
          FILTERS
      ====================================================== */}
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
              : `${ICON[f]} ${
                  f.charAt(0).toUpperCase() +
                  f.slice(1)
                }`}
          </button>

        ))}

      </div>

      {/* ======================================================
          NOTIFICATIONS
      ====================================================== */}
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
            className={`rounded-2xl border p-5 transition ${
              BG[n.type]
            } ${
              n.realId &&
              !n.read
                ? 'ring-2 ring-offset-1 ring-teal-300'
                : ''
            } ${
              n.realtime
                ? 'ring-2 ring-offset-1 ring-green-300 shadow-sm'
                : ''
            }`}
          >

            <div className="flex items-start gap-4">

              {/* Icon */}
              <span className="text-2xl flex-shrink-0">
                {ICON[n.type]}
              </span>

              <div className="flex-1 min-w-0">

                {/* Title + time */}
                <div className="flex items-center justify-between gap-2 mb-1">

                  <div className="flex items-center gap-2 min-w-0">

                    <p
                      className={`font-semibold text-sm ${
                        TXT[n.type]
                      }`}
                    >
                      {n.title}
                    </p>

                    {n.realtime && (
                      <span className="text-[10px] font-semibold uppercase tracking-wide px-2 py-0.5 rounded-full bg-green-100 text-green-700">
                        Live
                      </span>
                    )}

                    {!n.read &&
                      n.realId && (
                        <span className="text-[10px] font-semibold uppercase tracking-wide px-2 py-0.5 rounded-full bg-teal-100 text-teal-700">
                          New
                        </span>
                      )}

                  </div>

                  <span className="text-xs text-gray-400 flex-shrink-0">
                    {n.time}
                  </span>

                </div>

                {/* Message */}
                <p className="text-sm text-gray-600 leading-relaxed">
                  {n.message}
                </p>

                {/* Action */}
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
                        ).catch((error) => {
                          console.error(
                            'notifications: failed to mark notification read',
                            error
                          );
                        });

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
                      }
                    }}
                    className={`inline-block mt-3 text-xs font-semibold px-3 py-1.5 rounded-lg transition ${
                      BTN[n.type]
                    }`}
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