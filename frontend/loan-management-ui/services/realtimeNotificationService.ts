import {
  Client,
  IMessage,
  StompSubscription,
  IFrame,
} from '@stomp/stompjs';

export interface PaymentNotification {
  organizationId: number;
  loanId: number;
  paymentId: number;

  amount: number | string;

  transactionId?: string;
  paymentMethod?: string;
  paymentStatus?: string;

  outstandingBalance?: number | string;

  borrowerName?: string;
  loanNumber?: string;

  message?: string;
  createdAt?: string;
}

export interface RealtimePaymentCallbacks {
  onPaymentReceived: (notification: PaymentNotification) => void;

  onConnected?: () => void;
  onDisconnected?: () => void;

  onError?: (error: unknown) => void;
}

let stompClient: Client | null = null;
let organizationSubscription: StompSubscription | null = null;

let currentOrganizationId: number | null = null;

/**
 * Converts the configured HTTP API URL into the WebSocket URL.
 *
 * Examples:
 *
 * https://loansaas-backend.onrender.com
 *       -> wss://loansaas-backend.onrender.com/ws
 *
 * http://localhost:8080
 *       -> ws://localhost:8080/ws
 *
 * The /api portion of the REST API URL is deliberately removed.
 * WebSocketConfig exposes /ws at the server root.
 */
function getWebSocketUrl(): string {
  const apiUrl =
    process.env.NEXT_PUBLIC_API_URL ||
    process.env.NEXT_PUBLIC_API_BASE_URL;

  if (!apiUrl) {
    throw new Error(
      'NEXT_PUBLIC_API_URL or NEXT_PUBLIC_API_BASE_URL is not configured.'
    );
  }

  let normalized = apiUrl.trim();

  // Remove trailing slashes.
  normalized = normalized.replace(/\/+$/, '');

  // Remove REST API suffixes because WebSocket endpoint is /ws.
  normalized = normalized.replace(/\/api$/i, '');

  // Remove accidental /api/... suffixes.
  normalized = normalized.replace(/\/api\/.*$/i, '');

  const url = new URL(normalized);

  let protocol: string;

  if (url.protocol === 'https:') {
    protocol = 'wss:';
  } else if (url.protocol === 'http:') {
    protocol = 'ws:';
  } else {
    throw new Error(
      `Unsupported API URL protocol: ${url.protocol}`
    );
  }

  return `${protocol}//${url.host}/ws`;
}

/**
 * Safely converts organization ID values.
 */
function normalizeOrganizationId(
  organizationId: number | string
): number {
  const parsed = Number(organizationId);

  if (!Number.isInteger(parsed) || parsed <= 0) {
    throw new Error(
      `Invalid organizationId for realtime notifications: ${organizationId}`
    );
  }

  return parsed;
}

/**
 * Connects the dashboard to the backend STOMP WebSocket
 * and subscribes to organization-level payment notifications.
 */
export function connectToPaymentNotifications(
  organizationId: number | string,
  callbacks: RealtimePaymentCallbacks
): () => void {
  const normalizedOrganizationId =
    normalizeOrganizationId(organizationId);

  // Always close an existing connection before creating another.
  disconnectFromPaymentNotifications();

  currentOrganizationId = normalizedOrganizationId;

  const brokerURL = getWebSocketUrl();

  if (process.env.NODE_ENV === 'development') {
    console.log(
      '[REALTIME] WebSocket URL:',
      brokerURL
    );

    console.log(
      '[REALTIME] Organization:',
      normalizedOrganizationId
    );

    console.log(
      '[REALTIME] Subscription:',
      `/topic/organization/${normalizedOrganizationId}/payments`
    );
  }

  const client = new Client({
    brokerURL,

    reconnectDelay: 5000,

    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,

    connectHeaders: {},

    debug: (message: string) => {
      if (process.env.NODE_ENV === 'development') {
        console.debug('[STOMP]', message);
      }
    },
  });

  /**
   * Successful STOMP connection.
   */
  client.onConnect = (_frame: IFrame) => {
    if (process.env.NODE_ENV === 'development') {
      console.log(
        '[REALTIME] WebSocket/STOMP connected.'
      );
    }

    // Prevent an accidental duplicate subscription.
    if (organizationSubscription) {
      try {
        organizationSubscription.unsubscribe();
      } catch (error) {
        console.warn(
          '[REALTIME] Existing subscription cleanup failed.',
          error
        );
      }

      organizationSubscription = null;
    }

    const destination =
      `/topic/organization/${normalizedOrganizationId}/payments`;

    organizationSubscription = client.subscribe(
      destination,
      (message: IMessage) => {
        try {
          if (!message.body) {
            console.warn(
              '[REALTIME] Received empty payment notification.'
            );

            return;
          }

          const notification =
            JSON.parse(
              message.body
            ) as PaymentNotification;

          const notificationOrganizationId =
            Number(
              notification.organizationId
            );

          if (
            !Number.isInteger(
              notificationOrganizationId
            )
          ) {
            console.warn(
              '[REALTIME] Ignoring payment notification with invalid organizationId.',
              notification
            );

            return;
          }

          if (
            notificationOrganizationId !==
            normalizedOrganizationId
          ) {
            console.warn(
              '[REALTIME] Ignoring payment notification belonging to another organization.',
              {
                expected:
                  normalizedOrganizationId,
                received:
                  notificationOrganizationId,
              }
            );

            return;
          }

          if (
            !notification.loanId ||
            !notification.paymentId
          ) {
            console.warn(
              '[REALTIME] Ignoring malformed payment notification.',
              notification
            );

            return;
          }

          if (process.env.NODE_ENV === 'development') {
            console.log(
              '[REALTIME] PAYMENT NOTIFICATION RECEIVED.',
              notification
            );
          }

          callbacks.onPaymentReceived(
            notification
          );
        } catch (error) {
          console.error(
            '[REALTIME] Failed to parse payment notification.',
            error,
            message.body
          );

          callbacks.onError?.(error);
        }
      }
    );

    if (process.env.NODE_ENV === 'development') {
      console.log(
        '[REALTIME] Subscribed successfully:',
        destination
      );
    }

    callbacks.onConnected?.();
  };

  /**
   * STOMP broker error.
   */
  client.onStompError = (frame: IFrame) => {
    console.error(
      '[REALTIME] STOMP broker error:',
      frame.headers['message'],
      frame.body
    );

    callbacks.onError?.(
      new Error(
        frame.headers['message'] ||
          'STOMP broker error'
      )
    );
  };

  client.onWebSocketError = (event: Event) => {
    console.error(
      '[REALTIME] WebSocket error:',
      event
    );

    callbacks.onError?.(event);
  };

 
  client.onWebSocketClose = (event: CloseEvent) => {
    organizationSubscription = null;

    if (process.env.NODE_ENV === 'development') {
      console.warn(
        '[REALTIME] WebSocket closed.',
        {
          code: event.code,
          reason: event.reason,
        }
      );
    }

    callbacks.onDisconnected?.();
  };

  /**
   * STOMP disconnect.
   */
  client.onDisconnect = () => {
    organizationSubscription = null;

    if (process.env.NODE_ENV === 'development') {
      console.log(
        '[REALTIME] STOMP disconnected.'
      );
    }

    callbacks.onDisconnected?.();
  };

  stompClient = client;

  client.activate();

  /**
   * Cleanup function for React useEffect.
   */
  return () => {
    if (
      stompClient === client
    ) {
      disconnectFromPaymentNotifications();
    }
  };
}

/**
 * Disconnects the current realtime connection.
 */
export function disconnectFromPaymentNotifications(): void {
  currentOrganizationId = null;

  if (organizationSubscription) {
    try {
      organizationSubscription.unsubscribe();
    } catch (error) {
      console.warn(
        '[REALTIME] Failed to unsubscribe.',
        error
      );
    }

    organizationSubscription = null;
  }

  if (stompClient) {
    const client = stompClient;

    stompClient = null;

    try {
      void client.deactivate();
    } catch (error) {
      console.warn(
        '[REALTIME] Failed to deactivate STOMP client.',
        error
      );
    }
  }
}


export function isPaymentRealtimeConnected(): boolean {
  return (
    stompClient !== null &&
    stompClient.connected
  );
}

/**
 * Returns the organization currently connected to realtime notifications.
 */
export function getRealtimeOrganizationId(): number | null {
  return currentOrganizationId;
}