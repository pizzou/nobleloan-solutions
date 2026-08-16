"use client";

import React from "react";

interface CardProps {
  children: React.ReactNode;
  className?: string;
  onClick?: () => void;
}

export function Card({ children, className = "", onClick }: CardProps) {
  return (
    <section
      onClick={onClick}
      className={[
        "premium-card",
        onClick ? "cursor-pointer" : "",
        className,
      ].join(" ")}
    >
      {children}
    </section>
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
    <div className="premium-card-header">
      <div className="min-w-0">
        <h3 className="premium-card-title">{title}</h3>
        {subtitle ? <p className="premium-card-subtitle">{subtitle}</p> : null}
      </div>
      {action ? <div className="ml-4 shrink-0">{action}</div> : null}
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
  return <div className={`premium-card-body ${className}`}>{children}</div>;
}

interface StatCardProps {
  icon: React.ReactNode;
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
  color = "#0F766E",
  trend,
}: StatCardProps) {
  return (
    <div className="premium-stat-card">
      <div className="flex items-start justify-between gap-4">
        <div
          className="premium-stat-icon"
          style={{ backgroundColor: `${color}14`, color }}
        >
          {icon}
        </div>
        {trend != null ? (
          <span
            className={`premium-trend ${trend >= 0 ? "premium-trend-positive" : "premium-trend-negative"}`}
          >
            {trend >= 0 ? "↑" : "↓"} {Math.abs(trend)}%
          </span>
        ) : null}
      </div>
      <div className="premium-stat-label">{label}</div>
      <div className="premium-stat-value">{value}</div>
      {sub ? <div className="premium-stat-sub">{sub}</div> : null}
    </div>
  );
}
