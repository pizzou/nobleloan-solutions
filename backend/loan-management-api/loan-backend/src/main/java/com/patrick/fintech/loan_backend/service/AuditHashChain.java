package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.AuditLog;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Canonical hashing rules for the audit hash chain.
 *
 * Keep this class as the single source of truth for both audit persistence and
 * verification. The field order deliberately matches the established audit
 * contract so existing business semantics are not changed.
 */
public final class AuditHashChain {

    private AuditHashChain() {
    }

    public static String hashFor(
            String previousHash,
            Long organizationId,
            Long userId,
            String action,
            String entityType,
            String entityId,
            String description,
            String beforeValue,
            String afterValue,
            String ipAddress,
            String userAgent,
            String module,
            String timestamp) {

        return sha256(String.join("|",
                value(previousHash),
                value(organizationId),
                value(userId),
                value(action),
                value(entityType),
                value(entityId),
                value(description),
                value(beforeValue),
                value(afterValue),
                value(ipAddress),
                value(userAgent),
                value(module),
                value(timestamp)));
    }

    public static String hashFor(String previousHash, AuditLog entry) {
        return hashFor(
                previousHash,
                entry.getOrganization() == null ? null : entry.getOrganization().getId(),
                entry.getUser() == null ? null : entry.getUser().getId(),
                entry.getAction(),
                entry.getEntityType(),
                entry.getEntityId(),
                entry.getDescription(),
                entry.getBeforeValue(),
                entry.getAfterValue(),
                entry.getIpAddress(),
                entry.getUserAgent(),
                entry.getModule(),
                entry.getTimestamp() == null ? null : entry.getTimestamp().toString());
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String sha256(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                out.append(String.format("%02x", b));
            }
            return out.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to calculate audit hash", e);
        }
    }
}
