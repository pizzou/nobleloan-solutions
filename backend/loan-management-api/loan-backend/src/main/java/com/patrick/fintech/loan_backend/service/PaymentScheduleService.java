package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.publicportal.PaymentScheduleResponse;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.PaymentSchedule;
import com.patrick.fintech.loan_backend.model.PaymentSchedule.ScheduleStatus;
import com.patrick.fintech.loan_backend.repository.PaymentScheduleRepository;

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
public class PaymentScheduleService {

        private final PaymentScheduleRepository repository;

        private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

        private static final BigDecimal TWELVE = new BigDecimal("12");

        private static final BigDecimal ZERO = BigDecimal.ZERO;

        private static final BigDecimal ONE = BigDecimal.ONE;

        private static final BigDecimal ONE_CENT = new BigDecimal("0.01");

       
        private static final int CALCULATION_SCALE = 16;

        /**
         * Database/API monetary precision.
         */
        private static final int MONEY_SCALE = 2;

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
                                                money(
                                                                schedule.getInstallmentAmount()))
                                .principal(
                                                money(
                                                                schedule.getPrincipalAmount()))
                                .interest(
                                                money(
                                                                schedule.getInterestAmount()))
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
            Loan loan
    ) {

        // ------------------------------------------------------------
        // BASIC VALIDATION
        // ------------------------------------------------------------

        if (loan == null) {
            throw new IllegalArgumentException(
                    "Loan cannot be null"
            );
        }

        if (loan.getId() == null) {
            throw new IllegalArgumentException(
                    "Loan must be persisted before generating a schedule"
            );
        }

        if (
                loan.getOrganization() == null
                        || loan.getOrganization().getId() == null
        ) {
            throw new IllegalArgumentException(
                    "Loan organization is required"
            );
        }

        BigDecimal amount =
                loan.getAmountDecimal();

        if (amount == null) {
            throw new IllegalArgumentException(
                    "Loan principal is required"
            );
        }

        BigDecimal interestRate =
                loan.getInterestRateDecimal();

        if (interestRate == null) {
            throw new IllegalArgumentException(
                    "Loan interest rate is required"
            );
        }

        if (loan.getDurationMonths() == null) {
            throw new IllegalArgumentException(
                    "Loan duration is required"
            );
        }

        // ------------------------------------------------------------
        // DURATION
        // ------------------------------------------------------------

        int months =
                loan.getDurationMonths();

        if (
                months < Loan.MIN_LOAN_DURATION_MONTHS
                        || months > Loan.MAX_LOAN_DURATION_MONTHS
        ) {
            throw new IllegalArgumentException(
                    "Loan duration must be between "
                            + Loan.MIN_LOAN_DURATION_MONTHS
                            + " and "
                            + Loan.MAX_LOAN_DURATION_MONTHS
                            + " months"
            );
        }

        // ------------------------------------------------------------
        // NORMALIZE PRINCIPAL
        // ------------------------------------------------------------

        BigDecimal principal =
                normalizeMoney(amount);

        if (principal.compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Loan principal must be greater than zero"
            );
        }

        // ------------------------------------------------------------
        // VALIDATE INTEREST RATE
        // ------------------------------------------------------------

        BigDecimal rate =
                interestRate.setScale(
                        CALCULATION_SCALE,
                        RoundingMode.HALF_UP
                );

        if (rate.compareTo(ZERO) < 0) {
            throw new IllegalArgumentException(
                    "Loan interest rate cannot be negative"
            );
        }

        // ------------------------------------------------------------
        // INTEREST RATE TYPE
        // ------------------------------------------------------------

        String rateType =
                loan.getInterestRateType();

        if (
                rateType == null
                        || rateType.isBlank()
        ) {
            rateType = "MONTHLY";
        }

        rateType =
                rateType
                        .trim()
                        .toUpperCase();

        validateRateType(rateType);

        // ------------------------------------------------------------
        // MANAGEMENT FEE RATE
        // ------------------------------------------------------------

        BigDecimal managementFeeRate =
                loan.getManagementFeeRateDecimal();

        if (managementFeeRate == null) {
            managementFeeRate =
                    Loan.DEFAULT_MONTHLY_MANAGEMENT_FEE_RATE;
        }

        managementFeeRate =
                managementFeeRate.setScale(
                        CALCULATION_SCALE,
                        RoundingMode.HALF_UP
                );

        if (managementFeeRate.compareTo(ZERO) < 0) {
            throw new IllegalArgumentException(
                    "Management fee rate cannot be negative"
            );
        }

       

        BigDecimal monthlyManagementFeeRate =
                managementFeeRate.divide(
                        ONE_HUNDRED,
                        CALCULATION_SCALE,
                        RoundingMode.HALF_UP
                );

        // ------------------------------------------------------------
        // RESOLVE SCHEDULE START DATE
        // ------------------------------------------------------------

        LocalDate baseDate =
                resolveScheduleStartDate(loan);

      

        List<PaymentSchedule> existingSchedules =
                repository.findByLoanIdOrderByInstallmentNumberAsc(
                        loan.getId()
                );

        if (!existingSchedules.isEmpty()) {

            boolean hasPaymentActivity =
                    existingSchedules
                            .stream()
                            .anyMatch(this::hasPaymentActivity);

            if (hasPaymentActivity) {

                throw new IllegalStateException(
                        "Cannot regenerate payment schedule for loan "
                                + safeLoanReference(loan)
                                + " because payment activity already exists"
                );
            }

           
            repository.deleteByLoanId(
                    loan.getId()
            );
        }

        // ------------------------------------------------------------
        // CALCULATE MONTHLY INTEREST RATE
        // ------------------------------------------------------------

        BigDecimal monthlyInterestRate =
                calculateMonthlyRate(
                        rate,
                        rateType
                );

     

        BigDecimal baseMonthlyPayment =
                calculateMonthlyPayment(
                        principal,
                        monthlyInterestRate,
                        months
                );

        log.info(
                "Generating payment schedule for loan {}: principal={}, "
                        + "interestRate={}, rateType={}, managementFeeRate={}, "
                        + "months={}, monthlyInterestRate={}, baseMonthlyPayment={}",
                safeLoanReference(loan),
                principal,
                rate,
                rateType,
                managementFeeRate,
                months,
                monthlyInterestRate,
                baseMonthlyPayment
        );

        // ------------------------------------------------------------
        // GENERATE INSTALLMENTS
        // ------------------------------------------------------------

        BigDecimal balance =
                principal;

        BigDecimal totalScheduledInterest =
                ZERO;

        BigDecimal totalScheduledManagementFee =
                ZERO;

        BigDecimal totalScheduledPrincipal =
                ZERO;

        BigDecimal totalScheduledRepayable =
                ZERO;

        for (
                int installmentNumber = 1;
                installmentNumber <= months;
                installmentNumber++
        ) {

            balance =
                    money(balance);

            // --------------------------------------------------------
            // CONTRACTUAL MONTHLY INTEREST
            // --------------------------------------------------------

            BigDecimal interest =
                    money(
                            balance.multiply(
                                    monthlyInterestRate
                            )
                    );

           

            BigDecimal managementFee =
                    money(
                            balance.multiply(
                                    monthlyManagementFeeRate
                            )
                    );

            BigDecimal principalComponent;
            BigDecimal baseInstallmentAmount;
            BigDecimal installmentAmount;

            // --------------------------------------------------------
            // FINAL INSTALLMENT
            // --------------------------------------------------------

            if (installmentNumber == months) {

                /*
                 * The final installment always clears the remaining
                 * principal exactly.
                 */
                principalComponent =
                        money(balance);

                baseInstallmentAmount =
                        money(
                                principalComponent.add(
                                        interest
                                )
                        );

                installmentAmount =
                        money(
                                baseInstallmentAmount.add(
                                        managementFee
                                )
                        );

                balance =
                        ZERO;

            } else {

                // ----------------------------------------------------
                // NORMAL INSTALLMENT
                // ----------------------------------------------------

                baseInstallmentAmount =
                        money(
                                baseMonthlyPayment
                        );

                /*
                 * Principal is the portion left after contractual
                 * interest. Management fee is a separate contractual
                 * charge and must NOT be deducted from principal.
                 */
                principalComponent =
                        money(
                                baseInstallmentAmount.subtract(
                                        interest
                                )
                        );

                if (
                        principalComponent.compareTo(
                                ZERO
                        ) < 0
                ) {
                    principalComponent =
                            ZERO;
                }

                if (
                        principalComponent.compareTo(
                                balance
                        ) > 0
                ) {
                    principalComponent =
                            money(balance);
                }

                // ----------------------------------------------------
                // UPDATE PRINCIPAL BALANCE
                // ----------------------------------------------------

                balance =
                        money(
                                balance.subtract(
                                        principalComponent
                                )
                        );

                // ----------------------------------------------------
                // REMOVE TINY ROUNDING RESIDUAL
                // ----------------------------------------------------

                if (
                        balance.compareTo(
                                ONE_CENT
                        ) < 0
                ) {
                    balance =
                            ZERO;
                }

                /*
                 * Total borrower installment includes:
                 *
                 * principal
                 * + interest
                 * + management fee
                 */
                installmentAmount =
                        money(
                                principalComponent
                                        .add(interest)
                                        .add(managementFee)
                        );
            }

            // --------------------------------------------------------
            // DUE DATE
            // --------------------------------------------------------

            LocalDate dueDate =
                    baseDate.plusMonths(
                            installmentNumber
                    );

            // --------------------------------------------------------
            // BUILD PAYMENT SCHEDULE
            // --------------------------------------------------------

            PaymentSchedule schedule =
                    PaymentSchedule.builder()
                            .loan(loan)
                            .installmentNumber(
                                    installmentNumber
                            )
                            .dueDate(
                                    dueDate
                            )
                            .installmentAmount(
                                    installmentAmount
                            )
                            .principalAmount(
                                    money(
                                            principalComponent
                                    )
                            )
                            .interestAmount(
                                    money(
                                            interest
                                    )
                            )
                            .managementFeeAmount(
                                    money(
                                            managementFee
                                    )
                            )
                            .penaltyAmount(
                                    ZERO
                            )
                            .amountPaid(
                                    ZERO
                            )
                            .remainingBalance(
                                    money(balance)
                            )
                            .status(
                                    ScheduleStatus.PENDING
                            )
                            .build();

            repository.save(schedule);

            // --------------------------------------------------------
            // AGGREGATES
            // --------------------------------------------------------

            totalScheduledPrincipal =
                    money(
                            totalScheduledPrincipal
                                    .add(principalComponent)
                    );

            totalScheduledInterest =
                    money(
                            totalScheduledInterest
                                    .add(interest)
                    );

            totalScheduledManagementFee =
                    money(
                            totalScheduledManagementFee
                                    .add(managementFee)
                    );

            totalScheduledRepayable =
                    money(
                            totalScheduledRepayable
                                    .add(installmentAmount)
                    );
        }

        // ------------------------------------------------------------
        // FINAL FINANCIAL INTEGRITY CHECK
        // ------------------------------------------------------------

        if (
                totalScheduledPrincipal.compareTo(
                        principal
                ) != 0
        ) {
            throw new IllegalStateException(
                    "Generated schedule principal does not equal loan principal. "
                            + "Loan="
                            + safeLoanReference(loan)
                            + ", principal="
                            + principal
                            + ", scheduledPrincipal="
                            + totalScheduledPrincipal
            );
        }

        // ------------------------------------------------------------
        // SYNCHRONIZE LOAN AGGREGATE
        // ------------------------------------------------------------

        loan.setAmount(
                principal
        );

        loan.setOutstandingBalance(
                principal
        );

        loan.setTotalInterest(
                totalScheduledInterest
        );

        loan.setManagementFee(
                totalScheduledManagementFee
        );

        loan.setTotalRepayable(
                totalScheduledRepayable
        );

        loan.setNextDueDate(
                baseDate.plusMonths(1)
        );

        loan.setNextPaymentDate(
                baseDate.plusMonths(1)
        );

        /*
         * First scheduled installment includes:
         *
         * principal + interest + management fee.
         */
        if (months > 0) {

            PaymentSchedule firstSchedule =
                    repository
                            .findFirstByLoanIdAndStatusOrderByInstallmentNumberAsc(
                                    loan.getId(),
                                    ScheduleStatus.PENDING
                            )
                            .orElse(null);

            if (firstSchedule != null) {

                loan.setNextInstallmentAmount(
                        money(
                                firstSchedule
                                        .getInstallmentAmount()
                        )
                );
            } else {

                loan.setNextInstallmentAmount(
                        money(
                                baseMonthlyPayment
                                        .add(
                                                principal
                                                        .multiply(
                                                                monthlyManagementFeeRate
                                                        )
                                        )
                        )
                );
            }
        }

        log.info(
                "Payment schedule generated successfully for loan {}. "
                        + "installments={}, principal={}, interest={}, "
                        + "managementFee={}, totalRepayable={}",
                safeLoanReference(loan),
                months,
                totalScheduledPrincipal,
                totalScheduledInterest,
                totalScheduledManagementFee,
                totalScheduledRepayable
        );
    }

        // ================================================================
        // CHECK PAYMENT ACTIVITY
        // ================================================================

        /**
         * Determines whether an existing schedule contains payment
         * activity that makes schedule regeneration unsafe.
         */
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
         * Converts the configured interest rate into a contractual
         * monthly rate.
         *
         * MONTHLY:
         *
         * 10% / 100
         *
         * = 0.10 monthly
         *
         * ANNUAL:
         *
         * 10% / 100 / 12
         *
         * = 0.008333...
         *
         * IMPORTANT:
         *
         * This is the contractual schedule rate.
         *
         * It is NOT the elapsed-calendar-day interest calculation
         * performed by PaymentService.
         */
        private BigDecimal calculateMonthlyRate(
                        BigDecimal rate,
                        String rateType) {

                if (rate == null) {
                        throw new IllegalArgumentException(
                                        "Interest rate cannot be null");
                }

                if ("MONTHLY".equalsIgnoreCase(rateType)) {

                        return rate.divide(
                                        ONE_HUNDRED,
                                        CALCULATION_SCALE,
                                        RoundingMode.HALF_UP);
                }

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

        // ================================================================
        // MONTHLY PAYMENT
        // ================================================================

        /**
         * Calculates the contractual monthly principal + interest
         * payment.
         *
         * Management fee is intentionally excluded here because it is
         * calculated independently and stored in managementFeeAmount.
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

                // ------------------------------------------------------------
                // ZERO INTEREST
                // ------------------------------------------------------------

                if (monthlyRate.compareTo(
                                ZERO) == 0) {

                        return money(
                                        principal.divide(
                                                        BigDecimal.valueOf(months),
                                                        CALCULATION_SCALE,
                                                        RoundingMode.HALF_UP));
                }

                // ------------------------------------------------------------
                // EMI EXPONENT
                //
                // Math.pow is used only for exponentiation.
                // The monetary calculations remain BigDecimal.
                // ------------------------------------------------------------

                double rateDouble = monthlyRate.doubleValue();

                double factorDouble = Math.pow(
                                1.0 + rateDouble,
                                -months);

                if (Double.isNaN(factorDouble)
                                || Double.isInfinite(factorDouble)) {
                        throw new IllegalArgumentException(
                                        "Unable to calculate monthly installment");
                }

                BigDecimal discountFactor = BigDecimal.valueOf(
                                factorDouble);

                BigDecimal denominator = ONE.subtract(
                                discountFactor);

                if (denominator.compareTo(
                                ZERO) == 0) {
                        throw new IllegalArgumentException(
                                        "Invalid interest calculation");
                }

                BigDecimal payment = principal
                                .multiply(
                                                monthlyRate)
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
                 * For a disbursed loan, the disbursement date is the
                 * authoritative contractual schedule start date.
                 */
                if (loan.getDisbursedAt() != null) {

                        return loan
                                        .getDisbursedAt()
                                        .toLocalDate();
                }

                /*
                 * Legacy/test records may not have disbursedAt.
                 */
                if (loan.getStartDate() != null) {

                        return loan.getStartDate();
                }

                /*
                 * Final fallback for legacy/test data.
                 */
                return LocalDate.now();
        }

        // ================================================================
        // VALIDATE RATE TYPE
        // ================================================================

        private void validateRateType(
                        String rateType) {

                if (!"ANNUAL".equalsIgnoreCase(rateType)
                                && !"MONTHLY".equalsIgnoreCase(rateType)) {
                        throw new IllegalArgumentException(
                                        "Interest rate type must be MONTHLY or ANNUAL");
                }
        }

        // ================================================================
        // MONEY
        // ================================================================

        /**
         * Converts a value into the application's authoritative
         * two-decimal monetary representation.
         */
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

        // ================================================================
        // NORMALIZE MONEY
        // ================================================================

        /**
         * Normalizes an authoritative BigDecimal monetary value.
         *
         * No Double conversion is performed.
         */
        private BigDecimal normalizeMoney(
                        BigDecimal value) {

                if (value == null) {
                        throw new IllegalArgumentException(
                                        "Money value cannot be null");
                }

                return value.setScale(
                                MONEY_SCALE,
                                RoundingMode.HALF_UP);
        }

        // ================================================================
        // SAFE LOAN REFERENCE
        // ================================================================

        private String safeLoanReference(
                        Loan loan) {

                if (loan == null) {
                        return "UNKNOWN";
                }

                if (loan.getReferenceNumber() != null
                                && !loan.getReferenceNumber().isBlank()) {
                        return loan.getReferenceNumber().trim();
                }

                if (loan.getId() != null) {
                        return "LOAN-" + loan.getId();
                }

                return "UNPERSISTED-LOAN";
        }
}