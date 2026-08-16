package com.patrick.fintech.loan_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.patrick.fintech.loan_backend.mapper.ResponseDtoMapper;
import com.patrick.fintech.loan_backend.model.IdempotencyKey;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {
    private final IdempotencyKeyRepository repo;
    private final ObjectMapper objectMapper;

    @Transactional
    public IdempotencyOutcome checkOrReserve(String key, Organization org, String endpoint, String requestBody) {
        if (key == null || key.isBlank())
            return IdempotencyOutcome.proceed();
        if (org == null || org.getId() == null)
            throw new IllegalArgumentException("Organization is required for idempotency");
        if (key.length() > 128)
            throw new IllegalArgumentException("Idempotency-Key is too long");

        String hash = sha256(requestBody == null ? "" : requestBody);
        Optional<IdempotencyKey> existing = repo.findByKeyAndOrganization(key.trim(), org);
        if (existing.isPresent())
            return existingOutcome(existing.get(), hash);

        try {
            repo.saveAndFlush(IdempotencyKey.builder()
                    .key(key.trim())
                    .organization(org)
                    .endpoint(endpoint)
                    .requestHash(hash)
                    .status(IdempotencyKey.Status.IN_PROGRESS)
                    .build());
            return IdempotencyOutcome.proceed();
        } catch (DataIntegrityViolationException race) {
            // Unique (organization_id,idempotency_key) won by a concurrent request.
            // The current transaction may be rollback-only, so do not query here.
            throw new IdempotencyConflictException("A request with this Idempotency-Key is already being processed",
                    race);
        }
    }

    private IdempotencyOutcome existingOutcome(IdempotencyKey rec, String hash) {
        if (!hash.equals(rec.getRequestHash()))
            throw new IdempotencyConflictException("Idempotency-Key was reused with a different request body");
        if (rec.getStatus() == IdempotencyKey.Status.COMPLETED)
            return IdempotencyOutcome.replay(rec.getResponseBody(), rec.getResponseStatusCode());
        if (rec.getStatus() == IdempotencyKey.Status.FAILED) {
            rec.setStatus(IdempotencyKey.Status.IN_PROGRESS);
            rec.setResponseBody(null);
            rec.setResponseStatusCode(null);
            return IdempotencyOutcome.proceed();
        }
        throw new IdempotencyConflictException("Request with this Idempotency-Key is already in progress");
    }

    @Transactional
    public void recordSuccess(String key, Organization org, Object responseBody, int statusCode) {
        if (key == null || key.isBlank())
            return;
        repo.findByKeyAndOrganization(key.trim(), org).ifPresent(rec -> {
            try {
                rec.setResponseBody(objectMapper.writeValueAsString(ResponseDtoMapper.safe(responseBody)));
            } catch (Exception e) {
                throw new IllegalStateException("Could not persist idempotent response", e);
            }
            rec.setResponseStatusCode(statusCode);
            rec.setStatus(IdempotencyKey.Status.COMPLETED);
            repo.save(rec);
        });
    }

    @Transactional
    public void recordFailure(String key, Organization org) {
        if (key == null || key.isBlank())
            return;
        repo.findByKeyAndOrganization(key.trim(), org).ifPresent(rec -> {
            rec.setStatus(IdempotencyKey.Status.FAILED);
            repo.save(rec);
        });
    }

    private String sha256(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(hash.length * 2);
            for (byte b : hash)
                out.append(String.format("%02x", b));
            return out.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public record IdempotencyOutcome(boolean isReplay, boolean shouldProceed, String cachedResponseBody,
            Integer cachedStatusCode) {
        static IdempotencyOutcome proceed() {
            return new IdempotencyOutcome(false, true, null, null);
        }

        static IdempotencyOutcome replay(String body, Integer status) {
            return new IdempotencyOutcome(true, false, body, status);
        }
    }

    public static class IdempotencyConflictException extends RuntimeException {
        public IdempotencyConflictException(String message) {
            super(message);
        }

        public IdempotencyConflictException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
