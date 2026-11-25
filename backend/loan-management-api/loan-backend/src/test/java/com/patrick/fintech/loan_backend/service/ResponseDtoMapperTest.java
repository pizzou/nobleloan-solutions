package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.LoanResponse;
import com.patrick.fintech.loan_backend.dto.PaymentResponse;
import com.patrick.fintech.loan_backend.dto.UserResponse;
import com.patrick.fintech.loan_backend.mapper.ResponseDtoMapper;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.model.Role;
import com.patrick.fintech.loan_backend.model.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

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
    void loanApiCorrectsStaleOverdueStateDuringThreeGraceDays() {
        LocalDate disbursement = LocalDate.now().minusMonths(6);
        Loan loan = Loan.builder()
                .id(54L)
                .referenceNumber("NLS-000054")
                .status(LoanStatus.OVERDUE)
                .daysOverdue(66)
                .disbursedAt(disbursement.atStartOfDay())
                .durationMonths(6)
                .build();

        LoanResponse response = ResponseDtoMapper.loan(loan);
        assertEquals(LoanStatus.ACTIVE, response.getStatus());
        assertEquals(0, response.getDaysOverdue());
        // API projection cannot mutate historical loan data on read.
        assertEquals(LoanStatus.OVERDUE, loan.getStatus());
        assertEquals(66, loan.getDaysOverdue());
    }

    @Test
    void loanApiReportsChargeableOverdueDaysNotDaysSinceMaturity() {
        LocalDate disbursement = LocalDate.now().minusMonths(6).minusDays(10);
        Loan loan = Loan.builder()
                .id(54L)
                .referenceNumber("NLS-000054")
                .status(LoanStatus.ACTIVE)
                .daysOverdue(0)
                .disbursedAt(disbursement.atStartOfDay())
                .durationMonths(6)
                .build();

        LoanResponse response = ResponseDtoMapper.loan(loan);
        assertEquals(LoanStatus.OVERDUE, response.getStatus());
        assertEquals(7, response.getDaysOverdue());
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
