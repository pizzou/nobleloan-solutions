package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.WebhookReceipt;
import com.patrick.fintech.loan_backend.repository.WebhookReceiptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class WebhookReplayGuard {

    private static final int MAX_EVENT_KEY_LENGTH = 128;
    private static final int PROCESSING_TIMEOUT_MINUTES = 10;
    private static final int RETENTION_DAYS = 90;

    private final WebhookReceiptRepository repository;

    /**
     * Atomically claims a webhook event.
     *
     * This method is intentionally transactional because the INSERT
     * and subsequent SELECT must execute against a consistent database
     * transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Claim claim(
            String provider,
            String eventKey,
            String rawBody) {

        String normalizedProvider = normalizeProvider(provider);

        String normalizedEventKey = normalizeEventKey(
                eventKey,
                rawBody);

        String payloadHash = sha256(
                rawBody == null ? "" : rawBody);

        try {

            int inserted = repository.tryClaim(
                    normalizedProvider,
                    normalizedEventKey,
                    payloadHash);

            /*
             * We won the PostgreSQL INSERT.
             */
            if (inserted == 1) {
                return new Claim(
                        false,
                        true);
            }

            /*
             * Another request already owns this event.
             */
            WebhookReceipt existing = repository
                    .findByProviderAndEventKey(
                            normalizedProvider,
                            normalizedEventKey)
                    .orElseThrow(() -> new IllegalStateException(
                            "Webhook claim disappeared after "
                                    + "PostgreSQL conflict"));

            return validate(
                    existing,
                    payloadHash);

        } catch (DataAccessException exception) {

            throw new IllegalStateException(
                    "Unable to claim webhook event safely",
                    exception);
        }
    }

    /**
     * Marks a successfully processed webhook.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessed(
            String provider,
            String eventKey,
            String rawBody) {

        String normalizedProvider = normalizeProvider(provider);

        String normalizedEventKey = normalizeEventKey(
                eventKey,
                rawBody);

        repository.findByProviderAndEventKey(
                normalizedProvider,
                normalizedEventKey)
                .ifPresent(receipt -> {

                    receipt.setStatus("PROCESSED");
                    receipt.setProcessedAt(
                            LocalDateTime.now());
                });
    }

    /**
     * Marks a failed webhook so it can be retried safely.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(
            String provider,
            String eventKey,
            String rawBody) {

        String normalizedProvider = normalizeProvider(provider);

        String normalizedEventKey = normalizeEventKey(
                eventKey,
                rawBody);

        repository.findByProviderAndEventKey(
                normalizedProvider,
                normalizedEventKey)
                .ifPresent(receipt -> receipt.setStatus("FAILED"));
    }

    private Claim validate(
            WebhookReceipt receipt,
            String payloadHash) {

        /*
         * Same event ID with a different payload is a security violation.
         */
        if (!payloadHash.equals(
                receipt.getPayloadHash())) {

            throw new ReplayConflictException(
                    "Webhook event key was reused with "
                            + "a different payload");
        }

        /*
         * Already completed.
         */
        if ("PROCESSED".equals(
                receipt.getStatus())) {

            return new Claim(
                    true,
                    false);
        }

        /*
         * A stale PROCESSING record may have been created by a crashed
         * application instance.
         *
         * Allow recovery.
         */
        if (receipt.getCreatedAt() != null
                && receipt.getCreatedAt()
                        .plusMinutes(PROCESSING_TIMEOUT_MINUTES)
                        .isBefore(LocalDateTime.now())) {

            receipt.setStatus("PROCESSING");
            receipt.setProcessedAt(null);

            return new Claim(
                    false,
                    true);
        }

        /*
         * Another request is currently processing it.
         */
        return new Claim(
                false,
                false);
    }

    private String normalizeProvider(
            String provider) {

        if (provider == null
                || provider.isBlank()) {

            throw new IllegalArgumentException(
                    "Webhook provider is required");
        }

        return provider
                .trim()
                .toUpperCase();
    }

    private String normalizeEventKey(
            String eventKey,
            String rawBody) {

        String key;

        if (eventKey == null
                || eventKey.isBlank()) {

            key = sha256(
                    rawBody == null
                            ? ""
                            : rawBody);

        } else {

            key = eventKey.trim();
        }

        if (key.length() > MAX_EVENT_KEY_LENGTH) {
            key = sha256(key);
        }

        return key;
    }

    private static String sha256(
            String value) {

        try {

            byte[] hash = MessageDigest
                    .getInstance("SHA-256")
                    .digest(
                            value.getBytes(
                                    StandardCharsets.UTF_8));

            StringBuilder result = new StringBuilder(64);

            for (byte b : hash) {

                result.append(
                        String.format(
                                "%02x",
                                b));
            }

            return result.toString();

        } catch (Exception exception) {

            throw new IllegalStateException(
                    "SHA-256 algorithm unavailable",
                    exception);
        }
    }

    /**
     * Removes old webhook receipts.
     */
    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void cleanup() {

        repository.deleteByCreatedAtBefore(
                LocalDateTime.now()
                        .minusDays(RETENTION_DAYS));
    }

    public record Claim(
            boolean replay,
            boolean first) {
    }

    public static class ReplayConflictException
            extends RuntimeException {

        public ReplayConflictException(
                String message) {

            super(message);
        }
    }
}