"use client";
import React from "react";

interface CardProps {
  children: React.ReactNode;
  className?: string;
  onClick?: () => void;
}

export function Card({ children, className = "", onClick }: CardProps) {
  return (
    <div
      onClick={onClick}
      className={`
        rounded-[14px]
        border border-[#DCE4EF]
        bg-white
        shadow-[0_8px_28px_rgba(11,31,58,0.055)]
        ${onClick ? "cursor-pointer transition-all duration-200 hover:-translate-y-0.5 hover:border-[#B9C8DA] hover:shadow-[0_16px_36px_rgba(11,31,58,0.10)]" : ""}
        ${className}
      `}
    >
      {children}
    </div>
  );
}

export function CardHeader({
  title,
  subtitle,
  action,
}: {
  title: string;
  subtitle?: string;
  action?: React.ReactNode;
}) {
  return (
    <div className="flex items-start justify-between gap-4 border-b border-[#E7EDF5] px-5 py-4 sm:px-6">
      <div className="min-w-0">
        <h3 className="text-sm font-extrabold tracking-tight text-[#172033]">
          {title}
        </h3>
        {subtitle && (
          <p className="mt-1 text-xs leading-5 text-[#64748B]">{subtitle}</p>
        )}
      </div>
      {action && <div className="ml-4 shrink-0">{action}</div>}
    </div>
  );
}

export function CardBody({
  children,
  className = "",
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return <div className={`px-5 py-5 sm:px-6 ${className}`}>{children}</div>;
}

interface StatCardProps {
  icon: string;
  label: string;
  value: string | number;
  sub?: string;
  color?: string;
  trend?: number;
}

export function StatCard({
  icon,
  label,
  value,
  sub,
  color = "#0B1F3A",
  trend,
}: StatCardProps) {
  return (
    <Card>
      <CardBody>
        <div className="mb-4 flex items-start justify-between gap-3">
          <div
            className="flex h-11 w-11 items-center justify-center rounded-xl border text-xl shadow-sm"
            style={{
              background: `${color}0D`,
              borderColor: `${color}22`,
              color,
            }}
            aria-hidden="true"
          >
            {icon}
          </div>
          {trend != null && (
            <span
              className={`rounded-full px-2.5 py-1 text-[11px] font-extrabold tabular-nums ${
                trend >= 0
                  ? "bg-[#ECFDF3] text-[#166534] ring-1 ring-inset ring-[#BBF7D0]"
                  : "bg-[#FEF2F2] text-[#991B1B] ring-1 ring-inset ring-[#FECACA]"
              }`}
            >
              {trend >= 0 ? "↑" : "↓"} {Math.abs(trend)}%
            </span>
          )}
        </div>
        <div className="mb-1 text-[10px] font-extrabold uppercase tracking-[0.14em] text-[#64748B]">
          {label}
        </div>
        <div className="text-2xl font-extrabold tracking-tight text-[#172033] tabular-nums">
          {value}
        </div>
        {sub && (
          <div className="mt-1.5 text-xs leading-5 text-[#94A3B8]">{sub}</div>
        )}
      </CardBody>
    </Card>
  );
}
