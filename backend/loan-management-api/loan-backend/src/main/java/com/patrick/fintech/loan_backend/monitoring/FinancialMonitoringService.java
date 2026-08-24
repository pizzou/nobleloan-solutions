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
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Production financial monitoring loop.
 *
 * The monitoring loop never changes financial state. It runs the read-only
 * reconciliation engine, exposes Prometheus metrics, logs failures and uses
 * the existing in-app NotificationService for human alerts.
 */
@Service
@Slf4j
public class FinancialMonitoringService {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final FinancialReconciliationService reconciliationService;
    private final NotificationService notificationService;
    private final SchedulerLockService schedulerLockService;
    private final FinancialMonitoringState state;
    private final Counter reconciliationRuns;
    private final Counter reconciliationFailures;
    private final ConcurrentMap<Long, Long> lastAlertEpochMillis = new ConcurrentHashMap<>();

    @Value("${app.monitoring.reconciliation-enabled:true}")
    private boolean enabled;

    @Value("${app.monitoring.alert-cooldown-ms:3600000}")
    private long alertCooldownMs;

    @Value("${app.monitoring.reconciliation-lock-duration-ms:900000}")
    private long reconciliationLockDurationMs;

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
        this.reconciliationRuns = Counter.builder("loansaas_financial_reconciliation_runs_total")
                .description("Number of scheduled financial reconciliation runs")
                .register(meterRegistry);
        this.reconciliationFailures = Counter.builder("loansaas_financial_reconciliation_failures_total")
                .description("Number of organization reconciliation failures")
                .register(meterRegistry);
        Gauge.builder("loansaas_financial_reconciliation_failed_organizations", state, s -> s.getFailedOrganizations())
                .description("Organizations currently failing financial reconciliation")
                .register(meterRegistry);
        Gauge.builder("loansaas_financial_reconciliation_max_difference", state,
                s -> s.getLastMaximumDifference().doubleValue())
                .description("Largest financial reconciliation difference from the latest run")
                .register(meterRegistry);
        Gauge.builder("loansaas_financial_reconciliation_last_success_epoch_seconds", state,
                FinancialMonitoringState::getLastSuccessEpochSeconds)
                .description("Unix timestamp of the most recent successful financial reconciliation")
                .register(meterRegistry);
        Gauge.builder("loansaas_financial_reconciliation_last_run_epoch_seconds", state,
                FinancialMonitoringState::getLastRunEpochSeconds)
                .description("Unix timestamp of the most recent financial reconciliation attempt")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${app.monitoring.reconciliation-interval-ms:300000}", initialDelayString = "${app.monitoring.reconciliation-initial-delay-ms:60000}")
    public void runScheduledReconciliation() {
        if (!enabled) {
            return;
        }

        schedulerLockService.runExclusively(
                "financial-reconciliation",
                Duration.ofMillis(Math.max(60_000L, reconciliationLockDurationMs)),
                this::runReconciliationOnce);
    }

    private void runReconciliationOnce() {
        Instant startedAt = Instant.now();
        List<Organization> organizations = organizationRepository.findAll();
        int failed = 0;
        BigDecimal maximumDifference = BigDecimal.ZERO.setScale(2);
        List<String> failureMessages = new ArrayList<>();

        for (Organization organization : organizations) {
            if (organization == null || organization.getId() == null) {
                continue;
            }

            reconciliationRuns.increment();

            try {
                FinancialReconciliationService.ReconciliationReport report = reconciliationService
                        .reconcile(organization.getId());

                if (!report.balanced()) {
                    failed++;
                    reconciliationFailures.increment();
                    maximumDifference = maximumDifference.max(report.maximumDifference());
                    String message = "Financial reconciliation FAILED for organization "
                            + organization.getId()
                            + ": " + report.issues().stream()
                                    .limit(5)
                                    .map(FinancialReconciliationService.Issue::message)
                                    .reduce((a, b) -> a + " | " + b)
                                    .orElse("Unknown reconciliation failure.");
                    failureMessages.add(message);
                    log.error("{}", message);
                    alertOrganization(organization, report);
                } else {
                    log.info(
                            "Financial reconciliation PASS. organizationId={}, asOf={}, journals={}, loans={}",
                            organization.getId(),
                            report.asOf(),
                            report.journalEntryCount(),
                            report.loanCount());
                }
            } catch (Exception ex) {
                failed++;
                reconciliationFailures.increment();
                failureMessages.add(
                        "Organization " + organization.getId() + ": " + ex.getMessage());
                log.error(
                        "Financial reconciliation execution failed. organizationId={}",
                        organization.getId(),
                        ex);
                alertOperationalFailure(organization, ex);
            }
        }

        if (failed == 0) {
            state.recordSuccess(startedAt);
        } else {
            state.recordFailure(
                    startedAt,
                    failed,
                    maximumDifference,
                    String.join(" | ", failureMessages));
        }
    }

    private void alertOrganization(
            Organization organization,
            FinancialReconciliationService.ReconciliationReport report) {
        if (!shouldAlert(organization.getId())) {
            return;
        }

        List<User> recipients = financeRecipients(organization);
        if (recipients.isEmpty()) {
            return;
        }

        notificationService.notifyUsers(
                recipients,
                "URGENT: Financial reconciliation failed",
                "The accounting reconciliation for "
                        + organization.getName()
                        + " failed on " + report.asOf()
                        + ". Maximum difference: "
                        + report.maximumDifference().toPlainString()
                        + ". Accounting review is required before corrective entries are posted.",
                "FINANCIAL_RECONCILIATION_FAILURE",
                "/dashboard/accounting/reconciliation");
    }

    private void alertOperationalFailure(Organization organization, Exception ex) {
        if (!shouldAlert(organization.getId())) {
            return;
        }

        List<User> recipients = financeRecipients(organization);
        if (recipients.isEmpty()) {
            return;
        }

        notificationService.notifyUsers(
                recipients,
                "URGENT: Financial monitoring failure",
                "The financial reconciliation engine could not complete for "
                        + organization.getName()
                        + ". The monitoring job failed and requires immediate investigation."
                        + " Error: " + safeMessage(ex),
                "FINANCIAL_MONITORING_FAILURE",
                "/dashboard/accounting/reconciliation");
    }

    private List<User> financeRecipients(Organization organization) {
        return userRepository.findByOrganization(organization).stream()
                .filter(user -> user != null && user.getRole() != null)
                .filter(user -> {
                    String role = user.getRole().getName();
                    return role != null && ("ADMIN".equalsIgnoreCase(role)
                            || "MANAGER".equalsIgnoreCase(role)
                            || "ACCOUNTANT".equalsIgnoreCase(role));
                })
                .toList();
    }

    private boolean shouldAlert(Long organizationId) {
        long now = System.currentTimeMillis();
        Long previous = lastAlertEpochMillis.get(organizationId);
        if (previous != null && now - previous < Math.max(0L, alertCooldownMs)) {
            return false;
        }
        lastAlertEpochMillis.put(organizationId, now);
        return true;
    }

    private String safeMessage(Exception ex) {
        if (ex == null || ex.getMessage() == null || ex.getMessage().isBlank()) {
            return ex == null ? "Unknown error" : ex.getClass().getSimpleName();
        }
        return ex.getMessage().replaceAll("[\\r\\n]+", " ").trim();
    }

    public FinancialMonitoringState state() {
        return state;
    }
}
