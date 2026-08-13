package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.model.JournalEntry;
import com.patrick.fintech.loan_backend.repository.JournalEntryRepository;
import com.patrick.fintech.loan_backend.service.AccountingService;
import com.patrick.fintech.loan_backend.service.ReportingService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','ACCOUNTANT')")
public class ReportingController {

    private final ReportingService reportingService;
    private final AccountingService accountingService;
    private final CurrentUserUtil currentUserUtil;
    private final JournalEntryRepository journalEntryRepository;

    private static final MediaType EXCEL_MEDIA_TYPE =
            MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            );

    private static final MediaType CSV_MEDIA_TYPE =
            MediaType.parseMediaType(
                    "text/csv"
            );

    // ============================================================
    // LOAN STATUS REPORT
    // ============================================================

    @GetMapping("/loans/{orgId}")
    public ResponseEntity<Map<String, Long>> loanStatusReport(
            @PathVariable Long orgId) {

        validateOrganization(orgId);

        return ResponseEntity.ok(
                reportingService.loanStatusReport(orgId)
        );
    }

    // ============================================================
    // PAYMENT REPORT
    // ============================================================

    @GetMapping("/payments/{orgId}")
    public ResponseEntity<Map<String, BigDecimal>> paymentReport(
            @PathVariable Long orgId) {

        validateOrganization(orgId);

        return ResponseEntity.ok(
                reportingService.paymentReport(orgId)
        );
    }

    // ============================================================
    // ACCOUNTING DASHBOARD
    //
    // This is the main unified accounting report endpoint.
    //
    // It returns:
    //   - Trial Balance
    //   - Balance Sheet
    //   - Profit & Loss
    //   - Cash Flow
    //   - Journal
    //   - Monthly Profit
    //   - Monthly Expenses
    //
    // Example:
    //
    // GET /api/reports/accounting/1
    //
    // Or:
    //
    // GET /api/reports/accounting/1?from=2026-01-01&to=2026-08-13
    // ============================================================

    @GetMapping("/accounting/{orgId}")
    public ResponseEntity<Map<String, Object>> accountingReport(
            @PathVariable Long orgId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        validateOrganization(orgId);

        LocalDate fromDate = parseFromDate(from);
        LocalDate toDate = parseToDate(to);

        validateDateRange(fromDate, toDate);

        Map<String, Object> report =
                buildAccountingReport(
                        orgId,
                        fromDate,
                        toDate
                );

        return ResponseEntity.ok(report);
    }

    // ============================================================
    // ACCOUNTING DASHBOARD - CURRENT MONTH
    //
    // Convenient endpoint for the frontend dashboard.
    //
    // GET /api/reports/accounting/{orgId}/current-month
    // ============================================================

    @GetMapping("/accounting/{orgId}/current-month")
    public ResponseEntity<Map<String, Object>> currentMonthAccountingReport(
            @PathVariable Long orgId) {

        validateOrganization(orgId);

        LocalDate today = LocalDate.now();

        LocalDate fromDate =
                today.withDayOfMonth(1);

        LocalDate toDate =
                today;

        return ResponseEntity.ok(
                buildAccountingReport(
                        orgId,
                        fromDate,
                        toDate
                )
        );
    }

    // ============================================================
    // TRIAL BALANCE
    // ============================================================

    @GetMapping("/accounting/{orgId}/trial-balance")
    public ResponseEntity<Map<String, Object>> trialBalance(
            @PathVariable Long orgId) {

        validateOrganization(orgId);

        return ResponseEntity.ok(
                accountingService.getTrialBalance(orgId)
        );
    }

    // ============================================================
    // BALANCE SHEET
    // ============================================================

    @GetMapping("/accounting/{orgId}/balance-sheet")
    public ResponseEntity<Map<String, Object>> balanceSheet(
            @PathVariable Long orgId) {

        validateOrganization(orgId);

        return ResponseEntity.ok(
                accountingService.getBalanceSheet(orgId)
        );
    }

    // ============================================================
    // PROFIT AND LOSS
    // ============================================================

    @GetMapping("/accounting/{orgId}/profit-and-loss")
    public ResponseEntity<Map<String, Object>> profitAndLoss(
            @PathVariable Long orgId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        validateOrganization(orgId);

        LocalDate fromDate =
                parseFromDate(from);

        LocalDate toDate =
                parseToDate(to);

        validateDateRange(
                fromDate,
                toDate
        );

        return ResponseEntity.ok(
                accountingService.getProfitAndLoss(
                        orgId,
                        fromDate,
                        toDate
                )
        );
    }

    // ============================================================
    // MONTHLY PROFIT
    //
    // Returns monthly P&L information for a selected year.
    //
    // Example:
    //
    // GET /api/reports/accounting/1/monthly-profit?year=2026
    //
    // This deliberately uses AccountingService.getProfitAndLoss()
    // as the accounting source of truth.
    // ============================================================

    @GetMapping("/accounting/{orgId}/monthly-profit")
    public ResponseEntity<List<Map<String, Object>>> monthlyProfit(
            @PathVariable Long orgId,
            @RequestParam(required = false) Integer year) {

        validateOrganization(orgId);

        int requestedYear =
                year != null
                        ? year
                        : LocalDate.now().getYear();

        return ResponseEntity.ok(
                buildMonthlyProfitAndExpenses(
                        orgId,
                        requestedYear
                )
        );
    }

    // ============================================================
    // MONTHLY EXPENSES
    //
    // Same accounting source of truth as monthly profit.
    // ============================================================

    @GetMapping("/accounting/{orgId}/monthly-expenses")
    public ResponseEntity<List<Map<String, Object>>> monthlyExpenses(
            @PathVariable Long orgId,
            @RequestParam(required = false) Integer year) {

        validateOrganization(orgId);

        int requestedYear =
                year != null
                        ? year
                        : LocalDate.now().getYear();

        List<Map<String, Object>> monthly =
                buildMonthlyProfitAndExpenses(
                        orgId,
                        requestedYear
                );

        List<Map<String, Object>> result =
                new ArrayList<>();

        for (Map<String, Object> month : monthly) {

            Map<String, Object> row =
                    new LinkedHashMap<>();

            row.put(
                    "year",
                    month.get("year")
            );

            row.put(
                    "month",
                    month.get("month")
            );

            row.put(
                    "monthNumber",
                    month.get("monthNumber")
            );

            row.put(
                    "from",
                    month.get("from")
            );

            row.put(
                    "to",
                    month.get("to")
            );

            row.put(
                    "expenses",
                    month.get("expenses")
            );

            result.add(row);
        }

        return ResponseEntity.ok(result);
    }

    // ============================================================
    // CASH FLOW
    // ============================================================

    @GetMapping("/accounting/{orgId}/cash-flow")
    public ResponseEntity<Map<String, Object>> cashFlow(
            @PathVariable Long orgId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        validateOrganization(orgId);

        LocalDate fromDate =
                parseFromDate(from);

        LocalDate toDate =
                parseToDate(to);

        validateDateRange(
                fromDate,
                toDate
        );

        return ResponseEntity.ok(
                accountingService.getCashFlow(
                        orgId,
                        fromDate,
                        toDate
                )
        );
    }

    // ============================================================
    // GENERAL JOURNAL
    //
    // Accounting journal for the selected organization.
    // ============================================================

    @GetMapping("/accounting/{orgId}/journal")
    public ResponseEntity<List<JournalEntry>> journal(
            @PathVariable Long orgId) {

        validateOrganization(orgId);

        List<JournalEntry> entries =
                journalEntryRepository
                        .findByOrganization_IdOrderByEntryDateDesc(
                                orgId
                        );

        return ResponseEntity.ok(entries);
    }

    // ============================================================
    // GENERAL JOURNAL - DATE RANGE
    // ============================================================

    @GetMapping("/accounting/{orgId}/journal/range")
    public ResponseEntity<List<JournalEntry>> journalRange(
            @PathVariable Long orgId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        validateOrganization(orgId);

        LocalDate fromDate =
                parseFromDate(from);

        LocalDate toDate =
                parseToDate(to);

        validateDateRange(
                fromDate,
                toDate
        );

        /*
         * We intentionally use the existing repository method here
         * only if the repository exposes a date-range query.
         *
         * Otherwise the complete organization journal is loaded and
         * filtered safely in memory.
         */
        List<JournalEntry> allEntries =
                journalEntryRepository
                        .findByOrganization_IdOrderByEntryDateDesc(
                                orgId
                        );

        List<JournalEntry> filtered =
                allEntries.stream()
                        .filter(entry ->
                                entry != null
                                        && entry.getEntryDate() != null
                                        && !entry.getEntryDate().isBefore(fromDate)
                                        && !entry.getEntryDate().isAfter(toDate))
                        .toList();

        return ResponseEntity.ok(filtered);
    }

    // ============================================================
    // PORTFOLIO SUMMARY
    // ============================================================

    @GetMapping("/accounting/{orgId}/portfolio")
    public ResponseEntity<Map<String, Object>> portfolioSummary(
            @PathVariable Long orgId) {

        validateOrganization(orgId);

        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put(
                "loanStatus",
                reportingService.loanStatusReport(orgId)
        );

        result.put(
                "payments",
                reportingService.paymentReport(orgId)
        );

        return ResponseEntity.ok(result);
    }

    // ============================================================
    // CSV - LOANS
    // ============================================================

    @GetMapping("/export/loans")
    public ResponseEntity<byte[]> exportLoansCsv() {

        Long organizationId =
                getCurrentOrganizationId();

        String csv =
                reportingService.exportLoansCsv(
                        organizationId
                );

        return csvResponse(
                csv,
                "loans"
        );
    }

    // ============================================================
    // EXCEL - LOANS
    // ============================================================

    @GetMapping("/export/loans/excel")
    public ResponseEntity<byte[]> exportLoansExcel() {

        Long organizationId =
                getCurrentOrganizationId();

        byte[] excel =
                reportingService.exportLoansExcel(
                        organizationId
                );

        return excelResponse(
                excel,
                "loans"
        );
    }

    // ============================================================
    // CSV - PAYMENTS
    // ============================================================

    @GetMapping("/export/payments")
    public ResponseEntity<byte[]> exportPaymentsCsv() {

        Long organizationId =
                getCurrentOrganizationId();

        String csv =
                reportingService.exportPaymentsCsv(
                        organizationId
                );

        return csvResponse(
                csv,
                "payments"
        );
    }

    // ============================================================
    // EXCEL - PAYMENTS
    // ============================================================

    @GetMapping("/export/payments/excel")
    public ResponseEntity<byte[]> exportPaymentsExcel() {

        Long organizationId =
                getCurrentOrganizationId();

        byte[] excel =
                reportingService.exportPaymentsExcel(
                        organizationId
                );

        return excelResponse(
                excel,
                "payments"
        );
    }

    // ============================================================
    // CSV - OVERDUE
    // ============================================================

    @GetMapping("/export/overdue")
    public ResponseEntity<byte[]> exportOverdueCsv() {

        Long organizationId =
                getCurrentOrganizationId();

        String csv =
                reportingService.exportOverdueCsv(
                        organizationId
                );

        return csvResponse(
                csv,
                "overdue-payments"
        );
    }

    // ============================================================
    // EXCEL - OVERDUE
    // ============================================================

    @GetMapping("/export/overdue/excel")
    public ResponseEntity<byte[]> exportOverdueExcel() {

        Long organizationId =
                getCurrentOrganizationId();

        byte[] excel =
                reportingService.exportOverdueExcel(
                        organizationId
                );

        return excelResponse(
                excel,
                "overdue-payments"
        );
    }

    // ============================================================
    // CSV - PORTFOLIO SUMMARY
    // ============================================================

    @GetMapping("/export/summary")
    public ResponseEntity<byte[]> exportSummaryCsv() {

        Long organizationId =
                getCurrentOrganizationId();

        String csv =
                reportingService.exportPortfolioSummaryCsv(
                        organizationId
                );

        return csvResponse(
                csv,
                "portfolio-summary"
        );
    }

    // ============================================================
    // EXCEL - PORTFOLIO SUMMARY
    // ============================================================

    @GetMapping("/export/summary/excel")
    public ResponseEntity<byte[]> exportSummaryExcel() {

        Long organizationId =
                getCurrentOrganizationId();

        byte[] excel =
                reportingService.exportPortfolioSummaryExcel(
                        organizationId
                );

        return excelResponse(
                excel,
                "portfolio-summary"
        );
    }

    // ============================================================
    // CURRENT ORGANIZATION
    // ============================================================

    private Long getCurrentOrganizationId() {

        Long organizationId =
                currentUserUtil.getCurrentOrganizationId();

        if (organizationId == null) {

            log.warn(
                    "Report request rejected because no organization "
                            + "could be resolved for the authenticated user"
            );

            throw new IllegalStateException(
                    "Current organization could not be determined."
            );
        }

        return organizationId;
    }

    // ============================================================
    // ORGANIZATION SECURITY
    // ============================================================

    private void validateOrganization(
            Long requestedOrganizationId) {

        if (requestedOrganizationId == null) {

            throw new IllegalArgumentException(
                    "Organization ID is required."
            );
        }

        Long currentOrganizationId =
                currentUserUtil.getCurrentOrganizationId();

        if (currentOrganizationId == null) {

            log.warn(
                    "Report access denied: authenticated user has no "
                            + "resolved organization"
            );

            throw new IllegalStateException(
                    "Current organization could not be determined."
            );
        }

        if (!requestedOrganizationId.equals(
                currentOrganizationId
        )) {

            log.warn(
                    "Cross-organization report access blocked. "
                            + "requestedOrganizationId={}, currentOrganizationId={}",
                    requestedOrganizationId,
                    currentOrganizationId
            );

            throw new org.springframework.security.access.AccessDeniedException(
                    "Access denied."
            );
        }
    }

    // ============================================================
    // ACCOUNTING REPORT BUILDER
    // ============================================================

    private Map<String, Object> buildAccountingReport(
            Long organizationId,
            LocalDate fromDate,
            LocalDate toDate) {

        Map<String, Object> report =
                new LinkedHashMap<>();

        report.put(
                "organizationId",
                organizationId
        );

        report.put(
                "period",
                buildPeriod(
                        fromDate,
                        toDate
                )
        );

        /*
         * Balance sheet / trial balance are point-in-time reports.
         */
        report.put(
                "trialBalance",
                accountingService.getTrialBalance(
                        organizationId
                )
        );

        report.put(
                "balanceSheet",
                accountingService.getBalanceSheet(
                        organizationId
                )
        );

        /*
         * Income statement / P&L is period based.
         */
        report.put(
                "profitAndLoss",
                accountingService.getProfitAndLoss(
                        organizationId,
                        fromDate,
                        toDate
                )
        );

        /*
         * Cash flow is also period based.
         */
        report.put(
                "cashFlow",
                accountingService.getCashFlow(
                        organizationId,
                        fromDate,
                        toDate
                )
        );

        /*
         * Journal is included so the frontend can drill down from
         * financial statements into actual accounting entries.
         */
        List<JournalEntry> journal =
                journalEntryRepository
                        .findByOrganization_IdOrderByEntryDateDesc(
                                organizationId
                        )
                        .stream()
                        .filter(entry ->
                                entry != null
                                        && entry.getEntryDate() != null
                                        && !entry.getEntryDate().isBefore(fromDate)
                                        && !entry.getEntryDate().isAfter(toDate))
                        .toList();

        report.put(
                "journal",
                journal
        );

        /*
         * Monthly profit/expense trend for the selected year.
         */
        int year =
                fromDate.getYear();

        report.put(
                "monthlyProfitAndExpenses",
                buildMonthlyProfitAndExpenses(
                        organizationId,
                        year
                )
        );

        return report;
    }

    // ============================================================
    // MONTHLY PROFIT / EXPENSES
    // ============================================================

    private List<Map<String, Object>> buildMonthlyProfitAndExpenses(
            Long organizationId,
            int year) {

        List<Map<String, Object>> result =
                new ArrayList<>();

        LocalDate today =
                LocalDate.now();

        for (int month = 1; month <= 12; month++) {

            LocalDate monthStart =
                    LocalDate.of(
                            year,
                            month,
                            1
                    );

            LocalDate monthEnd =
                    monthStart
                            .withDayOfMonth(
                                    monthStart.lengthOfMonth()
                            );

            /*
             * Do not report future months as completed accounting
             * periods.
             */
            if (monthStart.isAfter(today)) {
                break;
            }

            LocalDate effectiveEnd =
                    monthEnd.isAfter(today)
                            ? today
                            : monthEnd;

            Map<String, Object> pnl =
                    accountingService.getProfitAndLoss(
                            organizationId,
                            monthStart,
                            effectiveEnd
                    );

            Map<String, Object> row =
                    new LinkedHashMap<>();

            row.put(
                    "year",
                    year
            );

            row.put(
                    "month",
                    monthStart.getMonth().name()
            );

            row.put(
                    "monthNumber",
                    month
            );

            row.put(
                    "from",
                    monthStart
            );

            row.put(
                    "to",
                    effectiveEnd
            );

            /*
             * Preserve the exact values returned by AccountingService.
             *
             * We do not cast monetary values to double.
             */
            row.put(
                    "profit",
                    extractMoney(
                            pnl,
                            "profit",
                            "netProfit",
                            "netIncome"
                    )
            );

            row.put(
                    "expenses",
                    extractMoney(
                            pnl,
                            "expenses",
                            "totalExpenses"
                    )
            );

            row.put(
                    "revenue",
                    extractMoney(
                            pnl,
                            "revenue",
                            "totalRevenue",
                            "income"
                    )
            );

            row.put(
                    "netIncome",
                    extractMoney(
                            pnl,
                            "netIncome",
                            "netProfit",
                            "profit"
                    )
            );

            /*
             * Keep the complete P&L as well.
             *
             * This makes the endpoint future-proof if AccountingService
             * returns additional accounting categories.
             */
            row.put(
                    "profitAndLoss",
                    pnl
            );

            result.add(row);
        }

        return result;
    }

    // ============================================================
    // MONEY EXTRACTION
    // ============================================================

    private BigDecimal extractMoney(
            Map<String, Object> source,
            String... keys) {

        if (source == null || keys == null) {
            return BigDecimal.ZERO.setScale(
                    2
            );
        }

        for (String key : keys) {

            if (!source.containsKey(key)) {
                continue;
            }

            Object value =
                    source.get(key);

            if (value == null) {
                continue;
            }

            if (value instanceof BigDecimal decimal) {
                return decimal;
            }

            if (value instanceof Number number) {

                /*
                 * Compatibility only.
                 *
                 * AccountingService should return BigDecimal.
                 * This prevents the controller from crashing if an
                 * older report implementation returns another Number.
                 */
                return new BigDecimal(
                        number.toString()
                );
            }

            try {

                return new BigDecimal(
                        value.toString()
                );

            } catch (NumberFormatException ignored) {
                // Try next supported key.
            }
        }

        return BigDecimal.ZERO.setScale(
                2
        );
    }

    // ============================================================
    // PERIOD
    // ============================================================

    private Map<String, Object> buildPeriod(
            LocalDate fromDate,
            LocalDate toDate) {

        Map<String, Object> period =
                new LinkedHashMap<>();

        period.put(
                "from",
                fromDate
        );

        period.put(
                "to",
                toDate
        );

        period.put(
                "days",
                java.time.temporal.ChronoUnit.DAYS.between(
                        fromDate,
                        toDate
                ) + 1
        );

        return period;
    }

    // ============================================================
    // DATE PARSING
    // ============================================================

    private LocalDate parseFromDate(
            String from) {

        if (from == null
                || from.isBlank()) {

            return LocalDate.now()
                    .withDayOfMonth(1);
        }

        try {

            return LocalDate.parse(
                    from
            );

        } catch (Exception e) {

            throw new IllegalArgumentException(
                    "Invalid 'from' date. Expected YYYY-MM-DD."
            );
        }
    }

    private LocalDate parseToDate(
            String to) {

        if (to == null
                || to.isBlank()) {

            return LocalDate.now();
        }

        try {

            return LocalDate.parse(
                    to
            );

        } catch (Exception e) {

            throw new IllegalArgumentException(
                    "Invalid 'to' date. Expected YYYY-MM-DD."
            );
        }
    }

    // ============================================================
    // DATE RANGE VALIDATION
    // ============================================================

    private void validateDateRange(
            LocalDate fromDate,
            LocalDate toDate) {

        if (fromDate == null
                || toDate == null) {

            throw new IllegalArgumentException(
                    "Both report dates are required."
            );
        }

        if (fromDate.isAfter(toDate)) {

            throw new IllegalArgumentException(
                    "'from' date cannot be after 'to' date."
            );
        }
    }

    // ============================================================
    // CSV RESPONSE
    // ============================================================

    private ResponseEntity<byte[]> csvResponse(
            String csv,
            String filename) {

        if (csv == null) {
            csv = "";
        }

        String finalFilename =
                sanitizeFilename(filename)
                        + "-"
                        + LocalDate.now()
                        + ".csv";

        byte[] body =
                csv.getBytes(
                        StandardCharsets.UTF_8
                );

        HttpHeaders headers =
                new HttpHeaders();

        headers.setContentType(
                new MediaType(
                        "text",
                        "csv",
                        StandardCharsets.UTF_8
                )
        );

        headers.setContentDisposition(
                ContentDisposition
                        .attachment()
                        .filename(
                                finalFilename,
                                StandardCharsets.UTF_8
                        )
                        .build()
        );

        headers.setContentLength(
                body.length
        );

        headers.setCacheControl(
                CacheControl
                        .noCache()
                        .noStore()
                        .mustRevalidate()
        );

        headers.add(
                HttpHeaders.PRAGMA,
                "no-cache"
        );

        headers.add(
                HttpHeaders.EXPIRES,
                "0"
        );

        return ResponseEntity
                .ok()
                .headers(headers)
                .body(body);
    }

    // ============================================================
    // EXCEL RESPONSE
    // ============================================================

    private ResponseEntity<byte[]> excelResponse(
            byte[] excel,
            String filename) {

        if (excel == null) {

            throw new IllegalStateException(
                    "Excel report generation returned no data."
            );
        }

        if (excel.length == 0) {

            throw new IllegalStateException(
                    "Excel report generation returned an empty file."
            );
        }

        String finalFilename =
                sanitizeFilename(filename)
                        + "-"
                        + LocalDate.now()
                        + ".xlsx";

        HttpHeaders headers =
                new HttpHeaders();

        headers.setContentType(
                EXCEL_MEDIA_TYPE
        );

        headers.setContentDisposition(
                ContentDisposition
                        .attachment()
                        .filename(
                                finalFilename,
                                StandardCharsets.UTF_8
                        )
                        .build()
        );

        headers.setContentLength(
                excel.length
        );

        headers.setCacheControl(
                CacheControl
                        .noCache()
                        .noStore()
                        .mustRevalidate()
        );

        headers.add(
                HttpHeaders.PRAGMA,
                "no-cache"
        );

        headers.add(
                HttpHeaders.EXPIRES,
                "0"
        );

        return ResponseEntity
                .ok()
                .headers(headers)
                .body(excel);
    }

    // ============================================================
    // FILENAME SECURITY
    // ============================================================

    private String sanitizeFilename(
            String filename) {

        if (filename == null
                || filename.isBlank()) {

            return "report";
        }

        return filename
                .replace(
                        "\\",
                        "-"
                )
                .replace(
                        "/",
                        "-"
                )
                .replace(
                        "\"",
                        "-"
                )
                .replace(
                        "\r",
                        "-"
                )
                .replace(
                        "\n",
                        "-"
                )
                .trim();
    }
}