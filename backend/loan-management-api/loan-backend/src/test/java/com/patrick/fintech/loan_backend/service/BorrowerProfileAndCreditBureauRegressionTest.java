package com.patrick.fintech.loan_backend.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class BorrowerProfileAndCreditBureauRegressionTest {

    private static Path source(String relative) {
        List<Path> candidates = List.of(
                Path.of("src/main/java/com/patrick/fintech/loan_backend", relative),
                Path.of("backend/loan-management-api/loan-backend/src/main/java/com/patrick/fintech/loan_backend", relative));
        return candidates.stream().filter(Files::exists).findFirst()
                .orElseThrow(() -> new AssertionError("Source file not found: " + relative));
    }

    @Test
    void creditBureauConsumerExportWritesPlaceOfBirthToItsDeclaredColumn() throws Exception {
        String source = Files.readString(source("service/CreditBureauRegulatoryExportService.java"));
        assertTrue(source.contains("\"Place Of Birth\""),
                "Consumer export template must define the place-of-birth column.");
        assertTrue(source.contains("values[16] =\n                borrower.getPlaceOfBirth();"),
                "Borrower place of birth must populate consumer column index 16.");
        assertTrue(source.contains("values[15] =\n                date(borrower.getDateOfBirth());"),
                "Date of birth must remain in consumer column index 15.");
    }

    @Test
    void publicBorrowerProfileUpdateUsesOwnershipCheckAndStrictAllowlist() throws Exception {
        String source = Files.readString(source("controller/PublicController.java"));
        assertTrue(source.contains("@PutMapping(\"/applications/{reference}/profile\")"));
        assertTrue(source.contains("Loan loan = verifyOwnership(reference, phone);"),
                "Profile mutations must validate application reference and registered phone.");
        assertTrue(source.contains("\"placeOfBirth\""),
                "Borrowers must be able to populate place of birth for reporting.");
        assertTrue(source.contains("if (!allowed.contains(key))"),
                "Unknown and protected profile fields must be rejected.");
        assertFalse(source.contains("\"nationalId\", \"dateOfBirth\", \"gender\""),
                "Protected identity and contract fields must not be part of the self-service allowlist.");
        assertTrue(source.contains("BORROWER_SELF_SERVICE_PROFILE_UPDATED"),
                "Self-service profile mutations must be audited.");
        assertTrue(source.contains("Place of birth is already recorded"),
                "Self-service must not overwrite an existing verified place of birth.");
    }

    @Test
    void borrowerDocumentActionsFetchAndValidateBinaryContentBeforeShowingSuccess() throws Exception {
        List<Path> candidates = List.of(
                Path.of("frontend/loan-management-ui/services/api.ts"),
                Path.of("../frontend/loan-management-ui/services/api.ts"),
                Path.of("../../frontend/loan-management-ui/services/api.ts"),
                Path.of("../../../frontend/loan-management-ui/services/api.ts"),
                Path.of("../../../../frontend/loan-management-ui/services/api.ts"));
        Path api = candidates.stream().filter(Files::exists).findFirst().orElse(null);
        if (api == null) {
            return; // Backend-only build context; frontend checks run in frontend CI.
        }
        String apiSource = Files.readString(api);
        assertTrue(apiSource.contains("fetchDocumentContent"));
        assertTrue(apiSource.contains("contentType.includes(\"text/html\")"),
                "HTML error pages returned with HTTP 200 must not be treated as JSON.");
        assertTrue(apiSource.contains("responseType: \"blob\""));
        assertTrue(apiSource.contains("The server returned an error page instead of the document"));
        assertTrue(apiSource.contains("!isPublicRequest"),
                "A public borrower API 401 must not redirect the borrower to staff login.");

        List<Path> trackCandidates = List.of(
                Path.of("frontend/loan-management-ui/app/(site)/track/page.tsx"),
                Path.of("../frontend/loan-management-ui/app/(site)/track/page.tsx"),
                Path.of("../../frontend/loan-management-ui/app/(site)/track/page.tsx"),
                Path.of("../../../frontend/loan-management-ui/app/(site)/track/page.tsx"),
                Path.of("../../../../frontend/loan-management-ui/app/(site)/track/page.tsx"));
        Path track = trackCandidates.stream().filter(Files::exists).findFirst().orElseThrow();
        String trackSource = Files.readString(track);
        assertTrue(trackSource.contains("publicApi.fetchDocumentContent"));
        assertFalse(trackSource.contains("window.open(url, \"_blank\", \"noopener,noreferrer\")"),
                "The borrower portal must not directly navigate to an unchecked document response.");
    }


    @Test
    void allCreditBureauRecordExportsCarryPlaceOfBirthAndClassification() throws Exception {
        String dto = Files.readString(source("dto/regulatory/CreditBureauRecord.java"));
        String reporting = Files.readString(source("service/RegulatoryReportingService.java"));
        String controller = Files.readString(source("controller/CreditBureauExportController.java"));
        assertTrue(dto.contains("private String placeOfBirth;"));
        assertTrue(reporting.contains(".placeOfBirth(") && reporting.contains("borrower.getPlaceOfBirth()"),
                "The regulatory reporting DTO must receive the borrower place of birth.");
        assertTrue(reporting.contains(".repaymentClassification("),
                "The already-computed repayment classification must be present in the exported DTO.");
        assertTrue(controller.contains("\"Place of Birth\""));
        assertTrue(controller.contains("r.getPlaceOfBirth()"),
                "CSV, PDF and XLSX exports must include place of birth.");
        assertTrue(controller.contains("private static void appendCsvRow"),
                "CSV values must be escaped to prevent column corruption from commas, quotes or newlines.");
        assertTrue(controller.contains("new com.lowagie.text.Table(COLUMNS.size())"),
                "PDF table width must stay aligned with the expanded column list.");
        assertTrue(controller.contains("row.createCell(20).setCellValue(r.getCurrency()"),
                "XLSX currency must remain in the final column after adding place of birth.");
    }

}
