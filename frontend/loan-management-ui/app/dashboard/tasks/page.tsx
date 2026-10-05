"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";

import { tasksApi, StaffTask } from "@/services/tasksService";
import { useAuth } from "@/hooks/useAuth";

function formatDue(value?: string | null) {
  if (!value) return "No due date";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

export default function TasksPage() {
  const { user } = useAuth();
  const [tasks, setTasks] = useState<StaffTask[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [claimingId, setClaimingId] = useState<number | null>(null);

  const load = useCallback(async () => {
    if (!user) return;
    try {
      setError("");
      setTasks(await tasksApi.mine());
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unable to load tasks.");
    } finally {
      setLoading(false);
    }
  }, [user]);

  useEffect(() => {
    void load();
    const timer = window.setInterval(() => void load(), 30000);
    return () => window.clearInterval(timer);
  }, [load]);

  const claim = async (id: number) => {
    try {
      setClaimingId(id);
      await tasksApi.claim(id);
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unable to claim task.");
    } finally {
      setClaimingId(null);
    }
  };

  return (
    <main className="space-y-6 p-6">
      <div>
        <p className="text-xs font-black uppercase tracking-[0.16em] text-slate-500">
          Operations
        </p>
        <h1 className="mt-1 text-2xl font-black text-slate-900">My Tasks</h1>
        <p className="mt-1 text-sm text-slate-500">
          Work items stay open until the underlying operational action is completed.
        </p>
      </div>

      {error ? (
        <div className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          {error}
        </div>
      ) : null}

      {loading ? (
        <div className="rounded-2xl border border-slate-200 bg-white p-8 text-sm text-slate-500">
          Loading tasks…
        </div>
      ) : tasks.length === 0 ? (
        <div className="rounded-2xl border border-slate-200 bg-white p-8 text-sm text-slate-500">
          No open tasks require your attention.
        </div>
      ) : (
        <div className="space-y-3">
          {tasks.map((task) => (
            <TaskRow
              key={task.id}
              task={task}
              claiming={claimingId === task.id}
              onClaim={() => void claim(task.id)}
            />
          ))}
        </div>
      )}
    </main>
  );
}

function TaskRow({
  task,
  claiming,
  onClaim,
}: {
  task: StaffTask;
  claiming: boolean;
  onClaim: () => void;
}) {
  return (
    <article
      className={
        "rounded-2xl border bg-white p-5 shadow-sm " +
        (task.overdue ? "border-red-200" : "border-slate-200")
      }
    >
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <h2 className="font-black text-slate-900">{task.title}</h2>
            <span className="rounded-full bg-slate-100 px-2 py-1 text-[10px] font-black uppercase text-slate-600">
              {task.priority}
            </span>
            {task.overdue ? (
              <span className="rounded-full bg-red-100 px-2 py-1 text-[10px] font-black uppercase text-red-700">
                overdue
              </span>
            ) : null}
          </div>
          <p className="mt-2 text-sm leading-6 text-slate-600">{task.description}</p>
          <p className="mt-2 text-xs text-slate-500">
            {task.reference || task.taskType} · Due {formatDue(task.dueAt)}
            {task.assignedUserName ? " · Assigned to " + task.assignedUserName : ""}
          </p>
        </div>

        <div className="flex items-center gap-2">
          {task.status === "OPEN" && task.assignedRole && !task.assignedUserId ? (
            <button
              type="button"
              onClick={onClaim}
              disabled={claiming}
              className="rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-black text-slate-700 disabled:opacity-40"
            >
              {claiming ? "Claiming…" : "Claim"}
            </button>
          ) : null}

          {task.actionLink ? (
            <Link
              href={task.actionLink}
              className="rounded-xl bg-[#0B1F3A] px-4 py-2.5 text-sm font-black text-white"
            >
              Open
            </Link>
          ) : null}
        </div>
      </div>
    </article>
  );
}
