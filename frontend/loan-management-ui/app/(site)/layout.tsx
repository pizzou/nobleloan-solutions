
'use client';

import { useState, useEffect, createContext, useContext } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { OfflineProvider } from '../../components/OfflineProvider';
import { ToastContainer } from '../../components/ui/ToastContainer';
import { TENANT_SLUG } from '../../lib/tenant';

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

const TenantCtx = createContext<TenantConfig | null>(null);

export const useTenant = () => useContext(TenantCtx);

const API_BASE =
  process.env.NEXT_PUBLIC_API_URL ||
  'http://localhost:8080/api';

/*
|--------------------------------------------------------------------------
| NOBLE LOAN SOLUTIONS LOGO
|--------------------------------------------------------------------------
| Place the final logo here:
|
| public/images/noble-loan-solutions-logo.svg
|
*/
const LOGO_SRC = '/images/noble-loan-solutions-logo.svg';

/** Shown only while the configured institution profile is loading. */
const FALLBACK_TENANT: TenantConfig = {
  name: 'Loading...',
  slug: TENANT_SLUG,
  country: 'Rwanda',
  currency: 'RWF',
  primaryColor: '#0F1B3D',
  accentColor: '#C9A227',
  services: [],
};

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
    >
      <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.127.96.361 1.903.7 2.81a2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.907.339 1.85.573 2.81.7A2 2 0 0 1 22 16.92z" />
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
    >
      <path d="M4 4h16v16H4z" opacity="0" />
      <path d="M22 6c0-1.1-.9-2-2-2H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6z" />
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
    >
      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
      <path d="m9 12 2 2 4-4" />
    </svg>
  );
}

export default function SiteLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const pathname = usePathname();

  const slug = TENANT_SLUG;

  const [tenant, setTenant] =
    useState<TenantConfig | null>(null);

  const [loading, setLoading] =
    useState(true);

  const [notFound, setNotFound] =
    useState(false);

  const [menuOpen, setMenuOpen] =
    useState(false);

  useEffect(() => {
    let cancelled = false;

    setLoading(true);
    setNotFound(false);

    fetch(`${API_BASE}/public/tenant/${slug}`)
      .then((response) => response.json())
      .then((configRes) => {
        if (cancelled) {
          return;
        }

        const data = configRes?.data;

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

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-white">
        <div className="w-8 h-8 border-2 border-[#0F1B3D] border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  if (notFound || !tenant) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50 p-6">
        <div className="bg-white rounded-lg border border-gray-200 p-8 max-w-md text-center">
          <h1 className="font-bold text-gray-900 mb-2">
            Site temporarily unavailable
          </h1>

          <p className="text-sm text-gray-500">
            We couldn't reach our services. Please try again
            shortly, or contact us directly if this persists.
          </p>
        </div>
      </div>
    );
  }

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

  const isActive = (href: string) =>
    pathname === href;

  const primary =
    tenant.primaryColor;

  const accent =
    tenant.accentColor;

  return (
    <TenantCtx.Provider value={tenant}>
      <OfflineProvider authHeader={() => ({})} />

      <ToastContainer />

      <div className="min-h-screen bg-white font-sans">

        {/* =====================================================
            TOP UTILITY BAR
        ===================================================== */}

        <div
          style={{
            backgroundColor: '#0B1220',
          }}
          className="text-white/80 text-xs py-2 px-4"
        >
          <div className="max-w-7xl mx-auto flex items-center justify-between">

            <div className="flex items-center gap-6">

              {tenant.contactPhone && (
                <span className="flex items-center gap-1.5">
                  <IconPhone />
                  {tenant.contactPhone}
                </span>
              )}

              {tenant.contactEmail && (
                <span className="hidden sm:flex items-center gap-1.5">
                  <IconMail />
                  {tenant.contactEmail}
                </span>
              )}

            </div>

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

        {/* =====================================================
            MAIN NAVIGATION
        ===================================================== */}

        <nav className="bg-white border-b border-gray-200 sticky top-0 z-50">

          <div className="max-w-7xl mx-auto px-4 py-3 flex items-center justify-between">

            {/* =================================================
                BRAND
            ================================================= */}

            <Link
              href="/"
              className="flex items-center gap-3 min-w-0"
            >

              <div className="flex items-center justify-center shrink-0">

                <img
                  src={LOGO_SRC}
                  alt="Noble Loan Solutions"
                  className="
                    h-12
                    w-auto
                    max-w-[260px]
                    object-contain
                  "
                />

              </div>

            </Link>

            {/* =================================================
                DESKTOP NAVIGATION
            ================================================= */}

            <div className="hidden md:flex items-center gap-1">

              {navLinks.map((link) => {

                const active =
                  isActive(link.href);

                return (
                  <Link
                    key={link.href}
                    href={link.href}
                    className={`
                      px-4
                      py-2
                      rounded-md
                      text-sm
                      font-semibold
                      transition-all
                      border-b-2

                      ${
                        active
                          ? 'bg-gray-50'
                          : 'text-gray-600 border-transparent hover:text-gray-900 hover:bg-gray-50'
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
                  px-4
                  py-2
                  rounded-md
                  text-sm
                  font-semibold
                  border
                  transition-colors
                  hover:bg-gray-50
                "
                style={{
                  borderColor: '#D1D5DB',
                  color: '#374151',
                }}
              >
                Staff Login
              </Link>

              {/* APPLY NOW */}

              <Link
                href="/apply"
                className="
                  ml-1
                  px-5
                  py-2.5
                  rounded-md
                  text-sm
                  font-bold
                  text-white
                  shadow-sm
                  hover:opacity-90
                  transition-opacity
                "
                style={{
                  backgroundColor: primary,
                }}
              >
                Apply Now
              </Link>

            </div>

            {/* =================================================
                MOBILE MENU BUTTON
            ================================================= */}

            <button
              className="md:hidden"
              onClick={() =>
                setMenuOpen(!menuOpen)
              }
              aria-label="Toggle menu"
            >
              <div className="w-6 h-0.5 bg-gray-700 my-1.5" />
              <div className="w-6 h-0.5 bg-gray-700 my-1.5" />
              <div className="w-6 h-0.5 bg-gray-700 my-1.5" />
            </button>

          </div>

          {/* ===================================================
              MOBILE NAVIGATION
          =================================================== */}

          {menuOpen && (
            <div className="md:hidden border-t border-gray-100 px-4 py-3 space-y-1">

              {navLinks.map((link) => (
                <Link
                  key={link.href}
                  href={link.href}
                  onClick={() =>
                    setMenuOpen(false)
                  }
                  className="
                    block
                    px-4
                    py-2.5
                    rounded-md
                    text-sm
                    font-semibold
                    text-gray-700
                    hover:bg-gray-50
                  "
                >
                  {link.label}
                </Link>
              ))}

              <Link
                href="/apply"
                onClick={() =>
                  setMenuOpen(false)
                }
                className="
                  block
                  px-4
                  py-2.5
                  rounded-md
                  text-sm
                  font-bold
                  text-white
                  text-center
                  mt-2
                "
                style={{
                  backgroundColor: primary,
                }}
              >
                Apply Now
              </Link>

              <Link
                href="/login"
                onClick={() =>
                  setMenuOpen(false)
                }
                className="
                  block
                  px-4
                  py-2.5
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

        {/* =====================================================
            PAGE CONTENT
        ===================================================== */}

        <main>
          {children}
        </main>

        {/* =====================================================
            FOOTER
        ===================================================== */}

        <footer
          style={{
            backgroundColor: '#0B1220',
          }}
          className="text-white mt-16"
        >

          <div className="max-w-7xl mx-auto px-4 py-12 grid grid-cols-1 md:grid-cols-4 gap-8">

            {/* BRAND / COMPANY */}

            <div className="md:col-span-2">

              <img
                src={LOGO_SRC}
                alt="Noble Loan Solutions"
                className="
                  h-12
                  w-auto
                  max-w-[280px]
                  object-contain
                  mb-5
                "
              />

              <div className="text-white/60 text-sm leading-relaxed mb-4 max-w-md">
                {tenant.mission}
              </div>

              <div className="text-sm text-white/50 space-y-1">

                <div>
                  {tenant.address}
                </div>

                <div className="flex items-center gap-2">
                  <IconPhone />
                  {tenant.contactPhone}
                </div>

                <div className="flex items-center gap-2">
                  <IconMail />
                  {tenant.contactEmail}
                </div>

              </div>

            </div>

            {/* QUICK LINKS */}

            <div>

              <div className="
                font-semibold
                mb-4
                text-white/90
                text-sm
                uppercase
                tracking-wider
              ">
                Quick Links
              </div>

              <div className="
                space-y-2
                text-sm
                text-white/60
              ">

                {navLinks.map((link) => (
                  <Link
                    key={link.href}
                    href={link.href}
                    className="
                      block
                      hover:text-white
                      transition
                    "
                  >
                    {link.label}
                  </Link>
                ))}

              </div>

            </div>

            {/* SERVICES */}

            <div>

              <div className="
                font-semibold
                mb-4
                text-white/90
                text-sm
                uppercase
                tracking-wider
              ">
                Our Services
              </div>

              <div className="
                space-y-2
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

          {/* FOOTER BOTTOM */}

          <div className="border-t border-white/10 px-4 py-4">

            <div className="
              max-w-7xl
              mx-auto
              flex
              flex-col
              md:flex-row
              items-center
              justify-between
              text-xs
              text-white/40
              gap-2
            ">

              <span>
                © {new Date().getFullYear()} {tenant.name}.
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
                  className="hover:text-white/70 transition"
                >
                  Terms &amp; Conditions
                </Link>

                <Link
                  href="/privacy"
                  className="hover:text-white/70 transition"
                >
                  Privacy Policy
                </Link>

              </span>

              <span>
                Your deposits and data are protected in line with
                applicable financial regulations.
              </span>

            </div>

          </div>

        </footer>

      </div>
    </TenantCtx.Provider>
  );
}
