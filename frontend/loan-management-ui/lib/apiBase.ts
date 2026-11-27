export function getApiBaseUrl(): string {
  const configured = process.env.NEXT_PUBLIC_API_URL?.trim();
  const baseUrl = configured || "/api";
  return baseUrl.replace(/\/+$/, "");
}

export const API_BASE_URL = getApiBaseUrl();
