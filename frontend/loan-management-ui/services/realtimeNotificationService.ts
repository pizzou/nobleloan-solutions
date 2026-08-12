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
  onPaymentReceived: (notification: PaymentNotification) => void;
  onConnected?: () => void;
  onDisconnected?: () => void;
  onError?: (error: unknown) => void;
}

let stompClient: Client | null = null;
let organizationSubscription: StompSubscription | null = null;

function getWebSocketUrl(): string {
  const configuredUrl =
    process.env.NEXT_PUBLIC_API_URL ||
    process.env.NEXT_PUBLIC_API_BASE_URL;

  if (!configuredUrl) {
    throw new Error(
      'NEXT_PUBLIC_API_URL or NEXT_PUBLIC_API_BASE_URL is not configured.'
    );
  }

  const normalized = configuredUrl.trim().replace(/\/+$/, '');

  const url = new URL(normalized);

  const protocol =
    url.protocol === 'https:' ? 'wss:' : 'ws:';

  /*
   * Spring Boot exposes:
   *
   *     /ws
   *
   * NOT:
   *
   *     /api/ws
   *
   * Therefore we deliberately use only the origin here.
   *
   * Example:
   *
   * API:
   * https://nobleloan-solutions.onrender.com/api
   *
   * WebSocket:
   * wss://nobleloan-solutions.onrender.com/ws
   */

  return `${protocol}//${url.host}/ws`;
}

export function connectToPaymentNotifications(
  organizationId: number,
  callbacks: RealtimePaymentCallbacks
): () => void {
  if (!organizationId) {
    const error = new Error(
      'Cannot connect to payment notifications without organizationId.'
    );

    callbacks.onError?.(error);

    throw error;
  }

  disconnectFromPaymentNotifications();

  let brokerURL: string;

  try {
    brokerURL = getWebSocketUrl();
  } catch (error) {
    callbacks.onError?.(error);
    throw error;
  }

  console.log(
    '[REALTIME] Connecting to:',
    brokerURL
  );

  const client = new Client({
    brokerURL,

    reconnectDelay: 5000,

    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,

    debug: (message: string) => {
      if (process.env.NODE_ENV === 'development') {
        console.debug('[STOMP]', message);
      }
    },
  });

  client.onConnect = () => {
    console.log(
      `[REALTIME] WebSocket connected. Organization=${organizationId}`
    );

    const destination =
      `/topic/organization/${organizationId}/payments`;

    console.log(
      '[REALTIME] Subscribing to:',
      destination
    );

    organizationSubscription =
      client.subscribe(
        destination,
        (message: IMessage) => {
          try {
            const notification =
              JSON.parse(
                message.body
              ) as PaymentNotification;

            if (
              Number(notification.organizationId) !==
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
              error
            );

            callbacks.onError?.(error);
          }
        }
      );

    callbacks.onConnected?.();
  };

  client.onDisconnect = () => {
    console.log(
      '[REALTIME] STOMP disconnected.'
    );

    organizationSubscription = null;

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

  client.onWebSocketError = (event) => {
    console.error(
      '[REALTIME] WebSocket error:',
      event
    );

    callbacks.onError?.(event);
  };

  client.onWebSocketClose = (event) => {
    console.warn(
      '[REALTIME] WebSocket closed:',
      event
    );

    organizationSubscription = null;

    callbacks.onDisconnected?.();
  };

  stompClient = client;

  client.activate();

  return () => {
    disconnectFromPaymentNotifications();
  };
}

export function disconnectFromPaymentNotifications(): void {
  if (organizationSubscription) {
    try {
      organizationSubscription.unsubscribe();
    } catch (error) {
      console.error(
        '[REALTIME] Failed to unsubscribe.',
        error
      );
    }

    organizationSubscription = null;
  }

  if (stompClient) {
    try {
      void stompClient.deactivate();
    } catch (error) {
      console.error(
        '[REALTIME] Failed to disconnect.',
        error
      );
    }

    stompClient = null;
  }
}