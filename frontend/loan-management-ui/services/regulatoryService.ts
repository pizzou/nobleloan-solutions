import API, { get, post } from "./api";

export type RegulatoryPeriod =
  | "DAILY"
  | "WEEKLY"
  | "MONTHLY"
  | "QUARTERLY"
  | "YEARLY"
  | "CUSTOM";
export type ExportFormat = "xlsx" | "csv" | "pdf";

export interface BnrReportParams {
  branchId?: number;
  period?: RegulatoryPeriod | string;
  from?: string;
  to?: string;
}

export interface BreakdownRow {
  label: string;
  count: number;
  amount: number;
}

export interface FinancialStatementRow {
  code?: string;
  name?: string;
  debit?: number | string;
  credit?: number | string;
  balance?: number | string;
  amount?: number | string;
  [key: string]: unknown;
}

export interface BnrFinancialStatementReport {
  organizationId?: number;
  organizationName?: string;
  bnrInstitutionCode?: string;
  branchId?: number;
  branchName?: string;
  currency?: string;
  reportPeriod?: string;
  periodStart?: string;
  periodEnd?: string;
  generatedAt?: string;
  assets?: FinancialStatementRow[];
  liabilities?: FinancialStatementRow[];
  equity?: FinancialStatementRow[];
  income?: FinancialStatementRow[];
  expenses?: FinancialStatementRow[];
  totalAssets?: number | string;
  totalLiabilities?: number | string;
  totalEquity?: number | string;
  currentPeriodNetIncome?: number | string;
  balanceSheetBalanced?: boolean;
  totalIncome?: number | string;
  totalExpenses?: number | string;
  netIncome?: number | string;
  trialBalanceDebit?: number | string;
  trialBalanceCredit?: number | string;
  trialBalanceBalanced?: boolean;
  cashUsedForLending?: number | string;
  cashFromCollections?: number | string;
  cashFromFees?: number | string;
  otherCashMovement?: number | string;
  netChangeInCash?: number | string;
  [key: string]: unknown;
}

export interface BnrSummary {
  organizationId?: number;
  organizationName?: string;
  bnrInstitutionCode?: string;
  registrationNumber?: string;
  institutionType?: string;
  country?: string;
  currency?: string;
  reportPeriod?: string;
  periodStart?: string;
  periodEnd?: string;
  reportDate?: string;
  generatedAt?: string;
  generatedBy?: string;
  reportReference?: string;
  branchId?: number;
  branchName?: string;
  totalLoans?: number;
  loansDisbursedDuringPeriod?: number;
  activeLoans?: number;
  closedLoans?: number;
  paidLoans?: number;
  pendingLoans?: number;
  approvedLoans?: number;
  rejectedLoans?: number;
  cancelledLoans?: number;
  overdueLoans?: number;
  defaultedLoans?: number;
  writtenOffLoans?: number;
  restructuredLoans?: number;
  totalPrincipalDisbursed?: number | string;
  totalApprovedAmount?: number | string;
  averageLoanSize?: number | string;
  largestLoanAmount?: number | string;
  smallestLoanAmount?: number | string;
  outstandingPrincipal?: number | string;
  outstandingInterest?: number | string;
  outstandingFees?: number | string;
  totalOutstanding?: number | string;
  totalPrincipalCollected?: number | string;
  totalInterestCollected?: number | string;
  totalFeesCollected?: number | string;
  totalAmountCollected?: number | string;
  interestAccruedUnpaid?: number | string;
  feesAccruedUnpaid?: number | string;
  totalPayments?: number;
  missedPayments?: number;
  overduePayments?: number;
  parAmount?: number | string;
  parRatio?: number | string;
  par1Ratio?: number | string;
  par30Ratio?: number | string;
  par60Ratio?: number | string;
  par90Ratio?: number | string;
  nplAmount?: number | string;
  nplRatio?: number | string;
  nplLoanCount?: number;
  defaultedAmount?: number | string;
  writtenOffAmount?: number | string;
  recoveriesAfterWriteOff?: number | string;
  requiredProvision?: number | string;
  existingProvision?: number | string;
  provisionShortfall?: number | string;
  totalBorrowers?: number;
  activeBorrowers?: number;
  maleBorrowers?: number;
  femaleBorrowers?: number;
  otherGenderBorrowers?: number;
  borrowersWithMultipleLoans?: number;
  loansMissingBranch?: number;
  loansMissingCurrency?: number;
  loansMissingRepaymentSchedule?: number;
  dataQualityWarnings?: string[];
  [key: string]: unknown;
}

export interface RegulatoryApiClient {
  id: number;
  name: string;
  clientType: "BNR" | "CREDIT_BUREAU";
  keyPrefix?: string;
  active?: boolean;
  revoked?: boolean;
  contactEmail?: string;
  description?: string;
  expiresAt?: string | null;
  lastUsedAt?: string | null;
  lastUsedIp?: string | null;
  revokedAt?: string | null;
  createdAt?: string;
}

export type RegulatoryApiClientType = RegulatoryApiClient["clientType"];

export interface RegulatoryApiClientCreatedResponse {
  client: RegulatoryApiClient;
  apiKey: string;
  key?: string;
}

export interface CreditRecord {
  borrowerId?: number;
  nationalId?: string;
  fullName?: string;
  dateOfBirth?: string;
  gender?: string;
  phone?: string;
  loanNumber?: string;
  loanType?: string;
  loanStatus?: string;
  repaymentClassification?: string;
  loanAmount?: number | string;
  outstandingBalance?: number | string;
  daysPastDue?: number;
  creditScore?: number;
  dateOpened?: string;
  lastPaymentDate?: string;
  maturityDate?: string;
  dateClosed?: string;
  branchName?: string;
  currency?: string;
  [key: string]: unknown;
}

export const regulatoryApi = {
  bnrSummary: (params: BnrReportParams = {}) =>
    get(`/regulatory/bnr/summary?${qs(params)}`) as Promise<BnrSummary>,

  bnrFinancialStatement: (params: BnrReportParams = {}) =>
    get(
      `/regulatory/bnr/financial-statement?${qs(params)}`,
    ) as Promise<BnrFinancialStatementReport>,

  bnrByLoanType: (params: BnrReportParams = {}) =>
    get(`/regulatory/bnr/breakdown/loan-type?${qs(params)}`) as Promise<
      BreakdownRow[]
    >,

  bnrByBranch: (params: BnrReportParams = {}) =>
    get(`/regulatory/bnr/breakdown/branch?${qs(params)}`) as Promise<
      BreakdownRow[]
    >,

  bnrByGender: (params: BnrReportParams = {}) =>
    get(`/regulatory/bnr/breakdown/gender?${qs(params)}`) as Promise<
      BreakdownRow[]
    >,

  bnrExport: (format: ExportFormat, params: BnrReportParams = {}) =>
    downloadFile(
      `/regulatory/bnr/export?format=${format}&${qs(params)}`,
      `bnr-summary.${format}`,
    ),

  creditBureauPreview: (
    params: {
      borrowerId?: number;
      branchId?: number;
      from?: string;
      to?: string;
    } = {},
  ) =>
    get(`/regulatory/credit-bureau/preview?${qs(params)}`) as Promise<
      CreditRecord[]
    >,

  creditBureauExport: (
    format: ExportFormat,
    params: { branchId?: number; from?: string; to?: string } = {},
  ) =>
    downloadFile(
      `/regulatory/credit-bureau/download?format=${format}&${qs(params)}`,
      `credit-bureau-export.${format}`,
    ),

  listApiClients: () =>
    get("/regulatory/api-clients") as Promise<RegulatoryApiClient[]>,

  createApiClient: (data: {
    name: string;
    clientType: RegulatoryApiClientType;
    contactEmail?: string;
    description?: string;
    expiresAt?: string | null;
  }) =>
    post(
      "/regulatory/api-clients",
      data,
    ) as Promise<RegulatoryApiClientCreatedResponse>,

  revokeApiClient: (id: number, reason?: string) =>
    post(`/regulatory/api-clients/${id}/revoke`, { reason }),

  getErrorMessage: (
    error: unknown,
    fallback = "An unexpected error occurred.",
  ) => {
    if (error instanceof Error && error.message) return error.message;
    if (error && typeof error === "object") {
      const value = error as {
        response?: { data?: { error?: string; message?: string } };
        message?: string;
      };
      return (
        value.response?.data?.error ||
        value.response?.data?.message ||
        value.message ||
        fallback
      );
    }
    return fallback;
  },
};

function qs(params: object) {
  const p = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== "") p.set(k, String(v));
  });
  return p.toString();
}

async function downloadFile(path: string, filename: string) {
  const res = await API.get(path, { responseType: "blob" });
  const url = URL.createObjectURL(res.data as Blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 60000);
}
