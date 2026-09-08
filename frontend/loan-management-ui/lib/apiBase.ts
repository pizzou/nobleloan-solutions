/**
 * Production API configuration.
 *
 * There is intentionally no localhost fallback. A missing API URL is a
 * deployment/configuration error and must fail loudly instead of silently
 * sending customer traffic to a local developer machine.
 */
export function getApiBaseUrl(): string {
  const configured = process.env.NEXT_PUBLIC_API_URL?.trim();

  if (configured) {
    return configured.replace(/\/+$/, "");
  }

  throw new Error(
    "NEXT_PUBLIC_API_URL is required. Refusing to fall back to localhost."
  );
}

export const API_BASE_URL = getApiBaseUrl();
