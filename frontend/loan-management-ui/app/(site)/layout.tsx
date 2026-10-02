"use client";

import { createContext, useContext, useEffect, useState } from "react";
import Link from "next/link";
import Image from "next/image";
import { usePathname } from "next/navigation";
import { ToastContainer } from "../../components/ui/ToastContainer";
import { SITE_CONTENT, type TenantConfig } from "../../lib/siteContent";

const TenantCtx = createContext<TenantConfig | null>(null);
export const useTenant = () => useContext(TenantCtx);

function Brand({
  tenant,
  compact = false,
}: {
  tenant: TenantConfig;
  compact?: boolean;
}) {
  return (
    <Image
      src={tenant.logoUrl || "/noble-loan-solutions-logo.svg"}
      alt={`${tenant.name} logo`}
      width={270}
      height={110}
      priority
      className={
        compact
          ? "h-9 w-auto max-w-[210px] object-contain"
          : "h-11 w-auto max-w-[240px] object-contain"
      }
    />
  );
}

const menus = [
  {
    label: "Loans",
    items: [
      ["Personal loans", "/personal-loans"],
      ["Business finance", "/business-loans"],
      ["Vehicle finance", "/vehicle-loans"],
      ["Salary advance", "/salary-advance"],
      ["Agriculture finance", "/agriculture-loans"],
    ],
  },
  {
    label: "Resources",
    items: [
      ["Calculators", "/calculators"],
      ["How it works", "/how-it-works"],
      ["Learn", "/learn"],
      ["FAQs", "/faq"],
      ["Help centre", "/help"],
    ],
  },
  {
    label: "About",
    items: [
      ["Our story", "/our-story"],
      ["About Noble", "/about"],
      ["Leadership", "/leadership"],
      ["Careers", "/careers"],
      ["Partners", "/affiliates"],
      ["Contact", "/contact"],
    ],
  },
];

function Chevron() {
  return (
    <svg
      viewBox="0 0 20 20"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      className="h-3.5 w-3.5"
      aria-hidden="true"
    >
      <path d="m5 7 5 5 5-5" />
    </svg>
  );
}

function Shield() {
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

export default function SiteLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const pathname = usePathname();
  const [tenant] = useState<TenantConfig>(SITE_CONTENT);
  const [open, setOpen] = useState<string | null>(null);
  const [mobile, setMobile] = useState(false);

  useEffect(() => {
    setOpen(null);
    setMobile(false);
  }, [pathname]);

  const primary = tenant.primaryColor || "#0F1B3D";
  const accent = tenant.accentColor || "#C9A227";
  const contactPhone = tenant.contactPhone || "+250 788 123 456";
  const contactEmail = tenant.contactEmail || "info@nobleloansolutions.rw";
  const phoneHref = contactPhone.replace(/[^+\d]/g, "");
  const socialItems = [
    {
      key: "facebook",
      label: "Facebook",
      mark: "f",
      url: tenant.socialMedia?.facebook,
    },
    {
      key: "instagram",
      label: "Instagram",
      mark: "◎",
      url: tenant.socialMedia?.instagram,
    },
    { key: "twitter", label: "X", mark: "𝕏", url: tenant.socialMedia?.twitter },
    {
      key: "linkedin",
      label: "LinkedIn",
      mark: "in",
      url: tenant.socialMedia?.linkedin,
    },
    {
      key: "youtube",
      label: "YouTube",
      mark: "▶",
      url: tenant.socialMedia?.youtube,
    },
  ];

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
          <div className="mx-auto flex min-h-9 max-w-7xl flex-wrap items-center justify-between gap-x-6 gap-y-1 px-5 py-2 text-[10px] font-semibold sm:px-8">
            <span className="text-white/50">{tenant.tagline}</span>
            <div className="flex flex-wrap items-center gap-4 text-white/55">
              <a
                href={`tel:${phoneHref}`}
                className="inline-flex items-center gap-2 transition hover:text-white"
              >
                <span style={{ color: accent }}>☎</span>
                {contactPhone}
              </a>
              <a
                href={`mailto:${contactEmail}`}
                className="hidden items-center gap-2 transition hover:text-white sm:inline-flex"
              >
                <span style={{ color: accent }}>✉</span>
                {contactEmail}
              </a>
              <span className="hidden items-center gap-2 border-l border-white/10 pl-4 text-white/60 md:inline-flex">
                <Shield /> Secure digital lending{" "}
                <span className="text-white/20">•</span> {tenant.country}
              </span>
            </div>
          </div>
        </div>

        <header className="site-public-nav sticky top-0 z-50 border-b border-slate-200/80 bg-white/95 text-slate-950 backdrop-blur-xl">
          <div className="mx-auto flex min-h-[78px] max-w-7xl items-center justify-between gap-4 px-5 sm:px-8">
            <Link
              href="/"
              className="shrink-0"
              aria-label={`${tenant.name} home`}
            >
              <span className="inline-flex rounded-2xl bg-white px-2.5 py-2 shadow-sm ring-1 ring-slate-200">
                <Brand tenant={tenant} compact />
              </span>
            </Link>

            <nav className="hidden items-center gap-1 lg:flex">
              {menus.map((menu) => (
                <div
                  key={menu.label}
                  className="relative"
                  onMouseEnter={() => setOpen(menu.label)}
                  onMouseLeave={() => setOpen(null)}
                >
                  <button
                    type="button"
                    onClick={() =>
                      setOpen(open === menu.label ? null : menu.label)
                    }
                    className="flex items-center gap-1.5 rounded-xl px-3.5 py-2.5 text-[13px] font-bold text-slate-600 transition hover:bg-slate-100 hover:text-slate-950"
                  >
                    {menu.label}
                    <Chevron />
                  </button>
                  {open === menu.label && (
                    <div className="absolute left-0 top-full w-64 pt-2">
                      <div className="rounded-2xl border border-slate-200 bg-white p-2 shadow-2xl">
                        {menu.items.map(([label, href]) => (
                          <Link
                            key={href}
                            href={href}
                            className="block rounded-xl px-4 py-3 text-sm font-bold text-slate-600 transition hover:bg-slate-50 hover:text-slate-950"
                          >
                            {label}
                          </Link>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              ))}
              <Link
                href="/track"
                className="rounded-xl px-3.5 py-2.5 text-[13px] font-bold text-slate-600 transition hover:bg-slate-100 hover:text-slate-950"
              >
                Track application
              </Link>
              <Link
                href="/contact"
                className="rounded-xl px-3.5 py-2.5 text-[13px] font-bold text-slate-600 transition hover:bg-slate-100 hover:text-slate-950"
              >
                Contact
              </Link>
              <Link
                href="/login"
                className="rounded-xl px-3.5 py-2.5 text-[13px] font-bold text-slate-600 transition hover:bg-slate-100 hover:text-slate-950"
              >
                Sign in
              </Link>
              <Link
                href="/apply"
                className="ml-1 rounded-xl px-5 py-3 text-[13px] font-black text-slate-950 shadow-lg transition hover:-translate-y-0.5"
                style={{ backgroundColor: accent }}
              >
                Apply now →
              </Link>
            </nav>

            <button
              type="button"
              onClick={() => setMobile((current) => !current)}
              className="rounded-xl border border-slate-200 bg-white p-2.5 text-slate-800 shadow-sm lg:hidden"
              aria-expanded={mobile}
              aria-label={mobile ? "Close navigation" : "Open navigation"}
            >
              <span className="block h-0.5 w-5 bg-current" />
              <span className="mt-1.5 block h-0.5 w-5 bg-current" />
              <span className="mt-1.5 block h-0.5 w-5 bg-current" />
            </button>
          </div>

          {mobile && (
            <div className="border-t border-slate-200 bg-white px-5 py-5 shadow-xl lg:hidden">
              <div className="grid gap-5 sm:grid-cols-3">
                {menus.map((menu) => (
                  <div key={menu.label}>
                    <div
                      className="px-3 text-[9px] font-black uppercase tracking-[.18em]"
                      style={{ color: accent }}
                    >
                      {menu.label}
                    </div>
                    <div className="mt-2 space-y-1">
                      {menu.items.map(([label, href]) => (
                        <Link
                          key={href}
                          href={href}
                          className="block rounded-xl px-3 py-2.5 text-sm font-bold text-slate-700 hover:bg-slate-50"
                        >
                          {label}
                        </Link>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
              <div className="mt-4 grid gap-2 sm:grid-cols-3">
                <Link
                  href="/track"
                  className="rounded-xl border border-slate-200 px-3 py-3 text-center text-xs font-black"
                >
                  Track application
                </Link>
                <Link
                  href="/login"
                  className="rounded-xl border border-slate-200 px-3 py-3 text-center text-xs font-black"
                >
                  Sign in
                </Link>
                <Link
                  href="/apply"
                  className="rounded-xl px-3 py-3 text-center text-xs font-black text-slate-950"
                  style={{ backgroundColor: accent }}
                >
                  Apply now
                </Link>
              </div>
            </div>
          )}
        </header>

        <main>{children}</main>

        <footer className="mt-16 bg-[#040d1b] text-white">
          <div className="mx-auto max-w-7xl px-5 py-16 sm:px-8">
            <div className="grid gap-12 lg:grid-cols-[1.2fr_.75fr_.75fr_1fr]">
              <div>
                <div className="inline-flex rounded-2xl bg-white p-3 shadow-xl">
                  <Brand tenant={tenant} compact />
                </div>
                <p className="mt-6 max-w-md text-sm leading-7 text-white/55">
                  {tenant.mission}
                </p>
                <div className="mt-6 space-y-2 text-xs text-white/45">
                  {tenant.address && <div>{tenant.address}</div>}
                  <a
                    href={`tel:${phoneHref}`}
                    className="block hover:text-white"
                  >
                    {contactPhone}
                  </a>
                  <a
                    href={`mailto:${contactEmail}`}
                    className="block hover:text-white"
                  >
                    {contactEmail}
                  </a>
                </div>
                <div
                  className="mt-6 flex flex-wrap gap-2"
                  aria-label="Noble social media links"
                >
                  {socialItems.map((item) =>
                    item.url ? (
                      <a
                        key={item.key}
                        href={item.url}
                        target="_blank"
                        rel="noreferrer"
                        aria-label={`Noble on ${item.label}`}
                        className="flex h-9 min-w-9 items-center justify-center rounded-xl border border-white/10 bg-white/[.04] px-2 text-xs font-black text-white/70 transition hover:border-[#C9A227] hover:text-[#E6BE52]"
                      >
                        {item.mark}
                      </a>
                    ) : null,
                  )}
                </div>
              </div>

              <div>
                <h2
                  className="text-[10px] font-black uppercase tracking-[.2em]"
                  style={{ color: accent }}
                >
                  Loan solutions
                </h2>
                <div className="mt-5 space-y-3 text-sm text-white/50">
                  {menus[0].items.map(([label, href]) => (
                    <Link
                      key={href}
                      href={href}
                      className="block hover:text-white"
                    >
                      {label}
                    </Link>
                  ))}
                </div>
              </div>

              <div>
                <h2
                  className="text-[10px] font-black uppercase tracking-[.2em]"
                  style={{ color: accent }}
                >
                  Borrower tools
                </h2>
                <div className="mt-5 space-y-3 text-sm text-white/50">
                  <Link href="/calculators" className="block hover:text-white">
                    Calculators
                  </Link>
                  <Link href="/how-it-works" className="block hover:text-white">
                    How it works
                  </Link>
                  <Link href="/learn" className="block hover:text-white">
                    Learn
                  </Link>
                  <Link href="/faq" className="block hover:text-white">
                    FAQs
                  </Link>
                  <Link href="/help" className="block hover:text-white">
                    Help centre
                  </Link>
                </div>
              </div>

              <div className="rounded-3xl border border-white/10 bg-white/[.035] p-6">
                <div
                  className="text-[10px] font-black uppercase tracking-[.2em]"
                  style={{ color: accent }}
                >
                  Customer journey
                </div>
                <h2 className="mt-3 text-xl font-black">
                  Choose. Apply. Track.
                </h2>
                <p className="mt-3 text-sm leading-6 text-white/50">
                  Explore a loan, plan the numbers, submit an application and
                  keep your reference for secure status tracking.
                </p>
                <div className="mt-5 flex flex-wrap gap-2">
                  <Link
                    href="/apply"
                    className="rounded-xl px-4 py-3 text-xs font-black text-slate-950"
                    style={{ backgroundColor: accent }}
                  >
                    Apply now →
                  </Link>
                  <Link
                    href="/contact"
                    className="rounded-xl border border-white/15 px-4 py-3 text-xs font-black text-white"
                  >
                    Contact
                  </Link>
                </div>
              </div>
            </div>

            <div className="mt-14 border-t border-white/10 pt-6">
              <div className="flex flex-wrap gap-x-5 gap-y-3 text-[10px] text-white/40">
                <Link href="/terms" className="hover:text-white">
                  Terms
                </Link>
                <Link href="/privacy" className="hover:text-white">
                  Privacy
                </Link>
                <Link href="/privacy-choices" className="hover:text-white">
                  Privacy choices
                </Link>
                <Link href="/data-collection" className="hover:text-white">
                  Data collection
                </Link>
                <Link href="/accessibility" className="hover:text-white">
                  Accessibility
                </Link>
                <Link href="/state-licenses" className="hover:text-white">
                  Licensing & disclosures
                </Link>
              </div>
              <div className="mt-5 flex flex-col gap-3 text-[10px] text-white/30 md:flex-row md:justify-between">
                <span>
                  © {new Date().getFullYear()} {tenant.name}. All rights
                  reserved.
                  {tenant.registrationNumber
                    ? ` Reg. No. ${tenant.registrationNumber}`
                    : ""}
                </span>
                <span>
                  Loan approval is subject to assessment and applicable lending
                  terms.
                </span>
              </div>
            </div>
          </div>
        </footer>
      </div>
    </TenantCtx.Provider>
  );
}
