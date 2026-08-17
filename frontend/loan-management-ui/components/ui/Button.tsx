"use client";

import React from "react";

type Variant =
  | "primary"
  | "secondary"
  | "danger"
  | "ghost"
  | "outline";

type Size = "xs" | "sm" | "md" | "lg";

interface Props
  extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  loading?: boolean;
  icon?: string;
}

const variants: Record<Variant, string> = {
  primary:
    "bg-[#0B1F3A] text-white shadow-[0_8px_20px_rgba(11,31,58,0.18)] hover:bg-[#16365F] hover:shadow-[0_12px_28px_rgba(11,31,58,0.22)]",
  secondary:
    "border border-slate-200 bg-white text-slate-700 shadow-sm hover:border-slate-300 hover:bg-slate-50",
  danger:
    "bg-red-600 text-white shadow-sm hover:bg-red-700 hover:shadow-md",
  ghost:
    "text-slate-600 hover:bg-slate-100 hover:text-slate-950",
  outline:
    "border border-[#0B1F3A] bg-white text-[#0B1F3A] hover:bg-[#F4F7FB]",
};

const sizes: Record<Size, string> = {
  xs: "min-h-8 px-2.5 py-1 text-xs",
  sm: "min-h-9 px-3 py-1.5 text-sm",
  md: "min-h-10 px-4 py-2 text-sm",
  lg: "min-h-12 px-6 py-3 text-base",
};

export function Button({
  variant = "primary",
  size = "md",
  loading = false,
  icon,
  children,
  disabled,
  className = "",
  type = "button",
  ...rest
}: Props) {
  return (
    <button
      type={type}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={[
        "inline-flex items-center justify-center gap-2 rounded-xl",
        "font-semibold transition-all duration-150",
        "focus:outline-none focus-visible:ring-2 focus-visible:ring-[#C9A227] focus-visible:ring-offset-2",
        "disabled:cursor-not-allowed disabled:opacity-50",
        variants[variant],
        sizes[size],
        className,
      ].join(" ")}
      {...rest}
    >
      {loading ? (
        <span
          className="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent"
          aria-hidden="true"
        />
      ) : (
        icon && <span aria-hidden="true">{icon}</span>
      )}

      {children}
    </button>
  );
}
