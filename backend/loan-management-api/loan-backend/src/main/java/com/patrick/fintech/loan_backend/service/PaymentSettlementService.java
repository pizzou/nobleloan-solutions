package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.BankStatementLine;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.model.PaymentSettlement;
import com.patrick.fintech.loan_backend.repository.BankStatementLineRepository;
import com.patrick.fintech.loan_backend.repository.PaymentRepository;
import com.patrick.fintech.loan_backend.repository.PaymentSettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PaymentSettlementService {

    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    private final PaymentSettlementRepository repo;
    private final PaymentRepository paymentRepo;
    private final BankStatementLineRepository statementRepo;

    @Transactional
    public PaymentSettlement record(
            Organization org,
            String provider,
            String providerReference,
            String internalRef,
            BigDecimal amount,
            String currency,
            LocalDate date,
            Long bankAccountId) {

        requireOrganization(org);

        if (provider == null || provider.isBlank()
                || providerReference == null || providerReference.isBlank()) {
            throw new IllegalArgumentException("Provider and provider reference are required");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Settlement amount must be positive");
        }

        boolean businessOwnerOnly = false;

        if (internalRef != null && !internalRef.isBlank()) {
            Payment payment = paymentRepo.findByPaymentReference(internalRef.trim())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Internal payment reference not found: " + internalRef));

            assertPaymentOrganization(payment, org);

            if (payment.getLoan() == null) {
                throw new IllegalStateException("Payment is not associated with a loan");
            }

            businessOwnerOnly = Boolean.TRUE.equals(payment.getLoan().getBusinessOwnerOnly());
            assertScope(businessOwnerOnly);

            BigDecimal internalAmount = payment.getAmountPaid() == null
                    ? BigDecimal.ZERO
                    : payment.getAmountPaid();
            if (internalAmount.subtract(amount).abs().compareTo(TOLERANCE) > 0) {
                throw new IllegalStateException("Settlement amount does not match internal payment amount");
            }
        }

        if (repo.findByOrganization_IdAndProviderAndProviderReference(
                org.getId(),
                provider.trim().toUpperCase(Locale.ROOT),
                providerReference.trim()).isPresent()) {
            throw new IllegalStateException("Settlement already exists for provider reference");
        }

        return repo.save(PaymentSettlement.builder()
                .organization(org)
                .provider(provider.trim().toUpperCase(Locale.ROOT))
                .providerReference(providerReference.trim())
                .internalPaymentReference(internalRef == null ? null : internalRef.trim())
                .amount(amount.setScale(2, RoundingMode.HALF_UP))
                .currency(currency == null ? "RWF" : currency.trim().toUpperCase(Locale.ROOT))
                .settlementDate(date == null ? LocalDate.now() : date)
                .bankAccountId(bankAccountId)
                .businessOwnerOnly(businessOwnerOnly)
                .build());
    }

    @Transactional
    public PaymentSettlement reconcile(
            Organization org,
            Long settlementId,
            Long statementLineId,
            String actor) {

        requireOrganization(org);

        PaymentSettlement settlement = repo.findById(settlementId)
                .orElseThrow(() -> new IllegalArgumentException("Settlement not found"));

        if (settlement.getOrganization() == null
                || !org.getId().equals(settlement.getOrganization().getId())) {
            throw new AccessDeniedException("Cross-organization settlement access denied");
        }

        assertScope(Boolean.TRUE.equals(settlement.getBusinessOwnerOnly()));

        BankStatementLine bankStatement = statementRepo.findForUpdate(
                        statementLineId, org.getId())
                .orElseThrow(() -> new IllegalArgumentException("Bank statement line not found"));

        if (!Objects.equals(settlement.getCurrency(), bankStatement.getCurrency())) {
            throw new IllegalStateException("Settlement currency does not match statement currency");
        }

        BigDecimal difference = settlement.getAmount()
                .subtract(bankStatement.getAmount())
                .setScale(2, RoundingMode.HALF_UP);

        if (difference.abs().compareTo(TOLERANCE) > 0) {
            throw new IllegalStateException(
                    "Settlement and bank statement amount do not match: " + difference);
        }

        settlement.setStatus("RECONCILED");
        settlement.setBankStatementLineId(bankStatement.getId());
        settlement.setDifference(difference);
        settlement.setMatchedAt(LocalDateTime.now());

        bankStatement.setReconciliationStatus("MATCHED");
        bankStatement.setMatchedBy(actor);
        bankStatement.setMatchedAt(LocalDateTime.now());
        statementRepo.save(bankStatement);

        return repo.save(settlement);
    }

    @Transactional(readOnly = true)
    public List<PaymentSettlement> pending(Organization org) {
        requireOrganization(org);

        List<PaymentSettlement> settlements = repo
                .findByOrganization_IdAndStatusOrderBySettlementDateAsc(
                        org.getId(), "UNRECONCILED");

        boolean include = ReportingScopeService.includeBusinessOwnerOnly();
        return settlements == null ? List.of() : settlements.stream()
                .filter(Objects::nonNull)
                .filter(s -> include || !Boolean.TRUE.equals(s.getBusinessOwnerOnly()))
                .toList();
    }

    private void assertScope(boolean businessOwnerOnly) {
        if (businessOwnerOnly && !ReportingScopeService.includeBusinessOwnerOnly()) {
            throw new AccessDeniedException(
                    "This accounting settlement belongs to the Business Owner reporting scope.");
        }
    }

    private void requireOrganization(Organization org) {
        if (org == null || org.getId() == null) {
            throw new IllegalArgumentException("Organization is required");
        }
    }

    private void assertPaymentOrganization(Payment payment, Organization org) {
        if (payment.getOrganization() == null
                || payment.getOrganization().getId() == null
                || !org.getId().equals(payment.getOrganization().getId())) {
            throw new AccessDeniedException("Cross-organization payment settlement denied");
        }
    }
}
