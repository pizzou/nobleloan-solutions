package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.LoanPrincipalAmendmentRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.PaymentRepository;
import com.patrick.fintech.loan_backend.repository.PaymentScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Common request and independent-approval ledger for imported and native loans.
 * No live financial balance is changed merely because a request is approved.
 */
@Service
@RequiredArgsConstructor
public class LoanPrincipalAmendmentService {
    private final LoanRepository loans;
    private final LoanPrincipalAmendmentRepository requests;
    private final AuditService audit;
    private final AccountingService accounting;
    private final PaymentRepository payments;
    private final PaymentScheduleRepository schedules;
    private final HolidayService holidayService;

    public record Proposal(String confirmReference, BigDecimal correctedPrincipal,
                           LoanPrincipalAmendmentRequest.CorrectionType correctionType,
                           LocalDate correctedDisbursementDate,
                           BigDecimal correctedInterestRate,
                           BigDecimal correctedManagementFeeRate,
                           String reason, String evidenceReference) {}
    public record Decision(String confirmReference, String reason) {}

    @Transactional
    public List<LoanPrincipalAmendmentRequest> history(Long loanId, User actor) {
        requireRole(actor, "BUSINESS_OWNER", "ADMIN", "MANAGER", "ACCOUNTANT", "LOAN_OFFICER", "AUDITOR");
        Loan loan = ownedLoan(loanId, actor);
        return requests.findByOrganizationIdAndLoanIdOrderByCreatedAtDesc(organization(actor), loan.getId());
    }

    @Transactional(readOnly = true)
    public List<LoanPrincipalAmendmentRequest> pending(User actor) {
        requireRole(actor, "BUSINESS_OWNER");
        return requests.findByOrganizationIdAndStatusOrderByCreatedAtAsc(
                organization(actor), LoanPrincipalAmendmentRequest.Status.PENDING_REVIEW);
    }

    @Transactional
    public LoanPrincipalAmendmentRequest submit(Long loanId, Proposal proposal, User actor) {
        requireRole(actor, "ADMIN", "MANAGER", "LOAN_OFFICER", "ACCOUNTANT");
        Loan loan = lockedLoan(loanId, actor);
        if (proposal == null || proposal.correctionType() == null)
            throw new IllegalArgumentException("Correction type and details are required");
        requireReference(loan, proposal.confirmReference());
        if (loan.getDeletedAt() != null || loan.getStatus() == LoanStatus.CANCELLED
                || loan.getStatus() == LoanStatus.WRITTEN_OFF)
            throw new IllegalStateException("This loan requires separate recovery or deletion controls");
        BigDecimal corrected = proposal.correctedPrincipal() == null
                ? money(loan.getAmountDecimal()) : money(proposal.correctedPrincipal());
        BigDecimal existing = money(loan.getAmountDecimal());
        if (corrected.signum() <= 0)
            throw new IllegalArgumentException("Corrected principal must be positive");
        LocalDate oldDate = loan.getDisbursedAt() == null ? null : loan.getDisbursedAt().toLocalDate();
        LocalDate proposedDate = proposal.correctedDisbursementDate() == null
                ? oldDate : proposal.correctedDisbursementDate();
        if (proposedDate != null && proposedDate.isAfter(LocalDate.now()))
            throw new IllegalArgumentException("Disbursement date cannot be in the future");
        BigDecimal oldInterest = rate(loan.getInterestRateDecimal());
        BigDecimal newInterest = proposal.correctedInterestRate() == null
                ? oldInterest : rate(proposal.correctedInterestRate());
        BigDecimal oldManagement = rate(loan.getManagementFeeRateDecimal());
        BigDecimal newManagement = proposal.correctedManagementFeeRate() == null
                ? oldManagement : rate(proposal.correctedManagementFeeRate());
        if ((proposal.correctedInterestRate() != null && (newInterest.signum() < 0 || newInterest.compareTo(new BigDecimal("5")) > 0))
                || (proposal.correctedManagementFeeRate() != null && (newManagement.signum() < 0 || newManagement.compareTo(new BigDecimal("5")) > 0)))
            throw new IllegalArgumentException("Negotiated interest and management fee must each be 0%–5%");
        if (corrected.compareTo(existing) == 0 && Objects.equals(oldDate, proposedDate)
                && oldInterest.compareTo(newInterest) == 0
                && oldManagement.compareTo(newManagement) == 0)
            throw new IllegalArgumentException("At least one contractual value must change");
        if (proposal.correctionType() == LoanPrincipalAmendmentRequest.CorrectionType.ADDITIONAL_DISBURSEMENT
                && corrected.compareTo(existing) <= 0)
            throw new IllegalArgumentException("Additional disbursement must increase principal");
        if (proposal.correctionType() == LoanPrincipalAmendmentRequest.CorrectionType.CONTRACT_TERMS
                && corrected.compareTo(existing) != 0)
            throw new IllegalArgumentException("Choose recording error or additional disbursement to change principal");
        String reason = explanation(proposal.reason(), 30, 2000, "Correction reason");
        String evidence = explanation(proposal.evidenceReference(), 8, 250, "Supporting evidence reference");
        if (requests.existsByOrganizationIdAndLoanIdAndStatusIn(organization(actor), loanId,
                List.of(LoanPrincipalAmendmentRequest.Status.PENDING_REVIEW,
                        LoanPrincipalAmendmentRequest.Status.APPROVED_FOR_RECONCILIATION)))
            throw new IllegalStateException("Resolve the existing principal amendment before creating another");

        LoanPrincipalAmendmentRequest item = new LoanPrincipalAmendmentRequest();
        item.setOrganizationId(organization(actor));
        item.setLoanId(loanId);
        item.setRequesterId(actor.getId());
        item.setLoanReference(loan.getReferenceNumber());
        item.setCorrectionType(proposal.correctionType());
        item.setOldPrincipal(existing);
        item.setCorrectedPrincipal(corrected);
        item.setOriginalDisbursedAmount(money(loan.getDisbursedAmountDecimal()));
        item.setOriginalTotalPaid(money(loan.getTotalPaidDecimal()));
        item.setOldDisbursementDate(oldDate);
        item.setCorrectedDisbursementDate(proposedDate);
        item.setOldInterestRate(oldInterest);
        item.setCorrectedInterestRate(newInterest);
        item.setOldManagementFeeRate(oldManagement);
        item.setCorrectedManagementFeeRate(newManagement);
        item.setReason(reason);
        item.setEvidenceReference(evidence);
        requests.saveAndFlush(item);
        audit.log(loan.getOrganization(), actor, "LOAN_PRINCIPAL_AMENDMENT_REQUESTED", "Loan",
                loanId.toString(), "Amendment " + item.getId() + ": " + reason
                        + "; evidence=" + evidence + "; type=" + item.getCorrectionType(),
                existing.toPlainString(), corrected.toPlainString(), "Loans");
        return item;
    }

    @Transactional
    public LoanPrincipalAmendmentRequest decide(Long loanId, Long requestId, boolean approved,
                                                Decision decision, User actor) {
        requireRole(actor, "BUSINESS_OWNER");
        Loan loan = lockedLoan(loanId, actor);
        if (decision == null) throw new IllegalArgumentException("Review decision is required");
        requireReference(loan, decision.confirmReference());
        String reviewReason = explanation(decision.reason(), 20, 2000, "Independent review reason");
        LoanPrincipalAmendmentRequest item = requests.lock(organization(actor), loanId, requestId)
                .orElseThrow(() -> new IllegalArgumentException("Principal amendment not found"));
        if (item.getStatus() != LoanPrincipalAmendmentRequest.Status.PENDING_REVIEW)
            throw new IllegalStateException("Amendment has already been reviewed");
        if (Objects.equals(item.getRequesterId(), actor.getId()))
            throw new AccessDeniedException("The requester cannot review their own amendment");
        if (approved && (!Objects.equals(item.getLoanReference(), loan.getReferenceNumber())
                || item.getOldPrincipal().compareTo(money(loan.getAmountDecimal())) != 0
                || item.getOriginalDisbursedAmount().compareTo(money(loan.getDisbursedAmountDecimal())) != 0
                || item.getOriginalTotalPaid().compareTo(money(loan.getTotalPaidDecimal())) != 0
                || !Objects.equals(item.getOldDisbursementDate(),
                        loan.getDisbursedAt() == null ? null : loan.getDisbursedAt().toLocalDate())
                || item.getOldInterestRate().compareTo(rate(loan.getInterestRateDecimal())) != 0
                || item.getOldManagementFeeRate().compareTo(rate(loan.getManagementFeeRateDecimal())) != 0))
            throw new IllegalStateException("Loan changed since request; reject and submit a fresh amendment");

        item.setStatus(approved
                ? LoanPrincipalAmendmentRequest.Status.APPROVED_FOR_RECONCILIATION
                : LoanPrincipalAmendmentRequest.Status.REJECTED);
        item.setReviewerId(actor.getId());
        item.setReviewerReason(reviewReason);
        item.setReviewedAt(LocalDateTime.now());
        requests.saveAndFlush(item);
        audit.log(loan.getOrganization(), actor,
                approved ? "LOAN_PRINCIPAL_AMENDMENT_REVIEW_APPROVED" : "LOAN_PRINCIPAL_AMENDMENT_REJECTED",
                "Loan", loanId.toString(), "Amendment " + requestId + ": " + reviewReason,
                item.getOldPrincipal().toPlainString(), item.getCorrectedPrincipal().toPlainString(), "Loans");
        // Owner authorization must persist independently of financial execution.
        // Repricing complex historical schedules can fail safely; that must not
        // erase the owner's decision or make the request disappear from the
        // review workflow. A separate authorized /apply operation performs the
        // reconciled accounting changes transactionally and idempotently.
        return item;
    }

    /** Retry a previously approved reconciliation; duplicate execution is forbidden. */
    @Transactional
    public LoanPrincipalAmendmentRequest apply(Long loanId, Long requestId, User actor) {
        requireRole(actor, "BUSINESS_OWNER", "ACCOUNTANT");
        Loan loan = lockedLoan(loanId, actor);
        LoanPrincipalAmendmentRequest item = requests.lock(organization(actor), loanId, requestId)
                .orElseThrow(() -> new IllegalArgumentException("Loan amendment not found"));
        if (item.getStatus() != LoanPrincipalAmendmentRequest.Status.APPROVED_FOR_RECONCILIATION)
            throw new IllegalStateException("Only an independently approved amendment can be applied");
        applyFinancial(loan, item, actor);
        return item;
    }

    private void applyFinancial(Loan loan, LoanPrincipalAmendmentRequest item, User actor) {
        if (item.getStatus() != LoanPrincipalAmendmentRequest.Status.APPROVED_FOR_RECONCILIATION
                || item.getReviewerId() == null
                || Objects.equals(item.getRequesterId(), item.getReviewerId()))
            throw new IllegalStateException("Independent approval is required for financial reconciliation");
        if (!Objects.equals(item.getLoanReference(), loan.getReferenceNumber())
                || item.getOldPrincipal().compareTo(money(loan.getAmountDecimal())) != 0
                || item.getOriginalDisbursedAmount().compareTo(money(loan.getDisbursedAmountDecimal())) != 0
                || item.getOriginalTotalPaid().compareTo(money(loan.getTotalPaidDecimal())) != 0
                || !Objects.equals(item.getOldDisbursementDate(),
                        loan.getDisbursedAt() == null ? null : loan.getDisbursedAt().toLocalDate())
                || item.getOldInterestRate().compareTo(rate(loan.getInterestRateDecimal())) != 0
                || item.getOldManagementFeeRate().compareTo(rate(loan.getManagementFeeRateDecimal())) != 0)
            throw new IllegalStateException("Loan changed since independent approval; re-submit the correction");
        if (loan.getDeletedAt() != null || loan.getStatus() == LoanStatus.WRITTEN_OFF
                || loan.getStatus() == LoanStatus.CANCELLED)
            throw new IllegalStateException("Written-off, cancelled or deleted loans require a recovery workflow");

        boolean changesFinancialTerms = item.getOldPrincipal().compareTo(item.getCorrectedPrincipal()) != 0
                || item.getOldInterestRate().compareTo(item.getCorrectedInterestRate()) != 0
                || item.getOldManagementFeeRate().compareTo(item.getCorrectedManagementFeeRate()) != 0;
        if (changesFinancialTerms && loan.getDisbursedAt() == null)
            throw new IllegalStateException("Undisbursed loans must use the existing approval amendment workflow");
        if (item.getCorrectionType() == LoanPrincipalAmendmentRequest.CorrectionType.ADDITIONAL_DISBURSEMENT)
            throw new IllegalStateException("Additional disbursements require the cash-controlled disbursement workflow; a correction approval cannot create a cash transaction");
        if (!Objects.equals(item.getOldDisbursementDate(), item.getCorrectedDisbursementDate())
                && money(loan.getPenaltiesAssessedDecimal()).signum() > 0)
            throw new IllegalStateException("Disbursement date correction requires a separate historical penalty restatement");
        if (changesFinancialTerms && (loan.getInterestRateType() == null
                || !"MONTHLY".equalsIgnoreCase(loan.getInterestRateType())))
            throw new IllegalStateException("Only monthly contractual loans support automatic repricing");

        BigDecimal oldPrincipal = money(loan.getAmountDecimal());
        BigDecimal oldInterest = money(loan.getTotalInterestDecimal());
        BigDecimal oldManagement = money(loan.getManagementFeeDecimal());
        BigDecimal oldPrincipalPaid = money(loan.getPrincipalPaidDecimal());
        BigDecimal oldInterestPaid = money(loan.getInterestPaidDecimal());
        BigDecimal oldManagementPaid = money(loan.getManagementFeePaidDecimal());
        BigDecimal totalPaid = money(loan.getTotalPaidDecimal());
        BigDecimal nonContractualPaid = money(loan.getPenaltiesPaidDecimal())
                .add(money(loan.getExtensionFeePaidDecimal()));
        BigDecimal repaymentPool = money(totalPaid.subtract(nonContractualPaid));
        BigDecimal oldApplied = money(oldPrincipalPaid.add(oldInterestPaid).add(oldManagementPaid));
        BigDecimal oldUnallocated = money(repaymentPool.subtract(oldApplied));
        if (repaymentPool.signum() < 0 || oldUnallocated.signum() < 0)
            throw new IllegalStateException("Historical payment components do not reconcile with total paid");

        var target = changesFinancialTerms
                ? LoanAmendmentReconciliationPlanner.contract(item.getCorrectedPrincipal(),
                        loan.getDurationMonths(), item.getCorrectedInterestRate(),
                        item.getCorrectedManagementFeeRate())
                : new LoanAmendmentReconciliationPlanner.ContractTotals(oldPrincipal, oldInterest,
                        oldManagement, money(oldPrincipal.add(oldInterest).add(oldManagement)));
        var plan = LoanAmendmentReconciliationPlanner.plan(target, repaymentPool,
                loan.getOutstandingBalanceDecimal(), loan.getInterestOutstandingDecimal(),
                loan.getManagementFeeOutstandingDecimal());
        var allocation = plan.allocation();
        BigDecimal overpaymentDelta = money(allocation.unallocated().subtract(oldUnallocated));

        if (changesFinancialTerms) {
            accounting.postApprovedLoanAmendment(loan, item.getId(),
                    plan.principalReceivableChange(), plan.interestReceivableChange(),
                    plan.managementReceivableChange(),
                    money(target.principal().subtract(oldPrincipal)),
                    money(target.interest().subtract(oldInterest)),
                    money(target.managementFee().subtract(oldManagement)), overpaymentDelta);
        }

        loan.setAmount(target.principal());
        loan.setInterestRate(item.getCorrectedInterestRate());
        loan.setManagementFeeRate(item.getCorrectedManagementFeeRate());
        loan.setTotalInterest(target.interest());
        loan.setManagementFee(target.managementFee());
        loan.setPrincipalPaid(allocation.principalPaid());
        loan.setInterestPaid(allocation.interestPaid());
        loan.setManagementFeePaid(allocation.managementFeePaid());
        loan.setOutstandingBalance(allocation.principalOutstanding());
        loan.setInterestOutstanding(allocation.interestOutstanding());
        loan.setManagementFeeOutstanding(allocation.managementFeeOutstanding());
        loan.setTotalRepayable(target.repayable());

        BigDecimal principalChange = money(target.principal().subtract(oldPrincipal));
        if (principalChange.signum() != 0) {
            loan.setDisbursedAmount(money(money(loan.getDisbursedAmountDecimal()).add(principalChange)));
            if (loan.getNetDisbursedAmountDecimal() != null)
                loan.setNetDisbursedAmount(money(loan.getNetDisbursedAmountDecimal().add(principalChange)));
        }
        if (item.getCorrectedDisbursementDate() != null) {
            java.time.LocalTime originalTime = loan.getDisbursedAt() == null
                    ? java.time.LocalTime.MIDNIGHT : loan.getDisbursedAt().toLocalTime();
            loan.setDisbursedAt(item.getCorrectedDisbursementDate().atTime(originalTime));
        }

        reconcileFutureSchedules(loan, allocation, item.getCorrectedDisbursementDate());

        BigDecimal totalDue = money(allocation.totalOutstanding()
                .add(money(loan.getExtensionFeeOutstandingDecimal()))
                .add(money(loan.getPenaltiesAssessedDecimal()).subtract(money(loan.getPenaltiesPaidDecimal()))));
        if (totalDue.signum() == 0) loan.setStatus(LoanStatus.PAID);
        else if (loan.getStatus() == LoanStatus.PAID) loan.setStatus(LoanStatus.ACTIVE);
        loans.saveAndFlush(loan);

        item.setStatus(LoanPrincipalAmendmentRequest.Status.APPLIED);
        requests.saveAndFlush(item);
        audit.log(loan.getOrganization(), actor, "LOAN_AMENDMENT_FINANCIALLY_APPLIED", "Loan",
                String.valueOf(loan.getId()), "Approved correction #" + item.getId()
                        + " reconciled; receivable deltas=" + plan.principalReceivableChange() + "/"
                        + plan.interestReceivableChange() + "/" + plan.managementReceivableChange(),
                item.getOldPrincipal().toPlainString(), item.getCorrectedPrincipal().toPlainString(), "Loans");
    }

    /**
     * Re-amortize only unpaid schedule rows. Historical paid installment rows
     * and repayment journal entries are not changed or deleted.
     */
    private void reconcileFutureSchedules(Loan loan,
            LoanAmendmentAllocationCalculator.Allocation allocation, LocalDate correctedDate) {
        var operational = payments.findByLoanId(loan.getId());
        if (operational.stream().anyMatch(p -> money(p.getAmountPaidDecimal()).signum() > 0
                && !Boolean.TRUE.equals(p.getPaid())))
            throw new IllegalStateException("Partially paid installments require payment-by-payment reconciliation before automatic repricing");
        var unpaid = operational.stream()
                .filter(p -> !Boolean.TRUE.equals(p.getPaid())
                        && money(p.getAmountPaidDecimal()).signum() == 0)
                .sorted(java.util.Comparator.comparing(Payment::getInstallmentNumber,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .toList();
        var calendar = schedules.findByLoanIdOrderByInstallmentNumberAsc(loan.getId());
        if (calendar.stream().anyMatch(s -> money(s.getAmountPaid()).signum() > 0
                && s.getStatus() != PaymentSchedule.ScheduleStatus.PAID))
            throw new IllegalStateException("Partially paid schedule rows require payment-by-payment reconciliation before automatic repricing");
        var unpaidCalendar = calendar.stream()
                .filter(s -> money(s.getAmountPaid()).signum() == 0
                        && s.getStatus() != PaymentSchedule.ScheduleStatus.PAID)
                .toList();
        if (!operational.isEmpty() && unpaid.isEmpty() && allocation.totalOutstanding().signum() > 0)
            throw new IllegalStateException("No unpaid installment remains to absorb the corrected obligation");
        if (!calendar.isEmpty() && unpaidCalendar.isEmpty() && allocation.totalOutstanding().signum() > 0)
            throw new IllegalStateException("No unpaid payment schedule remains to absorb the corrected obligation");

        BigDecimal remainingPrincipal = allocation.principalOutstanding();
        BigDecimal remainingInterest = allocation.interestOutstanding();
        BigDecimal remainingManagement = allocation.managementFeeOutstanding();
        for (int i = 0; i < unpaid.size(); i++) {
            Payment p = unpaid.get(i);
            int left = unpaid.size() - i;
            BigDecimal principal = slice(remainingPrincipal, left);
            BigDecimal interest = slice(remainingInterest, left);
            BigDecimal management = slice(remainingManagement, left);
            p.setPrincipalComponent(principal);
            p.setInterestComponent(interest);
            p.setManagementFeeComponent(management);
            p.setScheduledInterest(interest);
            p.setScheduledManagementFee(management);
            p.setCycleInterestDue(interest);
            p.setCycleInterestRemaining(interest);
            p.setCycleManagementFeeDue(management);
            p.setCycleManagementFeeRemaining(management);
            p.setAmount(money(principal.add(interest).add(management)));
            remainingPrincipal = money(remainingPrincipal.subtract(principal));
            remainingInterest = money(remainingInterest.subtract(interest));
            remainingManagement = money(remainingManagement.subtract(management));
            p.setOutstandingAfter(remainingPrincipal);
            if (correctedDate != null && p.getInstallmentNumber() != null)
                p.setDueDate(holidayService.adjustToBusinessDay(loan.getOrganization().getId(),
                        correctedDate.plusMonths(p.getInstallmentNumber())));
        }
        payments.saveAll(unpaid);

        remainingPrincipal = allocation.principalOutstanding();
        remainingInterest = allocation.interestOutstanding();
        remainingManagement = allocation.managementFeeOutstanding();
        for (int i = 0; i < unpaidCalendar.size(); i++) {
            PaymentSchedule s = unpaidCalendar.get(i);
            int left = unpaidCalendar.size() - i;
            BigDecimal principal = slice(remainingPrincipal, left);
            BigDecimal interest = slice(remainingInterest, left);
            BigDecimal management = slice(remainingManagement, left);
            s.setPrincipalAmount(principal);
            s.setInterestAmount(interest);
            s.setManagementFeeAmount(management);
            s.setInstallmentAmount(money(principal.add(interest).add(management)));
            remainingPrincipal = money(remainingPrincipal.subtract(principal));
            remainingInterest = money(remainingInterest.subtract(interest));
            remainingManagement = money(remainingManagement.subtract(management));
            s.setRemainingBalance(remainingPrincipal);
            if (correctedDate != null)
                s.setDueDate(holidayService.adjustToBusinessDay(loan.getOrganization().getId(),
                        correctedDate.plusMonths(s.getInstallmentNumber())));
        }
        schedules.saveAll(unpaidCalendar);
        if (!unpaid.isEmpty()) {
            loan.setNextDueDate(unpaid.get(0).getDueDate());
            loan.setNextPaymentDate(unpaid.get(0).getDueDate());
            loan.setNextInstallmentAmount(unpaid.get(0).getAmount());
        }
    }

    private BigDecimal slice(BigDecimal total, int installmentsLeft) {
        return installmentsLeft == 1 ? money(total) : money(total.divide(
                BigDecimal.valueOf(installmentsLeft), 2, RoundingMode.HALF_UP));
    }

    private Loan ownedLoan(Long id, User actor) {
        boolean includeOwnerOnly = actor.getRole() != null
                && "BUSINESS_OWNER".equalsIgnoreCase(actor.getRole().getName());
        return loans.findVisibleByIdForUpdate(id, organization(actor), includeOwnerOnly)
                .orElseThrow(() -> new IllegalArgumentException("Loan not found in organization"));
    }
    private Loan lockedLoan(Long id, User actor) { return ownedLoan(id, actor); }
    private Long organization(User actor) {
        if (actor == null || actor.getOrganization() == null || actor.getOrganization().getId() == null)
            throw new AccessDeniedException("Organization is required");
        return actor.getOrganization().getId();
    }
    private void requireRole(User actor, String... allowed) {
        String actual = actor == null || actor.getRole() == null ? "" : actor.getRole().getName();
        for (String role : allowed) if (role.equalsIgnoreCase(actual)) return;
        throw new AccessDeniedException("Insufficient role for principal amendment");
    }
    private void requireReference(Loan loan, String reference) {
        if (reference == null || !reference.trim().equals(loan.getReferenceNumber()))
            throw new IllegalArgumentException("Type the exact loan reference to confirm");
    }
    private String explanation(String value, int min, int max, String label) {
        String text = value == null ? "" : value.trim();
        if (text.length() < min || text.length() > max)
            throw new IllegalArgumentException(label + " must be " + min + "–" + max + " characters");
        return text;
    }
    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
    private BigDecimal rate(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(6, RoundingMode.HALF_UP);
    }
}
