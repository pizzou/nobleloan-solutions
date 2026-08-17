"use client";

import React from "react";

interface CardProps {
  children: React.ReactNode;
  className?: string;
  onClick?: () => void;
}

export function Card({
  children,
  className = "",
  onClick,
}: CardProps) {
  return (
    <div
      onClick={onClick}
      className={[
        "overflow-hidden rounded-2xl border border-slate-200/80 bg-white",
        "shadow-[0_8px_30px_rgba(15,23,42,0.045)]",
        "transition-all duration-200",
        onClick
          ? "cursor-pointer hover:-translate-y-0.5 hover:border-slate-300 hover:shadow-[0_18px_45px_rgba(15,23,42,0.09)]"
          : "",
        className,
      ].join(" ")}
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
    <div className="flex items-start justify-between gap-4 border-b border-slate-100 bg-white px-5 py-4 sm:px-6">
      <div className="min-w-0">
        <h3 className="truncate text-sm font-bold tracking-tight text-slate-950">
          {title}
        </h3>
        {subtitle && (
          <p className="mt-1 text-xs leading-5 text-slate-500">
            {subtitle}
          </p>
        )}
      </div>

      {action && (
        <div className="ml-4 shrink-0">
          {action}
        </div>
      )}
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
  return (
    <div className={`px-5 py-5 sm:px-6 ${className}`}>
      {children}
    </div>
  );
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
            className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl text-lg ring-1 ring-black/5"
            style={{
              backgroundColor: `${color}12`,
              color,
            }}
            aria-hidden="true"
          >
            {icon}
          </div>

          {trend != null && (
            <span
              className={[
                "rounded-full px-2.5 py-1 text-[10px] font-bold",
                trend >= 0
                  ? "bg-emerald-50 text-emerald-700"
                  : "bg-red-50 text-red-700",
              ].join(" ")}
            >
              {trend >= 0 ? "↑" : "↓"} {Math.abs(trend)}%
            </span>
          )}
        </div>

        <div className="mb-1 text-[10px] font-bold uppercase tracking-[0.16em] text-slate-400">
          {label}
        </div>

        <div className="break-words text-2xl font-black tracking-tight text-slate-950">
          {value}
        </div>

        {sub && (
          <div className="mt-1.5 text-xs leading-5 text-slate-500">
            {sub}
          </div>
        )}
      </CardBody>
    </Card>
  );
}
