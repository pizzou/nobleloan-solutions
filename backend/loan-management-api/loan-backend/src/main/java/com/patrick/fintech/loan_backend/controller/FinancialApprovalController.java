package com.patrick.fintech.loan_backend.controller;
import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.service.FinancialApprovalService;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.*; import org.springframework.http.*; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal; import java.util.*;
@RestController @RequestMapping("/api/financial-approvals") @RequiredArgsConstructor @PreAuthorize("hasAnyRole('ADMIN','MANAGER','ACCOUNTANT')")
public class FinancialApprovalController{
 private final FinancialApprovalService service; private final com.patrick.fintech.loan_backend.repository.FinancialApprovalRepository approvalRepo; private final OrganizationRepository orgRepo; private final CurrentUserUtil current;
 @PostMapping public ResponseEntity<ApiResponse<FinancialApproval>> submit(@RequestBody Request r){Organization o=org(); User u=current.getCurrentUser(); FinancialApproval a=service.submit(o,r.operationType(),r.operationId(),r.amount(),r.reason(),r.payload(),u.getId(),u.getName());return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.safe(a));}
 @PostMapping("/{id}/approve") public ResponseEntity<ApiResponse<FinancialApproval>> approve(@PathVariable Long id,@RequestBody(required=false) Decision d){User u=current.getCurrentUser();return ResponseEntity.ok(ApiResponse.safe(service.approve(id,org(),u.getId(),u.getName(),u.getRole()==null?null:u.getRole().getName(),d==null?null:d.comments())));}
 @PostMapping("/{id}/reject") public ResponseEntity<ApiResponse<FinancialApproval>> reject(@PathVariable Long id,@RequestBody(required=false) Decision d){User u=current.getCurrentUser();return ResponseEntity.ok(ApiResponse.safe(service.reject(id,org(),u.getId(),u.getName(),u.getRole()==null?null:u.getRole().getName(),d==null?null:d.comments())));}
 @GetMapping("/pending") public ResponseEntity<ApiResponse<List<FinancialApproval>>> pending(){return ResponseEntity.ok(ApiResponse.safe(serviceList()));}
 private List<FinancialApproval> serviceList(){return approvalRepo.findByOrganization_IdAndStatusOrderByCreatedAtAsc(org().getId(),"PENDING");}
 private Organization org(){return orgRepo.findById(current.getCurrentOrganizationId()).orElseThrow(()->new IllegalStateException("Organization not found"));}
 public record Request(String operationType,String operationId,BigDecimal amount,String reason,String payload){}
 public record Decision(String comments){}
}
