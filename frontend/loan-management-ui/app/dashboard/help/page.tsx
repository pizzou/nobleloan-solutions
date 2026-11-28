"use client";

import { Suspense } from "react";
import { useSearchParams } from "next/navigation";
import ProcedureGuide from "@/components/ProcedureGuide";
import { staffGuides } from "./guides";

const relatedWorkspaces = new Map([
  ["/dashboard/messages", "/dashboard/tasks"],
  ["/dashboard/notifications", "/dashboard/tasks"],
  ["/dashboard/users/recycle-bin", "/dashboard/loans/recycle-bin"],
  ["/dashboard/loans/principal-amendments", "/dashboard/principal-amendments"],
]);

function StaffGuideSearch() {
  const from = useSearchParams().get("from") ?? "";
  const sourcePage = relatedWorkspaces.get(from) ?? from;
  return (
    <ProcedureGuide title="Staff guide" guides={staffGuides} sourcePage={sourcePage}
      guidePath="/dashboard/help" placeholder="Search loans, settings, reports, or another task"
      footer="Available actions depend on your staff role. Ask your administrator if a required action is missing." />
  );
}

export default function StaffGuidePage() {
  return (
    <Suspense fallback={<p role="status">Loading staff guide…</p>}>
      <StaffGuideSearch />
    </Suspense>
  );
}
