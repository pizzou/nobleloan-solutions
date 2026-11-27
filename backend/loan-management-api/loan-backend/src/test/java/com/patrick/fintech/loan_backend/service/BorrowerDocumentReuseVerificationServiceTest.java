package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.BorrowerDocumentReuseChallenge;
import com.patrick.fintech.loan_backend.repository.BorrowerDocumentReuseChallengeRepository;
import com.patrick.fintech.loan_backend.repository.BorrowerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BorrowerDocumentReuseVerificationServiceTest {
    @Test
    void emailProofIsSingleUseAndBoundToBorrowerAndOrganization() {
        BorrowerDocumentReuseChallengeRepository repository =
                mock(BorrowerDocumentReuseChallengeRepository.class);
        BorrowerDocumentReuseChallenge challenge = new BorrowerDocumentReuseChallenge();
        challenge.setId(UUID.randomUUID());
        challenge.setBorrowerId(23L);
        challenge.setOrganizationId(7L);
        challenge.setCreatedAt(LocalDateTime.now());
        challenge.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        challenge.setOtpHash(new BCryptPasswordEncoder().encode("123456"));
        when(repository.findLocked(challenge.getId())).thenReturn(Optional.of(challenge));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        BorrowerDocumentReuseVerificationService service =
                new BorrowerDocumentReuseVerificationService(
                        mock(BorrowerRepository.class), repository, mock(MailService.class));

        assertNull(service.verify(challenge.getId(), "987654"));
        assertEquals(1, challenge.getAttempts());
        String proof = service.verify(challenge.getId(), "123456");
        assertNotNull(proof);
        assertThrows(IllegalArgumentException.class, () -> service.consumeProof(24L, 7L, proof));
        service.consumeProof(23L, 7L, proof);
        assertNotNull(challenge.getConsumedAt());
        assertThrows(IllegalArgumentException.class, () -> service.consumeProof(23L, 7L, proof));
    }

    @Test
    void expiredCodeCannotBeUsed() {
        BorrowerDocumentReuseChallengeRepository repository =
                mock(BorrowerDocumentReuseChallengeRepository.class);
        BorrowerDocumentReuseChallenge challenge = new BorrowerDocumentReuseChallenge();
        challenge.setId(UUID.randomUUID());
        challenge.setBorrowerId(1L);
        challenge.setOrganizationId(2L);
        challenge.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        challenge.setOtpHash(new BCryptPasswordEncoder().encode("123456"));
        when(repository.findLocked(challenge.getId())).thenReturn(Optional.of(challenge));
        BorrowerDocumentReuseVerificationService service =
                new BorrowerDocumentReuseVerificationService(
                        mock(BorrowerRepository.class), repository, mock(MailService.class));
        assertNull(service.verify(challenge.getId(), "123456"));
    }
}
