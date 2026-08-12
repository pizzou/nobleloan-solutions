'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import Link from 'next/link';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';

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

  /**
   * Used to identify realtime payment notifications.
   */
  realtime?: boolean;

  /**
   * Payment metadata.
   */
  paymentId?: number;
  loanId?: number;
  transactionId?: string;
  amount?: number;
}

interface PaymentNotificationPayload {
  organizationId?: number | string | null;
  loanId?: number | string | null;
  paymentId?: number | string | null;
  amount?: number | string | null;
  transactionId?: string | null;
  paymentMethod?: string | null;
  paymentStatus?: string | null;
  currency?: string | null;
  borrowerName?: string | null;
  loanNumber?: string | null;
  createdAt?: string | null;
  timestamp?: string | null;
  message?: string | null;
  title?: string | null;
}

type FilterType =
  | 'all'
  | 'danger'
  | 'warning'
  | 'success'
  | 'info';

type ConnectionState =
  | 'connecting'
  | 'connected'
  | 'reconnecting'
  | 'disconnected';

const ICON: Record<Notif['type'], string> = {
  danger: '🔴',
  warning: '⚠️',
  success: '✅',
  info: '💡',
};

const BG: Record<Notif['type'], string> = {
  danger: 'bg-red-50 border-red-100',
  warning: 'bg-yellow-50 border-yellow-100',
  success: 'bg-green-50 border-green-100',
  info: 'bg-blue-50 border-blue-100',
};

const TXT: Record<Notif['type'], string> = {
  danger: 'text-red-700',
  warning: 'text-yellow-700',
  success: 'text-green-700',
  info: 'text-blue-700',
};

const BTN: Record<Notif['type'], string> = {
  danger: 'bg-red-100 text-red-700 hover:bg-red-200',
  warning: 'bg-yellow-100 text-yellow-700 hover:bg-yellow-200',
  success: 'bg-green-100 text-green-700 hover:bg-green-200',
  info: 'bg-blue-100 text-blue-700 hover:bg-blue-200',
};

function toNumber(
  value: number | string | null | undefined,
): number | null {
  if (value === null || value === undefined || value === '') {
    return null;
  }

  const parsed = Number(value);

  return Number.isFinite(parsed) ? parsed : null;
}

function formatMoney(
  amount: number,
  currency = 'RWF',
): string {
  try {
    return new Intl.NumberFormat('en-RW', {
      style: 'currency',
      currency,
      maximumFractionDigits: 2,
    }).format(amount);
  } catch {
    return `${currency} ${amount.toLocaleString()}`;
  }
}

function formatNotificationTime(
  value?: string | null,
): string {
  if (!value) {
    return 'Just now';
  }

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return 'Just now';
  }

  return date.toLocaleString();
}

function getApiBaseUrl(): string {
  const configured =
    process.env.NEXT_PUBLIC_API_URL ||
    process.env.NEXT_PUBLIC_API_BASE_URL ||
    '';

  if (configured) {
    return configured.replace(/\/+$/, '');
  }

  if (typeof window !== 'undefined') {
    const origin = window.location.origin;

    return origin.replace(/\/+$/, '');
  }

  return '';
}

function getWebSocketUrl(): string {
  const apiBaseUrl = getApiBaseUrl();

  if (!apiBaseUrl) {
    return '';
  }

  if (apiBaseUrl.startsWith('https://')) {
    return apiBaseUrl.replace(
      /^https:\/\//i,
      'wss://',
    ) + '/ws';
  }

  if (apiBaseUrl.startsWith('http://')) {
    return apiBaseUrl.replace(
      /^http:\/\//i,
      'ws://',
    ) + '/ws';
  }

  if (apiBaseUrl.startsWith('ws://')) {
    return apiBaseUrl.replace(/\/+$/, '') + '/ws';
  }

  if (apiBaseUrl.startsWith('wss://')) {
    return apiBaseUrl.replace(/\/+$/, '') + '/ws';
  }

  return `wss://${apiBaseUrl.replace(/^\/+|\/+$/g, '')}/ws`;
}

function decodeJwtPayload(
  token: string,
): Record<string, unknown> | null {
  try {
    const parts = token.split('.');

    if (parts.length !== 3) {
      return null;
    }

    const base64Url = parts[1];

    const base64 = base64Url
      .replace(/-/g, '+')
      .replace(/_/g, '/');

    const padded =
      base64 +
      '='.repeat(
        (4 - (base64.length % 4)) % 4,
      );

    const decoded = window.atob(padded);

    const bytes = Uint8Array.from(
      decoded,
      (character) => character.charCodeAt(0),
    );

    const json = new TextDecoder().decode(bytes);

    return JSON.parse(json) as Record<string, unknown>;
  } catch {
    return null;
  }
}

function findOrganizationIdInObject(
  object: unknown,
): number | null {
  if (!object || typeof object !== 'object') {
    return null;
  }

  const source = object as Record<string, unknown>;

  const candidates = [
    source.organizationId,
    source.organization_id,
    source.orgId,
    source.org_id,
    source.tenantId,
    source.tenant_id,
  ];

  for (const candidate of candidates) {
    const number = toNumber(
      candidate as number | string | null | undefined,
    );

    if (number !== null && number > 0) {
      return number;
    }
  }

  return null;
}

function getOrganizationId(): number | null {
  if (typeof window === 'undefined') {
    return null;
  }

  /**
   * First check common application storage keys.
   *
   * This keeps the notification page compatible with the
   * existing tenant/authentication implementation without
   * hard-coding an organization.
   */
  const storageKeys = [
    'organizationId',
    'organization_id',
    'orgId',
    'org_id',
    'tenantId',
    'tenant_id',
    'currentOrganizationId',
    'current_organization_id',
  ];

  for (const key of storageKeys) {
    const value = window.localStorage.getItem(key);

    const id = toNumber(value);

    if (id !== null && id > 0) {
      return id;
    }
  }

  /**
   * Try common user/session objects.
   */
  const objectKeys = [
    'user',
    'currentUser',
    'authUser',
    'current_user',
    'auth_user',
    'session',
  ];

  for (const key of objectKeys) {
    const raw = window.localStorage.getItem(key);

    if (!raw) {
      continue;
    }

    try {
      const parsed = JSON.parse(raw);

      const id = findOrganizationIdInObject(parsed);

      if (id !== null) {
        return id;
      }

      if (
        parsed &&
        typeof parsed === 'object' &&
        'user' in parsed
      ) {
        const nestedId = findOrganizationIdInObject(
          (parsed as Record<string, unknown>).user,
        );

        if (nestedId !== null) {
          return nestedId;
        }
      }
    } catch {
      // Ignore malformed storage values.
    }
  }

  /**
   * Finally inspect JWT claims.
   */
  const tokenKeys = [
    'token',
    'accessToken',
    'access_token',
    'jwt',
    'authToken',
    'auth_token',
  ];

  for (const key of tokenKeys) {
    const token =
      window.localStorage.getItem(key);

    if (!token) {
      continue;
    }

    const payload =
      decodeJwtPayload(token);

    const id =
      findOrganizationIdInObject(payload);

    if (id !== null) {
      return id;
    }

    if (payload) {
      const nestedCandidates = [
        payload.user,
        payload.organization,
        payload.organizationContext,
        payload.tenant,
      ];

      for (const candidate of nestedCandidates) {
        const nestedId =
          findOrganizationIdInObject(candidate);

        if (nestedId !== null) {
          return nestedId;
        }
      }
    }
  }

  return null;
}

function getAuthToken(): string | null {
  if (typeof window === 'undefined') {
    return null;
  }

  const keys = [
    'accessToken',
    'access_token',
    'token',
    'jwt',
    'authToken',
    'auth_token',
  ];

  for (const key of keys) {
    const value =
      window.localStorage.getItem(key);

    if (value) {
      return value;
    }
  }

  return null;
}

function normalizeNotificationType(
  value?: string | null,
): Notif['type'] {
  const normalized =
    String(value || '')
      .trim()
      .toUpperCase();

  if (
    normalized.includes('OVERDUE') ||
    normalized.includes('PENALTY') ||
    normalized.includes('FAILED') ||
    normalized.includes('REJECT')
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

  if (
    normalized.includes('PAYMENT') ||
    normalized.includes('SUCCESS') ||
    normalized.includes('APPROVED') ||
    normalized.includes('COMPLETED')
  ) {
    return 'success';
  }

  return 'info';
}

function buildPaymentNotification(
  payload: PaymentNotificationPayload,
): Notif | null {
  const loanId =
    toNumber(payload.loanId);

  const paymentId =
    toNumber(payload.paymentId);

  const amount =
    toNumber(payload.amount);

  if (
    loanId === null &&
    paymentId === null
  ) {
    return null;
  }

  const currency =
    payload.currency ||
    'RWF';

  const borrower =
    payload.borrowerName?.trim();

  const loanNumber =
    payload.loanNumber?.trim();

  let message =
    payload.message?.trim();

  if (!message) {
    const paymentAmount =
      amount !== null
        ? formatMoney(amount, currency)
        : 'a payment';

    if (borrower && loanNumber) {
      message =
        `${borrower} paid ${paymentAmount} for loan ${loanNumber}.`;
    } else if (borrower) {
      message =
        `${borrower} paid ${paymentAmount}.`;
    } else if (loanNumber) {
      message =
        `Payment of ${paymentAmount} received for loan ${loanNumber}.`;
    } else if (loanId !== null) {
      message =
        `Payment of ${paymentAmount} received for loan #${loanId}.`;
    } else {
      message =
        `Payment of ${paymentAmount} received.`;
    }
  }

  const title =
    payload.title?.trim() ||
    'Payment Received';

  const createdAt =
    payload.createdAt ||
    payload.timestamp ||
    new Date().toISOString();

  /**
   * Payment ID is the strongest deduplication key.
   * Transaction ID is also included to protect against
   * duplicate provider events.
   */
  const identity =
    paymentId !== null
      ? `payment-${paymentId}`
      : payload.transactionId
        ? `payment-tx-${payload.transactionId}`
        : `payment-loan-${loanId ?? 'unknown'}-${amount ?? 'unknown'}-${createdAt}`;

  return {
    id: `realtime-${identity}`,
    type: 'success',
    title,
    message,
    link:
      loanId !== null
        ? `/dashboard/loans/${loanId}`
        : '/dashboard/payments',
    time: formatNotificationTime(createdAt),
    read: false,
    realtime: true,
    paymentId:
      paymentId ?? undefined,
    loanId:
      loanId ?? undefined,
    transactionId:
      payload.transactionId || undefined,
    amount:
      amount ?? undefined,
  };
}

export default function NotificationsPage() {
  const [notifs, setNotifs] =
    useState<Notif[]>([]);

  const [loading, setLoading] =
    useState(true);

  const [filter, setFilter] =
    useState<FilterType>('all');

  const [connectionState, setConnectionState] =
    useState<ConnectionState>('disconnected');

  const [organizationId, setOrganizationId] =
    useState<number | null>(null);

  const stompClientRef =
    useRef<Client | null>(null);

  const paymentSubscriptionRef =
    useRef<StompSubscription | null>(null);

  const reconnectTimerRef =
    useRef<ReturnType<typeof setTimeout> | null>(
      null,
    );

  const mountedRef =
    useRef(false);

  const receivedPaymentIdsRef =
    useRef<Set<string>>(new Set());

  /**
   * ------------------------------------------------------------
   * LOAD INITIAL NOTIFICATIONS
   * ------------------------------------------------------------
   */
  const loadNotifications =
    useCallback(async () => {
      setLoading(true);

      try {
        const [
          loans,
          overdue,
          stats,
          real,
        ] = await Promise.all([
          getLoans().catch((error) => {
            console.error(
              'notifications: getLoans failed',
              error,
            );

            return [];
          }),

          getOverduePayments().catch((error) => {
            console.error(
              'notifications: getOverduePayments failed',
              error,
            );

            return [];
          }),

          getDashboardStats().catch((error) => {
            console.error(
              'notifications: getDashboardStats failed',
              error,
            );

            return null;
          }),

          getMyNotifications().catch((error) => {
            console.error(
              'notifications: getMyNotifications failed',
              error,
            );

            return [];
          }),
        ]);

        if (!mountedRef.current) {
          return;
        }

        const loanList =
          Array.isArray(loans)
            ? (loans as Loan[])
            : [];

        const overdueList =
          Array.isArray(overdue)
            ? (overdue as Payment[])
            : [];

        const dashboardStats =
          stats as DashboardStats | null;

        const realList =
          Array.isArray(real)
            ? (real as any[])
            : [];

        const nextNotifications: Notif[] =
          [];

        /**
         * --------------------------------------------------------
         * BACKEND-PERSISTED NOTIFICATIONS
         * --------------------------------------------------------
         *
         * This preserves the notification system that already
         * works for loan creation and other backend events.
         */
        realList.forEach((notification) => {
          if (
            notification === null ||
            notification === undefined
          ) {
            return;
          }

          const realId =
            toNumber(notification.id);

          if (realId === null) {
            return;
          }

          nextNotifications.push({
            id: `real-${realId}`,
            realId,
            read:
              Boolean(notification.read),

            type:
              normalizeNotificationType(
                notification.type,
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
                notification.createdAt,
              ),
          });
        });

        /**
         * --------------------------------------------------------
         * DERIVED PORTFOLIO ALERTS
         * --------------------------------------------------------
         */
        try {
          if (overdueList.length > 0) {
            nextNotifications.push({
              id: 'ov',
              type: 'danger',

              title:
                `${overdueList.length} Overdue Payment${
                  overdueList.length > 1
                    ? 's'
                    : ''
                }`,

              message:
                `${overdueList.length} payment${
                  overdueList.length > 1
                    ? 's are'
                    : ' is'
                } past due. Penalties accruing daily.`,

              link:
                '/dashboard/payments',

              time: 'Now',
            });
          }

          const pendingLoans =
            loanList.filter(
              (loan) =>
                loan.status === 'PENDING',
            );

          if (pendingLoans.length > 0) {
            nextNotifications.push({
              id: 'pend',
              type: 'warning',

              title:
                `${pendingLoans.length} Loan${
                  pendingLoans.length > 1
                    ? 's'
                    : ''
                } Awaiting Approval`,

              message:
                `${pendingLoans.length} application${
                  pendingLoans.length > 1
                    ? 's need'
                    : ' needs'
                } your review.`,

              link:
                '/dashboard/approvals',

              time: 'Today',
            });
          }

          const highRiskLoans =
            loanList.filter(
              (loan) =>
                loan.riskCategory === 'HIGH' ||
                loan.riskCategory === 'CRITICAL',
            );

          if (highRiskLoans.length > 0) {
            nextNotifications.push({
              id: 'hr',
              type: 'warning',

              title:
                `${highRiskLoans.length} High-Risk Loan${
                  highRiskLoans.length > 1
                    ? 's'
                    : ''
                }`,

              message:
                `${highRiskLoans.length} loan${
                  highRiskLoans.length > 1
                    ? 's are'
                    : ' is'
                } rated HIGH or CRITICAL risk. Review collateral.`,

              link:
                '/dashboard/loans',

              time: 'Today',
            });
          }

          const totalDisbursed =
            Number(
              dashboardStats?.totalDisbursed || 0,
            );

          const totalCollected =
            Number(
              dashboardStats?.totalCollected || 0,
            );

          const collectionRate =
            totalDisbursed > 0
              ? (totalCollected /
                  totalDisbursed) *
                100
              : 0;

          if (
            dashboardStats &&
            collectionRate >= 80
          ) {
            nextNotifications.push({
              id: 'cr-good',
              type: 'success',

              title:
                'Strong Collection Rate',

              message:
                `Portfolio collection rate is ${collectionRate.toFixed(
                  0,
                )}% — excellent performance!`,

              time: 'This week',
            });
          } else if (
            dashboardStats &&
            collectionRate < 50 &&
            totalDisbursed > 0
          ) {
            nextNotifications.push({
              id: 'cr-low',
              type: 'warning',

              title:
                'Low Collection Rate',

              message:
                `Collection rate is only ${collectionRate.toFixed(
                  0,
                )}%. Consider sending payment reminders.`,

              time: 'This week',
            });
          }

          if (
            dashboardStats &&
            dashboardStats.completedLoans > 0
          ) {
            nextNotifications.push({
              id: 'closed',
              type: 'success',

              title:
                `${dashboardStats.completedLoans} Loan${
                  dashboardStats.completedLoans > 1
                    ? 's'
                    : ''
                } Fully Repaid`,

              message:
                `${dashboardStats.completedLoans} loan${
                  dashboardStats.completedLoans > 1
                    ? 's have'
                    : ' has'
                } been fully repaid. Great portfolio health!`,

              link:
                '/dashboard/loans',

              time: 'This month',
            });
          }

          if (dashboardStats) {
            nextNotifications.push({
              id: 'summary',
              type: 'info',

              title:
                'Portfolio Summary',

              message:
                `${dashboardStats.totalBorrowers} borrowers · ` +
                `${dashboardStats.activeLoans} active loans · ` +
                `$${Number(
                  dashboardStats.totalDisbursed || 0,
                ).toLocaleString()} disbursed.`,

              link:
                '/dashboard/reports',

              time: 'Today',
            });
          }
        } catch (error) {
          console.error(
            'notifications: failed to build portfolio-derived alerts',
            error,
          );
        }

        /**
         * Newest notifications first.
         *
         * Realtime payment notifications are inserted at the top
         * later without disturbing persisted notifications.
         */
        nextNotifications.sort(
          (a, b) => {
            const aTime =
              a.time === 'Now'
                ? Date.now()
                : new Date(a.time).getTime();

            const bTime =
              b.time === 'Now'
                ? Date.now()
                : new Date(b.time).getTime();

            return (
              (Number.isFinite(bTime)
                ? bTime
                : 0) -
              (Number.isFinite(aTime)
                ? aTime
                : 0)
            );
          },
        );

        setNotifs(nextNotifications);
      } catch (error) {
        console.error(
          'notifications: unexpected failure loading notifications',
          error,
        );
      } finally {
        if (mountedRef.current) {
          setLoading(false);
        }
      }
    }, []);

  /**
   * ------------------------------------------------------------
   * HANDLE REALTIME PAYMENT
   * ------------------------------------------------------------
   */
  const handleRealtimePayment =
    useCallback(
      (message: IMessage) => {
        try {
          const payload =
            JSON.parse(
              message.body,
            ) as PaymentNotificationPayload;

          console.info(
            '[REALTIME NOTIFICATIONS] Payment received:',
            payload,
          );

          const notification =
            buildPaymentNotification(
              payload,
            );

          if (!notification) {
            console.warn(
              '[REALTIME NOTIFICATIONS] Ignoring malformed payment notification:',
              payload,
            );

            return;
          }

          /**
           * Strong duplicate protection.
           *
           * The backend can legitimately deliver an event more than
           * once in distributed/retry scenarios. The UI must not
           * show duplicate payment notifications.
           */
          const dedupeKey =
            notification.paymentId !==
            undefined
              ? `payment-${notification.paymentId}`
              : notification.transactionId
                ? `transaction-${notification.transactionId}`
                : notification.id;

          if (
            receivedPaymentIdsRef.current.has(
              dedupeKey,
            )
          ) {
            return;
          }

          receivedPaymentIdsRef.current.add(
            dedupeKey,
          );

          /**
           * Keep the Set from growing indefinitely during a very
           * long-running browser session.
           */
          if (
            receivedPaymentIdsRef.current
              .size > 1000
          ) {
            const first =
              receivedPaymentIdsRef.current
                .values()
                .next()
                .value;

            if (first) {
              receivedPaymentIdsRef.current.delete(
                first,
              );
            }
          }

          setNotifs(
            (current) => {
              /**
               * Protect against duplicates already loaded from
               * the REST notification history.
               */
              const alreadyExists =
                current.some(
                  (item) =>
                    (
                      notification.paymentId !==
                        undefined &&
                      item.paymentId ===
                        notification.paymentId
                    ) ||
                    (
                      notification.transactionId &&
                      item.transactionId ===
                        notification.transactionId
                    ),
                );

              if (alreadyExists) {
                return current;
              }

              return [
                notification,
                ...current,
              ];
            },
          );

          /**
           * Optional browser notification.
           *
           * Only use this if the user has already granted browser
           * notification permission. We never request permission
           * automatically because that is bad UX.
           */
          if (
            typeof window !== 'undefined' &&
            'Notification' in window &&
            Notification.permission ===
              'granted'
          ) {
            try {
              new Notification(
                notification.title,
                {
                  body:
                    notification.message,
                  tag:
                    notification.paymentId
                      ? `payment-${notification.paymentId}`
                      : notification.id,
                },
              );
            } catch {
              // Browser notification failure must never affect
              // the dashboard notification itself.
            }
          }
        } catch (error) {
          console.error(
            '[REALTIME NOTIFICATIONS] Failed to process payment message:',
            error,
            message.body,
          );
        }
      },
      [],
    );

  /**
   * ------------------------------------------------------------
   * CONNECT TO PAYMENT WEBSOCKET
   * ------------------------------------------------------------
   */
  useEffect(() => {
    mountedRef.current = true;

    const id =
      getOrganizationId();

    setOrganizationId(id);

    if (id === null) {
      console.warn(
        '[REALTIME NOTIFICATIONS] Organization ID could not be determined. ' +
          'Realtime payment subscription will not start.',
      );

      return () => {
        mountedRef.current = false;
      };
    }

    const websocketUrl =
      getWebSocketUrl();

    if (!websocketUrl) {
      console.error(
        '[REALTIME NOTIFICATIONS] WebSocket URL could not be determined.',
      );

      setConnectionState(
        'disconnected',
      );

      return () => {
        mountedRef.current = false;
      };
    }

    let destroyed = false;

    const connect = () => {
      if (destroyed) {
        return;
      }

      setConnectionState(
        stompClientRef.current
          ? 'reconnecting'
          : 'connecting',
      );

      const client =
        new Client({
          brokerURL:
            websocketUrl,

          reconnectDelay: 5000,

          connectionTimeout: 10000,

          heartbeatIncoming: 10000,

          heartbeatOutgoing: 10000,

          debug: (message) => {
            /**
             * Keep STOMP debug disabled in production logs.
             *
             * Uncomment temporarily when diagnosing a WebSocket
             * connection:
             *
             * console.debug('[STOMP]', message);
             */
          },

          onConnect: () => {
            if (destroyed) {
              return;
            }

            console.info(
              '[REALTIME NOTIFICATIONS] WebSocket connected.',
            );

            setConnectionState(
              'connected',
            );

            /**
             * Remove an old subscription before creating a new one.
             */
            try {
              paymentSubscriptionRef.current?.unsubscribe();
            } catch {
              // Ignore stale subscription errors.
            }

            const destination =
              `/topic/organization/${id}/payments`;

            paymentSubscriptionRef.current =
              client.subscribe(
                destination,
                handleRealtimePayment,
              );

            console.info(
              '[REALTIME NOTIFICATIONS] Subscribed to:',
              destination,
            );
          },

          onDisconnect: () => {
            if (destroyed) {
              return;
            }

            console.warn(
              '[REALTIME NOTIFICATIONS] WebSocket disconnected.',
            );

            setConnectionState(
              'reconnecting',
            );
          },

          onStompError: (frame) => {
            console.error(
              '[REALTIME NOTIFICATIONS] STOMP broker error:',
              frame.headers['message'],
              frame.body,
            );

            if (!destroyed) {
              setConnectionState(
                'reconnecting',
              );
            }
          },

          onWebSocketError: (event) => {
            console.error(
              '[REALTIME NOTIFICATIONS] WebSocket error:',
              event,
            );

            if (!destroyed) {
              setConnectionState(
                'reconnecting',
              );
            }
          },

          onWebSocketClose: () => {
            if (destroyed) {
              return;
            }

            console.warn(
              '[REALTIME NOTIFICATIONS] WebSocket connection closed.',
            );

            setConnectionState(
              'reconnecting',
            );
          },
        });

      /**
       * If your backend later requires JWT authentication
       * headers on the STOMP CONNECT frame, they can be added
       * here without changing the subscription architecture.
       */
      const token =
        getAuthToken();

      if (token) {
        client.connectHeaders = {
          Authorization:
            token.startsWith('Bearer ')
              ? token
              : `Bearer ${token}`,
        };
      }

      stompClientRef.current =
        client;

      try {
        client.activate();
      } catch (error) {
        console.error(
          '[REALTIME NOTIFICATIONS] Failed to activate WebSocket:',
          error,
        );

        setConnectionState(
          'disconnected',
        );
      }
    };

    connect();

    return () => {
      destroyed = true;
      mountedRef.current = false;

      if (
        reconnectTimerRef.current
      ) {
        clearTimeout(
          reconnectTimerRef.current,
        );

        reconnectTimerRef.current =
          null;
      }

      try {
        paymentSubscriptionRef.current?.unsubscribe();
      } catch {
        // Ignore cleanup errors.
      }

      paymentSubscriptionRef.current =
        null;

      const client =
        stompClientRef.current;

      stompClientRef.current =
        null;

      if (client) {
        try {
          void client.deactivate();
        } catch {
          // Ignore cleanup errors.
        }
      }

      setConnectionState(
        'disconnected',
      );
    };
  }, [handleRealtimePayment]);

  /**
   * ------------------------------------------------------------
   * INITIAL DATA
   * ------------------------------------------------------------
   */
  useEffect(() => {
    mountedRef.current = true;

    void loadNotifications();

    return () => {
      mountedRef.current = false;
    };
  }, [loadNotifications]);

  /**
   * ------------------------------------------------------------
   * REFRESH HISTORY WHEN TAB RETURNS TO FOREGROUND
   * ------------------------------------------------------------
   *
   * This protects against notifications received while the
   * browser was suspended, offline, or temporarily disconnected.
   */
  useEffect(() => {
    const handleVisibility =
      () => {
        if (
          document.visibilityState ===
          'visible'
        ) {
          void loadNotifications();
        }
      };

    document.addEventListener(
      'visibilitychange',
      handleVisibility,
    );

    return () => {
      document.removeEventListener(
        'visibilitychange',
        handleVisibility,
      );
    };
  }, [loadNotifications]);

  /**
   * ------------------------------------------------------------
   * FILTERED NOTIFICATIONS
   * ------------------------------------------------------------
   */
  const filtered =
    useMemo(() => {
      if (filter === 'all') {
        return notifs;
      }

      return notifs.filter(
        (notification) =>
          notification.type ===
          filter,
      );
    }, [filter, notifs]);

  const unreadCount =
    useMemo(
      () =>
        notifs.filter(
          (notification) =>
            notification.read === false,
        ).length,
      [notifs],
    );

  const realtimePaymentCount =
    useMemo(
      () =>
        notifs.filter(
          (notification) =>
            notification.realtime === true,
        ).length,
      [notifs],
    );

  /**
   * ------------------------------------------------------------
   * MARK READ
   * ------------------------------------------------------------
   */
  const handleMarkRead =
    useCallback(
      async (
        notification: Notif,
      ) => {
        if (
          !notification.realId ||
          notification.read
        ) {
          return;
        }

        try {
          await markNotificationRead(
            notification.realId,
          );

          setNotifs(
            (current) =>
              current.map(
                (item) =>
                  item.id ===
                  notification.id
                    ? {
                        ...item,
                        read: true,
                      }
                    : item,
              ),
          );
        } catch (error) {
          console.error(
            'notifications: failed to mark notification as read',
            error,
          );
        }
      },
      [],
    );

  /**
   * ------------------------------------------------------------
   * LOADING
   * ------------------------------------------------------------
   */
  if (loading) {
    return <PageSpinner />;
  }

  return (
    <div className="space-y-5 max-w-4xl">
      {/* ======================================================
          HEADER
      ======================================================= */}
      <div className="flex items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-xl font-bold text-gray-900">
              Notifications
            </h1>

            {unreadCount > 0 && (
              <span className="inline-flex items-center justify-center min-w-6 h-6 px-2 rounded-full bg-red-600 text-white text-xs font-bold">
                {unreadCount}
              </span>
            )}
          </div>

          <p className="text-sm text-gray-500 mt-1">
            {notifs.length} alerts for your
            portfolio
          </p>
        </div>

        {/* ====================================================
            REALTIME CONNECTION STATUS
        ===================================================== */}
        <div className="flex items-center gap-2">
          <span
            className={`h-2.5 w-2.5 rounded-full ${
              connectionState ===
              'connected'
                ? 'bg-green-500'
                : connectionState ===
                    'connecting' ||
                  connectionState ===
                    'reconnecting'
                  ? 'bg-yellow-400'
                  : 'bg-gray-400'
            }`}
          />

          <span className="text-xs text-gray-500">
            {connectionState ===
            'connected'
              ? 'Live'
              : connectionState ===
                  'connecting'
                ? 'Connecting'
                : connectionState ===
                    'reconnecting'
                  ? 'Reconnecting'
                  : 'Offline'}
          </span>
        </div>
      </div>

      {/* ======================================================
          REALTIME PAYMENT STATUS
      ======================================================= */}
      {realtimePaymentCount > 0 && (
        <div className="rounded-2xl border border-green-100 bg-green-50 px-4 py-3">
          <div className="flex items-center gap-3">
            <span className="text-xl">
              ⚡
            </span>

            <div>
              <p className="text-sm font-semibold text-green-800">
                Live payment monitoring
              </p>

              <p className="text-xs text-green-700 mt-0.5">
                Payment notifications are
                delivered instantly when
                payments are received.
              </p>
            </div>
          </div>
        </div>
      )}

      {/* ======================================================
          WEBSOCKET WARNING
      ======================================================= */}
      {organizationId === null && (
        <div className="rounded-2xl border border-yellow-100 bg-yellow-50 px-4 py-3">
          <div className="flex items-start gap-3">
            <span className="text-lg">
              ⚠️
            </span>

            <div>
              <p className="text-sm font-semibold text-yellow-800">
                Live notifications unavailable
              </p>

              <p className="text-xs text-yellow-700 mt-1">
                Your organization context
                could not be determined.
                Existing notification history
                remains available.
              </p>
            </div>
          </div>
        </div>
      )}

      {/* ======================================================
          FILTERS
      ======================================================= */}
      <div className="flex gap-1 bg-gray-100 p-1 rounded-xl w-fit flex-wrap">
        {(
          [
            'all',
            'danger',
            'warning',
            'success',
            'info',
          ] as const
        ).map((filterValue) => (
          <button
            key={filterValue}
            type="button"
            onClick={() =>
              setFilter(
                filterValue,
              )
            }
            className={`px-3 py-1.5 rounded-lg text-xs font-medium capitalize transition ${
              filter ===
              filterValue
                ? 'bg-white shadow text-green-600'
                : 'text-gray-500 hover:text-gray-800'
            }`}
          >
            {filterValue ===
            'all'
              ? 'All'
              : `${ICON[filterValue]} ${filterValue
                  .charAt(0)
                  .toUpperCase()}${filterValue.slice(
                  1,
                )}`}
          </button>
        ))}
      </div>

      {/* ======================================================
          NOTIFICATION LIST
      ======================================================= */}
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
          (notification) => (
            <div
              key={
                notification.id
              }
              className={`relative rounded-2xl border p-5 transition ${
                BG[
                  notification.type
                ]
              } ${
                notification.realId &&
                !notification.read
                  ? 'ring-2 ring-offset-1 ring-teal-300'
                  : ''
              } ${
                notification.realtime
                  ? 'shadow-sm'
                  : ''
              }`}
            >
              {/* ==================================================
                  NEW REALTIME INDICATOR
              =================================================== */}
              {notification.realtime && (
                <div className="absolute top-3 right-3">
                  <span className="inline-flex items-center gap-1 rounded-full bg-green-100 px-2 py-1 text-[10px] font-semibold text-green-700">
                    <span className="h-1.5 w-1.5 rounded-full bg-green-500 animate-pulse" />
                    LIVE
                  </span>
                </div>
              )}

              <div className="flex items-start gap-4">
                <span className="text-2xl flex-shrink-0">
                  {
                    ICON[
                      notification.type
                    ]
                  }
                </span>

                <div className="flex-1 min-w-0 pr-12">
                  <div className="flex items-center justify-between gap-2 mb-1">
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

                  {/* =================================================
                      PAYMENT DETAILS
                  ================================================== */}
                  {notification.realtime &&
                    (notification.paymentId ||
                      notification.transactionId ||
                      notification.amount !==
                        undefined) && (
                      <div className="mt-3 flex flex-wrap gap-2">
                        {notification.amount !==
                          undefined && (
                          <span className="inline-flex items-center rounded-lg bg-white/70 border border-green-100 px-2.5 py-1 text-xs font-semibold text-green-700">
                            {formatMoney(
                              notification.amount,
                              'RWF',
                            )}
                          </span>
                        )}

                        {notification.paymentId && (
                          <span className="inline-flex items-center rounded-lg bg-white/70 border border-gray-100 px-2.5 py-1 text-xs text-gray-600">
                            Payment #
                            {
                              notification.paymentId
                            }
                          </span>
                        )}

                        {notification.transactionId && (
                          <span className="inline-flex items-center rounded-lg bg-white/70 border border-gray-100 px-2.5 py-1 text-xs text-gray-600 max-w-full truncate">
                            TX:
                            {' '}
                            {
                              notification.transactionId
                            }
                          </span>
                        )}
                      </div>
                    )}

                  {/* =================================================
                      ACTIONS
                  ================================================== */}
                  <div className="flex flex-wrap items-center gap-2 mt-3">
                    {notification.link && (
                      <Link
                        href={
                          notification.link
                        }
                        onClick={() => {
                          void handleMarkRead(
                            notification,
                          );
                        }}
                        className={`inline-block text-xs font-semibold px-3 py-1.5 rounded-lg transition ${
                          BTN[
                            notification.type
                          ]
                        }`}
                      >
                        {notification.realtime
                          ? 'View Payment →'
                          : 'Take action →'}
                      </Link>
                    )}

                    {notification.realId &&
                      !notification.read && (
                        <button
                          type="button"
                          onClick={() =>
                            void handleMarkRead(
                              notification,
                            )
                          }
                          className="inline-block text-xs font-medium px-3 py-1.5 rounded-lg bg-white/70 text-gray-600 hover:bg-white transition"
                        >
                          Mark as read
                        </button>
                      )}
                  </div>
                </div>
              </div>
            </div>
          ),
        )}
      </div>
    </div>
  );
}