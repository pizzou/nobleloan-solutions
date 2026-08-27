package com.patrick.fintech.loan_backend.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.patrick.fintech.loan_backend.util.LedgerFileParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

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
}
