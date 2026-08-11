package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.repository.BorrowerRepository;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import com.patrick.fintech.loan_backend.security.HmacIndexer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class BorrowerService {

    private final BorrowerRepository repo;
    private final OrganizationRepository orgRepo;

    public BorrowerService(
            BorrowerRepository repo,
            OrganizationRepository orgRepo) {

        this.repo = repo;
        this.orgRepo = orgRepo;
    }

    // ============================================================
    // CREATE BORROWER
    // ============================================================

    @Transactional
    public Borrower create(Borrower borrower, Long orgId) {

        if (borrower == null) {
            throw new IllegalArgumentException(
                    "Borrower information is required");
        }

        if (orgId == null) {
            throw new IllegalArgumentException(
                    "Organization ID is required");
        }

        Organization org = orgRepo.findById(orgId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Organization not found: " + orgId));

        // --------------------------------------------------------
        // AGE VALIDATION
        //
        // Borrower must be at least 18 years old.
        //
        // This is calculated dynamically from today's date.
        //
        // Example:
        // Today = 11 August 2026
        // Latest valid DOB = 11 August 2008
        //
        // On 11 August 2027:
        // Latest valid DOB = 11 August 2009
        // --------------------------------------------------------

        validateMinimumAge(borrower.getDateOfBirth());

        // --------------------------------------------------------
        // NATIONAL ID DUPLICATE CHECK
        // --------------------------------------------------------

        if (borrower.getNationalId() != null
                && !borrower.getNationalId().isBlank()) {

            String nationalId =
                    borrower.getNationalId().trim();

            repo.findByNationalIdHashAndOrganization_Id(
                    HmacIndexer.index(nationalId),
                    orgId
            ).ifPresent(existing -> {

                throw new IllegalArgumentException(
                        "A borrower with National ID \""
                                + nationalId
                                + "\" already exists: "
                                + existing.getFirstName()
                                + " "
                                + existing.getLastName()
                );
            });
        }

        // --------------------------------------------------------
        // PHONE DUPLICATE CHECK
        // --------------------------------------------------------

        if (borrower.getPhone() != null
                && !borrower.getPhone().isBlank()) {

            String phone =
                    borrower.getPhone().trim();

            repo.findByPhoneHashAndOrganization_Id(
                    HmacIndexer.index(phone),
                    orgId
            ).ifPresent(existing -> {

                throw new IllegalArgumentException(
                        "A borrower with phone \""
                                + phone
                                + "\" already exists: "
                                + existing.getFirstName()
                                + " "
                                + existing.getLastName()
                );
            });
        }

        // --------------------------------------------------------
        // EMAIL DUPLICATE CHECK
        // --------------------------------------------------------

        if (borrower.getEmail() != null
                && !borrower.getEmail().isBlank()) {

            String email =
                    borrower.getEmail().trim();

            repo.findByEmailAndOrganization_Id(
                    email,
                    orgId
            ).ifPresent(existing -> {

                throw new IllegalArgumentException(
                        "A borrower with email \""
                                + email
                                + "\" already exists: "
                                + existing.getFirstName()
                                + " "
                                + existing.getLastName()
                );
            });
        }

        // --------------------------------------------------------
        // ORGANIZATION
        // --------------------------------------------------------

        borrower.setOrganization(org);

        // --------------------------------------------------------
        // DEFAULT KYC STATUS
        // --------------------------------------------------------

        if (borrower.getKycStatus() == null
                || borrower.getKycStatus().isBlank()) {

            borrower.setKycStatus("PENDING");
        }

        return repo.save(borrower);
    }

    // ============================================================
    // UPDATE BORROWER
    // ============================================================

    @Transactional
    public Borrower update(
            Long id,
            Borrower updated) {

        if (id == null) {
            throw new IllegalArgumentException(
                    "Borrower ID is required");
        }

        if (updated == null) {
            throw new IllegalArgumentException(
                    "Borrower update information is required");
        }

        Borrower b = getById(id);

        if (b.getOrganization() == null
                || b.getOrganization().getId() == null) {

            throw new IllegalStateException(
                    "Borrower is not associated with an organization");
        }

        Long orgId =
                b.getOrganization().getId();

        // --------------------------------------------------------
        // AGE VALIDATION
        //
        // Validate the supplied DOB if one is being changed.
        // If no DOB is supplied, preserve the existing DOB.
        // --------------------------------------------------------

        if (updated.getDateOfBirth() != null) {

            validateMinimumAge(
                    updated.getDateOfBirth());

            b.setDateOfBirth(
                    updated.getDateOfBirth());
        }

        // --------------------------------------------------------
        // NATIONAL ID DUPLICATE CHECK
        // --------------------------------------------------------

        if (updated.getNationalId() != null
                && !updated.getNationalId().isBlank()
                && !updated.getNationalId()
                .equals(b.getNationalId())) {

            String nationalId =
                    updated.getNationalId().trim();

            repo.findByNationalIdHashAndOrganization_Id(
                    HmacIndexer.index(nationalId),
                    orgId
            ).ifPresent(existing -> {

                throw new IllegalArgumentException(
                        "National ID \""
                                + nationalId
                                + "\" is already used by: "
                                + existing.getFirstName()
                                + " "
                                + existing.getLastName()
                );
            });
        }

        // --------------------------------------------------------
        // PHONE DUPLICATE CHECK
        // --------------------------------------------------------

        if (updated.getPhone() != null
                && !updated.getPhone().isBlank()
                && !updated.getPhone()
                .equals(b.getPhone())) {

            String phone =
                    updated.getPhone().trim();

            repo.findByPhoneHashAndOrganization_Id(
                    HmacIndexer.index(phone),
                    orgId
            ).ifPresent(existing -> {

                throw new IllegalArgumentException(
                        "Phone \""
                                + phone
                                + "\" is already used by: "
                                + existing.getFirstName()
                                + " "
                                + existing.getLastName()
                );
            });
        }

        // --------------------------------------------------------
        // EMAIL DUPLICATE CHECK
        // --------------------------------------------------------

        if (updated.getEmail() != null
                && !updated.getEmail().isBlank()
                && !updated.getEmail()
                .equalsIgnoreCase(b.getEmail())) {

            String email =
                    updated.getEmail().trim();

            repo.findByEmailAndOrganization_Id(
                    email,
                    orgId
            ).ifPresent(existing -> {

                throw new IllegalArgumentException(
                        "Email \""
                                + email
                                + "\" is already used by: "
                                + existing.getFirstName()
                                + " "
                                + existing.getLastName()
                );
            });
        }

        // --------------------------------------------------------
        // UPDATE BASIC INFORMATION
        // --------------------------------------------------------

        if (updated.getFirstName() != null) {
            b.setFirstName(
                    updated.getFirstName());
        }

        if (updated.getLastName() != null) {
            b.setLastName(
                    updated.getLastName());
        }

        if (updated.getPhone() != null) {
            b.setPhone(
                    updated.getPhone());
        }

        if (updated.getEmail() != null) {
            b.setEmail(
                    updated.getEmail());
        }

        if (updated.getAddress() != null) {
            b.setAddress(
                    updated.getAddress());
        }

        if (updated.getNationalId() != null) {
            b.setNationalId(
                    updated.getNationalId());
        }

        if (updated.getCreditScore() != null) {
            b.setCreditScore(
                    updated.getCreditScore());
        }

        if (updated.getKycStatus() != null) {
            b.setKycStatus(
                    updated.getKycStatus());
        }

        return repo.save(b);
    }

    // ============================================================
    // AGE VALIDATION
    // ============================================================

    /**
     * Ensures that the borrower is at least 18 years old.
     *
     * The rule is birthday-aware.
     *
     * Example:
     *
     * Today: 2026-08-11
     *
     * DOB: 2008-08-11 -> valid
     * DOB: 2008-08-10 -> valid
     * DOB: 2008-08-12 -> invalid
     * DOB: 2009-01-01 -> invalid
     *
     * No hard-coded year is used.
     */
    private void validateMinimumAge(
            LocalDate dateOfBirth) {

        if (dateOfBirth == null) {
            throw new IllegalArgumentException(
                    "Date of birth is required");
        }

        LocalDate today =
                LocalDate.now();

        // A future DOB is automatically invalid.
        if (dateOfBirth.isAfter(today)) {
            throw new IllegalArgumentException(
                    "Date of birth cannot be in the future");
        }

        // Latest DOB that is allowed for an 18-year-old.
        LocalDate latestValidDateOfBirth =
                today.minusYears(18);

        if (dateOfBirth.isAfter(
                latestValidDateOfBirth)) {

            throw new IllegalArgumentException(
                    "Borrower must be at least 18 years old");
        }
    }

    // ============================================================
    // LIST
    // ============================================================

    @Transactional(readOnly = true)
    public List<Borrower> listByOrg(
            Long orgId) {

        if (orgId == null) {
            throw new IllegalArgumentException(
                    "Organization ID is required");
        }

        return repo.findByOrganization_Id(
                orgId);
    }

    // ============================================================
    // SEARCH
    // ============================================================

    @Transactional(readOnly = true)
    public List<Borrower> search(
            Long orgId,
            String query) {

        if (orgId == null) {
            throw new IllegalArgumentException(
                    "Organization ID is required");
        }

        if (query == null
                || query.isBlank()) {

            return listByOrg(orgId);
        }

        String q =
                query.trim()
                        .toLowerCase();

        return listByOrg(orgId)
                .stream()
                .filter(b ->
                        contains(
                                b.getFirstName(),
                                q)
                        || contains(
                                b.getLastName(),
                                q)
                        || contains(
                                b.getNationalId(),
                                q)
                        || contains(
                                b.getPhone(),
                                q))
                .toList();
    }

    // ============================================================
    // GET BY ID
    // ============================================================

    @Transactional(readOnly = true)
    public Borrower getById(
            Long id) {

        if (id == null) {
            throw new IllegalArgumentException(
                    "Borrower ID is required");
        }

        return repo.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Borrower not found: "
                                        + id));
    }

    // ============================================================
    // GET BY ID + ORGANIZATION
    // ============================================================

    @Transactional(readOnly = true)
    public Borrower getByIdForOrg(
            Long id,
            Long orgId) {

        if (id == null) {
            throw new IllegalArgumentException(
                    "Borrower ID is required");
        }

        if (orgId == null) {
            throw new IllegalArgumentException(
                    "Organization ID is required");
        }

        Borrower b =
                getById(id);

        if (b.getOrganization() == null
                || b.getOrganization().getId() == null
                || !b.getOrganization()
                        .getId()
                        .equals(orgId)) {

            throw new IllegalArgumentException(
                    "Borrower not found: " + id);
        }

        return b;
    }

    // ============================================================
    // STRING SEARCH HELPER
    // ============================================================

    private boolean contains(
            String field,
            String q) {

        return field != null
                && field
                        .toLowerCase()
                        .contains(q);
    }
}