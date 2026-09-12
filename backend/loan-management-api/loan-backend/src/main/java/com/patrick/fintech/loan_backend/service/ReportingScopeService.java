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

    public static Scope currentScope() {
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
