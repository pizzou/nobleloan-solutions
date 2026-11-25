"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { get } from "@/services/api";
import { useAuth } from "@/hooks/useAuth";

type PendingAmendment = {
  id: number;
  loanId: number;
  loanReference: string;
  correctionType: "RECORDING_ERROR" | "ADDITIONAL_DISBURSEMENT" | "CONTRACT_TERMS";
  oldPrincipal: number;
  correctedPrincipal: number;
  reason: string;
  createdAt: string;
};

export default function PrincipalAmendmentsInboxPage() {
  const { isBusinessOwner } = useAuth();
  const [pending, setPending] = useState<PendingAmendment[]>([]);
  const [error, setError] = useState("");
  useEffect(() => {
    if (!isBusinessOwner) return;
    (get("/principal-amendments/pending") as Promise<PendingAmendment[]>)
      .then(setPending)
      .catch((e) => setError(e instanceof Error ? e.message : "Could not load amendment requests"));
  }, [isBusinessOwner]);
  return (
    <main className="mx-auto max-w-4xl space-y-5 p-6">
      <h1 className="text-2xl font-bold text-slate-900">Loan correction approvals</h1>
      <p className="text-sm text-slate-600">Review correction requests from imported and system-created loans. Approval is separate from financial reconciliation.</p>
      {!isBusinessOwner && <p className="text-sm text-amber-700">This inbox is restricted to the Business Owner.</p>}
      {error && <p role="alert" className="text-red-700">{error}</p>}
      {isBusinessOwner && pending.length === 0 && <p className="text-sm text-slate-500">No pending loan corrections.</p>}
      {pending.map((item) => (
        <article key={item.id} className="rounded-xl border border-slate-200 bg-white p-5">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <h2 className="font-bold">{item.loanReference} · Request #{item.id}</h2>
              <p className="mt-1 text-sm text-slate-600">{item.correctionType.replaceAll("_", " ")}</p>
              <p className="mt-1 text-sm font-semibold">RWF {Number(item.oldPrincipal).toLocaleString()} → RWF {Number(item.correctedPrincipal).toLocaleString()}</p>
            </div>
            <Link href={`/dashboard/loans/${item.loanId}/principal-amendments#correction-request-${item.id}`} className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-semibold text-white">Review request</Link>
          </div>
          <p className="mt-3 text-sm text-slate-700">{item.reason}</p>
        </article>
      ))}
    </main>
  );
}
