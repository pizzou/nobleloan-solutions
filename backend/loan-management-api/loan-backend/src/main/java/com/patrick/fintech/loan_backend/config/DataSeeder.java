package com.patrick.fintech.loan_backend.config;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanProduct;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.Role;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.LoanProductRepository;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import com.patrick.fintech.loan_backend.repository.RoleRepository;
import com.patrick.fintech.loan_backend.repository.UserRepository;
import com.patrick.fintech.loan_backend.service.AccountingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

        private final OrganizationRepository orgRepo;

        private final UserRepository userRepo;

        private final RoleRepository roleRepo;

        private final PasswordEncoder encoder;

        private final AccountingService accountingService;

        private final LoanProductRepository loanProductRepo;

        /*
         * ============================================================
         * CURRENT BUSINESS RULES
         * ============================================================
         */

        private static final BigDecimal INTEREST_RATE = new BigDecimal("5.00");

        private static final String INTEREST_RATE_TYPE = "MONTHLY";

        private static final BigDecimal APPLICATION_FEE = new BigDecimal("2.00");

        private static final BigDecimal MANAGEMENT_FEE = new BigDecimal("5.00");

        private static final BigDecimal MIN_LOAN_AMOUNT = new BigDecimal("500000.00");

        private static final int MIN_TERM_MONTHS = 1;

        private static final int MAX_TERM_MONTHS = 6;

        @Override
        @Transactional
        public void run(String... args) {

                log.info("Starting Loan SaaS bootstrap validation...");

                Role adminRole = ensureRole(
                                "ADMIN",
                                "Full platform access");

                ensureRole(
                                "LOAN_OFFICER",
                                "Approve and disburse loans");

                ensureRole(
                                "MANAGER",
                                "Branch/portfolio management");

                List<Organization> organizations = orgRepo.findAll();

                if (organizations.isEmpty()) {

                        Organization organization = createDefaultOrganization();

                        createBootstrapAdmin(
                                        organization,
                                        adminRole);

                        accountingService.ensureChartOfAccounts(
                                        organization);

                        ensureLoanProducts(
                                        organization);

                        log.info(
                                        "Initial organization and loan products created successfully.");

                        logBootstrapInformation(
                                        organization);

                        return;
                }

                for (Organization organization : organizations) {

                        if (organization == null
                                        || organization.getId() == null) {
                                continue;
                        }

                        log.info(
                                        "Validating loan-product configuration for organization {} ({})",
                                        organization.getId(),
                                        organization.getName());

                        ensureLoanProducts(
                                        organization);

                        accountingService.ensureChartOfAccounts(
                                        organization);
                }

                /*
                 * Existing production databases do not enter the
                 * organizations.isEmpty() branch. Keep the configured
                 * bootstrap administrator's login phone synchronized on
                 * every startup so ADMIN email+SMS OTP works after a
                 * deployment without recreating or resetting the account.
                 */
                ensureConfiguredAdminPhone();

                log.info(
                                "Loan SaaS bootstrap validation completed successfully.");
        }

        /*
         * ============================================================
         * DEFAULT ORGANIZATION
         * ============================================================
         */

        private Organization createDefaultOrganization() {

                Organization organization = Organization.builder()

                                /*
                                 * Organization name remains exactly:
                                 * nobleloansolution
                                 */
                                .name(
                                                envOrDefault(
                                                                "BOOTSTRAP_ORG_NAME",
                                                                "nobleloansolution"))

                                /*
                                 * REQUIRED BY organizations.slug NOT NULL
                                 *
                                 * This is the missing field that caused
                                 * the production startup failure.
                                 */
                                .slug(
                                                envOrDefault(
                                                                "BOOTSTRAP_ORG_SLUG",
                                                                "nobleloansolution"))

                                .industry(
                                                "Microfinance")

                                .country(
                                                envOrDefault(
                                                                "BOOTSTRAP_ORG_COUNTRY",
                                                                "RW"))

                                .defaultCurrency(
                                                envOrDefault(
                                                                "BOOTSTRAP_ORG_CURRENCY",
                                                                "RWF"))

                                .timezone(
                                                envOrDefault(
                                                                "BOOTSTRAP_ORG_TIMEZONE",
                                                                "Africa/Kigali"))

                                .locale(
                                                envOrDefault(
                                                                "BOOTSTRAP_ORG_LOCALE",
                                                                "en-RW"))

                                .primaryColor(
                                                "#0F1B3D")

                                .accentColor(
                                                "#C9A227")

                                .website(
                                                "https://nobleloansolutions.rw")

                                .contactEmail(
                                                "info@nobleloansolutions.rw")

                                .contactPhone(
                                                "+250 788 000 000")

                                .address(
                                                "KG 7 Ave, Kigali, Rwanda")

                                .registrationNumber(
                                                "REG-NLS-004")

                                .tagline(
                                                "Your Trusted Partner in Financial Support")

                                .mission(
                                                "To provide honest, fairly-priced credit to individuals and businesses across Rwanda, delivered with integrity, transparency, and respect for every client.")

                                .vision(
                                                "To be Rwanda's most trusted name in lending — synonymous with fairness, transparency, and financial dignity for every client we serve.")

                                .heroHeadline(
                                                "Need Cash Fast? We've Got You Covered!")

                                .heroSubtext(
                                                "Your trusted partner in financial support — personal, business, vehicle, salary advance, and agriculture loans, backed by a secure, fully compliant lending platform.")

                                .foundedYear(
                                                2025)

                                .facebookUrl(
                                                "https://facebook.com/nobleloansolutionsrw")

                                .instagramUrl(
                                                "https://instagram.com/nobleloansolutionsrw")

                                .linkedinUrl(
                                                "https://linkedin.com/company/nobleloansolutionsrw")

                                .twitterUrl(
                                                "https://twitter.com/nobleloansolutionsrw")

                                .whatsappUrl(
                                                "https://wa.me/250788000000")

                                .mapUrl(
                                                "https://www.google.com/maps/embed?pb=!1m18!1m12!1m3!1d63800.15641867!2d30.0644!3d-1.9536!2m3!1f0!2f0!3f0!3m2!1i1024!2i768!4f13.1!3m3!1m2!1s0x19dca75a929d959f%3A0x0!2sKigali!5e0!3m2!1sen!2srw!4v1690000000000")

                                .subscriptionTier(
                                                Organization.SubscriptionTier.PROFESSIONAL)

                                .status(
                                                Organization.OrgStatus.ACTIVE)

                                .maxUsers(
                                                100)

                                .maxActiveLoans(
                                                10000)

                                .minLoanAmount(
                                                MIN_LOAN_AMOUNT)

                                /*
                                 * NULL means unlimited.
                                 */
                                .maxLoanAmount(
                                                null)

                                .subscribedAt(
                                                LocalDateTime.now())

                                .subscriptionExpiresAt(
                                                LocalDateTime.now()
                                                                .plusYears(1))

                                .build();

                return orgRepo.save(
                                organization);
        }

        /*
         * ============================================================
         * BOOTSTRAP ADMIN
         * ============================================================
         */

        private void createBootstrapAdmin(
                        Organization organization,
                        Role adminRole) {

                String email = System.getenv(
                                "BOOTSTRAP_ADMIN_EMAIL");

                String password = System.getenv(
                                "BOOTSTRAP_ADMIN_PASSWORD");

                String configuredName = System.getenv(
                                "BOOTSTRAP_ADMIN_NAME");

                String configuredPhone = System.getenv(
                                "BOOTSTRAP_ADMIN_PHONE");

                if (email == null
                                || email.isBlank()
                                || password == null
                                || password.isBlank()) {

                        throw new IllegalStateException(
                                        "BOOTSTRAP_ADMIN_EMAIL and BOOTSTRAP_ADMIN_PASSWORD "
                                                        + "must both be set — refusing to create "
                                                        + "an admin account with a guessable default.");
                }

                String normalizedEmail = email.trim()
                                .toLowerCase();

                if (userRepo.findByEmail(normalizedEmail).isPresent()) {

                        ensureConfiguredAdminPhone();

                        log.info(
                                        "Bootstrap admin {} already exists — no account reset performed.",
                                        normalizedEmail);

                        return;
                }

                String name = configuredName != null
                                && !configuredName.isBlank()
                                                ? configuredName.trim()
                                                : "Admin";

                User user = makeUser(
                                name,
                                normalizedEmail,
                                password,
                                adminRole,
                                organization);

                if (configuredPhone != null
                                && !configuredPhone.isBlank()) {

                        user.setPhone(
                                        configuredPhone.trim());
                }

                userRepo.save(user);

                log.info(
                                "Bootstrap administrator created: {}",
                                normalizedEmail);
        }

        private void ensureConfiguredAdminPhone() {

                String email = System.getenv(
                                "BOOTSTRAP_ADMIN_EMAIL");

                String phone = System.getenv(
                                "BOOTSTRAP_ADMIN_PHONE");

                if (email == null
                                || email.isBlank()) {

                        log.warn(
                                        "BOOTSTRAP_ADMIN_EMAIL is not configured; "
                                                        + "cannot identify the bootstrap administrator for login-phone repair.");

                        return;
                }

                if (phone == null
                                || phone.isBlank()) {

                        log.warn(
                                        "BOOTSTRAP_ADMIN_PHONE is not configured. "
                                                        + "ADMIN login will continue to require a registered mobile number.");

                        return;
                }

                String normalizedEmail = email.trim()
                                .toLowerCase();

                String normalizedPhone = phone.trim();

                User user = userRepo
                                .findByEmail(normalizedEmail)
                                .orElse(null);

                if (user == null) {

                        log.warn(
                                        "Configured bootstrap administrator {} does not exist yet; "
                                                        + "its phone will be assigned when the account is created.",
                                        normalizedEmail);

                        return;
                }

                if (user.getPhone() == null
                                || user.getPhone().isBlank()
                                || !user.getPhone()
                                                .trim()
                                                .equals(normalizedPhone)) {

                        user.setPhone(
                                        normalizedPhone);

                        userRepo.save(user);

                        log.info(
                                        "Registered login mobile number for bootstrap administrator {} was updated.",
                                        normalizedEmail);
                }
        }

        /*
         * ============================================================
         * ROLES
         * ============================================================
         */

        private Role ensureRole(
                        String name,
                        String description) {

                return roleRepo
                                .findByName(name)
                                .orElseGet(
                                                () -> roleRepo.save(
                                                                new Role(
                                                                                null,
                                                                                name,
                                                                                description)));
        }

        /*
         * ============================================================
         * USER
         * ============================================================
         */

        private User makeUser(
                        String name,
                        String email,
                        String password,
                        Role role,
                        Organization organization) {

                User user = new User();

                user.setName(
                                name);

                user.setEmail(
                                email);

                user.setPassword(
                                encoder.encode(password));

                user.setRole(
                                role);

                user.setOrganization(
                                organization);

                user.setStatus(
                                User.UserStatus.ACTIVE);

                return user;
        }

        /*
         * ============================================================
         * LOAN PRODUCTS
         * ============================================================
         */

        private void ensureLoanProducts(
                        Organization organization) {

                ensureProduct(
                                organization,
                                "Personal Loan",
                                "👤",
                                Loan.LoanType.PERSONAL,
                                "Personal financing for approved household and individual needs.",
                                1);

                ensureProduct(
                                organization,
                                "Business Finance",
                                "🏢",
                                Loan.LoanType.BUSINESS,
                                "Working capital and business expansion financing.",
                                2);

                ensureProduct(
                                organization,
                                "Vehicle Finance",
                                "🚗",
                                Loan.LoanType.AUTO,
                                "Financing for approved vehicle purchases.",
                                3);

                ensureProduct(
                                organization,
                                "Salary Advance Loan",
                                "💵",
                                Loan.LoanType.SALARY_ADVANCE,
                                "Short-term financing against verified salary income.",
                                4);

                ensureProduct(
                                organization,
                                "Agriculture Loan",
                                "🌾",
                                Loan.LoanType.AGRICULTURAL,
                                "Financing for approved agricultural and agribusiness activities.",
                                5);
        }

        /*
         * ============================================================
         * PRODUCT UPSERT
         * ============================================================
         */

        private void ensureProduct(
                        Organization organization,
                        String name,
                        String icon,
                        Loan.LoanType type,
                        String description,
                        int displayOrder) {

                LoanProduct product = loanProductRepo
                                .findByOrganization_IdAndLoanType(
                                                organization.getId(),
                                                type)
                                .orElseGet(
                                                LoanProduct::new);

                boolean isNew = product.getId() == null;

                product.setOrganization(
                                organization);

                product.setName(
                                name);

                product.setIcon(
                                icon);

                product.setLoanType(
                                type);

                product.setDescription(
                                description);

                /*
                 * ========================================================
                 * CURRENT FINANCIAL RULES
                 * ========================================================
                 */

                product.setInterestRate(
                                INTEREST_RATE);

                product.setInterestRateType(
                                INTEREST_RATE_TYPE);

                product.setMinAmount(
                                MIN_LOAN_AMOUNT);

                /*
                 * NULL means unlimited.
                 */
                product.setMaxAmount(
                                null);

                product.setMinTermMonths(
                                MIN_TERM_MONTHS);

                product.setMaxTermMonths(
                                MAX_TERM_MONTHS);

                /*
                 * Application fee:
                 * One-time fee charged at application/disbursement.
                 */
                product.setApplicationFeePercent(
                                APPLICATION_FEE);

                /*
                 * Management fee:
                 * Monthly management fee.
                 */
                product.setManagementFeePercent(
                                MANAGEMENT_FEE);

                product.setActive(
                                true);

                product.setDisplayOrder(
                                displayOrder);

                if (isNew) {

                        log.info(
                                        "Creating loan product '{}' for organization {}",
                                        name,
                                        organization.getId());

                } else {

                        log.info(
                                        "Updating loan product '{}' for organization {} "
                                                        + "to current lending rules",
                                        name,
                                        organization.getId());
                }

                loanProductRepo.save(
                                product);
        }

        private String envOrDefault(
                        String name,
                        String defaultValue) {

                String value = System.getenv(
                                name);

                return value == null
                                || value.isBlank()
                                                ? defaultValue
                                                : value.trim();
        }

        /*
         * ============================================================
         * BOOTSTRAP LOG
         * ============================================================
         */

        private void logBootstrapInformation(
                        Organization organization) {

                log.info("");

                log.info(
                                "╔══════════════════════════════════════════════════════════════╗");

                log.info(
                                "║             LOANSAAS PRO — BOOTSTRAP COMPLETE              ║");

                log.info(
                                "╠══════════════════════════════════════════════════════════════╣");

                log.info(
                                "║ Organization : {}",
                                organization.getName());

                log.info(
                                "║ Currency     : {}",
                                organization.getDefaultCurrency());

                log.info(
                                "║ Min Loan     : {}",
                                MIN_LOAN_AMOUNT);

                log.info(
                                "║ Max Loan     : UNLIMITED");

                log.info(
                                "║ Interest     : {}% MONTHLY",
                                INTEREST_RATE);

                log.info(
                                "║ Application   : {}%",
                                APPLICATION_FEE);

                log.info(
                                "║ Management   : {}%",
                                MANAGEMENT_FEE);

                log.info(
                                "║ Term         : {}-{} months",
                                MIN_TERM_MONTHS,
                                MAX_TERM_MONTHS);

                log.info(
                                "╚══════════════════════════════════════════════════════════════╝");

                log.info("");
        }
}