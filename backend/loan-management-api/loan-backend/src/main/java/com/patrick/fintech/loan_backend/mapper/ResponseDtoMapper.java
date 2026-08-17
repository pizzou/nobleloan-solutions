package com.patrick.fintech.loan_backend.mapper;

import com.patrick.fintech.loan_backend.dto.*;
import com.patrick.fintech.loan_backend.model.*;
import org.hibernate.Hibernate;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.temporal.Temporal;
import java.util.*;


public final class ResponseDtoMapper {

    private ResponseDtoMapper() {
    }

    // ============================================================
    // LOAN
    // ============================================================

    public static LoanResponse loan(Loan source) {

        if (source == null) {
            return null;
        }

        LoanResponse target = new LoanResponse();

        BeanUtils.copyProperties(
                source,
                target,
                "organization",
                "branch",
                "borrower",
                "createdBy",
                "approvedBy",
                "loanOfficer",
                "legacyAmountDouble",
                "internalNotes"
        );

        if (source.getOrganization() != null) {
            target.setOrganizationId(
                    source.getOrganization().getId()
            );
        }

        if (source.getBranch() != null) {
            target.setBranchId(
                    source.getBranch().getId()
            );
        }

        if (source.getBorrower() != null) {

            target.setBorrowerId(
                    source.getBorrower().getId()
            );

            target.setBorrowerName(
                    source.getBorrower().getFullName()
            );
        }

        if (source.getCreatedBy() != null) {
            target.setCreatedById(
                    source.getCreatedBy().getId()
            );
        }

        if (source.getApprovedBy() != null) {
            target.setApprovedById(
                    source.getApprovedBy().getId()
            );
        }

        if (source.getLoanOfficer() != null) {
            target.setLoanOfficerId(
                    source.getLoanOfficer().getId()
            );
        }

        return target;
    }

    public static Page<LoanResponse> loans(
            Page<Loan> page
    ) {

        if (page == null) {
            return Page.empty();
        }

        return page.map(
                ResponseDtoMapper::loan
        );
    }

    public static List<LoanResponse> loans(
            List<Loan> items
    ) {

        if (items == null || items.isEmpty()) {
            return List.of();
        }

        return items.stream()
                .filter(Objects::nonNull)
                .map(ResponseDtoMapper::loan)
                .toList();
    }

    // ============================================================
    // BORROWER
    // ============================================================

    public static BorrowerResponse borrower(
            Borrower source
    ) {

        if (source == null) {
            return null;
        }

        BorrowerResponse target =
                new BorrowerResponse();

        BeanUtils.copyProperties(
                source,
                target,
                "organization",
                "phoneHash",
                "nationalIdHash",
                "singleCertificateNumber",
                "spouseFullName",
                "spouseNationalId",
                "spousePhone",
                "spouseConsent"
        );

        if (source.getOrganization() != null) {
            target.setOrganizationId(
                    source.getOrganization().getId()
            );
        }

        return target;
    }

    public static Page<BorrowerResponse> borrowers(
            Page<Borrower> page
    ) {

        if (page == null) {
            return Page.empty();
        }

        return page.map(
                ResponseDtoMapper::borrower
        );
    }

    // ============================================================
    // PAYMENT
    // ============================================================

    public static PaymentResponse payment(
            Payment source
    ) {

        if (source == null) {
            return null;
        }

        PaymentResponse target =
                new PaymentResponse();

        BeanUtils.copyProperties(
                source,
                target,
                "loan",
                "organization",
                "recordedBy",
                "gatewayResponse"
        );

        if (source.getLoan() != null) {

            target.setLoanId(
                    source.getLoan().getId()
            );

            target.setLoanReference(
                    source.getLoan().getReferenceNumber()
            );
        }

        if (source.getOrganization() != null) {
            target.setOrganizationId(
                    source.getOrganization().getId()
            );
        }

        if (source.getRecordedBy() != null) {
            target.setRecordedById(
                    source.getRecordedBy().getId()
            );
        }

        return target;
    }

    public static List<PaymentResponse> payments(
            List<Payment> items
    ) {

        if (items == null || items.isEmpty()) {
            return List.of();
        }

        return items.stream()
                .filter(Objects::nonNull)
                .map(ResponseDtoMapper::payment)
                .toList();
    }

    // ============================================================
    // IMPORT BATCH
    // ============================================================

    public static ImportBatchResponse importBatch(
            ImportBatch source
    ) {

        if (source == null) {
            return null;
        }

        Long organizationId = null;

        if (source.getOrganization() != null) {
            organizationId =
                    source.getOrganization().getId();
        }

        Long importedById = null;
        String importedByName = null;

        if (source.getImportedBy() != null) {

            importedById =
                    source.getImportedBy().getId();

            importedByName =
                    source.getImportedBy().getFullName();
        }

        return ImportBatchResponse.builder()
                .id(source.getId())
                .organizationId(organizationId)
                .importedById(importedById)
                .importedByName(importedByName)
                .fileName(source.getFileName())
                .fileSize(source.getFileSize())
                .totalRows(source.getTotalRows())
                .processedRows(source.getProcessedRows())
                .successCount(source.getSuccessCount())
                .failureCount(source.getFailureCount())
                .progressPercent(source.getProgressPercent())
                .status(source.getStatus())
                .errorMessage(source.getErrorMessage())
                .createdAt(source.getCreatedAt())
                .build();
    }

    public static List<ImportBatchResponse> importBatches(
            List<ImportBatch> items
    ) {

        if (items == null || items.isEmpty()) {
            return List.of();
        }

        return items.stream()
                .filter(Objects::nonNull)
                .map(ResponseDtoMapper::importBatch)
                .toList();
    }

    // ============================================================
    // LOAN COMMENTS
    // ============================================================

    public static LoanCommentResponse loanComment(
            LoanComment source
    ) {

        if (source == null) {
            return null;
        }

        LoanCommentResponse target =
                new LoanCommentResponse();

        BeanUtils.copyProperties(
                source,
                target,
                "loan",
                "author"
        );

        if (source.getLoan() != null) {
            target.setLoanId(
                    source.getLoan().getId()
            );
        }

        if (source.getAuthor() != null) {

            target.setAuthorId(
                    source.getAuthor().getId()
            );

            target.setAuthorName(
                    source.getAuthor().getFullName()
            );
        }

        return target;
    }

    public static List<LoanCommentResponse> loanComments(
            List<LoanComment> items
    ) {

        if (items == null || items.isEmpty()) {
            return List.of();
        }

        return items.stream()
                .filter(Objects::nonNull)
                .map(ResponseDtoMapper::loanComment)
                .toList();
    }

    // ============================================================
    // USER
    // ============================================================

    public static UserResponse user(
            User source
    ) {

        if (source == null) {
            return null;
        }

        UserResponse target =
                new UserResponse();

        BeanUtils.copyProperties(
                source,
                target,
                "organization",
                "branch",
                "role",
                "password",
                "twoFactorSecret",
                "failedLoginAttempts",
                "lockedUntil",
                "loginOtpHash",
                "loginOtpExpiresAt",
                "loginOtpAttempts",
                "lastLoginIp",
                "auditLogs"
        );

        if (source.getOrganization() != null) {
            target.setOrganizationId(
                    source.getOrganization().getId()
            );
        }

        if (source.getBranch() != null) {
            target.setBranchId(
                    source.getBranch().getId()
            );
        }

        if (source.getRole() != null) {

            target.setRoleId(
                    source.getRole().getId()
            );

            target.setRoleName(
                    source.getRole().getName()
            );
        }

        return target;
    }

    // ============================================================
    // ORGANIZATION
    // ============================================================

    public static OrganizationResponse organization(
            Organization source
    ) {

        if (source == null) {
            return null;
        }

        OrganizationResponse target =
                new OrganizationResponse();

        BeanUtils.copyProperties(
                source,
                target,
                "users",
                "branches",
                "stripeCustomerId"
        );

        return target;
    }

    // ============================================================
    // JOURNAL ENTRY
    // ============================================================

    public static JournalEntryResponse journalEntry(
            JournalEntry source
    ) {

        if (source == null) {
            return null;
        }

        JournalEntryResponse target =
                new JournalEntryResponse();

        BeanUtils.copyProperties(
                source,
                target,
                "organization",
                "branch",
                "lines"
        );

        if (source.getOrganization() != null) {
            target.setOrganizationId(
                    source.getOrganization().getId()
            );
        }

        if (source.getBranch() != null) {
            target.setBranchId(
                    source.getBranch().getId()
            );
        }

        /*
         * IMPORTANT:
         *
         * Journal lines are potentially lazy.
         *
         * We only traverse them if Hibernate has already
         * initialized the collection.
         */
        if (source.getLines() != null
                && Hibernate.isInitialized(source.getLines())) {

            target.setLines(
                    source.getLines()
                            .stream()
                            .filter(Objects::nonNull)
                            .map(line -> {

                                JournalEntryResponse.JournalLineResponse x =
                                        new JournalEntryResponse.JournalLineResponse();

                                BeanUtils.copyProperties(
                                        line,
                                        x,
                                        "journalEntry",
                                        "account"
                                );

                                if (line.getAccount() != null) {

                                    x.setAccountId(
                                            line.getAccount().getId()
                                    );

                                    if (Hibernate.isInitialized(
                                            line.getAccount()
                                    )) {

                                        x.setAccountCode(
                                                line.getAccount().getCode()
                                        );

                                        x.setAccountName(
                                                line.getAccount().getName()
                                        );
                                    }
                                }

                                return x;
                            })
                            .toList()
            );

        } else {

            /*
             * Do not trigger Hibernate initialization.
             */
            target.setLines(
                    List.of()
            );
        }

        return target;
    }

    public static List<JournalEntryResponse> journalEntries(
            List<JournalEntry> items
    ) {

        if (items == null || items.isEmpty()) {
            return List.of();
        }

        return items.stream()
                .filter(Objects::nonNull)
                .map(ResponseDtoMapper::journalEntry)
                .toList();
    }

    // ============================================================
    // UNIVERSAL SAFE MAPPER
    // ============================================================

    /**
     * Converts supported API values into detached DTO-safe
     * representations.
     *
     * This method is used by ApiResponse.safe(...).
     *
     * The critical rule is:
     *
     * NEVER iterate a Hibernate lazy collection unless it is
     * already initialized.
     */
    public static Object safe(
            Object value
    ) {

        if (value == null) {
            return null;
        }

        // --------------------------------------------------------
        // Explicit mappings
        // --------------------------------------------------------

        if (value instanceof Loan l) {
            return loan(l);
        }

        if (value instanceof Borrower b) {
            return borrower(b);
        }

        if (value instanceof Payment p) {
            return payment(p);
        }

        if (value instanceof JournalEntry j) {
            return journalEntry(j);
        }

        if (value instanceof LoanComment c) {
            return loanComment(c);
        }

        if (value instanceof User u) {
            return user(u);
        }

        if (value instanceof Organization o) {
            return organization(o);
        }

        // --------------------------------------------------------
        // Spring Data Page
        // --------------------------------------------------------

        if (value instanceof Page<?> page) {

            return page.map(
                    ResponseDtoMapper::safe
            );
        }

        // --------------------------------------------------------
        // Collections
        // --------------------------------------------------------

        if (value instanceof Collection<?> collection) {

            /*
             * A top-level Hibernate PersistentCollection can also
             * be lazy.
             *
             * Never iterate it when it has not been initialized.
             */
            if (!Hibernate.isInitialized(collection)) {
                return List.of();
            }

            return collection.stream()
                    .map(ResponseDtoMapper::safe)
                    .toList();
        }

        // --------------------------------------------------------
        // Maps
        // --------------------------------------------------------

        if (value instanceof Map<?, ?> map) {

            Map<String, Object> out =
                    new LinkedHashMap<>();

            map.forEach(
                    (key, nestedValue) ->
                            out.put(
                                    String.valueOf(key),
                                    safe(nestedValue)
                            )
            );

            return out;
        }

        // --------------------------------------------------------
        // Scalars
        // --------------------------------------------------------

        if (isScalar(value)) {
            return value;
        }

        // --------------------------------------------------------
        // Domain entities
        // --------------------------------------------------------

        if (isEntity(value)) {
            return fallback(value);
        }

        // --------------------------------------------------------
        // Everything else
        // --------------------------------------------------------

        return value;
    }

    // ============================================================
    // GENERIC ENTITY FALLBACK
    // ============================================================

    /**
     * Defensive fallback for domain entities that do not yet have
     * a dedicated response DTO mapper.
     *
     * This is intentionally conservative.
     *
     * It exposes scalar values and identifiers but does not force
     * Hibernate to load lazy relationships.
     */
    private static SafeEntityResponse fallback(
            Object entity
    ) {

        Map<String, Object> out =
                new LinkedHashMap<>();

        for (Field field : allFields(entity.getClass())) {

            if (Modifier.isStatic(field.getModifiers())
                    || field.isSynthetic()) {

                continue;
            }

            String name =
                    field.getName();

            /*
             * Never expose secrets or internal payloads.
             */
            if (Set.of(
                    "password",
                    "twoFactorSecret",
                    "loginOtpHash",
                    "responseBody",
                    "rowResults",
                    "data",
                    "signingToken",
                    "otpCodeHash",
                    "secret",
                    "beforeValue",
                    "afterValue"
            ).contains(name)) {

                continue;
            }

            try {

                field.setAccessible(true);

                Object raw =
                        field.get(entity);

                // ------------------------------------------------
                // Null/scalar fields
                // ------------------------------------------------

                if (raw == null
                        || isScalar(raw)) {

                    out.put(
                            name,
                            raw
                    );

                    continue;
                }

                // ------------------------------------------------
                // Hibernate lazy collection
                // ------------------------------------------------

                if (raw instanceof Collection<?> collection) {

                    /*
                     * THIS IS THE IMPORTANT FIX.
                     *
                     * The old code did:
                     *
                     * c.stream()
                     *
                     * regardless of whether the Hibernate
                     * PersistentBag was initialized.
                     *
                     * That caused:
                     *
                     * LazyInitializationException:
                     * failed to lazily initialize a collection
                     * of role:
                     * CollectionCase.actions
                     *
                     * We now explicitly check initialization
                     * before touching the collection.
                     */
                    if (!Hibernate.isInitialized(collection)) {

                        out.put(
                                name,
                                List.of()
                        );

                        continue;
                    }

                    out.put(
                            name,
                            collection.stream()
                                    .map(
                                            ResponseDtoMapper::identifierOnly
                                    )
                                    .toList()
                    );

                    continue;
                }

                // ------------------------------------------------
                // Hibernate lazy map
                // ------------------------------------------------

                if (raw instanceof Map<?, ?> map) {

                    if (!Hibernate.isInitialized(map)) {

                        out.put(
                                name,
                                Map.of()
                        );

                        continue;
                    }

                    Map<String, Object> mapped =
                            new LinkedHashMap<>();

                    map.forEach(
                            (key, nestedValue) ->
                                    mapped.put(
                                            String.valueOf(key),
                                            safe(nestedValue)
                                    )
                    );

                    out.put(
                            name,
                            mapped
                    );

                    continue;
                }

                // ------------------------------------------------
                // Entity relationship
                // ------------------------------------------------

                if (isEntity(raw)) {

                    /*
                     * identifierOnly() itself checks Hibernate
                     * initialization and therefore does not force
                     * a lazy proxy to load.
                     */
                    out.put(
                            name,
                            identifierOnly(raw)
                    );
                }

            } catch (IllegalAccessException
                    | RuntimeException ignored) {

                /*
                 * A response mapper must never cause the entire
                 * endpoint to fail merely because a reflective
                 * field cannot be read.
                 *
                 * The field is simply omitted.
                 */
            }
        }

        return new SafeEntityResponse(
                out
        );
    }

    // ============================================================
    // IDENTIFIER-ONLY ENTITY MAPPING
    // ============================================================

    /**
     * Returns a safe representation of an entity relationship.
     *
     * If the Hibernate proxy is not initialized, only the identifier
     * is read.
     *
     * No lazy loading is triggered.
     */
    private static Map<String, Object> identifierOnly(
            Object entity
    ) {

        Map<String, Object> id =
                new LinkedHashMap<>();

        if (entity == null) {
            return id;
        }

        /*
         * Hibernate proxy/entity initialization check.
         */
        if (!Hibernate.isInitialized(entity)) {

            try {

                Field idField =
                        findField(
                                entity.getClass(),
                                "id"
                        );

                if (idField != null) {

                    idField.setAccessible(true);

                    Object value =
                            idField.get(entity);

                    id.put(
                            "id",
                            value
                    );
                }

            } catch (Exception ignored) {
                /*
                 * Keep identifier map empty if the proxy
                 * implementation does not expose the field.
                 */
            }

            return id;
        }

        /*
         * Already initialized entity.
         *
         * We can safely expose a small identifying projection.
         */
        try {

            Field idField =
                    findField(
                            entity.getClass(),
                            "id"
                    );

            if (idField != null) {

                idField.setAccessible(true);

                id.put(
                        "id",
                        idField.get(entity)
                );
            }

            for (String fieldName :
                    List.of(
                            "name",
                            "fullName",
                            "referenceNumber",
                            "slug",
                            "code",
                            "email"
                    )) {

                Field nestedField =
                        findField(
                                entity.getClass(),
                                fieldName
                        );

                if (nestedField == null) {
                    continue;
                }

                nestedField.setAccessible(true);

                Object nestedValue =
                        nestedField.get(entity);

                if (nestedValue != null
                        && isScalar(nestedValue)) {

                    id.put(
                            fieldName,
                            nestedValue
                    );
                }
            }

        } catch (Exception ignored) {
            /*
             * Defensive response handling.
             */
        }

        return id;
    }

    // ============================================================
    // TYPE HELPERS
    // ============================================================

    private static boolean isEntity(
            Object value
    ) {

        if (value == null) {
            return false;
        }

        return value.getClass()
                .getName()
                .startsWith(
                        "com.patrick.fintech.loan_backend.model."
                );
    }

    private static boolean isScalar(
            Object value
    ) {

        if (value == null) {
            return true;
        }

        return value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Enum<?>
                || value instanceof java.util.Date
                || value instanceof Temporal
                || value.getClass().isPrimitive()
                || value instanceof UUID;
    }

    private static Field findField(
            Class<?> type,
            String name
    ) {

        for (
                Class<?> current = type;
                current != null
                        && current != Object.class;
                current = current.getSuperclass()
        ) {

            try {

                return current.getDeclaredField(
                        name
                );

            } catch (NoSuchFieldException ignored) {
                // Continue through inheritance hierarchy.
            }
        }

        return null;
    }

    private static List<Field> allFields(
            Class<?> type
    ) {

        List<Field> fields =
                new ArrayList<>();

        for (
                Class<?> current = type;
                current != null
                        && current != Object.class;
                current = current.getSuperclass()
        ) {

            fields.addAll(
                    Arrays.asList(
                            current.getDeclaredFields()
                    )
            );
        }

        return fields;
    }
}