package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.PaymentTransaction.TransactionStatus;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HistoricalRepaymentCorrectionService {

    private final LoanRepository loans;
    private final PaymentTransactionRepository receipts;
    private final HistoricalRepaymentCalculation calculation;
    private final AccountingService accounting;
    private final JdbcTemplate jdbc;

    @Transactional
    public long applyVerifiedSingleReceipt(long loanId, long receiptId,
            HistoricalRepaymentCalculation.Opening opening,
            List<HistoricalRepaymentCalculation.Charge> charges,
            LocalDate cutoff, LocalDate historicalOpeningCutoff, BigDecimal expectedPostedAssessment) {
        var loan = loans.findByIdForUpdate(loanId).orElseThrow();
        var receipt = receipts.findByOrganization_IdAndId(loan.getOrganization().getId(), receiptId).orElseThrow();
        if (!receipt.getLoan().getId().equals(loanId) || receipt.getStatus() != TransactionStatus.POSTED
                || Boolean.TRUE.equals(receipt.getReversed()) || receipt.getInstallment() == null
                || receipt.getInstallment().getPaidDate() == null
                || loans.countRecordedPaymentTransactions(loanId) != 1 || loan.getDeletedAt() != null) {
            throw new IllegalStateException("The verified receipt and loan snapshot no longer match");
        }
        var row = receipt.getInstallment();
        LocalDate paidOn = row.getPaidDate();
        var before = new PaymentAllocationService.Allocation(
                zero(receipt.getPenaltyComponent()), zero(receipt.getExtensionFeeComponent()),
                zero(receipt.getInterestComponent()), zero(receipt.getManagementFeeComponent()),
                zero(receipt.getPrincipalComponent()), zero(receipt.getUnappliedAmount()));
        var result = calculation.replay(opening, charges,
                List.of(new HistoricalRepaymentCalculation.Receipt(receiptId, paidOn, receipt.getAmount())), cutoff);
        var corrected = result.allocations().getFirst();
        var after = corrected.components();
        var previous = jdbc.queryForList(
                "SELECT journal_entry_id FROM historical_receipt_reallocations WHERE receipt_id = ?",
                Long.class, receiptId);
        if (!previous.isEmpty()) {
            Boolean matches = jdbc.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM historical_receipt_reallocations
                    WHERE receipt_id = ? AND as_of_date = ? AND amount = ? AND penalty = ? AND management_fee = ?
                      AND interest = ? AND principal = ? AND extension_fee = ? AND unapplied = ?)
                    """, Boolean.class, receiptId, cutoff, receipt.getAmount(), after.penalty(),
                    after.managementFee(), after.interest(), after.principal(), after.extensionFee(), after.overpayment());
            if (!Boolean.TRUE.equals(matches)) {
                throw new IllegalStateException("An existing receipt correction uses a different allocation. "
                        + "Review the existing journal before posting a further adjustment.");
            }
            return previous.getFirst();
        }

        BigDecimal revisedAssessed = opening.penalty().add(result.penaltyAccrued());
        BigDecimal assessmentDelta = revisedAssessed.subtract(expectedPostedAssessment);
        if (historicalOpeningCutoff == null || historicalOpeningCutoff.isBefore(opening.asOf())
                || !historicalOpeningCutoff.isBefore(paidOn) || historicalOpeningCutoff.isAfter(cutoff)) {
            throw new IllegalArgumentException("The verified opening cutoff must precede the receipt");
        }
        var openingReplay = calculation.replay(opening, List.of(), List.of(), historicalOpeningCutoff);
        BigDecimal expectedOpeningAssessment = opening.penalty().add(openingReplay.penaltyAccrued());
        BigDecimal postedOpeningAssessment = postedAssessment(loan.getOrganization().getId(),
                loan.getReferenceNumber(), "LEGACY_PENALTY_OPENING", "3010", cutoff);
        BigDecimal postedIncomeAssessment = postedAssessment(loan.getOrganization().getId(),
                loan.getReferenceNumber(), "PENALTY_ACCRUAL", "4100", cutoff);
        BigDecimal openingAssessmentDelta = expectedOpeningAssessment.subtract(postedOpeningAssessment);
        BigDecimal incomeAssessmentDelta = revisedAssessed.subtract(expectedOpeningAssessment)
                .subtract(postedIncomeAssessment);

        if (loan.getPenaltiesAssessedDecimal().compareTo(expectedPostedAssessment) != 0
                || postedOpeningAssessment.add(postedIncomeAssessment).compareTo(expectedPostedAssessment) != 0
                || loan.getOutstandingBalanceDecimal().compareTo(opening.principal().subtract(before.principal())) != 0
                || row.getAmountPaidDecimal().compareTo(receipt.getAmount()) != 0
                || !opening.maturity().equals(loan.getMaturityDate())
                || opening.monthlyPenaltyRate().compareTo(loan.getPenaltyRateDecimal()) != 0
                || before.extensionFee().compareTo(after.extensionFee()) != 0
                || before.overpayment().compareTo(after.overpayment()) != 0) {
            throw new IllegalStateException("The current balances do not match the verified correction");
        }

        assertOriginalReceiptJournal(loan.getOrganization().getId(), receiptId, row.getId(), paidOn,
                receipt.getAmount(), before);
        var original = new HistoricalRepaymentCalculation.Allocation(receiptId, paidOn, receipt.getAmount(), before);
        var debits = calculation.receiptReclassification(original, corrected);
        if (debtsUnchanged(debits) && openingAssessmentDelta.signum() == 0 && incomeAssessmentDelta.signum() == 0) {
            return 0L;
        }
        accounting.postHistoricalPenaltyRestatement(loan, receiptId,
                openingAssessmentDelta, incomeAssessmentDelta);
        var journal = accounting.postHistoricalReceiptReclassification(loan, receiptId, debits);
        if (journal == null) {
            throw new IllegalArgumentException("The receipt has no allocation changes");
        }

        BigDecimal interestPaid = loan.getInterestPaidDecimal().subtract(before.interest()).add(after.interest());
        BigDecimal managementPaid = loan.getManagementFeePaidDecimal()
                .subtract(before.managementFee()).add(after.managementFee());
        BigDecimal penaltyPaid = loan.getPenaltiesPaidDecimal().subtract(before.penalty()).add(after.penalty());
        if (interestPaid.signum() < 0 || interestPaid.compareTo(loan.getTotalInterestDecimal()) > 0
                || managementPaid.signum() < 0 || managementPaid.compareTo(loan.getManagementFeeDecimal()) > 0
                || loan.getTotalInterestDecimal().subtract(interestPaid).compareTo(result.interest()) != 0
                || loan.getManagementFeeDecimal().subtract(managementPaid).compareTo(result.management()) != 0
                || penaltyPaid.signum() < 0 || penaltyPaid.compareTo(revisedAssessed) > 0
                || revisedAssessed.subtract(penaltyPaid).compareTo(result.penalty()) != 0) {
            throw new IllegalStateException("Revised paid balances exceed their assessments");
        }

        loan.setOutstandingBalance(result.principal());
        loan.setPrincipalPaid(loan.getPrincipalPaidDecimal().subtract(before.principal()).add(after.principal()));
        loan.setInterestPaid(interestPaid);
        loan.setInterestOutstanding(loan.getTotalInterestDecimal().subtract(interestPaid));
        loan.setManagementFeePaid(managementPaid);
        loan.setManagementFeeOutstanding(loan.getManagementFeeDecimal().subtract(managementPaid));
        loan.setPenaltiesAssessed(revisedAssessed);
        loan.setPenaltiesPaid(penaltyPaid);
        loan.setTotalRepayable(loan.getTotalRepayableDecimal().add(assessmentDelta));

        row.setPrincipalComponent(after.principal());
        row.setInterestComponent(after.interest());
        row.setManagementFeeComponent(after.managementFee());
        row.setCycleInterestDue(after.interest().add(result.interest()));
        row.setCycleInterestRemaining(result.interest());
        row.setCycleManagementFeeDue(after.managementFee().add(result.management()));
        row.setCycleManagementFeeRemaining(result.management());
        row.setPenaltyPaid(after.penalty());
        row.setPenalty(after.penalty());
        row.setOutstandingAfter(result.principal());

        jdbc.update("""
                INSERT INTO historical_receipt_reallocations
                    (receipt_id, journal_entry_id, as_of_date, amount, penalty,
                     management_fee, interest, principal, extension_fee, unapplied)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, receiptId, journal.getId(), cutoff, receipt.getAmount(), after.penalty(),
                after.managementFee(), after.interest(), after.principal(), after.extensionFee(), after.overpayment());
        return journal.getId();
    }

    private void assertOriginalReceiptJournal(long organizationId, long receiptId, long installmentId,
            LocalDate paidOn, BigDecimal amount, PaymentAllocationService.Allocation original) {
        var journalIds = jdbc.queryForList("""
                SELECT id FROM journal_entries
                WHERE organization_id = ? AND source_type = 'PAYMENT_RECEIVED'
                  AND source_id IN (?, ?) AND entry_date = ?
                  AND NOT reversed AND recycle_loan_id IS NULL
                """, Long.class, organizationId, "PAYTX:" + receiptId, Long.toString(installmentId), paidOn);
        if (journalIds.size() != 1) {
            throw new IllegalStateException("The original cash journal could not be uniquely verified");
        }
        var expected = java.util.Map.of(
                "1000", amount, "1100", original.principal().negate(),
                "1150", original.interest().negate(), "1160", original.managementFee().negate(),
                "1170", original.extensionFee().negate(), "1175", original.penalty().negate(),
                "2100", original.overpayment().negate());
        var actual = new java.util.HashMap<String, BigDecimal>();
        for (var line : jdbc.queryForList("""
                SELECT account.code, SUM(line.debit - line.credit) AS balance
                FROM journal_lines line JOIN chart_of_accounts account ON account.id = line.account_id
                WHERE line.journal_entry_id = ?
                GROUP BY account.code
                """, journalIds.getFirst())) {
            actual.put((String) line.get("code"), (BigDecimal) line.get("balance"));
        }
        if (!expected.keySet().containsAll(actual.keySet())
                || expected.entrySet().stream().anyMatch(item -> item.getValue()
                        .compareTo(actual.getOrDefault(item.getKey(), BigDecimal.ZERO)) != 0)) {
            throw new IllegalStateException("Original receipt journal differs from the posted allocation");
        }
    }

    private BigDecimal postedAssessment(long organizationId, String loanReference,
            String sourceType, String offsetAccount, LocalDate cutoff) {
        var totals = jdbc.queryForList("""
                SELECT account.code, SUM(line.debit - line.credit) AS balance
                FROM journal_entries entry
                JOIN journal_lines line ON line.journal_entry_id = entry.id
                JOIN chart_of_accounts account ON account.id = line.account_id
                WHERE entry.organization_id = ? AND entry.reference = ? AND entry.source_type = ?
                  AND entry.entry_date <= ? AND NOT entry.reversed AND entry.recycle_loan_id IS NULL
                GROUP BY account.code
                """, organizationId, loanReference, sourceType, cutoff);
        BigDecimal receivable = BigDecimal.ZERO;
        BigDecimal offset = BigDecimal.ZERO;
        for (var total : totals) {
            String code = (String) total.get("code");
            BigDecimal balance = (BigDecimal) total.get("balance");
            if ("1175".equals(code)) {
                receivable = balance;
            } else if (offsetAccount.equals(code)) {
                offset = balance;
            } else {
                throw new IllegalStateException("Unexpected penalty journal account " + code);
            }
        }
        if (receivable.signum() < 0 || receivable.add(offset).signum() != 0) {
            throw new IllegalStateException("Penalty journal totals do not reconcile");
        }
        return receivable;
    }

    private static boolean debtsUnchanged(java.util.Map<String, BigDecimal> changes) {
        return changes.values().stream().allMatch(value -> value.signum() == 0);
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
