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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CollectionsService {

    private final CollectionCaseRepository caseRepo;
    private final CollectionActionRepository actionRepo;
    private final LoanRepository loanRepo;
    private final UserRepository userRepo;
    private final AuditService auditService;
    private final AccountingService accountingService;

    private static final List<LoanStatus> DELINQUENT_STATUSES =
            List.of(
                    LoanStatus.OVERDUE,
                    LoanStatus.DEFAULTED
            );


    // ============================================================
    // SYNCHRONIZE COLLECTION CASES
    // ============================================================

    /**
     * Synchronizes collection cases from delinquent loans.
     *
     * A loan is considered eligible for collections when:
     *
     * 1. Its status is OVERDUE or DEFAULTED
     *
     * OR
     *
     * 2. Its daysOverdue is >= 1
     *
     * This is important because the loan status may still be ACTIVE
     * even though the borrower has already missed a payment.
     */
    @Transactional
    public int syncCasesFromOverdueLoans() {

        int touched = 0;

        List<Loan> delinquentLoans =
                loanRepo.findByStatusInOrDaysOverdueGreaterThanEqual(
                        DELINQUENT_STATUSES,
                        1
                );

        if (delinquentLoans == null || delinquentLoans.isEmpty()) {

            log.debug(
                    "Collection synchronization: no delinquent loans found."
            );

            return 0;
        }

        log.info(
                "Collection synchronization found {} potentially delinquent loans.",
                delinquentLoans.size()
        );


        for (Loan loan : delinquentLoans) {

            if (loan == null || loan.getId() == null) {
                continue;
            }

            try {

                /*
                 * A loan with no organization should not create a
                 * tenant collection case.
                 */
                if (loan.getOrganization() == null) {

                    log.warn(
                            "Skipping delinquent loan {} because organization is null.",
                            loan.getId()
                    );

                    continue;
                }


                CollectionCase collectionCase =
                        caseRepo.findByLoan_Id(loan.getId())
                                .orElse(null);


                /*
                 * daysOverdue is the primary source for the collection
                 * bucket.
                 */
                int daysPastDue =
                        loan.getDaysOverdue() != null
                                ? Math.max(
                                        loan.getDaysOverdue(),
                                        0
                                )
                                : 0;


                /*
                 * Safety:
                 *
                 * This repository query can find OVERDUE/DEFAULTED loans
                 * even when daysOverdue is 0.
                 *
                 * Such a loan should still be visible in collections,
                 * but it belongs to CURRENT rather than a DPD bucket.
                 */
                CollectionCase.CollectionBucket bucket =
                        bucketFor(daysPastDue);


                boolean isNew =
                        collectionCase == null;


                /*
                 * Never automatically reopen a case that was deliberately
                 * resolved or written off.
                 */
                if (!isNew
                        && (
                        collectionCase.getStatus()
                                == CollectionCase.CollectionStatus.RESOLVED

                        || collectionCase.getStatus()
                                == CollectionCase.CollectionStatus.WRITTEN_OFF
                )) {

                    continue;
                }


                /*
                 * CREATE NEW CASE
                 */
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
                                    .daysPastDue(daysPastDue)
                                    .overdueAmount(
                                            loan.getOutstandingBalance()
                                    )
                                    .totalOutstanding(
                                            loan.getOutstandingBalance()
                                    )
                                    .build();

                }

                /*
                 * UPDATE EXISTING CASE
                 */
                else {

                    collectionCase.setBucket(bucket);

                    /*
                     * Only automatically update priority while the
                     * case is still in active collections.
                     */
                    if (
                            collectionCase.getStatus()
                                    != CollectionCase.CollectionStatus.ESCALATED
                                    &&
                                    collectionCase.getStatus()
                                            != CollectionCase.CollectionStatus.LEGAL
                    ) {

                        collectionCase.setPriority(
                                priorityFor(bucket)
                        );
                    }


                    collectionCase.setDaysPastDue(
                            daysPastDue
                    );


                    if (loan.getOutstandingBalance() != null) {

                        collectionCase.setOverdueAmount(
                                loan.getOutstandingBalance()
                        );

                        collectionCase.setTotalOutstanding(
                                loan.getOutstandingBalance()
                        );
                    }
                }


                collectionCase =
                        caseRepo.save(collectionCase);


                /*
                 * Automatically create the first collection action.
                 */
                if (isNew) {

                    logAction(
                            collectionCase.getId(),
                            CollectionAction.ActionType.CASE_OPENED,
                            "Automatically opened because loan is "
                                    + daysPastDue
                                    + " day(s) past due.",
                            "SYSTEM",
                            "CASE OPENED",
                            null,
                            null
                    );


                    log.info(
                            "Collection case {} opened automatically for loan {}. DPD={}",
                            collectionCase.getId(),
                            loan.getId(),
                            daysPastDue
                    );
                }


                touched++;

            } catch (Exception ex) {

                log.error(
                        "Failed to synchronize collection case for loan {}",
                        loan.getId(),
                        ex
                );
            }
        }


        log.info(
                "Collection synchronization completed. {} cases touched.",
                touched
        );

        return touched;
    }


    // ============================================================
    // COLLECTION QUEUE
    // ============================================================

    /**
     * Returns collection queue for an organization.
     */
    @Transactional(readOnly = true)
    public List<CollectionCase> getQueue(
            Long orgId,
            CollectionCase.CollectionBucket bucket,
            CollectionCase.CollectionStatus status,
            Long agentId) {

        if (orgId == null) {
            return List.of();
        }


        List<CollectionCase> cases =
                caseRepo.findByOrganization_Id(orgId);


        if (cases == null || cases.isEmpty()) {
            return List.of();
        }


        return cases.stream()

                .filter(c -> c != null)

                .filter(c ->
                        bucket == null
                                || c.getBucket() == bucket
                )

                .filter(c ->
                        status == null
                                || c.getStatus() == status
                )

                .filter(c ->
                        agentId == null
                                ||
                                (
                                        c.getAssignedAgent() != null
                                                &&
                                                agentId.equals(
                                                        c.getAssignedAgent()
                                                                .getId()
                                                )
                                )
                )

                .sorted(
                        Comparator
                                .comparing(
                                        (CollectionCase c) ->
                                                c.getDaysPastDue() == null
                                                        ? 0
                                                        : c.getDaysPastDue()
                                )
                                .reversed()

                                .thenComparing(
                                        c ->
                                                priorityRank(
                                                        c.getPriority()
                                                ),
                                        Comparator.reverseOrder()
                                )

                                .thenComparing(
                                        c ->
                                                c.getOverdueAmount() == null
                                                        ? 0.0
                                                        : c.getOverdueAmount(),
                                        Comparator.reverseOrder()
                                )
                )

                .toList();
    }


    // ============================================================
    // GET CASE
    // ============================================================

    @Transactional(readOnly = true)
    public CollectionCase getCase(Long caseId) {

        if (caseId == null) {

            throw new IllegalArgumentException(
                    "Collection case ID is required"
            );
        }


        return caseRepo.findById(caseId)
                .orElseThrow(
                        () -> new RuntimeException(
                                "Collection case not found: "
                                        + caseId
                        )
                );
    }


    // ============================================================
    // ASSIGN AGENT
    // ============================================================

    @Transactional
    public CollectionCase assignAgent(
            Long caseId,
            Long agentUserId,
            String assignedBy) {

        CollectionCase collectionCase =
                getCase(caseId);


        if (agentUserId == null) {

            throw new IllegalArgumentException(
                    "Collection agent ID is required"
            );
        }


        User agent =
                userRepo.findById(agentUserId)
                        .orElseThrow(
                                () -> new RuntimeException(
                                        "Collection agent not found: "
                                                + agentUserId
                                )
                        );


        collectionCase.setAssignedAgent(agent);


        if (
                collectionCase.getStatus()
                        == CollectionCase.CollectionStatus.OPEN
        ) {

            collectionCase.setStatus(
                    CollectionCase.CollectionStatus.IN_PROGRESS
            );
        }


        collectionCase =
                caseRepo.save(collectionCase);


        auditService.log(
                collectionCase.getOrganization(),
                null,
                "COLLECTION_CASE_ASSIGNED",
                "COLLECTION_CASE",
                String.valueOf(caseId),
                "Assigned to "
                        + agent.getName()
                        + " by "
                        + (
                        assignedBy == null
                                ? "SYSTEM"
                                : assignedBy
                )
        );


        return collectionCase;
    }


    // ============================================================
    // LOG COLLECTION ACTION
    // ============================================================

    @Transactional
    public CollectionAction logAction(
            Long caseId,
            CollectionAction.ActionType type,
            String notes,
            String performedBy,
            String outcome,
            LocalDate promiseDate,
            Double promiseAmount) {


        if (caseId == null) {

            throw new IllegalArgumentException(
                    "Collection case ID is required"
            );
        }


        if (type == null) {

            throw new IllegalArgumentException(
                    "Collection action type is required"
            );
        }


        CollectionCase collectionCase =
                getCase(caseId);


        CollectionAction action =
                CollectionAction.builder()
                        .collectionCase(collectionCase)
                        .actionType(type)
                        .notes(notes)
                        .performedBy(
                                performedBy == null
                                        ? "SYSTEM"
                                        : performedBy
                        )
                        .outcome(outcome)
                        .promiseDate(promiseDate)
                        .promiseAmount(promiseAmount)
                        .build();


        action =
                actionRepo.save(action);


        /*
         * Contact actions update last contact date.
         */
        switch (type) {

            case CALL:
            case SMS:
            case EMAIL:
            case FIELD_VISIT:
            case LEGAL_NOTICE:
            case PROMISE_TO_PAY:
            case ESCALATED:

                collectionCase.setLastContactDate(
                        LocalDate.now()
                );

                break;

            default:
                break;
        }


        /*
         * PROMISE TO PAY
         */
        if (
                type
                        == CollectionAction.ActionType.PROMISE_TO_PAY
        ) {

            collectionCase.setStatus(
                    CollectionCase.CollectionStatus.PROMISE_TO_PAY
            );


            collectionCase.setPromiseToPayDate(
                    promiseDate
            );


            collectionCase.setPromiseToPayAmount(
                    promiseAmount
            );


            collectionCase.setNextActionDate(
                    promiseDate
            );
        }


        /*
         * ESCALATION
         */
        else if (
                type
                        == CollectionAction.ActionType.ESCALATED
        ) {

            collectionCase.setStatus(
                    CollectionCase.CollectionStatus.ESCALATED
            );


            if (
                    collectionCase.getNextActionDate()
                            == null
            ) {

                collectionCase.setNextActionDate(
                        LocalDate.now()
                );
            }
        }


        /*
         * LEGAL NOTICE
         */
        else if (
                type
                        == CollectionAction.ActionType.LEGAL_NOTICE
        ) {

            collectionCase.setStatus(
                    CollectionCase.CollectionStatus.LEGAL
            );


            if (
                    collectionCase.getNextActionDate()
                            == null
            ) {

                collectionCase.setNextActionDate(
                        LocalDate.now()
                );
            }
        }


        /*
         * PAYMENT RECEIVED
         */
        else if (
                type
                        == CollectionAction.ActionType.PAYMENT_RECEIVED
        ) {

            Loan loan =
                    collectionCase.getLoan();


            boolean cleared =
                    loan != null
                            &&
                            (
                                    loan.getOutstandingBalance() == null
                                            ||
                                            loan.getOutstandingBalance()
                                                    <= 0.01
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


                collectionCase.setResolutionNotes(
                        notes != null
                                ? notes
                                : "Loan fully cleared."
                );

            }

            else if (
                    collectionCase.getStatus()
                            == CollectionCase.CollectionStatus.PROMISE_TO_PAY
            ) {

                collectionCase.setStatus(
                        CollectionCase.CollectionStatus.IN_PROGRESS
                );
            }
        }


        /*
         * WRITE OFF
         */
        else if (
                type
                        == CollectionAction.ActionType.WRITE_OFF
        ) {

            collectionCase.setStatus(
                    CollectionCase.CollectionStatus.WRITTEN_OFF
            );


            collectionCase.setBucket(
                    CollectionCase.CollectionBucket.WRITE_OFF
            );


            collectionCase.setClosedAt(
                    LocalDateTime.now()
            );


            collectionCase.setNextActionDate(
                    null
            );


            collectionCase.setResolutionNotes(
                    notes
            );


            Loan loan =
                    collectionCase.getLoan();


            if (loan != null) {

                loan.setStatus(
                        LoanStatus.WRITTEN_OFF
                );


                loanRepo.save(loan);


                accountingService.postWriteOff(
                        loan
                );
            }
        }


        /*
         * CASE CLOSED
         */
        else if (
                type
                        == CollectionAction.ActionType.CASE_CLOSED
        ) {

            collectionCase.setStatus(
                    CollectionCase.CollectionStatus.RESOLVED
            );


            collectionCase.setClosedAt(
                    LocalDateTime.now()
            );


            collectionCase.setNextActionDate(
                    null
            );


            collectionCase.setResolutionNotes(
                    notes
            );
        }


        /*
         * NORMAL CONTACT ACTION
         */
        else if (
                type
                        == CollectionAction.ActionType.CALL

                        || type
                        == CollectionAction.ActionType.SMS

                        || type
                        == CollectionAction.ActionType.EMAIL

                        || type
                        == CollectionAction.ActionType.FIELD_VISIT
        ) {

            if (
                    collectionCase.getStatus()
                            == CollectionCase.CollectionStatus.OPEN
            ) {

                collectionCase.setStatus(
                        CollectionCase.CollectionStatus.IN_PROGRESS
                );
            }
        }


        caseRepo.save(collectionCase);


        auditService.log(
                collectionCase.getOrganization(),
                null,
                "COLLECTION_ACTION_" + type,
                "COLLECTION_CASE",
                String.valueOf(caseId),
                type
                        + " logged by "
                        + (
                        performedBy == null
                                ? "SYSTEM"
                                : performedBy
                )
                        + (
                        notes != null
                                && !notes.isBlank()
                                ? ": " + notes
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
            Long caseId) {

        if (caseId == null) {
            return List.of();
        }


        return actionRepo
                .findByCollectionCase_IdOrderByCreatedAtDesc(
                        caseId
                );
    }


    // ============================================================
    // COLLECTION STATISTICS
    // ============================================================

    @Transactional(readOnly = true)
    public Map<String, Object> getStats(
            Long orgId) {

        if (orgId == null) {
            return emptyStats();
        }


        List<CollectionCase> cases =
                caseRepo.findByOrganization_Id(orgId);


        if (cases == null) {
            cases = List.of();
        }


        Map<String, Long> byBucket =
                new LinkedHashMap<>();


        Map<String, Double> amountByBucket =
                new LinkedHashMap<>();


        for (
                CollectionCase.CollectionBucket bucket
                        : CollectionCase.CollectionBucket.values()
        ) {

            byBucket.put(
                    bucket.name(),
                    0L
            );


            amountByBucket.put(
                    bucket.name(),
                    0.0
            );
        }


        long openCases = 0;
        long activePromises = 0;
        long escalatedCases = 0;
        long legalCases = 0;
        long resolvedCases = 0;
        long writtenOffCases = 0;


        double totalOverdue = 0.0;


        long calls = 0;
        long sms = 0;
        long emails = 0;
        long fieldVisits = 0;
        long escalations = 0;
        long promisesToPay = 0;
        long paymentsReceived = 0;
        long legalNotices = 0;


        List<CollectionAction> allActions =
                new ArrayList<>();


        for (CollectionCase collectionCase : cases) {

            if (collectionCase == null) {
                continue;
            }


            CollectionCase.CollectionBucket bucket =
                    collectionCase.getBucket();


            if (bucket != null) {

                String bucketKey =
                        bucket.name();


                byBucket.merge(
                        bucketKey,
                        1L,
                        Long::sum
                );


                double amount =
                        collectionCase.getOverdueAmount() != null
                                ? collectionCase.getOverdueAmount()
                                : 0.0;


                amountByBucket.merge(
                        bucketKey,
                        amount,
                        Double::sum
                );


                if (
                        collectionCase.getStatus()
                                != CollectionCase.CollectionStatus.WRITTEN_OFF
                ) {

                    totalOverdue += amount;
                }
            }


            CollectionCase.CollectionStatus status =
                    collectionCase.getStatus();


            if (
                    status != CollectionCase.CollectionStatus.RESOLVED
                            &&
                            status
                                    != CollectionCase.CollectionStatus.WRITTEN_OFF
            ) {

                openCases++;
            }


            if (
                    status
                            == CollectionCase.CollectionStatus.PROMISE_TO_PAY
            ) {

                activePromises++;
            }


            if (
                    status
                            == CollectionCase.CollectionStatus.ESCALATED
            ) {

                escalatedCases++;
            }


            if (
                    status
                            == CollectionCase.CollectionStatus.LEGAL
            ) {

                legalCases++;
            }


            if (
                    status
                            == CollectionCase.CollectionStatus.RESOLVED
            ) {

                resolvedCases++;
            }


            if (
                    status
                            == CollectionCase.CollectionStatus.WRITTEN_OFF
            ) {

                writtenOffCases++;
            }


            /*
             * Retrieve action history.
             */
            List<CollectionAction> actions =
                    actionRepo
                            .findByCollectionCase_IdOrderByCreatedAtDesc(
                                    collectionCase.getId()
                            );


            if (
                    actions != null
                            && !actions.isEmpty()
            ) {

                allActions.addAll(actions);
            }
        }


        /*
         * Count action types.
         */
        for (CollectionAction action : allActions) {

            if (
                    action == null
                            || action.getActionType() == null
            ) {

                continue;
            }


            switch (action.getActionType()) {

                case CALL:
                    calls++;
                    break;

                case SMS:
                    sms++;
                    break;

                case EMAIL:
                    emails++;
                    break;

                case FIELD_VISIT:
                    fieldVisits++;
                    break;

                case ESCALATED:
                    escalations++;
                    break;

                case PROMISE_TO_PAY:
                    promisesToPay++;
                    break;

                case PAYMENT_RECEIVED:
                    paymentsReceived++;
                    break;

                case LEGAL_NOTICE:
                    legalNotices++;
                    break;

                default:
                    break;
            }
        }


        Map<String, Object> stats =
                new LinkedHashMap<>();


        stats.put(
                "casesByBucket",
                byBucket
        );


        stats.put(
                "overdueAmountByBucket",
                amountByBucket
        );


        stats.put(
                "totalOpenCases",
                openCases
        );


        stats.put(
                "totalOverdueAmount",
                totalOverdue
        );


        stats.put(
                "activePromises",
                activePromises
        );


        stats.put(
                "totalCases",
                cases.size()
        );


        stats.put(
                "escalatedCases",
                escalatedCases
        );


        stats.put(
                "legalCases",
                legalCases
        );


        stats.put(
                "resolvedCases",
                resolvedCases
        );


        stats.put(
                "writtenOffCases",
                writtenOffCases
        );


        /*
         * Action statistics.
         */
        stats.put(
                "calls",
                calls
        );


        stats.put(
                "sms",
                sms
        );


        stats.put(
                "emails",
                emails
        );


        stats.put(
                "fieldVisits",
                fieldVisits
        );


        stats.put(
                "escalations",
                escalations
        );


        stats.put(
                "promisesToPay",
                promisesToPay
        );


        stats.put(
                "paymentsReceived",
                paymentsReceived
        );


        stats.put(
                "legalNotices",
                legalNotices
        );


        stats.put(
                "totalActions",
                allActions.size()
        );


        return stats;
    }


    // ============================================================
    // EMPTY STATISTICS
    // ============================================================

    private Map<String, Object> emptyStats() {

        Map<String, Long> byBucket =
                new LinkedHashMap<>();


        Map<String, Double> amountByBucket =
                new LinkedHashMap<>();


        for (
                CollectionCase.CollectionBucket bucket
                        : CollectionCase.CollectionBucket.values()
        ) {

            byBucket.put(
                    bucket.name(),
                    0L
            );


            amountByBucket.put(
                    bucket.name(),
                    0.0
            );
        }


        Map<String, Object> stats =
                new LinkedHashMap<>();


        stats.put(
                "casesByBucket",
                byBucket
        );


        stats.put(
                "overdueAmountByBucket",
                amountByBucket
        );


        stats.put(
                "totalOpenCases",
                0L
        );


        stats.put(
                "totalOverdueAmount",
                0.0
        );


        stats.put(
                "activePromises",
                0L
        );


        stats.put(
                "totalCases",
                0
        );


        stats.put(
                "escalatedCases",
                0L
        );


        stats.put(
                "legalCases",
                0L
        );


        stats.put(
                "resolvedCases",
                0L
        );


        stats.put(
                "writtenOffCases",
                0L
        );


        stats.put(
                "calls",
                0L
        );


        stats.put(
                "sms",
                0L
        );


        stats.put(
                "emails",
                0L
        );


        stats.put(
                "fieldVisits",
                0L
        );


        stats.put(
                "escalations",
                0L
        );


        stats.put(
                "promisesToPay",
                0L
        );


        stats.put(
                "paymentsReceived",
                0L
        );


        stats.put(
                "legalNotices",
                0L
        );


        stats.put(
                "totalActions",
                0L
        );


        return stats;
    }


    // ============================================================
    // BUCKET
    // ============================================================

    private CollectionCase.CollectionBucket bucketFor(
            int daysPastDue) {

        if (daysPastDue <= 0) {

            return CollectionCase.CollectionBucket.CURRENT;
        }


        if (daysPastDue <= 30) {

            return CollectionCase.CollectionBucket.DPD_1_30;
        }


        if (daysPastDue <= 60) {

            return CollectionCase.CollectionBucket.DPD_31_60;
        }


        if (daysPastDue <= 90) {

            return CollectionCase.CollectionBucket.DPD_61_90;
        }


        return CollectionCase.CollectionBucket.DPD_90_PLUS;
    }


    // ============================================================
    // PRIORITY
    // ============================================================

    private CollectionCase.Priority priorityFor(
            CollectionCase.CollectionBucket bucket) {

        if (bucket == null) {

            return CollectionCase.Priority.MEDIUM;
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
    // PRIORITY RANK
    // ============================================================

    private int priorityRank(
            CollectionCase.Priority priority) {

        if (priority == null) {
            return 0;
        }


        return switch (priority) {

            case LOW -> 1;

            case MEDIUM -> 2;

            case HIGH -> 3;

            case URGENT -> 4;
        };
    }
}