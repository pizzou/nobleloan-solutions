package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.publicportal.PaymentScheduleResponse;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.PaymentSchedule;
import com.patrick.fintech.loan_backend.model.PaymentSchedule.ScheduleStatus;
import com.patrick.fintech.loan_backend.repository.PaymentRepository;
import com.patrick.fintech.loan_backend.repository.PaymentScheduleRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.util.FinancialPolicy;
import com.patrick.fintech.loan_backend.security.HmacIndexer;

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

        private final LoanRepository loanRepository;

        private final PaymentRepository paymentRepository;

        private final HolidayService holidayService;

        private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

        private static final BigDecimal TWELVE = new BigDecimal("12");

        private static final BigDecimal ZERO = BigDecimal.ZERO;

        private static final BigDecimal ONE = BigDecimal.ONE;

        private static final BigDecimal ONE_CENT = new BigDecimal("0.01");

        /**
         * Internal calculation precision.
         *
         * Financial calculations are performed using BigDecimal.
         * Money is rounded to two decimal places only when stored
         * as an actual monetary amount.
         */
        private static final int CALCULATION_SCALE = 16;

        /**
         * Database/API monetary scale.
         */
        private static final int MONEY_SCALE = 2;

        // ================================================================
        // GET SCHEDULE
        // ================================================================

        /**
         * Public borrower schedule lookup.
         *
         * The internal Loan.id is deliberately not accepted here. The caller
         * must prove ownership using the public reference and the HMAC-indexed
         * phone number stored on the borrower record.
         */
        @Transactional(readOnly = true)
        public List<PaymentScheduleResponse> getPublicSchedule(
                        String reference,
                        String phone) {

                String normalizedReference = normalizeReference(reference);
                String normalizedPhone = normalizePhone(phone);
                String phoneHash = HmacIndexer.index(normalizedPhone);

                Loan loan = loanRepository
                                .findByReferenceNumberAndBorrower_PhoneHash(normalizedReference, phoneHash)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "We couldn't find an application with that reference number and phone number."));

                return repository
                                .findByLoanIdOrderByInstallmentNumberAsc(loan.getId())
                                .stream()
                                .map(this::toResponse)
                                .toList();
        }

        private String normalizeReference(String reference) {
                if (reference == null || reference.isBlank() || reference.trim().length() > 100) {
                        throw new IllegalArgumentException("Application reference number is required.");
                }
                return reference.trim().toUpperCase(java.util.Locale.ROOT);
        }

        private String normalizePhone(String phone) {
                if (phone == null || phone.isBlank() || phone.trim().length() > 50) {
                        throw new IllegalArgumentException("Phone number is required.");
                }
                return phone.trim();
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

        @Transactional
        public void generateSchedule(
                        Loan loan) {

                // ------------------------------------------------------------
                // BASIC VALIDATION
                // ------------------------------------------------------------

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

                BigDecimal amount = loan.getAmountDecimal();

                if (amount == null) {
                        throw new IllegalArgumentException(
                                        "Loan principal is required");
                }

                BigDecimal interestRate = loan.getInterestRateDecimal();

                if (interestRate == null) {
                        throw new IllegalArgumentException(
                                        "Loan interest rate is required");
                }

                if (loan.getDurationMonths() == null) {
                        throw new IllegalArgumentException(
                                        "Loan duration is required");
                }

                // ------------------------------------------------------------
                // DURATION
                // ------------------------------------------------------------

                int months = loan.getDurationMonths();

                if (!Loan.isValidLoanDuration(months)) {
                        throw new IllegalArgumentException(
                                        "Loan duration must be between "
                                                        + Loan.MIN_LOAN_DURATION_MONTHS
                                                        + " and "
                                                        + Loan.MAX_LOAN_DURATION_MONTHS
                                                        + " months");
                }

                // ------------------------------------------------------------
                // NORMALIZE PRINCIPAL
                // ------------------------------------------------------------

                BigDecimal principal = normalizeMoney(amount);

                if (principal.compareTo(ZERO) <= 0) {
                        throw new IllegalArgumentException(
                                        "Loan principal must be greater than zero");
                }

                // ------------------------------------------------------------
                // VALIDATE INTEREST RATE
                // ------------------------------------------------------------

                BigDecimal rate = interestRate.setScale(
                                CALCULATION_SCALE,
                                RoundingMode.HALF_UP);

                if (rate.compareTo(ZERO) < 0) {
                        throw new IllegalArgumentException(
                                        "Loan interest rate cannot be negative");
                }

                // ------------------------------------------------------------
                // INTEREST RATE TYPE
                // ------------------------------------------------------------

                String rateType = loan.getInterestRateType();

                if (rateType == null
                                || rateType.isBlank()) {

                        rateType = "MONTHLY";
                }

                rateType = rateType
                                .trim()
                                .toUpperCase();

                validateRateType(
                                rateType);

                // ------------------------------------------------------------
                // RESOLVE SCHEDULE START DATE
                // ------------------------------------------------------------

                LocalDate baseDate = resolveScheduleStartDate(
                                loan);

                // ------------------------------------------------------------
                // PRODUCTION SAFETY
                //
                // Do not destroy a schedule that already contains
                // payment activity.
                // ------------------------------------------------------------

                List<PaymentSchedule> existingSchedules = repository.findByLoanIdOrderByInstallmentNumberAsc(
                                loan.getId());

                if (!existingSchedules.isEmpty()) {

                        boolean hasPaymentActivity = existingSchedules.stream()
                                        .anyMatch(this::hasPaymentActivity)
                                        || hasOperationalPaymentActivity(loan.getId());

                        if (hasPaymentActivity) {

                                throw new IllegalStateException(
                                                "Cannot regenerate payment schedule for loan "
                                                                + loan.getReferenceNumber()
                                                                + " because payment activity already exists");
                        }

                        /*
                         * Existing schedule has no payment activity.
                         * It is safe to replace it.
                         */
                        repository.deleteByLoanId(
                                        loan.getId());
                }

                BigDecimal managementFeeRate = loan.getManagementFeeRateDecimal() != null
                                ? loan.getManagementFeeRateDecimal().setScale(CALCULATION_SCALE, RoundingMode.HALF_UP)
                                : Loan.DEFAULT_MONTHLY_MANAGEMENT_FEE_RATE.setScale(CALCULATION_SCALE, RoundingMode.HALF_UP);

                if (managementFeeRate.compareTo(ZERO) < 0) {
                        throw new IllegalArgumentException("Management fee rate cannot be negative");
                }

                // ------------------------------------------------------------
                // CONTRACTUAL DECLINING-BALANCE SCHEDULE
                // ------------------------------------------------------------
                // The product is monthly and both recurring charges are calculated
                // from the opening principal of each installment. Do not use EMI
                // amortization or a flat management fee here: this schedule is the
                // contractual source used by borrower, portfolio, accounting and
                // regulatory views.
                BigDecimal balance = principal;

                for (int installmentNumber = 1; installmentNumber <= months; installmentNumber++) {
                        balance = money(balance);

                        FinancialPolicy.ScheduleLine line = FinancialPolicy.contractualScheduleLine(
                                        balance,
                                        months - installmentNumber + 1,
                                        rate,
                                        managementFeeRate);

                        BigDecimal principalComponent = money(line.principal());
                        BigDecimal interest = money(line.interest());
                        BigDecimal managementFeeAmount = money(line.managementFee());
                        BigDecimal installmentAmount = money(line.installment());
                        balance = money(line.remainingBalance());

                        LocalDate dueDate = holidayService.adjustToBusinessDay(
                                        loan.getOrganization().getId(),
                                        baseDate.plusMonths(installmentNumber));

                        PaymentSchedule schedule = PaymentSchedule.builder()
                                        .loan(loan)
                                        .installmentNumber(installmentNumber)
                                        .dueDate(dueDate)
                                        .installmentAmount(installmentAmount)
                                        .principalAmount(principalComponent)
                                        .interestAmount(interest)
                                        .managementFeeAmount(managementFeeAmount)
                                        .penaltyAmount(ZERO)
                                        .amountPaid(ZERO)
                                        .remainingBalance(balance)
                                        .status(ScheduleStatus.PENDING)
                                        .build();

                        repository.save(schedule);
                }

                // ------------------------------------------------------------
                // SYNCHRONIZE CONTRACTUAL FEE TOTALS
                // ------------------------------------------------------------
                List<PaymentSchedule> generatedSchedules = repository.findByLoanIdOrderByInstallmentNumberAsc(loan.getId());

                BigDecimal totalScheduledManagementFee = money(
                                generatedSchedules.stream()
                                                .map(PaymentSchedule::getManagementFeeAmount)
                                                .filter(java.util.Objects::nonNull)
                                                .reduce(ZERO, BigDecimal::add));

                BigDecimal totalScheduledInterest = money(
                                generatedSchedules.stream()
                                                .map(PaymentSchedule::getInterestAmount)
                                                .filter(java.util.Objects::nonNull)
                                                .reduce(ZERO, BigDecimal::add));

                loan.setManagementFee(totalScheduledManagementFee);
                loan.setManagementFeePaid(ZERO);
                loan.setManagementFeeOutstanding(totalScheduledManagementFee);

                loan.setTotalInterest(totalScheduledInterest);
                loan.setInterestPaid(ZERO);
                loan.setInterestOutstanding(totalScheduledInterest);
                loan.setTotalRepayable(
                                money(
                                                principal
                                                                .add(totalScheduledInterest)
                                                                .add(totalScheduledManagementFee)));

               

                loan.setAmount(
                                principal);

                loan.setOutstandingBalance(
                                principal);

                LocalDate firstDueDate = holidayService.adjustToBusinessDay(
                                loan.getOrganization().getId(),
                                baseDate.plusMonths(1));

                loan.setNextDueDate(firstDueDate);
                loan.setNextPaymentDate(firstDueDate);

                List<PaymentSchedule> finalSchedules = repository.findByLoanIdOrderByInstallmentNumberAsc(loan.getId());
                if (!finalSchedules.isEmpty()) {
                        loan.setNextInstallmentAmount(
                                        money(finalSchedules.get(0).getInstallmentAmount()));
                }

                log.info(
                                "Payment schedule generated successfully for loan {} with {} installments",
                                loan.getReferenceNumber(),
                                months);
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

        /**
         * Payment rows are the operational payment schedule used by payment
         * allocation/accounting. A separate PaymentSchedule row may have no
         * activity even when a payment has already been recorded. Therefore
         * schedule regeneration must inspect both representations before
         * resetting contractual loan aggregates.
         */
        private boolean hasOperationalPaymentActivity(Long loanId) {
                if (loanId == null) {
                        return false;
                }

                List<com.patrick.fintech.loan_backend.model.Payment> payments =
                                paymentRepository.findByLoanId(loanId);

                if (payments == null || payments.isEmpty()) {
                        return false;
                }

                return payments.stream().anyMatch(payment -> payment != null
                                && (money(payment.getAmountPaidDecimal()).compareTo(ZERO) > 0
                                                || money(payment.getPrincipalComponentDecimal()).compareTo(ZERO) > 0
                                                || money(payment.getInterestComponentDecimal()).compareTo(ZERO) > 0
                                                || money(payment.getManagementFeeComponentDecimal()).compareTo(ZERO) > 0
                                                || money(payment.getExtensionFeeComponentDecimal()).compareTo(ZERO) > 0
                                                || money(payment.getPenaltyPaidDecimal()).compareTo(ZERO) > 0));
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
                 * Older/test records may not have disbursedAt.
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

                if (!"MONTHLY".equalsIgnoreCase(rateType)) {

                        throw new IllegalArgumentException(
                                        "Interest rate type must be MONTHLY");
                }
        }

        // ================================================================
        // MONEY
        // ================================================================

        /**
         * Converts a BigDecimal value into a two-decimal monetary value.
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

}
