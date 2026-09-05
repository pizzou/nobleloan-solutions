"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";

import Sidebar from "@/components/Sidebar";
import { AuthContext, useAuthState } from "@/hooks/useAuth";
import { ToastContainer } from "@/components/ui/ToastContainer";
import ForcedPasswordChange from "@/components/ForcedPasswordChange";

/* ============================================================
   NOBLE LOAN SOLUTIONS BRAND
   ============================================================ */

const NAVY = "#0B1F3A";
const NAVY_LIGHT = "#16365F";
const NAVY_DARK = "#07152A";

const YELLOW = "#F4C430";
const YELLOW_LIGHT = "#FFF9DB";
const YELLOW_DARK = "#C99A00";

/* ============================================================
   AUTH HEADER
   ============================================================ */

const authHeader = (): Record<string, string> => {
  if (typeof window === "undefined") {
    return {};
  }

  const token = localStorage.getItem("token");

  if (!token) {
    return {};
  }

  return {
    Authorization: `Bearer ${token}`,
  };
};

/* ============================================================
   DASHBOARD LAYOUT
   ============================================================ */

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const auth = useAuthState();
  const router = useRouter();

  /* ==========================================================
     AUTHENTICATION CHECK
     ========================================================== */

  useEffect(() => {
    if (!auth.loading && !auth.user) {
      router.replace("/login");
    }
  }, [auth.loading, auth.user, router]);

  /* ==========================================================
     LOADING SCREEN
     ========================================================== */

  if (auth.loading) {
    return (
      <div className="min-h-screen bg-[#F4F7FB] flex items-center justify-center">
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
                shadow-lg
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

  /* ==========================================================
     MAIN DASHBOARD
     ========================================================== */

  return (
    <AuthContext.Provider value={auth}>
      <div className="min-h-screen bg-[#F4F7FB] text-gray-900">
        <div className="flex min-h-screen">
          {/* ==================================================
              SIDEBAR
              ================================================== */}

          <aside className="fixed left-0 top-0 bottom-0 z-40 w-64">
            <Sidebar />
          </aside>

          {/* ==================================================
              RIGHT APPLICATION AREA
              ================================================== */}

          <div className="flex min-h-screen flex-1 flex-col pl-64">
            {/* =================================================
                TOP NAVIGATION
                ================================================= */}

            <header
              className="
                sticky
                top-0
                z-30
                h-[72px]
                border-b
                border-[#DCE4EF]
                bg-white/95
                backdrop-blur-xl
              "
            >
              <div className="flex h-full items-center justify-between px-7">
                {/* =================================================
                    LEFT SIDE
                    ================================================= */}

                <div className="flex items-center gap-4">
                  {/* Noble Loan Solutions Logo */}

                  <div
                    className="
                      flex
                      h-10
                      w-10
                      items-center
                      justify-center
                      rounded-xl
                      bg-[#0B1F3A]
                      shadow-sm
                    "
                  >
                    <span className="text-base font-extrabold text-[#F4C430]">
                      N
                    </span>
                  </div>

                  <div>
                    <p
                      className="
                        text-[11px]
                        font-extrabold
                        uppercase
                        tracking-[0.18em]
                        text-[#C99A00]
                      "
                    >
                      Noble Loan Solutions
                    </p>

                    <h2 className="text-sm font-bold text-[#0B1F3A]">
                      Loan Management Platform
                    </h2>
                  </div>
                </div>

                {/* =================================================
                    RIGHT SIDE
                    ================================================= */}

                <div className="flex items-center gap-3">
                  {/* =================================================
                      SYSTEM STATUS
                      ================================================= */}

                  <div
                    className="
                      hidden
                      items-center
                      gap-2
                      rounded-full
                      border
                      border-[#E8D98A]
                      bg-[#FFF9DB]
                      px-3
                      py-1.5
                      sm:flex
                    "
                  >
                    <span className="relative flex h-2 w-2">
                      <span
                        className="
                          absolute
                          inline-flex
                          h-full
                          w-full
                          animate-ping
                          rounded-full
                          bg-[#F4C430]
                          opacity-75
                        "
                      />

                      <span
                        className="
                          relative
                          inline-flex
                          h-2
                          w-2
                          rounded-full
                          bg-[#C99A00]
                        "
                      />
                    </span>

                    <span className="text-xs font-semibold text-[#806200]">
                      System Online
                    </span>
                  </div>

                  {/* =================================================
                      NOTIFICATIONS
                      ================================================= */}

                  <button
                    type="button"
                    aria-label="Notifications"
                    className="
                      relative
                      flex
                      h-10
                      w-10
                      items-center
                      justify-center
                      rounded-xl
                      border
                      border-[#DCE4EF]
                      bg-white
                      text-gray-500
                      transition
                      hover:border-[#C7D5E5]
                      hover:bg-[#EEF3F9]
                      hover:text-[#0B1F3A]
                    "
                  >
                    <span className="text-lg">🔔</span>

                    <span
                      className="
                        absolute
                        right-2
                        top-2
                        h-2
                        w-2
                        rounded-full
                        border-2
                        border-white
                        bg-[#F4C430]
                      "
                    />
                  </button>

                  {/* =================================================
                      ORGANIZATION PROFILE
                      ================================================= */}

                  <div
                    className="
                      flex
                      items-center
                      gap-3
                      border-l
                      border-[#DCE4EF]
                      pl-4
                    "
                  >
                    {/* Organization name */}

                    <div className="hidden text-right sm:block">
                      <p className="text-sm font-bold text-[#0B1F3A]">
                        {auth.user.organizationName || "Noble Loan Solutions"}
                      </p>

                      <p className="text-[11px] font-medium text-gray-500">
                        Financial Management Workspace
                      </p>
                    </div>

                    {/* =================================================
                        NOBLE LOAN SOLUTIONS AVATAR
                        
                        OLD:
                        GF

                        NEW:
                        NL
                        ================================================= */}

                    <div
                      className="
                        relative
                        flex
                        h-10
                        w-10
                        items-center
                        justify-center
                        rounded-xl
                        bg-[#0B1F3A]
                        text-xs
                        font-extrabold
                        text-[#F4C430]
                        shadow-sm
                      "
                    >
                      NL
                      {/* Online indicator */}
                      <span
                        className="
                          absolute
                          -bottom-0.5
                          -right-0.5
                          h-3
                          w-3
                          rounded-full
                          border-2
                          border-white
                          bg-[#F4C430]
                        "
                      />
                    </div>
                  </div>
                </div>
              </div>
            </header>

            {/* =================================================
                MAIN CONTENT
                ================================================= */}

            <main className="flex-1">
              <div
                className="
                  mx-auto
                  w-full
                  max-w-[1800px]
                  px-5
                  py-6
                  sm:px-7
                  lg:px-8
                  lg:py-7
                "
              >
                {children}
              </div>
            </main>

            {/* =================================================
                FOOTER
                ================================================= */}

            <footer
              className="
                border-t
                border-[#DCE4EF]
                bg-white/80
                px-7
                py-4
              "
            >
              <div
                className="
                  flex
                  flex-col
                  items-center
                  justify-between
                  gap-2
                  text-[11px]
                  text-gray-400
                  sm:flex-row
                "
              >
                <p>
                  © {new Date().getFullYear()} Noble Loan Solutions. All rights
                  reserved.
                </p>

                <div className="flex items-center gap-2">
                  <span>Secure Financial Platform</span>

                  <span className="h-1 w-1 rounded-full bg-[#F4C430]" />

                  <span>Loan Management System</span>
                </div>
              </div>
            </footer>
          </div>
        </div>
      </div>

      <ToastContainer />
    </AuthContext.Provider>
  );
}
