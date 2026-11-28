package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.service.PaymentReviewService;
import com.patrick.fintech.loan_backend.service.PaymentReviewService.Review;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payment-reviews")
@RequiredArgsConstructor
public class PaymentReviewController {
    private final PaymentReviewService reviews;
    private final CurrentUserUtil current;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','ACCOUNTANT','TELLER','LOAN_OFFICER','BUSINESS_OWNER')")
    public ApiResponse<List<Review>> list(@RequestParam(defaultValue = "false") boolean pendingOnly,
                                         @RequestParam(defaultValue = "0") int page) {
        return ApiResponse.ok(reviews.list(current.getCurrentUser(), pendingOnly, page));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ApiResponse<Review> approve(@PathVariable Long id) {
        return ApiResponse.ok(reviews.decide(id, current.getCurrentUser(), true, null));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ApiResponse<Review> reject(@PathVariable Long id, @RequestBody Rejection body) {
        return ApiResponse.ok(reviews.decide(id, current.getCurrentUser(), false, body.reason()));
    }

    public record Rejection(String reason) {}
}
