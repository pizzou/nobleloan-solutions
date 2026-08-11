'use client';

import Image from 'next/image';
import { useEffect } from 'react';
import { useRouter } from 'next/navigation';

import Sidebar from '@/components/Sidebar';
import { AuthContext, useAuthState } from '@/hooks/useAuth';
import { ToastContainer } from '@/components/ui/ToastContainer';
import { OfflineProvider } from '@/components/OfflineProvider';
import ForcedPasswordChange from '@/components/ForcedPasswordChange';

/* ============================================================
   NOBLE LOAN SOLUTIONS BRAND
   ============================================================ */

const BRAND = {
  navy: '#0B1F3A',
  navyLight: '#16365F',
  navyDark: '#07152A',
  yellow: '#F4C430',
  yellowLight: '#FFF9DB',
  yellowDark: '#C99A00',
};

/* ============================================================
   AUTH HEADER
   ============================================================ */

const authHeader = (): Record<string, string> => {
  if (typeof window === 'undefined') {
    return {};
  }

  const token = window.localStorage.getItem('token');

  if (!token) {
    return {};
  }

  return {
    Authorization: `Bearer ${token}`,
  };
};

/* ============================================================
   PREMIUM BRAND MARK
   ============================================================ */

function BrandMark({
  size = 40,
  priority = false,
}: {
  size?: number;
  priority?: boolean;
}) {
  return (
    <Image
      src="/favIcon.png"
      alt="Noble Loan Solutions"
      width={size}
      height={size}
      priority={priority}
      unoptimized
      className="block object-contain"
    />
  );
}

/* ============================================================
   LOADING SCREEN
   ============================================================ */

function DashboardLoading() {
  return (
    <main
      className="
        flex
        min-h-screen
        items-center
        justify-center
        bg-[#F4F7FB]
        px-6
      "
      aria-label="Loading Noble Loan Solutions"
      aria-busy="true"
    >
      <div className="flex w-full max-w-sm flex-col items-center">

        {/* Premium logo */}

        <div
          className="
            relative
            flex
            h-20
            w-20
            items-center
            justify-center
            rounded-2xl
            border
            border-[#DCE4EF]
            bg-white
            p-3
            shadow-[0_12px_35px_rgba(11,31,58,0.10)]
          "
        >
          <BrandMark
            size={56}
            priority
          />

          <span
            className="
              absolute
              -bottom-1
              -right-1
              h-4
              w-4
              rounded-full
              border-[3px]
              border-white
              bg-[#F4C430]
              shadow-sm
            "
            aria-hidden="true"
          />
        </div>

        {/* Loading indicator */}

        <div
          className="
            relative
            mt-7
            h-9
            w-9
          "
          aria-hidden="true"
        >
          <div
            className="
              absolute
              inset-0
              rounded-full
              border-[3px]
              border-[#E3EAF3]
            "
          />

          <div
            className="
              absolute
              inset-0
              animate-spin
              rounded-full
              border-[3px]
              border-transparent
              border-t-[#0B1F3A]
              border-r-[#F4C430]
            "
          />
        </div>

        {/* Loading message */}

        <div className="mt-5 text-center">

          <p className="text-sm font-bold text-[#0B1F3A]">
            Loading Noble Loan Solutions
          </p>

          <p className="mt-1 text-xs text-gray-500">
            Preparing your financial workspace…
          </p>

        </div>

      </div>
    </main>
  );
}

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
     AUTHENTICATION REDIRECT
     ========================================================== */

  useEffect(() => {
    if (auth.loading) {
      return;
    }

    if (!auth.user) {
      router.replace('/login');
    }
  }, [
    auth.loading,
    auth.user,
    router,
  ]);

  /* ==========================================================
     LOADING
     ========================================================== */

  if (auth.loading) {
    return <DashboardLoading />;
  }

  /* ==========================================================
     NOT AUTHENTICATED
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

        <main className="min-h-screen bg-[#F4F7FB]">

          <ForcedPasswordChange />

        </main>

        <ToastContainer />

      </AuthContext.Provider>
    );
  }

  /* ==========================================================
     MAIN DASHBOARD
     ========================================================== */

  return (
    <AuthContext.Provider value={auth}>

      <OfflineProvider authHeader={authHeader} />

      <div
        className="
          min-h-screen
          bg-[#F4F7FB]
          text-gray-900
        "
      >

        <div className="flex min-h-screen">

          {/* ==================================================
              SIDEBAR
              ================================================== */}

          <aside
            className="
              fixed
              bottom-0
              left-0
              top-0
              z-40
              w-64
            "
            aria-label="Primary navigation"
          >
            <Sidebar />
          </aside>

          {/* ==================================================
              APPLICATION AREA
              ================================================== */}

          <div
            className="
              flex
              min-h-screen
              min-w-0
              flex-1
              flex-col
              pl-64
            "
          >

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

              <div
                className="
                  flex
                  h-full
                  items-center
                  justify-between
                  gap-4
                  px-5
                  sm:px-7
                "
              >

                {/* =================================================
                    BRAND
                    ================================================= */}

                <div
                  className="
                    flex
                    min-w-0
                    items-center
                    gap-3
                  "
                >

                  {/* Premium favicon / brand mark */}

                  <div
                    className="
                      flex
                      h-10
                      w-10
                      shrink-0
                      items-center
                      justify-center
                      rounded-xl
                      border
                      border-[#DCE4EF]
                      bg-white
                      p-1.5
                      shadow-sm
                    "
                  >
                    <BrandMark size={32} />
                  </div>

                  {/* Brand text */}

                  <div className="min-w-0">

                    <p
                      className="
                        truncate
                        text-[11px]
                        font-extrabold
                        uppercase
                        tracking-[0.18em]
                        text-[#C99A00]
                      "
                    >
                      Noble Loan Solutions
                    </p>

                    <h2
                      className="
                        truncate
                        text-sm
                        font-bold
                        text-[#0B1F3A]
                      "
                    >
                      Loan Management Platform
                    </h2>

                  </div>

                </div>

                {/* =================================================
                    RIGHT SIDE
                    ================================================= */}

                <div
                  className="
                    flex
                    shrink-0
                    items-center
                    gap-2
                    sm:gap-3
                  "
                >

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
                    role="status"
                    aria-label="System online"
                  >

                    <span
                      className="
                        relative
                        flex
                        h-2
                        w-2
                      "
                      aria-hidden="true"
                    >

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

                    <span
                      className="
                        text-xs
                        font-semibold
                        text-[#806200]
                      "
                    >
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
                      shadow-sm
                      transition
                      duration-200
                      hover:border-[#C7D5E5]
                      hover:bg-[#EEF3F9]
                      hover:text-[#0B1F3A]
                      focus:outline-none
                      focus:ring-2
                      focus:ring-[#F4C430]/40
                      focus:ring-offset-2
                    "
                  >

                    <span
                      className="
                        text-base
                        leading-none
                      "
                      aria-hidden="true"
                    >
                      🔔
                    </span>

                    {/* Notification indicator */}

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
                      aria-hidden="true"
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
                      pl-3
                      sm:pl-4
                    "
                  >

                    {/* Organization information */}

                    <div
                      className="
                        hidden
                        max-w-[240px]
                        text-right
                        sm:block
                      "
                    >

                      <p
                        className="
                          truncate
                          text-sm
                          font-bold
                          text-[#0B1F3A]
                        "
                      >
                        {auth.user.organizationName ||
                          'Noble Loan Solutions'}
                      </p>

                      <p
                        className="
                          truncate
                          text-[11px]
                          font-medium
                          text-gray-500
                        "
                      >
                        Financial Management Workspace
                      </p>

                    </div>

                    {/* Premium avatar */}

                    <div
                      className="
                        relative
                        flex
                        h-10
                        w-10
                        shrink-0
                        items-center
                        justify-center
                        overflow-hidden
                        rounded-xl
                        border
                        border-[#DCE4EF]
                        bg-white
                        p-1.5
                        shadow-sm
                      "
                    >

                      <BrandMark size={30} />

                      {/* Online indicator */}

                      <span
                        className="
                          absolute
                          bottom-[-1px]
                          right-[-1px]
                          h-3
                          w-3
                          rounded-full
                          border-2
                          border-white
                          bg-[#F4C430]
                        "
                        aria-label="Online"
                      />

                    </div>

                  </div>

                </div>

              </div>

            </header>

            {/* =================================================
                MAIN CONTENT
                ================================================= */}

            <main
              className="
                min-w-0
                flex-1
              "
            >

              <div
                className="
                  mx-auto
                  w-full
                  max-w-[1800px]
                  px-4
                  py-5
                  sm:px-6
                  sm:py-6
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
                px-5
                py-4
                sm:px-7
              "
            >

              <div
                className="
                  flex
                  flex-col
                  items-center
                  justify-between
                  gap-2
                  text-center
                  text-[11px]
                  text-gray-400
                  sm:flex-row
                  sm:text-left
                "
              >

                <p>
                  © {new Date().getFullYear()} Noble Loan Solutions.
                  All rights reserved.
                </p>

                <div
                  className="
                    flex
                    items-center
                    gap-2
                  "
                >

                  <span>
                    Secure Financial Platform
                  </span>

                  <span
                    className="
                      h-1
                      w-1
                      rounded-full
                      bg-[#F4C430]
                    "
                    aria-hidden="true"
                  />

                  <span>
                    Loan Management System
                  </span>

                </div>

              </div>

            </footer>

          </div>

        </div>

      </div>

      {/* ======================================================
          GLOBAL TOASTS
          ====================================================== */}

      <ToastContainer />

    </AuthContext.Provider>
  );
}