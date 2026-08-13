
package com.patrick.fintech.loan_backend.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties({
        "hibernateLazyInitializer",
        "handler"
})
@Entity
@Table(name = "organizations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Organization {

    /*
     * ============================================================
     * IDENTIFICATION
     * ============================================================
     */

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
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


    /*
     * ============================================================
     * PUBLIC MARKETING WEBSITE CONTENT
     * ============================================================
     */

    private String tagline;

    @Column(columnDefinition = "TEXT")
    private String mission;

    @Column(columnDefinition = "TEXT")
    private String vision;

    private Integer foundedYear;

    private String facebookUrl;

    private String instagramUrl;

    private String linkedinUrl;

    private String twitterUrl;

    private String whatsappUrl;

    @Column(columnDefinition = "TEXT")
    private String mapUrl;


    /*
     * ============================================================
     * FLEXIBLE CMS CONTENT
     * ============================================================
     */

    private String heroHeadline;

    @Column(columnDefinition = "TEXT")
    private String heroSubtext;

    @Column(columnDefinition = "TEXT")
    private String statsJson;

    @Column(columnDefinition = "TEXT")
    private String servicesJson;

    @Column(columnDefinition = "TEXT")
    private String testimonialsJson;

    @Column(columnDefinition = "TEXT")
    private String teamJson;


    /*
     * ============================================================
     * SUBSCRIPTION
     * ============================================================
     */

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionTier subscriptionTier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrgStatus status;

    private Integer maxUsers;

    private Integer maxActiveLoans;


    /*
     * ============================================================
     * LOAN LIMITS
     *
     * IMPORTANT:
     *
     * maxLoanAmount == null means NO MAXIMUM.
     *
     * This is intentional for the current lending rules.
     *
     * Minimum loan amount is stored as BigDecimal because this is
     * monetary financial data.
     * ============================================================
     */

    @JsonProperty("maxLoanAmount")
    @Column(
            precision = 19,
            scale = 6
    )
    private BigDecimal maxLoanAmount;

    @JsonProperty("minLoanAmount")
    @Column(
            precision = 19,
            scale = 6
    )
    private BigDecimal minLoanAmount;


    /*
     * ============================================================
     * BILLING / SUBSCRIPTION DATES
     * ============================================================
     */

    private String stripeCustomerId;

    private LocalDateTime subscribedAt;

    private LocalDateTime trialEndsAt;

    private LocalDateTime subscriptionExpiresAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;


    /*
     * ============================================================
     * RELATIONSHIPS
     * ============================================================
     */

    @JsonIgnore
    @OneToMany(
            mappedBy = "organization",
            fetch = FetchType.LAZY
    )
    private List<User> users;

    @JsonIgnore
    @OneToMany(
            mappedBy = "organization",
            fetch = FetchType.LAZY
    )
    private List<Loan> loans;


    /*
     * ============================================================
     * DEFAULTS
     * ============================================================
     */

    @PrePersist
    protected void onCreate() {

        LocalDateTime now = LocalDateTime.now();

        if (createdAt == null) {
            createdAt = now;
        }

        if (updatedAt == null) {
            updatedAt = now;
        }

        if (subscriptionTier == null) {
            subscriptionTier = SubscriptionTier.TRIAL;
        }

        if (status == null) {
            status = OrgStatus.ACTIVE;
        }

        if (defaultCurrency == null || defaultCurrency.isBlank()) {
            defaultCurrency = "USD";
        }

        if (timezone == null || timezone.isBlank()) {
            timezone = "UTC";
        }

        if (locale == null || locale.isBlank()) {
            locale = "en-US";
        }

        if (maxUsers == null) {
            maxUsers = 100;
        }

        if (maxActiveLoans == null) {
            maxActiveLoans = 10_000;
        }

        /*
         * Production lending rule:
         *
         * Minimum loan amount = RWF 500,000.
         *
         * Do NOT automatically create a maximum.
         * null means unlimited.
         */
        if (minLoanAmount == null) {
            minLoanAmount = new BigDecimal("500000.00");
        }

        /*
         * maxLoanAmount intentionally remains null when no maximum
         * has been configured.
         */
    }


    @PreUpdate
    protected void onUpdate() {

        updatedAt = LocalDateTime.now();
    }


    /*
     * ============================================================
     * BIGDECIMAL FINANCIAL ACCESSORS
     *
     * Financial code should use these methods.
     * ============================================================
     */

    @JsonIgnore
    public BigDecimal getMaxLoanAmountDecimal() {

        return maxLoanAmount;
    }

    public void setMaxLoanAmount(BigDecimal value) {

        this.maxLoanAmount = normalizeMoney(value);
    }


    @JsonIgnore
    public BigDecimal getMinLoanAmountDecimal() {

        return minLoanAmount;
    }

    public void setMinLoanAmount(BigDecimal value) {

        this.minLoanAmount = normalizeMoney(value);
    }


    /*
     * ============================================================
     * LIMIT HELPERS
     * ============================================================
     */

    /**
     * Returns true when this organization has no configured
     * maximum loan amount.
     */
    @JsonIgnore
    public boolean hasUnlimitedMaximumLoanAmount() {

        return maxLoanAmount == null;
    }


    /**
     * Checks whether a loan amount satisfies the organization's
     * configured loan limits.
     *
     * A null maximum means unlimited.
     */
    @JsonIgnore
    public boolean isLoanAmountWithinLimits(BigDecimal amount) {

        if (amount == null) {
            return false;
        }

        BigDecimal normalizedAmount = normalizeMoney(amount);

        if (minLoanAmount != null
                && normalizedAmount.compareTo(minLoanAmount) < 0) {

            return false;
        }

        if (maxLoanAmount != null
                && normalizedAmount.compareTo(maxLoanAmount) > 0) {

            return false;
        }

        return true;
    }


    /*
     * ============================================================
     * MONEY NORMALIZATION
     * ============================================================
     */

    private static BigDecimal normalizeMoney(BigDecimal value) {

        if (value == null) {
            return null;
        }

        return value.setScale(
                6,
                java.math.RoundingMode.HALF_UP
        );
    }


    /*
     * ============================================================
     * ENUMS
     * ============================================================
     */

    public enum SubscriptionTier {

        TRIAL,
        STARTER,
        PROFESSIONAL,
        ENTERPRISE,
        UNLIMITED
    }

    public enum OrgStatus {

        ACTIVE,
        SUSPENDED,
        TRIAL,
        EXPIRED,
        PENDING_SETUP
    }
}
