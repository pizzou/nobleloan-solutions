"use client";

import { useEffect, useMemo, useState } from "react";
import { contactMessageApi } from "@/services/api";
import { PageSpinner } from "@/components/ui/Skeleton";

interface ContactMsg {
  id: number;
  name: string;
  email?: string;
  phone?: string;
  subject?: string;
  message: string;
  read: boolean;
  createdAt: string;
}

type Filter = "all" | "unread" | "read";

const formatDate = (value: string) => {
  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return "Unknown date";
  }

  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(date);
};

const formatShortDate = (value: string) => {
  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return "—";
  }

  return new Intl.DateTimeFormat(undefined, {
    month: "short",
    day: "numeric",
  }).format(date);
};

const getInitials = (name: string) => {
  const parts = name.trim().split(/\s+/).filter(Boolean);

  if (!parts.length) return "?";

  return parts
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join("");
};

const getSubject = (message: ContactMsg) =>
  message.subject?.trim() || "General Inquiry";

export default function MessagesPage() {
  const [messages, setMessages] = useState<ContactMsg[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [expanded, setExpanded] = useState<number | null>(null);
  const [filter, setFilter] = useState<Filter>("all");
  const [search, setSearch] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<number | null>(null);

  const load = async (showRefreshState = false) => {
    if (showRefreshState) {
      setRefreshing(true);
    } else {
      setLoading(true);
    }

    setError(null);

    try {
      const data = await contactMessageApi.list();

      setMessages(Array.isArray(data) ? (data as ContactMsg[]) : []);
    } catch (err: unknown) {
      console.error("Failed to load contact messages", err);

      setError(
        err instanceof Error
          ? err.message
          : "Unable to load messages. Please try again.",
      );
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const unreadCount = useMemo(
    () => messages.filter((message) => !message.read).length,
    [messages],
  );

  const filteredMessages = useMemo(() => {
    const needle = search.trim().toLowerCase();

    return messages.filter((message) => {
      const matchesFilter =
        filter === "all" ||
        (filter === "unread" && !message.read) ||
        (filter === "read" && message.read);

      if (!matchesFilter) return false;

      if (!needle) return true;

      const haystack = [
        message.name,
        message.email,
        message.phone,
        message.subject,
        message.message,
      ]
        .filter(Boolean)
        .join(" ")
        .toLowerCase();

      return haystack.includes(needle);
    });
  }, [messages, filter, search]);

  const openMessage = async (message: ContactMsg) => {
    setExpanded((current) => (current === message.id ? null : message.id));

    if (!message.read) {
      try {
        await contactMessageApi.markRead(message.id);

        setMessages((previous) =>
          previous.map((item) =>
            item.id === message.id ? { ...item, read: true } : item,
          ),
        );
      } catch (err) {
        console.error("Failed to mark message as read", err);
      }
    }
  };

  const handleDelete = async (
    id: number,
    event: React.MouseEvent<HTMLButtonElement>,
  ) => {
    event.stopPropagation();

    const confirmed = window.confirm(
      "Delete this message permanently?\n\nThis action cannot be undone.",
    );

    if (!confirmed) return;

    setDeletingId(id);

    try {
      await contactMessageApi.delete(id);

      setMessages((previous) =>
        previous.filter((message) => message.id !== id),
      );

      setExpanded((current) => (current === id ? null : current));
    } catch (err: unknown) {
      window.alert(
        err instanceof Error ? err.message : "Could not delete this message.",
      );
    } finally {
      setDeletingId(null);
    }
  };

  if (loading) {
    return (
      <div className="flex min-h-[420px] items-center justify-center">
        <PageSpinner />
      </div>
    );
  }

  return (
    <div className="min-h-full bg-slate-50/50 pb-10">
      <div className="mx-auto max-w-[1180px] space-y-6">
        {/* Header */}
        <section className="rounded-3xl border border-slate-200/80 bg-slate-950 p-6 text-white shadow-[0_18px_50px_rgba(15,23,42,0.12)] sm:p-8">
          <div className="flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
            <div>
              <div className="mb-3 inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/5 px-3 py-1.5 text-[10px] font-bold uppercase tracking-[0.18em] text-teal-200">
                <span className="h-1.5 w-1.5 rounded-full bg-teal-400" />
                Customer Communications
              </div>

              <h1 className="text-3xl font-black tracking-tight sm:text-4xl">
                Messages
              </h1>

              <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-300">
                Manage enquiries and customer messages submitted through your
                public website.
              </p>
            </div>

            <button
              type="button"
              onClick={() => void load(true)}
              disabled={refreshing}
              className="inline-flex h-10 items-center justify-center rounded-xl border border-white/10 bg-white/5 px-4 text-xs font-bold text-white transition hover:bg-white/10 disabled:cursor-not-allowed disabled:opacity-60"
            >
              <svg
                className={`mr-2 h-4 w-4 ${refreshing ? "animate-spin" : ""}`}
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                aria-hidden="true"
              >
                <path
                  d="M20 11a8.1 8.1 0 0 0-15.5-2M4 5v4h4"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
                <path
                  d="M4 13a8.1 8.1 0 0 0 15.5 2M20 19v-4h-4"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              </svg>
              {refreshing ? "Refreshing…" : "Refresh"}
            </button>
          </div>

          {/* Header metrics */}
          <div className="mt-8 grid gap-3 sm:grid-cols-3">
            <div className="rounded-2xl border border-white/10 bg-white/5 p-4">
              <div className="text-[10px] font-bold uppercase tracking-[0.16em] text-slate-400">
                Total messages
              </div>
              <div className="mt-2 text-2xl font-black">
                {messages.length.toLocaleString()}
              </div>
              <div className="mt-1 text-xs text-slate-400">
                All website enquiries
              </div>
            </div>

            <div className="rounded-2xl border border-teal-400/20 bg-teal-400/10 p-4">
              <div className="text-[10px] font-bold uppercase tracking-[0.16em] text-teal-200">
                Unread
              </div>
              <div className="mt-2 text-2xl font-black text-white">
                {unreadCount.toLocaleString()}
              </div>
              <div className="mt-1 text-xs text-teal-100/70">
                Requiring attention
              </div>
            </div>

            <div className="rounded-2xl border border-white/10 bg-white/5 p-4">
              <div className="text-[10px] font-bold uppercase tracking-[0.16em] text-slate-400">
                Read
              </div>
              <div className="mt-2 text-2xl font-black">
                {(messages.length - unreadCount).toLocaleString()}
              </div>
              <div className="mt-1 text-xs text-slate-400">
                Previously reviewed
              </div>
            </div>
          </div>
        </section>

        {/* Error */}
        {error ? (
          <div
            role="alert"
            className="flex flex-col gap-3 rounded-2xl border border-red-200 bg-red-50 px-4 py-4 text-sm text-red-900 sm:flex-row sm:items-center sm:justify-between"
          >
            <div>
              <div className="font-bold">Unable to load messages</div>
              <div className="mt-1 text-xs text-red-700">{error}</div>
            </div>

            <button
              type="button"
              onClick={() => void load()}
              className="rounded-lg border border-red-200 bg-white px-3 py-2 text-xs font-bold text-red-700 transition hover:bg-red-100"
            >
              Try again
            </button>
          </div>
        ) : null}

        {/* Inbox */}
        <section className="overflow-hidden rounded-3xl border border-slate-200/80 bg-white shadow-sm">
          {/* Toolbar */}
          <div className="border-b border-slate-100 p-5 sm:p-6">
            <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
              <div>
                <h2 className="text-base font-bold text-slate-950">
                  Customer inbox
                </h2>
                <p className="mt-1 text-xs text-slate-400">
                  {filteredMessages.length} of {messages.length} message
                  {messages.length === 1 ? "" : "s"} shown
                </p>
              </div>

              <div className="flex flex-col gap-2 sm:flex-row">
                {/* Search */}
                <div className="relative min-w-0 sm:min-w-[280px]">
                  <svg
                    className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                    aria-hidden="true"
                  >
                    <circle cx="11" cy="11" r="7" />
                    <path d="m20 20-4-4" strokeLinecap="round" />
                  </svg>

                  <input
                    value={search}
                    onChange={(event) => setSearch(event.target.value)}
                    placeholder="Search messages…"
                    aria-label="Search messages"
                    className="h-10 w-full rounded-xl border border-slate-200 bg-slate-50 pl-9 pr-3 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-teal-500 focus:bg-white focus:ring-4 focus:ring-teal-500/10"
                  />
                </div>

                {/* Filter */}
                <div className="flex rounded-xl border border-slate-200 bg-slate-50 p-1">
                  {(
                    [
                      ["all", "All"],
                      ["unread", "Unread"],
                      ["read", "Read"],
                    ] as const
                  ).map(([value, label]) => (
                    <button
                      key={value}
                      type="button"
                      onClick={() => setFilter(value)}
                      className={`rounded-lg px-3 py-1.5 text-xs font-bold transition ${
                        filter === value
                          ? "bg-white text-slate-900 shadow-sm"
                          : "text-slate-500 hover:text-slate-800"
                      }`}
                    >
                      {label}
                      {value === "unread" && unreadCount > 0 ? (
                        <span
                          className={`ml-1.5 rounded-full px-1.5 py-0.5 text-[9px] ${
                            filter === value
                              ? "bg-teal-100 text-teal-700"
                              : "bg-slate-200 text-slate-500"
                          }`}
                        >
                          {unreadCount}
                        </span>
                      ) : null}
                    </button>
                  ))}
                </div>
              </div>
            </div>
          </div>

          {/* Empty state */}
          {filteredMessages.length === 0 ? (
            <div className="px-6 py-20 text-center">
              <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-2xl border border-slate-200 bg-slate-50 text-slate-400">
                <svg
                  className="h-7 w-7"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="1.7"
                  aria-hidden="true"
                >
                  <path
                    d="M4 5.5A2.5 2.5 0 0 1 6.5 3h11A2.5 2.5 0 0 1 20 5.5v8A2.5 2.5 0 0 1 17.5 16H10l-4.5 4v-4h-1A2.5 2.5 0 0 1 2 13.5v-8A2.5 2.5 0 0 1 4.5 3"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  />
                </svg>
              </div>

              <h3 className="mt-4 text-sm font-bold text-slate-900">
                {messages.length === 0
                  ? "No messages yet"
                  : "No messages match your filters"}
              </h3>

              <p className="mx-auto mt-1 max-w-md text-sm leading-6 text-slate-500">
                {messages.length === 0
                  ? "Messages submitted through your public website contact form will appear here."
                  : "Try changing the search term or selecting a different message filter."}
              </p>

              {messages.length > 0 && (search || filter !== "all") ? (
                <button
                  type="button"
                  onClick={() => {
                    setSearch("");
                    setFilter("all");
                  }}
                  className="mt-5 rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-xs font-bold text-slate-700 shadow-sm transition hover:bg-slate-50"
                >
                  Clear filters
                </button>
              ) : null}
            </div>
          ) : (
            <div className="divide-y divide-slate-100">
              {filteredMessages.map((message) => {
                const isExpanded = expanded === message.id;
                const isDeleting = deletingId === message.id;

                return (
                  <article
                    key={message.id}
                    className={`group transition ${
                      !message.read
                        ? "bg-teal-50/30"
                        : "bg-white hover:bg-slate-50/60"
                    }`}
                  >
                    <button
                      type="button"
                      onClick={() => void openMessage(message)}
                      aria-expanded={isExpanded}
                      className="w-full px-5 py-5 text-left outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-teal-500 sm:px-6"
                    >
                      <div className="flex items-start gap-4">
                        {/* Avatar */}
                        <div
                          className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-xl text-xs font-black ${
                            message.read
                              ? "bg-slate-100 text-slate-600"
                              : "bg-teal-100 text-teal-700"
                          }`}
                        >
                          {getInitials(message.name)}
                        </div>

                        <div className="min-w-0 flex-1">
                          <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                            <div className="min-w-0">
                              <div className="flex flex-wrap items-center gap-2">
                                {!message.read ? (
                                  <span className="inline-flex items-center gap-1.5 rounded-full bg-teal-100 px-2 py-0.5 text-[9px] font-bold uppercase tracking-wider text-teal-700">
                                    <span className="h-1.5 w-1.5 rounded-full bg-teal-500" />
                                    Unread
                                  </span>
                                ) : null}

                                <h3
                                  className={`truncate text-sm ${
                                    message.read
                                      ? "font-semibold text-slate-800"
                                      : "font-bold text-slate-950"
                                  }`}
                                >
                                  {getSubject(message)}
                                </h3>
                              </div>

                              <div className="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-slate-400">
                                <span className="font-medium text-slate-600">
                                  {message.name}
                                </span>
                                {message.email ? (
                                  <>
                                    <span>•</span>
                                    <span>{message.email}</span>
                                  </>
                                ) : null}
                              </div>
                            </div>

                            <div className="flex shrink-0 items-center gap-3 text-xs text-slate-400">
                              <time
                                dateTime={message.createdAt}
                                title={formatDate(message.createdAt)}
                              >
                                {formatShortDate(message.createdAt)}
                              </time>

                              <svg
                                className={`h-4 w-4 transition-transform ${
                                  isExpanded ? "rotate-180" : ""
                                }`}
                                viewBox="0 0 24 24"
                                fill="none"
                                stroke="currentColor"
                                strokeWidth="2"
                                aria-hidden="true"
                              >
                                <path
                                  d="m6 9 6 6 6-6"
                                  strokeLinecap="round"
                                  strokeLinejoin="round"
                                />
                              </svg>
                            </div>
                          </div>

                          <p
                            className={`mt-3 text-sm leading-6 ${
                              isExpanded
                                ? "text-slate-600"
                                : "line-clamp-2 text-slate-500"
                            }`}
                          >
                            {message.message}
                          </p>
                        </div>
                      </div>
                    </button>

                    {/* Expanded details */}
                    {isExpanded ? (
                      <div className="px-5 pb-5 sm:px-6">
                        <div className="ml-0 rounded-2xl border border-slate-200 bg-slate-50/70 p-4 sm:ml-[60px]">
                          <div className="grid gap-4 sm:grid-cols-2">
                            {message.email ? (
                              <div>
                                <div className="text-[10px] font-bold uppercase tracking-[0.14em] text-slate-400">
                                  Email
                                </div>
                                <a
                                  href={`mailto:${message.email}`}
                                  onClick={(event) => event.stopPropagation()}
                                  className="mt-1 block break-all text-sm font-semibold text-teal-700 hover:text-teal-800 hover:underline"
                                >
                                  {message.email}
                                </a>
                              </div>
                            ) : null}

                            {message.phone ? (
                              <div>
                                <div className="text-[10px] font-bold uppercase tracking-[0.14em] text-slate-400">
                                  Phone
                                </div>
                                <a
                                  href={`tel:${message.phone}`}
                                  onClick={(event) => event.stopPropagation()}
                                  className="mt-1 block text-sm font-semibold text-slate-700 hover:text-teal-700"
                                >
                                  {message.phone}
                                </a>
                              </div>
                            ) : null}

                            <div>
                              <div className="text-[10px] font-bold uppercase tracking-[0.14em] text-slate-400">
                                Received
                              </div>
                              <time
                                dateTime={message.createdAt}
                                className="mt-1 block text-sm font-semibold text-slate-700"
                              >
                                {formatDate(message.createdAt)}
                              </time>
                            </div>

                            <div>
                              <div className="text-[10px] font-bold uppercase tracking-[0.14em] text-slate-400">
                                Status
                              </div>
                              <div className="mt-1">
                                <span
                                  className={`inline-flex rounded-full px-2.5 py-1 text-[10px] font-bold ${
                                    message.read
                                      ? "bg-slate-200 text-slate-600"
                                      : "bg-teal-100 text-teal-700"
                                  }`}
                                >
                                  {message.read ? "Read" : "Unread"}
                                </span>
                              </div>
                            </div>
                          </div>

                          <div className="mt-5 border-t border-slate-200 pt-4">
                            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                              <div className="text-xs text-slate-400">
                                Submitted through the public website contact
                                form.
                              </div>

                              <button
                                type="button"
                                disabled={isDeleting}
                                onClick={(event) =>
                                  void handleDelete(message.id, event)
                                }
                                className="inline-flex items-center justify-center rounded-lg border border-red-200 bg-white px-3 py-2 text-xs font-bold text-red-600 transition hover:bg-red-50 disabled:cursor-not-allowed disabled:opacity-50"
                              >
                                <svg
                                  className="mr-1.5 h-3.5 w-3.5"
                                  viewBox="0 0 24 24"
                                  fill="none"
                                  stroke="currentColor"
                                  strokeWidth="2"
                                  aria-hidden="true"
                                >
                                  <path
                                    d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"
                                    strokeLinecap="round"
                                    strokeLinejoin="round"
                                  />
                                </svg>
                                {isDeleting ? "Deleting…" : "Delete message"}
                              </button>
                            </div>
                          </div>
                        </div>
                      </div>
                    ) : null}
                  </article>
                );
              })}
            </div>
          )}
        </section>

        <footer className="border-t border-slate-200 pt-5 text-xs leading-5 text-slate-400">
          Customer messages are submitted through the public website and are
          intended for authorised staff handling customer enquiries.
        </footer>
      </div>
    </div>
  );
}
