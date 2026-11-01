package com.patrick.fintech.loan_backend.service;

import org.springframework.security.access.AccessDeniedException;

import java.util.Locale;

/**
 * Portfolio listing is separate from reporting scope. The Business Owner can
 * choose NORMAL, BUSINESS_OWNER_ONLY or ALL; other roles may only use NORMAL.
 * A missing filter deliberately means NORMAL (never implicit ALL).
 */
public enum LoanPortfolioVisibility {
    NORMAL, BUSINESS_OWNER_ONLY, ALL;

    public static LoanPortfolioVisibility resolve(String raw, boolean isBusinessOwner) {
        LoanPortfolioVisibility selected;
        if (raw == null || raw.isBlank()) {
            selected = NORMAL;
        } else {
            try {
                selected = valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "visibility must be NORMAL, BUSINESS_OWNER_ONLY or ALL");
            }
        }
        if (selected != NORMAL && !isBusinessOwner) {
            throw new AccessDeniedException("Only the Business Owner may view confidential loan portfolios");
        }
        return selected;
    }
}
