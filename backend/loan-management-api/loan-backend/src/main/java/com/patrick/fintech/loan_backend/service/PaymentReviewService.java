package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.model.PaymentReview;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.PaymentRepository;
import com.patrick.fintech.loan_backend.repository.PaymentReviewRepository;
import com.patrick.fintech.loan_backend.repository.PaymentTransactionRepository;
import com.patrick.fintech.loan_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.patrick.fintech.loan_backend.model.PaymentReview.Status.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentReviewService {
    private static final Set<String> MAKERS = Set.of("ADMIN", "MANAGER", "ACCOUNTANT", "TELLER", "LOAN_OFFICER");
    private static final Set<String> METHODS = Set.of(
            "BANK_TRANSFER", "CASH", "MOBILE_MONEY", "CARD", "CHEQUE", "DIRECT_DEBIT");

    private final PaymentReviewRepository reviews;
    private final LoanRepository loans;
    private final PaymentTransactionRepository receipts;
    private final PaymentRepository installments;
    private final UserRepository users;
    private final PaymentService payments;
    private final ApplicationEventPublisher events;
    private final MailService mail;

    public record Request(BigDecimal amount, String paymentMethod, String transactionId, String channel, String notes) {}

    public record Review(Long id, Long loanId, String loanReference, BigDecimal amount, String currency,
                         String method, String reference, String channel, String notes, String submittedByName,
                         Instant submittedAt, PaymentReview.Status status, Instant decidedAt, String decisionReason) {}

    record Recipient(String email, String name) {}
    record ReviewRequested(Long id, List<Recipient> owners) {}

    @Transactional
    public Review submit(Long loanId, User maker, String key, Request input) {
        if (!MAKERS.contains(role(maker))) throw new AccessDeniedException("Payment submission is restricted to authorized staff");
        Long org = organization(maker);
        String requestKey = text(key, 128, "Request key", true);
        if (input == null || input.amount() == null || input.amount().signum() <= 0
                || input.amount().stripTrailingZeros().scale() > 2
                || input.amount().compareTo(new BigDecimal("100000000000000000")) >= 0) {
            throw new IllegalArgumentException("Enter a positive payment amount with at most two decimal places");
        }
        BigDecimal amount = input.amount().setScale(2);
        String method = text(input.paymentMethod(), 32, "Payment method", false);
        method = method == null ? "BANK_TRANSFER" : method.toUpperCase(Locale.ROOT);
        if (!METHODS.contains(method)) throw new IllegalArgumentException("Unsupported payment method");
        String reference = text(input.transactionId(), 100, "Bank transaction or receipt reference", true);
        String channel = text(input.channel(), 50, "Channel", false);
        String notes = text(input.notes(), 2000, "Notes", false);

        Loan loan = lockLoan(loanId, org, false);
        var existing = reviews.findByOrganizationIdAndRequestKey(org, requestKey);
        if (existing.isPresent()) {
            var review = existing.get();
            if (!review.getSubmittedBy().equals(maker.getId()) || !review.getLoan().getId().equals(loanId)
                    || review.getAmount().compareTo(amount) != 0 || !review.getReference().equals(reference)
                    || !review.getMethod().equals(method) || !java.util.Objects.equals(review.getChannel(), channel)
                    || !java.util.Objects.equals(review.getNotes(), notes)) {
                throw new IllegalStateException("This request key is already in use");
            }
            return view(review);
        }
        if (loan.getStatus() != LoanStatus.ACTIVE && loan.getStatus() != LoanStatus.OVERDUE) {
            throw new IllegalStateException("Only active or overdue loans can receive a payment");
        }
        rejectPostedReference(org, reference);
        if (reviews.existsByOrganizationIdAndReferenceIgnoreCaseAndStatusNot(org, reference, REJECTED)) {
            throw new IllegalStateException("That transaction reference already has a payment review");
        }
        List<Recipient> recipients = users.findActiveUsersByOrganizationAndRole(
                        org, "BUSINESS_OWNER", User.UserStatus.ACTIVE, Pageable.unpaged()).stream()
                .filter(user -> user.getDeletedAt() == null && user.getEmail() != null && !user.getEmail().isBlank())
                .map(user -> new Recipient(user.getEmail(), user.getName())).toList();
        if (recipients.isEmpty()) throw new IllegalStateException("An active Business Owner with an email address is required");

        var review = new PaymentReview();
        review.setLoan(loan);
        review.setOrganizationId(org);
        review.setRequestKey(requestKey);
        review.setAmount(amount);
        review.setMethod(method);
        review.setReference(reference);
        review.setChannel(channel);
        review.setNotes(notes);
        review.setSubmittedBy(maker.getId());
        review.setSubmittedByName(maker.getName() == null ? "Staff" : maker.getName());
        review.setSubmittedAt(Instant.now());
        review.setStatus(PENDING);
        try {
            reviews.saveAndFlush(review);
        } catch (DataIntegrityViolationException conflict) {
            throw new IllegalStateException("This transaction reference or request key already has a payment review", conflict);
        }
        events.publishEvent(new ReviewRequested(review.getId(), recipients));
        return view(review);
    }

    @Transactional(readOnly = true)
    public List<Review> list(User user, boolean pendingOnly, int page) {
        boolean owner = "BUSINESS_OWNER".equals(role(user));
        if (!owner && !MAKERS.contains(role(user))) throw new AccessDeniedException("Payment reviews are restricted");
        return reviews.findVisible(organization(user), user.getId(), owner, pendingOnly ? PENDING : null,
                PageRequest.of(page, 25)).stream().map(this::view).toList();
    }

    @Transactional
    public Review decide(Long id, User owner, boolean accept, String reason) {
        if (!"BUSINESS_OWNER".equals(role(owner))) {
            throw new AccessDeniedException("Only the Business Owner can decide a payment review");
        }
        Long org = organization(owner);
        var review = reviews.findForDecision(org, id)
                .orElseThrow(() -> new IllegalArgumentException("Payment review not found"));
        if (review.getSubmittedBy().equals(owner.getId())) throw new AccessDeniedException("A submitter cannot approve their own request");
        var decision = accept ? APPROVED : REJECTED;
        if (review.getStatus() == decision) return view(review);
        if (review.getStatus() != PENDING) throw new IllegalStateException("This payment review has already been decided");

        String decisionReason = text(reason, 1000, "Rejection reason", !accept);
        if (accept) {
            lockLoan(review.getLoan().getId(), org, true);
            rejectPostedReference(org, review.getReference());
            payments.recordPayment(review.getLoan().getId(), review.getAmount(), review.getMethod(),
                    review.getReference(), review.getChannel(), review.getNotes(), owner);
        }
        review.setStatus(decision);
        review.setDecidedAt(Instant.now());
        review.setDecidedBy(owner.getId());
        review.setDecisionReason(decisionReason);
        return view(review);
    }

    @TransactionalEventListener
    public void notifyOwners(ReviewRequested request) {
        for (Recipient owner : request.owners()) {
            try {
                mail.sendWorkflowTaskAssigned(owner.email(), owner.name(), "Verify a borrower payment",
                        "Compare the submitted payment with the bank or collection record. Approve or reject the request in Payments.",
                        "PAYMENT_REVIEW", Long.toString(request.id()), null, "/dashboard/payments#payment-reviews");
            } catch (RuntimeException failure) {
                log.warn("Could not queue Business Owner email for payment review {}", request.id(), failure);
            }
        }
    }

    private void rejectPostedReference(Long organization, String reference) {
        if (receipts.existsByOrganization_IdAndTransactionReferenceIgnoreCase(organization, reference)
                || installments.existsByOrganization_IdAndTransactionIdIgnoreCase(organization, reference)) {
            throw new IllegalStateException("That transaction reference is already posted");
        }
    }

    private Loan lockLoan(Long id, Long org, boolean owner) {
        return loans.findVisibleByIdForUpdate(id, org, owner).filter(loan -> loan.getDeletedAt() == null)
                .orElseThrow(() -> new IllegalArgumentException("Loan not found"));
    }

    private Review view(PaymentReview review) {
        return new Review(review.getId(), review.getLoan().getId(), review.getLoan().getReferenceNumber(),
                review.getAmount(), review.getLoan().getCurrency(), review.getMethod(), review.getReference(),
                review.getChannel(), review.getNotes(), review.getSubmittedByName(), review.getSubmittedAt(),
                review.getStatus(), review.getDecidedAt(), review.getDecisionReason());
    }

    private String role(User user) {
        return user == null || user.getRole() == null ? "" : user.getRole().getName();
    }

    private Long organization(User user) {
        if (user == null || user.getId() == null || user.getOrganization() == null
                || user.getOrganization().getId() == null || user.getDeletedAt() != null
                || user.getStatus() != User.UserStatus.ACTIVE) {
            throw new AccessDeniedException("An active organization account is required");
        }
        return user.getOrganization().getId();
    }

    private String text(String value, int maxLength, String name, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) throw new IllegalArgumentException(name + " is required");
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) throw new IllegalArgumentException(name + " exceeds " + maxLength + " characters");
        return trimmed;
    }
}
