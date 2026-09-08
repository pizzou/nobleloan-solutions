import { authApi } from "@/services/api";
import { AuthResponse } from "@/types";

/**
 * Browser authentication uses an HttpOnly NLS_SESSION cookie.
 *
 * The JWT is never exposed to JavaScript or persisted in localStorage.
 */
export async function login(
  email: string,
  password: string,
): Promise<AuthResponse> {
  await authApi.csrf();

  const data = (await authApi.login(email, password)) as AuthResponse;

  if (typeof window !== "undefined") {
    localStorage.setItem("user", JSON.stringify(data));
  }

  return data;
}

export async function logout(): Promise<void> {
  try {
    await authApi.logout();
  } finally {
    if (typeof window !== "undefined") {
      localStorage.removeItem("user");
      window.location.href = "/login";
    }
  }
}

export function getCurrentUser(): AuthResponse | null {
  if (typeof window === "undefined") {
    return null;
  }

  try {
    const raw = localStorage.getItem("user");

    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

/**
 * Normalize a role received from the backend.
 *
 * Supports:
 * ADMIN
 * admin
 * ROLE_ADMIN
 * role_admin
 * ROLE-ADMIN
 * role admin
 */
function normalizeRole(role: unknown): string | null {
  if (typeof role !== "string" || role.trim().length === 0) {
    return null;
  }

  let normalized = role
    .trim()
    .toUpperCase()
    .replace(/[-\s]+/g, "_");

  while (normalized.startsWith("ROLE_")) {
    normalized = normalized.substring(5);
  }

  return normalized || null;
}

/**
 * Frontend role check.
 *
 * ADMIN is the highest application role and therefore satisfies
 * every application-role check.
 *
 * This mirrors the backend RoleHierarchy.
 */
export function hasRole(...roles: string[]): boolean {
  const user = getCurrentUser();

  if (!user) {
    return false;
  }

  const userRole = normalizeRole(user.role);

  if (!userRole) {
    return false;
  }

  const requestedRoles = roles
    .map(normalizeRole)
    .filter((role): role is string => role !== null);

  /*
   * ADMIN has full application-level privileges.
   */
  if (userRole === "ADMIN") {
    return true;
  }

  return requestedRoles.includes(userRole);
}
