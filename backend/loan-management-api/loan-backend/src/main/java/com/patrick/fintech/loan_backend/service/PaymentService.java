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

    private static final BigDecimal ZERO =
            BigDecimal.ZERO.setScale(
                    2,
                    RoundingMode.HALF_UP
            );

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

            throw new IllegalArgumentException(
                    "Loan ID is required"
            );
        }

        if (amount == null
                || amount.compareTo(BigDecimal.ZERO) <= 0) {

            throw new IllegalArgumentException(
                    "Payment amount must be greater than zero"
            );
        }

        amount = roundMoney(amount);

        String normalizedTxnId =
                normalizeTransactionId(txnId);

        Loan loan =
                loanRepo.findByIdForUpdate(loanId)
                        .orElseThrow(
                                () -> new RuntimeException(
                                        "Loan not found: " + loanId
                                )
                        );

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
        // INTEREST STATE
        // ============================================================

        LocalDateTime previousInterestCalculationDate =
                findLatestInterestCalculationTimestamp(
                        loanPayments
                );

        boolean firstInterestCalculation =
                previousInterestCalculationDate == null;

        log.info(
                "Interest state BEFORE payment. " +
                        "loanId={}, firstInterestCalculation={}, " +
                        "latestInterestTimestamp={}, disbursedAt={}, " +
                        "startDate={}, paymentTimestamp={}",
                loanId,
                firstInterestCalculation,
                previousInterestCalculationDate,
                loan.getDisbursedAt(),
                loan.getStartDate(),
                now
        );

        // ============================================================
        // FIND CURRENT PARTIALLY PAID INSTALLMENT
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

        // ============================================================
        // FIND OLDEST UNPAID INSTALLMENT
        // ============================================================

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

        // ============================================================
        // SELECT PAYMENT ROW
        // ============================================================

        if (existingCurrentCycle.isPresent()) {

            installment =
                    existingCurrentCycle.get();

            log.info(
                    "Continuing existing payment cycle. " +
                            "loanId={}, installment={}, paymentId={}",
                    loanId,
                    installment.getInstallmentNumber(),
                    installment.getId()
            );

        } else if (unpaidInstallment.isPresent()) {

            installment =
                    unpaidInstallment.get();

            log.info(
                    "Using unpaid scheduled installment. " +
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
                            .amountPaid(ZERO)
                            .principalComponent(ZERO)
                            .interestComponent(ZERO)
                            .penalty(ZERO)
                            .penaltyPaid(ZERO)
                            .cycleInterestDue(ZERO)
                            .cycleInterestRemaining(ZERO)
                            .interestCalculationDate(null)
                            .paid(false)
                            .status(
                                    Payment.PaymentStatus.PENDING
                            )
                            .build();

            log.info(
                    "Creating new payment cycle. " +
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
        // EXISTING PAYMENT VALUES
        // ============================================================

        BigDecimal amountPaidSoFar =
                roundMoney(
                        safe(
                                installment.getAmountPaidDecimal()
                        )
                );

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

        BigDecimal interestAlreadyPaidThisCycle =
                roundMoney(
                        existingCycleInterestDue
                                .subtract(
                                        existingCycleInterestRemaining
                                )
                                .max(ZERO)
                );

        // ============================================================
        // PENALTY STATE
        // ============================================================

        BigDecimal existingPenaltyAssessed =
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

        if (penaltyAlreadyPaid.compareTo(
                existingPenaltyAssessed
        ) > 0) {

            log.warn(
                    "Payment contains penaltyPaid greater than penalty. " +
                            "loanId={}, paymentId={}, penalty={}, " +
                            "penaltyPaid={}. Capping penaltyPaid to penalty.",
                    loanId,
                    installment.getId(),
                    existingPenaltyAssessed,
                    penaltyAlreadyPaid
            );

            penaltyAlreadyPaid =
                    existingPenaltyAssessed;
        }

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
                            + safe(
                                    loan.getInterestRateDecimal()
                            )
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
        // INTEREST DAYS
        // ============================================================

        long elapsedDays =
                calculateActualInterestDays(
                        interestStartDateTime,
                        now,
                        firstInterestCalculation,
                        loanId
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
        // CURRENT CYCLE INTEREST
        // ============================================================

        BigDecimal totalCycleInterestDue =
                roundMoney(
                        existingCycleInterestDue
                                .add(
                                        newlyAccruedInterest
                                )
                );

        BigDecimal minimumCurrentCycleObligation =
                roundMoney(
                        interestAlreadyPaidThisCycle
                                .add(
                                        existingCycleInterestRemaining
                                )
                );

        if (minimumCurrentCycleObligation.compareTo(
                totalCycleInterestDue
        ) > 0) {

            totalCycleInterestDue =
                    minimumCurrentCycleObligation;
        }

        totalCycleInterestDue =
                roundMoney(totalCycleInterestDue);

        // ============================================================
        // CURRENT CYCLE INTEREST REMAINING
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

        BigDecimal newlyCalculatedPenalty =
                ZERO;

        if (daysLate > 0
                && currentBalance.compareTo(ZERO) > 0) {

            newlyCalculatedPenalty =
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
                existingPenaltyAssessed.max(
                        newlyCalculatedPenalty
                );

        totalPenalty =
                roundMoney(totalPenalty);

        // ============================================================
        // PENALTY REMAINING
        // ============================================================

        BigDecimal penaltyRemainingBeforePayment =
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

        BigDecimal totalPenaltyPaid =
                roundMoney(
                        penaltyAlreadyPaid
                                .add(
                                        penaltyPaidThisPayment
                                )
                );

        if (totalPenaltyPaid.compareTo(totalPenalty) > 0) {

            totalPenaltyPaid =
                    totalPenalty;
        }

        BigDecimal remainingPenaltyAfterPayment =
                roundMoney(
                        totalPenalty
                                .subtract(
                                        totalPenaltyPaid
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
        // CUMULATIVE PRINCIPAL
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

        // ============================================================
        // CUMULATIVE INTEREST
        // ============================================================

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

        // ============================================================
        // REMAINING INTEREST
        // ============================================================

        BigDecimal remainingInterestAfterPayment =
                roundMoney(
                        totalCycleInterestDue
                                .subtract(
                                        interestAlreadyPaidThisCycle
                                                .add(
                                                        interestPaidThisPayment
                                                )
                                )
                                .max(ZERO)
                );

        // ============================================================
        // COMPLETION
        // ============================================================

        boolean penaltyCovered =
                remainingPenaltyAfterPayment
                        .compareTo(ONE_CENT) <= 0;

        boolean interestCovered =
                remainingInterestAfterPayment
                        .compareTo(ONE_CENT) <= 0;

        boolean principalCovered =
                newBalance.compareTo(ONE_CENT) <= 0;

        boolean scheduledAmountCovered =
                isScheduledInstallmentCovered(
                        installment,
                        amountPaidSoFar,
                        amount
                );

        if (overpayment.compareTo(ZERO) > 0
                && !principalCovered) {

            throw new IllegalStateException(
                    "Invalid payment allocation: overpayment exists " +
                            "while principal remains outstanding."
            );
        }

        boolean cycleCompleted;

        if (principalCovered
                && interestCovered
                && penaltyCovered) {

            cycleCompleted = true;

        } else {

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
         * Critical interest anchor.
         *
         * The next payment uses this timestamp as the beginning of the
         * next interest interval.
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
        // UPDATE LOAN DAYS OVERDUE
        // ============================================================

        if (daysLate > 0) {

            int existingLoanDaysOverdue =
                    loan.getDaysOverdue() != null
                            ? loan.getDaysOverdue()
                            : 0;

            loan.setDaysOverdue(
                    Math.max(
                            existingLoanDaysOverdue,
                            daysLate
                    )
            );
        }

        // ============================================================
        // LOAN FULLY PAID
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
                        + ", interest already paid this cycle: "
                        + interestAlreadyPaidThisCycle
                        + ", interest paid this payment: "
                        + interestPaidThisPayment
                        + ", remaining cycle interest: "
                        + remainingInterestAfterPayment
                        + ", principal paid: "
                        + principalPaidThisPayment
                        + ", penalty days: "
                        + daysLate
                        + ", newly calculated penalty: "
                        + newlyCalculatedPenalty
                        + ", total penalty assessed: "
                        + totalPenalty
                        + ", penalty already paid: "
                        + penaltyAlreadyPaid
                        + ", penalty paid this payment: "
                        + penaltyPaidThisPayment
                        + ", total penalty paid: "
                        + totalPenaltyPaid
                        + ", remaining penalty: "
                        + remainingPenaltyAfterPayment
                        + ", outstanding principal: "
                        + newBalance
                        + ", overpayment: "
                        + overpayment
                        + ", transactionId: "
                        + normalizedTxnId
        );

        // ============================================================
        // PAYMENT EVENT
        // ============================================================

        /*
         * The immutable event is published while the current transaction
         * is active.
         *
         * PaymentReceivedEventListener receives it AFTER_COMMIT.
         *
         * Therefore realtime WebSocket notification cannot be sent for
         * a transaction that subsequently rolls back.
         */
        try {

            paymentEventPublisher.publishPaymentReceived(
                    loan,
                    installment,
                    amount,
                    principalPaidThisPayment,
                    interestPaidThisPayment,
                    penaltyPaidThisPayment,
                    newBalance,
                    now
            );

        } catch (Exception e) {

            /*
             * Event publication should not silently destroy the payment
             * transaction because realtime notification is a secondary
             * side effect.
             */
            log.error(
                    "Failed to publish PaymentReceivedEvent. " +
                            "loanId={}, paymentId={}, transactionId={}",
                    loan.getId(),
                    installment.getId(),
                    normalizedTxnId,
                    e
            );

            throw e;
        }

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
                                + ".",
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
                    "organizationId",
                    organizationId
            );

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
                    "penaltyPaidThisPayment",
                    penaltyPaidThisPayment
            );

            paymentWebhook.put(
                    "penaltyPaid",
                    totalPenaltyPaid
            );

            paymentWebhook.put(
                    "totalPenalty",
                    totalPenalty
            );

            paymentWebhook.put(
                    "remainingPenalty",
                    remainingPenaltyAfterPayment
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
                        "totalPenalty={}, " +
                        "penaltyAlreadyPaid={}, " +
                        "penaltyPaidThisPayment={}, " +
                        "totalPenaltyPaid={}, " +
                        "remainingPenalty={}, " +
                        "penaltyDays={}, " +
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
                totalPenalty,
                penaltyAlreadyPaid,
                penaltyPaidThisPayment,
                totalPenaltyPaid,
                remainingPenaltyAfterPayment,
                daysLate,
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
            List<Payment> payments
    ) {

        LocalDateTime latest = null;

        if (payments == null
                || payments.isEmpty()) {

            return null;
        }

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

        return latest;
    }

    // ================================================================
    // ACTUAL INTEREST DAYS
    // ================================================================

    private long calculateActualInterestDays(
            LocalDateTime interestStart,
            LocalDateTime now,
            boolean firstInterestCalculation,
            Long loanId
    ) {

        if (now == null) {
            return 0L;
        }

        if (firstInterestCalculation) {

            log.info(
                    "FIRST INTEREST CHARGE. " +
                            "loanId={}, interestStart={}, paymentTime={}, " +
                            "effectiveInterestDays=1.",
                    loanId,
                    interestStart,
                    now
            );

            return 1L;
        }

        if (interestStart == null) {

            log.warn(
                    "Interest anchor unexpectedly null for subsequent " +
                            "calculation. loanId={}. No new interest charged.",
                    loanId
            );

            return 0L;
        }

        if (interestStart.isAfter(now)) {

            log.warn(
                    "Interest anchor is after payment timestamp. " +
                            "loanId={}, interestStart={}, now={}. " +
                            "No new interest charged.",
                    loanId,
                    interestStart,
                    now
            );

            return 0L;
        }

        /*
         * Important production rule:
         *
         * Interest is based on elapsed calendar dates.
         *
         * Example:
         *
         * Payment 1:
         * 09 Aug 2026 10:00 -> 1 interest day on first calculation.
         *
         * Payment 2:
         * 09 Aug 2026 10:05 -> 0 additional calendar days.
         *
         * Payment 3:
         * 10 Aug 2026 09:00 -> 1 additional calendar day.
         */
        long calendarDays =
                ChronoUnit.DAYS.between(
                        interestStart.toLocalDate(),
                        now.toLocalDate()
                );

        long effectiveDays =
                Math.max(
                        0L,
                        calendarDays
                );

        log.info(
                "SUBSEQUENT INTEREST CALCULATION. " +
                        "loanId={}, interestStart={}, paymentTime={}, " +
                        "calendarDays={}, effectiveInterestDays={}",
                loanId,
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