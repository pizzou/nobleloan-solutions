package com.patrick.fintech.loan_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Public API representation of an import batch.
 *
 * IMPORTANT:
 * Never expose ImportBatch directly from a controller.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportBatchResponse {

    private Long id;

    private Long organizationId;

    private Long importedById;

    private String importedByName;

    private String fileName;

    private Integer totalRows;

    private Integer successCount;

    private Integer failureCount;

    private String status;

    private LocalDateTime createdAt;

    private Integer processedRows;

    private Integer progressPercent;

    private Long fileSize;

    private String errorMessage;

    private boolean errorReportAvailable;
}