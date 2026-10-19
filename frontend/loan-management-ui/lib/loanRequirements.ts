export type LoanRequirementProfile = {
  eligibility: string[];
  requiredDocuments: string[];
  additionalEvidence: string[];
};

const COMMON_DOCUMENTS = [
  "Valid Rwanda National ID (or accepted identity document, subject to verification)",
  "Recent bank statement covering the last 3 months",
  "Live selfie for identity verification",
  "Marital-status certificate: marriage certificate if married, or single-status certificate otherwise",
];

const PROFILES: Record<string, LoanRequirementProfile> = {
  PERSONAL: {
    eligibility: ["Be at least 18 years old", "Provide valid contact details and a verifiable residential address", "Show a reliable source of income and a repayment plan that fits your budget"],
    requiredDocuments: [...COMMON_DOCUMENTS, "Payslip for salaried applicants, or business registration/company certificate for business owners where applicable"],
    additionalEvidence: ["Proof of income or other supporting records may be requested during assessment"],
  },
  BUSINESS: {
    eligibility: ["Be at least 18 years old and authorised to apply for the business", "Operate a verifiable business with a clear financing purpose", "Demonstrate business cash flow sufficient to support repayments"],
    requiredDocuments: [...COMMON_DOCUMENTS, "Business registration/company certificate", "Recent business and income records requested in the application"],
    additionalEvidence: ["Business plan, sales records, tax records, supplier quotations or other evidence of business activity may be requested"],
  },
  AUTO: {
    eligibility: ["Be at least 18 years old", "Demonstrate income and affordability for the proposed repayments", "Provide details of the vehicle being financed"],
    requiredDocuments: [...COMMON_DOCUMENTS, "Payslip for salaried applicants or business registration/income evidence for business owners"],
    additionalEvidence: ["Vehicle quotation or pro-forma invoice, vehicle details and proof of any required contribution may be requested"],
  },
  SALARY_ADVANCE: {
    eligibility: ["Be at least 18 years old", "Have verifiable salaried employment and a regular salary", "Show that repayment is affordable from salary income"],
    requiredDocuments: [...COMMON_DOCUMENTS, "Recent payslip and evidence of employment or salary credits"],
    additionalEvidence: ["Employer confirmation or additional salary records may be requested during verification"],
  },
  AGRICULTURAL: {
    eligibility: ["Be at least 18 years old", "Have an active farming or agribusiness activity with a clear financing purpose", "Explain expected seasonal income and how repayments will be made"],
    requiredDocuments: [...COMMON_DOCUMENTS, "Business registration/company certificate for a registered agribusiness, where applicable", "Available evidence of farming or agribusiness income"],
    additionalEvidence: ["Farm or production records, crop/livestock plan, land-use evidence, input quotations or buyer agreements may be requested"],
  },
  MORTGAGE: {
    eligibility: ["Be at least 18 years old and demonstrate sustainable repayment capacity", "Identify the property or property-related purpose being financed", "Be able to provide property details and complete any required legal/title checks"],
    requiredDocuments: [...COMMON_DOCUMENTS, "Income/employment evidence and property details"],
    additionalEvidence: ["Property title or ownership records, valuation, sale agreement, proof of contribution and legal due-diligence documents may be requested"],
  },
  STUDENT: {
    eligibility: ["Be at least 18 years old, or apply through an accepted responsible applicant where permitted", "Provide details of the education institution and study costs", "Identify a realistic repayment source or acceptable sponsor/guarantor if required"],
    requiredDocuments: [...COMMON_DOCUMENTS, "Admission or enrolment letter and tuition/fee schedule", "Sponsor or income evidence where applicable"],
    additionalEvidence: ["Academic records, sponsor consent or guarantor documents may be requested"],
  },
  EMERGENCY: {
    eligibility: ["Be at least 18 years old", "Explain the urgent expense and requested amount", "Demonstrate a practical and affordable repayment plan"],
    requiredDocuments: [...COMMON_DOCUMENTS, "Income or employment evidence relevant to your circumstances"],
    additionalEvidence: ["A quotation, invoice, medical bill or other evidence of the emergency expense may be requested, depending on purpose"],
  },
  ASSET_FINANCE: {
    eligibility: ["Be at least 18 years old or apply through a registered business", "Identify the asset and explain how it will be used", "Demonstrate repayment capacity and the asset's intended value/use"],
    requiredDocuments: [...COMMON_DOCUMENTS, "Income/business evidence and supplier details"],
    additionalEvidence: ["Supplier quotation, pro-forma invoice, asset specifications, valuation or insurance information may be requested"],
  },
  MICROFINANCE: {
    eligibility: ["Be at least 18 years old", "Explain the household or income-generating purpose", "Demonstrate ability to repay without compromising essential expenses"],
    requiredDocuments: [...COMMON_DOCUMENTS, "Income or livelihood evidence relevant to your circumstances"],
    additionalEvidence: ["Simple cash-flow records, business activity evidence or guarantor details may be requested"],
  },
  TRADE_FINANCE: {
    eligibility: ["Be at least 18 years old and authorised to represent the applicant business", "Have a verifiable trade transaction or purchase/supply purpose", "Demonstrate the ability to repay from expected business proceeds"],
    requiredDocuments: [...COMMON_DOCUMENTS, "Business registration/company certificate and business bank records"],
    additionalEvidence: ["Purchase orders, supplier invoices, contracts, shipping/customs records or buyer agreements may be requested"],
  },
  GROUP: {
    eligibility: ["Meet the lender's group membership and participation rules", "Provide the required group/member information and agreed borrowing purpose", "Understand any group liability, guarantee and repayment obligations before signing"],
    requiredDocuments: [...COMMON_DOCUMENTS, "Group membership/registration records and relevant member income evidence"],
    additionalEvidence: ["Group resolution, member list, guarantor details or meeting records may be requested"],
  },
};

const DEFAULT_PROFILE: LoanRequirementProfile = {
  eligibility: ["Be at least 18 years old", "Provide valid identity and contact details", "Demonstrate ability to repay the requested loan"],
  requiredDocuments: [...COMMON_DOCUMENTS, "Income or employment evidence relevant to your situation"],
  additionalEvidence: ["Product-specific supporting documents may be requested during assessment"],
};

export function getLoanRequirementProfile(loanType?: string | null): LoanRequirementProfile {
  const key = String(loanType || "").trim().toUpperCase();
  return PROFILES[key] || DEFAULT_PROFILE;
}

export function getLoanProductType(product: { loanType?: string | null; title?: string | null; name?: string | null }): string {
  const explicit = String(product.loanType || "").trim().toUpperCase();
  if (explicit) return explicit;
  const name = String(product.title || product.name || "").toLowerCase();
  if (name.includes("business")) return "BUSINESS";
  if (name.includes("vehicle") || name.includes("auto")) return "AUTO";
  if (name.includes("salary")) return "SALARY_ADVANCE";
  if (name.includes("agri")) return "AGRICULTURAL";
  return "PERSONAL";
}
