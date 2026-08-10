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
        // LOAD LOAN WITH PESSIMISTIC LOCK
        // ============================================================

        Loan loan =
                loanRepo.findByIdForUpdate(loanId)
                        .orElseThrow(
                                () -> new RuntimeException(
                                        "Loan not found: " + loanId
                                )
                        );

        // ============================================================
        // ORGANIZATION ACCESS
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
        // COMPLETE LOAN PAYMENT HISTORY
        // ============================================================

        List<Payment> loanPayments =
                paymentRepo.findByLoanId(loanId);

        if (loanPayments == null) {
            loanPayments = List.of();
        }

        // ============================================================
        // FIND THE CURRENT OPEN INSTALLMENT FIRST
        //
        // THIS IS THE IMPORTANT CHANGE.
        //
        // We determine "first interest calculation" from the current
        // installment, NOT from whether the loan has ever had a payment.
        //
        // Therefore:
        //
        // OLD CYCLE:
        // installment 1 -> interest paid
        //
        // NEW CYCLE:
        // installment 2 -> no interest calculation yet
        //
        // installment 2 is a FIRST INTEREST CALCULATION even though
        // installment 1 exists.
        // ============================================================

        Optional<Payment> currentPartiallyPaidInstallment =
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
                                ).compareTo(BigDecimal.ZERO) > 0
                        )
                        .min(
                                Comparator.comparing(
                                        Payment::getDueDate,
                                        Comparator.nullsLast(
                                                Comparator.naturalOrder()
                                        )
                                )
                        );

        Optional<Payment> oldestUnpaidInstallment =
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

        if (currentPartiallyPaidInstallment.isPresent()) {

            installment =
                    currentPartiallyPaidInstallment.get();

            log.info(
                    "Continuing partially paid interest cycle. " +
                            "loanId={}, installment={}, paymentId={}, " +
                            "amountPaid={}, interestCalculationDate={}",
                    loanId,
                    installment.getInstallmentNumber(),
                    installment.getId(),
                    installment.getAmountPaidDecimal(),
                    installment.getInterestCalculationDate()
            );

        } else if (oldestUnpaidInstallment.isPresent()) {

            installment =
                    oldestUnpaidInstallment.get();

            log.info(
                    "Using next unpaid scheduled installment. " +
                            "loanId={}, installment={}, paymentId={}, " +
                            "amountPaid={}, interestCalculationDate={}",
                    loanId,
                    installment.getInstallmentNumber(),
                    installment.getId(),
                    installment.getAmountPaidDecimal(),
                    installment.getInterestCalculationDate()
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
                            .cycleInterestDue(BigDecimal.ZERO)
                            .cycleInterestRemaining(BigDecimal.ZERO)
                            .interestCalculationDate(null)
                            .paid(false)
                            .status(Payment.PaymentStatus.PENDING)
                            .build();

            log.info(
                    "Creating new payment cycle. " +
                            "loanId={}, installment={}",
                    loanId,
                    nextNumber
            );
        }

        // ============================================================
        // CURRENT CYCLE FIRST INTEREST CALCULATION
        //
        // THIS REPLACES USING:
        //
        // isFirstInterestCalculation(loanPayments)
        //
        // The current cycle is new when:
        //
        // 1. nothing has been paid on this installment, AND
        // 2. this installment has never had an interest calculation.
        //
        // This means an old payment on the loan does NOT prevent
        // the new cycle from receiving its first day's interest.
        // ============================================================

        boolean firstInterestCalculation =
                isFirstInterestCalculationForCycle(
                        installment
                );

        // ============================================================
        // GLOBAL INTEREST ANCHOR
        //
        // The anchor is still loan-wide.
        //
        // This means:
        //
        // previous cycle timestamp = 10:01
        // new cycle starts
        // payment at 10:05
        //
        // the new cycle knows the previous interest clock.
        //
        // But because this is the FIRST calculation of the NEW cycle,
        // a same-calendar-day first payment receives the minimum
        // one interest day.
        // ============================================================

        LocalDateTime previousInterestCalculationDate =
                findLatestInterestCalculationTimestamp(
                        loanPayments,
                        loan
                );

        log.info(
                "Interest cycle state. " +
                        "loanId={}, installment={}, paymentId={}, " +
                        "firstInterestCalculation={}, historyCount={}, " +
                        "latestInterestTimestamp={}, disbursedAt={}, now={}",
                loanId,
                installment.getInstallmentNumber(),
                installment.getId(),
                firstInterestCalculation,
                loanPayments.size(),
                previousInterestCalculationDate,
                loan.getDisbursedAt(),
                now
        );

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
        // CURRENT CYCLE VALUES
        // ============================================================

        BigDecimal amountPaidSoFar =
                roundMoney(
                        safe(
                                installment.getAmountPaidDecimal()
                        )
                );

        BigDecimal interestAlreadyPaid =
                roundMoney(
                        safe(
                                installment.getInterestComponentDecimal()
                        )
                ).max(ZERO);

        BigDecimal existingCycleInterestDue =
                roundMoney(
                        safe(
                                installment.getCycleInterestDueDecimal()
                        )
                ).max(ZERO);

        BigDecimal existingCycleInterestRemaining =
                roundMoney(
                        safe(
                                installment.getCycleInterestRemainingDecimal()
                        )
                ).max(ZERO);

        BigDecimal penaltyAssessed =
                roundMoney(
                        safe(
                                installment.getPenaltyDecimal()
                        )
                ).max(ZERO);

        // ============================================================
        // IMPORTANT:
        //
        // If this is a genuinely NEW cycle, do not inherit historical
        // cycle interest from a previous payment row.
        //
        // The current installment itself is authoritative for the
        // current cycle.
        // ============================================================

        if (firstInterestCalculation) {

            interestAlreadyPaid = ZERO;
            existingCycleInterestDue = ZERO;
            existingCycleInterestRemaining = ZERO;

            installment.setInterestComponent(ZERO);
            installment.setCycleInterestDue(ZERO);
            installment.setCycleInterestRemaining(ZERO);

            log.info(
                    "New interest cycle initialized. " +
                            "loanId={}, installment={}, " +
                            "historical interest is NOT carried into current cycle.",
                    loanId,
                    installment.getInstallmentNumber()
            );
        }

        BigDecimal penaltyAlreadyPaid =
                ZERO;

        BigDecimal penaltyRemainingBeforePayment =
                penaltyAssessed
                        .subtract(
                                penaltyAlreadyPaid
                        )
                        .max(ZERO);

        // ============================================================
        // CURRENT PRINCIPAL
        // ============================================================

        BigDecimal currentBalance =
                roundMoney(
                        safe(
                                loan.getOutstandingBalanceDecimal()
                        )
                ).max(ZERO);

        // ============================================================
        // INTEREST RATE
        // ============================================================

        BigDecimal dailyRate =
                calculateDailyRate(loan);

        if (currentBalance.compareTo(ZERO) > 0
                && dailyRate.compareTo(ZERO) <= 0) {

            throw new IllegalStateException(
                    "Loan "
                            + loan.getReferenceNumber()
                            + " has an outstanding principal balance of "
                            + currentBalance
                            + " but no valid positive interest rate. "
                            + "Interest rate="
                            + safe(loan.getInterestRateDecimal())
                            + ", rate type="
                            + loan.getInterestRateType()
            );
        }

        // ============================================================
        // INTEREST START
        // ============================================================

        LocalDateTime interestStartDateTime;

        if (previousInterestCalculationDate != null) {

            interestStartDateTime =
                    previousInterestCalculationDate;

        } else {

            interestStartDateTime =
                    loan.getDisbursedAt() != null
                            ? loan.getDisbursedAt()
                            : (
                            loan.getStartDate() != null
                                    ? loan.getStartDate().atStartOfDay()
                                    : now
                    );
        }

        if (interestStartDateTime == null) {
            interestStartDateTime = now;
        }

        if (interestStartDateTime.isAfter(now)) {

            log.warn(
                    "Interest start is after payment timestamp. " +
                            "loanId={}, interestStart={}, now={}. " +
                            "Using payment timestamp as anchor.",
                    loanId,
                    interestStartDateTime,
                    now
            );

            interestStartDateTime = now;
        }

        // ============================================================
        // ACTUAL INTEREST DAYS
        // ============================================================

        long elapsedDays =
                calculateActualInterestDays(
                        interestStartDateTime,
                        now,
                        installment,
                        loan,
                        firstInterestCalculation
                );

        // ============================================================
        // NEW ACCRUED INTEREST
        // ============================================================

        BigDecimal newlyAccruedInterest =
                calculateNewInterest(
                        currentBalance,
                        dailyRate,
                        elapsedDays
                );

        // ============================================================
        // CURRENT CYCLE INTEREST
        //
        // Existing current-cycle obligation + newly accrued interest.
        //
        // Historical previous-cycle interest is never inserted here.
        // ============================================================

        BigDecimal totalCycleInterestDue =
                roundMoney(
                        existingCycleInterestDue
                                .add(
                                        newlyAccruedInterest
                                )
                );

        if (!firstInterestCalculation
                && existingCycleInterestRemaining.compareTo(ZERO) > 0) {

            BigDecimal minimumRequiredInterest =
                    roundMoney(
                            interestAlreadyPaid
                                    .add(
                                            existingCycleInterestRemaining
                                    )
                    );

            if (minimumRequiredInterest.compareTo(
                    totalCycleInterestDue
            ) > 0) {

                totalCycleInterestDue =
                        minimumRequiredInterest;
            }
        }

        totalCycleInterestDue =
                roundMoney(
                        totalCycleInterestDue
                );

        // ============================================================
        // INTEREST REMAINING BEFORE PAYMENT
        // ============================================================

        BigDecimal calculatedRemainingInterest =
                roundMoney(
                        totalCycleInterestDue
                                .subtract(
                                        interestAlreadyPaid
                                )
                                .max(ZERO)
                );

        BigDecimal remainingInterestBeforePayment =
                calculatedRemainingInterest.max(
                        existingCycleInterestRemaining
                );

        remainingInterestBeforePayment =
                roundMoney(
                        remainingInterestBeforePayment
                );

        // ============================================================
        // PENALTY
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
        //
        // ORDER:
        //
        // 1. penalty
        // 2. interest
        // 3. principal
        // 4. overpayment
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
                        paymentRemaining.max(
                                ZERO
                        )
                );

        // ============================================================
        // NEW PRINCIPAL BALANCE
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
        // TOTAL CURRENT-CYCLE COMPONENTS
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

        BigDecimal totalInterestPaid =
                roundMoney(
                        interestAlreadyPaid
                                .add(
                                        interestPaidThisPayment
                                )
                );

        BigDecimal remainingInterestAfterPayment =
                roundMoney(
                        totalCycleInterestDue
                                .subtract(
                                        totalInterestPaid
                                )
                                .max(ZERO)
                );

        // ============================================================
        // COMPLETION
        // ============================================================

        boolean penaltyCovered =
                penaltyRemainingBeforePayment
                        .subtract(
                                penaltyPaidThisPayment
                        )
                        .compareTo(
                                ONE_CENT
                        ) <= 0;

        boolean interestCovered =
                remainingInterestAfterPayment
                        .compareTo(
                                ONE_CENT
                        ) <= 0;

        boolean scheduledAmountCovered =
                isScheduledInstallmentCovered(
                        installment,
                        amountPaidSoFar,
                        amount
                );

        /*
         * A scheduled cycle is completed when its scheduled amount
         * has been covered AND its interest and penalty obligations
         * have been covered.
         *
         * We do NOT use principalCovered alone to close a monthly
         * cycle.
         */
        boolean cycleCompleted =
                scheduledAmountCovered
                        && interestCovered
                        && penaltyCovered;

        boolean principalCovered =
                newBalance.compareTo(
                        ONE_CENT
                ) <= 0;

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
        // FULL LOAN PAYMENT
        // ============================================================

        boolean loanFullyPaid =
                principalCovered
                        && interestCovered
                        && penaltyCovered;

        // ============================================================
        // UPDATE PAYMENT RECORD
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

        installment.setOutstandingAfter(
                newBalance
        );

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

        // ============================================================
        // CRITICAL:
        //
        // Every successful interest calculation/payment advances
        // the loan interest clock.
        //
        // Same-day second payment:
        //
        // previous = 10:01
        // current  = 10:05
        // elapsed calendar days = 0
        //
        // Next day:
        //
        // previous = 10:05
        // current  = next day
        // elapsed calendar days = 1
        // ============================================================

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

        loan.setTotalPaid(
                newTotalPaid
        );

        loan.setOutstandingBalance(
                newBalance
        );

        loan.setLastPaymentDate(
                today
        );

        // ============================================================
        // LOAN FULLY PAID
        // ============================================================

        if (loanFullyPaid) {

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

                /*
                 * IMPORTANT:
                 *
                 * Do NOT copy the old cycle's interest into the
                 * next cycle.
                 *
                 * The next installment starts with zero current-cycle
                 * interest and receives new daily interest on its
                 * first payment.
                 */
                log.info(
                        "Interest cycle completed. " +
                                "Next cycle will start with fresh interest state. " +
                                "loanId={}, completedInstallment={}, " +
                                "nextDueDate={}",
                        loanId,
                        installment.getInstallmentNumber(),
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
        //
        // FULL ORIGINAL PAYMENT AMOUNT IS SENT.
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
                        + ", total cycle interest: "
                        + totalCycleInterestDue
                        + ", interest paid this payment: "
                        + interestPaidThisPayment
                        + ", total interest paid this cycle: "
                        + totalInterestPaid
                        + ", principal paid: "
                        + principalPaidThisPayment
                        + ", penalty days: "
                        + daysLate
                        + ", penalty paid: "
                        + penaltyPaidThisPayment
                        + ", total penalty: "
                        + totalPenalty
                        + ", remaining interest: "
                        + remainingInterestAfterPayment
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
                        "penaltyPaid={}, penaltyDays={}, " +
                        "overpayment={}, outstandingBalance={}, " +
                        "cycleCompleted={}, loanFullyPaid={}, loanStatus={}",
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
                interestAlreadyPaid,
                interestPaidThisPayment,
                totalInterestPaid,
                principalPaidThisPayment,
                totalPrincipalPaid,
                penaltyPaidThisPayment,
                daysLate,
                overpayment,
                newBalance,
                cycleCompleted,
                loanFullyPaid,
                loan.getStatus()
        );

        return installment;
    }

    // ================================================================
    // FIRST INTEREST CALCULATION - PUBLIC TEST COMPATIBILITY
    // ================================================================

    /**
     * Kept public because PaymentServiceTest directly calls this method.
     *
     * This method answers:
     *
     * "Has this loan EVER had an actual payment?"
     *
     * It is intentionally NOT used to determine whether the CURRENT
     * monthly interest cycle is new.
     *
     * Current-cycle detection is handled by:
     *
     * isFirstInterestCalculationForCycle(...)
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

            if (amountPaid.compareTo(
                    BigDecimal.ZERO
            ) > 0) {

                return false;
            }
        }

        return true;
    }

    // ================================================================
    // CURRENT CYCLE FIRST INTEREST CALCULATION
    // ================================================================

    /**
     * Determines whether the CURRENT payment cycle has received its
     * first interest calculation.
     *
     * This is deliberately based on the current installment, not the
     * entire loan history.
     *
     * Example:
     *
     * Installment 1:
     * amountPaid = 1,000,000
     * interestCalculationDate = 10:01
     * paid = true
     *
     * Installment 2:
     * amountPaid = 0
     * interestCalculationDate = null
     * paid = false
     *
     * Result:
     *
     * true
     *
     * even though the loan already has payment history.
     */
    private boolean isFirstInterestCalculationForCycle(
            Payment installment
    ) {

        if (installment == null) {
            return true;
        }

        BigDecimal amountPaid =
                safe(
                        installment.getAmountPaidDecimal()
                );

        LocalDateTime interestTimestamp =
                installment.getInterestCalculationDate();

        BigDecimal cycleInterestDue =
                safe(
                        installment.getCycleInterestDueDecimal()
                );

        BigDecimal cycleInterestRemaining =
                safe(
                        installment.getCycleInterestRemainingDecimal()
                );

        BigDecimal interestPaid =
                safe(
                        installment.getInterestComponentDecimal()
                );

        boolean noPaymentYet =
                amountPaid.compareTo(
                        ZERO
                ) <= 0;

        boolean noInterestTimestamp =
                interestTimestamp == null;

        boolean noCycleInterest =
                cycleInterestDue.compareTo(ZERO) <= 0
                        && cycleInterestRemaining.compareTo(ZERO) <= 0
                        && interestPaid.compareTo(ZERO) <= 0;

        boolean result =
                noPaymentYet
                        && noInterestTimestamp
                        && noCycleInterest;

        log.info(
                "Current interest cycle check. " +
                        "installment={}, paymentId={}, amountPaid={}, " +
                        "interestTimestamp={}, cycleInterestDue={}, " +
                        "cycleInterestRemaining={}, interestPaid={}, " +
                        "firstCurrentCycleCalculation={}",
                installment.getInstallmentNumber(),
                installment.getId(),
                amountPaid,
                interestTimestamp,
                cycleInterestDue,
                cycleInterestRemaining,
                interestPaid,
                result
        );

        return result;
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
    // FIND LATEST INTEREST TIMESTAMP
    // ================================================================

    private LocalDateTime findLatestInterestCalculationTimestamp(
            List<Payment> payments,
            Loan loan
    ) {

        LocalDateTime latest =
                null;

        if (payments != null
                && !payments.isEmpty()) {

            for (Payment payment : payments) {

                if (payment == null) {
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
    // ACTUAL DAILY INTEREST DAYS
    // ================================================================

    private long calculateActualInterestDays(
            LocalDateTime interestStart,
            LocalDateTime now,
            Payment installment,
            Loan loan,
            boolean firstInterestCalculation
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

        /*
         * FIRST PAYMENT OF A CYCLE
         *
         * Even when the payment occurs on the same calendar day
         * as the previous cycle's timestamp, the new cycle receives
         * one minimum interest day.
         *
         * Example:
         *
         * Previous cycle interest timestamp:
         * 10 Aug 10:01
         *
         * New monthly cycle first payment:
         * 10 Aug 10:05
         *
         * calendarDays = 0
         *
         * current cycle interest = 1 day
         *
         * This is the behavior the previous implementation was missing.
         */
        if (firstInterestCalculation
                && calendarDays <= 0) {

            log.info(
                    "FIRST INTEREST DAY OF NEW CYCLE. " +
                            "loanId={}, installment={}, " +
                            "interestStart={}, paymentTime={}, " +
                            "calendarDays=0, effectiveInterestDays=1",
                    loan != null
                            ? loan.getId()
                            : null,
                    installment != null
                            ? installment.getInstallmentNumber()
                            : null,
                    interestStart,
                    now
            );

            return 1L;
        }

        long effectiveDays =
                Math.max(
                        0L,
                        calendarDays
                );

        log.info(
                "DAILY INTEREST CALCULATION. " +
                        "loanId={}, installment={}, " +
                        "firstInterestCalculation={}, " +
                        "interestStart={}, paymentTime={}, " +
                        "calendarDays={}, effectiveInterestDays={}",
                loan != null
                        ? loan.getId()
                        : null,
                installment != null
                        ? installment.getInstallmentNumber()
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
        // MONTHLY RATE -> DAILY RATE
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
        // ANNUAL RATE -> DAILY RATE
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
                "Calculated DAILY interest. " +
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