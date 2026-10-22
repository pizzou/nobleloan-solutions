package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.PaymentSchedule;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LoanContractTemplateRendererTest {

        @Test
        void generatedContractHasLogoInRecurringHeaderBorrowerFooterAndKinyarwandaMoneyWords() throws Exception {
                Borrower borrower = Borrower.builder()
                                .firstName("Jane")
                                .lastName("Uwimana")
                                .nationalId("1234567890123456")
                                .email("jane@example.rw")
                                .phone("0788000000")
                                .addressLine1("Gisozi")
                                .physicalAddressDistrict("Gasabo")
                                .physicalAddressSector("Gisozi")
                                .physicalAddressVillage("Village A")
                                .build();

                Loan loan = Loan.builder()
                                .referenceNumber("NLS-TEST-0001")
                                .borrower(borrower)
                                .amount(new BigDecimal("1000000.00"))
                                .interestRate(new BigDecimal("5.00"))
                                .interestRateType("MONTHLY")
                                .managementFeeRate(new BigDecimal("5.00"))
                                .applicationFeeRate(new BigDecimal("2.00"))
                                .applicationFee(new BigDecimal("20000.00"))
                                .penaltyRate(new BigDecimal("15.00"))
                                .durationMonths(3)
                                .currency("RWF")
                                .purpose("kwishyura amafaranga y'ishuri")
                                .totalRepayable(new BigDecimal("1260000.00"))
                                .build();

                List<PaymentSchedule> schedules = List.of(
                                PaymentSchedule.builder().installmentNumber(1).dueDate(LocalDate.of(2026, 11, 1))
                                                .installmentAmount(new BigDecimal("420000.00"))
                                                .principalAmount(new BigDecimal("320000.00"))
                                                .interestAmount(new BigDecimal("50000.00")).build(),
                                PaymentSchedule.builder().installmentNumber(2).dueDate(LocalDate.of(2026, 12, 1))
                                                .installmentAmount(new BigDecimal("420000.00"))
                                                .principalAmount(new BigDecimal("320000.00"))
                                                .interestAmount(new BigDecimal("50000.00")).build(),
                                PaymentSchedule.builder().installmentNumber(3).dueDate(LocalDate.of(2027, 1, 1))
                                                .installmentAmount(new BigDecimal("420000.00"))
                                                .principalAmount(new BigDecimal("360000.00"))
                                                .interestAmount(new BigDecimal("50000.00")).build());

                byte[] bytes = LoanContractTemplateRenderer.render(loan, schedules);
                assertNotNull(bytes);
                assertTrue(bytes.length > 10_000, "Generated DOCX should contain formatted text and the logo image.");

                try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
                        assertTrue(document.getProperties().getCoreProperties().getDescription()
                                        .contains(LoanContractTemplateRenderer.DOCUMENT_SCHEMA),
                                        "The generated DOCX description should contain the renderer schema marker.");
                        assertEquals(11906L,
                                        Long.parseLong(document.getDocument().getBody().getSectPr().getPgSz().getW()
                                                        .toString()),
                                        "Contracts should explicitly use A4 page width.");
                        assertFalse(document.getHeaderList().isEmpty(), "The document should have a recurring header.");
                        assertFalse(document.getHeaderList().get(0).getAllPictures().isEmpty(),
                                        "The Noble logo must be embedded in the document header, not just body text.");
                        assertTrue(document.getHeaderList().stream().flatMap(h -> h.getParagraphs().stream())
                                        .map(p -> p.getText())
                                        .anyMatch(text -> text.contains("BNR No. 7420/2025-00211/7000")));
                        assertTrue(document.getFooterList().stream().flatMap(f -> f.getParagraphs().stream())
                                        .map(p -> p.getText())
                                        .anyMatch(text -> text.contains("UGURIJWE: Jane Uwimana")));
                        assertTrue(document.getFooterList().stream().flatMap(f -> f.getParagraphs().stream())
                                        .map(p -> p.getText()).anyMatch(text -> text.contains("1234567890123456")));

                        String body = String.join("\n",
                                        document.getParagraphs().stream().map(p -> p.getText()).toList());
                        String tables = document.getTables().stream()
                                        .flatMap(t -> t.getRows().stream())
                                        .flatMap(r -> r.getTableCells().stream())
                                        .map(c -> c.getText())
                                        .reduce("", (left, right) -> left + "\n" + right);
                        String all = body + "\n" + tables;
                        assertTrue(all.contains("amafaranga y’u Rwanda miliyoni imwe (RWF 1,000,000)"), all);
                        assertTrue(all.contains("amafaranga y’u Rwanda ibihumbi makumyabiri (RWF 20,000)"), all);
                        assertTrue(all.contains("amafaranga y’u Rwanda ibihumbi magana ane makumyabiri (RWF 420,000)"),
                                        all);
                        assertTrue(document.getTables().get(0).getRow(0).isRepeatHeader(),
                                        "The repayment schedule header should repeat on later pages.");
                        assertTrue(all.contains("INGINGO YA 10 — GUKEMURA IMPAKA"), all);
                        assertTrue(all.contains("Inguzanyo yakoreshejwe mu bintu bitandukanye"), all);
                }
        }

        @Test
        void moneyWordsPreserveFractionalAmountsInsteadOfDroppingTheirWords() {
                assertEquals("amafaranga y’u Rwanda igihumbi kimwe magana abiri mirongo itatu na ane n'ibice mirongo itanu kuri ijana (RWF 1,234.50)",
                                LoanContractTemplateRenderer.money(new BigDecimal("1234.50"), "RWF"));
        }

        @Test
        void generationFailsClosedWhenPrincipalIsMissingOrNonPositive() {
                Borrower borrower = Borrower.builder().firstName("Jane").lastName("Uwimana").build();
                Loan missingPrincipal = Loan.builder().referenceNumber("NLS-TEST-0002").borrower(borrower).build();
                assertThrows(IllegalArgumentException.class,
                                () -> LoanContractTemplateRenderer.render(missingPrincipal, List.of()));
        }
}
