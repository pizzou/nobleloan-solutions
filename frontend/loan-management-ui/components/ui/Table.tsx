import React from "react";

export function Table({
  children,
  className = "",
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <div className="w-full overflow-x-auto rounded-[14px] border border-[#E2E8F0]">
      <table className={`w-full border-collapse bg-white text-sm ${className}`}>
        {children}
      </table>
    </div>
  );
}

export function Thead({
  children,
  className = "",
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return <thead className={`bg-[#F8FAFC] ${className}`}>{children}</thead>;
}

export function Th({
  children,
  className = "",
  ...props
}: React.ThHTMLAttributes<HTMLTableCellElement>) {
  return (
    <th
      {...props}
      className={`border-b border-[#E2E8F0] px-4 py-3 text-left text-[10px] font-extrabold uppercase tracking-[0.12em] text-[#64748B] ${className}`}
    >
      {children}
    </th>
  );
}

export function Tbody({
  children,
  className = "",
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <tbody className={`divide-y divide-[#EEF2F7] ${className}`}>
      {children}
    </tbody>
  );
}

export function Tr({
  children,
  className = "",
  onClick,
  ...props
}: React.HTMLAttributes<HTMLTableRowElement>) {
  return (
    <tr
      {...props}
      onClick={onClick}
      className={`
        transition-colors duration-100
        ${onClick ? "cursor-pointer hover:bg-[#F8FAFC]" : ""}
        ${className}
      `}
    >
      {children}
    </tr>
  );
}

export function Td({
  children,
  className = "",
  ...props
}: React.TdHTMLAttributes<HTMLTableCellElement>) {
  return (
    <td
      {...props}
      className={`px-4 py-3.5 align-middle text-sm text-[#334155] ${className}`}
    >
      {children}
    </td>
  );
}

export function EmptyRow({
  cols = 1,
  message = "No records found.",
}: {
  cols?: number;
  message?: string;
}) {
  return (
    <tr>
      <td colSpan={cols} className="px-6 py-16 text-center">
        <div className="flex flex-col items-center justify-center">
          <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-2xl border border-[#E2E8F0] bg-[#F8FAFC]">
            <svg
              className="h-6 w-6 text-[#94A3B8]"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.7"
              aria-hidden="true"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                d="M20 13V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v7"
              />
              <path strokeLinecap="round" strokeLinejoin="round" d="M4 13h16" />
              <path strokeLinecap="round" strokeLinejoin="round" d="M8 17h8" />
              <path strokeLinecap="round" strokeLinejoin="round" d="M10 21h4" />
            </svg>
          </div>
          <p className="text-sm font-extrabold text-[#475569]">{message}</p>
          <p className="mt-1 text-xs text-[#94A3B8]">
            Try adjusting your search or add a new borrower.
          </p>
        </div>
      </td>
    </tr>
  );
}
