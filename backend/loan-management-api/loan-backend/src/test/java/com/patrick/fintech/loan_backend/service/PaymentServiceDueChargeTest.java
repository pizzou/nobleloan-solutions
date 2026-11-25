package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Payment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Regression coverage for cross-installment due-charge aggregation.
 * Scheduled but not-yet-due charges must not be accelerated.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceDueChargeTest {

    @InjectMocks
    private PaymentService service;

    @Test
    void sumsAllOverdueInterestAndManagementWithoutFutureCharges() {
        LocalDate asOf = LocalDate.of(2026, 11, 25);
        Payment october = Payment.builder()
                .id(11L)
                .dueDate(LocalDate.of(2026, 10, 19))
                .paid(false)
                .cycleInterestRemaining(new BigDecimal("1675000.00"))
                .cycleManagementFeeRemaining(new BigDecimal("1675000.00"))
                .build();
        Payment november = Payment.builder()
                .id(12L)
                .dueDate(LocalDate.of(2026, 11, 19))
                .paid(false)
                .cycleInterestRemaining(new BigDecimal("1675000.00"))
                .cycleManagementFeeRemaining(new BigDecimal("1675000.00"))
                .build();
        Payment december = Payment.builder()
                .id(13L)
                .dueDate(LocalDate.of(2026, 12, 19))
                .paid(false)
                .cycleInterestRemaining(new BigDecimal("1675000.00"))
                .cycleManagementFeeRemaining(new BigDecimal("1675000.00"))
                .build();

        List<Payment> schedule = List.of(october, november, december);
        BigDecimal interest = ReflectionTestUtils.invokeMethod(service,
                "totalDueCharge", schedule, october, asOf, true,
                new BigDecimal("1675000.00"));
        BigDecimal management = ReflectionTestUtils.invokeMethod(service,
                "totalDueCharge", schedule, october, asOf, false,
                new BigDecimal("1675000.00"));

        assertEquals(new BigDecimal("3350000.00"), interest);
        assertEquals(new BigDecimal("3350000.00"), management);
    }

    @Test
    void contractOverdueRecoversAllMissingMonthlyFeesForNativeAndImportedLoans() {
        LocalDate asOf = LocalDate.of(2026, 11, 25);
        // Only a single month is present in the schedule, but a matured
        // six-month contract has RWF 6.3m of remaining interest and fees each.
        Object gap = ReflectionTestUtils.invokeMethod(service,
                "overdueFeeGap", true,
                new BigDecimal("1675000.00"),
                new BigDecimal("1675000.00"),
                new BigDecimal("6300000.00"),
                new BigDecimal("6300000.00"),
                LocalDate.of(2026, 9, 26), asOf);
        BigDecimal extraInterest = ReflectionTestUtils.invokeMethod(gap, "interest");
        BigDecimal extraManagement = ReflectionTestUtils.invokeMethod(gap, "management");
        assertEquals(new BigDecimal("4625000.00"), extraInterest);
        assertEquals(new BigDecimal("4625000.00"), extraManagement);
    }

    @Test
    void nonOverdueLoanDoesNotAccelerateFutureFeeBalances() {
        Object gap = ReflectionTestUtils.invokeMethod(service,
                "overdueFeeGap", false,
                new BigDecimal("100000.00"),
                new BigDecimal("100000.00"),
                new BigDecimal("600000.00"),
                new BigDecimal("600000.00"),
                LocalDate.of(2026, 12, 26), LocalDate.of(2026, 11, 25));
        BigDecimal interest = ReflectionTestUtils.invokeMethod(gap, "interest");
        BigDecimal management = ReflectionTestUtils.invokeMethod(gap, "management");
        assertEquals(new BigDecimal("0.00"), interest);
        assertEquals(new BigDecimal("0.00"), management);
    }

    @Test
    void mismatchedOverdueFeeEvidenceFailsInsteadOfTakingPrincipal() {
        assertThrows(IllegalStateException.class, () ->
                ReflectionTestUtils.invokeMethod(service,
                        "overdueFeeGap", true,
                        new BigDecimal("800000.00"),
                        new BigDecimal("200000.00"),
                        new BigDecimal("700000.00"),
                        new BigDecimal("200000.00"),
                        LocalDate.of(2026, 9, 26), LocalDate.of(2026, 11, 25)));
    }

    @Test
    void cannotCollectPenaltyPreviouslyOverstatedAgainstDailyPrincipalRule() {
        assertThrows(IllegalStateException.class, () ->
                ReflectionTestUtils.invokeMethod(service,
                        "validatePenaltyAssessment", "NLS-000054",
                        new BigDecimal("8870000.00"),
                        new BigDecimal("6141666.67")));
        // No adjustment is necessary when the recorded penalty equals or
        // is below the correctly calculated chargeable daily penalty.
        ReflectionTestUtils.invokeMethod(service,
                "validatePenaltyAssessment", "NLS-000054",
                new BigDecimal("6141666.67"),
                new BigDecimal("6141666.67"));
    }

    @Test
    void includesNewUnsavedLegacyCycleWithoutDoubleCounting() {
        LocalDate asOf = LocalDate.of(2026, 11, 25);
        Payment unsaved = Payment.builder()
                .dueDate(LocalDate.of(2026, 11, 19))
                .paid(false)
                .build();

        BigDecimal interest = ReflectionTestUtils.invokeMethod(service,
                "totalDueCharge", List.of(), unsaved, asOf, true,
                new BigDecimal("100000.00"));

        assertEquals(new BigDecimal("100000.00"), interest);
    }
}
