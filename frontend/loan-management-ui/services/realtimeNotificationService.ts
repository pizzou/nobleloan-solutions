import {
  Client,
  IMessage,
  StompSubscription,
  IFrame,
} from "@stomp/stompjs";

export interface PaymentNotification {
  paymentId: number;
  loanId: number;
  loanReference?: string;
  borrowerId?: number;
  borrowerName?: string;

  organizationId: number;

  amount: number | string;
  principalPaid?: number | string;
  interestPaid?: number | string;
  penaltyPaid?: number | string;

  outstandingBalance?: number | string;

  totalInterestPaid?: number | string;
  totalInterestDue?: number | string;
  remainingInterest?: number | string;

  totalPrincipalPaid?: number | string;

  totalPenalty?: number | string;
  totalPenaltyPaid?: number | string;
  remainingPenalty?: number | string;

  currency?: string;
  paymentMethod?: string;
  channel?: string;

  transactionId?: string;
  paymentReference?: string;

  paymentStatus?: string;
  loanStatus?: string;

  paymentDate?: string;
  paymentTimestamp?: string;

  title?: string;
  message?: string;
}
export interface DisplayNotification {
  id: string;

  type: string;

  title: string;

  message?: string;

  organizationId?: number;

  loanId?: number;

  paymentId?: number;

  loanReference?: string;

  borrowerId?: number;

  borrowerName?: string;

  amount?: number | string;

  principalPaid?: number | string;

  interestPaid?: number | string;

  penaltyPaid?: number | string;

  outstandingBalance?: number | string;

  currency?: string;

  paymentMethod?: string;

  channel?: string;

  transactionId?: string;

  paymentReference?: string;

  paymentStatus?: string;

  loanStatus?: string;

  paymentTimestamp?: string;

  createdAt?: string;

  read: boolean;
}
export interface RealtimePaymentCallbacks {
  onPaymentReceived: (notification: PaymentNotification) => void;
  onConnected?: () => void;
  onDisconnected?: () => void;
  onError?: (error: unknown) => void;
}

let stompClient: Client | null = null;
let organizationSubscription: StompSubscription | null = null;

let activeOrganizationId: number | null = null;

function getWebSocketUrl(): string {
  const apiUrl =
    process.env.NEXT_PUBLIC_API_URL ||
    process.env.NEXT_PUBLIC_API_BASE_URL;

  if (!apiUrl) {
    throw new Error(
      "NEXT_PUBLIC_API_URL or NEXT_PUBLIC_API_BASE_URL is not configured."
    );
  }

  const normalized = apiUrl.replace(/\/+$/, "");

  const url = new URL(normalized);

  const protocol =
    url.protocol === "https:" ? "wss:" : "ws:";

  return `${protocol}//${url.host}/ws`;
}

function safelyParseNotification(
  message: IMessage
): PaymentNotification {
  const parsed = JSON.parse(message.body);

  if (!parsed || typeof parsed !== "object") {
    throw new Error(
      "Realtime payment notification is not a valid object."
    );
  }

  if (
    parsed.organizationId === undefined ||
    parsed.organizationId === null
  ) {
    throw new Error(
      "Realtime payment notification is missing organizationId."
    );
  }

  if (
    parsed.paymentId === undefined ||
    parsed.paymentId === null
  ) {
    throw new Error(
      "Realtime payment notification is missing paymentId."
    );
  }

  if (
    parsed.loanId === undefined ||
    parsed.loanId === null
  ) {
    throw new Error(
      "Realtime payment notification is missing loanId."
    );
  }

  return parsed as PaymentNotification;
}

export function connectToPaymentNotifications(
  organizationId: number,
  callbacks: RealtimePaymentCallbacks
): () => void {
  if (!organizationId) {
    throw new Error(
      "Cannot connect to payment notifications without organizationId."
    );
  }

  disconnectFromPaymentNotifications();

  activeOrganizationId = organizationId;

  const brokerURL = getWebSocketUrl();

  console.info(
    `[REALTIME] Connecting to: ${brokerURL}`
  );

  const client = new Client({
    brokerURL,

    reconnectDelay: 5000,

    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,

    connectionTimeout: 15000,

    debug: (message: string) => {
      if (process.env.NODE_ENV === "development") {
        console.debug("[STOMP]", message);
      }
    },
  });

  client.onConnect = (_frame: IFrame) => {
    console.info(
      `[REALTIME] WebSocket connected. Organization=${organizationId}`
    );

    const destination =
      `/topic/organization/${organizationId}/payments`;

    console.info(
      `[REALTIME] Subscribing to: ${destination}`
    );

    try {
      organizationSubscription =
        client.subscribe(
          destination,
          (message: IMessage) => {
            try {
              const notification =
                safelyParseNotification(message);

              const notificationOrganizationId =
                Number(
                  notification.organizationId
                );

              if (
                notificationOrganizationId !==
                organizationId
              ) {
                console.warn(
                  "[REALTIME] Ignoring payment notification for another organization.",
                  notification
                );

                return;
              }

              console.info(
                "[REALTIME] Payment notification received:",
                notification
              );

              callbacks.onPaymentReceived(
                notification
              );
            } catch (error) {
              console.error(
                "[REALTIME] Failed to parse payment notification:",
                error
              );

              callbacks.onError?.(error);
            }
          }
        );

      console.info(
        `[REALTIME] Successfully subscribed to: ${destination}`
      );

      callbacks.onConnected?.();
    } catch (error) {
      console.error(
        "[REALTIME] Failed to subscribe to payment notifications:",
        error
      );

      callbacks.onError?.(error);
    }
  };

  client.onDisconnect = () => {
    console.warn(
      "[REALTIME] WebSocket disconnected."
    );

    organizationSubscription = null;

    callbacks.onDisconnected?.();
  };

  client.onStompError = (frame: IFrame) => {
    console.error(
      "[REALTIME] STOMP broker error:",
      frame.headers["message"],
      frame.body
    );

    callbacks.onError?.(
      new Error(
        frame.headers["message"] ||
          "STOMP broker error."
      )
    );
  };

  client.onWebSocketError = (event: Event) => {
    console.error(
      "[REALTIME] WebSocket error:",
      event
    );

    callbacks.onError?.(event);
  };

  client.onWebSocketClose = (event: CloseEvent) => {
    console.warn(
      "[REALTIME] WebSocket closed:",
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
  activeOrganizationId = null;

  if (organizationSubscription) {
    try {
      organizationSubscription.unsubscribe();
    } catch (error) {
      console.error(
        "[REALTIME] Failed to unsubscribe:",
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
      console.error(
        "[REALTIME] Failed to disconnect:",
        error
      );
    }
  }
}

export function isPaymentRealtimeConnected(): boolean {
  return Boolean(
    stompClient?.connected
  );
}

export function getActivePaymentOrganizationId():
  | number
  | null {
  return activeOrganizationId;
}