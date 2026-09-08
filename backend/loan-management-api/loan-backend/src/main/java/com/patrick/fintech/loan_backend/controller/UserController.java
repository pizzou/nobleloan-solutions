package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.dto.RegisterRequest;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.RoleRepository;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.AuthService;
import com.patrick.fintech.loan_backend.service.UserService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthService authService;
    private final RoleRepository roleRepository;
    private final CurrentUserUtil currentUserUtil;
    private final AuditService auditService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','INSTITUTION_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(
            @RequestBody RegisterRequest req) {

        if (req == null) {
            throw new IllegalArgumentException("Request body is required");
        }

        Long organizationId = requireCurrentOrganizationId();

        req.setOrganizationId(organizationId);

        User user = authService.registerByAdmin(req);

        auditService.log(
                user.getOrganization(),
                currentUserUtil.getCurrentUser(),
                "USER_CREATED",
                "USER",
                String.valueOf(user.getId()),
                "Created user " + user.getName()
                        + " (" + user.getEmail()
                        + ") — credentials emailed",
                null,
                null,
                "User Management"
        );

        return ResponseEntity.ok(
                ApiResponse.ok(
                        "User created — login details have been emailed to them",
                        safeUser(user)
                )
        );
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAll() {

        Long organizationId = requireCurrentOrganizationId();

        List<Map<String, Object>> users =
                userService.getAll(organizationId)
                        .stream()
                        .filter(u ->
                                u != null
                                        && u.getOrganization() != null
                                        && organizationId.equals(
                                        u.getOrganization().getId()
                                )
                        )
                        .map(this::safeUser)
                        .toList();

        return ResponseEntity.ok(
                ApiResponse.ok(users)
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getById(
            @PathVariable Long id) {

        validateUserId(id);

        Long organizationId = requireCurrentOrganizationId();

        User user = userService.getById(
                id,
                organizationId
        );

        return ResponseEntity.ok(
                ApiResponse.ok(
                        safeUser(user)
                )
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> update(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        validateUserId(id);

        if (body == null) {
            body = Map.of();
        }

        Long organizationId = requireCurrentOrganizationId();

        Long callerId = currentUserUtil.getCurrentUserId();

        User caller = currentUserUtil.getCurrentUser();

        User user = userService.getById(
                id,
                organizationId
        );

        /*
         * The service already performs organization-scoped lookup.
         * Keep this additional controller-level assertion as defense
         * in depth.
         */
        assertSameOrganization(
                user,
                organizationId
        );

        boolean isSelf = id.equals(callerId);

        boolean isAdmin =
                caller != null
                        && caller.getRole() != null
                        && "ADMIN".equalsIgnoreCase(
                        caller.getRole().getName()
                );

        if (!isSelf && !isAdmin) {
            throw new AccessDeniedException(
                    "Only an administrator can edit another user's account"
            );
        }

        if (body.containsKey("name")
                && body.get("name") != null) {

            String name = body.get("name").trim();

            if (!name.isBlank()) {
                user.setName(name);
            }
        }

        if (body.containsKey("email")
                && body.get("email") != null
                && !body.get("email").isBlank()) {

            String newEmail = body.get("email").trim();

            if (newEmail.isBlank()) {
                throw new IllegalArgumentException(
                        "Email cannot be blank"
                );
            }

            if (isSelf) {

                String currentPassword =
                        body.get("currentPassword");

                if (currentPassword == null
                        || currentPassword.isBlank()) {

                    throw new IllegalArgumentException(
                            "Enter your current password to change your email"
                    );
                }

                if (!userService.verifyPassword(
                        id,
                        currentPassword,
                        organizationId
                )) {

                    throw new IllegalArgumentException(
                            "Current password is incorrect"
                    );
                }
            }

            user = userService.updateEmail(
                    id,
                    newEmail,
                    organizationId
            );
        }

        if (body.containsKey("password")
                && body.get("password") != null
                && !body.get("password").isBlank()) {

            String newPassword = body.get("password");

            if (isSelf) {

                String currentPassword =
                        body.get("currentPassword");

                if (currentPassword == null
                        || currentPassword.isBlank()) {

                    throw new IllegalArgumentException(
                            "Current password is required"
                    );
                }

                user = userService.changeOwnPassword(
                        id,
                        currentPassword,
                        newPassword,
                        organizationId
                );

            } else {

                user = userService.updatePassword(
                        id,
                        newPassword,
                        organizationId
                );
            }
        }

        User updated = userService.update(
                id,
                user,
                organizationId
        );

        auditService.log(
                updated.getOrganization(),
                currentUserUtil.getCurrentUser(),
                "USER_UPDATED",
                "USER",
                String.valueOf(updated.getId()),
                "Updated user " + updated.getName(),
                null,
                null,
                "User Management"
        );

        return ResponseEntity.ok(
                ApiResponse.ok(
                        "Updated",
                        safeUser(updated)
                )
        );
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> changeRole(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        validateUserId(id);

        if (body == null) {
            throw new IllegalArgumentException(
                    "Request body is required"
            );
        }

        String suppliedRoleName = body.get("role");

        if (suppliedRoleName == null
                || suppliedRoleName.isBlank()) {

            throw new IllegalArgumentException(
                    "role is required"
            );
        }

        final String normalizedRoleName =
                suppliedRoleName
                        .trim()
                        .toUpperCase(Locale.ROOT);

        Long organizationId = requireCurrentOrganizationId();

        User user = userService.getById(
                id,
                organizationId
        );

        assertSameOrganization(
                user,
                organizationId
        );

        var role =
                roleRepository.findByName(
                        normalizedRoleName
                ).orElseThrow(
                        () -> new IllegalArgumentException(
                                "Unknown role: "
                                        + normalizedRoleName
                        )
                );

        String previousRole =
                user.getRole() != null
                        ? user.getRole().getName()
                        : null;

        User updated = userService.changeRole(
                id,
                role,
                organizationId
        );

        auditService.log(
                updated.getOrganization(),
                currentUserUtil.getCurrentUser(),
                "USER_ROLE_CHANGED",
                "USER",
                String.valueOf(updated.getId()),
                "Changed role of "
                        + updated.getName()
                        + " to "
                        + normalizedRoleName,
                previousRole,
                normalizedRoleName,
                "User Management"
        );

        return ResponseEntity.ok(
                ApiResponse.ok(
                        "Role updated to "
                                + normalizedRoleName,
                        safeUser(updated)
                )
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id) {

        validateUserId(id);

        Long currentUserId =
                currentUserUtil.getCurrentUserId();

        if (id.equals(currentUserId)) {
            throw new IllegalArgumentException(
                    "Cannot delete your own account"
            );
        }

        Long organizationId = requireCurrentOrganizationId();

        User target = userService.getById(
                id,
                organizationId
        );

        assertSameOrganization(
                target,
                organizationId
        );

        userService.deactivate(
                id,
                organizationId
        );

        auditService.log(
                target.getOrganization(),
                currentUserUtil.getCurrentUser(),
                "USER_DEACTIVATED",
                "USER",
                String.valueOf(id),
                "Deactivated user "
                        + target.getName()
                        + " ("
                        + target.getEmail()
                        + ")",
                null,
                null,
                "User Management"
        );

        return ResponseEntity.ok(
                ApiResponse.ok(
                        "User deactivated — their login is disabled and their history is preserved"
                )
        );
    }

    @PutMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reactivate(
            @PathVariable Long id) {

        validateUserId(id);

        Long organizationId = requireCurrentOrganizationId();

        User target = userService.getById(
                id,
                organizationId
        );

        assertSameOrganization(
                target,
                organizationId
        );

        User updated = userService.reactivate(
                id,
                organizationId
        );

        auditService.log(
                updated.getOrganization(),
                currentUserUtil.getCurrentUser(),
                "USER_REACTIVATED",
                "USER",
                String.valueOf(id),
                "Reactivated user "
                        + updated.getName()
                        + " ("
                        + updated.getEmail()
                        + ")",
                null,
                null,
                "User Management"
        );

        return ResponseEntity.ok(
                ApiResponse.ok(
                        "User reactivated",
                        safeUser(updated)
                )
        );
    }

    /**
     * Returns the authenticated user's organization ID.
     *
     * This is deliberately centralized so every request-facing user
     * operation is guaranteed to operate within the authenticated
     * tenant/organization boundary.
     */
    private Long requireCurrentOrganizationId() {

        Long organizationId =
                currentUserUtil.getCurrentOrganizationId();

        if (organizationId == null || organizationId <= 0) {
            throw new AccessDeniedException(
                    "Authenticated user has no valid organization"
            );
        }

        return organizationId;
    }

    private void validateUserId(Long id) {

        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    "Invalid user id"
            );
        }
    }

    private void assertSameOrganization(
            User user,
            Long organizationId) {

        if (user == null) {
            throw new IllegalArgumentException(
                    "User not found"
            );
        }

        if (organizationId == null
                || organizationId <= 0) {

            throw new AccessDeniedException(
                    "Authenticated user has no valid organization"
            );
        }

        if (user.getOrganization() == null
                || user.getOrganization().getId() == null
                || !organizationId.equals(
                user.getOrganization().getId()
        )) {

            throw new AccessDeniedException(
                    "Access denied"
            );
        }
    }

    /**
     * Prevents sensitive/internal user fields from being exposed
     * through the API.
     */
    private Map<String, Object> safeUser(User u) {

        if (u == null) {
            return Map.of();
        }

        Map<String, Object> m =
                new LinkedHashMap<>();

        m.put(
                "id",
                u.getId()
        );

        m.put(
                "name",
                u.getName()
        );

        m.put(
                "email",
                u.getEmail()
        );

        m.put(
                "role",
                u.getRole() != null
                        ? Map.of(
                        "id",
                        u.getRole().getId(),
                        "name",
                        u.getRole().getName()
                )
                        : null
        );

        m.put(
                "organization",
                u.getOrganization() != null
                        ? Map.of(
                        "id",
                        u.getOrganization().getId(),
                        "name",
                        u.getOrganization().getName()
                )
                        : null
        );

        m.put(
                "mustChangePassword",
                u.isMustChangePassword()
        );

        m.put(
                "status",
                u.getStatus() != null
                        ? u.getStatus().name()
                        : "ACTIVE"
        );

        return m;
    }
}