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

    public static LoanResponse loan(Loan source) {
        if (source == null)
            return null;
        LoanResponse target = new LoanResponse();
        BeanUtils.copyProperties(source, target,
                "organization", "branch", "borrower", "createdBy", "approvedBy", "loanOfficer", "legacyAmountDouble",
                "internalNotes");
        if (source.getOrganization() != null)
            target.setOrganizationId(source.getOrganization().getId());
        if (source.getBranch() != null)
            target.setBranchId(source.getBranch().getId());
        if (source.getBorrower() != null) {
            target.setBorrowerId(source.getBorrower().getId());
            target.setBorrowerName(source.getBorrower().getFullName());
        }
        if (source.getCreatedBy() != null)
            target.setCreatedById(source.getCreatedBy().getId());
        if (source.getApprovedBy() != null)
            target.setApprovedById(source.getApprovedBy().getId());
        if (source.getLoanOfficer() != null)
            target.setLoanOfficerId(source.getLoanOfficer().getId());
        return target;
    }

    public static Page<LoanResponse> loans(Page<Loan> page) {
        return page.map(ResponseDtoMapper::loan);
    }

    public static List<LoanResponse> loans(List<Loan> items) {
        return items.stream().map(ResponseDtoMapper::loan).toList();
    }

    public static BorrowerResponse borrower(Borrower source) {
        if (source == null)
            return null;
        BorrowerResponse target = new BorrowerResponse();
        BeanUtils.copyProperties(source, target, "organization", "phoneHash", "nationalIdHash",
                "singleCertificateNumber", "spouseFullName", "spouseNationalId", "spousePhone", "spouseConsent");
        if (source.getOrganization() != null)
            target.setOrganizationId(source.getOrganization().getId());
        return target;
    }

    public static Page<BorrowerResponse> borrowers(Page<Borrower> page) {
        return page.map(ResponseDtoMapper::borrower);
    }

    public static PaymentResponse payment(Payment source) {
        if (source == null)
            return null;
        PaymentResponse target = new PaymentResponse();
        BeanUtils.copyProperties(source, target, "loan", "organization", "recordedBy", "gatewayResponse");
        if (source.getLoan() != null) {
            target.setLoanId(source.getLoan().getId());
            target.setLoanReference(source.getLoan().getReferenceNumber());
        }
        if (source.getOrganization() != null)
            target.setOrganizationId(source.getOrganization().getId());
        if (source.getRecordedBy() != null)
            target.setRecordedById(source.getRecordedBy().getId());
        return target;
    }

    public static List<PaymentResponse> payments(List<Payment> items) {
        return items.stream().map(ResponseDtoMapper::payment).toList();
    }

    public static LoanCommentResponse loanComment(LoanComment source) {
        if (source == null)
            return null;
        LoanCommentResponse target = new LoanCommentResponse();
        BeanUtils.copyProperties(source, target, "loan", "author");
        if (source.getLoan() != null)
            target.setLoanId(source.getLoan().getId());
        if (source.getAuthor() != null) {
            target.setAuthorId(source.getAuthor().getId());
            target.setAuthorName(source.getAuthor().getFullName());
        }
        return target;
    }

    public static List<LoanCommentResponse> loanComments(List<LoanComment> items) {
        return items.stream().map(ResponseDtoMapper::loanComment).toList();
    }

    public static UserResponse user(User source) {
        if (source == null)
            return null;
        UserResponse target = new UserResponse();
        BeanUtils.copyProperties(source, target, "organization", "branch", "role", "password", "twoFactorSecret",
                "failedLoginAttempts", "lockedUntil", "loginOtpHash", "loginOtpExpiresAt", "loginOtpAttempts",
                "lastLoginIp", "auditLogs");
        if (source.getOrganization() != null)
            target.setOrganizationId(source.getOrganization().getId());
        if (source.getBranch() != null)
            target.setBranchId(source.getBranch().getId());
        if (source.getRole() != null) {
            target.setRoleId(source.getRole().getId());
            target.setRoleName(source.getRole().getName());
        }
        return target;
    }

    public static OrganizationResponse organization(Organization source) {
        if (source == null)
            return null;
        OrganizationResponse target = new OrganizationResponse();
        BeanUtils.copyProperties(source, target, "users", "branches", "stripeCustomerId");
        return target;
    }

    public static JournalEntryResponse journalEntry(JournalEntry source) {
        if (source == null)
            return null;
        JournalEntryResponse t = new JournalEntryResponse();
        BeanUtils.copyProperties(source, t, "organization", "branch", "lines");
        if (source.getOrganization() != null)
            t.setOrganizationId(source.getOrganization().getId());
        if (source.getBranch() != null)
            t.setBranchId(source.getBranch().getId());
        if (source.getLines() != null)
            t.setLines(source.getLines().stream().map(line -> {
                JournalEntryResponse.JournalLineResponse x = new JournalEntryResponse.JournalLineResponse();
                BeanUtils.copyProperties(line, x, "journalEntry", "account");
                if (line.getAccount() != null) {
                    x.setAccountId(line.getAccount().getId());
                    if (Hibernate.isInitialized(line.getAccount())) {
                        x.setAccountCode(line.getAccount().getCode());
                        x.setAccountName(line.getAccount().getName());
                    }
                }
                return x;
            }).toList());
        return t;
    }

    public static List<JournalEntryResponse> journalEntries(List<JournalEntry> items) {
        return items.stream().map(ResponseDtoMapper::journalEntry).toList();
    }

    /** Convert any supported API value to a detached DTO-safe representation. */
    public static Object safe(Object value) {
        if (value == null)
            return null;
        if (value instanceof Loan l)
            return loan(l);
        if (value instanceof Borrower b)
            return borrower(b);
        if (value instanceof Payment p)
            return payment(p);
        if (value instanceof JournalEntry j)
            return journalEntry(j);
        if (value instanceof LoanComment c)
            return loanComment(c);
        if (value instanceof User u)
            return user(u);
        if (value instanceof Organization o)
            return organization(o);
        if (value instanceof Page<?> page)
            return page.map(ResponseDtoMapper::safe);
        if (value instanceof Collection<?> collection)
            return collection.stream().map(ResponseDtoMapper::safe).toList();
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> out.put(String.valueOf(k), safe(v)));
            return out;
        }
        if (isScalar(value))
            return value;
        if (value.getClass().getName().startsWith("com.patrick.fintech.loan_backend.model."))
            return fallback(value);
        return value;
    }

    private static SafeEntityResponse fallback(Object entity) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Field field : allFields(entity.getClass())) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic())
                continue;
            String name = field.getName();
            if (Set.of("password", "twoFactorSecret", "loginOtpHash", "responseBody", "rowResults", "data",
                    "signingToken", "otpCodeHash", "secret", "beforeValue", "afterValue").contains(name))
                continue;
            try {
                field.setAccessible(true);
                Object raw = field.get(entity);
                if (raw == null || isScalar(raw)) {
                    out.put(name, raw);
                    continue;
                }
                if (raw instanceof Collection<?> c) {
                    out.put(name, c.stream().map(ResponseDtoMapper::identifierOnly).toList());
                    continue;
                }
                if (isEntity(raw))
                    out.put(name, identifierOnly(raw));
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return new SafeEntityResponse(out);
    }

    private static Map<String, Object> identifierOnly(Object entity) {
        Map<String, Object> id = new LinkedHashMap<>();
        if (!Hibernate.isInitialized(entity)) {
            try {
                var f = findField(entity.getClass(), "id");
                if (f != null) {
                    f.setAccessible(true);
                    id.put("id", f.get(entity));
                }
            } catch (Exception ignored) {
            }
            return id;
        }
        try {
            var f = findField(entity.getClass(), "id");
            if (f != null) {
                f.setAccessible(true);
                id.put("id", f.get(entity));
            }
            for (String n : List.of("name", "referenceNumber", "slug", "code", "email")) {
                var nf = findField(entity.getClass(), n);
                if (nf != null) {
                    nf.setAccessible(true);
                    Object v = nf.get(entity);
                    if (v != null)
                        id.put(n, v);
                }
            }
        } catch (Exception ignored) {
        }
        return id;
    }

    private static boolean isEntity(Object value) {
        return value.getClass().getName().startsWith("com.patrick.fintech.loan_backend.model.");
    }

    private static boolean isScalar(Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean
                || value instanceof Character || value instanceof Enum<?> || value instanceof java.util.Date
                || value instanceof Temporal || value.getClass().isPrimitive();
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass())
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
        return null;
    }

    private static List<Field> allFields(Class<?> type) {
        List<Field> r = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass())
            r.addAll(Arrays.asList(c.getDeclaredFields()));
        return r;
    }
}
