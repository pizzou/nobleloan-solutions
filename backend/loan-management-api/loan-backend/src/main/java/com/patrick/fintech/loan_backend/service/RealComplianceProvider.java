package com.patrick.fintech.loan_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patrick.fintech.loan_backend.model.Borrower;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Production composite compliance decision:
 *
 * 1. Rwanda identity verification through the configured KYC provider.
 * 2. Sanctions/PEP/RCA/adverse-media screening through the configured AML provider.
 *
 * Both must succeed. A provider outage is fail-closed.
 */
@Service
@Primary
public class RealComplianceProvider implements ExternalComplianceProvider {

    private final LtgsKycProvider kycProvider;
    private final PanAfricaScreenComplianceProvider amlProvider;
    private final ObjectMapper objectMapper;

    public RealComplianceProvider(
            LtgsKycProvider kycProvider,
            PanAfricaScreenComplianceProvider amlProvider,
            ObjectMapper objectMapper) {
        this.kycProvider = kycProvider;
        this.amlProvider = amlProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    public ScreeningResult screen(Borrower borrower) {
        LtgsKycProvider.Result kyc = kycProvider.verify(borrower);
        ScreeningResult aml = amlProvider.screen(borrower);

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("kyc", parseJsonOrString(kyc.rawResponse()));
        raw.put("aml", parseJsonOrString(aml.rawResponse()));

        boolean clear = kyc.verified()
                && aml.sanctionsClear()
                && aml.pepClear()
                && aml.adverseMediaClear();

        double matchScore = Math.max(
                aml.matchScore(),
                kyc.verified() ? 0.0d : 100.0d);

        String reason = clear
                ? "Real Rwanda KYC verification and external AML screening both passed."
                : "Real KYC and/or AML screening did not pass. Manual review is required.";

        try {
            return new ScreeningResult(
                    "LTGS+PANAFRICASCREEN",
                    kyc.verified(),
                    aml.sanctionsClear(),
                    aml.pepClear(),
                    aml.adverseMediaClear(),
                    matchScore,
                    objectMapper.writeValueAsString(raw),
                    reason);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to serialize external compliance result.", ex);
        }
    }

    private Object parseJsonOrString(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            return objectMapper.readValue(value, Object.class);
        } catch (Exception ignored) {
            return value;
        }
    }
}
