package com.patrick.fintech.loan_backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanApplicationSnapshot;
import com.patrick.fintech.loan_backend.repository.LoanApplicationSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Captures original borrower-submitted answers at the time a public application
 * is accepted. No financial or approval data is changed by this service.
 *
 * A narrow allow-list excludes tenantSlug, reuseProof, upload contents and
 * unrelated client-provided fields. The JPA CryptoConverter on the snapshot
 * entity encrypts the JSON at rest.
 */
@Service
@RequiredArgsConstructor
public class LoanApplicationSnapshotWriter {

    private static final Set<String> ALLOWED_APPLICATION_FIELDS = Set.of(
            "firstName", "lastName", "email", "phone", "nationalId",
            "dateOfBirth", "gender", "maritalStatus", "nationality",
            "placeOfBirth", "singleCertificateNumber", "spouseFullName",
            "spouseNationalId", "spousePhone", "spouseConsent",
            "address", "city", "province", "district", "sector", "cell",
            "village", "country", "employmentType", "employerName",
            "jobTitle", "natureOfBusiness", "employmentTypeOther",
            "monthlyIncome", "monthlyExpenses", "loanType", "amount",
            "durationMonths", "purpose", "collateral", "collateralType",
            "collateralValue", "acceptedTerms"
    );

    private final LoanApplicationSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;

    /**
     * Must join PublicController.submitApplication's transaction. If snapshot
     * persistence fails, the public submission rolls back; a partial form
     * must not silently be accepted.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(Loan loan, Map<String, Object> submittedAnswers) {
        if (loan == null || loan.getId() == null || loan.getOrganization() == null) {
            throw new IllegalArgumentException("A persisted organization-owned loan is required.");
        }
        if (submittedAnswers == null) {
            throw new IllegalArgumentException("Original application answers are required.");
        }

        // Do not overwrite an already captured original application.
        if (snapshotRepository.findByLoan_IdAndLoan_Organization_Id(
                loan.getId(), loan.getOrganization().getId()).isPresent()) {
            return;
        }

        Map<String, Object> captured = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : submittedAnswers.entrySet()) {
            String field = entry.getKey();
            if (!ALLOWED_APPLICATION_FIELDS.contains(field)) {
                continue;
            }
            Object value = entry.getValue();
            if (value != null && !(value instanceof String)
                    && !(value instanceof Number) && !(value instanceof Boolean)) {
                throw new IllegalArgumentException("Invalid applicant field type: " + field);
            }
            if (value instanceof String s && s.length() > 8000) {
                throw new IllegalArgumentException("Applicant field is too long: " + field);
            }
            captured.put(field, value);
        }

        // No need to store a fabricated fullName: firstName and lastName are
        // retained exactly as submitted. The document can combine them.
        if (captured.isEmpty()) {
            throw new IllegalArgumentException("No public application answers were supplied.");
        }

        final String json;
        try {
            json = objectMapper.writeValueAsString(Collections.unmodifiableMap(captured));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize applicant-provided answers.", ex);
        }

        LoanApplicationSnapshot snapshot = new LoanApplicationSnapshot();
        snapshot.setLoan(loan);
        // CryptoConverter encrypts this attribute when JPA persists the entity.
        snapshot.setOriginalAnswersJson(json);
        snapshotRepository.saveAndFlush(snapshot);
    }
}
