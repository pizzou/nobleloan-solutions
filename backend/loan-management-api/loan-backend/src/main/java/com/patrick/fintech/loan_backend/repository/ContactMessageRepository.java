package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.ContactMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContactMessageRepository
        extends JpaRepository<ContactMessage, Long> {

    /**
     * Get contact messages for one organization,
     * newest messages first.
     */
    List<ContactMessage> findByOrganization_IdOrderByCreatedAtDesc(
            Long orgId
    );

    /**
     * Get one message while enforcing organization ownership.
     */
    Optional<ContactMessage> findByIdAndOrganization_Id(
            Long id,
            Long orgId
    );

    /**
     * Count unread messages for one organization.
     *
     * This is efficient for dashboard notification counts.
     */
    long countByOrganization_IdAndReadFalse(
            Long orgId
    );
}