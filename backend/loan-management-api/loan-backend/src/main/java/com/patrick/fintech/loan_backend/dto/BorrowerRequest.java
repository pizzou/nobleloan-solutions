package com.patrick.fintech.loan_backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

import jakarta.validation.constraints.*;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class BorrowerRequest {
    @NotBlank private String firstName;
    @NotBlank private String lastName;
    @Email   private String email;
    private String phone;
    @NotBlank
    private String alternatePhone;
       @NotBlank(message = "National ID is required")
@Pattern(
    regexp = "^\\d{16}$",
    message = "National ID must contain exactly 16 digits"
)
    private String nationalId;
    private String passportNumber;
    private String taxIdentificationNumber;
    private String dateOfBirth;
    @NotBlank
    private String gender;
    @NotBlank
    private String maritalStatus;
    @jakarta.validation.constraints.NotBlank(message = "Nationality is required")
    private String nationality;
    @jakarta.validation.constraints.NotBlank(message = "Place of birth is required")
    private String placeOfBirth;
    @jakarta.validation.constraints.NotBlank(message = "Physical address line 1 is required")
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String stateProvince;
    @jakarta.validation.constraints.NotBlank(message = "Physical address province is required")
    private String physicalAddressProvince;
    @jakarta.validation.constraints.NotBlank(message = "Physical address district is required")
    private String physicalAddressDistrict;
    @jakarta.validation.constraints.NotBlank(message = "Physical address sector is required")
    private String physicalAddressSector;
    @jakarta.validation.constraints.NotBlank(message = "Physical address cell is required")
    private String physicalAddressCell;
    private String physicalAddressVillage;
    @jakarta.validation.constraints.NotBlank(message = "Country is required")
    private String country;
    private String postalCode;
    private String employerName;
    private String employmentType;
    private String jobTitle;
    
    @JsonProperty("monthlyIncome")
private BigDecimal monthlyIncome;
    
    @JsonProperty("monthlyExpenses")
private BigDecimal monthlyExpenses;
    
    @JsonProperty("netWorth")
private BigDecimal netWorth;
    private Integer creditScore;
    private String creditBureau;
    private String bankName;
    private String bankAccountNumber;
    private String bankBranch;

    @Deprecated
    @JsonIgnore
    public Double getMonthlyIncome() {
        return monthlyIncome == null ? null : monthlyIncome.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getMonthlyIncomeDecimal() {
        return monthlyIncome;
    }

    @Deprecated
    public void setMonthlyIncome(Double value) {
        this.monthlyIncome = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setMonthlyIncome(BigDecimal value) {
        this.monthlyIncome = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getMonthlyExpenses() {
        return monthlyExpenses == null ? null : monthlyExpenses.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getMonthlyExpensesDecimal() {
        return monthlyExpenses;
    }

    @Deprecated
    public void setMonthlyExpenses(Double value) {
        this.monthlyExpenses = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setMonthlyExpenses(BigDecimal value) {
        this.monthlyExpenses = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getNetWorth() {
        return netWorth == null ? null : netWorth.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getNetWorthDecimal() {
        return netWorth;
    }

    @Deprecated
    public void setNetWorth(Double value) {
        this.netWorth = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setNetWorth(BigDecimal value) {
        this.netWorth = value;
    }

    /** Backward-compatible Double builder overloads; state remains BigDecimal. */
    public static class BorrowerRequestBuilder {
        private BigDecimal monthlyExpenses;
        private BigDecimal monthlyIncome;
        private BigDecimal netWorth;

        public BorrowerRequestBuilder monthlyIncome(Double value) {
            this.monthlyIncome = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BorrowerRequestBuilder monthlyIncome(BigDecimal value) {
            this.monthlyIncome = value;
            return this;
        }
        public BorrowerRequestBuilder monthlyExpenses(Double value) {
            this.monthlyExpenses = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BorrowerRequestBuilder monthlyExpenses(BigDecimal value) {
            this.monthlyExpenses = value;
            return this;
        }
        public BorrowerRequestBuilder netWorth(Double value) {
            this.netWorth = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BorrowerRequestBuilder netWorth(BigDecimal value) {
            this.netWorth = value;
            return this;
        }
    }
}
