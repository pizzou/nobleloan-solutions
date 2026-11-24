package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Applies the fixed financial policy to an entire organization without
 * borrower-by-borrower manual monetary entry. Each loan is processed in
 * its OWN transaction through LoanPolicyRecalculationService's Spring proxy:
 * a rejected loan cannot roll back another borrower's successful correction.
 *
 * Unsupported, incomplete or inconsistent histories are excluded and
 * reported, never silently guessed or balanced with arbitrary journal lines.
 */
@Service
@RequiredArgsConstructor
public class LoanPolicyPortfolioReconciliationService {
    private final LoanRepository loans;
    private final LoanPolicyRecalculationService corrections;

    public record Item(Long loanId, String reference, String status,
                       String description) {}
    public record Batch(Long organizationId, int eligible, int corrected,
                        int alreadyConsistent, int skipped, List<Item> items) {}

    public Batch run(Long organizationId, User actor, boolean apply) {
        if (organizationId == null || actor == null
                || actor.getOrganization() == null
                || !Objects.equals(organizationId, actor.getOrganization().getId()))
            throw new IllegalArgumentException("Authenticated organization required");

        List<Loan> owned = loans.findAll().stream()
                .filter(l -> l != null && l.getId() != null
                        && l.getOrganization() != null
                        && organizationId.equals(l.getOrganization().getId())
                        && Boolean.TRUE.equals(l.getImported())
                        && l.getDeletedAt() == null)
                .sorted(Comparator.comparing(Loan::getId))
                .toList();
        List<Item> results = new ArrayList<>();
        int eligible = 0, corrected = 0, alreadyConsistent = 0, skipped = 0;
        LocalDate today = LocalDate.now();
        for (Loan loan : owned) {
            try {
                LoanPolicyRecalculationService.Preview preview =
                        corrections.preview(loan.getId(), organizationId, today);
                eligible++;
                if (!preview.needsCorrection()) {
                    alreadyConsistent++;
                    results.add(new Item(loan.getId(), loan.getReferenceNumber(),
                            "CONSISTENT", "Amounts already match recorded policy"));
                } else if (!apply) {
                    results.add(new Item(loan.getId(), loan.getReferenceNumber(),
                            "ELIGIBLE_PREVIEW", "Reallocation calculated, not yet posted"));
                } else {
                    LoanPolicyRecalculationService.Applied applied =
                            corrections.apply(loan.getId(), organizationId, actor);
                    if ("APPLIED_NONCASH_POLICY_RECONCILIATION".equals(applied.status()))
                        corrected++;
                    else
                        alreadyConsistent++;
                    results.add(new Item(loan.getId(), loan.getReferenceNumber(),
                            applied.status(), "Non-cash policy reconciliation processed"));
                }
            } catch (RuntimeException e) {
                skipped++;
                results.add(new Item(loan.getId(), loan.getReferenceNumber(),
                        "SKIPPED_NEEDS_SOURCE_REVIEW",
                        e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            }
        }
        return new Batch(organizationId, eligible, corrected, alreadyConsistent,
                skipped, List.copyOf(results));
    }
}
