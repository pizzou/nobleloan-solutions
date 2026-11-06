
package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.*;
import com.patrick.fintech.loan_backend.mapper.ResponseDtoMapper;
import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.BorrowerRepository;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.BorrowerDetailsService;
import com.patrick.fintech.loan_backend.service.BorrowerDocumentRequirements;
import com.patrick.fintech.loan_backend.service.BorrowerFileService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

        private final BorrowerFileService borrowerFileService;

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
        // CREATE BORROWER WITH REQUIRED KYC DOCUMENTS
        // ============================================================

        /**
         * Staff-assisted registration mirrors the public application's KYC
         * requirements. The borrower part and every applicable document are
         * committed atomically. A failed or missing upload rolls back borrower
         * creation as well.
         *
         * Multipart parts: borrower (application/json), nationalId,
         * bankStatement, selfie, marriageCertificate, singleCertificate,
         * payslip and businessRegistration. Only profile-applicable files
         * are required.
         */
        @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
        public ResponseEntity<ApiResponse<BorrowerResponse>> create(
                        @Valid @RequestPart("borrower") BorrowerRequest req,
                        @RequestPart(value = "nationalId", required = false) MultipartFile nationalIdFile,
                        @RequestPart(value = "bankStatement", required = false) MultipartFile bankStatementFile,
                        @RequestPart(value = "selfie", required = false) MultipartFile selfieFile,
                        @RequestPart(value = "marriageCertificate", required = false) MultipartFile marriageCertificateFile,
                        @RequestPart(value = "singleCertificate", required = false) MultipartFile singleCertificateFile,
                        @RequestPart(value = "payslip", required = false) MultipartFile payslipFile,
                        @RequestPart(value = "businessRegistration", required = false) MultipartFile businessRegistrationFile)
                        throws IOException {

                if (req == null) {
                        throw new org.springframework.web.server.ResponseStatusException(
                                        HttpStatus.BAD_REQUEST, "Borrower details are required.");
                }

                User staffUser = currentUserUtil.getCurrentUser();
                Organization org = staffUser == null ? null : staffUser.getOrganization();
                if (org == null || org.getId() == null) {
                        throw new org.springframework.web.server.ResponseStatusException(
                                        HttpStatus.FORBIDDEN, "Your user account is not assigned to an organization.");
                }

                String maritalStatus = req.getMaritalStatus() == null
                                ? ""
                                : req.getMaritalStatus().trim().toUpperCase(Locale.ROOT);
                if (!BorrowerDocumentRequirements.isSupportedMaritalStatus(maritalStatus)) {
                        throw new org.springframework.web.server.ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Marital status must be SINGLE or MARRIED. The matching certificate is mandatory.");
                }
                req.setMaritalStatus(maritalStatus);

                String employmentType = req.getEmploymentType() == null
                                ? ""
                                : req.getEmploymentType().trim().toUpperCase(Locale.ROOT)
                                                .replace('-', '_').replace(' ', '_');
                if (employmentType.isBlank()) {
                        throw new org.springframework.web.server.ResponseStatusException(
                                        HttpStatus.BAD_REQUEST, "Employment type is required.");
                }
                req.setEmploymentType(employmentType);

                String spouseName = cleanOptionalValue(req.getSpouseFullName());
                String spouseNationalId = cleanOptionalValue(req.getSpouseNationalId());
                String spousePhone = cleanOptionalValue(req.getSpousePhone());
                String singleCertificateNumber = cleanOptionalValue(req.getSingleCertificateNumber());

                if ("MARRIED".equals(maritalStatus) && spouseName == null) {
                        throw new org.springframework.web.server.ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Spouse full name is required for a married borrower.");
                }
                if ("SINGLE".equals(maritalStatus) && singleCertificateNumber == null) {
                        throw new org.springframework.web.server.ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Single-status certificate number is required for a single borrower.");
                }
                if (spouseNationalId != null && !spouseNationalId.matches("[0-9]{16}")) {
                        throw new org.springframework.web.server.ResponseStatusException(
                                        HttpStatus.BAD_REQUEST, "Spouse National ID must contain exactly 16 digits.");
                }
                if (spousePhone != null && !spousePhone.matches("0[0-9]{9}")) {
                        throw new org.springframework.web.server.ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Spouse phone must contain exactly 10 digits and start with 0.");
                }

                List<DocumentType> requiredDocuments = BorrowerDocumentRequirements.forProfile(
                                maritalStatus, employmentType);
                Map<DocumentType, MultipartFile> suppliedFiles = new EnumMap<>(DocumentType.class);
                suppliedFiles.put(DocumentType.NATIONAL_ID, nationalIdFile);
                suppliedFiles.put(DocumentType.BANK_STATEMENT, bankStatementFile);
                suppliedFiles.put(DocumentType.SELFIE, selfieFile);
                suppliedFiles.put(DocumentType.MARRIAGE_CERTIFICATE, marriageCertificateFile);
                suppliedFiles.put(DocumentType.SINGLE_CERTIFICATE, singleCertificateFile);
                suppliedFiles.put(DocumentType.PAYSLIP, payslipFile);
                suppliedFiles.put(DocumentType.BUSINESS_REGISTRATION, businessRegistrationFile);

                List<String> missingDocuments = requiredDocuments.stream()
                                .filter(type -> suppliedFiles.get(type) == null || suppliedFiles.get(type).isEmpty())
                                .map(BorrowerController::documentLabel)
                                .toList();
                if (!missingDocuments.isEmpty()) {
                        throw new org.springframework.web.server.ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Upload all required borrower documents before creating the profile: "
                                                        + String.join(", ", missingDocuments) + ".");
                }

                if (req.getEmail() != null && !req.getEmail().isBlank()
                                && borrowerRepo.existsByEmailAndOrganization(req.getEmail().trim(), org)) {
                        throw new org.springframework.web.server.ResponseStatusException(
                                        HttpStatus.CONFLICT, "Email already registered.");
                }

                Borrower borrower = Borrower.builder()
                                .organization(org)
                                .firstName(req.getFirstName())
                                .lastName(req.getLastName())
                                .email(req.getEmail() != null ? req.getEmail().trim() : null)
                                .phone(req.getPhone())
                                .alternatePhone(req.getAlternatePhone())
                                .nationalId(req.getNationalId())
                                .passportNumber(req.getPassportNumber())
                                .taxIdentificationNumber(req.getTaxIdentificationNumber())
                                .dateOfBirth(req.getDateOfBirth() != null && !req.getDateOfBirth().isBlank()
                                                ? LocalDate.parse(req.getDateOfBirth())
                                                : null)
                                .gender(req.getGender())
                                .maritalStatus(maritalStatus)
                                .singleCertificateNumber("SINGLE".equals(maritalStatus)
                                                ? singleCertificateNumber
                                                : null)
                                .spouseFullName("MARRIED".equals(maritalStatus) ? spouseName : null)
                                .spouseNationalId("MARRIED".equals(maritalStatus) ? spouseNationalId : null)
                                .spousePhone("MARRIED".equals(maritalStatus) ? spousePhone : null)
                                .spouseConsent("MARRIED".equals(maritalStatus)
                                                ? Boolean.TRUE.equals(req.getSpouseConsent())
                                                : null)
                                .nationality(req.getNationality())
                                .placeOfBirth(req.getPlaceOfBirth())
                                .addressLine1(req.getAddressLine1())
                                .addressLine2(req.getAddressLine2())
                                .city(req.getCity())
                                .stateProvince(req.getStateProvince())
                                .physicalAddressProvince(req.getPhysicalAddressProvince())
                                .physicalAddressDistrict(req.getPhysicalAddressDistrict())
                                .physicalAddressSector(req.getPhysicalAddressSector())
                                .physicalAddressCell(req.getPhysicalAddressCell())
                                .physicalAddressVillage(req.getPhysicalAddressVillage())
                                .postalCode(req.getPostalCode())
                                .country(req.getCountry())
                                .employerName(req.getEmployerName())
                                .employmentType(employmentType)
                                .jobTitle(req.getJobTitle())
                                .monthlyIncome(req.getMonthlyIncome())
                                .monthlyExpenses(req.getMonthlyExpenses())
                                .netWorth(req.getNetWorth())
                                .creditScore(req.getCreditScore())
                                .creditBureau(req.getCreditBureau())
                                .bankName(req.getBankName())
                                .bankAccountNumber(req.getBankAccountNumber())
                                .bankBranch(req.getBankBranch())
                                .status(Borrower.BorrowerStatus.ACTIVE)
                                .build();

                Borrower saved = borrowerRepo.save(borrower);

                // Staff documents are borrower-level KYC evidence and start
                // in PENDING state. They must be reviewed before disbursement.
                for (DocumentType type : requiredDocuments) {
                        BorrowerFile stored = borrowerFileService.upload(
                                        saved.getId(), suppliedFiles.get(type), type, false);
                        auditService.log(
                                        org,
                                        staffUser,
                                        "DOCUMENT_UPLOADED",
                                        "BORROWER_FILE",
                                        String.valueOf(stored.getId()),
                                        "Staff uploaded required " + type.name()
                                                        + " during borrower onboarding for borrower #"
                                                        + saved.getId());
                }

                auditService.log(
                                org,
                                staffUser,
                                "BORROWER_CREATED",
                                "BORROWER",
                                String.valueOf(saved.getId()),
                                "Created borrower " + saved.getFirstName() + " " + saved.getLastName()
                                                + " with " + requiredDocuments.size()
                                                + " required KYC documents.");

                return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                                "Borrower created and required documents uploaded. Documents must be verified before loan disbursement.",
                                ResponseDtoMapper.borrower(saved)));
        }

        private static String cleanOptionalValue(String value) {
                if (value == null) {
                        return null;
                }
                String cleaned = value.trim();
                return cleaned.isEmpty() ? null : cleaned;
        }

        private static String documentLabel(DocumentType type) {
                return switch (type) {
                        case NATIONAL_ID -> "National ID";
                        case BANK_STATEMENT -> "Bank statement";
                        case SELFIE -> "Borrower selfie/photo";
                        case MARRIAGE_CERTIFICATE -> "Marriage certificate";
                        case SINGLE_CERTIFICATE -> "Single-status certificate";
                        case PAYSLIP -> "Recent payslip";
                        case BUSINESS_REGISTRATION -> "Business registration certificate";
                        default -> type.name().replace('_', ' ');
                };
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

                if (req.getAlternatePhone() != null) {
                        borrower.setAlternatePhone(req.getAlternatePhone().trim());
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
                if (req.getNetWorth() != null) {
                        borrower.setNetWorth(req.getNetWorth());
                }
                if (req.getMonthlyExpenses() != null) {
                        borrower.setMonthlyExpenses(req.getMonthlyExpenses());
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

                if (req.getMonthlyIncome() != null) {

                        borrower.setMonthlyIncome(
                                        req.getMonthlyIncome());
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
}
