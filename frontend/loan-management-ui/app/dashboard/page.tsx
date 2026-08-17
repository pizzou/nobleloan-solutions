"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";

import { accountingApi, expenseApi, loanApi } from "@/services/api";
import type { DashboardStats, Loan } from "@/types";
import { useAuth } from "@/hooks/useAuth";

import { Button } from "@/components/ui/Button";
import { Card, CardBody, CardHeader, StatCard } from "@/components/ui/Card";
import { PageSpinner } from "@/components/ui/Skeleton";

import { formatCurrency, formatDate, formatNumber } from "@/lib/utils";

const CACHE_KEY = "noble:dashboard:snapshot:v1";
const CACHE_TTL = 30_000;
const FINANCE_IDLE_DELAY = 900;

const n = (value: unknown): number => {
  const parsed = Number(value ?? 0);
  return Number.isFinite(parsed) ? parsed : 0;
};

const money = (value: unknown, currency: string, locale: string): string =>
  formatCurrency(n(value), currency || "RWF", locale || "en-RW");

const borrowerName = (loan: Loan): string => {
  const name =
    `${loan.borrower?.firstName ?? ""} ${loan.borrower?.lastName ?? ""}`.trim();

  return name || "Unnamed borrower";
};

function readCache(): DashboardStats | null {
  if (typeof window === "undefined") {
    return null;
  }

  try {
    const raw = sessionStorage.getItem(CACHE_KEY);

    if (!raw) {
      return null;
    }

    const parsed = JSON.parse(raw) as {
      timestamp?: number;
      data?: DashboardStats;
    };

    if (!parsed.data) {
      return null;
    }

    if (Date.now() - n(parsed.timestamp) > CACHE_TTL) {
      return null;
    }

    return parsed.data;
  } catch {
    return null;
  }
}

function writeCache(data: DashboardStats): void {
  if (typeof window === "undefined") {
    return;
  }

  try {
    sessionStorage.setItem(
      CACHE_KEY,
      JSON.stringify({
        timestamp: Date.now(),
        data,
      }),
    );
  } catch {
    // Cache is only an optimization.
  }
}

function riskTone(quality?: string): string {
  switch (quality) {
    case "CURRENT":
      return "premium-status premium-status-good";

    case "WATCH":
      return "premium-status premium-status-watch";

    case "SUBSTANDARD":
    case "DOUBTFUL":
    case "WRITTEN_OFF":
      return "premium-status premium-status-danger";

    default:
      return "premium-status premium-status-neutral";
  }
}

function parTone(par: number): {
  label: string;
  className: string;
} {
  if (par <= 3) {
    return {
      label: "Controlled",
      className: "premium-risk-good",
    };
  }

  if (par <= 5) {
    return {
      label: "Watch",
      className: "premium-risk-watch",
    };
  }

  if (par <= 10) {
    return {
      label: "Elevated",
      className: "premium-risk-elevated",
    };
  }

  return {
    label: "Critical",
    className: "premium-risk-critical",
  };
}

function MetricTile({
  label,
  value,
  detail,
  href,
}: {
  label: string;
  value: string;
  detail: string;
  href?: string;
}) {
  const content = (
    <div className="premium-metric-tile">
      <div className="premium-label">{label}</div>

      <div className="premium-metric-value">{value}</div>

      <div className="premium-metric-detail">{detail}</div>

      {href ? <div className="premium-open-link">Open workspace →</div> : null}
    </div>
  );

  return href ? <Link href={href}>{content}</Link> : content;
}

export default function DashboardPage() {
  const { currency, locale, user } = useAuth();

  const cached = useMemo(() => readCache(), []);

  const [stats, setStats] = useState<DashboardStats | null>(cached);

  const [loading, setLoading] = useState<boolean>(!cached);

  const [refreshing, setRefreshing] = useState(false);

  const [error, setError] = useState("");

  const [finance, setFinance] = useState<{
    income: number;
    expenses: number;
    profit: number;
    cashMovement: number;
    expenseCount: number;
  } | null>(null);

  const canFinance = ["ADMIN", "MANAGER", "ACCOUNTANT", "FINANCE"].includes(
    user?.role ?? "",
  );

  const loadDashboard = useCallback(async (background = false) => {
    if (background) {
      setRefreshing(true);
    } else {
      setLoading(true);
    }

    setError("");

    try {
      const result = (await loanApi.dashboard()) as DashboardStats;

      setStats(result);
      writeCache(result);
    } catch (errorValue: unknown) {
      const message =
        errorValue instanceof Error
          ? errorValue.message
          : "Unable to load the portfolio snapshot.";

      setError(message);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  /*
   * Important:
   *
   * Do NOT put `stats` in this dependency list.
   *
   * If stats were included here, every successful API response
   * would update stats, trigger the effect again, and create an
   * unnecessary request loop.
   */
  useEffect(() => {
    if (stats) {
      void loadDashboard(true);
      return;
    }

    void loadDashboard(false);
  }, [loadDashboard]);

  /*
   * Finance is intentionally loaded after the main portfolio
   * snapshot. Dashboard navigation should never wait for accounting.
   */
  useEffect(() => {
    if (!canFinance) {
      return;
    }

    let cancelled = false;

    const timer = window.setTimeout(async () => {
      const today = new Date();

      const from = new Date(today.getFullYear(), today.getMonth(), 1)
        .toISOString()
        .slice(0, 10);

      const to = today.toISOString().slice(0, 10);

      const [pnlResult, cashResult, expenseResult] = await Promise.allSettled([
        accountingApi.profitAndLoss(from, to),

        accountingApi.cashFlow(from, to),

        expenseApi.summary(from, to),
      ]);

      if (cancelled) {
        return;
      }

      const pnl =
        pnlResult.status === "fulfilled" ? (pnlResult.value ?? {}) : {};

      const cash =
        cashResult.status === "fulfilled" ? (cashResult.value ?? {}) : {};

      const expenses =
        expenseResult.status === "fulfilled" ? (expenseResult.value ?? {}) : {};

      setFinance({
        income: n(pnl.totalIncome),

        expenses: n(
          pnl.totalExpense ?? pnl.totalExpenses ?? expenses.totalExpenses,
        ),

        profit: n(pnl.netIncome),

        cashMovement: n(cash.netChangeInCash ?? cash.netCashFlow),

        expenseCount: n(expenses.count ?? expenses.totalCount),
      });
    }, FINANCE_IDLE_DELAY);

    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [canFinance]);

  const par = n(stats?.portfolioAtRiskPct);

  const risk = parTone(par);

  const disbursed = n(stats?.totalDisbursed);

  const collected = n(stats?.totalCollected);

  const collectionRate =
    disbursed > 0 ? Math.min(100, (collected / disbursed) * 100) : 0;

  const openAttention =
    n(stats?.pendingLoans) + n(stats?.overdueLoans) + n(stats?.defaultedLoans);

  const recentLoans = stats?.recentLoans?.slice(0, 8) ?? [];

  if (loading && !stats) {
    return <PageSpinner />;
  }

  if (!stats) {
    return (
      <main className="premium-page premium-empty-state">
        <Card>
          <CardBody>
            <div className="premium-empty-icon">!</div>

            <h1 className="premium-empty-title">
              Portfolio snapshot unavailable
            </h1>

            <p className="premium-empty-copy">
              {error ||
                "The lending engine did not return the dashboard snapshot."}
            </p>

            <Button onClick={() => void loadDashboard(false)}>Retry</Button>
          </CardBody>
        </Card>
      </main>
    );
  }

  const today = new Intl.DateTimeFormat(locale || "en-RW", {
    weekday: "long",
    year: "numeric",
    month: "long",
    day: "numeric",
  }).format(new Date());

  return (
    <main className="premium-page pb-16">
      <div className="premium-container">
        {/* =====================================================
            EXECUTIVE HEADER
            ===================================================== */}

        <section className="premium-command-hero">
          <div className="premium-command-grid" />

          <div className="premium-command-glow premium-command-glow-gold" />

          <div className="premium-command-glow premium-command-glow-teal" />

          <div className="relative z-10 grid gap-8 xl:grid-cols-[1fr_auto] xl:items-end">
            <div>
              <div className="premium-hero-kicker">
                <span className="premium-live-dot" />
                Noble Loan · Executive portfolio
              </div>

              <h1 className="premium-command-title">
                Good to see you
                {user?.name ? `, ${user.name.split(" ")[0]}` : ""}.
              </h1>

              <p className="premium-command-copy">
                A decision-ready view of lending exposure, collections, credit
                quality and finance performance. Financial values remain
                authoritative from the lending and accounting engines.
              </p>

              <div className="premium-command-meta">
                <span>{today}</span>

                <span className="premium-dot-separator">•</span>

                <span>
                  {refreshing
                    ? "Refreshing portfolio"
                    : "Live portfolio snapshot"}
                </span>
              </div>
            </div>

            <div className="premium-command-actions">
              <Link
                href="/dashboard/loans/new"
                className="premium-action premium-action-gold"
              >
                <span>+</span>
                New loan
              </Link>

              <Link
                href="/dashboard/payments"
                className="premium-action premium-action-light"
              >
                Record payment
              </Link>

              <Link
                href="/dashboard/reports"
                className="premium-action premium-action-light"
              >
                Management reports
              </Link>
            </div>
          </div>
        </section>

        {/* =====================================================
            STALE DATA / REFRESH ERROR
            ===================================================== */}

        {error ? (
          <div className="premium-alert premium-alert-warning">
            <div>
              <strong>Live refresh failed.</strong>

              <span>
                The last available portfolio snapshot remains visible.
              </span>
            </div>

            <button
              type="button"
              onClick={() => void loadDashboard(true)}
              className="premium-alert-action"
            >
              Retry
            </button>
          </div>
        ) : null}

        {/* =====================================================
            EXECUTIVE KPIs
            ===================================================== */}

        <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <StatCard
            icon="◈"
            label="Portfolio facilities"
            value={formatNumber(n(stats.totalLoans))}
            sub={`${formatNumber(n(stats.activeLoans))} active facilities`}
            color="#0B2944"
          />

          <StatCard
            icon="◆"
            label="Outstanding principal"
            value={money(stats.outstandingBalance, currency, locale)}
            sub="Current principal exposure"
            color="#087F74"
          />

          <StatCard
            icon="↗"
            label="Collected this month"
            value={money(stats.collectedThisMonth, currency, locale)}
            sub={`${collectionRate.toFixed(1)}% lifetime collection coverage`}
            color="#315B7F"
          />

          <StatCard
            icon="!"
            label="Portfolio at risk"
            value={`${par.toFixed(2)}%`}
            sub={`${risk.label} credit-quality position`}
            color={par > 5 ? "#B42318" : "#C9A227"}
          />
        </section>

        {/* =====================================================
            PORTFOLIO COMMAND + QUALITY
            ===================================================== */}

        <section className="grid gap-5 xl:grid-cols-[1.3fr_.7fr]">
          <Card>
            <CardHeader
              title="Portfolio command"
              subtitle="The operating indicators management should see first."
            />

            <CardBody>
              <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
                <MetricTile
                  label="Total borrowers"
                  value={formatNumber(n(stats.totalBorrowers))}
                  detail="Borrower relationships"
                  href="/dashboard/borrowers"
                />

                <MetricTile
                  label="Overdue facilities"
                  value={formatNumber(n(stats.overdueLoans))}
                  detail="Requires collection attention"
                  href="/dashboard/collections"
                />

                <MetricTile
                  label="Pending applications"
                  value={formatNumber(n(stats.pendingLoans))}
                  detail="Awaiting credit action"
                  href="/dashboard/approvals"
                />

                <MetricTile
                  label="Defaulted facilities"
                  value={formatNumber(n(stats.defaultedLoans))}
                  detail="Escalation / recovery"
                  href="/dashboard/loans?status=DEFAULTED"
                />
              </div>
            </CardBody>
          </Card>

          <Card>
            <CardHeader
              title="Portfolio quality"
              subtitle="Credit quality stays linked to facilities and borrowers."
            />

            <CardBody>
              <div className="premium-risk-panel">
                <div>
                  <div className="premium-label">Portfolio at risk</div>

                  <div className="premium-risk-number">{par.toFixed(2)}%</div>
                </div>

                <span className={`premium-risk-pill ${risk.className}`}>
                  {risk.label}
                </span>
              </div>

              <div className="premium-progress-track mt-5">
                <div
                  className="premium-progress-fill"
                  style={{
                    width: `${Math.min(100, Math.max(0, par))}%`,
                  }}
                />
              </div>

              <div className="mt-5 grid grid-cols-3 gap-2">
                <Link
                  href="/dashboard/loans?status=ACTIVE"
                  className="premium-quality-cell"
                >
                  <strong>{n(stats.activeLoans)}</strong>

                  <span>Active</span>
                </Link>

                <Link
                  href="/dashboard/collections"
                  className="premium-quality-cell premium-quality-cell-danger"
                >
                  <strong>{n(stats.overdueLoans)}</strong>

                  <span>Overdue</span>
                </Link>

                <Link
                  href="/dashboard/loans?status=DEFAULTED"
                  className="premium-quality-cell"
                >
                  <strong>{n(stats.defaultedLoans)}</strong>

                  <span>Defaulted</span>
                </Link>
              </div>

              <div className="premium-note mt-4">
                Individual credit quality is displayed on the borrower and
                linked loan record. No separate credit-quality page is
                introduced.
              </div>
            </CardBody>
          </Card>
        </section>

        {/* =====================================================
            COLLECTIONS + FINANCE
            ===================================================== */}

        <section className="grid gap-5 xl:grid-cols-[1.1fr_.9fr]">
          <Card>
            <CardHeader
              title="Collections control"
              subtitle="Overdue facilities are connected directly to the loan portfolio."
              action={
                <Link
                  href="/dashboard/collections"
                  className="premium-header-link"
                >
                  Open collections →
                </Link>
              }
            />

            <CardBody>
              <div className="grid gap-6 md:grid-cols-[180px_1fr] md:items-center">
                <div
                  className="premium-donut"
                  style={{
                    background: `conic-gradient(#087F74 ${collectionRate}%, #E9EEF2 ${collectionRate}% 100%)`,
                  }}
                >
                  <div className="premium-donut-inner">
                    <strong>{collectionRate.toFixed(1)}%</strong>

                    <span>coverage</span>
                  </div>
                </div>

                <div className="grid gap-3 sm:grid-cols-2">
                  <MetricTile
                    label="Gross disbursed"
                    value={money(disbursed, currency, locale)}
                    detail="Lifetime principal base"
                  />

                  <MetricTile
                    label="Total collected"
                    value={money(collected, currency, locale)}
                    detail="Posted collections"
                    href="/dashboard/payments"
                  />

                  <MetricTile
                    label="Overdue facilities"
                    value={formatNumber(n(stats.overdueLoans))}
                    detail="Facilities in arrears"
                    href="/dashboard/collections"
                  />

                  <MetricTile
                    label="Late payments"
                    value={formatNumber(n(stats.latePaymentsCount))}
                    detail="Backend-detected late payments"
                    href="/dashboard/payments"
                  />
                </div>
              </div>
            </CardBody>
          </Card>

          <Card>
            <CardHeader
              title="Finance control"
              subtitle="Accounting and expenses remain in their existing workspaces."
              action={
                canFinance ? (
                  <Link
                    href="/dashboard/accounting"
                    className="premium-header-link"
                  >
                    Open accounting →
                  </Link>
                ) : undefined
              }
            />

            <CardBody>
              {canFinance ? (
                <div className="grid grid-cols-2 gap-3">
                  <MetricTile
                    label="Income"
                    value={
                      finance
                        ? money(finance.income, currency, locale)
                        : "Loading…"
                    }
                    detail="Current accounting period"
                    href="/dashboard/accounting"
                  />

                  <MetricTile
                    label="Expenses"
                    value={
                      finance
                        ? money(finance.expenses, currency, locale)
                        : "Loading…"
                    }
                    detail={
                      finance
                        ? `${formatNumber(
                            finance.expenseCount,
                          )} expense records`
                        : "Current accounting period"
                    }
                    href="/dashboard/expenses"
                  />

                  <MetricTile
                    label="Net income"
                    value={
                      finance
                        ? money(finance.profit, currency, locale)
                        : "Loading…"
                    }
                    detail="Income less expenses"
                    href="/dashboard/reports"
                  />

                  <MetricTile
                    label="Net cash movement"
                    value={
                      finance
                        ? money(finance.cashMovement, currency, locale)
                        : "Loading…"
                    }
                    detail="Current accounting period"
                    href="/dashboard/accounting"
                  />
                </div>
              ) : (
                <div className="premium-permission-box">
                  <strong>Finance control is permission protected.</strong>

                  <span>
                    Authorised finance users can access accounting and expenses.
                  </span>
                </div>
              )}
            </CardBody>
          </Card>
        </section>

        {/* =====================================================
            ATTENTION QUEUE
            ===================================================== */}

        {openAttention > 0 ? (
          <section className="premium-attention">
            <div>
              <div className="premium-eyebrow">Management attention</div>

              <h2>{openAttention} portfolio items require action</h2>

              <p>
                Use the existing approval, loan and collections workflows to
                resolve them.
              </p>
            </div>

            <div className="premium-attention-actions">
              <Link href="/dashboard/approvals">
                <strong>{n(stats.pendingLoans)}</strong>

                <span>Pending approvals</span>
              </Link>

              <Link href="/dashboard/collections">
                <strong>{n(stats.overdueLoans)}</strong>

                <span>Overdue facilities</span>
              </Link>

              <Link href="/dashboard/loans?status=DEFAULTED">
                <strong>{n(stats.defaultedLoans)}</strong>

                <span>Defaulted facilities</span>
              </Link>
            </div>
          </section>
        ) : null}

        {/* =====================================================
            RECENT LOANS + EXECUTIVE ACCESS
            ===================================================== */}

        <section className="grid gap-5 xl:grid-cols-[1.35fr_.65fr]">
          <Card>
            <CardHeader
              title="Recent lending activity"
              subtitle="Recent facilities with borrower-linked credit quality."
              action={
                <Link href="/dashboard/loans" className="premium-header-link">
                  View portfolio →
                </Link>
              }
            />

            <div className="overflow-x-auto">
              <table className="premium-table">
                <thead>
                  <tr>
                    <th>Facility</th>
                    <th>Borrower</th>
                    <th>Outstanding</th>
                    <th>Status</th>
                    <th>Credit quality</th>
                    <th>Due</th>
                  </tr>
                </thead>

                <tbody>
                  {recentLoans.length ? (
                    recentLoans.map((loan) => (
                      <tr key={loan.id}>
                        <td>
                          <Link
                            href={`/dashboard/loans/${loan.id}`}
                            className="premium-table-primary"
                          >
                            {loan.referenceNumber || `#${loan.id}`}
                          </Link>
                        </td>

                        <td>
                          <Link
                            href={
                              loan.borrower?.id
                                ? `/dashboard/borrowers/${loan.borrower.id}`
                                : "/dashboard/borrowers"
                            }
                            className="premium-table-secondary"
                          >
                            {borrowerName(loan)}
                          </Link>
                        </td>

                        <td className="font-black tabular-nums">
                          {money(
                            loan.outstandingBalance ?? loan.amount,
                            loan.currency || currency,
                            locale,
                          )}
                        </td>

                        <td>
                          <span className="premium-status premium-status-neutral">
                            {String(loan.status || "—")}
                          </span>
                        </td>

                        <td>
                          <span className={riskTone(loan.creditQuality)}>
                            {loan.creditQuality ||
                              loan.riskCategory ||
                              "CURRENT"}
                          </span>
                        </td>

                        <td>
                          {loan.nextDueDate
                            ? formatDate(loan.nextDueDate, locale || "en-RW")
                            : "—"}
                        </td>
                      </tr>
                    ))
                  ) : (
                    <tr>
                      <td colSpan={6} className="premium-table-empty">
                        No recent lending activity returned.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </Card>

          <Card>
            <CardHeader
              title="Executive access"
              subtitle="Focused access to existing operational workspaces."
            />

            <CardBody>
              <div className="grid gap-2">
                {[
                  [
                    "Loan portfolio",
                    "/dashboard/loans",
                    "Facilities, exposure and borrower positions",
                  ],
                  [
                    "Collections",
                    "/dashboard/collections",
                    "Overdue facilities connected to their loans",
                  ],
                  [
                    "Accounting",
                    "/dashboard/accounting",
                    "Ledger, statements and controls",
                  ],
                  [
                    "Expenses",
                    "/dashboard/expenses",
                    "Operating costs and payment records",
                  ],
                  [
                    "Reports",
                    "/dashboard/reports",
                    "Management summaries and Excel exports",
                  ],
                ].map(([title, href, description]) => (
                  <Link key={href} href={href} className="premium-access-row">
                    <span>
                      <strong>{title}</strong>

                      <small>{description}</small>
                    </span>

                    <b>→</b>
                  </Link>
                ))}
              </div>
            </CardBody>
          </Card>
        </section>
      </div>
    </main>
  );
}
