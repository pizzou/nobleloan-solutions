package com.patrick.fintech.loan_backend.service;

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

    private static final BigDecimal ZERO =
            BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private static final BigDecimal ONE_CENT =
            new BigDecimal("0.01");

    private static final BigDecimal THIRTY =
            new BigDecimal("30");

    private static final BigDecimal TWELVE =
            new BigDecimal("12");

    private static final BigDecimal ONE_HUNDRED =
            new BigDecimal("100");

    private static final BigDecimal DEFAULT_MONTHLY_PENALTY_RATE =
            new BigDecimal("0.02");

    private static final String BORROWER_REFUNDS_PAYABLE_ACCOUNT =
            "2100";

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
            User recordedBy
    ) {

        if (loanId == null) {
            throw new IllegalArgumentException("Loan ID is required");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Payment amount must be greater than zero"
            );
        }

        amount = roundMoney(amount);

        String normalizedTxnId =
                normalizeTransactionId(txnId);

        // ============================================================
        // LOCK LOAN
        // ============================================================

        Loan loan =
                loanRepo.findByIdForUpdate(loanId)
                        .orElseThrow(
                                () -> new RuntimeException(
                                        "Loan not found: " + loanId
                                )
                        );

        // ============================================================
        // ORGANIZATION
        // ============================================================

        validateOrganizationAccess(
                loan,
                recordedBy
        );

        if (loan.getOrganization() == null
                || loan.getOrganization().getId() == null) {

            throw new IllegalStateException(
                    "Loan organization is required."
            );
        }

        Long organizationId =
                loan.getOrganization().getId();

        // ============================================================
        // IDEMPOTENCY
        // ============================================================

        if (normalizedTxnId != null) {

            Optional<Payment> existingPayment =
                    paymentRepo.findByOrganization_IdAndTransactionId(
                            organizationId,
                            normalizedTxnId
                    );

            if (existingPayment.isPresent()) {

                Payment existing =
                        existingPayment.get();

                if (existing.getLoan() != null
                        && existing.getLoan().getId() != null
                        && existing.getLoan().getId().equals(loanId)) {

                    log.info(
                            "Duplicate payment transaction detected. " +
                                    "transactionId={}, loanId={}, paymentId={}",
                            normalizedTxnId,
                            loanId,
                            existing.getId()
                    );

                    return existing;
                }

                throw new IllegalStateException(
                        "Transaction ID "
                                + normalizedTxnId
                                + " has already been used for another loan."
                );
            }
        }

        // ============================================================
        // LOAN STATUS
        // ============================================================

        if (loan.getStatus() != LoanStatus.ACTIVE
                && loan.getStatus() != LoanStatus.OVERDUE) {

            throw new IllegalStateException(
                    "Loan is not active. Current status: "
                            + loan.getStatus()
            );
        }

        LocalDate today =
                LocalDate.now();

        LocalDateTime now =
                LocalDateTime.now();

        // ============================================================
        // PAYMENT HISTORY
        // ============================================================

        List<Payment> loanPayments =
                paymentRepo.findByLoanId(loanId);

        if (loanPayments == null) {
            loanPayments = List.of();
        }

        // ============================================================
        // CRITICAL:
        //
        // FIRST PAYMENT MUST BE DETERMINED FROM ACTUAL CASH PAYMENTS.
        //
        // Scheduled payment rows do NOT count.
        // A row existing in the schedule does NOT mean interest has
        // already been charged.
        // ============================================================

        boolean firstInterestCalculation =
                isFirstInterestCalculation(
                        loanPayments
                );

        // ============================================================
        // FIND LAST ACTUAL INTEREST ANCHOR
        //
        // ONLY actual payment rows with money paid are allowed to
        // move the interest clock.
        // ============================================================

        LocalDateTime previousInterestCalculationDate =
                findLatestActualInterestTimestamp(
                        loanPayments,
                        loan
                );

        log.info(
                "INTEREST STATE BEFORE PAYMENT. " +
                        "loanId={}, firstInterestCalculation={}, " +
                        "paymentHistoryCount={}, previousInterestTimestamp={}, " +
                        "disbursedAt={}, now={}",
                loanId,
                firstInterestCalculation,
                loanPayments.size(),
                previousInterestCalculationDate,
                loan.getDisbursedAt(),
                now
        );

        // ============================================================
        // CURRENT PRINCIPAL
        // ============================================================

        BigDecimal currentBalance =
                roundMoney(
                        safe(
                                loan.getOutstandingBalanceDecimal()
                        )
                ).max(ZERO);

        if (currentBalance.compareTo(ZERO) <= 0) {

            throw new IllegalStateException(
                    "Loan has no outstanding principal balance."
            );
        }

        // ============================================================
        // DAILY INTEREST RATE
        // ============================================================

        BigDecimal dailyRate =
                calculateDailyRate(loan);

        if (dailyRate.compareTo(ZERO) <= 0) {

            throw new IllegalStateException(
                    "Loan "
                            + loan.getReferenceNumber()
                            + " has no valid positive interest rate."
            );
        }

        // ============================================================
        // FIND CURRENT INSTALLMENT
        //
        // We intentionally distinguish:
        //
        // 1. an installment already being partially paid
        // 2. the next unpaid scheduled installment
        // 3. a completely new cycle
        // ============================================================

        Optional<Payment> existingCurrentCycle =
                loanPayments.stream()
                        .filter(p -> p != null)
                        .filter(
                                p -> !Boolean.TRUE.equals(
                                        p.getPaid()
                                )
                        )
                        .filter(
                                p -> safe(
                                        p.getAmountPaidDecimal()
                                ).compareTo(ZERO) > 0
                        )
                        .min(
                                Comparator.comparing(
                                        Payment::getDueDate,
                                        Comparator.nullsLast(
                                                Comparator.naturalOrder()
                                        )
                                )
                        );

        Optional<Payment> unpaidInstallment =
                loanPayments.stream()
                        .filter(p -> p != null)
                        .filter(
                                p -> !Boolean.TRUE.equals(
                                        p.getPaid()
                                )
                        )
                        .min(
                                Comparator.comparing(
                                        Payment::getDueDate,
                                        Comparator.nullsLast(
                                                Comparator.naturalOrder()
                                        )
                                )
                        );

        Payment installment;

        boolean newCycleCreated =
                false;

        if (existingCurrentCycle.isPresent()) {

            installment =
                    existingCurrentCycle.get();

            log.info(
                    "Continuing partially paid installment. " +
                            "loanId={}, installment={}, paymentId={}",
                    loanId,
                    installment.getInstallmentNumber(),
                    installment.getId()
            );

        } else if (unpaidInstallment.isPresent()) {

            installment =
                    unpaidInstallment.get();

            log.info(
                    "Using existing unpaid scheduled installment. " +
                            "loanId={}, installment={}, paymentId={}",
                    loanId,
                    installment.getInstallmentNumber(),
                    installment.getId()
            );

        } else {

            LocalDate dueDate =
                    loan.getNextDueDate() != null
                            ? loan.getNextDueDate()
                            : today;

            int nextNumber =
                    loanPayments.stream()
                            .filter(p -> p != null)
                            .map(Payment::getInstallmentNumber)
                            .filter(n -> n != null)
                            .max(Integer::compareTo)
                            .orElse(0)
                            + 1;

            installment =
                    Payment.builder()
                            .loan(loan)
                            .organization(loan.getOrganization())
                            .installmentNumber(nextNumber)
                            .dueDate(dueDate)
                            .amount(
                                    safe(
                                            loan.getNextInstallmentAmountDecimal()
                                    )
                            )
                            .amountPaid(BigDecimal.ZERO)
                            .principalComponent(BigDecimal.ZERO)
                            .interestComponent(BigDecimal.ZERO)
                            .penalty(BigDecimal.ZERO)
                            .penaltyPaid(BigDecimal.ZERO)

                            // CRITICAL:
                            // A new cycle ALWAYS starts clean.
                            .cycleInterestDue(BigDecimal.ZERO)
                            .cycleInterestRemaining(BigDecimal.ZERO)

                            // CRITICAL:
                            // The new cycle has not processed interest yet.
                            .interestCalculationDate(null)

                            .paid(false)
                            .status(Payment.PaymentStatus.PENDING)
                            .build();

            newCycleCreated = true;

            log.info(
                    "Created NEW CLEAN INTEREST CYCLE. " +
                            "loanId={}, installment={}",
                    loanId,
                    nextNumber
            );
        }

        // ============================================================
        // CYCLE DUE DATE
        // ============================================================

        LocalDate cycleDueDate =
                installment.getDueDate() != null
                        ? installment.getDueDate()
                        : (
                        loan.getNextDueDate() != null
                                ? loan.getNextDueDate()
                                : today
                );

        // ============================================================
        // LATE DAYS
        // ============================================================

        long daysLateLong =
                ChronoUnit.DAYS.between(
                        cycleDueDate,
                        today
                );

        int daysLate =
                (int) Math.max(
                        0L,
                        daysLateLong
                );

        boolean isLate =
                daysLate > 0;

        // ============================================================
        // CURRENT CYCLE INTEREST STATE
        //
        // IMPORTANT:
        //
        // A completed previous installment MUST NEVER transfer its
        // cycleInterestDue into the next cycle.
        // ============================================================

        BigDecimal existingCycleInterestDue =
                ZERO;

        BigDecimal existingCycleInterestRemaining =
                ZERO;

        BigDecimal interestAlreadyPaidThisCycle =
                ZERO;

        if (!newCycleCreated
                && installment.getId() != null
                && !Boolean.TRUE.equals(
                        installment.getPaid()
                )) {

            existingCycleInterestDue =
                    roundMoney(
                            safe(
                                    installment
                                            .getCycleInterestDueDecimal()
                            )
                    ).max(ZERO);

            existingCycleInterestRemaining =
                    roundMoney(
                            safe(
                                    installment
                                            .getCycleInterestRemainingDecimal()
                            )
                    ).max(ZERO);

            interestAlreadyPaidThisCycle =
                    roundMoney(
                            existingCycleInterestDue
                                    .subtract(
                                            existingCycleInterestRemaining
                                    )
                                    .max(ZERO)
                    );
        }

        log.info(
                "CURRENT CYCLE INTEREST STATE. " +
                        "loanId={}, installmentId={}, newCycle={}, " +
                        "existingCycleInterestDue={}, " +
                        "existingCycleInterestRemaining={}, " +
                        "interestAlreadyPaidThisCycle={}",
                loanId,
                installment.getId(),
                newCycleCreated,
                existingCycleInterestDue,
                existingCycleInterestRemaining,
                interestAlreadyPaidThisCycle
        );

        // ============================================================
        // PENALTY STATE
        // ============================================================

        BigDecimal penaltyAssessed =
                roundMoney(
                        safe(
                                installment.getPenaltyDecimal()
                        )
                ).max(ZERO);

        BigDecimal penaltyAlreadyPaid =
                roundMoney(
                        safe(
                                installment.getPenaltyPaidDecimal()
                        )
                ).max(ZERO);

        BigDecimal penaltyRemainingBeforePayment =
                penaltyAssessed
                        .subtract(
                                penaltyAlreadyPaid
                        )
                        .max(ZERO);

        // ============================================================
        // INTEREST START
        // ============================================================

        LocalDateTime interestStartDateTime;

        if (firstInterestCalculation) {

            /*
             * FIRST EVER PAYMENT:
             *
             * The interest clock ALWAYS begins at disbursement.
             *
             * Do not use a payment row.
             * Do not use installment.interestCalculationDate.
             */

            interestStartDateTime =
                    loan.getDisbursedAt() != null
                            ? loan.getDisbursedAt()
                            : (
                            loan.getStartDate() != null
                                    ? loan.getStartDate().atStartOfDay()
                                    : now
                    );

        } else {

            /*
             * SUBSEQUENT PAYMENT:
             *
             * Use the timestamp of the last actual payment that
             * successfully processed interest.
             */

            interestStartDateTime =
                    previousInterestCalculationDate;

            if (interestStartDateTime == null) {

                /*
                 * Defensive recovery for legacy data.
                 *
                 * If old records have no timestamp, fall back to
                 * disbursement rather than silently charging zero.
                 */

                interestStartDateTime =
                        loan.getDisbursedAt() != null
                                ? loan.getDisbursedAt()
                                : (
                                loan.getStartDate() != null
                                        ? loan.getStartDate().atStartOfDay()
                                        : now
                        );

                log.warn(
                        "Missing previous interest timestamp. " +
                                "Falling back to loan origin. " +
                                "loanId={}, fallback={}",
                        loanId,
                        interestStartDateTime
                );
            }
        }

        if (interestStartDateTime == null) {
            interestStartDateTime = now;
        }

        if (interestStartDateTime.isAfter(now)) {

            log.warn(
                    "Interest start is after payment time. " +
                            "loanId={}, interestStart={}, now={}",
                    loanId,
                    interestStartDateTime,
                    now
            );

            interestStartDateTime = now;
        }

        // ============================================================
        // INTEREST DAYS
        // ============================================================

        long elapsedDays =
                calculateActualInterestDays(
                        interestStartDateTime,
                        now,
                        firstInterestCalculation,
                        loan
                );

        // ============================================================
        // NEW INTEREST
        // ============================================================

        BigDecimal newlyAccruedInterest =
                calculateNewInterest(
                        currentBalance,
                        dailyRate,
                        elapsedDays
                );

        // ============================================================
        // CURRENT CYCLE TOTAL INTEREST
        // ============================================================

        /*
         * CRITICAL RULE:
         *
         * Existing current-cycle unpaid interest is preserved.
         *
         * New elapsed interest is added only to THIS cycle.
         *
         * A previous completed installment is never copied here.
         */

        BigDecimal totalCycleInterestDue =
                roundMoney(
                        existingCycleInterestRemaining
                                .add(
                                        interestAlreadyPaidThisCycle
                                )
                                .add(
                                        newlyAccruedInterest
                                )
        );

        /*
         * If there was already unpaid current-cycle interest,
         * it cannot disappear.
         */
        BigDecimal minimumExistingCycleObligation =
                roundMoney(
                        existingCycleInterestDue
                );

        if (totalCycleInterestDue.compareTo(
                minimumExistingCycleObligation
        ) < 0) {

            totalCycleInterestDue =
                    minimumExistingCycleObligation;
        }

        totalCycleInterestDue =
                roundMoney(
                        totalCycleInterestDue
                );

        // ============================================================
        // INTEREST REMAINING BEFORE PAYMENT
        // ============================================================

        BigDecimal remainingInterestBeforePayment =
                roundMoney(
                        totalCycleInterestDue
                                .subtract(
                                        interestAlreadyPaidThisCycle
                                )
                                .max(ZERO)
                );

        if (existingCycleInterestRemaining.compareTo(
                remainingInterestBeforePayment
        ) > 0) {

            remainingInterestBeforePayment =
                    existingCycleInterestRemaining;
        }

        remainingInterestBeforePayment =
                roundMoney(
                        remainingInterestBeforePayment
                );

        // ============================================================
        // PENALTY CALCULATION
        // ============================================================

        BigDecimal monthlyPenaltyRate =
                DEFAULT_MONTHLY_PENALTY_RATE;

        BigDecimal dailyPenaltyRate =
                monthlyPenaltyRate.divide(
                        THIRTY,
                        16,
                        RoundingMode.HALF_UP
                );

        BigDecimal calculatedTotalPenalty =
                ZERO;

        if (daysLate > 0
                && currentBalance.compareTo(ZERO) > 0) {

            calculatedTotalPenalty =
                    roundMoney(
                            currentBalance
                                    .multiply(
                                            dailyPenaltyRate
                                    )
                                    .multiply(
                                            BigDecimal.valueOf(
                                                    daysLate
                                            )
                                    )
                    );
        }

        BigDecimal totalPenalty =
                calculatedTotalPenalty.max(
                        penaltyAssessed
                );

        totalPenalty =
                roundMoney(totalPenalty);

        penaltyRemainingBeforePayment =
                roundMoney(
                        totalPenalty
                                .subtract(
                                        penaltyAlreadyPaid
                                )
                                .max(ZERO)
                );

        // ============================================================
        // PAYMENT ALLOCATION
        // ============================================================

        BigDecimal paymentRemaining =
                amount;

        // ============================================================
        // 1. PENALTY
        // ============================================================

        BigDecimal penaltyPaidThisPayment =
                roundMoney(
                        paymentRemaining.min(
                                penaltyRemainingBeforePayment
                        )
                );

        paymentRemaining =
                roundMoney(
                        paymentRemaining
                                .subtract(
                                        penaltyPaidThisPayment
                                )
                                .max(ZERO)
                );

        // ============================================================
        // 2. INTEREST
        // ============================================================

        BigDecimal interestPaidThisPayment =
                roundMoney(
                        paymentRemaining.min(
                                remainingInterestBeforePayment
                        )
                );

        paymentRemaining =
                roundMoney(
                        paymentRemaining
                                .subtract(
                                        interestPaidThisPayment
                                )
                                .max(ZERO)
                );

        // ============================================================
        // 3. PRINCIPAL
        // ============================================================

        BigDecimal principalPaidThisPayment =
                roundMoney(
                        paymentRemaining.min(
                                currentBalance
                        )
                );

        paymentRemaining =
                roundMoney(
                        paymentRemaining
                                .subtract(
                                        principalPaidThisPayment
                                )
                                .max(ZERO)
                );

        // ============================================================
        // 4. OVERPAYMENT
        // ============================================================

        BigDecimal overpayment =
                roundMoney(
                        paymentRemaining.max(ZERO)
                );

        // ============================================================
        // NEW PRINCIPAL
        // ============================================================

        BigDecimal newBalance =
                roundMoney(
                        currentBalance
                                .subtract(
                                        principalPaidThisPayment
                                )
                                .max(ZERO)
                );

        // ============================================================
        // EXISTING ROW TOTALS
        // ============================================================

        BigDecimal existingPrincipalPaid =
                roundMoney(
                        safe(
                                installment
                                        .getPrincipalComponentDecimal()
                        )
                ).max(ZERO);

        BigDecimal totalPrincipalPaid =
                roundMoney(
                        existingPrincipalPaid
                                .add(
                                        principalPaidThisPayment
                                )
                );

        BigDecimal existingInterestComponent =
                roundMoney(
                        safe(
                                installment
                                        .getInterestComponentDecimal()
                        )
                ).max(ZERO);

        BigDecimal totalInterestPaid =
                roundMoney(
                        existingInterestComponent
                                .add(
                                        interestPaidThisPayment
                                )
                );

        BigDecimal totalPenaltyPaid =
                roundMoney(
                        penaltyAlreadyPaid
                                .add(
                                        penaltyPaidThisPayment
                                )
                );

        // ============================================================
        // INTEREST REMAINING AFTER PAYMENT
        // ============================================================

        BigDecimal remainingInterestAfterPayment =
                roundMoney(
                        remainingInterestBeforePayment
                                .subtract(
                                        interestPaidThisPayment
                                )
                                .max(ZERO)
                );

        // ============================================================
        // COMPLETION
        // ============================================================

        boolean penaltyCovered =
                totalPenalty
                        .subtract(
                                totalPenaltyPaid
                        )
                        .compareTo(ONE_CENT) <= 0;

        boolean interestCovered =
                remainingInterestAfterPayment
                        .compareTo(ONE_CENT) <= 0;

        boolean principalCovered =
                newBalance.compareTo(ONE_CENT) <= 0;

        BigDecimal amountPaidSoFar =
                roundMoney(
                        safe(
                                installment.getAmountPaidDecimal()
                        )
                );

        boolean scheduledAmountCovered =
                isScheduledInstallmentCovered(
                        installment,
                        amountPaidSoFar,
                        amount
                );

        boolean cycleCompleted =
                principalCovered
                        && interestCovered
                        && penaltyCovered;

        if (!cycleCompleted) {

            cycleCompleted =
                    scheduledAmountCovered
                            && interestCovered
                            && penaltyCovered;
        }

        if (principalCovered
                && !interestCovered) {

            cycleCompleted = false;
        }

        if (principalCovered
                && !penaltyCovered) {

            cycleCompleted = false;
        }

        // ============================================================
        // OVERPAYMENT VALIDATION
        // ============================================================

        if (overpayment.compareTo(ZERO) > 0
                && !principalCovered) {

            throw new IllegalStateException(
                    "Invalid payment allocation: overpayment exists " +
                            "while principal remains outstanding."
            );
        }

        // ============================================================
        // UPDATE PAYMENT ROW
        // ============================================================

        BigDecimal newAmountPaid =
                roundMoney(
                        amountPaidSoFar
                                .add(
                                        amount
                                )
                );

        installment.setAmountPaid(
                newAmountPaid
        );

        installment.setInterestComponent(
                totalInterestPaid
        );

        installment.setPrincipalComponent(
                totalPrincipalPaid
        );

        installment.setPenalty(
                totalPenalty
        );

        installment.setPenaltyPaid(
                totalPenaltyPaid
        );

        installment.setOutstandingAfter(
                newBalance
        );

        /*
         * CURRENT CYCLE STATE.
         */
        installment.setCycleInterestDue(
                totalCycleInterestDue
        );

        installment.setCycleInterestRemaining(
                remainingInterestAfterPayment
        );

        installment.setLate(
                isLate || installment.isLate()
        );

        int existingDaysLate =
                installment.getDaysLate() != null
                        ? installment.getDaysLate()
                        : 0;

        installment.setDaysLate(
                Math.max(
                        existingDaysLate,
                        daysLate
                )
        );

        installment.setPaymentMethod(
                method
        );

        installment.setTransactionId(
                normalizedTxnId
        );

        installment.setChannel(
                channel
        );

        installment.setNotes(
                notes
        );

        if (recordedBy != null) {
            installment.setRecordedBy(
                    recordedBy
            );
        }

        installment.setPaidDate(
                today
        );

        /*
         * CRITICAL:
         *
         * This timestamp represents the ACTUAL PAYMENT EVENT.
         *
         * It is NOT used as the anchor until this payment has
         * successfully been persisted.
         */
        installment.setInterestCalculationDate(
                now
        );

        installment.setPaid(
                cycleCompleted
        );

        installment.setStatus(
                cycleCompleted
                        ? Payment.PaymentStatus.COMPLETED
                        : Payment.PaymentStatus.PARTIALLY_PAID
        );

        if (installment.getPaymentReference() == null
                || installment.getPaymentReference().isBlank()) {

            installment.setPaymentReference(
                    generateRef(loan)
            );
        }

        // ============================================================
        // SAVE PAYMENT
        // ============================================================

        try {

            installment =
                    paymentRepo.save(
                            installment
                    );

        } catch (DataIntegrityViolationException e) {

            if (normalizedTxnId != null) {

                Optional<Payment> concurrentPayment =
                        paymentRepo
                                .findByOrganization_IdAndTransactionId(
                                        organizationId,
                                        normalizedTxnId
                                );

                if (concurrentPayment.isPresent()) {

                    Payment existing =
                            concurrentPayment.get();

                    if (existing.getLoan() != null
                            && existing.getLoan().getId() != null
                            && existing.getLoan()
                            .getId()
                            .equals(loanId)) {

                        log.info(
                                "Concurrent duplicate payment detected. " +
                                        "transactionId={}, loanId={}, paymentId={}",
                                normalizedTxnId,
                                loanId,
                                existing.getId()
                        );

                        return existing;
                    }
                }
            }

            throw e;
        }

        // ============================================================
        // UPDATE LOAN TOTAL PAID
        // ============================================================

        BigDecimal oldTotalPaid =
                roundMoney(
                        safe(
                                loan.getTotalPaidDecimal()
                        )
                );

        BigDecimal newTotalPaid =
                roundMoney(
                        oldTotalPaid
                                .add(
                                        amount
                                )
                );

        /*
         * totalPaid = actual cash received.
         */
        loan.setTotalPaid(
                newTotalPaid
        );

        /*
         * outstandingBalance = PRINCIPAL ONLY.
         */
        loan.setOutstandingBalance(
                newBalance
        );

        loan.setLastPaymentDate(
                today
        );

        // ============================================================
        // LOAN STATUS
        // ============================================================

        if (principalCovered
                && interestCovered
                && penaltyCovered) {

            loan.setStatus(
                    LoanStatus.PAID
            );

            Long currentInstallmentId =
                    installment.getId();

            List<Payment> stillPending =
                    paymentRepo
                            .findByLoanId(loanId)
                            .stream()
                            .filter(p -> p != null)
                            .filter(
                                    p -> !Boolean.TRUE.equals(
                                            p.getPaid()
                                    )
                            )
                            .filter(
                                    p ->
                                            p.getId() == null
                                                    || !p.getId()
                                                    .equals(
                                                            currentInstallmentId
                                                    )
                            )
                            .toList();

            if (!stillPending.isEmpty()) {

                paymentRepo.deleteAll(
                        stillPending
                );
            }

            loan.setNextDueDate(
                    null
            );

            loan.setNextPaymentDate(
                    null
            );

            loan.setNextInstallmentAmount(
                    BigDecimal.ZERO
            );

        } else {

            loan.setStatus(
                    isLate
                            ? LoanStatus.OVERDUE
                            : LoanStatus.ACTIVE
            );

            if (cycleCompleted) {

                LocalDate nextDue =
                        cycleDueDate.plusMonths(1);

                loan.setNextDueDate(
                        nextDue
                );

                loan.setNextPaymentDate(
                        nextDue
                );

            } else {

                loan.setNextDueDate(
                        cycleDueDate
                );

                loan.setNextPaymentDate(
                        cycleDueDate
                );
            }
        }

        loanRepo.save(
                loan
        );

        // ============================================================
        // ACCOUNTING
        // ============================================================

        accountingService.postPaymentReceived(
                installment,
                amount,
                principalPaidThisPayment,
                interestPaidThisPayment,
                penaltyPaidThisPayment,
                overpayment
        );

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
                        + " — first interest calculation: "
                        + firstInterestCalculation
                        + ", previous interest timestamp: "
                        + previousInterestCalculationDate
                        + ", interest start: "
                        + interestStartDateTime
                        + ", payment time: "
                        + now
                        + ", interest days: "
                        + elapsedDays
                        + ", daily interest rate: "
                        + dailyRate
                        + ", newly accrued interest: "
                        + newlyAccruedInterest
                        + ", current cycle interest due: "
                        + totalCycleInterestDue
                        + ", current cycle interest already paid: "
                        + interestAlreadyPaidThisCycle
                        + ", interest paid this payment: "
                        + interestPaidThisPayment
                        + ", remaining cycle interest: "
                        + remainingInterestAfterPayment
                        + ", principal paid: "
                        + principalPaidThisPayment
                        + ", penalty days: "
                        + daysLate
                        + ", penalty paid this payment: "
                        + penaltyPaidThisPayment
                        + ", total penalty: "
                        + totalPenalty
                        + ", outstanding principal: "
                        + newBalance
                        + ", overpayment: "
                        + overpayment
                        + ", transactionId: "
                        + normalizedTxnId
        );

        // ============================================================
        // EMAIL
        // ============================================================

        try {

            mailService.sendPaymentConfirmation(
                    loan,
                    amount.doubleValue()
            );

        } catch (Exception e) {

            log.warn(
                    "Payment email notification failed for loanId={}",
                    loan.getId(),
                    e
            );
        }

        // ============================================================
        // SMS
        // ============================================================

        try {

            smsService.sendPaymentConfirmed(
                    loan,
                    amount.doubleValue()
            );

        } catch (Exception e) {

            log.warn(
                    "Payment SMS notification failed for loanId={}",
                    loan.getId(),
                    e
            );
        }

        // ============================================================
        // LOAN OFFICER NOTIFICATION
        // ============================================================

        if (loan.getLoanOfficer() != null
                && (
                recordedBy == null
                        || loan.getLoanOfficer().getId() == null
                        || recordedBy.getId() == null
                        || !loan.getLoanOfficer()
                        .getId()
                        .equals(
                                recordedBy.getId()
                        )
        )) {

            try {

                notifService.notifyUsers(
                        List.of(
                                loan.getLoanOfficer()
                        ),
                        "Payment Received",
                        "A payment of "
                                + loan.getCurrency()
                                + " "
                                + amount
                                + " was recorded on loan "
                                + loan.getReferenceNumber()
                                + (
                                recordedBy != null
                                        ? " by "
                                        + recordedBy.getName()
                                        : " automatically"
                        )
                                + (
                                overpayment.compareTo(ZERO) > 0
                                        ? ". Borrower refund payable: "
                                        + loan.getCurrency()
                                        + " "
                                        + overpayment
                                        : "."
                        ),
                        "success",
                        "/dashboard/loans/"
                                + loan.getId()
                );

            } catch (Exception e) {

                log.warn(
                        "In-app payment notification failed for loanId={}",
                        loan.getId(),
                        e
                );
            }
        }

        // ============================================================
        // WEBHOOK
        // ============================================================

        try {

            Map<String, Object> paymentWebhook =
                    new HashMap<>();

            paymentWebhook.put(
                    "paymentId",
                    installment.getId()
            );

            paymentWebhook.put(
                    "loanId",
                    loan.getId()
            );

            paymentWebhook.put(
                    "loanReference",
                    loan.getReferenceNumber()
            );

            if (loan.getBorrower() != null) {

                paymentWebhook.put(
                        "borrowerId",
                        loan.getBorrower().getId()
                );
            }

            paymentWebhook.put(
                    "amount",
                    amount
            );

            paymentWebhook.put(
                    "principalPaid",
                    principalPaidThisPayment
            );

            paymentWebhook.put(
                    "interestPaid",
                    interestPaidThisPayment
            );

            paymentWebhook.put(
                    "penaltyPaid",
                    penaltyPaidThisPayment
            );

            paymentWebhook.put(
                    "totalPenalty",
                    totalPenalty
            );

            paymentWebhook.put(
                    "totalPenaltyPaidThisCycle",
                    totalPenaltyPaid
            );

            paymentWebhook.put(
                    "penaltyDays",
                    daysLate
            );

            paymentWebhook.put(
                    "dailyPenaltyRate",
                    dailyPenaltyRate
            );

            paymentWebhook.put(
                    "totalInterestPaid",
                    totalInterestPaid
            );

            paymentWebhook.put(
                    "totalInterestDue",
                    totalCycleInterestDue
            );

            paymentWebhook.put(
                    "currentCycleInterestAlreadyPaid",
                    interestAlreadyPaidThisCycle
            );

            paymentWebhook.put(
                    "remainingInterest",
                    remainingInterestAfterPayment
            );

            paymentWebhook.put(
                    "totalPrincipalPaid",
                    totalPrincipalPaid
            );

            paymentWebhook.put(
                    "outstandingBalance",
                    newBalance
            );

            paymentWebhook.put(
                    "overpayment",
                    overpayment
            );

            paymentWebhook.put(
                    "borrowerRefundPayable",
                    overpayment
            );

            paymentWebhook.put(
                    "borrowerRefundPayableAccount",
                    BORROWER_REFUNDS_PAYABLE_ACCOUNT
            );

            paymentWebhook.put(
                    "interestDays",
                    elapsedDays
            );

            paymentWebhook.put(
                    "firstInterestCalculation",
                    firstInterestCalculation
            );

            paymentWebhook.put(
                    "previousInterestCalculationDate",
                    previousInterestCalculationDate != null
                            ? previousInterestCalculationDate.toString()
                            : null
            );

            paymentWebhook.put(
                    "dailyInterestRate",
                    dailyRate
            );

            paymentWebhook.put(
                    "paymentMethod",
                    method
            );

            paymentWebhook.put(
                    "channel",
                    channel
            );

            paymentWebhook.put(
                    "transactionId",
                    normalizedTxnId
            );

            paymentWebhook.put(
                    "paymentReference",
                    installment.getPaymentReference()
            );

            paymentWebhook.put(
                    "paymentDate",
                    today.toString()
            );

            paymentWebhook.put(
                    "paymentTimestamp",
                    now.toString()
            );

            paymentWebhook.put(
                    "interestCalculationStart",
                    interestStartDateTime.toString()
            );

            paymentWebhook.put(
                    "interestCalculationDate",
                    installment.getInterestCalculationDate() != null
                            ? installment
                            .getInterestCalculationDate()
                            .toString()
                            : null
            );

            paymentWebhook.put(
                    "installmentNumber",
                    installment.getInstallmentNumber()
            );

            paymentWebhook.put(
                    "paymentStatus",
                    installment.getStatus() != null
                            ? installment.getStatus().name()
                            : null
            );

            paymentWebhook.put(
                    "loanStatus",
                    loan.getStatus() != null
                            ? loan.getStatus().name()
                            : null
            );

            webhookService.dispatch(
                    loan.getOrganization(),
                    "PAYMENT_MADE",
                    paymentWebhook
            );

        } catch (Exception e) {

            log.error(
                    "[PAYMENT WEBHOOK] Failed to dispatch PAYMENT_MADE. " +
                            "loanId={}, paymentId={}",
                    loan.getId(),
                    installment.getId(),
                    e
            );
        }

        // ============================================================
        // FINAL LOG
        // ============================================================

        log.info(
                "Payment successfully recorded. " +
                        "loanId={}, paymentId={}, amount={}, " +
                        "firstInterestCalculation={}, " +
                        "previousInterestTimestamp={}, " +
                        "interestStart={}, " +
                        "interestDays={}, dailyRate={}, " +
                        "newlyAccruedInterest={}, " +
                        "totalCycleInterest={}, " +
                        "interestAlreadyPaidThisCycle={}, " +
                        "interestPaidThisPayment={}, " +
                        "totalInterestPaid={}, " +
                        "principalPaidThisPayment={}, " +
                        "totalPrincipalPaid={}, " +
                        "penaltyAlreadyPaidThisCycle={}, " +
                        "penaltyPaid={}, penaltyDays={}, " +
                        "totalPenaltyPaidThisCycle={}, " +
                        "overpayment={}, outstandingBalance={}, " +
                        "cycleCompleted={}, loanStatus={}",
                loan.getId(),
                installment.getId(),
                amount,
                firstInterestCalculation,
                previousInterestCalculationDate,
                interestStartDateTime,
                elapsedDays,
                dailyRate,
                newlyAccruedInterest,
                totalCycleInterestDue,
                interestAlreadyPaidThisCycle,
                interestPaidThisPayment,
                totalInterestPaid,
                principalPaidThisPayment,
                totalPrincipalPaid,
                penaltyAlreadyPaid,
                penaltyPaidThisPayment,
                daysLate,
                totalPenaltyPaid,
                overpayment,
                newBalance,
                cycleCompleted,
                loan.getStatus()
        );

        return installment;
    }

    // ================================================================
    // FIRST INTEREST CALCULATION
    // ================================================================

    /**
     * Determines whether this is the first REAL payment transaction
     * for the loan.
     *
     * IMPORTANT:
     *
     * Merely having a Payment row does NOT mean interest has already
     * been calculated.
     *
     * Schedule rows can exist before the borrower pays anything.
     */
    public boolean isFirstInterestCalculation(
            List<Payment> payments
    ) {

        if (payments == null || payments.isEmpty()) {
            return true;
        }

        for (Payment payment : payments) {

            if (payment == null) {
                continue;
            }

            BigDecimal amountPaid =
                    safe(
                            payment.getAmountPaidDecimal()
                    );

            /*
             * Only actual money paid counts.
             */
            if (amountPaid.compareTo(
                    BigDecimal.ZERO
            ) > 0) {

                return false;
            }
        }

        return true;
    }

    // ================================================================
    // FIND LAST ACTUAL INTEREST TIMESTAMP
    // ================================================================

    /**
     * Returns the timestamp of the most recent REAL payment.
     *
     * IMPORTANT:
     *
     * We do NOT inspect every payment row blindly.
     * Scheduled/unpaid rows must never move the interest clock.
     */
    private LocalDateTime findLatestActualInterestTimestamp(
            List<Payment> payments,
            Loan loan
    ) {

        LocalDateTime latest =
                null;

        if (payments != null) {

            for (Payment payment : payments) {

                if (payment == null) {
                    continue;
                }

                BigDecimal amountPaid =
                        safe(
                                payment.getAmountPaidDecimal()
                        );

                /*
                 * No cash means no interest-clock movement.
                 */
                if (amountPaid.compareTo(
                        BigDecimal.ZERO
                ) <= 0) {

                    continue;
                }

                LocalDateTime timestamp =
                        payment.getInterestCalculationDate();

                if (timestamp == null) {
                    continue;
                }

                if (latest == null
                        || timestamp.isAfter(latest)) {

                    latest = timestamp;
                }
            }
        }

        if (latest != null) {
            return latest;
        }

        /*
         * Legacy-data fallback.
         */
        if (loan != null
                && loan.getDisbursedAt() != null) {

            return loan.getDisbursedAt();
        }

        if (loan != null
                && loan.getStartDate() != null) {

            return loan.getStartDate()
                    .atStartOfDay();
        }

        return null;
    }

    // ================================================================
    // INSTALLMENT COMPLETION
    // ================================================================

    private boolean isScheduledInstallmentCovered(
            Payment installment,
            BigDecimal amountPaidSoFar,
            BigDecimal currentPayment
    ) {

        if (installment == null) {
            return false;
        }

        BigDecimal scheduledAmount =
                roundMoney(
                        safe(
                                installment.getAmountDecimal()
                        )
                );

        if (scheduledAmount.compareTo(
                BigDecimal.ZERO
        ) <= 0) {

            return false;
        }

        BigDecimal newPaidAmount =
                roundMoney(
                        safe(amountPaidSoFar)
                                .add(
                                        safe(currentPayment)
                                )
                );

        return newPaidAmount.compareTo(
                scheduledAmount
        ) >= 0;
    }

    // ================================================================
    // VALIDATE ORGANIZATION ACCESS
    // ================================================================

    private void validateOrganizationAccess(
            Loan loan,
            User recordedBy
    ) {

        if (loan == null) {

            throw new IllegalArgumentException(
                    "Loan is required"
            );
        }

        if (loan.getOrganization() == null
                || loan.getOrganization().getId() == null) {

            throw new IllegalStateException(
                    "Loan organization is required."
            );
        }

        if (recordedBy == null) {
            return;
        }

        if (recordedBy.getOrganization() == null
                || recordedBy.getOrganization().getId() == null) {

            throw new IllegalStateException(
                    "Recorded user's organization is required."
            );
        }

        Long loanOrganizationId =
                loan.getOrganization().getId();

        Long userOrganizationId =
                recordedBy.getOrganization().getId();

        if (!loanOrganizationId.equals(
                userOrganizationId
        )) {

            throw new IllegalStateException(
                    "Access denied."
            );
        }
    }

    // ================================================================
    // GET LOAN SCHEDULE
    // ================================================================

    @Transactional(readOnly = true)
    public List<Payment> getLoanSchedule(
            Long loanId,
            Long orgId
    ) {

        if (loanId == null) {

            throw new IllegalArgumentException(
                    "Loan ID is required"
            );
        }

        if (orgId == null) {

            throw new IllegalArgumentException(
                    "Organization ID is required"
            );
        }

        Loan loan =
                loanRepo.findById(loanId)
                        .orElseThrow(
                                () ->
                                        new RuntimeException(
                                                "Loan not found"
                                        )
                        );

        if (loan.getOrganization() == null
                || loan.getOrganization().getId() == null
                || !loan.getOrganization()
                .getId()
                .equals(orgId)) {

            throw new IllegalStateException(
                    "Access denied."
            );
        }

        return paymentRepo.findByLoanId(
                loanId
        );
    }

    // ================================================================
    // MARK OVERDUE
    // ================================================================

    @Transactional
    public void markOverdueLoans(
            Long orgId
    ) {

        if (orgId == null) {

            throw new IllegalArgumentException(
                    "Organization ID is required"
            );
        }

        LocalDate today =
                LocalDate.now();

        List<Payment> overduePayments =
                paymentRepo
                        .findByOrganization_IdAndPaidFalseAndDueDateBefore(
                                orgId,
                                today
                        );

        if (overduePayments == null
                || overduePayments.isEmpty()) {

            return;
        }

        for (Payment payment : overduePayments) {

            if (payment == null) {
                continue;
            }

            Loan loan =
                    payment.getLoan();

            if (loan == null) {
                continue;
            }

            if (loan.getOrganization() == null
                    || loan.getOrganization().getId() == null
                    || !orgId.equals(
                    loan.getOrganization().getId()
            )) {

                continue;
            }

            if (loan.getStatus() == LoanStatus.ACTIVE) {

                loan.setStatus(
                        LoanStatus.OVERDUE
                );
            }

            if (payment.getDueDate() != null) {

                int days =
                        Math.max(
                                0,
                                (int) ChronoUnit.DAYS.between(
                                        payment.getDueDate(),
                                        today
                                )
                        );

                loan.setDaysOverdue(
                        Math.max(
                                loan.getDaysOverdue() != null
                                        ? loan.getDaysOverdue()
                                        : 0,
                                days
                        )
                );

                payment.setLate(
                        true
                );

                payment.setDaysLate(
                        Math.max(
                                payment.getDaysLate() != null
                                        ? payment.getDaysLate()
                                        : 0,
                                days
                        )
                );

                paymentRepo.save(
                        payment
                );
            }

            loanRepo.save(
                    loan
            );
        }
    }

    // ================================================================
    // ACTUAL DAILY INTEREST DAYS
    // ================================================================

    /**
     * Calculates elapsed calendar days.
     *
     * BUSINESS RULE:
     *
     * First payment:
     *
     * 09 Aug 10:00 -> 09 Aug 10:01
     * = 1 day
     *
     * Same-day second payment:
     *
     * 09 Aug 10:01 -> 09 Aug 10:05
     * = 0 additional days
     *
     * Next calendar day:
     *
     * 09 Aug 10:05 -> 10 Aug 10:05
     * = 1 additional day
     */
    private long calculateActualInterestDays(
            LocalDateTime interestStart,
            LocalDateTime now,
            boolean firstInterestCalculation,
            Loan loan
    ) {

        if (interestStart == null
                || now == null) {

            return firstInterestCalculation
                    ? 1L
                    : 0L;
        }

        if (interestStart.isAfter(now)) {

            return firstInterestCalculation
                    ? 1L
                    : 0L;
        }

        long calendarDays =
                ChronoUnit.DAYS.between(
                        interestStart.toLocalDate(),
                        now.toLocalDate()
                );

        long effectiveDays;

        if (firstInterestCalculation) {

            /*
             * FIRST PAYMENT ALWAYS CHARGES AT LEAST ONE DAY.
             */
            effectiveDays =
                    Math.max(
                            1L,
                            calendarDays
                    );

        } else {

            /*
             * SUBSEQUENT SAME-DAY PAYMENTS DO NOT CHARGE AGAIN.
             */
            effectiveDays =
                    Math.max(
                            0L,
                            calendarDays
                    );
        }

        log.info(
                "DAILY INTEREST CALCULATION. " +
                        "loanId={}, firstInterestCalculation={}, " +
                        "interestStart={}, paymentTime={}, " +
                        "calendarDays={}, effectiveInterestDays={}",
                loan != null
                        ? loan.getId()
                        : null,
                firstInterestCalculation,
                interestStart,
                now,
                calendarDays,
                effectiveDays
        );

        return effectiveDays;
    }

    // ================================================================
    // DAILY INTEREST RATE
    // ================================================================

    private BigDecimal calculateDailyRate(
            Loan loan
    ) {

        if (loan == null) {

            throw new IllegalArgumentException(
                    "Loan is required for interest calculation."
            );
        }

        BigDecimal rate =
                safe(
                        loan.getInterestRateDecimal()
                );

        if (rate.compareTo(
                BigDecimal.ZERO
        ) <= 0) {

            throw new IllegalStateException(
                    "Loan "
                            + loan.getId()
                            + " has no positive interest rate. "
                            + "interestRate="
                            + rate
            );
        }

        String rateType =
                loan.getInterestRateType() != null
                        ? loan.getInterestRateType().trim()
                        : null;

        if (rateType == null
                || rateType.isBlank()) {

            throw new IllegalStateException(
                    "Interest rate type is required for loan "
                            + loan.getId()
            );
        }

        // ============================================================
        // MONTHLY
        // ============================================================

        if ("MONTHLY".equalsIgnoreCase(
                rateType
        )) {

            return rate
                    .divide(
                            ONE_HUNDRED,
                            16,
                            RoundingMode.HALF_UP
                    )
                    .divide(
                            THIRTY,
                            16,
                            RoundingMode.HALF_UP
                    );
        }

        // ============================================================
        // ANNUAL
        // ============================================================

        if ("ANNUAL".equalsIgnoreCase(
                rateType
        )) {

            return rate
                    .divide(
                            ONE_HUNDRED,
                            16,
                            RoundingMode.HALF_UP
                    )
                    .divide(
                            TWELVE,
                            16,
                            RoundingMode.HALF_UP
                    )
                    .divide(
                            THIRTY,
                            16,
                            RoundingMode.HALF_UP
                    );
        }

        throw new IllegalStateException(
                "Unsupported interest rate type '"
                        + rateType
                        + "' for loan "
                        + loan.getId()
        );
    }

    // ================================================================
    // CALCULATE NEW INTEREST
    // ================================================================

    private BigDecimal calculateNewInterest(
            BigDecimal currentBalance,
            BigDecimal dailyRate,
            long elapsedDays
    ) {

        if (currentBalance == null
                || currentBalance.compareTo(
                BigDecimal.ZERO
        ) <= 0) {

            return ZERO;
        }

        if (dailyRate == null
                || dailyRate.compareTo(
                BigDecimal.ZERO
        ) <= 0) {

            return ZERO;
        }

        if (elapsedDays <= 0) {

            log.info(
                    "No new elapsed interest days. " +
                            "balance={}, dailyRate={}, days=0",
                    currentBalance,
                    dailyRate
            );

            return ZERO;
        }

        BigDecimal interest =
                currentBalance
                        .multiply(
                                dailyRate
                        )
                        .multiply(
                                BigDecimal.valueOf(
                                        elapsedDays
                                )
                        );

        BigDecimal rounded =
                roundMoney(
                        interest
                );

        log.info(
                "CALCULATED NEW INTEREST. " +
                        "balance={}, dailyRate={}, days={}, interest={}",
                currentBalance,
                dailyRate,
                elapsedDays,
                rounded
        );

        return rounded;
    }

    // ================================================================
    // SAFE BIGDECIMAL
    // ================================================================

    private BigDecimal safe(
            BigDecimal value
    ) {

        if (value == null) {
            return ZERO;
        }

        return value;
    }

    // ================================================================
    // ROUND MONEY
    // ================================================================

    private BigDecimal roundMoney(
            BigDecimal value
    ) {

        if (value == null) {
            return ZERO;
        }

        return value.setScale(
                2,
                RoundingMode.HALF_UP
        );
    }

    // ================================================================
    // TRANSACTION ID
    // ================================================================

    private String normalizeTransactionId(
            String txnId
    ) {

        if (txnId == null) {
            return null;
        }

        String normalized =
                txnId.trim();

        return normalized.isBlank()
                ? null
                : normalized;
    }

    // ================================================================
    // PAYMENT REFERENCE
    // ================================================================

    private String generateRef(
            Loan loan
    ) {

        String loanReference =
                loan != null
                        && loan.getReferenceNumber() != null
                        && !loan.getReferenceNumber().isBlank()
                        ? loan.getReferenceNumber()
                        : String.valueOf(
                        loan != null
                                ? loan.getId()
                                : "UNKNOWN"
                );

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
            String desc
    ) {

        auditService.log(
                org,
                user,
                action,
                entityType,
                entityId,
                desc
        );
    }
}