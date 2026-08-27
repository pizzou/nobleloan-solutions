package com.patrick.fintech.loan_backend.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.patrick.fintech.loan_backend.util.LedgerFileParser;
import com.patrick.fintech.loan_backend.util.StreamingLedgerFileParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LedgerFileParserLegacyPortfolioTest {

    @Test
    void legacyPortfolioDoesNotMapHistoricalProcessingColumnsToApplicationFee() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("PORTFOLIO 2025");
            sheet.createRow(0).createCell(1).setCellValue("PORTFOLIO OCT 2025");
            Row header = sheet.createRow(1);
            header.createCell(0).setCellValue("NO");
            header.createCell(1).setCellValue("NAMES");
            header.createCell(2).setCellValue("ID NUMBER");
            header.createCell(3).setCellValue("TELEPHONE");
            header.createCell(4).setCellValue("AMOUNT DISBURSED");
            header.createCell(5).setCellValue("APPLICATION FEES");
            header.createCell(6).setCellValue("INTEREST RATE");
            header.createCell(7).setCellValue("PERIOD OF THE LOAN");

            Row row = sheet.createRow(2);
            row.createCell(0).setCellValue("48");
            row.createCell(1).setCellValue("BAMUREBE NADIA");
            row.createCell(2).setCellValue("1198270186191100");
            row.createCell(3).setCellValue("250788494092");
            row.createCell(4).setCellValue(800000d);
            row.createCell(5).setCellValue(20000d);
            row.createCell(6).setCellValue(0.05d);
            row.createCell(7).setCellValue(1d);
            row.createCell(21).setCellValue(0d);
            row.createCell(22).setCellValue(25000d); // old processing balance
            row.createCell(23).setCellValue(20000d); // management paid
            row.createCell(24).setCellValue(12500d); // old processing paid
            row.createCell(25).setCellValue(40000d);
            row.createCell(26).setCellValue(800000d);
            row.createCell(27).setCellValue(0d);
            row.createCell(28).setCellValue(0d);
            row.createCell(29).setCellValue(0d);

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            workbook.write(output);

            List<Map<String, String>> rows = LedgerFileParser.parse(
                    "NLS LOAN PORTFOLIO.xlsx",
                    new ByteArrayInputStream(output.toByteArray()));

            assertEquals(1, rows.size());
            Map<String, String> imported = rows.get(0);
            assertEquals("20000.00", imported.get("application_fee"));
            assertEquals("20000.00", imported.get("application_fee_paid"));
            assertEquals("0.00", imported.get("application_fee_outstanding"));
            assertEquals("800000.00", imported.get("principal_paid"));
            assertEquals("0.00", imported.get("principal_balance"));
        }
    }

    @Test
    void streamingPortfolioMatchesPreviewForLegacyProcessingColumnsAndPercentRate() throws Exception {
        byte[] workbookBytes;

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("PORTFOLIO 2025");
            sheet.createRow(0).createCell(1).setCellValue("PORTFOLIO OCT 2025");
            Row header = sheet.createRow(1);
            header.createCell(0).setCellValue("NO");
            header.createCell(1).setCellValue("NAMES");
            header.createCell(2).setCellValue("ID NUMBER");
            header.createCell(3).setCellValue("TELEPHONE");
            header.createCell(4).setCellValue("AMOUNT DISBURSED");
            header.createCell(5).setCellValue("APPLICATION FEES");
            header.createCell(6).setCellValue("INTEREST RATE");
            header.createCell(7).setCellValue("PERIOD OF THE LOAN");

            Row row = sheet.createRow(2);
            row.createCell(0).setCellValue("49");
            row.createCell(1).setCellValue("RUKUNDO EMMANUEL");
            row.createCell(2).setCellValue("1198270186191101");
            row.createCell(3).setCellValue("250788494093");
            row.createCell(4).setCellValue(1000000d);
            row.createCell(5).setCellValue(20000d);
            row.createCell(6).setCellValue("5%");
            row.createCell(7).setCellValue(1d);
            row.createCell(14).setCellValue("2025-01-15");
            row.createCell(21).setCellValue(50000d); // management outstanding
            row.createCell(22).setCellValue(25000d); // OLD processing balance
            row.createCell(23).setCellValue(10000d); // management paid
            row.createCell(24).setCellValue(12500d); // OLD processing paid
            row.createCell(25).setCellValue(40000d); // interest paid
            row.createCell(26).setCellValue(800000d); // principal paid
            row.createCell(27).setCellValue(0d);
            row.createCell(28).setCellValue(10000d); // interest outstanding
            row.createCell(29).setCellValue(200000d); // source principal balance

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            workbook.write(output);
            workbookBytes = output.toByteArray();
        }

        List<Map<String, String>> previewRows = LedgerFileParser.parse(
                "NLS LOAN PORTFOLIO.xlsx",
                new ByteArrayInputStream(workbookBytes));

        List<Map<String, String>> streamingRows = new ArrayList<>();
        StreamingLedgerFileParser.stream(
                "NLS LOAN PORTFOLIO.xlsx",
                new ByteArrayInputStream(workbookBytes),
                100,
                (rowNumber, row) -> streamingRows.add(row));

        assertEquals(1, previewRows.size());
        assertEquals(1, streamingRows.size());

        Map<String, String> preview = previewRows.get(0);
        Map<String, String> streaming = streamingRows.get(0);

        assertEquals("5", preview.get("interest_rate"));
        assertEquals("5", streaming.get("interest_rate"));
        assertEquals(preview.get("application_fee"), streaming.get("application_fee"));
        assertEquals("20000.00", streaming.get("application_fee"));
        assertEquals("20000.00", streaming.get("application_fee_paid"));
        assertEquals("0.00", streaming.get("application_fee_outstanding"));
        assertEquals("50.00", streaming.get("total_management_fee_balance"));
    }

    @Test
    void invalidStandardHeaderRateIsNotSilentlyReplacedWithDefault() throws Exception {
        byte[] workbookBytes;

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Loans");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("national_id");
            header.createCell(1).setCellValue("first_name");
            header.createCell(2).setCellValue("last_name");
            header.createCell(3).setCellValue("phone");
            header.createCell(4).setCellValue("amount");
            header.createCell(5).setCellValue("interest_rate");
            header.createCell(6).setCellValue("duration_months");
            header.createCell(7).setCellValue("start_date");

            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("1198270186191103");
            row.createCell(1).setCellValue("TEST");
            row.createCell(2).setCellValue("BORROWER");
            row.createCell(3).setCellValue("250788494095");
            row.createCell(4).setCellValue(800000d);
            row.createCell(5).setCellValue("not-a-rate");
            row.createCell(6).setCellValue(1d);
            row.createCell(7).setCellValue("2025-03-15");

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            workbook.write(output);
            workbookBytes = output.toByteArray();
        }

        List<Map<String, String>> rows = new ArrayList<>();
        StreamingLedgerFileParser.stream(
                "standard.xlsx",
                new ByteArrayInputStream(workbookBytes),
                100,
                (rowNumber, row) -> rows.add(row));

        assertEquals(1, rows.size());
        assertEquals("not-a-rate", rows.get(0).get("interest_rate"));
    }

    @Test
    void standardHeaderRateWithPercentSignIsNormalizedForStreamingCommitPath() throws Exception {
        byte[] workbookBytes;

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Loans");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("national_id");
            header.createCell(1).setCellValue("first_name");
            header.createCell(2).setCellValue("last_name");
            header.createCell(3).setCellValue("phone");
            header.createCell(4).setCellValue("amount");
            header.createCell(5).setCellValue("interest_rate");
            header.createCell(6).setCellValue("duration_months");
            header.createCell(7).setCellValue("start_date");

            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("1198270186191102");
            row.createCell(1).setCellValue("MUKANDAYISABYE");
            row.createCell(2).setCellValue("PASCASIE");
            row.createCell(3).setCellValue("250788494094");
            row.createCell(4).setCellValue(800000d);
            row.createCell(5).setCellValue("5%");
            row.createCell(6).setCellValue(1d);
            row.createCell(7).setCellValue("2025-02-15");

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            workbook.write(output);
            workbookBytes = output.toByteArray();
        }

        List<Map<String, String>> rows = new ArrayList<>();
        StreamingLedgerFileParser.stream(
                "standard.xlsx",
                new ByteArrayInputStream(workbookBytes),
                100,
                (rowNumber, row) -> rows.add(row));

        assertEquals(1, rows.size());
        assertEquals("5", rows.get(0).get("interest_rate"));
    }

}
