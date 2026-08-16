package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.WebhookReceipt;
import com.patrick.fintech.loan_backend.repository.WebhookReceiptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class WebhookReplayGuard {
    private final WebhookReceiptRepository repository;

    public Claim claim(String provider, String eventKey, String rawBody) {
        String p = provider.trim().toUpperCase();
        String key = eventKey == null || eventKey.isBlank() ? sha256(rawBody == null ? "" : rawBody) : eventKey.trim();
        if (key.length() > 128)
            key = sha256(key);
        String hash = sha256(rawBody == null ? "" : rawBody);
        try {
            return create(p, key, hash);
        } catch (DataIntegrityViolationException race) {
            return existing(p, key, hash);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected Claim create(String provider, String key, String hash) {
        var existing = repository.findByProviderAndEventKey(provider, key);
        if (existing.isPresent())
            return validate(existing.get(), hash);
        repository.saveAndFlush(WebhookReceipt.builder().provider(provider).eventKey(key).payloadHash(hash)
                .status("PROCESSING").build());
        return new Claim(false, true);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    protected Claim existing(String provider, String key, String hash) {
        return repository.findByProviderAndEventKey(provider, key).map(r -> validate(r, hash))
                .orElse(new Claim(false, true));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessed(String provider, String eventKey, String rawBody) {
        String p = provider.trim().toUpperCase();
        String key = eventKey == null || eventKey.isBlank() ? sha256(rawBody == null ? "" : rawBody) : eventKey.trim();
        if (key.length() > 128)
            key = sha256(key);
        repository.findByProviderAndEventKey(p, key).ifPresent(r -> {
            r.setStatus("PROCESSED");
            r.setProcessedAt(LocalDateTime.now());
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String provider, String eventKey, String rawBody) {
        String p = provider.trim().toUpperCase();
        String key = eventKey == null || eventKey.isBlank() ? sha256(rawBody == null ? "" : rawBody) : eventKey.trim();
        if (key.length() > 128)
            key = sha256(key);
        repository.findByProviderAndEventKey(p, key).ifPresent(r -> {
            r.setStatus("FAILED");
        });
    }

    private Claim validate(WebhookReceipt r, String hash) {
        if (!hash.equals(r.getPayloadHash()))
            throw new ReplayConflictException("Webhook event key was reused with a different payload");
        if ("PROCESSED".equals(r.getStatus()))
            return new Claim(true, false);
        if (r.getCreatedAt() != null && r.getCreatedAt().plusMinutes(10).isBefore(java.time.LocalDateTime.now()))
            return new Claim(false, true);
        return new Claim(false, false);
    }

    private static String sha256(String value) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder s = new StringBuilder(64);
            for (byte b : h)
                s.append(String.format("%02x", b));
            return s.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void cleanup() {
        repository.deleteByCreatedAtBefore(LocalDateTime.now().minusDays(90));
    }

    public record Claim(boolean replay, boolean first) {
    }

    public static class ReplayConflictException extends RuntimeException {
        public ReplayConflictException(String m) {
            super(m);
        }
    }
}
