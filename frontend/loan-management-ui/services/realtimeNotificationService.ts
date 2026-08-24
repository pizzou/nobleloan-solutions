interface StompMessage {
  body: string;
  headers?: Record<string, string>;
}

interface StompFrame {
  headers: Record<string, string>;
  body: string;
}

interface StompSubscription {
  id: string;
  unsubscribe(headers?: Record<string, string>): void;
}

interface StompClient {
  connected: boolean;

  onConnect?: (frame: StompFrame) => void;

  onDisconnect?: (frame: StompFrame) => void;

  onStompError?: (frame: StompFrame) => void;

  onWebSocketError?: (event: Event) => void;

  onWebSocketClose?: (event: CloseEvent) => void;

  subscribe(
    destination: string,
    callback: (message: StompMessage) => void,
  ): StompSubscription;

  activate(): void;

  deactivate(): Promise<void>;
}

interface StompModule {
  Client: new (config: {
    brokerURL: string;
    reconnectDelay?: number;
    heartbeatIncoming?: number;
    heartbeatOutgoing?: number;
    connectionTimeout?: number;
    debug?: (message: string) => void;
  }) => StompClient;
}

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

export interface RealtimePaymentCallbacks {
  onPaymentReceived: (notification: PaymentNotification) => void;

  onConnected?: () => void;

  onDisconnected?: () => void;

  onError?: (error: unknown) => void;
}

let stompClient: StompClient | null = null;

let organizationSubscription: StompSubscription | null = null;

let activeOrganizationId: number | null = null;

let connectionGeneration = 0;

/**
 * ============================================================
 * WEBSOCKET URL
 * ============================================================
 */

function getWebSocketUrl(): string {
  const apiUrl =
    process.env.NEXT_PUBLIC_API_URL || process.env.NEXT_PUBLIC_API_BASE_URL;

  if (!apiUrl) {
    throw new Error(
      "NEXT_PUBLIC_API_URL or NEXT_PUBLIC_API_BASE_URL is not configured.",
    );
  }

  const normalized = apiUrl.replace(/\/+$/, "");

  const url = new URL(normalized);

  const protocol = url.protocol === "https:" ? "wss:" : "ws:";

  return `${protocol}//${url.host}/ws`;
}

/**
 * ============================================================
 * LOAD STOMP
 *
 * @stomp/stompjs 7.x exposes Client as a named export.
 *
 * We intentionally load it dynamically so Next.js does not try
 * to initialize the browser WebSocket/STOMP client during
 * server-side rendering.
 * ============================================================
 */

async function loadStompClient(): Promise<StompModule> {
  const stompModule =
    (await import("@stomp/stompjs")) as unknown as StompModule;

  if (!stompModule || typeof stompModule.Client !== "function") {
    throw new Error(
      "@stomp/stompjs Client export is unavailable. Verify that @stomp/stompjs 7.x is installed correctly.",
    );
  }

  return stompModule;
}

/**
 * ============================================================
 * PARSE PAYMENT NOTIFICATION
 * ============================================================
 */

function safelyParseNotification(message: StompMessage): PaymentNotification {
  let parsed: unknown;

  try {
    parsed = JSON.parse(message.body);
  } catch {
    throw new Error("Realtime payment notification contains invalid JSON.");
  }

  if (!parsed || typeof parsed !== "object") {
    throw new Error("Realtime payment notification is not a valid object.");
  }

  const value = parsed as Record<string, unknown>;

  if (value.organizationId === undefined || value.organizationId === null) {
    throw new Error("Realtime payment notification is missing organizationId.");
  }

  if (value.paymentId === undefined || value.paymentId === null) {
    throw new Error("Realtime payment notification is missing paymentId.");
  }

  if (value.loanId === undefined || value.loanId === null) {
    throw new Error("Realtime payment notification is missing loanId.");
  }

  const paymentId = Number(value.paymentId);

  const loanId = Number(value.loanId);

  const organizationId = Number(value.organizationId);

  if (!Number.isSafeInteger(paymentId) || paymentId <= 0) {
    throw new Error(
      "Realtime payment notification contains an invalid paymentId.",
    );
  }

  if (!Number.isSafeInteger(loanId) || loanId <= 0) {
    throw new Error(
      "Realtime payment notification contains an invalid loanId.",
    );
  }

  if (!Number.isSafeInteger(organizationId) || organizationId <= 0) {
    throw new Error(
      "Realtime payment notification contains an invalid organizationId.",
    );
  }

  return {
    ...value,

    paymentId,

    loanId,

    organizationId,
  } as PaymentNotification;
}

/**
 * ============================================================
 * CONNECT
 * ============================================================
 */

export function connectToPaymentNotifications(
  organizationId: number,
  callbacks: RealtimePaymentCallbacks,
): () => void {
  if (!Number.isSafeInteger(organizationId) || organizationId <= 0) {
    throw new Error(
      "Cannot connect to payment notifications without a valid organizationId.",
    );
  }

  /**
   * Ensure an existing connection/subscription is closed
   * before creating a new organization-specific connection.
   */
  disconnectFromPaymentNotifications();

  const generation = ++connectionGeneration;

  activeOrganizationId = organizationId;

  const brokerURL = getWebSocketUrl();

  console.info(`[REALTIME] Connecting to: ${brokerURL}`);

  void loadStompClient()
    .then((stompModule) => {
      /**
       * A disconnect/reconnect may have happened while the
       * STOMP library was loading.
       */
      if (generation !== connectionGeneration) {
        return;
      }

      const client = new stompModule.Client({
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

      /**
       * ========================================================
       * CONNECTED
       * ========================================================
       */

      client.onConnect = (_frame: StompFrame) => {
        if (generation !== connectionGeneration) {
          void client.deactivate();
          return;
        }

        console.info(
          `[REALTIME] WebSocket connected. Organization=${organizationId}`,
        );

        const destination = `/topic/organization/${organizationId}/payments`;

        console.info(`[REALTIME] Subscribing to: ${destination}`);

        try {
          organizationSubscription = client.subscribe(
            destination,
            (message: StompMessage) => {
              if (generation !== connectionGeneration) {
                return;
              }

              try {
                const notification = safelyParseNotification(message);

                /**
                 * Tenant isolation:
                 * Never allow a notification belonging to
                 * another organization to reach the UI.
                 */
                if (notification.organizationId !== organizationId) {
                  console.warn(
                    "[REALTIME] Ignoring payment notification for another organization.",
                    notification,
                  );

                  return;
                }

                console.info(
                  "[REALTIME] Payment notification received:",
                  notification,
                );

                callbacks.onPaymentReceived(notification);
              } catch (error) {
                console.error(
                  "[REALTIME] Failed to parse payment notification:",
                  error,
                );

                callbacks.onError?.(error);
              }
            },
          );

          console.info(`[REALTIME] Successfully subscribed to: ${destination}`);

          callbacks.onConnected?.();
        } catch (error) {
          console.error(
            "[REALTIME] Failed to subscribe to payment notifications:",
            error,
          );

          callbacks.onError?.(error);
        }
      };

      /**
       * ========================================================
       * STOMP DISCONNECT
       * ========================================================
       */

      client.onDisconnect = (_frame: StompFrame) => {
        if (generation !== connectionGeneration) {
          return;
        }

        console.warn("[REALTIME] WebSocket disconnected.");

        organizationSubscription = null;

        callbacks.onDisconnected?.();
      };

      /**
       * ========================================================
       * STOMP BROKER ERROR
       * ========================================================
       */

      client.onStompError = (frame: StompFrame) => {
        if (generation !== connectionGeneration) {
          return;
        }

        const brokerMessage = frame.headers?.message || "STOMP broker error.";

        console.error(
          "[REALTIME] STOMP broker error:",
          brokerMessage,
          frame.body,
        );

        callbacks.onError?.(new Error(brokerMessage));
      };

      /**
       * ========================================================
       * WEBSOCKET ERROR
       * ========================================================
       */

      client.onWebSocketError = (event: Event) => {
        if (generation !== connectionGeneration) {
          return;
        }

        console.error("[REALTIME] WebSocket error:", event);

        callbacks.onError?.(event);
      };

      /**
       * ========================================================
       * WEBSOCKET CLOSE
       * ========================================================
       */

      client.onWebSocketClose = (event: CloseEvent) => {
        if (generation !== connectionGeneration) {
          return;
        }

        console.warn("[REALTIME] WebSocket closed:", event);

        organizationSubscription = null;

        callbacks.onDisconnected?.();
      };

      /**
       * Store the active client only after all handlers
       * have been configured.
       */
      stompClient = client;

      /**
       * Start the STOMP connection.
       */
      client.activate();
    })
    .catch((error: unknown) => {
      if (generation !== connectionGeneration) {
        return;
      }

      console.error("[REALTIME] Failed to load STOMP client:", error);

      activeOrganizationId = null;

      callbacks.onError?.(error);

      callbacks.onDisconnected?.();
    });

  /**
   * Return cleanup function for React useEffect and
   * other consumers.
   */
  return () => {
    if (generation === connectionGeneration) {
      disconnectFromPaymentNotifications();
    }
  };
}

/**
 * ============================================================
 * DISCONNECT
 * ============================================================
 */

export function disconnectFromPaymentNotifications(): void {
  /**
   * Invalidate all previously created connection callbacks.
   */
  connectionGeneration++;

  activeOrganizationId = null;

  /**
   * Remove the organization subscription first.
   */
  if (organizationSubscription) {
    try {
      organizationSubscription.unsubscribe();
    } catch (error) {
      console.error("[REALTIME] Failed to unsubscribe:", error);
    }

    organizationSubscription = null;
  }

  /**
   * Then deactivate the STOMP client.
   */
  if (stompClient) {
    const client = stompClient;

    stompClient = null;

    try {
      void client.deactivate();
    } catch (error) {
      console.error("[REALTIME] Failed to disconnect:", error);
    }
  }
}

/**
 * ============================================================
 * CONNECTION STATUS
 * ============================================================
 */

export function isPaymentRealtimeConnected(): boolean {
  return Boolean(stompClient?.connected);
}

/**
 * ============================================================
 * ACTIVE ORGANIZATION
 * ============================================================
 */

export function getActivePaymentOrganizationId(): number | null {
  return activeOrganizationId;
}
