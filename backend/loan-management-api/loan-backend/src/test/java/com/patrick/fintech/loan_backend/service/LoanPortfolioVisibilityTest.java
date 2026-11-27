package com.patrick.fintech.loan_backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.*;

class LoanPortfolioVisibilityTest {
    @Test
    void defaultsToNormalEvenForBusinessOwner() {
        assertEquals(LoanPortfolioVisibility.NORMAL, LoanPortfolioVisibility.resolve(null, true));
        assertEquals(LoanPortfolioVisibility.NORMAL, LoanPortfolioVisibility.resolve("", false));
    }

    @Test
    void businessOwnerCanSelectConfidentialAndCombinedPortfolios() {
        assertEquals(LoanPortfolioVisibility.BUSINESS_OWNER_ONLY,
                LoanPortfolioVisibility.resolve("business_owner_only", true));
        assertEquals(LoanPortfolioVisibility.ALL,
                LoanPortfolioVisibility.resolve("ALL", true));
    }

    @Test
    void ordinaryRolesCannotSelectConfidentialOrCombinedPortfolios() {
        assertThrows(AccessDeniedException.class,
                () -> LoanPortfolioVisibility.resolve("BUSINESS_OWNER_ONLY", false));
        assertThrows(AccessDeniedException.class,
                () -> LoanPortfolioVisibility.resolve("ALL", false));
    }

    @Test
    void invalidVisibilityIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> LoanPortfolioVisibility.resolve("UNKNOWN", true));
    }
}
