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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.patrick.fintech.loan_backend.util.MoneyMath;

@JsonIgnoreProperties({
                "hibernateLazyInitializer",
                "handler"
})
@Entity
@Table(name = "payments", indexes = {

                @Index(name = "idx_payment_loan", columnList = "loan_id"),

                @Index(name = "idx_payment_due", columnList = "due_date"),

                @Index(name = "idx_payment_org", columnList = "organization_id"),

                @Index(name = "idx_payment_transaction", columnList = "transaction_id"),

                @Index(name = "idx_payment_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

        // ================================================================
        // IDENTITY
        // ================================================================

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(unique = true, nullable = false, length = 150)
        private String paymentReference;

        // ================================================================
        // RELATIONSHIPS
        // ================================================================

        @JsonIgnore
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "loan_id", nullable = false, foreignKey = @ForeignKey(name = "fk_payment_loan"))
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        private Loan loan;

        @JsonIgnore
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "organization_id", nullable = false, foreignKey = @ForeignKey(name = "fk_payment_organization"))
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        private Organization organization;

        @JsonIgnore
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "recorded_by", foreignKey = @ForeignKey(name = "fk_payment_recorded_by"))
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        private User recordedBy;

        // ================================================================
        // INSTALLMENT
        // ================================================================

        @Column(name = "installment_number")
        private Integer installmentNumber;

        // ================================================================
        // PAYMENT AMOUNT
        // ================================================================

        /**
         * Scheduled/expected amount for this installment.
         */
        @Column(name = "amount", precision = 19, scale = 2)
        @JsonProperty("amount")
        @Builder.Default
        private BigDecimal amount = BigDecimal.ZERO.setScale(
                        2,
                        RoundingMode.HALF_UP);

        /**
         * Principal actually paid against this installment.
         *
         * Cumulative value for the installment record.
         */
        @Column(name = "principal_component", precision = 19, scale = 2)
        @JsonProperty("principalComponent")
        @Builder.Default
        private BigDecimal principalComponent = BigDecimal.ZERO;

        /**
         * Interest actually paid against this installment.
         *
         * Cumulative value.
         *
         * This field contains ONLY the interest portion.
         */
        @Column(name = "interest_component", precision = 19, scale = 2)
        @JsonProperty("interestComponent")
        @Builder.Default
        private BigDecimal interestComponent = BigDecimal.ZERO;

        @Column(name = "management_fee_component", precision = 19, scale = 2)
        @JsonProperty("managementFeeComponent")
        @Builder.Default
        private BigDecimal managementFeeComponent = BigDecimal.ZERO;

        /** Extension/restructuring fee settled by this payment. */
        @Column(name = "extension_fee_component", precision = 19, scale = 2, nullable = false)
        @JsonProperty("extensionFeeComponent")
        @Builder.Default
        private BigDecimal extensionFeeComponent = BigDecimal.ZERO;

        @Column(name = "amount_paid", precision = 19, scale = 2)
        @JsonProperty("amountPaid")
        @Builder.Default
        private BigDecimal amountPaid = BigDecimal.ZERO;

        @Column(name = "scheduled_interest", precision = 19, scale = 2)
        @Builder.Default
        @JsonProperty("scheduledInterest")
        private BigDecimal scheduledInterest = BigDecimal.ZERO;

        @Column(name = "scheduled_management_fee", precision = 19, scale = 2)
        @Builder.Default
        @JsonProperty("scheduledManagementFee")
        private BigDecimal scheduledManagementFee = BigDecimal.ZERO;

        @Column(name = "cycle_interest_due", precision = 19, scale = 2)
        @Builder.Default
        @JsonProperty("cycleInterestDue")
        private BigDecimal cycleInterestDue = BigDecimal.ZERO;

        @Column(name = "cycle_interest_remaining", precision = 19, scale = 2)
        @Builder.Default
        @JsonProperty("cycleInterestRemaining")
        private BigDecimal cycleInterestRemaining = BigDecimal.ZERO;

        @Column(name = "cycle_management_fee_due", precision = 19, scale = 2)
        @Builder.Default
        @JsonProperty("cycleManagementFeeDue")
        private BigDecimal cycleManagementFeeDue = BigDecimal.ZERO;

        @Column(name = "cycle_management_fee_remaining", precision = 19, scale = 2)
        @Builder.Default
        @JsonProperty("cycleManagementFeeRemaining")
        private BigDecimal cycleManagementFeeRemaining = BigDecimal.ZERO;

        // ================================================================
        // INTEREST CALCULATION TIMESTAMP
        // ================================================================

        /**
         * Exact timestamp of the most recent actual interest/management
         * charge calculation.
         *
         * Schedule generation MUST NOT populate this field.
         *
         * Example:
         *
         * 09 Aug 2026 10:00
         * -> disbursement
         *
         * 09 Aug 2026 10:01
         * -> first payment
         *
         * first calculation = 1 day
         *
         * Another payment:
         *
         * 09 Aug 2026 10:05
         * -> 0 additional days
         */
        @Column(name = "interest_calculation_date")
        private LocalDateTime interestCalculationDate;

        // ================================================================
        // PENALTY
        // ================================================================

        /**
         * Total penalty assessed for this installment.
         *
         * Platform rule:
         *
         * 15% per month.
         *
         * Using a 30-day month:
         *
         * 15% / 30 = 0.5% per overdue day.
         */
        @Column(name = "penalty", precision = 19, scale = 2)
        @Builder.Default
        @JsonProperty("penalty")
        private BigDecimal penalty = BigDecimal.ZERO;

        /**
         * Total penalty actually paid.
         *
         * Cumulative.
         */
        @Column(name = "penalty_paid", precision = 19, scale = 2)
        @Builder.Default
        @JsonProperty("penaltyPaid")
        private BigDecimal penaltyPaid = BigDecimal.ZERO;

        /**
         * Amount waived by authorized staff.
         */
        @Column(name = "waived_amount", precision = 19, scale = 2)
        @Builder.Default
        @JsonProperty("waivedAmount")
        private BigDecimal waivedAmount = BigDecimal.ZERO;

        // ================================================================
        // BALANCE
        // ================================================================

        /**
         * Gross outstanding principal after this payment.
         *
         * Processing fee is NOT part of this balance.
         */
        @Column(name = "outstanding_after", precision = 19, scale = 2)
        @JsonProperty("outstandingAfter")
        @Builder.Default
        private BigDecimal outstandingAfter = BigDecimal.ZERO;

        // ================================================================
        // PAYMENT STATUS
        // ================================================================

        @Column(name = "paid", nullable = false)
        @Builder.Default
        private Boolean paid = false;

        @Column(name = "due_date")
        private LocalDate dueDate;

        @Column(name = "paid_date")
        private LocalDate paidDate;

        @Column(name = "payment_method", length = 50)
        private String paymentMethod;

        @Column(name = "transaction_id", length = 255)
        private String transactionId;

        @Column(name = "external_reference", length = 255)
        private String externalReference;

        @Column(name = "gateway_response", columnDefinition = "TEXT")
        private String gatewayResponse;

        @Column(name = "channel", length = 50)
        private String channel;

        @Column(name = "notes", columnDefinition = "TEXT")
        private String notes;

        @Column(name = "is_late", nullable = false)
        @Builder.Default
        private boolean isLate = false;

        @Column(name = "days_late", nullable = false)
        @Builder.Default
        private Integer daysLate = 0;

        @Enumerated(EnumType.STRING)
        @Column(name = "status", nullable = false, length = 30)
        @Builder.Default
        private PaymentStatus status = PaymentStatus.PENDING;

        // ================================================================
        // SYSTEM DATES
        // ================================================================

        @Column(name = "created_at", nullable = false, updatable = false)
        private LocalDateTime createdAt;

        @Column(name = "verified_at")
        private LocalDateTime verifiedAt;

        @Column(name = "updated_at")
        private LocalDateTime updatedAt;

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

                if (paid == null) {
                        paid = false;
                }

                if (status == null) {
                        status = PaymentStatus.PENDING;
                }

                if (daysLate == null) {
                        daysLate = 0;
                }

                normalizeMoneyFields();
        }

        @PreUpdate
        protected void onUpdate() {

                updatedAt = LocalDateTime.now();

                normalizeMoneyFields();
        }

        // ================================================================
        // NORMALIZE MONEY
        // ================================================================

        private void normalizeMoneyFields() {

                amount = normalizeMoney(
                                amount);

                principalComponent = normalizeMoney(
                                principalComponent);

                interestComponent = normalizeMoney(
                                interestComponent);

                managementFeeComponent = normalizeMoney(
                                managementFeeComponent);

                extensionFeeComponent = normalizeMoney(extensionFeeComponent);

                amountPaid = normalizeMoney(
                                amountPaid);

                scheduledInterest = normalizeMoney(
                                scheduledInterest);

                scheduledManagementFee = normalizeMoney(
                                scheduledManagementFee);

                cycleInterestDue = normalizeMoney(
                                cycleInterestDue);

                cycleInterestRemaining = normalizeMoney(
                                cycleInterestRemaining);

                cycleManagementFeeDue = normalizeMoney(
                                cycleManagementFeeDue);

                cycleManagementFeeRemaining = normalizeMoney(
                                cycleManagementFeeRemaining);

                penalty = normalizeMoney(
                                penalty);

                penaltyPaid = normalizeMoney(
                                penaltyPaid);

                waivedAmount = normalizeMoney(
                                waivedAmount);

                outstandingAfter = normalizeMoney(
                                outstandingAfter);
        }

        private BigDecimal normalizeMoney(
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

        // ================================================================
        // BIGDECIMAL ACCESSORS
        // ================================================================

        @JsonIgnore
        public BigDecimal getAmountDecimal() {

                return amount;
        }

        @JsonIgnore
        public BigDecimal getPrincipalComponentDecimal() {

                return principalComponent;
        }

        @JsonIgnore
        public BigDecimal getInterestComponentDecimal() {

                return interestComponent;
        }

        @JsonIgnore
        public BigDecimal getManagementFeeComponentDecimal() {

                return managementFeeComponent;
        }

        @JsonIgnore
        public BigDecimal getExtensionFeeComponentDecimal() {

                return extensionFeeComponent;
        }

        @JsonIgnore
        public BigDecimal getAmountPaidDecimal() {

                return amountPaid;
        }

        @JsonIgnore
        public BigDecimal getScheduledInterestDecimal() {

                return scheduledInterest;
        }

        @JsonIgnore
        public BigDecimal getScheduledManagementFeeDecimal() {

                return scheduledManagementFee;
        }

        @JsonIgnore
        public BigDecimal getCycleInterestDueDecimal() {

                return cycleInterestDue;
        }

        @JsonIgnore
        public BigDecimal getCycleInterestRemainingDecimal() {

                return cycleInterestRemaining;
        }

        @JsonIgnore
        public BigDecimal getCycleManagementFeeDueDecimal() {

                return cycleManagementFeeDue;
        }

        @JsonIgnore
        public BigDecimal getCycleManagementFeeRemainingDecimal() {

                return cycleManagementFeeRemaining;
        }

        @JsonIgnore
        public BigDecimal getPenaltyDecimal() {

                return penalty;
        }

        @JsonIgnore
        public BigDecimal getPenaltyPaidDecimal() {

                return penaltyPaid;
        }

        @JsonIgnore
        public BigDecimal getWaivedAmountDecimal() {

                return waivedAmount;
        }

        @JsonIgnore
        public BigDecimal getOutstandingAfterDecimal() {

                return outstandingAfter;
        }

        // ================================================================
        // DERIVED FINANCIAL HELPERS
        // ================================================================

        /**
         * Total scheduled recurring charge for this installment.
         *
         * Interest + management fee.
         */
        @JsonIgnore
        public BigDecimal getScheduledRecurringChargeDecimal() {

                return normalizeMoney(
                                safe(
                                                scheduledInterest).add(
                                                                safe(
                                                                                scheduledManagementFee)));
        }

        /**
         * Total current-cycle recurring charge due.
         *
         * Interest + management fee.
         */
        @JsonIgnore
        public BigDecimal getCycleRecurringChargeDueDecimal() {

                return normalizeMoney(
                                safe(
                                                cycleInterestDue).add(
                                                                safe(
                                                                                cycleManagementFeeDue)));
        }

        /**
         * Total current-cycle recurring charge remaining.
         *
         * Interest remaining + management fee remaining.
         */
        @JsonIgnore
        public BigDecimal getCycleRecurringChargeRemainingDecimal() {

                return normalizeMoney(
                                safe(
                                                cycleInterestRemaining).add(
                                                                safe(
                                                                                cycleManagementFeeRemaining)));
        }

        /**
         * Total recurring charges actually paid against this installment.
         *
         * Interest paid + management fee paid.
         */
        @JsonIgnore
        public BigDecimal getRecurringChargePaidDecimal() {

                return normalizeMoney(
                                safe(
                                                interestComponent).add(
                                                                safe(
                                                                                managementFeeComponent)));
        }

        private BigDecimal safe(
                        BigDecimal value) {

                return value == null
                                ? BigDecimal.ZERO
                                : value;
        }

        // ================================================================
        // DOUBLE COMPATIBILITY GETTERS
        // ================================================================

        public Double getAmountDouble() {

                return amount == null
                                ? null
                                : amount.doubleValue();
        }

        public Double getPrincipalComponentDouble() {

                return principalComponent == null
                                ? null
                                : principalComponent.doubleValue();
        }

        public Double getInterestComponentDouble() {

                return interestComponent == null
                                ? null
                                : interestComponent.doubleValue();
        }

        public Double getManagementFeeComponentDouble() {

                return managementFeeComponent == null
                                ? null
                                : managementFeeComponent.doubleValue();
        }

        public Double getAmountPaidDouble() {

                return amountPaid == null
                                ? null
                                : amountPaid.doubleValue();
        }

        public Double getScheduledInterestDouble() {

                return scheduledInterest == null
                                ? null
                                : scheduledInterest.doubleValue();
        }

        public Double getScheduledManagementFeeDouble() {

                return scheduledManagementFee == null
                                ? null
                                : scheduledManagementFee.doubleValue();
        }

        public Double getCycleInterestDueDouble() {

                return cycleInterestDue == null
                                ? null
                                : cycleInterestDue.doubleValue();
        }

        public Double getCycleInterestRemainingDouble() {

                return cycleInterestRemaining == null
                                ? null
                                : cycleInterestRemaining.doubleValue();
        }

        public Double getCycleManagementFeeDueDouble() {

                return cycleManagementFeeDue == null
                                ? null
                                : cycleManagementFeeDue.doubleValue();
        }

        public Double getCycleManagementFeeRemainingDouble() {

                return cycleManagementFeeRemaining == null
                                ? null
                                : cycleManagementFeeRemaining.doubleValue();
        }

        public Double getPenaltyDouble() {

                return penalty == null
                                ? null
                                : penalty.doubleValue();
        }

        public Double getPenaltyPaidDouble() {

                return penaltyPaid == null
                                ? null
                                : penaltyPaid.doubleValue();
        }

        public Double getWaivedAmountDouble() {

                return waivedAmount == null
                                ? null
                                : waivedAmount.doubleValue();
        }

        public Double getOutstandingAfterDouble() {

                return outstandingAfter == null
                                ? null
                                : outstandingAfter.doubleValue();
        }

        // ================================================================
        // BIGDECIMAL SETTERS
        // ================================================================

        public void setAmount(
                        BigDecimal value) {

                this.amount = normalizeMoney(
                                value);
        }

        public void setPrincipalComponent(
                        BigDecimal value) {

                this.principalComponent = normalizeMoney(
                                value);
        }

        public void setInterestComponent(
                        BigDecimal value) {

                this.interestComponent = normalizeMoney(
                                value);
        }

        public void setManagementFeeComponent(
                        BigDecimal value) {

                this.managementFeeComponent = normalizeMoney(
                                value);
        }

        public void setExtensionFeeComponent(
                        BigDecimal value) {

                this.extensionFeeComponent = normalizeMoney(
                                value);
        }

        public void setAmountPaid(
                        BigDecimal value) {

                this.amountPaid = normalizeMoney(
                                value);
        }

        public void setScheduledInterest(
                        BigDecimal value) {

                this.scheduledInterest = normalizeMoney(
                                value);
        }

        public void setScheduledManagementFee(
                        BigDecimal value) {

                this.scheduledManagementFee = normalizeMoney(
                                value);
        }

        public void setCycleInterestDue(
                        BigDecimal value) {

                this.cycleInterestDue = normalizeMoney(
                                value);
        }

        public void setCycleInterestRemaining(
                        BigDecimal value) {

                this.cycleInterestRemaining = normalizeMoney(
                                value);
        }

        public void setCycleManagementFeeDue(
                        BigDecimal value) {

                this.cycleManagementFeeDue = normalizeMoney(
                                value);
        }

        public void setCycleManagementFeeRemaining(
                        BigDecimal value) {

                this.cycleManagementFeeRemaining = normalizeMoney(
                                value);
        }

        public void setPenalty(
                        BigDecimal value) {

                this.penalty = normalizeMoney(
                                value);
        }

        public void setPenaltyPaid(
                        BigDecimal value) {

                this.penaltyPaid = normalizeMoney(
                                value);
        }

        public void setWaivedAmount(
                        BigDecimal value) {

                this.waivedAmount = normalizeMoney(
                                value);
        }

        public void setOutstandingAfter(
                        BigDecimal value) {

                this.outstandingAfter = normalizeMoney(
                                value);
        }

        // ================================================================
        // LEGACY DOUBLE SETTERS
        // ================================================================

        public void setAmount(
                        Double value) {

                this.amount = MoneyMath.of(
                                value);
        }

        public void setPrincipalComponent(
                        Double value) {

                this.principalComponent = MoneyMath.of(
                                value);
        }

        public void setInterestComponent(
                        Double value) {

                this.interestComponent = MoneyMath.of(
                                value);
        }

        public void setManagementFeeComponent(
                        Double value) {

                this.managementFeeComponent = MoneyMath.of(
                                value);
        }

        public void setExtensionFeeComponent(
                        Double value) {

                this.extensionFeeComponent = MoneyMath.of(
                                value);
        }

        public void setAmountPaid(
                        Double value) {

                this.amountPaid = MoneyMath.of(
                                value);
        }

        public void setScheduledInterest(
                        Double value) {

                this.scheduledInterest = MoneyMath.of(
                                value);
        }

        public void setScheduledManagementFee(
                        Double value) {

                this.scheduledManagementFee = MoneyMath.of(
                                value);
        }

        public void setCycleInterestDue(
                        Double value) {

                this.cycleInterestDue = MoneyMath.of(
                                value);
        }

        public void setCycleInterestRemaining(
                        Double value) {

                this.cycleInterestRemaining = MoneyMath.of(
                                value);
        }

        public void setCycleManagementFeeDue(
                        Double value) {

                this.cycleManagementFeeDue = MoneyMath.of(
                                value);
        }

        public void setCycleManagementFeeRemaining(
                        Double value) {

                this.cycleManagementFeeRemaining = MoneyMath.of(
                                value);
        }

        public void setPenalty(
                        Double value) {

                this.penalty = MoneyMath.of(
                                value);
        }

        public void setPenaltyPaid(
                        Double value) {

                this.penaltyPaid = MoneyMath.of(
                                value);
        }

        public void setWaivedAmount(
                        Double value) {

                this.waivedAmount = MoneyMath.of(
                                value);
        }

        public void setOutstandingAfter(
                        Double value) {

                this.outstandingAfter = MoneyMath.of(
                                value);
        }

        // ================================================================
        // STATUS
        // ================================================================

        public enum PaymentStatus {

                PENDING,

                COMPLETED,

                FAILED,

                REVERSED,

                PARTIALLY_PAID
        }
}