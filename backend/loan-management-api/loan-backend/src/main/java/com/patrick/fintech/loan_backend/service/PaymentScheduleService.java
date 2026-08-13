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
    private static final BigDecimal MIN_LOAN_AMOUNT =
            new BigDecimal("500000.00");

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
    private static final BigDecimal MONTHLY_INTEREST_RATE =
            new BigDecimal("5.00");

    /**
     * Monthly management fee.
     */
    private static final BigDecimal MONTHLY_MANAGEMENT_FEE_RATE =
            new BigDecimal("5.00");

    /**
     * Combined monthly charge.
     *
     * 5% interest + 5% management = 10%.
     */
    private static final BigDecimal TOTAL_MONTHLY_CHARGE_RATE =
            MONTHLY_INTEREST_RATE.add(
                    MONTHLY_MANAGEMENT_FEE_RATE
            );

    /**
     * One-time processing fee.
     */
    private static final BigDecimal PROCESSING_FEE_RATE =
            new BigDecimal("2.00");

    // ================================================================
    // GENERAL CONSTANTS
    // ================================================================

    private static final BigDecimal ONE_HUNDRED =
            new BigDecimal("100");

    private static final BigDecimal TWELVE =
            new BigDecimal("12");

    private static final BigDecimal ZERO =
            BigDecimal.ZERO;

    private static final BigDecimal ONE =
            BigDecimal.ONE;

    private static final BigDecimal ONE_CENT =
            new BigDecimal("0.01");

    private static final int CALCULATION_SCALE = 16;

    private static final int MONEY_SCALE = 2;

    private static final MathContext CALCULATION_CONTEXT =
            MathContext.DECIMAL128;

    // ================================================================
    // GET SCHEDULE
    // ================================================================

    @Transactional(readOnly = true)
    public List<PaymentScheduleResponse> getSchedule(
            Long loanId
    ) {

        if (loanId == null) {

            throw new IllegalArgumentException(
                    "Loan ID is required"
            );
        }

        return repository
                .findByLoanIdOrderByInstallmentNumberAsc(
                        loanId
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ================================================================
    // CONVERT TO RESPONSE
    // ================================================================

    private PaymentScheduleResponse toResponse(
            PaymentSchedule schedule
    ) {

        if (schedule == null) {
            return null;
        }

        return PaymentScheduleResponse.builder()
                .installmentNumber(
                        schedule.getInstallmentNumber()
                )
                .dueDate(
                        schedule.getDueDate()
                )
                .installmentAmount(
                        money(
                                schedule.getInstallmentAmount()
                        )
                )
                .principal(
                        money(
                                schedule.getPrincipalAmount()
                        )
                )
                .interest(
                        money(
                                schedule.getInterestAmount()
                        )
                )
                .penalty(
                        money(
                                schedule.getPenaltyAmount()
                        )
                )
                .paid(
                        money(
                                schedule.getAmountPaid()
                        )
                )
                .balance(
                        money(
                                schedule.getRemainingBalance()
                        )
                )
                .status(
                        schedule.getStatus() != null
                                ? schedule.getStatus().name()
                                : ScheduleStatus.PENDING.name()
                )
                .build();
    }

    // ================================================================
    // GENERATE SCHEDULE
    // ================================================================

    @Transactional
    public void generateSchedule(
            Loan loan
    ) {

        // ============================================================
        // BASIC VALIDATION
        // ============================================================

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

        // ============================================================
        // PRINCIPAL
        // ============================================================

        BigDecimal amount =
                loan.getAmountDecimal();

        if (amount == null) {

            throw new IllegalArgumentException(
                    "Loan principal is required"
            );
        }

        BigDecimal principal =
                normalizeMoney(amount);

        if (principal.compareTo(ZERO) <= 0) {

            throw new IllegalArgumentException(
                    "Loan principal must be greater than zero"
            );
        }

        if (
                principal.compareTo(
                        MIN_LOAN_AMOUNT
                ) < 0
        ) {

            throw new IllegalArgumentException(
                    "Loan principal cannot be below "
                            + MIN_LOAN_AMOUNT
                            + "."
            );
        }

        // ============================================================
        // DURATION
        // ============================================================

        Integer requestedMonths =
                loan.getDurationMonths();

        if (requestedMonths == null) {

            throw new IllegalArgumentException(
                    "Loan duration is required"
            );
        }

        validateLoanDuration(
                requestedMonths
        );

        int months =
                requestedMonths;

        // ============================================================
        // FORCE PLATFORM RATES
        // ============================================================

        /*
         * All loan types use exactly the same rates.
         *
         * Interest             = 5% monthly
         * Management fee       = 5% monthly
         * Total recurring      = 10% monthly
         * Processing fee       = 2% one time
         */

        BigDecimal interestRate =
                MONTHLY_INTEREST_RATE;

        BigDecimal managementFeeRate =
                MONTHLY_MANAGEMENT_FEE_RATE;

        BigDecimal totalMonthlyRate =
                TOTAL_MONTHLY_CHARGE_RATE;

        String rateType =
                "MONTHLY";

        // ============================================================
        // CALCULATE PROCESSING FEE
        // ============================================================

        /*
         * The processing fee is NOT part of monthly repayments.
         *
         * It is a one-time fee calculated from gross principal:
         *
         * principal × 2%.
         *
         * Importantly:
         *
         * interest and management calculations continue using
         * the gross principal.
         */

        BigDecimal processingFee =
                money(
                        principal
                                .multiply(
                                        PROCESSING_FEE_RATE
                                )
                                .divide(
                                        ONE_HUNDRED,
                                        CALCULATION_SCALE,
                                        RoundingMode.HALF_UP
                                )
                );

        // ============================================================
        // SYNCHRONIZE LOAN FINANCIAL RULES
        // ============================================================

        loan.setAmount(
                principal
        );

        loan.setInterestRate(
                interestRate
        );

        loan.setManagementFeeRate(
                managementFeeRate
        );

        loan.setProcessingFeeRate(
                PROCESSING_FEE_RATE
        );

        loan.setInterestRateType(
                rateType
        );

        loan.setProcessingFee(
                processingFee
        );

        // ============================================================
        // EXISTING SCHEDULE SAFETY
        // ============================================================

        List<PaymentSchedule> existingSchedules =
                repository.findByLoanIdOrderByInstallmentNumberAsc(
                        loan.getId()
                );

        if (
                existingSchedules != null
                        && !existingSchedules.isEmpty()
        ) {

            boolean hasPaymentActivity =
                    existingSchedules.stream()
                            .anyMatch(
                                    this::hasPaymentActivity
                            );

            if (hasPaymentActivity) {

                throw new IllegalStateException(
                        "Cannot regenerate payment schedule for loan "
                                + loan.getReferenceNumber()
                                + " because payment activity already exists."
                );
            }

            /*
             * Existing schedule has no payment activity.
             *
             * Safe to regenerate.
             */
            repository.deleteByLoanId(
                    loan.getId()
            );
        }

        // ============================================================
        // START DATE
        // ============================================================

        LocalDate baseDate =
                resolveScheduleStartDate(
                        loan
                );

        Long organizationId =
                loan.getOrganization().getId();

        // ============================================================
        // MONTHLY RATE COMPONENTS
        // ============================================================

        BigDecimal monthlyInterestRate =
                calculateMonthlyRate(
                        interestRate,
                        "MONTHLY"
                );

        BigDecimal monthlyManagementRate =
                calculateMonthlyRate(
                        managementFeeRate,
                        "MONTHLY"
                );

        BigDecimal monthlyCombinedRate =
                monthlyInterestRate.add(
                        monthlyManagementRate
                );

        // ============================================================
        // MONTHLY INSTALLMENT
        // ============================================================

        /*
         * The contractual monthly installment is calculated from
         * the complete recurring monthly charge:
         *
         * 5% interest + 5% management = 10%.
         *
         * Processing fee is deliberately excluded.
         */
        BigDecimal monthlyPayment =
                calculateMonthlyPayment(
                        principal,
                        monthlyCombinedRate,
                        months
                );

        log.info(
                "Generating payment schedule. " +
                        "loanId={}, reference={}, principal={}, " +
                        "months={}, interestRate={}%, " +
                        "managementFeeRate={}%, " +
                        "totalMonthlyRate={}%, " +
                        "processingFeeRate={}%, " +
                        "processingFee={}, monthlyPayment={}",
                loan.getId(),
                loan.getReferenceNumber(),
                principal,
                months,
                interestRate,
                managementFeeRate,
                totalMonthlyRate,
                PROCESSING_FEE_RATE,
                processingFee,
                monthlyPayment
        );

        // ============================================================
        // GENERATE INSTALLMENTS
        // ============================================================

        BigDecimal balance =
                principal;

        BigDecimal accumulatedInterest =
                ZERO;

        BigDecimal accumulatedManagementFee =
                ZERO;

        BigDecimal accumulatedMonthlyCharges =
                ZERO;

        for (
                int installmentNumber = 1;
                installmentNumber <= months;
                installmentNumber++
        ) {

            balance =
                    money(balance);

            // ========================================================
            // MONTHLY 5% INTEREST
            // ========================================================

            BigDecimal interest =
                    money(
                            balance.multiply(
                                    monthlyInterestRate
                            )
                    );

            // ========================================================
            // MONTHLY 5% MANAGEMENT FEE
            // ========================================================

            BigDecimal managementFee =
                    money(
                            balance.multiply(
                                    monthlyManagementRate
                            )
                    );

            // ========================================================
            // TOTAL MONTHLY CHARGE
            // ========================================================

            BigDecimal monthlyCharges =
                    money(
                            interest.add(
                                    managementFee
                            )
                    );

            accumulatedInterest =
                    money(
                            accumulatedInterest.add(
                                    interest
                            )
                    );

            accumulatedManagementFee =
                    money(
                            accumulatedManagementFee.add(
                                    managementFee
                            )
                    );

            accumulatedMonthlyCharges =
                    money(
                            accumulatedMonthlyCharges.add(
                                    monthlyCharges
                            )
                    );

            BigDecimal principalComponent;

            BigDecimal installmentAmount;

            // ========================================================
            // FINAL INSTALLMENT
            // ========================================================

            if (
                    installmentNumber == months
            ) {

                /*
                 * Final payment absorbs all remaining principal
                 * rounding residue.
                 */
                principalComponent =
                        money(balance);

                installmentAmount =
                        money(
                                principalComponent
                                        .add(
                                                monthlyCharges
                                        )
                        );

                balance =
                        ZERO;

            } else {

                // ====================================================
                // NORMAL INSTALLMENT
                // ====================================================

                installmentAmount =
                        money(
                                monthlyPayment
                        );

                principalComponent =
                        money(
                                installmentAmount
                                        .subtract(
                                                monthlyCharges
                                        )
                        );

                // ====================================================
                // SAFETY: NO NEGATIVE PRINCIPAL
                // ====================================================

                if (
                        principalComponent.compareTo(
                                ZERO
                        ) < 0
                ) {

                    principalComponent =
                            ZERO;
                }

                // ====================================================
                // SAFETY: NO PRINCIPAL ABOVE BALANCE
                // ====================================================

                if (
                        principalComponent.compareTo(
                                balance
                        ) > 0
                ) {

                    principalComponent =
                            balance;
                }

                // ====================================================
                // UPDATE BALANCE
                // ====================================================

                balance =
                        money(
                                balance.subtract(
                                        principalComponent
                                )
                        );

                // ====================================================
                // REMOVE ROUNDING RESIDUE
                // ====================================================

                if (
                        balance.compareTo(
                                ONE_CENT
                        ) < 0
                ) {

                    balance =
                            ZERO;
                }
            }

            // ========================================================
            // DUE DATE
            // ========================================================

            LocalDate rawDueDate =
                    baseDate.plusMonths(
                            installmentNumber
                    );

            LocalDate dueDate =
                    holidayService.adjustToBusinessDay(
                            organizationId,
                            rawDueDate
                    );

            // ========================================================
            // BUILD SCHEDULE
            // ========================================================

            /*
             * PaymentSchedule currently exposes only
             * interestAmount for the charge component.
             *
             * We therefore store the 5% contractual loan interest
             * in interestAmount.
             *
             * The complete installment amount already includes:
             *
             * principal
             * + 5% interest
             * + 5% management fee.
             *
             * The separate management fee is also synchronized
             * to Loan.managementFee and is handled separately
             * by PaymentService/Payment.
             */

            PaymentSchedule schedule =
                    PaymentSchedule.builder()
                            .loan(
                                    loan
                            )
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
                            .penaltyAmount(
                                    ZERO
                            )
                            .amountPaid(
                                    ZERO
                            )
                            .remainingBalance(
                                    money(
                                            balance
                                    )
                            )
                            .status(
                                    ScheduleStatus.PENDING
                            )
                            .build();

            repository.save(
                    schedule
            );
        }

        // ============================================================
        // TOTAL CONTRACTUAL VALUES
        // ============================================================

        /*
         * The final generated schedule is authoritative for:
         *
         * total interest
         * total management fee
         * total recurring charges
         * total repayable.
         */

        BigDecimal totalInterest =
                money(
                        accumulatedInterest
                );

        BigDecimal totalManagementFee =
                money(
                        accumulatedManagementFee
                );

        BigDecimal totalRecurringCharges =
                money(
                        totalInterest
                                .add(
                                        totalManagementFee
                                )
                );

        BigDecimal totalRepayable =
                money(
                        principal
                                .add(
                                        totalRecurringCharges
                                )
                );

        // ============================================================
        // SYNCHRONIZE LOAN AGGREGATE
        // ============================================================

        loan.setAmount(
                principal
        );

        /*
         * Processing fee is deducted from cash actually disbursed,
         * but it must NEVER reduce the repayment principal.
         */
        loan.setOutstandingBalance(
                principal
        );

        loan.setInterestRate(
                interestRate
        );

        loan.setManagementFeeRate(
                managementFeeRate
        );

        loan.setProcessingFeeRate(
                PROCESSING_FEE_RATE
        );

        loan.setInterestRateType(
                "MONTHLY"
        );

        loan.setProcessingFee(
                processingFee
        );

        loan.setTotalInterest(
                totalInterest
        );

        loan.setManagementFee(
                totalManagementFee
        );

        loan.setInterestPaid(
                safeMoney(
                        loan.getInterestPaidDecimal()
                )
        );

        loan.setManagementFeePaid(
                safeMoney(
                        loan.getManagementFeePaidDecimal()
                )
        );

        loan.setTotalRepayable(
                totalRepayable
        );

        loan.setNextDueDate(
                holidayService.adjustToBusinessDay(
                        organizationId,
                        baseDate.plusMonths(1)
                )
        );

        loan.setNextPaymentDate(
                holidayService.adjustToBusinessDay(
                        organizationId,
                        baseDate.plusMonths(1)
                )
        );

        loan.setNextInstallmentAmount(
                monthlyPayment
        );

        /*
         * Do not mark processingFeePaid here.
         *
         * The fee is only considered paid/collected when the
         * disbursement/accounting flow actually records it.
         */

        log.info(
                "Payment schedule generated successfully. " +
                        "loanId={}, installments={}, principal={}, " +
                        "totalInterest={}, totalManagementFee={}, " +
                        "totalRecurringCharges={}, processingFee={}, " +
                        "totalRepayable={}, monthlyPayment={}",
                loan.getId(),
                months,
                principal,
                totalInterest,
                totalManagementFee,
                totalRecurringCharges,
                processingFee,
                totalRepayable,
                monthlyPayment
        );
    }

    // ================================================================
    // CHECK PAYMENT ACTIVITY
    // ================================================================

    private boolean hasPaymentActivity(
            PaymentSchedule schedule
    ) {

        if (schedule == null) {
            return false;
        }

        BigDecimal amountPaid =
                schedule.getAmountPaid();

        if (
                amountPaid != null
                        && amountPaid.compareTo(
                        ZERO
                ) > 0
        ) {

            return true;
        }

        ScheduleStatus status =
                schedule.getStatus();

        return status == ScheduleStatus.PAID
                || status == ScheduleStatus.PARTIAL;
    }

    // ================================================================
    // NEXT INSTALLMENT
    // ================================================================

    @Transactional(readOnly = true)
    public PaymentSchedule getNextInstallment(
            Long loanId
    ) {

        if (loanId == null) {

            throw new IllegalArgumentException(
                    "Loan ID is required"
            );
        }

        return repository
                .findFirstByLoanIdAndStatusOrderByInstallmentNumberAsc(
                        loanId,
                        ScheduleStatus.PENDING
                )
                .orElseGet(
                        () ->
                                repository
                                        .findFirstByLoanIdAndStatusOrderByInstallmentNumberAsc(
                                                loanId,
                                                ScheduleStatus.PARTIAL
                                        )
                                        .orElse(null)
                );
    }

    // ================================================================
    // MONTHLY RATE
    // ================================================================

    private BigDecimal calculateMonthlyRate(
            BigDecimal rate,
            String rateType
    ) {

        if (rate == null) {

            throw new IllegalArgumentException(
                    "Interest rate cannot be null"
            );
        }

        if (rate.compareTo(ZERO) < 0) {

            throw new IllegalArgumentException(
                    "Interest rate cannot be negative"
            );
        }

        if (
                rateType == null
                        || rateType.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Interest rate type is required"
            );
        }

        if (
                "MONTHLY".equalsIgnoreCase(
                        rateType
                )
        ) {

            return rate.divide(
                    ONE_HUNDRED,
                    CALCULATION_SCALE,
                    RoundingMode.HALF_UP
            );
        }

        if (
                "ANNUAL".equalsIgnoreCase(
                        rateType
                )
        ) {

            return rate
                    .divide(
                            ONE_HUNDRED,
                            CALCULATION_SCALE,
                            RoundingMode.HALF_UP
                    )
                    .divide(
                            TWELVE,
                            CALCULATION_SCALE,
                            RoundingMode.HALF_UP
                    );
        }

        throw new IllegalArgumentException(
                "Interest rate type must be MONTHLY or ANNUAL"
        );
    }

    // ================================================================
    // MONTHLY PAYMENT
    // ================================================================

    private BigDecimal calculateMonthlyPayment(
            BigDecimal principal,
            BigDecimal monthlyRate,
            int months
    ) {

        if (principal == null) {

            throw new IllegalArgumentException(
                    "Principal cannot be null"
            );
        }

        if (monthlyRate == null) {

            throw new IllegalArgumentException(
                    "Monthly rate cannot be null"
            );
        }

        if (months <= 0) {

            throw new IllegalArgumentException(
                    "Number of months must be greater than zero"
            );
        }

        if (
                principal.compareTo(
                        ZERO
                ) <= 0
        ) {

            throw new IllegalArgumentException(
                    "Principal must be greater than zero"
            );
        }

        // ============================================================
        // ZERO RATE
        // ============================================================

        if (
                monthlyRate.compareTo(
                        ZERO
                ) == 0
        ) {

            return money(
                    principal.divide(
                            BigDecimal.valueOf(months),
                            CALCULATION_SCALE,
                            RoundingMode.HALF_UP
                    )
            );
        }

        // ============================================================
        // EMI USING BIGDECIMAL
        // ============================================================

        BigDecimal onePlusRate =
                ONE.add(
                        monthlyRate
                );

        BigDecimal positiveFactor =
                onePlusRate.pow(
                        months,
                        CALCULATION_CONTEXT
                );

        BigDecimal inverseFactor =
                ONE.divide(
                        positiveFactor,
                        CALCULATION_SCALE,
                        RoundingMode.HALF_UP
                );

        BigDecimal denominator =
                ONE.subtract(
                        inverseFactor
                );

        if (
                denominator.compareTo(
                        ZERO
                ) <= 0
        ) {

            throw new IllegalArgumentException(
                    "Invalid monthly interest calculation"
            );
        }

        BigDecimal payment =
                principal
                        .multiply(
                                monthlyRate
                        )
                        .divide(
                                denominator,
                                CALCULATION_SCALE,
                                RoundingMode.HALF_UP
                        );

        return money(
                payment
        );
    }

    // ================================================================
    // RESOLVE SCHEDULE START DATE
    // ================================================================

    private LocalDate resolveScheduleStartDate(
            Loan loan
    ) {

        /*
         * Exact disbursement timestamp is now LocalDateTime.
         *
         * The contractual monthly schedule uses its calendar date.
         */
        if (
                loan.getDisbursedAt() != null
        ) {

            return loan
                    .getDisbursedAt()
                    .toLocalDate();
        }

        /*
         * Keep compatibility with legacy/test records.
         */
        if (
                loan.getStartDate() != null
        ) {

            return loan.getStartDate();
        }

        return LocalDate.now();
    }

    // ================================================================
    // DURATION VALIDATION
    // ================================================================

    private void validateLoanDuration(
            Integer months
    ) {

        if (months == null) {

            throw new IllegalArgumentException(
                    "Loan duration is required"
            );
        }

        if (
                months < MIN_LOAN_DURATION_MONTHS
        ) {

            throw new IllegalArgumentException(
                    "Loan duration must be at least "
                            + MIN_LOAN_DURATION_MONTHS
                            + " month"
            );
        }

        if (
                months > MAX_LOAN_DURATION_MONTHS
        ) {

            throw new IllegalArgumentException(
                    "Loan duration cannot exceed "
                            + MAX_LOAN_DURATION_MONTHS
                            + " months"
            );
        }
    }

    // ================================================================
    // MONEY HELPERS
    // ================================================================

    private BigDecimal money(
            BigDecimal value
    ) {

        if (value == null) {

            return ZERO.setScale(
                    MONEY_SCALE,
                    RoundingMode.HALF_UP
            );
        }

        return value.setScale(
                MONEY_SCALE,
                RoundingMode.HALF_UP
        );
    }

    private BigDecimal normalizeMoney(
            BigDecimal value
    ) {

        if (value == null) {

            throw new IllegalArgumentException(
                    "Money value cannot be null"
            );
        }

        return money(
                value
        );
    }

    private BigDecimal safeMoney(
            BigDecimal value
    ) {

        return money(
                value
        );
    }
}