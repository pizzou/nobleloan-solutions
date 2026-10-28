package com.patrick.fintech.loan_backend.util;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StreamingLedgerFileParserTest {

    private void appendMonthly(Sheet sheet, int rowIndex, String sourceNo,
            String nationalId, String start, int amount) {
        Row row = sheet.createRow(rowIndex);
        String[] columns = new String[30];
        java.util.Arrays.fill(columns, "0");
        columns[0] = sourceNo;
        columns[1] = "Example Borrower";
        columns[2] = nationalId;
        columns[3] = "250788123456";
        columns[4] = Integer.toString(amount);
        columns[5] = "20000";
        columns[6] = "0.05";
        columns[7] = "2";
        columns[14] = start;
        columns[15] = "2026-01-30";
        columns[21] = "0";
        columns[23] = "0";
        columns[25] = "0";
        columns[26] = "0";
        columns[27] = "0";
        columns[28] = "0";
        columns[29] = Integer.toString(amount);
        for (int col = 0; col < columns.length; col++) {
            row.createCell(col).setCellValue(columns[col]);
        }
    }

    private List<Map<String, String>> parse(XSSFWorkbook book) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        book.write(bytes);
        List<Map<String, String>> result = new ArrayList<>();
        StreamingLedgerFileParser.stream("loans.xlsx", new ByteArrayInputStream(bytes.toByteArray()),
                10000, (r, row) -> result.add(row));
        return result;
    }

    @Test
    void identicalSupportingSheetsProduceOnlyOneLoanCandidate() throws Exception {
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            appendMonthly(book.createSheet("PORTFOLIO 2025"), 2, "1", "1198880004126183", "2025-10-30", 5000000);
            appendMonthly(book.createSheet("Sheet1"), 0, "1", "1198880004126183", "2025-10-30", 5000000);
            List<Map<String, String>> result = parse(book);
            assertThat(result).hasSize(1);
            assertThat(result.get(0).get("loan_reference")).isBlank();
            assertThat(result.get(0).get("__source_sheet")).isEqualTo("PORTFOLIO 2025");
            assertThat(result.get(0).get("__duplicate_count")).isEqualTo("1");
            assertThat(result.get(0)).doesNotContainKey("__import_issue");
        }
    }

    @Test
    void conflictingCopiesAreBlockedBeforeAnyImportRowIsEmitted() throws Exception {
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            appendMonthly(book.createSheet("PORTFOLIO 2025"), 2, "1", "1198880004126183", "2025-10-30", 8000000);
            appendMonthly(book.createSheet("Sheet1"), 0, "1", "1198880004126183", "2025-10-30", 2000000);
            List<Map<String, String>> result = parse(book);
            assertThat(result).hasSize(1);
            assertThat(result.get(0).get("__import_issue"))
                    .contains("amount", "PORTFOLIO 2025", "Sheet1");
        }
    }

    @Test
    void separateLoanOnAnotherDateIsPreserved() throws Exception {
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            Sheet master = book.createSheet("PORTFOLIO 2025");
            appendMonthly(master, 2, "1", "1198880004126183", "2025-10-30", 5000000);
            appendMonthly(master, 3, "2", "1198880004126183", "2025-11-30", 5000000);
            assertThat(parse(book)).hasSize(2);
        }
    }

    @Test
    void conflictingNationalIdsAcrossSheetsAreBlocked() throws Exception {
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            appendMonthly(book.createSheet("PORTFOLIO 2025"), 2, "1", "1198880004126183", "2025-10-30", 5000000);
            appendMonthly(book.createSheet("Sheet1"), 0, "1", "1198880004126184", "2025-10-30", 5000000);
            List<Map<String, String>> result = parse(book);
            assertThat(result).hasSize(2);
            assertThat(result)
                    .allSatisfy(row -> assertThat(row.get("__import_issue")).contains("national_id discrepancy"));
        }
    }

    @Test
    void partialHistoricalPaymentsRequireVerifiedOldestUnpaidDueDate() throws Exception {
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            Sheet sheet = book.createSheet("PORTFOLIO 2025");
            appendMonthly(sheet, 2, "1", "1198880004126183", "2025-10-30", 5000000);
            sheet.getRow(2).getCell(25).setCellValue("250000"); // interest paid
            List<Map<String, String>> result = parse(book);
            assertThat(result).hasSize(1);
            assertThat(result.get(0).get("total_paid")).isEqualTo("250000.00");
            assertThat(result.get(0).get("__import_issue"))
                    .contains("column AE", "oldest unpaid");
            assertThat(result.get(0).get("next_due_date")).isBlank();

            sheet.getRow(2).createCell(30).setCellValue("2026-02-28");
            List<Map<String, String>> corrected = parse(book);
            assertThat(corrected).hasSize(1);
            assertThat(corrected.get(0)).doesNotContainKey("__import_issue");
            assertThat(corrected.get(0).get("next_due_date")).isEqualTo("2026-02-28");
        }
    }

    @Test
    void supportingOnlyLoanRequiresReviewWhenMasterExists() throws Exception {
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            appendMonthly(book.createSheet("PORTFOLIO 2025"), 2, "1", "1198880004126183", "2025-10-30", 5000000);
            // Different date ensures this is a distinct supporting-only loan,
            // not a conflicting national ID on the SAME borrower's loan date.
            // The latter safety rule is tested separately above.
            appendMonthly(book.createSheet("Sheet1"), 0, "2", "1198880004126184", "2025-11-30", 5000000);
            List<Map<String, String>> result = parse(book);
            assertThat(result).hasSize(2);
            assertThat(result.get(0)).doesNotContainKey("__import_issue");
            assertThat(result.get(1).get("__source_sheet")).isEqualTo("Sheet1");
            assertThat(result.get(1).get("__import_issue")).contains("supporting worksheet");
        }
    }
}
