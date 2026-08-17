"use client";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { useAuth } from "@/hooks/useAuth";
import { getUnreadCount } from "@/services/notificationsService";

const groups = [
  {
    label: "Command",
    items: [
      ["Dashboard", "/dashboard", "⌂"],
      ["Loan portfolio", "/dashboard/loans", "◈"],
      ["Borrowers", "/dashboard/borrowers", "♙"],
      ["Approvals", "/dashboard/approvals", "✓"],
    ],
  },
  {
    label: "Collections",
    items: [
      ["Payments", "/dashboard/payments", "↗"],
      ["Collections", "/dashboard/collections", "◒"],
    ],
  },
  {
    label: "Finance & control",
    items: [
      ["Accounting", "/dashboard/accounting", "▤"],
      ["Expenses", "/dashboard/expenses", "¤"],
      ["Reports", "/dashboard/reports", "▥"],
      ["Audit log", "/dashboard/audit", "⌁"],
    ],
  },
  {
    label: "Operations",
    items: [
      ["Import & reconciliation", "/dashboard/import", "⇧"],
      ["Documents", "/dashboard/documents", "▧"],
      ["Notifications", "/dashboard/notifications", "◌"],
      ["Messages", "/dashboard/messages", "✉"],
    ],
  },
  {
    label: "Administration",
    items: [
      ["Users & roles", "/dashboard/users", "♟"],
      ["Loan products", "/dashboard/products", "◆"],
      ["Settings", "/dashboard/settings", "⚙"],
    ],
  },
] as const;
const regulatory = [
  ["BNR reports", "/dashboard/reports/regulatory/bnr"],
  ["Credit bureau", "/dashboard/reports/regulatory/crb"],
  ["API keys", "/dashboard/reports/regulatory/api-keys"],
] as const;
function initials(name?: string) {
  return (name || "User")
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((x) => x[0])
    .join("")
    .toUpperCase();
}
export default function Sidebar() {
  const pathname = usePathname();
  const { user, logout, isAdmin } = useAuth();
  const [unread, setUnread] = useState(0);
  const [regOpen, setRegOpen] = useState(
    pathname.startsWith("/dashboard/reports/regulatory"),
  );
  useEffect(() => {
    let cancelled = false;
    const loadUnread = () => {
      getUnreadCount()
        .then((v) => {
          if (!cancelled) setUnread(Number(v) || 0);
        })
        .catch(() => {});
    };
    loadUnread();
    const timer = window.setInterval(loadUnread, 60000);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, []);
  const canFinance = ["ADMIN", "MANAGER", "ACCOUNTANT", "FINANCE"].includes(
    user?.role || "",
  );
  const visible = (label: string) => {
    if (["Users & roles", "Loan products"].includes(label)) return isAdmin;
    if (["Accounting"].includes(label)) return canFinance;
    return true;
  };
  const active = (href: string) =>
    pathname === href ||
    (href !== "/dashboard" && pathname.startsWith(href + "/"));
  return (
    <aside className="flex h-full w-[272px] shrink-0 flex-col bg-[#061729] text-white shadow-[20px_0_55px_rgba(6,23,41,.13)]">
      <div className="border-b border-white/10 px-5 py-5">
        <Link href="/dashboard" className="flex items-center gap-3">
          <img
            src="/logo-mark.png"
            className="h-10 w-10 rounded-xl object-contain"
            alt="Noble Loan"
          />
          <div>
            <div className="text-[10px] font-black uppercase tracking-[.22em] text-[#d7b95d]">
              Noble Loan
            </div>
            <div className="mt-0.5 text-xs font-semibold text-slate-300">
              Private lending platform
            </div>
          </div>
        </Link>
      </div>
      <nav className="min-h-0 flex-1 overflow-y-auto px-3 py-4">
        {groups.map((g) => (
          <div key={g.label} className="mb-6">
            <div className="mb-2 px-3 text-[9px] font-black uppercase tracking-[.2em] text-slate-500">
              {g.label}
            </div>
            <div className="space-y-1">
              {g.items
                .filter(([label]) => visible(label))
                .map(([label, href, icon]) => {
                  const on = active(href);
                  return (
                    <Link
                      key={href}
                      href={href}
                      className={`group flex items-center gap-3 rounded-xl border px-3 py-2.5 transition ${on ? "border-white/10 bg-white/[.09] text-white" : "border-transparent text-slate-400 hover:bg-white/[.045] hover:text-white"}`}
                    >
                      <span
                        className={`grid h-8 w-8 place-items-center rounded-lg text-xs font-black ${on ? "bg-[#d2b24f] text-[#061729]" : "bg-white/[.055] text-slate-400"}`}
                      >
                        {icon}
                      </span>
                      <span className="flex-1 text-[11px] font800 font-semibold">
                        {label}
                      </span>
                      {label === "Notifications" && unread > 0 ? (
                        <span className="rounded-full bg-[#d2b24f] px-1.5 py-0.5 text-[9px] font-black text-[#061729]">
                          {unread > 99 ? "99+" : unread}
                        </span>
                      ) : null}
                    </Link>
                  );
                })}
            </div>
          </div>
        ))}
        <div className="mb-5">
          <button
            type="button"
            onClick={() => setRegOpen((v) => !v)}
            className={`flex w-full items-center gap-3 rounded-xl border px-3 py-2.5 text-left ${pathname.startsWith("/dashboard/reports/regulatory") ? "border-white/10 bg-white/[.09] text-white" : "border-transparent text-slate-400 hover:bg-white/[.045] hover:text-white"}`}
          >
            <span className="grid h-8 w-8 place-items-center rounded-lg bg-white/[.055] text-[10px] font-black">
              BNR
            </span>
            <span className="flex-1 text-[11px] font-semibold">
              Regulatory reporting
            </span>
            <span className={regOpen ? "rotate-180" : ""}>⌄</span>
          </button>
          {regOpen && (
            <div className="ml-5 mt-1 border-l border-white/10 pl-2">
              {regulatory.map(([label, href]) => (
                <Link
                  key={href}
                  href={href}
                  className={`block rounded-lg px-3 py-2 text-[10px] font-semibold ${active(href) ? "bg-white/[.08] text-white" : "text-slate-500 hover:text-white"}`}
                >
                  {label}
                </Link>
              ))}
            </div>
          )}
        </div>
      </nav>
      <div className="border-t border-white/10 p-3">
        <div className="mb-2 flex items-center gap-3 rounded-xl bg-white/[.045] p-3">
          <div className="grid h-9 w-9 place-items-center rounded-xl bg-[#0b2a47] text-xs font-black text-[#d2b24f]">
            {initials(user?.name)}
          </div>
          <div className="min-w-0 flex-1">
            <div className="truncate text-xs font-bold">
              {user?.name || "User"}
            </div>
            <div className="truncate text-[9px] uppercase tracking-wider text-slate-500">
              {user?.role || "Staff"}
            </div>
          </div>
        </div>
        <button
          onClick={logout}
          className="w-full rounded-xl px-3 py-2 text-left text-[11px] font-bold text-slate-400 hover:bg-red-500/10 hover:text-red-300"
        >
          Sign out <span className="float-right">→</span>
        </button>
      </div>
    </aside>
  );
}
