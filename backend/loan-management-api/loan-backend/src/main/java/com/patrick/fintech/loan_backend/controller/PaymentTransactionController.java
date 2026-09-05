package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.dto.PaymentTransactionResponse;
import com.patrick.fintech.loan_backend.model.PaymentTransaction;
import com.patrick.fintech.loan_backend.repository.PaymentTransactionRepository;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Organization-wide immutable payment transaction ledger.
 * This is the authoritative viewer-facing transaction history.
 */
@RestController
@RequestMapping("/api/payment-transactions")
@RequiredArgsConstructor
public class PaymentTransactionController {

    private final PaymentTransactionRepository repository;
    private final CurrentUserUtil currentUserUtil;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PaymentTransactionResponse>>> list() {
        Long organizationId = currentUserUtil.getCurrentOrganizationId();

        List<PaymentTransactionResponse> rows = repository
                .findByOrganization_IdOrderByCreatedAtDesc(organizationId)
                .stream()
                .filter(tx -> tx != null)
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(rows));
    }

    private PaymentTransactionResponse toResponse(PaymentTransaction source) {
        PaymentTransactionResponse target = new PaymentTransactionResponse();

        target.setId(source.getId());
        target.setOrganizationId(source.getOrganization() != null
                ? source.getOrganization().getId() : null);
        target.setLoanId(source.getLoan() != null ? source.getLoan().getId() : null);
        target.setLoanReference(source.getLoan() != null
                ? source.getLoan().getReferenceNumber() : null);

        if (source.getLoan() != null && source.getLoan().getBorrower() != null) {
            var borrower = source.getLoan().getBorrower();
            target.setBorrowerId(borrower.getId());
            String first = borrower.getFirstName() == null ? "" : borrower.getFirstName().trim();
            String last = borrower.getLastName() == null ? "" : borrower.getLastName().trim();
            target.setBorrowerName((first + " " + last).trim());
        }

        if (source.getRecordedBy() != null) {
            target.setRecordedById(source.getRecordedBy().getId());
            target.setRecordedByName(source.getRecordedBy().getName());
        }

        target.setTransactionReference(source.getTransactionReference());
        target.setAmount(source.getAmount());
        target.setPrincipalComponent(source.getPrincipalComponent());
        target.setInterestComponent(source.getInterestComponent());
        target.setManagementFeeComponent(source.getManagementFeeComponent());
        target.setExtensionFeeComponent(source.getExtensionFeeComponent());
        target.setPenaltyComponent(source.getPenaltyComponent());
        target.setUnappliedAmount(source.getUnappliedAmount());
        target.setProvider(source.getProvider());
        target.setCurrency(source.getCurrency());
        target.setExternalReference(source.getExternalReference());
        target.setGatewayStatus(source.getGatewayStatus());
        target.setPaymentMethod(source.getPaymentMethod());
        target.setChannel(source.getChannel());
        target.setNotes(source.getNotes());
        target.setStatus(source.getStatus() != null ? source.getStatus().name() : null);
        target.setReversed(source.getReversed());
        target.setCreatedAt(source.getCreatedAt());
        target.setReversedAt(source.getReversedAt());
        target.setReversalReason(source.getReversalReason());
        target.setReversalReference(source.getReversalReference());

        return target;
    }
}
