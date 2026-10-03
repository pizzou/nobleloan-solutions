import { authApi } from "@/services/api";
import { AuthResponse } from "@/types";

/** Browser authentication uses an HttpOnly NLS_SESSION cookie. The JWT is never
 * exposed to JavaScript or persisted in localStorage. */
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
  if (typeof window === "undefined") return null;
  try {
    const raw = localStorage.getItem("user");
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

export function hasRole(...roles: string[]): boolean {
  const user = getCurrentUser();
  if (!user?.role) return false;

  const actual = String(user.role)
    .trim()
    .toUpperCase()
    .replace(/^ROLE_/, "")
    .replace(/[- ]/g, "_");

  return roles.some((role) => {
    const expected = String(role)
      .trim()
      .toUpperCase()
      .replace(/^ROLE_/, "")
      .replace(/[- ]/g, "_");
    return actual === expected;
  });
}
