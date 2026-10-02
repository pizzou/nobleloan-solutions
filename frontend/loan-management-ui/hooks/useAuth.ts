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
  const [user, setUser] = useState<AuthResponse | null>(() => {
    if (typeof window === "undefined") return null;
    try {
      const cached = localStorage.getItem("user");
      return cached ? (JSON.parse(cached) as AuthResponse) : null;
    } catch {
      return null;
    }
  });
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    let mounted = true;
    setLoading(true);
    (async () => {
      try {
        const me = (await authApi.me()) as AuthResponse;
        if (mounted && me && typeof me.userId === "number") {
          setUser(me);
          localStorage.setItem("user", JSON.stringify(me));
        }
      } catch (error: any) {
        if (mounted) {
          // Only an explicit authentication rejection means the session is gone.
          // Network failures and server errors must not log an operator out.
          const status = error?.response?.status ?? error?.status;
          if (status === 401) {
            localStorage.removeItem("user");
            setUser(null);
          }
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
