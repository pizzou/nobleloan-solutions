package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.event.WorkflowTaskAssignedEvent;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class WorkflowTaskNotificationListener {

    private static final DateTimeFormatter DUE = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final MailService mailService;

    @Async("mailAsyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskAssigned(WorkflowTaskAssignedEvent event) {
        try {
            User user = userRepository.findByIdAndOrganizationId(
                    event.assigneeId(), event.organizationId()).orElse(null);
            if (user == null || user.getStatus() != User.UserStatus.ACTIVE) {
                log.warn("Task assignment notification skipped: inactive/missing assignee taskId={}", event.taskId());
                return;
            }

            String dueText = event.dueAt() == null ? "No due date" : event.dueAt().format(DUE);
            String message = event.referenceNumber() == null || event.referenceNumber().isBlank()
                    ? event.title()
                    : event.title() + " (" + event.referenceNumber() + ")";

            notificationService.notifyUsers(
                    List.of(user),
                    "Action required",
                    message + ". Due: " + dueText,
                    "WORKFLOW_TASK",
                    event.link());

            mailService.sendWorkflowTaskAssigned(
                    event.assigneeEmail(),
                    event.assigneeName(),
                    event.title(),
                    event.description(),
                    event.taskType(),
                    event.referenceNumber(),
                    event.dueAt(),
                    event.link());
        } catch (Exception e) {
            // Task persistence is the source of truth. Notification failure must
            // never roll back the already-committed work item.
            log.error("Workflow task notification failed for taskId={}: {}",
                    event.taskId(), e.getMessage(), e);
        }
    }
}
