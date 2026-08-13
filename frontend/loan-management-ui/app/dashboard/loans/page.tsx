"use client";

import { useEffect, useState, useCallback } from "react";
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

const safeNumber = (value: unknown): number => {
  const n = typeof value === "number" ? value : Number(value);
  return Number.isFinite(n) ? n : 0;
};

const label = (value?: string | null): string =>
  value ? value.replace(/_/g, " ") : "—";

const classificationClass = (value?: string | null): string => {
  switch (value) {
    case "CURRENT":
    case "NOT_DUE":
    case "NORMAL":
      return "bg-emerald-50 text-emerald-700";
    case "WATCH":
    case "REMINDER":
      return "bg-amber-50 text-amber-700";
    case "SUBSTANDARD":
    case "COLLECTION":
      return "bg-orange-50 text-orange-700";
    case "DOUBTFUL":
    case "LEGAL":
      return "bg-red-50 text-red-700";
    case "WRITTEN_OFF":
    case "RECOVERY":
      return "bg-slate-900 text-white";
    case "PAST_DUE":
      return "bg-red-50 text-red-700";
    default:
      return "bg-gray-100 text-gray-600";
  }
};

export default function LoansPage() {
  const [loans, setLoans] = useState<Loan[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [status, setStatus] = useState("");
  const [type, setType] = useState("");
  const [actionId, setActionId] = useState<number | null>(null);
  const [search, setSearch] = useState("");
  const { currency, locale, isOfficer } = useAuth();
  const router = useRouter();
  const fc = (n?: number) => formatCurrency(safeNumber(n), currency, locale);

  const load = useCallback(() => {
    setLoading(true);

    loanApi
      .list(page, 20, status, type)
      .then((r: any) => {
        const content = Array.isArray(r?.content)
          ? r.content
          : Array.isArray(r)
            ? r
            : [];

        setLoans(content);
        setTotal(safeNumber(r?.totalElements ?? content.length));
      })
      .catch((error) => {
        console.error("Failed to load loans:", error);
        setLoans([]);
        setTotal(0);
      })
      .finally(() => setLoading(false));
  }, [page, status, type]);

  useEffect(() => {
    load();
  }, [load]);

  const quickAction = async (
    e: React.MouseEvent,
    loanId: number,
    action: string,
  ) => {
    e.stopPropagation();
    setActionId(loanId);

    try {
      if (action === "approve") await loanApi.approve(loanId);
      if (action === "disburse")
        await loanApi.disburse(loanId, "BANK_TRANSFER");
      load();
    } catch (err: any) {
      alert(err?.message ?? "Unable to complete the loan action.");
    } finally {
      setActionId(null);
    }
  };

  const totalPages = Math.ceil(total / 20);

  const filteredLoans = search.trim()
    ? loans.filter((loan) => {
        const query = search.trim().toLowerCase();
        const borrower =
          `${loan.borrower?.firstName ?? ""} ${loan.borrower?.lastName ?? ""}`.toLowerCase();
        return (
          String(loan.referenceNumber ?? "")
            .toLowerCase()
            .includes(query) ||
          borrower.includes(query) ||
          String(loan.borrower?.nationalId ?? "")
            .toLowerCase()
            .includes(query)
        );
      })
    : loans;

  return (
    <div>
      <div className="flex items-start justify-between mb-6">
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

      <div className="flex gap-3 mb-4 flex-wrap items-center">
        <div className="relative">
          <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 text-sm">
            🔍
          </span>
          <Input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search loans…"
            className="pl-9 w-52"
          />
        </div>

        <Select
          value={status}
          onChange={(e) => {
            setStatus(e.target.value);
            setPage(0);
          }}
          className="w-40"
        >
          <option value="">All Statuses</option>
          {STATUSES.map((s) => (
            <option key={s} value={s}>
              {label(s)}
            </option>
          ))}
        </Select>

        <Select
          value={type}
          onChange={(e) => {
            setType(e.target.value);
            setPage(0);
          }}
          className="w-44"
        >
          <option value="">All Types</option>
          {TYPES.map((t) => (
            <option key={t} value={t}>
              {LOAN_TYPE_META[t]?.label ?? label(t)}
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
                  <Th>Principal</Th>
                  <Th>Rate</Th>
                  <Th>Fees</Th>
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
                    cols={isOfficer ? 16 : 15}
                    message="No loans match your filters"
                  />
                ) : (
                  filteredLoans.map((loan) => {
                    const totalRepayable = safeNumber(loan.totalRepayable);
                    const totalPaid = safeNumber(loan.totalPaid);
                    const prog =
                      totalRepayable > 0
                        ? Math.min(
                            100,
                            Math.round((totalPaid / totalRepayable) * 100),
                          )
                        : 0;

                    const totalFees =
                      safeNumber(loan.processingFee) +
                      safeNumber(loan.managementFee);

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
                          {safeNumber(loan.interestRate).toFixed(2)}%{" "}
                          {loan.interestRateType === "MONTHLY" ? "mo" : ""}
                        </Td>

                        <Td className="whitespace-nowrap">
                          <div className="font-semibold text-gray-700">
                            {fc(totalFees)}
                          </div>
                          <div className="text-[10px] text-gray-400">
                            Processing {fc(loan.processingFee)} · Mgmt{" "}
                            {fc(loan.managementFee)}
                          </div>
                        </Td>

                        <Td className="whitespace-nowrap">
                          <div className="font-bold text-gray-900">
                            {fc(loan.outstandingBalance)}
                          </div>
                          <div className="text-[10px] text-gray-400">
                            Paid {fc(loan.totalPaid)}
                          </div>
                        </Td>

                        <Td className="text-gray-500 whitespace-nowrap">
                          {loan.durationMonths}mo
                        </Td>

                        <Td>
                          <span
                            className={`inline-flex rounded-full px-2 py-1 text-[10px] font-bold uppercase tracking-wide ${classificationClass(loan.creditQuality)}`}
                          >
                            {label(loan.creditQuality)}
                          </span>
                        </Td>

                        <Td>
                          <span
                            className={`inline-flex rounded-full px-2 py-1 text-[10px] font-bold uppercase tracking-wide ${classificationClass(loan.arrearsStatus)}`}
                          >
                            {label(loan.arrearsStatus)}
                          </span>
                        </Td>

                        <Td className="text-center font-semibold text-gray-700">
                          {safeNumber(loan.daysOverdue)}
                        </Td>

                        <Td>
                          <span
                            className={`inline-flex rounded-full px-2 py-1 text-[10px] font-bold uppercase tracking-wide ${classificationClass(loan.collectionsStage)}`}
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
                            <span className="text-gray-300">—</span>
                          )}
                        </Td>

                        <Td>
                          <StatusBadge status={loan.status} />
                        </Td>

                        <Td className="text-xs text-gray-400 whitespace-nowrap">
                          {formatDate(
                            loan.classifiedAt ?? loan.startDate,
                            locale,
                          )}
                        </Td>

                        {isOfficer && (
                          <Td onClick={(e) => e.stopPropagation()}>
                            <div className="flex gap-1.5">
                              {loan.status === "PENDING" && (
                                <Button
                                  size="xs"
                                  loading={actionId === loan.id}
                                  onClick={(e) =>
                                    quickAction(e, loan.id, "approve")
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
                                  onClick={(e) =>
                                    quickAction(e, loan.id, "disburse")
                                  }
                                >
                                  Disburse
                                </Button>
                              )}

                              <Button
                                size="xs"
                                variant="ghost"
                                onClick={(e) => {
                                  e.stopPropagation();
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
          <div className="flex items-center justify-between px-5 py-3 border-t border-gray-100 bg-gray-50 rounded-b-xl">
            <span className="text-xs text-gray-500">
              Showing {page * 20 + 1}–{Math.min((page + 1) * 20, total)} of{" "}
              {formatNumber(total)}
            </span>

            <div className="flex gap-2">
              <Button
                variant="secondary"
                size="xs"
                disabled={page === 0}
                onClick={() => setPage((p) => p - 1)}
              >
                ← Prev
              </Button>

              {Array.from({ length: Math.min(totalPages, 5) }, (_, i) => (
                <Button
                  key={i}
                  size="xs"
                  variant={i === page ? "primary" : "secondary"}
                  onClick={() => setPage(i)}
                >
                  {i + 1}
                </Button>
              ))}

              <Button
                variant="secondary"
                size="xs"
                disabled={page >= totalPages - 1}
                onClick={() => setPage((p) => p + 1)}
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
