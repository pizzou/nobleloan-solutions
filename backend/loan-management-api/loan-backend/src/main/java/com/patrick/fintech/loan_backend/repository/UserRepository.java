package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
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
     * Atomically consumes a valid login OTP. This avoids holding a pessimistic
     * row lock for the whole HTTP login request while still guaranteeing that
     * only one concurrent request can redeem the same OTP.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update User u set u.loginOtpHash = null, u.loginOtpExpiresAt = null, "
         + "u.loginOtpAttempts = 0, u.lastLoginAt = :loginAt "
         + "where u.id = :id and u.loginOtpHash = :otpHash "
         + "and u.loginOtpExpiresAt > :now")
    int consumeLoginOtp(
            @Param("id") Long id,
            @Param("otpHash") String otpHash,
            @Param("now") java.time.LocalDateTime now,
            @Param("loginAt") java.time.LocalDateTime loginAt);

    /** Atomically increments OTP failures only while the same active OTP remains current. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update User u set u.loginOtpAttempts = "
         + "case when coalesce(u.loginOtpAttempts, 0) + 1 > :maxAttempts "
         + "then :maxAttempts else coalesce(u.loginOtpAttempts, 0) + 1 end "
         + "where u.id = :id and u.loginOtpHash = :otpHash "
         + "and u.loginOtpExpiresAt > :now and coalesce(u.loginOtpAttempts, 0) < :maxAttempts")
    int incrementLoginOtpAttempts(
            @Param("id") Long id,
            @Param("otpHash") String otpHash,
            @Param("now") java.time.LocalDateTime now,
            @Param("maxAttempts") int maxAttempts);

    /** Clears an exhausted/expired login OTP. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update User u set u.loginOtpHash = null, u.loginOtpExpiresAt = null, u.loginOtpAttempts = 0 "
         + "where u.id = :id")
    int clearLoginOtp(@Param("id") Long id);

    /**
     * User details are returned by several admin/security flows.
     * Explicitly load the stable identity associations.
     */
    @Override
    @EntityGraph(attributePaths = {"role", "organization", "branch"})
    Optional<User> findById(Long id);

    /**
     * Security-boundary lookup: the organization predicate is part of the SQL
     * query, not merely a controller-side check.
     */
    @EntityGraph(attributePaths = {"role", "organization", "branch"})
    @Query("select u from User u where u.id = :id and u.organization.id = :organizationId")
    Optional<User> findByIdAndOrganizationId(
            @Param("id") Long id,
            @Param("organizationId") Long organizationId);

    @EntityGraph(attributePaths = {"role", "organization", "branch"})
    List<User> findByOrganization_Id(Long organizationId);
}
