package com.patrick.fintech.loan_backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.patrick.fintech.loan_backend.util.MoneyMath;

@Entity
@Table(name = "payment_schedules")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentSchedule {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "loan_id", nullable = false)
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        private Loan loan;

        @Column(nullable = false)
        private Integer installmentNumber;

        @Column(nullable = false)
        private LocalDate dueDate;

        @Column(nullable = false, precision = 19, scale = 2)
        private BigDecimal installmentAmount;

        @Column(nullable = false, precision = 19, scale = 2)
        private BigDecimal principalAmount;

        /**
         * Monthly 5% loan interest.
         */
        @Column(nullable = false, precision = 19, scale = 2)
        private BigDecimal interestAmount;

        /**
         * Monthly 5% loan-management fee.
         */
        @Column(name = "management_fee_amount", nullable = false, precision = 19, scale = 2)
        @Builder.Default
        private BigDecimal managementFeeAmount = MoneyMath.ZERO;

        /**
         * 15% monthly / 0.5% daily overdue penalty.
         */
        @Builder.Default
        @Column(precision = 19, scale = 2)
        private BigDecimal penaltyAmount = MoneyMath.ZERO;

        @Builder.Default
        @Column(precision = 19, scale = 2)
        private BigDecimal amountPaid = MoneyMath.ZERO;

        @Builder.Default
        @Column(precision = 19, scale = 2)
        private BigDecimal remainingBalance = MoneyMath.ZERO;

        @Enumerated(EnumType.STRING)
        private ScheduleStatus status;

        private LocalDate paidDate;

        private LocalDateTime createdAt;

        private LocalDateTime updatedAt;

        // ================================================================
        // DOUBLE COMPATIBILITY
        // ================================================================

        public Double getInstallmentAmountDouble() {

                return installmentAmount == null
                                ? null
                                : installmentAmount.doubleValue();
        }

        public Double getPrincipalAmountDouble() {

                return principalAmount == null
                                ? null
                                : principalAmount.doubleValue();
        }

        public Double getInterestAmountDouble() {

                return interestAmount == null
                                ? null
                                : interestAmount.doubleValue();
        }

        public Double getManagementFeeAmountDouble() {

                return managementFeeAmount == null
                                ? null
                                : managementFeeAmount.doubleValue();
        }

        public Double getPenaltyAmountDouble() {

                return penaltyAmount == null
                                ? null
                                : penaltyAmount.doubleValue();
        }

        public Double getAmountPaidDouble() {

                return amountPaid == null
                                ? null
                                : amountPaid.doubleValue();
        }

        public Double getRemainingBalanceDouble() {

                return remainingBalance == null
                                ? null
                                : remainingBalance.doubleValue();
        }

        // ================================================================
        // BIGDECIMAL SETTERS
        // ================================================================

        public void setInstallmentAmount(
                        BigDecimal value) {
                this.installmentAmount = normalize(value);
        }

        public void setPrincipalAmount(
                        BigDecimal value) {
                this.principalAmount = normalize(value);
        }

        public void setInterestAmount(
                        BigDecimal value) {
                this.interestAmount = normalize(value);
        }

        public void setManagementFeeAmount(
                        BigDecimal value) {
                this.managementFeeAmount = normalize(value);
        }

        public void setPenaltyAmount(
                        BigDecimal value) {
                this.penaltyAmount = normalize(value);
        }

        public void setAmountPaid(
                        BigDecimal value) {
                this.amountPaid = normalize(value);
        }

        public void setRemainingBalance(
                        BigDecimal value) {
                this.remainingBalance = normalize(value);
        }

        // ================================================================
        // LEGACY DOUBLE SETTERS
        // ================================================================

        public void setInstallmentAmount(Double value) {
                this.installmentAmount = MoneyMath.of(value);
        }

        public void setPrincipalAmount(Double value) {
                this.principalAmount = MoneyMath.of(value);
        }

        public void setInterestAmount(Double value) {
                this.interestAmount = MoneyMath.of(value);
        }

        public void setManagementFeeAmount(Double value) {
                this.managementFeeAmount = MoneyMath.of(value);
        }

        public void setPenaltyAmount(Double value) {
                this.penaltyAmount = MoneyMath.of(value);
        }

        public void setAmountPaid(Double value) {
                this.amountPaid = MoneyMath.of(value);
        }

        public void setRemainingBalance(Double value) {
                this.remainingBalance = MoneyMath.of(value);
        }

        // ================================================================
        // JPA LIFECYCLE
        // ================================================================

        @PrePersist
        public void onCreate() {

                LocalDateTime now = LocalDateTime.now();

                createdAt = now;
                updatedAt = now;

                if (status == null) {
                        status = ScheduleStatus.PENDING;
                }

                if (installmentAmount == null) {
                        installmentAmount = MoneyMath.ZERO;
                }

                if (principalAmount == null) {
                        principalAmount = MoneyMath.ZERO;
                }

                if (interestAmount == null) {
                        interestAmount = MoneyMath.ZERO;
                }

                if (managementFeeAmount == null) {
                        managementFeeAmount = MoneyMath.ZERO;
                }

                if (penaltyAmount == null) {
                        penaltyAmount = MoneyMath.ZERO;
                }

                if (amountPaid == null) {
                        amountPaid = MoneyMath.ZERO;
                }

                if (remainingBalance == null) {
                        remainingBalance = MoneyMath.ZERO;
                }

                normalizeAll();
        }

        @PreUpdate
        public void onUpdate() {

                updatedAt = LocalDateTime.now();

                normalizeAll();
        }

        private void normalizeAll() {

                installmentAmount = normalize(installmentAmount);

                principalAmount = normalize(principalAmount);

                interestAmount = normalize(interestAmount);

                managementFeeAmount = normalize(managementFeeAmount);

                penaltyAmount = normalize(penaltyAmount);

                amountPaid = normalize(amountPaid);

                remainingBalance = normalize(remainingBalance);
        }

        private BigDecimal normalize(
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

        public enum ScheduleStatus {

                PENDING,

                PAID,

                PARTIAL,

                OVERDUE
        }
}