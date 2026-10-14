package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.PasswordResetToken;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.PasswordResetTokenRepository;
import com.patrick.fintech.loan_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordResetTokenRepository tokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock MailService mailService;
    @Mock AuditService auditService;

    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(
                userRepository, tokenRepository, passwordEncoder, mailService, auditService);
        ReflectionTestUtils.setField(service, "frontendUrl", "https://nobleloansolutions.rw");
    }

    @Test
    void forgotPasswordDoesNotCreateTokenWhenEmailDoesNotExist() {
        when(mailService.isPasswordResetEmailConfigured()).thenReturn(true);
        when(userRepository.findByEmailIgnoreCase("unknown@example.com")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.requestReset(" UNKNOWN@example.com "));

        verify(tokenRepository, never()).save(any());
        verify(mailService, never()).sendPasswordResetEmailNow(any(), anyString());
    }

    @Test
    void forgotPasswordDoesNotCreateTokenWhenEmailDeliveryIsNotConfigured() {
        when(mailService.isPasswordResetEmailConfigured()).thenReturn(false);

        assertDoesNotThrow(() -> service.requestReset("user@example.com"));

        verify(userRepository, never()).findByEmailIgnoreCase(anyString());
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void forgotPasswordStoresOnlyDigestAndRemovesTokenWhenDeliveryFails() {
        User user = activeUser();
        when(mailService.isPasswordResetEmailConfigured()).thenReturn(true);
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(mailService.sendPasswordResetEmailNow(eq(user), anyString())).thenReturn(false);

        service.requestReset(" USER@example.com ");

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(captor.capture());
        PasswordResetToken saved = captor.getValue();
        assertNotNull(saved.getToken());
        assertTrue(saved.getToken().matches("[0-9a-f]{64}"), "Only a SHA-256 token digest should be stored");
        assertTrue(saved.getExpiresAt().isAfter(LocalDateTime.now()));
        verify(tokenRepository).delete(saved);
        verify(auditService, never()).log(any(), any(), anyString(), anyString(), anyString(), anyString(),
                any(), any(), anyString());
    }

    @Test
    void resetPasswordChangesPasswordConsumesTokenAndRevokesSessions() {
        User user = activeUser();
        user.setId(42L);
        user.setTokenVersion(7L);
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        token.setUsed(false);
        when(tokenRepository.findByTokenAndUsedFalse(anyString())).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("StrongPassword1!")).thenReturn("encoded-password");

        service.resetPassword("a-valid-random-token", "StrongPassword1!");

        assertEquals("encoded-password", user.getPassword());
        assertEquals(8L, user.getTokenVersion());
        assertNull(user.getLoginOtpHash());
        assertNull(user.getLoginOtpExpiresAt());
        assertEquals(0, user.getLoginOtpAttempts());
        assertTrue(token.isUsed());
        verify(userRepository).save(user);
        verify(tokenRepository).save(token);
    }

    @Test
    void expiredResetTokenCannotBeRedeemed() {
        PasswordResetToken token = new PasswordResetToken();
        token.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        token.setUser(activeUser());
        when(tokenRepository.findByTokenAndUsedFalse(anyString())).thenReturn(Optional.of(token));

        assertThrows(IllegalArgumentException.class,
                () -> service.resetPassword("expired-token", "StrongPassword1!"));
        verify(userRepository, never()).save(any());
        verify(tokenRepository, never()).save(any());
    }

    private User activeUser() {
        User user = new User();
        user.setName("Test User");
        user.setEmail("user@example.com");
        user.setStatus(User.UserStatus.ACTIVE);
        user.setTokenVersion(0L);
        return user;
    }
}
