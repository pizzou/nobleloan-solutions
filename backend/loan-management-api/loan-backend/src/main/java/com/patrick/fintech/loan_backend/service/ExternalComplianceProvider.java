package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Borrower;

/**
 * Contract for a real, licensed KYC/AML provider.
 *
 * The application must receive a provider-backed decision before a borrower
 * can be marked KYC verified in production. Implementations must not return
 * CLEAR when the provider is unavailable.
 */
public interface ExternalComplianceProvider {

    ScreeningResult screen(Borrower borrower);

    record ScreeningResult(
            String provider,
            boolean identityVerified,
            boolean sanctionsClear,
            boolean pepClear,
            boolean adverseMediaClear,
            double matchScore,
            String rawResponse,
            String decisionReason) {
    }
}
