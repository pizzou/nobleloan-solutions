package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.dto.regulatory.CreditBureauRecord;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.CreditBureauRegulatoryExportService;
import com.patrick.fintech.loan_backend.service.RegulatoryReportingService;
import com.patrick.fintech.loan_backend.service.ReportExportService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/regulatory/credit-bureau")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','AUDITOR')")
public class CreditBureauExportController {

    private final RegulatoryReportingService reportingService;
    private final ReportExportService exportService;
    private final AuditService auditService;
    private final CreditBureauRegulatoryExportService creditBureauRegulatoryExportService;
    private final CurrentUserUtil currentUserUtil;

    private static final List<String> COLUMNS = List.of(
            "Borrower ID", "National ID", "Full Name", "Date of Birth", "Place of Birth", "Gender", "Phone",
            "Loan Number", "Loan Type", "Loan Status", "Repayment Classification", "Loan Amount",
            "Outstanding Balance", "Days Past Due", "Credit Score", "Date Opened", "Last Payment",
            "Maturity Date", "Date Closed", "Branch", "Currency");

    @GetMapping("/preview")
    public ResponseEntity<ApiResponse<List<CreditBureauRecord>>> preview(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        Long organizationId = currentUserUtil.getCurrentOrganizationId();
        if (organizationId == null) {
            throw new IllegalStateException("Current user is not associated with an organization.");
        }

        LocalDate fromDate = parseDate(from);
        LocalDate toDate = parseDate(to);
        validateDateRange(fromDate, toDate);

        List<CreditBureauRecord> records = reportingService.buildCreditBureauExport(
                organizationId, branchId, fromDate, toDate);

        auditService.log(
                currentUserUtil.getCurrentUser().getOrganization(),
                currentUserUtil.getCurrentUser(),
                "VIEW", "CreditBureauExport", "preview",
                "Previewed Credit Bureau report | Records: " + records.size(),
                null, null, "Regulatory Reporting");

        return ResponseEntity.ok(ApiResponse.ok(records));
    }

    @GetMapping(value = "/download", produces = {
            MediaType.APPLICATION_OCTET_STREAM_VALUE,
            MediaType.APPLICATION_PDF_VALUE,
            MediaType.TEXT_PLAIN_VALUE,
            "text/csv",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    })
    public ResponseEntity<byte[]> export(
            @RequestParam(defaultValue = "xlsx") String format,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        Long organizationId = currentUserUtil.getCurrentOrganizationId();
        if (organizationId == null) {
            throw new IllegalStateException("Current user is not associated with an organization.");
        }

        LocalDate fromDate = parseDate(from);
        LocalDate toDate = parseDate(to);
        validateDateRange(fromDate, toDate);

        if ("xlsx".equalsIgnoreCase(format)) {
            byte[] fileBytes = creditBureauRegulatoryExportService.export(
                    organizationId,
                    branchId,
                    null,
                    fromDate,
                    toDate);

            String fileName = "credit_bureau_" + LocalDate.now() + ".xlsx";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDispositionFormData("attachment", fileName);
            headers.setCacheControl("no-cache, no-store, must-revalidate");
            headers.setPragma("no-cache");
            headers.setContentLength(fileBytes.length);

            auditService.log(
                    currentUserUtil.getCurrentUser().getOrganization(),
                    currentUserUtil.getCurrentUser(),
                    "EXPORT",
                    "CreditBureauExport",
                    "export",
                    "Exported native CRB regulatory workbook",
                    null,
                    null,
                    "Regulatory Reporting");

            return ResponseEntity.ok().headers(headers).body(fileBytes);
        }

        List<CreditBureauRecord> records = reportingService.buildCreditBureauExport(
                organizationId, branchId, fromDate, toDate);

        byte[] fileBytes;
        String fileName = "credit_bureau_report_" + LocalDate.now();
        HttpHeaders headers = new HttpHeaders();

        try {
            if ("csv".equalsIgnoreCase(format)) {
                // ========================================================
                // NATIVE CSV ENGINE
                // ========================================================
                StringBuilder csv = new StringBuilder();
                csv.append(String.join(",", COLUMNS)).append("\n");

                if (records != null) {
                    for (CreditBureauRecord r : records) {
                        appendCsvRow(csv, List.of(
                                csvValue(r.getBorrowerId()),
                                csvValue(r.getNationalId()),
                                csvValue(r.getFullName()),
                                csvValue(r.getDateOfBirth()),
                                csvValue(r.getPlaceOfBirth()),
                                csvValue(r.getGender()),
                                csvValue(r.getPhone()),
                                csvValue(r.getLoanNumber()),
                                csvValue(r.getLoanType()),
                                csvValue(r.getLoanStatus()),
                                csvValue(r.getRepaymentClassification()),
                                csvValue(r.getLoanAmountDecimal()),
                                csvValue(r.getOutstandingBalanceDecimal()),
                                csvValue(r.getDaysPastDue()),
                                csvValue(r.getCreditScore()),
                                csvValue(r.getDateOpened()),
                                csvValue(r.getLastPaymentDate()),
                                csvValue(r.getMaturityDate()),
                                csvValue(r.getDateClosed()),
                                csvValue(r.getBranchName()),
                                csvValue(r.getCurrency())));

                    }
                }
                fileBytes = csv.toString().getBytes(StandardCharsets.UTF_8);
                headers.setContentType(MediaType.parseMediaType("text/csv"));
                headers.setContentDispositionFormData("attachment", fileName + ".csv");

            } else if ("pdf".equalsIgnoreCase(format)) {
                // ========================================================
                // NATIVE FIXED PDF ENGINE (OpenPDF - Now supports all 21 columns)
                // ========================================================
                try (java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
                    // Set Page Size to A3 Rotate (Landscape) to accommodate all 21 columns
                    // comfortably
                    com.lowagie.text.Document document = new com.lowagie.text.Document(
                            com.lowagie.text.PageSize.A3.rotate());
                    com.lowagie.text.pdf.PdfWriter.getInstance(document, out);
                    document.open();

                    com.lowagie.text.Paragraph title = new com.lowagie.text.Paragraph(
                            "CREDIT BUREAU REGULATORY REPORT\nGenerated: " + LocalDate.now() + "\n\n");
                    title.setAlignment(com.lowagie.text.Element.ALIGN_CENTER);
                    document.add(title);

                    // Keep the table width aligned with the report column definition.
                    com.lowagie.text.Table table = new com.lowagie.text.Table(COLUMNS.size());
                    table.setPadding(3);
                    table.setWidth(100);

                    // Add all 21 headers dynamically from your static COLUMNS configuration
                    for (String columnName : COLUMNS) {
                        table.addCell(columnName);
                    }

                    // Feed Data Rows cleanly with complete properties mapping
                    if (records != null) {
                        for (CreditBureauRecord r : records) {
                            table.addCell(r.getBorrowerId() != null ? String.valueOf(r.getBorrowerId()) : "");
                            table.addCell(r.getNationalId() != null ? r.getNationalId() : "");
                            table.addCell(r.getFullName() != null ? r.getFullName() : "");
                            table.addCell(r.getDateOfBirth() != null ? r.getDateOfBirth().toString() : "");
                            table.addCell(r.getPlaceOfBirth() != null ? r.getPlaceOfBirth() : "");
                            table.addCell(r.getGender() != null ? r.getGender() : "");
                            table.addCell(r.getPhone() != null ? r.getPhone() : "");
                            table.addCell(r.getLoanNumber() != null ? r.getLoanNumber() : "");
                            table.addCell(r.getLoanType() != null ? r.getLoanType() : "");
                            table.addCell(r.getLoanStatus() != null ? r.getLoanStatus() : "");
                            table.addCell(r.getRepaymentClassification() != null ? r.getRepaymentClassification() : "");
                            table.addCell(r.getLoanAmountDecimal() != null ? r.getLoanAmountDecimal().toPlainString() : "0.00");
                            table.addCell(r.getOutstandingBalanceDecimal() != null ? r.getOutstandingBalanceDecimal().toPlainString() : "0.00");
                            table.addCell(String.valueOf(r.getDaysPastDue()));
                            table.addCell(r.getCreditScore() != null ? String.valueOf(r.getCreditScore()) : "");
                            table.addCell(r.getDateOpened() != null ? r.getDateOpened().toString() : "");
                            table.addCell(r.getLastPaymentDate() != null ? r.getLastPaymentDate().toString() : "");
                            // Add trailing cells to finish out the OpenPDF table matrix
                            table.addCell(r.getMaturityDate() != null ? r.getMaturityDate().toString() : "");
                            table.addCell(r.getDateClosed() != null ? r.getDateClosed().toString() : "");
                            table.addCell(r.getBranchName() != null ? r.getBranchName() : "");
                            table.addCell(r.getCurrency() != null ? r.getCurrency() : "");
                        }
                    }

                    // Append table to PDF layout and close stream buffers safely
                    document.add(table);
                    document.close();
                    fileBytes = out.toByteArray();
                }

                headers.setContentType(MediaType.APPLICATION_PDF);
                headers.setContentDispositionFormData("attachment", fileName + ".pdf");

            } else {
                // ========================================================
                // NATIVE EXCEL (.XLSX) ENGINE
                // ========================================================
                try (org.apache.poi.ss.usermodel.Workbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
                        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {

                    org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet("Credit Bureau Report");
                    org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);

                    for (int i = 0; i < COLUMNS.size(); i++) {
                        headerRow.createCell(i).setCellValue(COLUMNS.get(i));
                    }

                    int rowIdx = 1;
                    if (records != null) {
                        for (CreditBureauRecord r : records) {
                            org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);

                            // Map all 21 columns cleanly to Excel cell indexes row by row
                            row.createCell(0).setCellValue(r.getBorrowerId() != null ? r.getBorrowerId() : 0L);
                            row.createCell(1).setCellValue(r.getNationalId() != null ? r.getNationalId() : "");
                            row.createCell(2).setCellValue(r.getFullName() != null ? r.getFullName() : "");
                            row.createCell(3)
                                    .setCellValue(r.getDateOfBirth() != null ? r.getDateOfBirth().toString() : "");
                            row.createCell(4).setCellValue(r.getPlaceOfBirth() != null ? r.getPlaceOfBirth() : "");
                            row.createCell(5).setCellValue(r.getGender() != null ? r.getGender() : "");
                            row.createCell(6).setCellValue(r.getPhone() != null ? r.getPhone() : "");
                            row.createCell(7).setCellValue(r.getLoanNumber() != null ? r.getLoanNumber() : "");
                            row.createCell(8).setCellValue(r.getLoanType() != null ? r.getLoanType() : "");
                            row.createCell(9).setCellValue(r.getLoanStatus() != null ? r.getLoanStatus() : "");
                            row.createCell(10).setCellValue(
                                    r.getRepaymentClassification() != null ? r.getRepaymentClassification() : "");
                            row.createCell(11).setCellValue(r.getLoanAmountDecimal() != null ? r.getLoanAmountDecimal().doubleValue() : 0d);
                            row.createCell(12).setCellValue(r.getOutstandingBalanceDecimal() != null ? r.getOutstandingBalanceDecimal().doubleValue() : 0d);
                            row.createCell(13).setCellValue(r.getDaysPastDue());
                            row.createCell(14).setCellValue(r.getCreditScore() != null ? r.getCreditScore() : 0);
                            row.createCell(15)
                                    .setCellValue(r.getDateOpened() != null ? r.getDateOpened().toString() : "");
                            row.createCell(16).setCellValue(
                                    r.getLastPaymentDate() != null ? r.getLastPaymentDate().toString() : "");
                            row.createCell(17)
                                    .setCellValue(r.getMaturityDate() != null ? r.getMaturityDate().toString() : "");
                            row.createCell(18)
                                    .setCellValue(r.getDateClosed() != null ? r.getDateClosed().toString() : "");
                            row.createCell(19).setCellValue(r.getBranchName() != null ? r.getBranchName() : "");
                            row.createCell(20).setCellValue(r.getCurrency() != null ? r.getCurrency() : "");
                        }
                    }

                    wb.write(out);
                    fileBytes = out.toByteArray();
                }

                headers.setContentType(
                        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
                headers.setContentDispositionFormData("attachment", fileName + ".xlsx");
            }

        } catch (Exception e) {
            throw new IllegalStateException("Failed to securely generate raw binary export payload", e);
        }

        // Apply strict corporate anti-cache headers
        headers.setCacheControl("no-cache, no-store, must-revalidate");

        // Commit execution parameters to system audit tables
        auditService.log(
                currentUserUtil.getCurrentUser().getOrganization(),
                currentUserUtil.getCurrentUser(),
                "EXPORT",
                "CreditBureauExport",
                "export",
                "Exported Credit Bureau report as " + format.toUpperCase(),
                null,
                null,
                "Regulatory Reporting");

        // Serve raw file transmission bundle down to connection pipeline
        return ResponseEntity.ok()
                .headers(headers)
                .body(fileBytes);
    }

    // ============================================================
    // PRIVATE UTILS
    // ============================================================

    private static String csvValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static void appendCsvRow(StringBuilder csv, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                csv.append(',');
            }
            String value = values.get(i) == null ? "" : values.get(i);
            csv.append('"').append(value.replace("\"", "\"\"")).append('"');
        }
        csv.append("\r\n");
    }

    private LocalDate parseDate(String d) {
        return d != null && !d.trim().isEmpty() ? LocalDate.parse(d) : null;
    }

    private void validateDateRange(LocalDate f, LocalDate t) {
        if (f != null && t != null && f.isAfter(t)) {
            throw new IllegalArgumentException("From date cannot be after To date.");
        }
    }
}
