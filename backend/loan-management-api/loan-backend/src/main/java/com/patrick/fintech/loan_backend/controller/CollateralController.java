package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.model.Collateral;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.repository.CollateralRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/loans/{loanId}/collateral")
@RequiredArgsConstructor
@org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER')")
public class CollateralController {

    private final CollateralRepository collateralRepo;
    private final LoanRepository loanRepo;
    private final CurrentUserUtil currentUserUtil;
    private final AuditService auditService;

    @GetMapping
    public ResponseEntity<ApiResponse<Object>> list(@PathVariable Long loanId) {
        var user = currentUserUtil.getCurrentUser();
        Loan loan = getOwnedLoan(loanId, user);
        return ResponseEntity.ok(ApiResponse.safe(
                collateralRepo.findByLoan_IdAndOrganization_Id(
                        loan.getId(), user.getOrganization().getId())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Object>> add(@PathVariable Long loanId, @RequestBody Map<String, Object> body) {
        var user = currentUserUtil.getCurrentUser();
        Loan loan = getOwnedLoan(loanId, user);

        Collateral c = Collateral.builder()
                .loan(loan).organization(loan.getOrganization())
                .type(Collateral.CollateralType.valueOf(body.get("type").toString()))
                .description(str(body, "description")).ownerName(str(body, "ownerName"))
                .estimatedValue(num(body, "estimatedValue"))
                .currency(str(body, "currency") != null ? str(body, "currency") : loan.getCurrency())
                .insured(Boolean.TRUE.equals(body.get("insured")))
                .insuranceExpiryDate(body.get("insuranceExpiryDate") != null
                        ? LocalDate.parse(body.get("insuranceExpiryDate").toString())
                        : null)
                .insurancePolicyNumber(str(body, "insurancePolicyNumber"))
                .documentUrl(str(body, "documentUrl"))
                .registrationNumber(str(body, "registrationNumber"))
                .build();
        c = collateralRepo.save(c);

        auditService.log(loan.getOrganization(), user, "COLLATERAL_ADDED", "LOAN", loanId.toString(),
                c.getType() + " collateral added, estimated value " + c.getEstimatedValue());
        return ResponseEntity.ok(ApiResponse.safe("Collateral added", c));
    }

    @PutMapping("/{collateralId}/status")
    public ResponseEntity<ApiResponse<Object>> updateStatus(
            @PathVariable Long loanId, @PathVariable Long collateralId, @RequestBody Map<String, String> body) {
        var user = currentUserUtil.getCurrentUser();
        Loan loan = getOwnedLoan(loanId, user);
        Collateral c = collateralRepo.findById(collateralId)
                .orElseThrow(() -> new RuntimeException("Collateral not found"));
        assertCollateralBelongsToLoan(c, loan, user);

        Collateral.CollateralStatus newStatus = Collateral.CollateralStatus.valueOf(body.get("status"));
        c.setStatus(newStatus);
        c = collateralRepo.save(c);
        auditService.log(c.getOrganization(), user, "COLLATERAL_STATUS_CHANGED", "LOAN", loanId.toString(),
                "Collateral #" + collateralId + " set to " + newStatus);
        return ResponseEntity.ok(ApiResponse.safe(c));
    }

    @DeleteMapping("/{collateralId}")
    public ResponseEntity<ApiResponse<String>> remove(@PathVariable Long loanId, @PathVariable Long collateralId) {
        var user = currentUserUtil.getCurrentUser();
        Loan loan = getOwnedLoan(loanId, user);
        Collateral c = collateralRepo.findById(collateralId)
                .orElseThrow(() -> new RuntimeException("Collateral not found"));
        assertCollateralBelongsToLoan(c, loan, user);

        collateralRepo.delete(c);
        auditService.log(c.getOrganization(), user, "COLLATERAL_REMOVED", "LOAN", loanId.toString(),
                "Collateral removed");
        return ResponseEntity.ok(ApiResponse.safe("Collateral removed"));
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

    private void assertCollateralBelongsToLoan(
            Collateral collateral,
            Loan loan,
            com.patrick.fintech.loan_backend.model.User user) {
        if (collateral.getOrganization() == null || collateral.getOrganization().getId() == null
                || !collateral.getOrganization().getId().equals(user.getOrganization().getId())
                || collateral.getLoan() == null || collateral.getLoan().getId() == null
                || !collateral.getLoan().getId().equals(loan.getId())) {
            throw new RuntimeException("Access denied");
        }
    }

    private String str(Map<String, Object> b, String k) {
        return b.get(k) != null ? b.get(k).toString() : null;
    }

    private Double num(Map<String, Object> b, String k) {
        return b.get(k) != null ? Double.valueOf(b.get(k).toString()) : null;
    }
}
