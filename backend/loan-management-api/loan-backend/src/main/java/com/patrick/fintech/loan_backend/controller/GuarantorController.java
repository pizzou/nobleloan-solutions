package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.model.Guarantor;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.repository.GuarantorRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/loans/{loanId}/guarantors")
@RequiredArgsConstructor
@org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER')")
public class GuarantorController {

    private final GuarantorRepository guarantorRepo;
    private final LoanRepository loanRepo;
    private final CurrentUserUtil currentUserUtil;
    private final AuditService auditService;

    @GetMapping
    public ResponseEntity<ApiResponse<Object>> list(@PathVariable Long loanId) {
        var user = currentUserUtil.getCurrentUser();
        Loan loan = getOwnedLoan(loanId, user);
        return ResponseEntity.ok(ApiResponse.safe(
                guarantorRepo.findByLoan_IdAndOrganization_Id(
                        loan.getId(), user.getOrganization().getId())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Object>> add(@PathVariable Long loanId, @RequestBody Map<String, Object> body) {
        var user = currentUserUtil.getCurrentUser();
        Loan loan = getOwnedLoan(loanId, user);

        Guarantor g = Guarantor.builder()
                .loan(loan).organization(loan.getOrganization())
                .fullName(str(body, "fullName")).nationalId(str(body, "nationalId")).phone(str(body, "phone"))
                .address(str(body, "address")).relationship(str(body, "relationship"))
                .employerName(str(body, "employerName"))
                .monthlyIncome(num(body, "monthlyIncome"))
                .guaranteedAmount(num(body, "guaranteedAmount"))
                .consentGiven(Boolean.TRUE.equals(body.get("consentGiven")))
                .documentUrl(str(body, "documentUrl"))
                .build();
        g = guarantorRepo.save(g);

        auditService.log(loan.getOrganization(), user, "GUARANTOR_ADDED", "LOAN", loanId.toString(),
                "Guarantor " + g.getFullName() + " added for "
                        + (g.getGuaranteedAmount() != null ? g.getGuaranteedAmount() : 0));
        return ResponseEntity.ok(ApiResponse.safe("Guarantor added", g));
    }

    @DeleteMapping("/{guarantorId}")
    public ResponseEntity<ApiResponse<String>> remove(@PathVariable Long loanId, @PathVariable Long guarantorId) {
        var user = currentUserUtil.getCurrentUser();
        Loan loan = getOwnedLoan(loanId, user);
        Guarantor g = guarantorRepo.findById(guarantorId)
                .orElseThrow(() -> new RuntimeException("Guarantor not found"));
        if (g.getOrganization() == null || g.getOrganization().getId() == null
                || !g.getOrganization().getId().equals(user.getOrganization().getId())
                || g.getLoan() == null || g.getLoan().getId() == null
                || !g.getLoan().getId().equals(loan.getId())) {
            throw new RuntimeException("Access denied");
        }
        guarantorRepo.delete(g);
        auditService.log(g.getOrganization(), user, "GUARANTOR_REMOVED", "LOAN", loanId.toString(),
                "Guarantor " + g.getFullName() + " removed");
        return ResponseEntity.ok(ApiResponse.safe("Guarantor removed"));
    }

    private Loan getOwnedLoan(Long loanId, com.patrick.fintech.loan_backend.model.User user) {
        if (loanId == null || loanId <= 0 || user == null || user.getOrganization() == null
                || user.getOrganization().getId() == null) {
            throw new RuntimeException("Access denied");
        }
        Loan loan = loanRepo.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));
        if (loan.getOrganization() == null || loan.getOrganization().getId() == null
                || !loan.getOrganization().getId().equals(user.getOrganization().getId())) {
            throw new RuntimeException("Access denied");
        }
        return loan;
    }

    private String str(Map<String, Object> b, String k) {
        return b.get(k) != null ? b.get(k).toString() : null;
    }

    private Double num(Map<String, Object> b, String k) {
        return b.get(k) != null ? Double.valueOf(b.get(k).toString()) : null;
    }
}
