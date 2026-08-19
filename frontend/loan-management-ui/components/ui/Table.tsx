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
