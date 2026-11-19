package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.Notification;
import com.patrick.fintech.loan_backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserAndReadFalseAndDismissedAtIsNullOrderByCreatedAtDesc(User user);
    long countByUserAndReadFalseAndDismissedAtIsNull(User user);
    List<Notification> findTop20ByUserAndDismissedAtIsNullOrderByCreatedAtDesc(User user);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.dismissedAt = :now, n.read = true, n.readAt = coalesce(n.readAt, :now) "
            + "where n.user = :user and n.dismissedAt is null")
    int dismissVisibleForUser(@Param("user") User user, @Param("now") LocalDateTime now);
}
