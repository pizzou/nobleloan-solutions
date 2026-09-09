package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.CollectionAction;
import com.patrick.fintech.loan_backend.model.CollectionCase;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.CollectionActionRepository;
import com.patrick.fintech.loan_backend.repository.CollectionCaseRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CollectionsService {

    private static final int MONEY_SCALE = 6;

    private static final RoundingMode MONEY_ROUNDING =
            RoundingMode.HALF_UP;

    private static final BigDecimal ZERO =
            BigDecimal.ZERO.setScale(
                    MONEY_SCALE,
                    MONEY_ROUNDING
            );

    /**
     * Loans considered delinquent by the collections module.
     */
    private static final List<LoanStatus> DELINQUENT_STATUSES =
            List.of(
                    LoanStatus.OVERDUE,
                    LoanStatus.DEFAULTED
            );

    private final CollectionCaseRepository caseRepo;

    private final CollectionActionRepository actionRepo;

    private final LoanRepository loanRepo;

    private final UserRepository userRepo;

    private final AuditService auditService;

    private final AccountingService accountingService;


    // ============================================================
    // MONEY HELPERS
    // ============================================================

    /**
     * Normalizes monetary values used by collections.
     */
    private BigDecimal money(BigDecimal value) {

        if (value == null) {
            return ZERO;
        }

        return value.setScale(
                MONEY_SCALE,
                MONEY_ROUNDING
        );
    }


    /**
     * Safely converts an Object returned by a JPA aggregate query
     * into a normalized BigDecimal.
     *
     * JPA aggregate functions such as SUM() may return different
     * Number implementations depending on the database column
     * type and JDBC driver.
     *
     * This prevents compile-time errors such as:
     *
     * The method money(BigDecimal) is not applicable for the
     * arguments (Object)
     */
    private BigDecimal money(Object value) {

        if (value == null) {
            return ZERO;
        }

        if (value instanceof BigDecimal) {
            return money((BigDecimal) value);
        }

        if (value instanceof Number) {

            Number number = (Number) value;

            if (number instanceof Double
                    || number instanceof Float) {

                double doubleValue = number.doubleValue();

                if (!Double.isFinite(doubleValue)) {
                    throw new IllegalArgumentException(
                            "Monetary amount must be finite"
                    );
                }

                return money(
                        BigDecimal.valueOf(doubleValue)
                );
            }

            return money(
                    new BigDecimal(
                            number.toString()
                    )
            );
        }

        if (value instanceof String) {

            String text = ((String) value).trim();

            if (text.isEmpty()) {
                return ZERO;
            }

            try {

                return money(
                        new BigDecimal(text)
                );

            } catch (NumberFormatException ex) {

                log.warn(
                        "Unable to convert monetary value '{}' to BigDecimal",
                        value
                );

                throw new IllegalArgumentException(
                        "Invalid monetary amount: " + value,
                        ex
                );
            }
        }

        throw new IllegalArgumentException(
                "Unsupported monetary value type: "
                        + value.getClass().getName()
                        + ", value="
                        + value
        );
    }


    /**
     * Converts legacy Double values safely.
     *
     * This method is retained for compatibility with existing
     * entity fields or callers that still use Double.
     *
     * Existing system monetary fields are intentionally not
     * changed from Double/double.
     */
    private BigDecimal money(Double value) {

        if (value == null) {
            return ZERO;
        }

        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    "Monetary amount must be finite"
            );
        }

        return money(
                BigDecimal.valueOf(value)
        );
    }


    /**
     * Converts primitive double values safely.
     */
    private BigDecimal money(double value) {

        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    "Monetary amount must be finite"
            );
        }

        return money(
                BigDecimal.valueOf(value)
        );
    }


    private boolean isZeroOrLess(
            BigDecimal value
    ) {

        return money(value).compareTo(
                ZERO
        ) <= 0;
    }


    private boolean isEffectivelyCleared(
            BigDecimal value
    ) {

        /*
         * Six-decimal accounting precision means that a balance
         * rounded to zero is considered cleared.
         */
        return money(value).compareTo(
                ZERO
        ) == 0;
    }


    private String safeText(
            String value,
            String fallback
    ) {

        if (value == null || value.isBlank()) {
            return fallback;
        }

        return value.trim();
    }


    // ============================================================
    // SYNC DELINQUENT LOANS
    // ============================================================

    /**
     * Scans overdue/defaulted loans and creates or refreshes
     * collection cases.
     *
     * This method is intended for scheduled execution.
     *
     * Important:
     * - Resolved cases are not automatically reopened.
     * - Written-off cases are not automatically reopened.
     * - Existing active cases are refreshed.
     * - Money is normalized to BigDecimal internally.
     */
    @Transactional
    public int syncCasesFromOverdueLoans() {

        int touched = 0;

        List<Loan> delinquentLoans =
                loanRepo.findByStatusIn(
                        DELINQUENT_STATUSES
                );

        if (delinquentLoans == null
                || delinquentLoans.isEmpty()) {

            return 0;
        }


        for (Loan loan : delinquentLoans) {

            if (loan == null
                    || loan.getId() == null) {

                continue;
            }


            if (loan.getOrganization() == null
                    || loan.getOrganization().getId() == null) {

                log.warn(
                        "Skipping delinquent loan {} because organization is missing",
                        loan.getId()
                );

                continue;
            }


            CollectionCase existingCase =
                    caseRepo
                            .findByLoan_Id(
                                    loan.getId()
                            )
                            .orElse(null);


            int daysPastDue =
                    loan.getDaysOverdue() != null
                            ? Math.max(
                                    loan.getDaysOverdue(),
                                    0
                            )
                            : 0;


            CollectionCase.CollectionBucket bucket =
                    bucketFor(
                            daysPastDue
                    );


            boolean isNew =
                    existingCase == null;


            CollectionCase collectionCase;


            if (isNew) {

                collectionCase =
                        CollectionCase.builder()
                                .loan(loan)
                                .organization(
                                        loan.getOrganization()
                                )
                                .bucket(bucket)
                                .status(
                                        CollectionCase.CollectionStatus.OPEN
                                )
                                .priority(
                                        priorityFor(bucket)
                                )
                                .build();

            } else {

                collectionCase =
                        existingCase;


                /*
                 * Closed collection cases are intentionally
                 * not reopened automatically.
                 */
                if (
                        collectionCase.getStatus()
                                == CollectionCase.CollectionStatus.RESOLVED

                                ||

                        collectionCase.getStatus()
                                == CollectionCase.CollectionStatus.WRITTEN_OFF
                ) {

                    continue;
                }


                collectionCase.setBucket(
                        bucket
                );

                collectionCase.setPriority(
                        priorityFor(bucket)
                );
            }


            collectionCase.setDaysPastDue(
                    daysPastDue
            );


            /*
             * Preserve the existing system behavior:
             * outstanding balance is used as the collection
             * overdue amount because the exact overdue
             * principal/interest fields are not assumed here.
             */
            BigDecimal outstanding =
                    money(
                            loan.getOutstandingBalance()
                    );


            collectionCase.setOverdueAmount(
                    outstanding
            );

            collectionCase.setTotalOutstanding(
                    outstanding
            );


            collectionCase =
                    caseRepo.save(
                            collectionCase
                    );


            if (isNew) {

                logAction(
                        collectionCase.getId(),
                        CollectionAction.ActionType.CASE_OPENED,
                        "Auto-opened: loan is "
                                + daysPastDue
                                + " day(s) past due",
                        "SYSTEM",
                        null,
                        null,
                        null
                );
            }


            touched++;
        }


        log.info(
                "Collection synchronization completed. {} case(s) touched.",
                touched
        );


        return touched;
    }


    // ============================================================
    // COLLECTION QUEUE
    // ============================================================

    /**
     * Returns the collection queue for an organization.
     *
     * Organization ID is mandatory to maintain tenant isolation.
     */
    @Transactional(readOnly = true)
    public List<CollectionCase> getQueue(
            Long orgId,
            CollectionCase.CollectionBucket bucket,
            CollectionCase.CollectionStatus status,
            Long agentId
    ) {

        requireOrganizationId(
                orgId
        );


        List<CollectionCase> cases =
                caseRepo.findQueue(
                        orgId,
                        bucket,
                        status,
                        agentId
                );


        return cases == null
                ? List.of()
                : cases;
    }


    // ============================================================
    // GET CASE
    // ============================================================

    /**
     * Gets a collection case without organization filtering.
     *
     * Retained for compatibility with existing callers.
     *
     * Tenant-sensitive controllers should prefer getCaseForOrg().
     */
    @Transactional(readOnly = true)
    public CollectionCase getCase(
            Long caseId
    ) {

        requireId(
                caseId,
                "Collection case ID"
        );


        return caseRepo
                .findById(
                        caseId
                )
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Collection case not found: "
                                                + caseId
                                )
                );
    }


    /**
     * Production-safe tenant-scoped case lookup.
     */
    @Transactional(readOnly = true)
    public CollectionCase getCaseForOrg(
            Long caseId,
            Long orgId
    ) {

        requireId(
                caseId,
                "Collection case ID"
        );

        requireOrganizationId(
                orgId
        );


        CollectionCase collectionCase =
                getCase(
                        caseId
                );


        if (
                collectionCase.getOrganization() == null
                        ||
                collectionCase
                        .getOrganization()
                        .getId() == null
        ) {

            throw new IllegalStateException(
                    "Collection case has no organization: "
                            + caseId
            );
        }


        if (
                !orgId.equals(
                        collectionCase
                                .getOrganization()
                                .getId()
                )
        ) {

            /*
             * Do not reveal whether another tenant's case exists.
             */
            throw new IllegalArgumentException(
                    "Collection case not found: "
                            + caseId
            );
        }


        return collectionCase;
    }


    // ============================================================
    // ASSIGN AGENT
    // ============================================================

    /**
     * Assigns a collection case to an agent.
     *
     * Agent must belong to the same organization as the case.
     */
    @Transactional
    public CollectionCase assignAgent(
            Long caseId,
            Long agentUserId,
            String assignedBy
    ) {

        requireId(
                caseId,
                "Collection case ID"
        );

        requireId(
                agentUserId,
                "Agent user ID"
        );


        CollectionCase collectionCase =
                getCase(
                        caseId
                );


        if (
                collectionCase.getOrganization() == null
                        ||
                collectionCase
                        .getOrganization()
                        .getId() == null
        ) {

            throw new IllegalStateException(
                    "Collection case has no organization"
            );
        }


        Long organizationId =
                collectionCase
                        .getOrganization()
                        .getId();


        User agent =
                userRepo
                        .findById(
                                agentUserId
                        )
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Agent not found: "
                                                        + agentUserId
                                        )
                        );


        /*
         * Tenant isolation.
         */
        if (
                agent.getOrganization() == null
                        ||
                agent.getOrganization().getId() == null
        ) {

            throw new IllegalStateException(
                    "Agent has no organization: "
                            + agentUserId
            );
        }


        if (
                !organizationId.equals(
                        agent
                                .getOrganization()
                                .getId()
                )
        ) {

            throw new IllegalArgumentException(
                    "Agent does not belong to the same organization"
            );
        }


        collectionCase.setAssignedAgent(
                agent
        );


        if (
                collectionCase.getStatus()
                        == CollectionCase.CollectionStatus.OPEN
        ) {

            collectionCase.setStatus(
                    CollectionCase.CollectionStatus.IN_PROGRESS
            );
        }


        collectionCase =
                caseRepo.save(
                        collectionCase
                );


        String actor =
                safeText(
                        assignedBy,
                        "SYSTEM"
                );


        auditService.log(
                collectionCase.getOrganization(),
                null,
                "COLLECTION_CASE_ASSIGNED",
                "COLLECTION_CASE",
                String.valueOf(caseId),
                "Assigned to "
                        + safeText(
                                agent.getName(),
                                "agent"
                        )
                        + " by "
                        + actor
        );


        return collectionCase;
    }


    // ============================================================
    // LOG COLLECTION ACTION
    // ============================================================

    /**
     * Records a collection action and updates the case state.
     *
     * This is transactional because the action, collection case,
     * loan write-off and accounting entry must remain consistent.
     */
    @Transactional
    public CollectionAction logAction(
            Long caseId,
            CollectionAction.ActionType type,
            String notes,
            String performedBy,
            String outcome,
            LocalDate promiseDate,
            Double promiseAmount
    ) {

        requireId(
                caseId,
                "Collection case ID"
        );


        if (type == null) {

            throw new IllegalArgumentException(
                    "Collection action type is required"
            );
        }


        CollectionCase collectionCase =
                getCase(
                        caseId
                );


        if (
                collectionCase.getOrganization() == null
                        ||
                collectionCase
                        .getOrganization()
                        .getId() == null
        ) {

            throw new IllegalStateException(
                    "Collection case has no organization"
            );
        }


        String actor =
                safeText(
                        performedBy,
                        "SYSTEM"
                );


        String safeNotes =
                notes != null
                        ? notes.trim()
                        : null;


        String safeOutcome =
                outcome != null
                        ? outcome.trim()
                        : null;


        // ========================================================
        // PROMISE-TO-PAY VALIDATION
        // ========================================================

        if (
                type
                        == CollectionAction.ActionType.PROMISE_TO_PAY
        ) {

            if (promiseDate == null) {

                throw new IllegalArgumentException(
                        "Promise-to-pay date is required"
                );
            }


            BigDecimal promise =
                    money(
                            promiseAmount
                    );


            if (
                    promise.compareTo(
                            ZERO
                    ) <= 0
            ) {

                throw new IllegalArgumentException(
                        "Promise-to-pay amount must be greater than zero"
                );
            }


            if (
                    promiseDate.isBefore(
                            LocalDate.now()
                    )
            ) {

                throw new IllegalArgumentException(
                        "Promise-to-pay date cannot be in the past"
                );
            }
        }


        // ========================================================
        // WRITTEN-OFF CASE PROTECTION
        // ========================================================

        /*
         * Do not allow operational actions on cases that are
         * already written off.
         */
        if (
                collectionCase.getStatus()
                        == CollectionCase.CollectionStatus.WRITTEN_OFF
                &&
                type
                        != CollectionAction.ActionType.CASE_CLOSED
        ) {

            throw new IllegalStateException(
                    "Cannot add this action to a written-off collection case"
            );
        }


        /*
         * Do not create another write-off for a case that has
         * already been written off.
         */
        if (
                type
                        == CollectionAction.ActionType.WRITE_OFF
                &&
                collectionCase.getStatus()
                        == CollectionCase.CollectionStatus.WRITTEN_OFF
        ) {

            throw new IllegalStateException(
                    "Collection case has already been written off"
            );
        }


        // ========================================================
        // SAVE ACTION
        // ========================================================

        BigDecimal normalizedPromiseAmount =
                promiseAmount == null
                        ? null
                        : money(
                                promiseAmount
                        );


        CollectionAction action =
                CollectionAction.builder()
                        .collectionCase(collectionCase)
                        .actionType(type)
                        .notes(safeNotes)
                        .performedBy(actor)
                        .outcome(safeOutcome)
                        .promiseDate(promiseDate)
                        .promiseAmount(
                                normalizedPromiseAmount != null
                                        ? normalizedPromiseAmount.doubleValue()
                                        : null
                        )
                        .build();


        action =
                actionRepo.save(
                        action
                );


        collectionCase.setLastContactDate(
                LocalDate.now()
        );


        // ========================================================
        // STATUS TRANSITIONS
        // ========================================================

        switch (type) {

            case PROMISE_TO_PAY -> {

                BigDecimal promise =
                        money(
                                promiseAmount
                        );


                collectionCase.setStatus(
                        CollectionCase.CollectionStatus.PROMISE_TO_PAY
                );


                collectionCase.setPromiseToPayDate(
                        promiseDate
                );


                collectionCase.setPromiseToPayAmount(
                        promise.doubleValue()
                );


                collectionCase.setNextActionDate(
                        promiseDate
                );
            }


            case ESCALATED -> {

                collectionCase.setStatus(
                        CollectionCase.CollectionStatus.ESCALATED
                );
            }


            case LEGAL_NOTICE -> {

                collectionCase.setStatus(
                        CollectionCase.CollectionStatus.LEGAL
                );
            }


            case PAYMENT_RECEIVED -> {

                Loan loan =
                        collectionCase.getLoan();


                if (loan == null) {

                    throw new IllegalStateException(
                            "Collection case has no loan"
                    );
                }


                BigDecimal outstanding =
                        money(
                                loan.getOutstandingBalance()
                        );


                boolean cleared =
                        isEffectivelyCleared(
                                outstanding
                        );


                if (cleared) {

                    collectionCase.setStatus(
                            CollectionCase.CollectionStatus.RESOLVED
                    );


                    collectionCase.setClosedAt(
                            LocalDateTime.now()
                    );


                    collectionCase.setNextActionDate(
                            null
                    );

                } else if (
                        collectionCase.getStatus()
                                == CollectionCase.CollectionStatus.PROMISE_TO_PAY
                ) {

                    collectionCase.setStatus(
                            CollectionCase.CollectionStatus.IN_PROGRESS
                    );
                }
            }


            case WRITE_OFF -> {

                Loan loan =
                        collectionCase.getLoan();


                if (loan == null) {

                    throw new IllegalStateException(
                            "Cannot write off collection case without a loan"
                    );
                }


                if (loan.getId() == null) {

                    throw new IllegalStateException(
                            "Cannot write off loan without an ID"
                    );
                }


                /*
                 * Change the loan status before accounting.
                 *
                 * Because this method is transactional, an
                 * accounting failure will roll back the loan
                 * and collection-case changes.
                 */
                loan.setStatus(
                        LoanStatus.WRITTEN_OFF
                );

                loan.setWrittenOffAt(
                        LocalDateTime.now()
                );


                loanRepo.save(
                        loan
                );


                collectionCase.setStatus(
                        CollectionCase.CollectionStatus.WRITTEN_OFF
                );


                collectionCase.setBucket(
                        CollectionCase.CollectionBucket.WRITE_OFF
                );


                collectionCase.setClosedAt(
                        LocalDateTime.now()
                );


                collectionCase.setResolutionNotes(
                        safeNotes
                );


                collectionCase.setNextActionDate(
                        null
                );


                /*
                 * Accounting write-off.
                 *
                 * AccountingService.postWriteOff() should be
                 * idempotent so that duplicate accounting entries
                 * cannot be generated.
                 */
                accountingService.postWriteOff(
                        loan
                );
            }


            case CASE_CLOSED -> {

                collectionCase.setStatus(
                        CollectionCase.CollectionStatus.RESOLVED
                );


                collectionCase.setClosedAt(
                        LocalDateTime.now()
                );


                collectionCase.setResolutionNotes(
                        safeNotes
                );


                collectionCase.setNextActionDate(
                        null
                );
            }


            case CALL,
                 SMS,
                 EMAIL,
                 FIELD_VISIT,
                 CASE_OPENED -> {

                /*
                 * Contact/action-only events.
                 *
                 * Do not change the case status automatically.
                 */
            }


            default -> {

                log.debug(
                        "No explicit collection status transition for action {}",
                        type
                );
            }
        }


        collectionCase =
                caseRepo.save(
                        collectionCase
                );


        // ========================================================
        // AUDIT
        // ========================================================

        auditService.log(
                collectionCase.getOrganization(),
                null,
                "COLLECTION_ACTION_" + type.name(),
                "COLLECTION_CASE",
                String.valueOf(caseId),
                type.name()
                        + " logged by "
                        + actor
                        + (
                                safeNotes != null
                                        ? ": " + safeNotes
                                        : ""
                        )
        );


        return action;
    }


    // ============================================================
    // ACTION HISTORY
    // ============================================================

    @Transactional(readOnly = true)
    public List<CollectionAction> getActions(
            Long caseId
    ) {

        requireId(
                caseId,
                "Collection case ID"
        );


        List<CollectionAction> actions =
                actionRepo
                        .findByCollectionCase_IdOrderByCreatedAtDesc(
                                caseId
                        );


        if (actions == null
                || actions.isEmpty()) {

            return List.of();
        }


        return actions;
    }


    // ============================================================
    // STATS
    // ============================================================

    /**
     * Returns collection statistics for one organization.
     *
     * The repository returns Object[] because the statistics query
     * uses grouped aggregate values. Monetary aggregate values are
     * converted safely through money(Object).
     *
     * BigDecimal is used internally for monetary calculations.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getStats(
            Long orgId
    ) {

        requireOrganizationId(
                orgId
        );


        Map<CollectionCase.CollectionBucket, Long> bucketCounts =
                new EnumMap<>(
                        CollectionCase.CollectionBucket.class
                );


        Map<CollectionCase.CollectionBucket, BigDecimal> bucketAmounts =
                new EnumMap<>(
                        CollectionCase.CollectionBucket.class
                );


        /*
         * Initialize every bucket so the API always returns
         * a complete and predictable statistics structure.
         */
        for (
                CollectionCase.CollectionBucket bucket
                        : CollectionCase.CollectionBucket.values()
        ) {

            bucketCounts.put(
                    bucket,
                    0L
            );

            bucketAmounts.put(
                    bucket,
                    ZERO
            );
        }


        long totalOpenCases = 0L;

        BigDecimal totalOverdue =
                ZERO;


        /*
         * One grouped database query supplies the collection
         * statistics instead of loading every CollectionCase
         * entity into Java.
         */
        List<Object[]> rows =
                caseRepo.getStatsByBucket(
                        orgId
                );


        if (rows != null) {

            for (Object[] row : rows) {

                if (
                        row == null
                                || row.length < 4
                                || row[0] == null
                ) {

                    continue;
                }


                CollectionCase.CollectionBucket bucket;


                try {

                    bucket =
                            (CollectionCase.CollectionBucket) row[0];

                } catch (ClassCastException ex) {

                    log.warn(
                            "Skipping collection statistics row because bucket type is invalid: {}",
                            row[0],
                            ex
                    );

                    continue;
                }


                CollectionCase.CollectionStatus status =
                        row[1] == null
                                ? null
                                : (CollectionCase.CollectionStatus) row[1];


                /*
                 * Resolved and written-off cases are not considered
                 * open collection exposure.
                 */
                if (
                        status
                                == CollectionCase.CollectionStatus.RESOLVED
                                ||
                        status
                                == CollectionCase.CollectionStatus.WRITTEN_OFF
                ) {

                    continue;
                }


                long count =
                        row[2] == null
                                ? 0L
                                : (
                                        row[2] instanceof Number
                                                ? ((Number) row[2]).longValue()
                                                : Long.parseLong(
                                                        row[2].toString()
                                                )
                                );


                /*
                 * IMPORTANT:
                 *
                 * row[3] is Object because the repository returns
                 * List<Object[]>.
                 *
                 * Do NOT call money(BigDecimal) directly with row[3].
                 *
                 * money(Object) safely converts the actual JDBC/JPA
                 * aggregate result.
                 */
                BigDecimal amount =
                        money(
                                row[3]
                        );


                bucketCounts.put(
                        bucket,
                        count
                );


                bucketAmounts.put(
                        bucket,
                        amount
                );


                totalOpenCases +=
                        count;


                totalOverdue =
                        totalOverdue.add(
                                amount
                        );
            }
        }


        Long promises =
                caseRepo.countByOrganization_IdAndStatus(
                        orgId,
                        CollectionCase.CollectionStatus.PROMISE_TO_PAY
                );


        long activePromises =
                promises == null
                        ? 0L
                        : promises;


        // ========================================================
        // API RESPONSE STRUCTURES
        // ========================================================

        Map<String, Long> casesByBucket =
                new LinkedHashMap<>();


        Map<String, BigDecimal> overdueAmountByBucket =
                new LinkedHashMap<>();


        for (
                CollectionCase.CollectionBucket bucket
                        : CollectionCase.CollectionBucket.values()
        ) {

            casesByBucket.put(
                    bucket.name(),
                    bucketCounts.getOrDefault(
                            bucket,
                            0L
                    )
            );


            overdueAmountByBucket.put(
                    bucket.name(),
                    money(
                            bucketAmounts.getOrDefault(
                                    bucket,
                                    ZERO
                            )
                    )
            );
        }


        Map<String, Object> stats =
                new LinkedHashMap<>();


        stats.put(
                "casesByBucket",
                casesByBucket
        );


        stats.put(
                "overdueAmountByBucket",
                overdueAmountByBucket
        );


        stats.put(
                "totalOpenCases",
                totalOpenCases
        );


        stats.put(
                "totalOverdueAmount",
                money(
                        totalOverdue
                )
        );


        stats.put(
                "activePromises",
                activePromises
        );


        return stats;
    }


    // ============================================================
    // BUCKET CALCULATION
    // ============================================================

    private CollectionCase.CollectionBucket bucketFor(
            int dpd
    ) {

        if (dpd <= 0) {

            return CollectionCase.CollectionBucket.CURRENT;
        }


        if (dpd <= 30) {

            return CollectionCase.CollectionBucket.DPD_1_30;
        }


        if (dpd <= 60) {

            return CollectionCase.CollectionBucket.DPD_31_60;
        }


        if (dpd <= 90) {

            return CollectionCase.CollectionBucket.DPD_61_90;
        }


        return CollectionCase.CollectionBucket.DPD_90_PLUS;
    }


    // ============================================================
    // PRIORITY
    // ============================================================

    private CollectionCase.Priority priorityFor(
            CollectionCase.CollectionBucket bucket
    ) {

        if (bucket == null) {

            return CollectionCase.Priority.LOW;
        }


        return switch (bucket) {

            case CURRENT,
                 DPD_1_30 ->
                    CollectionCase.Priority.LOW;


            case DPD_31_60 ->
                    CollectionCase.Priority.MEDIUM;


            case DPD_61_90 ->
                    CollectionCase.Priority.HIGH;


            case DPD_90_PLUS,
                 WRITE_OFF ->
                    CollectionCase.Priority.URGENT;
        };
    }


    // ============================================================
    // VALIDATION HELPERS
    // ============================================================

    private void requireId(
            Long id,
            String field
    ) {

        if (id == null
                || id <= 0) {

            throw new IllegalArgumentException(
                    field + " is required"
            );
        }
    }


    private void requireOrganizationId(
            Long orgId
    ) {

        if (orgId == null
                || orgId <= 0) {

            throw new IllegalArgumentException(
                    "Organization ID is required"
            );
        }
    }
}