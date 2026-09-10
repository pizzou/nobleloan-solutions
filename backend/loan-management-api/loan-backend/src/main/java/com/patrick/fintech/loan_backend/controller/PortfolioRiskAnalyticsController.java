package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.dto.PortfolioRiskAnalyticsResponse;
import com.patrick.fintech.loan_backend.service.PortfolioRiskAnalyticsService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics/portfolio-risk")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','ACCOUNTANT','CREDIT_ANALYST','COLLECTIONS_OFFICER')")
public class PortfolioRiskAnalyticsController {

    private final PortfolioRiskAnalyticsService portfolioRiskAnalyticsService;
    private final CurrentUserUtil currentUserUtil;

    /**
     * Returns cumulative PAR1/PAR7/PAR30/PAR60/PAR90 for the current
     * organization portfolio.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PortfolioRiskAnalyticsResponse>> current() {
        Long organizationId = currentUserUtil.getCurrentOrganizationId();

        return ResponseEntity.ok(
                ApiResponse.ok(
                        portfolioRiskAnalyticsService.getCurrentPortfolioRisk(organizationId)));
    }
}
