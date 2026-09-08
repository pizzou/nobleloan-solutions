"use client";

import { createContext, useContext, useEffect, useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { ToastContainer } from "../../components/ui/ToastContainer";
import { TENANT_SLUG } from "../../lib/tenant";

export interface TenantConfig {
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
  monthlyInterestRate?: string | number;
  monthlyManagementFeeRate?: string | number;
  applicationFeeRate?: string | number;
  services?: {
    title: string;
    description: string;
    icon: string;
    loanType?: string;
    rate: string | number;
    rateType?: string;
    interestRate?: string | number;
    applicationFeeRate?: string | number;
    managementFeeRate?: string | number;
    minAmount?: string | number;
    maxAmount?: string | number | null;
    minTermMonths?: number;
    maxTermMonths?: number;
    term: string;
  }[];
  hero?: { headline: string; subtext: string };
  stats?: { icon: string; value: string; label: string }[];
  testimonials?: { name: string; role: string; text: string; rating: number }[];
  team?: { name: string; role: string; initials: string }[];
}

const TenantCtx = createContext<TenantConfig | null>(null);
export const useTenant = () => useContext(TenantCtx);

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "/api";
const NAVY = "#0B1F3A";
const NAVY_DEEP = "#061326";
const GOLD = "#D4AF37";

const FALLBACK_TENANT: TenantConfig = {
  name: "Noble Loan Solutions",
  slug: TENANT_SLUG,
  country: "Rwanda",
  currency: "RWF",
  primaryColor: NAVY,
  accentColor: GOLD,
  services: [],
};

/* eslint-disable @next/next/no-img-element */
function NobleLogo({ compact = false }: { compact?: boolean }) {
  return (
    <img
      src="/noble-loan-solutions-logo.svg"
      alt="Noble Loan Solutions — Financial Support Partner"
      className={
        compact
          ? "h-9 w-auto max-w-[210px] object-contain"
          : "h-11 w-auto max-w-[270px] object-contain"
      }
    />
  );
}

function Brand({
  tenant,
  compact = false,
}: {
  tenant: TenantConfig;
  compact?: boolean;
}) {
  if (tenant.logoUrl) {
    return (
      // eslint-disable-next-line @next/next/no-img-element
      <img
        src={tenant.logoUrl}
        alt={tenant.name}
        className={
          compact
            ? "h-9 w-auto max-w-[210px] object-contain"
            : "h-11 w-auto max-w-[270px] object-contain"
        }
      />
    );
  }

  return <NobleLogo compact={compact} />;
}

function PhoneIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      className="h-3.5 w-3.5"
    >
      <path d="M22 16.9v3a2 2 0 0 1-2.2 2A19.8 19.8 0 0 1 11.2 19a19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 2.1 4.3 2 2 0 0 1 4.1 2h3a2 2 0 0 1 2 1.7c.1 1 .4 1.9.7 2.8a2 2 0 0 1-.4 2.1L8.1 9.9a16 16 0 0 0 6 6l1.3-1.3a2 2 0 0 1 2.1-.4c.9.3 1.8.6 2.8.7a2 2 0 0 1 1.7 2Z" />
    </svg>
  );
}
function MailIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      className="h-3.5 w-3.5"
    >
      <rect x="3" y="5" width="18" height="14" rx="2" />
      <path d="m3 7 9 6 9-6" />
    </svg>
  );
}
function ShieldIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      className="h-3.5 w-3.5"
    >
      <path d="M12 3 20 6v6c0 5-3.5 8.5-8 10-4.5-1.5-8-5-8-10V6l8-3Z" />
      <path d="m9 12 2 2 4-4" />
    </svg>
  );
}

const navLinks = [
  { href: "/", label: "Home" },
  { href: "/services", label: "Solutions" },
  { href: "/about", label: "About" },
  { href: "/contact", label: "Contact" },
  { href: "/track", label: "Track application" },
];

export default function SiteLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const pathname = usePathname();
  const [tenant, setTenant] = useState<TenantConfig | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [serviceError, setServiceError] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setNotFound(false);
    setServiceError(false);

    fetch(`${API_BASE}/public/tenant/${encodeURIComponent(TENANT_SLUG)}`, {
      credentials: "include",
      headers: { Accept: "application/json" },
      cache: "no-store",
    })
      .then(async (response) => {
        if (response.status === 404) {
          if (!cancelled) setNotFound(true);
          return null;
        }
        if (!response.ok)
          throw new Error(`Tenant service unavailable: ${response.status}`);
        return response.json();
      })
      .then((result) => {
        if (cancelled || result == null) return;
        if (!result?.data || result?.success === false) {
          setServiceError(true);
          return;
        }
        setTenant({ ...FALLBACK_TENANT, ...result.data, slug: TENANT_SLUG });
      })
      .catch(() => {
        if (!cancelled) setServiceError(true);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    setMenuOpen(false);
  }, [pathname]);

  if (loading) {
    return (
      <div
        className="min-h-screen bg-[#F4F7FB]"
        style={{
          backgroundImage:
            "radial-gradient(circle at 50% 0%, rgba(212,175,55,.10), transparent 34rem)",
        }}
      >
        <div className="flex min-h-screen items-center justify-center px-6">
          <div className="text-center">
            <NobleLogo />
            <div className="mx-auto mt-6 h-1 w-32 overflow-hidden rounded-full bg-slate-200">
              <div className="h-full w-1/2 animate-pulse rounded-full bg-[#D4AF37]" />
            </div>
            <p className="mt-4 text-[10px] font-black uppercase tracking-[.22em] text-slate-400">
              Preparing your secure experience
            </p>
          </div>
        </div>
      </div>
    );
  }

  if (notFound || serviceError || !tenant) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-[#F4F7FB] px-6">
        <div className="w-full max-w-md rounded-[28px] border border-slate-200 bg-white p-8 text-center shadow-[0_30px_90px_rgba(15,23,42,.10)]">
          <NobleLogo />
          <h1 className="mt-6 text-2xl font-black tracking-tight text-[#0B1F3A]">
            {notFound ? "Site not found" : "Service temporarily unavailable"}
          </h1>
          <p className="mt-3 text-sm leading-6 text-slate-500">
            {notFound
              ? "The requested financial institution site could not be found."
              : "We could not load the organization configuration. Please try again shortly."}
          </p>
          {!notFound && (
            <button
              onClick={() => window.location.reload()}
              className="mt-6 rounded-xl bg-[#0B1F3A] px-6 py-3 text-sm font-black text-white shadow-lg"
            >
              Try again
            </button>
          )}
        </div>
      </div>
    );
  }

  const primary = tenant.primaryColor || NAVY;
  const accent = tenant.accentColor || GOLD;

  return (
    <TenantCtx.Provider value={tenant}>
      <ToastContainer />
      <div
        className="min-h-screen bg-white"
        style={
          {
            "--brand-primary": primary,
            "--brand-accent": accent,
          } as React.CSSProperties
        }
      >
        <div className="bg-[#061326] text-white">
          <div className="mx-auto flex min-h-9 max-w-7xl items-center justify-between gap-4 px-5 text-[10px] font-semibold sm:px-8">
            <div className="flex items-center gap-5 text-white/65">
              {tenant.contactPhone && (
                <span className="hidden items-center gap-1.5 sm:flex">
                  <PhoneIcon />
                  {tenant.contactPhone}
                </span>
              )}
              {tenant.contactEmail && (
                <span className="hidden items-center gap-1.5 md:flex">
                  <MailIcon />
                  {tenant.contactEmail}
                </span>
              )}
            </div>
            <div className="flex items-center gap-1.5 text-white/65">
              <ShieldIcon />
              <span>Secure digital lending</span>
              <span className="text-white/20">•</span>
              <span>{tenant.country || "Rwanda"}</span>
            </div>
          </div>
        </div>

        <nav className="site-public-nav sticky top-0 z-50 border-b border-slate-200/80 bg-white/90 backdrop-blur-2xl">
          <div className="mx-auto flex h-[76px] max-w-7xl items-center justify-between px-5 sm:px-8">
            <Link
              href="/"
              aria-label={`${tenant.name} home`}
              className="shrink-0"
            >
              <Brand tenant={tenant} />
            </Link>
            <div className="hidden items-center gap-1 lg:flex">
              {navLinks.map((link) => {
                const active =
                  link.href === "/"
                    ? pathname === "/"
                    : pathname.startsWith(link.href);
                return (
                  <Link
                    key={link.href}
                    href={link.href}
                    className={`rounded-xl px-4 py-2.5 text-[13px] font-bold transition ${active ? "bg-slate-100 text-[#0B1F3A]" : "text-slate-500 hover:bg-slate-50 hover:text-[#0B1F3A]"}`}
                  >
                    {link.label}
                  </Link>
                );
              })}
              <span className="mx-2 h-7 w-px bg-slate-200" />
              <Link
                href="/login"
                className="rounded-xl px-4 py-2.5 text-[13px] font-bold text-slate-600 transition hover:bg-slate-50 hover:text-[#0B1F3A]"
              >
                Staff portal
              </Link>
              <Link
                href="/apply"
                className="ml-1 rounded-xl px-5 py-3 text-[13px] font-black text-white shadow-[0_10px_24px_rgba(11,31,58,.18)] transition hover:-translate-y-0.5"
                style={{ backgroundColor: primary }}
              >
                Apply now <span className="ml-1">→</span>
              </Link>
            </div>
            <button
              type="button"
              onClick={() => setMenuOpen((v) => !v)}
              className="rounded-xl border border-slate-200 p-2.5 lg:hidden"
              aria-label="Toggle navigation"
              aria-expanded={menuOpen}
            >
              <span className="block h-0.5 w-5 bg-slate-700" />
              <span className="mt-1.5 block h-0.5 w-5 bg-slate-700" />
              <span className="mt-1.5 block h-0.5 w-5 bg-slate-700" />
            </button>
          </div>
          {menuOpen && (
            <div className="border-t border-slate-200 bg-white px-5 py-4 shadow-xl lg:hidden">
              <div className="space-y-1">
                {navLinks.map((link) => (
                  <Link
                    key={link.href}
                    href={link.href}
                    className="block rounded-xl px-4 py-3 text-sm font-bold text-slate-700 hover:bg-slate-50"
                  >
                    {link.label}
                  </Link>
                ))}
              </div>
              <div className="mt-3 grid grid-cols-2 gap-2">
                <Link
                  href="/login"
                  className="rounded-xl border border-slate-200 px-4 py-3 text-center text-sm font-bold text-slate-700"
                >
                  Staff portal
                </Link>
                <Link
                  href="/apply"
                  className="rounded-xl px-4 py-3 text-center text-sm font-black text-white"
                  style={{ backgroundColor: primary }}
                >
                  Apply now
                </Link>
              </div>
            </div>
          )}
        </nav>

        <main>{children}</main>

        <footer className="mt-20 bg-[#061326] text-white">
          <div className="mx-auto max-w-7xl px-5 py-16 sm:px-8 lg:py-20">
            <div className="grid gap-12 lg:grid-cols-[1.5fr_.7fr_.7fr_.9fr]">
              <div>
                <div className="inline-flex rounded-2xl bg-white p-3">
                  <Brand tenant={tenant} compact />
                </div>
                <p className="mt-6 max-w-md text-sm leading-7 text-white/55">
                  {tenant.mission ||
                    tenant.tagline ||
                    "Responsible financial solutions with clear terms, secure digital journeys and human support."}
                </p>
                <div className="mt-6 space-y-2 text-xs text-white/45">
                  {tenant.address && <div>{tenant.address}</div>}
                  {tenant.contactPhone && <div>{tenant.contactPhone}</div>}
                  {tenant.contactEmail && <div>{tenant.contactEmail}</div>}
                </div>
              </div>
              <div>
                <div className="text-[10px] font-black uppercase tracking-[.2em] text-[#D4AF37]">
                  Navigate
                </div>
                <div className="mt-5 space-y-3 text-sm text-white/55">
                  {navLinks.slice(0, 4).map((l) => (
                    <Link
                      key={l.href}
                      href={l.href}
                      className="block transition hover:text-white"
                    >
                      {l.label}
                    </Link>
                  ))}
                  <Link
                    href="/track"
                    className="block transition hover:text-white"
                  >
                    Track application
                  </Link>
                </div>
              </div>
              <div>
                <div className="text-[10px] font-black uppercase tracking-[.2em] text-[#D4AF37]">
                  Solutions
                </div>
                <div className="mt-5 space-y-3 text-sm text-white/55">
                  {tenant.services?.slice(0, 5).map((s) => (
                    <div key={s.title}>{s.title}</div>
                  ))}
                </div>
              </div>
              <div>
                <div className="text-[10px] font-black uppercase tracking-[.2em] text-[#D4AF37]">
                  Client care
                </div>
                <p className="mt-5 text-sm leading-6 text-white/55">
                  Need help with an application or repayment? Our team is here
                  to guide you.
                </p>
                <Link
                  href="/contact"
                  className="mt-5 inline-flex rounded-xl border border-white/15 px-4 py-2.5 text-xs font-black text-white transition hover:bg-white/10"
                >
                  Contact our team →
                </Link>
              </div>
            </div>
            <div className="mt-14 flex flex-col gap-4 border-t border-white/10 pt-6 text-[10px] text-white/35 md:flex-row md:items-center md:justify-between">
              <span>
                © {new Date().getFullYear()} {tenant.name}. All rights reserved.
                {tenant.registrationNumber
                  ? ` Reg. No. ${tenant.registrationNumber}`
                  : ""}
              </span>
              <div className="flex gap-5">
                <Link href="/privacy" className="hover:text-white/70">
                  Privacy
                </Link>
                <Link href="/terms" className="hover:text-white/70">
                  Terms
                </Link>
              </div>
              <span>Secure financial services • Transparent by design</span>
            </div>
          </div>
        </footer>
      </div>
    </TenantCtx.Provider>
  );
}
