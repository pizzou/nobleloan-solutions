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

let socket: WebSocket | null = null;
let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
let heartbeatTimer: ReturnType<typeof setInterval> | null = null;
let activeOrganizationId: number | null = null;
let connectionGeneration = 0;
let shouldReconnect = false;

function getWebSocketUrl(): string {
  const apiUrl =
    process.env.NEXT_PUBLIC_API_URL || process.env.NEXT_PUBLIC_API_BASE_URL;

  if (!apiUrl) {
    throw new Error(
      "NEXT_PUBLIC_API_URL or NEXT_PUBLIC_API_BASE_URL is not configured.",
    );
  }

  const url = new URL(apiUrl.replace(/\/+$/, ""));
  const protocol = url.protocol === "https:" ? "wss:" : "ws:";
  return `${protocol}//${url.host}/ws`;
}

function stompFrame(
  command: string,
  headers: Record<string, string> = {},
  body = "",
) {
  const headerText = Object.entries(headers)
    .map(([key, value]) => `${key}:${value}`)
    .join("\n");

  return `${command}\n${headerText}\n\n${body}\0`;
}

function parseFrames(
  raw: string,
): Array<{ command: string; headers: Record<string, string>; body: string }> {
  return raw
    .split("\0")
    .map((frame) => frame.trim())
    .filter(Boolean)
    .map((frame) => {
      const separator = frame.indexOf("\n\n");
      const head = separator >= 0 ? frame.slice(0, separator) : frame;
      const body = separator >= 0 ? frame.slice(separator + 2) : "";
      const lines = head.split("\n");
      const command = lines.shift()?.trim() || "";
      const headers: Record<string, string> = {};

      for (const line of lines) {
        const colon = line.indexOf(":");
        if (colon > 0) headers[line.slice(0, colon)] = line.slice(colon + 1);
      }

      return { command, headers, body };
    });
}

function safelyParseNotification(body: string): PaymentNotification {
  const parsed = JSON.parse(body);

  if (!parsed || typeof parsed !== "object") {
    throw new Error("Realtime payment notification is not a valid object.");
  }

  for (const field of ["organizationId", "paymentId", "loanId"]) {
    if (parsed[field] === undefined || parsed[field] === null) {
      throw new Error(`Realtime payment notification is missing ${field}.`);
    }
  }

  return {
    ...parsed,
    paymentId: Number(parsed.paymentId),
    loanId: Number(parsed.loanId),
    organizationId: Number(parsed.organizationId),
  } as PaymentNotification;
}

function clearTimers() {
  if (reconnectTimer) clearTimeout(reconnectTimer);
  if (heartbeatTimer) clearInterval(heartbeatTimer);
  reconnectTimer = null;
  heartbeatTimer = null;
}

export function connectToPaymentNotifications(
  organizationId: number,
  callbacks: RealtimePaymentCallbacks,
): () => void {
  if (!organizationId) {
    throw new Error(
      "Cannot connect to payment notifications without organizationId.",
    );
  }

  disconnectFromPaymentNotifications();

  const generation = ++connectionGeneration;
  activeOrganizationId = organizationId;
  shouldReconnect = true;

  const connect = () => {
    if (!shouldReconnect || generation !== connectionGeneration) return;

    clearTimers();
    const brokerURL = getWebSocketUrl();
    const client = new WebSocket(brokerURL);
    socket = client;

    client.onopen = () => {
      if (generation !== connectionGeneration) return;

      client.send(
        stompFrame("CONNECT", {
          "accept-version": "1.2",
          "heart-beat": "10000,10000",
        }),
      );
    };

    client.onmessage = (event) => {
      if (generation !== connectionGeneration) return;

      for (const frame of parseFrames(String(event.data))) {
        if (frame.command === "CONNECTED") {
          const destination = `/topic/organization/${organizationId}/payments`;

          client.send(
            stompFrame("SUBSCRIBE", {
              id: `organization-${organizationId}`,
              destination,
              ack: "auto",
            }),
          );

          heartbeatTimer = setInterval(() => {
            if (client.readyState === WebSocket.OPEN) client.send("\n");
          }, 10000);

          callbacks.onConnected?.();
          continue;
        }

        if (frame.command === "MESSAGE") {
          try {
            const notification = safelyParseNotification(frame.body);

            if (notification.organizationId !== organizationId) {
              console.warn(
                "[REALTIME] Ignoring payment notification for another organization.",
              );
              continue;
            }

            callbacks.onPaymentReceived(notification);
          } catch (error) {
            console.error(
              "[REALTIME] Failed to parse payment notification:",
              error,
            );
            callbacks.onError?.(error);
          }
          continue;
        }

        if (frame.command === "ERROR") {
          const error = new Error(
            frame.headers.message || frame.body || "STOMP broker error.",
          );
          callbacks.onError?.(error);
        }
      }
    };

    client.onerror = (event) => {
      if (generation === connectionGeneration) callbacks.onError?.(event);
    };

    client.onclose = () => {
      if (generation !== connectionGeneration) return;

      clearTimers();
      socket = null;
      callbacks.onDisconnected?.();

      if (shouldReconnect) {
        reconnectTimer = setTimeout(connect, 5000);
      }
    };
  };

  connect();

  return () => {
    if (generation === connectionGeneration)
      disconnectFromPaymentNotifications();
  };
}

export function disconnectFromPaymentNotifications(): void {
  connectionGeneration++;
  shouldReconnect = false;
  activeOrganizationId = null;
  clearTimers();

  if (socket) {
    const current = socket;
    socket = null;
    try {
      if (current.readyState === WebSocket.OPEN) {
        current.send(stompFrame("DISCONNECT", { receipt: "disconnect" }));
      }
      current.close();
    } catch (error) {
      console.error("[REALTIME] Failed to disconnect:", error);
    }
  }
}

export function isPaymentRealtimeConnected(): boolean {
  return socket?.readyState === WebSocket.OPEN;
}

export function getActivePaymentOrganizationId(): number | null {
  return activeOrganizationId;
}
