
package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.service.LoanApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/loans/{loanId}/approval-chain")
@RequiredArgsConstructor
public class LoanApprovalController {

    private final LoanApprovalService approvalService;

    /**
     * Get the approval chain for a loan.
     */
    @GetMapping
    @PreAuthorize("""
            hasAnyRole(
                'ADMIN',
                'MANAGER',
                'LOAN_OFFICER',
                'CREDIT_ANALYST',
                'BUSINESS_OWNER'
            )
            """)
    public ResponseEntity<?> getChain(
            @PathVariable Long loanId) {

        return ResponseEntity.ok(
                approvalService.getApprovalChain(loanId)
        );
    }

    /**
     * Generic approval-chain decision endpoint.
     *
     * Kept as Map<String, String> for backward compatibility with
     * existing clients using the original decide endpoint.
     */
    @PostMapping("/decide")
    @PreAuthorize("""
            hasAnyRole(
                'ADMIN',
                'MANAGER',
                'LOAN_OFFICER',
                'BUSINESS_OWNER'
            )
            """)
    public ResponseEntity<?> decide(
            @PathVariable Long loanId,
            @RequestBody Map<String, String> body,
            User user) {

        String decision = firstNonBlank(
                body.get("decision")
        );

        String comments = firstNonBlank(
                body.get("comments"),
                body.get("notes")
        );

        return ResponseEntity.ok(
                approvalService.decide(
                        loanId,
                        user,
                        decision,
                        comments
                )
        );
    }

    /**
     * Final approval endpoint.
     *
     * The request intentionally uses Map<String, Object> because the
     * frontend may send numeric JSON values as Number and
     * businessOwnerOnly as Boolean.
     */
    @PostMapping("/approve")
    @PreAuthorize("""
            hasAnyRole(
                'ADMIN',
                'MANAGER',
                'LOAN_OFFICER',
                'BUSINESS_OWNER'
            )
            """)
    public ResponseEntity<?> approve(
            @PathVariable Long loanId,
            @RequestBody Map<String, Object> body,
            User user) {

        String comments = firstNonBlank(
                body.get("comments"),
                body.get("notes")
        );

        Double newInterestRate = parseInterestRate(body);

        Double newProcessingFeeRate = parseProcessingFeeRate(body);

        BigDecimal approvedAmount = parseApprovedAmount(body);

        Boolean businessOwnerOnly = parseBusinessOwnerOnly(body);

        return ResponseEntity.ok(
                approvalService.decide(
                        loanId,
                        user,
                        "APPROVED",
                        comments,
                        newInterestRate,
                        newProcessingFeeRate,
                        approvedAmount,
                        businessOwnerOnly
                )
        );
    }

    /**
     * Reject endpoint.
     *
     * Kept as Map<String, String> for compatibility with existing
     * clients using the original reject request structure.
     */
    @PostMapping("/reject")
    @PreAuthorize("""
            hasAnyRole(
                'ADMIN',
                'MANAGER',
                'LOAN_OFFICER',
                'BUSINESS_OWNER'
            )
            """)
    public ResponseEntity<?> reject(
            @PathVariable Long loanId,
            @RequestBody Map<String, String> body,
            User user) {

        String comments = firstNonBlank(
                body.get("comments"),
                body.get("notes")
        );

        return ResponseEntity.ok(
                approvalService.decide(
                        loanId,
                        user,
                        "REJECTED",
                        comments
                )
        );
    }

    /**
     * Parse approved amount from the approval request.
     *
     * Preferred frontend field:
     *     approvedAmount
     *
     * Backward-compatible alias:
     *     amount
     */
    private BigDecimal parseApprovedAmount(
            Map<String, Object> body) {

        Object value = firstPresent(
                body,
                "approvedAmount",
                "amount"
        );

        if (value == null) {
            return null;
        }

        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }

        if (value instanceof Number) {
            return BigDecimal.valueOf(
                    ((Number) value).doubleValue()
            );
        }

        String text = String.valueOf(value).trim();

        if (text.isEmpty()) {
            return null;
        }

        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "approvedAmount must be a valid numeric value"
            );
        }
    }

    /**
     * Parse interest rate from JSON.
     *
     * Accepted field names:
     *     interestRate
     *     newInterestRate
     */
    private Double parseInterestRate(
            Map<String, Object> body) {

        Object value = firstPresent(
                body,
                "interestRate",
                "newInterestRate"
        );

        return parsePercentage(
                value,
                "interestRate"
        );
    }

    /**
     * Parse processing/application fee rate from JSON.
     *
     * Accepted field names:
     *     applicationFeeRate
     *     processingFeeRate
     *     newProcessingFeeRate
     */
    private Double parseProcessingFeeRate(
            Map<String, Object> body) {

        Object value = firstPresent(
                body,
                "applicationFeeRate",
                "processingFeeRate",
                "newProcessingFeeRate"
        );

        return parsePercentage(
                value,
                "applicationFeeRate"
        );
    }

    /**
     * Parse the Business Owner reporting classification.
     *
     * The frontend normally sends:
     *
     *     businessOwnerOnly: true
     *
     * This method also accepts string values for backward compatibility.
     */
    private Boolean parseBusinessOwnerOnly(
            Map<String, Object> body) {

        Object value = firstPresent(
                body,
                "businessOwnerOnly"
        );

        if (value == null) {
            return null;
        }

        if (value instanceof Boolean) {
            return (Boolean) value;
        }

        if (value instanceof Number) {
            int numericValue = ((Number) value).intValue();

            if (numericValue == 1) {
                return Boolean.TRUE;
            }

            if (numericValue == 0) {
                return Boolean.FALSE;
            }
        }

        String text = String.valueOf(value)
                .trim()
                .toUpperCase();

        if (text.isEmpty()) {
            return null;
        }

        switch (text) {
            case "TRUE":
            case "YES":
            case "Y":
            case "1":
            case "BUSINESS_OWNER":
            case "BUSINESS_OWNER_ONLY":
            case "OWNER_ONLY":
            case "BUSINESS_OWNER_SCOPE":
                return Boolean.TRUE;

            case "FALSE":
            case "NO":
            case "N":
            case "0":
            case "NORMAL":
            case "NORMAL_SCOPE":
                return Boolean.FALSE;

            default:
                throw new IllegalArgumentException(
                        "businessOwnerOnly must be a boolean value"
                );
        }
    }

    /**
     * Parse a percentage/rate value.
     *
     * Valid range:
     *     0 <= value <= 100
     *
     * Null means that the approval request did not provide
     * a new value, allowing the service to retain the existing value.
     */
    private Double parsePercentage(
            Object value,
            String fieldName) {

        if (value == null) {
            return null;
        }

        Double result;

        if (value instanceof Number) {
            result = ((Number) value).doubleValue();
        } else {
            String text = String.valueOf(value).trim();

            if (text.isEmpty()) {
                return null;
            }

            try {
                result = Double.valueOf(text);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        fieldName + " must be a valid numeric value"
                );
            }
        }

        if (result.isNaN()
                || result.isInfinite()
                || result < 0.0
                || result > 100.0) {

            throw new IllegalArgumentException(
                    fieldName + " must be between 0 and 100"
            );
        }

        return result;
    }

    /**
     * Returns the first non-null value from the supplied keys.
     */
    private Object firstPresent(
            Map<String, Object> body,
            String... keys) {

        if (body == null || keys == null) {
            return null;
        }

        for (String key : keys) {
            if (key == null) {
                continue;
            }

            Object value = body.get(key);

            if (value != null) {
                return value;
            }
        }

        return null;
    }

    /**
     * Returns the first non-blank textual value.
     *
     * Object is intentionally used here because /approve receives
     * Map<String, Object>.
     */
    private String firstNonBlank(
            Object... values) {

        if (values == null) {
            return null;
        }

        for (Object value : values) {

            if (value == null) {
                continue;
            }

            String text = String.valueOf(value).trim();

            if (!text.isEmpty()) {
                return text;
            }
        }

        return null;
    }
}
