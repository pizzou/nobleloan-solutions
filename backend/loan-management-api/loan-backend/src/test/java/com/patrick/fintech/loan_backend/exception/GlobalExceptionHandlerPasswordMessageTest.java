package com.patrick.fintech.loan_backend.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerPasswordMessageTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void exposesSafePasswordPolicyMessageInsteadOfGenericInvalidRequest() {
        String message = "Password must contain at least one uppercase letter";

        ResponseEntity<Map<String, Object>> response = handler.handleIllegalArgument(
                new IllegalArgumentException(message));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(message, response.getBody().get("error"));
    }

    @Test
    void exposesSafePasswordMaximumLengthMessage() {
        String message = "Password must not exceed 128 characters";

        ResponseEntity<Map<String, Object>> response = handler.handleIllegalArgument(
                new IllegalArgumentException(message));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(message, response.getBody().get("error"));
    }

    @Test
    void exposesSafeCurrentPasswordValidationMessage() {
        String message = "Current password is incorrect";

        ResponseEntity<Map<String, Object>> response = handler.handleIllegalArgument(
                new IllegalArgumentException(message));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(message, response.getBody().get("error"));
    }

    @Test
    void doesNotExposeArbitraryPasswordRelatedExceptionMessages() {
        ResponseEntity<Map<String, Object>> response = handler.handleIllegalArgument(
                new IllegalArgumentException("password column constraint failed: secret-value"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Invalid request.", response.getBody().get("error"));
    }
}
