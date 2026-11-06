package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.DocumentType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BorrowerDocumentRequirementsTest {

    @Test
    void marriedEmployeeRequiresIdentityBankSelfieMarriageAndPayslip() {
        assertEquals(
                List.of(
                        DocumentType.NATIONAL_ID,
                        DocumentType.BANK_STATEMENT,
                        DocumentType.SELFIE,
                        DocumentType.MARRIAGE_CERTIFICATE,
                        DocumentType.PAYSLIP),
                BorrowerDocumentRequirements.forProfile("MARRIED", "EMPLOYED"));
    }

    @Test
    void singleBusinessOwnerRequiresSingleCertificateAndBusinessRegistration() {
        assertEquals(
                List.of(
                        DocumentType.NATIONAL_ID,
                        DocumentType.BANK_STATEMENT,
                        DocumentType.SELFIE,
                        DocumentType.SINGLE_CERTIFICATE,
                        DocumentType.BUSINESS_REGISTRATION),
                BorrowerDocumentRequirements.forProfile("SINGLE", "BUSINESS_OWNER"));
    }

    @Test
    void nonSalariedNonBusinessProfileDoesNotInventEmploymentEvidence() {
        assertEquals(
                List.of(
                        DocumentType.NATIONAL_ID,
                        DocumentType.BANK_STATEMENT,
                        DocumentType.SELFIE,
                        DocumentType.SINGLE_CERTIFICATE),
                BorrowerDocumentRequirements.forProfile("SINGLE", "FARMER"));
    }

    @Test
    void unsupportedMaritalStatusFailsClosed() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BorrowerDocumentRequirements.forProfile("WIDOWED", "EMPLOYED"));
    }
}
