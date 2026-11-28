import type { Procedure } from "@/components/ProcedureGuide";

export const staffGuides: Procedure[] = [
  {
    title: "Record a manual borrower payment", area: "Payments", path: "/dashboard/payments",
    keywords: "repayment bank transfer deposit receipt installment collections submit pending",
    steps: [
      "Open Payments, enter the loan ID, and select Load loan. Verify the borrower and the loan reference.",
      "Enter the amount received, payment method, channel, and external transaction reference. Add a collection note if needed.",
      "Select Review request, check the details, and select Submit for approval.",
      "Open My payment requests to check its status. A pending request leaves the loan balance and ledger as they were before submission.",
    ],
    check: "If you see a duplicate reference, check My payment requests and the posted ledger before submitting again.",
  },
  {
    title: "Approve or reject a borrower payment", area: "Payments", path: "/dashboard/payments#payment-reviews",
    keywords: "business owner payment review verify receipt pending approve reject posted",
    steps: [
      "Sign in as the Business Owner and open Payments → Payment reviews.",
      "Match the loan, amount, and transaction reference against the bank statement or collection record.",
      "Select Approve and post after verification. If the details are wrong or unsupported, select Reject and enter the reason.",
      "Confirm the request shows Posted and review the movement in the posted payment ledger.",
    ],
    check: "Use Refresh reviews to reload pending requests. If posting fails, read the error before attempting another approval.",
  },
  {
    title: "Review posted receipts and allocations", area: "Payments", path: "/dashboard/payments",
    keywords: "principal interest management fee penalty transaction ledger",
    steps: ["Open the payment transaction ledger in Payments.", "Match the loan and reference to the receipt, then check the recorded amount and allocation components.", "Open the loan record to review the current balance and installment schedule."],
  },
  {
    title: "Review the financial dashboard", area: "Loans & borrowers", path: "/dashboard",
    keywords: "overview totals portfolio collection balance",
    steps: ["Check the snapshot date and portfolio scope in Financial Overview.", "Use Refresh to reload the figures.", "Open Loan Portfolio, Payments, or Collections to investigate an amount."],
  },
  {
    title: "Find or register a borrower", area: "Loans & borrowers", path: "/dashboard/borrowers",
    keywords: "add new customer client edit identity",
    steps: ["Search Borrowers for an existing profile.", "Open the matching profile and confirm the identity. If a profile needs an update, use its edit action.", "If the borrower is new, select Add Borrower, complete the required fields, and save."],
    check: "If a duplicate is reported, review the existing record before creating another.",
  },
  {
    title: "Create a loan application", area: "Loans & borrowers", path: "/dashboard/loans/new",
    keywords: "new loan credit request application borrower product term",
    steps: ["Select the correct borrower and loan product.", "Enter the principal, term, and required application details. Review the quoted fees and repayment terms.", "Save the application and check its status in Loan Portfolio."],
  },
  {
    title: "Review, approve, or disburse a loan", area: "Loans & borrowers", path: "/dashboard/loans",
    keywords: "authorize approved funded release borrower disbursement historical backdate closed accounting period",
    steps: ["Find the loan in Loan Portfolio and open its details.", "Review the borrower, terms, documents, and status. Use the available approval action if your role permits it.", "When funds are released, record the actual disbursement date and verify the recorded principal and cash movement."],
    check: "If the posting month is closed, ask an authorized accountant to review the period and the supported posting procedure.",
  },
  {
    title: "Correct or amend an existing loan", area: "Loans & borrowers", path: "/dashboard/loans",
    keywords: "amend principal legacy imported excel correct date purpose correction requests",
    steps: ["Open the loan and choose the available correction or principal amendment action.", "Enter the corrected information, reason, and supporting evidence required by the form.", "Track the request in the corresponding queue. Complete any additional execution step shown after approval."],
    check: "Imported corrections and principal amendments use different approval paths. Use the action offered on the affected loan.",
  },
  {
    title: "Review a collection case", area: "Loans & borrowers", path: "/dashboard/collections",
    keywords: "arrears overdue past due recovery follow up borrower",
    steps: ["Open Collections and filter to the cases you need to review.", "Open the relevant loan and verify the amount due and last payment before contacting the borrower.", "Use the available follow-up actions to record the outcome."],
  },
  {
    title: "Estimate payments or review lending indicators", area: "Loans & borrowers", path: "/dashboard/calculator",
    keywords: "calculator estimate lending intelligence scenarios rate loan term affordability",
    steps: ["Enter the proposed amount, term, and rate in the calculator.", "Review the estimate and confirm the actual product terms in the loan application before quoting them.", "For portfolio indicators, use Lending Intelligence or Portfolio Risk if available to your role."],
  },
  {
    title: "Review balances and financial statements", area: "Accounting & expenses", path: "/dashboard/accounting",
    keywords: "trial balance profit loss cash flow ledger statement journal debit credit reconciliation",
    steps: ["Set the reporting dates in Accounting.", "Use Trial Balance for account balances, Balance Sheet for financial position, and Profit & Loss or Cash Flow for movements in the selected period.", "Open Journal and expand a relevant entry to check its debits and credits."],
    check: "The ending cash balance includes the starting balance and period movements. Confirm actual cash against the bank statement.",
  },
  {
    title: "Set up the main loan bank account", area: "Accounting & expenses", path: "/dashboard/accounting",
    keywords: "gl 1000 main loan bank account add setup funding disbursement receipt privacy",
    steps: ["Open Accounting → Bank Accounts → Add Account and choose Bank.", "Enter an internal label such as Main Loan Bank and select Link the main loan bank account to GL 1000.", "Save and verify that the account shows GL 1000 and its existing balance. Bank name and account number are unnecessary for this link."],
    check: "The link uses the existing ledger history. Reconcile its recorded balance with the bank statement.",
  },
  {
    title: "Correct a bank opening balance", area: "Accounting & expenses", path: "/dashboard/accounting",
    keywords: "example money wrong opening journal reversal equity adjustment approved",
    steps: ["In Accounting → Journal, expand the original BANK_ACCOUNT_OPENING entry and verify the account and amount.", "Ask an authorized finance reviewer for an approved JOURNAL_REVERSAL request ID for the original journal. Enter that ID and the reason in Correct opening balance.", "Check the offset journal and the balance through the correction date."],
  },
  {
    title: "Record an operating expense", area: "Accounting & expenses", path: "/dashboard/expenses",
    keywords: "rent salary paid from supplier invoice receipt cost",
    steps: ["Select Record Expense, then enter the date, category, amount, and approved financial approval ID.", "Choose the existing account that paid and enter the payment method and any required reference.", "Attach the receipt if available, check the summary, and save. Verify the expense in the list."],
    check: "The category explains the spending. The payment account identifies where the money came from.",
  },
  {
    title: "Run and compare financial reports", area: "Reports & compliance", path: "/dashboard/reports",
    keywords: "general report export totals as of period compare accounting portfolio",
    steps: ["Open Reports and choose the report and date range.", "Check the portfolio scope and whether the figures represent activity during the period or balances as of a date.", "Use the same dates and scope when comparing to Accounting or a regulatory report."],
  },
  {
    title: "Review portfolio risk", area: "Reports & compliance", path: "/dashboard/reports/portfolio-risk",
    keywords: "par at risk arrears risk ratios delinquency",
    steps: ["Open Portfolio Risk and check the report date and portfolio filters.", "Review overdue balances and risk groupings, then investigate exceptions against the relevant loan and collection records."],
  },
  {
    title: "Prepare a BNR report", area: "Reports & compliance", path: "/dashboard/reports/regulatory/bnr",
    keywords: "regulatory central bank template financial statements workbook export",
    steps: ["Open BNR Reports and choose the reporting period and schedule.", "Check totals and any validation messages before generating an export.", "Review the downloaded workbook and reconcile comparable totals to Accounting for the same dates and scope."],
  },
  {
    title: "Review or export Credit Bureau data", area: "Reports & compliance", path: "/dashboard/reports/regulatory/crb",
    keywords: "crb credit bureau report download borrower compliance",
    steps: ["Open Credit Bureau and select the required reporting filters.", "Review the records and resolve data warnings before downloading the selected format.", "Handle the export through your approved reporting process."],
  },
  {
    title: "Import historical loans", area: "Administration", path: "/dashboard/import",
    keywords: "legacy excel spreadsheet historical upload validation",
    steps: ["Open Import Legacy Loans and follow the template instructions on the page.", "Upload the file and review the validation results. Correct the source rows that fail.", "After import, check loan counts and balances against the original records."],
  },
  {
    title: "Configure loan products", area: "Administration", path: "/dashboard/products",
    keywords: "interest fee loan product rates settings term",
    steps: ["Open Loan Products and select a product or add one.", "Verify the rates, fees, terms, and status against the approved product rules before saving.", "Check the product terms on a new application before using it in production."],
  },
  {
    title: "Review a pending loan approval", area: "Loans & borrowers", path: "/dashboard/approvals",
    keywords: "authorize loan pricing review pending approve reject interest fee",
    steps: ["Open Loan Approvals and find the application awaiting a decision.", "Verify the proposed terms and final contractual pricing against the supporting record.", "Approve the loan if the terms are correct, or use the rejection action and record the reason."],
  },
  {
    title: "Manage staff access and review audit history", area: "Administration", path: "/dashboard/users",
    keywords: "roles permissions staff reset audit log activity restore users",
    steps: ["Open Users & Roles and find the staff member.", "Choose permissions appropriate to their job and verify the resulting role after saving.", "Use Audit Log to verify sensitive actions and the user or loan recycle bin for authorized recovery."],
  },
  {
    title: "Review deleted loans and users", area: "Administration", path: "/dashboard/loans/recycle-bin",
    keywords: "restore recycled deleted loan user recovery",
    steps: ["Open the relevant Loan Recycle Bin or user recycle bin.", "Confirm the exact record and review the outcome before using any authorized restore action."],
  },
  {
    title: "Work with tasks, messages, and notifications", area: "Daily work", path: "/dashboard/tasks",
    keywords: "task assigned due inbox messages alerts notification",
    steps: ["Open My Tasks to see work assigned to you.", "Use Notifications to find linked events and Messages for staff conversations.", "Open the underlying record to verify a task is complete before updating its status."],
  },
  {
    title: "Find or upload an internal document", area: "Daily work", path: "/dashboard/documents",
    keywords: "file attachment internal document upload download",
    steps: ["Open Internal Documents and search for the record or file.", "Review the file and its access scope. If uploading, use the relevant category and verify the file appears after saving."],
  },
  {
    title: "Review exchange rates", area: "Administration", path: "/dashboard/currencies",
    keywords: "fx currency foreign exchange rates",
    steps: ["Open FX Rates and check the currency pair and effective date.", "If your role permits updates, use the approved rate source and check the saved rate before relying on it."],
  },
  {
    title: "Manage organization and website settings", area: "Administration", path: "/dashboard/settings",
    keywords: "organizations website public settings configuration profile",
    steps: ["Open Settings and select the area you are authorized to change.", "Review the existing values before saving. Verify the change in the affected workspace or website."],
  },
  {
    title: "Review integration status", area: "Administration", path: "/dashboard/webhooks",
    keywords: "webhooks api keys integrations delivery credentials",
    steps: ["Open Webhooks to review endpoint or delivery status. Use Regulatory Reports → API Keys for approved key management.", "Confirm the intended integration and scope before making changes. Keep keys and secrets out of staff messages."],
  },
  {
    title: "Resolve a closed accounting period", area: "Accounting & expenses", path: "/dashboard/accounting",
    keywords: "posting month not open date backdate historical disbursement closed period",
    steps: ["Check the actual transaction date in the supporting records and identify the posting month.", "Ask an authorized accountant to review the period. If opening it is appropriate, use Open a posting month and select the correct month.", "Return to the original transaction and check its status before retrying."],
  },
  {
    title: "Record funding or a bank transfer", area: "Accounting & expenses", path: "/dashboard/accounting",
    keywords: "bank accounts deposit withdrawal transfer owner funding opening",
    steps: ["Open Accounting → Bank Accounts and identify the account or accounts that actually moved the funds.", "Choose the appropriate deposit, withdrawal, or transfer action. Verify the amount, reference, and counter account where required.", "Check the journal and match the movement to the bank statement."],
    check: "A transfer between company accounts moves cash between them. A supplier payment belongs in Expenses.",
  },
  {
    title: "Track a loan correction request", area: "Loans & borrowers", path: "/dashboard/loans/correction-requests",
    keywords: "correction requests history status pending approved",
    steps: ["Find the request for the affected loan and check its status and reason.", "Open the loan or linked request to see any follow-up action after the decision."],
  },
  {
    title: "Review loan correction approvals", area: "Loans & borrowers", path: "/dashboard/principal-amendments",
    keywords: "principal amendment approvals imported corrections owner",
    steps: ["Open Loan correction approvals and identify the request and affected loan.", "Review the requested changes and supporting records from the linked loan.", "Follow the decision and execution steps shown on the correction page."],
  },
  {
    title: "Review who changed a record", area: "Administration", path: "/dashboard/audit",
    keywords: "audit log actor time activity journal changes",
    steps: ["Open Audit Log and locate the event by its date, action, or related record.", "Compare the entry with the underlying transaction or request if you need to investigate."],
  },
  {
    title: "Manage the public website", area: "Administration", path: "/dashboard/settings/website",
    keywords: "website public branding content company settings site",
    steps: ["Open Website Settings and review the current public content.", "Make the authorized change and check the published page after saving."],
  },
  {
    title: "Check organization details", area: "Administration", path: "/dashboard/organizations",
    keywords: "organization tenant company details setup",
    steps: ["Open the organization workspace if your role has access.", "Confirm the organization before reviewing or changing its details."],
  },
  {
    title: "Review regulatory API keys", area: "Reports & compliance", path: "/dashboard/reports/regulatory/api-keys",
    keywords: "api key regulatory integration credential rotate revoke",
    steps: ["Open Regulatory Reports → API Keys and identify the integration and its intended access.", "Use the actions available to your role and verify which integrations depend on a key before revoking it.", "Store credentials in your organization’s approved secret manager."],
  },
  {
    title: "Review lending indicators", area: "Loans & borrowers", path: "/dashboard/lending-intelligence",
    keywords: "lending intelligence portfolio metrics signals risk",
    steps: ["Open Lending Intelligence and check the period and scope of the indicators.", "Use the underlying loans, collections, or risk report to investigate a figure."],
  },
  {
    title: "Sign in or reset a staff password", area: "Account access", path: "/login",
    keywords: "login password forgot reset account locked one time code otp",
    steps: ["Open staff sign-in and enter the credentials issued by your organization.", "Complete the verification step shown on screen. Enter the code only on the sign-in page.", "If you forgot your password, use Forgot password? and follow the recovery instructions for your registered account."],
    check: "If you cannot use the registered email or verification method, contact your administrator.",
  },
  {
    title: "Set up an authenticator for staff sign-in", area: "Account access", path: "/mfa-setup",
    keywords: "mfa two factor authenticator verification qr code",
    steps: ["Open the verification setup after signing in.", "Add the account in your authenticator app using the setup details shown on screen.", "Enter the current authenticator code and select Verify & Enable."],
    check: "If you lose access to the authenticator, contact your administrator.",
  },
];
