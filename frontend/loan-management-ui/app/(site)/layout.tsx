"use client";

import { createContext, useContext, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { OfflineProvider } from "../../components/OfflineProvider";
import { ToastContainer } from "../../components/ui/ToastContainer";
import { publicApi } from "../../services/api";
import { TENANT_SLUG } from "../../lib/tenant";

export interface TenantService {
  id?: number;
  title: string;
  description?: string;
  icon?: string;
  rate?: string;
  rateType?: string;
  monthlyInterestRate?: number | string | null;
  monthlyManagementFeeRate?: number | string | null;
  processingFeeRate?: number | string | null;
  minAmount?: number | string | null;
  maxAmount?: number | string | null;
  minTermMonths?: number | null;
  maxTermMonths?: number | null;
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

const FALLBACK_PRIMARY = "#0F1B3D";
const FALLBACK_ACCENT = "#C9A227";

function safeHex(value: unknown, fallback: string): string {
  const candidate = typeof value === "string" ? value.trim() : "";
  return /^#[0-9a-fA-F]{6}$/.test(candidate) ? candidate : fallback;
}

function tenantInitials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);

  if (parts.length === 0) return "NL";
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();

  return `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase();
}

function BrandMark({
  tenant,
  footer = false,
}: {
  tenant: TenantConfig;
  footer?: boolean;
}) {
  const primary = safeHex(tenant.primaryColor, FALLBACK_PRIMARY);
  const accent = safeHex(tenant.accentColor, FALLBACK_ACCENT);

  const normalizedName = tenant.name.toLowerCase();

  const isNoble =
    tenant.slug.toLowerCase() === "nobleloansolutions" ||
    normalizedName.includes("noble loan solutions");

  const logoSource =
    tenant.logoUrl || (isNoble ? "/noble-loan-solutions-logo.svg" : null);

  if (logoSource) {
    return (
      <img
        src={logoSource}
        alt={`${tenant.name} logo`}
        className={
          footer
            ? "h-12 w-auto max-w-[230px] object-contain brightness-0 invert"
            : "h-12 w-auto max-w-[230px] object-contain"
        }
      />
    );
  }

  return (
    <div className="flex items-center gap-3">
      <div
        className="flex h-11 w-11 items-center justify-center rounded-xl border-2 text-sm font-black"
        style={{
          borderColor: accent,
          color: accent,
          backgroundColor: primary,
        }}
      >
        {tenantInitials(tenant.name)}
      </div>

      <div className="min-w-0">
        <div
          className="truncate text-[15px] font-black tracking-[0.06em]"
          style={{ color: footer ? "#fff" : primary }}
        >
          {tenant.name}
        </div>

        {tenant.tagline && (
          <div
            className="mt-1 truncate text-[9px] font-bold uppercase tracking-[0.18em]"
            style={{ color: footer ? accent : accent }}
          >
            {tenant.tagline}
          </div>
        )}
      </div>
    </div>
  );
}

function PhoneIcon() {
  return <span aria-hidden="true">☎</span>;
}

function MailIcon() {
  return <span aria-hidden="true">✉</span>;
}

function ShieldIcon() {
  return <span aria-hidden="true">◆</span>;
}

function ArrowIcon() {
  return <span aria-hidden="true">→</span>;
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
        const raw = await publicApi.getTenant(TENANT_SLUG);

        if (cancelled) return;

        if (!raw || typeof raw !== "object") {
          throw new Error("Tenant configuration is unavailable.");
        }

        const data = raw as TenantConfig;

        const normalized: TenantConfig = {
          ...data,
          slug: TENANT_SLUG,
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

        if (!normalized.name) {
          throw new Error("Tenant has no public name configured.");
        }

        setTenant(normalized);
      } catch (err) {
        if (!cancelled) {
          setTenant(null);
          setError(
            err instanceof Error ? err.message : "Unable to load the website.",
          );
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
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
      { href: "/calculator", label: "Calculator" },
      { href: "/about", label: "About Us" },
      { href: "/contact", label: "Contact" },
      { href: "/track", label: "Track Application" },
    ],
    [],
  );

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-[#f7f8fa] p-6">
        <div className="w-full max-w-sm text-center">
          <div className="mx-auto h-14 w-14 animate-pulse rounded-2xl bg-slate-200" />
          <div className="mx-auto mt-6 h-5 w-40 animate-pulse rounded bg-slate-200" />
          <div className="mx-auto mt-3 h-3 w-64 animate-pulse rounded bg-slate-100" />
        </div>
      </div>
    );
  }

  if (!tenant) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-[#f7f8fa] p-6">
        <div className="w-full max-w-lg rounded-[2rem] border border-slate-200 bg-white p-10 text-center shadow-xl">
          <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-2xl bg-red-50 text-xl font-black text-red-600">
            !
          </div>

          <h1 className="mt-6 text-2xl font-black text-slate-950">
            Website temporarily unavailable
          </h1>

          <p className="mt-3 text-sm leading-7 text-slate-500">
            The lender website configuration could not be loaded. Please try
            again.
          </p>

          {error && (
            <p className="mt-4 rounded-xl bg-slate-50 p-3 text-xs text-slate-400">
              {error}
            </p>
          )}

          <button
            type="button"
            onClick={() => window.location.reload()}
            className="mt-6 rounded-xl bg-slate-950 px-6 py-3 text-sm font-black text-white transition hover:-translate-y-0.5 hover:shadow-lg"
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

      <div
        className="min-h-screen bg-white text-slate-900"
        style={
          {
            "--noble-primary": primary,
            "--noble-accent": accent,
          } as React.CSSProperties
        }
      >
        {/* Institutional utility bar */}
        <div
          className="border-b border-white/10 px-4 py-2.5 text-[11px] text-white/70"
          style={{
            backgroundColor: "#071426",
          }}
        >
          <div className="mx-auto flex max-w-7xl items-center justify-between gap-5">
            <div className="flex min-w-0 items-center gap-5">
              {tenant.contactPhone && (
                <a
                  href={`tel:${tenant.contactPhone}`}
                  className="flex items-center gap-2 truncate transition hover:text-white"
                >
                  <PhoneIcon />
                  {tenant.contactPhone}
                </a>
              )}

              {tenant.contactEmail && (
                <a
                  href={`mailto:${tenant.contactEmail}`}
                  className="hidden items-center gap-2 truncate transition hover:text-white sm:flex"
                >
                  <MailIcon />
                  {tenant.contactEmail}
                </a>
              )}
            </div>

            <div className="flex items-center gap-5 whitespace-nowrap">
              <span className="hidden md:inline">Professional lending</span>

              <span className="hidden lg:inline">Transparent terms</span>

              <span className="flex items-center gap-2">
                <ShieldIcon />
                Secure digital service
              </span>
            </div>
          </div>
        </div>

        {/* Main navigation */}
        <nav className="sticky top-0 z-50 border-b border-slate-200/80 bg-white/95 backdrop-blur-xl">
          <div className="mx-auto flex h-[76px] max-w-7xl items-center justify-between px-4">
            <Link
              href="/"
              aria-label={`${tenant.name} home`}
              onClick={() => setMenuOpen(false)}
              className="shrink-0"
            >
              <BrandMark tenant={tenant} />
            </Link>

            <div className="hidden items-center gap-1 lg:flex">
              {navLinks.map((link) => {
                const active =
                  link.href === "/"
                    ? activePath === "/"
                    : activePath === link.href ||
                      activePath.startsWith(`${link.href}/`);

                return (
                  <Link
                    key={link.href}
                    href={link.href}
                    className="group relative px-4 py-7 text-[13px] font-bold text-slate-600 transition hover:text-slate-950"
                    style={{
                      color: active ? primary : undefined,
                    }}
                  >
                    {link.label}

                    <span
                      className="absolute bottom-0 left-4 right-4 h-[2px] origin-left scale-x-0 transition-transform group-hover:scale-x-100"
                      style={{
                        backgroundColor: accent,
                        transform: active ? "scaleX(1)" : undefined,
                      }}
                    />
                  </Link>
                );
              })}
            </div>

            <div className="hidden items-center gap-2 lg:flex">
              <Link
                href="/login"
                className="rounded-xl border border-slate-200 px-4 py-3 text-[13px] font-bold text-slate-700 transition hover:border-slate-300 hover:bg-slate-50"
              >
                Staff Login
              </Link>

              <Link
                href="/apply"
                className="group inline-flex items-center gap-2 rounded-xl px-5 py-3 text-[13px] font-black text-white shadow-sm transition hover:-translate-y-0.5 hover:shadow-lg"
                style={{
                  backgroundColor: primary,
                }}
              >
                Start an Application
                <ArrowIcon />
              </Link>
            </div>

            <button
              type="button"
              className="rounded-xl border border-slate-200 p-3 lg:hidden"
              onClick={() => setMenuOpen((value) => !value)}
              aria-label="Toggle navigation"
              aria-expanded={menuOpen}
            >
              <span className="block h-0.5 w-6 bg-slate-800" />
              <span className="mt-1.5 block h-0.5 w-6 bg-slate-800" />
              <span className="mt-1.5 block h-0.5 w-6 bg-slate-800" />
            </button>
          </div>

          {menuOpen && (
            <div className="border-t border-slate-100 bg-white px-4 py-5 shadow-xl lg:hidden">
              <div className="space-y-1">
                {navLinks.map((link) => {
                  const active =
                    link.href === "/"
                      ? activePath === "/"
                      : activePath.startsWith(link.href);

                  return (
                    <Link
                      key={link.href}
                      href={link.href}
                      onClick={() => setMenuOpen(false)}
                      className="flex items-center justify-between rounded-xl px-4 py-3.5 text-sm font-bold transition hover:bg-slate-50"
                      style={{
                        color: active ? primary : undefined,
                      }}
                    >
                      {link.label}
                      <ArrowIcon />
                    </Link>
                  );
                })}
              </div>

              <div className="mt-4 grid grid-cols-2 gap-2">
                <Link
                  href="/login"
                  onClick={() => setMenuOpen(false)}
                  className="rounded-xl border border-slate-200 px-4 py-3 text-center text-sm font-bold text-slate-700"
                >
                  Staff Login
                </Link>

                <Link
                  href="/apply"
                  onClick={() => setMenuOpen(false)}
                  className="rounded-xl px-4 py-3 text-center text-sm font-black text-white"
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

        <main>{children}</main>

        {/* Footer */}
        <footer
          className="mt-20 text-white"
          style={{
            backgroundColor: "#071426",
          }}
        >
          <div className="mx-auto max-w-7xl px-4 py-16">
            <div className="grid gap-12 lg:grid-cols-[1.5fr_0.7fr_0.7fr_1fr]">
              <div>
                <BrandMark tenant={tenant} footer />

                <p className="mt-6 max-w-md text-sm leading-7 text-white/55">
                  {tenant.mission ||
                    tenant.tagline ||
                    `Professional financial services from ${tenant.name}.`}
                </p>

                <div className="mt-7 space-y-3 text-sm text-white/55">
                  {tenant.address && <div>{tenant.address}</div>}

                  {tenant.contactPhone && (
                    <a
                      href={`tel:${tenant.contactPhone}`}
                      className="flex items-center gap-2 transition hover:text-white"
                    >
                      <PhoneIcon />
                      {tenant.contactPhone}
                    </a>
                  )}

                  {tenant.contactEmail && (
                    <a
                      href={`mailto:${tenant.contactEmail}`}
                      className="flex items-center gap-2 transition hover:text-white"
                    >
                      <MailIcon />
                      {tenant.contactEmail}
                    </a>
                  )}

                  {tenant.registrationNumber && (
                    <div>Registration: {tenant.registrationNumber}</div>
                  )}
                </div>

                {tenant.socialMedia &&
                  Object.values(tenant.socialMedia).some(Boolean) && (
                    <div className="mt-7 flex flex-wrap gap-2">
                      {Object.entries(tenant.socialMedia)
                        .filter(([, value]) => Boolean(value))
                        .map(([label, value]) => (
                          <a
                            key={label}
                            href={value as string}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="rounded-full border border-white/10 px-3 py-2 text-[11px] font-bold capitalize text-white/55 transition hover:border-white/25 hover:text-white"
                          >
                            {label}
                          </a>
                        ))}
                    </div>
                  )}
              </div>

              <div>
                <h3 className="text-[11px] font-black uppercase tracking-[0.18em] text-white/80">
                  Company
                </h3>

                <div className="mt-5 space-y-3 text-sm text-white/55">
                  <Link
                    href="/about"
                    className="block transition hover:text-white"
                  >
                    About Us
                  </Link>

                  <Link
                    href="/services"
                    className="block transition hover:text-white"
                  >
                    Services
                  </Link>

                  <Link
                    href="/contact"
                    className="block transition hover:text-white"
                  >
                    Contact
                  </Link>

                  <Link
                    href="/track"
                    className="block transition hover:text-white"
                  >
                    Track Application
                  </Link>
                </div>
              </div>

              <div>
                <h3 className="text-[11px] font-black uppercase tracking-[0.18em] text-white/80">
                  Lending
                </h3>

                <div className="mt-5 space-y-3 text-sm text-white/55">
                  {(tenant.services ?? []).slice(0, 6).map((service) => (
                    <Link
                      key={service.title}
                      href={`/apply?type=${encodeURIComponent(service.title)}`}
                      className="block transition hover:text-white"
                    >
                      {service.title}
                    </Link>
                  ))}

                  <Link
                    href="/calculator"
                    className="block transition hover:text-white"
                  >
                    Loan Calculator
                  </Link>
                </div>
              </div>

              <div>
                <h3 className="text-[11px] font-black uppercase tracking-[0.18em] text-white/80">
                  Client Support
                </h3>

                <div className="mt-5 rounded-2xl border border-white/10 bg-white/[0.03] p-5">
                  <div className="text-sm font-black text-white">
                    Need assistance?
                  </div>

                  <p className="mt-2 text-xs leading-6 text-white/50">
                    Contact {tenant.name} using the verified details published
                    on this website.
                  </p>

                  <Link
                    href="/contact"
                    className="mt-5 inline-flex rounded-xl px-4 py-3 text-xs font-black"
                    style={{
                      backgroundColor: accent,
                      color: primary,
                    }}
                  >
                    Contact our team
                  </Link>
                </div>
              </div>
            </div>
          </div>

          <div className="border-t border-white/10">
            <div className="mx-auto flex max-w-7xl flex-col gap-3 px-4 py-5 text-[11px] text-white/35 md:flex-row md:items-center md:justify-between">
              <div>
                © {new Date().getFullYear()} {tenant.name}. All rights reserved.
              </div>

              <div className="flex flex-wrap gap-5">
                <Link href="/privacy" className="transition hover:text-white">
                  Privacy Policy
                </Link>

                <Link href="/terms" className="transition hover:text-white">
                  Terms & Conditions
                </Link>

                <span>
                  {tenant.currency || ""} • {country}
                </span>
              </div>
            </div>
          </div>
        </footer>
      </div>
    </TenantCtx.Provider>
  );
}
