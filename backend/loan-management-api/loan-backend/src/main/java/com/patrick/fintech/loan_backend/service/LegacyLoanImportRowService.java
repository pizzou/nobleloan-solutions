package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.ImportRowResult;
import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.repository.BorrowerRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.security.HmacIndexer;
import com.patrick.fintech.loan_backend.util.FinancialPolicy;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class LegacyLoanImportRowService {

        private static final Pattern LEADING_APOSTROPHES = Pattern.compile("^['\"`‘’“”_ \\s]+");

        private static final int MONEY_SCALE = 2;

        private static final int RATE_SCALE = 6;

        private static final int CALCULATION_SCALE = 16;

        private static final int MIN_DURATION_MONTHS = 1;

        private static final int MAX_DURATION_MONTHS = 6;

        private static final int MAX_TEXT_LENGTH = 5000;

        private static final int MAX_NAME_LENGTH = 150;

        private static final int MAX_PHONE_LENGTH = 50;

        private static final int MAX_NATIONAL_ID_LENGTH = 100;

        private static final int MAX_EMAIL_LENGTH = 320;

        private static final BigDecimal MIN_LOAN_AMOUNT = new BigDecimal("500000.00");

        private static final BigDecimal MAX_LOAN_AMOUNT = null;

        private static final BigDecimal MONTHLY_INTEREST_RATE = new BigDecimal("5.00");

        private static final BigDecimal MONTHLY_MANAGEMENT_FEE_RATE = new BigDecimal("5.00");

        private static final BigDecimal TOTAL_MONTHLY_CHARGE_RATE = MONTHLY_INTEREST_RATE.add(
                        MONTHLY_MANAGEMENT_FEE_RATE);

        // Noble Loan: application fee and application fee are the same one-time charge.
        // Default is 2% of gross principal and is deducted at disbursement.
        private static final BigDecimal APPLICATION_FEE_RATE = new BigDecimal("2.00");

        private static final BigDecimal MAX_IMPORT_RATE = new BigDecimal("1000.00");

        private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(
                        MONEY_SCALE,
                        RoundingMode.HALF_UP);

        private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

        private static final Set<String> ALLOWED_IMPORT_STATUSES = Set.of(
                        "ACTIVE",
                        "OVERDUE",
                        "PAID",
                        "CLOSED",
                        "DEFAULTED",
                        "WRITTEN_OFF",
                        "RESTRUCTURED");

        private static final Set<LoanStatus> STATUSES_REQUIRING_NEXT_DUE_DATE = Set.of(
                        LoanStatus.ACTIVE,
                        LoanStatus.OVERDUE,
                        LoanStatus.DEFAULTED,
                        LoanStatus.RESTRUCTURED);

        private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
                        DateTimeFormatter.ISO_LOCAL_DATE.withResolverStyle(ResolverStyle.STRICT),
                        DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT),
                        DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT),
                        DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT),
                        DateTimeFormatter.ofPattern("d-M-uuuu").withResolverStyle(ResolverStyle.STRICT),
                        DateTimeFormatter.ofPattern("M/d/uuuu").withResolverStyle(ResolverStyle.STRICT),
                        DateTimeFormatter.ofPattern("M/d/uu").withResolverStyle(ResolverStyle.SMART),
                        DateTimeFormatter.ofPattern("d/M/uu").withResolverStyle(ResolverStyle.SMART),
                        DateTimeFormatter.ofPattern("dd-MM-uu").withResolverStyle(ResolverStyle.SMART),
                        DateTimeFormatter.ofPattern("d-M-uu").withResolverStyle(ResolverStyle.SMART),
                        DateTimeFormatter.ofPattern("dd-MMM-uuuu", Locale.ENGLISH)
                                        .withResolverStyle(ResolverStyle.SMART),
                        DateTimeFormatter.ofPattern("d-MMM-uuuu", Locale.ENGLISH)
                                        .withResolverStyle(ResolverStyle.SMART),
                        DateTimeFormatter.ofPattern("dd-MMM-uu", Locale.ENGLISH).withResolverStyle(ResolverStyle.SMART),
                        DateTimeFormatter.ofPattern("d-MMM-uu", Locale.ENGLISH).withResolverStyle(ResolverStyle.SMART),
                        DateTimeFormatter.ofPattern("dd MMM uuuu", Locale.ENGLISH)
                                        .withResolverStyle(ResolverStyle.SMART),
                        DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH)
                                        .withResolverStyle(ResolverStyle.SMART),
                        DateTimeFormatter.ofPattern("dd MMM uu", Locale.ENGLISH).withResolverStyle(ResolverStyle.SMART),
                        DateTimeFormatter.ofPattern("d MMM uu", Locale.ENGLISH).withResolverStyle(ResolverStyle.SMART),
                        DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.ENGLISH)
                                        .withResolverStyle(ResolverStyle.SMART),
                        DateTimeFormatter.ofPattern("MMM d, uu", Locale.ENGLISH)
                                        .withResolverStyle(ResolverStyle.SMART));

        private final BorrowerRepository borrowerRepo;

        private final LoanRepository loanRepo;

        private final LoanService loanService;

        private final LoanReferenceService loanReferenceService;

        private final AccountingService accountingService;

        private final Validator validator;

        // ================================================================
        // IMPORT ROW
        // ================================================================

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public ImportRowResult importRow(
                        Map<String, String> row,
                        int rowNumber,
                        Organization org,
                        Long importBatchId,
                        boolean commit,
                        Map<String, Borrower> sessionBorrowers,
                        LegacyImportReferenceSession sessionLoanReferences) {

                String borrowerAction = null;
                boolean borrowerCreatedInThisRow = false;

                try {

                        // ========================================================
                        // IMPORT CONTEXT
                        // ========================================================

                        validateImportContext(
                                        row,
                                        rowNumber,
                                        org,
                                        importBatchId,
                                        sessionBorrowers,
                                        sessionLoanReferences);

                        // Sheet reconciliation is completed before any write; do
                        // not create borrowers, NLS numbers or GL journals for
                        // ambiguous cross-sheet copies.
                        if (row.containsKey("__import_issue")) {
                                ImportRowResult blocked = fail(rowNumber, row.get("__import_issue"));
                                blocked.setBorrowerName((row.getOrDefault("first_name", "") + " "
                                                + row.getOrDefault("last_name", "")).trim());
                                return blocked;
                        }

                        // Capture the workbook identity SEPARATELY from the new
                        // sequential public loan number. An upload retry must not
                        // manufacture a second loan just because its NLS number
                        // would be different on the next run.
                        String suppliedReference = opt(row, "loan_reference", null);
                        String sourceReference = suppliedReference == null || suppliedReference.isBlank()
                                        ? null
                                        : normalizeReference(suppliedReference);
                        String loanIdentity = com.patrick.fintech.loan_backend.util.LegacyLoanIdentity
                                        .stableIdentity(org.getId(), row);
                        String fingerprint = legacyRowFingerprint(row, org);
                        String olderSystemReference = resolveImportReference(row, org);
                        if (loanIdentity != null && (sessionLoanReferences.alreadySeen(fingerprint, sourceReference)
                                        || sessionLoanReferences.alreadySeenIdentity(loanIdentity)
                                        || loanReferenceService.previouslyImported(
                                                        org.getId(), sourceReference, fingerprint,
                                                        olderSystemReference, loanIdentity,
                                                        row.get("national_id").replaceAll("\\s+", ""),
                                                        row.get("start_date")))) {
                                return fail(rowNumber,
                                                "This legacy loan already exists or appears twice in this workbook. "
                                                                + "Check the original loan_reference and previous imports before retrying.");
                        }

                        // ========================================================
                        // BORROWER INFORMATION
                        // ========================================================

                        String nationalId = normalizeNationalId(
                                        req(
                                                        row,
                                                        "national_id"));

                        validateNationalId(
                                        nationalId);

                        String firstName = normalizeRequiredText(
                                        req(
                                                        row,
                                                        "first_name"),
                                        "first_name",
                                        MAX_NAME_LENGTH);

                        String lastName = normalizeRequiredText(
                                        req(
                                                        row,
                                                        "last_name"),
                                        "last_name",
                                        MAX_NAME_LENGTH);

                        String phone = normalizeRequiredText(
                                        req(
                                                        row,
                                                        "phone"),
                                        "phone",
                                        MAX_PHONE_LENGTH);

                        String gender = normalizeGender(
                                        req(
                                                        row,
                                                        "gender"));

                        // ========================================================
                        // LOAN INFORMATION
                        // ========================================================

                        BigDecimal amount = reqMoney(
                                        row,
                                        "amount");

                        validatePositiveMoney(
                                        amount,
                                        "amount");

                        /*
                         * Current platform rule:
                         *
                         * Minimum = RWF 500,000.
                         * There is NO maximum.
                         */
                        if (amount.compareTo(
                                        MIN_LOAN_AMOUNT) < 0) {

                                return fail(
                                                rowNumber,
                                                "amount must be at least "
                                                                + formatMoney(
                                                                                MIN_LOAN_AMOUNT)
                                                                + " "
                                                                + resolveCurrency(
                                                                                row,
                                                                                org));
                        }
                        /*
                         * Historical imports preserve the contractual rate when
                         * the source provides one. A blank historical rate falls
                         * back to the current Noble Loan default of 5% monthly.
                         * Excel commonly stores 5% as 0.05 while CSV files often
                         * store it as 5. Both forms are normalized to percentage
                         * points before the Loan entity is built.
                         */
                        BigDecimal importedInterestRate = optRate(
                                        row,
                                        "interest_rate",
                                        MONTHLY_INTEREST_RATE);

                        validateInterestRate(
                                        importedInterestRate);

                        BigDecimal effectiveInterestRate = importedInterestRate;

                        BigDecimal importedManagementFeeRate = optRate(
                                        row,
                                        "management_fee_rate",
                                        MONTHLY_MANAGEMENT_FEE_RATE);

                        validateInterestRate(importedManagementFeeRate);

                        BigDecimal effectiveManagementFeeRate = importedManagementFeeRate;

                        int durationMonths = reqInteger(
                                        row,
                                        "duration_months");

                        validateDuration(
                                        durationMonths);

                        LocalDate startDate = reqDate(
                                        row,
                                        "start_date");

                        String statusRaw = req(
                                        row,
                                        "status")
                                        .toUpperCase(
                                                        Locale.ROOT)
                                        .trim();

                        validateStatus(
                                        statusRaw);

                        LoanStatus status = LoanStatus.valueOf(
                                        statusRaw);
                        // Imported loans always enter the normal reporting/access scope.
                        // An authorized user may explicitly mark a loan Business-Owner-only
                        // later through the dedicated visibility-management workflow.
                        // Do not require or trust an import spreadsheet column for this control.
                        boolean businessOwnerOnly = false;

                        LocalDate nextDueDate = isHistoricalLoanStatus(statusRaw)
                                        ? optDate(row, "next_due_date", null)
                                        : null;

                        if (STATUSES_REQUIRING_NEXT_DUE_DATE.contains(status)
                                        && nextDueDate == null) {
                                return fail(
                                                rowNumber,
                                                "next_due_date is required for an outstanding "
                                                                + statusRaw
                                                                + " legacy loan. Enter the contractual next due date so arrears can be classified safely.");
                        }

                        // A due date at or before disbursement is commonly an Excel
                        // column-mapping mistake (start date copied as next due date).
                        // Do not invent a date or calculate arrears from disbursement.
                        // The actual oldest unpaid contractual due date is required.
                        if (nextDueDate != null && !nextDueDate.isAfter(startDate)
                                        && status != LoanStatus.PAID && status != LoanStatus.CLOSED) {
                                return fail(rowNumber,
                                                "next_due_date must be later than the original start_date. "
                                                                + "Enter the verified oldest unpaid installment due date; "
                                                                + "days overdue must not start at disbursement.");
                        }

                        // ========================================================
                        // HISTORICAL RATE TYPE
                        // ========================================================

                        /*
                         * Accept the old field for compatibility with imported
                         * files, but normalize the resulting Loan to MONTHLY.
                         */
                        String importedRateType = opt(
                                        row,
                                        "interest_rate_type",
                                        "MONTHLY")
                                        .toUpperCase(
                                                        Locale.ROOT)
                                        .trim();

                        if (!"MONTHLY".equals(importedRateType)) {
                                return fail(
                                                rowNumber,
                                                "interest_rate_type must be MONTHLY in the production engine. "
                                                                + "Annual-rate imports require an approved conversion outside this import path. Got \""
                                                                + importedRateType + "\".");
                        }

                        // ========================================================
                        // LOAN TYPE
                        // ========================================================

                        String loanTypeRaw = opt(
                                        row,
                                        "loan_type",
                                        "PERSONAL")
                                        .toUpperCase(
                                                        Locale.ROOT)
                                        .trim()
                                        .replace(
                                                        ' ',
                                                        '_');

                        Loan.LoanType loanType;

                        try {

                                loanType = Loan.LoanType.valueOf(
                                                loanTypeRaw);

                        } catch (IllegalArgumentException e) {

                                return fail(
                                                rowNumber,
                                                "loan_type \""
                                                                + loanTypeRaw
                                                                + "\" is not recognized. "
                                                                + "Valid values: "
                                                                + Arrays.toString(
                                                                                Loan.LoanType.values()));
                        }

                        // ========================================================
                        // OPTIONAL FINANCIAL VALUES
                        // ========================================================

                        BigDecimal totalPaid = optMoney(
                                        row,
                                        "total_paid");

                        BigDecimal outstandingGiven = optMoney(
                                        row,
                                        "outstanding_balance");

                        BigDecimal totalRepayableGiven = optMoney(
                                        row,
                                        "total_repayable");

                        BigDecimal principalPaidGiven = optMoney(
                                        row,
                                        "principal_paid");

                        BigDecimal principalBalanceGiven = optMoney(
                                        row,
                                        "principal_balance");

                        BigDecimal interestPaidGiven = optMoney(
                                        row,
                                        "interest_paid");

                        BigDecimal interestOutstandingGiven = optMoney(
                                        row,
                                        "interest_outstanding");

                        BigDecimal managementFeePaidGiven = optMoney(
                                        row,
                                        "management_fee_paid");

                        BigDecimal managementFeeOutstandingGiven = optMoney(
                                        row,
                                        "total_management_fee_balance");

                        BigDecimal applicationFeeGiven = optMoney(
                                        row,
                                        "application_fee");

                        BigDecimal applicationFeePaidGiven = optMoney(
                                        row,
                                        "application_fee_paid");

                        BigDecimal applicationFeeOutstandingGiven = optMoney(
                                        row,
                                        "application_fee_outstanding");

                        BigDecimal penaltiesAssessedGiven = optMoney(
                                        row,
                                        "penalties_assessed");

                        BigDecimal penaltiesPaidGiven = optMoney(
                                        row,
                                        "penalties_paid");

                        BigDecimal extensionFeeAssessedGiven = optMoney(
                                        row,
                                        "extension_fee_assessed");

                        BigDecimal extensionFeePaidGiven = optMoney(
                                        row,
                                        "extension_fee_paid");

                        BigDecimal extensionFeeOutstandingGiven = optMoney(
                                        row,
                                        "extension_fee_outstanding");

                        validateOptionalMoney(
                                        totalPaid,
                                        "total_paid");

                        validateOptionalMoney(
                                        outstandingGiven,
                                        "outstanding_balance");

                        validateOptionalMoney(
                                        totalRepayableGiven,
                                        "total_repayable");

                        validateOptionalMoney(principalPaidGiven, "principal_paid");
                        validateOptionalMoney(principalBalanceGiven, "principal_balance");
                        validateOptionalMoney(interestPaidGiven, "interest_paid");
                        validateOptionalMoney(interestOutstandingGiven, "interest_outstanding");
                        validateOptionalMoney(managementFeePaidGiven, "management_fee_paid");
                        validateOptionalMoney(managementFeeOutstandingGiven, "total_management_fee_balance");
                        validateOptionalMoney(applicationFeePaidGiven, "application_fee_paid");
                        validateOptionalMoney(applicationFeeOutstandingGiven, "application_fee_outstanding");
                        validateOptionalMoney(penaltiesAssessedGiven, "penalties_assessed");
                        validateOptionalMoney(penaltiesPaidGiven, "penalties_paid");
                        validateOptionalMoney(extensionFeeAssessedGiven, "extension_fee_assessed");
                        validateOptionalMoney(extensionFeePaidGiven, "extension_fee_paid");
                        validateOptionalMoney(extensionFeeOutstandingGiven, "extension_fee_outstanding");

                        if (totalPaid == null) {
                                totalPaid = ZERO;
                        } else {
                                totalPaid = money(totalPaid);
                        }

                        BigDecimal penaltiesAssessed = money(
                                        penaltiesAssessedGiven != null ? penaltiesAssessedGiven : ZERO);
                        BigDecimal penaltiesPaid = money(
                                        penaltiesPaidGiven != null ? penaltiesPaidGiven : ZERO);

                        BigDecimal extensionFeePaid = money(
                                        extensionFeePaidGiven != null ? extensionFeePaidGiven : ZERO);

                        BigDecimal extensionFeeAssessed = money(
                                        extensionFeeAssessedGiven != null
                                                        ? extensionFeeAssessedGiven
                                                        : (extensionFeeOutstandingGiven != null
                                                                        ? extensionFeePaid.add(
                                                                                        extensionFeeOutstandingGiven)
                                                                        : extensionFeePaid));

                        BigDecimal extensionFeeOutstanding = money(
                                        extensionFeeOutstandingGiven != null
                                                        ? extensionFeeOutstandingGiven
                                                        : extensionFeeAssessed.subtract(extensionFeePaid)
                                                                        .max(ZERO));

                        if (extensionFeePaid.compareTo(extensionFeeAssessed) > 0) {
                                return fail(
                                                rowNumber,
                                                "extension_fee_paid cannot exceed extension_fee_assessed.");
                        }

                        if (extensionFeeAssessedGiven != null
                                        && extensionFeeOutstandingGiven != null
                                        && extensionFeeAssessed.subtract(
                                                        extensionFeePaid.add(extensionFeeOutstanding))
                                                        .abs().compareTo(new BigDecimal("0.01")) > 0) {
                                return fail(
                                                rowNumber,
                                                "extension_fee_paid + extension_fee_outstanding must equal extension_fee_assessed.");
                        }

                        BigDecimal interestPaidHistorical = money(
                                        interestPaidGiven != null ? interestPaidGiven : ZERO);
                        BigDecimal interestOutstandingHistorical = money(
                                        interestOutstandingGiven != null ? interestOutstandingGiven : ZERO);
                        BigDecimal managementFeePaidHistorical = money(
                                        managementFeePaidGiven != null ? managementFeePaidGiven : ZERO);
                        BigDecimal managementFeeOutstandingHistorical = money(
                                        managementFeeOutstandingGiven != null ? managementFeeOutstandingGiven : ZERO);

                        // ========================================================
                        // FINANCIAL CONSISTENCY
                        // ========================================================

                        if (totalPaid.compareTo(
                                        ZERO) < 0) {

                                return fail(
                                                rowNumber,
                                                "total_paid cannot be negative.");
                        }

                        if (outstandingGiven != null
                                        && outstandingGiven.compareTo(
                                                        ZERO) < 0) {

                                return fail(
                                                rowNumber,
                                                "outstanding_balance cannot be negative.");
                        }

                        if (totalRepayableGiven != null
                                        && totalRepayableGiven.compareTo(
                                                        ZERO) < 0) {

                                return fail(
                                                rowNumber,
                                                "total_repayable cannot be negative.");
                        }

                        // ========================================================
                        // DETERMINE HISTORICAL BALANCE
                        // ========================================================

                        BigDecimal totalRepayable;

                        BigDecimal outstandingBalance;

                        if (outstandingGiven != null) {

                                outstandingBalance = money(
                                                outstandingGiven);

                                if (totalRepayableGiven != null) {

                                        totalRepayable = money(
                                                        totalRepayableGiven);

                                } else {

                                        totalRepayable = money(
                                                        totalPaid.add(
                                                                        outstandingBalance));
                                }

                        } else if (totalRepayableGiven != null) {

                                totalRepayable = money(
                                                totalRepayableGiven);

                                outstandingBalance = money(
                                                totalRepayable
                                                                .subtract(
                                                                                totalPaid)
                                                                .max(
                                                                                ZERO));

                        } else {

                                /*
                                 * No historical repayment totals were supplied.
                                 *
                                 * Calculate according to the contractual rates resolved above:
                                 *
                                 * interest rate from the historical source, default 5% monthly
                                 * +
                                 * management fee rate from the historical source, default 5% monthly.
                                 *
                                 * The one-time Application Fee is NOT included here;
                                 * it is deducted from gross funds at disbursement.
                                 */
                                BigDecimal[] calculated = calculateCurrentPlatformLoan(
                                                amount,
                                                durationMonths,
                                                effectiveInterestRate,
                                                effectiveManagementFeeRate);

                                totalRepayable = money(
                                                calculated[1]);

                                outstandingBalance = money(
                                                totalRepayable
                                                                .subtract(
                                                                                totalPaid)
                                                                .max(
                                                                                ZERO));
                        }

                        outstandingBalance = outstandingBalance.max(
                                        ZERO);

                        totalRepayable = totalRepayable.max(
                                        ZERO);

                        // Principal is reconciled independently from interest and management-fee
                        // balances. For the Noble legacy portfolio workbook, column AD is the
                        // historical principal-balance formula and can be stale. When principal_paid
                        // is present it is the authoritative principal collection figure; the current
                        // principal balance is therefore derived as amount - principal_paid.
                        // This fixes cases such as 800,000 paid + 300,000 source balance on a
                        // 1,000,000 loan: the reconciled principal balance is 200,000.
                        BigDecimal principalPaidHistorical;
                        if (principalPaidGiven != null) {
                                principalPaidHistorical = money(principalPaidGiven);
                        } else if (principalBalanceGiven != null) {
                                principalPaidHistorical = money(
                                                amount.subtract(principalBalanceGiven));
                        } else if ("PAID".equals(statusRaw) || "CLOSED".equals(statusRaw)) {
                                // A source row explicitly marked paid/closed must
                                // reconcile principal to zero remaining balance.
                                principalPaidHistorical = money(amount);
                        } else if (totalPaid.compareTo(ZERO) > 0) {
                                return fail(
                                                rowNumber,
                                                "For an open legacy loan with repayments, provide principal_paid or principal_balance. "
                                                                + "total_paid alone cannot safely determine how much principal remains after interest, fees and penalties.");
                        } else {
                                principalPaidHistorical = ZERO;
                        }

                        if (principalPaidHistorical.compareTo(ZERO) < 0) {
                                return fail(
                                                rowNumber,
                                                "principal_balance cannot exceed amount. "
                                                                + "principal_balance=" + principalBalanceGiven
                                                                + ", amount=" + amount);
                        }

                        if (principalPaidHistorical.compareTo(amount) > 0) {
                                return fail(
                                                rowNumber,
                                                "principal_paid cannot exceed amount. "
                                                                + "principal_paid=" + principalPaidHistorical
                                                                + ", amount=" + amount);
                        }

                        BigDecimal principalBalanceHistorical = money(
                                        amount.subtract(principalPaidHistorical).max(ZERO));

                        if (principalBalanceGiven != null
                                        && principalBalanceGiven.subtract(principalBalanceHistorical).abs()
                                                        .compareTo(new BigDecimal("0.01")) > 0) {
                                log.warn(
                                                "Legacy principal balance was stale/inconsistent and was reconciled from principal_paid. "
                                                                + "rowNumber={}, sourcePrincipalBalance={}, derivedPrincipalBalance={}, amount={}",
                                                rowNumber,
                                                money(principalBalanceGiven),
                                                principalBalanceHistorical,
                                                amount);
                        }

                        BigDecimal principalReconciled = money(
                                        principalPaidHistorical.add(principalBalanceHistorical));

                        if (principalReconciled.subtract(amount).abs().compareTo(new BigDecimal("0.01")) > 0) {
                                return fail(
                                                rowNumber,
                                                "principal_paid + principal_balance must equal amount. "
                                                                + "principal_paid=" + principalPaidHistorical
                                                                + ", principal_balance=" + principalBalanceHistorical
                                                                + ", amount=" + amount);
                        }

                        // ========================================================
                        // APPLICATION FEE (CANONICAL FIELD)
                        // ========================================================

                        /*
                         * Historical application fees must come from the source records.
                         * Never apply today's 2% default to an old loan or silently assume
                         * that an assessed fee was paid. Use explicit zero values when the
                         * client confirms a fee was not charged / remains unpaid by zero.
                         */
                        if (applicationFeeGiven == null
                                        || applicationFeePaidGiven == null
                                        || applicationFeeOutstandingGiven == null) {
                                return fail(
                                                rowNumber,
                                                "Provide application_fee, application_fee_paid, and application_fee_outstanding for every legacy loan (enter 0 where appropriate). The importer will not invent a historical fee or assume it was paid.");
                        }

                        BigDecimal applicationFee = money(applicationFeeGiven);
                        BigDecimal applicationFeePaidAmount = money(applicationFeePaidGiven);
                        BigDecimal applicationFeeOutstandingAmount = money(applicationFeeOutstandingGiven);

                        if (applicationFee.compareTo(ZERO) < 0) {
                                return fail(rowNumber, "application_fee cannot be negative.");
                        }
                        if (applicationFeePaidAmount.compareTo(ZERO) < 0
                                        || applicationFeeOutstandingAmount.compareTo(ZERO) < 0) {
                                return fail(rowNumber,
                                                "application_fee_paid and application_fee_outstanding cannot be negative.");
                        }
                        if (applicationFeePaidAmount.compareTo(applicationFee) > 0
                                        || applicationFeeOutstandingAmount.compareTo(applicationFee) > 0) {
                                return fail(rowNumber,
                                                "Application fee paid/outstanding cannot exceed application_fee.");
                        }

                        BigDecimal reconciledApplicationFee = money(
                                        applicationFeePaidAmount.add(applicationFeeOutstandingAmount));
                        if (reconciledApplicationFee.subtract(applicationFee).abs()
                                        .compareTo(new BigDecimal("0.01")) > 0) {
                                return fail(
                                                rowNumber,
                                                "application_fee_paid + application_fee_outstanding must equal application_fee. "
                                                                + "paid=" + applicationFeePaidAmount
                                                                + ", outstanding=" + applicationFeeOutstandingAmount
                                                                + ", fee=" + applicationFee);
                        }

                        BigDecimal applicationFeeRate = amount.compareTo(ZERO) > 0
                                        ? applicationFee.multiply(ONE_HUNDRED)
                                                        .divide(amount, RATE_SCALE, RoundingMode.HALF_UP)
                                        : APPLICATION_FEE_RATE;

                        // ========================================================
                        // BORROWER MATCHING
                        // ========================================================

                        String nationalIdHash = HmacIndexer.index(
                                        nationalId);

                        Borrower borrower = sessionBorrowers.get(
                                        nationalIdHash);

                        if (borrower == null) {

                                Optional<Borrower> existingBorrower = borrowerRepo
                                                .findByNationalIdHashAndOrganization_Id(
                                                                nationalIdHash,
                                                                org.getId());

                                if (existingBorrower.isPresent()) {

                                        borrower = existingBorrower.get();

                                        borrowerAction = "MATCHED_EXISTING_BORROWER";
                                }

                        } else {

                                borrowerAction = "MATCHED_SESSION_BORROWER";
                        }

                        // ========================================================
                        // CREATE BORROWER
                        // ========================================================

                        if (borrower == null) {

                                borrowerCreatedInThisRow = true;
                                borrower = Borrower.builder()
                                                .organization(
                                                                org)
                                                .firstName(
                                                                firstName)
                                                .lastName(
                                                                lastName)
                                                .nationalId(
                                                                nationalId)
                                                .email(
                                                                resolveEmail(
                                                                                row,
                                                                                nationalId,
                                                                                org.getId()))
                                                .phone(
                                                                phone)
                                                .gender(
                                                                gender)
                                                .maritalStatus(
                                                                opt(
                                                                                row,
                                                                                "marital_status",
                                                                                "UNKNOWN"))
                                                .address(
                                                                opt(
                                                                                row,
                                                                                "address",
                                                                                null))
                                                .monthlyIncome(
                                                                optDouble(
                                                                                row,
                                                                                "monthly_income"))
                                                .kycStatus(
                                                                "PENDING")
                                                .status(
                                                                Borrower.BorrowerStatus.ACTIVE)
                                                .imported(
                                                                true)
                                                .build();

                                if (commit) {

                                        try {

                                                /*
                                                 * Validate the borrower explicitly before persistence.
                                                 *
                                                 * Hibernate's Bean Validation normally runs at flush/commit.
                                                 * In an async REQUIRES_NEW import transaction that can happen
                                                 * after this method has left the row-level try/catch, turning
                                                 * one bad legacy row into an unexpected worker failure.
                                                 *
                                                 * Running the same Bean Validation contract here keeps
                                                 * validation failures row-scoped and prevents malformed
                                                 * borrowers from reaching the database transaction.
                                                 */
                                                Set<ConstraintViolation<Borrower>> borrowerViolations = validator
                                                                .validate(borrower);

                                                if (!borrowerViolations.isEmpty()) {
                                                        String validationMessage = borrowerViolations.stream()
                                                                        .map(ConstraintViolation::getMessage)
                                                                        .filter(message -> message != null
                                                                                        && !message.isBlank())
                                                                        .distinct()
                                                                        .sorted()
                                                                        .reduce((left, right) -> left + "; " + right)
                                                                        .orElse("Borrower validation failed.");

                                                        return fail(
                                                                        rowNumber,
                                                                        validationMessage);
                                                }

                                                borrower = borrowerRepo.save(
                                                                borrower);

                                                borrowerAction = "CREATED_NEW_BORROWER";

                                        } catch (DataIntegrityViolationException e) {

                                                log.warn(
                                                                "Concurrent borrower creation conflict. "
                                                                                + "rowNumber={}, organizationId={}, nationalIdHash={}",
                                                                rowNumber,
                                                                org.getId(),
                                                                nationalIdHash,
                                                                e);

                                                return fail(
                                                                rowNumber,
                                                                "Borrower could not be created because another "
                                                                                + "record appears to have created the same borrower "
                                                                                + "at the same time. Please retry this row.");
                                        }

                                } else {

                                        borrowerAction = "CREATED_NEW_BORROWER_PREVIEW";
                                }
                        }

                        if (borrowerAction == null) {

                                borrowerAction = commit
                                                ? "MATCHED_EXISTING_BORROWER"
                                                : "MATCHED_EXISTING_BORROWER_PREVIEW";
                        }

                        // In commit mode a newly-created borrower is cached only
                        // after the loan row has committed. This prevents a failed
                        // loan row from leaving a rolled-back/transient borrower in
                        // the in-memory session cache. Preview mode may cache the
                        // transient borrower because nothing is persisted.
                        if (!commit || !borrowerCreatedInThisRow) {
                                sessionBorrowers.put(nationalIdHash, borrower);
                        }

                        // ========================================================
                        // LOAN REFERENCE
                        // ========================================================

                        // ========================================================
                        // HISTORICAL STATUS
                        // ========================================================

                        boolean historicalLoan = isHistoricalLoanStatus(
                                        statusRaw);

                        // ========================================================
                        // DISBURSEMENT TIMESTAMP
                        // ========================================================

                        /*
                         * Loan.disbursedAt is LocalDateTime.
                         *
                         * Historical imported loans use the start date at
                         * midnight as their historical disbursement timestamp.
                         */
                        LocalDateTime disbursedAt = historicalLoan
                                        ? startDate.atStartOfDay()
                                        : null;

                        // ========================================================
                        // MATURITY DATE
                        // ========================================================

                        LocalDate maturityDate = startDate.plusMonths(
                                        durationMonths);

                        // ========================================================
                        // HISTORICAL TOTALS
                        // ========================================================

                        BigDecimal totalHistoricalInterest = money(
                                        interestPaidHistorical.add(interestOutstandingHistorical));

                        BigDecimal totalHistoricalManagementFee = money(
                                        managementFeePaidHistorical.add(managementFeeOutstandingHistorical));

                        if (interestPaidGiven != null || managementFeePaidGiven != null
                                        || principalPaidGiven != null || principalBalanceGiven != null
                                        || penaltiesPaidGiven != null || extensionFeePaidGiven != null) {
                                totalPaid = money(
                                                principalPaidHistorical
                                                                .add(interestPaidHistorical)
                                                                .add(managementFeePaidHistorical)
                                                                .add(penaltiesPaid)
                                                                .add(extensionFeePaid));
                        }

                        // Some legacy exports label the remaining PRINCIPAL as
                        // outstanding_balance, while other exports use that column for
                        // the complete amount still due. If the supplied value exactly
                        // matches the independently reconciled principal balance, treat
                        // it as principal-only and reconstruct total repayment outstanding
                        // from the separately supplied unpaid repayment components. Never
                        // overwrite the principal sub-ledger with a total contractual due.
                        boolean outstandingInputIsPrincipalOnly = outstandingGiven != null
                                        && outstandingGiven.subtract(principalBalanceHistorical).abs()
                                                        .compareTo(new BigDecimal("0.01")) <= 0;
                        if (outstandingInputIsPrincipalOnly) {
                                outstandingBalance = money(
                                                principalBalanceHistorical
                                                                .add(interestOutstandingHistorical)
                                                                .add(managementFeeOutstandingHistorical)
                                                                .add(extensionFeeOutstanding)
                                                                .add(penaltiesAssessed.subtract(penaltiesPaid)
                                                                                .max(ZERO)));
                        }

                        if (totalRepayableGiven == null) {
                                if (outstandingGiven != null && !outstandingInputIsPrincipalOnly) {
                                        // If the source explicitly gives the amount
                                        // still due but not the original total, derive
                                        // the contractual total from final paid + due
                                        // values so the two figures reconcile.
                                        totalRepayable = money(totalPaid.add(outstandingBalance));
                                } else {
                                        // totalRepayable covers the repayment obligation across
                                        // principal and assessed repayment charges. The one-time
                                        // application fee is handled separately as a disbursement
                                        // deduction/receivable and is not added to scheduled repayment.
                                        totalRepayable = money(
                                                        amount
                                                                        .add(totalHistoricalInterest)
                                                                        .add(totalHistoricalManagementFee)
                                                                        .add(extensionFeeAssessed)
                                                                        .add(penaltiesAssessed));
                                }
                        }

                        // total_repayable and total_paid describe the complete repayment
                        // obligation and all repayments allocated to it. The import's
                        // outstanding_balance column may instead be PRINCIPAL ONLY. Never
                        // use that principal-only source value as the aggregate balance in
                        // the total-repayment equation.
                        BigDecimal totalRepaymentOutstanding = money(
                                        totalRepayable.subtract(totalPaid).max(ZERO));
                        BigDecimal componentOutstanding = money(
                                        principalBalanceHistorical
                                                        .add(interestOutstandingHistorical)
                                                        .add(managementFeeOutstandingHistorical)
                                                        .add(extensionFeeOutstanding)
                                                        .add(penaltiesAssessed.subtract(penaltiesPaid).max(ZERO)));

                        if (totalRepayable.compareTo(totalPaid) < 0) {
                                return fail(rowNumber,
                                                "total_repayable cannot be less than total_paid.");
                        }

                        // An explicitly supplied aggregate total is not sufficient evidence
                        // to invent the allocation of unpaid amounts between principal,
                        // interest, management fees, extension fees and penalties. Require
                        // those component balances to reconcile before creating the loan;
                        // otherwise the imported loan could pass the aggregate check while
                        // its GL receivable accounts are wrong.
                        if (totalRepayableGiven != null
                                        && componentOutstanding.subtract(totalRepaymentOutstanding).abs()
                                                        .compareTo(new BigDecimal("0.01")) > 0) {
                                return fail(rowNumber,
                                                "Legacy balance components do not reconcile: total_repayable - total_paid must equal principal_balance + interest_outstanding + management_fee_outstanding + extension_fee_outstanding + penalty_outstanding. "
                                                                + "total_repayable=" + totalRepayable
                                                                + ", total_paid=" + totalPaid
                                                                + ", aggregate_outstanding=" + totalRepaymentOutstanding
                                                                + ", component_outstanding=" + componentOutstanding
                                                                + ". Supply or correct the missing historical component balances; principal outstanding must not be substituted for total outstanding.");
                        }

                        // Keep this local variable as aggregate repayment outstanding for
                        // overdue/paid-status classification only. Loan.outstandingBalance
                        // is persisted separately from principalBalanceHistorical below.
                        outstandingBalance = totalRepaymentOutstanding;

                        if (outstandingBalance.add(new BigDecimal("0.01"))
                                        .compareTo(principalBalanceHistorical) < 0) {
                                return fail(
                                                rowNumber,
                                                "The total outstanding_balance cannot be lower than principal_balance. Check principal, interest, fees and total-paid fields; the imported amounts do not reconcile.");
                        }

                        if (("PAID".equals(statusRaw) || "CLOSED".equals(statusRaw))
                                        && outstandingBalance.compareTo(new BigDecimal("0.01")) > 0) {
                                return fail(
                                                rowNumber,
                                                "A PAID/CLOSED loan must have zero outstanding_balance after historical totals are reconciled.");
                        }
                        if (("PAID".equals(statusRaw) || "CLOSED".equals(statusRaw))
                                        && principalBalanceHistorical.compareTo(new BigDecimal("0.01")) > 0) {
                                return fail(
                                                rowNumber,
                                                "A PAID/CLOSED loan must have zero principal_balance. The supplied principal history conflicts with the paid/closed status.");
                        }

                        // Do not classify a paid-off loan as delinquent just
                        // because an old next-due date remains in the workbook.
                        BigDecimal remainingReceivableForClassification = money(
                                        outstandingBalance
                                                        .add(extensionFeeOutstanding)
                                                        .add(applicationFee.subtract(applicationFeePaidAmount)
                                                                        .max(ZERO))
                                                        .add(penaltiesAssessed.subtract(penaltiesPaid).max(ZERO)));

                        int importedDaysOverdue = calculateImportedDaysOverdue(
                                        status,
                                        nextDueDate,
                                        remainingReceivableForClassification);

                        if ((status == LoanStatus.ACTIVE || status == LoanStatus.OVERDUE)
                                        && remainingReceivableForClassification.compareTo(ZERO) > 0) {
                                status = importedDaysOverdue > 0
                                                ? LoanStatus.OVERDUE
                                                : LoanStatus.ACTIVE;
                        }

                        // ========================================================
                        // BUILD LOAN
                        // ========================================================

                        // Allocate only after the row has passed financial and
                        // identity validation. A failed preview row consumes no
                        // preview number; a commit uses nextval atomically.
                        if (loanIdentity == null) {
                                return fail(rowNumber, "A verified 16-digit national_id, contractual "
                                                + "start_date and positive principal are required "
                                                + "before importing a historical loan.");
                        }
                        String referenceNumber = commit
                                        ? loanReferenceService.allocate()
                                        : sessionLoanReferences.previewReference(loanReferenceService);

                        Loan.LoanBuilder builder = Loan.builder()
                                        .referenceNumber(
                                                        referenceNumber)
                                        .organization(
                                                        org)
                                        .borrower(
                                                        borrower)
                                        .loanType(
                                                        loanType)
                                        .status(
                                                        status)
                                        .businessOwnerOnly(
                                                        businessOwnerOnly)

                                        // ------------------------------------------------
                                        // GROSS PRINCIPAL
                                        // ------------------------------------------------

                                        .amount(
                                                        amount)

                                        .disbursedAmount(
                                                        amount)

                                        .netDisbursedAmount(
                                                        money(amount.subtract(applicationFee).max(ZERO)))

                                        // ------------------------------------------------
                                        // CONTRACTUAL INTEREST
                                        // ------------------------------------------------

                                        .interestRate(
                                                        effectiveInterestRate)

                                        // ------------------------------------------------
                                        // FIXED MANAGEMENT FEE
                                        // ------------------------------------------------

                                        .managementFeeRate(
                                                        effectiveManagementFeeRate)

                                        // ------------------------------------------------
                                        // MONTHLY RATE TYPE
                                        // ------------------------------------------------

                                        .interestRateType(
                                                        importedRateType)

                                        // ------------------------------------------------
                                        // TERM
                                        // ------------------------------------------------

                                        .durationMonths(
                                                        durationMonths)

                                        // ------------------------------------------------
                                        // CURRENCY
                                        // ------------------------------------------------

                                        .currency(
                                                        resolveCurrency(
                                                                        row,
                                                                        org))

                                        // ------------------------------------------------
                                        // APPLICATION FEE
                                        // One-time fee; deducted from gross disbursement.
                                        // ------------------------------------------------

                                        .applicationFeeRate(
                                                        applicationFeeRate)

                                        .applicationFee(
                                                        applicationFee)

                                        .applicationFeePaid(
                                                        applicationFeePaidAmount)

                                        .totalRepayable(
                                                        totalRepayable)

                                        .totalPaid(
                                                        totalPaid)

                                        /*
                                         * The Loan.outstanding_balance column is the
                                         * authoritative PRINCIPAL receivable and is
                                         * protected by ck_loan_principal_reconciliation:
                                         *
                                         * principal_paid + outstanding_balance = amount
                                         *
                                         * The legacy source's outstanding_balance may be the
                                         * TOTAL contractual amount still payable (principal +
                                         * interest/fees), so persisting that source value here
                                         * can violate the database invariant. The principal
                                         * balance already reconciled above is the value that
                                         * belongs in Loan.outstandingBalance. Other historical
                                         * receivables remain in interestOutstanding,
                                         * managementFeeOutstanding, extension fees and penalties.
                                         */
                                        .outstandingBalance(
                                                        principalBalanceHistorical)

                                        .principalPaid(
                                                        principalPaidHistorical)

                                        .penaltiesAssessed(
                                                        penaltiesAssessed)

                                        .penaltiesPaid(
                                                        penaltiesPaid)

                                        .extensionFeeAssessed(
                                                        extensionFeeAssessed)

                                        .extensionFeePaid(
                                                        extensionFeePaid)

                                        .extensionFeeOutstanding(
                                                        extensionFeeOutstanding)

                                        .daysOverdue(
                                                        importedDaysOverdue)

                                        // ------------------------------------------------
                                        // MANAGEMENT FEE TOTALS
                                        // ------------------------------------------------

                                        .managementFee(
                                                        totalHistoricalManagementFee)

                                        .managementFeePaid(
                                                        managementFeePaidHistorical)

                                        .managementFeeOutstanding(
                                                        managementFeeOutstandingHistorical)

                                        // ------------------------------------------------
                                        // INTEREST TOTALS
                                        // ------------------------------------------------

                                        .totalInterest(
                                                        totalHistoricalInterest)

                                        .interestPaid(
                                                        interestPaidHistorical)

                                        .interestOutstanding(
                                                        interestOutstandingHistorical)

                                        // ------------------------------------------------
                                        // DATES
                                        // ------------------------------------------------

                                        .startDate(
                                                        startDate)

                                        .approvedAt(
                                                        historicalLoan
                                                                        ? startDate
                                                                        : null)

                                        /*
                                         * LocalDateTime.
                                         */
                                        .disbursedAt(
                                                        disbursedAt)

                                        /*
                                         * Keep the exact timestamp field synchronized.
                                         */
                                        .disbursedAtTimestamp(
                                                        disbursedAt)

                                        .maturityDate(
                                                        maturityDate)

                                        // ------------------------------------------------
                                        // NEXT PAYMENT DATE
                                        // ------------------------------------------------

                                        .nextDueDate(
                                                        nextDueDate)

                                        .nextPaymentDate(
                                                        nextDueDate)

                                        // ------------------------------------------------
                                        // NOTES
                                        // ------------------------------------------------

                                        .notes(
                                                        normalizeOptionalText(
                                                                        opt(
                                                                                        row,
                                                                                        "notes",
                                                                                        null),
                                                                        "notes",
                                                                        MAX_TEXT_LENGTH))

                                        .internalNotes(
                                                        buildInternalImportNote(
                                                                        importBatchId,
                                                                        importedInterestRate,
                                                                        importedRateType)
                                                                        + " Historical NLS financial balances were imported as opening loan state; no historical accounting journal was reposted, preventing double-counting."
                                                                        + " Initial arrears classification was derived from next_due_date and the imported remaining receivable."
                                                                        + (row.containsKey("__source_sheet")
                                                                                        ? " Source worksheet: "
                                                                                                        + row.get("__source_sheet")
                                                                                                                        .replaceAll("[\\r\\n]",
                                                                                                                                        " ")
                                                                                                                        .substring(0, Math
                                                                                                                                        .min(80, row.get(
                                                                                                                                                        "__source_sheet")
                                                                                                                                                        .length()))
                                                                                                        + ", row "
                                                                                                        + rowNumber
                                                                                                        + "."
                                                                                        : "")
                                                                        + (row.containsKey("__source_local_no")
                                                                                        ? " Worksheet local NO: "
                                                                                                        + row.get("__source_local_no")
                                                                                                                        .replaceAll("[\\r\\n]",
                                                                                                                                        " ")
                                                                                                                        .substring(0, Math
                                                                                                                                        .min(40, row.get(
                                                                                                                                                        "__source_local_no")
                                                                                                                                                        .length()))
                                                                                                        + "."
                                                                                        : "")
                                                                        + (businessOwnerOnly
                                                                                        ? " This loan is Business-Owner-only and must remain excluded from ordinary reporting and access scopes."
                                                                                        : ""))

                                        // ------------------------------------------------
                                        // IMPORT FLAGS
                                        // ------------------------------------------------

                                        .imported(
                                                        true)
                                        .legacySourceReference(sourceReference)
                                        .legacyImportFingerprint(fingerprint)
                                        .legacyLoanIdentity(loanIdentity)

                                        .importBatchId(
                                                        importBatchId);

                        Loan loan = builder.build();

                        // ========================================================
                        // PREVIEW MODE
                        // ========================================================

                        if (!commit) {

                                sessionLoanReferences.recordSuccess(fingerprint, sourceReference, loanIdentity);

                                return ImportRowResult.builder()
                                                .rowNumber(
                                                                rowNumber)
                                                .success(
                                                                true)
                                                .borrowerAction(
                                                                borrowerAction)
                                                .borrowerName(
                                                                firstName
                                                                                + " "
                                                                                + lastName)
                                                .loanReferenceNumber(
                                                                referenceNumber)
                                                .build();
                        }

                        // ========================================================
                        // SAVE LOAN
                        // ========================================================

                        try {

                                loan = loanRepo.saveAndFlush(
                                                loan);

                        } catch (DataIntegrityViolationException e) {
                                // PostgreSQL has rejected this transaction; roll
                                // back any borrower changes made earlier in the row.
                                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                                log.warn(
                                                "Legacy loan import database constraint violation. "
                                                                + "rowNumber={}, organizationId={}, referenceNumber={}",
                                                rowNumber,
                                                org.getId(),
                                                referenceNumber,
                                                e);

                                return fail(
                                                rowNumber,
                                                "Loan with reference number \""
                                                                + referenceNumber
                                                                + "\" could not be imported because "
                                                                + "a conflicting record already exists or "
                                                                + "the database rejected the record.");
                        }

                        // --------------------------------------------------------
                        // ACCOUNTING OPENING BALANCE
                        // --------------------------------------------------------
                        // Historical loans are already disbursed. Record only the
                        // remaining receivable position as an opening journal so
                        // accounting does not replay historical cash movements.
                        accountingService.postHistoricalLoanOpening(loan);

                        sessionLoanReferences.recordSuccess(fingerprint, sourceReference, loanIdentity);

                        // Only cache a newly-created borrower after both the loan
                        // and its opening accounting entry have succeeded.
                        if (borrowerCreatedInThisRow) {
                                sessionBorrowers.put(nationalIdHash, borrower);
                        }

                        // ========================================================
                        // SUCCESS LOG
                        // ========================================================

                        log.info(
                                        "Legacy loan imported successfully. "
                                                        + "rowNumber={}, organizationId={}, loanId={}, "
                                                        + "referenceNumber={}, borrowerId={}, status={}, "
                                                        + "amount={}, totalRepayable={}, totalPaid={}, "
                                                        + "outstandingBalance={}, interestRate={}%, "
                                                        + "managementFeeRate={}%, applicationFeeRate={}%, "
                                                        + "applicationFee={}, applicationFeePaid={}, "
                                                        + "duration={} months, batchId={}",
                                        rowNumber,
                                        org.getId(),
                                        loan.getId(),
                                        referenceNumber,
                                        borrower.getId(),
                                        status,
                                        amount,
                                        totalRepayable,
                                        totalPaid,
                                        outstandingBalance,
                                        effectiveInterestRate,
                                        effectiveManagementFeeRate,
                                        applicationFeeRate,
                                        applicationFee,
                                        applicationFeePaidAmount,
                                        durationMonths,
                                        importBatchId);

                        // ========================================================
                        // RETURN SUCCESS
                        // ========================================================

                        return ImportRowResult.builder()
                                        .rowNumber(
                                                        rowNumber)
                                        .success(
                                                        true)
                                        .borrowerAction(
                                                        borrowerAction)
                                        .borrowerName(
                                                        firstName
                                                                        + " "
                                                                        + lastName)
                                        .loanReferenceNumber(
                                                        referenceNumber)
                                        .build();

                } catch (IllegalArgumentException e) {
                        if (commit) {
                                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                        }
                        log.warn(
                                        "Legacy loan import validation failure. "
                                                        + "rowNumber={}, organizationId={}, error={}",
                                        rowNumber,
                                        org != null
                                                        ? org.getId()
                                                        : null,
                                        e.getMessage());

                        return fail(
                                        rowNumber,
                                        e.getMessage());

                } catch (Exception e) {
                        if (commit) {
                                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                        }
                        log.error(
                                        "Unexpected legacy loan import failure. "
                                                        + "rowNumber={}, organizationId={}",
                                        rowNumber,
                                        org != null
                                                        ? org.getId()
                                                        : null,
                                        e);

                        return fail(
                                        rowNumber,
                                        "Unexpected import error. "
                                                        + "The row was not imported. "
                                                        + "Reference row number: "
                                                        + rowNumber);
                }
        }

        // ================================================================
        // CURRENT PLATFORM LOAN CALCULATION
        // ================================================================

        /**
         * Calculates current platform repayment using:
         *
         * 5% monthly interest
         * +
         * 5% monthly management fee
         * =
         * 10% monthly recurring charge.
         *
         * The one-time Application Fee is excluded because it is a
         * disbursement deduction.
         *
         * Returns:
         *
         * [monthlyInstallment, totalRepayable]
         */
        private BigDecimal[] calculateCurrentPlatformLoan(
                        BigDecimal principal,
                        int months,
                        BigDecimal interestRate,
                        BigDecimal managementFeeRate) {

                BigDecimal normalizedPrincipal = money(principal);

                if (normalizedPrincipal.compareTo(MIN_LOAN_AMOUNT) < 0) {
                        throw new IllegalArgumentException(
                                        "Loan principal must be at least " + formatMoney(MIN_LOAN_AMOUNT));
                }

                if (months < MIN_DURATION_MONTHS || months > MAX_DURATION_MONTHS) {
                        throw new IllegalArgumentException(
                                        "Loan duration must be between "
                                                        + MIN_DURATION_MONTHS + " and " + MAX_DURATION_MONTHS
                                                        + " months");
                }

                /*
                 * Noble Loan uses a reducing-principal schedule:
                 * principal is divided across the contractual months; interest
                 * and management fee are each charged monthly on opening principal.
                 * The one-time application fee is excluded from repayment total.
                 *
                 * For RWF 10,000,000 over 3 months this produces total recurring
                 * charges of RWF 2,000,000 and total repayable of RWF 12,000,000.
                 */
                BigDecimal balance = normalizedPrincipal;
                BigDecimal totalInterest = ZERO;
                BigDecimal totalManagementFee = ZERO;
                BigDecimal firstInstallment = ZERO;

                for (int month = 1; month <= months; month++) {
                        FinancialPolicy.ScheduleLine line = FinancialPolicy.contractualScheduleLine(
                                        balance,
                                        months - month + 1,
                                        interestRate,
                                        managementFeeRate);

                        if (month == 1) {
                                firstInstallment = line.installment();
                        }
                        totalInterest = totalInterest.add(line.interest());
                        totalManagementFee = totalManagementFee.add(line.managementFee());
                        balance = line.remainingBalance();
                }

                BigDecimal totalRepayable = money(
                                normalizedPrincipal.add(totalInterest).add(totalManagementFee));

                return new BigDecimal[] { money(firstInstallment), totalRepayable };
        }

        // ================================================================
        // IMPORT CONTEXT VALIDATION
        // ================================================================

        private void validateImportContext(
                        Map<String, String> row,
                        int rowNumber,
                        Organization org,
                        Long importBatchId,
                        Map<String, Borrower> sessionBorrowers,
                        LegacyImportReferenceSession sessionLoanReferences) {

                if (row == null) {

                        throw new IllegalArgumentException(
                                        "Import row "
                                                        + rowNumber
                                                        + " is empty.");
                }

                if (rowNumber <= 0) {

                        throw new IllegalArgumentException(
                                        "Invalid import row number: "
                                                        + rowNumber);
                }

                if (org == null) {

                        throw new IllegalArgumentException(
                                        "Organization is required for legacy import.");
                }

                if (org.getId() == null) {

                        throw new IllegalArgumentException(
                                        "Organization ID is required for legacy import.");
                }

                if (sessionBorrowers == null) {

                        throw new IllegalArgumentException(
                                        "Import borrower session cache is required.");
                }

                if (sessionLoanReferences == null) {
                        throw new IllegalArgumentException(
                                        "Import loan-reference cache is required.");
                }

                if (importBatchId != null
                                && importBatchId <= 0) {

                        throw new IllegalArgumentException(
                                        "Invalid import batch ID.");
                }
        }

        // ================================================================
        // NATIONAL ID
        // ================================================================

        private String normalizeNationalId(
                        String value) {

                if (value == null) {

                        throw new IllegalArgumentException(
                                        "national_id is required.");
                }

                String normalized = value
                                .replace(
                                                "\uFEFF",
                                                "")
                                .trim();

                if (normalized.startsWith("'")
                                && normalized.length() > 1) {

                        normalized = normalized.substring(
                                        1).trim();

                        log.debug(
                                        "Removed Excel text-prefix apostrophe from national_id.");
                }

                if (normalized.startsWith("’")
                                && normalized.length() > 1) {

                        normalized = normalized.substring(
                                        1).trim();

                        log.debug(
                                        "Removed Unicode Excel-style apostrophe from national_id.");
                }

                normalized = normalized.replaceAll(
                                "\\s+",
                                "");

                if (normalized.length() >= 2
                                && ((normalized.startsWith("\"")
                                                && normalized.endsWith("\""))
                                                ||
                                                (normalized.startsWith("'")
                                                                && normalized.endsWith("'")))) {

                        normalized = normalized.substring(
                                        1,
                                        normalized.length() - 1).trim();
                }

                return normalized;
        }

        private void validateNationalId(
                        String nationalId) {

                if (nationalId == null
                                || nationalId.isBlank()) {

                        throw new IllegalArgumentException(
                                        "national_id is required.");
                }

                /*
                 * Borrower.nationalId is a contractual/KYC field and the entity
                 * requires exactly 16 digits. Validate the same rule here, before
                 * Hibernate attempts to persist the Borrower. This prevents a
                 * malformed legacy row from reaching the JPA Bean Validation
                 * event at transaction commit time, where it previously escaped
                 * the row-level error handling and failed the async import worker.
                 */
                if (!nationalId.matches("\\d{16}")) {

                        throw new IllegalArgumentException(
                                        "national_id must contain exactly 16 digits. Got "
                                                        + nationalId.length()
                                                        + " digits.");
                }

                if (nationalId.length() > MAX_NATIONAL_ID_LENGTH) {

                        throw new IllegalArgumentException(
                                        "national_id is too long.");
                }
        }

        // ================================================================
        // STATUS
        // ================================================================

        private void validateStatus(
                        String status) {

                if (status == null
                                || !ALLOWED_IMPORT_STATUSES.contains(
                                                status)) {

                        throw new IllegalArgumentException(
                                        "status must be one of "
                                                        + ALLOWED_IMPORT_STATUSES
                                                        + " for historical imported loans. "
                                                        + "Got \""
                                                        + status
                                                        + "\".");
                }
        }

        private boolean isHistoricalLoanStatus(
                        String status) {

                return ALLOWED_IMPORT_STATUSES.contains(
                                status);
        }

        // ================================================================
        // MONEY PARSING
        // ================================================================

        private BigDecimal reqMoney(
                        Map<String, String> row,
                        String key) {
                String value = req(row, key);
                try {
                        return money(parseDecimalValue(value, key, true));
                } catch (NumberFormatException e) {
                        throw new IllegalArgumentException(
                                        "\"" + key + "\" must be a valid decimal amount. Got \"" + value + "\".");
                }
        }

        private BigDecimal optMoney(
                        Map<String, String> row,
                        String key) {
                String value = row.get(key);
                if (isBlankOrSkipped(value))
                        return null;
                try {
                        return money(parseDecimalValue(value, key, true));
                } catch (NumberFormatException e) {
                        throw new IllegalArgumentException(
                                        "\"" + key + "\" must be a valid decimal amount if provided. Got \"" + value
                                                        + "\".");
                }
        }

        private void validateOptionalMoney(
                        BigDecimal value,
                        String field) {

                if (value == null) {
                        return;
                }

                if (value.compareTo(
                                ZERO) < 0) {

                        throw new IllegalArgumentException(
                                        field
                                                        + " cannot be negative.");
                }

                if (value.precision() > 30) {

                        throw new IllegalArgumentException(
                                        field
                                                        + " contains an unreasonably large value.");
                }
        }

        private void validatePositiveMoney(
                        BigDecimal value,
                        String field) {

                if (value == null
                                || value.compareTo(
                                                ZERO) <= 0) {

                        throw new IllegalArgumentException(
                                        field
                                                        + " must be greater than zero.");
                }
        }

        private BigDecimal money(
                        BigDecimal value) {

                if (value == null) {

                        return ZERO;
                }

                return value.setScale(
                                MONEY_SCALE,
                                RoundingMode.HALF_UP);
        }

        private String formatMoney(
                        BigDecimal value) {

                return money(
                                value).toPlainString();
        }

        // ================================================================
        // INTEREST RATE
        // ================================================================

        private BigDecimal optRate(
                        Map<String, String> row,
                        String key,
                        BigDecimal fallback) {

                String value = row.get(key);
                if (isBlankOrSkipped(value)) {
                        return fallback;
                }

                return parseImportedRate(value, key);
        }

        private BigDecimal reqRate(
                        Map<String, String> row,
                        String key) {

                return parseImportedRate(req(row, key), key);
        }

        /**
         * Parse imported rates consistently across CSV, Excel, preview and
         * asynchronous commit paths. Historical workbooks commonly represent
         * 5% as either 5, 5%, or Excel's fractional 0.05 value.
         */
        private BigDecimal parseImportedRate(
                        String rawValue,
                        String key) {

                String value = normalizeImportedValue(rawValue);

                if (value.isBlank()) {
                        throw new IllegalArgumentException(
                                        "\"" + key + "\" must be a valid interest rate. Got a blank value.");
                }

                String lower = value.toLowerCase(Locale.ROOT);
                boolean explicitPercent = lower.contains("%")
                                || lower.endsWith("percent")
                                || lower.endsWith("pct");

                String numeric = value
                                .replace("%", "")
                                .replaceAll("(?i)percent\\s*$", "")
                                .replaceAll("(?i)pct\\s*$", "")
                                .replace(",", "")
                                .trim();

                try {
                        BigDecimal parsed = new BigDecimal(numeric);

                        if (parsed.compareTo(ZERO) < 0) {
                                throw new IllegalArgumentException(
                                                "\"" + key + "\" cannot be negative. Got \"" + rawValue + "\".");
                        }

                        // A bare value between 0 and 1 is interpreted as an
                        // Excel-style percentage fraction (0.05 = 5%).
                        if (!explicitPercent
                                        && parsed.compareTo(BigDecimal.ONE) <= 0) {
                                parsed = parsed.multiply(ONE_HUNDRED);
                        }

                        return parsed.setScale(
                                        RATE_SCALE,
                                        RoundingMode.HALF_UP);

                } catch (NumberFormatException e) {
                        throw new IllegalArgumentException(
                                        "\"" + key + "\" must be a valid interest rate. Got \"" + rawValue + "\".");
                }
        }

        private void validateInterestRate(
                        BigDecimal rate) {

                if (rate == null
                                || rate.compareTo(
                                                ZERO) < 0) {

                        throw new IllegalArgumentException(
                                        "interest_rate cannot be negative.");
                }

                if (rate.compareTo(
                                MAX_IMPORT_RATE) > 0) {

                        throw new IllegalArgumentException(
                                        "interest_rate is unreasonably high. "
                                                        + "Maximum accepted import rate is "
                                                        + MAX_IMPORT_RATE
                                                        + "%.");
                }
        }

        // ================================================================
        // INTEGER PARSING
        // ================================================================

        private int reqInteger(
                        Map<String, String> row,
                        String key) {
                String value = req(row, key);
                try {
                        BigDecimal decimal = parseDecimalValue(value, key, true);
                        if (decimal.stripTrailingZeros().scale() > 0) {
                                throw new IllegalArgumentException(
                                                "\"" + key + "\" must be a whole number.");
                        }
                        return decimal.intValueExact();
                } catch (NumberFormatException | ArithmeticException e) {
                        throw new IllegalArgumentException(
                                        "\"" + key + "\" must be a valid whole number. Got \"" + value + "\".");
                }
        }

        private void validateDuration(
                        int durationMonths) {

                if (durationMonths < MIN_DURATION_MONTHS
                                || durationMonths > MAX_DURATION_MONTHS) {

                        throw new IllegalArgumentException(
                                        "duration_months must be between "
                                                        + MIN_DURATION_MONTHS
                                                        + " and "
                                                        + MAX_DURATION_MONTHS
                                                        + ".");
                }
        }

        // ================================================================
        // DATE PARSING
        // ================================================================

        private LocalDate reqDate(
                        Map<String, String> row,
                        String key) {

                String value = normalizeImportedValue(req(row, key));
                LocalDate parsed = parseFlexibleDate(value);

                if (parsed != null) {
                        return parsed;
                }

                throw new IllegalArgumentException(
                                "\"" + key + "\" isn't a recognized date. "
                                                + "Preferred format is YYYY-MM-DD. Got \"" + value + "\".");
        }

        private boolean isHistoricalStatusMarker(String value) {
                if (value == null) {
                        return false;
                }

                String normalized = value.trim().toUpperCase(Locale.ROOT);
                return normalized.contains("RESTRUCTURE");
        }

        private LocalDate parseFlexibleDate(String rawValue) {
                String value = normalizeImportedValue(rawValue);
                if (value.isBlank() || isHistoricalStatusMarker(value)) {
                        return null;
                }

                for (DateTimeFormatter formatter : DATE_FORMATS) {
                        try {
                                return LocalDate.parse(value, formatter);
                        } catch (DateTimeParseException ignored) {
                        }
                }

                try {
                        BigDecimal serial = new BigDecimal(value);
                        if (serial.compareTo(BigDecimal.ONE) >= 0
                                        && serial.compareTo(BigDecimal.valueOf(2958465)) <= 0
                                        && serial.stripTrailingZeros().scale() <= 0) {
                                return LocalDate.of(1899, 12, 30)
                                                .plusDays(serial.longValueExact());
                        }
                } catch (NumberFormatException | ArithmeticException ignored) {
                }

                return null;
        }

        // ================================================================
        // OPTIONAL DATE
        // ================================================================

        private LocalDate optDate(
                        Map<String, String> row,
                        String key,
                        LocalDate fallback) {

                String raw = row.get(key);

                if (isBlankOrSkipped(raw)) {
                        return fallback;
                }

                String value = normalizeImportedValue(raw);

                // Historical monthly portfolio sheets sometimes store a
                // status marker such as "loan restructure" in the next-due-date
                // column. It is not a date. The parser separately derives
                // RESTRUCTURED status from the same historical columns.
                if (isHistoricalStatusMarker(value)) {
                        return fallback;
                }

                LocalDate parsed = parseFlexibleDate(value);
                if (parsed != null) {
                        return parsed;
                }

                throw new IllegalArgumentException(
                                "\"" + key + "\" isn't a recognized optional date. "
                                                + "Preferred format is YYYY-MM-DD. Got \""
                                                + value + "\".");
        }

        // ================================================================
        // GENDER
        // ================================================================

        private String normalizeGender(
                        String value) {

                if (value == null) {
                        return "UNKNOWN";
                }

                String gender = value
                                .trim()
                                .toUpperCase(
                                                Locale.ROOT);

                if ("M".equals(gender)
                                || "MALE".equals(gender)) {

                        return "Male";
                }

                if ("F".equals(gender)
                                || "FEMALE".equals(gender)) {

                        return "Female";
                }

                if (gender.isBlank()) {
                        return "UNKNOWN";
                }

                return value.trim();
        }

        // ================================================================
        // REQUIRED TEXT
        // ================================================================

        private String normalizeRequiredText(
                        String value,
                        String field,
                        int maxLength) {

                String normalized = value == null
                                ? ""
                                : value.trim();

                if (normalized.isBlank()) {

                        throw new IllegalArgumentException(
                                        field
                                                        + " is required.");
                }

                if (normalized.length() > maxLength) {

                        throw new IllegalArgumentException(
                                        field
                                                        + " is too long. Maximum length is "
                                                        + maxLength
                                                        + " characters.");
                }

                return normalized;
        }

        private String normalizeOptionalText(
                        String value,
                        String field,
                        int maxLength) {

                if (value == null
                                || value.isBlank()) {

                        return null;
                }

                String normalized = value.trim();

                if (normalized.length() > maxLength) {

                        throw new IllegalArgumentException(
                                        field
                                                        + " is too long. Maximum length is "
                                                        + maxLength
                                                        + " characters.");
                }

                return normalized;
        }

        // ================================================================
        // REQUIRED FIELD
        // ================================================================

        private String req(
                        Map<String, String> row,
                        String key) {
                String value = row.get(key);
                if (isBlankOrSkipped(value)) {
                        throw new IllegalArgumentException(
                                        "\"" + key + "\" is required but was blank.");
                }
                return normalizeImportedValue(value);
        }

        private String opt(
                        Map<String, String> row,
                        String key,
                        String fallback) {
                String value = row.get(key);
                if (isBlankOrSkipped(value))
                        return fallback;
                return normalizeImportedValue(value);
        }

        // ================================================================
        // OPTIONAL DOUBLE
        // ================================================================

        private Double optDouble(
                        Map<String, String> row,
                        String key) {
                String value = row.get(key);
                if (isBlankOrSkipped(value))
                        return null;
                try {
                        BigDecimal parsed = parseDecimalValue(value, key, true);
                        double result = parsed.doubleValue();
                        if (!Double.isFinite(result)) {
                                throw new IllegalArgumentException(
                                                "\"" + key + "\" must be a finite number.");
                        }
                        if (result < 0) {
                                throw new IllegalArgumentException(
                                                "\"" + key + "\" cannot be negative.");
                        }
                        return result;
                } catch (NumberFormatException e) {
                        throw new IllegalArgumentException(
                                        "\"" + key + "\" must be a valid number if provided. Got \"" + value + "\".");
                }
        }

        private boolean isBlankOrSkipped(String value) {
                return value == null || normalizeImportedValue(value).isBlank();
        }

        private String normalizeImportedValue(String value) {
                if (value == null)
                        return "";
                String normalized = value.replace("\uFEFF", "").trim();
                if (normalized.isBlank())
                        return "";

                normalized = LEADING_APOSTROPHES.matcher(normalized).replaceFirst("").trim();

                for (int i = 0; i < 2 && normalized.length() >= 2; i++) {
                        char first = normalized.charAt(0);
                        char last = normalized.charAt(normalized.length() - 1);
                        if ((first == '\'' && last == '\'') || (first == '\"' && last == '\"')) {
                                normalized = normalized.substring(1, normalized.length() - 1).trim();
                        } else {
                                break;
                        }
                }
                return normalized;
        }

        private BigDecimal parseDecimalValue(
                        String value,
                        String key,
                        boolean allowNegative) {
                String normalized = normalizeImportedValue(value)
                                .replace(",", "")
                                .trim();
                if (normalized.isBlank()) {
                        throw new NumberFormatException("Blank numeric value for " + key);
                }
                BigDecimal parsed = new BigDecimal(normalized);
                if (!allowNegative && parsed.compareTo(ZERO) < 0) {
                        throw new NumberFormatException("Negative value for " + key);
                }
                return parsed;
        }

        private String resolveEmail(
                        Map<String, String> row,
                        String nationalId,
                        Long organizationId) {

                String supplied = row.get(
                                "email");

                if (supplied != null
                                && !supplied.isBlank()) {

                        String email = supplied
                                        .trim()
                                        .toLowerCase(
                                                        Locale.ROOT);

                        if (email.length() > MAX_EMAIL_LENGTH) {

                                throw new IllegalArgumentException(
                                                "email is too long.");
                        }

                        return email;
                }

                return "imported."
                                + organizationId
                                + "."
                                + nationalId
                                + "@imported.local";
        }

        private String optOrGenerated(
                        Map<String, String> row,
                        String key,
                        String nationalId,
                        Long organizationId) {

                return resolveEmail(
                                row,
                                nationalId,
                                organizationId);
        }

        // ================================================================
        // CURRENCY
        // ================================================================

        private String resolveCurrency(
                        Map<String, String> row,
                        Organization org) {

                String supplied = opt(
                                row,
                                "currency",
                                null);

                if (supplied != null
                                && !supplied.isBlank()) {

                        String currency = supplied
                                        .trim()
                                        .toUpperCase(
                                                        Locale.ROOT);

                        if (currency.length() != 3) {

                                throw new IllegalArgumentException(
                                                "currency must be a valid 3-letter currency code.");
                        }

                        return currency;
                }

                String organizationCurrency = org.getDefaultCurrency();

                if (organizationCurrency == null
                                || organizationCurrency.isBlank()) {

                        throw new IllegalArgumentException(
                                        "Currency is missing and the organization has no default currency.");
                }

                String currency = organizationCurrency
                                .trim()
                                .toUpperCase(
                                                Locale.ROOT);

                if (currency.length() != 3) {

                        throw new IllegalArgumentException(
                                        "Organization default currency is invalid: "
                                                        + currency);
                }

                return currency;
        }

        // ================================================================
        // LOAN REFERENCE
        // ================================================================

        private String normalizeReference(
                        String reference) {

                if (reference == null
                                || reference.isBlank()) {

                        throw new IllegalArgumentException(
                                        "loan_reference cannot be blank.");
                }

                String normalized = reference.trim();
                String suffix = normalized.replaceFirst("(?i)^NLS[-_\\s]*", "").trim();
                if (suffix.isBlank()) {
                        throw new IllegalArgumentException(
                                        "loan_reference must contain a value after the NLS prefix.");
                }

                String referenceWithPrefix = "NLS-" + suffix;

                // loans.reference_number is VARCHAR(100). Reject instead of
                // truncating a legacy key, because truncation can merge two
                // different source references and compromise idempotency.
                if (referenceWithPrefix.length() > 100) {
                        throw new IllegalArgumentException(
                                        "loan_reference is too long after applying the required NLS prefix (maximum 100 characters).");
                }

                return referenceWithPrefix;
        }

        /**
         * Reconstructs the historical reference ONLY for duplicate detection.
         * New imports always receive the next numeric NLS reference instead.
         * Older imports without a source reference used a deterministic UUID
         * derived from the organization and the complete normalized row.
         */
        private String legacyRowFingerprint(Map<String, String> row, Organization org) {
                return com.patrick.fintech.loan_backend.util.LegacyLoanIdentity
                                .originalRowFingerprint(org.getId(), row);
        }

        private String resolveImportReference(
                        Map<String, String> row,
                        Organization org) {

                String suppliedReference = opt(row, "loan_reference", null);
                if (suppliedReference != null && !suppliedReference.isBlank()) {
                        return normalizeReference(suppliedReference);
                }

                java.util.TreeMap<String, String> originalRow = new java.util.TreeMap<>();
                row.forEach((key, value) -> {
                        if (!key.startsWith("__"))
                                originalRow.put(key, value);
                });
                String stableInput = String.valueOf(org.getId()) + "|" + originalRow;
                String stableToken = UUID.nameUUIDFromBytes(
                                stableInput.getBytes(StandardCharsets.UTF_8))
                                .toString()
                                .replace("-", "")
                                .substring(0, 24)
                                .toUpperCase(Locale.ROOT);

                return "NLS-LEGACY-" + stableToken;
        }

        private int calculateImportedDaysOverdue(
                        LoanStatus status,
                        LocalDate nextDueDate,
                        BigDecimal remainingReceivable) {

                if (status == null
                                || nextDueDate == null
                                || remainingReceivable == null
                                || remainingReceivable.compareTo(ZERO) <= 0
                                || status == LoanStatus.PAID
                                || status == LoanStatus.CLOSED
                                || status == LoanStatus.REJECTED
                                || status == LoanStatus.CANCELLED) {
                        return 0;
                }

                LocalDate today = LocalDate.now();
                if (!today.isAfter(nextDueDate)) {
                        return 0;
                }

                long days = java.time.temporal.ChronoUnit.DAYS.between(nextDueDate, today);
                return (int) Math.min(days, Integer.MAX_VALUE);
        }

        private boolean optBoolean(
                        Map<String, String> row,
                        String key,
                        boolean fallback) {

                String raw = row.get(key);
                if (isBlankOrSkipped(raw)) {
                        return fallback;
                }

                String normalized = raw.trim().toUpperCase(Locale.ROOT);
                return switch (normalized) {
                        case "TRUE", "YES", "Y", "1", "ON", "BUSINESS_OWNER_ONLY", "OWNER_ONLY", "BO_ONLY" -> true;
                        case "FALSE", "NO", "N", "0", "OFF", "NORMAL", "STANDARD" -> false;
                        default -> throw new IllegalArgumentException(
                                        "\"" + key + "\" must be true/false, yes/no, 1/0, or BUSINESS_OWNER_ONLY/NORMAL. Got \""
                                                        + raw + "\".");
                };
        }

        // ================================================================
        // INTERNAL IMPORT NOTE
        // ================================================================

        private String buildInternalImportNote(
                        Long importBatchId,
                        BigDecimal importedInterestRate,
                        String importedRateType) {

                StringBuilder note = new StringBuilder();

                if (importBatchId == null) {

                        note.append(
                                        "Imported from legacy ledger.");

                } else {

                        note.append(
                                        "Imported from legacy ledger ");

                        note.append(
                                        "(batch #");

                        note.append(
                                        importBatchId);

                        note.append(
                                        ").");
                }

                note.append(
                                " Historical contractual rates were preserved on the imported loan; "
                                                + "5% monthly management fee and 2% one-time application fee "
                                                + "are used only when the source did not provide those values.");

                /*
                 * Preserve information about the original source rate
                 * without using it as the active loan rule.
                 */
                if (importedInterestRate != null) {

                        note.append(
                                        " Original imported interest rate was ");

                        note.append(
                                        importedInterestRate.stripTrailingZeros());

                        note.append(
                                        "% ");

                        note.append(
                                        importedRateType != null
                                                        ? importedRateType
                                                        : "UNKNOWN");

                        note.append(
                                        "; source rate was normalized.");
                }

                return note.toString();
        }

        // ================================================================
        // FAILURE RESULT
        // ================================================================

        private ImportRowResult fail(
                        int rowNumber,
                        String error) {

                // A row can create a borrower before a later loan validation or
                // accounting operation fails. Because each row runs in its own
                // REQUIRES_NEW transaction, mark that transaction rollback-only
                // before returning a failure result so the row is truly atomic.
                try {
                        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                } catch (IllegalStateException ignored) {
                        // Defensive: fail() is also safe to use outside a Spring
                        // transaction in tests or tooling.
                }

                String safeError = error == null
                                || error.isBlank()
                                                ? "Import failed."
                                                : error;

                return ImportRowResult.builder()
                                .rowNumber(
                                                rowNumber)
                                .success(
                                                false)
                                .error(
                                                safeError)
                                .build();
        }
}