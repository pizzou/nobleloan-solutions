"use client";

import { useState, useEffect, createContext, useContext } from "react";
import { AuthResponse } from "@/types";
import { authApi } from "@/services/api";
import { clearSensitiveClientState } from "@/lib/securityCleanup";
import { noteSessionActivity } from "@/lib/sessionActivity";

interface AuthCtx {
  user: AuthResponse | null;
  token: string | null;
  login: (user: AuthResponse) => void;
  logout: () => Promise<void>;
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
  logout: async () => {},
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
  const [sessionError, setSessionError] = useState(false);
  const [sessionAttempt, setSessionAttempt] = useState(0);

  const retrySession = () => {
    setSessionError(false);
    setLoading(true);
    setSessionAttempt((attempt) => attempt + 1);
  };

  useEffect(() => {
    let mounted = true;

    // Do not restore authorization/display state from localStorage before the
    // server validates the HttpOnly session. This prevents stale privileged UI
    // from flashing while an expired/revoked session is being checked.

    (async () => {
      try {
        const me = (await authApi.me()) as AuthResponse;
        if (mounted && me && typeof me.userId === "number") {
          setUser(me);
          localStorage.setItem("user", JSON.stringify(me));
        } else if (mounted) {
          setSessionError(true);
        }
      } catch (error: any) {
        if (!mounted) return;

        // Only a confirmed authentication failure means the session is gone.
        // Network errors, timeouts, 5xx responses, etc. must not log a user
        // out merely because the browser was refreshed at that moment.
        const status = error?.response?.status ?? error?.status;
        if (status === 401) {
          await clearSensitiveClientState();
          if (mounted) setUser(null);
        } else {
          setSessionError(true);
        }
      } finally {
        if (mounted) setLoading(false);
      }
    })();

    return () => {
      mounted = false;
    };
  }, [sessionAttempt]);

  const login = (userData: AuthResponse) => {
    if (
      !userData ||
      typeof userData.userId !== "number" ||
      typeof userData.email !== "string"
    )
      return;
    // Cached user JSON is presentation-only; server permissions remain the
    // authority and the user object is not restored until /auth/me succeeds.
    localStorage.setItem("user", JSON.stringify(userData));
    noteSessionActivity();
    setUser(userData);
  };

  const logout = async () => {
    try {
      await authApi.logout();
    } catch {
      /* local logout still completes */
    }
    await clearSensitiveClientState();
    setUser(null);
    if (typeof window !== "undefined") window.location.replace("/login");
  };

  return {
    user,
    token: null,
    loading,
    sessionError,
    retrySession,
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
