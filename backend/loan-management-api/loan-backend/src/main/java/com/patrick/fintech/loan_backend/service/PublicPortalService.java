package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.publicportal.BorrowerDashboardRequest;
import com.patrick.fintech.loan_backend.dto.publicportal.BorrowerDashboardResponse;
import com.patrick.fintech.loan_backend.dto.publicportal.PaymentHistoryResponse;
import com.patrick.fintech.loan_backend.dto.publicportal.UpcomingInstallmentResponse;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.PaymentRepository;
import com.patrick.fintech.loan_backend.security.HmacIndexer;
import com.patrick.fintech.loan_backend.util.FinancialPolicy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PublicPortalService {

        private final LoanRepository loanRepository;
        private final PaymentRepository paymentRepository;
        private final FinancialCalculationService financialCalculationService;

        private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

        private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(
                        2,
                        RoundingMode.HALF_UP);

        // ================================================================
        // PUBLIC DASHBOARD
        // ================================================================

        @Transactional(readOnly = true)
        public BorrowerDashboardResponse getDashboard(
                        BorrowerDashboardRequest request) {

                if (request == null) {
                        throw new IllegalArgumentException(
                                        "Dashboard request is required");
                }

                if (request.getReference() == null
                                || request.getReference().isBlank()) {

                        throw new IllegalArgumentException(
                                        "Reference number is required");
                }

                if (request.getPhone() == null
                                || request.getPhone().isBlank()) {

                        throw new IllegalArgumentException(
                                        "Phone number is required");
                }

                String phoneHash = HmacIndexer.index(
                                request.getPhone().trim());

                Loan loan = loanRepository
                                .findPublicDashboardLoan(
                                                request.getReference().trim(),
                                                phoneHash)
                                .orElseThrow(
                                                () -> new RuntimeException(
                                                                "Application not found"));

                // ============================================================
                // BASIC DATES
                // ============================================================

                LocalDate today = LocalDate.now();

                int daysUntilDue = 0;

                if (loan.getNextPaymentDate() != null) {

                        daysUntilDue = Math.max(
                                        0,
                                        (int) ChronoUnit.DAYS.between(
                                                        today,
                                                        loan.getNextPaymentDate()));
                }

                // ============================================================
                // PAYMENT HISTORY
                // ============================================================

                List<Payment> loanPayments = paymentRepository.findByLoanId(
                                loan.getId());

                if (loanPayments == null) {
                        loanPayments = List.of();
                }

                // ============================================================
                // CURRENT PAYMENT CYCLE
                // ============================================================

                Optional<Payment> existingCurrentCycle = loanPayments.stream()
                                .filter(
                                                payment -> payment != null
                                                                && !Boolean.TRUE.equals(
                                                                                payment.getPaid()))
                                .filter(
                                                payment -> safe(
                                                                payment.getAmountPaidDecimal()).compareTo(
                                                                                ZERO) > 0)
                                .min(
                                                Comparator.comparing(
                                                                Payment::getDueDate,
                                                                Comparator.nullsLast(
                                                                                Comparator.naturalOrder())));

                // ============================================================
                // OLDEST UNPAID INSTALLMENT
                // ============================================================

                Optional<Payment> unpaidInstallment = loanPayments.stream()
                                .filter(
                                                payment -> payment != null
                                                                && !Boolean.TRUE.equals(
                                                                                payment.getPaid()))
                                .min(
                                                Comparator.comparing(
                                                                Payment::getDueDate,
                                                                Comparator.nullsLast(
                                                                                Comparator.naturalOrder())));

                Payment currentInstallment = null;

                if (existingCurrentCycle.isPresent()) {

                        currentInstallment = existingCurrentCycle.get();

                } else if (unpaidInstallment.isPresent()) {

                        currentInstallment = unpaidInstallment.get();
                }

                // ============================================================
                // CURRENT PAYABLE AMOUNT
                // ============================================================

                BigDecimal currentPayableAmount = calculateCurrentPayableAmount(
                                loan,
                                currentInstallment,
                                loanPayments);

                // ============================================================
                // REPAYMENT PROGRESS
                // ============================================================

                BigDecimal totalRepayable = safe(
                                loan.getTotalRepayableDecimal());

                BigDecimal totalPaid = safe(
                                loan.getTotalPaidDecimal());

                double repaymentProgress = 0.0;

                if (totalRepayable.compareTo(
                                ZERO) > 0) {

                        repaymentProgress = totalPaid
                                        .divide(
                                                        totalRepayable,
                                                        8,
                                                        RoundingMode.HALF_UP)
                                        .multiply(
                                                        ONE_HUNDRED)
                                        .doubleValue();

                        repaymentProgress = Math.max(
                                        0.0,
                                        Math.min(
                                                        repaymentProgress,
                                                        100.0));
                }

                // ============================================================
                // RECENT PAYMENTS
                // ============================================================

                List<PaymentHistoryResponse> recentPayments = paymentRepository
                                .findTop10ByLoanIdOrderByPaidDateDesc(
                                                loan.getId())
                                .stream()
                                .filter(
                                                payment -> payment != null)
                                .map(
                                                this::toPaymentHistoryResponse)
                                .toList();

                // ============================================================
                // UPCOMING INSTALLMENTS
                // ============================================================

                List<UpcomingInstallmentResponse> upcomingInstallments = loanPayments
                                .stream()
                                .filter(
                                                payment -> payment != null
                                                                && Boolean.FALSE.equals(
                                                                                payment.getPaid()))
                                .sorted(
                                                Comparator.comparing(
                                                                Payment::getDueDate,
                                                                Comparator.nullsLast(
                                                                                Comparator.naturalOrder())))
                                .limit(6)
                                .map(
                                                this::toUpcomingInstallmentResponse)
                                .toList();

                // ============================================================
                // BORROWER
                // ============================================================

                Long borrowerId = null;

                if (loan.getBorrower() != null) {

                        borrowerId = loan.getBorrower().getId();
                }

                // ============================================================
                // ORGANIZATION
                // ============================================================

                Long organizationId = null;

                if (loan.getOrganization() != null) {

                        organizationId = loan.getOrganization().getId();
                }

                // ============================================================
                // ALL BORROWER LOANS
                // ============================================================

                List<Loan> borrowerLoans = borrowerId != null
                                && organizationId != null
                                                ? loanRepository
                                                                .findByBorrowerIdAndOrganizationId(
                                                                                borrowerId,
                                                                                organizationId)
                                                : List.of();

                // ============================================================
                // LOAN STATISTICS
                // ============================================================

                int activeLoans = 0;
                int overdueLoans = 0;
                int completedLoans = 0;

                for (Loan borrowerLoan : borrowerLoans) {

                        if (borrowerLoan == null
                                        || borrowerLoan.getStatus() == null) {
                                continue;
                        }

                        switch (borrowerLoan
                                        .getStatus()
                                        .name()) {

                                case "ACTIVE" -> activeLoans++;

                                case "OVERDUE" -> overdueLoans++;

                                case "PAID", "CLOSED" -> completedLoans++;

                                default -> {
                                        // Other statuses are not active/completed.
                                }
                        }
                }

                // ============================================================
                // PAYMENT METHODS
                // ============================================================

                List<String> paymentMethods = List.of(
                                "MTN Mobile Money",
                                "Airtel Money",
                                "Bank Transfer",
                                "Visa / Mastercard");

                // ============================================================
                // BORROWER NAME
                // ============================================================

                String borrowerName = null;

                if (loan.getBorrower() != null) {

                        borrowerName = loan.getBorrower().getFullName();
                }

                // ============================================================
                // LOAN OFFICER
                // ============================================================

                String loanOfficer = null;

                if (loan.getLoanOfficer() != null) {

                        loanOfficer = loan.getLoanOfficer().getFullName();
                }

                // ============================================================
                // RESPONSE
                // ============================================================

                return BorrowerDashboardResponse.builder()

                                .loanId(
                                                loan.getId())

                                .referenceNumber(
                                                loan.getReferenceNumber())

                                .borrowerName(
                                                borrowerName)

                                .status(
                                                loan.getStatus() == null
                                                                ? null
                                                                : loan.getStatus().name())

                                .loanType(
                                                loan.getLoanType() == null
                                                                ? null
                                                                : loan.getLoanType().name())

                                .principal(
                                                loan.getAmountDecimal())

                                .outstandingBalance(
                                                loan.getOutstandingBalanceDecimal())

                                .totalPaid(
                                                loan.getTotalPaidDecimal())

                                .totalRepayable(
                                                loan.getTotalRepayableDecimal())

                                .nextInstallmentAmount(
                                                currentPayableAmount)

                                .nextPaymentDate(
                                                loan.getNextPaymentDate())

                                .nextDueDate(
                                                loan.getNextDueDate())

                                .maturityDate(
                                                loan.getMaturityDate())

                                .missedInstallments(
                                                loan.getMissedInstallments())

                                .daysOverdue(
                                                loan.getDaysOverdue())

                                .interestRate(
                                                loan.getInterestRateDecimal())

                                .currency(
                                                loan.getCurrency())

                                .loanOfficer(
                                                loanOfficer)

                                .activeLoans(
                                                activeLoans)

                                .overdueLoans(
                                                overdueLoans)

                                .completedLoans(
                                                completedLoans)

                                .daysUntilDue(
                                                daysUntilDue)

                                .repaymentProgress(
                                                repaymentProgress)

                                .recentPayments(
                                                recentPayments)

                                .upcomingInstallments(
                                                upcomingInstallments)

                                .availablePaymentMethods(
                                                paymentMethods)

                                .build();
        }

        // ================================================================
        // CURRENT PAYABLE AMOUNT
        // ================================================================

        /**
         * Calculates the amount currently payable by the borrower.
         *
         * Current platform rules:
         *
         * 5% monthly interest
         * 5% monthly management fee
         * 15% monthly penalty when overdue
         *
         * Interest and management fee accrue on the outstanding
         * principal balance.
         *
         * Processing fee is NOT added here because it is a one-time
         * charge already deducted at disbursement.
         *
         * Payment priority:
         *
         * 1. Penalty
         * 2. Interest
         * 3. Management fee
         * 4. Principal
         *
         * This method is read-only and never modifies the database.
         */
        private BigDecimal calculateCurrentPayableAmount(
                        Loan loan,
                        Payment installment,
                        List<Payment> loanPayments) {

                if (loan == null) {
                        return ZERO;
                }

                // ============================================================
                // OUTSTANDING PRINCIPAL
                // ============================================================

                BigDecimal currentBalance = safe(
                                loan.getOutstandingBalanceDecimal());

                if (currentBalance.compareTo(
                                ZERO) <= 0) {
                        return ZERO;
                }

                // ============================================================
                // NO INSTALLMENT
                // ============================================================

                if (installment == null) {

                        BigDecimal scheduledAmount = safe(
                                        loan.getNextInstallmentAmountDecimal());

                        if (scheduledAmount.compareTo(
                                        ZERO) <= 0) {
                                scheduledAmount = calculateContractualMonthlyInstallment(
                                                loan);
                        }

                        return money(
                                        scheduledAmount);
                }

                // ============================================================
                // CURRENT TIME
                // ============================================================

                LocalDate today = LocalDate.now();

                LocalDateTime now = LocalDateTime.now();

                // ============================================================
                // EXISTING PAYMENT VALUES
                // ============================================================

                BigDecimal existingCycleInterestDue = safe(
                                installment.getCycleInterestDueDecimal());

                BigDecimal existingCycleInterestRemaining = safe(
                                installment.getCycleInterestRemainingDecimal());

                BigDecimal interestAlreadyPaidThisCycle = money(
                                existingCycleInterestDue
                                                .subtract(
                                                                existingCycleInterestRemaining)
                                                .max(
                                                                ZERO));

                BigDecimal existingManagementFeeDue = safe(
                                installment
                                                .getCycleManagementFeeDueDecimal());

                BigDecimal existingManagementFeeRemaining = safe(
                                installment
                                                .getCycleManagementFeeRemainingDecimal());

                BigDecimal managementAlreadyPaidThisCycle = money(
                                existingManagementFeeDue
                                                .subtract(
                                                                existingManagementFeeRemaining)
                                                .max(
                                                                ZERO));

                BigDecimal penaltyAlreadyRecorded = safe(
                                installment.getPenaltyDecimal());

                BigDecimal penaltyAlreadyPaid = safe(
                                installment.getPenaltyPaidDecimal());

                // ============================================================
                // INTEREST START
                // ============================================================

                LocalDateTime interestStartDateTime = determineInterestStartDateTime(
                                installment,
                                loan,
                                loanPayments,
                                now);

                if (interestStartDateTime.isAfter(
                                now)) {

                        interestStartDateTime = now;
                }

                // ============================================================
                // ELAPSED DAYS
                // ============================================================

                boolean firstInterestCalculation = installment.getInterestCalculationDate() == null
                                && interestAlreadyPaidThisCycle.compareTo(
                                                ZERO) <= 0
                                && existingCycleInterestDue.compareTo(
                                                ZERO) <= 0;

                long elapsedDays;

                if (firstInterestCalculation) {

                        /*
                         * Same rule as PaymentService:
                         *
                         * first payment after disbursement = 1 interest day.
                         */
                        elapsedDays = 1L;

                } else {

                        elapsedDays = Math.max(
                                        0L,
                                        ChronoUnit.DAYS.between(
                                                        interestStartDateTime.toLocalDate(),
                                                        now.toLocalDate()));
                }

                // ============================================================
                // DAILY INTEREST RATE
                // ============================================================

                BigDecimal dailyInterestRate = calculateDailyInterestRate(
                                loan);

                // ============================================================
                // DAILY MANAGEMENT RATE
                // ============================================================

                BigDecimal monthlyManagementRate = loan.getManagementFeeRateDecimal() != null
                                ? loan.getManagementFeeRateDecimal()
                                : FinancialPolicy.MONTHLY_MANAGEMENT_FEE_RATE;

                BigDecimal dailyManagementRate = FinancialPolicy.dailyRateFraction(
                                monthlyManagementRate,
                                now.toLocalDate());

                // ============================================================
                // NEW INTEREST
                // ============================================================

                BigDecimal newlyAccruedInterest = FinancialPolicy.accrueDaily(
                                currentBalance,
                                interestStartDateTime.toLocalDate(),
                                now.toLocalDate(),
                                loan.getInterestRateDecimal() != null
                                                ? loan.getInterestRateDecimal()
                                                : FinancialPolicy.MONTHLY_INTEREST_RATE);

                if (firstInterestCalculation && newlyAccruedInterest.compareTo(ZERO) <= 0) {
                        newlyAccruedInterest = FinancialPolicy.accrueDaily(
                                        currentBalance,
                                        now.toLocalDate(),
                                        now.toLocalDate().plusDays(1),
                                        loan.getInterestRateDecimal() != null
                                                        ? loan.getInterestRateDecimal()
                                                        : FinancialPolicy.MONTHLY_INTEREST_RATE);
                }

                // ============================================================
                // NEW MANAGEMENT FEE
                // ============================================================

                BigDecimal newlyAccruedManagementFee = FinancialPolicy.accrueDaily(
                                currentBalance,
                                interestStartDateTime.toLocalDate(),
                                now.toLocalDate(),
                                monthlyManagementRate);

                if (firstInterestCalculation && newlyAccruedManagementFee.compareTo(ZERO) <= 0) {
                        newlyAccruedManagementFee = FinancialPolicy.accrueDaily(
                                        currentBalance,
                                        now.toLocalDate(),
                                        now.toLocalDate().plusDays(1),
                                        monthlyManagementRate);
                }

                // ============================================================
                // TOTAL INTEREST DUE
                // ============================================================

                BigDecimal totalInterestDue = money(
                                existingCycleInterestDue
                                                .add(
                                                                newlyAccruedInterest));

                BigDecimal minimumInterestObligation = money(
                                interestAlreadyPaidThisCycle
                                                .add(
                                                                existingCycleInterestRemaining));

                if (totalInterestDue.compareTo(
                                minimumInterestObligation) < 0) {

                        totalInterestDue = minimumInterestObligation;
                }

                BigDecimal remainingInterest = money(
                                totalInterestDue
                                                .subtract(
                                                                interestAlreadyPaidThisCycle)
                                                .max(
                                                                ZERO));

                // ============================================================
                // TOTAL MANAGEMENT FEE DUE
                // ============================================================

                BigDecimal totalManagementFeeDue = money(
                                existingManagementFeeDue
                                                .add(
                                                                newlyAccruedManagementFee));

                BigDecimal minimumManagementObligation = money(
                                managementAlreadyPaidThisCycle
                                                .add(
                                                                existingManagementFeeRemaining));

                if (totalManagementFeeDue.compareTo(
                                minimumManagementObligation) < 0) {

                        totalManagementFeeDue = minimumManagementObligation;
                }

                BigDecimal remainingManagementFee = money(
                                totalManagementFeeDue
                                                .subtract(
                                                                managementAlreadyPaidThisCycle)
                                                .max(
                                                                ZERO));

                // ============================================================
                // LATE DAYS
                // ============================================================

                LocalDate cycleDueDate = installment.getDueDate() != null
                                ? installment.getDueDate()
                                : (loan.getNextDueDate() != null
                                                ? loan.getNextDueDate()
                                                : today);

                int daysLate = Math.max(
                                0,
                                (int) ChronoUnit.DAYS.between(
                                                cycleDueDate,
                                                today));

                // ============================================================
                // PENALTY
                // ============================================================

                int recordedOverdueDays = installment.getDaysLate() != null
                                ? Math.max(
                                                0,
                                                installment.getDaysLate())
                                : 0;

                int newPenaltyDays = Math.max(
                                0,
                                daysLate - recordedOverdueDays);

                BigDecimal newPenalty = ZERO;

                if (newPenaltyDays > 0
                                && currentBalance.compareTo(
                                                ZERO) > 0) {

                        newPenalty = money(
                                        FinancialPolicy.accrueDaily(
                                                        currentBalance,
                                                        cycleDueDate.plusDays(recordedOverdueDays),
                                                        today.plusDays(1),
                                                        FinancialPolicy.MONTHLY_PENALTY_RATE));
                }

                BigDecimal totalPenalty = money(
                                penaltyAlreadyRecorded
                                                .add(
                                                                newPenalty));

                BigDecimal unpaidPenalty = money(
                                totalPenalty
                                                .subtract(
                                                                penaltyAlreadyPaid)
                                                .max(
                                                                ZERO));

                // ============================================================
                // TOTAL CURRENT PAYABLE
                // ============================================================

                /*
                 * This is the amount necessary to bring the loan fully current:
                 *
                 * penalty
                 * + interest
                 * + management fee
                 * + outstanding principal
                 */
                BigDecimal currentPayable = money(
                                unpaidPenalty
                                                .add(
                                                                remainingInterest)
                                                .add(
                                                                remainingManagementFee)
                                                .add(
                                                                currentBalance));

                log.debug(
                                "PUBLIC PORTAL PAYABLE CALCULATION: " +
                                                "loanId={}, principal={}, " +
                                                "interestDays={}, dailyInterestRate={}, " +
                                                "newInterest={}, remainingInterest={}, " +
                                                "dailyManagementRate={}, " +
                                                "newManagementFee={}, " +
                                                "remainingManagementFee={}, " +
                                                "daysLate={}, newPenaltyDays={}, " +
                                                "dailyPenaltyRate={}, totalPenalty={}, " +
                                                "unpaidPenalty={}, currentPayable={}",
                                loan.getId(),
                                currentBalance,
                                elapsedDays,
                                dailyInterestRate,
                                newlyAccruedInterest,
                                remainingInterest,
                                dailyManagementRate,
                                newlyAccruedManagementFee,
                                remainingManagementFee,
                                daysLate,
                                newPenaltyDays,
                                FinancialPolicy.MONTHLY_PENALTY_RATE,
                                totalPenalty,
                                unpaidPenalty,
                                currentPayable);

                return currentPayable;
        }

        // ================================================================
        // DETERMINE INTEREST START
        // ================================================================

        private LocalDateTime determineInterestStartDateTime(
                        Payment installment,
                        Loan loan,
                        List<Payment> loanPayments,
                        LocalDateTime now) {

                if (installment != null
                                && installment.getInterestCalculationDate() != null) {

                        return installment
                                        .getInterestCalculationDate();
                }

                LocalDateTime latestTimestamp = findLatestInterestCalculationTimestamp(
                                loanPayments);

                if (latestTimestamp != null) {
                        return latestTimestamp;
                }

                /*
                 * Loan.disbursedAt is LocalDateTime.
                 */
                if (loan.getDisbursedAt() != null) {
                        return loan.getDisbursedAt();
                }

                if (installment != null
                                && installment.getCreatedAt() != null) {

                        return installment.getCreatedAt();
                }

                if (loan.getStartDate() != null) {

                        return loan
                                        .getStartDate()
                                        .atStartOfDay();
                }

                return now;
        }

        // ================================================================
        // FIND LATEST INTEREST TIMESTAMP
        // ================================================================

        private LocalDateTime findLatestInterestCalculationTimestamp(
                        List<Payment> payments) {

                if (payments == null
                                || payments.isEmpty()) {

                        return null;
                }

                return payments.stream()
                                .filter(
                                                payment -> payment != null)
                                .map(
                                                Payment::getInterestCalculationDate)
                                .filter(
                                                timestamp -> timestamp != null)
                                .max(
                                                LocalDateTime::compareTo)
                                .orElse(null);
        }

        // ================================================================
        // DAILY INTEREST RATE
        // ================================================================

        private BigDecimal calculateDailyInterestRate(
                        Loan loan) {
                BigDecimal monthlyRate = loan != null && loan.getInterestRateDecimal() != null
                                ? loan.getInterestRateDecimal()
                                : FinancialPolicy.MONTHLY_INTEREST_RATE;
                return FinancialPolicy.dailyRateFraction(monthlyRate, LocalDate.now());
        }

        // ================================================================
        // ACCRUED AMOUNT
        // ================================================================

        private BigDecimal calculateAccruedAmount(
                        BigDecimal balance,
                        BigDecimal dailyRate,
                        long elapsedDays) {

                if (balance == null
                                || balance.compareTo(
                                                ZERO) <= 0) {

                        return ZERO;
                }

                if (dailyRate == null
                                || dailyRate.compareTo(
                                                ZERO) <= 0) {

                        return ZERO;
                }

                if (elapsedDays <= 0) {
                        return ZERO;
                }

                return money(
                                balance
                                                .multiply(
                                                                dailyRate)
                                                .multiply(
                                                                BigDecimal.valueOf(
                                                                                elapsedDays)));
        }

        // ================================================================
        // CONTRACTUAL MONTHLY INSTALLMENT
        // ================================================================

        private BigDecimal calculateContractualMonthlyInstallment(
                        Loan loan) {

                if (loan == null) {
                        return ZERO;
                }

                BigDecimal principal = safe(
                                loan.getOutstandingBalanceDecimal());

                if (principal.compareTo(
                                ZERO) <= 0) {
                        return ZERO;
                }

                Integer duration = loan.getDurationMonths();

                if (duration == null
                                || duration <= 0) {

                        return ZERO;
                }

                BigDecimal interestRate = loan.getInterestRateDecimal() != null
                                ? loan.getInterestRateDecimal()
                                : FinancialPolicy.MONTHLY_INTEREST_RATE;
                BigDecimal managementRate = loan.getManagementFeeRateDecimal() != null
                                ? loan.getManagementFeeRateDecimal()
                                : FinancialPolicy.MONTHLY_MANAGEMENT_FEE_RATE;

                BigDecimal monthlyRate = interestRate
                                .add(managementRate)
                                .divide(
                                                ONE_HUNDRED,
                                                16,
                                                RoundingMode.HALF_UP);

                if (monthlyRate.compareTo(
                                ZERO) == 0) {

                        return money(
                                        principal.divide(
                                                        BigDecimal.valueOf(
                                                                        duration),
                                                        16,
                                                        RoundingMode.HALF_UP));
                }

                BigDecimal factor = BigDecimal.ONE
                                .add(
                                                monthlyRate)
                                .pow(
                                                duration,
                                                java.math.MathContext.DECIMAL128);

                BigDecimal numerator = principal
                                .multiply(
                                                monthlyRate)
                                .multiply(
                                                factor);

                BigDecimal denominator = factor.subtract(
                                BigDecimal.ONE);

                if (denominator.compareTo(
                                ZERO) == 0) {
                        return ZERO;
                }

                return money(
                                numerator.divide(
                                                denominator,
                                                16,
                                                RoundingMode.HALF_UP));
        }

        // ================================================================
        // PAYMENT HISTORY MAPPER
        // ================================================================

        private PaymentHistoryResponse toPaymentHistoryResponse(
                        Payment payment) {

                return PaymentHistoryResponse.builder()

                                .paymentId(
                                                payment.getId())

                                .paymentDate(
                                                payment.getPaidDate())

                                .amount(
                                                payment.getAmountPaidDecimal())

                                .method(
                                                payment.getPaymentMethod())

                                .status(
                                                payment.getStatus() == null
                                                                ? "UNKNOWN"
                                                                : payment
                                                                                .getStatus()
                                                                                .name())

                                .build();
        }

        // ================================================================
        // UPCOMING INSTALLMENT MAPPER
        // ================================================================

        private UpcomingInstallmentResponse toUpcomingInstallmentResponse(
                        Payment payment) {

                return UpcomingInstallmentResponse.builder()

                                .installmentNumber(
                                                payment.getInstallmentNumber())

                                .dueDate(
                                                payment.getDueDate())

                                .amount(
                                                payment.getAmountDecimal())

                                .principal(
                                                payment.getPrincipalComponentDecimal())

                                .interest(
                                                payment.getInterestComponentDecimal())

                                .status(
                                                payment.getStatus() == null
                                                                ? "PENDING"
                                                                : payment
                                                                                .getStatus()
                                                                                .name())

                                .build();
        }

        // ================================================================
        // SAFE MONEY
        // ================================================================

        private BigDecimal safe(
                        BigDecimal value) {

                if (value == null) {
                        return ZERO;
                }

                if (value.compareTo(
                                ZERO) < 0) {
                        return ZERO;
                }

                return money(value);
        }

        // ================================================================
        // MONEY
        // ================================================================

        private BigDecimal money(
                        BigDecimal value) {

                if (value == null) {
                        return ZERO;
                }

                return value.setScale(
                                2,
                                RoundingMode.HALF_UP);
        }
}
