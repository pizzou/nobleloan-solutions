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
 * Returns the backend WebSocket endpoint.
 *
 * Example:
 *
 * NEXT_PUBLIC_API_URL=https://loansaas-backend.onrender.com/api
 *
 * becomes:
 *
 * wss://loansaas-backend.onrender.com/ws
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

  let normalizedApiUrl = apiUrl.trim();

  /*
   * Remove trailing slashes.
   */
  normalizedApiUrl = normalizedApiUrl.replace(/\/+$/, '');

  let parsedUrl: URL;

  try {
    parsedUrl = new URL(normalizedApiUrl);
  } catch (error) {
    throw new Error(
      `Invalid API URL configured for realtime notifications: ${normalizedApiUrl}`
    );
  }

  /*
   * Spring WebSocket endpoint is:
   *
   * /ws
   *
   * We intentionally use only the host and port here.
   *
   * This prevents:
   *
   * https://backend.com/api
   *
   * from becoming:
   *
   * wss://backend.com/api/ws
   *
   * which would be wrong for the Spring configuration:
   *
   * registry.addEndpoint("/ws")
   */
  const protocol =
    parsedUrl.protocol === 'https:'
      ? 'wss:'
      : 'ws:';

  return `${protocol}//${parsedUrl.host}/ws`;
}

/**
 * Safely converts a value to a number.
 */
function toNumber(value: unknown): number | null {
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : null;
  }

  if (typeof value === 'string') {
    const parsed = Number(value);

    return Number.isFinite(parsed)
      ? parsed
      : null;
  }

  return null;
}

/**
 * Validates and normalizes an incoming payment notification.
 */
function normalizePaymentNotification(
  raw: unknown
): PaymentNotification | null {
  if (!raw || typeof raw !== 'object') {
    return null;
  }

  const value = raw as Record<string, unknown>;

  const organizationId =
    toNumber(value.organizationId);

  const loanId =
    toNumber(value.loanId);

  const paymentId =
    toNumber(value.paymentId);

  if (
    organizationId === null ||
    loanId === null ||
    paymentId === null
  ) {
    console.error(
      '[STOMP] Invalid payment notification received:',
      raw
    );

    return null;
  }

  if (
    value.amount === undefined ||
    value.amount === null
  ) {
    console.error(
      '[STOMP] Payment notification has no amount:',
      raw
    );

    return null;
  }

  return {
    organizationId,
    loanId,
    paymentId,
    amount:
      typeof value.amount === 'number' ||
      typeof value.amount === 'string'
        ? value.amount
        : String(value.amount),

    transactionId:
      value.transactionId != null
        ? String(value.transactionId)
        : undefined,

    paymentMethod:
      value.paymentMethod != null
        ? String(value.paymentMethod)
        : undefined,

    paymentStatus:
      value.paymentStatus != null
        ? String(value.paymentStatus)
        : undefined,

    outstandingBalance:
      value.outstandingBalance != null
        ? (
            typeof value.outstandingBalance === 'number' ||
            typeof value.outstandingBalance === 'string'
              ? value.outstandingBalance
              : String(value.outstandingBalance)
          )
        : undefined,

    borrowerName:
      value.borrowerName != null
        ? String(value.borrowerName)
        : undefined,

    loanNumber:
      value.loanNumber != null
        ? String(value.loanNumber)
        : undefined,

    message:
      value.message != null
        ? String(value.message)
        : undefined,

    createdAt:
      value.createdAt != null
        ? String(value.createdAt)
        : undefined,
  };
}

/**
 * Disconnects the existing payment realtime connection.
 *
 * This is intentionally asynchronous internally, but the public
 * function remains simple for callers.
 */
export function disconnectFromPaymentNotifications(): void {
  if (organizationSubscription) {
    try {
      organizationSubscription.unsubscribe();
    } catch (error) {
      console.error(
        '[STOMP] Failed to unsubscribe from payment topic.',
        error
      );
    }

    organizationSubscription = null;
  }

  currentOrganizationId = null;

  if (stompClient) {
    const client = stompClient;

    stompClient = null;

    try {
      void client.deactivate();
    } catch (error) {
      console.error(
        '[STOMP] Failed to deactivate payment realtime client.',
        error
      );
    }
  }
}

/**
 * Connects the dashboard to realtime payment notifications.
 *
 * Backend:
 *
 * /ws
 *
 * Organization topic:
 *
 * /topic/organization/{organizationId}/payments
 */
export function connectToPaymentNotifications(
  organizationId: number,
  callbacks: RealtimePaymentCallbacks
): () => void {
  if (
    !organizationId ||
    !Number.isFinite(organizationId)
  ) {
    const error = new Error(
      'Cannot connect to payment notifications without a valid organizationId.'
    );

    console.error(
      '[STOMP]',
      error.message
    );

    callbacks.onError?.(error);

    return () => undefined;
  }

  /*
   * Do not create another connection if this exact organization
   * is already connected.
   */
  if (
    stompClient &&
    currentOrganizationId === organizationId
  ) {
    if (process.env.NODE_ENV === 'development') {
      console.debug(
        `[STOMP] Existing payment connection already active for organization ${organizationId}.`
      );
    }

    return () => {
      disconnectFromPaymentNotifications();
    };
  }

  /*
   * If another organization is currently connected, close it first.
   */
  if (stompClient) {
    disconnectFromPaymentNotifications();
  }

  let brokerURL: string;

  try {
    brokerURL = getWebSocketUrl();
  } catch (error) {
    console.error(
      '[STOMP] Failed to build WebSocket URL.',
      error
    );

    callbacks.onError?.(error);

    return () => undefined;
  }

  if (process.env.NODE_ENV === 'development') {
    console.debug(
      '[STOMP] Payment WebSocket URL:',
      brokerURL
    );

    console.debug(
      '[STOMP] Organization:',
      organizationId
    );

    console.debug(
      '[STOMP] Payment topic:',
      `/topic/organization/${organizationId}/payments`
    );
  }

  const client = new Client({
    brokerURL,

    /*
     * Production-friendly reconnect.
     *
     * The browser will continue reconnecting if the backend,
     * Render instance, network, or connection temporarily goes down.
     */
    reconnectDelay: 5000,

    /*
     * STOMP heartbeats.
     */
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,

    /*
     * Prevent a dead connection from remaining open forever.
     */
    connectionTimeout: 10000,

    /*
     * Debug only in development.
     */
    debug: (message: string) => {
      if (
        process.env.NODE_ENV === 'development'
      ) {
        console.debug(
          '[STOMP]',
          message
        );
      }
    },
  });

  /**
   * Successful STOMP connection.
   */
  client.onConnect = (
    frame: IFrame
  ) => {
    if (process.env.NODE_ENV === 'development') {
      console.debug(
        '[STOMP] Connected to payment realtime broker.',
        frame.headers
      );
    }

    /*
     * Remove an old subscription before creating a new one.
     */
    if (organizationSubscription) {
      try {
        organizationSubscription.unsubscribe();
      } catch (error) {
        console.error(
          '[STOMP] Failed to remove previous payment subscription.',
          error
        );
      }

      organizationSubscription = null;
    }

    const destination =
      `/topic/organization/${organizationId}/payments`;

    try {
      organizationSubscription =
        client.subscribe(
          destination,
          (message: IMessage) => {
            try {
              if (!message.body) {
                console.warn(
                  '[STOMP] Empty payment notification received.'
                );

                return;
              }

              const parsed =
                JSON.parse(message.body);

              const notification =
                normalizePaymentNotification(
                  parsed
                );

              if (!notification) {
                callbacks.onError?.(
                  new Error(
                    'Invalid payment notification payload.'
                  )
                );

                return;
              }

              /*
               * Multi-tenant safety.
               *
               * Never allow a notification belonging to another
               * organization to appear in this dashboard.
               */
              if (
                notification.organizationId !==
                organizationId
              ) {
                console.warn(
                  '[STOMP] Ignoring payment notification for another organization.',
                  {
                    expectedOrganizationId:
                      organizationId,

                    receivedOrganizationId:
                      notification.organizationId,

                    loanId:
                      notification.loanId,

                    paymentId:
                      notification.paymentId,
                  }
                );

                return;
              }

              if (process.env.NODE_ENV === 'development') {
                console.debug(
                  '[STOMP] PAYMENT NOTIFICATION RECEIVED:',
                  notification
                );
              }

              callbacks.onPaymentReceived(
                notification
              );
            } catch (error) {
              console.error(
                '[STOMP] Failed to process payment notification.',
                error
              );

              callbacks.onError?.(
                error
              );
            }
          }
        );

      currentOrganizationId =
        organizationId;

      callbacks.onConnected?.();

      console.info(
        `[STOMP] Payment realtime notifications connected. Organization=${organizationId}`
      );
    } catch (error) {
      console.error(
        '[STOMP] Failed to subscribe to payment topic.',
        error
      );

      callbacks.onError?.(
        error
      );
    }
  };

  /**
   * STOMP broker-level error.
   */
  client.onStompError = (
    frame: IFrame
  ) => {
    console.error(
      '[STOMP] Broker error.',
      {
        message:
          frame.headers?.message,

        details:
          frame.headers,

        body:
          frame.body,
      }
    );

    callbacks.onError?.(
      frame
    );
  };

  /**
   * Raw WebSocket error.
   */
  client.onWebSocketError = (
    event: Event
  ) => {
    console.error(
      '[STOMP] WebSocket connection error.',
      event
    );

    callbacks.onError?.(
      event
    );
  };

  /**
   * WebSocket closed.
   *
   * STOMP will automatically reconnect because
   * reconnectDelay is configured.
   */
  client.onWebSocketClose = (
    event: CloseEvent
  ) => {
    organizationSubscription = null;

    console.warn(
      '[STOMP] Payment WebSocket connection closed.',
      {
        code: event.code,
        reason: event.reason,
        wasClean: event.wasClean,
      }
    );

    callbacks.onDisconnected?.();
  };

  /**
   * STOMP disconnect.
   */
  client.onDisconnect = () => {
    organizationSubscription = null;

    if (process.env.NODE_ENV === 'development') {
      console.debug(
        '[STOMP] Payment STOMP connection disconnected.'
      );
    }

    callbacks.onDisconnected?.();
  };

  /*
   * Save the client globally.
   */
  stompClient = client;
  currentOrganizationId = organizationId;

  /*
   * Start connection.
   */
  try {
    client.activate();
  } catch (error) {
    console.error(
      '[STOMP] Failed to activate payment realtime client.',
      error
    );

    stompClient = null;
    currentOrganizationId = null;

    callbacks.onError?.(
      error
    );
  }

  /*
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