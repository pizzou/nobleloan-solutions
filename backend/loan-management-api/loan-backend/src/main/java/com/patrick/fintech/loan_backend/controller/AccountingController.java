package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.mapper.ResponseDtoMapper;
import com.patrick.fintech.loan_backend.model.ChartOfAccount;
import com.patrick.fintech.loan_backend.model.JournalEntry;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.repository.ChartOfAccountRepository;
import com.patrick.fintech.loan_backend.repository.JournalEntryRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import com.patrick.fintech.loan_backend.service.AccountingService;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.ReportExportService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/accounting")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','ACCOUNTANT')")
public class AccountingController {

        private final AccountingService accountingService;
        private final ChartOfAccountRepository coaRepo;
        private final JournalEntryRepository journalRepo;
        private final LoanRepository loanRepo;
        private final OrganizationRepository orgRepo;
        private final CurrentUserUtil currentUserUtil;
        private final AuditService auditService;
        private final ReportExportService exportService;

        // ============================================================
        // ORGANIZATION / SECURITY
        // ============================================================

        /**
         * Returns the organization belonging to the currently authenticated user.
         *
         * Every accounting operation must be scoped to this organization.
         */
        private Organization requireCurrentOrganization() {

                Long orgId = currentUserUtil.getCurrentOrganizationId();

                if (orgId == null) {
                        throw new IllegalStateException(
                                        "No organization is associated with the current user");
                }

                return orgRepo.findById(orgId)
                                .orElseThrow(() -> new IllegalStateException(
                                                "Organization not found: " + orgId));
        }

        private Long requireOrganizationId() {
                return requireCurrentOrganization().getId();
        }

        // ============================================================
        // DATE VALIDATION
        // ============================================================

        private LocalDate parseDate(
                        String value,
                        String parameterName) {

                if (value == null || value.isBlank()) {
                        return null;
                }

                try {
                        return LocalDate.parse(value.trim());
                } catch (DateTimeException e) {
                        throw new IllegalArgumentException(
                                        "Invalid " + parameterName
                                                        + " date. Expected format YYYY-MM-DD.");
                }
        }

        /**
         * Resolves a report period.
         *
         * Default:
         * from = first day of current month
         * to = today
         */
        private DateRange resolveDateRange(
                        String from,
                        String to) {

                LocalDate fromDate = parseDate(from, "from");
                LocalDate toDate = parseDate(to, "to");

                LocalDate today = LocalDate.now();

                if (fromDate == null) {
                        fromDate = today.withDayOfMonth(1);
                }

                if (toDate == null) {
                        toDate = today;
                }

                if (fromDate.isAfter(toDate)) {
                        throw new IllegalArgumentException(
                                        "The from date cannot be after the to date");
                }

                return new DateRange(fromDate, toDate);
        }

        private record DateRange(
                        LocalDate from,
                        LocalDate to) {
        }

        // ============================================================
        // CHART OF ACCOUNTS
        // ============================================================

        @GetMapping("/chart-of-accounts")
        public ResponseEntity<ApiResponse<Object>> chartOfAccounts() {

                Long orgId = requireOrganizationId();

                Organization organization = requireCurrentOrganization();

                accountingService.ensureChartOfAccounts(organization);

                List<ChartOfAccount> accounts = coaRepo.findByOrganization_IdOrderByCodeAsc(orgId);

                if (accounts == null) {
                        accounts = List.of();
                }

                return ResponseEntity.ok(
                                ApiResponse.safe(accounts));
        }

        @PostMapping("/chart-of-accounts")
        @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
        public ResponseEntity<ApiResponse<Object>> createAccount(
                        @RequestBody Map<String, String> body) {

                Organization organization = requireCurrentOrganization();

                if (body == null) {
                        throw new IllegalArgumentException(
                                        "Account request body is required");
                }

                String code = requireText(
                                body.get("code"),
                                "Account code");

                String name = requireText(
                                body.get("name"),
                                "Account name");

                String typeValue = requireText(
                                body.get("type"),
                                "Account type");

                String normalBalanceValue = requireText(
                                body.get("normalBalance"),
                                "Normal balance");

                ChartOfAccount.AccountType accountType;

                ChartOfAccount.NormalBalance normalBalance;

                try {

                        accountType = ChartOfAccount.AccountType.valueOf(
                                        typeValue.trim().toUpperCase());

                } catch (IllegalArgumentException e) {

                        throw new IllegalArgumentException(
                                        "Invalid account type: " + typeValue);
                }

                try {

                        normalBalance = ChartOfAccount.NormalBalance.valueOf(
                                        normalBalanceValue.trim().toUpperCase());

                } catch (IllegalArgumentException e) {

                        throw new IllegalArgumentException(
                                        "Invalid normal balance: "
                                                        + normalBalanceValue);
                }

                ChartOfAccount created = accountingService.createAccount(
                                organization,
                                code,
                                name,
                                accountType,
                                normalBalance);

                auditService.log(
                                organization,
                                currentUserUtil.getCurrentUser(),
                                "COA_ACCOUNT_CREATED",
                                "CHART_OF_ACCOUNT",
                                String.valueOf(created.getId()),
                                "Created accounting account "
                                                + created.getCode()
                                                + " - "
                                                + created.getName(),
                                null,
                                null,
                                "Accounting");

                return ResponseEntity.ok(
                                ApiResponse.safe(
                                                "Account created",
                                                created));
        }

        @PutMapping("/chart-of-accounts/{id}")
        @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
        public ResponseEntity<ApiResponse<Object>> updateAccount(
                        @PathVariable Long id,
                        @RequestBody Map<String, Object> body) {

                if (id == null || id <= 0) {
                        throw new IllegalArgumentException(
                                        "Invalid chart-of-account ID");
                }

                Long orgId = requireOrganizationId();

                if (body == null) {
                        throw new IllegalArgumentException(
                                        "Account update body is required");
                }

                String name = null;

                if (body.get("name") != null) {
                        name = body.get("name")
                                        .toString()
                                        .trim();

                        if (name.isBlank()) {
                                name = null;
                        }
                }

                Boolean active = null;

                if (body.get("active") != null) {

                        Object value = body.get("active");

                        if (value instanceof Boolean booleanValue) {

                                active = booleanValue;

                        } else {

                                String text = value.toString()
                                                .trim();

                                if (!"true".equalsIgnoreCase(text)
                                                && !"false".equalsIgnoreCase(text)) {

                                        throw new IllegalArgumentException(
                                                        "active must be true or false");
                                }

                                active = Boolean.valueOf(text);
                        }
                }

                ChartOfAccount updated = accountingService.updateAccount(
                                orgId,
                                id,
                                name,
                                active);

                if (updated == null) {
                        throw new IllegalStateException(
                                        "Account update returned no account");
                }

                auditService.log(
                                updated.getOrganization(),
                                currentUserUtil.getCurrentUser(),
                                "COA_ACCOUNT_UPDATED",
                                "CHART_OF_ACCOUNT",
                                String.valueOf(id),
                                "Updated accounting account "
                                                + updated.getCode(),
                                null,
                                null,
                                "Accounting");

                return ResponseEntity.ok(
                                ApiResponse.safe(
                                                "Account updated",
                                                updated));
        }

        // ============================================================
        // JOURNAL
        // ============================================================

        @GetMapping("/journal")
        public ResponseEntity<ApiResponse<Object>> journal() {

                Long orgId = requireOrganizationId();

                /*
                 * IMPORTANT:
                 *
                 * JournalEntry.lines is currently LAZY and @JsonIgnore.
                 *
                 * Therefore this endpoint intentionally returns the journal
                 * entry header records only.
                 *
                 * The accounting ledger/reporting service remains responsible
                 * for financial calculations.
                 */
                List<JournalEntry> entries = journalRepo.findByOrganization_IdOrderByEntryDateDesc(
                                orgId);

                if (entries == null) {
                        entries = List.of();
                }

                return ResponseEntity.ok(
                                ApiResponse.safe(entries));
        }

        // ============================================================
        // LEGACY LOAN ACCOUNTING RECONCILIATION
        // ============================================================

        @PostMapping("/legacy-loans/reconcile")
        @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
        public ResponseEntity<ApiResponse<Map<String, Object>>> reconcileLegacyLoanAccounting() {

                Organization organization = requireCurrentOrganization();

                List<com.patrick.fintech.loan_backend.model.Loan> importedLoans = loanRepo
                                .findByOrganization_IdAndImportedTrue(
                                                organization.getId());

                int repaired = accountingService.reconcileLegacyLoanOpeningBalances(
                                importedLoans);

                auditService.log(
                                organization,
                                currentUserUtil.getCurrentUser(),
                                "LEGACY_LOAN_ACCOUNTING_RECONCILED",
                                "ACCOUNTING",
                                String.valueOf(organization.getId()),
                                "Reconciled historical loan opening accounting balances. " +
                                                "processed=" + importedLoans.size() +
                                                ", created=" + repaired,
                                null,
                                null,
                                "Accounting");

                Map<String, Object> result = new LinkedHashMap<>();

                result.put("processed", importedLoans.size());
                result.put("created", repaired);

                return ResponseEntity.ok(
                                ApiResponse.safe(
                                                "Historical loan accounting reconciliation completed",
                                                result));
        }

        // ============================================================
        // REVERSE JOURNAL ENTRY
        // ============================================================

        @PostMapping("/journal/{id}/reverse")
        @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
        public ResponseEntity<ApiResponse<Object>> reverseEntry(
                        @PathVariable Long id,
                        @RequestBody(required = false) Map<String, String> body) {

                if (id == null || id <= 0) {
                        throw new IllegalArgumentException(
                                        "Invalid journal entry ID");
                }

                Long orgId = requireOrganizationId();

                String reason = null;

                if (body != null && body.get("reason") != null) {

                        reason = body.get("reason").trim();

                        if (reason.isBlank()) {
                                reason = null;
                        }
                }

                JournalEntry reversal = accountingService.reverseEntry(
                                orgId,
                                id,
                                currentUserUtil.getCurrentUser().getName(),
                                reason);

                if (reversal == null) {
                        throw new IllegalStateException(
                                        "Journal reversal returned no entry");
                }

                auditService.log(
                                reversal.getOrganization(),
                                currentUserUtil.getCurrentUser(),
                                "JOURNAL_ENTRY_REVERSED",
                                "JOURNAL_ENTRY",
                                String.valueOf(id),
                                "Reversed journal entry #"
                                                + id
                                                + (reason != null
                                                                ? ": " + reason
                                                                : ""),
                                null,
                                null,
                                "Accounting");

                return ResponseEntity.ok(
                                ApiResponse.safe(
                                                "Journal entry reversed",
                                                reversal));
        }

        // ============================================================
        // GENERAL LEDGER
        // ============================================================

        @GetMapping("/ledger/{accountId}")
        public ResponseEntity<ApiResponse<Map<String, Object>>> ledger(
                        @PathVariable Long accountId) {

                if (accountId == null || accountId <= 0) {
                        throw new IllegalArgumentException(
                                        "Invalid account ID");
                }

                Long orgId = requireOrganizationId();

                Map<String, Object> ledger = accountingService.getLedger(
                                orgId,
                                accountId);

                return ResponseEntity.ok(
                                ApiResponse.safe(
                                                ledger != null
                                                                ? ledger
                                                                : Map.of()));
        }

        // ============================================================
        // TRIAL BALANCE
        // ============================================================

        @GetMapping("/trial-balance")
        public ResponseEntity<ApiResponse<Map<String, Object>>> trialBalance() {

                Long orgId = requireOrganizationId();

                Map<String, Object> report = accountingService.getTrialBalance(orgId);

                return ResponseEntity.ok(
                                ApiResponse.safe(
                                                report != null
                                                                ? report
                                                                : Map.of()));
        }

        // ============================================================
        // BALANCE SHEET
        // ============================================================

        @GetMapping("/balance-sheet")
        public ResponseEntity<ApiResponse<Map<String, Object>>> balanceSheet() {

                Long orgId = requireOrganizationId();

                Map<String, Object> report = accountingService.getBalanceSheet(orgId);

                return ResponseEntity.ok(
                                ApiResponse.safe(
                                                report != null
                                                                ? report
                                                                : Map.of()));
        }

        // ============================================================
        // PROFIT AND LOSS
        // ============================================================

        @GetMapping("/profit-and-loss")
        public ResponseEntity<ApiResponse<Map<String, Object>>> profitAndLoss(
                        @RequestParam(required = false) String from,
                        @RequestParam(required = false) String to) {

                Long orgId = requireOrganizationId();

                DateRange range = resolveDateRange(from, to);

                Map<String, Object> report = accountingService.getProfitAndLoss(
                                orgId,
                                range.from(),
                                range.to());

                return ResponseEntity.ok(
                                ApiResponse.safe(
                                                report != null
                                                                ? report
                                                                : Map.of()));
        }

        // ============================================================
        // MONTHLY PROFIT AND LOSS
        // ============================================================

        /**
         * Returns the current calendar month's P&L.
         *
         * Example:
         * 2026-08-01 through 2026-08-13
         *
         * The AccountingService remains authoritative for the actual
         * accounting calculations.
         */
        @GetMapping("/monthly-profit-and-loss")
        public ResponseEntity<ApiResponse<Map<String, Object>>> monthlyProfitAndLoss(
                        @RequestParam(required = false) Integer year,
                        @RequestParam(required = false) Integer month) {

                LocalDate today = LocalDate.now();

                int resolvedYear = year != null
                                ? year
                                : today.getYear();

                int resolvedMonth = month != null
                                ? month
                                : today.getMonthValue();

                if (resolvedMonth < 1
                                || resolvedMonth > 12) {

                        throw new IllegalArgumentException(
                                        "month must be between 1 and 12");
                }

                LocalDate from = LocalDate.of(
                                resolvedYear,
                                resolvedMonth,
                                1);

                LocalDate to = from.withDayOfMonth(
                                from.lengthOfMonth());

                /*
                 * For the current month, do not report future dates.
                 */
                if (from.getYear() == today.getYear()
                                && from.getMonthValue() == today.getMonthValue()) {

                        to = today;
                }

                Long orgId = requireOrganizationId();

                Map<String, Object> report = accountingService.getProfitAndLoss(
                                orgId,
                                from,
                                to);

                Map<String, Object> response = new LinkedHashMap<>();

                response.put(
                                "year",
                                resolvedYear);

                response.put(
                                "month",
                                resolvedMonth);

                response.put(
                                "from",
                                from);

                response.put(
                                "to",
                                to);

                response.put(
                                "report",
                                report != null
                                                ? report
                                                : Map.of());

                return ResponseEntity.ok(
                                ApiResponse.safe(response));
        }

        // ============================================================
        // MONTHLY EXPENSES
        // ============================================================

        /**
         * Provides the expense portion of the monthly P&L.
         *
         * This deliberately derives the result from the accounting
         * P&L rather than querying an unrelated operational expense
         * table. This keeps reported expenses tied to the general ledger.
         */
        @GetMapping("/monthly-expenses")
        public ResponseEntity<ApiResponse<Map<String, Object>>> monthlyExpenses(
                        @RequestParam(required = false) Integer year,
                        @RequestParam(required = false) Integer month) {

                LocalDate today = LocalDate.now();

                int resolvedYear = year != null
                                ? year
                                : today.getYear();

                int resolvedMonth = month != null
                                ? month
                                : today.getMonthValue();

                if (resolvedMonth < 1
                                || resolvedMonth > 12) {

                        throw new IllegalArgumentException(
                                        "month must be between 1 and 12");
                }

                LocalDate from = LocalDate.of(
                                resolvedYear,
                                resolvedMonth,
                                1);

                LocalDate to = from.withDayOfMonth(
                                from.lengthOfMonth());

                if (from.getYear() == today.getYear()
                                && from.getMonthValue() == today.getMonthValue()) {

                        to = today;
                }

                Long orgId = requireOrganizationId();

                Map<String, Object> pnl = accountingService.getProfitAndLoss(
                                orgId,
                                from,
                                to);

                Map<String, Object> response = new LinkedHashMap<>();

                response.put(
                                "year",
                                resolvedYear);

                response.put(
                                "month",
                                resolvedMonth);

                response.put(
                                "from",
                                from);

                response.put(
                                "to",
                                to);

                if (pnl == null) {

                        response.put(
                                        "expenses",
                                        List.of());

                        response.put(
                                        "totalExpenses",
                                        0);

                } else {

                        /*
                         * Keep the original P&L structure intact.
                         *
                         * Depending on the AccountingService implementation,
                         * these keys are expected to be present.
                         */
                        response.put(
                                        "expenses",
                                        pnl.getOrDefault(
                                                        "expenses",
                                                        List.of()));

                        response.put(
                                        "totalExpenses",
                                        pnl.getOrDefault(
                                                        "totalExpenses",
                                                        pnl.getOrDefault(
                                                                        "expensesTotal",
                                                                        0)));

                        response.put(
                                        "source",
                                        "GENERAL_LEDGER");
                }

                return ResponseEntity.ok(
                                ApiResponse.safe(response));
        }

        // ============================================================
        // CASH FLOW
        // ============================================================

        @GetMapping("/cash-flow")
        public ResponseEntity<ApiResponse<Map<String, Object>>> cashFlow(
                        @RequestParam(required = false) String from,
                        @RequestParam(required = false) String to) {

                Long orgId = requireOrganizationId();

                DateRange range = resolveDateRange(from, to);

                Map<String, Object> report = accountingService.getCashFlow(
                                orgId,
                                range.from(),
                                range.to());

                return ResponseEntity.ok(
                                ApiResponse.safe(
                                                report != null
                                                                ? report
                                                                : Map.of()));
        }

        // ============================================================
        // BRANCH SUMMARY
        // ============================================================

        @GetMapping("/branch-summary")
        public ResponseEntity<ApiResponse<List<Map<String, Object>>>> branchSummary(
                        @RequestParam(required = false) String from,
                        @RequestParam(required = false) String to) {

                Long orgId = requireOrganizationId();

                DateRange range = resolveDateRange(from, to);

                List<Map<String, Object>> report = accountingService.getBranchSummary(
                                orgId,
                                range.from(),
                                range.to());

                return ResponseEntity.ok(
                                ApiResponse.safe(
                                                report != null
                                                                ? report
                                                                : List.of()));
        }

        // ============================================================
        // TRIAL BALANCE CSV EXPORT
        // ============================================================

        @GetMapping("/trial-balance/export")
        public ResponseEntity<String> exportTrialBalance() {

                Long orgId = requireOrganizationId();

                Map<String, Object> tb = accountingService.getTrialBalance(orgId);

                if (tb == null) {
                        tb = Map.of();
                }

                List<Map<String, Object>> rows = extractRows(
                                tb.get("accounts"));

                StringBuilder csv = new StringBuilder();

                csv.append(
                                "Code,Name,Type,Debit,Credit\n");

                for (Map<String, Object> row : rows) {

                        csv.append(
                                        csvField(
                                                        row.get("code")))
                                        .append(',');

                        csv.append(
                                        csvField(
                                                        row.get("name")))
                                        .append(',');

                        csv.append(
                                        csvField(
                                                        row.get("type")))
                                        .append(',');

                        csv.append(
                                        csvField(
                                                        row.get("debit")))
                                        .append(',');

                        csv.append(
                                        csvField(
                                                        row.get("credit")))
                                        .append('\n');
                }

                csv.append(
                                "TOTAL,,,")
                                .append(
                                                csvField(
                                                                tb.get("totalDebit")))
                                .append(',');

                csv.append(
                                csvField(
                                                tb.get("totalCredit")))
                                .append('\n');

                return csvResponse(
                                csv.toString(),
                                "trial-balance");
        }

        // ============================================================
        // BALANCE SHEET CSV EXPORT
        // ============================================================

        @GetMapping("/balance-sheet/export")
        public ResponseEntity<String> exportBalanceSheet() {

                Long orgId = requireOrganizationId();

                Map<String, Object> bs = accountingService.getBalanceSheet(orgId);

                if (bs == null) {
                        bs = Map.of();
                }

                StringBuilder csv = new StringBuilder();

                csv.append(
                                "Section,Code,Name,Balance\n");

                appendSection(
                                csv,
                                "Assets",
                                bs.get("assets"));

                appendSection(
                                csv,
                                "Liabilities",
                                bs.get("liabilities"));

                appendSection(
                                csv,
                                "Equity",
                                bs.get("equity"));

                csv.append(
                                "Total Assets,,,")
                                .append(
                                                csvField(
                                                                bs.get("totalAssets")))
                                .append('\n');

                csv.append(
                                "Total Liabilities,,,")
                                .append(
                                                csvField(
                                                                bs.get("totalLiabilities")))
                                .append('\n');

                csv.append(
                                "Total Equity,,,")
                                .append(
                                                csvField(
                                                                bs.get("totalEquity")))
                                .append('\n');

                return csvResponse(
                                csv.toString(),
                                "balance-sheet");
        }

        // ============================================================
        // P&L CSV EXPORT
        // ============================================================

        @GetMapping("/profit-and-loss/export")
        public ResponseEntity<String> exportProfitAndLoss(
                        @RequestParam(required = false) String from,
                        @RequestParam(required = false) String to) {

                Long orgId = requireOrganizationId();

                DateRange range = resolveDateRange(from, to);

                Map<String, Object> pnl = accountingService.getProfitAndLoss(
                                orgId,
                                range.from(),
                                range.to());

                StringBuilder csv = new StringBuilder();

                csv.append(
                                "Report,From,To\n");

                csv.append(
                                "Profit and Loss,")
                                .append(
                                                csvField(
                                                                range.from()))
                                .append(',');

                csv.append(
                                csvField(
                                                range.to()))
                                .append('\n');

                csv.append('\n');

                if (pnl != null) {

                        appendGenericMap(
                                        csv,
                                        pnl);
                }

                return csvResponse(
                                csv.toString(),
                                "profit-and-loss");
        }

        // ============================================================
        // EXCEL EXPORT
        // ============================================================

        @GetMapping("/trial-balance/export/excel")
        public ResponseEntity<byte[]> exportTrialBalanceExcel() {

                Long orgId = requireOrganizationId();

                Map<String, Object> tb = accountingService.getTrialBalance(orgId);

                if (tb == null) {
                        tb = Map.of();
                }

                List<Map<String, Object>> rows = extractRows(
                                tb.get("accounts"));

                byte[] bytes = exportService.toExcel(
                                "Trial Balance",
                                List.of(
                                                "code",
                                                "name",
                                                "type",
                                                "debit",
                                                "credit"),
                                rows);

                return fileResponse(
                                bytes,
                                "trial-balance.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        }

        @GetMapping("/trial-balance/export/pdf")
        public ResponseEntity<byte[]> exportTrialBalancePdf() {

                Organization organization = requireCurrentOrganization();

                Long orgId = organization.getId();

                Map<String, Object> tb = accountingService.getTrialBalance(orgId);

                if (tb == null) {
                        tb = Map.of();
                }

                List<Map<String, Object>> rows = extractRows(
                                tb.get("accounts"));

                byte[] bytes = exportService.toPdf(
                                "Trial Balance",
                                List.of(
                                                "code",
                                                "name",
                                                "type",
                                                "debit",
                                                "credit"),
                                rows,
                                organization.getName());

                return fileResponse(
                                bytes,
                                "trial-balance.pdf",
                                "application/pdf");
        }

        // ============================================================
        // BALANCE SHEET EXCEL
        // ============================================================

        @GetMapping("/balance-sheet/export/excel")
        public ResponseEntity<byte[]> exportBalanceSheetExcel() {

                Long orgId = requireOrganizationId();

                Map<String, Object> bs = accountingService.getBalanceSheet(orgId);

                if (bs == null) {
                        bs = Map.of();
                }

                byte[] bytes = exportService.toExcel(
                                "Balance Sheet",
                                List.of(
                                                "section",
                                                "code",
                                                "name",
                                                "balance"),
                                flattenBalanceSheet(bs));

                return fileResponse(
                                bytes,
                                "balance-sheet.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        }

        // ============================================================
        // BALANCE SHEET PDF
        // ============================================================

        @GetMapping("/balance-sheet/export/pdf")
        public ResponseEntity<byte[]> exportBalanceSheetPdf() {

                Organization organization = requireCurrentOrganization();

                Long orgId = organization.getId();

                Map<String, Object> bs = accountingService.getBalanceSheet(orgId);

                if (bs == null) {
                        bs = Map.of();
                }

                byte[] bytes = exportService.toPdf(
                                "Balance Sheet",
                                List.of(
                                                "section",
                                                "code",
                                                "name",
                                                "balance"),
                                flattenBalanceSheet(bs),
                                organization.getName());

                return fileResponse(
                                bytes,
                                "balance-sheet.pdf",
                                "application/pdf");
        }

        // ============================================================
        // P&L EXCEL
        // ============================================================

        @GetMapping("/profit-and-loss/export/excel")
        public ResponseEntity<byte[]> exportProfitAndLossExcel(
                        @RequestParam(required = false) String from,
                        @RequestParam(required = false) String to) {

                Organization organization = requireCurrentOrganization();

                DateRange range = resolveDateRange(from, to);

                Map<String, Object> pnl = accountingService.getProfitAndLoss(
                                organization.getId(),
                                range.from(),
                                range.to());

                List<Map<String, Object>> rows = flattenProfitAndLoss(
                                pnl);

                byte[] bytes = exportService.toExcel(
                                "Profit and Loss",
                                List.of(
                                                "section",
                                                "code",
                                                "name",
                                                "balance"),
                                rows);

                return fileResponse(
                                bytes,
                                "profit-and-loss.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        }

        // ============================================================
        // P&L PDF
        // ============================================================

        @GetMapping("/profit-and-loss/export/pdf")
        public ResponseEntity<byte[]> exportProfitAndLossPdf(
                        @RequestParam(required = false) String from,
                        @RequestParam(required = false) String to) {

                Organization organization = requireCurrentOrganization();

                DateRange range = resolveDateRange(from, to);

                Map<String, Object> pnl = accountingService.getProfitAndLoss(
                                organization.getId(),
                                range.from(),
                                range.to());

                List<Map<String, Object>> rows = flattenProfitAndLoss(
                                pnl);

                byte[] bytes = exportService.toPdf(
                                "Profit and Loss",
                                List.of(
                                                "section",
                                                "code",
                                                "name",
                                                "balance"),
                                rows,
                                organization.getName());

                return fileResponse(
                                bytes,
                                "profit-and-loss.pdf",
                                "application/pdf");
        }

        // ============================================================
        // HELPERS
        // ============================================================

        private String requireText(
                        String value,
                        String field) {

                if (value == null
                                || value.trim().isBlank()) {

                        throw new IllegalArgumentException(
                                        field + " is required");
                }

                return value.trim();
        }

        @SuppressWarnings("unchecked")
        private List<Map<String, Object>> extractRows(
                        Object value) {

                if (!(value instanceof List<?> list)) {
                        return List.of();
                }

                List<Map<String, Object>> result = new ArrayList<>();

                for (Object item : list) {

                        if (!(item instanceof Map<?, ?> raw)) {
                                continue;
                        }

                        Map<String, Object> row = new LinkedHashMap<>();

                        for (Map.Entry<?, ?> entry : raw.entrySet()) {

                                if (entry.getKey() != null) {

                                        row.put(
                                                        String.valueOf(entry.getKey()),
                                                        entry.getValue());
                                }
                        }

                        result.add(row);
                }

                return result;
        }

        private List<Map<String, Object>> flattenBalanceSheet(
                        Map<String, Object> bs) {

                List<Map<String, Object>> flat = new ArrayList<>();

                if (bs == null) {
                        return flat;
                }

                appendFinancialSection(
                                flat,
                                "Assets",
                                bs.get("assets"));

                appendFinancialSection(
                                flat,
                                "Liabilities",
                                bs.get("liabilities"));

                appendFinancialSection(
                                flat,
                                "Equity",
                                bs.get("equity"));

                return flat;
        }

        private List<Map<String, Object>> flattenProfitAndLoss(
                        Map<String, Object> pnl) {

                List<Map<String, Object>> flat = new ArrayList<>();

                if (pnl == null) {
                        return flat;
                }

                appendFinancialSection(
                                flat,
                                "Revenue",
                                pnl.get("revenue"));

                appendFinancialSection(
                                flat,
                                "Income",
                                pnl.get("income"));

                appendFinancialSection(
                                flat,
                                "Expenses",
                                pnl.get("expenses"));

                return flat;
        }

        private void appendFinancialSection(
                        List<Map<String, Object>> target,
                        String section,
                        Object rowsObject) {

                List<Map<String, Object>> rows = extractRows(rowsObject);

                for (Map<String, Object> row : rows) {

                        Map<String, Object> result = new LinkedHashMap<>();

                        result.put(
                                        "section",
                                        section);

                        result.put(
                                        "code",
                                        row.get("code"));

                        result.put(
                                        "name",
                                        row.get("name"));

                        Object balance = row.containsKey("balance")
                                        ? row.get("balance")
                                        : row.get("amount");

                        result.put(
                                        "balance",
                                        balance);

                        target.add(result);
                }
        }

        private void appendSection(
                        StringBuilder csv,
                        String section,
                        Object rowsObject) {

                List<Map<String, Object>> rows = extractRows(rowsObject);

                for (Map<String, Object> row : rows) {

                        csv.append(
                                        csvField(section))
                                        .append(',');

                        csv.append(
                                        csvField(
                                                        row.get("code")))
                                        .append(',');

                        csv.append(
                                        csvField(
                                                        row.get("name")))
                                        .append(',');

                        csv.append(
                                        csvField(
                                                        row.get("balance")))
                                        .append('\n');
                }
        }

        private void appendGenericMap(
                        StringBuilder csv,
                        Map<String, Object> map) {

                if (map == null) {
                        return;
                }

                for (Map.Entry<String, Object> entry : map.entrySet()) {

                        Object value = entry.getValue();

                        /*
                         * Nested report structures are represented as JSON-like
                         * values rather than silently discarded.
                         */
                        String printable;

                        if (value instanceof List<?> list) {

                                printable = list.toString();

                        } else if (value instanceof Map<?, ?> nested) {

                                printable = nested.toString();

                        } else {

                                printable = String.valueOf(value);
                        }

                        csv.append(
                                        csvField(
                                                        entry.getKey()))
                                        .append(',');

                        csv.append(
                                        csvField(
                                                        printable))
                                        .append('\n');
                }
        }

        /**
         * Prevents malformed CSV and basic spreadsheet formula injection.
         */
        private String csvField(
                        Object value) {

                if (value == null) {
                        return "";
                }

                String text = String.valueOf(value);

                /*
                 * Spreadsheet applications can interpret values beginning
                 * with these characters as formulas.
                 *
                 * Prefixing with a single quote keeps exported financial
                 * reports safe when opened in Excel/LibreOffice.
                 */
                if (!text.isBlank()) {

                        char first = text.charAt(0);

                        if (first == '='
                                        || first == '+'
                                        || first == '-'
                                        || first == '@') {

                                text = "'" + text;
                        }
                }

                if (text.contains(",")
                                || text.contains("\"")
                                || text.contains("\n")
                                || text.contains("\r")) {

                        return "\""
                                        + text.replace(
                                                        "\"",
                                                        "\"\"")
                                        + "\"";
                }

                return text;
        }

        private ResponseEntity<String> csvResponse(
                        String csv,
                        String filename) {

                byte[] bytes = csv.getBytes(
                                StandardCharsets.UTF_8);

                return ResponseEntity.ok()
                                .header(
                                                HttpHeaders.CONTENT_TYPE,
                                                "text/csv; charset=UTF-8")
                                .header(
                                                HttpHeaders.CONTENT_DISPOSITION,
                                                "attachment; filename=\""
                                                                + filename
                                                                + ".csv\"")
                                .body(
                                                new String(
                                                                bytes,
                                                                StandardCharsets.UTF_8));
        }

        private ResponseEntity<byte[]> fileResponse(
                        byte[] bytes,
                        String filename,
                        String contentType) {

                if (bytes == null) {
                        bytes = new byte[0];
                }

                return ResponseEntity.ok()
                                .header(
                                                HttpHeaders.CONTENT_TYPE,
                                                contentType)
                                .header(
                                                HttpHeaders.CONTENT_DISPOSITION,
                                                "attachment; filename=\""
                                                                + filename
                                                                + "\"")
                                .body(bytes);
        }
}