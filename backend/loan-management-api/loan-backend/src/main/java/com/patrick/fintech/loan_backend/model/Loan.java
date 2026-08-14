package com.patrick.fintech.loan_backend.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.patrick.fintech.loan_backend.util.MoneyMath;

@JsonIgnoreProperties({
                "hibernateLazyInitializer",
                "handler"
})
@Entity
@Table(name = "loans", indexes = {
                @Index(name = "idx_loans_org", columnList = "organization_id"),
                @Index(name = "idx_loans_borrower", columnList = "borrower_id"),
                @Index(name = "idx_loans_status", columnList = "status"),
                @Index(name = "idx_loans_credit_quality", columnList = "credit_quality"),
                @Index(name = "idx_loans_arrears_status", columnList = "arrears_status"),
                @Index(name = "idx_loans_collections_stage", columnList = "collections_stage"),
                @Index(name = "idx_loans_days_overdue", columnList = "days_overdue")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Loan {

        // ================================================================
        // PLATFORM FINANCIAL RULES
        // ================================================================

        public static final BigDecimal MIN_LOAN_AMOUNT = new BigDecimal("500000.00");

        /**
         * There is deliberately no maximum loan amount.
         */
        public static final BigDecimal MAX_LOAN_AMOUNT = null;

        /**
         * Maximum loan duration.
         *
         * All loans must be between 1 and 6 months.
         */
        public static final int MAX_LOAN_DURATION_MONTHS = 6;

        /**
         * Minimum loan duration.
         */
        public static final int MIN_LOAN_DURATION_MONTHS = 1;

        /**
         * Unified monthly loan interest rate.
         *
         * ALL loan types use 5% monthly interest.
         */
        public static final BigDecimal DEFAULT_MONTHLY_INTEREST_RATE = new BigDecimal("5.00");

        /**
         * Unified monthly loan management fee rate.
         *
         * ALL loan types use 5% monthly management fee.
         */
        public static final BigDecimal DEFAULT_MONTHLY_MANAGEMENT_FEE_RATE = new BigDecimal("5.00");

        /**
         * Unified one-time processing fee rate.
         *
         * ALL loan types use 2%.
         */
        public static final BigDecimal DEFAULT_PROCESSING_FEE_RATE = new BigDecimal("2.00");

        /**
         * Total recurring monthly charge:
         *
         * 5% interest + 5% management fee = 10%.
         */
        public static final BigDecimal DEFAULT_TOTAL_MONTHLY_CHARGE_RATE = new BigDecimal("10.00");

        // ================================================================
        // CREDIT CLASSIFICATION THRESHOLDS
        // ================================================================

        /**
         * NORMAL:
         *
         * 0 days overdue.
         */
        public static final int NORMAL_MAX_DAYS_OVERDUE = 0;

        /**
         * WATCH:
         *
         * 1 - 89 days overdue.
         */
        public static final int WATCH_MIN_DAYS_OVERDUE = 1;
        public static final int WATCH_MAX_DAYS_OVERDUE = 89;

        /**
         * SUBSTANDARD:
         *
         * 90 - 179 days overdue.
         */
        public static final int SUBSTANDARD_MIN_DAYS_OVERDUE = 90;
        public static final int SUBSTANDARD_MAX_DAYS_OVERDUE = 179;

        /**
         * DOUBTFUL:
         *
         * 180 - 359 days overdue.
         */
        public static final int DOUBTFUL_MIN_DAYS_OVERDUE = 180;
        public static final int DOUBTFUL_MAX_DAYS_OVERDUE = 359;

        /**
         * WRITTEN_OFF:
         *
         * 360 - 719 days overdue according to the requested
         * classification range.
         *
         * For production safety, anything beyond 719 days is also
         * kept as WRITTEN_OFF rather than leaving the loan unclassified.
         */
        public static final int WRITTEN_OFF_MIN_DAYS_OVERDUE = 360;
        public static final int WRITTEN_OFF_MAX_DAYS_OVERDUE = 719;

        // ================================================================
        // ID
        // ================================================================

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        // ================================================================
        // REFERENCE
        // ================================================================

        @Column(unique = true, nullable = false, length = 100)
        private String referenceNumber;

        // ================================================================
        // ORGANIZATION / TENANCY
        // ================================================================

        @JsonIgnore
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "organization_id", nullable = false, foreignKey = @ForeignKey(name = "fk_loan_organization"))
        private Organization organization;

        @ManyToOne(fetch = FetchType.EAGER)
        @JoinColumn(name = "branch_id", foreignKey = @ForeignKey(name = "fk_loan_branch"))
        private Branch branch;

        // ================================================================
        // BORROWER / USERS
        // ================================================================

        @ManyToOne(fetch = FetchType.EAGER, optional = false)
        @JoinColumn(name = "borrower_id", nullable = false, foreignKey = @ForeignKey(name = "fk_loan_borrower"))
        private Borrower borrower;

        @ManyToOne(fetch = FetchType.EAGER)
        @JoinColumn(name = "created_by", foreignKey = @ForeignKey(name = "fk_loan_created_by"))
        private User createdBy;

        @ManyToOne(fetch = FetchType.EAGER)
        @JoinColumn(name = "approved_by", foreignKey = @ForeignKey(name = "fk_loan_approved_by"))
        private User approvedBy;

        @ManyToOne(fetch = FetchType.EAGER)
        @JoinColumn(name = "loan_officer_id", foreignKey = @ForeignKey(name = "fk_loan_officer"))
        private User loanOfficer;

        // ================================================================
        // LOAN CLASSIFICATION
        // ================================================================

        @Enumerated(EnumType.STRING)
        @Column(name = "loan_type", nullable = false, length = 50)
        private LoanType loanType;

        @Enumerated(EnumType.STRING)
        @Column(name = "status", nullable = false, length = 50)
        @Builder.Default
        private LoanStatus status = LoanStatus.PENDING;

        /**
         * Credit quality is owned by Loan because it is a loan-level
         * financial/risk classification.
         *
         * Classification is based on daysOverdue:
         *
         * 0 = CURRENT
         * 1-89 = WATCH
         * 90-179 = SUBSTANDARD
         * 180-359 = DOUBTFUL
         * 360+ = WRITTEN_OFF
         */
        @Enumerated(EnumType.STRING)
        @Builder.Default
        @Column(name = "credit_quality", nullable = false, length = 20)
        private CreditQuality creditQuality = CreditQuality.CURRENT;

        /**
         * Simple arrears state.
         *
         * NOT_DUE = no overdue amount / no overdue days
         * PAST_DUE = loan is overdue
         */
        @Enumerated(EnumType.STRING)
        @Builder.Default
        @Column(name = "arrears_status", nullable = false, length = 20)
        private ArrearsStatus arrearsStatus = ArrearsStatus.NOT_DUE;

        /**
         * Loan-level collection stage.
         *
         * This describes the overall stage of the loan.
         *
         * CollectionCase remains responsible for operational
         * collection activities.
         */
        @Enumerated(EnumType.STRING)
        @Builder.Default
        @Column(name = "collections_stage", nullable = false, length = 20)
        private CollectionsStage collectionsStage = CollectionsStage.NORMAL;

        /**
         * Date/time when the current credit classification was calculated.
         */
        @Column(name = "classified_at")
        private LocalDateTime classifiedAt;

        @Enumerated(EnumType.STRING)
        @Builder.Default
        @Column(name = "repayment_frequency", nullable = false, length = 20)
        private RepaymentFrequency repaymentFrequency = RepaymentFrequency.MONTHLY;

        // ================================================================
        // LOAN AMOUNTS
        // ================================================================

        /**
         * Original approved loan principal.
         *
         * Interest is calculated from this amount.
         */
        @Column(name = "amount", precision = 19, scale = 2, nullable = false)
        @JsonProperty("amount")
        private BigDecimal amount;

        /**
         * Gross principal actually used for disbursement calculations.
         *
         * This remains the full loan principal before the one-time
         * processing fee deduction.
         */
        @Column(name = "disbursed_amount", precision = 19, scale = 2)
        @JsonProperty("disbursedAmount")
        private BigDecimal disbursedAmount;

        /**
         * Net cash actually received by the borrower after the
         * one-time 2% processing fee is deducted.
         */
        @Column(name = "net_disbursed_amount", precision = 19, scale = 2)
        @JsonProperty("netDisbursedAmount")
        private BigDecimal netDisbursedAmount;

        /**
         * Total contractual amount repayable over the loan term.
         */
        @Column(name = "total_repayable", precision = 19, scale = 2)
        @JsonProperty("totalRepayable")
        private BigDecimal totalRepayable;

        @Column(name = "total_paid", precision = 19, scale = 2)
        @Builder.Default
        @JsonProperty("totalPaid")
        private BigDecimal totalPaid = BigDecimal.ZERO;

        @Column(name = "principal_paid", precision = 19, scale = 2, nullable = false)
        @Builder.Default
        @JsonProperty("principalPaid")
        private BigDecimal principalPaid = BigDecimal.ZERO;

        /**
         * Current outstanding loan balance.
         *
         * Loan owns this because it is the current financial state
         * of the loan.
         */
        @Column(name = "outstanding_balance", precision = 19, scale = 2)
        @Builder.Default
        @JsonProperty("outstandingBalance")
        private BigDecimal outstandingBalance = BigDecimal.ZERO;

        @Column(name = "next_installment_amount", precision = 19, scale = 2)
        @JsonProperty("nextInstallmentAmount")
        private BigDecimal nextInstallmentAmount;

        // ================================================================
        // PAYMENT DATES
        // ================================================================

        @Column(name = "next_payment_date")
        private LocalDate nextPaymentDate;

        @Column(name = "next_due_date")
        private LocalDate nextDueDate;

        @Column(name = "last_payment_date")
        private LocalDate lastPaymentDate;

        @Column(name = "missed_installments")
        @Builder.Default
        private Integer missedInstallments = 0;

        /**
         * Number of calendar days the loan is overdue.
         *
         * This is the primary input to the credit-quality
         * classification.
         */
        @Column(name = "days_overdue")
        @Builder.Default
        private Integer daysOverdue = 0;

        // ================================================================
        // INTEREST
        // ================================================================

        /**
         * Monthly loan interest rate.
         *
         * ALL loan types = 5% monthly.
         */
        @Column(name = "interest_rate", precision = 19, scale = 9)
        @Builder.Default
        @JsonProperty("interestRate")
        private BigDecimal interestRate = DEFAULT_MONTHLY_INTEREST_RATE;

        /**
         * Interest is always monthly for this platform.
         */
        @Column(name = "interest_rate_type", length = 20, nullable = false)
        @Builder.Default
        private String interestRateType = "MONTHLY";

        @Column(name = "management_fee_rate", precision = 19, scale = 9)
        @Builder.Default
        @JsonProperty("managementFeeRate")
        private BigDecimal managementFeeRate = DEFAULT_MONTHLY_MANAGEMENT_FEE_RATE;

        /**
         * Total management fee scheduled over the loan term.
         */
        @Column(name = "management_fee", precision = 19, scale = 2)
        @Builder.Default
        @JsonProperty("managementFee")
        private BigDecimal managementFee = BigDecimal.ZERO;

        /**
         * Total management fee actually paid so far.
         */
        @Column(name = "management_fee_paid", precision = 19, scale = 2)
        @Builder.Default
        @JsonProperty("managementFeePaid")
        private BigDecimal managementFeePaid = BigDecimal.ZERO;

        @Column(name = "management_fee_outstanding", precision = 19, scale = 2, nullable = false)
        @Builder.Default
        @JsonProperty("managementFeeOutstanding")
        private BigDecimal managementFeeOutstanding = BigDecimal.ZERO;

        // ================================================================
        // INTEREST TOTALS
        // ================================================================

        /**
         * Total interest scheduled over the loan term.
         */
        @Column(name = "total_interest", precision = 19, scale = 2)
        @Builder.Default
        @JsonProperty("totalInterest")
        private BigDecimal totalInterest = BigDecimal.ZERO;

        /**
         * Total interest actually paid so far.
         */
        @Column(name = "interest_paid", precision = 19, scale = 2)
        @Builder.Default
        @JsonProperty("interestPaid")
        private BigDecimal interestPaid = BigDecimal.ZERO;

        @Column(name = "interest_outstanding", precision = 19, scale = 2, nullable = false)
        @Builder.Default
        @JsonProperty("interestOutstanding")
        private BigDecimal interestOutstanding = BigDecimal.ZERO;

        @Column(name = "penalties_assessed", precision = 19, scale = 2, nullable = false)
        @Builder.Default
        @JsonProperty("penaltiesAssessed")
        private BigDecimal penaltiesAssessed = BigDecimal.ZERO;

        @Column(name = "penalties_paid", precision = 19, scale = 2, nullable = false)
        @Builder.Default
        @JsonProperty("penaltiesPaid")
        private BigDecimal penaltiesPaid = BigDecimal.ZERO;

        @Column(name = "duration_months", nullable = false)
        private Integer durationMonths;

        // ================================================================
        // CURRENCY
        // ================================================================

        @Column(name = "currency", length = 3, nullable = false)
        @Builder.Default
        private String currency = "RWF";

        @Column(name = "processing_fee_rate", precision = 19, scale = 9)
        @Builder.Default
        @JsonProperty("processingFeeRate")
        private BigDecimal processingFeeRate = DEFAULT_PROCESSING_FEE_RATE;

        /**
         * One-time processing fee amount.
         */
        @Column(name = "processing_fee", precision = 19, scale = 2)
        @Builder.Default
        @JsonProperty("processingFee")
        private BigDecimal processingFee = BigDecimal.ZERO;

        /**
         * Actual processing fee amount collected so far.
         */
        @Column(name = "processing_fee_paid", precision = 19, scale = 2, nullable = false)
        @Builder.Default
        @JsonProperty("processingFeePaid")
        private BigDecimal processingFeePaid = BigDecimal.ZERO;

        // ================================================================
        // LOAN PURPOSE / NOTES
        // ================================================================

        @Column(name = "notes", columnDefinition = "TEXT")
        private String notes;

        @Column(name = "purpose", columnDefinition = "TEXT")
        private String purpose;

        @Column(name = "collateral_description", columnDefinition = "TEXT")
        private String collateralDescription;

        @Column(name = "collateral_value", precision = 19, scale = 2)
        private BigDecimal collateralValue;

        @Column(name = "rejection_reason", columnDefinition = "TEXT")
        private String rejectionReason;

        @Column(name = "internal_notes", columnDefinition = "TEXT")
        private String internalNotes;

        // ================================================================
        // IMPORT INFORMATION
        // ================================================================

        @Column(name = "imported", nullable = false)
        @Builder.Default
        private Boolean imported = false;

        @Column(name = "import_batch_id")
        private Long importBatchId;

        // ================================================================
        // CREDIT / RISK
        // ================================================================

        @Column(name = "risk_score", precision = 19, scale = 2)
        private BigDecimal riskScore;

        @Column(name = "risk_category", length = 30)
        private String riskCategory;

        @Column(name = "debt_to_income_ratio", precision = 19, scale = 9)
        @JsonProperty("debtToIncomeRatio")
        private BigDecimal debtToIncomeRatio;

        @Column(name = "credit_score_snapshot")
        private Integer creditScoreSnapshot;

        // ================================================================
        // REGULATORY / BUSINESS DATES
        // ================================================================

        @Column(name = "start_date")
        private LocalDate startDate;

        @Column(name = "approved_at")
        private LocalDate approvedAt;

        @Column(name = "disbursed_at")
        private LocalDateTime disbursedAt;

        /**
         * Loan maturity date.
         */
        @Column(name = "maturity_date")
        private LocalDate maturityDate;

        /**
         * Legacy compatibility timestamp.
         */
        @Column(name = "disbursed_at_timestamp")
        private LocalDateTime disbursedAtTimestamp;

        // ================================================================
        // AUDIT / SYSTEM DATES
        // ================================================================

        @Column(name = "created_at", nullable = false, updatable = false)
        private LocalDateTime createdAt;

        @Column(name = "updated_at", nullable = false)
        private LocalDateTime updatedAt;

        // ================================================================
        // TERMS
        // ================================================================

        @Column(name = "terms_accepted_at")
        private LocalDateTime termsAcceptedAt;

        // ================================================================
        // PAYMENTS
        // ================================================================

        @JsonIgnore
        @OneToMany(mappedBy = "loan", cascade = CascadeType.ALL, orphanRemoval = false, fetch = FetchType.LAZY)
        @Builder.Default
        private List<Payment> payments = new ArrayList<>();

        // ================================================================
        // JPA LIFECYCLE
        // ================================================================

        @PrePersist
        protected void onCreate() {

                LocalDateTime now = LocalDateTime.now();

                if (createdAt == null) {
                        createdAt = now;
                }

                if (updatedAt == null) {
                        updatedAt = now;
                }

                if (status == null) {
                        status = LoanStatus.PENDING;
                }

                // ============================================================
                // FINANCIAL DEFAULTS
                // ============================================================

                if (interestRate == null) {
                        interestRate = DEFAULT_MONTHLY_INTEREST_RATE;
                }

                interestRate = interestRate.setScale(
                                9,
                                RoundingMode.HALF_UP);

                if (interestRateType == null
                                || interestRateType.isBlank()) {

                        interestRateType = "MONTHLY";
                }

                interestRateType = interestRateType.trim().toUpperCase();

                if (managementFeeRate == null) {
                        managementFeeRate = DEFAULT_MONTHLY_MANAGEMENT_FEE_RATE;
                }

                managementFeeRate = managementFeeRate.setScale(
                                9,
                                RoundingMode.HALF_UP);

                if (processingFeeRate == null) {
                        processingFeeRate = DEFAULT_PROCESSING_FEE_RATE;
                }

                processingFeeRate = processingFeeRate.setScale(
                                9,
                                RoundingMode.HALF_UP);

                if (repaymentFrequency == null) {
                        repaymentFrequency = RepaymentFrequency.MONTHLY;
                }

                // ============================================================
                // CURRENCY
                // ============================================================

                if (currency == null
                                || currency.isBlank()) {

                        currency = "RWF";
                }

                currency = currency.trim().toUpperCase();

                // ============================================================
                // DEFAULT COUNTERS
                // ============================================================

                if (missedInstallments == null) {
                        missedInstallments = 0;
                }

                if (daysOverdue == null
                                || daysOverdue < 0) {

                        daysOverdue = 0;
                }

                // ============================================================
                // MONEY DEFAULTS
                // ============================================================

                if (totalPaid == null) {
                        totalPaid = MoneyMath.ZERO;
                }

                if (outstandingBalance == null) {
                        outstandingBalance = MoneyMath.ZERO;
                }

                if (processingFee == null) {
                        processingFee = MoneyMath.ZERO;
                }

                if (processingFeePaid == null) {
                        processingFeePaid = MoneyMath.ZERO;
                }

                if (managementFee == null) {
                        managementFee = MoneyMath.ZERO;
                }

                if (managementFeePaid == null) {
                        managementFeePaid = MoneyMath.ZERO;
                }

                if (totalInterest == null) {
                        totalInterest = MoneyMath.ZERO;
                }

                if (interestPaid == null) {
                        interestPaid = MoneyMath.ZERO;
                }
                if (principalPaid == null) {
                        principalPaid = MoneyMath.ZERO;
                }
                if (interestOutstanding == null) {
                        interestOutstanding = MoneyMath.ZERO;
                }
                if (managementFeeOutstanding == null) {
                        managementFeeOutstanding = MoneyMath.ZERO;
                }
                if (penaltiesAssessed == null) {
                        penaltiesAssessed = MoneyMath.ZERO;
                }
                if (penaltiesPaid == null) {
                        penaltiesPaid = MoneyMath.ZERO;
                }

                if (totalRepayable == null) {
                        totalRepayable = MoneyMath.ZERO;
                }

                if (amount == null) {
                        amount = MoneyMath.ZERO;
                }

                if (disbursedAmount == null) {
                        disbursedAmount = MoneyMath.ZERO;
                }

                if (netDisbursedAmount == null) {
                        netDisbursedAmount = disbursedAmount
                                        .subtract(processingFee)
                                        .max(MoneyMath.ZERO);
                }

                if (nextInstallmentAmount == null) {
                        nextInstallmentAmount = MoneyMath.ZERO;
                }

                // ============================================================
                // CLASSIFICATION DEFAULTS
                // ============================================================

                if (creditQuality == null) {
                        creditQuality = CreditQuality.CURRENT;
                }

                if (arrearsStatus == null) {
                        arrearsStatus = ArrearsStatus.NOT_DUE;
                }

                if (collectionsStage == null) {
                        collectionsStage = CollectionsStage.NORMAL;
                }

                // ============================================================
                // IMPORT DEFAULT
                // ============================================================

                if (imported == null) {
                        imported = false;
                }

                // ============================================================
                // PAYMENTS
                // ============================================================

                if (payments == null) {
                        payments = new ArrayList<>();
                }

                // ============================================================
                // DISBURSEMENT TIMESTAMP COMPATIBILITY
                // ============================================================

                if (disbursedAt == null
                                && disbursedAtTimestamp != null) {

                        disbursedAt = disbursedAtTimestamp;
                }

                if (disbursedAtTimestamp == null
                                && disbursedAt != null) {

                        disbursedAtTimestamp = disbursedAt;
                }

                // ============================================================
                // INITIAL LOAN CLASSIFICATION
                // ============================================================

                recalculateClassification();
        }

        @PreUpdate
        protected void onUpdate() {

                updatedAt = LocalDateTime.now();

                // Keep both timestamp fields synchronized.
                if (disbursedAt == null
                                && disbursedAtTimestamp != null) {

                        disbursedAt = disbursedAtTimestamp;
                }

                if (disbursedAtTimestamp == null
                                && disbursedAt != null) {

                        disbursedAtTimestamp = disbursedAt;
                }

                // Keep classification consistent with days overdue.
                recalculateClassification();
        }

        public static CreditQuality classifyCreditQuality(
                        Integer daysOverdue) {

                int days = daysOverdue == null
                                ? 0
                                : Math.max(daysOverdue, 0);

                if (days == NORMAL_MAX_DAYS_OVERDUE) {
                        return CreditQuality.CURRENT;
                }

                if (days >= WATCH_MIN_DAYS_OVERDUE
                                && days <= WATCH_MAX_DAYS_OVERDUE) {

                        return CreditQuality.WATCH;
                }

                if (days >= SUBSTANDARD_MIN_DAYS_OVERDUE
                                && days <= SUBSTANDARD_MAX_DAYS_OVERDUE) {

                        return CreditQuality.SUBSTANDARD;
                }

                if (days >= DOUBTFUL_MIN_DAYS_OVERDUE
                                && days <= DOUBTFUL_MAX_DAYS_OVERDUE) {

                        return CreditQuality.DOUBTFUL;
                }

                return CreditQuality.WRITTEN_OFF;
        }

        /**
         * Calculates the arrears status from days overdue.
         */
        public static ArrearsStatus classifyArrearsStatus(
                        Integer daysOverdue) {

                int days = daysOverdue == null
                                ? 0
                                : Math.max(daysOverdue, 0);

                return days > 0
                                ? ArrearsStatus.PAST_DUE
                                : ArrearsStatus.NOT_DUE;
        }

        public static CollectionsStage classifyCollectionsStage(
                        Integer daysOverdue) {

                int days = daysOverdue == null
                                ? 0
                                : Math.max(daysOverdue, 0);

                if (days == 0) {
                        return CollectionsStage.NORMAL;
                }

                if (days <= 89) {
                        return CollectionsStage.REMINDER;
                }

                if (days <= 179) {
                        return CollectionsStage.COLLECTION;
                }

                if (days <= 359) {
                        return CollectionsStage.LEGAL;
                }

                return CollectionsStage.RECOVERY;
        }

        public void recalculateClassification() {

                int days = daysOverdue == null
                                ? 0
                                : Math.max(daysOverdue, 0);

                this.daysOverdue = days;

                this.creditQuality = classifyCreditQuality(days);

                this.arrearsStatus = classifyArrearsStatus(days);

                this.collectionsStage = classifyCollectionsStage(days);

                this.classifiedAt = LocalDateTime.now();
        }

        /**
         * Updates days overdue and immediately recalculates
         * the loan classification.
         *
         * CollectionService can use this method whenever it
         * calculates the current overdue days.
         */
        public void updateDaysOverdue(Integer days) {

                this.daysOverdue = days == null
                                ? 0
                                : Math.max(days, 0);

                recalculateClassification();
        }

        /**
         * Returns true when the loan is currently overdue.
         */
        @JsonIgnore
        public boolean isOverdue() {

                return daysOverdue != null
                                && daysOverdue > 0;
        }

        /**
         * Returns true when the loan has reached written-off
         * classification.
         */
        @JsonIgnore
        public boolean isWrittenOffClassification() {

                return creditQuality == CreditQuality.WRITTEN_OFF;
        }

        /**
         * Returns true when the loan is in the high-risk
         * collection/legal/recovery range.
         */
        @JsonIgnore
        public boolean isInCollections() {

                return collectionsStage == CollectionsStage.COLLECTION
                                || collectionsStage == CollectionsStage.LEGAL
                                || collectionsStage == CollectionsStage.RECOVERY;
        }

        // ================================================================
        // BUSINESS VALIDATION HELPERS
        // ================================================================

        /**
         * Returns true when the supplied principal satisfies
         * the platform minimum.
         *
         * There is deliberately no maximum amount check.
         */
        public static boolean isValidLoanAmount(
                        BigDecimal principal) {

                if (principal == null) {
                        return false;
                }

                return principal.compareTo(
                                MIN_LOAN_AMOUNT) >= 0;
        }

        public static boolean isValidLoanDuration(
                        Integer months) {

                if (months == null) {
                        return false;
                }

                return months >= MIN_LOAN_DURATION_MONTHS
                                && months <= MAX_LOAN_DURATION_MONTHS;
        }

        /**
         * Returns the total recurring monthly charge rate.
         *
         * 5% interest + 5% management = 10%.
         */
        public BigDecimal getTotalMonthlyChargeRate() {

                BigDecimal interest = interestRate != null
                                ? interestRate
                                : DEFAULT_MONTHLY_INTEREST_RATE;

                BigDecimal management = managementFeeRate != null
                                ? managementFeeRate
                                : DEFAULT_MONTHLY_MANAGEMENT_FEE_RATE;

                return interest
                                .add(management)
                                .setScale(
                                                2,
                                                RoundingMode.HALF_UP);
        }

        /**
         * Calculates the one-time processing fee from principal.
         */
        @JsonIgnore
        public BigDecimal calculateProcessingFee() {

                BigDecimal principal = amount != null
                                ? amount
                                : BigDecimal.ZERO;

                BigDecimal rate = processingFeeRate != null
                                ? processingFeeRate
                                : DEFAULT_PROCESSING_FEE_RATE;

                return principal
                                .multiply(rate)
                                .divide(
                                                new BigDecimal("100"),
                                                2,
                                                RoundingMode.HALF_UP);
        }

        /**
         * Calculates the net cash amount received after
         * the one-time 2% processing fee.
         */
        @JsonIgnore
        public BigDecimal calculateNetDisbursedAmount() {

                BigDecimal principal = amount != null
                                ? amount
                                : BigDecimal.ZERO;

                BigDecimal fee = processingFee != null
                                ? processingFee
                                : calculateProcessingFee();

                return principal
                                .subtract(fee)
                                .max(BigDecimal.ZERO)
                                .setScale(
                                                2,
                                                RoundingMode.HALF_UP);
        }

        // ================================================================
        // BIGDECIMAL ACCESSORS
        // ================================================================

        @JsonIgnore
        public BigDecimal getAmountDecimal() {
                return amount;
        }

        @JsonIgnore
        public BigDecimal getInterestRateDecimal() {
                return interestRate;
        }

        @JsonIgnore
        public BigDecimal getManagementFeeRateDecimal() {
                return managementFeeRate;
        }

        @JsonIgnore
        public BigDecimal getManagementFeeDecimal() {
                return managementFee;
        }

        @JsonIgnore
        public BigDecimal getManagementFeePaidDecimal() {
                return managementFeePaid;
        }

        @JsonIgnore
        public BigDecimal getTotalInterestDecimal() {
                return totalInterest;
        }

        @JsonIgnore
        public BigDecimal getPrincipalPaidDecimal() {
                return principalPaid == null ? MoneyMath.ZERO : principalPaid;
        }

        @JsonIgnore
        public BigDecimal getManagementFeeOutstandingDecimal() {
                return managementFeeOutstanding == null ? MoneyMath.ZERO : managementFeeOutstanding;
        }

        @JsonIgnore
        public BigDecimal getInterestOutstandingDecimal() {
                return interestOutstanding == null ? MoneyMath.ZERO : interestOutstanding;
        }

        @JsonIgnore
        public BigDecimal getPenaltiesAssessedDecimal() {
                return penaltiesAssessed == null ? MoneyMath.ZERO : penaltiesAssessed;
        }

        @JsonIgnore
        public BigDecimal getPenaltiesPaidDecimal() {
                return penaltiesPaid == null ? MoneyMath.ZERO : penaltiesPaid;
        }

        @JsonIgnore
        public BigDecimal getInterestPaidDecimal() {
                return interestPaid;
        }

        @JsonIgnore
        public BigDecimal getProcessingFeeRateDecimal() {
                return processingFeeRate;
        }

        @JsonIgnore
        public BigDecimal getProcessingFeeDecimal() {
                return processingFee;
        }

        @JsonIgnore
        public BigDecimal getProcessingFeePaidDecimal() {
                return processingFeePaid;
        }

        @JsonIgnore
        public BigDecimal getDisbursedAmountDecimal() {
                return disbursedAmount;
        }

        @JsonIgnore
        public BigDecimal getNetDisbursedAmountDecimal() {
                return netDisbursedAmount;
        }

        @JsonIgnore
        public BigDecimal getTotalRepayableDecimal() {
                return totalRepayable;
        }

        @JsonIgnore
        public BigDecimal getTotalPaidDecimal() {
                return totalPaid;
        }

        @JsonIgnore
        public BigDecimal getOutstandingBalanceDecimal() {
                return outstandingBalance;
        }

        @JsonIgnore
        public BigDecimal getCollateralValueDecimal() {
                return collateralValue;
        }

        @JsonIgnore
        public BigDecimal getNextInstallmentAmountDecimal() {
                return nextInstallmentAmount;
        }

        // ================================================================
        // LEGACY DOUBLE COMPATIBILITY GETTERS
        // ================================================================

        public Double getAmountDouble() {
                return amount == null
                                ? null
                                : amount.doubleValue();
        }

        public Double getInterestRateDouble() {
                return interestRate == null
                                ? null
                                : interestRate.doubleValue();
        }

        public Double getManagementFeeRateDouble() {
                return managementFeeRate == null
                                ? null
                                : managementFeeRate.doubleValue();
        }

        public Double getManagementFeeDouble() {
                return managementFee == null
                                ? null
                                : managementFee.doubleValue();
        }

        public Double getManagementFeePaidDouble() {
                return managementFeePaid == null
                                ? null
                                : managementFeePaid.doubleValue();
        }

        public Double getTotalInterestDouble() {
                return totalInterest == null
                                ? null
                                : totalInterest.doubleValue();
        }

        public Double getInterestPaidDouble() {
                return interestPaid == null
                                ? null
                                : interestPaid.doubleValue();
        }

        public Double getProcessingFeeRateDouble() {
                return processingFeeRate == null
                                ? null
                                : processingFeeRate.doubleValue();
        }

        public Double getProcessingFeeDouble() {
                return processingFee == null
                                ? null
                                : processingFee.doubleValue();
        }

        public Double getProcessingFeePaidDouble() {
                return processingFeePaid == null
                                ? null
                                : processingFeePaid.doubleValue();
        }

        public Double getDisbursedAmountDouble() {
                return disbursedAmount == null
                                ? null
                                : disbursedAmount.doubleValue();
        }

        public Double getNetDisbursedAmountDouble() {
                return netDisbursedAmount == null
                                ? null
                                : netDisbursedAmount.doubleValue();
        }

        public Double getTotalRepayableDouble() {
                return totalRepayable == null
                                ? null
                                : totalRepayable.doubleValue();
        }

        public Double getTotalPaidDouble() {
                return totalPaid == null
                                ? null
                                : totalPaid.doubleValue();
        }

        public Double getOutstandingBalanceDouble() {
                return outstandingBalance == null
                                ? null
                                : outstandingBalance.doubleValue();
        }

        public Double getCollateralValueDouble() {
                return collateralValue == null
                                ? null
                                : collateralValue.doubleValue();
        }

        public Double getNextInstallmentAmountDouble() {
                return nextInstallmentAmount == null
                                ? null
                                : nextInstallmentAmount.doubleValue();
        }

        // ================================================================
        // BIGDECIMAL SETTERS
        // ================================================================

        public void setAmount(BigDecimal value) {
                this.amount = normalizeMoney(value);
        }

        public void setInterestRate(BigDecimal value) {
                this.interestRate = normalizeRate(value);
        }

        public void setManagementFeeRate(BigDecimal value) {
                this.managementFeeRate = normalizeRate(value);
        }

        public void setManagementFee(BigDecimal value) {
                this.managementFee = normalizeMoney(value);
        }

        public void setManagementFeePaid(BigDecimal value) {
                this.managementFeePaid = normalizeMoney(value);
        }

        public void setTotalInterest(BigDecimal value) {
                this.totalInterest = normalizeMoney(value);
        }

        public void setInterestPaid(BigDecimal value) {
                this.interestPaid = normalizeMoney(value);
        }

        public void setProcessingFeeRate(BigDecimal value) {
                this.processingFeeRate = normalizeRate(value);
        }

        public void setProcessingFee(BigDecimal value) {
                this.processingFee = normalizeMoney(value);
        }

        public void setProcessingFeePaid(BigDecimal value) {
                this.processingFeePaid = normalizeMoney(value);
        }

        public void setDisbursedAmount(BigDecimal value) {
                this.disbursedAmount = normalizeMoney(value);
        }

        public void setNetDisbursedAmount(BigDecimal value) {
                this.netDisbursedAmount = normalizeMoney(value);
        }

        public void setTotalRepayable(BigDecimal value) {
                this.totalRepayable = normalizeMoney(value);
        }

        public void setTotalPaid(BigDecimal value) {
                this.totalPaid = normalizeMoney(value);
        }

        public void setOutstandingBalance(BigDecimal value) {
                this.outstandingBalance = normalizeMoney(value);
        }

        public void setCollateralValue(BigDecimal value) {
                this.collateralValue = normalizeMoney(value);
        }

        public void setNextInstallmentAmount(BigDecimal value) {
                this.nextInstallmentAmount = normalizeMoney(value);
        }

        // ================================================================
        // LEGACY DOUBLE SETTERS
        // ================================================================

        public void setAmount(Double value) {
                this.amount = MoneyMath.of(value);
        }

        public void setInterestRate(Double value) {
                this.interestRate = MoneyMath.of(value);
        }

        public void setManagementFeeRate(Double value) {
                this.managementFeeRate = MoneyMath.of(value);
        }

        public void setManagementFee(Double value) {
                this.managementFee = MoneyMath.of(value);
        }

        public void setManagementFeePaid(Double value) {
                this.managementFeePaid = MoneyMath.of(value);
        }

        public void setTotalInterest(Double value) {
                this.totalInterest = MoneyMath.of(value);
        }

        public void setInterestPaid(Double value) {
                this.interestPaid = MoneyMath.of(value);
        }

        public void setProcessingFeeRate(Double value) {
                this.processingFeeRate = MoneyMath.of(value);
        }

        public void setProcessingFee(Double value) {
                this.processingFee = MoneyMath.of(value);
        }

        public void setProcessingFeePaid(Double value) {
                this.processingFeePaid = MoneyMath.of(value);
        }

        public void setDisbursedAmount(Double value) {
                this.disbursedAmount = MoneyMath.of(value);
        }

        public void setNetDisbursedAmount(Double value) {
                this.netDisbursedAmount = MoneyMath.of(value);
        }

        public void setTotalRepayable(Double value) {
                this.totalRepayable = MoneyMath.of(value);
        }

        public void setTotalPaid(Double value) {
                this.totalPaid = MoneyMath.of(value);
        }

        public void setOutstandingBalance(Double value) {
                this.outstandingBalance = MoneyMath.of(value);
        }

        public void setCollateralValue(Double value) {
                this.collateralValue = MoneyMath.of(value);
        }

        public void setNextInstallmentAmount(Double value) {
                this.nextInstallmentAmount = MoneyMath.of(value);
        }

        // ================================================================
        // NORMALIZATION HELPERS
        // ================================================================

        private static BigDecimal normalizeMoney(
                        BigDecimal value) {

                if (value == null) {
                        return BigDecimal.ZERO.setScale(
                                        2,
                                        RoundingMode.HALF_UP);
                }

                return value.setScale(
                                2,
                                RoundingMode.HALF_UP);
        }

        private static BigDecimal normalizeRate(
                        BigDecimal value) {

                if (value == null) {
                        return BigDecimal.ZERO.setScale(
                                        9,
                                        RoundingMode.HALF_UP);
                }

                return value.setScale(
                                9,
                                RoundingMode.HALF_UP);
        }

        // ================================================================
        // ENUMS
        // ================================================================

        public enum LoanType {

                PERSONAL,

                MORTGAGE,

                AUTO,

                BUSINESS,

                STUDENT,

                EMERGENCY,

                ASSET_FINANCE,

                SALARY_ADVANCE,

                MICROFINANCE,

                AGRICULTURAL,

                TRADE_FINANCE,

                GROUP
        }

        public enum RepaymentFrequency {

                MONTHLY
        }

        public enum CreditQuality {

                CURRENT,

                WATCH,

                SUBSTANDARD,

                DOUBTFUL,

                WRITTEN_OFF
        }

        public enum ArrearsStatus {

                NOT_DUE,

                PAST_DUE
        }

        public enum CollectionsStage {

                NORMAL,

                REMINDER,

                COLLECTION,

                LEGAL,

                RECOVERY
        }
}