package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.DashboardStats;
import com.patrick.fintech.loan_backend.dto.LoanResponse;
import com.patrick.fintech.loan_backend.mapper.ResponseDtoMapper;
import com.patrick.fintech.loan_backend.dto.LoanRequest;
import com.patrick.fintech.loan_backend.dto.publicportal.BorrowerDashboardResponse;
import com.patrick.fintech.loan_backend.dto.publicportal.DashboardSummaryResponse;
import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.DocumentType;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.Loan.LoanType;
import com.patrick.fintech.loan_backend.model.LoanProduct;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.model.PaymentSchedule;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.AuditLogRepository;
import com.patrick.fintech.loan_backend.repository.BorrowerRepository;
import com.patrick.fintech.loan_backend.repository.LoanProductRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import com.patrick.fintech.loan_backend.repository.PaymentRepository;
import com.patrick.fintech.loan_backend.security.HmacIndexer;
import com.patrick.fintech.loan_backend.util.FinancialPolicy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanService {

        private final LoanRepository loanRepo;
        private final OrganizationRepository orgRepo;
        private final PaymentRepository paymentRepo;
        private final BorrowerRepository borrowerRepo;
        private final RiskScoringService riskService;
        private final NotificationService notifService;
        private final MailService mailService;
        private final SmsService smsService;
        private final AuditLogRepository auditRepo;
        private final WebhookService webhookService;
        private final AuditService auditService;
        private final LoanProductRepository loanProductRepo;
        private final AccountingService accountingService;
        private final BorrowerFileService fileService;
        private final HolidayService holidayService;
        private final CreditBureauService creditBureauService;
        private final PaymentScheduleService paymentScheduleService;

        private static final int MAX_LOAN_DURATION_MONTHS = 6;

        private static final int DEFAULT_LOAN_DURATION_MONTHS = 1;

        private static final BigDecimal MIN_LOAN_AMOUNT = bd("500000");

        private static final BigDecimal MONTHLY_INTEREST_RATE = FinancialPolicy.MONTHLY_INTEREST_RATE;

        private static final BigDecimal MONTHLY_MANAGEMENT_FEE_RATE = FinancialPolicy.MONTHLY_MANAGEMENT_FEE_RATE;

        private static final BigDecimal TOTAL_MONTHLY_CHARGE_RATE = MONTHLY_INTEREST_RATE
                        .add(MONTHLY_MANAGEMENT_FEE_RATE);

        private static final BigDecimal PROCESSING_FEE_RATE = FinancialPolicy.PROCESSING_FEE_RATE;

        private static final BigDecimal ZERO = BigDecimal.ZERO;

        private static final BigDecimal ONE_HUNDRED = bd("100");

        private static final BigDecimal TWELVE = bd("12");

        private static final BigDecimal MIN_MONEY_UNIT = bd("0.01");

        private static final List<DocumentType> DEFAULT_REQUIRED_DOCS = List.of(
                        DocumentType.NATIONAL_ID,
                        DocumentType.SELFIE,
                        DocumentType.PROOF_OF_ADDRESS);

        private static final int WATCH_MAX_DAYS = 89;

        private static final int SUBSTANDARD_MAX_DAYS = 179;

        private static final int DOUBTFUL_MAX_DAYS = 359;

        private static final int WRITTEN_OFF_MIN_DAYS = 360;

        // ================================================================
        // CREDIT QUALITY RESULT
        // ================================================================

        private record CreditQualityResult(
                        Loan.CreditQuality quality,
                        int daysOverdue) {
        }

        // ================================================================
        // REQUIRED DOCUMENTS
        // ================================================================

        private List<DocumentType> requiredDocsFor(Loan loan) {

                if (loan == null || loan.getOrganization() == null) {
                        return DEFAULT_REQUIRED_DOCS;
                }

                Long organizationId = loan.getOrganization().getId();

                if (organizationId == null || loan.getLoanType() == null) {
                        return DEFAULT_REQUIRED_DOCS;
                }

                LoanProduct product = loanProductRepo
                                .findFirstByOrganization_IdAndLoanTypeAndActiveTrue(
                                                organizationId,
                                                loan.getLoanType())
                                .orElse(null);

                if (product == null) {
                        return DEFAULT_REQUIRED_DOCS;
                }

                List<String> configured = product.getRequiredDocumentTypesList();

                if (configured == null || configured.isEmpty()) {
                        return DEFAULT_REQUIRED_DOCS;
                }

                List<DocumentType> documentTypes = new ArrayList<>();

                for (String type : configured) {

                        if (type == null || type.isBlank()) {
                                continue;
                        }

                        try {

                                documentTypes.add(
                                                DocumentType.valueOf(
                                                                type.trim().toUpperCase()));

                        } catch (IllegalArgumentException ex) {

                                throw new IllegalArgumentException(
                                                "Invalid document type configured for Loan Product: "
                                                                + type,
                                                ex);
                        }
                }

                return documentTypes.isEmpty()
                                ? DEFAULT_REQUIRED_DOCS
                                : documentTypes;
        }

        // ================================================================
        // BORROWER DASHBOARD
        // ================================================================

        public BorrowerDashboardResponse getBorrowerDashboard(
                        String reference,
                        String phone) {

                if (reference == null || reference.isBlank()) {
                        throw new IllegalArgumentException(
                                        "Loan reference is required");
                }

                if (phone == null || phone.isBlank()) {
                        throw new IllegalArgumentException(
                                        "Phone number is required");
                }

                String phoneHash = HmacIndexer.index(phone);

                Loan loan = loanRepo
                                .findByReferenceNumberAndBorrower_PhoneHash(
                                                reference,
                                                phoneHash)
                                .orElseThrow(
                                                () -> new RuntimeException(
                                                                "Loan not found"));

                return BorrowerDashboardResponse.builder()
                                .loanId(loan.getId())
                                .referenceNumber(loan.getReferenceNumber())
                                .borrowerName(
                                                loan.getBorrower() != null
                                                                ? loan.getBorrower().getFullName()
                                                                : null)
                                .loanOfficer(
                                                loan.getLoanOfficer() != null
                                                                ? loan.getLoanOfficer().getFullName()
                                                                : null)
                                .status(
                                                loan.getStatus() != null
                                                                ? loan.getStatus().name()
                                                                : null)
                                .loanType(
                                                loan.getLoanType() != null
                                                                ? loan.getLoanType().name()
                                                                : null)
                                .principal(
                                                loan.getAmountDecimal())
                                .outstandingBalance(
                                                loan.getOutstandingBalanceDecimal())
                                .totalPaid(
                                                loan.getTotalPaidDecimal())
                                .totalRepayable(
                                                loan.getTotalRepayableDecimal())
                                .nextInstallmentAmount(
                                                loan.getNextInstallmentAmountDecimal())
                                .nextPaymentDate(
                                                loan.getNextPaymentDate())
                                .maturityDate(
                                                loan.getMaturityDate())
                                .missedInstallments(
                                                loan.getMissedInstallments())
                                .daysOverdue(
                                                loan.getDaysOverdue())
                                .currency(
                                                loan.getCurrency())
                                .build();
        }

        // ================================================================
        // BORROWER SUMMARY
        // ================================================================

        public DashboardSummaryResponse getBorrowerSummary(
                        String phone) {

                if (phone == null || phone.isBlank()) {
                        throw new IllegalArgumentException(
                                        "Phone number is required");
                }

                String phoneHash = HmacIndexer.index(phone);

                List<Loan> loans = loanRepo.findByBorrower_PhoneHash(
                                phoneHash);

                if (loans == null || loans.isEmpty()) {
                        throw new RuntimeException(
                                        "Borrower not found");
                }

                int activeLoans = 0;
                int overdueLoans = 0;

                BigDecimal totalBorrowed = ZERO;
                BigDecimal outstanding = ZERO;
                BigDecimal totalPaid = ZERO;

                Loan nextLoan = null;

                for (Loan loan : loans) {

                        if (loan == null) {
                                continue;
                        }

                        totalBorrowed = money(
                                        totalBorrowed.add(
                                                        moneyValue(
                                                                        loan.getAmountDecimal())));

                        outstanding = money(
                                        outstanding.add(
                                                        moneyValue(
                                                                        loan.getOutstandingBalanceDecimal())));

                        totalPaid = money(
                                        totalPaid.add(
                                                        moneyValue(
                                                                        loan.getTotalPaidDecimal())));

                        if (loan.getStatus() == LoanStatus.ACTIVE) {
                                activeLoans++;
                        }

                        if (loan.getStatus() == LoanStatus.OVERDUE) {
                                overdueLoans++;
                        }

                        if (loan.getNextPaymentDate() != null) {

                                if (nextLoan == null
                                                || nextLoan.getNextPaymentDate() == null
                                                || loan.getNextPaymentDate()
                                                                .isBefore(
                                                                                nextLoan.getNextPaymentDate())) {
                                        nextLoan = loan;
                                }
                        }
                }

                return DashboardSummaryResponse.builder()
                                .totalLoans(loans.size())
                                .activeLoans(activeLoans)
                                .totalBorrowed(totalBorrowed)
                                .outstandingBalance(outstanding)
                                .totalPaid(totalPaid)
                                .overdueLoans(overdueLoans)
                                .nextPaymentAmount(
                                                nextLoan == null
                                                                ? null
                                                                : nextLoan
                                                                                .getNextInstallmentAmountDecimal())
                                .nextPaymentDate(
                                                nextLoan == null
                                                                ? null
                                                                : nextLoan.getNextPaymentDate())
                                .build();
        }

        // ================================================================
        // CREATE LOAN
        // ================================================================

        @Transactional
        public Loan createLoan(
                        LoanRequest req,
                        Long organizationId,
                        User createdBy) {

                if (req == null) {
                        throw new IllegalArgumentException(
                                        "Loan request cannot be null");
                }

                if (organizationId == null) {
                        throw new IllegalArgumentException(
                                        "Organization ID cannot be null");
                }

                Organization org = orgRepo.findById(
                                organizationId).orElseThrow(
                                                () -> new RuntimeException(
                                                                "Organization not found: "
                                                                                + organizationId));

                if (createdBy != null) {

                        if (createdBy.getOrganization() == null
                                        || createdBy.getOrganization().getId() == null
                                        || !createdBy
                                                        .getOrganization()
                                                        .getId()
                                                        .equals(organizationId)) {

                                throw new RuntimeException(
                                                "Creating user does not belong to this organization");
                        }
                }

                // ============================================================
                // BORROWER
                // ============================================================

                if (req.getBorrowerId() == null) {
                        throw new IllegalArgumentException(
                                        "Borrower ID is required");
                }

                Borrower borrower = borrowerRepo.findById(
                                req.getBorrowerId()).orElseThrow(
                                                () -> new RuntimeException(
                                                                "Borrower not found: "
                                                                                + req.getBorrowerId()));

                if (borrower.getOrganization() == null
                                || borrower.getOrganization().getId() == null
                                || !borrower
                                                .getOrganization()
                                                .getId()
                                                .equals(organizationId)) {

                        throw new RuntimeException(
                                        "Borrower does not belong to this organization");
                }

                if (borrower.getStatus() == Borrower.BorrowerStatus.BLACKLISTED) {

                        throw new RuntimeException(
                                        "This borrower is blacklisted and cannot be issued a new loan. "
                                                        + "Reason on file: "
                                                        + (borrower.getBlacklistReason() != null
                                                                        ? borrower.getBlacklistReason()
                                                                        : "not specified"));
                }

                // ============================================================
                // LOAN TYPE
                // ============================================================

                LoanType requestedType = req.getLoanType() != null
                                ? req.getLoanType()
                                : LoanType.PERSONAL;

                LoanProduct product = loanProductRepo
                                .findFirstByOrganization_IdAndLoanTypeAndActiveTrue(
                                                organizationId,
                                                requestedType)
                                .orElse(null);

                // ============================================================
                // DURATION
                // ============================================================

                Integer requestedDuration = req.getDurationMonths();

                if (requestedDuration == null) {
                        throw new IllegalArgumentException(
                                        "Loan duration is required");
                }

                validateLoanDuration(requestedDuration);

                int months = requestedDuration;

                // ============================================================
                // PRINCIPAL
                // ============================================================

                BigDecimal requestedAmount = toBigDecimal(req.getAmount());

                if (requestedAmount == null
                                || requestedAmount.compareTo(ZERO) <= 0) {

                        throw new IllegalArgumentException(
                                        "Loan amount must be greater than zero");
                }

                BigDecimal principal = normalizePrincipal(requestedAmount);

                if (principal.compareTo(MIN_LOAN_AMOUNT) < 0) {

                        throw new IllegalArgumentException(
                                        "Minimum loan amount is "
                                                        + formatMoney(MIN_LOAN_AMOUNT)
                                                        + " "
                                                        + org.getDefaultCurrency());
                }

                // ============================================================
                // NO MAXIMUM LOAN AMOUNT
                // ============================================================

                if (product != null) {

                        Integer productMinTerm = product.getMinTermMonths();

                        Integer productMaxTerm = product.getMaxTermMonths();

                        if (productMinTerm != null
                                        && months < productMinTerm) {

                                throw new IllegalArgumentException(
                                                String.format(
                                                                "%s term must be at least %d months",
                                                                product.getName(),
                                                                productMinTerm));
                        }

                        if (productMaxTerm != null
                                        && productMaxTerm < months) {

                                throw new IllegalArgumentException(
                                                String.format(
                                                                "%s term must not exceed %d months",
                                                                product.getName(),
                                                                productMaxTerm));
                        }
                }

                validateLoanDuration(months);

                // ============================================================
                // ORGANIZATION / PRODUCT PRICING
                // ============================================================

                BigDecimal interestRate = product != null
                                ? moneyValue(product.getInterestRateDecimal())
                                : MONTHLY_INTEREST_RATE;

                BigDecimal managementFeeRate = product != null
                                ? moneyValue(product.getManagementFeePercentDecimal())
                                : MONTHLY_MANAGEMENT_FEE_RATE;

                BigDecimal totalMonthlyRate = money(
                                interestRate.add(managementFeeRate));

                String rateType = "MONTHLY";

                validateInterestRate(interestRate);
                validateInterestRate(managementFeeRate);
                validateInterestRate(totalMonthlyRate);

                // ============================================================
                // LOAN CALCULATION
                // ============================================================

                BigDecimal[] calc = calcLoan(
                                principal,
                                totalMonthlyRate,
                                months,
                                "MONTHLY");

                BigDecimal monthlyInstallment = calc[0];
                BigDecimal totalRepayable = calc[1];

                // ============================================================
                // PROCESSING FEE
                // ============================================================

                BigDecimal processingFeeRate = product != null
                                ? moneyValue(product.getProcessingFeePercentDecimal())
                                : PROCESSING_FEE_RATE;

                BigDecimal processingFee = money(
                                principal
                                                .multiply(processingFeeRate)
                                                .divide(
                                                                ONE_HUNDRED,
                                                                16,
                                                                RoundingMode.HALF_UP));

                // ============================================================
                // DTI
                // ============================================================

                BigDecimal monthlyIncome = moneyValue(
                                borrower.getMonthlyIncome());

                BigDecimal existingMonthlyObligations = ZERO;
                BigDecimal existingOutstandingPrincipal = ZERO;

                List<Loan> borrowerLoans = loanRepo.findByBorrowerIdAndOrganizationId(
                                borrower.getId(),
                                organizationId);

                if (borrowerLoans != null) {
                        for (Loan existingLoan : borrowerLoans) {

                                if (existingLoan == null
                                                || existingLoan.getId() == null) {
                                        continue;
                                }

                                LoanStatus existingStatus = existingLoan.getStatus();

                                if (existingStatus != LoanStatus.ACTIVE
                                                && existingStatus != LoanStatus.OVERDUE
                                                && existingStatus != LoanStatus.DISBURSED) {
                                        continue;
                                }

                                BigDecimal outstanding = existingLoan.getOutstandingBalanceDecimal();

                                if (outstanding != null) {
                                        existingOutstandingPrincipal = money(
                                                        existingOutstandingPrincipal.add(outstanding));
                                }

                                BigDecimal installment = existingLoan.getNextInstallmentAmountDecimal();

                                if (installment != null) {
                                        existingMonthlyObligations = money(
                                                        existingMonthlyObligations.add(installment));
                                }
                        }
                }

                BigDecimal totalProposedMonthlyObligation = money(
                                existingMonthlyObligations.add(monthlyInstallment));

                BigDecimal dti = monthlyIncome.compareTo(ZERO) > 0
                                ? money(
                                                totalProposedMonthlyObligation
                                                                .divide(
                                                                                monthlyIncome,
                                                                                16,
                                                                                RoundingMode.HALF_UP)
                                                                .multiply(ONE_HUNDRED))
                                : ZERO.setScale(2);

                // ============================================================
                // COLLATERAL
                // ============================================================

                BigDecimal collateralValue = null;

                if (req.getCollateralValue() != null) {

                        collateralValue = money(
                                        toBigDecimal(
                                                        req.getCollateralValue()));

                        if (collateralValue.compareTo(ZERO) < 0) {

                                throw new IllegalArgumentException(
                                                "Collateral value cannot be negative");
                        }
                }

                // ============================================================
                // START DATE
                // ============================================================

                LocalDate startDate = req.getStartDate() != null
                                && !req.getStartDate().isBlank()
                                                ? LocalDate.parse(req.getStartDate())
                                                : LocalDate.now();

                // ============================================================
                // REPAYMENT FREQUENCY
                // ============================================================

                Loan.RepaymentFrequency repaymentFrequency = req.getRepaymentFrequency() != null
                                ? req.getRepaymentFrequency()
                                : Loan.RepaymentFrequency.MONTHLY;

                if (repaymentFrequency != Loan.RepaymentFrequency.MONTHLY) {

                        throw new IllegalArgumentException(
                                        "This loan system currently supports monthly repayment only");
                }

                // ============================================================
                // NEXT DUE DATE
                // ============================================================

                LocalDate nextDueDate = holidayService.adjustToBusinessDay(
                                organizationId,
                                startDate.plusMonths(1));

                // ============================================================
                // CURRENCY
                // ============================================================

                String currency = req.getCurrency() != null
                                && !req.getCurrency().isBlank()
                                                ? req.getCurrency()
                                                                .trim()
                                                                .toUpperCase()
                                                : org.getDefaultCurrency();

                // ============================================================
                // BUILD LOAN
                // ============================================================

                Loan loan = Loan.builder()
                                .referenceNumber(
                                                generateRef(org))
                                .organization(org)
                                .borrower(borrower)
                                .createdBy(createdBy)
                                .loanOfficer(createdBy)
                                .loanType(requestedType)
                                .repaymentFrequency(
                                                repaymentFrequency)
                                .status(
                                                LoanStatus.PENDING)
                                .creditQuality(
                                                Loan.CreditQuality.CURRENT)
                                .daysOverdue(0)
                                .amount(principal)
                                .requestedAmount(principal)
                                .interestRate(interestRate)
                                .managementFeeRate(
                                                managementFeeRate)
                                .processingFeeRate(
                                                processingFeeRate)
                                .interestRateType(rateType)
                                .durationMonths(months)
                                .currency(currency)
                                .processingFee(processingFee)
                                .managementFee(ZERO)
                                .managementFeePaid(ZERO)
                                .totalInterest(ZERO)
                                .interestPaid(ZERO)
                                .processingFeePaid(ZERO)
                                .totalRepayable(totalRepayable)
                                .outstandingBalance(principal)
                                .totalPaid(ZERO)
                                .purpose(req.getPurpose())
                                .notes(req.getNotes())
                                .collateralDescription(
                                                req.getCollateralDescription())
                                .collateralValue(
                                                collateralValue)
                                .startDate(startDate)
                                .debtToIncomeRatio(dti)
                                .creditScoreSnapshot(
                                                borrower.getCreditScore())
                                .nextDueDate(nextDueDate)
                                .build();

                Loan saved = loanRepo.save(loan);

                // ============================================================
                // PRINCIPAL SAFETY CHECK
                // ============================================================

                BigDecimal savedPrincipal = normalizePrincipal(
                                moneyValue(
                                                saved.getAmountDecimal()));

                if (savedPrincipal.compareTo(principal) != 0) {

                        log.error(
                                        "PRINCIPAL MISMATCH AFTER SAVE. Expected={}, saved={}, loanId={}",
                                        principal,
                                        savedPrincipal,
                                        saved.getId());

                        throw new IllegalStateException(
                                        "Loan principal changed during save. Expected "
                                                        + principal
                                                        + " but saved "
                                                        + savedPrincipal);
                }

                // ============================================================
                // DURATION SAFETY CHECK
                // ============================================================

                validateLoanDuration(
                                saved.getDurationMonths());

                // ============================================================
                // RISK SCORING
                // ============================================================

                scoreAsync(saved);

                // ============================================================
                // AUDIT
                // ============================================================

                String creatorDescription;

                if (createdBy != null) {

                        creatorDescription = "Loan "
                                        + saved.getReferenceNumber()
                                        + " created by "
                                        + createdBy.getName()
                                        + " for "
                                        + borrower.getFullName()
                                        + " — principal "
                                        + principal
                                        + " — monthly interest "
                                        + interestRate
                                        + "%"
                                        + " — monthly management fee "
                                        + managementFeeRate
                                        + "%"
                                        + " — processing fee "
                                        + processingFeeRate
                                        + "%"
                                        + " — duration "
                                        + months
                                        + " months"
                                        + " — credit quality CURRENT";

                } else {

                        creatorDescription = "Public borrower loan application "
                                        + saved.getReferenceNumber()
                                        + " created for "
                                        + borrower.getFullName()
                                        + " — principal "
                                        + principal
                                        + " — monthly interest "
                                        + interestRate
                                        + "%"
                                        + " — monthly management fee "
                                        + managementFeeRate
                                        + "%"
                                        + " — processing fee "
                                        + processingFeeRate
                                        + "%"
                                        + " — duration "
                                        + months
                                        + " months"
                                        + " — credit quality CURRENT";
                }

                audit(
                                org,
                                createdBy,
                                "LOAN_CREATED",
                                "LOAN",
                                saved.getId().toString(),
                                creatorDescription);

                return saved;
        }

        // ================================================================
        // APPROVE LOAN
        // ================================================================

        public Loan approveLoan(
                        Long loanId,
                        User approvedBy,
                        String notes) {

                return approveLoan(
                                loanId,
                                approvedBy,
                                notes,
                                null);
        }

        public Loan approveLoan(
                        Long loanId,
                        User approvedBy,
                        String notes,
                        Double newInterestRate) {
                return approveLoan(
                                loanId,
                                approvedBy,
                                notes,
                                newInterestRate,
                                null);
        }

        /**
         * Final approval with a contractual pricing snapshot.
         *
         * Interest may be overridden by an authorized approver.
         * Management fee is always 5% monthly.
         * Processing fee may only be overridden by MANAGER or ADMIN.
         */
        @Transactional
        public Loan approveLoan(
                        Long loanId,
                        User approvedBy,
                        String notes,
                        Double newInterestRate,
                        Double newProcessingFeeRate) {
                return approveLoan(
                                loanId,
                                approvedBy,
                                notes,
                                newInterestRate,
                                newProcessingFeeRate,
                                null);
        }

        /**
         * Final approval with a contractual principal override.
         *
         * The borrower request is retained in requestedAmount. The approved
         * principal becomes amount and is the sole principal used by the
         * repayment schedule, accounting, BNR and Credit Bureau reporting.
         */
        @Transactional
        public Loan approveLoan(
                        Long loanId,
                        User approvedBy,
                        String notes,
                        Double newInterestRate,
                        Double newProcessingFeeRate,
                        BigDecimal newApprovedAmount) {

                if (approvedBy == null
                                || approvedBy.getOrganization() == null
                                || approvedBy.getOrganization().getId() == null) {

                        throw new RuntimeException(
                                        "Approving user must belong to an organization");
                }

                Loan loan = getLoanForOrg(
                                loanId,
                                approvedBy.getOrganization().getId());

                if (loan.getStatus() != LoanStatus.PENDING
                                && loan.getStatus() != LoanStatus.UNDER_REVIEW) {

                        throw new RuntimeException(
                                        "Cannot approve a loan that is "
                                                        + loan.getStatus()
                                                        + " — only loans that are Pending or Under Review can be approved.");
                }

                if (loan.getBorrower() == null) {

                        throw new RuntimeException(
                                        "Cannot approve loan "
                                                        + loan.getReferenceNumber()
                                                        + " — it has no borrower record linked.");
                }

                validateLoanDuration(
                                loan.getDurationMonths());

                BigDecimal requestedAmount = normalizePrincipal(
                                moneyValue(
                                                loan.getRequestedAmountDecimal() != null
                                                                ? loan.getRequestedAmountDecimal()
                                                                : loan.getAmountDecimal()));

                BigDecimal principal = normalizePrincipal(
                                newApprovedAmount != null
                                                ? newApprovedAmount
                                                : moneyValue(loan.getAmountDecimal()));

                if (principal.compareTo(ZERO) <= 0) {
                        throw new IllegalArgumentException("Approved loan amount must be greater than zero.");
                }

                if (principal.compareTo(requestedAmount) > 0) {
                        throw new IllegalArgumentException(
                                        "Approved loan amount cannot exceed the borrower's requested amount of "
                                                        + requestedAmount + ".");
                }

                if (principal.compareTo(MIN_LOAN_AMOUNT) < 0) {

                        throw new IllegalArgumentException(
                                        "Cannot approve loan below minimum amount of "
                                                        + MIN_LOAN_AMOUNT);
                }

                List<DocumentType> missingDocs = fileService.getMissingDocumentTypes(
                                loan.getBorrower().getId(),
                                requiredDocsFor(loan));

                if (!missingDocs.isEmpty()) {

                        throw new RuntimeException(
                                        "Cannot approve this loan — the borrower hasn't uploaded: "
                                                        + missingDocs.stream()
                                                                        .map(DocumentType::name)
                                                                        .collect(Collectors.joining(", ")));
                }

                // ============================================================
                // PRICING SNAPSHOT
                // ============================================================

                BigDecimal interestRate = moneyValue(loan.getInterestRateDecimal());
                BigDecimal managementFeeRate = moneyValue(loan.getManagementFeeRateDecimal());
                BigDecimal processingFeeRate = moneyValue(loan.getProcessingFeeRateDecimal());

                LoanProduct activeProduct = loanProductRepo
                                .findFirstByOrganization_IdAndLoanTypeAndActiveTrue(
                                                loan.getOrganization().getId(),
                                                loan.getLoanType())
                                .orElse(null);

                if (interestRate.compareTo(ZERO) <= 0 && activeProduct != null) {
                        interestRate = moneyValue(activeProduct.getInterestRateDecimal());
                }
                if (managementFeeRate.compareTo(ZERO) < 0 && activeProduct != null) {
                        managementFeeRate = moneyValue(activeProduct.getManagementFeePercentDecimal());
                }
                if (processingFeeRate.compareTo(ZERO) < 0 && activeProduct != null) {
                        processingFeeRate = moneyValue(activeProduct.getProcessingFeePercentDecimal());
                }

                if (newInterestRate != null) {
                        BigDecimal requestedRate = bd(newInterestRate);
                        validateInterestRate(requestedRate);
                        interestRate = requestedRate;
                }

                // Noble Loan policy: management fee is fixed at 5% monthly.
                managementFeeRate = MONTHLY_MANAGEMENT_FEE_RATE;

                if (newApprovedAmount != null) {
                        String role = approvedBy.getRole() != null
                                        && approvedBy.getRole().getName() != null
                                                        ? approvedBy.getRole().getName().trim().toUpperCase(Locale.ROOT)
                                                        : "";

                        if (role.startsWith("ROLE_")) {
                                role = role.substring(5);
                        }

                        if (!"ADMIN".equals(role) && !"MANAGER".equals(role)) {
                                throw new SecurityException(
                                                "Only MANAGER or ADMIN may change the approved principal.");
                        }

                        // The approved principal may be reduced from the original request,
                        // but it may never be increased beyond what the borrower requested.
                        if (normalizePrincipal(newApprovedAmount).compareTo(requestedAmount) > 0) {
                                throw new IllegalArgumentException(
                                                "Approved loan amount cannot exceed the borrower's requested amount of "
                                                                + requestedAmount + ".");
                        }
                }

                if (newProcessingFeeRate != null) {
                        String role = approvedBy.getRole() != null
                                        && approvedBy.getRole().getName() != null
                                                        ? approvedBy.getRole().getName().trim().toUpperCase(Locale.ROOT)
                                                        : "";

                        if (role.startsWith("ROLE_")) {
                                role = role.substring(5);
                        }

                        if (!"ADMIN".equals(role) && !"MANAGER".equals(role)) {
                                throw new SecurityException(
                                                "Only MANAGER or ADMIN may change the processing fee rate.");
                        }

                        BigDecimal requestedProcessingFeeRate = bd(newProcessingFeeRate);
                        validateInterestRate(requestedProcessingFeeRate);
                        processingFeeRate = requestedProcessingFeeRate;
                }

                if (interestRate.compareTo(ZERO) <= 0) {
                        interestRate = MONTHLY_INTEREST_RATE;
                }
                if (processingFeeRate.compareTo(ZERO) < 0) {
                        processingFeeRate = PROCESSING_FEE_RATE;
                }

                BigDecimal totalMonthlyRate = money(interestRate.add(managementFeeRate));

                validateInterestRate(interestRate);
                validateInterestRate(managementFeeRate);
                validateInterestRate(totalMonthlyRate);

                loan.setInterestRate(interestRate);
                loan.setManagementFeeRate(managementFeeRate);
                loan.setProcessingFeeRate(processingFeeRate);
                loan.setInterestRateType("MONTHLY");

                // ============================================================
                // CREDIT QUALITY
                // ============================================================

                loan.setCreditQuality(
                                Loan.CreditQuality.CURRENT);

                loan.setDaysOverdue(0);

                // ============================================================
                // PROCESSING FEE
                // ============================================================

                BigDecimal processingFee = money(
                                principal
                                                .multiply(
                                                                moneyValue(loan.getProcessingFeeRateDecimal()))
                                                .divide(
                                                                ONE_HUNDRED,
                                                                16,
                                                                RoundingMode.HALF_UP));

                loan.setProcessingFee(
                                processingFee);

                // ============================================================
                // TOTAL REPAYABLE
                // ============================================================

                int durationMonths = loan.getDurationMonths() != null
                                ? loan.getDurationMonths()
                                : DEFAULT_LOAN_DURATION_MONTHS;

                validateLoanDuration(durationMonths);

                loan.setTotalRepayable(
                                calculateContractualTotalRepayable(
                                                principal,
                                                interestRate,
                                                managementFeeRate,
                                                durationMonths));

                loan.setRequestedAmount(requestedAmount);
                loan.setAmount(principal);

                if (loan.getOutstandingBalanceDecimal() == null
                                || moneyValue(
                                                loan.getOutstandingBalanceDecimal()).compareTo(ZERO) <= 0) {

                        loan.setOutstandingBalance(
                                        principal);
                }

                loan.setStatus(
                                LoanStatus.APPROVED);

                loan.setApprovedBy(
                                approvedBy);

                loan.setApprovedAt(
                                LocalDate.now());

                if (notes != null
                                && !notes.isBlank()) {

                        loan.setInternalNotes(
                                        notes.trim());
                }

                Loan saved = loanRepo.save(loan);

                List<Payment> existingSchedule = paymentRepo.findByLoanId(saved.getId());

                if (existingSchedule.isEmpty()) {
                        generateRepaymentSchedule(saved);
                } else if (existingSchedule.stream().anyMatch(p -> Boolean.TRUE.equals(p.getPaid()))) {
                        throw new IllegalStateException(
                                        "This loan already has a paid repayment row; its approved principal cannot be changed at this stage.");
                } else {
                        // A provisional schedule must never survive a contractual
                        // approval amendment. Rebuild all unpaid rows from the
                        // final approved principal and pricing snapshot.
                        paymentRepo.deleteAll(existingSchedule);
                        generateRepaymentSchedule(saved);
                }

                audit(
                                saved.getOrganization(),
                                approvedBy,
                                "LOAN_APPROVED",
                                "LOAN",
                                loanId.toString(),
                                "Loan "
                                                + saved.getReferenceNumber()
                                                + " approved — requested principal "
                                                + requestedAmount
                                                + " — approved principal "
                                                + saved.getAmountDecimal()
                                                + " — monthly interest "
                                                + saved.getInterestRateDecimal()
                                                + "%"
                                                + " — monthly management fee "
                                                + saved.getManagementFeeRateDecimal()
                                                + "%"
                                                + " — one-time processing fee "
                                                + saved.getProcessingFeeRateDecimal()
                                                + "%"
                                                + " — credit quality CURRENT");

                try {

                        mailService.sendLoanApproved(saved);

                } catch (Exception e) {

                        log.warn(
                                        "Loan approval email failed",
                                        e);
                }

                try {

                        smsService.sendLoanApproved(saved);

                } catch (Exception e) {

                        log.warn(
                                        "Loan approval SMS failed",
                                        e);
                }

                notifyOfficer(
                                saved,
                                approvedBy,
                                "Loan Approved",
                                "Loan "
                                                + saved.getReferenceNumber()
                                                + " has been approved by "
                                                + approvedBy.getName()
                                                + ". Requested amount: " + saved.getRequestedAmountDecimal()
                                                + ". Approved amount: " + saved.getAmountDecimal()
                                                + ". Monthly interest is " + saved.getInterestRateDecimal()
                                                + "% and monthly management fee is "
                                                + saved.getManagementFeeRateDecimal() + "%."
                                                + " Credit quality is CURRENT.",
                                "success");

                webhookService.dispatch(
                                saved.getOrganization(),
                                "LOAN_APPROVED",
                                saved);

                return saved;
        }

        // ================================================================
        // AMORTIZE
        // ================================================================

        public double[] amortize(
                        double principal,
                        double rate,
                        int months,
                        String rateType) {

                validateLoanDuration(months);

                BigDecimal principalDecimal = normalizePrincipal(
                                bd(principal));

                BigDecimal rateDecimal = bd(rate);

                validateInterestRate(rateDecimal);
                validateRateType(rateType);

                BigDecimal[] result = calcLoan(
                                principalDecimal,
                                rateDecimal,
                                months,
                                rateType);

                return new double[] {
                                result[0].doubleValue(),
                                result[1].doubleValue()
                };
        }

        // ================================================================
        // NEW REFERENCE
        // ================================================================

        public String newReferenceNumber(
                        Organization org) {

                return generateRef(org);
        }

        // ================================================================
        // REJECT LOAN
        // ================================================================

        @Transactional
        public Loan rejectLoan(
                        Long loanId,
                        User rejectedBy,
                        String reason) {

                if (rejectedBy == null
                                || rejectedBy.getOrganization() == null
                                || rejectedBy.getOrganization().getId() == null) {

                        throw new RuntimeException(
                                        "Rejecting user must belong to an organization");
                }

                Loan loan = getLoanForOrg(
                                loanId,
                                rejectedBy.getOrganization().getId());

                if (loan.getStatus() != LoanStatus.PENDING
                                && loan.getStatus() != LoanStatus.UNDER_REVIEW) {

                        throw new RuntimeException(
                                        "Cannot reject a loan that is "
                                                        + loan.getStatus());
                }

                if (reason == null
                                || reason.isBlank()) {

                        throw new IllegalArgumentException(
                                        "Rejection reason is required");
                }

                loan.setStatus(
                                LoanStatus.REJECTED);

                loan.setCreditQuality(
                                Loan.CreditQuality.CURRENT);

                loan.setDaysOverdue(0);

                loan.setRejectionReason(
                                reason.trim());

                Loan saved = loanRepo.save(loan);

                audit(
                                loan.getOrganization(),
                                rejectedBy,
                                "LOAN_REJECTED",
                                "LOAN",
                                loanId.toString(),
                                "Reason: " + reason.trim());

                try {

                        mailService.sendLoanRejected(saved);

                } catch (Exception e) {

                        log.warn(
                                        "Loan rejection email failed",
                                        e);
                }

                try {

                        smsService.sendLoanRejected(saved);

                } catch (Exception e) {

                        log.warn(
                                        "Loan rejection SMS failed",
                                        e);
                }

                notifyOfficer(
                                saved,
                                rejectedBy,
                                "Loan Rejected",
                                "Loan "
                                                + saved.getReferenceNumber()
                                                + " has been rejected by "
                                                + rejectedBy.getName()
                                                + ". Reason: "
                                                + reason.trim(),
                                "warning");

                webhookService.dispatch(
                                saved.getOrganization(),
                                "LOAN_REJECTED",
                                saved);

                return saved;
        }

        // ================================================================
        // DISBURSE LOAN
        // ================================================================

        @Transactional
        public Loan disburseLoan(
                        Long loanId,
                        User officer,
                        String disbursementMethod) {

                if (officer == null
                                || officer.getOrganization() == null
                                || officer.getOrganization().getId() == null) {

                        throw new RuntimeException(
                                        "Disbursing officer must belong to an organization");
                }

                Loan loan = loanRepo.findByIdForUpdate(
                                loanId)
                                .orElseThrow(
                                                () -> new RuntimeException(
                                                                "Loan not found: " + loanId));

                if (loan.getOrganization() == null
                                || loan.getOrganization().getId() == null
                                || !loan.getOrganization().getId().equals(
                                                officer.getOrganization().getId())) {
                        throw new RuntimeException("Access denied.");
                }

                if (loan.getStatus() != LoanStatus.APPROVED) {

                        throw new RuntimeException(
                                        "Loan must be APPROVED before disbursement");
                }

                if (loan.getBorrower() == null) {

                        throw new RuntimeException(
                                        "Cannot disburse loan "
                                                        + loan.getReferenceNumber()
                                                        + " — it has no borrower record linked.");
                }

                validateLoanDuration(
                                loan.getDurationMonths());

                BigDecimal exactPrincipal = normalizePrincipal(
                                moneyValue(
                                                loan.getAmountDecimal()));

                if (exactPrincipal.compareTo(
                                MIN_LOAN_AMOUNT) < 0) {

                        throw new IllegalStateException(
                                        "Cannot disburse a loan below minimum principal of "
                                                        + MIN_LOAN_AMOUNT);
                }

                if (exactPrincipal.compareTo(ZERO) <= 0) {

                        throw new IllegalStateException(
                                        "Cannot disburse a loan with zero or negative principal");
                }

                List<DocumentType> unverifiedDocs = fileService.getUnverifiedDocumentTypes(
                                loan.getBorrower().getId(),
                                requiredDocsFor(loan));

                if (!unverifiedDocs.isEmpty()) {

                        throw new RuntimeException(
                                        "Cannot disburse this loan — staff still needs to verify: "
                                                        + unverifiedDocs.stream()
                                                                        .map(DocumentType::name)
                                                                        .collect(Collectors.joining(", ")));
                }

                // ============================================================
                // PRESERVE CONTRACTUAL PRICING
                // ============================================================

                BigDecimal interestRate = moneyValue(loan.getInterestRateDecimal());
                BigDecimal managementFeeRate = moneyValue(loan.getManagementFeeRateDecimal());
                BigDecimal processingFeeRate = moneyValue(loan.getProcessingFeeRateDecimal());

                if (interestRate.compareTo(ZERO) <= 0
                                || managementFeeRate.compareTo(ZERO) < 0
                                || processingFeeRate.compareTo(ZERO) < 0) {

                        LoanProduct product = loanProductRepo
                                        .findFirstByOrganization_IdAndLoanTypeAndActiveTrue(
                                                        loan.getOrganization().getId(),
                                                        loan.getLoanType())
                                        .orElseThrow(() -> new IllegalStateException(
                                                        "No active loan product pricing is configured for this organization."));

                        if (interestRate.compareTo(ZERO) <= 0) {
                                interestRate = moneyValue(product.getInterestRateDecimal());
                        }
                        if (managementFeeRate.compareTo(ZERO) < 0) {
                                managementFeeRate = moneyValue(product.getManagementFeePercentDecimal());
                        }
                        if (processingFeeRate.compareTo(ZERO) < 0) {
                                processingFeeRate = moneyValue(product.getProcessingFeePercentDecimal());
                        }
                }

                validateInterestRate(interestRate);
                validateInterestRate(managementFeeRate);
                validateInterestRate(money(interestRate.add(managementFeeRate)));

                loan.setInterestRate(interestRate);
                loan.setManagementFeeRate(managementFeeRate);
                loan.setProcessingFeeRate(processingFeeRate);
                loan.setInterestRateType("MONTHLY");

                BigDecimal processingFee = money(
                                exactPrincipal
                                                .multiply(
                                                                processingFeeRate)
                                                .divide(
                                                                ONE_HUNDRED,
                                                                16,
                                                                RoundingMode.HALF_UP));

                loan.setProcessingFee(
                                processingFee);

                // The 2% processing fee is collected once at disbursement.
                // It is never part of principal or recurring monthly charges.
                loan.setProcessingFeePaid(processingFee);
                loan.setNetDisbursedAmount(
                                exactPrincipal.subtract(processingFee).max(ZERO));

                loan.setAmount(
                                exactPrincipal);

                loan.setOutstandingBalance(
                                exactPrincipal);

                loan.setStatus(
                                LoanStatus.ACTIVE);

                // ============================================================
                // CREDIT QUALITY
                // ============================================================

                loan.setCreditQuality(
                                Loan.CreditQuality.CURRENT);

                loan.setDaysOverdue(0);

                // ============================================================
                // EXACT DISBURSEMENT TIMESTAMP
                // ============================================================

                LocalDateTime exactDisbursementTimestamp = LocalDateTime.now();

                loan.setDisbursedAt(
                                exactDisbursementTimestamp);

                loan.setDisbursedAmount(
                                exactPrincipal);

                LocalDate disbursementDate = exactDisbursementTimestamp.toLocalDate();

                Integer duration = loan.getDurationMonths() != null
                                ? loan.getDurationMonths()
                                : DEFAULT_LOAN_DURATION_MONTHS;

                validateLoanDuration(duration);

                loan.setMaturityDate(
                                disbursementDate.plusMonths(duration));

                loan.setNextDueDate(
                                holidayService.adjustToBusinessDay(
                                                loan.getOrganization().getId(),
                                                disbursementDate.plusMonths(1)));

                Loan saved = loanRepo.save(loan);

                log.info(
                                "Loan {} disbursed at exact timestamp {}",
                                saved.getReferenceNumber(),
                                saved.getDisbursedAt());

                /*
                 * Approval creates a provisional schedule so the approved
                 * loan can already display repayment information. The
                 * contractual accrual period must, however, begin on the
                 * actual disbursement date. Rebuild the operational Payment
                 * rows now that the exact disbursement timestamp is known.
                 *
                 * This keeps the staff loan-detail schedule, PaymentService
                 * calculations, and the separate payment_schedules table on
                 * the same contractual start date without changing the
                 * existing architecture.
                 */
                regenerateRepaymentScheduleAfterDisbursement(saved);

                paymentScheduleService.generateSchedule(
                                saved);

                PaymentSchedule first = paymentScheduleService.getNextInstallment(
                                saved.getId());

                if (first != null) {

                        saved.setNextPaymentDate(
                                        first.getDueDate());

                        saved.setNextInstallmentAmount(
                                        first.getInstallmentAmount());

                        saved.setNextDueDate(
                                        first.getDueDate());
                }

                saved = loanRepo.save(saved);

                // ============================================================
                // CREDIT BUREAU
                // ============================================================

                if (creditBureauService
                                .isReportingRequiredForDisbursement()) {

                        try {

                                creditBureauService.reportDisbursedLoan(
                                                saved,
                                                officer.getName());

                                log.info(
                                                "Loan {} successfully reported to Credit Bureau.",
                                                saved.getReferenceNumber());

                        } catch (Exception e) {

                                log.error(
                                                "Credit Bureau reporting failed for loan {}",
                                                saved.getReferenceNumber(),
                                                e);

                                throw e;
                        }

                } else {

                        log.warn(
                                        "Credit Bureau reporting is explicitly disabled for disbursement. loan={}",
                                        saved.getReferenceNumber());
                }

                // ============================================================
                // AUDIT
                // ============================================================

                audit(
                                saved.getOrganization(),
                                officer,
                                "LOAN_DISBURSED",
                                "LOAN",
                                loanId.toString(),
                                "Disbursed via "
                                                + (disbursementMethod != null
                                                                && !disbursementMethod.isBlank()
                                                                                ? disbursementMethod
                                                                                : "unspecified")
                                                + " — monthly interest "
                                                + saved.getInterestRateDecimal()
                                                + "%"
                                                + " — monthly management fee "
                                                + saved.getManagementFeeRateDecimal()
                                                + "%"
                                                + " — processing fee "
                                                + saved.getProcessingFeeRateDecimal()
                                                + "% one time"
                                                + " — credit quality CURRENT");

                // ============================================================
                // ACCOUNTING
                // ============================================================

                accountingService.postDisbursement(
                                saved);

                // ============================================================
                // EMAIL
                // ============================================================

                try {

                        mailService.sendLoanDisbursed(
                                        saved,
                                        disbursementMethod);

                } catch (Exception e) {

                        log.warn(
                                        "Loan disbursement email failed.",
                                        e);
                }

                // ============================================================
                // SMS
                // ============================================================

                try {

                        smsService.sendLoanDisbursed(
                                        saved,
                                        disbursementMethod);

                } catch (Exception e) {

                        log.warn(
                                        "Loan disbursement SMS failed.",
                                        e);
                }

                // ============================================================
                // NOTIFICATION
                // ============================================================

                notifyOfficer(
                                saved,
                                officer,
                                "Loan Disbursed",
                                "Loan "
                                                + saved.getReferenceNumber()
                                                + " ("
                                                + saved.getCurrency()
                                                + " "
                                                + saved.getDisbursedAmountDecimal()
                                                + ") has been disbursed via "
                                                + (disbursementMethod != null
                                                                && !disbursementMethod.isBlank()
                                                                                ? disbursementMethod
                                                                                : "unspecified")
                                                + ". Monthly interest is " + saved.getInterestRateDecimal()
                                                + "% and monthly management fee is "
                                                + saved.getManagementFeeRateDecimal() + "%."
                                                + " Credit quality is CURRENT.",
                                "success");

                // ============================================================
                // WEBHOOK
                // ============================================================

                webhookService.dispatch(
                                saved.getOrganization(),
                                "LOAN_DISBURSED",
                                saved);

                return saved;
        }

        // ================================================================
        // NOTIFY OFFICER
        // ================================================================

        private void notifyOfficer(
                        Loan loan,
                        User actor,
                        String title,
                        String message,
                        String type) {

                if (loan == null) {
                        return;
                }

                User officer = loan.getLoanOfficer();

                if (officer == null) {
                        return;
                }

                if (actor != null
                                && officer.getId() != null
                                && officer.getId().equals(
                                                actor.getId())) {

                        return;
                }

                try {

                        notifService.notifyUsers(
                                        List.of(officer),
                                        title,
                                        message,
                                        type,
                                        "/dashboard/loans/"
                                                        + loan.getId());

                } catch (Exception e) {

                        log.warn(
                                        "In-app notification failed",
                                        e);
                }
        }

        // ================================================================
        // CREDIT QUALITY
        // ================================================================

        /**
         * Updates the credit quality of a loan using the exact delinquency
         * bands defined by the business:
         *
         * 0 days -> CURRENT
         * 1 - 89 -> WATCH
         * 90 - 179 -> SUBSTANDARD
         * 180 - 359 -> DOUBTFUL
         * 360+ -> WRITTEN_OFF
         *
         * This method uses the loan's organization for tenant isolation.
         */
        @Transactional
        public Loan updateCreditQuality(
                        Long loanId,
                        Long organizationId) {

                Loan loan = getLoanForOrg(
                                loanId,
                                organizationId);

                Loan.CreditQuality oldQuality = loan.getCreditQuality();

                CreditQualityResult result = determineCreditQuality(loan);

                loan.setDaysOverdue(
                                result.daysOverdue());

                loan.setCreditQuality(
                                result.quality());

                Loan saved = loanRepo.save(loan);

                if (oldQuality != result.quality()) {

                        log.info(
                                        "Loan {} credit quality changed from {} to {}. daysOverdue={}",
                                        saved.getReferenceNumber(),
                                        oldQuality,
                                        result.quality(),
                                        result.daysOverdue());

                        audit(
                                        saved.getOrganization(),
                                        null,
                                        "LOAN_CREDIT_QUALITY_CHANGED",
                                        "LOAN",
                                        saved.getId().toString(),
                                        "Credit quality changed from "
                                                        + (oldQuality != null
                                                                        ? oldQuality.name()
                                                                        : "null")
                                                        + " to "
                                                        + result.quality().name()
                                                        + " — days overdue "
                                                        + result.daysOverdue());
                }

                return saved;
        }

        /**
         * Refreshes credit quality for an already-loaded loan.
         *
         * This method is intended for collection services, scheduled jobs,
         * or other internal processes that already have the Loan entity.
         */
        @Transactional
        public Loan refreshCreditQuality(
                        Loan loan) {

                if (loan == null
                                || loan.getId() == null) {

                        return loan;
                }

                Loan.CreditQuality oldQuality = loan.getCreditQuality();

                CreditQualityResult result = determineCreditQuality(loan);

                loan.setDaysOverdue(
                                result.daysOverdue());

                loan.setCreditQuality(
                                result.quality());

                if (oldQuality != result.quality()) {

                        log.info(
                                        "Loan {} credit quality refreshed from {} to {}. daysOverdue={}",
                                        loan.getReferenceNumber(),
                                        oldQuality,
                                        result.quality(),
                                        result.daysOverdue());

                        return loanRepo.save(loan);
                }

                return loan;
        }

        /**
         * Determines the credit quality from the current loan state.
         *
         * Important:
         * CreditQuality is separate from LoanStatus.
         *
         * DEFAULTED does not automatically mean WRITTEN_OFF.
         * The actual overdue days determine the credit quality unless the
         * loan has formally been written off.
         */
        private CreditQualityResult determineCreditQuality(
                        Loan loan) {

                if (loan == null) {

                        return new CreditQualityResult(
                                        Loan.CreditQuality.CURRENT,
                                        0);
                }

                // ------------------------------------------------------------
                // FORMALLY WRITTEN OFF
                // ------------------------------------------------------------

                if (loan.getStatus() == LoanStatus.WRITTEN_OFF) {

                        int storedDays = loan.getDaysOverdue() != null
                                        ? Math.max(
                                                        loan.getDaysOverdue(),
                                                        0)
                                        : 0;

                        return new CreditQualityResult(
                                        Loan.CreditQuality.WRITTEN_OFF,
                                        storedDays);
                }

                // ------------------------------------------------------------
                // NON-DELINQUENT LOAN STATES
                // ------------------------------------------------------------

                if (loan.getStatus() == LoanStatus.PENDING
                                || loan.getStatus() == LoanStatus.UNDER_REVIEW
                                || loan.getStatus() == LoanStatus.APPROVED
                                || loan.getStatus() == LoanStatus.REJECTED
                                || loan.getStatus() == LoanStatus.PAID
                                || loan.getStatus() == LoanStatus.CLOSED) {

                        return new CreditQualityResult(
                                        Loan.CreditQuality.CURRENT,
                                        0);
                }

                // ------------------------------------------------------------
                // ACTIVE / OVERDUE / DEFAULTED
                // ------------------------------------------------------------

                int daysOverdue = calculateLoanDaysOverdue(loan);

                // ------------------------------------------------------------
                // CURRENT
                // 0 days
                // ------------------------------------------------------------

                if (daysOverdue == 0) {

                        return new CreditQualityResult(
                                        Loan.CreditQuality.CURRENT,
                                        0);
                }

                // ------------------------------------------------------------
                // WATCH
                // 1 - 89 days
                // ------------------------------------------------------------

                if (daysOverdue <= WATCH_MAX_DAYS) {

                        return new CreditQualityResult(
                                        Loan.CreditQuality.WATCH,
                                        daysOverdue);
                }

                // ------------------------------------------------------------
                // SUBSTANDARD
                // 90 - 179 days
                // ------------------------------------------------------------

                if (daysOverdue <= SUBSTANDARD_MAX_DAYS) {

                        return new CreditQualityResult(
                                        Loan.CreditQuality.SUBSTANDARD,
                                        daysOverdue);
                }

                // ------------------------------------------------------------
                // DOUBTFUL
                // 180 - 359 days
                // ------------------------------------------------------------

                if (daysOverdue <= DOUBTFUL_MAX_DAYS) {

                        return new CreditQualityResult(
                                        Loan.CreditQuality.DOUBTFUL,
                                        daysOverdue);
                }

                // ------------------------------------------------------------
                // WRITTEN OFF
                // 360+ days
                // ------------------------------------------------------------

                return new CreditQualityResult(
                                Loan.CreditQuality.WRITTEN_OFF,
                                daysOverdue);
        }

        /**
         * Calculates actual delinquency from the loan's next due date.
         *
         * The stored daysOverdue value is only used as a fallback when
         * there is no due date available.
         */
        private int calculateLoanDaysOverdue(
                        Loan loan) {

                if (loan == null) {
                        return 0;
                }

                if (loan.getStatus() != LoanStatus.ACTIVE
                                && loan.getStatus() != LoanStatus.OVERDUE
                                && loan.getStatus() != LoanStatus.DEFAULTED) {

                        return 0;
                }

                LocalDate dueDate = loan.getNextDueDate();

                if (dueDate == null) {

                        dueDate = loan.getNextPaymentDate();
                }

                if (dueDate == null) {

                        return Math.max(
                                        loan.getDaysOverdue() != null
                                                        ? loan.getDaysOverdue()
                                                        : 0,
                                        0);
                }

                LocalDate today = LocalDate.now();

                if (!today.isAfter(dueDate)) {

                        return 0;
                }

                return Math.max(
                                (int) ChronoUnit.DAYS.between(
                                                dueDate,
                                                today),
                                0);
        }

        // ================================================================
        // UPDATE STATUS
        // ================================================================

        @Transactional
        public Loan updateStatus(
                        Long loanId,
                        User user,
                        LoanStatus newStatus,
                        String notes) {

                if (user == null
                                || user.getOrganization() == null
                                || user.getOrganization().getId() == null) {

                        throw new RuntimeException(
                                        "User must belong to an organization");
                }

                if (newStatus == null) {

                        throw new IllegalArgumentException(
                                        "New loan status cannot be null");
                }

                Loan loan = getLoanForOrg(
                                loanId,
                                user.getOrganization().getId());

                LoanStatus current = loan.getStatus();

                if (current == null) {

                        throw new IllegalStateException(
                                        "Loan "
                                                        + loanId
                                                        + " has no current status");
                }

                switch (newStatus) {

                        case UNDER_REVIEW -> {

                                if (current != LoanStatus.PENDING) {

                                        throw new RuntimeException(
                                                        "Only a Pending loan can be moved to Under Review (currently "
                                                                        + current
                                                                        + ")");
                                }
                        }

                        case DEFAULTED -> {

                                if (current != LoanStatus.ACTIVE
                                                && current != LoanStatus.OVERDUE) {

                                        throw new RuntimeException(
                                                        "Only an Active or Overdue loan can be marked Defaulted (currently "
                                                                        + current
                                                                        + ")");
                                }
                        }

                        case WRITTEN_OFF -> {

                                if (current != LoanStatus.DEFAULTED
                                                && current != LoanStatus.OVERDUE
                                                && current != LoanStatus.ACTIVE) {

                                        throw new RuntimeException(
                                                        "Only an Active, Overdue, or Defaulted loan can be written off (currently "
                                                                        + current
                                                                        + ")");
                                }
                        }

                        case CLOSED -> {

                                if (current != LoanStatus.PAID
                                                && current != LoanStatus.WRITTEN_OFF) {

                                        throw new RuntimeException(
                                                        "Only a fully Paid or Written-off loan can be Closed (currently "
                                                                        + current
                                                                        + ")");
                                }
                        }

                        case RESTRUCTURED ->

                                throw new RuntimeException(
                                                "Use the Restructure Loan action instead.");

                        default ->

                                throw new RuntimeException(
                                                "Use the dedicated Approve / Reject / Disburse actions.");
                }

                // ============================================================
                // STATUS
                // ============================================================

                loan.setStatus(
                                newStatus);

                // ============================================================
                // CREDIT QUALITY
                // ============================================================

                Loan.CreditQuality oldQuality = loan.getCreditQuality();

                if (newStatus == LoanStatus.WRITTEN_OFF) {

                        loan.setCreditQuality(
                                        Loan.CreditQuality.WRITTEN_OFF);

                } else if (newStatus == LoanStatus.PAID
                                || newStatus == LoanStatus.CLOSED
                                || newStatus == LoanStatus.REJECTED
                                || newStatus == LoanStatus.PENDING
                                || newStatus == LoanStatus.UNDER_REVIEW
                                || newStatus == LoanStatus.APPROVED) {

                        loan.setCreditQuality(
                                        Loan.CreditQuality.CURRENT);

                        loan.setDaysOverdue(0);

                } else {

                        CreditQualityResult result = determineCreditQuality(loan);

                        loan.setDaysOverdue(
                                        result.daysOverdue());

                        loan.setCreditQuality(
                                        result.quality());
                }

                if (notes != null
                                && !notes.isBlank()) {

                        loan.setInternalNotes(
                                        notes.trim());
                }

                Loan saved = loanRepo.save(loan);

                audit(
                                loan.getOrganization(),
                                user,
                                "LOAN_STATUS_CHANGED",
                                "LOAN",
                                loanId.toString(),
                                current
                                                + " -> "
                                                + newStatus
                                                + " — credit quality "
                                                + oldQuality
                                                + " -> "
                                                + saved.getCreditQuality()
                                                + " — days overdue "
                                                + saved.getDaysOverdue()
                                                + (notes != null
                                                                && !notes.isBlank()
                                                                                ? ": " + notes.trim()
                                                                                : ""));

                if (oldQuality != saved.getCreditQuality()) {

                        audit(
                                        loan.getOrganization(),
                                        user,
                                        "LOAN_CREDIT_QUALITY_CHANGED",
                                        "LOAN",
                                        loanId.toString(),
                                        oldQuality
                                                        + " -> "
                                                        + saved.getCreditQuality()
                                                        + " — days overdue "
                                                        + saved.getDaysOverdue());
                }

                webhookService.dispatch(
                                loan.getOrganization(),
                                "LOAN_STATUS_CHANGED",
                                saved);

                return saved;
        }

        // ================================================================
        // GET LOANS
        // ================================================================

        public Page<Loan> getLoans(
                        Organization org,
                        int page,
                        int size,
                        String status,
                        String type) {

                if (org == null
                                || org.getId() == null) {

                        throw new IllegalArgumentException(
                                        "Organization is required");
                }

                if (page < 0) {
                        page = 0;
                }

                if (size <= 0) {
                        size = 20;
                }

                LoanStatus ls = null;

                if (status != null
                                && !status.isBlank()) {

                        try {

                                ls = LoanStatus.valueOf(
                                                status.trim().toUpperCase());

                        } catch (IllegalArgumentException e) {

                                throw new IllegalArgumentException(
                                                "Invalid loan status: "
                                                                + status);
                        }
                }

                LoanType lt = null;

                if (type != null
                                && !type.isBlank()) {

                        try {

                                lt = LoanType.valueOf(
                                                type.trim().toUpperCase());

                        } catch (IllegalArgumentException e) {

                                throw new IllegalArgumentException(
                                                "Invalid loan type: "
                                                                + type);
                        }
                }

                return loanRepo.findByFilters(
                                org,
                                ls,
                                lt,
                                PageRequest.of(
                                                page,
                                                size));
        }

        // ================================================================
        // GET LOAN
        // ================================================================

        public Loan getLoanForOrg(
                        Long loanId,
                        Long orgId) {

                if (loanId == null) {

                        throw new IllegalArgumentException(
                                        "Loan ID cannot be null");
                }

                if (orgId == null) {

                        throw new IllegalArgumentException(
                                        "Organization ID cannot be null");
                }

                Loan loan = loanRepo.findById(
                                loanId).orElseThrow(
                                                () -> new RuntimeException(
                                                                "Loan not found: "
                                                                                + loanId));

                if (loan.getOrganization() == null
                                || loan.getOrganization().getId() == null
                                || !loan
                                                .getOrganization()
                                                .getId()
                                                .equals(orgId)) {

                        throw new RuntimeException(
                                        "Access denied to loan: "
                                                        + loanId);
                }

                /*
                 * For system-originated loans, the operational Payment rows
                 * are the authoritative declining-balance schedule used by
                 * the staff loan-detail page and PaymentService. Older loan
                 * rows can still contain a legacy EMI-style totalRepayable
                 * even though their schedule already contains the correct
                 * daily-basis declining charges. Reconcile those aggregate
                 * fields when the detail loan is loaded.
                 *
                 * Imported legacy loans are deliberately excluded because
                 * their opening balances are historical accounting data and
                 * must not be silently rewritten from a reconstructed schedule.
                 */
                synchronizeLoanTotalsFromOperationalSchedule(loan);

                return loan;
        }

        // ================================================================
        // RECONCILE LOAN TOTALS FROM OPERATIONAL SCHEDULE
        // ================================================================

        private void synchronizeLoanTotalsFromOperationalSchedule(
                        Loan loan) {

                if (loan == null
                                || loan.getId() == null
                                || Boolean.TRUE.equals(loan.getImported())) {
                        return;
                }

                List<Payment> payments = paymentRepo.findByLoanId(loan.getId());

                if (payments == null || payments.isEmpty()) {
                        return;
                }

                BigDecimal scheduledInterest = payments.stream()
                                .filter(java.util.Objects::nonNull)
                                .map(payment -> payment.getScheduledInterestDecimal() != null
                                                ? payment.getScheduledInterestDecimal()
                                                : payment.getInterestComponentDecimal())
                                .filter(java.util.Objects::nonNull)
                                .reduce(ZERO, BigDecimal::add);

                BigDecimal scheduledManagementFee = payments.stream()
                                .filter(java.util.Objects::nonNull)
                                .map(payment -> payment.getScheduledManagementFeeDecimal() != null
                                                ? payment.getScheduledManagementFeeDecimal()
                                                : payment.getManagementFeeComponentDecimal())
                                .filter(java.util.Objects::nonNull)
                                .reduce(ZERO, BigDecimal::add);

                BigDecimal normalizedInterest = money(scheduledInterest);
                BigDecimal normalizedManagementFee = money(scheduledManagementFee);
                BigDecimal normalizedTotalRepayable = money(
                                moneyValue(loan.getAmountDecimal())
                                                .add(normalizedInterest)
                                                .add(normalizedManagementFee));

                boolean changed = false;

                if (moneyValue(loan.getTotalInterestDecimal())
                                .compareTo(normalizedInterest) != 0) {
                        loan.setTotalInterest(normalizedInterest);
                        changed = true;
                }

                if (moneyValue(loan.getManagementFeeDecimal())
                                .compareTo(normalizedManagementFee) != 0) {
                        loan.setManagementFee(normalizedManagementFee);
                        changed = true;
                }

                if (moneyValue(loan.getTotalRepayableDecimal())
                                .compareTo(normalizedTotalRepayable) != 0) {
                        loan.setTotalRepayable(normalizedTotalRepayable);
                        changed = true;
                }

                Payment next = payments.stream()
                                .filter(java.util.Objects::nonNull)
                                .filter(payment -> !Boolean.TRUE.equals(payment.getPaid()))
                                .filter(payment -> payment.getDueDate() != null)
                                .sorted(java.util.Comparator.comparing(Payment::getDueDate))
                                .findFirst()
                                .orElse(null);

                if (next != null) {
                        if (!java.util.Objects.equals(
                                        loan.getNextPaymentDate(),
                                        next.getDueDate())) {
                                loan.setNextPaymentDate(next.getDueDate());
                                changed = true;
                        }

                        if (!java.util.Objects.equals(
                                        loan.getNextDueDate(),
                                        next.getDueDate())) {
                                loan.setNextDueDate(next.getDueDate());
                                changed = true;
                        }

                        BigDecimal nextAmount = money(next.getAmountDecimal());
                        if (moneyValue(loan.getNextInstallmentAmountDecimal())
                                        .compareTo(nextAmount) != 0) {
                                loan.setNextInstallmentAmount(nextAmount);
                                changed = true;
                        }
                }

                if (changed) {
                        loanRepo.save(loan);
                }
        }

        // ================================================================
        // DOCUMENT REQUIREMENTS
        // ================================================================

        public Map<String, Object> getDocumentRequirements(
                        Long loanId,
                        Long orgId) {

                Loan loan = getLoanForOrg(
                                loanId,
                                orgId);

                Map<String, Object> result = new LinkedHashMap<>();

                if (loan.getBorrower() == null) {

                        result.put(
                                        "required",
                                        List.of());

                        result.put(
                                        "missing",
                                        List.of());

                        result.put(
                                        "unverified",
                                        List.of());

                        result.put(
                                        "readyToApprove",
                                        false);

                        result.put(
                                        "readyToDisburse",
                                        false);

                        result.put(
                                        "noBorrowerLinked",
                                        true);

                        return result;
                }

                List<DocumentType> required = requiredDocsFor(loan);

                List<DocumentType> missing = fileService.getMissingDocumentTypes(
                                loan.getBorrower().getId(),
                                required);

                List<DocumentType> unverified = fileService.getUnverifiedDocumentTypes(
                                loan.getBorrower().getId(),
                                required);

                result.put(
                                "required",
                                required.stream()
                                                .map(DocumentType::name)
                                                .toList());

                result.put(
                                "missing",
                                missing.stream()
                                                .map(DocumentType::name)
                                                .toList());

                result.put(
                                "unverified",
                                unverified.stream()
                                                .map(DocumentType::name)
                                                .toList());

                result.put(
                                "readyToApprove",
                                missing.isEmpty());

                result.put(
                                "readyToDisburse",
                                missing.isEmpty()
                                                && unverified.isEmpty());

                result.put(
                                "noBorrowerLinked",
                                false);

                return result;
        }

        // ================================================================
        // DASHBOARD
        // ================================================================

        public DashboardStats getDashboard(
                        Organization org) {

                if (org == null || org.getId() == null) {
                        throw new IllegalArgumentException("Organization is required");
                }

                LocalDate today = LocalDate.now();
                LocalDate firstOfMonth = today.withDayOfMonth(1);

                long overdueCount = Optional.ofNullable(
                                paymentRepo.findByOrganization_IdAndPaidFalseAndDueDateBefore(
                                                org.getId(), today))
                                .orElse(List.of())
                                .stream()
                                .filter(p -> p != null && p.getLoan() != null)
                                .map(p -> p.getLoan().getId())
                                .filter(java.util.Objects::nonNull)
                                .distinct()
                                .count();

                List<Map<String, Object>> typeBreakdown = loanRepo
                                .getLoanTypeBreakdown(org)
                                .stream()
                                .map(r -> {
                                        Map<String, Object> m = new LinkedHashMap<>();
                                        m.put("type", r[0]);
                                        m.put("count", r[1]);
                                        m.put("amount", r[2]);
                                        return m;
                                })
                                .collect(Collectors.toList());

                List<Loan> recentEntities = loanRepo.findRecentByOrg(
                                org,
                                PageRequest.of(0, 8));

                List<LoanResponse> recent = recentEntities == null
                                ? List.of()
                                : recentEntities.stream()
                                                .filter(java.util.Objects::nonNull)
                                                .map(ResponseDtoMapper::loan)
                                                .toList();

                BigDecimal totalDisbursed = Optional.ofNullable(
                                loanRepo.sumGrossDisbursedPrincipal(org))
                                .orElse(ZERO);

                BigDecimal totalCollected = Optional.ofNullable(
                                loanRepo.sumTotalCollected(org))
                                .orElse(ZERO);

                BigDecimal outstandingBalance = Optional.ofNullable(
                                loanRepo.sumOutstandingBalance(org))
                                .orElse(ZERO);

                BigDecimal collectedThisMonth = Optional.ofNullable(
                                paymentRepo.sumCollectedSince(org, firstOfMonth))
                                .orElse(ZERO);

                return DashboardStats.builder()
                                .totalLoans(loanRepo.countByOrganization(org))
                                .pendingLoans(loanRepo.countByOrganizationAndStatus(org, LoanStatus.PENDING))
                                .activeLoans(loanRepo.countByOrganizationAndStatus(org, LoanStatus.ACTIVE))
                                .overdueLoans(overdueCount)
                                .completedLoans(loanRepo.countByOrganizationAndStatus(org, LoanStatus.PAID))
                                .defaultedLoans(loanRepo.countByOrganizationAndStatus(org, LoanStatus.DEFAULTED))
                                .totalDisbursed(totalDisbursed)
                                .totalCollected(totalCollected)
                                .outstandingBalance(outstandingBalance)
                                .collectedThisMonth(collectedThisMonth)
                                .totalBorrowers(borrowerRepo.countByOrganization(org))
                                .latePaymentsCount(Optional.ofNullable(paymentRepo.countLatePayments(org)).orElse(0L))
                                .loanTypeBreakdown(typeBreakdown)
                                .recentLoans(recent)
                                .build();
        }

        // ================================================================
        // REBUILD OPERATIONAL REPAYMENT SCHEDULE AFTER DISBURSEMENT
        // ================================================================

        /**
         * Rebuilds the operational Payment schedule from the exact
         * disbursement date.
         *
         * Approval may create a provisional schedule before the loan is
         * actually disbursed. Once disbursement happens, the daily accrual
         * clock must start from the real disbursement date. This method only
         * replaces schedule rows that have no financial activity.
         */
        @Transactional
        public void regenerateRepaymentScheduleAfterDisbursement(Loan loan) {

                if (loan == null || loan.getId() == null) {
                        throw new IllegalArgumentException(
                                        "Loan is required before rebuilding repayment schedule");
                }

                List<Payment> existingPayments = paymentRepo.findByLoanId(loan.getId());

                boolean hasFinancialActivity = existingPayments.stream()
                                .filter(java.util.Objects::nonNull)
                                .anyMatch(payment -> Boolean.TRUE.equals(payment.getPaid())
                                                || (payment.getAmountPaidDecimal() != null
                                                                && payment.getAmountPaidDecimal()
                                                                                .compareTo(ZERO) > 0));

                if (hasFinancialActivity) {
                        log.warn(
                                        "Skipping disbursement schedule rebuild for loan {} because payment activity already exists",
                                        loan.getId());
                        return;
                }

                if (!existingPayments.isEmpty()) {
                        paymentRepo.deleteAll(existingPayments);
                        paymentRepo.flush();
                }

                generateRepaymentSchedule(loan);
        }

        // ================================================================
        // GENERATE REPAYMENT SCHEDULE
        // ================================================================

        private void generateRepaymentSchedule(
                        Loan loan) {

                if (loan == null) {

                        throw new IllegalArgumentException(
                                        "Loan cannot be null");
                }

                if (loan.getId() == null) {

                        throw new IllegalArgumentException(
                                        "Loan ID is required before generating repayment schedule");
                }

                if (loan.getOrganization() == null
                                || loan.getOrganization().getId() == null) {

                        throw new IllegalArgumentException(
                                        "Loan organization is required");
                }

                // ============================================================
                // DUPLICATE PROTECTION
                // ============================================================

                if (!paymentRepo
                                .findByLoanId(
                                                loan.getId())
                                .isEmpty()) {

                        log.warn(
                                        "Payment schedule already exists for loan {}, skipping generation",
                                        loan.getId());

                        return;
                }

                int months = loan.getDurationMonths() != null
                                ? loan.getDurationMonths()
                                : DEFAULT_LOAN_DURATION_MONTHS;

                validateLoanDuration(months);

                BigDecimal principal = normalizePrincipal(
                                moneyValue(
                                                loan.getAmountDecimal()));

                if (principal.compareTo(MIN_LOAN_AMOUNT) < 0) {

                        throw new IllegalArgumentException(
                                        "Loan principal cannot be below "
                                                        + MIN_LOAN_AMOUNT);
                }

                // ============================================================
                // CONTRACTUAL LOAN RATES
                // ============================================================

                BigDecimal interestRate = moneyValue(loan.getInterestRateDecimal());
                BigDecimal managementRate = moneyValue(loan.getManagementFeeRateDecimal());

                if (interestRate.compareTo(ZERO) <= 0
                                || managementRate.compareTo(ZERO) < 0) {
                        LoanProduct product = loanProductRepo
                                        .findFirstByOrganization_IdAndLoanTypeAndActiveTrue(
                                                        loan.getOrganization().getId(),
                                                        loan.getLoanType())
                                        .orElseThrow(() -> new IllegalStateException(
                                                        "No active loan product pricing is configured for this organization."));

                        if (interestRate.compareTo(ZERO) <= 0) {
                                interestRate = moneyValue(product.getInterestRateDecimal());
                        }
                        if (managementRate.compareTo(ZERO) < 0) {
                                managementRate = moneyValue(product.getManagementFeePercentDecimal());
                        }
                }

                validateInterestRate(interestRate);
                validateInterestRate(managementRate);

                BigDecimal combinedMonthlyRate = money(interestRate.add(managementRate));

                loan.setInterestRate(interestRate);
                loan.setManagementFeeRate(managementRate);
                loan.setProcessingFeeRate(
                                loan.getProcessingFeeRateDecimal() != null
                                                ? moneyValue(loan.getProcessingFeeRateDecimal())
                                                : PROCESSING_FEE_RATE);
                loan.setInterestRateType(
                                "MONTHLY");

                // ============================================================
                // PROCESSING FEE
                // ============================================================

                BigDecimal processingFee = money(
                                principal
                                                .multiply(
                                                                moneyValue(loan.getProcessingFeeRateDecimal()))
                                                .divide(
                                                                ONE_HUNDRED,
                                                                16,
                                                                RoundingMode.HALF_UP));

                loan.setProcessingFee(
                                processingFee);

                // ============================================================
                // CONTRACTUAL MONTHLY DECLINING-BALANCE SCHEDULE
                // ============================================================

                BigDecimal balance = principal;
                BigDecimal accumulatedInterest = ZERO;
                BigDecimal accumulatedManagementFee = ZERO;
                LocalDate firstDueDate = null;
                Long orgId = loan.getOrganization().getId();

                LocalDate scheduleBaseDate = loan.getDisbursedAt() != null
                                ? loan.getDisbursedAt().toLocalDate()
                                : (loan.getStartDate() != null
                                                ? loan.getStartDate()
                                                : LocalDate.now());

                for (int i = 1; i <= months; i++) {
                        balance = money(balance);

                        LocalDate rawDueDate = scheduleBaseDate.plusMonths(i);
                        LocalDate dueDate = holidayService.adjustToBusinessDay(
                                        orgId,
                                        rawDueDate);

                        if (firstDueDate == null) {
                                firstDueDate = dueDate;
                        }

                        FinancialPolicy.ScheduleLine line = FinancialPolicy.contractualScheduleLine(
                                        balance,
                                        months - i + 1,
                                        interestRate,
                                        managementRate);

                        BigDecimal principalComponent = money(line.principal());
                        BigDecimal interest = money(line.interest());
                        BigDecimal managementFee = money(line.managementFee());
                        BigDecimal installmentAmount = money(line.installment());
                        balance = money(line.remainingBalance());

                        accumulatedInterest = money(
                                        accumulatedInterest.add(interest));

                        accumulatedManagementFee = money(
                                        accumulatedManagementFee.add(managementFee));

                        Payment payment = Payment.builder()
                                        .paymentReference(generatePayRef(loan, i))
                                        .loan(loan)
                                        .organization(loan.getOrganization())
                                        .installmentNumber(i)
                                        .amount(installmentAmount)
                                        .principalComponent(principalComponent)
                                        .interestComponent(interest)
                                        .managementFeeComponent(managementFee)
                                        .scheduledInterest(interest)
                                        .scheduledManagementFee(managementFee)
                                        .cycleInterestDue(interest)
                                        .cycleInterestRemaining(interest)
                                        .cycleManagementFeeDue(managementFee)
                                        .cycleManagementFeeRemaining(managementFee)
                                        .interestCalculationDate(null)
                                        .dueDate(dueDate)
                                        .paid(false)
                                        .amountPaid(ZERO)
                                        .penalty(ZERO)
                                        .penaltyPaid(ZERO)
                                        .outstandingAfter(balance)
                                        .status(Payment.PaymentStatus.PENDING)
                                        .build();

                        paymentRepo.save(payment);
                }

                // ============================================================
                // STORE LOAN TOTALS
                // ============================================================

                loan.setAmount(
                                principal);

                loan.setOutstandingBalance(
                                principal);

                loan.setTotalInterest(
                                accumulatedInterest);

                loan.setManagementFee(
                                accumulatedManagementFee);

                loan.setManagementFeePaid(
                                ZERO);

                loan.setInterestPaid(
                                ZERO);

                // Approval/schedule generation happens before disbursement.
                // The one-time processing fee is therefore still unpaid here.
                // Do not change this to a collection event: actual collection
                // is recorded by the disbursement flow.
                loan.setProcessingFee(
                                processingFee);

                loan.setNextDueDate(
                                firstDueDate != null
                                                ? firstDueDate
                                                : holidayService.adjustToBusinessDay(
                                                                orgId,
                                                                LocalDate.now().plusMonths(1)));

                loan.setCreditQuality(
                                Loan.CreditQuality.CURRENT);

                loan.setDaysOverdue(0);

                loan.setTotalRepayable(
                                money(
                                                principal
                                                                .add(accumulatedInterest)
                                                                .add(accumulatedManagementFee)));

                loanRepo.save(loan);
        }

        // ================================================================
        // RISK SCORING
        // ================================================================

        @Async
        public void scoreAsync(
                        Loan loan) {

                try {

                        if (loan == null) {
                                return;
                        }

                        RiskScoringService.RiskResult risk = riskService.score(loan);

                        if (risk == null) {
                                return;
                        }

                        loan.setRiskScore(
                                        BigDecimal.valueOf(risk.getScore()));

                        loan.setRiskCategory(
                                        risk.getCategory());

                        loanRepo.save(loan);

                } catch (Exception e) {

                        log.warn(
                                        "Risk scoring skipped: {}",
                                        e.getMessage(),
                                        e);
                }
        }

        // ================================================================
        // LOAN CALCULATION
        // ================================================================

        /**
         * Single source of truth for the approval-time contractual total.
         *
         * This mirrors FinancialPolicy.contractualScheduleLine(), including
         * declining-balance principal, monthly interest and monthly management
         * fee. It intentionally excludes the one-time processing fee.
         */
        private BigDecimal calculateContractualTotalRepayable(
                        BigDecimal principal,
                        BigDecimal monthlyInterestRate,
                        BigDecimal monthlyManagementFeeRate,
                        int months) {

                BigDecimal balance = normalizePrincipal(principal);
                BigDecimal totalInterest = ZERO;
                BigDecimal totalManagementFee = ZERO;

                for (int i = 1; i <= months; i++) {
                        FinancialPolicy.ScheduleLine line = FinancialPolicy.contractualScheduleLine(
                                        balance,
                                        months - i + 1,
                                        monthlyInterestRate,
                                        monthlyManagementFeeRate);

                        totalInterest = money(totalInterest.add(line.interest()));
                        totalManagementFee = money(totalManagementFee.add(line.managementFee()));
                        balance = money(line.remainingBalance());
                }

                return money(
                                normalizePrincipal(principal)
                                                .add(totalInterest)
                                                .add(totalManagementFee));
        }

        private BigDecimal[] calcLoan(
                        BigDecimal principal,
                        BigDecimal rate,
                        int months,
                        String rateType) {

                principal = normalizePrincipal(principal);
                validateLoanDuration(months);

                if (principal.compareTo(MIN_LOAN_AMOUNT) < 0) {
                        throw new IllegalArgumentException(
                                        "Loan principal must be at least " + MIN_LOAN_AMOUNT);
                }

                if (rate == null) {
                        throw new IllegalArgumentException("Loan rate is required");
                }

                validateInterestRate(rate);
                validateRateType(rateType);

                BigDecimal monthlyRatePercent = rate;
                if ("ANNUAL".equalsIgnoreCase(rateType)) {
                        monthlyRatePercent = rate.divide(
                                        TWELVE,
                                        16,
                                        RoundingMode.HALF_UP);
                }

                if (!"MONTHLY".equalsIgnoreCase(rateType)
                                && !"ANNUAL".equalsIgnoreCase(rateType)) {
                        throw new IllegalArgumentException(
                                        "Unsupported loan rate type: " + rateType);
                }

                BigDecimal balance = money(principal);
                BigDecimal totalRecurringCharges = ZERO;
                BigDecimal firstInstallment = ZERO;

                for (int i = 1; i <= months; i++) {
                        FinancialPolicy.ScheduleLine line = FinancialPolicy.contractualScheduleLine(
                                        balance,
                                        months - i + 1,
                                        monthlyRatePercent,
                                        ZERO);

                        BigDecimal installment = money(line.principal().add(line.interest()));

                        if (i == 1) {
                                firstInstallment = installment;
                        }

                        totalRecurringCharges = money(
                                        totalRecurringCharges.add(line.interest()));
                        balance = money(line.remainingBalance());
                }

                return new BigDecimal[] {
                                firstInstallment,
                                money(principal.add(totalRecurringCharges))
                };
        }

        // ================================================================
        // MONTHLY RATE
        // ================================================================

        private BigDecimal calculateMonthlyRate(
                        BigDecimal rate,
                        String rateType) {

                if (rate == null) {
                        return ZERO;
                }

                validateInterestRate(rate);

                validateRateType(rateType);

                if ("MONTHLY".equalsIgnoreCase(
                                rateType)) {

                        return rate.divide(
                                        ONE_HUNDRED,
                                        16,
                                        RoundingMode.HALF_UP);
                }

                return rate
                                .divide(
                                                ONE_HUNDRED,
                                                16,
                                                RoundingMode.HALF_UP)
                                .divide(
                                                TWELVE,
                                                16,
                                                RoundingMode.HALF_UP);
        }

        // ================================================================
        // LOAN DURATION VALIDATION
        // ================================================================

        private void validateLoanDuration(
                        Integer months) {

                if (months == null) {

                        throw new IllegalArgumentException(
                                        "Loan duration is required");
                }

                if (months <= 0) {

                        throw new IllegalArgumentException(
                                        "Loan duration must be greater than zero");
                }

                if (months > MAX_LOAN_DURATION_MONTHS) {

                        throw new IllegalArgumentException(
                                        "Loan duration cannot exceed "
                                                        + MAX_LOAN_DURATION_MONTHS
                                                        + " months");
                }
        }

        // ================================================================
        // INTEREST RATE VALIDATION
        // ================================================================

        private void validateInterestRate(
                        BigDecimal rate) {

                if (rate == null) {

                        throw new IllegalArgumentException(
                                        "Interest rate is required");
                }

                if (rate.compareTo(ZERO) < 0) {

                        throw new IllegalArgumentException(
                                        "Interest rate cannot be negative");
                }

                BigDecimal maximumReasonableRate = bd("1000.0");

                if (rate.compareTo(
                                maximumReasonableRate) > 0) {

                        throw new IllegalArgumentException(
                                        "Interest rate is unreasonably high");
                }
        }

        // ================================================================
        // BIG DECIMAL HELPERS
        // ================================================================

        private static BigDecimal bd(
                        String value) {

                return new BigDecimal(value);
        }

        private static BigDecimal bd(
                        double value) {

                if (Double.isNaN(value)
                                || Double.isInfinite(value)) {

                        throw new IllegalArgumentException(
                                        "Invalid numeric value: "
                                                        + value);
                }

                return BigDecimal.valueOf(value);
        }

        private BigDecimal toBigDecimal(
                        Object value) {

                if (value == null) {
                        return null;
                }

                if (value instanceof BigDecimal decimal) {
                        return decimal;
                }

                if (value instanceof Integer integer) {
                        return BigDecimal.valueOf(
                                        integer.longValue());
                }

                if (value instanceof Long longValue) {
                        return BigDecimal.valueOf(
                                        longValue);
                }

                if (value instanceof Double doubleValue) {

                        if (doubleValue.isNaN()
                                        || doubleValue.isInfinite()) {

                                throw new IllegalArgumentException(
                                                "Invalid numeric value: "
                                                                + doubleValue);
                        }

                        return BigDecimal.valueOf(
                                        doubleValue);
                }

                if (value instanceof Float floatValue) {

                        if (floatValue.isNaN()
                                        || floatValue.isInfinite()) {

                                throw new IllegalArgumentException(
                                                "Invalid numeric value: "
                                                                + floatValue);
                        }

                        return BigDecimal.valueOf(
                                        floatValue.doubleValue());
                }

                if (value instanceof Short shortValue) {

                        return BigDecimal.valueOf(
                                        shortValue.longValue());
                }

                if (value instanceof Byte byteValue) {

                        return BigDecimal.valueOf(
                                        byteValue.longValue());
                }

                if (value instanceof Number number) {

                        try {

                                return new BigDecimal(
                                                number.toString());

                        } catch (NumberFormatException e) {

                                throw new IllegalArgumentException(
                                                "Invalid numeric value: "
                                                                + value,
                                                e);
                        }
                }

                if (value instanceof String string) {

                        if (string.isBlank()) {
                                return null;
                        }

                        try {

                                return new BigDecimal(
                                                string.trim());

                        } catch (NumberFormatException e) {

                                throw new IllegalArgumentException(
                                                "Invalid numeric value: "
                                                                + string,
                                                e);
                        }
                }

                throw new IllegalArgumentException(
                                "Unsupported numeric type: "
                                                + value.getClass().getName());
        }

        private BigDecimal moneyValue(
                        Object value) {

                BigDecimal result = toBigDecimal(value);

                return result != null
                                ? result
                                : ZERO;
        }

        private BigDecimal money(
                        BigDecimal value) {

                if (value == null) {

                        return ZERO.setScale(
                                        2,
                                        RoundingMode.HALF_UP);
                }

                return value.setScale(
                                2,
                                RoundingMode.HALF_UP);
        }

        private BigDecimal normalizePrincipal(
                        BigDecimal value) {

                if (value == null) {

                        throw new IllegalArgumentException(
                                        "Invalid loan principal: null");
                }

                if (value.compareTo(ZERO) < 0) {

                        throw new IllegalArgumentException(
                                        "Invalid loan principal: "
                                                        + value);
                }

                /*
                 * Principal is stored as a whole currency unit.
                 */
                return value.setScale(
                                0,
                                RoundingMode.HALF_UP);
        }

        private double toDouble(
                        Object value) {

                return moneyValue(value).doubleValue();
        }

        private String formatMoney(
                        BigDecimal value) {

                if (value == null) {
                        return "0";
                }

                return value
                                .setScale(
                                                0,
                                                RoundingMode.HALF_UP)
                                .toPlainString();
        }

        // ================================================================
        // RATE TYPE VALIDATION
        // ================================================================

        private void validateRateType(
                        String rateType) {

                if (rateType == null
                                || rateType.isBlank()) {

                        throw new IllegalArgumentException(
                                        "Interest rate type is required");
                }

                if (!"ANNUAL".equalsIgnoreCase(rateType)
                                && !"MONTHLY".equalsIgnoreCase(rateType)) {

                        throw new IllegalArgumentException(
                                        "Interest rate type must be MONTHLY or ANNUAL");
                }
        }

        // ================================================================
        // REFERENCE NUMBER
        // ================================================================

        private String generateRef(
                        Organization org) {

                String prefix = "RW";

                if (org != null
                                && org.getCountry() != null
                                && !org.getCountry().trim().isEmpty()) {

                        prefix = org.getCountry()
                                        .trim()
                                        .toUpperCase();
                }

                String timestamp = LocalDateTime.now()
                                .format(
                                                DateTimeFormatter.ofPattern(
                                                                "yyyyMMddHHmmssSSS"));

                return prefix + timestamp;
        }

        // ================================================================
        // PAYMENT REFERENCE
        // ================================================================

        private String generatePayRef(
                        Loan loan,
                        int installment) {

                if (loan == null) {

                        throw new IllegalArgumentException(
                                        "Loan is required to generate payment reference");
                }

                if (loan.getReferenceNumber() == null
                                || loan.getReferenceNumber().isBlank()) {

                        throw new IllegalArgumentException(
                                        "Loan reference number is required");
                }

                return "PAY-"
                                + loan.getReferenceNumber()
                                + "-"
                                + String.format(
                                                "%03d",
                                                installment);
        }

        // ================================================================
        // AUDIT
        // ================================================================

        private void audit(
                        Organization org,
                        User user,
                        String action,
                        String entityType,
                        String entityId,
                        String desc) {

                auditService.log(
                                org,
                                user,
                                action,
                                entityType,
                                entityId,
                                desc);
        }

        // ================================================================
        // REPOSITORY ACCESS
        // ================================================================

        public LoanRepository getLoanRepository() {

                return loanRepo;
        }
}