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

    // ============================================================
    // RECORD PAYMENT
    // ============================================================

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

        // ========================================================
        // LOCK LOAN
        // ========================================================

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

        // ========================================================
        // IDEMPOTENCY
        // ========================================================

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

                    return existing;
                }

                throw new IllegalStateException(
                        "Transaction ID "
                                + normalizedTxnId
                                + " has already been used for another loan."
                );
            }
        }

        // ========================================================
        // STATUS
        // ========================================================

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

        // ========================================================
        // LOAD HISTORY
        // ========================================================

        List<Payment> loanPayments =
                paymentRepo.findByLoanId(loanId);

        if (loanPayments == null) {
            loanPayments = List.of();
        }

        // ========================================================
        // FIND CURRENT INSTALLMENT
        // ========================================================

        Optional<Payment> currentOpenInstallment =
                loanPayments.stream()
                        .filter(p -> p != null)
                        .filter(p ->
                                !Boolean.TRUE.equals(
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

        boolean newInstallment = false;

        if (currentOpenInstallment.isPresent()) {

            installment =
                    currentOpenInstallment.get();

        } else {

            int nextNumber =
                    loanPayments.stream()
                            .filter(p -> p != null)
                            .map(Payment::getInstallmentNumber)
                            .filter(n -> n != null)
                            .max(Integer::compareTo)
                            .orElse(0)
                            + 1;

            LocalDate dueDate =
                    loan.getNextDueDate() != null
                            ? loan.getNextDueDate()
                            : today;

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

            newInstallment = true;
        }

        // ========================================================
        // CURRENT INSTALLMENT DUE DATE
        // ========================================================

        LocalDate cycleDueDate =
                installment.getDueDate() != null
                        ? installment.getDueDate()
                        : (
                        loan.getNextDueDate() != null
                                ? loan.getNextDueDate()
                                : today
                );

        // ========================================================
        // LATE DAYS
        // ========================================================

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

        // ========================================================
        // CURRENT INSTALLMENT VALUES
        // ========================================================

        BigDecimal amountPaidSoFar =
                roundMoney(
                        safe(
                                installment.getAmountPaidDecimal()
                        )
                );

        BigDecimal installmentInterestPaid =
                roundMoney(
                        safe(
                                installment.getInterestComponentDecimal()
                        )
                ).max(ZERO);

        BigDecimal cycleInterestDue =
                roundMoney(
                        safe(
                                installment.getCycleInterestDueDecimal()
                        )
                ).max(ZERO);

        BigDecimal cycleInterestRemaining =
                roundMoney(
                        safe(
                                installment.getCycleInterestRemainingDecimal()
                        )
                ).max(ZERO);

        // ========================================================
        // CRITICAL:
        //
        // NEVER USE HISTORICAL INTEREST PAID FROM ANOTHER
        // INSTALLMENT AS CURRENT INSTALLMENT INTEREST PAID.
        // ========================================================

        BigDecimal currentInstallmentInterestPaid =
                installmentInterestPaid;

        // ========================================================
        // PRINCIPAL
        // ========================================================

        BigDecimal currentBalance =
                roundMoney(
                        safe(
                                loan.getOutstandingBalanceDecimal()
                        )
                ).max(ZERO);

        // ========================================================
        // DAILY RATE
        // ========================================================

        BigDecimal dailyRate =
                calculateDailyRate(loan);

        if (currentBalance.compareTo(ZERO) > 0
                && dailyRate.compareTo(ZERO) <= 0) {

            throw new IllegalStateException(
                    "Loan "
                            + loan.getReferenceNumber()
                            + " has outstanding principal "
                            + currentBalance
                            + " but no valid positive interest rate."
            );
        }

        // ========================================================
        // GLOBAL INTEREST ANCHOR
        // ========================================================

        LocalDateTime latestInterestTimestamp =
                findLatestInterestCalculationTimestamp(
                        loanPayments,
                        loan
                );

        LocalDateTime interestStart;

        if (latestInterestTimestamp != null) {

            interestStart =
                    latestInterestTimestamp;

        } else {

            interestStart =
                    loan.getDisbursedAt() != null
                            ? loan.getDisbursedAt()
                            : (
                            loan.getStartDate() != null
                                    ? loan.getStartDate().atStartOfDay()
                                    : now
                    );
        }

        if (interestStart.isAfter(now)) {
            interestStart = now;
        }

        // ========================================================
        // DETERMINE WHETHER THIS IS THE FIRST EVER INTEREST
        // EVENT.
        //
        // IMPORTANT:
        // This is based on the existence of an interest timestamp,
        // NOT whether a payment has amountPaid > 0.
        // ========================================================

        boolean firstInterestEvent =
                latestInterestTimestamp == null
                        || !hasInterestCalculationTimestamp(
                        loanPayments
                );

        // ========================================================
        // INTEREST DAYS
        // ========================================================

        long interestDays =
                calculateActualInterestDays(
                        interestStart,
                        now,
                        firstInterestEvent
                );

        // ========================================================
        // NEW INTEREST
        // ========================================================

        BigDecimal newlyAccruedInterest =
                calculateNewInterest(
                        currentBalance,
                        dailyRate,
                        interestDays
                );

        // ========================================================
        // CURRENT CYCLE INTEREST
        //
        // New installment starts with ZERO historical interest.
        //
        // Existing installment preserves its own obligation.
        // ========================================================

        BigDecimal totalCycleInterestDue;

        if (newInstallment) {

            totalCycleInterestDue =
                    newlyAccruedInterest;

        } else {

            totalCycleInterestDue =
                    roundMoney(
                            cycleInterestDue
                                    .add(
                                            newlyAccruedInterest
                                    )
                    );
        }

        // ========================================================
        // NEVER ALLOW CURRENT CYCLE OBLIGATION TO DISAPPEAR
        // ========================================================

        if (!newInstallment
                && cycleInterestRemaining.compareTo(ZERO) > 0
                && totalCycleInterestDue.compareTo(
                cycleInterestRemaining
        ) < 0) {

            totalCycleInterestDue =
                    cycleInterestRemaining
                            .add(
                                    currentInstallmentInterestPaid
                            );
        }

        totalCycleInterestDue =
                roundMoney(
                        totalCycleInterestDue
                );

        // ========================================================
        // INTEREST REMAINING
        // ========================================================

        BigDecimal interestRemainingBeforePayment =
                roundMoney(
                        totalCycleInterestDue
                                .subtract(
                                        currentInstallmentInterestPaid
                                )
                                .max(ZERO)
                );

        // ========================================================
        // PENALTY
        // ========================================================

        BigDecimal existingPenalty =
                roundMoney(
                        safe(
                                installment.getPenaltyDecimal()
                        )
                ).max(ZERO);

        BigDecimal dailyPenaltyRate =
                DEFAULT_MONTHLY_PENALTY_RATE
                        .divide(
                                THIRTY,
                                16,
                                RoundingMode.HALF_UP
                        );

        BigDecimal calculatedPenalty =
                ZERO;

        if (daysLate > 0
                && currentBalance.compareTo(ZERO) > 0) {

            calculatedPenalty =
                    roundMoney(
                            currentBalance
                                    .multiply(
                                            dailyPenaltyRate
                                    )
                                    .multiply(
                                            BigDecimal.valueOf(daysLate))
                    );
        }

        BigDecimal totalPenalty =
                calculatedPenalty.max(
                        existingPenalty
                );

        totalPenalty =
                roundMoney(totalPenalty);

        BigDecimal penaltyRemaining =
                totalPenalty;

        // ========================================================
        // PAYMENT ALLOCATION
        // ========================================================

        BigDecimal paymentRemaining =
                amount;

        // ========================================================
        // 1. PENALTY
        // ========================================================

        BigDecimal penaltyPaid =
                roundMoney(
                        paymentRemaining.min(
                                penaltyRemaining
                        )
                );

        paymentRemaining =
                roundMoney(
                        paymentRemaining
                                .subtract(
                                        penaltyPaid
                                )
                                .max(ZERO)
                );

        // ========================================================
        // 2. INTEREST
        // ========================================================

        BigDecimal interestPaid =
                roundMoney(
                        paymentRemaining.min(
                                interestRemainingBeforePayment
                        )
                );

        paymentRemaining =
                roundMoney(
                        paymentRemaining
                                .subtract(
                                        interestPaid
                                )
                                .max(ZERO)
                );

        // ========================================================
        // 3. PRINCIPAL
        // ========================================================

        BigDecimal principalPaid =
                roundMoney(
                        paymentRemaining.min(
                                currentBalance
                        )
                );

        paymentRemaining =
                roundMoney(
                        paymentRemaining
                                .subtract(
                                        principalPaid
                                )
                                .max(ZERO)
                );

        // ========================================================
        // 4. OVERPAYMENT
        // ========================================================

        BigDecimal overpayment =
                roundMoney(
                        paymentRemaining
                );

        // ========================================================
        // NEW BALANCE
        // ========================================================

        BigDecimal newBalance =
                roundMoney(
                        currentBalance
                                .subtract(
                                        principalPaid
                                )
                                .max(ZERO)
                );

        // ========================================================
        // TOTAL CURRENT INSTALLMENT VALUES
        // ========================================================

        BigDecimal existingPrincipal =
                roundMoney(
                        safe(
                                installment
                                        .getPrincipalComponentDecimal()
                        )
                ).max(ZERO);

        BigDecimal totalPrincipalPaid =
                roundMoney(
                        existingPrincipal
                                .add(
                                        principalPaid
                                )
                );

        BigDecimal totalInterestPaid =
                roundMoney(
                        currentInstallmentInterestPaid
                                .add(
                                        interestPaid
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

        // ========================================================
        // COMPLETION
        // ========================================================

        boolean interestCovered =
                remainingInterestAfterPayment
                        .compareTo(ONE_CENT) <= 0;

        boolean penaltyCovered =
                totalPenalty
                        .subtract(penaltyPaid)
                        .compareTo(ONE_CENT) <= 0;

        boolean principalCovered =
                newBalance.compareTo(ONE_CENT) <= 0;

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

        if (!principalCovered) {

            cycleCompleted =
                    scheduledAmountCovered
                            && interestCovered
                            && penaltyCovered;
        }

        // ========================================================
        // UPDATE PAYMENT
        // ========================================================

        BigDecimal newAmountPaid =
                roundMoney(
                        amountPaidSoFar
                                .add(amount)
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

        installment.setPaymentMethod(method);
        installment.setTransactionId(normalizedTxnId);
        installment.setChannel(channel);
        installment.setNotes(notes);

        if (recordedBy != null) {
            installment.setRecordedBy(recordedBy);
        }

        installment.setPaidDate(today);

        // ========================================================
        // CRITICAL:
        //
        // UPDATE THE GLOBAL INTEREST CLOCK ONLY AFTER CALCULATING
        // THE CURRENT PAYMENT'S INTEREST.
        // ========================================================

        installment.setInterestCalculationDate(now);

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

        // ========================================================
        // SAVE PAYMENT
        // ========================================================

        try {

            installment =
                    paymentRepo.save(installment);

        } catch (DataIntegrityViolationException e) {

            if (normalizedTxnId != null) {

                Optional<Payment> concurrentPayment =
                        paymentRepo
                                .findByOrganization_IdAndTransactionId(
                                        organizationId,
                                        normalizedTxnId
                                );

                if (concurrentPayment.isPresent()) {
                    return concurrentPayment.get();
                }
            }

            throw e;
        }

        // ========================================================
        // LOAN TOTALS
        // ========================================================

        BigDecimal oldTotalPaid =
                roundMoney(
                        safe(
                                loan.getTotalPaidDecimal()
                        )
                );

        loan.setTotalPaid(
                roundMoney(
                        oldTotalPaid.add(amount)
                )
        );

        loan.setOutstandingBalance(
                newBalance
        );

        loan.setLastPaymentDate(
                today
        );

        // ========================================================
        // LOAN STATUS
        // ========================================================

        if (principalCovered
                && interestCovered
                && penaltyCovered) {

            loan.setStatus(
                    LoanStatus.PAID
            );

            loan.setNextDueDate(null);
            loan.setNextPaymentDate(null);
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

                loan.setNextDueDate(nextDue);
                loan.setNextPaymentDate(nextDue);

            } else {

                loan.setNextDueDate(cycleDueDate);
                loan.setNextPaymentDate(cycleDueDate);
            }
        }

        loanRepo.save(loan);

        // ========================================================
        // ACCOUNTING
        // ========================================================

        accountingService.postPaymentReceived(
                installment,
                amount,
                principalPaid,
                interestPaid,
                penaltyPaid,
                overpayment
        );

        // ========================================================
        // AUDIT
        // ========================================================

        audit(
                loan.getOrganization(),
                recordedBy,
                "PAYMENT_RECORDED",
                "PAYMENT",
                installment.getId() != null
                        ? installment.getId().toString()
                        : "UNKNOWN",
                "Payment "
                        + amount
                        + " on loan "
                        + loan.getReferenceNumber()
                        + " | interestStart="
                        + interestStart
                        + " | paymentTime="
                        + now
                        + " | interestDays="
                        + interestDays
                        + " | dailyRate="
                        + dailyRate
                        + " | newlyAccruedInterest="
                        + newlyAccruedInterest
                        + " | cycleInterestDue="
                        + totalCycleInterestDue
                        + " | interestPaid="
                        + interestPaid
                        + " | principalPaid="
                        + principalPaid
                        + " | remainingInterest="
                        + remainingInterestAfterPayment
                        + " | outstanding="
                        + newBalance
        );

        // ========================================================
        // EMAIL
        // ========================================================

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

        // ========================================================
        // SMS
        // ========================================================

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

        // ========================================================
        // OFFICER NOTIFICATION
        // ========================================================

        if (loan.getLoanOfficer() != null
                && (
                recordedBy == null
                        || loan.getLoanOfficer().getId() == null
                        || recordedBy.getId() == null
                        || !loan.getLoanOfficer()
                        .getId()
                        .equals(recordedBy.getId())
        )) {

            try {

                notifService.notifyUsers(
                        List.of(loan.getLoanOfficer()),
                        "Payment Received",
                        "A payment of "
                                + loan.getCurrency()
                                + " "
                                + amount
                                + " was recorded on loan "
                                + loan.getReferenceNumber(),
                        "success",
                        "/dashboard/loans/"
                                + loan.getId()
                );

            } catch (Exception e) {

                log.warn(
                        "Payment notification failed for loanId={}",
                        loan.getId(),
                        e
                );
            }
        }

        // ========================================================
        // WEBHOOK
        // ========================================================

        try {

            Map<String, Object> webhook =
                    new HashMap<>();

            webhook.put("paymentId", installment.getId());
            webhook.put("loanId", loan.getId());
            webhook.put("loanReference", loan.getReferenceNumber());
            webhook.put("amount", amount);
            webhook.put("principalPaid", principalPaid);
            webhook.put("interestPaid", interestPaid);
            webhook.put("penaltyPaid", penaltyPaid);
            webhook.put("totalPenalty", totalPenalty);
            webhook.put("interestDays", interestDays);
            webhook.put("dailyInterestRate", dailyRate);
            webhook.put("newlyAccruedInterest", newlyAccruedInterest);
            webhook.put("totalInterestDue", totalCycleInterestDue);
            webhook.put(
                    "remainingInterest",
                    remainingInterestAfterPayment
            );
            webhook.put("outstandingBalance", newBalance);
            webhook.put("overpayment", overpayment);
            webhook.put("paymentMethod", method);
            webhook.put("channel", channel);
            webhook.put("transactionId", normalizedTxnId);
            webhook.put(
                    "paymentReference",
                    installment.getPaymentReference()
            );
            webhook.put("paymentTimestamp", now.toString());
            webhook.put(
                    "interestCalculationStart",
                    interestStart.toString()
            );
            webhook.put(
                    "interestCalculationDate",
                    now.toString()
            );
            webhook.put(
                    "installmentNumber",
                    installment.getInstallmentNumber()
            );
            webhook.put(
                    "paymentStatus",
                    installment.getStatus() != null
                            ? installment.getStatus().name()
                            : null
            );
            webhook.put(
                    "loanStatus",
                    loan.getStatus() != null
                            ? loan.getStatus().name()
                            : null
            );

            if (loan.getBorrower() != null) {
                webhook.put(
                        "borrowerId",
                        loan.getBorrower().getId()
                );
            }

            webhookService.dispatch(
                    loan.getOrganization(),
                    "PAYMENT_MADE",
                    webhook
            );

        } catch (Exception e) {

            log.error(
                    "Payment webhook failed for loanId={}, paymentId={}",
                    loan.getId(),
                    installment.getId(),
                    e
            );
        }

        // ========================================================
        // FINAL LOG
        // ========================================================

        log.info(
                "Payment successfully recorded. "
                        + "loanId={}, paymentId={}, amount={}, "
                        + "firstInterestEvent={}, "
                        + "interestStart={}, "
                        + "paymentTimestamp={}, "
                        + "interestDays={}, "
                        + "dailyRate={}, "
                        + "newlyAccruedInterest={}, "
                        + "totalCycleInterest={}, "
                        + "interestAlreadyPaidThisInstallment={}, "
                        + "interestPaidThisPayment={}, "
                        + "totalInterestPaidThisInstallment={}, "
                        + "principalPaidThisPayment={}, "
                        + "totalPrincipalPaidThisInstallment={}, "
                        + "penaltyPaid={}, "
                        + "overpayment={}, "
                        + "outstandingBalance={}, "
                        + "cycleCompleted={}, "
                        + "loanStatus={}",
                loan.getId(),
                installment.getId(),
                amount,
                firstInterestEvent,
                interestStart,
                now,
                interestDays,
                dailyRate,
                newlyAccruedInterest,
                totalCycleInterestDue,
                currentInstallmentInterestPaid,
                interestPaid,
                totalInterestPaid,
                principalPaid,
                totalPrincipalPaid,
                penaltyPaid,
                overpayment,
                newBalance,
                cycleCompleted,
                loan.getStatus()
        );

        return installment;
    }

    // ============================================================
    // FIRST INTEREST CALCULATION
    // ============================================================

    public boolean isFirstInterestCalculation(
            List<Payment> payments
    ) {

        if (payments == null || payments.isEmpty()) {
            return true;
        }

        return !hasInterestCalculationTimestamp(payments);
    }

    // ============================================================
    // HAS INTEREST TIMESTAMP
    // ============================================================

    private boolean hasInterestCalculationTimestamp(
            List<Payment> payments
    ) {

        if (payments == null) {
            return false;
        }

        return payments.stream()
                .filter(p -> p != null)
                .anyMatch(
                        p ->
                                p.getInterestCalculationDate() != null
                );
    }

    // ============================================================
    // INSTALLMENT COMPLETION
    // ============================================================

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

        if (scheduledAmount.compareTo(ZERO) <= 0) {
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

    // ============================================================
    // ORGANIZATION ACCESS
    // ============================================================

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

        if (!loan.getOrganization()
                .getId()
                .equals(
                        recordedBy.getOrganization().getId()
                )) {

            throw new IllegalStateException(
                    "Access denied."
            );
        }
    }

    // ============================================================
    // GET LOAN SCHEDULE
    // ============================================================

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
                                () -> new RuntimeException(
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

        return paymentRepo.findByLoanIdOrderByDueDateAsc(
                loanId
        );
    }

    // ============================================================
    // MARK OVERDUE
    // ============================================================

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

                payment.setLate(true);

                payment.setDaysLate(
                        Math.max(
                                payment.getDaysLate() != null
                                        ? payment.getDaysLate()
                                        : 0,
                                days
                        )
                );

                paymentRepo.save(payment);
            }

            loanRepo.save(loan);
        }
    }

    // ============================================================
    // FIND LATEST INTEREST TIMESTAMP
    // ============================================================

    private LocalDateTime findLatestInterestCalculationTimestamp(
            List<Payment> payments,
            Loan loan
    ) {

        LocalDateTime latest = null;

        if (payments != null) {

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

        return latest;
    }

    // ============================================================
    // ACTUAL DAILY INTEREST DAYS
    // ============================================================

    private long calculateActualInterestDays(
            LocalDateTime interestStart,
            LocalDateTime now,
            boolean firstInterestEvent
    ) {

        if (interestStart == null
                || now == null) {

            return firstInterestEvent
                    ? 1L
                    : 0L;
        }

        if (interestStart.isAfter(now)) {

            return firstInterestEvent
                    ? 1L
                    : 0L;
        }

        long calendarDays =
                ChronoUnit.DAYS.between(
                        interestStart.toLocalDate(),
                        now.toLocalDate()
                );

        /*
         * FIRST INTEREST EVENT:
         *
         * Disbursed:
         * 2026-08-10 10:00
         *
         * First payment:
         * 2026-08-10 10:01
         *
         * Charge 1 day.
         */
        if (firstInterestEvent) {
            return 1L;
        }

        /*
         * SUBSEQUENT PAYMENT:
         *
         * Same calendar day:
         * 0 days.
         *
         * Next calendar day:
         * 1 day.
         *
         * Two calendar days:
         * 2 days.
         */
        return Math.max(
                0L,
                calendarDays
        );
    }

    // ============================================================
    // DAILY INTEREST RATE
    // ============================================================

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

        if (rate.compareTo(ZERO) <= 0) {

            throw new IllegalStateException(
                    "Loan "
                            + loan.getId()
                            + " has no positive interest rate."
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

        if ("MONTHLY".equalsIgnoreCase(rateType)) {

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

        if ("ANNUAL".equalsIgnoreCase(rateType)) {

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

    // ============================================================
    // CALCULATE NEW INTEREST
    // ============================================================

    private BigDecimal calculateNewInterest(
            BigDecimal currentBalance,
            BigDecimal dailyRate,
            long elapsedDays
    ) {

        if (currentBalance == null
                || currentBalance.compareTo(ZERO) <= 0) {

            return ZERO;
        }

        if (dailyRate == null
                || dailyRate.compareTo(ZERO) <= 0) {

            return ZERO;
        }

        if (elapsedDays <= 0) {
            return ZERO;
        }

        return roundMoney(
                currentBalance
                        .multiply(dailyRate)
                        .multiply(
                                BigDecimal.valueOf(
                                        elapsedDays
                                )
                        )
        );
    }

    // ============================================================
    // SAFE
    // ============================================================

    private BigDecimal safe(
            BigDecimal value
    ) {

        if (value == null) {
            return ZERO;
        }

        return value;
    }

    // ============================================================
    // ROUND
    // ============================================================

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

    // ============================================================
    // TRANSACTION ID
    // ============================================================

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

    // ============================================================
    // PAYMENT REFERENCE
    // ============================================================

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

    // ============================================================
    // AUDIT
    // ============================================================

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