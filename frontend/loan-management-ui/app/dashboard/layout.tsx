"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { Bell, Menu, X } from "lucide-react";
import { useRouter } from "next/navigation";

import Sidebar from "@/components/Sidebar";
import { AuthContext, useAuthState } from "@/hooks/useAuth";
import { ToastContainer } from "@/components/ui/ToastContainer";
import ForcedPasswordChange from "@/components/ForcedPasswordChange";
import { useSessionActivity } from "@/hooks/useSessionActivity";

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const auth = useAuthState();
  const router = useRouter();
  const navigation = useRef<HTMLDialogElement>(null);
  const [navigationOpen, setNavigationOpen] = useState(false);

  useEffect(() => {
    const desktop = window.matchMedia("(min-width: 1024px)");
    function closeOnDesktop() {
      if (desktop.matches) navigation.current?.close();
    }
    desktop.addEventListener("change", closeOnDesktop);
    return () => desktop.removeEventListener("change", closeOnDesktop);
  }, []);
  useEffect(() => {
    const dialog = navigation.current;
    if (navigationOpen && dialog && !dialog.open) dialog.showModal();
  }, [navigationOpen]);

  useSessionActivity(!auth.loading && Boolean(auth.user), () => {
    void auth.logout();
  });

  /* ==========================================================
     AUTHENTICATION CHECK
     ========================================================== */

  useEffect(() => {
    if (!auth.loading && !auth.sessionError && !auth.user) {
      router.replace("/login");
    }
  }, [auth.loading, auth.sessionError, auth.user, router]);

  /* ==========================================================
     LOADING SCREEN
     ========================================================== */

  if (auth.loading) {
    return (
      <div className="min-h-screen bg-[#F4F7FB] flex items-center justify-center bg-[radial-gradient(circle_at_top_right,rgba(244,196,48,0.08),transparent_28rem)]">
        <div className="flex flex-col items-center gap-4">
          {/* Noble logo */}

          <div className="relative">
            <div
              className="
                flex
                h-14
                w-14
                items-center
                justify-center
                rounded-2xl
                bg-[#0B1F3A]
                shadow-[0_12px_30px_rgba(11,31,58,0.18)]
                ring-1
                ring-white/10
              "
            >
              <span className="text-xl font-extrabold text-[#F4C430]">N</span>
            </div>

            <span
              className="
                absolute
                -bottom-1
                -right-1
                h-4
                w-4
                rounded-full
                border-2
                border-white
                bg-[#F4C430]
              "
            />
          </div>

          {/* Loading spinner */}

          <div className="relative h-10 w-10">
            <div className="absolute inset-0 rounded-full border-4 border-[#E3EAF3]" />

            <div
              className="
                absolute
                inset-0
                animate-spin
                rounded-full
                border-4
                border-transparent
                border-t-[#0B1F3A]
                border-r-[#F4C430]
              "
            />
          </div>

          <div className="text-center">
            <p className="text-sm font-bold text-[#0B1F3A]">
              Loading Noble Loan Solutions
            </p>

            <p className="mt-1 text-xs text-gray-500">
              Preparing your financial workspace…
            </p>
          </div>
        </div>
      </div>
    );
  }

  /* ==========================================================
     USER NOT AUTHENTICATED
     ========================================================== */

  if (auth.sessionError) {
    return (
      <main className="grid min-h-screen place-items-center bg-slate-50 p-5">
        <section className="w-full max-w-md rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <h1 className="text-lg font-semibold text-slate-900">Workspace unavailable</h1>
          <p role="alert" className="mt-2 text-sm text-slate-600">We couldn’t verify your session. Check your connection and try again.</p>
          <button type="button" onClick={auth.retrySession} className="mt-5 rounded-lg bg-[#0B1F3A] px-4 py-2 text-sm font-semibold text-white hover:bg-[#16365F]">
            Try again
          </button>
        </section>
      </main>
    );
  }

  if (!auth.user) {
    return null;
  }

  /* ==========================================================
     FORCE PASSWORD CHANGE
     ========================================================== */

  if (auth.mustChangePassword) {
    return (
      <AuthContext.Provider value={auth}>
        <div className="min-h-screen bg-[#F4F7FB]">
          <ForcedPasswordChange />
        </div>

        <ToastContainer />
      </AuthContext.Provider>
    );
  }

  return (
    <AuthContext.Provider value={auth}>
      <div className="min-h-screen bg-slate-50 text-slate-950">
        <a href="#workspace-content" className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-3 focus:z-50 focus:rounded-lg focus:bg-white focus:px-4 focus:py-3">
          Skip to content
        </a>
        <div className="fixed inset-y-0 left-0 z-40 hidden w-64 lg:block">
          <Sidebar />
        </div>
        <dialog
          id="workspace-navigation"
          ref={navigation}
          aria-labelledby="workspace-navigation-title"
          onClose={() => setNavigationOpen(false)}
          className="fixed inset-y-0 left-0 right-auto m-0 h-dvh max-h-none w-72 max-w-[90vw] overflow-hidden border-0 bg-[#0B1F3A] p-0 text-white backdrop:bg-slate-950/55"
        >
          {navigationOpen && (
            <div className="flex h-full flex-col">
              <div className="flex items-center justify-between border-b border-white/10 px-4 py-2">
                <h2 id="workspace-navigation-title" className="text-sm font-semibold">Navigation</h2>
                <button type="button" aria-label="Close navigation" onClick={() => navigation.current?.close()} className="grid h-10 w-10 place-items-center rounded-lg hover:bg-white/10">
                  <X size={20} aria-hidden="true" />
                </button>
              </div>
              <div className="min-h-0 flex-1">
                <Sidebar onNavigate={() => navigation.current?.close()} />
              </div>
            </div>
          )}
        </dialog>

        <div className="flex min-h-screen min-w-0 flex-col lg:pl-64">
          <header className="sticky top-0 z-30 border-b border-slate-200 bg-white/95 backdrop-blur">
            <div className="mx-auto flex min-h-16 max-w-[1800px] items-center justify-between gap-3 px-4 sm:px-6 lg:px-8">
              <div className="flex min-w-0 items-center gap-3">
                <button
                  type="button"
                  aria-label="Open navigation"
                  aria-haspopup="dialog"
                  aria-controls="workspace-navigation"
                  aria-expanded={navigationOpen}
                  onClick={() => setNavigationOpen(true)}
                  className="grid h-10 w-10 shrink-0 place-items-center rounded-lg border border-slate-200 text-slate-700 hover:bg-slate-100 lg:hidden"
                >
                  <Menu size={20} aria-hidden="true" />
                </button>
                <Link href="/dashboard" className="min-w-0 truncate text-sm font-semibold text-[#0B1F3A] hover:underline">
                  {auth.user.organizationName || "Noble Loan Solutions"}
                </Link>
              </div>
              <Link
                href="/dashboard/notifications"
                aria-label="Notifications"
                className="inline-flex h-10 shrink-0 items-center gap-2 rounded-lg border border-slate-200 px-3 text-sm font-medium text-slate-700 hover:bg-slate-100"
              >
                <Bell size={18} aria-hidden="true" />
                <span className="hidden sm:inline">Notifications</span>
              </Link>
            </div>
          </header>

          <main id="workspace-content" tabIndex={-1} className="w-full min-w-0 flex-1">
            <div className="mx-auto w-full max-w-[1800px] px-4 py-5 sm:px-6 lg:px-8 lg:py-7">
              {children}
            </div>
          </main>

          <footer className="border-t border-slate-200 bg-white px-4 py-4 text-xs text-slate-500 sm:px-6 lg:px-8">
            <p>© {new Date().getFullYear()} Noble Loan Solutions</p>
          </footer>
        </div>
      </div>
      <ToastContainer />
    </AuthContext.Provider>
  );
}
