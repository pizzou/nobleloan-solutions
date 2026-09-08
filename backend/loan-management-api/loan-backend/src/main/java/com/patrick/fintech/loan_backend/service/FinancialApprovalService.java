package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.FinancialApproval;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.repository.FinancialApprovalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FinancialApprovalService {


private final FinancialApprovalRepository repo;

private static final Set<String> TYPES = Set.of(
        "WRITE_OFF",
        "RESTRUCTURE",
        "EXPENSE",
        "BANK_TRANSFER",
        "CASH_WITHDRAWAL",
        "MANUAL_ADJUSTMENT",
        "JOURNAL_REVERSAL",
        "PAYMENT_ADJUSTMENT",
        "BANK_RECONCILIATION",
        "LOAN_EXTENSION",
        "MORATORIUM"
);

private static final BigDecimal LEVEL_1_MAX = new BigDecimal("1000000.00");
private static final BigDecimal LEVEL_2_MAX = new BigDecimal("5000000.00");


@Transactional
public FinancialApproval submit(
        Organization org,
        String operationType,
        String operationId,
        BigDecimal amount,
        String reason,
        String payload,
        Long makerId,
        String makerName
) {
    requireOrg(org);

    String type = normOperationType(operationType);
    String normalizedOperationId = cleanRequired(operationId, "Operation ID is required");

    if (!TYPES.contains(type)) {
        throw new IllegalArgumentException(
                "Unsupported financial approval operation: " + type
        );
    }

    if (makerId == null) {
        throw new IllegalArgumentException("Maker user ID is required");
    }

    if (amount != null && amount.signum() < 0) {
        throw new IllegalArgumentException("Approval amount cannot be negative");
    }

    BigDecimal normalizedAmount = money(amount);

    if (repo.findFirstByOrganization_IdAndOperationTypeAndOperationIdAndStatus(
            org.getId(),
            type,
            normalizedOperationId,
            "PENDING"
    ).isPresent()) {
        throw new IllegalStateException(
                "A pending approval already exists for this operation"
        );
    }

    int level = requiredLevel(normalizedAmount);

    String normalizedPayload = payload == null ? "" : payload;

    FinancialApproval approval = FinancialApproval.builder()
            .organization(org)
            .operationType(type)
            .operationId(normalizedOperationId)
            .amount(normalizedAmount)
            .makerUserId(makerId)
            .makerName(clean(makerName))
            .requiredLevel(level)
            .reason(clean(reason))
            .payloadHash(sha256(normalizedPayload))
            .status("PENDING")
            .build();

    try {
        return repo.save(approval);
    } catch (DataIntegrityViolationException ex) {
        throw new IllegalStateException(
                "A pending approval already exists for this financial operation", ex);
    }
}


@Transactional
public FinancialApproval approve(
        Long id,
        Organization org,
        Long checkerId,
        String checkerName,
        String checkerRole,
        String comments
) {
    requireOrg(org);

    if (id == null) {
        throw new IllegalArgumentException("Financial approval ID is required");
    }

    FinancialApproval approval = repo.findForUpdate(
            id,
            org.getId()
    ).orElseThrow(
            () -> new IllegalArgumentException(
                    "Financial approval not found: " + id
            )
    );

    if (!"PENDING".equalsIgnoreCase(approval.getStatus())) {
        throw new IllegalStateException(
                "Approval is already " + approval.getStatus()
        );
    }

    if (checkerId == null) {
        throw new IllegalArgumentException("Checker user ID is required");
    }

    
    enforceApprovalAuthority(approval, checkerRole);

   
    if (checkerId.equals(approval.getMakerUserId())) {
        throw new IllegalStateException(
                "Maker and checker must be different users"
        );
    }

    approval.setCheckerUserId(checkerId);
    approval.setCheckerName(clean(checkerName));
    approval.setReason(join(approval.getReason(), comments));
    approval.setStatus("APPROVED");
    approval.setDecidedAt(LocalDateTime.now());

    return repo.save(approval);
}


@Transactional
public FinancialApproval reject(
        Long id,
        Organization org,
        Long checkerId,
        String checkerName,
        String reason
) {
    return reject(
            id,
            org,
            checkerId,
            checkerName,
            "",
            reason
    );
}


@Transactional
public FinancialApproval reject(
        Long id,
        Organization org,
        Long checkerId,
        String checkerName,
        String checkerRole,
        String reason
) {
    requireOrg(org);

    if (id == null) {
        throw new IllegalArgumentException("Financial approval ID is required");
    }

    FinancialApproval approval = repo.findForUpdate(
            id,
            org.getId()
    ).orElseThrow(
            () -> new IllegalArgumentException(
                    "Financial approval not found: " + id
            )
    );

    if (!"PENDING".equalsIgnoreCase(approval.getStatus())) {
        throw new IllegalStateException(
                "Approval is already " + approval.getStatus()
        );
    }

    if (checkerId == null) {
        throw new IllegalArgumentException(
                "Checker user ID is required"
        );
    }

    if (checkerId.equals(approval.getMakerUserId())) {
        throw new IllegalStateException(
                "Maker and checker must be different users"
        );
    }

   
    enforceApprovalAuthority(approval, checkerRole);

    approval.setCheckerUserId(checkerId);
    approval.setCheckerName(clean(checkerName));
    approval.setReason(join(approval.getReason(), reason));
    approval.setStatus("REJECTED");
    approval.setDecidedAt(LocalDateTime.now());

    return repo.save(approval);
}


@Transactional
public FinancialApproval requireApproved(
        Long id,
        Organization org,
        String operationType,
        String operationId
) {
    requireOrg(org);

    if (id == null) {
        throw new IllegalArgumentException(
                "Financial approval ID is required"
        );
    }

    FinancialApproval approval = repo.findForUpdate(
            id,
            org.getId()
    ).orElseThrow(
            () -> new IllegalArgumentException(
                    "Approval not found: " + id
            )
    );

    if (!"APPROVED".equalsIgnoreCase(approval.getStatus())) {
        throw new IllegalStateException(
                "Financial operation requires an APPROVED maker-checker record"
        );
    }

    String expectedType = normOperationType(operationType);

    if (!expectedType.equals(approval.getOperationType())) {
        throw new IllegalStateException(
                "Approval operation type mismatch. Expected="
                        + expectedType
                        + ", actual="
                        + approval.getOperationType()
        );
    }

    if (operationId != null) {
        String normalizedOperationId = cleanRequired(
                operationId,
                "Operation ID is required"
        );

        if (!Objects.equals(
                normalizedOperationId,
                approval.getOperationId()
        )) {
            throw new IllegalStateException(
                    "Approval operation ID mismatch"
            );
        }
    }

    return approval;
}

/** Atomically consumes an approved financial control. The caller must execute
 * the actual financial mutation in the same database transaction. */
@Transactional
public FinancialApproval consumeApproved(
        Long id,
        Organization org,
        String operationType,
        String operationId) {

    FinancialApproval approval = requireApproved(id, org, operationType, operationId);
    approval.setStatus("CONSUMED");
    approval.setConsumedAt(LocalDateTime.now());
    return repo.save(approval);
}


public int requiredLevel(BigDecimal amount) {
    if (amount == null) {
        return 1;
    }

    BigDecimal normalizedAmount = money(amount);

    if (normalizedAmount.compareTo(LEVEL_2_MAX) > 0) {
        return 3;
    }

    if (normalizedAmount.compareTo(LEVEL_1_MAX) > 0) {
        return 2;
    }

    return 1;
}


private void enforceApprovalAuthority(
        FinancialApproval approval,
        String checkerRole
) {
    if (approval == null) {
        throw new IllegalArgumentException(
                "Financial approval is required"
        );
    }

    if (checkerRole == null || checkerRole.isBlank()) {
        throw new SecurityException(
                "Checker role is required"
        );
    }

    String role = checkerRole
            .trim()
            .toUpperCase(Locale.ROOT);

   
    if (role.startsWith("ROLE_")) {
        role = role.substring(5);
    }

    int checkerLevel;

    switch (role) {
        case "ADMIN":
            checkerLevel = 3;
            break;

        case "MANAGER":
            checkerLevel = 2;
            break;

        case "ACCOUNTANT":
            checkerLevel = 1;
            break;

        default:
            throw new SecurityException(
                    "User is not authorized to act as a financial checker: "
                            + checkerRole
            );
    }

    int requiredLevel = approval.getRequiredLevel();

    if (requiredLevel < 1 || requiredLevel > 3) {
        throw new IllegalStateException(
                "Invalid approval authority level: " + requiredLevel
        );
    }

    if (checkerLevel < requiredLevel) {
        throw new SecurityException(
                "Insufficient approval authority. Required level="
                        + requiredLevel
                        + ", checker level="
                        + checkerLevel
        );
    }
}


public String sha256(String value) {
    try {
        String input = value == null ? "" : value;

        byte[] bytes = MessageDigest
                .getInstance("SHA-256")
                .digest(input.getBytes(StandardCharsets.UTF_8));

        StringBuilder result = new StringBuilder(bytes.length * 2);

        for (byte valueByte : bytes) {
            result.append(
                    String.format(
                            Locale.ROOT,
                            "%02x",
                            valueByte
                    )
            );
        }

        return result.toString();

    } catch (Exception e) {
        throw new IllegalStateException(
                "Unable to hash approval payload",
                e
        );
    }
}


private BigDecimal money(BigDecimal value) {
    if (value == null) {
        return null;
    }

    if (value.signum() < 0) {
        throw new IllegalArgumentException(
                "Monetary amount cannot be negative"
        );
    }

    return value.setScale(
            2,
            RoundingMode.HALF_UP
    );
}


private String normOperationType(String value) {
    if (value == null || value.isBlank()) {
        throw new IllegalArgumentException(
                "Operation type is required"
        );
    }

    return value
            .trim()
            .toUpperCase(Locale.ROOT);
}

private String clean(String value) {
    if (value == null || value.isBlank()) {
        return null;
    }

    return value.trim();
}


private String cleanRequired(
        String value,
        String errorMessage
) {
    if (value == null || value.isBlank()) {
        throw new IllegalArgumentException(errorMessage);
    }

    String normalized = value.trim();
    if (normalized.length() > 100) {
        throw new IllegalArgumentException(
                "Operation ID exceeds the maximum supported length of 100 characters");
    }
    return normalized;
}


private String join(String first, String second) {
    String x = clean(first);
    String y = clean(second);

    if (x == null) {
        return y;
    }

    if (y == null) {
        return x;
    }

    return x + " | " + y;
}


private void requireOrg(Organization organization) {
    if (organization == null || organization.getId() == null) {
        throw new IllegalArgumentException(
                "Organization is required"
        );
    }
}


}
