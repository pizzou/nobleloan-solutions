package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.publicportal.PaymentScheduleResponse;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.PaymentSchedule;
import com.patrick.fintech.loan_backend.model.PaymentSchedule.ScheduleStatus;
import com.patrick.fintech.loan_backend.repository.PaymentScheduleRepository;
import com.patrick.fintech.loan_backend.util.FinancialPolicy;

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
public class PaymentScheduleService {

        private final PaymentScheduleRepository repository;

        private final HolidayService holidayService;

        private static final BigDecimal MIN_LOAN_AMOUNT = new BigDecimal("500000.00");

        private static final int MAX_LOAN_DURATION_MONTHS = 6;

        private static final int MIN_LOAN_DURATION_MONTHS = 1;

        /*
         * FinancialPolicy is the single source of truth for platform defaults.
         *
         * These are fallback values only. When the Loan already contains an
         * explicit rate, the loan's stored rate is used.
         */
        private static final BigDecimal MONTHLY_INTEREST_RATE = FinancialPolicy.MONTHLY_INTEREST_RATE;

        private static final BigDecimal MONTHLY_MANAGEMENT_FEE_RATE = FinancialPolicy.MONTHLY_MANAGEMENT_FEE_RATE;

        /**
         * One-time processing fee fallback.
         */
        private static final BigDecimal PROCESSING_FEE_RATE = FinancialPolicy.PROCESSING_FEE_RATE;

        // ================================================================
        // GENERAL CONSTANTS
        // ================================================================

        private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

        private static final BigDecimal TWELVE = new BigDecimal("12");

        private static final BigDecimal ZERO = BigDecimal.ZERO;

        private static final BigDecimal ONE = BigDecimal.ONE;

        private static final int CALCULATION_SCALE = 16;

        private static final int MONEY_SCALE = 2;

        private static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;

        // ================================================================
        // GET SCHEDULE
        // ================================================================

        @Transactional(readOnly = true)
        public List<PaymentScheduleResponse> getSchedule(Long loanId) {

                if (loanId == null) {
                        throw new IllegalArgumentException(
                                        "Loan ID is required");
                }

                return repository
                                .findByLoanIdOrderByInstallmentNumberAsc(loanId)
                                .stream()
                                .map(this::toResponse)
                                .toList();
        }

        // ================================================================
        // CONVERT TO RESPONSE
        // ================================================================

        private PaymentScheduleResponse toResponse(
                        PaymentSchedule schedule) {

                if (schedule == null) {
                        return null;
                }

                return PaymentScheduleResponse.builder()
                                .installmentNumber(
                                                schedule.getInstallmentNumber())
                                .dueDate(
                                                schedule.getDueDate())
                                .installmentAmount(
                                                money(schedule.getInstallmentAmount()))
                                .principal(
                                                money(schedule.getPrincipalAmount()))
                                .interest(
                                                money(schedule.getInterestAmount()))
                                .managementFee(
                                                money(schedule.getManagementFeeAmount()))
                                .penalty(
                                                money(schedule.getPenaltyAmount()))
                                .paid(
                                                money(schedule.getAmountPaid()))
                                .balance(
                                                money(schedule.getRemainingBalance()))
                                .status(
                                                schedule.getStatus() != null
                                                                ? schedule.getStatus().name()
                                                                : ScheduleStatus.PENDING.name())
                                .build();
        }

        // ================================================================
        // GENERATE SCHEDULE
        // ================================================================

        @Transactional
        public void generateSchedule(Loan loan) {

                validateLoanForSchedule(loan);

                BigDecimal principal = normalizeMoney(loan.getAmountDecimal());

                int months = loan.getDurationMonths();

                List<PaymentSchedule> existingSchedules = repository.findByLoanIdOrderByInstallmentNumberAsc(
                                loan.getId());

                if (existingSchedules != null
                                && !existingSchedules.isEmpty()) {

                        boolean hasPaymentActivity = existingSchedules.stream()
                                        .anyMatch(this::hasPaymentActivity);

                        if (hasPaymentActivity) {
                                throw new IllegalStateException(
                                                "Cannot regenerate payment schedule for loan "
                                                                + loan.getReferenceNumber()
                                                                + " because payment activity already exists.");
                        }

                        repository.deleteByLoanId(loan.getId());
                }

                /*
                 * IMPORTANT:
                 *
                 * The actual rate stored on the loan takes precedence.
                 * FinancialPolicy values are defaults only.
                 *
                 * This prevents:
                 *
                 * Loan contract rate
                 * !=
                 * Schedule rate
                 * !=
                 * Payment rate
                 */
                BigDecimal interestRate = normalizeNonNegativeRate(
                                loan.getInterestRateDecimal(),
                                MONTHLY_INTEREST_RATE);

                BigDecimal managementFeeRate = normalizeNonNegativeRate(
                                loan.getManagementFeeRateDecimal(),
                                MONTHLY_MANAGEMENT_FEE_RATE);

                BigDecimal processingFeeRate = normalizeNonNegativeRate(
                                loan.getProcessingFeeRateDecimal(),
                                PROCESSING_FEE_RATE);

                BigDecimal processingFee = calculateProcessingFee(
                                principal,
                                processingFeeRate);

                /*
                 * Persist the normalized contractual rates back onto the loan.
                 */
                loan.setAmount(principal);
                loan.setOutstandingBalance(principal);

                loan.setInterestRate(interestRate);
                loan.setManagementFeeRate(managementFeeRate);
                loan.setProcessingFeeRate(processingFeeRate);

                loan.setInterestRateType("MONTHLY");
                loan.setProcessingFee(processingFee);

                LocalDate baseDate = resolveScheduleStartDate(loan);

                Long organizationId = loan.getOrganization().getId();

                BigDecimal balance = principal;

                BigDecimal accumulatedInterest = ZERO;

                BigDecimal accumulatedManagementFee = ZERO;

                for (int installmentNumber = 1; installmentNumber <= months; installmentNumber++) {

                        balance = money(balance);

                        LocalDate periodEnd = baseDate.plusMonths(installmentNumber);

                        /*
                         * Contractual schedule:
                         *
                         * - equal principal
                         * - interest calculated from opening principal
                         * - management fee calculated from opening principal
                         * - final installment receives exact residual principal
                         */
                        FinancialPolicy.ScheduleLine pricingLine = FinancialPolicy.contractualScheduleLine(
                                        balance,
                                        months - installmentNumber + 1,
                                        interestRate,
                                        managementFeeRate);

                        BigDecimal principalComponent = money(pricingLine.principal());

                        BigDecimal interest = money(pricingLine.interest());

                        BigDecimal managementFee = money(pricingLine.managementFee());

                        BigDecimal installmentAmount = money(pricingLine.installment());

                        balance = money(pricingLine.remainingBalance());

                        LocalDate rawDueDate = periodEnd;

                        LocalDate dueDate = holidayService.adjustToBusinessDay(
                                        organizationId,
                                        rawDueDate);

                        PaymentSchedule schedule = PaymentSchedule.builder()
                                        .loan(loan)
                                        .installmentNumber(
                                                        installmentNumber)
                                        .dueDate(dueDate)
                                        .installmentAmount(
                                                        installmentAmount)
                                        .principalAmount(
                                                        principalComponent)
                                        .interestAmount(
                                                        interest)
                                        .managementFeeAmount(
                                                        managementFee)
                                        .penaltyAmount(ZERO)
                                        .amountPaid(ZERO)
                                        .remainingBalance(balance)
                                        .status(ScheduleStatus.PENDING)
                                        .build();

                        repository.save(schedule);

                        accumulatedInterest = money(
                                        accumulatedInterest.add(
                                                        interest));

                        accumulatedManagementFee = money(
                                        accumulatedManagementFee.add(
                                                        managementFee));
                }

                /*
                 * Contractual total repayable excludes processing fee because
                 * processing fee is represented separately on the loan.
                 */
                BigDecimal totalRepayable = money(
                                principal
                                                .add(accumulatedInterest)
                                                .add(accumulatedManagementFee));

                loan.setTotalInterest(
                                accumulatedInterest);

                loan.setManagementFee(
                                accumulatedManagementFee);

                loan.setTotalRepayable(
                                totalRepayable);

                loan.setNextDueDate(
                                holidayService.adjustToBusinessDay(
                                                organizationId,
                                                baseDate.plusMonths(1)));

                PaymentSchedule first = repository
                                .findFirstByLoanIdAndStatusOrderByInstallmentNumberAsc(
                                                loan.getId(),
                                                ScheduleStatus.PENDING)
                                .orElse(null);

                if (first != null) {

                        loan.setNextPaymentDate(
                                        first.getDueDate());

                        loan.setNextInstallmentAmount(
                                        first.getInstallmentAmount());
                }

                log.info(
                                "Generated contractual equal-principal monthly schedule. "
                                                + "loanId={}, principal={}, months={}, "
                                                + "interestRate={}, managementFeeRate={}, "
                                                + "processingFeeRate={}, totalInterest={}, "
                                                + "totalManagementFee={}, totalRepayable={}",
                                loan.getId(),
                                principal,
                                months,
                                interestRate,
                                managementFeeRate,
                                processingFeeRate,
                                accumulatedInterest,
                                accumulatedManagementFee,
                                totalRepayable);
        }

        // ================================================================
        // REFRESH NEXT INSTALLMENT
        // ================================================================

        /**
         * Recalculates the next pending installment from the loan's current
         * outstanding principal.
         *
         * The loan's stored contractual rates are used rather than silently
         * replacing them with platform defaults.
         */
        @Transactional
        public PaymentSchedule refreshNextInstallment(
                        Loan loan) {

                validateLoanForSchedule(loan);

                PaymentSchedule next = repository
                                .findFirstByLoanIdAndStatusOrderByInstallmentNumberAsc(
                                                loan.getId(),
                                                ScheduleStatus.PENDING)
                                .orElse(null);

                if (next == null) {
                        return null;
                }

                BigDecimal outstanding = normalizeMoney(
                                loan.getOutstandingBalanceDecimal())
                                .max(ZERO);

                if (outstanding.compareTo(ZERO) <= 0) {

                        next.setPrincipalAmount(ZERO);
                        next.setInterestAmount(ZERO);
                        next.setManagementFeeAmount(ZERO);
                        next.setInstallmentAmount(ZERO);
                        next.setRemainingBalance(ZERO);

                        return repository.save(next);
                }

                int installmentNumber = next.getInstallmentNumber();

                int totalMonths = loan.getDurationMonths();

                int remainingInstallments = Math.max(
                                1,
                                totalMonths
                                                - installmentNumber
                                                + 1);

                /*
                 * CRITICAL:
                 *
                 * Do not use the global 5% defaults here if the loan has its own
                 * contractual rates.
                 */
                BigDecimal interestRate = normalizeNonNegativeRate(
                                loan.getInterestRateDecimal(),
                                MONTHLY_INTEREST_RATE);

                BigDecimal managementFeeRate = normalizeNonNegativeRate(
                                loan.getManagementFeeRateDecimal(),
                                MONTHLY_MANAGEMENT_FEE_RATE);

                FinancialPolicy.ScheduleLine pricingLine = FinancialPolicy.contractualScheduleLine(
                                outstanding,
                                remainingInstallments,
                                interestRate,
                                managementFeeRate);

                BigDecimal principalComponent = money(pricingLine.principal());

                BigDecimal interest = money(pricingLine.interest());

                BigDecimal managementFee = money(pricingLine.managementFee());

                BigDecimal installmentAmount = money(pricingLine.installment());

                next.setPrincipalAmount(
                                principalComponent);

                next.setInterestAmount(
                                interest);

                next.setManagementFeeAmount(
                                managementFee);

                next.setInstallmentAmount(
                                installmentAmount);

                next.setRemainingBalance(
                                money(
                                                pricingLine.remainingBalance()));

                PaymentSchedule saved = repository.save(next);

                loan.setNextDueDate(
                                saved.getDueDate());

                loan.setNextPaymentDate(
                                saved.getDueDate());

                loan.setNextInstallmentAmount(
                                saved.getInstallmentAmount());

                return saved;
        }

        // ================================================================
        // LOAN VALIDATION
        // ================================================================

        private void validateLoanForSchedule(
                        Loan loan) {

                if (loan == null) {
                        throw new IllegalArgumentException(
                                        "Loan cannot be null");
                }

                if (loan.getId() == null) {
                        throw new IllegalArgumentException(
                                        "Loan must be persisted before generating a schedule");
                }

                if (loan.getOrganization() == null
                                || loan.getOrganization().getId() == null) {

                        throw new IllegalArgumentException(
                                        "Loan organization is required");
                }

                BigDecimal principal = loan.getAmountDecimal();

                if (principal == null
                                || principal.compareTo(ZERO) <= 0) {

                        throw new IllegalArgumentException(
                                        "Loan principal must be greater than zero");
                }

                if (principal.compareTo(
                                MIN_LOAN_AMOUNT) < 0) {

                        throw new IllegalArgumentException(
                                        "Loan principal cannot be below "
                                                        + MIN_LOAN_AMOUNT
                                                        + ".");
                }

                validateLoanDuration(
                                loan.getDurationMonths());
        }

        // ================================================================
        // RATE NORMALIZATION
        // ================================================================

        /**
         * Resolves a contractual rate safely.
         *
         * Rates are percentage values:
         *
         * 5.00 = 5%
         * 2.00 = 2%
         * 15.00 = 15%
         *
         * The explicitly stored loan rate takes precedence.
         * The FinancialPolicy rate is used only when the loan rate is null.
         *
         * A negative rate is always rejected.
         */
        private BigDecimal normalizeNonNegativeRate(
                        BigDecimal configuredRate,
                        BigDecimal fallbackRate) {

                if (configuredRate == null) {

                        if (fallbackRate == null
                                        || fallbackRate.compareTo(ZERO) < 0) {

                                throw new IllegalArgumentException(
                                                "Fallback pricing rate cannot be null or negative");
                        }

                        return money(fallbackRate);
                }

                if (configuredRate.compareTo(ZERO) < 0) {

                        throw new IllegalArgumentException(
                                        "Pricing rate cannot be negative: "
                                                        + configuredRate);
                }

                return money(configuredRate);
        }

        // ================================================================
        // PROCESSING FEE
        // ================================================================

        /**
         * Calculates the one-time processing fee from the actual configured
         * processing fee rate.
         */
        private BigDecimal calculateProcessingFee(
                        BigDecimal principal,
                        BigDecimal processingFeeRate) {

                if (principal == null
                                || principal.compareTo(ZERO) <= 0) {

                        return money(ZERO);
                }

                if (processingFeeRate == null
                                || processingFeeRate.compareTo(ZERO) < 0) {

                        throw new IllegalArgumentException(
                                        "Processing fee rate cannot be null or negative");
                }

                return money(
                                principal
                                                .multiply(processingFeeRate)
                                                .divide(
                                                                ONE_HUNDRED,
                                                                CALCULATION_SCALE,
                                                                RoundingMode.HALF_UP));
        }

        // ================================================================
        // PAYMENT ACTIVITY
        // ================================================================

        private boolean hasPaymentActivity(
                        PaymentSchedule schedule) {

                if (schedule == null) {
                        return false;
                }

                BigDecimal amountPaid = schedule.getAmountPaid();

                if (amountPaid != null
                                && amountPaid.compareTo(ZERO) > 0) {

                        return true;
                }

                ScheduleStatus status = schedule.getStatus();

                return status == ScheduleStatus.PAID
                                || status == ScheduleStatus.PARTIAL;
        }

        // ================================================================
        // NEXT INSTALLMENT
        // ================================================================

        @Transactional(readOnly = true)
        public PaymentSchedule getNextInstallment(
                        Long loanId) {

                if (loanId == null) {

                        throw new IllegalArgumentException(
                                        "Loan ID is required");
                }

                return repository
                                .findFirstByLoanIdAndStatusOrderByInstallmentNumberAsc(
                                                loanId,
                                                ScheduleStatus.PENDING)
                                .orElseGet(
                                                () -> repository
                                                                .findFirstByLoanIdAndStatusOrderByInstallmentNumberAsc(
                                                                                loanId,
                                                                                ScheduleStatus.PARTIAL)
                                                                .orElse(null));
        }

        // ================================================================
        // MONTHLY RATE
        // ================================================================

        /**
         * Converts a percentage rate to a monthly decimal fraction.
         *
         * Example:
         *
         * 5.00 MONTHLY -> 0.05
         *
         * 12.00 ANNUAL -> 0.01 monthly
         *
         * This method is retained for compatibility with existing callers.
         */
        private BigDecimal calculateMonthlyRate(
                        BigDecimal rate,
                        String rateType) {

                if (rate == null) {

                        throw new IllegalArgumentException(
                                        "Interest rate cannot be null");
                }

                if (rate.compareTo(ZERO) < 0) {

                        throw new IllegalArgumentException(
                                        "Interest rate cannot be negative");
                }

                if (rateType == null
                                || rateType.isBlank()) {

                        throw new IllegalArgumentException(
                                        "Interest rate type is required");
                }

                if ("MONTHLY".equalsIgnoreCase(
                                rateType)) {

                        return rate.divide(
                                        ONE_HUNDRED,
                                        CALCULATION_SCALE,
                                        RoundingMode.HALF_UP);
                }

                if ("ANNUAL".equalsIgnoreCase(
                                rateType)) {

                        return rate
                                        .divide(
                                                        ONE_HUNDRED,
                                                        CALCULATION_SCALE,
                                                        RoundingMode.HALF_UP)
                                        .divide(
                                                        TWELVE,
                                                        CALCULATION_SCALE,
                                                        RoundingMode.HALF_UP);
                }

                throw new IllegalArgumentException(
                                "Interest rate type must be MONTHLY or ANNUAL");
        }

        // ================================================================
        // MONTHLY PAYMENT
        // ================================================================

        /**
         * Retained for compatibility with existing callers.
         *
         * Note:
         * The contractual Noble Loan schedule generated above uses
         * equal-principal repayment through FinancialPolicy.contractualScheduleLine().
         * This EMI helper must not be used to reconstruct that schedule.
         */
        private BigDecimal calculateMonthlyPayment(
                        BigDecimal principal,
                        BigDecimal monthlyRate,
                        int months) {

                if (principal == null) {

                        throw new IllegalArgumentException(
                                        "Principal cannot be null");
                }

                if (monthlyRate == null) {

                        throw new IllegalArgumentException(
                                        "Monthly rate cannot be null");
                }

                if (months <= 0) {

                        throw new IllegalArgumentException(
                                        "Number of months must be greater than zero");
                }

                if (principal.compareTo(ZERO) <= 0) {

                        throw new IllegalArgumentException(
                                        "Principal must be greater than zero");
                }

                if (monthlyRate.compareTo(ZERO) == 0) {

                        return money(
                                        principal.divide(
                                                        BigDecimal.valueOf(months),
                                                        CALCULATION_SCALE,
                                                        RoundingMode.HALF_UP));
                }

                BigDecimal onePlusRate = ONE.add(monthlyRate);

                BigDecimal positiveFactor = onePlusRate.pow(
                                months,
                                CALCULATION_CONTEXT);

                BigDecimal inverseFactor = ONE.divide(
                                positiveFactor,
                                CALCULATION_SCALE,
                                RoundingMode.HALF_UP);

                BigDecimal denominator = ONE.subtract(inverseFactor);

                if (denominator.compareTo(ZERO) <= 0) {

                        throw new IllegalArgumentException(
                                        "Invalid monthly interest calculation");
                }

                BigDecimal payment = principal
                                .multiply(monthlyRate)
                                .divide(
                                                denominator,
                                                CALCULATION_SCALE,
                                                RoundingMode.HALF_UP);

                return money(payment);
        }

        // ================================================================
        // RESOLVE SCHEDULE START DATE
        // ================================================================

        private LocalDate resolveScheduleStartDate(
                        Loan loan) {

                /*
                 * The exact disbursement timestamp is retained on the Loan.
                 *
                 * The contractual monthly schedule itself uses the calendar date.
                 * Daily earned-interest calculations belong to PaymentService and
                 * must use the actual disbursement/payment timestamps.
                 */
                if (loan.getDisbursedAt() != null) {

                        return loan
                                        .getDisbursedAt()
                                        .toLocalDate();
                }

                /*
                 * Compatibility with legacy/test records.
                 */
                if (loan.getStartDate() != null) {

                        return loan.getStartDate();
                }

                return LocalDate.now();
        }

        // ================================================================
        // DURATION VALIDATION
        // ================================================================

        private void validateLoanDuration(
                        Integer months) {

                if (months == null) {

                        throw new IllegalArgumentException(
                                        "Loan duration is required");
                }

                if (months < MIN_LOAN_DURATION_MONTHS) {

                        throw new IllegalArgumentException(
                                        "Loan duration must be at least "
                                                        + MIN_LOAN_DURATION_MONTHS
                                                        + " month");
                }

                if (months > MAX_LOAN_DURATION_MONTHS) {

                        throw new IllegalArgumentException(
                                        "Loan duration cannot exceed "
                                                        + MAX_LOAN_DURATION_MONTHS
                                                        + " months");
                }
        }

        // ================================================================
        // MONEY HELPERS
        // ================================================================

        private BigDecimal money(
                        BigDecimal value) {

                if (value == null) {

                        return ZERO.setScale(
                                        MONEY_SCALE,
                                        RoundingMode.HALF_UP);
                }

                return value.setScale(
                                MONEY_SCALE,
                                RoundingMode.HALF_UP);
        }

        private BigDecimal normalizeMoney(
                        BigDecimal value) {

                if (value == null) {

                        throw new IllegalArgumentException(
                                        "Money value cannot be null");
                }

                return money(value);
        }

        private BigDecimal safeMoney(
                        BigDecimal value) {

                return money(value);
        }
}