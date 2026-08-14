"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
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
  "PAID",
  "CLOSED",
  "REJECTED",
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

const PAGE_SIZE = 20;

const safeNumber = (value: unknown): number => {
  if (typeof value === "number") {
    return Number.isFinite(value) ? value : 0;
  }

  if (typeof value === "string") {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : 0;
  }

  if (value instanceof Number) {
    const parsed = Number(value.valueOf());
    return Number.isFinite(parsed) ? parsed : 0;
  }

  return 0;
};

const label = (value?: string | null): string =>
  value ? value.replace(/_/g, " ") : "—";

const classificationClass = (value?: string | null): string => {
  switch (value) {
    case "CURRENT":
    case "NOT_DUE":
    case "NORMAL":
      return "bg-emerald-50 text-emerald-700 border-emerald-200";

    case "WATCH":
    case "REMINDER":
      return "bg-amber-50 text-amber-700 border-amber-200";

    case "SUBSTANDARD":
    case "COLLECTION":
      return "bg-orange-50 text-orange-700 border-orange-200";

    case "DOUBTFUL":
    case "LEGAL":
    case "PAST_DUE":
      return "bg-red-50 text-red-700 border-red-200";

    case "WRITTEN_OFF":
    case "RECOVERY":
      return "bg-slate-900 text-white border-slate-900";

    default:
      return "bg-gray-100 text-gray-600 border-gray-200";
  }
};

const safeDate = (value: unknown, locale?: string): string => {
  if (!value) {
    return "—";
  }

  try {
    return formatDate(String(value), locale);
  } catch {
    return String(value);
  }
};

export default function LoansPage() {
  const [loans, setLoans] = useState<Loan[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);

  const [loading, setLoading] = useState(true);
  const [status, setStatus] = useState("");
  const [type, setType] = useState("");
  const [search, setSearch] = useState("");

  const [actionId, setActionId] = useState<number | null>(null);

  const [error, setError] = useState<string | null>(null);

  const { currency, locale, isOfficer } = useAuth();

  const router = useRouter();

  const fc = useCallback(
    (value: unknown) => formatCurrency(safeNumber(value), currency, locale),
    [currency, locale],
  );

  const load = useCallback(() => {
    let mounted = true;

    setLoading(true);
    setError(null);

    loanApi
      .list(page, PAGE_SIZE, status, type)
      .then((response: any) => {
        if (!mounted) {
          return;
        }

        const content = Array.isArray(response?.content)
          ? response.content
          : Array.isArray(response)
            ? response
            : [];

        setLoans(content);

        setTotal(
          safeNumber(
            response?.totalElements ?? response?.total ?? content.length,
          ),
        );
      })
      .catch((err: any) => {
        if (!mounted) {
          return;
        }

        console.error("Failed to load loans:", err);

        setLoans([]);

        setTotal(0);

        setError(
          err?.response?.data?.message ??
            err?.message ??
            "Unable to load the loan portfolio.",
        );
      })
      .finally(() => {
        if (mounted) {
          setLoading(false);
        }
      });

    return () => {
      mounted = false;
    };
  }, [page, status, type]);

  useEffect(() => {
    const cleanup = load();

    return cleanup;
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
      }

      if (action === "disburse") {
        await loanApi.disburse(loanId, "BANK_TRANSFER");
      }

      load();
    } catch (err: any) {
      console.error(`Loan ${action} failed:`, err);

      setError(
        err?.response?.data?.message ??
          err?.message ??
          `Unable to ${action} loan.`,
      );
    } finally {
      setActionId(null);
    }
  };

  const totalPages = Math.ceil(total / PAGE_SIZE);

  const filteredLoans = useMemo(() => {
    const query = search.trim().toLowerCase();

    if (!query) {
      return loans;
    }

    return loans.filter((loan: any) => {
      const borrower = `${loan?.borrower?.firstName ?? ""} ${
        loan?.borrower?.lastName ?? ""
      }`.toLowerCase();

      return (
        String(loan?.referenceNumber ?? "")
          .toLowerCase()
          .includes(query) ||
        borrower.includes(query) ||
        String(loan?.borrower?.nationalId ?? "")
          .toLowerCase()
          .includes(query) ||
        String(loan?.loanType ?? "")
          .toLowerCase()
          .includes(query) ||
        String(loan?.creditQuality ?? "")
          .toLowerCase()
          .includes(query)
      );
    });
  }, [loans, search]);

  return (
    <div className="space-y-5">
      <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
        <div>
          <h1 className="text-2xl font-extrabold text-gray-900">
            Loan Portfolio
          </h1>

          <p className="mt-0.5 text-sm text-gray-500">
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
        <div className="flex items-start justify-between gap-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          <span>{error}</span>

          <button
            type="button"
            onClick={() => setError(null)}
            className="font-semibold hover:text-red-900"
          >
            Dismiss
          </button>
        </div>
      )}

      <div className="flex flex-wrap items-center gap-3">
        <div className="relative">
          <span className="absolute left-3 top-1/2 -translate-y-1/2 text-sm text-gray-400">
            🔍
          </span>

          <Input
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Search loans…"
            className="w-64 pl-9"
          />
        </div>

        <Select
          value={status}
          onChange={(event) => {
            setStatus(event.target.value);
            setPage(0);
          }}
          className="w-44"
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
          className="w-48"
        >
          <option value="">All Loan Types</option>

          {TYPES.map((item) => (
            <option key={item} value={item}>
              {LOAN_TYPE_META[item]?.label ?? label(item)}
            </option>
          ))}
        </Select>

        {(status || type || search) && (
          <Button
            variant="ghost"
            size="sm"
            onClick={() => {
              setStatus("");
              setType("");
              setSearch("");
              setPage(0);
            }}
          >
            ✕ Clear
          </Button>
        )}
      </div>

      <Card>
        {loading ? (
          <div className="flex items-center justify-center py-20">
            <div className="h-8 w-8 animate-spin rounded-full border-2 border-teal-500 border-t-transparent" />
          </div>
        ) : (
          <div className="overflow-x-auto">
            <Table>
              <Thead>
                <tr>
                  <Th>Reference</Th>
                  <Th>Borrower</Th>
                  <Th>Type</Th>
                  <Th>Principal</Th>
                  <Th>Net Disbursed</Th>
                  <Th>Rate</Th>
                  <Th>Processing Fee</Th>
                  <Th>Management Fee</Th>
                  <Th>Total Repayable</Th>
                  <Th>Outstanding</Th>
                  <Th>Term</Th>
                  <Th>Credit Quality</Th>
                  <Th>Arrears</Th>
                  <Th>Days Overdue</Th>
                  <Th>Collection Stage</Th>
                  <Th>Risk</Th>
                  <Th>Status</Th>
                  <Th>Classified</Th>
                  {isOfficer && <Th>Actions</Th>}
                </tr>
              </Thead>

              <Tbody>
                {filteredLoans.length === 0 ? (
                  <EmptyRow
                    cols={isOfficer ? 19 : 18}
                    message="No loans match your filters"
                  />
                ) : (
                  filteredLoans.map((loan: any) => {
                    const totalRepayable = safeNumber(loan.totalRepayable);

                    const totalPaid = safeNumber(loan.totalPaid);

                    const outstanding = safeNumber(loan.outstandingBalance);

                    const processingFee = safeNumber(loan.processingFee);

                    const managementFee = safeNumber(loan.managementFee);

                    const netDisbursed = safeNumber(loan.netDisbursedAmount);

                    const progress =
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
                          <code className="rounded bg-gray-100 px-2 py-1 font-mono text-xs">
                            {loan.referenceNumber ?? "—"}
                          </code>
                        </Td>

                        <Td>
                          <div className="font-semibold text-sm text-gray-900">
                            {loan.borrower?.firstName ?? ""}{" "}
                            {loan.borrower?.lastName ?? ""}
                          </div>

                          <div className="text-xs text-gray-400">
                            Score: {loan.creditScoreSnapshot ?? "—"}
                          </div>
                        </Td>

                        <Td className="whitespace-nowrap text-sm">
                          {LOAN_TYPE_META[loan.loanType]?.icon}{" "}
                          {LOAN_TYPE_META[loan.loanType]?.label ??
                            label(loan.loanType)}
                        </Td>

                        <Td className="whitespace-nowrap font-bold text-gray-900">
                          {fc(loan.amount)}
                        </Td>

                        <Td className="whitespace-nowrap font-semibold text-emerald-700">
                          {fc(netDisbursed)}
                        </Td>

                        <Td className="whitespace-nowrap text-gray-600">
                          {safeNumber(loan.interestRate).toFixed(2)}%
                          <span className="ml-1 text-xs text-gray-400">
                            {loan.interestRateType === "MONTHLY"
                              ? "monthly"
                              : label(loan.interestRateType)}
                          </span>
                        </Td>

                        <Td className="whitespace-nowrap">
                          {fc(processingFee)}
                        </Td>

                        <Td className="whitespace-nowrap">
                          {fc(managementFee)}
                        </Td>

                        <Td className="whitespace-nowrap font-semibold">
                          {fc(totalRepayable)}
                        </Td>

                        <Td className="whitespace-nowrap">
                          <div className="font-bold text-gray-900">
                            {fc(outstanding)}
                          </div>

                          <div className="mt-1 h-1.5 w-24 overflow-hidden rounded-full bg-gray-100">
                            <div
                              className="h-full rounded-full bg-teal-500"
                              style={{
                                width: `${progress}%`,
                              }}
                            />
                          </div>

                          <div className="mt-1 text-[10px] text-gray-400">
                            Paid {fc(totalPaid)}
                          </div>
                        </Td>

                        <Td className="whitespace-nowrap text-gray-500">
                          {loan.durationMonths ?? "—"}
                          mo
                        </Td>

                        <Td>
                          <span
                            className={`inline-flex whitespace-nowrap rounded-full border px-2 py-1 text-[10px] font-bold uppercase tracking-wide ${classificationClass(
                              loan.creditQuality,
                            )}`}
                          >
                            {label(loan.creditQuality)}
                          </span>
                        </Td>

                        <Td>
                          <span
                            className={`inline-flex whitespace-nowrap rounded-full border px-2 py-1 text-[10px] font-bold uppercase tracking-wide ${classificationClass(
                              loan.arrearsStatus,
                            )}`}
                          >
                            {label(loan.arrearsStatus)}
                          </span>
                        </Td>

                        <Td className="text-center font-semibold">
                          {safeNumber(loan.daysOverdue)}
                        </Td>

                        <Td>
                          <span
                            className={`inline-flex whitespace-nowrap rounded-full border px-2 py-1 text-[10px] font-bold uppercase tracking-wide ${classificationClass(
                              loan.collectionsStage,
                            )}`}
                          >
                            {label(loan.collectionsStage)}
                          </span>
                        </Td>

                        <Td>
                          {loan.riskCategory ? (
                            <RiskBadge
                              category={loan.riskCategory}
                              score={loan.riskScore}
                            />
                          ) : (
                            <div>
                              <span className="text-xs text-gray-400">—</span>

                              {loan.riskScore != null && (
                                <div className="text-[10px] text-gray-400">
                                  Score: {safeNumber(loan.riskScore).toFixed(2)}
                                </div>
                              )}
                            </div>
                          )}
                        </Td>

                        <Td>
                          <StatusBadge status={loan.status} />
                        </Td>

                        <Td className="whitespace-nowrap text-xs text-gray-400">
                          {safeDate(loan.classifiedAt, locale)}
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
                                onClick={() =>
                                  router.push(`/dashboard/loans/${loan.id}`)
                                }
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
          <div className="flex flex-col gap-3 border-t border-gray-100 bg-gray-50 px-5 py-3 sm:flex-row sm:items-center sm:justify-between">
            <span className="text-xs text-gray-500">
              Showing {total === 0 ? 0 : page * PAGE_SIZE + 1}–
              {Math.min((page + 1) * PAGE_SIZE, total)} of {formatNumber(total)}
            </span>

            <div className="flex flex-wrap gap-2">
              <Button
                variant="secondary"
                size="xs"
                disabled={page === 0}
                onClick={() => setPage((current) => Math.max(0, current - 1))}
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
                onClick={() =>
                  setPage((current) => Math.min(totalPages - 1, current + 1))
                }
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
