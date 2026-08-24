"use client";

import { useState, useEffect, createContext, useContext } from "react";

import Link from "next/link";
import { usePathname } from "next/navigation";

import { ToastContainer } from "../../components/ui/ToastContainer";
import { TENANT_SLUG } from "../../lib/tenant";

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
    loanType?: string;
    rate: string | number;
    rateType?: string;
    interestRate?: string | number;
    processingFeeRate?: string | number;
    managementFeeRate?: string | number;
    minAmount?: string | number;
    maxAmount?: string | number | null;
    minTermMonths?: number;
    maxTermMonths?: number;
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

const TenantCtx = createContext<TenantConfig | null>(null);

export const useTenant = () => useContext(TenantCtx);

/* ============================================================
   API
   ============================================================ */

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api";

/* ============================================================
   BRAND COLORS
   ============================================================ */

const BRAND_NAVY = "#0D2C54";
const BRAND_NAVY_DARK = "#071B35";
const BRAND_GOLD = "#D4AF37";
const BRAND_GOLD_DARK = "#B8941F";

/* ============================================================
   FALLBACK TENANT
   ============================================================ */

const FALLBACK_TENANT: TenantConfig = {
  name: "Noble Loan Solutions",
  slug: TENANT_SLUG,
  country: "Rwanda",
  currency: "RWF",
  primaryColor: BRAND_NAVY,
  accentColor: BRAND_GOLD,
  services: [],
};

/* ============================================================
   NOBLE LOGO
   ============================================================ */

function NobleLogo({
  className = "",
  showText = true,
}: {
  className?: string;
  showText?: boolean;
}) {
  return (
    <div
      className={`flex items-center ${className}`}
      aria-label="Noble Loan Solutions"
    >
      {/* Shield */}
      <svg
        viewBox="0 0 90 100"
        width="52"
        height="58"
        role="img"
        aria-label="Noble Loan Solutions logo"
        className="flex-shrink-0"
      >
        {/* Outer shield */}
        <path
          d="
            M45 5
            Q73 5 80 12
            Q83 52 45 93
            Q7 52 10 12
            Q17 5 45 5
            Z
          "
          fill="none"
          stroke={BRAND_GOLD}
          strokeWidth="5"
          strokeLinejoin="round"
        />

        {/* Inner shield */}
        <path
          d="
            M45 12
            Q68 12 73 17
            Q75 49 45 83
            Q15 49 17 17
            Q22 12 45 12
            Z
          "
          fill="none"
          stroke={BRAND_GOLD}
          strokeWidth="2"
          strokeLinejoin="round"
        />

        {/* Noble N */}
        <text
          x="45"
          y="64"
          textAnchor="middle"
          fontFamily="Georgia, 'Times New Roman', serif"
          fontSize="48"
          fontWeight="700"
          fill={BRAND_GOLD}
        >
          N
        </text>
      </svg>

      {/* Company name */}
      {showText && (
        <div className="ml-3 leading-none">
          <div
            className="font-bold tracking-[0.08em]"
            style={{
              color: BRAND_NAVY,
              fontSize: "20px",
            }}
          >
            NOBLE
          </div>

          <div
            className="font-light tracking-[0.04em]"
            style={{
              color: BRAND_NAVY,
              fontSize: "14px",
            }}
          >
            LOAN SOLUTIONS
          </div>

          <div
            className="mt-1 font-semibold uppercase tracking-[0.18em]"
            style={{
              color: BRAND_GOLD_DARK,
              fontSize: "7px",
            }}
          >
            Financial Support Partner
          </div>
        </div>
      )}
    </div>
  );
}

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
      <path d="M22 16.92v3a2 2 0 0 1-2.18 2A19.79 19.79 0 0 1 11.19 19a19.5 19.5 0 0 1-6-6A19.79 19.79 0 0 1 2.11 4.33 2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.127.96.361 1.903.7 2.81a2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.907.339 1.85.573 2.81.7A2 2 0 0 1 22 16.92z" />
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
      aria-hidden="true"
    >
      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
      <path d="m9 12 2 2 4-4" />
    </svg>
  );
}

/* ============================================================
   SITE LAYOUT
   ============================================================ */

export default function SiteLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const pathname = usePathname();

  const slug = TENANT_SLUG;

  const [tenant, setTenant] = useState<TenantConfig | null>(null);

  const [loading, setLoading] = useState(true);

  const [notFound, setNotFound] = useState(false);

  const [menuOpen, setMenuOpen] = useState(false);

  /* ==========================================================
     LOAD TENANT
     ========================================================== */

  useEffect(() => {
    let cancelled = false;

    setLoading(true);
    setNotFound(false);

    fetch(`${API_BASE}/public/tenant/${slug}`)
      .then(async (response) => {
        if (!response.ok) {
          throw new Error(`Tenant request failed: ${response.status}`);
        }

        return response.json();
      })
      .then((configRes) => {
        if (cancelled) return;

        const data = configRes?.data;

        if (!data || configRes?.success === false) {
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
      <div className="flex min-h-screen items-center justify-center bg-white">
        <div className="flex flex-col items-center gap-4">
          <NobleLogo showText={false} />

          <div className="h-7 w-7 animate-spin rounded-full border-2 border-gray-200 border-t-[#D4AF37]" />

          <p className="text-xs font-medium tracking-wide text-gray-400">
            Loading Noble Loan Solutions
          </p>
        </div>
      </div>
    );
  }

  /* ==========================================================
     NOT FOUND
     ========================================================== */

  if (notFound || !tenant) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-gray-50 p-6">
        <div className="w-full max-w-md rounded-2xl border border-gray-200 bg-white p-8 text-center shadow-sm">
          <div className="mb-6 flex justify-center">
            <NobleLogo showText={false} />
          </div>

          <h1 className="mb-2 text-xl font-bold text-gray-900">
            Site temporarily unavailable
          </h1>

          <p className="text-sm leading-6 text-gray-500">
            We couldn't reach our services. Please try again shortly, or contact
            us directly if this persists.
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
      href: "/",
      label: "Home",
    },
    {
      href: "/services",
      label: "Services",
    },
    {
      href: "/about",
      label: "About Us",
    },
    {
      href: "/contact",
      label: "Contact",
    },
    {
      href: "/track",
      label: "Track Application",
    },
  ];

  const isActive = (href: string) => pathname === href;

  const primary = tenant.primaryColor || BRAND_NAVY;

  return (
    <TenantCtx.Provider value={tenant}>
      <ToastContainer />

      <div className="min-h-screen bg-white font-sans">
        {/* ====================================================
            TOP UTILITY BAR
            ==================================================== */}

        <div
          className="border-b border-white/10 px-4 py-2 text-xs text-white/80"
          style={{
            backgroundColor: BRAND_NAVY_DARK,
          }}
        >
          <div className="mx-auto flex max-w-7xl items-center justify-between">
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

            <div className="flex items-center gap-1.5 text-white/60">
              <IconShield />

              <span className="hidden sm:inline">
                Licensed &amp; regulated financial institution
              </span>

              <span className="sm:hidden">Regulated institution</span>
            </div>
          </div>
        </div>

        {/* ====================================================
            MAIN NAVIGATION
            ==================================================== */}

        <nav className="sticky top-0 z-50 border-b border-gray-200 bg-white/95 shadow-sm backdrop-blur">
          <div className="mx-auto flex max-w-7xl items-center justify-between px-4 py-3">
            {/* BRAND */}

            <Link
              href="/"
              className="flex items-center"
              aria-label="Noble Loan Solutions home"
            >
              <NobleLogo />
            </Link>

            {/* DESKTOP NAV */}

            <div className="hidden items-center gap-1 md:flex">
              {navLinks.map((link) => {
                const active = isActive(link.href);

                return (
                  <Link
                    key={link.href}
                    href={link.href}
                    className={`
                      relative
                      px-4
                      py-2.5
                      text-sm
                      font-semibold
                      transition-colors
                      ${
                        active
                          ? "text-[#0D2C54]"
                          : "text-gray-600 hover:text-[#0D2C54]"
                      }
                    `}
                  >
                    {link.label}

                    {active && (
                      <span
                        className="absolute bottom-0 left-4 right-4 h-0.5 rounded-full"
                        style={{
                          backgroundColor: BRAND_GOLD,
                        }}
                      />
                    )}
                  </Link>
                );
              })}

              {/* STAFF LOGIN */}

              <Link
                href="/login"
                className="
                  ml-3
                  rounded-lg
                  border
                  border-gray-300
                  px-4
                  py-2.5
                  text-sm
                  font-semibold
                  text-gray-700
                  transition
                  hover:border-[#0D2C54]
                  hover:bg-gray-50
                  hover:text-[#0D2C54]
                "
              >
                Staff Login
              </Link>

              {/* APPLY */}

              <Link
                href="/apply"
                className="
                  ml-1
                  rounded-lg
                  px-5
                  py-2.5
                  text-sm
                  font-bold
                  text-white
                  shadow-sm
                  transition
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

            {/* MOBILE MENU BUTTON */}

            <button
              type="button"
              className="
                rounded-lg
                p-2
                transition
                hover:bg-gray-100
                md:hidden
              "
              onClick={() => setMenuOpen((previous) => !previous)}
              aria-label="Toggle menu"
              aria-expanded={menuOpen}
            >
              <div className="my-1 h-0.5 w-6 bg-gray-700" />
              <div className="my-1 h-0.5 w-6 bg-gray-700" />
              <div className="my-1 h-0.5 w-6 bg-gray-700" />
            </button>
          </div>

          {/* ==================================================
              MOBILE MENU
              ================================================== */}

          {menuOpen && (
            <div className="border-t border-gray-100 bg-white px-4 py-4 shadow-lg md:hidden">
              <div className="space-y-1">
                {navLinks.map((link) => {
                  const active = isActive(link.href);

                  return (
                    <Link
                      key={link.href}
                      href={link.href}
                      onClick={() => setMenuOpen(false)}
                      className={`
                        block
                        rounded-lg
                        px-4
                        py-3
                        text-sm
                        font-semibold
                        transition
                        ${
                          active
                            ? "bg-gray-50 text-[#0D2C54]"
                            : "text-gray-700 hover:bg-gray-50"
                        }
                      `}
                    >
                      {link.label}
                    </Link>
                  );
                })}
              </div>

              <div className="mt-3 grid grid-cols-2 gap-2">
                <Link
                  href="/login"
                  onClick={() => setMenuOpen(false)}
                  className="
                    rounded-lg
                    border
                    border-gray-300
                    px-4
                    py-3
                    text-center
                    text-sm
                    font-semibold
                    text-gray-700
                  "
                >
                  Staff Login
                </Link>

                <Link
                  href="/apply"
                  onClick={() => setMenuOpen(false)}
                  className="
                    rounded-lg
                    px-4
                    py-3
                    text-center
                    text-sm
                    font-bold
                    text-white
                  "
                  style={{
                    backgroundColor: primary,
                  }}
                >
                  Apply Now
                </Link>
              </div>
            </div>
          )}
        </nav>

        {/* ====================================================
            PAGE CONTENT
            ==================================================== */}

        <main>{children}</main>

        {/* ====================================================
            FOOTER
            ==================================================== */}

        <footer
          className="mt-16 text-white"
          style={{
            backgroundColor: BRAND_NAVY_DARK,
          }}
        >
          <div className="mx-auto grid max-w-7xl grid-cols-1 gap-10 px-4 py-14 md:grid-cols-4">
            {/* BRAND */}

            <div className="md:col-span-2">
              <div className="mb-5">
                <NobleLogo />
              </div>

              <div className="mb-5 max-w-md text-sm leading-7 text-white/60">
                {tenant.mission ||
                  "Reliable financial support designed to help individuals and businesses move forward with confidence."}
              </div>

              <div className="space-y-2 text-sm text-white/50">
                {tenant.address && <div>{tenant.address}</div>}

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

            {/* QUICK LINKS */}

            <div>
              <div
                className="
                  mb-4
                  text-xs
                  font-bold
                  uppercase
                  tracking-[0.16em]
                  text-white/90
                "
              >
                Quick Links
              </div>

              <div className="space-y-3 text-sm text-white/60">
                {navLinks.map((link) => (
                  <Link
                    key={link.href}
                    href={link.href}
                    className="
                      block
                      transition
                      hover:text-white
                    "
                  >
                    {link.label}
                  </Link>
                ))}
              </div>
            </div>

            {/* SERVICES */}

            <div>
              <div
                className="
                  mb-4
                  text-xs
                  font-bold
                  uppercase
                  tracking-[0.16em]
                  text-white/90
                "
              >
                Our Services
              </div>

              <div className="space-y-3 text-sm text-white/60">
                {tenant.services?.slice(0, 5).map((service) => (
                  <div key={service.title}>{service.title}</div>
                ))}
              </div>
            </div>
          </div>

          {/* FOOTER BOTTOM */}

          <div className="border-t border-white/10 px-4 py-5">
            <div className="mx-auto flex max-w-7xl flex-col items-center justify-between gap-3 text-xs text-white/40 md:flex-row">
              <span>
                © {new Date().getFullYear()} {tenant.name}. All rights reserved.
                {tenant.registrationNumber
                  ? ` Reg. No. ${tenant.registrationNumber}`
                  : ""}
              </span>

              <span className="flex items-center gap-5">
                <Link href="/terms" className="transition hover:text-white/70">
                  Terms &amp; Conditions
                </Link>

                <Link
                  href="/privacy"
                  className="transition hover:text-white/70"
                >
                  Privacy Policy
                </Link>
              </span>

              <span className="text-center md:text-right">
                Your deposits and data are protected in line with applicable
                financial regulations.
              </span>
            </div>
          </div>
        </footer>
      </div>
    </TenantCtx.Provider>
  );
}
