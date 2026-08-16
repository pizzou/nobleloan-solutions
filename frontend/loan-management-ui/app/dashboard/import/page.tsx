"use client";

import { ChangeEvent, useEffect, useState } from "react";
import { importApi } from "@/services/api";
import { toast } from "@/hooks/useToast";
import { Button } from "@/components/ui/Button";
import { Card, CardBody, CardHeader, StatCard } from "@/components/ui/Card";
import { Pill } from "@/components/ui/Badge";
import { PageSpinner } from "@/components/ui/Skeleton";

interface RowResult {
  rowNumber: number;
  success: boolean;
  borrowerAction?: string;
  borrowerName?: string;
  loanReferenceNumber?: string;
  error?: string;
}
interface Batch {
  id: number;
  fileName: string;
  totalRows: number;
  successCount: number;
  failureCount: number;
  status: string;
  createdAt: string;
  importedBy?: { name?: string };
  processedRows?: number;
  progressPercent?: number;
}

export default function ImportLegacyLoansPage() {
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<RowResult[] | null>(null);
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [batches, setBatches] = useState<Batch[]>([]);
  const [loading, setLoading] = useState(true);

  const loadBatches = async () => {
    setLoading(true);
    try {
      const result: any = await importApi.batches();
      setBatches(Array.isArray(result) ? result : []);
    } catch (err: any) {
      toast("error", err?.message || "Unable to load import history.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadBatches();
  }, []);

  const selectFile = (event: ChangeEvent<HTMLInputElement>) => {
    setFile(event.target.files?.[0] || null);
    setPreview(null);
    setMessage("");
  };

  const downloadTemplate = async () => {
    try {
      const response: any = await importApi.template();
      const url = window.URL.createObjectURL(new Blob([response.data]));
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = "legacy-loan-import-template.csv";
      anchor.click();
      window.URL.revokeObjectURL(url);
    } catch (err: any) {
      toast("error", err?.message || "Template download failed.");
    }
  };

  const previewFile = async () => {
    if (!file) return;
    setBusy(true);
    setMessage("Validating file…");
    try {
      const result: any = await importApi.preview(file);
      const rows = Array.isArray(result) ? result : [];
      setPreview(rows);
      const valid = rows.filter((row: RowResult) => row.success).length;
      setMessage(
        `${valid} of ${rows.length} rows passed validation. Nothing has been committed.`,
      );
    } catch (err: any) {
      toast("error", err?.message || "File validation failed.");
      setMessage("");
    } finally {
      setBusy(false);
    }
  };

  const commit = async () => {
    if (!file || !preview) return;
    const valid = preview.filter((row) => row.success).length;
    if (!valid) return;
    if (
      !window.confirm(
        `Commit ${valid} validated rows? This will create borrowers and loan records.`,
      )
    )
      return;
    setBusy(true);
    setMessage("Submitting import batch…");
    try {
      const batch: any = await importApi.commit(file);
      setFile(null);
      setPreview(null);
      setMessage(`Import #${batch.id} is processing in the background.`);
      toast("success", `Import #${batch.id} queued.`);
      let current = batch;
      for (let attempt = 0; attempt < 180; attempt++) {
        await new Promise((resolve) => setTimeout(resolve, 2000));
        current = await importApi.batch(batch.id);
        setMessage(
          `Import #${batch.id}: ${current?.progressPercent ?? 0}% · ${current?.processedRows ?? 0} rows processed.`,
        );
        if (["COMPLETED", "PARTIAL", "FAILED"].includes(current?.status)) break;
      }
      if (current?.status === "COMPLETED")
        toast(
          "success",
          `Import completed: ${current.successCount}/${current.processedRows ?? current.totalRows} succeeded.`,
        );
      if (current?.status === "PARTIAL")
        toast(
          "error",
          `Import completed with ${current.failureCount} failed rows.`,
        );
      if (current?.status === "FAILED")
        toast("error", current?.errorMessage || "Import failed.");
      await loadBatches();
    } catch (err: any) {
      toast("error", err?.message || "Import failed.");
    } finally {
      setBusy(false);
    }
  };

  const last = batches[0];
  const totalRows = batches.reduce(
    (sum, batch) => sum + (batch.totalRows || 0),
    0,
  );
  const totalFailed = batches.reduce(
    (sum, batch) => sum + (batch.failureCount || 0),
    0,
  );

  return (
    <main className="premium-page pb-12">
      <div className="mx-auto max-w-[1450px] space-y-6 px-4 py-6 sm:px-6 lg:px-8">
        <section className="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <div className="premium-eyebrow">Controlled data migration</div>
            <h1 className="premium-section-title">Historical loan import</h1>
            <p className="premium-section-copy">
              Upload, validate, review and commit legacy portfolio records
              through the existing idempotent import workflow. No financial
              records are committed during preview.
            </p>
          </div>
          <Button variant="secondary" onClick={downloadTemplate}>
            Download official template
          </Button>
        </section>

        <section className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          <StatCard
            icon="B"
            label="Import batches"
            value={batches.length.toLocaleString()}
            sub="Historical migration runs"
            color="#0B1F3A"
          />
          <StatCard
            icon="R"
            label="Rows processed"
            value={totalRows.toLocaleString()}
            sub="Across visible batches"
            color="#0F766E"
          />
          <StatCard
            icon="!"
            label="Rejected rows"
            value={totalFailed.toLocaleString()}
            sub="Requires remediation"
            color="#B42318"
          />
          <StatCard
            icon="✓"
            label="Latest status"
            value={last?.status || "No imports"}
            sub={last?.fileName || "Awaiting first upload"}
            color="#C8A84E"
          />
        </section>

        <section className="grid gap-5 xl:grid-cols-[1fr_380px]">
          <Card>
            <CardHeader
              title="01 · Upload & validate"
              subtitle="CSV and XLSX files are accepted. Preview is read-only."
            />
            <CardBody>
              <label className="block cursor-pointer rounded-2xl border border-dashed border-slate-300 bg-slate-50/70 p-8 text-center transition hover:border-[#0F766E] hover:bg-emerald-50/30">
                <input
                  type="file"
                  accept=".csv,.xlsx"
                  onChange={selectFile}
                  className="sr-only"
                />
                <div className="mx-auto grid h-14 w-14 place-items-center rounded-2xl bg-[#07152A] text-sm font-black text-[#C8A84E]">
                  XLS
                </div>
                <div className="mt-4 text-sm font-black text-slate-900">
                  {file ? file.name : "Choose a legacy portfolio file"}
                </div>
                <div className="mt-1 text-xs text-slate-400">
                  Drag-and-drop styling is available; click to select a CSV or
                  XLSX file.
                </div>
              </label>
              <div className="mt-4 flex flex-wrap gap-2">
                <Button
                  variant="secondary"
                  disabled={!file || busy}
                  onClick={() => void previewFile()}
                >
                  {busy ? "Working…" : "Validate file"}
                </Button>
                {preview?.some((row) => row.success) ? (
                  <Button disabled={busy} onClick={() => void commit()}>
                    Commit validated rows
                  </Button>
                ) : null}
              </div>
              {message ? (
                <div className="mt-4 rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-xs font-semibold text-slate-600">
                  {message}
                </div>
              ) : null}
            </CardBody>
          </Card>

          <Card>
            <CardHeader
              title="Migration controls"
              subtitle="Operational safeguards"
            />
            <CardBody>
              <div className="space-y-3 text-xs">
                {[
                  [
                    "Preview is read-only",
                    "No borrowers or loans are created until commit.",
                  ],
                  [
                    "Duplicate-safe",
                    "The backend import process is designed to avoid replaying the same opening operation.",
                  ],
                  [
                    "Review before commit",
                    "Rows that fail validation remain visible for correction.",
                  ],
                  [
                    "Reconcile after import",
                    "Historical accounting should be reconciled in the accounting workspace.",
                  ],
                ].map(([title, copy]) => (
                  <div
                    key={title}
                    className="rounded-xl border border-slate-100 bg-slate-50/60 p-3"
                  >
                    <div className="font-black text-slate-800">{title}</div>
                    <div className="mt-1 leading-5 text-slate-500">{copy}</div>
                  </div>
                ))}
              </div>
            </CardBody>
          </Card>
        </section>

        {preview ? (
          <Card>
            <CardHeader
              title="02 · Validation review"
              subtitle={`${preview.length.toLocaleString()} rows returned by the server validator`}
            />
            <div className="max-h-[520px] overflow-auto">
              <table className="premium-table min-w-[850px] w-full text-sm">
                <thead>
                  <tr>
                    <th>Row</th>
                    <th>Result</th>
                    <th>Borrower</th>
                    <th>Loan reference</th>
                    <th>Detail</th>
                  </tr>
                </thead>
                <tbody>
                  {preview.map((row) => (
                    <tr
                      key={row.rowNumber}
                      className={!row.success ? "bg-red-50/40" : ""}
                    >
                      <td className="font-mono text-xs text-slate-500">
                        {row.rowNumber}
                      </td>
                      <td>
                        <Pill
                          label={row.success ? "Valid" : "Rejected"}
                          color={row.success ? "green" : "red"}
                        />
                      </td>
                      <td className="font-semibold text-slate-800">
                        {row.borrowerName || "—"}
                      </td>
                      <td className="font-mono text-xs text-slate-600">
                        {row.loanReferenceNumber || "—"}
                      </td>
                      <td className="text-xs text-slate-500">
                        {row.success
                          ? row.borrowerAction === "CREATED_NEW_BORROWER"
                            ? "New borrower will be created"
                            : "Existing borrower matched"
                          : row.error}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>
        ) : null}

        <Card>
          <CardHeader
            title="03 · Import history"
            subtitle="Background processing and migration audit trail"
          />
          {loading ? (
            <PageSpinner />
          ) : (
            <div className="overflow-x-auto">
              <table className="premium-table min-w-[900px] w-full text-sm">
                <thead>
                  <tr>
                    <th>Batch</th>
                    <th>File</th>
                    <th>Rows</th>
                    <th>Status</th>
                    <th>Imported by</th>
                    <th>Created</th>
                  </tr>
                </thead>
                <tbody>
                  {batches.map((batch) => (
                    <tr key={batch.id}>
                      <td className="font-black text-[#07152A]">#{batch.id}</td>
                      <td className="font-semibold text-slate-800">
                        {batch.fileName}
                      </td>
                      <td className="text-xs text-slate-600">
                        {batch.successCount}/{batch.totalRows} succeeded
                        {batch.failureCount
                          ? ` · ${batch.failureCount} failed`
                          : ""}
                      </td>
                      <td>
                        <Pill
                          label={batch.status}
                          color={
                            batch.status === "COMPLETED"
                              ? "green"
                              : batch.status === "FAILED"
                                ? "red"
                                : batch.status === "PARTIAL"
                                  ? "yellow"
                                  : "blue"
                          }
                        />
                        {batch.status === "PROCESSING" ? (
                          <div className="mt-2 h-1.5 w-24 overflow-hidden rounded-full bg-slate-100">
                            <div
                              className="h-full rounded-full bg-[#0F766E]"
                              style={{
                                width: `${batch.progressPercent || 0}%`,
                              }}
                            />
                          </div>
                        ) : null}
                      </td>
                      <td className="text-xs text-slate-500">
                        {batch.importedBy?.name || "—"}
                      </td>
                      <td className="text-xs text-slate-500">
                        {batch.createdAt
                          ? new Date(batch.createdAt).toLocaleString()
                          : "—"}
                      </td>
                    </tr>
                  ))}
                  {!batches.length ? (
                    <tr>
                      <td
                        colSpan={6}
                        className="py-14 text-center text-xs text-slate-400"
                      >
                        No import batches yet.
                      </td>
                    </tr>
                  ) : null}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      </div>
    </main>
  );
}
