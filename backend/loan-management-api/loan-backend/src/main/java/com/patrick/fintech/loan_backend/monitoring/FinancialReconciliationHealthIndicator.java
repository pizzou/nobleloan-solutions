package com.patrick.fintech.loan_backend.monitoring;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("financialReconciliation")
public class FinancialReconciliationHealthIndicator implements HealthIndicator {

    private final FinancialMonitoringState state;

    public FinancialReconciliationHealthIndicator(FinancialMonitoringState state) {
        this.state = state;
    }

    @Override
    public Health health() {
        if (state.getFailedOrganizations() > 0) {
            return Health.down()
                    .withDetail("failedOrganizations", state.getFailedOrganizations())
                    .withDetail("lastRunAt", state.getLastRunAt())
                    .withDetail("lastSuccessAt", state.getLastSuccessAt())
                    .withDetail("maximumDifference", state.getLastMaximumDifference())
                    .withDetail("failure", state.getLastFailure())
                    .build();
        }

        return Health.up()
                .withDetail("lastRunAt", state.getLastRunAt())
                .withDetail("lastSuccessAt", state.getLastSuccessAt())
                .build();
    }
}
