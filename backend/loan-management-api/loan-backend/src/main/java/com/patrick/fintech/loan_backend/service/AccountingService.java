package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Branch;
import com.patrick.fintech.loan_backend.model.ChartOfAccount;
import com.patrick.fintech.loan_backend.model.Expense;
import com.patrick.fintech.loan_backend.model.JournalEntry;
import com.patrick.fintech.loan_backend.model.JournalLine;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.repository.ChartOfAccountRepository;
import com.patrick.fintech.loan_backend.repository.JournalEntryRepository;
import com.patrick.fintech.loan_backend.repository.JournalLineRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.util.FinancialPolicy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountingService {

        private final ChartOfAccountRepository coaRepo;
        private final JournalEntryRepository journalRepo;
        private final JournalLineRepository lineRepo;
        private final LoanRepository loanRepo;

        // ============================================================
        // MONEY CONFIGURATION
        // ============================================================

        private static final int MONEY_SCALE = 2;

        private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;

        private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(
                        MONEY_SCALE,
                        MONEY_ROUNDING);

        /** Maximum immaterial reconciliation difference, in RWF. */
        private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

        // ============================================================
        // DEFAULT CHART OF ACCOUNTS
        // ============================================================

        private static final String[][] DEFAULT_ACCOUNTS = {

                        // ASSETS
                        { "1000", "Cash and Bank", "ASSET", "DEBIT" },
                        { "1100", "Loans Receivable", "ASSET", "DEBIT" },
                        { "1150", "Interest Receivable", "ASSET", "DEBIT" },
                        { "1160", "Management Fees Receivable", "ASSET", "DEBIT" },
                        { "1170", "Extension Fees Receivable", "ASSET", "DEBIT" },
                        { "1175", "Penalties Receivable", "ASSET", "DEBIT" },

                        // CONTRA ASSET
                        { "1200", "Loan Loss Reserve", "ASSET", "CREDIT" },

                        // LIABILITIES
                        { "2000", "Customer Deposits Payable", "LIABILITY", "CREDIT" },
                        { "2100", "Borrower Refunds Payable", "LIABILITY", "CREDIT" },

                        // EQUITY
                        { "3000", "Owner's Equity", "EQUITY", "CREDIT" },
                        { "3010", "Historical Portfolio Migration Equity", "EQUITY", "CREDIT" },

                        // INCOME
                        { "4000", "Interest Income", "INCOME", "CREDIT" },
                        { "4100", "Fee and Penalty Income", "INCOME", "CREDIT" },

                        // EXPENSES
                        { "5000", "Loan Loss Expense", "EXPENSE", "DEBIT" },
                        { "5100", "Operating Expenses", "EXPENSE", "DEBIT" },
                        { "5200", "Salaries and Wages", "EXPENSE", "DEBIT" },
                        { "5201", "Rent", "EXPENSE", "DEBIT" },
                        { "5202", "Utilities", "EXPENSE", "DEBIT" },
                        { "5203", "Internet", "EXPENSE", "DEBIT" },
                        { "5204", "Transport", "EXPENSE", "DEBIT" },
                        { "5205", "Fuel", "EXPENSE", "DEBIT" },
                        { "5206", "Office Supplies", "EXPENSE", "DEBIT" },
                        { "5207", "Bank Charges", "EXPENSE", "DEBIT" },
                        { "5208", "Insurance", "EXPENSE", "DEBIT" },
                        { "5209", "Marketing", "EXPENSE", "DEBIT" },
                        { "5210", "Legal Fees", "EXPENSE", "DEBIT" },
                        { "5211", "Audit Fees", "EXPENSE", "DEBIT" },
                        { "5212", "Depreciation", "EXPENSE", "DEBIT" },
                        { "5213", "Loan Recovery Expenses", "EXPENSE", "DEBIT" },
                        { "5214", "IT Expenses", "EXPENSE", "DEBIT" },
                        { "5215", "Other Operating Expenses", "EXPENSE", "DEBIT" }
        };

        // ============================================================
        // MONEY HELPERS
        // ============================================================

        private BigDecimal money(
                        BigDecimal value) {

                if (value == null) {
                        return ZERO;
                }

                return value.setScale(
                                MONEY_SCALE,
                                MONEY_ROUNDING);
        }

        private BigDecimal money(
                        Double value) {

                if (value == null) {
                        return ZERO;
                }

                return BigDecimal.valueOf(
                                value).setScale(
                                                MONEY_SCALE,
                                                MONEY_ROUNDING);
        }

        private BigDecimal money(
                        double value) {

                return BigDecimal.valueOf(
                                value).setScale(
                                                MONEY_SCALE,
                                                MONEY_ROUNDING);
        }

        private BigDecimal money(
                        Number value) {

                if (value == null) {
                        return ZERO;
                }

                if (value instanceof BigDecimal) {
                        return money(
                                        (BigDecimal) value);
                }

                return BigDecimal.valueOf(
                                value.doubleValue()).setScale(
                                                MONEY_SCALE,
                                                MONEY_ROUNDING);
        }

        private BigDecimal maxZero(
                        BigDecimal value) {

                BigDecimal normalized = money(value);

                return normalized.compareTo(
                                ZERO) < 0
                                                ? ZERO
                                                : normalized;
        }

        private boolean isPositive(
                        BigDecimal value) {

                return money(value).compareTo(
                                ZERO) > 0;
        }

        private BigDecimal normalize(
                        BigDecimal value) {

                return money(value);
        }

        // ============================================================
        // VALIDATION
        // ============================================================

        private void requireOrganization(
                        Organization org) {

                if (org == null
                                || org.getId() == null) {

                        throw new IllegalArgumentException(
                                        "Organization is required");
                }
        }

        private void requireOrganizationId(
                        Long orgId) {

                if (orgId == null) {

                        throw new IllegalArgumentException(
                                        "Organization ID is required");
                }
        }

        private void requireAccountId(
                        Long accountId) {

                if (accountId == null) {

                        throw new IllegalArgumentException(
                                        "Account ID is required");
                }
        }

        private void validateDateRange(
                        LocalDate from,
                        LocalDate to) {

                if (from == null) {

                        throw new IllegalArgumentException(
                                        "Start date is required");
                }

                if (to == null) {

                        throw new IllegalArgumentException(
                                        "End date is required");
                }

                if (to.isBefore(from)) {

                        throw new IllegalArgumentException(
                                        "End date cannot be before start date");
                }
        }

        // ============================================================
        // CHART OF ACCOUNTS
        // ============================================================

        @Transactional
        public void ensureChartOfAccounts(
                        Organization org) {

                requireOrganization(org);

                List<ChartOfAccount> existing = coaRepo.findByOrganization_IdOrderByCodeAsc(
                                org.getId());

                Set<String> existingCodes = new HashSet<>();

                if (existing != null) {

                        for (ChartOfAccount account : existing) {

                                if (account != null
                                                && account.getCode() != null) {

                                        existingCodes.add(
                                                        account.getCode().trim());
                                }
                        }
                }

                for (String[] definition : DEFAULT_ACCOUNTS) {

                        String code = definition[0];

                        if (existingCodes.contains(
                                        code)) {

                                continue;
                        }

                        coaRepo.save(
                                        ChartOfAccount.builder()
                                                        .organization(org)
                                                        .code(code)
                                                        .name(definition[1])
                                                        .type(
                                                                        ChartOfAccount.AccountType
                                                                                        .valueOf(
                                                                                                        definition[2]))
                                                        .normalBalance(
                                                                        ChartOfAccount.NormalBalance
                                                                                        .valueOf(
                                                                                                        definition[3]))
                                                        .active(true)
                                                        .build());
                }

                log.info(
                                "Chart of accounts verified for organization {}",
                                org.getId());
        }

        private ChartOfAccount account(
                        Organization org,
                        String code) {

                requireOrganization(org);

                if (code == null
                                || code.isBlank()) {

                        throw new IllegalArgumentException(
                                        "Account code is required");
                }

                String normalizedCode = code.trim();

                ChartOfAccount account = coaRepo
                                .findByOrganization_IdAndCode(
                                                org.getId(),
                                                normalizedCode)
                                .orElseThrow(
                                                () -> new IllegalStateException(
                                                                "Chart of accounts is not configured " +
                                                                                "for organization " +
                                                                                org.getId() +
                                                                                " (missing account " +
                                                                                normalizedCode +
                                                                                ")"));

                if (!Boolean.TRUE.equals(
                                account.getActive())) {

                        throw new IllegalStateException(
                                        "GL account "
                                                        + normalizedCode
                                                        + " is inactive");
                }

                return account;
        }

        private void validateAccountOwnership(
                        Organization org,
                        ChartOfAccount account) {

                requireOrganization(org);

                if (account == null
                                || account.getId() == null) {

                        throw new IllegalArgumentException(
                                        "Journal line account is required");
                }

                if (account.getOrganization() == null
                                || account
                                                .getOrganization()
                                                .getId() == null) {

                        throw new IllegalStateException(
                                        "GL account has no organization");
                }

                if (!org.getId().equals(
                                account
                                                .getOrganization()
                                                .getId())) {

                        throw new IllegalStateException(
                                        "GL account "
                                                        + account.getId()
                                                        + " does not belong to organization "
                                                        + org.getId());
                }

                if (!Boolean.TRUE.equals(
                                account.getActive())) {

                        throw new IllegalStateException(
                                        "GL account "
                                                        + account.getCode()
                                                        + " is inactive");
                }
        }

        private void validateBranchOwnership(
                        Organization org,
                        Branch branch) {

                if (branch == null) {
                        return;
                }

                requireOrganization(org);

                if (branch.getId() == null
                                || branch.getOrganization() == null
                                || branch.getOrganization()
                                                .getId() == null) {

                        throw new IllegalStateException(
                                        "Branch must belong to an organization");
                }

                if (!org.getId().equals(
                                branch
                                                .getOrganization()
                                                .getId())) {

                        throw new IllegalStateException(
                                        "Branch "
                                                        + branch.getId()
                                                        + " does not belong to organization "
                                                        + org.getId());
                }
        }

        @Transactional
        public ChartOfAccount getEquityAccount(
                        Organization org) {

                ensureChartOfAccounts(org);

                return account(
                                org,
                                "3000");
        }

        @Transactional
        public ChartOfAccount createAccount(
                        Organization org,
                        String code,
                        String name,
                        ChartOfAccount.AccountType type,
                        ChartOfAccount.NormalBalance normalBalance) {

                requireOrganization(org);

                if (code == null
                                || code.isBlank()) {

                        throw new IllegalArgumentException(
                                        "Account code is required");
                }

                if (name == null
                                || name.isBlank()) {

                        throw new IllegalArgumentException(
                                        "Account name is required");
                }

                if (type == null) {

                        throw new IllegalArgumentException(
                                        "Account type is required");
                }

                if (normalBalance == null) {

                        throw new IllegalArgumentException(
                                        "Normal balance is required");
                }

                String normalizedCode = code.trim();

                if (coaRepo
                                .existsByOrganization_IdAndCode(
                                                org.getId(),
                                                normalizedCode)) {

                        throw new IllegalArgumentException(
                                        "Account code "
                                                        + normalizedCode
                                                        + " already exists");
                }

                return coaRepo.save(
                                ChartOfAccount.builder()
                                                .organization(org)
                                                .code(normalizedCode)
                                                .name(name.trim())
                                                .type(type)
                                                .normalBalance(normalBalance)
                                                .active(true)
                                                .build());
        }

        @Transactional
        public ChartOfAccount updateAccount(
                        Long orgId,
                        Long accountId,
                        String name,
                        Boolean active) {

                requireOrganizationId(orgId);
                requireAccountId(accountId);

                ChartOfAccount account = coaRepo.findByIdAndOrganization_Id(
                                accountId,
                                orgId).orElseThrow(
                                                () -> new IllegalArgumentException(
                                                                "Account not found: "
                                                                                + accountId));

                if (name != null
                                && !name.isBlank()) {

                        account.setName(
                                        name.trim());
                }

                if (active != null) {

                        account.setActive(
                                        active);
                }

                return coaRepo.save(
                                account);
        }

        // ============================================================
        // GENERIC JOURNAL POSTING
        // ============================================================

        @Transactional
        public JournalEntry post(
                        Organization org,
                        String sourceType,
                        String sourceId,
                        String reference,
                        String description,
                        List<JournalLine> lines) {

                return post(
                                org,
                                null,
                                sourceType,
                                sourceId,
                                reference,
                                description,
                                lines);
        }

        @Transactional
        public JournalEntry post(
                        Organization org,
                        Branch branch,
                        String sourceType,
                        String sourceId,
                        String reference,
                        String description,
                        List<JournalLine> lines) {

                return postAtDate(
                                org,
                                branch,
                                sourceType,
                                sourceId,
                                reference,
                                description,
                                LocalDate.now(),
                                lines);
        }

        @Transactional
        public JournalEntry postAtDate(
                        Organization org,
                        Branch branch,
                        String sourceType,
                        String sourceId,
                        String reference,
                        String description,
                        LocalDate entryDate,
                        List<JournalLine> lines) {

                requireOrganization(org);

                validateBranchOwnership(
                                org,
                                branch);

                if (sourceType == null
                                || sourceType.isBlank()) {

                        throw new IllegalArgumentException(
                                        "Journal source type is required");
                }

                if (sourceId == null
                                || sourceId.isBlank()) {

                        throw new IllegalArgumentException(
                                        "Journal source ID is required");
                }

                if (reference == null
                                || reference.isBlank()) {

                        throw new IllegalArgumentException(
                                        "Journal reference is required");
                }

                if (description == null
                                || description.isBlank()) {

                        throw new IllegalArgumentException(
                                        "Journal description is required");
                }

                if (lines == null
                                || lines.isEmpty()) {

                        throw new IllegalArgumentException(
                                        "Journal entry must contain at least one line");
                }

                String normalizedSourceType = sourceType.trim();

                String normalizedSourceId = sourceId.trim();

                if (!"REVERSAL".equals(
                                normalizedSourceType)) {

                        JournalEntry existing = journalRepo
                                        .findFirstByOrganization_IdAndSourceTypeAndSourceId(
                                                        org.getId(),
                                                        normalizedSourceType,
                                                        normalizedSourceId)
                                        .orElse(null);

                        if (existing != null) {

                                log.warn(
                                                "Accounting event already posted. " +
                                                                "Returning existing journal entry {} for {}:{}",
                                                existing.getId(),
                                                normalizedSourceType,
                                                normalizedSourceId);

                                return existing;
                        }
                }

                BigDecimal totalDebit = ZERO;

                BigDecimal totalCredit = ZERO;

                for (JournalLine line : lines) {

                        if (line == null) {

                                throw new IllegalArgumentException(
                                                "Journal entry contains a null line");
                        }

                        ChartOfAccount lineAccount = line.getAccount();

                        validateAccountOwnership(
                                        org,
                                        lineAccount);

                        BigDecimal debit = money(
                                        line.getDebitDecimal());

                        BigDecimal credit = money(
                                        line.getCreditDecimal());

                        if (debit.compareTo(ZERO) < 0
                                        || credit.compareTo(ZERO) < 0) {

                                throw new IllegalArgumentException(
                                                "Debit and credit amounts cannot be negative");
                        }

                        if (debit.compareTo(ZERO) > 0
                                        && credit.compareTo(ZERO) > 0) {

                                throw new IllegalArgumentException(
                                                "A journal line cannot contain both debit and credit");
                        }

                        if (debit.compareTo(ZERO) == 0
                                        && credit.compareTo(ZERO) == 0) {

                                throw new IllegalArgumentException(
                                                "A journal line must contain a debit or credit amount");
                        }

                        line.setDebit(
                                        debit);

                        line.setCredit(
                                        credit);

                        totalDebit = totalDebit.add(
                                        debit);

                        totalCredit = totalCredit.add(
                                        credit);
                }

                totalDebit = money(totalDebit);

                totalCredit = money(totalCredit);

                if (totalDebit.compareTo(
                                totalCredit) != 0) {

                        throw new IllegalStateException(
                                        "Journal entry does not balance: " +
                                                        "debits " +
                                                        totalDebit.toPlainString() +
                                                        " != credits " +
                                                        totalCredit.toPlainString() +
                                                        " (" +
                                                        description +
                                                        ")");
                }

                JournalEntry entry = JournalEntry.builder()
                                .organization(org)
                                .branch(branch)
                                .entryDate(
                                                entryDate != null ? entryDate : LocalDate.now())
                                .sourceType(
                                                normalizedSourceType)
                                .sourceId(
                                                normalizedSourceId)
                                .reference(
                                                reference.trim())
                                .description(
                                                description.trim())
                                .createdBy(
                                                "SYSTEM")
                                .reversed(false)
                                .build();

                entry = journalRepo.save(
                                entry);

                for (JournalLine line : lines) {

                        line.setJournalEntry(
                                        entry);

                        lineRepo.save(
                                        line);
                }

                return entry;
        }

        // ============================================================
        // LEGACY LOAN OPENING BALANCE
        // ============================================================

        /**
         * Records the opening financial position of a loan imported from
         * a historical portfolio. This is intentionally NOT a disbursement
         * cash journal: historical cash movements and historical income
         * are not replayed because that would double-count transactions
         * that already happened before migration.
         *
         * Opening balances:
         * DR 1100 Loans Receivable outstanding principal
         * DR 1150 Interest Receivable unpaid historical interest
         * DR 1160 Management Fees Receivable unpaid historical fee
         * CR 3000 Owner's Equity balancing migration opening
         *
         * Historical amounts already paid remain stored on the Loan as
         * opening cumulative totals and will not be reposted to cash/income.
         */
        @Transactional
        public JournalEntry postHistoricalLoanOpening(
                        Loan loan) {

                if (loan == null) {
                        throw new IllegalArgumentException("Loan is required");
                }

                Organization org = loan.getOrganization();
                requireOrganization(org);
                ensureChartOfAccounts(org);

                BigDecimal principalOutstanding = money(
                                loan.getOutstandingBalanceDecimal()).max(ZERO);
                BigDecimal interestOutstanding = money(
                                loan.getInterestOutstandingDecimal()).max(ZERO);
                BigDecimal managementOutstanding = money(
                                loan.getManagementFeeOutstandingDecimal()).max(ZERO);
                BigDecimal extensionOutstanding = money(
                                loan.getExtensionFeeOutstandingDecimal()).max(ZERO);

                BigDecimal penaltiesAssessed = money(
                                loan.getPenaltiesAssessedDecimal()).max(ZERO);
                BigDecimal penaltiesPaid = money(
                                loan.getPenaltiesPaidDecimal()).max(ZERO);
                BigDecimal penaltyOutstanding = penaltiesAssessed
                                .subtract(penaltiesPaid)
                                .max(ZERO);
                penaltyOutstanding = money(penaltyOutstanding);

                /*
                 * IMPORTANT:
                 *
                 * A migrated loan's opening position must contain the COMPLETE
                 * outstanding receivable position, not only principal,
                 * interest and management fees. Extension-fee and penalty
                 * receivables are part of the operational sub-ledger and must
                 * therefore be opened in GL 1170 and 1175 as well.
                 *
                 * Historical cash movements are deliberately NOT replayed.
                 */
                BigDecimal openingBalance = money(
                                principalOutstanding
                                                .add(interestOutstanding)
                                                .add(managementOutstanding)
                                                .add(extensionOutstanding)
                                                .add(penaltyOutstanding));

                if (openingBalance.compareTo(ZERO) <= 0) {
                        log.info(
                                        "Skipping legacy accounting opening balance for loanId={} because no receivable balance remains",
                                        loan.getId());
                        return null;
                }

                String sourceId = "LOAN:" + loan.getId();
                String reference = loan.getReferenceNumber() != null
                                ? loan.getReferenceNumber()
                                : sourceId;

                List<JournalLine> lines = new ArrayList<>();

                if (principalOutstanding.compareTo(ZERO) > 0) {
                        lines.add(
                                        JournalLine.builder()
                                                        .account(account(org, "1100"))
                                                        .debit(principalOutstanding)
                                                        .credit(ZERO)
                                                        .description(
                                                                        "Opening loan principal receivable — "
                                                                                        + reference)
                                                        .build());
                }

                if (interestOutstanding.compareTo(ZERO) > 0) {
                        lines.add(
                                        JournalLine.builder()
                                                        .account(account(org, "1150"))
                                                        .debit(interestOutstanding)
                                                        .credit(ZERO)
                                                        .description(
                                                                        "Opening historical interest receivable — "
                                                                                        + reference)
                                                        .build());
                }

                if (managementOutstanding.compareTo(ZERO) > 0) {
                        lines.add(
                                        JournalLine.builder()
                                                        .account(account(org, "1160"))
                                                        .debit(managementOutstanding)
                                                        .credit(ZERO)
                                                        .description(
                                                                        "Opening historical management fee receivable — "
                                                                                        + reference)
                                                        .build());
                }

                if (extensionOutstanding.compareTo(ZERO) > 0) {
                        lines.add(
                                        JournalLine.builder()
                                                        .account(account(org, "1170"))
                                                        .debit(extensionOutstanding)
                                                        .credit(ZERO)
                                                        .description(
                                                                        "Opening historical extension-fee receivable — "
                                                                                        + reference)
                                                        .build());
                }

                if (penaltyOutstanding.compareTo(ZERO) > 0) {
                        lines.add(
                                        JournalLine.builder()
                                                        .account(account(org, "1175"))
                                                        .debit(penaltyOutstanding)
                                                        .credit(ZERO)
                                                        .description(
                                                                        "Opening historical penalty receivable — "
                                                                                        + reference)
                                                        .build());
                }

                lines.add(
                                JournalLine.builder()
                                                .account(account(org, "3010"))
                                                .debit(ZERO)
                                                .credit(openingBalance)
                                                .description(
                                                                "Historical loan migration opening balance — "
                                                                                + reference)
                                                .build());

                return postAtDate(
                                org,
                                loan.getBranch(),
                                "LEGACY_LOAN_OPENING",
                                sourceId,
                                reference,
                                "Opening financial position for migrated historical loan "
                                                + reference,
                                loan.getDisbursedAt() != null
                                                ? loan.getDisbursedAt().toLocalDate()
                                                : (loan.getStartDate() != null
                                                                ? loan.getStartDate()
                                                                : LocalDate.now()),
                                lines);
        }

        /**
         * Idempotently repairs accounting opening journals for already-imported
         * historical loans. Existing journals are returned by the generic
         * source-event idempotency check, so this is safe to run repeatedly.
         */
        @Transactional
        public int reconcileLegacyLoanOpeningBalances(
                        List<Loan> importedLoans) {

                if (importedLoans == null || importedLoans.isEmpty()) {
                        return 0;
                }

                int repaired = 0;

                for (Loan loan : importedLoans) {
                        if (loan == null
                                        || loan.getId() == null
                                        || (!Boolean.TRUE.equals(loan.getImported())
                                                        && loan.getImportBatchId() == null)) {
                                continue;
                        }

                        /*
                         * First make sure the historical opening event exists.
                         * postAtDate() is source-idempotent, so an existing
                         * correct opening journal is never duplicated.
                         */
                        JournalEntry opening = postHistoricalLoanOpening(loan);
                        if (opening != null) {
                                repaired++;
                        }

                        /*
                         * Existing production data may already contain an opening
                         * journal created by an older version of the accounting
                         * code. Merely checking whether the journal exists is not
                         * enough: its receivable values may differ from the
                         * current imported loan opening state.
                         *
                         * We therefore calculate the per-loan GL balance for each
                         * operational receivable and create an explicit,
                         * auditable migration-equity correction only when a
                         * difference remains.
                         *
                         * This does NOT rewrite or delete historical journals.
                         * It also does NOT replay historical cash movements.
                         */
                        Map<String, BigDecimal> expected = new LinkedHashMap<>();
                        expected.put("1100", money(
                                        loan.getOutstandingBalanceDecimal()).max(ZERO));
                        expected.put("1150", money(
                                        loan.getInterestOutstandingDecimal()).max(ZERO));
                        expected.put("1160", money(
                                        loan.getManagementFeeOutstandingDecimal()).max(ZERO));
                        expected.put("1170", money(
                                        loan.getExtensionFeeOutstandingDecimal()).max(ZERO));

                        BigDecimal penaltyOutstanding = money(
                                        loan.getPenaltiesAssessedDecimal())
                                        .subtract(money(loan.getPenaltiesPaidDecimal()))
                                        .max(ZERO);
                        expected.put("1175", money(penaltyOutstanding));

                        Map<String, BigDecimal> deltas = new LinkedHashMap<>();

                        for (Map.Entry<String, BigDecimal> item : expected.entrySet()) {
                                String code = item.getKey();
                                BigDecimal expectedBalance = money(item.getValue());

                                ChartOfAccount receivable = coaRepo
                                                .findByOrganization_IdAndCode(
                                                                loan.getOrganization().getId(),
                                                                code)
                                                .orElse(null);

                                if (receivable == null) {
                                        throw new IllegalStateException(
                                                        "Required accounting account "
                                                                        + code
                                                                        + " is not configured for organization "
                                                                        + loan.getOrganization().getId());
                                }

                                List<JournalLine> lines = lineRepo.findReceivableLinesForLoanIdentity(
                                                receivable.getId(),
                                                loan.getOrganization().getId(),
                                                loan.getId(),
                                                loan.getReferenceNumber());

                                BigDecimal glBalance = ZERO;

                                if (lines != null) {
                                        for (JournalLine line : lines) {
                                                if (line == null) {
                                                        continue;
                                                }

                                                JournalEntry entry = line.getJournalEntry();

                                                if (entry == null
                                                                || Boolean.TRUE.equals(entry.getReversed())) {
                                                        continue;
                                                }

                                                glBalance = glBalance
                                                                .add(money(line.getDebitDecimal()))
                                                                .subtract(money(line.getCreditDecimal()));
                                        }
                                }

                                // Preserve the signed GL balance. A negative receivable
                                // is an accounting exception and must not be silently
                                // collapsed to zero before reconciliation.
                                glBalance = money(glBalance);
                                BigDecimal delta = money(expectedBalance.subtract(glBalance));

                                // Both directions are reconciled through an explicit,
                                // auditable migration-equity journal. A positive delta means
                                // the operational imported receivable is larger than GL; a
                                // negative delta means the GL receivable is overstated.
                                // Historical journals are never edited or deleted.
                                if (delta.abs().compareTo(TOLERANCE) >= 0) {
                                        deltas.put(code, delta);
                                }
                        }

                        if (!deltas.isEmpty()) {
                                repaired += postLegacyReconciliationAdjustment(
                                                loan,
                                                deltas);
                        }
                }

                return repaired;
        }

        /**
         * Synchronizes the operational loan receivable state upward when the
         * authoritative GL already contains a larger active receivable balance.
         *
         * This is intentionally conservative:
         * - it never decreases a loan balance automatically;
         * - it never creates a journal;
         * - it never edits or deletes accounting history;
         * - imported-loan opening differences are repaired by the legacy
         * reconciliation method before this synchronization runs.
         *
         * This closes the historical gap where scheduled contractual accruals
         * were posted to GL 1150/1160 but the Loan operational fields were not
         * advanced at the same time.
         */
        @Transactional
        public OperationalReceivableSyncResult synchronizeOperationalReceivables(
                        Long organizationId) {

                if (organizationId == null || organizationId <= 0) {
                        throw new IllegalArgumentException(
                                        "Organization ID must be positive.");
                }

                List<Loan> loans = loanRepo.findByOrganization_Id(organizationId);
                if (loans == null || loans.isEmpty()) {
                        return new OperationalReceivableSyncResult(0, 0, List.of());
                }

                Map<String, ChartOfAccount> receivableAccounts = new LinkedHashMap<>();
                for (String code : List.of("1100", "1150", "1160", "1170", "1175")) {
                        ChartOfAccount account = coaRepo
                                        .findByOrganization_IdAndCode(organizationId, code)
                                        .orElse(null);
                        if (account != null) {
                                receivableAccounts.put(code, account);
                        }
                }

                List<String> unresolved = new ArrayList<>();
                int updatedLoans = 0;
                int updatedComponents = 0;

                for (Loan loan : loans) {
                        if (loan == null
                                        || loan.getId() == null
                                        || loan.getOrganization() == null
                                        || !organizationId.equals(loan.getOrganization().getId())
                                        || loan.getReferenceNumber() == null
                                        || loan.getReferenceNumber().isBlank()) {
                                continue;
                        }

                        boolean loanUpdated = false;

                        for (Map.Entry<String, ChartOfAccount> accountEntry : receivableAccounts.entrySet()) {
                                String code = accountEntry.getKey();
                                ChartOfAccount account = accountEntry.getValue();

                                List<JournalLine> lines = lineRepo.findReceivableLinesForLoanIdentity(
                                                account.getId(),
                                                organizationId,
                                                loan.getId(),
                                                loan.getReferenceNumber());

                                BigDecimal glBalance = ZERO;

                                if (lines != null) {
                                        for (JournalLine line : lines) {
                                                if (line == null || line.getJournalEntry() == null) {
                                                        continue;
                                                }

                                                JournalEntry entry = line.getJournalEntry();

                                                if (Boolean.TRUE.equals(entry.getReversed())) {
                                                        continue;
                                                }

                                                // A legacy-imported loan must never acquire a
                                                // historical LOAN_DISBURSEMENT receivable from this
                                                // synchronization. Its opening position is represented
                                                // by LEGACY_LOAN_OPENING / RECONCILIATION.
                                                if (Boolean.TRUE.equals(loan.getImported())
                                                                && "LOAN_DISBURSEMENT".equals(entry.getSourceType())) {
                                                        continue;
                                                }

                                                glBalance = glBalance
                                                                .add(money(line.getDebitDecimal()))
                                                                .subtract(money(line.getCreditDecimal()));
                                        }
                                }

                                // Do not clamp a negative GL balance to zero. A negative
                                // receivable normally means historical payments/credits
                                // exceed the posted receivable and must remain visible as
                                // an unresolved reconciliation difference.
                                glBalance = money(glBalance);

                                BigDecimal operational;

                                switch (code) {
                                        case "1100" ->
                                                operational = money(loan.getOutstandingBalanceDecimal()).max(ZERO);
                                        case "1150" ->
                                                operational = money(loan.getInterestOutstandingDecimal()).max(ZERO);
                                        case "1160" -> operational = money(loan.getManagementFeeOutstandingDecimal())
                                                        .max(ZERO);
                                        case "1170" ->
                                                operational = money(loan.getExtensionFeeOutstandingDecimal()).max(ZERO);
                                        case "1175" -> operational = money(loan.getPenaltiesAssessedDecimal())
                                                        .subtract(money(loan.getPenaltiesPaidDecimal()))
                                                        .max(ZERO);
                                        default -> operational = ZERO;
                                }

                                BigDecimal difference = money(glBalance.subtract(operational));

                                if (difference.abs().compareTo(TOLERANCE) > 0) {
                                        /*
                                         * BANK-GRADE CONTROL:
                                         *
                                         * Reconciliation is a control, not a
                                         * mechanism for rewriting the loan
                                         * sub-ledger from the GL.
                                         *
                                         * The previous implementation silently
                                         * copied a larger GL balance into the
                                         * Loan record. That can make the
                                         * dashboard/portfolio appear to agree
                                         * while concealing the accounting
                                         * defect.
                                         *
                                         * We therefore report the exact
                                         * difference and leave both ledgers
                                         * untouched. A correction must be
                                         * posted from the identified source
                                         * transaction/opening journal.
                                         */
                                        unresolved.add(
                                                        loan.getReferenceNumber()
                                                                        + " / GL " + code
                                                                        + " difference="
                                                                        + difference.toPlainString()
                                                                        + ". No automatic operational balance change was applied.");
                                }
                        }

                        if (loanUpdated) {
                                updatedLoans++;
                        }
                }

                return new OperationalReceivableSyncResult(
                                updatedLoans,
                                updatedComponents,
                                unresolved);
        }

        /**
         * Result of the conservative operational-sub-ledger synchronization.
         */
        public record OperationalReceivableSyncResult(
                        int updatedLoans,
                        int updatedComponents,
                        List<String> unresolved) {
        }

        /**
         * Posts a controlled, auditable adjustment for an imported loan whose
         * existing legacy opening journals do not agree with the current
         * operational opening balances.
         *
         * Positive delta:
         * DR receivable
         * CR migration equity
         *
         * Negative delta:
         * CR receivable
         * DR migration equity
         *
         * The correction is source-idempotent and is dated on the current
         * accounting date. Historical journals are never edited or deleted.
         */
        private int postLegacyReconciliationAdjustment(
                        Loan loan,
                        Map<String, BigDecimal> deltas) {

                if (loan == null
                                || loan.getOrganization() == null
                                || loan.getOrganization().getId() == null
                                || deltas == null
                                || deltas.isEmpty()) {
                        return 0;
                }

                Organization org = loan.getOrganization();
                requireOrganization(org);
                ensureChartOfAccounts(org);

                String fingerprint = sha256(
                                loan.getId()
                                                + "|"
                                                + deltas.entrySet().stream()
                                                                .map(entry -> entry.getKey()
                                                                                + "="
                                                                                + money(entry.getValue())
                                                                                                .toPlainString())
                                                                .reduce((a, b) -> a + "|" + b)
                                                                .orElse(""));

                String sourceId = "LOAN:" + loan.getId() + ":" + fingerprint.substring(0, 24);
                String reference = loan.getReferenceNumber() != null
                                ? loan.getReferenceNumber()
                                : "LOAN:" + loan.getId();

                List<JournalLine> lines = new ArrayList<>();
                BigDecimal totalAdjustment = ZERO;

                for (Map.Entry<String, BigDecimal> item : deltas.entrySet()) {
                        BigDecimal delta = money(item.getValue());

                        if (delta.compareTo(ZERO) > 0) {
                                lines.add(
                                                JournalLine.builder()
                                                                .account(account(org, item.getKey()))
                                                                .debit(delta)
                                                                .credit(ZERO)
                                                                .description(
                                                                                "Legacy reconciliation increase — "
                                                                                                + item.getKey()
                                                                                                + " — "
                                                                                                + reference)
                                                                .build());
                                totalAdjustment = totalAdjustment.add(delta);
                        } else if (delta.compareTo(ZERO) < 0) {
                                BigDecimal absolute = delta.abs();
                                lines.add(
                                                JournalLine.builder()
                                                                .account(account(org, item.getKey()))
                                                                .debit(ZERO)
                                                                .credit(absolute)
                                                                .description(
                                                                                "Legacy reconciliation decrease — "
                                                                                                + item.getKey()
                                                                                                + " — "
                                                                                                + reference)
                                                                .build());
                                totalAdjustment = totalAdjustment.subtract(absolute);
                        }
                }

                totalAdjustment = money(totalAdjustment);

                if (totalAdjustment.compareTo(ZERO) > 0) {
                        lines.add(
                                        JournalLine.builder()
                                                        .account(account(org, "3010"))
                                                        .debit(ZERO)
                                                        .credit(totalAdjustment)
                                                        .description(
                                                                        "Legacy portfolio reconciliation equity adjustment — "
                                                                                        + reference)
                                                        .build());
                } else if (totalAdjustment.compareTo(ZERO) < 0) {
                        lines.add(
                                        JournalLine.builder()
                                                        .account(account(org, "3010"))
                                                        .debit(totalAdjustment.abs())
                                                        .credit(ZERO)
                                                        .description(
                                                                        "Legacy portfolio reconciliation equity adjustment — "
                                                                                        + reference)
                                                        .build());
                }

                if (lines.isEmpty()) {
                        return 0;
                }

                postAtDate(
                                org,
                                loan.getBranch(),
                                "LEGACY_LOAN_RECONCILIATION",
                                sourceId,
                                reference,
                                "Controlled reconciliation adjustment for migrated loan "
                                                + reference,
                                LocalDate.now(),
                                lines);

                log.warn(
                                "Legacy loan accounting reconciliation adjustment posted. "
                                                + "organizationId={}, loanId={}, reference={}, deltas={}",
                                org.getId(),
                                loan.getId(),
                                reference,
                                deltas);

                return 1;
        }

        private String sha256(String value) {
                try {
                        MessageDigest digest = MessageDigest.getInstance("SHA-256");
                        byte[] hash = digest.digest(
                                        value.getBytes(StandardCharsets.UTF_8));
                        StringBuilder hex = new StringBuilder(hash.length * 2);
                        for (byte b : hash) {
                                hex.append(String.format("%02x", b));
                        }
                        return hex.toString();
                } catch (NoSuchAlgorithmException ex) {
                        throw new IllegalStateException(
                                        "SHA-256 is not available in this runtime",
                                        ex);
                }
        }

        // ============================================================
        // LOAN DISBURSEMENT
        // ============================================================

        @Transactional
        public void postDisbursement(
                        Loan loan) {

                if (loan == null) {

                        throw new IllegalArgumentException(
                                        "Loan is required");
                }

                if (loan.getId() == null) {

                        throw new IllegalArgumentException(
                                        "Loan ID is required");
                }

                Organization org = loan.getOrganization();

                requireOrganization(org);

                ensureChartOfAccounts(org);

                // --------------------------------------------------------
                // GROSS PRINCIPAL
                // --------------------------------------------------------

                BigDecimal grossPrincipal = money(
                                loan.getAmountDecimal());

                if (grossPrincipal.compareTo(
                                ZERO) <= 0) {

                        throw new IllegalArgumentException(
                                        "Loan disbursement amount must be greater than zero");
                }

                // --------------------------------------------------------
                // PROCESSING FEE
                // --------------------------------------------------------

                BigDecimal applicationFee = money(
                                loan.getApplicationFeeDecimal());

                if (applicationFee.compareTo(
                                ZERO) < 0) {

                        throw new IllegalStateException(
                                        "Processing fee cannot be negative");
                }

                if (applicationFee.compareTo(
                                grossPrincipal) > 0) {

                        throw new IllegalStateException(
                                        "Processing fee cannot exceed the gross loan principal");
                }

                // --------------------------------------------------------
                // EXPECTED NET CASH
                // --------------------------------------------------------

                BigDecimal netCashDisbursed = money(
                                grossPrincipal.subtract(
                                                applicationFee));

                // The application fee is collected exactly once at disbursement.
                // Keep the operational loan state synchronized with the journal:
                // gross principal remains the receivable, while the borrower
                // receives principal minus the one-time application fee.
                loan.setApplicationFee(applicationFee);
                loan.setApplicationFeePaid(applicationFee);
                loan.setNetDisbursedAmount(netCashDisbursed);

                if (netCashDisbursed.compareTo(
                                ZERO) <= 0) {

                        throw new IllegalStateException(
                                        "Net loan disbursement must be greater than zero");
                }

                String sourceId = String.valueOf(
                                loan.getId());

                String reference = loan.getReferenceNumber() != null
                                && !loan.getReferenceNumber().isBlank()
                                                ? loan.getReferenceNumber().trim()
                                                : "LOAN-" + loan.getId();

                // --------------------------------------------------------
                // IDEMPOTENCY
                // --------------------------------------------------------

                JournalEntry existing = journalRepo
                                .findFirstByOrganization_IdAndSourceTypeAndSourceId(
                                                org.getId(),
                                                "LOAN_DISBURSEMENT",
                                                sourceId)
                                .orElse(null);

                if (existing != null) {

                        log.info(
                                        "Loan {} disbursement already posted as journal {}",
                                        loan.getId(),
                                        existing.getId());

                        return;
                }

                /*
                 * ========================================================
                 * CORRECT ACCOUNTING
                 * ========================================================
                 *
                 * Gross loan:
                 *
                 * RWF 1,000,000
                 *
                 * Processing fee:
                 *
                 * RWF 20,000
                 *
                 * Borrower receives:
                 *
                 * RWF 980,000
                 *
                 * Journal:
                 *
                 * DR Loans Receivable 1,000,000
                 * CR Cash 980,000
                 * CR Processing Fee Income 20,000
                 *
                 * This is one balanced journal.
                 *
                 * The fee must NOT create a second DR Cash entry.
                 */

                List<JournalLine> lines = new ArrayList<>();

                // --------------------------------------------------------
                // DR LOANS RECEIVABLE - GROSS
                // --------------------------------------------------------

                lines.add(
                                JournalLine.builder()
                                                .account(
                                                                account(
                                                                                org,
                                                                                "1100"))
                                                .debit(
                                                                grossPrincipal)
                                                .credit(
                                                                ZERO)
                                                .description(
                                                                "Gross loan principal receivable — "
                                                                                + reference)
                                                .build());

                // --------------------------------------------------------
                // CR CASH - NET AMOUNT ACTUALLY GIVEN TO BORROWER
                // --------------------------------------------------------

                lines.add(
                                JournalLine.builder()
                                                .account(
                                                                account(
                                                                                org,
                                                                                "1000"))
                                                .debit(
                                                                ZERO)
                                                .credit(
                                                                netCashDisbursed)
                                                .description(
                                                                "Net cash disbursed after "
                                                                                + money(loan.getApplicationFeeRateDecimal())
                                                                                                .toPlainString()
                                                                                + "% application fee — "
                                                                                + reference)
                                                .build());

                // --------------------------------------------------------
                // CR PROCESSING FEE INCOME
                // --------------------------------------------------------

                if (applicationFee.compareTo(
                                ZERO) > 0) {

                        lines.add(
                                        JournalLine.builder()
                                                        .account(
                                                                        account(
                                                                                        org,
                                                                                        "4100"))
                                                        .debit(
                                                                        ZERO)
                                                        .credit(
                                                                        applicationFee)
                                                        .description(
                                                                        "One-time "
                                                                                        + money(loan.getApplicationFeeRateDecimal())
                                                                                                        .toPlainString()
                                                                                        + "% application fee income — "
                                                                                        + reference)
                                                        .build());
                }

                post(
                                org,
                                loan.getBranch(),
                                "LOAN_DISBURSEMENT",
                                sourceId,
                                reference,
                                "Disbursement of loan "
                                                + reference
                                                + " — gross "
                                                + grossPrincipal.toPlainString()
                                                + ", application fee "
                                                + applicationFee.toPlainString()
                                                + ", net cash "
                                                + netCashDisbursed.toPlainString(),
                                lines);

                log.info(
                                "Loan disbursement accounted. " +
                                                "loanId={}, grossPrincipal={}, applicationFee={}, " +
                                                "netCashDisbursed={}",
                                loan.getId(),
                                grossPrincipal,
                                applicationFee,
                                netCashDisbursed);
        }

        // ============================================================
        // CONTRACTUAL SCHEDULE ACCRUAL
        // ============================================================

        /**
         * Accrues the unpaid portion of a contractual installment's interest
         * once the installment reaches its due date. This keeps accounting
         * income exactly aligned with the approved repayment schedule.
         */
        @Transactional
        public JournalEntry postScheduledInterestAccrual(Payment installment) {
                if (installment == null || installment.getLoan() == null || installment.getId() == null) {
                        throw new IllegalArgumentException("Payment installment is required");
                }

                Loan loan = installment.getLoan();
                Organization org = loan.getOrganization();
                requireOrganization(org);
                ensureChartOfAccounts(org);

                BigDecimal scheduled = money(installment.getScheduledInterestDecimal());
                BigDecimal paid = money(installment.getInterestComponentDecimal());
                BigDecimal amount = maxZero(scheduled.subtract(paid));
                if (amount.compareTo(ZERO) <= 0) {
                        return null;
                }

                String sourceId = "PAYMENT-" + installment.getId();
                JournalEntry existing = journalRepo
                                .findFirstByOrganization_IdAndSourceTypeAndSourceId(
                                                org.getId(), "SCHEDULED_INTEREST_ACCRUAL", sourceId)
                                .orElse(null);
                if (existing != null) {
                        return existing;
                }

                String reference = loan.getReferenceNumber() != null && !loan.getReferenceNumber().isBlank()
                                ? loan.getReferenceNumber().trim()
                                : "LOAN-" + loan.getId();

                JournalEntry entry = post(
                                org,
                                loan.getBranch(),
                                "SCHEDULED_INTEREST_ACCRUAL",
                                sourceId,
                                reference,
                                "Contractual interest accrued for installment " + installment.getInstallmentNumber()
                                                + " — " + reference,
                                List.of(
                                                JournalLine.builder()
                                                                .account(account(org, "1150"))
                                                                .debit(amount)
                                                                .credit(ZERO)
                                                                .description("Contractual interest receivable — "
                                                                                + reference)
                                                                .build(),
                                                JournalLine.builder()
                                                                .account(account(org, "4000"))
                                                                .debit(ZERO)
                                                                .credit(amount)
                                                                .description("Contractual interest income — "
                                                                                + reference)
                                                                .build()));

                // Keep the operational loan sub-ledger synchronized with the
                // receivable journal. The scheduler is the accounting event
                // that creates this contractual receivable, so the loan's
                // outstanding interest must increase atomically with GL 1150.
                loan.setInterestOutstanding(
                                money(loan.getInterestOutstandingDecimal())
                                                .add(amount));

                return entry;
        }

        /**
         * Accrues the unpaid portion of a contractual installment's management
         * fee once the installment reaches its due date.
         */
        @Transactional
        public JournalEntry postScheduledManagementFeeAccrual(Payment installment) {
                if (installment == null || installment.getLoan() == null || installment.getId() == null) {
                        throw new IllegalArgumentException("Payment installment is required");
                }

                Loan loan = installment.getLoan();
                Organization org = loan.getOrganization();
                requireOrganization(org);
                ensureChartOfAccounts(org);

                BigDecimal scheduled = money(installment.getScheduledManagementFeeDecimal());
                BigDecimal paid = money(installment.getManagementFeeComponentDecimal());
                BigDecimal amount = maxZero(scheduled.subtract(paid));
                if (amount.compareTo(ZERO) <= 0) {
                        return null;
                }

                String sourceId = "PAYMENT-" + installment.getId();
                JournalEntry existing = journalRepo
                                .findFirstByOrganization_IdAndSourceTypeAndSourceId(
                                                org.getId(), "SCHEDULED_MANAGEMENT_FEE_ACCRUAL", sourceId)
                                .orElse(null);
                if (existing != null) {
                        return existing;
                }

                String reference = loan.getReferenceNumber() != null && !loan.getReferenceNumber().isBlank()
                                ? loan.getReferenceNumber().trim()
                                : "LOAN-" + loan.getId();

                JournalEntry entry = post(
                                org,
                                loan.getBranch(),
                                "SCHEDULED_MANAGEMENT_FEE_ACCRUAL",
                                sourceId,
                                reference,
                                "Contractual management fee accrued for installment "
                                                + installment.getInstallmentNumber() + " — " + reference,
                                List.of(
                                                JournalLine.builder()
                                                                .account(account(org, "1160"))
                                                                .debit(amount)
                                                                .credit(ZERO)
                                                                .description("Contractual management fee receivable — "
                                                                                + reference)
                                                                .build(),
                                                JournalLine.builder()
                                                                .account(account(org, "4100"))
                                                                .debit(ZERO)
                                                                .credit(amount)
                                                                .description("Contractual management fee income — "
                                                                                + reference)
                                                                .build()));

                // Keep the operational loan sub-ledger synchronized with GL
                // 1160 at the exact moment the contractual fee is accrued.
                loan.setManagementFeeOutstanding(
                                money(loan.getManagementFeeOutstandingDecimal())
                                                .add(amount));

                return entry;
        }

        // ============================================================
        // LEGACY MONTHLY INTEREST / MANAGEMENT ACCRUAL COMPATIBILITY
        // ============================================================

        /**
         * Legacy compatibility entry point. The old parameter was named
         * dailyInterestAmount and could carry a daily accrual such as RWF 8,065.
         * That behavior is intentionally removed. The supplied amount is ignored
         * and one contractual monthly charge is posted at most once per month.
         * New code should use postScheduledInterestAccrual(Payment).
         */
        @Deprecated
        @Transactional
        public JournalEntry postInterestAccrual(Loan loan, double ignoredAmount) {
                return postContractualMonthlyInterestAccrual(loan);
        }

        @Deprecated
        @Transactional
        public JournalEntry postInterestAccrual(Loan loan, BigDecimal ignoredAmount) {
                return postContractualMonthlyInterestAccrual(loan);
        }

        private JournalEntry postContractualMonthlyInterestAccrual(Loan loan) {
                if (loan == null || loan.getId() == null) {
                        throw new IllegalArgumentException("Loan with ID is required");
                }

                Organization org = loan.getOrganization();
                requireOrganization(org);
                ensureChartOfAccounts(org);

                BigDecimal interest = FinancialPolicy.contractualMonthlyCharge(
                                maxZero(loan.getOutstandingBalanceDecimal()),
                                loan.getInterestRateDecimal() != null
                                                ? loan.getInterestRateDecimal()
                                                : FinancialPolicy.MONTHLY_INTEREST_RATE);
                if (interest.compareTo(ZERO) <= 0) {
                        return null;
                }

                YearMonth period = YearMonth.now();
                String sourceId = loan.getId() + "-" + period;
                JournalEntry existing = journalRepo
                                .findFirstByOrganization_IdAndSourceTypeAndSourceId(
                                                org.getId(), "CONTRACTUAL_MONTHLY_INTEREST_ACCRUAL", sourceId)
                                .orElse(null);
                if (existing != null) {
                        return existing;
                }

                String reference = loan.getReferenceNumber() != null && !loan.getReferenceNumber().isBlank()
                                ? loan.getReferenceNumber().trim()
                                : "LOAN-" + loan.getId();

                JournalEntry entry = post(
                                org,
                                loan.getBranch(),
                                "CONTRACTUAL_MONTHLY_INTEREST_ACCRUAL",
                                sourceId,
                                reference,
                                "Contractual monthly interest accrual for " + reference + " (" + period + ")",
                                List.of(
                                                JournalLine.builder()
                                                                .account(account(org, "1150"))
                                                                .debit(interest)
                                                                .credit(ZERO)
                                                                .description("Contractual monthly interest receivable — "
                                                                                + reference)
                                                                .build(),
                                                JournalLine.builder()
                                                                .account(account(org, "4000"))
                                                                .debit(ZERO)
                                                                .credit(interest)
                                                                .description("Contractual monthly interest income — "
                                                                                + reference)
                                                                .build()));

                loan.setInterestOutstanding(
                                money(loan.getInterestOutstandingDecimal())
                                                .add(interest));

                return entry;
        }

        /**
         * Legacy compatibility entry point. Management fee is contractual and
         * monthly; no daily calendar-day conversion is performed.
         */
        @Deprecated
        @Transactional
        public JournalEntry postManagementFeeAccrual(Loan loan, BigDecimal ignoredAmount) {
                return postContractualMonthlyManagementFeeAccrual(loan);
        }

        private JournalEntry postContractualMonthlyManagementFeeAccrual(Loan loan) {
                if (loan == null || loan.getId() == null) {
                        throw new IllegalArgumentException("Loan with ID is required");
                }

                Organization org = loan.getOrganization();
                requireOrganization(org);
                ensureChartOfAccounts(org);

                BigDecimal fee = FinancialPolicy.contractualMonthlyCharge(
                                maxZero(loan.getOutstandingBalanceDecimal()),
                                loan.getManagementFeeRateDecimal() != null
                                                ? loan.getManagementFeeRateDecimal()
                                                : FinancialPolicy.MONTHLY_MANAGEMENT_FEE_RATE);
                if (fee.compareTo(ZERO) <= 0) {
                        return null;
                }

                YearMonth period = YearMonth.now();
                String sourceId = loan.getId() + "-" + period;
                JournalEntry existing = journalRepo
                                .findFirstByOrganization_IdAndSourceTypeAndSourceId(
                                                org.getId(), "CONTRACTUAL_MONTHLY_MANAGEMENT_FEE_ACCRUAL", sourceId)
                                .orElse(null);
                if (existing != null) {
                        return existing;
                }

                String reference = loan.getReferenceNumber() != null && !loan.getReferenceNumber().isBlank()
                                ? loan.getReferenceNumber().trim()
                                : "LOAN-" + loan.getId();

                JournalEntry entry = post(
                                org,
                                loan.getBranch(),
                                "CONTRACTUAL_MONTHLY_MANAGEMENT_FEE_ACCRUAL",
                                sourceId,
                                reference,
                                "Contractual monthly management fee accrual for " + reference + " (" + period + ")",
                                List.of(
                                                JournalLine.builder()
                                                                .account(account(org, "1160"))
                                                                .debit(fee)
                                                                .credit(ZERO)
                                                                .description("Contractual monthly management fee receivable — "
                                                                                + reference)
                                                                .build(),
                                                JournalLine.builder()
                                                                .account(account(org, "4100"))
                                                                .debit(ZERO)
                                                                .credit(fee)
                                                                .description("Contractual monthly management fee income — "
                                                                                + reference)
                                                                .build()));

                loan.setManagementFeeOutstanding(
                                money(loan.getManagementFeeOutstandingDecimal())
                                                .add(fee));

                return entry;
        }

        // ============================================================
        // PENALTY ACCRUAL
        // ============================================================

        /**
         * Records daily late-payment penalty as a receivable. The platform
         * policy is 15% per month accrued by actual calendar day.
         */
        @Transactional
        public JournalEntry postPenaltyAccrual(
                        Loan loan,
                        BigDecimal penaltyAmount) {

                if (loan == null || loan.getId() == null) {
                        throw new IllegalArgumentException("Loan is required");
                }

                Organization org = loan.getOrganization();
                requireOrganization(org);
                ensureChartOfAccounts(org);

                BigDecimal penalty = maxZero(penaltyAmount);
                if (penalty.compareTo(ZERO) <= 0) {
                        return null;
                }

                LocalDate accrualDate = LocalDate.now();
                String sourceId = loan.getId() + "-" + accrualDate;

                JournalEntry existing = journalRepo
                                .findFirstByOrganization_IdAndSourceTypeAndSourceId(
                                                org.getId(),
                                                "PENALTY_ACCRUAL",
                                                sourceId)
                                .orElse(null);

                if (existing != null) {
                        return existing;
                }

                String reference = loan.getReferenceNumber() != null
                                && !loan.getReferenceNumber().isBlank()
                                                ? loan.getReferenceNumber().trim()
                                                : "LOAN-" + loan.getId();

                JournalEntry entry = post(
                                org,
                                loan.getBranch(),
                                "PENALTY_ACCRUAL",
                                sourceId,
                                reference,
                                "15% monthly / calendar-day penalty accrual for " + reference
                                                + " (" + accrualDate + ")",
                                List.of(
                                                JournalLine.builder()
                                                                .account(account(org, "1175"))
                                                                .debit(penalty)
                                                                .credit(ZERO)
                                                                .description("Penalty receivable accrued — "
                                                                                + reference)
                                                                .build(),
                                                JournalLine.builder()
                                                                .account(account(org, "4100"))
                                                                .debit(ZERO)
                                                                .credit(penalty)
                                                                .description("Penalty income accrued — " + reference)
                                                                .build()));

                loan.setPenaltiesAssessed(
                                money(loan.getPenaltiesAssessedDecimal())
                                                .add(penalty));

                return entry;
        }

        // ============================================================
        // EXTENSION FEE ASSESSMENT
        // ============================================================

        /**
         * Record a 10% extension/restructuring fee as a receivable and
         * fee income. The fee is never added to loan principal.
         */
        @Transactional
        public JournalEntry postExtensionFeeAssessment(
                        Loan loan,
                        BigDecimal extensionFeeAmount) {

                if (loan == null || loan.getId() == null) {
                        throw new IllegalArgumentException("Loan is required");
                }

                Organization org = loan.getOrganization();
                requireOrganization(org);
                ensureChartOfAccounts(org);

                BigDecimal fee = maxZero(extensionFeeAmount);

                if (fee.compareTo(ZERO) <= 0) {
                        return null;
                }

                String sourceId = loan.getId() + "-" +
                                loan.getExtensionCount() + "-EXTENSION-FEE";

                JournalEntry existing = journalRepo
                                .findFirstByOrganization_IdAndSourceTypeAndSourceId(
                                                org.getId(),
                                                "LOAN_EXTENSION_FEE",
                                                sourceId)
                                .orElse(null);

                if (existing != null) {
                        return existing;
                }

                String reference = loan.getReferenceNumber() != null
                                && !loan.getReferenceNumber().isBlank()
                                                ? loan.getReferenceNumber().trim()
                                                : "LOAN-" + loan.getId();

                return post(
                                org,
                                loan.getBranch(),
                                "LOAN_EXTENSION_FEE",
                                sourceId,
                                reference,
                                "10% extension fee assessed for " + reference,
                                List.of(
                                                JournalLine.builder()
                                                                .account(account(org, "1170"))
                                                                .debit(fee)
                                                                .credit(ZERO)
                                                                .description(
                                                                                "Extension fee receivable — "
                                                                                                + reference)
                                                                .build(),
                                                JournalLine.builder()
                                                                .account(account(org, "4100"))
                                                                .debit(ZERO)
                                                                .credit(fee)
                                                                .description(
                                                                                "Extension fee income — "
                                                                                                + reference)
                                                                .build()));
        }

        // ============================================================
        // EXTENSION FEE COLLECTION
        // ============================================================

        @Transactional
        public JournalEntry postExtensionFeeCollection(
                        Payment payment,
                        BigDecimal extensionFeeAmount) {

                if (payment == null || payment.getLoan() == null) {
                        throw new IllegalArgumentException("Payment with loan is required");
                }

                Loan loan = payment.getLoan();
                Organization org = loan.getOrganization();
                requireOrganization(org);
                ensureChartOfAccounts(org);

                BigDecimal amount = maxZero(extensionFeeAmount);
                if (amount.compareTo(ZERO) <= 0) {
                        return null;
                }

                String sourceId = payment.getId() + "-EXTENSION-FEE-COLLECTION";
                JournalEntry existing = journalRepo
                                .findFirstByOrganization_IdAndSourceTypeAndSourceId(
                                                org.getId(),
                                                "LOAN_EXTENSION_FEE_COLLECTION",
                                                sourceId)
                                .orElse(null);

                if (existing != null) {
                        return existing;
                }

                String reference = loan.getReferenceNumber() != null
                                && !loan.getReferenceNumber().isBlank()
                                                ? loan.getReferenceNumber().trim()
                                                : "LOAN-" + loan.getId();

                return post(
                                org,
                                loan.getBranch(),
                                "LOAN_EXTENSION_FEE_COLLECTION",
                                sourceId,
                                reference,
                                "Extension fee collection for " + reference,
                                List.of(
                                                JournalLine.builder()
                                                                .account(account(org, "1000"))
                                                                .debit(amount)
                                                                .credit(ZERO)
                                                                .description("Extension fee cash collection — "
                                                                                + reference)
                                                                .build(),
                                                JournalLine.builder()
                                                                .account(account(org, "1170"))
                                                                .debit(ZERO)
                                                                .credit(amount)
                                                                .description("Extension fee receivable cleared — "
                                                                                + reference)
                                                                .build()));
        }

        // ============================================================
        // PAYMENT RECEIVED
        // ============================================================

        /**
         * Backward-compatible overload.
         *
         * Management fee is zero when the caller does not supply it.
         */
        @Transactional
        public JournalEntry postPaymentReceived(
                        Payment payment,
                        Double paymentAmount,
                        double principalAmount,
                        double interestAmount,
                        double penaltyAmount,
                        double overpaymentAmount) {

                return postPaymentReceived(
                                payment,
                                money(paymentAmount),
                                money(principalAmount),
                                money(interestAmount),
                                ZERO,
                                money(penaltyAmount),
                                ZERO,
                                money(overpaymentAmount));
        }

        /**
         * Correct overload used by the corrected PaymentService.
         *
         * Allocation:
         *
         * DR Cash
         *
         * CR Loans Receivable
         * CR Interest Income
         * CR Management/Fee Income
         * CR Penalty/Fee Income
         * CR Borrower Refunds Payable
         */
        @Transactional
        public JournalEntry postPaymentReceived(
                        Payment payment,
                        BigDecimal paymentAmount,
                        BigDecimal principalAmount,
                        BigDecimal interestAmount,
                        BigDecimal managementFeeAmount,
                        BigDecimal penaltyAmount,
                        BigDecimal extensionFeeAmount,
                        BigDecimal overpaymentAmount) {

                if (payment == null) {

                        throw new IllegalArgumentException(
                                        "Payment is required");
                }

                if (payment.getId() == null) {

                        throw new IllegalArgumentException(
                                        "Payment ID is required");
                }

                Loan loan = payment.getLoan();

                if (loan == null) {

                        throw new IllegalArgumentException(
                                        "Payment has no loan");
                }

                if (loan.getId() == null) {

                        throw new IllegalArgumentException(
                                        "Payment loan has no ID");
                }

                Organization org = loan.getOrganization();

                requireOrganization(org);

                ensureChartOfAccounts(org);

                String sourceId = String.valueOf(
                                payment.getId());

                JournalEntry existing = journalRepo
                                .findFirstByOrganization_IdAndSourceTypeAndSourceId(
                                                org.getId(),
                                                "PAYMENT_RECEIVED",
                                                sourceId)
                                .orElse(null);

                if (existing != null) {

                        log.info(
                                        "Payment {} already posted as journal {}",
                                        payment.getId(),
                                        existing.getId());

                        return existing;
                }

                // ============================================================
                // AMOUNTS
                // ============================================================

                BigDecimal total = maxZero(
                                paymentAmount);

                if (total.compareTo(
                                ZERO) <= 0) {

                        throw new IllegalArgumentException(
                                        "Payment amount must be greater than zero");
                }

                BigDecimal principal = maxZero(
                                principalAmount);

                BigDecimal interest = maxZero(
                                interestAmount);

                BigDecimal managementFee = maxZero(
                                managementFeeAmount);

                BigDecimal penalty = maxZero(
                                penaltyAmount);

                BigDecimal extensionFee = maxZero(
                                extensionFeeAmount);

                BigDecimal overpayment = maxZero(
                                overpaymentAmount);

                // ============================================================
                // VALIDATE ALLOCATION
                // ============================================================

                BigDecimal allocated = principal
                                .add(interest)
                                .add(managementFee)
                                .add(penalty)
                                .add(extensionFee)
                                .add(overpayment);

                allocated = money(
                                allocated);

                BigDecimal allocationDifference = money(
                                total.subtract(
                                                allocated));

                if (allocationDifference.compareTo(
                                ZERO) != 0) {

                        throw new IllegalStateException(
                                        "Payment allocation does not equal payment amount: " +
                                                        "payment=" +
                                                        total.toPlainString() +
                                                        ", principal=" +
                                                        principal.toPlainString() +
                                                        ", interest=" +
                                                        interest.toPlainString() +
                                                        ", managementFee=" +
                                                        managementFee.toPlainString() +
                                                        ", penalty=" +
                                                        penalty.toPlainString() +
                                                        ", overpayment=" +
                                                        overpayment.toPlainString() +
                                                        ", allocated=" +
                                                        allocated.toPlainString());
                }

                String loanReference = loan.getReferenceNumber() != null
                                && !loan.getReferenceNumber().isBlank()
                                                ? loan.getReferenceNumber().trim()
                                                : "LOAN-" + loan.getId();

                List<JournalLine> lines = new ArrayList<>();

                // ============================================================
                // DR CASH
                // ============================================================

                lines.add(
                                JournalLine.builder()
                                                .account(
                                                                account(
                                                                                org,
                                                                                "1000"))
                                                .debit(
                                                                total)
                                                .credit(
                                                                ZERO)
                                                .description(
                                                                "Payment received — "
                                                                                + loanReference)
                                                .build());

                // ============================================================
                // CR LOANS RECEIVABLE
                // ============================================================

                if (principal.compareTo(
                                ZERO) > 0) {

                        lines.add(
                                        JournalLine.builder()
                                                        .account(
                                                                        account(
                                                                                        org,
                                                                                        "1100"))
                                                        .debit(
                                                                        ZERO)
                                                        .credit(
                                                                        principal)
                                                        .description(
                                                                        "Principal repayment — "
                                                                                        + loanReference)
                                                        .build());
                }

                // ============================================================
                // INTEREST
                // ============================================================

                if (interest.compareTo(
                                ZERO) > 0) {

                        BigDecimal accrued = accruedInterestReceivable(
                                        org,
                                        loan.getId());

                        BigDecimal clearReceivable = interest.min(
                                        maxZero(
                                                        accrued));

                        BigDecimal directIncome = interest.subtract(
                                        clearReceivable);

                        clearReceivable = maxZero(
                                        clearReceivable);

                        directIncome = maxZero(
                                        directIncome);

                        if (clearReceivable.compareTo(
                                        ZERO) > 0) {

                                lines.add(
                                                JournalLine.builder()
                                                                .account(
                                                                                account(
                                                                                                org,
                                                                                                "1150"))
                                                                .debit(
                                                                                ZERO)
                                                                .credit(
                                                                                clearReceivable)
                                                                .description(
                                                                                "Clears accrued interest — "
                                                                                                + loanReference)
                                                                .build());
                        }

                        if (directIncome.compareTo(
                                        ZERO) > 0) {

                                lines.add(
                                                JournalLine.builder()
                                                                .account(
                                                                                account(
                                                                                                org,
                                                                                                "4000"))
                                                                .debit(
                                                                                ZERO)
                                                                .credit(
                                                                                directIncome)
                                                                .description(
                                                                                "Interest income — "
                                                                                                + loanReference)
                                                                .build());
                        }
                }

                // ============================================================
                // MANAGEMENT FEE
                // ------------------------------------------------------------
                // Clear any previously accrued management-fee receivable first.
                // Only the unaccrued portion is recognized as new income.

                if (managementFee.compareTo(ZERO) > 0) {

                        BigDecimal accruedManagementFee = accruedManagementFeeReceivable(org, loan.getId());

                        BigDecimal clearManagementFee = managementFee.min(maxZero(accruedManagementFee));

                        BigDecimal directManagementIncome = managementFee.subtract(clearManagementFee);

                        if (clearManagementFee.compareTo(ZERO) > 0) {
                                lines.add(
                                                JournalLine.builder()
                                                                .account(account(org, "1160"))
                                                                .debit(ZERO)
                                                                .credit(clearManagementFee)
                                                                .description("Clears accrued management fee — "
                                                                                + loanReference)
                                                                .build());
                        }

                        if (directManagementIncome.compareTo(ZERO) > 0) {
                                lines.add(
                                                JournalLine.builder()
                                                                .account(account(org, "4100"))
                                                                .debit(ZERO)
                                                                .credit(directManagementIncome)
                                                                .description("Management fee income — " + loanReference)
                                                                .build());
                        }
                }

                // PENALTY
                // ============================================================

                if (penalty.compareTo(ZERO) > 0) {

                        BigDecimal accruedPenalty = accruedPenaltyReceivable(org, loan.getId());
                        BigDecimal clearPenalty = penalty.min(maxZero(accruedPenalty));
                        BigDecimal directPenaltyIncome = penalty.subtract(clearPenalty).max(ZERO);

                        if (clearPenalty.compareTo(ZERO) > 0) {
                                lines.add(
                                                JournalLine.builder()
                                                                .account(account(org, "1175"))
                                                                .debit(ZERO)
                                                                .credit(clearPenalty)
                                                                .description("Clears accrued penalty receivable — "
                                                                                + loanReference)
                                                                .build());
                        }

                        if (directPenaltyIncome.compareTo(ZERO) > 0) {
                                lines.add(
                                                JournalLine.builder()
                                                                .account(account(org, "4100"))
                                                                .debit(ZERO)
                                                                .credit(directPenaltyIncome)
                                                                .description("Penalty income — " + loanReference)
                                                                .build());
                        }
                }

                // ============================================================
                // EXTENSION FEE
                // ============================================================

                if (extensionFee.compareTo(ZERO) > 0) {

                        lines.add(
                                        JournalLine.builder()
                                                        .account(account(org, "1170"))
                                                        .debit(ZERO)
                                                        .credit(extensionFee)
                                                        .description(
                                                                        "Extension fee receivable settlement — "
                                                                                        + loanReference)
                                                        .build());
                }

                // ============================================================
                // OVERPAYMENT
                // ============================================================

                if (overpayment.compareTo(
                                ZERO) > 0) {

                        lines.add(
                                        JournalLine.builder()
                                                        .account(
                                                                        account(
                                                                                        org,
                                                                                        "2100"))
                                                        .debit(
                                                                        ZERO)
                                                        .credit(
                                                                        overpayment)
                                                        .description(
                                                                        "Borrower overpayment refundable — "
                                                                                        + loanReference)
                                                        .build());

                        log.info(
                                        "Payment {} created borrower refund liability of {}",
                                        payment.getId(),
                                        overpayment);
                }

                // ============================================================
                // VALIDATE JOURNAL
                // ============================================================

                BigDecimal totalDebits = ZERO;

                BigDecimal totalCredits = ZERO;

                for (JournalLine line : lines) {

                        totalDebits = totalDebits.add(
                                        money(
                                                        line.getDebitDecimal()));

                        totalCredits = totalCredits.add(
                                        money(
                                                        line.getCreditDecimal()));
                }

                totalDebits = money(
                                totalDebits);

                totalCredits = money(
                                totalCredits);

                if (totalDebits.compareTo(
                                totalCredits) != 0) {

                        throw new IllegalStateException(
                                        "Payment accounting does not balance: " +
                                                        "debits=" +
                                                        totalDebits.toPlainString() +
                                                        ", credits=" +
                                                        totalCredits.toPlainString());
                }

                String reference = payment.getPaymentReference() != null
                                && !payment.getPaymentReference().isBlank()
                                                ? payment.getPaymentReference().trim()
                                                : "PAY-" + payment.getId();

                return post(
                                org,
                                loan.getBranch(),
                                "PAYMENT_RECEIVED",
                                sourceId,
                                reference,
                                "Payment received on loan "
                                                + loanReference,
                                lines);
        }

        // ============================================================
        // PAYMENT RECEIVED FROM PAYMENT ENTITY
        // ============================================================

        @Transactional
        public JournalEntry postPaymentReceived(
                        Payment payment) {

                if (payment == null) {
                        throw new IllegalArgumentException("Payment is required");
                }

                BigDecimal amount = payment.getAmountPaid() != null
                                ? money(payment.getAmountPaid())
                                : ZERO;

                BigDecimal principal = payment.getPrincipalComponent() != null
                                ? money(payment.getPrincipalComponent())
                                : ZERO;

                BigDecimal interest = payment.getInterestComponent() != null
                                ? money(payment.getInterestComponent())
                                : ZERO;

                BigDecimal managementFee = payment.getManagementFeeComponent() != null
                                ? money(payment.getManagementFeeComponent())
                                : ZERO;

                BigDecimal penalty = payment.getPenaltyPaid() != null
                                ? money(payment.getPenaltyPaid())
                                : ZERO;

                BigDecimal extensionFee = payment.getExtensionFeeComponent() != null
                                ? money(payment.getExtensionFeeComponent())
                                : ZERO;

                BigDecimal derivedOverpayment = maxZero(
                                amount
                                                .subtract(principal)
                                                .subtract(interest)
                                                .subtract(managementFee)
                                                .subtract(penalty)
                                                .subtract(extensionFee));

                return postPaymentReceived(
                                payment,
                                amount,
                                principal,
                                interest,
                                managementFee,
                                penalty,
                                extensionFee,
                                derivedOverpayment);
        }
        // ============================================================
        // OVERPAYMENT REFUND PAYABLE
        // ============================================================

        @Transactional
        public JournalEntry postOverpaymentRefundPayable(
                        Payment payment,
                        BigDecimal refundAmount) {

                if (payment == null) {

                        throw new IllegalArgumentException(
                                        "Payment is required");
                }

                if (payment.getId() == null) {

                        throw new IllegalArgumentException(
                                        "Payment ID is required");
                }

                Loan loan = payment.getLoan();

                if (loan == null) {

                        throw new IllegalArgumentException(
                                        "Payment has no loan");
                }

                Organization org = loan.getOrganization();

                requireOrganization(org);

                ensureChartOfAccounts(org);

                BigDecimal amount = maxZero(
                                refundAmount);

                if (amount.compareTo(
                                ZERO) <= 0) {

                        throw new IllegalArgumentException(
                                        "Refund payable amount must be greater than zero");
                }

                String paymentSourceId = String.valueOf(
                                payment.getId());

                JournalEntry paymentJournal = journalRepo
                                .findFirstByOrganization_IdAndSourceTypeAndSourceId(
                                                org.getId(),
                                                "PAYMENT_RECEIVED",
                                                paymentSourceId)
                                .orElse(null);

                if (paymentJournal != null) {

                        log.info(
                                        "Overpayment for payment {} is already recognized " +
                                                        "in payment journal {}. No duplicate liability created.",
                                        payment.getId(),
                                        paymentJournal.getId());

                        return paymentJournal;
                }

                JournalEntry existing = journalRepo
                                .findFirstByOrganization_IdAndSourceTypeAndSourceId(
                                                org.getId(),
                                                "OVERPAYMENT_REFUND_PAYABLE",
                                                paymentSourceId)
                                .orElse(null);

                if (existing != null) {

                        return existing;
                }

                String reference = payment.getPaymentReference() != null
                                && !payment.getPaymentReference().isBlank()
                                                ? payment.getPaymentReference().trim()
                                                : "PAY-" + payment.getId();

                return post(
                                org,
                                loan.getBranch(),
                                "OVERPAYMENT_REFUND_PAYABLE",
                                paymentSourceId,
                                "REFUND-" + payment.getId(),
                                "Borrower refund payable for payment "
                                                + reference,

                                List.of(

                                                JournalLine.builder()
                                                                .account(
                                                                                account(
                                                                                                org,
                                                                                                "1000"))
                                                                .debit(
                                                                                amount)
                                                                .credit(
                                                                                ZERO)
                                                                .description(
                                                                                "Excess payment received — "
                                                                                                + reference)
                                                                .build(),

                                                JournalLine.builder()
                                                                .account(
                                                                                account(
                                                                                                org,
                                                                                                "2100"))
                                                                .debit(
                                                                                ZERO)
                                                                .credit(
                                                                                amount)
                                                                .description(
                                                                                "Borrower refund payable — "
                                                                                                + reference)
                                                                .build()));
        }

        // ============================================================
        // REFUND PAID
        // ============================================================

        @Transactional
        public JournalEntry postRefundPaid(
                        Organization org,
                        Branch branch,
                        String refundReference,
                        String sourceId,
                        BigDecimal refundAmount,
                        String description) {

                requireOrganization(org);

                ensureChartOfAccounts(org);

                validateBranchOwnership(
                                org,
                                branch);

                if (sourceId == null
                                || sourceId.isBlank()) {

                        throw new IllegalArgumentException(
                                        "Refund source ID is required");
                }

                if (refundReference == null
                                || refundReference.isBlank()) {

                        throw new IllegalArgumentException(
                                        "Refund reference is required");
                }

                BigDecimal amount = maxZero(
                                refundAmount);

                if (amount.compareTo(
                                ZERO) <= 0) {

                        throw new IllegalArgumentException(
                                        "Refund amount must be greater than zero");
                }

                BigDecimal payableBalance = borrowerRefundsPayableBalance(
                                org);

                if (amount.compareTo(
                                payableBalance) > 0) {

                        throw new IllegalStateException(
                                        "Refund amount exceeds borrower refund liability. " +
                                                        "Requested=" +
                                                        amount.toPlainString() +
                                                        ", available=" +
                                                        payableBalance.toPlainString());
                }

                JournalEntry existing = journalRepo
                                .findFirstByOrganization_IdAndSourceTypeAndSourceId(
                                                org.getId(),
                                                "REFUND_PAYMENT",
                                                sourceId.trim())
                                .orElse(null);

                if (existing != null) {

                        return existing;
                }

                String finalDescription = description != null
                                && !description.isBlank()
                                                ? description.trim()
                                                : "Borrower refund paid";

                return post(
                                org,
                                branch,
                                "REFUND_PAYMENT",
                                sourceId.trim(),
                                refundReference.trim(),
                                finalDescription,

                                List.of(

                                                JournalLine.builder()
                                                                .account(
                                                                                account(
                                                                                                org,
                                                                                                "2100"))
                                                                .debit(
                                                                                amount)
                                                                .credit(
                                                                                ZERO)
                                                                .description(
                                                                                "Borrower refund liability settled — "
                                                                                                + refundReference)
                                                                .build(),

                                                JournalLine.builder()
                                                                .account(
                                                                                account(
                                                                                                org,
                                                                                                "1000"))
                                                                .debit(
                                                                                ZERO)
                                                                .credit(
                                                                                amount)
                                                                .description(
                                                                                "Refund paid to borrower — "
                                                                                                + refundReference)
                                                                .build()));
        }

        // ============================================================
        // REFUND LIABILITY BALANCE
        // ============================================================

        @Transactional(readOnly = true)
        public BigDecimal borrowerRefundsPayableBalance(
                        Organization org) {

                requireOrganization(org);

                ChartOfAccount refundAccount = coaRepo
                                .findByOrganization_IdAndCode(
                                                org.getId(),
                                                "2100")
                                .orElse(null);

                if (refundAccount == null) {

                        return ZERO;
                }

                List<JournalLine> lines = lineRepo.findByAccount_IdAndOrganization_Id(
                                refundAccount.getId(),
                                org.getId());

                if (lines == null
                                || lines.isEmpty()) {

                        return ZERO;
                }

                BigDecimal debit = ZERO;

                BigDecimal credit = ZERO;

                for (JournalLine line : lines) {

                        if (line == null) {
                                continue;
                        }

                        debit = debit.add(
                                        money(
                                                        line.getDebitDecimal()));

                        credit = credit.add(
                                        money(
                                                        line.getCreditDecimal()));
                }

                return maxZero(
                                credit.subtract(
                                                debit));
        }

        // ============================================================
        // ACCRUED INTEREST RECEIVABLE
        // ============================================================

        private BigDecimal accruedInterestReceivable(
                        Organization org,
                        Long loanId) {

                requireOrganization(org);

                if (loanId == null) {

                        return ZERO;
                }

                ChartOfAccount receivable = coaRepo
                                .findByOrganization_IdAndCode(
                                                org.getId(),
                                                "1150")
                                .orElse(null);

                if (receivable == null) {

                        return ZERO;
                }

                Loan loan = loanRepo.findById(loanId).orElse(null);
                if (loan == null || loan.getReferenceNumber() == null) {
                        return ZERO;
                }

                List<JournalLine> lines = lineRepo.findReceivableLinesForLoanIdentity(
                                receivable.getId(),
                                org.getId(),
                                loan.getId(),
                                loan.getReferenceNumber());

                if (lines == null
                                || lines.isEmpty()) {

                        return ZERO;
                }

                BigDecimal balance = ZERO;

                for (JournalLine line : lines) {

                        if (line == null) {
                                continue;
                        }

                        JournalEntry entry = line.getJournalEntry();

                        if (entry == null) {
                                continue;
                        }

                        BigDecimal debit = money(
                                        line.getDebitDecimal());

                        BigDecimal credit = money(
                                        line.getCreditDecimal());

                        balance = balance
                                        .add(
                                                        debit)
                                        .subtract(
                                                        credit);
                }

                return maxZero(
                                balance);
        }

        // ============================================================
        // ACCRUED PENALTY RECEIVABLE
        // ============================================================

        private BigDecimal accruedPenaltyReceivable(
                        Organization org,
                        Long loanId) {

                requireOrganization(org);
                if (loanId == null) {
                        return ZERO;
                }

                ChartOfAccount receivable = coaRepo
                                .findByOrganization_IdAndCode(org.getId(), "1175")
                                .orElse(null);
                if (receivable == null) {
                        return ZERO;
                }

                Loan loan = loanRepo.findById(loanId).orElse(null);
                if (loan == null || loan.getReferenceNumber() == null) {
                        return ZERO;
                }

                List<JournalLine> lines = lineRepo.findReceivableLinesForLoanIdentity(
                                receivable.getId(), org.getId(), loan.getId(), loan.getReferenceNumber());
                if (lines == null || lines.isEmpty()) {
                        return ZERO;
                }

                BigDecimal balance = ZERO;
                for (JournalLine line : lines) {
                        if (line == null) {
                                continue;
                        }
                        balance = balance
                                        .add(money(line.getDebitDecimal()))
                                        .subtract(money(line.getCreditDecimal()));
                }
                return maxZero(balance);
        }

        // ============================================================
        // ACCRUED MANAGEMENT FEE RECEIVABLE
        // ============================================================

        private BigDecimal accruedManagementFeeReceivable(
                        Organization org,
                        Long loanId) {
                requireOrganization(org);
                if (loanId == null)
                        return ZERO;

                ChartOfAccount receivable = coaRepo.findByOrganization_IdAndCode(org.getId(), "1160")
                                .orElse(null);
                if (receivable == null)
                        return ZERO;

                Loan loan = loanRepo.findById(loanId).orElse(null);
                if (loan == null || loan.getReferenceNumber() == null)
                        return ZERO;

                List<JournalLine> lines = lineRepo.findReceivableLinesForLoanIdentity(
                                receivable.getId(), org.getId(), loan.getId(), loan.getReferenceNumber());
                if (lines == null || lines.isEmpty())
                        return ZERO;

                BigDecimal balance = ZERO;
                for (JournalLine line : lines) {
                        if (line == null)
                                continue;
                        balance = balance
                                        .add(money(line.getDebitDecimal()))
                                        .subtract(money(line.getCreditDecimal()));
                }
                return maxZero(balance);
        }

        // ============================================================
        // ============================================================

        @Transactional
        public void postWriteOff(
                        Loan loan) {

                if (loan == null) {

                        throw new IllegalArgumentException(
                                        "Loan is required");
                }

                if (loan.getId() == null) {

                        throw new IllegalArgumentException(
                                        "Loan ID is required");
                }

                Organization org = loan.getOrganization();

                requireOrganization(org);

                ensureChartOfAccounts(org);

                BigDecimal outstanding = money(
                                loan.getOutstandingBalanceDecimal());

                if (outstanding.compareTo(
                                ZERO) <= 0) {

                        return;
                }

                String sourceId = String.valueOf(
                                loan.getId());

                JournalEntry existing = journalRepo
                                .findFirstByOrganization_IdAndSourceTypeAndSourceId(
                                                org.getId(),
                                                "WRITE_OFF",
                                                sourceId)
                                .orElse(null);

                if (existing != null) {

                        log.info(
                                        "Loan {} already has a write-off journal {}",
                                        loan.getId(),
                                        existing.getId());

                        return;
                }

                String reference = loan.getReferenceNumber() != null
                                && !loan.getReferenceNumber().isBlank()
                                                ? loan.getReferenceNumber().trim()
                                                : "LOAN-" + loan.getId();

                BigDecimal reserveAvailable = loanLossReserveBalance(org);
                BigDecimal reserveUsed = outstanding.min(reserveAvailable);
                BigDecimal additionalLossExpense = outstanding.subtract(reserveUsed);

                List<JournalLine> writeOffLines = new ArrayList<>();

                if (reserveUsed.compareTo(ZERO) > 0) {
                        writeOffLines.add(
                                        JournalLine.builder()
                                                        .account(account(org, "1200"))
                                                        .debit(reserveUsed)
                                                        .credit(ZERO)
                                                        .description("Loan loss reserve utilized — " + reference)
                                                        .build());
                }

                if (additionalLossExpense.compareTo(ZERO) > 0) {
                        writeOffLines.add(
                                        JournalLine.builder()
                                                        .account(account(org, "5000"))
                                                        .debit(additionalLossExpense)
                                                        .credit(ZERO)
                                                        .description("Uncovered loan loss expense — " + reference)
                                                        .build());
                }

                writeOffLines.add(
                                JournalLine.builder()
                                                .account(account(org, "1100"))
                                                .debit(ZERO)
                                                .credit(outstanding)
                                                .description("Write off receivable — " + reference)
                                                .build());

                post(
                                org,
                                loan.getBranch(),
                                "WRITE_OFF",
                                sourceId,
                                reference,
                                "Write-off of loan " + reference,
                                writeOffLines);
        }

        // ============================================================
        // LOAN LOSS RESERVE BALANCE
        // ============================================================

        private BigDecimal loanLossReserveBalance(Organization org) {
                requireOrganization(org);
                ChartOfAccount reserve = coaRepo.findByOrganization_IdAndCode(org.getId(), "1200")
                                .orElse(null);
                if (reserve == null)
                        return ZERO;

                List<JournalLine> lines = lineRepo.findByAccount_IdAndOrganization_Id(
                                reserve.getId(), org.getId());
                if (lines == null || lines.isEmpty())
                        return ZERO;

                BigDecimal debit = ZERO;
                BigDecimal credit = ZERO;
                for (JournalLine line : lines) {
                        if (line == null)
                                continue;
                        debit = debit.add(money(line.getDebitDecimal()));
                        credit = credit.add(money(line.getCreditDecimal()));
                }
                return maxZero(credit.subtract(debit));
        }

        // ============================================================
        // ============================================================

        @Transactional
        public JournalEntry postExpense(
                        Expense expense) {

                if (expense == null) {

                        throw new IllegalArgumentException(
                                        "Expense is required");
                }

                if (expense.getId() == null) {

                        throw new IllegalArgumentException(
                                        "Expense ID is required");
                }

                Organization org = expense.getOrganization();

                requireOrganization(org);

                ensureChartOfAccounts(org);

                if (expense.getCategory() == null) {

                        throw new IllegalArgumentException(
                                        "Expense category is required");
                }

                if (expense.getPaymentAccount() == null) {

                        throw new IllegalArgumentException(
                                        "Expense payment account is required");
                }

                ChartOfAccount expenseAccount = account(
                                org,
                                expense
                                                .getCategory()
                                                .getAccountCode());

                ChartOfAccount paymentGlAccount = expense
                                .getPaymentAccount()
                                .getGlAccount();

                if (paymentGlAccount == null) {

                        throw new IllegalArgumentException(
                                        "Payment account has no GL account");
                }

                validateAccountOwnership(
                                org,
                                paymentGlAccount);

                BigDecimal amount = money(
                                expense.getAmount());

                if (amount.compareTo(
                                ZERO) <= 0) {

                        throw new IllegalArgumentException(
                                        "Expense amount must be greater than zero");
                }

                String sourceId = String.valueOf(
                                expense.getId());

                JournalEntry existing = journalRepo
                                .findFirstByOrganization_IdAndSourceTypeAndSourceId(
                                                org.getId(),
                                                "EXPENSE",
                                                sourceId)
                                .orElse(null);

                if (existing != null) {

                        log.info(
                                        "Expense {} already posted as journal {}",
                                        expense.getId(),
                                        existing.getId());

                        return existing;
                }

                String reference = "EXP-" +
                                expense.getId();

                String description = "Expense — " +
                                expense
                                                .getCategory()
                                                .getLabel();

                if (expense.getDescription() != null
                                && !expense
                                                .getDescription()
                                                .isBlank()) {

                        description += ": " +
                                        expense
                                                        .getDescription()
                                                        .trim();
                }

                return post(
                                org,
                                expense.getBranch(),
                                "EXPENSE",
                                sourceId,
                                reference,
                                description,

                                List.of(

                                                JournalLine.builder()
                                                                .account(
                                                                                expenseAccount)
                                                                .debit(
                                                                                amount)
                                                                .credit(
                                                                                ZERO)
                                                                .description(
                                                                                expense
                                                                                                .getCategory()
                                                                                                .getLabel() +
                                                                                                " — " +
                                                                                                reference)
                                                                .build(),

                                                JournalLine.builder()
                                                                .account(
                                                                                paymentGlAccount)
                                                                .debit(
                                                                                ZERO)
                                                                .credit(
                                                                                amount)
                                                                .description(
                                                                                "Paid from " +
                                                                                                expense
                                                                                                                .getPaymentAccount()
                                                                                                                .getName()
                                                                                                +
                                                                                                " — " +
                                                                                                reference)
                                                                .build()));
        }

        // ============================================================
        // REVERSE EXPENSE
        // ============================================================

        @Transactional
        public JournalEntry reverseExpense(
                        Long orgId,
                        Long journalEntryId,
                        String reversedBy,
                        String reason) {

                return reverseEntry(
                                orgId,
                                journalEntryId,
                                reversedBy,
                                reason);
        }

        // ============================================================
        // REVERSE JOURNAL ENTRY
        // ============================================================

        @Transactional
        public JournalEntry reverseEntry(
                        Long orgId,
                        Long entryId,
                        String reversedBy,
                        String reason) {

                requireOrganizationId(
                                orgId);

                if (entryId == null) {

                        throw new IllegalArgumentException(
                                        "Journal entry ID is required");
                }

                JournalEntry original = journalRepo
                                .findByIdAndOrganization_Id(
                                                entryId,
                                                orgId)
                                .orElseThrow(
                                                () -> new IllegalArgumentException(
                                                                "Journal entry not found: "
                                                                                + entryId));

                if (original.getOrganization() == null
                                || original
                                                .getOrganization()
                                                .getId() == null) {

                        throw new IllegalStateException(
                                        "Journal entry has no valid organization");
                }

                if (!orgId.equals(
                                original
                                                .getOrganization()
                                                .getId())) {

                        throw new IllegalStateException(
                                        "Journal entry does not belong to organization "
                                                        + orgId);
                }

                if (Boolean.TRUE.equals(
                                original.getReversed())) {

                        throw new IllegalStateException(
                                        "Entry "
                                                        + entryId
                                                        + " has already been reversed");
                }

                if ("REVERSAL".equals(
                                original.getSourceType())) {

                        throw new IllegalStateException(
                                        "A reversal entry cannot itself be reversed");
                }

                if (original.getLines() == null
                                || original.getLines().isEmpty()) {

                        throw new IllegalStateException(
                                        "Journal entry has no lines: "
                                                        + entryId);
                }

                // ------------------------------------------------------------
                // PAYMENT REFUND SAFETY
                // ------------------------------------------------------------

                if ("PAYMENT_RECEIVED".equals(
                                original.getSourceType())) {

                        String paymentSourceId = original.getSourceId();

                        JournalEntry paidRefund = journalRepo
                                        .findFirstByOrganization_IdAndSourceTypeAndSourceId(
                                                        orgId,
                                                        "REFUND_PAYMENT",
                                                        paymentSourceId)
                                        .orElse(null);

                        if (paidRefund != null) {

                                throw new IllegalStateException(
                                                "Payment journal "
                                                                + entryId
                                                                + " cannot be reversed because a borrower refund "
                                                                + "has already been paid under refund journal "
                                                                + paidRefund.getId());
                        }
                }

                String reversalSourceId = String.valueOf(
                                entryId);

                JournalEntry existingReversal = journalRepo
                                .findFirstByOrganization_IdAndSourceTypeAndSourceId(
                                                orgId,
                                                "REVERSAL",
                                                reversalSourceId)
                                .orElse(null);

                if (existingReversal != null) {

                        return existingReversal;
                }

                List<JournalLine> reversedLines = new ArrayList<>();

                for (JournalLine line : original.getLines()) {

                        if (line == null) {
                                continue;
                        }

                        ChartOfAccount lineAccount = line.getAccount();

                        validateAccountOwnership(
                                        original.getOrganization(),
                                        lineAccount);

                        BigDecimal originalDebit = money(
                                        line.getDebitDecimal());

                        BigDecimal originalCredit = money(
                                        line.getCreditDecimal());

                        if (originalDebit.compareTo(
                                        ZERO) < 0
                                        || originalCredit.compareTo(
                                                        ZERO) < 0) {

                                throw new IllegalStateException(
                                                "Original journal contains negative amount " +
                                                                "on line " +
                                                                line.getId());
                        }

                        if (originalDebit.compareTo(
                                        ZERO) > 0
                                        && originalCredit.compareTo(
                                                        ZERO) > 0) {

                                throw new IllegalStateException(
                                                "Original journal line "
                                                                + line.getId()
                                                                + " contains both debit and credit");
                        }

                        reversedLines.add(
                                        JournalLine.builder()
                                                        .account(
                                                                        lineAccount)
                                                        .debit(
                                                                        originalCredit)
                                                        .credit(
                                                                        originalDebit)
                                                        .description(
                                                                        "Reversal of #"
                                                                                        + entryId
                                                                                        + " — "
                                                                                        + (line.getDescription() != null
                                                                                                        ? line.getDescription()
                                                                                                        : ""))
                                                        .build());
                }

                if (reversedLines.isEmpty()) {

                        throw new IllegalStateException(
                                        "Journal entry contains no valid lines: "
                                                        + entryId);
                }

                BigDecimal reversalDebit = ZERO;

                BigDecimal reversalCredit = ZERO;

                for (JournalLine line : reversedLines) {

                        reversalDebit = reversalDebit.add(
                                        money(
                                                        line.getDebitDecimal()));

                        reversalCredit = reversalCredit.add(
                                        money(
                                                        line.getCreditDecimal()));
                }

                reversalDebit = normalize(
                                reversalDebit);

                reversalCredit = normalize(
                                reversalCredit);

                if (reversalDebit.compareTo(
                                reversalCredit) != 0) {

                        throw new IllegalStateException(
                                        "Generated reversal does not balance for entry "
                                                        + entryId);
                }

                String reversalDescription = "Reversal of entry #"
                                + entryId;

                if (reason != null
                                && !reason.isBlank()) {

                        reversalDescription += ": "
                                        + reason.trim();
                }

                if (original.getDescription() != null
                                && !original
                                                .getDescription()
                                                .isBlank()) {

                        reversalDescription += " — "
                                        + original.getDescription();
                }

                JournalEntry reversal = JournalEntry.builder()
                                .organization(
                                                original.getOrganization())
                                .branch(
                                                original.getBranch())
                                .entryDate(
                                                LocalDate.now())
                                .sourceType(
                                                "REVERSAL")
                                .sourceId(
                                                reversalSourceId)
                                .reference(
                                                original.getReference() != null
                                                                ? original.getReference()
                                                                : "REV-" + entryId)
                                .description(
                                                reversalDescription)
                                .createdBy(
                                                reversedBy != null
                                                                && !reversedBy.isBlank()
                                                                                ? reversedBy.trim()
                                                                                : "SYSTEM")
                                .reversed(false)
                                .build();

                reversal = journalRepo.save(
                                reversal);

                for (JournalLine line : reversedLines) {

                        line.setJournalEntry(
                                        reversal);

                        lineRepo.save(
                                        line);
                }

                original.setReversed(
                                true);

                journalRepo.save(
                                original);

                log.info(
                                "Journal entry {} reversed by journal {}",
                                entryId,
                                reversal.getId());

                return reversal;
        }

        // ============================================================
        // FINANCIAL REPORTING
        // ============================================================

        @Transactional(readOnly = true)
        public Map<String, Object> getLedger(Long orgId, Long accountId) {
                requireOrganizationId(orgId);
                requireAccountId(accountId);

                ChartOfAccount acc = coaRepo.findByIdAndOrganization_Id(accountId, orgId)
                                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

                validateAccountOwnership(acc.getOrganization(), acc);

                List<JournalLine> lines = lineRepo.findLedgerForAccountAndOrganization(accountId, orgId);
                List<Map<String, Object>> rows = new ArrayList<>();
                BigDecimal running = ZERO;

                if (lines != null) {
                        for (JournalLine line : lines) {
                                if (line == null || line.getJournalEntry() == null)
                                        continue;

                                JournalEntry entry = line.getJournalEntry();
                                BigDecimal debit = money(line.getDebitDecimal());
                                BigDecimal credit = money(line.getCreditDecimal());

                                running = running.add(
                                                acc.getNormalBalance() == ChartOfAccount.NormalBalance.DEBIT
                                                                ? debit.subtract(credit)
                                                                : credit.subtract(debit));
                                running = normalize(running);

                                Map<String, Object> row = new LinkedHashMap<>();
                                row.put("entryId", entry.getId());
                                row.put("date", entry.getEntryDate());
                                row.put("reference", entry.getReference());
                                row.put("sourceType", entry.getSourceType());
                                row.put("sourceId", entry.getSourceId());
                                row.put("description", line.getDescription() != null
                                                ? line.getDescription()
                                                : entry.getDescription());
                                row.put("debit", debit);
                                row.put("credit", credit);
                                row.put("balance", running);
                                row.put("reversed", Boolean.TRUE.equals(entry.getReversed()));
                                row.put("branch", entry.getBranch() == null ? null : entry.getBranch().getName());
                                rows.add(row);
                        }
                }

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("account", acc);
                result.put("entries", rows);
                result.put("closingBalance", running);
                result.put("normalBalance", acc.getNormalBalance());
                return result;
        }

        /** Current trial balance, through today. */
        @Transactional(readOnly = true)
        public Map<String, Object> getTrialBalance(Long orgId) {
                return getTrialBalance(orgId, LocalDate.now());
        }

        @Transactional(readOnly = true)
        public Map<String, Object> getTrialBalance(Long orgId, LocalDate asOf) {
                requireOrganizationId(orgId);
                if (asOf == null)
                        throw new IllegalArgumentException("As-of date is required");

                List<ChartOfAccount> accounts = coaRepo.findByOrganization_IdOrderByCodeAsc(orgId);
                List<JournalEntry> entries = loadEntries(orgId, null, asOf);
                Map<Long, BigDecimal[]> totals = accountTotals(entries);

                List<Map<String, Object>> rows = new ArrayList<>();
                BigDecimal totalDebit = ZERO;
                BigDecimal totalCredit = ZERO;

                if (accounts != null) {
                        for (ChartOfAccount acc : accounts) {
                                if (acc == null || acc.getId() == null || acc.getType() == null
                                                || acc.getNormalBalance() == null)
                                        continue;

                                BigDecimal[] dc = totals.getOrDefault(acc.getId(), new BigDecimal[] { ZERO, ZERO });
                                BigDecimal debitActivity = normalize(dc[0]);
                                BigDecimal creditActivity = normalize(dc[1]);
                                BigDecimal net = debitActivity.subtract(creditActivity);

                                BigDecimal debitBalance = net.compareTo(ZERO) > 0 ? net : ZERO;
                                BigDecimal creditBalance = net.compareTo(ZERO) < 0 ? net.negate() : ZERO;

                                Map<String, Object> row = new LinkedHashMap<>();
                                row.put("accountId", acc.getId());
                                row.put("code", acc.getCode());
                                row.put("name", acc.getName());
                                row.put("type", acc.getType());
                                row.put("normalBalance", acc.getNormalBalance());
                                row.put("debitActivity", debitActivity);
                                row.put("creditActivity", creditActivity);
                                row.put("debit", normalize(debitBalance));
                                row.put("credit", normalize(creditBalance));
                                row.put("balance", normalize(
                                                acc.getNormalBalance() == ChartOfAccount.NormalBalance.DEBIT
                                                                ? debitBalance.subtract(creditBalance)
                                                                : creditBalance.subtract(debitBalance)));
                                rows.add(row);

                                totalDebit = totalDebit.add(debitBalance);
                                totalCredit = totalCredit.add(creditBalance);
                        }
                }

                totalDebit = normalize(totalDebit);
                totalCredit = normalize(totalCredit);
                BigDecimal difference = normalize(totalDebit.subtract(totalCredit));

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("asOf", asOf);
                result.put("accounts", rows);
                result.put("accountCount", rows.size());
                result.put("totalDebit", totalDebit);
                result.put("totalCredit", totalCredit);
                result.put("difference", difference);
                result.put("balanced", difference.compareTo(ZERO) == 0);
                return result;
        }

        /** Current balance sheet, as of today. */
        @Transactional(readOnly = true)
        public Map<String, Object> getBalanceSheet(Long orgId) {
                return getBalanceSheet(orgId, LocalDate.now());
        }

        /**
         * Statement of financial position as of a date. Assets, liabilities and
         * equity are point-in-time balances. Income and expense balances are
         * included as unclosed current earnings so Assets = Liabilities + Equity
         * remains mathematically reconciled until the system implements formal
         * period-closing entries.
         */
        @Transactional(readOnly = true)
        public Map<String, Object> getBalanceSheet(Long orgId, LocalDate asOf) {
                requireOrganizationId(orgId);
                if (asOf == null)
                        throw new IllegalArgumentException("As-of date is required");

                List<ChartOfAccount> accounts = coaRepo.findByOrganization_IdOrderByCodeAsc(orgId);
                List<JournalEntry> entries = loadEntries(orgId, null, asOf);
                Map<Long, BigDecimal[]> totals = accountTotals(entries);

                Map<ChartOfAccount.AccountType, List<Map<String, Object>>> byType = new EnumMap<>(
                                ChartOfAccount.AccountType.class);
                for (ChartOfAccount.AccountType type : ChartOfAccount.AccountType.values()) {
                        byType.put(type, new ArrayList<>());
                }

                BigDecimal totalAssets = ZERO;
                BigDecimal totalLiabilities = ZERO;
                BigDecimal totalEquity = ZERO;
                BigDecimal totalIncome = ZERO;
                BigDecimal totalExpense = ZERO;
                BigDecimal contraAssets = ZERO;

                if (accounts != null) {
                        for (ChartOfAccount acc : accounts) {
                                if (acc == null || acc.getId() == null || acc.getType() == null
                                                || acc.getNormalBalance() == null)
                                        continue;

                                BigDecimal balance = accountBalance(acc, totals);
                                if (balance.compareTo(ZERO) == 0) {
                                        // Keep zero accounts available for the UI, but do not distort totals.
                                }

                                Map<String, Object> row = new LinkedHashMap<>();
                                row.put("accountId", acc.getId());
                                row.put("code", acc.getCode());
                                row.put("name", acc.getName());
                                row.put("type", acc.getType());
                                row.put("normalBalance", acc.getNormalBalance());
                                row.put("balance", balance);
                                byType.get(acc.getType()).add(row);

                                switch (acc.getType()) {
                                        case ASSET -> {
                                                if (acc.getNormalBalance() == ChartOfAccount.NormalBalance.CREDIT) {
                                                        contraAssets = contraAssets.add(balance);
                                                        totalAssets = totalAssets.subtract(balance);
                                                } else {
                                                        totalAssets = totalAssets.add(balance);
                                                }
                                        }
                                        case LIABILITY -> totalLiabilities = totalLiabilities.add(balance);
                                        case EQUITY -> totalEquity = totalEquity.add(balance);
                                        case INCOME -> totalIncome = totalIncome.add(balance);
                                        case EXPENSE -> totalExpense = totalExpense.add(balance);
                                }
                        }
                }

                BigDecimal unclosedProfit = normalize(totalIncome.subtract(totalExpense));
                BigDecimal totalEquityIncludingProfit = normalize(totalEquity.add(unclosedProfit));
                BigDecimal liabilitiesPlusEquity = normalize(totalLiabilities.add(totalEquityIncludingProfit));
                BigDecimal balanceDifference = normalize(totalAssets.subtract(liabilitiesPlusEquity));

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("asOf", asOf);
                result.put("assets", byType.get(ChartOfAccount.AccountType.ASSET));
                result.put("liabilities", byType.get(ChartOfAccount.AccountType.LIABILITY));
                result.put("equity", byType.get(ChartOfAccount.AccountType.EQUITY));
                result.put("incomeAccounts", byType.get(ChartOfAccount.AccountType.INCOME));
                result.put("expenseAccounts", byType.get(ChartOfAccount.AccountType.EXPENSE));
                result.put("contraAssets", normalize(contraAssets));
                result.put("totalAssets", normalize(totalAssets));
                result.put("totalLiabilities", normalize(totalLiabilities));
                result.put("ownerEquity", normalize(totalEquity));
                result.put("unclosedProfit", unclosedProfit);
                result.put("totalEquity", totalEquityIncludingProfit);
                result.put("liabilitiesPlusEquity", liabilitiesPlusEquity);
                result.put("balanceDifference", balanceDifference);
                result.put("balanced", balanceDifference.compareTo(ZERO) == 0);
                return result;
        }

        /**
         * Accrual-basis income statement / profit and loss for an exact period.
         * Reversal entries are treated as normal accounting entries on their
         * posting date, preserving the audit trail and period integrity.
         */
        @Transactional(readOnly = true)
        public Map<String, Object> getProfitAndLoss(Long orgId, LocalDate from, LocalDate to) {
                validateDateRange(from, to);
                requireOrganizationId(orgId);

                List<JournalEntry> entries = loadEntries(orgId, from, to);
                Map<String, AccountReportTotal> totals = new LinkedHashMap<>();

                for (JournalEntry entry : entries) {
                        if (entry == null || entry.getLines() == null)
                                continue;
                        for (JournalLine line : entry.getLines()) {
                                if (line == null || line.getAccount() == null)
                                        continue;
                                ChartOfAccount acc = line.getAccount();
                                if (acc.getType() != ChartOfAccount.AccountType.INCOME &&
                                                acc.getType() != ChartOfAccount.AccountType.EXPENSE)
                                        continue;

                                BigDecimal debit = money(line.getDebitDecimal());
                                BigDecimal credit = money(line.getCreditDecimal());
                                BigDecimal amount = acc.getType() == ChartOfAccount.AccountType.INCOME
                                                ? credit.subtract(debit)
                                                : debit.subtract(credit);

                                AccountReportTotal total = totals.computeIfAbsent(
                                                acc.getCode(),
                                                k -> new AccountReportTotal(acc.getCode(), acc.getName(),
                                                                acc.getType()));
                                total.amount = normalize(total.amount.add(amount));
                        }
                }

                List<Map<String, Object>> income = new ArrayList<>();
                List<Map<String, Object>> expenses = new ArrayList<>();
                BigDecimal totalIncome = ZERO;
                BigDecimal totalExpense = ZERO;

                for (AccountReportTotal total : totals.values()) {
                        BigDecimal amount = normalize(total.amount);
                        if (amount.compareTo(ZERO) == 0)
                                continue;

                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("code", total.code);
                        row.put("name", total.name);
                        row.put("amount", amount);

                        if (total.type == ChartOfAccount.AccountType.INCOME) {
                                income.add(row);
                                totalIncome = totalIncome.add(amount);
                        } else {
                                expenses.add(row);
                                totalExpense = totalExpense.add(amount);
                        }
                }

                income.sort((a, b) -> String.valueOf(a.get("code")).compareTo(String.valueOf(b.get("code"))));
                expenses.sort((a, b) -> String.valueOf(a.get("code")).compareTo(String.valueOf(b.get("code"))));

                totalIncome = normalize(totalIncome);
                totalExpense = normalize(totalExpense);
                BigDecimal netProfit = normalize(totalIncome.subtract(totalExpense));
                BigDecimal margin = totalIncome.compareTo(ZERO) == 0
                                ? ZERO
                                : normalize(netProfit.multiply(new BigDecimal("100"))
                                                .divide(totalIncome, 6, MONEY_ROUNDING));

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("from", from);
                result.put("to", to);
                result.put("income", income);
                result.put("expenses", expenses);
                result.put("expense", expenses); // backward compatibility
                result.put("totalIncome", totalIncome);
                result.put("totalExpense", totalExpense);
                result.put("netIncome", netProfit); // backward compatibility
                result.put("netProfit", netProfit);
                result.put("profitMarginPercent", margin);
                result.put("profitable", netProfit.compareTo(ZERO) >= 0);
                return result;
        }

        /** Monthly P&L plus year-to-date comparison. */
        @Transactional(readOnly = true)
        public Map<String, Object> getMonthlyProfitAndLoss(Long orgId, int year, int month) {
                validateYearMonth(year, month);
                LocalDate from = LocalDate.of(year, month, 1);
                LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
                LocalDate ytdFrom = LocalDate.of(year, 1, 1);

                Map<String, Object> monthly = getProfitAndLoss(orgId, from, to);
                Map<String, Object> ytd = getProfitAndLoss(orgId, ytdFrom, to);

                Map<String, Object> result = new LinkedHashMap<>();
                result.putAll(monthly);
                result.put("year", year);
                result.put("month", month);
                result.put("period", from.getMonth().toString() + " " + year);
                result.put("ytdFrom", ytdFrom);
                result.put("ytdTotalIncome", ytd.get("totalIncome"));
                result.put("ytdTotalExpense", ytd.get("totalExpense"));
                result.put("ytdNetProfit", ytd.get("netProfit"));
                return result;
        }

        /** Monthly expense report with category totals and YTD expense. */
        @Transactional(readOnly = true)
        public Map<String, Object> getMonthlyExpenses(Long orgId, int year, int month) {
                Map<String, Object> pnl = getMonthlyProfitAndLoss(orgId, year, month);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("year", year);
                result.put("month", month);
                result.put("from", pnl.get("from"));
                result.put("to", pnl.get("to"));
                result.put("expenses", pnl.get("expenses"));
                result.put("expense", pnl.get("expenses"));
                result.put("totalExpense", pnl.get("totalExpense"));
                result.put("ytdTotalExpense", pnl.get("ytdTotalExpense"));
                return result;
        }

        /**
         * Production reporting facade. One call supplies the core accounting
         * package for a reporting period: trial balance, P&L, balance sheet and
         * cash flow, plus reconciliation flags.
         */
        @Transactional(readOnly = true)
        public Map<String, Object> getFinancialReport(Long orgId, LocalDate from, LocalDate to) {
                validateDateRange(from, to);
                requireOrganizationId(orgId);

                Map<String, Object> pnl = getProfitAndLoss(orgId, from, to);
                Map<String, Object> trialBalance = getTrialBalance(orgId, to);
                Map<String, Object> balanceSheet = getBalanceSheet(orgId, to);
                Map<String, Object> cashFlow = getCashFlow(orgId, from, to);

                Map<String, Object> reconciliation = new LinkedHashMap<>();
                reconciliation.put("trialBalanceBalanced", trialBalance.get("balanced"));
                reconciliation.put("balanceSheetBalanced", balanceSheet.get("balanced"));
                reconciliation.put("trialBalanceDifference", trialBalance.get("difference"));
                reconciliation.put("balanceSheetDifference", balanceSheet.get("balanceDifference"));
                reconciliation.put("cashFlowReconciles", cashFlow.get("reconciles"));

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("from", from);
                result.put("to", to);
                result.put("generatedAt", java.time.LocalDateTime.now());
                result.put("trialBalance", trialBalance);
                result.put("profitAndLoss", pnl);
                result.put("balanceSheet", balanceSheet);
                result.put("cashFlow", cashFlow);
                result.put("reconciliation", reconciliation);
                result.put("allReportsHealthy",
                                Boolean.TRUE.equals(trialBalance.get("balanced")) &&
                                                Boolean.TRUE.equals(balanceSheet.get("balanced")) &&
                                                Boolean.TRUE.equals(cashFlow.get("reconciles")));
                return result;
        }

        /** Convenience monthly financial package. */
        @Transactional(readOnly = true)
        public Map<String, Object> getMonthlyFinancialReport(Long orgId, int year, int month) {
                validateYearMonth(year, month);
                LocalDate from = LocalDate.of(year, month, 1);
                LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
                Map<String, Object> result = getFinancialReport(orgId, from, to);
                result.put("year", year);
                result.put("month", month);
                result.put("period", from.getMonth().toString() + " " + year);
                return result;
        }

        /**
         * Cash flow based on the actual cash GL account (1000), not loan model
         * balances. This prevents reporting a loan balance change as cash when no
         * cash journal was posted.
         */
        @Transactional(readOnly = true)
        public Map<String, Object> getCashFlow(Long orgId, LocalDate from, LocalDate to) {
                validateDateRange(from, to);
                requireOrganizationId(orgId);

                ChartOfAccount cash = coaRepo.findByOrganization_IdAndCode(orgId, "1000")
                                .orElseThrow(() -> new IllegalStateException(
                                                "Cash and Bank account 1000 is not configured"));

                List<JournalLine> cashLines = lineRepo.findByAccount_IdAndOrganization_Id(cash.getId(), orgId);

                BigDecimal openingCash = ZERO;
                BigDecimal operatingInflows = ZERO;
                BigDecimal operatingOutflows = ZERO;
                BigDecimal lendingOutflows = ZERO;
                BigDecimal financingInflows = ZERO;
                BigDecimal financingOutflows = ZERO;
                BigDecimal investingInflows = ZERO;
                BigDecimal investingOutflows = ZERO;
                BigDecimal otherInflows = ZERO;
                BigDecimal otherOutflows = ZERO;

                if (cashLines != null) {
                        for (JournalLine line : cashLines) {
                                if (line == null || line.getJournalEntry() == null)
                                        continue;
                                JournalEntry entry = line.getJournalEntry();
                                BigDecimal movement = money(line.getDebitDecimal())
                                                .subtract(money(line.getCreditDecimal()));

                                if (entry.getEntryDate() != null && entry.getEntryDate().isBefore(from)) {
                                        openingCash = openingCash.add(movement);
                                        continue;
                                }
                                if (entry.getEntryDate() == null || entry.getEntryDate().isAfter(to))
                                        continue;

                                String source = entry.getSourceType() == null ? ""
                                                : entry.getSourceType().trim().toUpperCase();
                                if (movement.compareTo(ZERO) >= 0) {
                                        switch (source) {
                                                case "PAYMENT_RECEIVED" ->
                                                        operatingInflows = operatingInflows.add(movement);
                                                case "REFUND_PAYMENT" -> otherOutflows = otherOutflows.add(movement);
                                                case "LOAN_DISBURSEMENT" -> otherInflows = otherInflows.add(movement);
                                                case "EQUITY_CONTRIBUTION", "OWNER_CONTRIBUTION", "CAPITAL_INJECTION",
                                                                "DEPOSIT_RECEIVED" ->
                                                        financingInflows = financingInflows.add(movement);
                                                default -> otherInflows = otherInflows.add(movement);
                                        }
                                } else {
                                        BigDecimal outflow = movement.negate();
                                        switch (source) {
                                                case "LOAN_DISBURSEMENT" ->
                                                        lendingOutflows = lendingOutflows.add(outflow);
                                                case "EXPENSE", "OPERATING_EXPENSE" ->
                                                        operatingOutflows = operatingOutflows.add(outflow);
                                                case "REFUND_PAYMENT" ->
                                                        operatingOutflows = operatingOutflows.add(outflow);
                                                case "OWNER_DRAW", "DIVIDEND", "EQUITY_DISTRIBUTION",
                                                                "DEPOSIT_REPAYMENT" ->
                                                        financingOutflows = financingOutflows.add(outflow);
                                                default -> otherOutflows = otherOutflows.add(outflow);
                                        }
                                }
                        }
                }

                // Lending is operating for a loan-finance business, so expose it
                // separately while also including it in operating net cash flow.
                BigDecimal operatingNet = operatingInflows
                                .subtract(operatingOutflows)
                                .subtract(lendingOutflows);
                BigDecimal investingNet = investingInflows.subtract(investingOutflows);
                BigDecimal financingNet = financingInflows.subtract(financingOutflows);
                BigDecimal otherNet = otherInflows.subtract(otherOutflows);
                BigDecimal netChange = operatingNet.add(investingNet).add(financingNet).add(otherNet);
                BigDecimal closingCash = normalize(openingCash.add(netChange));
                BigDecimal actualClosingCash = cashBalanceAsOf(cash, orgId, to);
                BigDecimal reconciliationDifference = normalize(closingCash.subtract(actualClosingCash));

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("from", from);
                result.put("to", to);
                result.put("openingCash", normalize(openingCash));
                result.put("cashFromCollections", normalize(operatingInflows));
                result.put("cashUsedForLending", normalize(lendingOutflows));
                result.put("operatingCashOutflows", normalize(operatingOutflows));
                result.put("operatingNetCashFlow", normalize(operatingNet));
                result.put("investingCashInflow", normalize(investingInflows));
                result.put("investingCashOutflow", normalize(investingOutflows));
                result.put("investingNetCashFlow", normalize(investingNet));
                result.put("financingCashInflow", normalize(financingInflows));
                result.put("financingCashOutflow", normalize(financingOutflows));
                result.put("financingNetCashFlow", normalize(financingNet));
                result.put("otherCashInflow", normalize(otherInflows));
                result.put("otherCashOutflow", normalize(otherOutflows));
                result.put("otherNetCashFlow", normalize(otherNet));
                result.put("netChangeInCash", normalize(netChange));
                result.put("closingCash", closingCash);
                result.put("actualClosingCash", actualClosingCash);
                result.put("reconciliationDifference", reconciliationDifference);
                result.put("reconciles", reconciliationDifference.compareTo(ZERO) == 0);
                // Backward-compatible keys.
                result.put("cashFromFees", ZERO);
                result.put("cashRefundedToBorrowers", normalize(otherOutflows));
                result.put("otherCashMovement", normalize(otherNet));
                return result;
        }

        /** Branch cash activity summary. */
        @Transactional(readOnly = true)
        public List<Map<String, Object>> getBranchSummary(Long orgId, LocalDate from, LocalDate to) {
                validateDateRange(from, to);
                requireOrganizationId(orgId);

                List<JournalEntry> entries = loadEntries(orgId, from, to);
                Map<String, BigDecimal[]> byBranch = new LinkedHashMap<>();

                for (JournalEntry entry : entries) {
                        if (entry == null || entry.getLines() == null)
                                continue;
                        String branchName = entry.getBranch() != null && entry.getBranch().getName() != null
                                        ? entry.getBranch().getName()
                                        : "Unassigned";
                        BigDecimal[] totals = byBranch.computeIfAbsent(branchName,
                                        k -> new BigDecimal[] { ZERO, ZERO, ZERO, ZERO });

                        for (JournalLine line : entry.getLines()) {
                                if (line == null || line.getAccount() == null ||
                                                !"1000".equals(line.getAccount().getCode()))
                                        continue;
                                BigDecimal debit = money(line.getDebitDecimal());
                                BigDecimal credit = money(line.getCreditDecimal());
                                String source = entry.getSourceType() == null ? "" : entry.getSourceType();

                                switch (source) {
                                        case "LOAN_DISBURSEMENT" ->
                                                totals[0] = totals[0].add(credit.subtract(debit).max(ZERO));
                                        case "PAYMENT_RECEIVED" ->
                                                totals[1] = totals[1].add(debit.subtract(credit).max(ZERO));
                                        case "REFUND_PAYMENT" ->
                                                totals[3] = totals[3].add(credit.subtract(debit).max(ZERO));
                                        default -> {
                                        }
                                }
                        }
                }

                List<Map<String, Object>> rows = new ArrayList<>();
                for (Map.Entry<String, BigDecimal[]> entry : byBranch.entrySet()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("branch", entry.getKey());
                        row.put("disbursed", normalize(entry.getValue()[0]));
                        row.put("collected", normalize(entry.getValue()[1]));
                        row.put("feeIncome", ZERO);
                        row.put("refunded", normalize(entry.getValue()[3]));
                        rows.add(row);
                }
                return rows;
        }

        // ============================================================
        // REPORTING HELPERS
        // ============================================================

        private void validateYearMonth(int year, int month) {
                if (year < 1900 || year > 9999) {
                        throw new IllegalArgumentException("Year is outside the supported range");
                }
                if (month < 1 || month > 12) {
                        throw new IllegalArgumentException("Month must be between 1 and 12");
                }
        }

        private List<JournalEntry> loadEntries(Long orgId, LocalDate from, LocalDate to) {
                requireOrganizationId(orgId);
                if (to == null)
                        throw new IllegalArgumentException("End date is required");
                if (from != null && to.isBefore(from))
                        throw new IllegalArgumentException("End date cannot be before start date");

                LocalDate start = from == null ? LocalDate.of(1900, 1, 1) : from;
                List<JournalEntry> entries = journalRepo
                                .findByOrganization_IdAndEntryDateBetweenOrderByEntryDateAsc(orgId, start, to);
                return entries == null ? List.of() : entries;
        }

        private Map<Long, BigDecimal[]> accountTotals(List<JournalEntry> entries) {
                Map<Long, BigDecimal[]> totals = new LinkedHashMap<>();
                for (JournalEntry entry : entries) {
                        if (entry == null || entry.getLines() == null)
                                continue;
                        for (JournalLine line : entry.getLines()) {
                                if (line == null || line.getAccount() == null || line.getAccount().getId() == null)
                                        continue;
                                BigDecimal[] dc = totals.computeIfAbsent(line.getAccount().getId(),
                                                k -> new BigDecimal[] { ZERO, ZERO });
                                dc[0] = dc[0].add(money(line.getDebitDecimal()));
                                dc[1] = dc[1].add(money(line.getCreditDecimal()));
                        }
                }
                return totals;
        }

        private BigDecimal accountBalance(ChartOfAccount acc, Map<Long, BigDecimal[]> totals) {
                BigDecimal[] dc = totals.getOrDefault(acc.getId(), new BigDecimal[] { ZERO, ZERO });
                BigDecimal debit = money(dc[0]);
                BigDecimal credit = money(dc[1]);
                return normalize(acc.getNormalBalance() == ChartOfAccount.NormalBalance.DEBIT
                                ? debit.subtract(credit)
                                : credit.subtract(debit));
        }

        private BigDecimal cashBalanceAsOf(ChartOfAccount cash, Long orgId, LocalDate asOf) {
                List<JournalLine> lines = lineRepo.findByAccount_IdAndOrganization_Id(cash.getId(), orgId);
                BigDecimal balance = ZERO;
                if (lines != null) {
                        for (JournalLine line : lines) {
                                if (line == null || line.getJournalEntry() == null)
                                        continue;
                                LocalDate date = line.getJournalEntry().getEntryDate();
                                if (date == null || date.isAfter(asOf))
                                        continue;
                                balance = balance.add(money(line.getDebitDecimal()))
                                                .subtract(money(line.getCreditDecimal()));
                        }
                }
                return normalize(balance);
        }

        private static final class AccountReportTotal {
                private final String code;
                private final String name;
                private final ChartOfAccount.AccountType type;
                private BigDecimal amount = ZERO;

                private AccountReportTotal(String code, String name, ChartOfAccount.AccountType type) {
                        this.code = code;
                        this.name = name;
                        this.type = type;
                }
        }

}