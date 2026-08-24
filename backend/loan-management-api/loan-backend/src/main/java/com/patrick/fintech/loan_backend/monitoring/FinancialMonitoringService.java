package com.patrick.fintech.loan_backend.monitoring;

import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import com.patrick.fintech.loan_backend.repository.UserRepository;
import com.patrick.fintech.loan_backend.service.FinancialReconciliationService;
import com.patrick.fintech.loan_backend.service.NotificationService;
import com.patrick.fintech.loan_backend.service.SchedulerLockService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Production financial monitoring loop.
 *
 * <p>
 * This service is deliberately READ-ONLY from a financial perspective.
 * It never modifies accounting records, loan balances, payments, journal
 * entries, or reconciliation results.
 * </p>
 *
 * <p>
 * When reconciliation fails, the failure remains visible until an authorized
 * accounting user investigates the underlying transaction and posts an
 * appropriate correcting journal or reverses the originating transaction.
 * </p>
 *
 * <p>
 * The monitoring cycle verifies the authoritative financial reconciliation
 * engine for each organization and exposes operational metrics through
 * Micrometer/Prometheus.
 * </p>
 *
 * <p>
 * Because the application can run on multiple production instances, the
 * scheduled reconciliation job uses {@link SchedulerLockService} so that
 * only one instance performs the global reconciliation cycle at a time.
 * </p>
 */
@Service
@Slf4j
public class FinancialMonitoringService {

    private final OrganizationRepository organizationRepository;

    private final UserRepository userRepository;

    private final FinancialReconciliationService reconciliationService;

    private final NotificationService notificationService;

    /**
     * Distributed scheduler lock.
     *
     * <p>
     * This prevents duplicate financial reconciliation cycles when multiple
     * application instances are running simultaneously.
     * </p>
     */
    private final SchedulerLockService schedulerLockService;

    private final FinancialMonitoringState state;

    private final Counter reconciliationRuns;

    private final Counter reconciliationFailures;

    /**
     * Prevents the same organization from receiving repeated identical
     * reconciliation alerts on every monitoring cycle.
     */
    private final ConcurrentMap<Long, Long> lastAlertEpochMillis = new ConcurrentHashMap<>();

    @Value("${app.monitoring.reconciliation-enabled:true}")
    private boolean enabled;

    @Value("${app.monitoring.alert-cooldown-ms:3600000}")
    private long alertCooldownMs;

    @Value("${app.monitoring.lock-duration-ms:900000}")
    private long lockDurationMs;

    /**
     * Creates the financial monitoring service.
     *
     * <p>
     * SchedulerLockService is intentionally injected through the constructor.
     * It is a required production dependency because reconciliation must not
     * execute concurrently on multiple application instances.
     * </p>
     */
    public FinancialMonitoringService(
            OrganizationRepository organizationRepository,
            UserRepository userRepository,
            FinancialReconciliationService reconciliationService,
            NotificationService notificationService,
            SchedulerLockService schedulerLockService,
            FinancialMonitoringState state,
            MeterRegistry meterRegistry) {

        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.reconciliationService = reconciliationService;
        this.notificationService = notificationService;
        this.schedulerLockService = schedulerLockService;
        this.state = state;

        this.reconciliationRuns = Counter.builder(
                "loansaas_financial_reconciliation_runs_total")
                .description(
                        "Number of scheduled financial reconciliation runs")
                .register(meterRegistry);

        this.reconciliationFailures = Counter.builder(
                "loansaas_financial_reconciliation_failures_total")
                .description(
                        "Number of organization reconciliation failures")
                .register(meterRegistry);

        Gauge.builder(
                "loansaas_financial_reconciliation_failed_organizations",
                state,
                FinancialMonitoringState::getFailedOrganizations)
                .description(
                        "Organizations currently failing financial reconciliation")
                .register(meterRegistry);

        Gauge.builder(
                "loansaas_financial_reconciliation_max_difference",
                state,
                monitoringState -> monitoringState
                        .getLastMaximumDifference()
                        .doubleValue())
                .description(
                        "Largest financial reconciliation difference from the latest run")
                .register(meterRegistry);
    }

    /**
     * Scheduled production reconciliation.
     *
     * <p>
     * The actual reconciliation engine remains read-only. This monitoring
     * service only invokes it and reports the result.
     * </p>
     *
     * <p>
     * A distributed scheduler lock guarantees that only one application
     * instance performs the global cycle when the system is horizontally
     * scaled.
     * </p>
     */
    @Scheduled(fixedDelayString = "${app.monitoring.reconciliation-interval-ms:300000}", initialDelayString = "${app.monitoring.reconciliation-initial-delay-ms:60000}")
    public void runScheduledReconciliation() {

        if (!enabled) {
            log.debug(
                    "Financial reconciliation monitoring is disabled.");
            return;
        }

        long safeLockDuration = Math.max(60_000L, lockDurationMs);

        boolean lockAcquired;

        try {
            lockAcquired = schedulerLockService.tryAcquire(
                    "FINANCIAL_RECONCILIATION_MONITORING",
                    Duration.ofMillis(safeLockDuration));
        } catch (Exception ex) {
            log.error(
                    "Unable to acquire financial reconciliation scheduler lock.",
                    ex);
            return;
        }

        if (!lockAcquired) {
            log.debug(
                    "Financial reconciliation monitoring skipped because another "
                            + "application instance currently owns the scheduler lock.");
            return;
        }

        try {
            runReconciliationCycle();
        } catch (Exception ex) {
            log.error(
                    "Unexpected failure during financial reconciliation monitoring cycle.",
                    ex);
        } finally {
            try {
                schedulerLockService.release(
                        "FINANCIAL_RECONCILIATION_MONITORING");
            } catch (Exception ex) {
                /*
                 * Never hide the original reconciliation result because
                 * releasing the distributed lock failed.
                 */
                log.error(
                        "Failed to release financial reconciliation scheduler lock.",
                        ex);
            }
        }
    }

    /**
     * Executes one complete organization reconciliation cycle.
     *
     * <p>
     * This method never changes financial data.
     * </p>
     */
    private void runReconciliationCycle() {

        Instant startedAt = Instant.now();

        List<Organization> organizations;

        try {
            organizations = organizationRepository.findAll();
        } catch (Exception ex) {
            log.error(
                    "Unable to load organizations for financial reconciliation.",
                    ex);

            state.recordFailure(
                    startedAt,
                    1,
                    BigDecimal.ZERO.setScale(2),
                    "Unable to load organizations for reconciliation: "
                            + safeMessage(ex));

            return;
        }

        if (organizations == null) {
            organizations = List.of();
        }

        int failed = 0;

        BigDecimal maximumDifference = BigDecimal.ZERO.setScale(2);

        List<String> failureMessages = new ArrayList<>();

        for (Organization organization : organizations) {

            if (organization == null ||
                    organization.getId() == null) {
                continue;
            }

            reconciliationRuns.increment();

            Long organizationId = organization.getId();

            try {

                FinancialReconciliationService.ReconciliationReport report = reconciliationService.reconcile(
                        organizationId);

                if (report == null) {

                    failed++;
                    reconciliationFailures.increment();

                    String message = "Financial reconciliation returned no report "
                            + "for organization "
                            + organizationId
                            + ".";

                    failureMessages.add(message);

                    log.error(message);

                    alertOperationalFailure(
                            organization,
                            new IllegalStateException(
                                    "Financial reconciliation returned null report."));
                    continue;
                }

                if (!report.balanced()) {

                    failed++;
                    reconciliationFailures.increment();

                    BigDecimal reportDifference = report.maximumDifference();

                    if (reportDifference != null) {
                        maximumDifference = maximumDifference.max(
                                reportDifference.abs());
                    }

                    String message = "Financial reconciliation FAILED for organization "
                            + organizationId
                            + ": "
                            + report.issues()
                                    .stream()
                                    .limit(5)
                                    .map(
                                            FinancialReconciliationService.Issue::message)
                                    .reduce(
                                            (a, b) -> a + " | " + b)
                                    .orElse(
                                            "Unknown reconciliation failure.");

                    failureMessages.add(message);

                    log.error(
                            "{}",
                            message);

                    alertOrganization(
                            organization,
                            report);

                } else {

                    log.info(
                            "Financial reconciliation PASS. "
                                    + "organizationId={}, asOf={}, journals={}, loans={}",
                            organizationId,
                            report.asOf(),
                            report.journalEntryCount(),
                            report.loanCount());
                }

            } catch (Exception ex) {

                failed++;

                reconciliationFailures.increment();

                String message = "Organization "
                        + organizationId
                        + ": "
                        + safeMessage(ex);

                failureMessages.add(message);

                log.error(
                        "Financial reconciliation execution failed. "
                                + "organizationId={}",
                        organizationId,
                        ex);

                alertOperationalFailure(
                        organization,
                        ex);
            }
        }

        if (failed == 0) {

            state.recordSuccess(
                    startedAt);

        } else {

            state.recordFailure(
                    startedAt,
                    failed,
                    maximumDifference,
                    String.join(
                            " | ",
                            failureMessages));
        }
    }

    /**
     * Sends an accounting alert when an organization's financial
     * reconciliation fails.
     *
     * <p>
     * Existing NotificationService is deliberately reused. No duplicate
     * notification system is created.
     * </p>
     */
    private void alertOrganization(
            Organization organization,
            FinancialReconciliationService.ReconciliationReport report) {

        Long organizationId = organization.getId();

        if (!shouldAlert(organizationId)) {
            return;
        }

        List<User> recipients = financeRecipients(
                organization);

        if (recipients.isEmpty()) {

            log.warn(
                    "Financial reconciliation failed for organizationId={}, "
                            + "but no eligible accounting notification recipients were found.",
                    organizationId);

            return;
        }

        String organizationName = organization.getName() == null
                ? "Organization " + organizationId
                : organization.getName();

        BigDecimal maximumDifference = report.maximumDifference() == null
                ? BigDecimal.ZERO.setScale(2)
                : report.maximumDifference();

        notificationService.notifyUsers(
                recipients,
                "URGENT: Financial reconciliation failed",
                "The accounting reconciliation for "
                        + organizationName
                        + " failed on "
                        + report.asOf()
                        + ". Maximum difference: "
                        + maximumDifference.toPlainString()
                        + ". Accounting review is required before corrective entries are posted.",
                "FINANCIAL_RECONCILIATION_FAILURE",
                "/dashboard/accounting/reconciliation");
    }

    /**
     * Sends an operational alert when the reconciliation engine itself
     * cannot complete.
     */
    private void alertOperationalFailure(
            Organization organization,
            Exception ex) {

        Long organizationId = organization.getId();

        if (!shouldAlert(organizationId)) {
            return;
        }

        List<User> recipients = financeRecipients(
                organization);

        if (recipients.isEmpty()) {

            log.warn(
                    "Financial monitoring failed for organizationId={}, "
                            + "but no eligible accounting notification recipients were found.",
                    organizationId);

            return;
        }

        String organizationName = organization.getName() == null
                ? "Organization " + organizationId
                : organization.getName();

        notificationService.notifyUsers(
                recipients,
                "URGENT: Financial monitoring failure",
                "The financial reconciliation engine could not complete for "
                        + organizationName
                        + ". The monitoring job failed and requires immediate investigation."
                        + " Error: "
                        + safeMessage(ex),
                "FINANCIAL_MONITORING_FAILURE",
                "/dashboard/accounting/reconciliation");
    }

    /**
     * Determines the accounting users who should receive reconciliation
     * alerts.
     */
    private List<User> financeRecipients(
            Organization organization) {

        List<User> users;

        try {

            users = userRepository.findByOrganization(
                    organization);

        } catch (Exception ex) {

            log.error(
                    "Unable to load financial monitoring notification recipients. "
                            + "organizationId={}",
                    organization.getId(),
                    ex);

            return List.of();
        }

        if (users == null ||
                users.isEmpty()) {
            return List.of();
        }

        return users.stream()
                .filter(
                        user -> user != null
                                && user.getRole() != null)
                .filter(
                        user -> {

                            String role = user.getRole().getName();

                            if (role == null ||
                                    role.isBlank()) {
                                return false;
                            }

                            return "ADMIN".equalsIgnoreCase(role)
                                    || "MANAGER".equalsIgnoreCase(role)
                                    || "ACCOUNTANT".equalsIgnoreCase(role);
                        })
                .toList();
    }

    /**
     * Prevents alert storms while keeping the underlying reconciliation
     * failure visible in logs and metrics.
     */
    private boolean shouldAlert(
            Long organizationId) {

        if (organizationId == null) {
            return false;
        }

        long now = System.currentTimeMillis();

        long cooldown = Math.max(
                0L,
                alertCooldownMs);

        Long previous = lastAlertEpochMillis.get(
                organizationId);

        if (previous != null &&
                now - previous < cooldown) {

            return false;
        }

        lastAlertEpochMillis.put(
                organizationId,
                now);

        return true;
    }

    /**
     * Sanitizes exception messages before they are included in user-facing
     * notifications/log messages.
     */
    private String safeMessage(
            Exception ex) {

        if (ex == null) {
            return "Unknown error";
        }

        String message = ex.getMessage();

        if (message == null ||
                message.isBlank()) {

            return ex.getClass()
                    .getSimpleName();
        }

        return message
                .replaceAll(
                        "[\\r\\n]+",
                        " ")
                .trim();
    }

    /**
     * Exposes current monitoring state for metrics/tests.
     */
    public FinancialMonitoringState state() {
        return state;
    }
}