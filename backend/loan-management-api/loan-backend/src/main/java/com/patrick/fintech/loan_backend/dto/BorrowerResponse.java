package com.patrick.fintech.loan_backend.dto;

import com.patrick.fintech.loan_backend.model.Borrower;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Public API representation of a borrower. Sensitive internal relationships are excluded. */
@Data
public class BorrowerResponse {
    private Long id;
    private Long organizationId;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String alternatePhone;
    private String nationalId;
    private String passportNumber;
    private String taxIdentificationNumber;
    private LocalDate dateOfBirth;
    private String gender;
    private String maritalStatus;
    private String nationality;
    private Boolean imported;
    private Long importBatchId;
    private String address;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String stateProvince;
    private String postalCode;
    private String country;
    private String employerName;
    private String employmentType;
    private String jobTitle;
    private BigDecimal monthlyIncome;
    private BigDecimal monthlyExpenses;
    private BigDecimal netWorth;
    private Integer creditScore;
    private String creditBureau;
    private LocalDate creditReportDate;
    private String kycStatus;
    private Borrower.BorrowerStatus status;
    private String blacklistReason;
    private LocalDateTime blacklistedAt;
    private String bankName;
    private String bankAccountNumber;
    private String bankBranch;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
