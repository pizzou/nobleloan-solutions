package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.model.BorrowerFile;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.BorrowerFileRepository;
import com.patrick.fintech.loan_backend.model.DocumentType;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.BorrowerFileService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Stores the returned, signed contract in borrower_files and ties it to the exact loan. */
@RestController
@RequestMapping("/api/loans/{loanId}/contract/signed")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER')")
public class LoanSignedContractController {
    private final LoanRepository loanRepository;
    private final BorrowerFileRepository borrowerFileRepository;
    private final BorrowerFileService borrowerFileService;
    private final CurrentUserUtil currentUserUtil;
    private final AuditService auditService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadSignedCopy(
            @PathVariable Long loanId,
            @RequestParam("file") MultipartFile file) throws IOException {
        User actor = currentUserUtil.getCurrentUser();
        Loan loan = findAuthorizedLoan(loanId, actor);
        BorrowerFile saved = borrowerFileService.uploadSignedContractForLoan(
                loan.getBorrower().getId(), loan, file);
        auditService.log(loan.getOrganization(), actor, "SIGNED_LOAN_CONTRACT_UPLOADED",
                "BORROWER_FILE", String.valueOf(saved.getId()),
                "Uploaded signed contract for loan " + loan.getReferenceNumber(),
                null, null, "Loan Contracts");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", saved.getId());
        response.put("loanId", loan.getId());
        response.put("borrowerId", loan.getBorrower().getId());
        response.put("fileName", saved.getFileName());
        response.put("fileType", saved.getFileType());
        response.put("uploadedAt", saved.getUploadedAt());
        response.put("documentType", saved.getDocumentType().name());
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getSignedCopyMetadata(@PathVariable Long loanId) {
        User actor = currentUserUtil.getCurrentUser();
        Loan loan = findAuthorizedLoan(loanId, actor);
        // Query by the stored loan foreign key so documents for other loans of this
        // borrower are never mixed into this loan's contract list.
        var documents = borrowerFileRepository.findByLoan_IdAndDocumentTypeOrderByUploadedAtDesc(
                        loanId, DocumentType.SIGNED_LOAN_CONTRACT).stream()
                .map(file -> Map.of(
                        "id", (Object) file.getId(),
                        "fileName", file.getFileName() == null ? "Signed loan contract" : file.getFileName(),
                        "uploadedAt", file.getUploadedAt() == null ? "" : file.getUploadedAt().toString(),
                        "downloadUrl", "/api/files/download/" + file.getId()))
                .toList();
        return ResponseEntity.ok(Map.of("loanId", loanId, "documents", documents));
    }

    private Loan findAuthorizedLoan(Long loanId, User actor) {
        if (actor == null || actor.getOrganization() == null || actor.getOrganization().getId() == null) {
            throw new IllegalArgumentException("An authenticated organization user is required.");
        }
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Loan not found."));
        if (loan.getOrganization() == null || loan.getOrganization().getId() == null
                || !loan.getOrganization().getId().equals(actor.getOrganization().getId())) {
            throw new IllegalArgumentException("You are not authorized to access this loan.");
        }
        if (loan.getBorrower() == null || loan.getBorrower().getId() == null) {
            throw new IllegalStateException("This loan has no linked borrower record.");
        }
        return loan;
    }
}
