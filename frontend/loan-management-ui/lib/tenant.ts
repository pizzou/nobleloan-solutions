export const configuredTenantSlug =
  process.env.NEXT_PUBLIC_TENANT_SLUG?.trim() || "";

export const resolveTenantSlug = (): string => configuredTenantSlug;
