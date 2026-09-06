"use client";
import React from "react";

export function FormGroup({
  label,
  required,
  hint,
  error,
  children,
}: {
  label: string;
  required?: boolean;
  hint?: string;
  error?: string;
  children: React.ReactNode;
}) {
  return (
    <div className="mb-4">
      <label className="mb-1.5 block text-[10px] font-extrabold uppercase tracking-[0.13em] text-[#475569]">
        {label}
        {required && <span className="ml-1 text-[#B91C1C]">*</span>}
      </label>
      {children}
      {hint && !error && (
        <p className="mt-1.5 text-xs leading-5 text-[#94A3B8]">{hint}</p>
      )}
      {error && (
        <p
          className="mt-1.5 text-xs font-semibold leading-5 text-[#B91C1C]"
          role="alert"
        >
          {error}
        </p>
      )}
    </div>
  );
}

export function Input(props: React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <input
      {...props}
      className={`
        w-full rounded-xl border border-[#CBD5E1] bg-white px-3.5 py-2.5
        text-sm font-medium text-[#172033]
        placeholder:text-[#94A3B8]
        shadow-[0_1px_2px_rgba(15,23,42,0.03)]
        transition-[border-color,box-shadow,background-color] duration-150
        hover:border-[#A8B7C9]
        focus:border-[#16365F] focus:bg-white focus:outline-none focus:ring-4 focus:ring-[#16365F]/10
        disabled:cursor-not-allowed disabled:bg-[#F8FAFC] disabled:text-[#94A3B8]
        ${props.className || ""}
      `}
    />
  );
}

export function Select({
  children,
  ...props
}: React.SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select
      {...props}
      className={`
        w-full cursor-pointer rounded-xl border border-[#CBD5E1] bg-white px-3.5 py-2.5
        text-sm font-medium text-[#172033]
        shadow-[0_1px_2px_rgba(15,23,42,0.03)]
        transition-[border-color,box-shadow,background-color] duration-150
        hover:border-[#A8B7C9]
        focus:border-[#16365F] focus:outline-none focus:ring-4 focus:ring-[#16365F]/10
        disabled:cursor-not-allowed disabled:bg-[#F8FAFC] disabled:text-[#94A3B8]
        ${props.className || ""}
      `}
    >
      {children}
    </select>
  );
}

export function Textarea(
  props: React.TextareaHTMLAttributes<HTMLTextAreaElement>,
) {
  return (
    <textarea
      {...props}
      className={`
        min-h-[92px] w-full resize-y rounded-xl border border-[#CBD5E1] bg-white px-3.5 py-2.5
        text-sm font-medium text-[#172033]
        placeholder:text-[#94A3B8]
        shadow-[0_1px_2px_rgba(15,23,42,0.03)]
        transition-[border-color,box-shadow,background-color] duration-150
        hover:border-[#A8B7C9]
        focus:border-[#16365F] focus:bg-white focus:outline-none focus:ring-4 focus:ring-[#16365F]/10
        disabled:cursor-not-allowed disabled:bg-[#F8FAFC] disabled:text-[#94A3B8]
        ${props.className || ""}
      `}
    />
  );
}

export function FormRow({ children }: { children: React.ReactNode }) {
  return (
    <div className="grid grid-cols-1 gap-x-5 sm:grid-cols-2">{children}</div>
  );
}

export function Alert({
  type = "error",
  children,
}: {
  type?: "error" | "success" | "warning" | "info";
  children: React.ReactNode;
}) {
  const styles = {
    error: "border-[#FECACA] bg-[#FEF2F2] text-[#991B1B]",
    success: "border-[#BBF7D0] bg-[#F0FDF4] text-[#166534]",
    warning: "border-[#FDE68A] bg-[#FFFBEB] text-[#92400E]",
    info: "border-[#BFDBFE] bg-[#EFF6FF] text-[#1E40AF]",
  };
  const icons = { error: "!", success: "✓", warning: "!", info: "i" };

  return (
    <div
      className={`mb-4 flex items-start gap-3 rounded-xl border px-3.5 py-3 text-sm font-medium leading-5 ${styles[type]}`}
      role={type === "error" ? "alert" : "status"}
    >
      <span
        aria-hidden="true"
        className="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full border border-current text-[11px] font-extrabold"
      >
        {icons[type]}
      </span>
      <span>{children}</span>
    </div>
  );
}
