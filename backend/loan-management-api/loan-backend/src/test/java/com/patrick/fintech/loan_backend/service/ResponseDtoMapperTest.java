package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.LoanResponse;
import com.patrick.fintech.loan_backend.dto.PaymentResponse;
import com.patrick.fintech.loan_backend.dto.UserResponse;
import com.patrick.fintech.loan_backend.mapper.ResponseDtoMapper;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.model.Role;
import com.patrick.fintech.loan_backend.model.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ResponseDtoMapperTest {

    @Test
    void loanMapperDoesNotExposeEntityRelationships() {
        Loan loan = Loan.builder()
                .id(7L)
                .referenceNumber("LN-0007")
                .amount(new BigDecimal("500000.00"))
                .build();

        LoanResponse response = ResponseDtoMapper.loan(loan);

        assertEquals(7L, response.getId());
        assertEquals("LN-0007", response.getReferenceNumber());
    }

    @Test
    void contractDaysAreIndependentOfMonthlyInstallmentArrears() {
        java.time.LocalDate maturity = java.time.LocalDate.of(2026, 10, 19);
        Loan loan = Loan.builder()
                .id(110L)
                .referenceNumber("NLS-000110")
                .status(com.patrick.fintech.loan_backend.model.LoanStatus.OVERDUE)
                .imported(false)
                .disbursedAt(java.time.LocalDateTime.of(2026, 8, 19, 10, 0))
                .durationMonths(2)
                .maturityDate(maturity)
                .nextDueDate(java.time.LocalDate.of(2026, 9, 19))
                .daysOverdue(66)
                .build();

        LoanResponse response = ResponseDtoMapper.loan(loan);
        assertEquals(66, response.getDaysOverdue());
        assertTrue(response.getContractMaturityVerified());
        long expectedContractDays = Math.max(0L, java.time.temporal.ChronoUnit.DAYS.between(
                maturity, java.time.LocalDate.now(java.time.ZoneId.of("Africa/Kigali"))));
        assertEquals((int) expectedContractDays, response.getContractDaysOverdue());
    }

    @Test
    void paymentMapperProducesRelationshipIdsOnly() {
        Payment payment = Payment.builder()
                .id(9L)
                .paymentReference("PAY-0009")
                .amount(new BigDecimal("10000.00"))
                .build();

        PaymentResponse response = ResponseDtoMapper.payment(payment);

        assertEquals(9L, response.getId());
        assertEquals("PAY-0009", response.getPaymentReference());
        assertNull(response.getLoanId());
    }

    @Test
    void userMapperNeverExposesCredentialsOrMfaSecrets() {
        User user = User.builder()
                .id(11L)
                .name("Production User")
                .email("user@example.test")
                .password("hashed-password")
                .twoFactorSecret("super-secret")
                .build();

        UserResponse response = ResponseDtoMapper.user(user);

        assertEquals(11L, response.getId());
        assertEquals("user@example.test", response.getEmail());
        assertNull(response.getRoleId());
        assertThrows(NoSuchFieldException.class, () -> UserResponse.class.getDeclaredField("password"));
        assertThrows(NoSuchFieldException.class, () -> UserResponse.class.getDeclaredField("twoFactorSecret"));
    }
}
