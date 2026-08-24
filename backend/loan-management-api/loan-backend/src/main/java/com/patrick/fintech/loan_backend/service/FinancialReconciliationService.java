package com.patrick.fintech.loan_backend.service;

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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Authoritative financial reconciliation engine.
 *
 * This service is deliberately read-only. It never "fixes" accounting data
 * automatically. A reconciliation failure must remain visible until an
 * authorized accounting user investigates and posts a correcting journal or
 * reverses the originating transaction.
 *
 * The engine checks:
 * - every journal entry balances;
 * - every journal line is financially valid;
 * - the organization trial balance balances;
 * - active source events are not duplicated;
 * - the loan principal sub-ledger agrees with GL 1100;
 * - interest receivable agrees with GL 1150;
 * - management-fee receivable agrees with GL 1160;
 * - extension-fee receivable agrees with GL 1170;
 * - penalty receivable agrees with GL 1175.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FinancialReconciliationService {

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);
    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    private final JournalEntryRepository journalEntryRepository;
    private final ChartOfAccountRepository chartOfAccountRepository;
    private final LoanRepository loanRepository;

    @Transactional(readOnly = true)
    public ReconciliationReport reconcile(Long organizationId) {
        return reconcile(organizationId, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public ReconciliationReport reconcile(Long organizationId, LocalDate asOf) {
        requireOrganizationId(organizationId);
        if (asOf == null) {
            throw new IllegalArgumentException("As-of date is required");
        }

        List<JournalEntry> entries = journalEntryRepository
                .findByOrganization_IdAndEntryDateBetweenOrderByEntryDateAscIdAsc(
                        organizationId,
                        LocalDate.of(1900, 1, 1),
                        asOf);

        if (entries == null) {
            entries = List.of();
        }

        List<ChartOfAccount> accounts = chartOfAccountRepository
                .findByOrganization_IdOrderByCodeAsc(organizationId);

        if (accounts == null) {
            accounts = List.of();
        }

        List<Loan> loans = loanRepository.findByOrganization_Id(organizationId);
        if (loans == null) {
            loans = List.of();
        }

        List<Issue> issues = new ArrayList<>();

        BigDecimal journalDebits = ZERO;
        BigDecimal journalCredits = ZERO;
        int invalidLineCount = 0;
        int unbalancedEntryCount = 0;

        Map<Long, BigDecimal[]> accountTotals = new LinkedHashMap<>();

        for (JournalEntry entry : entries) {
            if (entry == null || entry.getId() == null) {
                continue;
            }

            BigDecimal entryDebit = ZERO;
            BigDecimal entryCredit = ZERO;

            if (entry.getLines() == null || entry.getLines().isEmpty()) {
                issues.add(issue(
                        "JOURNAL_EMPTY",
                        "Journal entry " + entry.getId() + " contains no lines.",
                        ZERO));
                unbalancedEntryCount++;
                continue;
            }

            for (JournalLine line : entry.getLines()) {
                if (line == null) {
                    invalidLineCount++;
                    issues.add(issue(
                            "JOURNAL_NULL_LINE",
                            "Journal entry " + entry.getId() + " contains a null line.",
                            ZERO));
                    continue;
                }

                BigDecimal debit = money(line.getDebitDecimal());
                BigDecimal credit = money(line.getCreditDecimal());

                boolean bothSides = debit.compareTo(ZERO) > 0 && credit.compareTo(ZERO) > 0;
                boolean neitherSide = debit.compareTo(ZERO) == 0 && credit.compareTo(ZERO) == 0;
                boolean negative = debit.compareTo(ZERO) < 0 || credit.compareTo(ZERO) < 0;
                boolean missingAccount = line.getAccount() == null || line.getAccount().getId() == null;
                boolean crossTenantAccount = line.getAccount() != null
                        && line.getAccount().getOrganization() != null
                        && line.getAccount().getOrganization().getId() != null
                        && !organizationId.equals(line.getAccount().getOrganization().getId());

                if (bothSides || neitherSide || negative || missingAccount || crossTenantAccount) {
                    invalidLineCount++;
                    issues.add(issue(
                            "JOURNAL_LINE_INVALID",
                            "Invalid journal line " + line.getId()
                                    + " in entry " + entry.getId()
                                    + ". Both/neither side, negative amount, missing account, or cross-tenant account.",
                            debit.subtract(credit).abs()));
                }

                entryDebit = entryDebit.add(debit);
                entryCredit = entryCredit.add(credit);
                journalDebits = journalDebits.add(debit);
                journalCredits = journalCredits.add(credit);

                if (line.getAccount() != null && line.getAccount().getId() != null) {
                    BigDecimal[] totals = accountTotals.computeIfAbsent(
                            line.getAccount().getId(),
                            ignored -> new BigDecimal[] { ZERO, ZERO });
                    totals[0] = totals[0].add(debit);
                    totals[1] = totals[1].add(credit);
                }
            }

            BigDecimal difference = normalize(entryDebit.subtract(entryCredit));
            if (difference.abs().compareTo(TOLERANCE) >= 0) {
                unbalancedEntryCount++;
                issues.add(issue(
                        "JOURNAL_UNBALANCED",
                        "Journal entry " + entry.getId() + " is out of balance by "
                                + difference.abs().toPlainString() + ".",
                        difference.abs()));
            }
        }

        journalDebits = normalize(journalDebits);
        journalCredits = normalize(journalCredits);
        BigDecimal journalDifference = normalize(journalDebits.subtract(journalCredits));

        if (journalDifference.abs().compareTo(TOLERANCE) >= 0) {
            issues.add(issue(
                    "GENERAL_LEDGER_UNBALANCED",
                    "Organization general ledger is out of balance by "
                            + journalDifference.abs().toPlainString() + ".",
                    journalDifference.abs()));
        }

        BigDecimal trialDebit = ZERO;
        BigDecimal trialCredit = ZERO;
        for (Map.Entry<Long, BigDecimal[]> item : accountTotals.entrySet()) {
            BigDecimal debit = normalize(item.getValue()[0]);
            BigDecimal credit = normalize(item.getValue()[1]);
            BigDecimal net = debit.subtract(credit);
            if (net.compareTo(ZERO) >= 0) {
                trialDebit = trialDebit.add(net);
            } else {
                trialCredit = trialCredit.add(net.negate());
            }
        }
        trialDebit = normalize(trialDebit);
        trialCredit = normalize(trialCredit);
        BigDecimal trialDifference = normalize(trialDebit.subtract(trialCredit));

        if (trialDifference.abs().compareTo(TOLERANCE) >= 0) {
            issues.add(issue(
                    "TRIAL_BALANCE_UNBALANCED",
                    "Trial balance is out of balance by "
                            + trialDifference.abs().toPlainString() + ".",
                    trialDifference.abs()));
        }

        Map<String, Integer> duplicateActiveSources = new LinkedHashMap<>();
        entries.stream()
                .filter(entry -> entry != null && !Boolean.TRUE.equals(entry.getReversed()))
                .filter(entry -> entry.getSourceType() != null && !entry.getSourceType().isBlank())
                .filter(entry -> entry.getSourceId() != null && !entry.getSourceId().isBlank())
                .forEach(entry -> {
                    String key = entry.getSourceType().trim() + ":" + entry.getSourceId().trim();
                    duplicateActiveSources.merge(key, 1, Integer::sum);
                });

        duplicateActiveSources.entrySet().stream()
                .filter(item -> item.getValue() > 1)
                .forEach(item -> issues.add(issue(
                        "DUPLICATE_SOURCE_EVENT",
                        "Multiple active journal entries exist for source "
                                + item.getKey() + " (count=" + item.getValue() + ").",
                        ZERO)));

        Map<String, BigDecimal> operationalSubledger = new LinkedHashMap<>();
        operationalSubledger.put("1100", sum(loans, Loan::getOutstandingBalanceDecimal));
        operationalSubledger.put("1150", sum(loans, Loan::getInterestOutstandingDecimal));
        operationalSubledger.put("1160", sum(loans, Loan::getManagementFeeOutstandingDecimal));
        operationalSubledger.put("1170", sum(loans, Loan::getExtensionFeeOutstandingDecimal));
        operationalSubledger.put("1175", sum(loans, loan -> {
            BigDecimal assessed = loan.getPenaltiesAssessedDecimal();
            BigDecimal paid = loan.getPenaltiesPaidDecimal();
            return money(assessed).subtract(money(paid)).max(ZERO);
        }));

        Map<String, ReconciliationLine> subledger = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> item : operationalSubledger.entrySet()) {
            String code = item.getKey();
            BigDecimal operational = normalize(item.getValue());
            ChartOfAccount account = accounts.stream()
                    .filter(a -> a != null && code.equals(a.getCode()))
                    .findFirst()
                    .orElse(null);

            BigDecimal glBalance = account == null
                    ? ZERO
                    : accountBalance(account, accountTotals.get(account.getId()));

            BigDecimal difference = normalize(glBalance.subtract(operational));
            boolean reconciles = difference.abs().compareTo(TOLERANCE) < 0;

            ReconciliationLine line = new ReconciliationLine(
                    code,
                    account == null ? null : account.getName(),
                    operational,
                    glBalance,
                    difference,
                    reconciles);
            subledger.put(code, line);

            if (!reconciles) {
                issues.add(issue(
                        "SUBLEDGER_MISMATCH",
                        "Operational sub-ledger does not reconcile with GL " + code
                                + ". GL minus operational difference=" + difference.abs().toPlainString(),
                        difference.abs()));
            }
        }

        int duplicateSourceCount = duplicateActiveSources.values().stream()
                .mapToInt(count -> Math.max(0, count - 1))
                .sum();

        boolean balanced = journalDifference.abs().compareTo(TOLERANCE) < 0
                && trialDifference.abs().compareTo(TOLERANCE) < 0
                && unbalancedEntryCount == 0
                && invalidLineCount == 0
                && duplicateSourceCount == 0
                && subledger.values().stream().allMatch(ReconciliationLine::reconciles);

        BigDecimal maxDifference = issues.stream()
                .map(Issue::difference)
                .map(this::money)
                .max(BigDecimal::compareTo)
                .orElse(ZERO);

        return new ReconciliationReport(
                organizationId,
                asOf,
                balanced,
                journalDebits,
                journalCredits,
                journalDifference,
                trialDebit,
                trialCredit,
                trialDifference,
                entries.size(),
                loans.size(),
                unbalancedEntryCount,
                invalidLineCount,
                duplicateSourceCount,
                maxDifference,
                subledger,
                issues);
    }

    private BigDecimal accountBalance(ChartOfAccount account, BigDecimal[] totals) {
        if (account == null || totals == null) {
            return ZERO;
        }
        BigDecimal debit = money(totals[0]);
        BigDecimal credit = money(totals[1]);
        if (account.getNormalBalance() == ChartOfAccount.NormalBalance.CREDIT) {
            return normalize(credit.subtract(debit));
        }
        return normalize(debit.subtract(credit));
    }

    private BigDecimal sum(List<Loan> loans, Function<Loan, BigDecimal> extractor) {
        BigDecimal total = ZERO;
        for (Loan loan : loans) {
            if (loan == null) {
                continue;
            }
            total = total.add(money(extractor.apply(loan)));
        }
        return normalize(total);
    }

    private Issue issue(String code, String message, BigDecimal difference) {
        return new Issue(code, message, normalize(difference));
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null) {
            return ZERO;
        }
        return value.setScale(MONEY_SCALE, ROUNDING);
    }

    private BigDecimal normalize(BigDecimal value) {
        return money(value);
    }

    private void requireOrganizationId(Long organizationId) {
        if (organizationId == null || organizationId <= 0) {
            throw new IllegalArgumentException("Organization ID must be positive.");
        }
    }

    public record ReconciliationLine(
            String accountCode,
            String accountName,
            BigDecimal operationalBalance,
            BigDecimal glBalance,
            BigDecimal difference,
            boolean reconciles) {
    }

    public record Issue(
            String code,
            String message,
            BigDecimal difference) {
    }

    public record ReconciliationReport(
            Long organizationId,
            LocalDate asOf,
            boolean balanced,
            BigDecimal journalDebits,
            BigDecimal journalCredits,
            BigDecimal journalDifference,
            BigDecimal trialDebit,
            BigDecimal trialCredit,
            BigDecimal trialDifference,
            int journalEntryCount,
            int loanCount,
            int unbalancedEntryCount,
            int invalidLineCount,
            int duplicateActiveSourceCount,
            BigDecimal maximumDifference,
            Map<String, ReconciliationLine> subledger,
            List<Issue> issues) {
    }
}
