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
import java.time.LocalDate;

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
    void historicalRestructureMarkerDoesNotBecomeNextDueDate() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("PORTFOLIO OCT 2025");
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
            row.createCell(0).setCellValue("8");
            row.createCell(1).setCellValue("TEST RESTRUCTURED BORROWER");
            row.createCell(2).setCellValue("1198270186191100");
            row.createCell(3).setCellValue("250788494092");
            row.createCell(4).setCellValue(800000d);
            row.createCell(5).setCellValue(16000d);
            row.createCell(6).setCellValue("5%");
            row.createCell(7).setCellValue(1d);
            row.createCell(14).setCellValue("2025-01-01");
            row.createCell(15).setCellValue("loan restructure");
            row.createCell(21).setCellValue(0d);
            row.createCell(22).setCellValue(25000d);
            row.createCell(23).setCellValue(0d);
            row.createCell(24).setCellValue(12500d);
            row.createCell(25).setCellValue(0d);
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
            assertEquals("5", imported.get("interest_rate"));
            assertEquals("", imported.get("next_due_date"));
            assertEquals("RESTRUCTURED", imported.get("status"));
            assertEquals("16000.00", imported.get("application_fee"));
            assertEquals("0.00", imported.get("application_fee_outstanding"));
        }
    }

    @Test
    void historicalExcelMonthNameDatesAreParsedInMonthlyPortfolio() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("PORTFOLIO 2025");
            sheet.createRow(0).createCell(1).setCellValue("PORTFOLIO JUN 2026");
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
            row.createCell(0).setCellValue("78");
            row.createCell(1).setCellValue("NDAYISHIMIYE FLORIDE");
            row.createCell(2).setCellValue("1198570047446095");
            row.createCell(3).setCellValue("250788424972");
            row.createCell(4).setCellValue(2000000d);
            row.createCell(5).setCellValue(20000d);
            row.createCell(6).setCellValue(0.05d);
            row.createCell(7).setCellValue(4d);
            var dateCell = row.createCell(14);
            dateCell.setCellValue(java.util.Date.from(
                    LocalDate.of(2026, 5, 29).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()));
            dateCell.setCellStyle(workbook.createCellStyle());
            dateCell.getCellStyle().setDataFormat(workbook.createDataFormat().getFormat("dd-mmm-yy"));
            row.createCell(15).setCellValue("loan restructure");
            row.createCell(21).setCellValue(100000d);
            row.createCell(23).setCellValue(100000d);
            row.createCell(25).setCellValue(200000d);
            row.createCell(26).setCellValue(0d);
            row.createCell(27).setCellValue(0d);
            row.createCell(28).setCellValue(200000d);
            row.createCell(29).setCellValue(2000000d);

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            workbook.write(output);

            List<Map<String, String>> rows = LedgerFileParser.parse(
                    "NLS LOAN PORTFOLIO.xlsx",
                    new ByteArrayInputStream(output.toByteArray()));

            assertEquals(1, rows.size());
            assertEquals("2026-05-29", rows.get(0).get("start_date"));
            assertEquals("", rows.get(0).get("next_due_date"));
        }
    }

    @Test
    void streamingParserAcceptsMonthNameDateAndRestructureMarker() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("PORTFOLIO 2025");
            sheet.createRow(0).createCell(1).setCellValue("PORTFOLIO JUN 2026");
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
            row.createCell(0).setCellValue("78");
            row.createCell(1).setCellValue("NDAYISHIMIYE FLORIDE");
            row.createCell(2).setCellValue("1198570047446095");
            row.createCell(3).setCellValue("250788424972");
            row.createCell(4).setCellValue(2000000d);
            row.createCell(5).setCellValue(20000d);
            row.createCell(6).setCellValue(0.05d);
            row.createCell(7).setCellValue(4d);
            var dateCell = row.createCell(14);
            dateCell.setCellValue(java.util.Date.from(
                    LocalDate.of(2026, 5, 29).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()));
            dateCell.setCellStyle(workbook.createCellStyle());
            dateCell.getCellStyle().setDataFormat(workbook.createDataFormat().getFormat("dd-mmm-yy"));
            row.createCell(15).setCellValue("loan restructure");
            row.createCell(21).setCellValue(100000d);
            row.createCell(23).setCellValue(100000d);
            row.createCell(25).setCellValue(200000d);
            row.createCell(26).setCellValue(0d);
            row.createCell(27).setCellValue(0d);
            row.createCell(28).setCellValue(200000d);
            row.createCell(29).setCellValue(2000000d);

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            workbook.write(output);

            List<Map<String, String>> parsed = new java.util.ArrayList<>();
            StreamingLedgerFileParser.stream(
                    "NLS LOAN PORTFOLIO.xlsx",
                    new ByteArrayInputStream(output.toByteArray()),
                    100,
                    (rowNumber, mapped) -> parsed.add(mapped));

            assertEquals(1, parsed.size());
            assertEquals("2026-05-29", parsed.get(0).get("start_date"));
            assertEquals("", parsed.get(0).get("next_due_date"));
            assertEquals("RESTRUCTURED", parsed.get(0).get("status"));
        }
    }

}
