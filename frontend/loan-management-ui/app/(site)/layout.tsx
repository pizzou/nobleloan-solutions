"use client";

import { createContext, useContext, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { OfflineProvider } from "../../components/OfflineProvider";
import { ToastContainer } from "../../components/ui/ToastContainer";
import { TENANT_SLUG } from "../../lib/tenant";

export interface TenantService {
  title: string;
  description: string;
  icon?: string;
  rate?: string | number;
  rateType?: string;
  managementFeeRate?: string | number;
  processingFeeRate?: string | number;
  minAmount?: string | number;
  maxAmount?: string | number;
  minTermMonths?: number;
  maxTermMonths?: number;
  term: string;
}

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
  services?: TenantService[];
  hero?: { headline: string; subtext: string };
  stats?: { icon?: string; value: string; label: string }[];
  testimonials?: {
    name: string;
    role: string;
    text: string;
    rating?: number;
  }[];
  team?: { name: string; role: string; initials: string }[];
}

const TenantCtx = createContext<TenantConfig | null>(null);
export const useTenant = () => useContext(TenantCtx);

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api";
const NAVY = "#071B35";
const FALLBACK_TENANT: TenantConfig = {
  name: "Financial Services",
  slug: TENANT_SLUG,
  country: "",
  currency: "RWF",
  primaryColor: "#0D2C54",
  accentColor: "#C8A84E",
  services: [],
};

function Mark({
  tenant,
  compact = false,
}: {
  tenant: TenantConfig;
  compact?: boolean;
}) {
  if (tenant.logoUrl) {
    return (
      <div className="flex items-center gap-3">
        <img
          src={tenant.logoUrl}
          alt={tenant.name}
          className="h-10 w-auto object-contain"
        />
        {!compact && (
          <div className="hidden sm:block text-sm font-black tracking-tight text-slate-950">
            {tenant.name}
          </div>
        )}
      </div>
    );
  }
  return (
    <div className="flex items-center gap-3">
      <div
        className="flex h-10 w-10 items-center justify-center rounded-xl text-white shadow-sm"
        style={{
          background: `linear-gradient(145deg, ${tenant.primaryColor}, ${NAVY})`,
        }}
      >
        <span className="text-base font-black">
          {tenant.name.slice(0, 1).toUpperCase()}
        </span>
      </div>
      {!compact && (
        <div className="min-w-0">
          <div className="truncate text-[15px] font-black tracking-tight text-slate-950">
            {tenant.name}
          </div>
          <div className="text-[9px] font-bold uppercase tracking-[0.2em] text-slate-400">
            Financial services
          </div>
        </div>
      )}
    </div>
  );
}

function ShieldIcon() {
  return (
    <svg
      width="15"
      height="15"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.9"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z" />
      <path d="m9 12 2 2 4-4" />
    </svg>
  );
}
function PhoneIcon() {
  return (
    <svg
      width="14"
      height="14"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M22 16.9v3a2 2 0 0 1-2.2 2 19.8 19.8 0 0 1-8.6-3.1 19.4 19.4 0 0 1-6-6A19.8 19.8 0 0 1 2.1 4.2 2 2 0 0 1 4.1 2h3a2 2 0 0 1 2 1.7c.1.9.3 1.8.7 2.6a2 2 0 0 1-.5 2.1L8.1 9.5a16 16 0 0 0 6.4 6.4l1.1-1.2a2 2 0 0 1 2.1-.5c.8.4 1.7.6 2.6.7a2 2 0 0 1 1.7 2Z" />
    </svg>
  );
}
function MailIcon() {
  return (
    <svg
      width="14"
      height="14"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <rect x="2" y="4" width="20" height="16" rx="2" />
      <path d="m22 6-10 7L2 6" />
    </svg>
  );
}

export default function SiteLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const pathname = usePathname();
  const [tenant, setTenant] = useState<TenantConfig | null>(null);
  const [loading, setLoading] = useState(true);
  const [menuOpen, setMenuOpen] = useState(false);

  const slug = useMemo(() => TENANT_SLUG, []);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      try {
        const response = await fetch(
          `${API_BASE}/public/tenant/${encodeURIComponent(slug)}`,
          { cache: "no-store" },
        );
        if (!response.ok) throw new Error("Tenant request failed");
        const json = await response.json();
        const data = json?.data;
        if (!data || json?.success === false)
          throw new Error("Tenant not found");
        if (!cancelled) setTenant({ ...FALLBACK_TENANT, ...data, slug });
      } catch {
        if (!cancelled) setTenant(FALLBACK_TENANT);
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    void load();
    return () => {
      cancelled = true;
    };
  }, [slug]);

  if (loading || !tenant) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-50">
        <div className="flex flex-col items-center gap-4">
          <Mark tenant={FALLBACK_TENANT} compact />
          <div className="h-8 w-8 animate-spin rounded-full border-2 border-slate-200 border-t-slate-700" />
          <div className="text-xs font-semibold text-slate-400">
            Loading secure website…
          </div>
        </div>
      </div>
    );
  }

  const navLinks = [
    ["/", "Home"],
    ["/services", "Solutions"],
    ["/about", "About"],
    ["/contact", "Contact"],
    ["/track", "Track application"],
  ];
  const primary = tenant.primaryColor || "#0D2C54";

  return (
    <TenantCtx.Provider value={tenant}>
      <OfflineProvider authHeader={() => ({})} />
      <ToastContainer />
      <div className="min-h-screen bg-[#F7F9FC] text-slate-900">
        <div className="border-b border-white/10 bg-[#06172D] text-white/75">
          <div className="mx-auto flex max-w-7xl flex-col gap-2 px-4 py-2 text-[11px] font-medium sm:flex-row sm:items-center sm:justify-between">
            <div className="flex items-center gap-4">
              {tenant.contactPhone && (
                <span className="inline-flex items-center gap-1.5">
                  <PhoneIcon />
                  {tenant.contactPhone}
                </span>
              )}
              {tenant.contactEmail && (
                <span className="hidden items-center gap-1.5 sm:inline-flex">
                  <MailIcon />
                  {tenant.contactEmail}
                </span>
              )}
            </div>
            <div className="inline-flex items-center gap-1.5">
              <ShieldIcon />
              Secure digital lending platform
            </div>
          </div>
        </div>

        <header className="sticky top-0 z-50 border-b border-slate-200/80 bg-white/90 backdrop-blur-xl">
          <div className="mx-auto flex max-w-7xl items-center justify-between gap-5 px-4 py-3.5">
            <Link
              href="/"
              aria-label={`${tenant.name} home`}
              onClick={() => setMenuOpen(false)}
            >
              <Mark tenant={tenant} />
            </Link>

            <nav className="hidden items-center gap-1 lg:flex">
              {navLinks.map(([href, label]) => {
                const active =
                  pathname === href ||
                  (href !== "/" && pathname.startsWith(href));
                return (
                  <Link
                    key={href}
                    href={href}
                    className="rounded-xl px-4 py-2.5 text-sm font-semibold transition"
                    style={
                      active
                        ? { backgroundColor: `${primary}0F`, color: primary }
                        : { color: "#475569" }
                    }
                  >
                    {label}
                  </Link>
                );
              })}
              <Link
                href="/login"
                className="ml-2 rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-bold text-slate-700 transition hover:border-slate-300 hover:bg-slate-50"
              >
                Sign in
              </Link>
              <Link
                href="/apply"
                className="ml-1 rounded-xl px-5 py-2.5 text-sm font-black text-white shadow-sm transition hover:-translate-y-0.5"
                style={{ backgroundColor: primary }}
              >
                Apply now
              </Link>
            </nav>

            <button
              type="button"
              onClick={() => setMenuOpen((open) => !open)}
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
            <div className="border-t border-slate-100 bg-white px-4 py-4 lg:hidden">
              <div className="space-y-1">
                {navLinks.map(([href, label]) => (
                  <Link
                    key={href}
                    href={href}
                    onClick={() => setMenuOpen(false)}
                    className="block rounded-xl px-4 py-3 text-sm font-semibold text-slate-700 hover:bg-slate-50"
                  >
                    {label}
                  </Link>
                ))}
              </div>
              <div className="mt-3 grid grid-cols-2 gap-2">
                <Link
                  href="/login"
                  onClick={() => setMenuOpen(false)}
                  className="rounded-xl border border-slate-200 px-4 py-3 text-center text-sm font-bold text-slate-700"
                >
                  Sign in
                </Link>
                <Link
                  href="/apply"
                  onClick={() => setMenuOpen(false)}
                  className="rounded-xl px-4 py-3 text-center text-sm font-black text-white"
                  style={{ backgroundColor: primary }}
                >
                  Apply now
                </Link>
              </div>
            </div>
          )}
        </header>

        <main>{children}</main>

        <footer className="mt-16 bg-[#06172D] text-white">
          <div className="mx-auto grid max-w-7xl gap-10 px-4 py-14 md:grid-cols-[1.4fr_.8fr_.8fr]">
            <div>
              <Mark tenant={tenant} />
              <p className="mt-5 max-w-lg text-sm leading-7 text-white/55">
                {tenant.mission ||
                  "Digital financial services designed around clarity, responsible lending and long-term customer relationships."}
              </p>
              <div className="mt-5 space-y-2 text-sm text-white/55">
                {tenant.address && <div>{tenant.address}</div>}
                {tenant.contactPhone && (
                  <div className="flex items-center gap-2">
                    <PhoneIcon />
                    {tenant.contactPhone}
                  </div>
                )}
                {tenant.contactEmail && (
                  <div className="flex items-center gap-2">
                    <MailIcon />
                    {tenant.contactEmail}
                  </div>
                )}
              </div>
            </div>
            <div>
              <div className="text-xs font-bold uppercase tracking-[0.18em] text-white/75">
                Company
              </div>
              <div className="mt-4 space-y-3 text-sm text-white/55">
                <Link className="block hover:text-white" href="/about">
                  About
                </Link>
                <Link className="block hover:text-white" href="/services">
                  Solutions
                </Link>
                <Link className="block hover:text-white" href="/contact">
                  Contact
                </Link>
                <Link className="block hover:text-white" href="/track">
                  Track application
                </Link>
              </div>
            </div>
            <div>
              <div className="text-xs font-bold uppercase tracking-[0.18em] text-white/75">
                Policies
              </div>
              <div className="mt-4 space-y-3 text-sm text-white/55">
                <Link className="block hover:text-white" href="/privacy">
                  Privacy
                </Link>
                <Link className="block hover:text-white" href="/terms">
                  Terms
                </Link>
                <Link className="block hover:text-white" href="/login">
                  Staff sign in
                </Link>
              </div>
            </div>
          </div>
          <div className="border-t border-white/10 px-4 py-5 text-xs text-white/35">
            <div className="mx-auto flex max-w-7xl flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
              <span>
                © {new Date().getFullYear()} {tenant.name}. All rights reserved.
              </span>
              <span>
                {tenant.registrationNumber
                  ? `Registration ${tenant.registrationNumber}`
                  : "Financial services platform"}
              </span>
            </div>
          </div>
        </footer>
      </div>
    </TenantCtx.Provider>
  );
}
