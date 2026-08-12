import { Client, IMessage, StompSubscription } from '@stomp/stompjs';

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
  const apiUrl =
    process.env.NEXT_PUBLIC_API_URL ||
    process.env.NEXT_PUBLIC_API_BASE_URL;

  if (!apiUrl) {
    throw new Error(
      'NEXT_PUBLIC_API_URL or NEXT_PUBLIC_API_BASE_URL is not configured.'
    );
  }

  const normalized = apiUrl.replace(/\/+$/, '');

  const url = new URL(normalized);

  const protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';

  return `${protocol}//${url.host}/ws`;
}

export function connectToPaymentNotifications(
  organizationId: number,
  callbacks: RealtimePaymentCallbacks
): () => void {
  if (!organizationId) {
    throw new Error(
      'Cannot connect to payment notifications without organizationId.'
    );
  }

  disconnectFromPaymentNotifications();

  const brokerURL = getWebSocketUrl();

  const client = new Client({
    brokerURL,

    reconnectDelay: 5000,

    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,

    debug: (message) => {
      if (process.env.NODE_ENV === 'development') {
        console.debug('[STOMP]', message);
      }
    },
  });

  client.onConnect = () => {
    if (process.env.NODE_ENV === 'development') {
      console.log(
        `[STOMP] Connected. Subscribing to organization ${organizationId} payment notifications.`
      );
    }

    const destination =
      `/topic/organization/${organizationId}/payments`;

    organizationSubscription = client.subscribe(
      destination,
      (message: IMessage) => {
        try {
          const notification =
            JSON.parse(message.body) as PaymentNotification;

          if (
            notification.organizationId !== organizationId
          ) {
            console.warn(
              '[STOMP] Ignoring notification for another organization.',
              notification
            );

            return;
          }

          callbacks.onPaymentReceived(notification);
        } catch (error) {
          console.error(
            '[STOMP] Failed to parse payment notification.',
            error
          );

          callbacks.onError?.(error);
        }
      }
    );

    callbacks.onConnected?.();
  };

  client.onDisconnect = () => {
    organizationSubscription = null;
    callbacks.onDisconnected?.();
  };

  client.onStompError = (frame) => {
    console.error(
      '[STOMP] Broker error:',
      frame.headers['message'],
      frame.body
    );

    callbacks.onError?.(frame);
  };

  client.onWebSocketError = (event) => {
    console.error(
      '[STOMP] WebSocket error:',
      event
    );

    callbacks.onError?.(event);
  };

  client.onWebSocketClose = () => {
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
        '[STOMP] Failed to unsubscribe.',
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
        '[STOMP] Failed to disconnect.',
        error
      );
    }

    stompClient = null;
  }
}