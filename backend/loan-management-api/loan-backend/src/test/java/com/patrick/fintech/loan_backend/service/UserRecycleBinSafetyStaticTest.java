package com.patrick.fintech.loan_backend.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class UserRecycleBinSafetyStaticTest {
    private static String read(String path) throws Exception {
        Path direct = Path.of(path);
        if (!Files.exists(direct)) {
            direct = Path.of("backend/loan-management-api/loan-backend", path);
        }
        return Files.readString(direct, StandardCharsets.UTF_8);
    }

    @Test
    void deletionMustBeOrganizationScopedAndRestorableForThirtyDays() throws Exception {
        String service = read("src/main/java/com/patrick/fintech/loan_backend/service/UserService.java");
        String controller = read("src/main/java/com/patrick/fintech/loan_backend/controller/UserController.java");
        String migration = read("src/main/resources/db/migration/V117__user_recycle_bin_and_retention.sql");
        assertTrue(service.contains("user.setPurgeAfter(now.plusDays(30))"));
        assertTrue(service.contains("restoreDeletedUser"));
        assertTrue(service.contains("requireBusinessOwner(actor)"));
        assertTrue(service.contains("user.getOrganization().getId().equals(organizationId)"));
        assertTrue(controller.contains("@GetMapping(\"/recycle-bin\")"));
        assertTrue(controller.contains("@PostMapping(\"/{id}/restore\")"));
        assertTrue(migration.contains("idx_app_users_recycle_bin_org_deadline"));
    }

    @Test
    void expiryMustPurgePersonalDataWithoutDeletingFinancialIdentityRows() throws Exception {
        String service = read("src/main/java/com/patrick/fintech/loan_backend/service/UserService.java");
        String migration = read("src/main/resources/db/migration/V117__user_recycle_bin_and_retention.sql");
        assertTrue(service.contains("purgeExpiredDeletedUsers"));
        assertTrue(service.contains("deleted-user-"));
        assertTrue(service.contains("user.setPurgedAt(now)"));
        assertFalse(service.contains("DELETE FROM app_users"));
        assertTrue(migration.contains("retains the FK identity row"));
    }
    @Test
    void recycledUsersCannotAuthenticateOrReceivePasswordResetLinks() throws Exception {
        String details = read("src/main/java/com/patrick/fintech/loan_backend/service/CustomUserDetailsService.java");
        String reset = read("src/main/java/com/patrick/fintech/loan_backend/service/PasswordResetService.java");
        String service = read("src/main/java/com/patrick/fintech/loan_backend/service/UserService.java");
        assertTrue(details.contains("user.getDeletedAt() != null || user.getPurgedAt() != null"));
        assertTrue(reset.contains("user.getStatus() != User.UserStatus.ACTIVE"));
        assertTrue(service.contains("passwordResetTokenRepository.deleteByUser_Id(user.getId())"));
        assertTrue(service.contains("findActiveByOrganizationAndRoleForUpdate"),
                "Last-owner protection must lock active owners to avoid concurrent deletion races");
    }

}
