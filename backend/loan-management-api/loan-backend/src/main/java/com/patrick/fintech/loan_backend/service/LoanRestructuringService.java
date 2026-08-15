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
        private final AccountingService accountingService;

        private static final int MIN_LOAN_DURATION_MONTHS = 1;
        private static final int MAX_LOAN_DURATION_MONTHS = 6;

        /*
         * Platform loan pricing.
         *
         * Interest:
         * 5% monthly, calculated daily using the actual calendar month.
         *
         * Management fee:
         * 5% monthly, calculated daily using the actual calendar month.
         *
         * Processing fee:
         * 2%, one-time at disbursement only.
         */
        private static final BigDecimal MONTHLY_INTEREST_RATE = new BigDecimal("5.00");

        private static final BigDecimal MONTHLY_MANAGEMENT_FEE_RATE = new BigDecimal("5.00");

        private static final BigDecimal PROCESSING_FEE_RATE = new BigDecimal("2.00");

        /*
         * Institution policy:
         *
         * Extension/restructuring fee =
         * 10% of the outstanding principal at the moment
         * the extension is approved/requested.
         *
         * This is NOT principal.
         * This is NOT the normal processing fee.
         */
        private static final BigDecimal EXTENSION_FEE_RATE = new BigDecimal("10.00");

        private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

        private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        // ================================================================
        // EXTENSION / RESTRUCTURING
        // ================================================================

        /**
         * Extend the maturity of an existing loan.
         *
         * Business rule:
         *
         * Extension fee =
         * 10% × outstanding principal at the time of extension.
         *
         * Important:
         *
         * - Existing loan ID remains unchanged.
         * - Principal does NOT increase because of the extension fee.
         * - Processing fee is NOT charged again.
         * - Existing unpaid schedule is rebuilt.
         * - Original extension amount is captured in the audit trail.
         */
        @Transactional
        public Loan extendLoan(
                        Long loanId,
                        Long orgId,
                        User officer,
                        int extensionMonths,
                        String reason) {

                Loan loan = get(loanId, orgId);

                validateOfficer(officer, orgId);

                if (loan.getStatus() != LoanStatus.ACTIVE
                                && loan.getStatus() != LoanStatus.OVERDUE
                                && loan.getStatus() != LoanStatus.DEFAULTED
                                && loan.getStatus() != LoanStatus.RESTRUCTURED) {
                        throw new IllegalStateException(
                                        "Only ACTIVE, OVERDUE, DEFAULTED, or RESTRUCTURED loans can be extended");
                }

                if (extensionMonths <= 0) {
                        throw new IllegalArgumentException(
                                        "Extension duration must be greater than zero");
                }

                if (loan.getDurationMonths() == null
                                || loan.getDurationMonths() <= 0) {
                        throw new IllegalStateException(
                                        "Loan has no valid existing duration");
                }

                int currentDuration = loan.getDurationMonths();

                int newDuration = currentDuration + extensionMonths;

                if (newDuration > MAX_LOAN_DURATION_MONTHS) {
                        throw new IllegalArgumentException(
                                        "Extension would exceed the maximum loan duration of "
                                                        + MAX_LOAN_DURATION_MONTHS
                                                        + " months");
                }

                if (reason == null || reason.isBlank()) {
                        throw new IllegalArgumentException(
                                        "Extension reason is required");
                }

                String trimmedReason = reason.trim();

                /*
                 * Capture the balance BEFORE changing anything.
                 *
                 * This is the amount used to calculate the 10% fee.
                 */
                BigDecimal outstandingAtExtension = safe(loan.getOutstandingBalanceDecimal());

                if (outstandingAtExtension.compareTo(ZERO) <= 0) {
                        throw new IllegalStateException(
                                        "Cannot extend a loan with no outstanding principal");
                }

                /*
                 * 10% extension fee.
                 *
                 * This value is frozen for this extension event.
                 */
                BigDecimal extensionFee = money(
                                outstandingAtExtension
                                                .multiply(EXTENSION_FEE_RATE)
                                                .divide(
                                                                ONE_HUNDRED,
                                                                16,
                                                                RoundingMode.HALF_UP));

                LocalDate previousMaturity = loan.getMaturityDate();

                LocalDate previousNextDue = loan.getNextDueDate();

                /*
                 * Remove unpaid future rows before rebuilding the schedule.
                 */
                List<Payment> futurePayments = paymentRepo
                                .findByLoanId(loan.getId())
                                .stream()
                                .filter(
                                                payment -> payment != null
                                                                && !Boolean.TRUE.equals(
                                                                                payment.getPaid()))
                                .toList();

                if (!futurePayments.isEmpty()) {
                        paymentRepo.deleteAll(futurePayments);
                        paymentRepo.flush();
                }

                /*
                 * Keep the existing principal.
                 *
                 * The extension fee is NOT added to principal.
                 */
                loan.setDurationMonths(newDuration);

                /*
                 * Preserve the loan's contractual pricing.
                 * An extension changes maturity; it must not silently
                 * reprice the existing contract.
                 */
                if (loan.getInterestRateDecimal() == null
                                || loan.getInterestRateDecimal().compareTo(ZERO) <= 0) {
                        loan.setInterestRate(MONTHLY_INTEREST_RATE);
                }

                if (loan.getManagementFeeRateDecimal() == null
                                || loan.getManagementFeeRateDecimal().compareTo(ZERO) < 0) {
                        loan.setManagementFeeRate(MONTHLY_MANAGEMENT_FEE_RATE);
                }

                loan.setInterestRateType(
                                "MONTHLY");

                /*
                 * Processing fee remains unchanged.
                 *
                 * NEVER recalculate 2% here.
                 */
                loan.setProcessingFeeRate(
                                loan.getProcessingFeeRateDecimal() != null
                                                ? loan.getProcessingFeeRateDecimal()
                                                : PROCESSING_FEE_RATE);

                /*
                 * Extend maturity and next due date.
                 *
                 * Schedule generation remains authoritative.
                 */
                if (previousMaturity != null) {
                        loan.setMaturityDate(
                                        previousMaturity.plusMonths(
                                                        extensionMonths));
                }

                if (previousNextDue != null) {
                        loan.setNextDueDate(
                                        previousNextDue.plusMonths(
                                                        extensionMonths));

                        loan.setNextPaymentDate(
                                        previousNextDue.plusMonths(
                                                        extensionMonths));
                }

                /*
                 * Maintain active servicing state.
                 */
                loan.setStatus(
                                LoanStatus.ACTIVE);

                int extensionCount = loan.getExtensionCount() == null
                                ? 0
                                : loan.getExtensionCount();

                loan.setExtensionCount(extensionCount + 1);
                loan.setLastExtensionDate(LocalDate.now());
                loan.setExtensionFeeAssessed(
                                safe(loan.getExtensionFeeAssessedDecimal())
                                                .add(extensionFee));
                loan.setExtensionFeeOutstanding(
                                safe(loan.getExtensionFeeOutstandingDecimal())
                                                .add(extensionFee));

                /*
                 * Preserve complete extension history in internal notes.
                 *
                 * This is intentionally explicit because the extension
                 * fee is frozen from the balance at this event.
                 */
                String previousNotes = loan.getInternalNotes();

                String extensionNote = "[LOAN EXTENSION]"
                                + " reason=" + trimmedReason
                                + " | extensionMonths=" + extensionMonths
                                + " | previousDurationMonths=" + currentDuration
                                + " | newDurationMonths=" + newDuration
                                + " | outstandingPrincipalAtExtension="
                                + outstandingAtExtension
                                + " | extensionFeeRate="
                                + EXTENSION_FEE_RATE
                                + "% "
                                + " | extensionFee="
                                + extensionFee
                                + " | previousMaturity="
                                + previousMaturity
                                + " | newMaturity="
                                + loan.getMaturityDate()
                                + " | previousNextDue="
                                + previousNextDue
                                + " | newNextDue="
                                + loan.getNextDueDate()
                                + " | requestedAt="
                                + LocalDate.now();

                loan.setInternalNotes(
                                previousNotes == null
                                                || previousNotes.isBlank()
                                                                ? extensionNote
                                                                : previousNotes
                                                                                + " | "
                                                                                + extensionNote);

                Loan saved = loanRepo.save(loan);

                /*
                 * Generate the new schedule using the existing authoritative
                 * PaymentScheduleService.
                 */
                paymentScheduleService.generateSchedule(saved);

                refreshNextPayment(saved);

                Loan finalSaved = loanRepo.save(saved);

                accountingService.postExtensionFeeAssessment(
                                finalSaved,
                                extensionFee);

                /*
                 * Audit entry.
                 */
                audit(
                                finalSaved.getOrganization(),
                                officer,
                                "LOAN_EXTENSION_APPROVED",
                                loanId,
                                "Loan extended"
                                                + " | reason=" + trimmedReason
                                                + " | oldTerm=" + currentDuration
                                                + " months"
                                                + " | extension=" + extensionMonths
                                                + " months"
                                                + " | newTerm=" + newDuration
                                                + " months"
                                                + " | outstandingAtExtension="
                                                + outstandingAtExtension
                                                + " | extensionFeeRate="
                                                + EXTENSION_FEE_RATE
                                                + "%"
                                                + " | extensionFee="
                                                + extensionFee
                                                + " | processingFee="
                                                + PROCESSING_FEE_RATE
                                                + "% one-time");

                /*
                 * Webhook.
                 */
                webhookService.dispatch(
                                finalSaved.getOrganization(),
                                "LOAN_EXTENSION_APPROVED",
                                finalSaved);

                /*
                 * Notifications.
                 */
                notify(
                                finalSaved,
                                () -> mailService.sendLoanRestructured(
                                                finalSaved,
                                                trimmedReason),
                                "Your loan "
                                                + finalSaved.getReferenceNumber()
                                                + " has been extended by "
                                                + extensionMonths
                                                + " month(s). "
                                                + "An extension fee of "
                                                + finalSaved.getCurrency()
                                                + " "
                                                + extensionFee
                                                + " has been applied.");

                log.info(
                                "Loan {} extended successfully: outstandingAtExtension={}, extensionFee={}, oldMaturity={}, newMaturity={}",
                                finalSaved.getReferenceNumber(),
                                outstandingAtExtension,
                                extensionFee,
                                previousMaturity,
                                finalSaved.getMaturityDate());

                return finalSaved;
        }

        // ================================================================
        // RESTRUCTURE
        // ================================================================

        /**
         * Restructure an existing loan.
         *
         * IMPORTANT:
         * This method does NOT charge the 2% processing fee again.
         */
        @Transactional
        public Loan restructure(
                        Long loanId,
                        Long orgId,
                        User officer,
                        int newMonths,
                        Double newRate,
                        String reason) {

                Loan loan = get(loanId, orgId);

                validateOfficer(officer, orgId);

                if (loan.getStatus() != LoanStatus.ACTIVE
                                && loan.getStatus() != LoanStatus.OVERDUE
                                && loan.getStatus() != LoanStatus.DEFAULTED
                                && loan.getStatus() != LoanStatus.RESTRUCTURED) {
                        throw new RuntimeException(
                                        "Only ACTIVE, OVERDUE, DEFAULTED, or RESTRUCTURED loans can be restructured");
                }

                validateDuration(newMonths);

                if (reason == null || reason.isBlank()) {
                        throw new IllegalArgumentException(
                                        "Restructuring reason is required");
                }

                String trimmedReason = reason.trim();

                BigDecimal previousRate = safe(loan.getInterestRateDecimal());

                BigDecimal previousManagementRate = safe(loan.getManagementFeeRateDecimal());

                int previousMonths = loan.getDurationMonths() != null
                                ? loan.getDurationMonths()
                                : MIN_LOAN_DURATION_MONTHS;

                BigDecimal previousOutstanding = safe(loan.getOutstandingBalanceDecimal());

                if (previousOutstanding.compareTo(ZERO) <= 0) {
                        throw new IllegalStateException(
                                        "Cannot restructure a loan with no outstanding principal balance");
                }

                /*
                 * Existing callers may still provide newRate.
                 *
                 * Platform rate is fixed at 5%.
                 */
                BigDecimal restructuringInterestRate = safe(loan.getInterestRateDecimal());
                BigDecimal restructuringManagementRate = safe(loan.getManagementFeeRateDecimal());

                if (restructuringInterestRate.compareTo(ZERO) <= 0) {
                        restructuringInterestRate = MONTHLY_INTEREST_RATE;
                }

                if (restructuringManagementRate.compareTo(ZERO) < 0) {
                        restructuringManagementRate = MONTHLY_MANAGEMENT_FEE_RATE;
                }

                if (newRate != null) {

                        if (Double.isNaN(newRate) || Double.isInfinite(newRate)) {
                                throw new IllegalArgumentException(
                                                "Invalid new interest rate");
                        }

                        restructuringInterestRate = money(BigDecimal.valueOf(newRate));

                        if (restructuringInterestRate.compareTo(ZERO) < 0
                                        || restructuringInterestRate.compareTo(new BigDecimal("100.00")) > 0) {
                                throw new IllegalArgumentException(
                                                "Interest rate must be between 0% and 100% per month");
                        }
                }

                loan.setInterestRate(restructuringInterestRate);

                loan.setManagementFeeRate(restructuringManagementRate);

                /*
                 * Do not create a new processing fee.
                 *
                 * The original processing fee belongs to disbursement.
                 */
                loan.setInterestRateType(
                                "MONTHLY");

                loan.setDurationMonths(
                                newMonths);

                String previousNotes = loan.getInternalNotes();

                String restructureNote = "[RESTRUCTURED]"
                                + " "
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
                                + restructuringInterestRate
                                + "% interest + "
                                + restructuringManagementRate
                                + "% management"
                                + " | Outstanding principal at restructuring: "
                                + previousOutstanding;

                loan.setInternalNotes(
                                previousNotes == null
                                                || previousNotes.isBlank()
                                                                ? restructureNote
                                                                : previousNotes
                                                                                + " | "
                                                                                + restructureNote);

                loan.setStatus(
                                LoanStatus.ACTIVE);

                List<Payment> futurePayments = paymentRepo
                                .findByLoanId(loan.getId())
                                .stream()
                                .filter(
                                                payment -> payment != null
                                                                && !Boolean.TRUE.equals(
                                                                                payment.getPaid()))
                                .toList();

                if (!futurePayments.isEmpty()) {

                        paymentRepo.deleteAll(
                                        futurePayments);

                        paymentRepo.flush();
                }

                Loan saved = loanRepo.save(loan);

                paymentScheduleService.generateSchedule(
                                saved);

                refreshNextPayment(saved);

                Loan finalSaved = loanRepo.save(saved);

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
                                                + restructuringInterestRate
                                                + "% monthly"
                                                + " | Previous management="
                                                + previousManagementRate
                                                + "% monthly"
                                                + " | New management="
                                                + restructuringManagementRate
                                                + "% monthly"
                                                + " | Processing fee remains one-time="
                                                + PROCESSING_FEE_RATE
                                                + "%");

                webhookService.dispatch(
                                finalSaved.getOrganization(),
                                "LOAN_RESTRUCTURED",
                                finalSaved);

                notify(
                                finalSaved,
                                () -> mailService.sendLoanRestructured(
                                                finalSaved,
                                                trimmedReason),
                                "Your loan "
                                                + finalSaved.getReferenceNumber()
                                                + " has been restructured. New term: "
                                                + finalSaved.getDurationMonths()
                                                + " months. Monthly interest: "
                                                + finalSaved.getInterestRateDecimal()
                                                + "% and management fee: "
                                                + finalSaved.getManagementFeeRateDecimal()
                                                + "%.");

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
                        String reason) {

                Loan loan = get(
                                loanId,
                                orgId);

                validateOfficer(
                                officer,
                                orgId);

                if (loan.getStatus() == LoanStatus.PAID
                                || loan.getStatus() == LoanStatus.CLOSED) {
                        throw new RuntimeException(
                                        "Cannot write off a PAID or CLOSED loan");
                }

                if (reason == null
                                || reason.isBlank()) {
                        throw new IllegalArgumentException(
                                        "Write-off reason is required");
                }

                String trimmedReason = reason.trim();

                BigDecimal outstanding = safe(
                                loan.getOutstandingBalanceDecimal());

                if (outstanding.compareTo(
                                ZERO) <= 0) {
                        throw new IllegalStateException(
                                        "Cannot write off a loan with no outstanding principal");
                }

                loan.setStatus(
                                LoanStatus.WRITTEN_OFF);

                loan.setOutstandingBalance(
                                ZERO);

                loan.setNextDueDate(
                                null);

                loan.setNextPaymentDate(
                                null);

                loan.setNextInstallmentAmount(
                                ZERO);

                String previousNotes = loan.getInternalNotes();

                String writeOffNote = "[WRITTEN OFF] "
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
                                                                                + writeOffNote);

                Loan saved = loanRepo.save(
                                loan);

                List<Payment> pendingPayments = paymentRepo
                                .findByLoanId(
                                                saved.getId())
                                .stream()
                                .filter(
                                                payment -> payment != null
                                                                && !Boolean.TRUE.equals(
                                                                                payment.getPaid()))
                                .toList();

                if (!pendingPayments.isEmpty()) {

                        paymentRepo.deleteAll(
                                        pendingPayments);

                        paymentRepo.flush();
                }

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
                                                + trimmedReason);

                webhookService.dispatch(
                                saved.getOrganization(),
                                "LOAN_WRITTEN_OFF",
                                saved);

                notify(
                                saved,
                                () -> mailService.sendLoanWrittenOff(
                                                saved,
                                                trimmedReason),
                                "There is an update on your loan "
                                                + saved.getReferenceNumber()
                                                + ". Please contact us for details.");

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
                        String reason) {

                Loan loan = get(
                                loanId,
                                orgId);

                validateOfficer(
                                officer,
                                orgId);

                if (loan.getStatus() != LoanStatus.ACTIVE
                                && loan.getStatus() != LoanStatus.OVERDUE) {
                        throw new RuntimeException(
                                        "Moratorium only applies to ACTIVE or OVERDUE loans");
                }

                if (pauseMonths <= 0) {
                        throw new IllegalArgumentException(
                                        "Moratorium duration must be greater than zero");
                }

                if (reason == null
                                || reason.isBlank()) {
                        throw new IllegalArgumentException(
                                        "Moratorium reason is required");
                }

                int currentDuration = loan.getDurationMonths() != null
                                ? loan.getDurationMonths()
                                : MIN_LOAN_DURATION_MONTHS;

                int resultingDuration = currentDuration + pauseMonths;

                if (resultingDuration > MAX_LOAN_DURATION_MONTHS) {
                        throw new IllegalArgumentException(
                                        "Moratorium would extend the loan beyond the maximum "
                                                        + MAX_LOAN_DURATION_MONTHS
                                                        + " months");
                }

                String trimmedReason = reason.trim();

                List<Payment> unpaidPayments = paymentRepo
                                .findByLoanId(
                                                loanId)
                                .stream()
                                .filter(
                                                payment -> payment != null
                                                                && !Boolean.TRUE.equals(
                                                                                payment.getPaid()))
                                .toList();

                for (Payment payment : unpaidPayments) {

                        if (payment.getDueDate() != null) {

                                payment.setDueDate(
                                                payment.getDueDate()
                                                                .plusMonths(
                                                                                pauseMonths));

                                paymentRepo.save(payment);
                        }
                }

                if (loan.getMaturityDate() != null) {

                        loan.setMaturityDate(
                                        loan.getMaturityDate()
                                                        .plusMonths(
                                                                        pauseMonths));
                }

                if (loan.getNextDueDate() != null) {

                        loan.setNextDueDate(
                                        loan.getNextDueDate()
                                                        .plusMonths(
                                                                        pauseMonths));

                        loan.setNextPaymentDate(
                                        loan.getNextDueDate());
                }

                loan.setStatus(
                                LoanStatus.ACTIVE);

                if (loan.getInterestRateDecimal() == null
                                || loan.getInterestRateDecimal().compareTo(ZERO) <= 0) {
                        loan.setInterestRate(MONTHLY_INTEREST_RATE);
                }

                if (loan.getManagementFeeRateDecimal() == null
                                || loan.getManagementFeeRateDecimal().compareTo(ZERO) < 0) {
                        loan.setManagementFeeRate(MONTHLY_MANAGEMENT_FEE_RATE);
                }

                loan.setInterestRateType(
                                "MONTHLY");

                String previousNotes = loan.getInternalNotes();

                String moratoriumNote = "[MORATORIUM "
                                + pauseMonths
                                + "mo] "
                                + trimmedReason;

                loan.setInternalNotes(
                                previousNotes == null
                                                || previousNotes.isBlank()
                                                                ? moratoriumNote
                                                                : previousNotes
                                                                                + " | "
                                                                                + moratoriumNote);

                Loan saved = loanRepo.save(
                                loan);

                audit(
                                saved.getOrganization(),
                                officer,
                                "MORATORIUM_GRANTED",
                                loanId,
                                pauseMonths
                                                + " month moratorium: "
                                                + trimmedReason);

                webhookService.dispatch(
                                saved.getOrganization(),
                                "MORATORIUM_GRANTED",
                                saved);

                notify(
                                saved,
                                () -> mailService.sendMoratoriumGranted(
                                                saved,
                                                pauseMonths,
                                                trimmedReason),
                                "Your payments on loan "
                                                + saved.getReferenceNumber()
                                                + " are paused for "
                                                + pauseMonths
                                                + " month(s). Next due date: "
                                                + saved.getNextDueDate()
                                                + ".");

                return saved;
        }

        // ================================================================
        // REFRESH NEXT PAYMENT
        // ================================================================

        private void refreshNextPayment(
                        Loan loan) {

                if (loan == null || loan.getId() == null) {
                        return;
                }

                Payment next = paymentRepo
                                .findByLoanId(
                                                loan.getId())
                                .stream()
                                .filter(
                                                payment -> payment != null
                                                                && !Boolean.TRUE.equals(
                                                                                payment.getPaid()))
                                .filter(
                                                payment -> payment.getDueDate() != null)
                                .min(
                                                (a, b) -> a.getDueDate()
                                                                .compareTo(
                                                                                b.getDueDate()))
                                .orElse(null);

                if (next == null) {

                        loan.setNextDueDate(null);
                        loan.setNextPaymentDate(null);
                        loan.setNextInstallmentAmount(ZERO);

                        return;
                }

                loan.setNextDueDate(
                                next.getDueDate());

                loan.setNextPaymentDate(
                                next.getDueDate());

                BigDecimal amount = safe(
                                next.getAmountDecimal());

                loan.setNextInstallmentAmount(
                                amount);

                log.info(
                                "Refreshed next payment for loan {}: dueDate={}, amount={}",
                                loan.getReferenceNumber(),
                                next.getDueDate(),
                                amount);
        }

        // ================================================================
        // GET LOAN WITH ORGANIZATION SECURITY
        // ================================================================

        private Loan get(
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

                Loan loan = loanRepo
                                .findById(
                                                loanId)
                                .orElseThrow(
                                                () -> new RuntimeException(
                                                                "Loan not found: "
                                                                                + loanId));

                if (loan.getOrganization() == null
                                || loan.getOrganization().getId() == null) {
                        throw new IllegalStateException(
                                        "Loan has no valid organization");
                }

                if (!loan.getOrganization()
                                .getId()
                                .equals(orgId)) {
                        throw new RuntimeException(
                                        "Access denied");
                }

                return loan;
        }

        // ================================================================
        // OFFICER VALIDATION
        // ================================================================

        private void validateOfficer(
                        User officer,
                        Long orgId) {

                if (officer == null) {
                        throw new IllegalArgumentException(
                                        "Officer is required");
                }

                if (officer.getOrganization() == null
                                || officer.getOrganization().getId() == null) {
                        throw new IllegalArgumentException(
                                        "Officer organization is required");
                }

                if (orgId == null
                                || !orgId.equals(
                                                officer.getOrganization().getId())) {
                        throw new IllegalStateException(
                                        "Officer does not belong to the loan organization");
                }
        }

        // ================================================================
        // DURATION VALIDATION
        // ================================================================

        private void validateDuration(
                        int months) {

                if (months < MIN_LOAN_DURATION_MONTHS
                                || months > MAX_LOAN_DURATION_MONTHS) {
                        throw new IllegalArgumentException(
                                        "Loan duration must be between "
                                                        + MIN_LOAN_DURATION_MONTHS
                                                        + " and "
                                                        + MAX_LOAN_DURATION_MONTHS
                                                        + " months");
                }
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

        private BigDecimal safe(
                        BigDecimal value) {

                if (value == null) {
                        return ZERO;
                }

                return money(value);
        }

        // ================================================================
        // AUDIT
        // ================================================================

        private void audit(
                        Organization org,
                        User user,
                        String action,
                        Long id,
                        String description) {

                auditService.log(
                                org,
                                user,
                                action,
                                "LOAN",
                                id.toString(),
                                description);
        }

        // ================================================================
        // NOTIFICATIONS
        // ================================================================

        private void notify(
                        Loan loan,
                        Runnable sendEmail,
                        String smsText) {

                if (loan == null
                                || loan.getBorrower() == null) {
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
                                        e);
                }

                try {

                        String phone = loan.getBorrower().getPhone();

                        if (phone != null
                                        && !phone.isBlank()) {

                                smsService.sendCustom(
                                                phone,
                                                smsText);
                        }

                } catch (Exception e) {

                        log.warn(
                                        "Loan notification SMS failed for loan {}",
                                        loan.getId(),
                                        e);
                }
        }
}