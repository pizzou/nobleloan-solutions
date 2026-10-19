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
            "payments");

    private final LoanRepository loanRepository;
    private final EntityManager entityManager;
    private final AuditService auditService;

    /**
     * Change a loan's reporting visibility without changing its contractual or
     * accounting amounts. This is deliberately separate from recycle-bin logic:
     * visibility changes must never set recycle_loan_id or alter deletion fields.
     */
    @Transactional
    public Loan changeReportingVisibility(Long loanId, boolean businessOwnerOnly, User actor) {
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
        if (loan.getDeletedAt() != null) {
            throw new IllegalStateException("A recycled loan cannot have its reporting visibility changed");
        }

        boolean previous = Boolean.TRUE.equals(loan.getBusinessOwnerOnly());
        if (previous == businessOwnerOnly) {
            return loan;
        }

        updateLoanAccountingVisibility(organizationId, loan.getId(), loan.getReferenceNumber(), businessOwnerOnly);
        loan.setBusinessOwnerOnly(businessOwnerOnly);
        Loan saved = loanRepository.saveAndFlush(loan);
        auditService.log(
                loan.getOrganization(), actor, "LOAN_REPORTING_VISIBILITY_CHANGED", "Loan",
                String.valueOf(loan.getId()),
                "Business Owner changed loan reporting visibility for " + loan.getReferenceNumber(),
                previous ? "BUSINESS_OWNER_ONLY" : "NORMAL",
                businessOwnerOnly ? "BUSINESS_OWNER_ONLY" : "NORMAL",
                "Loans");
        return saved;
    }

    private void updateLoanAccountingVisibility(
            Long organizationId, Long loanId, String reference, boolean businessOwnerOnly) {
        String loanIdText = String.valueOf(loanId);
        entityManager
                .createNativeQuery(
                        """
                                WITH related_entries AS (
                                    SELECT je.id
                                    FROM journal_entries je
                                    WHERE je.organization_id = :organizationId
                                      AND (
                                        je.reference = :reference
                                        OR (je.source_type IN (
                                            'LOAN_DISBURSEMENT', 'LOAN_PAYMENT', 'LOAN_EXTENSION_FEE',
                                            'LOAN_EXTENSION_FEE_COLLECTION', 'PENALTY_ACCRUAL',
                                            'INTEREST_ACCRUAL', 'MANAGEMENT_FEE_ACCRUAL',
                                            'SCHEDULED_INTEREST_ACCRUAL', 'SCHEDULED_MANAGEMENT_FEE_ACCRUAL',
                                            'CONTRACTUAL_MONTHLY_INTEREST_ACCRUAL',
                                            'CONTRACTUAL_MONTHLY_MANAGEMENT_FEE_ACCRUAL',
                                            'HISTORICAL_LOAN_OPENING', 'LEGACY_LOAN_OPENING',
                                            'LEGACY_LOAN_RECONCILIATION', 'LEGACY_LOAN_OPENING_DATE_REPAIR',
                                            'WRITE_OFF', 'APPLICATION_FEE_INCOME_RECLASSIFICATION')
                                          AND regexp_replace(trim(COALESCE(je.source_id, '')), '^LOAN:', '')
                                              ~ ('^' || :loanIdText || '($|[-:])'))
                                        OR (je.source_type IN ('PAYMENT_RECEIVED', 'REFUND_PAYMENT',
                                            'PAYMENT_REFUND', 'PAYMENT_REVERSAL', 'OVERPAYMENT_REFUND_PAYABLE')
                                          AND je.source_id IN (SELECT p.id::text FROM payments p WHERE p.loan_id = :loanId))
                                        OR (je.source_type IN ('SCHEDULED_INTEREST_ACCRUAL', 'SCHEDULED_MANAGEMENT_FEE_ACCRUAL')
                                          AND je.source_id IN (SELECT 'PAYMENT-' || p.id::text FROM payments p WHERE p.loan_id = :loanId))
                                        OR (je.source_type = 'REVERSAL' AND je.source_id IN (
                                            SELECT original.id::text FROM journal_entries original
                                            WHERE original.organization_id = :organizationId
                                              AND (original.reference = :reference
                                                OR regexp_replace(trim(COALESCE(original.source_id, '')), '^LOAN:', '')
                                                   ~ ('^' || :loanIdText || '($|[-:])')
                                                OR original.source_id IN (SELECT p.id::text FROM payments p WHERE p.loan_id = :loanId))))
                                      )
                                )
                                UPDATE journal_entries je
                                SET business_owner_only = :businessOwnerOnly
                                WHERE je.id IN (SELECT id FROM related_entries)
                                  AND je.recycle_loan_id IS NULL
                                """)
                .setParameter("organizationId", organizationId)
                .setParameter("loanId", loanId)
                .setParameter("loanIdText", loanIdText)
                .setParameter("reference", reference)
                .setParameter("businessOwnerOnly", businessOwnerOnly)
                .executeUpdate();

        entityManager.createNativeQuery("""
                UPDATE payment_settlements ps
                SET business_owner_only = :businessOwnerOnly
                WHERE ps.organization_id = :organizationId
                  AND ps.internal_payment_reference IN (
                    SELECT p.payment_reference FROM payments p WHERE p.loan_id = :loanId)
                  AND ps.recycle_loan_id IS NULL
                """)
                .setParameter("organizationId", organizationId)
                .setParameter("loanId", loanId)
                .setParameter("businessOwnerOnly", businessOwnerOnly)
                .executeUpdate();
    }

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

        LocalDateTime now = LocalDateTime.now();
        boolean originalBusinessOwnerOnly = Boolean.TRUE.equals(loan.getBusinessOwnerOnly());

        /*
         * Recycle is a visibility/lifecycle operation, not a reversal of the
         * loan. Keep immutable journal history, restrict the rows to the owner
         * for controlled audit access, and set recycle_loan_id so active accounting
         * excludes them for every role. The original scope is stored per row so
         * restore is lossless even when historical rows had their own scope.
         */
        upsertTombstone(
                organizationId,
                loanId,
                reference,
                now,
                originalBusinessOwnerOnly);
        restrictLoanAccountingToBusinessOwner(organizationId, loanId, reference);
        loan.setBusinessOwnerOnly(true);
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
         * Financial amounts and loan status are unchanged. Active accounting
         * excludes related journal and settlement rows for every role; the
         * Business Owner can inspect the recycle-bin record and restore it.
         */
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
        // A NULL deadline means automatic purge was blocked by protected financial
        // history. It must never be interpreted as an unlimited restore extension.
        if (purgeAfter == null || !purgeAfter.isAfter(LocalDateTime.now())) {
            throw new IllegalStateException(
                    "The 30-day recycle period has expired or permanent purge is blocked by protected financial history; the loan is no longer restorable");
        }

        entityManager.createNativeQuery("""
                UPDATE loans
                SET deleted_at = NULL,
                    deletion_reason = NULL,
                    deleted_by = NULL,
                    purge_after = NULL,
                    business_owner_only = COALESCE((
                        SELECT t.original_business_owner_only
                        FROM loan_deletion_tombstones t
                        WHERE t.organization_id = :organizationId
                          AND t.loan_id = :loanId
                    ), FALSE)
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

        restoreLoanAccountingScope(organizationId, loanId);

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
    @Scheduled(cron = "${app.loan.recycle-bin.purge-cron:0 15 2 * * *}", zone = "${app.loan.recycle-bin.purge-zone:Africa/Kigali}")
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
         * Posted journal entries and settlement records are NOT a reason to keep
         * the operational loan row forever: recycle markers exclude them from
         * active reports while preserving them for audit and accounting integrity. Only
         * signed contracts and submitted regulatory records that still depend on
         * the operational loan are allowed to block physical loan-row purge.
         */
        if (hasNonPurgeableLegalOrRegulatoryHistory(loanId)) {
            entityManager
                    .createNativeQuery(
                            """
                                    UPDATE loans
                                    SET purge_after = NULL,
                                        deletion_reason = CONCAT(
                                            COALESCE(deletion_reason, ''),
                                            CASE WHEN COALESCE(deletion_reason, '') = '' THEN '' ELSE E'\n' END,
                                            '[PURGE BLOCKED] Signed contractual or regulatory evidence must be retained. The 30-day restore window is closed; contact an authorized compliance administrator for a documented retention decision.'
                                        )
                                    WHERE id = :loanId
                                      AND organization_id = :organizationId
                                      AND deleted_at IS NOT NULL
                                    """)
                    .setParameter("loanId", loanId)
                    .setParameter("organizationId", organizationId)
                    .executeUpdate();
            log.error(
                    "Permanent purge blocked for recycled loan {} (id={}): signed contractual or regulatory evidence must be retained",
                    reference, loanId);
            auditService.log(
                    null,
                    null,
                    "LOAN_PURGE_BLOCKED_PROTECTED_EVIDENCE",
                    "LOAN",
                    String.valueOf(loanId),
                    "Permanent operational purge blocked for loan " + reference
                            + " because signed contractual or regulatory evidence must be retained. Restore window is closed.",
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
         * Delete operational loan children in FK-safe order. Posted journal entries,
         * journal lines, settlements, and audit history are deliberately retained;
         * recycle markers and the tombstone preserve attribution while excluding
         * the evidence from active accounting.
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
     * A permanent operational purge may remove the loan and its operational
     * children after 30 days, while posted ledger evidence remains retained and
     * excluded from active reports by its recycle marker. Signed
     * contracts and submitted bureau/regulatory evidence are different: they are
     * legally significant source records and must not be erased by a routine job.
     */
    private boolean hasNonPurgeableLegalOrRegulatoryHistory(Long loanId) {
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

    private void restrictLoanAccountingToBusinessOwner(
            Long organizationId,
            Long loanId,
            String reference) {
        String loanIdText = String.valueOf(loanId);

        entityManager.createNativeQuery("""
                WITH related_entries AS (
                    SELECT je.id
                    FROM journal_entries je
                    WHERE je.organization_id = :organizationId
                      AND (
                        je.reference = :reference
                        OR (
                            je.source_type IN (
                                'LOAN_DISBURSEMENT', 'LOAN_PAYMENT', 'LOAN_EXTENSION_FEE',
                                'LOAN_EXTENSION_FEE_COLLECTION', 'PENALTY_ACCRUAL',
                                'INTEREST_ACCRUAL', 'MANAGEMENT_FEE_ACCRUAL',
                                'SCHEDULED_INTEREST_ACCRUAL', 'SCHEDULED_MANAGEMENT_FEE_ACCRUAL',
                                'CONTRACTUAL_MONTHLY_INTEREST_ACCRUAL',
                                'CONTRACTUAL_MONTHLY_MANAGEMENT_FEE_ACCRUAL',
                                'HISTORICAL_LOAN_OPENING', 'LEGACY_LOAN_OPENING',
                                'LEGACY_LOAN_RECONCILIATION', 'LEGACY_LOAN_OPENING_DATE_REPAIR',
                                'WRITE_OFF', 'APPLICATION_FEE_INCOME_RECLASSIFICATION'
                            )
                            AND regexp_replace(trim(COALESCE(je.source_id, '')), '^LOAN:', '')
                                ~ ( '^' || :loanIdText || '($|[-:])' )
                        )
                        OR (
                            je.source_type IN ('PAYMENT_RECEIVED', 'REFUND_PAYMENT',
                                'PAYMENT_REFUND', 'PAYMENT_REVERSAL', 'OVERPAYMENT_REFUND_PAYABLE')
                            AND je.source_id IN (SELECT p.id::text FROM payments p WHERE p.loan_id = :loanId)
                        )
                        OR (
                            je.source_type IN ('SCHEDULED_INTEREST_ACCRUAL', 'SCHEDULED_MANAGEMENT_FEE_ACCRUAL')
                            AND je.source_id IN (
                                SELECT 'PAYMENT-' || p.id::text FROM payments p WHERE p.loan_id = :loanId
                            )
                        )
                        OR (
                            je.source_type = 'REVERSAL'
                            AND je.source_id IN (
                                SELECT original.id::text
                                FROM journal_entries original
                                WHERE original.organization_id = :organizationId
                                  AND (
                                    original.reference = :reference
                                    OR regexp_replace(trim(COALESCE(original.source_id, '')), '^LOAN:', '')
                                        ~ ( '^' || :loanIdText || '($|[-:])' )
                                    OR original.source_id IN (
                                        SELECT p.id::text FROM payments p WHERE p.loan_id = :loanId
                                    )
                                )
                            )
                        )
                      )
                )
                UPDATE journal_entries je
                SET recycle_original_business_owner_only = COALESCE(
                        je.recycle_original_business_owner_only, je.business_owner_only, FALSE),
                    recycle_loan_id = :loanId,
                    business_owner_only = TRUE
                WHERE je.id IN (SELECT id FROM related_entries)
                """)
                .setParameter("organizationId", organizationId)
                .setParameter("loanId", loanId)
                .setParameter("loanIdText", loanIdText)
                .setParameter("reference", reference)
                .executeUpdate();

        entityManager.createNativeQuery("""
                UPDATE payment_settlements ps
                SET recycle_original_business_owner_only = COALESCE(
                        ps.recycle_original_business_owner_only, ps.business_owner_only, FALSE),
                    recycle_loan_id = :loanId,
                    business_owner_only = TRUE
                WHERE ps.organization_id = :organizationId
                  AND ps.internal_payment_reference IN (
                      SELECT p.payment_reference FROM payments p WHERE p.loan_id = :loanId
                  )
                """)
                .setParameter("organizationId", organizationId)
                .setParameter("loanId", loanId)
                .executeUpdate();
    }

    private void restoreLoanAccountingScope(Long organizationId, Long loanId) {
        entityManager.createNativeQuery("""
                UPDATE journal_entries je
                SET business_owner_only = COALESCE(je.recycle_original_business_owner_only, FALSE),
                    recycle_original_business_owner_only = NULL,
                    recycle_loan_id = NULL
                WHERE je.organization_id = :organizationId
                  AND je.recycle_loan_id = :loanId
                """)
                .setParameter("organizationId", organizationId)
                .setParameter("loanId", loanId)
                .executeUpdate();

        entityManager.createNativeQuery("""
                UPDATE payment_settlements ps
                SET business_owner_only = COALESCE(ps.recycle_original_business_owner_only, FALSE),
                    recycle_original_business_owner_only = NULL,
                    recycle_loan_id = NULL
                WHERE ps.organization_id = :organizationId
                  AND ps.recycle_loan_id = :loanId
                """)
                .setParameter("organizationId", organizationId)
                .setParameter("loanId", loanId)
                .executeUpdate();
    }

    private void upsertTombstone(
            Long organizationId,
            Long loanId,
            String reference,
            LocalDateTime deletedAt,
            boolean originalBusinessOwnerOnly) {

        entityManager.createNativeQuery("""
                INSERT INTO loan_deletion_tombstones
                    (organization_id, loan_id, reference_number, deleted_at, original_business_owner_only)
                VALUES
                    (:organizationId, :loanId, :reference, :deletedAt, :originalBusinessOwnerOnly)
                ON CONFLICT (organization_id, loan_id)
                DO UPDATE SET
                    reference_number = EXCLUDED.reference_number,
                    deleted_at = EXCLUDED.deleted_at,
                    original_business_owner_only = EXCLUDED.original_business_owner_only,
                    purged_at = NULL
                """)
                .setParameter("organizationId", organizationId)
                .setParameter("loanId", loanId)
                .setParameter("reference", reference)
                .setParameter("deletedAt", deletedAt)
                .setParameter("originalBusinessOwnerOnly", originalBusinessOwnerOnly)
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
