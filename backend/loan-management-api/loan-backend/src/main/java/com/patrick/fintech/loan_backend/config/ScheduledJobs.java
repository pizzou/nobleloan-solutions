package com.patrick.fintech.loan_backend.config;

import com.patrick.fintech.loan_backend.model.IdempotencyKey;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.repository.IdempotencyKeyRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import com.patrick.fintech.loan_backend.repository.PaymentRepository;
import com.patrick.fintech.loan_backend.service.AccountingService;
import com.patrick.fintech.loan_backend.service.CollectionsService;
import com.patrick.fintech.loan_backend.service.CurrencyService;
import com.patrick.fintech.loan_backend.service.MailService;
import com.patrick.fintech.loan_backend.service.SchedulerLockService;
import com.patrick.fintech.loan_backend.service.SmsService;

import com.patrick.fintech.loan_backend.util.FinancialPolicy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduledJobs {

        private final LoanRepository loanRepo;
        private final PaymentRepository paymentRepo;
        private final SmsService smsService;
        private final MailService mailService;
        private final CurrencyService currencyService;
        private final IdempotencyKeyRepository idempotencyRepo;
        private final CollectionsService collectionsService;
        private final AccountingService accountingService;
        private final OrganizationRepository organizationRepo;
        private final SchedulerLockService lockService;

        private static final int MONEY_SCALE = 2;
        private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;

        private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(MONEY_SCALE, MONEY_ROUNDING);

        private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

        private static final BigDecimal THREE_HUNDRED_SIXTY_FIVE = new BigDecimal("365");

        /**
         * End-of-day accounting interest accrual.
         *
         * IMPORTANT:
         * This is an accounting accrual only.
         *
         * PaymentService remains authoritative for actual borrower
         * interest calculation and payment allocation.
         *
         * The calculation uses BigDecimal throughout so monetary values
         * are not converted through double.
         */
        @Scheduled(cron = "${app.scheduler.eod-cron:0 30 1 * * *}")
        @Transactional
        public void runEndOfDayAccruals() {

                if (!lockService.tryAcquire(
                                "eod-accrual",
                                Duration.ofHours(2))) {

                        log.info(
                                        "[Scheduler] EOD accrual already running on another instance - skipping");

                        return;
                }

                try {

                        LocalDate accrualDate = LocalDate.now();
                        String key = "EOD_ACCRUAL_" + accrualDate;

                        log.info(
                                        "[Scheduler] Starting end-of-day interest accrual for {}...",
                                        accrualDate);

                        List<Organization> organizations = organizationRepo.findAll();

                        if (organizations == null) {
                                organizations = List.of();
                        }

                        List<Loan> activeLoans = loanRepo.findByStatusIn(
                                        List.of(
                                                        LoanStatus.ACTIVE,
                                                        LoanStatus.OVERDUE));

                        if (activeLoans == null) {
                                activeLoans = List.of();
                        }

                        for (Organization organization : organizations) {

                                if (organization == null
                                                || organization.getId() == null) {
                                        continue;
                                }

                                if (idempotencyRepo
                                                .findByKeyAndOrganization(
                                                                key,
                                                                organization)
                                                .isPresent()) {

                                        log.debug(
                                                        "[Scheduler] EOD accrual already completed for organization {} on {}",
                                                        organization.getId(),
                                                        accrualDate);

                                        continue;
                                }

                                int posted = 0;

                                for (Loan loan : activeLoans) {

                                        if (!belongsToOrganization(
                                                        loan,
                                                        organization)) {

                                                continue;
                                        }

                                        try {

                                                BigDecimal outstanding = money(loan.getOutstandingBalanceDecimal());

                                                if (outstanding.compareTo(ZERO) <= 0) {
                                                        continue;
                                                }

                                                // Contractual interest and management fees are accrued
                                                // from the approved repayment schedule, not from a daily
                                                // 5%/calendar-day reconstruction. This guarantees that
                                                // accounting and the borrower-facing schedule reconcile.
                                                List<Payment> dueInstallments = paymentRepo.findByLoanId(loan.getId())
                                                                .stream()
                                                                .filter(p -> p != null)
                                                                .filter(p -> p.getDueDate() != null)
                                                                .filter(p -> !p.getDueDate().isAfter(accrualDate))
                                                                .toList();

                                                for (Payment installment : dueInstallments) {
                                                        try {
                                                                if (accountingService.postScheduledInterestAccrual(
                                                                                installment) != null) {
                                                                        posted++;
                                                                }
                                                                if (accountingService.postScheduledManagementFeeAccrual(
                                                                                installment) != null) {
                                                                        posted++;
                                                                }
                                                        } catch (Exception scheduleAccrualError) {
                                                                log.warn(
                                                                                "[Scheduler] Contractual installment accrual failed for loan {}, payment {}: {}",
                                                                                loan.getId(),
                                                                                installment.getId(),
                                                                                scheduleAccrualError.getMessage(),
                                                                                scheduleAccrualError);
                                                        }
                                                }

                                                if (loan.getStatus() == LoanStatus.OVERDUE) {
                                                        BigDecimal dailyPenalty = outstanding
                                                                        .multiply(FinancialPolicy.dailyRateFraction(
                                                                                        FinancialPolicy.MONTHLY_PENALTY_RATE,
                                                                                        accrualDate))
                                                                        .setScale(MONEY_SCALE, MONEY_ROUNDING);

                                                        if (dailyPenalty.compareTo(ZERO) > 0) {
                                                                accountingService.postPenaltyAccrual(loan,
                                                                                dailyPenalty);
                                                                posted++;
                                                        }
                                                }

                                        } catch (Exception e) {

                                                log.warn(
                                                                "[Scheduler] EOD accrual failed for loan {}: {}",
                                                                loan != null ? loan.getId() : null,
                                                                e.getMessage(),
                                                                e);
                                        }
                                }

                                idempotencyRepo.save(
                                                IdempotencyKey.builder()
                                                                .key(key)
                                                                .organization(organization)
                                                                .endpoint("EOD_ACCRUAL")
                                                                .status(
                                                                                IdempotencyKey.Status.COMPLETED)
                                                                .build());

                                log.info(
                                                "[Scheduler] EOD accrual for organization {} complete - {} loan(s) posted",
                                                organization.getId(),
                                                posted);
                        }

                } finally {

                        lockService.release("eod-accrual");
                }
        }

        /**
         * Daily overdue check.
         */
        @Scheduled(cron = "${app.scheduler.overdue-check-cron:0 0 7 * * *}")
        @Transactional
        public void checkOverdueLoans() {

                if (!lockService.tryAcquire(
                                "overdue-check",
                                Duration.ofHours(1))) {

                        log.info(
                                        "[Scheduler] Overdue check already running on another instance - skipping");

                        return;
                }

                try {

                        LocalDate today = LocalDate.now();

                        log.info(
                                        "[Scheduler] Starting daily overdue check...");

                        int flagged = 0;

                        List<Payment> overdue = paymentRepo.findByPaidFalseAndDueDateBefore(today);

                        if (overdue == null) {
                                overdue = List.of();
                        }

                        for (Payment payment : overdue) {

                                if (payment == null) {
                                        continue;
                                }

                                Loan loan = payment.getLoan();

                                if (loan == null) {
                                        continue;
                                }

                                if (payment.getDueDate() == null) {
                                        continue;
                                }

                                if (loan.getStatus() != LoanStatus.ACTIVE) {
                                        continue;
                                }

                                int daysOverdue = calculateDaysOverdue(
                                                payment.getDueDate(),
                                                today);

                                loan.setStatus(LoanStatus.OVERDUE);

                                Integer currentDays = loan.getDaysOverdue();

                                int previousDays = currentDays == null
                                                ? 0
                                                : Math.max(
                                                                0,
                                                                currentDays);

                                loan.setDaysOverdue(
                                                Math.max(
                                                                previousDays,
                                                                daysOverdue));

                                loanRepo.save(loan);

                                flagged++;

                                try {

                                        smsService.sendLoanOverdue(
                                                        loan,
                                                        loan.getDaysOverdue());

                                } catch (Exception e) {

                                        log.warn(
                                                        "[Scheduler] Overdue SMS failed for loan {}: {}",
                                                        loan.getId(),
                                                        e.getMessage());
                                }

                                try {

                                        mailService.sendOverdueReminder(
                                                        loan,
                                                        loan.getDaysOverdue());

                                } catch (Exception e) {

                                        log.warn(
                                                        "[Scheduler] Overdue email failed for loan {}: {}",
                                                        loan.getId(),
                                                        e.getMessage());
                                }
                        }

                        log.info(
                                        "[Scheduler] Overdue check done: {} loan(s) flagged",
                                        flagged);

                        try {

                                int cases = collectionsService.syncCasesFromOverdueLoans();

                                log.info(
                                                "[Scheduler] Collections queue synced: {} case(s) touched",
                                                cases);

                        } catch (Exception e) {

                                log.warn(
                                                "[Scheduler] Collections sync failed: {}",
                                                e.getMessage(),
                                                e);
                        }

                } finally {

                        lockService.release("overdue-check");
                }
        }

        /**
         * Daily payment reminders for installments due in 3 days.
         */
        @Scheduled(cron = "${app.scheduler.payment-reminder-cron:0 0 8 * * *}")
        @Transactional
        public void sendPaymentReminders() {

                if (!lockService.tryAcquire(
                                "payment-reminders",
                                Duration.ofHours(1))) {

                        log.info(
                                        "[Scheduler] Payment reminders already running on another instance - skipping");

                        return;
                }

                try {

                        LocalDate today = LocalDate.now();
                        LocalDate in3Days = today.plusDays(3);

                        log.info(
                                        "[Scheduler] Sending payment reminders for {}...",
                                        in3Days);

                        int sent = 0;

                        List<Payment> pendingPayments = paymentRepo.findByPaidFalseAndDueDateBefore(
                                        in3Days.plusDays(1));

                        if (pendingPayments == null) {
                                pendingPayments = List.of();
                        }

                        for (Payment payment : pendingPayments) {

                                if (payment == null) {
                                        continue;
                                }

                                if (Boolean.TRUE.equals(payment.getPaid())) {
                                        continue;
                                }

                                LocalDate dueDate = payment.getDueDate();

                                if (dueDate == null
                                                || !dueDate.equals(in3Days)) {
                                        continue;
                                }

                                Loan loan = payment.getLoan();

                                if (loan == null
                                                || loan.getStatus() != LoanStatus.ACTIVE) {
                                        continue;
                                }

                                BigDecimal amount = resolveReminderAmount(
                                                payment,
                                                loan);

                                if (amount.compareTo(ZERO) <= 0) {
                                        continue;
                                }

                                try {

                                        /*
                                         * SmsService currently accepts a double amount.
                                         *
                                         * Conversion happens only at this external service
                                         * boundary. Internal financial calculations remain
                                         * BigDecimal.
                                         */
                                        smsService.sendPaymentDue(
                                                        loan,
                                                        amount.doubleValue(),
                                                        in3Days.toString());

                                        mailService.sendPaymentDueReminder(loan);

                                        sent++;

                                } catch (Exception e) {

                                        log.warn(
                                                        "[Scheduler] Payment reminder failed for payment {} / loan {}: {}",
                                                        payment.getId(),
                                                        loan.getId(),
                                                        e.getMessage());
                                }
                        }

                        log.info(
                                        "[Scheduler] Payment reminders sent: {}",
                                        sent);

                } finally {

                        lockService.release("payment-reminders");
                }
        }

        /**
         * Daily FX refresh.
         */
        @Scheduled(cron = "${app.scheduler.fx-refresh-cron:0 0 2 * * *}")
        public void refreshFxRates() {

                if (!lockService.tryAcquire(
                                "fx-refresh",
                                Duration.ofMinutes(30))) {

                        log.info(
                                        "[Scheduler] FX refresh already running on another instance - skipping");

                        return;
                }

                try {

                        log.info(
                                        "[Scheduler] Refreshing FX rates...");

                        CurrencyService.RefreshResult result = currencyService.refreshRates();

                        if (result == null) {

                                log.warn(
                                                "[Scheduler] FX refresh returned no result");

                                return;
                        }

                        log.info(
                                        "[Scheduler] FX rates {}: {}",
                                        result.success()
                                                        ? "refreshed via"
                                                        : "failed, using cache for",
                                        result.source());

                } catch (Exception e) {

                        log.error(
                                        "[Scheduler] FX refresh failed: {}",
                                        e.getMessage(),
                                        e);

                } finally {

                        lockService.release("fx-refresh");
                }
        }

        /**
         * Midnight cleanup of expired idempotency keys.
         */
        @Scheduled(cron = "${app.scheduler.idempotency-cleanup-cron:0 0 0 * * *}")
        @Transactional
        public void cleanupIdempotencyKeys() {

                if (!lockService.tryAcquire(
                                "idempotency-cleanup",
                                Duration.ofMinutes(30))) {

                        log.info(
                                        "[Scheduler] Idempotency cleanup already running on another instance - skipping");

                        return;
                }

                try {

                        LocalDateTime now = LocalDateTime.now();

                        List<IdempotencyKey> allKeys = idempotencyRepo.findAll();

                        if (allKeys == null) {
                                allKeys = List.of();
                        }

                        List<IdempotencyKey> expired = allKeys.stream()
                                        .filter(
                                                        key -> key != null
                                                                        && key.getExpiresAt() != null
                                                                        && key.getExpiresAt()
                                                                                        .isBefore(now))
                                        .toList();

                        if (!expired.isEmpty()) {

                                idempotencyRepo.deleteAll(expired);

                                log.info(
                                                "[Scheduler] Cleaned {} expired idempotency keys",
                                                expired.size());
                        }

                } finally {

                        lockService.release("idempotency-cleanup");
                }
        }

        /*
         * ============================================================
         * HELPERS
         * ============================================================
         */

        private boolean belongsToOrganization(
                        Loan loan,
                        Organization organization) {

                if (loan == null
                                || organization == null
                                || organization.getId() == null
                                || loan.getOrganization() == null
                                || loan.getOrganization().getId() == null) {

                        return false;
                }

                return organization.getId()
                                .equals(
                                                loan.getOrganization().getId());
        }

        /**
         * Normalize a monetary BigDecimal without ever converting it
         * through double.
         */
        private BigDecimal money(BigDecimal value) {

                if (value == null) {
                        return ZERO;
                }

                return value.setScale(
                                MONEY_SCALE,
                                MONEY_ROUNDING);
        }

        private BigDecimal calculateDailyRate(
                        BigDecimal rate,
                        String rateType) {

                if (rate == null || rate.signum() <= 0) {
                        return ZERO;
                }

                if ("ANNUAL".equalsIgnoreCase(rateType)) {
                        return rate
                                        .divide(BigDecimal.valueOf(100), 16, MONEY_ROUNDING)
                                        .divide(BigDecimal.valueOf(365), 16, MONEY_ROUNDING);
                }

                return FinancialPolicy.dailyRateFraction(rate, LocalDate.now());
        }

        private int calculateDaysOverdue(
                        LocalDate dueDate,
                        LocalDate today) {

                if (dueDate == null
                                || today == null) {
                        return 0;
                }

                long days = ChronoUnit.DAYS.between(
                                dueDate,
                                today);

                if (days <= 0) {
                        return 0;
                }

                return days > Integer.MAX_VALUE
                                ? Integer.MAX_VALUE
                                : (int) days;
        }

        /**
         * Determines the amount shown in a payment reminder.
         *
         * Priority:
         *
         * 1. Scheduled payment amount.
         * 2. Outstanding amount after payment.
         * 3. Next installment amount on the loan.
         * 4. Outstanding loan balance.
         */
        private BigDecimal resolveReminderAmount(
                        Payment payment,
                        Loan loan) {

                if (payment != null) {

                        BigDecimal scheduled = money(payment.getAmountDecimal());

                        if (scheduled.compareTo(ZERO) > 0) {
                                return scheduled;
                        }

                        BigDecimal remaining = money(payment.getOutstandingAfterDecimal());

                        if (remaining.compareTo(ZERO) > 0) {
                                return remaining;
                        }
                }

                if (loan != null) {

                        BigDecimal next = money(
                                        loan.getNextInstallmentAmountDecimal());

                        if (next.compareTo(ZERO) > 0) {
                                return next;
                        }

                        return money(
                                        loan.getOutstandingBalanceDecimal());
                }

                return ZERO;
        }
}