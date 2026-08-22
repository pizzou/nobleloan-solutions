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
            "Borrower ID", "National ID", "Full Name", "Date of Birth", "Gender", "Phone",
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
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            MediaType.APPLICATION_PDF_VALUE,
            "text/csv"
    })
    public ResponseEntity<byte[]> export(
            @RequestParam(defaultValue = "xlsx") String format,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        Long organizationId = currentUserUtil.getCurrentOrganizationId();

        if (organizationId == null) {
            throw new IllegalStateException(
                    "Current user is not associated with an organization.");
        }

        LocalDate fromDate = parseDate(from);
        LocalDate toDate = parseDate(to);

        validateDateRange(fromDate, toDate);

        String normalizedFormat = format == null
                ? "xlsx"
                : format.trim().toLowerCase();

        /*
         * ============================================================
         * XLSX
         * ============================================================
         *
         * This branch MUST be handled before CSV/PDF because the
         * CreditBureauRegulatoryExportService already generates the
         * native regulatory XLSX workbook.
         */
        if ("xlsx".equals(normalizedFormat)) {

            byte[] fileBytes = creditBureauRegulatoryExportService.export(
                    organizationId,
                    branchId,
                    null,
                    fromDate,
                    toDate);

            if (fileBytes == null || fileBytes.length == 0) {
                throw new IllegalStateException(
                        "Credit Bureau XLSX report is empty.");
            }

            String fileName = "credit_bureau_"
                    + LocalDate.now()
                    + ".xlsx";

            HttpHeaders headers = new HttpHeaders();

            headers.setContentType(
                    MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

            headers.setContentDispositionFormData(
                    "attachment",
                    fileName);

            headers.setContentLength(fileBytes.length);

            headers.setCacheControl(
                    "no-cache, no-store, must-revalidate");

            headers.add(
                    "Pragma",
                    "no-cache");

            headers.add(
                    "Expires",
                    "0");

            auditService.log(
                    currentUserUtil.getCurrentUser().getOrganization(),
                    currentUserUtil.getCurrentUser(),
                    "EXPORT",
                    "CreditBureauExport",
                    "export",
                    "Exported native CRB regulatory XLSX workbook",
                    null,
                    null,
                    "Regulatory Reporting");

            return ResponseEntity
                    .ok()
                    .headers(headers)
                    .body(fileBytes);
        }

        /*
         * ============================================================
         * DATA FOR CSV / PDF
         * ============================================================
         */

        List<CreditBureauRecord> records = reportingService.buildCreditBureauExport(
                organizationId,
                branchId,
                fromDate,
                toDate);

        byte[] fileBytes;

        String fileName = "credit_bureau_report_"
                + LocalDate.now();

        HttpHeaders headers = new HttpHeaders();

        /*
         * ============================================================
         * CSV
         * ============================================================
         */

        if ("csv".equals(normalizedFormat)) {

            StringBuilder csv = new StringBuilder();

            csv.append(
                    String.join(",", COLUMNS))
                    .append("\n");

            if (records != null) {

                for (CreditBureauRecord r : records) {

                    csv.append(
                            String.format(
                                    "%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%.2f,%.2f,%d,%d,%s,%s,%s,%s,%s,%s\n",

                                    r.getBorrowerId() != null
                                            ? String.valueOf(r.getBorrowerId())
                                            : "",

                                    r.getNationalId() != null
                                            ? r.getNationalId()
                                            : "",

                                    r.getFullName() != null
                                            ? r.getFullName().replace(",", " ")
                                            : "",

                                    r.getDateOfBirth() != null
                                            ? r.getDateOfBirth()
                                            : "",

                                    r.getGender() != null
                                            ? r.getGender()
                                            : "",

                                    r.getPhone() != null
                                            ? r.getPhone()
                                            : "",

                                    r.getLoanNumber() != null
                                            ? r.getLoanNumber()
                                            : "",

                                    r.getLoanType() != null
                                            ? r.getLoanType()
                                            : "",

                                    r.getLoanStatus() != null
                                            ? r.getLoanStatus()
                                            : "",

                                    r.getRepaymentClassification() != null
                                            ? r.getRepaymentClassification()
                                            : "",

                                    r.getLoanAmount(),

                                    r.getOutstandingBalance(),

                                    r.getDaysPastDue(),

                                    r.getCreditScore() != null
                                            ? r.getCreditScore()
                                            : 0,

                                    r.getDateOpened() != null
                                            ? r.getDateOpened()
                                            : "",

                                    r.getLastPaymentDate() != null
                                            ? r.getLastPaymentDate()
                                            : "",

                                    r.getMaturityDate() != null
                                            ? r.getMaturityDate()
                                            : "",

                                    r.getDateClosed() != null
                                            ? r.getDateClosed()
                                            : "",

                                    r.getBranchName() != null
                                            ? r.getBranchName()
                                            : "",

                                    r.getCurrency() != null
                                            ? r.getCurrency()
                                            : ""));
                }
            }

            fileBytes = csv.toString()
                    .getBytes(StandardCharsets.UTF_8);

            headers.setContentType(
                    MediaType.parseMediaType("text/csv"));

            headers.setContentDispositionFormData(
                    "attachment",
                    fileName + ".csv");

            /*
             * ============================================================
             * PDF
             * ============================================================
             */

        } else if ("pdf".equals(normalizedFormat)) {

            try (
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {

                com.lowagie.text.Document document = new com.lowagie.text.Document(
                        com.lowagie.text.PageSize.A3.rotate());

                com.lowagie.text.pdf.PdfWriter
                        .getInstance(document, out);

                document.open();

                com.lowagie.text.Paragraph title = new com.lowagie.text.Paragraph(
                        "CREDIT BUREAU REGULATORY REPORT\n"
                                + "Generated: "
                                + LocalDate.now()
                                + "\n\n");

                title.setAlignment(
                        com.lowagie.text.Element.ALIGN_CENTER);

                document.add(title);

                com.lowagie.text.Table table = new com.lowagie.text.Table(20);

                table.setPadding(3);
                table.setWidth(100);

                for (String column : COLUMNS) {
                    table.addCell(column);
                }

                if (records != null) {

                    for (CreditBureauRecord r : records) {

                        table.addCell(
                                r.getBorrowerId() != null
                                        ? String.valueOf(r.getBorrowerId())
                                        : "");

                        table.addCell(
                                r.getNationalId() != null
                                        ? r.getNationalId()
                                        : "");

                        table.addCell(
                                r.getFullName() != null
                                        ? r.getFullName()
                                        : "");

                        table.addCell(
                                r.getDateOfBirth() != null
                                        ? r.getDateOfBirth().toString()
                                        : "");

                        table.addCell(
                                r.getGender() != null
                                        ? r.getGender()
                                        : "");

                        table.addCell(
                                r.getPhone() != null
                                        ? r.getPhone()
                                        : "");

                        table.addCell(
                                r.getLoanNumber() != null
                                        ? r.getLoanNumber()
                                        : "");

                        table.addCell(
                                r.getLoanType() != null
                                        ? r.getLoanType()
                                        : "");

                        table.addCell(
                                r.getLoanStatus() != null
                                        ? r.getLoanStatus()
                                        : "");

                        table.addCell(
                                r.getRepaymentClassification() != null
                                        ? r.getRepaymentClassification()
                                        : "");

                        table.addCell(
                                String.format(
                                        "%.2f",
                                        r.getLoanAmount()));

                        table.addCell(
                                String.format(
                                        "%.2f",
                                        r.getOutstandingBalance()));

                        table.addCell(
                                String.valueOf(
                                        r.getDaysPastDue()));

                        table.addCell(
                                r.getCreditScore() != null
                                        ? String.valueOf(r.getCreditScore())
                                        : "");

                        table.addCell(
                                r.getDateOpened() != null
                                        ? r.getDateOpened().toString()
                                        : "");

                        table.addCell(
                                r.getLastPaymentDate() != null
                                        ? r.getLastPaymentDate().toString()
                                        : "");

                        table.addCell(
                                r.getMaturityDate() != null
                                        ? r.getMaturityDate().toString()
                                        : "");

                        table.addCell(
                                r.getDateClosed() != null
                                        ? r.getDateClosed().toString()
                                        : "");

                        table.addCell(
                                r.getBranchName() != null
                                        ? r.getBranchName()
                                        : "");

                        table.addCell(
                                r.getCurrency() != null
                                        ? r.getCurrency()
                                        : "");
                    }
                }

                document.add(table);

                document.close();

                fileBytes = out.toByteArray();

            } catch (Exception e) {

                throw new IllegalStateException(
                        "Failed to generate Credit Bureau PDF report",
                        e);
            }

            headers.setContentType(
                    MediaType.APPLICATION_PDF);

            headers.setContentDispositionFormData(
                    "attachment",
                    fileName + ".pdf");

            /*
             * ============================================================
             * INVALID FORMAT
             * ============================================================
             */

        } else {

            throw new IllegalArgumentException(
                    "Unsupported Credit Bureau export format: "
                            + format
                            + ". Supported formats are xlsx, csv and pdf.");
        }

        /*
         * ============================================================
         * COMMON RESPONSE HEADERS
         * ============================================================
         */

        headers.setContentLength(
                fileBytes.length);

        headers.setCacheControl(
                "no-cache, no-store, must-revalidate");

        headers.add(
                "Pragma",
                "no-cache");

        headers.add(
                "Expires",
                "0");

        auditService.log(
                currentUserUtil.getCurrentUser().getOrganization(),
                currentUserUtil.getCurrentUser(),
                "EXPORT",
                "CreditBureauExport",
                "export",
                "Exported Credit Bureau report as "
                        + normalizedFormat.toUpperCase(),
                null,
                null,
                "Regulatory Reporting");

        return ResponseEntity
                .ok()
                .headers(headers)
                .body(fileBytes);
    }

    // ============================================================
    // PRIVATE UTILS
    // ============================================================

    private LocalDate parseDate(String d) {
        return d != null && !d.trim().isEmpty() ? LocalDate.parse(d) : null;
    }

    private void validateDateRange(LocalDate f, LocalDate t) {
        if (f != null && t != null && f.isAfter(t)) {
            throw new IllegalArgumentException("From date cannot be after To date.");
        }
    }
}
