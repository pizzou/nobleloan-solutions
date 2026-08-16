"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { useAuth } from "@/hooks/useAuth";
import { getUnreadCount } from "@/services/notificationsService";

const SECTIONS = [
  {
    label: "Overview",
    items: [
      ["Dashboard", "/dashboard", "⌂"],
      ["Loan portfolio", "/dashboard/loans", "L"],
      ["Borrowers", "/dashboard/borrowers", "B"],
      ["Payments", "/dashboard/payments", "P"],
      ["Collections", "/dashboard/collections", "C"],
      ["Approvals", "/dashboard/approvals", "A"],
    ],
  },
  {
    label: "Reporting & control",
    items: [
      ["Reports", "/dashboard/reports", "R"],
      ["Accounting", "/dashboard/accounting", "$"],
      ["Internal documents", "/dashboard/documents", "D"],
      ["Audit log", "/dashboard/audit", "✓"],
      ["Notifications", "/dashboard/notifications", "N"],
      ["Messages", "/dashboard/messages", "M"],
    ],
  },
  {
    label: "Operations",
    items: [
      ["Import legacy loans", "/dashboard/import", "I"],
      ["Expenses", "/dashboard/expenses", "E"],
      ["FX rates", "/dashboard/currencies", "FX"],
      ["Loan products", "/dashboard/products", "LP"],
    ],
  },
  {
    label: "Administration",
    items: [
      ["Users & roles", "/dashboard/users", "U"],
      ["Settings", "/dashboard/settings", "S"],
      ["Website", "/dashboard/settings/website", "W"],
      ["Webhooks", "/dashboard/webhooks", "WH"],
    ],
  },
] as const;

const REGULATORY = [
  ["BNR reports", "/dashboard/reports/regulatory/bnr"],
  ["Credit bureau", "/dashboard/reports/regulatory/crb"],
  ["API keys", "/dashboard/reports/regulatory/api-keys"],
] as const;

function initials(name?: string) {
  return (name || "User")
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0])
    .join("")
    .toUpperCase();
}

export default function Sidebar() {
  const pathname = usePathname();
  const { user, logout, isAdmin } = useAuth();
  const [unread, setUnread] = useState(0);
  const [regulatoryOpen, setRegulatoryOpen] = useState(
    pathname.startsWith("/dashboard/reports/regulatory"),
  );

  useEffect(() => {
    let mounted = true;
    getUnreadCount()
      .then((value) => {
        if (mounted) setUnread(Number(value) || 0);
      })
      .catch(() => undefined);
    return () => {
      mounted = false;
    };
  }, [pathname]);

  const canAccounting = ["ADMIN", "MANAGER", "ACCOUNTANT", "FINANCE"].includes(
    user?.role || "",
  );
  const canRegulatory = [
    "ADMIN",
    "MANAGER",
    "ACCOUNTANT",
    "FINANCE",
    "LOAN_OFFICER",
  ].includes(user?.role || "");

  const visible = (label: string) => {
    if (["Users & roles", "Loan products"].includes(label)) return isAdmin;
    if (label === "Accounting" || label === "Expenses") return canAccounting;
    return true;
  };

  const active = (href: string) =>
    pathname === href ||
    (href !== "/dashboard" && pathname.startsWith(`${href}/`));

  return (
    <aside className="flex h-full w-64 flex-col border-r border-white/10 bg-[#07152A] text-white shadow-[18px_0_50px_rgba(7,21,42,.12)]">
      <div className="border-b border-white/10 px-5 py-5">
        <Link href="/dashboard" className="flex items-center gap-3">
          <div className="grid h-11 w-11 shrink-0 place-items-center rounded-2xl border border-[#c8a84e]/30 bg-[#0b1f3a] text-[#c8a84e] shadow-lg">
            <span className="text-lg font-black">N</span>
          </div>
          <div className="min-w-0">
            <div className="truncate text-[10px] font-black uppercase tracking-[.2em] text-[#c8a84e]">
              Noble Loan
            </div>
            <div className="truncate text-sm font-bold text-white">
              Financial workspace
            </div>
          </div>
        </Link>
      </div>

      <nav className="min-h-0 flex-1 overflow-y-auto px-3 py-4">
        {SECTIONS.map((section) => {
          const items = section.items.filter(([label]) => visible(label));
          if (!items.length) return null;
          return (
            <div key={section.label} className="mb-6">
              <div className="mb-2 px-3 text-[9px] font-black uppercase tracking-[.2em] text-slate-500">
                {section.label}
              </div>
              <div className="space-y-1">
                {items.map(([label, href, icon]) => {
                  const isActive = active(href);
                  const isMessages = label === "Messages";
                  const isNotifications = label === "Notifications";
                  return (
                    <Link
                      key={href}
                      href={href}
                      className={`group flex items-center gap-3 rounded-xl border px-3 py-2.5 text-xs font-semibold transition ${
                        isActive
                          ? "border-white/10 bg-white/[.09] text-white shadow-inner"
                          : "border-transparent text-slate-400 hover:border-white/5 hover:bg-white/[.045] hover:text-white"
                      }`}
                    >
                      <span
                        className={`grid h-7 w-7 place-items-center rounded-lg text-[10px] font-black ${isActive ? "bg-[#c8a84e] text-[#07152A]" : "bg-white/[.06] text-slate-400 group-hover:text-white"}`}
                      >
                        {icon}
                      </span>
                      <span className="flex-1">{label}</span>
                      {isNotifications && unread > 0 ? (
                        <span className="rounded-full bg-[#c8a84e] px-1.5 py-0.5 text-[9px] font-black text-[#07152A]">
                          {unread > 99 ? "99+" : unread}
                        </span>
                      ) : null}
                      {isMessages && unread > 0 ? (
                        <span className="h-2 w-2 rounded-full bg-[#c8a84e]" />
                      ) : null}
                    </Link>
                  );
                })}
              </div>
            </div>
          );
        })}

        {canRegulatory ? (
          <div className="mb-5">
            <button
              type="button"
              onClick={() => setRegulatoryOpen((value) => !value)}
              className={`flex w-full items-center gap-3 rounded-xl border px-3 py-2.5 text-left text-xs font-semibold transition ${pathname.startsWith("/dashboard/reports/regulatory") ? "border-white/10 bg-white/[.09] text-white" : "border-transparent text-slate-400 hover:bg-white/[.045] hover:text-white"}`}
            >
              <span className="grid h-7 w-7 place-items-center rounded-lg bg-white/[.06] text-[10px] font-black">
                BR
              </span>
              <span className="flex-1">Regulatory reporting</span>
              <span
                className={`text-[10px] transition ${regulatoryOpen ? "rotate-180" : ""}`}
              >
                ⌄
              </span>
            </button>
            {regulatoryOpen ? (
              <div className="ml-5 mt-1 space-y-1 border-l border-white/10 pl-2">
                {REGULATORY.map(([label, href]) => (
                  <Link
                    key={href}
                    href={href}
                    className={`block rounded-lg px-3 py-2 text-[11px] font-semibold ${active(href) ? "bg-white/[.08] text-white" : "text-slate-500 hover:bg-white/[.04] hover:text-slate-200"}`}
                  >
                    {label}
                  </Link>
                ))}
              </div>
            ) : null}
          </div>
        ) : null}
      </nav>

      <div className="border-t border-white/10 p-3">
        <div className="mb-2 flex items-center gap-3 rounded-xl bg-white/[.04] p-3">
          <div className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-[#0b1f3a] text-xs font-black text-[#c8a84e] ring-1 ring-white/10">
            {initials(user?.name)}
          </div>
          <div className="min-w-0 flex-1">
            <div className="truncate text-xs font-bold text-white">
              {user?.name || "User"}
            </div>
            <div className="truncate text-[10px] text-slate-500">
              {user?.role?.replace(/_/g, " ") || "Staff"}
            </div>
          </div>
        </div>
        <button
          type="button"
          onClick={logout}
          className="flex w-full items-center justify-between rounded-xl px-3 py-2.5 text-xs font-bold text-slate-400 transition hover:bg-red-500/10 hover:text-red-300"
        >
          <span>Sign out</span>
          <span>→</span>
        </button>
      </div>
    </aside>
  );
}
