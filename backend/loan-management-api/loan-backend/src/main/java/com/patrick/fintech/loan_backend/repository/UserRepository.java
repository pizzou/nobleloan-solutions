package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    /**
     * Authentication and current-user flows frequently outlive the repository transaction.
     * Fetch the small, stable identity graph explicitly so role/organization/branch never
     * remain Hibernate proxies when the entity is returned to the web layer.
     */
    @EntityGraph(attributePaths = {"role", "organization", "branch"})
    Optional<User> findByEmail(String email);
    @Override
    @EntityGraph(attributePaths = {"role", "organization", "branch"})
    List<User> findAll();

    boolean existsByEmail(String email);
    @EntityGraph(attributePaths = {"role", "organization", "branch"})
    List<User> findByOrganization(Organization organization);
    long countByOrganization(Organization organization);
    @EntityGraph(attributePaths = {"role", "organization", "branch"})
    Optional<User> findByEmailIgnoreCase(String email);

    /**
     * User details are returned by several admin endpoints. Redeclaring findById with an
     * entity graph makes the safe identity associations deterministic instead of relying on
     * JPA's default EAGER semantics, which may still be represented by Hibernate proxies.
     */
    @Override
    @EntityGraph(attributePaths = {"role", "organization", "branch"})
    Optional<User> findById(Long id);
}
