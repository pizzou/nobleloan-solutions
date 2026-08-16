"use client";
import { useEffect, useState } from "react";
import { importApi, accountingApi } from "@/services/api";
import { Card, CardBody, CardHeader, StatCard } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
export default function ImportPage() {
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<any>(null);
  const [batches, setBatches] = useState<any[]>([]);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  async function load() {
    try {
      const r: any = await importApi.batches();
      setBatches(
        Array.isArray(r) ? r : r?.content || r?.items || r?.data || [],
      );
    } catch {}
  }
  useEffect(() => {
    void load();
  }, []);
  async function runPreview() {
    if (!file) return setError("Choose an Excel file first.");
    setBusy(true);
    setError("");
    setMessage("");
    try {
      setPreview(await importApi.preview(file));
      setMessage(
        "Validation preview completed. Review the returned result before committing.",
      );
    } catch (e: any) {
      setError(e?.message || "Preview failed.");
    } finally {
      setBusy(false);
    }
  }
  async function commit() {
    if (!file) return;
    setBusy(true);
    setError("");
    try {
      await importApi.commit(file);
      setMessage("Import committed successfully. Refreshing batches.");
      setPreview(null);
      await load();
    } catch (e: any) {
      setError(e?.message || "Commit failed.");
    } finally {
      setBusy(false);
    }
  }
  return (
    <main className="premium-page pb-14">
      <div className="mx-auto max-w-[1500px] space-y-6 px-4 py-6 sm:px-6 lg:px-8">
        <section>
          <div className="premium-eyebrow">Controlled migration</div>
          <h1 className="premium-section-title">Historical loan import</h1>
          <p className="premium-section-copy">
            A five-stage operational control: upload, validate, review, commit
            and reconcile. No historical cash movement is replayed by the
            interface.
          </p>
        </section>
        <section className="grid gap-4 sm:grid-cols-3">
          <StatCard
            icon={<span>1</span>}
            label="Upload"
            value="01"
            sub="Choose workbook"
            color="#0b2944"
          />
          <StatCard
            icon={<span>2</span>}
            label="Validate"
            value="02"
            sub="Preview and inspect"
            color="#c9a227"
          />
          <StatCard
            icon={<span>3</span>}
            label="Commit"
            value="03"
            sub="Persist validated records"
            color="#087f74"
          />
        </section>
        {error && (
          <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-xs font-bold text-red-800">
            {error}
          </div>
        )}
        {message && (
          <div className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-xs font-bold text-emerald-800">
            {message}
          </div>
        )}
        <div className="grid gap-5 lg:grid-cols-[1fr_420px]">
          <Card>
            <CardHeader
              title="Upload & validation"
              subtitle="Use the official legacy-loan workbook format."
            />
            <CardBody>
              <div className="rounded-2xl border-2 border-dashed border-slate-200 bg-slate-50 p-10 text-center">
                <div className="mx-auto grid h-14 w-14 place-items-center rounded-2xl bg-white text-xl shadow-sm">
                  ⇧
                </div>
                <h2 className="mt-4 text-lg font-black text-[#071a2d]">
                  Drop your workbook here
                </h2>
                <p className="mt-2 text-xs text-slate-500">
                  Excel .xlsx files are processed by the backend validation
                  service.
                </p>
                <input
                  className="mt-5 block w-full text-xs"
                  type="file"
                  accept=".xlsx,.xls"
                  onChange={(e) => setFile(e.target.files?.[0] || null)}
                />
                <div className="mt-5 flex flex-wrap justify-center gap-2">
                  <Button
                    variant="secondary"
                    onClick={async () => {
                      const r = await importApi.template();
                      const payload = r?.data ?? r;
                      const blob =
                        payload instanceof Blob
                          ? payload
                          : new Blob([payload ?? ""], {
                              type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            });
                      const a = document.createElement("a");
                      a.href = URL.createObjectURL(blob);
                      a.download = "legacy-loan-import-template.xlsx";
                      a.click();
                    }}
                  >
                    Download template
                  </Button>
                  <Button
                    loading={busy}
                    disabled={!file}
                    onClick={() => void runPreview()}
                  >
                    Validate workbook
                  </Button>
                </div>
              </div>
              {preview ? (
                <div className="mt-5">
                  <div className="premium-eyebrow">Validation result</div>
                  <pre className="mt-2 max-h-[420px] overflow-auto rounded-xl bg-[#071a2d] p-4 text-[10px] leading-5 text-slate-200">
                    {JSON.stringify(preview, null, 2)}
                  </pre>
                  <Button
                    className="mt-3"
                    loading={busy}
                    onClick={() => void commit()}
                  >
                    Commit validated import
                  </Button>
                </div>
              ) : null}
            </CardBody>
          </Card>
          <Card>
            <CardHeader
              title="Import history"
              subtitle="Operational batches and reconciliation state"
            />
            <CardBody>
              {batches.length ? (
                <div className="space-y-2">
                  {batches.slice(0, 12).map((b: any, i) => (
                    <div
                      key={b.id || i}
                      className="rounded-xl border border-slate-100 p-3"
                    >
                      <div className="flex justify-between">
                        <span className="text-xs font-black">
                          Batch {b.id || i + 1}
                        </span>
                        <span className="premium-badge bg-slate-100 text-slate-600">
                          {b.status || "Recorded"}
                        </span>
                      </div>
                      <div className="mt-2 text-[10px] text-slate-500">
                        {b.fileName || b.createdAt || "Historical import"}
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="rounded-xl bg-slate-50 p-5 text-xs text-slate-400">
                  No import batches returned.
                </div>
              )}
              <div className="mt-5 rounded-xl border border-amber-100 bg-amber-50 p-4">
                <div className="text-xs font-black text-amber-900">
                  Final control: reconciliation
                </div>
                <p className="mt-1 text-[10px] leading-5 text-amber-800">
                  After a successful commit, finance users should reconcile the
                  imported opening balances through the accounting workspace.
                </p>
                <Button
                  className="mt-3"
                  variant="secondary"
                  onClick={async () => {
                    setBusy(true);
                    try {
                      await accountingApi.reconcileLegacyLoans();
                      setMessage(
                        "Legacy-loan reconciliation requested successfully.",
                      );
                    } catch (e: any) {
                      setError(e?.message || "Reconciliation failed.");
                    } finally {
                      setBusy(false);
                    }
                  }}
                >
                  Reconcile opening balances
                </Button>
              </div>
            </CardBody>
          </Card>
        </div>
      </div>
    </main>
  );
}
