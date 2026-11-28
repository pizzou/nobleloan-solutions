package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.PaymentReview;
import com.patrick.fintech.loan_backend.model.Role;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentReviewServiceTest {
    private final PaymentReviewRepository reviews = mock(PaymentReviewRepository.class);
    private final LoanRepository loans = mock(LoanRepository.class);
    private final PaymentTransactionRepository receipts = mock(PaymentTransactionRepository.class);
    private final PaymentRepository installments = mock(PaymentRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final PaymentService payments = mock(PaymentService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final MailService mail = mock(MailService.class);
    private final PaymentReviewService service = new PaymentReviewService(
            reviews, loans, receipts, installments, users, payments, events, mail);
    private final Organization org = Organization.builder().id(1L).build();
    private final User staff = user(10L, "ACCOUNTANT");
    private final User owner = user(11L, "BUSINESS_OWNER");
    private final Loan loan = Loan.builder().id(20L).organization(org).referenceNumber("NLS-20")
            .status(LoanStatus.ACTIVE).currency("RWF").build();
    private final PaymentReviewService.Request request =
            new PaymentReviewService.Request(new BigDecimal("250.00"), "BANK_TRANSFER", "BANK-20", null, null);

    @BeforeEach
    void setUp() {
        when(loans.findVisibleByIdForUpdate(20L, 1L, false)).thenReturn(Optional.of(loan));
        when(loans.findVisibleByIdForUpdate(20L, 1L, true)).thenReturn(Optional.of(loan));
        when(users.findActiveUsersByOrganizationAndRole(1L, "BUSINESS_OWNER", User.UserStatus.ACTIVE,
                Pageable.unpaged())).thenReturn(List.of(owner));
        when(reviews.saveAndFlush(any(PaymentReview.class))).thenAnswer(call -> {
            PaymentReview row = call.getArgument(0);
            row.setId(30L);
            when(reviews.findByOrganizationIdAndRequestKey(1L, "request")).thenReturn(Optional.of(row));
            when(reviews.findForDecision(1L, 30L)).thenReturn(Optional.of(row));
            return row;
        });
    }

    @Test
    void submissionQueuesOneEmailAndOwnerApprovalPostsExactlyOnce() {
        assertThat(service.submit(20L, staff, "request", request).status()).isEqualTo(PaymentReview.Status.PENDING);
        service.submit(20L, staff, "request", request);
        verifyNoInteractions(payments, mail);
        var emailEvent = new PaymentReviewService.ReviewRequested(
                30L, List.of(new PaymentReviewService.Recipient(owner.getEmail(), owner.getName())));
        verify(events).publishEvent(emailEvent);
        service.notifyOwners(emailEvent);
        verify(mail).sendWorkflowTaskAssigned(eq(owner.getEmail()), eq(owner.getName()), anyString(),
                anyString(), eq("PAYMENT_REVIEW"), eq("30"), isNull(), eq("/dashboard/payments#payment-reviews"));

        assertThatThrownBy(() -> service.decide(30L, staff, true, null)).isInstanceOf(AccessDeniedException.class);
        assertThat(service.decide(30L, owner, true, null).status()).isEqualTo(PaymentReview.Status.APPROVED);
        service.decide(30L, owner, true, null);
        verify(payments).recordPayment(20L, request.amount(), "BANK_TRANSFER", "BANK-20", null, null, owner);
    }

    @Test
    void rejectionRequiresReasonAndDoesNotPostCash() {
        service.submit(20L, staff, "request", request);
        assertThatThrownBy(() -> service.decide(30L, owner, false, " ")).hasMessageContaining("reason is required");
        var result = service.decide(30L, owner, false, "No matching bank deposit");
        assertThat(result.status()).isEqualTo(PaymentReview.Status.REJECTED);
        assertThat(result.decisionReason()).isEqualTo("No matching bank deposit");
        assertThatThrownBy(() -> service.decide(30L, owner, true, null))
                .hasMessageContaining("already been decided");
        verifyNoInteractions(payments);
    }

    @Test
    void providerPostingBeforeApprovalLeavesRequestPending() {
        service.submit(20L, staff, "request", request);
        when(receipts.existsByOrganization_IdAndTransactionReferenceIgnoreCase(1L, "BANK-20")).thenReturn(true);
        assertThatThrownBy(() -> service.decide(30L, owner, true, null)).hasMessageContaining("already posted");
        assertThat(service.submit(20L, staff, "request", request).status()).isEqualTo(PaymentReview.Status.PENDING);
        verifyNoInteractions(payments);
    }

    private User user(long id, String role) {
        var accountRole = new Role();
        accountRole.setName(role);
        return User.builder().id(id).organization(org).role(accountRole)
                .status(User.UserStatus.ACTIVE).email(role + "@example.test").name(role).build();
    }
}
