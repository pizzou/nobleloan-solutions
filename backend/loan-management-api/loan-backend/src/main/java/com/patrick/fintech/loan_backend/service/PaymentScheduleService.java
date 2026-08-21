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

        /**
         * Holiday/business-day service.
         *
         * The schedule due dates are adjusted to the organization's
         * next valid business day.
         */
        private final HolidayService holidayService;

        // ================================================================
        // PLATFORM FINANCIAL RULES
        // ================================================================

        /**
         * Minimum principal allowed for every loan type.
         */
        private static final BigDecimal MIN_LOAN_AMOUNT = new BigDecimal("500000.00");

        /**
         * Maximum duration for every loan type.
         */
        private static final int MAX_LOAN_DURATION_MONTHS = 6;

        /**
         * Minimum duration for every loan type.
         */
        private static final int MIN_LOAN_DURATION_MONTHS = 1;

        /**
         * Monthly contractual loan interest.
         */
        private static final BigDecimal MONTHLY_INTEREST_RATE = FinancialPolicy.MONTHLY_INTEREST_RATE;

        /**
         * Monthly management fee.
         */
        private static final BigDecimal MONTHLY_MANAGEMENT_FEE_RATE = FinancialPolicy.MONTHLY_MANAGEMENT_FEE_RATE;

        /**
         * Combined monthly charge.
         *
         * 5% interest + 5% management = 10%.
         */
        private static final BigDecimal TOTAL_MONTHLY_CHARGE_RATE = MONTHLY_INTEREST_RATE.add(
                        MONTHLY_MANAGEMENT_FEE_RATE);

        /**
         * One-time processing fee.
         */
        private static final BigDecimal PROCESSING_FEE_RATE = FinancialPolicy.PROCESSING_FEE_RATE;

        // ================================================================
        // GENERAL CONSTANTS
        // ================================================================

        private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

        private static final BigDecimal TWELVE = new BigDecimal("12");

        private static final BigDecimal ZERO = BigDecimal.ZERO;

        private static final BigDecimal ONE = BigDecimal.ONE;

        private static final BigDecimal ONE_CENT = new BigDecimal("0.01");

        private static final int CALCULATION_SCALE = 16;

        private static final int MONEY_SCALE = 2;

        private static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;

        // ================================================================
        // GET SCHEDULE
        // ================================================================

        @Transactional(readOnly = true)
        public List<PaymentScheduleResponse> getSchedule(
                        Long loanId) {

                if (loanId == null) {

                        throw new IllegalArgumentException(
                                        "Loan ID is required");
                }

                return repository
                                .findByLoanIdOrderByInstallmentNumberAsc(
                                                loanId)
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
                                                money(
                                                                schedule.getInstallmentAmount()))
                                .principal(
                                                money(
                                                                schedule.getPrincipalAmount()))
                                .interest(
                                                money(
                                                                schedule.getInterestAmount()))
                                .managementFee(
                                                money(
                                                                schedule.getManagementFeeAmount()))
                                .penalty(
                                                money(
                                                                schedule.getPenaltyAmount()))
                                .paid(
                                                money(
                                                                schedule.getAmountPaid()))
                                .balance(
                                                money(
                                                                schedule.getRemainingBalance()))
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
        public void generateSchedule(
                        Loan loan) {

                validateLoanForSchedule(loan);

                BigDecimal principal = normalizeMoney(loan.getAmountDecimal());
                int months = loan.getDurationMonths();

                List<PaymentSchedule> existingSchedules = repository
                                .findByLoanIdOrderByInstallmentNumberAsc(loan.getId());

                if (existingSchedules != null && !existingSchedules.isEmpty()) {
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

                BigDecimal interestRate = MONTHLY_INTEREST_RATE;
                BigDecimal managementFeeRate = MONTHLY_MANAGEMENT_FEE_RATE;
                BigDecimal processingFee = calculateProcessingFee(principal);

                loan.setAmount(principal);
                loan.setOutstandingBalance(principal);
                loan.setInterestRate(interestRate);
                loan.setManagementFeeRate(managementFeeRate);
                loan.setProcessingFeeRate(PROCESSING_FEE_RATE);
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

                        FinancialPolicy.ScheduleLine pricingLine = FinancialPolicy.contractualScheduleLine(
                                        balance,
                                        months - installmentNumber + 1,
                                        MONTHLY_INTEREST_RATE,
                                        MONTHLY_MANAGEMENT_FEE_RATE);

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
                                        .installmentNumber(installmentNumber)
                                        .dueDate(dueDate)
                                        .installmentAmount(installmentAmount)
                                        .principalAmount(principalComponent)
                                        .interestAmount(interest)
                                        .managementFeeAmount(managementFee)
                                        .penaltyAmount(ZERO)
                                        .amountPaid(ZERO)
                                        .remainingBalance(balance)
                                        .status(ScheduleStatus.PENDING)
                                        .build();

                        repository.save(schedule);

                        accumulatedInterest = money(accumulatedInterest.add(interest));
                        accumulatedManagementFee = money(accumulatedManagementFee.add(managementFee));
                }

                BigDecimal totalRepayable = money(
                                principal
                                                .add(accumulatedInterest)
                                                .add(accumulatedManagementFee));

                loan.setTotalInterest(accumulatedInterest);
                loan.setManagementFee(accumulatedManagementFee);
                loan.setTotalRepayable(totalRepayable);
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
                        loan.setNextPaymentDate(first.getDueDate());
                        loan.setNextInstallmentAmount(first.getInstallmentAmount());
                }

                log.info(
                                "Generated contractual equal-principal monthly schedule. loanId={}, principal={}, months={}, totalInterest={}, totalManagementFee={}, totalRepayable={}",
                                loan.getId(),
                                principal,
                                months,
                                accumulatedInterest,
                                accumulatedManagementFee,
                                totalRepayable);
        }

        /**
         * Recalculates the next pending installment from the loan's current
         * outstanding principal. This is called after a completed payment cycle
         * so the next installment never continues using the original principal.
         */
        @Transactional
        public PaymentSchedule refreshNextInstallment(Loan loan) {
                validateLoanForSchedule(loan);

                PaymentSchedule next = repository
                                .findFirstByLoanIdAndStatusOrderByInstallmentNumberAsc(
                                                loan.getId(),
                                                ScheduleStatus.PENDING)
                                .orElse(null);

                if (next == null) {
                        return null;
                }

                BigDecimal outstanding = normalizeMoney(loan.getOutstandingBalanceDecimal()).max(ZERO);
                if (outstanding.compareTo(ZERO) <= 0) {
                        next.setPrincipalAmount(ZERO);
                        next.setInterestAmount(ZERO);
                        next.setManagementFeeAmount(ZERO);
                        next.setInstallmentAmount(ZERO);
                        next.setRemainingBalance(ZERO);
                        return repository.save(next);
                }

                LocalDate baseDate = resolveScheduleStartDate(loan);
                int installmentNumber = next.getInstallmentNumber();
                int totalMonths = loan.getDurationMonths();

                int remainingInstallments = Math.max(
                                1,
                                totalMonths - installmentNumber + 1);

                FinancialPolicy.ScheduleLine pricingLine = FinancialPolicy.contractualScheduleLine(
                                outstanding,
                                remainingInstallments,
                                MONTHLY_INTEREST_RATE,
                                MONTHLY_MANAGEMENT_FEE_RATE);

                BigDecimal principalComponent = money(pricingLine.principal());
                BigDecimal interest = money(pricingLine.interest());
                BigDecimal managementFee = money(pricingLine.managementFee());
                BigDecimal installmentAmount = money(pricingLine.installment());

                next.setPrincipalAmount(principalComponent);
                next.setInterestAmount(interest);
                next.setManagementFeeAmount(managementFee);
                next.setInstallmentAmount(installmentAmount);
                next.setRemainingBalance(money(pricingLine.remainingBalance()));

                PaymentSchedule saved = repository.save(next);

                loan.setNextDueDate(saved.getDueDate());
                loan.setNextPaymentDate(saved.getDueDate());
                loan.setNextInstallmentAmount(saved.getInstallmentAmount());

                return saved;
        }

        private void validateLoanForSchedule(Loan loan) {
                if (loan == null) {
                        throw new IllegalArgumentException("Loan cannot be null");
                }
                if (loan.getId() == null) {
                        throw new IllegalArgumentException("Loan must be persisted before generating a schedule");
                }
                if (loan.getOrganization() == null || loan.getOrganization().getId() == null) {
                        throw new IllegalArgumentException("Loan organization is required");
                }

                BigDecimal principal = loan.getAmountDecimal();
                if (principal == null || principal.compareTo(ZERO) <= 0) {
                        throw new IllegalArgumentException("Loan principal must be greater than zero");
                }
                if (principal.compareTo(MIN_LOAN_AMOUNT) < 0) {
                        throw new IllegalArgumentException("Loan principal cannot be below " + MIN_LOAN_AMOUNT + ".");
                }

                validateLoanDuration(loan.getDurationMonths());
        }

        private BigDecimal calculateProcessingFee(BigDecimal principal) {
                return money(
                                principal.multiply(PROCESSING_FEE_RATE)
                                                .divide(ONE_HUNDRED, CALCULATION_SCALE, RoundingMode.HALF_UP));
        }

        // ================================================================
        // CHECK PAYMENT ACTIVITY
        // ================================================================

        private boolean hasPaymentActivity(
                        PaymentSchedule schedule) {

                if (schedule == null) {
                        return false;
                }

                BigDecimal amountPaid = schedule.getAmountPaid();

                if (amountPaid != null
                                && amountPaid.compareTo(
                                                ZERO) > 0) {

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

                if (principal.compareTo(
                                ZERO) <= 0) {

                        throw new IllegalArgumentException(
                                        "Principal must be greater than zero");
                }

                // ============================================================
                // ZERO RATE
                // ============================================================

                if (monthlyRate.compareTo(
                                ZERO) == 0) {

                        return money(
                                        principal.divide(
                                                        BigDecimal.valueOf(months),
                                                        CALCULATION_SCALE,
                                                        RoundingMode.HALF_UP));
                }

                // ============================================================
                // EMI USING BIGDECIMAL
                // ============================================================

                BigDecimal onePlusRate = ONE.add(
                                monthlyRate);

                BigDecimal positiveFactor = onePlusRate.pow(
                                months,
                                CALCULATION_CONTEXT);

                BigDecimal inverseFactor = ONE.divide(
                                positiveFactor,
                                CALCULATION_SCALE,
                                RoundingMode.HALF_UP);

                BigDecimal denominator = ONE.subtract(
                                inverseFactor);

                if (denominator.compareTo(
                                ZERO) <= 0) {

                        throw new IllegalArgumentException(
                                        "Invalid monthly interest calculation");
                }

                BigDecimal payment = principal
                                .multiply(
                                                monthlyRate)
                                .divide(
                                                denominator,
                                                CALCULATION_SCALE,
                                                RoundingMode.HALF_UP);

                return money(
                                payment);
        }

        // ================================================================
        // RESOLVE SCHEDULE START DATE
        // ================================================================

        private LocalDate resolveScheduleStartDate(
                        Loan loan) {

                /*
                 * Exact disbursement timestamp is now LocalDateTime.
                 *
                 * The contractual monthly schedule uses its calendar date.
                 */
                if (loan.getDisbursedAt() != null) {

                        return loan
                                        .getDisbursedAt()
                                        .toLocalDate();
                }

                /*
                 * Keep compatibility with legacy/test records.
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

                return money(
                                value);
        }

        private BigDecimal safeMoney(
                        BigDecimal value) {

                return money(
                                value);
        }
}