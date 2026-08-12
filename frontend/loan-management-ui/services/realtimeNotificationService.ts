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

function getWebSocketUrl(): string {
  const apiUrl =
    process.env.NEXT_PUBLIC_API_URL ||
    process.env.NEXT_PUBLIC_API_BASE_URL;

  if (!apiUrl) {
    throw new Error(
      'NEXT_PUBLIC_API_URL or NEXT_PUBLIC_API_BASE_URL is not configured.'
    );
  }

  const parsed = new URL(apiUrl);

  const protocol =
    parsed.protocol === 'https:' ? 'wss:' : 'ws:';

  /*
   * IMPORTANT:
   *
   * Do NOT use parsed.pathname.
   *
   * If API URL is:
   *
   * https://nobleloan-solutions.onrender.com/api
   *
   * the WebSocket endpoint is:
   *
   * wss://nobleloan-solutions.onrender.com/ws
   *
   * NOT:
   *
   * wss://nobleloan-solutions.onrender.com/api/ws
   */

  return `${protocol}//${parsed.host}/ws`;
}

function normalizeOrganizationId(
  organizationId: number | string
): number {
  const value = Number(organizationId);

  if (!Number.isInteger(value) || value <= 0) {
    throw new Error(
      `Invalid organizationId: ${organizationId}`
    );
  }

  return value;
}

export function connectToPaymentNotifications(
  organizationId: number | string,
  callbacks: RealtimePaymentCallbacks
): () => void {
  const normalizedOrganizationId =
    normalizeOrganizationId(organizationId);

  disconnectFromPaymentNotifications();

  const brokerURL = getWebSocketUrl();

  const destination =
    `/topic/organization/${normalizedOrganizationId}/payments`;

  console.log(
    '[REALTIME PAYMENT] Connecting to:',
    brokerURL
  );

  console.log(
    '[REALTIME PAYMENT] Subscribing to:',
    destination
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

  client.onConnect = (_frame: IFrame) => {
    console.log(
      '[REALTIME PAYMENT] WebSocket connected.'
    );

    if (organizationSubscription) {
      try {
        organizationSubscription.unsubscribe();
      } catch (error) {
        console.warn(
          '[REALTIME PAYMENT] Previous subscription cleanup failed.',
          error
        );
      }

      organizationSubscription = null;
    }

    organizationSubscription =
      client.subscribe(
        destination,
        (message: IMessage) => {
          try {
            if (!message.body) {
              console.warn(
                '[REALTIME PAYMENT] Empty notification received.'
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
              notificationOrganizationId !==
              normalizedOrganizationId
            ) {
              console.warn(
                '[REALTIME PAYMENT] Ignoring notification for another organization.',
                notification
              );

              return;
            }

            console.log(
              '[REALTIME PAYMENT] PAYMENT NOTIFICATION RECEIVED:',
              notification
            );

            callbacks.onPaymentReceived(
              notification
            );
          } catch (error) {
            console.error(
              '[REALTIME PAYMENT] Failed to parse notification.',
              error
            );

            callbacks.onError?.(error);
          }
        }
      );

    console.log(
      '[REALTIME PAYMENT] Subscription established:',
      destination
    );

    callbacks.onConnected?.();
  };

  client.onStompError = (frame: IFrame) => {
    console.error(
      '[REALTIME PAYMENT] STOMP error:',
      frame.headers['message'],
      frame.body
    );

    callbacks.onError?.(frame);
  };

  client.onWebSocketError = (event: Event) => {
    console.error(
      '[REALTIME PAYMENT] WebSocket error:',
      event
    );

    callbacks.onError?.(event);
  };

  client.onWebSocketClose = (event: CloseEvent) => {
    organizationSubscription = null;

    console.warn(
      '[REALTIME PAYMENT] WebSocket closed.',
      {
        code: event.code,
        reason: event.reason,
      }
    );

    callbacks.onDisconnected?.();
  };

  client.onDisconnect = () => {
    organizationSubscription = null;

    console.log(
      '[REALTIME PAYMENT] STOMP disconnected.'
    );

    callbacks.onDisconnected?.();
  };

  stompClient = client;

  client.activate();

  return () => {
    if (stompClient === client) {
      disconnectFromPaymentNotifications();
    }
  };
}

export function disconnectFromPaymentNotifications(): void {
  if (organizationSubscription) {
    try {
      organizationSubscription.unsubscribe();
    } catch (error) {
      console.warn(
        '[REALTIME PAYMENT] Failed to unsubscribe.',
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
        '[REALTIME PAYMENT] Failed to deactivate STOMP client.',
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