package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.WorkflowTaskCreateRequest;
import com.patrick.fintech.loan_backend.dto.WorkflowTaskKpiResponse;
import com.patrick.fintech.loan_backend.dto.WorkflowTaskResponse;
import com.patrick.fintech.loan_backend.event.WorkflowTaskAssignedEvent;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.model.WorkflowTask;
import com.patrick.fintech.loan_backend.repository.UserRepository;
import com.patrick.fintech.loan_backend.repository.WorkflowTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowTaskService {

    private static final Set<String> OPEN_STATUSES = Set.of("OPEN", "IN_PROGRESS");
    private static final Set<String> PRIORITIES = Set.of("LOW", "NORMAL", "HIGH", "URGENT");
    private static final DateTimeFormatter EMAIL_DATE = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    private final WorkflowTaskRepository taskRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<WorkflowTaskResponse> getMine(User actor, int limit) {
        Long organizationId = organizationId(actor);
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return taskRepository.findMine(
                organizationId,
                actor.getId(),
                OPEN_STATUSES,
                PageRequest.of(0, safeLimit))
                .stream()
                .map(task -> WorkflowTaskResponse.from(task, LocalDateTime.now()))
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkflowTaskKpiResponse getMineKpi(User actor) {
        Long organizationId = organizationId(actor);
        long open = taskRepository.countByOrganization_IdAndAssignedTo_IdAndStatusIn(
                organizationId, actor.getId(), OPEN_STATUSES);
        long overdue = taskRepository.countByOrganization_IdAndAssignedTo_IdAndStatusInAndDueAtBefore(
                organizationId, actor.getId(), OPEN_STATUSES, LocalDateTime.now());
        long urgent = taskRepository.countByOrganization_IdAndAssignedTo_IdAndPriorityAndStatusIn(
                organizationId, actor.getId(), "URGENT", OPEN_STATUSES);
        return new WorkflowTaskKpiResponse(open, overdue, urgent);
    }

    @Transactional
    public WorkflowTask createTask(WorkflowTaskCreateRequest request, User actor) {
        if (request == null) {
            throw new IllegalArgumentException("Task request is required");
        }
        requireCanAssign(actor);

        Long organizationId = organizationId(actor);
        if (request.getAssigneeId() == null || request.getAssigneeId() <= 0) {
            throw new IllegalArgumentException("A task assignee is required");
        }

        User assignee = userRepository.findByIdAndOrganizationId(
                request.getAssigneeId(), organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Assigned user was not found in this organization"));

        requireActiveAssignee(assignee);

        String title = normalizeRequired(request.getTitle(), "Task title", 180);
        String taskType = normalizeRequired(request.getTaskType(), "Task type", 60).toUpperCase(Locale.ROOT);
        String priority = normalizeRequired(
                request.getPriority() == null || request.getPriority().isBlank() ? "NORMAL" : request.getPriority(),
                "Priority", 20).toUpperCase(Locale.ROOT);
        if (!PRIORITIES.contains(priority)) {
            throw new IllegalArgumentException("Priority must be LOW, NORMAL, HIGH, or URGENT");
        }

        String entityType = normalizeOptional(request.getEntityType(), 40);
        if ((entityType == null) != (request.getEntityId() == null)) {
            throw new IllegalArgumentException("Entity type and entity id must be provided together");
        }

        WorkflowTask task = WorkflowTask.builder()
                .organization(actor.getOrganization())
                .assignedTo(assignee)
                .assignedBy(actor)
                .title(title)
                .description(normalizeOptional(request.getDescription(), 4000))
                .taskType(taskType)
                .entityType(entityType == null ? null : entityType.toUpperCase(Locale.ROOT))
                .entityId(request.getEntityId())
                .referenceNumber(normalizeOptional(request.getReferenceNumber(), 255))
                .link(normalizeOptional(request.getLink(), 500))
                .status("OPEN")
                .priority(priority)
                .dueAt(request.getDueAt())
                .build();

        WorkflowTask saved = taskRepository.save(task);
        auditService.log(
                saved.getOrganization(),
                actor,
                "WORKFLOW_TASK_ASSIGNED",
                "WORKFLOW_TASK",
                String.valueOf(saved.getId()),
                "Assigned task " + saved.getTitle() + " to user " + assignee.getId(),
                null,
                null,
                "Operations Tasks");
        publishAssignmentEvent(saved, request.getLink());
        return saved;
    }

    @Transactional
    public WorkflowTask createDisbursementTask(Loan approvedLoan, User actor) {
        if (approvedLoan == null || approvedLoan.getId() == null || approvedLoan.getOrganization() == null) {
            throw new IllegalArgumentException("An approved loan with organization is required");
        }
        if (approvedLoan.getStatus() == null || !"APPROVED".equalsIgnoreCase(approvedLoan.getStatus().name())) {
            throw new IllegalStateException("Disbursement task can only be created for an approved loan");
        }

        Long organizationId = approvedLoan.getOrganization().getId();
        User owner = userRepository.findFirstActiveByOrganizationAndRole(
                organizationId, "BUSINESS_OWNER")
                .orElseThrow(() -> new IllegalStateException(
                        "No active BUSINESS_OWNER is available to receive the disbursement task"));

        String taskType = "LOAN_DISBURSEMENT";
        String entityType = "LOAN";

        var existing = taskRepository.findOpenEntityTask(
                organizationId,
                taskType,
                entityType,
                approvedLoan.getId(),
                owner.getId(),
                OPEN_STATUSES);
        if (existing.isPresent()) {
            return existing.get();
        }

        BigDecimal amount = approvedLoan.getAmountDecimal() == null
                ? BigDecimal.ZERO
                : approvedLoan.getAmountDecimal();
        String currency = approvedLoan.getCurrency() == null ? "RWF" : approvedLoan.getCurrency();
        LocalDateTime dueAt = LocalDateTime.now().plusHours(24);

        WorkflowTask task = WorkflowTask.builder()
                .organization(approvedLoan.getOrganization())
                .assignedTo(owner)
                .assignedBy(actor)
                .title("Disburse approved loan " + approvedLoan.getReferenceNumber())
                .description("Approved principal " + currency + " " + amount
                        + ". Complete the controlled disbursement process after confirming all required controls and documents.")
                .taskType(taskType)
                .entityType(entityType)
                .entityId(approvedLoan.getId())
                .referenceNumber(approvedLoan.getReferenceNumber())
                .link("/dashboard/loans/" + approvedLoan.getId())
                .status("OPEN")
                .priority("HIGH")
                .dueAt(dueAt)
                .build();

        WorkflowTask saved = taskRepository.save(task);
        auditService.log(
                saved.getOrganization(),
                actor,
                "WORKFLOW_TASK_ASSIGNED",
                "WORKFLOW_TASK",
                String.valueOf(saved.getId()),
                "Created mandatory disbursement task for approved loan " + approvedLoan.getReferenceNumber(),
                null,
                null,
                "Operations Tasks");
        publishAssignmentEvent(saved, "/dashboard/loans/" + approvedLoan.getId());
        return saved;
    }

    /**
     * Closes the mandatory disbursement task as part of the same financial
     * transaction that moves the loan to ACTIVE. The task is an operational
     * acknowledgement, not a permission to disburse.
     */
    @Transactional
    public void completeDisbursementTaskForLoan(Long loanId, User owner) {
        if (loanId == null || loanId <= 0) {
            throw new IllegalArgumentException("Loan id is required");
        }
        Long organizationId = organizationId(owner);
        String role = owner.getRole() == null ? null : owner.getRole().getName();
        if (role == null || !"BUSINESS_OWNER".equalsIgnoreCase(role.trim())) {
            throw new AccessDeniedException("Only the BUSINESS_OWNER may complete disbursement tasks");
        }
        var existing = taskRepository.findOpenEntityTask(
                organizationId,
                "LOAN_DISBURSEMENT",
                "LOAN",
                loanId,
                owner.getId(),
                OPEN_STATUSES);
        if (existing.isEmpty()) {
            log.warn("No open disbursement task found for loan {} during successful disbursement", loanId);
            return;
        }

        WorkflowTask task = existing.get();
        LocalDateTime now = LocalDateTime.now();
        task.setStatus("COMPLETED");
        task.setCompletedAt(now);
        task.setCompletedBy(owner);
        WorkflowTask saved = taskRepository.save(task);
        auditService.log(
                saved.getOrganization(),
                owner,
                "WORKFLOW_TASK_COMPLETED",
                "WORKFLOW_TASK",
                String.valueOf(saved.getId()),
                "Auto-completed mandatory disbursement task after loan disbursement",
                null,
                null,
                "Operations Tasks");
    }

    @Transactional
    public WorkflowTask start(Long taskId, User actor) {
        WorkflowTask task = findMine(taskId, actor);
        if ("COMPLETED".equalsIgnoreCase(task.getStatus())) {
            return task;
        }
        if ("CANCELLED".equalsIgnoreCase(task.getStatus())) {
            throw new IllegalStateException("Cancelled task cannot be started");
        }
        if ("OPEN".equalsIgnoreCase(task.getStatus())) {
            task.setStatus("IN_PROGRESS");
            task.setStartedAt(LocalDateTime.now());
            WorkflowTask saved = taskRepository.save(task);
            auditService.log(
                    saved.getOrganization(),
                    actor,
                    "WORKFLOW_TASK_STARTED",
                    "WORKFLOW_TASK",
                    String.valueOf(saved.getId()),
                    "Started task " + saved.getTitle(),
                    null,
                    null,
                    "Operations Tasks");
            return saved;
        }
        return task;
    }

    @Transactional
    public WorkflowTask complete(Long taskId, User actor) {
        WorkflowTask task = findMine(taskId, actor);
        if ("COMPLETED".equalsIgnoreCase(task.getStatus())) {
            return task;
        }
        if ("CANCELLED".equalsIgnoreCase(task.getStatus())) {
            throw new IllegalStateException("Cancelled task cannot be completed");
        }
        LocalDateTime now = LocalDateTime.now();
        task.setStatus("COMPLETED");
        task.setCompletedAt(now);
        task.setCompletedBy(actor);
        WorkflowTask saved = taskRepository.save(task);
        auditService.log(
                saved.getOrganization(),
                actor,
                "WORKFLOW_TASK_COMPLETED",
                "WORKFLOW_TASK",
                String.valueOf(saved.getId()),
                "Completed task " + saved.getTitle(),
                null,
                null,
                "Operations Tasks");
        return saved;
    }

    private WorkflowTask findMine(Long taskId, User actor) {
        Long organizationId = organizationId(actor);
        if (taskId == null || taskId <= 0) {
            throw new IllegalArgumentException("Invalid task id");
        }
        return taskRepository.findMineById(taskId, organizationId, actor.getId())
                .orElseThrow(
                        () -> new AccessDeniedException("Task not found or not assigned to the authenticated user"));
    }

    private void publishAssignmentEvent(WorkflowTask task, String link) {
        User assignee = task.getAssignedTo();
        if (assignee == null) {
            return;
        }
        eventPublisher.publishEvent(new WorkflowTaskAssignedEvent(
                task.getId(),
                task.getOrganization().getId(),
                assignee.getId(),
                assignee.getName(),
                assignee.getEmail(),
                task.getTitle(),
                task.getDescription(),
                task.getTaskType(),
                task.getReferenceNumber(),
                task.getDueAt(),
                link));
    }

    private void requireCanAssign(User actor) {
        if (actor == null || actor.getRole() == null || actor.getRole().getName() == null) {
            throw new AccessDeniedException("Authenticated user is required");
        }
        String role = actor.getRole().getName().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("BUSINESS_OWNER", "ADMIN", "MANAGER").contains(role)) {
            throw new AccessDeniedException("Only BUSINESS_OWNER, ADMIN, or MANAGER can assign tasks");
        }
    }

    private void requireActiveAssignee(User assignee) {
        if (assignee.getStatus() != User.UserStatus.ACTIVE) {
            throw new IllegalStateException("Tasks can only be assigned to an active user");
        }
    }

    private Long organizationId(User actor) {
        if (actor == null || actor.getId() == null || actor.getOrganization() == null
                || actor.getOrganization().getId() == null) {
            throw new AccessDeniedException("Authenticated user has no valid organization");
        }
        return actor.getOrganization().getId();
    }

    private String normalizeRequired(String value, String label, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(label + " must be at most " + maxLength + " characters");
        }
        return normalized;
    }

    private String normalizeOptional(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("Value must be at most " + maxLength + " characters");
        }
        return normalized;
    }
}
