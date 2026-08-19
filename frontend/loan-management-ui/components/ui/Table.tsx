import React from "react";

export function Table({
  children,
  className = "",
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <div className="w-full overflow-x-auto">
      <table className={`w-full border-collapse text-sm ${className}`}>
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
  return <thead className={`bg-gray-50/80 ${className}`}>{children}</thead>;
}

export function Th({
  children,
  className = "",
  ...props
}: React.ThHTMLAttributes<HTMLTableCellElement>) {
  return (
    <th
      {...props}
      className={`px-4 py-3 text-left text-[11px] font-bold uppercase tracking-wider text-gray-500 ${className}`}
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
    <tbody className={`divide-y divide-gray-100 ${className}`}>
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
        transition-colors
        ${onClick ? "cursor-pointer hover:bg-gray-50" : ""}
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
      className={`px-4 py-4 align-middle text-sm text-gray-700 ${className}`}
    >
      {children}
    </td>
  );
}

/* ============================================================
   EMPTY ROW
============================================================ */

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
          <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-2xl bg-slate-100">
            <svg
              className="h-6 w-6 text-slate-400"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.7"
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

          <p className="text-sm font-semibold text-slate-600">{message}</p>

          <p className="mt-1 text-xs text-slate-400">
            Try adjusting your search or add a new borrower.
          </p>
        </div>
      </td>
    </tr>
  );
}
