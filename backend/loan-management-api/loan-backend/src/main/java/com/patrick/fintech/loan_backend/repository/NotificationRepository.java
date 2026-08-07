package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.Notification;
import com.patrick.fintech.loan_backend.model.User;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository
        extends JpaRepository<Notification, Long> {

    // ============================================================
    // UNREAD NOTIFICATIONS
    // ============================================================

    List<Notification> findByUserAndReadFalseOrderByCreatedAtDesc(
            User user
    );


    // ============================================================
    // UNREAD COUNT
    // ============================================================

    long countByUserAndReadFalse(
            User user
    );


    // ============================================================
    // LATEST 20 NOTIFICATIONS
    // ============================================================

    List<Notification> findTop20ByUserOrderByCreatedAtDesc(
            User user
    );


    // ============================================================
    // PAGINATED NOTIFICATION HISTORY
    // ============================================================

    Page<Notification> findByUserOrderByCreatedAtDesc(
            User user,
            Pageable pageable
    );


    // ============================================================
    // PAGINATED UNREAD NOTIFICATIONS
    // ============================================================

    Page<Notification> findByUserAndReadFalseOrderByCreatedAtDesc(
            User user,
            Pageable pageable
    );
}