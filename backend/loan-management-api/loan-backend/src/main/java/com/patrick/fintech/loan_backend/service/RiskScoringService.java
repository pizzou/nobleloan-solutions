package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.CreditBureauCheck;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.repository.CreditBureauCheckRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class RiskScoringService {

        private final LoanRepository loanRepo;
        private final CreditBureauCheckRepository creditBureauCheckRepo;

        private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        private static final BigDecimal FIFTY_PERCENT = new BigDecimal("0.50");

        private static final BigDecimal DTI_20 = new BigDecimal("20.00");

        private static final BigDecimal DTI_30 = new BigDecimal("30.00");

        private static final BigDecimal DTI_40 = new BigDecimal("40.00");

        private static final BigDecimal DTI_50 = new BigDecimal("50.00");

        /**
         * Calculate a risk score from 0 to 100.
         *
         * Higher score = lower risk.
         *
         * Risk factors:
         *
         * 1. Credit score
         * 2. Debt-to-income ratio
         * 3. Employment type
         * 4. Collateral
         * 5. Loan type
         * 6. Existing active/overdue loans
         * 7. Credit bureau history
         */
        @Transactional(readOnly = true)
        public RiskResult score(Loan loan) {

                if (loan == null) {
                        throw new IllegalArgumentException(
                                        "Loan is required for risk scoring");
                }

                Borrower borrower = loan.getBorrower();

                if (borrower == null) {
                        throw new IllegalArgumentException(
                                        "Loan borrower is required for risk scoring");
                }

                if (loan.getOrganization() == null
                                || loan.getOrganization().getId() == null) {

                        throw new IllegalArgumentException(
                                        "Loan organization is required for risk scoring");
                }

                double score = 100.0;

                // ============================================================
                // 1. CREDIT SCORE
                // ============================================================

                Integer borrowerCreditScore = borrower.getCreditScore();

                if (borrowerCreditScore != null) {

                        int creditScore = borrowerCreditScore;

                        if (creditScore >= 800) {

                                score -= 0;

                        } else if (creditScore >= 750) {

                                score -= 5;

                        } else if (creditScore >= 700) {

                                score -= 12;

                        } else if (creditScore >= 650) {

                                score -= 20;

                        } else if (creditScore >= 600) {

                                score -= 28;

                        } else {

                                score -= 35;
                        }

                } else {

                        /*
                         * No credit history available.
                         */
                        score -= 20;
                }

                BigDecimal dti = loan.getDebtToIncomeRatio() != null
                                ? loan.getDebtToIncomeRatio()
                                : ZERO;

                dti = dti.setScale(
                                2,
                                RoundingMode.HALF_UP);

                if (dti.compareTo(DTI_20) < 0) {

                        score -= 0;

                } else if (dti.compareTo(DTI_30) < 0) {

                        score -= 5;

                } else if (dti.compareTo(DTI_40) < 0) {

                        score -= 12;

                } else if (dti.compareTo(DTI_50) < 0) {

                        score -= 20;

                } else {

                        score -= 28;
                }

                // ============================================================
                // 3. EMPLOYMENT TYPE
                // ============================================================

                if (borrower.getEmploymentType() != null) {

                        String employmentType = borrower.getEmploymentType()
                                        .trim()
                                        .toUpperCase(Locale.ROOT);

                        switch (employmentType) {

                                case "PERMANENT" -> {
                                        // No deduction.
                                }

                                case "CONTRACT" -> {
                                        score -= 5;
                                }

                                case "SELF_EMPLOYED" -> {
                                        score -= 8;
                                }

                                case "UNEMPLOYED" -> {
                                        score -= 18;
                                }

                                default -> {
                                        score -= 5;
                                }
                        }
                }

                // ============================================================
                // 4. COLLATERAL
                // ============================================================

                BigDecimal collateralValue = loan.getCollateralValue() != null
                                ? loan.getCollateralValue()
                                : ZERO;

                collateralValue = collateralValue.setScale(
                                2,
                                RoundingMode.HALF_UP);

                BigDecimal loanAmount = loan.getAmountDecimal() != null
                                ? loan.getAmountDecimal()
                                : ZERO;

                loanAmount = loanAmount.setScale(
                                2,
                                RoundingMode.HALF_UP);

                if (collateralValue.compareTo(ZERO) <= 0) {

                        /*
                         * No collateral.
                         */
                        score -= 12;

                } else if (loanAmount.compareTo(ZERO) > 0) {

                        BigDecimal minimumCollateralCoverage = loanAmount.multiply(
                                        FIFTY_PERCENT);

                        if (collateralValue.compareTo(
                                        minimumCollateralCoverage) < 0) {

                                score -= 6;
                        }
                }

                // ============================================================
                // 5. LOAN TYPE
                // ============================================================

                if (loan.getLoanType() != null) {

                        switch (loan.getLoanType()) {

                                case MORTGAGE,
                                                AGRICULTURAL -> {
                                        // No deduction.
                                }

                                case AUTO,
                                                ASSET_FINANCE -> {
                                        score -= 2;
                                }

                                case BUSINESS -> {
                                        score -= 5;
                                }

                                case PERSONAL -> {
                                        score -= 8;
                                }

                                case EMERGENCY -> {
                                        score -= 12;
                                }

                                case MICROFINANCE -> {
                                        score -= 10;
                                }

                                default -> {
                                        score -= 5;
                                }
                        }
                }

                // ============================================================
                // 6. EXISTING ACTIVE / OVERDUE LOANS
                // ============================================================

                List<Loan> borrowerLoans = loanRepo.findByBorrowerIdAndOrganizationId(
                                borrower.getId(),
                                loan.getOrganization().getId());

                if (borrowerLoans == null) {
                        borrowerLoans = List.of();
                }

                long existingActiveLoans = borrowerLoans.stream()
                                .filter(
                                                existingLoan -> existingLoan != null)
                                .filter(
                                                existingLoan -> existingLoan.getId() == null
                                                                || loan.getId() == null
                                                                || !existingLoan
                                                                                .getId()
                                                                                .equals(
                                                                                                loan.getId()))
                                .filter(
                                                existingLoan -> {

                                                        LoanStatus status = existingLoan.getStatus();

                                                        return status == LoanStatus.ACTIVE
                                                                        || status == LoanStatus.OVERDUE;
                                                })
                                .count();

                score -= existingActiveLoans * 8.0;

                // ============================================================
                // 7. CREDIT BUREAU HISTORY
                // ============================================================

                try {

                        if (borrower.getId() != null) {

                                var bureauCheck = creditBureauCheckRepo
                                                .findFirstByBorrower_IdOrderByCreatedAtDesc(
                                                                borrower.getId())
                                                .filter(
                                                                check -> check != null
                                                                                && !check.isExpired())
                                                .filter(
                                                                check -> check.getStatus() == CreditBureauCheck.CheckStatus.COMPLETED);

                                if (bureauCheck.isPresent()) {

                                        CreditBureauCheck creditBureau = bureauCheck.get();

                                        if (Boolean.TRUE.equals(
                                                        creditBureau.getHasDefaultHistory())) {

                                                score -= 12;
                                        }

                                        if (Boolean.TRUE.equals(
                                                        creditBureau.getHasActiveListing())) {

                                                score -= 15;
                                        }

                                        Integer delinquentAccounts = creditBureau.getDelinquentAccounts();

                                        if (delinquentAccounts != null
                                                        && delinquentAccounts > 0) {

                                                double delinquentPenalty = Math.min(
                                                                delinquentAccounts * 4.0,
                                                                12.0);

                                                score -= delinquentPenalty;
                                        }
                                }
                        }

                } catch (Exception e) {

                        /*
                         * Credit bureau information is supplementary.
                         * A bureau lookup failure must not prevent risk scoring.
                         */
                        log.warn(
                                        "Credit bureau history could not be evaluated for borrower {}: {}",
                                        borrower.getId(),
                                        e.getMessage());
                }

                // ============================================================
                // FINAL SCORE NORMALIZATION
                // ============================================================

                score = Math.max(
                                0.0,
                                Math.min(
                                                100.0,
                                                score));

                score = round(
                                score);

                // ============================================================
                // RISK CATEGORY
                // ============================================================

                String category;

                if (score >= 80.0) {

                        category = "LOW";

                } else if (score >= 60.0) {

                        category = "MEDIUM";

                } else if (score >= 40.0) {

                        category = "HIGH";

                } else {

                        category = "CRITICAL";
                }

                log.info(
                                "Risk score calculated. loanId={}, borrowerId={}, " +
                                                "dti={}, collateral={}, existingActiveLoans={}, " +
                                                "score={}, category={}",
                                loan.getId(),
                                borrower.getId(),
                                dti,
                                collateralValue,
                                existingActiveLoans,
                                score,
                                category);

                return new RiskResult(
                                score,
                                category);
        }

        // ============================================================
        // ROUND SCORE
        // ============================================================

        private double round(
                        double value) {

                return BigDecimal.valueOf(
                                value)
                                .setScale(
                                                1,
                                                RoundingMode.HALF_UP)
                                .doubleValue();
        }

        // ============================================================
        // RESULT
        // ============================================================

        public record RiskResult(
                        double score,
                        String category) {

                public double getScore() {
                        return score;
                }

                public String getCategory() {
                        return category;
                }
        }
}