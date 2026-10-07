"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import Link from "next/link";

import { get, taskApi } from "@/services/api";
import { useAuth } from "@/hooks/useAuth";
import { WorkflowTask } from "@/types";

interface StaffUser {
  id: number;
  name: string;
  email: string;
  status?: string;
  role?: { name?: string };
}

function dateText(value?: string | null) {
  if (!value) return "No due date";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

function badge(priority: string) {
  switch (priority.toUpperCase()) {
    case "URGENT":
      return "bg-red-100 text-red-700 border-red-200";
    case "HIGH":
      return "bg-amber-100 text-amber-700 border-amber-200";
    case "LOW":
      return "bg-slate-100 text-slate-600 border-slate-200";
    default:
      return "bg-blue-50 text-blue-700 border-blue-200";
  }
}

export default function TasksPage() {
  const { user } = useAuth();
  const [tasks, setTasks] = useState<WorkflowTask[]>([]);
  const [staff, setStaff] = useState<StaffUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const canAssign = ["ADMIN", "MANAGER", "BUSINESS_OWNER"].includes(user?.role || "");

  const [assigneeId, setAssigneeId] = useState<number | "">("");
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [priority, setPriority] = useState("NORMAL");
  const [dueAt, setDueAt] = useState("");
  const [link, setLink] = useState("");
  const [assigning, setAssigning] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await taskApi.mine(50);
      setTasks(Array.isArray(response) ? response : []);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unable to load tasks.");
    } finally {
      setLoading(false);
    }
  }, []);

  const loadStaff = useCallback(async () => {
    if (!canAssign) return;
    try {
      const response = await get("/users");
      const values = Array.isArray(response) ? response : [];
      const active = values.filter(
        (item: StaffUser) => item.status === "ACTIVE",
      );
      setStaff(active);
      if (!assigneeId && active.length) setAssigneeId(active[0].id);
    } catch {
      // Assignment form remains usable only when staff can be loaded.
    }
  }, [canAssign, assigneeId]);

  useEffect(() => {
    void load();
    void loadStaff();
  }, [load, loadStaff]);

  const assignTask = async (event: FormEvent) => {
    event.preventDefault();
    setNotice(null);
    setError(null);

    if (!assigneeId) {
      setError("Select an active user to receive the task.");
      return;
    }
    if (!title.trim()) {
      setError("Task title is required.");
      return;
    }

    setAssigning(true);
    try {
      await taskApi.create({
        assigneeId: Number(assigneeId),
        title: title.trim(),
        description: description.trim() || undefined,
        taskType: "MANUAL",
        priority,
        dueAt: dueAt ? `${dueAt}:00` : undefined,
        link: link.trim() || undefined,
      });
      setTitle("");
      setDescription("");
      setPriority("NORMAL");
      setDueAt("");
      setLink("");
      setNotice("Task assigned. The recipient will receive an in-app alert and email after the assignment is committed.");
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unable to assign task.");
    } finally {
      setAssigning(false);
    }
  };

  const start = async (id: number) => {
    setBusyId(id);
    setError(null);
    try {
      await taskApi.start(id);
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unable to start task.");
    } finally {
      setBusyId(null);
    }
  };

  const complete = async (id: number) => {
    setBusyId(id);
    setError(null);
    try {
      await taskApi.complete(id);
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unable to complete task.");
    } finally {
      setBusyId(null);
    }
  };

  return (
    <main className="space-y-6 p-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-xs font-black uppercase tracking-[0.16em] text-slate-500">
            Operations control
          </p>
          <h1 className="mt-1 text-2xl font-black text-slate-900">My Tasks</h1>
          <p className="mt-1 text-sm text-slate-500">
            Work assigned to your account, with accountable due dates and completion status.
          </p>
        </div>
        <Link
          href="/dashboard"
          className="rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm font-bold text-slate-700"
        >
          Dashboard
        </Link>
      </div>

      {error ? (
        <div className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm font-semibold text-red-700">
          {error}
        </div>
      ) : null}

      {notice ? (
        <div className="rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm font-semibold text-emerald-700">
          {notice}
        </div>
      ) : null}

      {canAssign ? (
        <form onSubmit={assignTask} className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="mb-4">
            <h2 className="text-sm font-black uppercase tracking-[0.12em] text-slate-900">Assign work</h2>
            <p className="mt-1 text-xs text-slate-500">Assignment is tenant-scoped and sent after the database transaction commits.</p>
          </div>
          <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
            <select value={assigneeId} onChange={(e) => setAssigneeId(e.target.value ? Number(e.target.value) : "")} className="rounded-xl border border-slate-200 px-3 py-2.5 text-sm">
              {staff.length === 0 ? <option value="">No active staff found</option> : null}
              {staff.map((person) => (
                <option key={person.id} value={person.id}>{person.name} · {person.role?.name || "Staff"}</option>
              ))}
            </select>
            <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Task title" maxLength={180} className="rounded-xl border border-slate-200 px-3 py-2.5 text-sm" />
            <select value={priority} onChange={(e) => setPriority(e.target.value)} className="rounded-xl border border-slate-200 px-3 py-2.5 text-sm">
              <option value="LOW">Low</option>
              <option value="NORMAL">Normal</option>
              <option value="HIGH">High</option>
              <option value="URGENT">Urgent</option>
            </select>
            <input type="datetime-local" value={dueAt} onChange={(e) => setDueAt(e.target.value)} className="rounded-xl border border-slate-200 px-3 py-2.5 text-sm" />
            <input value={link} onChange={(e) => setLink(e.target.value)} placeholder="Dashboard link (optional)" maxLength={500} className="rounded-xl border border-slate-200 px-3 py-2.5 text-sm md:col-span-2" />
            <textarea value={description} onChange={(e) => setDescription(e.target.value)} placeholder="Instructions" maxLength={4000} rows={3} className="rounded-xl border border-slate-200 px-3 py-2.5 text-sm md:col-span-2" />
          </div>
          <div className="mt-4 flex justify-end">
            <button type="submit" disabled={assigning || staff.length === 0} className="rounded-xl bg-slate-900 px-5 py-2.5 text-sm font-black text-white disabled:cursor-not-allowed disabled:opacity-40">
              {assigning ? "Assigning…" : "Assign task"}
            </button>
          </div>
        </form>
      ) : null}

      <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white">
        {loading ? (
          <div className="p-8 text-sm text-slate-500">Loading tasks…</div>
        ) : tasks.length === 0 ? (
          <div className="p-8 text-sm text-slate-500">No open tasks are assigned to you.</div>
        ) : (
          <div className="divide-y divide-slate-100">
            {tasks.map((task) => (
              <article key={task.id} className="p-5">
                <div className="flex flex-wrap items-start justify-between gap-4">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <h2 className="font-black text-slate-900">{task.title}</h2>
                      <span className={`rounded-full border px-2.5 py-1 text-[10px] font-black uppercase tracking-wide ${badge(task.priority)}`}>
                        {task.priority}
                      </span>
                      {task.overdue ? (
                        <span className="rounded-full bg-red-100 px-2.5 py-1 text-[10px] font-black uppercase tracking-wide text-red-700">Overdue</span>
                      ) : null}
                    </div>
                    {task.referenceNumber ? <p className="mt-1 text-xs font-bold text-slate-500">Reference: {task.referenceNumber}</p> : null}
                    {task.description ? <p className="mt-3 max-w-3xl text-sm leading-6 text-slate-600">{task.description}</p> : null}
                    <div className="mt-3 text-xs text-slate-500">
                      Due: <span className={task.overdue ? "font-bold text-red-700" : "font-semibold text-slate-700"}>{dateText(task.dueAt)}</span>
                      <span className="mx-2">·</span>
                      Status: <span className="font-semibold text-slate-700">{task.status.replace("_", " ")}</span>
                    </div>
                  </div>

                  <div className="flex flex-wrap items-center gap-2">
                    {task.link ? (
                      <Link href={task.link} className="rounded-xl border border-slate-200 px-3 py-2 text-xs font-bold text-slate-700">Open</Link>
                    ) : null}
                    {task.status === "OPEN" ? (
                      <button type="button" disabled={busyId === task.id} onClick={() => void start(task.id)} className="rounded-xl border border-blue-200 bg-blue-50 px-3 py-2 text-xs font-black text-blue-700 disabled:opacity-40">
                        {busyId === task.id ? "Saving…" : "Start"}
                      </button>
                    ) : null}
                    {task.status === "OPEN" || task.status === "IN_PROGRESS" ? (
                      <button type="button" disabled={busyId === task.id} onClick={() => void complete(task.id)} className="rounded-xl bg-emerald-600 px-3 py-2 text-xs font-black text-white disabled:opacity-40">
                        Complete
                      </button>
                    ) : null}
                  </div>
                </div>
              </article>
            ))}
          </div>
        )}
      </section>
    </main>
  );
}
