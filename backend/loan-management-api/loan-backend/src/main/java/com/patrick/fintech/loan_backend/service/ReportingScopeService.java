package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanStatus;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Central reporting/visibility boundary.
 *
 * NORMAL_SCOPE excludes loans explicitly classified BUSINESS_OWNER_ONLY.
 * BUSINESS_OWNER_SCOPE includes both ordinary and BUSINESS_OWNER_ONLY loans.
 *
 * The resolver is static intentionally: reporting services can determine the
 * interactive scope without adding a constructor dependency that would break
 * existing service tests/constructors. Background/system execution has no
 * authenticated principal and therefore uses the full internal scope so
 * scheduled accounting/repayment jobs continue to process every loan.
 */
@Service
public class ReportingScopeService {

    public enum Scope {
        NORMAL,
        BUSINESS_OWNER
    }

    public ReportingScopeService() {
        // Utility-style Spring bean; scope resolution is request-context based.
    }

    private static final ThreadLocal<Scope> EXPLICIT_SCOPE = new ThreadLocal<>();

    /**
     * Resolve the reporting scope for the current thread. Explicit scope takes
     * precedence over SecurityContext so user-triggered async jobs cannot fall
     * back to the background/system BUSINESS_OWNER scope when the request
     * authentication is no longer present on the worker thread.
     */
    public static Scope currentScope() {
        Scope explicitScope = EXPLICIT_SCOPE.get();
        if (explicitScope != null) {
            return explicitScope;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            // Background jobs and internal system transactions must see the
            // complete ledger; they are not user-facing reporting requests.
            return Scope.BUSINESS_OWNER;
        }

        boolean businessOwner = authentication.getAuthorities() != null
                && authentication.getAuthorities().stream()
                        .anyMatch(a -> "ROLE_BUSINESS_OWNER".equalsIgnoreCase(a.getAuthority()));

        return businessOwner ? Scope.BUSINESS_OWNER : Scope.NORMAL;
    }

    public static boolean includeBusinessOwnerOnly() {
        return currentScope() == Scope.BUSINESS_OWNER;
    }

    /**
     * Temporarily force a reporting scope for the current thread. The previous
     * scope is restored when the returned context is closed. This is intended
     * for async/background execution that still belongs to a specific user
     * request.
     */
    public static ScopeContext useScope(Scope scope) {
        if (scope == null) {
            throw new IllegalArgumentException("Reporting scope is required.");
        }

        Scope previous = EXPLICIT_SCOPE.get();
        EXPLICIT_SCOPE.set(scope);
        return new ScopeContext(previous);
    }

    public static final class ScopeContext implements AutoCloseable {
        private final Scope previous;
        private boolean closed;

        private ScopeContext(Scope previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }

            closed = true;
            if (previous == null) {
                EXPLICIT_SCOPE.remove();
            } else {
                EXPLICIT_SCOPE.set(previous);
            }
        }
    }

    /**
     * Pending/under-review loans remain visible to an authorized approval
     * workflow. Once finally approved, a BUSINESS_OWNER_ONLY loan is hidden
     * from NORMAL_SCOPE.
     */
    public static boolean isVisibleToCurrentUser(Loan loan) {
        if (loan == null) {
            return false;
        }
        if (!Boolean.TRUE.equals(loan.getBusinessOwnerOnly())) {
            return true;
        }
        if (includeBusinessOwnerOnly()) {
            return true;
        }
        return loan.getStatus() == LoanStatus.PENDING
                || loan.getStatus() == LoanStatus.UNDER_REVIEW;
    }
}
