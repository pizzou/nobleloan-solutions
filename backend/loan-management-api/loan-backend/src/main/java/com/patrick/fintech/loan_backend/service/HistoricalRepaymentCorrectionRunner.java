package com.patrick.fintech.loan_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/** Applies reviewed receipt corrections only when explicitly enabled by an operator. */
@Component
@ConditionalOnProperty(name = "app.historical-repayment.enabled", havingValue = "true")
public class HistoricalRepaymentCorrectionRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(HistoricalRepaymentCorrectionRunner.class);

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
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        List<VerifiedCorrection> reviewed;
        try (var stream = Files.newInputStream(input)) {
            var payload = mapper.readTree(stream);
            if (payload == null || (!payload.isObject() && !payload.isArray())) {
                throw new IllegalArgumentException("Expected a reviewed correction or an array of corrections");
            }
            reviewed = payload.isArray()
                    ? Arrays.asList(mapper.treeToValue(payload, VerifiedCorrection[].class))
                    : List.of(mapper.treeToValue(payload, VerifiedCorrection.class));
        }

        if (reviewed.isEmpty()) {
            throw new IllegalArgumentException("Provide at least one verified correction");
        }
        var loanIds = new HashSet<Long>();
        var receiptIds = new HashSet<Long>();
        for (var correction : reviewed) {
            if (correction == null || correction.loanId() <= 0 || correction.receiptId() <= 0
                    || !loanIds.add(correction.loanId()) || !receiptIds.add(correction.receiptId())) {
                throw new IllegalArgumentException("Each verified correction must identify a distinct loan and receipt");
            }
        }
        for (var correction : reviewed.stream().sorted(Comparator.comparingLong(VerifiedCorrection::loanId)).toList()) {
            corrections.applyVerifiedSingleReceipt(correction.loanId(), correction.receiptId(), correction.opening(),
                    correction.charges(), correction.cutoff(), correction.historicalOpeningCutoff(),
                    correction.expectedPostedAssessment());
        }
        log.info("Verified historical receipt correction batch processed {} receipts", reviewed.size());
    }

    record VerifiedCorrection(long loanId, long receiptId, HistoricalRepaymentCalculation.Opening opening,
            List<HistoricalRepaymentCalculation.Charge> charges, LocalDate cutoff,
            LocalDate historicalOpeningCutoff, BigDecimal expectedPostedAssessment) {}
}
