package com.patrick.fintech.loan_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patrick.fintech.loan_backend.model.IdempotencyKey;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.repository.IdempotencyKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private IdempotencyKeyRepository repository;

    private IdempotencyService service;
    private Organization organization;

    @BeforeEach
    void setUp() {
        service = new IdempotencyService(repository, new ObjectMapper());
        organization = Organization.builder().id(7L).name("Noble Loan Solutions").build();
    }

    @Test
    void blankKeyMustNotCreateIdempotencyRecord() {
        var outcome = service.checkOrReserve("  ", organization, "POST /payments", "{}");

        assertThat(outcome.shouldProceed()).isTrue();
        verifyNoInteractions(repository);
    }

    @Test
    void firstKeyMustBeReserved() {
        when(repository.findByKeyAndOrganization("abc", organization)).thenReturn(Optional.empty());

        var outcome = service.checkOrReserve("abc", organization, "POST /payments", "{\"amount\":100}");

        assertThat(outcome.shouldProceed()).isTrue();
        verify(repository).saveAndFlush(any(IdempotencyKey.class));
    }

    @Test
    void completedKeyMustReplayStoredResponse() {
        var existing = IdempotencyKey.builder()
                .key("abc")
                .organization(organization)
                .endpoint("POST /payments")
                .requestHash(sha256("{\"amount\":100}"))
                .status(IdempotencyKey.Status.COMPLETED)
                .responseBody("{\"id\":123}")
                .responseStatusCode(200)
                .build();

        when(repository.findByKeyAndOrganization("abc", organization)).thenReturn(Optional.of(existing));

        var outcome = service.checkOrReserve("abc", organization, "POST /payments", "{\"amount\":100}");

        assertThat(outcome.isReplay()).isTrue();
        assertThat(outcome.cachedResponseBody()).isEqualTo("{\"id\":123}");
        assertThat(outcome.cachedStatusCode()).isEqualTo(200);
    }

    @Test
    void sameKeyWithDifferentPayloadMustBeRejected() {
        var existing = IdempotencyKey.builder()
                .key("abc")
                .organization(organization)
                .requestHash(sha256("{\"amount\":100}"))
                .status(IdempotencyKey.Status.COMPLETED)
                .build();

        when(repository.findByKeyAndOrganization("abc", organization)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.checkOrReserve(
                "abc", organization, "POST /payments", "{\"amount\":999}"))
                .isInstanceOf(IdempotencyService.IdempotencyConflictException.class)
                .hasMessageContaining("different request body");
    }

    @Test
    void expiredKeyMustBeReplaced() {
        var existing = IdempotencyKey.builder()
                .key("abc")
                .organization(organization)
                .requestHash(sha256("{}"))
                .status(IdempotencyKey.Status.COMPLETED)
                .expiresAt(java.time.LocalDateTime.now().minusMinutes(1))
                .build();

        when(repository.findByKeyAndOrganization("abc", organization)).thenReturn(Optional.of(existing));

        var outcome = service.checkOrReserve("abc", organization, "POST /payments", "{}");

        assertThat(outcome.shouldProceed()).isTrue();
        verify(repository).delete(existing);
        verify(repository).flush();
        verify(repository).saveAndFlush(any(IdempotencyKey.class));
    }

    @Test
    void failedKeyMustBeReopenedForRetry() {
        var existing = IdempotencyKey.builder()
                .key("abc")
                .organization(organization)
                .requestHash(sha256("{}"))
                .status(IdempotencyKey.Status.FAILED)
                .build();

        when(repository.findByKeyAndOrganization("abc", organization)).thenReturn(Optional.of(existing));

        var outcome = service.checkOrReserve("abc", organization, "POST /payments", "{}");

        assertThat(outcome.shouldProceed()).isTrue();
        assertThat(existing.getStatus()).isEqualTo(IdempotencyKey.Status.IN_PROGRESS);
    }

    private static String sha256(String value) {
        try {
            byte[] bytes = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : bytes)
                out.append(String.format("%02x", b));
            return out.toString();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
