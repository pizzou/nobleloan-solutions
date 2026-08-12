'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
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

  createdAt?: string;

  source?: 'backend' | 'portfolio' | 'realtime-payment';
}

interface PaymentNotificationPayload {
  organizationId?: number | string | null;

  loanId?: number | string | null;

  paymentId?: number | string | null;

  amount?: number | string | null;

  transactionId?: string | null;

  paymentMethod?: string | null;

  status?: string | null;

  message?: string | null;

  createdAt?: string | null;

  timestamp?: string | null;
}

interface StoredNotification {
  id: number | string;

  type?: string | null;

  title?: string | null;

  message?: string | null;

  link?: string | null;

  read?: boolean | null;

  createdAt?: string | null;
}

interface OrganizationContext {
  organizationId: number | null;
}

/**
 * Production WebSocket/STOMP imports.
 *
 * Required package:
 *
 * npm install @stomp/stompjs sockjs-client
 *
 * If your backend endpoint is a normal WebSocket endpoint
 * exactly as configured in WebSocketConfig (/ws), the native
 * WebSocket transport is sufficient and avoids requiring SockJS.
 */
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';


/**
 * --------------------------------------------------------------------------
 * API BASE URL
 * --------------------------------------------------------------------------
 *
 * The frontend should already have the backend URL configured somewhere
 * in your project.
 *
 * This helper intentionally supports the common NEXT_PUBLIC_API_URL setup.
 */
function getApiBaseUrl(): string {
  const configured =
    process.env.NEXT_PUBLIC_API_URL ||
    process.env.NEXT_PUBLIC_API_BASE_URL ||
    '';

  return configured.replace(/\/+$/, '');
}


/**
 * --------------------------------------------------------------------------
 * ORGANIZATION ID
 * --------------------------------------------------------------------------
 *
 * Your backend publishes:
 *
 * /topic/organization/{organizationId}/payments
 *
 * Therefore the frontend MUST know the currently authenticated
 * organization's ID.
 *
 * This function checks several common locations without changing
 * your authentication architecture.
 *
 * IMPORTANT:
 * If your project already has a tenant/auth helper that exposes
 * organizationId, replace this function with that helper.
 */
function getOrganizationContext(): OrganizationContext {
  if (typeof window === 'undefined') {
    return {
      organizationId: null,
    };
  }

  const possibleKeys = [
    'organizationId',
    'organization_id',
    'orgId',
    'org_id',
  ];

  for (const key of possibleKeys) {
    const value = window.localStorage.getItem(key);

    if (value !== null && value.trim() !== '') {
      const parsed = Number(value);

      if (
        Number.isFinite(parsed) &&
        parsed > 0
      ) {
        return {
          organizationId: parsed,
        };
      }
    }
  }

  /**
   * Try authenticated user objects commonly stored by the frontend.
   */
  const possibleUserKeys = [
    'user',
    'currentUser',
    'authUser',
    'userData',
    'auth',
  ];

  for (const key of possibleUserKeys) {
    const raw =
      window.localStorage.getItem(key);

    if (!raw) {
      continue;
    }

    try {
      const parsed = JSON.parse(raw);

      const candidate =
        parsed?.organizationId ??
        parsed?.organization_id ??
        parsed?.organization?.id ??
        parsed?.user?.organizationId ??
        parsed?.user?.organization_id ??
        parsed?.data?.organizationId ??
        parsed?.data?.organization_id;

      const organizationId =
        Number(candidate);

      if (
        Number.isFinite(organizationId) &&
        organizationId > 0
      ) {
        return {
          organizationId,
        };
      }
    } catch {
      /**
       * Ignore invalid localStorage JSON.
       */
    }
  }

  return {
    organizationId: null,
  };
}


/**
 * --------------------------------------------------------------------------
 * NORMALIZATION HELPERS
 * --------------------------------------------------------------------------
 */

function toNumber(
  value: unknown
): number | null {
  if (
    value === null ||
    value === undefined ||
    value === ''
  ) {
    return null;
  }

  const parsed = Number(value);

  return Number.isFinite(parsed)
    ? parsed
    : null;
}


function formatCurrency(
  value: unknown
): string {
  const amount =
    toNumber(value);

  if (amount === null) {
    return 'RWF 0.00';
  }

  return `RWF ${amount.toLocaleString(
    'en-RW',
    {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }
  )}`;
}


function formatNotificationTime(
  value?: string | null
): string {
  if (!value) {
    return 'Just now';
  }

  const date =
    new Date(value);

  if (
    Number.isNaN(
      date.getTime()
    )
  ) {
    return 'Just now';
  }

  const diff =
    Date.now() -
    date.getTime();

  const seconds =
    Math.floor(
      diff / 1000
    );

  if (seconds < 10) {
    return 'Just now';
  }

  if (seconds < 60) {
    return `${seconds}s ago`;
  }

  const minutes =
    Math.floor(
      seconds / 60
    );

  if (minutes < 60) {
    return `${minutes}m ago`;
  }

  const hours =
    Math.floor(
      minutes / 60
    );

  if (hours < 24) {
    return `${hours}h ago`;
  }

  const days =
    Math.floor(
      hours / 24
    );

  if (days < 7) {
    return `${days}d ago`;
  }

  return date.toLocaleString();
}


function normalizeNotificationType(
  type?: string | null
): Notif['type'] {
  const normalized =
    String(type || '')
      .trim()
      .toUpperCase();

  if (
    normalized.includes('PAYMENT') ||
    normalized.includes('SUCCESS') ||
    normalized === 'SUCCESS'
  ) {
    return 'success';
  }

  if (
    normalized.includes('DANGER') ||
    normalized.includes('OVERDUE') ||
    normalized.includes('FAILED')
  ) {
    return 'danger';
  }

  if (
    normalized.includes('WARNING') ||
    normalized.includes('PENDING') ||
    normalized.includes('RISK')
  ) {
    return 'warning';
  }

  return 'info';
}


/**
 * --------------------------------------------------------------------------
 * PAYMENT NOTIFICATION NORMALIZATION
 * --------------------------------------------------------------------------
 *
 * Backend PaymentNotification DTO is converted into the same Notif shape
 * used by this page.
 */
function buildRealtimePaymentNotification(
  payload: PaymentNotificationPayload
): Notif | null {
  const loanId =
    toNumber(
      payload.loanId
    );

  const paymentId =
    toNumber(
      payload.paymentId
    );

  const amount =
    toNumber(
      payload.amount
    );

  const transactionId =
    payload.transactionId ||
    '';

  /**
   * A payment notification without a loan or payment ID is not safe
   * to display as a production payment event.
   */
  if (
    loanId === null &&
    paymentId === null
  ) {
    return null;
  }

  const timestamp =
    payload.createdAt ||
    payload.timestamp ||
    new Date().toISOString();

  const id =
    paymentId !== null
      ? `payment-${paymentId}`
      : `payment-${transactionId || loanId}-${timestamp}`;

  const paymentMessage =
    payload.message ||
    `Payment of ${formatCurrency(
      amount
    )} received successfully for loan #${loanId ?? '—'}.`;

  return {
    id,

    type: 'success',

    title: 'Payment Received',

    message:
      paymentMessage,

    link:
      loanId !== null
        ? `/dashboard/loans/${loanId}`
        : '/dashboard/payments',

    time:
      formatNotificationTime(
        timestamp
      ),

    createdAt:
      timestamp,

    read: false,

    source:
      'realtime-payment',
  };
}


/**
 * --------------------------------------------------------------------------
 * STORED BACKEND NOTIFICATION
 * --------------------------------------------------------------------------
 */
function buildStoredNotification(
  notification: StoredNotification
): Notif {
  const createdAt =
    notification.createdAt ||
    new Date().toISOString();

  return {
    id:
      `real-${notification.id}`,

    realId:
      typeof notification.id === 'number'
        ? notification.id
        : Number(notification.id),

    read:
      notification.read === true,

    type:
      normalizeNotificationType(
        notification.type
      ),

    title:
      notification.title ||
      'Notification',

    message:
      notification.message ||
      '',

    link:
      notification.link ||
      undefined,

    time:
      formatNotificationTime(
        createdAt
      ),

    createdAt,

    source:
      'backend',
  };
}


/**
 * --------------------------------------------------------------------------
 * PAGE
 * --------------------------------------------------------------------------
 */
export default function NotificationsPage() {
  const [
    notifs,
    setNotifs,
  ] =
    useState<Notif[]>([]);

  const [
    loading,
    setLoading,
  ] =
    useState(true);

  const [
    filter,
    setFilter,
  ] =
    useState<
      | 'all'
      | 'danger'
      | 'warning'
      | 'success'
      | 'info'
    >('all');

  const [
    realtimeConnected,
    setRealtimeConnected,
  ] =
    useState(false);

  const [
    realtimeError,
    setRealtimeError,
  ] =
    useState<string | null>(null);

  const stompClientRef =
    useRef<Client | null>(null);

  const organizationSubscriptionRef =
    useRef<StompSubscription | null>(null);

  const reconnectTimerRef =
    useRef<ReturnType<
      typeof setTimeout
    > | null>(null);

  const reconnectAttemptRef =
    useRef(0);

  const mountedRef =
    useRef(true);

  /**
   * Prevent duplicate payment events.
   */
  const paymentIdsRef =
    useRef<Set<string>>(
      new Set()
    );


  /**
   * ------------------------------------------------------------------------
   * ADD NOTIFICATION
   * ------------------------------------------------------------------------
   */
  const addNotification =
    useCallback(
      (
        notification: Notif
      ) => {
        if (!mountedRef.current) {
          return;
        }

        setNotifs(
          current => {
            /**
             * Never add the same notification twice.
             */
            const alreadyExists =
              current.some(
                existing =>
                  existing.id ===
                  notification.id
              );

            if (alreadyExists) {
              return current;
            }

            return [
              notification,
              ...current,
            ];
          }
        );
      },
      []
    );


  /**
   * ------------------------------------------------------------------------
   * LOAD EXISTING NOTIFICATIONS
   * ------------------------------------------------------------------------
   */
  useEffect(() => {
    mountedRef.current = true;

    Promise.all([
      getLoans().catch(
        error => {
          console.error(
            'notifications: getLoans failed',
            error
          );

          return [];
        }
      ),

      getOverduePayments().catch(
        error => {
          console.error(
            'notifications: getOverduePayments failed',
            error
          );

          return [];
        }
      ),

      getDashboardStats().catch(
        error => {
          console.error(
            'notifications: getDashboardStats failed',
            error
          );

          return null;
        }
      ),

      getMyNotifications().catch(
        error => {
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
          if (!mountedRef.current) {
            return;
          }

          const l =
            Array.isArray(loans)
              ? loans as Loan[]
              : [];

          const o =
            Array.isArray(overdue)
              ? overdue as Payment[]
              : [];

          const s =
            stats as
              | DashboardStats
              | null;

          const realList =
            Array.isArray(real)
              ? real as StoredNotification[]
              : [];

          const notifications: Notif[] =
            [];

          /**
           * --------------------------------------------------------------
           * REAL BACKEND NOTIFICATIONS
           * --------------------------------------------------------------
           */
          realList.forEach(
            notification => {
              notifications.push(
                buildStoredNotification(
                  notification
                )
              );
            }
          );


          /**
           * --------------------------------------------------------------
           * PORTFOLIO ALERTS
           * --------------------------------------------------------------
           */
          try {
            if (o.length > 0) {
              notifications.push({
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

                source:
                  'portfolio',
              });
            }


            const pending =
              l.filter(
                loan =>
                  loan.status ===
                  'PENDING'
              );

            if (
              pending.length > 0
            ) {
              notifications.push({
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

                source:
                  'portfolio',
              });
            }


            const highRisk =
              l.filter(
                loan =>
                  loan.riskCategory ===
                    'HIGH' ||
                  loan.riskCategory ===
                    'CRITICAL'
              );

            if (
              highRisk.length > 0
            ) {
              notifications.push({
                id: 'hr',

                type: 'warning',

                title:
                  `${highRisk.length} High-Risk Loan${
                    highRisk.length > 1
                      ? 's'
                      : ''
                  }`,

                message:
                  `${highRisk.length} loan${
                    highRisk.length > 1
                      ? 's are'
                      : ' is'
                  } rated HIGH or CRITICAL risk. Review collateral.`,

                link:
                  '/dashboard/loans',

                time:
                  'Today',

                source:
                  'portfolio',
              });
            }


            const disbursed =
              Number(
                s?.totalDisbursed ||
                0
              );

            const collected =
              Number(
                s?.totalCollected ||
                0
              );

            const rate =
              disbursed > 0
                ? (
                    collected /
                    disbursed
                  ) * 100
                : 0;

            if (
              s &&
              rate >= 80
            ) {
              notifications.push({
                id:
                  'cr-good',

                type:
                  'success',

                title:
                  'Strong Collection Rate',

                message:
                  `Portfolio collection rate is ${rate.toFixed(
                    0
                  )}% — excellent performance!`,

                time:
                  'This week',

                source:
                  'portfolio',
              });
            } else if (
              s &&
              rate < 50 &&
              disbursed > 0
            ) {
              notifications.push({
                id:
                  'cr-low',

                type:
                  'warning',

                title:
                  'Low Collection Rate',

                message:
                  `Collection rate is only ${rate.toFixed(
                    0
                  )}%. Consider sending payment reminders.`,

                time:
                  'This week',

                source:
                  'portfolio',
              });
            }


            if (
              s &&
              s.completedLoans > 0
            ) {
              notifications.push({
                id:
                  'closed',

                type:
                  'success',

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

                source:
                  'portfolio',
              });
            }


            if (s) {
              notifications.push({
                id:
                  'summary',

                type:
                  'info',

                title:
                  'Portfolio Summary',

                message:
                  `${s.totalBorrowers} borrowers · ${s.activeLoans} active loans · ${formatCurrency(
                    s.totalDisbursed
                  )} disbursed.`,

                link:
                  '/dashboard/reports',

                time:
                  'Today',

                source:
                  'portfolio',
              });
            }
          } catch (error) {
            console.error(
              'notifications: failed to build portfolio-derived alerts',
              error
            );
          }


          /**
           * Newest notifications first.
           */
          notifications.sort(
            (
              first,
              second
            ) => {
              const firstTime =
                first.createdAt
                  ? new Date(
                      first.createdAt
                    ).getTime()
                  : 0;

              const secondTime =
                second.createdAt
                  ? new Date(
                      second.createdAt
                    ).getTime()
                  : 0;

              return (
                secondTime -
                firstTime
              );
            }
          );


          setNotifs(
            notifications
          );
        }
      )
      .catch(
        error => {
          console.error(
            'notifications: unexpected failure loading notifications',
            error
          );
        }
      )
      .finally(
        () => {
          if (
            mountedRef.current
          ) {
            setLoading(
              false
            );
          }
        }
      );


    return () => {
      mountedRef.current =
        false;
    };
  }, []);


  /**
   * ------------------------------------------------------------------------
   * REALTIME PAYMENT WEBSOCKET
   * ------------------------------------------------------------------------
   *
   * Backend:
   *
   * registry.addEndpoint("/ws")
   *
   * Backend publishes:
   *
   * /topic/organization/{organizationId}/payments
   *
   * Therefore this client connects to:
   *
   * ws(s)://BACKEND/ws
   *
   * and subscribes to:
   *
   * /topic/organization/1/payments
   */
  useEffect(() => {
    if (loading) {
      return;
    }

    let cancelled =
      false;

    const {
      organizationId,
    } =
      getOrganizationContext();

    if (
      organizationId === null
    ) {
      console.warn(
        'Realtime notifications: organizationId was not found in frontend authentication context.'
      );

      setRealtimeError(
        'Organization context unavailable'
      );

      return;
    }


    const apiBaseUrl =
      getApiBaseUrl();

    if (!apiBaseUrl) {
      console.error(
        'Realtime notifications: NEXT_PUBLIC_API_URL is not configured.'
      );

      setRealtimeError(
        'Realtime server URL is not configured'
      );

      return;
    }


    /**
     * Convert HTTP backend URL to WebSocket URL.
     */
    const websocketUrl =
      apiBaseUrl
        .replace(
          /^https:/i,
          'wss:'
        )
        .replace(
          /^http:/i,
          'ws:'
        ) +
      '/ws';


    console.info(
      '[REALTIME] Connecting to:',
      websocketUrl
    );


    const client =
      new Client({
        brokerURL:
          websocketUrl,

        reconnectDelay:
          0,

        heartbeatIncoming:
          10000,

        heartbeatOutgoing:
          10000,

        debug:
          message => {
            /**
             * Keep STOMP debugging available during deployment,
             * but avoid flooding production logs.
             */
            if (
              process.env.NODE_ENV !==
              'production'
            ) {
              console.debug(
                '[STOMP]',
                message
              );
            }
          },

        onConnect:
          () => {
            if (
              cancelled ||
              !mountedRef.current
            ) {
              return;
            }

            console.info(
              '[REALTIME] WebSocket/STOMP connected.'
            );

            setRealtimeConnected(
              true
            );

            setRealtimeError(
              null
            );

            reconnectAttemptRef.current =
              0;


            const destination =
              `/topic/organization/${organizationId}/payments`;

            console.info(
              '[REALTIME] Subscribing to:',
              destination
            );


            organizationSubscriptionRef.current =
              client.subscribe(
                destination,
                (
                  message: IMessage
                ) => {
                  try {
                    const payload =
                      JSON.parse(
                        message.body
                      ) as PaymentNotificationPayload;

                    console.info(
                      '[REALTIME] Payment notification received:',
                      payload
                    );


                    /**
                     * Confirm organization routing.
                     */
                    const payloadOrganizationId =
                      toNumber(
                        payload.organizationId
                      );

                    if (
                      payloadOrganizationId !==
                        null &&
                      payloadOrganizationId !==
                        organizationId
                    ) {
                      console.warn(
                        '[REALTIME] Ignoring notification for another organization.',
                        {
                          organizationId,
                          payloadOrganizationId,
                        }
                      );

                      return;
                    }


                    const paymentKey =
                      payload.paymentId !==
                      undefined &&
                      payload.paymentId !==
                        null
                        ? `payment-${payload.paymentId}`
                        : `payment-${payload.transactionId || ''}-${payload.loanId || ''}`;


                    /**
                     * Prevent duplicate messages from reconnects
                     * or repeated provider events.
                     */
                    if (
                      paymentIdsRef.current.has(
                        paymentKey
                      )
                    ) {
                      return;
                    }

                    paymentIdsRef.current.add(
                      paymentKey
                    );


                    const notification =
                      buildRealtimePaymentNotification(
                        payload
                      );

                    if (
                      notification
                    ) {
                      addNotification(
                        notification
                      );
                    }
                  } catch (
                    error
                  ) {
                    console.error(
                      '[REALTIME] Failed to process payment notification:',
                      error,
                      message.body
                    );
                  }
                }
              );


            console.info(
              '[REALTIME] Subscription active:',
              destination
            );
          },


        onStompError:
          frame => {
            console.error(
              '[REALTIME] STOMP broker error:',
              frame.headers[
                'message'
              ],
              frame.body
            );

            if (
              mountedRef.current
            ) {
              setRealtimeConnected(
                false
              );

              setRealtimeError(
                'Realtime notification connection error'
              );
            }
          },


        onWebSocketError:
          event => {
            console.error(
              '[REALTIME] WebSocket error:',
              event
            );

            if (
              mountedRef.current
            ) {
              setRealtimeConnected(
                false
              );

              setRealtimeError(
                'Realtime notification connection failed'
              );
            }
          },


        onWebSocketClose:
          event => {
            console.warn(
              '[REALTIME] WebSocket closed:',
              event
            );

            if (
              mountedRef.current
            ) {
              setRealtimeConnected(
                false
              );
            }


            /**
             * Manual exponential reconnect.
             *
             * This is intentionally capped.
             */
            if (
              cancelled
            ) {
              return;
            }


            const attempt =
              reconnectAttemptRef.current;

            const delay =
              Math.min(
                30000,
                Math.max(
                  1000,
                  1000 *
                    Math.pow(
                      2,
                      attempt
                    )
                )
              );

            reconnectAttemptRef.current =
              attempt + 1;


            if (
              reconnectTimerRef.current
            ) {
              clearTimeout(
                reconnectTimerRef.current
              );
            }


            reconnectTimerRef.current =
              setTimeout(
                () => {
                  if (
                    cancelled
                  ) {
                    return;
                  }

                  console.info(
                    '[REALTIME] Reconnecting WebSocket...'
                  );

                  try {
                    client.activate();
                  } catch (
                    error
                  ) {
                    console.error(
                      '[REALTIME] Reconnect failed:',
                      error
                    );
                  }
                },
                delay
              );
          },
      });


    stompClientRef.current =
      client;


    try {
      client.activate();
    } catch (error) {
      console.error(
        '[REALTIME] Failed to activate WebSocket:',
        error
      );

      setRealtimeError(
        'Unable to start realtime notifications'
      );
    }


    return () => {
      cancelled =
        true;

      if (
        reconnectTimerRef.current
      ) {
        clearTimeout(
          reconnectTimerRef.current
        );

        reconnectTimerRef.current =
          null;
      }


      if (
        organizationSubscriptionRef.current
      ) {
        try {
          organizationSubscriptionRef.current.unsubscribe();
        } catch {
          // Ignore unsubscribe errors during teardown.
        }

        organizationSubscriptionRef.current =
          null;
      }


      try {
        client.deactivate();
      } catch {
        // Ignore client teardown errors.
      }


      stompClientRef.current =
        null;

      setRealtimeConnected(
        false
      );
    };
  }, [
    loading,
    addNotification,
  ]);


  /**
   * ------------------------------------------------------------------------
   * FILTERED NOTIFICATIONS
   * ------------------------------------------------------------------------
   */
  const filtered =
    useMemo(
      () => {
        if (
          filter ===
          'all'
        ) {
          return notifs;
        }

        return notifs.filter(
          notification =>
            notification.type ===
            filter
        );
      },
      [
        filter,
        notifs,
      ]
    );


  /**
   * ------------------------------------------------------------------------
   * MARK READ
   * ------------------------------------------------------------------------
   */
  const handleMarkRead =
    useCallback(
      async (
        notification: Notif
      ) => {
        if (
          !notification.realId ||
          notification.read
        ) {
          return;
        }

        try {
          await markNotificationRead(
            notification.realId
          );

          setNotifs(
            current =>
              current.map(
                item =>
                  item.id ===
                  notification.id
                    ? {
                        ...item,
                        read: true,
                      }
                    : item
              )
          );
        } catch (error) {
          console.error(
            'notifications: failed to mark notification read',
            error
          );
        }
      },
      []
    );


  /**
   * ------------------------------------------------------------------------
   * UI CONFIG
   * ------------------------------------------------------------------------
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
   * ------------------------------------------------------------------------
   * LOADING
   * ------------------------------------------------------------------------
   */
  if (loading) {
    return <PageSpinner />;
  }


  /**
   * ------------------------------------------------------------------------
   * RENDER
   * ------------------------------------------------------------------------
   */
  return (
    <div className="space-y-5 max-w-3xl">

      {/* --------------------------------------------------------------- */}
      {/* HEADER                                                          */}
      {/* --------------------------------------------------------------- */}

      <div className="flex items-start justify-between gap-4">

        <div>
          <h1 className="text-xl font-bold text-gray-900">
            Notifications
          </h1>

          <p className="text-sm text-gray-500">
            {notifs.length} alerts for your portfolio
          </p>
        </div>


        {/* Realtime status */}

        <div className="flex items-center gap-2">

          <span
            className={`h-2.5 w-2.5 rounded-full ${
              realtimeConnected
                ? 'bg-green-500 animate-pulse'
                : 'bg-gray-300'
            }`}
          />

          <span className="text-xs text-gray-500">

            {realtimeConnected
              ? 'Live'
              : 'Connecting...'}
          </span>

        </div>

      </div>


      {/* --------------------------------------------------------------- */}
      {/* REALTIME STATUS                                                 */}
      {/* --------------------------------------------------------------- */}

      {realtimeError && (
        <div className="rounded-xl border border-yellow-200 bg-yellow-50 px-4 py-3">

          <div className="flex items-start gap-3">

            <span>
              ⚠️
            </span>

            <div>

              <p className="text-sm font-medium text-yellow-800">
                Realtime notifications unavailable
              </p>

              <p className="text-xs text-yellow-700 mt-1">
                Existing notifications are still available.
                The system will continue trying to reconnect.
              </p>

            </div>

          </div>

        </div>
      )}


      {/* --------------------------------------------------------------- */}
      {/* FILTERS                                                         */}
      {/* --------------------------------------------------------------- */}

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
          type => (
            <button
              key={type}
              onClick={() =>
                setFilter(type)
              }
              className={`px-3 py-1.5 rounded-lg text-xs font-medium capitalize transition ${
                filter === type
                  ? 'bg-white shadow text-green-600'
                  : 'text-gray-500 hover:text-gray-800'
              }`}
            >

              {type === 'all'
                ? 'All'
                : `${ICON[type]} ${
                    type
                      .charAt(0)
                      .toUpperCase() +
                    type.slice(1)
                  }`}

            </button>
          )
        )}

      </div>


      {/* --------------------------------------------------------------- */}
      {/* NOTIFICATIONS                                                   */}
      {/* --------------------------------------------------------------- */}

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


        {filtered.map(
          notification => (

            <div
              key={notification.id}
              className={`rounded-2xl border p-5 transition ${
                BG[
                  notification.type
                ]
              } ${
                notification.realId &&
                !notification.read
                  ? 'ring-2 ring-offset-1 ring-teal-300'
                  : ''
              } ${
                notification.source ===
                'realtime-payment'
                  ? 'shadow-sm'
                  : ''
              }`}
            >

              <div className="flex items-start gap-4">

                {/* ICON */}

                <span className="text-2xl flex-shrink-0">

                  {
                    ICON[
                      notification.type
                    ]
                  }

                </span>


                {/* CONTENT */}

                <div className="flex-1 min-w-0">

                  <div className="flex items-center justify-between gap-2 mb-1">

                    <div className="flex items-center gap-2">

                      <p
                        className={`font-semibold text-sm ${
                          TXT[
                            notification.type
                          ]
                        }`}
                      >
                        {
                          notification.title
                        }
                      </p>


                      {notification.source ===
                        'realtime-payment' && (
                        <span className="text-[10px] font-semibold uppercase tracking-wide rounded-full bg-green-100 text-green-700 px-2 py-0.5">
                          Live
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


                  {/* ACTION */}

                  <div className="flex items-center gap-2 mt-3">

                    {notification.link && (
                      <Link
                        href={
                          notification.link
                        }
                        onClick={() =>
                          handleMarkRead(
                            notification
                          )
                        }
                        className={`inline-block text-xs font-semibold px-3 py-1.5 rounded-lg transition ${
                          BTN[
                            notification.type
                          ]
                        }`}
                      >
                        Take action →
                      </Link>
                    )}


                    {notification.realId &&
                      !notification.read && (
                        <button
                          type="button"
                          onClick={() =>
                            handleMarkRead(
                              notification
                            )
                          }
                          className="text-xs font-medium text-gray-500 hover:text-gray-800 px-2 py-1.5"
                        >
                          Mark as read
                        </button>
                      )}

                  </div>

                </div>

              </div>

            </div>

          )
        )}

      </div>

    </div>
  );
}