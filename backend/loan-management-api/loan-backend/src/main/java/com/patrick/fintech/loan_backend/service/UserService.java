package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.domain.PageRequest;
import java.time.LocalDateTime;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    public UserService(UserRepository u, RoleRepository r,
            OrganizationRepository o, PasswordEncoder p,
            PasswordResetTokenRepository passwordResetTokenRepository) {
        this.userRepository = u;
        this.roleRepository = r;
        this.organizationRepository = o;
        this.passwordEncoder = p;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
    }

    /** Compatibility constructor for existing unit tests and internal callers. */
    @Deprecated
    public UserService(UserRepository u, RoleRepository r,
            OrganizationRepository o, PasswordEncoder p) {
        this(u, r, o, p, null);
    }

    @Transactional
    public User createUser(User user, Long roleId, Long orgId) {
        if (userRepository.existsByEmail(user.getEmail()))
            throw new RuntimeException("Email already exists: " + user.getEmail());
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Role not found: " + roleId));
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new RuntimeException("Org not found: " + orgId));
        user.setRole(role);
        user.setOrganization(org);
        com.patrick.fintech.loan_backend.security.PasswordPolicy.validate(user.getPassword());
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<User> getAll(Long organizationId) {
        requireOrganizationId(organizationId);
        return userRepository.findByOrganization_IdAndDeletedAtIsNull(organizationId);
    }

    /**
     * Legacy unscoped lookup retained only for non-request/background callers.
     * Request-facing staff operations must use the organization-scoped overload.
     */
    @Deprecated
    @Transactional(readOnly = true)
    public User getById(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Invalid user id");
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
        if (user.getDeletedAt() != null || user.getPurgedAt() != null) {
            throw new RuntimeException("User not found: " + id);
        }
        return user;
    }

    @Transactional(readOnly = true)
    public User getById(Long id, Long organizationId) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Invalid user id");
        }
        requireOrganizationId(organizationId);
        User user = userRepository.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
        if (user.getDeletedAt() != null) {
            throw new RuntimeException("User is in the recycle bin: " + id);
        }
        return user;
    }

    @Transactional
    public User update(Long id, User updated, Long organizationId) {
        User user = getById(id, organizationId);
        if (updated.getName() != null && !updated.getName().isBlank())
            user.setName(updated.getName());
        return userRepository.save(user);
    }

    /**
     * Dedicated path for role changes. This must NOT go through update(id, user)
     * above —
     * that method only ever copies the `name` field onto a freshly-refetched
     * entity, so a
     * role set on the caller's in-memory User object (as UserController.changeRole
     * used to
     * do before calling update()) was silently discarded: the endpoint returned
     * success, but
     * the role in the database never changed.
     */
    @Transactional
    public User changeRole(Long id, Role role, Long organizationId) {
        User user = getById(id, organizationId);
        user.setRole(role);
        return userRepository.save(user);
    }

    /**
     * Changes a user's email after checking no one else already has it. Used both
     * for admins
     * editing another user and for self-service (where the controller additionally
     * verifies
     * the caller's current password before calling this).
     */
    @Transactional
    public User updateEmail(Long id, String newEmail, Long organizationId) {
        User user = getById(id, organizationId);
        String normalized = newEmail.trim().toLowerCase();
        if (!normalized.equals(user.getEmail())) {
            if (userRepository.existsByEmail(normalized))
                throw new RuntimeException("Email already in use: " + normalized);
            user.setEmail(normalized);
            user.setTokenVersion((user.getTokenVersion() == null ? 0L : user.getTokenVersion()) + 1L);
            userRepository.save(user);
        }
        return user;
    }

    /**
     * Admin-reset path: changes a user's password without knowing their current
     * one. Only
     * reachable when the caller is an admin/manager acting on someone else's
     * account —
     * see UserController for the self-vs-other authorization split.
     */
    @Transactional
    public User updatePassword(Long id, String newPassword, Long organizationId) {
        User user = getById(id, organizationId);
        com.patrick.fintech.loan_backend.security.PasswordPolicy.validate(newPassword);
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setTokenVersion((user.getTokenVersion() == null ? 0L : user.getTokenVersion()) + 1L);
        user.setMustChangePassword(true);
        return userRepository.save(user);
    }

    /**
     * Self-service path: the user is changing their own password, so they must
     * prove they
     * know the current one first — this was previously missing entirely, meaning
     * anyone
     * with a valid session could silently change their own password with no
     * verification.
     */
    @Transactional
    public User changeOwnPassword(Long id, String currentPassword, String newPassword, Long organizationId) {
        User user = getById(id, organizationId);
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPassword()))
            throw new RuntimeException("Current password is incorrect");
        com.patrick.fintech.loan_backend.security.PasswordPolicy.validate(newPassword);
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setTokenVersion((user.getTokenVersion() == null ? 0L : user.getTokenVersion()) + 1L);
        user.setMustChangePassword(false);
        return userRepository.save(user);
    }

    @Transactional
    public boolean verifyPassword(Long id, String rawPassword, Long organizationId) {
        User user = getById(id, organizationId);
        return rawPassword != null && passwordEncoder.matches(rawPassword, user.getPassword());
    }

    /**
     * "Delete" a staff account. This does NOT remove the row — every loan, audit
     * log entry,
     * document upload, and approval this user ever touched has a foreign key
     * pointing at
     * users.id with no cascade rule, so a real DELETE always failed with a
     * constraint
     * violation the moment the target had any history at all (which is to say,
     * always, for
     * any account actually used day to day). It surfaced to the admin as a generic
     * "conflicts with an existing record" error.
     *
     * Separately, hard-deleting a user referenced by the append-only audit hash
     * chain would
     * be the wrong move even if it didn't crash — it'd leave audit entries pointing
     * at a
     * user that no longer exists, undermining the tamper-evident trail this
     * platform relies
     * on for compliance. Deactivating (blocks login, hidden from active staff
     * lists, history
     * intact) is the correct operation here, not a workaround.
     */
    /**
     * High-risk business-owner deletion confirmation.
     *
     * The account is suspended rather than physically removed because this
     * system keeps foreign-keyed loan, audit, approval, notification and
     * operational history against users. The DELETE API therefore means
     * business deletion/deactivation while preserving the immutable history.
     */
    /**
     * Legacy overload retained for existing non-request callers. New request
     * paths must use the actor-aware overload below.
     *
     * @deprecated use {@link #deleteWithConfirmation(Long, String, Long, User)}
     */
    @Deprecated
    @Transactional
    public User deleteWithConfirmation(
            Long id,
            String confirmation,
            Long organizationId) {

        User user = getById(id, organizationId);
        String expected = "sudo " + user.getName();

        if (confirmation == null || !confirmation.equals(expected)) {
            throw new IllegalArgumentException(
                    "Deletion verification failed. Type exactly: " + expected);
        }

        if (user.getStatus() == User.UserStatus.SUSPENDED
                || user.getStatus() == User.UserStatus.INACTIVE) {
            throw new IllegalStateException("User account is already inactive");
        }

        String targetRole = user.getRole() == null ? null : user.getRole().getName();
        if ("BUSINESS_OWNER".equalsIgnoreCase(targetRole)
                && userRepository.countByOrganization_IdAndRole_NameAndStatus(
                        organizationId,
                        "BUSINESS_OWNER",
                        User.UserStatus.ACTIVE) <= 1L) {
            throw new IllegalStateException(
                    "The last active BUSINESS_OWNER cannot be deleted. Assign another active BUSINESS_OWNER first.");
        }

        LocalDateTime now = LocalDateTime.now();
        user.setStatus(User.UserStatus.SUSPENDED);
        user.setTokenVersion(
                (user.getTokenVersion() == null ? 0L : user.getTokenVersion()) + 1L);
        user.setLoginOtpHash(null);
        user.setLoginOtpExpiresAt(null);
        user.setLoginOtpAttempts(0);
        if (passwordResetTokenRepository != null) {
            passwordResetTokenRepository.deleteByUser_Id(user.getId());
        }
        user.setDeletedAt(now);
        user.setPurgeAfter(now.plusDays(30));
        user.setDeletionReason("Account deletion requested; reversible for 30 days.");
        user.setPurgedAt(null);
        return userRepository.save(user);
    }

    /**
     * High-risk staff-account deletion for the authenticated BUSINESS_OWNER.
     *
     * This is a business deletion (suspension), not a physical DELETE. User
     * rows are referenced by loans, approvals, audit records, notifications
     * and other operational history. Removing the row would either violate
     * foreign keys or destroy the identity needed to explain historical work.
     */
    @Transactional
    public User deleteWithConfirmation(
            Long id,
            String confirmation,
            Long organizationId,
            User actor) {

        requireBusinessOwner(actor);

        if (organizationId == null
                || actor.getOrganization() == null
                || actor.getOrganization().getId() == null
                || !organizationId.equals(actor.getOrganization().getId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "User deletion organization does not match the authenticated user");
        }

        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Invalid user id");
        }

        if (id.equals(actor.getId())) {
            throw new IllegalArgumentException("Cannot delete your own account");
        }

        // Lock active owners in stable order before changing a BUSINESS_OWNER
        // account. This serializes concurrent deletions and protects the last-owner
        // invariant across simultaneous requests.
        List<User> activeOwners = userRepository.findActiveByOrganizationAndRoleForUpdate(
                organizationId, "BUSINESS_OWNER", User.UserStatus.ACTIVE);
        User user = userRepository.findByIdAndOrganizationIdForUpdate(id, organizationId)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
        if (user.getDeletedAt() != null || user.getPurgedAt() != null) {
            throw new IllegalStateException("User account is already in the recycle bin or permanently purged");
        }

        if (user.getStatus() == User.UserStatus.SUSPENDED
                || user.getStatus() == User.UserStatus.INACTIVE) {
            throw new IllegalStateException("User account is already inactive");
        }

        String expected = "sudo " + user.getName();
        if (confirmation == null || !confirmation.equals(expected)) {
            throw new IllegalArgumentException(
                    "Deletion verification failed. Type exactly: " + expected);
        }

        String targetRole = user.getRole() == null ? null : user.getRole().getName();
        if ("BUSINESS_OWNER".equalsIgnoreCase(targetRole)) {
            long activeBusinessOwners = activeOwners.size();

            if (activeBusinessOwners <= 1L) {
                throw new IllegalStateException(
                        "The last active BUSINESS_OWNER cannot be deleted. Assign another active BUSINESS_OWNER first.");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        user.setStatus(User.UserStatus.SUSPENDED);
        user.setTokenVersion(
                (user.getTokenVersion() == null ? 0L : user.getTokenVersion()) + 1L);
        user.setLoginOtpHash(null);
        user.setLoginOtpExpiresAt(null);
        user.setLoginOtpAttempts(0);
        user.setLockedUntil(null);
        if (passwordResetTokenRepository != null) {
            passwordResetTokenRepository.deleteByUser_Id(user.getId());
        }
        user.setDeletedAt(now);
        user.setDeletedBy(actor.getId());
        user.setDeletionReason("Business Owner requested account deletion; reversible for 30 days.");
        user.setPurgeAfter(now.plusDays(30));
        user.setPurgedAt(null);

        return userRepository.save(user);
    }

    /** Lists restorable user accounts for the authenticated organization only. */
    @Transactional(readOnly = true)
    public List<User> listRecycleBin(Long organizationId, User actor) {
        requireBusinessOwner(actor);
        if (actor.getOrganization() == null || actor.getOrganization().getId() == null
                || !actor.getOrganization().getId().equals(organizationId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "User recycle-bin organization does not match the authenticated user");
        }
        requireOrganizationId(organizationId);
        return userRepository.findByOrganization_IdAndDeletedAtIsNotNullAndPurgedAtIsNullOrderByDeletedAtDesc(organizationId);
    }

    /** Restores a deleted account only during its 30-day retention window. */
    @Transactional
    public User restoreDeletedUser(Long id, Long organizationId, User actor) {
        requireBusinessOwner(actor);
        if (actor.getOrganization() == null || actor.getOrganization().getId() == null
                || !actor.getOrganization().getId().equals(organizationId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "User restore organization does not match the authenticated user");
        }
        User user = userRepository.findByIdAndOrganizationIdForUpdate(id, organizationId)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
        LocalDateTime now = LocalDateTime.now();
        if (user.getDeletedAt() == null || user.getPurgedAt() != null
                || user.getPurgeAfter() == null || !user.getPurgeAfter().isAfter(now)) {
            throw new IllegalStateException("User is not restorable; the 30-day retention period has expired");
        }
        user.setDeletedAt(null);
        user.setPurgeAfter(null);
        user.setDeletedBy(null);
        user.setDeletionReason(null);
        user.setStatus(User.UserStatus.ACTIVE);
        user.setTokenVersion((user.getTokenVersion() == null ? 0L : user.getTokenVersion()) + 1L);
        return userRepository.save(user);
    }

    /**
     * Purges personal data after 30 days while retaining the identity row needed by
     * immutable audit/loan/approval foreign keys. This is a permanent privacy purge,
     * not a physical row DELETE that would break financial/audit history.
     */
    @Scheduled(cron = "${app.user.recycle-bin.purge-cron:0 35 2 * * *}", zone = "${app.user.recycle-bin.purge-zone:Africa/Kigali}")
    @Transactional
    public void purgeExpiredDeletedUsers() {
        LocalDateTime now = LocalDateTime.now();
        List<User> expired = userRepository.findExpiredDeletedUsersForUpdate(now, PageRequest.of(0, 100));
        for (User user : expired) {
            if (user.getId() == null) continue;
            if (passwordResetTokenRepository != null) {
                passwordResetTokenRepository.deleteByUser_Id(user.getId());
            }
            user.setName("Deleted User #" + user.getId());
            user.setEmail("deleted-user-" + user.getId() + "@deleted.invalid");
            user.setPhone(null);
            user.setAvatarUrl(null);
            user.setJobTitle(null);
            user.setPassword(passwordEncoder.encode(Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(randomBytes(48))));
            user.setTwoFactorEnabled(false);
            user.setTwoFactorSecret(null);
            user.setLoginOtpHash(null);
            user.setLoginOtpExpiresAt(null);
            user.setLoginOtpAttempts(0);
            user.setLastLoginIp(null);
            user.setDeletionReason("Personal data permanently purged after 30-day retention.");
            user.setPurgedAt(now);
            user.setTokenVersion((user.getTokenVersion() == null ? 0L : user.getTokenVersion()) + 1L);
            userRepository.save(user);
        }
    }

    private static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    private void requireBusinessOwner(User actor) {
        if (actor == null
                || actor.getRole() == null
                || actor.getRole().getName() == null
                || !"BUSINESS_OWNER".equalsIgnoreCase(actor.getRole().getName())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Only the BUSINESS_OWNER can delete a user");
        }
    }

    @Transactional
    public User deactivate(Long id, Long organizationId) {
        User user = getById(id, organizationId);
        user.setStatus(User.UserStatus.SUSPENDED);
        return userRepository.save(user);
    }

    @Transactional
    public User reactivate(Long id, Long organizationId) {
        User user = getById(id, organizationId);
        user.setStatus(User.UserStatus.ACTIVE);
        return userRepository.save(user);
    }
    private void requireOrganizationId(Long organizationId) {
        if (organizationId == null || organizationId <= 0) {
            throw new IllegalArgumentException("Organization ID is required");
        }
    }

}
