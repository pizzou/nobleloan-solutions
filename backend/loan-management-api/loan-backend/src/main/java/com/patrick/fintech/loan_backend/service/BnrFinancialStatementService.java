package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.ChartOfAccount;
import com.patrick.fintech.loan_backend.model.JournalEntry;
import com.patrick.fintech.loan_backend.model.JournalLine;
import com.patrick.fintech.loan_backend.repository.ChartOfAccountRepository;
import com.patrick.fintech.loan_backend.repository.JournalEntryRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BnrFinancialStatementService {

    private final ChartOfAccountRepository chartOfAccountRepository;
    private final JournalEntryRepository journalEntryRepository;


    // ============================================================
    // MAIN FINANCIAL STATEMENT
    // ============================================================

    /**
     * Builds the accounting-based BNR financial statement.
     *
     * IMPORTANT:
     *
     * 1. All amounts remain DOUBLE.
     * 2. No Math.round().
     * 3. No setScale().
     * 4. No forced 2-decimal recording.
     *
     * Statement of Financial Position:
     *     Uses cumulative transactions through "to".
     *
     * Income Statement:
     *     Uses transactions between "from" and "to".
     *
     * Trial Balance:
     *     Uses transactions between "from" and "to".
     *
     * Accounting treatment:
     *
     * CASH / BANK
     *     Asset
     *     Debit normal balance
     *
     * INTEREST RECEIVABLE
     *     Asset
     *     Debit normal balance
     *
     * LOAN RECEIVABLE / PRINCIPAL
     *     Asset
     *     Debit normal balance
     *
     * INTEREST INCOME
     *     Income
     *     Credit normal balance
     *
     * EXPENSES
     *     Expense
     *     Debit normal balance
     */
    public Map<String, Object> buildFinancialStatement(
            Long organizationId,
            LocalDate from,
            LocalDate to
    ) {

        validateDates(
                organizationId,
                from,
                to
        );


        // ========================================================
        // LOAD CHART OF ACCOUNTS
        // ========================================================

        List<ChartOfAccount> accounts =
                chartOfAccountRepository
                        .findByOrganization_IdOrderByCodeAsc(
                                organizationId
                        );

        if (accounts == null) {
            accounts = new ArrayList<>();
        }


        // ========================================================
        // LOAD CUMULATIVE JOURNAL ENTRIES
        // ========================================================

        /*
         * The balance sheet needs all transactions from the
         * beginning of the accounting system through the report
         * end date.
         *
         * If your database contains transactions earlier than
         * 1970, change this date to the earliest supported date.
         */
        LocalDate accountingStart =
                LocalDate.of(
                        1970,
                        1,
                        1
                );


        List<JournalEntry> cumulativeEntries =
                journalEntryRepository
                        .findByOrganization_IdAndEntryDateBetweenOrderByEntryDateAsc(
                                organizationId,
                                accountingStart,
                                to
                        );

        if (cumulativeEntries == null) {
            cumulativeEntries =
                    new ArrayList<>();
        }


        // ========================================================
        // LOAD REPORTING PERIOD ENTRIES
        // ========================================================

        List<JournalEntry> periodEntries =
                journalEntryRepository
                        .findByOrganization_IdAndEntryDateBetweenOrderByEntryDateAsc(
                                organizationId,
                                from,
                                to
                        );

        if (periodEntries == null) {
            periodEntries =
                    new ArrayList<>();
        }


        // ========================================================
        // BALANCE MAPS
        // ========================================================

        Map<Long, Double> endingBalances =
                createBalanceMap(accounts);

        Map<Long, Double> cumulativeDebits =
                createBalanceMap(accounts);

        Map<Long, Double> cumulativeCredits =
                createBalanceMap(accounts);

        Map<Long, Double> periodDebits =
                createBalanceMap(accounts);

        Map<Long, Double> periodCredits =
                createBalanceMap(accounts);


        // ========================================================
        // PROCESS CUMULATIVE TRANSACTIONS
        // ========================================================

        for (JournalEntry entry : cumulativeEntries) {

            if (isExcluded(entry)) {
                continue;
            }

            processEndingBalanceEntry(
                    entry,
                    endingBalances
            );

            processPeriodEntry(
                    entry,
                    cumulativeDebits,
                    cumulativeCredits
            );
        }


        // ========================================================
        // PROCESS REPORTING PERIOD
        // ========================================================

        for (JournalEntry entry : periodEntries) {

            if (isExcluded(entry)) {
                continue;
            }

            processPeriodEntry(
                    entry,
                    periodDebits,
                    periodCredits
            );
        }


        // ========================================================
        // STATEMENT COLLECTIONS
        // ========================================================

        List<Map<String, Object>> assets =
                new ArrayList<>();

        List<Map<String, Object>> liabilities =
                new ArrayList<>();

        List<Map<String, Object>> equity =
                new ArrayList<>();

        List<Map<String, Object>> income =
                new ArrayList<>();

        List<Map<String, Object>> expenses =
                new ArrayList<>();


        double totalAssets =
                0.0;

        double totalLiabilities =
                0.0;

        double totalEquityAccounts =
                0.0;

        double totalIncome =
                0.0;

        double totalExpenses =
                0.0;


        // ========================================================
        // CUMULATIVE PROFIT / LOSS
        // ========================================================

        double cumulativeIncome =
                0.0;

        double cumulativeExpenses =
                0.0;


        // ========================================================
        // CLASSIFY ACCOUNTS
        // ========================================================

        for (ChartOfAccount account : accounts) {

            if (account == null) {
                continue;
            }

            Long accountId =
                    account.getId();

            if (accountId == null) {
                continue;
            }

            ChartOfAccount.AccountType type =
                    account.getType();

            if (type == null) {
                continue;
            }


            double endingBalance =
                    endingBalances.getOrDefault(
                            accountId,
                            0.0
                    );


            double periodDebit =
                    periodDebits.getOrDefault(
                            accountId,
                            0.0
                    );


            double periodCredit =
                    periodCredits.getOrDefault(
                            accountId,
                            0.0
                    );


            double cumulativeDebit =
                    cumulativeDebits.getOrDefault(
                            accountId,
                            0.0
                    );


            double cumulativeCredit =
                    cumulativeCredits.getOrDefault(
                            accountId,
                            0.0
                    );


            // ====================================================
            // ASSET
            // ====================================================

            if (type == ChartOfAccount.AccountType.ASSET) {

                if (endingBalance == 0.0) {
                    continue;
                }


                Map<String, Object> row =
                        accountRow(
                                account,
                                endingBalance
                        );


                /*
                 * A normal asset is normally DEBIT.
                 *
                 * If an asset has CREDIT normal balance, it is
                 * presented as a contra-asset.
                 *
                 * Therefore:
                 *
                 * Cash       -> normal ASSET
                 * Bank       -> normal ASSET
                 * Receivable -> normal ASSET
                 *
                 * Contra asset -> deduction.
                 */
                boolean contraAsset =
                        account.getNormalBalance()
                                == ChartOfAccount.NormalBalance.CREDIT;


                if (contraAsset) {

                    row.put(
                            "presentation",
                            "CONTRA_ASSET"
                    );

                    row.put(
                            "deduction",
                            -Math.abs(endingBalance)
                    );

                    totalAssets -=
                            Math.abs(endingBalance);

                } else {

                    row.put(
                            "presentation",
                            "ASSET"
                    );

                    totalAssets +=
                            endingBalance;
                }


                assets.add(row);

                continue;
            }


            // ====================================================
            // LIABILITY
            // ====================================================

            if (type == ChartOfAccount.AccountType.LIABILITY) {

                if (endingBalance == 0.0) {
                    continue;
                }


                Map<String, Object> row =
                        accountRow(
                                account,
                                endingBalance
                        );


                row.put(
                        "presentation",
                        "LIABILITY"
                );


                /*
                 * Liability accounts normally have CREDIT
                 * normal balance.
                 *
                 * A debit balance therefore reduces the
                 * liability.
                 */
                totalLiabilities +=
                        endingBalance;


                liabilities.add(row);

                continue;
            }


            // ====================================================
            // EQUITY
            // ====================================================

            if (type == ChartOfAccount.AccountType.EQUITY) {

                if (endingBalance == 0.0) {
                    continue;
                }


                Map<String, Object> row =
                        accountRow(
                                account,
                                endingBalance
                        );


                row.put(
                        "presentation",
                        "EQUITY"
                );


                totalEquityAccounts +=
                        endingBalance;


                equity.add(row);

                continue;
            }


            // ====================================================
            // INCOME
            // ====================================================

            if (type == ChartOfAccount.AccountType.INCOME) {

                double periodIncome =
                        periodCredit -
                                periodDebit;


                double cumulativeIncomeForAccount =
                        cumulativeCredit -
                                cumulativeDebit;


                cumulativeIncome +=
                        cumulativeIncomeForAccount;


                if (periodIncome == 0.0) {
                    continue;
                }


                Map<String, Object> row =
                        accountRow(
                                account,
                                periodIncome
                        );


                row.put(
                        "presentation",
                        "INCOME"
                );

                row.put(
                        "periodDebit",
                        periodDebit
                );

                row.put(
                        "periodCredit",
                        periodCredit
                );

                row.put(
                        "cumulativeBalance",
                        endingBalance
                );


                totalIncome +=
                        periodIncome;


                income.add(row);

                continue;
            }


            // ====================================================
            // EXPENSE
            // ====================================================

            if (type == ChartOfAccount.AccountType.EXPENSE) {

                double periodExpense =
                        periodDebit -
                                periodCredit;


                double cumulativeExpenseForAccount =
                        cumulativeDebit -
                                cumulativeCredit;


                cumulativeExpenses +=
                        cumulativeExpenseForAccount;


                if (periodExpense == 0.0) {
                    continue;
                }


                Map<String, Object> row =
                        accountRow(
                                account,
                                periodExpense
                        );


                row.put(
                        "presentation",
                        "EXPENSE"
                );

                row.put(
                        "periodDebit",
                        periodDebit
                );

                row.put(
                        "periodCredit",
                        periodCredit
                );

                row.put(
                        "cumulativeBalance",
                        endingBalance
                );


                totalExpenses +=
                        periodExpense;


                expenses.add(row);
            }
        }


        // ========================================================
        // REPORTING PERIOD NET INCOME
        // ========================================================

        double netIncome =
                totalIncome -
                        totalExpenses;


        // ========================================================
        // CUMULATIVE RETAINED / CURRENT EARNINGS
        // ========================================================

        /*
         * This is the critical correction.
         *
         * If income and expense accounts have NOT been closed
         * into retained earnings, their cumulative net balance
         * must be represented in equity for the balance sheet.
         *
         * This prevents the balance sheet from considering only
         * the current reporting period's profit.
         */
        double cumulativeNetIncome =
                cumulativeIncome -
                        cumulativeExpenses;


        // ========================================================
        // TOTAL EQUITY
        // ========================================================

        double totalEquityIncludingEarnings =
                totalEquityAccounts +
                        cumulativeNetIncome;


        // ========================================================
        // LIABILITIES + EQUITY
        // ========================================================

        double liabilitiesPlusEquity =
                totalLiabilities +
                        totalEquityIncludingEarnings;


        // ========================================================
        // BALANCE SHEET DIFFERENCE
        // ========================================================

        double balanceDifference =
                totalAssets -
                        liabilitiesPlusEquity;


        /*
         * No rounding is performed.
         *
         * This tolerance is ONLY used to determine whether
         * floating-point arithmetic has produced an insignificant
         * binary-double difference.
         */
        boolean balanceSheetBalanced =
                Math.abs(balanceDifference)
                        < 0.000000000001;


        // ========================================================
        // TRIAL BALANCE
        // ========================================================

        double trialBalanceDebit =
                0.0;

        double trialBalanceCredit =
                0.0;


        for (JournalEntry entry : periodEntries) {

            if (isExcluded(entry)) {
                continue;
            }


            if (entry.getLines() == null) {
                continue;
            }


            for (JournalLine line :
                    entry.getLines()) {

                if (line == null) {
                    continue;
                }


                trialBalanceDebit +=
                        value(
                                line.getDebit()
                        );


                trialBalanceCredit +=
                        value(
                                line.getCredit()
                        );
            }
        }


        double trialBalanceDifference =
                trialBalanceDebit -
                        trialBalanceCredit;


        boolean trialBalanceBalanced =
                Math.abs(
                        trialBalanceDifference
                ) < 0.000000000001;


        // ========================================================
        // STATEMENT OF FINANCIAL POSITION
        // ========================================================

        Map<String, Object>
                statementOfFinancialPosition =
                new LinkedHashMap<>();


        statementOfFinancialPosition.put(
                "assets",
                assets
        );


        statementOfFinancialPosition.put(
                "liabilities",
                liabilities
        );


        statementOfFinancialPosition.put(
                "equity",
                equity
        );


        statementOfFinancialPosition.put(
                "cumulativeRetainedEarnings",
                cumulativeNetIncome
        );


        statementOfFinancialPosition.put(
                "currentPeriodNetIncome",
                netIncome
        );


        statementOfFinancialPosition.put(
                "totalAssets",
                totalAssets
        );


        statementOfFinancialPosition.put(
                "totalLiabilities",
                totalLiabilities
        );


        statementOfFinancialPosition.put(
                "equityAccounts",
                totalEquityAccounts
        );


        statementOfFinancialPosition.put(
                "totalEquity",
                totalEquityIncludingEarnings
        );


        statementOfFinancialPosition.put(
                "liabilitiesPlusEquity",
                liabilitiesPlusEquity
        );


        statementOfFinancialPosition.put(
                "balanceDifference",
                balanceDifference
        );


        statementOfFinancialPosition.put(
                "balanced",
                balanceSheetBalanced
        );


        // ========================================================
        // INCOME STATEMENT
        // ========================================================

        Map<String, Object>
                incomeStatement =
                new LinkedHashMap<>();


        incomeStatement.put(
                "income",
                income
        );


        incomeStatement.put(
                "expenses",
                expenses
        );


        incomeStatement.put(
                "totalIncome",
                totalIncome
        );


        incomeStatement.put(
                "totalExpenses",
                totalExpenses
        );


        incomeStatement.put(
                "netIncome",
                netIncome
        );


        // ========================================================
        // TRIAL BALANCE
        // ========================================================

        Map<String, Object>
                trialBalance =
                new LinkedHashMap<>();


        trialBalance.put(
                "debit",
                trialBalanceDebit
        );


        trialBalance.put(
                "credit",
                trialBalanceCredit
        );


        trialBalance.put(
                "difference",
                trialBalanceDifference
        );


        trialBalance.put(
                "balanced",
                trialBalanceBalanced
        );


        // ========================================================
        // FINAL RESULT
        // ========================================================

        Map<String, Object> result =
                new LinkedHashMap<>();


        result.put(
                "reportType",
                "BNR_FINANCIAL_STATEMENT"
        );


        result.put(
                "organizationId",
                organizationId
        );


        result.put(
                "from",
                from
        );


        result.put(
                "to",
                to
        );


        result.put(
                "generatedAt",
                LocalDateTime.now()
        );


        result.put(
                "statementOfFinancialPosition",
                statementOfFinancialPosition
        );


        result.put(
                "incomeStatement",
                incomeStatement
        );


        result.put(
                "trialBalance",
                trialBalance
        );


        result.put(
                "accountingBalanced",
                balanceSheetBalanced
                        && trialBalanceBalanced
        );


        result.put(
                "balanceDifference",
                balanceDifference
        );


        result.put(
                "trialBalanceDebit",
                trialBalanceDebit
        );


        result.put(
                "trialBalanceCredit",
                trialBalanceCredit
        );


        result.put(
                "trialBalanceDifference",
                trialBalanceDifference
        );


        result.put(
                "trialBalanceBalanced",
                trialBalanceBalanced
        );


        return result;
    }


    // ============================================================
    // CREATE BALANCE MAP
    // ============================================================

    private Map<Long, Double> createBalanceMap(
            List<ChartOfAccount> accounts
    ) {

        Map<Long, Double> balances =
                new LinkedHashMap<>();


        if (accounts == null) {
            return balances;
        }


        for (ChartOfAccount account :
                accounts) {

            if (
                    account != null
                            &&
                    account.getId() != null
            ) {

                balances.put(
                        account.getId(),
                        0.0
                );
            }
        }


        return balances;
    }


    // ============================================================
    // PROCESS ENDING BALANCE
    // ============================================================

    private void processEndingBalanceEntry(
            JournalEntry entry,
            Map<Long, Double> balances
    ) {

        if (entry == null) {
            return;
        }


        if (entry.getLines() == null) {
            return;
        }


        for (JournalLine line :
                entry.getLines()) {

            if (line == null) {
                continue;
            }


            if (line.getAccount() == null) {
                continue;
            }


            ChartOfAccount account =
                    line.getAccount();


            if (account.getId() == null) {
                continue;
            }


            double debit =
                    value(
                            line.getDebit()
                    );


            double credit =
                    value(
                            line.getCredit()
                    );


            double movement;


            /*
             * Debit-normal accounts:
             *
             * Asset
             * Expense
             *
             * Balance = Debit - Credit
             *
             * Credit-normal accounts:
             *
             * Liability
             * Equity
             * Income
             *
             * Balance = Credit - Debit
             */
            if (
                    account.getNormalBalance()
                            ==
                    ChartOfAccount.NormalBalance.DEBIT
            ) {

                movement =
                        debit -
                                credit;

            } else {

                movement =
                        credit -
                                debit;
            }


            balances.merge(
                    account.getId(),
                    movement,
                    Double::sum
            );
        }
    }


    // ============================================================
    // PROCESS PERIOD ENTRY
    // ============================================================

    private void processPeriodEntry(
            JournalEntry entry,
            Map<Long, Double> debits,
            Map<Long, Double> credits
    ) {

        if (entry == null) {
            return;
        }


        if (entry.getLines() == null) {
            return;
        }


        for (JournalLine line :
                entry.getLines()) {

            if (line == null) {
                continue;
            }


            if (line.getAccount() == null) {
                continue;
            }


            Long accountId =
                    line.getAccount().getId();


            if (accountId == null) {
                continue;
            }


            double debit =
                    value(
                            line.getDebit()
                    );


            double credit =
                    value(
                            line.getCredit()
                    );


            debits.merge(
                    accountId,
                    debit,
                    Double::sum
            );


            credits.merge(
                    accountId,
                    credit,
                    Double::sum
            );
        }
    }


    // ============================================================
    // ACCOUNT ROW
    // ============================================================

    private Map<String, Object> accountRow(
            ChartOfAccount account,
            double balance
    ) {

        Map<String, Object> row =
                new LinkedHashMap<>();


        row.put(
                "id",
                account.getId()
        );


        row.put(
                "code",
                account.getCode()
        );


        row.put(
                "name",
                account.getName()
        );


        row.put(
                "type",
                account.getType()
        );


        row.put(
                "normalBalance",
                account.getNormalBalance()
        );


        /*
         * IMPORTANT:
         *
         * Keep the exact DOUBLE value.
         *
         * No:
         * Math.round()
         *
         * No:
         * setScale()
         *
         * No:
         * forced .01 precision.
         */
        row.put(
                "balance",
                balance
        );


        return row;
    }


    // ============================================================
    // EXCLUDED JOURNAL ENTRY
    // ============================================================

    private boolean isExcluded(
            JournalEntry entry
    ) {

        if (entry == null) {
            return true;
        }


        /*
         * A reversed original entry is excluded.
         *
         * The separate reversal journal entry remains in the
         * accounting records and offsets the original entry.
         */
        return Boolean.TRUE.equals(
                entry.getReversed()
        );
    }


    // ============================================================
    // DOUBLE VALUE
    // ============================================================

    private double value(
            Double value
    ) {

        return value == null
                ? 0.0
                : value;
    }


    // ============================================================
    // DATE VALIDATION
    // ============================================================

    private void validateDates(
            Long organizationId,
            LocalDate from,
            LocalDate to
    ) {

        if (organizationId == null) {

            throw new IllegalArgumentException(
                    "Organization ID is required."
            );
        }


        if (from == null) {

            throw new IllegalArgumentException(
                    "Financial statement start date is required."
            );
        }


        if (to == null) {

            throw new IllegalArgumentException(
                    "Financial statement end date is required."
            );
        }


        if (from.isAfter(to)) {

            throw new IllegalArgumentException(
                    "Financial statement start date cannot be after end date."
            );
        }
    }
}