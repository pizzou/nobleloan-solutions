package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.WorkflowTask;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowTaskRepository extends JpaRepository<WorkflowTask, Long> {

    @EntityGraph(attributePaths = {"assignedTo", "assignedTo.role"})
    @Query("""
            select t from WorkflowTask t
            where t.organization.id = :organizationId
              and t.assignedTo.id = :assigneeId
              and t.status in :statuses
            order by
              case t.priority when 'URGENT' then 0 when 'HIGH' then 1 when 'NORMAL' then 2 else 3 end,
              case when t.dueAt is null then 1 else 0 end,
              t.dueAt asc,
              t.createdAt desc
            """)
    List<WorkflowTask> findMine(
            @Param("organizationId") Long organizationId,
            @Param("assigneeId") Long assigneeId,
            @Param("statuses") Collection<String> statuses,
            Pageable pageable);

    @EntityGraph(attributePaths = {"assignedTo", "assignedTo.role", "organization"})
    @Query("""
            select t from WorkflowTask t
            where t.id = :id
              and t.organization.id = :organizationId
              and t.assignedTo.id = :assigneeId
            """)
    Optional<WorkflowTask> findMineById(
            @Param("id") Long id,
            @Param("organizationId") Long organizationId,
            @Param("assigneeId") Long assigneeId);

    long countByOrganization_IdAndAssignedTo_IdAndStatusIn(
            Long organizationId,
            Long assigneeId,
            Collection<String> statuses);

    long countByOrganization_IdAndAssignedTo_IdAndStatusInAndDueAtBefore(
            Long organizationId,
            Long assigneeId,
            Collection<String> statuses,
            LocalDateTime dueAt);

    long countByOrganization_IdAndAssignedTo_IdAndPriorityAndStatusIn(
            Long organizationId,
            Long assigneeId,
            String priority,
            Collection<String> statuses);

    @Query("""
            select t from WorkflowTask t
            where t.organization.id = :organizationId
              and t.taskType = :taskType
              and t.entityType = :entityType
              and t.entityId = :entityId
              and t.assignedTo.id = :assigneeId
              and t.status in :statuses
            order by t.createdAt desc
            """)
    Optional<WorkflowTask> findOpenEntityTask(
            @Param("organizationId") Long organizationId,
            @Param("taskType") String taskType,
            @Param("entityType") String entityType,
            @Param("entityId") Long entityId,
            @Param("assigneeId") Long assigneeId,
            @Param("statuses") Collection<String> statuses);
}
