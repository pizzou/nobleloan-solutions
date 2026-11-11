package com.patrick.fintech.loan_backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class ContactEmailVerificationServiceTest {
    @Test
    void requiresExactlyTenPhoneDigitsWithoutSms() {
        assertEquals("0781234567", ContactEmailVerificationService.phone(" 0781234567 "));
        assertEquals("0123456789", ContactEmailVerificationService.phone("0123456789"));
        assertThrows(ResponseStatusException.class, () -> ContactEmailVerificationService.phone(null));
        assertThrows(ResponseStatusException.class, () -> ContactEmailVerificationService.phone("078123456"));
        assertThrows(ResponseStatusException.class, () -> ContactEmailVerificationService.phone("07812345678"));
        assertThrows(ResponseStatusException.class, () -> ContactEmailVerificationService.phone("07812345AB"));
    }

    @Test
    void requiresSyntacticallyValidNonEmptyEmail() {
        assertEquals("user@example.com", ContactEmailVerificationService.email(" User@Example.Com "));
        assertThrows(ResponseStatusException.class, () -> ContactEmailVerificationService.email(null));
        assertThrows(ResponseStatusException.class, () -> ContactEmailVerificationService.email("not-an-email"));
        assertThrows(ResponseStatusException.class, () -> ContactEmailVerificationService.email("person@invalid"));
    }
}
