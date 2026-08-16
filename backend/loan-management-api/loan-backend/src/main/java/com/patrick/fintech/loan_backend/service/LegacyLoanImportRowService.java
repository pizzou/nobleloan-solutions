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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
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

        private static final BigDecimal MONTHLY_INTEREST_RATE = FinancialPolicy.MONTHLY_INTEREST_RATE;

        private static final BigDecimal MONTHLY_MANAGEMENT_FEE_RATE = FinancialPolicy.MONTHLY_MANAGEMENT_FEE_RATE;

        private static final BigDecimal TOTAL_MONTHLY_CHARGE_RATE = MONTHLY_INTEREST_RATE.add(
                        MONTHLY_MANAGEMENT_FEE_RATE);

        private static final BigDecimal PROCESSING_FEE_RATE = FinancialPolicy.PROCESSING_FEE_RATE;

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

        private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
                        DateTimeFormatter.ISO_LOCAL_DATE
                                        .withResolverStyle(
                                                        ResolverStyle.STRICT),

                        DateTimeFormatter.ofPattern(
                                        "dd/MM/uuuu")
                                        .withResolverStyle(
                                                        ResolverStyle.STRICT),

                        DateTimeFormatter.ofPattern(
                                        "d/M/uuuu")
                                        .withResolverStyle(
                                                        ResolverStyle.STRICT),

                        DateTimeFormatter.ofPattern(
                                        "dd-MM-uuuu")
                                        .withResolverStyle(
                                                        ResolverStyle.STRICT));

        private final BorrowerRepository borrowerRepo;

        private final LoanRepository loanRepo;

        private final LoanService loanService;

        private final AccountingService accountingService;

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
                        Map<String, Borrower> sessionBorrowers) {

                String borrowerAction = null;

                try {

                        // ========================================================
                        // IMPORT CONTEXT
                        // ========================================================

                        validateImportContext(
                                        row,
                                        rowNumber,
                                        org,
                                        importBatchId,
                                        sessionBorrowers);

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
                         * Historical source file may contain an old
                         * interest_rate field.
                         *
                         * We validate it but DO NOT use it for the imported
                         * loan because the current platform rules are fixed.
                         */
                        BigDecimal importedInterestRate = reqRate(
                                        row,
                                        "interest_rate");

                        validateInterestRate(
                                        importedInterestRate);

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

                        if (!"MONTHLY".equals(
                                        importedRateType)
                                        && !"ANNUAL".equals(
                                                        importedRateType)) {

                                return fail(
                                                rowNumber,
                                                "interest_rate_type must be MONTHLY or ANNUAL. "
                                                                + "Got \""
                                                                + importedRateType
                                                                + "\".");
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

                        BigDecimal processingFeePaidGiven = optMoney(
                                        row,
                                        "processing_fee_paid");

                        BigDecimal penaltiesAssessedGiven = optMoney(
                                        row,
                                        "penalties_assessed");

                        BigDecimal penaltiesPaidGiven = optMoney(
                                        row,
                                        "penalties_paid");

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
                        validateOptionalMoney(interestPaidGiven, "interest_paid");
                        validateOptionalMoney(interestOutstandingGiven, "interest_outstanding");
                        validateOptionalMoney(managementFeePaidGiven, "management_fee_paid");
                        validateOptionalMoney(managementFeeOutstandingGiven, "total_management_fee_balance");
                        validateOptionalMoney(processingFeePaidGiven, "processing_fee_paid");
                        validateOptionalMoney(penaltiesAssessedGiven, "penalties_assessed");
                        validateOptionalMoney(penaltiesPaidGiven, "penalties_paid");

                        if (totalPaid == null) {
                                totalPaid = ZERO;
                        } else {
                                totalPaid = money(totalPaid);
                        }

                        BigDecimal penaltiesAssessed = money(
                                        penaltiesAssessedGiven != null ? penaltiesAssessedGiven : ZERO);
                        BigDecimal penaltiesPaid = money(
                                        penaltiesPaidGiven != null ? penaltiesPaidGiven : ZERO);

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
                                 * Calculate according to the current platform rule:
                                 *
                                 * 5% monthly interest
                                 * +
                                 * 5% monthly management fee
                                 * =
                                 * 10% monthly recurring charge.
                                 *
                                 * Processing fee is NOT included here because it is
                                 * a one-time fee deducted at disbursement.
                                 */
                                BigDecimal[] calculated = calculateCurrentPlatformLoan(
                                                amount,
                                                durationMonths);

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

                        BigDecimal principalPaidHistorical = principalPaidGiven != null
                                        ? money(principalPaidGiven)
                                        : money(amount.subtract(outstandingBalance).max(ZERO));

                        BigDecimal principalReconciled = money(
                                        principalPaidHistorical.add(outstandingBalance));

                        if (principalReconciled.subtract(amount).abs().compareTo(new BigDecimal("0.01")) > 0) {
                                return fail(
                                                rowNumber,
                                                "principal_paid + outstanding_balance must equal amount. "
                                                                + "principal_paid=" + principalPaidHistorical
                                                                + ", outstanding_balance=" + outstandingBalance
                                                                + ", amount=" + amount);
                        }

                        // ========================================================
                        // PROCESSING FEE
                        // ========================================================

                        /*
                         * Current rule:
                         *
                         * 2% of GROSS principal.
                         *
                         * One-time only.
                         *
                         * Example:
                         *
                         * Gross loan = 500,000
                         * Processing fee = 10,000
                         * Borrower receives = 490,000
                         *
                         * Interest continues to be calculated on 500,000.
                         */
                        BigDecimal processingFee = money(
                                        amount
                                                        .multiply(
                                                                        PROCESSING_FEE_RATE)
                                                        .divide(
                                                                        ONE_HUNDRED,
                                                                        CALCULATION_SCALE,
                                                                        RoundingMode.HALF_UP));

                        /*
                         * Historical imported loans are assumed to have already
                         * gone through disbursement.
                         *
                         * Therefore do not charge the processing fee again.
                         */
                        boolean processingFeePaid = processingFeePaidGiven != null
                                        ? processingFeePaidGiven.compareTo(ZERO) > 0
                                        : isHistoricalLoanStatus(statusRaw);

                        if (processingFeePaidGiven != null
                                        && processingFeePaidGiven.compareTo(processingFee) > 0) {
                                return fail(rowNumber, "processing_fee_paid cannot exceed processing_fee.");
                        }

                        // ========================================================
                        // STATUS / BALANCE CONSISTENCY
                        // ========================================================

                        if (("PAID".equals(
                                        statusRaw)
                                        || "CLOSED".equals(
                                                        statusRaw))
                                        && outstandingBalance.compareTo(
                                                        ZERO) > 0) {

                                return fail(
                                                rowNumber,
                                                "Loan status "
                                                                + statusRaw
                                                                + " is inconsistent with outstanding_balance="
                                                                + outstandingBalance
                                                                + ". A PAID/CLOSED loan must have zero outstanding balance.");
                        }

                        if (("PAID".equals(
                                        statusRaw)
                                        || "CLOSED".equals(
                                                        statusRaw))
                                        && totalPaid.compareTo(
                                                        totalRepayable) < 0) {

                                log.warn(
                                                "Historical loan marked {} although totalPaid={} < totalRepayable={}. "
                                                                + "rowNumber={}, organizationId={}",
                                                statusRaw,
                                                totalPaid,
                                                totalRepayable,
                                                rowNumber,
                                                org.getId());
                        }

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

                        sessionBorrowers.put(
                                        nationalIdHash,
                                        borrower);

                        // ========================================================
                        // LOAN REFERENCE
                        // ========================================================

                        String suppliedReference = opt(
                                        row,
                                        "loan_reference",
                                        null);

                        String referenceNumber;

                        if (suppliedReference != null
                                        && !suppliedReference.isBlank()) {

                                referenceNumber = normalizeReference(
                                                suppliedReference);

                        } else {

                                referenceNumber = loanService.newReferenceNumber(
                                                org);
                        }

                        if (referenceNumber == null
                                        || referenceNumber.isBlank()) {

                                throw new IllegalStateException(
                                                "Unable to generate a loan reference number.");
                        }

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
                                        || principalPaidGiven != null) {
                                totalPaid = money(
                                                principalPaidHistorical
                                                                .add(interestPaidHistorical)
                                                                .add(managementFeePaidHistorical)
                                                                .add(penaltiesPaid));
                        }

                        if (totalRepayableGiven == null) {
                                totalRepayable = money(
                                                amount
                                                                .add(totalHistoricalInterest)
                                                                .add(totalHistoricalManagementFee));
                        }

                        // ========================================================
                        // BUILD LOAN
                        // ========================================================

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

                                        // ------------------------------------------------
                                        // GROSS PRINCIPAL
                                        // ------------------------------------------------

                                        .amount(
                                                        amount)

                                        // ------------------------------------------------
                                        // FIXED PLATFORM INTEREST
                                        // ------------------------------------------------

                                        .interestRate(
                                                        MONTHLY_INTEREST_RATE)

                                        // ------------------------------------------------
                                        // FIXED MANAGEMENT FEE
                                        // ------------------------------------------------

                                        .managementFeeRate(
                                                        MONTHLY_MANAGEMENT_FEE_RATE)

                                        // ------------------------------------------------
                                        // MONTHLY RATE TYPE
                                        // ------------------------------------------------

                                        .interestRateType(
                                                        "MONTHLY")

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
                                        // PROCESSING FEE
                                        // ------------------------------------------------

                                        .processingFeeRate(
                                                        PROCESSING_FEE_RATE)

                                        .processingFee(
                                                        processingFee)

                                        .processingFeePaid(
                                                        processingFeePaid ? processingFee : ZERO)

                                        .totalRepayable(
                                                        totalRepayable)

                                        .totalPaid(
                                                        totalPaid)

                                        .outstandingBalance(
                                                        outstandingBalance)

                                        .principalPaid(
                                                        principalPaidHistorical)

                                        .penaltiesAssessed(
                                                        penaltiesAssessed)

                                        .penaltiesPaid(
                                                        penaltiesPaid)

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
                                                        historicalLoan
                                                                        ? optDate(row, "next_due_date",
                                                                                        startDate.plusMonths(1))
                                                                        : null)

                                        .nextPaymentDate(
                                                        historicalLoan
                                                                        ? startDate.plusMonths(1)
                                                                        : null)

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
                                                                        + " Historical NLS financial balances were imported as opening loan state; no historical accounting journal was reposted, preventing double-counting.")

                                        // ------------------------------------------------
                                        // IMPORT FLAGS
                                        // ------------------------------------------------

                                        .imported(
                                                        true)

                                        .importBatchId(
                                                        importBatchId);

                        Loan loan = builder.build();

                        // ========================================================
                        // PREVIEW MODE
                        // ========================================================

                        if (!commit) {

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

                                loan = loanRepo.save(
                                                loan);

                        } catch (DataIntegrityViolationException e) {

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

                        // ========================================================
                        // SUCCESS LOG
                        // ========================================================

                        log.info(
                                        "Legacy loan imported successfully. "
                                                        + "rowNumber={}, organizationId={}, loanId={}, "
                                                        + "referenceNumber={}, borrowerId={}, status={}, "
                                                        + "amount={}, totalRepayable={}, totalPaid={}, "
                                                        + "outstandingBalance={}, interestRate={}%, "
                                                        + "managementFeeRate={}%, processingFeeRate={}%, "
                                                        + "processingFee={}, processingFeePaid={}, "
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
                                        MONTHLY_INTEREST_RATE,
                                        MONTHLY_MANAGEMENT_FEE_RATE,
                                        PROCESSING_FEE_RATE,
                                        processingFee,
                                        processingFeePaid,
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
         * Processing fee is excluded because it is a one-time
         * disbursement deduction.
         *
         * Returns:
         *
         * [monthlyInstallment, totalRepayable]
         */
        private BigDecimal[] calculateCurrentPlatformLoan(
                        BigDecimal principal,
                        int months) {

                BigDecimal normalizedPrincipal = money(
                                principal);

                if (normalizedPrincipal.compareTo(
                                MIN_LOAN_AMOUNT) < 0) {

                        throw new IllegalArgumentException(
                                        "Loan principal must be at least "
                                                        + formatMoney(
                                                                        MIN_LOAN_AMOUNT));
                }

                if (months < MIN_DURATION_MONTHS
                                || months > MAX_DURATION_MONTHS) {

                        throw new IllegalArgumentException(
                                        "Loan duration must be between "
                                                        + MIN_DURATION_MONTHS
                                                        + " and "
                                                        + MAX_DURATION_MONTHS
                                                        + " months");
                }

                BigDecimal monthlyRate = TOTAL_MONTHLY_CHARGE_RATE
                                .divide(
                                                ONE_HUNDRED,
                                                CALCULATION_SCALE,
                                                RoundingMode.HALF_UP);

                if (monthlyRate.compareTo(
                                ZERO) == 0) {

                        BigDecimal monthlyPayment = money(
                                        normalizedPrincipal
                                                        .divide(
                                                                        BigDecimal.valueOf(
                                                                                        months),
                                                                        CALCULATION_SCALE,
                                                                        RoundingMode.HALF_UP));

                        BigDecimal totalRepayable = money(
                                        monthlyPayment
                                                        .multiply(
                                                                        BigDecimal.valueOf(
                                                                                        months)));

                        return new BigDecimal[] {
                                        monthlyPayment,
                                        totalRepayable
                        };
                }

                BigDecimal onePlusRate = BigDecimal.ONE.add(
                                monthlyRate);

                BigDecimal factor = onePlusRate.pow(
                                months,
                                MathContext.DECIMAL128);

                BigDecimal numerator = normalizedPrincipal
                                .multiply(
                                                monthlyRate)
                                .multiply(
                                                factor);

                BigDecimal denominator = factor.subtract(
                                BigDecimal.ONE);

                if (denominator.compareTo(
                                ZERO) == 0) {

                        throw new IllegalStateException(
                                        "Invalid monthly loan calculation.");
                }

                BigDecimal monthlyPayment = money(
                                numerator.divide(
                                                denominator,
                                                CALCULATION_SCALE,
                                                RoundingMode.HALF_UP));

                BigDecimal totalRepayable = money(
                                monthlyPayment.multiply(
                                                BigDecimal.valueOf(
                                                                months)));

                return new BigDecimal[] {
                                monthlyPayment,
                                totalRepayable
                };
        }

        // ================================================================
        // IMPORT CONTEXT VALIDATION
        // ================================================================

        private void validateImportContext(
                        Map<String, String> row,
                        int rowNumber,
                        Organization org,
                        Long importBatchId,
                        Map<String, Borrower> sessionBorrowers) {

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

        private BigDecimal reqRate(
                        Map<String, String> row,
                        String key) {
                String value = req(row, key);
                try {
                        return parseDecimalValue(value, key, true)
                                        .setScale(RATE_SCALE, RoundingMode.HALF_UP);
                } catch (NumberFormatException e) {
                        throw new IllegalArgumentException(
                                        "\"" + key + "\" must be a valid interest rate. Got \"" + value + "\".");
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

                String value = req(
                                row,
                                key);

                for (DateTimeFormatter formatter : DATE_FORMATS) {

                        try {

                                return LocalDate.parse(
                                                value,
                                                formatter);

                        } catch (DateTimeParseException ignored) {
                                // Try next format.
                        }
                }

                throw new IllegalArgumentException(
                                "\""
                                                + key
                                                + "\" isn't a recognized date. "
                                                + "Preferred format is YYYY-MM-DD. Got \""
                                                + value
                                                + "\".");
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

                for (DateTimeFormatter formatter : DATE_FORMATS) {
                        try {
                                return LocalDate.parse(value, formatter);
                        } catch (DateTimeParseException ignored) {
                                // Try the next supported format.
                        }
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

                if (normalized.length() > 150) {

                        throw new IllegalArgumentException(
                                        "loan_reference is too long.");
                }

                return normalized;
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
                                " Current platform rates normalized to ");

                note.append(
                                "5% monthly interest, ");

                note.append(
                                "5% monthly management fee, ");

                note.append(
                                "2% one-time processing fee.");

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