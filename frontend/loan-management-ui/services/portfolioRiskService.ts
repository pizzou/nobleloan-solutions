import { get } from "./api";

export interface PortfolioRiskAgeingBucket {
  code: string;
  label: string;
  minDaysOverdue: number;
  maxDaysOverdue?: number | null;
  loanCount: number;
  outstandingPrincipal: number | string;
  percentageOfPortfolio: number | string;
}

export interface PortfolioRiskAnalytics {
  organizationId: number;
  asOfDate: string;
  generatedAt: string;
  currentPortfolioLoans: number;
  currentOutstandingPrincipal: number | string;
  par1LoanCount: number;
  par1Amount: number | string;
  par1Pct: number | string;
  par7LoanCount: number;
  par7Amount: number | string;
  par7Pct: number | string;
  par30LoanCount: number;
  par30Amount: number | string;
  par30Pct: number | string;
  par60LoanCount: number;
  par60Amount: number | string;
  par60Pct: number | string;
  par90LoanCount: number;
  par90Amount: number | string;
  par90Pct: number | string;
  hasOutstandingPortfolio: boolean;
  ageingBuckets: PortfolioRiskAgeingBucket[];
}

function unwrap<T>(value: unknown): T {
  if (!value || typeof value !== "object") {
    return value as T;
  }

  const root = value as Record<string, unknown>;
  if (root.data && typeof root.data === "object") {
    const data = root.data as Record<string, unknown>;
    if ("data" in data) {
      return data.data as T;
    }
    return data as T;
  }

  return value as T;
}

export async function getPortfolioRiskAnalytics(): Promise<PortfolioRiskAnalytics> {
  const response = await get("/analytics/portfolio-risk");
  return unwrap<PortfolioRiskAnalytics>(response);
}
