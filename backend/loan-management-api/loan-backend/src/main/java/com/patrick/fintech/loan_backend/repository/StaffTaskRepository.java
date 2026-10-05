package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.StaffTask;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface StaffTaskRepository extends JpaRepository<StaffTask, Long> {

    @EntityGraph(attributePaths = {"assignee", "createdBy", "organization"})
    @Query("""
        SELECT t
        FROM StaffTask t
        WHERE t.organization.id = :organizationId
          AND t.status IN :statuses
          AND (
                t.assignee.id = :userId
                OR (
                    t.assignee IS NULL
                    AND UPPER(t.assignedRole) = UPPER(:role)
                )
              )
        ORDER BY
            CASE WHEN t.dueAt IS NULL THEN 1 ELSE 0 END,
            t.dueAt ASC,
            t.createdAt ASC
        """)
    List<StaffTask> findMyOpenTasks(
            @Param("organizationId") Long organizationId,
            @Param("userId") Long userId,
            @Param("role") String role,
            @Param("statuses") Collection<String> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"assignee", "createdBy", "organization"})
    @Query("""
        SELECT t
        FROM StaffTask t
        WHERE t.id = :id
          AND t.organization.id = :organizationId
        """)
    Optional<StaffTask> findByIdForUpdate(
            @Param("id") Long id,
            @Param("organizationId") Long organizationId);

    @EntityGraph(attributePaths = {"assignee", "createdBy", "organization"})
    @Query("""
        SELECT t
        FROM StaffTask t
        WHERE t.organization.id = :organizationId
          AND t.taskType = :taskType
          AND t.entityType = :entityType
          AND t.entityId = :entityId
          AND t.status IN :statuses
        ORDER BY t.createdAt DESC
        """)
    List<StaffTask> findOpenForEntity(
            @Param("organizationId") Long organizationId,
            @Param("taskType") String taskType,
            @Param("entityType") String entityType,
            @Param("entityId") Long entityId,
            @Param("statuses") Collection<String> statuses);

    @Query("""
        SELECT COUNT(t)
        FROM StaffTask t
        WHERE t.organization.id = :organizationId
          AND t.status IN ('OPEN','CLAIMED')
          AND (
                t.assignee.id = :userId
                OR (
                    t.assignee IS NULL
                    AND UPPER(t.assignedRole) = UPPER(:role)
                )
              )
        """)
    long countOpenForUser(
            @Param("organizationId") Long organizationId,
            @Param("userId") Long userId,
            @Param("role") String role);

    @Query("""
        SELECT COUNT(t)
        FROM StaffTask t
        WHERE t.organization.id = :organizationId
          AND t.status IN ('OPEN','CLAIMED')
          AND (
                t.assignee.id = :userId
                OR (
                    t.assignee IS NULL
                    AND UPPER(t.assignedRole) = UPPER(:role)
                )
              )
          AND t.dueAt >= :start
          AND t.dueAt < :end
        """)
    long countDueTodayForUser(
            @Param("organizationId") Long organizationId,
            @Param("userId") Long userId,
            @Param("role") String role,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("""
        SELECT COUNT(t)
        FROM StaffTask t
        WHERE t.organization.id = :organizationId
          AND t.status IN ('OPEN','CLAIMED')
          AND (
                t.assignee.id = :userId
                OR (
                    t.assignee IS NULL
                    AND UPPER(t.assignedRole) = UPPER(:role)
                )
              )
          AND t.dueAt IS NOT NULL
          AND t.dueAt < :now
        """)
    long countOverdueForUser(
            @Param("organizationId") Long organizationId,
            @Param("userId") Long userId,
            @Param("role") String role,
            @Param("now") LocalDateTime now);

    @Query("""
        SELECT COUNT(t)
        FROM StaffTask t
        WHERE t.organization.id = :organizationId
          AND t.status IN ('OPEN','CLAIMED')
          AND t.taskType = 'LOAN_DISBURSEMENT'
          AND (
                t.assignee.id = :userId
                OR (
                    t.assignee IS NULL
                    AND UPPER(t.assignedRole) = UPPER(:role)
                )
              )
        """)
    long countAwaitingDisbursementForUser(
            @Param("organizationId") Long organizationId,
            @Param("userId") Long userId,
            @Param("role") String role);

    @EntityGraph(attributePaths = {"assignee", "createdBy", "organization"})
    @Query("""
        SELECT t
        FROM StaffTask t
        WHERE t.status IN ('OPEN','CLAIMED')
          AND t.dueAt IS NOT NULL
          AND t.dueAt <= :upperBound
          AND (
                t.lastReminderAt IS NULL
                OR t.lastReminderAt < :dayStart
              )
        ORDER BY t.dueAt ASC, t.createdAt ASC
        """)
    List<StaffTask> findTasksDueForReminder(
            @Param("upperBound") LocalDateTime upperBound,
            @Param("dayStart") LocalDateTime dayStart);

    @Query(value = """
        SELECT l.id
        FROM loans l
        WHERE l.status = 'APPROVED'
          AND l.deleted_at IS NULL
          AND NOT EXISTS (
              SELECT 1
              FROM staff_tasks t
              WHERE t.organization_id = l.organization_id
                AND t.task_type = 'LOAN_DISBURSEMENT'
                AND t.entity_type = 'LOAN'
                AND t.entity_id = l.id
                AND t.status IN ('OPEN','CLAIMED')
          )
        ORDER BY l.id
        LIMIT 500
        """, nativeQuery = true)
    List<Long> findApprovedLoanIdsMissingDisbursementTasks();
}
