package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.PaymentSchedule;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

class LoanContractTemplateRendererTest {

    private static Loan sampleLoan() {
        Borrower borrower = new Borrower();
        borrower.setFirstName("Jean");
        borrower.setLastName("UWIMANA");
        borrower.setNationalId("1990123456789012");
        borrower.setPhone("0780000000");
        borrower.setEmail("sample@example.test");
        borrower.setAddress("Kigali");

        Loan loan = new Loan();
        loan.setBorrower(borrower);
        loan.setReferenceNumber("NLS-CONTRACT-REGRESSION");
        loan.setAmount(new BigDecimal("10000000.00"));
        loan.setCurrency("RWF");
        loan.setPurpose("Kugura imodoka");
        loan.setDurationMonths(4);
        loan.setInterestRate(new BigDecimal("5"));
        loan.setInterestRateType("MONTHLY");
        loan.setManagementFeeRate(new BigDecimal("5"));
        loan.setApplicationFeeRate(new BigDecimal("2"));
        loan.setPenaltyRate(new BigDecimal("15"));
        loan.setTotalRepayable(new BigDecimal("12500000.00"));
        return loan;
    }

    private static List<PaymentSchedule> sampleSchedule() {
        List<PaymentSchedule> lines = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            PaymentSchedule row = new PaymentSchedule();
            row.setInstallmentNumber(i);
            row.setDueDate(LocalDate.of(2026, 11, 22).plusMonths(i - 1));
            row.setPrincipalAmount(new BigDecimal("2500000.00"));
            row.setInterestAmount(BigDecimal.valueOf(125000L * (5 - i)));
            row.setManagementFeeAmount(BigDecimal.valueOf(125000L * (5 - i)));
            row.setInstallmentAmount(BigDecimal.valueOf(2500000L + 250000L * (5 - i)));
            lines.add(row);
        }
        return lines;
    }

    private static String bodyText(byte[] bytes) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            StringBuilder result = new StringBuilder();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                result.append(paragraph.getText()).append('\n');
            }
            return result.toString();
        }
    }

    @Test
    void includesOriginalLogoInRepeatingHeaderAndWordsInBody() throws Exception {
        byte[] data = LoanContractTemplateRenderer.render(sampleLoan(), sampleSchedule());
        boolean hasHeader = false;
        boolean matchingLogo = false;
        byte[] approvedLogo;
        try (InputStream logo = getClass().getResourceAsStream("/branding/nls-logo.png")) {
            assertNotNull(logo, "Approved logo must be packaged");
            approvedLogo = logo.readAllBytes();
        }
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().matches("word/header\\d+\\.xml")) {
                    hasHeader = true;
                    assertTrue(new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                            .contains("<w:drawing>"), "The logo must appear in the repeating header");
                }
                if (entry.getName().startsWith("word/media/")
                        && Arrays.equals(approvedLogo, zip.readAllBytes())) {
                    matchingLogo = true;
                }
            }
        }
        assertTrue(hasHeader, "Missing repeating Word header");
        assertTrue(matchingLogo, "The approved logo is not embedded exactly");

        String body = bodyText(data);
        assertTrue(body.contains("miliyoni icumi"), "RWF 10,000,000 must be written in words");
        assertTrue(body.contains("RWF 10,000,000"));
        assertTrue(body.contains("RWF 200,000"), "One-time 2% fee must be RWF 200,000");
        assertTrue(body.contains("15%"), "Penalty rate must match the loan record");
        assertTrue(body.contains("BANK OF KIGALI"));
        for (int i = 1; i <= 10; i++) {
            assertTrue(body.contains("INGINGO YA " + i), "Missing original article " + i);
        }
        assertFalse(body.contains("[GAHUNDA YO KWISHYURA NTIRABONEKA"));
        assertFalse(body.contains("[KUZUZA]"));
    }

    @Test
    void missingScheduleIsClearlyFlaggedNotFabricated() throws Exception {
        String body = bodyText(LoanContractTemplateRenderer.render(sampleLoan(), List.of()));
        assertTrue(body.contains("GAHUNDA YO KWISHYURA NTIRABONEKA"));
        assertTrue(body.contains("NTIMWOHEREZE AMASEZERANO"));
    }

    @Test
    void stableComparisonIgnoresOnlyVolatileCoreProperties() throws Exception {
        byte[] a = LoanContractTemplateRenderer.render(sampleLoan(), sampleSchedule());
        byte[] b = LoanContractTemplateRenderer.render(sampleLoan(), sampleSchedule());
        Method method = LoanContractService.class
                .getDeclaredMethod("contractContentFingerprint", byte[].class);
        method.setAccessible(true);
        assertEquals(method.invoke(null, (Object) a), method.invoke(null, (Object) b));
    }

    @Test
    void articleFiveRetainsEveryApprovedConditionAsProfessionalTable() throws Exception {
        byte[] data = LoanContractTemplateRenderer.render(sampleLoan(), sampleSchedule());
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(data))) {
            org.apache.poi.xwpf.usermodel.XWPFTable articleFive = doc.getTables().stream()
                    .filter(table -> table.getNumberOfRows() == 5)
                    .filter(table -> table.getRow(0).getCell(0).getText().contains("a)"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Missing approved Article 5 table"));
            assertEquals(5, articleFive.getNumberOfRows());
            assertTrue(articleFive.getRow(0).getCell(1).getText().contains("inguzanyo yakoreshejwe"));
            assertTrue(articleFive.getRow(1).getCell(1).getText().contains("iminsi itatu"));
            assertTrue(articleFive.getRow(2).getCell(1).getText().contains("kubeshya"));
            assertTrue(articleFive.getRow(3).getCell(1).getText().contains("ingwate"));
            assertTrue(articleFive.getRow(4).getCell(1).getText().contains("Guhomba"));
        }
    }

    @Test
    void signatureSectionUsesNoTableAndReservesSpaceForNotaryStamp() throws Exception {
        byte[] docx = LoanContractTemplateRenderer.render(sampleLoan(), sampleSchedule());
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
            String text = doc.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .collect(java.util.stream.Collectors.joining("\n"));
            assertTrue(text.contains("IMIKONO Y’ABAGIRANYE AMASEZERANO"));
            assertTrue(text.contains("UGURIJWE / BORROWER"));
            assertTrue(text.contains("UGURIJE / LENDER"));
            assertTrue(text.contains("AHA HAGENEWE KASHE N’UMUKONO BYA NOTERI"));
            assertTrue(text.contains("TUYISENGE Annuarite"));
            assertTrue(
                    doc.getTables().stream()
                            .noneMatch(table -> table.getRows().stream()
                                    .anyMatch(row -> row.getTableCells().stream()
                                            .anyMatch(cell -> cell.getText().contains("UGURIJWE / BORROWER")
                                                    || cell.getText().contains("UGURIJE / LENDER")))),
                    "Signature blocks must not be enclosed inside a Word table");
        }
    }

    @Test
    void monetaryFractionsAreExpressedInKinyarwandaNotEnglish() throws Exception {
        Method method = LoanContractTemplateRenderer.class
                .getDeclaredMethod("money", BigDecimal.class, String.class);
        method.setAccessible(true);
        String words = (String) method.invoke(null, new BigDecimal("1234.50"), "RWF");
        assertTrue(words.contains("n’amasente"));
        assertTrue(words.contains("RWF 1,234.50"));
        assertFalse(words.contains("including cents"));
    }

}
