"use client";

import { FormEvent, useEffect, useState } from "react";
import Link from "next/link";
import { authApi } from "@/services/api";

const NAVY = "#0B1F3A";
const BLUE = "#123B66";
const YELLOW = "#F4C430";

export default function ResetPasswordPage() {
  const [token, setToken] = useState("");
  const [tokenLoaded, setTokenLoaded] = useState(false);
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [completed, setCompleted] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const suppliedToken = params.get("token") || "";
    setToken(suppliedToken);
    setTokenLoaded(true);
    // Keep the bearer reset token out of the address bar/history after capture.
    if (suppliedToken) window.history.replaceState(null, "", window.location.pathname);
  }, []);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    if (!token) {
      setError("This reset link is missing or invalid. Request a new password reset link.");
      return;
    }
    if (password !== confirmPassword) {
      setError("The passwords do not match.");
      return;
    }
    if (password.length < 10 || password.length > 128 || !/[A-Z]/.test(password) || !/[a-z]/.test(password) || !/[0-9]/.test(password) || !/[^A-Za-z0-9]/.test(password)) {
      setError("Use 10–128 characters, including uppercase and lowercase letters, a number, and a special character.");
      return;
    }

    setLoading(true);
    try {
      await authApi.resetPassword(token, password);
      setCompleted(true);
      setToken("");
      setPassword("");
      setConfirmPassword("");
    } catch (requestError: any) {
      setError(requestError?.message || "This reset link is invalid or expired. Request a new link and try again.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="min-h-screen flex items-center justify-center bg-slate-50 px-4 py-10">
      <section className="w-full max-w-md rounded-2xl border border-slate-200 bg-white p-7 shadow-sm sm:p-9">
        <div className="mb-7 flex items-center gap-3">
          <div className="flex h-11 w-11 items-center justify-center rounded-xl text-lg font-extrabold" style={{ backgroundColor: YELLOW, color: NAVY }}>N</div>
          <div>
            <div className="font-extrabold text-slate-900">Noble Loan Solutions</div>
            <div className="text-xs text-slate-500">Secure account recovery</div>
          </div>
        </div>
        <div className="mb-6 h-1 w-10 rounded-full" style={{ backgroundColor: YELLOW }} />
        <h1 className="text-2xl font-extrabold text-slate-900">Set a new password</h1>
        <p className="mt-2 text-sm leading-6 text-slate-600">Choose a strong password you have not used for this account before.</p>

        {completed ? (
          <div className="mt-6 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm leading-6 text-emerald-900" role="status" aria-live="polite">
            Your password has been changed. Existing sign-in sessions have been revoked. Sign in using your new password.
          </div>
        ) : !tokenLoaded ? (
          <p className="mt-6 text-sm text-slate-600" role="status">Checking reset link…</p>
        ) : !token ? (
          <div className="mt-6 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm leading-6 text-amber-900">This reset link is missing or invalid. Request a new link to continue.</div>
        ) : (
          <form onSubmit={handleSubmit} className="mt-6 space-y-4">
            {error && <div role="alert" className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</div>}
            <div>
              <label htmlFor="new-password" className="mb-1 block text-sm font-semibold text-slate-700">New password</label>
              <input id="new-password" name="newPassword" type="password" autoComplete="new-password" required minLength={10} maxLength={128} value={password} onChange={(event) => setPassword(event.target.value)} className="w-full rounded-lg border border-slate-300 px-3 py-3 text-sm text-slate-900 outline-none transition focus:border-blue-700 focus:ring-2 focus:ring-blue-100" />
            </div>
            <div>
              <label htmlFor="confirm-password" className="mb-1 block text-sm font-semibold text-slate-700">Confirm new password</label>
              <input id="confirm-password" name="confirmPassword" type="password" autoComplete="new-password" required minLength={10} maxLength={128} value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} className="w-full rounded-lg border border-slate-300 px-3 py-3 text-sm text-slate-900 outline-none transition focus:border-blue-700 focus:ring-2 focus:ring-blue-100" />
            </div>
            <p className="text-xs leading-5 text-slate-500">Use 10–128 characters with uppercase and lowercase letters, a number, and a special character.</p>
            <button type="submit" disabled={loading} className="w-full rounded-lg px-4 py-3 text-sm font-bold text-white transition disabled:cursor-not-allowed disabled:opacity-60" style={{ backgroundColor: BLUE }}>
              {loading ? "Updating password…" : "Update password"}
            </button>
          </form>
        )}
        <div className="mt-6 text-center text-sm">
          <Link href="/login" className="font-semibold hover:underline" style={{ color: BLUE }}>← Back to sign in</Link>
        </div>
      </section>
    </main>
  );
}
