package com.patrick.fintech.loan_backend.monitoring;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class FinancialMonitoringState {

    private final AtomicReference<Instant> lastRunAt = new AtomicReference<>();
    private final AtomicReference<Instant> lastSuccessAt = new AtomicReference<>();
    private final AtomicReference<String> lastFailure = new AtomicReference<>();
    private final AtomicReference<BigDecimal> lastMaximumDifference =
            new AtomicReference<>(BigDecimal.ZERO.setScale(2));
    private final AtomicInteger failedOrganizations = new AtomicInteger();

    public void recordSuccess(Instant timestamp) {
        lastRunAt.set(timestamp);
        lastSuccessAt.set(timestamp);
        lastFailure.set(null);
        failedOrganizations.set(0);
        lastMaximumDifference.set(BigDecimal.ZERO.setScale(2));
    }

    public void recordFailure(
            Instant timestamp,
            int failedCount,
            BigDecimal maximumDifference,
            String failure) {
        lastRunAt.set(timestamp);
        failedOrganizations.set(Math.max(0, failedCount));
        lastMaximumDifference.set(
                maximumDifference == null
                        ? BigDecimal.ZERO.setScale(2)
                        : maximumDifference);
        lastFailure.set(failure);
    }

    public Instant getLastRunAt() {
        return lastRunAt.get();
    }

    public Instant getLastSuccessAt() {
        return lastSuccessAt.get();
    }

    public String getLastFailure() {
        return lastFailure.get();
    }

    public BigDecimal getLastMaximumDifference() {
        return lastMaximumDifference.get();
    }

    public int getFailedOrganizations() {
        return failedOrganizations.get();
    }
}
