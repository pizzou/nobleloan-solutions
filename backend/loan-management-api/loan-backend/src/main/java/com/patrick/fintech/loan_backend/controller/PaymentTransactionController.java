package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.dto.PaymentTransactionResponse;
import com.patrick.fintech.loan_backend.model.PaymentTransaction;
import com.patrick.fintech.loan_backend.repository.PaymentTransactionRepository;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import com.patrick.fintech.loan_backend.service.PaymentAllocationService.Allocation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Organization-wide immutable payment transaction ledger.
 * This is the authoritative viewer-facing transaction history.
 */
@RestController
@RequestMapping("/api/payment-transactions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','ACCOUNTANT','AUDITOR')")
public class PaymentTransactionController {

    private final PaymentTransactionRepository repository;
    private final CurrentUserUtil currentUserUtil;
    private final JdbcTemplate jdbc;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PaymentTransactionResponse>>> list() {
        Long organizationId = currentUserUtil.getCurrentOrganizationId();

        Map<Long, Allocation> corrections = new HashMap<>();
        jdbc.query("""
                SELECT correction.receipt_id, correction.penalty, correction.extension_fee,
                       correction.interest, correction.management_fee, correction.principal, correction.unapplied
                FROM historical_receipt_reallocations correction
                JOIN payment_transactions receipt ON receipt.id = correction.receipt_id
                WHERE receipt.organization_id = ?
                """, rs -> {
            corrections.put(rs.getLong("receipt_id"), new Allocation(rs.getBigDecimal("penalty"),
                    rs.getBigDecimal("extension_fee"), rs.getBigDecimal("interest"),
                    rs.getBigDecimal("management_fee"), rs.getBigDecimal("principal"),
                    rs.getBigDecimal("unapplied")));
        }, organizationId);

        List<PaymentTransactionResponse> rows = repository
                .findVisibleByOrganizationIdOrderByCreatedAtDesc(
                        organizationId,
                        com.patrick.fintech.loan_backend.service.ReportingScopeService.includeBusinessOwnerOnly())
                .stream()
                .filter(tx -> tx != null)
                .map(tx -> toResponse(tx, corrections.get(tx.getId())))
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(rows));
    }

    private PaymentTransactionResponse toResponse(PaymentTransaction source, Allocation correction) {
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
        target.setPrincipalComponent(correction == null ? source.getPrincipalComponent() : correction.principal());
        target.setInterestComponent(correction == null ? source.getInterestComponent() : correction.interest());
        target.setManagementFeeComponent(correction == null ? source.getManagementFeeComponent() : correction.managementFee());
        target.setExtensionFeeComponent(correction == null ? source.getExtensionFeeComponent() : correction.extensionFee());
        target.setPenaltyComponent(correction == null ? source.getPenaltyComponent() : correction.penalty());
        target.setUnappliedAmount(correction == null ? source.getUnappliedAmount() : correction.overpayment());
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
