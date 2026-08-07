
'use client';

import {
  useState,
  useEffect,
  createContext,
  useContext,
} from 'react';

import Link from 'next/link';
import { usePathname } from 'next/navigation';

import { OfflineProvider } from '../../components/OfflineProvider';
import { ToastContainer } from '../../components/ui/ToastContainer';
import { TENANT_SLUG } from '../../lib/tenant';

/* ============================================================
   TENANT CONFIGURATION
   ============================================================ */

interface TenantConfig {
  name: string;
  slug: string;
  country: string;
  currency: string;

  primaryColor: string;
  accentColor: string;

  logoUrl?: string;

  contactEmail?: string;
  contactPhone?: string;

  website?: string;
  address?: string;

  tagline?: string;
  mission?: string;
  vision?: string;

  founded?: string;
  registrationNumber?: string;

  socialMedia?: {
    facebook?: string;
    instagram?: string;
    linkedin?: string;
    twitter?: string;
    whatsapp?: string;
  };

  mapUrl?: string;

  services?: {
    title: string;
    description: string;
    icon: string;
    rate: string;
    rateType?: string;
    maxAmount: string;
    term: string;
  }[];

  hero?: {
    headline: string;
    subtext: string;
  };

  stats?: {
    icon: string;
    value: string;
    label: string;
  }[];

  testimonials?: {
    name: string;
    role: string;
    text: string;
    rating: number;
  }[];

  team?: {
    name: string;
    role: string;
    initials: string;
  }[];
}

/* ============================================================
   TENANT CONTEXT
   ============================================================ */

const TenantCtx =
  createContext<TenantConfig | null>(null);

export const useTenant = () =>
  useContext(TenantCtx);

/* ============================================================
   API
   ============================================================ */

const API_BASE =
  process.env.NEXT_PUBLIC_API_URL ||
  'http://localhost:8080/api';

/* ============================================================
   NOBLE LOAN SOLUTIONS LOGO
   ============================================================

   IMPORTANT:

   Save the SVG file here:

   public/images/noble-loan-solutions-logo.svg

   It will then be available at:

   /images/noble-loan-solutions-logo.svg

============================================================ */

const LOGO_SRC =
  '/images/noble-loan-solutions-logo.svg';

/* ============================================================
   FALLBACK TENANT
   ============================================================ */

const FALLBACK_TENANT: TenantConfig = {
  name: 'Noble Loan Solutions',

  slug: TENANT_SLUG,

  country: 'Rwanda',

  currency: 'RWF',

  primaryColor: '#0D2C54',

  accentColor: '#D4AF37',

  services: [],
};

/* ============================================================
   ICONS
   ============================================================ */

function IconPhone() {
  return (
    <svg
      width="13"
      height="13"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path
        d="
          M22 16.92v3
          a2 2 0 0 1-2.18 2
          19.79 19.79 0 0 1-8.63-3.07
          19.5 19.5 0 0 1-6-6
          19.79 19.79 0 0 1-3.07-8.67
          A2 2 0 0 1 4.11 2h3
          a2 2 0 0 1 2 1.72
          c.127.96.361 1.903.7 2.81
          a2 2 0 0 1-.45 2.11L8.09 9.91
          a16 16 0 0 0 6 6
          l1.27-1.27
          a2 2 0 0 1 2.11-.45
          c.907.339 1.85.573 2.81.7
          A2 2 0 0 1 22 16.92z
        "
      />
    </svg>
  );
}

function IconMail() {
  return (
    <svg
      width="13"
      height="13"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path
        d="
          M22 6
          c0-1.1-.9-2-2-2H4
          c-1.1 0-2 .9-2 2v12
          c0 1.1.9 2 2 2h16
          c1.1 0 2-.9 2-2V6z
        "
      />

      <path d="m22 6-10 7L2 6" />
    </svg>
  );
}

function IconShield() {
  return (
    <svg
      width="14"
      height="14"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />

      <path d="m9 12 2 2 4-4" />
    </svg>
  );
}

/* ============================================================
   MAIN LAYOUT
   ============================================================ */

export default function SiteLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const pathname = usePathname();

  const slug = TENANT_SLUG;

  /* ==========================================================
     STATE
     ========================================================== */

  const [tenant, setTenant] =
    useState<TenantConfig | null>(null);

  const [loading, setLoading] =
    useState(true);

  const [notFound, setNotFound] =
    useState(false);

  const [menuOpen, setMenuOpen] =
    useState(false);

  /* ==========================================================
     LOAD TENANT
     ========================================================== */

  useEffect(() => {
    let cancelled = false;

    setLoading(true);
    setNotFound(false);

    fetch(
      `${API_BASE}/public/tenant/${slug}`
    )
      .then((response) => {
        if (!response.ok) {
          throw new Error(
            `Tenant request failed: ${response.status}`
          );
        }

        return response.json();
      })
      .then((configRes) => {
        if (cancelled) {
          return;
        }

        const data =
          configRes?.data;

        if (
          !data ||
          configRes?.success === false
        ) {
          setNotFound(true);
          return;
        }

        setTenant({
          ...FALLBACK_TENANT,
          ...data,
          slug,
        });
      })
      .catch(() => {
        if (!cancelled) {
          setNotFound(true);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [slug]);

  /* ==========================================================
     LOADING
     ========================================================== */

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-white">
        <div
          className="
            h-9
            w-9
            rounded-full
            border-2
            border-[#0D2C54]
            border-t-transparent
            animate-spin
          "
        />
      </div>
    );
  }

  /* ==========================================================
     NOT FOUND
     ========================================================== */

  if (notFound || !tenant) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50 p-6">
        <div
          className="
            w-full
            max-w-md
            rounded-2xl
            border
            border-gray-200
            bg-white
            p-8
            text-center
            shadow-sm
          "
        >
          <div
            className="
              mx-auto
              mb-5
              flex
              h-14
              w-14
              items-center
              justify-center
              rounded-full
              bg-[#0D2C54]/5
            "
          >
            <span className="text-xl">
              !
            </span>
          </div>

          <h1 className="mb-2 text-xl font-bold text-gray-900">
            Site temporarily unavailable
          </h1>

          <p className="text-sm leading-6 text-gray-500">
            We couldn't reach our services.
            Please try again shortly, or
            contact us directly if this persists.
          </p>
        </div>
      </div>
    );
  }

  /* ==========================================================
     NAVIGATION
     ========================================================== */

  const navLinks = [
    {
      href: '/',
      label: 'Home',
    },
    {
      href: '/services',
      label: 'Services',
    },
    {
      href: '/about',
      label: 'About Us',
    },
    {
      href: '/contact',
      label: 'Contact',
    },
    {
      href: '/track',
      label: 'Track Application',
    },
  ];

  /* ==========================================================
     BRAND COLORS
     ========================================================== */

  const primary =
    tenant.primaryColor ||
    '#0D2C54';

  const accent =
    tenant.accentColor ||
    '#D4AF37';

  /* ==========================================================
     ACTIVE ROUTE
     ========================================================== */

  const isActive = (
    href: string
  ) => pathname === href;

  /* ==========================================================
     CLOSE MOBILE MENU AFTER NAVIGATION
     ========================================================== */

  const closeMenu = () => {
    setMenuOpen(false);
  };

  /* ==========================================================
     RENDER
     ========================================================== */

  return (
    <TenantCtx.Provider value={tenant}>

      <OfflineProvider
        authHeader={() => ({})}
      />

      <ToastContainer />

      <div className="min-h-screen bg-white font-sans">

        {/* ====================================================
            TOP UTILITY BAR
        ==================================================== */}

        <div
          className="
            bg-[#07152A]
            text-white/80
          "
        >
          <div
            className="
              mx-auto
              flex
              max-w-7xl
              items-center
              justify-between
              px-4
              py-2
              text-xs
            "
          >

            {/* CONTACT */}

            <div className="flex items-center gap-6">

              {tenant.contactPhone && (
                <span className="flex items-center gap-1.5">
                  <IconPhone />
                  {tenant.contactPhone}
                </span>
              )}

              {tenant.contactEmail && (
                <span className="hidden items-center gap-1.5 sm:flex">
                  <IconMail />
                  {tenant.contactEmail}
                </span>
              )}

            </div>

            {/* REGULATORY */}

            <div className="flex items-center gap-1.5 text-white/60">

              <IconShield />

              <span className="hidden sm:inline">
                Licensed &amp; regulated financial institution
              </span>

              <span className="sm:hidden">
                Regulated institution
              </span>

            </div>

          </div>
        </div>

        {/* ====================================================
            MAIN NAVIGATION
        ==================================================== */}

        <nav
          className="
            sticky
            top-0
            z-50
            border-b
            border-gray-200
            bg-white/95
            backdrop-blur-md
          "
        >

          <div
            className="
              mx-auto
              flex
              max-w-7xl
              items-center
              justify-between
              px-4
              py-3
            "
          >

            {/* ==================================================
                BRAND LOGO
            ================================================== */}

            <Link
              href="/"
              className="
                flex
                min-w-0
                items-center
              "
            >

              <img
                src={LOGO_SRC}
                alt="Noble Loan Solutions"
                width={500}
                height={100}
                className="
                  block
                  h-auto
                  w-auto
                  max-h-12
                  max-w-[270px]
                  object-contain
                "
                draggable={false}
              />

            </Link>

            {/* ==================================================
                DESKTOP NAVIGATION
            ================================================== */}

            <div className="hidden items-center gap-1 md:flex">

              {navLinks.map((link) => {

                const active =
                  isActive(link.href);

                return (
                  <Link
                    key={link.href}
                    href={link.href}
                    className={`
                      rounded-md
                      border-b-2
                      px-4
                      py-2.5
                      text-sm
                      font-semibold
                      transition-all
                      duration-200

                      ${
                        active
                          ? 'bg-gray-50'
                          : `
                            border-transparent
                            text-gray-600
                            hover:bg-gray-50
                            hover:text-gray-900
                          `
                      }
                    `}
                    style={
                      active
                        ? {
                            color: primary,
                            borderColor: primary,
                          }
                        : undefined
                    }
                  >
                    {link.label}
                  </Link>
                );
              })}

              {/* STAFF LOGIN */}

              <Link
                href="/login"
                className="
                  ml-2
                  rounded-md
                  border
                  border-gray-300
                  px-4
                  py-2.5
                  text-sm
                  font-semibold
                  text-gray-700
                  transition-all
                  hover:border-gray-400
                  hover:bg-gray-50
                "
              >
                Staff Login
              </Link>

              {/* APPLY NOW */}

              <Link
                href="/apply"
                className="
                  ml-1
                  rounded-md
                  px-5
                  py-2.5
                  text-sm
                  font-bold
                  text-white
                  shadow-sm
                  transition-all
                  hover:-translate-y-0.5
                  hover:shadow-md
                "
                style={{
                  backgroundColor: primary,
                }}
              >
                Apply Now
              </Link>

            </div>

            {/* ==================================================
                MOBILE MENU BUTTON
            ================================================== */}

            <button
              type="button"
              className="
                flex
                h-10
                w-10
                flex-col
                items-center
                justify-center
                rounded-lg
                md:hidden
              "
              onClick={() =>
                setMenuOpen(
                  (previous) =>
                    !previous
                )
              }
              aria-label="Toggle navigation"
              aria-expanded={menuOpen}
            >
              <span className="block h-0.5 w-6 bg-gray-700" />
              <span className="my-1.5 block h-0.5 w-6 bg-gray-700" />
              <span className="block h-0.5 w-6 bg-gray-700" />
            </button>

          </div>

          {/* ==================================================
              MOBILE NAVIGATION
          ================================================== */}

          {menuOpen && (
            <div
              className="
                border-t
                border-gray-100
                bg-white
                px-4
                py-4
                shadow-lg
                md:hidden
              "
            >

              <div className="space-y-1">

                {navLinks.map((link) => {

                  const active =
                    isActive(link.href);

                  return (
                    <Link
                      key={link.href}
                      href={link.href}
                      onClick={closeMenu}
                      className={`
                        block
                        rounded-lg
                        px-4
                        py-3
                        text-sm
                        font-semibold
                        transition-colors

                        ${
                          active
                            ? 'bg-gray-50'
                            : 'text-gray-700 hover:bg-gray-50'
                        }
                      `}
                      style={
                        active
                          ? {
                              color: primary,
                            }
                          : undefined
                      }
                    >
                      {link.label}
                    </Link>
                  );
                })}

              </div>

              {/* MOBILE APPLY */}

              <Link
                href="/apply"
                onClick={closeMenu}
                className="
                  mt-3
                  block
                  rounded-lg
                  px-4
                  py-3
                  text-center
                  text-sm
                  font-bold
                  text-white
                  shadow-sm
                "
                style={{
                  backgroundColor: primary,
                }}
              >
                Apply Now
              </Link>

              {/* MOBILE LOGIN */}

              <Link
                href="/login"
                onClick={closeMenu}
                className="
                  mt-2
                  block
                  rounded-lg
                  px-4
                  py-3
                  text-center
                  text-sm
                  font-semibold
                "
                style={{
                  color: primary,
                }}
              >
                Staff Login →
              </Link>

            </div>
          )}

        </nav>

        {/* ====================================================
            PAGE CONTENT
        ==================================================== */}

        <main>
          {children}
        </main>

        {/* ====================================================
            FOOTER
        ==================================================== */}

        <footer
          className="
            mt-16
            bg-[#07152A]
            text-white
          "
        >

          <div
            className="
              mx-auto
              grid
              max-w-7xl
              grid-cols-1
              gap-10
              px-4
              py-14
              md:grid-cols-4
            "
          >

            {/* ==================================================
                COMPANY
            ================================================== */}

            <div className="md:col-span-2">

              <img
                src={LOGO_SRC}
                alt="Noble Loan Solutions"
                width={500}
                height={100}
                className="
                  mb-5
                  block
                  h-auto
                  w-auto
                  max-h-12
                  max-w-[280px]
                  object-contain
                "
                draggable={false}
              />

              {tenant.mission && (
                <div className="
                  mb-5
                  max-w-md
                  text-sm
                  leading-7
                  text-white/60
                ">
                  {tenant.mission}
                </div>
              )}

              <div className="
                space-y-2
                text-sm
                text-white/50
              ">

                {tenant.address && (
                  <div>
                    {tenant.address}
                  </div>
                )}

                {tenant.contactPhone && (
                  <div className="flex items-center gap-2">
                    <IconPhone />
                    {tenant.contactPhone}
                  </div>
                )}

                {tenant.contactEmail && (
                  <div className="flex items-center gap-2">
                    <IconMail />
                    {tenant.contactEmail}
                  </div>
                )}

              </div>

            </div>

            {/* ==================================================
                QUICK LINKS
            ================================================== */}

            <div>

              <div className="
                mb-5
                text-xs
                font-bold
                uppercase
                tracking-[0.18em]
                text-white/90
              ">
                Quick Links
              </div>

              <div className="
                space-y-3
                text-sm
                text-white/60
              ">

                {navLinks.map((link) => (
                  <Link
                    key={link.href}
                    href={link.href}
                    className="
                      block
                      transition-colors
                      hover:text-white
                    "
                  >
                    {link.label}
                  </Link>
                ))}

              </div>

            </div>

            {/* ==================================================
                SERVICES
            ================================================== */}

            <div>

              <div className="
                mb-5
                text-xs
                font-bold
                uppercase
                tracking-[0.18em]
                text-white/90
              ">
                Our Services
              </div>

              <div className="
                space-y-3
                text-sm
                text-white/60
              ">

                {tenant.services
                  ?.slice(0, 5)
                  .map((service) => (
                    <div key={service.title}>
                      {service.title}
                    </div>
                  ))}

              </div>

            </div>

          </div>

          {/* ==================================================
              FOOTER BOTTOM
          ================================================== */}

          <div
            className="
              border-t
              border-white/10
              px-4
              py-5
            "
          >

            <div
              className="
                mx-auto
                flex
                max-w-7xl
                flex-col
                items-center
                justify-between
                gap-3
                text-center
                text-xs
                text-white/40
                md:flex-row
                md:text-left
              "
            >

              <span>
                © {new Date().getFullYear()}{' '}
                {tenant.name}.
                {' '}
                All rights reserved.
                {' '}
                {tenant.registrationNumber
                  ? `Reg. No. ${tenant.registrationNumber}`
                  : ''}
              </span>

              <span className="flex items-center gap-4">

                <Link
                  href="/terms"
                  className="
                    transition-colors
                    hover:text-white/70
                  "
                >
                  Terms &amp; Conditions
                </Link>

                <Link
                  href="/privacy"
                  className="
                    transition-colors
                    hover:text-white/70
                  "
                >
                  Privacy Policy
                </Link>

              </span>

              <span>
                Your deposits and data are protected
                in line with applicable financial
                regulations.
              </span>

            </div>

          </div>

        </footer>

      </div>

    </TenantCtx.Provider>
  );
}
