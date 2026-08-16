package com.patrick.fintech.loan_backend.mapper;

import com.patrick.fintech.loan_backend.dto.ImportBatchResponse;
import com.patrick.fintech.loan_backend.model.ImportBatch;

import java.util.List;

public final class ImportBatchResponseMapper {

    private ImportBatchResponseMapper() {
    }

    public static ImportBatchResponse toResponse(ImportBatch source) {

        if (source == null) {
            return null;
        }

        Long organizationId = source.getOrganization() == null
                ? null
                : source.getOrganization().getId();

        Long importedById = source.getImportedBy() == null
                ? null
                : source.getImportedBy().getId();

        String importedByName = source.getImportedBy() == null
                ? null
                : source.getImportedBy().getFullName();

        return ImportBatchResponse.builder()
                .id(source.getId())
                .organizationId(organizationId)
                .importedById(importedById)
                .importedByName(importedByName)
                .fileName(source.getFileName())
                .totalRows(source.getTotalRows())
                .successCount(source.getSuccessCount())
                .failureCount(source.getFailureCount())
                .status(source.getStatus())
                .createdAt(source.getCreatedAt())
                .processedRows(source.getProcessedRows())
                .progressPercent(source.getProgressPercent())
                .fileSize(source.getFileSize())
                .errorMessage(source.getErrorMessage())
                .errorReportAvailable(
                        source.getErrorReportPath() != null
                                && !source.getErrorReportPath().isBlank())
                .build();
    }

    public static List<ImportBatchResponse> toResponses(
            List<ImportBatch> source) {

        return source.stream()
                .map(ImportBatchResponseMapper::toResponse)
                .toList();
    }
}