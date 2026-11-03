
package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.*;
import com.patrick.fintech.loan_backend.mapper.ResponseDtoMapper;
import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.BorrowerRepository;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.BorrowerDetailsService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/borrowers")
@RequiredArgsConstructor
@org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER')")
public class BorrowerController {

        private final BorrowerRepository borrowerRepo;

        private final CurrentUserUtil currentUserUtil;

        private final AuditService auditService;

        /*
         * NEW:
         *
         * Provides the complete borrower 360-degree profile:
         *
         * - borrower information
         * - loans
         * - payments
         * - repayment performance
         * - overdue information
         * - credit information
         * - risk information
         */
        private final BorrowerDetailsService borrowerDetailsService;

        // ============================================================
        // LIST BORROWERS
        // ============================================================

        @GetMapping
        public ResponseEntity<ApiResponse<Page<BorrowerResponse>>> list(
                        @RequestParam(defaultValue = "0") int page,

                        @RequestParam(defaultValue = "20") int size,

                        @RequestParam(required = false) String q) {

                Organization org = currentUserUtil
                                .getCurrentUser()
                                .getOrganization();

                Pageable pageable = PageRequest.of(
                                page,
                                size,
                                Sort.by(
                                                "createdAt").descending());

                Page<Borrower> result;

                if (q != null
                                &&
                                !q.isBlank()) {

                        result = borrowerRepo.search(
                                        org,
                                        q,
                                        pageable);

                } else {

                        result = borrowerRepo.findByOrganization(
                                        org,
                                        pageable);
                }

                return ResponseEntity.ok(
                                ApiResponse.ok(
                                                ResponseDtoMapper.borrowers(result)));
        }

        // ============================================================
        // GET BASIC BORROWER
        // ============================================================

        @GetMapping("/{id}")
        public ResponseEntity<ApiResponse<BorrowerResponse>> get(
                        @PathVariable Long id) {

                Organization org = currentUserUtil
                                .getCurrentUser()
                                .getOrganization();

                Borrower borrower = borrowerRepo
                                .findById(id)
                                .orElseThrow(
                                                () -> new RuntimeException(
                                                                "Borrower not found: "
                                                                                + id));

                /*
                 * MULTI-TENANT SECURITY
                 *
                 * A borrower belonging to another organization
                 * must never be accessible.
                 */

                if (borrower.getOrganization() == null
                                ||
                                borrower.getOrganization().getId() == null
                                ||
                                !borrower
                                                .getOrganization()
                                                .getId()
                                                .equals(
                                                                org.getId())) {

                        throw new RuntimeException(
                                        "Access denied");
                }

                return ResponseEntity.ok(
                                ApiResponse.ok(
                                                ResponseDtoMapper.borrower(borrower)));
        }

        // ============================================================
        // NEW: BORROWER 360 DETAILS
        // ============================================================

        @GetMapping("/{id}/details")
        public ResponseEntity<ApiResponse<BorrowerDetailsResponse>> details(
                        @PathVariable Long id) {

                Organization org = currentUserUtil
                                .getCurrentUser()
                                .getOrganization();

                /*
                 * The service performs the complete borrower
                 * financial analysis.
                 *
                 * The organization ID is passed explicitly so
                 * tenant isolation remains enforced.
                 */

                BorrowerDetailsResponse response = borrowerDetailsService
                                .getBorrowerDetails(
                                                id,
                                                org.getId());

                return ResponseEntity.ok(
                                ApiResponse.ok(
                                                response));
        }

        // ============================================================
        // CREATE BORROWER
        // ============================================================

        @PostMapping
        @org.springframework.transaction.annotation.Transactional
        public ResponseEntity<ApiResponse<BorrowerResponse>> create(
                        @Valid @RequestBody BorrowerRequest req) {

                if (req.getEmail() == null || req.getEmail().isBlank()) {
                        throw new org.springframework.web.server.ResponseStatusException(
                                        HttpStatus.BAD_REQUEST, "Email is required.");
                }

                String maritalStatus = req.getMaritalStatus().strip().toUpperCase(java.util.Locale.ROOT);
                boolean married = "MARRIED".equals(maritalStatus);
                boolean single = "SINGLE".equals(maritalStatus);
                String spouseName = trimToNull(req.getSpouseFullName());
                String spouseId = trimToNull(req.getSpouseNationalId());
                String spousePhone = trimToNull(req.getSpousePhone());
                String certificate = trimToNull(req.getSingleCertificateNumber());
                if (married && spouseName == null) {
                        throw invalidBorrower("Spouse full name is required for a married borrower.");
                }
                if (married && spouseId != null && !spouseId.matches("[0-9]{16}")) {
                        throw invalidBorrower("Spouse National ID must contain exactly 16 digits.");
                }
                if (married && spousePhone != null && !spousePhone.matches("0[0-9]{9}")) {
                        throw invalidBorrower("Spouse phone must contain exactly 10 digits and start with 0.");
                }
                if (single && certificate == null) {
                        throw invalidBorrower("Single status certificate number is required.");
                }
                LocalDate dateOfBirth = null;
                if (trimToNull(req.getDateOfBirth()) != null) {
                        try {
                                dateOfBirth = LocalDate.parse(req.getDateOfBirth().strip());
                        } catch (java.time.format.DateTimeParseException ex) {
                                throw invalidBorrower("Enter a valid date of birth.");
                        }
                }

                Organization org = currentUserUtil
                                .getCurrentUser()
                                .getOrganization();

                /*
                 * Prevent duplicate email addresses
                 * inside the same organization.
                 */

                if (req.getEmail() != null
                                &&
                                borrowerRepo.existsByEmailAndOrganization(
                                                req.getEmail(),
                                                org)) {

                        throw new org.springframework.web.server.ResponseStatusException(
                                        HttpStatus.CONFLICT, "Email already registered.");
                }

                Borrower borrower = Borrower.builder()

                                // ------------------------------------------------
                                // ORGANIZATION
                                // ------------------------------------------------

                                .organization(
                                                org)

                                // ------------------------------------------------
                                // PERSONAL
                                // ------------------------------------------------

                                .firstName(
                                                req.getFirstName())

                                .lastName(
                                                req.getLastName())

                                .email(
                                                req.getEmail())

                                .phone(
                                                req.getPhone())

                                .alternatePhone(
                                                req.getPhone())

                                .nationalId(
                                                req.getNationalId())

                                .passportNumber(
                                                req.getPassportNumber())

                                .taxIdentificationNumber(
                                                req.getTaxIdentificationNumber())

                                .dateOfBirth(dateOfBirth)
                                .gender(req.getGender())
                                .maritalStatus(maritalStatus)
                                .singleCertificateNumber(single ? certificate : null)
                                .spouseFullName(married ? spouseName : null)
                                .spouseNationalId(married ? spouseId : null)
                                .spousePhone(married ? spousePhone : null)
                                .spouseConsent(married ? Boolean.TRUE.equals(req.getSpouseConsent()) : null)

                                .nationality(
                                                req.getNationality())
                                .placeOfBirth(
                                                req.getPlaceOfBirth())

                                // ------------------------------------------------
                                // ADDRESS
                                // ------------------------------------------------

                                .addressLine1(
                                                req.getAddressLine1())

                                .addressLine2(
                                                req.getAddressLine2())

                                .city(
                                                req.getCity())

                                .stateProvince(
                                                req.getStateProvince())
                                .physicalAddressProvince(
                                                req.getPhysicalAddressProvince())
                                .physicalAddressDistrict(
                                                req.getPhysicalAddressDistrict())
                                .physicalAddressSector(
                                                req.getPhysicalAddressSector())
                                .physicalAddressCell(
                                                req.getPhysicalAddressCell())
                                .physicalAddressVillage(
                                                req.getPhysicalAddressVillage())

                                .postalCode(
                                                req.getPostalCode())

                                .country(
                                                req.getCountry())

                                // ------------------------------------------------
                                // EMPLOYMENT
                                // ------------------------------------------------

                                .employerName(
                                                req.getEmployerName())

                                .employmentType(
                                                req.getEmploymentType())

                                .jobTitle(
                                                req.getJobTitle())

                                // ------------------------------------------------
                                // FINANCIAL
                                // ------------------------------------------------

                                .monthlyIncome(
                                                req.getMonthlyIncomeDecimal())

                                .monthlyExpenses(
                                                req.getMonthlyExpensesDecimal())

                                .netWorth(
                                                req.getNetWorthDecimal())

                                // ------------------------------------------------
                                // CREDIT
                                // ------------------------------------------------

                                .creditScore(
                                                req.getCreditScore())

                                .creditBureau(
                                                req.getCreditBureau())

                                // ------------------------------------------------
                                // BANK
                                // ------------------------------------------------

                                .bankName(
                                                req.getBankName())

                                .bankAccountNumber(
                                                req.getBankAccountNumber())

                                .bankBranch(
                                                req.getBankBranch())

                                // ------------------------------------------------
                                // STATUS
                                // ------------------------------------------------

                                .status(
                                                Borrower.BorrowerStatus.ACTIVE)

                                .build();

                Borrower saved;
                try {
                        saved = borrowerRepo.saveAndFlush(borrower);
                } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                        Throwable cause = ex.getMostSpecificCause();
                        if (cause instanceof java.sql.SQLException sql) {
                                if ("23505".equals(sql.getSQLState())) {
                                        throw new org.springframework.web.server.ResponseStatusException(
                                                        HttpStatus.CONFLICT,
                                                        "A borrower with these identifying details already exists.");
                                }
                                if ("22001".equals(sql.getSQLState())) {
                                        throw invalidBorrower("One or more borrower fields exceed the allowed length.");
                                }
                        }
                        throw ex;
                }

                // ========================================================
                // AUDIT
                // ========================================================

                auditService.log(
                                org,
                                currentUserUtil
                                                .getCurrentUser(),

                                "BORROWER_CREATED",

                                "BORROWER",

                                String.valueOf(
                                                saved.getId()),

                                "Created borrower "
                                                + saved.getFirstName()
                                                + " "
                                                + saved.getLastName());

                return ResponseEntity
                                .status(
                                                HttpStatus.CREATED)
                                .body(
                                                ApiResponse.ok(
                                                                "Borrower created",
                                                                ResponseDtoMapper.borrower(saved)));
        }

        // ============================================================
        // UPDATE BORROWER
        // ============================================================

        @PutMapping("/{id}")
        public ResponseEntity<ApiResponse<BorrowerResponse>> update(
                        @PathVariable Long id,

                        @Valid @RequestBody BorrowerRequest req) {

                Organization org = currentUserUtil
                                .getCurrentUser()
                                .getOrganization();

                Borrower borrower = borrowerRepo
                                .findById(id)
                                .orElseThrow(
                                                () -> new RuntimeException(
                                                                "Borrower not found"));

                /*
                 * MULTI-TENANT SECURITY
                 */

                if (borrower.getOrganization() == null
                                ||
                                borrower
                                                .getOrganization()
                                                .getId() == null
                                ||
                                !borrower
                                                .getOrganization()
                                                .getId()
                                                .equals(
                                                                org.getId())) {

                        throw new RuntimeException(
                                        "Access denied");
                }

                // ========================================================
                // UPDATE BASIC INFORMATION
                // ========================================================

                if (req.getFirstName() != null) {

                        borrower.setFirstName(
                                        req.getFirstName());
                }

                if (req.getLastName() != null) {

                        borrower.setLastName(
                                        req.getLastName());
                }

                if (req.getPhone() != null) {

                        borrower.setPhone(
                                        req.getPhone());
                        // Keep the legacy alternate-phone column synchronized: the product collects one
                        // phone only.
                        borrower.setAlternatePhone(req.getPhone());
                }

                if (req.getPlaceOfBirth() != null) {
                        borrower.setPlaceOfBirth(req.getPlaceOfBirth());
                }
                if (req.getNationality() != null) {
                        borrower.setNationality(req.getNationality());
                }
                if (req.getAddressLine1() != null) {
                        borrower.setAddressLine1(req.getAddressLine1());
                }
                if (req.getPhysicalAddressProvince() != null) {
                        borrower.setPhysicalAddressProvince(req.getPhysicalAddressProvince());
                }
                if (req.getPhysicalAddressDistrict() != null) {
                        borrower.setPhysicalAddressDistrict(req.getPhysicalAddressDistrict());
                }
                if (req.getPhysicalAddressSector() != null) {
                        borrower.setPhysicalAddressSector(req.getPhysicalAddressSector());
                }
                if (req.getPhysicalAddressCell() != null) {
                        borrower.setPhysicalAddressCell(req.getPhysicalAddressCell());
                }
                if (req.getPhysicalAddressVillage() != null) {
                        borrower.setPhysicalAddressVillage(req.getPhysicalAddressVillage());
                }
                if (req.getCountry() != null) {
                        borrower.setCountry(req.getCountry());
                }

                if (req.getEmail() != null) {
                        borrower.setEmail(req.getEmail().trim());
                }
                if (req.getPassportNumber() != null) {
                        borrower.setPassportNumber(req.getPassportNumber().trim());
                }
                if (req.getTaxIdentificationNumber() != null) {
                        borrower.setTaxIdentificationNumber(req.getTaxIdentificationNumber().trim());
                }
                if (req.getDateOfBirth() != null) {
                        borrower.setDateOfBirth(LocalDate.parse(req.getDateOfBirth()));
                }
                if (req.getGender() != null) {
                        borrower.setGender(req.getGender().trim());
                }
                if (req.getMaritalStatus() != null) {
                        borrower.setMaritalStatus(req.getMaritalStatus().trim());
                }
                if (req.getAddressLine2() != null) {
                        borrower.setAddressLine2(req.getAddressLine2().trim());
                }
                if (req.getCity() != null) {
                        borrower.setCity(req.getCity().trim());
                }
                if (req.getStateProvince() != null) {
                        borrower.setStateProvince(req.getStateProvince().trim());
                }
                if (req.getPostalCode() != null) {
                        borrower.setPostalCode(req.getPostalCode().trim());
                }
                if (req.getNetWorthDecimal() != null) {
                        borrower.setNetWorth(req.getNetWorthDecimal());
                }
                if (req.getMonthlyExpensesDecimal() != null) {
                        borrower.setMonthlyExpenses(req.getMonthlyExpensesDecimal());
                }
                if (req.getJobTitle() != null) {
                        borrower.setJobTitle(req.getJobTitle().trim());
                }
                if (req.getBankName() != null) {
                        borrower.setBankName(req.getBankName().trim());
                }
                if (req.getBankAccountNumber() != null) {
                        borrower.setBankAccountNumber(req.getBankAccountNumber().trim());
                }
                if (req.getBankBranch() != null) {
                        borrower.setBankBranch(req.getBankBranch().trim());
                }
                if (borrower.getNationality() == null || borrower.getNationality().isBlank()) {
                        borrower.setNationality("Rwandan");
                }

                // ========================================================
                // UPDATE FINANCIAL INFORMATION
                // ========================================================

                if (req.getMonthlyIncomeDecimal() != null) {

                        borrower.setMonthlyIncome(
                                        req.getMonthlyIncomeDecimal());
                }

                if (req.getCreditScore() != null) {

                        borrower.setCreditScore(
                                        req.getCreditScore());
                }

                // ========================================================
                // UPDATE EMPLOYMENT
                // ========================================================

                if (req.getEmployerName() != null) {

                        borrower.setEmployerName(
                                        req.getEmployerName());
                }

                if (req.getEmploymentType() != null) {

                        borrower.setEmploymentType(
                                        req.getEmploymentType());
                }

                // ========================================================
                // SAVE
                // ========================================================

                Borrower saved = borrowerRepo.save(
                                borrower);

                // ========================================================
                // AUDIT
                // ========================================================

                auditService.log(
                                org,
                                currentUserUtil
                                                .getCurrentUser(),

                                "BORROWER_UPDATED",

                                "BORROWER",

                                String.valueOf(
                                                saved.getId()),

                                "Updated borrower "
                                                + saved.getFirstName()
                                                + " "
                                                + saved.getLastName());

                return ResponseEntity.ok(
                                ApiResponse.ok(
                                                "Borrower updated",
                                                ResponseDtoMapper.borrower(saved)));
        }

        private static String trimToNull(String value) {
                return value == null || value.isBlank() ? null : value.strip();
        }

        private static org.springframework.web.server.ResponseStatusException invalidBorrower(String message) {
                return new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
}
