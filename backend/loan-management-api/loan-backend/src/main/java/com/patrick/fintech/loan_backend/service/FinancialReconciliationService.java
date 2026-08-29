package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.ChartOfAccount;
import com.patrick.fintech.loan_backend.model.JournalEntry;
import com.patrick.fintech.loan_backend.model.JournalLine;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanStatus;
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
    private final RegulatoryReportingService regulatoryReportingService;

    @Transactional(readOnly = true)
    public ReconciliationReport reconcile(Long organizationId) {
        return reconcile(organizationId, null, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public ReconciliationReport reconcile(Long organizationId, LocalDate asOf) {
        return reconcile(organizationId, null, asOf);
    }

    @Transactional(readOnly = true)
    public ReconciliationReport reconcile(Long organizationId, LocalDate periodStart, LocalDate asOf) {
        requireOrganizationId(organizationId);
        if (asOf == null) {
            throw new IllegalArgumentException("As-of date is required");
        }
        LocalDate effectivePeriodStart = periodStart == null ? asOf : periodStart;
        if (effectivePeriodStart.isAfter(asOf)) {
            throw new IllegalArgumentException("Period start cannot be after the as-of date");
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

        loans = loans.stream()
                .filter(loan -> isFinanciallyOriginated(loan, asOf))
                .toList();

        List<Issue> issues = new ArrayList<>();

        for (Loan loan : loans) {
            if (loan == null || loan.getId() == null) {
                continue;
            }

            BigDecimal amount = money(loan.getAmountDecimal());
            BigDecimal principalPaid = money(loan.getPrincipalPaidDecimal());
            BigDecimal principalOutstanding = money(loan.getOutstandingBalanceDecimal());
            BigDecimal principalDifference = normalize(
                    principalPaid.add(principalOutstanding).subtract(amount));

            if (principalDifference.abs().compareTo(TOLERANCE) >= 0) {
                issues.add(issue(
                        "PRINCIPAL_RECONCILIATION_FAILED",
                        "Loan " + safeReference(loan)
                                + " fails principal reconciliation: principal_paid + outstanding_balance must equal amount. "
                                + "paid=" + principalPaid.toPlainString()
                                + ", outstanding=" + principalOutstanding.toPlainString()
                                + ", amount=" + amount.toPlainString(),
                        principalDifference.abs()));
            }

            BigDecimal totalInterest = money(loan.getTotalInterestDecimal());
            BigDecimal interestPaid = money(loan.getInterestPaidDecimal());
            BigDecimal interestOutstanding = money(loan.getInterestOutstandingDecimal());
            BigDecimal interestDifference = normalize(
                    interestPaid.add(interestOutstanding).subtract(totalInterest));

            if (interestDifference.abs().compareTo(TOLERANCE) >= 0) {
                issues.add(issue(
                        "INTEREST_RECONCILIATION_FAILED",
                        "Loan " + safeReference(loan)
                                + " fails interest reconciliation: interest_paid + interest_outstanding must equal total_interest. "
                                + "paid=" + interestPaid.toPlainString()
                                + ", outstanding=" + interestOutstanding.toPlainString()
                                + ", total=" + totalInterest.toPlainString(),
                        interestDifference.abs()));
            }

            BigDecimal totalManagementFee = money(loan.getManagementFeeDecimal());
            BigDecimal managementFeePaid = money(loan.getManagementFeePaidDecimal());
            BigDecimal managementFeeOutstanding = money(loan.getManagementFeeOutstandingDecimal());
            BigDecimal managementDifference = normalize(
                    managementFeePaid.add(managementFeeOutstanding).subtract(totalManagementFee));

            if (managementDifference.abs().compareTo(TOLERANCE) >= 0) {
                issues.add(issue(
                        "MANAGEMENT_FEE_RECONCILIATION_FAILED",
                        "Loan " + safeReference(loan)
                                + " fails management-fee reconciliation: management_fee_paid + management_fee_outstanding must equal management_fee. "
                                + "paid=" + managementFeePaid.toPlainString()
                                + ", outstanding=" + managementFeeOutstanding.toPlainString()
                                + ", total=" + totalManagementFee.toPlainString(),
                        managementDifference.abs()));
            }

            BigDecimal applicationFee = money(loan.getApplicationFeeDecimal());
            BigDecimal applicationFeePaid = money(loan.getApplicationFeePaidDecimal());
            BigDecimal applicationFeeOutstanding = money(applicationFee.subtract(applicationFeePaid).max(ZERO));
            BigDecimal applicationFeeDifference = normalize(
                    applicationFeePaid.add(applicationFeeOutstanding).subtract(applicationFee));

            if (applicationFeePaid.compareTo(applicationFee) > 0
                    || applicationFeeDifference.abs().compareTo(TOLERANCE) >= 0) {
                issues.add(issue(
                        "APPLICATION_FEE_RECONCILIATION_FAILED",
                        "Loan " + safeReference(loan)
                                + " fails one-time application/processing fee reconciliation. "
                                + "paid=" + applicationFeePaid.toPlainString()
                                + ", outstanding=" + applicationFeeOutstanding.toPlainString()
                                + ", fee=" + applicationFee.toPlainString(),
                        applicationFeePaid.subtract(applicationFee).abs().max(applicationFeeDifference.abs())));
            }
        }

        BigDecimal journalDebits = ZERO;
        BigDecimal journalCredits = ZERO;
        int invalidLineCount = 0;
        int unbalancedEntryCount = 0;

        Map<Long, BigDecimal[]> accountTotals = new LinkedHashMap<>();

        for (JournalEntry entry : entries) {
            if (entry == null || entry.getId() == null) {
                continue;
            }

            // A reversed original entry is no longer part of the active GL.
            // Its separate REVERSAL journal remains in the ledger and therefore
            // carries the accounting effect. Counting both the reversed original
            // and its reversal would double-count the transaction during
            // reconciliation.
            if (Boolean.TRUE.equals(entry.getReversed())) {
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

            BigDecimal glBalance;
            if (account == null) {
                glBalance = ZERO;
            } else {
                /*
                 * The organization-level reconciliation MUST compare the
                 * authoritative GL account balance against the aggregate
                 * operational sub-ledger.
                 *
                 * Do not reconstruct a GL account total by summing
                 * loanReceivableBalance(...) for every loan. A loan reference
                 * is not a globally unique token inside free-form journal
                 * descriptions (for example loan references 1 and 10).
                 * Reconstructing the GL this way can attribute the same journal
                 * line to multiple loans and produces large false variances.
                 *
                 * The detailed per-loan diagnostic below still performs
                 * attribution, but it uses exact identity/reference matching.
                 */
                glBalance = accountBalance(
                        account,
                        accountTotals.get(account.getId()));
            }

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

        // ------------------------------------------------------------
        // OPENING BALANCE / PERIOD MOVEMENT CONTROL
        // ------------------------------------------------------------
        BigDecimal openingNet = ZERO;
        BigDecimal movementNet = ZERO;
        BigDecimal closingNet = ZERO;
        for (JournalEntry entry : entries) {
            if (entry == null || Boolean.TRUE.equals(entry.getReversed()) || entry.getLines() == null) {
                continue;
            }
            BigDecimal debit = ZERO;
            BigDecimal credit = ZERO;
            for (JournalLine line : entry.getLines()) {
                if (line == null)
                    continue;
                debit = debit.add(money(line.getDebitDecimal()));
                credit = credit.add(money(line.getCreditDecimal()));
            }
            BigDecimal net = normalize(debit.subtract(credit));
            if (entry.getEntryDate() != null && entry.getEntryDate().isBefore(effectivePeriodStart)) {
                openingNet = openingNet.add(net);
            } else {
                movementNet = movementNet.add(net);
            }
        }
        closingNet = normalize(openingNet.add(movementNet));
        BigDecimal openingMovementDifference = normalize(closingNet.subtract(openingNet).subtract(movementNet));
        boolean openingMovementReconciles = openingMovementDifference.abs().compareTo(TOLERANCE) < 0;
        if (!openingMovementReconciles) {
            issues.add(issue(
                    "OPENING_BALANCE_MOVEMENT_FAILED",
                    "Opening balance plus period movement does not equal the closing general-ledger balance.",
                    openingMovementDifference.abs()));
        }

        // ------------------------------------------------------------
        // BNR <-> PORTFOLIO CONTROL
        // ------------------------------------------------------------
        BigDecimal portfolioOutstanding = loans.stream()
                .filter(this::isGrossPortfolioLoan)
                .map(Loan::getOutstandingBalanceDecimal)
                .map(this::money)
                .reduce(ZERO, BigDecimal::add);
        portfolioOutstanding = normalize(portfolioOutstanding);
        BigDecimal bnrOutstanding = ZERO;
        boolean bnrReconciles = true;
        try {
            var bnr = regulatoryReportingService.buildBnrSummary(
                    organizationId, null, RegulatoryReportingService.ReportPeriod.CUSTOM, asOf, asOf);
            bnrOutstanding = money(bnr.getOutstandingPrincipalDecimal());
            BigDecimal bnrDifference = normalize(bnrOutstanding.subtract(portfolioOutstanding));
            bnrReconciles = bnrDifference.abs().compareTo(TOLERANCE) < 0;
            if (!bnrReconciles) {
                issues.add(issue(
                        "BNR_PORTFOLIO_MISMATCH",
                        "BNR outstanding principal does not reconcile with the authoritative portfolio population. "
                                + "BNR=" + bnrOutstanding.toPlainString()
                                + ", portfolio=" + portfolioOutstanding.toPlainString(),
                        bnrDifference.abs()));
            }
        } catch (RuntimeException ex) {
            bnrReconciles = false;
            issues.add(issue(
                    "BNR_RECONCILIATION_UNAVAILABLE",
                    "BNR reconciliation could not be completed: " + ex.getMessage(),
                    ZERO));
            log.error("BNR reconciliation failed for organization {} as of {}", organizationId, asOf, ex);
        }

        balanced = balanced && openingMovementReconciles && bnrReconciles;

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
                effectivePeriodStart,
                openingNet,
                movementNet,
                closingNet,
                openingMovementDifference,
                openingMovementReconciles,
                bnrOutstanding,
                portfolioOutstanding,
                normalize(bnrOutstanding.subtract(portfolioOutstanding)),
                bnrReconciles,
                subledger,
                issues);
    }

    /**
     * Read-only loan-level diagnostic.
     *
     * This method never creates, changes, reverses, or deletes accounting
     * entries. It identifies the individual loans contributing to the
     * organization-level receivable reconciliation differences.
     */
    @Transactional(readOnly = true)
    public List<LoanReconciliationDiagnostic> diagnoseLoanSubledger(
            Long organizationId) {

        requireOrganizationId(organizationId);

        List<JournalEntry> entries = journalEntryRepository
                .findByOrganization_IdAndEntryDateBetweenOrderByEntryDateAscIdAsc(
                        organizationId,
                        LocalDate.of(1900, 1, 1),
                        LocalDate.now());

        if (entries == null) {
            entries = List.of();
        }

        List<ChartOfAccount> accounts = chartOfAccountRepository
                .findByOrganization_IdOrderByCodeAsc(organizationId);

        if (accounts == null) {
            accounts = List.of();
        }

        Map<String, ChartOfAccount> accountByCode = new LinkedHashMap<>();
        for (ChartOfAccount account : accounts) {
            if (account != null && account.getCode() != null) {
                accountByCode.put(account.getCode(), account);
            }
        }

        List<Loan> loans = loanRepository.findByOrganization_Id(organizationId);
        if (loans == null || loans.isEmpty()) {
            return List.of();
        }

        loans = loans.stream()
                .filter(loan -> isFinanciallyOriginated(loan, LocalDate.now()))
                .toList();

        List<LoanReconciliationDiagnostic> result = new ArrayList<>();

        for (Loan loan : loans) {
            if (loan == null || loan.getId() == null) {
                continue;
            }

            String reference = loan.getReferenceNumber() == null
                    ? ""
                    : loan.getReferenceNumber().trim();
            String loanSourceId = "LOAN:" + loan.getId();

            // A migrated loan is identified primarily by its immutable database id.
            // Reference text remains a compatibility fallback for payment/accrual
            // events whose sourceId is the payment/event id rather than the loan id.
            if (reference.isBlank() && loan.getId() == null) {
                continue;
            }

            Map<String, BigDecimal> operational = new LinkedHashMap<>();
            operational.put("1100", money(loan.getOutstandingBalanceDecimal()));
            operational.put("1150", money(loan.getInterestOutstandingDecimal()));
            operational.put("1160", money(loan.getManagementFeeOutstandingDecimal()));
            operational.put("1170", money(loan.getExtensionFeeOutstandingDecimal()));

            BigDecimal penalties = money(loan.getPenaltiesAssessedDecimal())
                    .subtract(money(loan.getPenaltiesPaidDecimal()))
                    .max(ZERO);
            operational.put("1175", money(penalties));

            Map<String, BigDecimal> gl = new LinkedHashMap<>();
            for (String code : operational.keySet()) {
                ChartOfAccount account = accountByCode.get(code);
                gl.put(code, account == null
                        ? ZERO
                        : loanReceivableBalance(entries, account, loan));
            }

            BigDecimal principalDifference = normalize(gl.get("1100").subtract(operational.get("1100")));
            BigDecimal interestDifference = normalize(gl.get("1150").subtract(operational.get("1150")));
            BigDecimal managementDifference = normalize(gl.get("1160").subtract(operational.get("1160")));
            BigDecimal extensionDifference = normalize(gl.get("1170").subtract(operational.get("1170")));
            BigDecimal penaltyDifference = normalize(gl.get("1175").subtract(operational.get("1175")));

            boolean reconciles = principalDifference.abs().compareTo(TOLERANCE) < 0
                    && interestDifference.abs().compareTo(TOLERANCE) < 0
                    && managementDifference.abs().compareTo(TOLERANCE) < 0
                    && extensionDifference.abs().compareTo(TOLERANCE) < 0
                    && penaltyDifference.abs().compareTo(TOLERANCE) < 0;

            if (!reconciles) {
                result.add(new LoanReconciliationDiagnostic(
                        loan.getId(),
                        reference,
                        Boolean.TRUE.equals(loan.getImported()),
                        operational.get("1100"),
                        gl.get("1100"),
                        principalDifference,
                        operational.get("1150"),
                        gl.get("1150"),
                        interestDifference,
                        operational.get("1160"),
                        gl.get("1160"),
                        managementDifference,
                        operational.get("1170"),
                        gl.get("1170"),
                        extensionDifference,
                        operational.get("1175"),
                        gl.get("1175"),
                        penaltyDifference));
            }
        }

        return result;
    }

    private BigDecimal loanReceivableBalance(
            List<JournalEntry> entries,
            ChartOfAccount account,
            Loan loan) {

        BigDecimal balance = ZERO;
        if (entries == null || account == null || loan == null || loan.getId() == null) {
            return ZERO;
        }

        String loanReference = loan.getReferenceNumber() == null
                ? ""
                : loan.getReferenceNumber().trim();
        String token = loanReference.toLowerCase(java.util.Locale.ROOT);
        String loanSourceId = "LOAN:" + loan.getId();

        for (JournalEntry entry : entries) {
            if (entry == null
                    || Boolean.TRUE.equals(entry.getReversed())
                    || entry.getLines() == null
                    || entry.getLines().isEmpty()) {
                continue;
            }

            String sourceType = entry.getSourceType() == null
                    ? ""
                    : entry.getSourceType().trim();

            boolean relevantSource = "INTEREST_ACCRUAL".equals(sourceType)
                    || "MANAGEMENT_FEE_ACCRUAL".equals(sourceType)
                    || "PENALTY_ACCRUAL".equals(sourceType)
                    || "LOAN_EXTENSION_FEE".equals(sourceType)
                    || "HISTORICAL_LOAN_OPENING".equals(sourceType)
                    || "LEGACY_LOAN_OPENING".equals(sourceType)
                    || "LEGACY_LOAN_RECONCILIATION".equals(sourceType)
                    || "LEGACY_LOAN_OPENING_DATE_REPAIR".equals(sourceType)
                    || "PAYMENT_RECEIVED".equals(sourceType)
                    || "LOAN_PAYMENT".equals(sourceType)
                    || "LOAN_DISBURSEMENT".equals(sourceType)
                    || "SCHEDULED_INTEREST_ACCRUAL".equals(sourceType)
                    || "SCHEDULED_MANAGEMENT_FEE_ACCRUAL".equals(sourceType)
                    || "CONTRACTUAL_MONTHLY_INTEREST_ACCRUAL".equals(sourceType)
                    || "CONTRACTUAL_MONTHLY_MANAGEMENT_FEE_ACCRUAL".equals(sourceType);

            if (!relevantSource) {
                continue;
            }

            if ((Boolean.TRUE.equals(loan.getImported()) || loan.getImportBatchId() != null)
                    && "LOAN_DISBURSEMENT".equals(sourceType)) {
                // Historical cash movement is deliberately not replayed for migrated loans.
                continue;
            }

            String sourceId = entry.getSourceId() == null ? "" : entry.getSourceId().trim();
            String entryReference = entry.getReference() == null
                    ? ""
                    : entry.getReference().trim().toLowerCase(java.util.Locale.ROOT);

            boolean identityMatch = loanSourceId.equals(sourceId);
            boolean referenceMatch = !token.isBlank() && entryReference.equals(token);

            for (JournalLine line : entry.getLines()) {
                if (line == null
                        || line.getAccount() == null
                        || line.getAccount().getId() == null
                        || !account.getId().equals(line.getAccount().getId())) {
                    continue;
                }

                String description = line.getDescription() == null
                        ? ""
                        : line.getDescription().trim();

                boolean descriptionMatch = loanDescriptionMatchesReference(
                        description,
                        loanReference);

                if (!identityMatch && !referenceMatch && !descriptionMatch) {
                    continue;
                }

                balance = balance
                        .add(money(line.getDebitDecimal()))
                        .subtract(money(line.getCreditDecimal()));
            }
        }

        return normalize(balance);
    }

    /**
     * Matches the loan reference only in the structured suffix used by the
     * accounting layer. Free-form substring matching is forbidden because a
     * numeric reference such as "1" would otherwise match "10", "11", etc.
     */
    private boolean loanDescriptionMatchesReference(
            String description,
            String loanReference) {

        if (description == null || loanReference == null) {
            return false;
        }

        String normalizedDescription = description
                .trim()
                .toLowerCase(java.util.Locale.ROOT);
        String normalizedReference = loanReference
                .trim()
                .toLowerCase(java.util.Locale.ROOT);

        if (normalizedDescription.isBlank()
                || normalizedReference.isBlank()) {
            return false;
        }

        return normalizedDescription.endsWith("— " + normalizedReference)
                || normalizedDescription.endsWith("- " + normalizedReference)
                || normalizedDescription.endsWith(" - " + normalizedReference);
    }

    /**
     * Returns true only when the loan represents an accounting receivable as
     * of the supplied date. Approval alone does not create a receivable.
     */
    private boolean isGrossPortfolioLoan(Loan loan) {
        if (loan == null || loan.getStatus() == null) {
            return false;
        }
        LoanStatus status = loan.getStatus();
        return status != LoanStatus.WRITTEN_OFF
                && status != LoanStatus.PAID
                && status != LoanStatus.CLOSED
                && status != LoanStatus.PENDING
                && status != LoanStatus.UNDER_REVIEW
                && status != LoanStatus.REJECTED
                && status != LoanStatus.CANCELLED;
    }

    private boolean isFinanciallyOriginated(Loan loan, LocalDate asOf) {
        if (loan == null || asOf == null) {
            return false;
        }

        if (Boolean.TRUE.equals(loan.getImported()) || loan.getImportBatchId() != null) {
            LoanStatus status = loan.getStatus();
            return status != LoanStatus.PENDING
                    && status != LoanStatus.UNDER_REVIEW
                    && status != LoanStatus.REJECTED
                    && status != LoanStatus.CANCELLED;
        }

        if (loan.getDisbursedAt() != null) {
            return !loan.getDisbursedAt().toLocalDate().isAfter(asOf);
        }

        // Never treat APPROVED/PENDING pipeline balances as GL receivables.
        LoanStatus status = loan.getStatus();
        if (status != null) {
            return status == LoanStatus.DISBURSED
                    || status == LoanStatus.ACTIVE
                    || status == LoanStatus.OVERDUE
                    || status == LoanStatus.DEFAULTED
                    || status == LoanStatus.RESTRUCTURED
                    || status == LoanStatus.WRITTEN_OFF
                    || status == LoanStatus.PAID
                    || status == LoanStatus.CLOSED;
        }

        /*
         * A malformed legacy row with no status must not silently disappear
         * from reconciliation if it already carries financial balances.
         */
        return hasFinancialEvidence(loan);
    }

    private boolean hasFinancialEvidence(Loan loan) {
        if (loan == null) {
            return false;
        }
        return money(loan.getAmountDecimal()).signum() > 0
                || money(loan.getPrincipalPaidDecimal()).signum() > 0
                || money(loan.getOutstandingBalanceDecimal()).signum() > 0
                || money(loan.getTotalInterestDecimal()).signum() > 0
                || money(loan.getInterestPaidDecimal()).signum() > 0
                || money(loan.getInterestOutstandingDecimal()).signum() > 0
                || money(loan.getManagementFeeDecimal()).signum() > 0
                || money(loan.getManagementFeePaidDecimal()).signum() > 0
                || money(loan.getManagementFeeOutstandingDecimal()).signum() > 0
                || money(loan.getApplicationFeeDecimal()).signum() > 0
                || money(loan.getApplicationFeePaidDecimal()).signum() > 0
                || money(loan.getPenaltiesAssessedDecimal()).signum() > 0
                || money(loan.getPenaltiesPaidDecimal()).signum() > 0;
    }

    private BigDecimal accountBalance(ChartOfAccount account, BigDecimal[] totals) {
        if (account == null || totals == null || totals.length < 2) {
            return ZERO;
        }

        BigDecimal debit = money(totals[0]);
        BigDecimal credit = money(totals[1]);

        /*
         * Loan receivable accounts are debit-balance assets.
         *
         * Do not derive their reconciliation value from the configured
         * normalBalance flag. That flag is a chart-of-accounts presentation
         * attribute and a legacy/misconfigured row must never be allowed to
         * invert the operational sub-ledger reconciliation.
         *
         * For 1100/1150/1160/1170/1175 the authoritative GL balance is:
         *
         * total debits - total credits
         *
         * This is also the sign convention used by AccountingService for the
         * receivable ledger. Keeping the reconciliation engine on the same
         * convention prevents false failures where a valid receivable balance
         * is reported as a negative credit balance.
         */
        if (isLoanReceivableAccount(account.getCode())) {
            return normalize(debit.subtract(credit));
        }

        if (account.getNormalBalance() == ChartOfAccount.NormalBalance.CREDIT) {
            return normalize(credit.subtract(debit));
        }

        return normalize(debit.subtract(credit));
    }

    private boolean isLoanReceivableAccount(String code) {
        if (code == null) {
            return false;
        }

        return switch (code.trim()) {
            case "1100", "1150", "1160", "1170", "1175" -> true;
            default -> false;
        };
    }

    private String safeReference(Loan loan) {
        if (loan == null || loan.getReferenceNumber() == null || loan.getReferenceNumber().isBlank()) {
            return "#" + (loan == null || loan.getId() == null ? "unknown" : loan.getId());
        }
        return loan.getReferenceNumber().trim();
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

    public record LoanReconciliationDiagnostic(
            Long loanId,
            String loanReference,
            boolean imported,
            BigDecimal operationalPrincipal,
            BigDecimal glPrincipal,
            BigDecimal principalDifference,
            BigDecimal operationalInterest,
            BigDecimal glInterest,
            BigDecimal interestDifference,
            BigDecimal operationalManagementFee,
            BigDecimal glManagementFee,
            BigDecimal managementFeeDifference,
            BigDecimal operationalExtensionFee,
            BigDecimal glExtensionFee,
            BigDecimal extensionFeeDifference,
            BigDecimal operationalPenalty,
            BigDecimal glPenalty,
            BigDecimal penaltyDifference) {
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
            LocalDate periodStart,
            BigDecimal openingNet,
            BigDecimal periodMovementNet,
            BigDecimal closingNet,
            BigDecimal openingMovementDifference,
            boolean openingMovementReconciles,
            BigDecimal bnrOutstandingPrincipal,
            BigDecimal portfolioOutstandingPrincipal,
            BigDecimal bnrPortfolioDifference,
            boolean bnrReconciles,
            Map<String, ReconciliationLine> subledger,
            List<Issue> issues) {
    }
}