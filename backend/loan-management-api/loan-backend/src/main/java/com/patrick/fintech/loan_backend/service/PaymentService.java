package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.event.PaymentEventPublisher;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.AuditLogRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.PaymentRepository;
import com.patrick.fintech.loan_backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

        private final PaymentRepository paymentRepo;
        private final LoanRepository loanRepo;
        private final AuditLogRepository auditRepo;
        private final AuditService auditService;
        private final UserRepository userRepo;
        private final NotificationService notifService;
        private final MailService mailService;
        private final SmsService smsService;
        private final WebhookService webhookService;
        private final AccountingService accountingService;
        private final PaymentEventPublisher paymentEventPublisher;

        // ================================================================
        // PLATFORM FINANCIAL RULES
        // ================================================================

        /**
         * Monthly loan interest.
         */
        private static final BigDecimal MONTHLY_INTEREST_RATE = new BigDecimal("0.05");

        /**
         * Monthly loan management fee.
         */
        private static final BigDecimal MONTHLY_MANAGEMENT_FEE_RATE = new BigDecimal("0.05");

        /**
         * One-time processing fee.
         *
         * This is NOT charged by PaymentService.
         * It is deducted at disbursement.
         */
        private static final BigDecimal PROCESSING_FEE_RATE = new BigDecimal("0.02");

        /**
         * Monthly late-payment penalty.
         *
         * 15% per month.
         */
        private static final BigDecimal MONTHLY_PENALTY_RATE = new BigDecimal("0.15");

        /**
         * 30-day financial month.
         */
        private static final BigDecimal THIRTY = new BigDecimal("30");

        private static final BigDecimal TWELVE = new BigDecimal("12");

        private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

        private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(
                        2,
                        RoundingMode.HALF_UP);

        private static final BigDecimal ONE_CENT = new BigDecimal("0.01");

        private static final BigDecimal MIN_LOAN_AMOUNT = new BigDecimal("500000.00");

        private static final int MAX_LOAN_DURATION_MONTHS = 6;

        private static final String BORROWER_REFUNDS_PAYABLE_ACCOUNT = "2100";

        // ================================================================
        // RECORD PAYMENT
        // ================================================================

        @Transactional
        public Payment recordPayment(
                        Long loanId,
                        BigDecimal amount,
                        String method,
                        String txnId,
                        String channel,
                        String notes,
                        User recordedBy) {

                if (loanId == null) {
                        throw new IllegalArgumentException(
                                        "Loan ID is required");
                }

                if (amount == null
                                || amount.compareTo(BigDecimal.ZERO) <= 0) {

                        throw new IllegalArgumentException(
                                        "Payment amount must be greater than zero");
                }

                amount = roundMoney(amount);

                String normalizedTxnId = normalizeTransactionId(txnId);

                // ============================================================
                // LOCK LOAN
                // ============================================================

                Loan loan = loanRepo.findByIdForUpdate(loanId)
                                .orElseThrow(
                                                () -> new RuntimeException(
                                                                "Loan not found: " + loanId));

                validateOrganizationAccess(
                                loan,
                                recordedBy);

                if (loan.getOrganization() == null
                                || loan.getOrganization().getId() == null) {

                        throw new IllegalStateException(
                                        "Loan organization is required.");
                }

                Long organizationId = loan.getOrganization().getId();

                // ============================================================
                // BASIC LOAN VALIDATION
                // ============================================================

                BigDecimal grossPrincipal = roundMoney(
                                safe(
                                                loan.getAmountDecimal()));

                if (grossPrincipal.compareTo(
                                MIN_LOAN_AMOUNT) < 0) {

                        throw new IllegalStateException(
                                        "Loan principal is below the platform minimum of "
                                                        + MIN_LOAN_AMOUNT);
                }

                Integer duration = loan.getDurationMonths();

                if (duration == null
                                || duration < 1
                                || duration > MAX_LOAN_DURATION_MONTHS) {

                        throw new IllegalStateException(
                                        "Loan duration must be between 1 and "
                                                        + MAX_LOAN_DURATION_MONTHS
                                                        + " months");
                }

                // ============================================================
                // IDEMPOTENCY
                // ============================================================

                if (normalizedTxnId != null) {

                        Optional<Payment> existingPayment = paymentRepo
                                        .findByOrganization_IdAndTransactionId(
                                                        organizationId,
                                                        normalizedTxnId);

                        if (existingPayment.isPresent()) {

                                Payment existing = existingPayment.get();

                                if (existing.getLoan() != null
                                                && existing.getLoan().getId() != null
                                                && existing.getLoan()
                                                                .getId()
                                                                .equals(loanId)) {

                                        log.info(
                                                        "Duplicate payment transaction detected. " +
                                                                        "transactionId={}, loanId={}, paymentId={}",
                                                        normalizedTxnId,
                                                        loanId,
                                                        existing.getId());

                                        return existing;
                                }

                                throw new IllegalStateException(
                                                "Transaction ID "
                                                                + normalizedTxnId
                                                                + " has already been used for another loan.");
                        }
                }

                // ============================================================
                // LOAN STATUS
                // ============================================================

                if (loan.getStatus() != LoanStatus.ACTIVE
                                && loan.getStatus() != LoanStatus.OVERDUE) {

                        throw new IllegalStateException(
                                        "Loan is not active. Current status: "
                                                        + loan.getStatus());
                }

                LocalDate today = LocalDate.now();

                LocalDateTime now = LocalDateTime.now();

                // ============================================================
                // PAYMENT HISTORY
                // ============================================================

                List<Payment> loanPayments = paymentRepo.findByLoanId(loanId);

                if (loanPayments == null) {
                        loanPayments = List.of();
                }

                // ============================================================
                // INTEREST / MANAGEMENT FEE STATE
                // ============================================================

                LocalDateTime previousInterestCalculationDate = findLatestInterestCalculationTimestamp(
                                loanPayments);

                boolean firstInterestCalculation = previousInterestCalculationDate == null;

                log.info(
                                "Payment calculation state. " +
                                                "loanId={}, firstInterestCalculation={}, " +
                                                "previousInterestCalculationDate={}, " +
                                                "disbursedAt={}, disbursedAtTimestamp={}, " +
                                                "paymentTimestamp={}",
                                loanId,
                                firstInterestCalculation,
                                previousInterestCalculationDate,
                                loan.getDisbursedAt(),
                                loan.getDisbursedAtTimestamp(),
                                now);

                // ============================================================
                // FIND CURRENT PARTIALLY PAID INSTALLMENT
                // ============================================================

                Optional<Payment> existingCurrentCycle = loanPayments.stream()
                                .filter(p -> p != null)
                                .filter(
                                                p -> !Boolean.TRUE.equals(
                                                                p.getPaid()))
                                .filter(
                                                p -> safe(
                                                                p.getAmountPaidDecimal()).compareTo(
                                                                                BigDecimal.ZERO) > 0)
                                .min(
                                                Comparator.comparing(
                                                                Payment::getDueDate,
                                                                Comparator.nullsLast(
                                                                                Comparator.naturalOrder())));

                // ============================================================
                // FIND OLDEST UNPAID INSTALLMENT
                // ============================================================

                Optional<Payment> unpaidInstallment = loanPayments.stream()
                                .filter(p -> p != null)
                                .filter(
                                                p -> !Boolean.TRUE.equals(
                                                                p.getPaid()))
                                .min(
                                                Comparator.comparing(
                                                                Payment::getDueDate,
                                                                Comparator.nullsLast(
                                                                                Comparator.naturalOrder())));

                Payment installment;

                // ============================================================
                // SELECT INSTALLMENT
                // ============================================================

                if (existingCurrentCycle.isPresent()) {

                        installment = existingCurrentCycle.get();

                        log.info(
                                        "Continuing partially paid installment. " +
                                                        "loanId={}, installment={}, paymentId={}",
                                        loanId,
                                        installment.getInstallmentNumber(),
                                        installment.getId());

                } else if (unpaidInstallment.isPresent()) {

                        installment = unpaidInstallment.get();

                        log.info(
                                        "Using oldest unpaid installment. " +
                                                        "loanId={}, installment={}, paymentId={}",
                                        loanId,
                                        installment.getInstallmentNumber(),
                                        installment.getId());

                } else {

                        LocalDate dueDate = loan.getNextDueDate() != null
                                        ? loan.getNextDueDate()
                                        : today;

                        int nextNumber = loanPayments.stream()
                                        .filter(p -> p != null)
                                        .map(Payment::getInstallmentNumber)
                                        .filter(n -> n != null)
                                        .max(Integer::compareTo)
                                        .orElse(0)
                                        + 1;

                        installment = Payment.builder()
                                        .loan(loan)
                                        .organization(
                                                        loan.getOrganization())
                                        .installmentNumber(
                                                        nextNumber)
                                        .dueDate(
                                                        dueDate)
                                        .amount(
                                                        safe(
                                                                        loan
                                                                                        .getNextInstallmentAmountDecimal()))
                                        .principalComponent(ZERO)
                                        .interestComponent(ZERO)
                                        .managementFeeComponent(ZERO)
                                        .amountPaid(ZERO)
                                        .scheduledInterest(ZERO)
                                        .scheduledManagementFee(ZERO)
                                        .cycleInterestDue(ZERO)
                                        .cycleInterestRemaining(ZERO)
                                        .cycleManagementFeeDue(ZERO)
                                        .cycleManagementFeeRemaining(ZERO)
                                        .interestCalculationDate(null)
                                        .penalty(ZERO)
                                        .penaltyPaid(ZERO)
                                        .outstandingAfter(
                                                        safe(
                                                                        loan
                                                                                        .getOutstandingBalanceDecimal()))
                                        .paid(false)
                                        .status(
                                                        Payment.PaymentStatus.PENDING)
                                        .build();

                        log.info(
                                        "Creating new payment cycle. " +
                                                        "loanId={}, installment={}",
                                        loanId,
                                        nextNumber);
                }

                // ============================================================
                // CYCLE DUE DATE
                // ============================================================

                LocalDate cycleDueDate = installment.getDueDate() != null
                                ? installment.getDueDate()
                                : (loan.getNextDueDate() != null
                                                ? loan.getNextDueDate()
                                                : today);

                // ============================================================
                // OVERDUE DAYS
                // ============================================================

                long daysLateLong = ChronoUnit.DAYS.between(
                                cycleDueDate,
                                today);

                int daysLate = (int) Math.max(
                                0L,
                                daysLateLong);

                boolean isLate = daysLate > 0;

                // ============================================================
                // EXISTING PAYMENT AMOUNTS
                // ============================================================

                BigDecimal amountPaidSoFar = roundMoney(
                                safe(
                                                installment.getAmountPaidDecimal()));

                // ============================================================
                // EXISTING INTEREST CYCLE
                // ============================================================

                BigDecimal existingCycleInterestDue = roundMoney(
                                safe(
                                                installment
                                                                .getCycleInterestDueDecimal()))
                                .max(ZERO);

                BigDecimal existingCycleInterestRemaining = roundMoney(
                                safe(
                                                installment
                                                                .getCycleInterestRemainingDecimal()))
                                .max(ZERO);

                BigDecimal interestAlreadyPaidThisCycle = roundMoney(
                                existingCycleInterestDue
                                                .subtract(
                                                                existingCycleInterestRemaining)
                                                .max(ZERO));

                // ============================================================
                // EXISTING MANAGEMENT FEE CYCLE
                // ============================================================

                BigDecimal existingCycleManagementFeeDue = roundMoney(
                                safe(
                                                installment
                                                                .getCycleManagementFeeDueDecimal()))
                                .max(ZERO);

                BigDecimal existingCycleManagementFeeRemaining = roundMoney(
                                safe(
                                                installment
                                                                .getCycleManagementFeeRemainingDecimal()))
                                .max(ZERO);

                BigDecimal managementFeeAlreadyPaidThisCycle = roundMoney(
                                existingCycleManagementFeeDue
                                                .subtract(
                                                                existingCycleManagementFeeRemaining)
                                                .max(ZERO));

                // ============================================================
                // PENALTY STATE
                // ============================================================

                BigDecimal existingPenaltyAssessed = roundMoney(
                                safe(
                                                installment.getPenaltyDecimal()))
                                .max(ZERO);

                BigDecimal penaltyAlreadyPaid = roundMoney(
                                safe(
                                                installment.getPenaltyPaidDecimal()))
                                .max(ZERO);

                if (penaltyAlreadyPaid.compareTo(
                                existingPenaltyAssessed) > 0) {

                        penaltyAlreadyPaid = existingPenaltyAssessed;
                }

                // ============================================================
                // CURRENT GROSS PRINCIPAL
                // ============================================================

                /*
                 * The processing fee is NOT subtracted from outstanding
                 * principal.
                 *
                 * Example:
                 *
                 * Gross loan = 1,000,000
                 * Processing fee = 20,000
                 * Cash received = 980,000
                 *
                 * Repayment/interest/management calculations still use
                 * 1,000,000 as the initial loan principal.
                 */
                BigDecimal currentBalance = roundMoney(
                                safe(
                                                loan.getOutstandingBalanceDecimal()))
                                .max(ZERO);

                // ============================================================
                // DAILY INTEREST / MANAGEMENT RATES
                // ============================================================

                BigDecimal dailyInterestRate = calculateDailyInterestRate();

                BigDecimal dailyManagementFeeRate = calculateDailyManagementFeeRate();

                // ============================================================
                // INTEREST START TIMESTAMP
                // ============================================================

                LocalDateTime interestStartDateTime;

                if (previousInterestCalculationDate != null) {

                        interestStartDateTime = previousInterestCalculationDate;

                } else if (loan.getDisbursedAt() != null) {

                        interestStartDateTime = loan.getDisbursedAt();

                } else if (loan.getDisbursedAtTimestamp() != null) {

                        interestStartDateTime = loan.getDisbursedAtTimestamp();

                } else if (loan.getStartDate() != null) {

                        interestStartDateTime = loan.getStartDate()
                                        .atStartOfDay();

                } else {

                        interestStartDateTime = now;
                }

                if (interestStartDateTime.isAfter(now)) {

                        interestStartDateTime = now;
                }

                // ============================================================
                // ACTUAL ELAPSED CALENDAR DAYS
                // ============================================================

                long elapsedDays = calculateActualInterestDays(
                                interestStartDateTime,
                                now,
                                firstInterestCalculation,
                                loanId);

                // ============================================================
                // NEW INTEREST
                // ============================================================

                BigDecimal newlyAccruedInterest = calculateNewInterest(
                                currentBalance,
                                dailyInterestRate,
                                elapsedDays);

                // ============================================================
                // NEW MANAGEMENT FEE
                // ============================================================

                BigDecimal newlyAccruedManagementFee = calculateNewManagementFee(
                                currentBalance,
                                dailyManagementFeeRate,
                                elapsedDays);

                // ============================================================
                // TOTAL CURRENT CYCLE INTEREST
                // ============================================================

                BigDecimal totalCycleInterestDue = roundMoney(
                                existingCycleInterestDue
                                                .add(
                                                                newlyAccruedInterest));

                BigDecimal minimumInterestObligation = roundMoney(
                                interestAlreadyPaidThisCycle
                                                .add(
                                                                existingCycleInterestRemaining));

                if (minimumInterestObligation.compareTo(
                                totalCycleInterestDue) > 0) {

                        totalCycleInterestDue = minimumInterestObligation;
                }

                // ============================================================
                // TOTAL CURRENT CYCLE MANAGEMENT FEE
                // ============================================================

                BigDecimal totalCycleManagementFeeDue = roundMoney(
                                existingCycleManagementFeeDue
                                                .add(
                                                                newlyAccruedManagementFee));

                BigDecimal minimumManagementFeeObligation = roundMoney(
                                managementFeeAlreadyPaidThisCycle
                                                .add(
                                                                existingCycleManagementFeeRemaining));

                if (minimumManagementFeeObligation.compareTo(
                                totalCycleManagementFeeDue) > 0) {

                        totalCycleManagementFeeDue = minimumManagementFeeObligation;
                }

                // ============================================================
                // INTEREST REMAINING
                // ============================================================

                BigDecimal remainingInterestBeforePayment = roundMoney(
                                totalCycleInterestDue
                                                .subtract(
                                                                interestAlreadyPaidThisCycle)
                                                .max(ZERO));

                if (existingCycleInterestRemaining.compareTo(
                                remainingInterestBeforePayment) > 0) {

                        remainingInterestBeforePayment = existingCycleInterestRemaining;
                }

                remainingInterestBeforePayment = roundMoney(
                                remainingInterestBeforePayment);

                // ============================================================
                // MANAGEMENT FEE REMAINING
                // ============================================================

                BigDecimal remainingManagementFeeBeforePayment = roundMoney(
                                totalCycleManagementFeeDue
                                                .subtract(
                                                                managementFeeAlreadyPaidThisCycle)
                                                .max(ZERO));

                if (existingCycleManagementFeeRemaining.compareTo(
                                remainingManagementFeeBeforePayment) > 0) {

                        remainingManagementFeeBeforePayment = existingCycleManagementFeeRemaining;
                }

                remainingManagementFeeBeforePayment = roundMoney(
                                remainingManagementFeeBeforePayment);

                // ============================================================
                // PENALTY RATE
                // ============================================================

                /*
                 * 15% monthly / 30 days
                 *
                 * = 0.5% per overdue day
                 */
                BigDecimal dailyPenaltyRate = MONTHLY_PENALTY_RATE
                                .divide(
                                                THIRTY,
                                                16,
                                                RoundingMode.HALF_UP);

                // ============================================================
                // NEW PENALTY DAYS
                // ============================================================

                int existingDaysLate = installment.getDaysLate() != null
                                ? Math.max(
                                                0,
                                                installment.getDaysLate())
                                : 0;

                int newPenaltyDays = Math.max(
                                0,
                                daysLate - existingDaysLate);

                // ============================================================
                // NEW PENALTY
                // ============================================================

                BigDecimal newlyCalculatedPenalty = ZERO;

                if (newPenaltyDays > 0
                                && currentBalance.compareTo(
                                                ZERO) > 0) {

                        newlyCalculatedPenalty = roundMoney(
                                        currentBalance
                                                        .multiply(
                                                                        dailyPenaltyRate)
                                                        .multiply(
                                                                        BigDecimal.valueOf(
                                                                                        newPenaltyDays)));
                }

                // ============================================================
                // TOTAL PENALTY
                // ============================================================

                BigDecimal totalPenalty = roundMoney(
                                existingPenaltyAssessed
                                                .add(
                                                                newlyCalculatedPenalty));

                // ============================================================
                // PENALTY REMAINING
                // ============================================================

                BigDecimal penaltyRemainingBeforePayment = roundMoney(
                                totalPenalty
                                                .subtract(
                                                                penaltyAlreadyPaid)
                                                .max(ZERO));

                // ============================================================
                // PAYMENT ALLOCATION
                // ============================================================

                BigDecimal paymentRemaining = amount;

                // ============================================================
                // 1. PENALTY
                // ============================================================

                BigDecimal penaltyPaidThisPayment = roundMoney(
                                paymentRemaining.min(
                                                penaltyRemainingBeforePayment));

                paymentRemaining = roundMoney(
                                paymentRemaining
                                                .subtract(
                                                                penaltyPaidThisPayment)
                                                .max(ZERO));

                BigDecimal totalPenaltyPaid = roundMoney(
                                penaltyAlreadyPaid
                                                .add(
                                                                penaltyPaidThisPayment));

                totalPenaltyPaid = totalPenaltyPaid.min(
                                totalPenalty);

                BigDecimal remainingPenaltyAfterPayment = roundMoney(
                                totalPenalty
                                                .subtract(
                                                                totalPenaltyPaid)
                                                .max(ZERO));

                // ============================================================
                // 2. INTEREST
                // ============================================================

                BigDecimal interestPaidThisPayment = roundMoney(
                                paymentRemaining.min(
                                                remainingInterestBeforePayment));

                paymentRemaining = roundMoney(
                                paymentRemaining
                                                .subtract(
                                                                interestPaidThisPayment)
                                                .max(ZERO));

                // ============================================================
                // 3. MANAGEMENT FEE
                // ============================================================

                BigDecimal managementFeePaidThisPayment = roundMoney(
                                paymentRemaining.min(
                                                remainingManagementFeeBeforePayment));

                paymentRemaining = roundMoney(
                                paymentRemaining
                                                .subtract(
                                                                managementFeePaidThisPayment)
                                                .max(ZERO));

                // ============================================================
                // 4. PRINCIPAL
                // ============================================================

                BigDecimal principalPaidThisPayment = roundMoney(
                                paymentRemaining.min(
                                                currentBalance));

                paymentRemaining = roundMoney(
                                paymentRemaining
                                                .subtract(
                                                                principalPaidThisPayment)
                                                .max(ZERO));

                // ============================================================
                // 5. OVERPAYMENT
                // ============================================================

                BigDecimal overpayment = roundMoney(
                                paymentRemaining.max(ZERO));

                // ============================================================
                // NEW PRINCIPAL BALANCE
                // ============================================================

                BigDecimal newBalance = roundMoney(
                                currentBalance
                                                .subtract(
                                                                principalPaidThisPayment)
                                                .max(ZERO));

                // ============================================================
                // CUMULATIVE PAYMENT COMPONENTS
                // ============================================================

                BigDecimal existingPrincipalPaid = roundMoney(
                                safe(
                                                installment
                                                                .getPrincipalComponentDecimal()))
                                .max(ZERO);

                BigDecimal totalPrincipalPaid = roundMoney(
                                existingPrincipalPaid
                                                .add(
                                                                principalPaidThisPayment));

                BigDecimal existingInterestComponent = roundMoney(
                                safe(
                                                installment
                                                                .getInterestComponentDecimal()))
                                .max(ZERO);

                BigDecimal totalInterestPaid = roundMoney(
                                existingInterestComponent
                                                .add(
                                                                interestPaidThisPayment));

                BigDecimal existingManagementFeeComponent = roundMoney(
                                safe(
                                                installment
                                                                .getManagementFeeComponentDecimal()))
                                .max(ZERO);

                BigDecimal totalManagementFeePaid = roundMoney(
                                existingManagementFeeComponent
                                                .add(
                                                                managementFeePaidThisPayment));

                // ============================================================
                // REMAINING INTEREST
                // ============================================================

                BigDecimal remainingInterestAfterPayment = roundMoney(
                                totalCycleInterestDue
                                                .subtract(
                                                                interestAlreadyPaidThisCycle
                                                                                .add(
                                                                                                interestPaidThisPayment))
                                                .max(ZERO));

                // ============================================================
                // REMAINING MANAGEMENT FEE
                // ============================================================

                BigDecimal remainingManagementFeeAfterPayment = roundMoney(
                                totalCycleManagementFeeDue
                                                .subtract(
                                                                managementFeeAlreadyPaidThisCycle
                                                                                .add(
                                                                                                managementFeePaidThisPayment))
                                                .max(ZERO));

                // ============================================================
                // COMPLETION
                // ============================================================

                boolean penaltyCovered = remainingPenaltyAfterPayment
                                .compareTo(
                                                ONE_CENT) <= 0;

                boolean interestCovered = remainingInterestAfterPayment
                                .compareTo(
                                                ONE_CENT) <= 0;

                boolean managementFeeCovered = remainingManagementFeeAfterPayment
                                .compareTo(
                                                ONE_CENT) <= 0;

                boolean principalCovered = newBalance.compareTo(
                                ONE_CENT) <= 0;

                boolean scheduledAmountCovered = isScheduledInstallmentCovered(
                                installment,
                                amountPaidSoFar,
                                amount);

                boolean cycleCompleted;

                if (principalCovered
                                && interestCovered
                                && managementFeeCovered
                                && penaltyCovered) {

                        cycleCompleted = true;

                } else {

                        cycleCompleted = scheduledAmountCovered
                                        && interestCovered
                                        && managementFeeCovered
                                        && penaltyCovered;
                }

                if (principalCovered
                                && (!interestCovered
                                                || !managementFeeCovered
                                                || !penaltyCovered)) {

                        cycleCompleted = false;
                }

                // ============================================================
                // PAYMENT TOTAL VALIDATION
                // ============================================================

                BigDecimal allocated = penaltyPaidThisPayment
                                .add(
                                                interestPaidThisPayment)
                                .add(
                                                managementFeePaidThisPayment)
                                .add(
                                                principalPaidThisPayment)
                                .add(
                                                overpayment);

                allocated = roundMoney(allocated);

                if (allocated.compareTo(amount) != 0) {

                        throw new IllegalStateException(
                                        "Payment allocation mismatch. " +
                                                        "payment=" + amount +
                                                        ", allocated=" + allocated);
                }

                // ============================================================
                // UPDATE PAYMENT ROW
                // ============================================================

                BigDecimal newAmountPaid = roundMoney(
                                amountPaidSoFar
                                                .add(
                                                                amount));

                installment.setAmountPaid(
                                newAmountPaid);

                installment.setInterestComponent(
                                totalInterestPaid);

                installment.setManagementFeeComponent(
                                totalManagementFeePaid);

                installment.setPrincipalComponent(
                                totalPrincipalPaid);

                installment.setPenalty(
                                totalPenalty);

                installment.setPenaltyPaid(
                                totalPenaltyPaid);

                installment.setOutstandingAfter(
                                newBalance);

                installment.setCycleInterestDue(
                                totalCycleInterestDue);

                installment.setCycleInterestRemaining(
                                remainingInterestAfterPayment);

                installment.setCycleManagementFeeDue(
                                totalCycleManagementFeeDue);

                installment.setCycleManagementFeeRemaining(
                                remainingManagementFeeAfterPayment);

                installment.setLate(
                                isLate || installment.isLate());

                installment.setDaysLate(
                                Math.max(
                                                existingDaysLate,
                                                daysLate));

                installment.setPaymentMethod(
                                method);

                installment.setTransactionId(
                                normalizedTxnId);

                installment.setChannel(
                                channel);

                installment.setNotes(
                                notes);

                if (recordedBy != null) {
                        installment.setRecordedBy(
                                        recordedBy);
                }

                installment.setPaidDate(
                                today);

                /*
                 * This is the authoritative timestamp used to prevent
                 * duplicate same-day interest/management fee charging.
                 */
                installment.setInterestCalculationDate(
                                now);

                installment.setPaid(
                                cycleCompleted);

                installment.setStatus(
                                cycleCompleted
                                                ? Payment.PaymentStatus.COMPLETED
                                                : Payment.PaymentStatus.PARTIALLY_PAID);

                if (installment.getPaymentReference() == null
                                || installment.getPaymentReference().isBlank()) {

                        installment.setPaymentReference(
                                        generateRef(loan));
                }

                // ============================================================
                // SAVE PAYMENT
                // ============================================================

                try {

                        installment = paymentRepo.save(
                                        installment);

                } catch (DataIntegrityViolationException e) {

                        if (normalizedTxnId != null) {

                                Optional<Payment> concurrentPayment = paymentRepo
                                                .findByOrganization_IdAndTransactionId(
                                                                organizationId,
                                                                normalizedTxnId);

                                if (concurrentPayment.isPresent()) {

                                        Payment existing = concurrentPayment.get();

                                        if (existing.getLoan() != null
                                                        && existing.getLoan().getId() != null
                                                        && existing.getLoan()
                                                                        .getId()
                                                                        .equals(loanId)) {

                                                return existing;
                                        }
                                }
                        }

                        throw e;
                }

                // ============================================================
                // UPDATE LOAN TOTALS
                // ============================================================

                BigDecimal oldTotalPaid = roundMoney(
                                safe(
                                                loan.getTotalPaidDecimal()));

                BigDecimal newTotalPaid = roundMoney(
                                oldTotalPaid
                                                .add(
                                                                amount));

                loan.setTotalPaid(
                                newTotalPaid);

                loan.setOutstandingBalance(
                                newBalance);

                loan.setLastPaymentDate(
                                today);

                // ============================================================
                // UPDATE LOAN INTEREST PAID
                // ============================================================

                BigDecimal oldLoanInterestPaid = roundMoney(
                                safe(
                                                loan.getInterestPaidDecimal()));

                BigDecimal newLoanInterestPaid = roundMoney(
                                oldLoanInterestPaid
                                                .add(
                                                                interestPaidThisPayment));

                loan.setInterestPaid(
                                newLoanInterestPaid);

                // ============================================================
                // UPDATE LOAN MANAGEMENT FEE PAID
                // ============================================================

                BigDecimal oldLoanManagementFeePaid = roundMoney(
                                safe(
                                                loan.getManagementFeePaidDecimal()));

                BigDecimal newLoanManagementFeePaid = roundMoney(
                                oldLoanManagementFeePaid
                                                .add(
                                                                managementFeePaidThisPayment));

                loan.setManagementFeePaid(
                                newLoanManagementFeePaid);

                // ============================================================
                // UPDATE LOAN DAYS OVERDUE
                // ============================================================

                if (daysLate > 0) {

                        int existingLoanDaysOverdue = loan.getDaysOverdue() != null
                                        ? loan.getDaysOverdue()
                                        : 0;

                        loan.setDaysOverdue(
                                        Math.max(
                                                        existingLoanDaysOverdue,
                                                        daysLate));
                }

                // ============================================================
                // FULLY PAID LOAN
                // ============================================================

                if (principalCovered
                                && interestCovered
                                && managementFeeCovered
                                && penaltyCovered) {

                        loan.setStatus(
                                        LoanStatus.PAID);

                        Long currentInstallmentId = installment.getId();

                        List<Payment> stillPending = paymentRepo
                                        .findByLoanId(
                                                        loanId)
                                        .stream()
                                        .filter(
                                                        p -> p != null)
                                        .filter(
                                                        p -> !Boolean.TRUE.equals(
                                                                        p.getPaid()))
                                        .filter(
                                                        p -> p.getId() == null
                                                                        || !p.getId()
                                                                                        .equals(
                                                                                                        currentInstallmentId))
                                        .toList();

                        if (!stillPending.isEmpty()) {

                                paymentRepo.deleteAll(
                                                stillPending);
                        }

                        loan.setNextDueDate(
                                        null);

                        loan.setNextPaymentDate(
                                        null);

                        loan.setNextInstallmentAmount(
                                        ZERO);

                } else {

                        loan.setStatus(
                                        isLate
                                                        ? LoanStatus.OVERDUE
                                                        : LoanStatus.ACTIVE);

                        if (cycleCompleted) {

                                LocalDate nextDue = cycleDueDate.plusMonths(
                                                1);

                                loan.setNextDueDate(
                                                nextDue);

                                loan.setNextPaymentDate(
                                                nextDue);

                        } else {

                                loan.setNextDueDate(
                                                cycleDueDate);

                                loan.setNextPaymentDate(
                                                cycleDueDate);
                        }
                }

                loanRepo.save(
                                loan);

                // ============================================================
                // ACCOUNTING
                // ============================================================

                accountingService.postPaymentReceived(
                                installment,
                                amount,
                                principalPaidThisPayment,
                                interestPaidThisPayment,
                                managementFeePaidThisPayment,
                                penaltyPaidThisPayment,
                                overpayment);

                // ============================================================
                // AUDIT
                // ============================================================

                audit(
                                loan.getOrganization(),
                                recordedBy,
                                "PAYMENT_RECORDED",
                                "PAYMENT",
                                installment.getId() != null
                                                ? installment.getId().toString()
                                                : "UNKNOWN",
                                "Payment of "
                                                + amount
                                                + " on loan "
                                                + loan.getReferenceNumber()
                                                + " — interest days="
                                                + elapsedDays
                                                + ", interest="
                                                + interestPaidThisPayment
                                                + ", management fee="
                                                + managementFeePaidThisPayment
                                                + ", principal="
                                                + principalPaidThisPayment
                                                + ", penalty="
                                                + penaltyPaidThisPayment
                                                + ", overpayment="
                                                + overpayment
                                                + ", monthly interest rate=5%"
                                                + ", monthly management fee rate=5%"
                                                + ", monthly penalty rate=15%"
                                                + ", daily penalty rate=0.5%");

                // ============================================================
                // EVENT
                // ============================================================

                try {

                        paymentEventPublisher.publishPaymentReceived(
                                        loan,
                                        installment,
                                        amount,
                                        principalPaidThisPayment,
                                        interestPaidThisPayment,
                                        penaltyPaidThisPayment,
                                        newBalance,
                                        now);

                } catch (Exception e) {

                        log.error(
                                        "Failed to publish PaymentReceivedEvent. " +
                                                        "loanId={}, paymentId={}, transactionId={}",
                                        loan.getId(),
                                        installment.getId(),
                                        normalizedTxnId,
                                        e);

                        throw e;
                }

                // ============================================================
                // EMAIL
                // ============================================================

                try {

                        mailService.sendPaymentConfirmation(
                                        loan,
                                        amount.doubleValue());

                } catch (Exception e) {

                        log.warn(
                                        "Payment email notification failed for loanId={}",
                                        loan.getId(),
                                        e);
                }

                // ============================================================
                // SMS
                // ============================================================

                try {

                        smsService.sendPaymentConfirmed(
                                        loan,
                                        amount.doubleValue());

                } catch (Exception e) {

                        log.warn(
                                        "Payment SMS notification failed for loanId={}",
                                        loan.getId(),
                                        e);
                }

                // ============================================================
                // LOAN OFFICER NOTIFICATION
                // ============================================================

                if (loan.getLoanOfficer() != null
                                && (recordedBy == null
                                                || loan.getLoanOfficer().getId() == null
                                                || recordedBy.getId() == null
                                                || !loan.getLoanOfficer()
                                                                .getId()
                                                                .equals(
                                                                                recordedBy.getId()))) {

                        try {

                                notifService.notifyUsers(
                                                List.of(
                                                                loan.getLoanOfficer()),
                                                "Payment Received",
                                                "A payment of "
                                                                + loan.getCurrency()
                                                                + " "
                                                                + amount
                                                                + " was recorded on loan "
                                                                + loan.getReferenceNumber()
                                                                + (recordedBy != null
                                                                                ? " by "
                                                                                                + recordedBy.getName()
                                                                                : " automatically")
                                                                + ".",
                                                "success",
                                                "/dashboard/loans/"
                                                                + loan.getId());

                        } catch (Exception e) {

                                log.warn(
                                                "In-app payment notification failed for loanId={}",
                                                loan.getId(),
                                                e);
                        }
                }

                // ============================================================
                // WEBHOOK
                // ============================================================

                try {

                        Map<String, Object> paymentWebhook = new HashMap<>();

                        paymentWebhook.put(
                                        "paymentId",
                                        installment.getId());

                        paymentWebhook.put(
                                        "loanId",
                                        loan.getId());

                        paymentWebhook.put(
                                        "loanReference",
                                        loan.getReferenceNumber());

                        if (loan.getBorrower() != null) {

                                paymentWebhook.put(
                                                "borrowerId",
                                                loan.getBorrower().getId());
                        }

                        paymentWebhook.put(
                                        "organizationId",
                                        organizationId);

                        paymentWebhook.put(
                                        "amount",
                                        amount);

                        paymentWebhook.put(
                                        "principalPaid",
                                        principalPaidThisPayment);

                        paymentWebhook.put(
                                        "interestPaid",
                                        interestPaidThisPayment);

                        paymentWebhook.put(
                                        "managementFeePaid",
                                        managementFeePaidThisPayment);

                        paymentWebhook.put(
                                        "penaltyPaidThisPayment",
                                        penaltyPaidThisPayment);

                        paymentWebhook.put(
                                        "penaltyPaid",
                                        totalPenaltyPaid);

                        paymentWebhook.put(
                                        "totalPenalty",
                                        totalPenalty);

                        paymentWebhook.put(
                                        "remainingPenalty",
                                        remainingPenaltyAfterPayment);

                        paymentWebhook.put(
                                        "penaltyDays",
                                        daysLate);

                        paymentWebhook.put(
                                        "newPenaltyDays",
                                        newPenaltyDays);

                        paymentWebhook.put(
                                        "monthlyPenaltyRate",
                                        MONTHLY_PENALTY_RATE);

                        paymentWebhook.put(
                                        "dailyPenaltyRate",
                                        dailyPenaltyRate);

                        paymentWebhook.put(
                                        "interestDays",
                                        elapsedDays);

                        paymentWebhook.put(
                                        "dailyInterestRate",
                                        dailyInterestRate);

                        paymentWebhook.put(
                                        "dailyManagementFeeRate",
                                        dailyManagementFeeRate);

                        paymentWebhook.put(
                                        "newInterest",
                                        newlyAccruedInterest);

                        paymentWebhook.put(
                                        "newManagementFee",
                                        newlyAccruedManagementFee);

                        paymentWebhook.put(
                                        "totalInterestDue",
                                        totalCycleInterestDue);

                        paymentWebhook.put(
                                        "remainingInterest",
                                        remainingInterestAfterPayment);

                        paymentWebhook.put(
                                        "totalManagementFeeDue",
                                        totalCycleManagementFeeDue);

                        paymentWebhook.put(
                                        "remainingManagementFee",
                                        remainingManagementFeeAfterPayment);

                        paymentWebhook.put(
                                        "totalPrincipalPaid",
                                        totalPrincipalPaid);

                        paymentWebhook.put(
                                        "outstandingBalance",
                                        newBalance);

                        paymentWebhook.put(
                                        "overpayment",
                                        overpayment);

                        paymentWebhook.put(
                                        "borrowerRefundPayable",
                                        overpayment);

                        paymentWebhook.put(
                                        "borrowerRefundPayableAccount",
                                        BORROWER_REFUNDS_PAYABLE_ACCOUNT);

                        paymentWebhook.put(
                                        "processingFee",
                                        loan.getProcessingFeeDecimal());

                        paymentWebhook.put(
                                        "processingFeePaid",
                                        loan.getProcessingFeePaid());

                        paymentWebhook.put(
                                        "loanGrossPrincipal",
                                        loan.getAmountDecimal());

                        paymentWebhook.put(
                                        "loanDisbursedAmount",
                                        loan.getDisbursedAmountDecimal());

                        paymentWebhook.put(
                                        "paymentMethod",
                                        method);

                        paymentWebhook.put(
                                        "channel",
                                        channel);

                        paymentWebhook.put(
                                        "transactionId",
                                        normalizedTxnId);

                        paymentWebhook.put(
                                        "paymentReference",
                                        installment.getPaymentReference());

                        paymentWebhook.put(
                                        "paymentDate",
                                        today.toString());

                        paymentWebhook.put(
                                        "paymentTimestamp",
                                        now.toString());

                        paymentWebhook.put(
                                        "interestCalculationStart",
                                        interestStartDateTime.toString());

                        paymentWebhook.put(
                                        "interestCalculationDate",
                                        installment.getInterestCalculationDate() != null
                                                        ? installment
                                                                        .getInterestCalculationDate()
                                                                        .toString()
                                                        : null);

                        paymentWebhook.put(
                                        "installmentNumber",
                                        installment.getInstallmentNumber());

                        paymentWebhook.put(
                                        "paymentStatus",
                                        installment.getStatus() != null
                                                        ? installment.getStatus().name()
                                                        : null);

                        paymentWebhook.put(
                                        "loanStatus",
                                        loan.getStatus() != null
                                                        ? loan.getStatus().name()
                                                        : null);

                        webhookService.dispatch(
                                        loan.getOrganization(),
                                        "PAYMENT_MADE",
                                        paymentWebhook);

                } catch (Exception e) {

                        log.error(
                                        "[PAYMENT WEBHOOK] Failed to dispatch PAYMENT_MADE. " +
                                                        "loanId={}, paymentId={}",
                                        loan.getId(),
                                        installment.getId(),
                                        e);
                }

                // ============================================================
                // FINAL LOG
                // ============================================================

                log.info(
                                "Payment successfully recorded. " +
                                                "loanId={}, paymentId={}, amount={}, " +
                                                "interestDays={}, interest={}, " +
                                                "managementFee={}, principal={}, " +
                                                "penalty={}, overpayment={}, " +
                                                "newBalance={}, cycleCompleted={}, loanStatus={}",
                                loan.getId(),
                                installment.getId(),
                                amount,
                                elapsedDays,
                                interestPaidThisPayment,
                                managementFeePaidThisPayment,
                                principalPaidThisPayment,
                                penaltyPaidThisPayment,
                                overpayment,
                                newBalance,
                                cycleCompleted,
                                loan.getStatus());

                return installment;
        }

        // ================================================================
        // FIRST INTEREST CALCULATION
        // ================================================================

        public boolean isFirstInterestCalculation(
                        List<Payment> payments) {

                if (payments == null || payments.isEmpty()) {
                        return true;
                }

                for (Payment payment : payments) {

                        if (payment == null) {
                                continue;
                        }

                        if (payment.getInterestCalculationDate() != null) {
                                return false;
                        }
                }

                return true;
        }

        // ================================================================
        // INSTALLMENT COMPLETION
        // ================================================================

        private boolean isScheduledInstallmentCovered(
                        Payment installment,
                        BigDecimal amountPaidSoFar,
                        BigDecimal currentPayment) {

                if (installment == null) {
                        return false;
                }

                BigDecimal scheduledAmount = roundMoney(
                                safe(
                                                installment.getAmountDecimal()));

                if (scheduledAmount.compareTo(
                                BigDecimal.ZERO) <= 0) {

                        return false;
                }

                BigDecimal newPaidAmount = roundMoney(
                                safe(amountPaidSoFar)
                                                .add(
                                                                safe(currentPayment)));

                return newPaidAmount.compareTo(
                                scheduledAmount) >= 0;
        }

        // ================================================================
        // ORGANIZATION ACCESS
        // ================================================================

        private void validateOrganizationAccess(
                        Loan loan,
                        User recordedBy) {

                if (loan == null) {

                        throw new IllegalArgumentException(
                                        "Loan is required");
                }

                if (loan.getOrganization() == null
                                || loan.getOrganization().getId() == null) {

                        throw new IllegalStateException(
                                        "Loan organization is required.");
                }

                if (recordedBy == null) {
                        return;
                }

                if (recordedBy.getOrganization() == null
                                || recordedBy.getOrganization().getId() == null) {

                        throw new IllegalStateException(
                                        "Recorded user's organization is required.");
                }

                Long loanOrganizationId = loan.getOrganization().getId();

                Long userOrganizationId = recordedBy.getOrganization().getId();

                if (!loanOrganizationId.equals(
                                userOrganizationId)) {

                        throw new IllegalStateException(
                                        "Access denied.");
                }
        }

        // ================================================================
        // GET LOAN SCHEDULE
        // ================================================================

        @Transactional(readOnly = true)
        public List<Payment> getLoanSchedule(
                        Long loanId,
                        Long orgId) {

                if (loanId == null) {

                        throw new IllegalArgumentException(
                                        "Loan ID is required");
                }

                if (orgId == null) {

                        throw new IllegalArgumentException(
                                        "Organization ID is required");
                }

                Loan loan = loanRepo.findById(
                                loanId).orElseThrow(
                                                () -> new RuntimeException(
                                                                "Loan not found"));

                if (loan.getOrganization() == null
                                || loan.getOrganization().getId() == null
                                || !loan.getOrganization()
                                                .getId()
                                                .equals(orgId)) {

                        throw new IllegalStateException(
                                        "Access denied.");
                }

                return paymentRepo.findByLoanId(
                                loanId);
        }

        // ================================================================
        // MARK OVERDUE
        // ================================================================

        @Transactional
        public void markOverdueLoans(
                        Long orgId) {

                if (orgId == null) {

                        throw new IllegalArgumentException(
                                        "Organization ID is required");
                }

                LocalDate today = LocalDate.now();

                List<Payment> overduePayments = paymentRepo
                                .findByOrganization_IdAndPaidFalseAndDueDateBefore(
                                                orgId,
                                                today);

                if (overduePayments == null
                                || overduePayments.isEmpty()) {

                        return;
                }

                for (Payment payment : overduePayments) {

                        if (payment == null) {
                                continue;
                        }

                        Loan loan = payment.getLoan();

                        if (loan == null) {
                                continue;
                        }

                        if (loan.getOrganization() == null
                                        || loan.getOrganization().getId() == null
                                        || !orgId.equals(
                                                        loan.getOrganization().getId())) {

                                continue;
                        }

                        if (loan.getStatus() == LoanStatus.ACTIVE) {

                                loan.setStatus(
                                                LoanStatus.OVERDUE);
                        }

                        if (payment.getDueDate() != null) {

                                int days = Math.max(
                                                0,
                                                (int) ChronoUnit.DAYS.between(
                                                                payment.getDueDate(),
                                                                today));

                                int existingDays = payment.getDaysLate() != null
                                                ? payment.getDaysLate()
                                                : 0;

                                payment.setLate(
                                                true);

                                payment.setDaysLate(
                                                Math.max(
                                                                existingDays,
                                                                days));

                                loan.setDaysOverdue(
                                                Math.max(
                                                                loan.getDaysOverdue() != null
                                                                                ? loan.getDaysOverdue()
                                                                                : 0,
                                                                days));

                                paymentRepo.save(
                                                payment);
                        }

                        loanRepo.save(
                                        loan);
                }
        }

        // ================================================================
        // FIND LATEST INTEREST TIMESTAMP
        // ================================================================

        private LocalDateTime findLatestInterestCalculationTimestamp(
                        List<Payment> payments) {

                LocalDateTime latest = null;

                if (payments == null
                                || payments.isEmpty()) {

                        return null;
                }

                for (Payment payment : payments) {

                        if (payment == null) {
                                continue;
                        }

                        LocalDateTime timestamp = payment.getInterestCalculationDate();

                        if (timestamp == null) {
                                continue;
                        }

                        if (latest == null
                                        || timestamp.isAfter(latest)) {

                                latest = timestamp;
                        }
                }

                return latest;
        }

        // ================================================================
        // ACTUAL INTEREST DAYS
        // ================================================================

        private long calculateActualInterestDays(
                        LocalDateTime interestStart,
                        LocalDateTime now,
                        boolean firstInterestCalculation,
                        Long loanId) {

                if (now == null) {
                        return 0L;
                }

                /*
                 * First payment:
                 *
                 * 09 Aug 10:00
                 * 09 Aug 10:01
                 *
                 * = 1 financial interest day.
                 */
                if (firstInterestCalculation) {

                        log.info(
                                        "FIRST INTEREST/MANAGEMENT FEE CHARGE. " +
                                                        "loanId={}, interestStart={}, paymentTime={}, " +
                                                        "effectiveInterestDays=1",
                                        loanId,
                                        interestStart,
                                        now);

                        return 1L;
                }

                if (interestStart == null) {

                        log.warn(
                                        "Interest anchor is null for subsequent calculation. " +
                                                        "loanId={}",
                                        loanId);

                        return 0L;
                }

                if (interestStart.isAfter(now)) {

                        log.warn(
                                        "Interest anchor is after payment timestamp. " +
                                                        "loanId={}, interestStart={}, now={}",
                                        loanId,
                                        interestStart,
                                        now);

                        return 0L;
                }

                /*
                 * Calendar-day rule:
                 *
                 * 09 Aug 10:00 -> 09 Aug 10:05
                 * = 0
                 *
                 * 09 Aug -> 10 Aug
                 * = 1
                 */
                long calendarDays = ChronoUnit.DAYS.between(
                                interestStart.toLocalDate(),
                                now.toLocalDate());

                long effectiveDays = Math.max(
                                0L,
                                calendarDays);

                log.info(
                                "SUBSEQUENT INTEREST/MANAGEMENT FEE CALCULATION. " +
                                                "loanId={}, interestStart={}, paymentTime={}, " +
                                                "calendarDays={}, effectiveInterestDays={}",
                                loanId,
                                interestStart,
                                now,
                                calendarDays,
                                effectiveDays);

                return effectiveDays;
        }

        // ================================================================
        // DAILY INTEREST RATE
        // ================================================================

        private BigDecimal calculateDailyInterestRate() {

                /*
                 * 5% monthly / 30
                 */
                return MONTHLY_INTEREST_RATE
                                .divide(
                                                THIRTY,
                                                16,
                                                RoundingMode.HALF_UP);
        }

        // ================================================================
        // DAILY MANAGEMENT FEE RATE
        // ================================================================

        private BigDecimal calculateDailyManagementFeeRate() {

                /*
                 * 5% monthly / 30
                 */
                return MONTHLY_MANAGEMENT_FEE_RATE
                                .divide(
                                                THIRTY,
                                                16,
                                                RoundingMode.HALF_UP);
        }

        // ================================================================
        // CALCULATE NEW INTEREST
        // ================================================================

        private BigDecimal calculateNewInterest(
                        BigDecimal currentBalance,
                        BigDecimal dailyRate,
                        long elapsedDays) {

                if (currentBalance == null
                                || currentBalance.compareTo(
                                                BigDecimal.ZERO) <= 0) {

                        return ZERO;
                }

                if (dailyRate == null
                                || dailyRate.compareTo(
                                                BigDecimal.ZERO) <= 0) {

                        return ZERO;
                }

                if (elapsedDays <= 0) {
                        return ZERO;
                }

                return roundMoney(
                                currentBalance
                                                .multiply(
                                                                dailyRate)
                                                .multiply(
                                                                BigDecimal.valueOf(
                                                                                elapsedDays)));
        }

        // ================================================================
        // CALCULATE NEW MANAGEMENT FEE
        // ================================================================

        private BigDecimal calculateNewManagementFee(
                        BigDecimal currentBalance,
                        BigDecimal dailyRate,
                        long elapsedDays) {

                if (currentBalance == null
                                || currentBalance.compareTo(
                                                BigDecimal.ZERO) <= 0) {

                        return ZERO;
                }

                if (dailyRate == null
                                || dailyRate.compareTo(
                                                BigDecimal.ZERO) <= 0) {

                        return ZERO;
                }

                if (elapsedDays <= 0) {
                        return ZERO;
                }

                return roundMoney(
                                currentBalance
                                                .multiply(
                                                                dailyRate)
                                                .multiply(
                                                                BigDecimal.valueOf(
                                                                                elapsedDays)));
        }

        // ================================================================
        // SAFE BIGDECIMAL
        // ================================================================

        private BigDecimal safe(
                        BigDecimal value) {

                return value == null
                                ? ZERO
                                : value;
        }

        // ================================================================
        // ROUND MONEY
        // ================================================================

        private BigDecimal roundMoney(
                        BigDecimal value) {

                if (value == null) {
                        return ZERO;
                }

                return value.setScale(
                                2,
                                RoundingMode.HALF_UP);
        }

        // ================================================================
        // TRANSACTION ID
        // ================================================================

        private String normalizeTransactionId(
                        String txnId) {

                if (txnId == null) {
                        return null;
                }

                String normalized = txnId.trim();

                return normalized.isBlank()
                                ? null
                                : normalized;
        }

        // ================================================================
        // PAYMENT REFERENCE
        // ================================================================

        private String generateRef(
                        Loan loan) {

                String loanReference = loan != null
                                && loan.getReferenceNumber() != null
                                && !loan.getReferenceNumber().isBlank()
                                                ? loan.getReferenceNumber()
                                                : String.valueOf(
                                                                loan != null
                                                                                ? loan.getId()
                                                                                : "UNKNOWN");

                return "PAY-"
                                + loanReference
                                + "-"
                                + System.currentTimeMillis();
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
}