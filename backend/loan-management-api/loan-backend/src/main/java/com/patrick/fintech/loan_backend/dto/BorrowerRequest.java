
package com.patrick.fintech.loan_backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;

import java.math.BigDecimal;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BorrowerRequest {

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    @Email
    private String email;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^0\\d{9}$", message = "Phone number must contain exactly 10 digits and start with 0")
    private String phone;

    // Deprecated compatibility field.
    // The application now collects one phone number.
    // Older clients may still supply this field.
    private String alternatePhone;

    @NotBlank(message = "National ID is required")
    @Pattern(regexp = "^\\d{16}$", message = "National ID must contain exactly 16 digits")
    private String nationalId;

    private String passportNumber;
    private String taxIdentificationNumber;
    private String dateOfBirth;

    @NotBlank
    private String gender;

    @NotBlank
    private String maritalStatus;

    @Size(max = 255)
    private String singleCertificateNumber;

    @Size(max = 255)
    private String spouseFullName;

    private String spouseNationalId;
    private String spousePhone;
    private Boolean spouseConsent;

    @Size(max = 10, message = "Nationality cannot exceed 10 characters")
    private String nationality;

    @NotBlank(message = "Place of birth is required")
    private String placeOfBirth;

    @NotBlank(message = "Physical address line 1 is required")
    private String addressLine1;

    private String addressLine2;
    private String city;
    private String stateProvince;

    @NotBlank(message = "Physical address province is required")
    private String physicalAddressProvince;

    @NotBlank(message = "Physical address district is required")
    private String physicalAddressDistrict;

    @NotBlank(message = "Physical address sector is required")
    private String physicalAddressSector;

    @NotBlank(message = "Physical address cell is required")
    private String physicalAddressCell;

    @NotBlank(message = "Physical address village is required")
    @Size(max = 255, message = "Physical address village cannot exceed 255 characters")
    private String physicalAddressVillage;

    @NotBlank(message = "Country is required")
    @Size(max = 10, message = "Country cannot exceed 10 characters")
    private String country;

    private String postalCode;

    private String employerName;
    private String employmentType;
    private String jobTitle;

    // Financial amounts are stored as BigDecimal to preserve
    // monetary precision during request processing.

    @JsonProperty("monthlyIncome")
    @DecimalMin(value = "0.0", message = "monthlyIncome must not be negative")
    private BigDecimal monthlyIncome;

    @JsonProperty("monthlyExpenses")
    @DecimalMin(value = "0.0", message = "monthlyExpenses must not be negative")
    private BigDecimal monthlyExpenses;

    @JsonProperty("netWorth")
    @DecimalMin(value = "0.0", message = "netWorth must not be negative")
    private BigDecimal netWorth;

    private Integer creditScore;
    private String creditBureau;

    private String bankName;
    private String bankAccountNumber;
    private String bankBranch;

    // ==========================================================
    // MONTHLY INCOME
    // ==========================================================

    /**
     * Backward-compatible getter for existing Java callers.
     * Jackson must not use this Double representation.
     */
    @Deprecated
    @JsonIgnore
    public Double getMonthlyIncome() {
        return monthlyIncome == null
                ? null
                : monthlyIncome.doubleValue();
    }

    /**
     * Full-precision accessor for Java service code.
     */
    @JsonIgnore
    public BigDecimal getMonthlyIncomeDecimal() {
        return monthlyIncome;
    }

    /**
     * Legacy Java setter.
     *
     * IMPORTANT:
     * JsonIgnore excludes this overload from Jackson
     * deserialization, preventing conflicting setter errors.
     */
    @Deprecated
    @JsonIgnore
    public void setMonthlyIncome(Double value) {
        this.monthlyIncome = value == null
                ? null
                : BigDecimal.valueOf(value);
    }

    /**
     * Authoritative setter for JSON requests.
     */
    @JsonSetter("monthlyIncome")
    public void setMonthlyIncome(BigDecimal value) {
        this.monthlyIncome = value;
    }

    // ==========================================================
    // MONTHLY EXPENSES
    // ==========================================================

    /**
     * Backward-compatible Double getter.
     */
    @Deprecated
    @JsonIgnore
    public Double getMonthlyExpenses() {
        return monthlyExpenses == null
                ? null
                : monthlyExpenses.doubleValue();
    }

    /**
     * Full-precision accessor.
     */
    @JsonIgnore
    public BigDecimal getMonthlyExpensesDecimal() {
        return monthlyExpenses;
    }

    /**
     * Legacy setter retained for Java compatibility.
     * Excluded from Jackson deserialization.
     */
    @Deprecated
    @JsonIgnore
    public void setMonthlyExpenses(Double value) {
        this.monthlyExpenses = value == null
                ? null
                : BigDecimal.valueOf(value);
    }

    /**
     * Authoritative JSON setter.
     */
    @JsonSetter("monthlyExpenses")
    public void setMonthlyExpenses(BigDecimal value) {
        this.monthlyExpenses = value;
    }

    // ==========================================================
    // NET WORTH
    // ==========================================================

    /**
     * Backward-compatible Double getter.
     */
    @Deprecated
    @JsonIgnore
    public Double getNetWorth() {
        return netWorth == null
                ? null
                : netWorth.doubleValue();
    }

    /**
     * Full-precision accessor.
     */
    @JsonIgnore
    public BigDecimal getNetWorthDecimal() {
        return netWorth;
    }

    /**
     * Legacy setter retained for Java compatibility.
     * Excluded from Jackson deserialization.
     */
    @Deprecated
    @JsonIgnore
    public void setNetWorth(Double value) {
        this.netWorth = value == null
                ? null
                : BigDecimal.valueOf(value);
    }

    /**
     * Authoritative JSON setter.
     */
    @JsonSetter("netWorth")
    public void setNetWorth(BigDecimal value) {
        this.netWorth = value;
    }

    // ==========================================================
    // BACKWARD-COMPATIBLE LOMBOK BUILDER
    // ==========================================================

    /**
     * Existing Java callers can continue passing Double
     * or BigDecimal values to the builder.
     *
     * Builder overloads do not participate in ordinary
     * Jackson bean setter discovery.
     */
    public static class BorrowerRequestBuilder {

        private BigDecimal monthlyExpenses;
        private BigDecimal monthlyIncome;
        private BigDecimal netWorth;

        public BorrowerRequestBuilder monthlyIncome(Double value) {
            this.monthlyIncome = value == null
                    ? null
                    : BigDecimal.valueOf(value);
            return this;
        }

        public BorrowerRequestBuilder monthlyIncome(BigDecimal value) {
            this.monthlyIncome = value;
            return this;
        }

        public BorrowerRequestBuilder monthlyExpenses(Double value) {
            this.monthlyExpenses = value == null
                    ? null
                    : BigDecimal.valueOf(value);
            return this;
        }

        public BorrowerRequestBuilder monthlyExpenses(BigDecimal value) {
            this.monthlyExpenses = value;
            return this;
        }

        public BorrowerRequestBuilder netWorth(Double value) {
            this.netWorth = value == null
                    ? null
                    : BigDecimal.valueOf(value);
            return this;
        }

        public BorrowerRequestBuilder netWorth(BigDecimal value) {
            this.netWorth = value;
            return this;
        }
    }
}
