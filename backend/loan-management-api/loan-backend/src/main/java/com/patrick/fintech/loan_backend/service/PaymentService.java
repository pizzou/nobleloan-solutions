package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.event.PaymentEventPublisher;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.model.PaymentTransaction;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.AuditLogRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.PaymentRepository;
import com.patrick.fintech.loan_backend.repository.PaymentTransactionRepository;
import com.patrick.fintech.loan_backend.repository.UserRepository;
import com.patrick.fintech.loan_backend.util.FinancialPolicy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

        private final PaymentRepository paymentRepo;
        private final PaymentTransactionRepository paymentTransactionRepo;
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
        private final PaymentAllocationService paymentAllocationService;
        private final HistoricalPenaltyBalanceService historicalPenaltyBalanceService;

        // ================================================================
        // PLATFORM FINANCIAL RULES
        // ================================================================

        /**
         * Monthly loan interest.
         */
        private static final BigDecimal MONTHLY_INTEREST_RATE = FinancialPolicy.MONTHLY_INTEREST_RATE;

        /**
         * Monthly loan management fee.
         */
        private static final BigDecimal MONTHLY_MANAGEMENT_FEE_RATE = FinancialPolicy.MONTHLY_MANAGEMENT_FEE_RATE;

        /**
         * One-time application fee.
         *
         * This is NOT charged by PaymentService.
         * It is deducted at disbursement.
         */
        private static final BigDecimal APPLICATION_FEE_RATE = FinancialPolicy.APPLICATION_FEE_RATE;

        /**
         * Default contractual overdue penalty rate: 10% per month, prorated
         * by actual calendar days after the three-day grace period.
         * Existing loans use their snapshotted Loan.penaltyRate.
         */
        private static final BigDecimal MONTHLY_PENALTY_RATE = FinancialPolicy.MONTHLY_PENALTY_RATE;

        /**
         * Monthly penalty is accrued against the actual calendar days in each month.
         */
        private static final BigDecimal TWELVE = new BigDecimal("12");

        private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

        private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(
                        2,
                        RoundingMode.HALF_UP);

        private static final BigDecimal ONE_CENT = new BigDecimal("0.01");

        private static final BigDecimal MIN_LOAN_AMOUNT = new BigDecimal("1000000.00");

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

                /*
                 * Monetary values are persisted at scale 2 throughout the
                 * operational loan/payment ledger. Never silently round a
                 * caller-supplied financial amount: doing so can turn a retry
                 * or provider callback into a different monetary event.
                 */
                if (amount.stripTrailingZeros().scale() > 2) {
                        throw new IllegalArgumentException(
                                        "Payment amount cannot contain more than two decimal places");
                }

                amount = roundMoney(amount);

                String normalizedTxnId = normalizeTransactionId(txnId);

                // ============================================================
                // LOCK LOAN
                // ============================================================

                Long recordedByOrganizationId = recordedBy != null && recordedBy.getOrganization() != null
                                ? recordedBy.getOrganization().getId()
                                : null;

                Loan loan = recordedByOrganizationId == null
                                ? loanRepo.findByIdForUpdate(loanId)
                                                .orElseThrow(() -> new RuntimeException("Loan not found: " + loanId))
                                : loanRepo.findVisibleByIdForUpdate(
                                                loanId,
                                                recordedByOrganizationId,
                                                ReportingScopeService.includeBusinessOwnerOnly())
                                                .orElseThrow(() -> new RuntimeException("Loan not found: " + loanId));

                validateOrganizationAccess(
                                loan,
                                recordedBy);

                if (loan.getOrganization() == null
                                || loan.getOrganization().getId() == null) {

                        throw new IllegalStateException(
                                        "Loan organization is required.");
                }

                Long organizationId = loan.getOrganization().getId();

                if (loan.getStatus() == LoanStatus.WRITTEN_OFF) {
                        throw new IllegalStateException(
                                        "Payments against a written-off loan require an approved recovery workflow.");
                }

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

                                        /*
                                         * Idempotency is not permission to accept a
                                         * different financial event under an existing
                                         * provider transaction reference. A retried
                                         * webhook must carry the same amount as the
                                         * transaction that was originally posted.
                                         */
                                        Optional<PaymentTransaction> existingTransaction = paymentTransactionRepo
                                                        .findByOrganization_IdAndTransactionReference(
                                                                        organizationId,
                                                                        normalizedTxnId);

                                        if (existingTransaction.isPresent()) {
                                                BigDecimal existingAmount = roundMoney(
                                                                safe(existingTransaction.get().getAmount()));

                                                if (existingAmount.compareTo(amount) != 0) {
                                                        throw new IllegalStateException(
                                                                        "Transaction ID " + normalizedTxnId
                                                                                        + " was already posted with a different amount.");
                                                }
                                        }

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

                long daysLateLong = OverduePenaltyClock.daysOverdue(loan, today);
                int daysLate = (int) Math.min(Integer.MAX_VALUE, daysLateLong);

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
                // Penalties are accrued at loan level by the EOD accounting
                // scheduler. Payment processing must therefore consume already
                // accrued loan-level penalties and only calculate a catch-up
                // amount when the scheduler has not yet caught up. This prevents
                // the same daily penalty from being charged twice.

                BigDecimal installmentPenaltyRecorded = roundMoney(
                                safe(installment.getPenaltyDecimal()))
                                .max(ZERO);

                BigDecimal penaltyAlreadyPaid = roundMoney(
                                safe(installment.getPenaltyPaidDecimal()))
                                .max(ZERO);

                if (penaltyAlreadyPaid.compareTo(installmentPenaltyRecorded) > 0) {
                        penaltyAlreadyPaid = installmentPenaltyRecorded;
                }

                BigDecimal loanPenaltyAssessedBeforePayment = roundMoney(
                                safe(loan.getPenaltiesAssessedDecimal()))
                                .max(ZERO);

                BigDecimal recordedPenaltyOnPaymentRows = paymentRepo.findByLoanId(loan.getId())
                                .stream()
                                .filter(java.util.Objects::nonNull)
                                .map(p -> safe(p.getPenaltyDecimal()))
                                .reduce(ZERO, BigDecimal::add);

                BigDecimal unallocatedAccruedPenalty = roundMoney(
                                loanPenaltyAssessedBeforePayment
                                                .subtract(recordedPenaltyOnPaymentRows)
                                                .max(ZERO));

                BigDecimal existingPenaltyAssessed = roundMoney(
                                installmentPenaltyRecorded
                                                .add(unallocatedAccruedPenalty));

                // ============================================================
                // CURRENT GROSS PRINCIPAL
                // ============================================================

                /*
                 * The application fee is NOT subtracted from outstanding
                 * principal.
                 *
                 * Example:
                 *
                 * Gross loan = 1,000,000
                 * Application fee = 20,000
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
                // CONTRACTUAL MONTHLY INTEREST / MANAGEMENT FEE
                // ============================================================
                // Interest and management fee are contractual monthly charges.
                // They are NEVER calculated from elapsed calendar days.
                // A populated schedule is authoritative; legacy records with no
                // cycle charge fall back to one full monthly charge on current
                // outstanding principal.

                LocalDateTime interestStartDateTime = previousInterestCalculationDate != null
                                ? previousInterestCalculationDate
                                : (loan.getDisbursedAt() != null
                                                ? loan.getDisbursedAt()
                                                : (loan.getDisbursedAtTimestamp() != null
                                                                ? loan.getDisbursedAtTimestamp()
                                                                : (loan.getStartDate() != null
                                                                                ? loan.getStartDate().atStartOfDay()
                                                                                : now)));

                if (interestStartDateTime.isAfter(now)) {
                        interestStartDateTime = now;
                }

                // Informational only. This value is NOT used to calculate interest.
                long elapsedDays = calculateActualInterestDays(
                                interestStartDateTime, now, firstInterestCalculation, loanId);

                boolean cycleIsDue = !cycleDueDate.isAfter(today);

                BigDecimal contractualMonthlyInterest = existingCycleInterestDue.signum() > 0
                                ? existingCycleInterestDue
                                : (cycleIsDue
                                                ? FinancialPolicy.contractualMonthlyCharge(
                                                                currentBalance,
                                                                moneyRatePercent(loan.getInterestRateDecimal(),
                                                                                MONTHLY_INTEREST_RATE))
                                                : ZERO);

                BigDecimal contractualMonthlyManagementFee = existingCycleManagementFeeDue.signum() > 0
                                ? existingCycleManagementFeeDue
                                : (cycleIsDue
                                                ? FinancialPolicy.contractualMonthlyCharge(
                                                                currentBalance,
                                                                moneyRatePercent(loan.getManagementFeeRateDecimal(),
                                                                                MONTHLY_MANAGEMENT_FEE_RATE))
                                                : ZERO);

                BigDecimal newlyAccruedInterest = existingCycleInterestDue.signum() > 0
                                ? ZERO
                                : contractualMonthlyInterest;

                BigDecimal newlyAccruedManagementFee = existingCycleManagementFeeDue.signum() > 0
                                ? ZERO
                                : contractualMonthlyManagementFee;

                // ============================================================
                // TOTAL CURRENT CYCLE INTEREST
                // ============================================================

                BigDecimal totalCycleInterestDue = roundMoney(contractualMonthlyInterest);

                BigDecimal minimumInterestObligation = roundMoney(
                                interestAlreadyPaidThisCycle
                                                .add(existingCycleInterestRemaining));

                if (minimumInterestObligation.compareTo(totalCycleInterestDue) > 0) {
                        totalCycleInterestDue = minimumInterestObligation;
                }

                // ============================================================
                // TOTAL CURRENT CYCLE MANAGEMENT FEE
                // ============================================================

                BigDecimal totalCycleManagementFeeDue = roundMoney(contractualMonthlyManagementFee);

                BigDecimal minimumManagementFeeObligation = roundMoney(
                                managementFeeAlreadyPaidThisCycle
                                                .add(existingCycleManagementFeeRemaining));

                if (minimumManagementFeeObligation.compareTo(totalCycleManagementFeeDue) > 0) {
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

                // Legacy imports can have only a loan-level contractual opening
                // balance, with no complete cycle-by-cycle schedule. When such a
                // loan has *matured*, its remaining interest/management receivables
                // are now due in full. Limiting payment allocation to a single
                // newly created cycle would wrongly put money against principal.
                // Never accelerate future charges for an unmatured loan.
                boolean maturedLegacyWithoutFullSchedule =
                                Boolean.TRUE.equals(loan.getImported())
                                                && OverduePenaltyClock.contractualMaturity(loan) != null
                                                && !OverduePenaltyClock.contractualMaturity(loan).isAfter(today)
                                                && loanPayments.size() <= 1;
                if (maturedLegacyWithoutFullSchedule) {
                        remainingInterestBeforePayment = remainingInterestBeforePayment.max(
                                        roundMoney(safe(loan.getInterestOutstandingDecimal())).max(ZERO));
                        remainingManagementFeeBeforePayment = remainingManagementFeeBeforePayment.max(
                                        roundMoney(safe(loan.getManagementFeeOutstandingDecimal())).max(ZERO));
                        totalCycleInterestDue = roundMoney(
                                        interestAlreadyPaidThisCycle.add(remainingInterestBeforePayment));
                        totalCycleManagementFeeDue = roundMoney(
                                        managementFeeAlreadyPaidThisCycle.add(remainingManagementFeeBeforePayment));
                }

                // The receipt must first settle every *earned and unpaid* monthly
                // charge, not just the currently selected installment. Scheduled
                // future fees are deliberately excluded until their due dates.
                BigDecimal allDueInterest = totalDueCharge(
                                loanPayments, installment, today, true,
                                remainingInterestBeforePayment);
                BigDecimal allDueManagementFees = totalDueCharge(
                                loanPayments, installment, today, false,
                                remainingManagementFeeBeforePayment);

                // Once the contractual loan is overdue, ALL its remaining
                // contractual interest and management charges are due, not
                // merely charges represented by the selected installment.
                // This applies to system-created and imported loans alike.
                // Reconcile loan-level receivables with the dated schedule
                // before the canonical penalty/fees/principal waterfall.
                OverdueFeeGap overdueGap = overdueFeeGap(
                                isLate,
                                allDueInterest,
                                allDueManagementFees,
                                safe(loan.getInterestOutstandingDecimal()),
                                safe(loan.getManagementFeeOutstandingDecimal()),
                                cycleDueDate, today);
                if (overdueGap.interest().signum() > 0
                                || overdueGap.management().signum() > 0) {
                        remainingInterestBeforePayment = roundMoney(
                                        remainingInterestBeforePayment.add(overdueGap.interest()));
                        remainingManagementFeeBeforePayment = roundMoney(
                                        remainingManagementFeeBeforePayment.add(overdueGap.management()));
                        totalCycleInterestDue = roundMoney(
                                        totalCycleInterestDue.add(overdueGap.interest()));
                        totalCycleManagementFeeDue = roundMoney(
                                        totalCycleManagementFeeDue.add(overdueGap.management()));
                        allDueInterest = roundMoney(allDueInterest.add(overdueGap.interest()));
                        allDueManagementFees = roundMoney(allDueManagementFees.add(overdueGap.management()));
                }

                // ============================================================
                // NEW PENALTY DAYS
                // ============================================================

                int existingDaysLate = installment.getDaysLate() != null
                                ? Math.max(
                                                0,
                                                installment.getDaysLate())
                                : 0;

                // Overdue days and penalty days both begin after three full
                // grace days following contractual maturity.
                int currentlyChargeablePenaltyDays = (int) Math.min(
                                Integer.MAX_VALUE,
                                OverduePenaltyClock.chargeableDays(loan, today));

                BigDecimal newlyCalculatedPenalty = ZERO;
                int newPenaltyDays = 0;

                if (loan.getStatus() != LoanStatus.WRITTEN_OFF
                                && currentlyChargeablePenaltyDays > 0) {

                        LocalDate earliestPenaltyDate = resolveEarliestPenaltyDate(loan, today);
                        BigDecimal requiredPenaltyToDate = earliestPenaltyDate == null
                                        ? ZERO
                                        : calculateHistoricalPenaltyToDate(
                                                        loan, currentBalance, earliestPenaltyDate, today);

                        // A previously over-assessed penalty is NOT a legitimate
                        // payable charge merely because it exists in the ledger.
                        // Do not silently take such money from the borrower:
                        // require an audited adjustment of loan and GL balances.
                        validatePenaltyAssessment(
                                        loan.getReferenceNumber(),
                                        loanPenaltyAssessedBeforePayment,
                                        requiredPenaltyToDate);

                        // Contractual penalty uses the individual loan's saved monthly
                        // rate (legacy 10%, new loans 15%), prorated daily after
                        // the 3-day grace period. There is no separate
                        // cumulative cap beyond the outstanding principal used
                        // for each daily accrual.
                        newlyCalculatedPenalty = roundMoney(
                                        requiredPenaltyToDate
                                                        .subtract(loanPenaltyAssessedBeforePayment)
                                                        .max(ZERO));

                        newPenaltyDays = newlyCalculatedPenalty.compareTo(ZERO) > 0
                                        ? currentlyChargeablePenaltyDays
                                        : 0;
                }

                // Post any missing penalty accrual before allocating cash.
                // The journal entry and loan update participate in this payment
                // transaction. If an accrual already exists for today but its
                // loan-level amount does not match, fail instead of silently
                // creating a receivable with no matching GL debit.
                if (newlyCalculatedPenalty.signum() > 0) {
                        BigDecimal assessedBeforeCatchUp = roundMoney(
                                        safe(loan.getPenaltiesAssessedDecimal()));
                        accountingService.postPenaltyAccrual(loan, newlyCalculatedPenalty);
                        BigDecimal actuallyPosted = roundMoney(
                                        safe(loan.getPenaltiesAssessedDecimal())
                                                        .subtract(assessedBeforeCatchUp));
                        if (actuallyPosted.compareTo(newlyCalculatedPenalty) != 0) {
                                throw new IllegalStateException(
                                                "Penalty accrual journal did not reconcile with loan "
                                                                + loan.getReferenceNumber()
                                                                + "; expected=" + newlyCalculatedPenalty
                                                                + ", posted=" + actuallyPosted);
                        }
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

                // Loan-level assessed-minus-paid is authoritative. A single
                // installment is not an adequate summary of previous penalty
                // payments and can otherwise cause the same penalty to be paid twice.
                BigDecimal penaltyRemainingBeforePayment = roundMoney(
                                loanPenaltyAssessedBeforePayment
                                                .add(newlyCalculatedPenalty)
                                                .subtract(safe(loan.getPenaltiesPaidDecimal()))
                                                .max(ZERO));

                // ============================================================
                // PAYMENT ALLOCATION — CANONICAL ORDER
                // ============================================================
                // Penalty -> equal priority due interest and management fees
                // -> extension fees -> principal. Never allocate to principal
                // while an already-due contractual charge is outstanding.

                PaymentAllocationService.Allocation allocation = paymentAllocationService.allocate(
                                amount,
                                penaltyRemainingBeforePayment,
                                safe(loan.getExtensionFeeOutstandingDecimal()),
                                allDueInterest,
                                allDueManagementFees,
                                currentBalance);

                BigDecimal penaltyPaidThisPayment = roundMoney(allocation.penalty());

                BigDecimal totalPenaltyPaid = roundMoney(
                                penaltyAlreadyPaid.add(penaltyPaidThisPayment));

                BigDecimal remainingPenaltyAfterPayment = roundMoney(
                                penaltyRemainingBeforePayment.subtract(penaltyPaidThisPayment)
                                                .max(ZERO));

                BigDecimal extensionFeeOutstandingBeforePayment = roundMoney(
                                safe(loan.getExtensionFeeOutstandingDecimal())).max(ZERO);

                BigDecimal extensionFeePaidThisPayment = roundMoney(allocation.extensionFee());

                BigDecimal extensionFeeOutstandingAfterPayment = roundMoney(
                                extensionFeeOutstandingBeforePayment
                                                .subtract(extensionFeePaidThisPayment)
                                                .max(ZERO));

                BigDecimal extensionFeePaidTotal = roundMoney(
                                safe(loan.getExtensionFeePaidDecimal())
                                                .add(extensionFeePaidThisPayment));

                BigDecimal interestPaidThisPayment = roundMoney(allocation.interest());
                BigDecimal managementFeePaidThisPayment = roundMoney(allocation.managementFee());

                // Apply interest and management payments to oldest earned cycles.
                // This prevents the same previous-month fee being collected again
                // at the next receipt, while leaving future cycles untouched.
                DueChargeSettlement dueSettlement = settleDueCharges(
                                loanPayments, installment, today,
                                interestPaidThisPayment, managementFeePaidThisPayment,
                                remainingInterestBeforePayment,
                                remainingManagementFeeBeforePayment);
                BigDecimal interestPaidOnSelected = dueSettlement.interestOnSelected();
                BigDecimal managementPaidOnSelected = dueSettlement.managementOnSelected();

                BigDecimal principalPaidThisPayment = roundMoney(allocation.principal());
                BigDecimal overpayment = roundMoney(allocation.overpayment());

                // NEW PRINCIPAL BALANCE
                BigDecimal newBalance = roundMoney(
                                currentBalance
                                                .subtract(principalPaidThisPayment)
                                                .max(ZERO));

                // ============================================================
                // CUMULATIVE PAYMENT COMPONENTS
                // ============================================================

                // Newly generated schedules prefill component columns with
                // *scheduled* amounts even before receiving a payment. Never
                // treat these placeholders as previously paid components.
                boolean selectedHadReceipt = amountPaidSoFar.signum() > 0;
                BigDecimal existingPrincipalPaid = selectedHadReceipt
                                ? roundMoney(safe(installment.getPrincipalComponentDecimal())).max(ZERO)
                                : ZERO;

                BigDecimal totalPrincipalPaid = roundMoney(
                                existingPrincipalPaid
                                                .add(
                                                                principalPaidThisPayment));

                BigDecimal existingInterestComponent = selectedHadReceipt
                                ? roundMoney(safe(installment.getInterestComponentDecimal())).max(ZERO)
                                : ZERO;

                BigDecimal totalInterestPaid = roundMoney(
                                existingInterestComponent
                                                .add(
                                                                interestPaidOnSelected));

                BigDecimal existingManagementFeeComponent = selectedHadReceipt
                                ? roundMoney(safe(installment.getManagementFeeComponentDecimal())).max(ZERO)
                                : ZERO;

                BigDecimal totalManagementFeePaid = roundMoney(
                                existingManagementFeeComponent
                                                .add(
                                                                managementPaidOnSelected));

                BigDecimal existingExtensionFeeComponent = selectedHadReceipt
                                ? roundMoney(safe(installment.getExtensionFeeComponentDecimal())).max(ZERO)
                                : ZERO;

                BigDecimal totalExtensionFeePaid = roundMoney(
                                existingExtensionFeeComponent
                                                .add(extensionFeePaidThisPayment));

                // ============================================================
                // REMAINING INTEREST
                // ============================================================

                BigDecimal remainingInterestAfterPayment = roundMoney(
                                totalCycleInterestDue
                                                .subtract(
                                                                interestAlreadyPaidThisCycle
                                                                                .add(
                                                                                                interestPaidOnSelected))
                                                .max(ZERO));

                // ============================================================
                // REMAINING MANAGEMENT FEE
                // ============================================================

                BigDecimal remainingManagementFeeAfterPayment = roundMoney(
                                totalCycleManagementFeeDue
                                                .subtract(
                                                                managementFeeAlreadyPaidThisCycle
                                                                                .add(
                                                                                                managementPaidOnSelected))
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

                boolean extensionFeeCovered = extensionFeeOutstandingAfterPayment
                                .compareTo(ONE_CENT) <= 0;

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
                                && penaltyCovered
                                && extensionFeeCovered) {

                        cycleCompleted = true;

                } else {

                        cycleCompleted = scheduledAmountCovered
                                        && interestCovered
                                        && managementFeeCovered
                                        && penaltyCovered
                                        && extensionFeeCovered;
                }

                if (principalCovered
                                && (!interestCovered
                                                || !managementFeeCovered
                                                || !penaltyCovered
                                                || !extensionFeeCovered)) {

                        cycleCompleted = false;
                }

                // ============================================================
                // PAYMENT TOTAL VALIDATION
                // ============================================================

                BigDecimal allocated = extensionFeePaidThisPayment
                                .add(penaltyPaidThisPayment)
                                .add(interestPaidThisPayment)
                                .add(managementFeePaidThisPayment)
                                .add(principalPaidThisPayment)
                                .add(overpayment);

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

                installment.setExtensionFeeComponent(
                                totalExtensionFeePaid);

                installment.setPrincipalComponent(
                                totalPrincipalPaid);

                installment.setPenalty(
                                totalPenalty.max(totalPenaltyPaid));

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

                // The loan is pessimistically locked for this transaction. A duplicate
                // transaction reference for the same loan therefore serializes and is
                // detected by the lookup above. The database unique constraint remains the
                // final authority for cross-loan duplicate references. We intentionally do
                // not catch a PostgreSQL unique violation here because doing so inside the
                // same transaction would leave the transaction aborted (25P02) and could
                // compromise atomicity of the financial operation.
                installment = paymentRepo.save(installment);

                // ============================================================
                // UPDATE LOAN TOTALS
                // ============================================================

                // totalPaid is the cumulative amount settled through the repayment
                // workflow, regardless of allocation to interest, management fees,
                // penalties, extension fees or principal. Keep this aggregate in sync
                // with the component-paid fields updated below; do not use it as a
                // principal-only figure. Application fees deducted at disbursement are
                // tracked separately and are not added by this repayment operation.
                BigDecimal oldTotalPaid = roundMoney(
                                safe(loan.getTotalPaidDecimal()));

                BigDecimal newTotalPaid = roundMoney(oldTotalPaid.add(amount));

                loan.setTotalPaid(newTotalPaid);

                loan.setOutstandingBalance(
                                newBalance);

                loan.setLastPaymentDate(
                                today);

                // ============================================================
                // UPDATE LOAN PRINCIPAL PAID
                // ============================================================

                BigDecimal oldLoanPrincipalPaid = roundMoney(
                                safe(loan.getPrincipalPaidDecimal()));

                BigDecimal newLoanPrincipalPaid = roundMoney(
                                oldLoanPrincipalPaid.add(principalPaidThisPayment));

                loan.setPrincipalPaid(newLoanPrincipalPaid);

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

                loan.setExtensionFeePaid(extensionFeePaidTotal);
                loan.setExtensionFeeOutstanding(extensionFeeOutstandingAfterPayment);

                // The loan aggregate stores the total contractual unpaid amount.
                // Scheduled accrual is an accounting/GL event; it must not create
                // a second receivable on the Loan entity. Keep the database
                // reconciliation invariant exact: outstanding = total - paid.
                BigDecimal newInterestOutstanding = roundMoney(
                                safe(loan.getTotalInterestDecimal())
                                                .subtract(newLoanInterestPaid)
                                                .max(ZERO));
                loan.setInterestOutstanding(newInterestOutstanding);

                BigDecimal newManagementOutstanding = roundMoney(
                                safe(loan.getManagementFeeDecimal())
                                                .subtract(newLoanManagementFeePaid)
                                                .max(ZERO));
                loan.setManagementFeeOutstanding(newManagementOutstanding);

                // Penalty accrual was already posted (with its balanced journal)
                // earlier in this transaction. Do not assess the same amount again.
                BigDecimal newPenaltiesAssessed = roundMoney(
                                safe(loan.getPenaltiesAssessedDecimal())).max(ZERO);

                BigDecimal oldPenaltiesPaid = roundMoney(
                                safe(loan.getPenaltiesPaidDecimal()));
                BigDecimal newPenaltiesPaid = roundMoney(
                                oldPenaltiesPaid.add(penaltyPaidThisPayment));
                loan.setPenaltiesPaid(newPenaltiesPaid.min(newPenaltiesAssessed));

                // ============================================================
                // UPDATE LOAN DAYS OVERDUE
                // ============================================================

                // Days overdue is a CURRENT metric, not a historical maximum.
                // A cleared arrears period must not leave a stale overdue badge.
                loan.setDaysOverdue(daysLate);

                // ============================================================
                // FULLY PAID LOAN
                // ============================================================

                // An installment may have no CURRENT-cycle interest or management
                // charge before its due date, while the loan still carries unpaid
                // contractual fees. Do not mark the entire loan PAID merely
                // because principal and the current cycle have been settled.
                // Early payoff/waiver must explicitly settle the loan-level
                // contractual obligation; never silently write off those fees.
                if (principalCovered
                                && interestCovered
                                && managementFeeCovered
                                && penaltyCovered
                                && extensionFeeCovered
                                && newInterestOutstanding.compareTo(ONE_CENT) <= 0
                                && newManagementOutstanding.compareTo(ONE_CENT) <= 0) {

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

                // Institution-wide contractual delinquency is based on
                // actual disbursement + duration + 3 full grace days.
                // Repayment installment dates govern cash scheduling only.
                // Preserve oldest unpaid installment for the next due date,
                // without using it as the regulatory overdue clock.
                if (loan.getStatus() == LoanStatus.PAID) {
                        loan.setDaysOverdue(0);
                } else {
                        int contractualDaysOverdue = (int) Math.min(
                                        Integer.MAX_VALUE, OverduePenaltyClock.daysOverdue(loan, today));
                        loan.setDaysOverdue(contractualDaysOverdue);
                        loan.setStatus(contractualDaysOverdue > 0
                                        ? LoanStatus.OVERDUE : LoanStatus.ACTIVE);
                        Optional<LocalDate> oldestOpenDue = paymentRepo.findByLoanId(loanId)
                                        .stream()
                                        .filter(p -> p != null && !Boolean.TRUE.equals(p.getPaid()))
                                        .map(Payment::getDueDate)
                                        .filter(java.util.Objects::nonNull)
                                        .min(LocalDate::compareTo);
                        oldestOpenDue.ifPresent(due -> {
                                loan.setNextDueDate(due);
                                loan.setNextPaymentDate(due);
                        });
                }

                // Any payment that actually reduces principal changes the
                // outstanding balance. Rebuild every future unpaid installment
                // from that NEW balance immediately. This is especially
                // important for early/extra principal payments; waiting until
                // the entire loan is paid would leave the future schedule
                // calculated from an old balance.
                if (principalPaidThisPayment.compareTo(ZERO) > 0
                                && !loan.getStatus().equals(LoanStatus.PAID)) {
                        refreshFutureInstallments(
                                        loan,
                                        today,
                                        newBalance,
                                        installment.getId());
                }

                loanRepo.save(
                                loan);

                // ============================================================
                // IMMUTABLE PAYMENT TRANSACTION LEDGER
                // ============================================================
                // Payment rows are cumulative installment state. The
                // PaymentTransaction row is the immutable transaction event
                // used by the business-owner payment ledger, audit views and
                // downstream reconciliation. Every successful posting gets
                // exactly one transaction event in the same database
                // transaction as the accounting journal.
                String transactionReference = normalizedTxnId != null
                                ? normalizedTxnId
                                : "PAYTX-" + UUID.randomUUID();

                validatePaymentTransactionAllocation(
                                amount,
                                principalPaidThisPayment,
                                interestPaidThisPayment,
                                managementFeePaidThisPayment,
                                extensionFeePaidThisPayment,
                                penaltyPaidThisPayment,
                                overpayment);

                PaymentTransaction transaction = PaymentTransaction.builder()
                                .loan(loan)
                                .organization(loan.getOrganization())
                                .installment(installment)
                                .recordedBy(recordedBy)
                                .transactionReference(transactionReference)
                                .amount(roundMoney(amount))
                                .penaltyComponent(roundMoney(penaltyPaidThisPayment))
                                .interestComponent(roundMoney(interestPaidThisPayment))
                                .principalComponent(roundMoney(principalPaidThisPayment))
                                .managementFeeComponent(roundMoney(managementFeePaidThisPayment))
                                .extensionFeeComponent(roundMoney(extensionFeePaidThisPayment))
                                .unappliedAmount(roundMoney(overpayment))
                                .provider(channel)
                                .currency(loan.getCurrency())
                                .externalReference(normalizedTxnId)
                                .gatewayStatus(installment.getStatus() != null
                                                ? installment.getStatus().name()
                                                : null)
                                .paymentMethod(method)
                                .channel(channel)
                                .notes(notes)
                                .status(PaymentTransaction.TransactionStatus.POSTED)
                                .reversed(false)
                                .build();

                PaymentTransaction savedTransaction = paymentTransactionRepo.saveAndFlush(transaction);

                // Record one balanced cash journal per immutable receipt, not per
                // cumulative installment. The enclosing transaction rolls both
                // records back if posting fails.
                accountingService.postPaymentReceived(
                                installment,
                                amount,
                                principalPaidThisPayment,
                                interestPaidThisPayment,
                                managementFeePaidThisPayment,
                                penaltyPaidThisPayment,
                                extensionFeePaidThisPayment,
                                overpayment,
                                savedTransaction.getId());

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
                                                + " — elapsed days (informational only)="
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
                                                + ", monthly interest rate="
                                                + moneyRatePercent(loan.getInterestRateDecimal(), MONTHLY_INTEREST_RATE)
                                                + "%"
                                                + ", monthly management fee rate="
                                                + moneyRatePercent(loan.getManagementFeeRateDecimal(),
                                                                MONTHLY_MANAGEMENT_FEE_RATE)
                                                + "%"
                                                + ", monthly penalty rate="
                                                + loan.getPenaltyRateDecimal()
                                                + "% prorated daily after 3-day grace");

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
                                        "monthlyPenaltyRatePercent",
                                        loan.getPenaltyRateDecimal());

                        paymentWebhook.put(
                                        "penaltyGraceDays",
                                        FinancialPolicy.PENALTY_GRACE_DAYS);

                        paymentWebhook.put(
                                        "interestDays",
                                        elapsedDays);

                        paymentWebhook.put(
                                        "monthlyInterestRate",
                                        moneyRatePercent(loan.getInterestRateDecimal(), MONTHLY_INTEREST_RATE));

                        paymentWebhook.put(
                                        "monthlyManagementFeeRate",
                                        moneyRatePercent(loan.getManagementFeeRateDecimal(),
                                                        MONTHLY_MANAGEMENT_FEE_RATE));

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
                                        "applicationFee",
                                        loan.getApplicationFeeDecimal());

                        paymentWebhook.put(
                                        "applicationFeePaid",
                                        loan.getApplicationFeePaid());

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

                Loan loan = loanRepo.findVisibleById(
                                loanId,
                                orgId,
                                ReportingScopeService.includeBusinessOwnerOnly()).orElseThrow(
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

                return paymentRepo.findByLoanIdAndReportingScope(
                                loanId,
                                ReportingScopeService.includeBusinessOwnerOnly());
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

                if (firstInterestCalculation) {
                        // Disbursement day is not an extra charge day.
                        // The first charge is earned only for calendar days actually elapsed.
                        return interestStart == null || now == null
                                        ? 0L
                                        : Math.max(0L, ChronoUnit.DAYS.between(
                                                        interestStart.toLocalDate(),
                                                        now.toLocalDate()));
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
        // REFRESH FUTURE INSTALLMENTS FROM OUTSTANDING PRINCIPAL
        // ================================================================

        private void refreshFutureInstallments(
                        Loan loan,
                        LocalDate anchorDate,
                        BigDecimal outstandingPrincipal,
                        Long completedPaymentId) {
                if (loan == null
                                || loan.getId() == null
                                || outstandingPrincipal == null
                                || outstandingPrincipal.compareTo(ZERO) <= 0) {
                        return;
                }

                List<Payment> allPayments = paymentRepo.findByLoanId(loan.getId());

                List<Payment> future = allPayments
                                .stream()
                                .filter(p -> p != null)
                                .filter(p -> !Boolean.TRUE.equals(p.getPaid()))
                                .filter(p -> completedPaymentId == null
                                                || p.getId() == null
                                                || !p.getId().equals(completedPaymentId))
                                // Only unearned future installments may be rescheduled.
                                // Prior overdue cycles and partial receipts are historical
                                // obligations and must never be overwritten by reamortization.
                                .filter(p -> p.getDueDate() != null
                                                && p.getDueDate().isAfter(anchorDate))
                                .filter(p -> safe(p.getAmountPaidDecimal()).signum() == 0)
                                .sorted(Comparator.comparing(
                                                Payment::getInstallmentNumber,
                                                Comparator.nullsLast(Comparator.naturalOrder())))
                                .toList();

                if (future.isEmpty()) {
                        // All future cycles may already be paid, or only historical
                        // arrears may remain. Do not clear the next contractual
                        // installment amount merely because no future rows qualify.
                        return;
                }

                BigDecimal balance = roundMoney(outstandingPrincipal);
                BigDecimal equalPrincipal = roundMoney(
                                balance.divide(
                                                BigDecimal.valueOf(future.size()),
                                                16,
                                                RoundingMode.HALF_UP));
                LocalDate start = anchorDate == null ? LocalDate.now() : anchorDate;

                for (int i = 0; i < future.size(); i++) {
                        Payment p = future.get(i);
                        LocalDate dueDate = p.getDueDate() == null ? start.plusMonths(1) : p.getDueDate();

                        if (!start.isBefore(dueDate)) {
                                start = dueDate.minusDays(1);
                                if (!start.isBefore(dueDate)) {
                                        start = dueDate;
                                }
                        }

                        BigDecimal principalComponent = (i == future.size() - 1)
                                        ? roundMoney(balance)
                                        : roundMoney(equalPrincipal.min(balance));

                        BigDecimal interest = FinancialPolicy.accrueScheduledMonthly(
                                        balance,
                                        moneyRatePercent(loan.getInterestRateDecimal(), MONTHLY_INTEREST_RATE));
                        BigDecimal managementFee = FinancialPolicy.accrueScheduledMonthly(
                                        balance,
                                        moneyRatePercent(loan.getManagementFeeRateDecimal(),
                                                        MONTHLY_MANAGEMENT_FEE_RATE));
                        BigDecimal projectedAmount = roundMoney(
                                        principalComponent.add(interest).add(managementFee));

                        p.setPrincipalComponent(principalComponent);
                        p.setInterestComponent(interest);
                        p.setManagementFeeComponent(managementFee);
                        p.setScheduledInterest(interest);
                        p.setScheduledManagementFee(managementFee);
                        p.setCycleInterestDue(interest);
                        p.setCycleInterestRemaining(interest);
                        p.setCycleManagementFeeDue(managementFee);
                        p.setCycleManagementFeeRemaining(managementFee);
                        p.setAmount(projectedAmount);
                        p.setOutstandingAfter(
                                        roundMoney(balance.subtract(principalComponent).max(ZERO)));
                        paymentRepo.save(p);

                        balance = roundMoney(balance.subtract(principalComponent).max(ZERO));
                        start = dueDate;
                }

                BigDecimal scheduledInterestTotal = allPayments
                                .stream()
                                .filter(java.util.Objects::nonNull)
                                .map(Payment::getScheduledInterestDecimal)
                                .filter(java.util.Objects::nonNull)
                                .reduce(ZERO, BigDecimal::add);

                BigDecimal scheduledManagementFeeTotal = allPayments
                                .stream()
                                .filter(java.util.Objects::nonNull)
                                .map(Payment::getScheduledManagementFeeDecimal)
                                .filter(java.util.Objects::nonNull)
                                .reduce(ZERO, BigDecimal::add);

                BigDecimal normalizedInterestTotal = roundMoney(scheduledInterestTotal);
                BigDecimal normalizedManagementTotal = roundMoney(scheduledManagementFeeTotal);
                loan.setTotalInterest(normalizedInterestTotal);
                loan.setManagementFee(normalizedManagementTotal);
                loan.setInterestOutstanding(
                                roundMoney(normalizedInterestTotal.subtract(safe(loan.getInterestPaidDecimal()))
                                                .max(ZERO)));
                loan.setManagementFeeOutstanding(
                                roundMoney(normalizedManagementTotal.subtract(safe(loan.getManagementFeePaidDecimal()))
                                                .max(ZERO)));
                loan.setTotalRepayable(
                                roundMoney(
                                                safe(loan.getAmountDecimal())
                                                                .add(scheduledInterestTotal)
                                                                .add(scheduledManagementFeeTotal)));

                Payment next = future.get(0);
                loan.setNextDueDate(next.getDueDate());
                loan.setNextPaymentDate(next.getDueDate());
                loan.setNextInstallmentAmount(roundMoney(next.getAmount()));
        }

        private BigDecimal moneyRatePercent(
                        BigDecimal loanRatePercent,
                        BigDecimal fallbackRatePercent) {
                if (loanRatePercent == null || loanRatePercent.compareTo(ZERO) < 0) {
                        return fallbackRatePercent;
                }
                return roundMoney(loanRatePercent);
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
        // PAYMENT TRANSACTION FINANCIAL INVARIANT
        // ================================================================

        /**
         * Every immutable payment transaction must account for the entire
         * received amount exactly once. This is checked before the ledger
         * row is persisted so a malformed allocation cannot enter the
         * immutable transaction history.
         */
        private void validatePaymentTransactionAllocation(
                        BigDecimal amount,
                        BigDecimal principal,
                        BigDecimal interest,
                        BigDecimal managementFee,
                        BigDecimal extensionFee,
                        BigDecimal penalty,
                        BigDecimal unapplied) {

                BigDecimal total = roundMoney(amount);
                BigDecimal principalValue = roundMoney(principal);
                BigDecimal interestValue = roundMoney(interest);
                BigDecimal managementFeeValue = roundMoney(managementFee);
                BigDecimal extensionFeeValue = roundMoney(extensionFee);
                BigDecimal penaltyValue = roundMoney(penalty);
                BigDecimal unappliedValue = roundMoney(unapplied);

                if (principalValue.signum() < 0
                                || interestValue.signum() < 0
                                || managementFeeValue.signum() < 0
                                || extensionFeeValue.signum() < 0
                                || penaltyValue.signum() < 0
                                || unappliedValue.signum() < 0) {
                        throw new IllegalStateException(
                                        "Payment allocation components cannot be negative.");
                }

                BigDecimal allocated = roundMoney(
                                principalValue
                                                .add(interestValue)
                                                .add(managementFeeValue)
                                                .add(extensionFeeValue)
                                                .add(penaltyValue)
                                                .add(unappliedValue));

                if (allocated.compareTo(total) != 0) {
                        throw new IllegalStateException(
                                        "Payment transaction allocation is not balanced: "
                                                        + "amount=" + total.toPlainString()
                                                        + ", allocated=" + allocated.toPlainString());
                }
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

        private LocalDate resolveEarliestPenaltyDate(Loan loan, LocalDate asOf) {
                LocalDate firstChargeable = OverduePenaltyClock.firstChargeableDay(loan);
                return firstChargeable != null && !firstChargeable.isAfter(asOf)
                                ? firstChargeable : null;
        }

        private BigDecimal calculateHistoricalPenaltyToDate(
                        Loan loan,
                        BigDecimal currentOutstandingPrincipal,
                        LocalDate firstChargeableDate,
                        LocalDate asOf) {
                return historicalPenaltyBalanceService.assessedToDate(
                                loan, currentOutstandingPrincipal, firstChargeableDate, asOf);
        }

        private static void validatePenaltyAssessment(
                        String loanReference, BigDecimal previouslyAssessed,
                        BigDecimal contractuallyDue) {
                BigDecimal old = safeMoney(previouslyAssessed);
                BigDecimal valid = safeMoney(contractuallyDue);
                if (old.subtract(valid).compareTo(new BigDecimal("0.01")) > 0) {
                        throw new IllegalStateException(
                                        "Penalty receivable exceeds contractual daily penalty for "
                                                        + loanReference + ": assessed=" + old
                                                        + ", expected=" + valid
                                                        + ". Correct historical loan and accounting balances before accepting repayment.");
                }
        }

        private record OverdueFeeGap(BigDecimal interest, BigDecimal management) {}

        /**
         * On contractual delinquency, every remaining loan-level monthly fee
         * is payable. The schedule may be incomplete for imported OR native
         * loans. Never silently divert those unscheduled fees to principal.
         *
         * A disagreement where schedules exceed loan-level totals (or the
         * selected cycle is in the future) is a financial evidence error:
         * reject the transaction instead of guessing/relabeling cash.
         */
        private static OverdueFeeGap overdueFeeGap(
                        boolean contractOverdue,
                        BigDecimal scheduledInterestDue,
                        BigDecimal scheduledManagementDue,
                        BigDecimal interestOutstanding,
                        BigDecimal managementOutstanding,
                        LocalDate selectedDueDate,
                        LocalDate asOf) {
                if (!contractOverdue) {
                        return new OverdueFeeGap(ZERO, ZERO);
                }
                BigDecimal scheduledInterest = safeMoney(scheduledInterestDue);
                BigDecimal scheduledManagement = safeMoney(scheduledManagementDue);
                BigDecimal totalInterest = safeMoney(interestOutstanding);
                BigDecimal totalManagement = safeMoney(managementOutstanding);
                if (scheduledInterest.compareTo(totalInterest) > 0
                                || scheduledManagement.compareTo(totalManagement) > 0) {
                        throw new IllegalStateException(
                                        "Due installment fees exceed loan-level outstanding fees; reconcile before payment.");
                }
                BigDecimal interestGap = totalInterest.subtract(scheduledInterest);
                BigDecimal managementGap = totalManagement.subtract(scheduledManagement);
                if ((interestGap.signum() > 0 || managementGap.signum() > 0)
                                && (selectedDueDate == null || asOf == null
                                        || selectedDueDate.isAfter(asOf))) {
                        throw new IllegalStateException(
                                        "Overdue loan has unrepresented contractual fees but no due payment cycle.");
                }
                return new OverdueFeeGap(interestGap, managementGap);
        }

        private static BigDecimal safeMoney(BigDecimal amount) {
                return (amount == null ? ZERO : amount.max(ZERO))
                                .setScale(2, RoundingMode.HALF_UP);
        }

        private BigDecimal totalDueCharge(
                        List<Payment> loanPayments,
                        Payment selected,
                        LocalDate asOf,
                        boolean interest,
                        BigDecimal selectedDue) {

                BigDecimal total = ZERO;
                boolean includedSelected = false;
                for (Payment cycle : loanPayments) {
                        if (cycle == null || cycle.getDueDate() == null
                                        || cycle.getDueDate().isAfter(asOf)
                                        || Boolean.TRUE.equals(cycle.getPaid())) {
                                continue;
                        }
                        if (cycle == selected
                                        || (cycle.getId() != null && selected.getId() != null
                                                        && cycle.getId().equals(selected.getId()))) {
                                includedSelected = true;
                        }
                        total = total.add(cycle == selected
                                        || (cycle.getId() != null && selected.getId() != null
                                                        && cycle.getId().equals(selected.getId()))
                                        ? selectedDue
                                        : (interest
                                                        ? safe(cycle.getCycleInterestRemainingDecimal()).max(ZERO)
                                                        : safe(cycle.getCycleManagementFeeRemainingDecimal()).max(ZERO)));
                }
                if (!includedSelected && selected.getDueDate() != null
                                && !selected.getDueDate().isAfter(asOf)) {
                        total = total.add(selectedDue);
                }
                return roundMoney(total);
        }

        private DueChargeSettlement settleDueCharges(
                        List<Payment> loanPayments,
                        Payment selected,
                        LocalDate asOf,
                        BigDecimal interestPayment,
                        BigDecimal managementPayment,
                        BigDecimal selectedInterestDue,
                        BigDecimal selectedManagementDue) {

                List<Payment> due = new java.util.ArrayList<>(loanPayments.stream()
                                .filter(p -> p != null && p.getDueDate() != null
                                                && !p.getDueDate().isAfter(asOf)
                                                && !Boolean.TRUE.equals(p.getPaid()))
                                .sorted(Comparator.comparing(Payment::getDueDate))
                                .toList());
                if (due.stream().noneMatch(p -> p == selected
                                || (p.getId() != null && p.getId().equals(selected.getId())))
                                && selected.getDueDate() != null
                                && !selected.getDueDate().isAfter(asOf)) {
                        due.add(selected);
                        due.sort(Comparator.comparing(Payment::getDueDate));
                }

                BigDecimal interestLeft = interestPayment;
                BigDecimal managementLeft = managementPayment;
                BigDecimal selectedInterest = ZERO;
                BigDecimal selectedManagement = ZERO;

                for (Payment cycle : due) {
                        boolean selectedCycle = cycle == selected
                                        || (cycle.getId() != null && cycle.getId().equals(selected.getId()));
                        BigDecimal interestOutstanding = selectedCycle
                                        ? selectedInterestDue
                                        : safe(cycle.getCycleInterestRemainingDecimal()).max(ZERO);
                        BigDecimal managementOutstanding = selectedCycle
                                        ? selectedManagementDue
                                        : safe(cycle.getCycleManagementFeeRemainingDecimal()).max(ZERO);
                        BigDecimal settledInterest = interestOutstanding.min(interestLeft);
                        BigDecimal settledManagement = managementOutstanding.min(managementLeft);
                        interestLeft = roundMoney(interestLeft.subtract(settledInterest));
                        managementLeft = roundMoney(managementLeft.subtract(settledManagement));
                        if (cycle == selected || (cycle.getId() != null
                                        && cycle.getId().equals(selected.getId()))) {
                                selectedInterest = settledInterest;
                                selectedManagement = settledManagement;
                        } else {
                                cycle.setCycleInterestRemaining(roundMoney(
                                                safe(cycle.getCycleInterestRemainingDecimal()).subtract(settledInterest)));
                                cycle.setCycleManagementFeeRemaining(roundMoney(
                                                safe(cycle.getCycleManagementFeeRemainingDecimal()).subtract(settledManagement)));
                                // An untouched schedule may carry planned amounts in the
                                // component columns. Only an existing receipt has actual
                                // paid components that can be carried forward.
                                boolean hadReceipt = safe(cycle.getAmountPaidDecimal()).signum() > 0;
                                cycle.setInterestComponent(roundMoney(
                                                (hadReceipt ? safe(cycle.getInterestComponentDecimal()) : ZERO)
                                                                .add(settledInterest)));
                                cycle.setManagementFeeComponent(roundMoney(
                                                (hadReceipt ? safe(cycle.getManagementFeeComponentDecimal()) : ZERO)
                                                                .add(settledManagement)));
                                paymentRepo.save(cycle);
                        }
                }

                if (interestLeft.signum() != 0 || managementLeft.signum() != 0) {
                        throw new IllegalStateException("Due charge allocation exceeds earned charges");
                }
                return new DueChargeSettlement(selectedInterest, selectedManagement);
        }

        private record DueChargeSettlement(
                        BigDecimal interestOnSelected,
                        BigDecimal managementOnSelected) {
        }

        private BigDecimal calculateQualifyingInterestDue(Loan loan, LocalDate asOf) {
                return roundMoney(
                                paymentRepo.findByLoanId(loan.getId()).stream()
                                                .filter(java.util.Objects::nonNull)
                                                .filter(p -> p.getDueDate() != null)
                                                .filter(p -> !p.getDueDate().isAfter(asOf))
                                                .map(p -> safe(p.getCycleInterestRemainingDecimal()))
                                                .reduce(ZERO, BigDecimal::add));
        }

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
