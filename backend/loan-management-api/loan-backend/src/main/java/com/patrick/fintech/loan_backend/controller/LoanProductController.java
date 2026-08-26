package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.mapper.ResponseDtoMapper;
import com.patrick.fintech.loan_backend.model.DocumentType;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanProduct;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.repository.LoanProductRepository;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/loan-products")
@RequiredArgsConstructor
@Slf4j
public class LoanProductController {

        /*
         * Noble's current defaults.
         *
         * These are DEFAULTS for newly created products only.
         *
         * They are NOT global mandatory rates. Each organization may
         * configure its own product interest rate and management fee.
         */
        private static final BigDecimal MINIMUM_LOAN_AMOUNT = new BigDecimal("500000.00");

        private static final BigDecimal DEFAULT_INTEREST_RATE = new BigDecimal("5.00");

        private static final BigDecimal DEFAULT_MANAGEMENT_FEE_PERCENT = new BigDecimal("5.00");

        private static final BigDecimal DEFAULT_APPLICATION_FEE_PERCENT = new BigDecimal("2.00");

        private static final int MAXIMUM_TERM_MONTHS = 6;

        private static final int MINIMUM_TERM_MONTHS = 1;

        private static final int MONEY_SCALE = 2;

        private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;

        /*
         * Percentages are stored with sufficient precision for financial
         * configuration while avoiding unnecessary trailing precision.
         */
        private static final int PERCENT_SCALE = 4;

        private final LoanProductRepository productRepo;

        private final OrganizationRepository orgRepo;

        private final CurrentUserUtil currentUserUtil;

        private final AuditService auditService;

        // ============================================================
        // LIST PRODUCTS
        // ============================================================

        @GetMapping
        public ResponseEntity<ApiResponse<Object>> list() {

                Long organizationId = currentUserUtil.getCurrentOrganizationId();

                validateOrganizationId(
                                organizationId);

                List<LoanProduct> products = productRepo.findByOrganization_IdOrderByDisplayOrderAsc(
                                organizationId);

                if (products == null) {
                        products = new ArrayList<>();
                }

                return ResponseEntity.ok(
                                ApiResponse.safe(products));
        }

        // ============================================================
        // CREATE PRODUCT
        // ============================================================

        @PostMapping
        @PreAuthorize("hasRole('ADMIN')")
        public ResponseEntity<ApiResponse<Object>> create(
                        @RequestBody Map<String, Object> body) {

                Long organizationId = currentUserUtil.getCurrentOrganizationId();

                validateOrganizationId(
                                organizationId);

                if (body == null) {
                        throw new IllegalArgumentException(
                                        "Loan product request body is required.");
                }

                Organization organization = orgRepo.findById(
                                organizationId)
                                .orElseThrow(
                                                () -> new IllegalArgumentException(
                                                                "Organization not found."));

                LoanProduct product = new LoanProduct();

                product.setOrganization(
                                organization);

                /*
                 * Apply the organization/product configuration.
                 */
                applyFields(
                                product,
                                body,
                                true);

                /*
                 * Final validation before persistence.
                 */
                validateProduct(
                                product);

                product = productRepo.save(
                                product);

                log.info(
                                "Loan product created: id={}, organizationId={}, name={}, loanType={}",
                                product.getId(),
                                organizationId,
                                product.getName(),
                                product.getLoanType());

                auditService.log(
                                organization,
                                currentUserUtil.getCurrentUser(),
                                "LOAN_PRODUCT_CREATED",
                                "LOAN_PRODUCT",
                                String.valueOf(
                                                product.getId()),
                                "Created product \""
                                                + product.getName()
                                                + "\" at "
                                                + product.getInterestRateDecimal()
                                                + "% "
                                                + product.getInterestRateType()
                                                + " interest and "
                                                + product.getManagementFeePercentDecimal()
                                                + "% management fee.");

                return ResponseEntity
                                .status(
                                                HttpStatus.CREATED)
                                .body(
                                                ApiResponse.safe(
                                                                "Product created",
                                                                product));
        }

        // ============================================================
        // UPDATE PRODUCT
        // ============================================================

        @PutMapping("/{id}")
        @PreAuthorize("hasRole('ADMIN')")
        public ResponseEntity<ApiResponse<Object>> update(
                        @PathVariable Long id,
                        @RequestBody Map<String, Object> body) {

                validateProductId(
                                id);

                if (body == null) {
                        throw new IllegalArgumentException(
                                        "Loan product request body is required.");
                }

                Long organizationId = currentUserUtil.getCurrentOrganizationId();

                validateOrganizationId(
                                organizationId);

                LoanProduct product = productRepo.findById(
                                id)
                                .orElseThrow(
                                                () -> new IllegalArgumentException(
                                                                "Loan product not found."));

                assertOwnership(
                                product);

                /*
                 * Only supplied fields are changed.
                 */
                applyFields(
                                product,
                                body,
                                false);

                validateProduct(
                                product);

                product = productRepo.save(
                                product);

                log.info(
                                "Loan product updated: id={}, organizationId={}, name={}, loanType={}, interestRate={}, managementFee={}, applicationFee={}",
                                product.getId(),
                                organizationId,
                                product.getName(),
                                product.getLoanType(),
                                product.getInterestRateDecimal(),
                                product.getManagementFeePercentDecimal(),
                                product.getApplicationFeePercentDecimal());

                auditService.log(
                                product.getOrganization(),
                                currentUserUtil.getCurrentUser(),
                                "LOAN_PRODUCT_UPDATED",
                                "LOAN_PRODUCT",
                                String.valueOf(
                                                product.getId()),
                                "Updated product \""
                                                + product.getName()
                                                + "\". Interest="
                                                + product.getInterestRateDecimal()
                                                + "%, management fee="
                                                + product.getManagementFeePercentDecimal()
                                                + "%, application fee="
                                                + product.getApplicationFeePercentDecimal()
                                                + "%.");

                return ResponseEntity.ok(
                                ApiResponse.safe(
                                                "Product updated",
                                                product));
        }

        // ============================================================
        // TOGGLE ACTIVE
        // ============================================================

        @PostMapping("/{id}/toggle")
        @PreAuthorize("hasRole('ADMIN')")
        public ResponseEntity<ApiResponse<Object>> toggleActive(
                        @PathVariable Long id) {

                validateProductId(
                                id);

                LoanProduct product = productRepo.findById(
                                id)
                                .orElseThrow(
                                                () -> new IllegalArgumentException(
                                                                "Loan product not found."));

                assertOwnership(
                                product);

                boolean newStatus = !Boolean.TRUE.equals(
                                product.getActive());

                product.setActive(
                                newStatus);

                product = productRepo.save(
                                product);

                log.info(
                                "Loan product status changed: id={}, organizationId={}, active={}",
                                product.getId(),
                                product.getOrganization() != null
                                                ? product.getOrganization().getId()
                                                : null,
                                product.getActive());

                auditService.log(
                                product.getOrganization(),
                                currentUserUtil.getCurrentUser(),
                                "LOAN_PRODUCT_TOGGLED",
                                "LOAN_PRODUCT",
                                String.valueOf(
                                                product.getId()),
                                product.getName()
                                                + " set to "
                                                + (product.getActive()
                                                                ? "ACTIVE"
                                                                : "INACTIVE"));

                return ResponseEntity.ok(
                                ApiResponse.safe(
                                                product));
        }

        // ============================================================
        // DELETE PRODUCT
        // ============================================================

        @DeleteMapping("/{id}")
        @PreAuthorize("hasRole('ADMIN')")
        public ResponseEntity<ApiResponse<String>> delete(
                        @PathVariable Long id) {

                validateProductId(
                                id);

                LoanProduct product = productRepo.findById(
                                id)
                                .orElseThrow(
                                                () -> new IllegalArgumentException(
                                                                "Loan product not found."));

                assertOwnership(
                                product);

                String productName = product.getName();

                Organization organization = product.getOrganization();

                productRepo.delete(
                                product);

                log.info(
                                "Loan product deleted: id={}, organizationId={}, name={}",
                                id,
                                organization != null
                                                ? organization.getId()
                                                : null,
                                productName);

                auditService.log(
                                organization,
                                currentUserUtil.getCurrentUser(),
                                "LOAN_PRODUCT_DELETED",
                                "LOAN_PRODUCT",
                                String.valueOf(
                                                id),
                                "Deleted product \""
                                                + productName
                                                + "\"");

                return ResponseEntity.ok(
                                ApiResponse.safe(
                                                "Product deleted"));
        }

        // ============================================================
        // APPLY FIELDS
        // ============================================================

        private void applyFields(
                        LoanProduct product,
                        Map<String, Object> body,
                        boolean creating) {

                // ----------------------------------------------------
                // NAME
                // ----------------------------------------------------

                if (body.containsKey("name")) {

                        String name = stringValue(
                                        body.get("name"));

                        if (name == null
                                        || name.isBlank()) {

                                throw new IllegalArgumentException(
                                                "Product name is required.");
                        }

                        product.setName(
                                        name.trim());
                }

                // ----------------------------------------------------
                // ICON
                // ----------------------------------------------------

                if (body.containsKey("icon")) {

                        product.setIcon(
                                        nullableString(
                                                        body.get("icon")));
                }

                // ----------------------------------------------------
                // DESCRIPTION
                // ----------------------------------------------------

                if (body.containsKey("description")) {

                        product.setDescription(
                                        nullableString(
                                                        body.get("description")));
                }

                // ----------------------------------------------------
                // LOAN TYPE
                // ----------------------------------------------------

                if (body.containsKey("loanType")) {

                        String rawLoanType = stringValue(
                                        body.get("loanType"));

                        if (rawLoanType == null
                                        || rawLoanType.isBlank()) {

                                throw new IllegalArgumentException(
                                                "loanType is required.");
                        }

                        try {

                                product.setLoanType(
                                                Loan.LoanType.valueOf(
                                                                rawLoanType
                                                                                .trim()
                                                                                .toUpperCase()));

                        } catch (IllegalArgumentException exception) {

                                throw new IllegalArgumentException(
                                                "Unknown loan type: "
                                                                + rawLoanType,
                                                exception);
                        }
                }

                // ----------------------------------------------------
                // INTEREST RATE
                //
                // Organization-specific.
                //
                // Noble default = 5.00% monthly.
                // Other organizations may choose another value.
                // ----------------------------------------------------

                if (creating
                                || body.containsKey("interestRate")) {

                        BigDecimal requestedRate = body.containsKey(
                                        "interestRate")
                                                        ? decimalValue(
                                                                        body.get(
                                                                                        "interestRate"),
                                                                        "interestRate")
                                                        : DEFAULT_INTEREST_RATE;

                        if (requestedRate.compareTo(
                                        BigDecimal.ZERO) < 0) {

                                throw new IllegalArgumentException(
                                                "Interest rate cannot be negative.");
                        }

                        product.setInterestRate(
                                        normalizePercent(
                                                        requestedRate));
                }

                // ----------------------------------------------------
                // INTEREST RATE TYPE
                // ----------------------------------------------------

                if (creating
                                || body.containsKey("interestRateType")) {

                        /*
                         * Current platform schedule engine uses monthly
                         * rates. This is kept explicit so a future
                         * annual/daily engine can be introduced safely.
                         */
                        String requestedType = body.containsKey(
                                        "interestRateType")
                                                        ? nullableString(
                                                                        body.get(
                                                                                        "interestRateType"))
                                                        : "MONTHLY";

                        if (requestedType == null
                                        || requestedType.isBlank()) {

                                requestedType = "MONTHLY";
                        }

                        if (!"MONTHLY".equalsIgnoreCase(
                                        requestedType.trim())) {

                                throw new IllegalArgumentException(
                                                "Interest rate type must be MONTHLY.");
                        }

                        product.setInterestRateType(
                                        "MONTHLY");
                }

                // ----------------------------------------------------
                // MINIMUM AMOUNT
                // ----------------------------------------------------

                if (creating
                                || body.containsKey("minAmount")) {

                        BigDecimal requestedMinimum = body.containsKey(
                                        "minAmount")
                                                        ? decimalValue(
                                                                        body.get(
                                                                                        "minAmount"),
                                                                        "minAmount")
                                                        : MINIMUM_LOAN_AMOUNT;

                        if (requestedMinimum.compareTo(
                                        MINIMUM_LOAN_AMOUNT) < 0) {

                                throw new IllegalArgumentException(
                                                "Minimum loan amount cannot be below RWF "
                                                                + MINIMUM_LOAN_AMOUNT);
                        }

                        product.setMinAmount(
                                        normalizeMoney(
                                                        requestedMinimum));
                }

                // ----------------------------------------------------
                // MAXIMUM AMOUNT
                //
                // null = unlimited.
                // ----------------------------------------------------

                if (creating) {

                        product.setMaxAmount(
                                        null);

                } else if (Boolean.TRUE.equals(
                                body.get("unlimited"))) {

                        product.setMaxAmount(
                                        null);

                } else if (body.containsKey(
                                "maxAmount")) {

                        Object rawMaximum = body.get(
                                        "maxAmount");

                        if (rawMaximum == null
                                        || String.valueOf(
                                                        rawMaximum)
                                                        .isBlank()) {

                                product.setMaxAmount(
                                                null);

                        } else {

                                BigDecimal maximum = decimalValue(
                                                rawMaximum,
                                                "maxAmount");

                                BigDecimal minimum = product.getMinAmountDecimal() != null
                                                ? product.getMinAmountDecimal()
                                                : MINIMUM_LOAN_AMOUNT;

                                if (maximum.compareTo(
                                                minimum) < 0) {

                                        throw new IllegalArgumentException(
                                                        "Maximum loan amount cannot be below minimum loan amount.");
                                }

                                product.setMaxAmount(
                                                normalizeMoney(
                                                                maximum));
                        }
                }

                // ----------------------------------------------------
                // PROCESSING FEE
                //
                // Organization/product-specific.
                //
                // Noble default = 2.00%.
                // ----------------------------------------------------

                if (creating
                                || body.containsKey(
                                                "applicationFeePercent")) {

                        BigDecimal requestedProcessingFee = body.containsKey(
                                        "applicationFeePercent")
                                                        ? decimalValue(
                                                                        body.get(
                                                                                        "applicationFeePercent"),
                                                                        "applicationFeePercent")
                                                        : DEFAULT_APPLICATION_FEE_PERCENT;

                        if (requestedProcessingFee.compareTo(
                                        BigDecimal.ZERO) < 0) {

                                throw new IllegalArgumentException(
                                                "Processing fee percentage cannot be negative.");
                        }

                        product.setProcessingFeePercent(
                                        normalizePercent(
                                                        requestedProcessingFee));
                }

                // ----------------------------------------------------
                // MANAGEMENT FEE
                //
                // Organization/product-specific.
                //
                // Noble default = 5.00%.
                // ----------------------------------------------------

                if (creating
                                || body.containsKey(
                                                "managementFeePercent")) {

                        BigDecimal requestedManagementFee = body.containsKey(
                                        "managementFeePercent")
                                                        ? decimalValue(
                                                                        body.get(
                                                                                        "managementFeePercent"),
                                                                        "managementFeePercent")
                                                        : DEFAULT_MANAGEMENT_FEE_PERCENT;

                        if (requestedManagementFee.compareTo(
                                        BigDecimal.ZERO) < 0) {

                                throw new IllegalArgumentException(
                                                "Management fee percentage cannot be negative.");
                        }

                        product.setManagementFeePercent(
                                        normalizePercent(
                                                        requestedManagementFee));
                }

                // ----------------------------------------------------
                // MINIMUM TERM
                // ----------------------------------------------------

                if (creating
                                || body.containsKey(
                                                "minTermMonths")) {

                        int minimumTerm = body.containsKey(
                                        "minTermMonths")
                                                        ? integerValue(
                                                                        body.get(
                                                                                        "minTermMonths"),
                                                                        "minTermMonths")
                                                        : MINIMUM_TERM_MONTHS;

                        if (minimumTerm < MINIMUM_TERM_MONTHS) {

                                throw new IllegalArgumentException(
                                                "Minimum term must be at least "
                                                                + MINIMUM_TERM_MONTHS
                                                                + " month.");
                        }

                        if (minimumTerm > MAXIMUM_TERM_MONTHS) {

                                throw new IllegalArgumentException(
                                                "Minimum term cannot exceed "
                                                                + MAXIMUM_TERM_MONTHS
                                                                + " months.");
                        }

                        product.setMinTermMonths(
                                        minimumTerm);
                }

                // ----------------------------------------------------
                // MAXIMUM TERM
                // ----------------------------------------------------

                if (creating
                                || body.containsKey(
                                                "maxTermMonths")) {

                        int maximumTerm = body.containsKey(
                                        "maxTermMonths")
                                                        ? integerValue(
                                                                        body.get(
                                                                                        "maxTermMonths"),
                                                                        "maxTermMonths")
                                                        : MAXIMUM_TERM_MONTHS;

                        if (maximumTerm < MINIMUM_TERM_MONTHS) {

                                throw new IllegalArgumentException(
                                                "Maximum term must be at least "
                                                                + MINIMUM_TERM_MONTHS
                                                                + " month.");
                        }

                        if (maximumTerm > MAXIMUM_TERM_MONTHS) {

                                throw new IllegalArgumentException(
                                                "Maximum term cannot exceed "
                                                                + MAXIMUM_TERM_MONTHS
                                                                + " months.");
                        }

                        product.setMaxTermMonths(
                                        maximumTerm);
                }

                // ----------------------------------------------------
                // DISPLAY ORDER
                // ----------------------------------------------------

                if (body.containsKey(
                                "displayOrder")) {

                        int displayOrder = integerValue(
                                        body.get(
                                                        "displayOrder"),
                                        "displayOrder");

                        if (displayOrder < 0) {

                                throw new IllegalArgumentException(
                                                "displayOrder cannot be negative.");
                        }

                        product.setDisplayOrder(
                                        displayOrder);
                }

                // ----------------------------------------------------
                // ACTIVE
                // ----------------------------------------------------

                if (body.containsKey(
                                "active")) {

                        product.setActive(
                                        booleanValue(
                                                        body.get(
                                                                        "active"),
                                                        "active"));
                }

                // ----------------------------------------------------
                // REQUIRED DOCUMENTS
                // ----------------------------------------------------

                if (body.containsKey(
                                "requiredDocumentTypes")) {

                        Object rawValue = body.get(
                                        "requiredDocumentTypes");

                        if (rawValue == null
                                        || rawValue.toString().isBlank()) {

                                product.setRequiredDocumentTypes(
                                                null);

                        } else {

                                String raw = rawValue.toString();

                                List<String> validatedTypes = new ArrayList<>();

                                for (String value : raw.split(",")) {

                                        String type = value.trim()
                                                        .toUpperCase();

                                        if (type.isBlank()) {
                                                continue;
                                        }

                                        try {

                                                DocumentType.valueOf(
                                                                type);

                                                if (!validatedTypes.contains(
                                                                type)) {

                                                        validatedTypes.add(
                                                                        type);
                                                }

                                        } catch (IllegalArgumentException exception) {

                                                throw new IllegalArgumentException(
                                                                "Unknown document type: "
                                                                                + type,
                                                                exception);
                                        }
                                }

                                product.setRequiredDocumentTypes(
                                                validatedTypes.isEmpty()
                                                                ? null
                                                                : String.join(
                                                                                ",",
                                                                                validatedTypes));
                        }
                }
        }

        // ============================================================
        // PRODUCT VALIDATION
        // ============================================================

        private void validateProduct(
                        LoanProduct product) {

                if (product == null) {

                        throw new IllegalArgumentException(
                                        "Loan product cannot be null.");
                }

                // ----------------------------------------------------
                // NAME
                // ----------------------------------------------------

                if (product.getName() == null
                                || product.getName().isBlank()) {

                        throw new IllegalArgumentException(
                                        "Product name is required.");
                }

                // ----------------------------------------------------
                // LOAN TYPE
                // ----------------------------------------------------

                if (product.getLoanType() == null) {

                        throw new IllegalArgumentException(
                                        "Loan type is required.");
                }

                // ----------------------------------------------------
                // INTEREST
                // ----------------------------------------------------

                BigDecimal interestRate = product.getInterestRateDecimal();

                if (interestRate == null) {

                        throw new IllegalArgumentException(
                                        "Interest rate is required.");
                }

                if (interestRate.compareTo(
                                BigDecimal.ZERO) < 0) {

                        throw new IllegalArgumentException(
                                        "Interest rate cannot be negative.");
                }

                /*
                 * Limit unreasonable accidental configurations.
                 *
                 * 100% monthly is already extremely high. The platform
                 * should not accept accidental values such as 50000%.
                 */
                if (interestRate.compareTo(
                                new BigDecimal("100.0000")) > 0) {

                        throw new IllegalArgumentException(
                                        "Interest rate cannot exceed 100% per month.");
                }

                // ----------------------------------------------------
                // INTEREST RATE TYPE
                // ----------------------------------------------------

                String interestRateType = product.getInterestRateType();

                if (interestRateType == null
                                || !"MONTHLY".equalsIgnoreCase(
                                                interestRateType.trim())) {

                        throw new IllegalArgumentException(
                                        "Interest rate type must be MONTHLY.");
                }

                // ----------------------------------------------------
                // MINIMUM AMOUNT
                // ----------------------------------------------------

                BigDecimal minimumAmount = product.getMinAmountDecimal();

                if (minimumAmount == null) {

                        throw new IllegalArgumentException(
                                        "Minimum loan amount is required.");
                }

                minimumAmount = normalizeMoney(
                                minimumAmount);

                if (minimumAmount.compareTo(
                                MINIMUM_LOAN_AMOUNT) < 0) {

                        throw new IllegalArgumentException(
                                        "Minimum loan amount cannot be below RWF "
                                                        + MINIMUM_LOAN_AMOUNT);
                }

                // ----------------------------------------------------
                // MAXIMUM AMOUNT
                // ----------------------------------------------------

                BigDecimal maximumAmount = product.getMaxAmountDecimal();

                if (maximumAmount != null) {

                        maximumAmount = normalizeMoney(
                                        maximumAmount);

                        if (maximumAmount.compareTo(
                                        minimumAmount) < 0) {

                                throw new IllegalArgumentException(
                                                "Maximum loan amount cannot be below minimum loan amount.");
                        }
                }

                // ----------------------------------------------------
                // MANAGEMENT FEE
                // ----------------------------------------------------

                BigDecimal managementFee = product.getManagementFeePercentDecimal();

                if (managementFee == null) {

                        throw new IllegalArgumentException(
                                        "Management fee percentage is required.");
                }

                if (managementFee.compareTo(
                                BigDecimal.ZERO) < 0) {

                        throw new IllegalArgumentException(
                                        "Management fee percentage cannot be negative.");
                }

                if (managementFee.compareTo(
                                new BigDecimal("100.0000")) > 0) {

                        throw new IllegalArgumentException(
                                        "Management fee percentage cannot exceed 100% per month.");
                }

                // ----------------------------------------------------
                // PROCESSING FEE
                // ----------------------------------------------------

                BigDecimal applicationFee = product.getApplicationFeePercentDecimal();

                if (applicationFee == null) {

                        throw new IllegalArgumentException(
                                        "Processing fee percentage is required.");
                }

                if (applicationFee.compareTo(
                                BigDecimal.ZERO) < 0) {

                        throw new IllegalArgumentException(
                                        "Processing fee percentage cannot be negative.");
                }

                if (applicationFee.compareTo(
                                new BigDecimal("100.0000")) > 0) {

                        throw new IllegalArgumentException(
                                        "Processing fee percentage cannot exceed 100%.");
                }

                // ----------------------------------------------------
                // TERMS
                // ----------------------------------------------------

                Integer minimumTerm = product.getMinTermMonths();

                Integer maximumTerm = product.getMaxTermMonths();

                if (minimumTerm == null) {

                        throw new IllegalArgumentException(
                                        "Minimum term is required.");
                }

                if (maximumTerm == null) {

                        throw new IllegalArgumentException(
                                        "Maximum term is required.");
                }

                if (minimumTerm < MINIMUM_TERM_MONTHS) {

                        throw new IllegalArgumentException(
                                        "Minimum term must be at least "
                                                        + MINIMUM_TERM_MONTHS
                                                        + " month.");
                }

                if (minimumTerm > MAXIMUM_TERM_MONTHS) {

                        throw new IllegalArgumentException(
                                        "Minimum term cannot exceed "
                                                        + MAXIMUM_TERM_MONTHS
                                                        + " months.");
                }

                if (maximumTerm < minimumTerm) {

                        throw new IllegalArgumentException(
                                        "Maximum term cannot be below minimum term.");
                }

                if (maximumTerm > MAXIMUM_TERM_MONTHS) {

                        throw new IllegalArgumentException(
                                        "Maximum term cannot exceed "
                                                        + MAXIMUM_TERM_MONTHS
                                                        + " months.");
                }

                // ----------------------------------------------------
                // ACTIVE
                // ----------------------------------------------------

                if (product.getActive() == null) {

                        product.setActive(
                                        true);
                }
        }

        // ============================================================
        // ORGANIZATION OWNERSHIP
        // ============================================================

        private void assertOwnership(
                        LoanProduct product) {

                if (product == null
                                || product.getOrganization() == null
                                || product.getOrganization().getId() == null) {

                        throw new IllegalArgumentException(
                                        "Loan product organization is missing.");
                }

                Long currentOrganizationId = currentUserUtil.getCurrentOrganizationId();

                validateOrganizationId(
                                currentOrganizationId);

                if (!Objects.equals(
                                product.getOrganization().getId(),
                                currentOrganizationId)) {

                        log.warn(
                                        "Unauthorized loan product access attempt: "
                                                        + "productId={}, productOrganizationId={}, "
                                                        + "currentOrganizationId={}",
                                        product.getId(),
                                        product.getOrganization().getId(),
                                        currentOrganizationId);

                        throw new org.springframework.security.access.AccessDeniedException(
                                        "Access denied.");
                }
        }

        // ============================================================
        // DECIMAL PARSING
        // ============================================================

        /**
         * Safely converts incoming JSON values to BigDecimal.
         *
         * Double/Float values are first converted through their textual
         * representation to avoid introducing binary floating-point noise.
         */
        private BigDecimal decimalValue(
                        Object value,
                        String fieldName) {

                if (value == null) {

                        throw new IllegalArgumentException(
                                        fieldName
                                                        + " cannot be null.");
                }

                if (value instanceof BigDecimal decimal) {

                        return decimal;
                }

                if (value instanceof Integer integer) {

                        return BigDecimal.valueOf(
                                        integer.longValue());
                }

                if (value instanceof Long longValue) {

                        return BigDecimal.valueOf(
                                        longValue);
                }

                if (value instanceof Short shortValue) {

                        return BigDecimal.valueOf(
                                        shortValue.longValue());
                }

                if (value instanceof Byte byteValue) {

                        return BigDecimal.valueOf(
                                        byteValue.longValue());
                }

                if (value instanceof Float floatValue) {

                        return new BigDecimal(
                                        floatValue.toString());
                }

                if (value instanceof Double doubleValue) {

                        return new BigDecimal(
                                        doubleValue.toString());
                }

                String raw = value.toString()
                                .trim();

                if (raw.isBlank()) {

                        throw new IllegalArgumentException(
                                        fieldName
                                                        + " cannot be blank.");
                }

                try {

                        return new BigDecimal(
                                        raw);

                } catch (NumberFormatException exception) {

                        throw new IllegalArgumentException(
                                        fieldName
                                                        + " must be a valid decimal number.",
                                        exception);
                }
        }

        // ============================================================
        // INTEGER PARSING
        // ============================================================

        private int integerValue(
                        Object value,
                        String fieldName) {

                BigDecimal decimal = decimalValue(
                                value,
                                fieldName);

                try {

                        return decimal.intValueExact();

                } catch (ArithmeticException exception) {

                        throw new IllegalArgumentException(
                                        fieldName
                                                        + " must be a whole number.",
                                        exception);
                }
        }

        // ============================================================
        // BOOLEAN PARSING
        // ============================================================

        private boolean booleanValue(
                        Object value,
                        String fieldName) {

                if (value instanceof Boolean bool) {

                        return bool;
                }

                String raw = value != null
                                ? value.toString().trim()
                                : "";

                if ("true".equalsIgnoreCase(
                                raw)) {

                        return true;
                }

                if ("false".equalsIgnoreCase(
                                raw)) {

                        return false;
                }

                throw new IllegalArgumentException(
                                fieldName
                                                + " must be true or false.");
        }

        // ============================================================
        // STRING HELPERS
        // ============================================================

        private String stringValue(
                        Object value) {

                if (value == null) {
                        return null;
                }

                return value.toString();
        }

        private String nullableString(
                        Object value) {

                if (value == null) {
                        return null;
                }

                String valueString = value.toString()
                                .trim();

                return valueString.isBlank()
                                ? null
                                : valueString;
        }

        // ============================================================
        // PERCENTAGE NORMALIZATION
        // ============================================================

        /**
         * Normalizes financial percentage configuration.
         *
         * Example:
         *
         * 5 -> 5.0000
         * 5.5 -> 5.5000
         * 5.12345 -> 5.1235
         *
         * The value remains a human-readable percentage, not a decimal
         * fraction. Therefore 5% is stored/displayed as 5.0000, not 0.05.
         */
        private BigDecimal normalizePercent(
                        BigDecimal value) {

                if (value == null) {
                        return null;
                }

                return value.setScale(
                                PERCENT_SCALE,
                                RoundingMode.HALF_UP);
        }

        // ============================================================
        // MONEY NORMALIZATION
        // ============================================================

        private BigDecimal normalizeMoney(
                        BigDecimal value) {

                if (value == null) {
                        return null;
                }

                return value.setScale(
                                MONEY_SCALE,
                                MONEY_ROUNDING);
        }

        // ============================================================
        // ORGANIZATION VALIDATION
        // ============================================================

        private void validateOrganizationId(
                        Long organizationId) {

                if (organizationId == null) {

                        throw new IllegalArgumentException(
                                        "Organization ID is required.");
                }

                if (organizationId <= 0) {

                        throw new IllegalArgumentException(
                                        "Organization ID must be greater than zero.");
                }
        }

        // ============================================================
        // PRODUCT ID VALIDATION
        // ============================================================

        private void validateProductId(
                        Long id) {

                if (id == null) {

                        throw new IllegalArgumentException(
                                        "Loan product ID is required.");
                }

                if (id <= 0) {

                        throw new IllegalArgumentException(
                                        "Loan product ID must be greater than zero.");
                }
        }
}