package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.LendingActionRequest;
import com.patrick.fintech.loan_backend.service.LendingIntelligenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/lending-intelligence")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','CREDIT_ANALYST','LOAN_OFFICER','TELLER')")
public class LendingIntelligenceController {

    private final LendingIntelligenceService service;

    @GetMapping("/catalog")
    public ResponseEntity<?> catalog() {
        return ResponseEntity.ok(service.featureCatalog());
    }

    @GetMapping("/portfolio-risk")
    public ResponseEntity<?> portfolioRisk(@RequestParam(required = false) LocalDate asOf) {
        return ResponseEntity.ok(service.portfolioRisk(asOf));
    }

    @GetMapping("/vintage")
    public ResponseEntity<?> vintage(@RequestParam(required = false) LocalDate from,
                                     @RequestParam(required = false) LocalDate to) {
        return ResponseEntity.ok(service.vintage(from, to));
    }

    @GetMapping("/ecl")
    public ResponseEntity<?> ecl(@RequestParam(required = false) LocalDate asOf) {
        return ResponseEntity.ok(service.ecl(asOf));
    }

    @GetMapping("/affordability")
    public ResponseEntity<?> affordability(
            @RequestParam(required = false) Long bankAccountId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return ResponseEntity.ok(service.affordability(bankAccountId, from, to));
    }

    @GetMapping("/credit-policy/{loanId}")
    public ResponseEntity<?> creditPolicy(@PathVariable Long loanId) {
        return ResponseEntity.ok(service.creditPolicy(loanId));
    }

    @GetMapping("/fraud/{borrowerId}")
    public ResponseEntity<?> fraud(@PathVariable Long borrowerId) {
        return ResponseEntity.ok(service.fraudCheck(borrowerId));
    }

    @GetMapping("/documents/{borrowerId}")
    public ResponseEntity<?> documents(@PathVariable Long borrowerId) {
        return ResponseEntity.ok(service.documentStatus(borrowerId));
    }

    @GetMapping("/payoff/{loanId}")
    public ResponseEntity<?> payoff(@PathVariable Long loanId,
                                    @RequestParam(required = false) LocalDate asOf) {
        return ResponseEntity.ok(service.payoff(loanId, asOf));
    }

    @GetMapping("/prepayment/{loanId}")
    public ResponseEntity<?> prepayment(@PathVariable Long loanId,
                                        @RequestParam(required = false) BigDecimal amount) {
        return ResponseEntity.ok(service.prepayment(loanId, amount));
    }

    @GetMapping("/collections-queue")
    public ResponseEntity<?> collectionsQueue() {
        return ResponseEntity.ok(service.collectionsQueue());
    }

    @GetMapping("/officers")
    public ResponseEntity<?> officers() {
        return ResponseEntity.ok(service.officerAnalytics());
    }

    @GetMapping("/profitability")
    public ResponseEntity<?> profitability() {
        return ResponseEntity.ok(service.profitability());
    }

    @GetMapping("/concentration")
    public ResponseEntity<?> concentration() {
        return ResponseEntity.ok(service.concentration());
    }

    @GetMapping("/stress")
    public ResponseEntity<?> stress() {
        return ResponseEntity.ok(service.stress());
    }

    @GetMapping("/liquidity")
    public ResponseEntity<?> liquidity(@RequestParam(defaultValue = "30") int horizonDays) {
        return ResponseEntity.ok(service.liquidity(horizonDays));
    }

    @GetMapping("/customer-360/{borrowerId}")
    public ResponseEntity<?> customer360(@PathVariable Long borrowerId) {
        return ResponseEntity.ok(service.customer360(borrowerId));
    }

    @GetMapping("/reconciliation-alerts")
    public ResponseEntity<?> reconciliationAlerts() {
        return ResponseEntity.ok(service.reconciliationAlerts());
    }

    @GetMapping("/regulatory-validation")
    public ResponseEntity<?> regulatoryValidation() {
        return ResponseEntity.ok(service.regulatoryValidation());
    }

    @GetMapping("/records")
    public ResponseEntity<?> records(@RequestParam(required = false) String type) {
        return ResponseEntity.ok(service.records(type));
    }

    @PostMapping("/records")
    public ResponseEntity<?> createRecord(@Valid @RequestBody LendingActionRequest request) {
        return ResponseEntity.ok(service.createWorkflowRecord(request));
    }

    @PatchMapping("/records/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id,
                                          @RequestParam String value) {
        return ResponseEntity.ok(service.updateStatus(id, value));
    }
}
