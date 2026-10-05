package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.WorkflowTaskResponse;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.model.WorkflowTask;
import com.patrick.fintech.loan_backend.repository.UserRepository;
import com.patrick.fintech.loan_backend.repository.WorkflowTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowTaskService {

    private final WorkflowTaskRepository taskRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final MailService mailService;
    private final SchedulerLockService schedulerLockService;

    @Transactional
    public void createDisbursementTasks(Loan loan) {
        if (loan == null || loan.getId() == null || loan.getOrganization() == null
                || loan.getOrganization().getId() == null) {
            throw new IllegalArgumentException("Approved loan and organization are required");
        }

        Long organizationId = loan.getOrganization().getId();
        List<User> owners = userRepository.findActiveBusinessOwners(organizationId);

        if (owners == null || owners.isEmpty()) {
            throw new IllegalStateException(
                    "Loan cannot be finalized as APPROVED because no active BUSINESS_OWNER is available to disburse it.");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dueAt = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);
        String reference = loan.getReferenceNumber() == null
                ? "Loan #" + loan.getId()
                : loan.getReferenceNumber();

        String title = "Disbursement required — " + reference;
        String message = "Loan " + reference
                + " has been approved. BUSINESS_OWNER action is required to disburse the approved funds.";

        for (User owner : owners) {
            if (owner == null || owner.getId() == null) continue;

            String taskKey = WorkflowTask.TYPE_LOAN_DISBURSEMENT + ":" + loan.getId();

            WorkflowTask task = taskRepository
                    .findByOrganization_IdAndAssignedTo_IdAndTaskKey(
                            organizationId, owner.getId(), taskKey)
                    .orElseGet(() -> WorkflowTask.builder()
                            .organization(loan.getOrganization())
                            .assignedTo(owner)
                            .taskKey(taskKey)
                            .taskType(WorkflowTask.TYPE_LOAN_DISBURSEMENT)
                            .title(title)
                            .message(message)
                            .link("/dashboard/loans/" + loan.getId())
                            .priority(WorkflowTask.PRIORITY_HIGH)
                            .status(WorkflowTask.STATUS_OPEN)
                            .referenceType("LOAN")
                            .referenceId(loan.getId())
                            .dueAt(dueAt)
                            .createdAt(now)
                            .updatedAt(now)
                            .build());

            boolean newlyCreated = task.getId() == null;
            taskRepository.save(task);

            if (newlyCreated) {
                notificationService.notifyUsers(
                        List.of(owner),
                        title,
                        message,
                        "TASK",
                        "/dashboard/loans/" + loan.getId());
            }
        }
    }

    @Transactional(readOnly = true)
    public List<WorkflowTaskResponse> getMyTasks(User actor) {
        requireActor(actor);
        return taskRepository.findByAssignedTo_IdOrderByStatusAscDueAtAscCreatedAtAsc(actor.getId())
                .stream()
                .map(WorkflowTaskResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TaskKpi getMyKpi(User actor) {
        requireActor(actor);
        long open = taskRepository.countByAssignedTo_IdAndStatus(
                actor.getId(), WorkflowTask.STATUS_OPEN);
        long overdue = taskRepository.countByAssignedTo_IdAndStatusAndDueAtBefore(
                actor.getId(), WorkflowTask.STATUS_OPEN, LocalDateTime.now());
        LocalDate today = LocalDate.now();
        long dueToday = taskRepository.countByAssignedTo_IdAndStatusAndDueAtBetween(
                actor.getId(), WorkflowTask.STATUS_OPEN,
                today.atStartOfDay(), today.atTime(LocalTime.MAX));
        long highPriority = taskRepository.countByAssignedTo_IdAndStatusAndPriority(
                actor.getId(), WorkflowTask.STATUS_OPEN, WorkflowTask.PRIORITY_HIGH);
        return new TaskKpi(open, overdue, dueToday, highPriority);
    }

    @Transactional
    public void completeLoanDisbursementTasks(Loan loan, User completedBy) {
        if (loan == null || loan.getOrganization() == null || loan.getOrganization().getId() == null) return;
        if (completedBy == null || completedBy.getOrganization() == null
                || !loan.getOrganization().getId().equals(completedBy.getOrganization().getId())) {
            throw new AccessDeniedException("Task completion organization mismatch");
        }
        taskRepository.completeOpenTasksForLoan(
                loan.getOrganization().getId(),
                loan.getId(),
                WorkflowTask.TYPE_LOAN_DISBURSEMENT,
                WorkflowTask.STATUS_OPEN,
                WorkflowTask.STATUS_COMPLETED,
                LocalDateTime.now(),
                completedBy.getId());
    }

    /**
     * Approval does not wait for the external mail provider. The task itself is
     * durable and this dispatcher retries unsuccessful email delivery.
     */
    @Scheduled(fixedDelayString = "\${app.workflow-task.email-scan-ms:30000}")
    public void deliverPendingTaskEmails() {
        if (!schedulerLockService.tryAcquire(
                "workflow-task-email-delivery",
                Duration.ofMinutes(5))) {
            return;
        }

        try {
            List<WorkflowTask> tasks = taskRepository
                    .findTop50ByStatusAndEmailSentAtIsNullOrderByCreatedAtAsc(
                            WorkflowTask.STATUS_OPEN);

            if (tasks == null || tasks.isEmpty()) return;

            for (WorkflowTask task : tasks) {
                try {
                    deliverSingleTaskEmail(task.getId());
                } catch (Exception e) {
                    log.error(
                            "Workflow task email delivery failed. taskId={}, error={}",
                            task.getId(),
                            e.getMessage(),
                            e);
                }
            }
        } finally {
            schedulerLockService.release("workflow-task-email-delivery");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliverSingleTaskEmail(Long taskId) {
        WorkflowTask task = taskRepository.findByIdForUpdate(taskId).orElse(null);

        if (task == null
                || !WorkflowTask.STATUS_OPEN.equalsIgnoreCase(task.getStatus())
                || task.getEmailSentAt() != null
                || task.getAssignedTo() == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        int attempts = task.getEmailAttempts() == null ? 0 : task.getEmailAttempts();
        task.setEmailAttempts(attempts + 1);
        task.setLastEmailAttemptAt(now);
        task.setLastEmailError(null);
        taskRepository.save(task);

        try {
            boolean sent = mailService.sendWorkflowTaskAssigned(task.getAssignedTo(), task);
            if (sent) {
                task.setEmailSentAt(LocalDateTime.now());
                task.setLastEmailError(null);
            } else {
                task.setLastEmailError("Email provider rejected or was not configured");
            }
        } catch (Exception e) {
            String error = e.getMessage();
            task.setLastEmailError(
                    error == null || error.isBlank()
                            ? "Email delivery failed"
                            : error.substring(0, Math.min(1000, error.length())));
        }

        taskRepository.save(task);
    }

    private void requireActor(User actor) {
        if (actor == null || actor.getId() == null) {
            throw new AccessDeniedException("Authenticated user is required");
        }
    }

    public record TaskKpi(long open, long overdue, long dueToday, long highPriority) {}
}
