import {
  Client,
  IMessage,
  StompSubscription,
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
  onPaymentReceived: (
    notification: PaymentNotification
  ) => void;

  onConnected?: () => void;

  onDisconnected?: () => void;

  onError?: (error: unknown) => void;
}

let stompClient: Client | null = null;
let organizationSubscription: StompSubscription | null = null;

let activeOrganizationId: number | null = null;

function getApiBaseUrl(): string {
  const apiUrl =
    process.env.NEXT_PUBLIC_API_URL ||
    process.env.NEXT_PUBLIC_API_BASE_URL;

  if (!apiUrl) {
    throw new Error(
      'NEXT_PUBLIC_API_URL or NEXT_PUBLIC_API_BASE_URL is not configured.'
    );
  }

  return apiUrl.replace(/\/+$/, '');
}

function getWebSocketUrl(): string {
  const apiBaseUrl = getApiBaseUrl();

  const parsedUrl = new URL(apiBaseUrl);

  const protocol =
    parsedUrl.protocol === 'https:'
      ? 'wss:'
      : 'ws:';

  /*
   * IMPORTANT:
   *
   * Backend:
   *
   * registry.addEndpoint("/ws")
   *
   * Therefore this MUST be:
   *
   * wss://nobleloan-solutions.onrender.com/ws
   *
   * NOT:
   *
   * wss://nobleloan-solutions.onrender.com/api/ws
   */
  return `${protocol}//${parsedUrl.host}/ws`;
}

export function connectToPaymentNotifications(
  organizationId: number,
  callbacks: RealtimePaymentCallbacks
): () => void {
  if (
    !Number.isInteger(organizationId) ||
    organizationId <= 0
  ) {
    const error = new Error(
      'Cannot connect to payment notifications without a valid organizationId.'
    );

    callbacks.onError?.(error);

    return () => undefined;
  }

  disconnectFromPaymentNotifications();

  activeOrganizationId = organizationId;

  let brokerURL: string;

  try {
    brokerURL = getWebSocketUrl();
  } catch (error) {
    console.error(
      '[REALTIME] Failed to construct WebSocket URL.',
      error
    );

    callbacks.onError?.(error);

    return () => undefined;
  }

  console.log(
    `[REALTIME] Connecting to: ${brokerURL}`
  );

  const client = new Client({
    brokerURL,

    reconnectDelay: 5000,

    heartbeatIncoming: 10000,

    heartbeatOutgoing: 10000,

    connectionTimeout: 10000,

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

  client.onConnect = () => {
    if (stompClient !== client) {
      return;
    }

    console.log(
      `[REALTIME] WebSocket connected. Organization=${organizationId}`
    );

    const destination =
      `/topic/organization/${organizationId}/payments`;

    console.log(
      `[REALTIME] Subscribing to: ${destination}`
    );

    try {
      organizationSubscription =
        client.subscribe(
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

              if (
                Number(
                  notification.organizationId
                ) !==
                Number(organizationId)
              ) {
                console.warn(
                  '[REALTIME] Ignoring notification for another organization.',
                  notification
                );

                return;
              }

              console.log(
                '[REALTIME] Payment notification received:',
                notification
              );

              callbacks.onPaymentReceived(
                notification
              );
            } catch (error) {
              console.error(
                '[REALTIME] Failed to parse payment notification.',
                error,
                message.body
              );

              callbacks.onError?.(
                error
              );
            }
          }
        );

      console.log(
        `[REALTIME] Successfully subscribed to: ${destination}`
      );

      callbacks.onConnected?.();
    } catch (error) {
      console.error(
        '[REALTIME] Failed to subscribe to payment destination.',
        error
      );

      callbacks.onError?.(error);
    }
  };

  client.onDisconnect = () => {
    console.warn(
      '[REALTIME] WebSocket disconnected.'
    );

    organizationSubscription =
      null;

    callbacks.onDisconnected?.();
  };

  client.onStompError = (frame) => {
    console.error(
      '[REALTIME] STOMP broker error:',
      frame.headers['message'],
      frame.body
    );

    callbacks.onError?.(frame);
  };

  client.onWebSocketError = (
    event
  ) => {
    console.error(
      '[REALTIME] WebSocket error:',
      event
    );

    callbacks.onError?.(
      event
    );
  };

  client.onWebSocketClose = (
    event
  ) => {
    console.warn(
      '[REALTIME] WebSocket closed:',
      event
    );

    organizationSubscription =
      null;

    callbacks.onDisconnected?.();
  };

  stompClient = client;

  client.activate();

  return () => {
    if (
      stompClient === client
    ) {
      disconnectFromPaymentNotifications();
    }
  };
}

export function disconnectFromPaymentNotifications(): void {
  activeOrganizationId = null;

  if (
    organizationSubscription
  ) {
    try {
      organizationSubscription.unsubscribe();
    } catch (error) {
      console.error(
        '[REALTIME] Failed to unsubscribe.',
        error
      );
    }

    organizationSubscription =
      null;
  }

  if (stompClient) {
    const client =
      stompClient;

    stompClient = null;

    try {
      void client.deactivate();
    } catch (error) {
      console.error(
        '[REALTIME] Failed to disconnect WebSocket.',
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

export function getActivePaymentOrganizationId():
  number | null {
  return activeOrganizationId;
}