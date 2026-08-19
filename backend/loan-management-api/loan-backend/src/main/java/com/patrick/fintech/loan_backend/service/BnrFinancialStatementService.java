package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.ChartOfAccount;
import com.patrick.fintech.loan_backend.model.JournalEntry;
import com.patrick.fintech.loan_backend.model.JournalLine;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.repository.ChartOfAccountRepository;
import com.patrick.fintech.loan_backend.repository.JournalEntryRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class BnrFinancialStatementService {

        private final ChartOfAccountRepository chartOfAccountRepository;

        private final JournalEntryRepository journalEntryRepository;

        private final LoanRepository loanRepository;

        private static final int MONEY_SCALE = 6;

        private static final int REPORT_SCALE = 2;

        private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;

        private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(
                        MONEY_SCALE,
                        MONEY_ROUNDING);

        private static final BigDecimal HUNDRED = new BigDecimal("100");

        /*
         * One cent is the materiality threshold used for balance checks.
         */
        private static final BigDecimal BALANCE_TOLERANCE = new BigDecimal("0.01");

        /*
         * Accounting history lower bound.
         */
        private static final LocalDate ACCOUNTING_EPOCH = LocalDate.of(
                        1970,
                        1,
                        1);

        // ============================================================
        // MAIN FINANCIAL STATEMENT
        // ============================================================

        public Map<String, Object> buildFinancialStatement(
                        Long organizationId,
                        LocalDate from,
                        LocalDate to) {

                validateDates(
                                organizationId,
                                from,
                                to);

                log.info(
                                "Generating BNR financial statement: organizationId={}, from={}, to={}",
                                organizationId,
                                from,
                                to);

                // ========================================================
                // LOAD CHART OF ACCOUNTS
                // ========================================================

                List<ChartOfAccount> accounts = chartOfAccountRepository
                                .findByOrganization_IdOrderByCodeAsc(
                                                organizationId);

                if (accounts == null) {
                        accounts = new ArrayList<>();
                }

                accounts = accounts.stream()
                                .filter(Objects::nonNull)
                                .filter(account -> account.getId() != null)
                                .sorted(
                                                Comparator.comparing(
                                                                account -> safeString(
                                                                                account.getCode())))
                                .toList();

                // ========================================================
                // LOAD HISTORICAL JOURNAL ENTRIES
                // ========================================================

                List<JournalEntry> historicalEntries = journalEntryRepository
                                .findByOrganization_IdAndEntryDateBetweenOrderByEntryDateAsc(
                                                organizationId,
                                                ACCOUNTING_EPOCH,
                                                to);

                if (historicalEntries == null) {
                        historicalEntries = new ArrayList<>();
                }

                // ========================================================
                // LOAD PERIOD JOURNAL ENTRIES
                // ========================================================

                List<JournalEntry> periodEntries = journalEntryRepository
                                .findByOrganization_IdAndEntryDateBetweenOrderByEntryDateAsc(
                                                organizationId,
                                                from,
                                                to);

                if (periodEntries == null) {
                        periodEntries = new ArrayList<>();
                }

                // ========================================================
                // ACTIVE ENTRIES
                // ========================================================

                historicalEntries = activeEntries(
                                historicalEntries);

                periodEntries = activeEntries(
                                periodEntries);

                // ========================================================
                // ACCOUNT MAPS
                // ========================================================

                Map<Long, BigDecimal> endingBalances = createBalanceMap(
                                accounts);

                Map<Long, BigDecimal> periodDebits = createBalanceMap(
                                accounts);

                Map<Long, BigDecimal> periodCredits = createBalanceMap(
                                accounts);

                Map<Long, BigDecimal> historicalIncome = createBalanceMap(
                                accounts);

                Map<Long, BigDecimal> historicalExpenses = createBalanceMap(
                                accounts);

                // ========================================================
                // PROCESS HISTORICAL ENTRIES
                // ========================================================

                for (JournalEntry entry : historicalEntries) {

                        processEndingBalanceEntry(
                                        entry,
                                        endingBalances,
                                        organizationId);

                        processIncomeExpenseEntry(
                                        entry,
                                        historicalIncome,
                                        historicalExpenses,
                                        organizationId);
                }

                // ========================================================
                // PROCESS CURRENT PERIOD
                // ========================================================

                for (JournalEntry entry : periodEntries) {

                        processPeriodEntry(
                                        entry,
                                        periodDebits,
                                        periodCredits,
                                        organizationId);
                }

                // ========================================================
                // STATEMENT COLLECTIONS
                // ========================================================

                List<Map<String, Object>> assets = new ArrayList<>();

                List<Map<String, Object>> liabilities = new ArrayList<>();

                List<Map<String, Object>> equity = new ArrayList<>();

                List<Map<String, Object>> income = new ArrayList<>();

                List<Map<String, Object>> expenses = new ArrayList<>();

                // ========================================================
                // TOTALS
                // ========================================================

                BigDecimal totalAssets = ZERO;

                BigDecimal totalLiabilities = ZERO;

                BigDecimal totalEquity = ZERO;

                BigDecimal totalIncome = ZERO;

                BigDecimal totalExpenses = ZERO;

                BigDecimal historicalIncomeTotal = ZERO;

                BigDecimal historicalExpenseTotal = ZERO;

                // ========================================================
                // CLASSIFY ACCOUNTS
                // ========================================================

                for (ChartOfAccount account : accounts) {

                        if (account == null) {
                                continue;
                        }

                        Long accountId = account.getId();

                        if (accountId == null) {
                                continue;
                        }

                        if (account.getType() == null) {
                                continue;
                        }

                        BigDecimal endingBalance = normalizeMoney(
                                        endingBalances.getOrDefault(
                                                        accountId,
                                                        ZERO));

                        BigDecimal debit = normalizeMoney(
                                        periodDebits.getOrDefault(
                                                        accountId,
                                                        ZERO));

                        BigDecimal credit = normalizeMoney(
                                        periodCredits.getOrDefault(
                                                        accountId,
                                                        ZERO));

                        BigDecimal historicalIncomeAmount = normalizeMoney(
                                        historicalIncome.getOrDefault(
                                                        accountId,
                                                        ZERO));

                        BigDecimal historicalExpenseAmount = normalizeMoney(
                                        historicalExpenses.getOrDefault(
                                                        accountId,
                                                        ZERO));

                        switch (account.getType()) {

                                // =================================================
                                // ASSET
                                // =================================================

                                case ASSET -> {

                                        if (!isMaterial(endingBalance)) {
                                                continue;
                                        }

                                        Map<String, Object> row = accountRow(
                                                        account,
                                                        endingBalance);

                                        boolean contraAsset = isContraAsset(
                                                        account);

                                        if (contraAsset) {

                                                row.put(
                                                                "presentation",
                                                                "CONTRA_ASSET");

                                                BigDecimal deduction = normalizeMoney(
                                                                endingBalance
                                                                                .abs()
                                                                                .negate());

                                                row.put(
                                                                "deduction",
                                                                deduction);

                                                totalAssets = subtract(
                                                                totalAssets,
                                                                endingBalance.abs());

                                        } else {

                                                row.put(
                                                                "presentation",
                                                                "ASSET");

                                                totalAssets = add(
                                                                totalAssets,
                                                                endingBalance);
                                        }

                                        assets.add(row);
                                }

                                // =================================================
                                // LIABILITY
                                // =================================================

                                case LIABILITY -> {

                                        if (!isMaterial(endingBalance)) {
                                                continue;
                                        }

                                        Map<String, Object> row = accountRow(
                                                        account,
                                                        endingBalance);

                                        row.put(
                                                        "presentation",
                                                        "LIABILITY");

                                        totalLiabilities = add(
                                                        totalLiabilities,
                                                        normalizedStatementBalance(
                                                                        account,
                                                                        endingBalance));

                                        liabilities.add(row);
                                }

                                // =================================================
                                // EQUITY
                                // =================================================

                                case EQUITY -> {

                                        if (!isMaterial(endingBalance)) {
                                                continue;
                                        }

                                        Map<String, Object> row = accountRow(
                                                        account,
                                                        endingBalance);

                                        row.put(
                                                        "presentation",
                                                        "EQUITY");

                                        totalEquity = add(
                                                        totalEquity,
                                                        normalizedStatementBalance(
                                                                        account,
                                                                        endingBalance));

                                        equity.add(row);
                                }

                                // =================================================
                                // INCOME
                                // =================================================

                                case INCOME -> {

                                        BigDecimal periodIncome = subtract(
                                                        credit,
                                                        debit);

                                        historicalIncomeTotal = add(
                                                        historicalIncomeTotal,
                                                        historicalIncomeAmount);

                                        if (!isMaterial(periodIncome)) {
                                                continue;
                                        }

                                        Map<String, Object> row = accountRow(
                                                        account,
                                                        periodIncome);

                                        row.put(
                                                        "presentation",
                                                        "INCOME");

                                        row.put(
                                                        "periodDebit",
                                                        debit);

                                        row.put(
                                                        "periodCredit",
                                                        credit);

                                        row.put(
                                                        "periodAmount",
                                                        periodIncome);

                                        totalIncome = add(
                                                        totalIncome,
                                                        periodIncome);

                                        income.add(row);
                                }

                                // =================================================
                                // EXPENSE
                                // =================================================

                                case EXPENSE -> {

                                        BigDecimal periodExpense = subtract(
                                                        debit,
                                                        credit);

                                        historicalExpenseTotal = add(
                                                        historicalExpenseTotal,
                                                        historicalExpenseAmount);

                                        if (!isMaterial(periodExpense)) {
                                                continue;
                                        }

                                        Map<String, Object> row = accountRow(
                                                        account,
                                                        periodExpense);

                                        row.put(
                                                        "presentation",
                                                        "EXPENSE");

                                        row.put(
                                                        "periodDebit",
                                                        debit);

                                        row.put(
                                                        "periodCredit",
                                                        credit);

                                        row.put(
                                                        "periodAmount",
                                                        periodExpense);

                                        totalExpenses = add(
                                                        totalExpenses,
                                                        periodExpense);

                                        expenses.add(row);
                                }
                        }
                }

                // ========================================================
                // CURRENT PERIOD NET INCOME
                // ========================================================

                BigDecimal netIncome = subtract(
                                totalIncome,
                                totalExpenses);

                // ========================================================
                // CURRENT PERIOD EQUITY
                // ========================================================

                BigDecimal totalEquityIncludingProfit = add(
                                totalEquity,
                                netIncome);

                // ========================================================
                // LIABILITIES + EQUITY
                // ========================================================

                BigDecimal liabilitiesPlusEquity = add(
                                totalLiabilities,
                                totalEquityIncludingProfit);

                // ========================================================
                // BALANCE SHEET DIFFERENCE
                // ========================================================

                BigDecimal balanceDifference = subtract(
                                totalAssets,
                                liabilitiesPlusEquity);

                boolean balanceSheetBalanced = isWithinTolerance(
                                balanceDifference);

                // ========================================================
                // TRIAL BALANCE
                // ========================================================

                BigDecimal trialBalanceDebit = ZERO;

                BigDecimal trialBalanceCredit = ZERO;

                for (JournalEntry entry : periodEntries) {

                        if (entry == null) {
                                continue;
                        }

                        if (entry.getLines() == null) {
                                continue;
                        }

                        validateJournalEntry(
                                        entry,
                                        organizationId);

                        for (JournalLine line : entry.getLines()) {

                                if (line == null) {
                                        continue;
                                }

                                trialBalanceDebit = add(
                                                trialBalanceDebit,
                                                value(
                                                                line.getDebit()));

                                trialBalanceCredit = add(
                                                trialBalanceCredit,
                                                value(
                                                                line.getCredit()));
                        }
                }

                trialBalanceDebit = normalizeMoney(
                                trialBalanceDebit);

                trialBalanceCredit = normalizeMoney(
                                trialBalanceCredit);

                BigDecimal trialBalanceDifference = subtract(
                                trialBalanceDebit,
                                trialBalanceCredit);

                boolean trialBalanceBalanced = isWithinTolerance(
                                trialBalanceDifference);

                // ========================================================
                // LOAN PORTFOLIO STATISTICS
                // ========================================================

                Map<String, Object> loanPortfolio = buildLoanPortfolioStatistics(
                                organizationId,
                                from,
                                to);

                // ========================================================
                // STATEMENT OF FINANCIAL POSITION
                // ========================================================

                Map<String, Object> statementOfFinancialPosition = new LinkedHashMap<>();

                statementOfFinancialPosition.put(
                                "assets",
                                assets);

                statementOfFinancialPosition.put(
                                "liabilities",
                                liabilities);

                statementOfFinancialPosition.put(
                                "equity",
                                equity);

                statementOfFinancialPosition.put(
                                "currentPeriodNetIncome",
                                netIncome);

                statementOfFinancialPosition.put(
                                "totalAssets",
                                totalAssets);

                statementOfFinancialPosition.put(
                                "totalLiabilities",
                                totalLiabilities);

                statementOfFinancialPosition.put(
                                "totalEquity",
                                totalEquityIncludingProfit);

                statementOfFinancialPosition.put(
                                "liabilitiesPlusEquity",
                                liabilitiesPlusEquity);

                statementOfFinancialPosition.put(
                                "balanceDifference",
                                balanceDifference);

                statementOfFinancialPosition.put(
                                "balanced",
                                balanceSheetBalanced);

                // ========================================================
                // INCOME STATEMENT
                // ========================================================

                Map<String, Object> incomeStatement = new LinkedHashMap<>();

                incomeStatement.put(
                                "income",
                                income);

                incomeStatement.put(
                                "expenses",
                                expenses);

                incomeStatement.put(
                                "totalIncome",
                                totalIncome);

                incomeStatement.put(
                                "totalExpenses",
                                totalExpenses);

                incomeStatement.put(
                                "netIncome",
                                netIncome);

                // ========================================================
                // TRIAL BALANCE
                // ========================================================

                Map<String, Object> trialBalance = new LinkedHashMap<>();

                trialBalance.put(
                                "debit",
                                trialBalanceDebit);

                trialBalance.put(
                                "credit",
                                trialBalanceCredit);

                trialBalance.put(
                                "difference",
                                trialBalanceDifference);

                trialBalance.put(
                                "balanced",
                                trialBalanceBalanced);

                // ========================================================
                // ACCOUNTING INTEGRITY
                // ========================================================

                boolean accountingBalanced = balanceSheetBalanced
                                && trialBalanceBalanced;

                // ========================================================
                // FINAL REPORT
                // ========================================================

                Map<String, Object> result = new LinkedHashMap<>();

                result.put(
                                "reportType",
                                "BNR_FINANCIAL_STATEMENT");

                result.put(
                                "organizationId",
                                organizationId);

                result.put(
                                "from",
                                from);

                result.put(
                                "to",
                                to);

                result.put(
                                "generatedAt",
                                LocalDateTime.now());

                result.put(
                                "currencyPrecision",
                                MONEY_SCALE);

                result.put(
                                "materialityTolerance",
                                BALANCE_TOLERANCE);

                result.put(
                                "statementOfFinancialPosition",
                                statementOfFinancialPosition);

                result.put(
                                "incomeStatement",
                                incomeStatement);

                result.put(
                                "trialBalance",
                                trialBalance);

                // ========================================================
                // BNR LOAN PORTFOLIO
                // ========================================================

                result.put(
                                "loanPortfolio",
                                loanPortfolio);

                result.put(
                                "accountingBalanced",
                                accountingBalanced);

                result.put(
                                "balanceSheetBalanced",
                                balanceSheetBalanced);

                result.put(
                                "balanceDifference",
                                balanceDifference);

                result.put(
                                "trialBalanceDebit",
                                trialBalanceDebit);

                result.put(
                                "trialBalanceCredit",
                                trialBalanceCredit);

                result.put(
                                "trialBalanceDifference",
                                trialBalanceDifference);

                result.put(
                                "trialBalanceBalanced",
                                trialBalanceBalanced);

                result.put(
                                "historicalIncomeTotal",
                                historicalIncomeTotal);

                result.put(
                                "historicalExpenseTotal",
                                historicalExpenseTotal);

                log.info(
                                "BNR financial statement generated: organizationId={}, from={}, to={}, assets={}, liabilities={}, equity={}, income={}, expenses={}, netIncome={}, totalLoans={}, totalLoanAmount={}, balanceDifference={}, trialBalanceDifference={}, accountingBalanced={}",
                                organizationId,
                                from,
                                to,
                                totalAssets,
                                totalLiabilities,
                                totalEquityIncludingProfit,
                                totalIncome,
                                totalExpenses,
                                netIncome,
                                loanPortfolio.get("totalLoans"),
                                loanPortfolio.get("totalLoanAmount"),
                                balanceDifference,
                                trialBalanceDifference,
                                accountingBalanced);

                return result;
        }

        // ============================================================
        // BNR LOAN PORTFOLIO STATISTICS
        // ============================================================

        /**
         * Builds the loan portfolio statistics required for BNR reporting.
         *
         * Includes:
         *
         * 1. Total number of loans.
         * 2. Total loan amount.
         * 3. Loan count by loan type.
         * 4. Loan amount by loan type.
         * 5. Percentage by loan type.
         * 6. Female borrower loan count and percentage.
         * 7. Male borrower loan count and percentage.
         * 8. Other/unknown gender loan count and percentage.
         * 9. Female borrower loan amount and percentage.
         * 10. Male borrower loan amount and percentage.
         * 11. Other/unknown gender loan amount and percentage.
         *
         * Loan restructuring is intentionally NOT included.
         */
        private Map<String, Object> buildLoanPortfolioStatistics(
                        Long organizationId,
                        LocalDate from,
                        LocalDate to) {

                Map<String, Object> portfolio = new LinkedHashMap<>();

                List<Loan> loans = loanRepository.findByOrganization_Id(
                                organizationId);

                if (loans == null) {
                        loans = new ArrayList<>();
                }

                /*
                 * Only loans relevant to the requested reporting period
                 * are included.
                 *
                 * We use startDate where available and fall back to
                 * disbursedAt when startDate is unavailable.
                 */
                List<Loan> reportingLoans = loans.stream()
                                .filter(Objects::nonNull)
                                .filter(loan -> belongsToReportingPeriod(
                                                loan,
                                                from,
                                                to))
                                .toList();

                // ========================================================
                // TOTAL PORTFOLIO
                // ========================================================

                int totalLoans = reportingLoans.size();

                BigDecimal totalLoanAmount = ZERO;

                for (Loan loan : reportingLoans) {

                        totalLoanAmount = add(
                                        totalLoanAmount,
                                        loanPrincipalForReporting(
                                                        loan));
                }

                totalLoanAmount = normalizeMoney(
                                totalLoanAmount);

                // ========================================================
                // LOANS BY TYPE
                // ========================================================

                Map<String, Integer> loanCountByType = new LinkedHashMap<>();

                Map<String, BigDecimal> loanAmountByType = new LinkedHashMap<>();

                for (Loan.LoanType loanType : Loan.LoanType.values()) {

                        loanCountByType.put(
                                        loanType.name(),
                                        0);

                        loanAmountByType.put(
                                        loanType.name(),
                                        ZERO);
                }

                for (Loan loan : reportingLoans) {

                        String loanType = loan.getLoanType() == null
                                        ? "UNKNOWN"
                                        : loan.getLoanType().name();

                        loanCountByType.merge(
                                        loanType,
                                        1,
                                        Integer::sum);

                        loanAmountByType.merge(
                                        loanType,
                                        loanPrincipalForReporting(
                                                        loan),
                                        this::add);
                }

                List<Map<String, Object>> loansByType = new ArrayList<>();

                for (Map.Entry<String, Integer> entry : loanCountByType.entrySet()) {

                        String loanType = entry.getKey();

                        int count = entry.getValue();

                        BigDecimal amount = normalizeMoney(
                                        loanAmountByType.getOrDefault(
                                                        loanType,
                                                        ZERO));

                        BigDecimal countPercentage = percentage(
                                        BigDecimal.valueOf(count),
                                        BigDecimal.valueOf(totalLoans));

                        BigDecimal amountPercentage = percentage(
                                        amount,
                                        totalLoanAmount);

                        Map<String, Object> row = new LinkedHashMap<>();

                        row.put(
                                        "loanType",
                                        loanType);

                        row.put(
                                        "loanCount",
                                        count);

                        row.put(
                                        "loanCountPercentage",
                                        countPercentage);

                        row.put(
                                        "loanAmount",
                                        amount);

                        row.put(
                                        "loanAmountPercentage",
                                        amountPercentage);

                        loansByType.add(row);
                }

                // ========================================================
                // GENDER STATISTICS
                // ========================================================

                int femaleLoanCount = 0;

                int maleLoanCount = 0;

                int otherGenderLoanCount = 0;

                BigDecimal femaleLoanAmount = ZERO;

                BigDecimal maleLoanAmount = ZERO;

                BigDecimal otherGenderLoanAmount = ZERO;

                for (Loan loan : reportingLoans) {

                        BigDecimal principal = loanPrincipalForReporting(
                                        loan);

                        String gender = extractGender(
                                        loan);

                        if ("FEMALE".equals(gender)) {

                                femaleLoanCount++;

                                femaleLoanAmount = add(
                                                femaleLoanAmount,
                                                principal);

                        } else if ("MALE".equals(gender)) {

                                maleLoanCount++;

                                maleLoanAmount = add(
                                                maleLoanAmount,
                                                principal);

                        } else {

                                otherGenderLoanCount++;

                                otherGenderLoanAmount = add(
                                                otherGenderLoanAmount,
                                                principal);
                        }
                }

                // ========================================================
                // GENDER COUNT PERCENTAGES
                // ========================================================

                BigDecimal femaleLoanCountPercentage = percentage(
                                BigDecimal.valueOf(
                                                femaleLoanCount),
                                BigDecimal.valueOf(
                                                totalLoans));

                BigDecimal maleLoanCountPercentage = percentage(
                                BigDecimal.valueOf(
                                                maleLoanCount),
                                BigDecimal.valueOf(
                                                totalLoans));

                BigDecimal otherGenderLoanCountPercentage = percentage(
                                BigDecimal.valueOf(
                                                otherGenderLoanCount),
                                BigDecimal.valueOf(
                                                totalLoans));

                // ========================================================
                // GENDER AMOUNT PERCENTAGES
                // ========================================================

                BigDecimal femaleLoanAmountPercentage = percentage(
                                femaleLoanAmount,
                                totalLoanAmount);

                BigDecimal maleLoanAmountPercentage = percentage(
                                maleLoanAmount,
                                totalLoanAmount);

                BigDecimal otherGenderLoanAmountPercentage = percentage(
                                otherGenderLoanAmount,
                                totalLoanAmount);

                // ========================================================
                // GENDER SUMMARY
                // ========================================================

                List<Map<String, Object>> loansByGender = new ArrayList<>();

                loansByGender.add(
                                genderRow(
                                                "FEMALE",
                                                femaleLoanCount,
                                                femaleLoanCountPercentage,
                                                femaleLoanAmount,
                                                femaleLoanAmountPercentage));

                loansByGender.add(
                                genderRow(
                                                "MALE",
                                                maleLoanCount,
                                                maleLoanCountPercentage,
                                                maleLoanAmount,
                                                maleLoanAmountPercentage));

                loansByGender.add(
                                genderRow(
                                                "OTHER_OR_UNKNOWN",
                                                otherGenderLoanCount,
                                                otherGenderLoanCountPercentage,
                                                otherGenderLoanAmount,
                                                otherGenderLoanAmountPercentage));

                // ========================================================
                // PORTFOLIO RESULT
                // ========================================================

                portfolio.put(
                                "reportingPeriodFrom",
                                from);

                portfolio.put(
                                "reportingPeriodTo",
                                to);

                portfolio.put(
                                "totalLoans",
                                totalLoans);

                portfolio.put(
                                "totalLoanAmount",
                                totalLoanAmount);

                portfolio.put(
                                "loansByType",
                                loansByType);

                portfolio.put(
                                "loansByGender",
                                loansByGender);

                // Direct summary values are also exposed for easy
                // frontend/report consumption.

                portfolio.put(
                                "femaleLoanCount",
                                femaleLoanCount);

                portfolio.put(
                                "femaleLoanCountPercentage",
                                femaleLoanCountPercentage);

                portfolio.put(
                                "femaleLoanAmount",
                                femaleLoanAmount);

                portfolio.put(
                                "femaleLoanAmountPercentage",
                                femaleLoanAmountPercentage);

                portfolio.put(
                                "maleLoanCount",
                                maleLoanCount);

                portfolio.put(
                                "maleLoanCountPercentage",
                                maleLoanCountPercentage);

                portfolio.put(
                                "maleLoanAmount",
                                maleLoanAmount);

                portfolio.put(
                                "maleLoanAmountPercentage",
                                maleLoanAmountPercentage);

                portfolio.put(
                                "otherGenderLoanCount",
                                otherGenderLoanCount);

                portfolio.put(
                                "otherGenderLoanCountPercentage",
                                otherGenderLoanCountPercentage);

                portfolio.put(
                                "otherGenderLoanAmount",
                                otherGenderLoanAmount);

                portfolio.put(
                                "otherGenderLoanAmountPercentage",
                                otherGenderLoanAmountPercentage);

                /*
                 * Explicitly indicate that restructuring is not part of
                 * this report. No restructuring calculations or categories
                 * are produced.
                 */
                portfolio.put(
                                "restructuringIncluded",
                                false);

                return portfolio;
        }

        // ============================================================
        // REPORTING PERIOD LOAN FILTER
        // ============================================================

        private boolean belongsToReportingPeriod(
                        Loan loan,
                        LocalDate from,
                        LocalDate to) {

                if (loan == null) {
                        return false;
                }

                /*
                 * BNR loan portfolio reporting is based on actual disbursement,
                 * not application/start dates. This prevents approved/pending loans
                 * from appearing as disbursed portfolio and keeps the report aligned
                 * with the operational dashboard.
                 */
                LocalDate disbursementDate = null;

                if (loan.getDisbursedAt() != null) {
                        disbursementDate = loan.getDisbursedAt().toLocalDate();
                } else if (loan.getDisbursedAtTimestamp() != null) {
                        disbursementDate = loan.getDisbursedAtTimestamp().toLocalDate();
                }

                if (disbursementDate == null) {
                        return false;
                }

                if (loan.getStatus() == null ||
                                loan.getStatus() == com.patrick.fintech.loan_backend.model.LoanStatus.PENDING ||
                                loan.getStatus() == com.patrick.fintech.loan_backend.model.LoanStatus.UNDER_REVIEW ||
                                loan.getStatus() == com.patrick.fintech.loan_backend.model.LoanStatus.APPROVED ||
                                loan.getStatus() == com.patrick.fintech.loan_backend.model.LoanStatus.REJECTED ||
                                loan.getStatus() == com.patrick.fintech.loan_backend.model.LoanStatus.CANCELLED) {
                        return false;
                }

                return !disbursementDate.isBefore(from)
                                && !disbursementDate.isAfter(to);
        }

        // ============================================================
        // LOAN PRINCIPAL
        // ============================================================

        private BigDecimal loanPrincipalForReporting(
                        Loan loan) {

                if (loan == null) {
                        return ZERO;
                }

                BigDecimal principal = loan.getAmountDecimal();

                if (principal == null) {
                        principal = ZERO;
                }

                return normalizeMoney(
                                principal);
        }

        // ============================================================
        // EXTRACT BORROWER GENDER
        // ============================================================

        /**
         * Supports a Borrower gender represented as String or enum.
         *
         * The service intentionally does not require a specific gender
         * enum type. Whatever getGender() returns is converted safely
         * to text.
         *
         * Female values:
         * FEMALE / F
         *
         * Male values:
         * MALE / M
         *
         * Everything else:
         * OTHER_OR_UNKNOWN
         */
        private String extractGender(
                        Loan loan) {

                if (loan == null) {
                        return "OTHER_OR_UNKNOWN";
                }

                Borrower borrower = loan.getBorrower();

                if (borrower == null) {
                        return "OTHER_OR_UNKNOWN";
                }

                Object genderObject = borrower.getGender();

                if (genderObject == null) {
                        return "OTHER_OR_UNKNOWN";
                }

                String gender = genderObject
                                .toString()
                                .trim()
                                .toUpperCase();

                if ("FEMALE".equals(gender)
                                || "F".equals(gender)) {

                        return "FEMALE";
                }

                if ("MALE".equals(gender)
                                || "M".equals(gender)) {

                        return "MALE";
                }

                return "OTHER_OR_UNKNOWN";
        }

        // ============================================================
        // GENDER ROW
        // ============================================================

        private Map<String, Object> genderRow(
                        String gender,
                        int loanCount,
                        BigDecimal loanCountPercentage,
                        BigDecimal loanAmount,
                        BigDecimal loanAmountPercentage) {

                Map<String, Object> row = new LinkedHashMap<>();

                row.put(
                                "gender",
                                gender);

                row.put(
                                "loanCount",
                                loanCount);

                row.put(
                                "loanCountPercentage",
                                loanCountPercentage);

                row.put(
                                "loanAmount",
                                normalizeMoney(
                                                loanAmount));

                row.put(
                                "loanAmountPercentage",
                                loanAmountPercentage);

                return row;
        }

        // ============================================================
        // PERCENTAGE
        // ============================================================

        private BigDecimal percentage(
                        BigDecimal numerator,
                        BigDecimal denominator) {

                BigDecimal safeNumerator = numerator == null
                                ? ZERO
                                : numerator;

                BigDecimal safeDenominator = denominator == null
                                ? ZERO
                                : denominator;

                if (safeDenominator.compareTo(ZERO) == 0) {
                        return BigDecimal.ZERO.setScale(
                                        REPORT_SCALE,
                                        MONEY_ROUNDING);
                }

                return safeNumerator
                                .multiply(HUNDRED)
                                .divide(
                                                safeDenominator,
                                                REPORT_SCALE,
                                                MONEY_ROUNDING);
        }

        // ============================================================
        // ACTIVE ENTRIES
        // ============================================================

        private List<JournalEntry> activeEntries(
                        List<JournalEntry> entries) {

                if (entries == null
                                || entries.isEmpty()) {

                        return new ArrayList<>();
                }

                return entries.stream()
                                .filter(Objects::nonNull)
                                .filter(entry -> !Boolean.TRUE.equals(
                                                entry.getReversed()))
                                .sorted(
                                                Comparator
                                                                .comparing(
                                                                                JournalEntry::getEntryDate,
                                                                                Comparator.nullsLast(
                                                                                                Comparator.naturalOrder())))
                                .toList();
        }

        // ============================================================
        // CREATE BALANCE MAP
        // ============================================================

        private Map<Long, BigDecimal> createBalanceMap(
                        List<ChartOfAccount> accounts) {

                Map<Long, BigDecimal> balances = new LinkedHashMap<>();

                if (accounts == null) {
                        return balances;
                }

                for (ChartOfAccount account : accounts) {

                        if (account == null) {
                                continue;
                        }

                        if (account.getId() == null) {
                                continue;
                        }

                        balances.put(
                                        account.getId(),
                                        ZERO);
                }

                return balances;
        }

        // ============================================================
        // PROCESS ENDING BALANCE
        // ============================================================

        private void processEndingBalanceEntry(
                        JournalEntry entry,
                        Map<Long, BigDecimal> balances,
                        Long organizationId) {

                if (entry == null) {
                        return;
                }

                if (Boolean.TRUE.equals(
                                entry.getReversed())) {
                        return;
                }

                validateJournalEntry(
                                entry,
                                organizationId);

                if (entry.getLines() == null) {
                        return;
                }

                for (JournalLine line : entry.getLines()) {

                        if (line == null) {
                                continue;
                        }

                        if (line.getAccount() == null) {
                                continue;
                        }

                        ChartOfAccount account = line.getAccount();

                        if (account.getId() == null) {
                                continue;
                        }

                        BigDecimal debit = value(
                                        line.getDebit());

                        BigDecimal credit = value(
                                        line.getCredit());

                        BigDecimal movement;

                        ChartOfAccount.NormalBalance normalBalance = account.getNormalBalance();

                        if (normalBalance == ChartOfAccount.NormalBalance.DEBIT) {

                                movement = subtract(
                                                debit,
                                                credit);

                        } else {

                                movement = subtract(
                                                credit,
                                                debit);
                        }

                        balances.merge(
                                        account.getId(),
                                        movement,
                                        this::add);
                }
        }

        // ============================================================
        // PROCESS PERIOD ENTRY
        // ============================================================

        private void processPeriodEntry(
                        JournalEntry entry,
                        Map<Long, BigDecimal> debits,
                        Map<Long, BigDecimal> credits,
                        Long organizationId) {

                if (entry == null) {
                        return;
                }

                if (Boolean.TRUE.equals(
                                entry.getReversed())) {
                        return;
                }

                validateJournalEntry(
                                entry,
                                organizationId);

                if (entry.getLines() == null) {
                        return;
                }

                for (JournalLine line : entry.getLines()) {

                        if (line == null) {
                                continue;
                        }

                        if (line.getAccount() == null) {
                                continue;
                        }

                        Long accountId = line.getAccount().getId();

                        if (accountId == null) {
                                continue;
                        }

                        BigDecimal debit = value(
                                        line.getDebit());

                        BigDecimal credit = value(
                                        line.getCredit());

                        debits.merge(
                                        accountId,
                                        debit,
                                        this::add);

                        credits.merge(
                                        accountId,
                                        credit,
                                        this::add);
                }
        }

        // ============================================================
        // PROCESS HISTORICAL INCOME / EXPENSE
        // ============================================================

        private void processIncomeExpenseEntry(
                        JournalEntry entry,
                        Map<Long, BigDecimal> historicalIncome,
                        Map<Long, BigDecimal> historicalExpenses,
                        Long organizationId) {

                if (entry == null) {
                        return;
                }

                if (Boolean.TRUE.equals(
                                entry.getReversed())) {
                        return;
                }

                validateJournalEntry(
                                entry,
                                organizationId);

                if (entry.getLines() == null) {
                        return;
                }

                for (JournalLine line : entry.getLines()) {

                        if (line == null) {
                                continue;
                        }

                        if (line.getAccount() == null) {
                                continue;
                        }

                        ChartOfAccount account = line.getAccount();

                        if (account.getId() == null) {
                                continue;
                        }

                        BigDecimal debit = value(
                                        line.getDebit());

                        BigDecimal credit = value(
                                        line.getCredit());

                        if (account.getType() == ChartOfAccount.AccountType.INCOME) {

                                BigDecimal amount = subtract(
                                                credit,
                                                debit);

                                historicalIncome.merge(
                                                account.getId(),
                                                amount,
                                                this::add);

                        } else if (account.getType() == ChartOfAccount.AccountType.EXPENSE) {

                                BigDecimal amount = subtract(
                                                debit,
                                                credit);

                                historicalExpenses.merge(
                                                account.getId(),
                                                amount,
                                                this::add);
                        }
                }
        }

        // ============================================================
        // ACCOUNT ROW
        // ============================================================

        private Map<String, Object> accountRow(
                        ChartOfAccount account,
                        BigDecimal balance) {

                Map<String, Object> row = new LinkedHashMap<>();

                row.put(
                                "id",
                                account.getId());

                row.put(
                                "code",
                                account.getCode());

                row.put(
                                "name",
                                account.getName());

                row.put(
                                "type",
                                account.getType());

                row.put(
                                "normalBalance",
                                account.getNormalBalance());

                row.put(
                                "balance",
                                normalizeMoney(
                                                balance));

                return row;
        }

        // ============================================================
        // CONTRA ASSET
        // ============================================================

        private boolean isContraAsset(
                        ChartOfAccount account) {

                if (account == null) {
                        return false;
                }

                /*
                 * 1200 is retained for compatibility with the existing
                 * chart of accounts.
                 */
                if ("1200".equals(
                                account.getCode())) {
                        return true;
                }

                return account.getNormalBalance() == ChartOfAccount.NormalBalance.CREDIT;
        }

        // ============================================================
        // NORMALIZED STATEMENT BALANCE
        // ============================================================

        private BigDecimal normalizedStatementBalance(
                        ChartOfAccount account,
                        BigDecimal balance) {

                if (account == null) {
                        return ZERO;
                }

                return normalizeMoney(
                                balance);
        }

        // ============================================================
        // BIGDECIMAL VALUE
        // ============================================================

        private BigDecimal value(
                        Number value) {

                if (value == null) {
                        return ZERO;
                }

                if (value instanceof BigDecimal) {

                        return normalizeMoney(
                                        (BigDecimal) value);
                }

                if (value instanceof Long) {

                        return normalizeMoney(
                                        BigDecimal.valueOf(
                                                        value.longValue()));
                }

                if (value instanceof Integer) {

                        return normalizeMoney(
                                        BigDecimal.valueOf(
                                                        value.intValue()));
                }

                if (value instanceof Short) {

                        return normalizeMoney(
                                        BigDecimal.valueOf(
                                                        value.shortValue()));
                }

                if (value instanceof Byte) {

                        return normalizeMoney(
                                        BigDecimal.valueOf(
                                                        value.byteValue()));
                }

                if (value instanceof Double) {

                        double doubleValue = value.doubleValue();

                        if (Double.isNaN(doubleValue)
                                        || Double.isInfinite(doubleValue)) {

                                throw new IllegalArgumentException(
                                                "Financial value cannot be NaN or infinite.");
                        }

                        return normalizeMoney(
                                        BigDecimal.valueOf(
                                                        doubleValue));
                }

                if (value instanceof Float) {

                        float floatValue = value.floatValue();

                        if (Float.isNaN(floatValue)
                                        || Float.isInfinite(floatValue)) {

                                throw new IllegalArgumentException(
                                                "Financial value cannot be NaN or infinite.");
                        }

                        return normalizeMoney(
                                        BigDecimal.valueOf(
                                                        floatValue));
                }

                throw new IllegalArgumentException(
                                "Unsupported financial number type: "
                                                + value.getClass().getName());
        }

        // ============================================================
        // ADD
        // ============================================================

        private BigDecimal add(
                        BigDecimal first,
                        BigDecimal second) {

                BigDecimal a = first == null
                                ? ZERO
                                : first;

                BigDecimal b = second == null
                                ? ZERO
                                : second;

                return normalizeMoney(
                                a.add(b));
        }

        // ============================================================
        // SUBTRACT
        // ============================================================

        private BigDecimal subtract(
                        BigDecimal first,
                        BigDecimal second) {

                BigDecimal a = first == null
                                ? ZERO
                                : first;

                BigDecimal b = second == null
                                ? ZERO
                                : second;

                return normalizeMoney(
                                a.subtract(b));
        }

        // ============================================================
        // NORMALIZE MONEY
        // ============================================================

        private BigDecimal normalizeMoney(
                        BigDecimal value) {

                if (value == null) {
                        return ZERO;
                }

                return value.setScale(
                                MONEY_SCALE,
                                MONEY_ROUNDING);
        }

        // ============================================================
        // MATERIAL VALUE CHECK
        // ============================================================

        private boolean isMaterial(
                        BigDecimal value) {

                if (value == null) {
                        return false;
                }

                return value
                                .abs()
                                .compareTo(
                                                BALANCE_TOLERANCE) >= 0;
        }

        // ============================================================
        // TOLERANCE CHECK
        // ============================================================

        private boolean isWithinTolerance(
                        BigDecimal value) {

                if (value == null) {
                        return true;
                }

                return value
                                .abs()
                                .compareTo(
                                                BALANCE_TOLERANCE) < 0;
        }

        // ============================================================
        // JOURNAL ENTRY VALIDATION
        // ============================================================

        private void validateJournalEntry(
                        JournalEntry entry,
                        Long organizationId) {

                if (entry == null) {
                        return;
                }

                if (organizationId == null) {
                        throw new IllegalArgumentException(
                                        "Organization ID is required.");
                }

                if (entry.getOrganization() != null
                                && entry.getOrganization().getId() != null
                                && !organizationId.equals(
                                                entry.getOrganization().getId())) {

                        throw new IllegalStateException(
                                        "Journal entry "
                                                        + entry.getId()
                                                        + " belongs to another organization.");
                }

                if (entry.getEntryDate() == null) {

                        throw new IllegalStateException(
                                        "Journal entry "
                                                        + entry.getId()
                                                        + " has no entry date.");
                }

                if (entry.getLines() == null
                                || entry.getLines().isEmpty()) {

                        throw new IllegalStateException(
                                        "Journal entry "
                                                        + entry.getId()
                                                        + " contains no journal lines.");
                }

                BigDecimal debitTotal = ZERO;

                BigDecimal creditTotal = ZERO;

                for (JournalLine line : entry.getLines()) {

                        if (line == null) {

                                throw new IllegalStateException(
                                                "Journal entry "
                                                                + entry.getId()
                                                                + " contains a null journal line.");
                        }

                        if (line.getAccount() == null
                                        || line.getAccount().getId() == null) {

                                throw new IllegalStateException(
                                                "Journal entry "
                                                                + entry.getId()
                                                                + " contains a journal line without an account.");
                        }

                        BigDecimal debit = value(
                                        line.getDebit());

                        BigDecimal credit = value(
                                        line.getCredit());

                        if (debit.compareTo(ZERO) < 0) {

                                throw new IllegalStateException(
                                                "Journal entry "
                                                                + entry.getId()
                                                                + " contains a negative debit.");
                        }

                        if (credit.compareTo(ZERO) < 0) {

                                throw new IllegalStateException(
                                                "Journal entry "
                                                                + entry.getId()
                                                                + " contains a negative credit.");
                        }

                        if (debit.compareTo(ZERO) > 0
                                        && credit.compareTo(ZERO) > 0) {

                                throw new IllegalStateException(
                                                "Journal entry "
                                                                + entry.getId()
                                                                + " contains a line with both debit and credit.");
                        }

                        if (debit.compareTo(ZERO) == 0
                                        && credit.compareTo(ZERO) == 0) {

                                throw new IllegalStateException(
                                                "Journal entry "
                                                                + entry.getId()
                                                                + " contains a zero-value journal line.");
                        }

                        debitTotal = add(
                                        debitTotal,
                                        debit);

                        creditTotal = add(
                                        creditTotal,
                                        credit);
                }

                BigDecimal difference = subtract(
                                debitTotal,
                                creditTotal);

                if (!isWithinTolerance(
                                difference)) {

                        throw new IllegalStateException(
                                        "Unbalanced journal entry "
                                                        + entry.getId()
                                                        + ": debit="
                                                        + debitTotal
                                                        + ", credit="
                                                        + creditTotal
                                                        + ", difference="
                                                        + difference);
                }
        }

        // ============================================================
        // SAFE STRING
        // ============================================================

        private String safeString(
                        String value) {

                return value == null
                                ? ""
                                : value;
        }

        // ============================================================
        // VALIDATION
        // ============================================================

        private void validateDates(
                        Long organizationId,
                        LocalDate from,
                        LocalDate to) {

                if (organizationId == null) {

                        throw new IllegalArgumentException(
                                        "Organization ID is required.");
                }

                if (from == null) {

                        throw new IllegalArgumentException(
                                        "Financial statement start date is required.");
                }

                if (to == null) {

                        throw new IllegalArgumentException(
                                        "Financial statement end date is required.");
                }

                if (from.isAfter(to)) {

                        throw new IllegalArgumentException(
                                        "Financial statement start date cannot be after end date.");
                }
        }
}