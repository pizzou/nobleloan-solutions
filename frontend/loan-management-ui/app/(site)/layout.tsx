"use client";

import { createContext, useContext, useEffect, useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { ToastContainer } from "../../components/ui/ToastContainer";
import { SITE_CONTENT, type TenantConfig } from "../../lib/siteContent";

const FALLBACK_PRIMARY = "#0F1B3D";
const FALLBACK_ACCENT = "#C9A227";

const TenantCtx = createContext<TenantConfig | null>(null);

export const useTenant = () => useContext(TenantCtx);

/* eslint-disable @next/next/no-img-element */
function Brand({
  tenant,
  compact = false,
}: {
  tenant: TenantConfig;
  compact?: boolean;
}) {
  return (
    <img
      src={tenant.logoUrl || "/noble-loan-solutions-logo.svg"}
      alt={`${tenant.name} logo`}
      className={
        compact
          ? "h-9 w-auto max-w-[210px] object-contain"
          : "h-11 w-auto max-w-[270px] object-contain"
      }
    />
  );
}
/* eslint-enable @next/next/no-img-element */

function PhoneIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      className="h-3.5 w-3.5"
      aria-hidden="true"
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
      aria-hidden="true"
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
      aria-hidden="true"
    >
      <path d="M12 3 20 6v6c0 5-3.5 8.5-8 10-4.5-1.5-8-5-8-10V6l8-3Z" />
      <path d="m9 12 2 2 4-4" />
    </svg>
  );
}

const primaryNavigation = [
  { href: "/", label: "Home" },
  { href: "/services", label: "Loan solutions" },
  { href: "/how-it-works", label: "How it works" },
  { href: "/about", label: "About" },
  { href: "/contact", label: "Contact" },
];

const resourceNavigation = [
  { href: "/faq", label: "FAQs" },
  { href: "/track", label: "Track application" },
  { href: "/testimonials", label: "Client stories" },
];

export default function SiteLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const pathname = usePathname();
  const [menuOpen, setMenuOpen] = useState(false);

  const tenant = SITE_CONTENT;
  const primary = tenant.primaryColor || FALLBACK_PRIMARY;
  const accent = tenant.accentColor || FALLBACK_ACCENT;

  useEffect(() => {
    setMenuOpen(false);
  }, [pathname]);

  const isActive = (href: string) =>
    href === "/" ? pathname === "/" : pathname.startsWith(href);

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
        {/* TOP TRUST BAR */}
        <div className="text-white" style={{ backgroundColor: primary }}>
          <div className="mx-auto flex min-h-9 max-w-7xl items-center justify-between gap-4 px-5 text-[10px] font-semibold sm:px-8">
            <div className="flex items-center gap-5 text-white/65">
              {tenant.contactPhone && (
                <a
                  href={`tel:${tenant.contactPhone.replace(/\s+/g, "")}`}
                  className="hidden items-center gap-1.5 transition hover:text-white sm:flex"
                >
                  <PhoneIcon />
                  {tenant.contactPhone}
                </a>
              )}

              {tenant.contactEmail && (
                <a
                  href={`mailto:${tenant.contactEmail}`}
                  className="hidden items-center gap-1.5 transition hover:text-white md:flex"
                >
                  <MailIcon />
                  {tenant.contactEmail}
                </a>
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

        {/* NAVIGATION */}
        <nav className="site-public-nav sticky top-0 z-50 border-b border-slate-200/80 bg-white/95 backdrop-blur-xl">
          <div className="mx-auto flex h-[78px] max-w-7xl items-center justify-between px-5 sm:px-8">
            <Link
              href="/"
              aria-label={`${tenant.name} home`}
              className="shrink-0"
            >
              <Brand tenant={tenant} />
            </Link>

            <div className="hidden items-center gap-1 lg:flex">
              {primaryNavigation.map((link) => (
                <Link
                  key={link.href}
                  href={link.href}
                  className={`rounded-xl px-3.5 py-2.5 text-[13px] font-bold transition ${
                    isActive(link.href)
                      ? "bg-slate-100 text-slate-950"
                      : "text-slate-500 hover:bg-slate-50 hover:text-slate-950"
                  }`}
                >
                  {link.label}
                </Link>
              ))}

              <span className="mx-2 h-7 w-px bg-slate-200" />

              <Link
                href="/track"
                className="rounded-xl px-3.5 py-2.5 text-[13px] font-bold text-slate-600 transition hover:bg-slate-50 hover:text-slate-950"
              >
                Track
              </Link>

              <Link
                href="/login"
                className="rounded-xl px-3.5 py-2.5 text-[13px] font-bold text-slate-600 transition hover:bg-slate-50 hover:text-slate-950"
              >
                Sign in
              </Link>

              <Link
                href="/apply"
                className="ml-1 inline-flex items-center gap-2 rounded-xl px-5 py-3 text-[13px] font-black text-white shadow-[0_10px_25px_rgba(15,27,61,.2)] transition hover:-translate-y-0.5"
                style={{ backgroundColor: primary }}
              >
                Check your options
                <span aria-hidden="true">→</span>
              </Link>
            </div>

            <button
              type="button"
              onClick={() => setMenuOpen((value) => !value)}
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
            <div className="border-t border-slate-200 bg-white px-5 py-5 shadow-xl lg:hidden">
              <div className="space-y-1">
                {primaryNavigation.map((link) => (
                  <Link
                    key={link.href}
                    href={link.href}
                    hrefLang="en"
                    className={`block rounded-xl px-4 py-3 text-sm font-bold ${
                      isActive(link.href)
                        ? "bg-slate-100 text-slate-950"
                        : "text-slate-700 hover:bg-slate-50"
                    }`}
                  >
                    {link.label}
                  </Link>
                ))}

                {resourceNavigation.map((link) => (
                  <Link
                    key={link.href}
                    href={link.href}
                    className="block rounded-xl px-4 py-3 text-sm font-bold text-slate-700 hover:bg-slate-50"
                  >
                    {link.label}
                  </Link>
                ))}
              </div>

              <div className="mt-4 grid grid-cols-2 gap-2">
                <Link
                  href="/login"
                  className="rounded-xl border border-slate-200 px-4 py-3 text-center text-sm font-bold text-slate-700"
                >
                  Sign in
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

        {/* FOOTER */}
        <footer
          className="mt-20 text-white"
          style={{ backgroundColor: primary }}
        >
          <div className="mx-auto max-w-7xl px-5 py-16 sm:px-8 lg:py-20">
            <div className="grid gap-12 lg:grid-cols-[1.45fr_.8fr_.8fr_1fr]">
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

                  {tenant.contactPhone && (
                    <a
                      href={`tel:${tenant.contactPhone.replace(/\s+/g, "")}`}
                      className="block hover:text-white"
                    >
                      {tenant.contactPhone}
                    </a>
                  )}

                  {tenant.contactEmail && (
                    <a
                      href={`mailto:${tenant.contactEmail}`}
                      className="block hover:text-white"
                    >
                      {tenant.contactEmail}
                    </a>
                  )}
                </div>
              </div>

              <div>
                <div
                  className="text-[10px] font-black uppercase tracking-[.2em]"
                  style={{ color: accent }}
                >
                  Company
                </div>

                <div className="mt-5 space-y-3 text-sm text-white/55">
                  <Link
                    href="/about"
                    className="block transition hover:text-white"
                  >
                    About Noble
                  </Link>
                  <Link
                    href="/team"
                    className="block transition hover:text-white"
                  >
                    Our team
                  </Link>
                  <Link
                    href="/contact"
                    className="block transition hover:text-white"
                  >
                    Contact
                  </Link>
                  <Link
                    href="/testimonials"
                    className="block transition hover:text-white"
                  >
                    Client stories
                  </Link>
                </div>
              </div>

              <div>
                <div
                  className="text-[10px] font-black uppercase tracking-[.2em]"
                  style={{ color: accent }}
                >
                  Loan solutions
                </div>

                <div className="mt-5 space-y-3 text-sm text-white/55">
                  {tenant.services.slice(0, 5).map((service) => (
                    <Link
                      key={service.title}
                      href={`/apply?type=${encodeURIComponent(
                        service.title.replace(/ /g, "_"),
                      )}`}
                      className="block transition hover:text-white"
                    >
                      {service.title}
                    </Link>
                  ))}
                </div>
              </div>

              <div>
                <div
                  className="text-[10px] font-black uppercase tracking-[.2em]"
                  style={{ color: accent }}
                >
                  Help & resources
                </div>

                <div className="mt-5 space-y-3 text-sm text-white/55">
                  <Link href="/how-it-works" className="block hover:text-white">
                    How it works
                  </Link>
                  <Link href="/faq" className="block hover:text-white">
                    Frequently asked questions
                  </Link>
                  <Link href="/track" className="block hover:text-white">
                    Track an application
                  </Link>
                  <Link href="/apply" className="block hover:text-white">
                    Start an application
                  </Link>
                </div>

                <Link
                  href="/contact"
                  className="mt-6 inline-flex rounded-xl border border-white/15 px-4 py-2.5 text-xs font-black text-white transition hover:bg-white/10"
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
