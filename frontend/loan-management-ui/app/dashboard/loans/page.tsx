"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { loanApi } from "@/services/api";
import { Loan, LoanStatus } from "@/types";
import { Card } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { StatusBadge, RiskBadge } from "@/components/ui/Badge";
import {
  Table,
  Thead,
  Th,
  Tbody,
  Tr,
  Td,
  EmptyRow,
} from "@/components/ui/Table";
import { Select, Input } from "@/components/ui/Form";
import {
  formatCurrency,
  formatDate,
  formatNumber,
  LOAN_TYPE_META,
} from "@/lib/utils";
import { useAuth } from "@/hooks/useAuth";

const STATUSES: LoanStatus[] = [
  "PENDING",
  "UNDER_REVIEW",
  "APPROVED",
  "ACTIVE",
  "OVERDUE",
  "DEFAULTED",
  "RESTRUCTURED",
  "WRITTEN_OFF",
  "PAID",
  "CLOSED",
  "REJECTED",
  "CANCELLED",
];

const TYPES = [
  "PERSONAL",
  "MORTGAGE",
  "AUTO",
  "BUSINESS",
  "STUDENT",
  "EMERGENCY",
  "ASSET_FINANCE",
  "SALARY_ADVANCE",
  "MICROFINANCE",
  "AGRICULTURAL",
  "TRADE_FINANCE",
  "GROUP",
];

function safeNumber(value?: number | null): number {
  return typeof value === "number" && Number.isFinite(value) ? value : 0;
}

function percent(value?: number | null): string {
  if (value == null || !Number.isFinite(value)) return "—";
  return `${value.toFixed(2)}%`;
}

function label(value?: string | null): string {
  if (!value) return "—";
  return value.replace(/_/g, " ");
}

function classificationTone(value?: string | null): string {
  switch (value) {
    case "CURRENT":
    case "NOT_DUE":
    case "NORMAL":
      return "text-emerald-700";
    case "WATCH":
    case "REMINDER":
      return "text-amber-700";
    case "SUBSTANDARD":
    case "COLLECTION":
      return "text-orange-700";
    case "DOUBTFUL":
    case "LEGAL":
      return "text-red-700";
    case "WRITTEN_OFF":
    case "RECOVERY":
      return "text-rose-800";
    case "PAST_DUE":
      return "text-red-700";
    default:
      return "text-gray-500";
  }
}

export default function LoansPage() {
  const [loans, setLoans] = useState<Loan[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [status, setStatus] = useState("");
  const [type, setType] = useState("");
  const [actionId, setActionId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const { currency, locale, isOfficer } = useAuth();
  const router = useRouter();

  const fc = (n?: number | null) =>
    formatCurrency(
      n == null || !Number.isFinite(n) ? undefined : n,
      currency,
      locale,
    );

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      const response = await loanApi.list(page, 20, status, type);

      const result = response ?? {};

      setLoans(
        Array.isArray(result)
          ? result
          : Array.isArray(result.content)
            ? result.content
            : [],
      );

      setTotal(
        Array.isArray(result)
          ? result.length
          : safeNumber(result.totalElements),
      );
    } catch (err: unknown) {
      console.error("Failed to load loan portfolio:", err);
      setLoans([]);
      setTotal(0);
      setError(
        err instanceof Error
          ? err.message
          : "Unable to load the loan portfolio.",
      );
    } finally {
      setLoading(false);
    }
  }, [page, status, type]);

  useEffect(() => {
    void load();
  }, [load]);

  const quickAction = async (
    event: React.MouseEvent,
    loanId: number,
    action: "approve" | "disburse",
  ) => {
    event.stopPropagation();
    setActionId(loanId);
    setError(null);

    try {
      if (action === "approve") {
        await loanApi.approve(loanId);
      } else {
        await loanApi.disburse(loanId, "BANK_TRANSFER");
      }

      await load();
    } catch (err: unknown) {
      console.error(`Failed to ${action} loan:`, err);
      setError(
        err instanceof Error ? err.message : `Unable to ${action} the loan.`,
      );
    } finally {
      setActionId(null);
    }
  };

  const totalPages = Math.ceil(total / 20);

  return (
    <div className="space-y-4">
      <div className="flex items-start justify-between gap-4 mb-6">
        <div>
          <h1 className="text-2xl font-extrabold text-gray-900">
            Loan Portfolio
          </h1>
          <p className="text-sm text-gray-500 mt-0.5">
            {formatNumber(total)} loans total
          </p>
        </div>

        {isOfficer && (
          <Button icon="+" onClick={() => router.push("/dashboard/loans/new")}>
            New Loan
          </Button>
        )}
      </div>

      {error && (
        <div
          role="alert"
          className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"
        >
          {error}
        </div>
      )}

      <div className="flex gap-3 mb-4 flex-wrap items-center">
        <div className="relative">
          <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 text-sm">
            🔍
          </span>
          <Input placeholder="Search loans…" className="pl-9 w-52" />
        </div>

        <Select
          value={status}
          onChange={(event) => {
            setStatus(event.target.value);
            setPage(0);
          }}
          className="w-40"
        >
          <option value="">All Statuses</option>
          {STATUSES.map((item) => (
            <option key={item} value={item}>
              {label(item)}
            </option>
          ))}
        </Select>

        <Select
          value={type}
          onChange={(event) => {
            setType(event.target.value);
            setPage(0);
          }}
          className="w-44"
        >
          <option value="">All Types</option>
          {TYPES.map((item) => (
            <option key={item} value={item}>
              {LOAN_TYPE_META[item]?.label ?? label(item)}
            </option>
          ))}
        </Select>

        {(status || type) && (
          <Button
            variant="ghost"
            size="sm"
            onClick={() => {
              setStatus("");
              setType("");
              setPage(0);
            }}
          >
            ✕ Clear
          </Button>
        )}
      </div>

      <Card>
        {loading ? (
          <div className="flex items-center justify-center py-16">
            <div className="w-8 h-8 border-2 border-teal-500 border-t-transparent rounded-full animate-spin" />
          </div>
        ) : (
          <div className="overflow-x-auto">
            <Table>
              <Thead>
                <tr>
                  <Th>Reference</Th>
                  <Th>Borrower</Th>
                  <Th>Type</Th>
                  <Th>Amount</Th>
                  <Th>Rate</Th>
                  <Th>Term</Th>
                  <Th>Progress</Th>
                  <Th>Risk</Th>
                  <Th>Classification</Th>
                  <Th>Financials</Th>
                  <Th>Status</Th>
                  <Th>Officer</Th>
                  <Th>Date</Th>
                  {isOfficer && <Th>Actions</Th>}
                </tr>
              </Thead>

              <Tbody>
                {loans.length === 0 ? (
                  <EmptyRow
                    cols={isOfficer ? 14 : 13}
                    message="No loans match your filters"
                  />
                ) : (
                  loans.map((loan) => {
                    const totalRepayable = safeNumber(loan.totalRepayable);
                    const totalPaid = safeNumber(loan.totalPaid);
                    const prog =
                      totalRepayable > 0
                        ? Math.min(
                            100,
                            Math.round((totalPaid / totalRepayable) * 100),
                          )
                        : 0;

                    return (
                      <Tr
                        key={loan.id}
                        onClick={() =>
                          router.push(`/dashboard/loans/${loan.id}`)
                        }
                      >
                        <Td>
                          <code className="text-xs bg-gray-100 px-2 py-0.5 rounded font-mono">
                            {loan.referenceNumber}
                          </code>
                        </Td>

                        <Td>
                          <div className="font-semibold text-sm text-gray-900">
                            {loan.borrower?.firstName} {loan.borrower?.lastName}
                          </div>
                          <div className="text-xs text-gray-400">
                            Score: {loan.creditScoreSnapshot ?? "—"}
                          </div>
                        </Td>

                        <Td className="text-sm whitespace-nowrap">
                          {LOAN_TYPE_META[loan.loanType]?.icon}{" "}
                          {LOAN_TYPE_META[loan.loanType]?.label ??
                            label(loan.loanType)}
                        </Td>

                        <Td className="font-bold text-gray-900 whitespace-nowrap">
                          {fc(loan.amount)}
                        </Td>

                        <Td className="text-gray-500 whitespace-nowrap">
                          {percent(loan.interestRate)}
                        </Td>

                        <Td className="text-gray-500 whitespace-nowrap">
                          {loan.durationMonths}mo
                        </Td>

                        <Td className="min-w-[130px]">
                          <div className="flex items-center gap-2">
                            <div className="flex-1 bg-gray-100 rounded-full h-1.5 overflow-hidden">
                              <div
                                className="h-1.5 rounded-full transition-all"
                                style={{
                                  width: `${prog}%`,
                                  background:
                                    prog >= 100
                                      ? "#0D9488"
                                      : prog > 50
                                        ? "#3B82F6"
                                        : "#F59E0B",
                                }}
                              />
                            </div>
                            <span className="text-xs text-gray-400 w-8 text-right">
                              {prog}%
                            </span>
                          </div>
                        </Td>

                        <Td>
                          {loan.riskCategory ? (
                            <RiskBadge
                              category={loan.riskCategory}
                              score={loan.riskScore}
                            />
                          ) : (
                            <span className="text-gray-300">—</span>
                          )}
                        </Td>

                        <Td className="min-w-[150px]">
                          <div className="space-y-0.5 text-xs">
                            <div
                              className={`font-semibold ${classificationTone(
                                loan.creditQuality,
                              )}`}
                            >
                              Credit: {label(loan.creditQuality)}
                            </div>
                            <div
                              className={classificationTone(loan.arrearsStatus)}
                            >
                              Arrears: {label(loan.arrearsStatus)}
                            </div>
                            <div
                              className={classificationTone(
                                loan.collectionsStage,
                              )}
                            >
                              Stage: {label(loan.collectionsStage)}
                            </div>
                            <div className="text-gray-500">
                              Days overdue: {loan.daysOverdue ?? 0}
                            </div>
                            <div className="text-gray-400 whitespace-nowrap">
                              Classified:{" "}
                              {formatDate(loan.classifiedAt, locale)}
                            </div>
                          </div>
                        </Td>

                        <Td className="min-w-[210px]">
                          <div className="grid grid-cols-2 gap-x-3 gap-y-1 text-xs">
                            <span className="text-gray-400">Outstanding</span>
                            <span className="font-semibold text-right text-gray-800">
                              {fc(loan.outstandingBalance)}
                            </span>

                            <span className="text-gray-400">Net disbursed</span>
                            <span className="font-semibold text-right text-gray-800">
                              {fc(
                                loan.netDisbursedAmount ?? loan.disbursedAmount,
                              )}
                            </span>

                            <span className="text-gray-400">Repayable</span>
                            <span className="font-semibold text-right text-gray-800">
                              {fc(loan.totalRepayable)}
                            </span>

                            <span className="text-gray-400">Interest</span>
                            <span className="text-right text-gray-700">
                              {fc(loan.totalInterest)}
                            </span>

                            <span className="text-gray-400">Mgmt fee</span>
                            <span className="text-right text-gray-700">
                              {fc(loan.managementFee)}
                            </span>

                            <span className="text-gray-400">
                              Processing fee
                            </span>
                            <span className="text-right text-gray-700">
                              {fc(loan.processingFee)}
                            </span>
                          </div>
                        </Td>

                        <Td>
                          <StatusBadge status={loan.status} />
                        </Td>

                        <Td className="text-xs text-gray-400 whitespace-nowrap">
                          {loan.loanOfficer?.name ?? "—"}
                        </Td>

                        <Td className="text-xs text-gray-400 whitespace-nowrap">
                          {formatDate(loan.startDate, locale)}
                        </Td>

                        {isOfficer && (
                          <Td onClick={(event) => event.stopPropagation()}>
                            <div className="flex gap-1.5">
                              {loan.status === "PENDING" && (
                                <Button
                                  size="xs"
                                  loading={actionId === loan.id}
                                  onClick={(event) =>
                                    void quickAction(event, loan.id, "approve")
                                  }
                                >
                                  Approve
                                </Button>
                              )}

                              {loan.status === "APPROVED" && (
                                <Button
                                  size="xs"
                                  variant="secondary"
                                  loading={actionId === loan.id}
                                  onClick={(event) =>
                                    void quickAction(event, loan.id, "disburse")
                                  }
                                >
                                  Disburse
                                </Button>
                              )}

                              <Button
                                size="xs"
                                variant="ghost"
                                onClick={(event) => {
                                  event.stopPropagation();
                                  router.push(`/dashboard/loans/${loan.id}`);
                                }}
                              >
                                →
                              </Button>
                            </div>
                          </Td>
                        )}
                      </Tr>
                    );
                  })
                )}
              </Tbody>
            </Table>
          </div>
        )}

        {totalPages > 1 && (
          <div className="flex items-center justify-between px-5 py-3 border-t border-gray-100 bg-gray-50 rounded-b-xl gap-3">
            <span className="text-xs text-gray-500">
              Showing {page * 20 + 1}–{Math.min((page + 1) * 20, total)} of{" "}
              {formatNumber(total)}
            </span>

            <div className="flex gap-2 flex-wrap justify-end">
              <Button
                variant="secondary"
                size="xs"
                disabled={page === 0}
                onClick={() => setPage((current) => current - 1)}
              >
                ← Prev
              </Button>

              {Array.from(
                {
                  length: Math.min(totalPages, 5),
                },
                (_, index) => (
                  <Button
                    key={index}
                    size="xs"
                    variant={index === page ? "primary" : "secondary"}
                    onClick={() => setPage(index)}
                  >
                    {index + 1}
                  </Button>
                ),
              )}

              <Button
                variant="secondary"
                size="xs"
                disabled={page >= totalPages - 1}
                onClick={() => setPage((current) => current + 1)}
              >
                Next →
              </Button>
            </div>
          </div>
        )}
      </Card>
    </div>
  );
}
