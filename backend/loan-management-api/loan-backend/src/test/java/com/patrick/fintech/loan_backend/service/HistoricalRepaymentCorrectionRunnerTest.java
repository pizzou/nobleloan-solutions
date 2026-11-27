package com.patrick.fintech.loan_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class HistoricalRepaymentCorrectionRunnerTest {
    @TempDir
    Path directory;

    @Test
    void processesReviewedLoansInLockOrder() throws Exception {
        var corrections = mock(HistoricalRepaymentCorrectionService.class);
        var input = Files.writeString(directory.resolve("review.json"), """
                [{"loanId": 20, "receiptId": 75}, {"loanId": 10, "receiptId": 80}]
                """);
        var runner = new HistoricalRepaymentCorrectionRunner(corrections, new ObjectMapper(), input.toString());

        runner.run(new DefaultApplicationArguments());

        var ordered = inOrder(corrections);
        ordered.verify(corrections).applyVerifiedSingleReceipt(10, 80, null, null, null, null, null);
        ordered.verify(corrections).applyVerifiedSingleReceipt(20, 75, null, null, null, null, null);
    }

    @Test
    void validatesTheEntireBatchBeforeApplyingAnyCorrection() throws Exception {
        var corrections = mock(HistoricalRepaymentCorrectionService.class);
        var input = Files.writeString(directory.resolve("review.json"), """
                [{"loanId": 10, "receiptId": 80}, {"loanId": 10, "receiptId": 81}]
                """);
        var runner = new HistoricalRepaymentCorrectionRunner(corrections, new ObjectMapper(), input.toString());

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("distinct loan and receipt");
        verifyNoInteractions(corrections);
    }
}
