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
}
