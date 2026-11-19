package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.LoanPrincipalAmendmentRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
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
        return item;
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
