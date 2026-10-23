package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.BorrowerDocumentReuseChallenge;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.repository.BorrowerDocumentReuseChallengeRepository;
import com.patrick.fintech.loan_backend.repository.BorrowerRepository;
import com.patrick.fintech.loan_backend.security.HmacIndexer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * One-time proof of control over the existing borrower's stored email address.
 * Phone and National ID alone are not sufficient for automatic KYC reuse.
 */
@Service
public class BorrowerDocumentReuseVerificationService {
    private final BorrowerRepository borrowers;
    private final BorrowerDocumentReuseChallengeRepository challenges;
    private final MailService mailService;
    private final SecureRandom random = new SecureRandom();
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

    public BorrowerDocumentReuseVerificationService(BorrowerRepository borrowers,
            BorrowerDocumentReuseChallengeRepository challenges, MailService mailService) {
        this.borrowers = borrowers;
        this.challenges = challenges;
        this.mailService = mailService;
    }

    /** Always return a challenge ID; do not reveal whether this borrower exists. */
    public UUID start(Organization org, String phone, String nationalId) {
        UUID placeholder = UUID.randomUUID();
        if (org == null || org.getId() == null || phone == null || nationalId == null
                || !phone.matches("^0\\d{9}$") || !nationalId.matches("^[0-9]{16}$")) {
            return placeholder;
        }
        Borrower byPhone = borrowers.findByPhoneHashAndOrganization_Id(
                HmacIndexer.index(phone), org.getId()).orElse(null);
        Borrower byId = borrowers.findByNationalIdHashAndOrganization_Id(
                HmacIndexer.index(nationalId), org.getId()).orElse(null);
        if (byPhone == null || byId == null || !byPhone.getId().equals(byId.getId())
                || byPhone.getEmail() == null || byPhone.getEmail().isBlank()) {
            return placeholder;
        }
        LocalDateTime now = LocalDateTime.now();
        if (challenges.countByBorrowerIdAndCreatedAtAfter(
                byPhone.getId(), now.minusHours(1)) >= 3) return placeholder;

        String code = String.format("%06d", random.nextInt(1_000_000));
        BorrowerDocumentReuseChallenge challenge = new BorrowerDocumentReuseChallenge();
        challenge.setId(UUID.randomUUID());
        challenge.setBorrowerId(byPhone.getId());
        challenge.setOrganizationId(org.getId());
        challenge.setCreatedAt(now);
        challenge.setExpiresAt(now.plusMinutes(5));
        challenge.setOtpHash(encoder.encode(code));
        challenges.save(challenge);
        if (!mailService.sendBorrowerDocumentReuseOtp(byPhone, code)) {
            challenges.deleteById(challenge.getId());
            return placeholder;
        }
        return challenge.getId();
    }

    /** Returns an opaque proof; null means invalid code and attempts are persisted. */
    @Transactional
    public String verify(UUID challengeId, String code) {
        if (challengeId == null || code == null || !code.matches("^[0-9]{6}$")) return null;
        BorrowerDocumentReuseChallenge c = challenges.findLocked(challengeId).orElse(null);
        LocalDateTime now = LocalDateTime.now();
        if (c == null || c.getVerifiedAt() != null || c.getAttempts() >= 5
                || !now.isBefore(c.getExpiresAt())) return null;
        if (!encoder.matches(code, c.getOtpHash())) {
            c.setAttempts(c.getAttempts() + 1);
            challenges.save(c);
            return null;
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        c.setVerifiedAt(now);
        c.setProofHash(sha256(secret));
        c.setProofExpiresAt(now.plusMinutes(10));
        challenges.save(c);
        return c.getId() + "." + secret;
    }

    /** Called in the same transaction as public loan creation. One use only. */
    @Transactional
    public void consumeProof(Long borrowerId, Long orgId, String proof) {
        try {
            if (proof == null || !proof.contains(".")) throw new IllegalArgumentException();
            String[] parts = proof.split("\\.", 2);
            UUID id = UUID.fromString(parts[0]);
            BorrowerDocumentReuseChallenge c = challenges.findLocked(id).orElse(null);
            LocalDateTime now = LocalDateTime.now();
            if (c == null || !borrowerId.equals(c.getBorrowerId())
                    || !orgId.equals(c.getOrganizationId()) || c.getVerifiedAt() == null
                    || c.getProofExpiresAt() == null || !now.isBefore(c.getProofExpiresAt())
                    || c.getConsumedAt() != null || c.getProofHash() == null
                    || !MessageDigest.isEqual(c.getProofHash().getBytes(StandardCharsets.US_ASCII),
                            sha256(parts[1]).getBytes(StandardCharsets.US_ASCII))) {
                throw new IllegalArgumentException();
            }
            c.setConsumedAt(now);
            challenges.save(c);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(
                    "The existing-borrower verification has expired or was not accepted. "
                            + "Request a new verification code or upload the required documents.");
        }
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to validate verification token.", e);
        }
    }
}
