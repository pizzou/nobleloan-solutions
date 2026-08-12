package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Notification;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepo;

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Creates the persistent in-app notification AND
     * immediately pushes the same notification to the user's
     * connected dashboard.
     */
    public void notifyUsers(
            List<User> users,
            String title,
            String message,
            String type,
            String link
    ) {

        if (users == null || users.isEmpty()) {
            return;
        }

        for (User user : users) {

            if (user == null) {
                continue;
            }

            try {

                Notification notification =
                        Notification.builder()
                                .user(user)
                                .institution(user.getOrganization())
                                .title(title)
                                .message(message)
                                .type(type)
                                .link(link)
                                .build();

                /*
                 * 1. Persist notification.
                 */
                Notification saved =
                        notificationRepo.save(notification);

                /*
                 * 2. Push notification immediately
                 *    to this user's dashboard.
                 */
                if (user.getId() != null) {

                    String destination =
                            "/user/"
                                    + user.getId()
                                    + "/queue/notifications";

                    messagingTemplate.convertAndSend(
                            destination,
                            saved
                    );

                    log.info(
                            "Real-time notification sent. userId={}, type={}",
                            user.getId(),
                            type
                    );
                }

            } catch (Exception e) {

                log.warn(
                        "Notification failed for user {}: {}",
                        user.getId(),
                        e.getMessage(),
                        e
                );
            }
        }
    }
}