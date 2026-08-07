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
        // CHART OF ACCOUNTS
        // ========================================================

        List<ChartOfAccount> accounts =
                chartOfAccountRepository
                        .findByOrganization_IdOrderByCodeAsc(
                                organizationId
                        );

        if (accounts == null) {
            accounts =
                    new ArrayList<>();
        }


        // ========================================================
        // CUMULATIVE ENTRIES
        // ========================================================

        /*
         * Balance-sheet accounts must be calculated from all
         * accounting movements up to the report date.
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
        // PERIOD ENTRIES
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

        Map<Long, Double> periodDebits =
                createBalanceMap(accounts);

        Map<Long, Double> periodCredits =
                createBalanceMap(accounts);


        // ========================================================
        // CUMULATIVE BALANCES
        // ========================================================

        for (JournalEntry entry :
                cumulativeEntries) {

            processEndingBalanceEntry(
                    entry,
                    endingBalances
            );
        }


        // ========================================================
        // PERIOD INCOME / EXPENSE MOVEMENTS
        // ========================================================

        for (JournalEntry entry :
                periodEntries) {

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


        double totalAssets = 0.0;
        double totalLiabilities = 0.0;
        double totalEquity = 0.0;
        double totalIncome = 0.0;
        double totalExpenses = 0.0;


        // ========================================================
        // CLASSIFY ACCOUNTS
        // ========================================================

        for (ChartOfAccount account :
                accounts) {

            if (account == null) {
                continue;
            }

            Long accountId =
                    account.getId();

            if (accountId == null) {
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

            if (account.getType() == null) {
                continue;
            }


            switch (account.getType()) {

                // =================================================
                // ASSETS
                // =================================================

                case ASSET -> {

                    if (endingBalance == 0.0) {
                        continue;
                    }

                    Map<String, Object> row =
                            accountRow(
                                    account,
                                    endingBalance
                            );

                    boolean contraAsset =
                            account.getNormalBalance()
                                    ==
                            ChartOfAccount.NormalBalance.CREDIT;

                    if (contraAsset) {

                        row.put(
                                "presentation",
                                "CONTRA_ASSET"
                        );

                        row.put(
                                "deduction",
                                -Math.abs(
                                        endingBalance
                                )
                        );

                        totalAssets -=
                                Math.abs(
                                        endingBalance
                                );

                    } else {

                        row.put(
                                "presentation",
                                "ASSET"
                        );

                        totalAssets +=
                                endingBalance;
                    }

                    assets.add(row);
                }


                // =================================================
                // LIABILITIES
                // =================================================

                case LIABILITY -> {

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

                    totalLiabilities +=
                            endingBalance;

                    liabilities.add(row);
                }


                // =================================================
                // EQUITY
                // =================================================

                case EQUITY -> {

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

                    totalEquity +=
                            endingBalance;

                    equity.add(row);
                }


                // =================================================
                // INCOME
                // =================================================

                case INCOME -> {

                    double incomeAmount =
                            periodCredit -
                            periodDebit;

                    if (incomeAmount == 0.0) {
                        continue;
                    }

                    Map<String, Object> row =
                            accountRow(
                                    account,
                                    incomeAmount
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
                            incomeAmount;

                    income.add(row);
                }


                // =================================================
                // EXPENSE
                // =================================================

                case EXPENSE -> {

                    double expenseAmount =
                            periodDebit -
                            periodCredit;

                    if (expenseAmount == 0.0) {
                        continue;
                    }

                    Map<String, Object> row =
                            accountRow(
                                    account,
                                    expenseAmount
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
                            expenseAmount;

                    expenses.add(row);
                }
            }
        }


        // ========================================================
        // NET INCOME
        // ========================================================

        double netIncome =
                totalIncome -
                totalExpenses;


        // ========================================================
        // EQUITY + CURRENT PERIOD PROFIT
        // ========================================================

        double totalEquityIncludingProfit =
                totalEquity +
                netIncome;


        double liabilitiesPlusEquity =
                totalLiabilities +
                totalEquityIncludingProfit;


        double balanceDifference =
                totalAssets -
                liabilitiesPlusEquity;


        /*
         * Exact comparison.
         *
         * No .01 tolerance.
         */
        boolean balanceSheetBalanced =
                Double.compare(
                        balanceDifference,
                        0.0
                ) == 0;


        // ========================================================
        // TRIAL BALANCE
        // ========================================================

        double trialBalanceDebit = 0.0;
        double trialBalanceCredit = 0.0;

        for (JournalEntry entry :
                periodEntries) {

            if (entry == null) {
                continue;
            }

            if (Boolean.TRUE.equals(
                    entry.getReversed()
            )) {
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
                Double.compare(
                        trialBalanceDifference,
                        0.0
                ) == 0;


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
                "totalEquity",
                totalEquityIncludingProfit
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
    // PROCESS CUMULATIVE ENTRY
    // ============================================================

    private void processEndingBalanceEntry(
            JournalEntry entry,
            Map<Long, Double> balances
    ) {

        if (entry == null) {
            return;
        }

        /*
         * Do not count the original reversed transaction.
         *
         * The active reversal transaction remains in the ledger.
         */
        if (Boolean.TRUE.equals(
                entry.getReversed()
        )) {
            return;
        }

        if (entry.getLines() == null) {
            return;
        }

        for (JournalLine line :
                entry.getLines()) {

            if (line == null
                    || line.getAccount() == null) {
                continue;
            }

            ChartOfAccount account =
                    line.getAccount();

            if (account.getId() == null) {
                continue;
            }

            double debit =
                    value(line.getDebit());

            double credit =
                    value(line.getCredit());

            double movement;

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

        if (Boolean.TRUE.equals(
                entry.getReversed()
        )) {
            return;
        }

        if (entry.getLines() == null) {
            return;
        }

        for (JournalLine line :
                entry.getLines()) {

            if (
                    line == null
                    || line.getAccount() == null
            ) {
                continue;
            }

            Long accountId =
                    line.getAccount().getId();

            if (accountId == null) {
                continue;
            }

            double debit =
                    value(line.getDebit());

            double credit =
                    value(line.getCredit());

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
         * Exact double value.
         *
         * No Math.round().
         * No setScale().
         */
        row.put(
                "balance",
                balance
        );

        return row;
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
    // VALIDATION
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