"use client";
import { ReactNode, useState } from "react";
import { usePathname } from "next/navigation";
import Sidebar from "@/components/Sidebar";
import { useAuth } from "@/hooks/useAuth";
export default function DashboardLayout({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState(false);
  const pathname = usePathname();
  const { user } = useAuth();
  const title =
    pathname === "/dashboard"
      ? "Executive dashboard"
      : pathname.includes("/loans")
        ? "Loan portfolio"
        : pathname.includes("/borrowers")
          ? "Client relationships"
          : pathname.includes("/payments")
            ? "Collections"
            : pathname.includes("/accounting") || pathname.includes("/expenses")
              ? "Finance control"
              : pathname.includes("/reports")
                ? "Reports & intelligence"
                : "Operations";
  return (
    <div className="flex min-h-screen bg-[#f5f7fa]">
      <div className="hidden lg:block">
        <Sidebar />
      </div>
      {open ? (
        <div className="fixed inset-0 z-50 lg:hidden">
          <div
            className="absolute inset-0 bg-black/40"
            onClick={() => setOpen(false)}
          />
          <div className="relative h-full w-[285px]">
            <Sidebar />
          </div>
        </div>
      ) : null}
      <div className="min-w-0 flex-1">
        <header className="sticky top-0 z-30 border-b border-slate-200/80 bg-white/90 px-4 py-3 backdrop-blur-xl sm:px-6">
          <div className="mx-auto flex max-w-[1680px] items-center justify-between gap-4">
            <div className="flex items-center gap-3">
              <button
                className="grid h-9 w-9 place-items-center rounded-xl border border-slate-200 lg:hidden"
                onClick={() => setOpen(true)}
              >
                <span className="text-lg">☰</span>
              </button>
              <div>
                <div className="text-[9px] font-black uppercase tracking-[.18em] text-slate-400">
                  {user?.organizationName || "Noble Loan"}
                </div>
                <div className="mt-0.5 text-sm font-black text-[#071a2d]">
                  {title}
                </div>
              </div>
            </div>
            <div className="hidden items-center gap-2 sm:flex">
              <span className="h-2 w-2 rounded-full bg-emerald-500" />
              <span className="text-[10px] font-bold text-slate-500">
                Production environment
              </span>
            </div>
          </div>
        </header>
        <div>{children}</div>
      </div>
    </div>
  );
}
