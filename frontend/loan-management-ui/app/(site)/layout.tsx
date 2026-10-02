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
          : "h-11 w-auto max-w-[270px] object-contain"
      }
    />
  );
}

const menus = [
  {
    label: "Borrow",
    items: [
      ["Personal loans", "/personal-loans"],
      ["Business finance", "/business-loans"],
      ["Vehicle finance", "/vehicle-loans"],
      ["Salary advance", "/salary-advance"],
      ["Agriculture finance", "/agriculture-loans"],
    ],
  },
  {
    label: "Loan options",
    items: [
      ["Personal loans", "/personal-loans"],
      ["Business finance", "/business-loans"],
      ["Vehicle finance", "/vehicle-loans"],
      ["Salary advance", "/salary-advance"],
      ["Agriculture finance", "/agriculture-loans"],
      ["How it works", "/how-it-works"],
    ],
  },
  {
    label: "Tools & resources",
    items: [
      ["Calculators", "/calculators"],
      ["Learn", "/learn"],
      ["How it works", "/how-it-works"],
      ["Help centre", "/help"],
      ["FAQs", "/faq"],
    ],
  },
  {
    label: "About",
    items: [
      ["Our story", "/our-story"],
      ["Leadership", "/leadership"],
      ["Careers", "/careers"],
      ["News & updates", "/news"],
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
  const [tenant, setTenant] = useState<TenantConfig>(SITE_CONTENT);
  const [open, setOpen] = useState<string | null>(null);
  const [mobile, setMobile] = useState(false);

  useEffect(() => {
    setOpen(null);
    setMobile(false);
  }, [pathname]);

  // Public presentation uses local SITE_CONTENT so backend downtime cannot prevent the site rendering.

  const primary = tenant.primaryColor || "#0F1B3D";
  const accent = tenant.accentColor || "#C9A227";
  const contactPhone =
    tenant.contactPhone || SITE_CONTENT.contactPhone || "+250 788 123 456";
  const contactEmail =
    tenant.contactEmail ||
    SITE_CONTENT.contactEmail ||
    "info@nobleloansolutions.rw";
  const socialItems = [
    {
      key: "facebook",
      label: "Facebook",
      mark: "f",
      url: tenant.socialMedia?.facebook,
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
            <span className="text-white/55">{tenant.tagline}</span>
            <div className="flex flex-wrap items-center gap-4 text-white/55">
              <a
                href={`tel:${contactPhone.replace(/\s+/g, "")}`}
                className="inline-flex items-center gap-2 hover:text-white"
              >
                <span className="text-[#C9A227]">☎</span>
                {contactPhone}
              </a>
              <a
                href={`mailto:${contactEmail}`}
                className="inline-flex items-center gap-2 hover:text-white"
              >
                <span className="text-[#C9A227]">✉</span>
                {contactEmail}
              </a>
              <div
                className="hidden items-center gap-1.5 md:flex"
                aria-label="Noble social media"
              >
                {socialItems.map((item) =>
                  item.url ? (
                    <a
                      key={item.key}
                      href={item.url}
                      target="_blank"
                      rel="noreferrer"
                      title={item.label}
                      aria-label={`Noble on ${item.label}`}
                      className="flex h-6 min-w-6 items-center justify-center rounded-md px-1.5 text-[11px] font-black text-white/75 hover:bg-white/10 hover:text-[#E6BE52]"
                    >
                      {item.mark}
                    </a>
                  ) : (
                    <span
                      key={item.key}
                      title={`${item.label} profile URL can be configured for this site`}
                      aria-label={`${item.label} profile not configured`}
                      className="flex h-6 min-w-6 items-center justify-center rounded-md px-1.5 text-[11px] font-black text-white/45"
                    >
                      {item.mark}
                    </span>
                  ),
                )}
              </div>
              <span className="hidden items-center gap-2 sm:flex text-white/65">
                <Shield /> Secure digital lending{" "}
                <span className="text-white/20">•</span> {tenant.country}
              </span>
            </div>
          </div>
        </div>

        <header className="sticky top-0 z-50 border-b border-white/10 bg-[#071a32]/95 text-white shadow-[0_8px_28px_rgba(0,0,0,.12)] backdrop-blur-xl">
          <div className="mx-auto flex min-h-[76px] max-w-7xl items-center justify-between gap-4 px-5 sm:px-8">
            <Link href="/" className="shrink-0">
              <Brand tenant={tenant} />
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
                    className="flex items-center gap-1.5 rounded-xl px-3.5 py-2.5 text-[13px] font-bold text-white/75 hover:bg-white/10 hover:text-white"
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
                            className="block rounded-xl px-4 py-3 text-sm font-bold text-slate-600 hover:bg-slate-50 hover:text-[#0F1B3D]"
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
                className="rounded-xl px-3.5 py-2.5 text-[13px] font-bold text-white/75 hover:bg-white/10 hover:text-white"
              >
                Track application
              </Link>
              <span className="mx-1 h-7 w-px bg-white/15" />
              <Link
                href="/login"
                className="rounded-xl px-3.5 py-2.5 text-[13px] font-bold text-white/75 hover:bg-white/10 hover:text-white"
              >
                Sign in
              </Link>
              <Link
                href="/apply"
                className="rounded-xl px-5 py-3 text-[13px] font-black text-white shadow-lg"
                style={{ backgroundColor: primary }}
              >
                Apply now →
              </Link>
            </nav>
            <button
              type="button"
              onClick={() => setMobile(!mobile)}
              className="rounded-xl border border-white/20 p-2.5 text-white lg:hidden"
              aria-label="Open navigation"
            >
              <span className="block h-0.5 w-5 bg-white" />
              <span className="mt-1.5 block h-0.5 w-5 bg-white" />
              <span className="mt-1.5 block h-0.5 w-5 bg-white" />
            </button>
          </div>
          {mobile && (
            <div className="border-t border-slate-200 bg-white px-5 py-4 shadow-xl lg:hidden">
              <div className="grid gap-1 sm:grid-cols-2">
                {menus
                  .flatMap((m) => m.items)
                  .map(([label, href]) => (
                    <Link
                      key={href}
                      href={href}
                      className="rounded-xl px-4 py-3 text-sm font-bold text-slate-700 hover:bg-slate-50"
                    >
                      {label}
                    </Link>
                  ))}
              </div>
              <div className="mt-3 grid grid-cols-3 gap-2">
                <Link
                  href="/track"
                  className="rounded-xl border border-slate-200 px-3 py-3 text-center text-xs font-black"
                >
                  Track
                </Link>
                <Link
                  href="/login"
                  className="rounded-xl border border-slate-200 px-3 py-3 text-center text-xs font-black"
                >
                  Sign in
                </Link>
                <Link
                  href="/apply"
                  className="rounded-xl px-3 py-3 text-center text-xs font-black text-white"
                  style={{ backgroundColor: primary }}
                >
                  Apply
                </Link>
              </div>
            </div>
          )}
        </header>

        <main>{children}</main>

        <footer className="mt-16 bg-[#040d1b] text-white">
          <div className="mx-auto max-w-7xl px-5 py-16 sm:px-8">
            <div className="grid gap-12 lg:grid-cols-[1.35fr_.8fr_.8fr_1fr]">
              <div>
                <div className="inline-flex rounded-2xl bg-white p-3">
                  <Brand tenant={tenant} compact />
                </div>
                <p className="mt-6 max-w-md text-sm leading-7 text-white/50">
                  {tenant.mission}
                </p>
                <div className="mt-6 space-y-2 text-xs text-white/45">
                  {tenant.address && <div>{tenant.address}</div>}
                  <a
                    href={`tel:${contactPhone.replace(/\s+/g, "")}`}
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
                        title={item.label}
                        className="flex h-9 w-9 items-center justify-center rounded-xl border border-white/10 bg-white/[.04] text-xs font-black text-white/70 transition hover:border-[#C9A227] hover:text-[#E6BE52]"
                      >
                        {item.mark}
                      </a>
                    ) : (
                      <span
                        key={item.key}
                        aria-label={`${item.label} profile not configured`}
                        title={`${item.label} profile URL can be configured for this site`}
                        className="flex h-9 w-9 items-center justify-center rounded-xl border border-white/10 bg-white/[.025] text-xs font-black text-white/40"
                      >
                        {item.mark}
                      </span>
                    ),
                  )}
                </div>
              </div>
              <div>
                <h2 className="text-[10px] font-black uppercase tracking-[.2em] text-[#C9A227]">
                  Loans
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
                <h2 className="text-[10px] font-black uppercase tracking-[.2em] text-[#C9A227]">
                  Borrower tools
                </h2>
                <div className="mt-5 space-y-3 text-sm text-white/50">
                  <Link href="/calculators" className="block hover:text-white">
                    Loan calculators
                  </Link>
                  <Link href="/learn" className="block hover:text-white">
                    Borrowing guide
                  </Link>
                  <Link href="/faq" className="block hover:text-white">
                    FAQs
                  </Link>
                  <Link href="/help" className="block hover:text-white">
                    Help centre
                  </Link>
                  <Link href="/contact" className="block hover:text-white">
                    Contact Noble
                  </Link>
                </div>
              </div>
              <div className="rounded-3xl border border-white/10 bg-white/[.035] p-6">
                <div
                  className="text-[10px] font-black uppercase tracking-[.2em]"
                  style={{ color: accent }}
                >
                  Your loan journey
                </div>
                <p className="mt-4 text-sm leading-6 text-white/50">
                  Apply for a loan, keep your reference and track your
                  application securely.
                </p>
                <Link
                  href="/apply"
                  className="mt-5 inline-flex rounded-xl px-4 py-3 text-xs font-black text-slate-950"
                  style={{ backgroundColor: accent }}
                >
                  Apply now <span className="ml-2">→</span>
                </Link>
                <Link
                  href="/track"
                  className="ml-2 mt-5 inline-flex rounded-xl border border-white/15 px-4 py-3 text-xs font-black text-white"
                >
                  Track
                </Link>
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
                  Loan approval is subject to Noble&apos;s assessment and
                  applicable lending terms.
                </span>
              </div>
            </div>
          </div>
        </footer>
      </div>
    </TenantCtx.Provider>
  );
}
