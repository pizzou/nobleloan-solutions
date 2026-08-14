package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.DashboardStats;
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

        private static final BigDecimal MONTHLY_INTEREST_RATE = bd("5.0");

        private static final BigDecimal MONTHLY_MANAGEMENT_FEE_RATE = bd("5.0");

        private static final BigDecimal TOTAL_MONTHLY_CHARGE_RATE = MONTHLY_INTEREST_RATE
                        .add(MONTHLY_MANAGEMENT_FEE_RATE);

        private static final BigDecimal PROCESSING_FEE_RATE = bd("2.0");

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
                // FIXED MONTHLY RATES
                // ============================================================

                BigDecimal interestRate = MONTHLY_INTEREST_RATE;

                BigDecimal managementFeeRate = MONTHLY_MANAGEMENT_FEE_RATE;

                BigDecimal totalMonthlyRate = TOTAL_MONTHLY_CHARGE_RATE;

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

                BigDecimal processingFeeRate = PROCESSING_FEE_RATE;

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

                BigDecimal dti = monthlyIncome.compareTo(ZERO) > 0
                                ? money(
                                                monthlyInstallment
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
                                        + " — monthly interest 5%"
                                        + " — monthly management fee 5%"
                                        + " — processing fee 2%"
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
                                        + " — monthly interest 5%"
                                        + " — monthly management fee 5%"
                                        + " — processing fee 2%"
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

        @Transactional
        public Loan approveLoan(
                        Long loanId,
                        User approvedBy,
                        String notes,
                        Double newInterestRate) {

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

                BigDecimal principal = normalizePrincipal(
                                moneyValue(
                                                loan.getAmountDecimal()));

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
                // RATE LOCK
                // ============================================================

                if (newInterestRate != null) {

                        BigDecimal requestedRate = bd(newInterestRate);

                        if (requestedRate.compareTo(
                                        MONTHLY_INTEREST_RATE) != 0) {

                                throw new IllegalArgumentException(
                                                "Loan interest rate is fixed at "
                                                                + MONTHLY_INTEREST_RATE
                                                                + "% per month");
                        }
                }

                loan.setInterestRate(
                                MONTHLY_INTEREST_RATE);

                loan.setManagementFeeRate(
                                MONTHLY_MANAGEMENT_FEE_RATE);

                loan.setProcessingFeeRate(
                                PROCESSING_FEE_RATE);

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
                                                                PROCESSING_FEE_RATE)
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

                BigDecimal[] calc = calcLoan(
                                principal,
                                TOTAL_MONTHLY_CHARGE_RATE,
                                durationMonths,
                                "MONTHLY");

                loan.setTotalRepayable(
                                calc[1]);

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

                if (paymentRepo
                                .findByLoanId(
                                                saved.getId())
                                .isEmpty()) {

                        generateRepaymentSchedule(saved);

                } else {

                        log.warn(
                                        "Repayment schedule already exists for loan {}, skipping regeneration",
                                        saved.getId());
                }

                audit(
                                saved.getOrganization(),
                                approvedBy,
                                "LOAN_APPROVED",
                                "LOAN",
                                loanId.toString(),
                                "Loan "
                                                + saved.getReferenceNumber()
                                                + " approved — fixed monthly interest 5%"
                                                + " — fixed monthly management fee 5%"
                                                + " — one-time processing fee 2%"
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
                                                + ". Monthly interest is 5% and monthly management fee is 5%."
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

                Loan loan = getLoanForOrg(
                                loanId,
                                officer.getOrganization().getId());

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
                // ENFORCE FIXED BUSINESS RATES
                // ============================================================

                loan.setInterestRate(
                                MONTHLY_INTEREST_RATE);

                loan.setManagementFeeRate(
                                MONTHLY_MANAGEMENT_FEE_RATE);

                loan.setProcessingFeeRate(
                                PROCESSING_FEE_RATE);

                loan.setInterestRateType("MONTHLY");

                BigDecimal processingFee = money(
                                exactPrincipal
                                                .multiply(
                                                                PROCESSING_FEE_RATE)
                                                .divide(
                                                                ONE_HUNDRED,
                                                                16,
                                                                RoundingMode.HALF_UP));

                loan.setProcessingFee(
                                processingFee);

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
                                                + " — monthly interest 5%"
                                                + " — monthly management fee 5%"
                                                + " — processing fee 2% one time"
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
                                                + ". Monthly interest is 5% and monthly management fee is 5%."
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

                return loan;
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

                if (org == null
                                || org.getId() == null) {

                        throw new IllegalArgumentException(
                                        "Organization is required");
                }

                LocalDate today = LocalDate.now();

                LocalDate firstOfMonth = today.withDayOfMonth(1);

                long overdueCount = paymentRepo
                                .findByOrganization_IdAndPaidFalseAndDueDateBefore(
                                                org.getId(),
                                                today)
                                .size();

                List<Map<String, Object>> typeBreakdown = loanRepo
                                .getLoanTypeBreakdown(org)
                                .stream()
                                .map(
                                                r -> {

                                                        Map<String, Object> m = new LinkedHashMap<>();

                                                        m.put(
                                                                        "type",
                                                                        r[0]);

                                                        m.put(
                                                                        "count",
                                                                        r[1]);

                                                        m.put(
                                                                        "amount",
                                                                        r[2]);

                                                        return m;
                                                })
                                .collect(
                                                Collectors.toList());

                List<Loan> recent = loanRepo.findRecentByOrg(
                                org,
                                PageRequest.of(
                                                0,
                                                8));

                return DashboardStats.builder()
                                .totalLoans(
                                                loanRepo.countByOrganization(org))
                                .pendingLoans(
                                                loanRepo.countByOrganizationAndStatus(
                                                                org,
                                                                LoanStatus.PENDING))
                                .activeLoans(
                                                loanRepo.countByOrganizationAndStatus(
                                                                org,
                                                                LoanStatus.ACTIVE))
                                .overdueLoans(
                                                overdueCount)
                                .completedLoans(
                                                loanRepo.countByOrganizationAndStatus(
                                                                org,
                                                                LoanStatus.PAID))
                                .defaultedLoans(
                                                loanRepo.countByOrganizationAndStatus(
                                                                org,
                                                                LoanStatus.DEFAULTED))
                                .totalDisbursed(
                                                Optional.ofNullable(
                                                                loanRepo.sumActivePrincipal(org))
                                                                .map(this::toDouble)
                                                                .orElse(0.0))
                                .totalCollected(
                                                Optional.ofNullable(
                                                                loanRepo.sumTotalCollected(org))
                                                                .map(this::toDouble)
                                                                .orElse(0.0))
                                .outstandingBalance(
                                                Optional.ofNullable(
                                                                loanRepo.sumOutstandingBalance(org))
                                                                .map(this::toDouble)
                                                                .orElse(0.0))
                                .collectedThisMonth(
                                                Optional.ofNullable(
                                                                paymentRepo.sumCollectedSince(
                                                                                org,
                                                                                firstOfMonth))
                                                                .map(this::toDouble)
                                                                .orElse(0.0))
                                .totalBorrowers(
                                                borrowerRepo.countByOrganization(org))
                                .latePaymentsCount(
                                                Optional.ofNullable(
                                                                paymentRepo.countLatePayments(org))
                                                                .orElse(0L))
                                .loanTypeBreakdown(typeBreakdown)
                                .recentLoans(recent)
                                .build();
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
                // FIXED BUSINESS RATES
                // ============================================================

                BigDecimal interestRate = MONTHLY_INTEREST_RATE;

                BigDecimal managementRate = MONTHLY_MANAGEMENT_FEE_RATE;

                BigDecimal combinedMonthlyRate = TOTAL_MONTHLY_CHARGE_RATE;

                loan.setInterestRate(
                                interestRate);

                loan.setManagementFeeRate(
                                managementRate);

                loan.setProcessingFeeRate(
                                PROCESSING_FEE_RATE);

                loan.setInterestRateType(
                                "MONTHLY");

                // ============================================================
                // PROCESSING FEE
                // ============================================================

                BigDecimal processingFee = money(
                                principal
                                                .multiply(
                                                                PROCESSING_FEE_RATE)
                                                .divide(
                                                                ONE_HUNDRED,
                                                                16,
                                                                RoundingMode.HALF_UP));

                loan.setProcessingFee(
                                processingFee);

                // ============================================================
                // DAILY-BASIS DECLINING-BALANCE SCHEDULE
                // ============================================================

                BigDecimal balance = principal;
                BigDecimal accumulatedInterest = ZERO;
                BigDecimal accumulatedManagementFee = ZERO;
                LocalDate firstDueDate = null;
                Long orgId = loan.getOrganization().getId();

                LocalDate startDate = loan.getDisbursedAt() != null
                                ? loan.getDisbursedAt().toLocalDate()
                                : (loan.getStartDate() != null
                                                ? loan.getStartDate()
                                                : LocalDate.now());

                int remainingInstallments = months;

                for (int i = 1; i <= months; i++) {
                        balance = money(balance);

                        LocalDate rawDueDate = startDate.plusMonths(i);
                        LocalDate dueDate = holidayService.adjustToBusinessDay(
                                        orgId,
                                        rawDueDate);

                        if (firstDueDate == null) {
                                firstDueDate = dueDate;
                        }

                        BigDecimal principalComponent = i == months
                                        ? money(balance)
                                        : money(balance.divide(
                                                        BigDecimal.valueOf(remainingInstallments),
                                                        16,
                                                        RoundingMode.HALF_UP));

                        BigDecimal interest = accrueDaily(
                                        balance,
                                        startDate,
                                        dueDate,
                                        interestRate);

                        BigDecimal managementFee = accrueDaily(
                                        balance,
                                        startDate,
                                        dueDate,
                                        managementRate);

                        BigDecimal installmentAmount = money(
                                        principalComponent
                                                        .add(interest)
                                                        .add(managementFee));

                        accumulatedInterest = money(
                                        accumulatedInterest.add(interest));

                        accumulatedManagementFee = money(
                                        accumulatedManagementFee.add(managementFee));

                        balance = money(
                                        balance.subtract(principalComponent));

                        if (balance.compareTo(MIN_MONEY_UNIT) < 0) {
                                balance = ZERO;
                        }

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

                        startDate = dueDate;
                        remainingInstallments--;
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

                loan.setProcessingFeePaid(
                                ZERO);

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

        private BigDecimal accrueDaily(
                        BigDecimal outstandingPrincipal,
                        LocalDate startDate,
                        LocalDate endDate,
                        BigDecimal monthlyRatePercent) {

                if (outstandingPrincipal == null
                                || outstandingPrincipal.compareTo(ZERO) <= 0
                                || startDate == null
                                || endDate == null
                                || !startDate.isBefore(endDate)
                                || monthlyRatePercent == null
                                || monthlyRatePercent.compareTo(ZERO) <= 0) {
                        return ZERO;
                }

                BigDecimal total = ZERO;
                LocalDate cursor = startDate;

                while (cursor.isBefore(endDate)) {
                        YearMonth month = YearMonth.from(cursor);
                        BigDecimal dailyRate = monthlyRatePercent
                                        .divide(ONE_HUNDRED, 16, RoundingMode.HALF_UP)
                                        .divide(BigDecimal.valueOf(month.lengthOfMonth()), 16, RoundingMode.HALF_UP);

                        total = total.add(outstandingPrincipal.multiply(dailyRate));
                        cursor = cursor.plusDays(1);
                }

                return money(total);
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

                LocalDate startDate = LocalDate.now();
                BigDecimal balance = money(principal);
                BigDecimal totalRecurringCharges = ZERO;
                BigDecimal firstInstallment = ZERO;
                int remainingInstallments = months;

                for (int i = 1; i <= months; i++) {
                        LocalDate dueDate = startDate.plusMonths(1);

                        BigDecimal principalComponent = i == months
                                        ? money(balance)
                                        : money(balance.divide(
                                                        BigDecimal.valueOf(remainingInstallments),
                                                        16,
                                                        RoundingMode.HALF_UP));

                        BigDecimal recurringCharge = accrueDaily(
                                        balance,
                                        startDate,
                                        dueDate,
                                        monthlyRatePercent);

                        BigDecimal installment = money(
                                        principalComponent.add(recurringCharge));

                        if (i == 1) {
                                firstInstallment = installment;
                        }

                        totalRecurringCharges = money(
                                        totalRecurringCharges.add(recurringCharge));

                        balance = money(balance.subtract(principalComponent));
                        if (balance.compareTo(MIN_MONEY_UNIT) < 0) {
                                balance = ZERO;
                        }

                        startDate = dueDate;
                        remainingInstallments--;
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