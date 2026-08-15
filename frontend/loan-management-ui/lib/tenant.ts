/**
 * Public tenant resolution.
 *
 * A tenant slug may still be supplied for local development or a dedicated
 * single-tenant deployment. In production multi-tenant mode the browser
 * hostname is sent to the backend and resolved server-side.
 */
export const configuredTenantSlug =
  process.env.NEXT_PUBLIC_TENANT_SLUG?.trim() || "";

export const resolveTenantSlug = (): string => configuredTenantSlug;

export const resolveTenantHost = (): string => {
  if (typeof window === "undefined") {
    return "";
  }

  return window.location.hostname.trim().toLowerCase();
};
