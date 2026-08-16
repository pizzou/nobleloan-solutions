package com.patrick.fintech.loan_backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class JournalEntryResponse {
    private Long id, organizationId, branchId;
    private LocalDate entryDate;
    private String sourceType, sourceId, reference, description, createdBy;
    private Boolean reversed;
    private LocalDateTime createdAt;
    private List<JournalLineResponse> lines;
    @Data public static class JournalLineResponse { private Long id, accountId; private String accountCode, accountName, description; private BigDecimal debit, credit; }
}
