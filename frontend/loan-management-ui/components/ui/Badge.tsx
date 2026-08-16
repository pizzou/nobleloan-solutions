"use client";

import type { ReactNode } from "react";
import { LoanStatus, RiskCategory } from "@/types";
import { STATUS_COLORS, RISK_COLORS } from "@/lib/utils";

export function StatusBadge({ status }: { status: LoanStatus }) {
  return (
    <span
      className={`premium-badge ${STATUS_COLORS[status] || "bg-slate-100 text-slate-700"}`}
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
      className={`premium-badge ${RISK_COLORS[category] || "bg-slate-100 text-slate-600"}`}
    >
      {category}
      {score != null ? <span className="opacity-60">({score})</span> : null}
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
    gray: "bg-slate-100 text-slate-700 border-slate-200",
    blue: "bg-blue-50 text-blue-700 border-blue-100",
    green: "bg-emerald-50 text-emerald-700 border-emerald-100",
    red: "bg-red-50 text-red-700 border-red-100",
    yellow: "bg-amber-50 text-amber-700 border-amber-100",
    purple: "bg-violet-50 text-violet-700 border-violet-100",
    teal: "bg-teal-50 text-teal-700 border-teal-100",
  };

  return (
    <span className={`premium-badge border ${colors[color] || colors.gray}`}>
      {label}
    </span>
  );
}
