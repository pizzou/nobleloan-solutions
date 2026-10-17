"use client";

import { FormEvent, useState } from "react";
import Link from "next/link";
import { authApi } from "@/services/api";

const NAVY = "#0B1F3A";
const BLUE = "#123B66";
const YELLOW = "#F4C430";

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [loading, setLoading] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState("");

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setLoading(true);
    try {
      await authApi.forgotPassword(email);
      // The backend intentionally uses the same response for known and unknown
      // addresses to prevent account enumeration.
      setSubmitted(true);
    } catch (requestError: unknown) {
      const error = requestError as {
        message?: unknown;
        status?: number;
        response?: { status?: number };
      };
      const status = error.response?.status ?? error.status;
      if (status === 400 && typeof error.message === "string" && error.message.trim()) {
        // Validation feedback is safe to show; account existence remains hidden
        // because unknown addresses receive the same generic success response.
        setError(error.message);
      } else if (status === 429) {
        setError("Too many reset requests. Please wait before trying again.");
      } else {
        setError(
          "We could not process this request right now. Please wait and try again.",
        );
      }
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="min-h-screen flex items-center justify-center bg-slate-50 px-4 py-10">
      <section className="w-full max-w-md rounded-2xl border border-slate-200 bg-white p-7 shadow-sm sm:p-9">
        <div className="mb-7 flex items-center gap-3">
          <div
            className="flex h-11 w-11 items-center justify-center rounded-xl text-lg font-extrabold"
            style={{ backgroundColor: YELLOW, color: NAVY }}
          >
            N
          </div>
          <div>
            <div className="font-extrabold text-slate-900">
              Noble Loan Solutions
            </div>
            <div className="text-xs text-slate-500">
              Secure account recovery
            </div>
          </div>
        </div>
        <div
          className="mb-6 h-1 w-10 rounded-full"
          style={{ backgroundColor: YELLOW }}
        />
        <h1 className="text-2xl font-extrabold text-slate-900">
          Forgot your password?
        </h1>
        <p className="mt-2 text-sm leading-6 text-slate-600">
          Enter the email address associated with your staff account. If the
          account is eligible, we will send a secure reset link.
        </p>

        {submitted ? (
          <div
            className="mt-6 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm leading-6 text-emerald-900"
            role="status"
            aria-live="polite"
          >
            If an active account matches that email and email delivery is
            available, a password reset link will be sent. Check your inbox and
            spam folder. The link expires after 30 minutes and can be used once.
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="mt-6 space-y-4">
            {error && (
              <div
                role="alert"
                className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700"
              >
                {error}
              </div>
            )}
            <label
              htmlFor="email"
              className="block text-sm font-semibold text-slate-700"
            >
              Email address
            </label>
            <input
              id="email"
              name="email"
              type="email"
              autoComplete="email"
              required
              maxLength={254}
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="you@organization.com"
              className="w-full rounded-lg border border-slate-300 px-3 py-3 text-sm text-slate-900 outline-none transition focus:border-blue-700 focus:ring-2 focus:ring-blue-100"
            />
            <button
              type="submit"
              disabled={loading}
              className="w-full rounded-lg px-4 py-3 text-sm font-bold text-white transition disabled:cursor-not-allowed disabled:opacity-60"
              style={{ backgroundColor: BLUE }}
            >
              {loading ? "Sending request…" : "Send reset link"}
            </button>
          </form>
        )}
        <div className="mt-6 text-center text-sm">
          <Link
            href="/login"
            className="font-semibold hover:underline"
            style={{ color: BLUE }}
          >
            ← Back to sign in
          </Link>
        </div>
      </section>
    </main>
  );
}
