package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@Service
@RequiredArgsConstructor
@Slf4j
public class LoanAdministrationService {

    private static final int RECYCLE_DAYS = 30;
    private static final int MIN_REASON_LENGTH = 20;
    private static final int MAX_REASON_LENGTH = 1000;

    /*
     * Every current direct FK from a loan child table is represented here.
     * The names are constants, never request input.
     */
    private static final List<String> DIRECT_LOAN_CHILD_TABLES = List.of(
            "credit_bureau_disputes",
            "credit_bureau_submission_records",
            "loan_comments",
            "loan_approvals",
            "loan_restructuring_history",
            "esignature_requests",
            "collection_cases",
            "guarantors",
            "collaterals",
            "lending_feature_records",
            "payment_transactions",
            "payment_schedules",
            "payments"
    );

    private final LoanRepository loanRepository;
    private final EntityManager entityManager;
    private final AuditService auditService;

    @Transactional
    public void deleteWithConfirmation(
            Long loanId,
            String confirmation,
            User actor) {
        deleteWithConfirmation(
                loanId,
                confirmation,
                "Business Owner recycled the loan through the controlled recycle-bin workflow.",
                actor);
    }

    @Transactional
    public void deleteWithConfirmation(
            Long loanId,
            String confirmation,
            String reason,
            User actor) {

        requireBusinessOwner(actor);

        if (loanId == null || loanId <= 0) {
            throw new IllegalArgumentException("Loan ID is required");
        }

        Long organizationId = organizationId(actor);

        Loan loan = loanRepository.findByIdForUpdate(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Loan not found"));

        if (loan.getOrganization() == null
                || !organizationId.equals(loan.getOrganization().getId())) {
            throw new AccessDeniedException("Loan does not belong to your organization");
        }

        String reference = requireReference(loan);
        requireConfirmation(confirmation, reference);
        String normalizedReason = requireReason(reason);

        if (loan.getDeletedAt() != null) {
            throw new IllegalStateException("Loan is already in the recycle bin");
        }

        /*
         * Do not change LoanStatus or any financial amount. The recycle state
         * is orthogonal to principal, interest, management fee, application
         * fee, penalty, repayment schedule and payment allocation.
         */
        ensureNoPendingPaymentTransaction(loanId);
        ensureNoFinancialOrProtectedHistory(organizationId, loanId, reference);

        LocalDateTime now = LocalDateTime.now();

        loan.setDeletedAt(now);
        loan.setDeletionReason(normalizedReason);
        loan.setDeletedBy(actor.getId());
        loan.setPurgeAfter(now.plusDays(RECYCLE_DAYS));

        /*
         * @SQLRestriction immediately removes this loan from ordinary JPA
         * queries after commit. No financial value is recalculated.
         */
        loanRepository.saveAndFlush(loan);

        /*
         * This is only an operational recycle-bin action. Financial activity
         * was rejected above; no journal rows are hidden or modified here.
         * Tombstones remain for audit/history only and never suppress reports.
         */
        upsertTombstone(
                organizationId,
                loanId,
                reference,
                now);

        auditService.log(
                loan.getOrganization(),
                actor,
                "LOAN_RECYCLED",
                "LOAN",
                String.valueOf(loanId),
                "Loan " + reference
                        + " moved to the operational recycle bin for " + RECYCLE_DAYS
                        + " days. Posted accounting records are retained. Reason: "
                        + normalizedReason,
                null,
                null,
                "Loan Management");
    }

    @Transactional(readOnly = true)
    public List<LoanRecycleBinItem> listRecycleBin(User actor) {
        requireBusinessOwner(actor);
        Long organizationId = organizationId(actor);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT id, reference_number, status, deleted_at,
                       purge_after, deletion_reason, deleted_by
                FROM loans
                WHERE organization_id = :organizationId
                  AND deleted_at IS NOT NULL
                ORDER BY deleted_at DESC, id DESC
                """)
                .setParameter("organizationId", organizationId)
                .getResultList();

        List<LoanRecycleBinItem> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(new LoanRecycleBinItem(
                    ((Number) row[0]).longValue(),
                    row[1] == null ? null : String.valueOf(row[1]),
                    row[2] == null ? null : String.valueOf(row[2]),
                    toLocalDateTime(row[3]),
                    toLocalDateTime(row[4]),
                    row[5] == null ? null : String.valueOf(row[5]),
                    row[6] == null ? null : ((Number) row[6]).longValue()));
        }
        return result;
    }

    @Transactional
    public void restoreWithConfirmation(
            Long loanId,
            String confirmation,
            User actor) {

        requireBusinessOwner(actor);

        if (loanId == null || loanId <= 0) {
            throw new IllegalArgumentException("Loan ID is required");
        }

        Long organizationId = organizationId(actor);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT id, reference_number, purge_after
                FROM loans
                WHERE id = :loanId
                  AND organization_id = :organizationId
                  AND deleted_at IS NOT NULL
                FOR UPDATE
                """)
                .setParameter("loanId", loanId)
                .setParameter("organizationId", organizationId)
                .getResultList();

        if (rows.isEmpty()) {
            throw new IllegalArgumentException(
                    "Recycled loan not found or it is no longer restorable");
        }

        Object[] row = rows.get(0);
        String reference = row[1] == null ? null : String.valueOf(row[1]);
        requireConfirmation(confirmation, reference);

        LocalDateTime purgeAfter = toLocalDateTime(row[2]);
        if (purgeAfter != null && !purgeAfter.isAfter(LocalDateTime.now())) {
            throw new IllegalStateException(
                    "The 30-day recycle period has expired; the loan is no longer restorable");
        }

        entityManager.createNativeQuery("""
                UPDATE loans
                SET deleted_at = NULL,
                    deletion_reason = NULL,
                    deleted_by = NULL,
                    purge_after = NULL
                WHERE id = :loanId
                  AND organization_id = :organizationId
                  AND deleted_at IS NOT NULL
                """)
                .setParameter("loanId", loanId)
                .setParameter("organizationId", organizationId)
                .executeUpdate();

        entityManager.createNativeQuery("""
                UPDATE journal_entries
                SET hidden_loan_id = NULL
                WHERE organization_id = :organizationId
                  AND hidden_loan_id = :loanId
                """)
                .setParameter("organizationId", organizationId)
                .setParameter("loanId", loanId)
                .executeUpdate();

        entityManager.createNativeQuery("""
                DELETE FROM loan_deletion_tombstones
                WHERE organization_id = :organizationId
                  AND loan_id = :loanId
                """)
                .setParameter("organizationId", organizationId)
                .setParameter("loanId", loanId)
                .executeUpdate();

        auditService.log(
                actor.getOrganization(),
                actor,
                "LOAN_RESTORED",
                "LOAN",
                String.valueOf(loanId),
                "Restored loan " + reference + " from the 30-day recycle bin",
                null,
                null,
                "Loan Management");
    }

    /**
     * Daily automatic purge. A batch limit prevents a large recycle bin from
     * creating one unbounded transaction.
     */
    @Scheduled(
            cron = "${app.loan.recycle-bin.purge-cron:0 15 2 * * *}",
            zone = "${app.loan.recycle-bin.purge-zone:Africa/Kigali}")
    @Transactional
    public void purgeExpiredRecycledLoans() {

        @SuppressWarnings("unchecked")
        List<Number> ids = entityManager.createNativeQuery("""
                SELECT id
                FROM loans
                WHERE deleted_at IS NOT NULL
                  AND purge_after IS NOT NULL
                  AND purge_after <= CURRENT_TIMESTAMP
                ORDER BY purge_after ASC, id ASC
                LIMIT 100
                """)
                .getResultList();

        for (Number idValue : ids) {
            purgeExpiredLoan(idValue.longValue());
        }
    }

    private void purgeExpiredLoan(Long loanId) {

        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT id, organization_id, reference_number
                FROM loans
                WHERE id = :loanId
                  AND deleted_at IS NOT NULL
                  AND purge_after <= CURRENT_TIMESTAMP
                FOR UPDATE
                """)
                .setParameter("loanId", loanId)
                .getResultList();

        if (rows.isEmpty()) {
            return;
        }

        Object[] row = rows.get(0);
        Long organizationId = ((Number) row[1]).longValue();
        String reference = row[2] == null ? String.valueOf(loanId) : String.valueOf(row[2]);

        /*
         * Fail safe at purge as well as at recycle time. If any financial
         * activity appeared after recycling (legacy writer, webhook race, or
         * manual repair), preserve the loan and all children for investigation.
         * Clearing purge_after prevents a daily destructive retry and keeps
         * the record restorable by the Business Owner.
         */
        if (hasFinancialOrProtectedHistory(organizationId, loanId, reference)) {
            entityManager.createNativeQuery("""
                    UPDATE loans
                    SET purge_after = NULL,
                        deletion_reason = CONCAT(
                            COALESCE(deletion_reason, ''),
                            CASE WHEN COALESCE(deletion_reason, '') = '' THEN '' ELSE E'\n' END,
                            '[PURGE BLOCKED] Financial ledger/payment activity exists; restore or resolve through an audited accounting workflow.'
                        )
                    WHERE id = :loanId
                      AND organization_id = :organizationId
                      AND deleted_at IS NOT NULL
                    """)
                    .setParameter("loanId", loanId)
                    .setParameter("organizationId", organizationId)
                    .executeUpdate();
            log.error("Permanent purge blocked for recycled loan {} (id={}): financial activity was detected; record retained for manual resolution",
                    reference, loanId);
            auditService.log(
                    null,
                    null,
                    "LOAN_PURGE_BLOCKED_FINANCIAL_HISTORY",
                    "LOAN",
                    String.valueOf(loanId),
                    "Permanent purge blocked for loan " + reference
                            + " because financial journal/payment activity exists. Loan retained for audited resolution.",
                    null,
                    null,
                    "Loan Management");
            return;
        }

        /*
         * Credit-bureau correction records can self-reference one another.
         * Break only that internal correction pointer before deleting the
         * expired operational records.
         */
        entityManager.createNativeQuery("""
                UPDATE credit_bureau_submission_records
                SET correction_of_record_id = NULL
                WHERE loan_id = :loanId
                """)
                .setParameter("loanId", loanId)
                .executeUpdate();

        /*
         * These are operational records only. Financial-history checks above
         * ensure a loan with any posted ledger/payment activity is never purged.
         * Tombstones preserve the deletion event but never hide ledger entries.
         */
        for (String table : DIRECT_LOAN_CHILD_TABLES) {
            deleteLoanChildren(table, loanId);
        }

        entityManager.createNativeQuery("""
                DELETE FROM loans
                WHERE id = :loanId
                  AND organization_id = :organizationId
                  AND deleted_at IS NOT NULL
                """)
                .setParameter("loanId", loanId)
                .setParameter("organizationId", organizationId)
                .executeUpdate();

        entityManager.createNativeQuery("""
                UPDATE loan_deletion_tombstones
                SET purged_at = CURRENT_TIMESTAMP
                WHERE organization_id = :organizationId
                  AND loan_id = :loanId
                """)
                .setParameter("organizationId", organizationId)
                .setParameter("loanId", loanId)
                .executeUpdate();

        auditService.log(
                null,
                null,
                "LOAN_PERMANENTLY_PURGED",
                "LOAN",
                String.valueOf(loanId),
                "Recycle-bin loan " + reference
                        + " permanently purged after " + RECYCLE_DAYS
                        + " days. Immutable accounting/audit tombstone retained.",
                null,
                null,
                "Loan Management");

        log.info("Recycle-bin loan {} permanently purged", reference);
    }

   private void deleteLoanChildren(String table, Long loanId) {
    entityManager.createNativeQuery(
            "DELETE FROM \"" + table + "\" WHERE loan_id = :loanId")
            .setParameter("loanId", loanId)
            .executeUpdate();
}

    /**
     * Detect financial activity and protected legal/regulatory evidence by
     * multiple independent signals. This is intentionally conservative: even
     * reversed journals remain auditable, signed agreements remain evidence,
     * and submitted bureau records/settlements must not be silently erased.
     */
    private void ensureNoFinancialOrProtectedHistory(
            Long organizationId,
            Long loanId,
            String reference) {
        if (hasFinancialOrProtectedHistory(organizationId, loanId, reference)) {
            throw new IllegalStateException(
                    "This loan has financial, contractual, settlement, or regulatory history and cannot be deleted through the recycle-bin workflow. "
                            + "Use the approved audited accounting/retention workflow; posted records and signed evidence must remain available.");
        }
    }

    private boolean hasFinancialOrProtectedHistory(
            Long organizationId,
            Long loanId,
            String reference) {

        Number journalCount = (Number) entityManager.createNativeQuery("""
                SELECT COUNT(*)
                FROM journal_entries je
                WHERE je.organization_id = :organizationId
                  AND (
                        je.reference = :reference
                        OR (
                            je.source_type IN (
                                'LOAN_DISBURSEMENT', 'LOAN_PAYMENT',
                                'LOAN_EXTENSION_FEE', 'LOAN_EXTENSION_FEE_COLLECTION',
                                'PENALTY_ACCRUAL', 'INTEREST_ACCRUAL',
                                'MANAGEMENT_FEE_ACCRUAL', 'SCHEDULED_INTEREST_ACCRUAL',
                                'SCHEDULED_MANAGEMENT_FEE_ACCRUAL',
                                'CONTRACTUAL_MONTHLY_INTEREST_ACCRUAL',
                                'CONTRACTUAL_MONTHLY_MANAGEMENT_FEE_ACCRUAL',
                                'HISTORICAL_LOAN_OPENING', 'LEGACY_LOAN_OPENING',
                                'LEGACY_LOAN_RECONCILIATION',
                                'LEGACY_LOAN_OPENING_DATE_REPAIR', 'WRITE_OFF'
                            )
                            AND regexp_replace(trim(COALESCE(je.source_id, '')), '^LOAN:', '') = :loanIdText
                        )
                        OR (
                            je.source_type IN (
                                'PAYMENT_RECEIVED', 'REFUND_PAYMENT',
                                'PAYMENT_REFUND', 'PAYMENT_REVERSAL'
                            )
                            AND je.source_id IN (
                                SELECT CAST(p.id AS TEXT)
                                FROM payments p
                                WHERE p.loan_id = :loanId
                            )
                        )
                        OR (
                            je.source_type = 'APPLICATION_FEE_INCOME_RECLASSIFICATION'
                            AND je.source_id = :applicationFeeSourceId
                        )
                  )
                """)
                .setParameter("organizationId", organizationId)
                .setParameter("loanId", loanId)
                .setParameter("loanIdText", String.valueOf(loanId))
                .setParameter("reference", reference)
                .setParameter("applicationFeeSourceId", loanId + ":APPLICATION_FEE_INCOME")
                .getSingleResult();
        if (journalCount != null && journalCount.longValue() > 0L) {
            return true;
        }

        Number postedPayments = (Number) entityManager.createNativeQuery("""
                SELECT COUNT(*)
                FROM payments p
                WHERE p.loan_id = :loanId
                  AND (
                        COALESCE(p.paid, FALSE) = TRUE
                        OR COALESCE(p.amount_paid, 0) > 0
                        OR COALESCE(p.status, '') IN ('COMPLETED', 'PARTIALLY_PAID')
                  )
                """)
                .setParameter("loanId", loanId)
                .getSingleResult();
        if (postedPayments != null && postedPayments.longValue() > 0L) {
            return true;
        }

        Number postedTransactions = (Number) entityManager.createNativeQuery("""
                SELECT COUNT(*)
                FROM payment_transactions pt
                WHERE pt.loan_id = :loanId
                  AND COALESCE(pt.status, '') IN ('POSTED', 'REVERSED')
                """)
                .setParameter("loanId", loanId)
                .getSingleResult();
        if (postedTransactions != null && postedTransactions.longValue() > 0L) {
            return true;
        }

        Number financialAmounts = (Number) entityManager.createNativeQuery("""
                SELECT COUNT(*)
                FROM loans l
                WHERE l.id = :loanId
                  AND l.organization_id = :organizationId
                  AND (
                        COALESCE(l.disbursed_amount, 0) > 0
                        OR COALESCE(l.net_disbursed_amount, 0) > 0
                        OR COALESCE(l.application_fee_paid, 0) > 0
                        OR COALESCE(l.total_paid, 0) > 0
                        OR COALESCE(l.principal_paid, 0) > 0
                  )
                """)
                .setParameter("loanId", loanId)
                .setParameter("organizationId", organizationId)
                .getSingleResult();
        if (financialAmounts != null && financialAmounts.longValue() > 0L) {
            return true;
        }

        // Bank statement settlement evidence is financial evidence even if
        // the corresponding payment has not yet reached POSTED status.
        Number settlements = (Number) entityManager.createNativeQuery("""
                SELECT COUNT(*)
                FROM payment_settlements ps
                WHERE ps.internal_payment_reference IN (
                    SELECT p.payment_reference
                    FROM payments p
                    WHERE p.loan_id = :loanId
                )
                """)
                .setParameter("loanId", loanId)
                .getSingleResult();
        if (settlements != null && settlements.longValue() > 0L) {
            return true;
        }

        // Preserve contractual and regulatory history beyond the operational
        // recycle window. These tables intentionally do not get purged here.
        Number signedAgreements = (Number) entityManager.createNativeQuery("""
                SELECT COUNT(*)
                FROM esignature_requests es
                WHERE es.loan_id = :loanId
                  AND es.status = 'SIGNED'
                """)
                .setParameter("loanId", loanId)
                .getSingleResult();
        if (signedAgreements != null && signedAgreements.longValue() > 0L) {
            return true;
        }

        Number creditBureauHistory = (Number) entityManager.createNativeQuery("""
                SELECT
                    (SELECT COUNT(*) FROM credit_bureau_submission_records cbsr WHERE cbsr.loan_id = :loanId)
                    + (SELECT COUNT(*) FROM credit_bureau_disputes cbd WHERE cbd.loan_id = :loanId)
                """)
                .setParameter("loanId", loanId)
                .getSingleResult();
        if (creditBureauHistory != null && creditBureauHistory.longValue() > 0L) {
            return true;
        }

        Number restructuringHistory = (Number) entityManager.createNativeQuery("""
                SELECT COUNT(*)
                FROM loan_restructuring_history lrh
                WHERE lrh.loan_id = :loanId
                """)
                .setParameter("loanId", loanId)
                .getSingleResult();
        return restructuringHistory != null && restructuringHistory.longValue() > 0L;
    }

    private void ensureNoPendingPaymentTransaction(Long loanId) {
        Number count = (Number) entityManager.createNativeQuery("""
                SELECT COUNT(*)
                FROM payment_transactions
                WHERE loan_id = :loanId
                  AND COALESCE(reversed, FALSE) = FALSE
                  AND status IN ('INITIATED', 'PENDING')
                """)
                .setParameter("loanId", loanId)
                .getSingleResult();

        if (count.longValue() > 0L) {
            throw new IllegalStateException(
                    "The loan cannot be recycled while a payment transaction is still pending. "
                            + "Complete or reverse the payment first.");
        }
    }

    private void upsertTombstone(
            Long organizationId,
            Long loanId,
            String reference,
            LocalDateTime deletedAt) {

        entityManager.createNativeQuery("""
                INSERT INTO loan_deletion_tombstones
                    (organization_id, loan_id, reference_number, deleted_at)
                VALUES
                    (:organizationId, :loanId, :reference, :deletedAt)
                ON CONFLICT (organization_id, loan_id)
                DO UPDATE SET
                    reference_number = EXCLUDED.reference_number,
                    deleted_at = EXCLUDED.deleted_at,
                    purged_at = NULL
                """)
                .setParameter("organizationId", organizationId)
                .setParameter("loanId", loanId)
                .setParameter("reference", reference)
                .setParameter("deletedAt", deletedAt)
                .executeUpdate();
    }

    private Long organizationId(User actor) {
        if (actor.getOrganization() == null
                || actor.getOrganization().getId() == null) {
            throw new IllegalArgumentException("Organization is required");
        }
        return actor.getOrganization().getId();
    }

    private String requireReference(Loan loan) {
        if (loan.getReferenceNumber() == null
                || loan.getReferenceNumber().isBlank()) {
            throw new IllegalStateException(
                    "The loan has no valid reference number and cannot be safely recycled");
        }
        return loan.getReferenceNumber().trim();
    }

    private void requireConfirmation(String confirmation, String reference) {
        String expected = "sudo " + reference;
        if (!expected.equals(confirmation)) {
            throw new IllegalArgumentException(
                    "High-risk confirmation failed. Type exactly: " + expected);
        }
    }

    private String requireReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();

        if (normalized.length() < MIN_REASON_LENGTH) {
            throw new IllegalArgumentException(
                    "A deletion reason of at least " + MIN_REASON_LENGTH + " characters is required");
        }

        if (normalized.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException(
                    "Deletion reason cannot exceed " + MAX_REASON_LENGTH + " characters");
        }

        return normalized;
    }

    private void requireBusinessOwner(User actor) {
        if (actor == null
                || actor.getRole() == null
                || actor.getRole().getName() == null
                || !"BUSINESS_OWNER".equalsIgnoreCase(actor.getRole().getName())) {
            throw new AccessDeniedException(
                    "Only the BUSINESS_OWNER can perform this operation");
        }
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        return LocalDateTime.parse(String.valueOf(value).replace(' ', 'T'));
    }

    public record LoanRecycleBinItem(
            Long id,
            String referenceNumber,
            String status,
            LocalDateTime deletedAt,
            LocalDateTime purgeAfter,
            String deletionReason,
            Long deletedBy) {
    }
}
