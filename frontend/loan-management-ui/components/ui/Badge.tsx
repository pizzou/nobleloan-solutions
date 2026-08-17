"use client";

import type { ReactNode } from "react";
import { LoanStatus, RiskCategory } from "@/types";
import { STATUS_COLORS, RISK_COLORS } from "@/lib/utils";

export function StatusBadge({
  status,
}: {
  status: LoanStatus;
}) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-[10px] font-bold uppercase tracking-wide ${
        STATUS_COLORS[status] ||
        "border-slate-200 bg-slate-100 text-slate-700"
      }`}
    >
      <span className="h-1.5 w-1.5 rounded-full bg-current opacity-70" />
      {status.replace(/_/g, " ")}
    </span>
  );
}

export function RiskBadge({
  category,
  score,
}: {
  category: RiskCategory;
  score?: number;
}) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[10px] font-bold uppercase tracking-wide ${
        RISK_COLORS[category] ||
        "bg-slate-100 text-slate-600"
      }`}
    >
      {category}
      {score != null && (
        <span className="opacity-60">({score})</span>
      )}
    </span>
  );
}

export function Pill({
  label,
  color = "gray",
}: {
  label: ReactNode;
  color?: string;
}) {
  const colors: Record<string, string> = {
    gray: "bg-slate-100 text-slate-700",
    blue: "bg-blue-50 text-blue-700",
    green: "bg-emerald-50 text-emerald-700",
    red: "bg-red-50 text-red-700",
    yellow: "bg-amber-50 text-amber-700",
    purple: "bg-violet-50 text-violet-700",
    teal: "bg-teal-50 text-teal-700",
  };

  return (
    <span
      className={`inline-flex rounded-full px-2.5 py-1 text-[10px] font-bold uppercase tracking-wide ${
        colors[color] || colors.gray
      }`}
    >
      {label}
    </span>
  );
}
