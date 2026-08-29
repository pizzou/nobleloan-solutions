package com.patrick.fintech.loan_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patrick.fintech.loan_backend.dto.ImportRowResult;
import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.ImportBatch;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.BorrowerRepository;
import com.patrick.fintech.loan_backend.repository.ImportBatchRepository;
import com.patrick.fintech.loan_backend.security.HmacIndexer;
import com.patrick.fintech.loan_backend.util.LedgerFileParser;
import com.patrick.fintech.loan_backend.util.StreamingLedgerFileParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ================================================================
 * LEGACY LOAN IMPORT SERVICE
 * ================================================================
 *
 * Production-oriented entry point for importing historical loan
 * ledgers from CSV / Excel files.
 *
 * Responsibilities:
 *
 * 1. Parse uploaded CSV / Excel ledger files.
 * 2. Validate import context.
 * 3. Clean Excel-specific values such as leading apostrophes.
 * 4. Normalize headers and cell values.
 * 5. Support safe preview mode.
 * 6. Create an auditable ImportBatch for committed imports.
 * 7. Delegate each row to LegacyLoanImportRowService.
 * 8. Keep individual rows transactionally independent.
 * 9. Record successful and failed rows.
 * 10. Update import batch status correctly.
 * 11. Produce an audit trail.
 *
 * IMPORTANT:
 *
 * LegacyLoanImportRowService owns the REQUIRES_NEW transaction
 * for each individual row.
 *
 * Therefore:
 *
 * Row 1 succeeds
 * Row 2 succeeds
 * Row 3 fails
 * Row 4 succeeds
 *
 * Rows 1, 2 and 4 remain committed.
 *
 * Row 3 is reported as failed.
 *
 * ================================================================
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LegacyLoanImportService {

        /*
         * ================================================================
         * CONFIGURATION
         * ================================================================
         */

        /**
         * Maximum number of rows allowed in one import.
         */
        private static final int MAX_IMPORT_ROWS = 10_000;

        /**
         * Maximum filename length stored in the database.
         */
        private static final int MAX_FILENAME_LENGTH = 255;

        /**
         * Maximum number of results retained in memory.
         */
        private static final int MAX_RESULTS = MAX_IMPORT_ROWS;

        /**
         * Import batch statuses.
         */
        public static final String STATUS_PROCESSING = "PROCESSING";
        public static final String STATUS_COMPLETED = "COMPLETED";
        public static final String STATUS_PARTIAL = "PARTIAL";
        public static final String STATUS_FAILED = "FAILED";

        private final LegacyLoanImportRowService rowService;

        private final ImportBatchRepository importBatchRepo;

        private final BorrowerRepository borrowerRepo;

        private final AuditService auditService;

        private final ObjectMapper objectMapper;

        /*
         * ================================================================
         * PREVIEW
         * ================================================================
         *
         * Preview performs the same validation/business logic as commit,
         * but does not persist borrowers or loans.
         *
         * It does NOT create an ImportBatch.
         */
        @Transactional(readOnly = true)
        public List<ImportRowResult> preview(
                        String filename,
                        InputStream in,
                        Organization org) throws IOException {

                validateImportRequest(
                                filename,
                                in,
                                org,
                                null);

                String safeFilename = normalizeFilename(filename);

                /*
                 * parsedRows is captured by the XLSX streaming lambda.
                 * It therefore MUST NOT be reassigned after the lambda
                 * is created.
                 */
                final List<Map<String, String>> parsedRows = new ArrayList<>();

                /*
                 * Physical worksheet row numbers for XLSX.
                 */
                final List<Integer> parsedRowNumbers = new ArrayList<>();

                try {
                        if (safeFilename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
                                StreamingLedgerFileParser.stream(
                                                safeFilename,
                                                in,
                                                MAX_IMPORT_ROWS,
                                                (sourceRowNumber, row) -> {
                                                        parsedRows.add(row);
                                                        parsedRowNumbers.add(
                                                                        Math.toIntExact(sourceRowNumber));
                                                });
                        } else {
                                List<Map<String, String>> parsedFileRows = LedgerFileParser.parse(
                                                safeFilename,
                                                in);

                                if (parsedFileRows != null && !parsedFileRows.isEmpty()) {
                                        parsedRows.addAll(parsedFileRows);
                                }
                        }

                } catch (IOException e) {

                        log.error(
                                        "Legacy loan preview file parsing failed. " +
                                                        "organizationId={}, filename={}",
                                        org.getId(),
                                        safeFilename,
                                        e);

                        throw new IOException(
                                        "The uploaded ledger could not be read. " +
                                                        "Please verify that the file is a valid CSV or Excel ledger.",
                                        e);
                }

                /*
                 * Clean Excel / CSV values before validation and application.
                 * Keep this as a different variable so the streaming list
                 * remains effectively final for the lambda above.
                 */
                final List<Map<String, String>> rows = sanitizeParsedRows(parsedRows);

                validateParsedRows(rows);

                Map<String, Borrower> sessionBorrowers = new HashMap<>();

                /*
                 * Preload all existing borrowers referenced by this file once.
                 * This avoids one borrower SELECT per workbook row during preview.
                 */
                preloadPreviewBorrowers(
                                rows,
                                org,
                                sessionBorrowers);

                List<ImportRowResult> results = new ArrayList<>(
                                Math.min(
                                                rows.size(),
                                                MAX_RESULTS));

                long startedAt = System.currentTimeMillis();

                int fallbackRowNumber = 1;

                for (int index = 0; index < rows.size(); index++) {

                        Map<String, String> row = rows.get(index);

                        final int rowNumber;

                        /*
                         * For XLSX, preserve the physical Excel row number.
                         * For CSV, retain the historical sequential behavior.
                         */
                        if (parsedRowNumbers.size() == rows.size()) {
                                rowNumber = parsedRowNumbers.get(index);
                        } else {
                                rowNumber = fallbackRowNumber + 1;
                        }

                        fallbackRowNumber = rowNumber;

                        ImportRowResult result = rowService.importRow(
                                        row,
                                        rowNumber,
                                        org,
                                        null,
                                        false,
                                        sessionBorrowers);

                        if (result != null) {
                                results.add(result);
                        } else {
                                results.add(
                                                ImportRowResult.builder()
                                                                .rowNumber(rowNumber)
                                                                .success(false)
                                                                .error(
                                                                                "The import service returned no result for this row.")
                                                                .build());
                        }
                }

                long durationMs = System.currentTimeMillis() - startedAt;

                long successful = results.stream()
                                .filter(ImportRowResult::isSuccess)
                                .count();

                long failed = results.size() - successful;

                log.info(
                                "Legacy loan preview completed. " +
                                                "organizationId={}, filename={}, rows={}, successful={}, failed={}, durationMs={}",
                                org.getId(),
                                safeFilename,
                                results.size(),
                                successful,
                                failed,
                                durationMs);

                return results;
        }

        /*
         * ================================================================
         * COMMIT
         * ================================================================
         *
         * Creates an ImportBatch as PROCESSING.
         *
         * Each row is then processed by LegacyLoanImportRowService.
         *
         * LegacyLoanImportRowService should use REQUIRES_NEW for the
         * individual row transaction.
         */
        public ImportBatch commit(
                        String filename,
                        InputStream in,
                        Organization org,
                        User importedBy) throws IOException {

                validateImportRequest(
                                filename,
                                in,
                                org,
                                importedBy);

                String safeFilename = normalizeFilename(filename);

                List<Map<String, String>> rows;

                try {

                        rows = LedgerFileParser.parse(
                                        safeFilename,
                                        in);

                } catch (IOException e) {

                        log.error(
                                        "Legacy loan commit file parsing failed. " +
                                                        "organizationId={}, filename={}",
                                        org.getId(),
                                        safeFilename,
                                        e);

                        throw new IOException(
                                        "The uploaded ledger could not be read. " +
                                                        "Please verify that the file is a valid CSV or Excel ledger.",
                                        e);
                }

                /*
                 * ============================================================
                 * IMPORTANT
                 * ============================================================
                 *
                 * Clean Excel-specific values here.
                 *
                 * Example:
                 *
                 * Excel cell:
                 *
                 * '119876543210
                 *
                 * becomes:
                 *
                 * 119876543210
                 *
                 * But:
                 *
                 * O'Connor
                 *
                 * remains:
                 *
                 * O'Connor
                 *
                 * Only a leading apostrophe is removed.
                 */
                rows = sanitizeParsedRows(rows);

                validateParsedRows(rows);

                /*
                 * ============================================================
                 * CREATE IMPORT BATCH
                 * ============================================================
                 */

                ImportBatch batch = ImportBatch.builder()
                                .organization(org)
                                .importedBy(importedBy)
                                .fileName(safeFilename)
                                .totalRows(rows.size())
                                .successCount(0)
                                .failureCount(0)
                                .status(STATUS_PROCESSING)
                                .build();

                batch = importBatchRepo.save(batch);

                final Long batchId = batch.getId();

                log.info(
                                "Legacy loan import started. " +
                                                "organizationId={}, batchId={}, filename={}, totalRows={}, importedBy={}",
                                org.getId(),
                                batchId,
                                safeFilename,
                                rows.size(),
                                importedBy.getId());

                Map<String, Borrower> sessionBorrowers = new HashMap<>();

                List<ImportRowResult> results = new ArrayList<>(
                                Math.min(
                                                rows.size(),
                                                MAX_RESULTS));

                long startedAt = System.currentTimeMillis();

                int rowNumber = 1;

                int success = 0;

                int failed = 0;

                /*
                 * ============================================================
                 * PROCESS EVERY ROW
                 * ============================================================
                 */
                for (Map<String, String> row : rows) {

                        rowNumber++;

                        ImportRowResult result;

                        try {

                                result = rowService.importRow(
                                                row,
                                                rowNumber,
                                                org,
                                                batchId,
                                                true,
                                                sessionBorrowers);

                                if (result == null) {

                                        result = ImportRowResult.builder()
                                                        .rowNumber(rowNumber)
                                                        .success(false)
                                                        .error(
                                                                        "The import service returned no result for this row.")
                                                        .build();
                                }

                        } catch (Exception e) {

                                log.error(
                                                "Unexpected exception while processing legacy loan import row. " +
                                                                "organizationId={}, batchId={}, rowNumber={}",
                                                org.getId(),
                                                batchId,
                                                rowNumber,
                                                e);

                                result = ImportRowResult.builder()
                                                .rowNumber(rowNumber)
                                                .success(false)
                                                .error(
                                                                "Unexpected error while processing this row. " +
                                                                                "The row was not imported.")
                                                .build();
                        }

                        results.add(result);

                        if (result.isSuccess()) {

                                success++;

                        } else {

                                failed++;
                        }
                }

                long durationMs = System.currentTimeMillis() - startedAt;

                /*
                 * ============================================================
                 * FINALIZE BATCH
                 * ============================================================
                 */

                String finalStatus;

                if (failed == 0) {

                        finalStatus = STATUS_COMPLETED;

                } else if (success == 0) {

                        finalStatus = STATUS_FAILED;

                } else {

                        finalStatus = STATUS_PARTIAL;
                }

                batch.setSuccessCount(success);

                batch.setFailureCount(failed);

                batch.setStatus(finalStatus);

                /*
                 * ============================================================
                 * STORE ROW RESULTS
                 * ============================================================
                 */

                try {

                        batch.setRowResults(
                                        objectMapper.writeValueAsString(
                                                        results));

                } catch (Exception e) {

                        log.error(
                                        "Unable to serialize legacy loan import row results. " +
                                                        "organizationId={}, batchId={}",
                                        org.getId(),
                                        batchId,
                                        e);

                        batch.setRowResults(null);
                }

                batch = importBatchRepo.save(batch);

                /*
                 * ============================================================
                 * AUDIT
                 * ============================================================
                 */

                try {

                        String auditMessage = "Imported " +
                                        success +
                                        "/" +
                                        results.size() +
                                        " rows from \"" +
                                        safeFilename +
                                        "\"";

                        if (failed > 0) {

                                auditMessage += " (" +
                                                failed +
                                                " row(s) failed — see batch detail)";
                        }

                        auditMessage += ". Batch status: " +
                                        finalStatus +
                                        ".";

                        auditService.log(
                                        org,
                                        importedBy,
                                        "LEGACY_LOANS_IMPORTED",
                                        "IMPORT_BATCH",
                                        batch.getId().toString(),
                                        auditMessage);

                } catch (Exception e) {

                        log.error(
                                        "Legacy loan import completed but audit logging failed. " +
                                                        "organizationId={}, batchId={}",
                                        org.getId(),
                                        batch.getId(),
                                        e);
                }

                log.info(
                                "Legacy loan import completed. " +
                                                "organizationId={}, batchId={}, filename={}, totalRows={}, " +
                                                "success={}, failed={}, status={}, durationMs={}",
                                org.getId(),
                                batch.getId(),
                                safeFilename,
                                results.size(),
                                success,
                                failed,
                                finalStatus,
                                durationMs);

                return batch;
        }

        /*
         * ================================================================
         * SANITIZE PARSED ROWS
         * ================================================================
         *
         * This is the important Excel compatibility layer.
         *
         * It handles:
         *
         * 1. Leading Excel apostrophe:
         *
         * '119876543210
         *
         * -> 119876543210
         *
         * 2. Leading/trailing whitespace.
         *
         * 3. UTF-8 BOM characters.
         *
         * 4. Non-breaking spaces.
         *
         * 5. Empty strings.
         *
         * 6. Excel-style text values.
         *
         * 7. Header normalization.
         *
         * We intentionally DO NOT remove apostrophes occurring inside
         * normal text.
         *
         * Example:
         *
         * O'Connor
         *
         * stays:
         *
         * O'Connor
         */
        private List<Map<String, String>> sanitizeParsedRows(
                        List<Map<String, String>> rows) {

                if (rows == null || rows.isEmpty()) {

                        return rows;
                }

                List<Map<String, String>> sanitizedRows = new ArrayList<>(
                                rows.size());

                for (Map<String, String> originalRow : rows) {

                        if (originalRow == null) {

                                sanitizedRows.add(null);

                                continue;
                        }

                        Map<String, String> sanitizedRow = new HashMap<>();

                        for (Map.Entry<String, String> entry : originalRow.entrySet()) {

                                String originalKey = entry.getKey();

                                String originalValue = entry.getValue();

                                String normalizedKey = sanitizeHeader(
                                                originalKey);

                                String normalizedValue = sanitizeCellValue(
                                                originalValue);

                                sanitizedRow.put(
                                                normalizedKey,
                                                normalizedValue);
                        }

                        sanitizedRows.add(
                                        sanitizedRow);
                }

                return sanitizedRows;
        }

        /*
         * ================================================================
         * SANITIZE HEADER
         * ================================================================
         */
        private String sanitizeHeader(
                        String value) {

                if (value == null) {

                        return "";
                }

                String result = normalizeExcelImportedValue(value)
                                .toLowerCase(Locale.ROOT);

                return result.trim();
        }

        /*
         * ================================================================
         * SANITIZE CELL VALUE
         * ================================================================
         */
        private String sanitizeCellValue(
                        String value) {

                if (value == null) {

                        return "";
                }

                return normalizeExcelImportedValue(value);
        }

        /*
         * ================================================================
         * REMOVE LEADING EXCEL APOSTROPHE
         * ================================================================
         *
         * Handles:
         *
         * '123456789
         *
         * -> 123456789
         *
         * Also handles whitespace before the apostrophe:
         *
         * '123456789
         *
         * -> 123456789
         *
         * Does NOT modify:
         *
         * O'Connor
         * Mary's
         * Borrower's loan
         */
        private String removeLeadingExcelApostrophe(
                        String value) {
                return normalizeExcelImportedValue(value);
        }

        private String normalizeExcelImportedValue(
                        String value) {

                if (value == null) {
                        return "";
                }

                String result = value
                                .replace("\uFEFF", "")
                                .replace("\u00A0", " ")
                                .trim();

                // Remove Excel/text-prefix quote markers, even when the source
                // contains double/smart quotes instead of a straight apostrophe.
                while (result.length() > 1 && isExcelTextMarker(result.charAt(0))) {
                        result = result.substring(1).trim();
                }

                // Remove accidental surrounding matching quotes.
                for (int i = 0; i < 2 && result.length() >= 2; i++) {
                        char first = result.charAt(0);
                        char last = result.charAt(result.length() - 1);
                        if (isQuote(first) && isQuote(last) && matchesQuote(first, last)) {
                                result = result.substring(1, result.length() - 1).trim();
                        } else {
                                break;
                        }
                }

                return result;
        }

        private boolean isExcelTextMarker(char c) {
                return c == '\'' || c == '\"' || c == '`'
                                || c == '‘' || c == '’' || c == '“' || c == '”';
        }

        private boolean isQuote(char c) {
                return c == '\'' || c == '\"'
                                || c == '‘' || c == '’' || c == '“' || c == '”';
        }

        private boolean matchesQuote(char first, char last) {
                return (first == '\'' && last == '\'')
                                || (first == '\"' && last == '\"')
                                || (first == '‘' && last == '’')
                                || (first == '“' && last == '”')
                                || (first == '’' && last == '’')
                                || (first == '”' && last == '”');
        }

        /*
         * ================================================================
         * VALIDATE IMPORT REQUEST
         * ================================================================
         */
        private void validateImportRequest(
                        String filename,
                        InputStream in,
                        Organization org,
                        User importedBy) {

                if (org == null) {

                        throw new IllegalArgumentException(
                                        "Organization is required for legacy loan import.");
                }

                if (org.getId() == null) {

                        throw new IllegalArgumentException(
                                        "Organization ID is required for legacy loan import.");
                }

                if (in == null) {

                        throw new IllegalArgumentException(
                                        "No ledger file was supplied.");
                }

                if (filename == null
                                || filename.isBlank()) {

                        throw new IllegalArgumentException(
                                        "A ledger filename is required.");
                }

                if (importedBy != null
                                && importedBy.getId() == null) {

                        throw new IllegalArgumentException(
                                        "The importing user must have a valid ID.");
                }
        }

        /**
         * Preload existing borrowers for preview in chunked bulk queries.
         * This removes the preview N+1 borrower lookup while leaving commit
         * row transactions/concurrency behavior unchanged.
         */
        private void preloadPreviewBorrowers(
                        List<Map<String, String>> rows,
                        Organization org,
                        Map<String, Borrower> sessionBorrowers) {

                if (rows == null || rows.isEmpty() || org == null || org.getId() == null) {
                        return;
                }

                HashSet<String> hashes = new HashSet<>();

                for (Map<String, String> row : rows) {
                        if (row == null) {
                                continue;
                        }

                        String nationalId = normalizePreviewNationalId(row.get("national_id"));

                        if (!nationalId.isBlank()) {
                                String hash = HmacIndexer.index(nationalId);
                                if (hash != null && !hash.isBlank()) {
                                        hashes.add(hash);
                                }
                        }
                }

                if (hashes.isEmpty()) {
                        return;
                }

                final int chunkSize = 500;
                List<String> allHashes = new ArrayList<>(hashes);

                for (int start = 0; start < allHashes.size(); start += chunkSize) {
                        int end = Math.min(start + chunkSize, allHashes.size());
                        List<Borrower> borrowers = borrowerRepo
                                        .findByOrganization_IdAndNationalIdHashIn(
                                                        org.getId(),
                                                        allHashes.subList(start, end));

                        for (Borrower borrower : borrowers) {
                                if (borrower == null) {
                                        continue;
                                }

                                String hash = borrower.getNationalIdHash();
                                if (hash != null && !hash.isBlank()) {
                                        sessionBorrowers.put(hash, borrower);
                                }
                        }
                }

                log.info(
                                "Legacy loan preview borrower preload complete. "
                                                + "organizationId={}, sourceRows={}, candidateHashes={}, matchedBorrowers={}",
                                org.getId(),
                                rows.size(),
                                hashes.size(),
                                sessionBorrowers.size());
        }

        private String normalizePreviewNationalId(String value) {
                if (value == null) {
                        return "";
                }

                String normalized = value
                                .replace("\uFEFF", "")
                                .trim();

                if (normalized.startsWith("'") || normalized.startsWith("’")
                                || normalized.startsWith("‘") || normalized.startsWith("`")) {
                        normalized = normalized.substring(1).trim();
                }

                normalized = normalized.replaceAll("\\s+", "");

                if (normalized.length() >= 2
                                && ((normalized.startsWith("\"") && normalized.endsWith("\""))
                                                || (normalized.startsWith("'") && normalized.endsWith("'")))) {
                        normalized = normalized.substring(1, normalized.length() - 1).trim();
                }

                return normalized;
        }

        /*
         * ================================================================
         * VALIDATE PARSED ROWS
         * ================================================================
         */
        private void validateParsedRows(
                        List<Map<String, String>> rows) {

                if (rows == null
                                || rows.isEmpty()) {

                        throw new IllegalArgumentException(
                                        "The uploaded ledger contains no data rows.");
                }

                if (rows.size() > MAX_IMPORT_ROWS) {

                        throw new IllegalArgumentException(
                                        "The uploaded ledger contains " +
                                                        rows.size() +
                                                        " rows. The maximum allowed per import is " +
                                                        MAX_IMPORT_ROWS +
                                                        ". Please split the ledger into smaller files.");
                }

                for (int i = 0; i < rows.size(); i++) {

                        Map<String, String> row = rows.get(i);

                        if (row == null) {

                                throw new IllegalArgumentException(
                                                "Import row " +
                                                                (i + 2) +
                                                                " is empty.");
                        }

                        if (row.isEmpty()) {

                                throw new IllegalArgumentException(
                                                "Import row " +
                                                                (i + 2) +
                                                                " contains no readable columns.");
                        }
                }
        }

        /*
         * ================================================================
         * FILENAME NORMALIZATION
         * ================================================================
         */
        private String normalizeFilename(
                        String filename) {

                String normalized = filename
                                .trim()
                                .replace(
                                                "\\",
                                                "_")
                                .replace(
                                                "/",
                                                "_");

                if (normalized.isBlank()) {

                        normalized = "legacy-loan-import.csv";
                }

                if (normalized.length() > MAX_FILENAME_LENGTH) {

                        normalized = normalized.substring(
                                        0,
                                        MAX_FILENAME_LENGTH);
                }

                return normalized;
        }

        /*
         * ================================================================
         * CSV TEMPLATE
         * ================================================================
         *
         * Synthetic example only.
         */
        public String buildCsvTemplate() {

                return String.join(
                                ",",
                                "national_id",
                                "first_name",
                                "last_name",
                                "email",
                                "phone",
                                "gender",
                                "marital_status",
                                "loan_type",
                                "amount",
                                "interest_rate",
                                "interest_rate_type",
                                "duration_months",
                                "start_date",
                                "status",
                                "total_paid",
                                "outstanding_balance",
                                "currency",
                                "loan_reference",
                                "notes")
                                + "\n"
                                +
                                String.join(
                                                ",",
                                                "SYNTHETIC-ID-0001",
                                                "Jean",
                                                "Uwimana",
                                                "",
                                                "0788000000",
                                                "Male",
                                                "Married",
                                                "PERSONAL",
                                                "500000",
                                                "10",
                                                "MONTHLY",
                                                "6",
                                                "2025-01-15",
                                                "ACTIVE",
                                                "150000",
                                                "",
                                                "RWF",
                                                "OLD-LEDGER-EXAMPLE-0042",
                                                "Synthetic example - replace with real historical data");
        }
}