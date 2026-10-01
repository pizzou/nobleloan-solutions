package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.AuditLog;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AuditHashChainTest {

    @Test
    void hashUsesPersistedTimestampAndAllCanonicalAuditFields() {
        LocalDateTime timestamp = LocalDateTime.of(2026, 10, 1, 7, 30, 15, 123_000_000);
        Organization organization = Organization.builder().id(10L).name("Test Org").build();
        User user = User.builder().id(20L).name("Checker").build();

        AuditLog entry = AuditLog.builder()
                .organization(organization)
                .user(user)
                .action("PAYMENT_RECORDED")
                .entityType("PAYMENT")
                .entityId("99")
                .description("Payment recorded")
                .beforeValue("{\"status\":\"PENDING\"}")
                .afterValue("{\"status\":\"POSTED\"}")
                .ipAddress("192.0.2.10")
                .userAgent("JUnit")
                .module("Payments")
                .timestamp(timestamp)
                .build();

        String first = AuditHashChain.hashFor("GENESIS", entry);
        String second = AuditHashChain.hashFor("GENESIS", entry);

        assertThat(first).hasSize(64).isEqualTo(second);

        entry.setTimestamp(timestamp.plusNanos(1));
        assertThat(AuditHashChain.hashFor("GENESIS", entry)).isNotEqualTo(first);
    }

    @Test
    void changingBeforeOrAfterValueChangesTheAuditHash() {
        AuditLog entry = AuditLog.builder()
                .action("LOAN_APPROVED")
                .entityType("LOAN")
                .entityId("42")
                .description("Approved")
                .beforeValue("PENDING")
                .afterValue("APPROVED")
                .module("Loans")
                .timestamp(LocalDateTime.of(2026, 10, 1, 7, 30))
                .build();

        String original = AuditHashChain.hashFor("GENESIS", entry);
        entry.setAfterValue("REJECTED");

        assertThat(AuditHashChain.hashFor("GENESIS", entry)).isNotEqualTo(original);
    }
}
