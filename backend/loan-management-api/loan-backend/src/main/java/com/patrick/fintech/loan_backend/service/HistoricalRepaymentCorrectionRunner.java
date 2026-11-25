package com.patrick.fintech.loan_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/** Applies one reviewed correction only when explicitly enabled by an operator. */
@Component
@ConditionalOnProperty(name = "app.historical-repayment.enabled", havingValue = "true")
public class HistoricalRepaymentCorrectionRunner implements ApplicationRunner {

    private final HistoricalRepaymentCorrectionService corrections;
    private final ObjectMapper mapper;
    private final Path input;

    public HistoricalRepaymentCorrectionRunner(HistoricalRepaymentCorrectionService corrections,
            ObjectMapper mapper, @Value("${app.historical-repayment.input}") String input) {
        this.corrections = corrections;
        this.mapper = mapper;
        this.input = Path.of(input);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        VerifiedCorrection reviewed;
        try (var stream = Files.newInputStream(input)) {
            reviewed = mapper.readValue(stream, VerifiedCorrection.class);
        }
        corrections.applyVerifiedSingleReceipt(reviewed.loanId(), reviewed.receiptId(), reviewed.opening(),
                reviewed.charges(), reviewed.cutoff(), reviewed.historicalOpeningCutoff(),
                reviewed.expectedPostedAssessment());
    }

    record VerifiedCorrection(long loanId, long receiptId, HistoricalRepaymentCalculation.Opening opening,
            List<HistoricalRepaymentCalculation.Charge> charges, LocalDate cutoff,
            LocalDate historicalOpeningCutoff, BigDecimal expectedPostedAssessment) {}
}
