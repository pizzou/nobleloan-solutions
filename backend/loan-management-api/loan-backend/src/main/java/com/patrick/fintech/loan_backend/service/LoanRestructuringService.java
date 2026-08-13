package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanRestructuringService {

    private final LoanRepository loanRepo;
    private final PaymentRepository paymentRepo;
    private final PaymentScheduleService paymentScheduleService;
    private final AuditService auditService;
    private final WebhookService webhookService;
    private final MailService mailService;
    private final SmsService smsService;

    private static final int MIN_LOAN_DURATION_MONTHS = 1;
    private static final int MAX_LOAN_DURATION_MONTHS = 6;

    private static final BigDecimal MONTHLY_INTEREST_RATE =
            new BigDecimal("5.00");

    private static final BigDecimal MONTHLY_MANAGEMENT_FEE_RATE =
            new BigDecimal("5.00");


    private static final BigDecimal TOTAL_MONTHLY_CHARGE_RATE =
            MONTHLY_INTEREST_RATE.add(
                    MONTHLY_MANAGEMENT_FEE_RATE
            );

    /**
     * One-time processing fee.
     *
     * This fee was already charged at disbursement and therefore
     * is NOT charged again during restructuring.
     */
    private static final BigDecimal PROCESSING_FEE_RATE =
            new BigDecimal("2.00");

    private static final BigDecimal ONE_HUNDRED =
            new BigDecimal("100");

    private static final BigDecimal ZERO =
            BigDecimal.ZERO.setScale(
                    2,
                    RoundingMode.HALF_UP
            );

    private static final BigDecimal ONE =
            BigDecimal.ONE;

    private static final MathContext CALCULATION_CONTEXT =
            MathContext.DECIMAL128;

    // ================================================================
    // RESTRUCTURE
    // ================================================================

    /**
     * Restructure an existing active/overdue/defaulted loan.
     *
     * Platform rules remain fixed:
     *
     * Interest          = 5% monthly
     * Management fee    = 5% monthly
     * Combined charge   = 10% monthly
     * Processing fee    = 2% one-time
     * Maximum term      = 6 months
     *
     * The 2% processing fee is NOT charged again.
     *
     * The restructuring uses the CURRENT outstanding principal as
     * the balance being rescheduled.
     */
    @Transactional
    public Loan restructure(
            Long loanId,
            Long orgId,
            User officer,
            int newMonths,
            Double newRate,
            String reason
    ) {

        Loan loan =
                get(
                        loanId,
                        orgId
                );

        validateOfficer(
                officer,
                orgId
        );

        if (
                loan.getStatus() != LoanStatus.ACTIVE
                        && loan.getStatus() != LoanStatus.OVERDUE
                        && loan.getStatus() != LoanStatus.DEFAULTED
                        && loan.getStatus() != LoanStatus.RESTRUCTURED
        ) {

            throw new RuntimeException(
                    "Only ACTIVE, OVERDUE, DEFAULTED, or RESTRUCTURED loans can be restructured"
            );
        }

        validateDuration(
                newMonths
        );

        if (
                reason == null
                        || reason.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Restructuring reason is required"
            );
        }

        String trimmedReason =
                reason.trim();

        // ============================================================
        // PREVIOUS TERMS
        // ============================================================

        BigDecimal previousRate =
                safe(
                        loan.getInterestRateDecimal()
                );

        BigDecimal previousManagementRate =
                safe(
                        loan.getManagementFeeRateDecimal()
                );

        int previousMonths =
                loan.getDurationMonths() != null
                        ? loan.getDurationMonths()
                        : MIN_LOAN_DURATION_MONTHS;

        BigDecimal previousOutstanding =
                safe(
                        loan.getOutstandingBalanceDecimal()
                );

        if (
                previousOutstanding.compareTo(
                        ZERO
                ) <= 0
        ) {

            throw new IllegalStateException(
                    "Cannot restructure a loan with no outstanding principal balance"
            );
        }

        // ============================================================
        // BACKWARD-COMPATIBLE NEW RATE VALIDATION
        // ============================================================

        /*
         * The method retains newRate because existing callers may
         * still send it.
         *
         * The platform rate is fixed at 5% monthly, so callers may
         * only provide 5.0 or null.
         */
        if (newRate != null) {

            if (
                    Double.isNaN(newRate)
                            || Double.isInfinite(newRate)
            ) {

                throw new IllegalArgumentException(
                        "Invalid new interest rate"
                );
            }

            BigDecimal requestedRate =
                    money(
                            BigDecimal.valueOf(
                                    newRate
                            )
                    );

            if (
                    requestedRate.compareTo(
                            MONTHLY_INTEREST_RATE
                    ) != 0
            ) {

                throw new IllegalArgumentException(
                        "Loan interest rate is fixed at "
                                + MONTHLY_INTEREST_RATE
                                + "% per month"
                );
            }
        }

        // ============================================================
        // APPLY FIXED PLATFORM TERMS
        // ============================================================

        loan.setInterestRate(
                MONTHLY_INTEREST_RATE
        );

        loan.setManagementFeeRate(
                MONTHLY_MANAGEMENT_FEE_RATE
        );

        loan.setProcessingFeeRate(
                PROCESSING_FEE_RATE
        );

        loan.setInterestRateType(
                "MONTHLY"
        );

        loan.setDurationMonths(
                newMonths
        );

        // ============================================================
        // PROCESSING FEE
        // ============================================================

        /*
         * IMPORTANT:
         *
         * The processing fee is one-time only.
         *
         * We preserve the existing processing fee and paid state.
         * We do NOT charge another 2% during restructuring.
         */
        if (
                loan.getProcessingFeeDecimal() == null
                        || loan.getProcessingFeeDecimal()
                        .compareTo(ZERO) < 0
        ) {

            BigDecimal processingFee =
                    money(
                            previousOutstanding
                                    .multiply(
                                            PROCESSING_FEE_RATE
                                    )
                                    .divide(
                                            ONE_HUNDRED,
                                            16,
                                            RoundingMode.HALF_UP
                                    )
                    );

            loan.setProcessingFee(
                    processingFee
            );
        }

        // ============================================================
        // INTERNAL NOTE
        // ============================================================

        String previousNotes =
                loan.getInternalNotes();

        String restructureNote =
                "[RESTRUCTURED] "
                        + trimmedReason
                        + " | Previous: "
                        + previousMonths
                        + "mo @ "
                        + previousRate
                        + "% interest + "
                        + previousManagementRate
                        + "% management"
                        + " | New: "
                        + newMonths
                        + "mo @ "
                        + MONTHLY_INTEREST_RATE
                        + "% interest + "
                        + MONTHLY_MANAGEMENT_FEE_RATE
                        + "% management"
                        + " | Outstanding principal at restructuring: "
                        + previousOutstanding;

        loan.setInternalNotes(
                previousNotes == null
                        || previousNotes.isBlank()
                        ? restructureNote
                        : previousNotes
                        + " | "
                        + restructureNote
        );

        // ============================================================
        // STATUS
        // ============================================================

        /*
         * Keep the loan ACTIVE after restructuring so that the
         * PaymentService can continue accepting payments.
         *
         * The restructuring itself is permanently recorded through
         * the audit entry and internal note.
         */
        loan.setStatus(
                LoanStatus.ACTIVE
        );

        // ============================================================
        // REMOVE FUTURE PAYMENT ROWS
        // ============================================================

        List<Payment> futurePayments =
                paymentRepo
                        .findByLoanId(
                                loan.getId()
                        )
                        .stream()
                        .filter(
                                payment ->
                                        payment != null
                                                && !Boolean.TRUE.equals(
                                                payment.getPaid()
                                        )
                        )
                        .toList();

        if (!futurePayments.isEmpty()) {

            paymentRepo.deleteAll(
                    futurePayments
            );

            paymentRepo.flush();
        }

        // ============================================================
        // SAVE UPDATED LOAN
        // ============================================================

        Loan saved =
                loanRepo.save(
                        loan
                );

        // ============================================================
        // GENERATE NEW SCHEDULE
        // ============================================================

        /*
         * PaymentScheduleService remains the application's
         * authoritative schedule service.
         *
         * The loan already contains the fixed 5% monthly interest
         * and 5% monthly management fee configuration.
         */
        paymentScheduleService.generateSchedule(
                saved
        );

        // ============================================================
        // REFRESH NEXT PAYMENT
        // ============================================================

        refreshNextPayment(
                saved
        );

        Loan finalSaved =
                loanRepo.save(
                        saved
                );

        // ============================================================
        // AUDIT
        // ============================================================

        audit(
                finalSaved.getOrganization(),
                officer,
                "LOAN_RESTRUCTURED",
                loanId,
                "Loan restructured: "
                        + trimmedReason
                        + " | Previous term="
                        + previousMonths
                        + " months"
                        + " | New term="
                        + newMonths
                        + " months"
                        + " | Previous interest="
                        + previousRate
                        + "% monthly"
                        + " | New interest="
                        + MONTHLY_INTEREST_RATE
                        + "% monthly"
                        + " | Previous management="
                        + previousManagementRate
                        + "% monthly"
                        + " | New management="
                        + MONTHLY_MANAGEMENT_FEE_RATE
                        + "% monthly"
                        + " | Processing fee="
                        + PROCESSING_FEE_RATE
                        + "% one-time"
        );

        // ============================================================
        // WEBHOOK
        // ============================================================

        webhookService.dispatch(
                finalSaved.getOrganization(),
                "LOAN_RESTRUCTURED",
                finalSaved
        );

        // ============================================================
        // NOTIFICATIONS
        // ============================================================

        notify(
                finalSaved,
                () ->
                        mailService.sendLoanRestructured(
                                finalSaved,
                                trimmedReason
                        ),
                "Your loan "
                        + finalSaved.getReferenceNumber()
                        + " has been restructured. New term: "
                        + finalSaved.getDurationMonths()
                        + " months. Monthly interest: "
                        + MONTHLY_INTEREST_RATE
                        + "% and management fee: "
                        + MONTHLY_MANAGEMENT_FEE_RATE
                        + "%."
        );

        return finalSaved;
    }

    // ================================================================
    // WRITE OFF
    // ================================================================

    @Transactional
    public Loan writeOff(
            Long loanId,
            Long orgId,
            User officer,
            String reason
    ) {

        Loan loan =
                get(
                        loanId,
                        orgId
                );

        validateOfficer(
                officer,
                orgId
        );

        if (
                loan.getStatus() == LoanStatus.PAID
                        || loan.getStatus() == LoanStatus.CLOSED
        ) {

            throw new RuntimeException(
                    "Cannot write off a PAID or CLOSED loan"
            );
        }

        if (
                reason == null
                        || reason.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Write-off reason is required"
            );
        }

        String trimmedReason =
                reason.trim();

        BigDecimal outstanding =
                safe(
                        loan.getOutstandingBalanceDecimal()
                );

        if (
                outstanding.compareTo(
                        ZERO
                ) <= 0
        ) {

            throw new IllegalStateException(
                    "Cannot write off a loan with no outstanding principal"
            );
        }

        loan.setStatus(
                LoanStatus.WRITTEN_OFF
        );

        loan.setOutstandingBalance(
                ZERO
        );

        loan.setNextDueDate(
                null
        );

        loan.setNextPaymentDate(
                null
        );

        loan.setNextInstallmentAmount(
                ZERO
        );

        String previousNotes =
                loan.getInternalNotes();

        String writeOffNote =
                "[WRITTEN OFF] "
                        + trimmedReason
                        + " | Amount: "
                        + loan.getCurrency()
                        + " "
                        + outstanding
                        + " | "
                        + LocalDate.now();

        loan.setInternalNotes(
                previousNotes == null
                        || previousNotes.isBlank()
                        ? writeOffNote
                        : previousNotes
                        + " | "
                        + writeOffNote
        );

        Loan saved =
                loanRepo.save(
                        loan
                );

        // ============================================================
        // REMOVE UNPAID FUTURE PAYMENTS
        // ============================================================

        List<Payment> pendingPayments =
                paymentRepo
                        .findByLoanId(
                                saved.getId()
                        )
                        .stream()
                        .filter(
                                payment ->
                                        payment != null
                                                && !Boolean.TRUE.equals(
                                                payment.getPaid()
                                        )
                        )
                        .toList();

        if (!pendingPayments.isEmpty()) {

            paymentRepo.deleteAll(
                    pendingPayments
            );

            paymentRepo.flush();
        }

        // ============================================================
        // AUDIT
        // ============================================================

        audit(
                saved.getOrganization(),
                officer,
                "LOAN_WRITTEN_OFF",
                loanId,
                "Written off "
                        + saved.getCurrency()
                        + " "
                        + outstanding
                        + " | "
                        + trimmedReason
        );

        // ============================================================
        // WEBHOOK
        // ============================================================

        webhookService.dispatch(
                saved.getOrganization(),
                "LOAN_WRITTEN_OFF",
                saved
        );

        // ============================================================
        // NOTIFICATION
        // ============================================================

        notify(
                saved,
                () ->
                        mailService.sendLoanWrittenOff(
                                saved,
                                trimmedReason
                        ),
                "There is an update on your loan "
                        + saved.getReferenceNumber()
                        + ". Please contact us for details."
        );

        return saved;
    }

    // ================================================================
    // MORATORIUM
    // ================================================================

    @Transactional
    public Loan grantMoratorium(
            Long loanId,
            Long orgId,
            User officer,
            int pauseMonths,
            String reason
    ) {

        Loan loan =
                get(
                        loanId,
                        orgId
                );

        validateOfficer(
                officer,
                orgId
        );

        if (
                loan.getStatus() != LoanStatus.ACTIVE
                        && loan.getStatus() != LoanStatus.OVERDUE
        ) {

            throw new RuntimeException(
                    "Moratorium only applies to ACTIVE or OVERDUE loans"
            );
        }

        if (pauseMonths <= 0) {

            throw new IllegalArgumentException(
                    "Moratorium duration must be greater than zero"
            );
        }

        if (
                reason == null
                        || reason.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Moratorium reason is required"
            );
        }

        int currentDuration =
                loan.getDurationMonths() != null
                        ? loan.getDurationMonths()
                        : MIN_LOAN_DURATION_MONTHS;

        int resultingDuration =
                currentDuration + pauseMonths;

        /*
         * The platform maximum remains six months.
         */
        if (
                resultingDuration
                        > MAX_LOAN_DURATION_MONTHS
        ) {

            throw new IllegalArgumentException(
                    "Moratorium would extend the loan beyond the maximum "
                            + MAX_LOAN_DURATION_MONTHS
                            + " months. Current term="
                            + currentDuration
                            + ", requested pause="
                            + pauseMonths
            );
        }

        String trimmedReason =
                reason.trim();

        // ============================================================
        // MOVE UNPAID PAYMENT DATES
        // ============================================================

        List<Payment> unpaidPayments =
                paymentRepo
                        .findByLoanId(
                                loanId
                        )
                        .stream()
                        .filter(
                                payment ->
                                        payment != null
                                                && !Boolean.TRUE.equals(
                                                payment.getPaid()
                                        )
                        )
                        .toList();

        for (Payment payment : unpaidPayments) {

            if (payment.getDueDate() != null) {

                payment.setDueDate(
                        payment.getDueDate()
                                .plusMonths(
                                        pauseMonths
                                )
                );

                paymentRepo.save(
                        payment
                );
            }
        }

        // ============================================================
        // UPDATE LOAN DATES
        // ============================================================

        if (loan.getMaturityDate() != null) {

            loan.setMaturityDate(
                    loan.getMaturityDate()
                            .plusMonths(
                                    pauseMonths
                            )
            );
        }

        if (loan.getNextDueDate() != null) {

            loan.setNextDueDate(
                    loan.getNextDueDate()
                            .plusMonths(
                                    pauseMonths
                            )
            );
        }

        if (loan.getNextPaymentDate() != null) {

            loan.setNextPaymentDate(
                    loan.getNextPaymentDate()
                            .plusMonths(
                                    pauseMonths
                            )
            );
        }

        loan.setStatus(
                LoanStatus.ACTIVE
        );

        // ============================================================
        // FIXED PLATFORM RATES
        // ============================================================

        loan.setInterestRate(
                MONTHLY_INTEREST_RATE
        );

        loan.setManagementFeeRate(
                MONTHLY_MANAGEMENT_FEE_RATE
        );

        loan.setProcessingFeeRate(
                PROCESSING_FEE_RATE
        );

        loan.setInterestRateType(
                "MONTHLY"
        );

        // ============================================================
        // INTERNAL NOTE
        // ============================================================

        String previousNotes =
                loan.getInternalNotes();

        String moratoriumNote =
                "[MORATORIUM "
                        + pauseMonths
                        + "mo] "
                        + trimmedReason;

        loan.setInternalNotes(
                previousNotes == null
                        || previousNotes.isBlank()
                        ? moratoriumNote
                        : previousNotes
                        + " | "
                        + moratoriumNote
        );

        Loan saved =
                loanRepo.save(
                        loan
                );

        // ============================================================
        // AUDIT
        // ============================================================

        audit(
                saved.getOrganization(),
                officer,
                "MORATORIUM_GRANTED",
                loanId,
                pauseMonths
                        + " month moratorium: "
                        + trimmedReason
        );

        // ============================================================
        // WEBHOOK
        // ============================================================

        webhookService.dispatch(
                saved.getOrganization(),
                "MORATORIUM_GRANTED",
                saved
        );

        // ============================================================
        // NOTIFICATION
        // ============================================================

        notify(
                saved,
                () ->
                        mailService.sendMoratoriumGranted(
                                saved,
                                pauseMonths,
                                trimmedReason
                        ),
                "Your payments on loan "
                        + saved.getReferenceNumber()
                        + " are paused for "
                        + pauseMonths
                        + " month(s). Next due date: "
                        + saved.getNextDueDate()
                        + "."
        );

        return saved;
    }

    // ================================================================
    // REFRESH NEXT PAYMENT
    // ================================================================

    private void refreshNextPayment(
            Loan loan
    ) {

        if (loan == null || loan.getId() == null) {
            return;
        }

        Payment next =
                paymentRepo
                        .findByLoanId(
                                loan.getId()
                        )
                        .stream()
                        .filter(
                                payment ->
                                        payment != null
                                                && !Boolean.TRUE.equals(
                                                payment.getPaid()
                                        )
                        )
                        .filter(
                                payment ->
                                        payment.getDueDate() != null
                        )
                        .min(
                                (a, b) ->
                                        a.getDueDate()
                                                .compareTo(
                                                        b.getDueDate()
                                                )
                        )
                        .orElse(null);

        if (next == null) {

            loan.setNextDueDate(null);
            loan.setNextPaymentDate(null);
            loan.setNextInstallmentAmount(ZERO);

            return;
        }

        loan.setNextDueDate(
                next.getDueDate()
        );

        loan.setNextPaymentDate(
                next.getDueDate()
        );

        BigDecimal amount =
                safe(
                        next.getAmountDecimal()
                );

        loan.setNextInstallmentAmount(
                amount
        );

        log.info(
                "Refreshed next payment for loan {}: dueDate={}, amount={}",
                loan.getReferenceNumber(),
                next.getDueDate(),
                amount
        );
    }

    // ================================================================
    // GET LOAN WITH ORGANIZATION SECURITY
    // ================================================================

    private Loan get(
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
                loanRepo
                        .findById(
                                loanId
                        )
                        .orElseThrow(
                                () ->
                                        new RuntimeException(
                                                "Loan not found: "
                                                        + loanId
                                        )
                        );

        if (
                loan.getOrganization() == null
                        || loan.getOrganization().getId() == null
        ) {

            throw new IllegalStateException(
                    "Loan has no valid organization"
            );
        }

        if (
                !loan.getOrganization()
                        .getId()
                        .equals(
                                orgId
                        )
        ) {

            throw new RuntimeException(
                    "Access denied"
            );
        }

        return loan;
    }

    // ================================================================
    // OFFICER VALIDATION
    // ================================================================

    private void validateOfficer(
            User officer,
            Long orgId
    ) {

        if (officer == null) {

            throw new IllegalArgumentException(
                    "Officer is required"
            );
        }

        if (
                officer.getOrganization() == null
                        || officer.getOrganization().getId() == null
        ) {

            throw new IllegalArgumentException(
                    "Officer organization is required"
            );
        }

        if (
                orgId == null
                        || !orgId.equals(
                        officer.getOrganization().getId()
                )
        ) {

            throw new IllegalStateException(
                    "Officer does not belong to the loan organization"
            );
        }
    }

    // ================================================================
    // DURATION VALIDATION
    // ================================================================

    private void validateDuration(
            int months
    ) {

        if (
                months < MIN_LOAN_DURATION_MONTHS
                        || months > MAX_LOAN_DURATION_MONTHS
        ) {

            throw new IllegalArgumentException(
                    "Loan duration must be between "
                            + MIN_LOAN_DURATION_MONTHS
                            + " and "
                            + MAX_LOAN_DURATION_MONTHS
                            + " months"
            );
        }
    }

    // ================================================================
    // MONEY
    // ================================================================

    private BigDecimal money(
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

    private BigDecimal safe(
            BigDecimal value
    ) {

        if (value == null) {
            return ZERO;
        }

        return money(
                value
        );
    }

    // ================================================================
    // AUDIT
    // ================================================================

    private void audit(
            Organization org,
            User user,
            String action,
            Long id,
            String description
    ) {

        auditService.log(
                org,
                user,
                action,
                "LOAN",
                id.toString(),
                description
        );
    }

    // ================================================================
    // NOTIFICATIONS
    // ================================================================

    /**
     * Best-effort email/SMS notification.
     *
     * Notification failures never roll back the loan transaction.
     */
    private void notify(
            Loan loan,
            Runnable sendEmail,
            String smsText
    ) {

        if (
                loan == null
                        || loan.getBorrower() == null
        ) {

            return;
        }

        try {

            if (sendEmail != null) {
                sendEmail.run();
            }

        } catch (Exception e) {

            log.warn(
                    "Loan notification email failed for loan {}",
                    loan.getId(),
                    e
            );
        }

        try {

            String phone =
                    loan.getBorrower().getPhone();

            if (
                    phone != null
                            && !phone.isBlank()
            ) {

                smsService.sendCustom(
                        phone,
                        smsText
                );
            }

        } catch (Exception e) {

            log.warn(
                    "Loan notification SMS failed for loan {}",
                    loan.getId(),
                    e
            );
        }
    }
}