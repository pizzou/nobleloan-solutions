package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

   
    @EntityGraph(attributePaths = {"role", "organization", "branch"})
    Optional<User> findByEmail(String email);

    @Override
    @EntityGraph(attributePaths = {"role", "organization", "branch"})
    List<User> findAll();

    boolean existsByEmail(String email);

    /**
     * Explicit pessimistic-lock query.
     *
     * The method name "findByIdForUpdate" cannot be a Spring Data derived query because
     * Spring Data would interpret "ForUpdate" as a property traversal after "id".
     *
     * Using @Query makes the intended query unambiguous while @Lock applies
     * PESSIMISTIC_WRITE at the database level.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"role", "organization", "branch"})
    List<User> findByOrganization(Organization organization);

    long countByOrganization(Organization organization);

    @EntityGraph(attributePaths = {"role", "organization", "branch"})
    Optional<User> findByEmailIgnoreCase(String email);

    /**
     * User details are returned by several admin/security flows.
     * Explicitly load the stable identity associations.
     */
    @Override
    @EntityGraph(attributePaths = {"role", "organization", "branch"})
    Optional<User> findById(Long id);
}

