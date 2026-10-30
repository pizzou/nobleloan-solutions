package com.patrick.fintech.loan_backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patrick.fintech.loan_backend.dto.LegacyLoanCorrectionRequest;
import com.patrick.fintech.loan_backend.dto.LegacyLoanCorrectionDecision;
import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Controlled post-import repair of incorrectly transcribed legacy records.
 * No cash receipt, payment or loan reversal is synthesized. Existing GL history
 * is immutable.
 * Normal loans: Admin/Manager/Accountant requests; only Business Owner approves
 * and posts.
 * Owner-only loans: two different Business Owners can follow maker/checker;
 * alternatively, a Business Owner may directly correct eligible, untouched
 * imported opening records with an explicitly audited owner exception.
 */
@Service
@RequiredArgsConstructor
public class LegacyLoanCorrectionService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private final LoanRepository loans;
    private final LegacyLoanCorrectionRepository corrections;
    private final AccountingService accounting;
    private final AuditService audit;
    private final ObjectMapper mapper;
    private final EntityManager em;

    public record Preview(String reference, Map<String, Object> current, Map<String, Object> proposed,
            Map<String, BigDecimal> receivableDeltas, boolean changesFinancialBalances) {
    }

    @Transactional(readOnly = true)
    public List<LegacyLoanCorrection> history(Long loanId, User actor) {
        Loan loan = ownedLoan(loanId, actor);
        requireRole(actor, "BUSINESS_OWNER", "ADMIN", "MANAGER", "ACCOUNTANT", "LOAN_OFFICER", "CREDIT_ANALYST",
                "AUDITOR");
        return corrections.findByOrganizationIdAndLoanIdOrderByCreatedAtDesc(org(actor), loan.getId());
    }

    @Transactional(readOnly = true)
    public Preview preview(Long loanId, LegacyLoanCorrectionRequest request, User actor) {
        Loan loan = ownedLoan(loanId, actor);
        // Preview is shared by staff requesters and Business Owners who use
        // the direct owner correction route. Submission privileges remain separate.
        requireRole(actor, "BUSINESS_OWNER", "ADMIN", "MANAGER", "ACCOUNTANT", "LOAN_OFFICER", "CREDIT_ANALYST");
        requireEligible(loan);
        requireConfirmed(loan, request.confirmReference());
        Map<String, Object> before = snapshot(loan);
        Map<String, Object> after = requested(before, request, loan);
        return new Preview(loan.getReferenceNumber(), before, after, deltas(before, after),
                !deltas(before, after).isEmpty());
    }

    @Transactional
    public LegacyLoanCorrection submit(Long loanId, LegacyLoanCorrectionRequest request, User actor) {
        Loan loan = lockedLoan(loanId, actor);
        requireProposer(loan, actor);
        requireEligible(loan);
        requireConfirmed(loan, request.confirmReference());
        String reason = explanation(request.reason(), 30, 2000, "Correction justification");
        String evidence = explanation(request.evidenceReference(), 8, 250, "Supporting evidence reference");
        Map<String, Object> before = snapshot(loan);
        Map<String, Object> after = requested(before, request, loan);
        if (before.equals(after))
            throw new IllegalArgumentException("No actual change was proposed");
        if (corrections.existsByOrganizationIdAndLoanIdAndStatus(org(actor), loanId, "PENDING"))
            throw new IllegalStateException("An unreviewed correction already exists for this loan");
        // Repairing the imported contractual term must not silently rewrite an existing
        // schedule.
        // For monetary changes, any later transactions require a separate reconciled
        // case.
        if (!deltas(before, after).isEmpty()
                || !Objects.equals(before.get("durationMonths"), after.get("durationMonths")))
            assertNoSubsequentFinancialActivity(loan);
        LegacyLoanCorrection item = new LegacyLoanCorrection();
        item.setOrganizationId(org(actor));
        item.setLoanId(loanId);
        item.setMakerId(actor.getId());
        item.setReason(reason);
        item.setEvidenceReference(evidence);
        item.setBeforeSnapshot(json(before));
        item.setAfterSnapshot(json(after));
        item.setSnapshotHash(hash(json(before)));
        corrections.saveAndFlush(item);
        audit.log(loan.getOrganization(), actor, "LEGACY_CORRECTION_REQUESTED", "Loan",
                String.valueOf(loanId), reason + "; evidence=" + evidence,
                item.getBeforeSnapshot(), item.getAfterSnapshot(), "Loans");
        return item;
    }

    /**
     * Owner-initiated, immediate correction of an imported opening record.
     * Safe for transcription mistakes discovered BEFORE subsequent activity.
     * This is an explicit exception to two-person approval, never a way to
     * alter real payments, backdate cash or erase accounting entries.
     * All steps (audit row, balanced GL adjustment, loan update) share one TX.
     */
    @Transactional
    public LegacyLoanCorrection applyOwnerCorrection(Long loanId, LegacyLoanCorrectionRequest request, User actor) {
        requireRole(actor, "BUSINESS_OWNER");
        Loan loan = lockedLoan(loanId, actor);
        requireEligible(loan);
        if (request == null)
            throw new IllegalArgumentException("Correction payload is required");
        requireConfirmed(loan, request.confirmReference());
        String reason = explanation(request.reason(), 30, 2000, "Owner correction justification");
        String evidence = explanation(request.evidenceReference(), 8, 250, "Supporting evidence reference");
        if (corrections.existsByOrganizationIdAndLoanIdAndStatus(org(actor), loanId, "PENDING"))
            throw new IllegalStateException("Resolve the pending independent correction request before applying another correction");
        Map<String, Object> before = snapshot(loan);
        Map<String, Object> after = requested(before, request, loan);
        if (before.equals(after))
            throw new IllegalArgumentException("No actual change was proposed");
        // The existing payment/schedule/journal guard is non-negotiable for
        // direct repair. A loan with later activity needs a reconciled case.
        assertNoSubsequentFinancialActivity(loan);
        LegacyLoanCorrection item = new LegacyLoanCorrection();
        item.setOrganizationId(org(actor));
        item.setLoanId(loanId);
        item.setMakerId(actor.getId());
        item.setOwnerDirect(true);
        item.setReason(reason);
        item.setEvidenceReference(evidence);
        item.setBeforeSnapshot(json(before));
        item.setAfterSnapshot(json(after));
        item.setSnapshotHash(hash(json(before)));
        corrections.saveAndFlush(item);
        Map<String, BigDecimal> changes = deltas(before, after);
        if (!changes.isEmpty()) {
            JournalEntry entry = accounting.postApprovedLegacyCorrection(loan, item.getId(), changes);
            if (entry != null)
                item.setJournalEntryId(entry.getId());
        }
        apply(loan, after);
        loans.saveAndFlush(loan);
        item.setStatus("APPROVED");
        item.setCheckerId(actor.getId());
        item.setReviewedAt(LocalDateTime.now());
        item.setReviewReason("Direct correction authorized by the Business Owner with evidence: " + evidence);
        corrections.saveAndFlush(item);
        audit.log(loan.getOrganization(), actor, "BUSINESS_OWNER_DIRECT_LEGACY_CORRECTION", "Loan",
                String.valueOf(loanId), "Single-owner correction override: " + reason
                        + "; evidence=" + evidence + "; correctionId=" + item.getId(),
                item.getBeforeSnapshot(), item.getAfterSnapshot(), "Loans");
        return item;
    }

    @Transactional
    public LegacyLoanCorrection decide(Long loanId, Long correctionId, boolean approve,
            LegacyLoanCorrectionDecision decision, User actor) {
        requireRole(actor, "BUSINESS_OWNER");
        Loan loan = lockedLoan(loanId, actor);
        if (approve)
            requireEligible(loan);
        if (decision == null)
            throw new IllegalArgumentException("Review decision is required");
        requireConfirmed(loan, decision.confirmReference());
        String justification = explanation(decision.reason(), 20, 2000, "Independent review explanation");
        LegacyLoanCorrection item = corrections.lock(org(actor), loanId, correctionId)
                .orElseThrow(() -> new IllegalArgumentException("Correction not found"));
        if (!"PENDING".equals(item.getStatus()))
            throw new IllegalStateException("Correction has already been reviewed");
        if (item.getMakerId().equals(actor.getId()))
            throw new AccessDeniedException("Maker cannot approve or reject their own correction");
        if (approve && !hash(json(snapshot(loan))).equals(item.getSnapshotHash()))
            throw new IllegalStateException(
                    "Loan balances, status or due date have changed since proposal. Reject this stale request and submit a fresh review");
        if (approve) {
            Map<String, Object> before = parse(item.getBeforeSnapshot());
            Map<String, Object> after = parse(item.getAfterSnapshot());
            Map<String, BigDecimal> changes = deltas(before, after);
            // No payment or cash record is created. Only an opening-position adjustment
            // may be posted, and only when subsequent financial activity is absent.
            assertNoSubsequentFinancialActivity(loan);
            if (!changes.isEmpty()) {
                JournalEntry entry = accounting.postApprovedLegacyCorrection(loan, item.getId(), changes);
                if (entry != null)
                    item.setJournalEntryId(entry.getId());
            }
            apply(loan, after);
            loans.saveAndFlush(loan);
        }
        item.setStatus(approve ? "APPROVED" : "REJECTED");
        item.setCheckerId(actor.getId());
        item.setReviewedAt(LocalDateTime.now());
        item.setReviewReason(justification);
        corrections.saveAndFlush(item);
        audit.log(loan.getOrganization(), actor,
                approve ? "LEGACY_CORRECTION_APPROVED" : "LEGACY_CORRECTION_REJECTED", "Loan",
                String.valueOf(loanId), justification + "; correctionId=" + correctionId,
                item.getBeforeSnapshot(), approve ? item.getAfterSnapshot() : item.getBeforeSnapshot(), "Loans");
        return item;
    }

    private void apply(Loan l, Map<String, Object> v) {
        BigDecimal principalPaid = n(v, "principalPaid");
        BigDecimal interestPaid = n(v, "interestPaid");
        BigDecimal interestDue = n(v, "interestOutstanding");
        BigDecimal managementPaid = n(v, "managementFeePaid");
        BigDecimal managementDue = n(v, "managementFeeOutstanding");
        BigDecimal penaltyAssessed = n(v, "penaltiesAssessed");
        BigDecimal penaltyPaid = n(v, "penaltiesPaid");
        l.setPrincipalPaid(principalPaid);
        l.setOutstandingBalance(n(v, "principalOutstanding"));
        l.setInterestPaid(interestPaid);
        l.setInterestOutstanding(interestDue);
        l.setTotalInterest(interestPaid.add(interestDue));
        l.setManagementFeePaid(managementPaid);
        l.setManagementFeeOutstanding(managementDue);
        l.setManagementFee(managementPaid.add(managementDue));
        l.setPenaltiesAssessed(penaltyAssessed);
        l.setPenaltiesPaid(penaltyPaid);
        l.setTotalPaid(n(v, "totalPaid"));
        l.setTotalRepayable(n(v, "totalRepayable"));
        // An import transcription mistake in duration is NOT a loan restructuring.
        // Recalculate maturity from the unchanged historical start date.
        Integer durationMonths = (Integer) v.get("durationMonths");
        if (durationMonths != null && (!durationMonths.equals(l.getDurationMonths())
                || !Objects.equals(date(v.get("maturityDate")), l.getMaturityDate()))) {
            l.setDurationMonths(durationMonths);
            l.setMaturityDate(date(v.get("maturityDate")));
        }
        LocalDate due = date(v.get("oldestUnpaidDueDate"));
        l.setNextDueDate(due);
        l.setNextPaymentDate(due);
        BigDecimal owed = n(v, "totalRepayable").subtract(n(v, "totalPaid"));
        int days = owed.signum() > 0 && due != null ? (int) Math.max(0, ChronoUnit.DAYS.between(due, LocalDate.now()))
                : 0;
        l.setDaysOverdue(days);
        l.setArrearsStatus(days > 0 ? Loan.ArrearsStatus.PAST_DUE : Loan.ArrearsStatus.NOT_DUE);
        // Do not silently erase an existing default, restructuring or write-off
        // decision.
        if (l.getStatus() == LoanStatus.ACTIVE || l.getStatus() == LoanStatus.OVERDUE
                || l.getStatus() == LoanStatus.PAID) {
            l.setStatus(owed.signum() == 0 ? LoanStatus.PAID : days > 0 ? LoanStatus.OVERDUE : LoanStatus.ACTIVE);
        }
    }

    private Map<String, Object> requested(Map<String, Object> before, LegacyLoanCorrectionRequest r, Loan l) {
        if (r == null)
            throw new IllegalArgumentException("Correction payload is required");
        Map<String, Object> v = new LinkedHashMap<>(before);
        set(v, "principalPaid", r.principalPaid());
        set(v, "interestPaid", r.interestPaid());
        set(v, "interestOutstanding", r.interestOutstanding());
        set(v, "managementFeePaid", r.managementFeePaid());
        set(v, "managementFeeOutstanding", r.managementFeeOutstanding());
        set(v, "penaltiesAssessed", r.penaltiesAssessed());
        set(v, "penaltiesPaid", r.penaltiesPaid());
        if (r.durationMonths() != null) {
            if (!Loan.isValidLoanDuration(r.durationMonths()))
                throw new IllegalArgumentException("Verified imported loan duration must be between "
                        + Loan.MIN_LOAN_DURATION_MONTHS + " and " + Loan.MAX_LOAN_DURATION_MONTHS + " months");
            if (l.getStartDate() == null)
                throw new IllegalStateException("Loan has no original start date; duration cannot be repaired here");
            if (l.getStatus() == LoanStatus.RESTRUCTURED)
                throw new IllegalStateException(
                        "A restructured loan needs a contractual amendment review, not an imported-term transcription repair");
            v.put("durationMonths", r.durationMonths());
            v.put("maturityDate", l.getStartDate().plusMonths(r.durationMonths()).toString());
        }
        if (r.oldestUnpaidDueDate() != null)
            v.put("oldestUnpaidDueDate", r.oldestUnpaidDueDate().toString());
        boolean changedFinancial = false;
        for (String f : List.of("principalPaid", "interestPaid", "interestOutstanding", "managementFeePaid",
                "managementFeeOutstanding", "penaltiesAssessed", "penaltiesPaid")) {
            if (n(before, f).compareTo(n(v, f)) != 0)
                changedFinancial = true;
        }
        LocalDate proposedDue = date(v.get("oldestUnpaidDueDate"));
        if (proposedDue != null && l.getStartDate() != null && proposedDue.isBefore(l.getStartDate()))
            throw new IllegalArgumentException("Oldest unpaid due date cannot precede disbursement/start date");
        if (!changedFinancial)
            return v; // A due-date-only correction must NEVER rewrite monetary figures.
        BigDecimal principal = n(v, "principalPaid");
        BigDecimal amount = n(v, "amount");
        if (principal.compareTo(amount) > 0)
            throw new IllegalArgumentException("Principal paid exceeds original principal");
        if (n(v, "penaltiesPaid").compareTo(n(v, "penaltiesAssessed")) > 0)
            throw new IllegalArgumentException("Penalty payments exceed penalties assessed");
        v.put("principalOutstanding", amount.subtract(principal).setScale(2));
        BigDecimal paid = principal.add(n(v, "interestPaid")).add(n(v, "managementFeePaid"))
                .add(n(v, "penaltiesPaid")).add(n(v, "extensionFeePaid"));
        BigDecimal owed = n(v, "principalOutstanding").add(n(v, "interestOutstanding"))
                .add(n(v, "managementFeeOutstanding")).add(n(v, "extensionFeeOutstanding"))
                .add(n(v, "penaltiesAssessed").subtract(n(v, "penaltiesPaid")));
        v.put("totalPaid", paid.setScale(2));
        v.put("totalRepayable", paid.add(owed).setScale(2));
        if (owed.signum() > 0 && date(v.get("oldestUnpaidDueDate")) == null)
            throw new IllegalArgumentException(
                    "Oldest unpaid contractual due date is required for an outstanding legacy loan");
        // Never accept a date before the loan even started.
        LocalDate due = date(v.get("oldestUnpaidDueDate"));
        if (due != null && l.getStartDate() != null && due.isBefore(l.getStartDate()))
            throw new IllegalArgumentException("Oldest unpaid due date cannot precede disbursement/start date");
        return v;
    }

    private void set(Map<String, Object> v, String key, BigDecimal value) {
        if (value != null) {
            if (value.scale() > 2 || value.scale() < 0 || value.signum() < 0
                    || value.compareTo(new BigDecimal("99999999999999999.99")) > 0)
                throw new IllegalArgumentException(key + " must be a nonnegative amount with at most 2 decimal places");
            v.put(key, value.setScale(2));
        }
    }

    private Map<String, Object> snapshot(Loan l) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("amount", money(l.getAmount()));
        v.put("principalPaid", money(l.getPrincipalPaidDecimal()));
        v.put("principalOutstanding", money(l.getOutstandingBalanceDecimal()));
        v.put("interestPaid", money(l.getInterestPaidDecimal()));
        v.put("interestOutstanding", money(l.getInterestOutstandingDecimal()));
        v.put("managementFeePaid", money(l.getManagementFeePaidDecimal()));
        v.put("managementFeeOutstanding", money(l.getManagementFeeOutstandingDecimal()));
        v.put("penaltiesAssessed", money(l.getPenaltiesAssessedDecimal()));
        v.put("penaltiesPaid", money(l.getPenaltiesPaidDecimal()));
        v.put("extensionFeePaid", money(l.getExtensionFeePaidDecimal()));
        v.put("extensionFeeOutstanding", money(l.getExtensionFeeOutstandingDecimal()));
        v.put("totalPaid", money(l.getTotalPaidDecimal()));
        v.put("totalRepayable", money(l.getTotalRepayableDecimal()));
        v.put("oldestUnpaidDueDate", l.getNextDueDate() == null ? null : l.getNextDueDate().toString());
        v.put("durationMonths", l.getDurationMonths());
        v.put("maturityDate", l.getMaturityDate() == null ? null : l.getMaturityDate().toString());
        v.put("status", l.getStatus() == null ? null : l.getStatus().name());
        v.put("businessOwnerOnly", Boolean.TRUE.equals(l.getBusinessOwnerOnly()));
        return v;
    }

    private Map<String, BigDecimal> deltas(Map<String, Object> before, Map<String, Object> after) {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        diff(out, "1100", before, after, "principalOutstanding");
        diff(out, "1150", before, after, "interestOutstanding");
        diff(out, "1160", before, after, "managementFeeOutstanding");
        // penalties GL is NET assessed minus paid.
        BigDecimal penaltyBefore = n(before, "penaltiesAssessed").subtract(n(before, "penaltiesPaid"));
        BigDecimal penaltyAfter = n(after, "penaltiesAssessed").subtract(n(after, "penaltiesPaid"));
        BigDecimal penaltyDelta = penaltyAfter.subtract(penaltyBefore);
        if (penaltyDelta.signum() != 0)
            out.put("1175", penaltyDelta);
        return out;
    }

    private void diff(Map<String, BigDecimal> out, String account, Map<String, Object> b, Map<String, Object> a,
            String key) {
        BigDecimal delta = n(a, key).subtract(n(b, key));
        if (delta.signum() != 0)
            out.put(account, delta);
    }

    private BigDecimal n(Map<String, Object> m, String field) {
        Object v = m.get(field);
        if (v == null)
            return ZERO;
        try {
            return new BigDecimal(v.toString()).setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException | NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid money value " + field);
        }
    }

    private BigDecimal money(BigDecimal v) {
        return (v == null ? ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    private LocalDate date(Object v) {
        return v == null || v.toString().isBlank() ? null : LocalDate.parse(v.toString());
    }

    private String json(Map<String, Object> m) {
        try {
            return mapper.writeValueAsString(m);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot store correction snapshot", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String text) {
        try {
            return mapper.readValue(text, LinkedHashMap.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Invalid persisted correction snapshot", ex);
        }
    }

    private String hash(String s) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception ex) {
            throw new IllegalStateException("Hashing unavailable", ex);
        }
    }

    private Loan ownedLoan(Long loanId, User actor) {
        Loan l = loans.findById(loanId).orElseThrow(() -> new IllegalArgumentException("Loan not found"));
        checkOrg(l, actor);
        return l;
    }

    private Loan lockedLoan(Long loanId, User actor) {
        Loan l = loans.findByIdForUpdate(loanId).orElseThrow(() -> new IllegalArgumentException("Loan not found"));
        checkOrg(l, actor);
        return l;
    }

    private void checkOrg(Loan l, User actor) {
        if (l.getOrganization() == null || !org(actor).equals(l.getOrganization().getId()))
            throw new AccessDeniedException("Loan belongs to another organization");
        if (Boolean.TRUE.equals(l.getBusinessOwnerOnly()))
            requireRole(actor, "BUSINESS_OWNER");
    }

    private void requireProposer(Loan loan, User actor) {
        if (Boolean.TRUE.equals(loan.getBusinessOwnerOnly())) {
            // Never expose owner-only loans to non-owners, even as correction requests.
            // A second, distinct Business Owner is required to review this exception.
            requireRole(actor, "BUSINESS_OWNER");
        } else {
            requireRole(actor, "ADMIN", "MANAGER", "ACCOUNTANT", "LOAN_OFFICER", "CREDIT_ANALYST");
        }
    }

    @Transactional(readOnly = true)
    public List<LegacyLoanCorrection> pendingForBusinessOwner(User actor) {
        requireRole(actor, "BUSINESS_OWNER");
        return corrections.findByOrganizationIdAndStatusOrderByCreatedAtAsc(org(actor), "PENDING");
    }

    private Long org(User u) {
        if (u == null || u.getOrganization() == null || u.getOrganization().getId() == null)
            throw new AccessDeniedException("Organization required");
        return u.getOrganization().getId();
    }

    private void requireEligible(Loan l) {
        if (!Boolean.TRUE.equals(l.getImported()))
            throw new IllegalStateException(
                    "Use the contractual amendment workflow for loans created within the system");
        if (l.getDeletedAt() != null || l.getStatus() == LoanStatus.WRITTEN_OFF
                || l.getStatus() == LoanStatus.CANCELLED)
            throw new IllegalStateException(
                    "A deleted, written-off or cancelled loan requires a separate controlled workflow");
    }

    private void requireRole(User u, String... roles) {
        String role = u == null || u.getRole() == null ? "" : u.getRole().getName();
        if (Arrays.stream(roles).noneMatch(r -> r.equalsIgnoreCase(role)))
            throw new AccessDeniedException("Insufficient role for legacy loan correction");
    }

    private void requireConfirmed(Loan loan, String typed) {
        if (typed == null || !typed.trim().equals(loan.getReferenceNumber()))
            throw new IllegalArgumentException("Type the exact loan reference to confirm");
    }

    private String explanation(String val, int min, int max, String label) {
        String s = val == null ? "" : val.trim();
        if (s.length() < min || s.length() > max)
            throw new IllegalArgumentException(label + " must be " + min + " to " + max + " characters");
        return s;
    }

    private void assertNoSubsequentFinancialActivity(Loan l) {
        // No destructive reversal. If new transactions or contractual schedules exist,
        // the simple opening-position repair must stop for a reconciled specialist
        // review.
        Number transactions = (Number) em
                .createNativeQuery("SELECT count(*) FROM payment_transactions WHERE loan_id=:id")
                .setParameter("id", l.getId()).getSingleResult();
        Number payments = (Number) em.createNativeQuery("SELECT count(*) FROM payments WHERE loan_id=:id")
                .setParameter("id", l.getId()).getSingleResult();
        Number schedules = (Number) em.createNativeQuery("SELECT count(*) FROM payment_schedules WHERE loan_id=:id")
                .setParameter("id", l.getId()).getSingleResult();
        Number journals = (Number) em.createNativeQuery("""
                SELECT count(*) FROM journal_entries WHERE organization_id=:org
                AND (reference=:ref OR source_id LIKE :sourcePrefix)
                AND source_type NOT IN ('HISTORICAL_LOAN_OPENING','LEGACY_LOAN_OPENING',
                  'LEGACY_LOAN_RECONCILIATION','LEGACY_LOAN_OPENING_DATE_REPAIR',
                  'LEGACY_IMPORT_DATA_CORRECTION')
                """).setParameter("org", l.getOrganization().getId())
                .setParameter("ref", l.getReferenceNumber())
                .setParameter("sourcePrefix", "LOAN:" + l.getId() + ":%")
                .getSingleResult();
        if (transactions.longValue() > 0 || payments.longValue() > 0 || schedules.longValue() > 0
                || journals.longValue() > 0)
            throw new IllegalStateException("This loan has subsequent payments, schedules or accounting activity. "
                    + "The simple import-data repair is blocked: use a specialist reconciled correction "
                    + "that preserves all intervening transactions; no data was changed");
    }
}
