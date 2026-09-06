"use client";
import React from "react";

type Variant = "primary" | "secondary" | "danger" | "ghost" | "outline";
type Size = "xs" | "sm" | "md" | "lg";

interface Props extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  loading?: boolean;
  icon?: string;
}

const variants: Record<Variant, string> = {
  primary:
    "bg-[#0B1F3A] text-white shadow-[0_6px_18px_rgba(11,31,58,0.16)] hover:bg-[#16365F] active:bg-[#07152A]",
  secondary:
    "border border-[#DCE4EF] bg-[#F8FAFC] text-[#334155] shadow-sm hover:border-[#C5D1E0] hover:bg-[#F1F5F9]",
  danger:
    "bg-[#B91C1C] text-white shadow-[0_6px_18px_rgba(185,28,28,0.16)] hover:bg-[#991B1B] active:bg-[#7F1D1D]",
  ghost: "text-[#475569] hover:bg-[#F1F5F9] hover:text-[#0B1F3A]",
  outline: "border border-[#0B1F3A] bg-white text-[#0B1F3A] hover:bg-[#F4F7FB]",
};

const sizes: Record<Size, string> = {
  xs: "min-h-8 px-2.5 py-1 text-xs",
  sm: "min-h-9 px-3 py-1.5 text-sm",
  md: "min-h-10 px-4 py-2 text-sm",
  lg: "min-h-11 px-6 py-3 text-base",
};

export function Button({
  variant = "primary",
  size = "md",
  loading,
  icon,
  children,
  disabled,
  className = "",
  ...rest
}: Props) {
  return (
    <button
      disabled={disabled || loading}
      className={`
        inline-flex items-center justify-center gap-2 rounded-xl
        font-bold tracking-[-0.01em]
        transition-all duration-150
        focus:outline-none focus-visible:ring-2 focus-visible:ring-[#F4C430] focus-visible:ring-offset-2
        disabled:cursor-not-allowed disabled:opacity-50
        ${variants[variant]}
        ${sizes[size]}
        ${className}
      `}
      {...rest}
    >
      {loading ? (
        <span className="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent" />
      ) : (
        icon && <span aria-hidden="true">{icon}</span>
      )}
      {children}
    </button>
  );
}
