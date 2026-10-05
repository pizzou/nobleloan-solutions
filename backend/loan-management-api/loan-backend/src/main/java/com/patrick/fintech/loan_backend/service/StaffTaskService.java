package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.StaffTaskDashboardResponse;
import com.patrick.fintech.loan_backend.dto.StaffTaskResponse;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.model.StaffTask;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.StaffTaskRepository;
import com.patrick.fintech.loan_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class StaffTaskService {

    private static final List<String> OPEN_STATUSES = List.of("OPEN", "CLAIMED");
    private static final String LOAN_DISBURSEMENT = "LOAN_DISBURSEMENT";
    private static final String LOAN = "LOAN";
    private static final String BUSINESS_OWNER = "BUSINESS_OWNER";

    private final StaffTaskRepository taskRepository;
    private final UserRepository userRepository;
    private final LoanRepository loanRepository;
    private final NotificationService notificationService;
    private final MailService mailService;
    private final AuditService auditService;

    @Transactional
    public StaffTask createLoanDisbursementTask(Loan loan, User createdBy) {
        if (loan == null || loan.getId() == null) {
            throw new IllegalArgumentException("Approved loan is required.");
        }
        if (loan.getOrganization() == null || loan.getOrganization().getId() == null) {
            throw new IllegalStateException("Approved loan has no organization.");
        }
        if (loan.getStatus() != LoanStatus.APPROVED) {
            throw new IllegalStateException("Disbursement task may only be created for an APPROVED loan.");
        }

        Long organizationId = loan.getOrganization().getId();

        List<StaffTask> existing = taskRepository.findOpenForEntity(
                organizationId, LOAN_DISBURSEMENT, LOAN, loan.getId(), OPEN_STATUSES);

        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        StaffTask task = StaffTask.builder()
                .organization(loan.getOrganization())
                .assignedRole(BUSINESS_OWNER)
                .taskType(LOAN_DISBURSEMENT)
                .entityType(LOAN)
                .entityId(loan.getId())
                .reference(loan.getReferenceNumber())
                .title("Disbursement required")
                .description(
                        "Loan " + safe(loan.getReferenceNumber())
                                + " has been approved. A Business Owner must complete the disbursement. "
                                + "Review the approved amount, KYC/AML clearance, repayment schedule, "
                                + "and payment destination before disbursing.")
                .priority("HIGH")
                .status("OPEN")
                .dueAt(LocalDateTime.now().plusHours(4))
                .createdBy(createdBy)
                .reminderCount(0)
                .build();

        StaffTask saved = taskRepository.save(task);

        scheduleNotification(saved);

        return saved;
    }

    @Transactional(readOnly = true)
    public StaffTaskDashboardResponse getDashboard(User actor) {
        requireActor(actor);

        Long orgId = actor.getOrganization().getId();
        String role = normalizedRole(actor);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = start.plusDays(1);

        List<StaffTask> tasks = taskRepository.findMyOpenTasks(
                orgId, actor.getId(), role, OPEN_STATUSES);

        return new StaffTaskDashboardResponse(
                taskRepository.countOpenForUser(orgId, actor.getId(), role),
                taskRepository.countDueTodayForUser(orgId, actor.getId(), role, start, end),
                taskRepository.countOverdueForUser(orgId, actor.getId(), role, now),
                taskRepository.countAwaitingDisbursementForUser(orgId, actor.getId(), role),
                tasks.stream().limit(12).map(t -> toResponse(t, now)).toList());
    }

    @Transactional(readOnly = true)
    public List<StaffTaskResponse> getMyTasks(User actor) {
        requireActor(actor);
        LocalDateTime now = LocalDateTime.now();
        return taskRepository.findMyOpenTasks(
                        actor.getOrganization().getId(),
                        actor.getId(),
                        normalizedRole(actor),
                        OPEN_STATUSES)
                .stream()
                .map(task -> toResponse(task, now))
                .toList();
    }

    @Transactional
    public StaffTaskResponse claim(Long id, User actor) {
        requireActor(actor);

        StaffTask task = taskRepository.findByIdForUpdate(
                        id, actor.getOrganization().getId())
                .orElseThrow(() -> new IllegalArgumentException("Task not found."));

        if (!OPEN_STATUSES.contains(task.getStatus())) {
            throw new IllegalStateException("Task is no longer open.");
        }

        if (task.getAssignee() != null
                && !task.getAssignee().getId().equals(actor.getId())) {
            throw new AccessDeniedException("This task has already been claimed by another user.");
        }

        String role = normalizedRole(actor);

        if (task.getAssignee() == null) {
            if (task.getAssignedRole() == null
                    || !role.equalsIgnoreCase(task.getAssignedRole())) {
                throw new AccessDeniedException("This task is not assigned to your role.");
            }

            task.setAssignee(actor);
            task.setClaimedAt(LocalDateTime.now());
            task.setStatus("CLAIMED");

            StaffTask saved = taskRepository.save(task);

            auditService.log(
                    actor.getOrganization(),
                    actor,
                    "STAFF_TASK_CLAIMED",
                    "TASK",
                    String.valueOf(saved.getId()),
                    "Claimed task " + saved.getTitle(),
                    null,
                    null,
                    "Task Management");

            return toResponse(saved, LocalDateTime.now());
        }

        return toResponse(task, LocalDateTime.now());
    }

    @Transactional
    public StaffTaskResponse complete(Long id, User actor) {
        requireActor(actor);

        StaffTask task = taskRepository.findByIdForUpdate(
                        id, actor.getOrganization().getId())
                .orElseThrow(() -> new IllegalArgumentException("Task not found."));

        if (!OPEN_STATUSES.contains(task.getStatus())) {
            throw new IllegalStateException("Task is no longer open.");
        }

        if (task.getAssignee() == null) {
            throw new IllegalStateException("Claim this task before completing it.");
        }

        if (!task.getAssignee().getId().equals(actor.getId())) {
            throw new AccessDeniedException("Only the assigned user can complete this task.");
        }

        if (LOAN_DISBURSEMENT.equals(task.getTaskType())) {
            Loan loan = loanRepository.findById(task.getEntityId())
                    .orElseThrow(() -> new IllegalStateException("Linked loan no longer exists."));

            if (loan.getStatus() != LoanStatus.ACTIVE) {
                throw new IllegalStateException(
                        "Disbursement task completes automatically after successful financial disbursement.");
            }
        }

        task.setStatus("COMPLETED");
        task.setCompletedAt(LocalDateTime.now());

        StaffTask saved = taskRepository.save(task);

        auditService.log(
                actor.getOrganization(),
                actor,
                "STAFF_TASK_COMPLETED",
                "TASK",
                String.valueOf(saved.getId()),
                "Completed task " + saved.getTitle(),
                null,
                null,
                "Task Management");

        return toResponse(saved, LocalDateTime.now());
    }

    @Transactional
    public void cancelOpenTasksForEntity(
            Long organizationId,
            String entityType,
            Long entityId,
            String reason,
            User actor) {

        if (organizationId == null || entityId == null || entityType == null || entityType.isBlank()) {
            return;
        }

        List<StaffTask> tasks = taskRepository.findOpenByEntity(
                organizationId,
                entityType,
                entityId,
                OPEN_STATUSES);

        if (tasks.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        String message = reason == null || reason.isBlank()
                ? "Underlying entity is no longer operationally active."
                : reason.trim();

        for (StaffTask task : tasks) {
            task.setStatus("CANCELLED");
            task.setCancelledAt(now);
            task.setDescription(
                    safe(task.getDescription())
                            + " [Cancelled: " + message + "]");
            taskRepository.save(task);
        }

        if (actor != null && actor.getOrganization() != null) {
            auditService.log(
                    actor.getOrganization(),
                    actor,
                    "STAFF_TASK_CANCELLED",
                    entityType,
                    String.valueOf(entityId),
                    "Cancelled " + tasks.size() + " open staff task(s): " + message,
                    null,
                    null,
                    "Task Management");
        }
    }

    @Transactional
    public void completeLoanDisbursementTask(Long loanId, User actor) {
        if (loanId == null || actor == null || actor.getOrganization() == null
                || actor.getOrganization().getId() == null) {
            return;
        }

        List<StaffTask> tasks = taskRepository.findOpenForEntity(
                actor.getOrganization().getId(),
                LOAN_DISBURSEMENT,
                LOAN,
                loanId,
                OPEN_STATUSES);

        if (tasks.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        for (StaffTask task : tasks) {
            task.setStatus("COMPLETED");
            task.setCompletedAt(now);
            taskRepository.save(task);
        }

        auditService.log(
                actor.getOrganization(),
                actor,
                "STAFF_TASK_AUTO_COMPLETED",
                "LOAN",
                String.valueOf(loanId),
                "Disbursement task completed automatically after successful financial disbursement.",
                null,
                null,
                "Task Management");
    }

    /**
     * Repairs approved loans that existed before the task workflow was deployed
     * or whose task creation previously failed. The SQL query is bounded so a
     * deployment cannot load the whole portfolio.
     */
    @Transactional
    public void ensureApprovedLoanTasks() {
        List<Long> loanIds = taskRepository.findApprovedLoanIdsMissingDisbursementTasks();

        for (Long loanId : loanIds) {
            try {
                loanRepository.findById(loanId).ifPresent(loan -> {
                    try {
                        createLoanDisbursementTask(loan, null);
                    } catch (Exception e) {
                        log.error(
                                "Failed to backfill disbursement task for approved loan {}",
                                loanId,
                                e);
                    }
                });
            } catch (Exception e) {
                log.error("Failed to load approved loan {} for task backfill", loanId, e);
            }
        }
    }

    @Transactional
    public void sendDueReminders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();

        List<StaffTask> dueTasks = taskRepository.findTasksDueForReminder(
                now.plusDays(1), dayStart);

        for (StaffTask task : dueTasks) {
            try {
                List<User> recipients = recipientsFor(task);
                if (recipients.isEmpty()) {
                    continue;
                }

                boolean overdue = task.getDueAt() != null
                        && task.getDueAt().isBefore(now);

                String title = overdue
                        ? "Overdue task — action required"
                        : "Task due — action required";

                String message = overdue
                        ? task.getTitle() + ": " + safe(task.getDescription())
                            + " The task passed its due time and requires immediate action."
                        : task.getTitle() + ": " + safe(task.getDescription());

                notificationService.notifyUsers(
                        recipients,
                        title,
                        message,
                        "TASK_REMINDER",
                        actionLink(task));

                for (User recipient : recipients) {
                    mailService.sendStaffTaskReminder(recipient, task, overdue);
                }

                task.setLastReminderAt(now);
                task.setReminderCount(
                        (task.getReminderCount() == null ? 0 : task.getReminderCount()) + 1);
                taskRepository.save(task);

            } catch (Exception e) {
                log.error(
                        "Failed to send reminder for staff task {}",
                        task.getId(),
                        e);
            }
        }
    }

    private void scheduleNotification(StaffTask task) {
        Runnable action = () -> {
            try {
                List<User> recipients = recipientsFor(task);

                if (recipients.isEmpty()) {
                    log.warn(
                            "No active recipient found for staff task {} type={}",
                            task.getId(),
                            task.getTaskType());
                    return;
                }

                notificationService.notifyUsers(
                        recipients,
                        task.getTitle(),
                        safe(task.getDescription()),
                        "TASK_ASSIGNED",
                        actionLink(task));

                for (User recipient : recipients) {
                    mailService.sendStaffTaskAssigned(recipient, task);
                }

            } catch (Exception e) {
                log.error(
                        "Task notification failed for staff task {}",
                        task.getId(),
                        e);
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            action.run();
                        }
                    });
        } else {
            action.run();
        }
    }

    private List<User> recipientsFor(StaffTask task) {
        if (task.getAssignee() != null) {
            return List.of(task.getAssignee());
        }

        if (task.getAssignedRole() == null || task.getOrganization() == null) {
            return List.of();
        }

        return userRepository.findByOrganization_IdAndRole_NameAndStatus(
                task.getOrganization().getId(),
                task.getAssignedRole(),
                User.UserStatus.ACTIVE);
    }

    private StaffTaskResponse toResponse(StaffTask task, LocalDateTime now) {
        boolean overdue = task.getDueAt() != null
                && task.getDueAt().isBefore(now)
                && OPEN_STATUSES.contains(task.getStatus());

        return new StaffTaskResponse(
                task.getId(),
                task.getTaskType(),
                task.getEntityType(),
                task.getEntityId(),
                task.getReference(),
                task.getTitle(),
                task.getDescription(),
                task.getPriority(),
                task.getStatus(),
                task.getAssignedRole(),
                task.getAssignee() != null ? task.getAssignee().getId() : null,
                task.getAssignee() != null ? task.getAssignee().getName() : null,
                task.getDueAt(),
                task.getCreatedAt(),
                task.getClaimedAt(),
                overdue,
                actionLink(task));
    }

    private String actionLink(StaffTask task) {
        if (LOAN.equals(task.getEntityType()) && task.getEntityId() != null) {
            return "/dashboard/loans/" + task.getEntityId();
        }
        return "/dashboard/notifications";
    }

    private void requireActor(User actor) {
        if (actor == null
                || actor.getOrganization() == null
                || actor.getOrganization().getId() == null
                || actor.getId() == null) {
            throw new AccessDeniedException("Authenticated staff user is required.");
        }
    }

    private String normalizedRole(User user) {
        String role = user.getRole() == null ? "" : user.getRole().getName();
        role = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        return role.startsWith("ROLE_") ? role.substring(5) : role;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
