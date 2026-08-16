package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.WebhookReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface WebhookReceiptRepository
        extends JpaRepository<WebhookReceipt, Long> {

    Optional<WebhookReceipt> findByProviderAndEventKey(
            String provider,
            String eventKey);

    long deleteByCreatedAtBefore(
            LocalDateTime cutoff);

    @Modifying
    @Query(value = """
            INSERT INTO webhook_receipts
                (provider, event_key, payload_hash, status, created_at)
            VALUES
                (:provider, :eventKey, :payloadHash, 'PROCESSING', CURRENT_TIMESTAMP)
            ON CONFLICT (provider, event_key)
            DO NOTHING
            """, nativeQuery = true)
    int tryClaim(
            @Param("provider") String provider,
            @Param("eventKey") String eventKey,
            @Param("payloadHash") String payloadHash);
}