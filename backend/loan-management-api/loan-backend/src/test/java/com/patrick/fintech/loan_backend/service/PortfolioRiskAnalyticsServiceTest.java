package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.PortfolioRiskAnalyticsResponse;
import com.patrick.fintech.loan_backend.repository.LoanRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioRiskAnalyticsServiceTest {

    @Mock
    private LoanRepository loanRepository;

    @InjectMocks
    private PortfolioRiskAnalyticsService service;

    @Test
    void calculatesCumulativeParPercentagesAndAgeing() {
        when(loanRepository.calculateVisiblePortfolioRiskMetrics(1L, false)).thenReturn(new Object[] {
                10L,
                new BigDecimal("1000000.00"),
                4L,
                new BigDecimal("400000.00"),
                3L,
                new BigDecimal("300000.00"),
                2L,
                new BigDecimal("200000.00"),
                1L,
                new BigDecimal("100000.00"),
                1L,
                new BigDecimal("50000.00")
        });

        PortfolioRiskAnalyticsResponse result = service.getCurrentPortfolioRisk(1L);

        assertEquals(10L, result.getCurrentPortfolioLoans());
        assertEquals(new BigDecimal("1000000.00"), result.getCurrentOutstandingPrincipal());
        assertEquals(new BigDecimal("40.00"), result.getPar1Pct());
        assertEquals(new BigDecimal("30.00"), result.getPar7Pct());
        assertEquals(new BigDecimal("20.00"), result.getPar30Pct());
        assertEquals(new BigDecimal("10.00"), result.getPar60Pct());
        assertEquals(new BigDecimal("5.00"), result.getPar90Pct());

        assertEquals(6, result.getAgeingBuckets().size());
        assertEquals(6L, result.getAgeingBuckets().get(0).getLoanCount());
        assertEquals(new BigDecimal("600000.00"), result.getAgeingBuckets().get(0).getOutstandingPrincipal());
        assertEquals(1L, result.getAgeingBuckets().get(5).getLoanCount());
        assertEquals(new BigDecimal("50000.00"), result.getAgeingBuckets().get(5).getOutstandingPrincipal());
        assertTrue(result.hasOutstandingPortfolio());
    }

    @Test
    void returnsZeroPercentagesForEmptyOutstandingPortfolio() {
        when(loanRepository.calculateVisiblePortfolioRiskMetrics(1L, false)).thenReturn(new Object[] {
                0L,
                BigDecimal.ZERO,
                0L, BigDecimal.ZERO,
                0L, BigDecimal.ZERO,
                0L, BigDecimal.ZERO,
                0L, BigDecimal.ZERO,
                0L, BigDecimal.ZERO
        });

        PortfolioRiskAnalyticsResponse result = service.getCurrentPortfolioRisk(1L);

        assertEquals(BigDecimal.ZERO.setScale(2), result.getPar1Pct());
        assertEquals(BigDecimal.ZERO.setScale(2), result.getPar90Pct());
        assertTrue(!result.hasOutstandingPortfolio());
    }
}
