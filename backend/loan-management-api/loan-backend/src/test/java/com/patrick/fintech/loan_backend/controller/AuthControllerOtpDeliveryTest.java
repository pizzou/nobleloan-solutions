package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.config.JwtUtils;
import com.patrick.fintech.loan_backend.dto.LoginRequest;
import com.patrick.fintech.loan_backend.model.Role;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.UserRepository;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.AuthService;
import com.patrick.fintech.loan_backend.service.MailService;
import com.patrick.fintech.loan_backend.service.MfaService;
import com.patrick.fintech.loan_backend.service.SmsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerOtpDeliveryTest {

    private AuthenticationManager authenticationManager;
    private AuthService authService;
    private JwtUtils jwtUtils;
    private UserRepository userRepository;
    private MfaService mfaService;
    private MailService mailService;
    private SmsService smsService;
    private PasswordEncoder passwordEncoder;
    private AuditService auditService;
    private AuthController controller;
    private User user;

    @BeforeEach
    void setUp() {
        authenticationManager = mock(AuthenticationManager.class);
        authService = mock(AuthService.class);
        jwtUtils = mock(JwtUtils.class);
        userRepository = mock(UserRepository.class);
        mfaService = mock(MfaService.class);
        mailService = mock(MailService.class);
        smsService = mock(SmsService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        auditService = mock(AuditService.class);

        controller = new AuthController(authenticationManager, authService, jwtUtils,
                userRepository, mfaService, mailService, smsService, passwordEncoder, auditService);

        Role role = new Role();
        role.setName("MANAGER");
        user = new User();
        user.setId(42L);
        user.setEmail("manager@example.com");
        user.setRole(role);
        user.setStatus(User.UserStatus.ACTIVE);
        user.setTokenVersion(3L);

        when(jwtUtils.isLoginOtpChallengeToken("valid-challenge")).thenReturn(true);
        when(jwtUtils.getEmailFromToken("valid-challenge")).thenReturn("manager@example.com");
        when(jwtUtils.getTokenVersion("valid-challenge")).thenReturn(3L);
        when(userRepository.findByEmailIgnoreCase("manager@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-otp-hash");
    }

    @Test
    void doesNotClaimOtpWasSentWhenProviderRejectsEmailAndClearsUnusableOtp() {
        when(mailService.sendLoginOtp(any(User.class), anyString())).thenReturn(false);

        ResponseEntity<Map<String, Object>> response = controller.sendLoginOtp(request());

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(Boolean.FALSE, response.getBody().get("success"));
        assertNull(user.getLoginOtpHash());
        assertNull(user.getLoginOtpExpiresAt());
        assertEquals(0, user.getLoginOtpAttempts());
        verify(userRepository, org.mockito.Mockito.times(2)).save(user);
    }

    @Test
    void claimsOtpWasSentOnlyAfterProviderAcceptsEmail() {
        when(mailService.sendLoginOtp(any(User.class), anyString())).thenReturn(true);

        ResponseEntity<Map<String, Object>> response = controller.sendLoginOtp(request());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Boolean.TRUE, response.getBody().get("success"));
        assertNotNull(user.getLoginOtpHash());
        assertNotNull(user.getLoginOtpExpiresAt());
        verify(mailService).sendLoginOtp(any(User.class), anyString());
    }

    private LoginRequest request() {
        LoginRequest request = new LoginRequest();
        request.setOtpChallengeToken("valid-challenge");
        return request;
    }
}
