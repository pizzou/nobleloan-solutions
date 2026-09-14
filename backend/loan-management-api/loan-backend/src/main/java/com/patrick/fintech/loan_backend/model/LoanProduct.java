package com.patrick.fintech.loan_backend.model;

import com.patrick.fintech.loan_backend.util.FinancialPolicy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@JsonIgnoreProperties({
                "hibernateLazyInitializer",
                "handler"
})
@Entity
@Table(name = "loan_products", indexes = {
                @Index(name = "idx_lp_org", columnList = "organization_id"),
                @Index(name = "idx_lp_org_loan_type", columnList = "organization_id, loan_type")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanProduct {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @JsonIgnore
        @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
        @JoinColumn(name = "organization_id", nullable = false)
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        private Organization organization;

        @Column(nullable = false, length = 150)
        private String name;

        @Column(length = 20)
        private String icon;

        @Column(columnDefinition = "TEXT")
        private String description;

        @Enumerated(EnumType.STRING)
        @Column(name = "loan_type", nullable = false, length = 50)
        private Loan.LoanType loanType;

        /**
         * Interest rate expressed as a percentage.
         *
         * Example:
         *
         * 5.00 = 5% per month
         */
        @Column(nullable = false, precision = 19, scale = 9)
        @JsonProperty("interestRate")
        private BigDecimal interestRate;

        /**
         * MONTHLY is the supported production rate type for
         * the current loan-product configuration.
         */
        @Builder.Default
        @Column(nullable = false, length = 20)
        private String interestRateType = "MONTHLY";

        @Column(nullable = false, precision = 19, scale = 6)
        @JsonProperty("minAmount")
        private BigDecimal minAmount;

        @Column(precision = 19, scale = 6)
        @JsonProperty("maxAmount")
        private BigDecimal maxAmount;

        @Column(nullable = false)
        private Integer minTermMonths;

        @Column(nullable = false)
        private Integer maxTermMonths;

        @Column(nullable = false, precision = 19, scale = 9)
        @JsonProperty("applicationFeePercent")
        private BigDecimal applicationFeePercent;

        @Column(nullable = false, precision = 19, scale = 9)
        @JsonProperty("managementFeePercent")
        private BigDecimal managementFeePercent;

        @Column(nullable = false, precision = 19, scale = 9)
        @JsonProperty("penaltyPercent")
        private BigDecimal penaltyPercent;

        @Builder.Default
        @Column(nullable = false)
        private Boolean active = true;

        private Integer displayOrder;

        /**
         * Comma-separated required document type codes.
         */
        @Column(columnDefinition = "TEXT")
        private String requiredDocumentTypes;

        @Column(nullable = false, updatable = false)
        private LocalDateTime createdAt;

        @Column(nullable = false)
        private LocalDateTime updatedAt;

        /*
         * ============================================================
         * DEFAULT BUSINESS VALUES
         * ============================================================
         */

        public static final BigDecimal DEFAULT_MIN_AMOUNT = new BigDecimal("500000.00");

        public static final BigDecimal DEFAULT_INTEREST_RATE = FinancialPolicy.MONTHLY_INTEREST_RATE;

        public static final BigDecimal DEFAULT_APPLICATION_FEE_PERCENT = FinancialPolicy.APPLICATION_FEE_RATE;

        public static final BigDecimal DEFAULT_MANAGEMENT_FEE_PERCENT = FinancialPolicy.MONTHLY_MANAGEMENT_FEE_RATE;

        public static final BigDecimal DEFAULT_PENALTY_PERCENT = FinancialPolicy.MONTHLY_PENALTY_RATE;

        public static final String DEFAULT_INTEREST_RATE_TYPE = "MONTHLY";

        public static final int DEFAULT_MIN_TERM_MONTHS = 1;

        public static final int DEFAULT_MAX_TERM_MONTHS = 6;

        /*
         * ============================================================
         * JPA LIFECYCLE
         * ============================================================
         */

        @PrePersist
        protected void onCreate() {

                LocalDateTime now = LocalDateTime.now();

                if (createdAt == null) {
                        createdAt = now;
                }

                updatedAt = now;

                applyDefaults();

                validateBusinessRules();
        }

        @PreUpdate
        protected void onUpdate() {

                updatedAt = LocalDateTime.now();

                applyDefaults();

                validateBusinessRules();
        }

        /*
         * ============================================================
         * DEFAULTS
         * ============================================================
         */

        private void applyDefaults() {

                if (active == null) {
                        active = true;
                }

                if (interestRate == null) {
                        interestRate = DEFAULT_INTEREST_RATE;
                }

                if (interestRateType == null
                                || interestRateType.isBlank()) {

                        interestRateType = DEFAULT_INTEREST_RATE_TYPE;
                }

                if (minAmount == null) {
                        minAmount = DEFAULT_MIN_AMOUNT;
                }

                /*
                 * maxAmount intentionally remains null.
                 * Null means unlimited.
                 */

                if (minTermMonths == null) {
                        minTermMonths = DEFAULT_MIN_TERM_MONTHS;
                }

                if (maxTermMonths == null) {
                        maxTermMonths = DEFAULT_MAX_TERM_MONTHS;
                }

                if (applicationFeePercent == null) {
                        applicationFeePercent = DEFAULT_APPLICATION_FEE_PERCENT;
                }

                if (managementFeePercent == null) {
                        managementFeePercent = DEFAULT_MANAGEMENT_FEE_PERCENT;
                }

                if (penaltyPercent == null) {
                        penaltyPercent = DEFAULT_PENALTY_PERCENT;
                }
        }

        /*
         * ============================================================
         * BUSINESS VALIDATION
         * ============================================================
         */

        private void validateBusinessRules() {

                if (interestRate == null
                                || interestRate.signum() < 0) {

                        throw new IllegalStateException(
                                        "Loan product interest rate cannot be negative.");
                }

                if (minAmount == null
                                || minAmount.compareTo(BigDecimal.ZERO) <= 0) {

                        throw new IllegalStateException(
                                        "Loan product minimum amount must be greater than zero.");
                }

                if (minAmount.compareTo(DEFAULT_MIN_AMOUNT) < 0) {

                        throw new IllegalStateException(
                                        "Loan product minimum amount cannot be below RWF 500,000.");
                }

                if (maxAmount != null
                                && maxAmount.compareTo(minAmount) < 0) {

                        throw new IllegalStateException(
                                        "Loan product maximum amount cannot be below minimum amount.");
                }

                if (minTermMonths == null
                                || minTermMonths < 1) {

                        throw new IllegalStateException(
                                        "Loan product minimum term must be at least 1 month.");
                }

                if (maxTermMonths == null
                                || maxTermMonths < minTermMonths) {

                        throw new IllegalStateException(
                                        "Loan product maximum term must be greater than or equal to minimum term.");
                }

                if (maxTermMonths > DEFAULT_MAX_TERM_MONTHS) {

                        throw new IllegalStateException(
                                        "Loan product maximum term cannot exceed 6 months.");
                }

                if (applicationFeePercent == null
                                || applicationFeePercent.signum() < 0) {

                        throw new IllegalStateException(
                                        "Processing fee cannot be negative.");
                }

                if (managementFeePercent == null
                                || managementFeePercent.signum() < 0) {

                        throw new IllegalStateException(
                                        "Management fee cannot be negative.");
                }

                if (penaltyPercent == null
                                || penaltyPercent.signum() < 0) {

                        throw new IllegalStateException(
                                        "Penalty percentage cannot be negative.");
                }

                if (!DEFAULT_INTEREST_RATE_TYPE.equalsIgnoreCase(
                                interestRateType)) {

                        throw new IllegalStateException(
                                        "Unsupported loan product interest rate type: "
                                                        + interestRateType);
                }
        }

        /*
         * ============================================================
         * REQUIRED DOCUMENTS
         * ============================================================
         */

        @JsonIgnore
        public List<String> getRequiredDocumentTypesList() {

                if (requiredDocumentTypes == null
                                || requiredDocumentTypes.isBlank()) {

                        return null;
                }

                return Arrays.stream(
                                requiredDocumentTypes.split(","))
                                .map(String::trim)
                                .filter(value -> !value.isEmpty())
                                .toList();
        }

        /*
         * ============================================================
         * SAFE FINANCIAL ACCESSORS
         * ============================================================
         */

        @JsonIgnore
        public BigDecimal getInterestRateDecimal() {
                return interestRate;
        }

        @JsonIgnore
        public BigDecimal getMinAmountDecimal() {
                return minAmount;
        }

        @JsonIgnore
        public BigDecimal getMaxAmountDecimal() {
                return maxAmount;
        }

        @JsonIgnore
        public BigDecimal getApplicationFeePercentDecimal() {
                return applicationFeePercent;
        }

        @JsonIgnore
        public BigDecimal getManagementFeePercentDecimal() {
                return managementFeePercent;
        }

        @JsonIgnore
        public BigDecimal getPenaltyPercentDecimal() {
                return penaltyPercent;
        }

        /*
         * ============================================================
         * SINGLE, UNAMBIGUOUS BIGDECIMAL SETTERS
         * ============================================================
         *
         * IMPORTANT:
         *
         * There are intentionally NO Double setters.
         *
         * This prevents calls such as:
         *
         * setMaxAmount(...)
         *
         * from becoming ambiguous when both Double and BigDecimal
         * overloads exist.
         */

        public void setInterestRate(
                        BigDecimal value) {

                this.interestRate = value;
        }

        public void setMinAmount(
                        BigDecimal value) {

                this.minAmount = value;
        }

        public void setMaxAmount(
                        BigDecimal value) {

                this.maxAmount = value;
        }

        public void setProcessingFeePercent(
                        BigDecimal value) {

                this.applicationFeePercent = value;
        }

        public void setManagementFeePercent(
                        BigDecimal value) {

                this.managementFeePercent = value;
        }

        public void setPenaltyPercent(
                        BigDecimal value) {

                this.penaltyPercent = value;
        }

        /*
         * ============================================================
         * AMOUNT VALIDATION
         * ============================================================
         */

        @JsonIgnore
        public boolean supportsAmount(
                        BigDecimal amount) {

                if (amount == null
                                || amount.signum() <= 0) {

                        return false;
                }

                if (amount.compareTo(minAmount) < 0) {
                        return false;
                }

                /*
                 * Null maxAmount = unlimited.
                 */
                return maxAmount == null
                                || amount.compareTo(maxAmount) <= 0;
        }

        /*
         * ============================================================
         * TERM VALIDATION
         * ============================================================
         */

        @JsonIgnore
        public boolean supportsTerm(
                        Integer termMonths) {

                if (termMonths == null) {
                        return false;
                }

                return termMonths >= minTermMonths
                                && termMonths <= maxTermMonths;
        }
}