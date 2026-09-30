package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.JournalEntryRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * High-risk administrative operations for loans.
 *
 * Physical deletion is deliberately limited to a genuinely unused loan.
 * Once a loan has any workflow, financial, accounting, regulatory,
 * reporting, collection, collateral, signature, or other database
 * relationship, the operation fails closed and the loan is preserved.
 *
 * This service intentionally does not cascade-delete business history.
 */
@Service
@RequiredArgsConstructor
public class LoanAdministrationService {

    /**
     * These statuses are already part of the financial/regulatory lifecycle.
     * They must never be physically removed even if a future schema change
     * accidentally weakens one of the foreign-key protections.
     */
    private static final List<LoanStatus> FINANCIAL_OR_REGULATORY_STATUSES = List.of(
            LoanStatus.APPROVED,
            LoanStatus.DISBURSED,
            LoanStatus.ACTIVE,
            LoanStatus.OVERDUE,
            LoanStatus.DEFAULTED,
            LoanStatus.RESTRUCTURED,
            LoanStatus.WRITTEN_OFF,
            LoanStatus.PAID,
            LoanStatus.CLOSED
    );

    /**
     * Loan-originated accounting journal families used by the current
     * accounting/reporting implementation. Payment-originated journals are
     * protected by the payment -> loan foreign key and by the payment checks
     * in the database relationship scan below.
     */
    private static final List<String> LOAN_ACCOUNTING_SOURCE_TYPES = List.of(
            "LOAN_DISBURSEMENT",
            "LOAN_PAYMENT",
            "LOAN_EXTENSION_FEE",
            "LOAN_EXTENSION_FEE_COLLECTION",
            "PENALTY_ACCRUAL",
            "INTEREST_ACCRUAL",
            "MANAGEMENT_FEE_ACCRUAL",
            "SCHEDULED_INTEREST_ACCRUAL",
            "SCHEDULED_MANAGEMENT_FEE_ACCRUAL",
            "CONTRACTUAL_MONTHLY_INTEREST_ACCRUAL",
            "CONTRACTUAL_MONTHLY_MANAGEMENT_FEE_ACCRUAL",
            "HISTORICAL_LOAN_OPENING",
            "LEGACY_LOAN_OPENING",
            "LEGACY_LOAN_RECONCILIATION",
            "LEGACY_LOAN_OPENING_DATE_REPAIR",
            "WRITE_OFF"
    );

    private final LoanRepository loanRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final EntityManager entityManager;

    /**
     * Deletes a loan only when exact high-risk confirmation is supplied and
     * the complete persistence graph proves that the loan has no history.
     */
    @Transactional
    public void deleteWithConfirmation(Long loanId, String confirmation, User actor) {
        requireBusinessOwner(actor);

        if (loanId == null) {
            throw new IllegalArgumentException("Loan ID is required");
        }

        Long organizationId = actor.getOrganization() == null
                ? null
                : actor.getOrganization().getId();
        if (organizationId == null) {
            throw new IllegalArgumentException("Organization is required");
        }

        // Serialize the deletion decision with loan lifecycle changes.
        Loan loan = loanRepository.findByIdForUpdate(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Loan not found"));

        if (loan.getOrganization() == null
                || !organizationId.equals(loan.getOrganization().getId())) {
            throw new AccessDeniedException("Loan does not belong to your organization");
        }

        String reference = loan.getReferenceNumber();
        if (reference == null || reference.isBlank()) {
            throw cannotSafelyDelete("The loan has no valid reference number. It cannot be safely deleted.");
        }

        String expected = "sudo " + reference;
        if (!expected.equals(confirmation)) {
            throw new IllegalArgumentException(
                    "High-risk deletion confirmation failed. Type exactly: " + expected);
        }

        if (FINANCIAL_OR_REGULATORY_STATUSES.contains(loan.getStatus())) {
            throw cannotSafelyDelete(
                    "This loan has entered the financial or regulatory lifecycle (status: "
                            + loan.getStatus()
                            + "). It must be preserved for accounting, BNR/credit-bureau and reporting history.");
        }

        /*
         * Do not maintain a hand-written list of child repositories here.
         * The database is the source of truth for relationships. This scan
         * discovers every foreign key whose parent is loans(id), including
         * tables represented only by SQL migrations (for example credit-bureau
         * submission/dispute tables).
         *
         * This also catches future loan child tables automatically after a
         * migration, so a new relationship fails closed instead of becoming
         * an accidental data-loss path.
         */
        String dependentTable = findLoanForeignKeyDependency(loanId);
        if (dependentTable != null) {
            throw cannotSafelyDelete(
                    "This loan has linked records in " + dependentTable
                            + ". The loan is preserved so accounting, BNR, credit-bureau and reporting history remain intact.");
        }

        /*
         * Journal entries deliberately do not have a loan_id foreign key in
         * this project. They use source_type/source_id, so they require a
         * separate accounting-history guard.
         */
        if (hasLoanAccountingHistory(organizationId, loanId, reference)) {
            throw cannotSafelyDelete(
                    "This loan has accounting journal history. The loan must be preserved so the general ledger and financial reports remain correct.");
        }

        try {
            // No child business record exists, so delete only the loan row.
            // No cascade-delete of financial/regulatory history is performed.
            loanRepository.delete(loan);
            loanRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            /*
             * Final database-level backstop. If a concurrent transaction or a
             * future schema relationship creates a dependency after the scan,
             * the transaction fails closed and no deletion is accepted.
             */
            throw cannotSafelyDelete(
                    "The loan has a linked record protected by the database. No financial or reporting data was deleted.",
                    ex);
        }
    }

    private void requireBusinessOwner(User actor) {
        if (actor == null
                || actor.getRole() == null
                || actor.getRole().getName() == null
                || !"BUSINESS_OWNER".equalsIgnoreCase(actor.getRole().getName())) {
            throw new AccessDeniedException("Only the BUSINESS_OWNER can delete a loan");
        }
    }

    /**
     * Returns the first actual child table/column containing the loan ID, or
     * null when no foreign-key child record exists.
     */
    private String findLoanForeignKeyDependency(Long loanId) {
        @SuppressWarnings("unchecked")
        List<Object[]> foreignKeys = entityManager.createNativeQuery("""
                SELECT
                    child_ns.nspname,
                    child_table.relname,
                    child_column.attname
                FROM pg_constraint constraint_row
                JOIN pg_class child_table
                  ON child_table.oid = constraint_row.conrelid
                JOIN pg_namespace child_ns
                  ON child_ns.oid = child_table.relnamespace
                JOIN LATERAL unnest(constraint_row.conkey)
                    WITH ORDINALITY AS child_key(attnum, ord)
                  ON TRUE
                JOIN pg_attribute child_column
                  ON child_column.attrelid = child_table.oid
                 AND child_column.attnum = child_key.attnum
                JOIN LATERAL unnest(constraint_row.confkey)
                    WITH ORDINALITY AS parent_key(attnum, ord)
                  ON parent_key.ord = child_key.ord
                JOIN pg_class parent_table
                  ON parent_table.oid = constraint_row.confrelid
                JOIN pg_namespace parent_ns
                  ON parent_ns.oid = parent_table.relnamespace
                WHERE constraint_row.contype = 'f'
                  AND parent_ns.nspname = 'public'
                  AND parent_table.relname = 'loans'
                  AND child_table.oid <> parent_table.oid
                  AND child_table.relkind IN ('r', 'p')
                ORDER BY child_ns.nspname, child_table.relname, child_column.attname
                """).getResultList();

        for (Object[] foreignKey : foreignKeys) {
            String schema = String.valueOf(foreignKey[0]);
            String table = String.valueOf(foreignKey[1]);
            String column = String.valueOf(foreignKey[2]);

            String sql = "SELECT EXISTS (SELECT 1 FROM "
                    + quoteIdentifier(schema) + "." + quoteIdentifier(table)
                    + " WHERE " + quoteIdentifier(column) + " = :loanId)";

            Query dependencyQuery = entityManager.createNativeQuery(sql);
            dependencyQuery.setParameter("loanId", loanId);

            Object result = dependencyQuery.getSingleResult();
            if (Boolean.TRUE.equals(result)) {
                return schema + "." + table + "." + column;
            }
        }

        return null;
    }

    private boolean hasLoanAccountingHistory(Long organizationId, Long loanId, String reference) {
        String numericLoanId = String.valueOf(loanId);
        String prefixedLoanId = "LOAN:" + loanId;

        for (String sourceType : LOAN_ACCOUNTING_SOURCE_TYPES) {
            if (journalEntryRepository
                    .findFirstByOrganization_IdAndSourceTypeAndSourceId(
                            organizationId, sourceType, numericLoanId)
                    .isPresent()) {
                return true;
            }

            if (journalEntryRepository
                    .findFirstByOrganization_IdAndSourceTypeAndSourceId(
                            organizationId, sourceType, prefixedLoanId)
                    .isPresent()) {
                return true;
            }
        }

        // Historical/compatibility journals can carry the immutable loan
        // reference instead of the numeric loan ID. A match is deliberately
        // treated as a blocker rather than attempting to infer ownership.
        if (reference != null && !reference.isBlank()) {
            Object count = entityManager.createNativeQuery("""
                    SELECT COUNT(*)
                    FROM journal_entries
                    WHERE organization_id = :organizationId
                      AND reference = :reference
                    """)
                    .setParameter("organizationId", organizationId)
                    .setParameter("reference", reference)
                    .getSingleResult();

            if (count instanceof Number number && number.longValue() > 0L) {
                return true;
            }
        }

        return false;
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private IllegalStateException cannotSafelyDelete(String message) {
        return new IllegalStateException(message);
    }

    private IllegalStateException cannotSafelyDelete(String message, Throwable cause) {
        return new IllegalStateException(message, cause);
    }
}
