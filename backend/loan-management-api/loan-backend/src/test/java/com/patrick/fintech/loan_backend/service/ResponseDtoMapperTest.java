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
