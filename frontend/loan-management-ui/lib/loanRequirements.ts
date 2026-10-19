/**
 * Public, pre-application requirements by loan product.
 *
 * Keep this module presentation-only: it does not approve applicants or replace
 * the active product configuration, application validation, KYC rules, or credit
 * assessment. Items phrased as "may be requested" are supporting evidence, not
 * a promise that every applicant must submit that item.
 */
export type LoanRequirementProfile = {
  eligibility: string[];
  requiredDocuments: string[];
  additionalEvidence: string[];
};

const IDENTITY_AND_CONTACT = [
  "Valid Rwanda National ID, or another identity document accepted by Noble and subject to verification.",
  "A working mobile number and contact details that Noble can use to communicate about the application.",
  "Current residential address and details sufficient for address verification.",
];

const FINANCIAL_BASE = [
  "Recent bank statement(s), normally covering the latest 3 months, where an account is available and relevant to the applicant's income.",
  "Evidence of income appropriate to the applicant's situation (for example, payslips, salary credits, business records, or other verifiable income evidence).",
];

const COMMON_DOCUMENTS = [
  ...IDENTITY_AND_CONTACT,
  ...FINANCIAL_BASE,
  "A recent photograph/selfie if requested by the secure identity-verification flow.",
  "Marital-status information and supporting documentation only where requested by the application or required for the specific case.",
];

const PROFILES: Record<string, LoanRequirementProfile> = {
  PERSONAL: {
    eligibility: [
      "Be at least 18 years old and legally able to enter into a loan agreement.",
      "Have a clear personal or household purpose for the requested funds.",
      "Provide verifiable identity, contact information, and residential-address details.",
      "Show a reliable source of income and sufficient disposable income to repay after essential living expenses and existing debt obligations.",
      "Choose a requested amount and repayment term within the active Personal Loan product limits; the final amount and term remain subject to assessment and approval.",
      "Agree to identity, affordability, credit-history, and other checks applicable to the application.",
    ],
    requiredDocuments: [
      ...COMMON_DOCUMENTS,
      "For salaried applicants: recent payslip(s) and/or employment confirmation or salary-credit evidence, as requested.",
      "For self-employed applicants: records showing the source and consistency of personal/business income.",
      "Any declarations or consent requested in the application for lawful verification and credit assessment.",
    ],
    additionalEvidence: [
      "Proof of the intended use of funds may be requested where it is relevant to the application.",
      "Additional bank statements, employer confirmation, guarantor details, or clarification of existing obligations may be requested if the initial evidence is insufficient.",
      "Documents must be current, readable, consistent, and belong to the applicant; Noble may request clarification where information does not match.",
    ],
  },
  BUSINESS: {
    eligibility: [
      "Be at least 18 years old and authorised to apply for the business, or apply in the capacity accepted by the product.",
      "Operate a verifiable business with a clear, legitimate financing purpose.",
      "Demonstrate business cash flow and a repayment plan that can support the proposed instalments.",
      "Explain how the funds will be used (for example, working capital, stock, equipment, or an approved expansion purpose).",
      "Provide ownership/authorisation details and accept the applicable business, owner, and credit checks.",
      "Request an amount and term within the active Business Loan product limits; approval is subject to assessment.",
    ],
    requiredDocuments: [
      ...COMMON_DOCUMENTS,
      "Business registration certificate or other official registration evidence, where the business is required or registered to have it.",
      "Recent business bank statements and available sales, revenue, or cash-flow records.",
      "Business address and contact details, plus identification of the owner(s) or authorised representative(s).",
      "Recent financial records appropriate to the business size and form, as requested during application.",
    ],
    additionalEvidence: [
      "Business plan, cash-flow forecast, stock list, supplier quotations, purchase invoices, contracts, or sales records may be requested according to the financing purpose.",
      "Tax records, licences, permits, ownership records, or board/partner authorisation may be requested where applicable.",
      "For an existing business, evidence of trading history and repayment of current credit obligations may be requested.",
    ],
  },
  AUTO: {
    eligibility: [
      "Be at least 18 years old and able to enter into the loan agreement.",
      "Demonstrate stable income or business cash flow sufficient to meet the proposed repayments.",
      "Identify the vehicle and explain whether the purpose is purchase, replacement, or another use allowed by the active product.",
      "Provide enough information for the vehicle, seller, price, and any required contribution to be verified.",
      "Accept affordability, identity, credit, and vehicle-related checks; final approval and any security conditions are confirmed by Noble.",
    ],
    requiredDocuments: [
      ...COMMON_DOCUMENTS,
      "Vehicle quotation, pro-forma invoice, or sale offer showing the seller, vehicle description, and price.",
      "Income/employment evidence, or business-income records for self-employed applicants.",
      "Seller and vehicle details available at application, including registration/VIN or other identifiers when already assigned.",
    ],
    additionalEvidence: [
      "Proof of deposit or own contribution may be requested if the approved structure requires one.",
      "Vehicle inspection, valuation, insurance, registration/title, or seller verification documents may be requested before disbursement.",
      "Where the vehicle is used, additional condition, ownership, transfer, and valuation evidence may be required.",
    ],
  },
  VEHICLE: {
    eligibility: [
      "Be at least 18 years old and able to enter into the loan agreement.",
      "Demonstrate stable income or business cash flow sufficient to meet the proposed repayments.",
      "Identify the vehicle and explain whether the purpose is purchase, replacement, or another use allowed by the active product.",
      "Provide enough information for the vehicle, seller, price, and any required contribution to be verified.",
      "Accept affordability, identity, credit, and vehicle-related checks; final approval and any security conditions are confirmed by Noble.",
    ],
    requiredDocuments: [
      ...COMMON_DOCUMENTS,
      "Vehicle quotation, pro-forma invoice, or sale offer showing the seller, vehicle description, and price.",
      "Income/employment evidence, or business-income records for self-employed applicants.",
      "Seller and vehicle details available at application, including registration/VIN or other identifiers when already assigned.",
    ],
    additionalEvidence: [
      "Proof of deposit or own contribution may be requested if the approved structure requires one.",
      "Vehicle inspection, valuation, insurance, registration/title, or seller verification documents may be requested before disbursement.",
      "Where the vehicle is used, additional condition, ownership, transfer, and valuation evidence may be required.",
    ],
  },
  SALARY_ADVANCE: {
    eligibility: [
      "Be at least 18 years old and currently employed in a role that provides verifiable regular salary income.",
      "Provide enough information for Noble to verify the employer and the regularity of salary payments.",
      "Show that the requested amount can be repaid from income without creating unaffordable financial pressure.",
      "Confirm the expected salary/payment date and any existing salary deductions or credit commitments requested in the application.",
      "Meet the active Salary Advance product's amount and term limits; approval is not automatic because salary is received.",
    ],
    requiredDocuments: [
      ...COMMON_DOCUMENTS,
      "Recent payslip(s) and evidence of employment or employer details.",
      "Recent salary-credit entries or other acceptable evidence of regular salary payments.",
      "Details of existing salary deductions or obligations where requested for affordability assessment.",
    ],
    additionalEvidence: [
      "Employer confirmation of employment status, salary, or pay date may be requested.",
      "Additional payslips or bank statement periods may be requested if salary is variable or the available records do not establish a reliable pattern.",
      "Noble may ask for clarification where employment is temporary, probationary, recently changed, or otherwise needs verification under the active policy.",
    ],
  },
  AGRICULTURAL: {
    eligibility: [
      "Be at least 18 years old and engaged in farming or a verifiable agricultural/agribusiness activity.",
      "Describe the activity, season, production cycle, and the specific purpose for the requested funds.",
      "Explain expected income and timing, including how repayments will be made if income is seasonal.",
      "Show a reasonable link between the amount requested and the proposed farming or agribusiness activity.",
      "Meet the active product's amount and term limits and complete the applicable identity, affordability, and credit checks.",
    ],
    requiredDocuments: [
      ...COMMON_DOCUMENTS,
      "Description of the farm or agribusiness activity and the applicant's role in it.",
      "Available evidence of agricultural income, sales, buyer payments, or other livelihood/business income.",
      "Business registration documents where the applicant is a registered entity and such documents apply.",
    ],
    additionalEvidence: [
      "Farm/land-use details, crop or livestock plan, seasonal budget, production records, or prior harvest/sales evidence may be requested.",
      "Input quotations for seed, fertiliser, feed, tools, equipment, or other intended purchases may be requested.",
      "Buyer/off-taker agreements, cooperative membership, storage/transport arrangements, insurance, or permits may be requested when relevant to the activity.",
    ],
  },
  AGRICULTURE: {
    eligibility: [
      "Be at least 18 years old and engaged in farming or a verifiable agricultural/agribusiness activity.",
      "Describe the activity, season, production cycle, and the specific purpose for the requested funds.",
      "Explain expected income and timing, including how repayments will be made if income is seasonal.",
      "Show a reasonable link between the amount requested and the proposed farming or agribusiness activity.",
      "Meet the active product's amount and term limits and complete the applicable identity, affordability, and credit checks.",
    ],
    requiredDocuments: [
      ...COMMON_DOCUMENTS,
      "Description of the farm or agribusiness activity and the applicant's role in it.",
      "Available evidence of agricultural income, sales, buyer payments, or other livelihood/business income.",
      "Business registration documents where the applicant is a registered entity and such documents apply.",
    ],
    additionalEvidence: [
      "Farm/land-use details, crop or livestock plan, seasonal budget, production records, or prior harvest/sales evidence may be requested.",
      "Input quotations for seed, fertiliser, feed, tools, equipment, or other intended purchases may be requested.",
      "Buyer/off-taker agreements, cooperative membership, storage/transport arrangements, insurance, or permits may be requested when relevant to the activity.",
    ],
  },
  MORTGAGE: {
    eligibility: [
      "Be at least 18 years old and demonstrate sustainable repayment capacity.",
      "Identify the property and explain the property-related purpose being financed.",
      "Provide ownership, seller, location, and transaction details needed for legal and valuation checks.",
      "Understand that property lending may require additional legal due diligence, valuation, security registration, and an approved contribution.",
      "Meet the configured amount/term limits and pass the applicable affordability, identity, and credit assessment.",
    ],
    requiredDocuments: [
      ...COMMON_DOCUMENTS,
      "Property address/location and available title or ownership documentation.",
      "Sale agreement, seller details, or other transaction documents relevant to the requested financing.",
      "Income/employment or business financial evidence sufficient to assess repayment capacity.",
    ],
    additionalEvidence: [
      "Independent property valuation, title search, land/building records, planning or compliance documents may be requested.",
      "Proof of deposit/contribution, insurance, legal documents, or security-registration documents may be required before disbursement.",
      "Additional co-owner/spouse consent or legal documentation may be requested where applicable under law and the transaction structure.",
    ],
  },
  STUDENT: {
    eligibility: [
      "Provide the student's identity and details of the education programme and institution.",
      "Show the cost and timing of tuition, accommodation, materials, or other eligible education expenses.",
      "Identify a realistic repayment source; where required by the active product, provide an acceptable sponsor or guarantor.",
      "Ensure the proposed repayment plan is affordable for the responsible borrower/sponsor.",
      "Meet the active product's amount/term limits and complete applicable identity and credit checks.",
    ],
    requiredDocuments: [
      ...COMMON_DOCUMENTS,
      "Admission/enrolment letter or other evidence of the programme and institution.",
      "Tuition invoice, fee schedule, or other evidence of the education costs being financed.",
      "Sponsor/guarantor identity and income evidence where a sponsor/guarantor is part of the application.",
    ],
    additionalEvidence: [
      "Academic records, student ID, payment deadlines, accommodation invoice, or education-related quotations may be requested.",
      "Sponsor consent, relationship evidence, or additional affordability documents may be requested where applicable.",
      "Where funds are intended for a specific institution or supplier, payment instructions or supplier details may be required.",
    ],
  },
  EMERGENCY: {
    eligibility: [
      "Be at least 18 years old and explain the urgent, legitimate expense for which funds are requested.",
      "Provide enough detail to assess the amount needed and the timing of the expense.",
      "Demonstrate a practical and affordable repayment plan, including the source and timing of repayment.",
      "Meet the active product's amount/term limits and complete applicable identity, affordability, and credit checks.",
    ],
    requiredDocuments: [
      ...COMMON_DOCUMENTS,
      "Income or livelihood evidence relevant to the applicant's circumstances.",
      "A clear description of the expense and the requested amount.",
    ],
    additionalEvidence: [
      "An invoice, quotation, bill, repair estimate, or other expense evidence may be requested depending on the purpose.",
      "Additional information may be requested to clarify urgency, amount, payee, or the repayment plan.",
      "Medical or other sensitive documents should only be submitted through the secure application flow if specifically requested; do not send them through public channels.",
    ],
  },
  ASSET_FINANCE: {
    eligibility: [
      "Be at least 18 years old or apply through an eligible registered business where the active product permits.",
      "Identify the asset, supplier, intended use, and the business or personal purpose it supports.",
      "Demonstrate repayment capacity and a reasonable relationship between the asset cost and intended use.",
      "Provide information needed to verify the supplier, asset, price, and any required contribution/security.",
      "Meet the active product's limits and pass applicable identity, affordability, and credit checks.",
    ],
    requiredDocuments: [
      ...COMMON_DOCUMENTS,
      "Supplier quotation or pro-forma invoice identifying the asset and purchase price.",
      "Income/business evidence appropriate to the applicant and intended use.",
      "Supplier contact details and asset specifications available at application.",
    ],
    additionalEvidence: [
      "Asset valuation, inspection, serial number, insurance, warranty, or registration documents may be requested depending on the asset.",
      "Proof of contribution, business-use plan, or evidence of expected income generated by the asset may be requested.",
      "For business assets, registration, financial statements, licences, or authorisation records may be requested where applicable.",
    ],
  },
  MICROFINANCE: {
    eligibility: [
      "Be at least 18 years old and explain the household, livelihood, or income-generating purpose.",
      "Demonstrate a realistic ability to repay without compromising essential household needs.",
      "Provide verifiable information about income sources, regular expenses, and existing credit obligations.",
      "Meet the active product's amount and term limits and complete the applicable checks.",
    ],
    requiredDocuments: [
      ...COMMON_DOCUMENTS,
      "Income, livelihood, or small-business activity evidence appropriate to the applicant's circumstances.",
      "A short explanation of the use of funds and proposed repayment source.",
    ],
    additionalEvidence: [
      "Simple sales/cash-flow records, stock or input costs, business location details, group records, or guarantor information may be requested where relevant.",
      "Additional household budget or existing debt information may be requested to assess affordability.",
    ],
  },
  TRADE_FINANCE: {
    eligibility: [
      "Be at least 18 years old and authorised to represent the applicant business.",
      "Have a verifiable purchase, supply, import/export, or other trade transaction supported by documents.",
      "Explain the transaction timeline and demonstrate how expected proceeds will repay the facility.",
      "Provide business and counterparty details needed for verification and any applicable compliance checks.",
      "Meet the active product's amount/term limits and complete the required assessment.",
    ],
    requiredDocuments: [
      ...COMMON_DOCUMENTS,
      "Business registration and authorised representative details, where applicable.",
      "Business bank statements and available sales, revenue, or cash-flow records.",
      "Purchase order, supplier invoice, sales contract, or other document describing the trade transaction.",
    ],
    additionalEvidence: [
      "Buyer/supplier contracts, shipping/customs records, delivery notes, insurance, licences, or proof of payment may be requested according to the transaction.",
      "Counterparty identity, trade references, tax records, and evidence of expected proceeds may be requested during verification.",
    ],
  },
  GROUP: {
    eligibility: [
      "Meet the membership, participation, and eligibility rules of the applicable group-lending product.",
      "Provide accurate information about the group and each member included in the application.",
      "Understand the repayment responsibilities, any group guarantee or shared-liability terms, and the consequences of missed payments before signing.",
      "Demonstrate the group/member repayment source and agree to the documented use of funds.",
      "Meet the active product's limits and complete identity and other checks required for the group and its members.",
    ],
    requiredDocuments: [
      ...COMMON_DOCUMENTS,
      "Group registration or membership records, where applicable.",
      "Member list and member identity/contact details requested by the group application.",
      "Relevant member income/livelihood evidence and the group's proposed borrowing purpose.",
    ],
    additionalEvidence: [
      "Group resolution/minutes approving the borrowing, authorised signatory details, and guarantor records may be requested.",
      "Group savings, cash-flow, activity, or repayment records may be requested where they exist and are relevant.",
      "The lender may request evidence that each member understands the obligations before the agreement is signed.",
    ],
  },
  DEBT_CONSOLIDATION: {
    eligibility: [
      "Provide details of the debts intended for consolidation and explain the proposed benefit.",
      "Demonstrate an affordable repayment plan and disclose existing credit obligations accurately.",
      "Meet the active product's limits and pass identity, affordability, and credit assessment.",
    ],
    requiredDocuments: [
      ...COMMON_DOCUMENTS,
      "Current statements or settlement figures for debts proposed for consolidation.",
      "Income evidence and details of current monthly debt repayments.",
    ],
    additionalEvidence: [
      "Creditor contact/payment details, settlement letters, or payoff instructions may be requested.",
      "Noble may verify debts and settlement amounts before any approved disbursement is made.",
    ],
  },
};

const DEFAULT_PROFILE: LoanRequirementProfile = {
  eligibility: [
    "Review the active product's purpose and confirm that it matches your borrowing need.",
    "Be legally eligible to enter into a loan agreement and provide verifiable identity/contact information.",
    "Demonstrate a reliable repayment source and affordability after essential expenses and existing obligations.",
    "Request an amount and term within the active product limits and complete the required assessment.",
  ],
  requiredDocuments: [
    ...COMMON_DOCUMENTS,
    "Evidence of income, employment, business activity, or another repayment source relevant to the product.",
    "Any product-specific information explicitly requested in the application before submission.",
  ],
  additionalEvidence: [
    "Supporting quotations, contracts, business/activity records, guarantor information, or other evidence may be requested depending on the purpose.",
    "The application flow and loan team will confirm any additional items relevant to your circumstances.",
  ],
};

export function getLoanRequirementProfile(
  loanType?: string | null,
): LoanRequirementProfile {
  const key = String(loanType || "")
    .trim()
    .toUpperCase()
    .replace(/[ -]+/g, "_");
  return PROFILES[key] || DEFAULT_PROFILE;
}

/**
 * Resolve product aliases only for the public requirements display. An explicit
 * configured loanType takes precedence; product name is a fallback for older
 * configurations that lack a stable type.
 */
export function getLoanProductType(product: {
  loanType?: string | null;
  title?: string | null;
  name?: string | null;
}): string {
  const explicit = String(product.loanType || "")
    .trim()
    .toUpperCase();
  if (explicit) return explicit;

  const name = String(product.title || product.name || "").toLowerCase();
  if (name.includes("business") || name.includes("working capital"))
    return "BUSINESS";
  if (name.includes("vehicle") || name.includes("auto") || name.includes("car"))
    return "AUTO";
  if (name.includes("salary") || name.includes("payday"))
    return "SALARY_ADVANCE";
  if (name.includes("agri") || name.includes("farm")) return "AGRICULTURAL";
  if (name.includes("mortgage") || name.includes("home loan"))
    return "MORTGAGE";
  if (name.includes("student") || name.includes("education")) return "STUDENT";
  if (name.includes("emergency")) return "EMERGENCY";
  if (name.includes("asset")) return "ASSET_FINANCE";
  if (name.includes("microfinance") || name.includes("micro finance"))
    return "MICROFINANCE";
  if (name.includes("trade")) return "TRADE_FINANCE";
  if (name.includes("group")) return "GROUP";
  if (name.includes("consolidation")) return "DEBT_CONSOLIDATION";
  return "PERSONAL";
}
