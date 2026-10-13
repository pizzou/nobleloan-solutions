"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { get, post } from "../../../../services/api";
import { toast } from "../../../../hooks/useToast";
import { hasRole } from "../../../../services/authService";
import { PageSpinner } from "../../../../components/ui/Skeleton";

type RecycledUser = {
  id: number;
  name: string;
  email: string;
  role?: { name?: string };
  deletedAt?: string;
  purgeAfter?: string;
  deletionReason?: string;
  purgedAt?: string | null;
};

const message = (error: unknown) => error instanceof Error ? error.message : "Request failed";
const dateText = (value?: string) => value ? new Date(value).toLocaleString() : "—";

export default function UserRecycleBinPage() {
  const authorized = hasRole("BUSINESS_OWNER");
  const [items, setItems] = useState<RecycledUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [restoring, setRestoring] = useState<number | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await get("/users/recycle-bin");
      setItems(Array.isArray(data) ? data as RecycledUser[] : []);
    } catch (error) {
      toast("error", message(error));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { if (authorized) void load(); else setLoading(false); }, [authorized, load]);

  const restore = async (user: RecycledUser) => {
    if (!window.confirm(`Restore ${user.name}? Their login will be enabled again and they must authenticate afresh.`)) return;
    setRestoring(user.id);
    try {
      await post(`/users/${user.id}/restore`, {});
      toast("success", "User restored successfully");
      await load();
    } catch (error) {
      toast("error", message(error));
    } finally {
      setRestoring(null);
    }
  };

  if (!authorized) return <div className="rounded-xl border bg-white p-6 text-sm text-red-700">Only the Business Owner can access the user recycle bin.</div>;

  return <div className="space-y-5">
    <div className="flex flex-wrap items-start justify-between gap-3">
      <div>
        <h1 className="text-xl font-bold text-gray-900">User Recycle Bin</h1>
        <p className="mt-1 text-sm text-gray-500">Deleted accounts can be restored before the 30-day deadline. After that, personal data is permanently purged while historical audit and financial references are retained.</p>
      </div>
      <Link href="/dashboard/users" className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50">Back to Users</Link>
    </div>
    <div className="overflow-hidden rounded-xl border border-gray-200 bg-white">
      {loading ? <PageSpinner /> : items.length === 0 ? <div className="p-8 text-center text-sm text-gray-500">No restorable deleted users.</div> : <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-left text-xs uppercase text-gray-500"><tr><th className="px-4 py-3">User</th><th className="px-4 py-3">Role</th><th className="px-4 py-3">Deleted</th><th className="px-4 py-3">Permanent privacy purge after</th><th className="px-4 py-3">Action</th></tr></thead>
          <tbody className="divide-y divide-gray-100">{items.map((user) => <tr key={user.id}>
            <td className="px-4 py-4"><div className="font-medium text-gray-900">{user.name}</div><div className="text-gray-500">{user.email}</div>{user.deletionReason && <div className="mt-1 max-w-md text-xs text-gray-500">{user.deletionReason}</div>}</td>
            <td className="px-4 py-4 text-gray-600">{user.role?.name ?? "—"}</td>
            <td className="px-4 py-4 text-gray-600">{dateText(user.deletedAt)}</td>
            <td className="px-4 py-4 text-gray-600">{dateText(user.purgeAfter)}</td>
            <td className="px-4 py-4"><button disabled={restoring === user.id} onClick={() => void restore(user)} className="rounded-lg bg-blue-600 px-3 py-2 text-xs font-semibold text-white hover:bg-blue-700 disabled:opacity-50">{restoring === user.id ? "Restoring…" : "Restore user"}</button></td>
          </tr>)}</tbody>
        </table>
      </div>}
    </div>
  </div>;
}
