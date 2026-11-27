package com.patrick.fintech.loan_backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class ContactEmailVerificationServiceTest {
    @Test void phoneRequiresExactlyTenDigits() {
        assertEquals("0781234567",ContactEmailVerificationService.phone(" 0781234567 "));
        assertThrows(ResponseStatusException.class,()->ContactEmailVerificationService.phone("078123456"));
        assertThrows(ResponseStatusException.class,()->ContactEmailVerificationService.phone("07812345678"));
        assertThrows(ResponseStatusException.class,()->ContactEmailVerificationService.phone("0781234ABC"));
    }
    @Test void emailMustBePresentAndWellFormed() {
        assertEquals("person@example.com",ContactEmailVerificationService.email(" Person@Example.Com "));
        assertThrows(ResponseStatusException.class,()->ContactEmailVerificationService.email(null));
        assertThrows(ResponseStatusException.class,()->ContactEmailVerificationService.email("not an email"));
    }
}
