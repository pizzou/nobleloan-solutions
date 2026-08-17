

export function currentTenantDomain(): string | null {
 
  const configuredDomain =
    process.env.NEXT_PUBLIC_TENANT_DOMAIN?.trim().toLowerCase();

  if (configuredDomain) {
    return normalizeDomain(configuredDomain);
  }

  
  if (typeof window === 'undefined') {
    return null;
  }

  const host = window.location.hostname;

  if (!host) {
    return null;
  }

  const normalizedHost = normalizeDomain(host);

  /*
   * Local development only.
   */
  if (
    normalizedHost === 'localhost' ||
    normalizedHost === '127.0.0.1' ||
    normalizedHost.endsWith('.local')
  ) {
    return normalizedHost;
  }


  return null;
}



function normalizeDomain(domain: string): string {
  let result = domain.trim().toLowerCase();

  if (
    result.startsWith('http://') ||
    result.startsWith('https://')
  ) {
    try {
      result = new URL(result).hostname.toLowerCase();
    } catch {
      return '';
    }
  }


  const colonIndex = result.indexOf(':');

  if (colonIndex !== -1) {
    result = result.substring(0, colonIndex);
  }

  
  if (result.startsWith('www.')) {
    result = result.substring(4);
  }

  
  while (result.endsWith('.')) {
    result = result.slice(0, -1);
  }

  return result;
}



export function isLocalDev(): boolean {
  const domain = currentTenantDomain();

  if (!domain) {
    if (typeof window === 'undefined') {
      return true;
    }

    const host =
      window.location.hostname.toLowerCase();

    return (
      host === 'localhost' ||
      host === '127.0.0.1' ||
      host.endsWith('.local')
    );
  }

  return (
    domain === 'localhost' ||
    domain === '127.0.0.1' ||
    domain.endsWith('.local')
  );
}



export const TENANT_SLUG =
  process.env.NEXT_PUBLIC_TENANT_SLUG ||
  'growthfinance';
