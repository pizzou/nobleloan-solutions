package com.patrick.fintech.loan_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;

import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.Role;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import com.patrick.fintech.loan_backend.repository.RoleRepository;
import com.patrick.fintech.loan_backend.repository.UserRepository;
import com.patrick.fintech.loan_backend.repository.PasswordResetTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceRecycleBinTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock OrganizationRepository organizationRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock PasswordResetTokenRepository passwordResetTokenRepository;

    private UserService service;
    private Organization organization;
    private User owner;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, roleRepository, organizationRepository, passwordEncoder,
                passwordResetTokenRepository);
        organization = new Organization();
        organization.setId(11L);
        owner = new User();
        owner.setId(1L);
        owner.setOrganization(organization);
        Role ownerRole = new Role();
        ownerRole.setName("BUSINESS_OWNER");
        owner.setRole(ownerRole);
    }

    @Test
    void deleteWithConfirmationSuspendsAndSetsThirtyDayRestoreWindow() {
        User target = new User();
        target.setId(2L);
        target.setName("Target Staff");
        target.setEmail("staff@example.test");
        target.setOrganization(organization);
        target.setStatus(User.UserStatus.ACTIVE);
        target.setTokenVersion(3L);
        Role staffRole = new Role();
        staffRole.setName("LOAN_OFFICER");
        target.setRole(staffRole);
        when(userRepository.findActiveByOrganizationAndRoleForUpdate(
                11L, "BUSINESS_OWNER", User.UserStatus.ACTIVE)).thenReturn(List.of(owner));
        when(userRepository.findByIdAndOrganizationIdForUpdate(2L, 11L)).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User deleted = service.deleteWithConfirmation(2L, "sudo Target Staff", 11L, owner);

        assertThat(deleted.getStatus()).isEqualTo(User.UserStatus.SUSPENDED);
        assertThat(deleted.getDeletedAt()).isNotNull();
        assertThat(deleted.getPurgeAfter()).isEqualTo(deleted.getDeletedAt().plusDays(30));
        assertThat(deleted.getDeletedBy()).isEqualTo(1L);
        assertThat(deleted.getTokenVersion()).isEqualTo(4L);
        assertThat(deleted.getLoginOtpHash()).isNull();
        verify(passwordResetTokenRepository).deleteByUser_Id(2L);
        verify(userRepository).save(target);
    }

    @Test
    void restoreRefusesAfterDeadlineAndRestoresBeforeDeadline() {
        User target = new User();
        target.setId(2L);
        target.setName("Target Staff");
        target.setOrganization(organization);
        target.setStatus(User.UserStatus.SUSPENDED);
        target.setTokenVersion(4L);
        target.setDeletedAt(LocalDateTime.now().minusDays(2));
        target.setPurgeAfter(LocalDateTime.now().plusDays(28));
        when(userRepository.findByIdAndOrganizationIdForUpdate(2L, 11L)).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User restored = service.restoreDeletedUser(2L, 11L, owner);

        assertThat(restored.getDeletedAt()).isNull();
        assertThat(restored.getPurgeAfter()).isNull();
        assertThat(restored.getStatus()).isEqualTo(User.UserStatus.ACTIVE);
        assertThat(restored.getTokenVersion()).isEqualTo(5L);

        target.setDeletedAt(LocalDateTime.now().minusDays(31));
        target.setPurgeAfter(LocalDateTime.now().minusSeconds(1));
        assertThatThrownBy(() -> service.restoreDeletedUser(2L, 11L, owner))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("30-day retention period has expired");

        // A missing deadline is not an unlimited restore extension: purge-blocked
        // records are outside the reversible 30-day window as well.
        target.setPurgeAfter(null);
        assertThatThrownBy(() -> service.restoreDeletedUser(2L, 11L, owner))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("30-day retention period has expired");
    }

    @Test
    void expiredUsersAreAnonymizedAndTheirIdentityRowsRemainForForeignKeys() {
        User target = new User();
        target.setId(42L);
        target.setName("Former Staff");
        target.setEmail("former@example.test");
        target.setPhone("+250788000000");
        target.setDeletedAt(LocalDateTime.now().minusDays(31));
        target.setPurgeAfter(LocalDateTime.now().minusDays(1));
        target.setStatus(User.UserStatus.SUSPENDED);
        target.setTokenVersion(8L);
        when(userRepository.findExpiredDeletedUsersForUpdate(any(), any(Pageable.class)))
                .thenReturn(List.of(target));
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-random-secret");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.purgeExpiredDeletedUsers();

        assertThat(target.getName()).isEqualTo("Deleted User #42");
        assertThat(target.getEmail()).isEqualTo("deleted-user-42@deleted.invalid");
        assertThat(target.getPhone()).isNull();
        assertThat(target.getPurgedAt()).isNotNull();
        assertThat(target.getTokenVersion()).isEqualTo(9L);
        verify(userRepository).save(target);
    }
}
