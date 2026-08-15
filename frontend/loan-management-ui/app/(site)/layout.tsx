"use client";

import { createContext, useContext, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { OfflineProvider } from "../../components/OfflineProvider";
import { ToastContainer } from "../../components/ui/ToastContainer";
import { publicApi } from "../../services/api";

export interface TenantService {
  title: string;
  description?: string;
  icon?: string;
  rate?: string;
  rateType?: string;
  maxAmount?: string;
  term?: string;
}

export interface TenantStat {
  icon?: string;
  value: string;
  label: string;
}

export interface TenantTestimonial {
  name: string;
  role?: string;
  text: string;
  rating?: number;
}

export interface TenantTeamMember {
  name: string;
  role?: string;
  initials?: string;
}

export interface TenantConfig {
  id?: number;
  name: string;
  slug: string;
  country?: string;
  currency?: string;
  primaryColor: string;
  accentColor: string;
  logoUrl?: string | null;
  contactEmail?: string | null;
  contactPhone?: string | null;
  website?: string | null;
  address?: string | null;
  tagline?: string | null;
  mission?: string | null;
  vision?: string | null;
  founded?: string | null;
  registrationNumber?: string | null;
  status?: string | null;
  mapUrl?: string | null;
  minLoanAmount?: number | string | null;
  maxLoanAmount?: number | string | null;
  monthlyInterestRate?: number | string | null;
  monthlyManagementFeeRate?: number | string | null;
  processingFeeRate?: number | string | null;
  paymentMethods?: string[];
  socialMedia?: {
    facebook?: string;
    instagram?: string;
    linkedin?: string;
    twitter?: string;
    whatsapp?: string;
  };
  services?: TenantService[];
  hero?: {
    headline?: string;
    subtext?: string;
  };
  stats?: TenantStat[];
  testimonials?: TenantTestimonial[];
  team?: TenantTeamMember[];
}

const TenantCtx = createContext<TenantConfig | null>(null);

export const useTenant = () => useContext(TenantCtx);

const FALLBACK_PRIMARY = "#0D2C54";
const FALLBACK_ACCENT = "#D4AF37";

function safeHex(value: unknown, fallback: string): string {
  const candidate = typeof value === "string" ? value.trim() : "";
  return /^#[0-9a-fA-F]{6}$/.test(candidate) ? candidate : fallback;
}

function tenantInitials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return "L";
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase();
}

function BrandMark({ tenant }: { tenant: TenantConfig; compact?: boolean }) {
  const primary = safeHex(tenant.primaryColor, FALLBACK_PRIMARY);
  const accent = safeHex(tenant.accentColor, FALLBACK_ACCENT);
  const initials = tenantInitials(tenant.name);

  if (tenant.logoUrl) {
    return (
      <img
        src={tenant.logoUrl}
        alt={`${tenant.name} logo`}
        className="h-12 w-auto max-w-[220px] object-contain"
      />
    );
  }

  return (
    <div className="flex items-center gap-3" aria-label={`${tenant.name} home`}>
      <div
        className="flex h-11 w-11 items-center justify-center rounded-xl border-2 text-sm font-black shadow-sm"
        style={{ borderColor: accent, color: accent, backgroundColor: primary }}
      >
        {initials}
      </div>
      <div className="min-w-0 leading-none">
        <div
          className="truncate text-base font-black tracking-[0.05em]"
          style={{ color: primary }}
        >
          {tenant.name}
        </div>
        {tenant.tagline && (
          <div
            className="mt-1 truncate text-[10px] font-semibold uppercase tracking-[0.16em]"
            style={{ color: accent }}
          >
            {tenant.tagline}
          </div>
        )}
      </div>
    </div>
  );
}

function IconPhone() {
  return <span aria-hidden="true">☎</span>;
}

function IconMail() {
  return <span aria-hidden="true">✉</span>;
}

function IconShield() {
  return <span aria-hidden="true">◈</span>;
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
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;

    async function loadTenant() {
      setLoading(true);
      setError("");
      try {
        const raw = await publicApi.getCurrentTenant();
        if (cancelled) return;
        if (!raw || typeof raw !== "object")
          throw new Error("Tenant configuration is unavailable.");

        const data = raw as TenantConfig;
        const normalized: TenantConfig = {
          ...data,
          slug:
            typeof data.slug === "string" && data.slug.trim()
              ? data.slug.trim()
              : "",
          name:
            typeof data.name === "string" && data.name.trim()
              ? data.name.trim()
              : "",
          primaryColor: safeHex(data.primaryColor, FALLBACK_PRIMARY),
          accentColor: safeHex(data.accentColor, FALLBACK_ACCENT),
          services: Array.isArray(data.services) ? data.services : [],
          stats: Array.isArray(data.stats) ? data.stats : [],
          testimonials: Array.isArray(data.testimonials)
            ? data.testimonials
            : [],
          team: Array.isArray(data.team) ? data.team : [],
          paymentMethods: Array.isArray(data.paymentMethods)
            ? data.paymentMethods
            : [],
        };

        if (!normalized.name)
          throw new Error("Tenant has no public name configured.");
        setTenant(normalized);
      } catch (err) {
        if (!cancelled) {
          setTenant(null);
          setError(
            err instanceof Error ? err.message : "Unable to load the website.",
          );
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    void loadTenant();
    return () => {
      cancelled = true;
    };
  }, []);

  const navLinks = useMemo(
    () => [
      { href: "/", label: "Home" },
      { href: "/services", label: "Services" },
      { href: "/about", label: "About Us" },
      { href: "/contact", label: "Contact" },
      { href: "/track", label: "Track Application" },
    ],
    [],
  );

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-50 p-6">
        <div className="w-full max-w-md rounded-3xl border border-slate-200 bg-white p-10 text-center shadow-sm">
          <div className="mx-auto mb-5 h-12 w-12 animate-pulse rounded-2xl bg-slate-200" />
          <div className="mx-auto h-5 w-48 animate-pulse rounded bg-slate-200" />
          <div className="mx-auto mt-3 h-3 w-64 animate-pulse rounded bg-slate-100" />
        </div>
      </div>
    );
  }

  if (!tenant) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-50 p-6">
        <div className="w-full max-w-lg rounded-3xl border border-slate-200 bg-white p-10 text-center shadow-sm">
          <div className="mx-auto mb-5 flex h-14 w-14 items-center justify-center rounded-2xl bg-red-50 text-red-600">
            !
          </div>
          <h1 className="text-xl font-black text-slate-900">
            Website temporarily unavailable
          </h1>
          <p className="mt-2 text-sm leading-6 text-slate-500">
            The lender website configuration could not be loaded.
          </p>
          {error && <p className="mt-3 text-xs text-slate-400">{error}</p>}
          <button
            type="button"
            className="mt-6 rounded-xl bg-slate-900 px-5 py-3 text-sm font-bold text-white"
            onClick={() => window.location.reload()}
          >
            Retry
          </button>
        </div>
      </div>
    );
  }

  const primary = safeHex(tenant.primaryColor, FALLBACK_PRIMARY);
  const accent = safeHex(tenant.accentColor, FALLBACK_ACCENT);
  const country = tenant.country?.trim() || "your market";
  const activePath = pathname || "/";

  return (
    <TenantCtx.Provider value={tenant}>
      <OfflineProvider authHeader={() => ({})} />
      <ToastContainer />
      <div className="min-h-screen bg-white font-sans text-slate-900">
        <div
          className="border-b border-white/10 px-4 py-2 text-xs text-white/80"
          style={{ backgroundColor: "#071B35" }}
        >
          <div className="mx-auto flex max-w-7xl items-center justify-between gap-4">
            <div className="flex min-w-0 items-center gap-5">
              {tenant.contactPhone && (
                <a
                  href={`tel:${tenant.contactPhone}`}
                  className="flex items-center gap-1.5 truncate hover:text-white"
                >
                  <IconPhone /> {tenant.contactPhone}
                </a>
              )}
              {tenant.contactEmail && (
                <a
                  href={`mailto:${tenant.contactEmail}`}
                  className="hidden items-center gap-1.5 truncate hover:text-white sm:flex"
                >
                  <IconMail /> {tenant.contactEmail}
                </a>
              )}
            </div>
            <div className="flex items-center gap-1.5 whitespace-nowrap text-white/60">
              <IconShield />
              <span className="hidden sm:inline">Official lender website</span>
              <span className="sm:hidden">Official website</span>
            </div>
          </div>
        </div>

        <nav className="sticky top-0 z-50 border-b border-slate-200 bg-white/95 shadow-sm backdrop-blur">
          <div className="mx-auto flex max-w-7xl items-center justify-between px-4 py-3">
            <Link
              href="/"
              aria-label={`${tenant.name} home`}
              onClick={() => setMenuOpen(false)}
            >
              <BrandMark tenant={tenant} />
            </Link>

            <div className="hidden items-center gap-1 md:flex">
              {navLinks.map((link) => {
                const active = activePath === link.href;
                return (
                  <Link
                    key={link.href}
                    href={link.href}
                    className="relative px-4 py-2.5 text-sm font-semibold transition-colors"
                    style={{ color: active ? primary : undefined }}
                  >
                    <span
                      className={
                        active
                          ? "font-bold"
                          : "text-slate-600 hover:text-slate-900"
                      }
                    >
                      {link.label}
                    </span>
                    {active && (
                      <span
                        className="absolute inset-x-4 bottom-0 h-0.5 rounded-full"
                        style={{ backgroundColor: accent }}
                      />
                    )}
                  </Link>
                );
              })}
              <Link
                href="/login"
                className="ml-2 rounded-xl border border-slate-300 px-4 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-50"
              >
                Staff Login
              </Link>
              <Link
                href="/apply"
                className="ml-1 rounded-xl px-5 py-2.5 text-sm font-bold text-white shadow-sm hover:-translate-y-0.5 hover:shadow-md"
                style={{ backgroundColor: primary }}
              >
                Apply Now
              </Link>
            </div>

            <button
              type="button"
              className="rounded-xl p-2 hover:bg-slate-100 md:hidden"
              onClick={() => setMenuOpen((v) => !v)}
              aria-label="Toggle navigation menu"
              aria-expanded={menuOpen}
            >
              <span className="block h-0.5 w-6 bg-slate-700" />
              <span className="mt-1.5 block h-0.5 w-6 bg-slate-700" />
              <span className="mt-1.5 block h-0.5 w-6 bg-slate-700" />
            </button>
          </div>

          {menuOpen && (
            <div className="border-t border-slate-100 bg-white px-4 py-4 shadow-lg md:hidden">
              <div className="space-y-1">
                {navLinks.map((link) => (
                  <Link
                    key={link.href}
                    href={link.href}
                    onClick={() => setMenuOpen(false)}
                    className="block rounded-xl px-4 py-3 text-sm font-semibold hover:bg-slate-50"
                    style={{
                      color: activePath === link.href ? primary : undefined,
                    }}
                  >
                    {link.label}
                  </Link>
                ))}
              </div>
              <div className="mt-3 grid grid-cols-2 gap-2">
                <Link
                  href="/login"
                  onClick={() => setMenuOpen(false)}
                  className="rounded-xl border border-slate-300 px-4 py-3 text-center text-sm font-semibold text-slate-700"
                >
                  Staff Login
                </Link>
                <Link
                  href="/apply"
                  onClick={() => setMenuOpen(false)}
                  className="rounded-xl px-4 py-3 text-center text-sm font-bold text-white"
                  style={{ backgroundColor: primary }}
                >
                  Apply Now
                </Link>
              </div>
            </div>
          )}
        </nav>

        <main>{children}</main>

        <footer
          className="mt-16 text-white"
          style={{ backgroundColor: "#071B35" }}
        >
          <div className="mx-auto grid max-w-7xl grid-cols-1 gap-10 px-4 py-14 md:grid-cols-4">
            <div className="md:col-span-2">
              <div className="mb-5">
                <BrandMark tenant={tenant} />
              </div>
              <p className="mb-5 max-w-md text-sm leading-7 text-white/65">
                {tenant.mission ||
                  tenant.tagline ||
                  `Financial services for individuals and businesses in ${country}.`}
              </p>
              <div className="space-y-2 text-sm text-white/55">
                {tenant.address && <div>{tenant.address}</div>}
                {tenant.contactPhone && (
                  <a
                    className="flex items-center gap-2 hover:text-white"
                    href={`tel:${tenant.contactPhone}`}
                  >
                    <IconPhone />
                    {tenant.contactPhone}
                  </a>
                )}
                {tenant.contactEmail && (
                  <a
                    className="flex items-center gap-2 hover:text-white"
                    href={`mailto:${tenant.contactEmail}`}
                  >
                    <IconMail />
                    {tenant.contactEmail}
                  </a>
                )}
                {tenant.registrationNumber && (
                  <div>Registration: {tenant.registrationNumber}</div>
                )}
              </div>
            </div>

            <div>
              <div className="mb-4 text-xs font-bold uppercase tracking-[0.16em] text-white/90">
                Quick Links
              </div>
              <div className="space-y-3 text-sm text-white/60">
                {navLinks.map((link) => (
                  <Link
                    key={link.href}
                    href={link.href}
                    className="block hover:text-white"
                  >
                    {link.label}
                  </Link>
                ))}
                <Link href="/privacy" className="block hover:text-white">
                  Privacy Policy
                </Link>
                <Link href="/terms" className="block hover:text-white">
                  Terms & Conditions
                </Link>
              </div>
            </div>

            <div>
              <div className="mb-4 text-xs font-bold uppercase tracking-[0.16em] text-white/90">
                Our Services
              </div>
              <div className="space-y-3 text-sm text-white/60">
                {(tenant.services ?? []).slice(0, 6).map((service) => (
                  <div key={service.title}>{service.title}</div>
                ))}
              </div>
            </div>
          </div>
          <div className="border-t border-white/10 px-4 py-5">
            <div className="mx-auto flex max-w-7xl flex-col items-center justify-between gap-3 text-xs text-white/40 md:flex-row">
              <span>
                © {new Date().getFullYear()} {tenant.name}. All rights reserved.
              </span>
              <span>
                {tenant.currency || ""} • {country}
              </span>
            </div>
          </div>
        </footer>
      </div>
    </TenantCtx.Provider>
  );
}
