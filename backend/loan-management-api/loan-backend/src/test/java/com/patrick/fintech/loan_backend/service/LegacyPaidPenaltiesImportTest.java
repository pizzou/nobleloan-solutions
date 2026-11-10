package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.util.LedgerFileParser;
import com.patrick.fintech.loan_backend.util.StreamingLedgerFileParser;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LegacyPaidPenaltiesImportTest {

    private byte[] portfolio(double paidPrincipal, double outstandingPrincipal,
                             double paidPenalty) throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("PORTFOLIO 2025");
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
            row.createCell(0).setCellValue("31");
            row.createCell(1).setCellValue("TEST BORROWER");
            row.createCell(2).setCellValue("1198880004126183");
            row.createCell(3).setCellValue("250788800740");
            row.createCell(4).setCellValue(15000000d);
            row.createCell(5).setCellValue(300000d);
            row.createCell(6).setCellValue(0.05d);
            row.createCell(7).setCellValue(1d);
            row.createCell(14).setCellValue("2025-10-01");
            row.createCell(15).setCellValue("2025-11-01");
            row.createCell(21).setCellValue(0d); // management outstanding
            row.createCell(23).setCellValue(0d); // management paid
            row.createCell(25).setCellValue(4500000d); // interest paid
            row.createCell(26).setCellValue(paidPrincipal);
            row.createCell(27).setCellValue(paidPenalty);
            row.createCell(28).setCellValue(0d); // interest outstanding
            row.createCell(29).setCellValue(outstandingPrincipal);
            row.createCell(30).setCellValue(""); // verified oldest unpaid due date
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            wb.write(bytes);
            return bytes.toByteArray();
        }
    }

    private void assertPaidPenalty(Map<String,String> loan, String status) {
        assertEquals("825000.00", loan.get("penalties_assessed"));
        assertEquals("825000.00", loan.get("penalties_paid"));
        assertEquals("20325000.00", loan.get("total_repayable"));
        assertEquals(status, loan.get("status"));
    }

    @Test
    void settledPenaltyDoesNotCreateOutstandingInstallmentForEitherParser() throws Exception {
        byte[] bytes = portfolio(15000000d, 0d, 825000d);
        List<Map<String,String>> normal = LedgerFileParser.parse(
                "NLS LOAN PORTFOLIO.xlsx",new ByteArrayInputStream(bytes));
        assertEquals(1, normal.size());
        assertPaidPenalty(normal.get(0), "PAID");
        assertEquals("20325000.00", normal.get(0).get("total_paid"));

        List<Map<String,String>> streaming = new ArrayList<>();
        StreamingLedgerFileParser.stream("NLS LOAN PORTFOLIO.xlsx",
                new ByteArrayInputStream(bytes),100,(n,row)->streaming.add(row));
        assertEquals(1, streaming.size());
        assertPaidPenalty(streaming.get(0), "PAID");
        assertEquals("20325000.00", streaming.get(0).get("total_paid"));
        assertFalse(streaming.get(0).containsKey("__import_issue"),
                "Already-paid penalties must not force a fictitious column AC date");
    }

    @Test
    void partiallyPaidPrincipalStillRequiresVerifiedOldestUnpaidDate() throws Exception {
        byte[] bytes = portfolio(14000000d,1000000d,825000d);
        List<Map<String,String>> streaming = new ArrayList<>();
        StreamingLedgerFileParser.stream("NLS LOAN PORTFOLIO.xlsx",
                new ByteArrayInputStream(bytes),100,(n,row)->streaming.add(row));
        assertEquals(1, streaming.size());
        assertEquals("ACTIVE",streaming.get(0).get("status"));
        assertEquals("825000.00",streaming.get(0).get("penalties_paid"));
        assertNotNull(streaming.get(0).get("__import_issue"),
                "Unpaid principal must still require a verified installment date");
    }
}
