"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import {
  CollectionBucket,
  CollectionCase,
  CollectionStats,
  getCollectionsQueue,
  getCollectionsStats,
  logCollectionAction,
  syncCollectionsQueue,
} from "@/services/collectionsService";
import { PageSpinner } from "@/components/ui/Skeleton";
import { Pill } from "@/components/ui/Badge";
import { Card, CardBody, CardHeader, StatCard } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { toast } from "@/hooks/useToast";

const BUCKET_LABEL: Record<CollectionBucket, string> = {
  CURRENT: "Current",
  DPD_1_30: "1–30 DPD",
  DPD_31_60: "31–60 DPD",
  DPD_61_90: "61–90 DPD",
  DPD_90_PLUS: "90+ DPD",
  WRITE_OFF: "Written off",
};

const BUCKET_COLOR: Record<CollectionBucket, string> = {
  CURRENT: "green",
  DPD_1_30: "yellow",
  DPD_31_60: "yellow",
  DPD_61_90: "red",
  DPD_90_PLUS: "red",
  WRITE_OFF: "gray",
};

const PRIORITY_COLOR: Record<string, string> = {
  LOW: "gray",
  MEDIUM: "blue",
  HIGH: "yellow",
  URGENT: "red",
};

const money = (value: unknown, currency = "RWF") =>
  new Intl.NumberFormat("en-RW", {
    style: "currency",
    currency,
    maximumFractionDigits: 0,
  }).format(Number(value || 0));

export default function CollectionsPage() {
  const [cases, setCases] = useState<CollectionCase[]>([]);
  const [stats, setStats] = useState<CollectionStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [bucketFilter, setBucketFilter] = useState<CollectionBucket | "">("");
  const [activeCase, setActiveCase] = useState<CollectionCase | null>(null);
  const [notes, setNotes] = useState("");
  const [promiseDate, setPromiseDate] = useState("");
  const [promiseAmount, setPromiseAmount] = useState("");
  const [busy, setBusy] = useState(false);
  const [syncing, setSyncing] = useState(false);

  const getMsg = (e: unknown) =>
    e instanceof Error ? e.message : "Something went wrong";

  const load = useCallback(async () => {
    setLoading(true);
    try {
      let [queue, summary] = await Promise.all([
        getCollectionsQueue(
          bucketFilter ? { bucket: bucketFilter } : undefined,
        ),
        getCollectionsStats(),
      ]);

      // A new overdue facility may have been classified since the last scheduled
      // synchronization. Only perform the write-side reconciliation when the
      // queue is genuinely empty, so normal navigation remains fast.
      if (queue.length === 0 && Number(summary?.totalOpenCases || 0) === 0) {
        try {
          await syncCollectionsQueue();
          [queue, summary] = await Promise.all([
            getCollectionsQueue(
              bucketFilter ? { bucket: bucketFilter } : undefined,
            ),
            getCollectionsStats(),
          ]);
        } catch {
          // The read result remains valid even when synchronization is unavailable.
        }
      }

      setCases(queue);
      setStats(summary);
    } catch (e) {
      toast("error", getMsg(e));
    } finally {
      setLoading(false);
    }
  }, [bucketFilter]);

  useEffect(() => {
    void load();
  }, [load]);

  const handleSync = async () => {
    setSyncing(true);
    try {
      await syncCollectionsQueue();
      toast("success", "Collections queue synchronized.");
      await load();
    } catch (e) {
      toast("error", getMsg(e));
    } finally {
      setSyncing(false);
    }
  };

  const handleAction = async (
    type: "CALL" | "PROMISE_TO_PAY" | "ESCALATED" | "FIELD_VISIT",
  ) => {
    if (!activeCase) return;
    setBusy(true);
    try {
      await logCollectionAction(activeCase.id, {
        actionType: type,
        notes: notes || undefined,
        promiseDate: type === "PROMISE_TO_PAY" ? promiseDate : undefined,
        promiseAmount:
          type === "PROMISE_TO_PAY" ? Number(promiseAmount) : undefined,
      });
      toast("success", "Collection action logged.");
      setActiveCase(null);
      setNotes("");
      setPromiseDate("");
      setPromiseAmount("");
      await load();
    } catch (e) {
      toast("error", getMsg(e));
    } finally {
      setBusy(false);
    }
  };

  if (loading && !stats) return <PageSpinner />;

  const buckets = Object.keys(BUCKET_LABEL) as CollectionBucket[];

  return (
    <main className="premium-page pb-16">
      <div className="mx-auto max-w-[1680px] space-y-6 px-4 py-6 sm:px-6 lg:px-8">
        <section className="premium-hero px-7 py-8 text-white sm:px-10">
          <div className="relative z-10 flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
            <div>
              <div className="premium-kicker">
                Collections · loan-linked operations
              </div>
              <h1 className="mt-2 text-4xl font-black tracking-[-.045em]">
                Collection command centre
              </h1>
              <p className="mt-3 max-w-3xl text-sm leading-7 text-slate-300">
                Every collection case is anchored to the underlying borrower and
                loan. Overdue facilities are synchronized into this queue so
                collections never becomes a disconnected list.
              </p>
            </div>
            <Button
              variant="secondary"
              loading={syncing}
              onClick={() => void handleSync()}
            >
              Synchronize overdue loans
            </Button>
          </div>
        </section>

        <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <StatCard
            icon={<span>!</span>}
            label="Open cases"
            value={String(stats?.totalOpenCases ?? 0)}
            sub="Facilities requiring collection action"
            color="#b42318"
          />
          <StatCard
            icon={<span>◆</span>}
            label="Overdue exposure"
            value={money(stats?.totalOverdueAmount)}
            sub="Outstanding amount in open cases"
            color="#087f74"
          />
          <StatCard
            icon={<span>◷</span>}
            label="Active promises"
            value={String(stats?.activePromises ?? 0)}
            sub="Promises to pay being monitored"
            color="#315b7f"
          />
          <StatCard
            icon={<span>!</span>}
            label="90+ DPD"
            value={String(stats?.casesByBucket?.DPD_90_PLUS ?? 0)}
            sub="Highest-priority delinquency"
            color="#b42318"
          />
        </section>

        <Card>
          <CardBody>
            <div className="flex flex-wrap gap-2">
              <button
                onClick={() => setBucketFilter("")}
                className={`rounded-full border px-3 py-1.5 text-[10px] font-black ${!bucketFilter ? "border-[#071a2d] bg-[#071a2d] text-white" : "border-slate-200 bg-white text-slate-600"}`}
              >
                All · {stats?.totalOpenCases ?? 0}
              </button>
              {buckets.map((bucket) => (
                <button
                  key={bucket}
                  onClick={() => setBucketFilter(bucket)}
                  className={`rounded-full border px-3 py-1.5 text-[10px] font-black ${bucketFilter === bucket ? "border-[#071a2d] bg-[#071a2d] text-white" : "border-slate-200 bg-white text-slate-600"}`}
                >
                  {BUCKET_LABEL[bucket]} · {stats?.casesByBucket?.[bucket] ?? 0}
                </button>
              ))}
            </div>
          </CardBody>
        </Card>

        <Card>
          <CardHeader
            title="Delinquency queue"
            subtitle="The loan remains the source record; the case is the controlled collection workflow."
          />
          <div className="overflow-x-auto">
            <table className="premium-table">
              <thead>
                <tr>
                  <th>Borrower</th>
                  <th>Loan</th>
                  <th>DPD</th>
                  <th>Bucket</th>
                  <th>Priority</th>
                  <th className="text-right">Overdue</th>
                  <th>Owner</th>
                  <th>Status</th>
                  <th className="text-right">Action</th>
                </tr>
              </thead>
              <tbody>
                {cases.length ? (
                  cases.map((item) => (
                    <tr key={item.id}>
                      <td>
                        <Link
                          href={
                            item.loan?.id
                              ? `/dashboard/borrowers`
                              : "/dashboard/borrowers"
                          }
                          className="font-black text-[#071a2d] hover:text-[#087f74]"
                        >
                          {item.loan?.borrower?.firstName}{" "}
                          {item.loan?.borrower?.lastName}
                        </Link>
                        <div className="mt-1 text-[9px] text-slate-400">
                          {item.loan?.borrower?.phone || "Client relationship"}
                        </div>
                      </td>
                      <td>
                        <Link
                          href={
                            item.loan?.id
                              ? `/dashboard/loans/${item.loan.id}`
                              : "/dashboard/loans"
                          }
                          className="font-black text-[#071a2d] hover:text-[#087f74]"
                        >
                          {item.loan?.referenceNumber || `#${item.loan?.id}`}
                        </Link>
                      </td>
                      <td className="font-black">{item.daysPastDue ?? 0}</td>
                      <td>
                        <Pill
                          label={BUCKET_LABEL[item.bucket]}
                          color={BUCKET_COLOR[item.bucket]}
                        />
                      </td>
                      <td>
                        <Pill
                          label={item.priority}
                          color={PRIORITY_COLOR[item.priority]}
                        />
                      </td>
                      <td className="text-right font-black tabular-nums">
                        {money(
                          item.overdueAmount,
                          item.loan?.currency || "RWF",
                        )}
                      </td>
                      <td>
                        {item.assignedAgent?.name || (
                          <span className="text-slate-400">Unassigned</span>
                        )}
                      </td>
                      <td>
                        <Pill
                          label={String(item.status).replace(/_/g, " ")}
                          color="gray"
                        />
                      </td>
                      <td className="text-right">
                        <button
                          onClick={() => setActiveCase(item)}
                          className="text-[10px] font-black text-[#087f74]"
                        >
                          Log action →
                        </button>
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan={9} className="py-16 text-center">
                      <div className="text-sm font-black text-[#071a2d]">
                        No collection cases are currently open.
                      </div>
                      <p className="mt-2 text-xs text-slate-500">
                        If the loan portfolio shows overdue facilities, use
                        synchronization to reconcile them into the collection
                        workflow.
                      </p>
                      <button
                        onClick={() => void handleSync()}
                        className="mt-4 text-[10px] font-black text-[#087f74]"
                      >
                        Synchronize overdue loans →
                      </button>
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </Card>
      </div>

      {activeCase ? (
        <div
          className="fixed inset-0 z-50 grid place-items-center bg-[#061729]/65 p-4 backdrop-blur-sm"
          onClick={() => setActiveCase(null)}
        >
          <div
            className="w-full max-w-lg rounded-3xl border border-white/10 bg-white p-6 shadow-2xl"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-start justify-between gap-4">
              <div>
                <div className="premium-eyebrow">Collection action</div>
                <h2 className="mt-1 text-xl font-black text-[#071a2d]">
                  {activeCase.loan?.borrower?.firstName}{" "}
                  {activeCase.loan?.borrower?.lastName}
                </h2>
                <p className="mt-1 text-xs text-slate-500">
                  {activeCase.loan?.referenceNumber}
                </p>
              </div>
              <button
                onClick={() => setActiveCase(null)}
                className="text-slate-400"
              >
                ✕
              </button>
            </div>
            <textarea
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="Action notes"
              rows={3}
              className="premium-input mt-5 w-full resize-none"
            />
            <div className="mt-3 grid grid-cols-2 gap-3">
              <input
                type="date"
                value={promiseDate}
                onChange={(e) => setPromiseDate(e.target.value)}
                className="premium-input"
              />
              <input
                type="number"
                value={promiseAmount}
                onChange={(e) => setPromiseAmount(e.target.value)}
                placeholder="Promise amount"
                className="premium-input"
              />
            </div>
            <div className="mt-5 grid grid-cols-2 gap-2">
              <button
                disabled={busy}
                onClick={() => void handleAction("CALL")}
                className="rounded-xl border border-slate-200 py-3 text-[10px] font-black text-[#071a2d]"
              >
                Call logged
              </button>
              <button
                disabled={busy}
                onClick={() => void handleAction("FIELD_VISIT")}
                className="rounded-xl border border-slate-200 py-3 text-[10px] font-black text-[#071a2d]"
              >
                Field visit
              </button>
              <button
                disabled={busy || !promiseDate || !promiseAmount}
                onClick={() => void handleAction("PROMISE_TO_PAY")}
                className="rounded-xl bg-[#071a2d] py-3 text-[10px] font-black text-white disabled:opacity-40"
              >
                Promise to pay
              </button>
              <button
                disabled={busy}
                onClick={() => void handleAction("ESCALATED")}
                className="rounded-xl bg-red-700 py-3 text-[10px] font-black text-white disabled:opacity-40"
              >
                Escalate
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </main>
  );
}
