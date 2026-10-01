package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.mapper.ResponseDtoMapper;
import com.patrick.fintech.loan_backend.model.AuditLog;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.repository.AuditLogRepository;
import com.patrick.fintech.loan_backend.service.AuditHashChain;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    private final AuditLogRepository auditLogRepo;
    private final CurrentUserUtil currentUserUtil;

    @GetMapping
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Object>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Organization org = currentUserUtil.getCurrentUser().getOrganization();
        return ResponseEntity.ok(ApiResponse.safe(
                auditLogRepo.findByInstitutionOrderByTimestampDesc(org, PageRequest.of(page, size))
                        .getContent()));
    }

    /**
     * Walks the entire tamper-evidence hash chain and confirms nothing has
     * been altered or deleted since it was written. Returns the first broken
     * link found, if any — for a regulator or internal audit to point to
     * concrete proof the log is (or isn't) intact.
     */
    @GetMapping("/verify")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyChain() {
        List<AuditLog> all = auditLogRepo.findAllByOrderByIdAsc();
        String expectedPrevious = "GENESIS";
        for (AuditLog entry : all) {
            if (!java.util.Objects.equals(expectedPrevious, entry.getPreviousHash())) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("intact", false);
                result.put("brokenAtId", entry.getId());
                result.put("brokenAtTimestamp", entry.getTimestamp());
                result.put("message", "Entry #" + entry.getId()
                        + " points to an unexpected previous audit hash; an entry may have been altered or deleted.");
                return ResponseEntity.ok(ApiResponse.safe(result));
            }

            String recomputed = AuditHashChain.hashFor(expectedPrevious, entry);

            if (!recomputed.equals(entry.getEntryHash())) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("intact", false);
                result.put("brokenAtId", entry.getId());
                result.put("brokenAtTimestamp", entry.getTimestamp());
                result.put("message", "Entry #" + entry.getId() + " does not match its expected hash — "
                        + "it, or an entry before it, may have been altered after being written.");
                return ResponseEntity.ok(ApiResponse.safe(result));
            }
            expectedPrevious = entry.getEntryHash();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("intact", true);
        result.put("entriesVerified", all.size());
        result.put("message", "All " + all.size() + " audit entries verified intact — no tampering detected.");
        return ResponseEntity.ok(ApiResponse.safe(result));
    }

    /**
     * CSV export for regulators/external auditors — full org history, not just the
     * current page.
     */
    @GetMapping("/export")
    public ResponseEntity<String> export() {
        Organization org = currentUserUtil.getCurrentUser().getOrganization();
        List<AuditLog> logs = auditLogRepo.findByInstitutionOrderByTimestampDesc(org, PageRequest.of(0, 100000))
                .getContent();

        StringBuilder csv = new StringBuilder(
                "Timestamp,User,Action,Module,EntityType,EntityId,IPAddress,Location,OS,Browser,Description\n");
        for (AuditLog l : logs) {
            csv.append(csvField(l.getTimestamp() != null ? l.getTimestamp().toString() : ""))
                    .append(',').append(csvField(l.getUser() != null ? l.getUser().getName() : "System/Public"))
                    .append(',').append(csvField(l.getAction()))
                    .append(',').append(csvField(l.getModule()))
                    .append(',').append(csvField(l.getEntityType()))
                    .append(',').append(csvField(l.getEntityId()))
                    .append(',').append(csvField(l.getIpAddress()))
                    .append(',').append(csvField(l.getLocation()))
                    .append(',').append(csvField(l.getOperatingSystem()))
                    .append(',').append(csvField(l.getBrowser()))
                    .append(',').append(csvField(l.getDescription()))
                    .append('\n');
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDisposition(ContentDisposition.attachment().filename("audit-log-export.csv").build());
        return new ResponseEntity<>(csv.toString(), headers, HttpStatus.OK);
    }

    private String csvField(String v) {
        if (v == null)
            return "";
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

}
