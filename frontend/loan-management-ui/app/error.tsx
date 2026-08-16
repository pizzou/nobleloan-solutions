"use client";

import { useEffect } from "react";

export default function ErrorPage({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error("Unhandled application error", {
      message: error?.message,
      digest: error?.digest,
    });
  }, [error]);

  return (
    <main className="min-h-screen bg-gray-50 flex items-center justify-center px-4">
      <section
        role="alert"
        aria-labelledby="error-title"
        className="w-full max-w-md rounded-2xl border border-gray-200 bg-white p-8 text-center shadow-sm"
      >
        <div
          aria-hidden="true"
          className="mx-auto mb-5 flex h-14 w-14 items-center justify-center rounded-full bg-red-50 text-red-600"
        >
          !
        </div>

        <h1
          id="error-title"
          className="mb-2 text-xl font-semibold text-gray-900"
        >
          Something went wrong
        </h1>

        <p className="mb-6 text-sm leading-6 text-gray-500">
          We could not complete this request. Please try again. If the problem
          continues, contact your administrator.
        </p>

        <div className="flex justify-center gap-3">
          <button
            type="button"
            onClick={reset}
            className="rounded-lg bg-gray-900 px-5 py-2.5 text-sm font-medium text-white transition hover:bg-gray-800 focus:outline-none focus:ring-2 focus:ring-gray-900 focus:ring-offset-2"
          >
            Try again
          </button>

          <button
            type="button"
            onClick={() => {
              window.location.assign("/dashboard");
            }}
            className="rounded-lg border border-gray-200 bg-white px-5 py-2.5 text-sm font-medium text-gray-700 transition hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-gray-400 focus:ring-offset-2"
          >
            Dashboard
          </button>
        </div>
      </section>
    </main>
  );
}
