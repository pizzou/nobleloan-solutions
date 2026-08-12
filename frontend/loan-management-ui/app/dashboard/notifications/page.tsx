'use client';

import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';

import Link from 'next/link';

import {
  getLoans,
} from '../../../services/loanService';

import {
  getOverduePayments,
} from '../../../services/paymentService';

import {
  getDashboardStats,
} from '../../../services/dashboardService';

import {
  getMyNotifications,
  markNotificationRead,
} from '../../../services/notificationsService';

import {
  connectToPaymentNotifications,
  disconnectFromPaymentNotifications,
  PaymentNotification,
} from '../../../services/realtimeNotificationService';

import {
  Loan,
  Payment,
  DashboardStats,
} from '../../../types/index';

import {
  PageSpinner,
} from '../../../components/ui/Skeleton';

interface Notif {
  id: string;

  type:
    | 'danger'
    | 'warning'
    | 'success'
    | 'info';

  title: string;

  message: string;

  link?: string;

  time: string;

  realId?: number;

  read?: boolean;

  realtime?: boolean;

  paymentId?: number;

  transactionId?: string;
}

type NotificationFilter =
  | 'all'
  | 'danger'
  | 'warning'
  | 'success'
  | 'info';

export default function NotificationsPage() {
  const [notifs, setNotifs] =
    useState<Notif[]>([]);

  const [loading, setLoading] =
    useState(true);

  const [filter, setFilter] =
    useState<NotificationFilter>('all');

  const [realtimeConnected, setRealtimeConnected] =
    useState(false);

  const [realtimeError, setRealtimeError] =
    useState(false);

  /*
   * Prevent duplicate realtime payment notifications.
   */
  const paymentIdsRef =
    useRef<Set<number>>(
      new Set()
    );

  const transactionIdsRef =
    useRef<Set<string>>(
      new Set()
    );

  /*
   * ------------------------------------------------------------
   * ORGANIZATION ID
   * ------------------------------------------------------------
   *
   * The organization ID must come from the authenticated
   * organization/user context used by the existing application.
   *
   * We first check localStorage because many existing tenant
   * applications already persist the authenticated organization.
   */
  const getOrganizationId =
    useCallback((): number | null => {
      if (typeof window === 'undefined') {
        return null;
      }

      const possibleKeys = [
        'organizationId',
        'organization_id',
        'orgId',
        'org_id',
      ];

      for (const key of possibleKeys) {
        const value =
          window.localStorage.getItem(
            key
          );

        if (!value) {
          continue;
        }

        const parsed =
          Number(value);

        if (
          Number.isInteger(parsed) &&
          parsed > 0
        ) {
          return parsed;
        }
      }

      /*
       * Some applications store the authenticated
       * user object.
       */
      const possibleUserKeys = [
        'user',
        'currentUser',
        'authUser',
        'auth_user',
      ];

      for (const key of possibleUserKeys) {
        try {
          const raw =
            window.localStorage.getItem(
              key
            );

          if (!raw) {
            continue;
          }

          const user =
            JSON.parse(raw);

          const organizationId =
            Number(
              user?.organizationId ??
              user?.organization_id ??
              user?.organization?.id ??
              user?.organization?.organizationId
            );

          if (
            Number.isInteger(
              organizationId
            ) &&
            organizationId > 0
          ) {
            return organizationId;
          }
        } catch {
          /*
           * Ignore malformed localStorage
           * values and continue checking.
           */
        }
      }

      return null;
    }, []);

  /*
   * ------------------------------------------------------------
   * REALTIME PAYMENT HANDLER
   * ------------------------------------------------------------
   */
  const handleRealtimePayment =
    useCallback(
      (
        payment: PaymentNotification
      ) => {
        const paymentId =
          Number(
            payment.paymentId
          );

        const transactionId =
          payment.transactionId
            ?.trim();

        /*
         * Strong duplicate protection.
         *
         * A provider can retry a webhook and STOMP can also
         * deliver messages again after reconnecting.
         */
        if (
          Number.isInteger(
            paymentId
          ) &&
          paymentId > 0
        ) {
          if (
            paymentIdsRef.current.has(
              paymentId
            )
          ) {
            return;
          }

          paymentIdsRef.current.add(
            paymentId
          );
        }

        if (transactionId) {
          if (
            transactionIdsRef.current.has(
              transactionId
            )
          ) {
            return;
          }

          transactionIdsRef.current.add(
            transactionId
          );
        }

        const amount =
          Number(
            payment.amount
          );

        const outstandingBalance =
          Number(
            payment.outstandingBalance ??
            0
          );

        const amountText =
          Number.isFinite(amount)
            ? `RWF ${amount.toLocaleString(
                undefined,
                {
                  minimumFractionDigits: 2,
                  maximumFractionDigits: 2,
                }
              )}`
            : `RWF ${payment.amount}`;

        const balanceText =
          Number.isFinite(
            outstandingBalance
          )
            ? `RWF ${outstandingBalance.toLocaleString(
                undefined,
                {
                  minimumFractionDigits: 2,
                  maximumFractionDigits: 2,
                }
              )}`
            : 'RWF 0.00';

        const borrower =
          payment.borrowerName ||
          'Borrower';

        const loanNumber =
          payment.loanNumber
            ? ` (${payment.loanNumber})`
            : '';

        const newNotification: Notif = {
          id:
            paymentId > 0
              ? `realtime-payment-${paymentId}`
              : `realtime-payment-${Date.now()}`,

          type: 'success',

          title: 'Payment Received',

          message:
            `${borrower} made a payment of ` +
            `${amountText}${loanNumber}. ` +
            `Outstanding balance: ${balanceText}.`,

          link:
            payment.loanId
              ? `/dashboard/loans/${payment.loanId}`
              : '/dashboard/payments',

          time: 'Just now',

          realtime: true,

          paymentId:
            paymentId > 0
              ? paymentId
              : undefined,

          transactionId,
        };

        /*
         * Put the newest notification at the top.
         */
        setNotifs(
          (current) => [
            newNotification,
            ...current.filter(
              (item) =>
                item.id !==
                newNotification.id
            ),
          ]
        );

        /*
         * Browser notification.
         *
         * This is optional and only works if the user has
         * granted permission.
         */
        try {
          if (
            typeof window !== 'undefined' &&
            'Notification' in window &&
            Notification.permission ===
              'granted'
          ) {
            new Notification(
              'Payment Received',
              {
                body:
                  `${borrower} paid ${amountText}. ` +
                  `Balance: ${balanceText}`,
              }
            );
          }
        } catch {
          /*
           * Browser notifications must never
           * break the dashboard.
           */
        }
      },
      []
    );

  /*
   * ------------------------------------------------------------
   * LOAD EXISTING NOTIFICATIONS
   * ------------------------------------------------------------
   */
  useEffect(() => {
    let mounted = true;

    setLoading(true);

    Promise.all([
      getLoans().catch(
        (error) => {
          console.error(
            'notifications: getLoans failed',
            error
          );

          return [];
        }
      ),

      getOverduePayments().catch(
        (error) => {
          console.error(
            'notifications: getOverduePayments failed',
            error
          );

          return [];
        }
      ),

      getDashboardStats().catch(
        (error) => {
          console.error(
            'notifications: getDashboardStats failed',
            error
          );

          return null;
        }
      ),

      getMyNotifications().catch(
        (error) => {
          console.error(
            'notifications: getMyNotifications failed',
            error
          );

          return [];
        }
      ),
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
            stats as
              | DashboardStats
              | null;

          const realList =
            Array.isArray(real)
              ? (real as any[])
              : [];

          const n: Notif[] = [];

          /*
           * Existing backend-persisted notifications.
           *
           * These are the notifications that were already
           * working for loan creation and other events.
           */
          realList.forEach(
            (r) => {
              if (!r?.id) {
                return;
              }

              n.push({
                id: `real-${r.id}`,

                realId: Number(
                  r.id
                ),

                read:
                  Boolean(r.read),

                type:
                  (
                    r.type as
                      | Notif['type']
                      | undefined
                  ) || 'info',

                title:
                  r.title ||
                  'Notification',

                message:
                  r.message ||
                  '',

                link:
                  r.link ||
                  undefined,

                time:
                  r.createdAt
                    ? new Date(
                        r.createdAt
                      ).toLocaleString()
                    : '',
              });
            }
          );

          /*
           * Portfolio-derived notifications.
           */
          try {
            if (o.length > 0) {
              n.push({
                id: 'ov',

                type: 'danger',

                title:
                  `${o.length} Overdue Payment` +
                  `${
                    o.length > 1
                      ? 's'
                      : ''
                  }`,

                message:
                  `${o.length} payment` +
                  `${
                    o.length > 1
                      ? 's are'
                      : ' is'
                  } past due. Penalties accruing daily.`,

                link:
                  '/dashboard/payments',

                time: 'Now',
              });
            }

            const pending =
              l.filter(
                (x) =>
                  x.status ===
                  'PENDING'
              );

            if (
              pending.length > 0
            ) {
              n.push({
                id: 'pend',

                type: 'warning',

                title:
                  `${pending.length} Loan` +
                  `${
                    pending.length >
                    1
                      ? 's'
                      : ''
                  } Awaiting Approval`,

                message:
                  `${pending.length} application` +
                  `${
                    pending.length >
                    1
                      ? 's need'
                      : ' needs'
                  } your review.`,

                link:
                  '/dashboard/approvals',

                time: 'Today',
              });
            }

            const highRisk =
              l.filter(
                (x) =>
                  x.riskCategory ===
                    'HIGH' ||
                  x.riskCategory ===
                    'CRITICAL'
              );

            if (
              highRisk.length > 0
            ) {
              n.push({
                id: 'hr',

                type: 'warning',

                title:
                  `${highRisk.length} High-Risk Loan` +
                  `${
                    highRisk.length >
                    1
                      ? 's'
                      : ''
                  }`,

                message:
                  `${highRisk.length} loan` +
                  `${
                    highRisk.length >
                    1
                      ? 's are'
                      : ' is'
                  } rated HIGH or CRITICAL risk. Review collateral.`,

                link:
                  '/dashboard/loans',

                time: 'Today',
              });
            }

            const rate =
              s &&
              Number(
                s.totalDisbursed
              ) > 0
                ? (
                    Number(
                      s.totalCollected
                    ) /
                    Number(
                      s.totalDisbursed
                    )
                  ) *
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

                message:
                  `Portfolio collection rate is ${rate.toFixed(
                    0
                  )}% — excellent performance!`,

                time: 'This week',
              });
            } else if (
              s &&
              rate < 50 &&
              Number(
                s.totalDisbursed
              ) > 0
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

                time: 'This week',
              });
            }

            if (
              s &&
              Number(
                s.completedLoans
              ) > 0
            ) {
              n.push({
                id: 'closed',

                type: 'success',

                title:
                  `${s.completedLoans} Loan` +
                  `${
                    s.completedLoans >
                    1
                      ? 's'
                      : ''
                  } Fully Repaid`,

                message:
                  `${s.completedLoans} loan` +
                  `${
                    s.completedLoans >
                    1
                      ? 's have'
                      : ' has'
                  } been fully repaid. Great portfolio health!`,

                link:
                  '/dashboard/loans',

                time: 'This month',
              });
            }

            if (s) {
              n.push({
                id: 'summary',

                type: 'info',

                title:
                  'Portfolio Summary',

                message:
                  `${s.totalBorrowers} borrowers · ` +
                  `${s.activeLoans} active loans · ` +
                  `RWF ${Number(
                    s.totalDisbursed
                  ).toLocaleString()} disbursed.`,

                link:
                  '/dashboard/reports',

                time: 'Today',
              });
            }
          } catch (error) {
            console.error(
              'notifications: failed to build portfolio-derived alerts',
              error
            );
          }

          setNotifs(n);
        }
      )
      .finally(() => {
        if (mounted) {
          setLoading(false);
        }
      });

    return () => {
      mounted = false;
    };
  }, []);

  /*
   * ------------------------------------------------------------
   * CONNECT REALTIME PAYMENT WEBSOCKET
   * ------------------------------------------------------------
   */
  useEffect(() => {
    if (loading) {
      return;
    }

    const organizationId =
      getOrganizationId();

    if (!organizationId) {
      console.error(
        '[REALTIME] No organization ID found. Cannot subscribe to payment notifications.'
      );

      setRealtimeError(true);

      return;
    }

    console.log(
      `[REALTIME] Starting payment notification connection. Organization=${organizationId}`
    );

    const cleanup =
      connectToPaymentNotifications(
        organizationId,
        {
          onPaymentReceived:
            handleRealtimePayment,

          onConnected: () => {
            console.log(
              '[REALTIME] Payment notification connection established.'
            );

            setRealtimeConnected(
              true
            );

            setRealtimeError(
              false
            );
          },

          onDisconnected: () => {
            console.warn(
              '[REALTIME] Payment notification connection disconnected.'
            );

            setRealtimeConnected(
              false
            );
          },

          onError: (error) => {
            console.error(
              '[REALTIME] Payment notification connection error:',
              error
            );

            setRealtimeConnected(
              false
            );

            setRealtimeError(
              true
            );
          },
        }
      );

    return () => {
      cleanup();

      setRealtimeConnected(
        false
      );
    };
  }, [
    loading,
    getOrganizationId,
    handleRealtimePayment,
  ]);

  /*
   * ------------------------------------------------------------
   * REQUEST BROWSER NOTIFICATION PERMISSION
   * ------------------------------------------------------------
   */
  useEffect(() => {
    if (
      typeof window === 'undefined' ||
      !('Notification' in window)
    ) {
      return;
    }

    if (
      Notification.permission ===
      'default'
    ) {
      /*
       * Do not automatically request permission on page load.
       * The browser may block this because it is not user initiated.
       */
    }
  }, []);

  const filtered =
    useMemo(() => {
      if (
        filter === 'all'
      ) {
        return notifs;
      }

      return notifs.filter(
        (notification) =>
          notification.type ===
          filter
      );
    }, [
      filter,
      notifs,
    ]);

  const unreadCount =
    useMemo(
      () =>
        notifs.filter(
          (notification) =>
            notification.realId &&
            !notification.read
        ).length,
      [notifs]
    );

  const ICON = {
    danger: '🔴',
    warning: '⚠️',
    success: '✅',
    info: '💡',
  } as const;

  const BG = {
    danger:
      'bg-red-50 border-red-100',

    warning:
      'bg-yellow-50 border-yellow-100',

    success:
      'bg-green-50 border-green-100',

    info:
      'bg-blue-50 border-blue-100',
  } as const;

  const TXT = {
    danger:
      'text-red-700',

    warning:
      'text-yellow-700',

    success:
      'text-green-700',

    info:
      'text-blue-700',
  } as const;

  const BTN = {
    danger:
      'bg-red-100 text-red-700 hover:bg-red-200',

    warning:
      'bg-yellow-100 text-yellow-700 hover:bg-yellow-200',

    success:
      'bg-green-100 text-green-700 hover:bg-green-200',

    info:
      'bg-blue-100 text-blue-700 hover:bg-blue-200',
  } as const;

  if (loading) {
    return <PageSpinner />;
  }

  return (
    <div className="space-y-5 max-w-3xl">

      {/* HEADER */}
      <div>
        <div className="flex items-center justify-between gap-4">

          <div>
            <h1 className="text-xl font-bold text-gray-900">
              Notifications
            </h1>

            <p className="text-sm text-gray-500">
              {notifs.length} alerts for your portfolio
              {unreadCount > 0 &&
                ` · ${unreadCount} unread`}
            </p>
          </div>

          {/* REALTIME STATUS */}
          <div className="flex items-center gap-2 text-xs">

            {realtimeConnected ? (
              <>
                <span className="relative flex h-2.5 w-2.5">
                  <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-green-400 opacity-75" />
                  <span className="relative inline-flex h-2.5 w-2.5 rounded-full bg-green-500" />
                </span>

                <span className="font-medium text-green-700">
                  Live
                </span>
              </>
            ) : (
              <>
                <span className="h-2.5 w-2.5 rounded-full bg-yellow-400" />

                <span className="font-medium text-yellow-700">
                  Connecting...
                </span>
              </>
            )}
          </div>
        </div>
      </div>

      {/* REALTIME ERROR */}
      {realtimeError &&
        !realtimeConnected && (
          <div className="rounded-xl border border-yellow-200 bg-yellow-50 px-4 py-3">

            <div className="flex items-start gap-3">

              <span className="text-lg">
                ⚠️
              </span>

              <div>
                <p className="text-sm font-semibold text-yellow-800">
                  Realtime notifications unavailable
                </p>

                <p className="mt-1 text-xs text-yellow-700">
                  Existing notifications are still
                  available. The system will continue
                  trying to reconnect.
                </p>
              </div>

            </div>
          </div>
        )}

      {/* FILTER */}
      <div className="flex gap-1 bg-gray-100 p-1 rounded-xl w-fit flex-wrap">

        {(
          [
            'all',
            'danger',
            'warning',
            'success',
            'info',
          ] as const
        ).map(
          (f) => (
            <button
              key={f}
              type="button"
              onClick={() =>
                setFilter(f)
              }
              className={
                `px-3 py-1.5 rounded-lg text-xs font-medium capitalize transition ` +
                `${
                  filter === f
                    ? 'bg-white shadow text-green-600'
                    : 'text-gray-500 hover:text-gray-800'
                }`
              }
            >
              {f === 'all'
                ? 'All'
                : `${ICON[f]} ${f.charAt(
                    0
                  ).toUpperCase()}${f.slice(
                    1
                  )}`}
            </button>
          )
        )}

      </div>

      {/* NOTIFICATIONS */}
      <div className="space-y-3">

        {filtered.length ===
          0 && (
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

        {filtered.map(
          (notification) => (
            <div
              key={
                notification.id
              }
              className={
                `rounded-2xl border p-5 ` +
                `${BG[notification.type]} ` +
                `${
                  notification.realId &&
                  !notification.read
                    ? 'ring-2 ring-offset-1 ring-teal-300'
                    : ''
                }`
              }
            >

              <div className="flex items-start gap-4">

                <span className="text-2xl flex-shrink-0">
                  {
                    ICON[
                      notification.type
                    ]
                  }
                </span>

                <div className="flex-1 min-w-0">

                  <div className="flex items-center justify-between gap-2 mb-1">

                    <div className="flex items-center gap-2">

                      <p
                        className={
                          `font-semibold text-sm ` +
                          TXT[
                            notification.type
                          ]
                        }
                      >
                        {
                          notification.title
                        }
                      </p>

                      {notification.realtime && (
                        <span className="rounded-full bg-green-100 px-2 py-0.5 text-[10px] font-semibold text-green-700">
                          LIVE
                        </span>
                      )}

                    </div>

                    <span className="text-xs text-gray-400 flex-shrink-0">
                      {
                        notification.time
                      }
                    </span>

                  </div>

                  <p className="text-sm text-gray-600 leading-relaxed">
                    {
                      notification.message
                    }
                  </p>

                  {notification.link && (
                    <Link
                      href={
                        notification.link
                      }
                      onClick={() => {
                        if (
                          notification.realId &&
                          !notification.read
                        ) {
                          markNotificationRead(
                            notification.realId
                          ).catch(
                            () => undefined
                          );

                          setNotifs(
                            (current) =>
                              current.map(
                                (
                                  item
                                ) =>
                                  item.id ===
                                  notification.id
                                    ? {
                                        ...item,
                                        read: true,
                                      }
                                    : item
                              )
                          );
                        }
                      }}
                      className={
                        `inline-block mt-3 text-xs font-semibold px-3 py-1.5 rounded-lg transition ` +
                        BTN[
                          notification.type
                        ]
                      }
                    >
                      Take action →
                    </Link>
                  )}

                </div>

              </div>

            </div>
          )
        )}

      </div>
    </div>
  );
}