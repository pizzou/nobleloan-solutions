package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.PaymentRepository;
import com.patrick.fintech.loan_backend.util.FinancialPolicy;

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
        private final HolidayService holidayService;
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
        private static final BigDecimal MONTHLY_INTEREST_RATE = FinancialPolicy.MONTHLY_INTEREST_RATE;

        private static final BigDecimal MONTHLY_MANAGEMENT_FEE_RATE = FinancialPolicy.MONTHLY_MANAGEMENT_FEE_RATE;

        private static final BigDecimal PROCESSING_FEE_RATE = FinancialPolicy.PROCESSING_FEE_RATE;

        /*
         * Institution policy:
         *
         * Extension/restructuring fee =
         * 10% of the outstanding principal at the moment
         * the extension is approved/requested.
         *
         * This is NOT principal.
         * This is NOT the normal application fee.
         */
        private static final BigDecimal EXTENSION_FEE_RATE = FinancialPolicy.EXTENSION_FEE_RATE;

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
                 * Rebuild only the remaining repayment schedule.
                 *
                 * IMPORTANT: never delete paid/partially-paid installment
                 * rows. Those rows are part of the financial history.
                 * Unpaid rows are replaced with a new schedule based on the
                 * CURRENT outstanding principal and the new total term.
                 */
                rebuildRemainingPaymentScheduleForExtension(
                                loan,
                                newDuration,
                                outstandingAtExtension);

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
                 * The remaining Payment rows were rebuilt above.
                 * PaymentScheduleService is intentionally NOT called here:
                 * that service rebuilds the full original principal schedule
                 * and would overwrite the already-paid principal balance.
                 */
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
        // EXTENSION SCHEDULE REBUILD
        // ================================================================

        /**
         * Rebuilds only the unpaid portion of an extended loan.
         *
         * Financial invariants:
         *
         * 1. Paid and partially-paid installment rows are preserved.
         * 2. Unpaid rows are replaced.
         * 3. The new schedule starts from the latest retained installment.
         * 4. Principal used for the new schedule is the current outstanding
         * principal, never the original loan amount.
         * 5. The application fee is never recalculated.
         * 6. Interest and management fee are calculated again for the new
         * remaining term using the platform calendar-day policy.
         */
        private void rebuildRemainingPaymentScheduleForExtension(
                        Loan loan,
                        int newDuration,
                        BigDecimal outstandingPrincipal) {

                List<Payment> existingPayments = paymentRepo
                                .findByLoanIdOrderByDueDateAsc(loan.getId());

                List<Payment> retainedPayments = existingPayments
                                .stream()
                                .filter(this::hasFinancialPaymentActivity)
                                .toList();

                List<Payment> replaceablePayments = existingPayments
                                .stream()
                                .filter(payment -> !hasFinancialPaymentActivity(payment))
                                .toList();

                if (!replaceablePayments.isEmpty()) {
                        paymentRepo.deleteAll(replaceablePayments);
                        paymentRepo.flush();
                }

                int retainedInstallments = retainedPayments.size();
                int remainingInstallments = newDuration - retainedInstallments;

                if (remainingInstallments <= 0
                                || outstandingPrincipal.compareTo(ZERO) <= 0) {
                        loan.setNextDueDate(null);
                        loan.setNextPaymentDate(null);
                        loan.setNextInstallmentAmount(ZERO);
                        return;
                }

                LocalDate baseDate = retainedPayments
                                .stream()
                                .map(Payment::getDueDate)
                                .filter(date -> date != null)
                                .max(LocalDate::compareTo)
                                .orElseGet(() -> loan.getDisbursedAt() != null
                                                ? loan.getDisbursedAt().toLocalDate()
                                                : (loan.getStartDate() != null
                                                                ? loan.getStartDate()
                                                                : LocalDate.now()));

                /*
                 * If the extension is being approved after the original
                 * maturity/overdue date, do not create another installment
                 * in the past. Start the new repayment cycle from today.
                 */
                if (baseDate.isBefore(LocalDate.now())) {
                        baseDate = LocalDate.now();
                }

                BigDecimal balance = money(outstandingPrincipal);
                BigDecimal accumulatedInterest = ZERO;
                BigDecimal accumulatedManagementFee = ZERO;
                BigDecimal interestRate = loan.getInterestRateDecimal();
                BigDecimal managementRate = loan.getManagementFeeRateDecimal();

                if (interestRate == null || interestRate.compareTo(ZERO) <= 0) {
                        interestRate = MONTHLY_INTEREST_RATE;
                }

                if (managementRate == null || managementRate.compareTo(ZERO) < 0) {
                        managementRate = MONTHLY_MANAGEMENT_FEE_RATE;
                }

                LocalDate accrualStart = baseDate;
                LocalDate firstDueDate = null;
                BigDecimal firstInstallmentAmount = ZERO;
                int startInstallmentNumber = retainedPayments
                                .stream()
                                .map(Payment::getInstallmentNumber)
                                .filter(number -> number != null)
                                .max(Integer::compareTo)
                                .orElse(0) + 1;

                for (int offset = 0; offset < remainingInstallments; offset++) {

                        int installmentNumber = startInstallmentNumber + offset;

                        LocalDate rawDueDate = baseDate.plusMonths(offset + 1L);

                        LocalDate dueDate = holidayService.adjustToBusinessDay(
                                        loan.getOrganization().getId(),
                                        rawDueDate);

                        if (firstDueDate == null) {
                                firstDueDate = dueDate;
                        }

                        int installmentsLeft = remainingInstallments - offset;

                        BigDecimal principalComponent = installmentNumber == startInstallmentNumber
                                        && installmentsLeft == 1
                                                        ? balance
                                                        : (offset == remainingInstallments - 1
                                                                        ? balance
                                                                        : balance.divide(
                                                                                        BigDecimal.valueOf(
                                                                                                        installmentsLeft),
                                                                                        16,
                                                                                        RoundingMode.HALF_UP));

                        principalComponent = money(principalComponent.min(balance).max(ZERO));

                        FinancialPolicy.ScheduleLine line = FinancialPolicy.contractualScheduleLine(
                                        balance,
                                        installmentsLeft,
                                        interestRate,
                                        managementRate);

                        principalComponent = money(line.principal());
                        BigDecimal interest = money(line.interest());
                        BigDecimal managementFee = money(line.managementFee());

                        BigDecimal installmentAmount = money(
                                        principalComponent
                                                        .add(interest)
                                                        .add(managementFee));

                        if (offset == 0) {
                                firstInstallmentAmount = installmentAmount;
                        }

                        accumulatedInterest = money(accumulatedInterest.add(interest));
                        accumulatedManagementFee = money(accumulatedManagementFee.add(managementFee));

                        balance = money(balance.subtract(principalComponent).max(ZERO));

                        Payment payment = Payment.builder()
                                        .paymentReference(generateExtensionPaymentReference(
                                                        loan,
                                                        installmentNumber))
                                        .loan(loan)
                                        .organization(loan.getOrganization())
                                        .installmentNumber(installmentNumber)
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
                                        .extensionFeeComponent(ZERO)
                                        .outstandingAfter(balance)
                                        .status(Payment.PaymentStatus.PENDING)
                                        .build();

                        paymentRepo.save(payment);

                        accrualStart = dueDate;
                }

                /*
                 * Preserve historical paid amounts and add the newly scheduled
                 * future charges. Extension fee remains separate from these
                 * totals and is tracked independently on Loan.
                 */
                BigDecimal historicalInterestPaid = safe(loan.getInterestPaidDecimal());
                BigDecimal historicalManagementPaid = safe(loan.getManagementFeePaidDecimal());

                BigDecimal newTotalInterest = money(
                                historicalInterestPaid.add(accumulatedInterest));

                BigDecimal newTotalManagementFee = money(
                                historicalManagementPaid.add(accumulatedManagementFee));

                loan.setInterestRate(interestRate);
                loan.setManagementFeeRate(managementRate);
                loan.setInterestRateType("MONTHLY");
                loan.setTotalInterest(newTotalInterest);
                loan.setManagementFee(newTotalManagementFee);
                loan.setTotalRepayable(
                                money(
                                                loan.getAmountDecimal()
                                                                .add(newTotalInterest)
                                                                .add(newTotalManagementFee)));
                loan.setOutstandingBalance(outstandingPrincipal);
                loan.setNextDueDate(firstDueDate);
                loan.setNextPaymentDate(firstDueDate);
                loan.setNextInstallmentAmount(firstInstallmentAmount);
        }

        private boolean hasFinancialPaymentActivity(Payment payment) {
                if (payment == null) {
                        return false;
                }

                if (Boolean.TRUE.equals(payment.getPaid())) {
                        return true;
                }

                BigDecimal amountPaid = payment.getAmountPaidDecimal();
                return amountPaid != null && amountPaid.compareTo(ZERO) > 0;
        }

        private String generateExtensionPaymentReference(
                        Loan loan,
                        int installmentNumber) {

                String reference = loan.getReferenceNumber() == null
                                || loan.getReferenceNumber().isBlank()
                                                ? "LOAN-" + loan.getId()
                                                : loan.getReferenceNumber().trim();

                return "PAY-"
                                + reference
                                + "-EXT-"
                                + installmentNumber
                                + "-"
                                + System.nanoTime();
        }

        // ================================================================
        // RESTRUCTURE
        // ================================================================

        /**
         * Restructure an existing loan.
         *
         * IMPORTANT:
         * This method does NOT charge the 2% application fee again.
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
                 * Do not create a new application fee.
                 *
                 * The original application fee belongs to disbursement.
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