"use client";

import React from "react";

export function Table({
  children,
  className = "",
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <div
      className={`overflow-x-auto rounded-2xl border border-slate-200/80 bg-white ${className}`}
    >
      <table className="w-full border-collapse text-sm">
        {children}
      </table>
    </div>
  );
}

export function Thead({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <thead className="border-b border-slate-200 bg-slate-50/80">
      {children}
    </thead>
  );
}

export function Th({
  children,
  className = "",
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <th
      className={`whitespace-nowrap px-4 py-3 text-left text-[10px] font-bold uppercase tracking-[0.14em] text-slate-400 ${className}`}
    >
      {children}
    </th>
  );
}

export function Tbody({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <tbody className="divide-y divide-slate-100 bg-white">
      {children}
    </tbody>
  );
}

export function Tr({
  children,
  onClick,
  className = "",
}: {
  children: React.ReactNode;
  onClick?: () => void;
  className?: string;
}) {
  return (
    <tr
      onClick={onClick}
      className={[
        "transition-colors",
        onClick
          ? "cursor-pointer hover:bg-slate-50"
          : "",
        className,
      ].join(" ")}
    >
      {children}
    </tr>
  );
}

export function Td({
  children,
  className = "",
  onClick,
  colSpan,
}: {
  children: React.ReactNode;
  className?: string;
  onClick?: (e: React.MouseEvent) => void;
  colSpan?: number;
}) {
  return (
    <td
      className={`px-4 py-3.5 text-slate-700 ${className}`}
      onClick={onClick}
      colSpan={colSpan}
    >
      {children}
    </td>
  );
}

export function EmptyRow({
  cols,
  message = "No data found",
}: {
  cols: number;
  message?: string;
}) {
  return (
    <Tr>
      <Td
        className="py-14 text-center text-slate-400"
        colSpan={cols}
      >
        <div
          className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-2xl bg-slate-100 text-xl"
          aria-hidden="true"
        >
          📋
        </div>
        <div className="font-medium">{message}</div>
      </Td>
    </Tr>
  );
}
