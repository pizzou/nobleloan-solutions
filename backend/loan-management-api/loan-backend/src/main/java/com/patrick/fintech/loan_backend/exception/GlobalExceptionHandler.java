package com.patrick.fintech.loan_backend.exception;

import com.patrick.fintech.loan_backend.exception.DuplicateBorrowerException;

import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotWritableException;

import org.springframework.security.access.AccessDeniedException;

import org.springframework.validation.FieldError;

import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.hibernate.LazyInitializationException;
import com.patrick.fintech.loan_backend.service.IdempotencyService;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

        // ============================================================
        // DUPLICATE BORROWER
        // ============================================================

        @ExceptionHandler(DuplicateBorrowerException.class)
        public ResponseEntity<Map<String, Object>> handleDuplicateBorrower(
                        DuplicateBorrowerException ex) {

                Map<String, Object> body = new LinkedHashMap<>();

                body.put(
                                "timestamp",
                                LocalDateTime.now().toString());

                body.put(
                                "error",
                                ex.getMessage());

                if (ex.getExistingBorrower() != null) {

                        Map<String, Object> existing = new LinkedHashMap<>();

                        var borrower = ex.getExistingBorrower();

                        existing.put(
                                        "id",
                                        borrower.getId());

                        existing.put(
                                        "firstName",
                                        borrower.getFirstName());

                        existing.put(
                                        "lastName",
                                        borrower.getLastName());

                        existing.put(
                                        "email",
                                        borrower.getEmail());

                        existing.put(
                                        "phone",
                                        borrower.getPhone());

                        existing.put(
                                        "matchedOn",
                                        ex.getMatchedOn());

                        body.put(
                                        "existingBorrower",
                                        existing);
                }

                return json(
                                HttpStatus.CONFLICT,
                                body);
        }

        // ============================================================
        // VALIDATION
        // ============================================================

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<Map<String, Object>> handleValidation(
                        MethodArgumentNotValidException ex) {

                Map<String, String> errors = new LinkedHashMap<>();

                ex.getBindingResult()
                                .getAllErrors()
                                .forEach(error -> {

                                        String field;

                                        if (error instanceof FieldError fieldError) {
                                                field = fieldError.getField();
                                        } else {
                                                field = "error";
                                        }

                                        errors.put(
                                                        field,
                                                        error.getDefaultMessage());
                                });

                return json(
                                HttpStatus.BAD_REQUEST,
                                error(
                                                "Validation failed",
                                                errors));
        }

        // ============================================================
        // ACCESS DENIED
        // ============================================================

        @ExceptionHandler(AccessDeniedException.class)
        public ResponseEntity<Map<String, Object>> handleAccess(
                        AccessDeniedException ex) {

                log.warn(
                                "Access denied: {}",
                                ex.getMessage());

                Map<String, Object> body = error(
                                "Access denied",
                                null);

                body.put(
                                "status",
                                HttpStatus.FORBIDDEN.value());

                return json(
                                HttpStatus.FORBIDDEN,
                                body);
        }

        // ============================================================
        // DATA INTEGRITY
        // ============================================================

        @ExceptionHandler(DataIntegrityViolationException.class)
        public ResponseEntity<Map<String, Object>> handleDataIntegrity(
                        DataIntegrityViolationException ex) {

                String cause = ex.getMostSpecificCause() != null
                                ? ex.getMostSpecificCause().getMessage()
                                : ex.getMessage();

                log.warn(
                                "Data integrity violation: {}",
                                cause);

                String friendly = "This action was already completed or conflicts with an existing record. "
                                + "Please refresh and try again.";

                return json(
                                HttpStatus.CONFLICT,
                                error(
                                                friendly,
                                                null));
        }

        // ============================================================
        // FILE TOO LARGE
        // ============================================================

        @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
        public ResponseEntity<Map<String, Object>> handleMaxUpload(
                        org.springframework.web.multipart.MaxUploadSizeExceededException ex) {

                log.warn(
                                "Upload rejected — exceeds servlet max request size: {}",
                                ex.getMessage());

                return json(
                                HttpStatus.BAD_REQUEST,
                                error(
                                                "File is too large for the server to accept. "
                                                                + "Please upload a smaller file.",
                                                null));
        }

        // ============================================================
        // IDEMPOTENCY CONFLICT
        // ============================================================

        @ExceptionHandler(IdempotencyService.IdempotencyConflictException.class)
        public ResponseEntity<Map<String, Object>> handleIdempotencyConflict(
                        IdempotencyService.IdempotencyConflictException ex) {
                log.warn("Idempotency conflict: {}", ex.getMessage());
                return json(HttpStatus.CONFLICT, error(
                                safeClientMessage(ex.getMessage(),
                                                "The request is already being processed or the idempotency key was reused."),
                                null));
        }

        // ============================================================
        // LAZY LOADING / DETACHED ENTITY
        // ============================================================

        @ExceptionHandler(LazyInitializationException.class)
        public ResponseEntity<Map<String, Object>> handleLazyInitialization(
                        LazyInitializationException ex) {
                log.error("Lazy-loading failure crossed the API boundary", ex);
                return json(HttpStatus.INTERNAL_SERVER_ERROR, error(
                                "The server could not prepare the requested response. Please retry the request.",
                                null));
        }

        // ============================================================
        // RESPONSE SERIALIZATION
        // ============================================================

        /**
         * Serialization failures are infrastructure failures, not business validation
         * errors.
         * In particular, a detached Hibernate proxy must never be reported as HTTP 400.
         */
        @ExceptionHandler(HttpMessageNotWritableException.class)
        public ResponseEntity<Map<String, Object>> handleResponseSerialization(
                        HttpMessageNotWritableException ex) {

                log.error("Response serialization failed", ex);

                return json(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                error(
                                                "The server could not serialize the response. Please retry the request.",
                                                null));
        }

        // ============================================================
        // EXPECTED CLIENT ERRORS
        // ============================================================

        @ExceptionHandler(IllegalArgumentException.class)
        public ResponseEntity<Map<String, Object>> handleIllegalArgument(
                        IllegalArgumentException ex) {

                log.warn("Invalid request: {}", ex.getMessage());

                return json(
                                HttpStatus.BAD_REQUEST,
                                error(
                                                safeClientMessage(ex.getMessage(), "Invalid request."),
                                                null));
        }

        @ExceptionHandler(IllegalStateException.class)
        public ResponseEntity<Map<String, Object>> handleIllegalState(
                        IllegalStateException ex) {

                log.warn("Request conflicts with current resource state: {}", ex.getMessage());

                return json(
                                HttpStatus.CONFLICT,
                                error(
                                                safeClientMessage(ex.getMessage(),
                                                                "The requested operation conflicts with the current resource state."),
                                                null));
        }

        // ============================================================
        // UNEXPECTED RUNTIME ERROR
        // ============================================================

        /**
         * An uncaught RuntimeException is a server defect, not automatically a HTTP
         * 400.
         * Returning 500 makes monitoring reliable and prevents internal exception text
         * from
         * becoming part of the public API contract.
         */
        @ExceptionHandler(RuntimeException.class)
        public ResponseEntity<Map<String, Object>> handleRuntime(
                        RuntimeException ex) {

                String message = ex.getMessage() == null ? "" : ex.getMessage().trim();
                String normalized = message.toLowerCase(java.util.Locale.ROOT);

                // Legacy business code still uses RuntimeException. Classify the common
                // expected cases safely while treating everything else as a server defect.
                if (normalized.contains("access denied") || normalized.contains("forbidden")) {
                        log.warn("Access denied: {}", message);
                        return json(HttpStatus.FORBIDDEN, error("Access denied", null));
                }

                if (normalized.contains("not found") || normalized.endsWith("not found")) {
                        log.warn("Resource not found: {}", message);
                        return json(HttpStatus.NOT_FOUND,
                                        error(safeClientMessage(message, "The requested resource was not found."),
                                                        null));
                }

                if (normalized.contains("already") || normalized.contains("duplicate")
                                || normalized.contains("conflict") || normalized.contains("locked")) {
                        log.warn("Business conflict: {}", message);
                        return json(HttpStatus.CONFLICT,
                                        error(safeClientMessage(message,
                                                        "The request conflicts with the current resource state."),
                                                        null));
                }

                if (normalized.contains("invalid") || normalized.contains("required")
                                || normalized.contains("must ") || normalized.contains("cannot ")
                                || normalized.contains("expired") || normalized.contains("too many")) {
                        log.warn("Rejected business request: {}", message);
                        return json(HttpStatus.BAD_REQUEST,
                                        error(safeClientMessage(message, "The request could not be accepted."), null));
                }

                log.error("Unhandled runtime exception", ex);
                return json(HttpStatus.INTERNAL_SERVER_ERROR,
                                error("Internal server error. Please retry the request.", null));
        }

        // ============================================================
        // GENERAL ERROR
        // ============================================================

        @ExceptionHandler(Exception.class)
        public ResponseEntity<Map<String, Object>> handleGeneral(
                        Exception ex) {

                log.error(
                                "Unhandled error",
                                ex);

                return json(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                error(
                                                "Internal server error",
                                                null));
        }

        // ============================================================
        // JSON RESPONSE
        // ============================================================

        private String safeClientMessage(String message, String fallback) {
                if (message == null || message.isBlank())
                        return fallback;
                // Avoid returning infrastructure/SQL/provider internals from generic exception
                // paths.
                String lower = message.toLowerCase(java.util.Locale.ROOT);
                if (lower.contains("sql") || lower.contains("hibernate") || lower.contains("proxy")
                                || lower.contains("jdbc") || lower.contains("constraint")
                                || lower.contains("password")) {
                        return fallback;
                }
                return message;
        }

        private ResponseEntity<Map<String, Object>> json(
                        HttpStatus status,
                        Map<String, Object> body) {

                return ResponseEntity
                                .status(status)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(body);
        }

        // ============================================================
        // ERROR BODY
        // ============================================================

        private Map<String, Object> error(
                        String message,
                        Object detail) {

                Map<String, Object> body = new LinkedHashMap<>();

                body.put(
                                "timestamp",
                                LocalDateTime.now().toString());

                body.put(
                                "success",
                                false);

                body.put(
                                "error",
                                message);

                if (detail != null) {

                        body.put(
                                        "detail",
                                        detail);
                }

                return body;
        }
}
