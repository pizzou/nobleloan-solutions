package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.WorkflowTask;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowTaskRepository extends JpaRepository<WorkflowTask, Long> {

    Optional<WorkflowTask> findByOrganization_IdAndAssignedTo_IdAndTaskKey(
            Long organizationId,
            Long assignedToId,
            String taskKey);

    List<WorkflowTask> findByAssignedTo_IdOrderByStatusAscDueAtAscCreatedAtAsc(
            Long assignedToId);

    long countByAssignedTo_IdAndStatus(Long assignedToId, String status);

    long countByAssignedTo_IdAndStatusAndDueAtBefore(
            Long assignedToId,
            String status,
            LocalDateTime cutoff);

    long countByAssignedTo_IdAndStatusAndDueAtBetween(
            Long assignedToId,
            String status,
            LocalDateTime from,
            LocalDateTime to);

    long countByAssignedTo_IdAndStatusAndPriority(
            Long assignedToId,
            String status,
            String priority);

    List<WorkflowTask> findTop50ByStatusAndEmailSentAtIsNullOrderByCreatedAtAsc(
            String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from WorkflowTask t where t.id = :id")
    Optional<WorkflowTask> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    @Transactional
    @Query("""
            update WorkflowTask t
               set t.status = :completedStatus,
                   t.completedAt = :completedAt,
                   t.completedBy = :completedBy,
                   t.updatedAt = :completedAt
             where t.organization.id = :organizationId
               and t.referenceType = 'LOAN'
               and t.referenceId = :loanId
               and t.taskType = :taskType
               and t.status = :openStatus
            """)
    int completeOpenTasksForLoan(
            @Param("organizationId") Long organizationId,
            @Param("loanId") Long loanId,
            @Param("taskType") String taskType,
            @Param("openStatus") String openStatus,
            @Param("completedStatus") String completedStatus,
            @Param("completedAt") LocalDateTime completedAt,
            @Param("completedBy") Long completedBy);
}
