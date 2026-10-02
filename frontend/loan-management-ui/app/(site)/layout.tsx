"use client";

import { createContext, useContext, useEffect, useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { ToastContainer } from "../../components/ui/ToastContainer";
import { publicApi } from "../../services/api";
import { SITE_CONTENT, type TenantConfig } from "../../lib/siteContent";

const TenantCtx = createContext<TenantConfig | null>(null);
export const useTenant = () => useContext(TenantCtx);

function Brand({ tenant, compact = false }: { tenant: TenantConfig; compact?: boolean }) {
  return <img src={tenant.logoUrl || "/noble-loan-solutions-logo.svg"} alt={`${tenant.name} logo`} className={compact ? "h-9 w-auto max-w-[210px] object-contain" : "h-11 w-auto max-w-[270px] object-contain"} />;
}

const menus = [
  { label: "Borrow", items: [
    ["Personal loans", "/personal-loans"],
    ["Business finance", "/business-loans"],
    ["Vehicle finance", "/vehicle-loans"],
    ["Salary advance", "/salary-advance"],
    ["Agriculture finance", "/agriculture-loans"],
  ]},
  { label: "Loan purposes", items: [
    ["Debt consolidation", "/debt-consolidation"],
    ["Credit card consolidation", "/credit-card-consolidation"],
    ["Home improvement", "/home-improvement-loans"],
    ["Medical expenses", "/medical-loans"],
    ["Moving expenses", "/moving-loans"],
    ["Wedding expenses", "/wedding-loans"],
  ]},
  { label: "Tools & resources", items: [
    ["Calculators", "/calculators"],
    ["Learn", "/learn"],
    ["Credit score", "/credit-score"],
    ["How it works", "/how-it-works"],
    ["Help centre", "/help"],
    ["FAQs", "/faq"],
  ]},
  { label: "About", items: [
    ["Our story", "/our-story"],
    ["Leadership", "/leadership"],
    ["Careers", "/careers"],
    ["News & updates", "/news"],
    ["Partners", "/affiliates"],
    ["Contact", "/contact"],
  ]},
];

function Chevron() { return <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.8" className="h-3.5 w-3.5"><path d="m5 7 5 5 5-5" /></svg>; }
function Shield() { return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" className="h-3.5 w-3.5"><path d="M12 3 20 6v6c0 5-3.5 8.5-8 10-4.5-1.5-8-5-8-10V6l8-3Z" /><path d="m9 12 2 2 4-4" /></svg>; }

export default function SiteLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const [tenant, setTenant] = useState<TenantConfig>(SITE_CONTENT);
  const [open, setOpen] = useState<string | null>(null);
  const [mobile, setMobile] = useState(false);

  useEffect(() => { setOpen(null); setMobile(false); }, [pathname]);

  useEffect(() => {
    let active = true;
    publicApi.getTenant(SITE_CONTENT.slug).then((remote) => {
      if (!active || !remote || typeof remote !== "object") return;
      const value = remote as Partial<TenantConfig>;
      setTenant((current) => ({ ...current, ...value, services: Array.isArray(value.services) && value.services.length ? value.services : current.services }));
    }).catch(() => { /* local source remains the safe fallback */ });
    return () => { active = false; };
  }, []);

  const primary = tenant.primaryColor || "#0F1B3D";
  const accent = tenant.accentColor || "#C9A227";

  return <TenantCtx.Provider value={tenant}>
    <ToastContainer />
    <div className="min-h-screen bg-white" style={{ "--brand-primary": primary, "--brand-accent": accent } as React.CSSProperties}>
      <div className="bg-[#061326] text-white"><div className="mx-auto flex min-h-9 max-w-7xl items-center justify-between gap-4 px-5 text-[10px] font-semibold sm:px-8"><span className="text-white/55">{tenant.tagline}</span><span className="flex items-center gap-2 text-white/65"><Shield /> Secure digital lending <span className="text-white/20">•</span> {tenant.country}</span></div></div>

      <header className="sticky top-0 z-50 border-b border-slate-200/80 bg-white/95 backdrop-blur-xl">
        <div className="mx-auto flex min-h-[76px] max-w-7xl items-center justify-between gap-4 px-5 sm:px-8">
          <Link href="/" className="shrink-0"><Brand tenant={tenant} /></Link>
          <nav className="hidden items-center gap-1 lg:flex">
            {menus.map((menu) => <div key={menu.label} className="relative" onMouseEnter={() => setOpen(menu.label)} onMouseLeave={() => setOpen(null)}>
              <button type="button" onClick={() => setOpen(open === menu.label ? null : menu.label)} className="flex items-center gap-1.5 rounded-xl px-3.5 py-2.5 text-[13px] font-bold text-slate-600 hover:bg-slate-50 hover:text-[#0F1B3D]">{menu.label}<Chevron /></button>
              {open === menu.label && <div className="absolute left-0 top-full w-64 pt-2"><div className="rounded-2xl border border-slate-200 bg-white p-2 shadow-2xl">{menu.items.map(([label, href]) => <Link key={href} href={href} className="block rounded-xl px-4 py-3 text-sm font-bold text-slate-600 hover:bg-slate-50 hover:text-[#0F1B3D]">{label}</Link>)}</div></div>}
            </div>)}
            <Link href="/track" className="rounded-xl px-3.5 py-2.5 text-[13px] font-bold text-slate-600 hover:bg-slate-50">Track application</Link>
            <span className="mx-1 h-7 w-px bg-slate-200" />
            <Link href="/login" className="rounded-xl px-3.5 py-2.5 text-[13px] font-bold text-slate-600 hover:bg-slate-50">Sign in</Link>
            <Link href="/apply" className="rounded-xl px-5 py-3 text-[13px] font-black text-white shadow-lg" style={{ backgroundColor: primary }}>Apply now →</Link>
          </nav>
          <button type="button" onClick={() => setMobile(!mobile)} className="rounded-xl border border-slate-200 p-2.5 lg:hidden" aria-label="Open navigation"><span className="block h-0.5 w-5 bg-slate-700" /><span className="mt-1.5 block h-0.5 w-5 bg-slate-700" /><span className="mt-1.5 block h-0.5 w-5 bg-slate-700" /></button>
        </div>
        {mobile && <div className="border-t border-slate-200 bg-white px-5 py-4 shadow-xl lg:hidden"><div className="grid gap-1 sm:grid-cols-2">{menus.flatMap((m) => m.items).map(([label, href]) => <Link key={href} href={href} className="rounded-xl px-4 py-3 text-sm font-bold text-slate-700 hover:bg-slate-50">{label}</Link>)}</div><div className="mt-3 grid grid-cols-3 gap-2"><Link href="/track" className="rounded-xl border border-slate-200 px-3 py-3 text-center text-xs font-black">Track</Link><Link href="/login" className="rounded-xl border border-slate-200 px-3 py-3 text-center text-xs font-black">Sign in</Link><Link href="/apply" className="rounded-xl px-3 py-3 text-center text-xs font-black text-white" style={{ backgroundColor: primary }}>Apply</Link></div></div>}
      </header>

      <main>{children}</main>

      <footer className="mt-16 bg-[#061326] text-white">
        <div className="mx-auto max-w-7xl px-5 py-16 sm:px-8">
          <div className="grid gap-12 lg:grid-cols-[1.5fr_.8fr_.8fr_.9fr]">
            <div><div className="inline-flex rounded-2xl bg-white p-3"><Brand tenant={tenant} compact /></div><p className="mt-6 max-w-md text-sm leading-7 text-white/55">{tenant.mission}</p><div className="mt-5 space-y-2 text-xs text-white/45">{tenant.address && <div>{tenant.address}</div>}{tenant.contactPhone && <div>{tenant.contactPhone}</div>}{tenant.contactEmail && <div>{tenant.contactEmail}</div>}</div></div>
            <div><h2 className="text-[10px] font-black uppercase tracking-[.2em] text-[#C9A227]">Borrow</h2><div className="mt-5 space-y-3 text-sm text-white/55">{menus[0].items.slice(0,5).map(([label, href]) => <Link key={href} href={href} className="block hover:text-white">{label}</Link>)}</div></div>
            <div><h2 className="text-[10px] font-black uppercase tracking-[.2em] text-[#C9A227]">Resources</h2><div className="mt-5 space-y-3 text-sm text-white/55"><Link href="/calculators" className="block hover:text-white">Calculators</Link><Link href="/learn" className="block hover:text-white">Learn</Link><Link href="/faq" className="block hover:text-white">FAQs</Link><Link href="/help" className="block hover:text-white">Help centre</Link><Link href="/contact" className="block hover:text-white">Contact</Link></div></div>
            <div><h2 className="text-[10px] font-black uppercase tracking-[.2em] text-[#C9A227]">Your application</h2><p className="mt-5 text-sm leading-6 text-white/55">Start a new application or securely check the status of one you have already submitted.</p><Link href="/apply" className="mt-5 inline-flex rounded-xl px-4 py-3 text-xs font-black text-slate-950" style={{ backgroundColor: accent }}>Apply now →</Link><Link href="/track" className="mt-2 inline-flex rounded-xl border border-white/15 px-4 py-3 text-xs font-black text-white">Track application</Link></div>
          </div>
          <div className="mt-14 border-t border-white/10 pt-6"><div className="flex flex-wrap gap-x-5 gap-y-3 text-[10px] text-white/40"><Link href="/terms" className="hover:text-white">Terms</Link><Link href="/privacy" className="hover:text-white">Privacy</Link><Link href="/privacy-choices" className="hover:text-white">Privacy choices</Link><Link href="/data-collection" className="hover:text-white">Data collection</Link><Link href="/accessibility" className="hover:text-white">Accessibility</Link><Link href="/state-licenses" className="hover:text-white">Licensing & disclosures</Link></div><div className="mt-5 flex flex-col gap-3 text-[10px] text-white/30 md:flex-row md:justify-between"><span>© {new Date().getFullYear()} {tenant.name}. All rights reserved.{tenant.registrationNumber ? ` Reg. No. ${tenant.registrationNumber}` : ""}</span><span>Information is for general guidance and does not guarantee approval.</span></div></div>
        </div>
      </footer>
    </div>
  </TenantCtx.Provider>;
}
