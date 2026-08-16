"use client";
import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { borrowerApi } from "@/services/api";
import { Borrower } from "@/types";
import { Button } from "@/components/ui/Button";
import { Card, CardBody, CardHeader, StatCard } from "@/components/ui/Card";
import { formatNumber } from "@/lib/utils";
const name = (b: Borrower) =>
  `${b.firstName || ""} ${b.lastName || ""}`.trim() || "Unnamed client";
export default function Borrowers() {
  const [rows, setRows] = useState<Borrower[]>([]);
  const [q, setQ] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const load = useCallback(async () => {
    setLoading(true);
    try {
      const r: any = await borrowerApi.list(0, 100, q);
      setRows(Array.isArray(r) ? r : r?.content || r?.items || r?.data || []);
    } catch (e: any) {
      setError(e?.message || "Unable to load client relationships.");
    } finally {
      setLoading(false);
    }
  }, [q]);
  useEffect(() => {
    const t = setTimeout(() => void load(), 250);
    return () => clearTimeout(t);
  }, [load]);
  const active = rows.filter((x) => x.status === "ACTIVE").length,
    verified = rows.filter((x) => x.kycStatus === "VERIFIED").length;
  const visible = useMemo(() => rows, [rows]);
  return (
    <main className="premium-page pb-14">
      <div className="mx-auto max-w-[1600px] space-y-6 px-4 py-6 sm:px-6 lg:px-8">
        <section className="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <div className="premium-eyebrow">Client relationships</div>
            <h1 className="premium-section-title">Borrower portfolio</h1>
            <p className="premium-section-copy">
              Present customers as lending relationships: identity, KYC status,
              contactability and credit context.
            </p>
          </div>
          <Button onClick={() => (location.href = "/dashboard/borrowers/new")}>
            Add client
          </Button>
        </section>
        <section className="grid gap-4 sm:grid-cols-3">
          <StatCard
            icon={<span>♙</span>}
            label="Clients in view"
            value={formatNumber(rows.length)}
            sub="Current search result"
            color="#0b2944"
          />
          <StatCard
            icon={<span>✓</span>}
            label="Active relationships"
            value={formatNumber(active)}
            sub="Active customer records"
            color="#087f74"
          />
          <StatCard
            icon={<span>◇</span>}
            label="KYC verified"
            value={formatNumber(verified)}
            sub="Verified in current view"
            color="#c9a227"
          />
        </section>
        <Card>
          <CardBody>
            <div className="flex gap-3">
              <input
                className="premium-input"
                value={q}
                onChange={(e) => setQ(e.target.value)}
                placeholder="Search name, national ID, phone or email"
              />
              <Button variant="outline" onClick={() => setQ("")}>
                Clear
              </Button>
            </div>
          </CardBody>
        </Card>
        {error && (
          <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-xs font-bold text-red-800">
            {error}
          </div>
        )}
        <Card>
          <CardHeader
            title="Relationship register"
            subtitle="Select a customer to open the full relationship workspace."
          />
          <div className="overflow-x-auto">
            <table className="premium-table">
              <thead>
                <tr>
                  <th>Client</th>
                  <th>Contact</th>
                  <th>Identity</th>
                  <th>KYC</th>
                  <th>Status</th>
                  <th>Employment</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {visible.map((b) => (
                  <tr key={b.id}>
                    <td>
                      <Link
                        href={`/dashboard/borrowers/${b.id}`}
                        className="font-black text-[#071a2d] hover:text-[#087f74]"
                      >
                        {name(b)}
                      </Link>
                      <div className="mt-1 text-[9px] text-slate-400">
                        Client #{b.id}
                      </div>
                    </td>
                    <td>
                      <div className="font-semibold">{b.phone || "—"}</div>
                      <div className="mt-1 text-[9px] text-slate-400">
                        {b.email || "No email"}
                      </div>
                    </td>
                    <td className="text-[10px]">
                      {b.nationalId || b.passportNumber || "—"}
                    </td>
                    <td>
                      <span
                        className={`premium-badge ${b.kycStatus === "VERIFIED" ? "bg-emerald-50 text-emerald-700" : b.kycStatus === "REJECTED" ? "bg-red-50 text-red-700" : "bg-amber-50 text-amber-700"}`}
                      >
                        {b.kycStatus}
                      </span>
                    </td>
                    <td>
                      <span className="premium-badge bg-slate-100 text-slate-600">
                        {b.status}
                      </span>
                    </td>
                    <td className="text-[10px]">
                      {b.employerName || b.employmentType || "—"}
                    </td>
                    <td>
                      <Link
                        href={`/dashboard/borrowers/${b.id}`}
                        className="text-[10px] font-black text-[#087f74]"
                      >
                        Open →
                      </Link>
                    </td>
                  </tr>
                ))}
                {!visible.length && !loading ? (
                  <tr>
                    <td
                      colSpan={7}
                      className="py-16 text-center text-xs text-slate-400"
                    >
                      No client relationships match the search.
                    </td>
                  </tr>
                ) : null}
              </tbody>
            </table>
          </div>
        </Card>
      </div>
    </main>
  );
}
