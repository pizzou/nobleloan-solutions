
package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

        @EntityGraph(attributePaths = { "role", "organization", "branch" })
        Optional<User> findByEmail(String email);

        @Override
        @EntityGraph(attributePaths = { "role", "organization", "branch" })
        List<User> findAll();

        boolean existsByEmail(String email);

        /**
         * Explicit pessimistic-lock query.
         * 
         * @Lock applies PESSIMISTIC_WRITE at the database level.
         */
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select u from User u where u.id = :id")
        Optional<User> findByIdForUpdate(@Param("id") Long id);

        @EntityGraph(attributePaths = { "role", "organization", "branch" })
        List<User> findByOrganization(Organization organization);

        long countByOrganization(Organization organization);

        /**
         * Counts active BUSINESS_OWNER accounts inside one tenant.
         */
        long countByOrganization_IdAndRole_NameAndStatus(
                        Long organizationId,
                        String roleName,
                        User.UserStatus status);

        @EntityGraph(attributePaths = { "role", "organization", "branch" })
        Optional<User> findByEmailIgnoreCase(String email);

        /**
         * Atomically consumes a valid login OTP.
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

        /**
         * Atomically increments OTP failures only while the same active OTP remains
         * current.
         */
        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Transactional
        @Query("update User u set u.loginOtpAttempts = "
                        + "case when coalesce(u.loginOtpAttempts, 0) + 1 > :maxAttempts "
                        + "then :maxAttempts else coalesce(u.loginOtpAttempts, 0) + 1 end "
                        + "where u.id = :id and u.loginOtpHash = :otpHash "
                        + "and u.loginOtpExpiresAt > :now "
                        + "and coalesce(u.loginOtpAttempts, 0) < :maxAttempts")
        int incrementLoginOtpAttempts(
                        @Param("id") Long id,
                        @Param("otpHash") String otpHash,
                        @Param("now") java.time.LocalDateTime now,
                        @Param("maxAttempts") int maxAttempts);

        /** Clears an exhausted or expired login OTP. */
        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Transactional
        @Query("update User u set u.loginOtpHash = null, "
                        + "u.loginOtpExpiresAt = null, u.loginOtpAttempts = 0 "
                        + "where u.id = :id")
        int clearLoginOtp(@Param("id") Long id);

        /**
         * Explicitly loads stable identity associations.
         */
        @Override
        @EntityGraph(attributePaths = { "role", "organization", "branch" })
        Optional<User> findById(Long id);

        /**
         * Organization-scoped security lookup.
         */
        @EntityGraph(attributePaths = { "role", "organization", "branch" })
        @Query("select u from User u "
                        + "where u.id = :id and u.organization.id = :organizationId")
        Optional<User> findByIdAndOrganizationId(
                        @Param("id") Long id,
                        @Param("organizationId") Long organizationId);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @EntityGraph(attributePaths = { "role", "organization", "branch" })
        @Query("select u from User u "
                        + "where u.id = :id and u.organization.id = :organizationId")
        Optional<User> findByIdAndOrganizationIdForUpdate(
                        @Param("id") Long id,
                        @Param("organizationId") Long organizationId);

        @EntityGraph(attributePaths = { "role", "organization", "branch" })
        List<User> findByOrganization_Id(Long organizationId);

        @EntityGraph(attributePaths = { "role", "organization", "branch" })
        List<User> findByOrganization_IdAndDeletedAtIsNull(Long organizationId);

        @EntityGraph(attributePaths = { "role", "organization", "branch" })
        List<User> findByOrganization_IdAndDeletedAtIsNotNullAndPurgedAtIsNullOrderByDeletedAtDesc(
                        Long organizationId);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @EntityGraph(attributePaths = { "role", "organization", "branch" })
        @Query("select u from User u "
                        + "where u.deletedAt is not null and u.purgedAt is null "
                        + "and u.purgeAfter <= :cutoff "
                        + "order by u.purgeAfter asc, u.id asc")
        List<User> findExpiredDeletedUsersForUpdate(
                        @Param("cutoff") java.time.LocalDateTime cutoff,
                        Pageable pageable);

        /**
         * Locks active users of a role in stable ID order. Retained for the
         * Business Owner deletion workflow and its concurrency protection.
         */
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @EntityGraph(attributePaths = { "role", "organization", "branch" })
        @Query("select u from User u "
                        + "where u.organization.id = :organizationId "
                        + "and upper(u.role.name) = upper(:roleName) "
                        + "and u.status = :status and u.deletedAt is null "
                        + "order by u.id asc")
        List<User> findActiveByOrganizationAndRoleForUpdate(
                        @Param("organizationId") Long organizationId,
                        @Param("roleName") String roleName,
                        @Param("status") User.UserStatus status);

        /**
         * Fetches matching users with database-level pagination.
         *
         * Pageable is essential: it limits the SQL result before Hibernate
         * attempts to convert it into a single User.
         */
        @EntityGraph(attributePaths = { "role", "organization", "branch" })
        @Query("select u from User u "
                        + "where u.organization.id = :organizationId "
                        + "and upper(u.role.name) = upper(:roleName) "
                        + "and u.status = :status "
                        + "order by u.id asc")
        List<User> findActiveUsersByOrganizationAndRole(
                        @Param("organizationId") Long organizationId,
                        @Param("roleName") String roleName,
                        @Param("status") User.UserStatus status,
                        Pageable pageable);

        /**
         * Compatibility method for existing workflow callers.
         * Selects the lowest-ID matching user, with the limit applied in SQL.
         */
        default Optional<User> findFirstActiveByOrganizationAndRole(
                        Long organizationId,
                        String roleName,
                        User.UserStatus status) {
                return findActiveUsersByOrganizationAndRole(
                                organizationId,
                                roleName,
                                status,
                                PageRequest.of(0, 1))
                                .stream()
                                .findFirst();
        }

        /** Convenience query used by durable workflow-task assignment. */
        default Optional<User> findFirstActiveByOrganizationAndRole(
                        Long organizationId,
                        String roleName) {
                return findFirstActiveByOrganizationAndRole(
                                organizationId,
                                roleName,
                                User.UserStatus.ACTIVE);
        }
}
