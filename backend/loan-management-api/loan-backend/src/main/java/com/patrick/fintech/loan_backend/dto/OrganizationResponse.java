package com.patrick.fintech.loan_backend.dto;

import com.patrick.fintech.loan_backend.model.Organization;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Tenant response containing only tenant/business configuration safe for API clients. */
@Data
public class OrganizationResponse {
    private Long id;
    private String slug;
    private String publicDomain;
    private String name;
    private String industry;
    private String country;
    private String defaultCurrency;
    private String timezone;
    private String locale;
    private String logoUrl;
    private String primaryColor;
    private String accentColor;
    private String website;
    private String contactEmail;
    private String contactPhone;
    private String address;
    private String registrationNumber;
    private String tagline;
    private String mission;
    private String vision;
    private Integer foundedYear;
    private String facebookUrl;
    private String instagramUrl;
    private String linkedinUrl;
    private String twitterUrl;
    private String whatsappUrl;
    private String mapUrl;
    private String heroHeadline;
    private String heroSubtext;
    private String statsJson;
    private String servicesJson;
    private String testimonialsJson;
    private String teamJson;
    private Organization.SubscriptionTier subscriptionTier;
    private Organization.OrgStatus status;
    private Integer maxUsers;
    private Integer maxActiveLoans;
    private BigDecimal maxLoanAmount;
    private BigDecimal minLoanAmount;
    private LocalDateTime subscribedAt;
    private LocalDateTime trialEndsAt;
    private LocalDateTime subscriptionExpiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
