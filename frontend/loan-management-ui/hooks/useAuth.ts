"use client";

import { useState, useEffect, createContext, useContext } from "react";
import { AuthResponse } from "@/types";
import { authApi } from "@/services/api";

interface AuthCtx {
  user: AuthResponse | null;
  token: string | null;
  login: (user: AuthResponse) => void;
  logout: () => void;
  loading: boolean;
  isAdmin: boolean;
  isBusinessOwner: boolean;
  isOfficer: boolean;
  currency: string;
  locale: string;
  mustChangePassword: boolean;
}

export const AuthContext = createContext<AuthCtx>({
  user: null,
  token: null,
  login: () => {},
  logout: () => {},
  loading: true,
  isAdmin: false,
  isBusinessOwner: false,
  isOfficer: false,
  currency: "USD",
  locale: "en-US",
  mustChangePassword: false,
});

export function useAuth() {
  return useContext(AuthContext);
}

export function useAuthState() {
  const [user, setUser] = useState<AuthResponse | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let mounted = true;

    // Restore the last known user immediately so a browser refresh does not
    // temporarily turn an authenticated session into an unauthenticated one.
    // The HttpOnly NLS_SESSION cookie remains the source of truth for the
    // server; localStorage is only a UI bootstrap cache and never a credential.
    try {
      const raw = localStorage.getItem("user");
      if (raw) {
        const cached = JSON.parse(raw) as AuthResponse;
        if (
          cached &&
          typeof cached === "object" &&
          typeof cached.userId === "number" &&
          typeof cached.email === "string"
        ) {
          setUser(cached);
        } else {
          localStorage.removeItem("user");
        }
      }
    } catch {
      localStorage.removeItem("user");
    }

    (async () => {
      try {
        const me = (await authApi.me()) as AuthResponse;
        if (mounted && me && typeof me.userId === "number") {
          setUser(me);
          localStorage.setItem("user", JSON.stringify(me));
        }
      } catch (error: any) {
        if (!mounted) return;

        // Only a confirmed authentication failure means the session is gone.
        // Network errors, timeouts, 5xx responses, etc. must not log a user
        // out merely because the browser was refreshed at that moment.
        const status = error?.response?.status ?? error?.status;
        if (status === 401) {
          localStorage.removeItem("user");
          setUser(null);
        }
      } finally {
        if (mounted) setLoading(false);
      }
    })();

    return () => {
      mounted = false;
    };
  }, []);

  const login = (userData: AuthResponse) => {
    if (
      !userData ||
      typeof userData.userId !== "number" ||
      typeof userData.email !== "string"
    )
      return;
    localStorage.setItem("user", JSON.stringify(userData));
    setUser(userData);
  };

  const logout = async () => {
    try {
      await authApi.logout();
    } catch {
      /* local logout still completes */
    }
    localStorage.removeItem("user");
    setUser(null);
  };

  return {
    user,
    token: null,
    loading,
    login,
    logout,
    isAdmin: user?.role === "ADMIN",
    isBusinessOwner: user?.role === "BUSINESS_OWNER",
    isOfficer: [
      "ADMIN",
      "BUSINESS_OWNER",
      "LOAN_OFFICER",
      "CREDIT_ANALYST",
      "MANAGER",
    ].includes(user?.role || ""),
    currency: user?.currency || "USD",
    locale: user?.locale || "en-US",
    mustChangePassword: Boolean(user?.mustChangePassword),
  };
}
